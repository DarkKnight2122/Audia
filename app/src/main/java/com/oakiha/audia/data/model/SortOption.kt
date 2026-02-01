package com.oakiha.audia.data.model

import androidx.compose.runtime.Immutable

// Sealed class for Sort Options
@Immutable
sealed class SortOption(val storageKey: String, val displayName: String) {
    // Track Sort Options
    object TrackDefaultOrder : SortOption("song_default_order", "Default Order")
    object TrackTitleAZ : SortOption("song_title_az", "Title (A-Z)")
    object TrackTitleZA : SortOption("song_title_za", "Title (Z-A)")
    object TrackAuthor : SortOption("song_artist", "Author")
    object TrackBook : SortOption("song_album", "Book")
    object TrackDateAdded : SortOption("song_date_added", "Date Added")
    object TrackDuration : SortOption("song_duration", "Duration")

    // Book Sort Options
    object BookTitleAZ : SortOption("album_title_az", "Title (A-Z)")
    object BookTitleZA : SortOption("album_title_za", "Title (Z-A)")
    object BookAuthor : SortOption("album_artist", "Author")
    object BookReleaseYear : SortOption("album_release_year", "Release Year")
    object BookSizeAsc : SortOption("album_size_asc", "Fewest TRACKS")
    object BookSizeDesc : SortOption("album_size_desc", "Most TRACKS")

    // Author Sort Options
    object AuthorNameAZ : SortOption("artist_name_az", "Name (A-Z)")
    object AuthorNameZA : SortOption("artist_name_za", "Name (Z-A)")
    // object ArtistNumTRACKS : SortOption("artist_num_TRACKS", "Number of TRACKS") // Requires ViewModel change & data

    // Playlist Sort Options
    object PlaylistNameAZ : SortOption("playlist_name_az", "Name (A-Z)")
    object PlaylistNameZA : SortOption("playlist_name_za", "Name (Z-A)")
    object PlaylistDateCreated : SortOption("playlist_date_created", "Date Created")
    // object PlaylistNumTRACKS : SortOption("playlist_num_TRACKS", "Number of TRACKS") // Requires ViewModel change & data

    // Liked Sort Options (similar to TRACKS)
    object LikedTrackTitleAZ : SortOption("liked_title_az", "Title (A-Z)")
    object LikedTrackTitleZA : SortOption("liked_title_za", "Title (Z-A)")
    object LikedTrackAuthor : SortOption("liked_artist", "Author")
    object LikedTrackBook : SortOption("liked_album", "Book")
    object LikedTrackDateLiked : SortOption("liked_date_liked", "Date Liked")

    // Folder Sort Options
    object FolderNameAZ : SortOption("folder_name_az", "Name (A-Z)")
    object FolderNameZA : SortOption("folder_name_za", "Name (Z-A)")

    companion object {

        val TRACKS: List<SortOption> by lazy {
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
        val BOOKS: List<SortOption> by lazy {
            listOf(
                BookTitleAZ,
                BookTitleZA,
                BookAuthor,
                BookReleaseYear,
                BookSizeAsc,
                BookSizeDesc
            )
        }
        val AUTHORS: List<SortOption> by lazy {
            listOf(
                AuthorNameAZ,
                AuthorNameZA
            )
        }
        val PLAYLISTS: List<SortOption> by lazy {
            listOf(
                PlaylistNameAZ,
                PlaylistNameZA,
                PlaylistDateCreated
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

