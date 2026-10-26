@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.pursuitiron.app.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.border
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pursuitiron.app.AppViewModel
import com.pursuitiron.app.ui.LocalWide
import com.pursuitiron.app.ui.common.Badge
import com.pursuitiron.app.ui.common.Kicker
import com.pursuitiron.app.ui.common.MorphChip
import com.pursuitiron.app.ui.common.PiCard
import com.pursuitiron.app.ui.common.PiIcons
import com.pursuitiron.app.ui.common.ProgressBar
import com.pursuitiron.app.ui.common.b
import com.pursuitiron.app.ui.common.d
import com.pursuitiron.app.ui.common.i
import com.pursuitiron.app.ui.common.list
import com.pursuitiron.app.ui.common.mmss
import com.pursuitiron.app.ui.common.o
import com.pursuitiron.app.ui.common.s
import com.pursuitiron.app.ui.common.strings
import com.pursuitiron.app.ui.theme.Motion
import org.json.JSONObject

private sealed interface WSheet {
    data class Edit(val ei: Int, val si: Int) : WSheet
    data class Menu(val ei: Int) : WSheet
    data class Swap(val ei: Int) : WSheet
    data class Plateau(val ei: Int) : WSheet
    data class Why(val ei: Int) : WSheet
    data class Hurt(val ei: Int) : WSheet
    data class Info(val id: String) : WSheet
    data class Note(val ei: Int) : WSheet
    data class Rest(val ei: Int) : WSheet
    data class Progression(val ei: Int) : WSheet
    data class LiftSettings(val ei: Int) : WSheet
    data class Pin(val id: String) : WSheet
    data class AddSet(val ei: Int) : WSheet
    data class SetOptions(val ei: Int, val si: Int) : WSheet
    data class Plates(val ei: Int, val si: Int) : WSheet
    data object Superset : WSheet
    data object SessionNote : WSheet
    data object SessionMenu : WSheet
    data object Exercises : WSheet
    data class History(val id: String) : WSheet
    data object Time : WSheet
    data object AddLift : WSheet
    data object Finish : WSheet
}

@Composable
fun WorkoutScreen(vm: AppViewModel) {
    val s = vm.session
    // During the exit transition this screen still composes after the session has ended; only
    // leave if it is still the current screen, or it would jump past the summary.
    if (s == null || !s.b("active")) { LaunchedEffect(Unit) { if (vm.screen == com.pursuitiron.app.Screen.Workout) vm.minimise() }; return }
    val wide = LocalWide.current
    val cs = MaterialTheme.colorScheme
    var sheet by remember { mutableStateOf<WSheet?>(null) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var confirmFinish by remember { mutableStateOf(false) }

    // Keep the screen on while a workout is open.
    val view = LocalView.current
    DisposableEffect(vm.prefs.keepAwake) { view.keepScreenOn = vm.prefs.keepAwake; onDispose { view.keepScreenOn = false } }

    // Ask once for notifications, so rest can count down on the lock screen.
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 && !vm.prefs.askedNotifications && !vm.rest.allowed()) {
            vm.prefs.markAskedNotifications(); perm.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val exs = s.list("exercises")
    val ei = s.i("exIdx").coerceIn(0, (exs.size - 1).coerceAtLeast(0))
    val ex = exs.getOrNull(ei)
    val done = s.i("doneSets"); val total = s.i("totalSets")
    val tryFinish = { if (done == 0 || done < total) confirmFinish = true else sheet = WSheet.Finish }

    Box(Modifier.fillMaxSize().background(cs.surface)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            WorkoutHeader(vm, s, wide, vm.prefs.focus, onFocus = { vm.prefs.changeFocus(!vm.prefs.focus) }, onNote = { sheet = WSheet.SessionNote },
                onTime = { sheet = WSheet.Time }, onDiscard = { confirmDiscard = true }, onFinish = tryFinish)
            ProgressBar(if (total > 0) done.toFloat() / total else 0f, Modifier.padding(horizontal = 16.dp))
            if (wide) {
                Row(Modifier.fillMaxSize()) {
                    LazyColumn(Modifier.width(300.dp).fillMaxHeight(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        itemsIndexed(exs) { k, e ->
                            MorphChip(e.s("name"), k == ei, { vm.act("select", "ei" to k) }, Modifier.fillMaxWidth(), trailing = workCount(e), done = e.b("done"), height = 56.dp, strong = true)
                        }
                    }
                    ExercisePane(vm, s, ex, wide, Modifier.weight(1f)) { sheet = it }
                }
            } else {
                ExerciseChips(vm, exs, ei) { sheet = WSheet.Exercises }
                ExercisePane(vm, s, ex, wide, Modifier.weight(1f)) { sheet = it }
            }
        }

        val restEnd = vm.restEndsAt()
        AnimatedVisibility(
            visible = restEnd != null,
            enter = slideInVertically(Motion.enter()) { it } + fadeIn(Motion.effects()),
            exit = slideOutVertically(Motion.exit()) { it } + fadeOut(Motion.effectsFast()),
            modifier = Modifier.align(if (wide) Alignment.BottomEnd else Alignment.BottomCenter)
                .padding(start = 12.dp, end = if (wide) 24.dp else 12.dp, bottom = if (wide) 24.dp else 96.dp).navigationBarsPadding()
                .then(if (wide) Modifier.width(420.dp) else Modifier.fillMaxWidth()),
        ) { if (restEnd != null) RestCard(vm, s, restEnd) }

        if (!wide) Dock(vm, s, ei, exs, Modifier.align(Alignment.BottomCenter), tryFinish)
    }

    // The design asks readiness on Today (the check-in); the workout applies it as it opens, no sheet here.
    LaunchedEffect(s.s("sessionId"), s.b("readinessDone")) {
        if (!s.b("readinessDone")) {
            val ready = vm.home?.let { readinessToday(vm, it) } ?: 100
            val (label, factor) = readinessPick(ready)
            vm.act("readiness", "label" to label, "factor" to factor)
        }
    }
    // A plateau choice made before the workout ("Try a variation") opens the swap picker on that lift.
    LaunchedEffect(s.s("sessionId"), s.optInt("openSwap", -1)) {
        val k = if (s.isNull("openSwap")) -1 else s.optInt("openSwap", -1)
        if (k >= 0) { vm.act("swapOpened"); sheet = WSheet.Swap(k) }
    }
    when (val sh = sheet) {
        is WSheet.Edit -> SetEditor(vm, s, sh.ei, sh.si) { sheet = null }
        is WSheet.Menu -> ExerciseMenu(vm, s, sh.ei, onPick = { sheet = it }) { sheet = null }
        is WSheet.Swap -> SwapSheet(vm, sh.ei) { sheet = null }
        is WSheet.Plateau -> exs.getOrNull(sh.ei)?.let { PlateauSheet(vm, s, it, onSwap = { sheet = WSheet.Swap(sh.ei) }) { sheet = null } } ?: run { sheet = null }
        is WSheet.Why -> WhySheet(vm, sh.ei) { sheet = null }
        is WSheet.Hurt -> HurtSheet(vm, sh.ei) { sheet = null }
        is WSheet.Info -> ExerciseSheet(vm, sh.id) { sheet = null }
        is WSheet.Note -> NoteSheet(vm, s, sh.ei) { sheet = null }
        is WSheet.Rest -> exs.getOrNull(sh.ei)?.let { RestTimeSheet(vm, it) { sheet = null } } ?: run { sheet = null }
        is WSheet.Progression -> exs.getOrNull(sh.ei)?.let { ProgressionSheet(vm, s, it) { sheet = null } } ?: run { sheet = null }
        is WSheet.LiftSettings -> exs.getOrNull(sh.ei)?.let { e -> LiftSettingsSheet(vm, s, e, onPin = { sheet = WSheet.Pin(e.s("id")) }) { sheet = null } } ?: run { sheet = null }
        is WSheet.Pin -> PinNoteSheet(vm, sh.id) { sheet = null }
        is WSheet.AddSet -> exs.getOrNull(sh.ei)?.let { AddSetSheet(vm, it) { sheet = null } } ?: run { sheet = null }
        is WSheet.SetOptions -> SetOptionsSheet(vm, s, sh.ei, sh.si) { sheet = null }
        is WSheet.Plates -> exs.getOrNull(sh.ei)?.let { e ->
            val set = e.list("sets").getOrNull(sh.si)
            ToolsSheet(vm, "plates", set?.s("weight").orEmpty(), e.s("bar").ifBlank { "barbell" },
                onUse = if (set != null && !set.b("done")) { v -> vm.act("edit", "ei" to sh.ei, "si" to sh.si, "field" to "weight", "value" to v); vm.toast("${setLabelOf(e, sh.si)} → $v ${s.s("unit")}") } else null) { sheet = null }
        } ?: run { sheet = null }
        WSheet.Superset -> SupersetSheet(vm, s) { sheet = null }
        WSheet.SessionNote -> SessionNoteSheet(vm, s) { sheet = null }
        WSheet.Exercises -> ExercisesSheet(vm, s, onAdd = { sheet = WSheet.AddLift }) { sheet = null }
        WSheet.Time -> TimeSheet(vm, s) { sheet = null }
        is WSheet.History -> LiftDetailSheet(vm, sh.id) { sheet = null }
        WSheet.SessionMenu -> SessionMenu(vm, s, onNote = { sheet = WSheet.SessionNote }, onAdd = { sheet = WSheet.AddLift }, onSuperset = { sheet = WSheet.Superset }) { sheet = null }
        WSheet.AddLift -> AddLiftSheet(vm) { sheet = null }
        WSheet.Finish -> FinishPage(vm, s) { sheet = null }
        null -> {}
    }
    if (confirmDiscard) AlertDialog(
        onDismissRequest = { confirmDiscard = false },
        title = { Text("Discard this workout?") },
        text = { Text(if (done > 0) "You've logged $done working ${if (done == 1) "set" else "sets"}. They won't be saved, and nothing will progress from them." else "Nothing has been logged yet.") },
        confirmButton = { TextButton(onClick = { confirmDiscard = false; vm.discard() }) { Text("Discard") } },
        dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep going") } },
    )
    /* Nothing logged: there is nothing to save, so the choice is to keep going or discard (the design's empty-finish guard). */
    if (confirmFinish && done == 0) AlertDialog(
        onDismissRequest = { confirmFinish = false },
        title = { Text("Nothing logged yet") },
        text = { Text("A workout with no logged sets has nothing to save or progress from. Log a set, or discard this one.") },
        confirmButton = { TextButton(onClick = { confirmFinish = false; vm.discard() }) { Text("Discard workout") } },
        dismissButton = { Button(onClick = { confirmFinish = false }) { Text("Keep going") } },
    )
    else if (confirmFinish) AlertDialog(
        onDismissRequest = { confirmFinish = false },
        title = { Text("Finish with ${total - done} sets not logged?") },
        text = { Text("Only logged sets count. The engine plans your next session from what you actually did.") },
        confirmButton = { Button(onClick = { confirmFinish = false; sheet = WSheet.Finish }) { Text("Finish") } },
        dismissButton = { TextButton(onClick = { confirmFinish = false }) { Text("Keep going") } },
    )
}

private fun workCount(e: JSONObject): String {
    val work = e.list("sets").filter { !it.b("warm") && !it.b("sub") }
    return "${work.count { it.b("done") }}/${work.size}"
}
private fun shortName(n: String): String = n.split(' ').filter { it.isNotBlank() }.let { w -> if (n.length <= 14) n else w.takeLast(2).joinToString(" ") }

@Composable
private fun Header(vm: AppViewModel, s: JSONObject, wide: Boolean, onDiscard: () -> Unit, onFinish: () -> Unit, onMenu: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val elapsed = ((vm.now - s.optLong("startedAt")) / 1000).coerceAtLeast(0)
    val est = s.i("estimateMin")
    Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { vm.minimise() }, modifier = Modifier.size(48.dp)) { Icon(PiIcons.ChevronDown, "Minimise workout") }
        Column(Modifier.weight(1f)) {
            Text(s.s("dayLabel"), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(mmss(elapsed), style = MaterialTheme.typography.titleLarge)
                if (est > 0) {
                    val left = est - (elapsed / 60).toInt()
                    Surface(color = cs.surfaceContainerHigh, shape = CircleShape) {
                        Text(if (left > 0) "~$left min left" else "over by ${-left} min", style = MaterialTheme.typography.labelMedium,
                            color = cs.onSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                    }
                }
            }
        }
        IconButton(onClick = onMenu) { Icon(PiIcons.More, "Workout options", tint = cs.onSurfaceVariant) }
        IconButton(onClick = onDiscard) { Icon(PiIcons.Trash, "Discard workout", tint = cs.onSurfaceVariant) }
        if (wide) Button(onClick = onFinish, modifier = Modifier.height(44.dp)) { Text("Finish") }
    }
}

@Composable
private fun ExercisePane(vm: AppViewModel, s: JSONObject, ex: JSONObject?, wide: Boolean, modifier: Modifier, open: (WSheet) -> Unit) {
    val cs = MaterialTheme.colorScheme
    if (ex == null) {
        Column(modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(48.dp))
            Text(if (s.b("quick")) "Quick workout" else "No lifts left", style = MaterialTheme.typography.headlineSmall)
            Text("Add the lifts you're doing as you go. Finish when you're done; you can save it as a routine.", color = cs.onSurfaceVariant, textAlign = TextAlign.Center)
            Button(onClick = { open(WSheet.AddLift) }, modifier = Modifier.height(52.dp)) { Icon(PiIcons.Plus, null, modifier = Modifier.size(18.dp)); Text("  Add a lift") }
        }
        return
    }
    val ei = ex.i("ei")
    val uri = androidx.compose.ui.platform.LocalUriHandler.current
    var cuesOpen by remember(ex.s("id")) { mutableStateOf(false) }
    val sets = ex.list("sets")
    val injury = s.list("injuries").firstOrNull { inj -> inj.optJSONArray("exercises")?.let { a -> (0 until a.length()).any { a.optString(it) == ex.s("id") } } == true }
    val lastDoneWork = sets.indexOfLast { it.b("done") && !it.b("warm") && !it.b("sub") }
    // Working-set numbers come from the data, not a counter: a counter outside the lazy item kept
    // counting each time the item recomposed (sets read 5–8 after the warm-up card was hidden).
    val setLabels = run { var n = 0; sets.map { st -> when { st.b("warm") -> "W"; st.b("sub") -> if (st.s("kind") == "drop") "D" else "M"; else -> { n++; "$n" } } } }
    var warmHidden by androidx.compose.runtime.saveable.rememberSaveable(s.s("sessionId")) { mutableStateOf(false) }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(start = if (wide) 16.dp else 16.dp, end = if (wide) 32.dp else 16.dp, top = 8.dp, bottom = 190.dp)) {
        val warm = s.o("warmup")
        if (ei == 0 && warm != null && !warmHidden) item { WarmupCard(warm) { warmHidden = true } }
        item {
            PiCard(Modifier.widthIn(max = 780.dp), radius = 32.dp, padding = 20.dp) {
                ExerciseHead(s, ex, onInfo = { open(WSheet.Info(ex.s("id"))) }, onProg = { open(WSheet.Progression(ei)) },
                    onSetup = { open(WSheet.Pin(ex.s("id"))) }, onMenu = { open(WSheet.Menu(ei)) })
                if (ex.b("superset")) Badge("Superset", cs.secondaryContainer, cs.onSecondaryContainer)
                LastNoteCard(ex, s.s("dayLabel"))
                s.o("reentry")?.let { r -> ReentryBanner(r) }
                WhyInline(vm, s, ex) { open(WSheet.History(ex.s("id"))) }
                if (injury != null || ex.has("injuryAdjusted") && !ex.isNull("injuryAdjusted")) {
                    Surface(color = cs.tertiaryContainer, contentColor = cs.onTertiaryContainer, shape = RoundedCornerShape(18.dp)) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Icon(PiIcons.Shield, null, modifier = Modifier.size(20.dp))
                                Text("Protecting: ${injury?.s("title") ?: "an injury"}. " + when (injury?.s("impact")) {
                                    "skip" -> "Remaining sets are paused."; "reduce" -> "Remaining work is reduced."; else -> "Check how it feels before each set."
                                }, style = MaterialTheme.typography.bodySmall)
                            }
                            OutlinedButton(onClick = { open(WSheet.Swap(ei)) }) { Text("Swap exercise") }
                        }
                    }
                }
                // The design: skip the remaining warm-ups in one tap, right above the sets (tap ✓ on one to bring it back).
                val warmLeft = sets.filter { it.b("warm") && !it.b("done") && !it.b("skipped") }
                if (warmLeft.isNotEmpty() && !vm.prefs.focus) TextButton(onClick = {
                    warmLeft.forEach { w -> vm.act("skipSet", "ei" to ei, "si" to w.i("i")) }; vm.toast("Warm-ups skipped · tap ✓ on one to bring it back")
                }, modifier = Modifier.align(Alignment.End)) { Text("Skip warm-up →") }
                if (vm.prefs.focus) FocusCard(vm, s, ex, onAdjust = { si -> open(WSheet.Edit(ei, si)) }, onNextLift = { if (ei < s.list("exercises").size - 1) vm.act("select", "ei" to ei + 1) })
                else {
                Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Set", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant, modifier = Modifier.width(32.dp), textAlign = TextAlign.Center)
                    // The design's Target ⇄ Last: the reference column shows the plan or what you did last time.
                    Row(Modifier.weight(1f).padding(start = 8.dp).clip(RoundedCornerShape(8.dp)).clickable { vm.prefs.changeLastMode(!vm.prefs.lastMode) }
                        .semantics { contentDescription = if (vm.prefs.lastMode) "Showing last time. Show targets" else "Showing targets. Show last time"; role = Role.Button },
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(if (vm.prefs.lastMode) "Last" else "Target", style = MaterialTheme.typography.labelMedium, color = cs.primary)
                        Icon(PiIcons.Redo, null, tint = cs.primary, modifier = Modifier.size(12.dp))
                    }
                    Text(if (ex.b("assisted")) "ASSIST" else s.s("unit").uppercase(), style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant, modifier = Modifier.width(if (vm.prefs.grip) 76.dp else 72.dp), textAlign = TextAlign.Center)
                    Text("Reps", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant, modifier = Modifier.width(if (vm.prefs.grip) 62.dp else 58.dp), textAlign = TextAlign.Center)
                    Spacer(Modifier.width(if (vm.prefs.grip) 72.dp else 52.dp))
                }
                sets.forEachIndexed { si, set ->
                    val label = setLabels[si]
                    val showRating = si == lastDoneWork && set.b("done") && set.isNull("rir")
                    SetRow(vm, s, ex, ei, si, set, label, showRating, onEdit = { open(WSheet.Edit(ei, si)) }, onOptions = { open(WSheet.SetOptions(ei, si)) })
                }
                PlateBar(ex, s.s("unit")) { open(WSheet.Plates(ei, ex.i("platesFor"))) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AddRow(vm, ex, Modifier.weight(1f)) { open(WSheet.AddSet(ei)) }
                    if (wide && !s.b("quick") && ei < s.list("exercises").size - 1) OutlinedButton(onClick = { vm.act("select", "ei" to ei + 1) }, modifier = Modifier.height(48.dp)) { Text("Next exercise") }
                }
                }
                if (s.b("quick")) OutlinedButton(onClick = { open(WSheet.AddLift) }, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("Add a lift") }
            }
        }
        item { Spacer(Modifier.height(12.dp)); SessionTotals(s) }
    }
}

@Composable
private fun SetRow(vm: AppViewModel, s: JSONObject, ex: JSONObject, ei: Int, si: Int, set: JSONObject, label: String, showRating: Boolean, onEdit: () -> Unit, onOptions: () -> Unit) {
    val assisted = ex.b("assisted")
    val skipped = set.b("skipped")
    val tag = showsTag(ex, set)
    val isCur = si == ex.i("cur") && !set.b("done")
    val cs = MaterialTheme.colorScheme
    val done = set.b("done"); val warm = set.b("warm")
    val grip = vm.prefs.grip
    val cellH: Dp = if (grip) 58.dp else 44.dp
    val bg by animateColorAsState(when { done && warm -> cs.surfaceContainerHigh; done -> cs.primaryContainer; else -> cs.surfaceContainerLow }, com.pursuitiron.app.ui.theme.Motion.effects(), label = "row")
    val target = set.o("target")
    // The design's reference column: the target as "82.5×6" over its effort, or last time's set (Target ⇄ Last).
    val prev = set.o("prev")
    val fmtW = { v: String -> if (tag && (v.toDoubleOrNull() ?: 0.0) <= 0.0) ex.s("loadTag") else v }
    val tw = target?.s("w")?.takeIf { it.isNotBlank() && it != "—" && it != "null" }
    val sub = when {
        vm.prefs.lastMode -> prev?.takeIf { it.d("w") > 0 || it.s("r").isNotBlank() }?.let { "${fmtW(com.pursuitiron.app.ui.common.fmtNum(it.d("w")))}×${it.s("r")}" } ?: "—"
        tw != null && target.s("reps").isNotBlank() -> "${fmtW(tw)}×${target.s("reps").replace("-", "–")}"
        target != null && target.s("reps").isNotBlank() -> "${target.s("reps").replace("-", "–")} reps"
        else -> set.s("weight").takeIf { it.isNotBlank() }?.let { "${fmtW(it)}×${set.s("reps")}" } ?: ""
    }
    val sub2 = when {
        skipped -> "Skipped · not counted"
        set.b("pr") -> "New best"
        // The engine eased this pending set after a harder-than-planned one (live-autoregulation.js)
        set.s("adjusted") == "load" -> "Load eased"
        set.s("adjusted") == "reps" -> "Reps capped"
        set.s("adjusted") == "backoff" -> "Back-off · 90% of top"
        // The design's top-set rows: the first working set is the top set, the rest are its back-offs
        !warm && !set.b("sub") && ex.o("progression")?.s("style") == "top" ->
            if (set.i("i") == ex.list("sets").firstOrNull { !it.b("warm") && !it.b("sub") }?.i("i")) "Top set" + (target?.takeIf { !it.isNull("rir") }?.let { " · @" + it.s("effort").ifBlank { it.s("rir") + " RIR" } } ?: "") else "Back-off"
        // App.js set-to-set tuning badge: the engine's hint is a direction, not copy
        !done && set.s("hint") == "up" -> "Retuned ↑"
        !done && set.s("hint") == "down" -> "Retuned ↓"
        done && !set.isNull("rir") -> "Rated ${set.i("rir").let { if (it >= 4) "4+" else "$it" }} RIR"
        vm.prefs.lastMode -> if (prev == null) "nothing logged" else "tap to use"
        warm -> "Warm-up"
        set.b("sub") -> if (set.s("kind") == "drop") "Drop · to failure" else "Myo mini"
        set.s("type") == "failure" -> "to failure"
        target != null && !target.isNull("rir") -> "@" + target.s("effort").ifBlank { target.s("rir") + " RIR" }
        set.b("added") -> "Added set"
        else -> ""
    }
    // The set number opens the design's set options.
    val labelCell = @Composable {
        Box(Modifier.width(32.dp).height(40.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onOptions)
            .semantics { contentDescription = "Options for ${if (warm) "warm-up" else "set"} $label"; role = Role.Button }, contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
        }
    }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(bg).alpha(if (skipped) 0.55f else 1f).padding(vertical = 6.dp, horizontal = 4.dp)) {
        if (showRating) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                labelCell()
                Row(Modifier.weight(1f).clip(CircleShape).background(cs.primary).padding(4.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Reps left?", style = MaterialTheme.typography.labelMedium, color = cs.onPrimary, modifier = Modifier.padding(start = 8.dp).weight(1f))
                    (0..4).forEach { r ->
                        Box(Modifier.size(if (grip) 44.dp else 36.dp).clip(CircleShape).background(cs.onPrimary.copy(alpha = 0.16f))
                            .clickable { vm.act("rir", "ei" to ei, "si" to si, "rir" to r) }
                            .semantics { contentDescription = if (r == 4) "4 or more reps left" else "$r reps left"; role = Role.Button },
                            contentAlignment = Alignment.Center) {
                            Text(if (r == 4) "4+" else "$r", style = MaterialTheme.typography.titleSmall, color = cs.onPrimary)
                        }
                    }
                }
            }
        } else Row(verticalAlignment = Alignment.CenterVertically) {
            labelCell()
            val useLast = vm.prefs.lastMode && !done && prev != null && (prev.d("w") > 0 || prev.s("r").isNotBlank())
            Column(Modifier.weight(1f).padding(start = 8.dp).then(if (useLast) Modifier.clip(RoundedCornerShape(10.dp)).clickable {
                    if (prev.d("w") > 0) vm.act("edit", "ei" to ei, "si" to si, "field" to "weight", "value" to com.pursuitiron.app.ui.common.fmtNum(prev.d("w")))
                    if (prev.s("r").isNotBlank()) vm.act("edit", "ei" to ei, "si" to si, "field" to "reps", "value" to prev.s("r"))
                }.semantics { contentDescription = "Last time $sub. Use it for this set"; role = Role.Button } else Modifier)) {
                Text(sub, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (sub2.isNotEmpty()) Text(sub2, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = if (set.b("pr") || set.s("adjusted") == "load" || set.s("adjusted") == "reps") cs.primary else cs.onSurfaceVariant)
            }
            val cellBg = if (done) Color.Transparent else cs.surfaceContainerHigh
            Box(Modifier.width(if (grip) 72.dp else 68.dp).height(cellH).clip(RoundedCornerShape(12.dp)).background(cellBg)
                .clickable(onClick = onEdit).semantics { contentDescription = if (tag) "${if (ex.s("loadTag") == "Band") "Band" else "Bodyweight"}, edit" else if (assisted) "${assistAbs(set.s("weight")).ifBlank { "No" }} ${s.s("unit")} of assistance, edit" else "Weight ${set.s("weight").ifBlank { "not set" }}, edit" },
                contentAlignment = Alignment.Center) {
                Text(if (tag) ex.s("loadTag") else if (assisted) assistShown(set.s("weight")) else set.s("weight").ifBlank { "—" }, style = if (grip) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.width(4.dp))
            Box(Modifier.width(if (grip) 58.dp else 54.dp).height(cellH).clip(RoundedCornerShape(12.dp)).background(cellBg)
                .clickable(onClick = onEdit).semantics { contentDescription = "Reps ${set.s("reps").ifBlank { "not set" }}, edit" },
                contentAlignment = Alignment.Center) {
                Text(set.s("reps").ifBlank { "—" }, style = if (grip) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.width(4.dp))
            // Animate between the two real radii: a spring from a 999 dp "circle" overshoots past zero, and a
            // negative corner size throws while drawing.
            val r by animateDpAsState(if (done) 14.dp else cellH / 2, Motion.spring(), label = "check")
            val checkW = if (grip) 68.dp else 44.dp
            Box(
                Modifier.width(checkW).height(cellH).clip(RoundedCornerShape(r.coerceIn(0.dp, cellH / 2)))
                    .background(if (done) cs.primary else Color.Transparent)
                    .then(if (done) Modifier else Modifier.background(Color.Transparent))
                    .clickable { vm.act("toggle", "ei" to ei, "si" to si) }
                    .semantics { role = Role.Checkbox; stateDescription = if (done) "Logged" else "Not logged"; contentDescription = "Set $label: ${set.s("weight").ifBlank { "no weight" }} ${s.s("unit")} for ${set.s("reps")} reps" },
                contentAlignment = Alignment.Center,
            ) {
                if (!done) Canvas(Modifier.fillMaxSize()) { drawRoundRect(cs.outlineVariant, cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.minDimension / 2), style = Stroke(1.5.dp.toPx())) }
                Icon(PiIcons.Check, null, tint = if (done) cs.onPrimary else cs.onSurfaceVariant.copy(alpha = 0.55f), modifier = Modifier.size(22.dp))
            }
        }
        // The design: the current set's plates, right under its weight.
        val pl = ex.o("plates")
        if (!showRating && isCur && !skipped && !tag && pl != null && ex.i("platesFor") == si && (set.s("weight").toDoubleOrNull() ?: 0.0) > 0)
            Text(pl.s("text"), style = MaterialTheme.typography.labelMedium, color = cs.primary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(start = 40.dp, end = if (grip) 72.dp else 48.dp, top = 2.dp), textAlign = TextAlign.End)
        if (!showRating && vm.prefs.steppers && !done && !skipped) SetSteppers(vm, s, ex, ei, si, set)
    }
}

@Composable
private fun RestCard(vm: AppViewModel, s: JSONObject, endAt: Long) {
    val cs = MaterialTheme.colorScheme
    val r = s.o("rest")
    val totalSec = r.i("seconds").coerceAtLeast(1)
    val left = ((endAt - vm.now) / 1000).coerceAtLeast(0)
    val frac = 1f - left.toFloat() / totalSec
    Surface(color = cs.primary, contentColor = cs.onPrimary, shape = RoundedCornerShape(32.dp), shadowElevation = 10.dp,
        modifier = Modifier.semantics { contentDescription = "Rest, ${mmss(left)} left" }) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Canvas(Modifier.size(44.dp)) {
                val st = Stroke(5.dp.toPx(), cap = StrokeCap.Round)
                val inset = 3.dp.toPx()
                drawArc(cs.onPrimary.copy(alpha = 0.25f), 0f, 360f, false, Offset(inset, inset), Size(size.width - 2 * inset, size.height - 2 * inset), style = st)
                drawArc(cs.onPrimary, -90f, 360f * frac.coerceIn(0f, 1f), false, Offset(inset, inset), Size(size.width - 2 * inset, size.height - 2 * inset), style = st)
            }
            Column(Modifier.weight(1f)) {
                Text(when (r.s("kind")) { "mini" -> "Myo rest"; "warm" -> "Warm-up rest"; else -> "Rest" }, style = MaterialTheme.typography.labelMedium)
                Text(mmss(left), style = MaterialTheme.typography.headlineMedium)
            }
            listOf(-15 to "−15", 15 to "+15").forEach { (d, t) ->
                Box(Modifier.height(44.dp).clip(RoundedCornerShape(14.dp)).background(cs.onPrimary).clickable { vm.act("restAdjust", "delta" to d) }
                    .padding(horizontal = 10.dp).semantics { contentDescription = if (d < 0) "15 seconds less" else "15 seconds more"; role = Role.Button },
                    contentAlignment = Alignment.Center) { Text(t, style = MaterialTheme.typography.labelLarge, color = cs.primary) }
            }
            OutlinedButton(onClick = { vm.act("restDone") }, colors = ButtonDefaults.outlinedButtonColors(contentColor = cs.onPrimary),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, cs.onPrimary)) { Text("Skip") }
        }
    }
}

@Composable
private fun Dock(vm: AppViewModel, s: JSONObject, ei: Int, exs: List<JSONObject>, modifier: Modifier, onFinish: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val h = if (vm.prefs.grip) 64.dp else 56.dp
    Row(modifier.fillMaxWidth().background(cs.surfaceContainer).navigationBarsPadding().padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val nxt = exs.getOrNull(ei + 1)
        if (nxt != null) {
            Row(Modifier.weight(1f).height(h).clip(RoundedCornerShape(18.dp)).background(cs.secondaryContainer).clickable { vm.act("select", "ei" to ei + 1) }
                .semantics { contentDescription = "Next exercise: ${nxt.s("name")}"; role = Role.Button }.padding(start = 16.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Kicker("Up next", cs.onSecondaryContainer)
                    Text(nxt.s("name"), style = MaterialTheme.typography.titleSmall, color = cs.onSecondaryContainer, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Icon(PiIcons.ChevronRight, null, tint = cs.onSecondaryContainer)
            }
        } else Column(Modifier.weight(1f).padding(start = 8.dp)) {
            Kicker("Last exercise")
            Text("${s.i("doneSets")} of ${s.i("totalSets")} sets logged", style = MaterialTheme.typography.titleSmall)
        }
        Button(onClick = onFinish, modifier = Modifier.height(h), contentPadding = PaddingValues(horizontal = 26.dp)) { Text("Finish", style = MaterialTheme.typography.titleSmall) }
    }
}

/* ---------------------------------------------------------------- sheets */

@Composable
private fun SetEditor(vm: AppViewModel, s: JSONObject, ei: Int, si: Int, onClose: () -> Unit) {
    val ex = s.list("exercises").getOrNull(ei); val set = ex?.list("sets")?.getOrNull(si)
    if (ex == null || set == null) { onClose(); return }
    val cs = MaterialTheme.colorScheme
    val assisted = ex.b("assisted")
    val shownW = { v: String -> if (assisted) assistAbs(v) else v }
    var w by remember(si, ei) { mutableStateOf(shownW(set.s("weight"))) }
    var r by remember(si, ei) { mutableStateOf(set.s("reps")) }
    LaunchedEffect(set.s("weight")) { w = shownW(set.s("weight")) }
    LaunchedEffect(set.s("reps")) { r = set.s("reps") }
    var plates by remember { mutableStateOf(false) }
    val bar = barFor(ex.strings("equip"))
    if (plates) { ToolsSheet(vm, "plates", w, ex.s("bar").ifBlank { bar ?: "barbell" }, onUse = { v -> w = v; vm.act("edit", "ei" to ei, "si" to si, "field" to "weight", "value" to v) }) { plates = false }; return }
    ModalBottomSheet(onDismissRequest = onClose) {
        Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()).imePadding().padding(horizontal = 20.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Kicker(ex.s("name"))
            Text(when { set.b("done") -> "Edit logged set"; set.b("warm") -> "Warm-up"; else -> "Set ${si + 1}" }, style = MaterialTheme.typography.headlineSmall)
            if (!set.b("done") && !set.b("sub")) com.pursuitiron.app.ui.common.Segmented(
                listOf("standard" to "Standard", "warmup" to "Warm-up", "failure" to "To failure"),
                if (set.b("warm")) "warmup" else set.s("type", "standard"), { k -> vm.act("setType", "ei" to ei, "si" to si, "kind" to k) }, height = 40.dp)
            set.o("target")?.let { t -> Text("Target: ${t.s("reps")} reps" + (if (!t.isNull("rir")) " at ${t.s("effort").ifBlank { t.s("rir") + " RIR" }}" else ""), color = cs.onSurfaceVariant) }
            if (assisted) {
                // App.js: help is a negative load. Typed and shown as the amount of help; minus adds help, plus takes it off.
                Text("Assisted: less help is progress. The help comes off once every set tops the range.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                Stepper("Assistance (${s.s("unit")})", w, KeyboardType.Decimal, onText = { w = com.pursuitiron.app.ui.common.decimalText(it) },
                    onCommit = { vm.act("edit", "ei" to ei, "si" to si, "field" to "weight", "value" to assistStored(w)) },
                    onMinus = { vm.act("bumpWeight", "ei" to ei, "si" to si, "dir" to -1) }, onPlus = { vm.act("bumpWeight", "ei" to ei, "si" to si, "dir" to 1) },
                    minusLabel = "More assistance", plusLabel = "Less assistance")
            } else Stepper("Weight (${s.s("unit")})", w, KeyboardType.Decimal, onText = { w = it }, onCommit = { vm.act("edit", "ei" to ei, "si" to si, "field" to "weight", "value" to w) },
                onMinus = { vm.act("bumpWeight", "ei" to ei, "si" to si, "dir" to -1) }, onPlus = { vm.act("bumpWeight", "ei" to ei, "si" to si, "dir" to 1) })
            Stepper("Reps", r, KeyboardType.Number, onText = { r = it }, onCommit = { vm.act("edit", "ei" to ei, "si" to si, "field" to "reps", "value" to r) },
                onMinus = { vm.act("bumpReps", "ei" to ei, "si" to si, "delta" to -1) }, onPlus = { vm.act("bumpReps", "ei" to ei, "si" to si, "delta" to 1) })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (set.b("added")) TextButton(onClick = { vm.act("removeSet", "ei" to ei, "si" to si); onClose() }) { Text("Remove set") }
                if (bar != null && !assisted) TextButton(onClick = { plates = true }) { Icon(PiIcons.Plates, null, modifier = Modifier.size(18.dp)); Text("  Plates") }
                Spacer(Modifier.weight(1f))
                Button(onClick = {
                    if (w != shownW(set.s("weight"))) vm.act("edit", "ei" to ei, "si" to si, "field" to "weight", "value" to if (assisted) assistStored(w) else w)
                    if (r != set.s("reps")) vm.act("edit", "ei" to ei, "si" to si, "field" to "reps", "value" to r)
                    onClose()
                }) { Text("Done") }
            }
        }
    }
}

@Composable
private fun Stepper(label: String, value: String, kb: KeyboardType, onText: (String) -> Unit, onCommit: () -> Unit, onMinus: () -> Unit, onPlus: () -> Unit,
                    minusLabel: String = "Less $label", plusLabel: String = "More $label") {
    val cs = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(cs.surfaceContainerHigh).clickable { onMinus() }
            .semantics { contentDescription = minusLabel; role = Role.Button }, contentAlignment = Alignment.Center) { Icon(PiIcons.Minus, null) }
        OutlinedTextField(value = value, onValueChange = onText, label = { Text(label) }, singleLine = true, modifier = Modifier.weight(1f),
            keyboardOptions = KeyboardOptions(keyboardType = kb, imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { onCommit() }),
            textStyle = MaterialTheme.typography.titleLarge.copy(textAlign = TextAlign.Center))
        Box(Modifier.size(52.dp).clip(CircleShape).background(cs.primary).clickable { onPlus() }
            .semantics { contentDescription = plusLabel; role = Role.Button }, contentAlignment = Alignment.Center) { Icon(PiIcons.Plus, null, tint = cs.onPrimary) }
    }
}

@Composable
private fun ExerciseMenu(vm: AppViewModel, s: JSONObject, ei: Int, onPick: (WSheet?) -> Unit, onClose: () -> Unit) {
    val ex = s.list("exercises").getOrNull(ei) ?: run { onClose(); return }
    val count = s.list("exercises").size
    val cs = MaterialTheme.colorScheme
    ModalBottomSheet(onDismissRequest = onClose, sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(horizontal = 12.dp).padding(bottom = 28.dp)) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Kicker("Exercise ${ei + 1} of $count")
                Text(ex.s("name"), style = MaterialTheme.typography.headlineSmall)
            }
            Column(Modifier.clip(RoundedCornerShape(22.dp)), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                ex.optJSONObject("plateau")?.let { pl -> MenuRow(PiIcons.Trend, "Plateau options", "No PR in ${pl.i("since")} sessions on this day", cs.tertiaryContainer, cs.onTertiaryContainer) { onPick(WSheet.Plateau(ei)) } }
                MenuRow(PiIcons.Swap, "Swap exercise", "Alternatives at your gym", cs.secondaryContainer, cs.onSecondaryContainer) { onPick(WSheet.Swap(ei)) }
                // B903: the working-set count for today; after saving, the finish page asks whether the program keeps it.
                run {
                    val work = ex.list("sets").filter { !it.b("warm") && !it.b("sub") && !it.b("skipped") }
                    val n = work.size; val minN = work.count { it.b("done") }.coerceAtLeast(1)
                    Row(Modifier.fillMaxWidth().background(cs.surfaceContainerHigh).padding(start = 14.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(cs.secondaryContainer), contentAlignment = Alignment.Center) { Icon(PiIcons.List, null, tint = cs.onSecondaryContainer, modifier = Modifier.size(20.dp)) }
                        Column(Modifier.weight(1f)) {
                            Text("Working sets", style = MaterialTheme.typography.titleSmall)
                            Text("Logged sets stay. Keep the new count after saving, or just today", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                        }
                        Row(Modifier.clip(CircleShape).background(cs.surface).padding(3.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(36.dp).clip(CircleShape).clickable(enabled = n > minN) { vm.act("setCount", "ei" to ei, "count" to n - 1) }
                                .semantics { contentDescription = "One fewer working set"; role = Role.Button }, contentAlignment = Alignment.Center) { Text("−", fontSize = 18.sp, color = if (n > minN) cs.onSurface else cs.onSurfaceVariant) }
                            Text("$n", style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center, modifier = Modifier.width(28.dp))
                            Box(Modifier.size(36.dp).clip(CircleShape).clickable(enabled = n < 20) { vm.act("setCount", "ei" to ei, "count" to n + 1) }
                                .semantics { contentDescription = "One more working set"; role = Role.Button }, contentAlignment = Alignment.Center) { Text("+", fontSize = 18.sp) }
                        }
                    }
                }
                MenuRow(PiIcons.Shield, "Something hurt", "Flag it and the plan protects that area", cs.tertiaryContainer, cs.onTertiaryContainer) { onPick(WSheet.Hurt(ei)) }
                MenuRow(PiIcons.List, "Same weight for all sets", "Set every unlogged set to the average", cs.secondaryContainer, cs.onSecondaryContainer) { vm.act("level", "ei" to ei); onPick(null) }
                if (count > 1) MenuRow(PiIcons.Swap, if (ex.b("superset")) "Superset: on" else "Superset", "Alternate sets with another lift", cs.secondaryContainer, cs.onSecondaryContainer) { vm.act("select", "ei" to ei); onPick(WSheet.Superset) }
                MenuRow(PiIcons.Timer, "Rest, goal & setup", listOf(if (ex.i("restCustom") > 0) "Rest ${restLabel(ex.i("restCustom"))}" else "Rest for this lift", if (ex.d("goal") > 0) "goal ${com.pursuitiron.app.ui.common.fmtNum(ex.d("goal"))}" else "a goal weight", "machine step").joinToString(", "), cs.secondaryContainer, cs.onSecondaryContainer) { onPick(WSheet.LiftSettings(ei)) }
                val pinned = (ex.list("setup").filter { it.s("value").isNotBlank() }.map { "${it.s("label")} ${it.s("value")}" } + listOf(ex.s("pinNote")).filter { it.isNotBlank() }).joinToString(" — ")
                MenuRow(PiIcons.Pin, if (pinned.isBlank()) "Pin a note" else "Edit pinned note", pinned.ifBlank { "Setup per machine and a cue you’ll see every time" }, cs.secondaryContainer, cs.onSecondaryContainer) { onPick(WSheet.Pin(ex.s("id"))) }
                MenuRow(PiIcons.Close, "Skip exercise", "Drop it from today's workout", cs.tertiaryContainer, cs.onTertiaryContainer) { vm.act("skipExercise", "ei" to ei); vm.toast("Skipped ${ex.s("name")}"); onPick(null) }
                MenuRow(PiIcons.List, "Note for today", if (ex.s("note").isBlank()) "Only this workout; pinned notes stay with the lift" else ex.s("note"), cs.secondaryContainer, cs.onSecondaryContainer) { onPick(WSheet.Note(ei)) }
                if (ei > 0) MenuRow(PiIcons.ChevronLeft, "Move earlier", "Logged sets move with it", cs.secondaryContainer, cs.onSecondaryContainer) { vm.act("move", "from" to ei, "to" to ei - 1); onPick(null) }
                if (ei < count - 1) MenuRow(PiIcons.ChevronRight, "Move later", "Logged sets move with it", cs.secondaryContainer, cs.onSecondaryContainer) { vm.act("move", "from" to ei, "to" to ei + 1); onPick(null) }
            }
        }
    }
}

@Composable
fun MenuRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, sub: String, bg: Color, fg: Color,
            trailing: androidx.compose.ui.graphics.vector.ImageVector? = null, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().background(cs.surfaceContainerHigh).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(bg), contentAlignment = Alignment.Center) { Icon(icon, null, tint = fg, modifier = Modifier.size(20.dp)) }
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        trailing?.let { Icon(it, null, tint = cs.onSurfaceVariant, modifier = Modifier.size(20.dp)) }
    }
}

@Composable
private fun SwapSheet(vm: AppViewModel, ei: Int, onClose: () -> Unit) {
    var opts by remember { mutableStateOf<List<JSONObject>?>(null) }
    var scope by remember { mutableStateOf("session") }
    val quick = vm.session?.b("quick") == true
    LaunchedEffect(ei) { opts = vm.swapOptions(ei) }
    ModalBottomSheet(onDismissRequest = onClose) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Swap exercise", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 4.dp))
            Text("Same muscle, ranked for this slot, from the gym you're in. Banned lifts are hidden.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
            if (!quick) com.pursuitiron.app.ui.common.Segmented(listOf("session" to "Today only", "program" to "In the program too"), scope, { scope = it }, height = 40.dp)
            val o = opts
            when {
                o == null -> Text("Finding alternatives…", modifier = Modifier.padding(4.dp))
                o.isEmpty() -> Text("No alternatives with your equipment.", modifier = Modifier.padding(4.dp))
                else -> LazyColumn(Modifier.clip(RoundedCornerShape(22.dp)).height((o.size.coerceAtMost(7) * 66).dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    items(o) { x ->
                        Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerHigh).clickable { if (scope == "program" && !quick) vm.swapInProgram(ei, x.s("id")) else vm.act("swap", "ei" to ei, "newId" to x.s("id")); vm.toast("Swapped to ${x.s("name")}" + if (scope == "program" && !quick) " in the program" else ""); onClose() }
                            .padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(x.s("name"), style = MaterialTheme.typography.titleSmall)
                                Text((x.optJSONArray("equip")?.let { a -> (0 until a.length()).joinToString(", ") { a.optString(it) } } ?: "").replaceFirstChar { it.uppercase() },
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(PiIcons.Swap, null, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WhySheet(vm: AppViewModel, ei: Int, onClose: () -> Unit) {
    var x by remember { mutableStateOf<JSONObject?>(null) }
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(ei) { x = vm.explain(ei); loaded = true }
    val cs = MaterialTheme.colorScheme
    ModalBottomSheet(onDismissRequest = onClose) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val e = x
            if (e == null) { Text(if (loaded) "No explanation is available for this lift." else "Asking the engine…"); return@Column }
            val lift = e.o("lift"); val ctx = e.o("context"); val read = e.o("read"); val dec = e.o("decided"); val load = e.o("load")
            Kicker(lift.s("name"))
            Text(load?.let { l -> if (l.isNull("weight")) "No weight yet" else "${l.s("weight")} ${vm.session?.s("unit").orEmpty()}" } ?: "This set", style = MaterialTheme.typography.headlineMedium)
            load?.s("reason")?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
            vm.session?.o("reentry")?.let { r ->
                Text("Re-entry after ${r.i("daysOff")} days off: ${Math.round(r.d("factor") * 100)}% of the planned load, ${r.i("sets")} working sets. Strength comes back fast; this keeps the first session from leaving you wrecked for the next one.",
                    style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            }
            val range = dec?.optJSONArray("repRange")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (range != null && range.length() == 2) Badge("${range.optInt(0)}–${range.optInt(1)} reps", cs.surfaceContainerHigh, cs.onSurface)
                dec?.s("targetRIR")?.takeIf { it.isNotBlank() }?.let { Badge("$it RIR", cs.surfaceContainerHigh, cs.onSurface) }
                dec?.s("style")?.takeIf { it.isNotBlank() }?.let { Badge(styleLabel(it), cs.primaryContainer, cs.onPrimaryContainer) }
            }
            dec?.s("styleWhy")?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant) }
            PiCard(color = cs.surfaceContainerHigh, radius = 20.dp, padding = 14.dp) {
                Text("What the engine read", style = MaterialTheme.typography.titleSmall)
                Text(listOfNotNull(
                    "Week ${ctx.i("weekIndex")} of ${ctx.i("weeksTotal")}" + (ctx.s("phase").takeIf { it.isNotBlank() }?.let { " · " + it.replace('_', ' ') } ?: ""),
                    if (ctx.b("deload")) "Deload week" else null,
                    read?.let { r -> if (!r.isNull("e1rm")) "Estimated 1RM ${r.s("e1rm")}" else null },
                    read?.let { r -> r.i("stallSessions").takeIf { it > 0 }?.let { n -> "Stalled $n sessions" } },
                    read?.let { r -> r.i("historyEntriesForThisDay").let { n -> if (n == 0) "First time on this day" else "$n earlier sessions on this day" } },
                ).joinToString("\n"), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

private fun styleLabel(s: String) = when (s) {
    "double" -> "Double progression"; "ddp" -> "Dynamic double progression"; "linear" -> "Linear"; "myo" -> "Myo-reps"; "wave" -> "Wave"
    else -> s.replace('_', ' ').replaceFirstChar { it.uppercase() }
}

@Composable
internal fun HurtSheet(vm: AppViewModel, ei: Int, onClose: () -> Unit) {
    val opts = vm.setup
    val areas = opts.list("injuryAreas"); val impacts = opts.list("injuryImpacts")
    var area by remember { mutableStateOf<String?>(null) }
    var impact by remember { mutableStateOf("reduce") }
    val cs = MaterialTheme.colorScheme
    ModalBottomSheet(onDismissRequest = onClose) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Something hurt?", style = MaterialTheme.typography.headlineSmall)
            Kicker("Where")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                areas.forEach { a -> MorphChip(a.s("label"), area == a.s("id"), { area = a.s("id") }, height = 44.dp) }
            }
            Kicker("What should happen")
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                impacts.forEach { im ->
                    val on = impact == im.s("id")
                    Surface(onClick = { impact = im.s("id") }, color = if (on) cs.primary else cs.surfaceContainerHigh, contentColor = if (on) cs.onPrimary else cs.onSurface,
                        shape = RoundedCornerShape(if (on) 18.dp else 22.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Text(im.s("label"), style = MaterialTheme.typography.titleSmall)
                            Text(im.s("detail"), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            Text("This adjusts training load. It isn't medical advice; pain that's sharp, swelling or lasting more than a few days is worth getting checked.",
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            Button(onClick = { area?.let { vm.act("injury", "region" to it, "impact" to impact) }; vm.toast("Logged. The plan protects that area."); onClose() },
                enabled = area != null, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Apply") }
        }
    }
}

@Composable
private fun NoteSheet(vm: AppViewModel, s: JSONObject, ei: Int, onClose: () -> Unit) {
    val ex = s.list("exercises").getOrNull(ei) ?: run { onClose(); return }
    var text by remember { mutableStateOf(ex.s("note")) }
    var repeat by remember { mutableStateOf("once") }
    ModalBottomSheet(onDismissRequest = onClose) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Note for ${ex.s("name")}", style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth(), minLines = 2, placeholder = { Text("Seat 4, close grip…") })
            Kicker("Show it again")
            com.pursuitiron.app.ui.common.Segmented(listOf("none" to "Just today", "once" to "Next time", "always" to "Every time"), repeat, { repeat = it })
            Button(onClick = { vm.act("note", "ei" to ei, "text" to text, "repeat" to repeat); onClose() }, modifier = Modifier.align(Alignment.End)) { Text("Save") }
        }
    }
}

/* The design's re-entry chip on the exercise card. */
@Composable
private fun ReentryBanner(r: JSONObject) {
    val cs = MaterialTheme.colorScheme
    Surface(color = cs.primaryContainer, contentColor = cs.onPrimaryContainer, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(PiIcons.Redo, null, Modifier.size(20.dp))
            Text("Re-entry session. ${r.i("sets")} working sets per lift at ${Math.round(r.d("factor") * 100)}% of the plan. Your full plan resumes next session.",
                style = MaterialTheme.typography.bodySmall)
        }
    }
}

/* Assisted lifts store help as a negative load (App.js). Shown as the help with a minus sign, typed as the help. */
private fun assistAbs(v: String): String = v.trim().removePrefix("-").removePrefix("−")
private fun assistShown(v: String): String = assistAbs(v).let { a -> if (a.isBlank()) "—" else if (a.toDoubleOrNull() == 0.0) "0" else "−$a" }
private fun assistStored(typed: String): String = typed.trim().let { t -> if (t.isBlank() || t.toDoubleOrNull() == 0.0) t else "-$t" }

/* App.js warmupRoutine as the design's "Warm-up & mobility" card: a checklist for the day's first muscles,
   shown on the first lift until a set is logged. Ticks are only for you; nothing is recorded. */
@Composable
private fun WarmupCard(w: JSONObject, onDone: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val moves = w.strings("moves")
    val ticked = remember(moves) { androidx.compose.runtime.mutableStateListOf<Int>() }
    PiCard(Modifier.widthIn(max = 780.dp).padding(bottom = 12.dp), color = cs.primaryContainer, radius = 28.dp, padding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(PiIcons.Flame, null, tint = cs.onPrimaryContainer)
            Column(Modifier.weight(1f)) {
                Text("Warm-up & mobility", style = MaterialTheme.typography.titleMedium, color = cs.onPrimaryContainer)
                Text("For ${w.strings("parts").joinToString(", ") { it.lowercase() }} · ${ticked.size} of ${moves.size}", style = MaterialTheme.typography.bodySmall, color = cs.onPrimaryContainer)
            }
            TextButton(onClick = onDone) { Text("Hide") }
        }
        Column(Modifier.clip(RoundedCornerShape(20.dp)), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            moves.forEachIndexed { k, m ->
                val on = k in ticked
                Row(Modifier.fillMaxWidth().background(cs.surface).clickable { if (on) ticked.remove(k) else ticked.add(k) }
                    .semantics(mergeDescendants = true) { role = Role.Checkbox; stateDescription = if (on) "Done" else "Not done" }
                    .padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(24.dp).clip(CircleShape).background(if (on) cs.primary else cs.surface).border(2.dp, if (on) cs.primary else cs.outlineVariant, CircleShape), contentAlignment = Alignment.Center) {
                        if (on) Icon(PiIcons.Check, null, Modifier.size(14.dp), tint = cs.onPrimary)
                    }
                    Text(m, style = MaterialTheme.typography.bodyMedium, color = if (on) cs.onSurfaceVariant else cs.onSurface)
                }
            }
        }
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth().height(48.dp), colors = ButtonDefaults.buttonColors(containerColor = cs.onPrimaryContainer, contentColor = cs.primaryContainer)) {
            Text("Warmed up — let's train")
        }
    }
}
