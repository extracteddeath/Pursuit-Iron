package com.pursuitiron.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pursuitiron.app.AppViewModel
import com.pursuitiron.app.ui.common.PiIcons
import com.pursuitiron.app.ui.common.b
import com.pursuitiron.app.ui.common.i
import com.pursuitiron.app.ui.common.list
import com.pursuitiron.app.ui.common.o
import com.pursuitiron.app.ui.common.s
import com.pursuitiron.app.ui.common.strings
import org.json.JSONObject

/* The design's fatigue pieces on Today: the deload card, the fresher-day card and its comparison sheet,
   and the readiness tile opening into the muscle map. Numbers come from the engine through home(). */

private fun pill() = RoundedCornerShape(999.dp)

/** The design's "A deload looks due": what the evidence is (kind · first sentence), deload now, or Not yet (until another session). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DeloadDueCard(vm: AppViewModel, h: JSONObject) {
    val d = h.o("deload") ?: return
    if (h.s("lastSessionId").isNotEmpty() && vm.prefs.deloadHiddenFor == h.s("lastSessionId")) return
    val cs = MaterialTheme.colorScheme
    Surface(color = cs.tertiaryContainer, contentColor = cs.onTertiaryContainer, shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(PiIcons.Pulse, null, Modifier.size(22.dp))
                Text("A deload looks due", style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp))
            }
            Text("Fatigue is outpacing recovery. A lighter week, about half the sets and 10% less load, lets progress rebound.",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp))
            val rows = d.list("rows")
            if (rows.isNotEmpty()) Column(Modifier.clip(RoundedCornerShape(18.dp)), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                rows.forEach { r ->
                    Row(Modifier.fillMaxWidth().background(cs.surface).padding(horizontal = 12.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(r.s("k"), style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, fontWeight = FontWeight.ExtraBold), color = cs.onSurface, modifier = Modifier.width(86.dp))
                        Text(r.s("v"), style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp), color = cs.onSurfaceVariant, modifier = Modifier.weight(1f))
                    }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (h.b("canDeload")) Surface(onClick = { vm.startDeload() }, color = cs.onTertiaryContainer, contentColor = cs.tertiaryContainer, shape = pill(), modifier = Modifier.height(48.dp)) {
                    Box(Modifier.padding(horizontal = 20.dp), contentAlignment = Alignment.Center) { Text("Train this week’s deload now", style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp)) }
                }
                Surface(onClick = {
                    val id = h.s("lastSessionId"); vm.prefs.hideDeload(id)
                    vm.toast("Hidden until the next session. It comes back if fatigue keeps building.") { vm.prefs.hideDeload("") }
                }, color = Color.Transparent, contentColor = cs.onTertiaryContainer, shape = pill(), modifier = Modifier.height(48.dp)) {
                    Box(Modifier.padding(horizontal = 16.dp), contentAlignment = Alignment.Center) { Text("Not yet", style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold)) }
                }
            }
        }
    }
}

private fun joinNames(xs: List<String>): String = when (xs.size) { 0 -> ""; 1 -> xs[0]; 2 -> "${xs[0]} and ${xs[1]}"; else -> xs.dropLast(1).joinToString(", ") + " and " + xs.last() }

/** The design's fresher-day card under the hero: names the muscles still recovering, opens the comparison. */
@Composable
fun FresherDayCard(vm: AppViewModel, h: JSONObject, onOpen: () -> Unit) {
    val alt = h.o("alt") ?: return
    if (h.o("live") != null) return
    if (h.s("lastSessionId").isNotEmpty() && vm.prefs.altKeptFor == h.s("lastSessionId")) return
    val cs = MaterialTheme.colorScheme
    val sore = alt.o("today")?.strings("recovering").orEmpty().take(3).map { it.lowercase() }
    Surface(onClick = onOpen, color = cs.secondaryContainer, contentColor = cs.onSecondaryContainer, shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(cs.surface), contentAlignment = Alignment.Center) { Icon(PiIcons.SwapDays, null, Modifier.size(20.dp), tint = cs.primary) }
            Column(Modifier.weight(1f)) {
                Text("Fresher today: ${alt.s("dayLabel")} · ${alt.i("score")}%", style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp))
                Text((if (sore.isNotEmpty()) "Your ${joinNames(sore)} ${if (sore.size == 1) "is" else "are"} still recovering." else "Today’s muscles are still recovering.") + " Swap days, or go light.",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp))
            }
            Icon(PiIcons.ChevronRight, null, Modifier.size(18.dp))
        }
    }
}

/** The design's fresher-day sheet: both sessions' muscle readiness, then start the fresher day, go light, or keep today. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FresherDaySheet(vm: AppViewModel, h: JSONObject, onClose: () -> Unit) {
    val alt = h.o("alt") ?: run { onClose(); return }
    val today = alt.o("today") ?: JSONObject()
    val cs = MaterialTheme.colorScheme
    ModalBottomSheet(onDismissRequest = onClose) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("A fresher option today", style = MaterialTheme.typography.headlineSmall)
            Text("Today’s plan trains muscles that are still recovering. ${alt.s("dayLabel")} trains different ones, so the week still adds up.",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp), color = cs.onSurfaceVariant)
            Surface(color = cs.surfaceContainerHigh, shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(Triple(today.s("dayLabel"), "Planned · " + today.list("parts").joinToString(", ") { it.s("label").lowercase() }, today.i("score")) to cs.onTertiaryContainer,
                        Triple(alt.s("dayLabel"), alt.list("parts").joinToString(", ") { it.s("label") }.replaceFirstChar { it.uppercase() }, alt.i("score")) to cs.primary).forEach { (row, color) ->
                        Column(Modifier.semantics(mergeDescendants = true) { contentDescription = "${row.first}: ${row.third} percent ready" }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Column(Modifier.weight(1f)) {
                                    Text(row.first, style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp))
                                    Text(row.second, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = cs.onSurfaceVariant, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                }
                                Text("${row.third}%", style = MaterialTheme.typography.headlineSmall.copy(fontSize = 22.sp))
                            }
                            com.pursuitiron.app.ui.common.ProgressBar(row.third / 100f, Modifier.fillMaxWidth(), color = color, track = cs.surface, height = 10.dp)
                        }
                    }
                    Text("Readiness of the muscles each session trains.", style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = cs.onSurfaceVariant)
                }
            }
            Surface(onClick = { onClose(); vm.startAlt() }, color = cs.primary, contentColor = cs.onPrimary, shape = CircleShape, modifier = Modifier.fillMaxWidth().height(54.dp)) {
                Box(contentAlignment = Alignment.Center) { Text("Start ${alt.s("dayLabel")} instead", style = MaterialTheme.typography.titleSmall.copy(fontSize = 16.sp)) }
            }
            if (h.b("canLight")) Surface(onClick = { onClose(); vm.startLight() }, color = cs.secondaryContainer, contentColor = cs.onSecondaryContainer, shape = CircleShape, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                Box(contentAlignment = Alignment.Center) { Text("Do a light session instead", style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold)) }
            }
            Surface(onClick = {
                vm.prefs.keepToday(h.s("lastSessionId")); onClose()
                vm.toast("Keeping ${today.s("dayLabel")} today. Trimmed sets show why.") { vm.prefs.keepToday("") }
            }, color = Color.Transparent, contentColor = cs.primary, shape = CircleShape, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Box(contentAlignment = Alignment.Center) { Text("Keep ${today.s("dayLabel")} today", style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold)) }
            }
        }
    }
}

/* The design's body map (front and back, 260×226): base silhouette rects, then the muscles, darker the more fatigued. */
private val BODY_BASE = listOf(floatArrayOf(36f, 38f, 48f, 72f, 16f), floatArrayOf(20f, 42f, 14f, 66f, 7f), floatArrayOf(86f, 42f, 14f, 66f, 7f), floatArrayOf(40f, 110f, 19f, 98f, 9f), floatArrayOf(61f, 110f, 19f, 98f, 9f))
private val BODY_MUSCLES = listOf(
    "Shoulders" to floatArrayOf(26f, 40f, 16f, 16f, 8f), "Shoulders" to floatArrayOf(78f, 40f, 16f, 16f, 8f), "Chest" to floatArrayOf(40f, 46f, 19f, 20f, 8f), "Chest" to floatArrayOf(61f, 46f, 19f, 20f, 8f),
    "Biceps" to floatArrayOf(21f, 60f, 12f, 26f, 6f), "Biceps" to floatArrayOf(87f, 60f, 12f, 26f, 6f), "Quads" to floatArrayOf(42f, 118f, 15f, 48f, 7f), "Quads" to floatArrayOf(63f, 118f, 15f, 48f, 7f),
    "Shoulders" to floatArrayOf(156f, 40f, 16f, 16f, 8f), "Shoulders" to floatArrayOf(208f, 40f, 16f, 16f, 8f), "Back" to floatArrayOf(170f, 46f, 40f, 44f, 14f), "Triceps" to floatArrayOf(151f, 60f, 12f, 26f, 6f),
    "Triceps" to floatArrayOf(217f, 60f, 12f, 26f, 6f), "Glutes" to floatArrayOf(171f, 106f, 38f, 20f, 9f), "Hamstrings" to floatArrayOf(172f, 130f, 15f, 44f, 7f), "Hamstrings" to floatArrayOf(193f, 130f, 15f, 44f, 7f))
/** Engine parts behind each region of the map; Back is the lower of lats and upper back. */
private val REGION_PARTS = mapOf("Shoulders" to listOf("shoulders"), "Chest" to listOf("chest"), "Biceps" to listOf("biceps"), "Quads" to listOf("quads"),
    "Back" to listOf("lats", "upper_back"), "Triceps" to listOf("triceps"), "Glutes" to listOf("glutes"), "Hamstrings" to listOf("hamstrings"))

@Composable
private fun BodyMap(ready: Map<String, Int>, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val region = REGION_PARTS.mapValues { (_, ps) -> ps.minOf { ready[it] ?: 100 } }
    val aria = "Fatigue map. " + region.entries.joinToString(", ") { "${it.key} ${it.value}% ready" }
    Canvas(modifier.semantics { contentDescription = aria }) {
        val k = minOf(size.width / 260f, size.height / 226f)
        val ox = (size.width - 260f * k) / 2f
        fun rr(a: FloatArray) = Triple(Offset(ox + a[0] * k, a[1] * k), Size(a[2] * k, a[3] * k), CornerRadius(a[4] * k))
        (BODY_BASE + BODY_BASE.map { floatArrayOf(it[0] + 130f, it[1], it[2], it[3], it[4]) }).forEach { b ->
            val (o, s, r) = rr(b); drawRoundRect(cs.surfaceContainerHigh, o, s, r); drawRoundRect(cs.outlineVariant, o, s, r, style = Stroke(1f * k))
        }
        listOf(60f, 190f).forEach { cx -> drawCircle(cs.surfaceContainerHigh, 13f * k, Offset(ox + cx * k, 20f * k)); drawCircle(cs.outlineVariant, 13f * k, Offset(ox + cx * k, 20f * k), style = Stroke(1f * k)) }
        BODY_MUSCLES.forEach { (name, a) ->
            val v = region[name] ?: 100
            val (o, s, r) = rr(a)
            drawRoundRect(cs.primary.copy(alpha = 0.12f + (100 - v) / 100f * 0.88f), o, s, r)
            if (v < 65) { // the design's hatch: under 65% ready
                val clip = Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(o.x, o.y, o.x + s.width, o.y + s.height, r)) }
                clipPath(clip, ClipOp.Intersect) {
                    var x = o.x - s.height
                    while (x < o.x + s.width) { drawLine(cs.surface.copy(alpha = 0.85f), Offset(x, o.y + s.height), Offset(x + s.height, o.y), strokeWidth = 1.6f * k); x += 5f * k }
                }
            }
        }
    }
}

/** The design's readiness tile opened in place: today's muscles' readiness, the map, every trained muscle, and a light session when most are spent. */
@Composable
fun ReadinessPanel(vm: AppViewModel, h: JSONObject, onClose: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val rd = h.o("readiness") ?: JSONObject()
    val today = rd.strings("todayParts").toSet()
    val parts = rd.list("parts")
    val ready = parts.associate { it.s("part") to it.i("readiness") }
    val shown = parts.filter { it.i("readiness") < 100 || it.s("part") in today }.sortedWith(compareBy({ it.s("part") !in today }, { it.i("readiness") }))
    Surface(color = cs.tertiaryContainer, contentColor = cs.onTertiaryContainer, shape = RoundedCornerShape(32.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Muscle readiness", style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("${readinessToday(vm, h)}%", style = MaterialTheme.typography.displaySmall.copy(fontSize = 40.sp, lineHeight = 44.sp))
                        Text(" for today’s muscles", style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp), modifier = Modifier.padding(bottom = 6.dp))
                    }
                }
                Surface(onClick = onClose, color = cs.surface, contentColor = cs.onSurface, shape = RoundedCornerShape(14.dp), modifier = Modifier.size(44.dp)) {
                    Box(contentAlignment = Alignment.Center) { Icon(PiIcons.ChevronUp, "Collapse readiness", Modifier.size(20.dp)) }
                }
            }
            Surface(color = cs.surface, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    BodyMap(ready, Modifier.fillMaxWidth().height(200.dp))
                    Row(Modifier.fillMaxWidth().padding(horizontal = 40.dp)) {
                        Text("FRONT", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold), color = cs.onSurfaceVariant, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        Text("BACK", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold), color = cs.onSurfaceVariant, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally)) {
                listOf("Recovered" to 0.15f, "Fatigued" to 1f).forEach { (l, a) ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.size(12.dp).clip(RoundedCornerShape(4.dp)).background(cs.primary.copy(alpha = a)))
                        Text(l, style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold))
                    }
                }
                Text("Hatched: under 65%", style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold))
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                shown.forEach { m ->
                    val v = m.i("readiness"); val bold = m.s("part") in today
                    Row(Modifier.semantics(mergeDescendants = true) { contentDescription = "${m.s("label")} $v percent ready" + if (bold) ", trained today" else "" },
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(m.s("label"), style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, fontWeight = if (bold) FontWeight.ExtraBold else FontWeight.Medium), modifier = Modifier.width(96.dp), maxLines = 1)
                        com.pursuitiron.app.ui.common.ProgressBar(v / 100f, Modifier.weight(1f), color = if (v >= 85) cs.primary else if (v >= 60) cs.onTertiaryContainer else cs.outlineVariant, track = cs.surface, height = 12.dp)
                        Text("$v%", style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp), modifier = Modifier.width(44.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End)
                    }
                }
            }
            Text("Estimated from when each muscle was last trained, how many sets and how close to failure you took it. Bold rows are trained today. Deleting a logged session resets its fatigue.",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 17.sp))
            val rec = h.o("recovery")
            if (rec?.b("restAdvised") == true && h.b("canLight")) {
                Text("Most muscles are still fatigued. Rest, or do a light session: about half the work at easy effort.", style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp))
                Surface(onClick = { vm.startLight() }, color = cs.onTertiaryContainer, contentColor = cs.tertiaryContainer, shape = CircleShape, modifier = Modifier.height(48.dp)) {
                    Box(Modifier.padding(horizontal = 20.dp), contentAlignment = Alignment.Center) { Text("Do a light session instead", style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp)) }
                }
            }
        }
    }
}
