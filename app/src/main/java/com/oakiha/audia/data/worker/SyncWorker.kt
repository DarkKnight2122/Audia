package com.oakiha.audia.data.worker

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import android.os.Trace // Import Trace
import android.provider.MediaStore
import android.util.Log
import androidx.core.net.toUri
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.oakiha.audia.data.database.BookEntity
import com.oakiha.audia.data.database.AuthorEntity
import com.oakiha.audia.data.database.AudiobookDao
import com.oakiha.audia.data.database.TrackAuthorCrossRef
import com.oakiha.audia.data.database.TrackEntity
import com.oakiha.audia.data.media.AudioMetadataReader
import com.oakiha.audia.data.model.Track
import com.oakiha.audia.data.preferences.UserPreferencesRepository
import com.oakiha.audia.data.repository.TranscriptRepository
import com.oakiha.audia.utils.BookArtCacheManager
import com.oakiha.audia.utils.BookArtUtils
import com.oakiha.audia.utils.AudioMetaUtils.getAudioMetadata
import com.oakiha.audia.utils.DirectoryRuleResolver
import com.oakiha.audia.utils.normalizeMetadataTextOrEmpty
import com.oakiha.audia.utils.splitAuthorsByDelimiters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import timber.log.Timber

enum class SyncMode {
    INCREMENTAL,
    FULL,
    REBUILD
}

@HiltWorker
class SyncWorker
@AssistedInject
constructor(
        @Assisted appContext: Context,
        @Assisted workerParams: WorkerParameters,
        private val AudiobookDao: AudiobookDao,
        private val userPreferencesRepository: UserPreferencesRepository,
        private val TranscriptRepository: TranscriptRepository
) : CoroutineWorker(appContext, workerParams) {

    private val contentResolver: ContentResolver = appContext.contentResolver

    override suspend fun doWork(): Result =
            withContext(Dispatchers.IO) {
                Trace.beginSection("SyncWorker.doWork")
                try {
                    val syncModeName =
                            inputData.getString(INPUT_SYNC_MODE) ?: SyncMode.INCREMENTAL.name
                    val syncMode = SyncMode.valueOf(syncModeName)
                    val forceMetadata = inputData.getBoolean(INPUT_FORCE_METADATA, false)

                    Timber.tag(TAG)
                        .i("Starting MediaStore synchronization (Mode: $syncMode, ForceMetadata: $forceMetadata)...")
                    val startTime = System.currentTimeMillis()

                    val AuthorDelimiters = userPreferencesRepository.AuthorDelimitersFlow.first()
                    val groupByBookAuthor =
                            userPreferencesRepository.groupByBookAuthorFlow.first()
                    val rescanRequired =
                            userPreferencesRepository.AuthorsettingsRescanRequiredFlow.first()

                    // Feature: Directory Filtering
                    val allowedDirs = userPreferencesRepository.allowedDirectoriesFlow.first()
                    val blockedDirs = userPreferencesRepository.blockedDirectoriesFlow.first()
                    val directoryResolver = DirectoryRuleResolver(allowedDirs, blockedDirs)
                    
                    var lastSyncTimestamp = userPreferencesRepository.getLastSyncTimestamp()

                    Timber.tag(TAG)
                        .d("Author parsing delimiters: $AuthorDelimiters, groupByBookAuthor: $groupByBookAuthor, rescanRequired: $rescanRequired")

                    // --- MEDIA SCAN PHASE ---
                    // For INCREMENTAL or FULL sync, trigger a media scan to detect new files
                    // that may not have been indexed by MediaStore yet (e.g., files added via USB)
                    if (syncMode != SyncMode.REBUILD) {
                        triggerMediaScanForNewFiles()
                    }

                    // --- DELETION PHASE ---
                    // Detect and remove deleted Tracks efficiently using ID comparison
                    // We do this for INCREMENTAL and FULL modes. REBUILD clears everything anyway.
                    if (syncMode != SyncMode.REBUILD) {
                        val localTrackIds = AudiobookDao.getAllTrackIds().toHashSet()
                        val mediaStoreIds = fetchMediaStoreIds(directoryResolver)

                        // Identify IDs that are in local DB but not in MediaStore
                        val deletedIds = localTrackIds - mediaStoreIds

                        if (deletedIds.isNotEmpty()) {
                            Timber.tag(TAG)
                                .i("Found ${deletedIds.size} deleted Tracks. Removing from database...")
                            // Chunk deletions to avoid SQLite variable limit (default 999)
                            val batchSize = 500
                            deletedIds.chunked(batchSize).forEach { chunk ->
                                AudiobookDao.deleteTracksByIds(chunk.toList())
                                AudiobookDao.deleteCrossRefsByTrackIds(chunk.toList())
                            }
                        } else {
                            Timber.tag(TAG).d("No deleted Tracks found.")
                        }
                    }

                    // --- FETCH PHASE ---
                    // Determine what to fetch based on mode
                    val isFreshInstall = AudiobookDao.getTrackCount().first() == 0

                    // If REBUILD or FULL or RescanRequired or Fresh Install -> Fetch EVERYTHING
                    // (timestamp = 0)
                    // If INCREMENTAL -> Fetch only changes since lastSyncTimestamp
                    val fetchTimestamp =
                            if (syncMode == SyncMode.INCREMENTAL &&
                                            !rescanRequired &&
                                            !isFreshInstall
                            ) {
                                lastSyncTimestamp /
                                        1000 // Convert to seconds for MediaStore comparison
                            } else {
                                0L
                            }

                    Timber.tag(TAG)
                        .i("Fetching Audiobook from MediaStore (since: $fetchTimestamp seconds)...")

                    // Update every 50 Tracks or ~5% of library
                    val progressBatchSize = 50

                    val mediaStoreTracks =
                            fetchAudiobookFromMediaStore(
                                    fetchTimestamp,
                                    forceMetadata,
                                    directoryResolver,
                                    progressBatchSize
                            ) { current, total, phaseOrdinal ->
                                setProgress(
                                        workDataOf(
                                                PROGRESS_CURRENT to current,
                                                PROGRESS_TOTAL to total,
                                                PROGRESS_PHASE to phaseOrdinal
                                        )
                                )
                            }

                    Timber.tag(TAG)
                        .i("Fetched ${mediaStoreTracks.size} new/modified Tracks from MediaStore.")

                    // --- PROCESSING PHASE ---
                    if (mediaStoreTracks.isNotEmpty()) {

                        // If rebuilding, clear everything first
                        if (syncMode == SyncMode.REBUILD) {
                            Timber.tag(TAG)
                                .i("Rebuild mode: Clearing all Audiobook data before insert.")
                            AudiobookDao.clearAllAudiobookDataWithCrossRefs()
                        }

                        val allExistingAuthors =
                                if (syncMode == SyncMode.REBUILD) {
                                    emptyList()
                                } else {
                                    AudiobookDao.getAllAuthorsListRaw()
                                }

                        val existingAuthorImageUrls =
                                allExistingAuthors.associate { it.id to it.imageUrl }
                        
                        // Load all existing Author IDs to ensure stability across incremental syncs
                        val existingAuthorIdMap = allExistingAuthors.associate { it.name to it.id }.toMutableMap()
                        val maxAuthorId = AudiobookDao.getMaxAuthorId() ?: 0L

                        // Prepare list of existing Tracks to preserve user edits
                        // We only need to check against existing Tracks if we are updating them
                        val localTracksMap =
                                if (syncMode != SyncMode.REBUILD) {
                                    AudiobookDao.getAllTracksList().associateBy { it.id }
                                } else {
                                    emptyMap()
                                }

                        val TracksToProcess = mediaStoreTracks

                        Timber.tag(TAG)
                            .i("Processing ${TracksToProcess.size} Tracks for upsert. Hash: ${TracksToProcess.hashCode()}")

                        val TracksToInsert =
                                TracksToProcess.map { mediaStoreTrack ->
                                    val localTrack = localTracksMap[mediaStoreTrack.id]
                                    if (localTrack != null) {
                                        // Preserve user-edited fields
                                        val needsAuthorCompare =
                                                !rescanRequired &&
                                                        localTrack.AuthorName.isNotBlank() &&
                                                        localTrack.AuthorName !=
                                                                mediaStoreTrack.AuthorName

                                        val shouldPreserveAuthorName =
                                                if (needsAuthorCompare) {
                                                    val mediaStoreAuthors =
                                                            mediaStoreTrack.AuthorName
                                                                    .splitAuthorsByDelimiters(
                                                                            AuthorDelimiters
                                                                    )
                                                    val mediaStorePrimaryAuthor =
                                                            mediaStoreAuthors.firstOrNull()?.trim()
                                                    val mediaStoreHasMultipleAuthors =
                                                            mediaStoreAuthors.size > 1
                                                    !(mediaStoreHasMultipleAuthors &&
                                                            mediaStorePrimaryAuthor != null &&
                                                            localTrack.AuthorName.trim() ==
                                                                    mediaStorePrimaryAuthor)
                                                } else {
                                                    false
                                                }

                                        mediaStoreTrack.copy(
                                                dateAdded =
                                                        localTrack.dateAdded, // Preserve original
                                                // date added if needed
                                                Transcript = localTrack.Transcript,
                                                title =
                                                        if (localTrack.title !=
                                                                        mediaStoreTrack.title &&
                                                                        localTrack.title.isNotBlank()
                                                        )
                                                                localTrack.title
                                                        else mediaStoreTrack.title,
                                                AuthorName =
                                                        if (shouldPreserveAuthorName)
                                                                localTrack.AuthorName
                                                        else mediaStoreTrack.AuthorName,
                                                BookName =
                                                        if (localTrack.BookName !=
                                                                        mediaStoreTrack.BookName &&
                                                                        localTrack.BookName
                                                                                .isNotBlank()
                                                        )
                                                                localTrack.BookName
                                                        else mediaStoreTrack.BookName,
                                                Category = localTrack.Category ?: mediaStoreTrack.Category,
                                                trackNumber =
                                                        if (localTrack.trackNumber != 0 &&
                                                                        localTrack.trackNumber !=
                                                                                mediaStoreTrack
                                                                                        .trackNumber
                                                        )
                                                                localTrack.trackNumber
                                                        else mediaStoreTrack.trackNumber,
                                                BookArtUriString = localTrack.BookArtUriString
                                                                ?: mediaStoreTrack.BookArtUriString
                                        )
                                    } else {
                                        mediaStoreTrack
                                    }
                                }

                        val (correctedTracks, Books, Authors, crossRefs) =
                                preProcessAndDeduplicateWithMultiAuthor(
                                        Tracks = TracksToInsert,
                                        AuthorDelimiters = AuthorDelimiters,
                                        groupByBookAuthor = groupByBookAuthor,
                                        existingAuthorImageUrls = existingAuthorImageUrls,
                                        existingAuthorIdMap = existingAuthorIdMap,
                                        initialMaxAuthorId = maxAuthorId
                                )

                        // Use incrementalSyncAudiobookData for all modes except REBUILD
                        // Even for FULL sync, we can just upsert the values
                        if (syncMode == SyncMode.REBUILD) {
                            AudiobookDao.insertAudiobookDataWithCrossRefs(
                                    correctedTracks,
                                    Books,
                                    Authors,
                                    crossRefs
                            )
                        } else {
                            // incrementalSyncAudiobookData handles upserts efficiently
                            // processing deleted Tracks was already handled at the start
                            AudiobookDao.incrementalSyncAudiobookData(
                                    Tracks = correctedTracks,
                                    Books = Books,
                                    Authors = Authors,
                                    crossRefs = crossRefs,
                                    deletedTrackIds = emptyList() // Already handled
                            )
                        }

                        // Clear the rescan required flag
                        userPreferencesRepository.clearAuthorsettingsRescanRequired()

                        val endTime = System.currentTimeMillis()
                        Timber.tag(TAG)
                            .i("Synchronization finished successfully in ${endTime - startTime}ms.")
                        userPreferencesRepository.setLastSyncTimestamp(System.currentTimeMillis())

                        // Count total Tracks for the output
                        val totalTracks = AudiobookDao.getTrackCount().first()

                        // --- LRC SCANNING PHASE ---
                        val autoScanLrc = userPreferencesRepository.autoScanLrcFilesFlow.first()
                        if (autoScanLrc) {
                            Timber.tag(TAG).i("Auto-scan LRC files enabled. Starting scan phase...")

                            val allTracksEntities = AudiobookDao.getAllTracksList()
                            val allTracks =
                                    allTracksEntities.map { entity ->
                                        Track(
                                                id = entity.id.toString(),
                                                title = entity.title,
                                                Author = entity.AuthorName,
                                                AuthorId = entity.AuthorId,
                                                Book = entity.BookName,
                                                BookId = entity.BookId,
                                                path = entity.filePath,
                                                contentUriString = entity.contentUriString,
                                                BookArtUriString = entity.BookArtUriString,
                                                duration = entity.duration,
                                                Transcript = entity.Transcript,
                                                dateAdded = entity.dateAdded,
                                                trackNumber = entity.trackNumber,
                                                year = entity.year,
                                                mimeType = entity.mimeType,
                                                bitrate = entity.bitrate,
                                                sampleRate = entity.sampleRate
                                        )
                                    }

                            val scannedCount =
                                    TranscriptRepository.scanAndAssignLocalLrcFiles(allTracks) {
                                            current,
                                            total ->
                                        setProgress(
                                                workDataOf(
                                                        PROGRESS_CURRENT to current,
                                                        PROGRESS_TOTAL to total,
                                                        PROGRESS_PHASE to
                                                                SyncProgress.SyncPhase.SCANNING_LRC
                                                                        .ordinal
                                                )
                                        )
                                    }

                            Log.i(TAG, "LRC Scan finished. Assigned Transcript to $scannedCount Tracks.")
                        }
                        
                        // Clean orphaned Book art cache files
                        val allTrackIds = AudiobookDao.getAllTrackIds().toSet()
                        BookArtCacheManager.cleanOrphanedCacheFiles(applicationContext, allTrackIds)

                        Result.success(workDataOf(OUTPUT_TOTAL_Tracks to totalTracks))
                    } else {
                        Log.i(TAG, "No new or modified Tracks found.")

                        // If it was a fresh install/rebuild and we found nothing, clear everything
                        if ((syncMode == SyncMode.REBUILD || isFreshInstall) &&
                                        mediaStoreTracks.isEmpty()
                        ) {
                            AudiobookDao.clearAllAudiobookDataWithCrossRefs()
                            Log.w(
                                    TAG,
                                    "MediaStore fetch resulted in empty list. Local Audiobook data cleared."
                            )
                        }

                        val endTime = System.currentTimeMillis()
                        Log.i(
                                TAG,
                                "Synchronization (No Changes) finished in ${endTime - startTime}ms."
                        )
                        userPreferencesRepository.setLastSyncTimestamp(System.currentTimeMillis())

                        val totalTracks = AudiobookDao.getTrackCount().first()
                        
                        // Clean orphaned Book art cache files
                        val allTrackIds = AudiobookDao.getAllTrackIds().toSet()
                        BookArtCacheManager.cleanOrphanedCacheFiles(applicationContext, allTrackIds)
                        
                        Result.success(workDataOf(OUTPUT_TOTAL_Tracks to totalTracks))
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error during MediaStore synchronization", e)
                    Result.failure()
                } finally {
                    Trace.endSection() // End SyncWorker.doWork
                }
            }

    /**
     * Efficiently fetches ONLY the IDs of all Tracks in MediaStore. Used for fast deletion
     * detection.
     */
    private fun getBaseSelection(): Pair<String, Array<String>> {
        val selectionBuilder = StringBuilder()
        val selectionArgsList = mutableListOf<String>()

        selectionBuilder.append(
                "((${MediaStore.Audio.Media.IS_Audiobook} != 0 AND ${MediaStore.Audio.Media.DURATION} >= ?) "
        )
        selectionArgsList.add("10000")

        selectionBuilder.append("OR ${MediaStore.Audio.Media.DATA} LIKE '%.m4a' ")
        selectionBuilder.append("OR ${MediaStore.Audio.Media.DATA} LIKE '%.flac' ")
        selectionBuilder.append("OR ${MediaStore.Audio.Media.DATA} LIKE '%.wav' ")
        selectionBuilder.append("OR ${MediaStore.Audio.Media.DATA} LIKE '%.opus' ")
        selectionBuilder.append("OR ${MediaStore.Audio.Media.DATA} LIKE '%.ogg')")

        return Pair(selectionBuilder.toString(), selectionArgsList.toTypedArray())
    }

    /**
     * Efficiently fetches ONLY the IDs of all Tracks in MediaStore. Used for fast deletion
     * detection.
     */
    private fun fetchMediaStoreIds(directoryResolver: DirectoryRuleResolver): Set<Long> {
        val ids = HashSet<Long>()
        // We need DATA to check path filtering
        val projection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DATA)
        val (selection, selectionArgs) = getBaseSelection()

        contentResolver.query(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        projection,
                        selection,
                        selectionArgs,
                        null
                )
                ?.use { cursor ->
                    val idCol = cursor.getColumnIndex(MediaStore.Audio.Media._ID)
                    val dataCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
                    if (idCol >= 0 && dataCol >= 0) {
                        while (cursor.moveToNext()) {
                            val path = cursor.getString(dataCol)
                            val parentPath = File(path).parent
                            if (parentPath != null && directoryResolver.isBlocked(parentPath)) {
                                continue 
                            }
                            ids.add(cursor.getLong(idCol))
                        }
                    }
                }
        return ids
    }

    /** Data class to hold the result of multi-Author preprocessing. */
    private data class MultiAuthorProcessResult(
            val Tracks: List<TrackEntity>,
            val Books: List<BookEntity>,
            val Authors: List<AuthorEntity>,
            val crossRefs: List<TrackAuthorCrossRef>
    )

    /**
     * Process Tracks with multi-Author support. Splits Author names by delimiters and creates proper
     * cross-references.
     */
    private fun preProcessAndDeduplicateWithMultiAuthor(
            Tracks: List<TrackEntity>,
            AuthorDelimiters: List<String>,
            groupByBookAuthor: Boolean,
            existingAuthorImageUrls: Map<Long, String?>,
            existingAuthorIdMap: MutableMap<String, Long>,
            initialMaxAuthorId: Long
    ): MultiAuthorProcessResult {
        
        val nextAuthorId = AtomicLong(initialMaxAuthorId + 1)
        val AuthorNameToId = existingAuthorIdMap // Re-use the map passed in
        
        val allCrossRefs = mutableListOf<TrackAuthorCrossRef>()
        val AuthorTrackCounts = mutableMapOf<Long, Int>()
        val BookMap = mutableMapOf<Pair<String, String>, Long>()
        val AuthorsplitCache = mutableMapOf<String, List<String>>()
        val correctedTracks = ArrayList<TrackEntity>(Tracks.size)

        Tracks.forEach { Track ->
            val rawAuthorName = Track.AuthorName
            val TrackAuthorNameTrimmed = rawAuthorName.trim()
            val AuthorsForTrack =
                    AuthorsplitCache.getOrPut(rawAuthorName) {
                        rawAuthorName.splitAuthorsByDelimiters(AuthorDelimiters)
                    }

            AuthorsForTrack.forEach { AuthorName ->
                val normalizedName = AuthorName.trim()
                if (normalizedName.isNotEmpty() && !AuthorNameToId.containsKey(normalizedName)) {
                     // Check if it's the Track's primary Author and we want to preserve that ID if possible?
                     // Actually, just generate new ID if not found in map.
                     val id = nextAuthorId.getAndIncrement()
                     AuthorNameToId[normalizedName] = id
                }
            }
            
            val primaryAuthorName =
                    AuthorsForTrack.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }
                            ?: TrackAuthorNameTrimmed
            val primaryAuthorId = AuthorNameToId[primaryAuthorName] ?: Track.AuthorId

            AuthorsForTrack.forEachIndexed { index, AuthorName ->
                val normalizedName = AuthorName.trim()
                val AuthorId = AuthorNameToId[normalizedName]
                if (AuthorId != null) {
                    val isPrimary = (index == 0) // First Author is primary
                    allCrossRefs.add(
                            TrackAuthorCrossRef(
                                    TrackId = Track.id,
                                    AuthorId = AuthorId,
                                    isPrimary = isPrimary
                            )
                    )
                    AuthorTrackCounts[AuthorId] = (AuthorTrackCounts[AuthorId] ?: 0) + 1
                }
            }

            // --- Book Logic ---
            val rawBookName = Track.BookName.trim()
            val BookIdentityAuthor = if (groupByBookAuthor) {
                Track.BookAuthor?.trim()?.takeIf { it.isNotEmpty() } ?: primaryAuthorName
            } else {
                primaryAuthorName
            }
            
            val BookKey = rawBookName to BookIdentityAuthor
            
            if (!BookMap.containsKey(BookKey)) {
                BookMap[BookKey] = Track.BookId 
            }
            val finalBookId = BookMap[BookKey] ?: Track.BookId // fallback

            correctedTracks.add(
                    Track.copy(
                            AuthorId = primaryAuthorId,
                            AuthorName = primaryAuthorName,
                            BookId = finalBookId
                    )
            )
        }

        // Build Entities
        val AuthorEntities = AuthorNameToId.map { (name, id) ->
            val count = AuthorTrackCounts[id] ?: 0
            AuthorEntity(
                id = id,
                name = name,
                trackCount = count,
                imageUrl = existingAuthorImageUrls[id]
            )
        }
        
        // Re-calculate Book Entities from the corrected Tracks to ensure we have valid metadata (Art, Year)
        // which isn't available in the simple BookMap (which only has ID)
        val BookEntities = correctedTracks.groupBy { it.BookId }.map { (catBookId, TracksInBook) ->
             val firstTrack = TracksInBook.first()
             // Determine Book Author Name
             val determinedBookAuthor = firstTrack.BookAuthor?.takeIf { it.isNotBlank() } 
                 ?: firstTrack.AuthorName
                 
             // Determine Book Author ID (best effort lookup)
             val determinedBookAuthorId = AuthorNameToId[determinedBookAuthor] ?: 0L

             BookEntity(
                 id = catBookId,
                 title = firstTrack.BookName,
                 AuthorName = determinedBookAuthor,
                 AuthorId = determinedBookAuthorId,
                 BookArtUriString = firstTrack.BookArtUriString,
                 TrackCount = TracksInBook.size, 
                 year = firstTrack.year
             )
        }

        return MultiAuthorProcessResult(
                Tracks = correctedTracks, // Corrected Tracks have the right Book IDs now
                Books = BookEntities,
                Authors = AuthorEntities,
                crossRefs = allCrossRefs
        )
    }

    private fun fetchBookArtUrisByBookId(): Map<Long, String> {
        val projection = arrayOf(MediaStore.Audio.Books._ID, MediaStore.Audio.Books.Book_ART)

        return buildMap {
            contentResolver.query(
                            MediaStore.Audio.Books.EXTERNAL_CONTENT_URI,
                            projection,
                            null,
                            null,
                            null
                    )
                    ?.use { cursor ->
                        val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Books._ID)
                        val artCol = cursor.getColumnIndex(MediaStore.Audio.Books.Book_ART)
                        if (artCol >= 0) {
                            while (cursor.moveToNext()) {
                                val BookId = cursor.getLong(idCol)
                                val storedArtPath = cursor.getString(artCol)
                                val uriString =
                                        when {
                                            !storedArtPath.isNullOrBlank() ->
                                                    File(storedArtPath).toURI().toString()
                                            BookId > 0 ->
                                                    ContentUris.withAppendedId(
                                                                    "content://media/external/audio/Bookart".toUri(),
                                                                    BookId
                                                            )
                                                            .toString()
                                            else -> null
                                        }

                                if (uriString != null) put(BookId, uriString)
                            }
                        }
                    }
        }
    }

    /**
     * Fetches a map of Track ID -> Category Name using the MediaStore.Audio.Categories table. This is
     * necessary because the Category column in MediaStore.Audio.Media is not reliably available or
     * populated on all Android versions (especially pre-API 30).
     * 
     * Optimized: 
     * 1. Caches results for 1 hour to avoid refetching on incremental syncs
     * 2. Fetches all Categories first, then queries members in parallel with controlled concurrency
     */
    private suspend fun fetchCategoryMap(forceRefresh: Boolean = false): Map<Long, String> = coroutineScope {
        // Check cache first (valid for 1 hour)
        val now = System.currentTimeMillis()
        val cacheAge = now - CategoryMapCacheTimestamp
        if (!forceRefresh && CategoryMapCache.isNotEmpty() && cacheAge < Category_CACHE_TTL_MS) {
            Log.d(TAG, "Using cached Category map (${CategoryMapCache.size} entries, age: ${cacheAge/1000}s)")
            return@coroutineScope CategoryMapCache
        }
        
        val CategoryMap = ConcurrentHashMap<Long, String>()
        val CategoryProjection = arrayOf(MediaStore.Audio.Categories._ID, MediaStore.Audio.Categories.NAME)
        
        // Semaphore to limit concurrent queries (avoid overwhelming ContentResolver)
        val querySemaphore = Semaphore(4)

        try {
            // Step 1: Fetch all Categories (single query)
            val Categories = mutableListOf<Pair<Long, String>>()
            
            contentResolver.query(
                            MediaStore.Audio.Categories.EXTERNAL_CONTENT_URI,
                            CategoryProjection,
                            null,
                            null,
                            null
                    )
                    ?.use { cursor ->
                        val idCol = cursor.getColumnIndex(MediaStore.Audio.Categories._ID)
                        val nameCol = cursor.getColumnIndex(MediaStore.Audio.Categories.NAME)

                        if (idCol >= 0 && nameCol >= 0) {
                            while (cursor.moveToNext()) {
                                val CategoryId = cursor.getLong(idCol)
                                val CategoryName = cursor.getString(nameCol)

                                if (!CategoryName.isNullOrBlank() &&
                                                !CategoryName.equals("unknown", ignoreCase = true)
                                ) {
                                    Categories.add(CategoryId to CategoryName)
                                }
                            }
                        }
                    }
            
            // Step 2: Fetch members for each Category in parallel (controlled concurrency)
            Categories.map { (CategoryId, CategoryName) ->
                async(Dispatchers.IO) {
                    querySemaphore.withPermit {
                        val membersUri =
                                MediaStore.Audio.Categories.Members.getContentUri(
                                        "external",
                                        CategoryId
                                )
                        val membersProjection =
                                arrayOf(MediaStore.Audio.Categories.Members.AUDIO_ID)

                        contentResolver.query(
                                        membersUri,
                                        membersProjection,
                                        null,
                                        null,
                                        null
                                )
                                ?.use { membersCursor ->
                                    val audioIdCol =
                                            membersCursor.getColumnIndex(
                                                    MediaStore.Audio.Categories.Members.AUDIO_ID
                                            )
                                    if (audioIdCol >= 0) {
                                        while (membersCursor.moveToNext()) {
                                            val audioId = membersCursor.getLong(audioIdCol)
                                            // If a Track has multiple Categories, the last one processed wins.
                                            // This is acceptable as a primary Category for display.
                                            CategoryMap[audioId] = CategoryName
                                        }
                                    }
                                }
                    }
                }
            }.awaitAll()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching Category map", e)
        }
        
        // Update cache
        if (CategoryMap.isNotEmpty()) {
            CategoryMapCache = CategoryMap.toMap()
            CategoryMapCacheTimestamp = System.currentTimeMillis()
            Log.d(TAG, "Category map cache updated with ${CategoryMap.size} entries")
        }
        
        CategoryMap
    }

    /** Raw data extracted from cursor - lightweight class for fast iteration */
    private data class RawTrackData(
            val id: Long,
            val BookId: Long,
            val AuthorId: Long,
            val filePath: String,
            val title: String,
            val Author: String,
            val Book: String,
            val BookAuthor: String?,
            val duration: Long,
            val trackNumber: Int,
            val year: Int,
            val dateModified: Long
    )

    private suspend fun fetchAudiobookFromMediaStore(
            sinceTimestamp: Long, // Seconds
            forceMetadata: Boolean,
            directoryResolver: DirectoryRuleResolver,
            progressBatchSize: Int,
            onProgress: suspend (current: Int, total: Int, phaseOrdinal: Int) -> Unit
    ): List<TrackEntity> {
        Trace.beginSection("SyncWorker.fetchAudiobookFromMediaStore")

        val deepScan = forceMetadata
        val BookArtByBookId = if (!deepScan) fetchBookArtUrisByBookId() else emptyMap()
        val CategoryMap = fetchCategoryMap() // Load Categories upfront

        val projection =
                arrayOf(
                        MediaStore.Audio.Media._ID,
                        MediaStore.Audio.Media.TITLE,
                        MediaStore.Audio.Media.Author,
                        MediaStore.Audio.Media.Author_ID,
                        MediaStore.Audio.Media.Book,
                        MediaStore.Audio.Media.Book_ID,
                        MediaStore.Audio.Media.Book_Author,
                        MediaStore.Audio.Media.DURATION,
                        MediaStore.Audio.Media.DATA,
                        MediaStore.Audio.Media.TRACK,
                        MediaStore.Audio.Media.YEAR,
                        MediaStore.Audio.Media.DATE_MODIFIED
                )

        val (baseSelection, baseArgs) = getBaseSelection()
        val selectionBuilder = StringBuilder(baseSelection)
        val selectionArgsList = baseArgs.toMutableList()

        // Incremental selection
        if (sinceTimestamp > 0) {
            selectionBuilder.append(
                    " AND (${MediaStore.Audio.Media.DATE_MODIFIED} > ? OR ${MediaStore.Audio.Media.DATE_ADDED} > ?)"
            )
            selectionArgsList.add(sinceTimestamp.toString())
            selectionArgsList.add(sinceTimestamp.toString())
        }

        val selection = selectionBuilder.toString()
        val selectionArgs = selectionArgsList.toTypedArray()

        // Phase 1: Fast cursor iteration to collect raw data
        val rawDataList = mutableListOf<RawTrackData>()

        contentResolver.query(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        projection,
                        selection,
                        selectionArgs,
                        null
                )
                ?.use { cursor ->
                    val totalCount = cursor.count
                    onProgress(0, totalCount, SyncProgress.SyncPhase.FETCHING_MEDIASTORE.ordinal)

                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                    val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                    val AuthorCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.Author)
                    val AuthorIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.Author_ID)
                    val BookCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.Book)
                    val BookIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.Book_ID)
                    val BookAuthorCol = cursor.getColumnIndex(MediaStore.Audio.Media.Book_Author)
                    val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                    val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                    val trackCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
                    val yearCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
                    val dateAddedCol =
                            cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)

                    while (cursor.moveToNext()) {
                        try {
                            val data = cursor.getString(dataCol)
                            val parentPath = File(data).parent
                            if (parentPath != null) {
                                val normalizedParent = File(parentPath).absolutePath
                                if (directoryResolver.isBlocked(normalizedParent)) {
                                    continue
                                }
                            }
                        } catch (e: Exception) {
                            // Proceed on error
                        }

                        val TrackId = cursor.getLong(idCol)
                        rawDataList.add(
                                RawTrackData(
                                        id = cursor.getLong(idCol),
                                        BookId = cursor.getLong(BookIdCol),
                                        AuthorId = cursor.getLong(AuthorIdCol),
                                        filePath = cursor.getString(dataCol) ?: "",
                                        title =
                                                cursor.getString(titleCol)
                                                        .normalizeMetadataTextOrEmpty()
                                                        .ifEmpty { "Unknown Title" },
                                        Author =
                                                cursor.getString(AuthorCol)
                                                        .normalizeMetadataTextOrEmpty()
                                                        .ifEmpty { "Unknown Author" },
                                        Book =
                                                cursor.getString(BookCol)
                                                        .normalizeMetadataTextOrEmpty()
                                                        .ifEmpty { "Unknown Book" },
                                        BookAuthor =
                                                if (BookAuthorCol >= 0)
                                                        cursor.getString(BookAuthorCol)
                                                                ?.normalizeMetadataTextOrEmpty()
                                                                ?.takeIf { it.isNotBlank() }
                                                else null,
                                        duration = cursor.getLong(durationCol),
                                        trackNumber = cursor.getInt(trackCol),
                                        year = cursor.getInt(yearCol),
                                        dateModified = cursor.getLong(dateAddedCol)
                                )
                        )
                    }
                }

        val totalCount = rawDataList.size
        if (totalCount == 0) {
            Trace.endSection()
            return emptyList()
        }

        // Phase 2: Parallel processing of Tracks
        val processedCount = AtomicInteger(0)
        val concurrencyLimit = 8 // Limit parallel operations to prevent resource exhaustion
        val semaphore = Semaphore(concurrencyLimit)

        val Tracks = coroutineScope {
            rawDataList
                    .map { raw ->
                        async {
                            semaphore.withPermit {
                                val Track =
                                        processTrackData(raw, BookArtByBookId, CategoryMap, deepScan)

                                // Report progress
                                val count = processedCount.incrementAndGet()
                                if (count % progressBatchSize == 0 || count == totalCount) {
                                    onProgress(
                                            count,
                                            totalCount,
                                            SyncProgress.SyncPhase.FETCHING_MEDIASTORE.ordinal
                                    )
                                }

                                Track
                            }
                        }
                    }
                    .awaitAll()
        }

        Trace.endSection()
        return Tracks
    }

    /**
     * Process a single Track's raw data into a TrackEntity. This is the CPU/IO intensive work that
     * benefits from parallelization.
     */
    private suspend fun processTrackData(
            raw: RawTrackData,
            BookArtByBookId: Map<Long, String>,
            CategoryMap: Map<Long, String>,
            deepScan: Boolean
    ): TrackEntity {
        val parentDir = java.io.File(raw.filePath).parent ?: ""
        val contentUriString =
                ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, raw.id)
                        .toString()

        var BookArtUriString = BookArtByBookId[raw.BookId]
        if (deepScan) {
            BookArtUriString =
                    BookArtUtils.getBookArtUri(
                            applicationContext,
                            AudiobookDao,
                            raw.filePath,
                            raw.BookId,
                            raw.id,
                            true
                    )
                            ?: BookArtUriString
        }
        val audioMetadata =
                if (deepScan) getAudioMetadata(AudiobookDao, raw.id, raw.filePath, true) else null

        var title = raw.title
        var Author = raw.Author
        var Book = raw.Book
        var trackNumber = raw.trackNumber
        var year = raw.year
        var Category: String? = CategoryMap[raw.id] // Use mapped Category as default

        val shouldAugmentMetadata =
                deepScan ||
                        raw.filePath.endsWith(".wav", true) ||
                        raw.filePath.endsWith(".opus", true) ||
                        raw.filePath.endsWith(".ogg", true) ||
                        raw.filePath.endsWith(".oga", true) ||
                        raw.filePath.endsWith(".aiff", true)

        if (shouldAugmentMetadata) {
            val file = java.io.File(raw.filePath)
            if (file.exists()) {
                try {
                    AudioMetadataReader.read(file)?.let { meta ->
                        if (!meta.title.isNullOrBlank()) title = meta.title
                        if (!meta.Author.isNullOrBlank()) Author = meta.Author
                        if (!meta.Book.isNullOrBlank()) Book = meta.Book
                        if (!meta.Category.isNullOrBlank()) Category = meta.Category
                        if (meta.trackNumber != null) trackNumber = meta.trackNumber
                        if (meta.year != null) year = meta.year

                        meta.artwork?.let { art ->
                            val uri =
                                    BookArtUtils.saveBookArtToCache(
                                            applicationContext,
                                            art.bytes,
                                            raw.id
                                    )
                            BookArtUriString = uri.toString()
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to read metadata via TagLib for ${raw.filePath}", e)
                }
            }
        }

        return TrackEntity(
                id = raw.id,
                title = title,
                AuthorName = Author,
                AuthorId = raw.AuthorId,
                BookAuthor = raw.BookAuthor,
                BookName = Book,
                BookId = raw.BookId,
                contentUriString = contentUriString,
                BookArtUriString = BookArtUriString,
                duration = raw.duration,
                Category = Category,
                filePath = raw.filePath,
                parentDirectoryPath = parentDir,
                trackNumber = trackNumber,
                year = year,
                dateAdded =
                        raw.dateModified.let { seconds ->
                            if (seconds > 0) TimeUnit.SECONDS.toMillis(seconds)
                            else System.currentTimeMillis()
                        },
                mimeType = audioMetadata?.mimeType,
                sampleRate = audioMetadata?.sampleRate,
                bitrate = audioMetadata?.bitrate
        )
    }

    /**
     * Triggers a media scan ONLY for new files that are not yet in MediaStore.
     * This is a fast, incremental scan optimized for pull-to-refresh.
     * It compares filesystem files with MediaStore entries and only scans the difference.
     */
    private suspend fun triggerMediaScanForNewFiles() {
        withContext(Dispatchers.IO) {
            // "Global Augmented Scan"
            // We always scan the External Storage Root, but we use the 'allowedDirs' list
            // as "Explicit Includes" to bypass default filters (like skipping Android).
            val searchRoots = listOf(Environment.getExternalStorageDirectory())
            val allowedSet = userPreferencesRepository.allowedDirectoriesFlow.first().map { File(it) }.toSet()
            
            Log.i(TAG, "Starting Global Augmented Scan. Explicit includes: ${allowedSet.size}")
            
            val existingRoots = searchRoots.filter { it.exists() && it.isDirectory }

            if (existingRoots.isEmpty()) {
                Log.d(TAG, "No storage roots found")
                return@withContext
            }

            // Get all file paths currently in MediaStore
            val mediaStorePaths = fetchMediaStoreFilePaths()
            
            Log.d(TAG, "MediaStore has ${mediaStorePaths.size} known files")

            // Collect audio files from filesystem that are NOT in MediaStore
            val audioExtensions =
                    setOf("mp3", "flac", "m4a", "wav", "ogg", "opus", "aac", "wma", "aiff")
            val newFilesToScan = mutableListOf<String>()

            existingRoots.forEach { root ->
                root.walkTopDown()
                    .onEnter { dir ->
                        val name = dir.name
                        if (dir.isHidden || name.startsWith(".")) return@onEnter false
                        
                        // Default Skip Rules (System Folders)
                        val isSystemFolder = (name == "Android" || name == "data" || name == "obb")
                        if (isSystemFolder) {
                            // Check if this specific folder is Explicitly Allowed or is a Parent of an allowed folder
                            // e.g. if Allowed is "Android/media", we MUST enter "Android".
                            // e.g. if Allowed is "Android" (root), we MUST enter "Android".
                            val path = dir.absolutePath
                            val isAllowed = allowedSet.any { allowed -> 
                                allowed.absolutePath == path || allowed.absolutePath.startsWith("$path/")
                            }
                            
                            if (!isAllowed) {
                                // Apply strict skipping for Android/data and Android/obb if not allowed
                                val parent = dir.parentFile
                                if (name == "Android" && parent?.absolutePath == Environment.getExternalStorageDirectory().absolutePath) return@onEnter false
                                if (parent?.name == "Android" && (name == "data" || name == "obb")) return@onEnter false
                            }
                        }
                        true
                    }
                    .filter { it.isFile && it.extension.lowercase() in audioExtensions }
                    .filter { it.absolutePath !in mediaStorePaths } // Only new files
                    .forEach { newFilesToScan.add(it.absolutePath) }
            }

            if (newFilesToScan.isEmpty()) {
                Log.d(TAG, "No new audio files found - MediaStore is up to date")
                return@withContext
            }

            Log.i(TAG, "Found ${newFilesToScan.size} NEW audio files to scan")

            // Scan only the new files
            val latch = CountDownLatch(1)
            var scannedCount = 0

            MediaScannerConnection.scanFile(
                applicationContext, 
                newFilesToScan.toTypedArray(), 
                null
            ) { _, _ ->
                scannedCount++
                if (scannedCount >= newFilesToScan.size) {
                    latch.countDown()
                }
            }

            // Wait for scan to complete (max 15 seconds)
            val completed = latch.await(15, TimeUnit.SECONDS)
            if (!completed) {
                Log.w(TAG, "Media scan timeout after scanning $scannedCount/${newFilesToScan.size} files")
            } else {
                Log.i(TAG, "Media scan completed for ${newFilesToScan.size} new files")
            }
        }
    }

    /**
     * Fetches all file paths currently known to MediaStore.
     * Used to identify new files that need scanning.
     */
    private fun fetchMediaStoreFilePaths(): Set<String> {
        val paths = HashSet<String>()
        val projection = arrayOf(MediaStore.Audio.Media.DATA)
        val (selection, selectionArgs) = getBaseSelection()
        
        contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            null
        )?.use { cursor ->
            val dataCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
            if (dataCol >= 0) {
                while (cursor.moveToNext()) {
                    cursor.getString(dataCol)?.let { paths.add(it) }
                }
            }
        }
        return paths
    }

    companion object {
        const val WORK_NAME = "com.oakiha.audia.data.worker.SyncWorker"
        private const val TAG = "SyncWorker"
        const val INPUT_FORCE_METADATA = "input_force_metadata"
        const val INPUT_SYNC_MODE = "input_sync_mode"

        // Progress reporting constants
        const val PROGRESS_CURRENT = "progress_current"
        const val PROGRESS_TOTAL = "progress_total"
        const val PROGRESS_PHASE = "progress_phase"
        const val OUTPUT_TOTAL_Tracks = "output_total_Tracks"
        
        // Category cache - shared across worker instances to avoid refetching on incremental syncs
        private const val Category_CACHE_TTL_MS = 60 * 60 * 1000L // 1 hour
        @Volatile private var CategoryMapCache: Map<Long, String> = emptyMap()
        @Volatile private var CategoryMapCacheTimestamp: Long = 0L
        
        fun invalidateCategoryCache() {
            CategoryMapCache = emptyMap()
            CategoryMapCacheTimestamp = 0L
            Log.d(TAG, "Category cache invalidated")
        }

        fun startUpSyncWork(deepScan: Boolean = false) =
                OneTimeWorkRequestBuilder<SyncWorker>()
                        .setInputData(
                                workDataOf(
                                        INPUT_FORCE_METADATA to deepScan,
                                        INPUT_SYNC_MODE to SyncMode.INCREMENTAL.name
                                )
                        )
                        .build()

        fun incrementalSyncWork() =
                OneTimeWorkRequestBuilder<SyncWorker>()
                        .setInputData(workDataOf(INPUT_SYNC_MODE to SyncMode.INCREMENTAL.name))
                        .build()

        fun fullSyncWork(deepScan: Boolean = false) =
                OneTimeWorkRequestBuilder<SyncWorker>()
                        .setInputData(
                                workDataOf(
                                        INPUT_SYNC_MODE to SyncMode.FULL.name,
                                        INPUT_FORCE_METADATA to deepScan
                                )
                        )
                        .build()

        fun rebuildDatabaseWork() =
                OneTimeWorkRequestBuilder<SyncWorker>()
                        .setInputData(workDataOf(INPUT_SYNC_MODE to SyncMode.REBUILD.name))
                        .build()
    }
}
