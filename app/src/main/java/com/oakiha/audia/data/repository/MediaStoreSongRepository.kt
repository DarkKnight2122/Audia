package com.oakiha.audia.data.repository

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import android.util.Log
import com.oakiha.audia.data.database.FavoritesDao
import com.oakiha.audia.data.model.Track
import com.oakiha.audia.data.observer.MediaStoreObserver
import com.oakiha.audia.data.preferences.UserPreferencesRepository
import com.oakiha.audia.utils.DirectoryRuleResolver
import com.oakiha.audia.utils.LogUtils
import com.oakiha.audia.utils.normalizeMetadataText
import com.oakiha.audia.utils.normalizeMetadataTextOrEmpty
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaStoreTrackRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaStoreObserver: MediaStoreObserver,
    private val favoritesDao: FavoritesDao,
    private val userPreferencesRepository: UserPreferencesRepository
) : TrackRepository {

    init {
        mediaStoreObserver.register()
    }

    private fun getBaseSelection(): String {
        // Relaxed filter: Remove IS_Audiobook to include all audio strings (WhatsApp, Recs, etc.)
        // We filter by duration to skip extremely short clips (likely UI sounds).
        return "${MediaStore.Audio.Media.DURATION} >= 30000 AND ${MediaStore.Audio.Media.TITLE} != ''"
    }

    private suspend fun getFavoriteIds(): Set<Long> {
        return favoritesDao.getFavoriteTrackIdsOnce().toSet()
    }

    private fun normalizePath(path: String): String = File(path).absolutePath

    private fun getExcludedPaths(): Set<String> {
        // This should come from a repository/store, not blocking flow preferably, 
        // but for query implementation we'll need to filter the cursor results.
        // For now, we will assume strict filtering logic inside mapCursorToTracks
        return emptySet() 
    }

    override fun getTracks(): Flow<List<Track>> = combine(
        mediaStoreObserver.mediaStoreChanges.onStart { emit(Unit) },
        favoritesDao.getFavoriteTrackIds(),
        userPreferencesRepository.allowedDirectoriesFlow,
        userPreferencesRepository.blockedDirectoriesFlow
    ) { _, favoriteIds, allowedDirs, blockedDirs ->
        // Triggered by mediaStore change or favorites change or directory config change
        fetchTracksFromMediaStore(favoriteIds.toSet(), allowedDirs.toList(), blockedDirs.toList())
    }.flowOn(Dispatchers.IO)

    private suspend fun fetchTracksFromMediaStore(
        favoriteIds: Set<Long>,
        allowedDirs: List<String>,
        blockedDirs: List<String>
    ): List<Track> = withContext(Dispatchers.IO) {
        val Tracks = mutableListOf<Track>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.Author,
            MediaStore.Audio.Media.Author_ID,
            MediaStore.Audio.Media.Book,
            MediaStore.Audio.Media.Book_ID,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DATE_MODIFIED,
            MediaStore.Audio.Media.Book_Author, // Valid on API 30+, fallback needed if minSdk < 30
            // Category is difficult in MediaStore.Audio.Media, usually requires separate query.
            // keeping it simple for now, maybe null or fetch separately.
        )
        
        // Handling API version differences for columns if necessary
        // Assuming minSdk is high enough or columns exist (Book_Author is API 30+, need check if app supports lower)
        
        val selection = getBaseSelection()

        val TrackIdToCategoryMap = getTrackIdToCategoryMap(context.contentResolver)

        try {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                "${MediaStore.Audio.Media.TITLE} ASC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val AuthorCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.Author)
                val AuthorIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.Author_ID)
                val BookCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.Book)
                val BookIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.Book_ID)
                val pathCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val trackCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
                val yearCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
                val dateAddedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                val dateModifiedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
                val BookAuthorCol = cursor.getColumnIndex(MediaStore.Audio.Media.Book_Author) // Can be -1

                val resolver = DirectoryRuleResolver(
                    allowedDirs.map(::normalizePath).toSet(),
                    blockedDirs.map(::normalizePath).toSet() 
                )
                val isFilterActive = blockedDirs.isNotEmpty()

                while (cursor.moveToNext()) {
                    val path = cursor.getString(pathCol)
                    
                    // Directory Filtering
                    if (isFilterActive) {
                        val lastSlashIndex = path.lastIndexOf('/')
                        val parentPath = if (lastSlashIndex != -1) path.substring(0, lastSlashIndex) else ""
                        if (resolver.isBlocked(parentPath)) {
                            continue
                        }
                    }

                    val id = cursor.getLong(idCol)
                    val BookId = cursor.getLong(BookIdCol)
                    
                    val Track = Track(
                        id = id.toString(),
                        title = cursor.getString(titleCol).normalizeMetadataTextOrEmpty(),
                        Author = cursor.getString(AuthorCol).normalizeMetadataTextOrEmpty(),
                        AuthorId = cursor.getLong(AuthorIdCol),
                        Authors = emptyList(), // TODO: Secondary query for Multi-Author or split string
                        Book = cursor.getString(BookCol).normalizeMetadataTextOrEmpty(),
                        BookId = BookId,
                        BookAuthor = if (BookAuthorCol != -1) cursor.getString(BookAuthorCol).normalizeMetadataText() else null,
                        path = path,
                        contentUriString = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id).toString(),
                        BookArtUriString = ContentUris.withAppendedId(
                            android.net.Uri.parse("content://media/external/audio/Bookart"),
                            BookId
                        ).toString(),
                        duration = cursor.getLong(durationCol),
                        Category = TrackIdToCategoryMap[id],
                        Transcript = null,
                        isFavorite = favoriteIds.contains(id),
                        trackNumber = cursor.getInt(trackCol),
                        year = cursor.getInt(yearCol),
                        dateAdded = cursor.getLong(dateAddedCol),
                        dateModified = cursor.getLong(dateModifiedCol),
                        mimeType = null, 
                        bitrate = null,
                        sampleRate = null
                    )
                    Tracks.add(Track)
                }
            }
        } catch (e: Exception) {
            Log.e("MediaStoreTrackRepository", "Error querying MediaStore", e)
        }
        Tracks
    }

    private fun getTrackIdToCategoryMap(contentResolver: android.content.ContentResolver): Map<Long, String> {
        val CategoryMap = mutableMapOf<Long, String>()
        try {
            val CategoriesUri = MediaStore.Audio.Categories.EXTERNAL_CONTENT_URI
            val CategoriesProjection = arrayOf(
                MediaStore.Audio.Categories._ID,
                MediaStore.Audio.Categories.NAME
            )
            
            contentResolver.query(CategoriesUri, CategoriesProjection, null, null, null)?.use { CategoryCursor ->
                val CategoryIdCol = CategoryCursor.getColumnIndexOrThrow(MediaStore.Audio.Categories._ID)
                val CategoryNameCol = CategoryCursor.getColumnIndexOrThrow(MediaStore.Audio.Categories.NAME)
                
                while (CategoryCursor.moveToNext()) {
                    val CategoryId = CategoryCursor.getLong(CategoryIdCol)
                    val CategoryName = CategoryCursor.getString(CategoryNameCol).normalizeMetadataTextOrEmpty()
                    
                    if (CategoryName.isNotBlank() && CategoryName != "<unknown>") {
                        val membersUri = MediaStore.Audio.Categories.Members.getContentUri("external", CategoryId)
                        val membersProjection = arrayOf(MediaStore.Audio.Categories.Members.AUDIO_ID)
                        
                        try {
                            contentResolver.query(membersUri, membersProjection, null, null, null)?.use { membersCursor ->
                                val audioIdCol = membersCursor.getColumnIndex(MediaStore.Audio.Categories.Members.AUDIO_ID)
                                if (audioIdCol != -1) {
                                    while (membersCursor.moveToNext()) {
                                        val TrackId = membersCursor.getLong(audioIdCol)
                                        // If a Track has multiple Categories, this simple map keeps the last one found.
                                        // Could be improved to join them if needed.
                                        CategoryMap[TrackId] = CategoryName 
                                    }
                                }
                            }
                        } catch (e: Exception) {
                             Log.w("MediaStoreTrackRepository", "Error querying members for CategoryId=$CategoryId", e)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("MediaStoreTrackRepository", "Error querying Categories", e)
        }
        return CategoryMap
    }

    override fun getTracksByBook(BookId: Long): Flow<List<Track>> {
         // Reusing getTracks() and filtering might be inefficient for one Book, 
         // but consistent with the reactive source of truth.
         // Optimization: Create specific query flow if needed.
         return getTracks().flowOn(Dispatchers.IO).combine(kotlinx.coroutines.flow.flowOf(BookId)) { Tracks, id ->
             Tracks.filter { it.BookId == id }
         }
    }

    override fun getTracksByAuthor(AuthorId: Long): Flow<List<Track>> {
        return getTracks().flowOn(Dispatchers.IO).combine(kotlinx.coroutines.flow.flowOf(AuthorId)) { Tracks, id ->
            Tracks.filter { it.AuthorId == id }
        }
    }

    override suspend fun searchTracks(query: String): List<Track> {
        val allTracks = getTracks().first() // Snapshot
        return allTracks.filter { 
            it.title.contains(query, true) || it.Author.contains(query, true) 
        }
    }

    override fun getTrackById(TrackId: Long): Flow<Track?> {
        return getTracks().flowOn(Dispatchers.IO).combine(kotlinx.coroutines.flow.flowOf(TrackId)) { Tracks, id ->
            Tracks.find { it.id == id.toString() }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getPaginatedTracks(): Flow<androidx.paging.PagingData<Track>> {
        return combine(
            mediaStoreObserver.mediaStoreChanges.onStart { emit(Unit) },
            userPreferencesRepository.allowedDirectoriesFlow,
            userPreferencesRepository.blockedDirectoriesFlow
        ) { _, allowedDirs, blockedDirs ->
            Triple(allowedDirs, blockedDirs, Unit)
        }.flatMapLatest { (allowedDirs, blockedDirs, _) ->
             val AudiobookIds = getFilteredTrackIds(allowedDirs.toList(), blockedDirs.toList())
             val CategoryMap = getTrackIdToCategoryMap(context.contentResolver) // Potentially expensive, optimize if needed
             
             androidx.paging.Pager(
                 config = androidx.paging.PagingConfig(
                     pageSize = 50,
                     enablePlaceholders = true,
                     initialLoadSize = 50
                 ),
                 pagingSourceFactory = {
                     com.oakiha.audia.data.paging.MediaStorePagingSource(context, AudiobookIds, CategoryMap)
                 }
             ).flow
        }.flowOn(Dispatchers.IO)
    }

    private suspend fun getFilteredTrackIds(allowedDirs: List<String>, blockedDirs: List<String>): List<Long> = withContext(Dispatchers.IO) {
        val ids = mutableListOf<Long>()
        val projection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DATA)
        val selection = getBaseSelection()
        
        try {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                "${MediaStore.Audio.Media.TITLE} ASC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val pathCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                
                val resolver = DirectoryRuleResolver(
                    allowedDirs.map(::normalizePath).toSet(),
                    blockedDirs.map(::normalizePath).toSet()
                )
                val isFilterActive = blockedDirs.isNotEmpty()

                while (cursor.moveToNext()) {
                    val path = cursor.getString(pathCol)
                    if (isFilterActive) {
                    if (isFilterActive) {
                        val lastSlashIndex = path.lastIndexOf('/')
                        val parentPath = if (lastSlashIndex != -1) path.substring(0, lastSlashIndex) else ""
                        if (resolver.isBlocked(parentPath)) {
                            continue
                        }
                    }
                    }
                    ids.add(cursor.getLong(idCol))
                }
            }
        } catch (e: Exception) {
            Log.e("MediaStoreTrackRepository", "Error getting IDs", e)
        }
        ids
    }
}
