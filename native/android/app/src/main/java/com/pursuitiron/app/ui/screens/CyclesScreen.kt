@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.pursuitiron.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pursuitiron.app.AppViewModel
import com.pursuitiron.app.ui.common.Kicker
import com.pursuitiron.app.ui.common.MorphChip
import com.pursuitiron.app.ui.common.PiCard
import com.pursuitiron.app.ui.common.PiIcons
import com.pursuitiron.app.ui.common.ProgressBar
import com.pursuitiron.app.ui.common.b
import com.pursuitiron.app.ui.common.d
import com.pursuitiron.app.ui.common.i
import com.pursuitiron.app.ui.common.list
import com.pursuitiron.app.ui.common.o
import com.pursuitiron.app.ui.common.s
import com.pursuitiron.app.ui.common.strings
import org.json.JSONArray
import org.json.JSONObject
import java.text.DateFormat
import java.util.Date

/** The blocks of a cycle as a row of segments: done, current, upcoming. Shape and text carry the state, not colour alone. */
@Composable
fun BlockStrip(blocks: List<JSONObject>, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        blocks.forEach { b ->
            val st = b.s("state")
            val w = b.i("total").coerceAtLeast(1).toFloat()
            Column(Modifier.weight(w), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.fillMaxWidth().height(if (st == "active") 10.dp else 6.dp).clip(CircleShape)
                    .background(when (st) { "done" -> cs.primary; "active" -> cs.primary.copy(alpha = 0.55f); else -> cs.surfaceContainerHighest }))
                Text(b.s("label"), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = if (st == "upcoming") cs.onSurfaceVariant else cs.onSurface)
            }
        }
    }
}

/** Plan tab: the cycle this program belongs to. */
@Composable
fun CycleBanner(vm: AppViewModel, cycleId: String, onOpen: () -> Unit) {
    val c = vm.cycles.list("cycles").firstOrNull { it.s("id") == cycleId } ?: return
    val cs = MaterialTheme.colorScheme
    val live = c.o("live")
    PiCard(color = cs.secondaryContainer, radius = 28.dp, onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Kicker("Training cycle", cs.onSecondaryContainer)
                Text(c.s("name"), style = MaterialTheme.typography.titleLarge, color = cs.onSecondaryContainer)
            }
            Icon(PiIcons.ChevronRight, "Open cycle", tint = cs.onSecondaryContainer)
        }
        BlockStrip(c.list("blocks"))
        val blocks = c.list("blocks")
        Text(buildString {
            append("Block ${c.i("activeBlock") + 1} of ${blocks.size}")
            if (live != null) append(if (live.b("isDeload")) " · deload week" else " · week ${live.i("weekIndex")} of ${live.i("weeksTotal")}")
            append(" · ${c.i("pct")}% through")
        }, style = MaterialTheme.typography.bodySmall, color = cs.onSecondaryContainer)
    }
}

/** Plan tab: every cycle, newest first. */
@Composable
fun CyclesList(vm: AppViewModel, onOpen: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val list = vm.cycles.list("cycles")
    Column(Modifier.clip(RoundedCornerShape(24.dp)), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        list.forEach { c ->
            Row(Modifier.fillMaxWidth().background(cs.surfaceContainerLow).clickable { onOpen(c.s("id")) }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(c.s("name"), style = MaterialTheme.typography.titleSmall)
                    Text(if (c.b("done")) "Complete · ${c.list("blocks").size} blocks" else "Block ${c.i("activeBlock") + 1} of ${c.list("blocks").size} · ${c.i("pct")}%" + if (c.has("live") && !c.isNull("live")) " · training now" else "",
                        style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
                Icon(PiIcons.ChevronRight, null, modifier = Modifier.size(18.dp))
            }
        }
        Row(Modifier.fillMaxWidth().background(cs.surfaceContainerLow).clickable { vm.openSetup(cycle = true) }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(PiIcons.Plus, null, tint = cs.primary); Text("  New training cycle", style = MaterialTheme.typography.titleSmall, color = cs.primary)
        }
    }
}

/* ------------------------------------------------------------------ cycle detail (App.js CycleDetail) */
@Composable
fun CycleSheet(vm: AppViewModel, id: String, onClose: () -> Unit) {
    var d by remember { mutableStateOf<JSONObject?>(null) }
    var confirmAdvance by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var review by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(id, vm.cycles) { d = vm.cycleDetail(id) }
    val cs = MaterialTheme.colorScheme
    review?.let { idx -> BlockReviewSheet(vm, id, idx) { review = null }; return }
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        val c = d
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (c == null) { Text("Loading…"); return@Column }
            Kicker(if (c.b("done")) "Cycle complete" else "Training cycle")
            Text(c.s("name"), style = MaterialTheme.typography.headlineMedium)
            val df = DateFormat.getDateInstance(DateFormat.MEDIUM)
            val start = c.optLong("startMs"); val end = c.optLong("endMs")
            if (start > 0) Text("Started ${df.format(Date(start))}" + if (!c.b("done") && end > 0) " · estimated finish ${df.format(Date(end))}" else "", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("${c.i("pct")}%", style = MaterialTheme.typography.displaySmall, color = cs.primary)
                Text("Week ${c.i("curWeek")} of ${c.i("totalWeeks")} · ${c.i("sessions")} sessions logged", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
            }
            ProgressBar(c.i("pct") / 100f, height = 8.dp)
            if (c.b("adapts")) Text("Future blocks adapt from your training history.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            Kicker("Blocks", modifier = Modifier.padding(top = 4.dp))
            Column(Modifier.clip(RoundedCornerShape(22.dp)), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                c.list("blocks").forEach { b ->
                    val st = b.s("state")
                    Row(Modifier.fillMaxWidth().background(if (st == "active") cs.primaryContainer else cs.surfaceContainerHigh).padding(horizontal = 14.dp, vertical = 12.dp)
                        .semantics(mergeDescendants = true) {},
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(32.dp).clip(if (st == "active") RoundedCornerShape(10.dp) else CircleShape)
                            .background(when (st) { "done" -> cs.primary; "active" -> cs.primary; else -> cs.surfaceContainerHighest }), contentAlignment = Alignment.Center) {
                            if (st == "done") Icon(PiIcons.Check, null, tint = cs.onPrimary, modifier = Modifier.size(16.dp))
                            else Text("${b.i("index") + 1}", style = MaterialTheme.typography.labelLarge, color = if (st == "active") cs.onPrimary else cs.onSurfaceVariant)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(b.s("label") + when (st) { "active" -> " · now"; "done" -> " · done"; else -> "" }, style = MaterialTheme.typography.titleSmall,
                                color = if (st == "active") cs.onPrimaryContainer else cs.onSurface)
                            Text(listOfNotNull("${b.i("weeks")} weeks" + if (b.b("deload")) " + deload" else "", b.i("days").takeIf { it > 0 }?.let { "$it days/wk" },
                                b.i("sessions").takeIf { it > 0 }?.let { "$it logged" }).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall, color = if (st == "active") cs.onPrimaryContainer else cs.onSurfaceVariant)
                            if (b.s("note").isNotBlank()) Text(b.s("note"), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        if (st != "upcoming" && b.i("sessions") > 0) TextButton(onClick = { review = b.i("index") }) { Text("Review") }
                    }
                }
            }
            c.o("changes")?.let { ch ->
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(cs.surfaceContainer).padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Kicker("What changes next block")
                    Text("${ch.i("retained")} lifts kept · ${ch.optJSONArray("added")?.length() ?: 0} added · ${ch.optJSONArray("removed")?.length() ?: 0} removed", style = MaterialTheme.typography.titleSmall)
                    val before = ch.o("before"); val after = ch.o("after")
                    if (before != null && after != null) Text("Opening-week sets: ${before.i("sets")} → ${after.i("sets")}. Rep ranges: ${before.s("repRange")} → ${after.s("repRange")}.",
                        style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    val added = ch.strings("added"); if (added.isNotEmpty()) Text("New: ${added.joinToString(", ")}", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    Text("These are planned targets. The next block may adapt from your completed workouts.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
            }
            if (!c.b("done")) Button(onClick = { confirmAdvance = true }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text(if (c.b("final")) "Complete final block" else "Complete block · advance to ${c.s("nextLabel").ifBlank { "the next block" }}", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) { Text("Delete cycle") }
        }
    }
    if (confirmAdvance) d?.let { c ->
        AlertDialog(onDismissRequest = { confirmAdvance = false },
            title = { Text(if (c.b("final")) "Finish this cycle?" else "Move to ${c.s("nextLabel").ifBlank { "the next block" }}?") },
            text = { Text("This can't be undone. The next block is prepared from the workouts you logged in this one. Your history and programs stay saved." +
                if (c.b("final") && c.s("onComplete") == "loop") " A fresh cycle is built to follow it." else "") },
            confirmButton = { Button(onClick = { confirmAdvance = false; vm.advanceCycle(id) }) { Text(if (c.b("final")) "Complete cycle" else "Advance") } },
            dismissButton = { TextButton(onClick = { confirmAdvance = false }) { Text("Not yet") } })
    }
    if (confirmDelete) d?.let { c ->
        AlertDialog(onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${c.s("name")}?") },
            text = { Text("Keep its ${c.list("blocks").size} blocks as standalone programs, or delete them too. Logged workouts are never deleted.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.deleteCycle(id, false); onClose() }) { Text("Keep blocks") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false; vm.deleteCycle(id, true); onClose() }) { Text("Delete blocks too") } })
    }
}

/* ------------------------------------------------------------------ block review (App.js BlockReviewView) */
@Composable
fun BlockReviewSheet(vm: AppViewModel, id: String, index: Int, onClose: () -> Unit) {
    var r by remember { mutableStateOf<JSONObject?>(null) }
    LaunchedEffect(id, index) { r = vm.reviewBlock(id, index) }
    val cs = MaterialTheme.colorScheme
    val df = DateFormat.getDateInstance(DateFormat.MEDIUM)
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        val x = r
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (x == null) { Text("Loading…"); return@Column }
            Text("${x.s("label")} review", style = MaterialTheme.typography.headlineSmall)
            Text(x.s("cycleName") + if (x.b("ok")) " · ${df.format(Date(x.optLong("from")))} to ${df.format(Date(x.optLong("to")))}" else "", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            if (!x.b("ok")) {
                Text("Not enough logged to review", style = MaterialTheme.typography.titleMedium)
                Text(x.s("reason"), color = cs.onSurfaceVariant)
                return@Column
            }
            PiCard(color = cs.surfaceContainer, radius = 22.dp) {
                Kicker("How it went")
                Text(x.s("headline"), style = MaterialTheme.typography.titleMedium)
                if (x.s("note").isNotBlank()) Text("Block intent: ${x.s("note")}", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
            val lifts = x.list("lifts")
            if (lifts.isNotEmpty()) {
                Kicker("Lifts")
                lifts.forEach { l ->
                    val st = l.s("state"); val pct = l.d("deltaPct")
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(cs.surfaceContainerHigh).padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(l.s("name"), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${l.i("sessions")} session${if (l.i("sessions") == 1) "" else "s"}" + if (st == "thin") " · too few to judge" else "", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                        }
                        Text((if (pct > 0) "+" else "") + com.pursuitiron.app.ui.common.fmtNum(pct) + "%", style = MaterialTheme.typography.titleSmall,
                            color = when (st) { "progressed" -> cs.primary; "regressed" -> cs.error; else -> cs.onSurfaceVariant })
                    }
                }
            }
            val muscles = x.list("muscles")
            if (muscles.isNotEmpty()) {
                Kicker("Volume vs outcome")
                muscles.forEach { m ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(m.s("part").replace('_', ' ').replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text("${com.pursuitiron.app.ui.common.fmtNum(m.d("avgSets"))} sets/wk · ${m.s("zone")}" + (m.s("drove").takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""), style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                    }
                }
            }
            x.optJSONArray("recs")?.let { a -> if (a.length() > 0) {
                Kicker("Next time")
                (0 until a.length()).mapNotNull { a.optJSONObject(it) }.forEach { v ->
                    Text(v.s("part").replace('_', ' ').replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.titleSmall)
                    Text(v.s("rec"), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                }
            } }
        }
    }
}

/* ------------------------------------------------------------------ cycle setup (App.js Wizard cycle mode + CustomCycleComposer) */
data class CustomBlock(val phase: String, val weeks: Int)

@Composable
fun CycleTemplatePicker(vm: AppViewModel, template: String, onTemplate: (String) -> Unit, custom: SnapshotStateList<CustomBlock>, adapt: Boolean, onAdapt: (Boolean) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val v = vm.cycles
    val phases = v.list("phases")
    Kicker("Cycle")
    v.list("templates").forEach { t ->
        val on = template == t.s("id")
        Surface(onClick = { onTemplate(t.s("id")) }, color = if (on) cs.primary else cs.surfaceContainerHigh, contentColor = if (on) cs.onPrimary else cs.onSurface,
            shape = RoundedCornerShape(if (on) 18.dp else 22.dp), modifier = Modifier.fillMaxWidth().semantics { role = Role.RadioButton }) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row { Text(t.s("name"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f)); Text(t.s("tag"), style = MaterialTheme.typography.labelMedium) }
                Text(t.s("blurb"), style = MaterialTheme.typography.bodySmall)
                Text(t.list("blocks").joinToString("  →  ") { "${it.s("label")} ${it.i("weeks")}w" }, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
    val onCustom = template == "custom"
    Surface(onClick = { onTemplate("custom") }, color = if (onCustom) cs.primary else cs.surfaceContainerHigh, contentColor = if (onCustom) cs.onPrimary else cs.onSurface,
        shape = RoundedCornerShape(if (onCustom) 18.dp else 22.dp), modifier = Modifier.fillMaxWidth().semantics { role = Role.RadioButton }) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text("Custom cycle", style = MaterialTheme.typography.titleSmall)
            Text("Choose every phase and how long it runs.", style = MaterialTheme.typography.bodySmall)
        }
    }
    if (onCustom) {
        custom.forEachIndexed { i, b ->
            PiCard(color = cs.surfaceContainer, radius = 22.dp, padding = 14.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Block ${i + 1}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    IconButton(onClick = { if (b.weeks > 1) custom[i] = b.copy(weeks = b.weeks - 1) }) { Icon(PiIcons.Minus, "Fewer weeks in block ${i + 1}") }
                    Text("${b.weeks} wk", style = MaterialTheme.typography.titleSmall, modifier = Modifier.width(44.dp))
                    IconButton(onClick = { if (b.weeks < 24) custom[i] = b.copy(weeks = b.weeks + 1) }) { Icon(PiIcons.Plus, "More weeks in block ${i + 1}") }
                    if (custom.size > 2) IconButton(onClick = { custom.removeAt(i) }) { Icon(PiIcons.Trash, "Remove block ${i + 1}") }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    phases.forEach { p -> MorphChip(p.s("label"), b.phase == p.s("phase"), { custom[i] = b.copy(phase = p.s("phase")) }, height = 36.dp) }
                }
            }
        }
        if (custom.size < 8) TextButton(onClick = { custom.add(CustomBlock(phases.firstOrNull()?.s("phase") ?: "hypertrophy_accumulation", 4)) }) { Text("+ Add a block") }
    }
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(cs.surfaceContainerHigh).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Adapt between blocks", style = MaterialTheme.typography.titleSmall)
            Text("Each block is rebuilt from how the last one went", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
        Switch(checked = adapt, onCheckedChange = onAdapt)
    }
}

fun customBlocksJson(list: List<CustomBlock>): JSONArray = JSONArray(list.map { JSONObject().put("phase", it.phase).put("weeks", it.weeks) })

