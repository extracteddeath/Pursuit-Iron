package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class BasicProgressionExtendedCoverageTest {
    private fun n(v:String)=BigDecimal(v)
    private val stock=VerifiedLoadStock.Stack(
        LoadUnit.LB,LoadBasis.MACHINE_STACK,n("90"),n("5"),n("120"))
    private val assist=VerifiedLoadStock.Stack(
        LoadUnit.LB,LoadBasis.ASSISTANCE_COUNTERWEIGHT,n("10"),n("10"),n("80"))
    private val scope=BasicProgressionScope(
        "athlete","program","gym","block","upper","press",
        "machine-A",LoadUnit.LB,LoadBasis.MACHINE_STACK)
    private val band=RepBand(6,10)
    private fun request(
        mode:BasicProgressionStrategy,
        reps:List<Int> = listOf(10,10,10),
        weight:String="100",
        scope:BasicProgressionScope=this.scope,
        stock:VerifiedLoadStock?=this.stock
    ):BasicProgressionRequest {
        val loads=listOf(weight,weight,weight)
        val old=ProgressionWorkoutEvidence(
            "last","press",LoadUnit.LB,band,3,
            reps.mapIndexed{i,r->WorkingSetFact(i,SetOutcome.COMPLETED,
                n(weight),r,0,n(weight),2)},"upper")
        return BasicProgressionRequest(mode,"next",scope,scope,old,band,
            loads.map(::n),stock,n("10"))
    }

    @Test fun linearProgressionRequiresEverySetToMeetRepFloor() {
        val mode=BasicProgressionStrategy.LINEAR_LOAD_PROGRESSION
        val earned=BasicProgressionPolicyDomain.review(request(mode,listOf(6,6,6)))
            as BasicProgressionResult.Candidate
        assertEquals(listOf(n("105"),n("105"),n("105")),earned.proposedWorkingLoads)
        assertEquals(setOf(0,1,2),earned.increasedOrdinals)
        val stalled=BasicProgressionPolicyDomain.review(request(mode,listOf(6,5,10)))
            as BasicProgressionResult.Candidate
        assertEquals(listOf(n("100"),n("100"),n("100")),stalled.proposedWorkingLoads)
        assertEquals(BasicProgressionDecision.BUILD_REPS,stalled.decision)
    }

    @Test fun authenticatedRepOnlyTargetsAdvanceIndependentlyWithoutWeightChange() {
        val mode=BasicProgressionStrategy.REP_TARGET_PROGRESSION
        val eligible=request(mode,reps=listOf(8,7,10)).copy(
            issuedRepTargets=listOf(8,8,10),verifiedStock=null)
        val out=BasicProgressionPolicyDomain.review(eligible)
            as BasicProgressionResult.Candidate
        assertEquals(listOf(9,8,10),out.proposedRepTargets)
        assertEquals(setOf(0),out.changedRepOrdinals)
        assertEquals(emptySet<Int>(),out.increasedOrdinals)
        assertEquals(listOf(n("100"),n("100"),n("100")),out.proposedWorkingLoads)
        assertFalse(out.maySaveFutureProgram)
        assertEquals(BasicProgressionResult.Refused(
            BasicProgressionRefusal.REP_TARGETS_NOT_VERIFIED),
            BasicProgressionPolicyDomain.review(eligible.copy(issuedRepTargets=null)))
        assertEquals(BasicProgressionResult.Refused(
            BasicProgressionRefusal.REP_TARGETS_NOT_VERIFIED),
            BasicProgressionPolicyDomain.review(eligible.copy(
                issuedRepTargets=listOf(11,8,10))))
    }

    @Test fun assistanceUsesExactNextLowerCounterweightOnlyAfterAllCeilingSets() {
        val mode=BasicProgressionStrategy.ASSISTANCE_DOUBLE_PROGRESSION
        val assistant=scope.copy(basis=LoadBasis.ASSISTANCE_COUNTERWEIGHT)
        val earned=BasicProgressionPolicyDomain.review(
            request(mode,listOf(10,10,10),"40",assistant,assist))
            as BasicProgressionResult.Candidate
        assertEquals(listOf(n("30"),n("30"),n("30")),earned.proposedWorkingLoads)
        assertEquals(setOf(0,1,2),earned.increasedOrdinals)
        val hold=BasicProgressionPolicyDomain.review(
            request(mode,listOf(10,9,10),"40",assistant,assist))
            as BasicProgressionResult.Candidate
        assertEquals(listOf(n("40"),n("40"),n("40")),hold.proposedWorkingLoads)
        val minimum=BasicProgressionPolicyDomain.review(
            request(mode,listOf(10,10,10),"10",assistant,assist))
            as BasicProgressionResult.Candidate
        assertEquals(BasicProgressionDecision.MAXIMUM_LOAD_REACHED,minimum.decision)
        assertEquals(listOf(n("10"),n("10"),n("10")),minimum.proposedWorkingLoads)
        assertEquals(BasicProgressionResult.Refused(
            BasicProgressionRefusal.UNSUPPORTED_ASSISTANCE_DIRECTION),
            BasicProgressionPolicyDomain.review(request(
                BasicProgressionStrategy.LINEAR_LOAD_PROGRESSION,
                listOf(10,10,10),"40",assistant,assist)))
    }

    @Test fun weightedAndUnweightedUseIdenticalEarnedOneRepRule() {
        assertEquals(listOf(9,8,10),BasicRepTargetStepDomain.advance(
            listOf(8,8,10),listOf(8,7,10),band))
        val scope=BasicUnloadedProgressionScope("athlete","gym","program",
            "block","upper","pushup")
        fun workout(reps:List<Int>)=ProgressionWorkoutEvidence(
            "prior","pushup",LoadUnit.LB,band,3,
            reps.mapIndexed{i,r->WorkingSetFact(i,SetOutcome.COMPLETED,
                null,r,null,null,2)},"upper")
        val req=BasicUnloadedProgressionRequest("next",scope,scope,
            workout(listOf(8,7,10)),List(3){DraftSetLoad.ExplicitBodyweight},
            listOf(8,8,10),band)
        val r=BasicBodyweightRepProgressionDomain.review(req)
            as BasicUnloadedProgressionResult.Candidate
        assertEquals(listOf(9,8,10),r.proposedExactReps)
        assertEquals(setOf(0),r.advancedOrdinals)
        assertFalse(r.mayModifyHistory)
        assertFalse(r.mayChangeIssuedWorkout)
        assertEquals(BasicUnloadedProgressionResult.Rejected(
            BasicUnloadedRefusal.UNVERIFIED_BODYWEIGHT_SOURCE),
            BasicBodyweightRepProgressionDomain.review(req.copy(
                issuedWorkingSets=listOf(DraftSetLoad.Verified(
                    LoadUnit.LB,LoadBasis.MACHINE_STACK,n("10"))))))
        assertEquals(BasicUnloadedProgressionResult.Rejected(
            BasicUnloadedRefusal.NO_COMPARABLE_SESSION),
            BasicBodyweightRepProgressionDomain.review(req.copy(
                lastScope=scope.copy(gymId="other"))))
    }

    @Test fun bodyweightCeilingRequiresLoggedRepsOnEveryWorkingSet() {
        val scope = BasicUnloadedProgressionScope("athlete", "gym", "program", "block", "upper", "pushup")
        fun candidate(targets: List<Int>, reps: List<Int>): BasicUnloadedProgressionResult.Candidate {
            val facts = reps.mapIndexed { i, actual ->
                WorkingSetFact(i, SetOutcome.COMPLETED, null, actual, null, null, 2)
            }
            val last = ProgressionWorkoutEvidence("prior", "pushup", LoadUnit.LB, band, 3, facts, "upper")
            val request = BasicUnloadedProgressionRequest("next", scope, scope, last,
                List(3) { DraftSetLoad.ExplicitBodyweight }, targets, band)
            return BasicBodyweightRepProgressionDomain.review(request) as BasicUnloadedProgressionResult.Candidate
        }

        val newTarget = candidate(listOf(9, 9, 9), listOf(9, 9, 9))
        assertEquals(listOf(10, 10, 10), newTarget.proposedExactReps)
        assertFalse(newTarget.atRepCeiling)
        assertFalse(candidate(listOf(10, 10, 10), listOf(10, 8, 10)).atRepCeiling)
        assertTrue(candidate(listOf(10, 10, 10), listOf(10, 11, 10)).atRepCeiling)
    }

    @Test fun repLadderBuildsTargetsThenResetsOnlyAfterAnEarnedVerifiedLoadStep() {
        val mode=BasicProgressionStrategy.REP_LADDER_PROGRESSION
        val building=request(mode,reps=listOf(8,7,10)).copy(
            issuedRepTargets=listOf(8,8,10))
        val pending=BasicProgressionPolicyDomain.review(building)
            as BasicProgressionResult.Candidate
        assertEquals(listOf(9,8,10),pending.proposedRepTargets)
        assertEquals(listOf(n("100"),n("100"),n("100")),pending.proposedWorkingLoads)
        assertEquals(emptySet<Int>(),pending.increasedOrdinals)
        val all=request(mode,reps=listOf(10,10,10)).copy(
            issuedRepTargets=listOf(10,10,10))
        val newRung=BasicProgressionPolicyDomain.review(all)
            as BasicProgressionResult.Candidate
        assertEquals(listOf(n("105"),n("105"),n("105")),newRung.proposedWorkingLoads)
        assertEquals(listOf(6,6,6),newRung.proposedRepTargets)
        assertEquals(setOf(0,1,2),newRung.increasedOrdinals)
        assertFalse(newRung.mayModifyIssuedWorkout)
        val prior=checkNotNull(all.previous)
        val stockLimit=BasicProgressionPolicyDomain.review(all.copy(
            issuedWorkingLoads=listOf(n("120"),n("120"),n("120")),
            previous=prior.copy(observations=prior.observations.map{
                it.copy(issuedLoad=n("120"),actualLoad=n("120"))
            }))) as BasicProgressionResult.Candidate
        assertEquals(BasicProgressionDecision.MAXIMUM_LOAD_REACHED,
            stockLimit.decision)
        assertEquals(listOf(n("120"),n("120"),n("120")),
            stockLimit.proposedWorkingLoads)
    }

    @Test fun repOnlyAndManualDoNotRequireAWeightIncrement() {
        val noStep=BigDecimal.ZERO
        val manual=BasicProgressionPolicyDomain.review(
            request(BasicProgressionStrategy.MANUAL_LOGGING).copy(
                previous=null,previousScope=null,verifiedStock=null,
                maximumAuthorizedIncrement=noStep))
        assertTrue(manual is BasicProgressionResult.Candidate)
        val repOnly=BasicProgressionPolicyDomain.review(request(
            BasicProgressionStrategy.REP_TARGET_PROGRESSION,
            reps=listOf(8,7,10)).copy(
                issuedRepTargets=listOf(8,8,10),verifiedStock=null,
                maximumAuthorizedIncrement=noStep))
        assertEquals(listOf(9,8,10),(repOnly as
            BasicProgressionResult.Candidate).proposedRepTargets)
        assertEquals(BasicProgressionResult.Refused(
            BasicProgressionRefusal.INVALID_SOURCE_OR_LOAD),
            BasicProgressionPolicyDomain.review(request(
                BasicProgressionStrategy.STRICT_DOUBLE_PROGRESSION).copy(
                    maximumAuthorizedIncrement=noStep)))
    }

    @Test fun eachPublicBaselineModeHasAnExplicitNonWritingPath() {
        assertEquals(setOf(
            BasicProgressionStrategy.MANUAL_LOGGING,
            BasicProgressionStrategy.STRICT_DOUBLE_PROGRESSION,
            BasicProgressionStrategy.PER_SET_DYNAMIC_PROGRESSION,
            BasicProgressionStrategy.LINEAR_LOAD_PROGRESSION,
            BasicProgressionStrategy.REP_TARGET_PROGRESSION,
            BasicProgressionStrategy.REP_LADDER_PROGRESSION,
            BasicProgressionStrategy.ASSISTANCE_DOUBLE_PROGRESSION
        ),BasicProgressionStrategy.entries.toSet())
    }
}
