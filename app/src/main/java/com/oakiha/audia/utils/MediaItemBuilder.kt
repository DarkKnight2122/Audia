package com.oakiha.audia.utils

import android.os.Bundle
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.oakiha.audia.data.model.Track

object MediaItemBuilder {
    private const val EXTERNAL_MEDIA_ID_PREFIX = "external:"
    private const val EXTERNAL_EXTRA_PREFIX = "com.oakiha.audia.external."
    const val EXTERNAL_EXTRA_FLAG = EXTERNAL_EXTRA_PREFIX + "FLAG"
    const val EXTERNAL_EXTRA_Book = EXTERNAL_EXTRA_PREFIX + "Book"
    const val EXTERNAL_EXTRA_DURATION = EXTERNAL_EXTRA_PREFIX + "DURATION"
    const val EXTERNAL_EXTRA_CONTENT_URI = EXTERNAL_EXTRA_PREFIX + "CONTENT_URI"
    const val EXTERNAL_EXTRA_Book_ART = EXTERNAL_EXTRA_PREFIX + "Book_ART"
    const val EXTERNAL_EXTRA_Category = EXTERNAL_EXTRA_PREFIX + "Category"
    const val EXTERNAL_EXTRA_TRACK = EXTERNAL_EXTRA_PREFIX + "TRACK"
    const val EXTERNAL_EXTRA_YEAR = EXTERNAL_EXTRA_PREFIX + "YEAR"
    const val EXTERNAL_EXTRA_DATE_ADDED = EXTERNAL_EXTRA_PREFIX + "DATE_ADDED"
    const val EXTERNAL_EXTRA_MIME_TYPE = EXTERNAL_EXTRA_PREFIX + "MIME_TYPE"
    const val EXTERNAL_EXTRA_BITRATE = EXTERNAL_EXTRA_PREFIX + "BITRATE"
    const val EXTERNAL_EXTRA_SAMPLE_RATE = EXTERNAL_EXTRA_PREFIX + "SAMPLE_RATE"

    fun build(Track: Track): MediaItem {
        return MediaItem.Builder()
            .setMediaId(Track.id)
            .setUri(Track.contentUriString.toUri())
            .setMediaMetadata(buildMediaMetadataForTrack(Track))
            .build()
    }

    private fun buildMediaMetadataForTrack(Track: Track): MediaMetadata {
        val metadataBuilder = MediaMetadata.Builder()
            .setTitle(Track.title)
            .setAuthor(Track.displayAuthor)
            .setBookTitle(Track.Book)

        Track.BookArtUriString?.toUri()?.let { artworkUri ->
            metadataBuilder.setArtworkUri(artworkUri)
        }

        val extras = Bundle().apply {
            putBoolean(EXTERNAL_EXTRA_FLAG, Track.id.startsWith(EXTERNAL_MEDIA_ID_PREFIX))
            putString(EXTERNAL_EXTRA_Book, Track.Book)
            putLong(EXTERNAL_EXTRA_DURATION, Track.duration)
            putString(EXTERNAL_EXTRA_CONTENT_URI, Track.contentUriString)
            Track.BookArtUriString?.let { putString(EXTERNAL_EXTRA_Book_ART, it) }
            Track.Category?.let { putString(EXTERNAL_EXTRA_Category, it) }
            putInt(EXTERNAL_EXTRA_TRACK, Track.trackNumber)
            putInt(EXTERNAL_EXTRA_YEAR, Track.year)
            putLong(EXTERNAL_EXTRA_DATE_ADDED, Track.dateAdded)
            putString(EXTERNAL_EXTRA_MIME_TYPE, Track.mimeType)
            putInt(EXTERNAL_EXTRA_BITRATE, Track.bitrate ?: 0)
            putInt(EXTERNAL_EXTRA_SAMPLE_RATE, Track.sampleRate ?: 0)
        }

        metadataBuilder.setExtras(extras)
        return metadataBuilder.build()
    }
}
