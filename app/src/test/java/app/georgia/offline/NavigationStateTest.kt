package app.georgia.offline

import app.georgia.offline.ui.NavigationState
import app.georgia.offline.ui.RoutePointRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class NavigationStateTest {
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
        val destination = state.target

        state.closeRouteMenu()

        assertEquals(false, state.routeMenuVisible)
        assertSame(destination, state.target)
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
    fun myLocationClearsExplicitStartAndPendingSelection() {
        val state = NavigationState()
        state.origin = app.georgia.offline.ui.NavigationTarget("Start", 41.6, 44.7)
        state.pointSelection = RoutePointRole.START

        state.useMyLocation()

        assertNull(state.origin)
        assertNull(state.pointSelection)
    }
}
