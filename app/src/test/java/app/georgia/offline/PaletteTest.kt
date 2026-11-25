package app.georgia.offline

import app.georgia.offline.ui.theme.AppPalettes
import app.georgia.offline.ui.theme.ThemeMode
import app.georgia.offline.ui.theme.contrastRatio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PaletteTest {
    @Test fun thereAreSevenThemesAndTwoAreHighContrast() {
        assertEquals(7, AppPalettes.size)
        assertEquals(2, AppPalettes.count { it.highContrast })
    }

    @Test fun bodyTextIsReadableInEveryThemeAndMode() {
        for (palette in AppPalettes) for (mode in ThemeMode.entries) {
            val scheme = palette.scheme(mode)
            val ratio = contrastRatio(scheme.onBackground, scheme.background)
            assertTrue("${palette.id}/$mode body text contrast $ratio", ratio >= 7f)
            val buttons = contrastRatio(scheme.onPrimary, scheme.primary)
            assertTrue("${palette.id}/$mode button contrast $buttons", buttons >= 4.5f)
        }
    }

    @Test fun highContrastThemesReachAAAForSecondaryText() {
        for (palette in AppPalettes.filter { it.highContrast }) for (mode in ThemeMode.entries) {
            val scheme = palette.scheme(mode)
            val ratio = contrastRatio(scheme.onSurfaceVariant, scheme.background)
            assertTrue("${palette.id}/$mode secondary text contrast $ratio", ratio >= 7f)
            val accent = contrastRatio(scheme.primary, scheme.background)
            assertTrue("${palette.id}/$mode accent contrast $accent", accent >= 7f)
        }
    }

    @Test fun accentIsLegibleAsTextOnBackgroundInEveryTheme() {
        for (palette in AppPalettes) for (mode in ThemeMode.entries) {
            val scheme = palette.scheme(mode)
            val ratio = contrastRatio(scheme.primary, scheme.background)
            assertTrue("${palette.id}/$mode accent contrast $ratio", ratio >= 3f)
        }
    }
}
