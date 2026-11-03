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
    fun inspect(journal:KotlinWorkoutJournalState):KotlinWorkoutPresentation {
        val snapshot=journal.snapshot
        val finished=journal.finishedByEventId!=null
        val sets=snapshot.targets.map{target->
            val receipt=journal.effectiveSets[target.setKey]
            val record=receipt?.operation as? KotlinJournalOperation.RecordSet
            KotlinWorkoutSetPresentation(
                target,receipt,record?.outcome,record?.performed,
                record?.eventId,
                !finished
            )
        }
        // Preserve the actual issued sequence. Never order by display name,
        // enum, equipment, or a guessed exercise hierarchy.
        val groups=linkedMapOf<String,MutableList<KotlinWorkoutSetPresentation>>()
        for(set in sets) groups.getOrPut(set.target.exerciseId){mutableListOf()}
            .add(set)
        val exercises=groups.map{(id,items)->
            KotlinWorkoutExercisePresentation(id,items.first().target.role,
                items.first().target.purpose,items.toList(),
                items.count{it.isResolved},
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
