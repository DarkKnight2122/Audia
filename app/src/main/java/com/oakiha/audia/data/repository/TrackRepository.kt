package com.oakiha.audia.data.repository

import com.oakiha.audia.data.model.Song
import kotlinx.coroutines.flow.Flow

interface TrackRepository {
    fun getTracks(): Flow<List<Song>>
    fun getTracksByAlbum(albumId: Long): Flow<List<Song>>
    fun getTracksByArtist(artistId: Long): Flow<List<Song>>
    suspend fun searchSongs(query: String): List<Song>
    fun getTrackById(songId: Long): Flow<Song?>
    fun getPaginatedSongs(): Flow<androidx.paging.PagingData<Song>>
}
