package com.pursuitiron.app.data

import org.json.JSONObject

/**
 * The workout as it shows outside the app: the rest countdown, or the session clock with the next set and a
 * Log set button. Shared by the app (going to the background) and by [RestActionReceiver] when the app's
 * process is gone and a lock-screen tap has to log the set on its own.
 */
object LiveCard {
    data class Next(val ei: Int, val si: Int, val name: String, val weight: String, val reps: String, val loggable: Boolean)

    // Lifts that log reps with no load typed (the in-app check does the same).
    private val BODYWEIGHT = setOf("pullup", "dip", "bodyweight")

    private fun JSONObject.items(key: String): List<JSONObject> = optJSONArray(key)?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it) } } ?: emptyList()

    /** The set the lifter does next: the current lift first, then the lifts after it, then any left earlier. */
    fun next(s: JSONObject?): Next? {
        val ex = s?.items("exercises") ?: return null
        if (ex.isEmpty()) return null
        val start = s.optInt("exIdx").coerceIn(0, ex.size - 1)
        for (k in ex.indices) {
            val ei = (start + k) % ex.size
            val e = ex[ei]
            val sets = e.items("sets")
            val si = sets.indexOfFirst { !it.optBoolean("done") && !it.optBoolean("skipped") }
            if (si < 0) continue
            val set = sets[si]
            val w = set.optString("weight"); val r = set.optString("reps")
            val equip = e.optJSONArray("equip")?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList()
            val loadOk = (w.toDoubleOrNull() ?: 0.0) > 0 || equip.isEmpty() || equip.all { it in BODYWEIGHT }
            return Next(ei, si, e.optString("name"), w, r, loadOk && (r.toIntOrNull() ?: 0) > 0)
        }
        return null
    }

    fun nextText(s: JSONObject, n: Next?): String = if (n == null) "Tap to finish your workout" else
        "Next: ${n.name} · " + (if ((n.weight.toDoubleOrNull() ?: 0.0) > 0) "${n.weight} ${s.optString("unit")}" else if (n.loggable) "bodyweight" else "?") + " × ${n.reps}"

    /** A paused rest holds its seconds left, so its end keeps moving with the clock. */
    fun restEndsAt(s: JSONObject?, now: Long): Long? = s?.optJSONObject("rest")?.let {
        if (it.optBoolean("paused")) now + it.optLong("left") * 1000 else it.optLong("startedAt") + it.optLong("seconds") * 1000
    }

    fun post(rest: RestNotifier, s: JSONObject?, now: Long) {
        if (s == null || !s.optBoolean("active")) { rest.cancel(); return }
        val n = next(s)
        val text = nextText(s, n)
        val end = restEndsAt(s, now)
        val paused = s.optJSONObject("rest")?.optBoolean("paused") == true
        if (end == null || paused || end <= now) {
            val sessionPaused = s.optLong("pausedAt") > 0
            rest.showWorkout(if (sessionPaused) null else s.optLong("startedAt"),
                s.optString("dayLabel").ifBlank { "Workout" } + if (sessionPaused) " · paused" else "",
                text, canLog = n?.loggable == true && !sessionPaused)
            return
        }
        val kind = s.optJSONObject("rest")?.optString("kind")
        rest.show(end, when (kind) { "mini" -> "Myo rest"; "warm" -> "Warm-up rest"; else -> "Rest" }, text, canLog = n?.loggable == true)
    }
}
