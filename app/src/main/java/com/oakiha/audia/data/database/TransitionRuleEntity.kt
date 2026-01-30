package com.oakiha.audia.data.database

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.oakiha.audia.data.model.TransitionSettings

@Entity(
    tableName = "transition_rules",
    indices = [Index(value = ["BooklistId", "fromTrackId", "toTrackId"], unique = true)]
)
data class TransitionRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val BooklistId: String,
    val fromTrackId: String?,
    val toTrackId: String?,
    @Embedded val settings: TransitionSettings
)
