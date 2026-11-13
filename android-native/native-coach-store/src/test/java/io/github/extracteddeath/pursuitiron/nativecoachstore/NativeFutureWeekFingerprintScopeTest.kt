package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

/**
 * Signed future-week draft review must bind owner, week and day identity.
 * A stale preview digest cannot authorize another owner's or day's change.
 */
class NativeFutureWeekFingerprintScopeTest {
    private fun candidate():CustomTrainingCycle {
        val set=CustomAuthoredSet(
            DraftSetType.WORKING,DraftSetLoad.ExplicitBodyweight,
            RepBand(10,15),2,45,75)
        val slot=CustomExerciseSlot(
            "slot0","bodyweight_crunch",ComposerRole.ACCESSORY,listOf(set))
        val day=CustomTrainingDay(
            0,"Core",ComposerSessionPurpose.HYPERTROPHY,listOf(slot))
        val block=CustomTrainingBlock(
            "base","Base",TrainingPhase.HYPERTROPHY,
            CustomBlockPurpose.TRAINING,4,
            CustomSplitIdentity.Freeform("custom"),listOf(day))
        return CustomTrainingCycle(
            "cycle1",2,"athlete1",AthleteTrainingGoal.HYPERTROPHY,
            "Week review",listOf(block))
    }

    private fun reviewed():NativeFutureWeekProposalResult.Reviewed =NativeFutureWeekProposalResult.Reviewed(
        ReviewedFutureWeekProgression(
            ReviewedFutureWeekProgressionStatus.CANDIDATE_FOR_ATHLETE_REVIEW,
            null,candidate(),null,3,"slot0","Verified proposal"),
        1,2,17,5,3,6)

    private fun selection():NativeFutureWeekDraftSelection =NativeFutureWeekDraftSelection(
        "owner1","cycle1",3,0,"slot0",CoachReadiness.NORMAL,
        false,BigDecimal("5"),CustomRepForecastPolicy.TARGET_RIR_V2)

    @Test fun digestCannotBeReusedAcrossOwnerCycleWeekOrDay() {
        val s=selection()
        val verified=reviewed()
        val digest=NativeKotlinFutureWeekDraftAcceptance.fingerprint(s,verified)
        for (other in listOf(
            s.copy(ownerKey="owner2"),
            s.copy(cycleId="cycle2"),
            s.copy(absoluteWeek=4),
            s.copy(weekday=1)
        )) {
            assertNotEquals(digest,
                NativeKotlinFutureWeekDraftAcceptance.fingerprint(other,verified))
        }
    }

    @Test fun fingerprintIsDeterministicForSameApprovedInput() {
        val s=selection()
        val verified=reviewed()
        val first=NativeKotlinFutureWeekDraftAcceptance.fingerprint(s,verified)
        assertEquals(first,NativeKotlinFutureWeekDraftAcceptance.fingerprint(
            s.copy(maximumAuthorizedIncrease=BigDecimal("5.000")),verified))
        assertTrue(first.matches(Regex("[a-f0-9]{64}")))
    }
}
