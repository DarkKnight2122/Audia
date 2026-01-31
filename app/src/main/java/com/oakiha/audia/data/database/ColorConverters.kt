package com.oakiha.audia.data.database

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.toColorInt

fun String.toComposeColor(): Color {
    // AÃ±adir manejo de errores para parseo de color
    return try {
        Color(this.toColorInt())
    } catch (e: IllegalArgumentException) {
        // Log error o devolver un color por defecto si el string no es vÃ¡lido
        Color.Black // Fallback color
    }
}
