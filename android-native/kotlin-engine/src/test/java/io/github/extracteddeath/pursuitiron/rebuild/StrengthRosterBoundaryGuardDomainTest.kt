package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

/** Regression for reported eight Hypertrophy exercises -> three in Strength. */
class StrengthRosterBoundaryGuardDomainTest {
    private val stock=VerifiedLoadStock.Totals(LoadUnit.LB,
        LoadBasis.DUMBBELL_PER_HAND,listOf(BigDecimal("20")))
    private val load=DraftSetLoad.Verified(LoadUnit.LB,
        LoadBasis.DUMBBELL_PER_HAND,BigDecimal("20"))
    private fun set(type:DraftSetType=DraftSetType.WORKING)=
        CustomAuthoredSet(type,load,RepBand(5,10),2,60,120)
    private fun day(count:Int,weekday:Int=0):CustomTrainingDay =
        CustomTrainingDay(weekday,"Day $weekday",
            ComposerSessionPurpose.HYPERTROPHY,
            (1..count).map {index->
                CustomExerciseSlot("slot_$index","exercise_$index",
                    if(index==1)ComposerRole.PRIMARY else ComposerRole.ACCESSORY,
                    listOf(set()),pinned=false)
            })
    private fun cycle(
        before:Int=8,after:Int=3,
        nextPhase:TrainingPhase=TrainingPhase.STRENGTH,
        nextSplit:CustomSplitIdentity=CustomSplitIdentity.Named(
            ComposerSplit.FULL_BODY),
        overrides:Map<String,List<CustomWeekSlotOverride>> = emptyMap()
    )=CustomTrainingCycle(
        "cycle-review",4,"athlete-review",AthleteTrainingGoal.POWERBUILDING,
        "Phase boundary",listOf(
            CustomTrainingBlock("base","Hypertrophy",TrainingPhase.HYPERTROPHY,
                CustomBlockPurpose.TRAINING,3,
                CustomSplitIdentity.Named(ComposerSplit.FULL_BODY),
                listOf(day(before))),
            CustomTrainingBlock("next","Strength",nextPhase,
                CustomBlockPurpose.TRAINING,3,nextSplit,
                listOf(day(after).copy(
                    purpose=ComposerSessionPurpose.STRENGTH_SKILL)))),
        overrides)
    private val available=(1..8).map {CustomExerciseAvailability(
        "exercise_$it",stock,false)}
    private fun inspect(cycle:CustomTrainingCycle):StrengthRosterBoundaryReview =
        StrengthRosterBoundaryGuardDomain.review(
            CustomTrainingCycleDomain.review(cycle,available),"next")

    @Test fun verifiedEightToThreeSameDayStrengthMenuIsNeverAcceptedQuietly() {
        val result=inspect(cycle())
        assertEquals(StrengthRosterBoundaryStatus.MATERIAL_EXERCISE_LOSS_REQUIRES_REVIEW,
            result.status)
        assertEquals(3,result.sourceExitWeek)
        assertEquals(4,result.targetEntryWeek)
        assertEquals(1,result.affectedDays.size)
        val lost=result.affectedDays.single()
        assertEquals(0,lost.weekday)
        assertEquals(8,lost.beforeWorkingExercises)
        assertEquals(3,lost.afterWorkingExercises)
        assertEquals((4..8).map{"exercise_$it"}.toSet(),
            lost.missingExerciseIds)
        assertFalse(result.canIssueWorkout)
        assertFalse(result.mayApplyAutomatically)
    }

    @Test fun exactHalfIsNotTheSameAsSevereEightToThreeCollapse() {
        assertEquals(StrengthRosterBoundaryStatus.CONTINUOUS,
            inspect(cycle(before=6,after=3)).status)
        assertEquals(StrengthRosterBoundaryStatus.CONTINUOUS,
            inspect(cycle(before=8,after=4)).status)
        assertEquals(StrengthRosterBoundaryStatus.CONTINUOUS,
            inspect(cycle(before=5,after=2)).status)
        assertEquals(StrengthRosterBoundaryStatus.CONTINUOUS,
            inspect(cycle(before=8,after=8)).status)
    }

    @Test fun sourceLastWeekOverridesDetermineTheActualMenuNotWeekOne() {
        val adjusted=(1..3).map{index->
            CustomWeekSlotOverride(3,0,"slot_$index",
                listOf(set(DraftSetType.WARMUP)))
        }
        val exact=cycle(overrides=mapOf("base" to adjusted))
        val check=CustomTrainingCycleDomain.review(exact,available)
        assertEquals(check.issues.toString(),
            CustomCycleReviewStatus.READY_FOR_COACH_REVIEW,check.status)
        assertEquals(StrengthRosterBoundaryStatus.CONTINUOUS,
            StrengthRosterBoundaryGuardDomain.review(check,"next").status)
        assertEquals(StrengthRosterBoundaryStatus.MATERIAL_EXERCISE_LOSS_REQUIRES_REVIEW,
            inspect(cycle(overrides=mapOf("base" to listOf(
                CustomWeekSlotOverride(1,0,"slot_1",
                    listOf(set(DraftSetType.WARMUP))))))).status)
    }

    @Test fun splitChangesAndPeakTapersNeedTheirOwnAuthoritativeAudits() {
        assertEquals(StrengthRosterBoundaryStatus.NOT_COMPARABLE,
            inspect(cycle(nextPhase=TrainingPhase.PEAK)).status)
        assertEquals(StrengthRosterBoundaryStatus.NOT_COMPARABLE,
            inspect(cycle(nextSplit=CustomSplitIdentity.Named(
                ComposerSplit.UPPER_LOWER))).status)
        assertEquals(StrengthRosterBoundaryStatus.NOT_COMPARABLE,
            inspect(cycle().copy(blocks=cycle().blocks.mapIndexed{i,block->
                if(i==1)block.copy(purpose=CustomBlockPurpose.RECOVERY)
                else block
            })).status)
    }

    @Test fun incompleteOrForgedReviewCannotAuthorizeTheBoundary() {
        val valid=CustomTrainingCycleDomain.review(cycle(),available)
        assertEquals(StrengthRosterBoundaryStatus.NOT_COMPARABLE,
            StrengthRosterBoundaryGuardDomain.review(valid,"missing").status)
        val incomplete=CustomTrainingCycleDomain.review(cycle(),
            available.dropLast(1))
        assertEquals(CustomCycleReviewStatus.UNRESOLVED,incomplete.status)
        assertEquals(StrengthRosterBoundaryStatus.SOURCE_UNVERIFIED,
            StrengthRosterBoundaryGuardDomain.review(incomplete,"next").status)
        val bogus=CustomCycleReview(
            CustomCycleReviewStatus.READY_FOR_COACH_REVIEW,emptyList(),6,null)
        assertEquals(StrengthRosterBoundaryStatus.SOURCE_UNVERIFIED,
            StrengthRosterBoundaryGuardDomain.review(bogus,"next").status)
    }

    @Test fun sameMenuButHalfSourceWorkingSetsCannotBeSilentlyGenerated() {
        val original=cycle(before=8,after=8)
        val prior=original.blocks.first()
        val inflated=original.copy(blocks=listOf(
            prior.copy(days=prior.days.map{day->
                day.copy(slots=day.slots.map{slot->
                    slot.copy(sets=listOf(set(),set()))
                })
            }),original.blocks.last()))
        val review=CustomTrainingCycleDomain.review(inflated,available)
        assertEquals(review.issues.toString(),
            CustomCycleReviewStatus.READY_FOR_COACH_REVIEW,review.status)
        assertEquals(StrengthRosterBoundaryStatus.CONTINUOUS,
            StrengthRosterBoundaryGuardDomain.review(review,"next").status)
        val result=StrengthDoseBoundaryGuardDomain.review(review,"next")
        assertEquals(StrengthDoseBoundaryStatus.HALVED_WORKING_DOSE_REQUIRES_REVIEW,
            result.status)
        assertEquals(16,result.hypertrophyWorkingSets)
        assertEquals(8,result.strengthWorkingSets)
        assertEquals(3,result.hypertrophyExitWeek)
        assertEquals(4,result.strengthEntryWeek)
        assertFalse(result.mayApplyAutomatically)
        assertFalse(result.mayChangeHistory)
        assertFalse(result.mayIssueWorkout)
    }

    @Test fun legitimateSmallAndLessThanHalfDoseChangesStayReviewable() {
        assertEquals(StrengthDoseBoundaryStatus.CONTINUOUS,
            StrengthDoseBoundaryGuardDomain.review(
                CustomTrainingCycleDomain.review(cycle(6,3),available),
                "next").status)
        assertEquals(StrengthDoseBoundaryStatus.CONTINUOUS,
            StrengthDoseBoundaryGuardDomain.review(
                CustomTrainingCycleDomain.review(cycle(8,5),available),
                "next").status)
        assertEquals(StrengthDoseBoundaryStatus.HALVED_WORKING_DOSE_REQUIRES_REVIEW,
            StrengthDoseBoundaryGuardDomain.review(
                CustomTrainingCycleDomain.review(cycle(8,4),available),
                "next").status)
    }

    @Test fun exactFinalHypertrophyAndFirstStrengthOverridesControlDose() {
        val raw=cycle(before=8,after=8)
        val prior=raw.blocks.first()
        val twice=raw.copy(blocks=listOf(
            prior.copy(days=prior.days.map{day->
                day.copy(slots=day.slots.map{slot->
                    slot.copy(sets=listOf(set(),set()))
                })
            }),raw.blocks.last()))
        val lastWeekOverrides=(1..8).map{n->
            CustomWeekSlotOverride(3,0,"slot_$n",listOf(set()))
        }
        val trueBoundary=twice.copy(weekOverrides=mapOf(
            "base" to lastWeekOverrides))
        val structural=CustomTrainingCycleDomain.review(trueBoundary,available)
        assertEquals(structural.issues.toString(),
            CustomCycleReviewStatus.READY_FOR_COACH_REVIEW,structural.status)
        val actual=StrengthDoseBoundaryGuardDomain.review(structural,"next")
        assertEquals(StrengthDoseBoundaryStatus.CONTINUOUS,actual.status)
        assertEquals(8,actual.hypertrophyWorkingSets)
        assertEquals(8,actual.strengthWorkingSets)
        val onlyWeekOne=twice.copy(weekOverrides=mapOf("base" to
            lastWeekOverrides.map{it.copy(weekInBlock=1)}))
        assertEquals(StrengthDoseBoundaryStatus.HALVED_WORKING_DOSE_REQUIRES_REVIEW,
            StrengthDoseBoundaryGuardDomain.review(
                CustomTrainingCycleDomain.review(onlyWeekOne,available),
                "next").status)
        // Extra warmups do not masquerade as working-set dose.
        val warmups=cycle(before=8,after=4).copy(blocks=
            cycle(before=8,after=4).blocks.mapIndexed{index,block->
                if(index!=0)block else block.copy(days=block.days.map{day->
                    day.copy(slots=day.slots.map{slot->
                        slot.copy(sets=listOf(set())+
                            List(4){set(DraftSetType.WARMUP)})
                    })
                })
            })
        assertEquals(8,StrengthDoseBoundaryGuardDomain.review(
            CustomTrainingCycleDomain.review(warmups,available),
            "next").hypertrophyWorkingSets)
    }

    @Test fun peakAndUnverifiedDoseHaveNoFalseStrengthClearance() {
        assertEquals(StrengthDoseBoundaryStatus.NOT_COMPARABLE,
            StrengthDoseBoundaryGuardDomain.review(
                CustomTrainingCycleDomain.review(
                    cycle(nextPhase=TrainingPhase.PEAK),available),"next").status)
        assertEquals(StrengthDoseBoundaryStatus.NOT_COMPARABLE,
            StrengthDoseBoundaryGuardDomain.review(
                CustomTrainingCycleDomain.review(cycle(
                    nextSplit=CustomSplitIdentity.Named(ComposerSplit.UPPER_LOWER)),
                    available),"next").status)
        assertEquals(StrengthDoseBoundaryStatus.SOURCE_UNVERIFIED,
            StrengthDoseBoundaryGuardDomain.review(
                CustomTrainingCycleDomain.review(cycle(),available.dropLast(1)),
                "next").status)
    }

}
