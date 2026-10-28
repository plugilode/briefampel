package de.briefampel.app.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode

val LocalFarben = staticCompositionLocalOf { DunkleFarben }

/** true, wenn der Nutzer in den Systemeinstellungen Animationen abgeschaltet hat. */
val LocalWenigBewegung = staticCompositionLocalOf { false }

object Bewegung {
    /** Ruhiges Ankommen für alles, was das System tut. */
    val Ruhig = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
}

@Composable
fun BriefampelTheme(dunkel: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val farben = if (dunkel) DunkleFarben else HelleFarben
    val context = LocalContext.current
    val inspection = LocalInspectionMode.current
    val wenigBewegung = remember(context, inspection) {
        inspection || Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    val schema = if (dunkel) {
        darkColorScheme(background = farben.grund, surface = farben.flaeche, onBackground = farben.tinte, onSurface = farben.tinte,
            primary = farben.knopf, onPrimary = farben.aufKnopf, secondary = farben.tinteLeise,
            secondaryContainer = farben.knopf, onSecondaryContainer = farben.aufKnopf, outline = farben.linie, onSurfaceVariant = farben.tinteLeise)
    } else {
        lightColorScheme(background = farben.grund, surface = farben.flaeche, onBackground = farben.tinte, onSurface = farben.tinte,
            primary = farben.knopf, onPrimary = farben.aufKnopf, secondary = farben.tinteLeise,
            secondaryContainer = farben.knopf, onSecondaryContainer = farben.aufKnopf, outline = farben.linie, onSurfaceVariant = farben.tinteLeise)
    }
    CompositionLocalProvider(LocalFarben provides farben, LocalWenigBewegung provides wenigBewegung) {
        MaterialTheme(colorScheme = schema, content = content)
    }
}
