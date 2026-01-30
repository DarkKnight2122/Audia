package com.oakiha.audia.presentation.viewmodel

import com.oakiha.audia.data.DailyMixManager
import com.oakiha.audia.data.model.Track
import com.oakiha.audia.data.preferences.UserPreferencesRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages Daily Mix and Your Mix state.
 * Extracted from PlayerViewModel to improve modularity.
 * 
 * Responsibilities:
 * - Generate and update daily/your mixes
 * - Persist and restore mix state
 * - Check if mix needs updating based on day change
 */
@Singleton
class DailyMixStateHolder @Inject constructor(
    private val dailyMixManager: DailyMixManager,
    private val userPreferencesRepository: UserPreferencesRepository
) {
    private var scope: CoroutineScope? = null
    private var updateJob: Job? = null

    private val _dailyMixTracks = MutableStateFlow<ImmutableList<Track>>(persistentListOf())
    val dailyMixTracks: StateFlow<ImmutableList<Track>> = _dailyMixTracks.asStateFlow()

    private val _yourMixTracks = MutableStateFlow<ImmutableList<Track>>(persistentListOf())
    val yourMixTracks: StateFlow<ImmutableList<Track>> = _yourMixTracks.asStateFlow()

    /**
     * Initialize with coroutine scope from ViewModel.
     */
    fun initialize(coroutineScope: CoroutineScope) {
        scope = coroutineScope
    }

    /**
     * Remove a Track from the daily mix.
     */
    fun removeFromDailyMix(TrackId: String) {
        _dailyMixTracks.update { currentList ->
            currentList.filterNot { it.id == TrackId }.toImmutableList()
        }
    }

    /**
     * Update the daily mix with new Tracks.
     * @param allTracksFlow Flow of all available books
     * @param favoriteTrackIdsFlow Flow of favorite Track IDs
     */
    fun updateDailyMix(allTracksFlow: Flow<List<Track>>, favoriteTrackIdsFlow: Flow<Set<String>>) {
        updateJob?.cancel()
        updateJob = scope?.launch(Dispatchers.IO) {
            val allTracks = allTracksFlow.first()
            if (allTracks.isNotEmpty()) {
                val favoriteIds = favoriteTrackIdsFlow.first()
                
                // Generate daily mix
                val mix = dailyMixManager.generateDailyMix(allTracks, favoriteIds)
                _dailyMixTracks.value = mix.toImmutableList()
                userPreferencesRepository.saveDailyMixTrackIds(mix.map { it.id })

                // Generate your mix
                val yourMix = dailyMixManager.generateYourMix(allTracks, favoriteIds)
                _yourMixTracks.value = yourMix.toImmutableList()
                userPreferencesRepository.saveYourMixTrackIds(yourMix.map { it.id })
            } else {
                _yourMixTracks.value = persistentListOf()
            }
        }
    }

    /**
     * Load persisted daily mix from storage.
     */
    fun loadPersistedDailyMix(allTracksFlow: Flow<List<Track>>) {
        // Load Daily Mix
        scope?.launch {
            userPreferencesRepository.dailyMixTrackIdsFlow
                .combine(allTracksFlow) { ids, allTracks ->
                    if (ids.isNotEmpty() && allTracks.isNotEmpty()) {
                        val TrackMap = allTracks.associateBy { it.id }
                        ids.mapNotNull { TrackMap[it] }.toImmutableList()
                    } else {
                        persistentListOf()
                    }
                }
                .flowOn(Dispatchers.Default)
                .collect { persistedMix ->
                    // Only update if current mix is empty
                    if (_dailyMixTracks.value.isEmpty() && persistedMix.isNotEmpty()) {
                        _dailyMixTracks.value = persistedMix
                    }
                }
        }
        
        // Load Your Mix
        scope?.launch {
            userPreferencesRepository.yourMixTrackIdsFlow
                .combine(allTracksFlow) { ids, allTracks ->
                    if (ids.isNotEmpty() && allTracks.isNotEmpty()) {
                        val TrackMap = allTracks.associateBy { it.id }
                        ids.mapNotNull { TrackMap[it] }.toImmutableList()
                    } else {
                        persistentListOf()
                    }
                }
                .flowOn(Dispatchers.Default)
                .collect { persistedMix ->
                    // Only update if current mix is empty
                    if (_yourMixTracks.value.isEmpty() && persistedMix.isNotEmpty()) {
                        _yourMixTracks.value = persistedMix
                    }
                }
        }
    }

    /**
     * Force update the daily mix regardless of day.
     */
    fun forceUpdate(allTracksFlow: Flow<List<Track>>, favoriteTrackIdsFlow: Flow<Set<String>>) {
        scope?.launch {
            updateDailyMix(allTracksFlow, favoriteTrackIdsFlow)
            userPreferencesRepository.saveLastDailyMixUpdateTimestamp(System.currentTimeMillis())
        }
    }

    /**
     * Check if daily mix needs updating (new day) and update if so.
     */
    fun checkAndUpdateIfNeeded(allTracksFlow: Flow<List<Track>>, favoriteTrackIdsFlow: Flow<Set<String>>) {
        scope?.launch {
            val lastUpdate = userPreferencesRepository.lastDailyMixUpdateFlow.first()
            val today = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
            val lastUpdateDay = Calendar.getInstance().apply { 
                timeInMillis = lastUpdate 
            }.get(Calendar.DAY_OF_YEAR)

            if (today != lastUpdateDay) {
                updateDailyMix(allTracksFlow, favoriteTrackIdsFlow)
                userPreferencesRepository.saveLastDailyMixUpdateTimestamp(System.currentTimeMillis())
            }
        }
    }

    /**
     * Set the daily mix Tracks directly (used for AI-generated mixes).
     */
    fun setDailyMixTracks(Tracks: List<Track>) {
        _dailyMixTracks.value = Tracks.toImmutableList()
        scope?.launch {
            userPreferencesRepository.saveDailyMixTrackIds(Tracks.map { it.id })
        }
    }

    /**
     * Get a candidate pool for AI Booklist generation.
     */
    suspend fun getCandidatePool(
        allTracks: List<Track>, 
        favoriteIds: Set<String>,
        maxSize: Int = 100
    ): List<Track> {
        return dailyMixManager.generateDailyMix(allTracks, favoriteIds, maxSize)
    }

    fun onCleared() {
        updateJob?.cancel()
        scope = null
    }
}
