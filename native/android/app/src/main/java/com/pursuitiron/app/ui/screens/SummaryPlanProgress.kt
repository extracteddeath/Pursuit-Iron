@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.pursuitiron.app.ui.screens

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
            StatTile("Volume", fmtNum(r.i("volume") / 1000.0), "t", Modifier.weight(1f), color = cs.surface)
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
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(if (wide) 2 else 1),
        contentPadding = PaddingValues(start = if (wide) 32.dp else 16.dp, end = if (wide) 32.dp else 16.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp), verticalItemSpacing = 14.dp, modifier = Modifier.statusBarsPadding(),
    ) {
        item(span = full) {
            Column(Modifier.padding(top = 20.dp, start = 4.dp)) {
                Text("Plan", style = MaterialTheme.typography.displayMedium)
                Text("${p.s("name")} · week $wk of $weeks" + (if (p.i("current") > 0) " · you're on week ${p.i("current")}" else ""), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            }
        }
        item(span = full) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(weeks) { w ->
                    val n = w + 1
                    MorphChip(if (n == p.i("current")) "Week $n · now" else "Week $n", n == wk, { vm.loadPlan(p.s("programId"), n) })
                }
            }
        }
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
        val progs = p.list("programs")
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
                    }
                }
                Row(Modifier.fillMaxWidth().background(cs.surfaceContainerLow).clickable { vm.openSetup() }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(PiIcons.Plus, null, tint = cs.primary); Text("  New program", style = MaterialTheme.typography.titleSmall, color = cs.primary)
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
        p.list("lifts").filter { it.list("points").size >= 2 }.take(8).forEach { l ->
            item {
                val pts = l.list("points").map { it.d("e1rm") }
                PiCard(radius = 28.dp) {
                    Row {
                        Text(l.s("name"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val ch = pts.last() - pts.first()
                        Text((if (ch >= 0) "+" else "−") + fmtNum(kotlin.math.abs(ch)) + " ${p.s("unit")}", style = MaterialTheme.typography.labelLarge, color = if (ch >= 0) cs.primary else cs.onSurfaceVariant)
                    }
                    Sparkline(pts, Modifier.fillMaxWidth().height(72.dp), description = "${l.s("name")} estimated 1RM from ${fmtNum(pts.first())} to ${fmtNum(pts.last())}")
                    Text("Estimated 1RM · last ${pts.size} sessions", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                }
            }
        }
        val recs = p.list("records")
        if (recs.isNotEmpty()) {
            item(span = full) { SectionTitle("Personal records") }
            item(span = full) {
                Column(Modifier.clip(RoundedCornerShape(24.dp)), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    recs.take(15).forEach { r ->
                        Row(Modifier.fillMaxWidth().background(cs.surfaceContainerLow).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(r.s("name"), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (r.s("weight").isNotBlank()) Text("${r.s("weight")}×${r.s("reps")}  ", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                            Text(fmtNum(r.d("e1rm")), style = MaterialTheme.typography.titleSmall)
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
