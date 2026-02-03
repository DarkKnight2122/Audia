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
import com.oakiha.audia.data.model.Playlist
import com.oakiha.audia.data.model.SearchFilterType
import com.oakiha.audia.data.model.SearchHistoryItem
import com.oakiha.audia.data.model.SearchResultItem
import com.oakiha.audia.data.model.SortOption
import com.oakiha.audia.data.preferences.UserPreferencesRepository
import androidx.sqlite.db.SimpleSQLiteQuery

import com.oakiha.audia.data.model.Genre
import com.oakiha.audia.data.database.TrackEntity
import com.oakiha.audia.data.database.TrackAuthorCrossRef
import com.oakiha.audia.data.database.AuthorEntity
import com.oakiha.audia.data.database.toBook
import com.oakiha.audia.data.database.toAuthor
import com.oakiha.audia.data.database.toTrack
import com.oakiha.audia.data.database.toTrackWithAuthorRefs
import com.oakiha.audia.data.model.Lyrics
import com.oakiha.audia.data.model.LyricsSourcePreference
import com.oakiha.audia.data.model.SyncedLine
import com.oakiha.audia.utils.LogUtils
import com.oakiha.audia.data.model.AudiobookFolder
import com.oakiha.audia.utils.LyricsUtils
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
    private val audiobookDao: AudiobookDao,
    private val lyricsRepository: LyricsRepository,
    private val trackRepository: TrackRepository,
    private val favoritesDao: FavoritesDao
) : AudiobookRepository {

    private val directoryScanMutex = Mutex()

    private fun normalizePath(path: String): String =
        runCatching { File(path).canonicalPath }.getOrElse { File(path).absolutePath }



    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getTracks(): Flow<List<Track>> {
        // Delegate to the reactive TrackRepository which queries MediaStore directly
        // and observes directory preference changes in real-time.
        return trackRepository.getTracks()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getPaginatedTracks(): Flow<PagingData<Track>> {
       // Delegate to reactive repository for correct filtering and paging
       return trackRepository.getPaginatedTracks()
    }

    override fun getTrackCountFlow(): Flow<Int> {
        return audiobookDao.getTrackCount()
    }

    override suspend fun getRandomTracks(limit: Int): List<Track> = withContext(Dispatchers.IO) {
        audiobookDao.getRandomTracks(limit).map { it.toTrack() }
    }

    override fun getBooks(): Flow<List<Book>> {
        return getTracks().map { tracks ->
            tracks.groupBy { it.bookId }
                .map { (bookId, tracksInBook) ->
                    val first = tracksInBook.first()
                    Book(
                        id = bookId,
                        title = first.book,
                        author = first.author, // Or bookAuthor if available
                        bookArtUriString = first.bookArtUriString,
                        trackCount = tracksInBook.size,
                        year = first.year
                    )
                }
                .sortedBy { it.title.lowercase() }
        }.flowOn(Dispatchers.Default)
    }

    override fun getBookById(id: Long): Flow<Book?> {
        return getBooks().map { books -> 
            books.find { it.id == id }
        }
    }

    override fun getAuthors(): Flow<List<Author>> {
        return getTracks().map { tracks ->
            tracks.groupBy { it.authorId }
                .map { (authorId, tracksInAuthor) ->
                    val first = tracksInAuthor.first()
                    Author(
                        id = authorId,
                        name = first.author,
                        trackCount = tracksInAuthor.size
                    )
                }
                .sortedBy { it.name.lowercase() }
        }.flowOn(Dispatchers.Default)
    }

    override fun getTracksForBook(bookId: Long): Flow<List<Track>> {
        return getTracks().map { tracks ->
            tracks.filter { it.bookId == bookId }
                .sortedBy { it.trackNumber }
        }
    }

    override fun getAuthorById(authorId: Long): Flow<Author?> {
         return getAuthors().map { authors ->
             authors.find { it.id == authorId }
         }
    }

    override fun getAuthorsForTrack(trackId: Long): Flow<List<Author>> {
        // Simple implementation assuming single author per track as per MediaStore
        // For multi-author, we would parse the separator/delimiter here.
        return getTracks().map { tracks ->
            val track = tracks.find { it.id == trackId.toString() }
            if (track != null) {
                listOf(Author(id = track.authorId, name = track.author, trackCount = 1, imageUrl = null))
            } else {
                emptyList()
            }
        }
    }

    override fun getTracksForAuthor(authorId: Long): Flow<List<Track>> {
        return getTracks().map { tracks ->
            tracks.filter { it.authorId == authorId }
                .sortedBy { it.title }
        }
    }

    override suspend fun getAllUniqueAudioDirectories(): Set<String> = withContext(Dispatchers.IO) {
        LogUtils.d(this, "getAllUniqueAudioDirectories")
        directoryScanMutex.withLock {
            val directories = mutableSetOf<String>()
            val projection = arrayOf(MediaStore.Audio.Media.DATA)
            val selection = "(${MediaStore.Audio.Media.IS_MUSIC} != 0 OR ${MediaStore.Audio.Media.DATA} LIKE '%.m4a' OR ${MediaStore.Audio.Media.DATA} LIKE '%.flac')"
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
        return audiobookDao.getAllUniqueBookArtUris().map { uriStrings ->
            uriStrings.mapNotNull { it.toUri() }
        }.flowOn(Dispatchers.IO)
    }

    // --- MÃƒÂ©todos de BÃƒÂºsqueda ---

    override fun searchTracks(query: String): Flow<List<Track>> {
        if (query.isBlank()) return flowOf(emptyList())
        // Passing emptyList and false for directory filter as we trust SSOT (SyncWorker filtered)
        return audiobookDao.searchTracks(query, emptyList(), false).map { entities ->
            entities.map { it.toTrack() }
        }.flowOn(Dispatchers.IO)
    }


    override fun searchBooks(query: String): Flow<List<Book>> {
       if (query.isBlank()) return flowOf(emptyList())
       return audiobookDao.searchBooks(query, emptyList(), false).map { entities ->
           entities.map { it.toBook() }
       }.flowOn(Dispatchers.IO)
    }

    override fun searchAuthors(query: String): Flow<List<Author>> {
        if (query.isBlank()) return flowOf(emptyList())
        return audiobookDao.searchAuthors(query, emptyList(), false).map { entities ->
            entities.map { it.toAuthor() }
        }.flowOn(Dispatchers.IO)
    }

    override suspend fun searchPlaylists(query: String): List<Playlist> {
        if (query.isBlank()) return emptyList()
        return userPreferencesRepository.userPlaylistsFlow.first()
            .filter { playlist ->
                playlist.name.contains(query, ignoreCase = true)
            }
    }

    override fun searchAll(query: String, filterType: SearchFilterType): Flow<List<SearchResultItem>> {
        if (query.isBlank()) return flowOf(emptyList())
        val playlistsFlow = flow { emit(searchPlaylists(query)) }

        return when (filterType) {
            SearchFilterType.ALL -> {
                combine(
                    searchTracks(query),
                    searchBooks(query),
                    searchAuthors(query),
                    playlistsFlow
                ) { tracks, books, authors, playlists ->
                    mutableListOf<SearchResultItem>().apply {
                        tracks.forEach { add(SearchResultItem.TrackItem(it)) }
                        books.forEach { add(SearchResultItem.BookItem(it)) }
                        authors.forEach { add(SearchResultItem.AuthorItem(it)) }
                        playlists.forEach { add(SearchResultItem.PlaylistItem(it)) }
                    }
                }
            }
            SearchFilterType.TRACKS -> searchTracks(query).map { tracks -> tracks.map { SearchResultItem.TrackItem(it) } }
            SearchFilterType.BOOKS -> searchBooks(query).map { books -> books.map { SearchResultItem.BookItem(it) } }
            SearchFilterType.AUTHORS -> searchAuthors(query).map { authors -> authors.map { SearchResultItem.AuthorItem(it) } }
            SearchFilterType.PLAYLISTS -> playlistsFlow.map { playlists -> playlists.map { SearchResultItem.PlaylistItem(it) } }
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

    override fun getMusicByGenre(genreId: String): Flow<List<Track>> {
        return userPreferencesRepository.mockGenresEnabledFlow.flatMapLatest { mockEnabled ->
            if (mockEnabled) {
                // Mock mode: Use the static genre name for filtering.
                val genreName = "Mock"//GenreDataSource.getStaticGenres().find { it.id.equals(genreId, ignoreCase = true) }?.name ?: genreId
                getTracks().map { tracks ->
                    tracks.filter { it.genre.equals(genreName, ignoreCase = true) }
                }
            } else {
                // Real mode: Use the genreId directly, which corresponds to the actual genre name from metadata.
                getTracks().map { tracks ->
                    if (genreId.equals("unknown", ignoreCase = true)) {
                        // Filter for tracks with no genre or an empty genre string.
                        tracks.filter { it.genre.isNullOrBlank() }
                    } else {
                        // Filter for tracks that match the given genre name.
                        tracks.filter { it.genre.equals(genreId, ignoreCase = true) }
                    }
                }
            }
        }.flowOn(Dispatchers.IO)
    }

    override fun getTracksByIds(trackIds: List<String>): Flow<List<Track>> {
        if (trackIds.isEmpty()) return flowOf(emptyList())
        return trackRepository.getTracks().map { tracks ->
             val tracksMap = tracks.associateBy { it.id }
             trackIds.mapNotNull { tracksMap[it] }
        }.flowOn(Dispatchers.Default)
    }

    override suspend fun getTrackByPath(path: String): Track? {
        return withContext(Dispatchers.IO) {
            audiobookDao.getTrackByPath(path)?.toTrack()
        }
    }

    override suspend fun invalidateCachesDependentOnAllowedDirectories() {
        Log.i("MusicRepo", "invalidateCachesDependentOnAllowedDirectories called. Reactive flows will update automatically.")
    }

    suspend fun syncMusicFromContentResolver() {
        // Esta funciÃƒÂ³n ahora estÃƒÂ¡ en SyncWorker. Se deja el esqueleto por si se llama desde otro lugar.
        Log.w("MusicRepo", "syncMusicFromContentResolver was called directly on repository. This should be handled by SyncWorker.")
    }

    // ImplementaciÃƒÂ³n de las nuevas funciones suspend para carga ÃƒÂºnica
    override suspend fun getAllTracksOnce(): List<Track> = withContext(Dispatchers.IO) {
        audiobookDao.getAllTracksOnce().map { it.toTrack() }
    }

    override suspend fun getAllBooksOnce(): List<Book> = withContext(Dispatchers.IO) {
        audiobookDao.getAllBooksOnce(emptyList(), false).map { it.toBook() }
    }

    override suspend fun getAllAuthorsOnce(): List<Author> = withContext(Dispatchers.IO) {
        audiobookDao.getAllAuthorsRawOnce().map { it.toAuthor() }
    }

    override suspend fun toggleFavoriteStatus(trackId: String): Boolean = withContext(Dispatchers.IO) {
        val id = trackId.toLongOrNull() ?: return@withContext false
        val isFav = favoritesDao.isFavorite(id) ?: false
        val newFav = !isFav
        if (newFav) {
            favoritesDao.setFavorite(com.oakiha.audia.data.database.FavoritesEntity(id, true))
        } else {
            favoritesDao.removeFavorite(id)
        }
        return@withContext newFav
    }

    override fun getTrack(trackId: String): Flow<Track?> {
        val id = trackId.toLongOrNull() ?: return flowOf(null)
        return audiobookDao.getTrackById(id).map { it?.toTrack() }.flowOn(Dispatchers.IO)
    }

    override fun getGenres(): Flow<List<Genre>> {
        return getTracks().map { tracks ->
            val genresMap = tracks.groupBy { track ->
                track.genre?.trim()?.takeIf { it.isNotBlank() } ?: "Unknown"
            }

            val dynamicGenres = genresMap.keys.mapNotNull { genreName ->
                val id = if (genreName.equals("Unknown", ignoreCase = true)) "unknown" else genreName.lowercase().replace(" ", "_")
                // Generate colors dynamically or use a default for "Unknown"
                val colorInt = genreName.hashCode()
                val lightColorHex = "#${(colorInt and 0x00FFFFFF).toString(16).padStart(6, '0').uppercase()}"
                // Simple inversion for dark color, or use a predefined set
                val darkColorHex = "#${((colorInt xor 0xFFFFFF) and 0x00FFFFFF).toString(16).padStart(6, '0').uppercase()}"

                Genre(
                    id = id,
                    name = genreName,
                    lightColorHex = lightColorHex,
                    onLightColorHex = "#000000", // Default black for light theme text
                    darkColorHex = darkColorHex,
                    onDarkColorHex = "#FFFFFF"  // Default white for dark theme text
                )
            }.sortedBy { it.name.lowercase() }

            // Ensure "Unknown" genre is last if it exists.
            val unknownGenre = dynamicGenres.find { it.id == "unknown" }
            if (unknownGenre != null) {
                (dynamicGenres.filterNot { it.id == "unknown" } + unknownGenre)
            } else {
                dynamicGenres
            }
        }.conflate().flowOn(Dispatchers.IO)
    }

    override suspend fun getLyrics(
        track: Track,
        sourcePreference: LyricsSourcePreference,
        forceRefresh: Boolean
    ): Lyrics? {
        return lyricsRepository.getLyrics(track, sourcePreference, forceRefresh)
    }

    /**
     * Obtiene la letra de una canciÃƒÂ³n desde la API de LRCLIB, la persiste en la base de datos
     * y la devuelve como un objeto Lyrics parseado.
     *
     * @param track La canciÃƒÂ³n para la cual se buscarÃƒÂ¡ la letra.
     * @return Un objeto Result que contiene el objeto Lyrics si se encontrÃƒÂ³, o un error.
     */
    override suspend fun getLyricsFromRemote(track: Track): Result<Pair<Lyrics, String>> {
        return lyricsRepository.fetchFromRemote(track)
    }

    override suspend fun searchRemoteLyrics(track: Track): Result<Pair<String, List<LyricsSearchResult>>> {
        return lyricsRepository.searchRemote(track)
    }

    override suspend fun searchRemoteLyricsByQuery(title: String, author: String?): Result<Pair<String, List<LyricsSearchResult>>> {
        return lyricsRepository.searchRemoteByQuery(title, author)
    }

    override suspend fun updateLyrics(trackId: Long, lyrics: String) {
        lyricsRepository.updateLyrics(trackId, lyrics)
    }

    override suspend fun resetLyrics(trackId: Long) {
        lyricsRepository.resetLyrics(trackId)
    }

    override suspend fun resetAllLyrics() {
        lyricsRepository.resetAllLyrics()
    }

    override fun getAudiobookFolders(): Flow<List<AudiobookFolder>> {
        return combine(
            getTracks(),
            userPreferencesRepository.allowedDirectoriesFlow,
            userPreferencesRepository.blockedDirectoriesFlow,
            userPreferencesRepository.isFolderFilterActiveFlow
        ) { tracks, allowedDirs, blockedDirs, isFolderFilterActive ->
            val resolver = DirectoryRuleResolver(
                allowedDirs.map(::normalizePath).toSet(),
                blockedDirs.map(::normalizePath).toSet()
            )
            val tracksToProcess = if (isFolderFilterActive && blockedDirs.isNotEmpty()) {
                tracks.filter { track ->
                    val trackDir = File(track.path).parentFile ?: return@filter false
                    val normalized = normalizePath(trackDir.path)
                    !resolver.isBlocked(normalized)
                }
            } else {
                tracks
            }

            if (tracksToProcess.isEmpty()) return@combine emptyList()

            data class TempFolder(
                val path: String,
                val name: String,
                val tracks: MutableList<Track> = mutableListOf(),
                val subFolderPaths: MutableSet<String> = mutableSetOf()
            )

            val tempFolders = mutableMapOf<String, TempFolder>()

            // Optimization: Group tracks by parent folder first to reduce File object creations and loop iterations
            val tracksByFolder = tracksToProcess.groupBy { File(it.path).parent }

            tracksByFolder.forEach { (folderPath, tracksInFolder) ->
                if (folderPath != null) {
                    val folderFile = File(folderPath)
                    // Create or get the leaf folder
                    val leafFolder = tempFolders.getOrPut(folderPath) { TempFolder(folderPath, folderFile.name) }
                    leafFolder.tracks.addAll(tracksInFolder)

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
                    tracks = tempFolder.tracks
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
        audiobookDao.deleteById(id)
    }

    

}

