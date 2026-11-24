package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class BasicProgrammedPercentageLoadDomainTest {
    private fun n(v:String)=BigDecimal(v)
    private val scope=BasicProgressionScope(
        "athlete","program","gym","strength","upper","bench",
        "bar-A",LoadUnit.LB,LoadBasis.MACHINE_STACK)
    private val inventory=VerifiedLoadStock.Stack(
        LoadUnit.LB,LoadBasis.MACHINE_STACK,n("5"),n("5"),n("300"))
    private val programmed=listOf(
        BasicProgrammedWeek(1,n("70"),4,RepBand(5,5)),
        BasicProgrammedWeek(2,n("75"),4,RepBand(4,4)),
        BasicProgrammedWeek(3,n("80"),3,RepBand(3,3)),
        BasicProgrammedWeek(4,n("60"),2,RepBand(5,5)))
    private fun request(
        weeks:List<BasicProgrammedWeek> = programmed,
        tm:String="200",confirmed:Boolean=true,
        stock:VerifiedLoadStock?=inventory,
        scope:BasicProgressionScope=this.scope
    )=BasicProgrammedLoadRequest(
        scope,n(tm),confirmed,weeks.size,weeks,stock)

    @Test fun genericWaveRefusesMachineTaggedFreeBarButAllowsVerifiedFreeBar() {
        val bar=VerifiedLoadStock.Barbell(LoadUnit.LB,n("45"),mapOf(
            n("25") to 2,n("5") to 2,n("2.5") to 2))
        val valid=scope.copy(basis=LoadBasis.BARBELL_TOTAL,machineInstanceId=null)
        val week=listOf(BasicProgrammedWeek(1,n("50"),3,RepBand(5,5)))
        val candidate=BasicProgrammedPercentageLoadDomain.review(request(
            weeks=week,stock=bar,scope=valid)) as BasicProgrammedLoadResult.Candidate
        assertEquals(n("100"),candidate.weeks.single().verifiedLoad)
        assertEquals(BasicProgrammedLoadResult.Refused(
            BasicProgrammedLoadRefusal.INCOMPATIBLE_ASSISTANCE_OR_LOAD_BASIS),
            BasicProgrammedPercentageLoadDomain.review(request(
                weeks=week,stock=bar,scope=valid.copy(machineInstanceId="machine-A"))))
    }

    @Test fun knownPhantomWeightsAreDownOnlyForProgrammedIntentAndNeverEarnedStock() {
        for((raw,lower,upper) in listOf(Triple("38.5","35","40"),Triple("159.5","155","160"))) {
            val physical=BasicProgrammedPercentageLoadDomain.verifiedPhysicalLoad(scope,inventory,n(raw))
            assertEquals(ProgrammedPhysicalLoadReview.Confirmed(n(lower)),physical)
            val bracket=EquipmentLoadDomain.inspect(inventory,n(raw),scope.unit) as LoadInspection.Confirmed
            assertFalse(bracket.exact)
            assertEquals(n(lower),bracket.atOrBelow)
            assertEquals(n(upper),bracket.nextAbove)
            assertEquals(KotlinFutureLoadReview.Rejected(KotlinFutureLoadRefusal.UNVERIFIED_CANDIDATE_LOAD),
                KotlinFutureLoadNumberAuthorityDomain.review(n(lower),n(raw),scope.unit,
                    scope.basis,inventory,n("5"),KotlinFutureLoadSource.EARNED_NEXT_INCREMENT))
            val earned=KotlinFutureLoadNumberAuthorityDomain.review(n(lower),n(upper),scope.unit,
                scope.basis,inventory,n("5"),KotlinFutureLoadSource.EARNED_NEXT_INCREMENT)
                as KotlinFutureLoadReview.Accepted
            assertEquals(n(upper),earned.fact.proposedLoad)
        }
    }

    @Test fun fourWeekSourceWaveProducesExactVerifiedLoadsAndRespectsDeload() {
        val plan=BasicProgrammedPercentageLoadDomain.review(request())
            as BasicProgrammedLoadResult.Candidate
        assertEquals(listOf(n("140"),n("150"),n("160"),n("120")),
            plan.weeks.map{it.verifiedLoad})
        assertEquals(programmed.map{it.workingSets},plan.weeks.map{it.workingSets})
        assertEquals(programmed.map{it.reps},plan.weeks.map{it.reps})
        assertFalse(plan.maySaveProgram)
        assertFalse(plan.mayModifyIssuedWorkout)
        assertFalse(plan.mayModifyHistory)
    }

    @Test fun oddPercentAlwaysSelectsRealStockAtOrBelowNotAnInventedWeight() {
        val steps=listOf(BasicProgrammedWeek(1,n("81.9"),3,RepBand(4,6)))
        val result=BasicProgrammedPercentageLoadDomain.review(request(weeks=steps))
            as BasicProgrammedLoadResult.Candidate
        assertEquals(n("163.8"),result.weeks.single().rawCalculatedLoad)
        assertEquals(n("160"),result.weeks.single().verifiedLoad)
        assertTrue(result.weeks.single().verifiedLoad<=result.weeks.single().rawCalculatedLoad)
    }

    @Test fun refusesInferredTrainingMaxWrongStockAndUnknownMachine() {
        assertEquals(BasicProgrammedLoadResult.Refused(
            BasicProgrammedLoadRefusal.INVALID_SCOPE_OR_MAX),
            BasicProgrammedPercentageLoadDomain.review(request(confirmed=false)))
        assertEquals(BasicProgrammedLoadResult.Refused(
            BasicProgrammedLoadRefusal.UNVERIFIED_GYM_STOCK),
            BasicProgrammedPercentageLoadDomain.review(request(stock=null)))
        assertEquals(BasicProgrammedLoadResult.Refused(
            BasicProgrammedLoadRefusal.UNVERIFIED_GYM_STOCK),
            BasicProgrammedPercentageLoadDomain.review(request(
                scope=scope.copy(unit=LoadUnit.KG))))
        assertEquals(BasicProgrammedLoadResult.Refused(
            BasicProgrammedLoadRefusal.INCOMPATIBLE_ASSISTANCE_OR_LOAD_BASIS),
            BasicProgrammedPercentageLoadDomain.review(request(
                scope=scope.copy(basis=LoadBasis.ASSISTANCE_COUNTERWEIGHT))))
        assertEquals(BasicProgrammedLoadResult.Refused(
            BasicProgrammedLoadRefusal.INVALID_SCOPE_OR_MAX),
            BasicProgrammedPercentageLoadDomain.review(request(tm="0")))
    }

    @Test fun incompleteOrUnorderedSourceWeeksNeverProduceAPhantomWave() {
        val short=programmed.dropLast(1)
        val missing=programmed.mapIndexed{i,x->
            if(i==1)x.copy(weekInBlock=3) else x
        }
        assertEquals(BasicProgrammedLoadResult.Refused(
            BasicProgrammedLoadRefusal.INCOMPLETE_OR_INVALID_WEEK_SCHEDULE),
            BasicProgrammedPercentageLoadDomain.review(
                request(weeks=short).copy(blockWeeks=4)))
        assertEquals(BasicProgrammedLoadResult.Refused(
            BasicProgrammedLoadRefusal.INCOMPLETE_OR_INVALID_WEEK_SCHEDULE),
            BasicProgrammedPercentageLoadDomain.review(request(weeks=missing)))
        assertEquals(BasicProgrammedLoadResult.Refused(
            BasicProgrammedLoadRefusal.INCOMPLETE_OR_INVALID_WEEK_SCHEDULE),
            BasicProgrammedPercentageLoadDomain.review(request(weeks=listOf(
                BasicProgrammedWeek(1,n("125"),4,RepBand(3,5))))))
    }

    @Test fun exactSourcePercentBelowFirstAvailableLoadFailsClosed() {
        val tiny=listOf(BasicProgrammedWeek(1,n("1"),1,RepBand(1,1)))
        assertEquals(BasicProgrammedLoadResult.Refused(
            BasicProgrammedLoadRefusal.NO_SAFE_AT_OR_BELOW_LOAD),
            BasicProgrammedPercentageLoadDomain.review(request(weeks=tiny)))
    }

    @Test fun decimalValuesBeyondThreePlacesUseDownOnlySearchEnvelope() {
        val stock=VerifiedLoadStock.Stack(LoadUnit.LB,
            LoadBasis.MACHINE_STACK,n("60"),n("0.001"),n("70"))
        val step=listOf(BasicProgrammedWeek(1,n("33.333"),3,RepBand(6,8)))
        val r=BasicProgrammedPercentageLoadDomain.review(request(
            tm="200.001",weeks=step,stock=stock))
            as BasicProgrammedLoadResult.Candidate
        val week=r.weeks.single()
        assertTrue(week.verifiedLoad<=week.rawCalculatedLoad)
        assertTrue(week.verifiedLoad>=n("66.666"))
    }
}
