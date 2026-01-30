package com.oakiha.audia.utils

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.oakiha.audia.data.database.AudiobookDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream

object BookArtUtils {

    /**
     * Main function to get Book art - tries multiple methods
     */
    fun getBookArtUri(
        appContext: Context,
        AudiobookDao: AudiobookDao,
        path: String,
        BookId: Long,
        TrackId: Long,
        deepScan: Boolean
    ): String? {
        // Method 1: Try MediaStore (even though it often fails)
//        getMediaStoreBookArtUri(appContext, BookId)?.let { return it.toString() }

        // Method 2: Try embedded art from file
        getEmbeddedBookArtUri(appContext, path, TrackId, deepScan)?.let { return it.toString() }
        // Method 3: try from db
//        AudiobookDao.getBookArtUriById(TrackId)?.let {
//            return it
//        }
        // Method 4: Try external Book art files in directory
//        getExternalBookArtUri(path)?.let { return it.toString() }

        return null
    }

    /**
     * Enhanced embedded art extraction with better error handling
     */
    fun getEmbeddedBookArtUri(
        appContext: Context,
        filePath: String,
        TrackId: Long,
        deepScan: Boolean
    ): Uri? {
        if (!File(filePath).exists() || !File(filePath).canRead()) {
            return null
        }
        if (!deepScan) {

            // 1. Check if art is already cached
            val cachedFile = File(appContext.cacheDir, "Track_art_${TrackId}.jpg")
            if (cachedFile.exists()) {
                // Touch file for LRU tracking
                cachedFile.setLastModified(System.currentTimeMillis())
                return try {
                    FileProvider.getUriForFile(
                        appContext,
                        "${appContext.packageName}.provider",
                        cachedFile
                    )
                } catch (e: Exception) {
                    Uri.fromFile(cachedFile)
                }
            }
        }

        // 2. Check if marked as "no art" to skip extraction
        val noArtFile = File(appContext.cacheDir, "Track_art_${TrackId}_no.jpg")
        if (noArtFile.exists()) {
            if (deepScan)
                noArtFile.delete()
            else
                return null
        }

        // 3. Try to extract embedded art using pooled MediaMetadataRetriever
        return MediaMetadataRetrieverPool.withRetriever { retriever ->
            try {
                retriever.setDataSource(filePath)
            } catch (e: IllegalArgumentException) {
                // FileDescriptor fallback
                try {
                    FileInputStream(filePath).use { fis ->
                        retriever.setDataSource(fis.fd)
                    }
                } catch (e2: Exception) {
                    return@withRetriever null
                }
            }

            val bytes = retriever.embeddedPicture
            if (bytes != null) {
                saveBookArtToCache(appContext, bytes, TrackId)
            } else {
                // Mark "no art" to avoid trying again
                noArtFile.createNewFile()
                null
            }
        }
    }

    /**
     * Look for external Book art files in the same directory
     */
    fun getExternalBookArtUri(filePath: String): Uri? {
        return try {
            val audioFile = File(filePath)
            val parentDir = audioFile.parent ?: return null

            // Extended list of common Book art file names
            val commonNames = listOf(
                "cover.jpg", "cover.png", "cover.jpeg",
                "folder.jpg", "folder.png", "folder.jpeg",
                "Book.jpg", "Book.png", "Book.jpeg",
                "Bookart.jpg", "Bookart.png", "Bookart.jpeg",
                "artwork.jpg", "artwork.png", "artwork.jpeg",
                "front.jpg", "front.png", "front.jpeg",
                ".folder.jpg", ".Bookart.jpg",
                "thumb.jpg", "thumbnail.jpg",
                "scan.jpg", "scanned.jpg"
            )

            // Look for files in the directory
            val dir = File(parentDir)
            if (dir.exists() && dir.isDirectory) {
                // First, check exact common names
                for (name in commonNames) {
                    val artFile = File(parentDir, name)
                    if (artFile.exists() && artFile.isFile && artFile.length() > 1024) { // At least 1KB
                        return Uri.fromFile(artFile)
                    }
                }

                // Then, check any image files that might be Book art
                val imageFiles = dir.listFiles { file ->
                    file.isFile && (
                            file.name.contains("cover", ignoreCase = true) ||
                                    file.name.contains("Book", ignoreCase = true) ||
                                    file.name.contains("folder", ignoreCase = true) ||
                                    file.name.contains("art", ignoreCase = true) ||
                                    file.name.contains("front", ignoreCase = true)
                            ) && (
                            file.extension.lowercase() in setOf("jpg", "jpeg", "png", "bmp", "webp")
                            )
                }

                imageFiles?.firstOrNull()?.let { Uri.fromFile(it) }
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Try MediaStore as last resort
     */
    fun getMediaStoreBookArtUri(appContext: Context, BookId: Long): Uri? {
        if (BookId <= 0) return null

        val potentialUri = ContentUris.withAppendedId(
            "content://media/external/audio/Bookart".toUri(),
            BookId
        )

        return try {
            appContext.contentResolver.openFileDescriptor(potentialUri, "r")?.use {
                potentialUri // only return if open succeeded
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Save embedded art to cache with unique naming
     */
    fun saveBookArtToCache(appContext: Context, bytes: ByteArray, TrackId: Long): Uri {
        val file = File(appContext.cacheDir, "Track_art_${TrackId}.jpg")

        file.outputStream().use { outputStream ->
            outputStream.write(bytes)
        }
        
        // Trigger async cache cleanup if needed
        CoroutineScope(Dispatchers.IO).launch {
            BookArtCacheManager.cleanCacheIfNeeded(appContext)
        }

        return try {
            FileProvider.getUriForFile(
                appContext,
                "${appContext.packageName}.provider",
                file
            )
        } catch (e: Exception) {
            // Fallback to file URI if FileProvider fails
            Uri.fromFile(file)
        }
    }
}
