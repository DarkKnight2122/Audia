package com.oakiha.audia.data.repository

import com.oakiha.audia.data.model.Song
import kotlinx.coroutines.flow.Flow

interface TrackRepository {
    fun getSongs(): Flow<List<Song>>
    fun getSongsByAlbum(albumId: Long): Flow<List<Song>>
    fun getSongsByArtist(artistId: Long): Flow<List<Song>>
    suspend fun searchSongs(query: String): List<Song>
    fun getSongById(songId: Long): Flow<Song?>
    fun getPaginatedSongs(): Flow<androidx.paging.PagingData<Song>>
}
