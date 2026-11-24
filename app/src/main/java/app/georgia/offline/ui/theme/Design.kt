package app.georgia.offline.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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

    /** Left/right screen margin. */
    val screen = 20.dp

    /** Inner padding of every card and panel. */
    val card = 20.dp
}

/** One generous corner radius for every surface (cards, map, panels); controls use a full pill. */
object Radius {
    val card = 28.dp
}

/** The signature accent gradient: primary glowing into tertiary. */
fun ColorScheme.accentBrush(): Brush = Brush.linearGradient(listOf(primary, tertiary))

/** Hairline that is bright at the top edge and fades out, so panels look lit from above. */
fun ColorScheme.edgeBrush(): Brush = Brush.verticalGradient(
    listOf(outlineVariant.copy(alpha = 1f), outlineVariant.copy(alpha = 0.25f))
)

/** Translucent surface colour used by floating glass elements over the map. */
fun ColorScheme.glass(alpha: Float = 0.88f): Color = surfaceContainerHighest.copy(alpha = alpha)
