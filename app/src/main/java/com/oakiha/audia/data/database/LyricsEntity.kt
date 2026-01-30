package com.oakiha.audia.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "Transcript")
data class TranscriptEntity(
    @PrimaryKey val TrackId: Long,
    val content: String,
    val isSynced: Boolean = false,
    val source: String? = null // "local", "remote", "embedded" - optional
)
