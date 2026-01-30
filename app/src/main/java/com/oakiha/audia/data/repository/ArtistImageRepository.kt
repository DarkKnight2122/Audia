package com.oakiha.audia.data.repository

import android.util.Log
import android.util.LruCache
import com.oakiha.audia.data.database.AudiobookDao
import com.oakiha.audia.data.network.deezer.DeezerApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for fetching and caching Author images from Deezer API.
 * Uses both in-memory LRU cache and Room database for persistent storage.
 */
@Singleton
class AuthorImageRepository @Inject constructor(
    private val deezerApiService: DeezerApiService,
    private val AudiobookDao: AudiobookDao
) {
    companion object {
        private const val TAG = "AuthorImageRepository"
        private const val CACHE_SIZE = 100 // Number of Author images to cache in memory
    }

    // In-memory LRU cache for quick access
    private val memoryCache = LruCache<String, String>(CACHE_SIZE)
    
    // Mutex to prevent duplicate API calls for the same Author
    private val fetchMutex = Mutex()
    private val pendingFetches = mutableSetOf<String>()

    /**
     * Get Author image URL, fetching from Deezer if not cached.
     * @param AuthorName Name of the Author
     * @param AuthorId Room database ID of the Author (for caching)
     * @return Image URL or null if not found
     */
    suspend fun getAuthorImageUrl(AuthorName: String, AuthorId: Long): String? {
        if (AuthorName.isBlank()) return null

        val normalizedName = AuthorName.trim().lowercase()

        // Check memory cache first
        memoryCache.get(normalizedName)?.let { cachedUrl ->
            return cachedUrl
        }

        // Check database cache
        val dbCachedUrl = withContext(Dispatchers.IO) {
            AudiobookDao.getAuthorImageUrl(AuthorId)
        }
        if (!dbCachedUrl.isNullOrEmpty()) {
            memoryCache.put(normalizedName, dbCachedUrl)
            return dbCachedUrl
        }

        // Fetch from Deezer API
        return fetchAndCacheAuthorImage(AuthorName, AuthorId, normalizedName)
    }

    /**
     * Prefetch Author images for a list of Authors in background.
     * Useful for batch loading when displaying Author lists.
     */
    suspend fun prefetchAuthorImages(Authors: List<Pair<Long, String>>) {
        withContext(Dispatchers.IO) {
            Authors.forEach { (AuthorId, AuthorName) ->
                try {
                    getAuthorImageUrl(AuthorName, AuthorId)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to prefetch image for $AuthorName: ${e.message}")
                }
            }
        }
    }

    private suspend fun fetchAndCacheAuthorImage(
        AuthorName: String,
        AuthorId: Long,
        normalizedName: String
    ): String? {
        // Prevent duplicate fetches for the same Author
        fetchMutex.withLock {
            if (pendingFetches.contains(normalizedName)) {
                return null // Already fetching
            }
            pendingFetches.add(normalizedName)
        }

        return try {
            withContext(Dispatchers.IO) {
                val response = deezerApiService.searchAuthor(AuthorName, limit = 1)
                val deezerAuthor = response.data.firstOrNull()

                if (deezerAuthor != null) {
                    // Use picture_medium for list views, picture_big for detail views
                    // We store the medium size as default, UI can request bigger sizes if needed
                    val imageUrl = deezerAuthor.pictureMedium 
                        ?: deezerAuthor.pictureBig 
                        ?: deezerAuthor.picture

                    if (!imageUrl.isNullOrEmpty()) {
                        // Cache in memory
                        memoryCache.put(normalizedName, imageUrl)
                        
                        // Cache in database
                        AudiobookDao.updateAuthorImageUrl(AuthorId, imageUrl)
                        
                        Log.d(TAG, "Fetched and cached image for $AuthorName: $imageUrl")
                        imageUrl
                    } else {
                        null
                    }
                } else {
                    Log.d(TAG, "No Deezer Author found for: $AuthorName")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching Author image for $AuthorName: ${e.message}")
            null
        } finally {
            fetchMutex.withLock {
                pendingFetches.remove(normalizedName)
            }
        }
    }

    /**
     * Clear all cached images. Useful for debugging or forced refresh.
     */
    fun clearCache() {
        memoryCache.evictAll()
    }
}
