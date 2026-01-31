package com.oakiha.audia.ui.theme.glass

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Applies a "Liquid Glass" refraction effect to the content.
 * Requires API 33+ for the full shader effect. 
 * Fallback to standard blur on API 31-32.
 */
fun Modifier.liquidGlass(
    refractionHeight: Dp = 20.dp,
    refractionAmount: Float = 0.15f,
    depthEffect: Float = 0.05f,
    cornerRadius: Dp = 28.dp
): Modifier = composed {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return@composed this

    val shader = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            RuntimeShader(GlassRefractionShaderString)
        } else null
    }

    this.graphicsLayer {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && shader != null) {
            shader.setFloatUniform("size", size.width, size.height)
            shader.setFloatUniform("offset", 0f, 0f)
            shader.setFloatUniform("cornerRadii", cornerRadius.toPx(), cornerRadius.toPx(), cornerRadius.toPx(), cornerRadius.toPx())
            shader.setFloatUniform("refractionHeight", refractionHeight.toPx())
            shader.setFloatUniform("refractionAmount", refractionAmount * density)
            shader.setFloatUniform("depthEffect", depthEffect)
            
            renderEffect = RenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect()
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Fallback to simple blur on API 31-32
            renderEffect = RenderEffect.createBlurEffect(
                refractionHeight.toPx() / 2f,
                refractionHeight.toPx() / 2f,
                android.graphics.Shader.TileMode.CLAMP
            ).asComposeRenderEffect()
        }
        
        clip = true
    }
}
