package app.georgia.offline.ui.theme

import android.app.Activity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import app.georgia.offline.R

private val GeorgiaShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(Radius.card),
    large = RoundedCornerShape(36.dp),
    extraLarge = RoundedCornerShape(44.dp)
)

// Inter (SIL OFL 1.1), bundled and subset to Latin + Cyrillic. Inter Display is the
// optically tighter cut for large sizes; Inter is tuned for body text. Georgian glyphs
// are not in Inter, so Android falls back to its system Georgian font for those.
private val InterText = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold)
)
private val InterDisplay = FontFamily(
    Font(R.font.interdisplay_light, FontWeight.Light),
    Font(R.font.interdisplay_regular, FontWeight.Normal)
)

// Scale 11 / 12 / 14 / 16 / 17 / 22 / 28 / 34 sp. Large sizes: Display cut, Light, tight tracking.
// Body: Inter with slightly negative tracking, 1.45-1.5 line height. Small labels: open tracking.
private fun text(weight: FontWeight, size: Int, line: Int, tracking: Double, family: FontFamily = InterText) = TextStyle(
    fontFamily = family,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.sp
)

private val GeorgiaTypography = Typography().copy(
    headlineMedium = text(FontWeight.SemiBold, 36, 42, -1.2),
    headlineSmall = text(FontWeight.SemiBold, 26, 32, -0.7),
    titleLarge = text(FontWeight.SemiBold, 22, 28, -0.5),
    titleMedium = text(FontWeight.SemiBold, 17, 24, -0.25),
    titleSmall = text(FontWeight.Medium, 14, 20, -0.05),
    bodyLarge = text(FontWeight.Normal, 16, 24, -0.15),
    bodyMedium = text(FontWeight.Normal, 14, 21, -0.05),
    bodySmall = text(FontWeight.Normal, 12, 18, 0.05),
    labelLarge = text(FontWeight.Medium, 14, 20, -0.05),
    labelMedium = text(FontWeight.Medium, 12, 16, 0.1),
    labelSmall = text(FontWeight.Medium, 11, 16, 0.2)
)

/**
 * One theme = a colour family ([paletteId]) in one brightness [mode]. The status-bar and
 * navigation-bar icons follow the chosen mode, not the phone's.
 */
@Composable
fun GeorgiaTheme(paletteId: String, mode: ThemeMode, content: @Composable () -> Unit) {
    val scheme = paletteById(paletteId).scheme(mode)
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !mode.isDark
                controller.isAppearanceLightNavigationBars = !mode.isDark
            }
        }
    }
    MaterialTheme(
        colorScheme = scheme,
        typography = GeorgiaTypography,
        shapes = GeorgiaShapes,
        content = content
    )
}
