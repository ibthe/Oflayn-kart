package app.georgia.offline.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Neutral ink-and-paper palette. The only chromatic colour is the red of the
// Georgian flag, used sparingly for the active tab and the offline indicator.
private val LightColors = lightColorScheme(
    primary = Color(0xFF14171C), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE9EBEF), onPrimaryContainer = Color(0xFF14171C),
    secondary = Color(0xFF4B5563), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE9EBEF), onSecondaryContainer = Color(0xFF14171C),
    tertiary = Color(0xFFE02D3C), onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFDE8EA), onTertiaryContainer = Color(0xFF7A1620),
    background = Color(0xFFF5F6F8), onBackground = Color(0xFF14171C),
    surface = Color(0xFFF5F6F8), onSurface = Color(0xFF14171C),
    surfaceVariant = Color(0xFFE9EBEF), onSurfaceVariant = Color(0xFF6B7280),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFFBFBFC),
    surfaceContainer = Color(0xFFF0F1F4), surfaceContainerHigh = Color(0xFFECEEF1),
    surfaceContainerHighest = Color(0xFFFFFFFF),
    outline = Color(0xFFC9CDD4), outlineVariant = Color(0xFFE3E6EB),
    error = Color(0xFFBA1A1A), onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFDECEA), onErrorContainer = Color(0xFF410002)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFF2F3F5), onPrimary = Color(0xFF0B0C0E),
    primaryContainer = Color(0xFF24282F), onPrimaryContainer = Color(0xFFF2F3F5),
    secondary = Color(0xFFB6BCC6), onSecondary = Color(0xFF0B0C0E),
    secondaryContainer = Color(0xFF24282F), onSecondaryContainer = Color(0xFFF2F3F5),
    tertiary = Color(0xFFFF5A66), onTertiary = Color(0xFF2A0508),
    tertiaryContainer = Color(0xFF3A1218), onTertiaryContainer = Color(0xFFFFD9DC),
    background = Color(0xFF0B0C0E), onBackground = Color(0xFFECEEF1),
    surface = Color(0xFF0B0C0E), onSurface = Color(0xFFECEEF1),
    surfaceVariant = Color(0xFF1E2127), onSurfaceVariant = Color(0xFF9AA1AD),
    surfaceContainerLowest = Color(0xFF070809), surfaceContainerLow = Color(0xFF101215),
    surfaceContainer = Color(0xFF131519), surfaceContainerHigh = Color(0xFF181B20),
    surfaceContainerHighest = Color(0xFF16181C),
    outline = Color(0xFF3A3F48), outlineVariant = Color(0xFF24282F),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6)
)

private val GeorgiaShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp)
)

private fun style(weight: FontWeight, size: Int, line: Int, tracking: Double) = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.sp
)

private val base = Typography()
private val GeorgiaTypography = base.copy(
    headlineMedium = style(FontWeight.Light, 34, 40, -0.9),
    headlineSmall = style(FontWeight.Light, 28, 34, -0.6),
    titleLarge = style(FontWeight.Normal, 22, 28, -0.3),
    titleMedium = style(FontWeight.Medium, 16, 22, 0.0),
    bodyLarge = style(FontWeight.Normal, 16, 24, 0.0),
    bodyMedium = style(FontWeight.Normal, 14, 21, 0.0),
    bodySmall = style(FontWeight.Normal, 12, 18, 0.1),
    labelMedium = style(FontWeight.Medium, 12, 16, 0.2),
    labelSmall = style(FontWeight.Medium, 11, 14, 0.2)
)

@Composable
fun GeorgiaTheme(themeMode: String, content: @Composable () -> Unit) {
    val useDark = when (themeMode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (useDark) DarkColors else LightColors,
        typography = GeorgiaTypography,
        shapes = GeorgiaShapes,
        content = content
    )
}
