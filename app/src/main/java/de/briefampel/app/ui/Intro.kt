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
internal fun Intro(fertig: () -> Unit) {
    val c = LocalFarben.current
    val wenig = LocalWenigBewegung.current
    val zeit = remember { Animatable(if (wenig) 1f else 0f) }
    LaunchedEffect(Unit) { if (!wenig) zeit.animateTo(1f, tween((DAUER * 1000).toInt(), easing = LinearEasing)) }
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
            Regenschirm(Modifier.align(Alignment.TopCenter).offset(y = (-150).dp - 20.dp * (1f - schirm)).size(184.dp, 160.dp).alpha(schirm))
            AmpelFreundin(Stimmung.WARTET, "Die Briefampel kommt mit dem Regenschirm angeschwebt", lampe = 60.dp, hutLuepfen = if (t > 4.5f) 1 else 0)
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

@Composable
private fun Unterzeile(text: String, sichtbar: Float) {
    if (sichtbar <= 0f) return
    Text(
        text, style = Schrift.Anzeige.copy(fontSize = 36.sp, lineHeight = 37.sp), color = LocalFarben.current.tinte,
        modifier = Modifier.graphicsLayer { alpha = sichtbar; translationY = (1f - sichtbar) * 24f },
    )
}
