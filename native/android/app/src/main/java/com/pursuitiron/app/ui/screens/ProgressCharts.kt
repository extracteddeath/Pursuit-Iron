@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.pursuitiron.app.ui.screens

import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pursuitiron.app.AppViewModel
import com.pursuitiron.app.ui.common.PiCard
import com.pursuitiron.app.ui.common.ProgressBar
import com.pursuitiron.app.ui.common.Segmented
import com.pursuitiron.app.ui.common.b
import com.pursuitiron.app.ui.common.fmt1
import com.pursuitiron.app.ui.common.i
import com.pursuitiron.app.ui.common.list
import com.pursuitiron.app.ui.common.o
import com.pursuitiron.app.ui.common.s
import com.pursuitiron.app.ui.common.strings
import com.pursuitiron.app.ui.common.volUnit
import com.pursuitiron.app.ui.theme.Motion
import org.json.JSONObject

/* The design's Progress charts, all computed by the core from logged history (core.progressCharts). */

@Composable
fun rememberProgressCharts(vm: AppViewModel): JSONObject? {
    var d by remember { mutableStateOf<JSONObject?>(null) }
    LaunchedEffect(vm.progress) { d = vm.progressCharts() }
    return d
}

/** Volume / Sessions for the last eight weeks: the headline is this week against the one before. */
@Composable
fun VolumeChartCard(g: JSONObject) {
    val weeks = g.list("weeks"); if (weeks.size < 2) return
    val cs = MaterialTheme.colorScheme
    var mode by remember { mutableStateOf("volume") }
    val u = g.s("unit", "kg")
    val v = { w: JSONObject -> if (mode == "volume") w.i("volume") / 1000.0 else w.i("sessions").toDouble() }
    val now = weeks.last(); val prev = weeks[weeks.size - 2]
    val max = weeks.maxOf { v(it) }.takeIf { it > 0 } ?: 1.0
    val unitLabel = if (mode == "volume") volUnit(u) else if (now.i("sessions") == 1) "session" else "sessions"
    PiCard(radius = 28.dp) {
        Segmented(listOf("volume" to "Volume", "sessions" to "Sessions"), mode, { mode = it }, height = 40.dp)
        Text((if (mode == "volume") "Volume, ${volUnit(u)}" else "Workouts") + " · ${now.s("label").lowercase().replace("w", "week ")} (this week)", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(fmt1(v(now)) + " " + unitLabel, style = MaterialTheme.typography.displaySmall)
            val dl = v(now) - v(prev)
            if (v(prev) > 0 || v(now) > 0) Surface(color = if (dl >= 0) cs.primaryContainer else cs.tertiaryContainer, shape = CircleShape) {
                Text((if (dl >= 0) "+" else "−") + fmt1(kotlin.math.abs(dl)) + " vs last week", style = MaterialTheme.typography.labelMedium,
                    color = if (dl >= 0) cs.onPrimaryContainer else cs.onTertiaryContainer, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
            }
        }
        Row(Modifier.fillMaxWidth().height(140.dp).semantics(mergeDescendants = true) {
            contentDescription = (if (mode == "volume") "Weekly volume in ${volUnit(u)}: " else "Workouts per week: ") + weeks.joinToString(", ") { "${it.s("label")} ${fmt1(v(it))}" }
        }, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
            weeks.forEach { w ->
                val h by animateDpAsState((8 + 104 * (v(w) / max)).dp, Motion.data(), label = "bar")
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.fillMaxWidth().height(h).clip(RoundedCornerShape(if (w.b("now")) 12.dp else 999.dp)).background(if (w.b("now")) cs.primary else cs.primaryContainer))
                    Text(w.s("label"), style = MaterialTheme.typography.labelSmall, color = if (w.b("now")) cs.onSurface else cs.onSurfaceVariant)
                }
            }
        }
    }
}

/** Working sets of the last four weeks by rep zone or by the effort you reported. */
@Composable
fun IntensityCard(g: JSONObject) {
    val it0 = g.o("intensity") ?: return
    val cs = MaterialTheme.colorScheme
    var mode by remember { mutableStateOf("reps") }
    val zones = it0.list(if (mode == "reps") "reps" else "effort")
    val tot = zones.sumOf { it.i("sets") }
    if (it0.list("reps").sumOf { it.i("sets") } == 0) return
    val colors = if (mode == "reps") listOf(cs.primary, cs.tertiaryContainer, cs.primaryContainer) else listOf(cs.primary, cs.onTertiaryContainer, cs.tertiaryContainer, cs.primaryContainer)
    PiCard(radius = 28.dp) {
        Text("Intensity distribution", style = MaterialTheme.typography.titleMedium)
        Text("Working sets by ${if (mode == "reps") "rep range" else "reported effort"} · last 4 weeks", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        Segmented(listOf("reps" to "By reps", "effort" to "By effort"), mode, { mode = it }, height = 40.dp)
        if (tot == 0) {
            Text("Rate how many reps you had left after a set and this fills in.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            return@PiCard
        }
        Row(Modifier.fillMaxWidth().height(16.dp).clip(CircleShape).semantics { contentDescription = zones.joinToString(", ") { "${it.s("label")} ${it.i("sets") * 100 / tot} percent" } },
            horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            zones.forEachIndexed { k, z -> if (z.i("sets") > 0) Box(Modifier.weight(z.i("sets").toFloat()).fillMaxHeight().background(colors[k % colors.size])) }
        }
        zones.forEachIndexed { k, z ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(colors[k % colors.size]))
                Text(z.s("label"), style = MaterialTheme.typography.titleSmall)
                Text("· ${z.s("sub")}", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, modifier = Modifier.weight(1f))
                Text("${z.i("sets")} sets", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                Text("${z.i("sets") * 100 / tot}%", style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(44.dp), textAlign = TextAlign.End)
            }
        }
        val n = it0.i("unrated")
        Text(
            if (mode == "reps") "Anything from about 5 to 30 reps builds muscle when sets end close to failure, so this is a preference, not a rule."
            else "Only sets you rated count here" + (if (n > 0) " · $n set${if (n == 1) "" else "s"} without a rating." else "."),
            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
        )
    }
}

/** How each muscle felt in your last sessions, beside how recovered it is now. */
@Composable
fun FeedbackCard(g: JSONObject) {
    val rows = g.list("feedback"); if (rows.isEmpty()) return
    val cs = MaterialTheme.colorScheme
    var all by remember { mutableStateOf(false) }
    PiCard(radius = 28.dp) {
        Text("Recovery & feedback", style = MaterialTheme.typography.titleMedium)
        Text("Freshness now, and how each muscle felt your last three sessions", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        (if (all) rows else rows.take(3)).forEach { m ->
            Surface(color = cs.surface, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(m.s("label").replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.titleSmall, modifier = Modifier.width(96.dp))
                        if (!m.isNull("readiness")) {
                            val r = m.i("readiness")
                            ProgressBar(r / 100f, Modifier.weight(1f), height = 8.dp, color = if (r < 65) cs.tertiary else cs.primary)
                            Text("$r%", style = MaterialTheme.typography.labelLarge)
                        } else Box(Modifier.weight(1f))
                    }
                    // The design's three answer tiles, oldest first, equal width.
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        m.strings("answers").forEach { a ->
                            val (bg, fg) = when (a) { "About right" -> cs.primary to cs.onPrimary; "Too much" -> cs.tertiaryContainer to cs.onTertiaryContainer; else -> cs.secondaryContainer to cs.onSecondaryContainer }
                            Box(Modifier.weight(1f).height(26.dp).clip(RoundedCornerShape(8.dp)).background(bg), contentAlignment = Alignment.Center) {
                                Text(a, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.ExtraBold), color = fg, maxLines = 1)
                            }
                        }
                    }
                    if (m.s("note").isNotBlank()) Text(m.s("note"), style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp), color = cs.onSurfaceVariant)
                }
            }
        }
        if (rows.size > 3) TextButton(onClick = { all = !all }) { Text(if (all) "Show fewer" else "Show all muscles") }
    }
}

