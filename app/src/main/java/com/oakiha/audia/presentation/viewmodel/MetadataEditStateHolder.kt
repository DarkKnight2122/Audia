package com.oakiha.audia.presentation.viewmodel

import android.content.Context
import android.util.Log
import com.oakiha.audia.data.media.CoverArtUpdate
import com.oakiha.audia.data.media.ImageCacheManager
import com.oakiha.audia.data.media.MetadataEditError
import com.oakiha.audia.data.media.TrackMetadataEditor
import com.oakiha.audia.data.model.Lyrics
import com.oakiha.audia.data.model.Track
import com.oakiha.audia.data.repository.AudiobookRepository
import com.oakiha.audia.utils.FileDeletionUtils
import com.oakiha.audia.utils.LyricsUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject

class MetadataEditStateHolder @Inject constructor(
    private val songMetadataEditor: TrackMetadataEditor,
    private val audiobookRepository: AudiobookRepository,
    private val imageCacheManager: ImageCacheManager,
    private val themeStateHolder: ThemeStateHolder,
    @ApplicationContext private val context: Context
) {

    data class MetadataEditResult(
        val success: Boolean,
        val updatedSong: Track? = null,
        val updatedAlbumArtUri: String? = null,
        val parsedLyrics: Lyrics? = null,
        val error: MetadataEditError? = null,
        val errorMessage: String? = null
    ) {
        /**
         * Returns a user-friendly error message based on the error type
         */
        fun getUserFriendlyErrorMessage(): String {
            return when (error) {
                MetadataEditError.FILE_NOT_FOUND -> "The song file could not be found. It may have been moved or deleted."
                MetadataEditError.NO_WRITE_PERMISSION -> "Cannot edit this file. You may need to grant additional permissions or the file is on read-only storage."
                MetadataEditError.INVALID_INPUT -> errorMessage ?: "Invalid input provided."
                MetadataEditError.UNSUPPORTED_FORMAT -> "This file format is not supported for editing."
                MetadataEditError.TAGLIB_ERROR -> "Failed to write metadata to the file. The file may be corrupted."
                MetadataEditError.TIMEOUT -> "The operation took too long and was cancelled."
                MetadataEditError.FILE_CORRUPTED -> "The file appears to be corrupted or in an unsupported format."
                MetadataEditError.IO_ERROR -> "An error occurred while accessing the file. Please try again."
                MetadataEditError.UNKNOWN, null -> errorMessage ?: "An unknown error occurred while editing metadata."
            }
        }
    }

    suspend fun saveMetadata(
        track: Track,
        newTitle: String,
        newArtist: String,
        newAlbum: String,
        newGenre: String,
        newLyrics: String,
        newTrackNumber: Int,
        coverArtUpdate: CoverArtUpdate?
    ): MetadataEditResult = withContext(Dispatchers.IO) {
        
        Log.d("MetadataEditStateHolder", "Starting saveMetadata for: ${song.title}")

        // CRITICAL FIX: Preserve existing embedded artwork if the user didn't provide a new one.
        // Editing text metadata might strip the artwork if the underlying tagging library
        // overwrites the file structure. Explicitly re-saving the existing artwork prevents this.
        val finalCoverArtUpdate = if (coverArtUpdate == null) {
            val existingMetadata = try {
                 com.oakiha.audia.data.media.AudioMetadataReader.read(java.io.File(song.path))
            } catch (e: Exception) {
                null
            }
            if (existingMetadata?.artwork != null) {
                Log.d("MetadataEditStateHolder", "Preserving existing embedded artwork")
                CoverArtUpdate(existingMetadata.artwork.bytes, existingMetadata.artwork.mimeType ?: "image/jpeg")
            } else {
                null
            }
        } else {
            coverArtUpdate
        }

        val trimmedLyrics = newLyrics.trim()
        val normalizedLyrics = trimmedLyrics.takeIf { it.isNotBlank() }
        // We parse lyrics here just to ensure they are valid or to have them ready, 
        // essentially mirroring logic in ViewModel
        val parsedLyrics = normalizedLyrics?.let { LyricsUtils.parseLyrics(it) }

        val result = songMetadataEditor.editTrackMetadata(
            newTitle = newTitle,
            newArtist = newArtist,
            newAlbum = newAlbum,
            newGenre = newGenre,
            newLyrics = trimmedLyrics,
            newTrackNumber = newTrackNumber,
            coverArtUpdate = finalCoverArtUpdate,
            trackId = song.id.toLong(),
        )

        Log.d("MetadataEditStateHolder", "Editor result: success=${result.success}, error=${result.error}")

        if (result.success) {
            val refreshedAlbumArtUri = result.updatedAlbumArtUri ?: song.bookArtUriString
            
            // Update Repository (Lyrics)
            if (normalizedLyrics != null) {
                audiobookRepository.updateLyrics(song.id.toLong(), normalizedLyrics)
            } else {
                audiobookRepository.resetLyrics(song.id.toLong())
            }

            val updatedSong = song.copy(
                title = newTitle,
                artist = newArtist,
                album = newAlbum,
                genre = newGenre,
                lyrics = normalizedLyrics,
                trackNumber = newTrackNumber,
                bookArtUriString = refreshedAlbumArtUri,
            )

            // CRITICAL: Fetch the authoritative song object from the repository (MediaStore/DB).
            // When metadata changes (especially album/artist), MediaStore might re-index the song
            // and assign it a NEW album ID, resulting in a NEW bookArtUri.
            // Using the 'updatedSong' copy above might retain a STALE bookArtUri.
            val freshSong = try {
                audiobookRepository.getTrack(song.id).first() ?: updatedSong
            } catch (e: Exception) {
                updatedSong
            }

            // Force cache invalidation if album art might have changed
            if (refreshedAlbumArtUri != null) {
                // Invalidate Coil/Glide caches
                imageCacheManager.invalidateCoverArtCaches(refreshedAlbumArtUri)
                
                // Force regenerate palette
                themeStateHolder.forceRegenerateColorScheme(refreshedAlbumArtUri)
            }

            MetadataEditResult(
                success = true,
                updatedSong = freshSong,
                updatedAlbumArtUri = freshSong.bookArtUriString,
                parsedLyrics = parsedLyrics
            )
        } else {
            Log.w("MetadataEditStateHolder", "Metadata edit failed: ${result.error} - ${result.errorMessage}")
            MetadataEditResult(
                success = false,
                error = result.error,
                errorMessage = result.errorMessage
            )
        }
    }

    suspend fun deleteSong(track: Track): Boolean = withContext(Dispatchers.IO) {
        val fileInfo = FileDeletionUtils.getFileInfo(song.path)
        if (fileInfo.exists && fileInfo.canWrite) {
            val success = FileDeletionUtils.deleteFile(context, song.path)
            if (success) {
                // Remove from DB happens in ViewModel call logic or should happen here?
                // VM's deleteFromDevice calls removeSong -> toggleFavorite(false) -> updates lists.
                // It does NOT explicitly call repository.deleteSong() because MediaStore/FileObserver handles it?
                // Or maybe explicit deletion IS needed but VM logic (Line 3687) says "removeSong(song)".
                // removeSong(3698) toggles favorites and updates _masterAllTracks. It implies memory update.
                // FileDeletionUtils deletes the physical file. The MediaScanner should eventually pick it up, 
                // but for immediate UI responsiveness, manual update is good.
                // Also, AudiobookRepository.deleteById(id) exists.
                // ViewModel did NOT call audiobookRepository.deleteById(). It relied on "removeSong" which is UI state only? 
                // Wait, removeSong updates UI state. Does it update DB?
                // Line 3698: toggleFavoriteSpecificSong(song, true)?? Wait.
                
                // Let's stick to returning success and letting ViewModel handle UI updates for now, 
                // or if we want to be thorough, we call repository delete.
                // But if ViewModel wasn't doing it, I won't add it to change behavior.
                true
            } else {
                false
            }
        } else {
            false
        }
    }
}
