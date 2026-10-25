package com.pursuitiron.app.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.pursuitiron.app.AppViewModel
import com.pursuitiron.app.ui.common.PiIcons
import com.pursuitiron.app.ui.common.Segmented
import com.pursuitiron.app.ui.common.b
import com.pursuitiron.app.ui.common.d
import com.pursuitiron.app.ui.common.fmtNum
import com.pursuitiron.app.ui.common.i
import com.pursuitiron.app.ui.common.list
import com.pursuitiron.app.ui.common.o
import com.pursuitiron.app.ui.common.s
import org.json.JSONObject
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/* The design's finish page (Main.dc.html isSummary): "Workout done" with a burst, time · sets · volume,
   how each muscle felt (it sets next time's sets), next time's loads, the main lift, the next day,
   Anything hurt? and Save session. Nothing is saved until Save session. */
@Composable
fun FinishPage(vm: AppViewModel, s: JSONObject, onClose: () -> Unit) {
    var p by remember { mutableStateOf<JSONObject?>(null) }
    LaunchedEffect(vm.session) { p = vm.finishPreview() }
    var hurt by remember { mutableStateOf(false) }
    var noteOpen by remember { mutableStateOf(false) }
    var routine by remember { mutableStateOf("") }
    var routineSaved by remember { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(cs.primaryContainer)) {
            val x = p
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 40.dp, bottom = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
                FinishHero()
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Workout done", style = MaterialTheme.typography.displaySmall.copy(fontSize = 38.sp, lineHeight = 42.sp), color = cs.onPrimaryContainer, textAlign = TextAlign.Center,
                        modifier = Modifier.semantics { heading() })
                    Text(x?.s("title").orEmpty(), style = MaterialTheme.typography.bodyLarge, color = cs.onPrimaryContainer.copy(alpha = 0.85f), modifier = Modifier.padding(top = 6.dp))
                }
                if (x == null) return@Column
                val u = x.s("unit", "kg")
                val prs = x.list("prs")
                if (prs.isNotEmpty()) Column(Modifier.clip(RoundedCornerShape(20.dp)).background(cs.primary).padding(horizontal = 16.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${prs.size} new e1RM ${if (prs.size == 1) "PR" else "PRs"}", style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold), color = cs.onPrimary)
                    Text(prs.joinToString(" · ") { it.s("name") }, style = MaterialTheme.typography.labelMedium, color = cs.onPrimary.copy(alpha = 0.9f), textAlign = TextAlign.Center)
                }
                val sec = (x.optLong("elapsedMs") / 1000).toInt()
                Row(Modifier.widthIn(max = 520.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Time" to (if (sec >= 3600) "${sec / 3600}:${String.format(Locale.US, "%02d", sec / 60 % 60)}:${String.format(Locale.US, "%02d", sec % 60)}" else "${sec / 60}:${String.format(Locale.US, "%02d", sec % 60)}"),
                        "Sets" to "${x.i("doneSets")}", "Volume" to "${NumberFormat.getIntegerInstance(Locale.US).format(x.i("volume"))} $u").forEach { (k, v) ->
                        Column(Modifier.weight(1f).clip(RoundedCornerShape(22.dp)).background(cs.surface).padding(vertical = 14.dp, horizontal = 8.dp).semantics(mergeDescendants = true) {}, horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(k, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                            Text(v, style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, fontWeight = FontWeight.SemiBold), color = cs.onSurface, maxLines = 1)
                        }
                    }
                }
                val parts = x.list("parts")
                if (parts.isNotEmpty()) FeelCard(vm, x, parts)
                Column(Modifier.widthIn(max = 520.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val next = x.list("nextPlan")
                    if (next.isNotEmpty()) SurfaceCard {
                        Kick("NEXT TIME · ${x.s("title").uppercase()}")
                        Text("Opening loads for next time, sized from today’s sets.", style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 18.sp), color = cs.onSurfaceVariant)
                        next.forEach { n ->
                            val delta = n.d("delta")
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.semantics(mergeDescendants = true) {}) {
                                Column(Modifier.weight(1f)) {
                                    Text(n.s("name"), style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold))
                                    Text(n.s("reason").ifBlank { if (delta > 0) "+${fmtNum(delta)} $u" else if (delta < 0) "${fmtNum(delta)} $u" else "Hold the load, build reps" },
                                        style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                                Text("${fmtNum(n.d("weight").takeIf { it > 0 } ?: n.d("from"))} $u", style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                                    color = if (delta > 0) cs.primary else cs.onSurface)
                            }
                        }
                    }
                    x.o("mainE1rm")?.let { m ->
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(cs.surface).padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp, 14.dp, 4.dp, 14.dp)).background(cs.primary), contentAlignment = Alignment.Center) { Icon(PiIcons.Progress, null, Modifier.size(22.dp), tint = cs.onPrimary) }
                            Column(Modifier.weight(1f)) {
                                Text("${m.s("name")} · e1RM", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = cs.onSurfaceVariant)
                                Text("${m.i("est")} $u", style = MaterialTheme.typography.headlineSmall.copy(fontSize = 24.sp, lineHeight = 28.sp), color = cs.onSurface)
                                val prev = m.i("prev")
                                Text(if (prev > 0 && m.i("est") > prev) "+${m.i("est") - prev} $u · new best" else if (prev > 0) "Best is $prev $u · held" else "First estimate",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = cs.primary)
                            }
                        }
                    }
                    val goals = x.list("goalsHit")
                    if (goals.isNotEmpty()) Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(cs.tertiaryContainer).padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("GOAL REACHED", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = 0.4.sp), color = cs.onTertiaryContainer)
                        goals.forEach { g -> Text("${g.s("name")} · ${fmtNum(g.d("goal"))} $u", style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold), color = cs.onTertiaryContainer) }
                        Text("Set a new one from the lift’s menu next time.", style = MaterialTheme.typography.labelMedium, color = cs.onTertiaryContainer)
                    }
                    x.s("sessionNote").takeIf { it.isNotBlank() }?.let { note ->
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(cs.surface).clickable { noteOpen = true }
                            .semantics(mergeDescendants = true) { contentDescription = "Edit workout note"; role = Role.Button }.padding(horizontal = 16.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(PiIcons.Note, null, Modifier.padding(top = 2.dp).size(18.dp), tint = cs.onSurfaceVariant)
                            Text(note, style = MaterialTheme.typography.bodyMedium, color = cs.onSurface)
                        }
                    }
                    x.o("nextDay")?.let { nd ->
                        SurfaceCard(gap = 8.dp) {
                            Kick("NEXT" + (nd.s("weekday").takeIf { it.isNotBlank() && it != "null" }?.let { " · ${it.uppercase()}" } ?: ""))
                            Text(nd.s("label"), style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, fontWeight = FontWeight.SemiBold))
                            val rows = nd.list("rows")
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                rows.take(4).forEach { r ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Text(r.s("name"), style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                        Text(r.s("rx"), style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold))
                                    }
                                }
                            }
                            if (rows.size > 4) Text("+ ${rows.size - 4} more", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                        }
                    }
                    val ids = (0 until (x.optJSONArray("routineIds")?.length() ?: 0)).map { x.getJSONArray("routineIds").optString(it) }
                    if (x.b("quick") && ids.isNotEmpty()) SurfaceCard(radius = 28.dp, gap = 12.dp) {
                        Column {
                            Text("Save as a routine?", style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, fontWeight = FontWeight.SemiBold))
                            Text("${ids.size} lift${if (ids.size == 1) "" else "s"} · start it again from Exercises any time", style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 18.sp), color = cs.onSurfaceVariant)
                        }
                        if (!routineSaved) {
                            OutlinedTextField(routine, { routine = it.take(40) }, Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("Name it — e.g. Hotel gym") }, shape = RoundedCornerShape(16.dp))
                            Box(Modifier.height(44.dp).clip(CircleShape).background(cs.secondaryContainer).clickable { routineSaved = true }.semantics { role = Role.Button }.padding(horizontal = 18.dp), contentAlignment = Alignment.Center) {
                                Text("Save routine", style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold), color = cs.onSecondaryContainer)
                            }
                        } else Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(cs.primaryContainer).padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(PiIcons.Check, null, Modifier.size(18.dp), tint = cs.onPrimaryContainer)
                            Text("“${routine.ifBlank { "My routine" }}” goes to your library when you save the session.", style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 18.sp), color = cs.onPrimaryContainer)
                        }
                    }
                }
                Row(Modifier.height(44.dp).clip(CircleShape).border(1.5.dp, cs.onPrimaryContainer, CircleShape).clickable { hurt = true }.semantics(mergeDescendants = true) { role = Role.Button }.padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(PiIcons.Shield, null, Modifier.size(18.dp), tint = cs.onPrimaryContainer)
                    Text("Anything hurt?", style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold), color = cs.onPrimaryContainer)
                }
                val nudged = parts.count { !it.isNull("diff") || !it.isNull("pump") || !it.isNull("sore") }
                Box(Modifier.widthIn(max = 520.dp).fillMaxWidth().height(56.dp).clip(CircleShape).background(cs.primary)
                    .clickable { onClose(); vm.finish(if (routineSaved) routine.ifBlank { "My routine" } else null, ids, nudged) }
                    .semantics { role = Role.Button }, contentAlignment = Alignment.Center) {
                    Text("Save session", style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold), color = cs.onPrimary)
                }
            }
            IconButton(onClick = onClose, modifier = Modifier.statusBarsPadding().padding(8.dp).align(Alignment.TopStart)) {
                Icon(PiIcons.ChevronLeft, "Keep going", tint = cs.onPrimaryContainer)
            }
        }
        if (hurt) HurtSheet(vm, 0) { hurt = false }
        if (noteOpen) SessionNoteSheet(vm, s) { noteOpen = false }
    }
}

@Composable
private fun Kick(t: String) = Text(t, style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.4.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
private fun SurfaceCard(radius: androidx.compose.ui.unit.Dp = 24.dp, gap: androidx.compose.ui.unit.Dp = 10.dp, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(radius)).background(MaterialTheme.colorScheme.surface).padding(16.dp), verticalArrangement = Arrangement.spacedBy(gap), content = content)
}

/** The hero: a spinning primary cookie, a check that draws itself, and a one-time burst of confetti. */
@Composable
private fun FinishHero() {
    val cs = MaterialTheme.colorScheme
    val spin by rememberInfiniteTransition(label = "hero").animateFloat(0f, 360f, infiniteRepeatable(tween(24000, easing = LinearEasing), RepeatMode.Restart), label = "spin")
    val draw = remember { Animatable(0f) }
    val burst = remember { Animatable(0f) }
    LaunchedEffect(Unit) { draw.animateTo(1f, tween(600, delayMillis = 200)) }
    LaunchedEffect(Unit) { burst.animateTo(1f, tween(1100)) }
    val colors = listOf(cs.primary, cs.tertiary, cs.secondary, cs.onPrimaryContainer)
    Box(Modifier.size(130.dp).semantics { contentDescription = "Workout done" }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(130.dp)) {
            val t = burst.value
            if (t < 1f) for (k in 0 until 16) {
                val a = Math.toRadians(k * 22.5 + 8).toFloat()
                val r = size.minDimension / 2 * (0.4f + 0.9f * t)
                drawCircle(colors[k % colors.size].copy(alpha = 1f - t), (if (k % 2 == 0) 5f else 3.5f).dp.toPx(), Offset(center.x + cos(a) * r, center.y + sin(a) * r))
            }
        }
        Box(Modifier.fillMaxSize().rotate(spin)) {
            Box(Modifier.fillMaxSize().clip(RoundedCornerShape(44.dp)).background(cs.primary))
            Box(Modifier.fillMaxSize().rotate(45f).clip(RoundedCornerShape(44.dp)).background(cs.primary))
        }
        val ink = cs.onPrimary
        Canvas(Modifier.size(60.dp)) {
            val sc = size.width / 24f
            val path = Path().apply { moveTo(5f * sc, 12.5f * sc); lineTo(9.5f * sc, 17f * sc); lineTo(19f * sc, 7f * sc) }
            val pm = PathMeasure().apply { setPath(path, false) }
            val part = Path(); pm.getSegment(0f, pm.length * draw.value, part, true)
            drawPath(part, ink, style = Stroke(2.6f * sc, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

/** How did each muscle feel? The answer sets next time's sets (only with room under MRV); pump and recovery are optional. */
@Composable
private fun FeelCard(vm: AppViewModel, x: JSONObject, parts: List<JSONObject>) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.widthIn(max = 520.dp).fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(cs.surface).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Column {
            Text("How did each muscle feel?", style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, fontWeight = FontWeight.SemiBold))
            Text("Sets next time follow your answer — only where there's room under MRV. Pump and recovery are optional.", style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp), color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
        }
        parts.forEach { pt ->
            val part = pt.s("part")
            val name = pt.s("label").replaceFirstChar { it.uppercase() }
            val diff = if (pt.isNull("diff")) null else pt.i("diff")
            val n = pt.i("sets")
            val (res, bg, fg) = when (diff) {
                1 -> if (!pt.b("room")) Triple("No room · at MRV", cs.tertiaryContainer, cs.onTertiaryContainer) else Triple("$n → ${n + 1} sets", cs.primary, cs.onPrimary)
                0 -> Triple("Holds at $n sets", cs.secondaryContainer, cs.onSecondaryContainer)
                -1 -> Triple("$n → ${(n - 1).coerceAtLeast(1)} sets", cs.tertiaryContainer, cs.onTertiaryContainer)
                else -> Triple("Pick one", cs.surfaceContainerHigh, cs.onSurfaceVariant)
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(name, style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
                    Text(res, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = fg, modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(bg).padding(horizontal = 8.dp, vertical = 3.dp))
                }
                Segmented(listOf("1" to "Too easy", "0" to "About right", "-1" to "Too much"), diff?.toString(), { vm.act("feedback", "part" to part, "diff" to it.toInt()) }, height = 40.dp, gap = 3.dp)
                FeelRow("Pump", listOf("flat" to "Low", "good" to "Moderate", "huge" to "Great"), pt.s("pump").takeIf { !pt.isNull("pump") }) { vm.act("feedback", "part" to part, "pump" to it) }
                FeelRow("Going in", listOf("sore" to "Still sore", "ontime" to "Recovered", "fresh" to "Fresh"), pt.s("sore").takeIf { !pt.isNull("sore") }) { vm.act("feedback", "part" to part, "sore" to it) }
            }
        }
    }
}

@Composable
private fun FeelRow(label: String, opts: List<Pair<String, String>>, sel: String?, onPick: (String) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(64.dp))
        Segmented(opts, sel, onPick, Modifier.weight(1f), height = 36.dp, gap = 3.dp)
    }
}
