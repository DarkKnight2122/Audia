package com.oakiha.audia.data.network.deezer

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Retrofit interface for Deezer API.
 * Used primarily for fetching author artwork.
 */
interface DeezerApiService {

    /**
     * Search for an author by name.
     * @param query Author name to search for
     * @param limit Maximum number of results to return
     * @return Search response containing list of matching authors
     */
    @GET("search/author")
    suspend fun searchArtist(
        @Query("q") query: String,
        @Query("limit") limit: Int = 1
    ): DeezerSearchResponse
}
