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

    fun reviewVisit(journal:CustomWorkoutJournalDraft,
        set:CustomWorkoutSetSnapshot):CustomWorkoutVisitKey? {
        // The engine owns which visits can actually appear. Only an
        // already-triggered, still-unrecorded follow-up supersedes the
        // main-set review target; do not call an engine-internal helper
        // across module boundaries or fabricate future amplifier visits.
        val visible=CustomWorkoutVisitFocusDomain.visibleOrder(journal).toSet()
        val main=CustomWorkoutVisitKey(set.setKey,CustomWorkoutVisitPart.MAIN)
        val follow=CustomWorkoutVisitKey(set.setKey,CustomWorkoutVisitPart.FOLLOW_UP)
        return when {
            follow in visible && follow !in journal.effectiveVisits -> follow
            main in visible -> main
            else -> null
        }
    }

    fun outcome(journal:CustomWorkoutJournalDraft,
        set:CustomWorkoutSetSnapshot):String {
        val main=CustomWorkoutVisitKey(set.setKey,CustomWorkoutVisitPart.MAIN)
        val follow=CustomWorkoutVisitKey(set.setKey,CustomWorkoutVisitPart.FOLLOW_UP)
        val mainResult=(journal.effectiveVisits[main]?.operation
            as? CustomWorkoutJournalOperation.Record)
        val followResult=(journal.effectiveVisits[follow]?.operation
            as? CustomWorkoutJournalOperation.Record)
        return when(mainResult?.outcome) {
            null -> "Not logged"
            KotlinJournalSetOutcome.SKIPPED -> "Skipped"
            KotlinJournalSetOutcome.FAILED_ATTEMPT -> "Failed attempt"
            KotlinJournalSetOutcome.COMPLETED -> when {
                set.prescription.amplifier==null -> "Completed"
                followResult==null -> "Amplifier follow-up due"
                followResult.outcome==KotlinJournalSetOutcome.COMPLETED ->
                    "Main + follow-up completed"
                followResult.outcome==KotlinJournalSetOutcome.SKIPPED ->
                    "Main completed · follow-up skipped"
                else -> "Main completed · follow-up failed"
            }
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
                        val reps=set.prescription.reps
                        val target=if(reps.minimum==reps.maximum)
                            reps.minimum.toString()
                        else "${reps.minimum}–${reps.maximum}"
                        Text(
                            kotlinExerciseLabel(slot.exerciseId)+
                                " · set $ordinal · $target reps"+
                                set.prescription.targetRir?.let{" · RIR $it"}.orEmpty(),
                            style=MaterialTheme.typography.bodyMedium)
                        Text(NativeSupersetRoundReview.outcome(journal,set),
                            style=MaterialTheme.typography.bodySmall,
                            color=MaterialTheme.colorScheme.onSurfaceVariant)
                        val visit=NativeSupersetRoundReview.reviewVisit(journal,set)
                        if(visit!=null)OutlinedButton(
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
