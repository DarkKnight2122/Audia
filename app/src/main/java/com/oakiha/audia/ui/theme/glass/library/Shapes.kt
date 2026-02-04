package com.oakiha.audia.ui.theme.glass.library

import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.util.fastCoerceAtMost

class RoundedRectangle(val cornerRadius: Dp) : RoundedRectangularShape {
    override fun cornerRadii(size: Size, layoutDirection: LayoutDirection, density: Density): FloatArray {
        val radius = with(density) { cornerRadius.toPx() }.fastCoerceAtMost(size.minDimension / 2f)
        return floatArrayOf(radius, radius, radius, radius)
    }

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val radius = with(density) { cornerRadius.toPx() }.fastCoerceAtMost(size.minDimension / 2f)
        return Outline.Rounded(RoundRect(0f, 0f, size.width, size.height, radius, radius))
    }
}

object Capsule : RoundedRectangularShape {
    override fun cornerRadii(size: Size, layoutDirection: LayoutDirection, density: Density): FloatArray {
        val radius = size.minDimension / 2f
        return floatArrayOf(radius, radius, radius, radius)
    }

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val radius = size.minDimension / 2f
        return Outline.Rounded(RoundRect(0f, 0f, size.width, size.height, radius, radius))
    }
}
