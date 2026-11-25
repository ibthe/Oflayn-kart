package app.georgia.offline.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** A point chosen by long-press on the map, from the places list or from the saved points. */
class NavigationTarget(val name: String, val latitude: Double, val longitude: Double)

/**
 * Shared between the tabs: where the route goes ([target]), where it starts if not at the person's own
 * position ([origin]), and which saved point is highlighted on the map ([selectedPlaceId]).
 */
class NavigationState {
    var target by mutableStateOf<NavigationTarget?>(null)

    /** Route start picked with "route from here"; null means "from my GPS position". */
    var origin by mutableStateOf<NavigationTarget?>(null)

    /** The saved point that is highlighted on the map and shows its actions card. */
    var selectedPlaceId by mutableStateOf<String?>(null)

    /** Bumped to ask the map to bring the selected point into view again, e.g. from the saved-places list. */
    var focusToken by mutableIntStateOf(0)

    fun clearRoute() {
        target = null
        origin = null
    }
}
