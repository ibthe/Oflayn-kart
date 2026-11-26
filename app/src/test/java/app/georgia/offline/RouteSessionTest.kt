package app.georgia.offline

import app.georgia.offline.routing.Route
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
