package app.georgia.offline.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Five complete themes. Ice, Mint and Sunset are light; Aurora and Neon are dark.
 *
 * Every palette pairs a saturated accent (primary) with a second glow colour (tertiary): the two
 * form the accent gradient used by buttons, the navigation bar and the route line. Backgrounds
 * stay tinted off-white / off-black so the vivid accents carry the personality while body text
 * keeps comfortable contrast.
 */
data class AppPalette(
    val id: String,
    val title: String,
    val isDark: Boolean,
    val scheme: ColorScheme
)

private fun c(argb: Long) = Color(argb)

private val MistScheme = lightColorScheme(
    primary = c(0xFF2547E8),
    onPrimary = c(0xFFFFFFFF),
    primaryContainer = c(0xFFDDE4FF),
    onPrimaryContainer = c(0xFF0E1F7A),
    secondary = c(0xFF4B5878),
    onSecondary = c(0xFFFFFFFF),
    secondaryContainer = c(0xFFE4E8F5),
    onSecondaryContainer = c(0xFF1B2340),
    tertiary = c(0xFF0B7BD6),
    onTertiary = c(0xFFFFFFFF),
    tertiaryContainer = c(0xFFD6EBFF),
    onTertiaryContainer = c(0xFF062F57),
    background = c(0xFFF3F6FD),
    onBackground = c(0xFF121A33),
    surface = c(0xFFF3F6FD),
    onSurface = c(0xFF121A33),
    surfaceVariant = c(0xFFE4E9F6),
    onSurfaceVariant = c(0xFF4B5878),
    surfaceContainerLowest = c(0xFFFFFFFF),
    surfaceContainerLow = c(0xFFF8FAFF),
    surfaceContainer = c(0xFFEBEFFA),
    surfaceContainerHigh = c(0xFFE3E8F6),
    surfaceContainerHighest = c(0xFFFFFFFF),
    outline = c(0xFF7C89AD),
    outlineVariant = c(0xFFD5DCF0),
    error = c(0xFFBA1A1A),
    onError = c(0xFFFFFFFF),
    errorContainer = c(0xFFFDE8E6),
    onErrorContainer = c(0xFF410002)
)

private val SageScheme = lightColorScheme(
    primary = c(0xFF0B7A55),
    onPrimary = c(0xFFFFFFFF),
    primaryContainer = c(0xFFCFF5E3),
    onPrimaryContainer = c(0xFF033D2A),
    secondary = c(0xFF4A6558),
    onSecondary = c(0xFFFFFFFF),
    secondaryContainer = c(0xFFDFEEE6),
    onSecondaryContainer = c(0xFF132A20),
    tertiary = c(0xFF0F8C62),
    onTertiary = c(0xFFFFFFFF),
    tertiaryContainer = c(0xFFC9F7E0),
    onTertiaryContainer = c(0xFF05402B),
    background = c(0xFFF1F8F4),
    onBackground = c(0xFF0F2219),
    surface = c(0xFFF1F8F4),
    onSurface = c(0xFF0F2219),
    surfaceVariant = c(0xFFE0EEE6),
    onSurfaceVariant = c(0xFF46604F),
    surfaceContainerLowest = c(0xFFFFFFFF),
    surfaceContainerLow = c(0xFFF7FCF9),
    surfaceContainer = c(0xFFE8F3ED),
    surfaceContainerHigh = c(0xFFDFEDE5),
    surfaceContainerHighest = c(0xFFFFFFFF),
    outline = c(0xFF6E9380),
    outlineVariant = c(0xFFCFE3D8),
    error = c(0xFFBA1A1A),
    onError = c(0xFFFFFFFF),
    errorContainer = c(0xFFFDE8E6),
    onErrorContainer = c(0xFF410002)
)

private val SandScheme = lightColorScheme(
    primary = c(0xFFD13B1F),
    onPrimary = c(0xFFFFFFFF),
    primaryContainer = c(0xFFFFE0D6),
    onPrimaryContainer = c(0xFF5C1000),
    secondary = c(0xFF7A5A4E),
    onSecondary = c(0xFFFFFFFF),
    secondaryContainer = c(0xFFF5E6DF),
    onSecondaryContainer = c(0xFF2E1810),
    tertiary = c(0xFFCC5A12),
    onTertiary = c(0xFFFFFFFF),
    tertiaryContainer = c(0xFFFFE3CC),
    onTertiaryContainer = c(0xFF4A2100),
    background = c(0xFFFFF6F1),
    onBackground = c(0xFF2A150E),
    surface = c(0xFFFFF6F1),
    onSurface = c(0xFF2A150E),
    surfaceVariant = c(0xFFF5E5DD),
    onSurfaceVariant = c(0xFF735548),
    surfaceContainerLowest = c(0xFFFFFFFF),
    surfaceContainerLow = c(0xFFFFFAF7),
    surfaceContainer = c(0xFFFBEDE6),
    surfaceContainerHigh = c(0xFFF6E4DB),
    surfaceContainerHighest = c(0xFFFFFFFF),
    outline = c(0xFFA8826F),
    outlineVariant = c(0xFFEBD6CB),
    error = c(0xFFBA1A1A),
    onError = c(0xFFFFFFFF),
    errorContainer = c(0xFFFDE8E6),
    onErrorContainer = c(0xFF410002)
)

private val DuskScheme = darkColorScheme(
    primary = c(0xFFB79CFF),
    onPrimary = c(0xFF1A0B45),
    primaryContainer = c(0xFF3A2878),
    onPrimaryContainer = c(0xFFE6DDFF),
    secondary = c(0xFFA9A3C9),
    onSecondary = c(0xFF17142B),
    secondaryContainer = c(0xFF2B2745),
    onSecondaryContainer = c(0xFFE4E0F7),
    tertiary = c(0xFF3DF2D0),
    onTertiary = c(0xFF00382F),
    tertiaryContainer = c(0xFF0E4A44),
    onTertiaryContainer = c(0xFFB8FFF0),
    background = c(0xFF0A0914),
    onBackground = c(0xFFECE9FA),
    surface = c(0xFF0A0914),
    onSurface = c(0xFFECE9FA),
    surfaceVariant = c(0xFF2B2745),
    onSurfaceVariant = c(0xFFA9A3C9),
    surfaceContainerLowest = c(0xFF07060F),
    surfaceContainerLow = c(0xFF110F1E),
    surfaceContainer = c(0xFF171428),
    surfaceContainerHigh = c(0xFF1E1A34),
    surfaceContainerHighest = c(0xFF15122A),
    outline = c(0xFF6A6490),
    outlineVariant = c(0xFF2E2A4A),
    error = c(0xFFFF8A80),
    onError = c(0xFF5C0A06),
    errorContainer = c(0xFF5C1A18),
    onErrorContainer = c(0xFFFFDAD6)
)

private val GraphiteScheme = darkColorScheme(
    primary = c(0xFFCFFF4A),
    onPrimary = c(0xFF1A2400),
    primaryContainer = c(0xFF3B4D00),
    onPrimaryContainer = c(0xFFE8FFB0),
    secondary = c(0xFFA6ADA0),
    onSecondary = c(0xFF151810),
    secondaryContainer = c(0xFF262A20),
    onSecondaryContainer = c(0xFFE4E9DC),
    tertiary = c(0xFF2EE6FF),
    onTertiary = c(0xFF00363F),
    tertiaryContainer = c(0xFF0A4A55),
    onTertiaryContainer = c(0xFFC2F6FF),
    background = c(0xFF060708),
    onBackground = c(0xFFEAEDE6),
    surface = c(0xFF060708),
    onSurface = c(0xFFEAEDE6),
    surfaceVariant = c(0xFF262A20),
    onSurfaceVariant = c(0xFFA6ADA0),
    surfaceContainerLowest = c(0xFF040505),
    surfaceContainerLow = c(0xFF0C0E0B),
    surfaceContainer = c(0xFF121510),
    surfaceContainerHigh = c(0xFF191D15),
    surfaceContainerHighest = c(0xFF101310),
    outline = c(0xFF5F665A),
    outlineVariant = c(0xFF282D22),
    error = c(0xFFFF8A80),
    onError = c(0xFF5C0A06),
    errorContainer = c(0xFF5C1A18),
    onErrorContainer = c(0xFFFFDAD6)
)

val AppPalettes: List<AppPalette> = listOf(
    AppPalette("mist", "Лёд", false, MistScheme),
    AppPalette("sage", "Мята", false, SageScheme),
    AppPalette("sand", "Закат", false, SandScheme),
    AppPalette("dusk", "Аврора", true, DuskScheme),
    AppPalette("graphite", "Неон", true, GraphiteScheme)
)

/** Used until the person picks a theme: Ice in light mode, Aurora when the phone is in dark mode. */
fun defaultPaletteId(systemDark: Boolean): String = if (systemDark) "dusk" else "mist"

fun paletteById(id: String?): AppPalette =
    AppPalettes.firstOrNull { it.id == id } ?: AppPalettes.first()
