package app.georgia.offline

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.georgia.offline.data.EmergencyContactRepository
import app.georgia.offline.data.SafetyChecklistRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SafetyChecklistRepositoryTest {
    @Test
    fun expandedChecklistAndIncidentGuidanceAreComplete() {
        assertEquals(12, SafetyChecklistRepository.items.size)
        assertEquals(SafetyChecklistRepository.items.size, SafetyChecklistRepository.items.map { it.id }.distinct().size)
        assertEquals(6, SafetyChecklistRepository.incidentSteps.size)
        assertTrue(SafetyChecklistRepository.incidentSteps.all { it.title.isNotBlank() && it.detail.isNotBlank() })
    }

    @Test
    fun emergencyContactIsValidatedAndStoredOnlyLocally() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        EmergencyContactRepository.clear(context)
        try {
            assertNull(EmergencyContactRepository.load(context))
            assertFalse(EmergencyContactRepository.save(context, "Contact", "call 112"))
            assertTrue(EmergencyContactRepository.save(context, "Family", "+995 555 123 456"))
            val contact = EmergencyContactRepository.load(context)
            assertNotNull(contact)
            assertEquals("Family", contact!!.name)
            assertEquals("+995 555 123 456", contact.phone)
        } finally {
            EmergencyContactRepository.clear(context)
        }
    }

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
