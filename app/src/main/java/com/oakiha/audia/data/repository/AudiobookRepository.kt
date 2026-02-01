package com.oakiha.audia.data.repository

import android.net.Uri
import androidx.paging.PagingData
import com.oakiha.audia.data.model.Book
import com.oakiha.audia.data.model.Author
import com.oakiha.audia.data.model.Lyrics
import com.oakiha.audia.data.model.LyricsSourcePreference
import com.oakiha.audia.data.model.Playlist
import com.oakiha.audia.data.model.SearchFilterType
import com.oakiha.audia.data.model.SearchHistoryItem
import com.oakiha.audia.data.model.SearchResultItem
import com.oakiha.audia.data.model.Track
import kotlinx.coroutines.flow.Flow

interface AudiobookRepository {
    /**
     * Obtiene la lista de archivos de audio (canciones) filtrada por directorios permitidos.
     * @return Flow que emite una lista completa de objetos Track.
     */
    fun getTracks(): Flow<List<Track>> // Existing Flow for reactive updates
    
    /**
     * Returns paginated tracks for efficient display of large libraries.
     * @return Flow of PagingData<Track> for use with LazyPagingItems.
     */
    fun getPaginatedSongs(): Flow<PagingData<Track>>

    /**
     * Returns the count of tracks in the library.
     * @return Flow emitting the current track count.
     */
    fun getTrackCountFlow(): Flow<Int>

    /**
     * Returns a random selection of tracks for efficient shuffle.
     * Uses database-level RANDOM() for performance.
     * @param limit Maximum number of tracks to return.
     * @return List of randomly selected tracks.
     */
    suspend fun getRandomSongs(limit: Int): List<Track>

    /**
     * Obtiene la lista de ÃƒÂ¡lbumes filtrada.
     * @return Flow que emite una lista completa de objetos Book.
     */
    fun getBooks(): Flow<List<Book>> // Existing Flow for reactive updates

    /**
     * Obtiene la lista de artistas filtrada.
     * @return Flow que emite una lista completa de objetos Author.
     */
    fun getAuthors(): Flow<List<Author>> // Existing Flow for reactive updates

    /**
     * Obtiene la lista completa de canciones una sola vez.
     * @return Lista de objetos Track.
     */
    suspend fun getAllTracksOnce(): List<Track>

    /**
     * Obtiene la lista completa de ÃƒÂ¡lbumes una sola vez.
     * @return Lista de objetos Book.
     */
    suspend fun getAllAlbumsOnce(): List<Book>

    /**
     * Obtiene la lista completa de artistas una sola vez.
     * @return Lista de objetos Author.
     */
    suspend fun getAllArtistsOnce(): List<Author>

    /**
     * Obtiene un ÃƒÂ¡lbum especÃƒÂ­fico por su ID.
     * @param id El ID del ÃƒÂ¡lbum.
     * @return Flow que emite el objeto Book o null si no se encuentra.
     */
    fun getBookById(id: Long): Flow<Book?>

    /**
     * Obtiene la lista de artistas filtrada.
     * @return Flow que emite una lista completa de objetos Author.
     */
    //fun getAuthors(): Flow<List<Author>>

    /**
     * Obtiene la lista de canciones para un ÃƒÂ¡lbum especÃƒÂ­fico (NO paginada para la cola de reproducciÃƒÂ³n).
     * @param bookId El ID del ÃƒÂ¡lbum.
     * @return Flow que emite una lista de objetos Track pertenecientes al ÃƒÂ¡lbum.
     */
    fun getTracksForAlbum(bookId: Long): Flow<List<Track>>

    /**
     * Obtiene la lista de canciones para un artista especÃƒÂ­fico (NO paginada para la cola de reproducciÃƒÂ³n).
     * @param authorId El ID del artista.
     * @return Flow que emite una lista de objetos Track pertenecientes al artista.
     */
    fun getTracksForArtist(authorId: Long): Flow<List<Track>>

    /**
     * Obtiene una lista de canciones por sus IDs.
     * @param trackIds Lista de IDs de canciones.
     * @return Flow que emite una lista de objetos Track correspondientes a los IDs, en el mismo orden.
     */
    fun getTracksByIds(trackIds: List<String>): Flow<List<Track>>

    /**
     * Obtiene una canciÃƒÂ³n por su ruta de archivo.
     * @param path Ruta del archivo.
     * @return El objeto Track o null si no se encuentra.
     */
    suspend fun getTrackByPath(path: String): Track?

    /**
     * Obtiene todos los directorios ÃƒÂºnicos que contienen archivos de audio.
     * Esto se usa principalmente para la configuraciÃƒÂ³n inicial de directorios.
     * TambiÃƒÂ©n gestiona el guardado inicial de directorios permitidos si es la primera vez.
     * @return Conjunto de rutas de directorios ÃƒÂºnicas.
     */
    suspend fun getAllUniqueAudioDirectories(): Set<String>

    fun getAllUniqueAlbumArtUris(): Flow<List<Uri>> // Nuevo para precarga de temas

    suspend fun invalidateCachesDependentOnAllowedDirectories() // Nuevo para precarga de temas

    fun searchTracks(query: String): Flow<List<Track>>
    fun searchBooks(query: String): Flow<List<Book>>
    fun searchAuthors(query: String): Flow<List<Author>>
    suspend fun searchPlaylists(query: String): List<Playlist> // Mantener suspend, ya que no hay Flow aÃƒÂºn
    fun searchAll(query: String, filterType: SearchFilterType): Flow<List<SearchResultItem>>

    // Search History
    suspend fun addSearchHistoryItem(query: String)
    suspend fun getRecentSearchHistory(limit: Int): List<SearchHistoryItem>
    suspend fun deleteSearchHistoryItemByQuery(query: String)
    suspend fun clearSearchHistory()

    /**
     * Obtiene la lista de canciones para un gÃƒÂ©nero especÃƒÂ­fico (placeholder implementation).
     * @param genreId El ID del gÃƒÂ©nero (e.g., "pop", "rock").
     * @return Flow que emite una lista de objetos Track (simulada para este gÃƒÂ©nero).
     */
    fun getMusicByGenre(genreId: String): Flow<List<Track>> // Changed to Flow

    /**
     * Cambia el estado de favorito de una canciÃƒÂ³n.
     * @param trackId El ID de la canciÃƒÂ³n.
     * @return El nuevo estado de favorito (true si es favorito, false si no).
     */
    suspend fun toggleFavoriteStatus(trackId: String): Boolean

    /**
     * Obtiene una canciÃƒÂ³n especÃƒÂ­fica por su ID.
     * @param trackId El ID de la canciÃƒÂ³n.
     * @return Flow que emite el objeto Track o null si no se encuentra.
     */
    fun getTrack(trackId: String): Flow<Track?>
    fun getAuthorById(authorId: Long): Flow<Author?>
    fun getAuthorsForTrack(trackId: Long): Flow<List<Author>>

    /**
     * Obtiene la lista de gÃƒÂ©neros, ya sea mockeados o leÃƒÂ­dos de los metadatos.
     * @return Flow que emite una lista de objetos Genre.
     */
    fun getGenres(): Flow<List<com.oakiha.audia.data.model.Genre>>

    suspend fun getLyrics(
        track: Track,
        sourcePreference: LyricsSourcePreference = LyricsSourcePreference.EMBEDDED_FIRST,
        forceRefresh: Boolean = false
    ): Lyrics?

    suspend fun getLyricsFromRemote(track: Track): Result<Pair<Lyrics, String>>

    /**
     * Search for lyrics remotely, less specific than `getLyricsFromRemote` but more lenient
     * @param track The track to search lyrics for
     * @return The search query and the results
     */
    suspend fun searchRemoteLyrics(track: Track): Result<Pair<String, List<LyricsSearchResult>>>

    /**
     * Search for lyrics remotely using query provided, and not use track metadata
     * @param query The query for searching, typically track title and author name
     * @return The search query and the results
     */
    suspend fun searchRemoteLyricsByQuery(title: String, author: String? = null): Result<Pair<String, List<LyricsSearchResult>>>

    suspend fun updateLyrics(trackId: Long, lyrics: String)

    suspend fun resetLyrics(trackId: Long)

    suspend fun resetAllLyrics()

    fun getAudiobookFolders(): Flow<List<com.oakiha.audia.data.model.AudiobookFolder>>

    suspend fun deleteById(id: Long)
}


