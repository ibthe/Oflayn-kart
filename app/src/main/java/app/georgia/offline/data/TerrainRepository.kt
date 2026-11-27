package app.georgia.offline.data

import android.content.Context
import java.io.File
import java.security.MessageDigest
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Installs the optional vector contour overlay from bundled assets without network access. */
object TerrainRepository {
    fun bundledContoursBytes(context: Context): Long = runCatching {
        val json = context.assets.open("offline/georgia-contours-manifest.json")
            .bufferedReader(Charsets.UTF_8).use { it.readText() }
        JSONObject(json).optLong("mapFileSizeBytes", 0L).coerceAtLeast(0L)
    }.getOrDefault(0L)

    fun installedContoursBytes(context: Context): Long =
        File(context.filesDir, "terrain/contours").walkTopDown()
            .filter { it.isFile && it.name == "georgia-contours.map" }
            .sumOf { it.length() }

    suspend fun ensureContoursInstalled(context: Context): File? = withContext(Dispatchers.IO) {
        val manifestBytes = runCatching {
            context.assets.open("offline/georgia-contours-manifest.json").use { it.readBytes() }
        }.getOrNull() ?: return@withContext null
        val manifest = runCatching { JSONObject(String(manifestBytes, Charsets.UTF_8)) }.getOrNull()
            ?: return@withContext null
        val fileName = manifest.optString("mapFile").takeIf { it == "georgia-contours.map" }
            ?: return@withContext null
        val expectedSize = manifest.optLong("mapFileSizeBytes", -1L).takeIf { it > 0L }
            ?: return@withContext null
        val expectedSha = manifest.optString("mapFileSha256").takeIf { it.length == 64 }
            ?: return@withContext null
        val bytes = context.assets.open("offline/$fileName").use { it.readBytes() }
        if (bytes.size.toLong() != expectedSize || sha256(bytes) != expectedSha) {
            throw IllegalStateException("Повреждён офлайн-пакет контурных линий.")
        }

        val root = File(context.filesDir, "terrain/contours")
        val directory = File(root, sha256(manifestBytes).take(12))
        if (!directory.exists() && !directory.mkdirs()) {
            throw IllegalStateException("Не удалось подготовить данные контурных линий.")
        }
        val target = File(directory, fileName)
        val marker = File(directory, ".manifest-sha256")
        val alreadyInstalled = target.isFile && target.length() == expectedSize &&
            runCatching { marker.readText() }.getOrNull() == sha256(manifestBytes)
        if (!alreadyInstalled) {
            val temporary = File(directory, "$fileName.tmp")
            temporary.writeBytes(bytes)
            if (target.exists() && !target.delete()) {
                temporary.delete()
                throw IllegalStateException("Не удалось заменить пакет контурных линий.")
            }
            if (!temporary.renameTo(target)) {
                temporary.delete()
                throw IllegalStateException("Не удалось сохранить офлайн-пакет контурных линий.")
            }
            marker.writeText(sha256(manifestBytes))
        }
        root.listFiles()?.filter { it.isDirectory && it != directory }?.forEach { it.deleteRecursively() }
        target
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
}
