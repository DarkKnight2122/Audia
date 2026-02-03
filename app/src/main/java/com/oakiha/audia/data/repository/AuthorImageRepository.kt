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
 * Repository for fetching and caching author images from Deezer API.
 * Uses both in-memory LRU cache and Room database for persistent storage.
 */
@Singleton
class AuthorImageRepository @Inject constructor(
    private val deezerApiService: DeezerApiService,
    private val audiobookDao: AudiobookDao
) {
    companion object {
        private const val TAG = "ArtistImageRepository"
        private const val CACHE_SIZE = 100 // Number of author images to cache in memory
    }

    // In-memory LRU cache for quick access
    private val memoryCache = LruCache<String, String>(CACHE_SIZE)
    
    // Mutex to prevent duplicate API calls for the same author
    private val fetchMutex = Mutex()
    private val pendingFetches = mutableSetOf<String>()

    /**
     * Get author image URL, fetching from Deezer if not cached.
     * @param authorName Name of the author
     * @param authorId Room database ID of the author (for caching)
     * @return Image URL or null if not found
     */
    suspend fun getAuthorImageUrl(authorName: String, authorId: Long): String? {
        if (authorName.isBlank()) return null

        val normalizedName = authorName.trim().lowercase()

        // Check memory cache first
        memoryCache.get(normalizedName)?.let { cachedUrl ->
            return cachedUrl
        }

        // Check database cache
        val dbCachedUrl = withContext(Dispatchers.IO) {
            audiobookDao.getAuthorImageUrl(authorId)
        }
        if (!dbCachedUrl.isNullOrEmpty()) {
            memoryCache.put(normalizedName, dbCachedUrl)
            return dbCachedUrl
        }

        // Fetch from Deezer API
        return fetchAndCacheArtistImage(authorName, authorId, normalizedName)
    }

    /**
     * Prefetch author images for a list of authors in background.
     * Useful for batch loading when displaying author lists.
     */
    suspend fun prefetchArtistImages(authors: List<Pair<Long, String>>) {
        withContext(Dispatchers.IO) {
            authors.forEach { (authorId, authorName) ->
                try {
                    getAuthorImageUrl(authorName, authorId)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to prefetch image for $authorName: ${e.message}")
                }
            }
        }
    }

    private suspend fun fetchAndCacheArtistImage(
        authorName: String,
        authorId: Long,
        normalizedName: String
    ): String? {
        // Prevent duplicate fetches for the same author
        fetchMutex.withLock {
            if (pendingFetches.contains(normalizedName)) {
                return null // Already fetching
            }
            pendingFetches.add(normalizedName)
        }

        return try {
            withContext(Dispatchers.IO) {
                val response = deezerApiService.searchArtist(authorName, limit = 1)
                val deezerArtist = response.data.firstOrNull()

                if (deezerArtist != null) {
                    // Use picture_medium for list views, picture_big for detail views
                    // We store the medium size as default, UI can request bigger sizes if needed
                    val imageUrl = deezerArtist.pictureMedium 
                        ?: deezerArtist.pictureBig 
                        ?: deezerArtist.picture

                    if (!imageUrl.isNullOrEmpty()) {
                        // Cache in memory
                        memoryCache.put(normalizedName, imageUrl)
                        
                        // Cache in database
                        audiobookDao.updateAuthorImageUrl(authorId, imageUrl)
                        
                        Log.d(TAG, "Fetched and cached image for $authorName: $imageUrl")
                        imageUrl
                    } else {
                        null
                    }
                } else {
                    Log.d(TAG, "No Deezer author found for: $authorName")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching author image for $authorName: ${e.message}")
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
