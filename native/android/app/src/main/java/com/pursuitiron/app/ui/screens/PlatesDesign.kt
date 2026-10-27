@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.pursuitiron.app.ui.screens

import com.pursuitiron.app.ui.common.b
import com.pursuitiron.app.ui.common.o
import com.pursuitiron.app.ui.common.strings
import com.pursuitiron.app.ui.common.has
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pursuitiron.app.AppViewModel
import com.pursuitiron.app.ui.common.MorphChip
import com.pursuitiron.app.ui.common.PiIcons
import com.pursuitiron.app.ui.common.d
import com.pursuitiron.app.ui.common.fmtNum
import com.pursuitiron.app.ui.common.i
import com.pursuitiron.app.ui.common.list
import com.pursuitiron.app.ui.common.s
import com.pursuitiron.app.ui.theme.Motion
import org.json.JSONObject

/* The design's Settings → Plates & bars: open the calculator, the active gym's bar, the plate pairs it
   owns (one stepper per size), the heaviest load they make and the smallest jump. */

@Composable
private fun PanelCap(text: String) = Text(text, style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.4.sp),
    color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
fun PlatesSheet(vm: AppViewModel, onCalc: () -> Unit, onClose: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    LaunchedEffect(Unit) { vm.loadPlateSetup() }
    val p = vm.plateSetup
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Plates & bars", style = MaterialTheme.typography.headlineSmall)
            if (p == null) return@Column
            val u = p.s("unit", "kg")
            val gymNote = if (p.i("gymCount") > 1 && p.s("gymName").isNotBlank()) " For ${p.s("gymName")} — each gym keeps its own bars and plates (Settings → Gyms)." else ""
            Text("The plate calculator only uses what you own, and tells you the closest load you can actually build.$gymNote",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 18.sp), color = cs.onSurfaceVariant)
            Row(Modifier.fillMaxWidth().height(48.dp).clip(CircleShape).background(cs.secondaryContainer).clickable(onClick = onCalc).semantics(mergeDescendants = true) { role = Role.Button },
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Icon(PiIcons.Plates, null, Modifier.size(18.dp), tint = cs.onSecondaryContainer)
                Text("  Open the plate calculator", style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold), color = cs.onSecondaryContainer)
            }
            PanelCap("BAR · ${u.uppercase()}")
            val bar = p.d("bar")
            val choices = p.optJSONArray("barChoices")?.let { a -> (0 until a.length()).map { a.optDouble(it) } } ?: emptyList()
            Row(Modifier.fillMaxWidth().semantics { contentDescription = "Bar weight" }, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                choices.forEach { b -> BarChip("${fmtNum(b)} $u", kotlin.math.abs(bar - b) < 0.01, Modifier.weight(1f)) { vm.setBarbellWeight(b) } }
            }
            PanelCap("PLATES · PAIRS YOU OWN")
            PlatePairList(p.list("rows"), u, 12.dp, 40.dp, 20.dp) { size, n -> vm.setPlatePair(size, n) }
            Text(buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold)) { append("${fmtNum(p.d("max"))} $u") }
                append(" is the heaviest you can load · smallest jump ")
                withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold)) { append(p.s("jump")) }
            }, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp), color = cs.onPrimaryContainer,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(cs.primaryContainer).padding(14.dp))
            PanelCap("SMALLEST JUMP")
            val mi = p.d("minInc")
            val jumps = p.optJSONArray("jumpChoices")?.let { a -> (0 until a.length()).mapNotNull { a.optJSONArray(it) }.map { it.optDouble(0) to it.optString(1) } } ?: emptyList()
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                jumps.forEach { (v, l) -> MorphChip(l, kotlin.math.abs(mi - v) < 1e-9, { vm.setMinInc(v) }) }
            }
            Text(p.s("jumpNote"), style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp), color = cs.onSurfaceVariant)
        }
    }
}

@Composable
private fun BarChip(label: String, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val r by animateDpAsState(if (on) 16.dp else 24.dp, Motion.spring(), label = "bar")
    val bg by animateColorAsState(if (on) cs.secondaryContainer else cs.surfaceContainerHigh, Motion.effects(), label = "barBg")
    Box(modifier.height(48.dp).clip(RoundedCornerShape(r)).background(bg).clickable(onClick = onClick)
        .semantics(mergeDescendants = true) { role = Role.RadioButton; selected = on }, contentAlignment = Alignment.Center) {
        Text(label, style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, fontWeight = FontWeight.ExtraBold), color = if (on) cs.onSecondaryContainer else cs.onSurface)
    }
}

/** One row per plate size: a disc sized to the plate, the size, − count +. Shared by Plates & bars and the gym editor. */
@Composable
fun PlatePairList(rows: List<JSONObject>, unit: String, pad: Dp, btn: Dp, radius: Dp, onSet: (Double, Int) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val big = if (unit == "lb") 45.0 else 25.0
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(radius)), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        rows.forEach { r ->
            val size = r.d("size"); val n = r.i("n"); val label = "${fmtNum(size)} $unit"
            Row(Modifier.fillMaxWidth().background(cs.surfaceContainerHigh).padding(horizontal = pad, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.width(40.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.width(if (size >= big * 0.6) 12.dp else 9.dp).height((12 + Math.round(size / big * 24).toInt()).dp).clip(RoundedCornerShape(4.dp))
                        .background(if (n > 0) cs.primary else cs.outlineVariant.copy(alpha = 0.5f)))
                }
                Text(label, style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, fontWeight = FontWeight.ExtraBold), modifier = Modifier.weight(1f))
                Box(Modifier.size(btn).clip(RoundedCornerShape(12.dp)).background(cs.surface).clickable(enabled = n > 0) { onSet(size, n - 1) }
                    .semantics { contentDescription = "One fewer pair of $label"; role = Role.Button }, contentAlignment = Alignment.Center) {
                    Icon(PiIcons.Minus, null, Modifier.size(18.dp), tint = if (n > 0) cs.onSurface else cs.onSurfaceVariant)
                }
                Text("$n", style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp, fontWeight = FontWeight.ExtraBold), textAlign = TextAlign.Center, modifier = Modifier.width(28.dp))
                Box(Modifier.size(btn).clip(CircleShape).background(cs.primary).clickable(enabled = n < 10) { onSet(size, n + 1) }
                    .semantics { contentDescription = "One more pair of $label"; role = Role.Button }, contentAlignment = Alignment.Center) {
                    Icon(PiIcons.Plus, null, Modifier.size(18.dp), tint = cs.onPrimary)
                }
            }
        }
    }
}

/** The gym editor's PLATES · PAIRS ON THE TREE: edits the draft gym's own counts (saved with the gym). */
@Composable
fun GymPlatePairs(g: JSONObject, gym: JSONObject, id: String, patch: ((JSONObject) -> Unit) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val u = g.s("unit", "kg")
    val saved = g.list("gyms").firstOrNull { it.s("id") == id }?.optJSONObject("plateSum")
    val sizes = saved?.list("rows")?.map { it.d("size") } ?: (if (u == "lb") listOf(45.0, 35.0, 25.0, 10.0, 5.0, 2.5, 1.25) else listOf(25.0, 20.0, 15.0, 10.0, 5.0, 2.5, 1.25, 0.5))
    val own = gym.optJSONObject("platePairs")?.optJSONObject(u)
    val base = saved?.list("rows")?.associate { it.d("size") to it.i("n") } ?: emptyMap()
    val counts = sizes.associateWith { s -> own?.let { o -> o.optInt(fmtNum(s), 0) } ?: (base[s] ?: 0) }
    PanelCap("PLATES · PAIRS ON THE TREE")
    PlatePairList(sizes.map { JSONObject().put("size", it).put("n", counts[it] ?: 0) }, u, 10.dp, 36.dp, 18.dp) { size, n ->
        patch { o ->
            val pp = o.optJSONObject("platePairs") ?: JSONObject()
            val m = JSONObject(); counts.forEach { (s, c) -> m.put(fmtNum(s), c) }; m.put(fmtNum(size), n)
            pp.put(u, m); o.put("platePairs", pp)
        }
    }
    val bw = gym.optJSONObject("barWeights"); val lb = 2.2046226218487757
    val bar = if (bw != null && bw.has("barbell") && !bw.isNull("barbell")) bw.optDouble("barbell").let { v -> if (bw.optString("unit") == u) v else if (u == "lb") v * lb else v / lb } else if (u == "lb") 45.0 else 20.0
    val max = bar + 2 * counts.entries.sumOf { (s, c) -> s * c }
    val ownedMin = counts.filter { it.value > 0 }.keys.minOrNull()
    Text("Up to ${fmtNum(Math.round(max * 100) / 100.0)} $u on the barbell · smallest jump ${fmtNum((ownedMin ?: 0.0) * 2)} $u",
        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = cs.onSurfaceVariant)
}
