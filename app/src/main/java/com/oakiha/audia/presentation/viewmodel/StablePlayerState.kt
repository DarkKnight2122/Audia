package com.oakiha.audia.presentation.viewmodel

import androidx.compose.runtime.Immutable
import androidx.media3.common.Player
import com.oakiha.audia.data.model.Track
import com.oakiha.audia.data.model.Transcript

@Immutable
data class StablePlayerState(
    val currentTrack: Track? = null,
    val isPlaying: Boolean = false,
    val currentPosition: Long = 0L,
    val totalDuration: Long = 0L,
    val isShuffleEnabled: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val isLoadingTranscript: Boolean = false,
    val Transcript: Transcript? = null
)
