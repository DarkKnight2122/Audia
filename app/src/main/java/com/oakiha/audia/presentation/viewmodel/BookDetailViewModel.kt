package com.oakiha.audia.presentation.viewmodel

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oakiha.audia.data.model.Book
import com.oakiha.audia.data.model.Track
import com.oakiha.audia.data.repository.AudiobookRepository // Importar AudiobookRepository
import com.oakiha.audia.R
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

data class BookDetailUiState(
    val book: Book? = null,
    val tracks: List<Track> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class BookDetailViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val audiobookRepository: AudiobookRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(BookDetailUiState())
    val uiState: StateFlow<BookDetailUiState> = _uiState.asStateFlow()

    init {
        val bookIdString: String? = savedStateHandle.get("bookId")
        if (bookIdString != null) {
            val bookId = bookIdString.toLongOrNull()
            if (bookId != null) {
                loadAlbumData(bookId)
            } else {
                _uiState.update { it.copy(error = context.getString(R.string.invalid_book_id), isLoading = false) }
            }
        } else {
            _uiState.update { it.copy(error = context.getString(R.string.book_id_not_found), isLoading = false) }
        }
    }

    private fun loadAlbumData(id: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val albumDetailsFlow = audiobookRepository.getBookById(id)
                val bookTracksFlow = audiobookRepository.getTracksForAlbum(id)

                combine(albumDetailsFlow, bookTracksFlow) { book, tracks ->
                    if (book != null) {
                        BookDetailUiState(
                            book = book,
                            tracks = tracks.sortedBy { it.trackNumber },
                            isLoading = false
                        )
                    } else {
                        BookDetailUiState(
                            error = context.getString(R.string.book_not_found),
                            isLoading = false
                        )
                    }
                }
                    .catch { e ->
                        emit(
                            BookDetailUiState(
                                error = context.getString(R.string.error_loading_book, e.localizedMessage ?: ""),
                                isLoading = false
                            )
                        )
                    }
                    .collect { newState ->
                        _uiState.value = newState
                    }

            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        error = context.getString(R.string.error_loading_book, e.localizedMessage ?: ""),
                        isLoading = false
                    )
                }
            }
        }
    }

    fun update(tracks: List<Track>) {
        _uiState.update {
            it.copy(
                isLoading = false,
                tracks = tracks
            )
        }
    }
}

