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
import com.pursuitiron.app.ui.common.plural
import com.pursuitiron.app.ui.common.s
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

enum class Tab { Today, Plan, Progress, Library, You }
enum class Screen { Tabs, Workout, Summary, Setup, Editor }

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
    private val beep = com.pursuitiron.app.ui.common.Beep(app)
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
    var profile by mutableStateOf<JSONObject?>(null); private set
    var gyms by mutableStateOf<JSONObject?>(null); private set
    var cycles by mutableStateOf<JSONObject?>(null); private set
    var templates by mutableStateOf<JSONObject?>(null); private set
    /** The program whose training maxes the sheet is open for, or null. */
    var tmFor by mutableStateOf<String?>(null)
    /** A long engine job in flight (building a cycle, advancing a block): the screen shows this and blocks input. */
    var busy by mutableStateOf<String?>(null); private set

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
                publishWidget()
                com.pursuitiron.app.data.Reminders.save(app, settings?.o("reminders"))
                if (pendingWidgetStart) { pendingWidgetStart = false; fromWidget() }
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

    /* ---------------------------------------------------------------- home-screen widget */
    private fun publishWidget() {
        val ctx = getApplication<Application>()
        val s = session; val h = home
        val snap = when {
            s?.b("active") == true -> com.pursuitiron.app.widget.WidgetSnapshot("live", s.s("dayLabel"), s.s("programName"), s.i("doneSets"), s.i("totalSets"),
                s.o("rest")?.let { it.optLong("startedAt") + it.optLong("seconds") * 1000 } ?: 0L)
            h?.o("next") != null -> h.o("next")!!.let { n -> com.pursuitiron.app.widget.WidgetSnapshot("next", n.s("dayLabel"),
                listOfNotNull(n.s("programName").ifBlank { null }, "${n.list("exercises").size} exercises", n.i("minutes").takeIf { it > 0 }?.let { "about $it min" }).joinToString(" · ")) }
            else -> com.pursuitiron.app.widget.WidgetSnapshot()
        }
        viewModelScope.launch { com.pursuitiron.app.widget.WidgetSnapshot.publish(ctx, snap) }
    }
    private var pendingWidgetStart = false
    /** The widget's Start/Resume button: resume a workout in progress, otherwise start the next one. */
    fun fromWidget() {
        if (!ready) { pendingWidgetStart = true; return }
        if (session?.b("active") == true) resume() else start()
    }

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
        profile = obj("profile")
        gyms = obj("gyms")
        cycles = obj("cyclesView")
        if (templates == null) templates = obj("templates")
    }
    private suspend fun refreshHomeSide() {
        home = obj("home"); plan = obj("plan"); progress = obj("progress"); history = arr(call("historyList")); settings = obj("settings")
        profile = obj("profile"); body = obj("body"); cycles = obj("cyclesView")
        publishWidget()
    }

    private suspend fun <T> working(label: String, block: suspend () -> T): T {
        busy = label
        try { return block() } finally { busy = null }
    }

    fun hap(h: Hap) { haptics.strong = prefs.grip; haptics.play(h) }

    /* ---------------------------------------------------------------- navigation */
    fun goTab(t: Tab) { tab = t; screen = Screen.Tabs }
    /** Opens program setup; with [cycle], in training-cycle mode. */
    var setupCycle by mutableStateOf(false); private set
    fun openSetup(cycle: Boolean = false) { setupCycle = cycle; screen = Screen.Setup }
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
        publishWidget()
    }

    fun act(type: String, vararg args: Pair<String, Any?>) = launchCore {
        val before = session
        val payload = jsonOf("type" to type, *args)
        val s = obj("act", payload) ?: return@launchCore
        session = s
        if (type == "toggle") { onToggled(before, s, payload); publishWidget() }
        if (type == "restDone" || type == "restAdjust") publishWidget()
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
            if (inForeground) beep.play()
            act("restDone")
            if (inForeground) toast("Rest over. Next set when you're ready.")
        }
    }

    fun restEndsAt(): Long? = session?.o("rest")?.let { it.optLong("startedAt") + it.optLong("seconds") * 1000 }

    fun discard() = launchCore {
        call("discardSession"); session = obj("session"); screen = Screen.Tabs; rest.cancel(); publishWidget()
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
        val r = working("Building your program") { obj("createProgram", jsonOf("config" to config)) } ?: return@launchCore
        if (!r.b("ok")) { toast(r.s("error", "The engine couldn't build that program")); return@launchCore }
        hap(Hap.Done)
        refreshHomeSide()
        screen = Screen.Tabs; tab = Tab.Today
        toast("${r.s("name")} is ready: ${r.i("days")} days a week")
    }

    /* ---------------------------------------------------------------- templates + training maxes */
    fun createFromTemplate(id: String) = launchCore {
        val r = working("Building your program") { obj("createFromTemplate", jsonOf("id" to id)) } ?: return@launchCore
        if (!r.b("ok")) { toast(r.s("error", "The engine couldn't build that template")); return@launchCore }
        hap(Hap.Done); refreshHomeSide(); plan = obj("plan")
        screen = Screen.Tabs; tab = Tab.Today
        toast("${r.s("name")} is ready")
        if (r.b("percent")) tmFor = r.s("programId")
    }
    suspend fun trainingMaxes(programId: String): JSONObject? = obj("trainingMaxes", jsonOf("programId" to programId))
    suspend fun setTrainingMax(programId: String, exId: String, value: Double?, fromOneRm: Boolean): JSONObject? {
        val r = obj("setTrainingMax", jsonOf("programId" to programId, "exId" to exId, "value" to value, "fromOneRm" to fromOneRm))
        home = obj("home"); plan = obj("plan"); publishWidget()
        return r
    }

    /* ---------------------------------------------------------------- cycles */
    fun createCycle(config: JSONObject, templateId: String, blocks: JSONArray?, adapt: Boolean) = launchCore {
        val r = working("Building your training cycle") { obj("createCycle", jsonOf("config" to config, "templateId" to templateId, "blocks" to blocks, "adapt" to adapt)) } ?: return@launchCore
        if (!r.b("ok")) { toast(r.s("error", "The cycle couldn't be built. Nothing was saved.")); return@launchCore }
        hap(Hap.Done)
        refreshHomeSide(); plan = obj("plan")
        screen = Screen.Tabs; tab = Tab.Plan
        toast("Cycle started: ${r.i("blocks")} blocks, beginning with ${r.s("first")}")
    }
    suspend fun cycleDetail(id: String): JSONObject? = obj("cycleDetail", jsonOf("id" to id))
    suspend fun reviewBlock(id: String, index: Int): JSONObject? = obj("reviewBlock", jsonOf("id" to id, "index" to index))
    fun advanceCycle(id: String, after: () -> Unit = {}) = launchCore {
        val r = working("Preparing your next block") { obj("advanceCycle", jsonOf("id" to id)) } ?: return@launchCore
        if (!r.b("ok")) { toast(r.s("error", "The next block couldn't be prepared. Nothing changed.")); return@launchCore }
        hap(Hap.Done); refreshHomeSide(); plan = obj("plan"); after()
        toast(r.s("message", "Advanced to the next block"))
    }
    fun deleteCycle(id: String, alsoBlocks: Boolean) = launchCore {
        val r = obj("deleteCycle", jsonOf("id" to id, "alsoBlocks" to alsoBlocks)) ?: return@launchCore
        if (!r.b("ok")) return@launchCore
        hap(Hap.Undo); refreshHomeSide(); plan = obj("plan")
        toast("Removed ${r.s("name")}" + if (alsoBlocks) " and its blocks" else "") { launchCore { call("undo"); refreshHomeSide(); plan = obj("plan"); toast("Restored") } }
    }
    fun nextBlock(programId: String) = launchCore {
        val r = working("Preparing your next block") { obj("nextBlock", jsonOf("programId" to programId)) } ?: return@launchCore
        if (!r.b("ok")) { toast(r.s("error", "The next block couldn't be created. Your program is unchanged.")); return@launchCore }
        hap(Hap.Done); refreshHomeSide(); plan = obj("plan")
        toast("${r.s("name")} is ready, built from your completed workouts")
    }

    /* ---------------------------------------------------------------- program editor */
    var editor by mutableStateOf<JSONObject?>(null); private set
    private var editWeek = 1
    fun openEditor(programId: String) = launchCore {
        editWeek = 1
        editor = obj("editor", jsonOf("programId" to programId, "weekIndex" to 1)) ?: return@launchCore
        screen = Screen.Editor
    }
    private suspend fun reloadEditor() { val id = editor?.s("programId") ?: return; editor = obj("editor", jsonOf("programId" to id, "weekIndex" to editWeek)) }
    fun editWeek(w: Int) = launchCore { editWeek = w; reloadEditor() }
    fun edit(type: String, vararg args: Pair<String, Any?>) = launchCore {
        val id = editor?.s("programId") ?: return@launchCore
        val r = obj("editAct", jsonOf("programId" to id, "type" to type, *args)) ?: return@launchCore
        if (r.b("changed")) hap(Hap.Tick)
        reloadEditor()
    }
    suspend fun editSwapOptions(dayId: String, slot: Int): List<JSONObject> = arr(call("editSwapOptions", jsonOf("programId" to editor?.s("programId"), "dayId" to dayId, "slot" to slot)))
    suspend fun editAddOptions(dayId: String, q: String, part: String?): JSONObject? = obj("editAddOptions", jsonOf("programId" to editor?.s("programId"), "dayId" to dayId, "q" to q, "part" to part))
    fun saveEdits(scope: String) = launchCore {
        val id = editor?.s("programId") ?: return@launchCore
        val r = obj("saveEdits", jsonOf("programId" to id, "scope" to scope)) ?: return@launchCore
        if (!r.b("ok")) { toast(r.s("error", "Couldn't save")); return@launchCore }
        hap(Hap.Done); reloadEditor(); refreshHomeSide(); plan = obj("plan")
        val n = r.i("changes")
        if (n == 0) toast("Nothing changed") else toast(if (n == 1) "Saved" else "Saved $n cycle blocks") { launchCore { val u = obj("undoSave", jsonOf("programId" to id)); if (u?.b("ok") == true) { reloadEditor(); refreshHomeSide(); plan = obj("plan"); toast("Save undone") } else toast(u?.s("error", "Couldn't undo") ?: "Couldn't undo") } }
    }
    suspend fun programSettings(): JSONObject? = obj("programSettings", jsonOf("programId" to editor?.s("programId")))
    fun applyProgramSettings(settings: JSONObject?, shuffle: Boolean = false, seed: Long? = null, after: () -> Unit = {}) = launchCore {
        val id = editor?.s("programId") ?: return@launchCore
        val a = jsonOf("programId" to id, "settings" to (settings ?: JSONObject()), "shuffle" to shuffle); if (seed != null) a.put("seed", seed)
        val rebuilding = shuffle || seed != null
        val r = (if (rebuilding || settings != null) working("Rebuilding your program") { obj("applyProgramSettings", a) } else obj("applyProgramSettings", a)) ?: return@launchCore
        if (!r.b("ok")) { toast(r.s("error", "Couldn't change that")); return@launchCore }
        if (r.b("changed")) { hap(Hap.Done); reloadEditor(); refreshHomeSide(); plan = obj("plan") }
        toast(when { r.b("rebuilt") -> "Program rebuilt"; r.b("changed") -> "Program settings saved"; else -> "No changes" })
        after()
    }
    suspend fun comparePrograms(a: String, b: String): JSONObject? = obj("comparePrograms", jsonOf("a" to a, "b" to b))
    suspend fun compareExercises(a: String, b: String): JSONObject? = obj("compareExercises", jsonOf("a" to a, "b" to b))
    suspend fun searchExercises(q: String): List<JSONObject> = arr(call("library", jsonOf("q" to q)))

    fun discardEdits() = launchCore { val id = editor?.s("programId") ?: return@launchCore; call("discardEdits", jsonOf("programId" to id)); reloadEditor(); toast("Changes discarded") }
    fun closeEditor() { screen = Screen.Tabs; editor = null }
    fun deleteProgram(id: String) = launchCore {
        val r = obj("deleteProgram", jsonOf("id" to id)) ?: return@launchCore
        if (!r.b("ok")) { toast(r.s("error", "Couldn't delete that")); return@launchCore }
        hap(Hap.Undo); refreshHomeSide(); plan = obj("plan")
        toast("Deleted ${r.s("name")}") { launchCore { call("undo"); refreshHomeSide(); plan = obj("plan"); toast("Restored") } }
    }
    fun duplicateProgram(id: String) = launchCore {
        val r = obj("duplicateProgram", jsonOf("id" to id)) ?: return@launchCore
        if (r.b("ok")) { refreshHomeSide(); plan = obj("plan"); toast("Duplicated as ${r.s("name")}" + if (r.b("detached")) " · detached from its cycle" else "") }
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
    fun logBodyweight(v: Double) = launchCore { call("logBodyweight", jsonOf("value" to v)); body = obj("body"); profile = obj("profile"); hap(Hap.Log); toast("Bodyweight logged") }
    fun logMeasurement(key: String, v: Double) = launchCore { call("logMeasurement", jsonOf("key" to key, "value" to v)); body = obj("body") }
    fun updateSetting(key: String, value: Any?) = launchCore {
        val r = obj("updateSettings", jsonOf(key to value)) ?: return@launchCore
        if (!r.b("ok")) toast(r.s("error", "Couldn't change that"))
        settings = obj("settings"); haptics.enabled = settings?.b("haptics", true) != false
        if (key == "unit" && r.b("ok")) { refreshAll(); toast("Switched to ${value} and converted your weights") }
    }

    /* ---------------------------------------------------------------- reminders, CSV, custom exercises */
    fun setReminders(enabled: Boolean? = null, time: String? = null, days: List<Int>? = null) = launchCore {
        val a = JSONObject(); if (enabled != null) a.put("enabled", enabled); if (time != null) a.put("time", time); if (days != null) a.put("days", JSONArray(days))
        val r = obj("setReminders", a) ?: return@launchCore
        com.pursuitiron.app.data.Reminders.save(getApplication(), r.o("reminders"))
        settings = obj("settings")
    }
    data class CsvOffer(val text: String, val summary: JSONObject)
    var csvOffer by mutableStateOf<CsvOffer?>(null)
    /** A file the user picked: a backup goes through the import dialog, a Strong/Hevy CSV through its own preview. */
    fun offerFile(text: String) = launchCore {
        val backup = runCatching { com.pursuitiron.app.data.Transfer.parse(text, "backup") }.getOrNull()
        if (backup != null) { importOffer = backup; return@launchCore }
        val r = obj("importStore", jsonOf("text" to text, "preview" to true))
        if (r != null && r.b("ok") && r.o("csv") != null) csvOffer = CsvOffer(text, r.o("csv")!!)
        else toast(r?.s("error", "That file isn't a Pursuit Iron backup or a Strong/Hevy export") ?: "That file couldn't be read")
    }
    fun acceptCsv(weightUnit: String?) = launchCore {
        val o = csvOffer ?: return@launchCore
        csvOffer = null
        val r = working("Importing workouts") { obj("importStore", jsonOf("text" to o.text, "csvWeightUnit" to weightUnit)) } ?: return@launchCore
        if (!r.b("ok")) { toast(r.s("error", "That file couldn't be imported")); return@launchCore }
        refreshAll(); hap(Hap.Done)
        toast("Imported ${plural(r.o("csv").i("workouts"), "workout")} from ${r.o("csv").s("label")}")
    }
    suspend fun exportCsv(kind: String): String? = call("exportCsv", jsonOf("kind" to kind)) as? String
    var customs by mutableStateOf<List<JSONObject>>(emptyList()); private set
    fun loadCustoms() = launchCore { customs = arr(call("customExercises")) }
    fun createCustom(name: String, part: String, type: String, equip: List<String>, lo: Int, hi: Int, dayId: String?) = launchCore {
        val r = obj("createCustomExercise", jsonOf("name" to name, "part" to part, "type" to type, "equip" to JSONArray(equip), "lo" to lo, "hi" to hi,
            "programId" to (if (dayId != null) editor?.s("programId") else null), "dayId" to dayId)) ?: return@launchCore
        if (!r.b("ok")) { toast(r.s("error", "Couldn't create that")); return@launchCore }
        hap(Hap.Done); customs = arr(call("customExercises")); library = arr(call("library", jsonOf("q" to libQuery, "part" to libPart)))
        if (dayId != null) reloadEditor()
        toast(if (dayId != null) "Created and added ${r.s("name")}" else "Created ${r.s("name")}")
    }
    fun deleteCustom(id: String) = launchCore {
        val r = obj("deleteCustomExercise", jsonOf("id" to id)) ?: return@launchCore
        if (!r.b("ok")) toast(r.s("error", "Couldn't delete that")) else { customs = arr(call("customExercises")); library = arr(call("library", jsonOf("q" to libQuery, "part" to libPart))) }
    }

    /* ---------------------------------------------------------------- profile, tools, gyms */
    fun setProfile(sex: String? = null, birth: String? = null) = launchCore {
        val a = JSONObject(); if (sex != null) a.put("sex", sex); if (birth != null) a.put("birth", birth)
        val r = obj("setProfile", a) ?: return@launchCore
        if (!r.b("ok")) toast(r.s("error", "Couldn't save that")) else profile = obj("profile")
    }
    suspend fun calendar(year: Int, month: Int): JSONObject? = obj("calendar", jsonOf("year" to year, "month" to month))
    suspend fun plateCalc(total: Double?, bar: String): JSONObject? = obj("plateCalc", jsonOf("total" to total, "bar" to bar))
    suspend fun oneRm(weight: Double?, reps: Int?): JSONObject? = obj("oneRm", jsonOf("weight" to weight, "reps" to reps))
    suspend fun newGym(preset: String): JSONObject? = obj("newGym", jsonOf("preset" to preset))
    fun saveGyms(list: JSONArray, activeId: String, removed: List<String>) = launchCore {
        val r = obj("saveGyms", jsonOf("gyms" to list, "activeGymId" to activeId, "removed" to JSONArray(removed))) ?: return@launchCore
        if (r.b("ok")) { gyms = obj("gyms"); home = obj("home"); toast("Gyms saved") }
    }
    fun setActiveGym(id: String) = launchCore {
        val g = gyms ?: return@launchCore
        val list = JSONArray(); g.list("gyms").forEach { x -> list.put(JSONObject(x.toString()).apply { remove("limitKeys"); remove("inventoryKeys"); remove("barDefaults") }) }
        call("saveGyms", jsonOf("gyms" to list, "activeGymId" to id)); gyms = obj("gyms"); home = obj("home")
    }
    fun setPlates(plates: List<Double>) = launchCore { call("setPlates", jsonOf("plates" to JSONArray(plates))); gyms = obj("gyms") }
    fun setMinInc(v: Double) = launchCore { call("setMinInc", jsonOf("v" to v)); gyms = obj("gyms") }

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
        toast("Brought over ${plural(added.i("sessions"), "workout")} and ${plural(added.i("programs"), "program")}")
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
