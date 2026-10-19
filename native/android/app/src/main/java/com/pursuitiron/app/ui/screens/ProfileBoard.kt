package com.pursuitiron.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pursuitiron.app.AppViewModel
import com.pursuitiron.app.ui.common.Kicker
import com.pursuitiron.app.ui.common.PiCard
import com.pursuitiron.app.ui.common.ProgressBar
import com.pursuitiron.app.ui.common.Segmented
import com.pursuitiron.app.ui.common.StatTile
import com.pursuitiron.app.ui.common.d
import com.pursuitiron.app.ui.common.fmt1
import com.pursuitiron.app.ui.common.fmtNum
import com.pursuitiron.app.ui.common.i
import com.pursuitiron.app.ui.common.list
import com.pursuitiron.app.ui.common.o
import com.pursuitiron.app.ui.common.s
import com.pursuitiron.app.ui.common.volUnit
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/* The design's You → Profile pieces, from core.profileBoard (logged history only). */

@Composable
fun LifetimeTiles(lt: JSONObject, unit: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatTile("Sessions", "${lt.i("sessions")}", modifier = Modifier.weight(1f))
        StatTile("Lifted", fmt1(lt.d("lifted") / 1000.0), volUnit(unit), Modifier.weight(1f))
        StatTile("Streak", "${lt.i("streakWeeks")}", "wk", Modifier.weight(1f))
        StatTile("PRs", "${lt.i("prs30")}", "30 d", Modifier.weight(1f))
    }
}

/** Big 3 total: the ring is the total against bodyweight; each lift's bar is its multiple of bodyweight. */
@Composable
fun Big3Card(b: JSONObject) {
    val big = b.o("big3") ?: return
    if (big.i("logged") == 0) return
    val cs = MaterialTheme.colorScheme
    val u = b.s("unit", "kg")
    val ratio = big.d("ratio")
    PiCard(color = cs.primaryContainer, radius = 32.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (ratio > 0) Box(Modifier.size(84.dp).semantics(mergeDescendants = true) { contentDescription = "Total is ${fmtNum(ratio)} times bodyweight" }, contentAlignment = Alignment.Center) {
                val ring = cs.primary; val track = cs.onPrimaryContainer.copy(alpha = 0.15f)
                Canvas(Modifier.fillMaxSize()) {
                    val st = 8.dp.toPx()
                    drawArc(track, 0f, 360f, false, style = Stroke(st))
                    drawArc(ring, -90f, 360f * (ratio / 8.0).toFloat().coerceIn(0.03f, 1f), false, style = Stroke(st, cap = StrokeCap.Round))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${fmtNum(ratio)}×", style = MaterialTheme.typography.titleMedium, color = cs.onPrimaryContainer)
                    Text("BW", style = MaterialTheme.typography.labelSmall, color = cs.onPrimaryContainer)
                }
            }
            Column(Modifier.weight(1f)) {
                Kicker("Big 3 total · e1RM", cs.onPrimaryContainer)
                Text("${fmtNum(big.d("total"))} $u", style = MaterialTheme.typography.headlineMedium, color = cs.onPrimaryContainer)
                if (big.i("logged") < 3) Text("${3 - big.i("logged")} of the three not logged yet", style = MaterialTheme.typography.bodySmall, color = cs.onPrimaryContainer)
            }
        }
        big.list("lifts").forEach { l ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(l.s("name"), style = MaterialTheme.typography.titleSmall, color = cs.onPrimaryContainer, modifier = Modifier.width(76.dp))
                val r = l.d("ratio")
                ProgressBar(if (r > 0) (r / 3.0).toFloat().coerceIn(0f, 1f) else if (l.d("e1rm") > 0) 0.05f else 0f, Modifier.weight(1f), height = 8.dp)
                Text(when { r > 0 -> "${fmtNum(r)}×"; l.d("e1rm") > 0 -> "${fmtNum(l.d("e1rm"))} $u"; else -> "—" }, style = MaterialTheme.typography.labelLarge, color = cs.onPrimaryContainer,
                    modifier = Modifier.width(64.dp), textAlign = TextAlign.End)
            }
        }
        if (b.isNull("bodyweight")) Text("Add your bodyweight on Body to see each lift as a multiple of it.", style = MaterialTheme.typography.bodySmall, color = cs.onPrimaryContainer)
    }
}

/** PR board: estimated 1RM or heaviest set, each with when it was set. */
@Composable
fun PrBoardCard(vm: AppViewModel, b: JSONObject) {
    val pb = b.o("prBoard") ?: return
    if (pb.list("e1rm").isEmpty()) return
    val cs = MaterialTheme.colorScheme
    var mode by remember { mutableStateOf("e1rm") }
    var all by remember { mutableStateOf(false) }
    var liftId by remember { mutableStateOf<String?>(null) }
    liftId?.let { id -> LiftDetailSheet(vm, id) { liftId = null } }
    val u = b.s("unit", "kg")
    val rows = pb.list(mode)
    PiCard(radius = 28.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("PR board", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text("${pb.list("e1rm").size} lifts", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
        }
        Segmented(listOf("e1rm" to "e1RM", "heaviest" to "Heaviest"), mode, { mode = it }, height = 40.dp)
        Column(Modifier.clip(RoundedCornerShape(20.dp)), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            (if (all) rows else rows.take(5)).forEachIndexed { k, r ->
                Row(Modifier.fillMaxWidth().background(cs.surface).clickable { liftId = r.s("id") }.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(color = if (k == 0) cs.primary else cs.primaryContainer, contentColor = if (k == 0) cs.onPrimary else cs.onPrimaryContainer, shape = RoundedCornerShape(10.dp), modifier = Modifier.size(30.dp)) {
                        Box(contentAlignment = Alignment.Center) { Text("${k + 1}", style = MaterialTheme.typography.labelLarge) }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(r.s("name"), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val date = r.optLong("date").takeIf { it > 0 }?.let { SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(it)) }
                        Text((if (mode == "e1rm") "Estimated 1RM" else "Heaviest set") + (date?.let { " · $it" } ?: ""), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                    }
                    Text("${fmtNum(r.d("value"))} $u" + (if (mode == "heaviest" && r.i("reps") > 0) " × ${r.i("reps")}" else ""), style = MaterialTheme.typography.titleSmall)
                }
            }
        }
        if (rows.size > 5) TextButton(onClick = { all = !all }) { Text(if (all) "Show fewer" else "Show all ${rows.size}") }
    }
}

/** Muscle balance: working sets per muscle in the last 30 days, and the push : pull ratio. */
@Composable
fun MuscleBalanceCard(b: JSONObject) {
    val bal = b.o("balance") ?: return
    val parts = bal.list("parts"); if (parts.isEmpty()) return
    val cs = MaterialTheme.colorScheme
    val max = parts.maxOf { it.i("sets") }.coerceAtLeast(1)
    PiCard(radius = 28.dp) {
        Text("Muscle balance · 30 days", style = MaterialTheme.typography.titleMedium)
        parts.take(10).forEach { m ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(m.s("label").replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(96.dp), maxLines = 1)
                ProgressBar(m.i("sets") / max.toFloat(), Modifier.weight(1f), height = 8.dp)
                Text("${m.i("sets")}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(32.dp), textAlign = TextAlign.End)
            }
        }
        val ratio = bal.d("ratio")
        if (ratio > 0) Surface(color = cs.tertiaryContainer, contentColor = cs.onTertiaryContainer, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
            Text(
                when {
                    ratio >= 1.4 -> "Push-heavy: ${fmtNum(ratio)} : 1 push to pull (${bal.i("push")} vs ${bal.i("pull")} sets)."
                    ratio <= 0.7 -> "Pull-heavy: ${fmtNum(ratio)} : 1 push to pull (${bal.i("push")} vs ${bal.i("pull")} sets)."
                    else -> "Balanced: ${fmtNum(ratio)} : 1 push to pull (${bal.i("push")} vs ${bal.i("pull")} sets)."
                },
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(14.dp),
            )
        }
    }
}

