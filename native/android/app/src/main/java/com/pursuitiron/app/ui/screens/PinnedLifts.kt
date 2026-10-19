@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.pursuitiron.app.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pursuitiron.app.AppViewModel
import com.pursuitiron.app.ui.common.Kicker
import com.pursuitiron.app.ui.common.PiIcons
import com.pursuitiron.app.ui.common.Sparkline
import com.pursuitiron.app.ui.common.d
import com.pursuitiron.app.ui.common.fmtNum
import com.pursuitiron.app.ui.common.i
import com.pursuitiron.app.ui.common.list
import com.pursuitiron.app.ui.common.o
import com.pursuitiron.app.ui.common.s
import java.text.DateFormat
import java.util.Date

/**
 * The design's "Pinned lifts" (web: chart favourites): up to six record cards, two to a row, first on
 * Progress. Each shows the record set, its e1RM and date, and the trend since the first session.
 * The first and fourth cards carry the design's cut corner and container colours.
 */
@Composable
fun PinnedLiftsSection(vm: AppViewModel, onOpen: (String) -> Unit, onManage: () -> Unit) {
    val pins = vm.progress?.o("pins") ?: return
    val cs = MaterialTheme.colorScheme
    val lifts = pins.list("lifts")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Pinned lifts", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Surface(onClick = onManage, shape = RoundedCornerShape(999.dp), color = cs.surfaceContainerHigh, modifier = Modifier.semantics { contentDescription = "Manage pinned lifts" }) {
                Text("Manage", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
            }
        }
        if (lifts.isEmpty()) {
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).border(1.5.dp, cs.outlineVariant, RoundedCornerShape(24.dp))
                    .clickable(role = Role.Button, onClick = onManage).padding(18.dp),
            ) {
                Text(
                    if (pins.list("options").isEmpty()) "Pinned lifts show here once a lift has two sessions." else "Pin the lifts you care about to keep their trend here.",
                    style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant,
                )
            }
            return@Column
        }
        val big = 28.dp; val cut = 10.dp
        lifts.chunked(2).forEachIndexed { row, pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEachIndexed { col, l ->
                    val k = (row * 2 + col) % 4
                    val shape = when (k) {
                        0 -> RoundedCornerShape(big, big, big, cut)
                        3 -> RoundedCornerShape(cut, big, big, big)
                        else -> RoundedCornerShape(big)
                    }
                    val (bg, fg) = when (k) {
                        0 -> cs.primaryContainer to cs.onPrimaryContainer
                        3 -> cs.tertiaryContainer to cs.onTertiaryContainer
                        else -> cs.surfaceContainerHigh to cs.onSurface
                    }
                    val pts = l.list("points").map { it.d("e1rm") }
                    val ch = l.optDouble("changePct").takeIf { !it.isNaN() }
                    val date = l.optLong("date").takeIf { it > 0 }?.let { DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it)) }
                    val u = vm.progress?.s("unit").orEmpty()
                    val trendWords = ch?.let { (if (it >= 0) "up " else "down ") + fmtNum(kotlin.math.abs(it)) + " percent over ${pts.size} sessions" } ?: ""
                    Surface(
                        onClick = { onOpen(l.s("id")) }, shape = shape, color = bg, contentColor = fg,
                        modifier = Modifier.weight(1f).semantics(mergeDescendants = true) {
                            contentDescription = "${l.s("name")} record ${l.s("heavy")}, e1RM ${fmtNum(l.d("e1rm"))} $u. $trendWords. Open lift history"
                        },
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(PiIcons.Trophy, null, Modifier.size(22.dp))
                            Text(l.s("name"), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(l.s("heavy").ifBlank { "${fmtNum(l.d("e1rm"))} $u" }, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("e1RM ${fmtNum(l.d("e1rm"))}" + (date?.let { " · $it" } ?: ""), style = MaterialTheme.typography.labelSmall, modifier = Modifier.alpha(0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (pts.size >= 2) Sparkline(pts, Modifier.weight(1f).height(28.dp), color = fg)
                                else Text("One session so far", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f).alpha(0.8f))
                                ch?.let { Text((if (it >= 0) "+" else "−") + fmtNum(kotlin.math.abs(it)) + "%", style = MaterialTheme.typography.labelLarge) }
                            }
                        }
                    }
                }
                if (pair.size == 1) Box(Modifier.weight(1f))
            }
        }
    }
}

/** The design's pins panel: reorder, unpin, and add from every lift that has a trend. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PinsSheet(vm: AppViewModel, onClose: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        val pins = vm.progress?.o("pins")
        val lifts = pins?.list("lifts").orEmpty(); val options = pins?.list("options").orEmpty()
        val max = pins?.i("max")?.takeIf { it > 0 } ?: 6
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Pinned lifts", style = MaterialTheme.typography.headlineSmall)
            Text("${lifts.size} of $max pinned. They show first on Progress, in this order.", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            if (lifts.isNotEmpty()) Column(Modifier.clip(RoundedCornerShape(22.dp)), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                lifts.forEachIndexed { k, l ->
                    val name = l.s("name"); val id = l.s("id")
                    Surface(color = cs.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            IconButton(onClick = { vm.movePin(id, -1) }, enabled = k > 0) { Icon(PiIcons.ChevronUp, "Move $name up") }
                            IconButton(onClick = { vm.movePin(id, 1) }, enabled = k < lifts.size - 1) { Icon(PiIcons.ChevronDown, "Move $name down") }
                            IconButton(onClick = { vm.unpinLift(id) }) { Icon(PiIcons.Close, "Unpin $name", Modifier.size(18.dp), tint = cs.onSurfaceVariant) }
                        }
                    }
                }
            }
            if (options.isNotEmpty()) {
                Kicker("Add a lift")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    options.forEach { o ->
                        Surface(
                            onClick = { vm.pinLift(o.s("id")) }, shape = RoundedCornerShape(999.dp), color = cs.surfaceContainerHigh,
                            modifier = Modifier.semantics { contentDescription = "Pin ${o.s("name")}" },
                        ) {
                            Row(Modifier.padding(start = 10.dp, end = 14.dp).height(40.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(PiIcons.Plus, null, Modifier.size(16.dp))
                                Text(o.s("name"), style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            } else if (lifts.isEmpty()) Text("Log a lift in two sessions to pin its trend.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
    }
}

/** Pin toggle for the lift detail sheet. Hidden for lifts without a trend yet. */
@Composable
fun PinToggle(vm: AppViewModel, id: String) {
    val pins = vm.progress?.o("pins") ?: return
    val pinned = pins.list("lifts").any { it.s("id") == id }
    if (!pinned && pins.list("options").none { it.s("id") == id }) return
    FilledTonalButton(onClick = { if (pinned) vm.unpinLift(id) else vm.pinLift(id) }) {
        Icon(if (pinned) PiIcons.Check else PiIcons.Pin, null, Modifier.size(18.dp))
        Text(if (pinned) "  Pinned to Progress" else "  Pin to Progress")
    }
}
