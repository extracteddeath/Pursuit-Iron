package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class TieredLinearProgressionDomainTest {
    private fun n(s:String)=BigDecimal(s)
    private val scope=BasicProgressionScope("athlete","program","gym","block","upper",
        "press","machine-A",LoadUnit.LB,LoadBasis.MACHINE_STACK)
    private val stock=VerifiedLoadStock.Stack(LoadUnit.LB,LoadBasis.MACHINE_STACK,n("5"),n("5"),n("300"))
    private fun request(
        completed:List<TieredLinearExposure> = emptyList(),
        tier:PercentageTier=PercentageTier.PRIMARY,
        count:Int=5
    )=TieredLinearRequest("next",scope,tier,TieredLinearState(0,n("100")),true,completed,stock,n("5"),count)

    private fun exposure(
        id:String, order:Long, stage:Int=0, load:String="100",
        reps:Int=3, count:Int=5, failed:Boolean=false,
        tier:PercentageTier=PercentageTier.PRIMARY,
        source:BasicProgressionScope=scope
    ):TieredLinearExposure {
        val target=if(tier==PercentageTier.PRIMARY)listOf(3,2,1)[stage] else listOf(10,8,6)[stage]
        return TieredLinearExposure(id,order,source,tier,stage,
            List(count){i->TieredLinearSetTarget(i,n(load),target,i==count-1)},
            List(count){i->if(failed && i==count-1)
                WorkingSetFact(i,SetOutcome.FAILED_ATTEMPT,n(load),0,null,n(load),2)
            else WorkingSetFact(i,SetOutcome.COMPLETED,n(load),reps,null,n(load),2)},true)
    }
    private fun review(r:TieredLinearRequest)=
        TieredLinearProgressionDomain.review(r) as TieredLinearResult.Candidate

    @Test fun successfulPrimaryAndSecondaryUseIndependentExactIncrements() {
        val a=exposure("a",1)
        val b=exposure("b",2,load="105",reps=4)
        val secondary=exposure("c",3,reps=10,count=3,tier=PercentageTier.SECONDARY)
        val result=review(request(listOf(a,b,secondary)))
        assertEquals(TieredLinearState(0,n("110")),result.state)
        assertEquals(5,result.sourceSetCount)
        assertEquals(listOf("a","b"),result.transitions.map{it.workoutId})
        assertTrue(result.transitions.all{it.earnedLoadFact!=null})
        assertEquals(List(5){n("110")},result.targets.map{it.load})
        assertEquals(listOf(false,false,false,false,true),result.targets.map{it.repOut})
        val s=review(request(listOf(a,b,secondary),PercentageTier.SECONDARY,3))
        assertEquals(TieredLinearState(0,n("105")),s.state)
        assertEquals(List(3){10},s.targets.map{it.minimumReps})
        assertFalse(s.maySaveProgram)
        assertFalse(s.mayIssueWorkout)
        assertFalse(s.mayModifyHistory)
    }

    @Test fun genuineFailureCascadesThenRequestsExplicitResetAtVerifiedSetting() {
        val a=exposure("a",1,failed=true)
        val b=exposure("b",2,stage=1,reps=2,count=6,failed=true)
        val c=exposure("c",3,stage=2,reps=1,count=10,failed=true)
        val first=review(request(listOf(a),count=6))
        assertEquals(TieredLinearState(1,n("100")),first.state)
        assertEquals(6,first.sourceSetCount)
        assertEquals(List(6){2},first.targets.map{it.minimumReps})
        val second=review(request(listOf(a,b),count=10))
        assertEquals(TieredLinearState(2,n("100")),second.state)
        assertEquals(10,second.sourceSetCount)
        assertEquals(List(10){1},second.targets.map{it.minimumReps})
        val reset=review(request(listOf(a,b,c)))
        assertEquals(TieredLinearState(0,n("85")),reset.state)
        assertEquals(listOf(TieredLinearDecision.NEXT_STAGE,TieredLinearDecision.NEXT_STAGE,
            TieredLinearDecision.RESET_REVIEW),reset.transitions.map{it.decision})
        assertTrue(reset.requiresReviewedReset)
        assertNull(reset.transitions.last().earnedLoadFact)
    }

    @Test fun secondaryUsesThreeSetsAndDescendingRepTiers() {
        val tier=PercentageTier.SECONDARY
        val first=exposure("a",1,reps=9,count=3,tier=tier)
        val second=exposure("b",2,stage=1,reps=7,count=3,tier=tier)
        val r=review(request(listOf(first,second),tier,3))
        assertEquals(TieredLinearState(2,n("100")),r.state)
        assertEquals(List(3){6},r.targets.map{it.minimumReps})
        assertEquals(3,r.sourceSetCount)
    }

    @Test fun missingSkippedChangedAndDuplicateFactsDoNotBecomeStallsOrSuccess() {
        val base=exposure("a",1)
        val invalid=listOf(
            base.copy(observations=base.observations.dropLast(1)),
            base.copy(observations=base.observations.dropLast(1)+base.observations.first()),
            base.copy(observations=base.observations.dropLast(1)+
                WorkingSetFact(4,SetOutcome.SKIPPED,null,null,null,n("100"),2)),
            base.copy(observations=base.observations.map{it.copy(actualLoad=n("95"))})
        )
        for(e in invalid)assertEquals(TieredLinearResult.Refused(TieredLinearRefusal.INVALID_ATTEMPT),
            TieredLinearProgressionDomain.review(request(listOf(e))))
        assertEquals(TieredLinearResult.Refused(TieredLinearRefusal.INCONSISTENT_ISSUED_STATE),
            TieredLinearProgressionDomain.review(request(listOf(base.copy(issuedStage=1)))))
        assertEquals(TieredLinearResult.Refused(TieredLinearRefusal.INVALID_LEDGER),
            TieredLinearProgressionDomain.review(request(listOf(base,base))))
    }

    @Test fun differentScopesDoNotEarnChangesAndLatestInterruptedAttemptBlocksFallback() {
        val base=exposure("a",1)
        val foreign=listOf(scope.copy(gymId="other"),scope.copy(trainingDayId="other"),
            scope.copy(machineInstanceId="machine-B"),scope.copy(blockId="other"))
        for(s in foreign) {
            val other=exposure("b",2,source=s)
            assertEquals(TieredLinearState(0,n("105")),review(request(listOf(base,other))).state)
        }
        val interrupted=exposure("b",2,load="105").copy(comparableTraining=false)
        assertEquals(TieredLinearResult.Refused(TieredLinearRefusal.NONCOMPARABLE_LATEST),
            TieredLinearProgressionDomain.review(request(listOf(base,interrupted))))
    }

    @Test fun recoveryDoesNotChangeUnderlyingStateAndNeverPrescribesRepOut() {
        val base=exposure("a",1)
        val recovery=review(request(listOf(base),count=3).copy(recovery=true))
        assertEquals(TieredLinearState(0,n("105")),recovery.state)
        assertEquals(List(3){n("60")},recovery.targets.map{it.load})
        assertFalse(recovery.targets.any{it.repOut})
        assertTrue(recovery.recovery)
    }

    @Test fun completedRecoveryReplaysWithoutChangingTheUnderlyingTier() {
        val a=exposure("a",1)
        val light=exposure("light",2,load="60",reps=15,count=3).let {
            it.copy(comparableTraining=false,recoveryReference=n("105"),
                issuedTargets=it.issuedTargets.map{target->target.copy(repOut=false)})
        }
        val recovered=review(request(listOf(a,light)))
        assertEquals(TieredLinearState(0,n("105")),recovered.state)
        assertEquals(listOf("a"),recovered.transitions.map{it.workoutId})
        val interrupted=exposure("interrupted",2,load="105").copy(comparableTraining=false)
        assertEquals(TieredLinearResult.Refused(TieredLinearRefusal.NONCOMPARABLE_LATEST),
            TieredLinearProgressionDomain.review(request(listOf(a,interrupted,
                light.copy(completionOrder=3)))))
        val next=exposure("b",3,load="105")
        assertEquals(TieredLinearState(0,n("110")),review(request(listOf(a,light,next))).state)
        assertEquals(TieredLinearResult.Refused(TieredLinearRefusal.INCONSISTENT_ISSUED_STATE),
            TieredLinearProgressionDomain.review(request(listOf(a,light.copy(recoveryReference=n("100"))))))
        assertEquals(TieredLinearResult.Refused(TieredLinearRefusal.INCONSISTENT_ISSUED_STATE),
            TieredLinearProgressionDomain.review(request(listOf(a,light.copy(
                issuedTargets=light.issuedTargets.map{it.copy(load=n("65"))})))))
        assertEquals(TieredLinearResult.Refused(TieredLinearRefusal.INCONSISTENT_ISSUED_STATE),
            TieredLinearProgressionDomain.review(request(listOf(a,light.copy(comparableTraining=true)))))
        assertEquals(TieredLinearResult.Refused(TieredLinearRefusal.INCONSISTENT_ISSUED_STATE),
            TieredLinearProgressionDomain.review(request(listOf(a,light.copy(
                issuedTargets=light.issuedTargets.map{it.copy(repOut=true)})))))
    }

    @Test fun limitsAndStockNeverInventLoadOrImplicitReset() {
        val base=exposure("a",1)
        val held=review(request(listOf(base)).copy(maximumAuthorizedIncrease=n("2.5")))
        assertEquals(TieredLinearState(0,n("100")),held.state)
        assertEquals(TieredLinearDecision.HOLD_AT_STOCK_OR_LIMIT,held.transitions.single().decision)
        assertNull(held.transitions.single().earnedLoadFact)
        assertEquals(TieredLinearResult.Refused(TieredLinearRefusal.UNVERIFIED_STOCK),
            TieredLinearProgressionDomain.review(request().copy(verifiedStock=null)))
        assertEquals(TieredLinearResult.Refused(TieredLinearRefusal.INVALID_INITIAL_STATE),
            TieredLinearProgressionDomain.review(request().copy(initialStateConfirmed=false)))
        assertEquals(TieredLinearResult.Refused(TieredLinearRefusal.INVALID_INITIAL_STATE),
            TieredLinearProgressionDomain.review(request().copy(initialState=TieredLinearState(0,n("103")))))
        val minStock=VerifiedLoadStock.Totals(LoadUnit.LB,LoadBasis.MACHINE_STACK,listOf(n("100"),n("105")))
        assertEquals(TieredLinearResult.Refused(TieredLinearRefusal.NO_SAFE_RESET_OR_RECOVERY),
            TieredLinearProgressionDomain.review(request().copy(verifiedStock=minStock,recovery=true)))
    }

    @Test fun correctionThatChangesAnEarlierTransitionCannotRewriteLaterIssuedTargets() {
        val original=exposure("a",1)
        val later=exposure("b",2,load="105")
        val corrected=original.copy(observations=original.observations.map{it.copy(actualReps=2)})
        assertEquals(TieredLinearState(0,n("110")),review(request(listOf(original,later))).state)
        assertEquals(TieredLinearResult.Refused(TieredLinearRefusal.INCONSISTENT_ISSUED_STATE),
            TieredLinearProgressionDomain.review(request(listOf(corrected,later))))
    }
}
