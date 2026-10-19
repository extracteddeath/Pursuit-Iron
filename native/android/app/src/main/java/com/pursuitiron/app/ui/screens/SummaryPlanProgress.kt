@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.pursuitiron.app.ui.screens

import com.pursuitiron.app.ui.common.o
import org.json.JSONObject
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pursuitiron.app.AppViewModel
import com.pursuitiron.app.ui.LocalWide
import com.pursuitiron.app.ui.common.Badge
import com.pursuitiron.app.ui.common.Cookie
import com.pursuitiron.app.ui.common.EmptyState
import com.pursuitiron.app.ui.common.Kicker
import com.pursuitiron.app.ui.common.MorphChip
import com.pursuitiron.app.ui.common.PiCard
import com.pursuitiron.app.ui.common.PiIcons
import com.pursuitiron.app.ui.common.ProgressBar
import com.pursuitiron.app.ui.common.SectionTitle
import com.pursuitiron.app.ui.common.Sparkline
import com.pursuitiron.app.ui.common.StatTile
import com.pursuitiron.app.ui.common.b
import com.pursuitiron.app.ui.common.d
import com.pursuitiron.app.ui.common.fmtNum
import com.pursuitiron.app.ui.common.i
import com.pursuitiron.app.ui.common.list
import com.pursuitiron.app.ui.common.s

/* ---------------------------------------------------------------- Summary */
@Composable
fun SummaryScreen(vm: AppViewModel) {
    val r = vm.summary ?: return
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxSize().background(cs.primaryContainer).statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(Modifier.padding(top = 24.dp).size(140.dp), contentAlignment = Alignment.Center) {
            Cookie(cs.primary, Modifier.size(140.dp))
            Icon(PiIcons.Check, null, tint = cs.onPrimary, modifier = Modifier.size(56.dp))
        }
        Text("Workout saved", style = MaterialTheme.typography.displaySmall, color = cs.onPrimaryContainer, textAlign = TextAlign.Center)
        Text("Your next session is already planned from these sets.", style = MaterialTheme.typography.bodyLarge, color = cs.onPrimaryContainer, textAlign = TextAlign.Center)
        Row(Modifier.widthIn(max = 520.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("Sets", "${r.i("setsDone")}", "/ ${r.i("totalSets")}", Modifier.weight(1f), color = cs.surface)
            StatTile("Volume", com.pursuitiron.app.ui.common.fmt1(r.i("volume") / 1000.0), com.pursuitiron.app.ui.common.volUnit(r.s("unit", "kg")), Modifier.weight(1f), color = cs.surface)
            StatTile("Time", "${r.i("durationMin")}", "min", Modifier.weight(1f), color = cs.surface)
        }
        val prs = r.list("prs")
        if (prs.isNotEmpty()) PiCard(Modifier.widthIn(max = 520.dp), color = cs.surface, radius = 24.dp) {
            Text("${prs.size} new ${if (prs.size == 1) "best" else "bests"}", style = MaterialTheme.typography.titleMedium)
            prs.forEach { p ->
                Row { Text(p.s("name"), Modifier.weight(1f)); Text("e1RM ${fmtNum(p.d("prev"))} → ${fmtNum(p.d("e1rm"))}", style = MaterialTheme.typography.labelLarge, color = cs.primary) }
            }
        }
        r.list("achievements").forEach { a -> Badge(a.s("label"), cs.tertiaryContainer, cs.onTertiaryContainer) }
        Button(onClick = { vm.closeSummary() }, modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth().height(56.dp)) { Text("Done", style = MaterialTheme.typography.titleSmall) }
    }
}

/* ---------------------------------------------------------------- Plan */
@Composable
fun PlanScreen(vm: AppViewModel) {
    val p = vm.plan
    val wide = LocalWide.current
    val cs = MaterialTheme.colorScheme
    val full = StaggeredGridItemSpan.FullLine
    if (p == null) {
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(16.dp)) {
            Text("Plan", style = MaterialTheme.typography.displayMedium, modifier = Modifier.padding(top = 20.dp, bottom = 16.dp))
            EmptyState("No program yet", "Build one and every week of it appears here, set by set.") { Button(onClick = { vm.openSetup() }) { Text("Build my program") } }
        }
        return
    }
    val wk = p.i("weekIndex"); val weeks = p.i("weeksTotal").coerceAtLeast(1)
    var openCycle by remember { mutableStateOf<String?>(null) }
    var confirmNext by remember { mutableStateOf(false) }
    var compareOpen by remember { mutableStateOf(false) }
    var scheduleOpen by remember { mutableStateOf(false) }
    var matrixOpen by remember { mutableStateOf(false) }
    if (matrixOpen) WeekByWeekSheet(vm, p.s("programId")) { matrixOpen = false }
    if (compareOpen) CompareSheet(vm) { compareOpen = false }
    if (scheduleOpen) WeeklyScheduleSheet(vm, p.s("programId")) { scheduleOpen = false }
    openCycle?.let { id -> CycleSheet(vm, id) { openCycle = null } }
    if (confirmNext) AlertDialog(onDismissRequest = { confirmNext = false },
        title = { Text("Build the next block?") },
        text = { Text("Finish this block first. The next one is built from its logged workouts and the effort you reported. This program and your history stay saved.") },
        confirmButton = { Button(onClick = { confirmNext = false; vm.nextBlock(p.s("programId")) }) { Text("Build next block") } },
        dismissButton = { TextButton(onClick = { confirmNext = false }) { Text("Cancel") } })
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(if (wide) 2 else 1),
        contentPadding = PaddingValues(start = if (wide) 32.dp else 16.dp, end = if (wide) 32.dp else 16.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp), verticalItemSpacing = 14.dp, modifier = Modifier.statusBarsPadding(),
    ) {
        item(span = full) {
            Column(Modifier.padding(top = 20.dp, start = 4.dp)) {
                Text("Plan", style = MaterialTheme.typography.displayMedium)
                Text("${p.s("name")} · " + (if (p.b("endless")) (if (wk > weeks) "continuous · deload" else "continuous · week $wk of the wave") else if (wk > weeks) "deload week" else "week $wk of $weeks") + (if (p.i("current") > 0) " · you're on week ${p.i("current")}" else ""), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                androidx.compose.foundation.layout.FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { vm.openEditor(p.s("programId")) }) { Icon(PiIcons.List, null, modifier = Modifier.size(18.dp)); Text("  Edit program") }
                    FilledTonalButton(onClick = { scheduleOpen = true }) { Icon(PiIcons.Calendar, null, modifier = Modifier.size(18.dp)); Text("  Schedule") }
                    if (p.b("percent")) FilledTonalButton(onClick = { vm.tmFor = p.s("programId") }) { Text("Training maxes") }
                }
            }
        }
        if (p.s("cycleId").isNotBlank()) item(span = full) { CycleBanner(vm, p.s("cycleId")) { openCycle = p.s("cycleId") } }
        item(span = full) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(weeks) { w ->
                    val n = w + 1
                    MorphChip(if (n == p.i("current")) "Week $n · now" else "Week $n", n == wk, { vm.loadPlan(p.s("programId"), n) })
                }
            }
        }
        item(span = full) { VolumeWaveCard(vm, p.s("programId"), wk, { vm.loadPlan(p.s("programId"), it) }, { matrixOpen = true }) }
        item(span = full) { WeekVolumeCard(vm, p.s("programId"), wk) }
        p.list("days").forEach { d ->
            item {
                val isNext = d.s("id") == p.s("nextDayId") && wk == p.i("current")
                PiCard(color = if (isNext) cs.primaryContainer else cs.surfaceContainerLow, radius = 28.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            if (isNext) Kicker("Up next", cs.onPrimaryContainer)
                            Text(d.s("label"), style = MaterialTheme.typography.titleLarge)
                            d.i("minutes").takeIf { it > 0 }?.let { Text("About $it min", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant) }
                        }
                        FilledTonalButton(onClick = { vm.start(p.s("programId"), d.s("id"), wk) }) { Text("Start") }
                    }
                    d.list("exercises").forEach { e ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(e.s("name"), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(listOfNotNull(e.i("sets").takeIf { it > 0 }?.let { "$it ×" }, e.s("reps").takeIf { it.isNotBlank() }, e.s("rir").takeIf { it.isNotBlank() }?.let { "@ $it" }).joinToString(" "),
                                style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        if (p.b("canNextBlock")) item(span = full) {
            OutlinedButton(onClick = { confirmNext = true }, modifier = Modifier.fillMaxWidth()) { Icon(PiIcons.Redo, null, modifier = Modifier.size(18.dp)); Text("  Build next block") }
        }
        val progs = p.list("programs")
        item(span = full) { SectionTitle("Training cycles") }
        item(span = full) { CyclesList(vm) { openCycle = it } }
        item(span = full) { SectionTitle("Programs") }
        item(span = full) {
            Column(Modifier.clip(RoundedCornerShape(24.dp)), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                progs.forEach { pr ->
                    Row(Modifier.fillMaxWidth().background(cs.surfaceContainerLow).clickable { vm.loadPlan(pr.s("id"), null) }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(pr.s("name"), style = MaterialTheme.typography.titleSmall)
                            Text("${pr.i("days")} days a week" + if (pr.b("active")) " · training now" else "", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                        }
                        if (!pr.b("active")) FilledTonalButton(onClick = { vm.loadPlan(pr.s("id"), null); vm.pin(pr.s("id")) }) { Text("Train this") }
                        ProgramMenu(vm, pr.s("id"), pr.s("name"))
                    }
                }
                Row(Modifier.fillMaxWidth().background(cs.surfaceContainerLow).clickable { vm.openSetup() }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(PiIcons.Plus, null, tint = cs.primary); Text("  New program", style = MaterialTheme.typography.titleSmall, color = cs.primary)
                }
                Row(Modifier.fillMaxWidth().background(cs.surfaceContainerLow).clickable { compareOpen = true }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(PiIcons.Swap, null, tint = cs.primary); Text("  Compare programs or exercises", style = MaterialTheme.typography.titleSmall, color = cs.primary)
                }
            }
        }
    }
}

/* ---------------------------------------------------------------- Progress */
@Composable
fun ProgressScreen(vm: AppViewModel) {
    val p = vm.progress
    val wide = LocalWide.current
    val cs = MaterialTheme.colorScheme
    val full = StaggeredGridItemSpan.FullLine
    var liftId by remember { mutableStateOf<String?>(null) }
    var pinsOpen by remember { mutableStateOf(false) }
    liftId?.let { id -> LiftDetailSheet(vm, id) { liftId = null } }
    if (pinsOpen) PinsSheet(vm) { pinsOpen = false }
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(if (wide) 2 else 1),
        contentPadding = PaddingValues(start = if (wide) 32.dp else 16.dp, end = if (wide) 32.dp else 16.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp), verticalItemSpacing = 14.dp, modifier = Modifier.statusBarsPadding(),
    ) {
        item(span = full) { Text("Progress", style = MaterialTheme.typography.displayMedium, modifier = Modifier.padding(top = 20.dp, start = 4.dp)) }
        if (p == null || p.i("sessions") == 0) {
            item(span = full) { EmptyState("Your charts start here", "Log your first session and this fills with strength trends and records. Trends need two sessions per lift.") }
            return@LazyVerticalStaggeredGrid
        }
        item(span = full) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("Sessions", "${p.i("sessions")}", modifier = Modifier.weight(1f))
                StatTile("Lifts tracked", "${p.list("lifts").size}", modifier = Modifier.weight(1f))
                StatTile("Records", "${p.list("records").size}", modifier = Modifier.weight(1f))
            }
        }
        p.o("recap")?.takeIf { it.i("count") > 0 }?.let { r -> item(span = full) { RecapCard(JSONObject().put("recap", r).put("unit", p.s("unit"))) } }
        item(span = full) { PinnedLiftsSection(vm, onOpen = { liftId = it }, onManage = { pinsOpen = true }) }
        // Web VolumeCard: this week's sets per muscle against the landmarks, for the program being trained.
        vm.home?.o("next")?.let { nx -> item(span = full) { WeekVolumeCard(vm, nx.s("programId"), nx.i("weekIndex")) } }
        val recs = p.list("records")
        if (recs.isNotEmpty()) {
            item(span = full) { SectionTitle("Personal records") }
            item(span = full) {
                Column(Modifier.clip(RoundedCornerShape(24.dp)), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    recs.take(15).forEach { r ->
                        Row(Modifier.fillMaxWidth().background(cs.surfaceContainerLow).clickable { liftId = r.s("id") }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(r.s("name"), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (r.s("weight").isNotBlank()) Text(r.s("weight") + (r.s("reps").takeIf { it.isNotBlank() }?.let { "×$it" } ?: "") + "  ", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                            Text(fmtNum(r.d("e1rm")), style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }
            }
        }
        item(span = full) { StrengthStandardsCard(vm) { liftId = it } }
        // Every lift with history, trend or not; pinned ones already lead the page as cards.
        val others = p.list("lifts").filter { !it.b("pinned") }
        if (others.isNotEmpty()) {
            item(span = full) { SectionTitle("All lifts") }
            item(span = full) {
                Column(Modifier.clip(RoundedCornerShape(24.dp)), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    others.forEach { l ->
                        Row(Modifier.fillMaxWidth().background(cs.surfaceContainerLow).clickable { liftId = l.s("id") }.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                            val pts = l.list("points").map { it.d("e1rm") }
                            Text(l.s("name"), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (pts.size >= 2) {
                                Sparkline(pts, Modifier.width(56.dp).height(20.dp), color = cs.onSurfaceVariant)
                                val ch = pts.last() - pts.first()
                                Text("  " + (if (ch >= 0) "+" else "−") + fmtNum(kotlin.math.abs(ch)) + " ${p.s("unit")}", style = MaterialTheme.typography.labelMedium, color = if (ch > 0) cs.primary else cs.onSurfaceVariant)
                            } else Text("1 session", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                            Icon(PiIcons.ChevronRight, null, tint = cs.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        val rd = p.list("readiness")
        if (rd.isNotEmpty()) {
            item(span = full) { SectionTitle("Muscle readiness") }
            item(span = full) {
                PiCard(radius = 24.dp) {
                    rd.sortedBy { it.i("readiness") }.forEach { m ->
                        val v = m.i("readiness")
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(m.s("label"), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(110.dp))
                            ProgressBar(v / 100f, Modifier.weight(1f), height = 12.dp, color = if (v < 65) cs.tertiary else cs.primary)
                            Text("$v%" + if (v < 65) " · rest" else "", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(72.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgramMenu(vm: AppViewModel, id: String, name: String) {
    var open by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    Box {
        IconButton(onClick = { open = true }) { Icon(PiIcons.More, "$name options") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Edit") }, onClick = { open = false; vm.openEditor(id) })
            DropdownMenuItem(text = { Text("Duplicate") }, onClick = { open = false; vm.duplicateProgram(id) })
            // App.js programToText: share the plan as plain text.
            DropdownMenuItem(text = { Text("Share as text") }, onClick = {
                open = false
                scope.launch {
                    val t = vm.programText(id) ?: return@launch
                    val send = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain")
                        .putExtra(android.content.Intent.EXTRA_SUBJECT, t.s("name")).putExtra(android.content.Intent.EXTRA_TEXT, t.s("text"))
                    runCatching { ctx.startActivity(android.content.Intent.createChooser(send, "Share ${t.s("name")}")) }.onFailure { vm.toast("No app to share with") }
                }
            })
            DropdownMenuItem(text = { Text("Delete") }, onClick = { open = false; confirm = true })
        }
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("Delete $name?") },
        text = { Text("Your logged workouts stay in your history. You can undo this for a few seconds.") },
        confirmButton = { Button(onClick = { confirm = false; vm.deleteProgram(id) }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } })
}
