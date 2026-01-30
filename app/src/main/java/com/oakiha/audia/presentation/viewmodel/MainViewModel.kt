package com.oakiha.audia.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oakiha.audia.data.preferences.UserPreferencesRepository
import com.oakiha.audia.data.repository.AudiobookRepository
import com.oakiha.audia.data.worker.SyncManager
import com.oakiha.audia.data.worker.SyncProgress
import com.oakiha.audia.utils.LogUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val syncManager: SyncManager,
    AudiobookRepository: AudiobookRepository,
    userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    val isSetupComplete: StateFlow<Boolean> = userPreferencesRepository.initialSetupDoneFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    /**
     * Un Flow que emite `true` si el SyncWorker estÃ¡ encolado o en ejecuciÃ³n.
     * Ideal para mostrar un indicador de carga.
     */
    val isSyncing: StateFlow<Boolean> = syncManager.isSyncing
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true // Asumimos que podrÃ­a estar sincronizando al inicio
        )

    /**
     * Flow that exposes detailed sync progress including file count and phase.
     */
    val syncProgress: StateFlow<SyncProgress> = syncManager.syncProgress
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SyncProgress()
        )

    /**
     * Un Flow que emite `true` si la base de datos de Room no tiene canciones.
     * Nos ayuda a saber si es la primera vez que se abre la app.
     */
    val isLibraryEmpty: StateFlow<Boolean> = AudiobookRepository
        .getAudioFiles()
        .map { it.isEmpty() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    /**
     * FunciÃ³n para iniciar la sincronizaciÃ³n de la biblioteca de mÃºsica.
     * Se debe llamar despuÃ©s de que los permisos hayan sido concedidos.
     */
    fun startSync() {
        LogUtils.i(this, "startSync called")
        viewModelScope.launch {
            // For fresh installs after setup, SetupViewModel.setSetupComplete() triggers sync
            // For returning users (setup already complete), we trigger sync here
            if (isSetupComplete.value) {
                syncManager.sync()
            }
        }
    }
}
