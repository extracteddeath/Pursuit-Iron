package io.github.extracteddeath.pursuitiron

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.extracteddeath.pursuitiron.domain.*
import org.json.JSONObject
import java.util.UUID

/** Rotation-safe draft and retry identity. Only an acknowledged journal operation closes the sheet. */
@Composable
internal fun NativeExerciseNoteSheet(progress: NativeWorkoutProgress,exercise: NativeWorkoutExercise,
    busy: Boolean,error: String?,onSave: (JournalExerciseNote)->Unit,onDismiss: ()->Unit) {
    val context=NativeExerciseNotePolicy.resolved(progress.plan,progress.journal.exerciseNotes,exercise.id) ?: return
    val prior=progress.journal.exerciseNotes.lastOrNull {it.exerciseId==exercise.id}
    val workoutId=progress.plan.session.workoutId
    var text by rememberSaveable(workoutId,exercise.id) {mutableStateOf(context.text)}
    var setupJson by rememberSaveable(workoutId,exercise.id) {mutableStateOf(JSONObject(context.setup).toString())}
    var operationId by rememberSaveable(workoutId,exercise.id) {mutableStateOf<String?>(null)}
    var updatedAt by rememberSaveable(workoutId,exercise.id) {mutableStateOf<Long?>(null)}
    var confirmDiscard by remember {mutableStateOf(false)}
    val raw=JSONObject(setupJson)
    val setup=raw.keys().asSequence().associateWith {raw.getString(it)}
    val changed=text!=context.text || setup!=context.setup
    val valid=JournalSessionNoteCodec.validText(text) && NativeExerciseNotePolicy.validSetup(setup)
    LaunchedEffect(prior?.operationId,busy) {
        if(!busy && operationId!=null && prior?.operationId==operationId)onDismiss()
    }
    fun edited() {operationId=null;updatedAt=null}
    fun close() {if(!busy) {if(changed)confirmDiscard=true else onDismiss()}}
    NativeEditSheet(busy,onDismiss={close()},canDismiss={
        if(changed) {confirmDiscard=true;false} else true
    }) {
        Text("Pinned note & setup",style=MaterialTheme.typography.headlineSmall)
        Text(exercise.name,style=MaterialTheme.typography.titleMedium)
        Text("Kept with this exercise for future workouts when you finish. Saved edits recover with this workout.",
            style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        context.fields.forEach {field->
            val value=setup[field.key].orEmpty()
            OutlinedTextField(value=value,onValueChange={
                val next=JSONObject(setupJson)
                if(it.isEmpty())next.remove(field.key) else next.put(field.key,it)
                setupJson=next.toString();edited()
            },enabled=!busy,label={Text(field.label)},singleLine=true,
                isError=value.length>200 || !JournalSessionNoteCodec.validText(value),
                supportingText=if(field.orphan) ({Text("Saved earlier · clear to remove")}) else null,
                modifier=Modifier.fillMaxWidth())
        }
        OutlinedTextField(value=text,onValueChange={text=it;edited()},enabled=!busy,
            label={Text("Pinned exercise note")},minLines=3,maxLines=8,
            isError=!JournalSessionNoteCodec.validText(text),
            supportingText={Text("${text.length}/${JournalSessionNoteCodec.MAX_CHARS} characters")},
            modifier=Modifier.fillMaxWidth())
        error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
        Button(onClick={
            val op=operationId ?: ("exercise-note-"+UUID.randomUUID()).also {operationId=it}
            val at=updatedAt ?: maxOf(System.currentTimeMillis(),progress.plan.session.startedAt,
                (prior?.updatedAt ?: 0)+1).also {updatedAt=it}
            onSave(JournalExerciseNote(op,exercise.id,text,setup,at,prior?.operationId))
        },enabled=!busy && changed && valid,modifier=Modifier.fillMaxWidth()) {
            Text(if(busy)"Saving…" else "Save note & setup")
        }
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick={text="";setupJson="{}";edited()},enabled=!busy && (text.isNotEmpty() || setup.isNotEmpty())) {
                Text("Clear note & setup")
            }
            TextButton(onClick={close()},enabled=!busy) {Text("Close")}
        }
    }
    if(confirmDiscard)AlertDialog(onDismissRequest={confirmDiscard=false},
        title={Text("Discard pinned changes?")},text={Text("Your last saved exercise note and setup will be kept.")},
        confirmButton={TextButton(onClick={confirmDiscard=false;onDismiss()}) {Text("Discard changes")}},
        dismissButton={TextButton(onClick={confirmDiscard=false}) {Text("Keep editing")}})
}
