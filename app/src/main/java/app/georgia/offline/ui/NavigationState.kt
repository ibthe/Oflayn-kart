package app.georgia.offline.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
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

    /** Ordered intermediate stops between origin and destination. */
    val viaPoints = mutableStateListOf<NavigationTarget>()

    fun addWaypoint(point: NavigationTarget, index: Int = viaPoints.size) {
        viaPoints.add(index.coerceIn(0, viaPoints.size), point)
    }

    fun updateWaypoint(index: Int, point: NavigationTarget) {
        if (index in viaPoints.indices) viaPoints[index] = point
    }

    fun removeWaypoint(index: Int) {
        if (index in viaPoints.indices) viaPoints.removeAt(index)
    }

    fun moveWaypoint(from: Int, to: Int) {
        if (from !in viaPoints.indices || to !in viaPoints.indices || from == to) return
        val point = viaPoints.removeAt(from)
        viaPoints.add(to, point)
    }

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

    /** Starts a fresh route to a destination from the current GPS position. */
    fun startRouteTo(point: NavigationTarget) {
        // If "Route from here" was chosen, keep that explicit origin while the person
        // selects a destination from the map, Places catalogue, or saved places.
        val awaitingDestination = origin != null && target == null
        target = point
        if (!awaitingDestination) origin = null
        viaPoints.clear()
        pointSelection = null
        selectedPlaceId = null
        routeMenuVisible = true
    }

    /**
     * Initial planning may use the explicitly selected origin. A reroute must always
     * use the latest GPS fix, otherwise it sends the person back to the old start.
     */
    fun resolveRouteStart(
        currentLatitude: Double?,
        currentLongitude: Double?,
        rerouting: Boolean
    ): Pair<Double, Double>? {
        val current = if (currentLatitude != null && currentLongitude != null) {
            currentLatitude to currentLongitude
        } else {
            null
        }
        return if (rerouting) current else origin?.let { it.latitude to it.longitude } ?: current
    }

    /** Selects a new route origin and waits for the person to choose a destination. */
    fun startRouteFrom(point: NavigationTarget) {
        target = null
        origin = point
        viaPoints.clear()
        pointSelection = null
        selectedPlaceId = null
        routeMenuVisible = false
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
        viaPoints.reverse()
        pointSelection = null
    }

    fun clearRoute() {
        target = null
        origin = null
        viaPoints.clear()
        pointSelection = null
        routeMenuVisible = false
    }
}
