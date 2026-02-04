package com.oakiha.audia.presentation.components

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

@Composable
fun GradientBackground(
    modifier: Modifier = Modifier,
    isDarkTheme: Boolean = androidx.compose.foundation.isSystemInDarkTheme(),
    content: @Composable () -> Unit = {}
) {
    val infiniteTransition = rememberInfiniteTransition(label = "Gradient")
    
    // Vibrant Palette for Glass effect
    val color1 by infiniteTransition.animateColor(
        initialValue = if (isDarkTheme) Color(0xFF0F0C29) else Color(0xFFFF9A9E), // Midnight vs Sunset Pink
        targetValue = if (isDarkTheme) Color(0xFF302B63) else Color(0xFFFAD0C4),  // Deep Purple vs Peach
        animationSpec = infiniteRepeatable(
            animation = tween(15000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "Color1"
    )

    val color2 by infiniteTransition.animateColor(
        initialValue = if (isDarkTheme) Color(0xFF24243E) else Color(0xFFBEE3F8), // Obsidian vs Sky Blue
        targetValue = if (isDarkTheme) Color(0xFF0F2027) else Color(0xFFE9D5FF),  // Deep Teal vs Lavender
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "Color2"
    )
    
    val bottomColor = if (isDarkTheme) Color(0xFF000000) else Color(0xFFFFFFFF)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(color1, color2, bottomColor)
                )
            )
    ) {
        content()
    }
}
