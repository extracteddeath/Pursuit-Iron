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
    /** App.js chart favourites: lift ids shown first on Progress, in this order. null = never chosen (the engine picks). */
    var pins by mutableStateOf(sp.getString("pins", null)?.let { raw ->
        runCatching { org.json.JSONArray(raw).let { a -> (0 until a.length()).map { a.getString(it) } } }.getOrNull()
    })
        private set

    fun changeSeed(v: Seed) { seed = v; sp.edit().putString("seed", v.name).apply() }
    fun changeBlack(v: Boolean) { black = v; sp.edit().putBoolean("black", v).apply() }
    fun changeDark(v: Boolean?) { dark = v; sp.edit().apply { if (v == null) remove("dark") else putBoolean("dark", v) }.apply() }
    fun changeGrip(v: Boolean) { grip = v; sp.edit().putBoolean("grip", v).apply() }
    fun changeKeepAwake(v: Boolean) { keepAwake = v; sp.edit().putBoolean("keepAwake", v).apply() }
    fun dismissTransfer() { transferDismissed = true; sp.edit().putBoolean("transferDismissed", true).apply() }
    fun markIntro(v: Int) { seenIntro = v; sp.edit().putInt("seenIntro", v).apply() }
    fun markWhatsNew(v: Int) { seenWhatsNew = v; sp.edit().putInt("seenWhatsNew", v).apply() }
    /** The design's "Train as planned": the welcome-back offer stays dismissed for this break (keyed by the last session's date). */
    var reentrySkippedFor by mutableStateOf(sp.getLong("reentrySkippedFor", 0L))
        private set
    fun skipReentry(lastDate: Long) { reentrySkippedFor = lastDate; sp.edit().putLong("reentrySkippedFor", lastDate).apply() }
    /** App.js ProfileCustomization ("wpb:profile-layout-v1"): profile section order and the ones hidden. */
    var profileLayout by mutableStateOf(ProfileLayout.parse(sp.getString("profileLayout", null)))
        private set
    fun changeProfileLayout(v: ProfileLayout) { profileLayout = v.normalized(); sp.edit().putString("profileLayout", profileLayout.json()).apply() }
    /** The backup nudge's X: quiet for a week, then it may come back if there is still no backup. */
    var backupNudgeHiddenAt by mutableStateOf(sp.getLong("backupNudgeHiddenAt", 0L))
        private set
    fun hideBackupNudge() { backupNudgeHiddenAt = System.currentTimeMillis(); sp.edit().putLong("backupNudgeHiddenAt", backupNudgeHiddenAt).apply() }
    fun changePins(v: List<String>) { pins = v; sp.edit().putString("pins", org.json.JSONArray(v).toString()).apply() }
    fun markAskedNotifications() { askedNotifications = true; sp.edit().putBoolean("askedNotifications", true).apply() }
}

/** App.js normalizeProfilePrefs: every known section appears once, in order; at least one stays shown. */
data class ProfileLayout(val order: List<String>, val hidden: Set<String>) {
    fun normalized(): ProfileLayout {
        val o = (order.filter { it in IDS }.distinct() + IDS.filter { it !in order })
        var h = hidden.filter { it in IDS }.toSet()
        if (h.size >= IDS.size) h = h - o.first()
        return ProfileLayout(o, h)
    }
    fun visible() = order.filter { it !in hidden }
    fun json(): String = org.json.JSONObject().put("order", org.json.JSONArray(order)).put("hidden", org.json.JSONArray(hidden.toList())).toString()
    companion object {
        /** Native profile sections, in the default order. */
        val SECTIONS = listOf("big3" to "Big 3 total", "level" to "Level and XP", "strength" to "Strength Score", "prs" to "PR board",
            "club" to "Thousand Pound Club", "achievements" to "Achievements", "balance" to "Muscle balance")
        val IDS = SECTIONS.map { it.first }
        val DEFAULT = ProfileLayout(IDS, emptySet())
        fun label(id: String) = SECTIONS.firstOrNull { it.first == id }?.second ?: id
        fun parse(raw: String?): ProfileLayout = runCatching {
            val o = org.json.JSONObject(raw ?: return DEFAULT)
            val list = { k: String -> o.optJSONArray(k)?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList() }
            ProfileLayout(list("order"), list("hidden").toSet()).normalized()
        }.getOrDefault(DEFAULT)
    }
}
