@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.pursuitiron.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pursuitiron.app.AppViewModel
import com.pursuitiron.app.ui.common.PiIcons
import com.pursuitiron.app.ui.common.b
import com.pursuitiron.app.ui.common.d
import com.pursuitiron.app.ui.common.fmtNum
import com.pursuitiron.app.ui.common.i
import com.pursuitiron.app.ui.common.s
import com.pursuitiron.app.ui.common.strings
import com.pursuitiron.app.ui.theme.Motion
import org.json.JSONObject

private fun lastText(pl: JSONObject): String? = pl.optJSONObject("last")?.let { l ->
    (if (l.d("w") > 0) "${fmtNum(l.d("w"))} ${l.s("unit")}" else "BW") + " × ${l.i("r")}"
}

/** One choice row on a tonal card: icon tile, label, the line under it. */
@Composable
private fun WayOut(icon: ImageVector, label: String, sub: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().background(cs.surface).clickable(onClick = onClick).semantics(mergeDescendants = true) { role = Role.Button }
        .padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(cs.tertiaryContainer), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = cs.onTertiaryContainer, modifier = Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold), color = cs.onSurface)
            Text(sub, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp), color = cs.onSurfaceVariant)
        }
    }
}

/* The design's Today plateau card (Main.dc.html stall): the next session's longest-stalled lift and three ways out,
   applied when that day's workout starts. The saved program stays as it is (App.js Plateau options). */
@Composable
fun PlateauCard(vm: AppViewModel, next: JSONObject, pl: JSONObject) {
    val cs = MaterialTheme.colorScheme
    val pid = next.s("programId"); val did = next.s("dayId"); val id = pl.s("id"); val name = pl.s("name")
    val choice = pl.s("choice").takeIf { it.isNotBlank() && it != "null" }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(cs.tertiaryContainer).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(PiIcons.Trend, null, tint = cs.onTertiaryContainer, modifier = Modifier.size(22.dp))
            Text("$name has stalled", style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold), color = cs.onTertiaryContainer)
        }
        AnimatedContent(choice, transitionSpec = { Motion.peer() }, label = "stall") { c ->
            if (c == null) Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("No estimated-1RM PR in ${pl.i("since")} sessions" + (lastText(pl)?.let { ", last $it" } ?: "") + ". Pick a way out for your next session; your saved plan stays as it is.",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp), color = cs.onTertiaryContainer)
                Column(Modifier.clip(RoundedCornerShape(20.dp)), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    WayOut(PiIcons.Swap, "Try a variation", "The swap picker opens on it when the workout starts. Keep the swap for today or for the program.") {
                        vm.setPlateauChoice(pid, did, id, "swap", "$name: pick a variation when you start") }
                    if (!pl.b("percent")) WayOut(PiIcons.ChevronUp, "Build reps first", "Double progression this session: hold " + (pl.optJSONObject("last")?.takeIf { it.d("w") > 0 }?.let { "${fmtNum(it.d("w"))} ${it.s("unit")}" } ?: "the load") + ", add reps, then load.") {
                        vm.setPlateauChoice(pid, did, id, "reps", "$name builds reps next session") }
                    if (!pl.b("percent")) WayOut(PiIcons.Redo, "Go about 10% lighter", "About 10% under today’s load next session, rounded down to your plates.") {
                        vm.setPlateauChoice(pid, did, id, "lighter", "$name goes about 10% lighter next session") }
                }
            } else Row(verticalAlignment = Alignment.CenterVertically) {
                Text(when (c) { "swap" -> "The swap picker opens on $name when you start."
                    "reps" -> "$name holds its load and builds reps next session."
                    else -> "$name starts about 10% lighter next session. Your saved plan is unchanged." },
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp), color = cs.onTertiaryContainer, modifier = Modifier.weight(1f))
                TextButton(onClick = { vm.setPlateauChoice(pid, did, id, null) }) { Text("Change", fontWeight = FontWeight.Bold, color = cs.onTertiaryContainer) }
            }
        }
    }
}

/* App.js Plateau options in the workout: keep the plan, build reps with double progression, lighter targets today, or a variation. */
@Composable
fun PlateauSheet(vm: AppViewModel, s: JSONObject, ex: JSONObject, onSwap: () -> Unit, onClose: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val ei = ex.i("ei")
    val pl = ex.optJSONObject("plateau")
    val percent = ex.optJSONObject("progression")?.b("locked") == true
    val lighter = ex.strings("lighter")
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.padding(horizontal = 4.dp)) {
                Text("Plateau options", style = MaterialTheme.typography.headlineSmall.copy(fontSize = 22.sp, fontWeight = FontWeight.SemiBold))
                Text(ex.s("name"), style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold), modifier = Modifier.padding(top = 4.dp))
                Text(pl?.s("advice") ?: "Choose how to approach this exercise today.", style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
                    color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            }
            Column(Modifier.clip(RoundedCornerShape(24.dp)), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                WayOut(PiIcons.Check, "Keep the current plan", "Train it as prescribed today.") { onClose() }
                if (!percent) WayOut(PiIcons.ChevronUp, "Build reps with double progression", "Applies to the remaining sets. Review keeping it in the program after this workout.") {
                    vm.act("progression", "ei" to ei, "style" to "double"); onClose() }
                if (!percent && lighter.isNotEmpty()) WayOut(PiIcons.Redo, "Use lighter targets today", lighter.joinToString(" · ") + "\nAbout 10% lighter, rounded down to loads you have. Your entries stay; pending technique sets are removed.") {
                    vm.act("plateauLighter", "ei" to ei); onClose()
                    vm.toast("Lighter remaining targets for today. Your saved plan is unchanged.") { vm.act("plateauUndo") } }
                WayOut(PiIcons.Swap, "Choose an exercise variation", "Review replacements in the swap picker.") { onSwap() }
            }
            if (percent) Text("Keep this program’s percentage stages together. Load reductions and progression switches belong in its program settings.",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp), color = cs.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
        }
    }
}
