package com.oakiha.audia.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient // Para campos que no queremos serializar

@Serializable
data class QueueItem(
    val id: Long, // ID ÃƒÂºnico de la canciÃƒÂ³n
    val BookArtBitmapData: ByteArray?
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as QueueItem

        if (id != other.id) return false
        if (BookArtBitmapData != null) {
            if (other.BookArtBitmapData == null) return false
            if (!BookArtBitmapData.contentEquals(other.BookArtBitmapData)) return false
        } else if (other.BookArtBitmapData != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + (BookArtBitmapData?.contentHashCode() ?: 0)
        return result
    }
}

@Serializable
data class PlayerInfo(
    val TrackTitle: String = "",
    val AuthorName: String = "",
    val isPlaying: Boolean = false,
    val BookArtUri: String? = null,
    val BookArtBitmapData: ByteArray? = null,
    val currentPositionMs: Long = 0L,
    val totalDurationMs: Long = 0L,
    val isFavorite: Boolean = false,
    val Transcript: Transcript? = null,
    val isLoadingTranscript: Boolean = false,
    val queue: List<QueueItem> = emptyList()
) {
    // equals y hashCode para ByteArray, ya que el por defecto no es comparando contenido
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PlayerInfo

        if (TrackTitle != other.TrackTitle) return false
        if (AuthorName != other.AuthorName) return false
        if (isPlaying != other.isPlaying) return false
        if (BookArtUri != other.BookArtUri) return false
        if (BookArtBitmapData != null) {
            if (other.BookArtBitmapData == null) return false
            if (!BookArtBitmapData.contentEquals(other.BookArtBitmapData)) return false
        } else if (other.BookArtBitmapData != null) return false
        if (currentPositionMs != other.currentPositionMs) return false
        if (totalDurationMs != other.totalDurationMs) return false
        if (queue != other.queue) return false
        if (Transcript != other.Transcript) return false
        if (isLoadingTranscript != other.isLoadingTranscript) return false

        return true
    }

    override fun hashCode(): Int {
        var result = TrackTitle.hashCode()
        result = 31 * result + AuthorName.hashCode()
        result = 31 * result + isPlaying.hashCode()
        result = 31 * result + (BookArtUri?.hashCode() ?: 0)
        result = 31 * result + (BookArtBitmapData?.contentHashCode() ?: 0)
        result = 31 * result + currentPositionMs.hashCode()
        result = 31 * result + totalDurationMs.hashCode()
        result = 31 * result + queue.hashCode()
        result = 31 * result + (Transcript?.hashCode() ?: 0)
        result = 31 * result + isLoadingTranscript.hashCode()
        return result
    }
}
