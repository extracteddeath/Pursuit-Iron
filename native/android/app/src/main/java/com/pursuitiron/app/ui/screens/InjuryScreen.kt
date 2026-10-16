@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.pursuitiron.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pursuitiron.app.AppViewModel
import com.pursuitiron.app.ui.common.Kicker
import com.pursuitiron.app.ui.common.MorphChip
import com.pursuitiron.app.ui.common.PiCard
import com.pursuitiron.app.ui.common.Segmented
import com.pursuitiron.app.ui.common.jsonOf
import com.pursuitiron.app.ui.common.list
import com.pursuitiron.app.ui.common.s
import com.pursuitiron.app.ui.common.strings
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** App.js HomeInjuryJournal: log, edit, resolve; resolved entries stay as a record. */
@Composable
fun InjuryJournalSheet(vm: AppViewModel) {
    if (!vm.injuryLogOpen) return
    var log by remember { mutableStateOf<JSONObject?>(null) }
    var editing by remember { mutableStateOf<JSONObject?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(reload) { log = vm.injuryLog(); if (reload == 0 && log.list("active").isEmpty()) editing = JSONObject() }
    val cs = MaterialTheme.colorScheme
    ModalBottomSheet(onDismissRequest = { vm.injuryLogOpen = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val l = log ?: run { Text("Loading…"); return@Column }
            val e = editing
            if (e != null) {
                InjuryForm(l, e, onCancel = { editing = null }) { entry ->
                    scope.launch { val r = vm.saveInjury(entry); if (r?.optBoolean("ok") == true) { editing = null; reload++ } else vm.toast(r?.s("error", "Couldn't save") ?: "Couldn't save") }
                }
                return@Column
            }
            Text("Injury log", style = MaterialTheme.typography.headlineSmall)
            Button(onClick = { editing = JSONObject() }, modifier = Modifier.fillMaxWidth()) { Text("Log an injury") }
            if (l.list("active").isEmpty()) Text("No active injuries. Resolved entries stay here for your records.", color = cs.onSurfaceVariant)
            l.list("active").forEach { x -> InjuryRecord(x, onEdit = { editing = x }) { scope.launch { vm.toggleInjury(x.s("id")); reload++ } } }
            val resolved = l.list("resolved")
            if (resolved.isNotEmpty()) {
                Kicker("Resolved", modifier = Modifier.padding(top = 8.dp))
                resolved.forEach { x -> InjuryRecord(x, onEdit = { editing = x }) { scope.launch { vm.toggleInjury(x.s("id")); reload++ } } }
            }
        }
    }
}

@Composable
private fun InjuryRecord(x: JSONObject, onEdit: () -> Unit, onToggle: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val resolved = x.optLong("resolvedAt") > 0
    val impact = when (x.s("impact")) { "skip" -> "Skip affected lifts"; "reduce" -> "Reduce affected work"; else -> "Review before training" }
    val since = x.s("onset").takeIf { it.isNotBlank() }?.let { runCatching { SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(it) }.getOrNull() }
    PiCard(color = if (resolved) cs.surfaceContainer else cs.tertiaryContainer, radius = 22.dp, padding = 14.dp) {
        Text(x.s("title"), style = MaterialTheme.typography.titleSmall, color = if (resolved) cs.onSurface else cs.onTertiaryContainer)
        Text(listOfNotNull(since?.let { "Since ${DateFormat.getDateInstance(DateFormat.MEDIUM).format(it)}" }, if (resolved) "Resolved" else impact).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall, color = if (resolved) cs.onSurfaceVariant else cs.onTertiaryContainer)
        if (x.s("note").isNotBlank()) Text(x.s("note"), style = MaterialTheme.typography.bodySmall, color = if (resolved) cs.onSurfaceVariant else cs.onTertiaryContainer)
        val touches = x.strings("nextWorkout")
        if (!resolved && touches.isNotEmpty()) Text("Next workout: ${touches.joinToString(", ")}", style = MaterialTheme.typography.labelMedium, color = cs.onTertiaryContainer)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onEdit) { Text("Edit") }
            TextButton(onClick = onToggle) { Text(if (resolved) "Mark active" else "Mark resolved") }
        }
    }
}

@Composable
private fun InjuryForm(log: JSONObject, entry: JSONObject, onCancel: () -> Unit, onSave: (JSONObject) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val areas = log.list("areas")
    val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    var region by remember { mutableStateOf(entry.s("region")) }
    var part by remember { mutableStateOf(if (entry.s("region").isBlank()) entry.s("part") else "") }
    var group by remember { mutableStateOf(areas.firstOrNull { it.s("id") == entry.s("region") }?.s("group") ?: "Upper body") }
    var side by remember { mutableStateOf(entry.s("side", "unspecified")) }
    var impact by remember { mutableStateOf(entry.s("impact", "monitor")) }
    var onset by remember { mutableStateOf(entry.s("onset").ifBlank { today }) }
    var note by remember { mutableStateOf(entry.s("note")) }
    val editingId = entry.s("id")
    Text(if (editingId.isNotBlank()) "Edit injury" else "Log an injury", style = MaterialTheme.typography.headlineSmall)
    Text("Choose the area, then how the plan should handle related lifts.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
    Kicker("Area")
    Segmented(areas.map { it.s("group") }.distinct().map { it to it }, group, { group = it })
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        areas.filter { it.s("group") == group }.forEach { a -> MorphChip(a.s("label"), region == a.s("id"), { region = a.s("id"); part = "" }, height = 40.dp) }
    }
    Kicker("Or a specific muscle")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        log.list("parts").forEach { p -> MorphChip(p.s("label"), region.isBlank() && part == p.s("id"), { part = p.s("id"); region = "" }, height = 36.dp) }
    }
    Kicker("Side")
    Segmented(listOf("unspecified" to "Not specified", "left" to "Left", "right" to "Right", "both" to "Both"), side, { side = it }, height = 40.dp)
    Kicker("What should happen")
    log.list("impacts").forEach { im ->
        val on = impact == im.s("id")
        Surface(onClick = { impact = im.s("id") }, color = if (on) cs.primaryContainer else cs.surfaceContainerHigh, shape = RoundedCornerShape(if (on) 16.dp else 20.dp),
            modifier = Modifier.fillMaxWidth().semantics { role = Role.RadioButton; selected = on }) {
            Column(Modifier.padding(14.dp)) {
                Text(im.s("label"), style = MaterialTheme.typography.titleSmall)
                Text(im.s("detail"), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
        }
    }
    OutlinedTextField(onset, { onset = it.filter { c -> c.isDigit() || c == '-' }.take(10) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Since (YYYY-MM-DD)") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
    OutlinedTextField(note, { note = it.take(300) }, Modifier.fillMaxWidth(), label = { Text("Details (optional)") }, minLines = 2)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f).height(52.dp)) { Text("Cancel") }
        Button(onClick = {
            onSave(jsonOf("id" to editingId.ifBlank { null }, "region" to region, "part" to part, "side" to side, "impact" to impact, "onset" to onset, "note" to note))
        }, enabled = region.isNotBlank() || part.isNotBlank(), modifier = Modifier.weight(1f).height(52.dp)) { Text(if (editingId.isNotBlank()) "Save changes" else "Save injury") }
    }
}

