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

    /** Older builds stored Teal and Violet; Seed.from maps retired names onto the theme that replaced them. */
    var seed by mutableStateOf(Seed.from(sp.getString("seed", null)))
        private set
    /** Pure black surfaces in dark mode. */
    var black by mutableStateOf(sp.getBoolean("black", false))
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
    /** App.js seenIntroV / seenWhatsNew: the welcome screen and the release card, shown once per version. */
    var seenIntro by mutableStateOf(sp.getInt("seenIntro", 0))
        private set
    var seenWhatsNew by mutableStateOf(sp.getInt("seenWhatsNew", 0))
        private set

    fun changeSeed(v: Seed) { seed = v; sp.edit().putString("seed", v.name).apply() }
    fun changeBlack(v: Boolean) { black = v; sp.edit().putBoolean("black", v).apply() }
    fun changeDark(v: Boolean?) { dark = v; sp.edit().apply { if (v == null) remove("dark") else putBoolean("dark", v) }.apply() }
    fun changeGrip(v: Boolean) { grip = v; sp.edit().putBoolean("grip", v).apply() }
    fun changeKeepAwake(v: Boolean) { keepAwake = v; sp.edit().putBoolean("keepAwake", v).apply() }
    fun dismissTransfer() { transferDismissed = true; sp.edit().putBoolean("transferDismissed", true).apply() }
    fun markIntro(v: Int) { seenIntro = v; sp.edit().putInt("seenIntro", v).apply() }
    fun markWhatsNew(v: Int) { seenWhatsNew = v; sp.edit().putInt("seenWhatsNew", v).apply() }
    fun markAskedNotifications() { askedNotifications = true; sp.edit().putBoolean("askedNotifications", true).apply() }
}
