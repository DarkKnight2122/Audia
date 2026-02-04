package com.oakiha.audia.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.oakiha.audia.presentation.viewmodel.ColorSchemePair
import androidx.core.graphics.ColorUtils
import androidx.compose.ui.unit.dp

val LocalAudioBookPlayerDarkTheme = staticCompositionLocalOf { false }

val DarkColorScheme = darkColorScheme(
    primary = AudioBookPlayerPurplePrimary,
    secondary = AudioBookPlayerPink,
    tertiary = AudioBookPlayerOrange,
    background = AudioBookPlayerPurpleDark,
    surface = AudioBookPlayerSurface,
    onPrimary = AudioBookPlayerWhite,
    onSecondary = AudioBookPlayerWhite,
    onTertiary = AudioBookPlayerWhite,
    onBackground = AudioBookPlayerWhite,
    onSurface = AudioBookPlayerLightPurple, // Texto sobre superficies
    error = Color(0xFFFF5252),
    onError = AudioBookPlayerWhite
)

val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = AudioBookPlayerWhite,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    secondary = AudioBookPlayerPink,
    onSecondary = AudioBookPlayerWhite,
    secondaryContainer = AudioBookPlayerPink.copy(alpha = 0.15f),
    onSecondaryContainer = AudioBookPlayerPink.copy(alpha = 0.85f),
    tertiary = AudioBookPlayerOrange,
    onTertiary = AudioBookPlayerBlack,
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    outlineVariant = LightOutline.copy(alpha = 0.6f),
    surfaceTint = LightPrimary,
    error = Color(0xFFD32F2F),
    onError = AudioBookPlayerWhite
)

@Composable
fun AudioBookPlayerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    usePureBlack: Boolean = false,
    appThemeStyle: com.oakiha.audia.data.model.AppThemeStyle = com.oakiha.audia.data.model.AppThemeStyle.System,
    colorSchemePairOverride: ColorSchemePair? = null,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val isBlackMode = appThemeStyle == com.oakiha.audia.data.model.AppThemeStyle.Black || (darkTheme && usePureBlack)
    
    val isGlassMode = appThemeStyle == com.oakiha.audia.data.model.AppThemeStyle.GLASS
    
    val baseDarkScheme = if (isBlackMode) {
        DarkColorScheme.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceContainer = Color(0xFF111111),
            surfaceContainerHigh = Color(0xFF1A111A),
            surfaceContainerHighest = Color(0xFF222222),
            surfaceContainerLow = Color(0xFF0A0A0A),
            surfaceContainerLowest = Color(0xFF050505)
        )
    } else if (isGlassMode) {
        DarkColorScheme.copy(
            background = Color.Transparent,
            surface = Color.White.copy(alpha = 0.05f),
            surfaceContainer = Color.White.copy(alpha = 0.08f),
            surfaceContainerHigh = Color.White.copy(alpha = 0.12f),
            surfaceContainerHighest = Color.White.copy(alpha = 0.15f),
            surfaceContainerLow = Color.Black.copy(alpha = 0.05f),
            surfaceContainerLowest = Color.Black.copy(alpha = 0.1f)
        )
    } else {
        DarkColorScheme
    }

    val finalColorScheme = when {
        colorSchemePairOverride == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !isBlackMode && !isGlassMode -> {
            // Tema dinÃ¡mico del sistema como prioridad si no hay override Y no es un modo especial
            try {
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            } catch (e: Exception) {
                // Fallback a los defaults si dynamic colors falla (raro, pero posible en algunos dispositivos)
                if (darkTheme) baseDarkScheme else LightColorScheme
            }
        }
        colorSchemePairOverride != null -> {
            // Usar el esquema del Ã¡lbum si se proporciona
            if (darkTheme) colorSchemePairOverride.dark else colorSchemePairOverride.light
        }
        // Fallback final a los defaults si no hay override ni dynamic colors aplicables
        darkTheme -> baseDarkScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val statusBarElevation = if (darkTheme) 4.dp else 12.dp
            val elevatedSurface = finalColorScheme.surfaceColorAtElevation(statusBarElevation)
            val statusBarColor = Color(ColorUtils.blendARGB(finalColorScheme.background.toArgb(), elevatedSurface.toArgb(), 0.35f))
            // window.statusBarColor = statusBarColor.toArgb()
            val isLightStatusBar = ColorUtils.calculateLuminance(statusBarColor.toArgb()) > 0.55
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = isLightStatusBar
        }
    }

    CompositionLocalProvider(LocalAudioBookPlayerDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = finalColorScheme,
            typography = Typography,
            shapes = Shapes,
            content = content
        )
    }
}
