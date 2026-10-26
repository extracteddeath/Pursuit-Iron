package com.pursuitiron.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.contentDescription
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
import com.pursuitiron.app.ui.common.b
import com.pursuitiron.app.ui.common.d
import com.pursuitiron.app.ui.common.fmtNum
import com.pursuitiron.app.ui.common.i
import com.pursuitiron.app.ui.common.list
import com.pursuitiron.app.ui.common.s
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.max

/* A design full page (Main.dc.html .fsd): a borderless dialog over the app with a close button and title,
   a scrolling body, and an optional bottom bar on surfaceContainer. */
@Composable
fun DesignPage(title: String, onClose: () -> Unit, actions: (@Composable () -> Unit)? = null, bottom: (@Composable () -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val cs = MaterialTheme.colorScheme
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val lightBars = cs.surface.luminance() > 0.5f
        (androidx.compose.ui.platform.LocalView.current.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window?.let { w ->
            LaunchedEffect(w) {
                w.setDimAmount(0f)
                w.statusBarColor = android.graphics.Color.TRANSPARENT; w.navigationBarColor = android.graphics.Color.TRANSPARENT
                if (android.os.Build.VERSION.SDK_INT >= 29) { w.isStatusBarContrastEnforced = false; w.isNavigationBarContrastEnforced = false }
                androidx.core.view.WindowCompat.getInsetsController(w, w.decorView).isAppearanceLightStatusBars = lightBars
            }
        }
        Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().background(cs.surface).statusBarsPadding()) {
            Row(Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = onClose) { Icon(PiIcons.Close, "Close") }
                Text(title, style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp, fontWeight = FontWeight.SemiBold), modifier = Modifier.weight(1f))
                actions?.invoke()
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
            if (bottom != null) Box(Modifier.fillMaxWidth().background(cs.surfaceContainer).navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 18.dp)) { bottom() }
            else Box(Modifier.navigationBarsPadding())
        }
        com.pursuitiron.app.ui.PageOverlays(if (bottom != null) 96.dp else 24.dp)
        }
    }
}

@Composable
private fun RvHeading(t: String) = Text(t, style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, fontWeight = FontWeight.SemiBold), modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 6.dp))

/* Segmented list: 2dp gaps on one rounded group, like the design's grouped rows. */
@Composable
private fun RvGroup(content: @Composable ColumnScope.() -> Unit) =
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)), verticalArrangement = Arrangement.spacedBy(2.dp), content = content)

private fun pct(v: Double) = (if (v >= 0) "+" else "−") + String.format("%.1f", abs(v)) + "%"
private fun cap(s: String) = s.replaceFirstChar { it.uppercase() }

/* The design's "Block N review" (Main.dc.html reviewOn): sessions against the plan, which lifts went up or stalled,
   how each muscle's weekly sets lined up with its e1RM, sets against the personal range, what changes next block. */
@Composable
fun BlockReviewSheet(vm: AppViewModel, id: String, index: Int, onClose: () -> Unit) {
    var d by remember { mutableStateOf<JSONObject?>(null) }
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(id, index) { d = vm.reviewBlock(id, index); loaded = true }
    val cs = MaterialTheme.colorScheme
    val r = d
    val blockNo = (r?.optInt("blockIndex", index) ?: index) + 1
    val last = r != null && !r.b("standalone") && blockNo >= r.optInt("blocks", 1)
    DesignPage("Block $blockNo review", onClose, bottom = if (r == null) null else ({
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { onClose(); vm.openEditor(r.s("programId")) }, enabled = r.s("programId").isNotBlank(),
                modifier = Modifier.weight(1f).height(56.dp)) { Text("Edit in builder", fontWeight = FontWeight.Bold, maxLines = 1) }
            Button(onClick = { onClose(); if (r.b("standalone")) vm.nextBlock(r.s("programId")) else vm.advanceCycle(id) },
                modifier = Modifier.weight(1.4f).height(56.dp)) {
                Text(if (last) "Complete cycle" else "Start block ${blockNo + 1}", style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold), maxLines = 1)
            }
        }
    })) {
        when {
            !loaded -> {}
            r == null -> Text("This block couldn't be reviewed.", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            !r.b("ok") -> Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(32.dp)).background(cs.surfaceContainerLow).padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${r.i("sessions")}", style = MaterialTheme.typography.displaySmall.copy(fontSize = 34.sp, lineHeight = 38.sp))
                Text(r.s("reason", "Not enough logged sessions to say anything honest."), style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp), color = cs.onSurfaceVariant)
            }
            else -> ReviewBody(r)
        }
    }
}

@Composable
private fun ColumnScope.ReviewBody(r: JSONObject) {
    val cs = MaterialTheme.colorScheme
    val u = r.s("unit")
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(32.dp)).background(cs.primaryContainer).padding(20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        val planned = if (r.isNull("planned")) 0 else r.i("planned")
        listOf("${r.i("sessions")}" to (if (planned > 0) "sessions done of $planned planned" else "sessions done"),
            "${r.i("stronger")} of ${r.i("judged")}" to "lifts stronger by e1RM").forEach { (big, small) ->
            Column(Modifier.weight(1f)) {
                Text(big, style = MaterialTheme.typography.displaySmall.copy(fontSize = 34.sp, lineHeight = 38.sp), color = cs.onPrimaryContainer, maxLines = 1)
                Text(small, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 18.sp), color = cs.onPrimaryContainer)
            }
        }
    }
    val lifts = r.list("lifts")
    val up = lifts.filter { it.s("state") == "progressed" }
    val stall = lifts.filter { it.s("state") == "flat" || it.s("state") == "regressed" }
    fun range(l: JSONObject) = if (l.isNull("start") || l.isNull("end")) "" else "${fmtNum(l.d("start"))} → ${fmtNum(l.d("end"))} $u"
    if (up.isNotEmpty()) {
        RvHeading("Went up")
        RvGroup { up.forEach { l ->
            Row(Modifier.fillMaxWidth().background(cs.surfaceContainerLow).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(l.s("name"), style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(range(l), style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp), color = cs.onSurfaceVariant)
                Text(pct(l.d("deltaPct")), style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold), color = cs.primary, textAlign = TextAlign.End, modifier = Modifier.widthIn(min = 56.dp))
            }
        } }
    }
    if (stall.isNotEmpty()) {
        RvHeading("Stalled")
        RvGroup { stall.forEach { l ->
            Row(Modifier.fillMaxWidth().background(cs.surfaceContainerLow).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(l.s("name"), style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text((if (l.s("state") == "regressed") "e1RM down over ${l.i("sessions")} sessions" else "e1RM flat for ${l.i("sessions")} sessions") +
                        (if (l.isNull("end")) "" else " at ${fmtNum(l.d("end"))} $u"), style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = cs.onSurfaceVariant)
                }
                Text(pct(l.d("deltaPct")), style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold))
            }
        } }
    }
    val muscles = r.list("muscles")
    if (muscles.isNotEmpty()) {
        RvHeading("Volume vs outcome")
        RvGroup { muscles.forEach { m ->
            val trend = m.s("trend")
            val (tag, bg, fg) = when (trend) {
                "progressed" -> Triple("Responding", cs.primaryContainer, cs.onPrimaryContainer)
                "regressed" -> Triple("Down", cs.errorContainer, cs.onErrorContainer)
                "flat" -> Triple("Flat", cs.surfaceContainerHighest, cs.onSurfaceVariant)
                else -> Triple("Too few", cs.surfaceContainerHighest, cs.onSurfaceVariant)
            }
            val out = Regex("[+−-]?\\d+(\\.\\d+)?%").find(m.s("drove"))?.value?.let { "$it e1RM" }
            Column(Modifier.fillMaxWidth().background(cs.surfaceContainerLow).padding(horizontal = 14.dp, vertical = 12.dp)
                .semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(cap(m.s("part")), style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold))
                    Text("${fmtNum(m.d("avgSets"))} sets / wk" + (out?.let { " · $it" } ?: ""), style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = cs.onSurfaceVariant, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(tag, style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold), color = fg,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(bg).padding(horizontal = 10.dp, vertical = 4.dp))
                }
                if (m.s("rec").isNotBlank()) Text(m.s("rec"), style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp), color = cs.onSurfaceVariant)
            }
        } }
        RvHeading("Weekly sets vs your range")
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(cs.surfaceContainerLow).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val mx = muscles.maxOf { max(m(it, "mrv"), m(it, "avgSets")) }.coerceAtLeast(1.0) * 1.1
            muscles.forEach { v ->
                val at = m(v, "avgSets"); val lo = m(v, "mev"); val hi = m(v, "mrv")
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(cap(v.s("part")), style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold), modifier = Modifier.width(80.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    BoxWithConstraints(Modifier.weight(1f).height(20.dp).semantics {
                        contentDescription = "${cap(v.s("part"))}: ${fmtNum(at)} sets a week, range ${fmtNum(lo)} to ${fmtNum(hi)}" + if (at < lo) ", below range" else ""
                    }, contentAlignment = Alignment.CenterStart) {
                        val w = maxWidth
                        Box(Modifier.fillMaxWidth().height(14.dp).clip(CircleShape).background(cs.surfaceContainerHighest))
                        Box(Modifier.offset(x = w * (lo / mx).toFloat()).width(w * ((hi - lo).coerceAtLeast(0.0) / mx).toFloat()).height(14.dp).clip(CircleShape).background(cs.secondaryContainer))
                        Box(Modifier.offset(x = (w * (at / mx).toFloat() - 2.dp).coerceAtLeast(0.dp)).width(4.dp).height(20.dp).clip(RoundedCornerShape(2.dp)).background(cs.primary))
                    }
                    Text(fmtNum(Math.round(at).toDouble()), style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold), textAlign = TextAlign.End, modifier = Modifier.width(36.dp))
                }
            }
            Text("Shaded band: your minimum to maximum useful sets a week. Line: what you did.", style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp), color = cs.onSurfaceVariant)
        }
    }
    val recs = r.list("recs")
    if (recs.isNotEmpty()) {
        RvHeading("Next block")
        Text("Built in from this block's workouts. Change anything after in the builder.", style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp),
            color = cs.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp).offset(y = (-10).dp))
        RvGroup { recs.forEach { x ->
            Column(Modifier.fillMaxWidth().background(cs.surfaceContainerLow).padding(horizontal = 16.dp, vertical = 14.dp)) {
                Text(cap(x.s("part")), style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold))
                Text(x.s("rec"), style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 17.sp), color = cs.onSurfaceVariant)
            }
        } }
    }
}

private fun m(o: JSONObject, k: String) = o.optDouble(k, 0.0).let { if (it.isNaN()) 0.0 else it }
