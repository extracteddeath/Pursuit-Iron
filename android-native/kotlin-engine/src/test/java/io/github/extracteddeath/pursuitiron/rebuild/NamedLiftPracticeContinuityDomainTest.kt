package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class NamedLiftPracticeContinuityDomainTest {
    private fun working(type:DraftSetType=DraftSetType.WORKING)=
        CustomAuthoredSet(type,DraftSetLoad.ExplicitBodyweight,
            RepBand(5,8),2,45,120)
    private fun day(weekday:Int,exercise:String="bench",
        type:DraftSetType=DraftSetType.WORKING,
        role:ComposerRole=ComposerRole.PRIMARY)=
        CustomTrainingDay(weekday,"Strength",ComposerSessionPurpose.STRENGTH_SKILL,
            listOf(CustomExerciseSlot("slot-$weekday",exercise,
                role,listOf(working(type)))))
    private fun cycle(days:List<CustomTrainingDay>,
        overrides:List<CustomWeekSlotOverride> = emptyList()) =
        CustomTrainingCycle("cycle-1",4,"athlete-1",
            AthleteTrainingGoal.STRENGTH,"Strength plan",
            listOf(CustomTrainingBlock("block-1","Strength",
                TrainingPhase.STRENGTH,CustomBlockPurpose.TRAINING,
                2,CustomSplitIdentity.Freeform("custom"),days)),
            weekOverrides=if(overrides.isEmpty())emptyMap()
                else mapOf("block-1" to overrides))
    private fun ready(cycle:CustomTrainingCycle)=CustomCycleReview(
        CustomCycleReviewStatus.READY_FOR_COACH_REVIEW,
        emptyList(),2,cycle)
    private fun lost(
        before:CustomTrainingCycle,after:CustomTrainingCycle,
        lifts:Set<String> = setOf("bench")) =
        NamedLiftPracticeContinuityDomain.lostDays(
            ready(before),ready(after),"block-1",lifts)

    @Test fun namedLiftIdentityAndWorkingStatusDefinePractice() {
        val approved=cycle(listOf(day(0),day(3)))
        assertEquals(BigDecimal.ONE,lost(approved,
            cycle(listOf(day(0),day(3,"ohp")))))
        assertEquals(BigDecimal.ONE,lost(approved,
            cycle(listOf(day(0),day(3,type=DraftSetType.WARMUP)))))
        assertEquals(BigDecimal.ONE,lost(approved,
            cycle(listOf(day(0),day(3,role=ComposerRole.SECONDARY)))))
        // Do not force any universal two-day frequency when source had one.
        assertEquals(BigDecimal.ZERO,lost(
            cycle(listOf(day(0))),cycle(listOf(day(0),day(3)))))
        // An unrelated lift never creates an invented penalty.
        assertEquals(BigDecimal.ZERO,lost(approved,
            cycle(listOf(day(0),day(3,"ohp"))),setOf("squat")))
    }

    @Test fun resolvedWeekOverridesPreventBestWeekCherryPicking() {
        val approved=cycle(listOf(day(0),day(3)))
        val changed=cycle(listOf(day(0),day(3)),
            listOf(CustomWeekSlotOverride(2,3,"slot-3",
                listOf(working(DraftSetType.WARMUP)))))
        assertEquals(BigDecimal.ONE,lost(approved,changed))
        val firstOnly=cycle(listOf(day(0),day(3)),
            listOf(CustomWeekSlotOverride(1,3,"slot-3",
                listOf(working(DraftSetType.WARMUP)))))
        // Both cycles have the same minimum weekly frequency; only
        // aligned per-week evidence reveals the reversed missing exposure.
        assertEquals(BigDecimal.ONE,lost(firstOnly,changed))
        assertEquals(BigDecimal.ONE,lost(changed,firstOnly))
        assertEquals(BigDecimal.ZERO,lost(approved,approved))
    }

    @Test fun absentNamedLiftsCannotInfluenceRankingAndUnverifiedReviewFailsClosed() {
        val approved=cycle(listOf(day(0),day(3)))
        assertEquals(BigDecimal.ZERO,lost(approved,approved,emptySet()))
        assertThrows(IllegalArgumentException::class.java) {
            NamedLiftPracticeContinuityDomain.lostDays(
                CustomCycleReview(CustomCycleReviewStatus.UNRESOLVED,
                    emptyList(),2,null),ready(approved),"block-1",
                setOf("bench"))
        }
    }
}
