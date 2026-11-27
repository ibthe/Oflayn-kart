package app.georgia.offline.data

import java.io.File
import java.util.Base64

data class TripPlanStop(
    val poiId: String,
    val day: Int,
    val order: Int,
    val note: String = ""
)

/** A tiny atomic file-backed itinerary store; POI IDs resolve against the bundled offline database. */
class TripPlanRepository(private val file: File) {
    @Synchronized
    fun list(): List<TripPlanStop> = read().sortedWith(compareBy<TripPlanStop> { it.day }.thenBy { it.order })

    @Synchronized
    fun add(poiId: String, day: Int = 1, note: String = ""): Boolean {
        if (!poiId.matches(Regex("(node|way|relation)/[0-9]+"))) return false
        val current = read().toMutableList()
        if (current.size >= MAX_STOPS || current.any { it.poiId == poiId }) return false
        val safeDay = day.coerceIn(1, MAX_DAYS)
        val order = current.filter { it.day == safeDay }.maxOfOrNull { it.order + 1 } ?: 0
        current += TripPlanStop(poiId, safeDay, order, note.take(MAX_NOTE_LENGTH))
        write(current)
        return true
    }

    @Synchronized
    fun remove(poiId: String) {
        val current = read().filterNot { it.poiId == poiId }
        write(normalise(current))
    }

    @Synchronized
    fun move(poiId: String, direction: Int) {
        if (direction == 0) return
        val current = read().toMutableList()
        val index = current.indexOfFirst { it.poiId == poiId }
        if (index < 0) return
        val stop = current[index]
        val dayStops = current.filter { it.day == stop.day }.sortedBy { it.order }.toMutableList()
        val position = dayStops.indexOfFirst { it.poiId == poiId }
        val target = (position + direction.coerceIn(-1, 1)).coerceIn(0, dayStops.lastIndex)
        if (target == position) return
        val other = dayStops[target]
        dayStops[target] = stop.copy(order = other.order)
        dayStops[position] = other.copy(order = stop.order)
        write(current.filterNot { it.day == stop.day } + dayStops)
    }

    @Synchronized
    fun setDay(poiId: String, day: Int) {
        val current = read().toMutableList()
        val index = current.indexOfFirst { it.poiId == poiId }
        if (index < 0) return
        current[index] = current[index].copy(day = day.coerceIn(1, MAX_DAYS))
        write(normalise(current))
    }

    @Synchronized
    fun updateNote(poiId: String, note: String) {
        val current = read().map { if (it.poiId == poiId) it.copy(note = note.take(MAX_NOTE_LENGTH)) else it }
        write(current)
    }

    @Synchronized
    fun clear() {
        file.delete()
        File(file.parentFile, file.name + ".tmp").delete()
    }

    private fun read(): List<TripPlanStop> {
        if (!file.isFile) return emptyList()
        return file.readLines(Charsets.UTF_8).mapNotNull { line ->
            val fields = line.split('\t')
            if (fields.size != 4) return@mapNotNull null
            val id = fields[0]
            val day = fields[1].toIntOrNull()?.takeIf { it in 1..MAX_DAYS } ?: return@mapNotNull null
            val order = fields[2].toIntOrNull()?.takeIf { it >= 0 } ?: return@mapNotNull null
            if (!id.matches(Regex("(node|way|relation)/[0-9]+"))) return@mapNotNull null
            val note = runCatching { String(Base64.getUrlDecoder().decode(fields[3]), Charsets.UTF_8) }.getOrDefault("")
            TripPlanStop(id, day, order, note)
        }.distinctBy { it.poiId }
    }

    private fun write(stops: List<TripPlanStop>) {
        file.parentFile?.mkdirs()
        val temporary = File(file.parentFile, file.name + ".tmp")
        val content = normalise(stops).joinToString("\n") { stop ->
            val note = Base64.getUrlEncoder().withoutPadding().encodeToString(stop.note.toByteArray(Charsets.UTF_8))
            "${stop.poiId}\t${stop.day}\t${stop.order}\t$note"
        }
        temporary.writeText(content, Charsets.UTF_8)
        if (!temporary.renameTo(file)) {
            temporary.copyTo(file, overwrite = true)
            temporary.delete()
        }
    }

    private fun normalise(stops: List<TripPlanStop>): List<TripPlanStop> =
        stops.groupBy { it.day.coerceIn(1, MAX_DAYS) }
            .toSortedMap()
            .flatMap { (day, values) ->
                values.sortedBy { it.order }.mapIndexed { index, stop -> stop.copy(day = day, order = index) }
            }

    companion object {
        const val MAX_DAYS = 30
        const val MAX_STOPS = 20
        const val MAX_NOTE_LENGTH = 500
    }
}
