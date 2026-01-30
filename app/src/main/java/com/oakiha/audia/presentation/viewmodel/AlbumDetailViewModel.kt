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
    val Book: Book? = null,
    val Tracks: List<Track> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class BookDetailViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val AudiobookRepository: AudiobookRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(BookDetailUiState())
    val uiState: StateFlow<BookDetailUiState> = _uiState.asStateFlow()

    init {
        val BookIdString: String? = savedStateHandle.get("BookId")
        if (BookIdString != null) {
            val BookId = BookIdString.toLongOrNull()
            if (BookId != null) {
                loadBookData(BookId)
            } else {
                _uiState.update { it.copy(error = context.getString(R.string.invalid_Book_id), isLoading = false) }
            }
        } else {
            _uiState.update { it.copy(error = context.getString(R.string.Book_id_not_found), isLoading = false) }
        }
    }

    private fun loadBookData(id: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val BookDetailsFlow = AudiobookRepository.getBookById(id)
                val BookTracksFlow = AudiobookRepository.getTracksForBook(id)

                combine(BookDetailsFlow, BookTracksFlow) { Book, Tracks ->
                    if (Book != null) {
                        BookDetailUiState(
                            Book = Book,
                            Tracks = Tracks.sortedBy { it.trackNumber },
                            isLoading = false
                        )
                    } else {
                        BookDetailUiState(
                            error = context.getString(R.string.Book_not_found),
                            isLoading = false
                        )
                    }
                }
                    .catch { e ->
                        emit(
                            BookDetailUiState(
                                error = context.getString(R.string.error_loading_Book, e.localizedMessage ?: ""),
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
                        error = context.getString(R.string.error_loading_Book, e.localizedMessage ?: ""),
                        isLoading = false
                    )
                }
            }
        }
    }

    fun update(Tracks: List<Track>) {
        _uiState.update {
            it.copy(
                isLoading = false,
                Tracks = Tracks
            )
        }
    }
}
