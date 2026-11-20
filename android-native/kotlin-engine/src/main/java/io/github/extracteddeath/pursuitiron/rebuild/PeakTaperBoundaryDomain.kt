package io.github.extracteddeath.pursuitiron.rebuild

import java.util.Collections

/**
 * Actual final STRENGTH training week -> first PEAK training week, never
 * an intervening deload or performance-test week. This is a factual
 * comparison of source-authored sets and main-lift PRACTICE, not a
 * prescription, taper algorithm, permission or universal volume quota.
 *
 * Peak may legitimately use fewer accessories and working sets. Dropping
 * a lift entirely is a separate decision from reducing its sets; surface
 * that difference before the athlete approves their future cycle.
 */
enum class PeakTaperBoundaryStatus {
    SOURCE_UNVERIFIED, NOT_COMPARABLE, MEASURED_FOR_REVIEW
}

data class PeakMainLiftExposure(
    val exerciseId:String,
    val strengthDays:Int,
    val peakDays:Int
) {
    val absentFromPeak:Boolean get()=strengthDays>0 && peakDays==0
    val practiceReduced:Boolean get()=peakDays<strengthDays
}

data class PeakDayWorkingChange(
    val weekday:Int,
    val strengthExercises:Int?,
    val peakExercises:Int?,
    val strengthWorkingSets:Int?,
    val peakWorkingSets:Int?
)

data class PeakTaperBoundaryReview(
    val status:PeakTaperBoundaryStatus,
    val strengthExitWeek:Int?=null,
    val peakEntryWeek:Int?=null,
    val strengthWorkingSets:Int?=null,
    val peakWorkingSets:Int?=null,
    val strengthExerciseVisits:Int?=null,
    val peakExerciseVisits:Int?=null,
    val splitChanged:Boolean=false,
    val primaryPractice:List<PeakMainLiftExposure> = emptyList(),
    val dayChanges:List<PeakDayWorkingChange> = emptyList()
) {
    val lostMainLiftIds:Set<String> get()=primaryPractice
        .filter{it.absentFromPeak}.map{it.exerciseId}.toSet()
    val reducedPrimaryPracticeIds:Set<String> get()=primaryPractice
        .filter{it.practiceReduced}.map{it.exerciseId}.toSet()
    val maySaveProgram:Boolean get()=false
    val mayApplyAutomatically:Boolean get()=false
    val mayIssueWorkout:Boolean get()=false
    val mayAlterHistory:Boolean get()=false
}

object PeakTaperBoundaryDomain {
    private fun working(day:CustomTrainingDay)=day.slots.filter{slot->
        slot.sets.any{it.type==DraftSetType.WORKING}
    }
    private fun workingSetCount(day:CustomTrainingDay)=day.slots.sumOf{slot->
        slot.sets.count{it.type==DraftSetType.WORKING}
    }
    private fun primaryDays(week:CustomResolvedWeek):Map<String,Int> =
        week.days.flatMap{day ->
            working(day).filter{it.role==ComposerRole.PRIMARY}
                .map{it.exerciseId to day.weekday}
        }.groupBy({it.first},{it.second}).mapValues{(_,days)->
            days.distinct().size
        }

    fun review(structure:CustomCycleReview,
        targetBlockId:String):PeakTaperBoundaryReview {
        fun refuse(status:PeakTaperBoundaryStatus)=
            PeakTaperBoundaryReview(status)
        val cycle=structure.verifiedDraft
        if(structure.status!=CustomCycleReviewStatus.READY_FOR_COACH_REVIEW ||
            cycle==null)
            return refuse(PeakTaperBoundaryStatus.SOURCE_UNVERIFIED)
        val index=cycle.blocks.indexOfFirst{it.blockId==targetBlockId}
        if(index<=0)return refuse(PeakTaperBoundaryStatus.NOT_COMPARABLE)
        val priorIndex=(index-1 downTo 0).firstOrNull {
            cycle.blocks[it].purpose==CustomBlockPurpose.TRAINING
        } ?:return refuse(PeakTaperBoundaryStatus.NOT_COMPARABLE)
        val previous=cycle.blocks[priorIndex]
        val target=cycle.blocks[index]
        if(previous.phase!=TrainingPhase.STRENGTH ||
            target.phase!=TrainingPhase.PEAK ||
            previous.purpose!=CustomBlockPurpose.TRAINING ||
            target.purpose!=CustomBlockPurpose.TRAINING)
            return refuse(PeakTaperBoundaryStatus.NOT_COMPARABLE)
        // Do not silently compare a strength deload's one-set skill visit:
        // week numbers resolve every saved override on the normal phase
        // exit and Peak entry. A prior HYPERTROPHY training block cannot
        // impersonate the specific Strength -> Peak transition.
        val strengthWeek=cycle.blocks.take(priorIndex+1).sumOf{it.weeks}
        val peakWeek=cycle.blocks.take(index).sumOf{it.weeks}+1
        val strength=CustomTrainingCycleDomain.week(structure,strengthWeek)
        val peak=CustomTrainingCycleDomain.week(structure,peakWeek)
        if(strength==null || peak==null ||
            strength.blockId!=previous.blockId ||
            peak.blockId!=target.blockId)
            return refuse(PeakTaperBoundaryStatus.SOURCE_UNVERIFIED)
        val before=primaryDays(strength)
        val after=primaryDays(peak)
        val lifts=(before.keys+after.keys).sorted().map {lift ->
            PeakMainLiftExposure(lift,before[lift] ?: 0,after[lift] ?: 0)
        }
        val dayBefore=strength.days.associateBy{it.weekday}
        val dayAfter=peak.days.associateBy{it.weekday}
        val days=(dayBefore.keys+dayAfter.keys).sorted().map{weekday ->
            val a=dayBefore[weekday]
            val b=dayAfter[weekday]
            PeakDayWorkingChange(weekday,
                a?.let{working(it).size},
                b?.let{working(it).size},
                a?.let(::workingSetCount),
                b?.let(::workingSetCount))
        }
        return PeakTaperBoundaryReview(
            PeakTaperBoundaryStatus.MEASURED_FOR_REVIEW,
            strengthWeek,peakWeek,
            strength.days.sumOf(::workingSetCount),
            peak.days.sumOf(::workingSetCount),
            strength.days.sumOf{working(it).size},
            peak.days.sumOf{working(it).size},
            previous.split!=target.split,
            Collections.unmodifiableList(lifts),
            Collections.unmodifiableList(days)
        )
    }
}
