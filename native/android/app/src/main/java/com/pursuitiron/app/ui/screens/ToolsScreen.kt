@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.pursuitiron.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pursuitiron.app.AppViewModel
import com.pursuitiron.app.ui.common.Kicker
import com.pursuitiron.app.ui.common.MorphChip
import com.pursuitiron.app.ui.common.PiCard
import com.pursuitiron.app.ui.common.PiIcons
import com.pursuitiron.app.ui.common.Segmented
import com.pursuitiron.app.ui.common.b
import com.pursuitiron.app.ui.common.d
import com.pursuitiron.app.ui.common.fmtNum
import com.pursuitiron.app.ui.common.list
import com.pursuitiron.app.ui.common.o
import com.pursuitiron.app.ui.common.s
import com.pursuitiron.app.ui.common.strings
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/** The bar a lift loads, for the plate calculator's default (App.js PlateCalcSheet initialBar). */
fun barFor(equip: List<String>): String? = when {
    "smith" in equip -> "smith"
    "ezbar" in equip -> "ezbar"
    "trapbar" in equip -> "trap"
    "safetybar" in equip -> "ssb"
    "barbell" in equip -> "barbell"
    else -> null
}

/* ------------------------------------------------------------------ plates + 1RM (App.js PlateCalcSheet) */
@Composable
fun ToolsSheet(vm: AppViewModel, initialTab: String = "plates", initialWeight: String = "", initialBar: String = "barbell", onUse: ((String) -> Unit)? = null, onClose: () -> Unit) {
    var tab by remember { mutableStateOf(initialTab) }
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // From a set, the sheet is just the calculator (the design's plate bar); from Tools it also has 1RM.
            if (onUse == null) Segmented(listOf("plates" to "Plates", "orm" to "1RM"), tab, { tab = it })
            if (tab == "plates") PlatesTool(vm, initialWeight, initialBar, onUse?.let { u -> { v: String -> u(v); onClose() } }) else OneRmTool(vm)
            TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("Done") }
        }
    }
}

@Composable
private fun PlatesTool(vm: AppViewModel, initialWeight: String, initialBar: String, onUse: ((String) -> Unit)?) {
    var w by remember { mutableStateOf(initialWeight) }
    var bar by remember { mutableStateOf(initialBar) }
    var res by remember { mutableStateOf<JSONObject?>(null) }
    LaunchedEffect(w, bar) { res = vm.plateCalc(w.replace(',', '.').toDoubleOrNull(), bar) }
    val cs = MaterialTheme.colorScheme
    val r = res
    val u = r.s("unit", "kg")
    val step = r.d("step").takeIf { it > 0 } ?: if (u == "lb") 5.0 else 2.5
    val barW = r.list("bars").firstOrNull { it.s("id") == bar }?.d("weight") ?: 0.0
    val total = w.replace(',', '.').toDoubleOrNull()
    val setTo = { v: Double -> w = fmtNum(kotlin.math.round(v * 100) / 100) }
    Text("Plate calculator", style = MaterialTheme.typography.headlineSmall)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        r.list("bars").forEach { b -> MorphChip("${b.s("label")} · ${fmtNum(b.d("weight"))}", bar == b.s("id"), { bar = b.s("id") }) }
    }
    // The design's total: big, with −/+ by the smallest pair of plates you own. Tap it to type.
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(52.dp).clip(CircleShape).background(cs.surfaceContainerHigh).clickable { setTo(((total ?: barW) - step).coerceAtLeast(barW)) }
            .semantics { contentDescription = "${fmtNum(step)} $u lighter"; role = Role.Button }, contentAlignment = Alignment.Center) { Icon(PiIcons.Minus, null) }
        Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.Center) {
            androidx.compose.foundation.text.BasicTextField(w, { v -> w = v.filter { it.isDigit() || it == '.' || it == ',' }.take(7) }, singleLine = true,
                textStyle = MaterialTheme.typography.displayMedium.copy(color = cs.onSurface, textAlign = TextAlign.End),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(cs.primary),
                modifier = Modifier.width(150.dp).semantics { contentDescription = "Total weight, $u" },
                decorationBox = { inner -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) { if (w.isEmpty()) Text("0", style = MaterialTheme.typography.displayMedium, color = cs.onSurfaceVariant); inner() } })
            Text(" $u", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
        }
        Box(Modifier.size(52.dp).clip(CircleShape).background(cs.surfaceContainerHigh).clickable { setTo((total ?: barW) + step) }
            .semantics { contentDescription = "${fmtNum(step)} $u heavier"; role = Role.Button }, contentAlignment = Alignment.Center) { Icon(PiIcons.Plus, null) }
    }
    val out = r.o("result")
    val plates = out?.optJSONArray("plates")?.let { a -> (0 until a.length()).map { a.optDouble(it) } } ?: emptyList()
    val under = out?.b("under") == true
    val per = when { out == null -> "Enter a total, bar included"; under -> "Lighter than the ${fmtNum(barW)} $u bar"; plates.isEmpty() -> "Just the bar"; else -> "Per side: ${plates.joinToString(" · ") { fmtNum(it) }} $u" }
    // Both sides of the bar, inner plate at the collar, sized by weight (the design's drawing).
    val big = if (u == "lb") 45.0 else 25.0
    val sizes = plates.distinct().sortedDescending()
    val tones = listOf(cs.primary, cs.tertiary, cs.onSecondaryContainer, cs.onSurfaceVariant)
    @Composable fun Disc(p: Double) {
        Box(Modifier.width(if (p >= big * 0.5) 13.dp else if (p >= big * 0.2) 10.dp else 7.dp).height((26 + kotlin.math.sqrt(p / big) * 74).dp)
            .clip(RoundedCornerShape(4.dp)).background(tones[sizes.indexOf(p).coerceIn(0, 3)]))
    }
    Row(Modifier.fillMaxWidth().height(112.dp).clip(RoundedCornerShape(22.dp)).background(cs.surface).semantics { contentDescription = per },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) { plates.reversed().forEach { Disc(it) } }
        Box(Modifier.width(8.dp).height(16.dp).clip(RoundedCornerShape(2.dp)).background(cs.outline))
        Box(Modifier.weight(1f, fill = false).widthIn(min = 40.dp, max = 120.dp).fillMaxWidth().height(8.dp).clip(RoundedCornerShape(2.dp)).background(cs.outline))
        Box(Modifier.width(8.dp).height(16.dp).clip(RoundedCornerShape(2.dp)).background(cs.outline))
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) { plates.forEach { Disc(it) } }
    }
    Text(per, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    if (out != null) {
        val exact = out.b("exact") && !under
        val below = out.d("below").takeIf { !out.isNull("below") && it > 0 }
        val above = out.d("above").takeIf { !out.isNull("above") && it > 0 }
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(cs.surfaceContainerHigh).padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (exact) Icon(PiIcons.Check, null, tint = cs.primary, modifier = Modifier.size(18.dp))
            Text(when { under -> "Pick a weight at or above the bar."; exact -> "Exact with the plates you have."
                else -> "Can't make ${fmtNum(total ?: 0.0)} $u exactly with your plates — closest: ${below?.let { fmtNum(it) } ?: "—"}${above?.let { " or ${fmtNum(it)}" } ?: ""} $u." },
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            if (!exact && !under && below != null) androidx.compose.material3.FilledTonalButton(onClick = { setTo(below) }, modifier = Modifier.height(36.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp)) { Text("Use ${fmtNum(below)} $u", style = MaterialTheme.typography.labelLarge) }
        }
    }
    Text("Uses the bars and plates saved for your active gym. Log the total, bar included.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
    if (onUse != null && total != null && total > 0 && !under) Button(onClick = { onUse(fmtNum(total)) }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
        Text("Use ${fmtNum(total)} $u for this set", style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun OneRmTool(vm: AppViewModel) {
    var w by remember { mutableStateOf("") }
    var reps by remember { mutableStateOf("5") }
    var res by remember { mutableStateOf<JSONObject?>(null) }
    LaunchedEffect(w, reps) { res = vm.oneRm(w.replace(',', '.').toDoubleOrNull(), reps.toIntOrNull()) }
    val cs = MaterialTheme.colorScheme
    val u = res.s("unit", "kg")
    Text("One-rep max", style = MaterialTheme.typography.headlineSmall)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(w, { w = it }, Modifier.weight(1f), singleLine = true, label = { Text("Weight ($u)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        OutlinedTextField(reps, { reps = it.filter(Char::isDigit).take(2) }, Modifier.weight(1f), singleLine = true, label = { Text("Reps (1-20)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
    }
    val r = res ?: return
    if (r.isNull("e1rm")) { Text("Enter a set you did to estimate your max.", color = cs.onSurfaceVariant); return }
    Text("${fmtNum(r.d("e1rm"))} $u", style = MaterialTheme.typography.displaySmall, color = cs.primary)
    Text("Loads are rounded down, never past what the estimate supports.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f)) {
            Kicker("% of 1RM")
            r.list("percents").forEach { x -> Row { Text("${x.optInt("pct")}%", color = cs.onSurfaceVariant, modifier = Modifier.weight(1f)); Text(fmtNum(x.d("load")), style = MaterialTheme.typography.titleSmall) } }
        }
        Column(Modifier.weight(1f)) {
            Kicker("Reps")
            r.list("reps").forEach { x -> Row { Text("${x.optInt("reps")} rep${if (x.optInt("reps") == 1) "" else "s"}", color = cs.onSurfaceVariant, modifier = Modifier.weight(1f)); Text(fmtNum(x.d("load")), style = MaterialTheme.typography.titleSmall) } }
        }
    }
}

/* ------------------------------------------------------------------ plates owned + smallest jump (App.js Settings) */
@Composable
fun LoadingCard(vm: AppViewModel) {
    val g = vm.gyms ?: return
    val u = g.s("unit", "kg")
    val all = g.o("plates")?.optJSONArray("all")?.let { a -> (0 until a.length()).map { a.optDouble(it) } } ?: emptyList()
    val on = g.o("plates")?.optJSONArray("on")?.let { a -> (0 until a.length()).map { a.optDouble(it) } } ?: emptyList()
    val cs = MaterialTheme.colorScheme
    PiCard(radius = 28.dp) {
        Text("Plates and jumps", style = MaterialTheme.typography.titleMedium)
        Text("The plates you have (per side). Plate math and suggested loads use only these.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            all.forEach { p -> val sel = on.any { kotlin.math.abs(it - p) < 1e-9 }
                MorphChip(fmtNum(p), sel, { vm.setPlates(if (sel) on.filter { kotlin.math.abs(it - p) > 1e-9 } else on + p) }) }
        }
        Kicker("Smallest load jump")
        val mi = g.o("minInc")
        val choices = g.optJSONArray("minIncChoices")?.let { a -> (0 until a.length()).mapNotNull { a.optJSONArray(it) }.map { it.optDouble(0) to it.optString(1) } } ?: emptyList()
        val cur = mi.d("v").takeIf { it > 0 && mi.s("unit") == u } ?: 0.0
        Segmented(choices.map { (v, l) -> fmtNum(v) to (if (v == 0.0) "Standard" else "$l $u") }, fmtNum(cur), { vm.setMinInc(it.toDouble()) })
    }
}

/* ------------------------------------------------------------------ gyms (App.js GymsSheet) */
@Composable
fun GymsCard(vm: AppViewModel, onEdit: () -> Unit) {
    val g = vm.gyms ?: return
    val active = g.list("gyms").firstOrNull { it.s("id") == g.s("activeGymId") }
    val cs = MaterialTheme.colorScheme
    PiCard(radius = 28.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Gyms", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = onEdit) { Text("Edit") }
        }
        Text("Swaps and loads use the equipment at the gym you're in.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            g.list("gyms").forEach { x -> MorphChip(x.s("name"), x.s("id") == active?.s("id"), { vm.setActiveGym(x.s("id")) }) }
        }
        active?.let { Text(gymMeta(it), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant) }
    }
}

private fun cleanGym(x: JSONObject): JSONObject = JSONObject(x.toString()).apply { remove("limitKeys"); remove("inventoryKeys"); remove("barDefaults") }

@Composable
fun GymsSheet(vm: AppViewModel, onClose: () -> Unit) {
    val g = vm.gyms ?: return
    val list = remember { mutableStateListOf<JSONObject>().apply { addAll(g.list("gyms").map(::cleanGym)) } }
    var active by remember { mutableStateOf(g.s("activeGymId")) }
    var open by remember { mutableStateOf<String?>(active) }
    val removed = remember { mutableStateListOf<String>() }
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme
    val equipment = g.list("equipment")
    fun patch(id: String, f: (JSONObject) -> Unit) { val i = list.indexOfFirst { it.s("id") == id }; if (i >= 0) list[i] = JSONObject(list[i].toString()).also(f) }
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Gyms", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                Button(onClick = { vm.saveGyms(JSONArray(list.map { it }), active, removed.toList()); onClose() }) { Text("Save") }
            }
            list.forEach { gym ->
                val id = gym.s("id"); val eq = gym.strings("equipment").toSet(); val isOpen = open == id
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(cs.surfaceContainerHigh)) {
                    Row(Modifier.fillMaxWidth().clickable { open = if (isOpen) null else id }.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = active == id, onClick = { active = id }, modifier = Modifier.semantics { contentDescription = "Train at ${gym.s("name")}" })
                        Column(Modifier.weight(1f)) {
                            Text(gym.s("name"), style = MaterialTheme.typography.titleSmall)
                            Text(gymMeta(gym), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1)
                        }
                        Icon(if (isOpen) PiIcons.ChevronDown else PiIcons.ChevronRight, if (isOpen) "Collapse" else "Edit ${gym.s("name")}", modifier = Modifier.size(20.dp))
                    }
                    if (isOpen) Column(Modifier.padding(horizontal = 14.dp).padding(bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(gym.s("name"), { v -> patch(id) { it.put("name", v.take(40)) } }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Name") })
                        equipment.forEach { cat ->
                            val items = cat.list("items")
                            val allOn = items.all { it.s("id") in eq }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Kicker(cat.s("cat"), modifier = Modifier.weight(1f))
                                TextButton(onClick = { patch(id) { o -> val cur = o.strings("equipment").toMutableSet(); if (allOn) cur.removeAll(items.map { it.s("id") }.toSet()) else cur.addAll(items.map { it.s("id") }); o.put("equipment", JSONArray(cur.toList())) } }) {
                                    Text(if (allOn) "Clear all" else "Select all")
                                }
                            }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                items.forEach { e -> val has = e.s("id") in eq
                                    MorphChip(e.s("label"), has, { patch(id) { o -> val cur = o.strings("equipment").toMutableList(); if (has) cur.remove(e.s("id")) else cur.add(e.s("id")); o.put("equipment", JSONArray(cur)) } }, height = 36.dp)
                                }
                            }
                        }
                        GymBarsAndWeights(g, gym, id) { f -> patch(id, f) }
                        // Heaviest available, typed in the unit shown (App.js stores limits with limitUnit).
                        val lu = gym.s("limitUnit", "kg")
                        val limitKeys = g.list("gyms").firstOrNull { it.s("id") == id }?.list("limitKeys") ?: emptyList()
                        if (limitKeys.isNotEmpty()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Kicker("Heaviest available", modifier = Modifier.weight(1f))
                                TextButton(onClick = { patch(id) { it.put("limitUnit", if (lu == "kg") "lb" else "kg") } }) { Text("Use ${if (lu == "kg") "lb" else "kg"}") }
                            }
                            limitKeys.forEach { k ->
                                val cur = gym.optJSONObject("limits")?.let { l -> if (l.isNull(k.s("key"))) "" else fmtNum(l.optDouble(k.s("key"))) } ?: ""
                                OutlinedTextField(cur, { v -> val clean = v.filter { c -> c.isDigit() || c == '.' }
                                    patch(id) { o -> val l = o.optJSONObject("limits") ?: JSONObject(); if (clean.isBlank()) l.remove(k.s("key")) else clean.toDoubleOrNull()?.let { l.put(k.s("key"), it) }; o.put("limits", l); o.put("limitUnit", lu) } },
                                    Modifier.fillMaxWidth(), singleLine = true, label = { Text("${k.s("label")}, ${k.s("sub")} ($lu)") }, placeholder = { Text("No limit") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                            }
                        }
                        if (list.size > 1) OutlinedButton(onClick = {
                            removed.add(id); list.removeAll { it.s("id") == id }
                            if (active == id) active = list.first().s("id"); open = null
                        }) { Text("Remove ${gym.s("name")}") }
                    }
                }
            }
            Kicker("Add a gym", modifier = Modifier.padding(top = 8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                g.list("presets").forEach { pr ->
                    MorphChip("+ ${pr.s("name")}", false, { scope.launch { vm.newGym(pr.s("key"))?.let { ng ->
                        var name = ng.s("name"); var k = 2
                        while (list.any { it.s("name") == name }) name = "${pr.s("name")} ${k++}"
                        ng.put("name", name); list.add(ng); open = ng.s("id")
                    } } })
                }
            }
            Box(Modifier.heightIn(min = 8.dp))
        }
    }
}


/* The design's gym editor: BARS · EMPTY WEIGHT (barbell and Smith, typed in the unit shown — App.js
   barWeights) and AVAILABLE WEIGHTS (dumbbells, kettlebells, stack notches — App.js inventory, kept in the
   gym's limitUnit), with the rack presets. Loads snap to these lists. */
@Composable
private fun GymBarsAndWeights(g: JSONObject, gym: JSONObject, id: String, patch: ((JSONObject) -> Unit) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val eq = gym.strings("equipment").toSet()
    val bars = listOf("barbell" to "Barbell", "smith" to "Smith machine").filter { it.first in eq }
    if (bars.isNotEmpty()) {
        val bw = gym.optJSONObject("barWeights")
        val bu = bw?.optString("unit")?.takeIf { it == "kg" || it == "lb" } ?: g.s("unit", "kg")
        val defaults = mapOf("barbell" to (if (bu == "kg") 20.0 else 45.0), "smith" to (if (bu == "kg") 15.0 else 35.0))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Kicker("Bars · empty weight", modifier = Modifier.weight(1f))
            TextButton(onClick = { patch { o ->
                val cur = o.optJSONObject("barWeights") ?: JSONObject().put("unit", bu)
                val to = if (bu == "kg") "lb" else "kg"; val f = if (to == "lb") 2.2046226218487757 else 1 / 2.2046226218487757
                val nb = JSONObject().put("unit", to)
                listOf("barbell", "smith").forEach { k -> if (cur.has(k) && !cur.isNull(k)) nb.put(k, Math.round(cur.optDouble(k) * f * 2) / 2.0) }
                o.put("barWeights", nb)
            } }) { Text("Use ${if (bu == "kg") "lb" else "kg"}") }
        }
        bars.forEach { (k, label) ->
            val v = bw?.takeIf { it.has(k) && !it.isNull(k) }?.optDouble(k) ?: defaults.getValue(k)
            val step = if (bu == "kg") 0.5 else 1.0
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(cs.surfaceContainer).padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(label, style = MaterialTheme.typography.titleSmall)
                    if (v != defaults.getValue(k)) Text("Standard is ${fmtNum(defaults.getValue(k))} $bu", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
                val put = { nv: Double -> patch { o -> val cur = o.optJSONObject("barWeights") ?: JSONObject().put("unit", bu); cur.put("unit", bu); cur.put(k, nv.coerceIn(0.0, 500.0)); o.put("barWeights", cur) } }
                HoldStep(PiIcons.Minus, "$label bar ${fmtNum(step)} $bu lighter", 40.dp, false) { put(v - step) }
                Text("${fmtNum(v)} $bu", style = MaterialTheme.typography.titleSmall, modifier = Modifier.widthIn(min = 64.dp), textAlign = TextAlign.Center)
                HoldStep(PiIcons.Plus, "$label bar ${fmtNum(step)} $bu heavier", 40.dp, false) { put(v + step) }
            }
        }
    }
    val invKeys = g.list("gyms").firstOrNull { it.s("id") == id }?.list("inventoryKeys") ?: emptyList()
    if (invKeys.isEmpty()) return
    var open by remember(id) { mutableStateOf(false) }
    val lu = gym.s("limitUnit", "kg")
    val inv = gym.optJSONObject("inventory")
    val count = invKeys.sumOf { k -> inv?.optJSONArray(k.s("key"))?.length() ?: 0 }
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { open = !open }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Kicker("Available weights")
            Text(if (count == 0) "Any weight — tell it what you actually have" else "$count weights listed · loads snap to these", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
        Icon(if (open) PiIcons.ChevronDown else PiIcons.ChevronRight, if (open) "Collapse available weights" else "Edit available weights", modifier = Modifier.size(20.dp))
    }
    if (!open) return
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Weights in $lu", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, modifier = Modifier.weight(1f))
        TextButton(onClick = { patch { o ->
            val to = if (lu == "kg") "lb" else "kg"; val f = if (to == "lb") 2.2046226218487757 else 1 / 2.2046226218487757
            val cur = o.optJSONObject("inventory") ?: JSONObject(); val ni = JSONObject()
            cur.keys().forEach { key -> val a = cur.optJSONArray(key) ?: return@forEach; ni.put(key, JSONArray((0 until a.length()).map { Math.round(a.optDouble(it) * f * 2) / 2.0 }.distinct().sorted())) }
            o.put("inventory", ni); o.put("limitUnit", to)
            o.optJSONObject("limits")?.let { l -> val nl = JSONObject(); l.keys().forEach { key -> nl.put(key, Math.round(l.optDouble(key) * f * 2) / 2.0) }; o.put("limits", nl) }
        } }) { Text("Use ${if (lu == "kg") "lb" else "kg"}") }
    }
    val presets = g.o("rackPresets")
    invKeys.forEach { k ->
        val key = k.s("key")
        val vals = inv?.optJSONArray(key)?.let { a -> (0 until a.length()).map { a.optDouble(it) } } ?: emptyList()
        val setVals = { nv: List<Double> -> patch { o -> val cur = o.optJSONObject("inventory") ?: JSONObject(); if (nv.isEmpty()) cur.remove(key) else cur.put(key, JSONArray(nv.distinct().sorted())); o.put("inventory", cur); o.put("limitUnit", lu) } }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(cs.surfaceContainer).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(k.s("label"), style = MaterialTheme.typography.titleSmall)
                    Text(if (vals.isEmpty()) "${k.s("sub")} · any weight" else "${k.s("sub")} · ${fmtNum(vals.first())}–${fmtNum(vals.last())} $lu", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
                if (vals.isNotEmpty()) TextButton(onClick = { setVals(emptyList()) }) { Text("Clear") }
            }
            presets?.optJSONArray(key)?.let { arr ->
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (0 until arr.length()).map { arr.getJSONObject(it) }.forEach { pr ->
                        val pu = pr.s("unit", "kg"); val f = if (pu == lu) 1.0 else if (lu == "lb") 2.2046226218487757 else 1 / 2.2046226218487757
                        val pv = pr.optJSONArray("v")?.let { a -> (0 until a.length()).map { Math.round(a.optDouble(it) * f * 2) / 2.0 } } ?: emptyList()
                        MorphChip(pr.s("name"), pv == vals, { setVals(pv) }, height = 34.dp)
                    }
                }
            }
            if (vals.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                vals.forEach { v -> MorphChip("${fmtNum(v)} ✕", true, { setVals(vals - v) }, height = 32.dp, modifier = Modifier.semantics { contentDescription = "Remove ${fmtNum(v)} $lu" }) }
            }
            var add by remember(id, key) { mutableStateOf("") }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(add, { add = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(6) }, Modifier.weight(1f), singleLine = true,
                    label = { Text("Add a weight ($lu)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedButton(onClick = { add.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }?.let { setVals(vals + it); add = "" } }, modifier = Modifier.height(52.dp)) { Text("Add") }
            }
        }
    }
}


/** The design's short gym line: "23 items · dumbbells to 24 kg · 3 machine settings". */
fun gymMeta(gym: JSONObject): String {
    val lu = gym.s("limitUnit", "kg")
    val inv = gym.optJSONObject("inventory")
    val parts = mutableListOf("${gym.strings("equipment").size} items")
    fun last(k: String) = inv?.optJSONArray(k)?.let { a -> if (a.length() > 0) a.optDouble(a.length() - 1) else null }
    (last("dumbbell") ?: gym.optJSONObject("limits")?.takeIf { it.has("dumbbell") }?.optDouble("dumbbell"))?.let { parts += "dumbbells to ${fmtNum(it)} $lu" }
    (last("kettlebell") ?: gym.optJSONObject("limits")?.takeIf { it.has("kettlebell") }?.optDouble("kettlebell"))?.let { parts += "bells to ${fmtNum(it)} $lu" }
    inv?.optJSONArray("machine")?.length()?.takeIf { it > 0 }?.let { parts += "$it machine setting${if (it == 1) "" else "s"}" }
    return parts.joinToString(" · ")
}
