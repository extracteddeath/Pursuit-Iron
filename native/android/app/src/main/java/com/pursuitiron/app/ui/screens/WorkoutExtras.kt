@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.pursuitiron.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pursuitiron.app.AppViewModel
import com.pursuitiron.app.ui.common.Badge
import com.pursuitiron.app.ui.common.Kicker
import com.pursuitiron.app.ui.common.MorphChip
import com.pursuitiron.app.ui.common.PiCard
import com.pursuitiron.app.ui.common.PiIcons
import com.pursuitiron.app.ui.common.Segmented
import com.pursuitiron.app.ui.common.b
import com.pursuitiron.app.ui.common.d
import com.pursuitiron.app.ui.common.fmtNum
import com.pursuitiron.app.ui.common.i
import com.pursuitiron.app.ui.common.list
import com.pursuitiron.app.ui.common.o
import com.pursuitiron.app.ui.common.s
import org.json.JSONArray
import org.json.JSONObject

/* App.js readiness check-in: shown once when a workout opens; it scales today's loads. */
private val READINESS = listOf(
    Triple("Feeling strong", "Hit the prescribed loads", 1.0),
    Triple("A little off", "Trim loads ~5%", 0.95),
    Triple("Run down", "Back off ~12%, focus on quality", 0.88),
)

@Composable
fun ReadinessCheckSheet(vm: AppViewModel) {
    val cs = MaterialTheme.colorScheme
    val pick = { label: String, factor: Double -> vm.act("readiness", "label" to label, "factor" to factor) }
    ModalBottomSheet(onDismissRequest = { pick("—", 1.0) }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("How are you feeling?", style = MaterialTheme.typography.headlineSmall)
            Text("Today's loads adjust to match. Logged sets never change.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            READINESS.forEachIndexed { k, (label, sub, factor) ->
                Surface(onClick = { pick(label, factor) }, color = cs.surfaceContainerHigh, shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth().semantics { role = Role.Button }) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Icon(if (k == 0) PiIcons.Flame else PiIcons.Timer, null, tint = cs.primary)
                        Column(Modifier.weight(1f)) {
                            Text(label, style = MaterialTheme.typography.titleSmall)
                            Text(sub, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                        }
                        Icon(PiIcons.ChevronRight, null, tint = cs.onSurfaceVariant)
                    }
                }
            }
            TextButton(onClick = { pick("—", 1.0) }, modifier = Modifier.fillMaxWidth()) { Text("Skip") }
        }
    }
}

/* App.js finish sheet: what the workout earned, then pump/soreness per muscle before it is saved. */
@Composable
fun FinishSheet(vm: AppViewModel, onClose: () -> Unit) {
    var p by remember { mutableStateOf<JSONObject?>(null) }
    var reload by remember { mutableStateOf(0) }
    LaunchedEffect(reload) { p = vm.finishPreview() }
    var routine by remember { mutableStateOf("") }
    var saveAsRoutine by remember { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val x = p ?: run { Text("Loading…"); return@Column }
            val u = x.s("unit")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(PiIcons.Trophy, null, tint = cs.primary)
                Text("Workout complete", style = MaterialTheme.typography.headlineSmall)
            }
            Text("${x.i("doneSets")} of ${x.i("totalSets")} sets logged", color = cs.onSurfaceVariant)
            val prs = x.list("prs")
            if (prs.isNotEmpty()) PiCard(color = cs.primaryContainer, radius = 22.dp, padding = 14.dp) {
                Text("${prs.size} estimated-1RM PR${if (prs.size == 1) "" else "s"}!", style = MaterialTheme.typography.titleSmall, color = cs.onPrimaryContainer)
                prs.forEach { r -> Row { Text(r.s("name"), Modifier.weight(1f), color = cs.onPrimaryContainer); Text("+${fmtNum(r.d("gain"))} $u", color = cs.onPrimaryContainer) } }
            }
            x.o("mainE1rm")?.let { m ->
                PiCard(color = cs.surfaceContainerHigh, radius = 22.dp, padding = 14.dp) {
                    Kicker("Estimated 1RM · ${m.s("name")}")
                    Text("${m.i("est")} $u", style = MaterialTheme.typography.headlineMedium, color = cs.primary)
                    Text("from your top set of ${fmtNum(m.d("weight"))} $u × ${m.i("reps")}" + (if (m.i("prev") > 0) " · previous best ${m.i("prev")} $u" else ""),
                        style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
            }
            val goals = x.list("goalsHit")
            if (goals.isNotEmpty()) PiCard(color = cs.primaryContainer, radius = 22.dp, padding = 14.dp) {
                Text("${goals.size} goal${if (goals.size == 1) "" else "s"} reached", style = MaterialTheme.typography.titleSmall, color = cs.onPrimaryContainer)
                goals.forEach { g -> Row { Text(g.s("name"), Modifier.weight(1f), color = cs.onPrimaryContainer); Text("${fmtNum(g.d("goal"))} $u ✓", color = cs.onPrimaryContainer) } }
            }
            val next = x.list("nextPlan")
            if (next.isNotEmpty()) PiCard(color = cs.surfaceContainerHigh, radius = 22.dp, padding = 14.dp) {
                Text("Next session", style = MaterialTheme.typography.titleSmall)
                next.forEach { n ->
                    val delta = n.d("delta")
                    val what = when {
                        delta > 0 -> "+${fmtNum(delta)} $u → ${fmtNum(n.d("weight"))}"
                        delta < 0 -> "${fmtNum(delta)} $u → ${fmtNum(n.d("weight"))}"
                        else -> "Hold ${fmtNum(n.d("from"))}, build reps"
                    }
                    Row { Text(n.s("name"), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium); Text(what, style = MaterialTheme.typography.labelLarge, color = if (delta > 0) cs.primary else cs.onSurfaceVariant) }
                }
            }
            val parts = x.list("parts")
            if (parts.isNotEmpty()) {
                Kicker("How did each muscle feel?")
                Text("Pump today and soreness since last time tune next week's volume.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                parts.forEach { pt ->
                    PiCard(color = cs.surfaceContainer, radius = 20.dp, padding = 12.dp) {
                        Text(pt.s("label").replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.titleSmall)
                        FeedbackRow("Pump", x.optJSONArray("pumpChoices"), pt.s("pump")) { v -> vm.act("feedback", "part" to pt.s("part"), "pump" to v); reload++ }
                        FeedbackRow("Recovered", x.optJSONArray("soreChoices"), pt.s("sore")) { v -> vm.act("feedback", "part" to pt.s("part"), "sore" to v); reload++ }
                    }
                }
            }
            val ids = (0 until (x.optJSONArray("routineIds")?.length() ?: 0)).map { x.getJSONArray("routineIds").optString(it) }
            if (x.b("quick") && ids.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().toggleable(saveAsRoutine, role = Role.Switch) { saveAsRoutine = it }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("Save as a routine", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    androidx.compose.material3.Switch(saveAsRoutine, null)
                }
                if (saveAsRoutine) OutlinedTextField(routine, { routine = it.take(40) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Name this routine") })
            }
            Button(onClick = { onClose(); vm.finish(if (saveAsRoutine) routine.ifBlank { "My routine" } else null, ids) }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Save workout") }
            TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Keep going") }
        }
    }
}

@Composable
private fun FeedbackRow(label: String, choices: JSONArray?, current: String, onPick: (String) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(76.dp))
        (0 until (choices?.length() ?: 0)).forEach { k ->
            val c = choices!!.getJSONArray(k); val id = c.optString(0)
            MorphChip(c.optString(1), current == id, { onPick(id) }, height = 36.dp)
        }
    }
}

/* App.js pendingProgramUpdate prompt: keep a mid-workout progression change in the program, or not. */
@Composable
fun PendingUpdateDialog(vm: AppViewModel) {
    val p = vm.pendingUpdate ?: return
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Update ${p.s("programName").ifBlank { "the program" }}?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("You changed how these lifts progress during the workout:")
                p.list("changes").forEach { c -> Text("• ${c.s("name")}: ${c.s("style")}", style = MaterialTheme.typography.bodyMedium) }
            }
        },
        confirmButton = { Button(onClick = { vm.completeProgramUpdate(true) }) { Text("Update program") } },
        dismissButton = { TextButton(onClick = { vm.completeProgramUpdate(false) }) { Text("This workout only") } },
    )
}

/* App.js add-exercise sheet (quick workout, or extra work in any session). */
@Composable
fun AddLiftSheet(vm: AppViewModel, onClose: () -> Unit) {
    var q by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    LaunchedEffect(q) { results = vm.searchExercises(q).take(40) }
    val cs = MaterialTheme.colorScheme
    val have = vm.session?.list("exercises")?.map { it.s("id") }?.toSet().orEmpty()
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Add a lift", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 4.dp))
            OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Search exercises") })
            androidx.compose.foundation.lazy.LazyColumn(Modifier.height(420.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(results.size) { k ->
                    val x = results[k]; val added = x.s("id") in have
                    Surface(onClick = { if (!added) { vm.act("addExercise", "id" to x.s("id")); vm.toast("Added ${x.s("name")}"); onClose() } },
                        color = cs.surfaceContainerHigh, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(x.s("name"), style = MaterialTheme.typography.titleSmall)
                                Text(x.s("partLabel", x.s("part")).replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                            }
                            if (added) Badge("In workout") else Icon(PiIcons.Plus, null)
                        }
                    }
                }
            }
        }
    }
}

/* Per-lift rest (App.js restCustom): saved to the program so next time uses it too. */
@Composable
fun RestTimeSheet(vm: AppViewModel, ex: JSONObject, onClose: () -> Unit) {
    val cur = ex.i("restCustom"); val def = ex.i("restDefault")
    val opts = listOf(60, 90, 120, 150, 180, 240, 300)
    ModalBottomSheet(onDismissRequest = onClose) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Kicker(ex.s("name"))
            Text("Rest between sets", style = MaterialTheme.typography.headlineSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MorphChip("Default${if (def > 0) " (${restLabel(def)})" else ""}", cur == 0, { vm.act("restFor", "ei" to ex.i("ei"), "sec" to 0); onClose() }, height = 44.dp)
                opts.forEach { sec -> MorphChip(restLabel(sec), cur == sec, { vm.act("restFor", "ei" to ex.i("ei"), "sec" to sec); onClose() }, height = 44.dp) }
            }
            if (!(vm.session?.b("quick") ?: false)) Text("Saved for this lift in the program.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
fun restLabel(sec: Int) = if (sec % 60 == 0) "${sec / 60} min" else "${sec / 60}:${(sec % 60).toString().padStart(2, '0')}"

/* App.js progression picker for one lift: this workout first, the program on finish. */
@Composable
fun ProgressionSheet(vm: AppViewModel, s: JSONObject, ex: JSONObject, onClose: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val cur = ex.o("progression")?.s("style")
    ModalBottomSheet(onDismissRequest = onClose) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Kicker(ex.s("name"))
            Text("Progression", style = MaterialTheme.typography.headlineSmall)
            Text("Applies to today's remaining sets. When you finish, you choose whether the program keeps it.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            // Each style says what it does; Auto names what it would pick for this lift right now.
            val opts = ex.o("progression").list("options").ifEmpty { s.list("progressionStyles") }
            Column(Modifier.clip(RoundedCornerShape(22.dp)), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                opts.forEach { st ->
                    val on = st.s("id") == cur
                    val ring by androidx.compose.animation.core.animateDpAsState(if (on) 6.dp else 2.dp, com.pursuitiron.app.ui.theme.Motion.spring(), label = "radio")
                    val fg = if (on) cs.onSecondaryContainer else cs.onSurface
                    Row(Modifier.fillMaxWidth().background(if (on) cs.secondaryContainer else cs.surfaceContainerHigh)
                        .clickable { if (!on) vm.act("progression", "ei" to ex.i("ei"), "style" to st.s("id")); onClose() }
                        .semantics(mergeDescendants = true) { role = Role.RadioButton; selected = on }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        androidx.compose.foundation.layout.Box(Modifier.size(20.dp).border(ring, fg, androidx.compose.foundation.shape.CircleShape))
                        Column(Modifier.weight(1f)) {
                            Text(st.s("label"), style = MaterialTheme.typography.titleSmall, color = fg)
                            st.s("detail").takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = fg.copy(alpha = 0.8f)) }
                        }
                    }
                }
            }
        }
    }
}

/* Goal weight, machine weight step and setup notes: the lift's settings that outlive the workout. */
@Composable
fun LiftSettingsSheet(vm: AppViewModel, s: JSONObject, ex: JSONObject, onClose: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val u = s.s("unit")
    val id = ex.s("id")
    var goal by remember { mutableStateOf(if (ex.d("goal") > 0) fmtNum(ex.d("goal")) else "") }
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Kicker(ex.s("name"))
            Text("Lift settings", style = MaterialTheme.typography.headlineSmall)
            Text("Goal weight", style = MaterialTheme.typography.titleSmall)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(goal, { goal = com.pursuitiron.app.ui.common.decimalText(it, 6) }, Modifier.weight(1f), singleLine = true,
                    label = { Text("Target ($u)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                Button(onClick = { vm.setGoal(id, goal.toDoubleOrNull()); vm.toast(if (goal.isBlank()) "Goal cleared" else "Goal set") }, modifier = Modifier.height(56.dp)) { Text("Set") }
            }
            Text("Weight step", style = MaterialTheme.typography.titleSmall)
            Text("For machines and stacks that jump in bigger steps. Applies to every block of this program.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            val step = ex.d("step")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MorphChip("Default", step <= 0, { vm.setExLoadStep(id, null) }, height = 40.dp)
                val choices = ex.optJSONArray("stepChoices")
                (0 until (choices?.length() ?: 0)).forEach { k -> val v = choices!!.optDouble(k); MorphChip("${fmtNum(v)} $u", step == v, { vm.setExLoadStep(id, v) }, height = 40.dp) }
            }
            Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Done") }
        }
    }
}

/* App.js suggested supersets: pair this lift with one that doesn't compete for the same muscles. */
@Composable
fun SupersetSheet(vm: AppViewModel, s: JSONObject, onClose: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val ei = s.i("exIdx")
    val ex = s.list("exercises").getOrNull(ei) ?: run { onClose(); return }
    ModalBottomSheet(onDismissRequest = onClose) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Kicker(ex.s("name"))
            Text(if (ex.b("superset")) "In a superset" else "Superset with…", style = MaterialTheme.typography.headlineSmall)
            if (ex.b("superset")) {
                Text("Sets alternate between the paired lifts, resting after each round.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                OutlinedButton(onClick = { vm.act("superset", "ei" to ei); onClose() }, modifier = Modifier.fillMaxWidth()) { Text("Unpair") }
                return@Column
            }
            val sug = s.list("supersetSuggestions")
            if (sug.isEmpty()) Text("No good partner among the remaining lifts.", color = cs.onSurfaceVariant)
            sug.forEach { x ->
                Surface(onClick = { vm.act("pairWith", "ei" to ei, "partner" to x.i("ei")); vm.toast("Paired with ${x.s("name")}"); onClose() },
                    color = cs.surfaceContainerHigh, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Text(x.s("name"), style = MaterialTheme.typography.titleSmall)
                        if (x.s("reason").isNotBlank()) Text(x.s("reason"), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/* App.js session note: one line about today, optionally shown again next time. */
@Composable
fun SessionNoteSheet(vm: AppViewModel, s: JSONObject, onClose: () -> Unit) {
    // The design's workout note: free text, quick tags, and how long it sticks.
    var text by remember { mutableStateOf(s.s("sessionNote")) }
    var repeat by remember { mutableStateOf(s.s("sessionNoteRepeat", "once").let { if (it == "none" && s.s("sessionNote").isNotBlank()) "none" else if (it == "none") "once" else it }) }
    val cs = MaterialTheme.colorScheme
    val save = { vm.act("sessionNote", "text" to text.trim(), "repeat" to repeat); onClose() }
    ModalBottomSheet(onDismissRequest = save) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Workout note", style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(text, { text = it.take(300) }, Modifier.fillMaxWidth(), minLines = 3, placeholder = { Text("How did it go? Sleep, energy, anything for next time") },
                shape = RoundedCornerShape(16.dp))
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Slept badly", "Felt strong", "Short on time", "Gym was busy", "Left elbow sore").forEach { t ->
                    val on = text.contains(t, ignoreCase = true)
                    androidx.compose.material3.FilterChip(selected = on, onClick = {
                        text = if (on) text.replace(Regex("(,\\s*)?" + Regex.escape(t), RegexOption.IGNORE_CASE), "").trim().trimStart(',').trim()
                        else listOf(text.trim(), t).filter { it.isNotBlank() }.joinToString(", ")
                    }, label = { Text(t) }, shape = CircleShape)
                }
            }
            Segmented(listOf("none" to "This session", "once" to "Next time", "always" to "Every time"), repeat, { repeat = it })
            Text(when (repeat) {
                "none" -> "Saved with this session only — it won't come back next time."
                "always" -> "Shown every time you train ${s.s("dayLabel")} until you clear it."
                else -> "Saved with this session and shown once, the next time you train ${s.s("dayLabel")}."
            }, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            Button(onClick = save, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Done") }
        }
    }
}

/* The workout's overflow: things about the session rather than one lift. */
@Composable
fun SessionMenu(vm: AppViewModel, s: JSONObject, onNote: () -> Unit, onAdd: () -> Unit, onSuperset: () -> Unit, onClose: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    ModalBottomSheet(onDismissRequest = onClose) {
        Column(Modifier.padding(horizontal = 12.dp).padding(bottom = 28.dp)) {
            Text("Workout", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
            Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                MenuRow(PiIcons.Plus, "Add a lift", "Extra work for today", cs.primaryContainer, cs.onPrimaryContainer, onAdd)
                MenuRow(PiIcons.List, "Workout note", s.s("sessionNote").ifBlank { "Anything about today" }, cs.secondaryContainer, cs.onSecondaryContainer, onNote)
                if (s.list("exercises").size > 1) MenuRow(PiIcons.Swap, "Superset", "Pair the current lift with another", cs.secondaryContainer, cs.onSecondaryContainer, onSuperset)
                MenuRow(PiIcons.Timer, "Reset workout timer", "Start the clock from now", cs.secondaryContainer, cs.onSecondaryContainer) { vm.act("resetTimer"); vm.toast("Timer reset"); onClose() }
            }
        }
    }
}

/* The design's add panel (the ⋯ beside + Add set): a standard set, a drop set, myo-reps or the warm-up ramp,
   with the same rules as the direct buttons, and removing the set you added. Prescribed sets are skipped
   from their set options, never removed. */
@Composable
fun AddSetSheet(vm: AppViewModel, ex: JSONObject, onClose: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val ei = ex.i("ei")
    val sets = ex.list("sets")
    val work = sets.filter { !it.b("warm") && !it.b("sub") && !it.b("skipped") }
    val last = work.lastOrNull()
    val u = vm.session.s("unit")
    val lastLabel = last?.let { l -> listOfNotNull(l.s("weight").takeIf { it.isNotBlank() }?.let { "$it $u" }, l.s("reps").takeIf { it.isNotBlank() }).joinToString(" × ") }.orEmpty()
    val rules = ex.o("addRules")
    data class Tile(val label: String, val sub: String, val why: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val tone: Boolean, val run: () -> Unit)
    val tiles = buildList {
        add(Tile("Standard set", if (lastLabel.isNotBlank()) "Another $lastLabel" else "One more working set", "", PiIcons.Plus, false) { vm.act("addSet", "ei" to ei) })
        rules?.o("drop")?.takeIf { it.b("show") }?.let { r -> add(Tile("Drop set", "−25% straight after your last set, to failure", r.s("why"), PiIcons.Redo, true) { vm.act("addOne", "ei" to ei, "kind" to "drop") }) }
        rules?.o("myo")?.takeIf { it.b("show") }?.let { r -> add(Tile("Myo-reps", "Short-rest minis after your last set", r.s("why"), PiIcons.Sparkle, true) { vm.act("addOne", "ei" to ei, "kind" to "myo") }) }
        add(Tile(if (ex.b("hasWarm")) "Remove warm-ups" else "Warm-up ramp", if (ex.b("hasWarm")) "Drop the ramp for this lift today" else "A ramp up to your first working weight", "", PiIcons.Flame, false) { vm.act("warmups", "ei" to ei) })
    }
    val lastAdded = sets.lastOrNull { !it.b("warm") && !it.b("sub") && !it.b("done") && it.b("added") }
    var confirm by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onClose) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Kicker(ex.s("name"))
            Text("Add a set", style = MaterialTheme.typography.headlineSmall)
            tiles.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEach { t ->
                        val bg = if (t.tone) cs.tertiaryContainer else cs.secondaryContainer
                        val fg = if (t.tone) cs.onTertiaryContainer else cs.onSecondaryContainer
                        val off = t.why.isNotBlank()
                        Column(Modifier.weight(1f).heightIn(min = 112.dp).clip(RoundedCornerShape(22.dp)).background(bg).alpha(if (off) 0.5f else 1f)
                            .clickable { if (off) vm.toast(t.why) else { t.run(); onClose() } }
                            .semantics(mergeDescendants = true) { role = Role.Button; if (off) contentDescription = "${t.label}, unavailable: ${t.why}" }.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(t.icon, null, tint = fg, modifier = Modifier.size(22.dp))
                            Text(t.label, style = MaterialTheme.typography.titleSmall, color = fg)
                            Text(if (off) t.why else t.sub, style = MaterialTheme.typography.bodySmall, color = fg.copy(alpha = 0.85f))
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            Text("Drop sets and myo-reps never set the next load. Warm-ups don't count toward volume.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            if (lastAdded != null) {
                if (!confirm) TextButton(onClick = { confirm = true }) { Text("Remove the set you added…", color = cs.tertiary) }
                else Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(cs.surface).padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Remove the last set you added? Logged sets are kept.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    TextButton(onClick = { confirm = false }) { Text("Cancel") }
                    Button(onClick = { vm.act("removeSet", "ei" to ei, "si" to lastAdded.i("i")); vm.toast("Removed the set you added · logged sets stay"); onClose() },
                        colors = ButtonDefaults.buttonColors(containerColor = cs.tertiary, contentColor = cs.surface)) { Text("Remove") }
                }
            }
        }
    }
}
