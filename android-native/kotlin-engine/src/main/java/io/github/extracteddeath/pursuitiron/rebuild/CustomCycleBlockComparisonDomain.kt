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
    /** Exact declared direct muscle credits for the first audited week. */
    val firstAuditedWeekDirectSets:Map<TrainingMuscle,BigDecimal>?
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

data class CustomCycleBlockComparison(
    val prior:CustomBlockComparisonFacts,
    val next:CustomBlockComparisonFacts,
    val addedWeekdays:List<Int>,
    val removedWeekdays:List<Int>,
    val exerciseChanges:List<CustomBlockExposureChange>,
    /** A report of verified measurements may still contain audit failures. */
    val matchingAuditStatus:CustomCoachAuditStatus?,
    val auditIssueCount:Int?
) {
    val auditedDirectMuscleChanges:List<CustomBlockMuscleChange>? get() {
        val earlier=prior.firstAuditedWeekDirectSets ?:return null
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
        val earlier=prior.firstAuditedWeekDirectSets ?:return null
        val later=next.firstAuditedWeekDirectSets ?:return null
        return later.keys-earlier.keys
    }
    val noLongerTrackedMuscles:Set<TrainingMuscle>? get() {
        val earlier=prior.firstAuditedWeekDirectSets ?:return null
        val later=next.firstAuditedWeekDirectSets ?:return null
        return earlier.keys-later.keys
    }
    val baseWorkingSetDelta:Int get()=
        next.baseWorkingSets-prior.baseWorkingSets
    val firstAuditedWeekDelta:Int? get()=
        prior.auditedWeeklyWorkingSets?.firstOrNull()?.let {before->
            next.auditedWeeklyWorkingSets?.firstOrNull()?.minus(before)
        }
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
            if(validWeeks)weeks.first().directSets else null
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
        val prior=facts(cycle,cycle.blocks[index-1],validAudit)
        val next=facts(cycle,cycle.blocks[index],validAudit)
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
            changes,validAudit?.status,validAudit?.issues?.size
        )
    }
}
