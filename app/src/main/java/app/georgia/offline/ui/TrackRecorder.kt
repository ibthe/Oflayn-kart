package app.georgia.offline.ui

import android.location.Location
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.georgia.offline.data.GpxTrack
import app.georgia.offline.data.TrackPoint
import app.georgia.offline.domain.GeoMath

/** Foreground-only track recorder. Fixes are filtered locally before they are stored. */
class TrackRecorder {
    var recording by mutableStateOf(false)
        private set
    val points = mutableStateListOf<TrackPoint>()
    var startedAtEpochMillis by mutableStateOf(0L)
        private set
    var note by mutableStateOf("")
    var hasRecovery by mutableStateOf(false)
        private set
    private var recoveryName = "Мой трек"
    private var lastElapsedRealtimeNanos = 0L
    private var lastCheckpointNanos = 0L
    private var lastCheckpointPointCount = 0

    fun start() {
        if (!hasRecovery) {
            points.clear()
            startedAtEpochMillis = System.currentTimeMillis()
            recoveryName = "Мой трек"
            lastCheckpointNanos = 0L
            lastCheckpointPointCount = 0
        }
        hasRecovery = false
        lastElapsedRealtimeNanos = 0L
        recording = true
    }

    fun restore(track: GpxTrack) {
        points.clear()
        track.segments.forEach { points.addAll(it) }
        startedAtEpochMillis = track.createdAtEpochMillis
        recoveryName = track.name
        note = track.description.orEmpty()
        lastElapsedRealtimeNanos = 0L
        lastCheckpointNanos = 0L
        lastCheckpointPointCount = points.size
        hasRecovery = points.size >= 2
        recording = false
    }

    fun checkpointIfDue(elapsedRealtimeNanos: Long): GpxTrack? {
        if (!recording || points.size < 2) return null
        val enoughNewPoints = points.size - lastCheckpointPointCount >= CHECKPOINT_POINT_INTERVAL
        val enoughTime = lastCheckpointNanos == 0L || elapsedRealtimeNanos - lastCheckpointNanos >= CHECKPOINT_INTERVAL_NANOS
        if (!enoughNewPoints && !enoughTime) return null
        lastCheckpointNanos = elapsedRealtimeNanos
        lastCheckpointPointCount = points.size
        return GpxTrack(recoveryName, listOf(points.toList()), startedAtEpochMillis, note.ifBlank { null })
    }

    fun addFix(location: Location) {
        addFix(
            TrackPoint(
                latitude = location.latitude,
                longitude = location.longitude,
                elevationMeters = if (location.hasAltitude()) location.altitude else null,
                timeEpochMillis = location.time.takeIf { it > 0L }
            ),
            location.elapsedRealtimeNanos,
            if (location.hasAccuracy()) location.accuracy else null
        )
    }

    fun addFix(point: TrackPoint, elapsedRealtimeNanos: Long, accuracyMeters: Float? = null) {
        if (!recording || !point.latitude.isFinite() || !point.longitude.isFinite()) return
        if (point.latitude !in -90.0..90.0 || point.longitude !in -180.0..180.0) return
        if (elapsedRealtimeNanos <= lastElapsedRealtimeNanos) return
        if (accuracyMeters != null && accuracyMeters > MAX_ACCURACY_METERS) return

        val previous = points.lastOrNull()
        if (previous != null) {
            val distance = GeoMath.distanceMeters(previous.latitude, previous.longitude, point.latitude, point.longitude)
            val elapsed = (point.timeEpochMillis ?: 0L) - (previous.timeEpochMillis ?: 0L)
            if (distance < MIN_POINT_DISTANCE_METERS && elapsed < MAX_STATIONARY_INTERVAL_MILLIS) {
                lastElapsedRealtimeNanos = elapsedRealtimeNanos
                return
            }
        }
        points += point
        lastElapsedRealtimeNanos = elapsedRealtimeNanos
    }

    fun finish(name: String = recoveryName, description: String? = note): GpxTrack? {
        recording = false
        if (points.size < 2) {
            points.clear()
            return null
        }
        return GpxTrack(name.trim().ifBlank { "Мой трек" }.take(100), listOf(points.toList()), startedAtEpochMillis, description?.trim()?.take(1_000)?.ifBlank { null })
            .also {
                points.clear()
                note = ""
                hasRecovery = false
                recoveryName = "Мой трек"
                lastCheckpointNanos = 0L
                lastCheckpointPointCount = 0
            }
    }

    fun cancel() {
        recording = false
        points.clear()
        note = ""
        hasRecovery = false
        recoveryName = "Мой трек"
        lastElapsedRealtimeNanos = 0L
        lastCheckpointNanos = 0L
        lastCheckpointPointCount = 0
    }

    private companion object {
        const val MAX_ACCURACY_METERS = 50f
        const val MIN_POINT_DISTANCE_METERS = 3.0
        const val MAX_STATIONARY_INTERVAL_MILLIS = 10_000L
        const val CHECKPOINT_POINT_INTERVAL = 10
        const val CHECKPOINT_INTERVAL_NANOS = 30_000_000_000L
    }
}
