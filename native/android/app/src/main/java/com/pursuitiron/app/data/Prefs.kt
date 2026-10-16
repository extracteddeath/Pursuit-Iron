package com.pursuitiron.app.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.pursuitiron.app.ui.theme.Seed

/**
 * Native-only preferences. These describe this phone, not the training record, so they stay out
 * of the `wpb:v1` store the PWA and the engine share.
 */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("pi-native", Context.MODE_PRIVATE)

    var seed by mutableStateOf(runCatching { Seed.valueOf(sp.getString("seed", null) ?: "Ember") }.getOrDefault(Seed.Ember))
        private set
    /** null follows the system. */
    var dark by mutableStateOf(if (sp.contains("dark")) sp.getBoolean("dark", false) else null)
        private set
    var grip by mutableStateOf(sp.getBoolean("grip", false))
        private set
    var keepAwake by mutableStateOf(sp.getBoolean("keepAwake", true))
        private set
    var transferDismissed by mutableStateOf(sp.getBoolean("transferDismissed", false))
        private set
    var askedNotifications by mutableStateOf(sp.getBoolean("askedNotifications", false))
        private set

    fun changeSeed(v: Seed) { seed = v; sp.edit().putString("seed", v.name).apply() }
    fun changeDark(v: Boolean?) { dark = v; sp.edit().apply { if (v == null) remove("dark") else putBoolean("dark", v) }.apply() }
    fun changeGrip(v: Boolean) { grip = v; sp.edit().putBoolean("grip", v).apply() }
    fun changeKeepAwake(v: Boolean) { keepAwake = v; sp.edit().putBoolean("keepAwake", v).apply() }
    fun dismissTransfer() { transferDismissed = true; sp.edit().putBoolean("transferDismissed", true).apply() }
    fun markAskedNotifications() { askedNotifications = true; sp.edit().putBoolean("askedNotifications", true).apply() }
}
