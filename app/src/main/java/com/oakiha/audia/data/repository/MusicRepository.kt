package com.oakiha.audia.data.repository

import android.net.Uri
import androidx.paging.PagingData
import com.oakiha.audia.data.model.Book
import com.oakiha.audia.data.model.Author
import com.oakiha.audia.data.model.Transcript
import com.oakiha.audia.data.model.TranscriptSourcePreference
import com.oakiha.audia.data.model.Booklist
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
    fun getAudioFiles(): Flow<List<Track>> // Existing Flow for reactive updates
    
    /**
     * Returns paginated Tracks for efficient display of large libraries.
     * @return Flow of PagingData<Track> for use with LazyPagingItems.
     */
    fun getPaginatedTracks(): Flow<PagingData<Track>>

    /**
     * Returns the count of Tracks in the library.
     * @return Flow emitting the current Track count.
     */
    fun getTrackCountFlow(): Flow<Int>

    /**
     * Returns a random selection of Tracks for efficient shuffle.
     * Uses database-level RANDOM() for performance.
     * @param limit Maximum number of Tracks to return.
     * @return List of randomly selected Tracks.
     */
    suspend fun getRandomTracks(limit: Int): List<Track>

    /**
     * Obtiene la lista de ÃƒÂ¡lbumes filtrada.
     * @return Flow que emite una lista completa de objetos Book.
     */
    fun getBooks(): Flow<List<Book>> // Existing Flow for reactive updates

    /**
     * Obtiene la lista de Authoras filtrada.
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
    suspend fun getAllBooksOnce(): List<Book>

    /**
     * Obtiene la lista completa de Authoras una sola vez.
     * @return Lista de objetos Author.
     */
    suspend fun getAllAuthorsOnce(): List<Author>

    /**
     * Obtiene un ÃƒÂ¡lbum especÃƒÂ­fico por su ID.
     * @param id El ID del ÃƒÂ¡lbum.
     * @return Flow que emite el objeto Book o null si no se encuentra.
     */
    fun getBookById(id: Long): Flow<Book?>

    /**
     * Obtiene la lista de Authoras filtrada.
     * @return Flow que emite una lista completa de objetos Author.
     */
    //fun getAuthors(): Flow<List<Author>>

    /**
     * Obtiene la lista de canciones para un ÃƒÂ¡lbum especÃƒÂ­fico (NO paginada para la cola de reproducciÃƒÂ³n).
     * @param BookId El ID del ÃƒÂ¡lbum.
     * @return Flow que emite una lista de objetos Track pertenecientes al ÃƒÂ¡lbum.
     */
    fun getTracksForBook(BookId: Long): Flow<List<Track>>

    /**
     * Obtiene la lista de canciones para un Authora especÃƒÂ­fico (NO paginada para la cola de reproducciÃƒÂ³n).
     * @param AuthorId El ID del Authora.
     * @return Flow que emite una lista de objetos Track pertenecientes al Authora.
     */
    fun getTracksForAuthor(AuthorId: Long): Flow<List<Track>>

    /**
     * Obtiene una lista de canciones por sus IDs.
     * @param TrackIds Lista de IDs de canciones.
     * @return Flow que emite una lista de objetos Track correspondientes a los IDs, en el mismo orden.
     */
    fun getTracksByIds(TrackIds: List<String>): Flow<List<Track>>

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

    fun getAllUniqueBookArtUris(): Flow<List<Uri>> // Nuevo para precarga de temas

    suspend fun invalidateCachesDependentOnAllowedDirectories() // Nuevo para precarga de temas

    fun searchTracks(query: String): Flow<List<Track>>
    fun searchBooks(query: String): Flow<List<Book>>
    fun searchAuthors(query: String): Flow<List<Author>>
    suspend fun searchBooklists(query: String): List<Booklist> // Mantener suspend, ya que no hay Flow aÃƒÂºn
    fun searchAll(query: String, filterType: SearchFilterType): Flow<List<SearchResultItem>>

    // Search History
    suspend fun addSearchHistoryItem(query: String)
    suspend fun getRecentSearchHistory(limit: Int): List<SearchHistoryItem>
    suspend fun deleteSearchHistoryItemByQuery(query: String)
    suspend fun clearSearchHistory()

    /**
     * Obtiene la lista de canciones para un gÃƒÂ©nero especÃƒÂ­fico (placeholder implementation).
     * @param CategoryId El ID del gÃƒÂ©nero (e.g., "pop", "rock").
     * @return Flow que emite una lista de objetos Track (simulada para este gÃƒÂ©nero).
     */
    fun getAudiobookByCategory(CategoryId: String): Flow<List<Track>> // Changed to Flow

    /**
     * Cambia el estado de favorito de una canciÃƒÂ³n.
     * @param TrackId El ID de la canciÃƒÂ³n.
     * @return El nuevo estado de favorito (true si es favorito, false si no).
     */
    suspend fun toggleFavoriteStatus(TrackId: String): Boolean

    /**
     * Obtiene una canciÃƒÂ³n especÃƒÂ­fica por su ID.
     * @param TrackId El ID de la canciÃƒÂ³n.
     * @return Flow que emite el objeto Track o null si no se encuentra.
     */
    fun getTrack(TrackId: String): Flow<Track?>
    fun getAuthorById(AuthorId: Long): Flow<Author?>
    fun getAuthorsForTrack(TrackId: Long): Flow<List<Author>>

    /**
     * Obtiene la lista de gÃƒÂ©neros, ya sea mockeados o leÃƒÂ­dos de los metadatos.
     * @return Flow que emite una lista de objetos Category.
     */
    fun getCategories(): Flow<List<com.oakiha.audia.data.model.Category>>

    suspend fun getTranscript(
        Track: Track,
        sourcePreference: TranscriptSourcePreference = TranscriptSourcePreference.EMBEDDED_FIRST,
        forceRefresh: Boolean = false
    ): Transcript?

    suspend fun getTranscriptFromRemote(Track: Track): Result<Pair<Transcript, String>>

    /**
     * Search for Transcript remotely, less specific than `getTranscriptFromRemote` but more lenient
     * @param Track The Track to search Transcript for
     * @return The search query and the results
     */
    suspend fun searchRemoteTranscript(Track: Track): Result<Pair<String, List<TranscriptSearchResult>>>

    /**
     * Search for Transcript remotely using query provided, and not use Track metadata
     * @param query The query for searching, typically Book title and Author name
     * @return The search query and the results
     */
    suspend fun searchRemoteTranscriptByQuery(title: String, Author: String? = null): Result<Pair<String, List<TranscriptSearchResult>>>

    suspend fun updateTranscript(TrackId: Long, Transcript: String)

    suspend fun resetTranscript(TrackId: Long)

    suspend fun resetAllTranscript()

    fun getAudiobookFolders(): Flow<List<com.oakiha.audia.data.model.AudiobookFolder>>

    suspend fun deleteById(id: Long)
}
