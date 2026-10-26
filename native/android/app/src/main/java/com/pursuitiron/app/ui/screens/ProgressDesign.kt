@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.pursuitiron.app.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pursuitiron.app.AppViewModel
import com.pursuitiron.app.ui.common.PiIcons
import com.pursuitiron.app.ui.common.b
import com.pursuitiron.app.ui.common.d
import com.pursuitiron.app.ui.common.fmtNum
import com.pursuitiron.app.ui.common.list
import com.pursuitiron.app.ui.common.s
import com.pursuitiron.app.ui.theme.Motion
import org.json.JSONObject

/* The design's Progress → Trends middle: this week's sets per muscle against its landmarks, and the weekly audit. */

private fun sets(v: Double) = fmtNum(Math.round(v * 2) / 2.0)

@Composable
fun LandmarksCard(d: JSONObject) {
    val rows = d.list("rows"); if (rows.isEmpty()) return
    val cs = MaterialTheme.colorScheme
    val scale = (rows.maxOf { maxOf(it.d("mrv"), it.d("sets"), it.d("done")) } * 1.1).coerceAtLeast(1.0)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(32.dp)).background(cs.surfaceContainerLow).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Column {
            Text("Weekly sets vs landmarks", style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, fontWeight = FontWeight.SemiBold), modifier = Modifier.semantics { heading() })
            Text("This week: solid bar is done so far, faint bar is the plan. The band runs from MEV (least that grows) to MRV (most you recover from).",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp), color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
        }
        rows.forEach { r ->
            val planned = r.d("sets"); val done = r.d("done"); val mev = r.d("mev"); val mrv = r.d("mrv")
            val status = when { planned < mev -> "under MEV"; planned > mrv -> "over MRV"; planned > (mev + mrv) / 2 -> "high"; else -> "productive" }
            val col = when { planned < mev -> cs.onSurfaceVariant; planned > mrv -> cs.onTertiaryContainer; else -> cs.primary }
            val pos = { v: Double -> (v / scale).toFloat().coerceIn(0f, 1f) }
            val fill by animateFloatAsState(pos(done), Motion.data(), label = "lm")
            Column(Modifier.semantics(mergeDescendants = true) { contentDescription = "${r.s("label")}: ${sets(done)} done, ${sets(planned)} planned, $status. MEV ${fmtNum(mev)}, MRV ${fmtNum(mrv)}" },
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row {
                    Text(r.s("label").replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.weight(1f))
                    Text("${sets(done)}", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                    Text(" done · ${sets(planned)} planned · $status", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                }
                BoxWithConstraints(Modifier.fillMaxWidth().height(14.dp).clip(CircleShape).background(cs.surfaceContainerHighest)) {
                    val w = maxWidth
                    Box(Modifier.offset(x = w * pos(mev)).width(w * (pos(mrv) - pos(mev))).fillMaxHeight().clip(CircleShape).background(cs.primaryContainer))
                    Box(Modifier.padding(3.dp).width(maxOf(8.dp, w * pos(planned) - 6.dp)).fillMaxHeight().clip(CircleShape).background(col).alpha(0.28f))
                    Box(Modifier.padding(3.dp).width(maxOf(8.dp, w * fill - 6.dp)).fillMaxHeight().clip(CircleShape).background(col))
                    Box(Modifier.offset(x = w * pos(mev)).width(2.dp).fillMaxHeight().background(cs.onSurface.copy(alpha = 0.5f)))
                    Box(Modifier.offset(x = w * pos(mrv) - 2.dp).width(2.dp).fillMaxHeight().background(cs.onSurface.copy(alpha = 0.5f)))
                }
            }
        }
    }
}

@Composable
fun AuditCard(vm: AppViewModel, d: JSONObject) {
    val cs = MaterialTheme.colorScheme
    val issues = d.list("issues")
    var preview by remember { mutableStateOf<JSONObject?>(null) }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(cs.surfaceContainerLow).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Weekly audit", style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, fontWeight = FontWeight.SemiBold), modifier = Modifier.weight(1f).semantics { heading() })
            Text(if (issues.isEmpty()) "All clear" else "${issues.size} to fix", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                color = if (issues.isEmpty()) cs.onPrimary else cs.onTertiaryContainer,
                modifier = Modifier.clip(CircleShape).background(if (issues.isEmpty()) cs.primary else cs.tertiaryContainer).padding(horizontal = 10.dp, vertical = 4.dp))
        }
        issues.forEach { i ->
            val under = i.s("status") == "under"
            val none = under && i.d("sets") <= 0.05
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(cs.surface).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(36.dp).clip(if (none) RoundedCornerShape(12.dp) else CircleShape).background(if (none) cs.secondaryContainer else cs.tertiaryContainer), contentAlignment = Alignment.Center) {
                    Icon(if (none) PiIcons.Plus else if (under) PiIcons.ChevronUp else PiIcons.ChevronDown, null, Modifier.size(18.dp), tint = if (none) cs.onSecondaryContainer else cs.onTertiaryContainer)
                }
                Column(Modifier.weight(1f)) {
                    Text(issueTitle(i), style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold))
                    Text(issueDetail(i), style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 17.sp, fontWeight = FontWeight.Normal), color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
                    if (d.b("canRepair")) Box(Modifier.padding(top = 8.dp).height(36.dp).clip(CircleShape).background(cs.secondaryContainer).clickable { preview = i }
                        .semantics { role = Role.Button }.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                        Text("Preview fix", style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold), color = cs.onSecondaryContainer)
                    }
                }
            }
        }
        if (issues.isEmpty()) Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(cs.primaryContainer).padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(PiIcons.Check, null, Modifier.size(20.dp), tint = cs.onPrimaryContainer)
            Text("Every muscle sits between its MEV and MRV across the planned week.", style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 18.sp), color = cs.onPrimaryContainer)
        }
        Text("Checks the full planned week · ${if (issues.any { it.s("status") != "under" }) "something over MRV" else "nothing over MRV"}", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
    }
    preview?.let { i ->
        ModalBottomSheet(onDismissRequest = { preview = null }) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(issueTitle(i), style = MaterialTheme.typography.headlineSmall)
                Text(issueDetail(i), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(cs.surfaceContainerHigh).padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("THE FIX", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold), color = cs.onSurfaceVariant)
                    Text(i.s("fix").ifBlank { "Rebalance this week's sets so ${i.s("label")} lands inside its range, without pushing another muscle past its MRV." },
                        style = MaterialTheme.typography.bodyMedium)
                    Text("The audit repairs the whole planned week at once, and you can undo it.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
                Box(Modifier.fillMaxWidth().height(52.dp).clip(CircleShape).background(cs.primary).clickable { vm.repairVolume(d.s("programId")); preview = null }.semantics { role = Role.Button }, contentAlignment = Alignment.Center) {
                    Text("Apply fix", style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold), color = cs.onPrimary)
                }
            }
        }
    }
}

private fun issueTitle(i: JSONObject): String {
    val l = i.s("label").replaceFirstChar { it.uppercase() }
    return when { i.s("status") != "under" -> "$l over MRV"; i.d("sets") <= 0.05 -> "$l not trained"; else -> "$l under MEV" }
}
private fun issueDetail(i: JSONObject): String =
    if (i.s("status") != "under") "Planned ${sets(i.d("sets"))} sets this week, MRV is ${i.s("mrv")}. More than you can recover from."
    else "Planned ${sets(i.d("sets"))} sets this week, MEV is ${i.s("mev")}." + if (i.d("sets") <= 0.05) " Nothing in the week trains it directly." else ""

/** Loads this week's volume view once for both cards. */
@Composable
fun WeekLandmarksAndAudit(vm: AppViewModel, programId: String, week: Int, content: @Composable (JSONObject) -> Unit) {
    var data by remember(programId, week) { mutableStateOf<JSONObject?>(null) }
    LaunchedEffect(programId, week, vm.plan, vm.history.size) { data = vm.weeklyVolume(programId, week) }
    data?.let { content(it) }
}
