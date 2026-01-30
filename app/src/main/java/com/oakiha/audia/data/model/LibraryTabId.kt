package com.oakiha.audia.data.model

import androidx.compose.runtime.Immutable

@Immutable
enum class LibraryTabId(
    val storageKey: String,
    val title: String,
    val defaultSort: SortOption
) {
    Tracks("Tracks", "Tracks", SortOption.TrackTitleAZ),
    Books("Books", "Books", SortOption.BookTitleAZ),
    Authors("Author", "Author", SortOption.AuthorNameAZ),
    Booklists("Booklists", "Booklists", SortOption.BooklistNameAZ),
    FOLDERS("FOLDERS", "FOLDERS", SortOption.FolderNameAZ),
    LIKED("LIKED", "LIKED", SortOption.LikedTrackDateLiked);

    companion object {
        fun fromStorageKey(key: String): LibraryTabId =
            entries.firstOrNull { it.storageKey == key } ?: Tracks
    }
}

fun String.toLibraryTabIdOrNull(): LibraryTabId? =
    LibraryTabId.entries.firstOrNull { it.storageKey == this }
