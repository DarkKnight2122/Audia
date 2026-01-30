package com.oakiha.audia.presentation.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oakiha.audia.data.model.Category
import com.oakiha.audia.data.model.Track
import com.oakiha.audia.data.repository.AudiobookRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

// Define Item Types for LazyColumn
sealed interface GroupedTrackListItem {
    data class AuthorHeader(val name: String) : GroupedTrackListItem
    data class BookHeader(val name: String, val AuthorName: String, val BookArtUri: String?) : GroupedTrackListItem
    data class TrackItem(val Track: Track) : GroupedTrackListItem
}

data class CategoryDetailUiState(
    val Category: Category? = null,
    val Tracks: List<Track> = emptyList(),
    val groupedTracks: List<GroupedTrackListItem> = emptyList(),
    val isLoadingCategoryName: Boolean = false,
    val isLoadingTracks: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class CategoryDetailViewModel @Inject constructor(
    private val AudiobookRepository: AudiobookRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(CategoryDetailUiState())
    val uiState: StateFlow<CategoryDetailUiState> = _uiState.asStateFlow()

    init {
        savedStateHandle.get<String>("CategoryId")?.let { CategoryId ->
            val decodedCategoryId = java.net.URLDecoder.decode(CategoryId, "UTF-8")
            loadCategoryDetails(decodedCategoryId)
        } ?: run {
            _uiState.value = _uiState.value.copy(error = "Category ID not found", isLoadingCategoryName = false, isLoadingTracks = false)
        }
    }

    private fun loadCategoryDetails(CategoryId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingCategoryName = true, error = null)

            try {
                // Step 1: Find the Category object by its ID.
                val Categories = AudiobookRepository.getCategories().first()
                val foundCategory = Categories.find { it.id.equals(CategoryId, ignoreCase = true) }
                    ?: Category(
                        id = CategoryId,
                        name = CategoryId.replace("_", " ").replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }, // Fallback name from ID
                        lightColorHex = "#9E9E9E", onLightColorHex = "#000000",
                        darkColorHex = "#616161", onDarkColorHex = "#FFFFFF"
                    )

                _uiState.value = _uiState.value.copy(Category = foundCategory, isLoadingCategoryName = false, isLoadingTracks = true)

                // Step 2: Fetch Tracks using the Category's NAME.
                val listOfTracks = AudiobookRepository.getAudiobookByCategory(foundCategory.name).first()

                // Step 3: Group the Tracks for display.
                val groupedTracks = groupTracks(listOfTracks)

                _uiState.value = _uiState.value.copy(
                    Tracks = listOfTracks,
                    groupedTracks = groupedTracks,
                    isLoadingTracks = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to load Category details: ${e.message}",
                    isLoadingCategoryName = false,
                    isLoadingTracks = false
                )
            }
        }
    }

    private fun groupTracks(Tracks: List<Track>): List<GroupedTrackListItem> {
        val newGroupedList = mutableListOf<GroupedTrackListItem>()
        Tracks.groupBy { it.Author }
            .forEach { (AuthorName, AuthorTracks) ->
                newGroupedList.add(GroupedTrackListItem.AuthorHeader(AuthorName))
                AuthorTracks.groupBy { it.Book }
                    .forEach { (BookName, BookTracks) ->
                        val BookArtUri = BookTracks.firstOrNull()?.BookArtUriString
                        newGroupedList.add(GroupedTrackListItem.BookHeader(BookName, AuthorName, BookArtUri))
                        BookTracks.forEach { Track ->
                            newGroupedList.add(GroupedTrackListItem.TrackItem(Track))
                        }
                    }
            }
        return newGroupedList
    }
}
