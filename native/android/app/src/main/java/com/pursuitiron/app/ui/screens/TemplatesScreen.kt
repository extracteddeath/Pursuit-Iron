@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.pursuitiron.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.semantics.selected
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material3.IconButton
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pursuitiron.app.AppViewModel
import com.pursuitiron.app.ui.common.Badge
import com.pursuitiron.app.ui.common.Kicker
import com.pursuitiron.app.ui.common.MorphChip
import com.pursuitiron.app.ui.common.PiCard
import com.pursuitiron.app.ui.common.Segmented
import com.pursuitiron.app.ui.common.b
import com.pursuitiron.app.ui.common.d
import com.pursuitiron.app.ui.common.fmtNum
import com.pursuitiron.app.ui.common.i
import com.pursuitiron.app.ui.common.list
import com.pursuitiron.app.ui.common.o
import com.pursuitiron.app.ui.common.s
import com.pursuitiron.app.ui.common.strings
import kotlinx.coroutines.launch
import org.json.JSONObject

/** App.js template browser: featured first, then by category; building one uses the active gym. */
@Composable
fun TemplatePicker(vm: AppViewModel) {
    val t = vm.templates ?: return
    val cs = MaterialTheme.colorScheme
    var cat by remember { mutableStateOf<String?>(null) }
    var q by remember { mutableStateOf("") }
    val items = t.list("items").filter { (cat == null || it.s("cat") == cat) && (q.isBlank() || it.s("name").contains(q, true) || it.s("desc").contains(q, true)) }
        .sortedWith(compareBy({ if (cat == null && q.isBlank()) (if (it.b("featured")) 0 else 1) else 0 }, { it.optInt("rank", 50) }))
    OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Search ${t.list("items").size} templates") })
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        item { MorphChip("All", cat == null, { cat = null }, height = 36.dp) }
        items(t.strings("categories")) { c -> MorphChip(c, cat == c, { cat = if (cat == c) null else c }, height = 36.dp) }
    }
    items.forEach { x ->
        Surface(onClick = { vm.createFromTemplate(x.s("id")) }, color = cs.surfaceContainerHigh, shape = RoundedCornerShape(22.dp),
            modifier = Modifier.fillMaxWidth().semantics { role = Role.Button }) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                // The design's template row: name with its category at the right, the blurb, then days · time · length
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(x.s("name"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    if (x.b("percent")) Badge("% of max")
                    Text(x.s("cat"), style = MaterialTheme.typography.labelSmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold), color = cs.onSurfaceVariant)
                }
                Text(x.s("desc"), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                if (x.s("meta").isNotBlank()) Text(x.s("meta"), style = MaterialTheme.typography.labelMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold))
            }
        }
    }
    if (items.isEmpty()) Text("No templates match.", color = cs.onSurfaceVariant)
}

/** Today's nudge when a percentage program has lifts without a training max. */
@Composable
fun TrainingMaxPrompt(vm: AppViewModel) {
    val h = vm.home ?: return
    val n = h.optInt("needsTrainingMax", 0)
    val pid = h.o("next")?.s("programId") ?: return
    if (n <= 0) return
    val cs = MaterialTheme.colorScheme
    PiCard(color = cs.tertiaryContainer, radius = 28.dp, onClick = { vm.tmFor = pid }) {
        Kicker("Before you start", cs.onTertiaryContainer)
        Text("Set training maxes for $n lift${if (n == 1) "" else "s"}", style = MaterialTheme.typography.titleMedium, color = cs.onTertiaryContainer)
        Text("This program works in percentages of a training max. Type one, or enter a recent 1RM and the app uses 90% of it.",
            style = MaterialTheme.typography.bodySmall, color = cs.onTertiaryContainer)
    }
}

/* App.js tmSheet as the design's percentage program panel: the method, a chip per week of the block
   (deload included), and each main lift's training max with that week's sets. Maxes are typed (or
   entered as a 1RM, 90% of it is used) and nudged a step at a time. */
@Composable
fun TrainingMaxSheet(vm: AppViewModel) {
    val pid = vm.tmFor ?: return
    var week by remember(pid) { mutableStateOf<Int?>(null) }
    var data by remember(pid) { mutableStateOf<JSONObject?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(pid, week) { data = vm.trainingMaxes(pid, week) }
    var methodsOpen by remember(pid) { mutableStateOf(false) }
    var methods by remember(pid) { mutableStateOf<JSONObject?>(null) }
    var pickMethod by remember(pid) { mutableStateOf<JSONObject?>(null) }
    LaunchedEffect(pid, methodsOpen) { if (methodsOpen && methods == null) methods = vm.percentMethods(pid) }
    val cs = MaterialTheme.colorScheme
    ModalBottomSheet(onDismissRequest = { vm.tmFor = null }, sheetState = com.pursuitiron.app.ui.common.rememberSettlingSheet(data)) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Percentage program", style = MaterialTheme.typography.headlineSmall)
            val d = data ?: run { Text("Loading…"); return@Column }
            val u = d.s("unit", "kg")
            d.o("method")?.let { m ->
                Surface(onClick = { methodsOpen = !methodsOpen }, color = cs.surfaceContainerHigh, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Kicker("Method")
                            Text(m.s("name"), style = MaterialTheme.typography.titleMedium)
                            Text("${d.s("name")} works from your ${m.s("basis").substringBefore(" (").lowercase()}.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                        }
                        Icon(if (methodsOpen) com.pursuitiron.app.ui.common.PiIcons.ChevronUp else com.pursuitiron.app.ui.common.PiIcons.ChevronDown, if (methodsOpen) "Hide methods" else "Change method")
                    }
                }
                if (methodsOpen) {
                    val ms = methods
                    if (ms == null) Text("Loading…", color = cs.onSurfaceVariant)
                    else Column(Modifier.clip(RoundedCornerShape(20.dp)), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        ms.list("methods").forEach { x ->
                            val on = x.b("current")
                            Row(
                                Modifier.fillMaxWidth().background(if (on) cs.secondaryContainer else cs.surfaceContainerLow)
                                    .clickable(enabled = !on) { pickMethod = x }.padding(horizontal = 14.dp, vertical = 12.dp)
                                    .semantics(mergeDescendants = true) { role = Role.RadioButton; selected = on },
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                androidx.compose.material3.RadioButton(selected = on, onClick = null)
                                Column(Modifier.weight(1f)) {
                                    Text(x.s("name"), style = MaterialTheme.typography.titleSmall)
                                    Text(x.s("method") + (x.s("tag").takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""), style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
            pickMethod?.let { x ->
                androidx.compose.material3.AlertDialog(onDismissRequest = { pickMethod = null },
                    title = { Text("Switch to ${x.s("name")}?") },
                    text = { Text("The program is rebuilt on this method with your equipment. Your history stays, and the plan starts again at day 1. Training maxes carry over when the method works from the same kind of max; otherwise you'll set them again.") },
                    confirmButton = { Button(onClick = { val t = x.s("templateId"); pickMethod = null; scope.launch { if (vm.changePercentMethod(pid, t)) { methodsOpen = false; methods = vm.percentMethods(pid); week = null; data = vm.trainingMaxes(pid, null) } } }) { Text("Switch") } },
                    dismissButton = { androidx.compose.material3.TextButton(onClick = { pickMethod = null }) { Text("Cancel") } })
            }
            val weeks = d.list("weeks")
            if (weeks.size > 1) LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.semantics { contentDescription = "Week" }) {
                items(weeks) { w ->
                    val n = w.i("w")
                    MorphChip(
                        if (n == d.i("deloadWeek")) "DL" else "W$n", selected = n == d.i("weekIndex"), onClick = { week = n },
                        trailing = when { w.b("now") -> "now"; w.b("deload") && n != d.i("deloadWeek") -> "deload"; else -> null },
                    )
                }
            }
            var mode by remember { mutableStateOf("tm") }
            Segmented(listOf("tm" to "Training max", "orm" to "From a 1RM"), mode, { mode = it })
            Text(if (mode == "tm") "Usually about 90% of your true one-rep max." else "Enter a recent one-rep max; 90% of it, rounded to the lift's step, becomes the training max.",
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            d.list("lifts").forEach { l ->
                val tm = l.d("tm"); val step = l.d("step").takeIf { it > 0 } ?: if (u == "lb") 5.0 else 2.5
                var v by remember(l.s("id"), mode, tm) { mutableStateOf(if (mode == "tm" && tm > 0) fmtNum(tm) else "") }
                val set = { value: Double?, orm: Boolean -> scope.launch { vm.setTrainingMax(pid, l.s("id"), value, orm); data = vm.trainingMaxes(pid, week) }; Unit }
                Surface(color = cs.surfaceContainerLow, shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(l.s("name"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            if (tm > 0 && mode == "tm") {
                                IconButton(onClick = { set((tm - step).coerceAtLeast(step), false) }) { Text("−", style = MaterialTheme.typography.titleLarge) }
                                Text("${fmtNum(tm)} $u", style = MaterialTheme.typography.titleMedium)
                                IconButton(onClick = { set(tm + step, false) }) { Text("+", style = MaterialTheme.typography.titleLarge) }
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(v, { v = it.filter { c -> c.isDigit() || c == '.' }.take(6) }, Modifier.weight(1f), singleLine = true,
                                label = { Text("${if (mode == "tm") "Training max" else "1RM"} ($u)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                            Button(onClick = { set(v.toDoubleOrNull(), mode == "orm") }, modifier = Modifier.height(56.dp)) { Text("Set") }
                        }
                        val wk = l.o("week")
                        if (wk != null) {
                            Text(wk.s("label"), style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                            Text(wk.list("sets").joinToString("  ·  ") { x -> x.s("text") + (x.optDouble("pct").takeIf { !it.isNaN() }?.let { " @${fmtNum(it)}%" } ?: "") },
                                style = MaterialTheme.typography.bodyMedium)
                        } else if (tm <= 0) Text("Set a training max to see this week's sets.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    }
                }
            }
            Button(onClick = { vm.tmFor = null }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Done") }
        }
    }
}
