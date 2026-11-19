package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class WholeCycleStrengthContinuityDomainTest {
    private val stock=VerifiedLoadStock.Totals(
        LoadUnit.LB,LoadBasis.DUMBBELL_PER_HAND,listOf(BigDecimal("20")))
    private val load=DraftSetLoad.Verified(
        LoadUnit.LB,LoadBasis.DUMBBELL_PER_HAND,BigDecimal("20"))
    private fun set()=CustomAuthoredSet(
        DraftSetType.WORKING,load,RepBand(5,10),2,60,120)
    private fun day(n:Int,setsEach:Int=1):CustomTrainingDay=
        CustomTrainingDay(0,"Monday",
            ComposerSessionPurpose.HYPERTROPHY,
            (1..n).map {id->
                CustomExerciseSlot("slot_$id","exercise_$id",
                    if(id==1)ComposerRole.PRIMARY else ComposerRole.ACCESSORY,
                    List(setsEach){set()},pinned=false)
            })
    private fun cycle(
        prior:CustomTrainingDay=day(8),
        next:CustomTrainingDay=day(3),
        nextPhase:TrainingPhase=TrainingPhase.STRENGTH,
        bridge:Boolean=false
    ):CustomTrainingCycle {
        val split=CustomSplitIdentity.Named(ComposerSplit.FULL_BODY)
        val base=CustomTrainingBlock("hyper","Hypertrophy",
            TrainingPhase.HYPERTROPHY,CustomBlockPurpose.TRAINING,3,
            split,listOf(prior))
        val after=CustomTrainingBlock("strength","Strength",
            nextPhase,CustomBlockPurpose.TRAINING,3,split,
            listOf(next.copy(purpose=ComposerSessionPurpose.STRENGTH_SKILL)))
        val middle=if(bridge)
            listOf(base.copy(blockId="deload",label="Recovery",
                purpose=CustomBlockPurpose.RECOVERY,weeks=1,
                days=listOf(day(2)))) else emptyList()
        return CustomTrainingCycle("guard-cycle",1,"athlete",
            AthleteTrainingGoal.POWERBUILDING,"Guard comparison",
            listOf(base)+middle+after)
    }
    private val available=(1..8).map {
        CustomExerciseAvailability("exercise_$it",stock,false)
    }
    private fun review(cycle:CustomTrainingCycle)=
        WholeCycleStrengthContinuityDomain.review(
            CustomTrainingCycleDomain.review(cycle,available))

    @Test fun reportedEightToThreeStrengthDayBlocksAReadyClaim() {
        val result=review(cycle())
        assertEquals(WholeCycleStrengthContinuityStatus.REQUIRES_ATHLETE_REVIEW,
            result.status)
        val finding=result.findings.single()
        assertEquals("strength",finding.blockId)
        assertEquals(setOf(0),finding.affectedWeekdays)
        assertTrue(finding.hasSevereRosterLoss)
        assertTrue(finding.hasHalvedWorkingDose)
        assertEquals(8,finding.roster.affectedDays.single().beforeWorkingExercises)
        assertEquals(3,finding.roster.affectedDays.single().afterWorkingExercises)
        assertEquals(8,finding.dose.hypertrophyWorkingSets)
        assertEquals(3,finding.dose.strengthWorkingSets)
        assertTrue(result.explanation.contains("8 to 3 exercises"))
        assertFalse(result.mayIssueWorkout)
        assertFalse(result.mayApplyAutomatically)
        assertFalse(result.maySaveProgram)
    }

    @Test fun exerciseCountAloneDoesNotMaskHalvedRealWorkingSets() {
        val result=review(cycle(prior=day(8,setsEach=2),next=day(8)))
        assertEquals(WholeCycleStrengthContinuityStatus.REQUIRES_ATHLETE_REVIEW,
            result.status)
        val finding=result.findings.single()
        assertFalse(finding.hasSevereRosterLoss)
        assertTrue(finding.hasHalvedWorkingDose)
        assertEquals(16,finding.dose.hypertrophyWorkingSets)
        assertEquals(8,finding.dose.strengthWorkingSets)
        assertEquals(setOf(0),finding.affectedWeekdays)
        assertTrue(result.explanation.contains("16 to 8 working sets"))
    }

    @Test fun continuousComparableStrengthAndIntentionalPeakTaperAreNotBlocked() {
        val stable=review(cycle(prior=day(8),next=day(8)))
        assertEquals(WholeCycleStrengthContinuityStatus.CONTINUOUS,stable.status)
        assertTrue(stable.findings.isEmpty())
        val peak=review(cycle(prior=day(8),next=day(2),
            nextPhase=TrainingPhase.PEAK))
        assertEquals(WholeCycleStrengthContinuityStatus.CONTINUOUS,peak.status)
        assertTrue(peak.findings.isEmpty())
    }

    @Test fun recoveryBridgeNeverReplacesActualHypertrophyBaseline() {
        val bridged=review(cycle(bridge=true))
        assertEquals(WholeCycleStrengthContinuityStatus.REQUIRES_ATHLETE_REVIEW,
            bridged.status)
        val finding=bridged.findings.single()
        assertEquals(3,finding.roster.sourceExitWeek)
        assertEquals(5,finding.roster.targetEntryWeek)
        assertEquals(8,finding.dose.hypertrophyWorkingSets)
        assertEquals(3,finding.dose.strengthWorkingSets)
    }

    @Test fun unverifiedOrIncompleteCycleNeverBecomesContinuous() {
        assertEquals(WholeCycleStrengthContinuityStatus.SOURCE_UNVERIFIED,
            WholeCycleStrengthContinuityDomain.review(null).status)
        val input=cycle()
        val invalid=CustomTrainingCycleDomain.review(input,available.dropLast(1))
        val unresolved=WholeCycleStrengthContinuityDomain.review(invalid)
        assertEquals(WholeCycleStrengthContinuityStatus.SOURCE_UNVERIFIED,
            unresolved.status)
        assertTrue(unresolved.findings.isEmpty())
        assertFalse(unresolved.mayIssueWorkout)
    }
}
