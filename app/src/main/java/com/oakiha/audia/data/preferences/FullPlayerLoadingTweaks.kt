package com.oakiha.audia.data.preferences

data class FullPlayerLoadingTweaks(
    val delayAll: Boolean = true,
    val delayBookCarousel: Boolean = false,
    val delayTrackMetadata: Boolean = false,
    val delayProgressBar: Boolean = false,
    val delayControls: Boolean = false,
    val showPlaceholders: Boolean = false,
    val transparentPlaceholders: Boolean = false,
    val contentAppearThresholdPercent: Int = 100
)
