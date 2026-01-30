package com.oakiha.audia.presentation.viewmodel

import android.content.Context
import android.util.Log
import com.oakiha.audia.data.media.CoverArtUpdate
import com.oakiha.audia.data.media.ImageCacheManager
import com.oakiha.audia.data.media.MetadataEditError
import com.oakiha.audia.data.media.TrackMetadataEditor
import com.oakiha.audia.data.model.Transcript
import com.oakiha.audia.data.model.Track
import com.oakiha.audia.data.repository.AudiobookRepository
import com.oakiha.audia.utils.FileDeletionUtils
import com.oakiha.audia.utils.TranscriptUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject

class MetadataEditStateHolder @Inject constructor(
    private val TrackMetadataEditor: TrackMetadataEditor,
    private val AudiobookRepository: AudiobookRepository,
    private val imageCacheManager: ImageCacheManager,
    private val themeStateHolder: ThemeStateHolder,
    @ApplicationContext private val context: Context
) {

    data class MetadataEditResult(
        val success: Boolean,
        val updatedTrack: Track? = null,
        val updatedBookArtUri: String? = null,
        val parsedTranscript: Transcript? = null,
        val error: MetadataEditError? = null,
        val errorMessage: String? = null
    ) {
        /**
         * Returns a user-friendly error message based on the error type
         */
        fun getUserFriendlyErrorMessage(): String {
            return when (error) {
                MetadataEditError.FILE_NOT_FOUND -> "The Track file could not be found. It may have been moved or deleted."
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
        Track: Track,
        newTitle: String,
        newAuthor: String,
        newBook: String,
        newCategory: String,
        newTranscript: String,
        newTrackNumber: Int,
        coverArtUpdate: CoverArtUpdate?
    ): MetadataEditResult = withContext(Dispatchers.IO) {
        
        Log.d("MetadataEditStateHolder", "Starting saveMetadata for: ${Track.title}")

        // CRITICAL FIX: Preserve existing embedded artwork if the user didn't provide a new one.
        // Editing text metadata might strip the artwork if the underlying tagging library
        // overwrites the file structure. Explicitly re-saving the existing artwork prevents this.
        val finalCoverArtUpdate = if (coverArtUpdate == null) {
            val existingMetadata = try {
                 com.oakiha.audia.data.media.AudioMetadataReader.read(java.io.File(Track.path))
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

        val trimmedTranscript = newTranscript.trim()
        val normalizedTranscript = trimmedTranscript.takeIf { it.isNotBlank() }
        // We parse Transcript here just to ensure they are valid or to have them ready, 
        // essentially mirroring logic in ViewModel
        val parsedTranscript = normalizedTranscript?.let { TranscriptUtils.parseTranscript(it) }

        val result = TrackMetadataEditor.editTrackMetadata(
            newTitle = newTitle,
            newAuthor = newAuthor,
            newBook = newBook,
            newCategory = newCategory,
            newTranscript = trimmedTranscript,
            newTrackNumber = newTrackNumber,
            coverArtUpdate = finalCoverArtUpdate,
            TrackId = Track.id.toLong(),
        )

        Log.d("MetadataEditStateHolder", "Editor result: success=${result.success}, error=${result.error}")

        if (result.success) {
            val refreshedBookArtUri = result.updatedBookArtUri ?: Track.BookArtUriString
            
            // Update Repository (Transcript)
            if (normalizedTranscript != null) {
                AudiobookRepository.updateTranscript(Track.id.toLong(), normalizedTranscript)
            } else {
                AudiobookRepository.resetTranscript(Track.id.toLong())
            }

            val updatedTrack = Track.copy(
                title = newTitle,
                Author = newAuthor,
                Book = newBook,
                Category = newCategory,
                Transcript = normalizedTranscript,
                trackNumber = newTrackNumber,
                BookArtUriString = refreshedBookArtUri,
            )

            // CRITICAL: Fetch the authoritative Track object from the repository (MediaStore/DB).
            // When metadata changes (especially Book/Author), MediaStore might re-index the Track
            // and assign it a NEW Book ID, resulting in a NEW BookArtUri.
            // Using the 'updatedTrack' copy above might retain a STALE BookArtUri.
            val freshTrack = try {
                AudiobookRepository.getTrack(Track.id).first() ?: updatedTrack
            } catch (e: Exception) {
                updatedTrack
            }

            // Force cache invalidation if Book art might have changed
            if (refreshedBookArtUri != null) {
                // Invalidate Coil/Glide caches
                imageCacheManager.invalidateCoverArtCaches(refreshedBookArtUri)
                
                // Force regenerate palette
                themeStateHolder.forceRegenerateColorScheme(refreshedBookArtUri)
            }

            MetadataEditResult(
                success = true,
                updatedTrack = freshTrack,
                updatedBookArtUri = freshTrack.BookArtUriString,
                parsedTranscript = parsedTranscript
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

    suspend fun deleteTrack(Track: Track): Boolean = withContext(Dispatchers.IO) {
        val fileInfo = FileDeletionUtils.getFileInfo(Track.path)
        if (fileInfo.exists && fileInfo.canWrite) {
            val success = FileDeletionUtils.deleteFile(context, Track.path)
            if (success) {
                // Remove from DB happens in ViewModel call logic or should happen here?
                // VM's deleteFromDevice calls removeTrack -> toggleFavorite(false) -> updates lists.
                // It does NOT explicitly call repository.deleteTrack() because MediaStore/FileObserver handles it?
                // Or maybe explicit deletion IS needed but VM logic (Line 3687) says "removeTrack(Track)".
                // removeTrack(3698) toggles favorites and updates _masterAllTracks. It implies memory update.
                // FileDeletionUtils deletes the physical file. The MediaScanner should eventually pick it up, 
                // but for immediate UI responsiveness, manual update is good.
                // Also, AudiobookRepository.deleteById(id) exists.
                // ViewModel did NOT call AudiobookRepository.deleteById(). It relied on "removeTrack" which is UI state only? 
                // Wait, removeTrack updates UI state. Does it update DB?
                // Line 3698: toggleFavoriteSpecificTrack(Track, true)?? Wait.
                
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
