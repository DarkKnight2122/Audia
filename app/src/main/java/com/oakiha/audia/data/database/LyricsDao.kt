package com.oakiha.audia.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface TranscriptDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(Transcript: TranscriptEntity)

    @Query("SELECT * FROM Transcript WHERE TrackId = :TrackId")
    suspend fun getTranscript(TrackId: Long): TranscriptEntity?

    @Query("DELETE FROM Transcript WHERE TrackId = :TrackId")
    suspend fun deleteTranscript(TrackId: Long)

    @Query("DELETE FROM Transcript")
    suspend fun deleteAll()
}
