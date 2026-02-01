package com.oakiha.audia.data.model

import android.net.Uri
import androidx.compose.runtime.Immutable

@Immutable
data class Book(
    val id: Long, // MediaStore.Audio.Albums._ID
    val title: String,
    val author: String,
    val year: Int,
    val bookArtUriString: String?,
    val trackCount: Int
) {
    companion object {
        fun empty() = Book(
            id = -1,
            title = "",
            author = "",
            year = 0,
            bookArtUriString = null,
            trackCount = 0
        )
    }
}

@Immutable
data class Author(
    val id: Long, // MediaStore.Audio.Artists._ID
    val name: String,
    val trackCount: Int,
    val imageUrl: String? = null // Deezer author image URL
) {
    companion object {
        fun empty() = Author(
            id = -1,
            name = "",
            trackCount = 0,
            imageUrl = null
        )
    }
}

/**
 * Represents a simplified author reference for multi-author support.
 * Used when displaying multiple authors for a track.
 */
@Immutable
data class AuthorRef(
    val id: Long,
    val name: String,
    val isPrimary: Boolean = false
)

