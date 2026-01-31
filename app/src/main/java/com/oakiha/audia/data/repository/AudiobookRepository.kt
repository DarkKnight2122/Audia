package com.oakiha.audia.data.repository

import android.net.Uri
import androidx.paging.PagingData
import com.oakiha.audia.data.model.Album
import com.oakiha.audia.data.model.Artist
import com.oakiha.audia.data.model.Lyrics
import com.oakiha.audia.data.model.LyricsSourcePreference
import com.oakiha.audia.data.model.Playlist
import com.oakiha.audia.data.model.SearchFilterType
import com.oakiha.audia.data.model.SearchHistoryItem
import com.oakiha.audia.data.model.SearchResultItem
import com.oakiha.audia.data.model.Song
import kotlinx.coroutines.flow.Flow

interface AudiobookRepository {
    /**
     * Obtiene la lista de archivos de audio (canciones) filtrada por directorios permitidos.
     * @return Flow que emite una lista completa de objetos Song.
     */
    fun getAudioFiles(): Flow<List<Song>> // Existing Flow for reactive updates
    
    /**
     * Returns paginated songs for efficient display of large libraries.
     * @return Flow of PagingData<Song> for use with LazyPagingItems.
     */
    fun getPaginatedSongs(): Flow<PagingData<Song>>

    /**
     * Returns the count of songs in the library.
     * @return Flow emitting the current song count.
     */
    fun getSongCountFlow(): Flow<Int>

    /**
     * Returns a random selection of songs for efficient shuffle.
     * Uses database-level RANDOM() for performance.
     * @param limit Maximum number of songs to return.
     * @return List of randomly selected songs.
     */
    suspend fun getRandomSongs(limit: Int): List<Song>

    /**
     * Obtiene la lista de Ã¡lbumes filtrada.
     * @return Flow que emite una lista completa de objetos Album.
     */
    fun getAlbums(): Flow<List<Album>> // Existing Flow for reactive updates

    /**
     * Obtiene la lista de artistas filtrada.
     * @return Flow que emite una lista completa de objetos Artist.
     */
    fun getArtists(): Flow<List<Artist>> // Existing Flow for reactive updates

    /**
     * Obtiene la lista completa de canciones una sola vez.
     * @return Lista de objetos Song.
     */
    suspend fun getAllSongsOnce(): List<Song>

    /**
     * Obtiene la lista completa de Ã¡lbumes una sola vez.
     * @return Lista de objetos Album.
     */
    suspend fun getAllAlbumsOnce(): List<Album>

    /**
     * Obtiene la lista completa de artistas una sola vez.
     * @return Lista de objetos Artist.
     */
    suspend fun getAllArtistsOnce(): List<Artist>

    /**
     * Obtiene un Ã¡lbum especÃ­fico por su ID.
     * @param id El ID del Ã¡lbum.
     * @return Flow que emite el objeto Album o null si no se encuentra.
     */
    fun getAlbumById(id: Long): Flow<Album?>

    /**
     * Obtiene la lista de artistas filtrada.
     * @return Flow que emite una lista completa de objetos Artist.
     */
    //fun getArtists(): Flow<List<Artist>>

    /**
     * Obtiene la lista de canciones para un Ã¡lbum especÃ­fico (NO paginada para la cola de reproducciÃ³n).
     * @param albumId El ID del Ã¡lbum.
     * @return Flow que emite una lista de objetos Song pertenecientes al Ã¡lbum.
     */
    fun getSongsForAlbum(albumId: Long): Flow<List<Song>>

    /**
     * Obtiene la lista de canciones para un artista especÃ­fico (NO paginada para la cola de reproducciÃ³n).
     * @param artistId El ID del artista.
     * @return Flow que emite una lista de objetos Song pertenecientes al artista.
     */
    fun getSongsForArtist(artistId: Long): Flow<List<Song>>

    /**
     * Obtiene una lista de canciones por sus IDs.
     * @param songIds Lista de IDs de canciones.
     * @return Flow que emite una lista de objetos Song correspondientes a los IDs, en el mismo orden.
     */
    fun getSongsByIds(songIds: List<String>): Flow<List<Song>>

    /**
     * Obtiene una canciÃ³n por su ruta de archivo.
     * @param path Ruta del archivo.
     * @return El objeto Song o null si no se encuentra.
     */
    suspend fun getSongByPath(path: String): Song?

    /**
     * Obtiene todos los directorios Ãºnicos que contienen archivos de audio.
     * Esto se usa principalmente para la configuraciÃ³n inicial de directorios.
     * TambiÃ©n gestiona el guardado inicial de directorios permitidos si es la primera vez.
     * @return Conjunto de rutas de directorios Ãºnicas.
     */
    suspend fun getAllUniqueAudioDirectories(): Set<String>

    fun getAllUniqueAlbumArtUris(): Flow<List<Uri>> // Nuevo para precarga de temas

    suspend fun invalidateCachesDependentOnAllowedDirectories() // Nuevo para precarga de temas

    fun searchSongs(query: String): Flow<List<Song>>
    fun searchAlbums(query: String): Flow<List<Album>>
    fun searchArtists(query: String): Flow<List<Artist>>
    suspend fun searchPlaylists(query: String): List<Playlist> // Mantener suspend, ya que no hay Flow aÃºn
    fun searchAll(query: String, filterType: SearchFilterType): Flow<List<SearchResultItem>>

    // Search History
    suspend fun addSearchHistoryItem(query: String)
    suspend fun getRecentSearchHistory(limit: Int): List<SearchHistoryItem>
    suspend fun deleteSearchHistoryItemByQuery(query: String)
    suspend fun clearSearchHistory()

    /**
     * Obtiene la lista de canciones para un gÃ©nero especÃ­fico (placeholder implementation).
     * @param genreId El ID del gÃ©nero (e.g., "pop", "rock").
     * @return Flow que emite una lista de objetos Song (simulada para este gÃ©nero).
     */
    fun getMusicByGenre(genreId: String): Flow<List<Song>> // Changed to Flow

    /**
     * Cambia el estado de favorito de una canciÃ³n.
     * @param songId El ID de la canciÃ³n.
     * @return El nuevo estado de favorito (true si es favorito, false si no).
     */
    suspend fun toggleFavoriteStatus(songId: String): Boolean

    /**
     * Obtiene una canciÃ³n especÃ­fica por su ID.
     * @param songId El ID de la canciÃ³n.
     * @return Flow que emite el objeto Song o null si no se encuentra.
     */
    fun getSong(songId: String): Flow<Song?>
    fun getArtistById(artistId: Long): Flow<Artist?>
    fun getArtistsForSong(songId: Long): Flow<List<Artist>>

    /**
     * Obtiene la lista de gÃ©neros, ya sea mockeados o leÃ­dos de los metadatos.
     * @return Flow que emite una lista de objetos Genre.
     */
    fun getGenres(): Flow<List<com.oakiha.audia.data.model.Genre>>

    suspend fun getLyrics(
        song: Song,
        sourcePreference: LyricsSourcePreference = LyricsSourcePreference.EMBEDDED_FIRST,
        forceRefresh: Boolean = false
    ): Lyrics?

    suspend fun getLyricsFromRemote(song: Song): Result<Pair<Lyrics, String>>

    /**
     * Search for lyrics remotely, less specific than `getLyricsFromRemote` but more lenient
     * @param song The song to search lyrics for
     * @return The search query and the results
     */
    suspend fun searchRemoteLyrics(song: Song): Result<Pair<String, List<LyricsSearchResult>>>

    /**
     * Search for lyrics remotely using query provided, and not use song metadata
     * @param query The query for searching, typically song title and artist name
     * @return The search query and the results
     */
    suspend fun searchRemoteLyricsByQuery(title: String, artist: String? = null): Result<Pair<String, List<LyricsSearchResult>>>

    suspend fun updateLyrics(songId: Long, lyrics: String)

    suspend fun resetLyrics(songId: Long)

    suspend fun resetAllLyrics()

    fun getAudiobookFolders(): Flow<List<com.oakiha.audia.data.model.AudiobookFolder>>

    suspend fun deleteById(id: Long)
}
