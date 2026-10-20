package com.pursuitiron.app.ui.screens

import androidx.compose.foundation.horizontalScroll
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
fun HistoryList(vm: AppViewModel, onOpen: (String) -> Unit) {
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
        x.list("groups").forEach { g ->
            Row(Modifier.fillMaxWidth().padding(top = 6.dp, start = 4.dp), verticalAlignment = Alignment.Bottom) {
                Kicker(g.s("label"), modifier = Modifier.weight(1f))
                Text("${g.i("count")} session${if (g.i("count") == 1) "" else "s"}" + (g.i("volume").takeIf { it > 0 }?.let { " · ${fmt1(it / 1000.0)} ${volUnit(u)}" } ?: ""),
                    style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
            }
            Column(Modifier.clip(RoundedCornerShape(18.dp)), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                g.list("items").forEach { r -> HistoryRow(r) { onOpen(r.s("id")) } }
            }
        }
    }
}
