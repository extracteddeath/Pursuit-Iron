package io.github.extracteddeath.pursuitiron.rebuild

import java.util.Collections

/** A read-only selection of unresolved entries in ONE current exercise/slot.
 * The exact journal revision must be checked again by its durable writer.
 * Skipping these entries never changes a prescription or a future program. */
data class RemainingExerciseReview<K>(
    val exerciseId:String,
    val journalRevision:Long,
    val entries:List<K>,
    val warmupCount:Int,
    val workingCount:Int,
    val followUpCount:Int,
    val preservedEntryCount:Int
)

object WorkoutRemainingExerciseDomain {
    fun generated(
        journal:KotlinWorkoutJournalState,exerciseId:String
    ):RemainingExerciseReview<String>? {
        if(journal.finishedByEventId!=null ||
            !KotlinWorkoutPresentationDomain.hasSingleOccurrence(
                journal.snapshot.targets,exerciseId))return null
        val targets=journal.snapshot.targets.filter{it.exerciseId==exerciseId}
        if(targets.isEmpty())return null
        val remaining=targets.filter{it.setKey !in journal.effectiveSets}
        return RemainingExerciseReview(exerciseId,journal.revision,
            Collections.unmodifiableList(remaining.map{it.setKey}),
            remaining.count{it.type==DraftSetType.WARMUP},
            remaining.count{it.type==DraftSetType.WORKING},0,
            targets.size-remaining.size)
    }

    /** Scope by authored slot, since a custom day may repeat a movement.
     * Only COMPLETED mains unlock follow-ups. Skipping an unlogged main
     * does not fabricate a follow-up observation. Existing follow-ups and
     * main results, including failures and corrections, remain untouched. */
    fun custom(
        journal:CustomWorkoutJournalDraft,slotId:String
    ):RemainingExerciseReview<CustomWorkoutVisitKey>? {
        if(journal.finishedByEventId!=null)return null
        val slot=journal.snapshot.slots.singleOrNull{it.slotId==slotId}
            ?:return null
        val targets=slot.sets.associateBy{it.setKey}
        val visits=CustomWorkoutVisitFocusDomain.visibleOrder(journal)
            .filter{it.setKey in targets}
        val remaining=visits.filter{it !in journal.effectiveVisits}
        return RemainingExerciseReview(slot.exerciseId,journal.revision,
            Collections.unmodifiableList(remaining),
            remaining.count{it.part==CustomWorkoutVisitPart.MAIN &&
                targets.getValue(it.setKey).prescription.type==DraftSetType.WARMUP},
            remaining.count{it.part==CustomWorkoutVisitPart.MAIN &&
                targets.getValue(it.setKey).prescription.type==DraftSetType.WORKING},
            remaining.count{it.part==CustomWorkoutVisitPart.FOLLOW_UP},
            visits.size-remaining.size)
    }
}
