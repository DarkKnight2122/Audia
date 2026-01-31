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

    fun build(song: Track): MediaItem {
        return MediaItem.Builder()
            .setMediaId(song.id)
            .setUri(song.contentUriString.toUri())
            .setMediaMetadata(buildMediaMetadataForSong(song))
            .build()
    }

    private fun buildMediaMetadataForSong(song: Track): MediaMetadata {
        val metadataBuilder = MediaMetadata.Builder()
            .setTitle(song.title)
            .setArtist(song.displayAuthor)
            .setAlbumTitle(song.album)

        song.bookArtUriString?.toUri()?.let { artworkUri ->
            metadataBuilder.setArtworkUri(artworkUri)
        }

        val extras = Bundle().apply {
            putBoolean(EXTERNAL_EXTRA_FLAG, song.id.startsWith(EXTERNAL_MEDIA_ID_PREFIX))
            putString(EXTERNAL_EXTRA_ALBUM, song.album)
            putLong(EXTERNAL_EXTRA_DURATION, song.duration)
            putString(EXTERNAL_EXTRA_CONTENT_URI, song.contentUriString)
            song.bookArtUriString?.let { putString(EXTERNAL_EXTRA_ALBUM_ART, it) }
            song.genre?.let { putString(EXTERNAL_EXTRA_GENRE, it) }
            putInt(EXTERNAL_EXTRA_TRACK, song.trackNumber)
            putInt(EXTERNAL_EXTRA_YEAR, song.year)
            putLong(EXTERNAL_EXTRA_DATE_ADDED, song.dateAdded)
            putString(EXTERNAL_EXTRA_MIME_TYPE, song.mimeType)
            putInt(EXTERNAL_EXTRA_BITRATE, song.bitrate ?: 0)
            putInt(EXTERNAL_EXTRA_SAMPLE_RATE, song.sampleRate ?: 0)
        }

        metadataBuilder.setExtras(extras)
        return metadataBuilder.build()
    }
}
