package com.oakiha.audia.data.repository

import com.oakiha.audia.data.model.Lyrics
import com.oakiha.audia.data.model.LyricsSourcePreference
import com.oakiha.audia.data.model.Track

interface LyricsRepository {
    /**
     * Get lyrics for a track with source preference support.
     * 
     * @param track The track to get lyrics for
     * @param sourcePreference The preferred order of sources to try (API, Embedded, Local)
     * @param forceRefresh If true, bypasses in-memory cache
     * @return Lyrics object or null if not found
     */
    suspend fun getLyrics(
        track: Track,
        sourcePreference: LyricsSourcePreference = LyricsSourcePreference.EMBEDDED_FIRST,
        forceRefresh: Boolean = false
    ): Lyrics?
    
    /**
     * Fetch lyrics from remote API and save to database.
     */
    suspend fun fetchFromRemote(track: Track): Result<Pair<Lyrics, String>>
    
    /**
     * Search for lyrics on remote API and return multiple results.
     */
    suspend fun searchRemote(track: Track): Result<Pair<String, List<LyricsSearchResult>>>
  
    /**
     * Search for lyrics on remote API using query title and author, and return multiple results.
     */
    suspend fun searchRemoteByQuery(title: String, author: String? = null): Result<Pair<String, List<LyricsSearchResult>>>
    
    /**
     * Update lyrics for a track in the database.
     */
    suspend fun updateLyrics(trackId: Long, lyricsContent: String)
    
    /**
     * Reset lyrics for a track (remove from database and cache).
     */
    suspend fun resetLyrics(trackId: Long)
    
    /**
     * Reset all lyrics (clear database and cache).
     */
    suspend fun resetAllLyrics()
    
    /**
     * Clear in-memory cache only.
     */
    fun clearCache()

    /**
     * Scans local .lrc files for the provided tracks and updates the database if found.
     * 
     * @param tracks List of tracks to scan for
     * @param onProgress Callback for progress updates (current, total)
     * @return Number of tracks updated
     */
    suspend fun scanAndAssignLocalLrcFiles(
        tracks: List<Track>,
        onProgress: suspend (current: Int, total: Int) -> Unit
    ): Int
}
