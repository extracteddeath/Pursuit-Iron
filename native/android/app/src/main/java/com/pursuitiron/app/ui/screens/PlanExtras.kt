@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.pursuitiron.app.ui.screens

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pursuitiron.app.AppViewModel
import com.pursuitiron.app.ui.LocalWide
import com.pursuitiron.app.ui.common.Kicker
import com.pursuitiron.app.ui.common.MorphChip
import com.pursuitiron.app.ui.common.PiCard
import com.pursuitiron.app.ui.common.PiIcons
import com.pursuitiron.app.ui.common.b
import com.pursuitiron.app.ui.common.i
import com.pursuitiron.app.ui.common.list
import com.pursuitiron.app.ui.common.s
import com.pursuitiron.app.ui.theme.Motion
import org.json.JSONObject

/**
 * App.js LoadWaveCard: planned working sets per week, the deload included as the program's own fact.
 * Bars are scaled from the lightest week, not from zero (every week of a block is a large number), and
 * the caption says so. Tapping a bar shows that week in the plan.
 */
@Composable
fun VolumeWaveCard(vm: AppViewModel, programId: String, selected: Int, onPick: (Int) -> Unit, onMatrix: () -> Unit) {
    var data by remember(programId) { mutableStateOf<JSONObject?>(null) }
    LaunchedEffect(programId, vm.plan) { data = vm.volumeWave(programId) }
    val weeks = data.list("weeks")
    if (weeks.isEmpty()) return
    val cs = MaterialTheme.colorScheme
    val peak = weeks.maxOf { it.i("sets") }
    val low = weeks.minOf { it.i("sets") }
    val sel = weeks.firstOrNull { it.i("w") == selected }
    PiCard(radius = 28.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Volume wave", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (sel != null) Text((if (sel.b("deload")) "Deload" else "Week $selected") + " · ${sel.i("sets")} sets", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
        Row(Modifier.fillMaxWidth().height(118.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            weeks.forEach { wk ->
                val w = wk.i("w"); val on = w == selected; val dl = wk.b("deload"); val n = wk.i("sets")
                val frac = if (peak == low) 1f else (n - low).toFloat() / (peak - low).toFloat()
                val r by animateDpAsState(if (on) 12.dp else 8.dp, Motion.spring(), label = "bar")
                Column(
                    Modifier.weight(1f).clickable { onPick(w) }
                        .clearAndSetSemantics { contentDescription = (if (dl) "Deload week" else "Week $w") + ": $n sets" + if (on) ", showing" else "" },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("$n", style = MaterialTheme.typography.labelSmall, color = if (on) cs.primary else cs.onSurfaceVariant)
                    Box(Modifier.fillMaxWidth().height((16 + 62 * frac).dp).clip(RoundedCornerShape(r))
                        .background(when { on -> cs.primary; dl -> cs.tertiaryContainer; else -> cs.primaryContainer }))
                    Text(if (dl) "DL" else "W$w", style = MaterialTheme.typography.labelSmall, color = if (on) cs.primary else cs.onSurfaceVariant)
                }
            }
        }
        Text("Bars run from the lightest week ($low sets) to the heaviest ($peak), not from zero. Tap one to see that week.",
            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        FilledTonalButton(onClick = onMatrix) { Icon(PiIcons.Grid, null, Modifier.size(18.dp)); Text("  Every lift, week by week") }
    }
}

/**
 * App.js ProgressionCard: every lift for every week of the block, sets × reps and reps in reserve,
 * paged a few weeks at a time so the lift's name never scrolls away. Cells where a lift steps up from
 * the week before are highlighted; the deload column is its own colour.
 */
@Composable
fun WeekByWeekSheet(vm: AppViewModel, programId: String, onClose: () -> Unit) {
    var data by remember(programId) { mutableStateOf<JSONObject?>(null) }
    LaunchedEffect(programId) { data = vm.weekByWeek(programId) }
    var filter by remember { mutableStateOf("all") }
    var q by remember { mutableStateOf("") }
    var from by remember { mutableIntStateOf(-1) }
    val cs = MaterialTheme.colorScheme
    val cols = if (LocalWide.current) 4 else 2
    ModalBottomSheet(onDismissRequest = onClose) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Week by week", style = MaterialTheme.typography.headlineMedium)
            val d = data
            if (d == null) { Text("Loading…", color = cs.onSurfaceVariant); return@Column }
            val rows = d.list("rows")
            val total = rows.firstOrNull()?.list("cells")?.size ?: 0
            if (total == 0) { Text("This program has no weeks to show.", color = cs.onSurfaceVariant); return@Column }
            val current = d.i("current")
            // Opens on the week you're in; the pager takes over from there.
            val start = (if (from < 0) (if (current > 0) current else 1) - 1 else from).coerceIn(0, (total - cols).coerceAtLeast(0))
            val shownCols = (start until (start + cols).coerceAtMost(total)).toList()
            val days = rows.map { it.s("dayLabel") }.distinct().size
            Text("${rows.size} lifts across $days day${if (days == 1) "" else "s"}. The same numbers the plan and the workout use; highlighted cells are where a lift steps up from the week before.",
                style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("Search a lift") }, shape = RoundedCornerShape(999.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("all" to "All lifts", "main" to "Main lifts", "upper" to "Upper", "lower" to "Lower").forEach { (k, l) -> MorphChip(l, filter == k, { filter = k }) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { from = (start - 1).coerceAtLeast(0) }, enabled = start > 0, modifier = Modifier.alpha(if (start > 0) 1f else 0.38f)) { Icon(PiIcons.ChevronLeft, "Earlier weeks") }
                Text("Weeks ${start + 1}–${shownCols.last() + 1} of $total", style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                val more = start + cols < total
                IconButton(onClick = { from = start + 1 }, enabled = more, modifier = Modifier.alpha(if (more) 1f else 0.38f)) { Icon(PiIcons.ChevronRight, "Later weeks") }
            }
            val needle = q.trim().lowercase()
            val shown = rows.filter { r ->
                (needle.isEmpty() || r.s("name").lowercase().contains(needle)) && when (filter) {
                    "main" -> r.b("primary"); "upper" -> r.b("upper"); "lower" -> !r.b("upper"); else -> true
                }
            }
            Column(Modifier.clip(RoundedCornerShape(22.dp)), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(Modifier.fillMaxWidth().background(cs.surfaceContainerHigh).padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Kicker("Lift", modifier = Modifier.weight(1f))
                    shownCols.forEach { c ->
                        val cell = rows.first().list("cells")[c]
                        val w = cell.i("w"); val dl = cell.b("deload")
                        Column(Modifier.width(76.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("W$w", style = MaterialTheme.typography.labelMedium, color = if (w == current) cs.primary else cs.onSurfaceVariant)
                            if (dl || w == current) Text(if (dl) "DELOAD" else "NOW", style = MaterialTheme.typography.labelSmall, color = if (dl) cs.tertiary else cs.primary)
                        }
                    }
                }
                if (shown.isEmpty()) Text("No lift matches that.", color = cs.onSurfaceVariant, modifier = Modifier.fillMaxWidth().background(cs.surfaceContainerLow).padding(16.dp))
                shown.forEach { r ->
                    val cells = r.list("cells")
                    val desc = r.s("name") + ", " + r.s("dayLabel") + ": " + shownCols.joinToString("; ") { c -> val x = cells[c]; "week ${x.i("w")} ${x.i("sets")} sets of ${x.s("range")}" + (x.s("rir").takeIf { it.isNotBlank() }?.let { " at $it reps in reserve" } ?: "") }
                    Row(Modifier.fillMaxWidth().background(cs.surfaceContainerLow).padding(horizontal = 14.dp, vertical = 10.dp).semantics(mergeDescendants = true) { contentDescription = desc },
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(r.s("name"), style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(r.s("dayLabel") + if (r.b("primary")) " · main" else "", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        shownCols.forEach { c ->
                            val x = cells[c]; val prev = cells.getOrNull(c - 1)
                            val step = prev != null && !x.b("deload") && (x.i("sets") != prev.i("sets") || x.s("rir") != prev.s("rir"))
                            val bg = when { x.b("deload") -> cs.surfaceContainerHigh; step -> cs.primaryContainer; else -> cs.surfaceContainerLow }
                            val fg = if (step) cs.onPrimaryContainer else cs.onSurface
                            Column(Modifier.width(76.dp).clip(RoundedCornerShape(14.dp)).background(bg).padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("${x.i("sets")} × ${x.s("range")}", style = MaterialTheme.typography.labelLarge, color = fg, maxLines = 1)
                                x.s("rir").takeIf { it.isNotBlank() }?.let { Text("@$it", style = MaterialTheme.typography.labelSmall, color = if (step) cs.onPrimaryContainer else cs.onSurfaceVariant) }
                            }
                        }
                    }
                }
            }
            if (d.b("percent")) Text("Percentage lifts take their loads from your training maxes; sets and reps here are the method's own.",
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
    }
}

/**
 * App.js VolumeCard for the week on screen: working sets per muscle drawn against its own MEV–MRV band
 * (MAV marked), the four-band verdict in App.js's wording, the volume check across all training weeks
 * with the engine's repair, and what your own history says about each muscle's productive range.
 */
@Composable
fun WeekVolumeCard(vm: AppViewModel, programId: String, week: Int) {
    var data by remember(programId, week) { mutableStateOf<JSONObject?>(null) }
    LaunchedEffect(programId, week, vm.plan) { data = vm.weeklyVolume(programId, week) }
    val d = data ?: return
    val rows = d.list("rows")
    if (rows.isEmpty()) return
    val cs = MaterialTheme.colorScheme
    var open by remember { mutableStateOf(false) }
    val flagged = rows.count { it.s("tone") != "good" }
    PiCard(radius = 28.dp) {
        Row(Modifier.fillMaxWidth().clickable { open = !open }.semantics(mergeDescendants = true) { contentDescription = "Week $week volume. " + if (open) "Collapse" else "Expand" },
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Week $week volume", style = MaterialTheme.typography.titleMedium)
                Text("${com.pursuitiron.app.ui.common.fmtNum(d.optDouble("total"))} sets · ${rows.size} muscles" + if (flagged > 0) " · $flagged outside MEV–MRV" else " · all in range",
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
            Icon(if (open) PiIcons.ChevronDown else PiIcons.ChevronRight, null, tint = cs.onSurfaceVariant)
        }
        if (!open) return@PiCard
        val issues = d.list("issues")
        if (issues.isNotEmpty()) Column(Modifier.clip(RoundedCornerShape(18.dp)).background(cs.surfaceContainerHigh).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Volume check · all training weeks", style = MaterialTheme.typography.titleSmall)
            issues.forEach { i ->
                val under = i.s("status") == "under"
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(if (under) "LOW" else "HIGH", style = MaterialTheme.typography.labelSmall, color = if (under) cs.onSurfaceVariant else cs.error, modifier = Modifier.width(36.dp).padding(top = 2.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${i.s("label")} · ${com.pursuitiron.app.ui.common.fmtNum(i.optDouble("sets"))} sets (" + (if (under) "MEV ${i.s("mev")}" else "MRV ${i.s("mrv")}") + ")", style = MaterialTheme.typography.bodyMedium)
                        if (i.s("fix").isNotBlank()) Text(i.s("fix"), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    }
                }
            }
            if (d.b("canRepair")) FilledTonalButton(onClick = { vm.repairVolume(programId) }, modifier = Modifier.fillMaxWidth()) { Icon(PiIcons.Sparkle, null, Modifier.size(16.dp)); Text("  Fix weekly volume") }
            else Text("These need an exercise added or a longer session; the auto-fix can't resolve them without changing your day structure.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
        rows.forEach { r ->
            val sets = r.optDouble("sets"); val mev = r.optDouble("mev"); val mav = r.optDouble("mav"); val mrv = r.optDouble("mrv")
            val tone = r.s("tone")
            val fill = when (tone) { "warn" -> cs.error; "muted" -> cs.onSurfaceVariant; else -> cs.primary }
            Column(Modifier.semantics(mergeDescendants = true) { contentDescription = "${r.s("label")}: ${com.pursuitiron.app.ui.common.fmtNum(sets)} sets, ${r.s("zone")}. MEV ${com.pursuitiron.app.ui.common.fmtNum(mev)}, MRV ${com.pursuitiron.app.ui.common.fmtNum(mrv)}" },
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(r.s("label"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Text("${com.pursuitiron.app.ui.common.fmtNum(sets)} · ${r.s("zone")}", style = MaterialTheme.typography.labelMedium, color = if (tone == "warn") cs.error else cs.onSurfaceVariant)
                }
                val track = cs.surfaceContainerHighest; val band = cs.secondaryContainer; val tick = cs.onSecondaryContainer
                androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp))) {
                    val top = maxOf(mrv * 1.15, sets * 1.05, 1.0).toFloat()
                    fun x(v: Double) = (v.toFloat() / top).coerceIn(0f, 1f) * size.width
                    drawRect(track)
                    drawRect(band, topLeft = androidx.compose.ui.geometry.Offset(x(mev), 0f), size = androidx.compose.ui.geometry.Size(x(mrv) - x(mev), size.height))
                    drawRect(fill, size = androidx.compose.ui.geometry.Size(x(sets), size.height * 0.5f), topLeft = androidx.compose.ui.geometry.Offset(0f, size.height * 0.25f))
                    drawRect(tick, topLeft = androidx.compose.ui.geometry.Offset(x(mav) - 1f, 0f), size = androidx.compose.ui.geometry.Size(2f, size.height))
                }
            }
        }
        Text("Band: MEV to MRV for your experience; the tick is MAV. Sets include partial credit from lifts that also train the muscle.",
            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        val verdicts = d.list("verdicts")
        if (verdicts.isNotEmpty()) Column(Modifier.clip(RoundedCornerShape(18.dp)).background(cs.surfaceContainerHigh).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("From your training · your response, not averages", style = MaterialTheme.typography.titleSmall)
            verdicts.forEach { v ->
                val st = v.s("status"); val n = com.pursuitiron.app.ui.common.fmtNum(v.optDouble("planned"))
                Text("${v.s("label")}: " + when (st) {
                    "in" -> "planned at $n sets — inside the ${v.s("lo")}–${v.s("hi")} range that has driven your fastest gains."
                    "over" -> "planned at $n sets, above the ${v.s("lo")}–${v.s("hi")} that has driven your fastest gains. More sets haven't bought you more progress."
                    else -> "planned at $n sets, below the ${v.s("lo")}–${v.s("hi")} that has driven your fastest gains."
                } + " (${v.s("weeks")} weeks compared)", style = MaterialTheme.typography.bodySmall, color = if (st == "in") cs.onSurface else cs.error)
            }
        }
    }
}
