@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.pursuitiron.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.ui.unit.dp
import com.pursuitiron.app.AppViewModel
import com.pursuitiron.app.BuildConfig
import com.pursuitiron.app.ui.common.Kicker
import com.pursuitiron.app.ui.common.PiCard
import com.pursuitiron.app.ui.common.PiIcons
import com.pursuitiron.app.ui.common.Segmented
import com.pursuitiron.app.ui.common.b
import com.pursuitiron.app.ui.common.i
import com.pursuitiron.app.ui.common.list
import com.pursuitiron.app.ui.common.s
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.Locale

const val WEB_APP_URL = "https://extracteddeath.github.io/Pursuit-Iron/"
const val SUPPORT_URL = "https://ko-fi.com/extracteddeath"
const val BUG_EMAIL = "pursuitiron@gmail.com"

/* App.js Settings → Training preferences, as the design's panel: experience with what each level means, the effort
   scale with a live example of how a target reads, and the warm-up checklist. */
@Composable
fun TrainingPrefsCard(vm: AppViewModel) {
    val st = vm.settings
    val cs = MaterialTheme.colorScheme
    val cap = @Composable { t: String -> Text(t, style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.4.sp), color = cs.onSurfaceVariant) }
    PiCard(radius = 28.dp) {
        Text("Training preferences", style = MaterialTheme.typography.headlineSmall)
        cap("TRAINING EXPERIENCE")
        val exp = st.s("experience").let { if (it == "novice") "none" else it }
        Column(Modifier.clip(RoundedCornerShape(20.dp)), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            listOf(Triple("none", "New to lifting", "Under 6 months. Fewer sets, more technique practice"), Triple("beginner", "Beginner", "6–18 months. Volume ramps gently"),
                Triple("intermediate", "Intermediate", "1.5–4 years. The full volume range"), Triple("advanced", "Advanced", "4+ years. Higher volume, harder top sets")).forEach { (id, l, sub) ->
                val on = exp == id
                Row(Modifier.fillMaxWidth().background(if (on) cs.secondaryContainer else cs.surfaceContainerHigh).clickable { vm.updateSetting("experience", id) }
                    .semantics(mergeDescendants = true) { role = Role.RadioButton; selected = on }.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    androidx.compose.material3.RadioButton(selected = on, onClick = null)
                    Column(Modifier.weight(1f)) {
                        Text(l, style = MaterialTheme.typography.titleSmall, color = if (on) cs.onSecondaryContainer else cs.onSurface)
                        Text(sub, style = MaterialTheme.typography.bodySmall, color = if (on) cs.onSecondaryContainer else cs.onSurfaceVariant)
                    }
                }
            }
        }
        Text("Sets your weekly volume range. It applies from the next program you build; the current one keeps its numbers.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        cap("EFFORT SCALE")
        val mode = st.s("loadMode", "rir")
        Segmented(listOf("rir" to "Reps in reserve", "rpe" to "RPE"), mode, { vm.updateSetting("loadMode", it) })
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(cs.surface).padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(if (mode == "rpe") "4 × 6–8 @ RPE 8" else "4 × 6–8 @2 RIR", style = MaterialTheme.typography.titleMedium)
            Text(if (mode == "rpe") "RPE 8 means a hard set with about 2 good reps left. After a set you tap how hard it actually was."
                else "@2 means stop with about 2 good reps left. After a set you tap how many you actually had.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(cs.surfaceContainerHigh).padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Warm-up checklist", style = MaterialTheme.typography.titleSmall)
                Text("Show a warm-up and mobility checklist before each session", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
            androidx.compose.material3.Switch(st.b("warmupCard", true), { vm.updateSetting("warmupCard", it) }, modifier = Modifier.semantics { contentDescription = "Warm-up checklist" })
        }
    }
}

/* The design's Data & storage panel (App.js Settings → Data): what's on this phone, spreadsheet export, import from
   another app, the banned list, and starting over. Backup & restore keeps the full JSON backup. */
@Composable
fun ManageDataCard(vm: AppViewModel) {
    val st = vm.settings
    val cs = MaterialTheme.colorScheme
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var bannedOpen by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<String?>(null) }
    var storage by remember { mutableStateOf<JSONObject?>(null) }
    LaunchedEffect(st) { storage = vm.storageStatus() }
    var csvKind by remember { mutableStateOf("workouts") }
    val saveCsv = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scope.launch {
            val text = vm.exportCsv(csvKind) ?: return@launch
            runCatching { ctx.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) } }
                .onSuccess { vm.toast("CSV saved") }.onFailure { vm.toast("Couldn't save the CSV") }
        }
    }
    val openCsv = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching { com.pursuitiron.app.data.Transfer.readUri(ctx, uri) }.getOrNull()?.let { vm.offerFile(it) } ?: run { if (uri != null) vm.toast("That file couldn't be read") }
    }
    val day = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US).format(java.util.Date())
    val cap = @Composable { t: String -> Text(t, style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.4.sp), color = cs.onSurfaceVariant) }
    PiCard(radius = 28.dp) {
        Text("Data & storage", style = MaterialTheme.typography.headlineSmall)
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(cs.surface).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row { cap("ON THIS PHONE"); androidx.compose.foundation.layout.Spacer(Modifier.weight(1f)); cap("PRIVATE") }
            val bytes = storage?.optLong("bytes") ?: 0L
            Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(cs.surfaceContainerHighest)) {
                Box(Modifier.fillMaxWidth((bytes / (5.0 * 1024 * 1024)).toFloat().coerceIn(0.01f, 1f)).height(6.dp).clip(RoundedCornerShape(3.dp)).background(cs.primary))
            }
            val kb = bytes / 1024
            Text("${if (kb >= 1024) String.format(Locale.US, "%.1f MB", kb / 1024.0) else "$kb KB"} on this phone · ${st.i("sessions")} sessions · ${st.i("programs")} programs · ${vm.customs.size} custom lifts",
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
        cap("EXPORT · SPREADSHEET")
        Column(Modifier.clip(RoundedCornerShape(20.dp)), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            MenuRow(PiIcons.Download, "Workouts CSV", "One row per set from ${st.i("sessions")} sessions", cs.secondaryContainer, cs.onSecondaryContainer) {
                csvKind = "workouts"; saveCsv.launch("pursuit-iron-workouts-$day.csv") }
            MenuRow(PiIcons.Download, "Body CSV", "Bodyweight and measurements", cs.secondaryContainer, cs.onSecondaryContainer) {
                csvKind = "body"; saveCsv.launch("pursuit-iron-body-$day.csv") }
        }
        cap("IMPORT · FROM ANOTHER APP")
        androidx.compose.material3.FilledTonalButton(onClick = { openCsv.launch(arrayOf("text/csv", "text/comma-separated-values", "application/csv", "text/plain", "application/octet-stream")) },
            modifier = Modifier.fillMaxWidth().height(48.dp)) { Icon(PiIcons.Upload, null, Modifier.size(18.dp)); Text("  Choose a CSV") }
        Text("Strong or Hevy: export your workouts as CSV there, then pick the file. You review what comes in before anything is added.",
            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        val banned = st.list("banned")
        Column(Modifier.clip(RoundedCornerShape(20.dp))) {
            DataRow("Banned exercises", "${banned.size} hidden from generation", if (banned.isNotEmpty()) "Review" else null) { bannedOpen = true }
        }
        cap("START OVER")
        androidx.compose.material3.FilledTonalButton(onClick = { confirm = "history" }, enabled = st.i("sessions") > 0, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("Clear workout history") }
        OutlinedButton(onClick = { confirm = "reset" }, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("Erase everything", color = cs.error) }
        Text("Everything lives in this app's private storage. Uninstalling removes it, so keep a backup file somewhere else.",
            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
    }
    if (bannedOpen) BannedSheet(vm) { bannedOpen = false }
    when (confirm) {
        "history" -> AlertDialog(onDismissRequest = { confirm = null },
            title = { Text("Clear ${st.i("sessions")} logged workouts?") },
            text = { Text("Programs stay. You can undo this right after.") },
            confirmButton = { TextButton(onClick = { confirm = null; vm.clearHistory() }) { Text("Clear", color = cs.error) } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } })
        "reset" -> AlertDialog(onDismissRequest = { confirm = null },
            title = { Text("Erase all data?") },
            text = { Text("Programs, cycles, workouts, bodyweight, progress photos, notes and injuries are deleted from this phone. Theme, unit and plates stay. This can't be undone; save a backup first if you might want it.") },
            confirmButton = { TextButton(onClick = { confirm = null; vm.resetAll() }) { Text("Erase everything", color = cs.error) } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } })
    }
}

@Composable
private fun DataRow(label: String, sub: String, action: String?, danger: Boolean = false, onAction: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().background(cs.surfaceContainerHigh).padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
        if (action != null) TextButton(onClick = onAction, modifier = Modifier.semantics { contentDescription = "$action $label" }) { Text(action, color = if (danger) cs.error else cs.primary) }
    }
}

@Composable
private fun BannedSheet(vm: AppViewModel, onClose: () -> Unit) {
    val banned = vm.settings.list("banned")
    ModalBottomSheet(onDismissRequest = onClose) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Banned exercises", style = MaterialTheme.typography.headlineSmall)
            Text("These are never picked when a program is built or a lift is swapped.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (banned.isEmpty()) Text("Nothing banned.")
            banned.forEach { b ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(b.s("name"), Modifier.weight(1f))
                    TextButton(onClick = { vm.setBan(b.s("id"), false) }) { Text("Unban") }
                }
            }
            if (banned.size > 1) OutlinedButton(onClick = { vm.clearBanned(); onClose() }, modifier = Modifier.fillMaxWidth()) { Text("Unban all") }
        }
    }
}

/* App.js Settings → Report a bug, support, privacy, references. */
@Composable
fun HelpCard(vm: AppViewModel) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    var refsOpen by remember { mutableStateOf(false) }
    val open = { url: String -> runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }.onFailure { vm.toast("No app can open that link") } }
    PiCard(radius = 28.dp) {
        Text("Help and support", style = MaterialTheme.typography.titleMedium)
        Text("Found something wrong, or something missing? Email $BUG_EMAIL and say what you expected to happen. Screenshots help a lot.",
            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        Column(Modifier.clip(RoundedCornerShape(20.dp)), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            MenuRow(PiIcons.Info, "Report a bug", "Opens your email with the version filled in", cs.secondaryContainer, cs.onSecondaryContainer) {
                val subject = Uri.encode("Pursuit Iron bug report (Android ${BuildConfig.VERSION_NAME}, build ${BuildConfig.VERSION_CODE})")
                val body = Uri.encode("What happened:\n\nWhat I expected:\n\nDevice: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}, Android ${android.os.Build.VERSION.RELEASE}\n")
                runCatching { ctx.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$BUG_EMAIL?subject=$subject&body=$body"))) }.onFailure { vm.toast("No email app found. Write to $BUG_EMAIL") }
            }
            MenuRow(PiIcons.Flame, "Support Pursuit Iron", "Buy the developer a coffee on Ko-fi", cs.tertiaryContainer, cs.onTertiaryContainer) { open(SUPPORT_URL) }
            MenuRow(PiIcons.Shield, "Privacy policy", "Your data stays on this phone", cs.secondaryContainer, cs.onSecondaryContainer) { open(WEB_APP_URL + "privacy.html") }
            MenuRow(PiIcons.List, "References & methodology", "The research the programming defaults draw on", cs.secondaryContainer, cs.onSecondaryContainer) { refsOpen = true }
            MenuRow(PiIcons.Sparkle, "Show the welcome screen", "The introduction from first launch", cs.secondaryContainer, cs.onSecondaryContainer) { vm.prefs.markIntro(0); vm.goTab(com.pursuitiron.app.Tab.Today) }
        }
    }
    if (refsOpen) ReferencesSheet(vm) { refsOpen = false }
}

@Composable
private fun ReferencesSheet(vm: AppViewModel, onClose: () -> Unit) {
    var refs by remember { mutableStateOf<List<JSONObject>?>(null) }
    LaunchedEffect(Unit) { refs = vm.references() }
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("References & methodology", style = MaterialTheme.typography.headlineSmall)
            Text("The programming defaults draw on the following work:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            refs?.forEach { r ->
                PiCard(color = MaterialTheme.colorScheme.surfaceContainerHigh, radius = 18.dp, padding = 12.dp) {
                    Text(r.s("name"), style = MaterialTheme.typography.titleSmall)
                    Text(r.s("detail"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/* App.js WeeklyScheduleSheet: tap a weekday to cycle rest → day 1 → … → rest. Rotation follows it. */
@Composable
fun WeeklyScheduleSheet(vm: AppViewModel, programId: String, onClose: () -> Unit) {
    var sc by remember(programId) { mutableStateOf<JSONObject?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(programId) { sc = vm.schedule(programId) }
    val cs = MaterialTheme.colorScheme
    val names = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    val today = (java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK) - 1) // 0 = Sunday
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Weekly schedule", style = MaterialTheme.typography.headlineSmall)
            val x = sc ?: run { Text("Loading…"); return@Column }
            Text("Tap a day to assign a workout. ${x.i("trainingDays")} training day${if (x.i("trainingDays") == 1) "" else "s"} a week.",
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            x.list("week").forEachIndexed { k, d ->
                val wd = d.i("weekday"); val label = d.s("label")
                Surface(onClick = { scope.launch { sc = vm.cycleSchedule(programId, wd) } },
                    color = if (label.isNotBlank()) cs.primaryContainer else cs.surfaceContainerHigh, shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().semantics { role = Role.Button; contentDescription = "${names[k]}: ${label.ifBlank { "rest" }}. Tap to change" }) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(names[k] + if (wd == today) " · today" else "", style = MaterialTheme.typography.titleSmall, modifier = Modifier.width(110.dp))
                        Text(label.ifBlank { "Rest" }, style = MaterialTheme.typography.bodyMedium, color = if (label.isNotBlank()) cs.onPrimaryContainer else cs.onSurfaceVariant)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { scope.launch { sc = vm.clearSchedule(programId) } }, modifier = Modifier.weight(1f)) { Text("Clear schedule") }
                Button(onClick = onClose, modifier = Modifier.weight(1f)) { Text("Done") }
            }
        }
    }
}
