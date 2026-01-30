package com.oakiha.audia.data.repository

import com.oakiha.audia.data.model.Transcript
import com.oakiha.audia.data.model.TranscriptSourcePreference
import com.oakiha.audia.data.model.Track

interface TranscriptRepository {
    /**
     * Get Transcript for a Track with source preference support.
     * 
     * @param Track The Track to get Transcript for
     * @param sourcePreference The preferred order of sources to try (API, Embedded, Local)
     * @param forceRefresh If true, bypasses in-memory cache
     * @return Transcript object or null if not found
     */
    suspend fun getTranscript(
        Track: Track,
        sourcePreference: TranscriptSourcePreference = TranscriptSourcePreference.EMBEDDED_FIRST,
        forceRefresh: Boolean = false
    ): Transcript?
    
    /**
     * Fetch Transcript from remote API and save to database.
     */
    suspend fun fetchFromRemote(Track: Track): Result<Pair<Transcript, String>>
    
    /**
     * Search for Transcript on remote API and return multiple results.
     */
    suspend fun searchRemote(Track: Track): Result<Pair<String, List<TranscriptSearchResult>>>
  
    /**
     * Search for Transcript on remote API using query title and Author, and return multiple results.
     */
    suspend fun searchRemoteByQuery(title: String, Author: String? = null): Result<Pair<String, List<TranscriptSearchResult>>>
    
    /**
     * Update Transcript for a Track in the database.
     */
    suspend fun updateTranscript(TrackId: Long, TranscriptContent: String)
    
    /**
     * Reset Transcript for a Track (remove from database and cache).
     */
    suspend fun resetTranscript(TrackId: Long)
    
    /**
     * Reset all Transcript (clear database and cache).
     */
    suspend fun resetAllTranscript()
    
    /**
     * Clear in-memory cache only.
     */
    fun clearCache()

    /**
     * Scans local .lrc files for the provided Tracks and updates the database if found.
     * 
     * @param Tracks List of Tracks to scan for
     * @param onProgress Callback for progress updates (current, total)
     * @return Number of Tracks updated
     */
    suspend fun scanAndAssignLocalLrcFiles(
        Tracks: List<Track>,
        onProgress: suspend (current: Int, total: Int) -> Unit
    ): Int
}
