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
    val artist: Author? = null,
    val songs: List<Track> = emptyList(),
    val albumSections: List<ArtistAlbumSection> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@Immutable
data class AuthorAlbumSection(
    val bookId: Long,
    val title: String,
    val year: Int?,
    val bookArtUriString: String?,
    val songs: List<Track>
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
                loadArtistData(authorId)
            } else {
                _uiState.update { it.copy(error = context.getString(R.string.invalid_artist_id), isLoading = false) }
            }
        } else {
            _uiState.update { it.copy(error = context.getString(R.string.author_id_not_found), isLoading = false) }
        }
    }

    private fun loadArtistData(id: Long) {
        viewModelScope.launch {
            Log.d("ArtistDebug", "loadArtistData: id=$id")
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val artistDetailsFlow = audiobookRepository.getArtistById(id)
                val artistSongsFlow = audiobookRepository.getTracksForArtist(id)

                combine(artistDetailsFlow, artistSongsFlow) { artist, songs ->
                    Log.d("ArtistDebug", "loadArtistData: id=$id found=${artist != null} songs=${songs.size}")
                    if (artist != null) {
                        val albumSections = buildAlbumSections(songs)
                        val orderedSongs = albumSections.flatMap { it.tracks }
                        AuthorDetailUiState(
                            artist = artist,
                            songs = orderedSongs,
                            albumSections = albumSections,
                            isLoading = false
                        )
                    } else {
                        AuthorDetailUiState(
                            error = context.getString(R.string.could_not_find_artist),
                            isLoading = false
                        )
                    }
                }
                    .catch { e ->
                        emit(
                            AuthorDetailUiState(
                                error = context.getString(
                                    R.string.error_loading_artist,
                                    e.localizedMessage ?: ""
                                ), isLoading = false
                            )
                        )
                    }
                    .collect { newState ->
                        _uiState.value = newState
                        
                        // Fetch artist image from Deezer if not already cached
                        newState.author?.let { artist ->
                            if (artist.imageUrl.isNullOrEmpty()) {
                                launch {
                                    try {
                                        val imageUrl = artistImageRepository.getArtistImageUrl(artist.name, artist.id)
                                        if (!imageUrl.isNullOrEmpty()) {
                                            _uiState.update { state ->
                                                state.copy(artist = state.author?.copy(imageUrl = imageUrl))
                                            }
                                        }
                                    } catch (e: Exception) {
                                        Log.w("ArtistDebug", "Failed to fetch artist image: ${e.message}")
                                    }
                                }
                            }
                        }
                    }

            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        error = context.getString(R.string.error_loading_artist, e.localizedMessage ?: ""),
                        isLoading = false
                    )
                }
            }
        }
    }
    fun removeSongFromAlbumSection(trackId: String) {
        _uiState.update { currentState ->
            val updatedAlbumSections = currentState.bookSections.map { section ->
                // Remove the song from this section if it exists
                val updatedSongs = section.tracks.filterNot { it.id == trackId }
                // Return updated section only if it still has songs, otherwise filter out empty sections
                section.copy(songs = updatedSongs)
            }.filter { it.tracks.isNotEmpty() } // Remove empty album sections

            currentState.copy(
                albumSections = updatedAlbumSections,
                songs = currentState.tracks.filterNot { it.id == trackId } // Also update the main songs list
            )
        }
    }
}

private val songDisplayComparator = compareBy<Track> {
    if (it.trackNumber > 0) it.trackNumber else Int.MAX_VALUE
}.thenBy { it.title.lowercase() }

private fun buildAlbumSections(songs: List<Track>): List<ArtistAlbumSection> {
    if (songs.isEmpty()) return emptyList()

    val sections = songs
        .groupBy { it.bookId to it.book }
        .map { (key, albumSongs) ->
            val sortedSongs = albumSongs.sortedWith(songDisplayComparator)
            val albumYear = albumSongs.mapNotNull { song -> song.year.takeIf { it > 0 } }.maxOrNull()
            val bookArtUri = albumSongs.firstNotNullOfOrNull { it.bookArtUriString }
            ArtistAlbumSection(
                bookId = key.first,
                title = (key.second.takeIf { it.isNotBlank() } ?: "Unknown Album"),
                year = albumYear,
                bookArtUriString = bookArtUri,
                songs = sortedSongs
            )
        }

    val (withYear, withoutYear) = sections.partition { it.year != null }
    val withYearSorted = withYear.sortedWith(
        compareByDescending<ArtistAlbumSection> { it.year ?: Int.MIN_VALUE }
            .thenBy { it.title.lowercase() }
    )
    val withoutYearSorted = withoutYear.sortedBy { it.title.lowercase() }

    return withYearSorted + withoutYearSorted
}
