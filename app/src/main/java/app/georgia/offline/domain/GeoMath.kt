package app.georgia.offline.domain

import java.util.Locale
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Great-circle helpers for the straight-line navigator. Pure Kotlin, no Android dependencies. */
object GeoMath {
    private const val EARTH_RADIUS_METERS = 6_371_008.8

    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val deltaPhi = phi2 - phi1
        val deltaLambda = Math.toRadians(lon2 - lon1)
        val a = sin(deltaPhi / 2) * sin(deltaPhi / 2) +
            cos(phi1) * cos(phi2) * sin(deltaLambda / 2) * sin(deltaLambda / 2)
        return 2 * EARTH_RADIUS_METERS * asin(sqrt(a))
    }

    /** Initial bearing from point 1 to point 2, in degrees clockwise from north, 0 until 360. */
    fun bearingDegrees(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val deltaLambda = Math.toRadians(lon2 - lon1)
        val y = sin(deltaLambda) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(deltaLambda)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    private val points = listOf("С", "СВ", "В", "ЮВ", "Ю", "ЮЗ", "З", "СЗ")

    fun compassPoint(bearing: Double): String {
        val normalized = ((bearing % 360.0) + 360.0) % 360.0
        return points[((normalized + 22.5) / 45.0).toInt() % 8]
    }

    fun formatDistance(meters: Double): String = when {
        meters < 100.0 -> "${meters.toInt()} м"
        meters < 1000.0 -> "${(meters / 10.0).toInt() * 10} м"
        meters < 10_000.0 -> String.format(Locale.US, "%.1f", meters / 1000.0).replace('.', ',') + " км"
        else -> "${(meters / 1000.0).toInt()} км"
    }
}
