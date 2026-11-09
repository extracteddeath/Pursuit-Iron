package io.github.extracteddeath.pursuitiron

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.extracteddeath.pursuitiron.rebuild.CustomWorkoutJournalDomain
import io.github.extracteddeath.pursuitiron.rebuild.CustomWorkoutJournalDraft

/**
 * A rotation-safe draft, never an optimistic journal acknowledgment.
 * The only successful Save is an owner-fenced, replayable SessionNote receipt
 * followed by observing its updated durable journal. Finish is separate.
 */
@Composable
internal fun NativeKotlinCustomSessionNoteSheet(
    journal:CustomWorkoutJournalDraft,
    busy:Boolean,
    error:String?,
    onSave:(String)->Unit,
    onDismiss:()->Unit
) {
    NativeKotlinSessionNoteEditor(
        workoutId=journal.workoutId,
        committed=journal.sessionNote,
        maxChars=CustomWorkoutJournalDomain.MAX_SESSION_NOTE_CHARS,
        validate=CustomWorkoutJournalDomain::validSessionNote,
        busy=busy,error=error,onSave=onSave,onDismiss=onDismiss
    )
}

/** The same durable edit UI for both immutable Kotlin journal formats. */
@Composable
internal fun NativeKotlinSessionNoteEditor(
    workoutId:String,
    committed:String,
    maxChars:Int,
    validate:(String)->Boolean,
    busy:Boolean,
    error:String?,
    onSave:(String)->Unit,
    onDismiss:()->Unit
) {
    var draft by rememberSaveable(workoutId) {mutableStateOf(committed)}
    var submitted by rememberSaveable(workoutId) {
        mutableStateOf<String?>(null)
    }
    var confirmDiscard by remember {mutableStateOf(false)}
    val dirty=draft!=committed
    val valid=validate(draft)
    LaunchedEffect(committed,busy,submitted,error) {
        // The reducer result alone does not count: the caller refreshes from
        // the durable journal before exposing an updated SessionNote.
        if(!busy && error==null && submitted!=null && committed==submitted)
            onDismiss()
    }
    fun close() {
        if(!busy) {
            if(dirty)confirmDiscard=true else onDismiss()
        }
    }
    NativeEditSheet(
        busy=busy,
        onDismiss={close()},
        canDismiss={
            if(dirty) {confirmDiscard=true;false}
            else true
        }
    ) {
        Text("Session note",style=MaterialTheme.typography.headlineSmall)
        Text("Saved to this workout's journal. Does not change your training targets or apply to the next session.",
            style=MaterialTheme.typography.bodySmall,
            color=MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            value=draft,
            onValueChange={
                draft=it.take(maxChars+1)
                submitted=null
            },
            enabled=!busy,
            label={Text("Workout note")},
            minLines=3,
            maxLines=8,
            isError=!valid,
            supportingText={
                Text("${draft.length}/$maxChars characters")
            },
            modifier=Modifier.fillMaxWidth()
        )
        error?.let {
            Text(it,style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.error)
        }
        Button(
            enabled=!busy && dirty && valid,
            modifier=Modifier.fillMaxWidth().heightIn(min=48.dp),
            onClick={submitted=draft;onSave(draft)}
        ) {Text(if(busy)"Saving…" else "Save session note")}
        Row(horizontalArrangement=Arrangement.spacedBy(PursuitSpace.xs)) {
            OutlinedButton(
                onClick={draft="";submitted=null},
                enabled=!busy && draft.isNotEmpty(),
                modifier=Modifier.weight(1f).heightIn(min=48.dp)
            ) {Text("Clear text")}
            TextButton(
                onClick={close()},
                enabled=!busy,
                modifier=Modifier.weight(1f).heightIn(min=48.dp)
            ) {Text("Close")}
        }
    }
    if(confirmDiscard)AlertDialog(
        onDismissRequest={confirmDiscard=false},
        title={Text("Discard note changes?")},
        text={Text("The last saved note remains in this workout's journal.")},
        confirmButton={
            TextButton(onClick={confirmDiscard=false;onDismiss()}) {
                Text("Discard draft")
            }
        },
        dismissButton={
            TextButton(onClick={confirmDiscard=false}) {
                Text("Keep editing")
            }
        }
    )
}
