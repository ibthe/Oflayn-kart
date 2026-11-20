package app.georgia.offline

import app.georgia.offline.domain.FeatureCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureCatalogTest {
    @Test
    fun containsAllSevenRequestedModules() {
        assertEquals(listOf("A", "B", "C", "D", "E", "F", "G"), FeatureCatalog.groups.map { it.id })
    }

    @Test
    fun containsAllFourTravelModes() {
        val routeModule = FeatureCatalog.groups.first { it.id == "C" }
        assertTrue(routeModule.features.any { it.contains("4×4") })
        val mountainModule = FeatureCatalog.groups.first { it.id == "B" }
        assertTrue(mountainModule.features.any { it.contains("велосипедные") })
    }
}
