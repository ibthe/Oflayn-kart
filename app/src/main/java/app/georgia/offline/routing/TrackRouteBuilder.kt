package app.georgia.offline.routing

import app.georgia.offline.data.GpxTrack
import app.georgia.offline.data.TrackPoint
import app.georgia.offline.domain.GeoMath

/** Builds a reverse route from the person's current position to the start of the nearest GPX segment. */
object TrackRouteBuilder {
    fun reverseFromNearest(
        track: GpxTrack,
        latitude: Double,
        longitude: Double,
        profile: RoutingProfile,
        maxDistanceMeters: Double = 150.0
    ): Route? {
        if (!latitude.isFinite() || !longitude.isFinite()) return null
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null

        var chosen: List<TrackPoint>? = null
        var chosenIndex = -1
        var nearest = Double.MAX_VALUE
        for (segment in track.segments) {
            if (segment.size < 2) continue
            segment.forEachIndexed { index, point ->
                val distance = GeoMath.distanceMeters(latitude, longitude, point.latitude, point.longitude)
                if (distance < nearest) {
                    nearest = distance
                    chosen = segment
                    chosenIndex = index
                }
            }
        }
        val segment = chosen ?: return null
        if (nearest > maxDistanceMeters || chosenIndex <= 0) return null

        val points = ArrayList<Pair<Double, Double>>()
        points += latitude to longitude
        for (index in chosenIndex downTo 0) {
            val point = segment[index]
            val previous = points.last()
            if (GeoMath.distanceMeters(previous.first, previous.second, point.latitude, point.longitude) >= 2.0) {
                points += point.latitude to point.longitude
            }
        }
        if (points.size < 2) return null

        val geometry = DoubleArray(points.size * 2)
        var distanceMeters = 0.0
        points.forEachIndexed { index, point ->
            geometry[index * 2] = point.first
            geometry[index * 2 + 1] = point.second
            if (index > 0) {
                val previous = points[index - 1]
                distanceMeters += GeoMath.distanceMeters(previous.first, previous.second, point.first, point.second)
            }
        }
        val first = points.first()
        val last = points.last()
        val steps = listOf(
            RouteStep(Maneuver.START, "Следуйте по выделенной линии GPX", 0.0, first.first, first.second),
            RouteStep(Maneuver.ARRIVE, "Вы вернулись к началу трека", distanceMeters, last.first, last.second)
        )
        val averageSpeed = (profile.maxSpeedMps * 0.35).coerceAtLeast(0.5)
        return Route(
            profile,
            geometry,
            distanceMeters / averageSpeed,
            steps,
            listOf("Возврат идёт по записанной линии GPX. Обычные поворотные подсказки для этого трека недоступны.")
        )
    }
}
