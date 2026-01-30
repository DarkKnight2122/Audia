package com.oakiha.audia.presentation.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oakiha.audia.data.preferences.AppThemeMode
import com.oakiha.audia.data.preferences.CarouselStyle
import com.oakiha.audia.data.preferences.LibraryNavigationMode
import com.oakiha.audia.data.preferences.ThemePreference
import com.oakiha.audia.data.preferences.UserPreferencesRepository
import com.oakiha.audia.data.preferences.BookArtQuality
import com.oakiha.audia.data.preferences.FullPlayerLoadingTweaks
import com.oakiha.audia.data.repository.TranscriptRepository
import com.oakiha.audia.data.repository.AudiobookRepository
import com.oakiha.audia.data.model.TranscriptSourcePreference
import com.oakiha.audia.data.worker.SyncManager
import com.oakiha.audia.data.worker.SyncProgress
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

import com.oakiha.audia.data.preferences.NavBarStyle
import com.oakiha.audia.data.ai.GeminiModelService
import com.oakiha.audia.data.ai.GeminiModel
import com.oakiha.audia.data.preferences.LaunchTab
import com.oakiha.audia.data.model.Track
import java.io.File

data class SettingsUiState(
    val isLoadingDirectories: Boolean = false,
    val appThemeMode: String = AppThemeMode.FOLLOW_SYSTEM,
    val playerThemePreference: String = ThemePreference.Book_ART,
    val mockCategoriesEnabled: Boolean = false,
    val navBarCornerRadius: Int = 32,
    val navBarStyle: String = NavBarStyle.DEFAULT,
    val carouselStyle: String = CarouselStyle.NO_PEEK,
    val libraryNavigationMode: String = LibraryNavigationMode.TAB_ROW,
    val launchTab: String = LaunchTab.HOME,
    val keepPlayingInBackground: Boolean = true,
    val disableCastAutoplay: Boolean = false,
    val showQueueHistory: Boolean = true,
    val isCrossfadeEnabled: Boolean = true,
    val crossfadeDuration: Int = 6000,
    val persistentShuffleEnabled: Boolean = false,
    val TranscriptSourcePreference: TranscriptSourcePreference = TranscriptSourcePreference.EMBEDDED_FIRST,
    val autoScanLrcFiles: Boolean = false,
    val blockedDirectories: Set<String> = emptySet(),
    val availableModels: List<GeminiModel> = emptyList(),
    val isLoadingModels: Boolean = false,
    val modelsFetchError: String? = null,
    val appRebrandDialogShown: Boolean = false,
    val fullPlayerLoadingTweaks: FullPlayerLoadingTweaks = FullPlayerLoadingTweaks(),
    // Developer Options
    val BookArtQuality: BookArtQuality = BookArtQuality.MEDIUM,
    val tapBackgroundClosesPlayer: Boolean = true,
    val immersiveTranscriptEnabled: Boolean = false,
    val immersiveTranscriptTimeout: Long = 4000L
)

data class FailedTrackInfo(
    val id: String,
    val title: String,
    val Author: String
)

data class TranscriptRefreshProgress(
    val totalTracks: Int = 0,
    val currentCount: Int = 0,
    val savedCount: Int = 0,
    val notFoundCount: Int = 0,
    val skippedCount: Int = 0,
    val isComplete: Boolean = false,
    val failedTracks: List<FailedTrackInfo> = emptyList()
) {
    val hasProgress: Boolean get() = totalTracks > 0
    val progress: Float get() = if (totalTracks > 0) currentCount.toFloat() / totalTracks else 0f
    val hasFailedTracks: Boolean get() = failedTracks.isNotEmpty()
}

// Helper classes for consolidated combine() collectors to reduce coroutine overhead
private sealed interface SettingsUiUpdate {
    data class Group1(
        val appRebrandDialogShown: Boolean,
        val appThemeMode: String,
        val playerThemePreference: String,
        val mockCategoriesEnabled: Boolean,
        val navBarCornerRadius: Int,
        val navBarStyle: String,
        val libraryNavigationMode: String,
        val carouselStyle: String,
        val launchTab: String
    ) : SettingsUiUpdate
    
    data class Group2(
        val keepPlayingInBackground: Boolean,
        val disableCastAutoplay: Boolean,
        val showQueueHistory: Boolean,
        val isCrossfadeEnabled: Boolean,
        val crossfadeDuration: Int,
        val persistentShuffleEnabled: Boolean,
        val TranscriptSourcePreference: TranscriptSourcePreference,
        val autoScanLrcFiles: Boolean,
        val blockedDirectories: Set<String>,
        val immersiveTranscriptEnabled: Boolean,
        val immersiveTranscriptTimeout: Long
    ) : SettingsUiUpdate
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val syncManager: SyncManager,
    private val geminiModelService: GeminiModelService,
    private val TranscriptRepository: TranscriptRepository,
    private val AudiobookRepository: AudiobookRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    val geminiApiKey: StateFlow<String> = userPreferencesRepository.geminiApiKey
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val geminiModel: StateFlow<String> = userPreferencesRepository.geminiModel
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val geminiSystemPrompt: StateFlow<String> = userPreferencesRepository.geminiSystemPrompt
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserPreferencesRepository.DEFAULT_SYSTEM_PROMPT)

    private val fileExplorerStateHolder = FileExplorerStateHolder(userPreferencesRepository, viewModelScope, context)

    val currentPath = fileExplorerStateHolder.currentPath
    val currentDirectoryChildren = fileExplorerStateHolder.currentDirectoryChildren
    val blockedDirectories = fileExplorerStateHolder.blockedDirectories
    val availableStorages = fileExplorerStateHolder.availableStorages
    val selectedStorageIndex = fileExplorerStateHolder.selectedStorageIndex
    val isLoadingDirectories = fileExplorerStateHolder.isLoading
    val isExplorerPriming = fileExplorerStateHolder.isPrimingExplorer
    val isExplorerReady = fileExplorerStateHolder.isExplorerReady

    val isSyncing: StateFlow<Boolean> = syncManager.isSyncing
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    val syncProgress: StateFlow<SyncProgress> = syncManager.syncProgress
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SyncProgress()
        )

    init {
        // Consolidated collectors using combine() to reduce coroutine overhead
        // Instead of 20 separate coroutines, we use 2 combined flows
        
        // Group 1: Core UI settings (theme, navigation, appearance)
        viewModelScope.launch {
            combine(
                userPreferencesRepository.appRebrandDialogShownFlow,
                userPreferencesRepository.appThemeModeFlow,
                userPreferencesRepository.playerThemePreferenceFlow,
                userPreferencesRepository.mockCategoriesEnabledFlow,
                userPreferencesRepository.navBarCornerRadiusFlow,
                userPreferencesRepository.navBarStyleFlow,
                userPreferencesRepository.libraryNavigationModeFlow,
                userPreferencesRepository.carouselStyleFlow,
                userPreferencesRepository.launchTabFlow
            ) { values ->
                SettingsUiUpdate.Group1(
                    appRebrandDialogShown = values[0] as Boolean,
                    appThemeMode = values[1] as String,
                    playerThemePreference = values[2] as String,
                    mockCategoriesEnabled = values[3] as Boolean,
                    navBarCornerRadius = values[4] as Int,
                    navBarStyle = values[5] as String,
                    libraryNavigationMode = values[6] as String,
                    carouselStyle = values[7] as String,
                    launchTab = values[8] as String
                )
            }.collect { update ->
                _uiState.update { state ->
                    state.copy(
                        appRebrandDialogShown = update.appRebrandDialogShown,
                        appThemeMode = update.appThemeMode,
                        playerThemePreference = update.playerThemePreference,
                        mockCategoriesEnabled = update.mockCategoriesEnabled,
                        navBarCornerRadius = update.navBarCornerRadius,
                        navBarStyle = update.navBarStyle,
                        libraryNavigationMode = update.libraryNavigationMode,
                        carouselStyle = update.carouselStyle,
                        launchTab = update.launchTab
                    )
                }
            }
        }
        
        // Group 2: Playback and system settings
        viewModelScope.launch {
            combine(
                userPreferencesRepository.keepPlayingInBackgroundFlow,
                userPreferencesRepository.disableCastAutoplayFlow,
                userPreferencesRepository.showQueueHistoryFlow,
                userPreferencesRepository.isCrossfadeEnabledFlow,
                userPreferencesRepository.crossfadeDurationFlow,
                userPreferencesRepository.persistentShuffleEnabledFlow,
                userPreferencesRepository.TranscriptSourcePreferenceFlow,
                userPreferencesRepository.autoScanLrcFilesFlow,
                userPreferencesRepository.blockedDirectoriesFlow,
                userPreferencesRepository.immersiveTranscriptEnabledFlow,
                userPreferencesRepository.immersiveTranscriptTimeoutFlow
            ) { values ->
                SettingsUiUpdate.Group2(
                    keepPlayingInBackground = values[0] as Boolean,
                    disableCastAutoplay = values[1] as Boolean,
                    showQueueHistory = values[2] as Boolean,
                    isCrossfadeEnabled = values[3] as Boolean,
                    crossfadeDuration = values[4] as Int,
                    persistentShuffleEnabled = values[5] as Boolean,
                    TranscriptSourcePreference = values[6] as TranscriptSourcePreference,
                    autoScanLrcFiles = values[7] as Boolean,
                    blockedDirectories = @Suppress("UNCHECKED_CAST") (values[8] as Set<String>),
                    immersiveTranscriptEnabled = values[9] as Boolean,
                    immersiveTranscriptTimeout = values[10] as Long
                )
            }.collect { update ->
                _uiState.update { state ->
                    state.copy(
                        keepPlayingInBackground = update.keepPlayingInBackground,
                        disableCastAutoplay = update.disableCastAutoplay,
                        showQueueHistory = update.showQueueHistory,
                        isCrossfadeEnabled = update.isCrossfadeEnabled,
                        crossfadeDuration = update.crossfadeDuration,
                        persistentShuffleEnabled = update.persistentShuffleEnabled,
                        TranscriptSourcePreference = update.TranscriptSourcePreference,
                        autoScanLrcFiles = update.autoScanLrcFiles,
                        blockedDirectories = update.blockedDirectories,
                        immersiveTranscriptEnabled = update.immersiveTranscriptEnabled,
                        immersiveTranscriptTimeout = update.immersiveTranscriptTimeout
                    )
                }
            }
        }
        
        // Group 3: Remaining individual collectors (loading state, tweaks)
        viewModelScope.launch {
            userPreferencesRepository.fullPlayerLoadingTweaksFlow.collect { tweaks ->
                _uiState.update { it.copy(fullPlayerLoadingTweaks = tweaks) }
            }
        }

        viewModelScope.launch {
            fileExplorerStateHolder.isLoading.collect { loading ->
                _uiState.update { it.copy(isLoadingDirectories = loading) }
            }
        }

        // Beta Features Collectors
        viewModelScope.launch {
            userPreferencesRepository.BookArtQualityFlow.collect { quality ->
                _uiState.update { it.copy(BookArtQuality = quality) }
            }
        }

        viewModelScope.launch {
            userPreferencesRepository.tapBackgroundClosesPlayerFlow.collect { enabled ->
                _uiState.update { it.copy(tapBackgroundClosesPlayer = enabled) }
            }
        }
    }

    fun setAppRebrandDialogShown(wasShown: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setAppRebrandDialogShown(wasShown)
        }
    }

    fun toggleDirectoryAllowed(file: File) {
        viewModelScope.launch {
            fileExplorerStateHolder.toggleDirectoryAllowed(file)
            // Now that preferences are securely saved, we can sync/refresh
            syncManager.sync()
        }
    }

    fun loadDirectory(file: File) {
        fileExplorerStateHolder.loadDirectory(file)
    }

    fun primeExplorer() {
        fileExplorerStateHolder.primeExplorerRoot()
    }

    fun navigateUp() {
        fileExplorerStateHolder.navigateUp()
    }

    fun refreshExplorer() {
        fileExplorerStateHolder.refreshCurrentDirectory()
    }

    fun selectStorage(index: Int) {
        fileExplorerStateHolder.selectStorage(index)
    }

    fun refreshAvailableStorages() {
        fileExplorerStateHolder.refreshAvailableStorages()
    }

    fun isAtRoot(): Boolean = fileExplorerStateHolder.isAtRoot()

    fun explorerRoot(): File = fileExplorerStateHolder.rootDirectory()

    // MÃƒÂ©todo para guardar la preferencia de tema del reproductor
    fun setPlayerThemePreference(preference: String) {
        viewModelScope.launch {
            userPreferencesRepository.setPlayerThemePreference(preference)
        }
    }

    fun setAppThemeMode(mode: String) {
        viewModelScope.launch {
            userPreferencesRepository.setAppThemeMode(mode)
        }
    }

    fun setNavBarStyle(style: String) {
        viewModelScope.launch {
            userPreferencesRepository.setNavBarStyle(style)
        }
    }

    fun setLibraryNavigationMode(mode: String) {
        viewModelScope.launch {
            userPreferencesRepository.setLibraryNavigationMode(mode)
        }
    }

    fun setCarouselStyle(style: String) {
        viewModelScope.launch {
            userPreferencesRepository.setCarouselStyle(style)
        }
    }

    fun setLaunchTab(tab: String) {
        viewModelScope.launch {
            userPreferencesRepository.setLaunchTab(tab)
        }
    }

    fun setKeepPlayingInBackground(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setKeepPlayingInBackground(enabled)
        }
    }

    fun setDisableCastAutoplay(disabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setDisableCastAutoplay(disabled)
        }
    }

    fun setShowQueueHistory(show: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setShowQueueHistory(show)
        }
    }

    fun setCrossfadeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setCrossfadeEnabled(enabled)
        }
    }

    fun setCrossfadeDuration(duration: Int) {
        viewModelScope.launch {
            userPreferencesRepository.setCrossfadeDuration(duration)
        }
    }

    fun setPersistentShuffleEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setPersistentShuffleEnabled(enabled)
        }
    }

    fun setTranscriptSourcePreference(preference: TranscriptSourcePreference) {
        viewModelScope.launch {
            userPreferencesRepository.setTranscriptSourcePreference(preference)
        }
    }

    fun setAutoScanLrcFiles(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setAutoScanLrcFiles(enabled)
        }
    }

    fun setDelayAllFullPlayerContent(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setDelayAllFullPlayerContent(enabled)
        }
    }

    fun setDelayBookCarousel(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setDelayBookCarousel(enabled)
        }
    }

    fun setDelayTrackMetadata(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setDelayTrackMetadata(enabled)
        }
    }

    fun setDelayProgressBar(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setDelayProgressBar(enabled)
        }
    }

    fun setDelayControls(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setDelayControls(enabled)
        }
    }

    fun setFullPlayerPlaceholders(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setFullPlayerPlaceholders(enabled)
            if (!enabled) {
                userPreferencesRepository.setTransparentPlaceholders(false)
            }
        }
    }

    fun setTransparentPlaceholders(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setTransparentPlaceholders(enabled)
        }
    }

    fun setFullPlayerAppearThreshold(thresholdPercent: Int) {
        viewModelScope.launch {
            userPreferencesRepository.setFullPlayerAppearThreshold(thresholdPercent)
        }
    }

    fun refreshLibrary() {
        viewModelScope.launch {
            if (isSyncing.value) return@launch
            syncManager.forceRefresh()
        }
    }



    /**
     * Performs a full library rescan - rescans all files from scratch.
     * Use when Tracks are missing or metadata is incorrect.
     */
    fun fullSyncLibrary() {
        viewModelScope.launch {
            if (isSyncing.value) return@launch
            syncManager.fullSync()
        }
    }

    fun setImmersiveTranscriptEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setImmersiveTranscriptEnabled(enabled)
        }
    }

    fun setImmersiveTranscriptTimeout(timeout: Long) {
        viewModelScope.launch {
            userPreferencesRepository.setImmersiveTranscriptTimeout(timeout)
        }
    }

    /**
     * Completely rebuilds the database from scratch.
     * Clears all data including user edits (Transcript, favorites) and rescans.
     * Use when database is corrupted or as a last resort.
     */
    fun rebuildDatabase() {
        viewModelScope.launch {
            if (isSyncing.value) return@launch
            syncManager.rebuildDatabase()
        }
    }

    fun onGeminiApiKeyChange(apiKey: String) {
        viewModelScope.launch {
            userPreferencesRepository.setGeminiApiKey(apiKey)

            // Fetch models when API key changes and is not empty
            if (apiKey.isNotBlank()) {
                fetchAvailableModels(apiKey)
            } else {
                // Clear models if API key is empty
                _uiState.update {
                    it.copy(
                        availableModels = emptyList(),
                        modelsFetchError = null
                    )
                }
                userPreferencesRepository.setGeminiModel("")
            }
        }
    }

    fun fetchAvailableModels(apiKey: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingModels = true, modelsFetchError = null) }

            val result = geminiModelService.fetchAvailableModels(apiKey)

            result.onSuccess { models ->
                _uiState.update {
                    it.copy(
                        availableModels = models,
                        isLoadingModels = false,
                        modelsFetchError = null
                    )
                }

                // Auto-select first model if none is selected
                val currentModel = userPreferencesRepository.geminiModel.first()
                if (currentModel.isEmpty() && models.isNotEmpty()) {
                    userPreferencesRepository.setGeminiModel(models.first().name)
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoadingModels = false,
                        modelsFetchError = error.message ?: "Failed to fetch models"
                    )
                }
            }
        }
    }

    fun onGeminiModelChange(modelName: String) {
        viewModelScope.launch {
            userPreferencesRepository.setGeminiModel(modelName)
        }
    }

    fun setNavBarCornerRadius(radius: Int) {
        viewModelScope.launch {
            userPreferencesRepository.setNavBarCornerRadius(radius)
        }
    }

    fun onGeminiSystemPromptChange(prompt: String) {
        viewModelScope.launch {
            userPreferencesRepository.setGeminiSystemPrompt(prompt)
        }
    }

    fun resetGeminiSystemPrompt() {
        viewModelScope.launch {
            userPreferencesRepository.resetGeminiSystemPrompt()
        }
    }



    /**
     * Triggers a test crash to verify the crash handler is working correctly.
     * This should only be used for testing in Developer Options.
     */
    fun triggerTestCrash() {
        throw RuntimeException("Test crash triggered from Developer Options - This is intentional for testing the crash reporting system")
    }

    fun resetSetupFlow() {
        viewModelScope.launch {
            userPreferencesRepository.setInitialSetupDone(false)
        }
    }

    // ===== Developer Options =====

    val BookArtQuality: StateFlow<BookArtQuality> = userPreferencesRepository.BookArtQualityFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BookArtQuality.MEDIUM)

    val useSmoothCorners: StateFlow<Boolean> = userPreferencesRepository.useSmoothCornersFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val tapBackgroundClosesPlayer: StateFlow<Boolean> = userPreferencesRepository.tapBackgroundClosesPlayerFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun setBookArtQuality(quality: BookArtQuality) {
        viewModelScope.launch {
            userPreferencesRepository.setBookArtQuality(quality)
        }
    }

    fun setUseSmoothCorners(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setUseSmoothCorners(enabled)
        }
    }

    fun setTapBackgroundClosesPlayer(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setTapBackgroundClosesPlayer(enabled)
        }
    }
}
