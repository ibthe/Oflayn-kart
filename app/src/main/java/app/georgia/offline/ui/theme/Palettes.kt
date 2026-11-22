package app.georgia.offline.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Five complete themes: Mist, Sand and Sage are light, Dusk and Graphite are dark.
 *
 * Generated from one lightness ladder so every palette has the same visual
 * rhythm: backgrounds are tinted off-white / off-black (never pure #FFF / #000,
 * which causes glare and halation over long sessions); body text sits at roughly
 * 11-14:1 contrast (readable but not harsh); secondary text, buttons and
 * borders meet at least 4.5:1 / 7:1 / 3:1; accents are desaturated (<= 40%).
 */
data class AppPalette(
    val id: String,
    val title: String,
    val isDark: Boolean,
    val scheme: ColorScheme
)

private fun c(argb: Long) = Color(argb)

private val MistScheme = lightColorScheme(
    primary = c(0xFF314C68),
    onPrimary = c(0xFFFBFBFC),
    primaryContainer = c(0xFFD7E0EA),
    onPrimaryContainer = c(0xFF213245),
    secondary = c(0xFF525F6F),
    onSecondary = c(0xFFFBFBFC),
    secondaryContainer = c(0xFFE5E8EB),
    onSecondaryContainer = c(0xFF1E232A),
    tertiary = c(0xFF4176AF),
    onTertiary = c(0xFFFFFFFF),
    tertiaryContainer = c(0xFFE3E8ED),
    onTertiaryContainer = c(0xFF24374C),
    background = c(0xFFF2F3F5),
    onBackground = c(0xFF1E232A),
    surface = c(0xFFF2F3F5),
    onSurface = c(0xFF1E232A),
    surfaceVariant = c(0xFFE5E8EB),
    onSurfaceVariant = c(0xFF525F6F),
    surfaceContainerLowest = c(0xFFFDFDFD),
    surfaceContainerLow = c(0xFFF8F8F9),
    surfaceContainer = c(0xFFEDEEF1),
    surfaceContainerHigh = c(0xFFE7E9EC),
    surfaceContainerHighest = c(0xFFFBFBFC),
    outline = c(0xFF808D9D),
    outlineVariant = c(0xFFD8DCE1),
    error = c(0xFFBA1A1A),
    onError = c(0xFFFFFFFF),
    errorContainer = c(0xFFFBE9E7),
    onErrorContainer = c(0xFF410002)
)

private val SageScheme = lightColorScheme(
    primary = c(0xFF365E47),
    onPrimary = c(0xFFFBFCFB),
    primaryContainer = c(0xFFD9E7DF),
    onPrimaryContainer = c(0xFF254131),
    secondary = c(0xFF54695B),
    onSecondary = c(0xFFFBFCFB),
    secondaryContainer = c(0xFFE6EAE7),
    onSecondaryContainer = c(0xFF1F2822),
    tertiary = c(0xFF489D6D),
    onTertiary = c(0xFFFFFFFF),
    tertiaryContainer = c(0xFFE4ECE8),
    onTertiaryContainer = c(0xFF294736),
    background = c(0xFFF2F5F3),
    onBackground = c(0xFF1F2822),
    surface = c(0xFFF2F5F3),
    onSurface = c(0xFF1F2822),
    surfaceVariant = c(0xFFE6EAE7),
    onSurfaceVariant = c(0xFF54695B),
    surfaceContainerLowest = c(0xFFFDFDFD),
    surfaceContainerLow = c(0xFFF8F9F8),
    surfaceContainer = c(0xFFEDF0EE),
    surfaceContainerHigh = c(0xFFE8ECE9),
    surfaceContainerHighest = c(0xFFFBFCFB),
    outline = c(0xFF7C9284),
    outlineVariant = c(0xFFD9E0DC),
    error = c(0xFFBA1A1A),
    onError = c(0xFFFFFFFF),
    errorContainer = c(0xFFFBE9E7),
    onErrorContainer = c(0xFF410002)
)

private val SandScheme = lightColorScheme(
    primary = c(0xFF684731),
    onPrimary = c(0xFFFCFBFB),
    primaryContainer = c(0xFFEADFD7),
    onPrimaryContainer = c(0xFF452F21),
    secondary = c(0xFF6D6150),
    onSecondary = c(0xFFFCFBFB),
    secondaryContainer = c(0xFFEBE9E5),
    onSecondaryContainer = c(0xFF2A251E),
    tertiary = c(0xFFAF6D41),
    onTertiary = c(0xFFFFFFFF),
    tertiaryContainer = c(0xFFEDE7E3),
    onTertiaryContainer = c(0xFF4C3424),
    background = c(0xFFF5F4F2),
    onBackground = c(0xFF2A251E),
    surface = c(0xFFF5F4F2),
    onSurface = c(0xFF2A251E),
    surfaceVariant = c(0xFFEBE9E5),
    onSurfaceVariant = c(0xFF6D6150),
    surfaceContainerLowest = c(0xFFFDFDFD),
    surfaceContainerLow = c(0xFFF9F9F8),
    surfaceContainer = c(0xFFF1EFED),
    surfaceContainerHigh = c(0xFFECEAE7),
    surfaceContainerHighest = c(0xFFFCFBFB),
    outline = c(0xFF978A78),
    outlineVariant = c(0xFFE1DDD8),
    error = c(0xFFBA1A1A),
    onError = c(0xFFFFFFFF),
    errorContainer = c(0xFFFBE9E7),
    onErrorContainer = c(0xFF410002)
)

private val DuskScheme = darkColorScheme(
    primary = c(0xFFBAAED5),
    onPrimary = c(0xFF18171C),
    primaryContainer = c(0xFF3B334C),
    onPrimaryContainer = c(0xFFE3DFEC),
    secondary = c(0xFFA39DB4),
    onSecondary = c(0xFF18171C),
    secondaryContainer = c(0xFF2C2933),
    onSecondaryContainer = c(0xFFE4E2E9),
    tertiary = c(0xFF9682C4),
    onTertiary = c(0xFF131217),
    tertiaryContainer = c(0xFF342F42),
    onTertiaryContainer = c(0xFFE1DBF0),
    background = c(0xFF161419),
    onBackground = c(0xFFE4E2E9),
    surface = c(0xFF161419),
    onSurface = c(0xFFE4E2E9),
    surfaceVariant = c(0xFF2C2933),
    onSurfaceVariant = c(0xFFA39DB4),
    surfaceContainerLowest = c(0xFF111014),
    surfaceContainerLow = c(0xFF1B191F),
    surfaceContainer = c(0xFF222028),
    surfaceContainerHigh = c(0xFF28252F),
    surfaceContainerHighest = c(0xFF1F1E25),
    outline = c(0xFF665F77),
    outlineVariant = c(0xFF35323E),
    error = c(0xFFFFB4AB),
    onError = c(0xFF690005),
    errorContainer = c(0xFF5C1A18),
    onErrorContainer = c(0xFFFFDAD6)
)

private val GraphiteScheme = darkColorScheme(
    primary = c(0xFFDEE0E3),
    onPrimary = c(0xFF18191B),
    primaryContainer = c(0xFF3D3F43),
    onPrimaryContainer = c(0xFFDFE3EC),
    secondary = c(0xFFA3A7AE),
    onSecondary = c(0xFF18191B),
    secondaryContainer = c(0xFF2C2D30),
    onSecondaryContainer = c(0xFFE3E5E8),
    tertiary = c(0xFFE2656D),
    onTertiary = c(0xFF141415),
    tertiaryContainer = c(0xFF4C2427),
    onTertiaryContainer = c(0xFFF0DBDD),
    background = c(0xFF161718),
    onBackground = c(0xFFE3E5E8),
    surface = c(0xFF161718),
    onSurface = c(0xFFE3E5E8),
    surfaceVariant = c(0xFF2C2D30),
    onSurfaceVariant = c(0xFFA3A7AE),
    surfaceContainerLowest = c(0xFF111213),
    surfaceContainerLow = c(0xFF1B1C1D),
    surfaceContainer = c(0xFF222325),
    surfaceContainerHigh = c(0xFF282A2C),
    surfaceContainerHighest = c(0xFF202122),
    outline = c(0xFF62656A),
    outlineVariant = c(0xFF36373A),
    error = c(0xFFFFB4AB),
    onError = c(0xFF690005),
    errorContainer = c(0xFF5C1A18),
    onErrorContainer = c(0xFFFFDAD6)
)

val AppPalettes: List<AppPalette> = listOf(
    AppPalette("mist", "Туман", false, MistScheme),
    AppPalette("sage", "Шалфей", false, SageScheme),
    AppPalette("sand", "Песок", false, SandScheme),
    AppPalette("dusk", "Сумерки", true, DuskScheme),
    AppPalette("graphite", "Графит", true, GraphiteScheme)
)

/** Used until the person picks a theme: Mist in light mode, Dusk when the phone is in dark mode. */
fun defaultPaletteId(systemDark: Boolean): String = if (systemDark) "dusk" else "mist"

fun paletteById(id: String?): AppPalette =
    AppPalettes.firstOrNull { it.id == id } ?: AppPalettes.first()
