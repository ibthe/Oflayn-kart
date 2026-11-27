package app.georgia.offline.data

import android.content.Context
import android.net.Uri
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.mapsforge.map.reader.MapFile
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

data class MapStorageOption(
    val id: String,
    val label: String,
    val directory: File,
    val availableBytes: Long,
    val totalBytes: Long,
    val removable: Boolean
)

/** Installs, imports and validates Mapsforge .map files without network access. */
object MapFileRepository {
    const val INTERNAL_STORAGE_ID = "internal"

    fun availableStorageOptions(context: Context): List<MapStorageOption> =
        availableStorageOptions(
            context,
            context.getExternalFilesDirs(null),
            { directory -> Environment.isExternalStorageRemovable(directory) },
            { directory -> Environment.getExternalStorageState(directory) }
        )

    internal fun availableStorageOptions(
        context: Context,
        externalDirectories: Array<File?>,
        isRemovable: (File) -> Boolean,
        storageState: (File) -> String
    ): List<MapStorageOption> {
        val internalDirectory = File(context.filesDir, "maps")
        val options = mutableListOf(
            MapStorageOption(
                INTERNAL_STORAGE_ID,
                "Внутренняя память",
                internalDirectory,
                context.filesDir.usableSpace,
                context.filesDir.totalSpace,
                removable = false
            )
        )
        externalDirectories.filterNotNull().forEach { appDirectory ->
            if (isRemovable(appDirectory) && storageState(appDirectory) == Environment.MEDIA_MOUNTED) {
                val mapDirectory = File(appDirectory, "maps")
                options += MapStorageOption(
                    appDirectory.absolutePath,
                    "SD-карта",
                    mapDirectory,
                    appDirectory.usableSpace,
                    appDirectory.totalSpace,
                    removable = true
                )
            }
        }
        return options
    }

    private const val BUNDLED_ASSET = "offline/georgia.map"
    private const val BUNDLED_MANIFEST = "offline/georgia-map-build.json"
    private const val MINIMUM_MAP_BYTES = 1024L
    private const val BUNDLED_HASH_MARKER = ".georgia-bundled.sha256"

    /**
     * Installs the map embedded in this APK and refreshes it when a newer APK contains
     * a different SHA-256. The caller must not invoke this for a user-imported map.
     */
    suspend fun installBundledMap(context: Context, storageId: String? = null): File? = withContext(Dispatchers.IO) {
        val destination = mapDestination(context, storageId)
        val marker = File(destination.parentFile, BUNDLED_HASH_MARKER)
        val expectedSha = readBundledMapSha256(context)
        val expectedSize = readBundledMapSize(context)

        val existingIsValid = destination.isFile &&
            destination.length() >= MINIMUM_MAP_BYTES &&
            runCatching { isGeorgiaMap(destination) }.getOrDefault(false)
        if (existingIsValid && expectedSha != null && marker.isFile &&
            marker.readText(Charsets.UTF_8).trim().equals(expectedSha, ignoreCase = true)
        ) {
            return@withContext destination
        }

        if (expectedSize != null) {
            val minimumFreeSpace = expectedSize + 32L * 1024L * 1024L
            val availableSpace = destination.parentFile?.usableSpace ?: Long.MAX_VALUE
            if (availableSpace < minimumFreeSpace) {
                val requiredMb = (minimumFreeSpace + 1024L * 1024L - 1L) / (1024L * 1024L)
                error("Недостаточно свободного места для установки карты. Освободи примерно $requiredMb МБ и попробуй снова.")
            }
        }

        val input = try {
            context.assets.open(BUNDLED_ASSET)
        } catch (_: FileNotFoundException) {
            return@withContext null
        }

        val temporary = File(destination.parentFile, "georgia-bundled.map.part")
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            input.use { source ->
                temporary.outputStream().buffered().use { target ->
                    val buffer = ByteArray(1024 * 1024)
                    while (true) {
                        val count = source.read(buffer)
                        if (count < 0) break
                        digest.update(buffer, 0, count)
                        target.write(buffer, 0, count)
                    }
                }
            }
            val actualSha = digest.digest().joinToString("") { byte -> "%02x".format(byte) }
            if (expectedSha != null && !actualSha.equals(expectedSha, ignoreCase = true)) {
                error("SHA-256 встроенной карты не совпадает с картографическим манифестом.")
            }
            validateGeorgiaMap(temporary)
            moveIntoPlace(temporary, destination)
            marker.writeText(actualSha, Charsets.UTF_8)
            destination
        } catch (error: Exception) {
            temporary.delete()
            throw IllegalArgumentException(
                error.message ?: "Не удалось установить встроенную карту Грузии.",
                error
            )
        }
    }

    private fun readBundledMapSha256(context: Context): String? = readBundledManifest(context)
        ?.optString("mapFileSha256")
        ?.takeIf { it.matches(Regex("[0-9a-fA-F]{64}")) }

    private fun readBundledMapSize(context: Context): Long? = readBundledManifest(context)
        ?.optLong("mapFileSizeBytes", -1L)
        ?.takeIf { it > 0L }

    private fun readBundledManifest(context: Context): JSONObject? {
        return try {
            val json = context.assets.open(BUNDLED_MANIFEST).bufferedReader(Charsets.UTF_8).use { it.readText() }
            JSONObject(json)
        } catch (_: FileNotFoundException) {
            null
        }
    }

    /** Imports a Mapsforge file selected via Android's Storage Access Framework. */
    suspend fun importGeorgiaMap(context: Context, source: Uri, storageId: String? = null): File = withContext(Dispatchers.IO) {
        val destination = mapDestination(context, storageId)
        val temporary = File(destination.parentFile, "georgia-import.map.part")
        val sourceLength = runCatching {
            context.contentResolver.openAssetFileDescriptor(source, "r")?.use { it.length }
        }.getOrNull()
        if (sourceLength != null && sourceLength > 0L) {
            val required = sourceLength + 32L * 1024L * 1024L
            if ((destination.parentFile?.usableSpace ?: Long.MAX_VALUE) < required) {
                error("Недостаточно свободного места для импорта карты на выбранный накопитель.")
            }
        }

        try {
            val input = context.contentResolver.openInputStream(source)
                ?: error("Android не смог открыть выбранный файл.")
            input.use { sourceStream ->
                temporary.outputStream().buffered().use { destinationStream ->
                    sourceStream.copyTo(destinationStream, bufferSize = 1024 * 1024)
                }
            }
            validateGeorgiaMap(temporary)
            moveIntoPlace(temporary, destination)
            // A manually imported file must never be mistaken for the previous APK bundle.
            File(destination.parentFile, BUNDLED_HASH_MARKER).delete()
            destination
        } catch (error: Exception) {
            temporary.delete()
            throw IllegalArgumentException(
                error.message ?: "Не удалось импортировать карту. Проверь, что это файл Mapsforge .map.",
                error
            )
        }
    }

    /** Moves a user-imported map to the selected volume without deleting the source until validation succeeds. */
    suspend fun moveImportedMap(context: Context, source: File, storageId: String?): File = withContext(Dispatchers.IO) {
        require(source.isFile) { "Текущий файл карты недоступен." }
        val destination = mapDestination(context, storageId)
        if (source.canonicalFile == destination.canonicalFile) return@withContext source
        val required = source.length() + 32L * 1024L * 1024L
        val available = destination.parentFile?.usableSpace ?: Long.MAX_VALUE
        if (available < required) {
            error("Недостаточно свободного места на выбранном накопителе для переноса карты.")
        }
        val temporary = File(destination.parentFile, "georgia-move.map.part")
        try {
            source.copyTo(temporary, overwrite = true)
            validateGeorgiaMap(temporary)
            moveIntoPlace(temporary, destination)
            File(destination.parentFile, BUNDLED_HASH_MARKER).delete()
            destination
        } catch (error: Exception) {
            temporary.delete()
            throw IllegalArgumentException(error.message ?: "Не удалось перенести карту.", error)
        }
    }

    /** Deletes the previous managed copy only after settings point at the validated destination. */
    fun deletePreviousMap(source: File, destination: File) {
        if (runCatching { source.canonicalFile != destination.canonicalFile }.getOrDefault(false) &&
            source.name == "georgia.map" && source.parentFile?.name == "maps"
        ) {
            source.delete()
            File(source.parentFile, BUNDLED_HASH_MARKER).delete()
        }
    }

    private fun mapDestination(context: Context, storageId: String? = null): File {
        val option = if (storageId == null || storageId == INTERNAL_STORAGE_ID) {
            availableStorageOptions(context).first()
        } else {
            availableStorageOptions(context).firstOrNull { it.id == storageId }
                ?: error("Выбранный накопитель недоступен. Подключи SD-карту или выбери внутреннюю память.")
        }
        val directory = option.directory
        if (!directory.exists() && !directory.mkdirs()) {
            error("Не удалось создать папку для карты на выбранном накопителе.")
        }
        return File(directory, "georgia.map")
    }

    private fun validateGeorgiaMap(file: File) {
        if (!file.isFile || file.length() < MINIMUM_MAP_BYTES) {
            error("Файл слишком мал или пуст. Нужен полноценный Mapsforge .map.")
        }
        if (!isGeorgiaMap(file)) {
            error("Выбранная карта не пересекается с территорией Грузии или имеет неверный формат.")
        }
    }

    private fun isGeorgiaMap(file: File): Boolean {
        val probe = MapFile(file)
        return try {
            val bounds = probe.boundingBox()
            bounds.maxLatitude >= 40.0 &&
                bounds.minLatitude <= 44.0 &&
                bounds.maxLongitude >= 39.5 &&
                bounds.minLongitude <= 47.0
        } finally {
            probe.close()
        }
    }

    private fun moveIntoPlace(temporary: File, destination: File) {
        try {
            Files.move(
                temporary.toPath(),
                destination.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
