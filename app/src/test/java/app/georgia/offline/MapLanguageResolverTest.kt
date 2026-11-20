package app.georgia.offline

import app.georgia.offline.domain.MapLanguageResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MapLanguageResolverTest {
    @Test fun englishHasStablePriorityWhenSeveralLanguagesAreEnabled() {
        assertEquals("en", MapLanguageResolver.preferredLanguage(setOf("ru", "ka", "en")))
    }

    @Test fun russianIsUsedWhenEnglishIsDisabled() {
        assertEquals("ru", MapLanguageResolver.preferredLanguage(setOf("ru", "ka")))
    }

    @Test fun georgianIsUsedWhenItIsTheOnlyEnabledLanguage() {
        assertEquals("ka", MapLanguageResolver.preferredLanguage(setOf("ka")))
    }

    @Test fun noLanguageReturnsNullForMapsforgeDefault() {
        assertNull(MapLanguageResolver.preferredLanguage(emptySet()))
    }
}
