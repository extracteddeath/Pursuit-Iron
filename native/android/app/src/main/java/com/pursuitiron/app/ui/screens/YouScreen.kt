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
import androidx.compose.ui.Modifier
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
    var section by remember { mutableStateOf("body") }
    var historyId by remember { mutableStateOf<String?>(null) }
    val full = StaggeredGridItemSpan.FullLine
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(if (wide) 2 else 1),
        contentPadding = PaddingValues(start = if (wide) 32.dp else 16.dp, end = if (wide) 32.dp else 16.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp), verticalItemSpacing = 14.dp, modifier = Modifier.statusBarsPadding(),
    ) {
        item(span = full) {
            Row(Modifier.padding(top = 20.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                androidx.compose.foundation.layout.Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                    Cookie(MaterialTheme.colorScheme.tertiaryContainer, Modifier.size(64.dp))
                    Icon(PiIcons.You, null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                }
                Column {
                    Text("You", style = MaterialTheme.typography.displayMedium)
                    val st = vm.settings
                    Text("${com.pursuitiron.app.ui.common.plural(st.i("sessions"), "workout")} · ${com.pursuitiron.app.ui.common.plural(st.i("programs"), "program")}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item(span = full) { Segmented(listOf("body" to "Body", "history" to "History", "settings" to "Settings"), section, { section = it }) }
        when (section) {
            "body" -> { item { BodyweightCard(vm) }; item { MeasurementsCard(vm) } }
            "history" -> {
                if (vm.history.isEmpty()) item(span = full) { Text("No workouts yet.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp)) }
                vm.history.forEach { r -> item { Column(Modifier.clip(RoundedCornerShape(18.dp))) { HistoryRow(r) { historyId = r.s("id") } } } }
            }
            else -> { item { SettingsCard(vm) }; item { DataCard(vm) }; item { AboutCard(vm) } }
        }
    }
    historyId?.let { id -> HistorySheet(vm, id) { historyId = null } }
}

@Composable
private fun BodyweightCard(vm: AppViewModel) {
    val b = vm.body
    val unit = b.s("unit", "kg")
    val log = b.list("log")
    val vals = log.map { it.d("v") }.filter { it > 0 }
    var entry by remember { mutableStateOf(b.s("bodyweight")) }
    val cs = MaterialTheme.colorScheme
    PiCard(radius = 28.dp) {
        Row { Text("Bodyweight", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (vals.size >= 8) { val a = vals.takeLast(7).average(); val p = vals.dropLast(7).takeLast(7).average(); val ch = a - p
                Text((if (ch <= 0) "−" else "+") + fmtNum(kotlin.math.abs(ch)) + " $unit vs prior week", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant) } }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(b.s("bodyweight").ifBlank { "—" }, style = MaterialTheme.typography.displaySmall)
            Text("  $unit" + if (vals.size >= 7) " · 7-day average ${fmtNum(vals.takeLast(7).average())}" else "", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
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
    val lu = if (vm.body.s("unit") == "lb") "in" else "cm"
    var key by remember { mutableStateOf("waist") }
    var v by remember { mutableStateOf("") }
    val cs = MaterialTheme.colorScheme
    PiCard(radius = 28.dp) {
        Text("Measurements", style = MaterialTheme.typography.titleMedium)
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
    PiCard(radius = 28.dp) {
        Text("Training", style = MaterialTheme.typography.titleMedium)
        Kicker("Units")
        Segmented(listOf("kg" to "kg", "lb" to "lb"), st.s("unit", "kg"), { vm.updateSetting("unit", it) })
        Kicker("Rest length")
        Segmented(listOf("0.75" to "Shorter", "1.0" to "Standard", "1.25" to "Longer"), when { st.d("restScale", 1.0) < 0.9 -> "0.75"; st.d("restScale", 1.0) > 1.1 -> "1.25"; else -> "1.0" },
            { vm.updateSetting("restScale", it.toDouble()) })
        Column(Modifier.clip(RoundedCornerShape(20.dp)), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            SettingRow("Start rest automatically", "After each logged set", st.b("restAutoStart", true)) { vm.updateSetting("restAutoStart", it) }
            SettingRow("Haptics", "A distinct tap for logged sets, records and rest ending", st.b("haptics", true)) { vm.updateSetting("haptics", it) }
            SettingRow("Keep screen on", "While a workout is open", vm.prefs.keepAwake) { vm.prefs.changeKeepAwake(it) }
            SettingRow("Grip mode", "Bigger set controls for sweaty hands", vm.prefs.grip) { vm.prefs.changeGrip(it) }
        }
        Text("Looks", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
        Segmented(Seed.entries.map { it.name to it.name }, vm.prefs.seed.name, { vm.prefs.changeSeed(Seed.valueOf(it)) })
        Segmented(listOf("system" to "System", "light" to "Light", "dark" to "Dark"), when (vm.prefs.dark) { null -> "system"; true -> "dark"; false -> "light" },
            { vm.prefs.changeDark(when (it) { "dark" -> true; "light" -> false; else -> null }) })
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
        if (uri != null) runCatching { Transfer.readUri(ctx, uri)?.let { Transfer.parse(it, "backup") } }.getOrNull()
            ?.let { vm.importOffer = it } ?: run { if (uri != null) vm.toast("That file isn't a Pursuit Iron backup") }
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
            MenuRow(PiIcons.Upload, "Import a backup", "Merges with what's here; nothing is deleted", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer) {
                open.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
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
