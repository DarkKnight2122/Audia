package com.oakiha.audia.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Playlist(
    val id: String,
    val name: String,
    val trackIds: List<String> = emptyList(),
    val coverImageUri: String? = null,
    val coverColorArgb: Int? = null,
    val coverIconName: String? = null,
    val coverShapeType: String? = null, // Stored as string name of enum
    val coverShapeDetail1: Float? = null, // Changed to Float based on ViewModel usage
    val coverShapeDetail2: Float? = null,
    val coverShapeDetail3: Float? = null,
    val coverShapeDetail4: Float? = null,
    val lastModified: Long = System.currentTimeMillis(), // Changed from dateModified to match usage
    val dateCreated: Long = System.currentTimeMillis(),
    val isAiGenerated: Boolean = false,
    val isQueueGenerated: Boolean = false
)

enum class PlaylistShapeType {
    Circle,
    Square,
    RoundedSquare,
    Squircle,
    Star,
    Flower,
    SmoothRect,
    RotatedPill,
    Custom
}
