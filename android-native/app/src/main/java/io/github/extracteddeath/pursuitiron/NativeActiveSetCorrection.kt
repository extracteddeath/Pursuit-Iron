package io.github.extracteddeath.pursuitiron

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import io.github.extracteddeath.pursuitiron.domain.*
import java.util.UUID

/** Edits replace one explicitly selected receipt; original evidence stays in the journal. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NativeActiveSetCorrection(session: JournalSessionKey,set: NativeWorkoutSet,commit: JournalSetCommit,
    busy: Boolean,error: String?,onCorrect: (JournalSetIntent)->Unit) {
    var open by rememberSaveable(commit.intent.operationId) { mutableStateOf(false) }
    TextButton(onClick={open=true},enabled=!busy) {
        Text(if(commit.intent.disposition==JournalSetDisposition.SKIPPED)"Record skipped set" else "Edit saved set")
    }
    if(!open)return
    NativeSetEditSheet(busy,onDismiss={open=false}) {
            val actual=commit.intent.values
            var load by rememberSaveable(commit.intent.operationId) { mutableStateOf(actual?.load ?: set.load) }
            var reps by rememberSaveable(commit.intent.operationId) { mutableStateOf(actual?.reps?.toString() ?: set.reps) }
            var rir by rememberSaveable(commit.intent.operationId) { mutableStateOf(actual?.rir?.toString() ?: "") }
            var operation by rememberSaveable(commit.intent.operationId) { mutableStateOf<String?>(null) }
            var pendingDisposition by rememberSaveable(commit.intent.operationId) { mutableStateOf<String?>(null) }
            var confirmSkip by rememberSaveable(commit.intent.operationId) { mutableStateOf(false) }
            Text("Correct saved set",style=MaterialTheme.typography.headlineSmall)
            Text(if(set.isExtension)if(set.kind=="myo")"Myo-rep mini set" else "Drop set"
                else "Set ${set.slot.setIndex+1}",style=MaterialTheme.typography.titleMedium)
            NativeSetValueFields(session.unit,load,reps,rir,!busy,
                {load=it;operation=null;pendingDisposition=null},{reps=it;operation=null;pendingDisposition=null},
                {rir=it;operation=null;pendingDisposition=null})
            val repCount=reps.toIntOrNull();val effort=rir.toIntOrNull()
            val valid=load.matches(Regex("^(0|[1-9][0-9]{0,4})(\\.[0-9]{1,3})?$")) &&
                repCount!=null && repCount in 1..100 && (rir.isEmpty() || effort!=null && effort in 0..10)
            val changed=commit.intent.disposition==JournalSetDisposition.SKIPPED ||
                load.toBigDecimalOrNull()?.compareTo(actual?.load?.toBigDecimalOrNull() ?: java.math.BigDecimal.ZERO)!=0 ||
                repCount!=actual?.reps || effort!=actual?.rir
            fun submit(disposition: JournalSetDisposition,values: JournalSetValues?) {
                val id=operation?.takeIf { pendingDisposition==disposition.name } ?:
                    ("edit-"+UUID.randomUUID()).also { operation=it;pendingDisposition=disposition.name }
                onCorrect(JournalSetIntent(id,set.slot,disposition,values,commit.intent.operationId))
            }
            error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
            Button(onClick={if(pendingDisposition==JournalSetDisposition.SKIPPED.name)
                submit(JournalSetDisposition.SKIPPED,null) else submit(JournalSetDisposition.COMPLETED,
                    JournalSetValues(load,session.unit,checkNotNull(repCount),effort))},
                enabled=!busy && valid && (changed || operation!=null),modifier=Modifier.fillMaxWidth()) {
                Text(if(busy)"Saving…" else if(operation!=null)"Retry correction" else "Save correction")
            }
            if(commit.intent.disposition==JournalSetDisposition.COMPLETED)TextButton(
                onClick={confirmSkip=true},enabled=!busy,modifier=Modifier.fillMaxWidth()) { Text("Mark as skipped") }
            TextButton(onClick={open=false},enabled=!busy,modifier=Modifier.fillMaxWidth()) { Text("Cancel") }
            if(confirmSkip)AlertDialog(onDismissRequest={if(!busy)confirmSkip=false},
                title={Text("Mark this set as skipped?")},text={Text("It will no longer count as completed work. The original entry is kept.")},
                confirmButton={Button(onClick={confirmSkip=false;submit(JournalSetDisposition.SKIPPED,null)},
                    enabled=!busy) { Text("Mark skipped") }},
                dismissButton={TextButton(onClick={confirmSkip=false},enabled=!busy) { Text("Keep set") }})
    }
}
