package com.oakiha.audia.presentation.viewmodel

import android.os.Trace
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import androidx.compose.ui.graphics.toArgb
import android.util.Log
import com.oakiha.audia.data.model.Book
import com.oakiha.audia.data.model.Author
import com.oakiha.audia.data.model.LibraryTabId
import com.oakiha.audia.data.model.AudiobookFolder
import com.oakiha.audia.data.model.Track
import com.oakiha.audia.data.model.SortOption
import com.oakiha.audia.data.preferences.UserPreferencesRepository
import com.oakiha.audia.data.repository.AudiobookRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the data state of the audiobook library: Tracks, Albums, Artists, Folders.
 * Handles loading from Repository and applying SortOptions.
 */
@Singleton
class LibraryStateHolder @Inject constructor(
    private val audiobookRepository: AudiobookRepository,
    private val userPreferencesRepository: UserPreferencesRepository
) {

    // --- UI State ---
    private val _allTracks = MutableStateFlow<ImmutableList<Track>>(persistentListOf())
    val allTracks = _allTracks.asStateFlow()

    private val _albums = MutableStateFlow<ImmutableList<Book>>(persistentListOf())
    val books = _albums.asStateFlow()

    private val _artists = MutableStateFlow<ImmutableList<Author>>(persistentListOf())
    val authors = _artists.asStateFlow()

    private val _audiobookFolders = MutableStateFlow<ImmutableList<AudiobookFolder>>(persistentListOf())
    val audiobookFolders = _audiobookFolders.asStateFlow()

    private val _isLoadingLibrary = MutableStateFlow(false)
    val isLoadingLibrary = _isLoadingLibrary.asStateFlow()

    private val _isLoadingCategories = MutableStateFlow(false)
    val isLoadingCategories = _isLoadingCategories.asStateFlow()

    // Sort Options
    private val _currentTrackSortOption = MutableStateFlow<SortOption>(SortOption.TrackDefaultOrder)
    val currentTrackSortOption = _currentTrackSortOption.asStateFlow()

    private val _currentAlbumSortOption = MutableStateFlow<SortOption>(SortOption.BookTitleAZ)
    val currentAlbumSortOption = _currentAlbumSortOption.asStateFlow()

    private val _currentArtistSortOption = MutableStateFlow<SortOption>(SortOption.AuthorNameAZ)
    val currentArtistSortOption = _currentArtistSortOption.asStateFlow()

    private val _currentFolderSortOption = MutableStateFlow<SortOption>(SortOption.FolderNameAZ)
    val currentFolderSortOption = _currentFolderSortOption.asStateFlow()

    private val _currentFavoriteSortOption = MutableStateFlow<SortOption>(SortOption.LikedTrackTitleAZ)
    val currentFavoriteSortOption = _currentFavoriteSortOption.asStateFlow()



    @OptIn(ExperimentalStdlibApi::class)
    val genres: kotlinx.coroutines.flow.Flow<ImmutableList<com.oakiha.audia.data.model.Genre>> = _allTracks
        .map { tracks ->
            val genreMap = mutableMapOf<String, MutableList<Track>>()
            val unknownGenreName = "Unknown Genre"

            tracks.forEach { track ->
                val genreName = track.genre?.trim()
                if (genreName.isNullOrBlank()) {
                    genreMap.getOrPut(unknownGenreName) { mutableListOf() }.add(track)
                } else {
                    genreMap.getOrPut(genreName) { mutableListOf() }.add(track)
                }
            }

            genreMap.toList().mapIndexedNotNull { index, (genreName, tracks) ->
                if (tracks.isNotEmpty()) {
                    val id = if (genreName.equals(unknownGenreName, ignoreCase = true)) {
                        "unknown"
                    } else {
                        genreName.lowercase().replace(" ", "_").replace("/", "_")
                    }
                    val color = com.oakiha.audia.ui.theme.GenreColors.colors[index % com.oakiha.audia.ui.theme.GenreColors.colors.size]
                    com.oakiha.audia.data.model.Genre(
                        id = id,
                        name = genreName,
                        lightColorHex = color.lightColor.toHexString(),
                        onLightColorHex = color.onLightColor.toHexString(),
                        darkColorHex = color.darkColor.toHexString(),
                        onDarkColorHex = color.onDarkColor.toHexString()
                    )
                } else {
                    null
                }
            }
                .distinctBy { it.id }
                .sortedBy { it.name.lowercase() }
                .toImmutableList()
        }
        .flowOn(Dispatchers.Default)

    
    // Internal state
    private var scope: CoroutineScope? = null

    // --- Initialization ---

    fun initialize(scope: CoroutineScope) {
        this.scope = scope
        // Initial load of sort preferences
        scope.launch {
            val songSortKey = userPreferencesRepository.tracksSortOptionFlow.first()
            _currentTrackSortOption.value = SortOption.TRACKS.find { it.storageKey == songSortKey } ?: SortOption.TrackDefaultOrder

            val albumSortKey = userPreferencesRepository.booksSortOptionFlow.first()
            _currentAlbumSortOption.value = SortOption.BOOKS.find { it.storageKey == albumSortKey } ?: SortOption.BookTitleAZ
            
            val artistSortKey = userPreferencesRepository.authorsSortOptionFlow.first()
            _currentArtistSortOption.value = SortOption.AUTHORS.find { it.storageKey == artistSortKey } ?: SortOption.AuthorNameAZ
            
            
            val likedSortKey = userPreferencesRepository.likedTracksSortOptionFlow.first()
            _currentFavoriteSortOption.value = SortOption.LIKED.find { it.storageKey == likedSortKey } ?: SortOption.LikedTrackDateLiked
        }
    }

    fun onCleared() {
        scope = null
    }

    // --- Data Loading ---
    
    // We observe the repository flows permanently in initialize(), or we start collecting here?
    // Better to start collecting in initialize() or have these functions just be "ensure active".
    // Actually, explicit "load" functions are legacy imperative style.
    // We should launch collectors in initialize() that update the state.
    
    private var songsJob: Job? = null
    private var albumsJob: Job? = null
    private var artistsJob: Job? = null
    private var foldersJob: Job? = null
    
    fun startObservingLibraryData() {
        if (songsJob?.isActive == true) return
        
        Log.d("LibraryStateHolder", "startObservingLibraryData called.")
        
        songsJob = scope?.launch {
            _isLoadingLibrary.value = true
            audiobookRepository.getTracks().collect { tracks ->
                 // When the repository emits a new list (triggered by directory changes),
                 // we update our state and re-apply current sorting.
                 _allTracks.value = tracks.toImmutableList()
                 // Apply sort to the new data
                 sortSongs(_currentTrackSortOption.value, persist = false)
                 _isLoadingLibrary.value = false
            }
        }
        
        albumsJob = scope?.launch {
            _isLoadingCategories.value = true
            audiobookRepository.getBooks().collect { books ->
                _albums.value = books.toImmutableList()
                sortAlbums(_currentAlbumSortOption.value, persist = false)
                _isLoadingCategories.value = false
            }
        }
        
        artistsJob = scope?.launch {
            _isLoadingCategories.value = true
            audiobookRepository.getAuthors().collect { authors ->
                _artists.value = authors.toImmutableList()
                sortArtists(_currentArtistSortOption.value, persist = false)
                _isLoadingCategories.value = false
            }
        }
        
        foldersJob = scope?.launch {
            audiobookRepository.getAudiobookFolders().collect { folders ->
                 _audiobookFolders.value = folders.toImmutableList()
                 sortFolders(_currentFolderSortOption.value)
            }
        }
    }

    // Deprecated imperative loaders - redirected to observer start
    fun loadSongsFromRepository() {
         startObservingLibraryData()
    }

    fun loadAlbumsFromRepository() {
         startObservingLibraryData()
    }

    fun loadArtistsFromRepository() {
         startObservingLibraryData()
    }
    
    fun loadFoldersFromRepository() {
        startObservingLibraryData()
    }
    
    // --- Lazy Loading Checks ---

    // --- Lazy Loading Checks ---
    // We replace conditional "check if empty" with "ensure observing".
    // If we are already observing, startObservingLibraryData returns early.
    // If we are not (e.g. process death recovery?), it restarts.
    
    fun loadSongsIfNeeded() {
         startObservingLibraryData()
    }

    fun loadAlbumsIfNeeded() {
        startObservingLibraryData()
    }

    fun loadArtistsIfNeeded() {
        startObservingLibraryData()
    }

    // --- Sorting ---

    fun sortSongs(sortOption: SortOption, persist: Boolean = true) {
        scope?.launch {
            if (persist) {
                userPreferencesRepository.setTracksSortOption(sortOption.storageKey)
            }
            _currentTrackSortOption.value = sortOption

            val sorted = when (sortOption) {
                SortOption.TrackTitleAZ -> _allTracks.value.sortedBy { it.title.lowercase() }
                SortOption.TrackTitleZA -> _allTracks.value.sortedByDescending { it.title.lowercase() }
                SortOption.TrackAuthor -> _allTracks.value.sortedBy { it.author.lowercase() }
                SortOption.TrackBook -> _allTracks.value.sortedBy { it.book.lowercase() }
                SortOption.TrackDateAdded -> _allTracks.value.sortedByDescending { it.dateAdded }
                SortOption.TrackDuration -> _allTracks.value.sortedBy { it.duration }
                else -> _allTracks.value // Default or unhandled
            }
            _allTracks.value = sorted.toImmutableList()
        }
    }

    fun sortAlbums(sortOption: SortOption, persist: Boolean = true) {
        scope?.launch {
            if (persist) {
                userPreferencesRepository.setBooksSortOption(sortOption.storageKey)
            }
            _currentAlbumSortOption.value = sortOption

            val sorted = when (sortOption) {
                SortOption.BookTitleAZ -> _albums.value.sortedBy { it.title.lowercase() }
                SortOption.BookTitleZA -> _albums.value.sortedByDescending { it.title.lowercase() }
                SortOption.BookAuthor -> _albums.value.sortedBy { it.author.lowercase() }
                SortOption.BookReleaseYear -> _albums.value.sortedByDescending { it.year }
                SortOption.BookSizeAsc -> _albums.value.sortedWith(compareBy<Book> { it.trackCount }.thenBy { it.title.lowercase() })
                SortOption.BookSizeDesc -> _albums.value.sortedWith(compareByDescending<Book> { it.trackCount }.thenBy { it.title.lowercase() })
                 else -> _albums.value
            }
            _albums.value = sorted.toImmutableList()
        }
    }
    
    fun sortArtists(sortOption: SortOption, persist: Boolean = true) {
        scope?.launch {
            if (persist) {
                userPreferencesRepository.setAuthorsSortOption(sortOption.storageKey)
            }
            _currentArtistSortOption.value = sortOption

            val sorted = when (sortOption) {
                SortOption.AuthorNameAZ -> _artists.value.sortedBy { it.name.lowercase() }
                SortOption.AuthorNameZA -> _artists.value.sortedByDescending { it.name.lowercase() }
                else -> _artists.value
            }
            _artists.value = sorted.toImmutableList()
        }
    }

    fun sortFolders(sortOption: SortOption) {
        scope?.launch {
            // Folders sort preference might not be persisted in the same way or done elsewhere?
            // ViewModel checked "setFoldersPlaylistView" but not explicitly saving sort option in "sortFolders" function 
            // except locally in state?
            // Checking ViewModel: it just updates _playerUiState.
            // But wait, initialize() loads getFolderSortOption(). So it should be persisted.
            // Looking at ViewModel code again: sortFolders(sortOption) implementation at 4150 DOES NOT persist.
            // But initialize calls userPreferencesRepository.getFolderSortOption().
            // So perhaps persistence is missing in ViewModel or handled differently?
            // I will add persistence if 'persist' arg is added or just match ViewModel behavior.
            // The ViewModel sortFolders takes only sortOption.
            
            _currentFolderSortOption.value = sortOption
            
            val sorted = when (sortOption) {
                SortOption.FolderNameAZ -> _audiobookFolders.value.sortedBy { it.name.lowercase() }
                SortOption.FolderNameZA -> _audiobookFolders.value.sortedByDescending { it.name.lowercase() }
                else -> _audiobookFolders.value
            }
            _audiobookFolders.value = sorted.toImmutableList()
        }
    }

    fun sortFavoriteSongs(sortOption: SortOption, persist: Boolean = true) {
        scope?.launch {
            if (persist) {
                userPreferencesRepository.setLikedTracksSortOption(sortOption.storageKey)
            }
            _currentFavoriteSortOption.value = sortOption
            // The actual filtering/sorting of favorites happens in ViewModel using this flow
        }
    }

    /**
     * Updates a single track in the in-memory list.
     * Used effectively after metadata edits to reflect changes immediately.
     */
    fun updateSong(updatedSong: Track) {
        _allTracks.update { currentList ->
            currentList.map { if (it.id == updatedSong.id) updatedSong else it }.toImmutableList()
        }
    }
}

private fun androidx.compose.ui.graphics.Color.toHexString(): String {
    return String.format("#%08X", this.toArgb())
}



