package com.oakiha.audia.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient // Para campos que no queremos serializar

@Serializable
data class QueueItem(
    val id: Long, // ID ÃƒÂºnico de la canciÃƒÂ³n
    val bookArtBitmapData: ByteArray?
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as QueueItem

        if (id != other.id) return false
        if (bookArtBitmapData != null) {
            if (other.bookArtBitmapData == null) return false
            if (!bookArtBitmapData.contentEquals(other.bookArtBitmapData)) return false
        } else if (other.bookArtBitmapData != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + (bookArtBitmapData?.contentHashCode() ?: 0)
        return result
    }
}

@Serializable
data class PlayerInfo(
    val trackTitle: String = "",
    val authorName: String = "",
    val isPlaying: Boolean = false,
    val bookArtUri: String? = null,
    val bookArtBitmapData: ByteArray? = null,
    val currentPositionMs: Long = 0L,
    val totalDurationMs: Long = 0L,
    val isFavorite: Boolean = false,
    val lyrics: Lyrics? = null,
    val isLoadingLyrics: Boolean = false,
    val queue: List<QueueItem> = emptyList()
) {
    // equals y hashCode para ByteArray, ya que el por defecto no es comparando contenido
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PlayerInfo

        if (trackTitle != other.trackTitle) return false
        if (authorName != other.authorName) return false
        if (isPlaying != other.isPlaying) return false
        if (bookArtUri != other.bookArtUri) return false
        if (bookArtBitmapData != null) {
            if (other.bookArtBitmapData == null) return false
            if (!bookArtBitmapData.contentEquals(other.bookArtBitmapData)) return false
        } else if (other.bookArtBitmapData != null) return false
        if (currentPositionMs != other.currentPositionMs) return false
        if (totalDurationMs != other.totalDurationMs) return false
        if (queue != other.queue) return false
        if (lyrics != other.lyrics) return false
        if (isLoadingLyrics != other.isLoadingLyrics) return false

        return true
    }

    override fun hashCode(): Int {
        var result = trackTitle.hashCode()
        result = 31 * result + authorName.hashCode()
        result = 31 * result + isPlaying.hashCode()
        result = 31 * result + (bookArtUri?.hashCode() ?: 0)
        result = 31 * result + (bookArtBitmapData?.contentHashCode() ?: 0)
        result = 31 * result + currentPositionMs.hashCode()
        result = 31 * result + totalDurationMs.hashCode()
        result = 31 * result + queue.hashCode()
        result = 31 * result + (lyrics?.hashCode() ?: 0)
        result = 31 * result + isLoadingLyrics.hashCode()
        return result
    }
}

