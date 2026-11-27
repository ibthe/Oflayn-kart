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
    fun onlyMountedRemovableVolumesAreOfferedAsSdStorage() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.cacheDir, "storage-probe-test").apply { mkdirs() }
        val mounted = File(root, "mounted").apply { mkdirs() }
        val unmounted = File(root, "unmounted").apply { mkdirs() }
        val adopted = File(root, "adopted").apply { mkdirs() }
        try {
            val options = MapFileRepository.availableStorageOptions(
                context,
                arrayOf(mounted, unmounted, adopted, null),
                isRemovable = { it != adopted },
                storageState = { if (it == mounted) Environment.MEDIA_MOUNTED else Environment.MEDIA_UNMOUNTED }
            )
            assertEquals(2, options.size)
            assertEquals(MapFileRepository.INTERNAL_STORAGE_ID, options.first().id)
            assertEquals(mounted.absolutePath, options.last().id)
            assertTrue(options.last().removable)
            assertTrue(options.last().directory.absolutePath.endsWith("/mounted/maps"))
            assertEquals(options.size, options.map { it.id }.distinct().size)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun failedMoveKeepsTheOriginalMapAndCleansTemporaryCopy() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = File(context.cacheDir, "invalid-source-map.map").apply {
            writeBytes(byteArrayOf(1, 2, 3, 4, 5))
        }
        val temporary = File(context.filesDir, "maps/georgia-move.map.part")
        try {
            val failure = runCatching {
                kotlinx.coroutines.runBlocking {
                    MapFileRepository.moveImportedMap(context, source, MapFileRepository.INTERNAL_STORAGE_ID)
                }
            }.exceptionOrNull()
            assertTrue("invalid map should be rejected", failure != null)
            assertTrue("original file must be preserved", source.isFile)
            assertTrue("temporary copy must be removed", !temporary.exists())
        } finally {
            source.delete()
            temporary.delete()
        }
    }

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
