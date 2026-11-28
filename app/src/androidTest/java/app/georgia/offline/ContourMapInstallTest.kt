package app.georgia.offline

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.georgia.offline.data.TerrainRepository
import org.mapsforge.map.reader.MapFile
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ContourMapInstallTest {
    @Test
    fun bundledContourMapInstallsAndMatchesItsManifest() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manifest = context.assets.open("offline/georgia-contours-manifest.json")
            .bufferedReader().use { it.readText() }
        assertTrue(manifest.contains("\"contourWays\""))

        val file = TerrainRepository.ensureContoursInstalled(context)
        assertNotNull(file)
        assertEquals("georgia-contours.map", file!!.name)
        assertTrue(file.isFile && file.length() > 1024L)
        assertEquals(file.length(), TerrainRepository.installedContoursBytes(context))
        MapFile(file).close()
    }
}
