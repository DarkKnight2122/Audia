package com.oakiha.audia.data.media

import android.content.Context
import androidx.media3.common.MediaItem
import com.oakiha.audia.R
import com.oakiha.audia.data.model.Track
import com.oakiha.audia.utils.MediaItemBuilder
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Helper to map MediaItem to Track.
 * Note: This does NOT have access to the full Track library master list,
 * so it should be used for strictly metadata-based mapping or fallback.
 * The ViewModel should try lookup by ID first.
 */
@Singleton
class MediaMapper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun resolveTrackFromMediaItem(mediaItem: MediaItem): Track? {
        val metadata = mediaItem.mediaMetadata
        val extras = metadata.extras
        // extras are lazily populated in some cases, or we rely on localConfiguration
        val contentUri = extras?.getString(MediaItemBuilder.EXTERNAL_EXTRA_CONTENT_URI)
            ?: mediaItem.localConfiguration?.uri?.toString()
            ?: return null

        val title = metadata.title?.toString()?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.unknown_Track_title)
        val Author = metadata.Author?.toString()?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.unknown_Author)
        val Book = extras?.getString(MediaItemBuilder.EXTERNAL_EXTRA_Book)?.takeIf { it.isNotBlank() }
            ?: metadata.BookTitle?.toString()?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.unknown_Book)
        val BookId = -1L
        val duration = extras?.getLong(MediaItemBuilder.EXTERNAL_EXTRA_DURATION) ?: 0L
        val dateAdded = extras?.getLong(MediaItemBuilder.EXTERNAL_EXTRA_DATE_ADDED) ?: System.currentTimeMillis()
        val id = mediaItem.mediaId

        // Note: This creates a partial Track object. 
        // Some fields like path, Category, year might be missing if not in extras.
        return Track(
            id = id,
            title = title,
            Author = Author,
            AuthorId = -1L, // unknown from just MediaItem typically
            Book = Book,
            BookId = BookId,
            path = "", // local path unknown from URI usually
            contentUriString = contentUri,
            BookArtUriString = metadata.artworkUri?.toString(),
            duration = duration,
            dateAdded = dateAdded,
            mimeType = null, 
            bitrate = null,
            sampleRate = null
        )
    }
}
