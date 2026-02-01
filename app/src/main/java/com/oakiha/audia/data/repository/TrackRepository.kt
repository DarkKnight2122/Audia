package com.oakiha.audia.data.repository

import com.oakiha.audia.data.model.Track
import kotlinx.coroutines.flow.Flow

interface TrackRepository {
    fun getTracks(): Flow<List<Track>>
    fun getTracksByBook(bookId: Long): Flow<List<Track>>
    fun getTracksByAuthor(authorId: Long): Flow<List<Track>>
    suspend fun searchTracks(query: String): List<Track>
    fun getTrackById(trackId: Long): Flow<Track?>
    fun getPaginatedTracks(): Flow<androidx.paging.PagingData<Track>>
}

