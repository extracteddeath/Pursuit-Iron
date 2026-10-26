@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.pursuitiron.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.pursuitiron.app.ui.common.Segmented
import com.pursuitiron.app.ui.common.d
import com.pursuitiron.app.ui.common.fmtNum
import com.pursuitiron.app.ui.common.strings
import com.pursuitiron.app.ui.theme.Motion
import kotlinx.coroutines.launch
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pursuitiron.app.AppViewModel
import com.pursuitiron.app.ui.LocalWide
import com.pursuitiron.app.ui.common.Badge
import com.pursuitiron.app.ui.common.Kicker
import com.pursuitiron.app.ui.common.MorphChip
import com.pursuitiron.app.ui.common.PiCard
import com.pursuitiron.app.ui.common.PiIcons
import com.pursuitiron.app.ui.common.b
import com.pursuitiron.app.ui.common.i
import com.pursuitiron.app.ui.common.list
import com.pursuitiron.app.ui.common.s
import org.json.JSONObject

private sealed interface ESheet {
    data class Slot(val dayId: String, val slot: Int) : ESheet
    data class Swap(val dayId: String, val slot: Int) : ESheet
    data class Add(val dayId: String) : ESheet
    data class Day(val dayId: String) : ESheet
    data class RenameDay(val dayId: String, val label: String) : ESheet
    data object Rename : ESheet
}

/** The design's Edit program (Main.dc.html pe): one day at a time, its focus and weekly sets, then each lift with
    swap, progression and more. Every change goes to a draft (App.js ProgramView) and reaches the program on Save. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ProgramEditorScreen(vm: AppViewModel) {
    val e = vm.editor ?: return
    val cs = MaterialTheme.colorScheme
    val wide = LocalWide.current
    var sheet by remember { mutableStateOf<ESheet?>(null) }
    var leaving by remember { mutableStateOf(false) }
    var scopeAsk by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    var dayId by remember { mutableStateOf<String?>(null) }
    var dayMenu by remember { mutableStateOf(false) }
    var noteEdit by remember { mutableStateOf<String?>(null) }
    var confirmDeleteDay by remember { mutableStateOf(false) }
    var openRow by remember { mutableStateOf<Pair<Int, String>?>(null) } // slot to "swap" | "prog" | "more"
    var why by remember { mutableStateOf<Int?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    var hitOpen by remember { mutableStateOf<String?>(null) }
    if (settingsOpen) ProgramSettingsSheet(vm) { settingsOpen = false }
    val dirty = e.b("dirty")
    BackHandler { if (dirty) leaving = true else vm.closeEditor() }
    fun save() { if (e.s("cycleId").isNotBlank()) scopeAsk = true else vm.saveEdits("program") }
    val days = e.list("days")
    val d = days.firstOrNull { it.s("id") == dayId } ?: days.firstOrNull()
    val di = days.indexOf(d)
    LaunchedEffect(d?.s("id")) { openRow = null; dayMenu = false; noteEdit = null }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        // Header: close, title over the program and week, Undo all and Save.
        Row(Modifier.padding(start = 12.dp, end = 16.dp, top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(onClick = { if (dirty) leaving = true else vm.closeEditor() }) { Icon(PiIcons.Close, "Close editor") }
            Column(Modifier.weight(1f).clickable { sheet = ESheet.Rename }.semantics { contentDescription = "Edit program: ${e.s("name")}. Rename" }) {
                Text("Edit program", style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold))
                Text(e.s("name") + (e.optInt("currentWeek", 0).takeIf { it > 0 }?.let { " · week $it of ${e.i("weeksTotal")}" } ?: ""), style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp),
                    color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (dirty) TextButton(onClick = { vm.discardEdits() }) { Text("Undo all", fontWeight = FontWeight.Bold) }
            Box(Modifier.height(40.dp).clip(CircleShape).background(if (dirty) cs.primary else cs.surfaceContainerHighest)
                .clickable { if (dirty) save() else vm.toast("No changes yet") }.semantics { role = Role.Button; if (!dirty) stateDescription = "No changes yet" }
                .padding(horizontal = 20.dp), contentAlignment = Alignment.Center) {
                Text("Save", style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold), color = if (dirty) cs.onPrimary else cs.onSurfaceVariant)
            }
        }
        if (d == null) return@Column
        val dId = d.s("id")
        val exs = d.list("exercises")
        val listState = androidx.compose.foundation.lazy.rememberLazyListState()
        // Opening another day brings its header to the top, so the folded days above don't push it away.
        var first by remember { mutableStateOf(true) }
        LaunchedEffect(dId) { if (first) { first = false; return@LaunchedEffect }; listState.animateScrollToItem(1 + (if (e.list("strip").isNotEmpty()) 1 else 0) + di) }
        LazyColumn(Modifier.weight(1f).fillMaxWidth().widthIn(max = 760.dp).align(Alignment.CenterHorizontally), state = listState,
            contentPadding = PaddingValues(start = if (wide) 32.dp else 16.dp, end = if (wide) 32.dp else 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // The whole block: each week's sets and minutes; the targets below follow the week picked.
            item(key = "weeks") {
                val cur = e.optInt("currentWeek", 0)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LazyRow(Modifier.fillMaxWidth().layout { m, k -> val x = (if (wide) 32 else 16).dp.roundToPx(); val pl = m.measure(k.copy(minWidth = 0, maxWidth = k.maxWidth + 2 * x)); layout(pl.width - 2 * x, pl.height) { pl.place(-x, 0) } },
                        contentPadding = PaddingValues(horizontal = if (wide) 32.dp else 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(e.list("weeks"), key = { "w" + it.i("week") }) { w ->
                            val on = w.i("week") == e.i("weekIndex")
                            val r by animateDpAsState(if (on) 16.dp else 28.dp, Motion.spatialFast(), label = "wk")
                            val label = if (w.b("deload")) "Deload" else "Week ${w.i("week")}"
                            Column(Modifier.widthIn(min = 104.dp).height(56.dp).clip(RoundedCornerShape(r)).background(if (on) cs.primary else androidx.compose.ui.graphics.Color.Transparent)
                                .then(if (on) Modifier else Modifier.border(1.5.dp, cs.outlineVariant, RoundedCornerShape(r)))
                                .clickable { vm.editWeek(w.i("week")) }
                                .semantics { role = Role.RadioButton; selected = on; contentDescription = "$label: ${w.i("sets")} sets, ${w.i("minutes")} minutes" + if (w.i("week") == cur) ", this week" else "" }
                                .padding(horizontal = 14.dp), verticalArrangement = Arrangement.Center) {
                                val fg = if (on) cs.onPrimary else cs.onSurface
                                Text(label, style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold), color = fg)
                                Text("${w.i("sets")} sets · ${w.i("minutes")}m", style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = fg.copy(alpha = 0.8f))
                            }
                        }
                    }
                    Text((if (e.i("weekIndex") == cur) "This week" else if (e.list("weeks").getOrNull(e.i("weekIndex") - 1)?.b("deload") == true) "The deload week" else "Week ${e.i("weekIndex")}") +
                        "’s targets are shown. Swaps, order and days apply to every week of the block.",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp), color = cs.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
                }
            }
            // The week at a glance: which day trains when, rest days between.
            val strip = e.list("strip")
            if (strip.isNotEmpty()) item(key = "strip") {
                Row(Modifier.fillMaxWidth().semantics { contentDescription = "This week" }, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    strip.forEach { c ->
                        val train = c.s("dayId").isNotBlank() && c.s("dayId") != "null"; val on = train && c.s("dayId") == dId
                        Column(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(when { on -> cs.primary; train -> cs.surfaceContainerHigh; else -> androidx.compose.ui.graphics.Color.Transparent })
                            .clickable(enabled = train && !on) { dayId = c.s("dayId") }
                            .semantics(mergeDescendants = true) { contentDescription = c.s("weekday") + ": " + (if (train) c.s("label") + (if (on) ", open" else "") else "rest day") }
                            .padding(vertical = 8.dp, horizontal = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            val fg = when { on -> cs.onPrimary; train -> cs.onSurface; else -> cs.onSurfaceVariant }
                            Text(c.s("weekday").lowercase().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.ExtraBold), color = fg.copy(alpha = 0.8f))
                            Text(if (train) shortDay(c.s("label")) else "Rest", style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold), color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            days.take(di).forEach { x -> item(key = "before:" + x.s("id")) { DayCard(x, e.i("weekIndex")) { dayId = x.s("id") } } }
            item(key = "head") {
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(cs.secondaryContainer).padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 14.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(cs.surface), contentAlignment = Alignment.Center) {
                        Text(d.s("weekday").ifBlank { "D${di + 1}" }, style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.ExtraBold), color = cs.onSurface)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(d.s("label"), style = MaterialTheme.typography.headlineSmall.copy(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold), color = cs.onSecondaryContainer)
                        Text(if (exs.isEmpty()) "No lifts yet · add some, or it stays a rest day"
                            else "${exs.size} lifts · ${fmtNum(d.d("sets"))} sets" + (d.i("minutes").takeIf { it > 0 }?.let { " · ~$it min" } ?: ""),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp), color = cs.onSecondaryContainer.copy(alpha = 0.85f))
                    }
                    Box(Modifier.height(40.dp).clip(CircleShape).background(cs.surface).clickable { dayMenu = !dayMenu }
                        .semantics { role = Role.Button; stateDescription = if (dayMenu) "Expanded" else "Collapsed" }.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                        Text(if (dayMenu) "Done" else "Day options", style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold), color = cs.onSurface)
                    }
                }
            }
            if (dayMenu) item(key = "menu") {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(cs.surfaceContainerLow).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        DayAct(PiIcons.Pencil, "Rename") { sheet = ESheet.RenameDay(dId, d.s("label")); dayMenu = false }
                        DayAct(PiIcons.Note, if (d.s("note").isBlank()) "Add day note" else "Edit day note") { noteEdit = d.s("note"); dayMenu = false }
                        DayAct(PiIcons.Copy, "Duplicate") { vm.edit("copyDay", "dayId" to dId); dayMenu = false }
                        if (di > 0) DayAct(PiIcons.ChevronLeft, "Move earlier") { vm.edit("moveDay", "dayId" to dId, "dir" to -1) }
                        if (di < days.size - 1) DayAct(PiIcons.ChevronRight, "Move later") { vm.edit("moveDay", "dayId" to dId, "dir" to 1) }
                        DayAct(PiIcons.Plus, "Add a day") { vm.edit("addDay"); dayMenu = false }
                        if (days.size > 1) DayAct(PiIcons.Trash, "Delete day", tone = true) { confirmDeleteDay = true }
                    }
                    Text(if (days.size < 2) "A program needs at least one day." else "Moves change the weekly order. Logged workouts stay in your history.",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp), color = cs.onSurfaceVariant)
                }
            }
            noteEdit?.let { cur -> item(key = "noteEdit") {
                var v by remember(dId) { mutableStateOf(cur) }
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(cs.surfaceContainerLow).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Day note", style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold))
                    Text("Shown at the top of this workout: focus cues, tempo, reminders.", style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = cs.onSurfaceVariant)
                    OutlinedTextField(v, { v = it.take(240) }, Modifier.fillMaxWidth(), minLines = 3, placeholder = { Text("e.g. Slow eccentrics; leave 2 in the tank on squats.") })
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                        TextButton(onClick = { noteEdit = null }) { Text("Cancel") }
                        Button(onClick = { vm.edit("dayNote", "dayId" to dId, "note" to v); noteEdit = null }) { Text("Save") }
                    }
                }
            } }
            if (d.s("note").isNotBlank() && noteEdit == null) item(key = "note") {
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(cs.primaryContainer).padding(horizontal = 14.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(PiIcons.Info, null, tint = cs.onPrimaryContainer, modifier = Modifier.size(16.dp).padding(top = 1.dp))
                    Text(d.s("note"), style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp), color = cs.onPrimaryContainer)
                }
            }
            // Day setup: focus, sets by muscle, and who owns the numbers.
            item(key = "setup") {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(cs.surfaceContainerLow).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Cap("DAY FOCUS")
                        val f = d.s("focus").ifBlank { "auto" }
                        Segmented(listOf("auto" to "Auto", "strength" to "Strength", "hypertrophy" to "Hypertrophy"), f,
                            { vm.edit("dayFocus", "dayId" to dId, "focus" to if (it == "auto") null else it) })
                        Text(when (f) { "strength" -> "Heavier loads, lower reps, longer rest on this day. Overrides the program goal."
                            "hypertrophy" -> "Higher reps, shorter rest, more volume on this day. Overrides the program goal."
                            else -> "Follows the program goal. Tag a day to run a hybrid week (e.g. strength Upper/Lower + hypertrophy Push/Pull/Legs)." },
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp), color = cs.onSurfaceVariant)
                    }
                    val hits = d.list("hits")
                    if (hits.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Cap("SETS THIS WEEK BY MUSCLE")
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            hits.forEach { h -> val on = hitOpen == h.s("part")
                                Text("${h.s("label")} ${fmtNum(h.d("sets"))}", style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
                                    color = if (on) cs.onSecondaryContainer else cs.onSurface,
                                    modifier = Modifier.clip(CircleShape).background(if (on) cs.secondaryContainer else cs.surfaceContainerHigh)
                                        .clickable { hitOpen = if (on) null else h.s("part") }.semantics { role = Role.Button; selected = on }
                                        .padding(horizontal = 14.dp, vertical = 9.dp)) }
                        }
                        // Tap a muscle to see what trains it: the lifts on this day whose main muscle it is.
                        hits.firstOrNull { it.s("part") == hitOpen }?.let { h ->
                            val from = exs.filter { it.s("part") == h.s("part") && it.i("sets") > 0 }
                            Text("${h.s("label")}: " + from.joinToString(" · ") { "${it.s("name")} ${it.i("sets")}" } + " direct sets this week.",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 17.sp), color = cs.onSurfaceVariant)
                        }
                    }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(cs.outlineVariant))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(if (e.b("manual")) "Manual targets: change sets and reps on each lift. Auto won’t rewrite these at block review."
                            else "Sets, reps and effort come from the engine for each week of the block. Switch Set & rep targets to Manual in Settings to set them yourself.",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp), color = cs.onSurfaceVariant, modifier = Modifier.weight(1f))
                        if (!e.b("manual")) Box(Modifier.height(40.dp).clip(CircleShape).background(cs.secondaryContainer).clickable { settingsOpen = true }
                            .semantics { role = Role.Button }.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                            Text("Settings", style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold), color = cs.onSecondaryContainer)
                        }
                    }
                }
            }
            item(key = "exh") {
                Row(Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.Bottom) {
                    Text("Exercises", style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, fontWeight = FontWeight.SemiBold), modifier = Modifier.weight(1f))
                    Text("${exs.size} ${if (exs.size == 1) "lift" else "lifts"}", style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp), color = cs.onSurfaceVariant)
                }
            }
            items(exs, key = { "$dId:${it.i("slot")}:${it.s("id")}" }) { x ->
                val slot = x.i("slot")
                val paired = x.b("superset") || exs.getOrNull(slot - 1)?.b("superset") == true
                ExerciseCard(vm, e, dId, x, exs.size, paired, openRow?.takeIf { it.first == slot }?.second,
                    onToggle = { what -> openRow = if (openRow == slot to what) null else slot to what },
                    onWhy = { why = slot }, onInfo = { info = x.s("id") }, modifier = Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null, placementSpec = Motion.spatial()))
            }
            item(key = "add") {
                Box(Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(18.dp)).border(1.5.dp, cs.outlineVariant, RoundedCornerShape(18.dp))
                    .clickable { sheet = ESheet.Add(dId) }.semantics { role = Role.Button }, contentAlignment = Alignment.Center) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(PiIcons.Plus, null, tint = cs.primary, modifier = Modifier.size(18.dp))
                        Text("Add an exercise", style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold), color = cs.primary)
                    }
                }
            }
            days.drop(di + 1).forEach { x -> item(key = "after:" + x.s("id")) { DayCard(x, e.i("weekIndex")) { dayId = x.s("id") } } }
            val banned = e.strings("banned")
            if (banned.isNotEmpty()) item(key = "banned") {
                Text("${banned.size} exercise${if (banned.size == 1) "" else "s"} banned from all programs: ${banned.joinToString(", ")}.",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp), color = cs.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
            }
        }
    }

    when (val sh = sheet) {
        is ESheet.Slot -> SlotSheet(vm, e, sh.dayId, sh.slot, { sheet = it }) { sheet = null }
        is ESheet.Swap -> EditSwapSheet(vm, sh.dayId, sh.slot) { sheet = null }
        is ESheet.Add -> AddExerciseSheet(vm, sh.dayId) { sheet = null }
        is ESheet.Day -> DaySheet(vm, e, sh.dayId, { sheet = it }) { sheet = null }
        is ESheet.RenameDay -> TextDialog("Rename day", sh.label, { vm.edit("renameDay", "dayId" to sh.dayId, "label" to it) }) { sheet = null }
        ESheet.Rename -> TextDialog("Rename program", e.s("name"), { vm.edit("rename", "name" to it) }) { sheet = null }
        null -> {}
    }
    why?.let { k -> d?.let { WhyLiftSheet(vm, e.s("programId"), it.s("id"), k, e.i("weekIndex")) { why = null } } }
    info?.let { id -> ExerciseSheet(vm, id) { info = null } }
    if (confirmDeleteDay && d != null) AlertDialog(onDismissRequest = { confirmDeleteDay = false }, title = { Text("Delete ${d.s("label")}?") },
        text = { Text("It comes out of the draft. Nothing changes until you save. Logged workouts stay in your history.") },
        confirmButton = { Button(onClick = { confirmDeleteDay = false; dayMenu = false; vm.edit("deleteDay", "dayId" to d.s("id")); dayId = null }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { confirmDeleteDay = false }) { Text("Cancel") } })
    if (leaving) AlertDialog(onDismissRequest = { leaving = false },
        title = { Text("Save your changes?") },
        text = { Text("Your edits are kept as a draft until you save or discard them.") },
        confirmButton = { Button(onClick = { leaving = false; save(); if (e.s("cycleId").isBlank()) vm.closeEditor() }) { Text("Save") } },
        dismissButton = { Row {
            TextButton(onClick = { leaving = false; vm.discardEdits(); vm.closeEditor() }) { Text("Discard") }
            TextButton(onClick = { leaving = false; vm.closeEditor() }) { Text("Keep draft") }
        } })
    if (scopeAsk) ModalBottomSheet(onDismissRequest = { scopeAsk = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        val n = days.count { it.b("edited") }
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.padding(horizontal = 4.dp)) {
                Text(if (n > 0) "Save $n ${if (n == 1) "day" else "days"}" else "Save changes", style = MaterialTheme.typography.headlineSmall.copy(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold))
                Text("This program is part of a training cycle.", style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp), color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            }
            Column(Modifier.clip(RoundedCornerShape(24.dp)), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                listOf(Triple("program", "This block", "Only this block changes. The next blocks keep their own picks."),
                    Triple("cycle", "Every block", "Every block in the cycle gets these lifts. Sets and reps still follow each block’s own ramp.")).forEach { (sc, t, sub) ->
                    Column(Modifier.fillMaxWidth().background(cs.surfaceContainerHigh).clickable { scopeAsk = false; vm.saveEdits(sc) }
                        .semantics(mergeDescendants = true) { role = Role.Button }.padding(horizontal = 16.dp, vertical = 14.dp)) {
                        Text(t, style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold))
                        Text(sub, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp), color = cs.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

private fun shortDay(label: String) = label.split(" — ", " · ", " - ").first().let { Regex("^(Push|Pull|Legs|Upper|Lower|Full)").find(it)?.value ?: it }

/** Another day of the week, folded: weekday, name, its size this week and the first lifts; tap to open it. */
@Composable
private fun DayCard(x: JSONObject, week: Int, onOpen: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val exs = x.list("exercises")
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(cs.surfaceContainerLow).clickable(onClick = onOpen)
        .semantics(mergeDescendants = true) { role = Role.Button; contentDescription = "${x.s("weekday")} ${x.s("label")}. Open" }
        .padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(cs.surfaceContainerHigh), contentAlignment = Alignment.Center) {
            Text(x.s("weekday").ifBlank { "D${x.i("index") + 1}" }, style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.ExtraBold))
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(x.s("label"), style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (x.b("edited")) Box(Modifier.size(7.dp).clip(CircleShape).background(cs.primary).semantics { contentDescription = "edited" })
            }
            Text("${exs.size} lifts · ${fmtNum(x.d("sets"))} sets" + (x.i("minutes").takeIf { it > 0 }?.let { " · ~$it min" } ?: ""),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = cs.onSurfaceVariant)
            Text(if (exs.isEmpty()) "No lifts yet" else exs.take(3).joinToString(" · ") { it.s("name") } + if (exs.size > 3) " +${exs.size - 3}" else "",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(PiIcons.ChevronDown, null, tint = cs.onSurfaceVariant, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun Cap(t: String) = Text(t, style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.4.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
private fun DayAct(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tone: Boolean = false, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.height(40.dp).clip(CircleShape).background(if (tone) cs.tertiaryContainer else cs.secondaryContainer).clickable(onClick = onClick)
        .semantics(mergeDescendants = true) { role = Role.Button }.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        val fg = if (tone) cs.onTertiaryContainer else cs.onSecondaryContainer
        Icon(icon, null, tint = fg, modifier = Modifier.size(16.dp))
        Text(label, style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold), color = fg)
    }
}

private fun dash(s: String) = s.replace(Regex("(\\d)-(\\d)"), "$1–$2")

/** One lift in the design's editor: number, name, what it is and last time, its targets, then Swap · progression · more. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ExerciseCard(vm: AppViewModel, e: JSONObject, dayId: String, x: JSONObject, count: Int, paired: Boolean, open: String?,
                         onToggle: (String) -> Unit, onWhy: () -> Unit, onInfo: () -> Unit, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val slot = x.i("slot")
    var alts by remember(x.s("id")) { mutableStateOf<List<JSONObject>?>(null) }
    LaunchedEffect(open == "swap", x.s("id")) { if (open == "swap") alts = vm.editSwapOptions(dayId, slot) }
    var rmAsk by remember(x.s("id")) { mutableStateOf(false) }
    var repsEdit by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    // An opened panel (swap, progression, more) scrolls into view once it has expanded.
    val bring = remember { androidx.compose.foundation.relocation.BringIntoViewRequester() }
    LaunchedEffect(open) { if (open != null) { kotlinx.coroutines.delay(320); bring.bringIntoView() } }
    val tertiary = cs.tertiary
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(cs.surfaceContainerLow)
        .then(if (paired) Modifier.drawBehind { drawRect(tertiary, size = androidx.compose.ui.geometry.Size(4.dp.toPx(), size.height)) } else Modifier)
        .then(Modifier.bringIntoViewRequester(bring))
        .padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(28.dp).clip(RoundedCornerShape(10.dp)).background(cs.secondaryContainer), contentAlignment = Alignment.Center) {
                Text("${slot + 1}", style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold), color = cs.onSecondaryContainer)
            }
            Column(Modifier.weight(1f)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(x.s("name"), style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold))
                    if (x.b("primary")) Text("KEY LIFT", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.4.sp), color = cs.primary,
                        modifier = Modifier.align(Alignment.CenterVertically).border(1.dp, cs.primary, RoundedCornerShape(8.dp)).padding(horizontal = 7.dp, vertical = 2.dp))
                }
                val last = x.optJSONObject("last")
                Text(listOfNotNull(x.s("partLabel"), x.s("type"), last?.let { l -> "last " + (if (l.d("w") > 0) "${fmtNum(l.d("w"))} ${l.s("unit")}" else "BW") + " × ${l.i("r")}" }).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp), color = cs.onSurfaceVariant)
            }
            IconButton(onClick = { vm.edit("move", "dayId" to dayId, "slot" to slot, "dir" to -1) }, enabled = slot > 0, modifier = Modifier.size(40.dp)) { Icon(PiIcons.ChevronUp, "Move ${x.s("name")} earlier", Modifier.size(18.dp)) }
            IconButton(onClick = { vm.edit("move", "dayId" to dayId, "slot" to slot, "dir" to 1) }, enabled = slot < count - 1, modifier = Modifier.size(40.dp)) { Icon(PiIcons.ChevronDown, "Move ${x.s("name")} later", Modifier.size(18.dp)) }
        }
        x.optJSONObject("plateau")?.takeIf { it.i("since") >= 3 }?.let { pl ->
            val ch = x.s("plateauChoice").takeIf { it.isNotBlank() && it != "null" }
            val label = "PLATEAU · ${pl.i("since")} sessions · " + when (ch) { "lighter" -> "10% LIGHTER NEXT"; "reps" -> "REPS FIRST NEXT"; "swap" -> "VARIATION NEXT"; else -> "LOAD HELD" }
            Text(label, style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.3.sp),
                color = if (ch != null) cs.onTertiary else cs.onTertiaryContainer,
                modifier = Modifier.clip(CircleShape).background(if (ch != null) cs.tertiary else cs.tertiaryContainer)
                    .clickable { vm.setPlateauChoice(e.s("programId"), dayId, x.s("id"), if (ch == null) "lighter" else null) }
                    .semantics { role = Role.Button; contentDescription = if (ch == null) "Plateau for ${pl.i("since")} sessions. Tap to go about 10% lighter next session" else "Cancel the plateau choice for ${x.s("name")}" }
                    .padding(horizontal = 10.dp, vertical = 7.dp))
        }
        val rir = x.s("rir").takeIf { it.isNotBlank() && it != "null" }?.let { "@ ${dash(it)} RIR" }
        if (e.b("manual")) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.clip(CircleShape).background(cs.surfaceContainerHigh).padding(3.dp).semantics { contentDescription = "Sets for ${x.s("name")}" }, verticalAlignment = Alignment.CenterVertically) {
                RoundBtn("−", "One fewer set") { vm.edit("override", "dayId" to dayId, "slot" to slot, "field" to "sets", "value" to (x.i("sets") - 1).coerceAtLeast(1)) }
                Text("${x.i("sets")} sets", style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold), textAlign = TextAlign.Center, modifier = Modifier.widthIn(min = 54.dp))
                RoundBtn("+", "One more set") { vm.edit("override", "dayId" to dayId, "slot" to slot, "field" to "sets", "value" to (x.i("sets") + 1).coerceAtMost(20)) }
            }
            Box(Modifier.height(42.dp).clip(CircleShape).background(cs.surfaceContainerHigh).clickable { repsEdit = true }.semantics { role = Role.Button; contentDescription = "Rep range ${x.s("reps")}. Change" }
                .padding(horizontal = 14.dp), contentAlignment = Alignment.Center) { Text("${dash(x.s("reps"))} reps", style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold)) }
            if (rir != null) Text(rir, style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold), color = cs.primary)
        } else Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("${x.i("sets")} × ${dash(x.s("reps"))}", style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, fontWeight = FontWeight.SemiBold))
            if (rir != null) Text(rir, style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold), color = cs.primary, modifier = Modifier.padding(bottom = 2.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.height(42.dp).clip(CircleShape).background(cs.secondaryContainer).clickable { onToggle("swap") }
                .semantics(mergeDescendants = true) { role = Role.Button; contentDescription = "Swap ${x.s("name")}"; stateDescription = if (open == "swap") "Expanded" else "Collapsed" }.padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(PiIcons.Swap, null, tint = cs.onSecondaryContainer, modifier = Modifier.size(16.dp))
                Text(if (open == "swap") "Close" else "Swap", style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold), color = cs.onSecondaryContainer)
            }
            Row(Modifier.weight(1f).height(42.dp).clip(CircleShape).background(cs.surfaceContainerHigh).clickable(enabled = !e.b("percent")) { onToggle("prog") }
                .semantics(mergeDescendants = true) { role = Role.Button; contentDescription = "How ${x.s("name")} progresses: ${x.s("styleLabel")}" }.padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)) {
                Icon(PiIcons.Trend, null, modifier = Modifier.size(15.dp))
                Text(x.s("styleLabel").ifBlank { "Auto" },
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Box(Modifier.size(42.dp).clip(CircleShape).background(cs.surfaceContainerHigh).clickable { onToggle("more") }
                .semantics { role = Role.Button; contentDescription = "More options for ${x.s("name")}" }, contentAlignment = Alignment.Center) { Icon(PiIcons.MoreH, null, Modifier.size(18.dp)) }
        }
        AnimatedVisibility(open == "swap", enter = Motion.expandIn, exit = Motion.shrinkOut) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val a = alts
                when {
                    a == null -> Text("Finding alternatives…", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    a.isEmpty() -> Text("No other lift for this muscle at your gym.", style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp), color = cs.onSurfaceVariant)
                    else -> FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        a.take(8).forEach { o ->
                            Row(Modifier.height(40.dp).clip(RoundedCornerShape(12.dp)).background(cs.surface).border(1.dp, cs.outlineVariant, RoundedCornerShape(12.dp))
                                .clickable { vm.edit("replace", "dayId" to dayId, "slot" to slot, "newId" to o.s("id")); onToggle("swap") }
                                .semantics(mergeDescendants = true) { role = Role.Button }.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(o.s("name"), style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold))
                                if (o.s("equip").isNotBlank()) Text(" · ${o.s("equip")}", style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp), color = cs.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
        AnimatedVisibility(open == "prog", enter = Motion.expandIn, exit = Motion.shrinkOut) {
            Column(Modifier.clip(RoundedCornerShape(18.dp)), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                e.list("styles").forEach { o ->
                    val on = o.s("id") == x.s("style")
                    Column(Modifier.fillMaxWidth().background(if (on) cs.secondaryContainer else cs.surface)
                        .clickable { if (!on) scope.launch { if (vm.setLiftProgression(e.s("programId"), x.s("id"), o.s("id")) != null) vm.refreshEditor() }; onToggle("prog") }
                        .semantics(mergeDescendants = true) { role = Role.RadioButton; selected = on }.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        Text(o.s("label"), style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold), color = if (on) cs.onSecondaryContainer else cs.onSurface)
                        if (o.s("sub").isNotBlank() && o.s("sub") != o.s("label")) Text(o.s("sub"), style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
                            color = (if (on) cs.onSecondaryContainer else cs.onSurface).copy(alpha = 0.8f))
                    }
                }
            }
        }
        AnimatedVisibility(open == "more", enter = Motion.expandIn, exit = Motion.shrinkOut) {
            Column(Modifier.clip(RoundedCornerShape(18.dp)), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (slot < count - 1) MoreRow(PiIcons.Layers, if (x.b("superset")) "Split the superset" else "Superset with the next lift", "Pair with the next exercise, alternating sets") { vm.edit("superset", "dayId" to dayId, "slot" to slot); onToggle("more") }
                MoreRow(PiIcons.Help, "Why this lift?", "See why the program selected this exercise") { onWhy(); onToggle("more") }
                MoreRow(PiIcons.Info, "Exercise info", "Muscles worked, equipment, form demo") { onInfo(); onToggle("more") }
                MoreRow(PiIcons.Ban, "Ban exercise", "Never auto-pick this again, then pick a swap", tone = true) { vm.setBan(x.s("id"), true); onToggle("swap") }
                if (count > 1) {
                    if (rmAsk) Row(Modifier.fillMaxWidth().background(cs.surface).padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Remove ${x.s("name")} from this day?", style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
                        TextButton(onClick = { rmAsk = false }) { Text("Cancel") }
                        Button(onClick = { rmAsk = false; vm.edit("remove", "dayId" to dayId, "slot" to slot); onToggle("more") }, colors = ButtonDefaults.buttonColors(containerColor = cs.tertiary, contentColor = cs.onTertiary)) { Text("Remove") }
                    } else MoreRow(PiIcons.Trash, "Remove from day", "Take this exercise out of the workout", tone = true) { rmAsk = true }
                }
            }
        }
    }
    if (repsEdit) TextDialog("Rep range", x.s("reps"), { vm.edit("override", "dayId" to dayId, "slot" to slot, "field" to "reps", "value" to it) }) { repsEdit = false }
}

@Composable
private fun RoundBtn(t: String, desc: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(Modifier.size(36.dp).clip(CircleShape).background(cs.surface).clickable(onClick = onClick).semantics { contentDescription = desc; role = Role.Button }, contentAlignment = Alignment.Center) {
        Text(t, fontSize = 18.sp, color = cs.onSurface)
    }
}

@Composable
private fun MoreRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, sub: String, tone: Boolean = false, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().background(cs.surface).clickable(onClick = onClick).semantics(mergeDescendants = true) { role = Role.Button }.padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, null, tint = if (tone) cs.tertiary else cs.onSurface, modifier = Modifier.size(18.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold), color = if (tone) cs.tertiary else cs.onSurface)
            Text(sub, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp), color = cs.onSurfaceVariant)
        }
    }
}

@Composable
private fun TextDialog(title: String, initial: String, onSave: (String) -> Unit, onClose: () -> Unit) {
    var v by remember { mutableStateOf(initial) }
    AlertDialog(onDismissRequest = onClose, title = { Text(title) },
        text = { OutlinedTextField(v, { v = it }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { Button(onClick = { onSave(v); onClose() }, enabled = v.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } })
}

@Composable
private fun SlotSheet(vm: AppViewModel, e: JSONObject, dayId: String, slot: Int, open: (ESheet) -> Unit, onClose: () -> Unit) {
    val d = e.list("days").firstOrNull { it.s("id") == dayId } ?: run { onClose(); return }
    val x = d.list("exercises").getOrNull(slot) ?: run { onClose(); return }
    val count = d.list("exercises").size
    val cs = MaterialTheme.colorScheme
    ModalBottomSheet(onDismissRequest = onClose) {
        Column(Modifier.padding(horizontal = 12.dp).padding(bottom = 28.dp)) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Kicker("${d.s("label")} · exercise ${slot + 1} of $count")
                Text(x.s("name"), style = MaterialTheme.typography.headlineSmall)
            }
            if (e.b("manual")) PiCard(color = cs.surfaceContainer, radius = 20.dp, padding = 14.dp) {
                Text("Your prescription", style = MaterialTheme.typography.titleSmall)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = { vm.edit("override", "dayId" to dayId, "slot" to slot, "field" to "sets", "value" to (x.i("sets") - 1).coerceAtLeast(1)) }) { Icon(PiIcons.Minus, "Fewer sets") }
                    Text("${x.i("sets")} sets", style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(64.dp))
                    IconButton(onClick = { vm.edit("override", "dayId" to dayId, "slot" to slot, "field" to "sets", "value" to (x.i("sets") + 1).coerceAtMost(20)) }) { Icon(PiIcons.Plus, "More sets") }
                    var reps by remember(x.s("reps")) { mutableStateOf(x.s("reps")) }
                    OutlinedTextField(reps, { reps = it.take(9); vm.edit("override", "dayId" to dayId, "slot" to slot, "field" to "reps", "value" to reps) }, Modifier.weight(1f), singleLine = true, label = { Text("Reps") })
                }
            }
            Column(Modifier.clip(RoundedCornerShape(22.dp)), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                MenuRow(PiIcons.Swap, "Swap exercise", "Every week of this program", cs.secondaryContainer, cs.onSecondaryContainer) { open(ESheet.Swap(dayId, slot)) }
                if (slot > 0) MenuRow(PiIcons.ChevronLeft, "Move earlier", "The primary lift stays primary", cs.secondaryContainer, cs.onSecondaryContainer) { vm.edit("move", "dayId" to dayId, "slot" to slot, "dir" to -1); onClose() }
                if (slot < count - 1) MenuRow(PiIcons.ChevronRight, "Move later", "The primary lift stays primary", cs.secondaryContainer, cs.onSecondaryContainer) { vm.edit("move", "dayId" to dayId, "slot" to slot, "dir" to 1); onClose() }
                if (slot < count - 1) MenuRow(PiIcons.Redo, if (x.b("superset")) "Unpair from the next" else "Superset with the next", "Alternate sets to save time", cs.tertiaryContainer, cs.onTertiaryContainer) { vm.edit("superset", "dayId" to dayId, "slot" to slot); onClose() }
                if (count > 1) MenuRow(PiIcons.Trash, "Remove", "Later lifts move up", cs.errorContainer, cs.onErrorContainer) { vm.edit("remove", "dayId" to dayId, "slot" to slot); onClose() }
            }
        }
    }
}

@Composable
private fun EditSwapSheet(vm: AppViewModel, dayId: String, slot: Int, onClose: () -> Unit) {
    var opts by remember { mutableStateOf<List<JSONObject>?>(null) }
    LaunchedEffect(dayId, slot) { opts = vm.editSwapOptions(dayId, slot) }
    val cs = MaterialTheme.colorScheme
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Swap for", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 4.dp))
            Text("Same muscle, ranked for this slot, from your gym. Banned lifts are hidden.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
            val o = opts
            when {
                o == null -> Text("Finding alternatives…")
                o.isEmpty() -> Text("No alternatives with your equipment.")
                else -> LazyColumn(Modifier.clip(RoundedCornerShape(22.dp)).heightIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    items(o) { x ->
                        Row(Modifier.fillMaxWidth().background(cs.surfaceContainerHigh).clickable { vm.edit("replace", "dayId" to dayId, "slot" to slot, "newId" to x.s("id")); onClose() }.padding(16.dp)) {
                            Text(x.s("name"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            Icon(PiIcons.Swap, null, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddExerciseSheet(vm: AppViewModel, dayId: String, onClose: () -> Unit) {
    var creating by remember { mutableStateOf(false) }
    if (creating) { CreateExerciseSheet(vm, dayId) { creating = false; onClose() }; return }
    var q by remember { mutableStateOf("") }
    var part by remember { mutableStateOf<String?>(null) }
    var res by remember { mutableStateOf<JSONObject?>(null) }
    LaunchedEffect(q, part) { res = vm.editAddOptions(dayId, q, part) }
    val cs = MaterialTheme.colorScheme
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Add exercise", style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Search") })
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                item { MorphChip("All", part == null, { part = null }, height = 36.dp) }
                items(res.list("parts")) { p -> MorphChip(p.s("label"), part == p.s("id"), { part = if (part == p.s("id")) null else p.s("id") }, height = 36.dp) }
            }
            val items = res.list("items")
            if (res != null && items.isEmpty()) Text("Nothing left to add for that with your equipment.", color = cs.onSurfaceVariant)
            TextButton(onClick = { creating = true }) { Icon(PiIcons.Plus, null, modifier = Modifier.size(18.dp)); Text("  Create a custom exercise") }
            LazyColumn(Modifier.clip(RoundedCornerShape(22.dp)).heightIn(max = 460.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(items) { x ->
                    Row(Modifier.fillMaxWidth().background(cs.surfaceContainerHigh).clickable { vm.edit("add", "dayId" to dayId, "exId" to x.s("id")); vm.toast("Added ${x.s("name")}"); onClose() }.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(x.s("name"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        Icon(PiIcons.Plus, null, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun DaySheet(vm: AppViewModel, e: JSONObject, dayId: String, open: (ESheet) -> Unit, onClose: () -> Unit) {
    val days = e.list("days")
    val d = days.firstOrNull { it.s("id") == dayId } ?: run { onClose(); return }
    val idx = days.indexOf(d)
    val cs = MaterialTheme.colorScheme
    var confirmDelete by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onClose) {
        Column(Modifier.padding(horizontal = 12.dp).padding(bottom = 28.dp)) {
            Text(d.s("label"), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
            Column(Modifier.clip(RoundedCornerShape(22.dp)), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                MenuRow(PiIcons.List, "Rename", "What this day is called", cs.secondaryContainer, cs.onSecondaryContainer) { open(ESheet.RenameDay(dayId, d.s("label"))) }
                MenuRow(PiIcons.Plus, "Duplicate day", "Same lifts and settings, right after it", cs.secondaryContainer, cs.onSecondaryContainer) { vm.edit("copyDay", "dayId" to dayId); onClose() }
                if (idx > 0) MenuRow(PiIcons.ChevronLeft, "Move earlier", "In the weekly order", cs.secondaryContainer, cs.onSecondaryContainer) { vm.edit("moveDay", "dayId" to dayId, "dir" to -1); onClose() }
                if (idx < days.size - 1) MenuRow(PiIcons.ChevronRight, "Move later", "In the weekly order", cs.secondaryContainer, cs.onSecondaryContainer) { vm.edit("moveDay", "dayId" to dayId, "dir" to 1); onClose() }
                if (days.size > 1) MenuRow(PiIcons.Trash, "Delete day", "Logged workouts stay in your history", cs.errorContainer, cs.onErrorContainer) { confirmDelete = true }
            }
        }
    }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("Delete ${d.s("label")}?") },
        text = { Text("It comes out of the draft. Nothing changes until you save.") },
        confirmButton = { Button(onClick = { confirmDelete = false; vm.edit("deleteDay", "dayId" to dayId); onClose() }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } })
}

