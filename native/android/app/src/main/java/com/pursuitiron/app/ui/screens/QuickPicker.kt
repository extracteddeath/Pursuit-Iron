package com.pursuitiron.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pursuitiron.app.AppViewModel
import com.pursuitiron.app.ui.common.MorphChip
import com.pursuitiron.app.ui.common.PiIcons
import com.pursuitiron.app.ui.common.b
import com.pursuitiron.app.ui.common.s
import org.json.JSONObject

/* The design's quick workout (Main.dc.html qp, mode quick): pick lifts in the order you'll do them, numbered as you
   pick; search and a muscle filter; banned lifts stay out; New exercise; Start · N lifts. Nothing in the program
   changes, and every set still teaches the engine. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickPickerSheet(vm: AppViewModel) {
    if (!vm.quickPickOpen) return
    val cs = MaterialTheme.colorScheme
    var q by remember { mutableStateOf("") }
    var chip by remember { mutableStateOf("All") }
    val picked = remember { mutableStateListOf<String>() }
    val names = remember { mutableStateOf(mapOf<String, String>()) }
    var rows by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var creating by remember { mutableStateOf(false) }
    LaunchedEffect(q) { rows = vm.searchExercises(q) }
    val close = { vm.quickPickOpen = false }
    if (creating) { CreateExerciseSheet(vm, null, onCreated = { id -> picked.add(id) }) { creating = false }; return }
    val shown = rows.filter { !it.b("banned") && (chip == "All" || it.s("group") == chip) }.take(120)
    ModalBottomSheet(onDismissRequest = close, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Quick workout", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f).padding(start = 4.dp))
                Surface(onClick = { creating = true }, color = cs.surfaceContainerHigh, shape = CircleShape, modifier = Modifier.height(40.dp)) {
                    Box(Modifier.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) { Text("New exercise", style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold)) }
                }
            }
            Text("Pick lifts in the order you’ll do them. Nothing in your program changes; every set still teaches the engine.",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp), color = cs.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
            TextField(q, { q = it }, singleLine = true, placeholder = { Text("Search exercises") }, leadingIcon = { Icon(PiIcons.Search, null) },
                shape = CircleShape, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Search exercises" },
                colors = TextFieldDefaults.colors(focusedContainerColor = cs.surfaceContainerHigh, unfocusedContainerColor = cs.surfaceContainerHigh,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(listOf("All", "Chest", "Back", "Legs", "Shoulders", "Arms", "Core")) { g -> MorphChip(g, chip == g, { chip = g }, height = 36.dp) }
            }
            LazyColumn(Modifier.weight(1f, fill = false).heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(shown, key = { it.s("id") }) { x ->
                    val id = x.s("id"); val at = picked.indexOf(id); val on = at >= 0
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (on) cs.secondaryContainer else cs.surfaceContainerLow)
                        .clickable { if (on) picked.remove(id) else { picked.add(id); names.value = names.value + (id to x.s("name")) } }
                        .semantics(mergeDescendants = true) { role = Role.Checkbox; selected = on; contentDescription = (if (on) "Remove " else "Add ") + x.s("name") + if (on) ", number ${at + 1}" else "" }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(36.dp).clip(if (on) RoundedCornerShape(12.dp) else CircleShape).background(if (on) cs.primary else Color.Transparent)
                            .then(if (on) Modifier else Modifier.border(1.5.dp, cs.outlineVariant, CircleShape)), contentAlignment = Alignment.Center) {
                            if (on) Text("${at + 1}", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold), color = cs.onPrimary)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(x.s("name"), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(listOfNotNull(x.s("group").ifBlank { null }, x.s("equipLabel").ifBlank { null }, if (x.b("custom")) "yours" else null).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1)
                        }
                    }
                }
            }
            if (picked.isNotEmpty()) Text(picked.joinToString(" → ") { names.value[it] ?: rows.firstOrNull { r -> r.s("id") == it }?.s("name") ?: "New lift" },
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (picked.isNotEmpty()) Surface(onClick = { picked.clear() }, color = Color.Transparent, contentColor = cs.primary, shape = CircleShape, modifier = Modifier.height(56.dp)) {
                    Box(Modifier.padding(horizontal = 16.dp), contentAlignment = Alignment.Center) { Text("Clear", style = MaterialTheme.typography.labelLarge) }
                }
                Surface(onClick = { if (picked.isNotEmpty()) { close(); vm.startQuick(picked.toList()) } },
                    color = if (picked.isNotEmpty()) cs.primary else cs.surfaceContainerHighest, contentColor = if (picked.isNotEmpty()) cs.onPrimary else cs.onSurfaceVariant,
                    shape = CircleShape, modifier = Modifier.weight(1f).height(56.dp).semantics { if (picked.isEmpty()) contentDescription = "Pick at least one lift" }) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(if (picked.isEmpty()) "Pick at least one lift" else "Start · ${picked.size} ${if (picked.size == 1) "lift" else "lifts"}",
                            style = MaterialTheme.typography.titleSmall.copy(fontSize = 16.sp))
                    }
                }
            }
        }
    }
}
