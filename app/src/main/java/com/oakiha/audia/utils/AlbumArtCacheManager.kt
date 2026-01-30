package com.oakiha.audia.utils

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Manages Book art cache with LRU eviction policy.
 * 
 * Features:
 * - Configurable max cache size (default 200MB)
 * - LRU eviction based on file lastModified timestamp
 * - Cleanup of orphaned cache files for deleted Tracks
 * - Thread-safe operations
 */
object BookArtCacheManager {
    
    private const val TAG = "BookArtCacheManager"
    
    /**
     * Maximum cache size in bytes (200MB default)
     */
    private const val MAX_CACHE_SIZE_BYTES = 200L * 1024 * 1024
    
    /**
     * Prefix for Book art cache files
     */
    private const val CACHE_PREFIX = "Track_art_"
    
    /**
     * Suffix for "no art" marker files
     */
    private const val NO_ART_SUFFIX = "_no.jpg"
    
    /**
     * Percentage of cache to clean when limit is exceeded (25%)
     */
    private const val CLEANUP_PERCENTAGE = 0.25
    
    /**
     * Mutex to prevent concurrent cleanup operations
     */
    private val cleanupMutex = Mutex()
    
    /**
     * Last cleanup timestamp to prevent too frequent cleanups
     */
    @Volatile
    private var lastCleanupTime = 0L
    
    /**
     * Minimum interval between cleanups (5 minutes)
     */
    private const val MIN_CLEANUP_INTERVAL_MS = 5 * 60 * 1000L
    
    /**
     * Cleans the cache if it exceeds the maximum size.
     * Uses LRU policy to remove the oldest 25% of files.
     * 
     * @param context Application context
     * @return Number of files deleted, or 0 if no cleanup was needed
     */
    suspend fun cleanCacheIfNeeded(context: Context): Int = withContext(Dispatchers.IO) {
        // Skip if cleaned recently
        val now = System.currentTimeMillis()
        if (now - lastCleanupTime < MIN_CLEANUP_INTERVAL_MS) {
            return@withContext 0
        }
        
        cleanupMutex.withLock {
            // Double-check after acquiring lock
            if (now - lastCleanupTime < MIN_CLEANUP_INTERVAL_MS) {
                return@withLock 0
            }
            
            val cacheDir = context.cacheDir
            val artFiles = getBookArtFiles(cacheDir)
            
            if (artFiles.isEmpty()) {
                return@withLock 0
            }
            
            val currentSize = artFiles.sumOf { it.length() }
            
            if (currentSize <= MAX_CACHE_SIZE_BYTES) {
                return@withLock 0
            }
            
            Log.d(TAG, "Cache size ${currentSize / 1024 / 1024}MB exceeds limit, cleaning...")
            
            // Sort by lastModified (oldest first) and delete oldest 25%
            val filesToDelete = artFiles
                .sortedBy { it.lastModified() }
                .take((artFiles.size * CLEANUP_PERCENTAGE).toInt().coerceAtLeast(1))
            
            var deletedCount = 0
            var freedBytes = 0L
            
            for (file in filesToDelete) {
                val size = file.length()
                if (file.delete()) {
                    deletedCount++
                    freedBytes += size
                }
            }
            
            lastCleanupTime = System.currentTimeMillis()
            
            Log.d(TAG, "Cleaned $deletedCount files, freed ${freedBytes / 1024}KB")
            
            deletedCount
        }
    }
    
    /**
     * Cleans orphaned cache files for Tracks that no longer exist.
     * Should be called after sync operations.
     * 
     * @param context Application context
     * @param validTrackIds Set of Track IDs that still exist in the library
     * @return Number of orphaned files deleted
     */
    suspend fun cleanOrphanedCacheFiles(
        context: Context,
        validTrackIds: Set<Long>
    ): Int = withContext(Dispatchers.IO) {
        cleanupMutex.withLock {
            val cacheDir = context.cacheDir
            val allArtFiles = getAllBookArtRelatedFiles(cacheDir)
            
            if (allArtFiles.isEmpty()) {
                return@withLock 0
            }
            
            var deletedCount = 0
            
            for (file in allArtFiles) {
                val TrackId = extractTrackIdFromFilename(file.name)
                if (TrackId != null && TrackId !in validTrackIds) {
                    if (file.delete()) {
                        deletedCount++
                    }
                }
            }
            
            if (deletedCount > 0) {
                Log.d(TAG, "Cleaned $deletedCount orphaned Book art files")
            }
            
            deletedCount
        }
    }
    
    /**
     * Gets the current cache size in bytes.
     * 
     * @param context Application context
     * @return Total size of Book art cache in bytes
     */
    fun getCacheSizeBytes(context: Context): Long {
        return getBookArtFiles(context.cacheDir).sumOf { it.length() }
    }
    
    /**
     * Gets the current cache size in a human-readable format.
     * 
     * @param context Application context
     * @return Cache size as "X.X MB" string
     */
    fun getCacheSizeFormatted(context: Context): String {
        val bytes = getCacheSizeBytes(context)
        val mb = bytes.toDouble() / (1024 * 1024)
        return String.format("%.1f MB", mb)
    }
    
    /**
     * Gets the number of cached Book art files.
     * 
     * @param context Application context
     * @return Number of cached files
     */
    fun getCachedFileCount(context: Context): Int {
        return getBookArtFiles(context.cacheDir).size
    }
    
    /**
     * Clears all Book art cache files.
     * 
     * @param context Application context
     * @return Number of files deleted
     */
    suspend fun clearAllCache(context: Context): Int = withContext(Dispatchers.IO) {
        cleanupMutex.withLock {
            val files = getAllBookArtRelatedFiles(context.cacheDir)
            var deletedCount = 0
            
            for (file in files) {
                if (file.delete()) {
                    deletedCount++
                }
            }
            
            Log.d(TAG, "Cleared all Book art cache: $deletedCount files")
            deletedCount
        }
    }
    
    /**
     * Gets all Book art cache files (excluding "no art" markers).
     */
    private fun getBookArtFiles(cacheDir: File): List<File> {
        return cacheDir.listFiles { file ->
            file.isFile &&
            file.name.startsWith(CACHE_PREFIX) &&
            !file.name.contains(NO_ART_SUFFIX)
        }?.toList() ?: emptyList()
    }
    
    /**
     * Gets all Book art related files (including "no art" markers).
     */
    private fun getAllBookArtRelatedFiles(cacheDir: File): List<File> {
        return cacheDir.listFiles { file ->
            file.isFile && file.name.startsWith(CACHE_PREFIX)
        }?.toList() ?: emptyList()
    }
    
    /**
     * Extracts Track ID from cache filename.
     * Handles formats: "Track_art_123.jpg" and "Track_art_123_no.jpg"
     * 
     * @param filename The filename to parse
     * @return Track ID or null if parsing fails
     */
    private fun extractTrackIdFromFilename(filename: String): Long? {
        return try {
            // Remove prefix "Track_art_"
            val withoutPrefix = filename.removePrefix(CACHE_PREFIX)
            
            // Extract the ID (before any underscore or dot)
            val idPart = withoutPrefix
                .substringBefore("_")
                .substringBefore(".")
            
            idPart.toLongOrNull()
        } catch (e: Exception) {
            null
        }
    }
}
