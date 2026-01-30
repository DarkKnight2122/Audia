package com.oakiha.audia.data.model

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class Booklist(
    val id: String,
    var name: String,
    var TrackIds: List<String>,
    val createdAt: Long = System.currentTimeMillis(),
    var lastModified: Long = System.currentTimeMillis(),
    val isAiGenerated: Boolean = false,
    val isQueueGenerated: Boolean = false,
    val coverImageUri: String? = null,
    val coverColorArgb: Int? = null,
    val coverIconName: String? = null,
    val coverShapeType: String? = null, // "Circle", "SmoothRect", etc. Storing as String to avoid Enum import issues if moved
    val coverShapeDetail1: Float? = null, // e.g., CornerRadius / StarCurve
    val coverShapeDetail2: Float? = null, // e.g., Smoothness / StarRotation
    val coverShapeDetail3: Float? = null, // e.g., StarScale
    val coverShapeDetail4: Float? = null // e.g., Star Sides (Int)
)

enum class BooklistshapeType {
    Circle,
    SmoothRect,
    RotatedPill,
    Star
}
