package io.github.extracteddeath.pursuitiron

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.extracteddeath.pursuitiron.rebuild.*

@Composable
internal fun NativeRecoveryRecipePanel(
    cycle:CustomTrainingCycle,state:NativeKotlinCustomCycleState,
    controller:NativeKotlinCustomCycleController,busy:Boolean
) {
    val anchor=cycle.blocks.lastOrNull()?.takeIf{it.purpose==CustomBlockPurpose.TRAINING}
        ?:return
    val ready=state.fatigueCoachingEnabled && state.sourceRevision!=null &&
        state.auditSourceRevision==state.sourceRevision &&
        state.reviewedCoachInputs!=null &&
        state.coachingAudit?.status==CustomCoachAuditStatus.READY_FOR_ATHLETE_REVIEW &&
        state.coachingAudit?.structuralReview?.verifiedDraft==cycle
    var expanded by rememberSaveable(cycle.cycleId,cycle.revision){mutableStateOf(false)}
    var cap by rememberSaveable(cycle.cycleId,cycle.revision){mutableIntStateOf(2)}
    var rir by rememberSaveable(cycle.cycleId,cycle.revision){mutableIntStateOf(4)}
    val reviewed=state.recoveryRecipeReview?.takeIf{
        it.request.cycleId==cycle.cycleId &&
            it.request.expectedCycleRevision==cycle.revision &&
            it.request.expectedSourceRevision==state.sourceRevision &&
            it.request.coaching==state.reviewedCoachInputs &&
            it.request.maximumWorkingSetsPerSlot==cap &&
            it.request.minimumTargetRir==rir
    }
    PursuitCard {
        PursuitDisclosureRow(title="Plan a recovery week",
            subtitle="Append after ${anchor.label} · no existing weeks move",
            expanded=expanded,onToggle={expanded=!expanded})
        if(expanded) {
            Text("Choose a set cap and minimum reserve. Retained weights stay the same; amplifiers are removed only with permission. This is a draft proposal, not a scheduled automatic deload.",
                style=MaterialTheme.typography.bodySmall)
            Text("Maximum working sets per exercise",style=MaterialTheme.typography.labelLarge)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(PursuitSpace.xs)) {
                for(value in 1..3) FilterChip(selected=cap==value,
                    onClick={cap=value},enabled=!busy,label={Text(value.toString())})
            }
            Text("Minimum target RIR",style=MaterialTheme.typography.labelLarge)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(PursuitSpace.xs)) {
                for(value in 3..5) FilterChip(selected=rir==value,
                    onClick={rir=value},enabled=!busy,label={Text(value.toString())})
            }
            OutlinedButton(onClick={controller.reviewRecoveryRecipe(cap,rir)},
                enabled=ready && !busy,
                modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {Text("Preview recovery week")}
            if(!ready)Text("Review the full cycle and enable fatigue coaching first.",
                style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
            if(reviewed!=null) {
                HorizontalDivider()
                Text("Week ${reviewed.recovery.absoluteWeek} · ${reviewed.baseline.workingSets} → ${reviewed.recovery.workingSets} working sets",
                    style=MaterialTheme.typography.titleMedium)
                val block=reviewed.proposedCycle.blocks.last()
                for(day in block.days) {
                    Text(day.label,style=MaterialTheme.typography.labelLarge)
                    for(slot in day.slots) {
                        val work=slot.sets.filter{it.type==DraftSetType.WORKING}
                        Text(kotlinExerciseLabel(slot.exerciseId)+
                            (slot.machineInstanceId?.let{" · bound station "+it} ?: "")+
                            " · "+work.joinToString("; "){set->
                            val load=when(val value=set.load) {
                                is DraftSetLoad.Verified -> value.value.stripTrailingZeros().toPlainString()+" "+
                                    value.unit.name.lowercase()+" "+kotlinLoadBasisLabel(value.basis)
                                DraftSetLoad.ExplicitBodyweight -> "bodyweight"
                                else -> "unresolved"
                            }
                            val reps=set.exactRepTarget?.toString()
                                ?: "${set.reps.minimum}–${set.reps.maximum}"
                            "$load × $reps · RIR ${set.targetRir}"
                        },style=MaterialTheme.typography.bodySmall)
                    }
                }
                Text("Saving adds this week to the draft and requires renewed program approval. Issued workouts and recorded history are unchanged.",
                    style=MaterialTheme.typography.bodySmall)
                var confirmed by remember(reviewed){mutableStateOf(false)}
                Row(Modifier.fillMaxWidth(),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                    Checkbox(checked=confirmed,onCheckedChange={confirmed=it},enabled=!busy)
                    Text("I reviewed this recovery week",style=MaterialTheme.typography.bodyMedium)
                }
                Button(onClick={controller.acceptRecoveryRecipe(confirmed)},
                    enabled=ready && !busy && confirmed,
                    modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {Text("Save reviewed recovery week")}
            }
        }
    }
}
