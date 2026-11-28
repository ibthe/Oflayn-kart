package app.georgia.offline

import app.georgia.offline.data.LocalSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MapThemeTest {
    @Test
    fun mapStyleChoicesHaveUniquePersistedIds() {
        assertEquals(LocalSettings.MAP_THEME_CHOICES.size, LocalSettings.MAP_THEMES.size)
        assertTrue(LocalSettings.MAP_THEMES.contains("classic"))
        assertTrue(LocalSettings.MAP_THEMES.contains("oflayn_natural"))
        assertTrue(LocalSettings.MAP_THEMES.contains("oflayn_natural_dark"))
    }
}
