@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.pursuitiron.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pursuitiron.app.AppViewModel
import com.pursuitiron.app.ui.common.Cookie
import com.pursuitiron.app.ui.common.PiIcons
import com.pursuitiron.app.ui.common.Segmented
import com.pursuitiron.app.ui.common.b
import com.pursuitiron.app.ui.common.d
import com.pursuitiron.app.ui.common.fmt1
import com.pursuitiron.app.ui.common.fmtNum
import com.pursuitiron.app.ui.common.i
import com.pursuitiron.app.ui.common.list
import com.pursuitiron.app.ui.common.o
import com.pursuitiron.app.ui.common.s
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/* The design's You → Profile (Main.dc.html PROFILE): the spinning cookie avatar with your name, the
   program you're running and your level, four lifetime stats, then the sections in your order. */

private val ink = { v: Double -> fmtNum(v) }

@Composable
fun YouHeader(vm: AppViewModel, board: JSONObject?, onEdit: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val p = vm.profile
    val name = p?.s("name").orEmpty().trim().ifBlank { "Athlete" }
    Column(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val spin by rememberInfiniteTransition(label = "avatar").animateFloat(0f, 360f, infiniteRepeatable(tween(24000, easing = LinearEasing), RepeatMode.Restart), label = "spin")
        Box(Modifier.size(104.dp).clip(RoundedCornerShape(34.dp)).clickable(onClick = onEdit).semantics { contentDescription = "Edit your profile: $name"; role = Role.Button }, contentAlignment = Alignment.Center) {
            Cookie(cs.tertiaryContainer, Modifier.fillMaxSize().rotate(spin), radiusFraction = 0.33f)
            Text(name.take(1).uppercase(), style = MaterialTheme.typography.displaySmall.copy(fontSize = 40.sp), color = cs.onTertiaryContainer)
        }
        Row(Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onEdit).semantics(mergeDescendants = true) { contentDescription = "Edit your name and details"; role = Role.Button }.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(name, style = MaterialTheme.typography.headlineSmall.copy(fontSize = 24.sp, fontWeight = FontWeight.SemiBold))
            Icon(PiIcons.Pencil, null, Modifier.size(16.dp), tint = cs.onSurfaceVariant)
        }
        board?.s("program")?.takeIf { it.isNotBlank() && it != "null" }?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant) }
        p?.o("score")?.let { sc ->
            Text("${sc.s("level").uppercase()} · SCORE ${sc.i("overall")}", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = 0.4.sp),
                color = cs.onPrimary, modifier = Modifier.clip(CircleShape).background(cs.primary).padding(horizontal = 12.dp, vertical = 6.dp))
        }
    }
}

@Composable
fun YouStats(board: JSONObject) {
    val cs = MaterialTheme.colorScheme
    val lt = board.o("lifetime") ?: return
    val u = board.s("unit", "kg")
    val lifted = lt.d("lifted") / (if (u == "lb") 2204.62 else 1000.0)
    val stats = listOf(
        "Sessions" to "${lt.i("sessions")}",
        "Lifted" to "${if (lifted >= 100) Math.round(lifted).toString() else fmt1(lifted)} t",
        "Streak" to "${lt.i("streakWeeks")} wk",
        "PRs · block" to "${board.i("prsBlock")}",
    )
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        stats.forEach { (k, v) ->
            Column(Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).background(cs.surfaceContainerLow).padding(vertical = 12.dp, horizontal = 4.dp).semantics(mergeDescendants = true) {},
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text(v, style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp), maxLines = 1)
                Text(k, style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), color = cs.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

/** Big 3 total: the ring fills toward the next strength club; each bar is a lift's multiple of bodyweight against a strong target. */
@Composable
fun ProfBig3Card(vm: AppViewModel, board: JSONObject) {
    val big = board.o("big3") ?: return
    if (big.i("logged") == 0) return
    val cs = MaterialTheme.colorScheme
    val u = board.s("unit", "kg")
    val club = vm.profile?.o("clubE1rm")
    val ratio = big.d("ratio")
    val ringTo by animateFloatAsState(((club?.i("pct") ?: 0) / 100f).coerceIn(0.02f, 1f).let { if (club?.b("maxedOut") == true) 1f else it }, tween(800), label = "ring")
    val tier = club?.let { c ->
        val cur = c.s("current").takeIf { it.isNotBlank() && it != "null" } ?: "No club yet"
        if (c.b("maxedOut") || c.s("next").let { it.isBlank() || it == "null" }) "$cur · every club cleared"
        else "$cur · ${ink(c.d("remaining"))} $u to ${c.s("next").replace(" lb Club", "").replace(" Club", "")}"
    }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp)).background(cs.primaryContainer).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(84.dp).semantics(mergeDescendants = true) { contentDescription = "Big three total ${fmtNum(ratio)} times bodyweight" + (tier?.let { ", $it" } ?: "") }, contentAlignment = Alignment.Center) {
                val track = cs.surface.copy(alpha = 0.6f); val ring = cs.primary
                Canvas(Modifier.fillMaxSize()) {
                    val st = 9.dp.toPx(); val r = 34.dp.toPx()
                    val tl = Offset(center.x - r, center.y - r); val sz = androidx.compose.ui.geometry.Size(r * 2, r * 2)
                    drawArc(track, 0f, 360f, false, tl, sz, style = Stroke(st))
                    drawArc(ring, -90f, 360f * ringTo, false, tl, sz, style = Stroke(st, cap = StrokeCap.Round))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (ratio > 0) "${fmt1(ratio)}×" else "—", style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, lineHeight = 22.sp), color = cs.onPrimaryContainer)
                    Text("BW", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold), color = cs.onPrimaryContainer)
                }
            }
            Column(Modifier.weight(1f)) {
                Text("BIG 3 TOTAL · e1RM", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.4.sp), color = cs.onPrimaryContainer.copy(alpha = 0.8f))
                Text("${ink(big.d("total"))} $u", style = MaterialTheme.typography.displaySmall.copy(fontSize = 34.sp, lineHeight = 38.sp), color = cs.onPrimaryContainer)
                Text(tier ?: "${3 - big.i("logged")} of the three not logged yet", style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp), color = cs.onPrimaryContainer)
            }
        }
        val targets = mapOf("bench" to 1.75, "squat" to 2.5, "deadlift" to 3.0)
        big.list("lifts").forEach { l ->
            val r = l.d("ratio")
            val w by animateFloatAsState(if (r > 0) (r / (targets[l.s("key")] ?: 3.0)).toFloat().coerceIn(0f, 1f) else 0f, tween(600), label = "b3")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(l.s("name"), style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold), color = cs.onPrimaryContainer, modifier = Modifier.width(74.dp))
                Box(Modifier.weight(1f).height(10.dp).clip(CircleShape).background(cs.surface)) { Box(Modifier.fillMaxWidth(w).height(10.dp).clip(CircleShape).background(cs.primary)) }
                Text(when { r > 0 -> "${"%.2f".format(Locale.US, r)}×"; l.d("e1rm") > 0 -> "${ink(l.d("e1rm"))}"; else -> "—" },
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold), color = cs.onPrimaryContainer, modifier = Modifier.width(54.dp), textAlign = TextAlign.End)
            }
        }
        if (board.isNull("bodyweight")) Text("Log your bodyweight to see each lift as a multiple of it.", style = MaterialTheme.typography.bodySmall, color = cs.onPrimaryContainer)
    }
}

/** Strength Score: the number, its level and 8-week change, a trend line, the four patterns, the next milestone. */
@Composable
fun ProfScoreCard(vm: AppViewModel, onOpen: () -> Unit, onNeedBodyweight: () -> Unit) {
    val p = vm.profile ?: return
    val cs = MaterialTheme.colorScheme
    val sc = p.o("score")
    if (sc == null) {
        if (!p.b("scoreLocked")) return
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(cs.surfaceContainerLow).clickable(onClick = onNeedBodyweight).padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("STRENGTH SCORE", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = 0.4.sp), color = cs.onSurfaceVariant)
            Text("Log your bodyweight to score your lifts against strength standards.", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
        }
        return
    }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(cs.surfaceContainerLow).clickable(onClick = onOpen)
        .semantics { contentDescription = "Strength score ${sc.i("overall")} of 100, ${sc.s("level")}. Open strength standards" }.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(Modifier.weight(1f)) {
                Text("STRENGTH SCORE", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = 0.4.sp), color = cs.onSurfaceVariant)
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${sc.i("overall")}", style = MaterialTheme.typography.displayMedium.copy(fontSize = 44.sp, lineHeight = 48.sp))
                    Text("/ 100", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(sc.s("level"), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold), color = cs.onSecondaryContainer,
                        modifier = Modifier.clip(CircleShape).background(cs.secondaryContainer).padding(horizontal = 10.dp, vertical = 4.dp))
                    if (!sc.isNull("delta8w") && sc.has("delta8w")) { val dl = sc.i("delta8w")
                        Text((if (dl >= 0) "+$dl" else "−${-dl}") + " in 8 wk", style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold), color = cs.primary) }
                }
            }
            val tr = sc.optJSONArray("trend")
            if (tr != null && tr.length() >= 2) {
                val vals = (0 until tr.length()).map { tr.optDouble(it) }
                val line = cs.primary
                Canvas(Modifier.padding(top = 18.dp).width(120.dp).height(40.dp)) {
                    val mn = vals.min(); val mx = vals.max().let { if (it - mn < 1) mn + 1 else it }
                    val pts = vals.mapIndexed { k, v -> Offset(k * size.width / (vals.size - 1), size.height - 3.dp.toPx() - ((v - mn) / (mx - mn)).toFloat() * (size.height - 6.dp.toPx())) }
                    val path = Path().apply { moveTo(pts[0].x, pts[0].y); pts.drop(1).forEach { lineTo(it.x, it.y) } }
                    val area = Path().apply { addPath(path); lineTo(size.width, size.height); lineTo(0f, size.height); close() }
                    drawPath(area, line.copy(alpha = 0.14f))
                    drawPath(path, line, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            sc.list("subs").forEachIndexed { k, sub ->
                val w by animateFloatAsState(sub.i("score") / 100f, tween(600), label = "sub")
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(sub.s("label"), style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold), modifier = Modifier.width(52.dp))
                    Box(Modifier.weight(1f).height(10.dp).clip(CircleShape).background(cs.surfaceContainerHighest)) {
                        Box(Modifier.fillMaxWidth(w).height(10.dp).clip(CircleShape).background(if (k == sc.list("subs").size - 1) cs.onTertiaryContainer else cs.primary))
                    }
                    Text("${sub.i("score")}", style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold), modifier = Modifier.width(30.dp), textAlign = TextAlign.End)
                }
            }
        }
        sc.o("next")?.let { n ->
            val lowest = sc.list("subs").minByOrNull { it.i("score") }?.s("label")
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(cs.surface).padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(PiIcons.Star, null, Modifier.size(20.dp), tint = cs.primary)
                Column(Modifier.weight(1f)) {
                    Text("${n.s("name")} ${ink(n.d("target"))} ${n.s("unit")} reaches ${n.s("level")}", style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp))
                    Text("Now ${ink(n.d("current"))} ${n.s("unit")} e1RM" + (if (lowest != null && n.s("pattern").equals(lowest, true)) " · ${lowest} is your lowest pattern, so it moves the score most" else ""),
                        style = MaterialTheme.typography.bodySmall.copy(lineHeight = 16.sp), color = cs.onSurfaceVariant)
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Standards and Strength Club", style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold), color = cs.primary)
            Icon(PiIcons.ChevronRight, null, Modifier.size(16.dp), tint = cs.primary)
        }
    }
}

/** Bodyweight: today's number with the 8-week change, ±0.5 to log, and the 8-week line. */
@Composable
fun ProfBodyweightCard(vm: AppViewModel, onOpenBody: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val b = vm.body
    val u = b.s("unit", "kg")
    val bw = b.s("bodyweight").toDoubleOrNull()
    val since = System.currentTimeMillis() - 56L * 86400000
    val log = b.list("log").filter { it.optLong("date") >= since && it.d("w") > 0 }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(cs.surfaceContainerLow).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Bodyweight", style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp), color = cs.onSurfaceVariant)
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AnimatedContent(bw?.let { fmt1(it) } ?: "—", transitionSpec = { (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut()) }, label = "bw") {
                        Text(it, style = MaterialTheme.typography.displaySmall.copy(fontSize = 32.sp))
                    }
                    Text(u, style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp), modifier = Modifier.padding(bottom = 5.dp))
                    if (bw != null && log.size >= 2) { val dl = bw - log.first().d("w")
                        Text((if (dl <= 0) "−" else "+") + fmt1(kotlin.math.abs(dl)) + " in 8 wk", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = cs.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 7.dp).clip(CircleShape).background(cs.surfaceContainerHigh).padding(horizontal = 8.dp, vertical = 3.dp)) }
                }
            }
            if (bw != null) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(cs.secondaryContainer).clickable { vm.logBodyweight(Math.round((bw - 0.5) * 10) / 10.0) }
                    .semantics { contentDescription = "Log bodyweight 0.5 lower"; role = Role.Button }, contentAlignment = Alignment.Center) { Text("−", fontSize = 22.sp, color = cs.onSecondaryContainer) }
                Box(Modifier.size(44.dp).clip(CircleShape).background(cs.primary).clickable { vm.logBodyweight(Math.round((bw + 0.5) * 10) / 10.0) }
                    .semantics { contentDescription = "Log bodyweight 0.5 higher"; role = Role.Button }, contentAlignment = Alignment.Center) { Text("+", fontSize = 22.sp, color = cs.onPrimary) }
            } else {
                Text("Log it", style = MaterialTheme.typography.labelLarge, color = cs.onPrimary, modifier = Modifier.clip(CircleShape).background(cs.primary).clickable(onClick = onOpenBody).padding(horizontal = 16.dp, vertical = 12.dp))
            }
        }
        val vals = log.map { it.d("w") }.let { if (it.size == 1) it + it else it }
        if (vals.size >= 2) {
            val line = cs.primary
            Canvas(Modifier.fillMaxWidth().height(54.dp).semantics { contentDescription = "Bodyweight over 8 weeks: ${vals.joinToString(", ") { fmt1(it) }} $u" }) {
                val mn = vals.min() - 0.3; val mx = vals.max() + 0.3
                val pts = vals.mapIndexed { k, v -> Offset(k * size.width / (vals.size - 1), (48.dp.toPx() - ((v - mn) / (mx - mn)).toFloat() * 42.dp.toPx())) }
                val path = Path().apply { moveTo(pts[0].x, pts[0].y); pts.drop(1).forEach { lineTo(it.x, it.y) } }
                val area = Path().apply { addPath(path); lineTo(size.width, size.height); lineTo(0f, size.height); close() }
                drawPath(area, line.copy(alpha = 0.12f))
                drawPath(path, line, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
        Text("8 weeks · every ratio above recalculates when you log", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
    }
}

/** PR board: e1RM, heaviest set, or the rep-PR matrix for one lift. */
@Composable
fun ProfPrBoard(vm: AppViewModel, board: JSONObject) {
    val pb = board.o("prBoard") ?: return
    if (pb.list("e1rm").isEmpty()) return
    val cs = MaterialTheme.colorScheme
    val u = board.s("unit", "kg")
    var mode by remember { mutableStateOf("e1rm") }
    var liftId by remember { mutableStateOf<String?>(null) }
    var repLift by remember { mutableStateOf<String?>(null) }
    liftId?.let { id -> LiftDetailSheet(vm, id) { liftId = null } }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.padding(start = 4.dp, end = 4.dp, top = 6.dp), verticalAlignment = Alignment.Bottom) {
            Text("PR board", style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp, fontWeight = FontWeight.SemiBold), modifier = Modifier.weight(1f).semantics { heading() })
            Text("${pb.list("e1rm").size} lifts tracked", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
        }
        Segmented(listOf("e1rm" to "e1RM", "heaviest" to "Heaviest", "reps" to "Rep PRs"), mode, { mode = it })
        AnimatedContent(mode, transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) }, label = "prmode") { m ->
            if (m != "reps") {
                Column(Modifier.clip(RoundedCornerShape(24.dp)), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    pb.list(m).forEachIndexed { k, r ->
                        Row(Modifier.fillMaxWidth().background(cs.surfaceContainerLow).clickable { liftId = r.s("id") }
                            .semantics(mergeDescendants = true) { contentDescription = "${r.s("name")} record. Open lift history" }.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.size(32.dp).clip(if (k < 3) RoundedCornerShape(11.dp) else CircleShape)
                                .background(when { k == 0 -> cs.primary; k < 3 -> cs.primaryContainer; else -> cs.surfaceContainerHigh }), contentAlignment = Alignment.Center) {
                                Text("${k + 1}", style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                                    color = when { k == 0 -> cs.onPrimary; k < 3 -> cs.onPrimaryContainer; else -> cs.onSurface })
                            }
                            Column(Modifier.weight(1f)) {
                                Text(r.s("name"), style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                val date = r.optLong("date").takeIf { it > 0 }?.let { SimpleDateFormat("MMM d", Locale.US).format(Date(it)) }
                                Text((if (m == "e1rm") "Estimated 1RM" else "Heaviest set") + (date?.let { " · $it" } ?: ""), style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp), color = cs.onSurfaceVariant)
                            }
                            Text(if (m == "heaviest" && r.i("reps") > 0) "${ink(r.d("value"))}×${r.i("reps")}" else "${ink(r.d("value"))} $u", style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold))
                        }
                    }
                }
            } else {
                val lifts = board.list("repPRs")
                val cur = lifts.firstOrNull { it.s("id") == repLift } ?: lifts.firstOrNull()
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        lifts.forEach { l ->
                            val on = l.s("id") == cur?.s("id")
                            val bg by animateColorAsState(if (on) cs.secondaryContainer else Color.Transparent, label = "rl")
                            Box(Modifier.height(36.dp).clip(RoundedCornerShape(if (on) 12.dp else 18.dp)).background(bg)
                                .then(if (on) Modifier else Modifier.border(1.dp, cs.outlineVariant, RoundedCornerShape(18.dp)))
                                .clickable { repLift = l.s("id") }.semantics { selected = on; role = Role.Button }.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                                Text(l.s("short").ifBlank { l.s("name") }, style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp), color = if (on) cs.onSecondaryContainer else cs.onSurfaceVariant)
                            }
                        }
                    }
                    cur?.let { l ->
                        l.list("reps").chunked(3).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                row.forEach { r ->
                                    val done = r.b("done")
                                    Column(Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).background(if (done) cs.primaryContainer else Color.Transparent)
                                        .then(if (done) Modifier else Modifier.border(1.5.dp, cs.outlineVariant, RoundedCornerShape(18.dp)))
                                        .semantics(mergeDescendants = true) {}.padding(vertical = 12.dp, horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        val fg = if (done) cs.onPrimaryContainer else cs.onSurface
                                        Text("${r.i("reps")} ${if (r.i("reps") == 1) "rep" else "reps"}", style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), color = fg.copy(alpha = 0.85f))
                                        Text(ink(r.d("w")), style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, fontWeight = FontWeight.SemiBold), color = fg)
                                        Text(if (done) "DONE" else "PREDICTED", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold), color = fg.copy(alpha = 0.8f))
                                    }
                                }
                            }
                        }
                    }
                    Text("Solid tiles are sets you actually did. Outlined tiles are what your best e1RM predicts — targets worth chasing.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
                }
            }
        }
    }
}

/** Training calendar: twelve Monday-first weeks, each day shaded by how much you did. */
@Composable
fun ProfHeatCard(board: JSONObject, onOpen: (String) -> Unit) {
    val cells = board.list("heat"); if (cells.size != 84) return
    val cs = MaterialTheme.colorScheme
    val shade = { lvl: Int -> if (lvl == 0) cs.surfaceContainerHighest else lerp(cs.surfaceContainerHighest, cs.primary, listOf(0f, 0.38f, 0.68f, 1f)[lvl]) }
    val fmt = SimpleDateFormat("MMM d", Locale.US)
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Training calendar", style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp, fontWeight = FontWeight.SemiBold), modifier = Modifier.padding(start = 4.dp, top = 6.dp).semantics { heading() })
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(cs.surfaceContainerLow).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("M", "", "W", "", "F", "", "S").forEach { Box(Modifier.height(18.dp), contentAlignment = Alignment.CenterStart) { Text(it, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold), color = cs.onSurfaceVariant) } }
                }
                Row(Modifier.weight(1f).semantics { contentDescription = "Training calendar, last 12 weeks: ${board.i("heatCount")} sessions" }, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (wk in 0 until 12) Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (dw in 0 until 7) {
                            val c = cells[wk * 7 + dw]
                            val future = c.b("future"); val today = c.b("today")
                            val bg by animateColorAsState(if (future) Color.Transparent else shade(c.i("level")), tween(400), label = "heat")
                            val id = c.s("id").takeIf { it.isNotBlank() && it != "null" }
                            Box(Modifier.fillMaxWidth().height(18.dp).clip(RoundedCornerShape(5.dp)).background(bg)
                                .then(when { today -> Modifier.border(2.dp, cs.onSurface, RoundedCornerShape(5.dp)); future -> Modifier.border(1.dp, cs.outlineVariant, RoundedCornerShape(5.dp)); else -> Modifier })
                                .then(if (id != null) Modifier.clickable { onOpen(id) } else Modifier)
                                .semantics { contentDescription = fmt.format(Date(c.optLong("date"))) + when { future -> " · upcoming"; c.i("sessions") > 0 -> " · trained"; else -> " · rest" } })
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${board.i("heatCount")} sessions in 12 weeks", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, modifier = Modifier.weight(1f))
                Text("Less", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                (0..3).forEach { Box(Modifier.padding(start = 4.dp).size(12.dp).clip(RoundedCornerShape(3.dp)).background(shade(it))) }
                Text(" More", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
        }
    }
}

private val BADGE_ICON = mapOf(
    "first" to PiIcons.icon("bd-first", "M8 21h8M12 17v4M7 4h10v5a5 5 0 0 1-10 0zM7 6H4v1a3 3 0 0 0 3 3M17 6h3v1a3 3 0 0 1-3 3"),
    "bar" to PiIcons.icon("bd-bar", "M6.5 6.5v11M17.5 6.5v11M3.5 9v6M20.5 9v6M6.5 12h11"),
    "century" to PiIcons.icon("bd-cal", "M4 6h16v14H4zM4 10h16M9 3v4M15 3v4"),
    "streak6" to PiIcons.Flame,
    "tonne" to PiIcons.icon("bd-tonne", "M4 18h4V10H4zM10 18h4V6h-4zM16 18h4v-5h-4z"),
)

@Composable
private fun BadgeShape(done: Boolean, size: androidx.compose.ui.unit.Dp, radius: androidx.compose.ui.unit.Dp, id: String, iconSize: androidx.compose.ui.unit.Dp) {
    val cs = MaterialTheme.colorScheme
    val fill by animateColorAsState(if (done) cs.primary else cs.surfaceContainerHighest, tween(400), label = "bd")
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(radius)).background(fill))
        Box(Modifier.fillMaxSize().rotate(45f).clip(RoundedCornerShape(radius)).background(fill))
        Icon(BADGE_ICON[id] ?: BADGE_ICON["bar"]!!, null, Modifier.size(iconSize), tint = if (done) cs.onPrimary else cs.onSurfaceVariant)
    }
}

/** Achievements: eight rings; tap one for what it takes and how close you are. */
@Composable
fun ProfBadges(board: JSONObject) {
    val all = board.list("badges"); if (all.isEmpty()) return
    val cs = MaterialTheme.colorScheme
    var open by remember { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Achievements", style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp, fontWeight = FontWeight.SemiBold), modifier = Modifier.padding(start = 4.dp, top = 6.dp).semantics { heading() })
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            all.chunked(4).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { m ->
                        val on = open == m.s("id"); val done = m.b("done")
                        val bg by animateColorAsState(if (on) cs.secondaryContainer else Color.Transparent, label = "bdc")
                        val prog by animateFloatAsState(m.d("progress").toFloat(), tween(800), label = "bdp")
                        Column(Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).background(bg).clickable { open = if (on) null else m.s("id") }
                            .semantics(mergeDescendants = true) { role = Role.Button; selected = on; contentDescription = m.s("name") + if (done) ", earned" else ", ${(m.d("progress") * 100).toInt()} percent" }
                            .padding(vertical = 8.dp, horizontal = 2.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(Modifier.size(58.dp), contentAlignment = Alignment.Center) {
                                val track = cs.outlineVariant; val ring = cs.primary
                                Canvas(Modifier.fillMaxSize()) {
                                    val st = 3.dp.toPx(); val r = 26.dp.toPx()
                                    val tl = Offset(center.x - r, center.y - r); val sz = androidx.compose.ui.geometry.Size(r * 2, r * 2)
                                    if (!done) drawArc(track, 0f, 360f, false, tl, sz, style = Stroke(st))
                                    drawArc(ring, -90f, 360f * prog, false, tl, sz, style = Stroke(st, cap = StrokeCap.Round))
                                }
                                BadgeShape(done, 40.dp, 13.dp, badgeIcon(m.s("id")), 22.dp)
                            }
                            Text(m.s("short"), style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold, lineHeight = 13.sp), textAlign = TextAlign.Center, maxLines = 2)
                        }
                    }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        val m = all.firstOrNull { it.s("id") == open }
        AnimatedVisibility(m != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            val x = m ?: return@AnimatedVisibility
            val done = x.b("done")
            val fg = if (done) cs.onPrimaryContainer else cs.onSurface
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(if (done) cs.primaryContainer else cs.surfaceContainerHigh).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                BadgeShape(done, 44.dp, 14.dp, badgeIcon(x.s("id")), 26.dp)
                Spacer(Modifier.width(6.dp))
                Column(Modifier.weight(1f)) {
                    Text(x.s("name"), style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold), color = fg)
                    Text(x.s("desc"), style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 18.sp), color = fg.copy(alpha = 0.9f))
                    Text(x.s("status"), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold), color = fg, modifier = Modifier.padding(top = 4.dp))
                }
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(cs.surface).clickable { open = null }.semantics { contentDescription = "Close badge"; role = Role.Button }, contentAlignment = Alignment.Center) {
                    Icon(PiIcons.Close, null, Modifier.size(16.dp))
                }
            }
        }
    }
}

private fun badgeIcon(id: String) = when (id) { "first" -> "first"; "century" -> "century"; "streak6" -> "streak6"; "tonne" -> "tonne"; else -> "bar" }

/** Muscle balance: working sets per muscle (pressing muscles in primary), and the push : pull read. */
@Composable
fun ProfBalance(board: JSONObject) {
    val bal = board.o("balance") ?: return
    val parts = bal.list("parts"); if (parts.isEmpty()) return
    val cs = MaterialTheme.colorScheme
    val max = parts.maxOf { it.i("sets") }.coerceAtLeast(1)
    val push = setOf("chest", "shoulders", "triceps")
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Muscle balance · 30 days", style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp, fontWeight = FontWeight.SemiBold), modifier = Modifier.padding(start = 4.dp, top = 6.dp).semantics { heading() })
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(cs.surfaceContainerLow).padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            parts.take(10).forEach { m ->
                val w by animateFloatAsState(m.i("sets") / max.toFloat(), tween(600), label = "bal")
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(m.s("label").replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold), modifier = Modifier.width(84.dp), maxLines = 1)
                    Box(Modifier.weight(1f).height(10.dp).clip(CircleShape).background(cs.surfaceContainerHighest)) {
                        Box(Modifier.fillMaxWidth(w).height(10.dp).clip(CircleShape).background(if (m.s("part") in push) cs.primary else cs.onTertiaryContainer))
                    }
                    Text("${m.i("sets")}", style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold), modifier = Modifier.width(30.dp), textAlign = TextAlign.End)
                }
            }
            val ratio = bal.d("ratio")
            if (ratio > 0) {
                val heavy = ratio > 1.3
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(if (heavy) cs.tertiaryContainer else cs.primaryContainer).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val fg = if (heavy) cs.onTertiaryContainer else cs.onPrimaryContainer
                    Icon(PiIcons.Scale, null, Modifier.padding(top = 1.dp).size(18.dp), tint = fg)
                    Text(if (heavy) "Push-heavy at ${fmt1(ratio)} : 1 push to pull (${bal.i("push")} vs ${bal.i("pull")} sets). Your next block could trade a press slot for a row."
                        else "Push and pull are balanced at ${fmt1(ratio)} : 1.", style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 18.sp), color = fg)
                }
            }
        }
    }
}

/** The design's "Your profile" panel: name, and the details the standards use. */
@Composable
fun MeSheet(vm: AppViewModel, onClose: () -> Unit, onStandards: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val p = vm.profile
    var name by remember { mutableStateOf(p?.s("name").orEmpty()) }
    val age = p?.optString("age")?.toIntOrNull() ?: 31
    ModalBottomSheet(onDismissRequest = { if (name != p?.s("name").orEmpty()) vm.setProfile(name = name); onClose() }) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Your profile", style = MaterialTheme.typography.headlineSmall)
            Text("NAME", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = 0.4.sp), color = cs.onSurfaceVariant)
            OutlinedTextField(name, { name = it.take(40) }, Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("Athlete") }, shape = RoundedCornerShape(16.dp))
            Text("FOR STRENGTH STANDARDS", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = 0.4.sp), color = cs.onSurfaceVariant)
            Segmented(listOf("male" to "Male", "female" to "Female"), p?.s("sex", "male") ?: "male", { vm.setProfile(sex = it) })
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(cs.surfaceContainerHigh).padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Age", style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(cs.surface).clickable { vm.setProfile(age = (age - 1).coerceAtLeast(14)) }.semantics { contentDescription = "Younger"; role = Role.Button }, contentAlignment = Alignment.Center) { Text("−", fontSize = 18.sp) }
                Text("$age", style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp), modifier = Modifier.width(36.dp), textAlign = TextAlign.Center)
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(cs.surface).clickable { vm.setProfile(age = (age + 1).coerceAtMost(90)) }.semantics { contentDescription = "Older"; role = Role.Button }, contentAlignment = Alignment.Center) { Text("+", fontSize = 18.sp) }
            }
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(cs.surfaceContainerHigh).padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Bodyweight", style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
                Text(p?.s("bodyweight")?.takeIf { it.isNotBlank() }?.let { "$it ${p.s("unit")}" } ?: "—", style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp))
                Text("· log it on Body", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
            }
            Text("Used for your Strength Score and standards. Stays on this phone.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            Box(Modifier.fillMaxWidth().height(52.dp).clip(CircleShape).background(cs.primary).clickable { if (name != p?.s("name").orEmpty()) vm.setProfile(name = name); onStandards() }
                .semantics { role = Role.Button }, contentAlignment = Alignment.Center) {
                Text("See where I rank", style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold), color = cs.onPrimary)
            }
        }
    }
}

@Composable
fun CustomizeProfileButton(onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Row(Modifier.height(44.dp).clip(CircleShape).background(cs.surfaceContainerHigh).clickable(onClick = onClick).semantics(mergeDescendants = true) { role = Role.Button }.padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(PiIcons.Sliders, null, Modifier.size(18.dp))
            Text("Customize profile", style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold))
        }
    }
}

/** The design's Strength Club (in Strength standards): lifted or estimated big-three total against the pound clubs. */
@Composable
fun StrengthClubCard(vm: AppViewModel) {
    val p = vm.profile ?: return
    val cs = MaterialTheme.colorScheme
    var mode by remember { mutableStateOf("lifted") }
    val c = (if (mode == "lifted") p.o("clubLifted") else p.o("clubE1rm")) ?: return
    val u = p.s("unit", "kg")
    val tiers = c.list("tiers")
    val cur = c.s("current").takeIf { it.isNotBlank() && it != "null" }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(cs.surfaceContainerLow).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Strength Club", style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold), modifier = Modifier.weight(1f))
            Segmented(listOf("lifted" to "Lifted", "e1rm" to "Estimated max"), mode, { mode = it }, Modifier.width(210.dp), height = 34.dp)
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("${if (u == "lb") java.text.NumberFormat.getIntegerInstance(Locale.US).format(Math.round(c.d("total"))) else Math.round(c.d("total")).toString()} $u",
                style = MaterialTheme.typography.displaySmall.copy(fontSize = 34.sp, lineHeight = 38.sp))
            Text(cur ?: "Not in a club yet", style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold), color = cs.primary, modifier = Modifier.padding(bottom = 6.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            c.list("rows").forEach { r ->
                Column(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(cs.surface).padding(vertical = 8.dp, horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (r.b("has")) "${fmtNum(r.d("value"))} $u" else "—", style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold))
                    Text(r.s("label").replace("Back squat", "Squat").replace("Bench press", "Bench"), style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), color = cs.onSurfaceVariant)
                }
            }
        }
        val w by animateFloatAsState(c.i("pct") / 100f, tween(600), label = "club")
        Box(Modifier.fillMaxWidth().height(10.dp).clip(CircleShape).background(cs.surfaceContainerHighest)) { Box(Modifier.fillMaxWidth(w).height(10.dp).clip(CircleShape).background(cs.primary)) }
        val nextLb = tiers.firstOrNull { !it.b("on") }?.i("lb")
        Text(when {
            c.b("maxedOut") -> "Every club cleared"
            !c.b("complete") -> if (mode == "lifted") "Log a heavy set of squat, bench and deadlift to count a total." else "Log squat, bench and deadlift to complete your total."
            nextLb != null -> "${nextLb - c.i("totalLb")} lb to the ${tiers.first { it.i("lb") == nextLb }.s("name")}" + (if (u == "kg") " (${Math.ceil((nextLb - c.i("totalLb")) / 2.20462).toInt()} kg)" else "")
            else -> "Every club cleared"
        }, style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            tiers.forEach { t ->
                val on = t.b("on")
                val bg by animateColorAsState(if (on) cs.primary else cs.surfaceContainerHighest, label = "tier")
                Box(Modifier.weight(1f).height(34.dp).clip(RoundedCornerShape(12.dp)).background(bg).semantics { contentDescription = t.s("name") + if (on) ", cleared" else "" }, contentAlignment = Alignment.Center) {
                    Text(java.text.NumberFormat.getIntegerInstance(Locale.US).format(t.i("lb")), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold), color = if (on) cs.onPrimary else cs.onSurfaceVariant)
                }
            }
        }
        Text((if (mode == "lifted") "Heaviest weight you have put on the bar across squat, bench and deadlift." else "What your best sets predict you could lift for one rep.") +
            " Clubs are counted in pounds, the way the lifting world counts them.", style = MaterialTheme.typography.bodySmall.copy(lineHeight = 16.sp), color = cs.onSurfaceVariant)
    }
}
