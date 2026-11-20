package app.georgia.offline

import app.georgia.offline.domain.GeoFormatting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class GeoFormattingTest {
    @Test fun formatsDecimalCoordinatesWithSixDecimals() {
        assertEquals("41.715137, 44.827096", GeoFormatting.decimalCoordinates(41.715137, 44.827096))
    }

    @Test fun formatsSouthernAndWesternHemisphere() {
        assertEquals("33° 51′ 24.12″ S", GeoFormatting.degreesMinutesSeconds(-33.8567, latitude = true))
        assertEquals("151° 12′ 33.12″ E", GeoFormatting.degreesMinutesSeconds(151.2092, latitude = false))
    }

    @Test fun rejectsInvalidCoordinates() {
        assertThrows(IllegalArgumentException::class.java) { GeoFormatting.decimalCoordinates(91.0, 0.0) }
        assertThrows(IllegalArgumentException::class.java) { GeoFormatting.degreesMinutesSeconds(181.0, latitude = false) }
    }

    @Test fun normalizesCompassHeading() {
        assertEquals(10f, GeoFormatting.normalizeHeading(370f))
        assertEquals(350f, GeoFormatting.normalizeHeading(-10f))
        assertEquals(0f, GeoFormatting.normalizeHeading(720f))
    }

    @Test fun mapsHeadingToCardinalDirection() {
        assertEquals("С", GeoFormatting.cardinalDirection(0f))
        assertEquals("В", GeoFormatting.cardinalDirection(90f))
        assertEquals("Ю", GeoFormatting.cardinalDirection(180f))
        assertEquals("З", GeoFormatting.cardinalDirection(270f))
    }
}
