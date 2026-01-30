package com.oakiha.audia.presentation.viewmodel

import android.os.SystemClock
import androidx.media3.common.C
import com.oakiha.audia.data.DailyMixManager
import com.oakiha.audia.data.model.Track
import com.oakiha.audia.data.stats.PlaybackStatsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Tracks listening statistics for Tracks.
 * Extracted from PlayerViewModel to reduce its size and improve modularity.
 *
 * Responsibilities:
 * - Track active listening sessions
 * - Record play statistics when session ends
 * - Handle voluntary vs automatic plays
 */
class ListeningStatsTracker @Inject constructor(
    private val dailyMixManager: DailyMixManager,
    private val playbackStatsRepository: PlaybackStatsRepository
) {
    private var currentSession: ActiveSession? = null
    private var pendingVoluntaryTrackId: String? = null
    private var scope: CoroutineScope? = null

    /**
     * Must be called to set the coroutine scope for async operations.
     */
    fun initialize(coroutineScope: CoroutineScope) {
        scope = coroutineScope
    }

    fun onVoluntarySelection(TrackId: String) {
        pendingVoluntaryTrackId = TrackId
    }

    fun onTrackChanged(
        Track: Track?,
        positionMs: Long,
        durationMs: Long,
        isPlaying: Boolean
    ) {
        finalizeCurrentSession()
        if (Track == null) {
            return
        }

        val nowRealtime = SystemClock.elapsedRealtime()
        val nowEpoch = System.currentTimeMillis()
        val normalizedDuration = when {
            durationMs > 0 && durationMs != C.TIME_UNSET -> durationMs
            Track.duration > 0 -> Track.duration
            else -> 0L
        }

        currentSession = ActiveSession(
            TrackId = Track.id,
            totalDurationMs = normalizedDuration,
            startedAtEpochMs = nowEpoch,
            lastKnownPositionMs = positionMs.coerceAtLeast(0L),
            accumulatedListeningMs = 0L,
            lastRealtimeMs = nowRealtime,
            lastUpdateEpochMs = nowEpoch,
            isPlaying = isPlaying,
            isVoluntary = pendingVoluntaryTrackId == Track.id
        )
        if (pendingVoluntaryTrackId == Track.id) {
            pendingVoluntaryTrackId = null
        }
    }

    fun onPlayStateChanged(isPlaying: Boolean, positionMs: Long) {
        val session = currentSession ?: return
        val nowRealtime = SystemClock.elapsedRealtime()
        if (session.isPlaying) {
            session.accumulatedListeningMs += (nowRealtime - session.lastRealtimeMs).coerceAtLeast(0L)
        }
        session.isPlaying = isPlaying
        session.lastRealtimeMs = nowRealtime
        session.lastKnownPositionMs = positionMs.coerceAtLeast(0L)
        session.lastUpdateEpochMs = System.currentTimeMillis()
    }

    fun onProgress(positionMs: Long, isPlaying: Boolean) {
        val session = currentSession ?: return
        val nowRealtime = SystemClock.elapsedRealtime()
        if (session.isPlaying) {
            val delta = (nowRealtime - session.lastRealtimeMs).coerceAtLeast(0L)
            if (delta > 0) {
                session.accumulatedListeningMs += delta
            }
        }
        session.isPlaying = isPlaying
        session.lastRealtimeMs = nowRealtime
        session.lastKnownPositionMs = positionMs.coerceAtLeast(0L)
        session.lastUpdateEpochMs = System.currentTimeMillis()
    }

    fun ensureSession(
        Track: Track?,
        positionMs: Long,
        durationMs: Long,
        isPlaying: Boolean
    ) {
        if (Track == null) {
            finalizeCurrentSession()
            return
        }
        val existing = currentSession
        if (existing?.TrackId == Track.id) {
            updateDuration(durationMs)
            val nowRealtime = SystemClock.elapsedRealtime()
            if (existing.isPlaying) {
                existing.accumulatedListeningMs += (nowRealtime - existing.lastRealtimeMs).coerceAtLeast(0L)
            }
            existing.isPlaying = isPlaying
            existing.lastRealtimeMs = nowRealtime
            existing.lastKnownPositionMs = positionMs.coerceAtLeast(0L)
            existing.lastUpdateEpochMs = System.currentTimeMillis()
            return
        }
        onTrackChanged(Track, positionMs, durationMs, isPlaying)
    }

    fun updateDuration(durationMs: Long) {
        val session = currentSession ?: return
        if (durationMs > 0 && durationMs != C.TIME_UNSET) {
            session.totalDurationMs = durationMs
        }
    }

    fun finalizeCurrentSession() {
        val session = currentSession ?: return
        val nowRealtime = SystemClock.elapsedRealtime()
        if (session.isPlaying) {
            session.accumulatedListeningMs += (nowRealtime - session.lastRealtimeMs).coerceAtLeast(0L)
        }
        val totalCap = if (session.totalDurationMs > 0) session.totalDurationMs else Long.MAX_VALUE
        val listened = session.accumulatedListeningMs.coerceAtMost(totalCap).coerceAtLeast(0L)
        if (listened >= MIN_SESSION_LISTEN_MS) {
            val rawEndTimestamp = session.lastUpdateEpochMs.takeIf { it > 0L }
                ?: (session.startedAtEpochMs + listened)
            val timestamp = rawEndTimestamp
                .coerceAtLeast(session.startedAtEpochMs.coerceAtLeast(0L))
                .coerceAtMost(System.currentTimeMillis())
            val TrackId = session.TrackId
            scope?.launch(Dispatchers.IO) {
                dailyMixManager.recordPlay(
                    TrackId = TrackId,
                    TrackDurationMs = listened,
                    timestamp = timestamp
                )
                playbackStatsRepository.recordPlayback(
                    TrackId = TrackId,
                    durationMs = listened,
                    timestamp = timestamp
                )
            }
        }
        currentSession = null
        if (pendingVoluntaryTrackId == session.TrackId) {
            pendingVoluntaryTrackId = null
        }
    }

    fun onPlaybackStopped() {
        finalizeCurrentSession()
    }

    fun onCleared() {
        finalizeCurrentSession()
        scope = null
    }

    companion object {
        private val MIN_SESSION_LISTEN_MS = TimeUnit.SECONDS.toMillis(5)
    }
}

/**
 * Represents an active listening session for a Track.
 */
data class ActiveSession(
    val TrackId: String,
    var totalDurationMs: Long,
    val startedAtEpochMs: Long,
    var lastKnownPositionMs: Long,
    var accumulatedListeningMs: Long,
    var lastRealtimeMs: Long,
    var lastUpdateEpochMs: Long,
    var isPlaying: Boolean,
    val isVoluntary: Boolean
)
