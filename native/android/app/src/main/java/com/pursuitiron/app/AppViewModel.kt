package com.pursuitiron.app

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pursuitiron.app.data.ImportOffer
import com.pursuitiron.app.data.Prefs
import com.pursuitiron.app.data.RestNotifier
import com.pursuitiron.app.engine.Core
import com.pursuitiron.app.ui.common.Hap
import com.pursuitiron.app.ui.common.Haptics
import com.pursuitiron.app.ui.common.b
import com.pursuitiron.app.ui.common.i
import com.pursuitiron.app.ui.common.jsonOf
import com.pursuitiron.app.ui.common.list
import com.pursuitiron.app.ui.common.o
import com.pursuitiron.app.ui.common.s
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

enum class Tab { Today, Plan, Progress, Library, You }
enum class Screen { Tabs, Workout, Summary, Setup }

data class Snack(val text: String, val undo: (() -> Unit)? = null, val id: Long = System.nanoTime())

/**
 * The only place the screens talk to the engine. Every user action becomes one call into the
 * JavaScript core; the returned view model replaces the screen's state wholesale, so the screen
 * never computes training numbers of its own.
 */
class AppViewModel(app: Application) : AndroidViewModel(app) {
    val prefs = Prefs(app)
    val haptics = Haptics(app)
    val rest = RestNotifier(app)
    private var core: Core? = null

    var ready by mutableStateOf(false); private set
    var startupError by mutableStateOf<String?>(null); private set
    var runtime by mutableStateOf(""); private set

    var tab by mutableStateOf(Tab.Today)
    var screen by mutableStateOf(Screen.Tabs); private set

    var home by mutableStateOf<JSONObject?>(null); private set
    var session by mutableStateOf<JSONObject?>(null); private set
    var summary by mutableStateOf<JSONObject?>(null); private set
    var plan by mutableStateOf<JSONObject?>(null); private set
    var progress by mutableStateOf<JSONObject?>(null); private set
    var library by mutableStateOf<List<JSONObject>>(emptyList()); private set
    var settings by mutableStateOf<JSONObject?>(null); private set
    var body by mutableStateOf<JSONObject?>(null); private set
    var history by mutableStateOf<List<JSONObject>>(emptyList()); private set
    var setup by mutableStateOf<JSONObject?>(null); private set

    var snack by mutableStateOf<Snack?>(null); private set
    var importOffer by mutableStateOf<ImportOffer?>(null)
    var now by mutableLongStateOf(System.currentTimeMillis()); private set

    private var libQuery = ""
    private var libPart: String? = null
    private var inForeground = true

    init {
        viewModelScope.launch {
            try {
                val c = Core.start(app)
                core = c
                runtime = c.runtime
                refreshAll()
                if (session?.b("active") == true) screen = Screen.Workout
                ready = true
            } catch (e: Throwable) {
                startupError = e.message ?: e.toString()
            }
        }
        viewModelScope.launch {
            while (true) {
                delay(1000)
                now = System.currentTimeMillis()
                checkRest()
            }
        }
    }

    /* ---------------------------------------------------------------- plumbing */
    private suspend fun call(method: String, args: JSONObject = JSONObject()): Any? {
        val c = core ?: return null
        return try { c.call(method, args) } catch (e: Exception) { toast(e.message ?: "Something went wrong"); null }
    }
    private suspend fun obj(method: String, args: JSONObject = JSONObject()) = call(method, args) as? JSONObject
    private fun arr(v: Any?): List<JSONObject> = (v as? JSONArray)?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it) } } ?: emptyList()

    private fun launchCore(block: suspend () -> Unit) { viewModelScope.launch { block() } }

    fun toast(text: String, undo: (() -> Unit)? = null) { snack = Snack(text, undo) }
    fun clearSnack() { snack = null }

    private suspend fun refreshAll() {
        settings = obj("settings")
        haptics.enabled = settings?.b("haptics", true) != false
        haptics.strong = prefs.grip
        home = obj("home")
        session = obj("session")
        plan = obj("plan")
        progress = obj("progress")
        body = obj("body")
        history = arr(call("historyList"))
        library = arr(call("library", jsonOf("q" to libQuery, "part" to libPart)))
        setup = obj("setupOptions")
    }
    private suspend fun refreshHomeSide() {
        home = obj("home"); plan = obj("plan"); progress = obj("progress"); history = arr(call("historyList")); settings = obj("settings")
    }

    fun hap(h: Hap) { haptics.strong = prefs.grip; haptics.play(h) }

    /* ---------------------------------------------------------------- navigation */
    fun goTab(t: Tab) { tab = t; screen = Screen.Tabs }
    fun openSetup() { screen = Screen.Setup }
    fun closeSetup() { screen = Screen.Tabs }
    fun minimise() { screen = Screen.Tabs; launchCore { home = obj("home") } }
    fun resume() { if (session?.b("active") == true) screen = Screen.Workout }

    /* ---------------------------------------------------------------- workout */
    fun start(programId: String? = null, dayId: String? = null, weekIndex: Int? = null) = launchCore {
        val args = JSONObject()
        if (programId != null) args.put("programId", programId)
        if (dayId != null) args.put("dayId", dayId)
        if (weekIndex != null) args.put("weekIndex", weekIndex)
        val s = obj("startSession", args)
        if (s == null || s.has("error") && !s.b("active")) { toast(s?.s("error") ?: "Couldn't start the workout"); return@launchCore }
        session = s
        screen = Screen.Workout
    }

    fun act(type: String, vararg args: Pair<String, Any?>) = launchCore {
        val before = session
        val payload = jsonOf("type" to type, *args)
        val s = obj("act", payload) ?: return@launchCore
        session = s
        if (type == "toggle") onToggled(before, s, payload)
        if (type == "injury") { hap(Hap.Warn); home = obj("home") }
    }

    private fun onToggled(before: JSONObject?, after: JSONObject, payload: JSONObject) {
        val ei = payload.optInt("ei"); val si = payload.optInt("si")
        val set = after.list("exercises").getOrNull(ei)?.list("sets")?.getOrNull(si) ?: return
        if (set.b("done")) {
            hap(if (set.b("pr")) Hap.Pr else Hap.Log)
            if (set.b("pr")) toast("New best for ${after.list("exercises")[ei].s("name")}")
        } else hap(Hap.Undo)
        if (before?.o("rest") == null && after.o("rest") != null) restFired = false
    }

    private var restFired = false
    private fun checkRest() {
        val r = session?.o("rest") ?: return
        val end = r.optLong("startedAt") + r.optLong("seconds") * 1000
        if (!restFired && now >= end) {
            restFired = true
            hap(Hap.Rest)
            act("restDone")
            if (inForeground) toast("Rest over. Next set when you're ready.")
        }
    }

    fun restEndsAt(): Long? = session?.o("rest")?.let { it.optLong("startedAt") + it.optLong("seconds") * 1000 }

    fun discard() = launchCore {
        call("discardSession"); session = obj("session"); screen = Screen.Tabs; rest.cancel()
        refreshHomeSide(); toast("Workout discarded")
    }

    fun finish() = launchCore {
        val r = obj("finishSession") ?: return@launchCore
        if (!r.b("ok")) { toast(r.s("error", "Couldn't save the workout")); return@launchCore }
        rest.cancel()
        hap(Hap.Done)
        summary = r
        session = obj("session")
        refreshHomeSide()
        screen = Screen.Summary
    }

    fun closeSummary() { summary = null; screen = Screen.Tabs; tab = Tab.Today }

    suspend fun swapOptions(ei: Int): List<JSONObject> = arr(call("swapOptions", jsonOf("ei" to ei)))
    suspend fun explain(ei: Int): JSONObject? = obj("explain", jsonOf("ei" to ei))

    /* ---------------------------------------------------------------- plan / programs */
    fun loadPlan(programId: String?, week: Int?) = launchCore {
        val a = JSONObject(); if (programId != null) a.put("programId", programId); if (week != null) a.put("weekIndex", week)
        plan = obj("plan", a)
    }
    fun pin(programId: String) = launchCore { call("pinProgram", jsonOf("id" to programId)); refreshHomeSide(); toast("Now training: ${plan?.s("name").orEmpty()}") }

    suspend fun recommend(config: JSONObject): String? = call("recommend", jsonOf("config" to config)) as? String

    fun createProgram(config: JSONObject) = launchCore {
        val r = obj("createProgram", jsonOf("config" to config)) ?: return@launchCore
        if (!r.b("ok")) { toast(r.s("error", "The engine couldn't build that program")); return@launchCore }
        hap(Hap.Done)
        refreshHomeSide()
        screen = Screen.Tabs; tab = Tab.Today
        toast("${r.s("name")} is ready: ${r.i("days")} days a week")
    }

    /* ---------------------------------------------------------------- library */
    fun searchLibrary(q: String, part: String?) { libQuery = q; libPart = part; launchCore { library = arr(call("library", jsonOf("q" to q, "part" to part))) } }
    suspend fun exercise(id: String): JSONObject? = obj("exercise", jsonOf("id" to id))
    fun setBan(id: String, banned: Boolean) = launchCore { call("setBan", jsonOf("id" to id, "banned" to banned)); library = arr(call("library", jsonOf("q" to libQuery, "part" to libPart))); toast(if (banned) "Banned. It won't be picked again." else "Unbanned") }
    fun setExNote(id: String, text: String) = launchCore { call("setExNote", jsonOf("id" to id, "text" to text)) }

    /* ---------------------------------------------------------------- history */
    suspend fun historyDetail(id: String): JSONObject? = obj("historyDetail", jsonOf("id" to id))
    fun deleteHistory(id: String) = launchCore {
        val r = obj("deleteHistory", jsonOf("id" to id)) ?: return@launchCore
        if (!r.b("ok")) return@launchCore
        hap(Hap.Undo)
        refreshHomeSide()
        toast("Deleted ${r.s("label")}. Progression recalculated.") { launchCore { call("undo"); refreshHomeSide(); toast("Restored") } }
    }

    /* ---------------------------------------------------------------- body + settings */
    fun logBodyweight(v: Double) = launchCore { call("logBodyweight", jsonOf("value" to v)); body = obj("body"); hap(Hap.Log); toast("Bodyweight logged") }
    fun logMeasurement(key: String, v: Double) = launchCore { call("logMeasurement", jsonOf("key" to key, "value" to v)); body = obj("body") }
    fun updateSetting(key: String, value: Any?) = launchCore {
        val r = obj("updateSettings", jsonOf(key to value)) ?: return@launchCore
        if (!r.b("ok")) toast(r.s("error", "Couldn't change that"))
        settings = obj("settings"); haptics.enabled = settings?.b("haptics", true) != false
        if (key == "unit") refreshAll()
    }

    /* ---------------------------------------------------------------- data in and out */
    suspend fun exportText(): String? = core?.exportStoreText()
    fun markBackup() = launchCore { call("markBackup"); settings = obj("settings") }

    fun acceptImport() = launchCore {
        val offer = importOffer ?: return@launchCore
        importOffer = null
        val r = obj("importStore", jsonOf("text" to offer.store, "mode" to "merge")) ?: return@launchCore
        if (!r.b("ok")) { toast(r.s("error", "That backup couldn't be read")); return@launchCore }
        if (offer.live != null) call("adoptLive", jsonOf("live" to offer.live))
        prefs.dismissTransfer()
        refreshAll()
        hap(Hap.Done)
        val added = r.o("added")
        toast("Brought over ${added.i("sessions")} workouts and ${added.i("programs")} programs")
        if (session?.b("active") == true) screen = Screen.Workout
    }

    /* ---------------------------------------------------------------- lifecycle */
    fun onForeground() { inForeground = true; rest.cancel() }
    fun onBackground() {
        inForeground = false
        val end = restEndsAt() ?: return
        if (end <= System.currentTimeMillis()) return
        val s = session ?: return
        val ex = s.list("exercises").getOrNull(s.i("exIdx"))
        val next = ex?.list("sets")?.firstOrNull { !it.b("done") }
        val text = if (ex != null && next != null) "Next: ${ex.s("name")} · ${next.s("weight").ifBlank { "?" }} ${s.s("unit")} × ${next.s("reps")}" else "Next set when it ends"
        rest.show(end, "Rest", text)
    }

    override fun onCleared() { core?.close(); super.onCleared() }
}
