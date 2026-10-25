@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.pursuitiron.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.alpha
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.role
import androidx.compose.foundation.verticalScroll
import org.json.JSONObject
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pursuitiron.app.AppViewModel
import com.pursuitiron.app.BuildConfig
import com.pursuitiron.app.data.Transfer
import com.pursuitiron.app.ui.LocalWide
import com.pursuitiron.app.ui.common.Cookie
import com.pursuitiron.app.ui.common.Kicker
import com.pursuitiron.app.ui.common.MorphChip
import com.pursuitiron.app.ui.common.PiCard
import com.pursuitiron.app.ui.common.PiIcons
import com.pursuitiron.app.ui.common.Segmented
import com.pursuitiron.app.ui.common.SectionTitle
import com.pursuitiron.app.ui.common.Sparkline
import com.pursuitiron.app.ui.common.ThemePicker
import com.pursuitiron.app.ui.common.b
import com.pursuitiron.app.ui.common.d
import com.pursuitiron.app.ui.common.fmtNum
import com.pursuitiron.app.ui.common.i
import com.pursuitiron.app.ui.common.list
import com.pursuitiron.app.ui.common.o
import com.pursuitiron.app.ui.common.s
import com.pursuitiron.app.ui.theme.Seed
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun YouScreen(vm: AppViewModel) {
    val wide = LocalWide.current
    var section by remember { mutableStateOf("profile") }
    var historyId by remember { mutableStateOf<String?>(null) }
    var gymsOpen by remember { mutableStateOf(false) }
    var toolsOpen by remember { mutableStateOf(false) }
    var customizeOpen by remember { mutableStateOf(false) }
    var meOpen by remember { mutableStateOf(false) }
    var standardsOpen by remember { mutableStateOf(false) }
    var settingsSheet by remember { mutableStateOf<String?>(null) }
    var board by remember { mutableStateOf<JSONObject?>(null) }
    LaunchedEffect(vm.progress, vm.profile) { board = vm.profileBoard() }
    val full = StaggeredGridItemSpan.FullLine
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(if (wide) 2 else 1),
        contentPadding = PaddingValues(start = if (wide) 32.dp else 16.dp, end = if (wide) 32.dp else 16.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp), verticalItemSpacing = 16.dp, modifier = Modifier.statusBarsPadding(),
    ) {
        item(span = full) { YouHeader(vm, board) { meOpen = true } }
        board?.takeIf { (it.o("lifetime")?.i("sessions") ?: 0) > 0 }?.let { b -> item(span = full) { YouStats(b) } }
        item(span = full) { Segmented(listOf("profile" to "Profile", "body" to "Body", "settings" to "Settings"), section, { section = it }) }
        when (section) {
            "profile" -> {
                // The design's profile sections, in your order, hidden ones left out.
                vm.prefs.profileLayout.visible().forEach { id ->
                    when (id) {
                        "big3" -> board?.let { b -> item { ProfBig3Card(vm, b) } }
                        "score" -> item { ProfScoreCard(vm, onOpen = { standardsOpen = true }) { section = "body" } }
                        "bw" -> item { ProfBodyweightCard(vm) { section = "body" } }
                        "prs" -> board?.let { b -> item { ProfPrBoard(vm, b) } }
                        "cal" -> board?.let { b -> item { ProfHeatCard(b) { historyId = it } } }
                        "ach" -> board?.let { b -> item(span = full) { ProfBadges(b) } }
                        "bal" -> board?.let { b -> item { ProfBalance(b) } }
                    }
                }
                item(span = full) { CustomizeProfileButton { customizeOpen = true } }
            }
            "body" -> { item { BodyweightCard(vm) }; item { MeasurementsCard(vm) }; item(span = full) { ProgressPhotosCard(vm) }; item { InjuriesCard(vm) } }
            else -> {
                // The design's Settings: the everyday controls inline, everything else one row away in a sheet.
                item { SettingsCard(vm) }
                item { GymsCard(vm) { gymsOpen = true } }
                item(span = full) { SettingsNav(vm, onTools = { toolsOpen = true }) { settingsSheet = it } }
            }
        }
    }
    historyId?.let { id -> HistorySheet(vm, id) { historyId = null } }
    if (standardsOpen) StrengthStandardsSheet(vm) { standardsOpen = false }
    if (gymsOpen) GymsSheet(vm) { gymsOpen = false }
    if (toolsOpen) ToolsSheet(vm) { toolsOpen = false }
    if (customizeOpen) ProfileCustomizeSheet(vm) { customizeOpen = false }
    if (meOpen) MeSheet(vm, onClose = { meOpen = false }) { meOpen = false; standardsOpen = true }
    settingsSheet?.let { k -> SettingsPageSheet(vm, k) { settingsSheet = null } }
}

/* App.js ProfileCustomization, as the design's panel: reorder the profile and hide what you don't use. */
@Composable
private fun ProfileCustomizeSheet(vm: AppViewModel, onClose: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val lay = vm.prefs.profileLayout
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onClose) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Customize profile", style = MaterialTheme.typography.headlineSmall)
            Text("Reorder your profile and hide what you don't use. ${lay.visible().size} of ${lay.order.size} sections shown.", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            Column(Modifier.clip(RoundedCornerShape(22.dp)), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                lay.order.forEachIndexed { k, id ->
                    val name = com.pursuitiron.app.data.ProfileLayout.label(id)
                    val shown = id !in lay.hidden
                    val lastShown = shown && lay.visible().size == 1
                    Row(Modifier.fillMaxWidth().background(cs.surfaceContainerHigh).padding(start = 16.dp, end = 12.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f).alpha(if (shown) 1f else 0.6f))
                        IconButton(onClick = { val o = lay.order.toMutableList(); o.add(k - 1, o.removeAt(k)); vm.prefs.changeProfileLayout(lay.copy(order = o)) }, enabled = k > 0) { Icon(PiIcons.ChevronUp, "Move $name up") }
                        IconButton(onClick = { val o = lay.order.toMutableList(); o.add(k + 1, o.removeAt(k)); vm.prefs.changeProfileLayout(lay.copy(order = o)) }, enabled = k < lay.order.size - 1) { Icon(PiIcons.ChevronDown, "Move $name down") }
                        androidx.compose.material3.Switch(
                            checked = shown, enabled = !lastShown,
                            onCheckedChange = { on -> vm.prefs.changeProfileLayout(lay.copy(hidden = if (on) lay.hidden - id else lay.hidden + id)) },
                            modifier = Modifier.semantics { contentDescription = "Show $name" },
                        )
                    }
                }
            }
            OutlinedButton(onClick = { vm.prefs.changeProfileLayout(com.pursuitiron.app.data.ProfileLayout.DEFAULT) }, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("Restore the default layout") }
        }
    }
}

@Composable
private fun BodyweightCard(vm: AppViewModel) {
    val b = vm.body
    val unit = b.s("unit", "kg")
    val log = b.list("log")
    val vals = log.map { it.d("w") }.filter { it > 0 }
    var entry by remember { mutableStateOf(b.s("bodyweight")) }
    val cs = MaterialTheme.colorScheme
    PiCard(radius = 28.dp) {
        Row { Text("Bodyweight", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (vals.size >= 8) { val a = vals.takeLast(7).average(); val p = vals.dropLast(7).takeLast(7).average(); val ch = a - p
                Text((if (ch <= 0) "−" else "+") + fmtNum(Math.round(kotlin.math.abs(ch) * 10) / 10.0) + " $unit vs prior week", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant) } }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(b.s("bodyweight").ifBlank { "—" }, style = MaterialTheme.typography.displaySmall)
            Text("  $unit" + if (vals.size >= 7) " · 7-day average ${fmtNum(Math.round(vals.takeLast(7).average() * 10) / 10.0)}" else "", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
        }
        if (vals.size >= 2) {
            val smooth = vals.indices.map { i -> vals.subList((i - 6).coerceAtLeast(0), i + 1).average() }
            Sparkline(smooth.takeLast(60), Modifier.fillMaxWidth().height(96.dp), description = "Bodyweight 7-day average over the last ${smooth.takeLast(60).size} entries")
        } else Text("Log a few days and your 7-day average appears here.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(entry, { entry = it }, Modifier.weight(1f), singleLine = true, label = { Text("Today ($unit)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            Button(onClick = { entry.replace(',', '.').toDoubleOrNull()?.let { vm.logBodyweight(it) } }, modifier = Modifier.height(56.dp)) { Text("Log") }
        }
    }
}

private val MEASURES = listOf("waist", "chest", "arm", "thigh", "hips", "neck")

@Composable
private fun MeasurementsCard(vm: AppViewModel) {
    val m = vm.body.o("measurements")
    val lu = vm.body.s("lengthUnit", if (vm.body.s("unit") == "lb") "in" else "cm")
    var key by remember { mutableStateOf("waist") }
    var v by remember { mutableStateOf("") }
    val cs = MaterialTheme.colorScheme
    PiCard(radius = 28.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Measurements", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Segmented(listOf("cm" to "cm", "in" to "in"), lu, { if (it != lu) vm.setLengthUnit(it) }, modifier = Modifier.width(120.dp), height = 36.dp)
        }
        MEASURES.forEach { k ->
            val rows = m.list(k)
            if (rows.isNotEmpty()) Row {
                Text(k.replaceFirstChar { it.uppercase() }, Modifier.weight(1f))
                val last = rows.last().d("v"); val first = rows.first().d("v")
                if (rows.size > 1) Text((if (last - first >= 0) "+" else "−") + fmtNum(kotlin.math.abs(last - first)) + "  ", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                Text("${fmtNum(last)} $lu", style = MaterialTheme.typography.titleSmall)
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            MEASURES.forEach { k -> MorphChip(k.replaceFirstChar { it.uppercase() }, key == k, { key = k }) }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(v, { v = it }, Modifier.weight(1f), singleLine = true, label = { Text("${key.replaceFirstChar { it.uppercase() }} ($lu)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            Button(onClick = { v.replace(',', '.').toDoubleOrNull()?.let { vm.logMeasurement(key, it); v = "" } }, modifier = Modifier.height(56.dp)) { Text("Log") }
        }
    }
}

@Composable
private fun SettingRow(label: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SettingsCard(vm: AppViewModel) {
    val st = vm.settings
    var confirmUnit by remember { mutableStateOf<String?>(null) }
    PiCard(radius = 28.dp) {
        Text("Training", style = MaterialTheme.typography.titleMedium)
        Kicker("Units")
        Segmented(listOf("kg" to "kg", "lb" to "lb"), st.s("unit", "kg"), { if (it != st.s("unit", "kg")) confirmUnit = it })
        Kicker("Measurements")
        val lu = vm.body.s("lengthUnit", if (st.s("unit") == "lb") "in" else "cm")
        Segmented(listOf("cm" to "Centimetres", "in" to "Inches"), lu, { if (it != lu) vm.setLengthUnit(it) })
        Kicker("Rest length")
        Segmented(listOf("0.75" to "Shorter", "1.0" to "Standard", "1.25" to "Longer"), when { st.d("restScale", 1.0) < 0.9 -> "0.75"; st.d("restScale", 1.0) > 1.1 -> "1.25"; else -> "1.0" },
            { vm.updateSetting("restScale", it.toDouble()) })
        Column(Modifier.clip(RoundedCornerShape(20.dp)), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            SettingRow("Start rest automatically", "After each logged set", st.b("restAutoStart", true)) { vm.updateSetting("restAutoStart", it) }
            SettingRow("Haptics", "A distinct tap for logged sets, records and rest ending", st.b("haptics", true)) { vm.updateSetting("haptics", it) }
            SettingRow("Keep screen on", "While a workout is open", vm.prefs.keepAwake) { vm.prefs.changeKeepAwake(it) }
            SettingRow("Grip mode", "Bigger set controls for sweaty hands", vm.prefs.grip) { vm.prefs.changeGrip(it) }
            SettingRow("Weight & reps steppers", if (vm.prefs.steppers) "−/+ beside each value · hold to run it up" else "Tap a value to edit it · turn on for −/+ beside each value", vm.prefs.steppers) { vm.prefs.changeSteppers(it) }
        }
        Text("Looks", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
        val darkNow = vm.prefs.dark ?: androidx.compose.foundation.isSystemInDarkTheme()
        Kicker("Theme")
        Segmented(listOf("system" to "System", "light" to "Light", "dark" to "Dark"), when (vm.prefs.dark) { null -> "system"; true -> "dark"; false -> "light" },
            { vm.prefs.changeDark(when (it) { "dark" -> true; "light" -> false; else -> null }) })
        Text(when (vm.prefs.dark) { null -> "Follows your phone · " + (if (darkNow) "dark" else "light") + " right now"; true -> "Always dark"; false -> "Always light" },
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (darkNow) Column(Modifier.clip(RoundedCornerShape(20.dp))) {
            SettingRow("Pure black", "True black behind everything. Easier on OLED screens and the battery", vm.prefs.black) { vm.prefs.changeBlack(it) }
        }
        Kicker("Color")
        ThemePicker(vm.prefs.seed, darkNow, { vm.prefs.changeSeed(it) }, { vm.toast(it) })
        Text(if (vm.prefs.seed.isDynamic) "Wallpaper · matches your wallpaper on Android 12 and up" else vm.prefs.seed.label + " · every theme works light and dark",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!vm.rest.exactAllowed()) {
            val ctx = LocalContext.current
            Column(Modifier.clip(RoundedCornerShape(20.dp))) {
                MenuRow(PiIcons.Timer, "Rest alert on the second", "Let Pursuit Iron use exact alarms so the rest-over alert isn't late with the screen off", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer) {
                    if (android.os.Build.VERSION.SDK_INT >= 31) runCatching { ctx.startActivity(Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + ctx.packageName))) }
                }
            }
        }
    }
    confirmUnit?.let { to ->
        androidx.compose.material3.AlertDialog(onDismissRequest = { confirmUnit = null },
            title = { Text("Switch to $to?") },
            text = { Text("Your programs, training maxes, bodyweight and measurements convert to $to. Past workouts keep the unit they were logged in.") },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { vm.updateSetting("unit", to); confirmUnit = null }) { Text("Switch") } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { confirmUnit = null }) { Text("Cancel") } })
    }
}

@Composable
private fun DataCard(vm: AppViewModel) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            val text = vm.exportText() ?: return@launch
            runCatching { ctx.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) } }
                .onSuccess { vm.markBackup(); vm.toast("Backup saved") }.onFailure { vm.toast("Couldn't save the backup") }
        }
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching { Transfer.readUri(ctx, uri) }.getOrNull()?.let { vm.offerFile(it) } ?: run { if (uri != null) vm.toast("That file couldn't be read") }
    }
    var csvKind by remember { mutableStateOf("workouts") }
    val saveCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scope.launch {
            val text = vm.exportCsv(csvKind) ?: return@launch
            runCatching { ctx.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) } }
                .onSuccess { vm.toast("CSV saved") }.onFailure { vm.toast("Couldn't save the CSV") }
        }
    }
    val last = vm.settings?.optLong("lastBackup") ?: 0L
    PiCard(radius = 28.dp) {
        Text("Your data", style = MaterialTheme.typography.titleMedium)
        Text("Everything stays on this phone. " + if (last > 0) "Last backup ${SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(last))}." else "No backup yet.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.clip(RoundedCornerShape(20.dp)), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            MenuRow(PiIcons.Download, "Save a backup", "A file you can keep anywhere", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer) {
                save.launch("pursuit-iron-backup-${SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())}.json")
            }
            MenuRow(PiIcons.Upload, "Import a backup or CSV", "A Pursuit Iron backup, or a Strong or Hevy export. Merges; nothing is deleted", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer) {
                open.launch(arrayOf("application/json", "text/plain", "text/csv", "text/comma-separated-values", "application/csv", "application/octet-stream"))
            }
            MenuRow(PiIcons.Download, "Export workouts as CSV", "One row per lift per session, for a spreadsheet", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer) {
                csvKind = "workouts"; saveCsv.launch("pursuit-iron-workouts-${SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())}.csv")
            }
            MenuRow(PiIcons.Download, "Export body data as CSV", "Bodyweight and measurements", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer) {
                csvKind = "body"; saveCsv.launch("pursuit-iron-body-${SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())}.csv")
            }
            MenuRow(PiIcons.Redo, "Bring data from the web app", "Opens it in your browser, where your history lives", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer) {
                Transfer.openTransferPage(ctx)
            }
        }
    }
}

@Composable
private fun AboutCard(vm: AppViewModel) {
    PiCard(radius = 28.dp) {
        Text("About", style = MaterialTheme.typography.titleMedium)
        Text("Pursuit Iron ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", style = MaterialTheme.typography.bodyMedium)
        Text("Training engine runs on this phone (${vm.runtime}). The same engine as the web app, so every number matches.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SectionTitle("Not medical advice", Modifier.padding(start = 0.dp))
        Text("Pursuit Iron plans training load. It is not a medical device.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun InjuriesCard(vm: AppViewModel) {
    val n = vm.home.list("injuries").size
    PiCard(radius = 28.dp) {
        Text("Injuries", style = MaterialTheme.typography.titleMedium)
        Text(if (n == 0) "Nothing active. Log one and the plan protects that area." else "$n active. Related lifts are adjusted until you mark it resolved.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.clip(RoundedCornerShape(20.dp))) {
            MenuRow(PiIcons.Shield, "Injury log", "Log, edit or resolve", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer) { vm.injuryLogOpen = true }
        }
    }
}

/* ------------------------------------------------------------------ the design's settings rows */
private data class NavRow(val key: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val title: String, val sub: String)

@Composable
private fun SettingsNav(vm: AppViewModel, onTools: () -> Unit, onOpen: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val rows = listOf(
        NavRow("training", PiIcons.Flame, "Training preferences", "Experience, effort scale, set and rest defaults"),
        NavRow("tools", PiIcons.Plates, "Plates and 1RM", "What to load on each side, and your estimated max"),
        NavRow("loading", PiIcons.Gym, "Plates & bars", "The plates you own and your bars"),
        NavRow("reminders", PiIcons.Calendar, "Reminders", "Training day reminders that survive a reboot"),
        NavRow("exercises", PiIcons.Plus, "Your exercises", if (vm.customs.isEmpty()) "Create lifts that work everywhere" else "${vm.customs.size} custom lift${if (vm.customs.size == 1) "" else "s"}"),
        NavRow("profile", PiIcons.You, "Profile details", "Bodyweight, sex and age for standards"),
        NavRow("backup", PiIcons.Download, "Backup & restore", "Save everything to a file, or bring it back"),
        NavRow("data", PiIcons.Shield, "Data & storage", "Import, export, banned exercises, clear or reset"),
        NavRow("help", PiIcons.Help, "Help & about", "Report a bug, privacy, the research behind it"),
    )
    Column(Modifier.clip(RoundedCornerShape(24.dp)), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        rows.forEach { r ->
            Row(
                Modifier.fillMaxWidth().background(cs.surfaceContainerLow).clickable { if (r.key == "tools") onTools() else onOpen(r.key) }
                    .semantics(mergeDescendants = true) { role = androidx.compose.ui.semantics.Role.Button }.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(cs.secondaryContainer), contentAlignment = Alignment.Center) {
                    Icon(r.icon, null, Modifier.size(20.dp), tint = cs.onSecondaryContainer)
                }
                Column(Modifier.weight(1f)) {
                    Text(r.title, style = MaterialTheme.typography.titleSmall)
                    Text(r.sub, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 2)
                }
                Icon(PiIcons.ChevronRight, null, tint = cs.onSurfaceVariant)
            }
        }
    }
}

/** One settings page as a sheet: the same card the page used to show inline. */
@Composable
private fun SettingsPageSheet(vm: AppViewModel, key: String, onClose: () -> Unit) {
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onClose, sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(horizontal = 12.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (key) {
                "training" -> TrainingPrefsCard(vm)
                "loading" -> LoadingCard(vm)
                "reminders" -> RemindersCard(vm)
                "exercises" -> CustomExercisesCard(vm)
                "profile" -> ProfileFieldsCard(vm)
                "backup" -> DataCard(vm)
                "data" -> ManageDataCard(vm)
                "help" -> { HelpCard(vm); AboutCard(vm) }
            }
        }
    }
}
