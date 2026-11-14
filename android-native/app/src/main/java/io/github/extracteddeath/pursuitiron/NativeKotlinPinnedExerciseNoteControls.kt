package io.github.extracteddeath.pursuitiron

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.extracteddeath.pursuitiron.nativecoachstore.*
import org.json.JSONObject
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

 /**
 * Applies to the SAME exact exercise ID across independently issued workouts
 * for one signed-in athlete. Displays only verified owner-scoped storage,
 * never infers a machine setting, weight or exercise authorization.
 */
@Composable
internal fun NativeKotlinPinnedExerciseControl(
    host:NativeKotlinCandidateHost,
    workoutId:String,exerciseId:String,custom:Boolean,
    enabled:Boolean,
    showEditAction:Boolean=true,
    externalOpen:Boolean=false,
    onExternalDismiss:()->Unit={}
) {
    val scope=rememberCoroutineScope()
    var opened by rememberSaveable(workoutId,exerciseId){mutableStateOf(false)}
    val editorOpen=opened || externalOpen
    var loaded by remember(host,workoutId,exerciseId,custom) {
        mutableStateOf<NativePinnedNoteRead?>(null)
    }
    var previewFields by remember(host,workoutId,exerciseId,custom) {
        mutableStateOf<List<NativePinnedSetupField>>(emptyList())
    }
    LaunchedEffect(host,workoutId,exerciseId,custom,editorOpen) {
        if(editorOpen)return@LaunchedEffect
        val response=withContext(Dispatchers.IO){
            runCatching {
                host.readPinnedExerciseNote(workoutId,exerciseId,custom)
            }.getOrElse {
                NativePinnedNoteRead.Rejected(
                    NativePinnedNoteRefusal.STORAGE_UNAVAILABLE)
            }
        }
        val note=(response as? NativePinnedNoteRead.Current)?.note
        previewFields=if(note!=null)withContext(Dispatchers.IO) {
            host.reviewedPinnedExerciseSetupFields(
                workoutId,exerciseId,custom,note.setup)
        }.orEmpty() else emptyList()
        loaded=response
    }
    val saved=(loaded as? NativePinnedNoteRead.Current)?.note
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(6.dp)) {
        if(saved?.hasContent==true) {
            Text("PINNED TO THIS EXERCISE",
                style=MaterialTheme.typography.labelSmall,
                color=MaterialTheme.colorScheme.primary)
            if(saved.text.isNotBlank())Text(saved.text,
                style=MaterialTheme.typography.bodyMedium)
            val shown=previewFields.ifEmpty {
                NativePinnedSetupFieldDomain.fields(null,saved.setup)
            }
            shown.forEach { field ->
                saved.setup[field.key]?.let { value ->
                    Text(field.label+": "+value,
                        style=MaterialTheme.typography.bodySmall,
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if(loaded is NativePinnedNoteRead.Rejected)
            Text("Exercise setup unavailable; saved data was not reset.",
                color=MaterialTheme.colorScheme.error,
                style=MaterialTheme.typography.bodySmall)
        if(showEditAction)OutlinedButton(
            onClick={opened=true},
            enabled=enabled && saved!=null,
            modifier=Modifier.heightIn(min=48.dp)
        ) {
            Text(if(saved?.hasContent==true)"Edit pinned note & setup"
                else "Pin note & setup")
        }
    }
    if(editorOpen)NativeKotlinPinnedExerciseNoteSheet(
        host=host,workoutId=workoutId,exerciseId=exerciseId,
        custom=custom,onDismiss={opened=false;onExternalDismiss()}
    )
}

/**
 * The draft is SAVEABLE while the durable record must be re-authorized on
 * every open and every Save. On failure (including ambiguous disk ack) it
 * remains visible and editable; no optimistic success or discarded content.
 */
@Composable
internal fun NativeKotlinPinnedExerciseNoteSheet(
    host:NativeKotlinCandidateHost,
    workoutId:String,exerciseId:String,custom:Boolean,
    onDismiss:()->Unit
) {
    val scope=rememberCoroutineScope()
    var loaded by remember(host,workoutId,exerciseId,custom) {
        mutableStateOf<NativePinnedExerciseNote?>(null)
    }
    var busy by remember {mutableStateOf(true)}
    var error by remember {mutableStateOf<String?>(null)}
    var initialized by rememberSaveable(workoutId,exerciseId){mutableStateOf(false)}
    var draft by rememberSaveable(workoutId,exerciseId){mutableStateOf("")}
    // JSON is only a saveable UI draft container. The owner-authenticated
    // store accepts an independently validated bounded string map.
    var setupJson by rememberSaveable(workoutId,exerciseId) {
        mutableStateOf("{}")
    }
    var reviewedFields by remember(host,workoutId,exerciseId,custom) {
        mutableStateOf<List<NativePinnedSetupField>>(emptyList())
    }
    var eventId by rememberSaveable(workoutId,exerciseId){mutableStateOf<String?>(null)}
    var confirmDiscard by remember {mutableStateOf(false)}

    LaunchedEffect(host,workoutId,exerciseId,custom) {
        busy=true
        val response=withContext(Dispatchers.IO) {
            runCatching{host.readPinnedExerciseNote(
                workoutId,exerciseId,custom)}.getOrElse {
                NativePinnedNoteRead.Rejected(
                    NativePinnedNoteRefusal.STORAGE_UNAVAILABLE)
            }
        }
        if(response is NativePinnedNoteRead.Current) {
            val fields=withContext(Dispatchers.IO) {
                runCatching {
                    host.reviewedPinnedExerciseSetupFields(
                        workoutId,exerciseId,custom,response.note.setup)
                }.getOrNull()
            }
            reviewedFields=fields ?:
                NativePinnedSetupFieldDomain.fields(null,response.note.setup)
            loaded=response.note
            if(!initialized) {
                draft=response.note.text
                setupJson=JSONObject(response.note.setup).toString()
                initialized=true
            }
            error=null
        } else error="Your current workout and saved exercise note could not be verified. Nothing has been changed."
        busy=false
    }

    val setup=remember(setupJson) {
        val values=JSONObject(setupJson)
        values.keys().asSequence()
            .associateWith{values.getString(it)}
    }
    val saved=loaded
    val dirty=saved!=null && (saved.text!=draft || saved.setup!=setup)
    val valid=NativePinnedExerciseNotePolicy.validContent(draft,setup)
    fun onEdited(){eventId=null;error=null}
    fun dismiss() {
        if(!busy) {
            if(dirty)confirmDiscard=true else onDismiss()
        }
    }
    fun commit() {
        val current=loaded?:return
        if(busy || !dirty || !valid)return
        val request=eventId ?: ("pinned-note-"+UUID.randomUUID())
            .also{eventId=it}
        val text=draft
        val selectedSetup=setup.toMap()
        busy=true
        error=null
        scope.launch {
            val response=withContext(Dispatchers.IO) {
                runCatching {
                    host.savePinnedExerciseNote(
                        workoutId,exerciseId,custom,current.revision,
                        request,text,selectedSetup)
                }.getOrElse {
                    NativePinnedNoteWrite.Rejected(
                        NativePinnedNoteRefusal.STORAGE_UNAVAILABLE)
                }
            }
            if(response is NativePinnedNoteWrite.Saved) {
                loaded=response.note
                busy=false
                onDismiss()
            } else {
                val recovered=withContext(Dispatchers.IO) {
                    runCatching {
                        host.readPinnedExerciseNote(workoutId,exerciseId,custom)
                    }.getOrNull()
                } as? NativePinnedNoteRead.Current
                val persisted=recovered?.note
                if(persisted!=null && persisted.lastEventId==request &&
                    persisted.revision==current.revision+1 &&
                    persisted.text==text && persisted.setup==selectedSetup) {
                    loaded=persisted
                    busy=false
                    onDismiss()
                } else {
                    // Reopening failure is NOT grounds to discard the draft.
                    error="Note not confirmed saved. Your edits remain here. "+
                        "If another editor changed this exercise, close and reopen "+
                        "to review the newer saved version before applying changes."
                    busy=false
                }
            }
        }
    }

    NativeEditSheet(
        busy=busy,
        onDismiss={dismiss()},
        canDismiss={
            if(dirty){confirmDiscard=true;false} else true
        }
    ) {
        Text("Pinned exercise note & setup",
            style=MaterialTheme.typography.headlineSmall)
        Text(kotlinExerciseLabel(exerciseId),
            style=MaterialTheme.typography.titleMedium)
        Text("Carries forward only for this exact exercise in your "+
            "signed-in native workouts. Setup values are your own notes—"+
            "check them against the equipment in your current gym.",
            style=MaterialTheme.typography.bodySmall,
            color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(loaded==null && busy)LinearProgressIndicator(Modifier.fillMaxWidth())
        if(loaded!=null) {
            val fields=reviewedFields.ifEmpty {
                NativePinnedSetupFieldDomain.fields(null,setup)
            }
            val firstSavedEarlier=fields.indexOfFirst{it.savedEarlier}
            fields.forEachIndexed {index,field ->
                if(index==firstSavedEarlier && firstSavedEarlier!=-1) {
                    Text("Saved earlier",
                        style=MaterialTheme.typography.titleSmall)
                    Text("These settings are no longer in the reviewed equipment fields. They are preserved until you explicitly clear them.",
                        style=MaterialTheme.typography.bodySmall,
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val value=setup[field.key].orEmpty()
                OutlinedTextField(
                    value=value,
                    onValueChange={ updated ->
                        val bounded=updated.take(
                            NativePinnedExerciseNotePolicy.MAX_SETUP_VALUE_CHARS+1)
                        val next=JSONObject(setupJson)
                        if(bounded.isBlank())next.remove(field.key)
                        else next.put(field.key,bounded)
                        setupJson=next.toString()
                        onEdited()
                    },
                    enabled=!busy,
                    singleLine=true,
                    isError=value.length>
                        NativePinnedExerciseNotePolicy.MAX_SETUP_VALUE_CHARS,
                    label={Text(field.label)},
                    supportingText=if(field.savedEarlier) ({
                        Text("Saved earlier · clear the value to remove")
                    }) else null,
                    modifier=Modifier.fillMaxWidth()
                )
            }
            if(!valid)Text("Check the note and setup values. Up to 32 setup fields, 200 characters per field.",
                style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.error)
            OutlinedTextField(
                value=draft,
                onValueChange={
                    draft=it.take(
                        NativePinnedExerciseNotePolicy.MAX_NOTE_CHARS+1)
                    onEdited()
                },
                enabled=!busy,
                minLines=3,maxLines=8,
                isError=!NativePinnedExerciseNotePolicy.validText(
                    draft,NativePinnedExerciseNotePolicy.MAX_NOTE_CHARS),
                label={Text("Pinned note")},
                supportingText={Text(draft.length.toString()+"/"+
                    NativePinnedExerciseNotePolicy.MAX_NOTE_CHARS+" characters")},
                modifier=Modifier.fillMaxWidth()
            )
        }
        error?.let{
            Text(it,color=MaterialTheme.colorScheme.error,
                style=MaterialTheme.typography.bodySmall)
        }
        Button(onClick={commit()},
            enabled=!busy && dirty && valid,
            modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {
            Text(if(busy)"Saving…" else "Save for this exercise")
        }
        Row(Modifier.fillMaxWidth(),
            horizontalArrangement=Arrangement.spacedBy(PursuitSpace.xs)) {
            OutlinedButton(onClick={
                draft="";setupJson="{}"
                onEdited()
            },enabled=!busy && (draft.isNotBlank() || setup.isNotEmpty()),
                modifier=Modifier.weight(1f).heightIn(min=48.dp)) {
                Text("Clear")
            }
            TextButton(onClick={dismiss()},
                enabled=!busy,
                modifier=Modifier.weight(1f).heightIn(min=48.dp)) {
                Text("Close")
            }
        }
    }
    if(confirmDiscard)AlertDialog(
        onDismissRequest={confirmDiscard=false},
        title={Text("Discard pinned changes?")},
        text={Text("The last saved note and setup will stay on this exercise.")},
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
