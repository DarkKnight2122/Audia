package com.oakiha.audia.ui.theme

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

object SmoothRect : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val path = Path().apply {
            addRoundRect(
                RoundRect(
                    left = 0f, top = 0f,
                    right = size.width, bottom = size.height,
                    radiusX = size.width * 0.25f, radiusY = size.height * 0.25f
                )
            )
        }
        return Outline.Generic(path)
    }
}

object RotatedPill : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val path = Path().apply {
             // Pill shape (capsule)
             val radius = minOf(size.width, size.height) / 2f
             addRoundRect(
                 RoundRect(
                     left = 0f, top = 0f,
                     right = size.width, bottom = size.height,
                     radiusX = radius, radiusY = radius
                 )
             )
        }
        // Rotation is usually handled by modifier, but if this shape implies intrinsic rotation...
        // For now, standard pill.
        return Outline.Generic(path)
    }
}
