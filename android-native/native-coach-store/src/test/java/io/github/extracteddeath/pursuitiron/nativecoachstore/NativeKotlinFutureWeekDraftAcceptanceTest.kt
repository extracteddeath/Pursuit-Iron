package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class NativeKotlinFutureWeekDraftAcceptanceTest {
    private fun cycle(reps:Int=10)=CustomTrainingCycle(
        "cycle1",2,"athlete1",AthleteTrainingGoal.HYPERTROPHY,
        "Future week",listOf(CustomTrainingBlock(
            "base","Base",TrainingPhase.HYPERTROPHY,
            CustomBlockPurpose.TRAINING,4,
            CustomSplitIdentity.Freeform("custom"),
            listOf(CustomTrainingDay(0,"Core",ComposerSessionPurpose.HYPERTROPHY,
                listOf(CustomExerciseSlot("slot0","bodyweight_crunch",
                    ComposerRole.ACCESSORY,listOf(CustomAuthoredSet(
                        DraftSetType.WORKING,DraftSetLoad.ExplicitBodyweight,
                        RepBand(reps,15),2,45,75)))))))))

    private fun result(
        cycle:CustomTrainingCycle=cycle(),
        history:Long=17,
        consent:Long=3,
        permissions:Long=5,
        assistance:Long=6
    )=NativeFutureWeekProposalResult.Reviewed(
        ReviewedFutureWeekProgression(
            ReviewedFutureWeekProgressionStatus.CANDIDATE_FOR_ATHLETE_REVIEW,
            null,cycle,null,3,"slot0","Verified proposal"),1,2,
        history,permissions,consent,assistance)

    private fun selection()=NativeFutureWeekDraftSelection(
        "owner1","cycle1",3,0,"slot0",CoachReadiness.NORMAL,
        false,BigDecimal("5"),CustomRepForecastPolicy.TARGET_RIR_V2)

    @Test fun onlyExactAuditedNextRevisionIsEligible() {
        val good=result()
        assertTrue(NativeKotlinFutureWeekDraftAcceptance.eligible(good))
        assertFalse(NativeKotlinFutureWeekDraftAcceptance.eligible(
            good.copy(proposal=good.proposal.copy(
                status=ReviewedFutureWeekProgressionStatus.HOLD_WITHOUT_AMENDMENT))))
        assertFalse(NativeKotlinFutureWeekDraftAcceptance.eligible(
            good.copy(proposal=good.proposal.copy(candidate=null))))
        assertFalse(NativeKotlinFutureWeekDraftAcceptance.eligible(
            good.copy(proposal=good.proposal.copy(changedSlotId=null))))
        assertFalse(NativeKotlinFutureWeekDraftAcceptance.eligible(
            good.copy(proposal=good.proposal.copy(candidate=cycle().copy(revision=3)))))
    }

    @Test fun confirmationDigestBindsAllMutableAuthorityAndActualTargets() {
        val s=selection()
        val v=result()
        val original=NativeKotlinFutureWeekDraftAcceptance.fingerprint(s,v)
        assertEquals(64,original.length)
        fun different(s:NativeFutureWeekDraftSelection=selection(),
            v:NativeFutureWeekProposalResult.Reviewed=result()) {
            assertNotEquals(original,
                NativeKotlinFutureWeekDraftAcceptance.fingerprint(s,v))
        }
        different(v=result(history=18))
        different(v=result(consent=4))
        different(v=result(permissions=6))
        different(v=result(assistance=7))
        different(v=v.copy(machineRosterRevision=7))
        different(v=v.copy(gymSourceRevision=3))
        different(v=v.copy(cycleRevision=2,
            proposal=v.proposal.copy(candidate=cycle().copy(revision=3))))
        different(v=result(cycle(reps=11)))
        different(s=s.copy(readiness=CoachReadiness.LOW))
        different(s=s.copy(movementRestricted=true))
        different(s=s.copy(maximumAuthorizedIncrease=BigDecimal("7")))
        different(s=s.copy(slotId="other_slot"))
        different(s=s.copy(forecast=CustomRepForecastPolicy.TARGET_RIR_TREND_V3))
        // Decimal format is normalized, so identical gym increments
        // produce the same consent fingerprint.
        assertEquals(original,NativeKotlinFutureWeekDraftAcceptance.fingerprint(
            s.copy(maximumAuthorizedIncrease=BigDecimal("5.0")),v))
    }

    @Test fun preparedOrIssuedWeekAndAnyUnfinishedSessionBlockSaving() {
        val p=NativeFutureWeekProposalInventoryPolicy
        assertFalse(p.blocks(emptyList(),"cycle1",3))
        assertFalse(p.blocks(listOf(NativeCustomInventoryFact(
            "old","cycle1",2,0,true,true)),"cycle1",3))
        assertFalse(p.blocks(listOf(NativeCustomInventoryFact(
            "other","another",3,0,true,false)),"cycle1",3))
        assertTrue(p.blocks(listOf(NativeCustomInventoryFact(
            "prepared","cycle1",3,2,false,false)),"cycle1",3))
        assertTrue(p.blocks(listOf(NativeCustomInventoryFact(
            "issued","cycle1",3,0,true,false)),"cycle1",3))
        assertTrue(p.blocks(listOf(NativeCustomInventoryFact(
            "finished-target","cycle1",3,0,true,true)),"cycle1",3))
        assertTrue(p.blocks(listOf(NativeCustomInventoryFact(
            "unfinished-other-week","cycle1",2,2,true,false)),"cycle1",3))
    }
}
