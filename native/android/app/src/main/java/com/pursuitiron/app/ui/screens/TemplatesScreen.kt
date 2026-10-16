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
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(x.s("name"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    if (x.b("percent")) Badge("% of max")
                }
                Text(x.s("tag"), style = MaterialTheme.typography.labelMedium, color = cs.primary)
                Text(x.s("desc"), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
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

/* App.js tmSheet: one field per main lift, typed as a training max or as a 1RM (90% of it is used). */
@Composable
fun TrainingMaxSheet(vm: AppViewModel) {
    val pid = vm.tmFor ?: return
    var data by remember(pid) { mutableStateOf<JSONObject?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(pid) { data = vm.trainingMaxes(pid) }
    val cs = MaterialTheme.colorScheme
    ModalBottomSheet(onDismissRequest = { vm.tmFor = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Training maxes", style = MaterialTheme.typography.headlineSmall)
            val d = data ?: run { Text("Loading…"); return@Column }
            val u = d.s("unit", "kg")
            var mode by remember { mutableStateOf("tm") }
            Segmented(listOf("tm" to "Training max", "orm" to "From a 1RM"), mode, { mode = it })
            Text(if (mode == "tm") "Usually about 90% of your true one-rep max." else "Enter a recent one-rep max; 90% of it, rounded to the lift's step, becomes the training max.",
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            d.list("lifts").forEach { l ->
                var v by remember(l.s("id"), mode) { mutableStateOf(if (mode == "tm" && l.d("tm") > 0) fmtNum(l.d("tm")) else "") }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(v, { v = it.filter { c -> c.isDigit() || c == '.' }.take(6) }, Modifier.weight(1f), singleLine = true,
                            label = { Text("${l.s("name")} (${if (mode == "tm") "TM" else "1RM"}, $u)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                        Button(onClick = { scope.launch { vm.setTrainingMax(pid, l.s("id"), v.toDoubleOrNull(), mode == "orm"); data = vm.trainingMaxes(pid) } }, modifier = Modifier.height(56.dp)) { Text("Set") }
                    }
                    if (l.d("tm") > 0) Text("TM ${fmtNum(l.d("tm"))} $u" + (l.o("week1")?.let { w -> " · week 1 (${w.s("label")}): ${w.strings("sets").joinToString("  ")}" } ?: ""),
                        style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                }
            }
            Button(onClick = { vm.tmFor = null }, modifier = Modifier.fillMaxWidth()) { Text("Done") }
        }
    }
}
