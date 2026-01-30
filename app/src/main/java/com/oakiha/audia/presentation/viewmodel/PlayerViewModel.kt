package com.oakiha.audia.presentation.viewmodel

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ComponentName
import android.net.Uri
import android.os.SystemClock
import android.os.Trace
import android.os.Looper
import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.core.content.ContextCompat
import com.oakiha.audia.data.model.LibraryTabId
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.compose.runtime.snapshotFlow
import androidx.media3.common.Timeline
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import androidx.mediarouter.media.MediaControlIntent
import androidx.mediarouter.media.MediaRouter
import com.google.android.gms.cast.framework.SessionManager
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.oakiha.audia.R
import com.oakiha.audia.data.EotStateHolder
import com.oakiha.audia.data.ai.TrackMetadata
import com.oakiha.audia.data.database.BookArtThemeDao
import com.oakiha.audia.data.media.CoverArtUpdate
import com.oakiha.audia.data.model.Book
import com.oakiha.audia.data.model.Author
import com.oakiha.audia.data.model.Category
import com.oakiha.audia.data.model.Transcript
import com.oakiha.audia.data.model.TranscriptSourcePreference
import com.oakiha.audia.data.model.AudiobookFolder
import com.oakiha.audia.data.model.SearchFilterType
import com.oakiha.audia.data.model.Track
import com.oakiha.audia.data.model.SortOption
import com.oakiha.audia.data.model.toLibraryTabIdOrNull
import com.oakiha.audia.data.preferences.CarouselStyle
import com.oakiha.audia.data.preferences.LibraryNavigationMode
import com.oakiha.audia.data.preferences.NavBarStyle
import com.oakiha.audia.data.preferences.FullPlayerLoadingTweaks
import com.oakiha.audia.data.preferences.UserPreferencesRepository
import com.oakiha.audia.data.preferences.BookArtQuality
import com.oakiha.audia.data.repository.TranscriptSearchResult
import com.oakiha.audia.data.repository.AudiobookRepository
import com.oakiha.audia.data.service.AudiobookNotificationProvider
import com.oakiha.audia.data.service.AudiobookService
import com.oakiha.audia.data.service.player.CastPlayer
import com.oakiha.audia.data.service.http.MediaFileHttpServerService
import com.oakiha.audia.data.service.player.DualPlayerEngine
import com.oakiha.audia.data.worker.SyncManager
import com.oakiha.audia.utils.AppShortcutManager
import com.oakiha.audia.utils.QueueUtils
import com.oakiha.audia.utils.MediaItemBuilder
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File
import java.util.ArrayDeque
import javax.inject.Inject

private const val CAST_LOG_TAG = "PlayerCastTransfer"

@UnstableApi
@SuppressLint("LogNotTimber")
@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val AudiobookRepository: AudiobookRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val BookArtThemeDao: BookArtThemeDao,
    val syncManager: SyncManager, // Inyectar SyncManager

    private val dualPlayerEngine: DualPlayerEngine,
    private val appShortcutManager: AppShortcutManager,
    private val listeningStatsTracker: ListeningStatsTracker,
    private val dailyMixStateHolder: DailyMixStateHolder,
    private val TranscriptStateHolder: TranscriptStateHolder,
    private val castStateHolder: CastStateHolder,
    private val queueStateHolder: QueueStateHolder,
    private val playbackStateHolder: PlaybackStateHolder,
    private val connectivityStateHolder: ConnectivityStateHolder,
    private val sleepTimerStateHolder: SleepTimerStateHolder,
    private val searchStateHolder: SearchStateHolder,
    private val aiStateHolder: AiStateHolder,
    private val libraryStateHolder: LibraryStateHolder,
    private val castTransferStateHolder: CastTransferStateHolder,
    private val metadataEditStateHolder: MetadataEditStateHolder,
    private val externalMediaStateHolder: ExternalMediaStateHolder,
    val themeStateHolder: ThemeStateHolder
) : ViewModel() {

    private val _playerUiState = MutableStateFlow(PlayerUiState())
    val playerUiState: StateFlow<PlayerUiState> = _playerUiState.asStateFlow()
    
    val stablePlayerState: StateFlow<StablePlayerState> = playbackStateHolder.stablePlayerState
    
    private val _masterAllTracks = MutableStateFlow<ImmutableList<Track>>(persistentListOf())

    // Transcript load callback for TranscriptStateHolder
    private val TranscriptLoadCallback = object : TranscriptLoadCallback {
        override fun onLoadingStarted(TrackId: String) {
            playbackStateHolder.updateStablePlayerState { state ->
                if (state.currentTrack?.id != TrackId) state
                else state.copy(isLoadingTranscript = true, Transcript = null)
            }
        }

        override fun onTranscriptLoaded(TrackId: String, Transcript: Transcript?) {
            playbackStateHolder.updateStablePlayerState { state ->
                if (state.currentTrack?.id != TrackId) state
                else state.copy(isLoadingTranscript = false, Transcript = Transcript)
            }
        }
    }



    @OptIn(ExperimentalCoroutinesApi::class)
    val currentTrackAuthors: StateFlow<List<Author>> = stablePlayerState
        .map { it.currentTrack?.id }
        .distinctUntilChanged()
        .flatMapLatest { TrackId ->
            val idLong = TrackId?.toLongOrNull()
            if (idLong == null) flowOf(emptyList())
            else AudiobookRepository.getAuthorsForTrack(idLong)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _sheetState = MutableStateFlow(PlayerSheetState.COLLAPSED)
    val sheetState: StateFlow<PlayerSheetState> = _sheetState.asStateFlow()
    private val _isSheetVisible = MutableStateFlow(false)
    private val _bottomBarHeight = MutableStateFlow(0)
    val bottomBarHeight: StateFlow<Int> = _bottomBarHeight.asStateFlow()
    private val _predictiveBackCollapseFraction = MutableStateFlow(0f)
    val predictiveBackCollapseFraction: StateFlow<Float> = _predictiveBackCollapseFraction.asStateFlow()

    val playerContentExpansionFraction = Animatable(0f)

    // AI Booklist Generation State
    private val _showAiBooklistsheet = MutableStateFlow(false)
    val showAiBooklistsheet: StateFlow<Boolean> = _showAiBooklistsheet.asStateFlow()

    private val _isGeneratingAiBooklist = MutableStateFlow(false)
    val isGeneratingAiBooklist: StateFlow<Boolean> = _isGeneratingAiBooklist.asStateFlow()

    private val _aiError = MutableStateFlow<String?>(null)
    val aiError: StateFlow<String?> = _aiError.asStateFlow()

    private val _selectedTrackForInfo = MutableStateFlow<Track?>(null)
    val selectedTrackForInfo: StateFlow<Track?> = _selectedTrackForInfo.asStateFlow()

    // Theme & Colors - delegated to ThemeStateHolder
    val currentBookArtColorSchemePair: StateFlow<ColorSchemePair?> = themeStateHolder.currentBookArtColorSchemePair
    val activePlayerColorSchemePair: StateFlow<ColorSchemePair?> = themeStateHolder.activePlayerColorSchemePair

    val navBarCornerRadius: StateFlow<Int> = userPreferencesRepository.navBarCornerRadiusFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 32)

    val navBarStyle: StateFlow<String> = userPreferencesRepository.navBarStyleFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = NavBarStyle.DEFAULT
        )

    val libraryNavigationMode: StateFlow<String> = userPreferencesRepository.libraryNavigationModeFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = LibraryNavigationMode.TAB_ROW
        )

    val carouselStyle: StateFlow<String> = userPreferencesRepository.carouselStyleFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = CarouselStyle.NO_PEEK
        )

    val fullPlayerLoadingTweaks: StateFlow<FullPlayerLoadingTweaks> = userPreferencesRepository.fullPlayerLoadingTweaksFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = FullPlayerLoadingTweaks()
        )

    /**
     * Whether tapping the background of the player sheet toggles its state.
     * When disabled, users must use gestures or buttons to expand/collapse.
     */
    val tapBackgroundClosesPlayer: StateFlow<Boolean> = userPreferencesRepository.tapBackgroundClosesPlayerFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    // Transcript sync offset - now managed by TranscriptStateHolder
    val currentTrackTranscriptSyncOffset: StateFlow<Int> = TranscriptStateHolder.currentTracksyncOffset

    // Transcript source preference (API_FIRST, EMBEDDED_FIRST, LOCAL_FIRST)
    val TranscriptSourcePreference: StateFlow<TranscriptSourcePreference> = userPreferencesRepository.TranscriptSourcePreferenceFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = TranscriptSourcePreference.EMBEDDED_FIRST
        )

    val immersiveTranscriptEnabled: StateFlow<Boolean> = userPreferencesRepository.immersiveTranscriptEnabledFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    val immersiveTranscriptTimeout: StateFlow<Long> = userPreferencesRepository.immersiveTranscriptTimeoutFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 4000L
        )

    private val _isImmersiveTemporarilyDisabled = MutableStateFlow(false)
    val isImmersiveTemporarilyDisabled: StateFlow<Boolean> = _isImmersiveTemporarilyDisabled.asStateFlow()

    fun setImmersiveTemporarilyDisabled(disabled: Boolean) {
        _isImmersiveTemporarilyDisabled.value = disabled
    }

    val BookArtQuality: StateFlow<BookArtQuality> = userPreferencesRepository.BookArtQualityFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BookArtQuality.MEDIUM)

    fun setTranscriptSyncOffset(TrackId: String, offsetMs: Int) {
        TranscriptStateHolder.setSyncOffset(TrackId, offsetMs)
    }

    val useSmoothCorners: StateFlow<Boolean> = userPreferencesRepository.useSmoothCornersFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )



    private val _isInitialThemePreloadComplete = MutableStateFlow(false)

    val isEndOfTrackTimerActive: StateFlow<Boolean> = sleepTimerStateHolder.isEndOfTrackTimerActive
    val activeTimerValueDisplay: StateFlow<String?> = sleepTimerStateHolder.activeTimerValueDisplay
    val playCount: StateFlow<Float> = sleepTimerStateHolder.playCount

    // Transcript search UI state - managed by TranscriptStateHolder
    val TranscriptSearchUiState: StateFlow<TranscriptSearchUiState> = TranscriptStateHolder.searchUiState


    // Toast Events
    private val _toastEvents = MutableSharedFlow<String>()
    val toastEvents = _toastEvents.asSharedFlow()

    private val _AuthorNavigationRequests = MutableSharedFlow<Long>(extraBufferCapacity = 1)
    val AuthorNavigationRequests = _AuthorNavigationRequests.asSharedFlow()
    private var AuthorNavigationJob: Job? = null

    val castRoutes: StateFlow<List<MediaRouter.RouteInfo>> = castStateHolder.castRoutes
    val selectedRoute: StateFlow<MediaRouter.RouteInfo?> = castStateHolder.selectedRoute
    val routeVolume: StateFlow<Int> = castStateHolder.routeVolume
    val isRefreshingRoutes: StateFlow<Boolean> = castStateHolder.isRefreshingRoutes

    // Connectivity state delegated to ConnectivityStateHolder
    val isWifiEnabled: StateFlow<Boolean> = connectivityStateHolder.isWifiEnabled
    val isWifiRadioOn: StateFlow<Boolean> = connectivityStateHolder.isWifiRadioOn
    val wifiName: StateFlow<String?> = connectivityStateHolder.wifiName
    val isBluetoothEnabled: StateFlow<Boolean> = connectivityStateHolder.isBluetoothEnabled
    val bluetoothName: StateFlow<String?> = connectivityStateHolder.bluetoothName
    val bluetoothAudioDevices: StateFlow<List<String>> = connectivityStateHolder.bluetoothAudioDevices


    
    // Connectivity is now managed by ConnectivityStateHolder
    
    // Cast state is now managed by CastStateHolder
    private val sessionManager: SessionManager get() = castStateHolder.sessionManager

    val isRemotePlaybackActive: StateFlow<Boolean> = castStateHolder.isRemotePlaybackActive
    val isCastConnecting: StateFlow<Boolean> = castStateHolder.isCastConnecting
    private val castControlCategory get() = CastMediaControlIntent.categoryForCast(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID)
    val remotePosition: StateFlow<Long> = castStateHolder.remotePosition

    private val _trackVolume = MutableStateFlow(1.0f)
    val trackVolume: StateFlow<Float> = _trackVolume.asStateFlow()


    @Inject
    lateinit var mediaMapper: com.oakiha.audia.data.media.MediaMapper

    @Inject
    lateinit var imageCacheManager: com.oakiha.audia.data.media.ImageCacheManager

    init {
        // Initialize helper classes with our coroutine scope
        listeningStatsTracker.initialize(viewModelScope)
        dailyMixStateHolder.initialize(viewModelScope)
        TranscriptStateHolder.initialize(viewModelScope, TranscriptLoadCallback, playbackStateHolder.stablePlayerState)
        playbackStateHolder.initialize(viewModelScope)
        themeStateHolder.initialize(viewModelScope)

        viewModelScope.launch {
            playbackStateHolder.stablePlayerState.collect { state ->
                _playerUiState.update { it.copy(currentPosition = state.currentPosition) }
            }
        }

        viewModelScope.launch {
            TranscriptStateHolder.TrackUpdates.collect { update: Pair<com.oakiha.audia.data.model.Track, com.oakiha.audia.data.model.Transcript?> ->
                val Track = update.first
                val Transcript = update.second
                // Check if this update is relevant to the currently playing Track OR the selected Track
                if (playbackStateHolder.stablePlayerState.value.currentTrack?.id == Track.id) {
                     updateTrackInStates(Track, Transcript)
                }
                if (_selectedTrackForInfo.value?.id == Track.id) {
                    _selectedTrackForInfo.value = Track
                }
            }
        }

        TranscriptStateHolder.messageEvents
            .onEach { msg: String -> _toastEvents.emit(msg) }
            .launchIn(viewModelScope)
    }

    fun setTrackVolume(volume: Float) {
        mediaController?.let {
            val clampedVolume = volume.coerceIn(0f, 1f)
            it.volume = clampedVolume
            _trackVolume.value = clampedVolume
        }
    }

    fun sendToast(message: String) {
        viewModelScope.launch {
            _toastEvents.emit(message)
        }
    }


    // Last Library Tab Index
    val lastLibraryTabIndexFlow: StateFlow<Int> =
        userPreferencesRepository.lastLibraryTabIndexFlow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0 // Default to Tracks tab
        )

    val libraryTabsFlow: StateFlow<List<String>> = userPreferencesRepository.libraryTabsOrderFlow
        .map { orderJson ->
            if (orderJson != null) {
                try {
                    Json.decodeFromString<List<String>>(orderJson)
                } catch (e: Exception) {
                    listOf("Tracks", "Books", "Author", "Booklists", "FOLDERS", "LIKED")
                }
            } else {
                listOf("Tracks", "Books", "Author", "Booklists", "FOLDERS", "LIKED")
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), listOf("Tracks", "Books", "Author", "Booklists", "FOLDERS", "LIKED"))

    private val _loadedTabs = MutableStateFlow(emptySet<String>())
    private var lastBlockedDirectories: Set<String>? = null

    private val _currentLibraryTabId = MutableStateFlow(LibraryTabId.Tracks)
    val currentLibraryTabId: StateFlow<LibraryTabId> = _currentLibraryTabId.asStateFlow()

    private val _isSortingSheetVisible = MutableStateFlow(false)
    val isSortingSheetVisible: StateFlow<Boolean> = _isSortingSheetVisible.asStateFlow()

    val availableSortOptions: StateFlow<List<SortOption>> =
        currentLibraryTabId.map { tabId ->
            Trace.beginSection("PlayerViewModel.availableSortOptionsMapping")
            val options = when (tabId) {
                LibraryTabId.Tracks -> SortOption.Tracks
                LibraryTabId.Books -> SortOption.Books
                LibraryTabId.Authors -> SortOption.Authors
                LibraryTabId.Booklists -> SortOption.Booklists
                LibraryTabId.FOLDERS -> SortOption.FOLDERS
                LibraryTabId.LIKED -> SortOption.LIKED
            }
            Trace.endSection()
            options
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SortOption.Tracks
        )

    val isSyncingStateFlow: StateFlow<Boolean> = syncManager.isSyncing
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    private val _isInitialDataLoaded = MutableStateFlow(false)

    // Public read-only access to all Tracks (using _masterAllTracks declared at class level)
    // Library State - delegated to LibraryStateHolder
    val allTracksFlow: StateFlow<ImmutableList<Track>> = libraryStateHolder.allTracks

    // Categories StateFlow - delegated to LibraryStateHolder
    val Categories: StateFlow<ImmutableList<Category>> = libraryStateHolder.Categories
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = persistentListOf()
        )





    private var mediaController: MediaController? = null
    private val sessionToken = SessionToken(context, ComponentName(context, AudiobookService::class.java))
    private val mediaControllerListener = object : MediaController.Listener {
        override fun onCustomCommand(
            controller: MediaController,
            command: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            if (command.customAction == AudiobookNotificationProvider.CUSTOM_COMMAND_SET_SHUFFLE_STATE) {
                val enabled = args.getBoolean(
                    AudiobookNotificationProvider.EXTRA_SHUFFLE_ENABLED,
                    false
                )
                viewModelScope.launch {
                    if (enabled != playbackStateHolder.stablePlayerState.value.isShuffleEnabled) {
                        toggleShuffle()
                    }
                }
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            return Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
        }
    }
    private val mediaControllerFuture: ListenableFuture<MediaController> =
        MediaController.Builder(context, sessionToken)
            .setListener(mediaControllerListener)
            .buildAsync()
    private var pendingRepeatMode: Int? = null

    private var pendingPlaybackAction: (() -> Unit)? = null

    val favoriteTrackIds: StateFlow<Set<String>> = userPreferencesRepository.favoriteTrackIdsFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val isCurrentTrackFavorite: StateFlow<Boolean> = combine(
        stablePlayerState,
        favoriteTrackIds
    ) { state, ids ->
        state.currentTrack?.id?.let { ids.contains(it) } ?: false
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // Library State - delegated to LibraryStateHolder

    val favoriteTracks: StateFlow<ImmutableList<Track>> = combine(
        favoriteTrackIds,
        _masterAllTracks,
        libraryStateHolder.currentFavoriteSortOption
    ) { ids: Set<String>, allTracksList: List<Track>, sortOption: SortOption ->
        val favoriteTracksList = allTracksList.filter { Track -> ids.contains(Track.id) }
        when (sortOption) {
            SortOption.LikedTrackTitleAZ -> favoriteTracksList.sortedBy { it.title.lowercase() }
            SortOption.LikedTrackTitleZA -> favoriteTracksList.sortedByDescending { it.title.lowercase() }
            SortOption.LikedTrackAuthor -> favoriteTracksList.sortedBy { it.Author.lowercase() }
            SortOption.LikedTrackBook -> favoriteTracksList.sortedBy { it.Book.lowercase() }
            SortOption.LikedTrackDateLiked -> favoriteTracksList.sortedByDescending { it.id }
            else -> favoriteTracksList
        }.toImmutableList()
    }
    .flowOn(Dispatchers.Default) // Execute combine and transformations on Default dispatcher
    .stateIn(viewModelScope, SharingStarted.Lazily, persistentListOf())

    // Daily mix state is now managed by DailyMixStateHolder
    val dailyMixTracks: StateFlow<ImmutableList<Track>> = dailyMixStateHolder.dailyMixTracks
    val yourMixTracks: StateFlow<ImmutableList<Track>> = dailyMixStateHolder.yourMixTracks

    fun removeFromDailyMix(TrackId: String) {
        dailyMixStateHolder.removeFromDailyMix(TrackId)
    }

    /**
     * Observes a Track by ID, combining the latest metadata from [allTracksFlow]
     * with the latest favorite status from [favoriteTrackIds].
     * Returns null if the Track is not found in the library.
     */
    fun observeTrack(TrackId: String?): Flow<Track?> {
        if (TrackId == null) return flowOf(null)
        return combine(allTracksFlow, favoriteTrackIds) { Tracks, favorites ->
            Tracks.find { it.id == TrackId }?.copy(isFavorite = favorites.contains(TrackId))
        }.distinctUntilChanged()
    }

    private fun updateDailyMix() {
        // Delegate to DailyMixStateHolder
        dailyMixStateHolder.updateDailyMix(
            allTracksFlow = allTracksFlow,
            favoriteTrackIdsFlow = userPreferencesRepository.favoriteTrackIdsFlow
        )
    }

    fun shuffleAllTracks() {
        Log.d("ShuffleDebug", "shuffleAllTracks called.")
        // Don't use ExoPlayer's shuffle mode - we manually shuffle instead
        val currentTrack = playbackStateHolder.stablePlayerState.value.currentTrack
        val isPlaying = playbackStateHolder.stablePlayerState.value.isPlaying
        
        // If something is playing, just toggle shuffle on current queue
        if (currentTrack != null && isPlaying) {
            if (!playbackStateHolder.stablePlayerState.value.isShuffleEnabled) {
                toggleShuffle()
            }
            return
        }
        
        // Otherwise start a new shuffled queue
        val allTracks = _masterAllTracks.value
        if (allTracks.isNotEmpty()) {
            playTracksShuffled(allTracks, "All Tracks (Shuffled)")
        }
    }

    fun playRandomTrack() {
        val allTracks = _masterAllTracks.value
        if (allTracks.isNotEmpty()) {
            playTracksShuffled(allTracks, "All Tracks (Shuffled)")
        }
    }

    fun shuffleFavoriteTracks() {
        Log.d("ShuffleDebug", "shuffleFavoriteTracks called.")
        // Don't use ExoPlayer's shuffle mode - we manually shuffle instead
        val currentTrack = playbackStateHolder.stablePlayerState.value.currentTrack
        val isPlaying = playbackStateHolder.stablePlayerState.value.isPlaying
        
        // If something is playing, just toggle shuffle on current queue
        if (currentTrack != null && isPlaying) {
            if (!playbackStateHolder.stablePlayerState.value.isShuffleEnabled) {
                toggleShuffle()
            }
            return
        }
        
        // Otherwise start a new shuffled queue
        val favTracks = favoriteTracks.value
        if (favTracks.isNotEmpty()) {
            playTracksShuffled(favTracks, "Liked Tracks (Shuffled)")
        }
    }

    fun shuffleRandomBook() {
        val allBooks = _playerUiState.value.Books
        if (allBooks.isNotEmpty()) {
            val randomBook = allBooks.random()
            val BookTracks = _masterAllTracks.value.filter { it.BookId == randomBook.id }
            if (BookTracks.isNotEmpty()) {
                playTracksShuffled(BookTracks, randomBook.title)
            }
        }
    }

    fun shuffleRandomAuthor() {
        val allAuthors = _playerUiState.value.Authors
        if (allAuthors.isNotEmpty()) {
            val randomAuthor = allAuthors.random()
            val AuthorTracks = _masterAllTracks.value.filter { it.AuthorId == randomAuthor.id }
            if (AuthorTracks.isNotEmpty()) {
                playTracksShuffled(AuthorTracks, randomAuthor.name)
            }
        }
    }


    private fun loadPersistedDailyMix() {
        // Delegate to DailyMixStateHolder
        dailyMixStateHolder.loadPersistedDailyMix(allTracksFlow)
    }

    fun forceUpdateDailyMix() {
        // Delegate to DailyMixStateHolder
        dailyMixStateHolder.forceUpdate(
            allTracksFlow = allTracksFlow,
            favoriteTrackIdsFlow = userPreferencesRepository.favoriteTrackIdsFlow
        )
    }

    private var transitionSchedulerJob: Job? = null

    private fun incrementTrackscore(Track: Track) {
        listeningStatsTracker.onVoluntarySelection(Track.id)
    }

    // MIN_SESSION_LISTEN_MS, currentSession, and ListeningStatsTracker class
    // have been moved to ListeningStatsTracker.kt for better modularity


    fun updatePredictiveBackCollapseFraction(fraction: Float) {
        _predictiveBackCollapseFraction.value = fraction.coerceIn(0f, 1f)
    }

    // Helper to resolve stored sort keys against the allowed group
    private fun resolveSortOption(
        optionKey: String?,
        allowed: Collection<SortOption>,
        fallback: SortOption
    ): SortOption {
        return SortOption.fromStorageKey(optionKey, allowed, fallback)
    }

    private fun MediaRouter.RouteInfo.isCastRoute(): Boolean {
        return supportsControlCategory(MediaControlIntent.CATEGORY_REMOTE_PLAYBACK) ||
            supportsControlCategory(castControlCategory)
    }

    // Connectivity refresh delegated to ConnectivityStateHolder
    fun refreshLocalConnectionInfo() {
        connectivityStateHolder.refreshLocalConnectionInfo()
    }

    init {
        Log.i("PlayerViewModel", "init started.")

        // Cast initialization if already connected
        val currentSession = sessionManager.currentCastSession
        if (currentSession != null) {
            castStateHolder.setCastPlayer(CastPlayer(currentSession))
            castStateHolder.setRemotePlaybackActive(true)
        }



        viewModelScope.launch {
            userPreferencesRepository.migrateTabOrder()
        }

        viewModelScope.launch {
            userPreferencesRepository.ensureLibrarySortDefaults()
        }

        viewModelScope.launch {
            userPreferencesRepository.isFoldersBooklistViewFlow.collect { isBooklistView ->
                setFoldersBooklistViewState(isBooklistView)
            }
        }

        viewModelScope.launch {
            userPreferencesRepository.blockedDirectoriesFlow
                .distinctUntilChanged()
                .collect { blocked ->
                    if (lastBlockedDirectories == null) {
                        lastBlockedDirectories = blocked
                        return@collect
                    }

                    if (blocked != lastBlockedDirectories) {
                        lastBlockedDirectories = blocked
                        onBlockedDirectoriesChanged()
                    }
                }
        }

        viewModelScope.launch {
            combine(libraryTabsFlow, lastLibraryTabIndexFlow) { tabs, index ->
                tabs.getOrNull(index)?.toLibraryTabIdOrNull() ?: LibraryTabId.Tracks
            }.collect { tabId ->
                _currentLibraryTabId.value = tabId
            }
        }

        // Load initial sort options ONCE at startup.
        viewModelScope.launch {
            val initialTracksort = resolveSortOption(
                userPreferencesRepository.TracksSortOptionFlow.first(),
                SortOption.Tracks,
                SortOption.TrackTitleAZ
            )
            val initialBooksort = resolveSortOption(
                userPreferencesRepository.BooksSortOptionFlow.first(),
                SortOption.Books,
                SortOption.BookTitleAZ
            )
            val initialAuthorsort = resolveSortOption(
                userPreferencesRepository.AuthorsSortOptionFlow.first(),
                SortOption.Authors,
                SortOption.AuthorNameAZ
            )
            val initialLikedSort = resolveSortOption(
                userPreferencesRepository.likedTracksSortOptionFlow.first(),
                SortOption.LIKED,
                SortOption.LikedTrackDateLiked
            )

            _playerUiState.update {
                it.copy(
                    currentTracksortOption = initialTracksort,
                    currentBooksortOption = initialBooksort,
                    currentAuthorsortOption = initialAuthorsort,
                    currentFavoriteSortOption = initialLikedSort
                )
            }
            // Also update the dedicated flow for favorites to ensure consistency
            // _currentFavoriteSortOptionStateFlow.value = initialLikedSort // Delegated to LibraryStateHolder

            sortTracks(initialTracksort, persist = false)
            sortBooks(initialBooksort, persist = false)
            sortAuthors(initialAuthorsort, persist = false)
            sortFavoriteTracks(initialLikedSort, persist = false)
        }

        viewModelScope.launch {
            val isPersistent = userPreferencesRepository.persistentShuffleEnabledFlow.first()
            if (isPersistent) {
                // If persistent shuffle is on, read the last used shuffle state (On/Off)
                val savedShuffle = userPreferencesRepository.isShuffleOnFlow.first()
                // Update the UI state so the shuffle button reflects the saved setting immediately
                playbackStateHolder.updateStablePlayerState { it.copy(isShuffleEnabled = savedShuffle) }
            }
        }

        // launchColorSchemeProcessor() - Handled by ThemeStateHolder and on-demand calls

        loadPersistedDailyMix()
        loadSearchHistory()

        viewModelScope.launch {
            isSyncingStateFlow.collect { isSyncing ->
                val oldSyncingLibraryState = _playerUiState.value.isSyncingLibrary
                _playerUiState.update { it.copy(isSyncingLibrary = isSyncing) }

        if (oldSyncingLibraryState && !isSyncing) {
            Log.i("PlayerViewModel", "Sync completed. Calling resetAndLoadInitialData from isSyncingStateFlow observer.")
                    resetAndLoadInitialData("isSyncingStateFlow observer")
                }
            }
        }

        viewModelScope.launch {
            if (!isSyncingStateFlow.value && !_isInitialDataLoaded.value && _masterAllTracks.value.isEmpty()) {
                Log.i("PlayerViewModel", "Initial check: Sync not active and initial data not loaded. Calling resetAndLoadInitialData.")
                resetAndLoadInitialData("Initial Check")
            }
        }

        mediaControllerFuture.addListener({
            try {
                mediaController = mediaControllerFuture.get()
                // Pass controller to PlaybackStateHolder
                playbackStateHolder.setMediaController(mediaController)
                
                setupMediaControllerListeners()
                flushPendingRepeatMode()
                syncShuffleStateWithSession(playbackStateHolder.stablePlayerState.value.isShuffleEnabled)
                // Execute any pending action that was queued while the controller was connecting
                pendingPlaybackAction?.invoke()
                pendingPlaybackAction = null
            } catch (e: Exception) {
                _playerUiState.update { it.copy(isLoadingInitialTracks = false, isLoadingLibraryCategories = false) }
                Log.e("PlayerViewModel", "Error setting up MediaController", e)
            }
        }, ContextCompat.getMainExecutor(context))

        
        // Start Cast discovery
        castStateHolder.startDiscovery()
        
        // Observe selection for HTTP server management
        viewModelScope.launch {
            castStateHolder.selectedRoute.collect { route ->
                if (route != null && !route.isDefault && route.supportsControlCategory(MediaControlIntent.CATEGORY_REMOTE_PLAYBACK)) {
                     castTransferStateHolder.ensureHttpServerRunning()
                } else if (route?.isDefault == true) {
                     context.stopService(Intent(context, MediaFileHttpServerService::class.java))
                }
            }
        }

        // Initialize connectivity monitoring (WiFi/Bluetooth)
        connectivityStateHolder.initialize()

        // Initialize sleep timer state holder
        sleepTimerStateHolder.initialize(
            scope = viewModelScope,
            toastEmitter = { msg -> _toastEvents.emit(msg) },
            mediaControllerProvider = { mediaController },
            currentTrackIdProvider = { stablePlayerState.map { it.currentTrack?.id }.stateIn(viewModelScope, SharingStarted.Eagerly, null) },
            TrackTitleResolver = { TrackId -> _masterAllTracks.value.find { it.id == TrackId }?.title ?: "Unknown" }
        )

        // Initialize SearchStateHolder
        searchStateHolder.initialize(viewModelScope)

        // Collect SearchStateHolder flows
        viewModelScope.launch {
            searchStateHolder.searchResults.collect { results ->
                _playerUiState.update { it.copy(searchResults = results) }
            }
        }
        viewModelScope.launch {
            searchStateHolder.selectedSearchFilter.collect { filter ->
                _playerUiState.update { it.copy(selectedSearchFilter = filter) }
            }
        }
        viewModelScope.launch {
            searchStateHolder.searchHistory.collect { history ->
                _playerUiState.update { it.copy(searchHistory = history) }
            }
        }

        // Initialize AiStateHolder
        aiStateHolder.initialize(
            scope = viewModelScope,
            allTracksProvider = { _masterAllTracks.value },
            favoriteTrackIdsProvider = { favoriteTrackIds.value },
            toastEmitter = { msg -> viewModelScope.launch { _toastEvents.emit(msg) } },
            playTracksCallback = { Tracks, startTrack, queueName -> playTracks(Tracks, startTrack, queueName) },
            openPlayerSheetCallback = { _isSheetVisible.value = true }
        )

        // Collect AiStateHolder flows
        viewModelScope.launch {
            aiStateHolder.showAiBooklistsheet.collect { show ->
                _showAiBooklistsheet.value = show
            }
        }
        viewModelScope.launch {
            aiStateHolder.isGeneratingAiBooklist.collect { generating ->
                _isGeneratingAiBooklist.value = generating
            }
        }
        viewModelScope.launch {
            aiStateHolder.aiError.collect { error ->
                _aiError.value = error
            }
        }
        viewModelScope.launch {
            aiStateHolder.isGeneratingMetadata.collect { generating ->
                _playerUiState.update { it.copy(isGeneratingAiMetadata = generating) }
            }
        }

        viewModelScope.launch {
            aiStateHolder.isGeneratingMetadata.collect { generating ->
                _playerUiState.update { it.copy(isGeneratingAiMetadata = generating) }
            }
        }

        // Initialize LibraryStateHolder
        libraryStateHolder.initialize(viewModelScope)

        // Collect LibraryStateHolder flows to sync with UI State
        viewModelScope.launch {
            libraryStateHolder.allTracks.collect { Tracks ->
                _playerUiState.update { it.copy(allTracks = Tracks, TrackCount = Tracks.size) }
                // Update master Tracks for Cast usage if needed
                _masterAllTracks.value = Tracks
            }
        }
        viewModelScope.launch {
            libraryStateHolder.Books.collect { Books ->
                _playerUiState.update { it.copy(Books = Books) }
            }
        }
        viewModelScope.launch {
            libraryStateHolder.Authors.collect { Authors ->
                _playerUiState.update { it.copy(Authors = Authors) }
            }
        }
        viewModelScope.launch {
            libraryStateHolder.AudiobookFolders.collect { folders ->
                _playerUiState.update { it.copy(AudiobookFolders = folders) }
            }
        }
        // Sync loading states
        viewModelScope.launch {
            libraryStateHolder.isLoadingLibrary.collect { loading ->
                _playerUiState.update { it.copy(isLoadingInitialTracks = loading) }
            }
        }
        viewModelScope.launch {
            libraryStateHolder.isLoadingCategories.collect { loading ->
                _playerUiState.update { it.copy(isLoadingLibraryCategories = loading) }
            }
        }
        
        // Sync sort options
        viewModelScope.launch {
            libraryStateHolder.currentTracksortOption.collect { sort ->
                _playerUiState.update { it.copy(currentTracksortOption = sort) }
            }
        }
        viewModelScope.launch {
            libraryStateHolder.currentBooksortOption.collect { sort ->
                _playerUiState.update { it.copy(currentBooksortOption = sort) }
            }
        }
        viewModelScope.launch {
            libraryStateHolder.currentAuthorsortOption.collect { sort ->
                _playerUiState.update { it.copy(currentAuthorsortOption = sort) }
            }
        }
        viewModelScope.launch {
            libraryStateHolder.currentFolderSortOption.collect { sort ->
                _playerUiState.update { it.copy(currentFolderSortOption = sort) }
            }
        }
        viewModelScope.launch {
            libraryStateHolder.currentFavoriteSortOption.collect { sort ->
                _playerUiState.update { it.copy(currentFavoriteSortOption = sort) }
            }
        }


        castTransferStateHolder.initialize(
            scope = viewModelScope,
            getCurrentQueue = { _playerUiState.value.currentPlaybackQueue },
            updateQueue = { newQueue -> 
                _playerUiState.update { 
                    it.copy(currentPlaybackQueue = newQueue.toImmutableList()) 
                }
            },
            getMasterAllTracks = { _masterAllTracks.value },
            onTransferBackComplete = { startProgressUpdates() },
            onSheetVisible = { _isSheetVisible.value = true },
            onDisconnect = { disconnect() },
            onTrackChanged = { uriString ->
                uriString?.toUri()?.let { uri ->
                     viewModelScope.launch { 
                         val currentUri = playbackStateHolder.stablePlayerState.value.currentTrack?.BookArtUriString
                         themeStateHolder.extractAndGenerateColorScheme(uri, currentUri) 
                     }
                }
            }
        )



        viewModelScope.launch {
            userPreferencesRepository.repeatModeFlow.collect { mode ->
                applyPreferredRepeatMode(mode)
            }
        }

        viewModelScope.launch {
            stablePlayerState
                .map { it.isShuffleEnabled }
                .distinctUntilChanged()
                .collect { enabled ->
                    syncShuffleStateWithSession(enabled)
                }
        }

        // Auto-hide undo bar when a new Track starts playing
        setupUndoBarPlaybackObserver()

        Trace.endSection() // End PlayerViewModel.init
    }

    fun onMainActivityStart() {
        Trace.beginSection("PlayerViewModel.onMainActivityStart")
        preloadThemesAndInitialData()
        checkAndUpdateDailyMixIfNeeded()
        Trace.endSection()
    }


    private fun checkAndUpdateDailyMixIfNeeded() {
        // Delegate to DailyMixStateHolder
        dailyMixStateHolder.checkAndUpdateIfNeeded(
            allTracksFlow = allTracksFlow,
            favoriteTrackIdsFlow = userPreferencesRepository.favoriteTrackIdsFlow
        )
    }

    private fun preloadThemesAndInitialData() {
        Trace.beginSection("PlayerViewModel.preloadThemesAndInitialData")
        viewModelScope.launch {
            _isInitialThemePreloadComplete.value = false
            if (isSyncingStateFlow.value && !_isInitialDataLoaded.value) {
                // Sync is active - defer to sync completion handler
            } else if (!_isInitialDataLoaded.value && _masterAllTracks.value.isEmpty()) {
                resetAndLoadInitialData("preloadThemesAndInitialData")
            }
            _isInitialThemePreloadComplete.value = true
        }
        Trace.endSection()
    }

    private fun loadInitialLibraryDataParallel() {
        libraryStateHolder.loadTracksFromRepository()
        libraryStateHolder.loadBooksFromRepository()
        libraryStateHolder.loadAuthorsFromRepository()
        libraryStateHolder.loadFoldersFromRepository()
    }

    private fun resetAndLoadInitialData(caller: String = "Unknown") {
        Trace.beginSection("PlayerViewModel.resetAndLoadInitialData")
        loadInitialLibraryDataParallel()
        updateDailyMix()
        Trace.endSection()
    }

    fun loadTracksIfNeeded() = libraryStateHolder.loadTracksIfNeeded()
    fun loadBooksIfNeeded() = libraryStateHolder.loadBooksIfNeeded()
    fun loadAuthorsIfNeeded() = libraryStateHolder.loadAuthorsIfNeeded()
    fun loadFoldersFromRepository() = libraryStateHolder.loadFoldersFromRepository()

    fun showAndPlayTrack(
        Track: Track,
        contextTracks: List<Track>,
        queueName: String = "Current Context",
        isVoluntaryPlay: Boolean = true
    ) {
        val castSession = castStateHolder.castSession.value
        if (castSession != null && castSession.remoteMediaClient != null) {
            val remoteMediaClient = castSession.remoteMediaClient!!
            val mediaStatus = remoteMediaClient.mediaStatus
            val remoteQueueItems = mediaStatus?.queueItems ?: emptyList()
            val itemInQueue = remoteQueueItems.find { it.customData?.optString("TrackId") == Track.id }

            if (itemInQueue != null) {
                // Track is already in the remote queue; prefer adjacent navigation commands to
                // mirror the no-glitch behavior of next/previous buttons regardless of context
                // mismatches.
                castTransferStateHolder.markPendingRemoteTrack(Track)
                val currentItemId = mediaStatus?.currentItemId
                val currentIndex = remoteQueueItems.indexOfFirst { it.itemId == currentItemId }
                val targetIndex = remoteQueueItems.indexOf(itemInQueue)
                val castPlayer = castStateHolder.castPlayer
                when {
                    currentIndex >= 0 && targetIndex - currentIndex == 1 -> castPlayer?.next()
                    currentIndex >= 0 && targetIndex - currentIndex == -1 -> castPlayer?.previous()
                    else -> castPlayer?.jumpToItem(itemInQueue.itemId, 0L)
                }
                if (isVoluntaryPlay) incrementTrackscore(Track)
            } else {
                val lastQueue = castTransferStateHolder.lastRemoteQueue
                val currentRemoteId = mediaStatus
                    ?.let { status ->
                        status.getQueueItemById(status.getCurrentItemId())
                            ?.customData?.optString("TrackId")
                    } ?: castTransferStateHolder.lastRemoteTrackId
                val currentIndex = lastQueue.indexOfFirst { it.id == currentRemoteId }
                val targetIndex = lastQueue.indexOfFirst { it.id == Track.id }
                if (currentIndex != -1 && targetIndex != -1) {
                    castTransferStateHolder.markPendingRemoteTrack(Track)
                    val castPlayer = castStateHolder.castPlayer
                    when (targetIndex - currentIndex) {
                        1 -> castPlayer?.next()
                        -1 -> castPlayer?.previous()
                        else -> {
                           viewModelScope.launch {
                               castTransferStateHolder.playRemoteQueue(contextTracks, Track, playbackStateHolder.stablePlayerState.value.isShuffleEnabled)
                           }
                        }
                    }
                } else {
                    viewModelScope.launch {
                        castTransferStateHolder.playRemoteQueue(contextTracks, Track, playbackStateHolder.stablePlayerState.value.isShuffleEnabled)
                    }
                }
                if (isVoluntaryPlay) incrementTrackscore(Track)
            }
            return
        }    // Local playback logic
            mediaController?.let { controller ->
                val currentQueue = _playerUiState.value.currentPlaybackQueue
                val TrackIndexInQueue = currentQueue.indexOfFirst { it.id == Track.id }
                val queueMatchesContext = currentQueue.matchesTrackOrder(contextTracks)

                if (TrackIndexInQueue != -1 && queueMatchesContext) {
                    if (controller.currentMediaItemIndex == TrackIndexInQueue) {
                        if (!controller.isPlaying) controller.play()
                    } else {
                        controller.seekTo(TrackIndexInQueue, 0L)
                        controller.play()
                    }
                    if (isVoluntaryPlay) incrementTrackscore(Track)
                } else {
                    if (isVoluntaryPlay) incrementTrackscore(Track)
                    playTracks(contextTracks, Track, queueName, null)
                }
            }
            _predictiveBackCollapseFraction.value = 0f
        }

    fun showAndPlayTrack(Track: Track) {
        Log.d("ShuffleDebug", "showAndPlayTrack (single Track overload) called for '${Track.title}'")
        val allTracks = _masterAllTracks.value.toList()
        // Look up the current version of the Track in allTracks to get the most up-to-date metadata
        val currentTrack = allTracks.find { it.id == Track.id } ?: Track
        showAndPlayTrack(currentTrack, allTracks, "Library")
    }

    private fun List<Track>.matchesTrackOrder(contextTracks: List<Track>): Boolean {
        if (size != contextTracks.size) return false
        return indices.all { this[it].id == contextTracks[it].id }
    }

    fun playBook(Book: Book) {
        Log.d("ShuffleDebug", "playBook called for Book: ${Book.title}")
        viewModelScope.launch {
            try {
                val TracksList: List<Track> = withContext(Dispatchers.IO) {
                    AudiobookRepository.getTracksForBook(Book.id).first()
                }

                if (TracksList.isNotEmpty()) {
                    val sortedTracks = TracksList.sortedWith(
                        compareBy<Track> {
                            if (it.trackNumber > 0) it.trackNumber else Int.MAX_VALUE
                        }.thenBy { it.title.lowercase() }
                    )

                    playTracks(sortedTracks, sortedTracks.first(), Book.title, null)
                    _isSheetVisible.value = true // Mostrar reproductor
                } else {
                    Log.w("PlayerViewModel", "Book '${Book.title}' has no playable Tracks.")
                }
            } catch (e: Exception) {
                Log.e("PlayerViewModel", "Error playing Book ${Book.title}", e)
            }
        }
    }

    fun playAuthor(Author: Author) {
        Log.d("ShuffleDebug", "playAuthor called for Author: ${Author.name}")
        viewModelScope.launch {
            try {
                val TracksList: List<Track> = withContext(Dispatchers.IO) {
                    AudiobookRepository.getTracksForAuthor(Author.id).first()
                }

                if (TracksList.isNotEmpty()) {
                    playTracks(TracksList, TracksList.first(), Author.name, null)
                    _isSheetVisible.value = true
                } else {
                    Log.w("PlayerViewModel", "Author '${Author.name}' has no playable Tracks.")
                    // podrÃƒÂ­as emitir un evento Toast
                }
            } catch (e: Exception) {
                Log.e("PlayerViewModel", "Error playing Author ${Author.name}", e)
            }
        }
    }

    fun removeTrackFromQueue(TrackId: String) {
        mediaController?.let { controller ->
            val currentQueue = _playerUiState.value.currentPlaybackQueue
            val indexToRemove = currentQueue.indexOfFirst { it.id == TrackId }

            if (indexToRemove != -1) {
                // Command the player to remove the item. This is the source of truth for playback.
                controller.removeMediaItem(indexToRemove)

            }
        }
    }

    fun reorderQueueItem(fromIndex: Int, toIndex: Int) {
        mediaController?.let { controller ->
            if (fromIndex >= 0 && fromIndex < controller.mediaItemCount &&
                toIndex >= 0 && toIndex < controller.mediaItemCount) {

                // Move the item in the MediaController's timeline.
                // This is the source of truth for playback.
                controller.moveMediaItem(fromIndex, toIndex)

            }
        }
    }

    fun togglePlayerSheetState() {
        _sheetState.value = if (_sheetState.value == PlayerSheetState.COLLAPSED) {
            PlayerSheetState.EXPANDED
        } else {
            PlayerSheetState.COLLAPSED
        }
        _predictiveBackCollapseFraction.value = 0f
    }

    fun expandPlayerSheet() {
        _sheetState.value = PlayerSheetState.EXPANDED
        _predictiveBackCollapseFraction.value = 0f
    }

    fun collapsePlayerSheet() {
        _sheetState.value = PlayerSheetState.COLLAPSED
        _predictiveBackCollapseFraction.value = 0f
    }

    fun triggerAuthorNavigationFromPlayer(AuthorId: Long) {
        if (AuthorId <= 0) {
            Log.d("AuthorDebug", "triggerAuthorNavigationFromPlayer ignored invalid AuthorId=$AuthorId")
            return
        }

        val existingJob = AuthorNavigationJob
        if (existingJob != null && existingJob.isActive) {
            Log.d("AuthorDebug", "triggerAuthorNavigationFromPlayer ignored; navigation already in progress for AuthorId=$AuthorId")
            return
        }

        AuthorNavigationJob?.cancel()
        AuthorNavigationJob = viewModelScope.launch {
            val currentTrack = playbackStateHolder.stablePlayerState.value.currentTrack
            Log.d(
                "AuthorDebug",
                "triggerAuthorNavigationFromPlayer: AuthorId=$AuthorId, TrackId=${currentTrack?.id}, title=${currentTrack?.title}"
            )
            collapsePlayerSheet()

            withTimeoutOrNull(900) {
                awaitSheetState(PlayerSheetState.COLLAPSED)
                awaitPlayerCollapse()
            }

            _AuthorNavigationRequests.emit(AuthorId)
        }
    }

    suspend fun awaitSheetState(target: PlayerSheetState) {
        sheetState.first { it == target }
    }

    suspend fun awaitPlayerCollapse(threshold: Float = 0.1f, timeoutMillis: Long = 800L) {
        withTimeoutOrNull(timeoutMillis) {
            snapshotFlow { playerContentExpansionFraction.value }
                .first { it <= threshold }
        }
    }

    private fun resolveTrackFromMediaItem(mediaItem: MediaItem): Track? {
        _playerUiState.value.currentPlaybackQueue.find { it.id == mediaItem.mediaId }?.let { return it }
        _masterAllTracks.value.find { it.id == mediaItem.mediaId }?.let { return it }

        return mediaMapper.resolveTrackFromMediaItem(mediaItem)
    }

    private fun updateCurrentPlaybackQueueFromPlayer(playerCtrl: MediaController?) {
        val currentMediaController = playerCtrl ?: mediaController ?: return
        val count = currentMediaController.mediaItemCount

        if (count == 0) {
            _playerUiState.update { it.copy(currentPlaybackQueue = persistentListOf()) }
            return
        }

        val queue = mutableListOf<Track>()

        for (i in 0 until count) {
            val mediaItem = currentMediaController.getMediaItemAt(i)
            resolveTrackFromMediaItem(mediaItem)?.let { queue.add(it) }
        }

        _playerUiState.update { it.copy(currentPlaybackQueue = queue.toImmutableList()) }
        if (queue.isNotEmpty()) {
            _isSheetVisible.value = true
        }
    }

    private fun applyPreferredRepeatMode(@Player.RepeatMode mode: Int) {
        playbackStateHolder.updateStablePlayerState { it.copy(repeatMode = mode) }

        val castSession = castStateHolder.castSession.value
        if (castSession != null && castSession.remoteMediaClient != null) {
            pendingRepeatMode = mode
            return
        }

        val controller = mediaController
        if (controller == null) {
            pendingRepeatMode = mode
            return
        }

        if (controller.repeatMode != mode) {
            controller.repeatMode = mode
        }
        pendingRepeatMode = null
    }

    private fun flushPendingRepeatMode() {
        pendingRepeatMode?.let { applyPreferredRepeatMode(it) }
    }

    private fun setupMediaControllerListeners() {
        Trace.beginSection("PlayerViewModel.setupMediaControllerListeners")
        val playerCtrl = mediaController ?: return Trace.endSection()
        _trackVolume.value = playerCtrl.volume
        playbackStateHolder.updateStablePlayerState {
            it.copy(
                isShuffleEnabled = it.isShuffleEnabled,
                repeatMode = playerCtrl.repeatMode,
                isPlaying = playerCtrl.isPlaying
            )
        }

        updateCurrentPlaybackQueueFromPlayer(playerCtrl)

        playerCtrl.currentMediaItem?.let { mediaItem ->
            val Track = resolveTrackFromMediaItem(mediaItem)

            if (Track != null) {
                playbackStateHolder.updateStablePlayerState {
                    it.copy(
                        currentTrack = Track,
                        totalDuration = playerCtrl.duration.coerceAtLeast(0L)
                    )
                }
                _playerUiState.update { it.copy(currentPosition = playerCtrl.currentPosition.coerceAtLeast(0L)) }
                viewModelScope.launch {
                    Track.BookArtUriString?.toUri()?.let { uri ->
                        val currentUri = playbackStateHolder.stablePlayerState.value.currentTrack?.BookArtUriString
                        themeStateHolder.extractAndGenerateColorScheme(uri, currentUri)
                    }
                }
                listeningStatsTracker.onTrackChanged(
                    Track = Track,
                    positionMs = playerCtrl.currentPosition.coerceAtLeast(0L),
                    durationMs = playerCtrl.duration.coerceAtLeast(0L),
                    isPlaying = playerCtrl.isPlaying
                )
                if (playerCtrl.isPlaying) {
                    _isSheetVisible.value = true
                    startProgressUpdates()
                }
            } else {
                playbackStateHolder.updateStablePlayerState { it.copy(currentTrack = null, isPlaying = false) }
                _playerUiState.update { it.copy(currentPosition = 0L) }
            }
        }

        playerCtrl.addListener(object : Player.Listener {
            override fun onVolumeChanged(volume: Float) {
                _trackVolume.value = volume
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playbackStateHolder.updateStablePlayerState { it.copy(isPlaying = isPlaying) }
                listeningStatsTracker.onPlayStateChanged(
                    isPlaying = isPlaying,
                    positionMs = playerCtrl.currentPosition.coerceAtLeast(0L)
                )
                if (isPlaying) {
                    _isSheetVisible.value = true
                    if (_playerUiState.value.preparingTrackId != null) {
                        _playerUiState.update { it.copy(preparingTrackId = null) }
                    }
                    startProgressUpdates()
                } else {
                    stopProgressUpdates()
                    val pausedPosition = playerCtrl.currentPosition.coerceAtLeast(0L)
                    if (pausedPosition != _playerUiState.value.currentPosition) {
                        _playerUiState.update { it.copy(currentPosition = pausedPosition) }
                    }
                }
            }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                transitionSchedulerJob?.cancel()
                TranscriptStateHolder.cancelLoading()
                transitionSchedulerJob = viewModelScope.launch {
                    if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                        val activeEotTrackId = EotStateHolder.eotTargetTrackId.value
                        val previousTrackId = playerCtrl.run { if (previousMediaItemIndex != C.INDEX_UNSET) getMediaItemAt(previousMediaItemIndex).mediaId else null }

                        if (isEndOfTrackTimerActive.value && activeEotTrackId != null && previousTrackId != null && previousTrackId == activeEotTrackId) {
                            playerCtrl.seekTo(0L)
                            playerCtrl.pause()

                            val finishedTrackTitle = _masterAllTracks.value.find { it.id == previousTrackId }?.title
                                ?: "Track"

                            viewModelScope.launch {
                                _toastEvents.emit("Playback stopped: $finishedTrackTitle finished (End of Track).")
                            }
                            cancelSleepTimer(suppressDefaultToast = true)
                        }
                    }

                    mediaItem?.let { transitionedItem ->
                        listeningStatsTracker.finalizeCurrentSession()
                        val Track = resolveTrackFromMediaItem(transitionedItem)
                        resetTranscriptSearchState()
                        playbackStateHolder.updateStablePlayerState {
                            val hasTrack = Track != null
                            it.copy(
                                currentTrack = Track,
                                totalDuration = if (hasTrack) playerCtrl.duration.coerceAtLeast(0L) else 0L,
                                Transcript = null,
                                isLoadingTranscript = hasTrack
                            )
                        }
                        _playerUiState.update { it.copy(currentPosition = 0L) }

                        Track?.let { currentTrackValue ->
                            listeningStatsTracker.onTrackChanged(
                                Track = currentTrackValue,
                                positionMs = 0L,
                                durationMs = playerCtrl.duration.coerceAtLeast(0L),
                                isPlaying = playerCtrl.isPlaying
                            )
                            viewModelScope.launch {
                                currentTrackValue.BookArtUriString?.toUri()?.let { uri ->
                                    val currentUri = playbackStateHolder.stablePlayerState.value.currentTrack?.BookArtUriString
                                    themeStateHolder.extractAndGenerateColorScheme(uri, currentUri)
                                }
                            }
                            loadTranscriptForCurrentTrack()
                        }
                    } ?: run {
                        if (!isCastConnecting.value && !isRemotePlaybackActive.value) {
                            TranscriptStateHolder.cancelLoading()
                            playbackStateHolder.updateStablePlayerState {
                                it.copy(
                                    currentTrack = null,
                                    isPlaying = false,
                                    Transcript = null,
                                    isLoadingTranscript = false,
                                    totalDuration = 0L
                                )
                            }
                        }
                    }
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    playbackStateHolder.updateStablePlayerState { it.copy(totalDuration = playerCtrl.duration.coerceAtLeast(0L)) }
                    listeningStatsTracker.updateDuration(playerCtrl.duration.coerceAtLeast(0L))
                    startProgressUpdates()
                }
                if (playbackState == Player.STATE_ENDED) {
                    listeningStatsTracker.finalizeCurrentSession()
                }
                if (playbackState == Player.STATE_IDLE && playerCtrl.mediaItemCount == 0) {
                    if (!isCastConnecting.value && !isRemotePlaybackActive.value) {
                        listeningStatsTracker.onPlaybackStopped()
                        TranscriptStateHolder.cancelLoading()
                        playbackStateHolder.updateStablePlayerState {
                            it.copy(
                                currentTrack = null,
                                isPlaying = false,
                                Transcript = null,
                                isLoadingTranscript = false,
                                totalDuration = 0L
                            )
                        }
                        _playerUiState.update { it.copy(currentPosition = 0L) }
                    }
                }
            }
            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                // IMPORTANT: We don't use ExoPlayer's shuffle mode anymore
                // Instead, we manually shuffle the queue to fix crossfade issues
                // If ExoPlayer's shuffle gets enabled (e.g., from media button), turn it off and use our toggle
                if (shuffleModeEnabled) {
                    playerCtrl.shuffleModeEnabled = false
                    // Trigger our manual shuffle instead
                    if (!playbackStateHolder.stablePlayerState.value.isShuffleEnabled) {
                        toggleShuffle()
                    }
                }
            }
            override fun onRepeatModeChanged(repeatMode: Int) {
                playbackStateHolder.updateStablePlayerState { it.copy(repeatMode = repeatMode) }
                viewModelScope.launch { userPreferencesRepository.setRepeatMode(repeatMode) }
            }
            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                transitionSchedulerJob?.cancel()
                updateCurrentPlaybackQueueFromPlayer(mediaController)
            }
        })
        Trace.endSection()
    }


    // rebuildPlayerQueue functionality moved to PlaybackStateHolder (simplified)
    fun playTracks(TracksToPlay: List<Track>, startTrack: Track, queueName: String = "None", BooklistId: String? = null) {
        viewModelScope.launch {
            transitionSchedulerJob?.cancel()
            
            // Validate Tracks - filter out any with missing files (efficient: uses contentUri check)
            val validTracks = TracksToPlay.filter { Track ->
                try {
                    // Use ContentResolver to check if URI is still valid (more efficient than File check)
                    val uri = Track.contentUriString.toUri()
                    context.contentResolver.openInputStream(uri)?.use { true } ?: false
                } catch (e: Exception) {
                    Timber.w("Track file missing or inaccessible: ${Track.title}")
                    false
                }
            }
            
            if (validTracks.isEmpty()) {
                _toastEvents.emit(context.getString(R.string.no_valid_Tracks))
                return@launch
            }
            
            // Adjust startTrack if it was filtered out
            val validStartTrack = if (validTracks.contains(startTrack)) startTrack else validTracks.first()
            
            // Store the original order so we can "unshuffle" later if the user turns shuffle off
            queueStateHolder.setOriginalQueueOrder(validTracks)
            queueStateHolder.saveOriginalQueueState(validTracks, queueName)

            // Check if the user wants shuffle to be persistent across different Books
            val isPersistent = userPreferencesRepository.persistentShuffleEnabledFlow.first()
            // Check if shuffle is currently active in the player
            val isShuffleOn = playbackStateHolder.stablePlayerState.value.isShuffleEnabled

            // If Persistent Shuffle is OFF, we reset shuffle to "false" every time a new Book starts
            if (!isPersistent) {
                playbackStateHolder.updateStablePlayerState { it.copy(isShuffleEnabled = false) }
            }

            // If shuffle is persistent and currently ON, we shuffle the new Tracks immediately
            val finalTracksToPlay = if (isPersistent && isShuffleOn) {
                // Shuffle the list but make sure the Track you clicked stays at its current index or starts first
                QueueUtils.buildAnchoredShuffleQueue(validTracks, validTracks.indexOf(validStartTrack).coerceAtLeast(0))
            } else {
                // Otherwise, just use the normal sequential order
                validTracks
            }

            // Send the final list (shuffled or not) to the player engine
            internalPlayTracks(finalTracksToPlay, validStartTrack, queueName, BooklistId)
        }
    }

    // Start playback with shuffle enabled in one coroutine to avoid racing queue updates
    fun playTracksShuffled(TracksToPlay: List<Track>, queueName: String = "None", BooklistId: String? = null) {
        viewModelScope.launch {
            val result = queueStateHolder.prepareShuffledQueue(TracksToPlay, queueName)
            if (result == null) {
                sendToast("No Tracks to shuffle.")
                return@launch
            }
            
            val (shuffledQueue, startTrack) = result
            transitionSchedulerJob?.cancel()

            // Optimistically update shuffle state
            playbackStateHolder.updateStablePlayerState { it.copy(isShuffleEnabled = true) }
            launch { userPreferencesRepository.setShuffleOn(true) }

            internalPlayTracks(shuffledQueue, startTrack, queueName, BooklistId)
        }
    }

    fun playExternalUri(uri: Uri) {
        viewModelScope.launch {
            val externalResult = externalMediaStateHolder.buildExternalTrackFromUri(uri)
            if (externalResult == null) {
                sendToast(context.getString(R.string.external_playback_error))
                return@launch
            }

            transitionSchedulerJob?.cancel()

            val queueTracks = externalMediaStateHolder.buildExternalQueue(externalResult, uri)
            val immutableQueue = queueTracks.toImmutableList()

            _playerUiState.update { state ->
                state.copy(
                    currentPosition = 0L,
                    currentPlaybackQueue = immutableQueue,
                    currentQueueSourceName = context.getString(R.string.external_queue_label),
                    showDismissUndoBar = false,
                    dismissedTrack = null,
                    dismissedQueue = persistentListOf(),
                    dismissedQueueName = "",
                    dismissedPosition = 0L
                )
            }

            playbackStateHolder.updateStablePlayerState { state ->
                state.copy(
                    currentTrack = externalResult.Track,
                    isPlaying = true,
                    totalDuration = externalResult.Track.duration,
                    Transcript = null,
                    isLoadingTranscript = false
                )
            }

            _sheetState.value = PlayerSheetState.COLLAPSED
            _isSheetVisible.value = true

            internalPlayTracks(queueTracks, externalResult.Track, context.getString(R.string.external_queue_label), null)
            showPlayer()
        }
    }

    fun showPlayer() {
        if (stablePlayerState.value.currentTrack != null) {
            _isSheetVisible.value = true
        }
    }



    private suspend fun internalPlayTracks(TracksToPlay: List<Track>, startTrack: Track, queueName: String = "None", BooklistId: String? = null) {
        // Update dynamic shortcut for last played Booklist
        if (BooklistId != null && queueName != "None") {
            appShortcutManager.updateLastBooklistshortcut(BooklistId, queueName)
        }
        
        val castSession = castStateHolder.castSession.value
        if (castSession != null && castSession.remoteMediaClient != null) {
            castTransferStateHolder.playRemoteQueue(
                TracksToPlay = TracksToPlay,
                startTrack = startTrack,
                isShuffleEnabled = playbackStateHolder.stablePlayerState.value.isShuffleEnabled
            )

            _playerUiState.update { it.copy(currentPlaybackQueue = TracksToPlay.toImmutableList(), currentQueueSourceName = queueName) }
            playbackStateHolder.updateStablePlayerState {
                it.copy(
                    currentTrack = startTrack,
                    isPlaying = true,
                    totalDuration = startTrack.duration.coerceAtLeast(0L)
                )
            }
        } else {
            val playTracksAction = {
                // Use Direct Engine Access to avoid TransactionTooLargeException on Binder
                val enginePlayer = dualPlayerEngine.masterPlayer
                
                val mediaItems = TracksToPlay.map { Track ->
                    val metadataBuilder = MediaMetadata.Builder()
                        .setTitle(Track.title)
                        .setAuthor(Track.displayAuthor)
                    BooklistId?.let {
                        val extras = Bundle()
                        extras.putString("BooklistId", it)
                        metadataBuilder.setExtras(extras)
                    }
                    Track.BookArtUriString?.toUri()?.let { uri ->
                        metadataBuilder.setArtworkUri(uri)
                    }
                    val metadata = metadataBuilder.build()
                    MediaItem.Builder()
                        .setMediaId(Track.id)
                        .setUri(Track.contentUriString.toUri())
                        .setMediaMetadata(metadata)
                        .build()
                }
                val startIndex = TracksToPlay.indexOf(startTrack).coerceAtLeast(0)

                if (mediaItems.isNotEmpty()) {
                    // Direct access: No IPC limit involved
                    enginePlayer.setMediaItems(mediaItems, startIndex, 0L)
                    enginePlayer.prepare()
                    enginePlayer.play()
                    
                    _playerUiState.update { it.copy(currentPlaybackQueue = TracksToPlay.toImmutableList(), currentQueueSourceName = queueName) }
                    playbackStateHolder.updateStablePlayerState {
                        it.copy(
                            currentTrack = startTrack,
                            isPlaying = true,
                            totalDuration = startTrack.duration.coerceAtLeast(0L)
                        )
                    }
                }
                _playerUiState.update { it.copy(isLoadingInitialTracks = false) }
            }

            // We still check for mediaController to ensure the Service is bound and active
            // even though we aren't using it for the heavy lifting anymore.
            if (mediaController == null) {
                Timber.w("MediaController not available. Queuing playback action.")
                pendingPlaybackAction = playTracksAction
            } else {
                playTracksAction()
            }
        }
    }


    private fun loadAndPlayTrack(Track: Track) {
        val controller = mediaController
        if (controller == null) {
            pendingPlaybackAction = {
                loadAndPlayTrack(Track)
            }
            return
        }

        val mediaItem = MediaItem.Builder()
            .setMediaId(Track.id)
            .setUri(Track.contentUriString.toUri())
            .setMediaMetadata(MediaItemBuilder.build(Track).mediaMetadata)
            .build()
        if (controller.currentMediaItem?.mediaId == Track.id) {
            if (!controller.isPlaying) controller.play()
        } else {
            controller.setMediaItem(mediaItem)
            controller.prepare()
            controller.play()
        }
        playbackStateHolder.updateStablePlayerState { it.copy(currentTrack = Track, isPlaying = true) }
        viewModelScope.launch {
            Track.BookArtUriString?.toUri()?.let { uri ->
                val currentUri = playbackStateHolder.stablePlayerState.value.currentTrack?.BookArtUriString
                themeStateHolder.extractAndGenerateColorScheme(uri, currentUri, isPreload = false)
            }
        }
    }

// buildMediaMetadataForTrack moved to MediaItemBuilder

    private fun syncShuffleStateWithSession(enabled: Boolean) {
        val controller = mediaController ?: return
        val args = Bundle().apply {
            putBoolean(AudiobookNotificationProvider.EXTRA_SHUFFLE_ENABLED, enabled)
        }
        controller.sendCustomCommand(
            SessionCommand(AudiobookNotificationProvider.CUSTOM_COMMAND_SET_SHUFFLE_STATE, Bundle.EMPTY),
            args
        )
    }

    fun toggleShuffle(currentTrackOverride: Track? = null) {
        val currentQueue = _playerUiState.value.currentPlaybackQueue.toList()
        val currentTrack = currentTrackOverride
            ?: playbackStateHolder.stablePlayerState.value.currentTrack
            ?: mediaController?.currentMediaItem?.let { resolveTrackFromMediaItem(it) }
            ?: currentQueue.firstOrNull()
        
        playbackStateHolder.toggleShuffle(
            currentTracks = currentQueue,
            currentTrack = currentTrack,
            currentQueueSourceName = _playerUiState.value.currentQueueSourceName,
            updateQueueCallback = { newQueue ->
                _playerUiState.update { it.copy(currentPlaybackQueue = newQueue.toImmutableList()) }
            }
        )
    }

    fun cycleRepeatMode() {
        playbackStateHolder.cycleRepeatMode()
    }

    fun toggleFavorite() {
        playbackStateHolder.stablePlayerState.value.currentTrack?.id?.let { TrackId ->
            viewModelScope.launch {
                userPreferencesRepository.toggleFavoriteTrack(TrackId)
            }
        }
    }

    fun toggleFavoriteSpecificTrack(Track: Track, removing: Boolean = false) {
        viewModelScope.launch {
            userPreferencesRepository.toggleFavoriteTrack(Track.id, removing)
        }
    }

    fun addTrackToQueue(Track: Track) {
        mediaController?.let { controller ->
            val mediaItem = MediaItem.Builder()
                .setMediaId(Track.id)
                .setUri(Track.contentUriString.toUri())
                .setMediaMetadata(MediaMetadata.Builder()
                    .setTitle(Track.title)
                    .setAuthor(Track.displayAuthor)
                    .setArtworkUri(Track.BookArtUriString?.toUri())
                    .build())
                .build()
            controller.addMediaItem(mediaItem)
            // Queue UI is synced via onTimelineChanged listener
        }
    }

    fun addTrackNextToQueue(Track: Track) {
        mediaController?.let { controller ->
            val mediaItem = MediaItem.Builder()
                .setMediaId(Track.id)
                .setUri(Track.contentUriString.toUri())
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(Track.title)
                        .setAuthor(Track.displayAuthor)
                        .setArtworkUri(Track.BookArtUriString?.toUri())
                        .build()
                )
                .build()

            val insertionIndex = if (controller.currentMediaItemIndex != C.INDEX_UNSET) {
                (controller.currentMediaItemIndex + 1).coerceAtMost(controller.mediaItemCount)
            } else {
                controller.mediaItemCount
            }

            controller.addMediaItem(insertionIndex, mediaItem)
            // Queue UI is synced via onTimelineChanged listener
        }
    }
    private suspend fun showMaterialDeleteConfirmation(activity: Activity, Track: Track): Boolean {
        return withContext(Dispatchers.Main) {
            try {
                if (activity.isFinishing || activity.isDestroyed) {
                    return@withContext false
                }

                val userChoice = CompletableDeferred<Boolean>()

                val dialog = MaterialAlertDialogBuilder(activity)
                    .setTitle("Delete Track?")
                    .setMessage("""
                    "${Track.title}" by ${Track.displayAuthor}

                    This Track will be permanently deleted from your device and cannot be recovered.
                """.trimIndent())
                    .setPositiveButton("Delete") { _, _ ->
                        userChoice.complete(true)
                    }
                    .setNegativeButton("Cancel") { _, _ ->
                        userChoice.complete(false)
                    }
                    .setOnCancelListener {
                        userChoice.complete(false)
                    }
                    .setCancelable(true)
                    .create()

                dialog.show()

                // Wait for user response - this will suspend until complete is called
                userChoice.await()
            } catch (e: Exception) {
                false
            }
        }
    }

    fun deleteFromDevice(activity: Activity, Track: Track, onResult: (Boolean) -> Unit = {}){
        viewModelScope.launch {
            val userConfirmed = showMaterialDeleteConfirmation(activity, Track)
            if (!userConfirmed) {
                onResult(false)
                return@launch
            }
            // Check if we're currently playing the Track being deleted
            if (playbackStateHolder.stablePlayerState.value.currentTrack?.id == Track.id) {
                listeningStatsTracker.finalizeCurrentSession()
                mediaController?.pause()
                mediaController?.stop()
                mediaController?.clearMediaItems()
                playbackStateHolder.updateStablePlayerState {
                    it.copy(
                        currentTrack = null,
                        isPlaying = false,
                        totalDuration = 0L
                    )
                }
            }

            val success = metadataEditStateHolder.deleteTrack(Track)
            if (success) {
                _toastEvents.emit("File deleted")
                removeFromMediaControllerQueue(Track.id)
                removeTrack(Track)
                onResult(true)
            } else {
                _toastEvents.emit("Can't delete the file or file not found")
                onResult(false)
            }
        }
    }

    suspend fun removeTrack(Track: Track) {
        toggleFavoriteSpecificTrack(Track, true)
        _playerUiState.update { currentState ->
            currentState.copy(
                currentPosition = 0L,
                currentPlaybackQueue = currentState.currentPlaybackQueue.filter { it.id != Track.id }.toImmutableList(),
                currentQueueSourceName = ""
            )
        }
        _masterAllTracks.value = _masterAllTracks.value.filter { it.id != Track.id }.toImmutableList()
        _isSheetVisible.value = false
        AudiobookRepository.deleteById(Track.id.toLong())
        userPreferencesRepository.removeTrackFromAllBooklists(Track.id)
    }

    private fun removeFromMediaControllerQueue(TrackId: String) {
        val controller = mediaController ?: return

        try {
            // Get the current timeline and media item count
            val timeline = controller.currentTimeline
            val mediaItemCount = timeline.windowCount

            // Find the media item to remove by iterating through windows
            for (i in 0 until mediaItemCount) {
                val window = timeline.getWindow(i, Timeline.Window())
                if (window.mediaItem.mediaId == TrackId) {
                    // Remove the media item by index
                    controller.removeMediaItem(i)
                    break
                }
            }
        } catch (e: Exception) {
            Log.e("MediaController", "Error removing from queue: ${e.message}")
        }
    }

    fun playPause() {
        val castSession = castStateHolder.castSession.value
        if (castSession != null && castSession.remoteMediaClient != null) {
            val remoteMediaClient = castSession.remoteMediaClient!!
            if (remoteMediaClient.isPlaying) {
                castStateHolder.castPlayer?.pause()
            } else {
                // If there are items in the remote queue, just play.
                // Otherwise, load the current local queue to the remote player.
                if (remoteMediaClient.mediaQueue != null && remoteMediaClient.mediaQueue.itemCount > 0) {
                    castStateHolder.castPlayer?.play()
                } else {
                    val queue = _playerUiState.value.currentPlaybackQueue
                    if (queue.isNotEmpty()) {
                        val startTrack = playbackStateHolder.stablePlayerState.value.currentTrack ?: queue.first()
                        viewModelScope.launch {
                            internalPlayTracks(queue.toList(), startTrack, _playerUiState.value.currentQueueSourceName)
                        }
                    }
                }
            }
        } else {
            mediaController?.let { controller ->
                if (controller.isPlaying) {
                    controller.pause()
                } else {
                    if (controller.currentMediaItem == null) {
                        val currentQueue = _playerUiState.value.currentPlaybackQueue
                        val currentTrack = playbackStateHolder.stablePlayerState.value.currentTrack
                        when {
                            currentQueue.isNotEmpty() && currentTrack != null -> {
                                viewModelScope.launch {
                                    transitionSchedulerJob?.cancel()
                                    internalPlayTracks(
                                        currentQueue.toList(),
                                        currentTrack,
                                        _playerUiState.value.currentQueueSourceName
                                    )
                                }
                            }
                            currentTrack != null -> {
                                loadAndPlayTrack(currentTrack)
                            }
                            _masterAllTracks.value.isNotEmpty() -> {
                                loadAndPlayTrack(_masterAllTracks.value.first())
                            }
                            else -> {
                                controller.play()
                            }
                        }
                    } else {
                        controller.play()
                    }
                }
            }
        }
    }

    fun seekTo(position: Long) {
        playbackStateHolder.seekTo(position)
    }

    fun nextTrack() {
        playbackStateHolder.nextTrack()
    }

    fun previousTrack() {
        playbackStateHolder.previousTrack()
    }

    private fun startProgressUpdates() {
        playbackStateHolder.startProgressUpdates()
    }

    private fun stopProgressUpdates() {
        playbackStateHolder.stopProgressUpdates()
    }

    suspend fun getTracks(TrackIds: List<String>) : List<Track>{
        return AudiobookRepository.getTracksByIds(TrackIds).first()
    }

    //Sorting
    fun sortTracks(sortOption: SortOption, persist: Boolean = true) {
        libraryStateHolder.sortTracks(sortOption, persist)
    }

    fun sortBooks(sortOption: SortOption, persist: Boolean = true) {
        libraryStateHolder.sortBooks(sortOption, persist)
    }

    fun sortAuthors(sortOption: SortOption, persist: Boolean = true) {
        libraryStateHolder.sortAuthors(sortOption, persist)
    }

    fun sortFavoriteTracks(sortOption: SortOption, persist: Boolean = true) {
        libraryStateHolder.sortFavoriteTracks(sortOption, persist)
    }

    fun sortFolders(sortOption: SortOption) {
        libraryStateHolder.sortFolders(sortOption)
    }

    fun setFoldersBooklistView(isBooklistView: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setFoldersBooklistView(isBooklistView)
            setFoldersBooklistViewState(isBooklistView)
        }
    }

    fun navigateToFolder(path: String) {
        val storageRootPath = android.os.Environment.getExternalStorageDirectory().path
        if (path == storageRootPath) {
            _playerUiState.update {
                it.copy(
                    currentFolderPath = null,
                    currentFolder = null
                )
            }
            return
        }

        val folder = findFolder(path, _playerUiState.value.AudiobookFolders)
        if (folder != null) {
            _playerUiState.update {
                it.copy(
                    currentFolderPath = path,
                    currentFolder = folder
                )
            }
        }
    }

    fun navigateBackFolder() {
        _playerUiState.update {
            val currentFolder = it.currentFolder
            if (currentFolder != null) {
                val parentPath = File(currentFolder.path).parent
                val parentFolder = findFolder(parentPath, _playerUiState.value.AudiobookFolders)
                it.copy(
                    currentFolderPath = parentPath,
                    currentFolder = parentFolder
                )
            } else {
                it
            }
        }
    }

    private fun findFolder(path: String?, folders: List<AudiobookFolder>): AudiobookFolder? {
        if (path == null) {
            return null
        }
        val queue = ArrayDeque(folders)
        while (queue.isNotEmpty()) {
            val folder = queue.remove()
            if (folder.path == path) {
                return folder
            }
            queue.addAll(folder.subFolders)
        }
        return null
    }

    private fun setFoldersBooklistViewState(isBooklistView: Boolean) {
        _playerUiState.update { currentState ->
            currentState.copy(
                isFoldersBooklistView = isBooklistView,
                currentFolderPath = null,
                currentFolder = null
            )
        }
    }

    fun updateSearchFilter(filterType: SearchFilterType) {
        searchStateHolder.updateSearchFilter(filterType)
    }

    fun loadSearchHistory(limit: Int = 15) {
        searchStateHolder.loadSearchHistory(limit)
    }

    fun onSearchQuerySubmitted(query: String) {
        searchStateHolder.onSearchQuerySubmitted(query)
    }

    fun performSearch(query: String) {
        searchStateHolder.performSearch(query)
    }

    fun deleteSearchHistoryItem(query: String) {
        searchStateHolder.deleteSearchHistoryItem(query)
    }

    fun clearSearchHistory() {
        searchStateHolder.clearSearchHistory()
    }

    // --- AI Booklist Generation ---

    // --- AI Booklist Generation ---

    fun showAiBooklistsheet() {
        aiStateHolder.showAiBooklistsheet()
    }

    fun dismissAiBooklistsheet() {
        aiStateHolder.dismissAiBooklistsheet()
    }

    fun generateAiBooklist(prompt: String, minLength: Int, maxLength: Int, saveAsBooklist: Boolean = false) {
        aiStateHolder.generateAiBooklist(prompt, minLength, maxLength, saveAsBooklist)
    }

    fun regenerateDailyMixWithPrompt(prompt: String) {
        aiStateHolder.regenerateDailyMixWithPrompt(prompt)
    }

    fun clearQueueExceptCurrent() {
        mediaController?.let { controller ->
            val currentTrackIndex = controller.currentMediaItemIndex
            if (currentTrackIndex == C.INDEX_UNSET) return@let
            val indicesToRemove = (0 until controller.mediaItemCount)
                .filter { it != currentTrackIndex }
                .sortedDescending()

            for (index in indicesToRemove) {
                controller.removeMediaItem(index)
            }
        }
    }

    fun selectRoute(route: MediaRouter.RouteInfo) {
        val selectedRouteId = castStateHolder.selectedRoute.value?.id
        val isCastRoute = route.isCastRoute() && !route.isDefault
        // Use castStateHolder.isRemotePlaybackActive directly
        val isSwitchingBetweenRemotes = isCastRoute &&
            (castStateHolder.isRemotePlaybackActive.value || castStateHolder.isCastConnecting.value) &&
            selectedRouteId != null &&
            selectedRouteId != route.id

        if (isSwitchingBetweenRemotes) {
            castStateHolder.setPendingCastRouteId(route.id)
            castStateHolder.setCastConnecting(true)
            sessionManager.currentCastSession?.let { sessionManager.endCurrentSession(true) }
        } else {
            castStateHolder.setPendingCastRouteId(null)
        }
        
        castStateHolder.selectRoute(route)
    }

    fun disconnect(resetConnecting: Boolean = true) {
        val start = SystemClock.elapsedRealtime()
        castStateHolder.setPendingCastRouteId(null)
        val wasRemote = castStateHolder.isRemotePlaybackActive.value
        if (wasRemote) {
            Timber.tag(CAST_LOG_TAG).i(
                "Manual disconnect requested; marking castConnecting=true until session ends. mainThread=%s",
                Looper.myLooper() == Looper.getMainLooper()
            )
            castStateHolder.setCastConnecting(true)
        }
        castStateHolder.disconnect()
        castStateHolder.setRemotePlaybackActive(false)
        if (resetConnecting && !wasRemote) {
            castStateHolder.setCastConnecting(false)
        }
        Timber.tag(CAST_LOG_TAG).i(
            "Disconnect call finished in %dms (wasRemote=%s resetConnecting=%s)",
            SystemClock.elapsedRealtime() - start,
            wasRemote,
            resetConnecting
        )
    }

    fun setRouteVolume(volume: Int) {
        castStateHolder.setRouteVolume(volume)
    }

    fun refreshCastRoutes() {
        castStateHolder.refreshRoutes(viewModelScope)
    }



    override fun onCleared() {
        super.onCleared()
        stopProgressUpdates()
        listeningStatsTracker.onCleared()
        listeningStatsTracker.onCleared()
        castStateHolder.onCleared()
        searchStateHolder.onCleared()
        aiStateHolder.onCleared()
        libraryStateHolder.onCleared()
        sleepTimerStateHolder.onCleared()
        connectivityStateHolder.onCleared()

    }

    // Sleep Timer Control Functions - delegated to SleepTimerStateHolder
    fun setSleepTimer(durationMinutes: Int) {
        sleepTimerStateHolder.setSleepTimer(durationMinutes)
    }

    fun playCounted(count: Int) {
        sleepTimerStateHolder.playCounted(count)
    }

    fun cancelCountedPlay() {
        sleepTimerStateHolder.cancelCountedPlay()
    }

    fun setEndOfTrackTimer(enable: Boolean) {
        val currentTrackId = stablePlayerState.value.currentTrack?.id
        sleepTimerStateHolder.setEndOfTrackTimer(enable, currentTrackId)
    }

    fun cancelSleepTimer(overrideToastMessage: String? = null, suppressDefaultToast: Boolean = false) {
        sleepTimerStateHolder.cancelSleepTimer(overrideToastMessage, suppressDefaultToast)
    }

    fun dismissBooklistAndShowUndo() {
        viewModelScope.launch {
            val TrackToDismiss = playbackStateHolder.stablePlayerState.value.currentTrack
            val queueToDismiss = _playerUiState.value.currentPlaybackQueue
            val queueNameToDismiss = _playerUiState.value.currentQueueSourceName
            val positionToDismiss = _playerUiState.value.currentPosition

            if (TrackToDismiss == null && queueToDismiss.isEmpty()) {
                // Nothing to dismiss
                return@launch
            }

            Log.d("PlayerViewModel", "Dismissing Booklist. Track: ${TrackToDismiss?.title}, Queue size: ${queueToDismiss.size}")

            // Store state for potential undo
            _playerUiState.update {
                it.copy(
                    dismissedTrack = TrackToDismiss,
                    dismissedQueue = queueToDismiss,
                    dismissedQueueName = queueNameToDismiss,
                    dismissedPosition = positionToDismiss,
                    showDismissUndoBar = true
                )
            }

            // Stop playback and clear current player state
            mediaController?.stop() // This should also clear Media3's Booklist
            mediaController?.clearMediaItems() // Ensure items are cleared

            playbackStateHolder.updateStablePlayerState {
                it.copy(
                    currentTrack = null,
                    isPlaying = false,
                    totalDuration = 0L,
                    //isCurrentTrackFavorite = false
                )
            }
            _playerUiState.update {
                it.copy(
                    currentPosition = 0L,
                    currentPlaybackQueue = persistentListOf(),
                    currentQueueSourceName = ""
                )
            }
            _isSheetVisible.value = false // Hide the player sheet

            // Launch timer to hide the undo bar
            launch {
                delay(_playerUiState.value.undoBarVisibleDuration)
                // Only hide if it's still showing (i.e., undo wasn't pressed)
                if (_playerUiState.value.showDismissUndoBar) {
                    _playerUiState.update { it.copy(showDismissUndoBar = false, dismissedTrack = null, dismissedQueue = persistentListOf()) }
                }
            }
        }
    }

    fun hideDismissUndoBar() {
        _playerUiState.update {
            it.copy(
                showDismissUndoBar = false,
                dismissedTrack = null,
                dismissedQueue = persistentListOf(),
                dismissedQueueName = "",
                dismissedPosition = 0L
            )
        }
    }

    /**
     * Monitors Track changes and automatically hides the dismiss undo bar
     * when the user plays a different Track, as the undo option becomes irrelevant.
     */
    private fun setupUndoBarPlaybackObserver() {
        viewModelScope.launch {
            stablePlayerState
                .map { it.currentTrack?.id }
                .distinctUntilChanged()
                .collect { newTrackId ->
                    val uiState = _playerUiState.value
                    // If undo bar is showing and a different Track is now playing,
                    // hide the undo bar as it's no longer relevant
                    if (uiState.showDismissUndoBar &&
                        newTrackId != null &&
                        newTrackId != uiState.dismissedTrack?.id
                    ) {
                        hideDismissUndoBar()
                    }
                }
        }
    }

    fun undoDismissBooklist() {
        viewModelScope.launch {
            val TrackToRestore = _playerUiState.value.dismissedTrack
            val queueToRestore = _playerUiState.value.dismissedQueue
            val queueNameToRestore = _playerUiState.value.dismissedQueueName
            val positionToRestore = _playerUiState.value.dismissedPosition

            if (TrackToRestore != null && queueToRestore.isNotEmpty()) {
                // Restore the Booklist and Track
                playTracks(queueToRestore.toList(), TrackToRestore, queueNameToRestore) // playTracks handles setting media items and playing

                delay(500) // Small delay to allow player to prepare
                mediaController?.seekTo(positionToRestore)


                _playerUiState.update {
                    it.copy(
                        showDismissUndoBar = false, // Hide undo bar
                        dismissedTrack = null,
                        dismissedQueue = persistentListOf(),
                        dismissedQueueName = "",
                        dismissedPosition = 0L
                    )
                }
                _isSheetVisible.value = true // Ensure player sheet is visible again
                _sheetState.value = PlayerSheetState.COLLAPSED // Start collapsed

                Log.d("PlayerViewModel", "Booklist restored. Track: ${TrackToRestore.title}")
                _toastEvents.emit("Booklist restored")
            } else {
                // Nothing to restore, hide bar anyway
                _playerUiState.update { it.copy(showDismissUndoBar = false) }
            }
        }
    }

    fun getTrackUrisForCategory(CategoryId: String): Flow<List<String>> {
        return AudiobookRepository.getAudiobookByCategory(CategoryId).map { Tracks ->
            Tracks.take(4).mapNotNull { it.BookArtUriString }
        }
    }

    fun saveLastLibraryTabIndex(tabIndex: Int) {
        viewModelScope.launch {
            userPreferencesRepository.saveLastLibraryTabIndex(tabIndex)
        }
    }

    fun showSortingSheet() {
        _isSortingSheetVisible.value = true
    }

    fun hideSortingSheet() {
        _isSortingSheetVisible.value = false
    }

    fun onLibraryTabSelected(tabIndex: Int) {
        Trace.beginSection("PlayerViewModel.onLibraryTabSelected")
        saveLastLibraryTabIndex(tabIndex)

        val tabIdentifier = libraryTabsFlow.value.getOrNull(tabIndex) ?: return
        val tabId = tabIdentifier.toLibraryTabIdOrNull() ?: LibraryTabId.Tracks
        _currentLibraryTabId.value = tabId

        if (_loadedTabs.value.contains(tabIdentifier)) {
            Log.d("PlayerViewModel", "Tab '$tabIdentifier' already loaded. Skipping data load.")
            Trace.endSection()
            return
        }

        Log.d("PlayerViewModel", "Tab '$tabIdentifier' selected. Attempting to load data.")
        viewModelScope.launch {
            Trace.beginSection("PlayerViewModel.onLibraryTabSelected_coroutine_load")
            try {
                when (tabId) {
                    LibraryTabId.Tracks -> loadTracksIfNeeded()
                    LibraryTabId.Books -> loadBooksIfNeeded()
                    LibraryTabId.Authors -> loadAuthorsIfNeeded()
                    LibraryTabId.FOLDERS -> loadFoldersFromRepository()
                    else -> Unit
                }
                _loadedTabs.update { currentTabs -> currentTabs + tabIdentifier }
                Log.d("PlayerViewModel", "Tab '$tabIdentifier' marked as loaded. Current loaded tabs: ${_loadedTabs.value}")
            } finally {
                Trace.endSection()
            }
        }
        Trace.endSection()
    }

    fun saveLibraryTabsOrder(tabs: List<String>) {
        viewModelScope.launch {
            val orderJson = Json.encodeToString(tabs)
            userPreferencesRepository.saveLibraryTabsOrder(orderJson)
        }
    }

    fun resetLibraryTabsOrder() {
        viewModelScope.launch {
            userPreferencesRepository.resetLibraryTabsOrder()
        }
    }

    fun selectTrackForInfo(Track: Track) {
        _selectedTrackForInfo.value = Track
    }

    private fun loadTranscriptForCurrentTrack() {
        val currentTrack = playbackStateHolder.stablePlayerState.value.currentTrack ?: return
        // Delegate to TranscriptStateHolder
        TranscriptStateHolder.loadTranscriptForTrack(currentTrack, TranscriptSourcePreference.value)
    }

    fun editTrackMetadata(
        Track: Track,
        newTitle: String,
        newAuthor: String,
        newBook: String,
        newCategory: String,
        newTranscript: String,
        newTrackNumber: Int,
        coverArtUpdate: CoverArtUpdate?,
    ) {
        viewModelScope.launch {
            Log.e("PlayerViewModel", "METADATA_EDIT_VM: Starting editTrackMetadata via Holder")
            
            val previousBookArt = Track.BookArtUriString
            
            val result = metadataEditStateHolder.saveMetadata(
                Track = Track,
                newTitle = newTitle,
                newAuthor = newAuthor,
                newBook = newBook,
                newCategory = newCategory,
                newTranscript = newTranscript,
                newTrackNumber = newTrackNumber,
                coverArtUpdate = coverArtUpdate
            )

            Log.e("PlayerViewModel", "METADATA_EDIT_VM: Result success=${result.success}")

            if (result.success && result.updatedTrack != null) {
                val updatedTrack = result.updatedTrack
                val refreshedBookArtUri = result.updatedBookArtUri

                invalidateCoverArtCaches(previousBookArt, refreshedBookArtUri)

                _playerUiState.update { state ->
                    val queueIndex = state.currentPlaybackQueue.indexOfFirst { it.id == Track.id }
                    if (queueIndex == -1) {
                        state
                    } else {
                        val newQueue = state.currentPlaybackQueue.toMutableList()
                        newQueue[queueIndex] = updatedTrack
                        state.copy(currentPlaybackQueue = newQueue.toImmutableList())
                    }
                }

                // Update the Track in the master Tracks flow
                _masterAllTracks.update { Tracks ->
                    Tracks.map { existing ->
                        if (existing.id == Track.id) updatedTrack else existing
                    }.toImmutableList()
                }
                
                // Update the LibraryStateHolder which drives the UI
                libraryStateHolder.updateTrack(updatedTrack)

                if (playbackStateHolder.stablePlayerState.value.currentTrack?.id == Track.id) {
                    playbackStateHolder.updateStablePlayerState {
                        it.copy(
                            currentTrack = updatedTrack,
                            Transcript = result.parsedTranscript
                        )
                    }
                    
                    // Update the player's current MediaItem to refresh notification artwork
                    // This is efficient: only replaces metadata, not the media stream
                    val controller = playbackStateHolder.mediaController
                    if (controller != null) {
                        val currentIndex = controller.currentMediaItemIndex
                        if (currentIndex >= 0 && currentIndex < controller.mediaItemCount) {
                            val currentPosition = controller.currentPosition
                            val newMediaItem = MediaItemBuilder.build(updatedTrack)
                            controller.replaceMediaItem(currentIndex, newMediaItem)
                            // Restore position since replaceMediaItem may reset it
                            controller.seekTo(currentIndex, currentPosition)
                        }
                    }
                }

                if (_selectedTrackForInfo.value?.id == Track.id) {
                    _selectedTrackForInfo.value = updatedTrack
                }

                if (coverArtUpdate != null) {
                    purgeBookArtThemes(previousBookArt, updatedTrack.BookArtUriString)
                    val paletteTargetUri = updatedTrack.BookArtUriString
                    if (paletteTargetUri != null) {
                        themeStateHolder.getBookColorSchemeFlow(paletteTargetUri)
                        val currentUri = playbackStateHolder.stablePlayerState.value.currentTrack?.BookArtUriString
                        themeStateHolder.extractAndGenerateColorScheme(paletteTargetUri.toUri(), currentUri, isPreload = false)
                    } else {
                        val currentUri = playbackStateHolder.stablePlayerState.value.currentTrack?.BookArtUriString
                        themeStateHolder.extractAndGenerateColorScheme(null, currentUri, isPreload = false)
                    }
                }

                // No need for full library sync - file, MediaStore, and local DB are already updated
                // syncManager.sync() was removed to avoid unnecessary wait time
                _toastEvents.emit("Metadata updated successfully")
            } else {
                val errorMessage = result.getUserFriendlyErrorMessage()
                Log.e("PlayerViewModel", "METADATA_EDIT_VM: Failed - ${result.error}: $errorMessage")
                _toastEvents.emit(errorMessage)
            }
        }
    }

    private fun invalidateCoverArtCaches(vararg uriStrings: String?) {
        imageCacheManager.invalidateCoverArtCaches(*uriStrings)
    }

    private suspend fun purgeBookArtThemes(vararg uriStrings: String?) {
        val uris = uriStrings.mapNotNull { it?.takeIf(String::isNotBlank) }.distinct()
        if (uris.isEmpty()) return

        withContext(Dispatchers.IO) {
            BookArtThemeDao.deleteThemesByUris(uris)
        }

        uris.forEach { uri ->
            // Cache invalidation delegated to ThemeStateHolder (if implemented) or relied on re-generation
            // individualBookColorSchemes was removed.
        }
    }

    suspend fun generateAiMetadata(Track: Track, fields: List<String>): Result<TrackMetadata> {
        return aiStateHolder.generateAiMetadata(Track, fields)
    }

    private fun updateTrackInStates(updatedTrack: Track, newTranscript: Transcript? = null) {
        // Update the queue first
        val currentQueue = _playerUiState.value.currentPlaybackQueue
        val TrackIndex = currentQueue.indexOfFirst { it.id == updatedTrack.id }

        if (TrackIndex != -1) {
            val newQueue = currentQueue.toMutableList()
            newQueue[TrackIndex] = updatedTrack
            _playerUiState.update { it.copy(currentPlaybackQueue = newQueue.toImmutableList()) }
        }

        // Then, update the stable state
        playbackStateHolder.updateStablePlayerState { state ->
            // Only update Transcript if they are explicitly passed
            val finalTranscript = newTranscript ?: state.Transcript
            state.copy(
                currentTrack = updatedTrack,
                Transcript = if (state.currentTrack?.id == updatedTrack.id) finalTranscript else state.Transcript
            )
        }
    }

    /**
     * Busca la letra de la canciÃƒÂ³n actual en el servicio remoto.
     */
    /**
     * Busca la letra de la canciÃƒÂ³n actual en el servicio remoto.
     */
    fun fetchTranscriptForCurrentTrack(forcePickResults: Boolean = false) {
        val currentTrack = stablePlayerState.value.currentTrack ?: return
        TranscriptStateHolder.fetchTranscriptForTrack(currentTrack, forcePickResults) { resId ->
            context.getString(resId)
        }
    }

    /**
     * Manual search Transcript using query provided by user (title and Author)
     */
    fun searchTranscriptManually(title: String, Author: String? = null) {
        TranscriptStateHolder.searchTranscriptManually(title, Author)
    }

    fun acceptTranscriptSearchResultForCurrentTrack(result: TranscriptSearchResult) {
         val currentTrack = stablePlayerState.value.currentTrack ?: return
         TranscriptStateHolder.acceptTranscriptSearchResult(result, currentTrack)
    }

    fun resetTranscriptForCurrentTrack() {
        val TrackId = stablePlayerState.value.currentTrack?.id?.toLongOrNull() ?: return
        TranscriptStateHolder.resetTranscript(TrackId)
        playbackStateHolder.updateStablePlayerState { state -> state.copy(Transcript = null) }
    }

    fun resetAllTranscript() {
        TranscriptStateHolder.resetAllTranscript()
        playbackStateHolder.updateStablePlayerState { state -> state.copy(Transcript = null) }
    }

    /**
     * Procesa la letra importada de un archivo, la guarda y actualiza la UI.
     * @param TrackId El ID de la canciÃƒÂ³n para la que se importa la letra.
     * @param TranscriptContent El contenido de la letra como String.
     */
    fun importTranscriptFromFile(TrackId: Long, TranscriptContent: String) {
        val currentTrack = stablePlayerState.value.currentTrack
        TranscriptStateHolder.importTranscriptFromFile(TrackId, TranscriptContent, currentTrack)
        
        // Optimistic local update since holder event handles persistence
        if (currentTrack?.id?.toLong() == TrackId) {
             val parsed = com.oakiha.audia.utils.TranscriptUtils.parseTranscript(TranscriptContent)
             val updatedTrack = currentTrack.copy(Transcript = TranscriptContent)
             updateTrackInStates(updatedTrack, parsed)
        }
    }

    /**
     * Resetea el estado de la bÃƒÂºsqueda de letras a Idle.
     */
    fun resetTranscriptSearchState() {
        TranscriptStateHolder.resetSearchState()
    }

    private fun onBlockedDirectoriesChanged() {
        viewModelScope.launch {
            AudiobookRepository.invalidateCachesDependentOnAllowedDirectories()
            resetAndLoadInitialData("Blocked directories changed")
        }
    }
}
