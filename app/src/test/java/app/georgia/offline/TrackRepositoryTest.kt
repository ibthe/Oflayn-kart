package app.georgia.offline

import app.georgia.offline.data.GpxTrack
import app.georgia.offline.data.TrackPoint
import app.georgia.offline.data.TrackRepository
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TrackRepositoryTest {
    @get:Rule
    val temporary = TemporaryFolder()

    @Test
    fun savesListsLoadsAndDeletesLocalGpxTracks() {
        val repository = TrackRepository(File(temporary.root, "tracks"))
        val source = GpxTrack(
            "Переход",
            listOf(listOf(TrackPoint(41.7, 44.8), TrackPoint(41.8, 44.9))),
            description = "Проверка маршрута"
        )

        val id = repository.save(source)
        val listed = repository.list()
        assertEquals(1, listed.size)
        assertEquals(id, listed.single().id)
        assertEquals("Переход", listed.single().name)
        assertEquals(2, listed.single().pointCount)
        assertEquals(source.description, listed.single().description)
        assertEquals(source.name, repository.load(id).name)
        assertTrue(repository.delete(id))
        assertFalse(repository.delete(id))
        assertTrue(repository.list().isEmpty())
    }
}
