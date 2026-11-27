package app.georgia.offline

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.georgia.offline.data.MapFileRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MapStorageTest {
    @Test
    fun internalStorageIsAlwaysAvailableAndStorageIdsAreUnique() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val options = MapFileRepository.availableStorageOptions(context)
        assertTrue(options.isNotEmpty())
        assertEquals(MapFileRepository.INTERNAL_STORAGE_ID, options.first().id)
        assertEquals(options.size, options.map { it.id }.distinct().size)
        assertTrue(options.first().directory.absolutePath.startsWith(context.filesDir.absolutePath))
    }
}
