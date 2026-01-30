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

data class AuthorsettingsUiState(
    val AuthorDelimiters: List<String> = UserPreferencesRepository.DEFAULT_Author_DELIMITERS,
    val groupByBookAuthor: Boolean = false,
    val rescanRequired: Boolean = false,
    val isResyncing: Boolean = false
)

@HiltViewModel
class AuthorsettingsViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val syncManager: SyncManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthorsettingsUiState())
    val uiState: StateFlow<AuthorsettingsUiState> = _uiState.asStateFlow()

    val isSyncing: StateFlow<Boolean> = syncManager.isSyncing
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    init {
        viewModelScope.launch {
            userPreferencesRepository.AuthorDelimitersFlow.collect { delimiters ->
                _uiState.update { it.copy(AuthorDelimiters = delimiters) }
            }
        }

        viewModelScope.launch {
            userPreferencesRepository.groupByBookAuthorFlow.collect { enabled ->
                _uiState.update { it.copy(groupByBookAuthor = enabled) }
            }
        }

        viewModelScope.launch {
            userPreferencesRepository.AuthorsettingsRescanRequiredFlow.collect { required ->
                _uiState.update { it.copy(rescanRequired = required) }
            }
        }

        viewModelScope.launch {
            syncManager.isSyncing.collect { syncing ->
                _uiState.update { it.copy(isResyncing = syncing) }
            }
        }
    }

    fun setGroupByBookAuthor(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setGroupByBookAuthor(enabled)
        }
    }

    fun setAuthorDelimiters(delimiters: List<String>) {
        viewModelScope.launch {
            userPreferencesRepository.setAuthorDelimiters(delimiters)
        }
    }

    fun addDelimiter(delimiter: String): Boolean {
        val trimmed = delimiter.trim()
        if (trimmed.isEmpty()) return false
        
        val current = _uiState.value.AuthorDelimiters
        if (current.contains(trimmed)) return false
        
        viewModelScope.launch {
            userPreferencesRepository.setAuthorDelimiters(current + trimmed)
        }
        return true
    }

    fun removeDelimiter(delimiter: String) {
        val current = _uiState.value.AuthorDelimiters
        if (current.size <= 1) return // Keep at least one delimiter
        
        viewModelScope.launch {
            userPreferencesRepository.setAuthorDelimiters(current - delimiter)
        }
    }

    fun resetDelimitersToDefault() {
        viewModelScope.launch {
            userPreferencesRepository.resetAuthorDelimitersToDefault()
        }
    }

    fun rescanLibrary() {
        viewModelScope.launch {
            syncManager.fullSync()
        }
    }
}
