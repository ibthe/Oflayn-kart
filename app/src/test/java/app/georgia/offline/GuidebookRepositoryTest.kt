package app.georgia.offline

import app.georgia.offline.data.GuidebookRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuidebookRepositoryTest {
    @Test
    fun everyRegionHasLinkedPlacesRoutesAndPracticalServiceNotes() {
        val regions = GuidebookRepository.regions
        assertEquals(8, regions.size)
        assertEquals(regions.size, regions.map { it.id }.distinct().size)
        regions.forEach { region ->
            assertTrue(region.title.isNotBlank() && region.overview.isNotBlank())
            assertTrue(region.bestSeason.isNotBlank() && region.roadAdvice.isNotBlank())
            assertTrue("places missing for ${region.id}", region.places.size >= 4)
            assertEquals(region.places.size, region.places.map { it.id }.distinct().size)
            assertTrue("routes missing for ${region.id}", region.routes.isNotEmpty())
            assertTrue("service notes missing for ${region.id}", region.services.size >= 3)
            val placeIds = region.places.map { it.id }.toSet()
            assertTrue(region.places.all { it.name.isNotBlank() && it.category.isNotBlank() &&
                it.description.isNotBlank() && it.searchQuery.isNotBlank() })
            assertTrue(region.routes.all { route ->
                route.title.isNotBlank() && route.advice.isNotBlank() &&
                    route.stopIds.isNotEmpty() && route.stopIds.all { it in placeIds }
            })
            assertTrue(region.services.all { it.title.isNotBlank() && it.advice.isNotBlank() })
        }
    }

    @Test
    fun tushetiWarnsAboutAbanoAndNoGuidebookEntryClaimsLiveAccess() {
        val tusheti = GuidebookRepository.regions.first { it.id == "tusheti" }
        assertTrue(tusheti.roadAdvice.contains("Абано"))
        assertTrue(tusheti.roadAdvice.contains("официальные ограничения"))
        assertFalse(GuidebookRepository.regions.any {
            it.roadAdvice.contains("дорога открыта сейчас", ignoreCase = true) ||
                it.routes.any { route -> route.advice.contains("проезд открыт сейчас", ignoreCase = true) }
        })
    }
}
