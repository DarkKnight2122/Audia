package com.oakiha.audia.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * DAO for Track engagement statistics.
 * Provides efficient database operations for tracking play counts and durations.
 */
@Dao
interface EngagementDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEngagement(engagement: TrackEngagementEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEngagements(engagements: List<TrackEngagementEntity>)

    @Query("SELECT * FROM Track_engagements WHERE Track_id = :TrackId")
    suspend fun getEngagement(TrackId: String): TrackEngagementEntity?

    @Query("SELECT * FROM Track_engagements")
    suspend fun getAllEngagements(): List<TrackEngagementEntity>

    @Query("SELECT * FROM Track_engagements")
    fun getAllEngagementsFlow(): Flow<List<TrackEngagementEntity>>

    @Query("SELECT play_count FROM Track_engagements WHERE Track_id = :TrackId")
    suspend fun getPlayCount(TrackId: String): Int?

    @Query("DELETE FROM Track_engagements WHERE Track_id = :TrackId")
    suspend fun deleteEngagement(TrackId: String)

    @Query("DELETE FROM Track_engagements WHERE Track_id NOT IN (SELECT CAST(id AS TEXT) FROM Tracks)")
    suspend fun deleteOrphanedEngagements()

    @Query("DELETE FROM Track_engagements")
    suspend fun clearAllEngagements()

    /**
     * Increments play count and updates last played timestamp atomically.
     * More efficient than read-modify-write pattern.
     */
    @Query("""
        INSERT INTO Track_engagements (Track_id, play_count, total_play_duration_ms, last_played_timestamp)
        VALUES (:TrackId, 1, :durationMs, :timestamp)
        ON CONFLICT(Track_id) DO UPDATE SET
            play_count = play_count + 1,
            total_play_duration_ms = total_play_duration_ms + :durationMs,
            last_played_timestamp = :timestamp
    """)
    suspend fun recordPlay(TrackId: String, durationMs: Long, timestamp: Long)

    /**
     * Get top Tracks by play count for quick access.
     */
    @Query("SELECT * FROM Track_engagements ORDER BY play_count DESC LIMIT :limit")
    suspend fun getTopPlayedTracks(limit: Int): List<TrackEngagementEntity>
}
