package com.pursuitiron.app.ui.common

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** The design system's haptic vocabulary: each event has its own pattern so it can be felt, not looked at. */
enum class Hap(val pattern: LongArray) {
    Log(longArrayOf(18)), Pr(longArrayOf(30, 40, 30, 40, 90)), Rest(longArrayOf(40, 60, 40)),
    Warn(longArrayOf(90, 60, 90)), Undo(longArrayOf(12, 40, 12)), Tick(longArrayOf(10)), Done(longArrayOf(20, 30, 60))
}

class Haptics(context: Context) {
    private val vib: Vibrator? = if (Build.VERSION.SDK_INT >= 31)
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    else @Suppress("DEPRECATION") (context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)

    var enabled = true
    var strong = false

    fun play(h: Hap) {
        val v = vib ?: return
        if (!enabled || !v.hasVibrator()) return
        // Pattern alternates on/off starting with "on"; the waveform API starts with a delay.
        val timings = LongArray(h.pattern.size + 1)
        h.pattern.forEachIndexed { i, ms -> timings[i + 1] = if (strong && i % 2 == 0) (ms * 1.6).toLong() else ms }
        runCatching { v.vibrate(VibrationEffect.createWaveform(timings, -1)) }
    }
}
