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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pursuitiron.app.AppViewModel
import com.pursuitiron.app.ui.common.Kicker
import com.pursuitiron.app.ui.common.MorphChip
import com.pursuitiron.app.ui.common.PiIcons
import com.pursuitiron.app.ui.common.ProgressBar
import com.pursuitiron.app.ui.common.Segmented
import com.pursuitiron.app.ui.common.b
import com.pursuitiron.app.ui.common.d
import com.pursuitiron.app.ui.common.fmtNum
import com.pursuitiron.app.ui.common.i
import com.pursuitiron.app.ui.common.list
import com.pursuitiron.app.ui.common.o
import com.pursuitiron.app.ui.common.s
import com.pursuitiron.app.ui.common.strings
import org.json.JSONObject
import java.text.DateFormat
import java.util.Date

/* ------------------------------------------------------------------ program settings (App.js ProgramSettingsSheet + seed sheet) */
@Composable
fun ProgramSettingsSheet(vm: AppViewModel, onClose: () -> Unit) {
    var ps by remember { mutableStateOf<JSONObject?>(null) }
    LaunchedEffect(Unit) { ps = vm.programSettings() }
    val p = ps
    val cs = MaterialTheme.colorScheme
    var goal by remember(p) { mutableStateOf(p.s("goal", "both")) }
    var weeks by remember(p) { mutableIntStateOf(p.i("weeks", 6)) }
    var session by remember(p) { mutableStateOf(p.s("session", "s60")) }
    var progression by remember(p) { mutableStateOf(p.s("progression", "auto")) }
    var endless by remember(p) { mutableStateOf(p.b("endless")) }
    var deload by remember(p) { mutableStateOf(p.b("deload")) }
    var cap by remember(p) { mutableStateOf(if (p == null || p.isNull("barbellCap")) "none" else p.i("barbellCap").toString()) }
    var noBw by remember(p) { mutableStateOf(p.b("noBodyweight")) }
    var noSs by remember(p) { mutableStateOf(p.b("noSupersets")) }
    var vol by remember(p) { mutableStateOf(p.s("volumeApproach", "standard")) }
    var folder by remember(p) { mutableStateOf(p.s("folder")) }
    var desc by remember(p) { mutableStateOf(p.s("description")) }
    var confirm by remember { mutableStateOf<(() -> Unit)?>(null) }
    val draftOpen = vm.editor.b("dirty")
    fun settings() = JSONObject().put("goal", goal).put("weeks", weeks).put("session", session).put("progression", progression)
        .put("endless", endless).put("deload", deload)
        .put("barbellCap", if (cap == "none") JSONObject.NULL else cap.toInt()).put("noBodyweight", noBw).put("noSupersets", noSs)
        .put("volumeApproach", vol).put("folder", folder).put("description", desc)
    val rebuilds = p != null && (goal != p.s("goal") || weeks != p.i("weeks") || session != p.s("session") || progression != p.s("progression") || endless != p.b("endless") || deload != p.b("deload") ||
        cap != (if (p.isNull("barbellCap")) "none" else p.i("barbellCap").toString()) || noBw != p.b("noBodyweight") || noSs != p.b("noSupersets") || vol != p.s("volumeApproach"))
    fun guarded(action: () -> Unit) { if (draftOpen) confirm = action else action() }
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Program settings", style = MaterialTheme.typography.headlineSmall)
            if (p == null) { Text("Loading…"); return@Column }
            if (p.b("cycleOwned")) Text("This block's goal, length and volume are set by its training cycle. Duplicate it for a standalone copy you can rebuild.",
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            val locked = p.b("cycleOwned")
            if (!locked) {
            Kicker("Training goal")
            Segmented(listOf("strength" to "Strength", "hypertrophy" to "Size", "both" to "Both"), goal, { if (!locked) goal = it })
            Kicker("Block length")
            Column(Modifier.clip(RoundedCornerShape(20.dp)), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                ToggleRow("Continuous", "No fixed end: load and volume keep progressing, with automatic deloads", endless) { if (!locked) endless = it }
                if (!endless) ToggleRow("Deload week", "A lighter week ${weeks + 1} after the block", deload) { if (!locked) deload = it }
            }
            if (!endless) Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { if (!locked && weeks > 1) weeks-- }) { Icon(PiIcons.Minus, "Decrease block length") }
                Text("$weeks weeks", style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(96.dp), textAlign = TextAlign.Center)
                IconButton(onClick = { if (!locked && weeks < 16) weeks++ }) { Icon(PiIcons.Plus, "Increase block length") }
            }
            Kicker("Time per session")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("s20" to "20 min", "s40" to "40 min", "s60" to "60 min", "s90" to "90 min", "s120" to "2 hours", "s120p" to "2+ hours").forEach { (id, l) ->
                    MorphChip(l, session == id, { if (!locked) session = id }, height = 36.dp)
                }
            }
            Kicker("Set and rep targets")
            Segmented(listOf("auto" to "Auto: periodized", "manual" to "Manual: your own"), progression, { if (!locked) progression = it })
            Kicker("Training approach")
            Segmented(listOf("standard" to "Standard volume", "minimalist" to "Minimalist"), vol, { if (!locked) vol = it })
            Kicker("Barbell movements per session")
            Segmented(listOf("none" to "No limit", "1" to "1", "2" to "2", "3" to "3"), cap, { if (!locked) cap = it })
            Column(Modifier.clip(RoundedCornerShape(20.dp)), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                ToggleRow("Skip bodyweight exercises", "Replace them with loaded ones", noBw) { if (!locked) noBw = it }
                ToggleRow("No supersets", "Budget the workout as straight sets only", noSs) { if (!locked) noSs = it }
            }
            }
            OutlinedTextField(folder, { folder = it.take(40) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Folder") }, placeholder = { Text("e.g. Bulk 2026") })
            OutlinedTextField(desc, { desc = it.take(500) }, Modifier.fillMaxWidth(), label = { Text("Description") }, minLines = 2)
            if (rebuilds) Text("Saving rebuilds this program with the engine from the same seed. Lifts you added are kept.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            Button(onClick = { guarded { vm.applyProgramSettings(settings()) { onClose() } } }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text(if (rebuilds) "Save and rebuild" else "Save program settings")
            }
            // The design keeps the lift-by-lift progression in Program settings.
            (vm.plan?.s("programId"))?.takeIf { it.isNotBlank() }?.let { pid -> LiftProgressionCard(vm, pid) }
            if (!locked && p.s("engine") == "pursuit-next") {
                Kicker("Exercise layout", modifier = Modifier.padding(top = 8.dp))
                OutlinedButton(onClick = { guarded { vm.applyProgramSettings(null, shuffle = true) { onClose() } } }, modifier = Modifier.fillMaxWidth()) {
                    Icon(PiIcons.Swap, null); Text("  Shuffle exercises")
                }
                val rolls = p.list("rolls")
                if (rolls.isNotEmpty()) {
                    Text("Previous layouts", style = MaterialTheme.typography.titleSmall)
                    Column(Modifier.clip(RoundedCornerShape(18.dp)), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        rolls.forEach { r ->
                            Row(Modifier.fillMaxWidth().background(cs.surfaceContainerHigh).clickable { guarded { vm.applyProgramSettings(null, seed = r.optLong("seed")) { onClose() } } }.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(r.strings("lifts").joinToString(" · ").ifBlank { "Layout" }, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (r.optLong("at") > 0) Text("Replaced ${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(r.optLong("at")))}", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                                }
                                Text("Restore", style = MaterialTheme.typography.labelLarge, color = cs.primary)
                            }
                        }
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
private fun ToggleRow(label: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().background(cs.surfaceContainerHigh).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(label, style = MaterialTheme.typography.titleSmall); Text(sub, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant) }
        Switch(checked = checked, onCheckedChange = onChange, modifier = Modifier.semantics { contentDescription = label })
    }
}

/* ------------------------------------------------------------------ compare (App.js CompareView) */
@Composable
fun CompareSheet(vm: AppViewModel, onClose: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val colA = cs.primary; val colB = cs.tertiary
    var mode by remember { mutableStateOf("programs") }
    val programs = vm.plan.list("programs")
    var a by remember { mutableStateOf(programs.getOrNull(0)?.s("id")) }
    var b by remember { mutableStateOf(programs.getOrNull(1)?.s("id") ?: programs.getOrNull(0)?.s("id")) }
    var xa by remember { mutableStateOf<JSONObject?>(null) }
    var xb by remember { mutableStateOf<JSONObject?>(null) }
    var res by remember { mutableStateOf<JSONObject?>(null) }
    var pick by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(mode, a, b, xa, xb) {
        res = if (mode == "programs") { if (a != null && b != null) vm.comparePrograms(a!!, b!!) else null }
        else { if (xa != null && xb != null) vm.compareExercises(xa!!.s("id"), xb!!.s("id")) else null }
    }
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Compare", style = MaterialTheme.typography.headlineSmall)
            Segmented(listOf("programs" to "Programs", "exercises" to "Exercises"), mode, { mode = it; res = null })
            if (mode == "programs") {
                if (programs.size < 2) { Text("Save a second program to compare.", color = cs.onSurfaceVariant); return@Column }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProgramPicker("A", programs, a, colA, Modifier.weight(1f)) { a = it }
                    ProgramPicker("B", programs, b, colB, Modifier.weight(1f)) { b = it }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PickButton("Exercise A", xa?.s("name") ?: "Choose", colA, Modifier.weight(1f)) { pick = "A" }
                    PickButton("Exercise B", xb?.s("name") ?: "Choose", colB, Modifier.weight(1f)) { pick = "B" }
                }
            }
            val r = res ?: return@Column
            val ra = r.o("a"); val rb = r.o("b")
            Header(ra.s("name"), rb.s("name"), colA, colB)
            if (mode == "programs") {
                CompareRow("Goal", ra.s("goal"), rb.s("goal"))
                CompareRow("Split", ra.s("split"), rb.s("split"))
                CompareRow("Days / week", "${ra.i("days")}", "${rb.i("days")}", ra.i("days"), rb.i("days"), colA, colB)
                CompareRow("Weeks", "${ra.i("weeks")}", "${rb.i("weeks")}")
                CompareRow("Sets / week", "${ra.i("totalSets")}", "${rb.i("totalSets")}", ra.i("totalSets"), rb.i("totalSets"), colA, colB)
                CompareRow("Time / week", hm(ra.i("weeklyMin")), hm(rb.i("weeklyMin")))
                CompareRow("Exercises", "${ra.i("exercises")}", "${rb.i("exercises")}", ra.i("exercises"), rb.i("exercises"), colA, colB)
                CompareRow("Compound · iso", "${ra.i("comp")} · ${ra.i("iso")}", "${rb.i("comp")} · ${rb.i("iso")}")
                MuscleBars(r.list("muscles"), colA, colB, "Weekly sets per muscle")
                val shared = r.strings("shared"); val onlyA = r.strings("onlyA"); val onlyB = r.strings("onlyB")
                Kicker("Exercise selection · ${shared.size} shared", modifier = Modifier.padding(top = 6.dp))
                if (onlyA.isNotEmpty()) Text("Only in A: ${onlyA.joinToString(", ")}", style = MaterialTheme.typography.bodySmall, color = colA)
                if (onlyB.isNotEmpty()) Text("Only in B: ${onlyB.joinToString(", ")}", style = MaterialTheme.typography.bodySmall, color = colB)
                if (shared.isNotEmpty()) Text("Both: ${shared.joinToString(", ")}", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            } else {
                CompareRow("Primary muscle", ra.s("part"), rb.s("part"))
                CompareRow("Type", ra.s("type").replaceFirstChar { it.uppercase() }, rb.s("type").replaceFirstChar { it.uppercase() })
                CompareRow("Equipment", ra.strings("equip").joinToString(", ").ifBlank { "Bodyweight" }, rb.strings("equip").joinToString(", ").ifBlank { "Bodyweight" })
                CompareRow("Rep range", ra?.optJSONArray("rep")?.let { "${it.optInt(0)}–${it.optInt(1)}" } ?: "—", rb?.optJSONArray("rep")?.let { "${it.optInt(0)}–${it.optInt(1)}" } ?: "—")
                CompareRow("Pattern", ra.s("pattern").replaceFirstChar { it.uppercase() }, rb.s("pattern").replaceFirstChar { it.uppercase() })
                CompareRow("Tempo", ra.s("tempo"), rb.s("tempo"))
                CompareRow("Axial / systemic", if (ra.b("axial")) "Yes" else "No", if (rb.b("axial")) "Yes" else "No")
                MuscleBars(r.list("muscles"), colA, colB, "Muscles worked (share of the lift)", percent = true)
            }
        }
    }
    pick?.let { which -> ExercisePickSheet(vm, { picked -> if (which == "A") xa = picked else xb = picked; pick = null }) { pick = null } }
}

private fun hm(m: Int) = if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m"

@Composable
private fun Header(a: String, b: String, colA: Color, colB: Color) {
    Row(Modifier.padding(top = 6.dp)) {
        Box(Modifier.weight(1f))
        Text(a, color = colA, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 10.dp).width(104.dp), textAlign = TextAlign.End)
        Text(b, color = colB, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 10.dp).width(104.dp), textAlign = TextAlign.End)
    }
}

@Composable
private fun CompareRow(label: String, a: String, b: String, an: Int? = null, bn: Int? = null, colA: Color = Color.Unspecified, colB: Color = Color.Unspecified) {
    val cs = MaterialTheme.colorScheme
    val aWin = an != null && bn != null && an > bn; val bWin = an != null && bn != null && bn > an
    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(a, style = MaterialTheme.typography.titleSmall, fontWeight = if (aWin) FontWeight.ExtraBold else null, color = if (aWin) colA else cs.onSurface, modifier = Modifier.padding(start = 10.dp).width(104.dp), textAlign = TextAlign.End, maxLines = 2)
        Text(b, style = MaterialTheme.typography.titleSmall, fontWeight = if (bWin) FontWeight.ExtraBold else null, color = if (bWin) colB else cs.onSurface, modifier = Modifier.padding(start = 10.dp).width(104.dp), textAlign = TextAlign.End, maxLines = 2)
    }
}

@Composable
private fun MuscleBars(rows: List<JSONObject>, colA: Color, colB: Color, title: String, percent: Boolean = false) {
    if (rows.isEmpty()) return
    val max = rows.maxOf { maxOf(it.d("a"), it.d("b")) }.coerceAtLeast(0.01)
    Kicker(title, modifier = Modifier.padding(top = 6.dp))
    rows.forEach { m ->
        val fa = if (percent) "${(m.d("a") * 100).toInt()}%" else fmtNum(m.d("a")); val fb = if (percent) "${(m.d("b") * 100).toInt()}%" else fmtNum(m.d("b"))
        Column(Modifier.semantics(mergeDescendants = true) { contentDescription = "${m.s("label")}: A $fa, B $fb" }) {
            Text(m.s("label"), style = MaterialTheme.typography.labelMedium)
            Row(verticalAlignment = Alignment.CenterVertically) { ProgressBar((m.d("a") / max).toFloat(), Modifier.weight(1f), color = colA, height = 7.dp); Text(" A $fa", style = MaterialTheme.typography.labelSmall, color = colA, modifier = Modifier.width(56.dp)) }
            Row(verticalAlignment = Alignment.CenterVertically) { ProgressBar((m.d("b") / max).toFloat(), Modifier.weight(1f), color = colB, height = 7.dp); Text(" B $fb", style = MaterialTheme.typography.labelSmall, color = colB, modifier = Modifier.width(56.dp)) }
        }
    }
}

@Composable
private fun ProgramPicker(label: String, programs: List<JSONObject>, selected: String?, accent: Color, modifier: Modifier, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        PickButton("Program $label", programs.firstOrNull { it.s("id") == selected }?.s("name") ?: "Choose", accent, Modifier.fillMaxWidth()) { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            programs.forEach { p -> DropdownMenuItem(text = { Text(p.s("name")) }, onClick = { open = false; onPick(p.s("id")) }) }
        }
    }
}

@Composable
private fun PickButton(label: String, value: String, accent: Color, modifier: Modifier, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(cs.surfaceContainerHigh).clickable(onClick = onClick).padding(12.dp)
        .semantics(mergeDescendants = true) { contentDescription = "$label: $value. Change" }) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = accent)
        Text(value, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ExercisePickSheet(vm: AppViewModel, onPick: (JSONObject) -> Unit, onClose: () -> Unit) {
    var q by remember { mutableStateOf("") }
    var items by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    LaunchedEffect(q) { items = vm.searchExercises(q).take(80) }
    val cs = MaterialTheme.colorScheme
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Search exercises") })
            LazyColumn(Modifier.clip(RoundedCornerShape(22.dp)).heightIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(items) { x ->
                    Column(Modifier.fillMaxWidth().background(cs.surfaceContainerHigh).clickable { onPick(x) }.padding(14.dp)) {
                        Text(x.s("name"), style = MaterialTheme.typography.titleSmall)
                        Text(x.s("partLabel"), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
