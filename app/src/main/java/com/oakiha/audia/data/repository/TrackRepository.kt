package com.oakiha.audia.data.repository

import com.oakiha.audia.data.model.Track
import kotlinx.coroutines.flow.Flow

interface TrackRepository {
    fun getTracks(): Flow<List<Track>>
    fun getTracksByAlbum(bookId: Long): Flow<List<Track>>
    fun getTracksByArtist(authorId: Long): Flow<List<Track>>
    suspend fun searchSongs(query: String): List<Track>
    fun getTrackById(trackId: Long): Flow<Track?>
    fun getPaginatedSongs(): Flow<androidx.paging.PagingData<Track>>
}
