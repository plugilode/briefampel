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
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import kotlin.math.roundToInt
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
    /** Zeigt zwei Arme mit weißen Handschuhen (fürs Intro). */
    arme: Boolean = false,
    /** 1 = sie hält den Regenschirm hoch über sich, 0 = Arm unten, kein Schirm. Dazwischen sinkt beides. */
    schirm: Float = 0f,
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

        if (arme) {
            val breite = lampe + 40.dp * s
            val hoch = schirm.coerceIn(0f, 1f)
            // Rechte Hand: oben rechts neben dem Hut am Schirmstock, oder locker an der Seite.
            val handX = breite + (12f + 8f * hoch).dp * s
            val handY = (92f - 156f * hoch).dp * s
            if (hoch > 0.01f) {
                Regenschirm(
                    Modifier
                        .layout { messbar, _ ->
                            val b = (196.dp * s).roundToPx()
                            val h = (170.dp * s).roundToPx()
                            val p = messbar.measure(Constraints.fixed(b, h))
                            // Der Schirm braucht keinen Platz im Layout; die Hand greift den Stock knapp über dem Griff.
                            layout(0, 0) { p.place((handX.toPx() - b / 2f).roundToInt(), (handY.toPx() - h * 0.9f).roundToInt()) }
                        }
                        .graphicsLayer { alpha = hoch },
                )
            }
            val winken = if (wenig) 0f else sin(atmen * 2 * PI.toFloat() * 2) * 3f
            Canvas(Modifier.matchParentSize()) {
                val e = 1.dp.toPx() * s
                arme(breite.toPx(), e, Offset(handX.toPx(), handY.toPx()), hoch, winken)
            }
        }
    }
}

/** Zwei schlanke Arme in Gehäusefarbe mit hellem Rand, dazu weiße Handschuhe wie bei einer Zeichentrickfigur. */
private fun DrawScope.arme(breite: Float, e: Float, hand: Offset, hoch: Float, winken: Float) {
    fun mix(a: Offset, b: Offset, t: Float) = Offset(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
    val schulterR = Offset(breite - 3f * e, 46f * e)
    val rechts = Path().apply {
        moveTo(schulterR.x, schulterR.y)
        val k2 = mix(Offset(hand.x - 2f * e, hand.y - 20f * e), Offset(hand.x + 2f * e, hand.y + 34f * e), hoch)
        cubicTo(schulterR.x + 18f * e, schulterR.y, k2.x, k2.y, hand.x, hand.y)
    }
    val schulterL = Offset(3f * e, 50f * e)
    val handL = Offset(-14f * e, (94f + winken) * e)
    val links = Path().apply {
        moveTo(schulterL.x, schulterL.y)
        cubicTo(schulterL.x - 16f * e, schulterL.y, handL.x, handL.y - 22f * e, handL.x, handL.y)
    }
    for (arm in listOf(rechts, links)) {
        drawPath(arm, Color(0xFF4A4F57), style = Stroke(7f * e, cap = StrokeCap.Round))
        drawPath(arm, Color(0xFF24272C), style = Stroke(4.6f * e, cap = StrokeCap.Round))
    }
    for ((h, daumen) in listOf(hand to Offset(-5.5f * e, -1.5f * e), handL to Offset(4.5f * e, -3f * e))) {
        drawCircle(Color.Black.copy(alpha = 0.3f), 8f * e, h + Offset(0f, 1.5f * e))
        drawCircle(Signal.Kreide, 7.5f * e, h)
        drawCircle(Signal.Kreide, 3.2f * e, h + daumen)
        drawCircle(Color(0xFFBDB6A8), 7.5f * e, h, style = Stroke(1.1f * e))
        // Kleine Stulpe am Handgelenk.
        drawCircle(Color(0xFFE4DFD4), 3f * e, h + Offset(0f, 6.5f * e))
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
        fun p(x: Float, y: Float) = Offset(x * v, y * v)
        val spitze = p(115f, 16f)
        // Speichenenden am Saum, leicht von unten gesehen: außen tiefer, Mitte höher.
        val enden = listOf(p(4f, 100f), p(28f, 96f), p(66f, 93f), p(115f, 92f), p(164f, 93f), p(202f, 96f), p(226f, 100f))
        // Die Kuppel wölbt sich: Jede Speiche läuft in einem Bogen von der Spitze nach außen.
        fun bogen(ende: Offset) = Offset(spitze.x + (ende.x - spitze.x) * 1.04f, spitze.y + (ende.y - spitze.y) * 0.18f)

        // Schatten unter dem Schirm, damit er sich vom Grund löst.
        drawOval(
            Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.35f), Color.Transparent), p(115f, 104f), 110f * v),
            p(10f, 90f), Size(210f * v, 28f * v),
        )

        // Stoffbahnen: abwechselnd im Licht und im Schatten, außen dunkler wie auf einer echten Kuppel.
        val bahnen = listOf(
            Color(0xFFB9AE99), Color(0xFFE2D9C7), Color(0xFFF4EEE2), Color(0xFFE8E0CF), Color(0xFFD3C8B3), Color(0xFFA99F8B),
        )
        for (i in 0 until enden.size - 1) {
            val a = enden[i]
            val b = enden[i + 1]
            val mitteSaum = Offset((a.x + b.x) / 2f, (a.y + b.y) / 2f - 9f * v)
            val bahn = Path().apply {
                moveTo(spitze.x, spitze.y)
                quadraticTo(bogen(a).x, bogen(a).y, a.x, a.y)
                quadraticTo(mitteSaum.x, mitteSaum.y, b.x, b.y)
                quadraticTo(bogen(b).x, bogen(b).y, spitze.x, spitze.y)
                close()
            }
            val farbe = bahnen[i]
            drawPath(bahn, Brush.verticalGradient(listOf(farbe.copy(alpha = 1f), lerp(farbe, Color(0xFF6E6656), 0.25f)), spitze.y, a.y))
            // Glanz oben links, wo das Licht auftrifft.
            drawPath(bahn, Brush.radialGradient(listOf(Color.White.copy(alpha = 0.35f), Color.Transparent), p(82f, 34f), 70f * v))
            // Saumkante.
            drawPath(Path().apply { moveTo(a.x, a.y); quadraticTo(mitteSaum.x, mitteSaum.y, b.x, b.y) }, Color(0xFF8F8573), style = Stroke(1.6f * v))
        }
        // Speichen als feine Nähte.
        for (ende in enden) {
            drawPath(Path().apply { moveTo(spitze.x, spitze.y); quadraticTo(bogen(ende).x, bogen(ende).y, ende.x, ende.y) }, Color(0xFF9E947F), style = Stroke(1.1f * v))
        }
        // Metallkappen an den Speichenenden.
        for (ende in enden) {
            drawCircle(Color(0xFF6D6F75), 2.6f * v, ende)
            drawCircle(Color(0xFFC9CCD2), 1.0f * v, ende - Offset(0.8f * v, 0.8f * v))
        }

        // Stock aus Metall mit Glanzlinie, von der Spitze sichtbar ab dem Saum.
        val stockOben = 90f
        val stockUnten = 182f
        drawRect(
            Brush.horizontalGradient(listOf(Color(0xFF4A4C52), Color(0xFFB8BCC4), Color(0xFF55585E)), 112.6f * v, 117.4f * v),
            p(112.6f, stockOben), Size(4.8f * v, (stockUnten - stockOben) * v),
        )
        // Schieber, mit dem man den Schirm öffnet.
        drawRoundRect(Color(0xFF3E4046), p(110.8f, 104f), Size(8.4f * v, 9f * v), CornerRadius(2f * v))
        drawRoundRect(Color(0xFF8E9299), p(111.6f, 105f), Size(2.2f * v, 7f * v), CornerRadius(1f * v))

        // Gebogener Holzgriff: dunkle Unterseite, warmer Körper, heller Glanz.
        val griff = Path().apply {
            moveTo(115f * v, 178f * v)
            lineTo(115f * v, 186f * v)
            cubicTo(115f * v, 201f * v, 93f * v, 201f * v, 93f * v, 189f * v)
        }
        drawPath(griff, Color(0xFF3B2112), style = Stroke(9f * v, cap = StrokeCap.Round))
        drawPath(griff, Brush.linearGradient(listOf(Color(0xFF7A4526), Color(0xFFB0703F), Color(0xFF6B3A1E)), p(90f, 180f), p(118f, 200f)), style = Stroke(7f * v, cap = StrokeCap.Round))
        drawPath(
            Path().apply { moveTo(113.4f * v, 180f * v); lineTo(113.4f * v, 186f * v); cubicTo(113.4f * v, 197f * v, 97f * v, 198f * v, 95.5f * v, 191f * v) },
            Color(0xFFE2B184).copy(alpha = 0.55f), style = Stroke(1.4f * v, cap = StrokeCap.Round),
        )
        // Metallring zwischen Stock und Griff.
        drawRoundRect(Brush.horizontalGradient(listOf(Color(0xFF8A6A2C), Color(0xFFE6C877), Color(0xFF8A6A2C)), 111.5f * v, 118.5f * v), p(111.5f, 175f), Size(7f * v, 4.5f * v), CornerRadius(1.5f * v))

        // Spitze oben: Metallstift mit kleinem roten Knauf.
        drawLine(Color(0xFF7C8088), p(115f, 16f), p(115f, 5f), 2.4f * v, cap = StrokeCap.Round)
        drawCircle(Brush.radialGradient(listOf(Color(0xFFFF9A8C), Signal.Rot, Color(0xFFB0281A)), p(113.5f, 3.2f), 5f * v), 4.2f * v, p(115f, 4.5f))
    }
}

private fun lerp(a: Color, b: Color, t: Float) = Color(
    red = a.red + (b.red - a.red) * t,
    green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t,
    alpha = 1f,
)

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
