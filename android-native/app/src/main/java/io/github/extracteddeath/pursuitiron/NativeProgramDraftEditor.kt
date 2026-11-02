package io.github.extracteddeath.pursuitiron

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.extracteddeath.pursuitiron.domain.NativeProgramEditDraft
import io.github.extracteddeath.pursuitiron.domain.ReadOnlyProgramReference
import io.github.extracteddeath.pursuitiron.domain.ReadOnlyProgramPresentation

/**
 * Native, revision-pinned program edit workspace. Not a canonical editor.
 * A draft must never become a prescription or a saved program without a
 * future engine-certified edit preparation and guarded owner transaction.
 */
@Composable
internal fun NativeProgramDraftEditor(
    verified: ReadOnlyProgramPresentation.Structure,
    allowEditing: Boolean,
    lookupNames: suspend (ReadOnlyProgramReference,List<String>) -> Map<String,String>?,
    onClose: () -> Unit
) {
    val approved=verified.program
    var draft by remember(approved.reference) {
        mutableStateOf(NativeProgramEditDraft.from(verified))
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
    SectionLabel("Program editor", "Local draft · no saved changes")
    PursuitCard(highlighted=true) {
        Text("Organize your existing training days",style=MaterialTheme.typography.titleLarge)
        Text("Exercise identities stay attached to their original day. Nothing here changes your saved program, set targets or workout history.",
            style=MaterialTheme.typography.bodySmall,
            color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(!allowEditing) Text("Finish or resolve the active workout before modifying a plan.",
            color=MaterialTheme.colorScheme.error)
        OutlinedTextField(
            value=draft.name,
            onValueChange={ value->draft.renameProgram(value)?.let { draft=it } },
            enabled=allowEditing, label={Text("Program name")},
            singleLine=true,modifier=Modifier.fillMaxWidth()
        )
        Text("${draft.changedDayCount} training day(s) changed · " +
            if(draft.isChanged)"Unsaved draft" else "Matches saved plan",
            style=MaterialTheme.typography.labelMedium)
    }
    draft.days.forEachIndexed { dayIndex,day ->
        PursuitCard {
            Text("Day ${dayIndex+1}",style=MaterialTheme.typography.labelLarge)
            OutlinedTextField(
                value=day.label,
                onValueChange={value->draft.renameDay(day.id,value)?.let { draft=it }},
                enabled=allowEditing,label={Text("Training day name")},
                singleLine=true,modifier=Modifier.fillMaxWidth()
            )
            Text("${day.exerciseIds.size} original movements · IDs unchanged",
                style=MaterialTheme.typography.labelSmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
            day.exerciseIds.forEachIndexed { index,id ->
                Row(modifier=Modifier.fillMaxWidth(),
                    horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    Column(modifier=Modifier.weight(1f)) {
                        Text("Movement ${index+1}",style=MaterialTheme.typography.labelSmall)
                        Text(names[id] ?: id.replace('_',' ').replace('-',' '),
                            style=MaterialTheme.typography.bodyMedium)
                        if(names[id]==null) Text("Movement ID: $id",
                            style=MaterialTheme.typography.labelSmall,
                            color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    OutlinedButton(
                        onClick={draft.moveExercise(day.id,index,-1)?.let { draft=it }},
                        enabled=allowEditing && index>0
                    ) { Text("Up") }
                    OutlinedButton(
                        onClick={draft.moveExercise(day.id,index,1)?.let { draft=it }},
                        enabled=allowEditing && index<day.exerciseIds.lastIndex
                    ) { Text("Down") }
                }
                if(index<day.exerciseIds.lastIndex) HorizontalDivider()
            }
        }
    }
    PursuitCard {
        Text("Review only — not saved",style=MaterialTheme.typography.titleMedium)
        Text("Renaming or reordering is a draft. The next integration step must obtain a fresh canonical engine validation, resolve equipment and adaptive-cycle constraints, and atomically update only the expected saved-program revision.",
            style=MaterialTheme.typography.bodySmall)
        Button(onClick={},enabled=false,modifier=Modifier.fillMaxWidth()) {
            Text("Save program edits · not connected")
        }
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick={draft=draft.reset()},enabled=allowEditing && draft.isChanged) {
                Text("Reset draft")
            }
            OutlinedButton(onClick=onClose) { Text("Close editor") }
        }
    }
}
