package io.github.extracteddeath.pursuitiron

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
    lookupNames: suspend (ReadOnlyProgramReference,List<String>) -> Map<String,String>?,
    onClose: () -> Unit,
    onOpenGym: (String?) -> Unit,
    verifiedEquipmentExerciseIds: Set<String>,
    protectedSourceSlots: List<io.github.extracteddeath.pursuitiron.domain.NativeProtectedProgramSlot>
) {
    var confirmSave by remember { mutableStateOf(false) }
    var pendingRemoval by remember { mutableStateOf<Pair<String,Int>?>(null) }
    var confirmClose by remember { mutableStateOf(false) }
    var confirmOpenGym by remember { mutableStateOf(false) }
    var requestedGymExerciseId by remember { mutableStateOf<String?>(null) }
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
    var names by remember(approved.reference) { mutableStateOf<Map<String,String>>(emptyMap()) }
    LaunchedEffect(approved.reference) {
        val ids=approved.days.flatMap { it.exerciseIds }.distinct()
        if(ids.isNotEmpty()) {
            names=try { lookupNames(approved.reference,ids).orEmpty() }
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
    fun requestGymEdit(exerciseId: String?) {
        if(draft.isChanged || invalidInputs) {
            requestedGymExerciseId=exerciseId
            confirmOpenGym=true
        } else onOpenGym(exerciseId)
    }
    SectionLabel("Program editor", if(saving) "Saving changes…" else "Review and save your changes")
    PursuitCard(highlighted=true) {
        Text("Organize your existing training days",style=MaterialTheme.typography.titleLarge)
        Text("Rename, reorder or remove movements. Retained slot settings follow the movement. " +
            "Key lifts and linked pairs cannot be removed. Linked groups move as one; " +
            "individual moves across a pair are disabled.",
            style=MaterialTheme.typography.bodySmall,
            color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(!allowEditing && !saving) Text("Finish or resolve the active workout before modifying a plan.",
            color=MaterialTheme.colorScheme.error)
        OutlinedTextField(
            value=nameInput,
            onValueChange={ value->
                nameInput=value
                draft.renameProgram(value)?.let { draft=it }
            },
            enabled=allowEditing, label={Text("Program name")},
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
                    enabled=allowEditing && dayIndex>0
                ) { Text("Day up") }
                OutlinedButton(
                    onClick={ draft.moveDay(dayIndex,1)?.let { draft=it } },
                    enabled=allowEditing && dayIndex<draft.days.lastIndex
                ) { Text("Day down") }
            }
            OutlinedTextField(
                value=dayLabelsInput[day.id] ?: day.label,
                onValueChange={value->
                    dayLabelsInput=dayLabelsInput+(day.id to value)
                    draft.renameDay(day.id,value)?.let { draft=it }
                },
                enabled=allowEditing,label={Text("Training day name")},
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
                val protectedLoad=draft.wouldOrphanVerifiedLoading(
                    day.id,index,verifiedEquipmentExerciseIds)
                val protectedRole=draft.removalProtection(day.id,index,protectedSourceSlots)
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
                        enabled=allowEditing && draft.canMoveWithoutSplittingPair(
                            day.id,index,-1,protectedSourceSlots)
                    ) { Text("Up") }
                    OutlinedButton(
                        onClick={draft.moveExercise(day.id,index,1)?.let { draft=it }},
                        enabled=allowEditing && draft.canMoveWithoutSplittingPair(
                            day.id,index,1,protectedSourceSlots)
                    ) { Text("Down") }
                    OutlinedButton(
                        onClick={pendingRemoval=day.id to index},
                        enabled=allowEditing && !saving && day.exerciseIds.size>1 &&
                            !protectedLoad && protectedRole==null
                    ) { Text("Remove") }
                }
                val linkedBlock=draft.linkedBlock(day.id,index,protectedSourceSlots)
                if(linkedBlock!=null && linkedBlock.first==index) {
                    Row(modifier=Modifier.fillMaxWidth(),
                        horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick={
                            draft.moveLinkedBlock(day.id,index,-1,protectedSourceSlots)?.let { draft=it }
                        },enabled=allowEditing && !saving && draft.canMoveLinkedBlock(
                            day.id,index,-1,protectedSourceSlots),
                            modifier=Modifier.weight(1f)) { Text("Group up") }
                        OutlinedButton(onClick={
                            draft.moveLinkedBlock(day.id,index,1,protectedSourceSlots)?.let { draft=it }
                        },enabled=allowEditing && !saving && draft.canMoveLinkedBlock(
                            day.id,index,1,protectedSourceSlots),
                            modifier=Modifier.weight(1f)) { Text("Group down") }
                    }
                    Text("Move all ${linkedBlock.last-linkedBlock.first+1} linked movements together.",
                        style=MaterialTheme.typography.labelSmall,
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if(protectedRole!=null) Text(
                    when(protectedRole) {
                        "primary" -> "Key lift: this slot cannot be removed."
                        "primary_paired" -> "Key lift and linked pair: this slot cannot be removed or moved alone."
                        else -> "Linked movement: this slot cannot be removed or moved alone."
                    },
                    style=MaterialTheme.typography.bodySmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant
                )
                if(protectedLoad) {
                    Text(
                        "This is the last occurrence with a verified machine loading profile. " +
                            "Clear the specific weight setting and save before removing it.",
                        style=MaterialTheme.typography.bodySmall,
                        color=MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick={requestGymEdit(id)},
                        enabled=allowEditing && !saving,
                        modifier=Modifier.fillMaxWidth()
                    ) { Text("Edit this movement's verified weights") }
                }
                if(index<day.exerciseIds.lastIndex) HorizontalDivider()
            }
        }
    }
    PursuitCard {
        Text("Save this program",style=MaterialTheme.typography.titleMedium)
        Text("Changes apply to this program; ${draft.removedMovementCount} removal(s) pending. Workout history and other cycle blocks are kept.",
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
        OutlinedButton(onClick={requestGymEdit(null)},
            enabled=allowEditing && !saving,modifier=Modifier.fillMaxWidth()) {
            Text("Edit verified gym weights")
        }
    }
    pendingRemoval?.let { (dayId,index)->
        val movementId=draft.days.firstOrNull { it.id==dayId }?.exerciseIds?.getOrNull(index)
        if(movementId==null) pendingRemoval=null
        else AlertDialog(
            onDismissRequest={pendingRemoval=null},
            title={Text("Remove this movement?")},
            text={Text("Remove ${names[movementId] ?: movementId} from this day only? " +
                "Saved workouts stay. Primary and paired movements are protected. Clear any verified gym weight setting for the final occurrence of this movement before removing it.")},
            confirmButton={Button(onClick={
                draft.removeExercise(dayId,index)?.let { draft=it }
                pendingRemoval=null
            }) { Text("Remove movement") }},
            dismissButton={OutlinedButton(onClick={pendingRemoval=null}) { Text("Keep movement") }}
        )
    }
    if(confirmSave) AlertDialog(
        onDismissRequest={if(!saving)confirmSave=false},
        title={Text("Save program changes?")},
        text={Text("Save “${draft.name}” with ${draft.changedDayCount} changed training days. " +
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
    if(confirmOpenGym) AlertDialog(
        onDismissRequest={confirmOpenGym=false},
        title={Text("Leave program editor?")},
        text={Text("Unsaved program changes will be discarded. Your saved plan stays unchanged while you edit verified gym weights.")},
        confirmButton={Button(onClick={
            val requested=requestedGymExerciseId
            requestedGymExerciseId=null
            confirmOpenGym=false
            onOpenGym(requested)
        }) {Text("Discard and edit gym weights")}},
        dismissButton={OutlinedButton(onClick={confirmOpenGym=false}) {Text("Keep editing program")}}
    )
}
