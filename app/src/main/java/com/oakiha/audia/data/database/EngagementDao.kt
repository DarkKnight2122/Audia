package com.oakiha.audia.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * DAO for track engagement statistics.
 * Provides efficient database operations for tracking play counts and durations.
 */
@Dao
interface EngagementDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEngagement(engagement: TrackEngagementEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEngagements(engagements: List<TrackEngagementEntity>)

    @Query("SELECT * FROM track_engagements WHERE track_id = :trackId")
    suspend fun getEngagement(trackId: String): TrackEngagementEntity?

    @Query("SELECT * FROM track_engagements")
    suspend fun getAllEngagements(): List<TrackEngagementEntity>

    @Query("SELECT * FROM track_engagements")
    fun getAllEngagementsFlow(): Flow<List<TrackEngagementEntity>>

    @Query("SELECT play_count FROM track_engagements WHERE track_id = :trackId")
    suspend fun getPlayCount(trackId: String): Int?

    @Query("DELETE FROM track_engagements WHERE track_id = :trackId")
    suspend fun deleteEngagement(trackId: String)

    @Query("DELETE FROM track_engagements WHERE track_id NOT IN (SELECT CAST(id AS TEXT) FROM tracks)")
    suspend fun deleteOrphanedEngagements()

    @Query("DELETE FROM track_engagements")
    suspend fun clearAllEngagements()

    /**
     * Increments play count and updates last played timestamp atomically.
     * More efficient than read-modify-write pattern.
     */
    @Query("""
        INSERT INTO track_engagements (track_id, play_count, total_play_duration_ms, last_played_timestamp) 
        VALUES (:trackId, 1, :durationMs, :timestamp)
        ON CONFLICT(track_id) DO UPDATE SET
            play_count = play_count + 1,
            total_play_duration_ms = total_play_duration_ms + :durationMs,
            last_played_timestamp = :timestamp
    """)
    suspend fun recordPlay(trackId: String, durationMs: Long, timestamp: Long)

    /**
     * Get top tracks by play count for quick access.
     */
    @Query("SELECT * FROM track_engagements ORDER BY play_count DESC LIMIT :limit")
    suspend fun getTopPlayedTracks(limit: Int): List<TrackEngagementEntity>
}
