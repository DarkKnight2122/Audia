package com.oakiha.audia.presentation.viewmodel

import com.oakiha.audia.data.DailyMixManager
import com.oakiha.audia.data.model.Song
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

    private val _dailyMixSongs = MutableStateFlow<ImmutableList<Song>>(persistentListOf())
    val dailyMixSongs: StateFlow<ImmutableList<Song>> = _dailyMixSongs.asStateFlow()

    private val _yourMixSongs = MutableStateFlow<ImmutableList<Song>>(persistentListOf())
    val yourMixSongs: StateFlow<ImmutableList<Song>> = _yourMixSongs.asStateFlow()

    /**
     * Initialize with coroutine scope from ViewModel.
     */
    fun initialize(coroutineScope: CoroutineScope) {
        scope = coroutineScope
    }

    /**
     * Remove a song from the daily mix.
     */
    fun removeFromDailyMix(songId: String) {
        _dailyMixSongs.update { currentList ->
            currentList.filterNot { it.id == songId }.toImmutableList()
        }
    }

    /**
     * Update the daily mix with new songs.
     * @param allTracksFlow Flow of all available songs
     * @param favoriteSongIdsFlow Flow of favorite song IDs
     */
    fun updateDailyMix(allTracksFlow: Flow<List<Song>>, favoriteSongIdsFlow: Flow<Set<String>>) {
        updateJob?.cancel()
        updateJob = scope?.launch(Dispatchers.IO) {
            val allTracks = allTracksFlow.first()
            if (allTracks.isNotEmpty()) {
                val favoriteIds = favoriteSongIdsFlow.first()
                
                // Generate daily mix
                val mix = dailyMixManager.generateDailyMix(allTracks, favoriteIds)
                _dailyMixSongs.value = mix.toImmutableList()
                userPreferencesRepository.saveDailyMixSongIds(mix.map { it.id })

                // Generate your mix
                val yourMix = dailyMixManager.generateYourMix(allTracks, favoriteIds)
                _yourMixSongs.value = yourMix.toImmutableList()
                userPreferencesRepository.saveYourMixSongIds(yourMix.map { it.id })
            } else {
                _yourMixSongs.value = persistentListOf()
            }
        }
    }

    /**
     * Load persisted daily mix from storage.
     */
    fun loadPersistedDailyMix(allTracksFlow: Flow<List<Song>>) {
        // Load Daily Mix
        scope?.launch {
            userPreferencesRepository.dailyMixSongIdsFlow
                .combine(allTracksFlow) { ids, allTracks ->
                    if (ids.isNotEmpty() && allTracks.isNotEmpty()) {
                        val songMap = allTracks.associateBy { it.id }
                        ids.mapNotNull { songMap[it] }.toImmutableList()
                    } else {
                        persistentListOf()
                    }
                }
                .flowOn(Dispatchers.Default)
                .collect { persistedMix ->
                    // Only update if current mix is empty
                    if (_dailyMixSongs.value.isEmpty() && persistedMix.isNotEmpty()) {
                        _dailyMixSongs.value = persistedMix
                    }
                }
        }
        
        // Load Your Mix
        scope?.launch {
            userPreferencesRepository.yourMixSongIdsFlow
                .combine(allTracksFlow) { ids, allTracks ->
                    if (ids.isNotEmpty() && allTracks.isNotEmpty()) {
                        val songMap = allTracks.associateBy { it.id }
                        ids.mapNotNull { songMap[it] }.toImmutableList()
                    } else {
                        persistentListOf()
                    }
                }
                .flowOn(Dispatchers.Default)
                .collect { persistedMix ->
                    // Only update if current mix is empty
                    if (_yourMixSongs.value.isEmpty() && persistedMix.isNotEmpty()) {
                        _yourMixSongs.value = persistedMix
                    }
                }
        }
    }

    /**
     * Force update the daily mix regardless of day.
     */
    fun forceUpdate(allTracksFlow: Flow<List<Song>>, favoriteSongIdsFlow: Flow<Set<String>>) {
        scope?.launch {
            updateDailyMix(allTracksFlow, favoriteSongIdsFlow)
            userPreferencesRepository.saveLastDailyMixUpdateTimestamp(System.currentTimeMillis())
        }
    }

    /**
     * Check if daily mix needs updating (new day) and update if so.
     */
    fun checkAndUpdateIfNeeded(allTracksFlow: Flow<List<Song>>, favoriteSongIdsFlow: Flow<Set<String>>) {
        scope?.launch {
            val lastUpdate = userPreferencesRepository.lastDailyMixUpdateFlow.first()
            val today = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
            val lastUpdateDay = Calendar.getInstance().apply { 
                timeInMillis = lastUpdate 
            }.get(Calendar.DAY_OF_YEAR)

            if (today != lastUpdateDay) {
                updateDailyMix(allTracksFlow, favoriteSongIdsFlow)
                userPreferencesRepository.saveLastDailyMixUpdateTimestamp(System.currentTimeMillis())
            }
        }
    }

    /**
     * Set the daily mix songs directly (used for AI-generated mixes).
     */
    fun setDailyMixSongs(songs: List<Song>) {
        _dailyMixSongs.value = songs.toImmutableList()
        scope?.launch {
            userPreferencesRepository.saveDailyMixSongIds(songs.map { it.id })
        }
    }

    /**
     * Get a candidate pool for AI playlist generation.
     */
    suspend fun getCandidatePool(
        allTracks: List<Song>, 
        favoriteIds: Set<String>,
        maxSize: Int = 100
    ): List<Song> {
        return dailyMixManager.generateDailyMix(allTracks, favoriteIds, maxSize)
    }

    fun onCleared() {
        updateJob?.cancel()
        scope = null
    }
}
