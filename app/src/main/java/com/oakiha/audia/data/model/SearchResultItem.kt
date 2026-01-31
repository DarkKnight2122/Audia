package com.oakiha.audia.data.model

import androidx.compose.runtime.Immutable

@Immutable
sealed interface SearchResultItem {
    data class TrackItem(val song: Track) : SearchResultItem
    data class BookItem(val album: Book) : SearchResultItem
    data class AuthorItem(val artist: Author) : SearchResultItem
    data class PlaylistItem(val playlist: Playlist) : SearchResultItem
}
