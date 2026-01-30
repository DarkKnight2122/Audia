package com.oakiha.audia.data.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for transition rules.
 */
@Dao
interface TransitionDao {

    /**
     * Inserts a new rule or updates an existing one if it matches a unique index.
     */
    @Upsert
    suspend fun setRule(rule: TransitionRuleEntity)

    /**
     * Gets the default transition rule for a given Booklist.
     * A default rule is one where fromTrackId and toTrackId are both null.
     */
    @Query("SELECT * FROM transition_rules WHERE BooklistId = :BooklistId AND fromTrackId IS NULL AND toTrackId IS NULL")
    fun getBooklistDefaultRule(BooklistId: String): Flow<TransitionRuleEntity?>

    /**
     * Gets a specific transition rule between two tracks in a Booklist.
     */
    @Query("SELECT * FROM transition_rules WHERE BooklistId = :BooklistId AND fromTrackId = :fromTrackId AND toTrackId = :toTrackId")
    fun getSpecificRule(BooklistId: String, fromTrackId: String, toTrackId: String): Flow<TransitionRuleEntity?>

    /**
     * Gets all rules (default and specific) for a given Booklist.
     * Useful for a settings screen.
     */
    @Query("SELECT * FROM transition_rules WHERE BooklistId = :BooklistId")
    fun getAllRulesForBooklist(BooklistId: String): Flow<List<TransitionRuleEntity>>

    /**
     * Deletes a rule by its primary key.
     */
    @Query("DELETE FROM transition_rules WHERE id = :ruleId")
    suspend fun deleteRule(ruleId: Long)

    /**
     * Deletes the default rule for a given Booklist.
     */
    @Query("DELETE FROM transition_rules WHERE BooklistId = :BooklistId AND fromTrackId IS NULL AND toTrackId IS NULL")
    suspend fun deleteBooklistDefaultRule(BooklistId: String)
}
