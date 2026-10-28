package de.briefampel.app.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import de.briefampel.app.domain.Ampel
import de.briefampel.app.ui.theme.*

/** Resolution independent illustration, drawn locally. No network assets or bitmaps. */
@Composable
fun TrafficLight(ampel: Ampel?, modifier: Modifier = Modifier, reading: Boolean = false) {
    val reduced = LocalWenigBewegung.current
    val transition = rememberInfiniteTransition(label = "Ampel")
    val phase = if (reduced) 0f else transition.animateFloat(
        0f, 1f, infiniteRepeatable(tween(4200, easing = LinearEasing)), label = "Atmen",
    ).value
    val lamp = if (reading) Ampel.entries[(phase * 3).toInt().coerceIn(0, 2)] else ampel
    Canvas(modifier.semantics { contentDescription = if (reading) "Brief wird geprüft" else "Ampel: ${when (ampel) { Ampel.ROT -> "Rot"; Ampel.GELB -> "Gelb"; Ampel.GRUEN -> "Grün"; null -> "bereit" }}" }) {
        val factor = minOf(size.width / 140f, size.height / 390f)
        scale(factor, factor, pivot = Offset.Zero) {
            val left = (size.width / factor - 140) / 2
            val top = if (reduced) 0f else kotlin.math.sin(phase * Math.PI * 2).toFloat() * 2
            val x = left + 22
            val y = 76 + top
            // Soft, color matched halo.
            lamp?.let {
                drawCircle(Brush.radialGradient(listOf(Signal.farbe(it).copy(alpha = .17f), Color.Transparent), Offset(left + 70, 220f), 140f), 140f, Offset(left + 70, 220f))
            }
            drawRoundRect(Brush.verticalGradient(listOf(Signal.GehaeuseOben, Signal.GehaeuseUnten)), Offset(x, y), Size(96f, 290f), CornerRadius(47f))
            drawRoundRect(Signal.GehaeuseKante, Offset(x, y), Size(96f, 290f), CornerRadius(47f), style = androidx.compose.ui.graphics.drawscope.Stroke(1f))
            // Warm little face.
            val blink = !reduced && phase in .92f.. .945f
            val eyeY = y + 39
            for (eyeX in listOf(x + 34, x + 63)) {
                if (blink) drawLine(Signal.Augenweiss, Offset(eyeX - 7, eyeY), Offset(eyeX + 7, eyeY), 2f)
                else {
                    drawOval(Signal.Augenweiss, Offset(eyeX - 7, eyeY - 9), Size(14f, 18f))
                    val pupilX = eyeX + if (reading) kotlin.math.sin(phase * Math.PI * 8).toFloat() * 3 else 0f
                    drawCircle(Signal.Pupille, 4.8f, Offset(pupilX, eyeY + 1))
                    drawCircle(Color.White, 1.7f, Offset(pupilX - 1.4f, eyeY - 1.7f))
                }
                drawOval(Signal.Wange.copy(alpha = .45f), Offset(eyeX - 13, eyeY + 12), Size(11f, 5f))
            }
            drawLine(Signal.Kreide.copy(alpha = .8f), Offset(x + 26, eyeY - 17), Offset(x + 38, eyeY - 20), 2f)
            drawLine(Signal.Kreide.copy(alpha = .8f), Offset(x + 59, eyeY - 20), Offset(x + 71, eyeY - 17), 2f)
            val smile = Path().apply { moveTo(x + 42, eyeY + 15); quadraticTo(x + 49, eyeY + 21, x + 55, eyeY + 14) }
            drawPath(smile, Signal.Kreide, style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
            Ampel.entries.forEachIndexed { index, signal ->
                val cy = y + 104 + index * 73
                val center = Offset(x + 48, cy)
                val on = lamp == signal
                val color = Signal.farbe(signal)
                if (on) drawCircle(Brush.radialGradient(listOf(color.copy(alpha = .42f), Color.Transparent), center, 53f), 53f, center)
                drawCircle(Color(0xFF090B0D), 30f, center)
                drawCircle(Brush.radialGradient(
                    if (on) listOf(Color.White.copy(alpha = .85f), color, color.copy(alpha = .7f))
                    else listOf(color.copy(alpha = .16f), color.copy(alpha = .05f), Color(0xFF0B0D0F)),
                    center - Offset(8f, 10f), 47f,
                ), 27f, center)
                if (on) drawOval(Color.White.copy(alpha = .15f), center - Offset(17f, 20f), Size(24f, 15f))
            }
            // Cream hat, red band, tiny flower.
            rotate(-10f, Offset(left + 70, y - 4)) {
                drawRoundRect(Signal.Kreide, Offset(left + 41, y - 19), Size(59f, 27f), CornerRadius(8f))
                drawRect(Signal.Rot, Offset(left + 41, y - 1), Size(59f, 6f))
                drawRoundRect(Signal.Hutkrempe, Offset(left + 27, y + 7), Size(87f, 6f), CornerRadius(3f))
                drawCircle(Signal.Gruen, 6f, Offset(left + 82, y - 2))
                drawCircle(Signal.Gelb, 7f, Offset(left + 89, y - 8))
                drawCircle(Signal.Kreide, 3f, Offset(left + 89, y - 8))
            }
        }
    }
}
