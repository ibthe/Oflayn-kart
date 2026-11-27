package app.georgia.offline

import app.georgia.offline.data.GpxTrack
import app.georgia.offline.data.TrackPoint
import app.georgia.offline.routing.Maneuver
import app.georgia.offline.routing.RoutingProfile
import app.georgia.offline.routing.TrackRouteBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackRouteBuilderTest {
    private val track = GpxTrack(
        "Mountain loop",
        listOf(listOf(
            TrackPoint(41.7000, 44.8000),
            TrackPoint(41.7010, 44.8010),
            TrackPoint(41.7020, 44.8020),
            TrackPoint(41.7030, 44.8030)
        ))
    )

    @Test
    fun buildsReturnGeometryFromNearestTrackPointBackToTrackStart() {
        val route = TrackRouteBuilder.reverseFromNearest(track, 41.7020, 44.8020, RoutingProfile.FOOT)
        assertTrue(route != null)
        route!!
        assertEquals(3, route.pointCount)
        assertEquals(41.7020, route.lat(0), 0.000001)
        assertEquals(41.7000, route.lat(route.pointCount - 1), 0.000001)
        assertEquals(Maneuver.START, route.steps.first().maneuver)
        assertEquals(Maneuver.ARRIVE, route.steps.last().maneuver)
        assertTrue(route.warnings.isNotEmpty())
    }

    @Test
    fun rejectsLocationsFarFromTrackAndLocationsAlreadyAtItsStart() {
        assertNull(TrackRouteBuilder.reverseFromNearest(track, 42.5, 44.8, RoutingProfile.CAR))
        assertNull(TrackRouteBuilder.reverseFromNearest(track, 41.7000, 44.8000, RoutingProfile.CAR))
    }

    @Test
    fun choosesTheNearestContinuousSegment() {
        val split = GpxTrack(
            "Split",
            listOf(
                listOf(TrackPoint(41.7, 44.8), TrackPoint(41.701, 44.801)),
                listOf(TrackPoint(42.0, 45.0), TrackPoint(42.001, 45.001), TrackPoint(42.002, 45.002))
            )
        )
        val route = TrackRouteBuilder.reverseFromNearest(split, 42.001, 45.001, RoutingProfile.BIKE)
        assertTrue(route != null)
        assertEquals(42.0, route!!.lat(route.pointCount - 1), 0.000001)
        assertTrue(route.warnings.any { it.contains("сегмент") })
    }
}
