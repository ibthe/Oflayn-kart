package app.georgia.offline

import app.georgia.offline.ui.NavigationState
import app.georgia.offline.ui.NavigationTarget
import app.georgia.offline.ui.RoutePointRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class NavigationStateTest {
    @Test
    fun startingANewRouteToADestinationClearsThePreviousOriginAndWaypoints() {
        val state = NavigationState()
        state.target = app.georgia.offline.ui.NavigationTarget("Old destination", 41.7, 44.8)
        state.origin = app.georgia.offline.ui.NavigationTarget("Old origin", 41.6, 44.7)
        state.addWaypoint(app.georgia.offline.ui.NavigationTarget("Old via", 41.65, 44.75))

        state.startRouteTo(app.georgia.offline.ui.NavigationTarget("New destination", 41.8, 44.9))

        assertEquals("New destination", state.target?.name)
        assertNull(state.origin)
        assertEquals(0, state.viaPoints.size)
        assertEquals(true, state.routeMenuVisible)
    }

    @Test
    fun choosingADestinationAfterRouteFromHereKeepsTheSelectedOrigin() {
        val state = NavigationState()
        state.startRouteFrom(app.georgia.offline.ui.NavigationTarget("Selected origin", 41.6, 44.7))

        state.startRouteTo(app.georgia.offline.ui.NavigationTarget("Chosen destination", 41.8, 44.9))

        assertEquals("Selected origin", state.origin?.name)
        assertEquals("Chosen destination", state.target?.name)
        assertEquals(true, state.routeMenuVisible)
    }

    @Test
    fun startingFromASavedPlaceClearsTheOldDestinationAndWaitsForANewOne() {
        val state = NavigationState()
        state.target = app.georgia.offline.ui.NavigationTarget("Old destination", 41.7, 44.8)
        state.addWaypoint(app.georgia.offline.ui.NavigationTarget("Old via", 41.65, 44.75))

        state.startRouteFrom(app.georgia.offline.ui.NavigationTarget("New origin", 41.6, 44.7))

        assertNull(state.target)
        assertEquals("New origin", state.origin?.name)
        assertEquals(0, state.viaPoints.size)
        assertEquals(false, state.routeMenuVisible)
    }

    @Test
    fun initialRouteUsesExplicitOriginWhenOneWasSelected() {
        val state = NavigationState()
        state.startRouteFrom(NavigationTarget("Chosen start", 41.6, 44.7))

        assertEquals(41.6 to 44.7, state.resolveRouteStart(41.7, 44.8, rerouting = false))
    }

    @Test
    fun rerouteAlwaysUsesCurrentGpsInsteadOfTheOldExplicitOrigin() {
        val state = NavigationState()
        state.startRouteFrom(NavigationTarget("Old start", 41.6, 44.7))

        assertEquals(41.8 to 44.9, state.resolveRouteStart(41.8, 44.9, rerouting = true))
    }

    @Test
    fun rerouteWithoutFreshGpsDoesNotFallBackToTheOldOrigin() {
        val state = NavigationState()
        state.startRouteFrom(NavigationTarget("Old start", 41.6, 44.7))

        assertEquals(null, state.resolveRouteStart(null, null, rerouting = true))
    }

    @Test
    fun mapPointDefaultsToDestination() {
        val state = NavigationState()

        state.chooseMapPoint("Destination", 41.7, 44.8)

        assertNull(state.origin)
        assertEquals("Destination", state.target?.name)
        assertEquals(41.7, state.target!!.latitude, 0.0)
        assertEquals(44.8, state.target!!.longitude, 0.0)
    }

    @Test
    fun newMapDestinationDoesNotInheritOldIntermediateStops() {
        val state = NavigationState()
        state.target = NavigationTarget("Old destination", 41.8, 44.9)
        state.addWaypoint(NavigationTarget("Old stop", 41.7, 44.8))
        state.closeRouteMenu()

        state.chooseMapPoint("New destination", 41.9, 45.0)

        assertEquals("New destination", state.target?.name)
        assertEquals(0, state.viaPoints.size)
    }

    @Test
    fun changingDestinationInsideOpenPlannerKeepsArrangedStops() {
        val state = NavigationState()
        state.target = NavigationTarget("Old destination", 41.8, 44.9)
        state.addWaypoint(NavigationTarget("Keep this stop", 41.7, 44.8))
        state.routeMenuVisible = true

        state.chooseMapPoint("New destination", 41.9, 45.0)

        assertEquals("New destination", state.target?.name)
        assertEquals("Keep this stop", state.viaPoints.single().name)
    }

    @Test
    fun mapPointCanBeAssignedAsRouteStart() {
        val state = NavigationState()
        state.chooseMapPoint("Destination", 41.7, 44.8)
        state.pointSelection = RoutePointRole.START

        state.chooseMapPoint("Start", 41.6, 44.7)

        assertEquals("Start", state.origin?.name)
        assertEquals("Destination", state.target?.name)
        assertNull(state.pointSelection)
    }

    @Test
    fun mapPointCanReplaceDestinationWithoutChangingStart() {
        val state = NavigationState()
        state.chooseMapPoint("Start", 41.6, 44.7)
        state.origin = state.target
        state.target = null
        state.pointSelection = RoutePointRole.DESTINATION

        state.chooseMapPoint("New destination", 41.8, 44.9)

        assertEquals("Start", state.origin?.name)
        assertEquals("New destination", state.target?.name)
        assertNull(state.pointSelection)
    }

    @Test
    fun swapEndpointsReversesExplicitRoute() {
        val state = NavigationState()
        state.chooseMapPoint("Destination", 41.7, 44.8)
        state.origin = app.georgia.offline.ui.NavigationTarget("Start", 41.6, 44.7)

        state.swapEndpoints()

        assertEquals("Destination", state.origin?.name)
        assertEquals("Start", state.target?.name)
        assertEquals(41.7, state.origin!!.latitude, 0.0)
        assertEquals(41.6, state.target!!.latitude, 0.0)
    }

    @Test
    fun closingRouteMenuKeepsDestinationAndPlannedRouteContext() {
        val state = NavigationState()
        state.chooseMapPoint("Destination", 41.7, 44.8)
        state.origin = app.georgia.offline.ui.NavigationTarget("Start", 41.6, 44.7)
        state.addWaypoint(app.georgia.offline.ui.NavigationTarget("Via", 41.65, 44.75))
        val destination = state.target
        val origin = state.origin

        state.closeRouteMenu()

        assertEquals(false, state.routeMenuVisible)
        assertSame(destination, state.target)
        assertSame(origin, state.origin)
        assertEquals("Via", state.viaPoints.single().name)
    }

    @Test
    fun routeMenuCanBeReopenedAfterClosing() {
        val state = NavigationState()
        state.chooseMapPoint("Destination", 41.7, 44.8)
        state.closeRouteMenu()

        state.openRouteMenu()

        assertEquals(true, state.routeMenuVisible)
        assertEquals("Destination", state.target?.name)
    }

    @Test
    fun waypointCanBeMovedAndRemovedWithoutChangingEndpoints() {
        val state = NavigationState()
        state.target = app.georgia.offline.ui.NavigationTarget("End", 41.8, 44.9)
        state.addWaypoint(app.georgia.offline.ui.NavigationTarget("First", 41.6, 44.7))
        state.addWaypoint(app.georgia.offline.ui.NavigationTarget("Second", 41.7, 44.8))

        state.moveWaypoint(0, 1)

        assertEquals("Second", state.viaPoints[0].name)
        assertEquals("First", state.viaPoints[1].name)
        state.removeWaypoint(0)
        assertEquals(1, state.viaPoints.size)
        assertEquals("End", state.target?.name)
    }

    @Test
    fun clearingRouteRemovesAllEndpointsAndIntermediateStops() {
        val state = NavigationState()
        state.target = app.georgia.offline.ui.NavigationTarget("End", 41.8, 44.9)
        state.origin = app.georgia.offline.ui.NavigationTarget("Start", 41.6, 44.7)
        state.addWaypoint(app.georgia.offline.ui.NavigationTarget("Via", 41.7, 44.8))
        state.routeMenuVisible = true

        state.clearRoute()

        assertNull(state.target)
        assertNull(state.origin)
        assertEquals(0, state.viaPoints.size)
        assertEquals(false, state.routeMenuVisible)
    }

    @Test
    fun myLocationClearsExplicitStartAndPendingSelection() {
        val state = NavigationState()
        state.origin = app.georgia.offline.ui.NavigationTarget("Start", 41.6, 44.7)
        state.pointSelection = RoutePointRole.START

        state.useMyLocation()

        assertNull(state.origin)
        assertNull(state.pointSelection)
    }
}
