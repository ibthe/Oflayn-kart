package app.georgia.offline

import app.georgia.offline.domain.GeoMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoMathTest {
    @Test
    fun distanceTbilisiToBatumiIsAbout265Km() {
        val meters = GeoMath.distanceMeters(41.7151, 44.8271, 41.6168, 41.6367)
        assertTrue("got $meters", meters in 264_000.0..266_500.0)
    }

    @Test
    fun oneDegreeOfLongitudeAtEquatorIsAbout111Km() {
        assertEquals(111_195.0, GeoMath.distanceMeters(0.0, 0.0, 0.0, 1.0), 50.0)
    }

    @Test
    fun bearingsFollowCompass() {
        assertEquals(90.0, GeoMath.bearingDegrees(0.0, 0.0, 0.0, 1.0), 0.01)
        assertEquals(0.0, GeoMath.bearingDegrees(0.0, 0.0, 1.0, 0.0), 0.01)
        assertEquals(180.0, GeoMath.bearingDegrees(1.0, 0.0, 0.0, 0.0), 0.01)
        assertEquals(268.7, GeoMath.bearingDegrees(41.7151, 44.8271, 41.6168, 41.6367), 0.2)
    }

    @Test
    fun compassPointsAreRussianAndWrapAround() {
        assertEquals("С", GeoMath.compassPoint(0.0))
        assertEquals("С", GeoMath.compassPoint(350.0))
        assertEquals("СВ", GeoMath.compassPoint(44.0))
        assertEquals("В", GeoMath.compassPoint(90.0))
        assertEquals("ЮЗ", GeoMath.compassPoint(225.0))
        assertEquals("СЗ", GeoMath.compassPoint(315.0))
    }

    @Test
    fun distanceFormatting() {
        assertEquals("45 м", GeoMath.formatDistance(45.4))
        assertEquals("850 м", GeoMath.formatDistance(854.0))
        assertEquals("3,4 км", GeoMath.formatDistance(3_400.0))
        assertEquals("12 км", GeoMath.formatDistance(12_300.0))
    }
}
