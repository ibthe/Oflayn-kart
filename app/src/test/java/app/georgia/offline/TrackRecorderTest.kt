package app.georgia.offline

import app.georgia.offline.data.TrackPoint
import app.georgia.offline.ui.TrackRecorder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackRecorderTest {
    @Test
    fun filtersInaccurateOutOfOrderAndNearDuplicateFixes() {
        val recorder = TrackRecorder()
        recorder.start()
        recorder.addFix(TrackPoint(41.7, 44.8, timeEpochMillis = 1_000), 1_000_000_000L, 5f)
        recorder.addFix(TrackPoint(41.700001, 44.800001, timeEpochMillis = 2_000), 2_000_000_000L, 5f)
        recorder.addFix(TrackPoint(41.71, 44.81, timeEpochMillis = 3_000), 3_000_000_000L, 80f)
        recorder.addFix(TrackPoint(41.71, 44.81, timeEpochMillis = 4_000), 500_000_000L, 5f)
        assertEquals(1, recorder.points.size)

        recorder.addFix(TrackPoint(41.7, 44.8, timeEpochMillis = 12_000), 12_000_000_000L, 5f)
        assertEquals(2, recorder.points.size)
    }

    @Test
    fun finishReturnsTrackAndStopsRecording() {
        val recorder = TrackRecorder()
        recorder.start()
        recorder.addFix(TrackPoint(41.7, 44.8, timeEpochMillis = 1_000), 1_000_000_000L)
        recorder.addFix(TrackPoint(41.71, 44.81, timeEpochMillis = 12_000), 12_000_000_000L)

        val track = recorder.finish("  Поездка  ")
        assertEquals("Поездка", track?.name)
        assertEquals(2, track?.pointCount)
        assertFalse(recorder.recording)
        assertTrue(recorder.points.isEmpty())
    }

    @Test
    fun restoredRecordingCanContinueAndKeepsItsNoteAndPoints() {
        val recorder = TrackRecorder()
        val recovery = app.georgia.offline.data.GpxTrack(
            "Interrupted",
            listOf(listOf(TrackPoint(41.7, 44.8), TrackPoint(41.71, 44.81))),
            description = "Вершина"
        )
        recorder.restore(recovery)
        assertTrue(recorder.hasRecovery)
        assertEquals(2, recorder.points.size)
        recorder.start()
        assertTrue(recorder.recording)
        assertEquals(2, recorder.points.size)
        recorder.addFix(TrackPoint(41.72, 44.82), 13_000_000_000L)
        val continued = recorder.finish()
        assertEquals(3, continued?.pointCount)
        assertEquals("Вершина", continued?.description)
    }

    @Test
    fun completedRecordingInvalidatesOutstandingCheckpoints() {
        val recorder = TrackRecorder()
        recorder.start()
        recorder.addFix(TrackPoint(41.7, 44.8), 1_000_000_000L)
        recorder.addFix(TrackPoint(41.71, 44.81), 12_000_000_000L)
        val generation = recorder.checkpointGeneration
        recorder.checkpointIfDue(12_000_000_000L)
        recorder.finish()
        assertTrue(recorder.checkpointGeneration > generation)
    }

    @Test
    fun recordingWithLessThanTwoPointsIsNotSaved() {
        val recorder = TrackRecorder()
        recorder.start()
        recorder.addFix(TrackPoint(41.7, 44.8), 1_000_000_000L)
        assertNull(recorder.finish())
    }
}
