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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pursuitiron.app.AppViewModel
import com.pursuitiron.app.ui.common.Kicker
import com.pursuitiron.app.ui.common.MorphChip
import com.pursuitiron.app.ui.common.PiCard
import com.pursuitiron.app.ui.common.PiIcons
import com.pursuitiron.app.ui.common.ProgressBar
import com.pursuitiron.app.ui.common.b
import com.pursuitiron.app.ui.common.d
import com.pursuitiron.app.ui.common.i
import com.pursuitiron.app.ui.common.list
import com.pursuitiron.app.ui.common.o
import com.pursuitiron.app.ui.common.s
import com.pursuitiron.app.ui.common.strings
import org.json.JSONArray
import org.json.JSONObject
import java.text.DateFormat
import java.util.Date

/** The blocks of a cycle as a row of segments: done, current, upcoming. Shape and text carry the state, not colour alone. */
@Composable
fun BlockStrip(blocks: List<JSONObject>, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        blocks.forEach { b ->
            val st = b.s("state")
            val w = b.i("total").coerceAtLeast(1).toFloat()
            Column(Modifier.weight(w), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.fillMaxWidth().height(if (st == "active") 10.dp else 6.dp).clip(CircleShape)
                    .background(when (st) { "done" -> cs.primary; "active" -> cs.primary.copy(alpha = 0.55f); else -> cs.surfaceContainerHighest }))
                Text(b.s("label"), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = if (st == "upcoming") cs.onSurfaceVariant else cs.onSurface)
            }
        }
    }
}

/** Plan tab: the cycle this program belongs to. */
@Composable
fun CycleBanner(vm: AppViewModel, cycleId: String, onOpen: () -> Unit) {
    val c = vm.cycles.list("cycles").firstOrNull { it.s("id") == cycleId } ?: return
    val cs = MaterialTheme.colorScheme
    val live = c.o("live")
    PiCard(color = cs.secondaryContainer, radius = 28.dp, onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Kicker("Training cycle", cs.onSecondaryContainer)
                Text(c.s("name"), style = MaterialTheme.typography.titleLarge, color = cs.onSecondaryContainer)
            }
            Icon(PiIcons.ChevronRight, "Open cycle", tint = cs.onSecondaryContainer)
        }
        BlockStrip(c.list("blocks"))
        val blocks = c.list("blocks")
        Text(buildString {
            append("Block ${c.i("activeBlock") + 1} of ${blocks.size}")
            if (live != null) append(if (live.b("isDeload")) " · deload week" else " · week ${live.i("weekIndex")} of ${live.i("weeksTotal")}")
            append(" · ${c.i("pct")}% through")
        }, style = MaterialTheme.typography.bodySmall, color = cs.onSecondaryContainer)
    }
}

/** Plan tab: every cycle, newest first. */
@Composable
fun CyclesList(vm: AppViewModel, onOpen: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val list = vm.cycles.list("cycles")
    Column(Modifier.clip(RoundedCornerShape(24.dp)), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        list.forEach { c ->
            Row(Modifier.fillMaxWidth().background(cs.surfaceContainerLow).clickable { onOpen(c.s("id")) }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(c.s("name"), style = MaterialTheme.typography.titleSmall)
                    Text(if (c.b("done")) "Complete · ${c.list("blocks").size} blocks" else "Block ${c.i("activeBlock") + 1} of ${c.list("blocks").size} · ${c.i("pct")}%" + if (c.has("live") && !c.isNull("live")) " · training now" else "",
                        style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
                Icon(PiIcons.ChevronRight, null, modifier = Modifier.size(18.dp))
            }
        }
        Row(Modifier.fillMaxWidth().background(cs.surfaceContainerLow).clickable { vm.openSetup(cycle = true) }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(PiIcons.Plus, null, tint = cs.primary); Text("  New training cycle", style = MaterialTheme.typography.titleSmall, color = cs.primary)
        }
    }
}

/* ------------------------------------------------------------------ cycle setup (App.js Wizard cycle mode + CustomCycleComposer) */
data class CustomBlock(val phase: String, val weeks: Int)

@Composable
fun CycleTemplatePicker(vm: AppViewModel, template: String, onTemplate: (String) -> Unit, custom: SnapshotStateList<CustomBlock>, adapt: Boolean, onAdapt: (Boolean) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val v = vm.cycles
    val phases = v.list("phases")
    v.list("templates").forEach { t ->
        val on = template == t.s("id")
        Surface(onClick = { onTemplate(t.s("id")) }, color = if (on) cs.secondaryContainer else cs.surfaceContainerLow, contentColor = if (on) cs.onSecondaryContainer else cs.onSurface,
            shape = RoundedCornerShape(if (on) 22.dp else 18.dp), border = androidx.compose.foundation.BorderStroke(if (on) 2.dp else 1.dp, if (on) cs.primary else cs.outlineVariant),
            modifier = Modifier.fillMaxWidth().semantics { role = Role.RadioButton }) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row { Text(t.s("name"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f)); Text(t.s("tag"), style = MaterialTheme.typography.labelMedium) }
                Text(t.s("blurb"), style = MaterialTheme.typography.bodySmall)
                Text(t.list("blocks").joinToString("  →  ") { "${it.s("label")} ${it.i("weeks")}w" }, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
    val onCustom = template == "custom"
    Surface(onClick = { onTemplate("custom") }, color = if (onCustom) cs.secondaryContainer else cs.surfaceContainerLow, contentColor = if (onCustom) cs.onSecondaryContainer else cs.onSurface,
        shape = RoundedCornerShape(if (onCustom) 22.dp else 18.dp), border = androidx.compose.foundation.BorderStroke(if (onCustom) 2.dp else 1.dp, if (onCustom) cs.primary else cs.outlineVariant),
        modifier = Modifier.fillMaxWidth().semantics { role = Role.RadioButton }) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text("Custom cycle", style = MaterialTheme.typography.titleSmall)
            Text("Choose every phase and how long it runs.", style = MaterialTheme.typography.bodySmall)
        }
    }
    if (onCustom) {
        custom.forEachIndexed { i, b ->
            PiCard(color = cs.surfaceContainer, radius = 22.dp, padding = 14.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Block ${i + 1}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    IconButton(onClick = { if (b.weeks > 1) custom[i] = b.copy(weeks = b.weeks - 1) }) { Icon(PiIcons.Minus, "Fewer weeks in block ${i + 1}") }
                    Text("${b.weeks} wk", style = MaterialTheme.typography.titleSmall, modifier = Modifier.width(44.dp))
                    IconButton(onClick = { if (b.weeks < 24) custom[i] = b.copy(weeks = b.weeks + 1) }) { Icon(PiIcons.Plus, "More weeks in block ${i + 1}") }
                    if (custom.size > 2) IconButton(onClick = { custom.removeAt(i) }) { Icon(PiIcons.Trash, "Remove block ${i + 1}") }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    phases.forEach { p -> MorphChip(p.s("label"), b.phase == p.s("phase"), { custom[i] = b.copy(phase = p.s("phase")) }, height = 36.dp) }
                }
            }
        }
        if (custom.size < 8) TextButton(onClick = { custom.add(CustomBlock(phases.firstOrNull()?.s("phase") ?: "hypertrophy_accumulation", 4)) }) { Text("+ Add a block") }
    }
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(cs.surfaceContainerHigh).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Adapt between blocks", style = MaterialTheme.typography.titleSmall)
            Text("Each block is rebuilt from how the last one went", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
        Switch(checked = adapt, onCheckedChange = onAdapt)
    }
}

fun customBlocksJson(list: List<CustomBlock>): JSONArray = JSONArray(list.map { JSONObject().put("phase", it.phase).put("weeks", it.weeks) })

