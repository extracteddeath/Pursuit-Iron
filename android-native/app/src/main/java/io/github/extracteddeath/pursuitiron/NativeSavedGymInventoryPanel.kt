package io.github.extracteddeath.pursuitiron

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.extracteddeath.pursuitiron.domain.NativeGymInventoryCodec
import io.github.extracteddeath.pursuitiron.domain.NativeCoachVerifiedLoadEvidence
import io.github.extracteddeath.pursuitiron.domain.NativeSavedProgram
import io.github.extracteddeath.pursuitiron.domain.ReadOnlyProgramReference

/** Revision-bound real equipment editing. No inferred rack or training prescriptions. */
@Composable
internal fun NativeSavedGymInventoryPanel(
    saved: NativeSavedProgram,
    reference: ReadOnlyProgramReference,
    enabled: Boolean,
    busy: Boolean,
    error: String?,
    message: String?,
    onSave: (ReadOnlyProgramReference, String?)->Unit,
    initiallyEditing: Boolean = false,
    onClose: (() -> Unit)? = null,
    focusExerciseId: String? = null
) {
    val unit=saved.profile.unit
    // Evidence is read-only and source-bound to the authenticated saved
    // program shown here. It does not select weights or approve progression.
    val reviewedLoads=remember(saved.loadingInventory,saved.loadingCandidates,unit) {
        runCatching {
            NativeCoachVerifiedLoadEvidence.review(
                saved.loadingInventory,unit,saved.loadingCandidates)
        }.getOrNull()
    }
    val original=NativeGymInventoryCodec.toDraft(saved.loadingInventory)
    var editing by rememberSaveable(reference.snapshotSha256) { mutableStateOf(initiallyEditing) }
    var pending by rememberSaveable(reference.snapshotSha256) { mutableStateOf(false) }
    var confirmExit by rememberSaveable(reference.snapshotSha256) { mutableStateOf(false) }
    var draft by rememberSaveable(reference.snapshotSha256) { mutableStateOf(HashMap(original)) }
    val result=NativeGymInventoryCodec.fromDraft(draft,unit,saved.loadingCandidates.map { it.id }.toSet(),
        saved.loadingCandidates.filter { it.mode=="external_load" }.map { it.id }.toSet())
    val changed=draft.filterValues { it.isNotBlank() }!=original.filterValues { it.isNotBlank() }

    PursuitCard {
        Text("Gym loading settings",style=MaterialTheme.typography.titleMedium)
        Text(
            if(saved.loadingInventory==null) "No verified weight inventory. Enter your sets manually."
            else "Your confirmed weight settings are saved with this program.",
            style=MaterialTheme.typography.bodyMedium
        )
        if(reviewedLoads==null) Text(
            "Machine loading evidence could not be verified. Use manual weight entry until your gym settings are reviewed.",
            style=MaterialTheme.typography.bodySmall,
            color=MaterialTheme.colorScheme.error
        ) else if(reviewedLoads.candidateCount>0) Text(
            "${reviewedLoads.facts.size} of ${reviewedLoads.candidateCount} eligible program exercises have individually verified weight settings. Unverified exercises remain manual.",
            style=MaterialTheme.typography.bodySmall,
            color=MaterialTheme.colorScheme.onSurfaceVariant
        )
        error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
        message?.let { Text(it,color=MaterialTheme.colorScheme.primary) }
        if(!editing) {
            OutlinedButton(onClick={ editing=true },enabled=enabled && !busy,
                modifier=Modifier.fillMaxWidth()) {
                Text(if(saved.loadingInventory==null) "Set up actual weights" else "Edit actual weights")
            }
        } else {
            NativeGymInventoryEditor(unit,draft,{ key,value ->
                draft=HashMap(draft).apply { put(key,value) }
            },result,enabled && !busy,saved.loadingCandidates,
                initiallyExpanded=initiallyEditing,
                focusExerciseId=focusExerciseId?.takeIf { id ->
                    saved.loadingCandidates.any { it.id==id }
                })
            Text("Only verified physical settings change. Exercises, current prescriptions, and history remain unchanged.",
                style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick={pending=true},
                enabled=enabled && !busy && result.valid && changed,
                modifier=Modifier.fillMaxWidth()) {
                Text(if(busy) "Saving…" else if(result.json==null) "Remove verified weight setup"
                    else "Review weight setup changes")
            }
            OutlinedButton(onClick={
                draft=HashMap(original)
                pending=false
                editing=false
            },enabled=!busy,modifier=Modifier.fillMaxWidth()) {
                Text("Cancel changes")
            }
        }
        if(onClose!=null) OutlinedButton(
            onClick={if(changed)confirmExit=true else onClose()},
            enabled=!busy,modifier=Modifier.fillMaxWidth()
        ) { Text("Back to program") }
    }
    if(confirmExit && onClose!=null) AlertDialog(
        onDismissRequest={confirmExit=false},
        title={Text("Discard unsaved gym changes?")},
        text={Text("The verified weight settings already saved with your program will stay unchanged.")},
        confirmButton={Button(onClick={confirmExit=false;onClose()}) {
            Text("Discard and return")
        }},
        dismissButton={TextButton(onClick={confirmExit=false}) { Text("Keep editing weights") }}
    )
    if(pending) AlertDialog(
        onDismissRequest={pending=false},
        title={Text(if(result.json==null) "Remove verified gym weights?" else "Save verified gym weights?")},
        text={Text(
            if(result.json==null) "Weight steps will revert to manual entry. Your logged workouts and prescriptions will not change."
            else "Only physical equipment facts for this saved program will change. Existing workouts and training history stay intact."
        )},
        confirmButton={
            Button(onClick={pending=false;onSave(reference,result.json)},
                enabled=enabled && !busy && result.valid && changed) {
                Text(if(result.json==null) "Remove weight settings" else "Save gym weights")
            }
        },
        dismissButton={TextButton(onClick={pending=false}) { Text("Keep editing") }}
    )
}
