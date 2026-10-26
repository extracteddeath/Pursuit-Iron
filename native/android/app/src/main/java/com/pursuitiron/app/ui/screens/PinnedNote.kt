@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.pursuitiron.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pursuitiron.app.AppViewModel
import com.pursuitiron.app.ui.common.b
import com.pursuitiron.app.ui.common.list
import com.pursuitiron.app.ui.common.s
import org.json.JSONObject

/* The design's pinned-note panel (Main.dc.html pPin) and web B932: the exercise's setup fields and its cue in one
   staged editor. Save writes both, Remove clears both, Cancel or dismissal keeps what was saved. Used by Workout and Exercises. */
@Composable
fun PinNoteSheet(vm: AppViewModel, id: String, onClose: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    var d by remember(id) { mutableStateOf<JSONObject?>(null) }
    var note by remember(id) { mutableStateOf("") }
    val setup = remember(id) { mutableStateMapOf<String, String>() }
    LaunchedEffect(id) { d = vm.pinnedNote(id); note = d?.s("note").orEmpty(); d?.list("fields")?.forEach { setup[it.s("key")] = it.s("value") } }
    val x = d ?: return
    val fields = x.list("fields")
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Pinned note", style = MaterialTheme.typography.headlineSmall)
            Text("PINNED NOTE · ${x.s("name").uppercase()}", style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.4.sp), color = cs.onSurfaceVariant)
            fields.forEach { f ->
                val k = f.s("key")
                OutlinedTextField(setup[k].orEmpty(), { setup[k] = it.take(40) }, Modifier.fillMaxWidth().semantics { contentDescription = "${f.s("label")} for ${x.s("name")}" },
                    singleLine = true, label = { Text(f.s("label")) }, placeholder = { Text("—") })
            }
            Text(if (fields.isNotEmpty()) "Set these once per machine. They show at the top of the lift every time." else "This lift has no adjustable equipment — a cue is all it needs.",
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            OutlinedTextField(note, { note = it.take(200) }, Modifier.fillMaxWidth(), minLines = 3, label = { Text("Note") },
                placeholder = { Text("e.g. pause on chest · pinky on ring") })
            Text("Saved to this exercise and shown every time it comes up — in any workout or program.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.savePinnedNote(id, note, JSONObject().apply { setup.forEach { (k, v) -> put(k, v) } }) { onClose() } },
                    modifier = Modifier.weight(1f).height(52.dp)) { Text("Save note", fontWeight = FontWeight.Bold) }
                if (x.b("has")) OutlinedButton(onClick = { vm.removePinnedNote(id) { onClose() } }, modifier = Modifier.height(52.dp)) { Text("Remove") }
                TextButton(onClick = onClose, modifier = Modifier.height(52.dp)) { Text("Cancel") }
            }
        }
    }
}
