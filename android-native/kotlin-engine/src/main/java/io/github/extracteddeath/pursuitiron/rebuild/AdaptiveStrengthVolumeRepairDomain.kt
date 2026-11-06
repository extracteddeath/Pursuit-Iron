package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import java.util.Collections

/**
 * Bounded, read-only dose REPAIR for an already-authored future strength
 * block. It does not create exercises, training days, rep schemes, weights,
 * effort targets or coaching thresholds. Every proposed added working set
 * copies the source's EXACT verified purpose-specific prescription.
 *
 * The authoritative CustomCycleCoachAuditDomain and
 * AdaptiveCycleOptionReviewDomain still decide all hard constraints,
 * permissions and full-cycle quality. This helper is neither a second week
 * composer nor an issuance/persistence authority.
 */
data class AdaptiveStrengthVolumeRepairRequest(
    val review:AdaptiveCycleOptionRequest,
    /** Explicit coach/athlete bound; there is no guessed per-slot maximum. */
    val maximumWorkingSetsPerSlot:Int,
    val optionId:String="auto_strength_dose_repair"
)

enum class AdaptiveStrengthVolumeRepairStatus {
    SOURCE_UNRESOLVED, PERMISSION_REQUIRED, PRESCRIPTION_UNSUPPORTED,
    NO_FEASIBLE_REPAIR, CANDIDATE_READY_FOR_REVIEW
}

data class AdaptiveStrengthVolumeRepairResult(
    val status:AdaptiveStrengthVolumeRepairStatus,
    val comparison:AdaptiveCycleComparison,
    val proposedOption:AdaptiveCycleOption?,
    val addedWorkingSets:Int,
    val searchTruncated:Boolean,
    val explanation:String
) {
    val maySaveProgram:Boolean get()=false
    val mayApplyAutomatically:Boolean get()=false
    val mayIssueWorkout:Boolean get()=false
    val mayAlterHistory:Boolean get()=false
}

object AdaptiveStrengthVolumeRepairDomain {
    private val zero=BigDecimal.ZERO
    private val one=BigDecimal.ONE
    private const val MAX_ADDING_STEPS=64
    private val id=Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,79}$")

    private fun positive(value:BigDecimal)=value.max(zero)
    private data class Placement(
        val weekday:Int,
        val slotId:String,
        val directBenefit:BigDecimal,
        val totalBenefit:BigDecimal,
        val daySeconds:Long
    )

    fun propose(input:AdaptiveStrengthVolumeRepairRequest):
        AdaptiveStrengthVolumeRepairResult {
        require(input.review.alternatives.isEmpty() &&
            input.review.prospectiveByOptionId.isEmpty()) {
            "Volume repair owns its candidate; separate future schedule changes."
        }
        require(id.matches(input.optionId) &&
            input.maximumWorkingSetsPerSlot in 1..20)
        val initial=AdaptiveCycleOptionReviewDomain.review(input.review)
        fun response(
            status:AdaptiveStrengthVolumeRepairStatus,
            reason:String,
            comparison:AdaptiveCycleComparison=initial,
            option:AdaptiveCycleOption?=null,
            added:Int=0,truncated:Boolean=false
        )=AdaptiveStrengthVolumeRepairResult(status,comparison,option,
            added,truncated,reason)
        if(!initial.sourceRepairRequired)
            return response(AdaptiveStrengthVolumeRepairStatus.SOURCE_UNRESOLVED,
                "Source defects are not limited to this future block's reviewed training-dose continuity.")
        if(!input.review.permissions.adaptSetCount)
            return response(AdaptiveStrengthVolumeRepairStatus.PERMISSION_REQUIRED,
                "Adapt working-set counts must be explicitly enabled for a coach suggestion.")

        val cycle=input.review.approvedCycle
        val block=cycle.blocks.singleOrNull {
            it.blockId==input.review.targetBlockId
        }
        val profile=input.review.athleteSetup.reviewed
        val policy=block?.let {input.review.coaching.policiesByPhase[it.phase]}
        val continuity=input.review.coaching.continuityPolicy
        val target=initial.sourceAudit.weeks.singleOrNull {
            it.blockId==input.review.targetBlockId
        }
        val previous=target?.let {current ->
            initial.sourceAudit.weeks.lastOrNull {
                it.absoluteWeek<current.absoluteWeek &&
                    it.blockPurpose==CustomBlockPurpose.TRAINING
            }
        }
        // A single resolved prospective week makes every addition auditable.
        // Do not erase athlete week overrides, amplifiers, or intentionally
        // varied top/backoff prescriptions to satisfy a simple dose metric.
        if(block==null || block.phase!=TrainingPhase.STRENGTH ||
            block.purpose!=CustomBlockPurpose.TRAINING ||
            block.weeks!=1 || target==null || previous==null ||
            profile==null || policy==null || continuity==null ||
            cycle.weekOverrides[block.blockId].orEmpty().isNotEmpty() ||
            block.days.any {day->day.slots.any {slot->
                val work=slot.sets.filter {it.type==DraftSetType.WORKING}
                work.isEmpty() || work.size!=slot.sets.size ||
                    work.any {it.amplifier!=null || it.targetRir==null} ||
                    work.any {it!=work.first()}
            }})
            return response(AdaptiveStrengthVolumeRepairStatus.PRESCRIPTION_UNSUPPORTED,
                "Only one-week strength blocks with verified, uniform working sets and no custom overrides or amplifiers can be repaired automatically.")

        val dropTotal=continuity.maximumTotalWorkingSetDropFraction
        val dropDirect=continuity.maximumDirectMuscleDropFraction
        if(dropTotal<zero || dropTotal>one ||
            dropDirect<zero || dropDirect>one)
            return response(AdaptiveStrengthVolumeRepairStatus.SOURCE_UNRESOLVED,
                "Training continuity limits are not reviewed.")
        val totalFloor=BigDecimal(previous.workingSets)
            .multiply(one.subtract(dropTotal))
        val directFloor=TrainingMuscle.entries.associateWith {muscle->
            (previous.directSets[muscle] ?:zero)
                .multiply(one.subtract(dropDirect))
        }
        val policyBounds=policy.selectedBounds.associateBy {it.muscle}
        val catalog=input.review.catalog.associateBy {it.exerciseId}
        val maxMinutes=listOfNotNull(
            profile.preferredMaximumMinutes,
            profile.explicitHardMaximumMinutes).minOrNull()
        val maxSeconds=maxMinutes?.times(60L)
        var candidate=cycle
        var working=target.workingSets
        val direct=target.directSets.toMutableMap()
        val credits=target.totalSetCredits.toMutableMap()
        val seconds=target.estimatedSecondsByDay.toMutableMap()
        var added=0
        val activeGaps:()->Boolean={
            BigDecimal(working)<totalFloor ||
                TrainingMuscle.entries.any {muscle->
                    (direct[muscle] ?:zero) <
                        checkNotNull(directFloor[muscle])
                }
        }
        while(activeGaps() && added<MAX_ADDING_STEPS) {
            val targetBlock=checkNotNull(candidate.blocks.singleOrNull {
                it.blockId==block.blockId
            })
            val totalGap=positive(totalFloor.subtract(BigDecimal(working)))
            val placements=mutableListOf<Placement>()
            for(day in targetBlock.days) for(slot in day.slots) {
                val sourceSet=slot.sets.first()
                val elapsed=seconds[day.weekday] ?:continue
                val cost=sourceSet.activeSeconds.toLong()+sourceSet.restSeconds
                val limit=input.maximumWorkingSetsPerSlot
                if(slot.sets.count {it.type==DraftSetType.WORKING}>=limit ||
                    slot.sets.size>=20 ||
                    (maxSeconds!=null && elapsed+cost>maxSeconds))
                    continue
                val movement=catalog[slot.exerciseId] ?:continue
                var gain=zero
                var excess=false
                for(credit in movement.credits) {
                    val muscle=credit.muscle
                    val existing=credits[muscle] ?:zero
                    val ceiling=policyBounds[muscle]?.mrv
                    if(ceiling==null || existing.add(credit.fractionalSet)>ceiling) {
                        excess=true
                        break
                    }
                    if(credit.role==MuscleCreditRole.DIRECT) {
                        val gap=positive(
                            checkNotNull(directFloor[muscle])
                                .subtract(direct[muscle] ?:zero))
                        gain=gain.add(gap.min(credit.fractionalSet))
                    }
                }
                if(!excess && (gain>zero || totalGap>zero))
                    placements+=Placement(day.weekday,slot.slotId,
                        gain,totalGap.min(one),elapsed)
            }
            val pick=placements.sortedWith(
                compareByDescending<Placement> {it.directBenefit}
                    .thenByDescending {it.totalBenefit}
                    .thenBy {it.daySeconds}
                    .thenBy {it.weekday}
                    .thenBy {it.slotId}
            ).firstOrNull() ?:break
            val currentDay=targetBlock.days.single {it.weekday==pick.weekday}
            val selected=currentDay.slots.single {it.slotId==pick.slotId}
            val oneMore=selected.sets.first()
            val move=checkNotNull(catalog[selected.exerciseId])
            candidate=candidate.copy(revision=cycle.revision+1,
                blocks=candidate.blocks.map {existing ->
                    if(existing.blockId!=block.blockId)existing
                    else existing.copy(days=existing.days.map {day ->
                        if(day.weekday!=pick.weekday)day
                        else day.copy(slots=day.slots.map {slot ->
                            if(slot.slotId!=pick.slotId)slot
                            else slot.copy(sets=slot.sets+oneMore)
                        })
                    })
                })
            }
            working++
            added++
            seconds[pick.weekday]=checkNotNull(seconds[pick.weekday])+
                oneMore.activeSeconds+oneMore.restSeconds
            for(credit in move.credits) {
                val muscle=credit.muscle
                credits[muscle]=(credits[muscle] ?:zero).add(credit.fractionalSet)
                if(credit.role==MuscleCreditRole.DIRECT)
                    direct[muscle]=(direct[muscle] ?:zero)
                        .add(credit.fractionalSet)
            }
        }
        val truncated=activeGaps() && added==MAX_ADDING_STEPS
        if(activeGaps() || added==0)
            return response(AdaptiveStrengthVolumeRepairStatus.NO_FEASIBLE_REPAIR,
                "Existing exact sets cannot repair every muscle and total-volume deficit within authored set ceilings and session limits.",
                added=added,truncated=truncated)
        val proposed=AdaptiveCycleOption(input.optionId,candidate)
        val comparison=AdaptiveCycleOptionReviewDomain.review(
            input.review.copy(alternatives=listOf(proposed)))
        val approved=comparison.evaluated.singleOrNull()?.status==
            AdaptiveCycleOptionStatus.READY_FOR_REVIEW &&
            comparison.recommendedOptionId==input.optionId
        if(!approved)
            return response(AdaptiveStrengthVolumeRepairStatus.NO_FEASIBLE_REPAIR,
                "The candidate still fails the full-cycle dose, equipment, time, phase or edit-permission review.",
                comparison,added=added)
        return response(AdaptiveStrengthVolumeRepairStatus.CANDIDATE_READY_FOR_REVIEW,
            "Rebalanced $added existing, exact strength working-set prescriptions; all unchanged blocks and training records remain untouched.",
            comparison,proposed,added)
    }
}
