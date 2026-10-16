@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.pursuitiron.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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

/** App.js ProgramView, editing: every change goes to a draft and reaches the program on Save. */
@Composable
fun ProgramEditorScreen(vm: AppViewModel) {
    val e = vm.editor ?: return
    val cs = MaterialTheme.colorScheme
    val wide = LocalWide.current
    var sheet by remember { mutableStateOf<ESheet?>(null) }
    var leaving by remember { mutableStateOf(false) }
    var scopeAsk by remember { mutableStateOf(false) }
    val dirty = e.b("dirty")
    BackHandler { if (dirty) leaving = true else vm.closeEditor() }
    fun save() { if (e.s("cycleId").isNotBlank()) scopeAsk = true else vm.saveEdits("program") }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.padding(start = 4.dp, end = 12.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (dirty) leaving = true else vm.closeEditor() }) { Icon(PiIcons.ChevronLeft, "Back") }
            Column(Modifier.weight(1f).clickable { sheet = ESheet.Rename }) {
                Kicker(if (dirty) "Editing · unsaved" else "Editing")
                Text(e.s("name"), style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (dirty) TextButton(onClick = { vm.discardEdits() }) { Text("Discard") }
            Button(onClick = { save() }, enabled = dirty) { Text("Save") }
        }
        LazyRow(Modifier.padding(vertical = 6.dp), contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(e.i("weeksTotal").coerceAtLeast(1)) { w -> MorphChip("Week ${w + 1}", e.i("weekIndex") == w + 1, { vm.editWeek(w + 1) }) }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth().widthIn(max = 760.dp).align(Alignment.CenterHorizontally),
            contentPadding = PaddingValues(start = if (wide) 32.dp else 16.dp, end = if (wide) 32.dp else 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(e.list("days"), key = { it.s("id") }) { d ->
                PiCard(radius = 28.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(d.s("label"), style = MaterialTheme.typography.titleLarge)
                            Text("${d.list("exercises").size} exercises" + (d.i("minutes").takeIf { it > 0 }?.let { " · about $it min" } ?: ""), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                        }
                        IconButton(onClick = { sheet = ESheet.Day(d.s("id")) }) { Icon(PiIcons.More, "${d.s("label")} options") }
                    }
                    Column(Modifier.clip(RoundedCornerShape(20.dp)), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        d.list("exercises").forEach { x ->
                            Row(Modifier.fillMaxWidth().background(cs.surfaceContainerHigh).clickable { sheet = ESheet.Slot(d.s("id"), x.i("slot")) }
                                .semantics(mergeDescendants = true) { contentDescription = "${x.s("name")}, ${x.i("sets")} sets of ${x.s("reps")}" + (if (x.b("primary")) ", primary lift" else "") + (if (x.b("superset")) ", supersetted with the next" else "") + ". Edit" }
                                .padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(x.s("name"), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                                        if (x.b("primary")) Badge("Primary")
                                        if (x.b("added")) Badge("Added", cs.secondaryContainer, cs.onSecondaryContainer)
                                    }
                                    Text(x.s("partLabel"), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                                }
                                Text("${x.i("sets")} × ${x.s("reps")}", style = MaterialTheme.typography.labelLarge)
                                if (x.b("superset")) Box(Modifier.size(8.dp).clip(CircleShape).background(cs.tertiary))
                            }
                        }
                    }
                    TextButton(onClick = { sheet = ESheet.Add(d.s("id")) }) { Icon(PiIcons.Plus, null, modifier = Modifier.size(18.dp)); Text("  Add exercise") }
                }
            }
            item { OutlinedButton(onClick = { vm.edit("addDay") }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("+ Add a day") } }
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
    if (leaving) AlertDialog(onDismissRequest = { leaving = false },
        title = { Text("Save your changes?") },
        text = { Text("Your edits are kept as a draft until you save or discard them.") },
        confirmButton = { Button(onClick = { leaving = false; save(); if (e.s("cycleId").isBlank()) vm.closeEditor() }) { Text("Save") } },
        dismissButton = { Row {
            TextButton(onClick = { leaving = false; vm.discardEdits(); vm.closeEditor() }) { Text("Discard") }
            TextButton(onClick = { leaving = false; vm.closeEditor() }) { Text("Keep draft") }
        } })
    if (scopeAsk) AlertDialog(onDismissRequest = { scopeAsk = false },
        title = { Text("Apply to the whole cycle?") },
        text = { Text("Save this block only, or carry the same exercise changes to the other blocks in its cycle.") },
        confirmButton = { Button(onClick = { scopeAsk = false; vm.saveEdits("cycle") }) { Text("Whole cycle") } },
        dismissButton = { TextButton(onClick = { scopeAsk = false; vm.saveEdits("program") }) { Text("This block") } })
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

