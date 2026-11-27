package app.georgia.offline

import app.georgia.offline.data.GuidebookRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuidebookRepositoryTest {
    @Test
    fun hasUniqueOfflineRegionsWithPracticalTravelNotes() {
        val regions = GuidebookRepository.regions
        assertEquals(8, regions.size)
        assertEquals(regions.size, regions.map { it.id }.distinct().size)
        assertTrue(regions.all {
            it.title.isNotBlank() && it.overview.isNotBlank() &&
                it.highlights.isNotEmpty() && it.bestSeason.isNotBlank() && it.roadAdvice.isNotBlank()
        })
        val tusheti = regions.first { it.id == "tusheti" }
        assertTrue(tusheti.roadAdvice.contains("Абано"))
        assertTrue(tusheti.roadAdvice.contains("официальные ограничения"))
        assertFalse(regions.any { it.roadAdvice.contains("дорога открыта сейчас", ignoreCase = true) })
    }
}
