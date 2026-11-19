package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

/** A same-day Strength dose loss may be hidden by other days gaining sets. */
class AdaptiveStrengthDayDoseRepairTest {
    private val fixture=GeneratedAdaptiveBlockFixture
    private fun n(value:String)=BigDecimal(value)
    private fun cycle():CustomTrainingCycle {
        val original=fixture.cycle()
        val template=original.blocks.single()
        fun days(strength:Boolean):List<CustomTrainingDay> =
            template.days.map{day->
                val amounts=when(day.weekday){
                    0->if(strength)listOf(3,3,2) else listOf(6,5,5)
                    2->if(strength)listOf(6,5,5) else listOf(3,3,2)
                    else->listOf(1,1,1)
                }
                day.copy(purpose=if(strength)
                    ComposerSessionPurpose.STRENGTH_SKILL else
                    ComposerSessionPurpose.HYPERTROPHY,
                    slots=day.slots.mapIndexed{index,slot->
                        val exact=slot.sets.single().copy(
                            activeSeconds=40,restSeconds=70)
                        slot.copy(sets=List(amounts[index]){exact})
                    })
            }
        val base=template.copy(blockId="hypertrophy",weeks=2,days=days(false))
        val future=template.copy(blockId="strength",weeks=2,
            phase=TrainingPhase.STRENGTH,days=days(true))
        return original.copy(blocks=listOf(base,future))
    }
    private fun request(permission:Boolean=true,limit:Int=10):
        AdaptiveStrengthVolumeRepairRequest {
        val base=checkNotNull(fixture.generation().policy)
        val generous=base.selectedBounds.map{
            it.copy(mev=n("0"),mav=n("12"),mrv=n("45"),minimumDirectDays=0)
        }
        val h=base.copy(baselineBounds=generous,selectedBounds=generous)
        val s=h.copy(policyId="strength-day-dose",revision="strength-v2",
            phase=TrainingPhase.STRENGTH,
            minimumPurposeDays=mapOf(ComposerSessionPurpose.STRENGTH_SKILL to 3),
            primaryLiftMinimumDays=mapOf("bench" to 1))
        val coaching=CustomCoachAuditInputs(
            policiesByPhase=mapOf(TrainingPhase.HYPERTROPHY to h,
                TrainingPhase.STRENGTH to s),
            nonSetSecondsByWeekday=mapOf(0 to 300,2 to 300,4 to 300),
            continuityPolicy=CustomCoachVolumeContinuityPolicy(
                n("0.25"),n("0.25"),n("0.8"),n("0.8")))
        return AdaptiveStrengthVolumeRepairRequest(
            AdaptiveCycleOptionRequest(cycle(),"strength",fixture.athlete(),
                fixture.movements,coaching,
                CustomCycleCoachPermissions(adaptSetCount=permission),
                emptyList()),limit)
    }

    @Test fun reconstructsDayDoseDespiteUnchangedWeeklyTotalWithoutMutatingSource() {
        val input=request()
        val original=input.review.approvedCycle
        val structural=CustomTrainingCycleDomain.review(original,
            CustomExerciseAuthoringDomain.picker(fixture.athlete(),
                fixture.movements).map{it.availability})
        assertEquals(structural.issues.toString(),
            CustomCycleReviewStatus.READY_FOR_COACH_REVIEW,structural.status)
        val before=StrengthDoseBoundaryGuardDomain.review(structural,"strength")
        assertEquals(27,before.hypertrophyWorkingSets)
        assertEquals(27,before.strengthWorkingSets)
        assertEquals(listOf(StrengthDayDoseLoss(0,16,8)),before.affectedDays)
        val outcome=AdaptiveStrengthVolumeRepairDomain.propose(input)
        assertEquals(outcome.explanation+" / "+outcome.comparison.evaluated.map{
            it.status to it.coachingAudit?.issues},
            AdaptiveStrengthVolumeRepairStatus.CANDIDATE_READY_FOR_REVIEW,
            outcome.status)
        assertEquals(4,outcome.addedWorkingSets)
        assertEquals(2,outcome.repairedWeekCount)
        val option=checkNotNull(outcome.proposedOption)
        val candidate=option.proposedCycle
        assertEquals(original.revision+1,candidate.revision)
        assertEquals(original.blocks.first(),candidate.blocks.first())
        assertEquals(original.blocks.last().days.drop(1),
            candidate.blocks.last().days.drop(1))
        val reviewed=outcome.comparison.evaluated.single()
        assertEquals(AdaptiveCycleOptionStatus.READY_FOR_REVIEW,reviewed.status)
        assertEquals(StrengthDoseBoundaryStatus.CONTINUOUS,
            StrengthDoseBoundaryGuardDomain.review(
                checkNotNull(reviewed.coachingAudit?.structuralReview),
                "strength").status)
        assertEquals(27,CustomTrainingCycleDomain.week(structural,3)!!.days
            .sumOf{day->day.slots.sumOf{slot->
                slot.sets.count{it.type==DraftSetType.WORKING}}})
        assertEquals(4L,original.revision)
        assertFalse(outcome.mayApplyAutomatically)
        assertFalse(outcome.maySaveProgram)
        assertFalse(outcome.mayIssueWorkout)
        assertFalse(outcome.mayAlterHistory)
    }

    @Test fun consentAndUnattainableSetCapacityRefuseWithoutWriting() {
        val without=AdaptiveStrengthVolumeRepairDomain.propose(
            request(permission=false))
        assertEquals(AdaptiveStrengthVolumeRepairStatus.PERMISSION_REQUIRED,
            without.status)
        assertNull(without.proposedOption)
        val capped=AdaptiveStrengthVolumeRepairDomain.propose(
            request(limit=6))
        assertTrue(capped.status!=
            AdaptiveStrengthVolumeRepairStatus.CANDIDATE_READY_FOR_REVIEW)
        assertNull(capped.proposedOption)
    }
}
