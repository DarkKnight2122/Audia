package com.oakiha.audia.presentation.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import com.oakiha.audia.data.model.Track
import com.oakiha.audia.data.repository.AudiobookRepository
import com.oakiha.audia.presentation.viewmodel.exts.DeckController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DeckState(
    val Track: Track? = null,
    val isPlaying: Boolean = false,
    val progress: Float = 0f,
    val volume: Float = 1f,
    val speed: Float = 1f,
    val stemWaveforms: Map<String, List<Int>> = emptyMap()
)

data class MashupUiState(
    val deck1: DeckState = DeckState(),
    val deck2: DeckState = DeckState(),
    val crossfaderValue: Float = 0f,
    val allTracks: List<Track> = emptyList(),
    val showTrackPickerForDeck: Int? = null
)

@HiltViewModel
class MashupViewModel @Inject constructor(
    private val application: Application,
    private val AudiobookRepository: AudiobookRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MashupUiState())
    val uiState = _uiState.asStateFlow()

    private lateinit var deck1Controller: DeckController
    private lateinit var deck2Controller: DeckController

    private var progressJob: Job? = null

    init {
        initializeDecks()
        loadAllTracks()
        startProgressUpdater()
    }

    private fun initializeDecks() {
        deck1Controller = DeckController(application)
        deck2Controller = DeckController(application)
    }

    private fun loadAllTracks() {
        viewModelScope.launch {
            AudiobookRepository.getAudioFiles().collect { Tracks ->
                _uiState.update { it.copy(allTracks = Tracks) }
            }
        }
    }

    fun loadTrack(deck: Int, Track: Track) {
        updateDeckState(deck) { it.copy(Track = Track) }
        val TrackUri = Uri.parse(Track.contentUriString)
        val controller = if (deck == 1) deck1Controller else deck2Controller
        controller.loadTrack(TrackUri)
        controller.player?.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updateDeckState(deck) { it.copy(isPlaying = isPlaying) }
            }
        })
        closeTrackPicker()
    }

    private fun updateDeckState(deck: Int, update: (DeckState) -> DeckState) {
        if (deck == 1) _uiState.update { it.copy(deck1 = update(it.deck1)) }
        else _uiState.update { it.copy(deck2 = update(it.deck2)) }
    }

    fun playPause(deck: Int) { if (deck == 1) deck1Controller.playPause() else deck2Controller.playPause() }
    fun seek(deck: Int, progress: Float) { if (deck == 1) deck1Controller.seek(progress) else deck2Controller.seek(progress) }
    fun nudge(deck: Int, amountMs: Long) { if (deck == 1) deck1Controller.nudge(amountMs) else deck2Controller.nudge(amountMs) }

    fun setVolume(deck: Int, volume: Float) {
        updateDeckState(deck) { it.copy(volume = volume.coerceIn(0f, 1f)) }
        updateCrossfaderAndVolumes()
    }

    fun onCrossfaderChange(value: Float) {
        _uiState.update { it.copy(crossfaderValue = value) }
        updateCrossfaderAndVolumes()
    }

    fun setSpeed(deck: Int, speed: Float) {
        val safeSpeed = speed.coerceIn(0.5f, 2.0f)
        if (deck == 1) deck1Controller.setSpeed(safeSpeed) else deck2Controller.setSpeed(safeSpeed)
        updateDeckState(deck) { it.copy(speed = safeSpeed) }
    }

    private fun updateCrossfaderAndVolumes() {
        val state = _uiState.value
        val vol1Multiplier = (1f - ((state.crossfaderValue + 1f) / 2f)).coerceIn(0f, 1f)
        val vol2Multiplier = ((state.crossfaderValue + 1f) / 2f).coerceIn(0f, 1f)

        deck1Controller.setDeckVolume(state.deck1.volume * vol1Multiplier)
        deck2Controller.setDeckVolume(state.deck2.volume * vol2Multiplier)
    }

    private fun startProgressUpdater() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (isActive) {
                updateDeckState(1) { it.copy(progress = deck1Controller.getProgress()) }
                updateDeckState(2) { it.copy(progress = deck2Controller.getProgress()) }
                delay(100)
            }
        }
    }

    fun openTrackPicker(deck: Int) { _uiState.update { it.copy(showTrackPickerForDeck = deck) } }
    fun closeTrackPicker() { _uiState.update { it.copy(showTrackPickerForDeck = null) } }

    override fun onCleared() {
        super.onCleared()
        deck1Controller.release()
        deck2Controller.release()
        progressJob?.cancel()
    }
}
