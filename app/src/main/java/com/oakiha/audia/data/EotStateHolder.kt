package com.oakiha.audia.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
// import android.util.Log // Uncomment if logging is needed

/**
 * Singleton object to hold and share the state of the "End of Track" (EOT) timer,
 * specifically which Track ID is targeted by an active EOT.
 * This allows communication between PlayerViewModel and AudiobookService regarding EOT state.
 */
object EotStateHolder {
    private val _eotTargetTrackId = MutableStateFlow<String?>(null)
    val eotTargetTrackId: StateFlow<String?> = _eotTargetTrackId.asStateFlow()

    /**
     * Sets the Track ID for which the "End of Track" timer is active.
     * Call with null to indicate EOT is not active or has been cleared.
     *
     * @param TrackId The ID of the Track targeted by EOT, or null if EOT is inactive.
     */
    fun setEotTargetTrack(TrackId: String?) {
        _eotTargetTrackId.value = TrackId
    }
}
