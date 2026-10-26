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

/* App.js Settings → Training preferences: experience and the effort scale. */
@Composable
fun TrainingPrefsCard(vm: AppViewModel) {
    val st = vm.settings
    PiCard(radius = 28.dp) {
        Text("Training preferences", style = MaterialTheme.typography.titleMedium)
        Kicker("Training experience")
        Text("Sets your weekly volume range", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val exp = st.s("experience").let { if (it == "novice") "none" else it }
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("none" to "New", "beginner" to "Beginner", "intermediate" to "Intermediate", "advanced" to "Advanced").forEach { (id, l) ->
                com.pursuitiron.app.ui.common.MorphChip(l, exp == id, { vm.updateSetting("experience", id) }, height = 40.dp)
            }
        }
        Kicker("Effort scale")
        Text("Targets read as reps in reserve, or as RPE", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Segmented(listOf("rir" to "RIR", "rpe" to "RPE"), st.s("loadMode", "rir"), { vm.updateSetting("loadMode", it) })
    }
}

/* App.js Settings → Data: banned list, history, reset, device storage. */
@Composable
fun ManageDataCard(vm: AppViewModel) {
    val st = vm.settings
    val cs = MaterialTheme.colorScheme
    var bannedOpen by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<String?>(null) }
    var storage by remember { mutableStateOf<JSONObject?>(null) }
    LaunchedEffect(st) { storage = vm.storageStatus() }
    PiCard(radius = 28.dp) {
        Text("Manage data", style = MaterialTheme.typography.titleMedium)
        Column(Modifier.clip(RoundedCornerShape(20.dp)), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            val banned = st.list("banned")
            DataRow("Banned exercises", "${banned.size} hidden from generation", if (banned.isNotEmpty()) "Review" else null) { bannedOpen = true }
            DataRow("Workout history", "${st.i("sessions")} sessions logged", if (st.i("sessions") > 0) "Clear" else null, danger = true) { confirm = "history" }
        }
        OutlinedButton(onClick = { confirm = "reset" }, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("Reset everything", color = cs.error) }
        storage?.let { x ->
            Kicker("Device storage")
            val kb = x.i("bytes") / 1024
            Text("${if (kb >= 1024) String.format(Locale.US, "%.1f MB", kb / 1024.0) else "$kb KB"} in this app's private storage on this phone. " +
                "Uninstalling the app removes it, so keep a backup file somewhere else.",
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
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
