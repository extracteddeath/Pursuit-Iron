package io.github.extracteddeath.pursuitiron

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.extracteddeath.pursuitiron.domain.JournalSessionNoteCodec
import io.github.extracteddeath.pursuitiron.domain.NativeWorkoutProgress

/** A note draft is never reported as saved until its journal operation is acknowledged. */
@Composable
internal fun NativeSessionNoteSheet(progress: NativeWorkoutProgress,busy: Boolean,error: String?,
    onSave: (String)->Unit,onDismiss: ()->Unit) {
    val note=progress.journal.note
    var text by rememberSaveable(progress.plan.session.workoutId) { mutableStateOf(note?.text.orEmpty()) }
    var submitted by rememberSaveable(progress.plan.session.workoutId) { mutableStateOf<String?>(null) }
    var confirmDiscard by remember { mutableStateOf(false) }
    val changed=text!=note?.text.orEmpty()
    val valid=JournalSessionNoteCodec.validText(text)
    // A failed save retains the editable draft. An acknowledged save closes
    // even after rotation; no error is mistaken for durable persistence.
    LaunchedEffect(note?.operationId,busy) {
        if(!busy && submitted!=null && note?.text==submitted)onDismiss()
    }
    fun close() { if(!busy) {if(changed)confirmDiscard=true else onDismiss()} }
    NativeEditSheet(busy,onDismiss={close()},canDismiss={
        if(changed) {confirmDiscard=true;false} else true
    }) {
        Text("Session note",style=MaterialTheme.typography.headlineSmall)
        Text("Saved with this workout. Your next workout starts with an empty session note.",
            style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(value=text,onValueChange={text=it;submitted=null},enabled=!busy,
            label={Text("Workout note")},minLines=3,maxLines=8,isError=!valid,
            supportingText={Text("${text.length}/${JournalSessionNoteCodec.MAX_CHARS} characters")},
            modifier=Modifier.fillMaxWidth())
        error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
        Button(onClick={submitted=text;onSave(text)},enabled=!busy && changed && valid,
            modifier=Modifier.fillMaxWidth()) {Text(if(busy)"Saving note…" else "Save note")}
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick={text="";submitted=null},enabled=!busy && text.isNotEmpty()) {Text("Clear text")}
            TextButton(onClick={close()},enabled=!busy) {Text("Close")}
        }
    }
    if(confirmDiscard)AlertDialog(onDismissRequest={confirmDiscard=false},
        title={Text("Discard note changes?")},text={Text("Your last saved session note will be kept.")},
        confirmButton={TextButton(onClick={confirmDiscard=false;onDismiss()}) {Text("Discard changes")}},
        dismissButton={TextButton(onClick={confirmDiscard=false}) {Text("Keep editing")}})
}
