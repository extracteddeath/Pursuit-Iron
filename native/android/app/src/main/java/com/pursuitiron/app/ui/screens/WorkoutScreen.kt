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
import com.pursuitiron.app.AppViewModel
import com.pursuitiron.app.ui.LocalWide
import com.pursuitiron.app.ui.common.Badge
import com.pursuitiron.app.ui.common.Kicker
import com.pursuitiron.app.ui.common.MorphChip
import com.pursuitiron.app.ui.common.PiCard
import com.pursuitiron.app.ui.common.PiIcons
import com.pursuitiron.app.ui.common.ProgressBar
import com.pursuitiron.app.ui.common.b
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
    data class Why(val ei: Int) : WSheet
    data class Hurt(val ei: Int) : WSheet
    data class Info(val id: String) : WSheet
    data class Note(val ei: Int) : WSheet
    data class Rest(val ei: Int) : WSheet
    data class Progression(val ei: Int) : WSheet
    data class LiftSettings(val ei: Int) : WSheet
    data object Superset : WSheet
    data object SessionNote : WSheet
    data object SessionMenu : WSheet
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
    val tryFinish = { if (done < total) confirmFinish = true else sheet = WSheet.Finish }

    Box(Modifier.fillMaxSize().background(cs.surface)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Header(vm, s, wide, onDiscard = { confirmDiscard = true }, onFinish = tryFinish, onMenu = { sheet = WSheet.SessionMenu })
            ProgressBar(if (total > 0) done.toFloat() / total else 0f, Modifier.padding(horizontal = 16.dp))
            if (wide) {
                Row(Modifier.fillMaxSize()) {
                    LazyColumn(Modifier.width(300.dp).fillMaxHeight(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        itemsIndexed(exs) { k, e ->
                            MorphChip(e.s("name"), k == ei, { vm.act("select", "ei" to k) }, Modifier.fillMaxWidth(), trailing = workCount(e), done = e.b("done"), height = 56.dp)
                        }
                    }
                    ExercisePane(vm, s, ex, wide, Modifier.weight(1f)) { sheet = it }
                }
            } else {
                val rowState = rememberLazyListState()
                LaunchedEffect(ei) { rowState.animateScrollToItem(ei) }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)) {
                    LazyRow(Modifier.weight(1f), state = rowState, contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        itemsIndexed(exs) { k, e ->
                            MorphChip(shortName(e.s("name")), k == ei, { vm.act("select", "ei" to k) }, trailing = workCount(e), done = e.b("done"))
                        }
                    }
                }
                ExercisePane(vm, s, ex, wide, Modifier.weight(1f)) { sheet = it }
            }
        }

        val restEnd = vm.restEndsAt()
        AnimatedVisibility(
            visible = restEnd != null,
            enter = slideInVertically(Motion.spring()) { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(if (wide) Alignment.BottomEnd else Alignment.BottomCenter)
                .padding(start = 12.dp, end = if (wide) 24.dp else 12.dp, bottom = if (wide) 24.dp else 96.dp).navigationBarsPadding()
                .then(if (wide) Modifier.width(420.dp) else Modifier.fillMaxWidth()),
        ) { if (restEnd != null) RestCard(vm, s, restEnd) }

        if (!wide) Dock(vm, s, ei, exs, Modifier.align(Alignment.BottomCenter), tryFinish)
    }

    when (val sh = sheet) {
        is WSheet.Edit -> SetEditor(vm, s, sh.ei, sh.si) { sheet = null }
        is WSheet.Menu -> ExerciseMenu(vm, s, sh.ei, onPick = { sheet = it }) { sheet = null }
        is WSheet.Swap -> SwapSheet(vm, sh.ei) { sheet = null }
        is WSheet.Why -> WhySheet(vm, sh.ei) { sheet = null }
        is WSheet.Hurt -> HurtSheet(vm, sh.ei) { sheet = null }
        is WSheet.Info -> ExerciseSheet(vm, sh.id) { sheet = null }
        is WSheet.Note -> NoteSheet(vm, s, sh.ei) { sheet = null }
        is WSheet.Rest -> exs.getOrNull(sh.ei)?.let { RestTimeSheet(vm, it) { sheet = null } } ?: run { sheet = null }
        is WSheet.Progression -> exs.getOrNull(sh.ei)?.let { ProgressionSheet(vm, s, it) { sheet = null } } ?: run { sheet = null }
        is WSheet.LiftSettings -> exs.getOrNull(sh.ei)?.let { LiftSettingsSheet(vm, s, it) { sheet = null } } ?: run { sheet = null }
        WSheet.Superset -> SupersetSheet(vm, s) { sheet = null }
        WSheet.SessionNote -> SessionNoteSheet(vm, s) { sheet = null }
        WSheet.SessionMenu -> SessionMenu(vm, s, onNote = { sheet = WSheet.SessionNote }, onAdd = { sheet = WSheet.AddLift }, onSuperset = { sheet = WSheet.Superset }) { sheet = null }
        WSheet.AddLift -> AddLiftSheet(vm) { sheet = null }
        WSheet.Finish -> FinishSheet(vm) { sheet = null }
        null -> if (!s.b("readinessDone")) ReadinessCheckSheet(vm)
    }
    if (confirmDiscard) AlertDialog(
        onDismissRequest = { confirmDiscard = false },
        title = { Text("Discard this workout?") },
        text = { Text(if (done > 0) "You've logged $done working ${if (done == 1) "set" else "sets"}. They won't be saved, and nothing will progress from them." else "Nothing has been logged yet.") },
        confirmButton = { TextButton(onClick = { confirmDiscard = false; vm.discard() }) { Text("Discard") } },
        dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep going") } },
    )
    if (confirmFinish) AlertDialog(
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
    var workNo = 0
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(start = if (wide) 16.dp else 16.dp, end = if (wide) 32.dp else 16.dp, top = 8.dp, bottom = 190.dp)) {
        item {
            PiCard(Modifier.widthIn(max = 780.dp), radius = 32.dp, padding = 20.dp) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f).clickable { open(WSheet.Info(ex.s("id"))) }) {
                        Text(ex.s("name"), style = MaterialTheme.typography.headlineLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                            if (ex.b("primary")) Badge("Primary")
                            if (ex.b("superset")) Badge("Superset", cs.secondaryContainer, cs.onSecondaryContainer)
                            if (ex.d("goal") > 0) Badge("Goal ${com.pursuitiron.app.ui.common.fmtNum(ex.d("goal"))} ${s.s("unit")}", cs.primaryContainer, cs.onPrimaryContainer)
                            if (ex.o("progression")?.b("changed") == true) Badge("Progression changed", cs.secondaryContainer, cs.onSecondaryContainer)
                            Text(ex.s("part").replace('_', ' ').replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                        }
                    }
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(cs.secondaryContainer).clickable { open(WSheet.Menu(ei)) }
                        .semantics { contentDescription = "Exercise actions"; role = Role.Button }, contentAlignment = Alignment.Center) {
                        Icon(PiIcons.More, null, tint = cs.onSecondaryContainer)
                    }
                }
                Surface(onClick = { open(WSheet.Why(ei)) }, color = cs.surfaceContainerHigh, shape = CircleShape) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(PiIcons.Help, null, modifier = Modifier.size(16.dp))
                        val firstLoad = sets.firstOrNull { !it.b("warm") && it.s("weight").isNotBlank() }?.s("weight")
                        Text(if (firstLoad != null) "Why $firstLoad ${s.s("unit")}?" else "No suggestion yet: why?", style = MaterialTheme.typography.labelLarge)
                    }
                }
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
                if (ex.s("note").isNotBlank()) Text("Note: ${ex.s("note")}", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                else if (ex.s("lastNote").isNotBlank()) Text("Last time: ${ex.s("lastNote")}", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                val setupLine = ex.list("setup").filter { it.s("value").isNotBlank() }.joinToString(" · ") { "${it.s("label")} ${it.s("value")}" }
                if (setupLine.isNotBlank()) Text("Setup: $setupLine", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                val cues = ex.strings("cues")
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (cues.isNotEmpty()) TextButton(onClick = { cuesOpen = !cuesOpen }) { Text(if (cuesOpen) "Hide cues" else "Form cues") }
                    TextButton(onClick = { runCatching { uri.openUri("https://www.youtube.com/results?search_query=" + java.net.URLEncoder.encode("${ex.s("name")} proper form", "UTF-8")) } }) { Text("Watch a demo") }
                }
                if (cuesOpen) cues.forEach { c -> Text("•  $c", style = MaterialTheme.typography.bodyMedium) }

                Row(Modifier.padding(horizontal = 4.dp)) {
                    Text("Set", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant, modifier = Modifier.width(32.dp), textAlign = TextAlign.Center)
                    Text("Target", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant, modifier = Modifier.weight(1f).padding(start = 8.dp))
                    Text(s.s("unit").uppercase(), style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant, modifier = Modifier.width(if (vm.prefs.grip) 76.dp else 72.dp), textAlign = TextAlign.Center)
                    Text("Reps", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant, modifier = Modifier.width(if (vm.prefs.grip) 62.dp else 58.dp), textAlign = TextAlign.Center)
                    Spacer(Modifier.width(if (vm.prefs.grip) 72.dp else 52.dp))
                }
                sets.forEachIndexed { si, set ->
                    val label = when { set.b("warm") -> "W"; set.b("sub") -> if (set.s("kind") == "drop") "D" else "M"; else -> { workNo++; "$workNo" } }
                    val showRating = si == lastDoneWork && set.b("done") && set.isNull("rir")
                    SetRow(vm, s, ei, si, set, label, showRating, onEdit = { open(WSheet.Edit(ei, si)) })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { vm.act("addSet", "ei" to ei) }, modifier = Modifier.weight(1f).height(48.dp)) {
                        Icon(PiIcons.Plus, null, modifier = Modifier.size(18.dp)); Text("  Add set")
                    }
                    if (s.b("quick")) OutlinedButton(onClick = { open(WSheet.AddLift) }, modifier = Modifier.weight(1f).height(48.dp)) { Text("Add a lift") }
                    else if (wide && ei < s.list("exercises").size - 1) OutlinedButton(onClick = { vm.act("select", "ei" to ei + 1) }, modifier = Modifier.weight(1f).height(48.dp)) { Text("Next exercise") }
                }
            }
        }
    }
}

@Composable
private fun SetRow(vm: AppViewModel, s: JSONObject, ei: Int, si: Int, set: JSONObject, label: String, showRating: Boolean, onEdit: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val done = set.b("done"); val warm = set.b("warm")
    val grip = vm.prefs.grip
    val cellH: Dp = if (grip) 58.dp else 44.dp
    val bg by animateColorAsState(when { done && warm -> cs.surfaceContainerHigh; done -> cs.primaryContainer; else -> cs.surfaceContainerLow }, label = "row")
    val target = set.o("target")
    val sub = when {
        warm -> "Warm-up"; set.b("sub") -> if (set.s("kind") == "drop") "Drop set" else "Myo mini"
        target != null && set.s("type") == "failure" -> "${target.s("reps")} to failure"
        target != null && !target.isNull("rir") -> "${target.s("reps")} @ ${target.s("rir")} RIR"
        target != null -> "${target.s("reps")} reps"; else -> ""
    }
    val prev = set.o("prev")
    val sub2 = when {
        set.b("pr") -> "New best"
        set.has("hint") && !set.isNull("hint") -> set.s("hint")
        done && !set.isNull("rir") -> "Rated ${set.i("rir").let { if (it >= 4) "4+" else "$it" }} RIR"
        !done && prev != null && prev.d("w") > 0 -> "Last: ${com.pursuitiron.app.ui.common.fmtNum(prev.d("w"))} × ${prev.s("r")}"
        set.b("added") -> "Added set"
        else -> ""
    }
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(bg).padding(vertical = 6.dp, horizontal = 4.dp)) {
        if (showRating) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.width(32.dp), textAlign = TextAlign.Center)
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
            Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.width(32.dp), textAlign = TextAlign.Center)
            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                Text(sub, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (sub2.isNotEmpty()) Text(sub2, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = if (set.b("pr")) cs.primary else cs.onSurfaceVariant)
            }
            val cellBg = if (done) Color.Transparent else cs.surfaceContainerHigh
            Box(Modifier.width(if (grip) 72.dp else 68.dp).height(cellH).clip(RoundedCornerShape(12.dp)).background(cellBg)
                .clickable(onClick = onEdit).semantics { contentDescription = "Weight ${set.s("weight").ifBlank { "not set" }}, edit" },
                contentAlignment = Alignment.Center) {
                Text(set.s("weight").ifBlank { "—" }, style = if (grip) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.width(4.dp))
            Box(Modifier.width(if (grip) 58.dp else 54.dp).height(cellH).clip(RoundedCornerShape(12.dp)).background(cellBg)
                .clickable(onClick = onEdit).semantics { contentDescription = "Reps ${set.s("reps").ifBlank { "not set" }}, edit" },
                contentAlignment = Alignment.Center) {
                Text(set.s("reps").ifBlank { "—" }, style = if (grip) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.width(4.dp))
            val r by animateDpAsState(if (done) 14.dp else 999.dp, Motion.spring(), label = "check")
            val checkW = if (grip) 68.dp else 44.dp
            Box(
                Modifier.width(checkW).height(cellH).clip(RoundedCornerShape(r.coerceAtMost(cellH / 2)))
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
    var w by remember(si, ei) { mutableStateOf(set.s("weight")) }
    var r by remember(si, ei) { mutableStateOf(set.s("reps")) }
    LaunchedEffect(set.s("weight")) { w = set.s("weight") }
    LaunchedEffect(set.s("reps")) { r = set.s("reps") }
    var plates by remember { mutableStateOf(false) }
    val bar = barFor(ex.strings("equip"))
    if (plates) { ToolsSheet(vm, "plates", w, bar ?: "barbell") { plates = false }; return }
    ModalBottomSheet(onDismissRequest = onClose) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Kicker(ex.s("name"))
            Text(when { set.b("done") -> "Edit logged set"; set.b("warm") -> "Warm-up"; else -> "Set ${si + 1}" }, style = MaterialTheme.typography.headlineSmall)
            if (!set.b("done") && !set.b("sub")) com.pursuitiron.app.ui.common.Segmented(
                listOf("standard" to "Standard", "warmup" to "Warm-up", "failure" to "To failure"),
                if (set.b("warm")) "warmup" else set.s("type", "standard"), { k -> vm.act("setType", "ei" to ei, "si" to si, "kind" to k) }, height = 40.dp)
            set.o("target")?.let { t -> Text("Target: ${t.s("reps")} reps at ${t.s("rir")} RIR", color = cs.onSurfaceVariant) }
            Stepper("Weight (${s.s("unit")})", w, KeyboardType.Decimal, onText = { w = it }, onCommit = { vm.act("edit", "ei" to ei, "si" to si, "field" to "weight", "value" to w) },
                onMinus = { vm.act("bumpWeight", "ei" to ei, "si" to si, "dir" to -1) }, onPlus = { vm.act("bumpWeight", "ei" to ei, "si" to si, "dir" to 1) })
            Stepper("Reps", r, KeyboardType.Number, onText = { r = it }, onCommit = { vm.act("edit", "ei" to ei, "si" to si, "field" to "reps", "value" to r) },
                onMinus = { vm.act("bumpReps", "ei" to ei, "si" to si, "delta" to -1) }, onPlus = { vm.act("bumpReps", "ei" to ei, "si" to si, "delta" to 1) })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (set.b("added")) TextButton(onClick = { vm.act("removeSet", "ei" to ei, "si" to si); onClose() }) { Text("Remove set") }
                if (bar != null) TextButton(onClick = { plates = true }) { Icon(PiIcons.Plates, null, modifier = Modifier.size(18.dp)); Text("  Plates") }
                Spacer(Modifier.weight(1f))
                Button(onClick = {
                    if (w != set.s("weight")) vm.act("edit", "ei" to ei, "si" to si, "field" to "weight", "value" to w)
                    if (r != set.s("reps")) vm.act("edit", "ei" to ei, "si" to si, "field" to "reps", "value" to r)
                    onClose()
                }) { Text("Done") }
            }
        }
    }
}

@Composable
private fun Stepper(label: String, value: String, kb: KeyboardType, onText: (String) -> Unit, onCommit: () -> Unit, onMinus: () -> Unit, onPlus: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(cs.surfaceContainerHigh).clickable { onMinus() }
            .semantics { contentDescription = "Less $label"; role = Role.Button }, contentAlignment = Alignment.Center) { Icon(PiIcons.Minus, null) }
        OutlinedTextField(value = value, onValueChange = onText, label = { Text(label) }, singleLine = true, modifier = Modifier.weight(1f),
            keyboardOptions = KeyboardOptions(keyboardType = kb, imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { onCommit() }),
            textStyle = MaterialTheme.typography.titleLarge.copy(textAlign = TextAlign.Center))
        Box(Modifier.size(52.dp).clip(CircleShape).background(cs.primary).clickable { onPlus() }
            .semantics { contentDescription = "More $label"; role = Role.Button }, contentAlignment = Alignment.Center) { Icon(PiIcons.Plus, null, tint = cs.onPrimary) }
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
                MenuRow(PiIcons.Info, "Exercise info", "Form cues and your history", cs.secondaryContainer, cs.onSecondaryContainer) { onPick(WSheet.Info(ex.s("id"))) }
                MenuRow(PiIcons.Swap, "Swap exercise", "Alternatives at your gym", cs.secondaryContainer, cs.onSecondaryContainer) { onPick(WSheet.Swap(ei)) }
                MenuRow(PiIcons.Shield, "Something hurt", "Flag it and the plan protects that area", cs.tertiaryContainer, cs.onTertiaryContainer) { onPick(WSheet.Hurt(ei)) }
                MenuRow(PiIcons.Plus, "Add a set", "One more working set", cs.primaryContainer, cs.onPrimaryContainer) { vm.act("addSet", "ei" to ei); onPick(null) }
                MenuRow(PiIcons.Flame, if (ex.b("hasWarm")) "Remove warm-ups" else "Add warm-ups", "A ramp up to your first working weight", cs.secondaryContainer, cs.onSecondaryContainer) { vm.act("warmups", "ei" to ei); onPick(null) }
                MenuRow(PiIcons.Redo, if (ex.b("hasDrop")) "Remove drop sets" else "Add drop sets", "Two quick drops after the last set", cs.secondaryContainer, cs.onSecondaryContainer) { vm.act("advanced", "ei" to ei, "kind" to "drop"); onPick(null) }
                MenuRow(PiIcons.Sparkle, if (ex.b("hasMyo")) "Remove myo-reps" else "Add myo-reps", "Short-rest mini sets after the last set", cs.secondaryContainer, cs.onSecondaryContainer) { vm.act("advanced", "ei" to ei, "kind" to "myo"); onPick(null) }
                MenuRow(PiIcons.List, "Same weight for all sets", "Set every unlogged set to the average", cs.secondaryContainer, cs.onSecondaryContainer) { vm.act("level", "ei" to ei); onPick(null) }
                MenuRow(PiIcons.Timer, "Rest time", if (ex.i("restCustom") > 0) restLabel(ex.i("restCustom")) else "Default" + (if (ex.i("restDefault") > 0) " · ${restLabel(ex.i("restDefault"))}" else ""), cs.secondaryContainer, cs.onSecondaryContainer) { onPick(WSheet.Rest(ei)) }
                if (count > 1) MenuRow(PiIcons.Swap, if (ex.b("superset")) "Superset: on" else "Superset", "Alternate sets with another lift", cs.secondaryContainer, cs.onSecondaryContainer) { vm.act("select", "ei" to ei); onPick(WSheet.Superset) }
                val prog = ex.o("progression")
                if (prog != null && !prog.b("locked") && !s.b("quick")) MenuRow(PiIcons.Focus, "Progression", s.list("progressionStyles").firstOrNull { it.s("id") == prog.s("style") }?.s("label") ?: "How this lift moves up", cs.secondaryContainer, cs.onSecondaryContainer) { onPick(WSheet.Progression(ei)) }
                MenuRow(PiIcons.Gym, "Goal, weight step, setup", "Settings kept for this lift", cs.secondaryContainer, cs.onSecondaryContainer) { onPick(WSheet.LiftSettings(ei)) }
                MenuRow(PiIcons.Close, "Skip exercise", "Drop it from today's workout", cs.tertiaryContainer, cs.onTertiaryContainer) { vm.act("skipExercise", "ei" to ei); vm.toast("Skipped ${ex.s("name")}"); onPick(null) }
                MenuRow(PiIcons.List, "Note", if (ex.s("note").isBlank()) "Seat height, grip, anything to remember" else ex.s("note"), cs.secondaryContainer, cs.onSecondaryContainer) { onPick(WSheet.Note(ei)) }
                if (ei > 0) MenuRow(PiIcons.ChevronLeft, "Move earlier", "Logged sets move with it", cs.secondaryContainer, cs.onSecondaryContainer) { vm.act("move", "from" to ei, "to" to ei - 1); onPick(null) }
                if (ei < count - 1) MenuRow(PiIcons.ChevronRight, "Move later", "Logged sets move with it", cs.secondaryContainer, cs.onSecondaryContainer) { vm.act("move", "from" to ei, "to" to ei + 1); onPick(null) }
                MenuRow(PiIcons.Sparkle, if (vm.prefs.grip) "Grip mode: on" else "Grip mode", "Bigger controls and stronger taps", cs.secondaryContainer, cs.onSecondaryContainer) { vm.prefs.changeGrip(!vm.prefs.grip); onPick(null) }
            }
        }
    }
}

@Composable
fun MenuRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, sub: String, bg: Color, fg: Color, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().background(cs.surfaceContainerHigh).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(bg), contentAlignment = Alignment.Center) { Icon(icon, null, tint = fg, modifier = Modifier.size(20.dp)) }
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
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
private fun HurtSheet(vm: AppViewModel, ei: Int, onClose: () -> Unit) {
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
