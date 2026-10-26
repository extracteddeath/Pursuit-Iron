@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.pursuitiron.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pursuitiron.app.AppViewModel
import com.pursuitiron.app.ui.common.PiIcons
import com.pursuitiron.app.ui.common.Segmented
import com.pursuitiron.app.ui.common.b
import com.pursuitiron.app.ui.common.i
import com.pursuitiron.app.ui.common.list
import com.pursuitiron.app.ui.common.s
import com.pursuitiron.app.ui.theme.Motion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.DateFormat
import java.util.Date

private val SESSION_SHORT = mapOf("s20" to "≤20 min", "s40" to "20–40 min", "s60" to "40–60 min", "s90" to "60–90 min", "s120" to "90–120 min", "s120p" to "120+ min")
private val GOAL_SHORT = mapOf("strength" to "Strength", "both" to "Strength & Size", "hypertrophy" to "Hypertrophy")

@Composable
private fun PsCap(t: String, modifier: Modifier = Modifier) = Text(t, style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.4.sp),
    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)

@Composable
private fun PsNote(t: String) = Text(t, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
private fun PsChip(label: String, on: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(Modifier.height(40.dp).clip(if (on) RoundedCornerShape(12.dp) else CircleShape).background(if (on) cs.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent)
        .then(if (on) Modifier else Modifier.border(1.dp, cs.outlineVariant, CircleShape)).clickable(onClick = onClick)
        .semantics { role = Role.RadioButton; selected = on }.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
        Text(label, style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold), color = if (on) cs.onSecondaryContainer else cs.onSurface)
    }
}

@Composable
private fun PsField(value: String, onChange: (String) -> Unit, placeholder: String, desc: String, minHeight: Int = 48, single: Boolean = true, numeric: Boolean = false, modifier: Modifier = Modifier.fillMaxWidth()) {
    val cs = MaterialTheme.colorScheme
    BasicTextField(value, onChange, singleLine = single, textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, color = cs.onSurface), cursorBrush = SolidColor(cs.primary),
        keyboardOptions = if (numeric) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
        modifier = modifier.heightIn(min = minHeight.dp).clip(RoundedCornerShape(14.dp)).background(cs.surfaceContainerHigh).semantics { contentDescription = desc },
        decorationBox = { inner -> Box(Modifier.padding(horizontal = 14.dp, vertical = 13.dp)) { if (value.isEmpty()) Text(placeholder, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp), color = cs.onSurfaceVariant); inner() } })
}

/* The design's Program settings (Main.dc.html pProgSet): goal, capacity, length, approach and rules with a live rebuild
   preview before anything is saved (B905); set and rep targets; each lift's progression; and the layout: shuffle,
   earlier layouts, this roll's seed, and starting again from week 1. */
@Composable
fun ProgramSettingsSheet(vm: AppViewModel, onClose: () -> Unit) {
    var ps by remember { mutableStateOf<JSONObject?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(reload, vm.plan) { ps = vm.programSettings() }
    val p = ps
    val cs = MaterialTheme.colorScheme
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val clip = LocalClipboardManager.current
    var goal by remember(p) { mutableStateOf(p.s("goal", "both")) }
    var weeks by remember(p) { mutableIntStateOf(p.i("weeks", 6)) }
    var session by remember(p) { mutableStateOf(p.s("session", "s60")) }
    var endless by remember(p) { mutableStateOf(p.b("endless")) }
    var deload by remember(p) { mutableStateOf(p.b("deload")) }
    var cap by remember(p) { mutableStateOf(if (p == null || p.isNull("barbellCap")) "none" else p.i("barbellCap").toString()) }
    var noBw by remember(p) { mutableStateOf(p.b("noBodyweight")) }
    var noSs by remember(p) { mutableStateOf(p.b("noSupersets")) }
    var vol by remember(p) { mutableStateOf(p.s("volumeApproach", "standard")) }
    var folder by remember(p) { mutableStateOf(p.s("folder")) }
    var desc by remember(p) { mutableStateOf(p.s("description")) }
    var seedText by remember { mutableStateOf("") }
    var restartAsk by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<(() -> Unit)?>(null) }
    var preview by remember { mutableStateOf<JSONObject?>(null) }
    var previewing by remember { mutableStateOf(false) }
    val draftOpen = vm.editor.b("dirty")
    val locked = p.b("cycleOwned")
    fun settings() = JSONObject().put("goal", goal).put("weeks", weeks).put("session", session)
        .put("endless", endless).put("deload", deload)
        .put("barbellCap", if (cap == "none") JSONObject.NULL else cap.toInt()).put("noBodyweight", noBw).put("noSupersets", noSs)
        .put("volumeApproach", vol).put("folder", folder).put("description", desc)
    val rebuilds = p != null && !locked && (goal != p.s("goal") || weeks != p.i("weeks") || session != p.s("session") || endless != p.b("endless") || deload != p.b("deload") ||
        cap != (if (p.isNull("barbellCap")) "none" else p.i("barbellCap").toString()) || noBw != p.b("noBodyweight") || noSs != p.b("noSupersets") || vol != p.s("volumeApproach"))
    val metaChanged = p != null && (folder != p.s("folder") || desc != p.s("description"))
    val key = settings().toString()
    // The rebuild preview follows the settings as they change; a beat of debounce keeps typing smooth.
    LaunchedEffect(key, rebuilds) {
        if (!rebuilds) { preview = null; previewing = false; return@LaunchedEffect }
        previewing = true; delay(350)
        preview = vm.previewProgramSettings(settings()); previewing = false
    }
    fun guarded(action: () -> Unit) { if (draftOpen) confirm = action else action() }
    fun resetDraft() { reload++; preview = null }

    ModalBottomSheet(onDismissRequest = onClose, sheetState = com.pursuitiron.app.ui.common.rememberSettlingSheet(p)) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Program settings", style = MaterialTheme.typography.headlineSmall.copy(fontSize = 26.sp, lineHeight = 32.sp))
            if (p == null) { Text("Loading…", color = cs.onSurfaceVariant); return@Column }
            val summary = listOf(GOAL_SHORT[p.s("goal")] ?: p.s("goal"), SESSION_SHORT[p.s("session")] ?: p.s("session"),
                (if (p.s("volumeApproach") == "minimalist") "minimalist" else "standard") + " volume",
                if (locked) "block length set by the cycle" else if (p.b("endless")) "endless" else "${p.i("weeks")} weeks" + if (p.b("deload")) " + deload" else "").joinToString(" · ")
            Text("Now: $summary. " + if (locked) "This block's goal, length and volume are set by its training cycle. Duplicate it for a standalone copy you can rebuild."
                else "Changing these rebuilds the program around your history; you see every change before it’s saved.",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 18.sp), color = cs.onSurfaceVariant)
            if (!locked) {
                PsCap("TRAINING GOAL")
                Segmented(listOf("strength" to "Strength", "both" to "Strength & Size", "hypertrophy" to "Hypertrophy"), goal, { goal = it })
                PsCap("SESSION CAPACITY")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    p.list("sessions").forEach { o -> PsChip(SESSION_SHORT[o.s("id")] ?: o.s("label"), session == o.s("id")) { session = o.s("id") } }
                }
                p.list("sessions").firstOrNull { it.s("id") == session }?.let { o -> PsNote(o.s("label") + (if (o.i("count") > 0) " · ~${o.i("count")} exercises a session" else "")) }
                PsCap("BLOCK LENGTH")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.clip(CircleShape).background(cs.surfaceContainerHigh).padding(3.dp).semantics { contentDescription = "Block length" }, verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(38.dp).clip(CircleShape).background(cs.surface).clickable(enabled = !endless) { if (weeks > 1) weeks-- }.semantics { contentDescription = "One week shorter"; role = Role.Button }, contentAlignment = Alignment.Center) { Text("−", fontSize = 18.sp) }
                        Text(if (endless) "Endless" else "$weeks weeks", style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold), textAlign = TextAlign.Center, modifier = Modifier.widthIn(min = 84.dp))
                        Box(Modifier.size(38.dp).clip(CircleShape).background(cs.surface).clickable(enabled = !endless) { if (weeks < 16) weeks++ }.semantics { contentDescription = "One week longer"; role = Role.Button }, contentAlignment = Alignment.Center) { Text("+", fontSize = 18.sp) }
                    }
                    PsChip("Endless", endless) { endless = !endless }
                }
                if (endless) PsNote("No fixed end. Load and volume auto-progress; a light deload drops in when you plateau or fatigue builds.")
                PsCap("TRAINING APPROACH")
                Segmented(listOf("standard" to "Standard", "minimalist" to "Minimalist"), vol, { vol = it })
                PsNote(if (vol == "minimalist") "Fewer hard sets taken closer to failure. Faster sessions with less total volume." else "Balanced weekly volume and recovery. The best default for most lifters.")
                PsCap("BARBELL MOVEMENTS PER SESSION")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("none" to "No limit", "1" to "1", "2" to "2", "3" to "3").forEach { (id, l) -> PsChip(l, cap == id) { cap = id } } }
                Column(Modifier.clip(RoundedCornerShape(22.dp)), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    PsToggle("Deload week", if (endless) "Endless blocks deload on their own" else "Week ${weeks + 1} · reduced volume & load", deload && !endless) { if (!endless) deload = it }
                    PsToggle("Skip bodyweight exercises", "No push-ups, planks, bodyweight squats — only loaded movements", noBw) { noBw = it }
                    PsToggle("No supersets", "Every exercise gets its own rest; nothing is paired", noSs) { noSs = it }
                }
            }
            PsCap("FOLDER")
            PsField(folder, { folder = it.take(40) }, "No folder", "Folder")
            PsCap("DESCRIPTION")
            PsField(desc, { desc = it.take(280) }, "What this program is for", "Description", minHeight = 84, single = false)
            // Rebuild preview (B905): the engine's own rebuild, compared week by week with the program you have.
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(cs.surface).padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PsCap("REBUILD PREVIEW")
                val rv = preview?.optJSONObject("review")
                val lines = remember(rv) { reviewLines(rv) }
                when {
                    previewing -> Text("Building the preview…", style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp), color = cs.onSurfaceVariant)
                    !rebuilds -> Text(if (metaChanged) "Folder and description save without a rebuild." else "Change a setting to preview what the rebuild does before anything is saved.",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp), color = cs.onSurfaceVariant)
                    preview?.b("ok") == false -> Text(preview.s("error", "This rebuild isn't possible with these settings."), style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp), color = cs.error)
                    lines.isEmpty() -> Text("Those settings don’t change any lifts in this program.", style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp), color = cs.onSurfaceVariant)
                    else -> {
                        lines.take(6).forEach { (a, t) ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(a, style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold), color = cs.primary, modifier = Modifier.widthIn(min = 44.dp, max = 120.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(t, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp), modifier = Modifier.weight(1f))
                            }
                        }
                        if (lines.size > 6) Text("+ ${lines.size - 6} more changes", style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = cs.onSurfaceVariant)
                    }
                }
            }
            val n = if (rebuilds) (preview?.optJSONObject("review")?.optInt("count", 0) ?: 0) else if (metaChanged) 1 else 0
            val canSave = (rebuilds && preview?.b("ok") == true && !previewing) || (!rebuilds && metaChanged)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (rebuilds || metaChanged) Box(Modifier.height(54.dp).clip(CircleShape).background(cs.surfaceContainerHigh).clickable { vm.discardRebuild(); resetDraft() }
                    .semantics { role = Role.Button }.padding(horizontal = 18.dp), contentAlignment = Alignment.Center) { Text("Reset", style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold)) }
                Box(Modifier.weight(1f).height(54.dp).clip(CircleShape).background(if (canSave) cs.primary else cs.surfaceContainerHighest)
                    .clickable(enabled = canSave) { guarded { if (rebuilds) vm.commitRebuild("Program saved · ${n.coerceAtLeast(1)} change${if (n == 1) "" else "s"}") { resetDraft() } else vm.applyProgramSettings(settings()) { resetDraft() } } }
                    .semantics { role = Role.Button }, contentAlignment = Alignment.Center) {
                    Text(if (canSave) "Save · ${n.coerceAtLeast(1)} change${if (n.coerceAtLeast(1) == 1) "" else "s"}" else "Nothing to save",
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold), color = if (canSave) cs.onPrimary else cs.onSurfaceVariant)
                }
            }
            if (!locked) {
                PsCap("SET & REP TARGETS", Modifier.padding(top = 6.dp))
                val prog = p.s("progression", "auto")
                Segmented(listOf("auto" to "Auto targets", "manual" to "Manual targets"), prog, { m -> if (m != prog) guarded { vm.applyProgramSettings(JSONObject().put("progression", m)) { resetDraft() } } })
                PsNote(if (prog == "manual") "Your weekly sets and reps stay exactly as you set them. Block reviews won’t rewrite them; loads still progress per lift below."
                    else "Weekly sets and reps adapt to your recovery at each block review. Each lift’s load progresses by the method below.")
            }
            PsCap("LIFT PROGRESSION", Modifier.padding(top = 6.dp))
            LiftProgressionInline(vm, p.s("programId"))
            if (!locked && p.s("engine") == "pursuit-next") {
                PsCap("LAYOUT", Modifier.padding(top = 6.dp))
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(cs.secondaryContainer)
                    .clickable { guarded { vm.rebuildNow(shuffle = true, message = "Shuffled · same muscles, sets and days") { resetDraft() } } }
                    .semantics(mergeDescendants = true) { role = Role.Button }.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Icon(PiIcons.Swap, null, tint = cs.onSecondaryContainer, modifier = Modifier.size(22.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Shuffle exercises", style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold), color = cs.onSecondaryContainer)
                        Text("Deals new variations into the same plan: same muscles, sets and days. Your logged workouts stay.", style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp), color = cs.onSecondaryContainer)
                    }
                }
                val layouts = p.list("layouts")
                if (layouts.isNotEmpty()) {
                    Text("Earlier layouts", style = MaterialTheme.typography.titleSmall.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold), modifier = Modifier.padding(top = 4.dp))
                    Column(Modifier.clip(RoundedCornerShape(22.dp)), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        layouts.forEachIndexed { k, l ->
                            val date = if (l.optLong("at") > 0) DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(l.optLong("at"))) else "Earlier"
                            Row(Modifier.fillMaxWidth().background(cs.surfaceContainerHigh).padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Column(Modifier.weight(1f)) {
                                    Text(date, style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold))
                                    Text(l.s("label"), style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = cs.onSurfaceVariant)
                                }
                                Box(Modifier.height(40.dp).clip(CircleShape).border(1.dp, cs.outlineVariant, CircleShape)
                                    .clickable { guarded { vm.rebuildNow(restoreIndex = k, message = "Restored the layout from $date") { resetDraft() } } }
                                    .semantics { role = Role.Button; contentDescription = "Restore the layout from $date: ${l.s("label")}" }.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                                    Text("Restore", style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold), color = cs.primary)
                                }
                            }
                        }
                    }
                }
                if (!p.isNull("seed")) {
                    PsCap("THIS ROLL’S SEED", Modifier.padding(top = 6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(p.s("seed"), style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp, fontWeight = FontWeight.SemiBold), modifier = Modifier.weight(1f))
                        Box(Modifier.height(38.dp).clip(CircleShape).background(cs.surfaceContainerHigh).clickable { clip.setText(AnnotatedString(p.s("seed"))); vm.toast("Seed copied") }
                            .semantics { role = Role.Button }.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) { Text("Copy", style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold)) }
                    }
                    PsNote("Every shuffle is a roll of the dice. The same seed deals the same lifts into the same slots of this program. To hand the whole program to someone, share its program code instead.")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        PsField(seedText, { seedText = it.filter { c -> c.isDigit() }.take(10) }, "Enter a seed…", "Seed", minHeight = 46, numeric = true, modifier = Modifier.weight(1f))
                        val sv = seedText.toLongOrNull()
                        Box(Modifier.height(46.dp).clip(CircleShape).background(if (sv != null) cs.secondaryContainer else cs.surfaceContainerHigh)
                            .clickable(enabled = sv != null) { guarded { vm.rebuildNow(seed = sv, message = "Built from seed $sv") { seedText = ""; resetDraft() } } }
                            .semantics { role = Role.Button }.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                            Text("Build from seed", style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold), color = if (sv != null) cs.onSecondaryContainer else cs.onSurfaceVariant)
                        }
                    }
                }
                Box(Modifier.fillMaxWidth().padding(top = 6.dp).height(50.dp).clip(CircleShape).border(1.5.dp, cs.outlineVariant, CircleShape).clickable { restartAsk = !restartAsk }
                    .semantics { role = Role.Button; stateDescription = if (restartAsk) "Expanded" else "Collapsed" }, contentAlignment = Alignment.Center) {
                    Text("Start again from week 1", style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold))
                }
                AnimatedVisibility(restartAsk, enter = Motion.expandIn, exit = Motion.shrinkOut) {
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(cs.surfaceContainerHigh).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Both start the block again at week 1 and leave every workout you’ve logged where it is. Same exercises keeps the program as it stands now, including swaps. Original exercises rebuilds it as first created, discarding swaps, set changes, supersets and shuffles.",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp))
                        Button(onClick = { restartAsk = false; vm.resetProgram("same") { onClose() } }, modifier = Modifier.fillMaxWidth().height(46.dp)) { Text("Start again — same exercises", fontWeight = FontWeight.Bold) }
                        androidx.compose.material3.FilledTonalButton(onClick = { restartAsk = false; vm.resetProgram("original") { onClose() } }, modifier = Modifier.fillMaxWidth().height(46.dp)) { Text("Start again — original exercises", fontWeight = FontWeight.Bold) }
                        TextButton(onClick = { restartAsk = false }, modifier = Modifier.fillMaxWidth()) { Text("Keep my changes", fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    }
    confirm?.let { go ->
        AlertDialog(onDismissRequest = { confirm = null }, title = { Text("Drop your unsaved edits?") },
            text = { Text("Rebuilding replaces the draft you're editing. Save it first if you want to keep it.") },
            confirmButton = { Button(onClick = { confirm = null; go() }) { Text("Rebuild anyway") } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } })
    }
}

@Composable
private fun PsToggle(label: String, sub: String, on: Boolean, onChange: (Boolean) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().background(cs.surfaceContainerHigh).clickable { onChange(!on) }.padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold))
            Text(sub, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp), color = cs.onSurfaceVariant)
        }
        Switch(on, onChange, modifier = Modifier.semantics { contentDescription = label })
    }
}

/** The review as the design's lines: a short day or setting name, then what changes. */
private fun reviewLines(rv: JSONObject?): List<Pair<String, String>> {
    rv ?: return emptyList()
    val out = mutableListOf<Pair<String, String>>()
    rv.list("config").forEach { c -> out += c.s("label") to "${c.s("before")} → ${c.s("after")}" }
    val days = rv.list("days")
    val short = days.map { it.s("label").substringBefore(" —").substringBefore(" ·").take(14) }
    days.forEachIndexed { k, d ->
        // Two days that shorten to the same word ("Upper") keep their full names.
        val day = if (short.count { it == short[k] } > 1) d.s("label") else short[k]
        d.list("rows").forEach { r ->
            out += day to when (r.s("kind")) {
                "Added" -> "adds ${r.s("name")}"
                "Removed" -> "drops ${r.s("name")}"
                "Day removed" -> "day removed"
                "Moved / updated" -> "${r.s("name")} moves and updates"
                else -> "${r.s("name")} updated"
            }
        }
        if (d.list("rows").isEmpty() && d.i("beforeMinutes") != d.i("afterMinutes")) out += day to "~${d.i("beforeMinutes")} → ~${d.i("afterMinutes")} min"
    }
    return out
}

/** The design's Lift progression in Program settings: search, then each lift with its method; open one to choose. */
@Composable
private fun LiftProgressionInline(vm: AppViewModel, programId: String) {
    var data by remember(programId) { mutableStateOf<JSONObject?>(null) }
    LaunchedEffect(programId, vm.plan) { data = vm.liftProgression(programId) }
    var q by remember { mutableStateOf("") }
    var open by remember { mutableStateOf<String?>(null) }
    val cs = MaterialTheme.colorScheme
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val d = data ?: return
    if (d.b("percent")) { PsNote("Percentage programs set their main lifts by the method. Change the method from Training maxes."); return }
    PsNote(d.s("note"))
    Row(Modifier.fillMaxWidth().height(44.dp).clip(CircleShape).background(cs.surfaceContainerHigh).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(PiIcons.Search, null, tint = cs.onSurfaceVariant, modifier = Modifier.size(16.dp))
        BasicTextField(q, { q = it }, singleLine = true, textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, color = cs.onSurface), cursorBrush = SolidColor(cs.primary),
            modifier = Modifier.weight(1f).semantics { contentDescription = "Search a lift" },
            decorationBox = { inner -> Box { if (q.isEmpty()) Text("Search a lift", style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp), color = cs.onSurfaceVariant); inner() } })
    }
    val lifts = d.list("lifts").filter { q.isBlank() || it.s("name").contains(q.trim(), ignoreCase = true) }
    if (lifts.isEmpty()) { Text("No lift matches that.", style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp), color = cs.onSurfaceVariant); return }
    Column(Modifier.clip(RoundedCornerShape(22.dp)), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        lifts.forEach { l ->
            val id = l.s("id"); val isOpen = open == id
            Column(Modifier.fillMaxWidth().background(cs.surfaceContainerHigh)) {
                Row(Modifier.fillMaxWidth().clickable { open = if (isOpen) null else id }.semantics { stateDescription = if (isOpen) "Expanded" else "Collapsed" }.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(l.s("name"), style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${l.s("dayLabel")} · ${l.s("label")}", style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(if (l.b("locked")) "Yours" else "Auto", style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                        color = if (l.b("locked")) cs.onPrimary else cs.onSurfaceVariant,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if (l.b("locked")) cs.primary else cs.surfaceContainerHighest).padding(horizontal = 8.dp, vertical = 3.dp))
                }
                AnimatedVisibility(isOpen, enter = Motion.expandIn, exit = Motion.shrinkOut) {
                    Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(l.s("reason"), style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp), color = cs.onSurfaceVariant)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            d.list("styles").forEach { st ->
                                val sel = if (st.s("id") == "auto") !l.b("locked") else l.b("locked") && l.s("style") == st.s("id")
                                PsChip(st.s("label"), sel) { if (!sel) scope.launch { vm.setLiftProgression(programId, id, st.s("id"))?.let { data = it } } }
                            }
                        }
                    }
                }
            }
        }
    }
}
