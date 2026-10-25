package com.pursuitiron.app.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.pursuitiron.app.AppViewModel
import com.pursuitiron.app.ui.common.Kicker
import com.pursuitiron.app.ui.common.MorphChip
import com.pursuitiron.app.ui.common.PiIcons
import com.pursuitiron.app.ui.common.Segmented
import com.pursuitiron.app.ui.common.b
import com.pursuitiron.app.ui.common.fmt1
import com.pursuitiron.app.ui.common.i
import com.pursuitiron.app.ui.common.list
import com.pursuitiron.app.ui.common.s
import com.pursuitiron.app.ui.common.volUnit
import kotlinx.coroutines.delay
import org.json.JSONObject

/* The design's history (web): search, time and program filters, grouped by cycle or month with each
   group's sessions and volume. Filters change this list only; charts and totals still use all history. */
@Composable
fun HistoryList(vm: AppViewModel, day: Triple<Int, Int, Int>? = null, onOpen: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    var q by rememberSaveable { mutableStateOf("") }
    var days by rememberSaveable { mutableStateOf("all") }
    var prog by rememberSaveable { mutableStateOf("all") }
    var by by rememberSaveable { mutableStateOf("cycle") }
    var d by remember { mutableStateOf<JSONObject?>(null) }
    LaunchedEffect(q, days, prog, by, vm.history.size) { delay(if (q.isNotEmpty()) 180 else 0); d = vm.historyGroups(q, days, prog, by) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(), singleLine = true, placeholder = { Text("Find a workout — lift, day or note") },
            shape = CircleShape, leadingIcon = { Icon(PiIcons.Focus, null) },
            trailingIcon = { if (q.isNotEmpty()) TextButton(onClick = { q = "" }) { Text("Clear") } },
            colors = TextFieldDefaults.colors(focusedContainerColor = cs.surfaceContainerHigh, unfocusedContainerColor = cs.surfaceContainerHigh, unfocusedIndicatorColor = Color.Transparent))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("all" to "All time", "30" to "Last 30 days", "90" to "Last 90 days", "365" to "Last year").forEach { (k, l) -> MorphChip(l, days == k, { days = k }, height = 36.dp) }
        }
        val x = d ?: return@Column
        val programs = x.list("programs")
        if (programs.size > 1) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MorphChip("All programs", prog == "all", { prog = "all" }, height = 36.dp)
            programs.forEach { p -> MorphChip(p.s("name"), prog == p.s("id"), { prog = p.s("id") }, height = 36.dp) }
        }
        Segmented(listOf("cycle" to "By cycle", "month" to "By month"), by, { by = it }, height = 40.dp)
        if (x.b("filtered")) Text("Filters change this list only; charts and lifetime totals still use all history.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        if (x.i("shown") == 0) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("No workouts match", style = MaterialTheme.typography.titleSmall)
                TextButton(onClick = { q = ""; days = "all"; prog = "all" }) { Text("Clear filters") }
            }
        }
        val u = x.s("unit", "kg")
        val onDay = { r: JSONObject -> day == null || java.util.Calendar.getInstance().apply { timeInMillis = r.optLong("date") }.let { c -> c.get(java.util.Calendar.YEAR) == day.first && c.get(java.util.Calendar.MONTH) == day.second && c.get(java.util.Calendar.DAY_OF_MONTH) == day.third } }
        x.list("groups").map { g -> g to g.list("items").filter(onDay) }.filter { it.second.isNotEmpty() }.forEach { (g, items) ->
            Row(Modifier.fillMaxWidth().padding(top = 6.dp, start = 4.dp), verticalAlignment = Alignment.Bottom) {
                Kicker(g.s("label"), modifier = Modifier.weight(1f))
                Text("${if (day != null) items.size else g.i("count")} session${if ((if (day != null) items.size else g.i("count")) == 1) "" else "s"}" + (g.i("volume").takeIf { it > 0 }?.let { " · ${fmt1(it / 1000.0)} ${volUnit(u)}" } ?: ""),
                    style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
            }
            Column(Modifier.clip(RoundedCornerShape(18.dp)), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items.forEach { r -> HistoryRow(r) { onOpen(r.s("id")) } }
            }
        }
    }
}


/* The design's month calendar on History: Monday first, trained days filled, today ringed, tap a trained
   day to see only its sessions (the list below follows). */
@Composable
fun MonthCalendar(vm: AppViewModel, sel: Triple<Int, Int, Int>?, onSelect: (Triple<Int, Int, Int>?) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val now = remember { java.util.Calendar.getInstance() }
    var off by rememberSaveable { mutableStateOf(0) }
    val first = java.util.Calendar.getInstance().apply { set(java.util.Calendar.DAY_OF_MONTH, 1); add(java.util.Calendar.MONTH, off) }
    val y = first.get(java.util.Calendar.YEAR); val m = first.get(java.util.Calendar.MONTH)
    var cal by remember { mutableStateOf<JSONObject?>(null) }
    LaunchedEffect(y, m, vm.history.size) { cal = vm.calendar(y, m) }
    val counts = cal?.list("days")?.associate { it.i("day") to it.list("sessions").size } ?: emptyMap()
    val nDays = first.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
    val lead = (first.get(java.util.Calendar.DAY_OF_WEEK) + 5) % 7
    val monthName = java.text.DateFormatSymbols.getInstance().months[m]
    val shortMonth = java.text.DateFormatSymbols.getInstance().shortMonths[m]
    val atNow = off >= 0
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(cs.surfaceContainerLow).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.IconButton(onClick = { off -= 1; onSelect(null) }) { Icon(PiIcons.ChevronLeft, "Previous month") }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$monthName $y", style = MaterialTheme.typography.titleMedium)
                val n = counts.values.sum()
                Text(if (n == 0) "No workouts this month" else "$n workout${if (n == 1) "" else "s"} this month", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
            }
            androidx.compose.material3.IconButton(onClick = { if (!atNow) { off += 1; onSelect(null) } }, modifier = Modifier.alpha(if (atNow) 0.35f else 1f)) { Icon(PiIcons.ChevronRight, "Next month") }
        }
        Row { listOf("M", "T", "W", "T", "F", "S", "S").forEach { Text(it, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.weight(1f)) } }
        val rows = (lead + nDays + 6) / 7
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for (r in 0 until rows) Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (k in 0 until 7) {
                    val d = r * 7 + k - lead + 1
                    if (d < 1 || d > nDays) { androidx.compose.foundation.layout.Spacer(Modifier.weight(1f).height(40.dp)); continue }
                    val n = counts[d] ?: 0
                    val isToday = atNow && d == now.get(java.util.Calendar.DAY_OF_MONTH)
                    val future = atNow && d > now.get(java.util.Calendar.DAY_OF_MONTH)
                    val on = sel == Triple(y, m, d)
                    val radius by androidx.compose.animation.core.animateDpAsState(if (on) 12.dp else 20.dp, com.pursuitiron.app.ui.theme.Motion.spring(), label = "day")
                    val bg by androidx.compose.animation.animateColorAsState(if (on) cs.primary else if (n > 0) cs.primaryContainer else Color.Transparent, label = "dayBg")
                    androidx.compose.foundation.layout.Box(Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(radius)).background(bg)
                        .then(if (isToday && !on) Modifier.border(2.dp, cs.primary, RoundedCornerShape(radius)) else Modifier)
                        .then(if (n > 0) Modifier.clickable { onSelect(if (on) null else Triple(y, m, d)) } else Modifier)
                        .semantics(mergeDescendants = true) { contentDescription = "$shortMonth $d, " + (if (n == 0) "rest" else if (n == 1) "1 workout" else "$n workouts") + if (isToday) ", today" else "" },
                        contentAlignment = Alignment.Center) {
                        Text("$d", style = if (n > 0) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyMedium,
                            color = when { on -> cs.onPrimary; n > 0 -> cs.onPrimaryContainer; future -> cs.outline; else -> cs.onSurfaceVariant })
                    }
                }
            }
        }
        if (sel != null) Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Showing $shortMonth ${sel.third}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f).padding(start = 4.dp))
            TextButton(onClick = { onSelect(null) }) { Text("Show all") }
        }
    }
}
