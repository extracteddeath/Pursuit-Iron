@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.pursuitiron.app.ui.screens

import com.pursuitiron.app.ui.common.o
import com.pursuitiron.app.ui.common.has
import androidx.compose.foundation.layout.imePadding
import androidx.lifecycle.viewModelScope
import androidx.compose.foundation.background
import kotlinx.coroutines.launch
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pursuitiron.app.AppViewModel
import com.pursuitiron.app.ui.common.Kicker
import com.pursuitiron.app.ui.common.PiIcons
import com.pursuitiron.app.ui.common.b
import com.pursuitiron.app.ui.common.d
import com.pursuitiron.app.ui.common.fmtNum
import com.pursuitiron.app.ui.common.i
import com.pursuitiron.app.ui.common.list
import com.pursuitiron.app.ui.common.s
import com.pursuitiron.app.ui.common.strings
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/* The design's Plan chrome: the program pill that opens Programs, the action row, Programs and Program details. */

@Composable
fun PlanProgramHeader(p: JSONObject, onIcon: () -> Unit, onPrograms: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        com.pursuitiron.app.ui.common.IconTile(p.optJSONObject("icon"), 44.dp, Modifier.clickable(onClick = onIcon).semantics { contentDescription = "Change the icon for ${p.s("name")}"; role = Role.Button })
        Surface(onClick = onPrograms, color = Color.Transparent, shape = RoundedCornerShape(12.dp),
            modifier = Modifier.semantics { contentDescription = "Program: ${p.s("name")}. Switch or manage programs" }) {
            Row(Modifier.padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(p.s("name"), style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Box(Modifier.size(24.dp).clip(CircleShape).background(cs.surfaceContainerHigh), contentAlignment = Alignment.Center) { Icon(PiIcons.ChevronDown, null, modifier = Modifier.size(16.dp)) }
            }
        }
    }
}

/** The design's action row under the plan's title. */
@Composable
fun PlanActions(p: JSONObject, onEdit: () -> Unit, onReview: () -> Unit, onSchedule: () -> Unit, onCycle: (() -> Unit)?, onSettings: () -> Unit, onDetails: () -> Unit, onNew: () -> Unit, onTm: (() -> Unit)?) {
    val cs = MaterialTheme.colorScheme
    @Composable fun Act(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector?, primary: Boolean = false, tonal: Boolean = false, onClick: () -> Unit) {
        Surface(onClick = onClick, color = when { primary -> cs.primary; tonal -> cs.secondaryContainer; else -> cs.surfaceContainerHigh },
            contentColor = when { primary -> cs.onPrimary; tonal -> cs.onSecondaryContainer; else -> cs.onSurface }, shape = CircleShape, modifier = Modifier.height(40.dp)) {
            Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (icon != null) Icon(icon, null, modifier = Modifier.size(16.dp))
                Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
            }
        }
    }
    LazyRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(end = 16.dp)) {
        item { Act("Edit program", PiIcons.List, primary = true, onClick = onEdit) }
        item { Act("Review block", PiIcons.Progress, tonal = true, onClick = onReview) }
        item { Act("Schedule", PiIcons.Calendar, onClick = onSchedule) }
        if (onTm != null) item { Act("Training maxes", PiIcons.Calc, onClick = onTm) }
        if (onCycle != null) item { Act("Cycle", PiIcons.Redo, onClick = onCycle) }
        item { Act("Settings", PiIcons.Grid, onClick = onSettings) }
        item { Act("Details", PiIcons.Info, onClick = onDetails) }
        item { Act("New", PiIcons.Plus, onClick = onNew) }
    }
}

/** The design's Programs: every program, active first; tap one for its options; browse and add shared ones. */
@Composable
fun ProgramsSheet(vm: AppViewModel, onCompare: () -> Unit, onGallery: () -> Unit, onClose: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    var d by remember { mutableStateOf<JSONObject?>(null) }
    LaunchedEffect(vm.plan) { d = vm.programsList() }
    var sel by remember { mutableStateOf<String?>(null) }
    var addShared by remember { mutableStateOf(false) }
    if (addShared) { AddSharedProgramSheet(vm) { addShared = false; vm.viewModelScope.launch { d = vm.programsList() } }; return }
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Programs", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                Surface(onClick = { onClose(); onCompare() }, color = cs.surfaceContainerHigh, shape = CircleShape, modifier = Modifier.height(40.dp)) {
                    Box(Modifier.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) { Text("Compare", style = MaterialTheme.typography.labelLarge) }
                }
                Surface(onClick = { onClose(); vm.openSetup() }, color = cs.primary, contentColor = cs.onPrimary, shape = CircleShape, modifier = Modifier.height(40.dp)) {
                    Box(Modifier.padding(horizontal = 16.dp), contentAlignment = Alignment.Center) { Text("New", style = MaterialTheme.typography.labelLarge) }
                }
            }
            val x = d ?: run { Text("Loading…", color = cs.onSurfaceVariant); return@Column }
            Text("${x.i("count")} program${if (x.i("count") == 1) "" else "s"} · tap one for its options", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
            x.list("programs").forEach { pr ->
                val active = pr.b("active"); val open = sel == pr.s("id")
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(if (active) cs.primaryContainer else cs.surfaceContainerLow)
                    .clickable { sel = if (open) null else pr.s("id") }.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        com.pursuitiron.app.ui.common.IconTile(pr.optJSONObject("icon"), 48.dp)
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(pr.s("name"), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                                if (active) Text("ACTIVE", style = MaterialTheme.typography.labelSmall, color = cs.onPrimary, modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(cs.primary).padding(horizontal = 8.dp, vertical = 3.dp))
                            }
                            Text(pr.s("sub"), style = MaterialTheme.typography.bodySmall, color = if (active) cs.onPrimaryContainer else cs.onSurfaceVariant)
                            Text(pr.s("meta"), style = MaterialTheme.typography.labelMedium, color = if (active) cs.onPrimaryContainer else cs.onSurface)
                        }
                    }
                    if (open) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!active) Button(onClick = { vm.loadPlan(pr.s("id"), null); vm.pin(pr.s("id")); onClose() }) { Text("Train this") }
                        OutlinedButton(onClick = { onClose(); vm.openEditor(pr.s("id")) }) { Text("Edit") }
                        if (!active) TextButton(onClick = { vm.loadPlan(pr.s("id"), null); onClose() }) { Text("View") }
                        ProgramMenuButton(vm, pr.s("id"), pr.s("name"))
                    }
                }
            }
            Surface(onClick = { onClose(); onGallery() }, color = cs.surfaceContainerLow, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(cs.primaryContainer), contentAlignment = Alignment.Center) { Icon(PiIcons.Grid, null, tint = cs.primary) }
                    Column(Modifier.weight(1f)) {
                        Text("Browse shared programs", style = MaterialTheme.typography.titleSmall)
                        Text("Import a community program — yours to edit", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    }
                    Icon(PiIcons.ChevronRight, null, tint = cs.onSurfaceVariant)
                }
            }
            Surface(onClick = { addShared = true }, color = androidx.compose.ui.graphics.Color.Transparent, contentColor = cs.primary, shape = RoundedCornerShape(24.dp),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, cs.outlineVariant), modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Icon(PiIcons.Download, null, Modifier.size(18.dp)); Text("  Add a shared program", style = MaterialTheme.typography.titleSmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold))
                }
            }
        }
    }
}

/** The design's Add a shared program: paste a program code, see what it is, add it (the program you train stays). */
@Composable
fun AddSharedProgramSheet(vm: AppViewModel, onClose: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    var code by remember { mutableStateOf("") }
    var pv by remember { mutableStateOf<JSONObject?>(null) }
    LaunchedEffect(code) { pv = vm.previewProgramCode(code) }
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 16.dp).padding(bottom = 22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Add a shared program", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 4.dp))
            Kicker("Program code")
            androidx.compose.material3.TextField(code, { code = it.uppercase().take(48) }, Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("PI-XXXX-XXXX-XXXX") },
                shape = RoundedCornerShape(18.dp), keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Characters),
                colors = androidx.compose.material3.TextFieldDefaults.colors(focusedContainerColor = cs.surfaceContainerHigh, unfocusedContainerColor = cs.surfaceContainerHigh,
                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent, unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent))
            val p = pv
            if (code.isBlank()) TextButton(onClick = { vm.viewModelScope.launch { vm.galleryCodes().firstOrNull()?.let { code = it } } }) { Text("Paste a sample code") }
            else if (p != null && !p.b("ok") && p.s("error").isNotBlank()) Text("That doesn’t look like a program code. Codes look like PI-ABCD-1234-WXYZ.", style = MaterialTheme.typography.bodySmall,
                color = cs.onTertiaryContainer, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(cs.tertiaryContainer).padding(horizontal = 12.dp, vertical = 10.dp))
            if (p != null && p.b("ok")) {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(cs.surfaceContainerHigh).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(p.s("name"), style = MaterialTheme.typography.titleMedium)
                    Text(p.s("meta"), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    Text(p.s("equipment"), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
                Text("It joins your programs. The one you train now stays until you switch.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                Button(onClick = { vm.importProgramCode(p.s("code")) { onClose() } }, modifier = Modifier.fillMaxWidth().height(54.dp)) { Text("Add to my programs", style = MaterialTheme.typography.titleMedium) }
            }
        }
    }
}

/** The design's Program details: how it was built, muscle priorities, weekly volume, the block plan. */
@Composable
fun ProgramDetailsSheet(vm: AppViewModel, programId: String, onSettings: () -> Unit, onClose: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    var d by remember { mutableStateOf<JSONObject?>(null) }
    LaunchedEffect(programId) { d = vm.programDetails(programId) }
    ModalBottomSheet(onDismissRequest = onClose, sheetState = com.pursuitiron.app.ui.common.rememberSettlingSheet(d)) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Program details", style = MaterialTheme.typography.headlineSmall)
            val x = d ?: run { Text("Loading…", color = cs.onSurfaceVariant); return@Column }
            Text(x.s("name"), style = MaterialTheme.typography.titleMedium)
            Kicker("How this was built")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                x.list("chips").forEach { c ->
                    Row(Modifier.clip(RoundedCornerShape(10.dp)).background(if (c.b("accent")) cs.primaryContainer else cs.surfaceContainerHigh).padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(c.s("k"), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                        Text(c.s("v"), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            if (x.s("cycleLine").isNotBlank() && !x.isNull("cycleLine")) Text(x.s("cycleLine"), style = MaterialTheme.typography.bodySmall)
            val pri = x.list("priorities")
            if (pri.isNotEmpty()) {
                Kicker("Your muscle priorities · week ${x.i("week")}")
                Text("Estimated sets, including shared work from compound lifts.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                Column(Modifier.clip(RoundedCornerShape(20.dp)), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    pri.forEach { r -> Row(Modifier.fillMaxWidth().background(cs.surfaceContainerHigh).padding(horizontal = 14.dp, vertical = 12.dp)) {
                        Text(r.s("label"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f)); Text("${fmtNum(r.d("sets"))} sets", style = MaterialTheme.typography.labelLarge) } }
                }
            }
            WeekVolumeCard(vm, programId, x.i("week"))
            Kicker("Block plan")
            val df = SimpleDateFormat("MMM d", Locale.getDefault())
            Column(Modifier.clip(RoundedCornerShape(20.dp)), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                x.list("plan").forEach { w ->
                    Row(Modifier.fillMaxWidth().background(if (w.b("current")) cs.primaryContainer else cs.surfaceContainerHigh).padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("W${w.i("w")}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(32.dp))
                        Text(df.format(Date(w.optLong("date"))), style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant, modifier = Modifier.width(52.dp))
                        Text(w.s("note"), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Text("${w.i("sets")} sets", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            androidx.compose.material3.FilledTonalButton(onClick = { onClose(); onSettings() }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Program settings") }
        }
    }
}

/** The design's Routines on Plan: quick workouts you saved, one tap to repeat. */
@Composable
fun RoutinesSection(vm: AppViewModel) {
    val cs = MaterialTheme.colorScheme
    var d by remember { mutableStateOf<JSONObject?>(null) }
    LaunchedEffect(vm.plan, vm.history.size) { d = vm.programsList() }
    val rows = d?.list("programs")?.filter { it.b("quick") } ?: return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.padding(start = 4.dp, top = 8.dp), verticalAlignment = Alignment.Bottom) {
            Text("Routines", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Text("Off-plan sessions you saved", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
        }
        if (rows.isEmpty()) Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(cs.surfaceContainerLow)
            .then(Modifier.padding(16.dp)), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(PiIcons.Zap, null, tint = cs.primary)
            Text(androidx.compose.ui.text.buildAnnotatedString {
                pushStyle(androidx.compose.ui.text.SpanStyle(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)); append("None yet. "); pop()
                append("Finish a quick workout and save it, and it lands here for one-tap repeats.")
            }, style = MaterialTheme.typography.bodyMedium)
        }
        rows.forEach { r ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(cs.surfaceContainerLow).padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(cs.primaryContainer), contentAlignment = Alignment.Center) { Icon(PiIcons.Zap, null, tint = cs.onPrimaryContainer) }
                Column(Modifier.weight(1f)) {
                    Text(r.s("name"), style = MaterialTheme.typography.titleSmall)
                    Text("${r.i("exercises")} exercises · " + r.strings("lifts").joinToString(", "), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Button(onClick = { vm.start(r.s("id"), r.s("dayId")) }, modifier = Modifier.height(40.dp), contentPadding = PaddingValues(horizontal = 14.dp)) {
                    Icon(PiIcons.Play, null, modifier = Modifier.size(14.dp)); Text(" Start")
                }
                ProgramMenuButton(vm, r.s("id"), r.s("name"))
            }
        }
    }
}


/** The design's icon picker: preview, colour, shape, search and groups (Suggested from the goal). */
private val SUGGESTED = mapOf(
    "hypertrophy" to listOf("dumbbell", "biceps-flexed", "layers", "trending-up", "chart-column", "flame", "heart-pulse", "gem", "sparkles", "mountain"),
    "strength" to listOf("weight", "anchor", "hammer", "flame", "zap", "gauge", "trophy", "crown", "mountain", "target"),
    "both" to listOf("zap", "swords", "weight", "dumbbell", "rocket", "trophy", "medal", "shield", "mountain", "activity"),
)
private val SUGGESTED_CYCLE = listOf("orbit", "repeat", "layers", "milestone", "compass", "infinity", "calendar", "chart-line", "mountain", "trophy")

@Composable
fun IconPickerSheet(vm: AppViewModel, key: String, onClose: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    var cur by remember { mutableStateOf<JSONObject?>(null) }
    LaunchedEffect(key) { cur = vm.programIcon(key) }
    var tab by remember { mutableStateOf("Suggested") }
    var q by remember { mutableStateOf("") }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val save = { icon: String?, color: String?, shape: String? -> scope.launch { cur = vm.setProgramIcon(key, icon, color, shape) }; Unit }
    ModalBottomSheet(onDismissRequest = onClose, sheetState = com.pursuitiron.app.ui.common.rememberSettlingSheet(cur)) {
        val c = cur ?: run { Text("Loading…", modifier = Modifier.padding(20.dp)); return@ModalBottomSheet }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                com.pursuitiron.app.ui.common.IconTile(c, 88.dp)
                Column(Modifier.weight(1f)) {
                    Text(if (c.b("cycle")) "Cycle icon" else "Program icon", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                    Text(c.s("name"), style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                        com.pursuitiron.app.ui.common.IconTile(c, 28.dp)
                        Text("in a list · ${c.s("icon").replace('-', ' ')}", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    }
                }
            }
            Kicker("Colour")
            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(com.pursuitiron.app.ui.common.ProgramIconStyle.colors.size) { k ->
                    val (id, col) = com.pursuitiron.app.ui.common.ProgramIconStyle.colors[k]
                    val on = c.s("color") == id
                    Box(Modifier.size(40.dp).clip(CircleShape).background(col ?: cs.primary)
                        .then(if (on) Modifier.padding(3.dp).clip(CircleShape).background(cs.surface).padding(3.dp).clip(CircleShape).background(col ?: cs.primary) else Modifier)
                        .clickable { save(null, id, null) }.semantics { contentDescription = (com.pursuitiron.app.ui.common.ProgramIconStyle.colorLabel[id] ?: id) + if (on) ", selected" else ""; role = Role.Button })
                }
            }
            Kicker("Shape")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                com.pursuitiron.app.ui.common.ProgramIconStyle.shapes.forEach { (id, label) ->
                    val on = c.s("shape") == id
                    Column(Modifier.weight(1f).height(64.dp).clip(RoundedCornerShape(18.dp)).background(if (on) cs.secondaryContainer else cs.surfaceContainerHigh)
                        .then(if (on) Modifier.border(2.dp, cs.primary, RoundedCornerShape(18.dp)) else Modifier)
                        .clickable { save(null, null, id) }.semantics(mergeDescendants = true) { contentDescription = "$label shape" + if (on) ", selected" else ""; role = Role.Button },
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Box(Modifier.size(22.dp).clip(com.pursuitiron.app.ui.common.ProgramIconStyle.shape(id)).background(cs.onSurfaceVariant))
                        Text(label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
            androidx.compose.material3.OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(), singleLine = true, shape = CircleShape,
                placeholder = { Text("Search icons — flame, trophy, mountain…") }, leadingIcon = { Icon(PiIcons.Focus, null) })
            val groups = listOf("Suggested") + com.pursuitiron.app.ui.common.ProgramIconLib.all.map { it.second }.distinct()
            if (q.isBlank()) androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(groups.size) { k -> com.pursuitiron.app.ui.common.MorphChip(groups[k], tab == groups[k], { tab = groups[k] }, height = 36.dp) }
            }
            val all = com.pursuitiron.app.ui.common.ProgramIconLib.all
            val list = when {
                q.isNotBlank() -> all.filter { it.first.replace('-', ' ').contains(q.trim().lowercase()) || it.second.lowercase().contains(q.trim().lowercase()) }
                tab == "Suggested" -> (if (c.b("cycle")) SUGGESTED_CYCLE else SUGGESTED[c.s("goal")] ?: SUGGESTED.getValue("hypertrophy")).mapNotNull { n -> all.firstOrNull { it.first == n } }
                else -> all.filter { it.second == tab }
            }
            if (list.isEmpty()) Text("No icon by that name. Try flame, trophy or mountain.", color = cs.onSurfaceVariant)
            list.chunked(6).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { (name, _, _) ->
                        val on = c.s("icon") == name
                        Box(Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(if (on) 18.dp else 26.dp)).background(if (on) cs.primary else cs.surfaceContainerHigh)
                            .clickable { save(name, null, null) }.semantics { contentDescription = name.replace('-', ' ') + if (on) ", selected" else ""; role = Role.Button },
                            contentAlignment = Alignment.Center) {
                            Icon(com.pursuitiron.app.ui.common.ProgramIconStyle.vector(name), null, tint = if (on) cs.onPrimary else cs.onSurface, modifier = Modifier.size(24.dp))
                        }
                    }
                    repeat(6 - row.size) { Box(Modifier.weight(1f)) }
                }
            }
            Text(if (c.b("cycle")) "Shows on the cycle and its blocks." else "Shows on the Plan tab, your programs list and when you switch programs. Colour and shape stay the same in light and dark themes.",
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            if (c.b("saved")) TextButton(onClick = { scope.launch { cur = vm.setProgramIcon(key, reset = true); vm.toast("Back to the default icon") } }) { Text("Use the default icon") }
            Button(onClick = onClose, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Done") }
        }
    }
}
