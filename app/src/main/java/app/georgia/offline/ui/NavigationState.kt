package app.georgia.offline.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** A point chosen by long-press on the map, from the places list or from the saved points. */
class NavigationTarget(val name: String, val latitude: Double, val longitude: Double)

/** Which end of a route the next long-pressed map point should update. */
enum class RoutePointRole { START, DESTINATION }

/**
 * Shared between the tabs: where the route goes ([target]), where it starts if not at the person's own
 * position ([origin]), and which saved point is highlighted on the map ([selectedPlaceId]).
 */
class NavigationState {
    var target by mutableStateOf<NavigationTarget?>(null)

    /** Whether the route-planning sheet is currently visible; hiding it must not discard the route. */
    var routeMenuVisible by mutableStateOf(false)

    /** Route start picked on the map; null means "from my GPS position". */
    var origin by mutableStateOf<NavigationTarget?>(null)

    /** The next long-press on the map edits this endpoint. Null keeps the default "choose destination" action. */
    var pointSelection by mutableStateOf<RoutePointRole?>(null)

    /** The saved point that is highlighted on the map and shows its actions card. */
    var selectedPlaceId by mutableStateOf<String?>(null)

    /** Bumped to ask the map to bring the selected point into view again, e.g. from the saved-places list. */
    var focusToken by mutableIntStateOf(0)

    /**
     * Applies a map point to the selected route endpoint. With no active selection, a map point
     * keeps the familiar behaviour and becomes the destination.
     */
    fun chooseMapPoint(name: String, latitude: Double, longitude: Double) {
        val point = NavigationTarget(name, latitude, longitude)
        when (pointSelection) {
            RoutePointRole.START -> origin = point
            RoutePointRole.DESTINATION, null -> target = point
        }
        pointSelection = null
        selectedPlaceId = null
        routeMenuVisible = true
    }

    fun closeRouteMenu() {
        routeMenuVisible = false
        pointSelection = null
    }

    fun openRouteMenu() {
        if (target != null) routeMenuVisible = true
    }

    /** Uses the live GPS position as the route start. */
    fun useMyLocation() {
        origin = null
        pointSelection = null
    }

    /** Reverses a route whose start and destination were both explicitly selected. */
    fun swapEndpoints() {
        val currentOrigin = origin ?: return
        val currentTarget = target ?: return
        origin = NavigationTarget(currentTarget.name, currentTarget.latitude, currentTarget.longitude)
        target = NavigationTarget(currentOrigin.name, currentOrigin.latitude, currentOrigin.longitude)
        pointSelection = null
    }

    fun clearRoute() {
        target = null
        origin = null
        pointSelection = null
        routeMenuVisible = false
    }
}
