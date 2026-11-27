package app.georgia.offline

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.georgia.offline.data.SafetyChecklistRepository
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SafetyChecklistRepositoryTest {
    @Test
    fun checklistPersistsOnlyKnownItemsAndCanBeReset() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        SafetyChecklistRepository.clear(context)
        try {
            assertEquals(emptySet<String>(), SafetyChecklistRepository.load(context))
            SafetyChecklistRepository.save(context, setOf("helmet", "offline_map", "unknown"))
            assertEquals(setOf("helmet", "offline_map"), SafetyChecklistRepository.load(context))
            SafetyChecklistRepository.clear(context)
            assertEquals(emptySet<String>(), SafetyChecklistRepository.load(context))
        } finally {
            SafetyChecklistRepository.clear(context)
        }
    }
}
