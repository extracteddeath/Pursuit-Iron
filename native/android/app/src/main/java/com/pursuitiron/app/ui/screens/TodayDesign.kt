@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.pursuitiron.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pursuitiron.app.AppViewModel
import com.pursuitiron.app.ui.common.PiIcons
import com.pursuitiron.app.ui.common.b
import com.pursuitiron.app.ui.common.i
import com.pursuitiron.app.ui.common.list
import com.pursuitiron.app.ui.common.o
import com.pursuitiron.app.ui.common.s
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/* The design's Today pieces: the avatar, the plan-changes banner and sheet (web Build 904–910), the check-in,
   and the game plan built from the next session's review. */

private val CK_DEFS = listOf(
    Triple("sleep", "Sleep", listOf("poor" to "Poor", "ok" to "OK", "great" to "Great")),
    Triple("sore", "Soreness", listOf("very" to "Very", "some" to "Some", "none" to "None")),
    Triple("energy", "Energy", listOf("low" to "Low", "ok" to "OK", "high" to "High")),
)
private val CKV = mapOf(
    "sleep" to mapOf("poor" to -8, "ok" to 0, "great" to 3),
    "sore" to mapOf("very" to -10, "some" to -4, "none" to 2),
    "energy" to mapOf("low" to -6, "ok" to 0, "high" to 3),
)
private fun todayKey() = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

/** Today's check-in answers (empty when none today) and whether it was put away. */
data class CheckIn(val answers: Map<String, String>, val done: Boolean) {
    val mod: Int get() = answers.entries.sumOf { (k, v) -> CKV[k]?.get(v) ?: 0 }
}
fun checkInOf(vm: AppViewModel): CheckIn {
    val p = vm.prefs.checkIn.split('|')
    if (p.size < 5 || p[0] != todayKey()) return CheckIn(emptyMap(), false)
    return CheckIn(listOf("sleep", "sore", "energy").zip(p.subList(1, 4)).filter { it.second.isNotBlank() }.toMap(), p[4] == "1")
}
private fun saveCheckIn(vm: AppViewModel, c: CheckIn) =
    vm.prefs.changeCheckIn(listOf(todayKey(), c.answers["sleep"].orEmpty(), c.answers["sore"].orEmpty(), c.answers["energy"].orEmpty(), if (c.done) "1" else "0").joinToString("|"))

/** Readiness for today: muscle freshness for the session, moved by the check-in. */
fun readinessToday(vm: AppViewModel, h: JSONObject): Int = (h.o("readiness").i("today", 100) + checkInOf(vm).mod).coerceIn(0, 100)

/** What readiness does to the workout's loads, applied when the workout opens (the design has no sheet there). */
fun readinessPick(ready: Int): Pair<String, Double> = when { ready >= 80 -> "Feeling strong" to 1.0; ready >= 70 -> "A little off" to 0.95; else -> "Run down" to 0.88 }

@Composable
fun TodayHeader(vm: AppViewModel) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(top = 20.dp, start = 4.dp), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(Date()), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            Text("Today", style = MaterialTheme.typography.displayMedium)
        }
        val name = vm.profile?.s("name").orEmpty()
        Box(Modifier.padding(top = 8.dp).size(48.dp).clip(RoundedCornerShape(16.dp)).background(cs.tertiaryContainer).clickable { vm.goTab(com.pursuitiron.app.Tab.You) }
            .semantics { contentDescription = "You${if (name.isNotBlank()) ": $name" else ""}"; role = Role.Button }, contentAlignment = Alignment.Center) {
            if (name.isNotBlank()) Text(name.trim().first().uppercase(), style = MaterialTheme.typography.titleLarge, color = cs.onTertiaryContainer)
            else Icon(PiIcons.You, null, tint = cs.onTertiaryContainer)
        }
    }
}

/** The design's "N changes to your next session" banner. */
@Composable
fun PlanChangesBanner(r: JSONObject, onOpen: () -> Unit) {
    val n = r.list("changes").size; if (n == 0) return
    val cs = MaterialTheme.colorScheme
    val undone = r.i("undone")
    val src = r.optLong("sourceDate").takeIf { it > 0 }?.let { SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(Date(it)) }
    Surface(onClick = onOpen, color = cs.primaryContainer, contentColor = cs.onPrimaryContainer, shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Review plan changes: $n change${if (n == 1) "" else "s"} to your next session" }) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(cs.surface), contentAlignment = Alignment.Center) { Icon(PiIcons.Redo, null, tint = cs.primary, modifier = Modifier.size(20.dp)) }
            Column(Modifier.weight(1f)) {
                Text("$n change${if (n == 1) "" else "s"} to your next session", style = MaterialTheme.typography.titleSmall)
                Text(if (undone > 0) "$undone undone · review choices" else "${r.s("dayLabel")}" + (src?.let { " · based on $it" } ?: "") + ". Review before you start.",
                    style = MaterialTheme.typography.bodySmall)
            }
            Icon(PiIcons.ChevronRight, null)
        }
    }
}

/** Web PlanChangesDialog: each change as before → after, why, and Undo (or Use recommendation). */
@Composable
fun PlanChangesSheet(vm: AppViewModel, review: JSONObject, onUpdated: (JSONObject) -> Unit, onClose: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    var r by remember { mutableStateOf(review) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Plan changes", style = MaterialTheme.typography.headlineSmall)
            Text("${r.s("dayLabel")} · Week ${r.i("weekIndex")}. Choices apply to this workout's load and reps. Sets, target effort and technique follow the plan.",
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            if (r.list("changes").isEmpty()) Text("No changes from your last comparable workout.", color = cs.onSurfaceVariant)
            r.list("changes").forEach { c ->
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(cs.surfaceContainer).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(c.s("name"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        Text(if (c.b("undone")) "Undone" else c.s("category"), style = MaterialTheme.typography.labelMedium,
                            color = if (c.b("undone")) cs.onTertiaryContainer else cs.onSecondaryContainer,
                            modifier = Modifier.clip(CircleShape).background(if (c.b("undone")) cs.tertiaryContainer else cs.secondaryContainer).padding(horizontal = 10.dp, vertical = 4.dp))
                    }
                    c.list("rows").forEach { row ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (!row.isNull("set")) Text("Set ${row.i("set")}", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant, modifier = Modifier.width(44.dp))
                            Text(row.s("before"), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                            Icon(PiIcons.ChevronRight, "to", modifier = Modifier.size(16.dp))
                            Text(row.s("after"), style = MaterialTheme.typography.titleSmall)
                        }
                    }
                    Text(c.o("basis")?.let { "Compared with this exercise on ${SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(it.optLong("date")))} for the same program day." } ?: "No comparable completed workout yet.",
                        style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    Text(c.s("reason"), style = MaterialTheme.typography.bodyMedium)
                    if (c.b("canUndo")) OutlinedButton(enabled = !busy, onClick = {
                        busy = true
                        scope.launch {
                            val res = vm.planChoice(r.s("programId"), r.s("dayId"), r.i("weekIndex"), c.s("id"), c.i("slot"), !c.b("undone"))
                            busy = false
                            if (res?.b("ok") == true) res.o("review")?.let { r = it; onUpdated(it) } else vm.toast(res?.s("error") ?: "Could not save this choice. Try again.")
                        }
                    }, modifier = Modifier.semantics { contentDescription = (if (c.b("undone")) "Restore recommendation for " else "Undo change for ") + c.s("name") }) {
                        Text(if (c.b("undone")) "Use recommendation" else "Undo change")
                    } else Text("Edit this exercise in your program to change its targets.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
            }
            Button(onClick = onClose, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Done") }
        }
    }
}

/** The design's check-in on Today: optional, three quick answers, then a chip that reopens it. */
@Composable
fun CheckInCard(vm: AppViewModel) {
    val cs = MaterialTheme.colorScheme
    val c = checkInOf(vm)
    val effect = if (c.answers.isEmpty()) "Optional" else (if (c.mod > 0) "+" else if (c.mod < 0) "−" else "±") + "${kotlin.math.abs(c.mod)}% readiness"
    if (c.done) {
        Surface(onClick = { saveCheckIn(vm, c.copy(done = false)) }, color = cs.surfaceContainerLow, shape = CircleShape,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Edit today's check-in" }) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(PiIcons.Check, null, tint = cs.primary, modifier = Modifier.size(18.dp))
                Text(if (c.answers.isEmpty()) "Check-in skipped — plan runs as written"
                    else CK_DEFS.filter { c.answers[it.first] != null }.joinToString(" · ") { d -> "${d.second} ${d.third.first { it.first == c.answers[d.first] }.second.lowercase()}" },
                    style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(effect, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
            }
        }
        return
    }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(cs.surfaceContainerLow).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("How are you today?", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(effect, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
        }
        CK_DEFS.forEach { (key, label, opts) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(84.dp))
                Row(Modifier.weight(1f).clip(CircleShape).background(cs.surfaceContainerHigh).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    opts.forEach { (k, l) ->
                        val on = c.answers[key] == k
                        Box(Modifier.weight(1f).height(36.dp).clip(CircleShape).background(if (on) cs.secondaryContainer else Color.Transparent)
                            .clickable { saveCheckIn(vm, c.copy(answers = if (on) c.answers - key else c.answers + (key to k))) }
                            .semantics { contentDescription = "$label: $l"; role = Role.Button; stateDescriptionOf(on) }, contentAlignment = Alignment.Center) {
                            Text(l, style = MaterialTheme.typography.labelLarge, color = if (on) cs.onSecondaryContainer else cs.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Adjusts readiness before any sets are trimmed. Skip it and the plan runs as written.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, modifier = Modifier.weight(1f))
            Button(onClick = { saveCheckIn(vm, c.copy(done = true)) }) { Text(when (c.answers.size) { 3 -> "Done"; 0 -> "Skip"; else -> "Save" }) }
        }
    }
}

private fun androidx.compose.ui.semantics.SemanticsPropertyReceiver.stateDescriptionOf(on: Boolean) {
    this.stateDescription = if (on) "Selected" else "Not selected"
}

/** Web SessionGamePlan: up to three highlights from the next session's review — a PR chance first. */
@Composable
fun GamePlanCard(review: JSONObject, changes: Int) {
    val notes = review.list("gamePlan"); if (notes.isEmpty()) return
    val cs = MaterialTheme.colorScheme
    var open by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(cs.surfaceContainerLow).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(PiIcons.Progress, null, tint = cs.primary, modifier = Modifier.size(20.dp))
            Text("Today's game plan", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (changes > 0) Text("$changes change${if (changes == 1) "" else "s"} from last time", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
        }
        notes.forEach { g ->
            val key = g.s("id") + g.i("slot"); val on = open == key
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { open = if (on) null else key }, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(if (g.s("kind") == "pr") cs.primary else cs.tertiaryContainer), contentAlignment = Alignment.Center) {
                    Icon(when (g.s("kind")) { "pr" -> PiIcons.Trophy; "technique" -> PiIcons.Sparkle; else -> PiIcons.Progress }, null,
                        tint = if (g.s("kind") == "pr") cs.onPrimary else cs.onTertiaryContainer, modifier = Modifier.size(16.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(g.s("title"), style = MaterialTheme.typography.titleSmall)
                    Text(g.s("guidance"), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = if (on) 8 else 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
