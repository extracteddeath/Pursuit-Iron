package io.github.extracteddeath.pursuitiron.rebuild

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

data class StrengthDoseBoundaryReview(
    val status:StrengthDoseBoundaryStatus,
    val hypertrophyExitWeek:Int?,
    val strengthEntryWeek:Int?,
    val hypertrophyWorkingSets:Int?,
    val strengthWorkingSets:Int?
) {
    val mayApplyAutomatically:Boolean get()=false
    val mayChangeHistory:Boolean get()=false
    val mayIssueWorkout:Boolean get()=false
}

object StrengthDoseBoundaryGuardDomain {
    private fun countedSets(week:CustomResolvedWeek):Int=
        week.days.sumOf{day->
            day.slots.sumOf{slot->
                slot.sets.count{it.type==DraftSetType.WORKING}
            }
        }

    fun review(structure:CustomCycleReview,
        targetBlockId:String):StrengthDoseBoundaryReview {
        fun result(status:StrengthDoseBoundaryStatus,
            exit:Int?=null,entry:Int?=null,
            before:Int?=null,after:Int?=null
        )=StrengthDoseBoundaryReview(status,exit,entry,before,after)
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
        // A tiny source week (e.g. 2 -> 1 sets) is NOT a broad half-volume
        // release blocker. Eight or more source working sets plus a decline
        // to half or below warrants an athlete-specific full-cycle review.
        val reviewRequired=before>=8 && after.toLong()*2<=before
        return result(if(reviewRequired)
            StrengthDoseBoundaryStatus.HALVED_WORKING_DOSE_REQUIRES_REVIEW
        else StrengthDoseBoundaryStatus.CONTINUOUS,
            exitWeek,entryWeek,before,after)
    }
}
