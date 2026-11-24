package io.github.extracteddeath.pursuitiron

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import java.math.BigDecimal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.extracteddeath.pursuitiron.rebuild.*

@Composable
internal fun NativeRecoveryRecipePanel(
    cycle:CustomTrainingCycle,state:NativeKotlinCustomCycleState,
    controller:NativeKotlinCustomCycleController,busy:Boolean,
    focusRequested:Boolean=false,onFocusConsumed:()->Unit={}
) {
    val eligible=cycle.blocks.filterIndexed{index,block->
        block.purpose==CustomBlockPurpose.TRAINING &&
            (index==cycle.blocks.lastIndex ||
                cycle.blocks.getOrNull(index+1)?.purpose==CustomBlockPurpose.TRAINING)
    }
    if(eligible.isEmpty())return
    var anchorId by rememberSaveable(cycle.athleteId,cycle.cycleId,cycle.revision){
        mutableStateOf(eligible.last().blockId)
    }
    val anchor=eligible.singleOrNull{it.blockId==anchorId} ?:eligible.last()
    val anchorIndex=cycle.blocks.indexOf(anchor)
    val boundary=cycle.blocks.take(anchorIndex+1).sumOf{it.weeks}
    val inserting=anchorIndex!=cycle.blocks.lastIndex
    val ready=state.fatigueCoachingEnabled && state.sourceRevision!=null &&
        state.auditSourceRevision==state.sourceRevision &&
        state.reviewedCoachInputs!=null &&
        state.coachingAudit?.status==CustomCoachAuditStatus.READY_FOR_ATHLETE_REVIEW &&
        state.coachingAudit?.structuralReview?.verifiedDraft==cycle
    var expanded by rememberSaveable(cycle.cycleId,cycle.revision){mutableStateOf(false)}
    val focusAnchor=remember {BringIntoViewRequester()}
    LaunchedEffect(focusRequested) {
        if(focusRequested) {
            expanded=true
            // The existing fatigue shortcut is for the final training week.
            eligible.lastOrNull{it.blockId==cycle.blocks.lastOrNull()?.blockId}
                ?.let{anchorId=it.blockId}
            withFrameNanos { }
            focusAnchor.bringIntoView()
            onFocusConsumed()
        }
    }
    var cap by rememberSaveable(cycle.cycleId,cycle.revision){mutableIntStateOf(2)}
    var rir by rememberSaveable(cycle.cycleId,cycle.revision){mutableIntStateOf(4)}
    val unit=state.reviewedSourceSetup?.reviewed?.gym?.unit
    var adjustLoad by rememberSaveable(cycle.cycleId,cycle.revision){mutableStateOf(false)}
    var budgetText by rememberSaveable(cycle.cycleId,cycle.revision,unit){mutableStateOf("")}
    val budget=budgetText.toBigDecimalOrNull()?.takeIf{
        it.signum()>0 && KotlinSetNumberAuthorityDomain.validPhysicalLoadValue(it)
    }
    val validBudget=!adjustLoad || (budget!=null && unit!=null)
    val reviewed=state.recoveryRecipeReview?.takeIf{
        it.request.cycleId==cycle.cycleId &&
            it.request.expectedCycleRevision==cycle.revision &&
            it.request.expectedSourceRevision==state.sourceRevision &&
            it.request.coaching==state.reviewedCoachInputs &&
            it.request.anchorBlockId==anchor.blockId &&
            it.request.insertAfterAnchor==inserting &&
            it.request.maximumWorkingSetsPerSlot==cap &&
            it.request.minimumTargetRir==rir && validBudget &&
            it.request.maximumEasierStep==(if(adjustLoad)budget else null) &&
            it.request.stepUnit==(if(adjustLoad)unit else null)
    }
    Column(Modifier.fillMaxWidth().bringIntoViewRequester(focusAnchor)) {
    PursuitCard {
        PursuitDisclosureRow(title="Plan a recovery week",
            subtitle=if(inserting)"Insert after ${anchor.label} · later weeks move by one"
                else "Append after ${anchor.label} · no existing weeks move",
            expanded=expanded,onToggle={expanded=!expanded})
        if(expanded) {
            if(eligible.size>1) {
                Text("Recovery after training block",style=MaterialTheme.typography.labelLarge)
                for(block in eligible) {
                    val lastWeek=cycle.blocks.take(cycle.blocks.indexOf(block)+1).sumOf{it.weeks}
                    Row(Modifier.fillMaxWidth(),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                        RadioButton(selected=anchor.blockId==block.blockId,
                            onClick={anchorId=block.blockId},enabled=!busy)
                        Text(block.label+" · after week "+lastWeek,
                            style=MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            if(inserting)Text("Week ${boundary+1} becomes recovery. Every later unissued week moves by one. Existing approvals and unfinished workouts must be resolved before saving.",
                style=MaterialTheme.typography.bodySmall)
            Text("Choose a set cap, minimum reserve and optional easier equipment setting. Amplifiers are removed only with permission. This is a draft proposal, not a scheduled automatic deload.",
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
            Row(Modifier.fillMaxWidth(),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                Text("Use one easier verified setting",modifier=Modifier.weight(1f),
                    style=MaterialTheme.typography.bodyMedium)
                Switch(checked=adjustLoad,onCheckedChange={adjustLoad=it},enabled=!busy && unit!=null)
            }
            if(adjustLoad) {
                OutlinedTextField(value=budgetText,onValueChange={input->
                    if(input.length<=10 && input.all{it.isDigit()||it=='.'})budgetText=input
                },label={Text("Maximum setting change (${unit?.name?.lowercase().orEmpty()})")},
                    singleLine=true,enabled=!busy,isError=budgetText.isNotBlank() && !validBudget,
                    keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),
                    modifier=Modifier.fillMaxWidth())
                Text("One attainable easier setting per weighted working set. Assisted movements use more counterweight. If any setting cannot be verified within your limit, no partial proposal is saved.",
                    style=MaterialTheme.typography.bodySmall)
            }
            OutlinedButton(onClick={controller.reviewRecoveryRecipe(cap,rir,
                if(adjustLoad)budget else null,if(adjustLoad)unit else null,
                anchor.blockId,inserting)},
                enabled=ready && !busy && validBudget,
                modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {Text("Preview recovery week")}
            if(!ready)Text("Review the full cycle and enable fatigue coaching first.",
                style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
            if(reviewed!=null) {
                HorizontalDivider()
                Text("Week ${reviewed.recovery.absoluteWeek} · ${reviewed.baseline.workingSets} → ${reviewed.recovery.workingSets} working sets",
                    style=MaterialTheme.typography.titleMedium)
                val block=reviewed.proposedCycle.blocks.single{it.blockId==reviewed.recovery.blockId}
                if(reviewed.request.insertAfterAnchor) {
                    Text("Shifted future blocks",style=MaterialTheme.typography.labelLarge)
                    var oldWeek=reviewed.recovery.absoluteWeek
                    for(later in cycle.blocks.drop(anchorIndex+1)) {
                        val end=oldWeek+later.weeks-1
                        Text(later.label+" · weeks "+oldWeek+"–"+end+" → "+(oldWeek+1)+"–"+(end+1),
                            style=MaterialTheme.typography.bodySmall)
                        oldWeek=end+1
                    }
                }
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
                    Text(if(inserting)"I reviewed recovery and the shifted weeks" else "I reviewed this recovery week",style=MaterialTheme.typography.bodyMedium)
                }
                Button(onClick={controller.acceptRecoveryRecipe(confirmed)},
                    enabled=ready && !busy && confirmed && validBudget,
                    modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {Text("Save reviewed recovery week")}
            }
        }
    }
    }
}
