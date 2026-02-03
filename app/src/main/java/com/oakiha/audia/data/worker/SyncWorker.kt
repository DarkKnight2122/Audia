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
import com.oakiha.audia.data.repository.LyricsRepository
import com.oakiha.audia.utils.BookArtCacheManager
import com.oakiha.audia.utils.BookArtUtils
import com.oakiha.audia.utils.AudioMetaUtils.getAudioMetadata
import com.oakiha.audia.utils.DirectoryRuleResolver
import com.oakiha.audia.utils.normalizeMetadataTextOrEmpty
import com.oakiha.audia.utils.splitArtistsByDelimiters
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
        private val audiobookDao: AudiobookDao,
        private val userPreferencesRepository: UserPreferencesRepository,
        private val lyricsRepository: LyricsRepository
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

                    val authorDelimiters = userPreferencesRepository.authorDelimitersFlow.first()
                    val groupByAlbumArtist =
                            userPreferencesRepository.groupByAlbumArtistFlow.first()
                    val rescanRequired =
                            userPreferencesRepository.authorSettingsRescanRequiredFlow.first()

                    // Feature: Directory Filtering
                    val allowedDirs = userPreferencesRepository.allowedDirectoriesFlow.first()
                    val blockedDirs = userPreferencesRepository.blockedDirectoriesFlow.first()
                    val directoryResolver = DirectoryRuleResolver(allowedDirs, blockedDirs)
                    
                    var lastSyncTimestamp = userPreferencesRepository.getLastSyncTimestamp()

                    Timber.tag(TAG)
                        .d("Author parsing delimiters: $authorDelimiters, groupByAlbumArtist: $groupByAlbumArtist, rescanRequired: $rescanRequired")

                    // --- MEDIA SCAN PHASE ---
                    // For INCREMENTAL or FULL sync, trigger a media scan to detect new files
                    // that may not have been indexed by MediaStore yet (e.g., files added via USB)
                    if (syncMode != SyncMode.REBUILD) {
                        triggerMediaScanForNewFiles()
                    }

                    // --- DELETION PHASE ---
                    // Detect and remove deleted tracks efficiently using ID comparison
                    // We do this for INCREMENTAL and FULL modes. REBUILD clears everything anyway.
                    if (syncMode != SyncMode.REBUILD) {
                        val localSongIds = audiobookDao.getAllTrackIds().toHashSet()
                        val mediaStoreIds = fetchMediaStoreIds(directoryResolver)

                        // Identify IDs that are in local DB but not in MediaStore
                        val deletedIds = localSongIds - mediaStoreIds

                        if (deletedIds.isNotEmpty()) {
                            Timber.tag(TAG)
                                .i("Found ${deletedIds.size} deleted tracks. Removing from database...")
                            // Chunk deletions to avoid SQLite variable limit (default 999)
                            val batchSize = 500
                            deletedIds.chunked(batchSize).forEach { chunk ->
                                audiobookDao.deleteTracksByIds(chunk.toList())
                                audiobookDao.deleteCrossRefsByTrackIds(chunk.toList())
                            }
                        } else {
                            Timber.tag(TAG).d("No deleted tracks found.")
                        }
                    }

                    // --- FETCH PHASE ---
                    // Determine what to fetch based on mode
                    val isFreshInstall = audiobookDao.getTrackCount().first() == 0

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
                        .i("Fetching music from MediaStore (since: $fetchTimestamp seconds)...")

                    // Update every 50 tracks or ~5% of library
                    val progressBatchSize = 50

                    val mediaStoreSongs =
                            fetchMusicFromMediaStore(
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
                        .i("Fetched ${mediaStoreSongs.size} new/modified tracks from MediaStore.")

                    // --- PROCESSING PHASE ---
                    if (mediaStoreSongs.isNotEmpty()) {

                        // If rebuilding, clear everything first
                        if (syncMode == SyncMode.REBUILD) {
                            Timber.tag(TAG)
                                .i("Rebuild mode: Clearing all music data before insert.")
                            audiobookDao.clearAllAudiobookDataWithCrossRefs()
                        }

                        val allExistingArtists =
                                if (syncMode == SyncMode.REBUILD) {
                                    emptyList()
                                } else {
                                    audiobookDao.getAllAuthorsRawOnce()
                                }

                        val existingArtistImageUrls =
                                allExistingArtists.associate { it.id to it.imageUrl }
                        
                        // Load all existing author IDs to ensure stability across incremental syncs
                        val existingArtistIdMap = allExistingArtists.associate { it.name to it.id }.toMutableMap()
                        val maxArtistId = audiobookDao.getMaxAuthorId() ?: 0L

                        // Prepare list of existing tracks to preserve user edits
                        // We only need to check against existing tracks if we are updating them
                        val localSongsMap =
                                if (syncMode != SyncMode.REBUILD) {
                                    audiobookDao.getAllTracksOnce().associateBy { it.id }
                                } else {
                                    emptyMap()
                                }

                        val songsToProcess = mediaStoreSongs

                        Timber.tag(TAG)
                            .i("Processing ${songsToProcess.size} tracks for upsert. Hash: ${songsToProcess.hashCode()}")

                        val songsToInsert =
                                songsToProcess.map { mediaStoreSong ->
                                    val localSong = localSongsMap[mediaStoreSong.id]
                                    if (localSong != null) {
                                        // Preserve user-edited fields
                                        val needsArtistCompare =
                                                !rescanRequired &&
                                                        localSong.authorName.isNotBlank() &&
                                                        localSong.authorName !=
                                                                mediaStoreSong.authorName

                                        val shouldPreserveArtistName =
                                                if (needsArtistCompare) {
                                                    val mediaStoreArtists =
                                                            mediaStoreSong.authorName
                                                                    .splitArtistsByDelimiters(
                                                                            authorDelimiters
                                                                    )
                                                    val mediaStorePrimaryArtist =
                                                            mediaStoreArtists.firstOrNull()?.trim()
                                                    val mediaStoreHasMultipleArtists =
                                                            mediaStoreArtists.size > 1
                                                    !(mediaStoreHasMultipleArtists &&
                                                            mediaStorePrimaryArtist != null &&
                                                            localSong.authorName.trim() ==
                                                                    mediaStorePrimaryArtist)
                                                } else {
                                                    false
                                                }

                                        mediaStoreSong.copy(
                                                dateAdded =
                                                        localSong.dateAdded, // Preserve original
                                                // date added if needed
                                                lyrics = localSong.lyrics,
                                                title =
                                                        if (localSong.title !=
                                                                        mediaStoreSong.title &&
                                                                        localSong.title.isNotBlank()
                                                        )
                                                                localSong.title
                                                        else mediaStoreSong.title,
                                                authorName =
                                                        if (shouldPreserveArtistName)
                                                                localSong.authorName
                                                        else mediaStoreSong.authorName,
                                                bookName =
                                                        if (localSong.bookName !=
                                                                        mediaStoreSong.bookName &&
                                                                        localSong.bookName
                                                                                .isNotBlank()
                                                        )
                                                                localSong.bookName
                                                        else mediaStoreSong.bookName,
                                                genre = localSong.genre ?: mediaStoreSong.genre,
                                                trackNumber =
                                                        if (localSong.trackNumber != 0 &&
                                                                        localSong.trackNumber !=
                                                                                mediaStoreSong
                                                                                        .trackNumber
                                                        )
                                                                localSong.trackNumber
                                                        else mediaStoreSong.trackNumber,
                                                bookArtUriString = localSong.bookArtUriString
                                                                ?: mediaStoreSong.bookArtUriString
                                        )
                                    } else {
                                        mediaStoreSong
                                    }
                                }

                        val (correctedSongs, books, authors, crossRefs) =
                                preProcessAndDeduplicateWithMultiArtist(
                                        tracks = songsToInsert,
                                        authorDelimiters = authorDelimiters,
                                        groupByAlbumArtist = groupByAlbumArtist,
                                        existingArtistImageUrls = existingArtistImageUrls,
                                        existingArtistIdMap = existingArtistIdMap,
                                        initialMaxArtistId = maxArtistId
                                )

                        // Use incrementalSyncAudiobookData for all modes except REBUILD
                        // Even for FULL sync, we can just upsert the values
                        if (syncMode == SyncMode.REBUILD) {
                            audiobookDao.insertAudiobookDataWithCrossRefs(
                                    correctedSongs,
                                    books,
                                    authors,
                                    crossRefs
                            )
                        } else {
                            // incrementalSyncAudiobookData handles upserts efficiently
                            // processing deleted tracks was already handled at the start
                            audiobookDao.incrementalSyncAudiobookData(
                                    tracks = correctedSongs,
                                    books = books,
                                    authors = authors,
                                    crossRefs = crossRefs,
                                    deletedTrackIds = emptyList() // Already handled
                            )
                        }

                        // Clear the rescan required flag
                        userPreferencesRepository.clearAuthorSettingsRescanRequired()

                        val endTime = System.currentTimeMillis()
                        Timber.tag(TAG)
                            .i("Synchronization finished successfully in ${endTime - startTime}ms.")
                        userPreferencesRepository.setLastSyncTimestamp(System.currentTimeMillis())

                        // Count total tracks for the output
                        val totalSongs = audiobookDao.getTrackCount().first()

                        // --- LRC SCANNING PHASE ---
                        val autoScanLrc = userPreferencesRepository.autoScanLrcFilesFlow.first()
                        if (autoScanLrc) {
                            Timber.tag(TAG).i("Auto-scan LRC files enabled. Starting scan phase...")

                            val allTracksEntities = audiobookDao.getAllTracksOnce()
                            val allTracks =
                                    allTracksEntities.map { entity ->
                                        Track(
                                                id = entity.id.toString(),
                                                title = entity.title,
                                                author = entity.authorName,
                                                authorId = entity.authorId,
                                                book = entity.bookName,
                                                bookId = entity.bookId,
                                                path = entity.filePath,
                                                contentUriString = entity.contentUriString,
                                                bookArtUriString = entity.bookArtUriString,
                                                duration = entity.duration,
                                                lyrics = entity.lyrics,
                                                dateAdded = entity.dateAdded,
                                                trackNumber = entity.trackNumber,
                                                year = entity.year,
                                                mimeType = entity.mimeType,
                                                bitrate = entity.bitrate,
                                                sampleRate = entity.sampleRate
                                        )
                                    }

                            val scannedCount =
                                    lyricsRepository.scanAndAssignLocalLrcFiles(allTracks) {
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

                            Log.i(TAG, "LRC Scan finished. Assigned lyrics to $scannedCount tracks.")
                        }
                        
                        // Clean orphaned book art cache files
                        val allSongIds = audiobookDao.getAllTrackIds().toSet()
                        BookArtCacheManager.cleanOrphanedCacheFiles(applicationContext, allSongIds)

                        Result.success(workDataOf(OUTPUT_TOTAL_SONGS to totalSongs))
                    } else {
                        Log.i(TAG, "No new or modified tracks found.")

                        // If it was a fresh install/rebuild and we found nothing, clear everything
                        if ((syncMode == SyncMode.REBUILD || isFreshInstall) &&
                                        mediaStoreSongs.isEmpty()
                        ) {
                            audiobookDao.clearAllAudiobookDataWithCrossRefs()
                            Log.w(
                                    TAG,
                                    "MediaStore fetch resulted in empty list. Local music data cleared."
                            )
                        }

                        val endTime = System.currentTimeMillis()
                        Log.i(
                                TAG,
                                "Synchronization (No Changes) finished in ${endTime - startTime}ms."
                        )
                        userPreferencesRepository.setLastSyncTimestamp(System.currentTimeMillis())

                        val totalSongs = audiobookDao.getTrackCount().first()
                        
                        // Clean orphaned book art cache files
                        val allSongIds = audiobookDao.getAllTrackIds().toSet()
                        BookArtCacheManager.cleanOrphanedCacheFiles(applicationContext, allSongIds)
                        
                        Result.success(workDataOf(OUTPUT_TOTAL_SONGS to totalSongs))
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error during MediaStore synchronization", e)
                    Result.failure()
                } finally {
                    Trace.endSection() // End SyncWorker.doWork
                }
            }

    /**
     * Efficiently fetches ONLY the IDs of all tracks in MediaStore. Used for fast deletion
     * detection.
     */
    private fun getBaseSelection(): Pair<String, Array<String>> {
        val selectionBuilder = StringBuilder()
        val selectionArgsList = mutableListOf<String>()

        selectionBuilder.append(
                "((${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= ?) "
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
     * Efficiently fetches ONLY the IDs of all tracks in MediaStore. Used for fast deletion
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

    /** Data class to hold the result of multi-author preprocessing. */
    private data class MultiArtistProcessResult(
            val tracks: List<TrackEntity>,
            val books: List<BookEntity>,
            val authors: List<AuthorEntity>,
            val crossRefs: List<TrackAuthorCrossRef>
    )

    /**
     * Process tracks with multi-author support. Splits author names by delimiters and creates proper
     * cross-references.
     */
    private fun preProcessAndDeduplicateWithMultiArtist(
            tracks: List<TrackEntity>,
            authorDelimiters: List<String>,
            groupByAlbumArtist: Boolean,
            existingArtistImageUrls: Map<Long, String?>,
            existingArtistIdMap: MutableMap<String, Long>,
            initialMaxArtistId: Long
    ): MultiArtistProcessResult {
        
        val nextArtistId = AtomicLong(initialMaxArtistId + 1)
        val authorNameToId = existingArtistIdMap // Re-use the map passed in
        
        val allCrossRefs = mutableListOf<TrackAuthorCrossRef>()
        val artistTrackCounts = mutableMapOf<Long, Int>()
        val albumMap = mutableMapOf<Pair<String, String>, Long>()
        val artistSplitCache = mutableMapOf<String, List<String>>()
        val correctedSongs = ArrayList<TrackEntity>(tracks.size)

        tracks.forEach { track ->
            val rawArtistName = track.authorNameName
            val songArtistNameTrimmed = rawArtistName.trim()
            val artistsForSong =
                    artistSplitCache.getOrPut(rawArtistName) {
                        rawArtistName.splitArtistsByDelimiters(authorDelimiters)
                    }

            artistsForSong.forEach { authorName ->
                val normalizedName = authorName.trim()
                if (normalizedName.isNotEmpty() && !authorNameToId.containsKey(normalizedName)) {
                     // Check if it's the track's primary author and we want to preserve that ID if possible?
                     // Actually, just generate new ID if not found in map.
                     val id = nextArtistId.getAndIncrement()
                     authorNameToId[normalizedName] = id
                }
            }
            
            val primaryArtistName =
                    artistsForSong.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }
                            ?: songArtistNameTrimmed
            val primaryArtistId = authorNameToId[primaryArtistName] ?: track.authorNameId

            artistsForSong.forEachIndexed { index, authorName ->
                val normalizedName = authorName.trim()
                val authorId = authorNameToId[normalizedName]
                if (authorId != null) {
                    val isPrimary = (index == 0) // First author is primary
                    allCrossRefs.add(
                            TrackAuthorCrossRef(
                                    trackId = track.id,
                                    authorId = authorId,
                                    isPrimary = isPrimary
                            )
                    )
                    artistTrackCounts[authorId] = (artistTrackCounts[authorId] ?: 0) + 1
                }
            }

            // --- Book Logic ---
            val rawAlbumName = track.bookName.trim()
            val bookIdentityArtist = if (groupByAlbumArtist) {
                track.bookAuthor?.trim()?.takeIf { it.isNotEmpty() } ?: primaryArtistName
            } else {
                primaryArtistName
            }
            
            val albumKey = rawAlbumName to bookIdentityArtist
            
            if (!albumMap.containsKey(albumKey)) {
                albumMap[albumKey] = track.bookId 
            }
            val finalAlbumId = albumMap[albumKey] ?: track.bookId // fallback

            correctedSongs.add(
                    track.copy(
                            authorId = primaryArtistId,
                            authorName = primaryArtistName,
                            bookId = finalAlbumId
                    )
            )
        }

        // Build Entities
        val artistEntities = authorNameToId.map { (name, id) ->
            val count = artistTrackCounts[id] ?: 0
            AuthorEntity(
                id = id,
                name = name,
                trackCount = count,
                imageUrl = existingArtistImageUrls[id]
            )
        }
        
        // Re-calculate Book Entities from the corrected tracks to ensure we have valid metadata (Art, Year)
        // which isn't available in the simple albumMap (which only has ID)
        val albumEntities = correctedSongs.groupBy { it.bookId }.map { (catAlbumId, songsInAlbum) ->
             val firstSong = songsInAlbum.first()
             // Determine Book Author Name
             val determinedAlbumArtist = firstSong.bookAuthor?.takeIf { it.isNotBlank() } 
                 ?: firstSong.authorName
                 
             // Determine Book Author ID (best effort lookup)
             val determinedAlbumArtistId = authorNameToId[determinedAlbumArtist] ?: 0L

             BookEntity(
                 id = catAlbumId,
                 title = firstSong.bookName,
                 authorName = determinedAlbumArtist,
                 authorId = determinedAlbumArtistId,
                 bookArtUriString = firstSong.bookArtUriString,
                 trackCount = songsInAlbum.size, 
                 year = firstSong.year
             )
        }

        return MultiArtistProcessResult(
                tracks = correctedSongs, // Corrected tracks have the right Book IDs now
                books = albumEntities,
                authors = artistEntities,
                crossRefs = allCrossRefs
        )
    }

    private fun fetchAlbumArtUrisByAlbumId(): Map<Long, String> {
        val projection = arrayOf(MediaStore.Audio.Albums._ID, MediaStore.Audio.Albums.ALBUM_ART)

        return buildMap {
            contentResolver.query(
                            MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
                            projection,
                            null,
                            null,
                            null
                    )
                    ?.use { cursor ->
                        val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Albums._ID)
                        val artCol = cursor.getColumnIndex(MediaStore.Audio.Albums.ALBUM_ART)
                        if (artCol >= 0) {
                            while (cursor.moveToNext()) {
                                val bookId = cursor.getLong(idCol)
                                val storedArtPath = cursor.getString(artCol)
                                val uriString =
                                        when {
                                            !storedArtPath.isNullOrBlank() ->
                                                    File(storedArtPath).toURI().toString()
                                            bookId > 0 ->
                                                    ContentUris.withAppendedId(
                                                                    "content://media/external/audio/albumart".toUri(),
                                                                    bookId
                                                            )
                                                            .toString()
                                            else -> null
                                        }

                                if (uriString != null) put(bookId, uriString)
                            }
                        }
                    }
        }
    }

    /**
     * Fetches a map of Track ID -> Genre Name using the MediaStore.Audio.Genres table. This is
     * necessary because the GENRE column in MediaStore.Audio.Media is not reliably available or
     * populated on all Android versions (especially pre-API 30).
     * 
     * Optimized: 
     * 1. Caches results for 1 hour to avoid refetching on incremental syncs
     * 2. Fetches all genres first, then queries members in parallel with controlled concurrency
     */
    private suspend fun fetchGenreMap(forceRefresh: Boolean = false): Map<Long, String> = coroutineScope {
        // Check cache first (valid for 1 hour)
        val now = System.currentTimeMillis()
        val cacheAge = now - genreMapCacheTimestamp
        if (!forceRefresh && genreMapCache.isNotEmpty() && cacheAge < GENRE_CACHE_TTL_MS) {
            Log.d(TAG, "Using cached genre map (${genreMapCache.size} entries, age: ${cacheAge/1000}s)")
            return@coroutineScope genreMapCache
        }
        
        val genreMap = ConcurrentHashMap<Long, String>()
        val genreProjection = arrayOf(MediaStore.Audio.Genres._ID, MediaStore.Audio.Genres.NAME)
        
        // Semaphore to limit concurrent queries (avoid overwhelming ContentResolver)
        val querySemaphore = Semaphore(4)

        try {
            // Step 1: Fetch all genres (single query)
            val genres = mutableListOf<Pair<Long, String>>()
            
            contentResolver.query(
                            MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI,
                            genreProjection,
                            null,
                            null,
                            null
                    )
                    ?.use { cursor ->
                        val idCol = cursor.getColumnIndex(MediaStore.Audio.Genres._ID)
                        val nameCol = cursor.getColumnIndex(MediaStore.Audio.Genres.NAME)

                        if (idCol >= 0 && nameCol >= 0) {
                            while (cursor.moveToNext()) {
                                val genreId = cursor.getLong(idCol)
                                val genreName = cursor.getString(nameCol)

                                if (!genreName.isNullOrBlank() &&
                                                !genreName.equals("unknown", ignoreCase = true)
                                ) {
                                    genres.add(genreId to genreName)
                                }
                            }
                        }
                    }
            
            // Step 2: Fetch members for each genre in parallel (controlled concurrency)
            genres.map { (genreId, genreName) ->
                async(Dispatchers.IO) {
                    querySemaphore.withPermit {
                        val membersUri =
                                MediaStore.Audio.Genres.Members.getContentUri(
                                        "external",
                                        genreId
                                )
                        val membersProjection =
                                arrayOf(MediaStore.Audio.Genres.Members.AUDIO_ID)

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
                                                    MediaStore.Audio.Genres.Members.AUDIO_ID
                                            )
                                    if (audioIdCol >= 0) {
                                        while (membersCursor.moveToNext()) {
                                            val audioId = membersCursor.getLong(audioIdCol)
                                            // If a track has multiple genres, the last one processed wins.
                                            // This is acceptable as a primary genre for display.
                                            genreMap[audioId] = genreName
                                        }
                                    }
                                }
                    }
                }
            }.awaitAll()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching genre map", e)
        }
        
        // Update cache
        if (genreMap.isNotEmpty()) {
            genreMapCache = genreMap.toMap()
            genreMapCacheTimestamp = System.currentTimeMillis()
            Log.d(TAG, "Genre map cache updated with ${genreMap.size} entries")
        }
        
        genreMap
    }

    /** Raw data extracted from cursor - lightweight class for fast iteration */
    private data class RawSongData(
            val id: Long,
            val bookId: Long,
            val authorId: Long,
            val filePath: String,
            val title: String,
            val author: String,
            val book: String,
            val bookAuthor: String?,
            val duration: Long,
            val trackNumber: Int,
            val year: Int,
            val dateModified: Long
    )

    private suspend fun fetchMusicFromMediaStore(
            sinceTimestamp: Long, // Seconds
            forceMetadata: Boolean,
            directoryResolver: DirectoryRuleResolver,
            progressBatchSize: Int,
            onProgress: suspend (current: Int, total: Int, phaseOrdinal: Int) -> Unit
    ): List<TrackEntity> {
        Trace.beginSection("SyncWorker.fetchMusicFromMediaStore")

        val deepScan = forceMetadata
        val bookArtByAlbumId = if (!deepScan) fetchAlbumArtUrisByAlbumId() else emptyMap()
        val genreMap = fetchGenreMap() // Load genres upfront

        val projection =
                arrayOf(
                        MediaStore.Audio.Media._ID,
                        MediaStore.Audio.Media.TITLE,
                        MediaStore.Audio.Media.ARTIST,
                        MediaStore.Audio.Media.ARTIST_ID,
                        MediaStore.Audio.Media.ALBUM,
                        MediaStore.Audio.Media.ALBUM_ID,
                        MediaStore.Audio.Media.ALBUM_ARTIST,
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
        val rawDataList = mutableListOf<RawSongData>()

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
                    val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                    val authorIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST_ID)
                    val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                    val bookIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                    val bookArtistCol = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ARTIST)
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

                        val trackId = cursor.getLong(idCol)
                        rawDataList.add(
                                RawSongData(
                                        id = cursor.getLong(idCol),
                                        bookId = cursor.getLong(bookIdCol),
                                        authorId = cursor.getLong(authorIdCol),
                                        filePath = cursor.getString(dataCol) ?: "",
                                        title =
                                                cursor.getString(titleCol)
                                                        .normalizeMetadataTextOrEmpty()
                                                        .ifEmpty { "Unknown Title" },
                                        author =
                                                cursor.getString(artistCol)
                                                        .normalizeMetadataTextOrEmpty()
                                                        .ifEmpty { "Unknown Author" },
                                        book =
                                                cursor.getString(albumCol)
                                                        .normalizeMetadataTextOrEmpty()
                                                        .ifEmpty { "Unknown Book" },
                                        bookAuthor =
                                                if (bookArtistCol >= 0)
                                                        cursor.getString(bookArtistCol)
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

        // Phase 2: Parallel processing of tracks
        val processedCount = AtomicInteger(0)
        val concurrencyLimit = 8 // Limit parallel operations to prevent resource exhaustion
        val semaphore = Semaphore(concurrencyLimit)

        val tracks = coroutineScope {
            rawDataList
                    .map { raw ->
                        async {
                            semaphore.withPermit {
                                val track =
                                        processSongData(raw, bookArtByAlbumId, genreMap, deepScan)

                                // Report progress
                                val count = processedCount.incrementAndGet()
                                if (count % progressBatchSize == 0 || count == totalCount) {
                                    onProgress(
                                            count,
                                            totalCount,
                                            SyncProgress.SyncPhase.FETCHING_MEDIASTORE.ordinal
                                    )
                                }

                                track
                            }
                        }
                    }
                    .awaitAll()
        }

        Trace.endSection()
        return tracks
    }

    /**
     * Process a single track's raw data into a TrackEntity. This is the CPU/IO intensive work that
     * benefits from parallelization.
     */
    private suspend fun processSongData(
            raw: RawSongData,
            bookArtByAlbumId: Map<Long, String>,
            genreMap: Map<Long, String>,
            deepScan: Boolean
    ): TrackEntity {
        val parentDir = java.io.File(raw.filePath).parent ?: ""
        val contentUriString =
                ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, raw.id)
                        .toString()

        var bookArtUriString = bookArtByAlbumId[raw.bookId]
        if (deepScan) {
            bookArtUriString =
                    BookArtUtils.getBookArtUri(
                            applicationContext,
                            audiobookDao,
                            raw.filePath,
                            raw.bookId,
                            raw.id,
                            true
                    )
                            ?: bookArtUriString
        }
        val audioMetadata =
                if (deepScan) getAudioMetadata(audiobookDao, raw.id, raw.filePath, true) else null

        var title = raw.title
        var author = raw.authorName
        var book = raw.book
        var trackNumber = raw.trackNumber
        var year = raw.year
        var genre: String? = genreMap[raw.id] // Use mapped genre as default

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
                        if (!meta.author.isNullOrBlank()) author = meta.authorName
                        if (!meta.book.isNullOrBlank()) book = meta.book
                        if (!meta.genre.isNullOrBlank()) genre = meta.genre
                        if (meta.trackNumber != null) trackNumber = meta.trackNumber
                        if (meta.year != null) year = meta.year

                        meta.artwork?.let { art ->
                            val uri =
                                    BookArtUtils.saveAlbumArtToCache(
                                            applicationContext,
                                            art.bytes,
                                            raw.id
                                    )
                            bookArtUriString = uri.toString()
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
                authorName = author,
                authorId = raw.authorId,
                bookAuthor = raw.bookAuthor,
                bookName = book,
                bookId = raw.bookId,
                contentUriString = contentUriString,
                bookArtUriString = bookArtUriString,
                duration = raw.duration,
                genre = genre,
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
        const val OUTPUT_TOTAL_SONGS = "output_total_songs"
        
        // Genre cache - shared across worker instances to avoid refetching on incremental syncs
        private const val GENRE_CACHE_TTL_MS = 60 * 60 * 1000L // 1 hour
        @Volatile private var genreMapCache: Map<Long, String> = emptyMap()
        @Volatile private var genreMapCacheTimestamp: Long = 0L
        
        fun invalidateGenreCache() {
            genreMapCache = emptyMap()
            genreMapCacheTimestamp = 0L
            Log.d(TAG, "Genre cache invalidated")
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