package app.georgia.offline

import app.georgia.offline.data.LocalSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MapThemeTest {
    @Test
    fun everyMapStyleOfferedByThePickerIsAcceptedBySettings() {
        val offeredByPicker = setOf(
            "classic",
            "oflayn_natural",
            "oflayn_natural_dark",
            "outdoor",
            "motorider",
            "biker"
        )
        assertEquals(offeredByPicker, LocalSettings.MAP_THEMES)
    }

    @Test
    fun naturalThemesAreIncludedInTheSupportedMapStyles() {
        assertTrue("oflayn_natural" in LocalSettings.MAP_THEMES)
        assertTrue("oflayn_natural_dark" in LocalSettings.MAP_THEMES)
    }
}
