package app.georgia.offline.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class MapZoomPolicyTest {
    @Test
    fun routeOverviewWaitsSevenSecondsBeforeCloserZoom() {
        assertEquals(7_000L, ROUTE_AUTO_ZOOM_DELAY_MS)
    }

    @Test
    fun speedZoomPullsBackAtEachRidingSpeedThreshold() {
        assertEquals(16, speedBasedZoomLevel(0f).toInt())
        assertEquals(16, speedBasedZoomLevel(14.9f).toInt())
        assertEquals(14, speedBasedZoomLevel(15f).toInt())
        assertEquals(14, speedBasedZoomLevel(34.9f).toInt())
        assertEquals(13, speedBasedZoomLevel(35f).toInt())
        assertEquals(13, speedBasedZoomLevel(59.9f).toInt())
        assertEquals(12, speedBasedZoomLevel(60f).toInt())
        assertEquals(12, speedBasedZoomLevel(89.9f).toInt())
        assertEquals(11, speedBasedZoomLevel(90f).toInt())
    }
}
