package app.georgia.offline

import app.georgia.offline.domain.Qibla
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QiblaTest {
    @Test fun bearingFromTbilisiPointsSouthSouthWest() {
        // Tbilisi: Mecca lies to the south-south-west, roughly 195-200 degrees.
        val bearing = Qibla.bearing(41.7151, 44.8271)
        assertTrue("bearing was $bearing", bearing in 190.0..205.0)
    }

    @Test fun distanceFromTbilisiIsAboutThreeAndAHalfThousandKilometres() {
        val distance = Qibla.distanceKm(41.7151, 44.8271)
        assertTrue("distance was $distance", distance in 3200.0..3500.0)
    }

    @Test fun bearingFromLondonPointsSouthEast() {
        val bearing = Qibla.bearing(51.5074, -0.1278)
        assertTrue("bearing was $bearing", bearing in 115.0..125.0)
    }

    @Test fun angleDifferenceWrapsAroundNorth() {
        assertEquals(20.0, Qibla.angleDifference(350.0, 10.0), 1e-9)
        assertEquals(-20.0, Qibla.angleDifference(10.0, 350.0), 1e-9)
    }
}
