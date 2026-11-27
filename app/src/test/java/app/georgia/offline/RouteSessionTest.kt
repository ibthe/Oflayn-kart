package app.georgia.offline

import app.georgia.offline.routing.Route
import app.georgia.offline.routing.Maneuver
import app.georgia.offline.routing.RouteStep
import app.georgia.offline.routing.RoutingProfile
import app.georgia.offline.ui.RouteSession
import app.georgia.offline.ui.TrackingEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class RouteSessionTest {
    private fun simpleRoute() = Route(
        profile = RoutingProfile.CAR,
        points = doubleArrayOf(0.0, 0.0, 0.0, 0.01),
        durationSeconds = 120.0,
        steps = emptyList()
    )

    @Test
    fun beingBesideTheFinalRouteSegmentDoesNotCountAsArrival() {
        val session = RouteSession().apply {
            route = simpleRoute()
            beginNavigation()
        }

        val event = session.update(
            lat = 0.002,
            lon = 0.01,
            nowMillis = 1_000L
        )

        assertNotEquals(TrackingEvent.ARRIVED, event)
    }

    @Test
    fun rerouteKeepsOnlyIntermediateStopsStillAheadOfCurrentProgress() {
        val route = Route(
            profile = RoutingProfile.CAR,
            points = doubleArrayOf(0.0, 0.0, 0.0, 0.01),
            durationSeconds = 120.0,
            steps = listOf(
                RouteStep(Maneuver.WAYPOINT, "First stop", 300.0, 0.0, 0.003),
                RouteStep(Maneuver.WAYPOINT, "Second stop", 800.0, 0.0, 0.007),
                RouteStep(Maneuver.ARRIVE, "Arrive", 1_111.0, 0.0, 0.01)
            )
        )
        val session = RouteSession().apply { this.route = route }

        assertEquals(listOf(0.003 to 0.0, 0.007 to 0.0).map { it.second to it.first }, session.remainingWaypoints())

        session.update(lat = 0.0, lon = 0.005, nowMillis = 1_000L)

        assertEquals(listOf(0.0 to 0.007), session.remainingWaypoints())
    }

    @Test
    fun arrivingWithinTwentyFiveMetersOfTheDestinationIsRecognized() {
        val session = RouteSession().apply {
            route = simpleRoute()
            beginNavigation()
        }

        val event = session.update(
            lat = 0.00005,
            lon = 0.01,
            nowMillis = 1_000L
        )

        assertEquals(TrackingEvent.ARRIVED, event)
    }
}
