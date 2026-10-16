package com.pursuitiron.app.ui.common

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.sin

/**
 * The web app's rest-over beep (App.js beep): a sine at 880 Hz stepping to 660 Hz, about 0.3 s,
 * quiet. Skipped when the phone is on silent or vibrate, where the haptic carries it alone.
 */
class Beep(private val context: Context) {
    private val rate = 44_100
    private val pcm: ShortArray by lazy {
        val n = (rate * 0.32).toInt()
        ShortArray(n) { i ->
            val t = i.toDouble() / rate
            val f = if (t < 0.12) 880.0 else 660.0
            val env = when { t < 0.005 -> t / 0.005; t < 0.2 -> 1.0; else -> (1 - (t - 0.2) / 0.12).coerceAtLeast(0.0) }
            (sin(2 * PI * f * t) * env * 0.35 * Short.MAX_VALUE).toInt().toShort()
        }
    }

    fun play() {
        val am = context.getSystemService(AudioManager::class.java) ?: return
        if (am.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        runCatching {
            val track = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(rate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(pcm.size * 2)
                .build()
            track.write(pcm, 0, pcm.size)
            track.setNotificationMarkerPosition(pcm.size - 1)
            track.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
                override fun onMarkerReached(t: AudioTrack) { runCatching { t.release() } }
                override fun onPeriodicNotification(t: AudioTrack) {}
            })
            track.play()
        }
    }
}
