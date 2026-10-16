@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.pursuitiron.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pursuitiron.app.AppViewModel
import com.pursuitiron.app.ui.common.Kicker
import com.pursuitiron.app.ui.common.MorphChip
import com.pursuitiron.app.ui.common.PiIcons
import com.pursuitiron.app.ui.common.Segmented
import com.pursuitiron.app.ui.common.list
import com.pursuitiron.app.ui.common.s
import org.json.JSONArray
import org.json.JSONObject

private val PRESETS = listOf(
    "Full gym" to listOf("barbell", "dumbbell", "bench", "cable", "machine", "pullup", "dip", "legpress", "legext", "legcurl", "ezbar", "smith", "calfmachine"),
    "Barbell garage" to listOf("barbell", "dumbbell", "bench", "pullup"),
    "Dumbbells at home" to listOf("dumbbell", "bench", "bands"),
    "Bodyweight" to listOf("pullup", "bands"),
)

@Composable
fun SetupScreen(vm: AppViewModel) {
    val opts = vm.setup
    val cs = MaterialTheme.colorScheme
    var goal by remember { mutableStateOf("both") }
    var exp by remember { mutableStateOf("intermediate") }
    var days by remember { mutableStateOf(4) }
    var session by remember { mutableStateOf("s60") }
    var equip by remember { mutableStateOf(PRESETS[0].second.toSet()) }
    var split by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf("") }
    var pick by remember { mutableStateOf<String?>(null) }
    var mode by remember { mutableStateOf(if (vm.setupCycle) "cycle" else "program") }
    val cycleMode = mode == "cycle"
    var template by remember { mutableStateOf(vm.cycles.list("templates").firstOrNull()?.s("id") ?: "powerbuilding") }
    val custom = remember { mutableStateListOf(CustomBlock("hypertrophy_accumulation", 4), CustomBlock("strength_accumulation", 4)) }
    var adapt by remember { mutableStateOf(false) }

    fun config(withSplit: Boolean): JSONObject = JSONObject()
        .put("goal", goal).put("experience", exp).put("days", days).put("session", session)
        .put("equipment", JSONArray(equip.toList()))
        .apply { if (withSplit && split != null) put("split", split) }
        .apply { if (name.isNotBlank()) put("name", name.trim()) }

    LaunchedEffect(goal, exp, days, session, equip) { pick = vm.recommend(config(false)) }

    val splits = opts.list("splits").filter { sp -> sp.optJSONArray("days")?.let { a -> (0 until a.length()).any { a.optInt(it) == days } } ?: true }
    val chosen = split?.takeIf { s -> splits.any { it.s("id") == s } } ?: pick ?: splits.firstOrNull()?.s("id")

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.padding(start = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.closeSetup() }) { Icon(PiIcons.Close, "Close") }
            Text(when (mode) { "cycle" -> "New training cycle"; "template" -> "Start from a template"; else -> "New program" }, style = MaterialTheme.typography.titleLarge)
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).widthIn(max = 760.dp).align(Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Segmented(listOf("program" to "Build", "template" to "Template", "cycle" to "Cycle"), mode, { mode = it })
            if (mode == "template") { TemplatePicker(vm); return@Column }
            if (cycleMode) Text("A cycle runs several blocks in a row, each with its own job, and moves you to the next when you finish one.",
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            Kicker("Goal")
            Segmented(listOf("strength" to "Strength", "hypertrophy" to "Size", "both" to "Both"), goal, { goal = it })
            Kicker("Experience")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("none" to "Brand new", "beginner" to "Under a year", "intermediate" to "1–3 years", "advanced" to "3+ years").forEach { (id, l) ->
                    MorphChip(l, exp == id, { exp = id }, height = 44.dp)
                }
            }
            Kicker("Days a week")
            Segmented((2..6).map { "$it" to "$it" }, "$days", { days = it.toInt(); split = null })
            Kicker("Time per session")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("s20" to "20 min", "s40" to "40 min", "s60" to "60 min", "s90" to "90 min", "s120" to "2 hours", "s120p" to "2+ hours").forEach { (id, l) ->
                    MorphChip(l, session == id, { session = id }, height = 44.dp)
                }
            }
            Kicker("Equipment")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PRESETS.forEach { (l, ids) -> MorphChip(l, equip == ids.toSet(), { equip = ids.toSet() }, height = 44.dp) }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                opts.list("equipment").forEach { e ->
                    val id = e.s("id"); val on = id in equip
                    MorphChip(e.s("label"), on, { equip = if (on) equip - id else equip + id }, height = 36.dp)
                }
            }
            Kicker("Split")
            splits.forEach { sp ->
                val id = sp.s("id"); val on = id == chosen
                Surface(onClick = { split = id }, color = if (on) cs.primary else cs.surfaceContainerHigh, contentColor = if (on) cs.onPrimary else cs.onSurface,
                    shape = RoundedCornerShape(if (on) 18.dp else 22.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(sp.s("name"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        if (id == pick) Text("Engine's pick", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            if (cycleMode) CycleTemplatePicker(vm, template, { template = it }, custom, adapt, { adapt = it })
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Name (optional)") })
        }
        if (mode != "template") Button(
            onClick = {
                if (cycleMode) vm.createCycle(config(false).put("split", chosen), template, if (template == "custom") customBlocksJson(custom) else null, adapt)
                else vm.createProgram(config(false).put("split", chosen))
            },
            enabled = equip.isNotEmpty() && chosen != null,
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp).height(56.dp),
        ) { Text(if (cycleMode) "Build my cycle" else "Build my program", style = MaterialTheme.typography.titleSmall) }
    }
}
