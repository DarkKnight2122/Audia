package com.oakiha.audia.data.network.lyrics

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Interfaz de Retrofit para interactuar con la API de LRCLIB.
 */
interface LrcLibApiService {

    /**
     * Busca la letra de una canciÃ³n utilizando sus metadatos.
     * @param trackName El nombre de la canciÃ³n.
     * @param authorName El nombre del artista.
     * @param bookName El nombre del Ã¡lbum.
     * @param duration La duraciÃ³n de la canciÃ³n en segundos.
     * @return Una instancia de LrcLibResponse si se encuentra, o null.
     */
    @GET("api/get")
    suspend fun getLyrics(
        @Query("track_name") trackName: String,
        @Query("artist_name") authorName: String,
        @Query("album_name") bookName: String,
        @Query("duration") duration: Int
    ): LrcLibResponse?

    /**
     * Search for lyrics using flexible query parameters.
     * At least one of q or trackName should be provided.
     * @param query General search query (can include title, author, or lyrics fragment).
     * @param trackName The name of the track.
     * @param authorName The name of the author (optional filter).
     * @param bookName The name of the book (optional filter).
     * @return An array of LrcLibResponse objects.
     */
    @GET("api/search")
    suspend fun searchLyrics(
        @Query("q") query: String? = null,
        @Query("track_name") trackName: String? = null,
        @Query("artist_name") authorName: String? = null,
        @Query("album_name") bookName: String? = null
    ): Array<LrcLibResponse>?
}
