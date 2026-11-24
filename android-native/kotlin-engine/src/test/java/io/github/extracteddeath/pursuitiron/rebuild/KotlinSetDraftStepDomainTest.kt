package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class KotlinSetDraftStepDomainTest {
    private fun n(value:String)=BigDecimal(value)
    @Test fun loadGesturesUseExactStockNeighborsAndStopAtBoundaries() {
        val p=KotlinSetDraftStepDomain
        val target=DraftSetLoad.Verified(LoadUnit.LB,LoadBasis.MACHINE_STACK,n("40"))
        val stock=VerifiedLoadStock.Stack(LoadUnit.LB,LoadBasis.MACHINE_STACK,
            n("25"),n("5"),n("50"))
        assertEquals("40",p.stepLoad("38.5",1,1,target,stock))
        assertEquals("35",p.stepLoad("38.5",-1,1,target,stock))
        assertEquals("50",p.stepLoad("45",1,3,target,stock))
        assertEquals("25",p.stepLoad("30",-1,3,target,stock))
        assertNull(p.stepLoad("25",-1,1,target,stock))
        assertNull(p.stepLoad("50",1,1,target,stock))
        assertNull(p.stepLoad("40",1,1,target,null))
        assertNull(p.stepLoad("40",1,1,target,stock.copy(unit=LoadUnit.KG)))
        assertNull(p.stepLoad("40",1,1,target,stock.copy(basis=LoadBasis.CABLE_STACK)))
        for(invalid in listOf("","-5","1e2","3.0001","100001","4..5"))
            assertNull(p.stepLoad(invalid,1,1,target,stock))
    }

    @Test fun loadGesturesRespectIrregularDumbbellsPairedPlatesAndZeroBasis() {
        val p=KotlinSetDraftStepDomain
        val db=DraftSetLoad.Verified(LoadUnit.LB,LoadBasis.DUMBBELL_PER_HAND,n("25"))
        val rack=VerifiedLoadStock.Totals(db.unit,db.basis,
            listOf(n("15"),n("25"),n("35"),n("50")))
        assertEquals("35",p.stepLoad("25",1,1,db,rack))
        assertEquals("15",p.stepLoad("35",-1,2,db,rack))
        val bar=DraftSetLoad.Verified(LoadUnit.LB,LoadBasis.BARBELL_TOTAL,n("45"))
        val plates=VerifiedLoadStock.Barbell(bar.unit,n("45"),mapOf(n("2.5") to 1,n("10") to 1))
        assertEquals("50",p.stepLoad("45",1,1,bar,plates))
        assertEquals("65",p.stepLoad("50",1,1,bar,plates))
        val landmine=DraftSetLoad.Verified(LoadUnit.LB,LoadBasis.LANDMINE_ADDED_PLATES,n("5"))
        val landmineStock=VerifiedLoadStock.Totals(landmine.unit,landmine.basis,listOf(n("0"),n("5")))
        assertEquals("0",p.stepLoad("5",-1,1,landmine,landmineStock))
        val machine=DraftSetLoad.Verified(LoadUnit.LB,LoadBasis.MACHINE_STACK,n("5"))
        val machineStock=VerifiedLoadStock.Stack(machine.unit,machine.basis,n("0"),n("5"),n("20"))
        assertNull(p.stepLoad("5",-1,1,machine,machineStock))
    }

    @Test fun ordinaryRepsRemainInsideFactualCompletionBounds() {
        val p=KotlinSetDraftStepDomain
        assertEquals("1",p.stepReps("",1,false))
        assertEquals("3",p.stepRepsBy("",1,3,false))
        assertNull(p.stepReps("1",-1,false))
        assertEquals("100",p.stepRepsBy("99",1,3,false))
        assertNull(p.stepReps("100",1,false))
        assertNull(p.stepReps("unknown",1,false))
        assertNull(p.stepReps("8",2,false))
        assertNull(p.stepRepsBy("8",1,4,false))
        assertEquals("5",p.stepRepsBy("8",-1,3,false))
    }

    @Test fun failedAttemptsMayStartAtZeroWithoutInventingCompletion() {
        val p=KotlinSetDraftStepDomain
        assertEquals("0",p.stepReps("",1,true))
        assertEquals("0",p.stepReps("1",-1,true))
        assertNull(p.stepReps("0",-1,true))
        assertEquals("2",p.stepRepsBy("",1,3,true))
        assertNull(p.stepRepsBy("10",1,0,true))
    }

    @Test fun unreportedRirIsNotConvertedToEffortByDecrement() {
        val p=KotlinSetDraftStepDomain
        assertNull(p.stepRir("",-1))
        assertEquals("0",p.stepRir("",1))
        assertEquals("",p.stepRir("0",-1))
        assertEquals("1",p.stepRirBy("",1,2))
        assertEquals("",p.stepRirBy("1",-1,2))
        assertEquals("10",p.stepRirBy("9",1,2))
        assertNull(p.stepRir("10",1))
        assertNull(p.stepRir("11",-1))
        assertNull(p.stepRirBy("8",1,3))
        assertNull(p.stepRir("8",0))
    }

    @Test fun gesturesDoNotCreateAJournalOrExerciseLoadRule() {
        assertEquals("12",KotlinSetDraftStepDomain.stepReps("11",1,false))
        assertEquals("2",KotlinSetDraftStepDomain.stepRir("1",1))
        // The domain returns draft text only. Issuance and performed actual
        // validation remain in KotlinSetNumberAuthorityDomain.
    }
}
