package com.oakiha.audia.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoritesDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setFavorite(favorite: FavoritesEntity)

    @Query("DELETE FROM favorites WHERE TrackId = :TrackId")
    suspend fun removeFavorite(TrackId: Long)

    @Query("SELECT isFavorite FROM favorites WHERE TrackId = :TrackId")
    suspend fun isFavorite(TrackId: Long): Boolean?

    @Query("SELECT TrackId FROM favorites WHERE isFavorite = 1")
    fun getFavoriteTrackIds(): Flow<List<Long>>

    @Query("SELECT TrackId FROM favorites WHERE isFavorite = 1")
    suspend fun getFavoriteTrackIdsOnce(): List<Long>
}
