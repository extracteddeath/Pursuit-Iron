@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.pursuitiron.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pursuitiron.app.AppViewModel
import com.pursuitiron.app.ui.common.Kicker
import com.pursuitiron.app.ui.common.PiCard
import com.pursuitiron.app.ui.common.PiIcons
import com.pursuitiron.app.ui.common.ProgressBar
import com.pursuitiron.app.ui.common.Segmented
import com.pursuitiron.app.ui.common.Sparkline
import com.pursuitiron.app.ui.common.StatTile
import com.pursuitiron.app.ui.common.b
import com.pursuitiron.app.ui.common.d
import com.pursuitiron.app.ui.common.fmtNum
import com.pursuitiron.app.ui.common.has
import com.pursuitiron.app.ui.common.i
import com.pursuitiron.app.ui.common.list
import com.pursuitiron.app.ui.common.o
import com.pursuitiron.app.ui.common.s
import org.json.JSONObject
import java.text.DateFormatSymbols
import java.util.Calendar

/* ------------------------------------------------------------------ level (App.js ProfileView hero) */
@Composable
fun LevelCard(vm: AppViewModel) {
    val p = vm.profile ?: return
    val lv = p.o("level") ?: return
    val cs = MaterialTheme.colorScheme
    PiCard(radius = 28.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(64.dp).clip(RoundedCornerShape(20.dp)).background(cs.primaryContainer), contentAlignment = Alignment.Center) {
                Text(lv.i("level").toString(), style = MaterialTheme.typography.headlineMedium, color = cs.onPrimaryContainer)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Kicker("Level ${lv.i("level")}")
                Text(lv.s("title").ifBlank { "Lifter" }, style = MaterialTheme.typography.titleLarge)
                val span = lv.i("span").coerceAtLeast(1)
                ProgressBar(lv.i("into").toFloat() / span, Modifier.semantics { contentDescription = "${lv.i("into")} of $span XP to level ${lv.i("level") + 1}" })
                Text("${(span - lv.i("into")).coerceAtLeast(0)} XP to level ${lv.i("level") + 1}", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
        }
        val sm = p.o("summary")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("Workouts", sm.i("workouts").toString(), modifier = Modifier.weight(1f), color = cs.surfaceContainer)
            StatTile("Best streak", sm.i("bestStreak").toString(), if (sm.i("bestStreak") == 1) "week" else "weeks", Modifier.weight(1f), color = cs.surfaceContainer)
            val v = sm.i("volume").toDouble()
            StatTile("Volume", if (v >= 1_000_000) fmtNum(Math.round(v / 100_000) / 10.0) + "M" else if (v >= 1000) "${Math.round(v / 1000)}k" else v.toInt().toString(), p.s("unit"), Modifier.weight(1f), color = cs.surfaceContainer)
        }
    }
}

/* ------------------------------------------------------------------ strength score (App.js StrengthSnapshotCard / ProfileView strength) */
@Composable
fun StrengthScoreCard(vm: AppViewModel, onSetBodyweight: () -> Unit) {
    val p = vm.profile ?: return
    val sc = p.o("score")
    val cs = MaterialTheme.colorScheme
    if (sc == null) {
        if (!p.b("scoreLocked")) return
        PiCard(radius = 28.dp, onClick = onSetBodyweight) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(PiIcons.Trophy, null, tint = cs.primary, modifier = Modifier.size(18.dp))
                Text("Strength Score", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Icon(PiIcons.ChevronRight, null, modifier = Modifier.size(18.dp))
            }
            Text("Add your bodyweight to score your lifts against strength standards.", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
        }
        return
    }
    PiCard(radius = 28.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(PiIcons.Trophy, null, tint = cs.primary, modifier = Modifier.size(18.dp))
            Text("Strength Score", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).semantics { heading() })
            if (sc.b("estimated")) Text("Estimated", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(sc.i("overall").toString(), style = MaterialTheme.typography.displayMedium, color = cs.primary,
                modifier = Modifier.semantics { contentDescription = "Strength score ${sc.i("overall")} of 100, ${sc.s("level")}" })
            Column(Modifier.padding(bottom = 8.dp)) {
                Text(sc.s("level"), style = MaterialTheme.typography.titleMedium)
                val levels = p.optJSONArray("levels")
                val idx = sc.i("levelIdx")
                if (levels != null && idx + 1 < levels.length()) Text("Next: ${levels.optString(idx + 1)}", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
        }
        val tr = sc.optJSONArray("trend")
        if (tr != null && tr.length() >= 2) {
            val vals = (0 until tr.length()).map { tr.optDouble(it) }
            Sparkline(vals, Modifier.fillMaxWidth().height(72.dp), description = "Strength score over ${vals.size} sessions, from ${vals.first().toInt()} to ${vals.last().toInt()}")
        }
        sc.list("subs").forEach { sub ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(sub.s("label"), style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(64.dp))
                ProgressBar(sub.i("score") / 100f, Modifier.weight(1f), height = 8.dp)
                Text(sub.i("score").toString(), style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(30.dp), textAlign = TextAlign.End)
            }
            sub.o("lift")?.let { l -> Text("${l.s("name")}${if (l.b("est")) " · estimated" else ""}${l.s("level").let { if (it.isNotBlank()) " · $it" else "" }}",
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, modifier = Modifier.padding(start = 74.dp)) }
        }
        sc.o("next")?.let { n ->
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(cs.secondaryContainer).padding(12.dp)) {
                Kicker("Next milestone", color = cs.onSecondaryContainer)
                Text("${n.s("name")} ${n.i("target")} ${n.s("unit")} for ${n.s("level")} ${n.s("pattern").lowercase()}", style = MaterialTheme.typography.titleSmall, color = cs.onSecondaryContainer)
                Text("Now ${n.i("current")}${if (n.has("est") && n.b("est")) " (estimated)" else ""}", style = MaterialTheme.typography.bodySmall, color = cs.onSecondaryContainer)
            }
        }
    }
}

/* ------------------------------------------------------------------ strength club (App.js ProfileStrengthClub) */
@Composable
fun ClubCard(vm: AppViewModel) {
    val p = vm.profile ?: return
    var mode by remember { mutableStateOf("lifted") }
    val c = (if (mode == "lifted") p.o("clubLifted") else p.o("clubE1rm")) ?: return
    val cs = MaterialTheme.colorScheme
    val u = p.s("unit")
    PiCard(radius = 28.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val earned = c.b("inThousand")
            Text(when { earned && mode == "lifted" -> c.s("current").ifBlank { "1,000 lb Club" }; earned -> "Projected ${c.s("current").ifBlank { "1,000 lb Club" }}"; else -> "Road to 1,000 lb Club" },
                style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        }
        Segmented(listOf("lifted" to "Lifted", "e1rm" to "Est. 1RM"), mode, { mode = it }, height = 36.dp)
        Text("${fmtNum(c.d("total"))} $u total", style = MaterialTheme.typography.headlineSmall)
        Text(when {
            c.b("maxedOut") -> "Every club tier cleared"
            c.b("inThousand") && mode == "lifted" -> "${fmtNum(c.d("remaining"))} $u to ${c.s("next")}"
            c.b("inThousand") -> "Projection cleared 1,000 lb. Switch to Lifted for traditional club status."
            !c.b("allLogged") -> "Log squat, bench and deadlift to complete your total."
            else -> "${fmtNum(c.d("thousandRemaining"))} $u to the 1,000 lb Club."
        }, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        if (!c.b("inThousand") && c.b("allLogged")) ProgressBar(c.i("thousandPct") / 100f, height = 8.dp)
        c.list("rows").forEach { r ->
            Row {
                Text(r.s("label"), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), color = if (r.b("has")) cs.onSurface else cs.onSurfaceVariant)
                Text(if (r.b("has")) "${fmtNum(r.d("value"))} $u" else "Not logged", style = MaterialTheme.typography.titleSmall, color = if (r.b("has")) cs.onSurface else cs.onSurfaceVariant)
            }
        }
    }
}

/* ------------------------------------------------------------------ achievements (App.js ProfileView achievements) */
@Composable
fun AchievementsCard(vm: AppViewModel) {
    val p = vm.profile ?: return
    val all = p.list("achievements")
    if (all.isEmpty()) return
    val cs = MaterialTheme.colorScheme
    var detail by remember { mutableStateOf<JSONObject?>(null) }
    PiCard(radius = 28.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Achievements", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text("${all.count { it.b("done") }}/${all.size}", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
        }
        p.o("nextAchievement")?.let { n -> Text("Closest: ${n.s("label")} · ${(n.d("progress") * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant) }
        all.groupBy { it.s("cat").ifBlank { "Other" } }.forEach { (cat, items) ->
            Row(Modifier.padding(top = 6.dp)) {
                Kicker(cat, modifier = Modifier.weight(1f))
                Text("${items.count { it.b("done") }}/${items.size}", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), maxItemsInEachRow = 4) {
                items.forEach { m -> AchievementTile(m, Modifier.weight(1f)) { detail = m } }
                repeat((4 - items.size % 4) % 4) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
    detail?.let { m ->
        AlertDialog(onDismissRequest = { detail = null }, confirmButton = { TextButton(onClick = { detail = null }) { Text("Close") } },
            title = { Text(m.s("label")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(m.s("desc"))
                    if (!m.b("done")) {
                        ProgressBar(m.d("progress").toFloat())
                        if (m.has("value") && m.has("target")) Text("${fmtNum(m.d("value"))} of ${fmtNum(m.d("target"))}", style = MaterialTheme.typography.bodySmall)
                    } else Text("Earned", color = MaterialTheme.colorScheme.primary)
                }
            })
    }
}

@Composable
private fun AchievementTile(m: JSONObject, modifier: Modifier, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val done = m.b("done")
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(if (done) cs.primaryContainer else cs.surfaceContainer).clickable(onClick = onClick)
        .semantics(mergeDescendants = true) { role = Role.Button; contentDescription = m.s("label") + if (done) ", earned" else ", ${(m.d("progress") * 100).toInt()} percent" }
        .padding(vertical = 10.dp, horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val ring = cs.primary; val track = cs.surfaceContainerHighest
        Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(36.dp)) {
                val w = 4.dp.toPx()
                drawArc(track, 0f, 360f, false, style = Stroke(w))
                drawArc(ring, -90f, 360f * (if (done) 1f else m.d("progress").toFloat()), false, style = Stroke(w, cap = StrokeCap.Round))
            }
            if (done) Icon(PiIcons.Check, null, tint = cs.onPrimaryContainer, modifier = Modifier.size(16.dp))
        }
        Text(m.s("label"), style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            color = if (done) cs.onPrimaryContainer else cs.onSurfaceVariant)
    }
}

/* ------------------------------------------------------------------ profile fields (App.js Settings → Profile) */
@Composable
fun ProfileFieldsCard(vm: AppViewModel) {
    val p = vm.profile ?: return
    val cs = MaterialTheme.colorScheme
    var birth by remember(p.s("birth")) { mutableStateOf(p.s("birth")) }
    PiCard(radius = 28.dp) {
        Text("Profile", style = MaterialTheme.typography.titleMedium)
        Text("Sex and age adjust the strength standards. They stay on this phone.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        Kicker("Sex")
        Segmented(listOf("male" to "Male", "female" to "Female"), p.s("sex", "male"), { vm.setProfile(sex = it) })
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            androidx.compose.material3.OutlinedTextField(birth, { birth = it.filter { c -> c.isDigit() || c == '-' }.take(10) }, Modifier.weight(1f), singleLine = true,
                label = { Text("Birth date (YYYY-MM-DD)") },
                supportingText = { if (p.has("age") && p.s("age").isNotBlank()) Text("Age ${p.s("age")}") },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
            androidx.compose.material3.Button(onClick = { vm.setProfile(birth = birth.trim()) }, modifier = Modifier.height(56.dp)) { Text("Save") }
        }
    }
}

/* ------------------------------------------------------------------ training calendar (App.js TrainingHeatmap) */
@Composable
fun CalendarCard(vm: AppViewModel, onOpen: (String) -> Unit) {
    val today = remember { Calendar.getInstance() }
    var y by remember { mutableIntStateOf(today.get(Calendar.YEAR)) }
    var m by remember { mutableIntStateOf(today.get(Calendar.MONTH)) }
    var cal by remember { mutableStateOf<JSONObject?>(null) }
    var sel by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(y, m, vm.history.size) { cal = vm.calendar(y, m); sel = null }
    val cs = MaterialTheme.colorScheme
    val c = cal
    val atNow = y == today.get(Calendar.YEAR) && m == today.get(Calendar.MONTH)
    val earliest = c?.optLong("earliest")?.takeIf { it > 0 }?.let { Calendar.getInstance().apply { timeInMillis = it } }
    val atStart = earliest == null || (y < earliest.get(Calendar.YEAR) || (y == earliest.get(Calendar.YEAR) && m <= earliest.get(Calendar.MONTH)))
    fun shift(d: Int) { val k = Calendar.getInstance().apply { set(y, m, 1); add(Calendar.MONTH, d) }; y = k.get(Calendar.YEAR); m = k.get(Calendar.MONTH) }
    val monthName = DateFormatSymbols.getInstance().months[m]
    PiCard(radius = 28.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(PiIcons.Calendar, null, tint = cs.primary, modifier = Modifier.size(18.dp))
            Text("  Training calendar", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { shift(-1) }, enabled = !atStart) { Icon(PiIcons.ChevronLeft, "Previous month") }
            Text("$monthName $y", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            IconButton(onClick = { shift(1) }, enabled = !atNow) { Icon(PiIcons.ChevronRight, "Next month") }
        }
        if (c == null) return@PiCard
        val days = c.list("days").associateBy { it.i("day") }
        val first = c.i("firstDow"); val n = c.i("daysInMonth")
        val dow = DateFormatSymbols.getInstance().shortWeekdays
        Row { (1..7).forEach { d -> Text(dow[d].take(2), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.weight(1f)) } }
        val cells = first + n
        val rows = (cells + 6) / 7
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for (r in 0 until rows) Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (k in 0 until 7) {
                    val day = r * 7 + k - first + 1
                    if (day < 1 || day > n) { Spacer(Modifier.weight(1f).aspectRatio(1f)); continue }
                    val hit = days[day]
                    val count = hit?.list("sessions")?.size ?: 0
                    val isToday = atNow && day == today.get(Calendar.DAY_OF_MONTH)
                    val selected = sel == day
                    val bg = when { selected -> cs.primary; count > 0 -> cs.primaryContainer; else -> cs.surfaceContainer }
                    val fg = when { selected -> cs.onPrimary; count > 0 -> cs.onPrimaryContainer; else -> cs.onSurfaceVariant }
                    Box(Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(10.dp)).background(bg)
                        .then(if (isToday && !selected) Modifier.border(1.5.dp, cs.primary, RoundedCornerShape(10.dp)) else Modifier)
                        .then(if (count > 0) Modifier.clickable { sel = if (selected) null else day } else Modifier)
                        .semantics(mergeDescendants = true) {
                            contentDescription = "$monthName $day: ${if (count == 0) "no workouts" else if (count == 1) "1 workout" else "$count workouts"}"
                            if (count > 0) { role = Role.Button; this.selected = selected }
                        }, contentAlignment = Alignment.Center) {
                        Text(day.toString(), style = if (count > 0) MaterialTheme.typography.labelLarge else MaterialTheme.typography.labelMedium, color = fg)
                        if (count > 0 && !selected) Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp).size(4.dp).clip(CircleShape).background(cs.primary))
                    }
                }
            }
        }
        sel?.let { d -> days[d]?.let { hit ->
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(cs.surfaceContainer).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("$monthName $d", style = MaterialTheme.typography.titleSmall)
                hit.list("sessions").forEach { sx ->
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onOpen(sx.s("id")) }.padding(4.dp)) {
                        Row { Text(sx.s("label"), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f)); Text(sx.s("summary"), style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant) }
                        sx.list("lifts").forEach { l ->
                            Row { Text(l.s("name"), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, modifier = Modifier.weight(0.45f)); Text(l.s("sets"), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(0.55f)) }
                        }
                    }
                }
            }
        } }
        Row {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) { Text(c.i("trainingDays").toString(), style = MaterialTheme.typography.titleMedium, color = cs.primary); Text("Training days", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant) }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) { Text(c.i("workouts").toString(), style = MaterialTheme.typography.titleMedium, color = cs.primary); Text("Workouts", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant) }
        }
    }
}

