package com.oakiha.audia.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface LyricsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(lyrics: LyricsEntity)

    @Query("SELECT * FROM lyrics WHERE trackId = :trackId")
    suspend fun getLyrics(trackId: Long): LyricsEntity?

    @Query("DELETE FROM lyrics WHERE trackId = :trackId")
    suspend fun deleteLyrics(trackId: Long)

    @Query("DELETE FROM lyrics")
    suspend fun deleteAll()
}
