package app.georgia.offline.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.georgia.offline.ui.theme.Space
import app.georgia.offline.ui.theme.accentBrush
import app.georgia.offline.ui.theme.edgeBrush
import app.georgia.offline.ui.theme.glass
import kotlinx.coroutines.delay

/** Springy press feedback: the element shrinks while a finger is on it and bounces back. */
internal fun Modifier.bounceClick(enabled: Boolean = true, onClick: () -> Unit): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.93f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "press"
    )
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(interactionSource = source, indication = null, enabled = enabled, onClick = onClick)
}

/** Hands out a growing entrance delay so blocks of one screen appear one after another. */
internal class Stagger {
    private var count = 0
    fun next(): Int = (count++ % 6) * 70
}

internal val LocalStagger = staticCompositionLocalOf<Stagger?> { null }

/** Fade and rise into place once, after [delayMillis]. */
@Composable
internal fun Modifier.appear(delayMillis: Int = 0): Modifier {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (delayMillis > 0) delay(delayMillis.toLong())
        progress.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
    }
    return this.graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * 40f
    }
}

/** Entrance animation whose delay comes from the surrounding page's [Stagger], if any. */
@Composable
internal fun Modifier.appearInOrder(): Modifier {
    val stagger = LocalStagger.current
    val delayMillis = remember { stagger?.next() ?: 0 }
    return this.appear(delayMillis)
}

/** Slowly drifting colour glows behind the whole app: the accent colours bleed in from the corners. */
@Composable
internal fun Modifier.aurora(): Modifier {
    val scheme = MaterialTheme.colorScheme
    val transition = rememberInfiniteTransition(label = "aurora")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(16000, easing = LinearEasing), RepeatMode.Reverse),
        label = "phase"
    )
    val first = scheme.primary
    val second = scheme.tertiary
    val strength = if (scheme.background.luminance() < 0.5f) 0.20f else 0.13f
    return this.drawBehind {
        val w = size.width
        val h = size.height
        drawRect(
            Brush.radialGradient(
                colors = listOf(first.copy(alpha = strength), Color.Transparent),
                center = Offset(w * (0.10f + 0.55f * phase), h * 0.03f),
                radius = w * 0.95f
            )
        )
        drawRect(
            Brush.radialGradient(
                colors = listOf(second.copy(alpha = strength * 0.85f), Color.Transparent),
                center = Offset(w * (0.95f - 0.55f * phase), h * 0.32f),
                radius = w * 0.85f
            )
        )
    }
}

/** Status dot with a ring that keeps expanding and fading, like a live indicator. */
@Composable
internal fun PulseDot(color: Color, size: Dp = 8.dp) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val wave by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1700, easing = LinearEasing), RepeatMode.Restart),
        label = "wave"
    )
    Box(Modifier.size(size * 2.4f), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(size * 2.4f)
                .graphicsLayer {
                    val scale = 0.4f + 0.6f * wave
                    scaleX = scale
                    scaleY = scale
                    alpha = 1f - wave
                }
                .background(color.copy(alpha = 0.55f), CircleShape)
        )
        Box(Modifier.size(size).background(color, CircleShape))
    }
}

/** Floating glass pill used for every control that sits on top of the map. */
@Composable
internal fun GlassPill(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    centered: Boolean = false,
    content: @Composable RowScope.() -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.let { if (onClick != null) it.bounceClick(onClick = onClick) else it },
        shape = CircleShape,
        color = scheme.glass(0.9f),
        contentColor = scheme.onSurface,
        border = BorderStroke(1.dp, scheme.edgeBrush()),
        shadowElevation = 8.dp
    ) {
        Row(
            Modifier.padding(horizontal = Space.m, vertical = Space.s),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (centered) {
                androidx.compose.foundation.layout.Arrangement.Center
            } else {
                androidx.compose.foundation.layout.Arrangement.spacedBy(Space.xs)
            },
            content = content
        )
    }
}

/** Primary call to action: accent gradient, springy press. */
@Composable
internal fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val scheme = MaterialTheme.colorScheme
    val fill = if (enabled) scheme.accentBrush() else Brush.linearGradient(listOf(scheme.surfaceVariant, scheme.surfaceVariant))
    Box(
        modifier = modifier.height(48.dp).clip(CircleShape).background(fill)
            .bounceClick(enabled = enabled, onClick = onClick)
            .padding(horizontal = Space.l),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (enabled) scheme.onPrimary else scheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

/** Selectable pill whose colours animate between states. Replaces the stock filter chip. */
@Composable
internal fun SelectPill(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val background by animateColorAsState(
        if (selected) scheme.primary else scheme.surfaceContainerHigh,
        animationSpec = tween(260),
        label = "pillBackground"
    )
    val foreground by animateColorAsState(
        if (selected) scheme.onPrimary else scheme.onSurfaceVariant,
        animationSpec = tween(260),
        label = "pillForeground"
    )
    Box(
        modifier = modifier.clip(CircleShape).background(background)
            .bounceClick(onClick = onClick)
            .padding(horizontal = Space.m, vertical = Space.xs + 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = foreground,
            maxLines = 1
        )
    }
}

/** Crosshair used by the locate button. */
@Composable
internal fun CrosshairGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(18.dp)) {
        val stroke = 2.dp.toPx()
        val c = Offset(size.width / 2, size.height / 2)
        drawCircle(color, radius = size.minDimension * 0.28f, center = c, style = Stroke(stroke))
        drawCircle(color, radius = size.minDimension * 0.09f, center = c)
        val gap = size.minDimension * 0.40f
        val end = size.minDimension / 2
        drawLine(color, Offset(c.x, c.y - end), Offset(c.x, c.y - gap), stroke)
        drawLine(color, Offset(c.x, c.y + gap), Offset(c.x, c.y + end), stroke)
        drawLine(color, Offset(c.x - end, c.y), Offset(c.x - gap, c.y), stroke)
        drawLine(color, Offset(c.x + gap, c.y), Offset(c.x + end, c.y), stroke)
    }
}

/** Line icons for the bottom navigation, drawn on a canvas so no icon library is needed. */
@Composable
internal fun NavGlyph(kind: Int, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(24.dp)) {
        val stroke = Stroke(width = 2.dp.toPx())
        val w = size.width
        val h = size.height
        when (kind) {
            // Map: location pin
            0 -> {
                val pin = Path().apply {
                    moveTo(w * 0.5f, h * 0.94f)
                    cubicTo(w * 0.14f, h * 0.58f, w * 0.16f, h * 0.30f, w * 0.30f, h * 0.18f)
                    cubicTo(w * 0.42f, h * 0.06f, w * 0.58f, h * 0.06f, w * 0.70f, h * 0.18f)
                    cubicTo(w * 0.84f, h * 0.30f, w * 0.86f, h * 0.58f, w * 0.5f, h * 0.94f)
                    close()
                }
                drawPath(pin, color, style = stroke)
                drawCircle(color, radius = w * 0.10f, center = Offset(w * 0.5f, h * 0.38f))
            }
            // Places: compass
            1 -> {
                drawCircle(color, radius = w * 0.42f, center = Offset(w / 2, h / 2), style = stroke)
                val needle = Path().apply {
                    moveTo(w * 0.64f, h * 0.36f)
                    lineTo(w * 0.54f, h * 0.54f)
                    lineTo(w * 0.36f, h * 0.64f)
                    lineTo(w * 0.46f, h * 0.46f)
                    close()
                }
                drawPath(needle, color)
            }
            // Tools: 2x2 grid
            2 -> {
                val cell = w * 0.34f
                val r = CornerRadius(w * 0.09f)
                listOf(0.12f to 0.12f, 0.54f to 0.12f, 0.12f to 0.54f, 0.54f to 0.54f).forEach { (x, y) ->
                    drawRoundRect(color, topLeft = Offset(w * x, h * y), size = Size(cell, cell), cornerRadius = r, style = stroke)
                }
            }
            // More: three dots
            else -> {
                listOf(0.2f, 0.5f, 0.8f).forEach { x ->
                    drawCircle(color, radius = w * 0.075f, center = Offset(w * x, h * 0.5f))
                }
            }
        }
    }
}
