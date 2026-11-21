package app.georgia.offline.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val GeorgiaShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(Radius.card),
    large = RoundedCornerShape(32.dp),
    extraLarge = RoundedCornerShape(40.dp)
)

// Modular type scale: 12 / 14 / 16 / 18 / 23 / 28 / 33 sp (ratio about 1.2 above 16 sp),
// line heights on a 2 dp half-grid, tracking tightened as size grows and opened for
// the smallest sizes. Only system fonts are used because the app has no network
// and bundles no font files.
private fun style(weight: FontWeight, size: Int, line: Int, tracking: Double) = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.sp
)

private val GeorgiaTypography = Typography().copy(
    headlineMedium = style(FontWeight.Light, 33, 40, -0.7),
    headlineSmall = style(FontWeight.Light, 28, 34, -0.5),
    titleLarge = style(FontWeight.Normal, 23, 30, -0.2),
    titleMedium = style(FontWeight.Medium, 18, 24, -0.1),
    titleSmall = style(FontWeight.Medium, 14, 20, 0.1),
    bodyLarge = style(FontWeight.Normal, 16, 24, 0.0),
    bodyMedium = style(FontWeight.Normal, 14, 22, 0.0),
    bodySmall = style(FontWeight.Normal, 12, 18, 0.1),
    labelLarge = style(FontWeight.Medium, 14, 20, 0.1),
    labelMedium = style(FontWeight.Medium, 12, 16, 0.2),
    labelSmall = style(FontWeight.Medium, 11, 16, 0.3)
)

@Composable
fun GeorgiaTheme(themeMode: String, palette: String = DEFAULT_PALETTE, content: @Composable () -> Unit) {
    val useDark = when (themeMode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    val selected = paletteById(palette)
    MaterialTheme(
        colorScheme = if (useDark) selected.dark else selected.light,
        typography = GeorgiaTypography,
        shapes = GeorgiaShapes,
        content = content
    )
}
