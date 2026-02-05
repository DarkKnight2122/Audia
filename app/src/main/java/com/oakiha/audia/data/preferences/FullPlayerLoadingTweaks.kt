package com.oakiha.audia.data.preferences

data class FullPlayerLoadingTweaks(
    val delayAll: Boolean = true,
    val delayAlbumCarousel: Boolean = true,
    val delayTrackMetadata: Boolean = true,
    val delayProgressBar: Boolean = true,
    val delayControls: Boolean = true,
    val showPlaceholders: Boolean = true,
    val transparentPlaceholders: Boolean = false,
    val contentAppearThresholdPercent: Int = 80
)
