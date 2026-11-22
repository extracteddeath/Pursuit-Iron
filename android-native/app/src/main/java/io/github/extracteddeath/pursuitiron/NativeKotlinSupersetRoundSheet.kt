package io.github.extracteddeath.pursuitiron

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.extracteddeath.pursuitiron.rebuild.*

/** One read-only projection of the canonical issued custom workout order. */
internal object NativeSupersetRoundReview {
    fun rounds(journal:CustomWorkoutJournalDraft,groupId:String):
        List<List<CustomWorkoutExecutionStep>> {
        if(groupId.isBlank())return emptyList()
        return CustomWorkoutExecutionDomain.orderedSets(journal.snapshot)
            .filter{it.round?.groupId==groupId}
            .groupBy{checkNotNull(it.round).number}.values.toList()
    }

    data class SetReview(val visit:CustomWorkoutVisitKey,val outcome:String)

    fun review(journal:CustomWorkoutJournalDraft,set:CustomWorkoutSetSnapshot):SetReview {
        val main=CustomWorkoutVisitKey(set.setKey,CustomWorkoutVisitPart.MAIN)
        fun outcome(key:CustomWorkoutVisitKey)=(journal.effectiveVisits[key]?.operation
            as? CustomWorkoutJournalOperation.Record)?.outcome
        when(outcome(main)) {
            null -> return SetReview(main,"Not logged")
            KotlinJournalSetOutcome.SKIPPED -> return SetReview(main,"Skipped")
            KotlinJournalSetOutcome.FAILED_ATTEMPT -> return SetReview(main,"Failed attempt")
            KotlinJournalSetOutcome.COMPLETED -> Unit
        }
        // The engine exposes stages in order and stops at the first pending
        // stage or protocol stop. An unsafe drop can yield no follow-up.
        val last=CustomWorkoutExecutionDomain.followUpVisits(journal,set).lastOrNull()
            ?: return SetReview(main,"Completed")
        val multi=(set.prescription.amplifier?.followUpRounds ?: 1)>1
        val label=if(multi)"Follow-up ${last.stage}" else "Amplifier follow-up"
        return when(outcome(last)) {
            null -> SetReview(last,"$label due")
            KotlinJournalSetOutcome.COMPLETED -> SetReview(main,
                if(multi)"Main + ${last.stage} follow-up${if(last.stage==1) "" else "s"} completed"
                else "Main + follow-up completed")
            KotlinJournalSetOutcome.SKIPPED -> SetReview(main,
                if(multi)"Main completed · follow-up ${last.stage} skipped"
                else "Main completed · follow-up skipped")
            KotlinJournalSetOutcome.FAILED_ATTEMPT -> SetReview(main,
                if(multi)"Main completed · follow-up ${last.stage} failed"
                else "Main completed · follow-up failed")
        }
    }

}

/**
 * A sheet from Focus; only current issued targets and correction-aware
 * outcomes are shown. Selecting a visit never saves sets or edits the plan.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NativeKotlinSupersetRoundSheet(
    journal:CustomWorkoutJournalDraft,groupId:String,
    focus:CustomWorkoutVisitKey?,busy:Boolean,
    onChoose:(CustomWorkoutVisitKey)->Unit,onDismiss:()->Unit
) {
    val rounds=remember(journal.revision,journal.snapshot,groupId) {
        NativeSupersetRoundReview.rounds(journal,groupId)
    }
    ModalBottomSheet(onDismissRequest=onDismiss) {
        Column(
            Modifier.fillMaxWidth().heightIn(max=680.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal=PursuitSpace.md,vertical=PursuitSpace.sm),
            verticalArrangement=Arrangement.spacedBy(PursuitSpace.md)
        ) {
            PursuitSectionHeading("Superset rounds",
                "Issued order and saved outcomes. Reviewing a set does not log it.")
            if(rounds.isEmpty())PursuitEmptyState("No linked rounds",
                "This group is not part of the current issued workout.")
            rounds.forEach{members->
                val round=checkNotNull(members.first().round)
                PursuitCard {
                    Text("Round ${round.number} of ${round.total}",
                        style=MaterialTheme.typography.titleMedium)
                    Text(
                        if(CustomWorkoutExecutionDomain.roundIsResolved(journal,round))
                            "All required outcomes recorded"
                        else "Work remaining",
                        style=MaterialTheme.typography.bodySmall,
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                    members.forEach{step->
                        val set=step.set
                        val slot=journal.snapshot.slots[step.slotIndex]
                        val ordinal=slot.sets.filter{
                            it.prescription.type==DraftSetType.WORKING
                        }.indexOfFirst{it.setKey==set.setKey}+1
                        val target=set.prescription.repTargetLabel()
                        Text(
                            kotlinExerciseLabel(slot.exerciseId)+
                                " · set $ordinal · $target"+
                                set.prescription.targetRir?.let{" · RIR $it"}.orEmpty(),
                            style=MaterialTheme.typography.bodyMedium)
                        val review=NativeSupersetRoundReview.review(journal,set)
                        Text(review.outcome,
                            style=MaterialTheme.typography.bodySmall,
                            color=MaterialTheme.colorScheme.onSurfaceVariant)
                        val visit=review.visit
                        OutlinedButton(
                            onClick={onChoose(visit)},enabled=!busy,
                            modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {
                            Text(when {
                                visit==focus -> "Viewing this set"
                                visit.part==CustomWorkoutVisitPart.FOLLOW_UP ->
                                    "View pending follow-up"
                                else -> "View set"
                            })
                        }
                    }
                }
            }
            Text(
                "Complete any amplifier follow-up before switching movements. " +
                    "Rest follows the issued round rules, not this guide.",
                style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick=onDismiss,
                modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {
                Text("Back to workout")
            }
        }
    }
}
