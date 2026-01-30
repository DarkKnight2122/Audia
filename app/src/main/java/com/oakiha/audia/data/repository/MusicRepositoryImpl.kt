package com.oakiha.audia.data.repository

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.oakiha.audia.data.model.Track
import com.oakiha.audia.data.database.AudiobookDao
import com.oakiha.audia.data.database.FavoritesDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
// import kotlinx.coroutines.withContext // May not be needed for Flow transformations
import javax.inject.Inject
import javax.inject.Singleton
import androidx.core.net.toUri
import com.oakiha.audia.data.model.Book
import com.oakiha.audia.data.database.SearchHistoryDao
import com.oakiha.audia.data.database.SearchHistoryEntity
import com.oakiha.audia.data.database.toSearchHistoryItem
import com.oakiha.audia.data.model.Author
import com.oakiha.audia.data.model.Booklist
import com.oakiha.audia.data.model.SearchFilterType
import com.oakiha.audia.data.model.SearchHistoryItem
import com.oakiha.audia.data.model.SearchResultItem
import com.oakiha.audia.data.model.SortOption
import com.oakiha.audia.data.preferences.UserPreferencesRepository
import androidx.sqlite.db.SimpleSQLiteQuery

import com.oakiha.audia.data.model.Category
import com.oakiha.audia.data.database.TrackEntity
import com.oakiha.audia.data.database.TrackAuthorCrossRef
import com.oakiha.audia.data.database.AuthorEntity
import com.oakiha.audia.data.database.toBook
import com.oakiha.audia.data.database.toAuthor
import com.oakiha.audia.data.database.toTrack
import com.oakiha.audia.data.database.toTrackWithAuthorRefs
import com.oakiha.audia.data.model.Transcript
import com.oakiha.audia.data.model.TranscriptSourcePreference
import com.oakiha.audia.data.model.SyncedLine
import com.oakiha.audia.utils.LogUtils
import com.oakiha.audia.data.model.AudiobookFolder
import com.oakiha.audia.utils.TranscriptUtils
import com.oakiha.audia.utils.DirectoryRuleResolver
import kotlinx.coroutines.flow.conflate
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first // Still needed for initialSetupDoneFlow.first() if used that way
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
// import kotlinx.coroutines.sync.withLock // May not be needed if directoryScanMutex logic changes
import java.io.File
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import androidx.paging.filter

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class AudiobookRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val searchHistoryDao: SearchHistoryDao,
    private val AudiobookDao: AudiobookDao,
    private val TranscriptRepository: TranscriptRepository,
    private val TrackRepository: TrackRepository,
    private val favoritesDao: FavoritesDao
) : AudiobookRepository {

    private val directoryScanMutex = Mutex()

    private fun normalizePath(path: String): String =
        runCatching { File(path).canonicalPath }.getOrElse { File(path).absolutePath }



    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getAudioFiles(): Flow<List<Track>> {
        // Delegate to the reactive TrackRepository which queries MediaStore directly
        // and observes directory preference changes in real-time.
        return TrackRepository.getTracks()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getPaginatedTracks(): Flow<PagingData<Track>> {
       // Delegate to reactive repository for correct filtering and paging
       return TrackRepository.getPaginatedTracks()
    }

    override fun getTrackCountFlow(): Flow<Int> {
        return AudiobookDao.getTrackCount()
    }

    override suspend fun getRandomTracks(limit: Int): List<Track> = withContext(Dispatchers.IO) {
        AudiobookDao.getRandomTracks(limit).map { it.toTrack() }
    }

    override fun getBooks(): Flow<List<Book>> {
        return getAudioFiles().map { Tracks ->
            Tracks.groupBy { it.BookId }
                .map { (BookId, Tracks) ->
                    val first = Tracks.first()
                    Book(
                        id = BookId,
                        title = first.Book,
                        Author = first.Author, // Or BookAuthor if available
                        BookArtUriString = first.BookArtUriString,
                        TrackCount = Tracks.size,
                        year = first.year
                    )
                }
                .sortedBy { it.title.lowercase() }
        }.flowOn(Dispatchers.Default)
    }

    override fun getBookById(id: Long): Flow<Book?> {
        return getBooks().map { Books -> 
            Books.find { it.id == id }
        }
    }

    override fun getAuthors(): Flow<List<Author>> {
        return getAudioFiles().map { Tracks ->
            Tracks.groupBy { it.AuthorId }
                .map { (AuthorId, Tracks) ->
                    val first = Tracks.first()
                    Author(
                        id = AuthorId,
                        name = first.Author,
                        TrackCount = Tracks.size
                    )
                }
                .sortedBy { it.name.lowercase() }
        }.flowOn(Dispatchers.Default)
    }

    override fun getTracksForBook(BookId: Long): Flow<List<Track>> {
        return getAudioFiles().map { Tracks ->
            Tracks.filter { it.BookId == BookId }
                .sortedBy { it.trackNumber }
        }
    }

    override fun getAuthorById(AuthorId: Long): Flow<Author?> {
         return getAuthors().map { Authors ->
             Authors.find { it.id == AuthorId }
         }
    }

    override fun getAuthorsForTrack(TrackId: Long): Flow<List<Author>> {
        // Simple implementation assuming single Author per Track as per MediaStore
        // For multi-Author, we would parse the separator/delimiter here.
        return getAudioFiles().map { Tracks ->
            val Track = Tracks.find { it.id == TrackId.toString() }
            if (Track != null) {
                listOf(Author(id = Track.AuthorId, name = Track.Author, TrackCount = 1, imageUrl = null))
            } else {
                emptyList()
            }
        }
    }

    override fun getTracksForAuthor(AuthorId: Long): Flow<List<Track>> {
        return getAudioFiles().map { Tracks ->
            Tracks.filter { it.AuthorId == AuthorId }
                .sortedBy { it.title }
        }
    }

    override suspend fun getAllUniqueAudioDirectories(): Set<String> = withContext(Dispatchers.IO) {
        LogUtils.d(this, "getAllUniqueAudioDirectories")
        directoryScanMutex.withLock {
            val directories = mutableSetOf<String>()
            val projection = arrayOf(MediaStore.Audio.Media.DATA)
            val selection = "(${MediaStore.Audio.Media.IS_Audiobook} != 0 OR ${MediaStore.Audio.Media.DATA} LIKE '%.m4a' OR ${MediaStore.Audio.Media.DATA} LIKE '%.flac')"
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection, selection, null, null
            )?.use { c ->
                val dataColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                while (c.moveToNext()) {
                    File(c.getString(dataColumn)).parent?.let { directories.add(it) }
                }
            }
            LogUtils.i(this, "Found ${directories.size} unique audio directories")
            return@withLock directories
        }
    }

    override fun getAllUniqueBookArtUris(): Flow<List<Uri>> {
        return AudiobookDao.getAllUniqueBookArtUrisFromTracks().map { uriStrings ->
            uriStrings.mapNotNull { it.toUri() }
        }.flowOn(Dispatchers.IO)
    }

    // --- MÃƒÂ©todos de BÃƒÂºsqueda ---

    override fun searchTracks(query: String): Flow<List<Track>> {
        if (query.isBlank()) return flowOf(emptyList())
        // Passing emptyList and false for directory filter as we trust SSOT (SyncWorker filtered)
        return AudiobookDao.searchTracks(query, emptyList(), false).map { entities ->
            entities.map { it.toTrack() }
        }.flowOn(Dispatchers.IO)
    }


    override fun searchBooks(query: String): Flow<List<Book>> {
       if (query.isBlank()) return flowOf(emptyList())
       return AudiobookDao.searchBooks(query, emptyList(), false).map { entities ->
           entities.map { it.toBook() }
       }.flowOn(Dispatchers.IO)
    }

    override fun searchAuthors(query: String): Flow<List<Author>> {
        if (query.isBlank()) return flowOf(emptyList())
        return AudiobookDao.searchAuthors(query, emptyList(), false).map { entities ->
            entities.map { it.toAuthor() }
        }.flowOn(Dispatchers.IO)
    }

    override suspend fun searchBooklists(query: String): List<Booklist> {
        if (query.isBlank()) return emptyList()
        return userPreferencesRepository.userBooklistsFlow.first()
            .filter { Booklist ->
                Booklist.name.contains(query, ignoreCase = true)
            }
    }

    override fun searchAll(query: String, filterType: SearchFilterType): Flow<List<SearchResultItem>> {
        if (query.isBlank()) return flowOf(emptyList())
        val BooklistsFlow = flow { emit(searchBooklists(query)) }

        return when (filterType) {
            SearchFilterType.ALL -> {
                combine(
                    searchTracks(query),
                    searchBooks(query),
                    searchAuthors(query),
                    BooklistsFlow
                ) { Tracks, Books, Authors, Booklists ->
                    mutableListOf<SearchResultItem>().apply {
                        Tracks.forEach { add(SearchResultItem.TrackItem(it)) }
                        Books.forEach { add(SearchResultItem.BookItem(it)) }
                        Authors.forEach { add(SearchResultItem.AuthorItem(it)) }
                        Booklists.forEach { add(SearchResultItem.BooklistItem(it)) }
                    }
                }
            }
            SearchFilterType.Tracks -> searchTracks(query).map { Tracks -> Tracks.map { SearchResultItem.TrackItem(it) } }
            SearchFilterType.Books -> searchBooks(query).map { Books -> Books.map { SearchResultItem.BookItem(it) } }
            SearchFilterType.Authors -> searchAuthors(query).map { Authors -> Authors.map { SearchResultItem.AuthorItem(it) } }
            SearchFilterType.Booklists -> BooklistsFlow.map { Booklists -> Booklists.map { SearchResultItem.BooklistItem(it) } }
        }.flowOn(Dispatchers.Default)
    }

    override suspend fun addSearchHistoryItem(query: String) {
        withContext(Dispatchers.IO) {
            searchHistoryDao.deleteByQuery(query)
            searchHistoryDao.insert(SearchHistoryEntity(query = query, timestamp = System.currentTimeMillis()))
        }
    }

    override suspend fun getRecentSearchHistory(limit: Int): List<SearchHistoryItem> {
        return withContext(Dispatchers.IO) {
            searchHistoryDao.getRecentSearches(limit).map { it.toSearchHistoryItem() }
        }
    }

    override suspend fun deleteSearchHistoryItemByQuery(query: String) {
        withContext(Dispatchers.IO) {
            searchHistoryDao.deleteByQuery(query)
        }
    }

    override suspend fun clearSearchHistory() {
        withContext(Dispatchers.IO) {
            searchHistoryDao.clearAll()
        }
    }

    override fun getAudiobookByCategory(CategoryId: String): Flow<List<Track>> {
        return userPreferencesRepository.mockCategoriesEnabledFlow.flatMapLatest { mockEnabled ->
            if (mockEnabled) {
                // Mock mode: Use the static Category name for filtering.
                val CategoryName = "Mock"//CategoryDataSource.getStaticCategories().find { it.id.equals(CategoryId, ignoreCase = true) }?.name ?: CategoryId
                getAudioFiles().map { Tracks ->
                    Tracks.filter { it.Category.equals(CategoryName, ignoreCase = true) }
                }
            } else {
                // Real mode: Use the CategoryId directly, which corresponds to the actual Category name from metadata.
                getAudioFiles().map { Tracks ->
                    if (CategoryId.equals("unknown", ignoreCase = true)) {
                        // Filter for Tracks with no Category or an empty Category string.
                        Tracks.filter { it.Category.isNullOrBlank() }
                    } else {
                        // Filter for Tracks that match the given Category name.
                        Tracks.filter { it.Category.equals(CategoryId, ignoreCase = true) }
                    }
                }
            }
        }.flowOn(Dispatchers.IO)
    }

    override fun getTracksByIds(TrackIds: List<String>): Flow<List<Track>> {
        if (TrackIds.isEmpty()) return flowOf(emptyList())
        return TrackRepository.getTracks().map { Tracks ->
             val TracksMap = Tracks.associateBy { it.id }
             TrackIds.mapNotNull { TracksMap[it] }
        }.flowOn(Dispatchers.Default)
    }

    override suspend fun getTrackByPath(path: String): Track? {
        return withContext(Dispatchers.IO) {
            AudiobookDao.getTrackByPath(path)?.toTrack()
        }
    }

    override suspend fun invalidateCachesDependentOnAllowedDirectories() {
        Log.i("AudiobookRepo", "invalidateCachesDependentOnAllowedDirectories called. Reactive flows will update automatically.")
    }

    suspend fun syncAudiobookFromContentResolver() {
        // Esta funciÃƒÂ³n ahora estÃƒÂ¡ en SyncWorker. Se deja el esqueleto por si se llama desde otro lugar.
        Log.w("AudiobookRepo", "syncAudiobookFromContentResolver was called directly on repository. This should be handled by SyncWorker.")
    }

    // ImplementaciÃƒÂ³n de las nuevas funciones suspend para carga ÃƒÂºnica
    override suspend fun getAllTracksOnce(): List<Track> = withContext(Dispatchers.IO) {
        AudiobookDao.getAllTracksList().map { it.toTrack() }
    }

    override suspend fun getAllBooksOnce(): List<Book> = withContext(Dispatchers.IO) {
        AudiobookDao.getAllBooksList(emptyList(), false).map { it.toBook() }
    }

    override suspend fun getAllAuthorsOnce(): List<Author> = withContext(Dispatchers.IO) {
        AudiobookDao.getAllAuthorsListRaw().map { it.toAuthor() }
    }

    override suspend fun toggleFavoriteStatus(TrackId: String): Boolean = withContext(Dispatchers.IO) {
        val id = TrackId.toLongOrNull() ?: return@withContext false
        val isFav = favoritesDao.isFavorite(id) ?: false
        val newFav = !isFav
        if (newFav) {
            favoritesDao.setFavorite(com.oakiha.audia.data.database.FavoritesEntity(id, true))
        } else {
            favoritesDao.removeFavorite(id)
        }
        return@withContext newFav
    }

    override fun getTrack(TrackId: String): Flow<Track?> {
        val id = TrackId.toLongOrNull() ?: return flowOf(null)
        return AudiobookDao.getTrackById(id).map { it?.toTrack() }.flowOn(Dispatchers.IO)
    }

    override fun getCategories(): Flow<List<Category>> {
        return getAudioFiles().map { Tracks ->
            val CategoriesMap = Tracks.groupBy { Track ->
                Track.Category?.trim()?.takeIf { it.isNotBlank() } ?: "Unknown"
            }

            val dynamicCategories = CategoriesMap.keys.mapNotNull { CategoryName ->
                val id = if (CategoryName.equals("Unknown", ignoreCase = true)) "unknown" else CategoryName.lowercase().replace(" ", "_")
                // Generate colors dynamically or use a default for "Unknown"
                val colorInt = CategoryName.hashCode()
                val lightColorHex = "#${(colorInt and 0x00FFFFFF).toString(16).padStart(6, '0').uppercase()}"
                // Simple inversion for dark color, or use a predefined set
                val darkColorHex = "#${((colorInt xor 0xFFFFFF) and 0x00FFFFFF).toString(16).padStart(6, '0').uppercase()}"

                Category(
                    id = id,
                    name = CategoryName,
                    lightColorHex = lightColorHex,
                    onLightColorHex = "#000000", // Default black for light theme text
                    darkColorHex = darkColorHex,
                    onDarkColorHex = "#FFFFFF"  // Default white for dark theme text
                )
            }.sortedBy { it.name.lowercase() }

            // Ensure "Unknown" Category is last if it exists.
            val unknownCategory = dynamicCategories.find { it.id == "unknown" }
            if (unknownCategory != null) {
                (dynamicCategories.filterNot { it.id == "unknown" } + unknownCategory)
            } else {
                dynamicCategories
            }
        }.conflate().flowOn(Dispatchers.IO)
    }

    override suspend fun getTranscript(
        Track: Track,
        sourcePreference: TranscriptSourcePreference,
        forceRefresh: Boolean
    ): Transcript? {
        return TranscriptRepository.getTranscript(Track, sourcePreference, forceRefresh)
    }

    /**
     * Obtiene la letra de una canciÃƒÂ³n desde la API de LRCLIB, la persiste en la base de datos
     * y la devuelve como un objeto Transcript parseado.
     *
     * @param Track La canciÃƒÂ³n para la cual se buscarÃƒÂ¡ la letra.
     * @return Un objeto Result que contiene el objeto Transcript si se encontrÃƒÂ³, o un error.
     */
    override suspend fun getTranscriptFromRemote(Track: Track): Result<Pair<Transcript, String>> {
        return TranscriptRepository.fetchFromRemote(Track)
    }

    override suspend fun searchRemoteTranscript(Track: Track): Result<Pair<String, List<TranscriptSearchResult>>> {
        return TranscriptRepository.searchRemote(Track)
    }

    override suspend fun searchRemoteTranscriptByQuery(title: String, Author: String?): Result<Pair<String, List<TranscriptSearchResult>>> {
        return TranscriptRepository.searchRemoteByQuery(title, Author)
    }

    override suspend fun updateTranscript(TrackId: Long, Transcript: String) {
        TranscriptRepository.updateTranscript(TrackId, Transcript)
    }

    override suspend fun resetTranscript(TrackId: Long) {
        TranscriptRepository.resetTranscript(TrackId)
    }

    override suspend fun resetAllTranscript() {
        TranscriptRepository.resetAllTranscript()
    }

    override fun getAudiobookFolders(): Flow<List<AudiobookFolder>> {
        return combine(
            getAudioFiles(),
            userPreferencesRepository.allowedDirectoriesFlow,
            userPreferencesRepository.blockedDirectoriesFlow,
            userPreferencesRepository.isFolderFilterActiveFlow
        ) { Tracks, allowedDirs, blockedDirs, isFolderFilterActive ->
            val resolver = DirectoryRuleResolver(
                allowedDirs.map(::normalizePath).toSet(),
                blockedDirs.map(::normalizePath).toSet()
            )
            val TracksToProcess = if (isFolderFilterActive && blockedDirs.isNotEmpty()) {
                Tracks.filter { Track ->
                    val TrackDir = File(Track.path).parentFile ?: return@filter false
                    val normalized = normalizePath(TrackDir.path)
                    !resolver.isBlocked(normalized)
                }
            } else {
                Tracks
            }

            if (TracksToProcess.isEmpty()) return@combine emptyList()

            data class TempFolder(
                val path: String,
                val name: String,
                val Tracks: MutableList<Track> = mutableListOf(),
                val subFolderPaths: MutableSet<String> = mutableSetOf()
            )

            val tempFolders = mutableMapOf<String, TempFolder>()

            // Optimization: Group Tracks by parent folder first to reduce File object creations and loop iterations
            val TracksByFolder = TracksToProcess.groupBy { File(it.path).parent }

            TracksByFolder.forEach { (folderPath, TracksInFolder) ->
                if (folderPath != null) {
                    val folderFile = File(folderPath)
                    // Create or get the leaf folder
                    val leafFolder = tempFolders.getOrPut(folderPath) { TempFolder(folderPath, folderFile.name) }
                    leafFolder.Tracks.addAll(TracksInFolder)

                    // Build hierarchy upwards
                    var currentPath = folderPath
                    var currentFile = folderFile

                    while (currentFile.parentFile != null) {
                        val parentFile = currentFile.parentFile!!
                        val parentPath = parentFile.path

                        val parentFolder = tempFolders.getOrPut(parentPath) { TempFolder(parentPath, parentFile.name) }
                        val added = parentFolder.subFolderPaths.add(currentPath)

                        if (!added) {
                            // If the link already existed, we have processed this branch up to the root already.
                            break
                        }

                        currentFile = parentFile
                        currentPath = parentPath
                    }
                }
            }

            fun buildImmutableFolder(path: String, visited: MutableSet<String>): AudiobookFolder? {
                if (path in visited) return null
                visited.add(path)
                val tempFolder = tempFolders[path] ?: return null
                val subFolders = tempFolder.subFolderPaths
                    .mapNotNull { subPath -> buildImmutableFolder(subPath, visited.toMutableSet()) }
                    .sortedBy { it.name.lowercase() }
                    .toImmutableList()
                return AudiobookFolder(
                    path = tempFolder.path,
                    name = tempFolder.name,
                    Tracks = tempFolder.Tracks
                        .sortedWith(
                            compareBy<Track> { if (it.trackNumber > 0) it.trackNumber else Int.MAX_VALUE }
                                .thenBy { it.title.lowercase() }
                        )
                        .toImmutableList(),
                    subFolders = subFolders
                )
            }

            val storageRootPath = Environment.getExternalStorageDirectory().path
            val rootTempFolder = tempFolders[storageRootPath]

            val result = rootTempFolder?.subFolderPaths?.mapNotNull { path ->
                buildImmutableFolder(path, mutableSetOf())
            }?.filter { it.totalTrackCount > 0 }?.sortedBy { it.name.lowercase() } ?: emptyList()

            // Fallback for devices that might not use the standard storage root path
            if (result.isEmpty() && tempFolders.isNotEmpty()) {
                 val allSubFolderPaths = tempFolders.values.flatMap { it.subFolderPaths }.toSet()
                 val topLevelPaths = tempFolders.keys - allSubFolderPaths
                 return@combine topLevelPaths
                     .mapNotNull { buildImmutableFolder(it, mutableSetOf()) }
                     .filter { it.totalTrackCount > 0 }
                    .sortedBy { it.name.lowercase() }
             }

            result
        }.conflate().flowOn(Dispatchers.IO)
    }

    override suspend fun deleteById(id: Long) {
        AudiobookDao.deleteById(id)
    }
}
