@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.pursuitiron.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pursuitiron.app.AppViewModel
import com.pursuitiron.app.ui.LocalWide
import com.pursuitiron.app.ui.common.Badge
import com.pursuitiron.app.ui.common.Kicker
import com.pursuitiron.app.ui.common.MorphChip
import com.pursuitiron.app.ui.common.PiCard
import com.pursuitiron.app.ui.common.PiIcons
import com.pursuitiron.app.ui.common.StretchFocusNote
import com.pursuitiron.app.ui.common.b
import com.pursuitiron.app.ui.common.d
import com.pursuitiron.app.ui.common.list
import com.pursuitiron.app.ui.common.s
import com.pursuitiron.app.ui.common.strings
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val PARTS = listOf(null to "All", "chest" to "Chest", "lats" to "Lats", "upper_back" to "Upper back", "shoulders" to "Shoulders", "biceps" to "Biceps",
    "triceps" to "Triceps", "quads" to "Quads", "hamstrings" to "Hamstrings", "glutes" to "Glutes", "calves" to "Calves", "abs" to "Abs", "forearms" to "Forearms")

@Composable
fun LibraryScreen(vm: AppViewModel) {
    val wide = LocalWide.current
    val cs = MaterialTheme.colorScheme
    var q by remember { mutableStateOf("") }
    var part by remember { mutableStateOf<String?>(null) }
    var open by remember { mutableStateOf<String?>(null) }
    LazyVerticalGrid(
        columns = GridCells.Fixed(if (wide) 2 else 1),
        contentPadding = PaddingValues(start = if (wide) 32.dp else 16.dp, end = if (wide) 32.dp else 16.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.statusBarsPadding(),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) { Text("Exercises", style = MaterialTheme.typography.displayMedium, modifier = Modifier.padding(top = 20.dp, start = 4.dp, bottom = 12.dp)) }
        item(span = { GridItemSpan(maxLineSpan) }) {
            OutlinedTextField(q, { q = it; vm.searchLibrary(it, part) }, Modifier.fillMaxWidth().padding(bottom = 10.dp), singleLine = true,
                placeholder = { Text("Search exercises") }, shape = CircleShape,
                colors = TextFieldDefaults.colors(focusedContainerColor = cs.surfaceContainerHigh, unfocusedContainerColor = cs.surfaceContainerHigh, unfocusedIndicatorColor = Color.Transparent))
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 10.dp)) {
                items(PARTS) { (id, label) -> MorphChip(label, part == id, { part = id; vm.searchLibrary(q, id) }) }
            }
        }
        if (vm.library.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
            Text("No exercises match. Try a shorter search.", color = cs.onSurfaceVariant, modifier = Modifier.padding(16.dp))
        }
        items(vm.library, key = { it.s("id") }) { x ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(cs.surfaceContainerLow).clickable { open = x.s("id") }.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(x.s("name"), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(x.s("partLabel") + " · " + x.strings("equip").joinToString(", "), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (x.b("banned")) Badge("Banned")
            }
        }
    }
    open?.let { id -> ExerciseSheet(vm, id) { open = null } }
}

@Composable
fun ExerciseSheet(vm: AppViewModel, id: String, onClose: () -> Unit) {
    var x by remember(id) { mutableStateOf<JSONObject?>(null) }
    var note by remember(id) { mutableStateOf("") }
    LaunchedEffect(id) { x = vm.exercise(id); note = x?.s("note").orEmpty() }
    val cs = MaterialTheme.colorScheme
    ModalBottomSheet(onDismissRequest = { x?.let { if (note != it.s("note")) vm.setExNote(id, note) }; onClose() }) {
        val e = x
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (e == null) { Text("Loading…"); return@Column }
            Kicker(e.s("partLabel"))
            Text(e.s("name"), style = MaterialTheme.typography.headlineMedium)
            Text(e.strings("equip").joinToString(", ").replaceFirstChar { it.uppercase() }, color = cs.onSurfaceVariant)
            val sec = e.list("secondary")
            if (e.b("stretchFocus")) StretchFocusNote()
            if (sec.isNotEmpty()) Text("Also works " + sec.joinToString(", ") { "${it.s("label")} (${(it.d("share") * 100).toInt()}%)" }, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            val cues = e.strings("cues")
            if (cues.isNotEmpty()) PiCard(color = cs.surfaceContainerHigh, radius = 20.dp, padding = 14.dp) {
                Text("Form cues", style = MaterialTheme.typography.titleSmall)
                cues.forEach { c -> Row { Text("•  "); Text(c, style = MaterialTheme.typography.bodyMedium) } }
            }
            val hist = e.list("history")
            if (hist.isNotEmpty()) PiCard(color = cs.surfaceContainerHigh, radius = 20.dp, padding = 14.dp) {
                Text("Your history", style = MaterialTheme.typography.titleSmall)
                hist.take(6).forEach { h ->
                    Row {
                        Text(SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(h.optLong("date"))), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(end = 12.dp))
                        Text(h.list("sets").joinToString("  ") { "${it.s("w")}×${it.s("r")}" }, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            OutlinedTextField(note, { note = it }, Modifier.fillMaxWidth(), label = { Text("Your note") }, placeholder = { Text("Seat height, grip, cues that work for you") })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (e.b("banned")) FilledTonalButton(onClick = { vm.setBan(id, false); x = JSONObject(e.toString()).put("banned", false) }) { Text("Unban") }
                else OutlinedButton(onClick = { vm.setBan(id, true); x = JSONObject(e.toString()).put("banned", true) }) {
                    Icon(PiIcons.Close, null, modifier = Modifier.size(16.dp)); Text("  Never pick this")
                }
            }
        }
    }
}

