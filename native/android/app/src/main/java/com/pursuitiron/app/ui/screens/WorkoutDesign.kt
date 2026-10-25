@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.pursuitiron.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pursuitiron.app.AppViewModel
import com.pursuitiron.app.ui.common.Kicker
import com.pursuitiron.app.ui.common.PiIcons
import com.pursuitiron.app.ui.common.b
import com.pursuitiron.app.ui.common.d
import com.pursuitiron.app.ui.common.fmtNum
import com.pursuitiron.app.ui.common.i
import com.pursuitiron.app.ui.common.list
import com.pursuitiron.app.ui.common.mmss
import com.pursuitiron.app.ui.common.o
import com.pursuitiron.app.ui.common.s
import com.pursuitiron.app.ui.theme.Motion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

/* The design's workout chrome (prototype workout screen): the header with the workout note, focus mode and
   the clock that opens "Short on time?"; the short-name chips with the exercise list; the exercise head; the
   focus card; the session totals. */

/** Elapsed seconds on the workout clock, holding still while paused. */
fun liveElapsedSec(s: JSONObject, now: Long): Long {
    val until = s.optLong("pausedAt").takeIf { it > 0 } ?: now
    return ((until - s.optLong("startedAt")) / 1000).coerceAtLeast(0)
}

@Composable
fun WorkoutHeader(vm: AppViewModel, s: JSONObject, wide: Boolean, focus: Boolean, onFocus: () -> Unit, onNote: () -> Unit, onTime: () -> Unit, onDiscard: () -> Unit, onFinish: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val elapsed = liveElapsedSec(s, vm.now)
    val est = s.i("estimateMin")
    val paused = s.optLong("pausedAt") > 0
    Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.material3.IconButton(onClick = { vm.minimise() }, modifier = Modifier.size(48.dp)) { Icon(PiIcons.ChevronDown, "Minimise workout") }
        Column(Modifier.weight(1f)) {
            Text(s.s("dayLabel"), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onTime)
                .semantics { contentDescription = "${mmss(elapsed)} elapsed" + (if (paused) ", paused" else "") + (if (est > 0) ", about ${(est - elapsed / 60).coerceAtLeast(0)} minutes left" else "") + ". Short on time?"; role = Role.Button },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(mmss(elapsed), style = MaterialTheme.typography.titleLarge, color = if (paused) cs.onSurfaceVariant else cs.onSurface)
                Surface(color = cs.surfaceContainerHigh, shape = CircleShape) {
                    val left = est - (elapsed / 60).toInt()
                    Text(when { paused -> "paused"; est <= 0 -> "short on time?"; left > 0 -> "~$left min left"; else -> "over by ${-left} min" },
                        style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                }
            }
        }
        val hasNote = s.s("sessionNote").isNotBlank()
        Box(Modifier.size(44.dp).clip(CircleShape).background(if (hasNote) cs.secondaryContainer else Color.Transparent).clickable(onClick = onNote)
            .semantics { contentDescription = if (hasNote) "Workout note: ${s.s("sessionNote")}" else "Add a workout note"; role = Role.Button }, contentAlignment = Alignment.Center) {
            Icon(PiIcons.Note, null, tint = if (hasNote) cs.onSecondaryContainer else cs.onSurfaceVariant, modifier = Modifier.size(22.dp))
            if (hasNote) Box(Modifier.align(Alignment.TopEnd).padding(9.dp).size(8.dp).clip(CircleShape).background(cs.primary))
        }
        val r by animateDpAsState(if (focus) 14.dp else 22.dp, Motion.spring(), label = "focus")
        val bg by animateColorAsState(if (focus) cs.primaryContainer else Color.Transparent, label = "focusBg")
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(r)).background(bg).clickable(onClick = onFocus)
            .semantics { contentDescription = "Focus mode"; role = Role.Switch; stateDescription = if (focus) "On" else "Off" }, contentAlignment = Alignment.Center) {
            Icon(PiIcons.Focus, null, tint = if (focus) cs.onPrimaryContainer else cs.onSurfaceVariant, modifier = Modifier.size(22.dp))
        }
        androidx.compose.material3.IconButton(onClick = onDiscard) { Icon(PiIcons.Trash, "Discard workout", tint = cs.onSurfaceVariant) }
        if (wide) Button(onClick = onFinish, modifier = Modifier.height(44.dp)) { Text("Finish") }
    }
}

/** The design's chip row: short names on a phone, the list button for the whole session. */
@Composable
fun ExerciseChips(vm: AppViewModel, exs: List<JSONObject>, ei: Int, onList: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val state = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(ei) { state.animateScrollToItem(ei.coerceAtLeast(0)) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp, end = 12.dp)) {
        androidx.compose.foundation.lazy.LazyRow(Modifier.weight(1f), state = state, contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(exs.size) { k ->
                val e = exs[k]
                val work = e.list("sets").filter { !it.b("warm") && !it.b("sub") && !it.b("skipped") }
                com.pursuitiron.app.ui.common.MorphChip(e.s("short").ifBlank { e.s("name") }, k == ei, { vm.act("select", "ei" to k) },
                    trailing = "${work.count { it.b("done") }}/${work.size}", done = e.b("done"),
                    modifier = Modifier.semantics { contentDescription = "${e.s("name")}, ${work.count { it.b("done") }} of ${work.size} sets" })
            }
        }
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(cs.primaryContainer).clickable(onClick = onList)
            .semantics { contentDescription = "All exercises — reorder"; role = Role.Button }, contentAlignment = Alignment.Center) {
            Icon(PiIcons.List, null, tint = cs.onPrimaryContainer)
        }
    }
}

/** The design's "Today's exercises": tap to jump, arrows to reorder (logged sets move with their lift), add a lift. */
@Composable
fun ExercisesSheet(vm: AppViewModel, s: JSONObject, onAdd: () -> Unit, onClose: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val exs = s.list("exercises"); val cur = s.i("exIdx")
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Today's exercises", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 4.dp))
            Text("Tap to jump · arrows to reorder · logged sets move with their lift", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp))
            exs.forEachIndexed { k, e ->
                val on = k == cur
                val work = e.list("sets").filter { !it.b("warm") && !it.b("sub") && !it.b("skipped") }
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(if (on) cs.primaryContainer else cs.surfaceContainer)
                    .clickable { vm.act("select", "ei" to k); onClose() }.padding(start = 10.dp, end = 6.dp, top = 8.dp, bottom = 8.dp)
                    .semantics { selected = on }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(if (on) cs.primary else cs.surfaceContainerHighest), contentAlignment = Alignment.Center) {
                        Text("${k + 1}", style = MaterialTheme.typography.labelLarge, color = if (on) cs.onPrimary else cs.onSurface)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(e.s("name"), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${work.count { it.b("done") }}/${work.size} sets", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    }
                    listOf(Triple(PiIcons.ChevronUp, k - 1, "Move ${e.s("name")} up"), Triple(PiIcons.ChevronDown, k + 1, "Move ${e.s("name")} down")).forEach { (ic, to, lbl) ->
                        val ok = to in exs.indices
                        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(if (ok) cs.surface else Color.Transparent)
                            .then(if (ok) Modifier.clickable { vm.act("move", "from" to k, "to" to to) } else Modifier)
                            .semantics { contentDescription = lbl; role = Role.Button }, contentAlignment = Alignment.Center) {
                            Icon(ic, null, tint = if (ok) cs.onSurface else cs.outlineVariant, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
            OutlinedButton(onClick = { onClose(); onAdd() }, modifier = Modifier.fillMaxWidth().height(48.dp).padding(top = 4.dp)) {
                Icon(PiIcons.Plus, null, modifier = Modifier.size(18.dp)); Text("  Add a lift")
            }
            Button(onClick = onClose, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Done") }
        }
    }
}

/** The design's "Short on time?": trim to a time budget (later lifts' last sets first), pause or reset the clock. */
@Composable
fun TimeSheet(vm: AppViewModel, s: JSONObject, onClose: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    var o by remember { mutableStateOf<JSONObject?>(null) }
    LaunchedEffect(s.i("totalSets"), s.i("doneSets")) { o = vm.timeOptions() }
    val paused = s.optLong("pausedAt") > 0
    ModalBottomSheet(onDismissRequest = onClose) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Short on time?", style = MaterialTheme.typography.headlineSmall)
            val x = o
            if (x != null) {
                val rests = listOfNotNull(x.optInt("compoundRest").takeIf { it > 0 }?.let { "${mmss(it.toLong())} compound" }, x.optInt("isolationRest").takeIf { it > 0 }?.let { "${mmss(it.toLong())} isolation" })
                Text("About ${x.i("leftMin")} minutes of work left, at each lift's own rest" + (if (rests.isNotEmpty()) " (${rests.joinToString(", ")})." else "."),
                    style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    x.list("options").forEach { op ->
                        val cut = op.i("cut")
                        Column(Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).background(if (cut > 0) cs.primaryContainer else cs.surfaceContainerHigh)
                            .clickable { if (cut > 0) { vm.act("fitTime", "minutes" to op.i("minutes")); vm.toast("Fit to ${op.i("minutes")} min · $cut set${if (cut == 1) "" else "s"} trimmed") } else vm.toast("${op.i("minutes")} min fits what's left"); onClose() }
                            .semantics(mergeDescendants = true) { role = Role.Button; contentDescription = "${op.i("minutes")} minutes, " + if (cut > 0) "trims $cut sets" else "fits now" }
                            .padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            val fg = if (cut > 0) cs.onPrimaryContainer else cs.onSurface
                            Text("${op.i("minutes")}m", style = MaterialTheme.typography.titleLarge, color = fg)
                            Text(if (cut > 0) "−$cut sets" else "fits now", style = MaterialTheme.typography.labelSmall, color = fg.copy(alpha = 0.8f))
                        }
                    }
                }
                if (x.i("trimmed") > 0) TextButton(onClick = { vm.act("restoreTrimmed"); vm.toast("Trimmed sets restored"); onClose() }) { Text("Restore ${x.i("trimmed")} trimmed set${if (x.i("trimmed") == 1) "" else "s"}") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.FilledTonalButton(onClick = { vm.act("pauseTimer"); onClose() }, modifier = Modifier.weight(1f).height(48.dp)) { Text(if (paused) "Resume workout timer" else "Pause workout timer") }
                OutlinedButton(onClick = { vm.act("resetTimer"); vm.toast("Timer reset"); onClose() }, modifier = Modifier.weight(1f).height(48.dp)) { Text("Reset timer") }
            }
            Text("Trims the last sets of later exercises first and keeps at least one working set of every lift. Trimmed sets can be restored from the list.",
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
    }
}

/** The design's exercise head: name (opens the lift), progression tag, the target line, the setup chip. */
@Composable
fun ExerciseHead(s: JSONObject, ex: JSONObject, onInfo: () -> Unit, onProg: () -> Unit, onSetup: () -> Unit, onMenu: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.weight(1f)) {
            Row(Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onInfo).semantics { contentDescription = "${ex.s("name")}: how to do it"; role = Role.Button },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(ex.s("name"), style = MaterialTheme.typography.headlineLarge)
                Icon(PiIcons.Info, null, tint = cs.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
            FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                val prog = ex.o("progression")
                if (prog != null && !prog.b("locked") && !s.b("quick")) {
                    val st = prog.s("style", "auto")
                    val name = if (st == "auto") "Auto · " + prog.s("auto").replaceFirstChar { it.uppercase() } else prog.list("options").firstOrNull { it.s("id") == st }?.s("label") ?: st
                    Surface(onClick = onProg, color = cs.tertiaryContainer, contentColor = cs.onTertiaryContainer, shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(28.dp).semantics { contentDescription = "Progression: $name. Change how this lift progresses" }) {
                        Row(Modifier.padding(start = 10.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(name, style = MaterialTheme.typography.labelMedium); Icon(PiIcons.ChevronDown, null, modifier = Modifier.size(14.dp))
                        }
                    }
                }
                if (ex.b("primary")) com.pursuitiron.app.ui.common.Badge("Primary")
                if (ex.b("assisted")) com.pursuitiron.app.ui.common.Badge("Assisted · less help is progress", cs.tertiaryContainer, cs.onTertiaryContainer)
                if (ex.d("goal") > 0) com.pursuitiron.app.ui.common.Badge("Goal ${fmtNum(ex.d("goal"))} ${s.s("unit")}", cs.primaryContainer, cs.onPrimaryContainer)
                if (ex.s("line").isNotBlank()) Text(ex.s("line"), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                val setup = ex.list("setup").filter { it.s("value").isNotBlank() }.joinToString(" · ") { "${it.s("label")} ${it.s("value")}" }
                if (setup.isNotBlank()) Surface(onClick = onSetup, color = cs.surfaceContainerHigh, shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(28.dp).semantics { contentDescription = "Setup: $setup. Edit" }) {
                    Row(Modifier.padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(PiIcons.Stretch, null, modifier = Modifier.size(14.dp)); Text(setup, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(cs.secondaryContainer).clickable(onClick = onMenu)
            .semantics { contentDescription = "Exercise actions"; role = Role.Button }, contentAlignment = Alignment.Center) { Icon(PiIcons.More, null, tint = cs.onSecondaryContainer) }
    }
}

/** The design's note from last time on this lift, dismissible for this session. */
@Composable
fun LastNoteCard(ex: JSONObject, dayLabel: String) {
    val note = ex.s("note").ifBlank { ex.s("lastNote") }
    if (note.isBlank()) return
    var shown by remember(ex.s("id")) { mutableStateOf(true) }
    val cs = MaterialTheme.colorScheme
    AnimatedVisibility(shown, exit = shrinkVertically() + fadeOut()) {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(cs.secondaryContainer).padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(PiIcons.Note, null, tint = cs.onSecondaryContainer, modifier = Modifier.size(18.dp))
            Column(Modifier.weight(1f)) {
                Text(if (ex.s("note").isNotBlank()) "Your note" else "From your last $dayLabel", style = MaterialTheme.typography.labelLarge, color = cs.onSecondaryContainer)
                Text("“$note”", style = MaterialTheme.typography.bodyMedium, color = cs.onSecondaryContainer)
            }
            androidx.compose.material3.IconButton(onClick = { shown = false }, modifier = Modifier.size(36.dp)) { Icon(PiIcons.Close, "Dismiss last time's note", tint = cs.onSecondaryContainer, modifier = Modifier.size(18.dp)) }
        }
    }
}

/** The design's "Why 82.5 kg?": a chip that opens in place, with the engine's reason, its read and the lift's recent e1RM. */
@Composable
fun WhyInline(vm: AppViewModel, s: JSONObject, ex: JSONObject, onHistory: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val ei = ex.i("ei")
    var open by remember(ex.s("id")) { mutableStateOf(false) }
    var x by remember(ex.s("id")) { mutableStateOf<JSONObject?>(null) }
    var spark by remember(ex.s("id")) { mutableStateOf<List<Double>>(emptyList()) }
    LaunchedEffect(open, ex.s("id")) { if (open) { x = vm.explain(ei); spark = vm.liftDetail(ex.s("id"))?.list("points")?.takeLast(6)?.map { it.d("e1rm") } ?: emptyList() } }
    val first = ex.list("sets").firstOrNull { !it.b("warm") && it.s("weight").isNotBlank() }
    val tag = first?.let { showsTag(ex, it) } == true
    val title = when {
        first == null -> "No suggestion yet: why?"
        tag -> "Why ${first.s("reps")} reps?"
        ex.b("assisted") -> "Why ${first.s("weight").trim().removePrefix("-")} ${s.s("unit")} of help?"
        else -> "Why ${first.s("weight")} ${s.s("unit")}?"
    }
    if (!open) {
        Surface(onClick = { open = true }, color = cs.surfaceContainerHigh, shape = CircleShape, modifier = Modifier.height(36.dp)) {
            Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(PiIcons.Help, null, modifier = Modifier.size(16.dp)); Text(title, style = MaterialTheme.typography.labelLarge)
            }
        }
        return
    }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(cs.surfaceContainerHigh).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(cs.surface).clickable { open = false }.semantics { contentDescription = "Close explanation"; role = Role.Button },
                contentAlignment = Alignment.Center) { Icon(PiIcons.Close, null, modifier = Modifier.size(16.dp)) }
        }
        val e = x
        if (e == null) { Text("Asking the engine…", color = cs.onSurfaceVariant); return@Column }
        e.o("load")?.s("reason")?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val read = e.o("read"); val dec = e.o("decided")
            listOfNotNull(read?.takeIf { !it.isNull("e1rm") }?.let { "e1RM ${it.s("e1rm")} ${s.s("unit")}" },
                dec?.s("style")?.takeIf { it.isNotBlank() }?.let { it.replace('_', ' ').replaceFirstChar { c -> c.uppercase() } },
                read?.i("historyEntriesForThisDay")?.let { n -> if (n == 0) "First time on this day" else "From $n earlier session${if (n == 1) "" else "s"}" }).forEach { t ->
                Text(t, style = MaterialTheme.typography.labelMedium, modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(cs.surface).padding(horizontal = 10.dp, vertical = 6.dp))
            }
        }
        if (spark.size >= 2) Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onHistory).padding(vertical = 4.dp)
            .semantics { contentDescription = "Open ${ex.s("name")} history" }) {
            Row { Text("e1RM · last ${spark.size} sessions · tap for history", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant, modifier = Modifier.weight(1f))
                Text("${fmtNum(spark.min())}–${fmtNum(spark.max())}", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant) }
            com.pursuitiron.app.ui.common.Sparkline(spark, Modifier.fillMaxWidth().height(56.dp))
        }
    }
}

/** The design's session totals at the foot of the exercise. */
@Composable
fun SessionTotals(s: JSONObject) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(cs.surfaceContainer).padding(horizontal = 20.dp, vertical = 16.dp)) {
        Column(Modifier.weight(1f)) {
            Text("Working sets", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
            Text("${s.i("doneSets")} / ${s.i("totalSets")}", style = MaterialTheme.typography.titleLarge)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("Volume", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
            val v = s.i("volume"); val u = s.s("unit")
            Text(if (v >= 1000) "${com.pursuitiron.app.ui.common.fmt1(v / 1000.0)} ${if (u == "lb") "k lb" else "t"}" else "$v $u", style = MaterialTheme.typography.titleLarge)
        }
    }
}

/** The design's focus mode: one set at a time, big, with hold-to-log. */
@Composable
fun FocusCard(vm: AppViewModel, s: JSONObject, ex: JSONObject, onAdjust: (Int) -> Unit, onNextLift: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val ei = ex.i("ei"); val sets = ex.list("sets"); val u = s.s("unit")
    // After a set is logged the card holds on it for the rating, as in the list.
    val lastDone = sets.indexOfLast { it.b("done") && !it.b("warm") && !it.b("sub") && it.isNull("rir") }
    val fi = if (lastDone >= 0 && lastDone == sets.indexOfLast { it.b("done") }) lastDone else ex.i("cur")
    if (fi < 0 || fi >= sets.size) { LiftDoneCard(vm, s, ex, onNextLift); return }
    val x = sets[fi]
    val rating = x.b("done")
    val label = setLabelOf(ex, fi)
    val tag = showsTag(ex, x)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp)).background(cs.primaryContainer).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = cs.onPrimary, modifier = Modifier.clip(CircleShape).background(cs.primary).padding(horizontal = 12.dp, vertical = 6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                sets.forEachIndexed { k, z ->
                    val w by animateDpAsState(if (k == fi) 20.dp else 8.dp, Motion.spring(), label = "dot")
                    Box(Modifier.width(w).height(8.dp).clip(CircleShape).background(when { z.b("done") -> cs.primary; k == fi -> cs.onPrimaryContainer; else -> cs.onPrimaryContainer.copy(alpha = 0.25f) }))
                }
            }
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(if (tag) ex.s("loadTag") else x.s("weight").ifBlank { "—" }, style = MaterialTheme.typography.displayMedium.copy(fontSize = 88.sp, lineHeight = 92.sp), color = cs.onPrimaryContainer,
                modifier = Modifier.semantics { contentDescription = if (tag) ex.s("loadTag") else "${x.s("weight")} $u" })
            if (!tag) Text(" $u", style = MaterialTheme.typography.titleLarge, color = cs.onPrimaryContainer, modifier = Modifier.padding(bottom = 14.dp))
        }
        val t = x.o("target")
        val sub = when { x.b("warm") -> "warm-up"; x.b("sub") && x.s("kind") == "myo" -> "then a short rest"; t != null && !t.isNull("rir") -> "@" + t.s("effort").ifBlank { t.s("rir") + " RIR" }; else -> "" }
        Text("× ${x.s("reps").ifBlank { "—" }} reps" + if (sub.isNotBlank()) " · $sub" else "", style = MaterialTheme.typography.titleLarge, color = cs.onPrimaryContainer)
        x.o("prev")?.takeIf { it.d("w") > 0 }?.let { p -> Text("Last: ${fmtNum(p.d("w"))} × ${p.s("r")}", style = MaterialTheme.typography.bodySmall, color = cs.onPrimaryContainer.copy(alpha = 0.85f)) }
        // The bar with one side's plates, as the prototype draws it.
        val pl = ex.o("plates")
        if (!tag && pl != null && ex.i("platesFor") == fi) {
            val per = pl.optJSONArray("perSide")?.let { a -> (0 until a.length()).map { a.optDouble(it) } } ?: emptyList()
            if (per.isNotEmpty()) Row(Modifier.height(40.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                val big = if (u == "lb") 25.0 else 15.0; val maxP = if (u == "lb") 45.0 else 25.0
                Box(Modifier.width(22.dp).height(7.dp).clip(RoundedCornerShape(3.dp)).background(cs.onPrimaryContainer.copy(alpha = 0.35f)))
                per.forEach { p -> Box(Modifier.width(9.dp).height((14 + Math.round(p / maxP * 26)).dp).clip(RoundedCornerShape(3.dp)).background(if (p >= big) cs.primary else cs.onPrimaryContainer.copy(alpha = 0.7f))) }
                Box(Modifier.width(60.dp).height(7.dp).clip(RoundedCornerShape(3.dp)).background(cs.onPrimaryContainer.copy(alpha = 0.35f)))
            }
        }
        if (rating) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(cs.primary).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Reps left in the tank?", style = MaterialTheme.typography.labelLarge, color = cs.onPrimary)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    (0..4).forEach { r ->
                        Box(Modifier.size(52.dp).clip(CircleShape).background(cs.onPrimary.copy(alpha = 0.16f)).clickable { vm.act("rir", "ei" to ei, "si" to fi, "rir" to r) }
                            .semantics { contentDescription = if (r == 4) "4 or more reps left" else "$r reps left"; role = Role.Button }, contentAlignment = Alignment.Center) {
                            Text(if (r == 4) "4+" else "$r", style = MaterialTheme.typography.titleMedium, color = cs.onPrimary)
                        }
                    }
                }
            }
        } else HoldToLog(if (x.b("warm")) "warm-up" else "set") { vm.act("toggle", "ei" to ei, "si" to fi) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(onClick = { onAdjust(fi) }, color = cs.surface, shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f).height(44.dp)) {
                Box(contentAlignment = Alignment.Center) { Text("Adjust", style = MaterialTheme.typography.labelLarge) }
            }
            if (!x.b("done") && !x.b("sub")) Surface(onClick = { vm.act("skipSet", "ei" to ei, "si" to fi); vm.toast("Set skipped — not counted") }, color = Color.Transparent,
                contentColor = cs.onPrimaryContainer, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, cs.onPrimaryContainer), modifier = Modifier.weight(1f).height(44.dp)) {
                Box(contentAlignment = Alignment.Center) { Text("Skip set", style = MaterialTheme.typography.labelLarge) }
            }
        }
        val ni = sets.indices.firstOrNull { it > fi && !sets[it].b("done") && !sets[it].b("skipped") }
        val nextEx = s.list("exercises").getOrNull(ei + 1)
        Text(when {
            ni != null -> "Up next: " + (if (sets[ni].b("warm")) "warm-up" else if (sets[ni].b("sub")) "mini" else "set") + " · " + (if (showsTag(ex, sets[ni])) ex.s("loadTag") else sets[ni].s("weight")) + " × " + sets[ni].s("reps")
            nextEx != null -> "Then: ${nextEx.s("name")}"
            else -> "Last set of the workout"
        }, style = MaterialTheme.typography.labelMedium, color = cs.onPrimaryContainer.copy(alpha = 0.8f))
    }
}

/** Press and hold to log (the design's guard against a stray tap); TalkBack and keyboards activate it directly. */
@Composable
fun HoldToLog(kind: String, onLog: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    var holding by remember { mutableStateOf(false) }
    val fill by animateFloatAsState(if (holding) 1f else 0f, if (holding) tween(700) else tween(150), label = "hold")
    val scope = rememberCoroutineScope()
    Box(Modifier.fillMaxWidth().height(68.dp).clip(CircleShape).background(cs.primary)
        .semantics { contentDescription = "Log $kind: press and hold"; role = Role.Button; onClick(label = "Log $kind") { onLog(); true } }
        .pointerInput(Unit) {
            detectTapGestures(onPress = {
                holding = true
                val job = scope.launch { delay(700); holding = false; onLog() }
                tryAwaitRelease()
                if (job.isActive) { job.cancel(); holding = false }
            })
        }, contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(fill).align(Alignment.CenterStart).background(cs.onPrimary.copy(alpha = 0.28f)))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(PiIcons.Check, null, tint = cs.onPrimary, modifier = Modifier.size(22.dp))
            Text("Hold to log", style = MaterialTheme.typography.titleMedium, color = cs.onPrimary)
        }
    }
}

/** Focus mode when the lift is finished: the design's done card with its sets, top set and e1RM. */
@Composable
private fun LiftDoneCard(vm: AppViewModel, s: JSONObject, ex: JSONObject, onNextLift: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val work = ex.list("sets").filter { !it.b("warm") && !it.b("sub") && it.b("done") }
    val top = work.maxByOrNull { (it.s("weight").toDoubleOrNull() ?: 0.0) * 100 + (it.s("reps").toDoubleOrNull() ?: 0.0) }
    val e1 = work.maxOfOrNull { val w = it.s("weight").toDoubleOrNull() ?: 0.0; val r = it.s("reps").toDoubleOrNull() ?: 0.0; if (w > 0 && r > 0) w * (1 + r / 30.0) else 0.0 } ?: 0.0
    val pr = work.any { it.b("pr") }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp)).background(cs.primaryContainer).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(56.dp).clip(RoundedCornerShape(18.dp)).background(cs.primary), contentAlignment = Alignment.Center) { Icon(PiIcons.Check, null, tint = cs.onPrimary, modifier = Modifier.size(28.dp)) }
        Text("${ex.s("short").ifBlank { ex.s("name") }} done", style = MaterialTheme.typography.headlineMedium, color = cs.onPrimaryContainer)
        if (pr) Text("NEW e1RM PR", style = MaterialTheme.typography.labelLarge, color = cs.onPrimary, modifier = Modifier.clip(CircleShape).background(cs.primary).padding(horizontal = 14.dp, vertical = 6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Sets" to "${work.size}", "Top set" to (top?.let { "${it.s("weight")}×${it.s("reps")}" } ?: "—"), "e1RM" to if (e1 > 0) fmtNum(Math.round(e1 * 2) / 2.0) else "—").forEach { (k, v) ->
                Column(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(cs.surface).padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(k, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant); Text(v, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        val nx = s.list("exercises").getOrNull(ex.i("ei") + 1)
        Button(onClick = onNextLift, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text(if (nx != null) "Next: ${nx.s("name")}" else "Finish workout", style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
}
