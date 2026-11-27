package app.georgia.offline.data

import java.io.File
import java.util.UUID

data class SavedTrackInfo(
    val id: String,
    val name: String,
    val pointCount: Int,
    val modifiedAtEpochMillis: Long,
    val description: String? = null
)

/** Stores GPX files privately on the device; no network or account access is used. */
class TrackRepository(private val directory: File) {
    fun save(track: GpxTrack): String {
        require(track.pointCount >= 2) { "Для сохранения нужно не менее двух точек." }
        directory.mkdirs()
        val id = UUID.randomUUID().toString()
        val target = fileFor(id)
        val temporary = File(directory, "$id.gpx.tmp")
        temporary.writeText(GpxCodec.write(track), Charsets.UTF_8)
        if (!temporary.renameTo(target)) {
            temporary.copyTo(target, overwrite = true)
            temporary.delete()
        }
        return id
    }

    fun load(id: String): GpxTrack = fileFor(id).inputStream().use { GpxCodec.parse(it) }

    fun saveRecovery(track: GpxTrack, isCurrent: () -> Boolean = { true }) = synchronized(recoveryLock) {
        if (track.pointCount < 2 || !isCurrent()) return@synchronized
        directory.mkdirs()
        val target = File(directory, RECOVERY_FILE_NAME)
        val temporary = File(directory, "$RECOVERY_FILE_NAME.tmp")
        temporary.writeText(GpxCodec.write(track), Charsets.UTF_8)
        if (!temporary.renameTo(target)) {
            temporary.copyTo(target, overwrite = true)
            temporary.delete()
        }
    }

    fun loadRecovery(): GpxTrack? {
        val file = File(directory, RECOVERY_FILE_NAME)
        if (!file.isFile) return null
        return runCatching { file.inputStream().use { GpxCodec.parse(it, "Восстановленный трек") } }.getOrNull()
    }

    fun clearRecovery() = synchronized(recoveryLock) {
        File(directory, RECOVERY_FILE_NAME).delete()
        File(directory, "$RECOVERY_FILE_NAME.tmp").delete()
    }

    fun list(): List<SavedTrackInfo> {
        if (!directory.isDirectory) return emptyList()
        return directory.listFiles { file -> file.isFile && file.extension == "gpx" && file.name != RECOVERY_FILE_NAME }
            .orEmpty()
            .mapNotNull { file ->
                runCatching {
                    val track = file.inputStream().use { GpxCodec.parse(it, file.nameWithoutExtension) }
                    SavedTrackInfo(file.nameWithoutExtension, track.name, track.pointCount, file.lastModified(), track.description)
                }.getOrNull()
            }
            .sortedByDescending { it.modifiedAtEpochMillis }
    }

    fun delete(id: String): Boolean = fileFor(id).delete()

    private companion object {
        val recoveryLock = Any()
        const val RECOVERY_FILE_NAME = ".recording-recovery.gpx"
    }

    private fun fileFor(id: String): File {
        require(id.matches(Regex("[A-Za-z0-9-]{1,80}"))) { "Некорректный идентификатор трека." }
        return File(directory, "$id.gpx")
    }
}
