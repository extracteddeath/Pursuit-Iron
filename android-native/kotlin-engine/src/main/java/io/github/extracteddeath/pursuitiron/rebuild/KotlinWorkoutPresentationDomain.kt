package io.github.extracteddeath.pursuitiron.rebuild

/**
 * UI-safe representation of the DURABLY recovered Kotlin-issued journal.
 * No recommendation, target, status or actual effort is invented here.
 * This projection is pure and can be presented by Compose, tests or backups.
 */
data class KotlinWorkoutSetPresentation(
    val target:KotlinWorkoutTarget,
    val currentReceipt:KotlinJournalReceipt?,
    val outcome:KotlinJournalSetOutcome?,
    val actual:KotlinPerformedSet?,
    /** Immediate prior effective receipt, if this entry was an explicit correction.
     * This is NOT the current receipt's own eventId (available via currentReceipt). */
    val correctsEventId:String?,
    val canEdit:Boolean
) {
    val isResolved:Boolean get()=currentReceipt!=null
}
data class KotlinWorkoutExercisePresentation(
    val exerciseId:String,
    val role:ComposerRole,
    val purpose:ComposerSessionPurpose,
    val sets:List<KotlinWorkoutSetPresentation>,
    val resolvedCount:Int,
    val nextUnresolvedSetKey:String?
)
data class KotlinWorkoutPresentation(
    val workoutId:String,
    val programId:String,
    val programRevision:Long,
    val purpose:ComposerSessionPurpose,
    val estimatedMinutes:Long,
    val calendarDay:Int,
    val dayIndex:Int,
    val revision:Long,
    val exercises:List<KotlinWorkoutExercisePresentation>,
    val resolvedCount:Int,
    val completedCount:Int,
    val skippedCount:Int,
    val failedCount:Int,
    val remainingCount:Int,
    val firstUnresolvedSetKey:String?,
    val finished:Boolean,
    val canFinish:Boolean
)

object KotlinWorkoutPresentationDomain {
    /** The issued target sequence, not exerciseId alone, owns a movement
     * occurrence. Repeating a lift after another movement (or under a new
     * role/purpose) cannot move its sets back into an earlier exercise card. */
    private fun sameOccurrence(a:KotlinWorkoutTarget,b:KotlinWorkoutTarget)=
        a.exerciseId==b.exerciseId && a.role==b.role && a.purpose==b.purpose

    /** Shared ordinal for the workout card and the post-set rest cue.
     * Returns -1 for an unknown set; no inferred exercise index. */
    internal fun occurrenceIndex(targets:List<KotlinWorkoutTarget>,setKey:String):Int {
        var index=-1
        var previous:KotlinWorkoutTarget?=null
        for(target in targets) {
            if(previous==null || !sameOccurrence(previous,target))index++
            if(target.setKey==setKey)return index
            previous=target
        }
        return -1
    }

    fun inspect(journal:KotlinWorkoutJournalState):KotlinWorkoutPresentation {
        val snapshot=journal.snapshot
        val finished=journal.finishedByEventId!=null
        val sets=snapshot.targets.map{target->
            val receipt=journal.effectiveSets[target.setKey]
            val record=receipt?.operation as? KotlinJournalOperation.RecordSet
            KotlinWorkoutSetPresentation(
                target,receipt,record?.outcome,record?.performed,
                record?.correctsEventId,
                !finished
            )
        }
        // Contiguous occurrences, never a global groupBy(exerciseId).
        // A -> B -> A must remain three separate workout cards in that
        // order, including independent completion and next-visit counts.
        val groups=mutableListOf<MutableList<KotlinWorkoutSetPresentation>>()
        for(set in sets) {
            val previous=groups.lastOrNull()?.lastOrNull()?.target
            if(previous==null || !sameOccurrence(previous,set.target))
                groups.add(mutableListOf())
            groups.last().add(set)
        }
        val exercises=groups.map{items->
            val first=items.first().target
            KotlinWorkoutExercisePresentation(first.exerciseId,first.role,
                first.purpose,items.toList(),items.count{it.isResolved},
                items.firstOrNull{!it.isResolved}?.target?.setKey)
        }
        val resolved=sets.count{it.isResolved}
        val complete=sets.count{it.outcome==KotlinJournalSetOutcome.COMPLETED}
        val skipped=sets.count{it.outcome==KotlinJournalSetOutcome.SKIPPED}
        val failed=sets.count{it.outcome==KotlinJournalSetOutcome.FAILED_ATTEMPT}
        require(resolved==journal.resolvedSetCount &&
            resolved==complete+skipped+failed)
        val remaining=sets.size-resolved
        return KotlinWorkoutPresentation(
            snapshot.workoutId,snapshot.source.programId,
            snapshot.source.programRevision,snapshot.purpose,
            snapshot.estimatedMinutes,snapshot.calendarDay,snapshot.dayIndex,
            journal.revision,exercises,resolved,complete,skipped,failed,
            remaining,sets.firstOrNull{!it.isResolved}?.target?.setKey,
            finished,!finished && remaining==0
        )
    }
}
