package app.georgia.offline.domain

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Direction and distance to the Kaaba in Mecca. Pure maths, no I/O. */
object Qibla {
    const val KAABA_LATITUDE = 21.422487
    const val KAABA_LONGITUDE = 39.826206

    private const val EARTH_RADIUS_KM = 6371.0088

    /** Initial great-circle bearing from the given point to the Kaaba, 0..360° clockwise from true north. */
    fun bearing(latitude: Double, longitude: Double): Double {
        val phi1 = Math.toRadians(latitude)
        val phi2 = Math.toRadians(KAABA_LATITUDE)
        val deltaLambda = Math.toRadians(KAABA_LONGITUDE - longitude)
        val y = sin(deltaLambda) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(deltaLambda)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    /** Great-circle distance to the Kaaba in kilometres. */
    fun distanceKm(latitude: Double, longitude: Double): Double {
        val phi1 = Math.toRadians(latitude)
        val phi2 = Math.toRadians(KAABA_LATITUDE)
        val dPhi = phi2 - phi1
        val dLambda = Math.toRadians(KAABA_LONGITUDE - longitude)
        val a = sin(dPhi / 2) * sin(dPhi / 2) + cos(phi1) * cos(phi2) * sin(dLambda / 2) * sin(dLambda / 2)
        return 2 * EARTH_RADIUS_KM * atan2(sqrt(a), sqrt(1 - a))
    }

    /** Smallest signed angle from [from] to [to], in -180..180 degrees. */
    fun angleDifference(from: Double, to: Double): Double {
        var difference = (to - from) % 360.0
        if (difference > 180.0) difference -= 360.0
        if (difference < -180.0) difference += 360.0
        return difference
    }
}
