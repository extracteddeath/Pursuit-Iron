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
            LoadBasis.MACHINE_STACK,n("0.001"),n("0.001"),n("300"))
        val step=listOf(BasicProgrammedWeek(1,n("33.333"),3,RepBand(6,8)))
        val r=BasicProgrammedPercentageLoadDomain.review(request(
            tm="200.001",weeks=step,stock=stock))
            as BasicProgrammedLoadResult.Candidate
        val week=r.weeks.single()
        assertTrue(week.verifiedLoad<=week.rawCalculatedLoad)
        assertTrue(week.verifiedLoad>=n("66.666"))
    }
}
