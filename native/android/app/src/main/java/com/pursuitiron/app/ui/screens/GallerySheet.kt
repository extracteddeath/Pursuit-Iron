@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.pursuitiron.app.ui.screens

import com.pursuitiron.app.ui.common.fadingHScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pursuitiron.app.AppViewModel
import com.pursuitiron.app.ui.common.Badge
import com.pursuitiron.app.ui.common.Kicker
import com.pursuitiron.app.ui.common.MorphChip
import com.pursuitiron.app.ui.common.b
import com.pursuitiron.app.ui.common.i
import com.pursuitiron.app.ui.common.s
import com.pursuitiron.app.ui.common.strings
import org.json.JSONObject

private val GOAL_LABEL = mapOf("hypertrophy" to "Size", "strength" to "Strength", "both" to "Strength & size")
private fun sessLabel(s: String) = s.removePrefix("s").toIntOrNull()?.let { "$it min" } ?: s

/* The design's shared programs (web GalleryView): filter by split, days and goal; import adds a program
   built for your gym without switching to it. */
@Composable
fun GallerySheet(vm: AppViewModel, onClose: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    var rows by remember { mutableStateOf<List<JSONObject>?>(null) }
    var busy by remember { mutableStateOf<String?>(null) }
    var split by remember { mutableStateOf("all") }
    var days by remember { mutableStateOf("all") }
    var goal by remember { mutableStateOf("all") }
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Shared programs", style = MaterialTheme.typography.headlineSmall)
            Text("Rebuilt for your gym’s equipment. Importing adds a program; nothing changes until you switch to it.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            val all = rows ?: run { com.pursuitiron.app.ui.common.LoadingSkeleton(Modifier.padding(vertical = 8.dp)); return@Column }
            // Three rows each led by "All" read as one repeated word; each says what it leaves open.
            @Composable fun Filter(sel: String, any: String, vals: List<Pair<String, String>>, pick: (String) -> Unit) {
                Row(Modifier.fadingHScroll(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (listOf("all" to any) + vals).forEach { (k, l) -> MorphChip(l, sel == k, { pick(k) }, height = 36.dp) }
                }
            }
            Filter(split, "Any split", all.map { it.s("split") to it.s("splitName") }.distinct()) { split = it }
            Filter(days, "Any days", all.map { it.i("days") }.distinct().sorted().map { "$it" to "$it day" }) { days = it }
            Filter(goal, "Any goal", all.map { it.s("goal") }.distinct().map { it to (GOAL_LABEL[it] ?: it) }) { goal = it }
            val shown = all.filter { (split == "all" || it.s("split") == split) && (days == "all" || "${it.i("days")}" == days) && (goal == "all" || it.s("goal") == goal) }
            if (shown.isEmpty()) Text("No shared program matches. Clear a filter.", color = cs.onSurfaceVariant)
            shown.forEach { e ->
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(cs.surfaceContainer).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(e.s("title"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        if (e.b("coached")) Badge("Coached", cs.tertiaryContainer, cs.onTertiaryContainer)
                    }
                    // The title already names the days and split; the line adds what it doesn't say.
                    val t = e.s("title").lowercase()
                    Text(listOfNotNull("${e.i("days")} day".takeIf { "${e.i("days")} day" !in t }, e.s("splitName").takeIf { it.isNotBlank() && it.lowercase() !in t }, GOAL_LABEL[e.s("goal")] ?: e.s("goal"), e.s("session").takeIf { it.isNotBlank() }?.let(::sessLabel), "by ${e.s("author").let { if (it == "pursuit") "Pursuit Iron" else it }}").joinToString(" · "),
                        style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                    Text(e.s("note"), style = MaterialTheme.typography.bodyMedium)
                    val eq = e.strings("equipment")
                    Text(if (e.isNull("equipment") || eq.isEmpty()) "Built for your gym's equipment" else "Needs: " + eq.take(4).joinToString(", ") + if (eq.size > 4) " +${eq.size - 4}" else "",
                        style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    // Tags that repeat the days or the goal ("6 day", "Hypertrophy" beside "Size") are dropped.
                    val tags = e.strings("tags").filterNot { Regex("^\\d+-day$").matches(it) || it.lowercase() in setOf("hypertrophy", "strength", "size", "strength-size") }
                    if (tags.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { tags.forEach { Badge(it.replace('-', ' ').replaceFirstChar { c -> c.uppercase() }) } }
                    val added = e.b("added")
                    Button(onClick = { if (!added && busy == null) { busy = e.s("code"); vm.importGallery(e.s("code")) { busy = null; rows = null } } },
                        enabled = busy == null || busy == e.s("code"),
                        colors = if (added) ButtonDefaults.buttonColors(containerColor = cs.surfaceContainerHigh, contentColor = cs.onSurfaceVariant) else ButtonDefaults.buttonColors(),
                        modifier = Modifier.fillMaxWidth().height(48.dp).semantics { contentDescription = if (added) "${e.s("title")} is in your programs" else "Import ${e.s("title")}" }) {
                        Text(when { added -> "Added to your programs"; busy == e.s("code") -> "Building…"; else -> "Import program" })
                    }
                }
            }
        }
    }
    LaunchedEffect(rows == null) { if (rows == null) rows = vm.galleryList() }
}
