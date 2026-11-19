package io.github.extracteddeath.pursuitiron.rebuild

import java.util.Collections

/**
 * Source-derived working-set continuity at a Hypertrophy -> Strength boundary.
 *
 * Prevent a generated replacement from silently halving an established
 * same-split week's working sets. Unlike an arbitrary global set quota, the
 * reference is THIS athlete's exact final hypertrophy week, including every
 * authored week override. The first Strength week is evaluated with the same
 * resolved cycle. Warmups, advanced amplifier mini-sets and later weeks must
 * not be counted as source working sets.
 *
 * No return value is a clinical dose prescription, auto-repair permission,
 * signed approval or instruction to change an already-issued workout.
 */
enum class StrengthDoseBoundaryStatus {
    SOURCE_UNVERIFIED, NOT_COMPARABLE, CONTINUOUS,
    HALVED_WORKING_DOSE_REQUIRES_REVIEW
}

/** A real same-weekday working-set collapse, even when other days compensate. */
data class StrengthDayDoseLoss(
    val weekday:Int,
    val beforeWorkingSets:Int,
    val afterWorkingSets:Int
)

data class StrengthDoseBoundaryReview(
    val status:StrengthDoseBoundaryStatus,
    val hypertrophyExitWeek:Int?,
    val strengthEntryWeek:Int?,
    val hypertrophyWorkingSets:Int?,
    val strengthWorkingSets:Int?,
    val affectedDays:List<StrengthDayDoseLoss> = emptyList()
) {
    val mayApplyAutomatically:Boolean get()=false
    val mayChangeHistory:Boolean get()=false
    val mayIssueWorkout:Boolean get()=false
}

object StrengthDoseBoundaryGuardDomain {
    private fun countedSets(day:CustomTrainingDay):Int=
        day.slots.sumOf{slot->
            slot.sets.count{it.type==DraftSetType.WORKING}
        }

    private fun countedSets(week:CustomResolvedWeek):Int=
        week.days.sumOf(::countedSets)

    fun review(structure:CustomCycleReview,
        targetBlockId:String):StrengthDoseBoundaryReview {
        fun result(status:StrengthDoseBoundaryStatus,
            exit:Int?=null,entry:Int?=null,
            before:Int?=null,after:Int?=null,
            days:List<StrengthDayDoseLoss> = emptyList()
        )=StrengthDoseBoundaryReview(status,exit,entry,before,after,
            Collections.unmodifiableList(days.toList()))
        val cycle=structure.verifiedDraft
        if(structure.status!=CustomCycleReviewStatus.READY_FOR_COACH_REVIEW ||
            cycle==null)
            return result(StrengthDoseBoundaryStatus.SOURCE_UNVERIFIED)
        val index=cycle.blocks.indexOfFirst{it.blockId==targetBlockId}
        if(index<=0)
            return result(StrengthDoseBoundaryStatus.NOT_COMPARABLE)
        val previous=cycle.blocks[index-1]
        val target=cycle.blocks[index]
        if(previous.phase!=TrainingPhase.HYPERTROPHY ||
            target.phase!=TrainingPhase.STRENGTH ||
            previous.purpose!=CustomBlockPurpose.TRAINING ||
            target.purpose!=CustomBlockPurpose.TRAINING ||
            previous.split!=target.split ||
            previous.days.map{it.weekday}.toSet()!=
                target.days.map{it.weekday}.toSet())
            return result(StrengthDoseBoundaryStatus.NOT_COMPARABLE)
        val exitWeek=cycle.blocks.take(index).sumOf{it.weeks}
        val entryWeek=exitWeek+1
        val prior=CustomTrainingCycleDomain.week(structure,exitWeek)
        val next=CustomTrainingCycleDomain.week(structure,entryWeek)
        if(prior==null || next==null ||
            prior.blockId!=previous.blockId ||
            next.blockId!=target.blockId)
            return result(StrengthDoseBoundaryStatus.SOURCE_UNVERIFIED)
        val before=countedSets(prior)
        val after=countedSets(next)
        // Weekly totals alone can conceal a lost session: e.g. Monday's
        // 16 -> 8 working sets while Wednesday's 8 -> 16 restores 24 -> 24.
        // Same weekday and split are required above. Fail closed if a
        // structurally verified day is unexpectedly absent on the other side.
        val losses=prior.days.mapNotNull {day->
            val following=next.days.singleOrNull{it.weekday==day.weekday}
                ?:return result(StrengthDoseBoundaryStatus.SOURCE_UNVERIFIED)
            val dayBefore=countedSets(day)
            val dayAfter=countedSets(following)
            if(dayBefore>=8 && dayAfter.toLong()*2<=dayBefore)
                StrengthDayDoseLoss(day.weekday,dayBefore,dayAfter)
            else null
        }.sortedBy{it.weekday}
        // Small low-volume days are not subjected to a universal quota.
        // Preserve both exact weekly evidence and the affected weekdays.
        val reviewRequired=(before>=8 && after.toLong()*2<=before) ||
            losses.isNotEmpty()
        return result(if(reviewRequired)
            StrengthDoseBoundaryStatus.HALVED_WORKING_DOSE_REQUIRES_REVIEW
        else StrengthDoseBoundaryStatus.CONTINUOUS,
            exitWeek,entryWeek,before,after,losses)
    }
}
