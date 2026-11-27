package app.georgia.offline

import app.georgia.offline.data.TripPlanRepository
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TripPlanRepositoryTest {
    @get:Rule
    val temporary = TemporaryFolder()

    @Test
    fun storesStopsOfflineAndKeepsDayOrderAcrossRepositoryInstances() {
        val file = File(temporary.root, "trip-plan.tsv")
        val first = TripPlanRepository(file)
        assertTrue(first.add("node/1001"))
        assertTrue(first.add("way/2001"))
        assertTrue(first.add("relation/3001", day = 2, note = "Смотровая точка"))

        val reopened = TripPlanRepository(file)
        assertEquals(listOf("node/1001", "way/2001", "relation/3001"), reopened.list().map { it.poiId })
        assertEquals("Смотровая точка", reopened.list().last().note)
        assertFalse(reopened.add("node/1001"))
        assertFalse(reopened.add("../unsafe"))
    }

    @Test
    fun reordersStopsAndMovesThemBetweenDays() {
        val repository = TripPlanRepository(File(temporary.root, "trip-plan.tsv"))
        repository.add("node/1")
        repository.add("node/2")
        repository.add("node/3")
        repository.move("node/3", -1)
        assertEquals(listOf("node/1", "node/3", "node/2"), repository.list().map { it.poiId })

        repository.setDay("node/3", 2)
        assertEquals(listOf("node/1", "node/2", "node/3"), repository.list().map { it.poiId })
        assertEquals(2, repository.list().last().day)
    }

    @Test
    fun limitsItineraryToTwentyStops() {
        val repository = TripPlanRepository(File(temporary.root, "trip-plan.tsv"))
        repeat(20) { index -> assertTrue(repository.add("node/${index + 1}")) }
        assertFalse(repository.add("node/21"))
        assertEquals(20, repository.list().size)
    }

    @Test
    fun removesAndClearsStops() {
        val repository = TripPlanRepository(File(temporary.root, "trip-plan.tsv"))
        repository.add("node/1")
        repository.add("node/2")
        repository.remove("node/1")
        assertEquals(listOf("node/2"), repository.list().map { it.poiId })
        repository.clear()
        assertTrue(repository.list().isEmpty())
    }
}
