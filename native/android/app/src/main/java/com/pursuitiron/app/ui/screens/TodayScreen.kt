@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.pursuitiron.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pursuitiron.app.AppViewModel
import com.pursuitiron.app.data.Transfer
import com.pursuitiron.app.ui.LocalWide
import com.pursuitiron.app.ui.common.Cookie
import com.pursuitiron.app.ui.common.Kicker
import com.pursuitiron.app.ui.common.PiCard
import com.pursuitiron.app.ui.common.PiIcons
import com.pursuitiron.app.ui.common.ProgressBar
import com.pursuitiron.app.ui.common.SectionTitle
import com.pursuitiron.app.ui.common.StatTile
import com.pursuitiron.app.ui.common.b
import com.pursuitiron.app.ui.common.fmtNum
import com.pursuitiron.app.ui.common.i
import com.pursuitiron.app.ui.common.list
import com.pursuitiron.app.ui.common.o
import com.pursuitiron.app.ui.common.s
import com.pursuitiron.app.ui.common.strings
import org.json.JSONObject
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TodayScreen(vm: AppViewModel) {
    val h = vm.home ?: return
    val wide = LocalWide.current
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    var readinessOpen by remember { mutableStateOf(false) }
    var historyId by remember { mutableStateOf<String?>(null) }
    val next = h.o("next")
    val full = StaggeredGridItemSpan.FullLine

    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(if (wide) 2 else 1),
        contentPadding = PaddingValues(start = if (wide) 32.dp else 16.dp, end = if (wide) 32.dp else 16.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalItemSpacing = 16.dp,
        modifier = Modifier.statusBarsPadding(),
    ) {
        item(span = full) {
            Column(Modifier.padding(top = 20.dp, start = 4.dp)) {
                Text(SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(Date()), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                Text("Today", style = MaterialTheme.typography.displayMedium)
            }
        }

        if (h.b("empty") && !vm.prefs.transferDismissed) item(span = full) {
            PiCard(color = cs.secondaryContainer, radius = 28.dp, padding = 20.dp) {
                Kicker("Already training with Pursuit Iron?", cs.onSecondaryContainer)
                Text("Bring your workouts over", style = MaterialTheme.typography.headlineSmall, color = cs.onSecondaryContainer)
                Text("Your history lives in the web app. Open it once and tap Send: programs, workouts and progression all come across.",
                    style = MaterialTheme.typography.bodyMedium, color = cs.onSecondaryContainer)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { Transfer.openTransferPage(ctx) }) { Text("Open the web app") }
                    TextButton(onClick = { vm.prefs.dismissTransfer() }) { Text("I'm new") }
                }
            }
        }

        if (next == null) item(span = full) {
            PiCard(color = cs.primaryContainer, radius = 28.dp, padding = 20.dp) {
                Kicker("Welcome", cs.onPrimaryContainer)
                Text("Let's build your first program", style = MaterialTheme.typography.headlineMedium, color = cs.onPrimaryContainer)
                Text("A few questions about your goal, schedule and gym. The engine builds the program and adjusts every set from what you log.",
                    style = MaterialTheme.typography.bodyMedium, color = cs.onPrimaryContainer)
                Button(onClick = { vm.openSetup() }) { Text("Build my program") }
            }
        }

        h.list("injuries").forEach { inj ->
            item {
                PiCard(color = cs.tertiaryContainer, radius = 24.dp, padding = 16.dp, onClick = { vm.injuryLogOpen = true }) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(PiIcons.Shield, null, tint = cs.onTertiaryContainer)
                        Column(Modifier.weight(1f)) {
                            Text("Protecting: ${inj.s("title")}", style = MaterialTheme.typography.titleSmall, color = cs.onTertiaryContainer)
                            Text(when (inj.s("impact")) { "skip" -> "Related lifts are skipped"; "reduce" -> "Related work is reduced"; else -> "Related lifts are flagged for review" },
                                style = MaterialTheme.typography.bodySmall, color = cs.onTertiaryContainer)
                        }
                    }
                }
            }
        }

        if (!h.b("empty") && vm.prefs.seenWhatsNew < WHATS_NEW_VERSION) item(span = full) { WhatsNewCard(vm) }
        if (h.optInt("needsTrainingMax", 0) > 0) item(span = full) { TrainingMaxPrompt(vm) }
        h.o("reentry")?.takeIf { next != null && h.o("live") == null && it.optLong("lastDate") != vm.prefs.reentrySkippedFor }?.let { r ->
            item(span = full) { WelcomeBackCard(vm, r) }
        }
        if (next != null) item(span = if (wide) StaggeredGridItemSpan.SingleLane else full) {
            val rest = h.o("restDay")
            if (rest != null) RestDayHero(vm, rest) else Hero(vm, next, h)
        }

        if (h.o("deload") != null) item { DeloadCard(vm, h) }
        val insights = h.strings("insights")
        if ("recovery" in insights) item { RecoveryCard(vm, h) }
        if ("strength" in insights) item { StrengthTeaser(vm, h) }
        if ("recap" in insights) item { RecapCard(h) }

        if (h.o("live") == null) item(span = if (wide) StaggeredGridItemSpan.SingleLane else full) {
            PiCard(color = cs.surfaceContainer, radius = 24.dp, padding = 16.dp, onClick = { vm.startQuick() }) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(PiIcons.Flame, null, tint = cs.primary)
                    Column(Modifier.weight(1f)) {
                        Text("Quick workout", style = MaterialTheme.typography.titleSmall)
                        Text("Off-plan session: add lifts as you go", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    }
                    Icon(PiIcons.ChevronRight, null, tint = cs.onSurfaceVariant)
                }
            }
        }

        if (next != null) item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val wk = h.o("week")
                StatTile("Sessions", "${wk.i("sessions")}", "/ ${wk.i("planned")}", Modifier.weight(1f))
                StatTile("Volume", com.pursuitiron.app.ui.common.fmt1(wk.i("volume") / 1000.0), com.pursuitiron.app.ui.common.volUnit(h.s("unit", "kg")), Modifier.weight(1f))
                StatTile("Readiness", "${h.o("readiness").i("today", 100)}", "%", Modifier.weight(1f), color = cs.tertiaryContainer, onClick = { readinessOpen = true })
            }
        }

        val recent = h.list("recent")
        if (recent.isNotEmpty()) {
            item(span = full) { SectionTitle("Recent") }
            item(span = full) {
                Column(Modifier.clip(RoundedCornerShape(24.dp)), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    recent.forEach { r -> HistoryRow(r) { historyId = r.s("id") } }
                }
            }
        }
    }

    if (readinessOpen) ModalBottomSheet(onDismissRequest = { readinessOpen = false }) { ReadinessSheet(h) }
    historyId?.let { id -> HistorySheet(vm, id) { historyId = null } }
}

@Composable
private fun Hero(vm: AppViewModel, next: JSONObject, h: JSONObject) {
    val cs = MaterialTheme.colorScheme
    val ready = h.o("readiness").i("today", 100)
    val ex = next.list("exercises")
    val live = h.o("live")
    Surface(color = cs.primaryContainer, contentColor = cs.onPrimaryContainer, shape = RoundedCornerShape(36.dp), modifier = Modifier.fillMaxWidth()) {
        Box {
            Cookie(cs.primary, Modifier.size(180.dp).align(Alignment.TopEnd).offset(x = 50.dp, y = (-40).dp), alpha = 0.16f)
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(color = cs.primary, contentColor = cs.onPrimary, shape = CircleShape) {
                    Text("READINESS $ready% · " + when { ready >= 85 -> "GOOD TO GO"; ready >= 70 -> "TRAIN AS PLANNED"; else -> "SOME SETS TRIMMED" },
                        style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                }
                Text(next.s("dayLabel"), style = MaterialTheme.typography.displaySmall)
                val cyc = h.o("cycle")
                if (cyc != null) {
                    val blocks = cyc.optJSONArray("blocks")
                    Text("${blocks?.optString(cyc.i("blockIdx")) ?: "Block"} block ${cyc.i("blockIdx") + 1} of ${blocks?.length() ?: 1} · " +
                        (if (cyc.b("isDeload")) "deload week" else "week ${cyc.i("weekIndex")} of ${cyc.i("weeksTotal")}") + " · ${cyc.s("name")}", style = MaterialTheme.typography.bodyMedium)
                } else Text("${next.s("programName")} · week ${next.i("weekIndex")} of ${next.i("weeksTotal")}", style = MaterialTheme.typography.bodyMedium)
                Text("${ex.size} exercises · ${ex.sumOf { it.i("sets") }} working sets" + (next.i("minutes").takeIf { it > 0 }?.let { " · about $it min" } ?: ""),
                    style = MaterialTheme.typography.bodyMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ex.map { it.s("part") }.distinct().filter { it.isNotEmpty() }.forEach { p ->
                        Surface(color = cs.surface, contentColor = cs.onSurface, shape = RoundedCornerShape(10.dp)) {
                            Text(p.replace('_', ' ').replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ex.take(6).forEach { e ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(e.s("name"), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${e.i("sets")} × ${e.s("reps")}", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                Button(
                    onClick = { if (live != null) vm.resume() else vm.start() },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = cs.primary, contentColor = cs.onPrimary),
                ) { Text(if (live != null) "Resume workout" else "Start workout", style = MaterialTheme.typography.titleSmall) }
                val alt = h.o("alt")
                if (live == null && alt != null) OutlinedButton(onClick = { vm.startAlt() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Fresher today: ${alt.s("dayLabel")} (${alt.i("score")}%)")
                }
            }
        }
    }
}

@Composable
fun HistoryRow(r: JSONObject, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val date = r.optLong("date")
    PiCard(radius = 0.dp, padding = 14.dp, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(cs.primaryContainer), contentAlignment = Alignment.Center) {
                Text(if (date > 0) SimpleDateFormat("EEE", Locale.getDefault()).format(Date(date)).uppercase() else "—", style = MaterialTheme.typography.labelMedium, color = cs.onPrimaryContainer)
            }
            Column(Modifier.weight(1f)) {
                Text(r.s("dayLabel"), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(listOfNotNull(
                    if (date > 0) SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(date)) else null,
                    r.i("setsDone").takeIf { it > 0 }?.let { "$it sets" },
                    r.i("volume").takeIf { it > 0 }?.let { "${com.pursuitiron.app.ui.common.fmt1(it / 1000.0)} ${com.pursuitiron.app.ui.common.volUnit(r.s("unit", "kg"))}" },
                    r.i("durationMin").takeIf { it > 0 }?.let { "$it min" },
                ).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
            Icon(PiIcons.ChevronRight, null, tint = cs.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun ReadinessSheet(h: JSONObject) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Muscle readiness", style = MaterialTheme.typography.headlineSmall)
        Text("Estimated from when each muscle was last trained and how hard. Under 65% is hatched in the web app; here it's marked \"needs rest\".",
            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        h.o("readiness").list("parts").sortedBy { it.i("readiness") }.forEach { p ->
            val v = p.i("readiness")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(p.s("label"), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(110.dp))
                ProgressBar(v / 100f, Modifier.weight(1f), height = 12.dp, color = if (v < 65) cs.tertiary else cs.primary)
                Text("$v%" + if (v < 65) " · needs rest" else "", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(96.dp))
            }
        }
    }
}

@Composable
fun HistorySheet(vm: AppViewModel, id: String, onClose: () -> Unit) {
    var detail by remember(id) { mutableStateOf<JSONObject?>(null) }
    var editing by remember(id) { mutableStateOf(false) }
    if (editing) { HistoryEditSheet(vm, id) { editing = false; onClose() }; return }
    androidx.compose.runtime.LaunchedEffect(id) { detail = vm.historyDetail(id) }
    val cs = MaterialTheme.colorScheme
    ModalBottomSheet(onDismissRequest = onClose) {
        val d = detail
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (d == null) { Text("Loading…"); return@Column }
            Kicker(d.s("programName"))
            Text(d.s("dayLabel"), style = MaterialTheme.typography.headlineSmall)
            Text(listOfNotNull(d.i("setsDone").takeIf { it > 0 }?.let { "$it sets" }, d.i("durationMin").takeIf { it > 0 }?.let { "$it min" },
                d.i("volume").takeIf { it > 0 }?.let { "${com.pursuitiron.app.ui.common.fmt1(it / 1000.0)} ${com.pursuitiron.app.ui.common.volUnit(d.s("unit", "kg"))}" }).joinToString(" · "), color = cs.onSurfaceVariant)
            if (d.s("note").isNotBlank()) PiCard(color = cs.surfaceContainerHigh, radius = 18.dp, padding = 12.dp) { Text(d.s("note")) }
            d.list("lifts").forEach { l ->
                PiCard(color = cs.surfaceContainerHigh, radius = 18.dp, padding = 12.dp) {
                    Text(l.s("name"), style = MaterialTheme.typography.titleSmall)
                    Text(l.list("sets").filter { !it.b("warm") }.joinToString("   ") { s -> "${s.s("w")}×${s.s("r")}" + if (s.b("sub")) "*" else "" },
                        style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (vm.session?.b("active") != true) Button(onClick = { onClose(); vm.repeatWorkout(id) }, modifier = Modifier.fillMaxWidth()) { Text("Repeat this workout") }
            val shareCtx = LocalContext.current
            val scope = androidx.compose.runtime.rememberCoroutineScope()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { editing = true }, modifier = Modifier.weight(1f)) { Text("Correct log") }
                OutlinedButton(onClick = { scope.launch { vm.shareCard(id)?.let { c -> runCatching { shareWorkoutImage(shareCtx, c, cs) }.onFailure { vm.toast("Couldn't share the image") } } } }, modifier = Modifier.weight(1f)) { Text("Share image") }
            }
            TextButton(onClick = { vm.deleteHistory(id); onClose() }) {
                Icon(PiIcons.Trash, null, modifier = Modifier.size(18.dp)); Text("  Delete this workout")
            }
        }
    }
}

