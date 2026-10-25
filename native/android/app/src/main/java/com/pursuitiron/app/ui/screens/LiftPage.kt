@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.pursuitiron.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pursuitiron.app.AppViewModel
import com.pursuitiron.app.ui.common.Badge
import com.pursuitiron.app.ui.common.Kicker
import com.pursuitiron.app.ui.common.MorphChip
import com.pursuitiron.app.ui.common.PiCard
import com.pursuitiron.app.ui.common.PiIcons
import com.pursuitiron.app.ui.common.ProgressBar
import com.pursuitiron.app.ui.common.Segmented
import com.pursuitiron.app.ui.common.b
import com.pursuitiron.app.ui.common.d
import com.pursuitiron.app.ui.common.fmtNum
import com.pursuitiron.app.ui.common.i
import com.pursuitiron.app.ui.common.list
import com.pursuitiron.app.ui.common.o
import com.pursuitiron.app.ui.common.s
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/* The design's lift page (web ExerciseCharts + records): Charts with six metrics and six ranges, every
   session in History, and Records with the goal and rep maxes. Numbers come from core.liftDetail. */

private data class Metric(val id: String, val label: String, val reps: Int, val vol: Boolean, val title: String, val blurb: String)
private val METRICS = listOf(
    Metric("e1rm", "1-RM", 1, false, "Estimated 1-rep max", "Your best set each session, converted to a one-rep max — the cleanest single measure of strength."),
    Metric("e3rm", "3-RM", 3, false, "Estimated 3-rep max", "What your best set implies you could hold for three reps. An estimate, not a set you performed."),
    Metric("e10rm", "10-RM", 10, false, "Estimated 10-rep max", "What your best set implies you could hold for ten reps — the load your hypertrophy work sits near."),
    Metric("top", "Heaviest weight", 0, false, "Heaviest weight", "The heaviest load you actually put in your hands each session. No estimate involved."),
    Metric("vol", "Total volume", 0, true, "Total volume", "Weight × reps across every working set of the session — how much work the lift did."),
    Metric("volset", "Volume per set", 0, true, "Volume per set", "Session volume divided by working sets — whether each set is doing more work, independent of how many you did."),
)
private val WINS = listOf("1W" to 7, "1M" to 30, "3M" to 91, "6M" to 182, "1Y" to 365, "All" to 0)
private val dayFmt = SimpleDateFormat("MMM d", Locale.US)
private fun dS(ms: Long) = dayFmt.format(Date(ms))

@Composable
fun LiftPage(vm: AppViewModel, id: String, x: JSONObject, onGoalSaved: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    var tab by remember(id) { mutableStateOf("charts") }
    Segmented(listOf("charts" to "Charts", "history" to "History", "records" to "Records"), tab, { tab = it }, height = 40.dp)
    when (tab) {
        "charts" -> ChartsTab(x)
        "history" -> HistoryTab(x)
        else -> RecordsTab(vm, id, x, onGoalSaved)
    }
    x.o("level")?.let { l ->
        PiCard(color = cs.surfaceContainerHigh, radius = 20.dp, padding = 14.dp) {
            Text("Strength standard: ${l.s("level")}", style = MaterialTheme.typography.titleSmall)
            ProgressBar(l.d("within").toFloat(), Modifier.fillMaxWidth(), height = 8.dp)
            l.s("next").takeIf { it.isNotBlank() }?.let { nx -> Text(if (l.d("add") > 0.5) "+${fmtNum(l.d("add"))} ${x.s("unit")} e1RM → $nx" else "On the cusp of $nx",
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant) }
        }
    }
}

@Composable
private fun ChartsTab(x: JSONObject) {
    val cs = MaterialTheme.colorScheme
    val u = x.s("unit")
    var mId by remember { mutableStateOf("e1rm") }
    var win by remember { mutableStateOf("All") }
    val m = METRICS.first { it.id == mId }
    val days = WINS.first { it.first == win }.second
    val now = System.currentTimeMillis()
    val rows = x.list("log").filter { days == 0 || it.optLong("date") >= now - days * 86_400_000L }
    val valOf = { r: JSONObject -> when {
        m.vol -> if (m.id == "vol") r.d("vol") else r.d("vol") / r.i("n").coerceAtLeast(1)
        m.reps > 0 -> if (m.reps <= 1) r.d("e1") else r.d("e1") / (1 + m.reps / 30.0)
        else -> r.o("heavy")?.d("w") ?: 0.0
    } }
    val fmt = { v: Double -> if (m.vol) "%,d".format(Locale.US, Math.round(v)) else fmtNum(Math.round(v * 10) / 10.0) }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        METRICS.forEach { mt -> MorphChip(mt.label, mt.id == mId, { mId = mt.id }, height = 36.dp, strong = true) }
    }
    Column {
        Text(m.title, style = MaterialTheme.typography.titleMedium)
        Text(m.blurb, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
    }
    if (rows.isEmpty()) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(cs.surfaceContainerHigh).padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Nothing logged in this range", style = MaterialTheme.typography.titleSmall)
            Text("Widen the range below, or log a session with this lift.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
    } else {
        val vals = rows.map(valOf); val n = vals.size
        val best = vals.max(); val bestIdx = vals.indexOf(best); val delta = vals.last() - vals.first(); val avg = vals.average()
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(fmt(best), style = MaterialTheme.typography.displayMedium)
            Text(u, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
            Spacer(Modifier.weight(1f))
            val up = delta > (if (m.vol) 1.0 else 0.05)
            Surface(color = if (up) cs.primaryContainer else cs.surfaceContainerHigh, contentColor = if (up) cs.onPrimaryContainer else cs.onSurface, shape = CircleShape,
                modifier = Modifier.padding(bottom = 10.dp)) {
                Text(if (n < 2) "1 session" else (if (kotlin.math.abs(delta) < (if (m.vol) 1.0 else 0.05)) "±0" else (if (delta > 0) "+" else "−") + fmt(kotlin.math.abs(delta))) + " in $n sessions",
                    style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Avg" to avg, "Best" to best, "Change" to delta).forEach { (k, v) ->
                Column(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(cs.surfaceContainerHigh).padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Text(k, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                    Text((if (k == "Change") (if (v >= 0) "+" else "−") + fmt(kotlin.math.abs(v)) else fmt(v)) + " $u", style = MaterialTheme.typography.titleSmall, maxLines = 1)
                }
            }
        }
        val lo = vals.min(); val hi = vals.max(); val pad = (hi - lo) * 0.12 + if (m.vol) 20.0 else 1.5
        val mn = lo - pad; val mx = hi + pad
        Canvas(Modifier.fillMaxWidth().height(190.dp).semantics {
            contentDescription = "${x.s("name")} ${m.title.lowercase()} by session: " + rows.zip(vals).joinToString(", ") { (r, v) -> "${dS(r.optLong("date"))} ${fmt(v)}" }
        }) {
            val w = size.width; val h = size.height - 18.dp.toPx(); val left = 30.dp.toPx()
            val X = { k: Int -> if (n == 1) (left + w) / 2 else left + k * (w - left - 8.dp.toPx()) / (n - 1) }
            val Y = { v: Double -> (h - (v - mn) / (mx - mn) * (h - 8.dp.toPx())).toFloat() }
            listOf(hi, (mn + mx) / 2, lo).forEach { g -> drawLine(cs.outlineVariant, Offset(left, Y(g)), Offset(w, Y(g)), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 12f))) }
            if (n > 1) {
                val line = Path(); vals.forEachIndexed { k, v -> if (k == 0) line.moveTo(X(k), Y(v)) else line.lineTo(X(k), Y(v)) }
                val area = Path().apply { addPath(line); lineTo(X(n - 1), h); lineTo(X(0), h); close() }
                drawPath(area, cs.primary.copy(alpha = 0.12f))
                drawPath(line, cs.primary, style = Stroke(3.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            vals.forEachIndexed { k, v ->
                val r = if (k == bestIdx) (if (n > 10) 5.5f else 7f) else (if (n > 10) 3f else 4.5f)
                drawCircle(cs.surfaceContainerLow, (r + 2).dp.toPx(), Offset(X(k), Y(v)))
                drawCircle(if (k == bestIdx) cs.primary else cs.onSurfaceVariant, r.dp.toPx(), Offset(X(k), Y(v)))
            }
        }
        Text("${dS(rows.first().optLong("date"))} – ${dS(rows.last().optLong("date"))} · best on ${dS(rows[bestIdx].optLong("date"))}",
            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
    }
    Row(Modifier.fillMaxWidth().clip(CircleShape).background(cs.surfaceContainerHigh).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        WINS.forEach { (k, dd) ->
            val on = k == win
            Box(Modifier.weight(1f).height(36.dp).clip(CircleShape).background(if (on) cs.secondaryContainer else Color.Transparent)
                .clickable { win = k }.semantics { role = Role.Button; contentDescription = if (dd == 0) "All time" else "Last " + when (k) { "1W" -> "week"; "1M" -> "month"; "1Y" -> "year"; else -> k.replace("M", " months") } },
                contentAlignment = Alignment.Center) {
                Text(k, style = MaterialTheme.typography.labelLarge, color = if (on) cs.onSecondaryContainer else cs.onSurfaceVariant)
            }
        }
    }
    if (!m.vol && rows.isNotEmpty()) {
        val bestE1 = rows.maxOf { it.d("e1") }
        Kicker("What your best 1RM in this range predicts")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(1, 3, 5, 8, 10).forEachIndexed { k, r ->
                val v = if (r == 1) bestE1 else bestE1 / (1 + r / 30.0)
                Column(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(if (k == 0) cs.primaryContainer else cs.surfaceContainerHigh).padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    val fg = if (k == 0) cs.onPrimaryContainer else cs.onSurface
                    Text(if (r == 1) "1 rep" else "$r reps", style = MaterialTheme.typography.labelSmall, color = fg)
                    Text(fmtNum(Math.round(v / 2.5) * 2.5), style = MaterialTheme.typography.titleSmall, color = fg)
                }
            }
        }
    }
}

@Composable
private fun HistoryTab(x: JSONObject) {
    val cs = MaterialTheme.colorScheme
    val u = x.s("unit")
    val log = x.list("log")
    if (log.isEmpty()) { Text("Log this lift in a session and every session shows up here.", color = cs.onSurfaceVariant); return }
    Text("${log.size} session${if (log.size == 1) "" else "s"} · since ${dS(log.first().optLong("date"))}", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
    var prBefore = 0.0
    val prs = log.mapIndexed { k, r -> val pr = k > 0 && r.d("e1") > prBefore + 0.05; prBefore = maxOf(prBefore, r.d("e1")); pr }
    log.indices.reversed().forEach { k ->
        val r = log[k]
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(cs.surfaceContainerHigh).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(dS(r.optLong("date")), style = MaterialTheme.typography.titleSmall)
                if (prs[k]) Badge("PR", cs.primaryContainer, cs.onPrimaryContainer)
                Spacer(Modifier.weight(1f))
                Text("e1RM ${fmtNum(r.d("e1"))} $u", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                r.list("sets").forEachIndexed { i, st ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${i + 1} ", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                        Text((if (st.d("w") > 0) fmtNum(st.d("w")) else "BW") + " × " + st.s("r").ifBlank { "—" }, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordsTab(vm: AppViewModel, id: String, x: JSONObject, onGoalSaved: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val u = x.s("unit")
    val rec = x.o("records")
    if (rec == null) { Text("Records appear after you log this lift.", color = cs.onSurfaceVariant); return }
    val hv = rec.o("heaviest"); val be = rec.o("bestE1"); val tv = rec.o("topVolume")
    Column(Modifier.clip(RoundedCornerShape(20.dp)), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        listOfNotNull(
            hv?.let { Triple("Heaviest weight", "${fmtNum(it.d("w"))} $u", "× ${it.s("r")} · ${dS(it.optLong("date"))}") },
            be?.let { Triple("Best estimated 1RM", "${fmtNum(it.d("v"))} $u", dS(it.optLong("date"))) },
            tv?.let { Triple("Top set volume", "${"%,d".format(Locale.US, it.optLong("v"))} $u", "${fmtNum(it.d("w"))} × ${it.s("r")} · ${dS(it.optLong("date"))}") },
        ).forEach { (k, v, d) ->
            Row(Modifier.fillMaxWidth().background(cs.surfaceContainerHigh).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(k, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, modifier = Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Text(v, style = MaterialTheme.typography.titleSmall)
                    Text(d, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                }
            }
        }
    }
    // The goal, against the heaviest weight lifted (as on the web).
    val bestW = hv?.d("w") ?: 0.0
    val goal = x.d("goal").takeIf { it > 0 }
    val step = if (u == "lb") 5.0 else 2.5
    var edit by remember(id) { mutableStateOf<Double?>(null) }
    val ge = edit
    if (ge == null) {
        if (goal != null) {
            val pct = (bestW / goal * 100).coerceIn(0.0, 100.0).toInt(); val reached = bestW >= goal
            Surface(onClick = { edit = goal }, color = cs.surfaceContainerLow, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, if (reached) cs.primary else cs.outlineVariant),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Edit goal" }) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row { Text("Goal" + if (reached) " · reached!" else "", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        Text("${fmtNum(bestW)} / ${fmtNum(goal)} $u", style = MaterialTheme.typography.titleSmall) }
                    ProgressBar(pct / 100f, Modifier.fillMaxWidth(), height = 8.dp)
                    Text(if (reached) "Tap to set a new target" else "$pct% there · tap to edit", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
            }
        } else androidx.compose.material3.OutlinedButton(onClick = { edit = Math.round(bestW * 1.05 / step) * step }, modifier = Modifier.fillMaxWidth().height(48.dp)) {
            Icon(PiIcons.Plus, null, modifier = Modifier.size(18.dp)); Text("  Set a goal weight")
        }
    } else Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(cs.surfaceContainerHigh).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Kicker("Goal weight")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HoldStep(PiIcons.Minus, "Lower goal by ${fmtNum(step)} $u", 48.dp, false) { edit = ((edit ?: 0.0) - step).coerceAtLeast(step) }
            Text("${fmtNum(ge)} $u", style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            HoldStep(PiIcons.Plus, "Raise goal by ${fmtNum(step)} $u", 48.dp, false) { edit = (edit ?: 0.0) + step }
        }
        Text(if (ge > bestW) "+${fmtNum(Math.round((ge - bestW) * 10) / 10.0)} $u over your heaviest (${fmtNum(bestW)})" else "At or under your heaviest — you've already lifted this",
            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (goal != null) TextButton(onClick = { vm.setGoal(id, null); vm.toast("Goal removed · ${x.s("name")}"); edit = null; onGoalSaved() }) { Text("Remove", color = cs.tertiary) }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { edit = null }) { Text("Cancel") }
            Button(onClick = { vm.setGoal(id, ge); vm.toast("Goal set · ${fmtNum(ge)} $u on ${x.s("name")}"); edit = null; onGoalSaved() }) { Text("Save") }
        }
    }
    val rm = x.list("repMax")
    if (rm.isNotEmpty()) {
        Kicker("Rep maxes — best weight at each rep count")
        Column(Modifier.clip(RoundedCornerShape(20.dp)), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            rm.forEach { r ->
                Row(Modifier.fillMaxWidth().background(cs.surfaceContainerHigh).padding(horizontal = 16.dp, vertical = 10.dp).alpha(if (r.b("beaten")) 0.55f else 1f),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(if (r.i("reps") == 1) "1 rep" else "${r.i("reps")} reps", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(72.dp))
                    Text("${fmtNum(r.d("w"))} $u", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Text(dS(r.optLong("date")), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                }
            }
        }
        if (rm.any { it.b("beaten") }) Text("Dimmed rows are beaten by a heavier set at more reps.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
    }
    Box(Modifier.heightIn(min = 4.dp))
}
