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
    /** The design's optional inline −/+ beside each set's weight and reps; off by default. */
    var steppers by mutableStateOf(sp.getBoolean("steppers", false))
        private set
    /** The design's focus mode in the workout: one set at a time. */
    var focus by mutableStateOf(sp.getBoolean("focus", false))
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
    /** The design's Target ⇄ Last reference column. */
    var lastMode by mutableStateOf(sp.getBoolean("lastMode", false))
        private set
    fun changeLastMode(v: Boolean) { lastMode = v; sp.edit().putBoolean("lastMode", v).apply() }
    /** The design's check-in on Today: "yyyy-MM-dd|sleep|sore|energy|done", one day at a time. */
    var checkIn by mutableStateOf(sp.getString("checkIn", "") ?: "")
        private set
    fun changeCheckIn(v: String) { checkIn = v; sp.edit().putString("checkIn", v).apply() }
    fun changeFocus(v: Boolean) { focus = v; sp.edit().putBoolean("focus", v).apply() }
    fun changeSteppers(v: Boolean) { steppers = v; sp.edit().putBoolean("steppers", v).apply() }
    /** Comfort → Reduce motion (the design's toggle): drives ui.theme.Motion.reduced. */
    var reduceMotion by mutableStateOf(sp.getBoolean("reduceMotion", false))
        private set
    fun changeReduceMotion(v: Boolean) { reduceMotion = v; sp.edit().putBoolean("reduceMotion", v).apply() }
    /** The design's rest cues: "off", "tones" (3-2-1 then a higher tone) or "voice"; and lowering music instead of pausing it. */
    var cues by mutableStateOf(sp.getString("cues", "tones") ?: "tones")
        private set
    fun changeCues(v: String) { cues = v; sp.edit().putString("cues", v).apply() }
    var duck by mutableStateOf(sp.getBoolean("duck", true))
        private set
    fun changeDuck(v: Boolean) { duck = v; sp.edit().putBoolean("duck", v).apply() }
    /** The design's Comfort & accessibility text size: 1, 1.1 or 1.2 on top of the phone's own font scale. */
    var zoom by mutableStateOf(sp.getFloat("zoom", 1f))
        private set
    fun changeZoom(v: Float) { zoom = v; sp.edit().putFloat("zoom", v).apply() }
    fun changeKeepAwake(v: Boolean) { keepAwake = v; sp.edit().putBoolean("keepAwake", v).apply() }
    fun dismissTransfer() { transferDismissed = true; sp.edit().putBoolean("transferDismissed", true).apply() }
    fun markIntro(v: Int) { seenIntro = v; sp.edit().putInt("seenIntro", v).apply() }
    fun markWhatsNew(v: Int) { seenWhatsNew = v; sp.edit().putInt("seenWhatsNew", v).apply() }
    /** The design's "Train as planned": the welcome-back offer stays dismissed for this break (keyed by the last session's date). */
    var reentrySkippedFor by mutableStateOf(sp.getLong("reentrySkippedFor", 0L))
        private set
    fun skipReentry(lastDate: Long) { reentrySkippedFor = lastDate; sp.edit().putLong("reentrySkippedFor", lastDate).apply() }
    /** The design's deload "Not yet" and fresher-day "Keep … today": hidden until another session is logged (keyed by the last session's id). */
    var deloadHiddenFor by mutableStateOf(sp.getString("deloadHiddenFor", "") ?: "")
        private set
    fun hideDeload(lastId: String) { deloadHiddenFor = lastId; sp.edit().putString("deloadHiddenFor", lastId).apply() }
    var altKeptFor by mutableStateOf(sp.getString("altKeptFor", "") ?: "")
        private set
    fun keepToday(lastId: String) { altKeptFor = lastId; sp.edit().putString("altKeptFor", lastId).apply() }
    /** The design's weekly recap: hidden until a week from when you closed it. */
    var recapHiddenUntil by mutableStateOf(sp.getLong("recapHiddenUntil", 0L))
    fun hideRecap(until: Long) { recapHiddenUntil = until; sp.edit().putLong("recapHiddenUntil", until).apply() }
    /** App.js ProfileCustomization ("wpb:profile-layout-v1"): profile section order and the ones hidden. */
    var profileLayout by mutableStateOf(ProfileLayout.parse(sp.getString("profileLayout", null)))
        private set
    fun changeProfileLayout(v: ProfileLayout) { profileLayout = v.normalized(); sp.edit().putString("profileLayout", profileLayout.json()).apply() }
    /** The backup nudge's X: quiet for a week, then it may come back if there is still no backup. */
    var backupNudgeHiddenAt by mutableStateOf(sp.getLong("backupNudgeHiddenAt", 0L))
        private set
    fun hideBackupNudge() { backupNudgeHiddenAt = System.currentTimeMillis(); sp.edit().putLong("backupNudgeHiddenAt", backupNudgeHiddenAt).apply() }
    fun changePins(v: List<String>) { pins = v; sp.edit().putString("pins", org.json.JSONArray(v).toString()).apply() }
    /** B894: unfinished cycle setups (the Create wizard, and turning a program into a cycle). Device-only, separate from training records;
        cleared by a successful build or a deliberate discard, kept when generation fails. */
    var setupDraft: String? = sp.getString("setupDraft", null)
        private set
    fun putSetupDraft(v: String?) { if (v == setupDraft) return; setupDraft = v; sp.edit().apply { if (v == null) remove("setupDraft") else putString("setupDraft", v) }.apply() }
    var convDraft: String? = sp.getString("convDraft", null)
        private set
    fun putConvDraft(v: String?) { if (v == convDraft) return; convDraft = v; sp.edit().apply { if (v == null) remove("convDraft") else putString("convDraft", v) }.apply() }
    fun markAskedNotifications() { askedNotifications = true; sp.edit().putBoolean("askedNotifications", true).apply() }
}

/** App.js normalizeProfilePrefs: every known section appears once, in order; at least one stays shown. */
data class ProfileLayout(val order: List<String>, val hidden: Set<String>,
                         /** B892 share card: what it shows, the name on it and up to three favorite lifts. */
                         val share: List<String> = listOf("strength", "streak"), val cardName: String = "", val favorites: List<String> = emptyList()) {
    fun normalized(): ProfileLayout {
        val o = (order.filter { it in IDS }.distinct() + IDS.filter { it !in order })
        var h = hidden.filter { it in IDS }.toSet()
        if (h.size >= IDS.size) h = h - o.first()
        val sh = share.filter { it in SHARE_IDS }.distinct().ifEmpty { listOf(SHARE_IDS.first()) }
        return ProfileLayout(o, h, sh, cardName.take(60), favorites.filter { it.isNotBlank() && it.length <= 100 }.distinct().take(3))
    }
    fun visible() = order.filter { it !in hidden }
    fun json(): String = org.json.JSONObject().put("order", org.json.JSONArray(order)).put("hidden", org.json.JSONArray(hidden.toList()))
        .put("share", org.json.JSONArray(share)).put("cardName", cardName).put("favorites", org.json.JSONArray(favorites)).toString()
    companion object {
        /** Native profile sections, in the default order. */
        val SECTIONS = listOf("big3" to "Big 3 total", "score" to "Strength Score", "bw" to "Bodyweight", "prs" to "PR board",
            "cal" to "Training calendar", "ach" to "Achievements", "bal" to "Muscle balance")
        val IDS = SECTIONS.map { it.first }
        val DEFAULT = ProfileLayout(IDS, emptySet())
        val SHARE_IDS = listOf("level", "strength", "club", "prs", "achievements", "streak", "favorites")
        fun label(id: String) = SECTIONS.firstOrNull { it.first == id }?.second ?: id
        fun parse(raw: String?): ProfileLayout = runCatching {
            val o = org.json.JSONObject(raw ?: return DEFAULT)
            val list = { k: String -> o.optJSONArray(k)?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList() }
            ProfileLayout(list("order"), list("hidden").toSet(), if (o.has("share")) list("share") else listOf("strength", "streak"),
                o.optString("cardName", ""), list("favorites")).normalized()
        }.getOrDefault(DEFAULT)
    }
}
