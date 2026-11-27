package app.georgia.offline.data

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID
import java.util.zip.ZipInputStream

data class RasterPackageInfo(
    val id: String,
    val name: String,
    val attribution: String,
    val license: String,
    val extension: String,
    val minZoom: Int,
    val maxZoom: Int,
    val tileCount: Int,
    val storageId: String,
    val directory: File
) {
    val tileDirectory: File get() = File(directory, "tiles")
}

/**
 * Imports optional, user-supplied XYZ raster tile packs.
 * The package is kept separate from the vector map and is never downloaded by the app.
 */
object RasterPackageRepository {
    private const val PREFS = "oflayn_raster_packages"
    private const val KEY_ACTIVE_ID = "active_id"
    private const val KEY_ACTIVE_STORAGE = "active_storage"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_OPACITY = "opacity"
    private const val FORMAT = "oflayn-raster-pack"
    private const val VERSION = 1
    private const val MAX_MANIFEST_BYTES = 1024L * 1024L
    private const val MAX_TILE_BYTES = 2L * 1024L * 1024L
    private const val MAX_TOTAL_BYTES = 8L * 1024L * 1024L * 1024L
    private const val MAX_TILE_COUNT = 1_000_000

    private data class Manifest(
        val name: String,
        val attribution: String,
        val license: String,
        val extension: String,
        val minZoom: Int,
        val maxZoom: Int,
        val tileCount: Int
    )

    fun importZip(context: Context, uri: Uri, storageId: String): RasterPackageInfo {
        val storage = MapFileRepository.availableStorageOptions(context)
            .firstOrNull { it.id == storageId }
            ?: error("Выбранное хранилище недоступно. Подключи накопитель и повтори импорт.")
        val storageRoot = storage.directory.parentFile ?: context.filesDir
        val packageRoot = File(storageRoot, "raster-packages")
        check(packageRoot.isDirectory || packageRoot.mkdirs()) { "Не удалось создать папку спутниковых пакетов." }
        val temporary = File(packageRoot, ".${UUID.randomUUID()}.part")
        check(temporary.mkdirs()) { "Не удалось подготовить временную папку пакета." }

        try {
            var manifestBytes: ByteArray? = null
            var totalBytes = 0L
            var tileCount = 0
            val zoomLevels = HashSet<Int>()
            val seenPaths = HashSet<String>()
            val allowedExtension = setOf("png", "jpg")

            val input = context.contentResolver.openInputStream(uri)
                ?: error("Не удалось открыть ZIP-файл.")
            ZipInputStream(BufferedInputStream(input)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val name = entry.name
                    check(name.isNotBlank() && !name.startsWith("/") && !name.contains('\\') &&
                        !name.contains(':') && name.split('/').none { it == ".." || it == "." }
                    ) { "В архиве обнаружен небезопасный путь." }
                    if (entry.isDirectory) {
                        check(name == "tiles/" || name.startsWith("tiles/")) { "В архиве есть недопустимая папка." }
                        zip.closeEntry()
                        continue
                    }
                    check(seenPaths.add(name)) { "В архиве повторяется файл: $name" }
                    if (name == "manifest.json") {
                        val bytes = ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (true) {
                            val read = zip.read(buffer)
                            if (read < 0) break
                            totalBytes += read
                            check(bytes.size().toLong() + read <= MAX_MANIFEST_BYTES && totalBytes <= MAX_TOTAL_BYTES) {
                                "Манифест или архив слишком велик."
                            }
                            bytes.write(buffer, 0, read)
                        }
                        manifestBytes = bytes.toByteArray()
                    } else {
                        val tile = parseTilePath(name, allowedExtension)
                            ?: error("Недопустимый файл в пакете: $name")
                        check(tile.zoom in 6..18 && tile.x in 0 until (1 shl tile.zoom) &&
                            tile.y in 0 until (1 shl tile.zoom)
                        ) { "Координаты тайла вне допустимого диапазона: $name" }
                        zoomLevels += tile.zoom
                        val destination = File(temporary, name)
                        val canonicalRoot = temporary.canonicalFile
                        check(destination.canonicalFile.path.startsWith(canonicalRoot.path + File.separator)) {
                            "Путь тайла выходит за пределы пакета."
                        }
                        destination.parentFile?.let { parent ->
                            check(parent.isDirectory || parent.mkdirs()) { "Не удалось создать папку тайлов." }
                        }
                        var tileBytes = 0L
                        val header = ByteArray(8)
                        var headerSize = 0
                        BufferedOutputStream(FileOutputStream(destination)).use { output ->
                            val buffer = ByteArray(16 * 1024)
                            while (true) {
                                val read = zip.read(buffer)
                                if (read < 0) break
                                tileBytes += read
                                totalBytes += read
                                check(tileBytes <= MAX_TILE_BYTES && totalBytes <= MAX_TOTAL_BYTES) {
                                    "Размер тайла или архива превышает безопасный предел."
                                }
                                if (headerSize < header.size) {
                                    val copy = minOf(read, header.size - headerSize)
                                    System.arraycopy(buffer, 0, header, headerSize, copy)
                                    headerSize += copy
                                }
                                output.write(buffer, 0, read)
                            }
                        }
                        check(isValidImageHeader(tile.extension, header, headerSize)) {
                            "Файл не похож на изображение тайла: $name"
                        }
                        tileCount++
                        check(tileCount <= MAX_TILE_COUNT) { "В пакете слишком много тайлов." }
                    }
                    zip.closeEntry()
                }
            }

            val rawManifest = manifestBytes ?: error("В архиве нет manifest.json.")
            val manifest = parseManifest(JSONObject(String(rawManifest, Charsets.UTF_8)))
            check(zoomLevels.all { it in manifest.minZoom..manifest.maxZoom }) {
                "В архиве есть тайлы вне диапазона масштабов манифеста."
            }
            check(tileCount == manifest.tileCount) {
                "Количество тайлов не совпадает с манифестом: найдено $tileCount, заявлено ${manifest.tileCount}."
            }
            check(seenPaths.any { it.startsWith("tiles/") }) { "Пакет не содержит тайлов." }
            check(seenPaths.filter { it.startsWith("tiles/") }.all {
                it.endsWith(".${manifest.extension}")
            }) { "Расширение файлов не совпадает с манифестом." }

            File(temporary, "manifest.json").writeBytes(rawManifest)
            val id = UUID.randomUUID().toString()
            val destination = File(packageRoot, id)
            check(temporary.renameTo(destination)) { "Не удалось завершить установку пакета." }
            val installed = RasterPackageInfo(
                id, manifest.name, manifest.attribution, manifest.license, manifest.extension,
                manifest.minZoom, manifest.maxZoom, manifest.tileCount, storageId, destination
            )
            setActive(context, installed)
            setEnabled(context, false)
            return installed
        } catch (error: Exception) {
            temporary.deleteRecursively()
            throw error
        }
    }

    fun listInstalled(context: Context): List<RasterPackageInfo> =
        MapFileRepository.availableStorageOptions(context).flatMap { storage ->
            val root = File(storage.directory.parentFile ?: context.filesDir, "raster-packages")
            root.listFiles()?.filter { it.isDirectory && !it.name.endsWith(".part") }
                ?.mapNotNull { directory ->
                    runCatching {
                        val manifest = parseManifest(JSONObject(File(directory, "manifest.json").readText()))
                        val tiles = File(directory, "tiles")
                        if (!tiles.isDirectory) return@runCatching null
                        RasterPackageInfo(
                            directory.name, manifest.name, manifest.attribution, manifest.license, manifest.extension,
                            manifest.minZoom, manifest.maxZoom, manifest.tileCount, storage.id, directory
                        )
                    }.getOrNull()
                }.orEmpty()
        }

    fun active(context: Context): RasterPackageInfo? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val id = prefs.getString(KEY_ACTIVE_ID, null) ?: return null
        val storageId = prefs.getString(KEY_ACTIVE_STORAGE, null) ?: return null
        return listInstalled(context).firstOrNull { it.id == id && it.storageId == storageId }
    }

    fun setActive(context: Context, packageInfo: RasterPackageInfo?) {
        val edit = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        if (packageInfo == null) {
            edit.remove(KEY_ACTIVE_ID).remove(KEY_ACTIVE_STORAGE)
        } else {
            edit.putString(KEY_ACTIVE_ID, packageInfo.id).putString(KEY_ACTIVE_STORAGE, packageInfo.storageId)
        }
        edit.apply()
    }

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun opacity(context: Context): Float =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getFloat(KEY_OPACITY, 0.62f).coerceIn(0.25f, 0.9f)

    fun setOpacity(context: Context, opacity: Float) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putFloat(KEY_OPACITY, opacity.coerceIn(0.25f, 0.9f)).apply()
    }

    fun remove(context: Context, packageInfo: RasterPackageInfo): Boolean {
        val storage = MapFileRepository.availableStorageOptions(context)
            .firstOrNull { it.id == packageInfo.storageId } ?: return false
        val root = File(storage.directory.parentFile ?: context.filesDir, "raster-packages").canonicalFile
        val directory = packageInfo.directory.canonicalFile
        if (!directory.path.startsWith(root.path + File.separator)) return false
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val wasActive = prefs.getString(KEY_ACTIVE_ID, null) == packageInfo.id &&
            prefs.getString(KEY_ACTIVE_STORAGE, null) == packageInfo.storageId
        val removed = directory.deleteRecursively()
        if (removed && wasActive) {
            setActive(context, null)
            setEnabled(context, false)
        }
        return removed
    }

    private data class TilePath(val zoom: Int, val x: Int, val y: Int, val extension: String)

    private fun parseTilePath(path: String, extensions: Set<String>): TilePath? {
        val parts = path.split('/')
        if (parts.size != 4 || parts[0] != "tiles") return null
        val zoom = parts[1].toIntOrNull() ?: return null
        val x = parts[2].toIntOrNull() ?: return null
        val filename = parts[3]
        val dot = filename.lastIndexOf('.')
        if (dot <= 0) return null
        val y = filename.substring(0, dot).toIntOrNull() ?: return null
        val extension = filename.substring(dot + 1).lowercase()
        if (extension !in extensions) return null
        return TilePath(zoom, x, y, extension)
    }

    private fun isValidImageHeader(extension: String, header: ByteArray, size: Int): Boolean =
        when (extension) {
            "png" -> size >= 8 && header.copyOfRange(0, 8).contentEquals(
                byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
            )
            "jpg" -> size >= 3 && header[0] == 0xFF.toByte() && header[1] == 0xD8.toByte() && header[2] == 0xFF.toByte()
            else -> false
        }

    private fun parseManifest(json: JSONObject): Manifest {
        require(json.optString("format") == FORMAT) { "Неизвестный формат спутникового пакета." }
        require(json.optInt("version", -1) == VERSION) { "Версия спутникового пакета не поддерживается." }
        val name = json.optString("name").trim()
        val attribution = json.optString("attribution").trim()
        val license = json.optString("license").trim()
        val extension = json.optString("extension").lowercase()
        val minZoom = json.optInt("minZoom", -1)
        val maxZoom = json.optInt("maxZoom", -1)
        val tileCount = json.optInt("tileCount", -1)
        require(name.isNotBlank() && name.length <= 80) { "В манифесте неверное название пакета." }
        require(attribution.isNotBlank() && attribution.length <= 240) { "В манифесте отсутствует атрибуция источника снимков." }
        require(license.isNotBlank() && license.length <= 120) { "В манифесте не указана лицензия снимков." }
        require(json.optString("tileScheme") == "xyz") { "Поддерживается только XYZ-схема тайлов." }
        require(json.optInt("tileSize", -1) == 256) { "Размер тайла должен быть 256×256." }
        require(extension == "png" || extension == "jpg") { "Поддерживаются только PNG и JPG тайлы." }
        require(minZoom in 6..18 && maxZoom in minZoom..18) { "Диапазон масштабов пакета вне пределов карты." }
        require(tileCount in 1..MAX_TILE_COUNT) { "В манифесте указано неверное количество тайлов." }
        val bounds = json.optJSONArray("bounds")
        if (bounds != null) {
            require(bounds.length() == 4) { "Границы пакета должны содержать south, west, north, east." }
            val south = bounds.getDouble(0)
            val west = bounds.getDouble(1)
            val north = bounds.getDouble(2)
            val east = bounds.getDouble(3)
            require(south in -90.0..90.0 && north in -90.0..90.0 &&
                west in -180.0..180.0 && east in -180.0..180.0 && south < north && west < east
            ) { "В манифесте неверные географические границы." }
            require(north >= 41.0 && south <= 43.8 && east >= 40.0 && west <= 46.8) {
                "Пакет не покрывает территорию Грузии."
            }
        }
        return Manifest(name, attribution, license, extension, minZoom, maxZoom, tileCount)
    }
}
