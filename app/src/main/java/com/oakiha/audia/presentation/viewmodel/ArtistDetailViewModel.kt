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
    val Author: Author? = null,
    val Tracks: List<Track> = emptyList(),
    val Booksections: List<AuthorBooksection> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@Immutable
data class AuthorBooksection(
    val BookId: Long,
    val title: String,
    val year: Int?,
    val BookArtUriString: String?,
    val Tracks: List<Track>
)

@HiltViewModel
class AuthorDetailViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val AudiobookRepository: AudiobookRepository,
    private val AuthorImageRepository: AuthorImageRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthorDetailUiState())
    val uiState: StateFlow<AuthorDetailUiState> = _uiState.asStateFlow()

    init {
        val AuthorIdString: String? = savedStateHandle.get("AuthorId")
        if (AuthorIdString != null) {
            val AuthorId = AuthorIdString.toLongOrNull()
            if (AuthorId != null) {
                loadAuthorData(AuthorId)
            } else {
                _uiState.update { it.copy(error = context.getString(R.string.invalid_Author_id), isLoading = false) }
            }
        } else {
            _uiState.update { it.copy(error = context.getString(R.string.Author_id_not_found), isLoading = false) }
        }
    }

    private fun loadAuthorData(id: Long) {
        viewModelScope.launch {
            Log.d("AuthorDebug", "loadAuthorData: id=$id")
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val AuthorDetailsFlow = AudiobookRepository.getAuthorById(id)
                val AuthorTracksFlow = AudiobookRepository.getTracksForAuthor(id)

                combine(AuthorDetailsFlow, AuthorTracksFlow) { Author, Tracks ->
                    Log.d("AuthorDebug", "loadAuthorData: id=$id found=${Author != null} Tracks=${Tracks.size}")
                    if (Author != null) {
                        val Booksections = buildBooksections(Tracks)
                        val orderedTracks = Booksections.flatMap { it.Tracks }
                        AuthorDetailUiState(
                            Author = Author,
                            Tracks = orderedTracks,
                            Booksections = Booksections,
                            isLoading = false
                        )
                    } else {
                        AuthorDetailUiState(
                            error = context.getString(R.string.could_not_find_Author),
                            isLoading = false
                        )
                    }
                }
                    .catch { e ->
                        emit(
                            AuthorDetailUiState(
                                error = context.getString(
                                    R.string.error_loading_Author,
                                    e.localizedMessage ?: ""
                                ), isLoading = false
                            )
                        )
                    }
                    .collect { newState ->
                        _uiState.value = newState
                        
                        // Fetch Author image from Deezer if not already cached
                        newState.Author?.let { Author ->
                            if (Author.imageUrl.isNullOrEmpty()) {
                                launch {
                                    try {
                                        val imageUrl = AuthorImageRepository.getAuthorImageUrl(Author.name, Author.id)
                                        if (!imageUrl.isNullOrEmpty()) {
                                            _uiState.update { state ->
                                                state.copy(Author = state.Author?.copy(imageUrl = imageUrl))
                                            }
                                        }
                                    } catch (e: Exception) {
                                        Log.w("AuthorDebug", "Failed to fetch Author image: ${e.message}")
                                    }
                                }
                            }
                        }
                    }

            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        error = context.getString(R.string.error_loading_Author, e.localizedMessage ?: ""),
                        isLoading = false
                    )
                }
            }
        }
    }
    fun removeTrackFromBooksection(TrackId: String) {
        _uiState.update { currentState ->
            val updatedBooksections = currentState.Booksections.map { section ->
                // Remove the Track from this section if it exists
                val updatedTracks = section.Tracks.filterNot { it.id == TrackId }
                // Return updated section only if it still has Tracks, otherwise filter out empty sections
                section.copy(Tracks = updatedTracks)
            }.filter { it.Tracks.isNotEmpty() } // Remove empty Book sections

            currentState.copy(
                Booksections = updatedBooksections,
                Tracks = currentState.Tracks.filterNot { it.id == TrackId } // Also update the main Tracks list
            )
        }
    }
}

private val TrackDisplayComparator = compareBy<Track> {
    if (it.trackNumber > 0) it.trackNumber else Int.MAX_VALUE
}.thenBy { it.title.lowercase() }

private fun buildBooksections(Tracks: List<Track>): List<AuthorBooksection> {
    if (Tracks.isEmpty()) return emptyList()

    val sections = Tracks
        .groupBy { it.BookId to it.Book }
        .map { (key, BookTracks) ->
            val sortedTracks = BookTracks.sortedWith(TrackDisplayComparator)
            val BookYear = BookTracks.mapNotNull { Track -> Track.year.takeIf { it > 0 } }.maxOrNull()
            val BookArtUri = BookTracks.firstNotNullOfOrNull { it.BookArtUriString }
            AuthorBooksection(
                BookId = key.first,
                title = (key.second.takeIf { it.isNotBlank() } ?: "Unknown Book"),
                year = BookYear,
                BookArtUriString = BookArtUri,
                Tracks = sortedTracks
            )
        }

    val (withYear, withoutYear) = sections.partition { it.year != null }
    val withYearSorted = withYear.sortedWith(
        compareByDescending<AuthorBooksection> { it.year ?: Int.MIN_VALUE }
            .thenBy { it.title.lowercase() }
    )
    val withoutYearSorted = withoutYear.sortedBy { it.title.lowercase() }

    return withYearSorted + withoutYearSorted
}
