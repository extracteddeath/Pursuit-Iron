@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.pursuitiron.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.pursuitiron.app.AppViewModel
import com.pursuitiron.app.ui.common.Kicker
import com.pursuitiron.app.ui.common.MorphChip
import com.pursuitiron.app.ui.common.PiCard
import com.pursuitiron.app.ui.common.PiIcons
import com.pursuitiron.app.ui.common.Segmented
import com.pursuitiron.app.ui.common.b
import com.pursuitiron.app.ui.common.i
import com.pursuitiron.app.ui.common.list
import com.pursuitiron.app.ui.common.o
import com.pursuitiron.app.ui.common.s
import com.pursuitiron.app.ui.common.strings
import java.text.DateFormat
import java.text.DateFormatSymbols
import java.util.Calendar

/* ------------------------------------------------------------------ training reminders (App.js Settings → Reminders) */
@Composable
fun RemindersCard(vm: AppViewModel) {
    val r = vm.settings.o("reminders") ?: return
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val enabled = r.b("enabled")
    val time = r.s("time", "18:00")
    val days = r.optJSONArray("days")?.let { a -> (0 until a.length()).map { a.optInt(it) }.toSet() } ?: emptySet()
    var picking by remember { mutableStateOf(false) }
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.setReminders(enabled = granted)
        if (!granted) vm.toast("Reminders need notifications. You can allow them in Android settings.")
    }
    fun turnOn() {
        val needs = Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needs) askPermission.launch(Manifest.permission.POST_NOTIFICATIONS) else vm.setReminders(enabled = true)
    }
    val (hh, mm) = time.split(":").let { (it.getOrNull(0)?.toIntOrNull() ?: 18) to (it.getOrNull(1)?.toIntOrNull() ?: 0) }
    val label = DateFormat.getTimeInstance(DateFormat.SHORT).format(Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, hh); set(Calendar.MINUTE, mm) }.time)
    PiCard(radius = 28.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Training reminders", style = MaterialTheme.typography.titleMedium)
                Text("A notification on the days you train, naming the session that's up next. Works with the app closed.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
            Switch(checked = enabled, onCheckedChange = { if (it) turnOn() else vm.setReminders(enabled = false) }, modifier = Modifier.semantics { contentDescription = "Training reminders" })
        }
        if (enabled) {
            // The design's reminder panel: the time with 15-minute steps (tap it for any time), the days, and a preview.
            fun shift(min: Int) { val t = ((hh * 60 + mm + min) % 1440 + 1440) % 1440; vm.setReminders(time = "%02d:%02d".format(t / 60, t % 60)) }
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(cs.surfaceContainerHigh).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.FilledTonalButton(onClick = { shift(-15) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
                    modifier = Modifier.semantics { contentDescription = "15 minutes earlier" }) { Text("−15") }
                Text(label, style = MaterialTheme.typography.headlineSmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable { picking = true }.padding(vertical = 6.dp).semantics { contentDescription = "Reminder at $label. Change" })
                Button(onClick = { shift(15) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
                    modifier = Modifier.semantics { contentDescription = "15 minutes later" }) { Text("+15") }
            }
            val names = DateFormatSymbols.getInstance().shortWeekdays
            // Monday first, as the design draws the week
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf(1, 2, 3, 4, 5, 6, 0).forEach { d -> val on = d in days
                    Box(Modifier.size(40.dp).clip(if (on) RoundedCornerShape(12.dp) else CircleShape).background(if (on) cs.primary else cs.surfaceContainerHigh)
                        .clickable { vm.setReminders(days = (if (on) days - d else days + d).sorted()) }
                        .semantics { contentDescription = names[d + 1] + if (on) ", on" else ", off"; role = Role.Switch; selected = on }, contentAlignment = Alignment.Center) {
                        Text(names[d + 1].take(1), style = MaterialTheme.typography.labelLarge, color = if (on) cs.onPrimary else cs.onSurfaceVariant)
                    }
                }
            }
            Text("Pick the days you train.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            Text("PREVIEW", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
            val next = vm.home?.o("next")
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(cs.surfaceContainerHigh).padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(32.dp).clip(CircleShape).background(cs.primary))
                Column(Modifier.weight(1f)) {
                    Row { Text("Pursuit Iron", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant, modifier = Modifier.weight(1f)); Text(label, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant) }
                    Text(next?.let { "${it.s("dayLabel")} is up today" } ?: "Time to train", style = MaterialTheme.typography.titleSmall)
                    next?.let { n -> Text(listOfNotNull("${n.list("exercises").size} exercises", n.i("minutes").takeIf { it > 0 }?.let { "about $it min" }).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant) }
                }
            }
        }
    }
    if (picking) {
        val st = rememberTimePickerState(initialHour = hh, initialMinute = mm, is24Hour = android.text.format.DateFormat.is24HourFormat(ctx))
        AlertDialog(onDismissRequest = { picking = false }, title = { Text("Reminder time") }, text = { TimePicker(st) },
            confirmButton = { Button(onClick = { picking = false; vm.setReminders(time = "%02d:%02d".format(st.hour, st.minute)) }) { Text("Set") } },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancel") } })
    }
}

/* ------------------------------------------------------------------ custom exercises (App.js AddSheet "create") */
@Composable
fun CustomExercisesCard(vm: AppViewModel) {
    LaunchedEffect(Unit) { vm.loadCustoms() }
    val cs = MaterialTheme.colorScheme
    var creating by remember { mutableStateOf(false) }
    PiCard(radius = 28.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Your exercises", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = { creating = true }) { Text("+ New") }
        }
        if (vm.customs.isEmpty()) Text("Lifts you create work everywhere: programs, swaps, suggestions and progress.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        Column(Modifier.clip(RoundedCornerShape(18.dp)), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            vm.customs.forEach { x ->
                Row(Modifier.fillMaxWidth().background(cs.surfaceContainerHigh).padding(start = 14.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(x.s("name"), style = MaterialTheme.typography.titleSmall)
                        val rep = x.optJSONArray("rep")
                        Text("${x.s("partLabel")} · ${x.s("type")}" + (rep?.let { " · ${it.optInt(0)}-${it.optInt(1)} reps" } ?: "") + if (x.b("inUse")) " · in a program" else "",
                            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    }
                    if (!x.b("inUse")) IconButton(onClick = { vm.deleteCustom(x.s("id")) }) { Icon(PiIcons.Trash, "Delete ${x.s("name")}") }
                }
            }
        }
    }
    if (creating) CreateExerciseSheet(vm, null) { creating = false }
}

/* The design's New exercise (Main.dc.html newEx): name (a library name is refused), main muscle, equipment, rep range,
   a preview line, Save exercise. Type stays because the engine needs it (compound lifts progress and rest differently).
   onCreated gets the new id, so Add a lift can put it straight into the workout. */
@Composable
fun CreateExerciseSheet(vm: AppViewModel, dayId: String?, onCreated: (String) -> Unit = {}, onClose: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val opts = vm.setup
    val parts = opts.list("parts").map { it.s("id") to it.s("label") }.ifEmpty { listOf("chest" to "Chest") }
    var name by remember { mutableStateOf("") }
    var part by remember { mutableStateOf(parts.first().first) }
    var type by remember { mutableStateOf("compound") }
    val equips = listOf("barbell" to "Barbell", "dumbbell" to "Dumbbell", "cable" to "Cable", "machine" to "Machine", "" to "Bodyweight")
    var equip by remember { mutableStateOf("dumbbell") }
    val reps = listOf(4 to 6, 6 to 10, 8 to 12, 12 to 20)
    var rep by remember { mutableStateOf(8 to 12) }
    var taken by remember { mutableStateOf(false) }
    LaunchedEffect(name) { taken = vm.exerciseNameTaken(name) }
    val valid = name.trim().length >= 3 && !taken
    val cap = @Composable { t: String -> Text(t, style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold, letterSpacing = 0.4.sp), color = cs.onSurfaceVariant) }
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("New exercise", style = MaterialTheme.typography.headlineSmall)
            Text("Yours to use anywhere: swaps, quick workouts, the program editor. The engine progresses it like any other lift.", style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp), color = cs.onSurfaceVariant)
            cap("NAME")
            androidx.compose.material3.TextField(name, { name = it.take(60) }, singleLine = true, placeholder = { Text("e.g. Kneeling cable chop") },
                shape = RoundedCornerShape(16.dp), colors = androidx.compose.material3.TextFieldDefaults.colors(focusedContainerColor = cs.surfaceContainerHigh, unfocusedContainerColor = cs.surfaceContainerHigh,
                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent, unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Exercise name" })
            if (taken) Text("That name is already in the library.", style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp), color = cs.onTertiaryContainer,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(cs.tertiaryContainer).padding(horizontal = 12.dp, vertical = 10.dp))
            cap("MAIN MUSCLE")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                parts.forEach { (id, l) -> MorphChip(l, part == id, { part = id }, height = 40.dp) }
            }
            cap("EQUIPMENT")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                equips.forEach { (id, l) -> MorphChip(l, equip == id, { equip = id }, height = 40.dp) }
            }
            cap("TYPE")
            Segmented(listOf("compound" to "Compound", "isolation" to "Isolation"), type, { type = it })
            cap("REP RANGE")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                reps.forEach { r -> MorphChip("${r.first}–${r.second}", rep == r, { rep = r }, height = 40.dp) }
            }
            Text("${name.trim().ifBlank { "Your exercise" }} · ${parts.firstOrNull { it.first == part }?.second ?: part} · ${equips.first { it.first == equip }.second} · ${rep.first}–${rep.second} reps",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp), color = cs.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(cs.surfaceContainerHigh).padding(horizontal = 14.dp, vertical = 12.dp))
            Button(onClick = { vm.createCustom(name.trim().replaceFirstChar { it.uppercase() }, part, type, if (equip.isEmpty()) emptyList() else listOf(equip), rep.first, rep.second, dayId) { id -> onCreated(id) }; onClose() },
                enabled = valid, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text(if (dayId != null) "Save and add" else "Save exercise", style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}

/* ------------------------------------------------------------------ Strong / Hevy CSV import (App.js CsvImportSummary) */
@Composable
fun CsvImportDialog(vm: AppViewModel) {
    val offer = vm.csvOffer ?: return
    val s = offer.summary
    var unit by remember { mutableStateOf(s.s("weightUnit").ifBlank { vm.settings.s("unit", "kg") }) }
    val unmatched = s.list("unmatched")
    val created = s.strings("customCreated")
    AlertDialog(onDismissRequest = { vm.csvOffer = null },
        title = { Text("Import from ${s.s("label")}?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val df = DateFormat.getDateInstance(DateFormat.MEDIUM)
                Text("${s.i("workouts")} workouts and ${s.i("sets")} sets, ${df.format(java.util.Date(s.optLong("first")))} to ${df.format(java.util.Date(s.optLong("last")))}. They merge into your history; nothing here is deleted.")
                if (s.i("matched") > 0) Text("${s.i("matched")} exercises matched the library.", style = MaterialTheme.typography.bodySmall)
                if (created.isNotEmpty()) Text("New exercises: ${created.take(6).joinToString(", ")}${if (created.size > 6) " and ${created.size - 6} more" else ""}", style = MaterialTheme.typography.bodySmall)
                if (unmatched.isNotEmpty()) Text("Skipped: ${unmatched.take(6).joinToString(", ") { it.s("name") }}", style = MaterialTheme.typography.bodySmall)
                if (s.b("unitAssumed")) {
                    Text("The file doesn't say which unit its weights are in.", style = MaterialTheme.typography.bodySmall)
                    Segmented(listOf("kg" to "Weights are kg", "lb" to "Weights are lb"), unit, { unit = it }, height = 40.dp)
                }
            }
        },
        confirmButton = { Button(onClick = { vm.acceptCsv(if (s.b("unitAssumed")) unit else null) }) { Text("Import") } },
        dismissButton = { TextButton(onClick = { vm.csvOffer = null }) { Text("Cancel") } })
}
