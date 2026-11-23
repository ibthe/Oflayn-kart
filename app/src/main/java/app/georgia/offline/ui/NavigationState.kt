package app.georgia.offline.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** A destination chosen by long-press on the map or from the places list. */
class NavigationTarget(val name: String, val latitude: Double, val longitude: Double)

/** Shared between the Places tab (sets the target) and the Map tab (shows it). */
class NavigationState {
    var target by mutableStateOf<NavigationTarget?>(null)
}
