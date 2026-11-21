package app.georgia.offline

import app.georgia.offline.data.PoiRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PoiRepositoryTest {
    private val tbilisi = PoiRepository.PoiRecord(
        poiId = "node/1",
        category = "settlement",
        subcategory = "city",
        name = "Tbilisi",
        nameEn = "Tbilisi",
        nameRu = "Тбилиси",
        nameKa = "თბილისი",
        latitude = 41.7151,
        longitude = 44.8271
    )

    @Test
    fun namesForReturnsOnlyEnabledLanguagesInStableOrder() {
        assertEquals(
            listOf("Tbilisi", "Тбилиси", "თბილისი"),
            tbilisi.namesFor(setOf("en", "ru", "ka"))
        )
        assertEquals(listOf("Тбилиси"), tbilisi.namesFor(setOf("ru")))
    }

    @Test
    fun namesForFallsBackWhenSelectedTranslationIsMissing() {
        val recordWithoutRussian = tbilisi.copy(nameRu = "")
        assertEquals(listOf("Tbilisi"), recordWithoutRussian.namesFor(setOf("ru")))
    }

    @Test
    fun namesForNeverReturnsAnEmptyList() {
        assertTrue(tbilisi.namesFor(emptySet()).isNotEmpty())
    }
}
