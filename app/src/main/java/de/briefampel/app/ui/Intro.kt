package de.briefampel.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import de.briefampel.app.ui.figur.AmpelFreundin
import de.briefampel.app.ui.figur.Briefumschlag
import de.briefampel.app.ui.figur.Regenschirm
import de.briefampel.app.ui.figur.Stimmung
import de.briefampel.app.ui.theme.LocalFarben
import de.briefampel.app.ui.theme.LocalWenigBewegung
import de.briefampel.app.ui.theme.Schrift
import kotlin.math.PI
import kotlin.math.sin

private const val DAUER = 6.2f

/**
 * Kurzes Intro beim ersten Start: Briefe regnen herab, die Briefampel schwebt mit dem Regenschirm herunter,
 * schirmt sie ab und nimmt den Mahnbrief unter ihren Hut. Bei „Animationen entfernen“ steht sofort das Schlussbild.
 */
@Composable
internal fun Intro(fertig: () -> Unit, ton: Boolean = false) {
    val c = LocalFarben.current
    val wenig = LocalWenigBewegung.current
    val zeit = remember { Animatable(if (wenig) 1f else 0f) }
    val kontext = LocalContext.current
    val vorschau = LocalInspectionMode.current
    LaunchedEffect(Unit) {
        if (ton && !vorschau) IntroTon.spielen(kontext)
        if (!wenig) zeit.animateTo(1f, tween((DAUER * 1000).toInt(), easing = LinearEasing))
    }
    val t = zeit.value * DAUER
    fun fenster(von: Float, bis: Float) = ((t - von) / (bis - von)).coerceIn(0f, 1f)
    fun sanft(x: Float) = 1f - (1f - x) * (1f - x) * (1f - x)

    BoxWithConstraints(
        Modifier.fillMaxSize().background(c.grund)
            .clickable(remember { MutableInteractionSource() }, indication = null) {},
    ) {
        val h = maxHeight
        val w = maxWidth
        val freundinOben = h * 0.38f

        // Briefregen: drei kleine Umschläge, die am Schirm abprallen.
        listOf(Triple(0.16f, 0.0f, -1f), Triple(0.66f, 0.25f, 1f), Triple(0.42f, 0.7f, 1f)).forEach { (x, start, richtung) ->
            val fallen = sanft(fenster(start, 2.7f))
            val weg = fenster(2.7f, 3.3f)
            Briefumschlag(
                Modifier.offset(x = w * x + (180.dp * weg * richtung), y = (-80).dp + (freundinOben - 110.dp + 80.dp) * fallen - 200.dp * weg)
                    .size(64.dp, 42.dp)
                    .graphicsLayer { rotationZ = (-18f + 26f * fallen) * richtung + 80f * weg * richtung; alpha = 1f - weg },
            )
        }

        // Die Freundin schwebt mit dem Schirm herab und landet sanft.
        val schweben = sanft(fenster(1.6f, 3.0f))
        val schwanken = if (schweben < 1f) sin(t * 2.6f * PI.toFloat()) * 5f * (1f - schweben) else 0f
        val schirm = 1f - fenster(4.0f, 4.4f)
        Box(
            Modifier.align(Alignment.TopCenter).offset(y = freundinOben - (h + 200.dp) * (1f - schweben))
                .graphicsLayer { rotationZ = schwanken },
        ) {
            // Sie hält den Schirm selbst am Stock; nach der Landung lässt sie ihn sinken.
            AmpelFreundin(
                Stimmung.WARTET, "Die Briefampel kommt mit dem Regenschirm in der Hand angeschwebt",
                lampe = 60.dp, hutLuepfen = if (t > 4.5f) 1 else 0, arme = true, schirm = schirm,
            )
        }

        // Der Mahnbrief landet vor ihr, zittert und verschwindet dann unter ihrem Hut.
        val landen = sanft(fenster(0.3f, 0.9f))
        val zittern = if (t in 0.9f..4.4f && !wenig) sin(t * 40f) * 2.5f else 0f
        val nehmen = sanft(fenster(4.5f, 5.1f))
        Box(
            Modifier.align(Alignment.TopCenter)
                .offset(y = (-200).dp + (freundinOben + 150.dp + 200.dp) * landen - (176.dp) * nehmen)
                .size(200.dp, 132.dp)
                .graphicsLayer {
                    rotationZ = -5f + zittern
                    scaleX = 1f - 0.86f * nehmen; scaleY = 1f - 0.86f * nehmen
                    alpha = 1f - fenster(5.0f, 5.2f)
                },
        ) {
            Briefumschlag(Modifier.fillMaxSize(), siegel = true)
            Text(
                "LETZTE MAHNUNG", style = Schrift.Marke, color = Color(0xFFC8392A),
                modifier = Modifier.align(Alignment.BottomStart).padding(14.dp).graphicsLayer { rotationZ = -4f },
            )
        }

        Column(Modifier.statusBarsPadding().padding(horizontal = 28.dp, vertical = 20.dp)) {
            Box {
                Unterzeile("Manche Post\nmacht Angst.", fenster(0.2f, 0.6f) * (1f - fenster(1.9f, 2.2f)))
                Unterzeile("Keine Sorge.\nIch bin da.", fenster(2.8f, 3.1f) * (1f - fenster(4.1f, 4.4f)))
                Unterzeile("Gib her.\nIch lese zuerst.", fenster(4.4f, 4.8f))
            }
        }

        Text(
            "Überspringen", style = Schrift.KnopfKlein, color = c.tinteLeise,
            modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(8.dp).clip(CircleShape)
                .clickable(onClick = fertig).padding(horizontal = 14.dp, vertical = 12.dp).alpha(1f - fenster(5.2f, 5.6f)),
        )
        Box(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(20.dp).alpha(fenster(5.2f, 5.8f)),
        ) {
            if (t > 5.2f) Pille("Los geht’s", fertig)
        }
    }
}

/**
 * Kurzes Marken-Intro bei jedem weiteren Start (2 Sekunden): Die Briefampel gleitet am Schirm herein, schaltet einmal
 * Rot–Gelb–Grün durch, lüpft den Hut, darunter der Schriftzug. Bei „Animationen entfernen“ entfällt es.
 */
@Composable
internal fun KurzIntro(fertig: () -> Unit, ton: Boolean = false) {
    val c = LocalFarben.current
    val wenig = LocalWenigBewegung.current
    val zeit = remember { Animatable(0f) }
    val kontext = LocalContext.current
    val vorschau = LocalInspectionMode.current
    LaunchedEffect(Unit) {
        if (wenig) { fertig(); return@LaunchedEffect }
        // Das Sound-Logo startet mit dem Hereingleiten; es klingt nach dem Intro noch kurz aus.
        if (ton && !vorschau) IntroTon.spielen(kontext)
        zeit.animateTo(1f, tween(2000, easing = LinearEasing))
        fertig()
    }
    val t = zeit.value * 2.0f
    fun fenster(von: Float, bis: Float) = ((t - von) / (bis - von)).coerceIn(0f, 1f)
    // Sie gleitet am Schirm von oben herein und bremst sanft ab, wie im großen Intro.
    val gleiten = 1f - (1f - fenster(0f, 0.8f)).let { it * it * it }
    val schwanken = sin(t * 9f) * 6f * (1f - gleiten)
    val schirm = 1f - fenster(1.15f, 1.45f)
    val stimmung = when {
        t < 0.75f -> Stimmung.WARTET
        t < 1.15f -> Stimmung.LIEST
        else -> Stimmung.GRUEN
    }
    BoxWithConstraints(
        Modifier.fillMaxSize().background(c.grund)
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = fertig)
            .graphicsLayer { alpha = 1f - fenster(1.75f, 2.0f) },
        contentAlignment = Alignment.Center,
    ) {
        val start = maxHeight.value * 0.9f
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.graphicsLayer {
                alpha = fenster(0f, 0.15f)
                translationY = -(1f - gleiten) * start * density
                rotationZ = schwanken
            }) {
                AmpelFreundin(stimmung, "Briefampel", lampe = 46.dp, hutLuepfen = if (t > 1.2f) 1 else 0, arme = true, schirm = schirm)
            }
            Spacer(Modifier.height(26.dp))
            Text(
                "Briefampel", style = Schrift.Anzeige.copy(fontSize = 34.sp, lineHeight = 34.sp), color = c.tinte,
                modifier = Modifier.graphicsLayer { alpha = fenster(0.85f, 1.2f); translationY = (1f - fenster(0.85f, 1.2f)) * 16f },
            )
            Text(
                "Liest zuerst. Für dich.", style = Schrift.TextKlein, color = c.tinteLeise,
                modifier = Modifier.padding(top = 8.dp).graphicsLayer { alpha = fenster(1.05f, 1.4f) },
            )
        }
    }
}

@Composable
private fun Unterzeile(text: String, sichtbar: Float) {
    if (sichtbar <= 0f) return
    Text(
        text, style = Schrift.Anzeige.copy(fontSize = 36.sp, lineHeight = 37.sp), color = LocalFarben.current.tinte,
        modifier = Modifier.graphicsLayer { alpha = sichtbar; translationY = (1f - sichtbar) * 24f },
    )
}
