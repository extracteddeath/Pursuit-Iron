package io.github.extracteddeath.pursuitiron

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.extracteddeath.pursuitiron.rebuild.*

internal val NativeKotlinSupersetRequestSaver=listSaver<NativeKotlinSupersetEditRequest?,Any>(
    save={it?.savedState().orEmpty()},restore={NativeKotlinSupersetEditRequest.restore(it)})

@Composable
internal fun NativeKotlinSupersetEditorSheet(
    request:NativeKotlinSupersetEditRequest,draft:CustomTrainingCycle,
    allowSupersets:Boolean,sourceRevision:Long?,busy:Boolean,message:String?,
    needsSourceReload:Boolean,onSave:(List<String>)->Unit,onDismiss:()->Unit,onReload:()->Unit
) {
    val day=draft.blocks.firstOrNull{it.blockId==request.blockId}
        ?.days?.firstOrNull{it.weekday==request.weekday}
    val existing=day?.slots.orEmpty().filter{it.supersetGroup==request.groupId}.map{it.slotId}
    var selected by rememberSaveable(request.cycleId,request.cycleRevision,request.groupId) {
        mutableStateOf(ArrayList(existing))
    }
    var submitted by rememberSaveable(request.cycleId,request.cycleRevision,request.groupId) {
        mutableStateOf<ArrayList<String>?>(null)
    }
    val sameProgram=draft.cycleId==request.cycleId && draft.athleteId==request.athleteId
    val committed=submitted!=null && sameProgram && draft.revision==request.cycleRevision+1 &&
        existing.toSet()==submitted?.toSet()
    val stale=!committed && (!request.matches(draft) || sourceRevision!=request.sourceRevision ||
        day==null || needsSourceReload)
    val eligible=day?.slots.orEmpty().filter {slot->
        (slot.supersetGroup==null || slot.supersetGroup==request.groupId) &&
            slot.sets.any{it.type==DraftSetType.WORKING}
    }.map{it.slotId}.toSet()
    // Close only after the durable revision and exact requested membership
    // are visible. Failure keeps the athlete's choices and recovery message.
    LaunchedEffect(draft.revision,busy,submitted?.toList()) {
        if(!busy && committed)onDismiss()
    }
    NativeEditSheet(busy=busy,onDismiss=onDismiss) {
        PursuitSectionHeading(if(existing.isEmpty())"Create superset" else "Edit superset",
            "Choose two or three movements in ${day?.label ?:"this training day"}.")
        Text("Working sets alternate in the current exercise order. Sets, weights, amplifiers and week-specific edits are preserved. Review the program again before issuing another workout.",
            style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(stale)Text("The program or training setup changed. Close and reopen this editor to use the latest saved version.",
            style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.error)
        if(stale || needsSourceReload)OutlinedButton(onClick=onReload,enabled=!busy,
            modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {Text("Close and reload program")}
        if(!allowSupersets)Text("Supersets are disabled in your setup. You can unlink an existing group.",
            style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(submitted!=null && !busy && !message.isNullOrBlank())Text(message,
            style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        day?.slots.orEmpty().forEach {slot->
            val checked=slot.slotId in selected
            val available=slot.slotId in eligible
            FilterChip(selected=checked,
                onClick={selected=ArrayList(selected).apply {
                    if(checked)remove(slot.slotId) else add(slot.slotId)
                }},
                enabled=!busy && !stale && allowSupersets && available &&
                    (checked || selected.size<3),
                modifier=Modifier.fillMaxWidth().heightIn(min=48.dp),
                label={Text(kotlinExerciseLabel(slot.exerciseId)+
                    " · ${slot.sets.count{it.type==DraftSetType.WORKING}} working sets"+
                    (if(slot.role==ComposerRole.PRIMARY)" · main lift" else "")+
                    if(slot.supersetGroup!=null && slot.supersetGroup!=request.groupId)" · already paired" else "")})
        }
        if(selected.isNotEmpty())Text("Round order · "+day?.slots.orEmpty().filter{it.slotId in selected}
            .joinToString(" → "){kotlinExerciseLabel(it.exerciseId)}+
            ". The group starts at its first selected movement.",
            style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Text("${selected.size} of 2–3 movements selected. Cancel keeps the saved program.",
            style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick={val chosen=selected.toList();submitted=ArrayList(chosen);onSave(chosen)},
            enabled=!busy && !stale && allowSupersets && selected.size in 2..3 &&
                eligible.containsAll(selected) && selected.toSet()!=existing.toSet(),
            modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {
            Text(if(busy)"Saving superset…" else "Save superset")
        }
        if(existing.isNotEmpty())OutlinedButton(onClick={submitted=arrayListOf();onSave(emptyList())},
            enabled=!busy && !stale,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {
            Text("Unlink all movements")
        }
        TextButton(onClick=onDismiss,enabled=!busy,modifier=Modifier.heightIn(min=48.dp)){Text("Cancel")}
    }
}
