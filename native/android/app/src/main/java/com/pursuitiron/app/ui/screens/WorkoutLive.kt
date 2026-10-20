@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.pursuitiron.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pursuitiron.app.AppViewModel
import com.pursuitiron.app.ui.common.Kicker
import com.pursuitiron.app.ui.common.PiIcons
import com.pursuitiron.app.ui.common.Segmented
import com.pursuitiron.app.ui.common.b
import com.pursuitiron.app.ui.common.fmtNum
import com.pursuitiron.app.ui.common.i
import com.pursuitiron.app.ui.common.list
import com.pursuitiron.app.ui.common.o
import com.pursuitiron.app.ui.common.s
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

/* The design's live-set pieces (prototype extras23–26): the plate bar, the direct add row, hold-to-repeat
   steppers and the set-number options sheet. The rules and numbers come from the core. */

/** A set's label for sheets and toasts: "Set 2", "Warm-up 1", "Drop set", "Myo mini". */
fun setLabelOf(ex: JSONObject, si: Int): String {
    val sets = ex.list("sets"); val x = sets.getOrNull(si) ?: return "Set"
    val upTo = sets.take(si + 1)
    return when {
        x.b("warm") -> "Warm-up ${upTo.count { it.b("warm") }}"
        x.b("sub") -> if (x.s("kind") == "drop") "Drop set" else "Myo mini"
        else -> "Set ${upTo.count { !it.b("warm") && !it.b("sub") }}"
    }
}

/** True when this set shows the lift's BW / Band tag instead of a load. */
fun showsTag(ex: JSONObject, set: JSONObject): Boolean =
    ex.s("loadTag").isNotBlank() && !ex.b("assisted") && (set.s("weight").toDoubleOrNull() ?: 0.0) <= 0.0

/** The design's plate bar under the sets: "20 barbell + 20 · 20 · 5 /side", tap for the calculator. */
@Composable
fun PlateBar(ex: JSONObject, unit: String, onOpen: () -> Unit) {
    val p = ex.o("plates") ?: return
    if (ex.s("loadTag") == "Band") return
    val ref = ex.list("sets").getOrNull(ex.i("platesFor")) ?: return
    val total = ref.s("weight").toDoubleOrNull() ?: return
    if (total <= 0) return
    val cs = MaterialTheme.colorScheme
    Surface(onClick = onOpen, color = cs.surfaceContainerHigh, contentColor = cs.onSurface, shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics { contentDescription = "Open the plate calculator for ${fmtNum(total)} $unit" }) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(PiIcons.Plates, null, tint = cs.primary, modifier = Modifier.size(20.dp))
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Text(fmtNum(p.optDouble("barW")), style = MaterialTheme.typography.titleSmall)
                Text(" ${p.s("barLabel")} + ", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                Text(p.s("bandText"), style = MaterialTheme.typography.titleSmall, color = cs.primary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Text(" /side", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            }
            Icon(PiIcons.ChevronRight, null, tint = cs.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
    }
}

/** The design's add row: + Add set, + Drop, + Myo, and ⋯ for warm-ups and removing a set you added. */
@Composable
fun AddRow(vm: AppViewModel, ex: JSONObject, modifier: Modifier = Modifier, onMore: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val ei = ex.i("ei")
    val rules = ex.o("addRules")
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Surface(onClick = { vm.act("addSet", "ei" to ei) }, color = cs.secondaryContainer, contentColor = cs.onSecondaryContainer, shape = RoundedCornerShape(16.dp),
            modifier = Modifier.weight(1.4f).height(48.dp).semantics { contentDescription = "Add set" }) {
            Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Icon(PiIcons.Plus, null, modifier = Modifier.size(18.dp)); Text(" Add set", style = MaterialTheme.typography.titleSmall, maxLines = 1)
            }
        }
        listOf("drop" to "Drop", "myo" to "Myo").forEach { (k, label) ->
            val r = rules?.o(k)
            if (r != null && r.b("show")) {
                val why = r.s("why")
                Surface(onClick = { if (why.isNotBlank()) vm.toast(why) else vm.act("addOne", "ei" to ei, "kind" to k) },
                    color = Color.Transparent, contentColor = cs.primary, shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, cs.outline),
                    modifier = Modifier.height(48.dp).alpha(if (why.isNotBlank()) 0.55f else 1f)
                        .semantics { contentDescription = if (why.isNotBlank()) "$label, unavailable: $why" else "Add a ${if (k == "drop") "drop set" else "myo-rep set"}" }) {
                    Row(Modifier.padding(horizontal = 14.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Icon(PiIcons.Plus, null, modifier = Modifier.size(18.dp)); Text(" $label", style = MaterialTheme.typography.titleSmall, maxLines = 1)
                    }
                }
            }
        }
        Surface(onClick = onMore, color = Color.Transparent, contentColor = cs.onSurfaceVariant, shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, cs.outlineVariant),
            modifier = Modifier.size(48.dp).semantics { contentDescription = "More set options — warm-ups, remove a set" }) {
            Box(contentAlignment = Alignment.Center) { Icon(PiIcons.More, null) }
        }
    }
}

/** A −/+ button that runs on while held (the design's hold-to-repeat). */
@Composable
fun HoldStep(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, size: Dp, filled: Boolean, onStep: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val step by rememberUpdatedState(onStep)
    val scope = rememberCoroutineScope()
    Box(Modifier.size(size).clip(RoundedCornerShape(12.dp)).background(if (filled) cs.primary else cs.surfaceContainerHighest)
        .semantics { contentDescription = label; role = Role.Button }
        .pointerInput(Unit) {
            detectTapGestures(onPress = {
                step()
                val job = scope.launch { delay(420); while (true) { step(); delay(90) } }
                tryAwaitRelease(); job.cancel()
            })
        }, contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = if (filled) cs.onPrimary else cs.onSurface, modifier = Modifier.size(18.dp))
    }
}

/** The design's optional steppers: the second tier under an open set, − weight + and − reps +. */
@Composable
fun SetSteppers(vm: AppViewModel, s: JSONObject, ex: JSONObject, ei: Int, si: Int, set: JSONObject) {
    val cs = MaterialTheme.colorScheme
    val u = s.s("unit"); val assisted = ex.b("assisted")
    val h = if (vm.prefs.grip) 48.dp else 40.dp
    Row(Modifier.fillMaxWidth().padding(start = 40.dp, top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(cs.surfaceContainerHigh).padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            HoldStep(PiIcons.Minus, if (assisted) "More assistance" else "Less weight", h, false) { vm.act("bumpWeight", "ei" to ei, "si" to si, "dir" to -1) }
            val w = set.s("weight")
            Text(when { showsTag(ex, set) -> ex.s("loadTag"); assisted -> assistShownLive(w); else -> w.ifBlank { "—" } } + if (!showsTag(ex, set) && w.isNotBlank()) " $u" else "",
                style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 1)
            HoldStep(PiIcons.Plus, if (assisted) "Less assistance" else "More weight", h, false) { vm.act("bumpWeight", "ei" to ei, "si" to si, "dir" to 1) }
        }
        Row(Modifier.weight(0.8f).clip(RoundedCornerShape(14.dp)).background(cs.surfaceContainerHigh).padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            HoldStep(PiIcons.Minus, "One rep fewer", h, false) { vm.act("bumpReps", "ei" to ei, "si" to si, "delta" to -1) }
            Text(set.s("reps").ifBlank { "—" } + if (set.s("reps").isNotBlank()) " reps" else "", style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 1)
            HoldStep(PiIcons.Plus, "One rep more", h, false) { vm.act("bumpReps", "ei" to ei, "si" to si, "delta" to 1) }
        }
    }
}

private fun assistShownLive(v: String): String = v.trim().removePrefix("-").removePrefix("−").let { a -> if (a.isBlank()) "—" else if (a.toDoubleOrNull() == 0.0) "0" else "−$a" }

/** The design's set options (tap the set number): type, mark not done, skip / restore, remove a set you added. */
@Composable
fun SetOptionsSheet(vm: AppViewModel, s: JSONObject, ei: Int, si: Int, onClose: () -> Unit) {
    val ex = s.list("exercises").getOrNull(ei); val set = ex?.list("sets")?.getOrNull(si)
    if (ex == null || set == null) { onClose(); return }
    val cs = MaterialTheme.colorScheme
    val u = s.s("unit")
    val label = setLabelOf(ex, si)
    val done = set.b("done"); val skipped = set.b("skipped"); val warm = set.b("warm"); val sub = set.b("sub")
    var confirm by remember(ei, si) { mutableStateOf(false) }
    val say = { msg: String -> vm.toast(msg); onClose() }
    ModalBottomSheet(onDismissRequest = onClose) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column {
                Text("$label · ${ex.s("name")}", style = MaterialTheme.typography.headlineSmall)
                val w = when { showsTag(ex, set) -> ex.s("loadTag"); else -> set.s("weight").ifBlank { "—" } + " $u" }
                Text("$w × ${set.s("reps").ifBlank { "—" }}" + when { done -> " · logged"; skipped -> " · skipped"; else -> "" },
                    style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            }
            if (!done && !sub && !skipped) {
                Kicker("Set type")
                Segmented(listOf("standard" to "Working", "warmup" to "Warm-up", "failure" to "To failure"),
                    if (warm) "warmup" else set.s("type", "standard"), { k ->
                        val cur = if (warm) "warmup" else set.s("type", "standard")
                        if (k != cur) { vm.act("setType", "ei" to ei, "si" to si, "kind" to k)
                            say("$label is now ${mapOf("standard" to "working", "warmup" to "warm-up", "failure" to "to failure")[k]} · weight and reps kept") }
                    }, height = 44.dp)
                Text("Changing the type keeps its weight and reps. Warm-ups don't count toward volume; a to-failure set is logged at 0 RIR.",
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
            val removable = !done && (set.b("added") || (sub && !set.b("prescribed")))
            Column(Modifier.clip(RoundedCornerShape(20.dp)), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                @Composable fun Item(text: String, tone: Color = cs.onSurface, onClick: () -> Unit) {
                    Box(Modifier.fillMaxWidth().heightIn(min = 56.dp).background(cs.surfaceContainerHigh).clickable(onClick = onClick)
                        .semantics { role = Role.Button }.padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
                        Text(text, style = MaterialTheme.typography.titleSmall, color = tone)
                    }
                }
                if (done) Item("Mark not done") { vm.act("toggle", "ei" to ei, "si" to si); say("$label marked not done") }
                if (!done && !skipped && !sub) Item("Skip this set") { vm.act("skipSet", "ei" to ei, "si" to si); say("$label skipped — not counted") }
                if (skipped) Item("Restore this set") { vm.act("skipSet", "ei" to ei, "si" to si, "skip" to false); say("$label restored") }
                if (removable && !confirm) Item("Remove this set…", cs.tertiary) { confirm = true }
                if (removable && confirm) Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).background(cs.tertiaryContainer).padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Remove it from today?", style = MaterialTheme.typography.titleSmall, color = cs.onTertiaryContainer, modifier = Modifier.weight(1f))
                    TextButton(onClick = { confirm = false }) { Text("Keep") }
                    androidx.compose.material3.Button(onClick = { vm.act("removeSet", "ei" to ei, "si" to si); say("$label removed") },
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = cs.tertiary, contentColor = cs.onPrimary)) { Text("Remove") }
                }
            }
            if (!removable && !done && !warm && !sub && !set.b("added")) Text("Prescribed sets can be skipped but not removed — the program counts on them. Sets you add can be removed.",
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            if (sub && !done) Text("${if (set.s("kind") == "drop") "Drop" else "Myo"} sets keep their structure; they never set the next load.",
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("Done", fontWeight = FontWeight.Bold) }
        }
    }
}
