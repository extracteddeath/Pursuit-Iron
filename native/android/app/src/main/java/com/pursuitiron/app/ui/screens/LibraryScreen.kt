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
import androidx.compose.foundation.layout.Box
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

private val EQUIPS = listOf(null to "Any equipment", "barbell" to "Barbell", "dumbbell" to "Dumbbell", "cable" to "Cable", "machine" to "Machine",
    "bodyweight" to "Bodyweight", "kettlebell" to "Kettlebell", "band" to "Band")
private val LOWER = setOf("quads", "hamstrings", "glutes", "calves", "adductors", "abductors", "lower_back")

/* The design's Exercises: compare and + New in the header, muscle and equipment chips, a count, and rows
   with an initial badge, "Muscle · Equipment", and TODAY / BANNED tags. */
@Composable
fun LibraryScreen(vm: AppViewModel) {
    val wide = LocalWide.current
    val cs = MaterialTheme.colorScheme
    var q by remember { mutableStateOf("") }
    var part by remember { mutableStateOf<String?>(null) }
    var equip by remember { mutableStateOf<String?>(null) }
    var open by remember { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }
    var comparing by remember { mutableStateOf(false) }
    if (creating) CreateExerciseSheet(vm, null) { creating = false }
    if (comparing) CompareSheet(vm) { comparing = false }
    LazyVerticalGrid(
        columns = GridCells.Fixed(if (wide) 2 else 1),
        contentPadding = PaddingValues(start = if (wide) 32.dp else 16.dp, end = if (wide) 32.dp else 16.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.statusBarsPadding(),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(Modifier.padding(top = 20.dp, start = 4.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Exercises", style = MaterialTheme.typography.displayMedium, modifier = Modifier.weight(1f))
                androidx.compose.material3.FilledTonalIconButton(onClick = { comparing = true }) { Icon(PiIcons.Progress, "Compare exercises") }
                androidx.compose.material3.FilledTonalButton(onClick = { creating = true }) { Icon(PiIcons.Plus, null, Modifier.size(18.dp)); Text(" New") }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            OutlinedTextField(q, { q = it; vm.searchLibrary(it, part, equip) }, Modifier.fillMaxWidth().padding(bottom = 10.dp), singleLine = true,
                placeholder = { Text("Search exercises") }, shape = CircleShape, leadingIcon = { Icon(PiIcons.Focus, null) },
                colors = TextFieldDefaults.colors(focusedContainerColor = cs.surfaceContainerHigh, unfocusedContainerColor = cs.surfaceContainerHigh, unfocusedIndicatorColor = Color.Transparent))
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                items(PARTS) { (id, label) -> MorphChip(label, part == id, { part = id; vm.searchLibrary(q, id, equip) }) }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                items(EQUIPS) { (id, label) -> MorphChip(label, equip == id, { equip = id; vm.searchLibrary(q, part, id) }, height = 36.dp) }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(if (vm.library.size >= 400) "400+ exercises" else "${vm.library.size} exercise${if (vm.library.size == 1) "" else "s"}",
                style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
        }
        if (vm.library.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
            Text("No exercises match. Try a shorter search or another filter.", color = cs.onSurfaceVariant, modifier = Modifier.padding(16.dp))
        }
        items(vm.library, key = { it.s("id") }) { x ->
            val lower = x.s("part") in LOWER
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(cs.surfaceContainerLow).clickable { open = x.s("id") }.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(if (lower) cs.tertiaryContainer else cs.primaryContainer), contentAlignment = Alignment.Center) {
                    Text(x.s("name").firstOrNull()?.uppercase() ?: "?", style = MaterialTheme.typography.titleSmall, color = if (lower) cs.onTertiaryContainer else cs.onPrimaryContainer)
                }
                Column(Modifier.weight(1f)) {
                    Text(x.s("name"), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(x.s("partLabel").replaceFirstChar { it.uppercase() } + " · " + x.s("equipLabel"), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (x.b("today")) Badge("TODAY", cs.primaryContainer, cs.onPrimaryContainer)
                if (x.b("banned")) Badge("BANNED", cs.surfaceContainerHighest, cs.onSurfaceVariant)
                Icon(PiIcons.ChevronRight, null, tint = cs.onSurfaceVariant)
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
            com.pursuitiron.app.ui.common.MuscleChips(e.s("partLabel").replaceFirstChar { it.uppercase() }, sec.sortedByDescending { it.d("share") }.map { it.s("label").replaceFirstChar { c -> c.uppercase() } }, max = 6)
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

