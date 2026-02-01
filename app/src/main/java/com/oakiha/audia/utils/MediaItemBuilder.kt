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
    const val EXTERNAL_EXTRA_ALBUM = EXTERNAL_EXTRA_PREFIX + "ALBUM"
    const val EXTERNAL_EXTRA_DURATION = EXTERNAL_EXTRA_PREFIX + "DURATION"
    const val EXTERNAL_EXTRA_CONTENT_URI = EXTERNAL_EXTRA_PREFIX + "CONTENT_URI"
    const val EXTERNAL_EXTRA_ALBUM_ART = EXTERNAL_EXTRA_PREFIX + "ALBUM_ART"
    const val EXTERNAL_EXTRA_GENRE = EXTERNAL_EXTRA_PREFIX + "GENRE"
    const val EXTERNAL_EXTRA_TRACK = EXTERNAL_EXTRA_PREFIX + "TRACK"
    const val EXTERNAL_EXTRA_YEAR = EXTERNAL_EXTRA_PREFIX + "YEAR"
    const val EXTERNAL_EXTRA_DATE_ADDED = EXTERNAL_EXTRA_PREFIX + "DATE_ADDED"
    const val EXTERNAL_EXTRA_MIME_TYPE = EXTERNAL_EXTRA_PREFIX + "MIME_TYPE"
    const val EXTERNAL_EXTRA_BITRATE = EXTERNAL_EXTRA_PREFIX + "BITRATE"
    const val EXTERNAL_EXTRA_SAMPLE_RATE = EXTERNAL_EXTRA_PREFIX + "SAMPLE_RATE"

    fun build(track: Track): MediaItem {
        return MediaItem.Builder()
            .setMediaId(track.id)
            .setUri(track.contentUriString.toUri())
            .setMediaMetadata(buildMediaMetadataForSong(track))
            .build()
    }

    private fun buildMediaMetadataForSong(track: Track): MediaMetadata {
        val metadataBuilder = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.displayAuthor)
            .setAlbumTitle(track.book)

        track.bookArtUriString?.toUri()?.let { artworkUri ->
            metadataBuilder.setArtworkUri(artworkUri)
        }

        val extras = Bundle().apply {
            putBoolean(EXTERNAL_EXTRA_FLAG, track.id.startsWith(EXTERNAL_MEDIA_ID_PREFIX))
            putString(EXTERNAL_EXTRA_ALBUM, track.book)
            putLong(EXTERNAL_EXTRA_DURATION, track.duration)
            putString(EXTERNAL_EXTRA_CONTENT_URI, track.contentUriString)
            track.bookArtUriString?.let { putString(EXTERNAL_EXTRA_ALBUM_ART, it) }
            track.genre?.let { putString(EXTERNAL_EXTRA_GENRE, it) }
            putInt(EXTERNAL_EXTRA_TRACK, track.trackNumber)
            putInt(EXTERNAL_EXTRA_YEAR, track.year)
            putLong(EXTERNAL_EXTRA_DATE_ADDED, track.dateAdded)
            putString(EXTERNAL_EXTRA_MIME_TYPE, track.mimeType)
            putInt(EXTERNAL_EXTRA_BITRATE, track.bitrate ?: 0)
            putInt(EXTERNAL_EXTRA_SAMPLE_RATE, track.sampleRate ?: 0)
        }

        metadataBuilder.setExtras(extras)
        return metadataBuilder.build()
    }
}
