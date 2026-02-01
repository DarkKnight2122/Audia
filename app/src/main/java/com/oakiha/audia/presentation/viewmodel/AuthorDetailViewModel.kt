package com.oakiha.audia.presentation.viewmodel

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oakiha.audia.R
import com.oakiha.audia.data.model.Author
import com.oakiha.audia.data.model.Track
import com.oakiha.audia.data.repository.AuthorImageRepository
import com.oakiha.audia.data.repository.AudiobookRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthorDetailUiState(
    val author: Author? = null,
    val tracks: List<Track> = emptyList(),
    val bookSections: List<AuthorBookSection> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@Immutable
data class AuthorBookSection(
    val bookId: Long,
    val title: String,
    val year: Int?,
    val bookArtUriString: String?,
    val tracks: List<Track>
)

@HiltViewModel
class AuthorDetailViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val audiobookRepository: AudiobookRepository,
    private val artistImageRepository: AuthorImageRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthorDetailUiState())
    val uiState: StateFlow<AuthorDetailUiState> = _uiState.asStateFlow()

    init {
        val authorIdString: String? = savedStateHandle.get("authorId")
        if (authorIdString != null) {
            val authorId = authorIdString.toLongOrNull()
            if (authorId != null) {
                loadAuthorData(authorId)
            } else {
                _uiState.update { it.copy(error = context.getString(R.string.invalid_author_id), isLoading = false) }
            }
        } else {
            _uiState.update { it.copy(error = context.getString(R.string.author_id_not_found), isLoading = false) }
        }
    }

    private fun loadAuthorData(id: Long) {
        viewModelScope.launch {
            Log.d("AuthorDebug", "loadAuthorData: id=$id")
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val artistDetailsFlow = audiobookRepository.getAuthorById(id)
                val artistSongsFlow = audiobookRepository.getTracksForArtist(id)

                combine(artistDetailsFlow, artistSongsFlow) { author, tracks ->
                    Log.d("AuthorDebug", "loadAuthorData: id=$id found=${author != null} tracks=${tracks.size}")
                    if (author != null) {
                        val bookSections = buildBookSections(tracks)
                        val orderedSongs = bookSections.flatMap { it.tracks }
                        AuthorDetailUiState(
                            author = author,
                            tracks = orderedSongs,
                            bookSections = bookSections,
                            isLoading = false
                        )
                    } else {
                        AuthorDetailUiState(
                            error = context.getString(R.string.could_not_find_author),
                            isLoading = false
                        )
                    }
                }
                    .catch { e ->
                        emit(
                            AuthorDetailUiState(
                                error = context.getString(
                                    R.string.error_loading_author,
                                    e.localizedMessage ?: ""
                                ), isLoading = false
                            )
                        )
                    }
                    .collect { newState ->
                        _uiState.value = newState
                        
                        // Fetch author image from Deezer if not already cached
                        newState.author?.let { author ->
                            if (author.imageUrl.isNullOrEmpty()) {
                                launch {
                                    try {
                                        val imageUrl = artistImageRepository.getAuthorImageUrl(author.name, author.id)
                                        if (!imageUrl.isNullOrEmpty()) {
                                            _uiState.update { state ->
                                                state.copy(author = state.author?.copy(imageUrl = imageUrl))
                                            }
                                        }
                                    } catch (e: Exception) {
                                        Log.w("AuthorDebug", "Failed to fetch author image: ${e.message}")
                                    }
                                }
                            }
                        }
                    }

            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        error = context.getString(R.string.error_loading_author, e.localizedMessage ?: ""),
                        isLoading = false
                    )
                }
            }
        }
    }
    fun removeTrackFromBookSection(trackId: String) {
        _uiState.update { currentState ->
            val updatedAlbumSections = currentState.bookSections.map { section ->
                // Remove the track from this section if it exists
                val updatedSongs = section.tracks.filterNot { it.id == trackId }
                // Return updated section only if it still has tracks, otherwise filter out empty sections
                section.copy(tracks = updatedSongs)
            }.filter { it.tracks.isNotEmpty() } // Remove empty book sections

            currentState.copy(
                bookSections = updatedAlbumSections,
                tracks = currentState.tracks.filterNot { it.id == trackId } // Also update the main tracks list
            )
        }
    }
}

private val trackDisplayComparator = compareBy<Track> {
    if (it.trackNumber > 0) it.trackNumber else Int.MAX_VALUE
}.thenBy { it.title.lowercase() }

private fun buildBookSections(tracks: List<Track>): List<AuthorBookSection> {
    if (tracks.isEmpty()) return emptyList()

    val sections = tracks
        .groupBy { it.bookId to it.book }
        .map { (key, bookTracks) ->
            val sortedSongs = bookTracks.sortedWith(trackDisplayComparator)
            val albumYear = bookTracks.mapNotNull { track -> track.year.takeIf { it > 0 } }.maxOrNull()
            val bookArtUri = bookTracks.firstNotNullOfOrNull { it.bookArtUriString }
            AuthorBookSection(
                bookId = key.first,
                title = (key.second.takeIf { it.isNotBlank() } ?: "Unknown Book"),
                year = albumYear,
                bookArtUriString = bookArtUri,
                tracks = sortedSongs
            )
        }

    val (withYear, withoutYear) = sections.partition { it.year != null }
    val withYearSorted = withYear.sortedWith(
        compareByDescending<AuthorBookSection> { it.year ?: Int.MIN_VALUE }
            .thenBy { it.title.lowercase() }
    )
    val withoutYearSorted = withoutYear.sortedBy { it.title.lowercase() }

    return withYearSorted + withoutYearSorted
}

