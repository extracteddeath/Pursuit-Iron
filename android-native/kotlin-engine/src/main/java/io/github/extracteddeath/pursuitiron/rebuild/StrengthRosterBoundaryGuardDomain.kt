package io.github.extracteddeath.pursuitiron.rebuild

import java.util.Collections

/**
 * Prevent a reviewed, independently generated future Strength block from
 * silently losing most of the preceding Hypertrophy block's working
 * movement menu (e.g. eight exercises dropping to three on one same-split
 * day). This is a conservative *generation* gate, not a universal exercise
 * quota or authority to insert made-up exercises, loads or sets.
 *
 * Only genuine final Hypertrophy-week versus first Strength-week rosters,
 * including week-specific overrides, are eligible. The FULL cycle must
 * already pass exact source/inventory structural review. A different split
 * must go through the separate future-schedule/coverage audit; Peak may
 * intentionally taper under its own policies and is not blocked here.
 */
enum class StrengthRosterBoundaryStatus {
    SOURCE_UNVERIFIED, NOT_COMPARABLE, CONTINUOUS,
    MATERIAL_EXERCISE_LOSS_REQUIRES_REVIEW
}

data class StrengthDayRosterLoss(
    val weekday:Int,
    val beforeWorkingExercises:Int,
    val afterWorkingExercises:Int,
    val missingExerciseIds:Set<String>
)

data class StrengthRosterBoundaryReview(
    val status:StrengthRosterBoundaryStatus,
    val sourceExitWeek:Int?,
    val targetEntryWeek:Int?,
    val affectedDays:List<StrengthDayRosterLoss>
) {
    val canIssueWorkout:Boolean get()=false
    val mayApplyAutomatically:Boolean get()=false
}

object StrengthRosterBoundaryGuardDomain {
    private fun workingExerciseIds(day:CustomTrainingDay):Set<String> =
        day.slots.filter{slot ->
            slot.sets.any{it.type==DraftSetType.WORKING}
        }.map{it.exerciseId}.toSet()

    fun review(
        structure:CustomCycleReview,
        targetBlockId:String
    ):StrengthRosterBoundaryReview {
        fun result(status:StrengthRosterBoundaryStatus,
            exit:Int?=null,entry:Int?=null,
            days:List<StrengthDayRosterLoss> = emptyList()
        )=StrengthRosterBoundaryReview(status,exit,entry,
            Collections.unmodifiableList(days))
        val cycle=structure.verifiedDraft
        if(structure.status!=CustomCycleReviewStatus.READY_FOR_COACH_REVIEW ||
            cycle==null)return result(StrengthRosterBoundaryStatus.SOURCE_UNVERIFIED)
        val index=cycle.blocks.indexOfFirst{it.blockId==targetBlockId}
        if(index<=0)return result(StrengthRosterBoundaryStatus.NOT_COMPARABLE)
        val prior=cycle.blocks[index-1]
        val target=cycle.blocks[index]
        if(prior.phase!=TrainingPhase.HYPERTROPHY ||
            target.phase!=TrainingPhase.STRENGTH ||
            prior.purpose!=CustomBlockPurpose.TRAINING ||
            target.purpose!=CustomBlockPurpose.TRAINING ||
            prior.split!=target.split ||
            prior.days.map{it.weekday}.toSet()!=target.days.map{it.weekday}.toSet())
            return result(StrengthRosterBoundaryStatus.NOT_COMPARABLE)
        val exitWeek=cycle.blocks.take(index).sumOf{it.weeks}
        val entryWeek=exitWeek+1
        val exit=CustomTrainingCycleDomain.week(structure,exitWeek)
        val entry=CustomTrainingCycleDomain.week(structure,entryWeek)
        if(exit==null || entry==null ||
            exit.blockId!=prior.blockId ||
            entry.blockId!=target.blockId)
            return result(StrengthRosterBoundaryStatus.SOURCE_UNVERIFIED)
        val losses=exit.days.mapNotNull{day ->
            val following=entry.days.singleOrNull{it.weekday==day.weekday}
                ?:return result(StrengthRosterBoundaryStatus.SOURCE_UNVERIFIED)
            val before=workingExerciseIds(day)
            val after=workingExerciseIds(following)
            // This is the same severity trigger displayed on Plan: a
            // day with >=6 previously trained exercises losing MORE than
            // half its menu. Two-to-one is not treated as a broad quota.
            if(before.size>=6 && after.size*2<before.size)
                StrengthDayRosterLoss(day.weekday,before.size,after.size,
                    Collections.unmodifiableSet(before-after))
            else null
        }.sortedBy{it.weekday}
        return result(if(losses.isEmpty())
            StrengthRosterBoundaryStatus.CONTINUOUS
        else StrengthRosterBoundaryStatus.MATERIAL_EXERCISE_LOSS_REQUIRES_REVIEW,
            exitWeek,entryWeek,losses)
    }
}
