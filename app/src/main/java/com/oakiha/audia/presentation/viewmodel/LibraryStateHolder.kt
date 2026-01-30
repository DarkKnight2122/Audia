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
 * Manages the data state of the Audiobook library: Tracks, Books, Authors, Folders.
 * Handles loading from Repository and applying SortOptions.
 */
@Singleton
class LibraryStateHolder @Inject constructor(
    private val AudiobookRepository: AudiobookRepository,
    private val userPreferencesRepository: UserPreferencesRepository
) {

    // --- State ---
    private val _allTracks = MutableStateFlow<ImmutableList<Track>>(persistentListOf())
    val allTracks = _allTracks.asStateFlow()

    private val _Books = MutableStateFlow<ImmutableList<Book>>(persistentListOf())
    val Books = _Books.asStateFlow()

    private val _Authors = MutableStateFlow<ImmutableList<Author>>(persistentListOf())
    val Authors = _Authors.asStateFlow()

    private val _AudiobookFolders = MutableStateFlow<ImmutableList<AudiobookFolder>>(persistentListOf())
    val AudiobookFolders = _AudiobookFolders.asStateFlow()

    private val _isLoadingLibrary = MutableStateFlow(false)
    val isLoadingLibrary = _isLoadingLibrary.asStateFlow()

    private val _isLoadingCategories = MutableStateFlow(false)
    val isLoadingCategories = _isLoadingCategories.asStateFlow()

    // Sort Options
    private val _currentTracksortOption = MutableStateFlow<SortOption>(SortOption.TrackDefaultOrder)
    val currentTracksortOption = _currentTracksortOption.asStateFlow()

    private val _currentBooksortOption = MutableStateFlow<SortOption>(SortOption.BookTitleAZ)
    val currentBooksortOption = _currentBooksortOption.asStateFlow()

    private val _currentAuthorsortOption = MutableStateFlow<SortOption>(SortOption.AuthorNameAZ)
    val currentAuthorsortOption = _currentAuthorsortOption.asStateFlow()

    private val _currentFolderSortOption = MutableStateFlow<SortOption>(SortOption.FolderNameAZ)
    val currentFolderSortOption = _currentFolderSortOption.asStateFlow()

    private val _currentFavoriteSortOption = MutableStateFlow<SortOption>(SortOption.LikedTrackTitleAZ)
    val currentFavoriteSortOption = _currentFavoriteSortOption.asStateFlow()



    @OptIn(ExperimentalStdlibApi::class)
    val Categories: kotlinx.coroutines.flow.Flow<ImmutableList<com.oakiha.audia.data.model.Category>> = _allTracks
        .map { Tracks ->
            val CategoryMap = mutableMapOf<String, MutableList<Track>>()
            val unknownCategoryName = "Unknown Category"

            Tracks.forEach { Track ->
                val CategoryName = Track.Category?.trim()
                if (CategoryName.isNullOrBlank()) {
                    CategoryMap.getOrPut(unknownCategoryName) { mutableListOf() }.add(Track)
                } else {
                    CategoryMap.getOrPut(CategoryName) { mutableListOf() }.add(Track)
                }
            }

            CategoryMap.toList().mapIndexedNotNull { index, (CategoryName, Tracks) ->
                if (Tracks.isNotEmpty()) {
                    val id = if (CategoryName.equals(unknownCategoryName, ignoreCase = true)) {
                        "unknown"
                    } else {
                        CategoryName.lowercase().replace(" ", "_").replace("/", "_")
                    }
                    val color = com.oakiha.audia.ui.theme.CategoryColors.colors[index % com.oakiha.audia.ui.theme.CategoryColors.colors.size]
                    com.oakiha.audia.data.model.Category(
                        id = id,
                        name = CategoryName,
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
            val TracksortKey = userPreferencesRepository.TracksSortOptionFlow.first()
            _currentTracksortOption.value = SortOption.Tracks.find { it.storageKey == TracksortKey } ?: SortOption.TrackDefaultOrder

            val BooksortKey = userPreferencesRepository.BooksSortOptionFlow.first()
            _currentBooksortOption.value = SortOption.Books.find { it.storageKey == BooksortKey } ?: SortOption.BookTitleAZ
            
            val AuthorsortKey = userPreferencesRepository.AuthorsSortOptionFlow.first()
            _currentAuthorsortOption.value = SortOption.Authors.find { it.storageKey == AuthorsortKey } ?: SortOption.AuthorNameAZ
            
            
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
    
    private var TracksJob: Job? = null
    private var BooksJob: Job? = null
    private var AuthorsJob: Job? = null
    private var foldersJob: Job? = null
    
    fun startObservingLibraryData() {
        if (TracksJob?.isActive == true) return
        
        Log.d("LibraryStateHolder", "startObservingLibraryData called.")
        
        TracksJob = scope?.launch {
            _isLoadingLibrary.value = true
            AudiobookRepository.getAudioFiles().collect { Tracks ->
                 // When the repository emits a new list (triggered by directory changes),
                 // we update our state and re-apply current sorting.
                 _allTracks.value = Tracks.toImmutableList()
                 // Apply sort to the new data
                 sortTracks(_currentTracksortOption.value, persist = false)
                 _isLoadingLibrary.value = false
            }
        }
        
        BooksJob = scope?.launch {
            _isLoadingCategories.value = true
            AudiobookRepository.getBooks().collect { Books ->
                _Books.value = Books.toImmutableList()
                sortBooks(_currentBooksortOption.value, persist = false)
                _isLoadingCategories.value = false
            }
        }
        
        AuthorsJob = scope?.launch {
            _isLoadingCategories.value = true
            AudiobookRepository.getAuthors().collect { Authors ->
                _Authors.value = Authors.toImmutableList()
                sortAuthors(_currentAuthorsortOption.value, persist = false)
                _isLoadingCategories.value = false
            }
        }
        
        foldersJob = scope?.launch {
            AudiobookRepository.getAudiobookFolders().collect { folders ->
                 _AudiobookFolders.value = folders.toImmutableList()
                 sortFolders(_currentFolderSortOption.value)
            }
        }
    }

    // Deprecated imperative loaders - redirected to observer start
    fun loadTracksFromRepository() {
         startObservingLibraryData()
    }

    fun loadBooksFromRepository() {
         startObservingLibraryData()
    }

    fun loadAuthorsFromRepository() {
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
    
    fun loadTracksIfNeeded() {
         startObservingLibraryData()
    }

    fun loadBooksIfNeeded() {
        startObservingLibraryData()
    }

    fun loadAuthorsIfNeeded() {
        startObservingLibraryData()
    }

    // --- Sorting ---

    fun sortTracks(sortOption: SortOption, persist: Boolean = true) {
        scope?.launch {
            if (persist) {
                userPreferencesRepository.setTracksSortOption(sortOption.storageKey)
            }
            _currentTracksortOption.value = sortOption

            val sorted = when (sortOption) {
                SortOption.TrackTitleAZ -> _allTracks.value.sortedBy { it.title.lowercase() }
                SortOption.TrackTitleZA -> _allTracks.value.sortedByDescending { it.title.lowercase() }
                SortOption.TrackAuthor -> _allTracks.value.sortedBy { it.Author.lowercase() }
                SortOption.TrackBook -> _allTracks.value.sortedBy { it.Book.lowercase() }
                SortOption.TrackDateAdded -> _allTracks.value.sortedByDescending { it.dateAdded }
                SortOption.TrackDuration -> _allTracks.value.sortedBy { it.duration }
                else -> _allTracks.value // Default or unhandled
            }
            _allTracks.value = sorted.toImmutableList()
        }
    }

    fun sortBooks(sortOption: SortOption, persist: Boolean = true) {
        scope?.launch {
            if (persist) {
                userPreferencesRepository.setBooksSortOption(sortOption.storageKey)
            }
            _currentBooksortOption.value = sortOption

            val sorted = when (sortOption) {
                SortOption.BookTitleAZ -> _Books.value.sortedBy { it.title.lowercase() }
                SortOption.BookTitleZA -> _Books.value.sortedByDescending { it.title.lowercase() }
                SortOption.BookAuthor -> _Books.value.sortedBy { it.Author.lowercase() }
                SortOption.BookReleaseYear -> _Books.value.sortedByDescending { it.year }
                SortOption.BooksizeAsc -> _Books.value.sortedWith(compareBy<Book> { it.TrackCount }.thenBy { it.title.lowercase() })
                SortOption.BooksizeDesc -> _Books.value.sortedWith(compareByDescending<Book> { it.TrackCount }.thenBy { it.title.lowercase() })
                 else -> _Books.value
            }
            _Books.value = sorted.toImmutableList()
        }
    }
    
    fun sortAuthors(sortOption: SortOption, persist: Boolean = true) {
        scope?.launch {
            if (persist) {
                userPreferencesRepository.setAuthorsSortOption(sortOption.storageKey)
            }
            _currentAuthorsortOption.value = sortOption

            val sorted = when (sortOption) {
                SortOption.AuthorNameAZ -> _Authors.value.sortedBy { it.name.lowercase() }
                SortOption.AuthorNameZA -> _Authors.value.sortedByDescending { it.name.lowercase() }
                else -> _Authors.value
            }
            _Authors.value = sorted.toImmutableList()
        }
    }

    fun sortFolders(sortOption: SortOption) {
        scope?.launch {
            // Folders sort preference might not be persisted in the same way or done elsewhere?
            // ViewModel checked "setFoldersBooklistView" but not explicitly saving sort option in "sortFolders" function 
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
                SortOption.FolderNameAZ -> _AudiobookFolders.value.sortedBy { it.name.lowercase() }
                SortOption.FolderNameZA -> _AudiobookFolders.value.sortedByDescending { it.name.lowercase() }
                else -> _AudiobookFolders.value
            }
            _AudiobookFolders.value = sorted.toImmutableList()
        }
    }

    fun sortFavoriteTracks(sortOption: SortOption, persist: Boolean = true) {
        scope?.launch {
            if (persist) {
                userPreferencesRepository.setLikedTracksSortOption(sortOption.storageKey)
            }
            _currentFavoriteSortOption.value = sortOption
            // The actual filtering/sorting of favorites happens in ViewModel using this flow
        }
    }

    /**
     * Updates a single Track in the in-memory list.
     * Used effectively after metadata edits to reflect changes immediately.
     */
    fun updateTrack(updatedTrack: Track) {
        _allTracks.update { currentList ->
            currentList.map { if (it.id == updatedTrack.id) updatedTrack else it }.toImmutableList()
        }
    }
}

private fun androidx.compose.ui.graphics.Color.toHexString(): String {
    return String.format("#%08X", this.toArgb())
}
