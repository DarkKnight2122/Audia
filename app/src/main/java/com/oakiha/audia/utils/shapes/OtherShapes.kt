package com.oakiha.audia.utils.shapes

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

fun createHexagonShape() = object : Shape {
    override fun createOutline(
        size: androidx.compose.ui.geometry.Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        return Outline.Generic(Path().apply {
            val radius = minOf(size.width, size.height) / 2f
            val angle = 2.0 * Math.PI / 6
            moveTo(size.width / 2f + radius * cos(0.0).toFloat(), size.height / 2f + radius * sin(0.0).toFloat())
            for (i in 1..6) {
                lineTo(size.width / 2f + radius * cos(angle * i).toFloat(), size.height / 2f + radius * sin(angle * i).toFloat())
            }
            close()
        })
    }
}

// Implementaciones similares para createRoundedTriangleShape, createSemiCircleShape
// (Estas pueden ser mÃ¡s complejas dependiendo del diseÃ±o exacto que quieras)

// Ejemplo simple de triÃ¡ngulo redondeado (tendrÃ­as que ajustarlo)
fun createRoundedTriangleShape() = object : Shape {
    override fun createOutline(size: androidx.compose.ui.geometry.Size, layoutDirection: LayoutDirection, density: Density): Outline {
        return Outline.Generic(Path().apply {
            val path = Path()
            path.moveTo(size.width / 2f, 0f)
            path.lineTo(size.width, size.height)
            path.lineTo(0f, size.height)
            path.close()

            // Para redondear las esquinas, podrÃ­as usar CornerPathEffect en un Modifier.drawBehind,
            // o construir la forma con arcos y lÃ­neas. Clipping con Shape solo recorta.
            // Una forma simple es usar un RoundRect para el clip con radios grandes, pero no es un triÃ¡ngulo real.
            // Para un triÃ¡ngulo redondeado preciso, tendrÃ­as que dibujar la forma con arcos.
            // Por ahora, dejaremos el clip simple o necesitarÃ¡s una implementaciÃ³n mÃ¡s avanzada.

            // Alternativa simple: clip a un rectÃ¡ngulo con esquinas redondeadas
            // return Outline.Rounded(RoundRect(0f, 0f, size.width, size.height, CornerRadius(16f, 16f)))
            // Esto no es un triÃ¡ngulo. Necesitas una implementaciÃ³n real de forma de triÃ¡ngulo redondeado.
            // Por simplicidad en este ejemplo, usaremos formas mÃ¡s estÃ¡ndar o de la librerÃ­a.

            // Para el ejemplo, simplemente usaremos un triÃ¡ngulo bÃ¡sico sin redondeo complejo en el clip.
            // Si necesitas triÃ¡ngulos redondeados reales, busca implementaciones mÃ¡s avanzadas.
            moveTo(size.width / 2f, 0f)
            lineTo(size.width, size.height * 0.8f) // Ajuste para que la base no llegue hasta abajo
            lineTo(0f, size.height * 0.8f)
            close()

        })
    }
}

// Ejemplo simple de SemicÃ­rculo (tendrÃ­as que ajustarlo)
fun createSemiCircleShape() = object : Shape {
    override fun createOutline(size: androidx.compose.ui.geometry.Size, layoutDirection: LayoutDirection, density: Density): Outline {
        return Outline.Generic(Path().apply {
            arcTo(
                rect = Rect(0f, 0f, size.width, size.width), // Un cÃ­rculo basado en el ancho
                startAngleDegrees = 0f,
                sweepAngleDegrees = 180f,
                forceMoveTo = false
            )
            lineTo(size.width / 2f, size.width / 2f) // Dibuja una lÃ­nea hacia el centro si necesitas cerrarlo como pastel
            close() // Opcional: cierra la forma
        })
    }
}

/**
 * Crea una forma de hexÃ¡gono con esquinas redondeadas.
 */
fun createRoundedHexagonShape(cornerRadius: Dp) = object : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        return Outline.Generic(Path().apply {
            val width = size.width
            val height = size.height
            val radius = min(width, height) / 2f
            val cornerRadiusPx = with(density) { cornerRadius.toPx() }

            // Puntos del hexÃ¡gono sin redondear
            val points = (0..5).map { i ->
                val angle = PI / 3 * i
                Offset(
                    x = width / 2f + radius * cos(angle).toFloat(),
                    y = height / 2f + radius * sin(angle).toFloat()
                )
            }

            // Movemos al primer punto con un offset para empezar el arco
            moveTo(points[0].x + cornerRadiusPx * cos(PI / 3.0).toFloat(), points[0].y + cornerRadiusPx * sin(PI / 3.0).toFloat())

            for (i in 0..5) {
                val p1 = points[i]
                val p2 = points[(i + 1) % 6]
                val p3 = points[(i + 2) % 6]

                // LÃ­nea hacia el punto de inicio del arco
                lineTo(p2.x - cornerRadiusPx * cos(PI / 3.0).toFloat(), p2.y - cornerRadiusPx * sin(PI / 3.0).toFloat())

                // Arco en la esquina
                arcTo(
                    rect = Rect(
                        left = p2.x - cornerRadiusPx,
                        top = p2.y - cornerRadiusPx,
                        right = p2.x + cornerRadiusPx,
                        bottom = p2.y + cornerRadiusPx
                    ),
                    startAngleDegrees = (i * 60 + 30).toFloat(), // Ãngulo de inicio del arco
                    sweepAngleDegrees = 60f, // Ãngulo del arco
                    forceMoveTo = false
                )
            }
            close()
        })
    }
}

/**
 * Crea una forma de triÃ¡ngulo con esquinas redondeadas.
 * ImplementaciÃ³n simple para clipping.
 */
fun createRoundedTriangleShape(cornerRadius: Dp) = object : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        return Outline.Generic(Path().apply {
            val width = size.width
            val height = size.height
            val cornerRadiusPx = with(density) { cornerRadius.toPx() }

            // Puntos del triÃ¡ngulo
            val p1 = Offset(width / 2f, 0f) // Superior
            val p2 = Offset(width, height) // Inferior derecha
            val p3 = Offset(0f, height) // Inferior izquierda

            // Para simplificar el redondeo en el clip, usaremos arcos.
            // Esto no es un triÃ¡ngulo perfecto con arcos tangentes, sino un enfoque prÃ¡ctico para clipping.

            // Calcula puntos de control para los arcos
            val control12 = Offset(p1.x + (p2.x - p1.x) * 0.8f, p1.y + (p2.y - p1.y) * 0.8f)
            val control23 = Offset(p2.x + (p3.x - p2.x) * 0.2f, p2.y + (p3.y - p2.y) * 0.2f)
            val control31 = Offset(p3.x + (p1.x - p3.x) * 0.8f, p3.y + (p1.y - p3.y) * 0.8f)


            moveTo(p1.x, p1.y + cornerRadiusPx * 2) // Empieza un poco mÃ¡s abajo del vÃ©rtice superior

            // Arco superior derecha
            quadraticTo(p1.x, p1.y, p1.x + cornerRadiusPx * sqrt(2f), p1.y + cornerRadiusPx * sqrt(2f))
            lineTo(p2.x - cornerRadiusPx * sqrt(2f), p2.y - cornerRadiusPx * sqrt(2f))

            // Arco inferior derecha
            quadraticTo(p2.x, p2.y, p2.x - cornerRadiusPx * sqrt(2f), p2.y + cornerRadiusPx * sqrt(2f))
            lineTo(p3.x + cornerRadiusPx * sqrt(2f), p3.y + cornerRadiusPx * sqrt(2f))

            // Arco inferior izquierda
            quadraticTo(p3.x, p3.y, p3.x + cornerRadiusPx * sqrt(2f), p3.y - cornerRadiusPx * sqrt(2f))
            lineTo(p1.x - cornerRadiusPx * sqrt(2f), p1.y + cornerRadiusPx * sqrt(2f))

            close() // Cierra la forma
        })
    }
}


/**
 * Crea una forma de semicÃ­rculo con una base ligeramente redondeada.
 */
fun createSemiCircleShape(cornerRadius: Dp) = object : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        return Outline.Generic(Path().apply {
            val width = size.width
            val height = size.height
            val radius = width / 2f
            val cornerRadiusPx = with(density) { cornerRadius.toPx() }

            // Arco superior (semicÃ­rculo)
            arcTo(
                rect = Rect(0f, 0f, width, width), // Un cÃ­rculo basado en el ancho
                startAngleDegrees = 0f,
                sweepAngleDegrees = 180f,
                forceMoveTo = false
            )

            // Base (lÃ­nea con arcos en los extremos)
            val startBaseX = 0f + cornerRadiusPx
            val endBaseX = width - cornerRadiusPx
            val baseY = width / 2f // La base estÃ¡ a la mitad del diÃ¡metro del cÃ­rculo

            lineTo(endBaseX, baseY) // LÃ­nea hacia el final de la base

            // Arco inferior derecho
            arcTo(
                rect = Rect(endBaseX - cornerRadiusPx, baseY - cornerRadiusPx, endBaseX + cornerRadiusPx, baseY + cornerRadiusPx),
                startAngleDegrees = 90f,
                sweepAngleDegrees = -90f, // Arco hacia abajo
                forceMoveTo = false
            )

            lineTo(startBaseX, baseY + cornerRadiusPx) // LÃ­nea inferior

            // Arco inferior izquierdo
            arcTo(
                rect = Rect(startBaseX - cornerRadiusPx, baseY - cornerRadiusPx, startBaseX + cornerRadiusPx, baseY + cornerRadiusPx),
                startAngleDegrees = 180f,
                sweepAngleDegrees = -90f, // Arco hacia abajo
                forceMoveTo = false
            )

            close() // Cierra la forma
        })
    }
}
