package app.georgia.offline.data

import java.io.File
import java.util.UUID

data class SavedTrackInfo(
    val id: String,
    val name: String,
    val pointCount: Int,
    val modifiedAtEpochMillis: Long
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

    fun load(id: String): GpxTrack = GpxCodec.parse(fileFor(id).inputStream())

    fun list(): List<SavedTrackInfo> {
        if (!directory.isDirectory) return emptyList()
        return directory.listFiles { file -> file.isFile && file.extension == "gpx" }
            .orEmpty()
            .mapNotNull { file ->
                runCatching {
                    val track = GpxCodec.parse(file.inputStream(), file.nameWithoutExtension)
                    SavedTrackInfo(file.nameWithoutExtension, track.name, track.pointCount, file.lastModified())
                }.getOrNull()
            }
            .sortedByDescending { it.modifiedAtEpochMillis }
    }

    fun delete(id: String): Boolean = fileFor(id).delete()

    private fun fileFor(id: String): File {
        require(id.matches(Regex("[A-Za-z0-9-]{1,80}"))) { "Некорректный идентификатор трека." }
        return File(directory, "$id.gpx")
    }
}
