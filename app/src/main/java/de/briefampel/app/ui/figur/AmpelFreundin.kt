package de.briefampel.app.ui.figur

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.briefampel.app.domain.Ampel
import de.briefampel.app.ui.theme.Bewegung
import de.briefampel.app.ui.theme.LocalWenigBewegung
import de.briefampel.app.ui.theme.Signal
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** Was die Figur gerade tut. Bestimmt Lampen, Blick und Mimik. */
enum class Stimmung { WARTET, LIEST, ROT, GELB, GRUEN }

fun Ampel.alsStimmung(): Stimmung = when (this) {
    Ampel.ROT -> Stimmung.ROT
    Ampel.GELB -> Stimmung.GELB
    Ampel.GRUEN -> Stimmung.GRUEN
}

private fun Stimmung.ampel(): Ampel? = when (this) {
    Stimmung.ROT -> Ampel.ROT
    Stimmung.GELB -> Ampel.GELB
    Stimmung.GRUEN -> Ampel.GRUEN
    else -> null
}

/**
 * Die Briefampel als warmherzige Beschützerin: Gehäuse mit Gesicht, Hut und drei Glaslampen.
 * Alles wird lokal gezeichnet; die Maße skalieren mit [lampe] (Referenz 64 dp).
 *
 * @param hutLuepfen jede Änderung des Werts lässt sie einmal den Hut lüpfen.
 * @param lampenMitte meldet die Mitte jeder Lampe im Fenster, z. B. für den Farbschein dahinter.
 */
@Composable
fun AmpelFreundin(
    stimmung: Stimmung,
    beschreibung: String,
    modifier: Modifier = Modifier,
    lampe: Dp = 64.dp,
    hutLuepfen: Int = 0,
    lampenMitte: ((Ampel, Offset) -> Unit)? = null,
) {
    val s = lampe.value / 64f
    val wenig = LocalWenigBewegung.current
    val dichte = LocalDensity.current
    val endlos = rememberInfiniteTransition(label = "Freundin")
    val atmen by endlos.animateFloat(0f, 1f, infiniteRepeatable(tween(4200, easing = LinearEasing)), label = "Atmen")
    val lesen by endlos.animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing)), label = "Lesen")
    val blick by endlos.animateFloat(0f, 1f, infiniteRepeatable(tween(1300, easing = LinearEasing)), label = "Blick")

    val blinzeln = remember { Animatable(1f) }
    LaunchedEffect(wenig) {
        if (wenig) return@LaunchedEffect
        while (true) {
            delay(Random.nextLong(2800, 5200))
            blinzeln.animateTo(0.12f, tween(80))
            blinzeln.animateTo(1f, tween(130))
        }
    }

    // Ohne Animationen leuchtet die Lampe schon im ersten Bild, nicht erst nach dem ersten Effekt.
    val zuendung = remember { Animatable(if (wenig && stimmung.ampel() != null) 1f else 0f) }
    LaunchedEffect(stimmung) {
        if (stimmung.ampel() == null) { zuendung.snapTo(0f); return@LaunchedEffect }
        zuendung.snapTo(0f)
        if (wenig) zuendung.snapTo(1f) else zuendung.animateTo(1f, tween(1200, easing = Bewegung.Ruhig))
    }

    val hut = remember { Animatable(0f) }
    LaunchedEffect(hutLuepfen) {
        if (hutLuepfen == 0 || wenig) return@LaunchedEffect
        launch {
            hut.animateTo(1f, tween(360, easing = Bewegung.Ruhig))
            delay(260)
            hut.animateTo(0f, spring(dampingRatio = 0.45f, stiffness = 260f))
        }
    }

    val neigung by animateFloatAsState(if (stimmung == Stimmung.ROT) 16f else 0f, spring(0.6f, 180f), label = "Brauen")
    val hub by animateFloatAsState(when (stimmung) { Stimmung.GRUEN -> -3f; Stimmung.LIEST -> -1.5f; else -> 0f }, spring(0.6f, 180f), label = "Hub")
    val mundBreite by animateFloatAsState(if (stimmung == Stimmung.GRUEN) 24f else 16f, spring(0.55f, 160f), label = "Mund")
    val mundTiefe by animateFloatAsState(if (stimmung == Stimmung.GRUEN) 12f else 8f, spring(0.55f, 160f), label = "Lächeln")
    val blickY by animateFloatAsState(when (stimmung) { Stimmung.WARTET -> 1.5f; Stimmung.LIEST -> 1f; else -> 0f }, label = "BlickY")

    val atmenPx = if (wenig) 0f else with(dichte) { (-(sin(atmen * 2 * PI).toFloat() + 1f) / 2f * 4.dp.toPx()) }
    val pupilleX = if (stimmung == Stimmung.LIEST && !wenig) sin(blick * 2 * PI).toFloat() * 3f else 0f

    Box(
        modifier
            .semantics { contentDescription = beschreibung }
            .graphicsLayer { translationY = atmenPx }
            .padding(top = 22.dp * s),
    ) {
        val form = RoundedCornerShape(percent = 50)
        Column(
            Modifier
                .width(lampe + 40.dp * s)
                .shadow(28.dp * s, form, clip = false, ambientColor = Color.Black, spotColor = Color.Black)
                .background(Brush.verticalGradient(listOf(Signal.GehaeuseOben, Signal.GehaeuseUnten)), form)
                .border(1.dp, Signal.GehaeuseKante, form)
                .padding(start = 20.dp * s, end = 20.dp * s, top = 14.dp * s, bottom = 20.dp * s),
            verticalArrangement = Arrangement.spacedBy(18.dp * s),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Canvas(Modifier.size(64.dp * s, 46.dp * s)) {
                gesicht(neigung, hub, blinzeln.value, pupilleX, blickY, mundBreite, mundTiefe)
            }
            Ampel.entries.forEachIndexed { index, farbe ->
                val an = when {
                    stimmung == Stimmung.LIEST && !wenig -> puls(lesen, index)
                    stimmung.ampel() == farbe -> zuendung.value
                    else -> 0f
                }
                Canvas(
                    Modifier
                        .size(lampe)
                        .onGloballyPositioned { k -> lampenMitte?.invoke(farbe, k.boundsInRoot().center) },
                ) { lampe(farbe, an, if (stimmung.ampel() == farbe) zuendung.value else 1f) }
            }
        }
        Canvas(
            Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-21).dp * s)
                .size(82.dp * s, 28.dp * s)
                .graphicsLayer {
                    rotationZ = -7f - 9f * hut.value
                    translationY = -10.dp.toPx() * s * hut.value
                },
        ) { hut() }
    }
}

/** Lesend: die Lampen laufen nacheinander durch, ruhig statt hektisch. */
private fun puls(t: Float, index: Int): Float {
    val lokal = ((t - index / 3f) % 1f + 1f) % 1f
    return when {
        lokal < 0.3f -> 0.9f * lokal / 0.3f
        lokal < 0.62f -> 0.9f * (1f - (lokal - 0.3f) / 0.32f)
        else -> 0f
    }
}

private val lampenFarben = mapOf(
    Ampel.ROT to (listOf(Color(0xFF3B2421), Color(0xFF251615), Color(0xFF190F0E)) to listOf(Color(0xFFFFB3A6), Signal.Rot, Color(0xFFC42A1B))),
    Ampel.GELB to (listOf(Color(0xFF392E1A), Color(0xFF241D11), Color(0xFF18130C)) to listOf(Color(0xFFFFE2A8), Signal.Gelb, Color(0xFFC07A12))),
    Ampel.GRUEN to (listOf(Color(0xFF1C3327), Color(0xFF12211A), Color(0xFF0C1611)) to listOf(Color(0xFFB4F7D3), Signal.Gruen, Color(0xFF188A50))),
)

/** Eine Glaslampe. Aus: ahnt man noch ihre Farbe. An: leuchtet sie mit weichem Schein über das Gehäuse. */
private fun DrawScope.lampe(farbe: Ampel, an: Float, zuendung: Float) {
    val (aus, ein) = lampenFarben.getValue(farbe)
    val r = size.minDimension / 2f
    val glanz = Offset(size.width * 0.38f, size.height * 0.32f)
    drawCircle(Brush.radialGradient(aus, glanz, r * 1.4f), r)
    drawCircle(Brush.radialGradient(0.72f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.55f), center = center, radius = r), r)
    if (an <= 0f) return
    val a = min(an, 1f)
    // Zündung: kurzes Überstrahlen, dann ruhiges Leuchten.
    val blitz = 1f + 0.6f * (1f - zuendung)
    val groesse = if (zuendung < 0.3f) 0.9f + 0.17f * (zuendung / 0.3f) else 1.07f - 0.07f * ((zuendung - 0.3f) / 0.7f)
    val signal = ein[1]
    drawCircle(
        Brush.radialGradient(
            0f to signal.copy(alpha = 0.55f * a * min(blitz, 1.4f)),
            0.45f to signal.copy(alpha = 0.22f * a),
            1f to Color.Transparent,
            center = center, radius = r * 2.6f,
        ),
        r * 2.6f,
    )
    scale(groesse.coerceIn(0.85f, 1.1f)) {
        drawCircle(Brush.radialGradient(ein, Offset(size.width * 0.38f, size.height * 0.30f), r * 1.5f), r, alpha = a)
    }
}

/** Gesicht in Einheiten eines 64 × 46 großen Rasters. */
private fun DrawScope.gesicht(neigung: Float, hub: Float, blinzeln: Float, pupilleX: Float, pupilleY: Float, mundBreite: Float, mundTiefe: Float) {
    val u = size.width / 64f
    val strich = Stroke(width = 3f * u, cap = StrokeCap.Round)
    // Brauen: bei Rot innen angehoben, das wirkt mitfühlend statt böse.
    for ((mitte, richtung) in listOf(16f to -1f, 48f to 1f)) {
        rotate(richtung * neigung, pivot = Offset(mitte * u, 5f * u)) {
            drawArc(Signal.Kreide, 200f, 140f, false, Offset((mitte - 8f) * u, (1f + hub) * u), Size(16f * u, 10f * u), style = strich)
        }
    }
    for (mitte in listOf(16f, 48f)) {
        val auge = Offset((mitte - 8f) * u, 10f * u)
        val augeGroesse = Size(16f * u, 18f * u)
        scale(1f, blinzeln, pivot = Offset(mitte * u, 19f * u)) {
            drawOval(Signal.Augenweiss, auge, augeGroesse)
            clipPath(Path().apply { addOval(androidx.compose.ui.geometry.Rect(auge, augeGroesse)) }) {
                val p = Offset((mitte - 4f + pupilleX) * u, (16f + pupilleY) * u)
                drawOval(Signal.Pupille, p, Size(8f * u, 9f * u))
                drawCircle(Color.White, 1.5f * u, Offset(p.x + 2.5f * u, p.y + 2.5f * u))
            }
        }
    }
    for (x in listOf(6f, 58f)) {
        drawCircle(Brush.radialGradient(listOf(Signal.Wange.copy(alpha = 0.42f), Color.Transparent), Offset(x * u, 32f * u), 7f * u), 7f * u, Offset(x * u, 32f * u))
    }
    drawArc(Signal.Kreide, 20f, 140f, false, Offset((32f - mundBreite / 2f) * u, (31f - mundTiefe) * u), Size(mundBreite * u, mundTiefe * 2f * u), style = strich)
}

/** Kleiner Strohhut mit rotem Band und einer Blume in den Ampelfarben, 82 × 28 Einheiten. */
private fun DrawScope.hut() {
    val v = size.width / 82f
    drawRoundRect(Signal.Kreide, Offset(18f * v, 0f), Size(46f * v, 22f * v), CornerRadius(7f * v))
    drawRect(Signal.Rot, Offset(18f * v, 13f * v), Size(46f * v, 6f * v))
    drawCircle(Signal.Gruen, 4f * v, Offset(49f * v, 15f * v))
    drawCircle(Signal.Gelb, 5f * v, Offset(55f * v, 12f * v))
    drawCircle(Color(0xFFE08F1A), 1.6f * v, Offset(55f * v, 12f * v))
    drawRoundRect(Color.Black.copy(alpha = 0.3f), Offset(1f * v, 23f * v), Size(80f * v, 6f * v), CornerRadius(3.5f * v))
    drawRoundRect(Signal.Hutkrempe, Offset(0f, 20f * v), Size(82f * v, 7f * v), CornerRadius(3.5f * v))
}

/** Cremefarbener Regenschirm mit rotem Knauf, für das Intro. */
@Composable
fun Regenschirm(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val v = size.width / 230f
        val dach = Path().apply {
            moveTo(5f * v, 92f * v)
            quadraticTo(115f * v, -8f * v, 225f * v, 92f * v)
            quadraticTo(197f * v, 76f * v, 170f * v, 92f * v)
            quadraticTo(143f * v, 76f * v, 115f * v, 92f * v)
            quadraticTo(88f * v, 76f * v, 60f * v, 92f * v)
            quadraticTo(33f * v, 76f * v, 5f * v, 92f * v)
            close()
        }
        drawPath(dach, Signal.Kreide)
        val rippe = Stroke(1.5f * v)
        for (x in listOf(60f, 115f, 170f)) drawLine(Color(0xFFCFC9BD), Offset(115f * v, 10f * v), Offset(x * v, 92f * v), rippe.width)
        drawCircle(Signal.Rot, 5f * v, Offset(115f * v, 6f * v))
        val stiel = Path().apply {
            moveTo(115f * v, 92f * v)
            lineTo(115f * v, 186f * v)
            quadraticTo(115f * v, 197f * v, 105f * v, 197f * v)
            quadraticTo(97f * v, 197f * v, 97f * v, 189f * v)
        }
        drawPath(stiel, Color(0xFFC9C4B8), style = Stroke(3.5f * v, cap = StrokeCap.Round))
    }
}

/** Ein Briefumschlag, optional mit rotem Siegel. */
@Composable
fun Briefumschlag(modifier: Modifier = Modifier, siegel: Boolean = false) {
    Canvas(modifier) {
        val r = CornerRadius(5.dp.toPx())
        drawRoundRect(Color.Black.copy(alpha = 0.35f), Offset(0f, 8.dp.toPx()), size, r)
        drawRoundRect(Color(0xFFECE8DF), cornerRadius = r)
        drawRoundRect(Color(0xFFD2CDC2), cornerRadius = r, style = Stroke(1.dp.toPx()))
        val falz = Path().apply {
            moveTo(2.dp.toPx(), 3.dp.toPx())
            lineTo(size.width / 2f, size.height * 0.54f)
            lineTo(size.width - 2.dp.toPx(), 3.dp.toPx())
        }
        drawPath(falz, Color(0xFFBDB8AD), style = Stroke(1.6.dp.toPx()))
        if (siegel) {
            val m = Offset(size.width / 2f, size.height * 0.54f)
            val rs = size.minDimension * 0.11f
            drawCircle(Brush.radialGradient(listOf(Color(0xFFFF7A69), Color(0xFFD63A2A), Color(0xFFA72417)), m - Offset(rs * 0.3f, rs * 0.3f), rs * 1.4f), rs, m)
        }
    }
}
