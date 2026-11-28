package app.georgia.offline

import android.graphics.Bitmap
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import app.georgia.offline.data.MapFileRepository
import app.georgia.offline.data.RasterPackageRepository
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RasterPackageRepositoryTest {
    @Test
    fun importsAndManagesAnAttributedOfflineRasterPackage() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val archive = createValidArchive(context.cacheDir)
        try {
            val installed = RasterPackageRepository.importZip(
                context, Uri.fromFile(archive), MapFileRepository.INTERNAL_STORAGE_ID
            )
            assertEquals("Test satellite", installed.name)
            assertEquals("CC BY 4.0", installed.license)
            assertEquals(1, installed.tileCount)
            assertTrue(File(installed.tileDirectory, "6/33/21.png").isFile)
            assertEquals(installed.id, RasterPackageRepository.active(context)?.id)
            assertFalse(RasterPackageRepository.isEnabled(context))

            RasterPackageRepository.setEnabled(context, true)
            RasterPackageRepository.setOpacity(context, 0.74f)
            assertTrue(RasterPackageRepository.isEnabled(context))
            assertEquals(0.74f, RasterPackageRepository.opacity(context), 0.001f)

            assertTrue(RasterPackageRepository.remove(context, installed))
            assertFalse(installed.directory.exists())
            assertNull(RasterPackageRepository.active(context))
            assertFalse(RasterPackageRepository.isEnabled(context))
        } finally {
            archive.delete()
        }
    }

    @Test
    fun rejectsZipSlipEntriesWithoutWritingOutsideThePackageRoot() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val archive = File(context.cacheDir, "unsafe-raster-pack.zip")
        ZipOutputStream(FileOutputStream(archive)).use { zip ->
            zip.putNextEntry(ZipEntry("../outside-raster-test.txt"))
            zip.write("unsafe".toByteArray())
            zip.closeEntry()
        }
        val outside = File(context.filesDir, "outside-raster-test.txt")
        try {
            val failure = runCatching {
                RasterPackageRepository.importZip(
                    context, Uri.fromFile(archive), MapFileRepository.INTERNAL_STORAGE_ID
                )
            }.exceptionOrNull()
            assertNotNull(failure)
            assertFalse(outside.exists())
            val root = File(context.filesDir, "raster-packages")
            assertTrue(root.listFiles()?.none { it.name.endsWith(".part") } != false)
        } finally {
            archive.delete()
            outside.delete()
        }
    }

    private fun createValidArchive(directory: File): File {
        val bitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        val png = ByteArrayOutputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            output.toByteArray()
        }
        bitmap.recycle()
        val manifest = """
            {
              "format": "oflayn-raster-pack",
              "version": 1,
              "name": "Test satellite",
              "attribution": "Test imagery © Example",
              "license": "CC BY 4.0",
              "tileScheme": "xyz",
              "tileSize": 256,
              "extension": "png",
              "minZoom": 6,
              "maxZoom": 6,
              "tileCount": 1,
              "bounds": [41.0, 40.0, 43.8, 46.8]
            }
        """.trimIndent()
        val archive = File(directory, "valid-raster-pack.zip")
        ZipOutputStream(FileOutputStream(archive)).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(manifest.toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("tiles/6/33/21.png"))
            zip.write(png)
            zip.closeEntry()
        }
        return archive
    }
}
