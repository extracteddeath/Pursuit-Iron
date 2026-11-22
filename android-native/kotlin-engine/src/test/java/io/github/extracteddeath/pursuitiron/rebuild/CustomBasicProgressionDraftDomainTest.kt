package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class CustomBasicProgressionDraftDomainTest {
    private fun pounds(value: Int) = DraftSetLoad.Verified(
        LoadUnit.LB, LoadBasis.MACHINE_STACK, BigDecimal(value))
    private fun set(value: Int, exact: Int? = null) = CustomAuthoredSet(
        DraftSetType.WORKING,pounds(value),RepBand(6,10),2,45,90,
        exactRepTarget=exact)
    private fun weighted(candidate: BasicProgressionResult.Candidate) =
        CustomBasicHistoryResult.Reviewed("previous",1,candidate,"block",0,"slot",null)

    @Test fun aPerSetEarnedIncreasePreservesWarmupsAndEveryOtherField() {
        val warmup=set(40).copy(type=DraftSetType.WARMUP)
        val original=listOf(warmup,set(100),set(105))
        val proposed=weighted(BasicProgressionResult.Candidate(
            BasicProgressionStrategy.PER_SET_DYNAMIC_PROGRESSION,
            listOf(BigDecimal(105),BigDecimal(105)),setOf(0),
            BasicProgressionDecision.EARNED_INCREMENT,"previous"))
        val next=checkNotNull(CustomBasicProgressionDraftDomain.forWeekEdit(
            "slot",original,proposed))
        assertEquals(warmup,next[0])
        assertEquals(pounds(105),next[1].load)
        assertEquals(original[2],next[2])
        assertEquals(original[1].targetRir,next[1].targetRir)
        assertEquals(original[1].restSeconds,next[1].restSeconds)
        assertEquals(original[1].reps,next[1].reps)
        assertEquals(pounds(100),original[1].load)
    }

    @Test fun exactRepAdvancementDoesNotInventLoadOrModifyOtherSets() {
        val original=listOf(set(100,8),set(105,9))
        val proposed=weighted(BasicProgressionResult.Candidate(
            BasicProgressionStrategy.REP_TARGET_PROGRESSION,
            listOf(BigDecimal(100),BigDecimal(105)),emptySet(),
            BasicProgressionDecision.EARNED_REP_TARGET,"previous",
            proposedRepTargets=listOf(9,9),changedRepOrdinals=setOf(0)))
        val next=checkNotNull(CustomBasicProgressionDraftDomain.forWeekEdit(
            "slot",original,proposed))
        assertEquals(9,next[0].exactRepTarget)
        assertEquals(original[1],next[1])
        assertEquals(original.map { it.load },next.map { it.load })
    }

    @Test fun bodyweightCopiesOnlyReviewedExactRepTargets() {
        val body=CustomAuthoredSet(
            DraftSetType.WORKING,DraftSetLoad.ExplicitBodyweight,
            RepBand(5,12),2,40,80,exactRepTarget=8)
        val original=listOf(body,body.copy(exactRepTarget=9))
        val proposed=CustomBasicHistoryResult.UnloadedReviewed(
            "previous",1,BasicUnloadedProgressionResult.Candidate(
                listOf(9,9),setOf(0),"previous"),"block",0,"slot")
        val next=checkNotNull(CustomBasicProgressionDraftDomain.forWeekEdit(
            "slot",original,proposed))
        assertEquals(listOf(9,9),next.map { it.exactRepTarget })
        assertEquals(original.map { it.load },next.map { it.load })
    }

    @Test fun rejectsUnmarkedNumericChangesAndStaleSlotIdentity() {
        val original=listOf(set(100),set(100))
        val wrongMarker=weighted(BasicProgressionResult.Candidate(
            BasicProgressionStrategy.DOUBLE_PROGRESSION,
            listOf(BigDecimal(105),BigDecimal(100)),emptySet(),
            BasicProgressionDecision.EARNED_INCREMENT,"previous"))
        assertNull(CustomBasicProgressionDraftDomain.forWeekEdit(
            "slot",original,wrongMarker))
        assertNull(CustomBasicProgressionDraftDomain.forWeekEdit(
            "another-slot",original,wrongMarker))
        val oversized=weighted(BasicProgressionResult.Candidate(
            BasicProgressionStrategy.REP_TARGET_PROGRESSION,
            listOf(BigDecimal(100),BigDecimal(100)),emptySet(),
            BasicProgressionDecision.EARNED_REP_TARGET,"previous",
            proposedRepTargets=listOf(11,8),changedRepOrdinals=setOf(0)))
        assertNull(CustomBasicProgressionDraftDomain.forWeekEdit(
            "slot",original,oversized))
    }

    @Test fun noOpAndChangedSetCountCannotBecomeAWeekEdit() {
        val original=listOf(set(100),set(100))
        val noOp=weighted(BasicProgressionResult.Candidate(
            BasicProgressionStrategy.DOUBLE_PROGRESSION,
            listOf(BigDecimal(100),BigDecimal(100)),emptySet(),
            BasicProgressionDecision.BUILD_REPS,"previous"))
        assertNull(CustomBasicProgressionDraftDomain.forWeekEdit(
            "slot",original,noOp))
        val mismatch=weighted(noOp.candidate.copy(
            proposedWorkingLoads=listOf(BigDecimal(105)),increasedOrdinals=setOf(0)))
        assertNull(CustomBasicProgressionDraftDomain.forWeekEdit(
            "slot",original,mismatch))
    }
}
