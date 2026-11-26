package app.georgia.offline.routing

import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

enum class Maneuver { START, WAYPOINT, SLIGHT_LEFT, LEFT, SHARP_LEFT, SLIGHT_RIGHT, RIGHT, SHARP_RIGHT, UTURN, ROUNDABOUT, ARRIVE }

/** One instruction, located [distanceFromStartMeters] along the route. */
class RouteStep(
    val maneuver: Maneuver,
    val text: String,
    val distanceFromStartMeters: Double,
    val lat: Double,
    val lon: Double
)

class RouteProjection(val segment: Int, val alongMeters: Double, val offRouteMeters: Double)

/** A computed route: geometry as interleaved lat, lon degrees plus turn-by-turn steps. */
class Route(
    val profile: RoutingProfile,
    val points: DoubleArray,
    val durationSeconds: Double,
    val steps: List<RouteStep>
) {
    val pointCount: Int = points.size / 2
    val cumulativeMeters: DoubleArray = DoubleArray(pointCount)
    val distanceMeters: Double

    init {
        for (i in 1 until pointCount) {
            cumulativeMeters[i] = cumulativeMeters[i - 1] + app.georgia.offline.domain.GeoMath.distanceMeters(
                points[2 * i - 2], points[2 * i - 1], points[2 * i], points[2 * i + 1]
            )
        }
        distanceMeters = if (pointCount > 0) cumulativeMeters[pointCount - 1] else 0.0
    }

    fun lat(i: Int) = points[2 * i]
    fun lon(i: Int) = points[2 * i + 1]

    /**
     * Closest position on the route to the given fix. Searches a window around [hintSegment] first
     * (cheap while driving along the route) and the whole route only when that window is far off.
     */
    fun project(latitude: Double, longitude: Double, hintSegment: Int = 0): RouteProjection {
        if (pointCount < 2) return RouteProjection(0, 0.0, Double.MAX_VALUE)
        val windowStart = max(0, hintSegment - 5)
        val windowEnd = min(pointCount - 2, hintSegment + 150)
        val local = scan(latitude, longitude, windowStart, windowEnd)
        return if (local.offRouteMeters <= 60.0 || (windowStart == 0 && windowEnd == pointCount - 2)) {
            local
        } else {
            scan(latitude, longitude, 0, pointCount - 2)
        }
    }

    private fun scan(latitude: Double, longitude: Double, from: Int, to: Int): RouteProjection {
        val mLat = METERS_PER_DEGREE
        val mLon = METERS_PER_DEGREE * cos(Math.toRadians(latitude))
        var bestSegment = from
        var bestT = 0.0
        var bestDistance = Double.MAX_VALUE
        for (i in from..to) {
            val ax = (lon(i) - longitude) * mLon
            val ay = (lat(i) - latitude) * mLat
            val dx = (lon(i + 1) - lon(i)) * mLon
            val dy = (lat(i + 1) - lat(i)) * mLat
            val denom = dx * dx + dy * dy
            val t = if (denom == 0.0) 0.0 else (-(ax * dx + ay * dy) / denom).coerceIn(0.0, 1.0)
            val d = hypot(ax + dx * t, ay + dy * t)
            if (d < bestDistance) {
                bestDistance = d
                bestSegment = i
                bestT = t
            }
        }
        val along = cumulativeMeters[bestSegment] + (cumulativeMeters[bestSegment + 1] - cumulativeMeters[bestSegment]) * bestT
        return RouteProjection(bestSegment, along, bestDistance)
    }

    /** First instruction still ahead of [alongMeters] (the arrival step always remains). */
    fun nextStep(alongMeters: Double): RouteStep? =
        steps.firstOrNull { it.maneuver != Maneuver.START && it.distanceFromStartMeters > alongMeters + 3.0 }

    private companion object {
        const val METERS_PER_DEGREE = 111_195.0
    }
}

enum class RouteFailure { NO_ROAD_NEAR_START, NO_ROAD_NEAR_END, NO_PATH }

class RouteResult private constructor(val route: Route?, val failure: RouteFailure?) {
    companion object {
        fun found(route: Route) = RouteResult(route, null)
        fun failed(failure: RouteFailure) = RouteResult(null, failure)
    }
}
