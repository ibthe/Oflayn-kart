package app.georgia.offline.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Brightness of the interface. [DIM] sits between the two: a soft slate-grey look that is
 * easier on the eyes than pure black in a lit room and than glaring white in the evening.
 */
enum class ThemeMode(val id: String, val title: String) {
    LIGHT("light", "Светлая"),
    DIM("dim", "Сумерки"),
    DARK("dark", "Тёмная");

    val isDark: Boolean get() = this != LIGHT
}

/** What the person chose in settings; [SYSTEM] follows the phone's light/dark setting. */
val THEME_MODE_CHOICES = listOf("system" to "Авто", "light" to "Светлая", "dim" to "Сумерки", "dark" to "Тёмная")

fun resolveThemeMode(choice: String, systemDark: Boolean): ThemeMode = when (choice) {
    "light" -> ThemeMode.LIGHT
    "dim" -> ThemeMode.DIM
    "dark" -> ThemeMode.DARK
    else -> if (systemDark) ThemeMode.DARK else ThemeMode.LIGHT
}

/** Primary (the main accent) and tertiary (the second glow colour) for one brightness mode. */
private class Accent(val primary: String, val tertiary: String)

/**
 * One colour theme = one family of accents that exists in all three brightness modes.
 * Neutrals (backgrounds, text, borders) are generated from [hue] / [tint] so every
 * theme keeps the same visual rhythm. [highContrast] themes use pure black/white
 * text and strong borders instead of the soft neutrals.
 */
class AppPalette internal constructor(
    val id: String,
    val title: String,
    val highContrast: Boolean,
    private val hue: Float,
    private val tint: Float,
    private val light: Accent,
    private val dim: Accent,
    private val dark: Accent
) {
    private val cache = HashMap<ThemeMode, ColorScheme>()

    fun scheme(mode: ThemeMode): ColorScheme = cache.getOrPut(mode) { build(mode) }

    private fun build(mode: ThemeMode): ColorScheme {
        val accent = when (mode) {
            ThemeMode.LIGHT -> light
            ThemeMode.DIM -> dim
            ThemeMode.DARK -> dark
        }
        val primary = hex(accent.primary)
        val tertiary = hex(accent.tertiary)
        fun n(lightness: Float, saturation: Float = tint) = hsl(hue, saturation, lightness)

        // Lightness ladders per mode: [background, lowest, low, container, high, highest, text, textVariant, outline, outlineVariant].
        val ladder = when {
            highContrast && mode == ThemeMode.LIGHT -> floatArrayOf(1.0f, 1.0f, 0.97f, 0.94f, 0.91f, 1.0f, 0.0f, 0.16f, 0.30f, 0.38f)
            highContrast && mode == ThemeMode.DIM -> floatArrayOf(0.14f, 0.10f, 0.16f, 0.19f, 0.23f, 0.18f, 1.0f, 0.92f, 0.78f, 0.62f)
            highContrast -> floatArrayOf(0.0f, 0.0f, 0.05f, 0.08f, 0.12f, 0.06f, 1.0f, 0.93f, 0.80f, 0.64f)
            mode == ThemeMode.LIGHT -> floatArrayOf(0.965f, 1.0f, 0.985f, 0.935f, 0.91f, 1.0f, 0.10f, 0.32f, 0.55f, 0.86f)
            mode == ThemeMode.DIM -> floatArrayOf(0.20f, 0.15f, 0.23f, 0.26f, 0.30f, 0.275f, 0.92f, 0.73f, 0.52f, 0.34f)
            else -> floatArrayOf(0.045f, 0.03f, 0.07f, 0.095f, 0.12f, 0.10f, 0.93f, 0.71f, 0.45f, 0.20f)
        }
        val sat = if (highContrast) 0.0f else if (mode == ThemeMode.DIM) tint * 0.8f else tint
        val background = n(ladder[0], sat)
        val text = n(ladder[6], sat)
        val textVariant = n(ladder[7], sat)

        val dark = mode.isDark
        val primaryContainer = mix(primary, background, if (dark) 0.68f else 0.84f)
        val tertiaryContainer = mix(tertiary, background, if (dark) 0.68f else 0.84f)
        val onPrimaryContainer = if (dark) mix(primary, Color.White, 0.72f) else mix(primary, Color.Black, 0.55f)
        val onTertiaryContainer = if (dark) mix(tertiary, Color.White, 0.72f) else mix(tertiary, Color.Black, 0.55f)
        val secondary = textVariant
        val secondaryContainer = n(ladder[4], sat)
        val error = if (dark) hex("FF8A80") else hex("B3261E")
        val errorContainer = if (dark) hex("5C1A18") else hex("FDE8E6")

        val base = if (dark) darkColorScheme() else lightColorScheme()
        return base.copy(
            primary = primary,
            onPrimary = onColorFor(primary),
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            secondary = secondary,
            onSecondary = onColorFor(secondary),
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = text,
            tertiary = tertiary,
            onTertiary = onColorFor(tertiary),
            tertiaryContainer = tertiaryContainer,
            onTertiaryContainer = onTertiaryContainer,
            background = background,
            onBackground = text,
            surface = background,
            onSurface = text,
            surfaceVariant = n(ladder[4], sat),
            onSurfaceVariant = textVariant,
            surfaceContainerLowest = n(ladder[1], sat),
            surfaceContainerLow = n(ladder[2], sat),
            surfaceContainer = n(ladder[3], sat),
            surfaceContainerHigh = n(ladder[4], sat),
            surfaceContainerHighest = n(ladder[5], sat),
            outline = n(ladder[8], sat),
            outlineVariant = n(ladder[9], sat),
            error = error,
            onError = onColorFor(error),
            errorContainer = errorContainer,
            onErrorContainer = if (dark) hex("FFDAD6") else hex("410002")
        )
    }
}

/** Five colour families plus two high-contrast themes; each exists in light, dim and dark. */
val AppPalettes: List<AppPalette> = listOf(
    AppPalette(
        "mist", "Лёд", false, 225f, 0.30f,
        Accent("2547E8", "0B7BD6"), Accent("93AAFF", "4DB8FF"), Accent("8EA6FF", "4CC2FF")
    ),
    AppPalette(
        "sage", "Мята", false, 150f, 0.22f,
        Accent("0B7A55", "0F8C62"), Accent("58E0AE", "2CD6C0"), Accent("62EDB6", "38E0D0")
    ),
    AppPalette(
        "sand", "Закат", false, 20f, 0.30f,
        Accent("D13B1F", "CC5A12"), Accent("FF9A78", "FFB35C"), Accent("FF8F66", "FFB454")
    ),
    AppPalette(
        "dusk", "Аврора", false, 255f, 0.35f,
        Accent("6A3FE0", "0E8F83"), Accent("B79CFF", "3DF2D0"), Accent("B79CFF", "3DF2D0")
    ),
    AppPalette(
        "graphite", "Неон", false, 95f, 0.10f,
        Accent("3F6B00", "0A7F96"), Accent("CFFF4A", "2EE6FF"), Accent("CFFF4A", "2EE6FF")
    ),
    AppPalette(
        "hc_mono", "Контраст", true, 0f, 0f,
        Accent("0033CC", "00508F"), Accent("A9C8FF", "6FE3EA"), Accent("8CB8FF", "5CE1E6")
    ),
    AppPalette(
        "hc_amber", "Контраст+", true, 0f, 0f,
        Accent("7A2E00", "00566B"), Accent("FFC83D", "4DD0E1"), Accent("FFD60A", "00E5FF")
    )
)

/** Used until the person picks a colour theme. */
const val DEFAULT_PALETTE_ID = "mist"

fun paletteById(id: String?): AppPalette =
    AppPalettes.firstOrNull { it.id == id } ?: AppPalettes.first()

// ---- colour helpers ----

private fun hex(value: String): Color = Color(0xFF000000.toInt() or value.toLong(16).toInt())

private fun hsl(hue: Float, saturation: Float, lightness: Float): Color {
    val c = (1f - abs(2f * lightness - 1f)) * saturation
    val hp = (((hue % 360f) + 360f) % 360f) / 60f
    val x = c * (1f - abs(hp % 2f - 1f))
    val (r1, g1, b1) = when {
        hp < 1f -> Triple(c, x, 0f)
        hp < 2f -> Triple(x, c, 0f)
        hp < 3f -> Triple(0f, c, x)
        hp < 4f -> Triple(0f, x, c)
        hp < 5f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    val m = lightness - c / 2f
    return Color(min(1f, max(0f, r1 + m)), min(1f, max(0f, g1 + m)), min(1f, max(0f, b1 + m)))
}

/** [amount] of [other] mixed into [base]. */
private fun mix(base: Color, other: Color, amount: Float): Color = Color(
    red = base.red + (other.red - base.red) * amount,
    green = base.green + (other.green - base.green) * amount,
    blue = base.blue + (other.blue - base.blue) * amount
)

private fun contrast(a: Color, b: Color): Float {
    val l1 = max(a.luminance(), b.luminance())
    val l2 = min(a.luminance(), b.luminance())
    return (l1 + 0.05f) / (l2 + 0.05f)
}

/** Near-black or white, whichever reads better on [background]. */
private fun onColorFor(background: Color): Color {
    val white = Color.White
    val ink = Color(0xFF0B0B10)
    return if (contrast(background, white) >= contrast(background, ink)) white else ink
}

/** Exposed for tests: contrast ratio between text and its background. */
fun contrastRatio(foreground: Color, background: Color): Float = contrast(foreground, background)

internal fun Color.argb(): Int = toArgb()
