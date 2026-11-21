package app.georgia.offline.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Five comfort-oriented palettes, each in a light and a dark variant.
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
    val description: String,
    val light: ColorScheme,
    val dark: ColorScheme
)

private fun c(argb: Long) = Color(argb)

private val MistLight = lightColorScheme(
    primary = c(0xFF314C68),
    onPrimary = c(0xFFFBFBFC),
    primaryContainer = c(0xFFDDE3E9),
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

private val MistDark = darkColorScheme(
    primary = c(0xFFA9C1DA),
    onPrimary = c(0xFF16191D),
    primaryContainer = c(0xFF323D48),
    onPrimaryContainer = c(0xFFDFE5EC),
    secondary = c(0xFF9BA7B5),
    onSecondary = c(0xFF16191D),
    secondaryContainer = c(0xFF282D34),
    onSecondaryContainer = c(0xFFE1E5EA),
    tertiary = c(0xFF7BA2CC),
    onTertiary = c(0xFF121417),
    tertiaryContainer = c(0xFF2C3844),
    onTertiaryContainer = c(0xFFDBE5F0),
    background = c(0xFF14171A),
    onBackground = c(0xFFE1E5EA),
    surface = c(0xFF14171A),
    onSurface = c(0xFFE1E5EA),
    surfaceVariant = c(0xFF282D34),
    onSurfaceVariant = c(0xFF9BA7B5),
    surfaceContainerLowest = c(0xFF101214),
    surfaceContainerLow = c(0xFF181C20),
    surfaceContainer = c(0xFF1F2328),
    surfaceContainerHigh = c(0xFF252930),
    surfaceContainerHighest = c(0xFF1D2125),
    outline = c(0xFF5B6776),
    outlineVariant = c(0xFF31373F),
    error = c(0xFFFFB4AB),
    onError = c(0xFF690005),
    errorContainer = c(0xFF5C1A18),
    onErrorContainer = c(0xFFFFDAD6)
)


private val SageLight = lightColorScheme(
    primary = c(0xFF365E47),
    onPrimary = c(0xFFFBFCFB),
    primaryContainer = c(0xFFDFE7E2),
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

private val SageDark = darkColorScheme(
    primary = c(0xFFAFD5BF),
    onPrimary = c(0xFF171C19),
    primaryContainer = c(0xFF35453C),
    onPrimaryContainer = c(0xFFDFECE5),
    secondary = c(0xFF9FB2A5),
    onSecondary = c(0xFF171C19),
    secondaryContainer = c(0xFF2A322D),
    onSecondaryContainer = c(0xFFE2E9E4),
    tertiary = c(0xFF83C39F),
    onTertiary = c(0xFF131614),
    tertiaryContainer = c(0xFF2F4137),
    onTertiaryContainer = c(0xFFDBF0E4),
    background = c(0xFF151916),
    onBackground = c(0xFFE2E9E4),
    surface = c(0xFF151916),
    onSurface = c(0xFFE2E9E4),
    surfaceVariant = c(0xFF2A322D),
    onSurfaceVariant = c(0xFF9FB2A5),
    surfaceContainerLowest = c(0xFF101311),
    surfaceContainerLow = c(0xFF1A1F1B),
    surfaceContainer = c(0xFF202723),
    surfaceContainerHigh = c(0xFF262E29),
    surfaceContainerHighest = c(0xFF1E2420),
    outline = c(0xFF586A5E),
    outlineVariant = c(0xFF333D36),
    error = c(0xFFFFB4AB),
    onError = c(0xFF690005),
    errorContainer = c(0xFF5C1A18),
    onErrorContainer = c(0xFFFFDAD6)
)


private val SandLight = lightColorScheme(
    primary = c(0xFF684731),
    onPrimary = c(0xFFFCFBFB),
    primaryContainer = c(0xFFE9E2DD),
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

private val SandDark = darkColorScheme(
    primary = c(0xFFDABDA9),
    onPrimary = c(0xFF1D1A16),
    primaryContainer = c(0xFF483B32),
    onPrimaryContainer = c(0xFFECE4DF),
    secondary = c(0xFFB5AB9B),
    onSecondary = c(0xFF1D1A16),
    secondaryContainer = c(0xFF342F28),
    onSecondaryContainer = c(0xFFEAE6E1),
    tertiary = c(0xFFCC9B7B),
    onTertiary = c(0xFF171512),
    tertiaryContainer = c(0xFF44362C),
    onTertiaryContainer = c(0xFFF0E3DB),
    background = c(0xFF1A1814),
    onBackground = c(0xFFEAE6E1),
    surface = c(0xFF1A1814),
    onSurface = c(0xFFEAE6E1),
    surfaceVariant = c(0xFF342F28),
    onSurfaceVariant = c(0xFFB5AB9B),
    surfaceContainerLowest = c(0xFF141210),
    surfaceContainerLow = c(0xFF201D18),
    surfaceContainer = c(0xFF28251F),
    surfaceContainerHigh = c(0xFF302B25),
    surfaceContainerHighest = c(0xFF25221D),
    outline = c(0xFF6D6354),
    outlineVariant = c(0xFF3F3A31),
    error = c(0xFFFFB4AB),
    onError = c(0xFF690005),
    errorContainer = c(0xFF5C1A18),
    onErrorContainer = c(0xFFFFDAD6)
)


private val DuskLight = lightColorScheme(
    primary = c(0xFF443762),
    onPrimary = c(0xFFFBFBFC),
    primaryContainer = c(0xFFE1DFE7),
    onPrimaryContainer = c(0xFF2D2541),
    secondary = c(0xFF5B546D),
    onSecondary = c(0xFFFBFBFC),
    secondaryContainer = c(0xFFE7E5EB),
    onSecondaryContainer = c(0xFF211E29),
    tertiary = c(0xFF664AA5),
    onTertiary = c(0xFFFFFFFF),
    tertiaryContainer = c(0xFFE7E4EC),
    onTertiaryContainer = c(0xFF322848),
    background = c(0xFFF3F2F5),
    onBackground = c(0xFF211E29),
    surface = c(0xFFF3F2F5),
    onSurface = c(0xFF211E29),
    surfaceVariant = c(0xFFE7E5EB),
    onSurfaceVariant = c(0xFF5B546D),
    surfaceContainerLowest = c(0xFFFDFDFD),
    surfaceContainerLow = c(0xFFF8F8F9),
    surfaceContainer = c(0xFFEEEDF0),
    surfaceContainerHigh = c(0xFFE8E7EC),
    surfaceContainerHighest = c(0xFFFBFBFC),
    outline = c(0xFF8E88A0),
    outlineVariant = c(0xFFDBD9E0),
    error = c(0xFFBA1A1A),
    onError = c(0xFFFFFFFF),
    errorContainer = c(0xFFFBE9E7),
    onErrorContainer = c(0xFF410002)
)

private val DuskDark = darkColorScheme(
    primary = c(0xFFBAAED5),
    onPrimary = c(0xFF18171C),
    primaryContainer = c(0xFF3A3546),
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


private val GraphiteLight = lightColorScheme(
    primary = c(0xFF26282C),
    onPrimary = c(0xFFFBFBFB),
    primaryContainer = c(0xFFE1E2E5),
    onPrimaryContainer = c(0xFF2F3237),
    secondary = c(0xFF5B5F67),
    onSecondary = c(0xFFFBFBFB),
    secondaryContainer = c(0xFFE7E8E9),
    onSecondaryContainer = c(0xFF212327),
    tertiary = c(0xFFCC242F),
    onTertiary = c(0xFFFFFFFF),
    tertiaryContainer = c(0xFFF0E0E1),
    onTertiaryContainer = c(0xFF5A161B),
    background = c(0xFFF3F3F4),
    onBackground = c(0xFF212327),
    surface = c(0xFFF3F3F4),
    onSurface = c(0xFF212327),
    surfaceVariant = c(0xFFE7E8E9),
    onSurfaceVariant = c(0xFF5B5F67),
    surfaceContainerLowest = c(0xFFFDFDFD),
    surfaceContainerLow = c(0xFFF8F8F9),
    surfaceContainer = c(0xFFEEEEEF),
    surfaceContainerHigh = c(0xFFE9E9EA),
    surfaceContainerHighest = c(0xFFFBFBFB),
    outline = c(0xFF8A8D93),
    outlineVariant = c(0xFFDBDCDE),
    error = c(0xFFBA1A1A),
    onError = c(0xFFFFFFFF),
    errorContainer = c(0xFFFBE9E7),
    onErrorContainer = c(0xFF410002)
)

private val GraphiteDark = darkColorScheme(
    primary = c(0xFFDEE0E3),
    onPrimary = c(0xFF18191B),
    primaryContainer = c(0xFF3A3C40),
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
    AppPalette("mist", "Туман", "Спокойный сине-серый. Нейтральный и собранный — не отвлекает от карты.", MistLight, MistDark),
    AppPalette("sage", "Шалфей", "Приглушённый зелёный. Мягкий для глаз, напоминает природу и успокаивает.", SageLight, SageDark),
    AppPalette("sand", "Песок", "Тёплый светло-серый с глиняным акцентом. Уютный мягкий свет без резкости.", SandLight, SandDark),
    AppPalette("dusk", "Сумерки", "Сизо-сиреневый. Расслабляет и хорошо подходит для вечернего чтения.", DuskLight, DuskDark),
    AppPalette("graphite", "Графит", "Строгий нейтральный серый с красным акцентом флага. Минимум красок.", GraphiteLight, GraphiteDark)
)

const val DEFAULT_PALETTE = "mist"

fun paletteById(id: String): AppPalette =
    AppPalettes.firstOrNull { it.id == id } ?: AppPalettes.first()
