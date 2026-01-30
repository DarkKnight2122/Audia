package com.oakiha.audia.data.model

import android.net.Uri
import androidx.compose.runtime.Immutable

@Immutable
data class Book(
    val id: Long, // MediaStore.Audio.Books._ID
    val title: String,
    val Author: String,
    val year: Int,
    val BookArtUriString: String?,
    val TrackCount: Int
) {
    companion object {
        fun empty() = Book(
            id = -1,
            title = "",
            Author = "",
            year = 0,
            BookArtUriString = null,
            TrackCount = 0
        )
    }
}

@Immutable
data class Author(
    val id: Long, // MediaStore.Audio.Authors._ID
    val name: String,
    val TrackCount: Int,
    val imageUrl: String? = null // Deezer Author image URL
) {
    companion object {
        fun empty() = Author(
            id = -1,
            name = "",
            TrackCount = 0,
            imageUrl = null
        )
    }
}

/**
 * Represents a simplified Author reference for multi-Author support.
 * Used when displaying multiple Authors for a Track.
 */
@Immutable
data class AuthorRef(
    val id: Long,
    val name: String,
    val isPrimary: Boolean = false
)
