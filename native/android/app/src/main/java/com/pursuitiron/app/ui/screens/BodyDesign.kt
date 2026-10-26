package com.pursuitiron.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pursuitiron.app.AppViewModel
import com.pursuitiron.app.ui.common.PiIcons
import com.pursuitiron.app.ui.common.d
import com.pursuitiron.app.ui.common.list
import com.pursuitiron.app.ui.common.o
import com.pursuitiron.app.ui.common.s
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/* The design's You → Body (Main.dc.html profBody): bodyweight with daily dots and a 7-day line,
   measurements as stepper rows that open their own trend, and progress photos. */

private const val DAY = 86_400_000L
private fun f1(v: Double) = String.format(Locale.US, "%.1f", Math.round(v * 10) / 10.0)
private val md = SimpleDateFormat("MMM d", Locale.US)

private fun startOfDay(ms: Long): Long = java.util.Calendar.getInstance().apply { timeInMillis = ms; set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0); set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0) }.timeInMillis

/** A number you can type instead of stepping to it. */
@Composable
private fun TypeValueDialog(title: String, unit: String, initial: String, onDone: (Double) -> Unit, onClose: () -> Unit) {
    var v by remember { mutableStateOf(initial) }
    AlertDialog(onDismissRequest = onClose, title = { Text(title) },
        text = { OutlinedTextField(v, { v = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(6) }, singleLine = true, suffix = { Text(unit) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)) },
        confirmButton = { TextButton(onClick = { v.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }?.let(onDone); onClose() }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } })
}

@Composable
fun BodyBwCard(vm: AppViewModel) {
    val cs = MaterialTheme.colorScheme
    val b = vm.body
    val u = b.s("unit", "kg")
    val saved = b.s("bodyweight").toDoubleOrNull()
    var draft by remember(saved) { mutableStateOf(saved ?: if (u == "lb") 165.0 else 75.0) }
    var typing by remember { mutableStateOf(false) }
    val today = startOfDay(System.currentTimeMillis())
    val since = today - 27 * DAY
    val log = b.list("log").map { startOfDay(it.optLong("date")) to it.d("w") }.filter { it.first >= since && it.second > 0 }
    val avg = log.indices.map { i -> log.filter { it.first in (log[i].first - 6 * DAY)..log[i].first }.map { it.second }.average() }
    val loggedToday = log.lastOrNull()?.let { it.first == today && Math.abs(it.second - draft) < 0.05 } == true
    val avgNow = log.filter { it.first > today - 7 * DAY }.map { it.second }.takeIf { it.isNotEmpty() }?.average()
    val avgThen = log.filter { it.first <= today - 21 * DAY }.map { it.second }.takeIf { it.isNotEmpty() }?.average()
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(cs.surfaceContainerLow).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("Bodyweight", style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold), modifier = Modifier.weight(1f))
            if (avgNow != null && avgThen != null) { val dl = avgNow - avgThen
                Text((if (dl <= 0) "−" else "+") + f1(Math.abs(dl)) + " $u in 3 weeks", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = cs.onSurfaceVariant) }
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(saved?.let { f1(it) } ?: "—", style = MaterialTheme.typography.displaySmall.copy(fontSize = 40.sp, lineHeight = 44.sp))
            Text(buildAnnotatedString {
                append("$u · 7-day average ")
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = cs.onSurface)) { append(avgNow?.let { f1(it) } ?: "—") }
            }, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
        }
        if (log.size >= 2) {
            val draw = remember { Animatable(0f) }
            LaunchedEffect(log.size) { draw.snapTo(0f); draw.animateTo(1f, com.pursuitiron.app.ui.theme.Motion.data(700)) }
            val dotC = cs.onSurfaceVariant.copy(alpha = 0.45f); val lineC = cs.primary; val axis = cs.outlineVariant
            Canvas(Modifier.fillMaxWidth().height(120.dp).semantics { contentDescription = "Bodyweight over 4 weeks. 7-day average from ${f1(avg.first())} to ${f1(avg.last())} $u." }) {
                val all = log.map { it.second }
                val lo = all.min() - 0.3; val hi = all.max() + 0.3
                val sx = size.width / 300f; val sy = size.height / 120f
                val x = { t: Long -> (4 + (t - since).toFloat() / (27 * DAY) * 292) * sx }
                val y = { v: Double -> ((104 - (v - lo) / (hi - lo) * 96).toFloat()) * sy }
                drawLine(axis, Offset(0f, 110 * sy), Offset(size.width, 110 * sy), 1.dp.toPx())
                log.forEach { drawCircle(dotC, 2.6.dp.toPx(), Offset(x(it.first), y(it.second))) }
                val path = Path().apply { log.forEachIndexed { i, p -> if (i == 0) moveTo(x(p.first), y(avg[i])) else lineTo(x(p.first), y(avg[i])) } }
                val pm = PathMeasure().apply { setPath(path, false) }
                val part = Path(); pm.getSegment(0f, pm.length * draw.value, part, true)
                drawPath(part, lineC, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            Row {
                Text(md.format(Date(since)), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = cs.onSurfaceVariant)
                Text("Dots: daily · line: 7-day average", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = cs.onSurfaceVariant, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                Text("Today", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = cs.onSurfaceVariant)
            }
        } else Text("Log a few days and your 7-day average appears here.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(cs.surfaceContainerHigh).clickable { draft = Math.round((draft - 0.1) * 10) / 10.0 }
                .semantics { contentDescription = "Less bodyweight"; role = Role.Button }, contentAlignment = Alignment.Center) { Text("−", fontSize = 20.sp) }
            Text(f1(draft), style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, fontWeight = FontWeight.SemiBold), textAlign = TextAlign.Center,
                modifier = Modifier.width(64.dp).clip(RoundedCornerShape(10.dp)).clickable { typing = true }.semantics { contentDescription = "Bodyweight to log, ${f1(draft)} $u. Tap to type" })
            Box(Modifier.size(44.dp).clip(CircleShape).background(cs.surfaceContainerHigh).clickable { draft = Math.round((draft + 0.1) * 10) / 10.0 }
                .semantics { contentDescription = "More bodyweight"; role = Role.Button }, contentAlignment = Alignment.Center) { Text("+", fontSize = 20.sp) }
            Box(Modifier.weight(1f))
            val bg by animateColorAsState(if (loggedToday) cs.surfaceContainerHigh else cs.primary, com.pursuitiron.app.ui.theme.Motion.effects(), label = "bwsave")
            Box(Modifier.height(44.dp).clip(CircleShape).background(bg).clickable(enabled = !loggedToday) { vm.logBodyweight(draft) }
                .semantics { role = Role.Button }.padding(horizontal = 18.dp), contentAlignment = Alignment.Center) {
                Text(if (loggedToday) "Logged today" else "Log today", style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                    color = if (loggedToday) cs.onSurfaceVariant else cs.onPrimary)
            }
        }
    }
    if (typing) TypeValueDialog("Bodyweight", u, f1(draft), { draft = Math.round(it * 10) / 10.0 }) { typing = false }
}

private val MEASURES = listOf("waist", "chest", "arm", "thigh", "hips", "neck")

@Composable
fun BodyMeasureCard(vm: AppViewModel) {
    val cs = MaterialTheme.colorScheme
    val m = vm.body.o("measurements")
    val lu = vm.body.s("lengthUnit", if (vm.body.s("unit") == "lb") "in" else "cm")
    val inch = lu == "in"
    val step = if (inch) 0.25 else 0.5
    val fx = { v: Double -> if (inch) String.format(Locale.US, "%.2f", v).trimEnd('0').let { if (it.endsWith(".")) it + "0" else it } else f1(v) }
    var open by remember { mutableStateOf<String?>(null) }
    var typing by remember { mutableStateOf<String?>(null) }
    val first = MEASURES.mapNotNull { k -> m.list(k).firstOrNull()?.optLong("date") }.filter { it > 0 }.minOrNull()
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(cs.surfaceContainerLow).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Measurements", style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold), modifier = Modifier.weight(1f))
            first?.let { Text("since ${md.format(Date(it))}", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = cs.onSurfaceVariant) }
            Row(Modifier.clip(CircleShape).background(cs.surfaceContainerHigh).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                listOf("cm", "in").forEach { o ->
                    val on = lu == o
                    val bg by animateColorAsState(if (on) cs.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent, com.pursuitiron.app.ui.theme.Motion.effects(), label = "lu")
                    Box(Modifier.height(32.dp).clip(CircleShape).background(bg).clickable { if (!on) vm.setLengthUnit(o) }.semantics { role = Role.RadioButton; stateDescription = if (on) "Selected" else "" }.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                        Text(o, style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold), color = if (on) cs.onSecondaryContainer else cs.onSurfaceVariant)
                    }
                }
            }
        }
        Column(Modifier.clip(RoundedCornerShape(18.dp)), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            MEASURES.forEach { k ->
                val rows = m.list(k).filter { it.d("v") > 0 }
                val name = k.replaceFirstChar { it.uppercase() }
                val last = rows.lastOrNull()?.d("v")
                val isOpen = open == k
                Column(Modifier.fillMaxWidth().background(cs.surfaceContainerHigh)) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(10.dp)).clickable(enabled = rows.size >= 2) { open = if (isOpen) null else k }
                            .semantics(mergeDescendants = true) { contentDescription = "$name trend"; stateDescription = if (isOpen) "Expanded" else "Collapsed" },
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(name, style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold))
                            if (rows.size >= 2) Icon(if (isOpen) PiIcons.ChevronUp else PiIcons.ChevronDown, null, Modifier.size(14.dp), tint = cs.onSurfaceVariant)
                        }
                        if (last != null) { val dl = last - rows.first().d("v")
                            Text(if (Math.abs(dl) < 0.001) "±0" else (if (dl > 0) "+" else "−") + fx(Math.abs(dl)), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = cs.onSurfaceVariant) }
                        Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(cs.surface).clickable { if (last == null) typing = k else vm.logMeasurement(k, Math.round((last - step) * 1000) / 1000.0) }
                            .semantics { contentDescription = "$name down ${if (inch) "a quarter inch" else "half a centimetre"}"; role = Role.Button }, contentAlignment = Alignment.Center) { Text("−") }
                        Text(last?.let { fx(it) } ?: "—", style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold), textAlign = TextAlign.Center,
                            modifier = Modifier.width(44.dp).clip(RoundedCornerShape(8.dp)).clickable { typing = k }.semantics { contentDescription = "$name ${last?.let { fx(it) + " " + lu } ?: "not logged"}. Tap to type" })
                        Box(Modifier.size(36.dp).clip(CircleShape).background(cs.surface).clickable { if (last == null) typing = k else vm.logMeasurement(k, Math.round((last + step) * 1000) / 1000.0) }
                            .semantics { contentDescription = "$name up ${if (inch) "a quarter inch" else "half a centimetre"}"; role = Role.Button }, contentAlignment = Alignment.Center) { Text("+") }
                    }
                    AnimatedVisibility(isOpen, enter = com.pursuitiron.app.ui.theme.Motion.expandIn, exit = com.pursuitiron.app.ui.theme.Motion.shrinkOut) {
                        val hist = rows.takeLast(6)
                        Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            val lineC = cs.primary
                            Canvas(Modifier.fillMaxWidth().height(40.dp).semantics { contentDescription = "$name over ${hist.size} entries: ${hist.joinToString(", ") { fx(it.d("v")) }} $lu" }) {
                                val vs = hist.map { it.d("v") }; val lo = vs.min(); val rg = (vs.max() - lo).takeIf { it > 0 } ?: 1.0
                                val pts = vs.mapIndexed { i, v -> Offset(i * size.width / (vs.size - 1).coerceAtLeast(1), (36 - ((v - lo) / rg * 30).toFloat()) * size.height / 40) }
                                drawPath(Path().apply { moveTo(pts[0].x, pts[0].y); pts.drop(1).forEach { lineTo(it.x, it.y) } }, lineC, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                hist.reversed().take(4).forEach { h ->
                                    Column(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(cs.surface).padding(vertical = 6.dp, horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("${fx(h.d("v"))} $lu", style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold))
                                        Text(md.format(Date(h.optLong("date"))), style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = cs.onSurfaceVariant)
                                    }
                                }
                                repeat(4 - hist.size.coerceAtMost(4)) { Box(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
            }
        }
    }
    typing?.let { k ->
        val last = m.list(k).lastOrNull()?.d("v")
        TypeValueDialog(k.replaceFirstChar { it.uppercase() }, lu, last?.let { fx(it) } ?: "", { vm.logMeasurement(k, it) }) { typing = null }
    }
}
