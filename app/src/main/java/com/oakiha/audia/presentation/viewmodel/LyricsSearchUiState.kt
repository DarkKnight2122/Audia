package com.oakiha.audia.presentation.viewmodel

import com.oakiha.audia.data.model.Transcript
import com.oakiha.audia.data.repository.TranscriptSearchResult

sealed interface TranscriptSearchUiState {
    object Idle : TranscriptSearchUiState
    object Loading : TranscriptSearchUiState
    data class PickResult(val query: String, val results: List<TranscriptSearchResult>) : TranscriptSearchUiState
    data class Success(val Transcript: Transcript) : TranscriptSearchUiState
    data class NotFound(val message: String, val allowManualSearch: Boolean = true) : TranscriptSearchUiState
    data class Error(val message: String, val query: String? = null) : TranscriptSearchUiState
}
