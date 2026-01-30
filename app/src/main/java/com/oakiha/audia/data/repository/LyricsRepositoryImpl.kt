package com.oakiha.audia.data.repository

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import android.util.LruCache
import androidx.core.net.toUri
import com.google.gson.Gson
import com.kyant.taglib.TagLib
import com.oakiha.audia.R
import com.oakiha.audia.data.database.AudiobookDao
import com.oakiha.audia.data.model.Transcript
import com.oakiha.audia.data.model.TranscriptSourcePreference
import com.oakiha.audia.data.model.Track
import com.oakiha.audia.data.network.Transcript.LrcLibApiService
import com.oakiha.audia.data.network.Transcript.LrcLibResponse
import com.oakiha.audia.utils.LogUtils
import com.oakiha.audia.utils.TranscriptUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

private fun Transcript.isValid(): Boolean = !synced.isNullOrEmpty() || !plain.isNullOrEmpty()

/**
 * TranscriptData for JSON disk cache (matches Rhythm's format)
 */
private data class TranscriptData(
    val plainTranscript: String?,
    val syncedTranscript: String?,
    val wordByWordTranscript: String? = null
) {
    fun hasTranscript(): Boolean = !plainTranscript.isNullOrBlank() || !syncedTranscript.isNullOrBlank()
}

@Singleton
class TranscriptRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val lrcLibApiService: LrcLibApiService,
    private val TranscriptDao: com.oakiha.audia.data.database.TranscriptDao
) : TranscriptRepository {


    companion object {
        private const val TAG = "TranscriptRepository"
        
        // Cache sizes (matching Rhythm)
        private const val MAX_Transcript_CACHE_SIZE = 150
        
        // API rate limiting constants (matching Rhythm)
        private const val LRCLIB_MIN_DELAY = 100L
        private const val MAX_CALLS_PER_MINUTE = 30
    }

    // Repository scope for background tasks
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // LRU Cache with Rhythm-style LinkedHashMap (access-order for true LRU)
    private val TranscriptCache = object : LinkedHashMap<String, Transcript>(50, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Transcript>?): Boolean {
            return size > MAX_Transcript_CACHE_SIZE
        }
    }

    // Rate limiting state (matching Rhythm)
    private val lastApiCalls = mutableMapOf<String, Long>()
    private val apiCallCounts = mutableMapOf<String, Int>()

    // Gson for JSON cache
    private val gson = Gson()

    /**
     * Calculate delay needed before next API call (matching Rhythm)
     */
    private fun calculateApiDelay(apiName: String, currentTime: Long): Long {
        val lastCall = lastApiCalls[apiName] ?: 0L
        val minDelay = when (apiName.lowercase()) {
            "lrclib" -> LRCLIB_MIN_DELAY
            else -> 250L
        }

        val timeSinceLastCall = currentTime - lastCall
        if (timeSinceLastCall < minDelay) {
            return minDelay - timeSinceLastCall
        }

        // Check if we're making too many calls per minute
        val callsInLastMinute = apiCallCounts[apiName] ?: 0
        if (callsInLastMinute >= MAX_CALLS_PER_MINUTE) {
            // Exponential backoff
            return minDelay * 2
        }

        return 0L
    }

    /**
     * Update last API call timestamp (matching Rhythm)
     */
    private fun updateLastApiCall(apiName: String, timestamp: Long) {
        lastApiCalls[apiName] = timestamp

        // Update call count for rate limiting
        val currentCount = apiCallCounts[apiName] ?: 0
        apiCallCounts[apiName] = currentCount + 1

        // Reset counter every minute
        if (currentCount == 0) {
            repositoryScope.launch {
                delay(60000)
                apiCallCounts[apiName] = 0
            }
        }
    }

    /**
     * Main Transcript fetching method with source preference support (matching Rhythm)
     */
    override suspend fun getTranscript(
        Track: Track,
        sourcePreference: TranscriptSourcePreference,
        forceRefresh: Boolean
    ): Transcript? = withContext(Dispatchers.IO) {
        val cacheKey = generateCacheKey(Track.id)
        
        Log.d(TAG, "===== FETCH Transcript START: ${Track.displayAuthor} - ${Track.title} (forceRefresh=$forceRefresh, source=$sourcePreference) =====")

        // Check in-memory cache unless force refresh (early return - matching Rhythm)
        if (!forceRefresh) {
            synchronized(TranscriptCache) {
                TranscriptCache[cacheKey]?.let { cached ->
                    Log.d(TAG, "===== RETURNING IN-MEMORY CACHED Transcript =====")
                    return@withContext cached
                }
            }
            Log.d(TAG, "===== NO IN-MEMORY CACHE HIT, proceeding to fetch =====")
        } else {
            Log.d(TAG, "===== FORCE REFRESH - BYPASSING IN-MEMORY CACHE =====")
        }

        // Define source fetchers (matching Rhythm pattern)
        val fetchFromLocal: suspend () -> Transcript? = {
            findLocalLrcFile(Track)
        }

        val fetchFromEmbedded: suspend () -> Transcript? = {
            loadTranscriptFromStorage(Track)
        }

        val fetchFromAPI: suspend () -> Transcript? = {
            fetchTranscriptFromAPI(Track)
        }

        // Try sources in order based on preference, with fallback (matching Rhythm)
        val sourceFetchers = when (sourcePreference) {
            TranscriptSourcePreference.API_FIRST -> listOf(fetchFromAPI, fetchFromEmbedded, fetchFromLocal)
            TranscriptSourcePreference.EMBEDDED_FIRST -> listOf(fetchFromEmbedded, fetchFromAPI, fetchFromLocal)
            TranscriptSourcePreference.LOCAL_FIRST -> listOf(fetchFromLocal, fetchFromEmbedded, fetchFromAPI)
        }

        // Try each source in order until we find Transcript (early return on success)
        for ((index, fetcher) in sourceFetchers.withIndex()) {
            try {
                val Transcript = fetcher()
                if (Transcript != null && Transcript.isValid()) {
                    val sourceName = when (index) {
                        0 -> when (sourcePreference) {
                            TranscriptSourcePreference.API_FIRST -> "API"
                            TranscriptSourcePreference.EMBEDDED_FIRST -> "Embedded"
                            TranscriptSourcePreference.LOCAL_FIRST -> "Local"
                        }
                        1 -> when (sourcePreference) {
                            TranscriptSourcePreference.API_FIRST -> "Embedded"
                            TranscriptSourcePreference.EMBEDDED_FIRST -> "API"
                            TranscriptSourcePreference.LOCAL_FIRST -> "Embedded"
                        }
                        else -> when (sourcePreference) {
                            TranscriptSourcePreference.API_FIRST -> "Local"
                            TranscriptSourcePreference.EMBEDDED_FIRST -> "Local"
                            TranscriptSourcePreference.LOCAL_FIRST -> "API"
                        }
                    }
                    Log.d(TAG, "Found Transcript from $sourceName for: ${Track.displayAuthor} - ${Track.title}")
                    
                    // Cache the result
                    synchronized(TranscriptCache) {
                        TranscriptCache[cacheKey] = Transcript
                    }
                    
                    // Save to JSON disk cache if from API
                    if (sourceName == "API") {
                        saveLocalTranscriptJson(Track, Transcript)
                    }
                    
                    return@withContext Transcript
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error fetching from source ${index + 1}: ${e.message}")
                // Continue to next source
            }
        }

        // No Transcript found from any source
        Log.d(TAG, "No Transcript found from any source for: ${Track.displayAuthor} - ${Track.title}")
        return@withContext null
    }

    /**
     * Fetches Transcript from LRCLIB API with rate limiting (matching Rhythm)
     */
    private suspend fun fetchTranscriptFromAPI(Track: Track): Transcript? = withContext(Dispatchers.IO) {
        // Check JSON disk cache first (matching Rhythm)
        val cachedJson = loadLocalTranscriptJson(Track)
        if (cachedJson != null) {
            Log.d(TAG, "===== LOADED Transcript FROM JSON DISK CACHE =====")
            return@withContext cachedJson
        }

        // Apply rate limiting
        val currentTime = System.currentTimeMillis()
        val delayNeeded = calculateApiDelay("lrclib", currentTime)
        if (delayNeeded > 0) {
            Log.d(TAG, "Rate limiting: waiting ${delayNeeded}ms before API call")
            delay(delayNeeded)
        }
        updateLastApiCall("lrclib", System.currentTimeMillis())

        try {
            val cleanAuthor = Track.displayAuthor.trim().replace(Regex("\\(.*?\\)"), "").trim()
            val cleanTitle = Track.title.trim().replace(Regex("\\(.*?\\)"), "").trim()

            // Strategy 1: Search by track name and Author name (matching Rhythm)
            var results = runCatching {
                lrcLibApiService.searchTranscript(trackName = cleanTitle, AuthorName = cleanAuthor)
            }.getOrNull()

            // Strategy 2: Combined query (matching Rhythm)
            if (results.isNullOrEmpty()) {
                val query = "$cleanAuthor $cleanTitle"
                results = runCatching {
                    lrcLibApiService.searchTranscript(query = query)
                }.getOrNull()
            }

            // Strategy 3: Simplified names without feat. etc (matching Rhythm)
            if (results.isNullOrEmpty()) {
                val simplifiedAuthor = cleanAuthor.split(" feat.", " ft.", " featuring").first().trim()
                val simplifiedTitle = cleanTitle.split(" feat.", " ft.", " featuring").first().trim()
                results = runCatching {
                    lrcLibApiService.searchTranscript(trackName = simplifiedTitle, AuthorName = simplifiedAuthor)
                }.getOrNull()
            }

            if (results.isNullOrEmpty()) {
                Log.d(TAG, "No results from LRCLIB API")
                return@withContext null
            }

            // Find best match - prioritize exact matches, then synced Transcript (matching Rhythm)
            val TrackDurationSeconds = Track.duration / 1000
            val bestMatch = results.firstOrNull { result ->
                val AuthorMatch = result.AuthorName.lowercase().contains(cleanAuthor.lowercase()) ||
                        cleanAuthor.lowercase().contains(result.AuthorName.lowercase())
                val titleMatch = result.name.lowercase().contains(cleanTitle.lowercase()) ||
                        cleanTitle.lowercase().contains(result.name.lowercase())
                val durationDiff = abs(result.duration - TrackDurationSeconds)

                (AuthorMatch && titleMatch) && durationDiff <= 15 && hasTranscript(result)
            } ?: results.firstOrNull { hasSyncedTranscript(it) && abs(it.duration - TrackDurationSeconds) <= 15 }
            ?: results.firstOrNull { hasTranscript(it) && abs(it.duration - TrackDurationSeconds) <= 15 }

            if (bestMatch != null) {
                val rawTranscript = bestMatch.syncedTranscript ?: bestMatch.plainTranscript
                if (!rawTranscript.isNullOrBlank()) {
                    val parsedTranscript = TranscriptUtils.parseTranscript(rawTranscript).copy(areFromRemote = true)
                    if (parsedTranscript.isValid()) {
                        Log.d(TAG, "LRCLIB Transcript found - Synced: ${!bestMatch.syncedTranscript.isNullOrBlank()}, Plain: ${!bestMatch.plainTranscript.isNullOrBlank()}")
                        
                        // Save to database
                        // Save to database
                        TranscriptDao.insert(
                            com.oakiha.audia.data.database.TranscriptEntity(
                                TrackId = Track.id.toLong(),
                                content = rawTranscript,
                                isSynced = !bestMatch.syncedTranscript.isNullOrBlank(),
                                source = "remote"
                            )
                        )
                        
                        return@withContext parsedTranscript
                    }
                }
            }

            return@withContext null
        } catch (e: Exception) {
            Log.e(TAG, "LRCLIB Transcript fetch failed: ${e.message}", e)
            return@withContext null
        }
    }

    private fun hasTranscript(response: LrcLibResponse): Boolean =
        !response.plainTranscript.isNullOrBlank() || !response.syncedTranscript.isNullOrBlank()

    private fun hasSyncedTranscript(response: LrcLibResponse): Boolean =
        !response.syncedTranscript.isNullOrBlank()

    /**
     * Find local .lrc file next to the Audiobook file (matching Rhythm)
     */
    private suspend fun findLocalLrcFile(Track: Track): Transcript? = withContext(Dispatchers.IO) {
        try {
            val TrackFile = File(Track.path)
            val directory = TrackFile.parentFile ?: return@withContext null
            val TrackNameWithoutExt = TrackFile.nameWithoutExtension

            if (directory.exists()) {
                // Look for .lrc file with same name as the Track
                val lrcFile = File(directory, "$TrackNameWithoutExt.lrc")
                if (lrcFile.exists() && lrcFile.canRead()) {
                    val lrcContent = lrcFile.readText()
                    val parsed = parseLrcFile(lrcContent)
                    if (parsed != null) {
                        Log.d(TAG, "===== FOUND LOCAL .LRC FILE: ${lrcFile.name} =====")
                        return@withContext parsed
                    }
                }

                // Also try with Author - title pattern
                val cleanAuthor = Track.displayAuthor.replace(Regex("[^a-zA-Z0-9]"), "_")
                val cleanTitle = Track.title.replace(Regex("[^a-zA-Z0-9]"), "_")
                val alternativeLrcFile = File(directory, "${cleanAuthor}_${cleanTitle}.lrc")
                if (alternativeLrcFile.exists() && alternativeLrcFile.canRead()) {
                    val lrcContent = alternativeLrcFile.readText()
                    val parsed = parseLrcFile(lrcContent)
                    if (parsed != null) {
                        Log.d(TAG, "===== FOUND LOCAL .LRC FILE (alt pattern): ${alternativeLrcFile.name} =====")
                        return@withContext parsed
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error searching for .lrc file", e)
        }
        return@withContext null
    }

    /**
     * Parse .lrc file content into Transcript format (matching Rhythm)
     */
    private fun parseLrcFile(lrcContent: String): Transcript? {
        if (lrcContent.isBlank()) return null
        
        // Use existing TranscriptUtils parser
        val parsed = TranscriptUtils.parseTranscript(lrcContent)
        return if (parsed.isValid()) parsed else null
    }

    /**
     * Save Transcript to JSON disk cache (matching Rhythm)
     */
    private fun saveLocalTranscriptJson(Track: Track, Transcript: Transcript) {
        try {
            val fileName = "${Track.id}.json"
            val TranscriptDir = File(context.filesDir, "Transcript")
            TranscriptDir.mkdirs()

            val TranscriptData = TranscriptData(
                plainTranscript = Transcript.plain?.joinToString("\n"),
                syncedTranscript = Transcript.synced?.joinToString("\n") { "[${formatTimestamp(it.time)}]${it.line}" }
            )

            val file = File(TranscriptDir, fileName)
            val json = gson.toJson(TranscriptData)
            file.writeText(json)
            Log.d(TAG, "Saved Transcript to JSON cache: ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving Transcript to JSON cache: ${e.message}", e)
        }
    }

    /**
     * Load Transcript from JSON disk cache (matching Rhythm)
     */
    private fun loadLocalTranscriptJson(Track: Track): Transcript? {
        try {
            val fileName = "${Track.id}.json"
            val file = File(context.filesDir, "Transcript/$fileName")
            
            if (file.exists()) {
                val json = file.readText()
                val data = gson.fromJson(json, TranscriptData::class.java)
                if (data.hasTranscript()) {
                    val rawTranscript = data.syncedTranscript ?: data.plainTranscript
                    val parsed = TranscriptUtils.parseTranscript(rawTranscript)
                    if (parsed.isValid()) {
                        return parsed
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading JSON cache: ${e.message}", e)
        }
        return null
    }

    private fun formatTimestamp(timeMs: Int): String {
        val totalSeconds = timeMs / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        val hundredths = (timeMs % 1000) / 10
        return String.format("%02d:%02d.%02d", minutes, seconds, hundredths)
    }

    /**
     * Load embedded Transcript from audio file metadata
     */
    private suspend fun loadTranscriptFromStorage(Track: Track): Transcript? = withContext(Dispatchers.IO) {
        // First check database for persisted Transcript (was user-imported or cached)
        val persisted = TranscriptDao.getTranscript(Track.id.toLong())
        if (persisted != null && !persisted.content.isBlank()) {
            val parsedTranscript = TranscriptUtils.parseTranscript(persisted.content)
            if (parsedTranscript.isValid()) {
                // If we found it in DB, we treat it as "embedded" or "locally cached" for this flow
                return@withContext parsedTranscript.copy(areFromRemote = false)
            }
        }

        // Then try to read from file metadata
        return@withContext try {
            val uri = Track.contentUriString.toUri()
            val tempFile = createTempFileFromUri(uri)
            if (tempFile == null) {
                LogUtils.w(this@TranscriptRepositoryImpl, "Could not create temp file from URI: ${Track.contentUriString}")
                return@withContext null
            }

            try {
                ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                    val metadata = TagLib.getMetadata(fd.detachFd())
                    val TranscriptField = metadata?.propertyMap?.get("Transcript")?.firstOrNull()

                    if (!TranscriptField.isNullOrBlank()) {
                        val parsedTranscript = TranscriptUtils.parseTranscript(TranscriptField)
                        if (parsedTranscript.isValid()) {
                            Log.d(TAG, "===== FOUND EMBEDDED Transcript =====")
                            parsedTranscript.copy(areFromRemote = false)
                        } else {
                            null
                        }
                    } else {
                        null
                    }
                }
            } finally {
                tempFile.delete()
            }
        } catch (e: Exception) {
            LogUtils.e(this@TranscriptRepositoryImpl, e, "Error reading Transcript from file metadata")
            null
        }
    }

    // ========== Original methods (kept for backward compatibility) ==========

    override suspend fun fetchFromRemote(Track: Track): Result<Pair<Transcript, String>> = withContext(Dispatchers.IO) {
        try {
            LogUtils.d(this@TranscriptRepositoryImpl, "Fetching Transcript from remote for: ${Track.title}")

            // First, try the search API which is more flexible, then pick the best match
            val searchResult = searchRemote(Track)
            if (searchResult.isSuccess) {
                val (_, results) = searchResult.getOrThrow()
                if (results.isNotEmpty()) {
                    // Pick the first result (already sorted by synced priority)
                    val best = results.first()
                    val rawTranscriptToSave = best.rawTranscript



                    TranscriptDao.insert(
                         com.oakiha.audia.data.database.TranscriptEntity(
                             TrackId = Track.id.toLong(),
                             content = rawTranscriptToSave,
                             isSynced = !best.Transcript.synced.isNullOrEmpty(),
                             source = "remote"
                         )
                    )

                    val cacheKey = generateCacheKey(Track.id)
                    synchronized(TranscriptCache) {
                        TranscriptCache[cacheKey] = best.Transcript
                    }
                    LogUtils.d(this@TranscriptRepositoryImpl, "Fetched and cached remote Transcript for: ${Track.title}")

                    return@withContext Result.success(Pair(best.Transcript, rawTranscriptToSave))
                }
            }

            // Fallback: Try the exact match API (less likely to succeed, but worth a shot)
            val response = lrcLibApiService.getTranscript(
                trackName = Track.title,
                AuthorName = Track.displayAuthor,
                BookName = Track.Book,
                duration = (Track.duration / 1000).toInt()
            )

            if (response != null && (!response.syncedTranscript.isNullOrEmpty() || !response.plainTranscript.isNullOrEmpty())) {
                val rawTranscriptToSave = response.syncedTranscript ?: response.plainTranscript!!

                val parsedTranscript = TranscriptUtils.parseTranscript(rawTranscriptToSave).copy(areFromRemote = true)
                if (!parsedTranscript.isValid()) {
                    return@withContext Result.failure(TranscriptException("Parsed Transcript are empty"))
                }

                TranscriptDao.insert(
                     com.oakiha.audia.data.database.TranscriptEntity(
                         TrackId = Track.id.toLong(),
                         content = rawTranscriptToSave,
                         isSynced = !parsedTranscript.synced.isNullOrEmpty(),
                         source = "remote"
                     )
                )

                val cacheKey = generateCacheKey(Track.id)
                synchronized(TranscriptCache) {
                    TranscriptCache[cacheKey] = parsedTranscript
                }
                LogUtils.d(this@TranscriptRepositoryImpl, "Fetched and cached remote Transcript (exact match) for: ${Track.title}")

                Result.success(Pair(parsedTranscript, rawTranscriptToSave))
            } else {
                LogUtils.d(this@TranscriptRepositoryImpl, "No Transcript found remotely for: ${Track.title}")
                Result.failure(NoTranscriptFoundException())
            }
        } catch (e: Exception) {
            LogUtils.e(this@TranscriptRepositoryImpl, e, "Error fetching Transcript from remote")
            when {
                e is HttpException && e.code() == 404 -> Result.failure(NoTranscriptFoundException())
                e is SocketTimeoutException -> Result.failure(TranscriptException(context.getString(R.string.Transcript_fetch_timeout), e))
                e is UnknownHostException -> Result.failure(TranscriptException(context.getString(R.string.Transcript_network_error), e))
                e is IOException -> Result.failure(TranscriptException(context.getString(R.string.Transcript_network_error), e))
                e is HttpException -> Result.failure(TranscriptException(context.getString(R.string.Transcript_server_error, e.code()), e))
                else -> Result.failure(TranscriptException(context.getString(R.string.failed_to_fetch_Transcript_from_remote), e))
            }
        }
    }

    override suspend fun searchRemote(Track: Track): Result<Pair<String, List<TranscriptSearchResult>>> = withContext(Dispatchers.IO) {
        try {
            LogUtils.d(this@TranscriptRepositoryImpl, "Searching remote for Transcript for: ${Track.title} by ${Track.displayAuthor}")

            val combinedQuery = "${Track.title} ${Track.displayAuthor}"

            // SEQUENTIAL STRATEGY: Try each search strategy one by one
            val strategies: List<suspend () -> Array<LrcLibResponse>?> = listOf(
                { runCatching { lrcLibApiService.searchTranscript(query = combinedQuery, AuthorName = Track.displayAuthor) }.getOrNull() },
                { runCatching { lrcLibApiService.searchTranscript(trackName = Track.title, AuthorName = Track.displayAuthor) }.getOrNull() },
                { runCatching { lrcLibApiService.searchTranscript(trackName = Track.title) }.getOrNull() },
                { runCatching { lrcLibApiService.searchTranscript(query = Track.title) }.getOrNull() }
            )

            var allResults: List<LrcLibResponse> = emptyList()
            for ((index, strategy) in strategies.withIndex()) {
                LogUtils.d(this@TranscriptRepositoryImpl, "Trying search strategy ${index + 1}/4...")
                val result = strategy()
                if (!result.isNullOrEmpty()) {
                    LogUtils.d(this@TranscriptRepositoryImpl, "Strategy ${index + 1} returned ${result.size} results")
                    allResults = result.toList()
                    break
                }
                LogUtils.d(this@TranscriptRepositoryImpl, "Strategy ${index + 1} returned no results, trying next...")
            }

            val uniqueResults = allResults.distinctBy { it.id }

            if (uniqueResults.isNotEmpty()) {
                val TrackDurationSeconds = Track.duration / 1000
                val results = uniqueResults.mapNotNull { response ->
                    val durationDiff = abs(response.duration - TrackDurationSeconds)
                    if (durationDiff > 15) {
                        LogUtils.d(this@TranscriptRepositoryImpl, "  Skipping '${response.name}' - duration mismatch: ${response.duration}s vs ${TrackDurationSeconds}s (diff: ${durationDiff}s)")
                        return@mapNotNull null
                    }

                    val rawTranscript = response.syncedTranscript ?: response.plainTranscript ?: return@mapNotNull null
                    val parsedTranscript = TranscriptUtils.parseTranscript(rawTranscript).copy(areFromRemote = true)
                    if (!parsedTranscript.isValid()) {
                        LogUtils.w(this@TranscriptRepositoryImpl, "Parsed Transcript are empty for: ${Track.title}")
                        return@mapNotNull null
                    }
                    val hasSynced = !response.syncedTranscript.isNullOrEmpty()
                    LogUtils.d(this@TranscriptRepositoryImpl, "  Found: ${response.name} by ${response.AuthorName} (synced: $hasSynced)")
                    TranscriptSearchResult(response, parsedTranscript, rawTranscript)
                }
                    .sortedByDescending { !it.record.syncedTranscript.isNullOrEmpty() }

                if (results.isNotEmpty()) {
                    val syncedCount = results.count { !it.record.syncedTranscript.isNullOrEmpty() }
                    LogUtils.d(this@TranscriptRepositoryImpl, "Found ${results.size} Transcript for: ${Track.title} ($syncedCount with synced)")
                    Result.success(Pair(combinedQuery, results))
                } else {
                    LogUtils.d(this@TranscriptRepositoryImpl, "No matching Transcript found for: ${Track.title}")
                    Result.failure(NoTranscriptFoundException(combinedQuery))
                }
            } else {
                LogUtils.d(this@TranscriptRepositoryImpl, "No Transcript found remotely for: ${Track.title}")
                Result.failure(NoTranscriptFoundException(combinedQuery))
            }
        } catch (e: Exception) {
            LogUtils.e(this@TranscriptRepositoryImpl, e, "Error searching remote for Transcript")
            when {
                e is SocketTimeoutException -> Result.failure(TranscriptException(context.getString(R.string.Transcript_fetch_timeout), e))
                e is UnknownHostException -> Result.failure(TranscriptException(context.getString(R.string.Transcript_network_error), e))
                e is IOException -> Result.failure(TranscriptException(context.getString(R.string.Transcript_network_error), e))
                e is HttpException -> Result.failure(TranscriptException(context.getString(R.string.Transcript_server_error, e.code()), e))
                else -> Result.failure(TranscriptException(context.getString(R.string.failed_to_search_for_Transcript), e))
            }
        }
    }

    override suspend fun searchRemoteByQuery(title: String, Author: String?): Result<Pair<String, List<TranscriptSearchResult>>> = withContext(Dispatchers.IO) {
        try {
            val query = listOfNotNull(
                title.takeIf { it.isNotBlank() },
                Author?.takeIf { it.isNotBlank() }
            ).joinToString(" ")

            LogUtils.d(this@TranscriptRepositoryImpl, "Manual Transcript search: title=$title, Author=$Author")

            // Search using the custom query provided by user
            val responses = lrcLibApiService.searchTranscript(query = query)
                ?.distinctBy { it.id }
                ?: emptyList()

            if (responses.isEmpty()) {
                return@withContext Result.failure(NoTranscriptFoundException(query))
            }

            val results = responses.mapNotNull { response ->
                val rawTranscript = response.syncedTranscript ?: response.plainTranscript ?: return@mapNotNull null
                val parsed = TranscriptUtils.parseTranscript(rawTranscript).copy(areFromRemote = true)
                if (!parsed.isValid()) return@mapNotNull null

                TranscriptSearchResult(response, parsed, rawTranscript)
            }.sortedByDescending { !it.record.syncedTranscript.isNullOrEmpty() }

            if (results.isEmpty()) {
                Result.failure(NoTranscriptFoundException(query))
            } else {
                Result.success(Pair(query, results))
            }
        } catch (e: Exception) {
            LogUtils.e(this@TranscriptRepositoryImpl, e, "Manual search failed")
            Result.failure(TranscriptException(context.getString(R.string.failed_to_search_for_Transcript), e)
            )
        }
    }

    override suspend fun updateTranscript(TrackId: Long, TranscriptContent: String): Unit = withContext(Dispatchers.IO) {
        LogUtils.d(this@TranscriptRepositoryImpl, "Updating Transcript for TrackId: $TrackId")

        val parsedTranscript = TranscriptUtils.parseTranscript(TranscriptContent)
        if (!parsedTranscript.isValid()) {
            LogUtils.w(this@TranscriptRepositoryImpl, "Attempted to save empty Transcript for TrackId: $TrackId")
            return@withContext
        }

        TranscriptDao.insert(
             com.oakiha.audia.data.database.TranscriptEntity(
                 TrackId = TrackId,
                 content = TranscriptContent,
                 isSynced = parsedTranscript.synced?.isNotEmpty() == true,
                 source = "manual"
             )
        )

        val cacheKey = generateCacheKey(TrackId.toString())
        synchronized(TranscriptCache) {
            TranscriptCache[cacheKey] = parsedTranscript
        }
        LogUtils.d(this@TranscriptRepositoryImpl, "Updated and cached Transcript for TrackId: $TrackId")
    }

    override suspend fun resetTranscript(TrackId: Long): Unit = withContext(Dispatchers.IO) {
        LogUtils.d(this, "Resetting Transcript for TrackId: $TrackId")
        val cacheKey = generateCacheKey(TrackId.toString())
        synchronized(TranscriptCache) {
            TranscriptCache.remove(cacheKey)
        }
        TranscriptDao.deleteTranscript(TrackId)
        
        // Also remove JSON cache
        try {
            val file = File(context.filesDir, "Transcript/${TrackId}.json")
            if (file.exists()) file.delete()
        } catch (e: Exception) {
            Log.w(TAG, "Error deleting JSON cache: ${e.message}")
        }
    }

    override suspend fun resetAllTranscript(): Unit = withContext(Dispatchers.IO) {
        LogUtils.d(this, "Resetting all Transcript")
        synchronized(TranscriptCache) {
            TranscriptCache.clear()
        }
        TranscriptDao.deleteAll()
        
        // Also clear JSON cache directory
        try {
            val TranscriptDir = File(context.filesDir, "Transcript")
            if (TranscriptDir.exists()) {
                TranscriptDir.listFiles()?.forEach { it.delete() }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error clearing JSON cache: ${e.message}")
        }
    }

    override suspend fun scanAndAssignLocalLrcFiles(
        Tracks: List<Track>,
        onProgress: suspend (current: Int, total: Int) -> Unit
    ): Int = withContext(Dispatchers.IO) {
        LogUtils.d(this@TranscriptRepositoryImpl, "Starting bulk scan for .lrc files for ${Tracks.size} Tracks")
        val updatedCount = AtomicInteger(0)
        val processedCount = AtomicInteger(0)
        val total = Tracks.size
        
        // Only scan Tracks that don't have Transcript
        val TracksToScan = Tracks.filter { it.Transcript.isNullOrBlank() }
        val skippedCount = total - TracksToScan.size
        processedCount.addAndGet(skippedCount)
        
        LogUtils.d(this@TranscriptRepositoryImpl, "Skipping $skippedCount Tracks that already have Transcript. Scanning ${TracksToScan.size} Tracks.")
        
        onProgress(processedCount.get(), total)
        
        if (TracksToScan.isEmpty()) {
            return@withContext 0
        }

        val semaphore = Semaphore(8) // Limit concurrency

        coroutineScope {
            TracksToScan.map { Track ->
                async {
                    semaphore.withPermit {
                        try {
                            // Find Transcript file
                            val TrackFile = File(Track.path)
                            val directory = TrackFile.parentFile
                            
                            if (directory != null && directory.exists()) {
                                var foundFile: File? = null
                                
                                // Strategy 1: Exact match name
                                val exactMatch = File(directory, "${TrackFile.nameWithoutExtension}.lrc")
                                if (exactMatch.exists() && exactMatch.canRead()) {
                                    foundFile = exactMatch
                                }
                                
                                // Strategy 2: Author - Title
                                if (foundFile == null) {
                                    val cleanAuthor = Track.displayAuthor.replace(Regex("[^a-zA-Z0-9]"), "_")
                                    val cleanTitle = Track.title.replace(Regex("[^a-zA-Z0-9]"), "_")
                                    val altMatch = File(directory, "${cleanAuthor}_${cleanTitle}.lrc")
                                    if (altMatch.exists() && altMatch.canRead()) {
                                        foundFile = altMatch
                                    }
                                }
                                
                                if (foundFile != null) {
                                    val content = foundFile.readText()
                                    // Verify validity
                                    if (TranscriptUtils.parseTranscript(content).isValid()) {
                                        TranscriptDao.insert(
                                             com.oakiha.audia.data.database.TranscriptEntity(
                                                 TrackId = Track.id.toLong(),
                                                 content = content,
                                                 isSynced = TranscriptUtils.parseTranscript(content).synced?.isNotEmpty() == true,
                                                 source = "local_file"
                                             )
                                        )
                                        updatedCount.incrementAndGet()
                                        LogUtils.d(this@TranscriptRepositoryImpl, "Auto-assigned Transcript from ${foundFile.name}")
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Error scanning Transcript for ${Track.title}: ${e.message}")
                        }
                        
                        val current = processedCount.incrementAndGet()
                        if (current % 20 == 0 || current == total) {
                            onProgress(current, total)
                        }
                    }
                }
            }.awaitAll()
        }
        
        LogUtils.d(this@TranscriptRepositoryImpl, "Bulk scan complete. Updated ${updatedCount.get()} Tracks.")
        return@withContext updatedCount.get()
    }

    override fun clearCache() {
        LogUtils.d(this, "Clearing Transcript in-memory cache")
        synchronized(TranscriptCache) {
            TranscriptCache.clear()
        }
    }

    private fun generateCacheKey(TrackId: String): String = TrackId

    private fun createTempFileFromUri(uri: Uri): File? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val fileName = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex >= 0) cursor.getString(nameIndex) else "temp_audio"
                    } else "temp_audio"
                } ?: "temp_audio"

                val tempFile = File.createTempFile("Transcript_", "_$fileName", context.cacheDir)
                FileOutputStream(tempFile).use { output ->
                    inputStream.copyTo(output)
                }
                tempFile
            }
        } catch (e: Exception) {
            LogUtils.e(this, e, "Error creating temp file from URI")
            null
        }
    }
}

data class TranscriptSearchResult(val record: LrcLibResponse, val Transcript: Transcript, val rawTranscript: String)

data class NoTranscriptFoundException(val query: String? = null) : Exception()

class TranscriptException(message: String, cause: Throwable? = null) : Exception(message, cause)
