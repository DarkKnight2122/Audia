package com.oakiha.audia.presentation.components.subcomps

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider

/**
 * Un Composable de Glance que ajusta automÃ¡ticamente el tamaÃ±o de la fuente del texto
 * para llenar las dimensiones especificadas.
 *
 * NOTA: A diferencia de Jetpack Compose, Glance requiere que se especifiquen explÃ­citamente
 * el ancho y el alto (`width` y `height`) para que el cÃ¡lculo funcione.
 *
 * @param text El texto a mostrar.
 * @param modifier El GlanceModifier a aplicar al contenedor.
 * @param style El estilo base del texto. El tamaÃ±o de la fuente se sobrescribirÃ¡, pero se respetarÃ¡n
 * propiedades como fontWeight.
 * @param color El color del texto.
 * @param width El ancho exacto del Ã¡rea disponible para el texto.
 * @param height La altura exacta del Ã¡rea disponible para el texto.
 * @param textAlign La alineaciÃ³n del texto.
 * @param minFontSize El tamaÃ±o de fuente mÃ¡s pequeÃ±o permitido.
 * @param maxFontSize El tamaÃ±o de fuente mÃ¡s grande permitido.
 */
@Composable
fun AutoSizingTextGlance(
    text: String,
    modifier: GlanceModifier = GlanceModifier,
    style: TextStyle,
    color: ColorProvider,
    width: Dp,
    height: Dp,
    textAlign: TextAlign = TextAlign.Start,
    minFontSize: TextUnit = 8.sp,
    maxFontSize: TextUnit = 100.sp
) {
    val context = LocalContext.current
    val textColor = color.getColor(context).toArgb()
    val density = context.resources.displayMetrics.density

    // Convertir dimensiones Dp a PÃ­xeles
    val widthPx = (width.value * density).toInt()
    val heightPx = (height.value * density).toInt()

    // Crear el bitmap que contendrÃ¡ el texto renderizado
    val bitmap = Bitmap.createBitmap(widthPx.coerceAtLeast(1), heightPx.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)

    // Configurar TextPaint para medir y dibujar el texto
    val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = textColor
        // Aplicar fontWeight del estilo
        this.typeface = when (style.fontWeight) {
            FontWeight.Bold -> Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            FontWeight.Medium -> Typeface.create("sans-serif-medium", Typeface.NORMAL) // Requiere API 21+
            else -> Typeface.DEFAULT
        }
    }

    // Mapear TextAlign de Glance a Layout.Alignment de Android
    val alignment = when (textAlign) {
        TextAlign.Center -> Layout.Alignment.ALIGN_CENTER
        TextAlign.End -> Layout.Alignment.ALIGN_OPPOSITE
        else -> Layout.Alignment.ALIGN_NORMAL // Start, Left
    }


    // --- BÃºsqueda binaria para el tamaÃ±o de fuente Ã³ptimo ---
    var lowerBound = minFontSize.value
    var upperBound = maxFontSize.value
    var bestSize = lowerBound

    // Realizar la bÃºsqueda solo si el Ã¡rea es vÃ¡lida
    if (widthPx > 0 && heightPx > 0) {
        while (lowerBound <= upperBound) {
            val mid = (lowerBound + upperBound) / 2
            if (mid <= 0) break // Evitar tamaÃ±os de fuente no vÃ¡lidos

            textPaint.textSize = mid * density

            // StaticLayout es la herramienta de Android para manejar texto multilÃ­nea y con saltos de lÃ­nea.
            val staticLayout = StaticLayout.Builder.obtain(text, 0, text.length, textPaint, widthPx)
                .setAlignment(alignment)
                .setLineSpacing(0f, 1f)
                .setIncludePad(false)
                .build()

            // Si el texto cabe en la altura, es un candidato vÃ¡lido. Intentamos un tamaÃ±o mayor.
            if (staticLayout.height <= heightPx) {
                bestSize = mid
                lowerBound = mid + 0.1f
            } else {
                // Si no cabe, necesitamos un tamaÃ±o mÃ¡s pequeÃ±o.
                upperBound = mid - 0.1f
            }
        }
    }
    // --- Fin de la bÃºsqueda binaria ---

    // Dibujar el texto final en el canvas con el mejor tamaÃ±o de fuente encontrado
    textPaint.textSize = bestSize * density
    val finalLayout = StaticLayout.Builder.obtain(text, 0, text.length, textPaint, widthPx)
        .setAlignment(alignment)
        .setLineSpacing(0f, 1f)
        .setIncludePad(false)
        .build()

    finalLayout.draw(canvas)

    // Mostrar el bitmap renderizado en un Composable Image
    Box(
        modifier = modifier.size(width, height),
        contentAlignment = Alignment.CenterStart
    ) {
        Image(
            provider = ImageProvider(bitmap),
            contentDescription = text, // Usar el texto como descripciÃ³n de contenido
            modifier = GlanceModifier
                .fillMaxSize()
                //.size(width, height)
        )
    }
}
