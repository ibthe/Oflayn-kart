package app.georgia.offline

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.georgia.offline.data.DemRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DemHillshadeInstallTest {
    @Test
    fun bundledDemTileCanBeSampledFromTheCompressedAndroidAsset() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val elevation = DemRepository.elevationAt(context, 41.5, 44.5)
        assertNotNull("The packaged DEM tile must be readable by the runtime repository", elevation)
    }

    @Test
    fun bundledHgtArchivesInstallAndMatchTheAssetManifest() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val assetNames = context.assets.list("dem/hgt")
            ?.filter { it.endsWith(".hgt.zip") }
            .orEmpty()
        assertTrue("No bundled HGT hillshade archives were packaged", assetNames.isNotEmpty())

        val directory = DemRepository.ensureHillshadeInstalled(context)
        assertNotNull(directory)
        val installedNames = directory!!.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".hgt.zip") }
            ?.map { it.name }
            ?.toSet()
            .orEmpty()
        assertEquals(assetNames.toSet(), installedNames)
    }
}
