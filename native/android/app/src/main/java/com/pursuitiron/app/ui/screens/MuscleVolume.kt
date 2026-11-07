package com.pursuitiron.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pursuitiron.app.AppViewModel
import com.pursuitiron.app.ui.common.PiCard
import com.pursuitiron.app.ui.common.PiIcons
import com.pursuitiron.app.ui.common.b
import com.pursuitiron.app.ui.common.fmtNum
import com.pursuitiron.app.ui.common.list
import com.pursuitiron.app.ui.common.s
import com.pursuitiron.app.ui.theme.Motion
import org.json.JSONObject

/**
 * Plan's "Sets by muscle" (App.js VolumeCard → VolumeBars, open by default and dense): every muscle the week trains
 * with its sets against its own MEV–MRV band, one line each. Tap a muscle for the lifts behind it — day, lift and the
 * sets it counts (a share when the lift only helps). Heads, when on, list front/side/rear delts, upper traps,
 * obliques, serratus, tibialis and the wrist directions under their muscle, each with its own band and lifts.
 */
@Composable
fun MuscleVolumeCard(vm: AppViewModel, programId: String, week: Int, onDetails: () -> Unit) {
    var data by remember(programId, week) { mutableStateOf<JSONObject?>(null) }
    LaunchedEffect(programId, week, vm.plan) { data = vm.weeklyVolume(programId, week) }
    val d = data ?: return
    val rows = d.list("rows")
    if (rows.isEmpty()) return
    val cs = MaterialTheme.colorScheme
    var open by remember(programId) { mutableStateOf<String?>(null) }
    val heads = vm.prefs.volHeads
    val anyHeads = rows.any { it.list("heads").isNotEmpty() }
    val flagged = rows.count { it.b("off") }
    PiCard(radius = 28.dp, padding = 16.dp) {
        // Title and the Heads switch share a line; the summary gets the full width under them (it wrapped to three lines beside the switch).
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Sets by muscle", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (anyHeads) {
                    Text("Heads", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                    Switch(heads, { vm.prefs.changeVolHeads(it) }, modifier = Modifier.semantics { contentDescription = "Show muscle heads" })
                }
            }
            Text("Week $week · ${d.optInt("workingSets").takeIf { it > 0 } ?: fmtNum(d.optDouble("total"))} working sets · ${rows.size} muscles" +
                if (flagged > 0) " · $flagged outside their range" else " · all in range",
                style = MaterialTheme.typography.bodySmall, color = if (flagged > 0) cs.error else cs.onSurfaceVariant)
        }
        Column(Modifier.clip(RoundedCornerShape(18.dp)), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            rows.forEach { r ->
                val key = r.s("part")
                MuscleLine(r, key, open == key, 0.dp) { open = if (open == key) null else key }
                if (heads) r.list("heads").forEach { h ->
                    val hk = "${key}:${h.s("key")}"
                    MuscleLine(h, hk, open == hk, 18.dp) { open = if (open == hk) null else hk }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Shaded: the weekly range that builds muscle. Tick: the sweet spot. A lift counts fully for its main muscle and partly for muscles it also works.",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp), color = cs.onSurfaceVariant, modifier = Modifier.weight(1f))
        }
        if (d.list("issues").isNotEmpty() || d.list("verdicts").isNotEmpty())
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(cs.surfaceContainerHigh).clickable(onClick = onDetails)
                .semantics { role = Role.Button }.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(listOfNotNull(d.list("issues").size.takeIf { it > 0 }?.let { "Volume check: $it to review" },
                    d.list("verdicts").size.takeIf { it > 0 }?.let { "what your own training says" }).joinToString(" · "),
                    style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                Icon(PiIcons.ChevronRight, null, tint = cs.onSurfaceVariant)
            }
    }
}

@Composable
private fun MuscleLine(r: JSONObject, key: String, expanded: Boolean, indent: Dp, onTap: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val sets = r.optDouble("sets", 0.0); val mev = r.optDouble("mev", 0.0); val mav = r.optDouble("mav", 0.0); val mrv = r.optDouble("mrv", 0.0)
    val tone = r.s("tone")
    val from = r.list("from")
    val head = indent > 0.dp
    val ink = when (tone) { "warn" -> cs.error; "muted" -> cs.onSurfaceVariant; else -> cs.primary }
    Column(Modifier.fillMaxWidth().background(if (expanded) cs.surfaceContainerHighest else cs.surfaceContainerHigh)) {
        Row(Modifier.fillMaxWidth().heightIn(min = if (head) 40.dp else 46.dp).clickable(onClick = onTap)
            .semantics(mergeDescendants = true) {
                role = Role.Button; stateDescription = if (expanded) "Expanded" else "Collapsed"
                contentDescription = "${r.s("label")}: ${fmtNum(sets)} sets, ${r.s("zone")}. range ${fmtNum(mev)} to ${fmtNum(mrv)}. ${from.size} lifts"
            }.padding(start = 12.dp + indent, end = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(r.s("label"), style = if (head) MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp) else MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp),
                color = if (head) cs.onSurfaceVariant else cs.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(if (head) 100.dp else 112.dp))
            val track = cs.surfaceContainerLowest; val band = cs.secondaryContainer; val tick = cs.onSecondaryContainer
            Canvas(Modifier.weight(1f).height(if (head) 8.dp else 10.dp).clip(RoundedCornerShape(5.dp))) {
                val top = maxOf(mrv * 1.15, sets * 1.05, 1.0).toFloat()
                fun x(v: Double) = (v.toFloat() / top).coerceIn(0f, 1f) * size.width
                drawRect(track)
                if (mrv > mev) drawRect(band, topLeft = Offset(x(mev), 0f), size = Size(x(mrv) - x(mev), size.height))
                drawRect(ink, size = Size(x(sets), size.height * 0.5f), topLeft = Offset(0f, size.height * 0.25f))
                if (mav > 0) drawRect(tick, topLeft = Offset(x(mav) - 1f, 0f), size = Size(2f, size.height))
            }
            Text(fmtNum(sets), style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold), color = if (tone == "warn") cs.error else cs.onSurface,
                maxLines = 1, softWrap = false, modifier = Modifier.widthIn(min = 38.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End)
            Icon(if (expanded) PiIcons.ChevronUp else PiIcons.ChevronDown, null, Modifier.size(16.dp), tint = cs.onSurfaceVariant)
        }
        AnimatedVisibility(expanded, enter = Motion.expandIn, exit = Motion.shrinkOut) {
            Column(Modifier.padding(start = 12.dp + indent, end = 12.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${r.s("zone").replaceFirstChar { it.uppercase() }} · range ${fmtNum(mev)}–${fmtNum(mrv)} · sweet spot ${fmtNum(mav)}",
                    style = MaterialTheme.typography.labelMedium, color = if (tone == "warn") cs.error else cs.onSurfaceVariant)
                if (from.isEmpty()) Text("Nothing in this program trains it this week.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                from.forEach { f ->
                    val direct = f.b("direct")
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(if (direct) cs.primary else Color.Transparent)
                            .then(if (direct) Modifier else Modifier.background(cs.outline.copy(alpha = 0.5f), CircleShape)))
                        Column(Modifier.weight(1f)) {
                            Text(f.s("name"), style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(f.s("day") + if (!direct) " · helps (${fmtNum(f.optDouble("sets"))} sets × share)" else "",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text(fmtNum(f.optDouble("contrib")), style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = if (direct) cs.onSurface else cs.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
