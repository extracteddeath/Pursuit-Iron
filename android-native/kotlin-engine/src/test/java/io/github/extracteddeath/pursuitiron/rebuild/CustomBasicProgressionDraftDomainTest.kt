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

    /** Test-only authentic future-number gate proofs for 5-lb selectors. */
    private fun withProof(
        reviewed:CustomBasicHistoryResult.Reviewed,
        originals:List<CustomAuthoredSet>
    ):CustomBasicHistoryResult.Reviewed {
        val working=originals.filter{it.type==DraftSetType.WORKING}
        val unit=LoadUnit.LB
        val basis=(working.first().load as DraftSetLoad.Verified).basis
        val stock=VerifiedLoadStock.Stack(unit,basis,
            BigDecimal("5"),BigDecimal("5"),BigDecimal("200"))
        val approved=reviewed.candidate.increasedOrdinals.associateWith { index ->
            val issued=working[index].load as DraftSetLoad.Verified
            val source=if(basis==LoadBasis.ASSISTANCE_COUNTERWEIGHT)
                KotlinFutureLoadSource.EARNED_ASSISTANCE_DECREMENT
            else KotlinFutureLoadSource.EARNED_NEXT_INCREMENT
            (KotlinFutureLoadNumberAuthorityDomain.review(
                issued.value,reviewed.candidate.proposedWorkingLoads[index],
                unit,basis,stock,BigDecimal("5"),source)
                as KotlinFutureLoadReview.Accepted).fact
        }
        val scope=BasicProgressionScope(
            "athlete","program","gym",reviewed.blockId,
            reviewed.blockId+":d"+reviewed.weekday,"exercise",
            reviewed.machineInstanceId,unit,basis)
        return reviewed.copy(candidate=reviewed.candidate.copy(
            sourceScope=scope,approvedFutureLoadFacts=approved))
    }

    @Test fun uniformProgressionNeverIssuesDifferentNextLoadsForIdenticalSets() {
        val candidate=weighted(BasicProgressionResult.Candidate(
            BasicProgressionStrategy.STRICT_DOUBLE_PROGRESSION,
            listOf(BigDecimal(105),BigDecimal(110)),setOf(0,1),
            BasicProgressionDecision.EARNED_INCREMENT,"previous"))
        val source=listOf(set(100),set(100))
        assertNull(CustomBasicProgressionDraftDomain.reviewedSets("slot",source,candidate))
        assertNull(CustomBasicProgressionDraftDomain.forWeekEdit("slot",source,candidate))
    }

    @Test fun equivalentDecimalHoldIsVisibleButCannotCreateAnEdit() {
        val candidate=weighted(BasicProgressionResult.Candidate(
            BasicProgressionStrategy.PER_SET_DYNAMIC_PROGRESSION,
            listOf(BigDecimal("100.00")),emptySet(),BasicProgressionDecision.BUILD_REPS,
            "previous",dynamicSetDecisions=listOf(BasicProgressionDecision.BUILD_REPS)))
        val source=listOf(set(100))
        assertEquals(source,CustomBasicProgressionDraftDomain.reviewedSets("slot",source,candidate))
        assertNull(CustomBasicProgressionDraftDomain.forWeekEdit("slot",source,candidate))
        val falseIncrease=weighted(candidate.candidate.copy(
            dynamicSetDecisions=listOf(BasicProgressionDecision.EARNED_INCREMENT)))
        assertNull(CustomBasicProgressionDraftDomain.reviewedSets("slot",source,falseIncrease))
    }

    @Test fun aPerSetEarnedIncreasePreservesWarmupsAndEveryOtherField() {
        val warmup=set(40).copy(type=DraftSetType.WARMUP)
        val original=listOf(warmup,set(100),set(105))
        val proposed=weighted(BasicProgressionResult.Candidate(
            BasicProgressionStrategy.PER_SET_DYNAMIC_PROGRESSION,
            listOf(BigDecimal(105),BigDecimal(105)),setOf(0),
            BasicProgressionDecision.EARNED_INCREMENT,"previous",
            dynamicSetDecisions=listOf(
                BasicProgressionDecision.EARNED_INCREMENT,
                BasicProgressionDecision.BUILD_REPS)))
        val next=checkNotNull(CustomBasicProgressionDraftDomain.forWeekEdit(
            "slot",original,withProof(proposed,original)))
        assertEquals(warmup,next[0])
        assertEquals(pounds(105),next[1].load)
        assertEquals(original[2],next[2])
        assertEquals(original[1].targetRir,next[1].targetRir)
        assertEquals(original[1].restSeconds,next[1].restSeconds)
        assertEquals(original[1].reps,next[1].reps)
        assertEquals(pounds(100),original[1].load)
    }

    @Test fun dynamicEditRequiresCompletePerSetEvaluatorDecisionVector() {
        val original=listOf(set(100),set(105),set(110))
        val authenticated=BasicProgressionResult.Candidate(
            BasicProgressionStrategy.PER_SET_DYNAMIC_PROGRESSION,
            listOf(BigDecimal(105),BigDecimal(105),BigDecimal(115)),
            setOf(0,2),BasicProgressionDecision.EARNED_INCREMENT,"previous",
            dynamicSetDecisions=listOf(
                BasicProgressionDecision.EARNED_INCREMENT,
                BasicProgressionDecision.BUILD_REPS,
                BasicProgressionDecision.EARNED_INCREMENT))
        val reviewed=weighted(authenticated)
        val edit=checkNotNull(CustomBasicProgressionDraftDomain.forWeekEdit(
            "slot",original,withProof(reviewed,original)))
        assertEquals(listOf(pounds(105),pounds(105),pounds(115)),
            edit.map {it.load})
        for(spoofed in listOf(
            authenticated.copy(dynamicSetDecisions=emptyList()),
            authenticated.copy(dynamicSetDecisions=listOf(
                BasicProgressionDecision.EARNED_INCREMENT)),
            authenticated.copy(dynamicSetDecisions=listOf(
                BasicProgressionDecision.EARNED_INCREMENT,
                BasicProgressionDecision.EARNED_INCREMENT,
                BasicProgressionDecision.EARNED_INCREMENT)),
            authenticated.copy(dynamicSetDecisions=listOf(
                BasicProgressionDecision.BUILD_REPS,
                BasicProgressionDecision.BUILD_REPS,
                BasicProgressionDecision.EARNED_INCREMENT)))) {
            assertNull(CustomBasicProgressionDraftDomain.reviewedSets(
                "slot",original,weighted(spoofed)))
        }
        val improperUniform=authenticated.copy(
            strategy=BasicProgressionStrategy.STRICT_DOUBLE_PROGRESSION,
            proposedWorkingLoads=listOf(BigDecimal(115),BigDecimal(115),BigDecimal(115)),
            increasedOrdinals=setOf(0,1,2))
        assertNull(CustomBasicProgressionDraftDomain.reviewedSets(
            "slot",listOf(set(100),set(100),set(100)),weighted(improperUniform)))
    }

    @Test fun weightedAndBodyweightEditMustMatchTheirVerifiedHistorySource() {
        val source=listOf(set(100),set(100))
        val earned=BasicProgressionResult.Candidate(
            BasicProgressionStrategy.STRICT_DOUBLE_PROGRESSION,
            listOf(BigDecimal(105),BigDecimal(105)),setOf(0,1),
            BasicProgressionDecision.EARNED_INCREMENT,"previous")
        assertNotNull(CustomBasicProgressionDraftDomain.forWeekEdit(
            "slot",source,withProof(weighted(earned),source)))
        for(stale in listOf(
            weighted(earned.copy(evidenceWorkoutId=null)),
            weighted(earned.copy(evidenceWorkoutId="older-success")),
            weighted(earned).copy(historyWorkoutId="other-workout"),
            weighted(earned).copy(historyWorkoutId=null),
            weighted(earned).copy(completionOrder=0),
            weighted(earned).copy(completionOrder=null))) {
            assertNull(CustomBasicProgressionDraftDomain.forWeekEdit(
                "slot",source,stale))
        }

        val body=set(100,8).copy(load=DraftSetLoad.ExplicitBodyweight)
        val bodyCandidate=BasicUnloadedProgressionResult.Candidate(
            listOf(9),setOf(0),"previous")
        val authentic=CustomBasicHistoryResult.UnloadedReviewed(
            "previous",1,bodyCandidate,"block",0,"slot")
        assertNotNull(CustomBasicProgressionDraftDomain.forWeekEdit(
            "slot",listOf(body),authentic))
        assertNull(CustomBasicProgressionDraftDomain.reviewedSets(
            "slot",listOf(body),authentic.copy(historyWorkoutId="other")))
        assertNull(CustomBasicProgressionDraftDomain.reviewedSets(
            "slot",listOf(body),authentic.copy(completionOrder=0)))
        assertNull(CustomBasicProgressionDraftDomain.reviewedSets(
            "slot",listOf(body),authentic.copy(
                candidate=bodyCandidate.copy(evidenceWorkoutId="older"))))
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

    @Test fun ceilingLabelsRequireEveryIssuedTargetToBeAtTheCap() {
        val body = set(100, 8).copy(load = DraftSetLoad.ExplicitBodyweight)
        val cap = CustomBasicHistoryResult.UnloadedReviewed("previous", 1,
            BasicUnloadedProgressionResult.Candidate(listOf(9), setOf(0), "previous", atRepCeiling = true),
            "block", 0, "slot")
        assertNull(CustomBasicProgressionDraftDomain.reviewedSets("slot", listOf(body), cap))
        val weightedCap = weighted(BasicProgressionResult.Candidate(
            BasicProgressionStrategy.REP_TARGET_PROGRESSION, listOf(BigDecimal(100)), emptySet(),
            BasicProgressionDecision.REP_CEILING_REACHED, "previous", proposedRepTargets = listOf(8)))
        assertNull(CustomBasicProgressionDraftDomain.reviewedSets("slot", listOf(set(100, 8)), weightedCap))
    }

    @Test fun assistedProgressionCopiesTheLowerCounterweightNotAHeavierLoad() {
        val assisted = DraftSetLoad.Verified(
            LoadUnit.LB,LoadBasis.ASSISTANCE_COUNTERWEIGHT,BigDecimal(70))
        val original=listOf(set(70).copy(load=assisted),set(70).copy(load=assisted))
        val candidate=weighted(BasicProgressionResult.Candidate(
            BasicProgressionStrategy.ASSISTANCE_DOUBLE_PROGRESSION,
            listOf(BigDecimal(65),BigDecimal(65)),setOf(0,1),
            BasicProgressionDecision.EARNED_INCREMENT,"previous"))
        val next=checkNotNull(CustomBasicProgressionDraftDomain.forWeekEdit(
            "slot",original,withProof(candidate,original)))
        assertEquals(BigDecimal(65),(next[0].load as DraftSetLoad.Verified).value)
        assertEquals(BigDecimal(65),(next[1].load as DraftSetLoad.Verified).value)
        assertEquals(original.map { it.reps },next.map { it.reps })
    }

    @Test fun repLadderCanExplicitlyResetTheEarnedSetToItsFloor() {
        val original=listOf(set(100,10),set(100,10))
        val candidate=weighted(BasicProgressionResult.Candidate(
            BasicProgressionStrategy.REP_LADDER_PROGRESSION,
            listOf(BigDecimal(105),BigDecimal(105)),setOf(0,1),
            BasicProgressionDecision.EARNED_INCREMENT,"previous",
            proposedRepTargets=listOf(6,6),changedRepOrdinals=setOf(0,1)))
        val next=checkNotNull(CustomBasicProgressionDraftDomain.forWeekEdit(
            "slot",original,candidate))
        assertEquals(pounds(105),next[0].load)
        assertEquals(6,next[0].exactRepTarget)
        assertEquals(pounds(105),next[1].load)
        assertEquals(6,next[1].exactRepTarget)
    }

    @Test fun rejectsUnmarkedNumericChangesAndStaleSlotIdentity() {
        val original=listOf(set(100),set(100))
        val wrongMarker=weighted(BasicProgressionResult.Candidate(
            BasicProgressionStrategy.STRICT_DOUBLE_PROGRESSION,
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
            BasicProgressionStrategy.STRICT_DOUBLE_PROGRESSION,
            listOf(BigDecimal(100),BigDecimal(100)),emptySet(),
            BasicProgressionDecision.BUILD_REPS,"previous"))
        assertNull(CustomBasicProgressionDraftDomain.forWeekEdit(
            "slot",original,noOp))
        val mismatch=weighted(noOp.candidate.copy(
            proposedWorkingLoads=listOf(BigDecimal(105)),increasedOrdinals=setOf(0)))
        assertNull(CustomBasicProgressionDraftDomain.forWeekEdit(
            "slot",original,mismatch))
    }

    @Test fun markedLowerFreeWeightIsNotAProgressionEvenWhenOrdinalsMatch() {
        val original=listOf(set(100),set(100))
        for (strategy in listOf(
            BasicProgressionStrategy.STRICT_DOUBLE_PROGRESSION,
            BasicProgressionStrategy.PER_SET_DYNAMIC_PROGRESSION,
            BasicProgressionStrategy.LINEAR_LOAD_PROGRESSION)) {
            val candidate=weighted(BasicProgressionResult.Candidate(
                strategy,listOf(BigDecimal(95),BigDecimal(95)),setOf(0,1),
                BasicProgressionDecision.EARNED_INCREMENT,"previous"))
            assertNull(strategy.toString(),CustomBasicProgressionDraftDomain.forWeekEdit(
                "slot",original,candidate))
        }
        val zero=weighted(BasicProgressionResult.Candidate(
            BasicProgressionStrategy.PER_SET_DYNAMIC_PROGRESSION,
            listOf(BigDecimal.ZERO,BigDecimal(100)),setOf(0),
            BasicProgressionDecision.EARNED_INCREMENT,"previous"))
        assertNull(CustomBasicProgressionDraftDomain.forWeekEdit("slot",original,zero))
    }

    @Test fun assistedCounterweightsMustGetSmallerNotHeavier() {
        val assisted=DraftSetLoad.Verified(
            LoadUnit.LB,LoadBasis.ASSISTANCE_COUNTERWEIGHT,BigDecimal(70))
        val original=listOf(set(70).copy(load=assisted),set(70).copy(load=assisted))
        val heavier=weighted(BasicProgressionResult.Candidate(
            BasicProgressionStrategy.ASSISTANCE_DOUBLE_PROGRESSION,
            listOf(BigDecimal(75),BigDecimal(75)),setOf(0,1),
            BasicProgressionDecision.EARNED_INCREMENT,"previous"))
        assertNull(CustomBasicProgressionDraftDomain.forWeekEdit(
            "slot",original,heavier))
        val nonAssisted=weighted(heavier.candidate.copy(
            proposedWorkingLoads=listOf(BigDecimal(65),BigDecimal(65))))
        assertNull(CustomBasicProgressionDraftDomain.forWeekEdit(
            "slot",listOf(set(70),set(70)),nonAssisted))
    }

    @Test fun uniformProgressionsCannotAdvanceOneRowAndAdviceCannotClaimAWin() {
        val original=listOf(set(100),set(100))
        for(strategy in listOf(
            BasicProgressionStrategy.STRICT_DOUBLE_PROGRESSION,
            BasicProgressionStrategy.LINEAR_LOAD_PROGRESSION)) {
            val partial=weighted(BasicProgressionResult.Candidate(
                strategy,listOf(BigDecimal(105),BigDecimal(100)),setOf(0),
                BasicProgressionDecision.EARNED_INCREMENT,"previous"))
            assertNull(CustomBasicProgressionDraftDomain.forWeekEdit(
                "slot",original,partial))
        }
        val notEarned=weighted(BasicProgressionResult.Candidate(
            BasicProgressionStrategy.PER_SET_DYNAMIC_PROGRESSION,
            listOf(BigDecimal(105),BigDecimal(100)),setOf(0),
            BasicProgressionDecision.BUILD_REPS,"previous"))
        assertNull(CustomBasicProgressionDraftDomain.forWeekEdit(
            "slot",original,notEarned))
    }

    @Test fun repOnlyAndBodyweightCannotSkipOrReverseAnEarnedRung() {
        val original=listOf(set(100,8),set(100,9))
        for (next in listOf(7,10,11)) {
            val forged=weighted(BasicProgressionResult.Candidate(
                BasicProgressionStrategy.REP_TARGET_PROGRESSION,
                listOf(BigDecimal(100),BigDecimal(100)),emptySet(),
                BasicProgressionDecision.EARNED_REP_TARGET,"previous",
                proposedRepTargets=listOf(next,9),changedRepOrdinals=setOf(0)))
            assertNull(CustomBasicProgressionDraftDomain.forWeekEdit(
                "slot",original,forged))
        }
        val body=CustomAuthoredSet(
            DraftSetType.WORKING,DraftSetLoad.ExplicitBodyweight,
            RepBand(5,12),2,40,80,exactRepTarget=8)
        for (next in listOf(7,10)) {
            val forged=CustomBasicHistoryResult.UnloadedReviewed(
                "previous",1,BasicUnloadedProgressionResult.Candidate(
                    listOf(next),setOf(0),"previous"),"block",0,"slot")
            assertNull(CustomBasicProgressionDraftDomain.forWeekEdit(
                "slot",listOf(body),forged))
        }
    }

    @Test fun repLadderResetsToFloorOnlyAlongsideAtomicEarnedLoad() {
        val original=listOf(set(100,10),set(100,10))
        val falseReset=weighted(BasicProgressionResult.Candidate(
            BasicProgressionStrategy.REP_LADDER_PROGRESSION,
            listOf(BigDecimal(100),BigDecimal(100)),emptySet(),
            BasicProgressionDecision.EARNED_REP_TARGET,"previous",
            proposedRepTargets=listOf(6,6),changedRepOrdinals=setOf(0,1)))
        assertNull(CustomBasicProgressionDraftDomain.forWeekEdit(
            "slot",original,falseReset))
        val partialLoad=weighted(BasicProgressionResult.Candidate(
            BasicProgressionStrategy.REP_LADDER_PROGRESSION,
            listOf(BigDecimal(105),BigDecimal(100)),setOf(0),
            BasicProgressionDecision.EARNED_INCREMENT,"previous",
            proposedRepTargets=listOf(6,6),changedRepOrdinals=setOf(0,1)))
        assertNull(CustomBasicProgressionDraftDomain.forWeekEdit(
            "slot",original,partialLoad))
        val wrongReset=weighted(partialLoad.candidate.copy(
            proposedWorkingLoads=listOf(BigDecimal(105),BigDecimal(105)),
            increasedOrdinals=setOf(0,1),proposedRepTargets=listOf(7,7)))
        assertNull(CustomBasicProgressionDraftDomain.forWeekEdit(
            "slot",original,wrongReset))
        val ladder=listOf(set(100,10),set(100,8))
        val actualRung=weighted(BasicProgressionResult.Candidate(
            BasicProgressionStrategy.REP_LADDER_PROGRESSION,
            listOf(BigDecimal(100),BigDecimal(100)),emptySet(),
            BasicProgressionDecision.EARNED_REP_TARGET,"previous",
            proposedRepTargets=listOf(10,9),changedRepOrdinals=setOf(1)))
        assertEquals(listOf(10,9),checkNotNull(
            CustomBasicProgressionDraftDomain.forWeekEdit(
                "slot",ladder,actualRung)).map{it.exactRepTarget})
    }

}
