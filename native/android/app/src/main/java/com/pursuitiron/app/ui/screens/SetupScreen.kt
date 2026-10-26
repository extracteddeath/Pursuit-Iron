@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.pursuitiron.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pursuitiron.app.AppViewModel
import com.pursuitiron.app.ui.common.PiIcons
import com.pursuitiron.app.ui.common.list
import com.pursuitiron.app.ui.common.s
import com.pursuitiron.app.ui.theme.Motion
import org.json.JSONArray
import org.json.JSONObject

/* The design's Create wizard (Main.dc.html wz, web: App.js builder): one question a screen, a progress bar, Back
   and Next, a live "Your setup" summary, and "Generate with these settings" once the essentials are answered.
   From the guided setup it also asks units, experience and equipment; from the Plan it uses the active gym. */

private val WZ = object {
    val trophy = PiIcons.icon("wz-trophy", "M8 21h8M12 17v4M7 4h10v5a5 5 0 0 1-10 0zM7 6H4a3 3 0 0 0 3 4M17 6h3a3 3 0 0 1-3 4")
    val cal = PiIcons.icon("wz-cal", "M4 6h16v14H4zM4 10h16M8 3v4M16 3v4")
    val clock = PiIcons.icon("wz-clock", "M12 7v5l3 2M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18z")
    val rep = PiIcons.icon("wz-rep", "M17 2l4 4-4 4M3 11V9a3 3 0 0 1 3-3h15M7 22l-4-4 4-4M21 13v2a3 3 0 0 1-3 3H3")
    val layers = PiIcons.icon("wz-layers", "M12 3l9 5-9 5-9-5zM3 13l9 5 9-5")
    val flame = PiIcons.icon("wz-flame", "M12 22c4 0 7-3 7-7 0-5-5-7-5-12-3 2-6 6-6 9-1-1-2-2-2-4-1 2-1 4-1 6 0 5 3 8 7 8z")
    val zap = PiIcons.icon("wz-zap", "M13 2 4 14h7l-1 8 9-12h-7z")
    val wand = PiIcons.icon("wz-wand", "M15 4V2M15 10V8M11 6h2M17 6h2M4 20 14 10")
    val pencil = PiIcons.icon("wz-pencil", "M12 20h9M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4z")
    val battery = PiIcons.icon("wz-battery", "M3 8h15v8H3zM21 11v2M6 11v2")
    val dumb = PiIcons.icon("wz-dumb", "M6.5 6.5v11M17.5 6.5v11M3.5 9v6M20.5 9v6M6.5 12h11")
}

private val SESS = listOf("s20" to "Up to 20 minutes", "s40" to "20 to 40 minutes", "s60" to "40 to 60 minutes", "s90" to "60 to 90 minutes", "s120" to "90 to 120 minutes", "s120p" to "Over 120 minutes")
private val PRESETS = listOf(
    "Full gym" to listOf("barbell", "dumbbell", "bench", "cable", "machine", "smith", "ezbar", "pullup", "dip", "kettlebell", "bands"),
    "Barbell + rack" to listOf("barbell", "bench", "ezbar", "pullup"),
    "Dumbbells + bench" to listOf("dumbbell", "bench", "pullup"),
    "Dumbbells only" to listOf("dumbbell"),
    "Bodyweight" to emptyList(),
)
private val EXP = listOf(Triple("none", "None", "Brand new to lifting"), Triple("beginner", "Beginner", "< 1 year training"), Triple("intermediate", "Intermediate", "1–3 years training"), Triple("advanced", "Advanced", "3+ years, dialled-in"))
private val GOALS = mapOf("hypertrophy" to "Hypertrophy", "strength" to "Strength", "both" to "Strength & Size")

@Composable
fun SetupScreen(vm: AppViewModel) {
    val opts = vm.setup
    val cs = MaterialTheme.colorScheme
    val full = vm.setupFull
    val activeGym = vm.gyms?.let { g -> g.list("gyms").firstOrNull { it.s("id") == g.s("activeGymId") } }
    val gymEquip = activeGym?.optJSONArray("equipment")?.let { a -> (0 until a.length()).map { a.optString(it) } }?.filter { it != "bodyweight" } ?: PRESETS[0].second
    // Answers
    var chose by remember { mutableStateOf(false) }
    var cyc by remember { mutableStateOf(vm.setupMode == "cycle") }
    var tplOpen by remember { mutableStateOf(vm.setupMode == "template") }
    var step by remember { mutableIntStateOf(0) }
    var unit by remember { mutableStateOf(vm.settings.s("unit", "kg")) }
    var name by remember { mutableStateOf("") }
    var exp by remember { mutableStateOf(vm.settings.s("experience").let { if (it == "novice") "none" else it }.ifBlank { "intermediate" }) }
    var goal by remember { mutableStateOf("hypertrophy") }
    var approach by remember { mutableStateOf("standard") }
    var days by remember { mutableIntStateOf(4) }
    var split by remember { mutableStateOf<String?>(null) }
    var session by remember { mutableStateOf("s60") }
    var equip by remember { mutableStateOf(if (full) PRESETS[0].second.toSet() else gymEquip.toSet()) }
    var noRack by remember { mutableStateOf(false) }
    var noBodyweight by remember { mutableStateOf(false) }
    var noSupersets by remember { mutableStateOf(false) }
    var barbellCap by remember { mutableStateOf<Int?>(null) }
    val focus = remember { mutableStateMapOf<String, Int>() }
    val reduce = remember { mutableStateListOf<String>() }
    var progression by remember { mutableStateOf("auto") }
    var weeks by remember { mutableIntStateOf(4) }
    var endless by remember { mutableStateOf(false) }
    var deload by remember { mutableStateOf(false) }
    var template by remember { mutableStateOf(vm.cycles.list("templates").firstOrNull()?.s("id") ?: "powerbuilding") }
    val custom = remember { mutableStateListOf(CustomBlock("hypertrophy_accumulation", 6), CustomBlock("strength_accumulation", 5), CustomBlock("peak", 3)) }
    var adapt by remember { mutableStateOf(false) }
    var revOpen by remember { mutableStateOf(false) }
    var pick by remember { mutableStateOf<String?>(null) }

    // Steps, as the design orders them
    val sk = listOf("units", "name", "experience", "goal", "approach", "days", "split", "session", "equipment", "constraints", "focus", "reduce", "progression", "length", "deload")
    val ck = listOf("cycletype", "units", "name", "experience", "approach", "days", "split", "session", "equipment", "constraints", "focus", "reduce", "deload")
    var keys = if (cyc) (if (template == "custom") listOf("cycletype", "goal") + ck.drop(1) else ck) else sk
    if (!full) keys = keys.filter { it != "units" && it != "experience" && it != "equipment" }
    if (endless && !cyc) keys = keys.filter { it != "deload" }
    val steps = if (chose) keys else listOf("mode")
    val at = step.coerceIn(0, steps.size - 1)
    val key = steps[at]

    val eq = (if (full) equip else gymEquip.toSet()).let { if (noRack) it + "no-rack" else it }
    val focusTotal = focus.values.sum()
    fun config(): JSONObject = JSONObject()
        .put("unit", unit).put("goal", goal).put("experience", exp).put("days", days).put("session", session)
        .put("equipment", JSONArray(eq.toList())).put("volumeApproach", approach)
        .put("noBodyweight", noBodyweight).put("noSupersets", noSupersets).put("barbellCap", barbellCap ?: JSONObject.NULL)
        .put("focus", JSONObject().apply { focus.forEach { (k, v) -> if (v > 0) put(k, v) } })
        .put("reduce", JSONArray(reduce.filter { (focus[it] ?: 0) == 0 }))
        .apply { if (name.isNotBlank()) put("name", name.trim().take(60)) }
        .apply { if (!cyc) { if (endless) put("endless", true).put("weeks", 5).put("deload", true).put("progression", "auto") else put("weeks", weeks).put("deload", deload).put("progression", progression) } else put("deload", deload) }
    LaunchedEffect(goal, exp, days, session, equip, noRack) { pick = vm.recommend(config()) }

    val sessIdx = { id: String? -> SESS.indexOfFirst { it.first == id } }
    val splits = opts.list("splits").filter { sp -> sp.optJSONArray("days")?.let { a -> (0 until a.length()).any { a.optInt(it) == days } } ?: true }
    val fits = { sp: JSONObject, ses: String -> val i = sessIdx(ses); (sp.s("minSession").let { it.isBlank() || it == "null" || i >= sessIdx(it) }) && (sp.s("maxSession").let { it.isBlank() || it == "null" || i <= sessIdx(it) }) }
    val chosenSplit = split?.takeIf { s -> splits.any { it.s("id") == s } } ?: pick?.takeIf { p -> splits.any { it.s("id") == p } } ?: splits.firstOrNull { fits(it, session) }?.s("id") ?: splits.firstOrNull()?.s("id")
    val sd = splits.firstOrNull { it.s("id") == chosenSplit }
    val sessChoices = SESS.filter { sd == null || fits(sd, it.first) }
    val effSession = if (sessChoices.any { it.first == session }) session else sessChoices.firstOrNull()?.first ?: session

    val nameOk = name.isNotBlank()
    val cycleOk = !cyc || template != "custom" || (custom.size in 2..8 && custom.all { it.weeks in 1..24 } && custom.sumOf { it.weeks } <= 104)
    val canNext = when (key) { "name" -> nameOk; "cycletype" -> cycleOk; "split" -> chosenSplit != null; else -> true }
    val essEnd = listOf("constraints", "equipment", "session").map { steps.indexOf(it) }.firstOrNull { it > -1 } ?: -1
    val canEarly = key != "mode" && canNext && essEnd > -1 && at > essEnd && at < steps.size - 1
    fun finish() {
        if (!nameOk) { step = steps.indexOf("name"); vm.toast("Name it first — it’s the one required answer"); return }
        val c = config().put("split", chosenSplit).put("session", effSession)
        if (cyc) vm.createCycle(c, template, if (template == "custom") customBlocksJson(custom) else null, adapt) else vm.createProgram(c)
    }
    fun next() {
        if (!canNext) return
        when {
            key == "mode" -> { chose = true; step = 0; tplOpen = false }
            at < steps.size - 1 -> { step = at + 1; revOpen = false }
            else -> finish()
        }
    }
    fun back() {
        when {
            !chose && at == 0 -> vm.closeSetup()
            chose && at == 0 -> { chose = false; step = 0 }
            else -> step = at - 1
        }
    }
    BackHandler { back() }

    val heads = mapOf(
        "mode" to ("What do you want to build?" to "Both ask the same questions about your training — a cycle just chains multiple blocks together."),
        "units" to ("Which units do you train in?" to "Used everywhere — plates, targets, history. You can change it later in Settings."),
        "cycletype" to ("Which cycle do you want to run?" to "Each phase is planned around its goal, then later blocks can adapt from the workouts you actually complete."),
        "name" to ((if (cyc) "Name your cycle" else "Name your program") to "You can rename it any time after it’s built."),
        "experience" to ("What’s your experience level?" to "This scales your weekly set volume sensibly."),
        "goal" to ("What’s your main goal?" to "Drives rep ranges, set counts and progression."),
        "approach" to ("Training approach" to "How much volume per exercise. Both build muscle — pick what fits your recovery and time."),
        "days" to ("How many days per week?" to "Only splits that fit this frequency are shown."),
        "split" to ("Pick your split" to "Choose how to spread $days training days across the week."),
        "session" to ("How much time per session?" to "Choose a capacity band, not a quota. Pursuit uses the time when another set is worth its fatigue cost."),
        "equipment" to ("What equipment do you have?" to "Only exercises you can actually perform get programmed. Bodyweight is available by default and can be excluded below."),
        "constraints" to ("Workout constraints" to "Choose how the workout is allowed to use gym space. These are firm generation constraints, not suggestions."),
        "focus" to ("Focus on any muscles?" to "Prioritise these muscles within your time and equipment limits. One point raises priority; two points marks a stronger specialization target."),
        "reduce" to ("Any muscles to reduce?" to "Aim for maintenance work on these muscles. Shared compound lifts and fixed program prescriptions may limit the reduction. Focused muscles are hidden here."),
        "progression" to ("How should progression work?" to "Auto chooses the progression that fits each exercise and this block, then re-checks it from your training history. Manual keeps the weekly prescription in your hands."),
        "length" to ("Block length" to "Pick a fixed block that resets on a set schedule, or go continuous — training that auto-progresses forever and manages its own deloads."),
        "deload" to ("Add a deload week?" to "A deload adds a lighter recovery week at the end."),
    )

    Column(Modifier.fillMaxSize().background(cs.surface).statusBarsPadding()) {
        // Top bar: back, title, step count, progress
        Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                IconButton(onClick = { back() }) { Icon(PiIcons.ChevronLeft, "Back") }
                Text(if (key == "mode") "Create" else if (cyc) "Create Cycle" else "Create Program", style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                Text(if (key == "mode") "" else "${at + 1}/${steps.size}", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold), color = cs.onSurfaceVariant,
                    modifier = Modifier.widthIn(min = 48.dp), textAlign = TextAlign.Center)
            }
            val prog by animateFloatAsState(if (key == "mode") 0.06f else (at + 1f) / steps.size, Motion.data(), label = "wz")
            Box(Modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp).fillMaxWidth().height(4.dp).clip(CircleShape).background(cs.surfaceContainerHighest)
                .semantics { contentDescription = "Setup progress, step ${at + 1} of ${steps.size}" }) {
                Box(Modifier.fillMaxWidth(prog).fillMaxHeight().clip(CircleShape).background(cs.primary))
            }
        }
        // The question
        AnimatedContent(key to cyc, transitionSpec = {
            val fwd = steps.indexOf(targetState.first) >= steps.indexOf(initialState.first)
            (slideInHorizontally(Motion.spatial()) { if (fwd) it / 6 else -it / 6 } + fadeIn(Motion.enter(250))) togetherWith (slideOutHorizontally(Motion.spatial()) { if (fwd) -it / 6 else it / 6 } + fadeOut(Motion.exit(120)))
        }, label = "wzStep", modifier = Modifier.weight(1f)) { (k, _) ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(top = 18.dp, bottom = 24.dp).widthIn(max = 760.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val (h, sub) = heads[k] ?: ("" to "")
                Column(Modifier.padding(bottom = 6.dp)) {
                    Text(h, style = MaterialTheme.typography.headlineSmall.copy(fontSize = 28.sp, lineHeight = 32.sp), modifier = Modifier.semantics { heading() })
                    Text(if (k == "deload") "Your block runs ${if (cyc) "its first block's" else "$weeks"} weeks. A deload adds a lighter recovery week at the end." else sub,
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 21.sp), color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                }
                when (k) {
                    "mode" -> {
                        WzOption("Single program", "One training block built around your goal, schedule, and gym.", WZ.dumb, !cyc) { cyc = false }
                        WzOption("Training cycle", "Multiple blocks that progress from one phase to the next over time.", WZ.layers, cyc) { cyc = true }
                        Box(Modifier.padding(top = 6.dp).height(44.dp).clip(CircleShape).background(cs.tertiaryContainer).clickable { tplOpen = !tplOpen }
                            .semantics { role = Role.Button }.padding(horizontal = 18.dp), contentAlignment = Alignment.Center) {
                            Text(if (tplOpen) "Hide templates" else "Start from a template · ${vm.templates?.list("items")?.size ?: 0}",
                                style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold), color = cs.onTertiaryContainer)
                        }
                        AnimatedVisibility(tplOpen, enter = Motion.expandIn, exit = Motion.shrinkOut) { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { TemplatePicker(vm) } }
                    }
                    "units" -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        listOf("kg" to "Kilograms", "lb" to "Pounds").forEach { (u, l) ->
                            val on = unit == u
                            val r by animateDpAsState(if (on) 22.dp else 28.dp, Motion.spatialFast(), label = "u")
                            val bg by animateColorAsState(if (on) cs.secondaryContainer else cs.surfaceContainerLow, Motion.effects(), label = "ub")
                            Column(Modifier.weight(1f).clip(RoundedCornerShape(r)).background(bg).border(if (on) 2.dp else 1.dp, if (on) cs.primary else cs.outlineVariant, RoundedCornerShape(r))
                                .clickable { unit = u }.semantics { role = Role.RadioButton; selected = on }.padding(vertical = 26.dp, horizontal = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(u, style = MaterialTheme.typography.displaySmall.copy(fontSize = 34.sp), color = if (on) cs.onSecondaryContainer else cs.onSurface)
                                Text(l, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                            }
                        }
                    }
                    "cycletype" -> CycleTemplatePicker(vm, template, { template = it }, custom, adapt, { adapt = it })
                    "name" -> OutlinedTextField(name, { name = it.take(60) }, Modifier.fillMaxWidth().semantics { contentDescription = if (cyc) "Cycle name" else "Program name" }, singleLine = true,
                        placeholder = { Text(if (cyc) "e.g. Summer Training Cycle" else "e.g. Summer Hypertrophy Block") }, shape = RoundedCornerShape(18.dp),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp), keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
                    "experience" -> EXP.forEach { (id, l, s) -> WzOption(l, s, WZ.trophy, exp == id) { exp = id } }
                    "goal" -> listOf(Triple("hypertrophy", "Build muscle & size", WZ.layers), Triple("strength", "Get strong, lower reps", WZ.flame), Triple("both", "Heavy main lifts, hypertrophy accessories", WZ.zap))
                        .forEach { (id, s, ic) -> WzOption(GOALS[id]!!, s, ic, goal == id) { goal = id } }
                    "approach" -> {
                        WzOption("Standard volume", "Balanced weekly volume and recovery. The best default for most lifters.", WZ.layers, approach == "standard") { approach = "standard" }
                        WzOption("Minimalist · high intensity", "Fewer hard sets taken closer to failure. Faster sessions with less total volume.", WZ.flame, approach == "minimalist") { approach = "minimalist" }
                    }
                    "days" -> (2..6).forEach { d ->
                        val n = opts.list("splits").count { sp -> sp.optJSONArray("days")?.let { a -> (0 until a.length()).any { a.optInt(it) == d } } ?: true }
                        WzOption("$d days / week", "$n compatible splits", WZ.cal, days == d) { days = d; split = null }
                    }
                    "split" -> splits.sortedBy { if (it.s("id") == pick) 0 else 1 }.forEach { sp ->
                        WzOption(sp.s("name") + if (sp.s("id") == pick) " · Recommended" else "", sp.s("blurb"), WZ.rep, sp.s("id") == chosenSplit) { split = sp.s("id") }
                    }
                    "session" -> sessChoices.forEach { (id, l) -> WzOption(l, "Optimized inside this time band", WZ.clock, effSession == id) { session = id } }
                    "equipment" -> EquipmentStep(opts, equip, { equip = it }, noRack, { noRack = it }, noBodyweight, { noBodyweight = it })
                    "constraints" -> {
                        WzKicker("SUPERSETS")
                        Text("Pairing two exercises with opposing muscles cuts rest time, so more work fits in the same session. It needs both stations free.", style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 18.sp), color = cs.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            WzSeg("Use supersets", !noSupersets, Modifier.weight(1f)) { noSupersets = false }
                            WzSeg("No supersets", noSupersets, Modifier.weight(1f)) { noSupersets = true }
                        }
                        WzKicker("BARBELL LIMIT", Modifier.padding(top = 14.dp))
                        Text("Cap barbell movements per session so a busy commercial gym (racks, platforms) doesn’t bottleneck your workout — dumbbell, machine & cable lifts are used instead." +
                            if ("barbell" !in eq) " Applies once you add a barbell." else "", style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 18.sp), color = cs.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf<Pair<Int?, String>>(null to "No limit", 1 to "1", 2 to "2", 3 to "3").forEach { (v, l) -> WzSeg(l, barbellCap == v, Modifier.weight(1f)) { barbellCap = v } }
                        }
                        val need = sd?.optInt("minBarbells", 0) ?: 0
                        if (barbellCap != null && need > 0 && barbellCap!! < need) Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(cs.tertiaryContainer).padding(horizontal = 14.dp, vertical = 12.dp)) {
                            Text("Most sessions here use $need or more barbell lifts", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold), color = cs.onTertiaryContainer)
                            Text("${sd?.s("name")} is built around its main barbell lifts, so a limit of $barbellCap cannot be met on most days. Raise the limit, or pick a program that leans on machines and dumbbells.",
                                style = MaterialTheme.typography.bodySmall, color = cs.onTertiaryContainer)
                        }
                    }
                    "focus" -> {
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(cs.surfaceContainerHigh).padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Text("Focus points used", style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp), color = cs.onSurfaceVariant, modifier = Modifier.weight(1f))
                            Text("$focusTotal / 5", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold), color = if (focusTotal >= 5) cs.primary else cs.onSurface)
                        }
                        opts.list("parts").forEach { p ->
                            val id = p.s("id"); val v = focus[id] ?: 0
                            val bg by animateColorAsState(if (v > 0) cs.secondaryContainer else cs.surfaceContainerLow, Motion.effects(), label = "f")
                            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(bg).padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Column(Modifier.weight(1f)) {
                                    Text(p.s("label"), style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold))
                                    if (id == "neck") Text("Rarely fits — most sessions have no room for direct neck work", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                                }
                                WzStep("−", v > 0, "Decrease ${p.s("label")} focus") { val nv = (v - 1).coerceAtLeast(0); if (nv == 0) focus.remove(id) else focus[id] = nv }
                                Text("$v", style = MaterialTheme.typography.titleMedium, modifier = Modifier.widthIn(min = 24.dp), textAlign = TextAlign.Center)
                                WzStep("+", v < 2 && focusTotal < 5, "Increase ${p.s("label")} focus") { focus[id] = v + 1; reduce.remove(id) }
                            }
                        }
                    }
                    "reduce" -> opts.list("parts").filter { (focus[it.s("id")] ?: 0) == 0 }.forEach { p ->
                        val id = p.s("id"); val on = id in reduce
                        WzCheckRow(p.s("label"), null, on, RoundedCornerShape(18.dp)) { if (on) reduce.remove(id) else reduce.add(id) }
                    }
                    "progression" -> {
                        WzOption("Auto · choose per exercise", "Pursuit picks the method for each lift and only changes it when your training gives enough evidence", WZ.wand, progression == "auto") { progression = "auto" }
                        WzOption("Manual weekly targets", "You set the weekly sets and reps; loads still progress per lift", WZ.pencil, progression == "manual") { progression = "manual" }
                    }
                    "length" -> {
                        WzOption("Endless — continuous", "No fixed end. Load and volume auto-progress every session; a light deload drops in automatically when you plateau or accumulate fatigue. Never runs out.", WZ.rep, endless) { endless = true }
                        listOf(4 to "Short block — Auto avoids methods that need a longer runway", 6 to "Balanced block — more room for advanced progression when it fits", 8 to "Long block — gradual ramp", 10 to "Extended accumulation for advanced lifters")
                            .forEach { (w, s) -> WzOption("$w weeks", s, WZ.cal, !endless && weeks == w) { weeks = w; endless = false } }
                    }
                    "deload" -> {
                        WzOption("Yes, add a deload", "Week ${(if (cyc) 0 else weeks) + 1} · reduced volume & load", WZ.battery, deload) { deload = true }
                        WzOption("No deload", "Train through the whole block", WZ.flame, !deload) { deload = false }
                    }
                }
                // Your setup (web: WizardReviewPane)
                val rows = buildList {
                    add("Format" to if (chose) (if (cyc) "Training cycle" else "Single program") else "Choose a format")
                    add("Name" to name.trim().ifBlank { "Not named yet" })
                    add("Goal" to if (chose) GOALS[goal]!! else "After choosing format")
                    if (!full) add("Experience" to (EXP.firstOrNull { it.first == exp }?.second ?: exp) + " · from your profile")
                    add("Schedule" to "$days days per week")
                    add("Split" to (sd?.s("name") ?: "Not selected"))
                    add("Session capacity" to (SESS.firstOrNull { it.first == effSession }?.second ?: "—"))
                    val nEq = eq.count { it != "no-rack" }
                    add("Equipment" to (if (nEq == 0) "Bodyweight only" else "$nEq selected types") + if (full) "" else " · ${activeGym?.s("name") ?: "your gym"}")
                    add("Training style" to if (approach == "minimalist") "Minimalist · high intensity" else "Standard volume")
                    add("Barbell limit" to (barbellCap?.let { "$it per day" } ?: "No daily limit"))
                    add("Supersets" to if (noSupersets) "Disabled" else "Allowed")
                    add("Bodyweight exercises" to if (noBodyweight) "Disabled" else "Allowed")
                    if (!cyc) { add("Block" to if (endless) "Endless" else "$weeks weeks" + if (deload) " + deload" else ""); add("Progression" to if (progression == "manual") "Manual weekly targets" else "Auto") }
                    val labelOf = { pid: String -> opts.list("parts").firstOrNull { it.s("id") == pid }?.s("label") ?: pid }
                    focus.filter { it.value > 0 }.takeIf { it.isNotEmpty() }?.let { f -> add("Emphasize" to f.entries.joinToString(", ") { labelOf(it.key) + if (it.value > 1) " ×${it.value}" else "" }) }
                    reduce.filter { (focus[it] ?: 0) == 0 }.takeIf { it.isNotEmpty() }?.let { r -> add("Reduce" to r.joinToString(", ") { labelOf(it) }) }
                }
                Box(Modifier.padding(top = 10.dp).fillMaxWidth().height(46.dp).clip(CircleShape).background(cs.surfaceContainerHigh).clickable { revOpen = !revOpen }
                    .semantics { role = Role.Button; stateDescription = if (revOpen) "Expanded" else "Collapsed" }, contentAlignment = Alignment.Center) {
                    Text(if (revOpen) "Hide your setup" else "Your setup · ${rows.size} settings", style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold))
                }
                AnimatedVisibility(revOpen, enter = Motion.expandIn, exit = Motion.shrinkOut) {
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(cs.surfaceContainerLow).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Column {
                            Text("YOUR SETUP", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = 0.6.sp), color = cs.primary)
                            Text(if (chose && cyc) "Cycle overview" else "Program overview", style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, fontWeight = FontWeight.SemiBold))
                            Text(if (key == "mode") "Choose a program or a multi-phase cycle to begin." else "Step ${at + 1} of ${steps.size}. Your choices update here as you go.",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Normal), color = cs.onSurfaceVariant)
                        }
                        Column(Modifier.clip(RoundedCornerShape(18.dp)), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            rows.forEach { (k2, v2) ->
                                Row(Modifier.fillMaxWidth().background(cs.surface).padding(horizontal = 12.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text(k2, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant, modifier = Modifier.weight(1f))
                                    Text(v2, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(1.3f))
                                }
                            }
                        }
                        Text("This is a summary of your settings, not a generated workout. Exercise selection, working sets and progression are finalized when you build.",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Normal, letterSpacing = 0.sp), color = cs.onSurfaceVariant)
                    }
                }
            }
        }
        // Bottom bar
        Column(Modifier.fillMaxWidth().background(cs.surfaceContainer).navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.widthIn(min = 112.dp).height(54.dp).clip(CircleShape).background(cs.surfaceContainerHigh).clickable { back() }.semantics { role = Role.Button }.padding(horizontal = 18.dp), contentAlignment = Alignment.Center) {
                    Text(if (at == 0 && !chose) "Cancel" else "Back", style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold))
                }
                val bg by animateColorAsState(if (canNext) cs.primary else cs.surfaceContainerHighest, Motion.effects(), label = "next")
                Box(Modifier.weight(1f).height(54.dp).clip(CircleShape).background(bg).clickable(enabled = canNext) { next() }.semantics { role = Role.Button }, contentAlignment = Alignment.Center) {
                    Text(when { key == "mode" -> "Continue"; at == steps.size - 1 -> if (cyc) "Build Cycle" else "Generate Program"; else -> "Next" },
                        style = MaterialTheme.typography.titleSmall.copy(fontSize = 16.sp, fontWeight = FontWeight.ExtraBold), color = if (canNext) cs.onPrimary else cs.onSurfaceVariant)
                }
            }
            if (canEarly) {
                val left = steps.size - 1 - at
                Text((if (cyc) "Build with these settings" else "Generate with these settings") + " · $left optional ${if (left == 1) "step" else "steps"} left",
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold), color = cs.primary, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(12.dp)).clickable { finish() }.padding(vertical = 8.dp))
            }
        }
    }
}

/** The design's option card: icon tile, label and supporting text; selected squares off with a primary outline. */
@Composable
private fun WzOption(label: String, sub: String?, icon: ImageVector, on: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val r by animateDpAsState(if (on) 22.dp else 18.dp, Motion.spatialFast(), label = "opt")
    val ir by animateDpAsState(if (on) 14.dp else 20.dp, Motion.spatialFast(), label = "optI")
    val bg by animateColorAsState(if (on) cs.secondaryContainer else cs.surfaceContainerLow, Motion.effects(), label = "optBg")
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(r)).background(bg).border(if (on) 2.dp else 1.dp, if (on) cs.primary else cs.outlineVariant, RoundedCornerShape(r))
        .clickable(onClick = onClick).semantics(mergeDescendants = true) { role = Role.RadioButton; selected = on }.padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(ir)).background(if (on) cs.primary else cs.surfaceContainerHigh), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(20.dp), tint = if (on) cs.onPrimary else cs.onSurfaceVariant)
        }
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold), color = if (on) cs.onSecondaryContainer else cs.onSurface)
            if (!sub.isNullOrBlank()) Text(sub, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 18.sp), color = (if (on) cs.onSecondaryContainer else cs.onSurface).copy(alpha = 0.8f), modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
private fun WzKicker(t: String, modifier: Modifier = Modifier) = Text(t, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = 0.5.sp), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)

@Composable
private fun WzSeg(label: String, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val r by animateDpAsState(if (on) 14.dp else 24.dp, Motion.spatialFast(), label = "seg")
    val bg by animateColorAsState(if (on) cs.primary else cs.surfaceContainerHigh, Motion.effects(), label = "segBg")
    Box(modifier.height(48.dp).clip(RoundedCornerShape(r)).background(bg).clickable(onClick = onClick).semantics { role = Role.RadioButton; selected = on }, contentAlignment = Alignment.Center) {
        Text(label, style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, fontWeight = FontWeight.ExtraBold), color = if (on) cs.onPrimary else cs.onSurface)
    }
}

@Composable
private fun WzStep(sym: String, enabled: Boolean, desc: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(cs.surfaceContainerHigh).alpha(if (enabled) 1f else 0.35f).clickable(enabled = enabled, onClick = onClick)
        .semantics { contentDescription = desc; role = Role.Button }, contentAlignment = Alignment.Center) { Text(sym, fontSize = 20.sp) }
}

@Composable
private fun WzCheckRow(label: String, sub: String?, on: Boolean, shape: RoundedCornerShape? = null, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val bg by animateColorAsState(if (on) cs.secondaryContainer else cs.surfaceContainerLow, Motion.effects(), label = "chk")
    Row(Modifier.fillMaxWidth().then(if (shape != null) Modifier.clip(shape) else Modifier).background(bg).clickable(onClick = onClick)
        .semantics(mergeDescendants = true) { role = Role.Checkbox; stateDescription = if (on) "Checked" else "Not checked" }.padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(22.dp).clip(RoundedCornerShape(8.dp)).background(if (on) cs.primary else Color.Transparent).border(2.dp, if (on) cs.primary else cs.outline, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
            if (on) Icon(PiIcons.Check, null, Modifier.size(14.dp), tint = cs.onPrimary)
        }
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold), color = if (on) cs.onSecondaryContainer else cs.onSurface)
            if (!sub.isNullOrBlank()) Text(sub, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Normal), color = cs.onSurfaceVariant)
        }
    }
}

@Composable
private fun EquipmentStep(opts: JSONObject?, equip: Set<String>, onEquip: (Set<String>) -> Unit, noRack: Boolean, onNoRack: (Boolean) -> Unit, noBw: Boolean, onNoBw: (Boolean) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        PRESETS.forEach { (l, ids) ->
            val on = ids.toSet() == equip
            Box(Modifier.height(38.dp).clip(CircleShape).background(if (on) cs.primary else cs.surfaceContainerLow).clickable { onEquip(ids.toSet()) }
                .semantics { role = Role.Button; selected = on }.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                Text(l, style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold), color = if (on) cs.onPrimary else cs.onSurface)
            }
        }
    }
    Text(if (equip.isEmpty()) "Bodyweight only" else "${equip.size} selected types", style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold), color = cs.onSurfaceVariant)
    opts.list("equipment").filter { it.s("id") != "bodyweight" && it.s("id") != "no-rack" }.groupBy { it.s("cat").ifBlank { "Other" } }.forEach { (cat, items) ->
        Row(Modifier.padding(top = 6.dp)) {
            Text(cat.uppercase(), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = 0.5.sp), color = cs.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text("${items.count { it.s("id") in equip }} of ${items.size}", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
        }
        Column(Modifier.clip(RoundedCornerShape(20.dp)), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items.forEach { e -> val id = e.s("id"); val on = id in equip; WzCheckRow(e.s("label"), null, on) { onEquip(if (on) equip - id else equip + id) } }
        }
    }
    Spacer(Modifier.height(4.dp))
    Column(Modifier.clip(RoundedCornerShape(20.dp)), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if ("barbell" in equip) WzCheckRow("No squat rack", "Skips lifts taken from a rack or safeties — squats, bench and standing presses. Barbell work from the floor stays.", noRack) { onNoRack(!noRack) }
        WzCheckRow("Skip bodyweight exercises", "No push-ups, planks, bodyweight squats, etc. — only loaded movements.", noBw) { onNoBw(!noBw) }
    }
}
