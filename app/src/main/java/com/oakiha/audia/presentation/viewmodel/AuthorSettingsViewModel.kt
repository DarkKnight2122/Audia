package com.oakiha.audia.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oakiha.audia.data.preferences.UserPreferencesRepository
import com.oakiha.audia.data.worker.SyncManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthorSettingsUiState(
    val authorDelimiters: List<String> = UserPreferencesRepository.DEFAULT_ARTIST_DELIMITERS,
    val groupByAlbumArtist: Boolean = false,
    val rescanRequired: Boolean = false,
    val isResyncing: Boolean = false
)

@HiltViewModel
class AuthorSettingsViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val syncManager: SyncManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthorSettingsUiState())
    val uiState: StateFlow<AuthorSettingsUiState> = _uiState.asStateFlow()

    val isSyncing: StateFlow<Boolean> = syncManager.isSyncing
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    init {
        viewModelScope.launch {
            userPreferencesRepository.authorDelimitersFlow.collect { delimiters ->
                _uiState.update { it.copy(authorDelimiters = delimiters) }
            }
        }

        viewModelScope.launch {
            userPreferencesRepository.groupByAlbumArtistFlow.collect { enabled ->
                _uiState.update { it.copy(groupByAlbumArtist = enabled) }
            }
        }

        viewModelScope.launch {
            userPreferencesRepository.authorSettingsRescanRequiredFlow.collect { required ->
                _uiState.update { it.copy(rescanRequired = required) }
            }
        }

        viewModelScope.launch {
            syncManager.isSyncing.collect { syncing ->
                _uiState.update { it.copy(isResyncing = syncing) }
            }
        }
    }

    fun setGroupByAlbumArtist(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setGroupByAlbumArtist(enabled)
        }
    }

    fun setArtistDelimiters(delimiters: List<String>) {
        viewModelScope.launch {
            userPreferencesRepository.setArtistDelimiters(delimiters)
        }
    }

    fun addDelimiter(delimiter: String): Boolean {
        val trimmed = delimiter.trim()
        if (trimmed.isEmpty()) return false
        
        val current = _uiState.value.authorDelimiters
        if (current.contains(trimmed)) return false
        
        viewModelScope.launch {
            userPreferencesRepository.setArtistDelimiters(current + trimmed)
        }
        return true
    }

    fun removeDelimiter(delimiter: String) {
        val current = _uiState.value.authorDelimiters
        if (current.size <= 1) return // Keep at least one delimiter
        
        viewModelScope.launch {
            userPreferencesRepository.setArtistDelimiters(current - delimiter)
        }
    }

    fun resetDelimitersToDefault() {
        viewModelScope.launch {
            userPreferencesRepository.resetArtistDelimitersToDefault()
        }
    }

    fun rescanLibrary() {
        viewModelScope.launch {
            syncManager.fullSync()
        }
    }
}
