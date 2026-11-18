package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal

/**
 * Read-only comparison of TWO ALREADY AUTHORED adjacent cycle blocks.
 *
 * The base-week facts come from the exact saved exercise visits and working
 * sets; they exclude per-week overrides. When a complete revision-matched
 * coach audit is available, the separate audited series contains effective
 * weekly set counts including overrides. Neither source is an approval,
 * an individualized dose threshold, or a generated alternative.
 */
data class CustomBlockComparisonFacts(
    val blockId:String,
    val label:String,
    val phase:TrainingPhase,
    val purpose:CustomBlockPurpose,
    val split:CustomSplitIdentity,
    val weeks:Int,
    val weekdays:List<Int>,
    val exerciseVisits:Int,
    val uniqueExercises:Int,
    val baseWorkingSets:Int,
    val baseWarmupSets:Int,
    val pinnedVisits:Int,
    val weekOverrideCount:Int,
    val exposuresByExercise:Map<String,Int>,
    /** Present only when every week was measured by a matching cycle audit. */
    val auditedWeeklyWorkingSets:List<Int>?,
    /** Exact direct-muscle credits at the first reviewed training week. */
    val firstAuditedWeekDirectSets:Map<TrainingMuscle,BigDecimal>?,
    /** Exact direct-muscle credits at the last reviewed training week. */
    val lastAuditedWeekDirectSets:Map<TrainingMuscle,BigDecimal>? = null
)

data class CustomBlockMuscleChange(
    val muscle:TrainingMuscle,
    val previousDirectSets:BigDecimal,
    val nextDirectSets:BigDecimal
) {
    val delta:BigDecimal get()=nextDirectSets.subtract(previousDirectSets)
}

data class CustomBlockExposureChange(
    val exerciseId:String,
    val beforeVisits:Int,
    val afterVisits:Int
)

/** Per matched actual day, not a fabricated universal exercise quota. */
data class CustomBlockDayExerciseChange(
    val weekday:Int,
    val priorExerciseCount:Int,
    val nextExerciseCount:Int,
    val droppedExerciseIds:Set<String>
)

data class CustomCycleBlockComparison(
    val prior:CustomBlockComparisonFacts,
    val next:CustomBlockComparisonFacts,
    val addedWeekdays:List<Int>,
    val removedWeekdays:List<Int>,
    val exerciseChanges:List<CustomBlockExposureChange>,
    /** A report of verified measurements may still contain audit failures. */
    val matchingAuditStatus:CustomCoachAuditStatus?,
    val auditIssueCount:Int?,
    /** Source-verified resolved final→first day rosters when available;
     * otherwise these differences are only the author's BASE templates. */
    val strengthDayExerciseChanges:List<CustomBlockDayExerciseChange> = emptyList(),
    val exerciseRosterUsesReviewedWeeks:Boolean=false
) {
    /** Review trigger, never an evidence-free minimum of eight exercises. */
    val strengthExerciseCollapseReviewRequired:Boolean get() =
        prior.phase==TrainingPhase.HYPERTROPHY &&
        next.phase==TrainingPhase.STRENGTH &&
        prior.purpose==CustomBlockPurpose.TRAINING &&
        next.purpose==CustomBlockPurpose.TRAINING &&
        strengthDayExerciseChanges.any {day->
            day.priorExerciseCount>=6 &&
            day.nextExerciseCount*2<day.priorExerciseCount
        }
    val auditedDirectMuscleChanges:List<CustomBlockMuscleChange>? get() {
        // The athlete leaves hypertrophy at its LAST effective week,
        // not its first one. Overrides and per-week prescriptions matter.
        val earlier=prior.lastAuditedWeekDirectSets ?:return null
        val later=next.firstAuditedWeekDirectSets ?:return null
        return earlier.keys.intersect(later.keys).mapNotNull{muscle->
            val before=earlier.getValue(muscle)
            val after=later.getValue(muscle)
            if(before.compareTo(after)==0)null else
                CustomBlockMuscleChange(muscle,before,after)
        }.sortedWith(compareByDescending<CustomBlockMuscleChange>{
            it.delta.abs()
        }.thenBy{it.muscle.name})
    }
    val newlyTrackedMuscles:Set<TrainingMuscle>? get() {
        val earlier=prior.lastAuditedWeekDirectSets ?:return null
        val later=next.firstAuditedWeekDirectSets ?:return null
        return later.keys-earlier.keys
    }
    val noLongerTrackedMuscles:Set<TrainingMuscle>? get() {
        val earlier=prior.lastAuditedWeekDirectSets ?:return null
        val later=next.firstAuditedWeekDirectSets ?:return null
        return earlier.keys-later.keys
    }
    val baseWorkingSetDelta:Int get()=
        next.baseWorkingSets-prior.baseWorkingSets
    /** Exact boundary: final prior week → first next week, including overrides. */
    val boundaryWorkingSetDelta:Int? get()=
        prior.auditedWeeklyWorkingSets?.lastOrNull()?.let {before->
            next.auditedWeeklyWorkingSets?.firstOrNull()?.minus(before)
        }
    // Compatibility for older callers; the calculation is now boundary-based.
    val firstAuditedWeekDelta:Int? get()=boundaryWorkingSetDelta
    private fun percentage(before:Int,after:Int):Int? =
        if(before<=0) null
        else ((after.toLong()*100+before/2)/before).toInt()
    /** The effective entry dose, never a claim that a drop is medically unsafe. */
    val boundaryRetentionPercent:Int? get()=
        prior.auditedWeeklyWorkingSets?.lastOrNull()?.let {before->
            next.auditedWeeklyWorkingSets?.firstOrNull()?.let {after->
                percentage(before,after)
            }
        }
    /** A later low-dose strength week must not be hidden behind week one. */
    val lowestNextWeekRetentionPercent:Int? get()=
        prior.auditedWeeklyWorkingSets?.lastOrNull()?.let {before->
            next.auditedWeeklyWorkingSets?.minOrNull()?.let {lowest->
                percentage(before,lowest)
            }
        }
    /** A factual high-severity review cue, not a replacement for the
     * athlete-specific full-cycle auditor or its authorized dose bounds.
     * If audited weeks are unavailable, only the authored BASE is assessed. */
    val strengthHalfVolumeReviewRequired:Boolean get() {
        if(prior.phase!=TrainingPhase.HYPERTROPHY ||
            next.phase!=TrainingPhase.STRENGTH ||
            prior.purpose!=CustomBlockPurpose.TRAINING ||
            next.purpose!=CustomBlockPurpose.TRAINING)return false
        val before=prior.auditedWeeklyWorkingSets?.lastOrNull()
        val after=next.auditedWeeklyWorkingSets?.minOrNull()
        return if(before!=null && after!=null)
            before>=16 && after.toLong()*2<=before
        else prior.baseWorkingSets>=16 &&
            next.baseWorkingSets.toLong()*2<=prior.baseWorkingSets
    }
    val strengthHalfVolumeUsesAuditedWeeks:Boolean get()=
        prior.auditedWeeklyWorkingSets!=null &&
        next.auditedWeeklyWorkingSets!=null
    val mayApplyAutomatically:Boolean get()=false
    val mayApproveProgram:Boolean get()=false
    val mayIssueWorkout:Boolean get()=false
}

object CustomCycleBlockComparisonDomain {
    private fun facts(
        cycle:CustomTrainingCycle,
        block:CustomTrainingBlock,
        audit:CustomCoachAudit?
    ):CustomBlockComparisonFacts {
        val days=block.days.sortedBy{it.weekday}
        val slots=days.flatMap{it.slots}
        // A partial or stale audit can never masquerade as a complete
        // measurement. It also must refer to this exact saved draft.
        val weeks=audit?.weeks?.filter{it.blockId==block.blockId}
            ?.sortedBy{it.absoluteWeek}.orEmpty()
        val firstAbsoluteWeek=cycle.blocks.takeWhile{
            it.blockId!=block.blockId
        }.sumOf{it.weeks}+1
        val expectedWeeks=(firstAbsoluteWeek until
            firstAbsoluteWeek+block.weeks).toList()
        val validWeeks=audit!=null && block.weeks>=1 &&
            audit.auditedCycleRevision==cycle.revision &&
            audit.structuralReview?.verifiedDraft==cycle &&
            weeks.map{it.absoluteWeek}==expectedWeeks &&
            weeks.all{it.phase==block.phase && it.blockPurpose==block.purpose}
        return CustomBlockComparisonFacts(
            block.blockId,block.label,block.phase,block.purpose,
            block.split,block.weeks,days.map{it.weekday},
            slots.size,slots.map{it.exerciseId}.distinct().size,
            slots.sumOf{slot->
                slot.sets.count{it.type==DraftSetType.WORKING}
            },
            slots.sumOf{slot->
                slot.sets.count{it.type==DraftSetType.WARMUP}
            },
            slots.count{it.pinned},
            cycle.weekOverrides[block.blockId].orEmpty().size,
            slots.groupingBy{it.exerciseId}.eachCount().toSortedMap(),
            if(validWeeks)weeks.map{it.workingSets} else null,
            if(validWeeks)weeks.first().directSets else null,
            if(validWeeks)weeks.last().directSets else null
        )
    }

    /**
     * A target must have an actual preceding block; never synthesize a future
     * calendar or deduce coaching clearance from a 1-week volume difference.
     */
    fun compare(
        cycle:CustomTrainingCycle,
        targetBlockId:String,
        audit:CustomCoachAudit?=null
    ):CustomCycleBlockComparison? {
        val index=cycle.blocks.indexOfFirst{it.blockId==targetBlockId}
        if(index<=0)return null
        val validAudit=audit?.takeIf{
            it.auditedCycleRevision==cycle.revision &&
            it.structuralReview?.verifiedDraft==cycle
        }
        val priorBlock=cycle.blocks[index-1]
        val nextBlock=cycle.blocks[index]
        val prior=facts(cycle,priorBlock,validAudit)
        val next=facts(cycle,nextBlock,validAudit)
        // Exercise-variety continuity is separate from total set continuity.
        // Only compare weekday-for-weekday when the split is unchanged;
        // a new split redistributes work and requires weekly coverage review.
        // A complete revision-matched audit supplies effective day rosters,
        // including saved week-specific overrides. Without that audit,
        // report only base-authoring facts, NEVER claim reviewed clearance.
        val matchedTraining=prior.phase==TrainingPhase.HYPERTROPHY &&
            next.phase==TrainingPhase.STRENGTH &&
            prior.purpose==CustomBlockPurpose.TRAINING &&
            next.purpose==CustomBlockPurpose.TRAINING &&
            prior.split==next.split &&
            prior.weekdays.toSet()==next.weekdays.toSet()
        val reviewedCycle=validAudit?.structuralReview?.takeIf {
            prior.auditedWeeklyWorkingSets!=null &&
            next.auditedWeeklyWorkingSets!=null
        }
        val priorExit=cycle.blocks.take(index).sumOf{it.weeks}
        val nextEntry=priorExit+1
        val auditedPrior=reviewedCycle?.let{
            CustomTrainingCycleDomain.week(it,priorExit)
        }
        val auditedNext=reviewedCycle?.let{
            CustomTrainingCycleDomain.week(it,nextEntry)
        }
        val auditedDayRosters=matchedTraining &&
            auditedPrior?.blockId==prior.blockId &&
            auditedNext?.blockId==next.blockId
        val beforeDays=if(auditedDayRosters)checkNotNull(auditedPrior).days
            else priorBlock.days
        val afterDays=if(auditedDayRosters)checkNotNull(auditedNext).days
            else nextBlock.days
        fun workoutExercises(day:CustomTrainingDay):Set<String> =
            day.slots.filter{slot->
                slot.sets.any{set->set.type==DraftSetType.WORKING}
            }.map{it.exerciseId}.toSet()
        val matchedDayChanges=if(!matchedTraining)emptyList() else
            beforeDays.mapNotNull{day->
                val following=afterDays.singleOrNull{it.weekday==day.weekday}
                    ?:return@mapNotNull null
                val old=workoutExercises(day)
                val new=workoutExercises(following)
                if(old==new)null else CustomBlockDayExerciseChange(
                    day.weekday,old.size,new.size,old-new)
            }.sortedBy{it.weekday}
        val before=prior.exposuresByExercise
        val after=next.exposuresByExercise
        val changes=(before.keys+after.keys).sorted()
            .mapNotNull{id->
                val a=before[id]?:0
                val b=after[id]?:0
                if(a==b)null else CustomBlockExposureChange(id,a,b)
            }
        return CustomCycleBlockComparison(
            prior,next,next.weekdays.filterNot{it in prior.weekdays},
            prior.weekdays.filterNot{it in next.weekdays},
            changes,validAudit?.status,validAudit?.issues?.size,
            matchedDayChanges,auditedDayRosters
        )
    }
}
