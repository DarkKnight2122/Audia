package com.oakiha.audia.data.model

import android.net.Uri
import androidx.compose.runtime.Immutable
import com.oakiha.audia.utils.splitAuthorsByDelimiters

@Immutable
data class Track(
    val id: String,
    val title: String,
    /**
     * Legacy Author display string.
     * - With multi-Author parsing enabled by default, this typically contains only the primary Author for backward compatibility.
     * For accurate display of all Authors, use the [Authors] list and [displayAuthor] property.
     */
    val Author: String,
    val AuthorId: Long, // Primary Author ID for backward compatibility
    val Authors: List<AuthorRef> = emptyList(), // All Authors for multi-Author support
    val Book: String,
    val BookId: Long,
    val BookAuthor: String? = null, // Book Author from metadata
    val path: String, // Added for direct file system access
    val contentUriString: String,
    val BookArtUriString: String?,
    val duration: Long,
    val Category: String? = null,
    val Transcript: String? = null,
    val isFavorite: Boolean = false,
    val trackNumber: Int = 0,
    val year: Int = 0,
    val dateAdded: Long = 0,
    val dateModified: Long = 0,
    val mimeType: String?,
    val bitrate: Int?,
    val sampleRate: Int?,
) {
    private val defaultAuthorDelimiters = listOf("/", ";", ",", "+", "&")

    /**
     * Returns the display string for Authors.
     * If multiple Authors exist, joins them with ", ".
     * Falls back to splitting the legacy Author string using common delimiters,
     * and finally the raw Author field if nothing else is available.
     */
    val displayAuthor: String
        get() {
            if (Authors.isNotEmpty()) {
                return Authors.sortedByDescending { it.isPrimary }.joinToString(", ") { it.name }
            }
            val split = Author.splitAuthorsByDelimiters(defaultAuthorDelimiters)
            return if (split.isNotEmpty()) split.joinToString(", ") else Author
        }

    /**
     * Returns the primary Author from the Authors list,
     * or creates one from the legacy Author field.
     */
    val primaryAuthor: AuthorRef
        get() = Authors.find { it.isPrimary }
            ?: Authors.firstOrNull()
            ?: AuthorRef(id = AuthorId, name = Author, isPrimary = true)

    companion object {
        fun emptyTrack(): Track {
            return Track(
                id = "-1",
                title = "",
                Author = "",
                AuthorId = -1L,
                Authors = emptyList(),
                Book = "",
                BookId = -1L,
                BookAuthor = null,
                path = "",
                contentUriString = "",
                BookArtUriString = null,
                duration = 0L,
                Category = null,
                Transcript = null,
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
