package app.georgia.offline.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Spacing scale on a 4 dp grid. Every gap in the app is one of these values, so
 * blocks relate to each other by fixed ratios (4 : 8 : 12 : 16 : 24 : 32).
 */
object Space {
    val xxs = 4.dp
    val xs = 8.dp
    val s = 12.dp
    val m = 16.dp
    val l = 24.dp
    val xl = 32.dp

    /** Left/right screen margin; equal to [card] so text inside a card aligns with the screen edge rhythm. */
    val screen = 20.dp

    /** Inner padding of every card and panel. */
    val card = 20.dp
}

/** One corner radius for every surface (cards, map, panels); controls use a full pill. */
object Radius {
    val card = 24.dp
}
