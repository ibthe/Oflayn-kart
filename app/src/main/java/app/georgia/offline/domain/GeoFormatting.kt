package app.georgia.offline.domain

import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor

/** Formatting helpers for local GPS tools. These functions perform no I/O. */
object GeoFormatting {
    fun decimalCoordinates(latitude: Double, longitude: Double): String {
        require(latitude in -90.0..90.0) { "Latitude must be between -90 and 90" }
        require(longitude in -180.0..180.0) { "Longitude must be between -180 and 180" }
        return String.format(Locale.US, "%.6f, %.6f", latitude, longitude)
    }

    fun degreesMinutesSeconds(value: Double, latitude: Boolean): String {
        val limit = if (latitude) 90.0 else 180.0
        require(value in -limit..limit) { "Coordinate is outside its valid range" }
        val absolute = abs(value)
        val degrees = floor(absolute).toInt()
        val minutesValue = (absolute - degrees) * 60.0
        val minutes = floor(minutesValue).toInt()
        val seconds = (minutesValue - minutes) * 60.0
        val hemisphere = when {
            latitude && value < 0 -> "S"
            latitude -> "N"
            value < 0 -> "W"
            else -> "E"
        }
        return String.format(Locale.US, "%d° %02d′ %05.2f″ %s", degrees, minutes, seconds, hemisphere)
    }

    fun normalizeHeading(degrees: Float): Float {
        if (!degrees.isFinite()) return 0f
        return ((degrees % 360f) + 360f) % 360f
    }

    fun cardinalDirection(degrees: Float): String {
        val directions = listOf("С", "СВ", "В", "ЮВ", "Ю", "ЮЗ", "З", "СЗ")
        val normalized = normalizeHeading(degrees)
        val index = floor((normalized + 22.5f) / 45f).toInt() % directions.size
        return directions[index]
    }
}
