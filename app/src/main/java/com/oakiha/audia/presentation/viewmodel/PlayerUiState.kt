package com.oakiha.audia.presentation.viewmodel

import androidx.compose.runtime.Immutable
import com.oakiha.audia.data.model.Book
import com.oakiha.audia.data.model.Author
import com.oakiha.audia.data.model.AudiobookFolder
import com.oakiha.audia.data.model.Track
import com.oakiha.audia.data.model.SearchResultItem
import com.oakiha.audia.data.model.SortOption
import com.oakiha.audia.data.model.SearchFilterType
import com.oakiha.audia.data.model.SearchHistoryItem
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Immutable
data class PlayerUiState(
    val allTracks: ImmutableList<Track> = persistentListOf(),
    val currentPosition: Long = 0L,
    val currentPlaybackQueue: ImmutableList<Track> = persistentListOf(),
    val currentQueueSourceName: String = "All Songs",
    val books: ImmutableList<Book> = persistentListOf(),
    val authors: ImmutableList<Author> = persistentListOf(),
    val searchResults: ImmutableList<SearchResultItem> = persistentListOf(),
    val musicFolders: ImmutableList<AudiobookFolder> = persistentListOf(),
    val sortOption: SortOption = SortOption.TrackDefaultOrder,
    val isLoadingInitialSongs: Boolean = true,
    val isLoadingLibrary: Boolean = true,
    val filteredSongs: ImmutableList<Track> = persistentListOf(), // For search filtering within lists
    val isFiltering: Boolean = false,
    val showDismissUndoBar: Boolean = false,
    val dismissedSong: Track? = null,
    val dismissedQueue: ImmutableList<Track> = persistentListOf(),
    val dismissedQueueName: String = "",
    val dismissedPosition: Long = 0L,
    val currentFolder: AudiobookFolder? = null,
    val currentFolderPath: String? = null,
    val lavaLampColors: ImmutableList<androidx.compose.ui.graphics.Color> = persistentListOf(),
    val undoBarVisibleDuration: Long = 4000L,
    val isFolderFilterActive: Boolean = false,
    val isFoldersPlaylistView: Boolean = false,
    val preparingSongId: String? = null,
    val isLoadingLibraryCategories: Boolean = true,
    val currentFavoriteSortOption: SortOption = SortOption.LikedTrackDateLiked,
    val currentAlbumSortOption: SortOption = SortOption.BookTitleAZ,
    val currentArtistSortOption: SortOption = SortOption.AuthorNameAZ,
    val currentFolderSortOption: SortOption = SortOption.FolderNameAZ,
    val currentTrackSortOption: SortOption = SortOption.TrackTitleAZ,
    val trackCount: Int = 0,
    val isGeneratingAiMetadata: Boolean = false,
    val searchHistory: ImmutableList<SearchHistoryItem> = persistentListOf(),
    val searchQuery: String = "",
    val isSyncingLibrary: Boolean = false,
    val selectedSearchFilter: SearchFilterType = SearchFilterType.ALL
)

