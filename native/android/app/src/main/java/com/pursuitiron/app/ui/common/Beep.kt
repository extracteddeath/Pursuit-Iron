package com.pursuitiron.app.ui.common

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import java.util.Locale
import kotlin.math.PI
import kotlin.math.sin

/**
 * The design's rest cues (Settings → Rest timer & cues). Tones: three short beeps from 3 seconds,
 * then a higher one at zero. Voice: says "Rest over" and the next exercise. Off: the haptic alone.
 * Music lowers for the cue and comes straight back, or pauses if "lower music" is off.
 * Skipped when the phone is on silent or vibrate, where the haptic carries it alone.
 */
class Beep(private val context: Context) {
    private val rate = 44_100
    private val am get() = context.getSystemService(AudioManager::class.java)
    private val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
    private val main = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var focus: AudioFocusRequest? = null

    private fun tone(freq: Double, sec: Double): ShortArray {
        val n = (rate * sec).toInt()
        return ShortArray(n) { i ->
            val t = i.toDouble() / rate
            val env = when { t < 0.005 -> t / 0.005; t < sec - 0.04 -> 1.0; else -> ((sec - t) / 0.04).coerceAtLeast(0.0) }
            (sin(2 * PI * freq * t) * env * 0.35 * Short.MAX_VALUE).toInt().toShort()
        }
    }
    private val short by lazy { tone(880.0, 0.09) }
    private val high by lazy { tone(1320.0, 0.28) }

    private fun audible() = am?.ringerMode == AudioManager.RINGER_MODE_NORMAL

    private fun take(duck: Boolean, holdMs: Long) {
        val a = am ?: return
        if (Build.VERSION.SDK_INT >= 26) {
            val r = AudioFocusRequest.Builder(if (duck) AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK else AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(attrs).build()
            focus?.let { runCatching { a.abandonAudioFocusRequest(it) } }
            focus = r
            runCatching { a.requestAudioFocus(r) }
            main.postDelayed({ focus?.takeIf { it === r }?.let { runCatching { a.abandonAudioFocusRequest(it) }; focus = null } }, holdMs)
        }
    }

    private fun play(pcm: ShortArray) {
        runCatching {
            val track = AudioTrack.Builder().setAudioAttributes(attrs)
                .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setTransferMode(AudioTrack.MODE_STATIC).setBufferSizeInBytes(pcm.size * 2).build()
            track.write(pcm, 0, pcm.size)
            track.setNotificationMarkerPosition(pcm.size - 1)
            track.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
                override fun onMarkerReached(t: AudioTrack) { runCatching { t.release() } }
                override fun onPeriodicNotification(t: AudioTrack) {}
            })
            track.play()
        }
    }

    /** 3, 2, 1: a short beep each second (tones only). */
    fun countdown(mode: String, duck: Boolean) {
        if (mode != "tones" || !audible()) return
        take(duck, 1200); play(short)
    }

    /** Rest is over: the higher tone, or the voice with the next exercise. */
    fun end(mode: String, duck: Boolean, next: String? = null) {
        if (mode == "off" || !audible()) return
        if (mode == "voice") {
            take(duck, 3500)
            val text = "Rest over." + (next?.let { " Next, $it." } ?: "")
            val t = tts
            if (t != null && ttsReady) t.speak(text, TextToSpeech.QUEUE_FLUSH, null, "rest")
            else tts = TextToSpeech(context) { st ->
                ttsReady = st == TextToSpeech.SUCCESS
                if (ttsReady) { tts?.language = Locale.getDefault(); tts?.setAudioAttributes(attrs); tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "rest") }
            }
        } else { take(duck, 800); play(high) }
    }

    /** The old single beep, kept for callers that only want the end tone. */
    fun play() = end("tones", true)

    fun close() { runCatching { tts?.shutdown() }; tts = null }
}
