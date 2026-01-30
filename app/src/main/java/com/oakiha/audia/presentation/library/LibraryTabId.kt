package com.oakiha.audia.presentation.library

import com.oakiha.audia.data.model.SortOption
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/**
 * Stable identifiers for each library tab. The [stableKey] value is persisted so it must not
 * change between app versions.
 */
enum class LibraryTabId(
    val stableKey: String,
    val label: String,
    val sortOptions: List<SortOption>
) {
    Tracks(
        stableKey = "Tracks",
        label = "Tracks",
        sortOptions = listOf(
            SortOption.TrackTitleAZ,
            SortOption.TrackTitleZA,
            SortOption.TrackAuthor,
            SortOption.TrackBook,
            SortOption.TrackDateAdded,
            SortOption.TrackDuration
        )
    ),
    Books(
        stableKey = "Books",
        label = "Books",
        sortOptions = listOf(
            SortOption.BookTitleAZ,
            SortOption.BookTitleZA,
            SortOption.BookAuthor,
            SortOption.BookReleaseYear
        )
    ),
    Authors(
        stableKey = "Author",
        label = "Author",
        sortOptions = listOf(
            SortOption.AuthorNameAZ,
            SortOption.AuthorNameZA
        )
    ),
    Booklists(
        stableKey = "Booklists",
        label = "Booklists",
        sortOptions = listOf(
            SortOption.BooklistNameAZ,
            SortOption.BooklistNameZA,
            SortOption.BooklistDateCreated
        )
    ),
    Folders(
        stableKey = "FOLDERS",
        label = "FOLDERS",
        sortOptions = listOf(
            SortOption.FolderNameAZ,
            SortOption.FolderNameZA
        )
    ),
    Liked(
        stableKey = "LIKED",
        label = "LIKED",
        sortOptions = listOf(
            SortOption.LikedTrackTitleAZ,
            SortOption.LikedTrackTitleZA,
            SortOption.LikedTrackAuthor,
            SortOption.LikedTrackBook,
            SortOption.LikedTrackDateLiked
        )
    );

    companion object {
        val defaultOrder: List<LibraryTabId> = entries.toList()

        fun fromStableKey(key: String): LibraryTabId? = entries.firstOrNull { it.stableKey == key }
    }
}

internal fun decodeLibraryTabOrder(orderJson: String?): List<LibraryTabId> {
    val storedKeys = orderJson?.let {
        runCatching { Json.decodeFromString<List<String>>(it) }.getOrNull()
    } ?: emptyList()

    val ordered = LinkedHashSet<LibraryTabId>()
    storedKeys.mapNotNull { LibraryTabId.fromStableKey(it) }.forEach { ordered.add(it) }
    LibraryTabId.defaultOrder.forEach { ordered.add(it) }
    return ordered.toList()
}
