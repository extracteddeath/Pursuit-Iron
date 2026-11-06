package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
import org.junit.Assert.*
import org.junit.Test

/** Boundary arithmetic never trusts a screen's "next phase" label. */
class NativeFutureBlockBoundaryDomainTest {
    private fun cycle()=CustomTrainingCycle(
        "cycle-1",2,"athlete-1",AthleteTrainingGoal.HYPERTROPHY,
        "Custom multiblock",listOf(
            CustomTrainingBlock("base","Base",TrainingPhase.HYPERTROPHY,
                CustomBlockPurpose.TRAINING,2,
                CustomSplitIdentity.Named(ComposerSplit.FULL_BODY),
                listOf(0,2,4).map{day->CustomTrainingDay(
                    day,"Base $day",ComposerSessionPurpose.HYPERTROPHY,
                    emptyList())}),
            CustomTrainingBlock("build","Build",TrainingPhase.STRENGTH,
                CustomBlockPurpose.TRAINING,3,
                CustomSplitIdentity.Named(ComposerSplit.UPPER_LOWER),
                listOf(0,1,2,4).map{day->CustomTrainingDay(
                    day,"Build $day",ComposerSessionPurpose.STRENGTH_SKILL,
                    emptyList())})
        ))
    private fun done(block:String,week:Int,day:Int,cycleId:String="cycle-1")=
        NativeVerifiedCustomBlockCompletion(cycleId,block,week,day)
    private fun earlier()=(1..2).flatMap{week->
        listOf(0,2,4).map{day->done("base",week,day)}
    }
    @Test fun completePriorBlockAllowsReviewButNeverAuthorizesActivation() {
        val checked=NativeFutureBlockBoundaryDomain.check(
            cycle(),"build",earlier(),7)
        assertEquals(NativeFutureActivationReview.BoundaryReviewed(
            "cycle-1","build",3,2,6,7),checked)
        val reviewed=checked as NativeFutureActivationReview.BoundaryReviewed
        assertFalse(reviewed.mayActivate)
        assertFalse(reviewed.mayIssueWorkout)
        assertFalse(reviewed.mayChangeSchedule)
    }
    @Test fun firstBlockIsNeverProspectiveActivationTarget() {
        assertEquals(NativeFutureActivationReview.Rejected(
            NativeFutureActivationRefusal.NOT_A_FUTURE_BLOCK),
            NativeFutureBlockBoundaryDomain.check(cycle(),"base",earlier(),7))
        assertEquals(NativeFutureActivationReview.Rejected(
            NativeFutureActivationRefusal.NOT_A_FUTURE_BLOCK),
            NativeFutureBlockBoundaryDomain.check(cycle(),"unknown",earlier(),7))
    }
    @Test fun incompletePriorSessionsDoNotClaimTrainingBoundary() {
        assertEquals(NativeFutureActivationReview.Rejected(
            NativeFutureActivationRefusal.INCOMPLETE_HISTORY),
            NativeFutureBlockBoundaryDomain.check(
                cycle(),"build",earlier().dropLast(1),7))
        assertEquals(NativeFutureActivationReview.Rejected(
            NativeFutureActivationRefusal.INCOMPLETE_HISTORY),
            NativeFutureBlockBoundaryDomain.check(
                cycle(),"build",emptyList(),7))
    }
    @Test fun anyAlreadyCompletedFutureWorkoutBlocksRetroactiveActivation() {
        assertEquals(NativeFutureActivationReview.Rejected(
            NativeFutureActivationRefusal.TARGET_BLOCK_ALREADY_STARTED),
            NativeFutureBlockBoundaryDomain.check(
                cycle(),"build",earlier()+done("build",3,0),7))
        assertEquals(NativeFutureActivationReview.Rejected(
            NativeFutureActivationRefusal.TARGET_BLOCK_ALREADY_STARTED),
            NativeFutureBlockBoundaryDomain.check(
                cycle(),"build",earlier()+done("build",5,4),7))
    }
    @Test fun mismatchedBlockOrDuplicateCompletionCannotFillGap() {
        val wrong=earlier().dropLast(1)+done("build",2,4)
        assertEquals(NativeFutureActivationReview.Rejected(
            NativeFutureActivationRefusal.CONFLICTING_HISTORY),
            NativeFutureBlockBoundaryDomain.check(
                cycle(),"build",wrong,7))
        assertEquals(NativeFutureActivationReview.Rejected(
            NativeFutureActivationRefusal.CONFLICTING_HISTORY),
            NativeFutureBlockBoundaryDomain.check(
                cycle(),"build",earlier()+earlier().first(),7))
    }
    @Test fun unrelatedCycleCannotAdvanceTheSelectedCustomCycle() {
        val unrelated=earlier().map{it.copy(cycleId="another-cycle")}
        assertEquals(NativeFutureActivationReview.Rejected(
            NativeFutureActivationRefusal.INCOMPLETE_HISTORY),
            NativeFutureBlockBoundaryDomain.check(
                cycle(),"build",unrelated,7))
        assertEquals(NativeFutureActivationReview.BoundaryReviewed(
            "cycle-1","build",3,2,6,7),
            NativeFutureBlockBoundaryDomain.check(
                cycle(),"build",earlier()+unrelated,7))
    }

    @Test fun realFreeformSplitRetainsAthleteAuthoredWeekdays() {
        assertTrue(NativeFutureBlockBoundaryDomain.scheduleMatches(
            CustomSplitIdentity.Freeform("Chest and Arms"),listOf(0,1,3,5),
            ComposerSplit.UPPER_LOWER,setOf(0,1,3,5)))
        assertFalse(NativeFutureBlockBoundaryDomain.scheduleMatches(
            CustomSplitIdentity.Freeform("Chest and Arms"),listOf(0,1,3,5),
            ComposerSplit.UPPER_LOWER,setOf(0,1,2,5)))
        assertFalse(NativeFutureBlockBoundaryDomain.scheduleMatches(
            CustomSplitIdentity.Freeform("Chest and Arms"),listOf(0,1,3,3),
            ComposerSplit.UPPER_LOWER,setOf(0,1,3)))
        assertFalse(NativeFutureBlockBoundaryDomain.scheduleMatches(
            CustomSplitIdentity.Named(ComposerSplit.FULL_BODY),listOf(0,1,3,5),
            ComposerSplit.UPPER_LOWER,setOf(0,1,3,5)))
    }

}
