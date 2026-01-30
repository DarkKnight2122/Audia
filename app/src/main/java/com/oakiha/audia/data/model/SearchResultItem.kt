package com.oakiha.audia.data.model

import androidx.compose.runtime.Immutable

@Immutable
sealed interface SearchResultItem {
    data class TrackItem(val Track: Track) : SearchResultItem
    data class BookItem(val Book: Book) : SearchResultItem
    data class AuthorItem(val Author: Author) : SearchResultItem
    data class BooklistItem(val Booklist: Booklist) : SearchResultItem
}
