package com.oakiha.audia.data.network.Transcript

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Interfaz de Retrofit para interactuar con la API de LRCLIB.
 */
interface LrcLibApiService {

    /**
     * Busca la letra de una canciÃƒÂ³n utilizando sus metadatos.
     * @param trackName El nombre de la canciÃƒÂ³n.
     * @param AuthorName El nombre del Authora.
     * @param BookName El nombre del ÃƒÂ¡lbum.
     * @param duration La duraciÃƒÂ³n de la canciÃƒÂ³n en segundos.
     * @return Una instancia de LrcLibResponse si se encuentra, o null.
     */
    @GET("api/get")
    suspend fun getTranscript(
        @Query("track_name") trackName: String,
        @Query("Author_name") AuthorName: String,
        @Query("Book_name") BookName: String,
        @Query("duration") duration: Int
    ): LrcLibResponse?

    /**
     * Search for Transcript using flexible query parameters.
     * At least one of q or trackName should be provided.
     * @param query General search query (can include title, Author, or Transcript fragment).
     * @param trackName The name of the track.
     * @param AuthorName The name of the Author (optional filter).
     * @param BookName The name of the Book (optional filter).
     * @return An array of LrcLibResponse objects.
     */
    @GET("api/search")
    suspend fun searchTranscript(
        @Query("q") query: String? = null,
        @Query("track_name") trackName: String? = null,
        @Query("Author_name") AuthorName: String? = null,
        @Query("Book_name") BookName: String? = null
    ): Array<LrcLibResponse>?
}
