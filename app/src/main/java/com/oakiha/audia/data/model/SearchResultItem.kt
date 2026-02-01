package com.oakiha.audia.data.model

import androidx.compose.runtime.Immutable

@Immutable
sealed interface SearchResultItem {
    data class TrackItem(val track: Track) : SearchResultItem
    data class BookItem(val book: Book) : SearchResultItem
    data class AuthorItem(val author: Author) : SearchResultItem
    data class PlaylistItem(val playlist: Playlist) : SearchResultItem
}
