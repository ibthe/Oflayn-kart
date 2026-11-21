package app.georgia.offline

import app.georgia.offline.domain.PoiLabels
import org.junit.Assert.assertEquals
import org.junit.Test

class PoiLabelsTest {
    @Test
    fun transportHubsHaveRussianLabels() {
        assertEquals("Железнодорожный вокзал / станция", PoiLabels.subcategoryLabel("station"))
        assertEquals("Автовокзал / автостанция", PoiLabels.subcategoryLabel("bus_station"))
        assertEquals("Аэропорт / аэродром", PoiLabels.subcategoryLabel("aerodrome"))
    }

    @Test
    fun unknownSubcategoryFallsBackToReadableText() {
        assertEquals("some new value", PoiLabels.subcategoryLabel("some_new_value"))
    }
}
