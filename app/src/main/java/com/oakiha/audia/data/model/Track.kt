package com.oakiha.audia.data.model

import android.net.Uri
import androidx.compose.runtime.Immutable
import com.oakiha.audia.utils.splitArtistsByDelimiters

@Immutable
data class Track(
    val id: String,
    val title: String,
    /**
     * Legacy artist display string.
     * - With multi-artist parsing enabled by default, this typically contains only the primary artist for backward compatibility.
     * For accurate display of all artists, use the [artists] list and [displayAuthor] property.
     */
    val artist: String,
    val authorId: Long, // Primary artist ID for backward compatibility
    val artists: List<AuthorRef> = emptyList(), // All artists for multi-artist support
    val album: String,
    val bookId: Long,
    val bookArtist: String? = null, // Album artist from metadata
    val path: String, // Added for direct file system access
    val contentUriString: String,
    val bookArtUriString: String?,
    val duration: Long,
    val genre: String? = null,
    val lyrics: String? = null,
    val isFavorite: Boolean = false,
    val trackNumber: Int = 0,
    val year: Int = 0,
    val dateAdded: Long = 0,
    val dateModified: Long = 0,
    val mimeType: String?,
    val bitrate: Int?,
    val sampleRate: Int?,
) {
    private val defaultArtistDelimiters = listOf("/", ";", ",", "+", "&")

    /**
     * Returns the display string for artists.
     * If multiple artists exist, joins them with ", ".
     * Falls back to splitting the legacy artist string using common delimiters,
     * and finally the raw artist field if nothing else is available.
     */
    val displayAuthor: String
        get() {
            if (artists.isNotEmpty()) {
                return artists.sortedByDescending { it.isPrimary }.joinToString(", ") { it.name }
            }
            val split = artist.splitArtistsByDelimiters(defaultArtistDelimiters)
            return if (split.isNotEmpty()) split.joinToString(", ") else artist
        }

    /**
     * Returns the primary artist from the artists list,
     * or creates one from the legacy artist field.
     */
    val primaryArtist: AuthorRef
        get() = artists.find { it.isPrimary }
            ?: artists.firstOrNull()
            ?: AuthorRef(id = authorId, name = artist, isPrimary = true)

    companion object {
        fun emptySong(): Track {
            return Track(
                id = "-1",
                title = "",
                artist = "",
                authorId = -1L,
                artists = emptyList(),
                album = "",
                bookId = -1L,
                bookArtist = null,
                path = "",
                contentUriString = "",
                bookArtUriString = null,
                duration = 0L,
                genre = null,
                lyrics = null,
                isFavorite = false,
                trackNumber = 0,
                year = 0,
                dateAdded = 0,
                dateModified = 0,
                mimeType = "-",
                bitrate = 0,
                sampleRate = 0
            )
        }
    }
}
