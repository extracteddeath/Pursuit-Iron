package io.github.extracteddeath.pursuitiron

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.Saver
import io.github.extracteddeath.pursuitiron.domain.NativeProgramEditCodec
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.extracteddeath.pursuitiron.domain.CanonicalExercise
import io.github.extracteddeath.pursuitiron.domain.NativeProgramEditDraft
import io.github.extracteddeath.pursuitiron.domain.ReadOnlyProgramReference
import io.github.extracteddeath.pursuitiron.domain.ReadOnlyProgramPresentation

/**
 * Revision-pinned editing workspace. Saves are explicitly confirmed and
 * prepared by the packaged canonical editor through the atomic store owner.
 */
@Composable
internal fun NativeProgramDraftEditor(
    verified: ReadOnlyProgramPresentation.Structure,
    allowEditing: Boolean,
    saving: Boolean,
    saveError: String?,
    onSave: (NativeProgramEditDraft)->Unit,
    lookupNames: suspend (ReadOnlyProgramReference,List<String>) -> Map<String,CanonicalExercise>?,
    onClose: () -> Unit
) {
    var confirmSave by remember { mutableStateOf(false) }
    var pendingRemoval by remember { mutableStateOf<Pair<String,Int>?>(null) }
    var pendingReplacement by remember { mutableStateOf<Pair<String,Int>?>(null) }
    var confirmClose by remember { mutableStateOf(false) }
    val approved=verified.program
    val draftSaver=remember(approved.reference) { Saver<NativeProgramEditDraft,String>(
        save={NativeProgramEditCodec.workspaceJson(it)},
        restore={NativeProgramEditCodec.restoreWorkspace(verified,it)}
    ) }
    var draft by rememberSaveable(approved.reference,stateSaver=draftSaver) {
        mutableStateOf(NativeProgramEditDraft.from(verified))
    }
    // Input buffers are separate from the validated, immutable edit draft:
    // deleting a whole label while typing must not trap the text field.
    var nameInput by rememberSaveable(approved.reference) { mutableStateOf(approved.name) }
    val labelSaver=remember(approved.reference) { Saver<Map<String,String>,String>(
        save={org.json.JSONObject(it).toString()},
        restore={raw->runCatching {
            val rows=org.json.JSONObject(raw)
            approved.days.associate { it.id to rows.getString(it.id) }
        }.getOrNull()}
    ) }
    var dayLabelsInput by rememberSaveable(approved.reference,stateSaver=labelSaver) {
        mutableStateOf(approved.days.associate { it.id to it.label })
    }
    var catalog by remember(approved.reference) { mutableStateOf<Map<String,CanonicalExercise>>(emptyMap()) }
    val names=catalog.mapValues { it.value.name }
    val catalogCandidates=approved.days.flatMap { it.exerciseIds }.distinct().mapNotNull { catalog[it] }
    fun compatibleCandidates(dayIds: List<String>,oldId: String): List<CanonicalExercise> {
        val source=catalog[oldId] ?: return emptyList()
        return catalogCandidates.filter { ex ->
            ex.id!=oldId && ex.id !in dayIds && ex.muscleGroup==source.muscleGroup &&
                ex.type==source.type &&
                (ex.movementPattern==null || source.movementPattern==null ||
                    ex.movementPattern==source.movementPattern)
        }.sortedBy { it.name }.take(8)
    }
    LaunchedEffect(approved.reference) {
        val ids=approved.days.flatMap { it.exerciseIds }.distinct()
        if(ids.isNotEmpty()) {
            catalog=try { lookupNames(approved.reference,ids).orEmpty() }
                catch(cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                catch(_: Exception) { emptyMap() }
        }
    }
    if(!draft.matchesCurrent(verified)) {
        Text("Your saved program changed. Close this draft and reopen it from the current Plan.",
            color=MaterialTheme.colorScheme.error)
        OutlinedButton(onClick=onClose) { Text("Close outdated draft") }
        return
    }
    val invalidInputs = nameInput != draft.name ||
        draft.days.any { dayLabelsInput[it.id] != it.label }
    SectionLabel("Program editor", if(saving) "Saving changes…" else "Review and save your changes")
    PursuitCard(highlighted=true) {
        Text("Organize your existing training days",style=MaterialTheme.typography.titleLarge)
        Text("Organize movements or replace one accessory with a compatible exercise already in this program. A replacement starts without the previous exercise's progression.",
            style=MaterialTheme.typography.bodySmall,
            color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(draft.replacement!=null) Text("Replacement selected: review or reset before making another change.",
            color=MaterialTheme.colorScheme.primary)
        if(!allowEditing && !saving) Text("Finish or resolve the active workout before modifying a plan.",
            color=MaterialTheme.colorScheme.error)
        OutlinedTextField(
            value=nameInput,
            onValueChange={ value->
                nameInput=value
                draft.renameProgram(value)?.let { draft=it }
            },
            enabled=allowEditing && draft.replacement==null, label={Text("Program name")},
            isError=nameInput!=draft.name,
            supportingText={
                if(nameInput!=draft.name)
                    Text("Enter a non-empty name of up to 120 characters.")
            },
            singleLine=true,modifier=Modifier.fillMaxWidth()
        )
        Text("${draft.changedDayCount} training day position(s)/detail(s) changed · " +
            when {
                invalidInputs -> "Fix highlighted names"
                draft.isChanged -> "Unsaved draft"
                else -> "Matches saved plan"
            },
            style=MaterialTheme.typography.labelMedium)
    }
    draft.days.forEachIndexed { dayIndex,day ->
        PursuitCard {
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("Day ${dayIndex+1}",style=MaterialTheme.typography.labelLarge,
                    modifier=Modifier.weight(1f))
                OutlinedButton(
                    onClick={ draft.moveDay(dayIndex,-1)?.let { draft=it } },
                    enabled=allowEditing && draft.replacement==null && dayIndex>0
                ) { Text("Day up") }
                OutlinedButton(
                    onClick={ draft.moveDay(dayIndex,1)?.let { draft=it } },
                    enabled=allowEditing && draft.replacement==null && dayIndex<draft.days.lastIndex
                ) { Text("Day down") }
            }
            OutlinedTextField(
                value=dayLabelsInput[day.id] ?: day.label,
                onValueChange={value->
                    dayLabelsInput=dayLabelsInput+(day.id to value)
                    draft.renameDay(day.id,value)?.let { draft=it }
                },
                enabled=allowEditing && draft.replacement==null,label={Text("Training day name")},
                isError=(dayLabelsInput[day.id] ?: day.label)!=day.label,
                supportingText={
                    if((dayLabelsInput[day.id] ?: day.label)!=day.label)
                        Text("Enter a non-empty day name of up to 120 characters.")
                },
                singleLine=true,modifier=Modifier.fillMaxWidth()
            )
            Text("${day.exerciseIds.size} movements",
                style=MaterialTheme.typography.labelSmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
            day.exerciseIds.forEachIndexed { index,id ->
                Row(modifier=Modifier.fillMaxWidth(),
                    horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    Column(modifier=Modifier.weight(1f)) {
                        Text("Movement ${index+1}",style=MaterialTheme.typography.labelSmall)
                        Text(names[id] ?: "Movement name unavailable",
                            style=MaterialTheme.typography.bodyMedium)
                        if(names[id]==null) Text("Movement ID: $id",
                            style=MaterialTheme.typography.labelSmall,
                            color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    OutlinedButton(
                        onClick={draft.moveExercise(day.id,index,-1)?.let { draft=it }},
                        enabled=allowEditing && draft.replacement==null && index>0
                    ) { Text("Up") }
                    OutlinedButton(
                        onClick={draft.moveExercise(day.id,index,1)?.let { draft=it }},
                        enabled=allowEditing && draft.replacement==null && index<day.exerciseIds.lastIndex
                    ) { Text("Down") }
                    OutlinedButton(
                        onClick={pendingRemoval=day.id to index},
                        enabled=allowEditing && !saving && draft.replacement==null &&
                            day.exerciseIds.size>1
                    ) { Text("Remove") }
                    OutlinedButton(
                        onClick={pendingReplacement=day.id to index},
                        enabled=allowEditing && !saving && !draft.isChanged && index>0 &&
                            compatibleCandidates(day.exerciseIds,id).isNotEmpty()
                    ) { Text("Replace") }
                }
                if(index<day.exerciseIds.lastIndex) HorizontalDivider()
            }
        }
    }
    PursuitCard {
        Text("Save this program",style=MaterialTheme.typography.titleMedium)
        Text(if(draft.replacement!=null)
            "One accessory replacement pending. Old exercise history is kept, but its load and progression are not transferred."
            else "Changes apply to this program; ${draft.removedMovementCount} removal(s) pending. Workout history and other cycle blocks are kept.",
            style=MaterialTheme.typography.bodySmall)
            style=MaterialTheme.typography.bodySmall)
        saveError?.let { Text(it,color=MaterialTheme.colorScheme.error) }
        Button(onClick={confirmSave=true},enabled=allowEditing && !saving && draft.isChanged && !invalidInputs,
            modifier=Modifier.fillMaxWidth()) {
            Text(if(saving) "Saving changes…" else "Review program changes")
        }
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick={
                draft=draft.reset()
                nameInput=draft.name
                dayLabelsInput=draft.days.associate { it.id to it.label }
            },enabled=allowEditing && (draft.isChanged || invalidInputs)) {
                Text("Reset draft")
            }
            OutlinedButton(onClick={
                if(draft.isChanged || invalidInputs)confirmClose=true else onClose()
            },enabled=!saving) { Text("Close editor") }
        }
    }
    pendingRemoval?.let { (dayId,index)->
        val movementId=draft.days.firstOrNull { it.id==dayId }?.exerciseIds?.getOrNull(index)
        if(movementId==null) pendingRemoval=null
        else AlertDialog(
            onDismissRequest={pendingRemoval=null},
            title={Text("Remove this movement?")},
            text={Text("Remove ${names[movementId] ?: movementId} from this day only? " +
                "Saved workouts stay. Removing a protected primary or paired movement is refused on Save.")},
            confirmButton={Button(onClick={
                draft.removeExercise(dayId,index)?.let { draft=it }
                pendingRemoval=null
            }) { Text("Remove movement") }},
            dismissButton={OutlinedButton(onClick={pendingRemoval=null}) { Text("Keep movement") }}
        )
    }
    pendingReplacement?.let { (dayId,index)->
        val targetDay=draft.days.firstOrNull { it.id==dayId }
        val oldId=targetDay?.exerciseIds?.getOrNull(index)
        val choices=if(oldId==null) emptyList()
            else compatibleCandidates(targetDay.exerciseIds,oldId)
        AlertDialog(
            onDismissRequest={pendingReplacement=null},
            title={Text("Replace accessory movement")},
            text={
                Column(modifier=Modifier.heightIn(max=340.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text("Choose a similar movement from another training day. The existing load and progression won't carry over.",
                        style=MaterialTheme.typography.bodySmall)
                    choices.forEach { ex ->
                        OutlinedButton(onClick={
                            draft.substituteExercise(dayId,index,ex.id)?.let { draft=it }
                            pendingReplacement=null
                        },enabled=allowEditing && !saving,
                            modifier=Modifier.fillMaxWidth()) { Text(ex.name) }
                    }
                    if(choices.isEmpty()) Text("No compatible movement is available in this program.")
                }
            },
            confirmButton={OutlinedButton(onClick={pendingReplacement=null}) { Text("Cancel") }}
        )
    }
    if(confirmSave) AlertDialog(
        onDismissRequest={if(!saving)confirmSave=false},
        title={Text(if(draft.replacement!=null) "Save accessory replacement?" else "Save program changes?")},
        text={Text(if(draft.replacement!=null)
            "Use the selected replacement in future workouts. Keep all past workout history. Do not carry over the old movement's training loads, progression or amplifiers."
            else "Save “${draft.name}” with ${draft.changedDayCount} changed training days. " +
                "Paired exercises must stay together. Your saved workouts are kept.")},
        confirmButton={Button(onClick={confirmSave=false;onSave(draft)},enabled=allowEditing && !saving) {
            Text("Save changes")
        }},
        dismissButton={OutlinedButton(onClick={confirmSave=false},enabled=!saving) {Text("Keep editing")}}
    )
    if(confirmClose) AlertDialog(
        onDismissRequest={confirmClose=false},title={Text("Discard unsaved changes?")},
        text={Text("Your saved program will stay as it is.")},
        confirmButton={Button(onClick={confirmClose=false;onClose()}) {Text("Discard changes")}},
        dismissButton={OutlinedButton(onClick={confirmClose=false}) {Text("Keep editing")}}
    )
}
