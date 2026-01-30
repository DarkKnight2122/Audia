package com.oakiha.audia.data.model

import androidx.compose.runtime.Immutable

// Sealed class for Sort Options
@Immutable
sealed class SortOption(val storageKey: String, val displayName: String) {
    // Track Sort Options
    object TrackDefaultOrder : SortOption("Track_default_order", "Default Order")
    object TrackTitleAZ : SortOption("Track_title_az", "Title (A-Z)")
    object TrackTitleZA : SortOption("Track_title_za", "Title (Z-A)")
    object TrackAuthor : SortOption("Track_Author", "Author")
    object TrackBook : SortOption("Track_Book", "Book")
    object TrackDateAdded : SortOption("Track_date_added", "Date Added")
    object TrackDuration : SortOption("Track_duration", "Duration")

    // Book Sort Options
    object BookTitleAZ : SortOption("Book_title_az", "Title (A-Z)")
    object BookTitleZA : SortOption("Book_title_za", "Title (Z-A)")
    object BookAuthor : SortOption("Book_Author", "Author")
    object BookReleaseYear : SortOption("Book_release_year", "Release Year")
    object BooksizeAsc : SortOption("Book_size_asc", "Fewest Tracks")
    object BooksizeDesc : SortOption("Book_size_desc", "Most Tracks")

    // Author Sort Options
    object AuthorNameAZ : SortOption("Author_name_az", "Name (A-Z)")
    object AuthorNameZA : SortOption("Author_name_za", "Name (Z-A)")
    // object AuthorNumTracks : SortOption("Author_num_Tracks", "Number of Tracks") // Requires ViewModel change & data

    // Booklist Sort Options
    object BooklistNameAZ : SortOption("Booklist_name_az", "Name (A-Z)")
    object BooklistNameZA : SortOption("Booklist_name_za", "Name (Z-A)")
    object BooklistDateCreated : SortOption("Booklist_date_created", "Date Created")
    // object BooklistNumTracks : SortOption("Booklist_num_Tracks", "Number of Tracks") // Requires ViewModel change & data

    // Liked Sort Options (similar to Tracks)
    object LikedTrackTitleAZ : SortOption("liked_title_az", "Title (A-Z)")
    object LikedTrackTitleZA : SortOption("liked_title_za", "Title (Z-A)")
    object LikedTrackAuthor : SortOption("liked_Author", "Author")
    object LikedTrackBook : SortOption("liked_Book", "Book")
    object LikedTrackDateLiked : SortOption("liked_date_liked", "Date Liked")

    // Folder Sort Options
    object FolderNameAZ : SortOption("folder_name_az", "Name (A-Z)")
    object FolderNameZA : SortOption("folder_name_za", "Name (Z-A)")

    companion object {

        val Tracks: List<SortOption> by lazy {
             listOf(
                TrackDefaultOrder,
                TrackTitleAZ,
                TrackTitleZA,
                TrackAuthor,
                TrackBook,
                TrackDateAdded,
                TrackDuration
            )
        }
        val Books: List<SortOption> by lazy {
            listOf(
                BookTitleAZ,
                BookTitleZA,
                BookAuthor,
                BookReleaseYear,
                BooksizeAsc,
                BooksizeDesc
            )
        }
        val Authors: List<SortOption> by lazy {
            listOf(
                AuthorNameAZ,
                AuthorNameZA
            )
        }
        val Booklists: List<SortOption> by lazy {
            listOf(
                BooklistNameAZ,
                BooklistNameZA,
                BooklistDateCreated
            )
        }
        val FOLDERS: List<SortOption> by lazy {
            listOf(
                FolderNameAZ,
                FolderNameZA
            )
        }
        val LIKED: List<SortOption> by lazy {
            listOf(
                LikedTrackTitleAZ,
                LikedTrackTitleZA,
                LikedTrackAuthor,
                LikedTrackBook,
                LikedTrackDateLiked
            )
        }

        fun fromStorageKey(
            rawValue: String?,
            allowed: Collection<SortOption>,
            fallback: SortOption
        ): SortOption {
            if (rawValue.isNullOrBlank()) {
                return fallback
            }

            val sanitized = allowed.filterIsInstance<SortOption>()
            if (sanitized.isEmpty()) {
                return fallback
            }

            sanitized.firstOrNull { option -> option.storageKey == rawValue }?.let { matched ->
                return matched
            }

            // Legacy values used display names; fall back to matching within the allowed group.
            return sanitized.firstOrNull { option -> option.displayName == rawValue } ?: fallback
        }
    }
}
