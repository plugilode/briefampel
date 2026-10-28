package de.briefampel.app.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import de.briefampel.app.R

/** Das Sound-Logo der Briefampel. Rücksichtsvoll: nicht bei stummem Handy und nicht über laufende Musik. */
internal object IntroTon {
    fun spielen(context: Context) {
        val audio = context.getSystemService(AudioManager::class.java) ?: return
        if (audio.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        if (audio.isMusicActive) return
        val attribute = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val spieler = try {
            MediaPlayer.create(context, R.raw.briefampel_chime, attribute, audio.generateAudioSessionId())
        } catch (_: Exception) { null } ?: return
        spieler.setVolume(0.7f, 0.7f)
        spieler.setOnCompletionListener { it.release() }
        spieler.setOnErrorListener { mp, _, _ -> mp.release(); true }
        spieler.start()
    }
}
