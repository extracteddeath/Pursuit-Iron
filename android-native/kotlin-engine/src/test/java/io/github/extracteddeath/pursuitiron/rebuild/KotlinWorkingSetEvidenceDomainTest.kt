package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

/**
 * One canonical bridge from actual journal fields into downstream evidence.
 * A future session may review these facts, but never reinterpret them as a
 * new working-set target or invent a completed amplifier/failed set.
 */
class KotlinWorkingSetEvidenceDomainTest {
    private fun n(value:String)=BigDecimal(value)
    private val issued=DraftSetLoad.Verified(
        LoadUnit.LB,LoadBasis.MACHINE_STACK,n("215"))
    private fun actual(
        load:String?="215",reps:Int=8,rir:Int?=null,
        unit:LoadUnit?=LoadUnit.LB,machine:String?="A"
    )=KotlinPerformedSet(load?.let(::n),unit,reps,rir,machine)
    private fun read(
        ordinal:Int,outcome:KotlinJournalSetOutcome,
        performed:KotlinPerformedSet?=actual(),
        load:DraftSetLoad=issued,
        rir:Int?=2
    )=KotlinWorkingSetEvidenceDomain.review(
        ordinal,load,rir,outcome,performed)

    @Test fun changedActualNeverBecomesNewIssuedWeightOrInventedEffort() {
        val result=read(3,KotlinJournalSetOutcome.COMPLETED,
            actual("205.000",2,null,"LB".let{LoadUnit.LB},"B"))
            as KotlinWorkingSetEvidenceReview.Accepted
        val e=result.evidence
        assertEquals(KotlinSetLoadRelation.COMPLETED_DIFFERENT_ACTUAL,e.relation)
        assertEquals("B",e.machineInstanceId)
        assertEquals(3,e.fact.workingOrdinal)
        assertEquals(SetOutcome.COMPLETED,e.fact.outcome)
        assertEquals(n("215"),e.fact.issuedLoad)
        assertEquals(n("205.000"),e.fact.actualLoad)
        assertEquals(2,e.fact.actualReps)
        assertNull(e.fact.actualRir)
        assertEquals(2,e.fact.issuedRir)
        assertFalse(e.mayChangeIssuedTargets)
        assertFalse(e.mayChangeHistory)
        assertFalse(e.mayChangeFutureProgram)
        assertEquals(issued,e.issuedLoad)
    }

    @Test fun allOutcomesAndPhysicalUnitsRetainExactSemantics() {
        val same=read(0,KotlinJournalSetOutcome.COMPLETED,
            actual("215.000")) as KotlinWorkingSetEvidenceReview.Accepted
        assertEquals(KotlinSetLoadRelation.COMPLETED_AS_ISSUED,
            same.evidence.relation)
        assertEquals(n("215.000"),same.evidence.fact.actualLoad)
        val failed=read(1,KotlinJournalSetOutcome.FAILED_ATTEMPT,
            actual("205",0,null)) as KotlinWorkingSetEvidenceReview.Accepted
        assertEquals(SetOutcome.FAILED_ATTEMPT,failed.evidence.fact.outcome)
        assertEquals(KotlinSetLoadRelation.FAILED_ATTEMPT,failed.evidence.relation)
        assertEquals(0,failed.evidence.fact.actualReps)
        assertNull(failed.evidence.fact.actualRir)
        val skip=read(2,KotlinJournalSetOutcome.SKIPPED,null)
            as KotlinWorkingSetEvidenceReview.Accepted
        assertEquals(SetOutcome.SKIPPED,skip.evidence.fact.outcome)
        assertNull(skip.evidence.fact.actualLoad)
        assertNull(skip.evidence.fact.actualReps)
        assertNull(skip.evidence.fact.actualRir)
        assertNull(skip.evidence.machineInstanceId)
        assertEquals(n("215"),skip.evidence.fact.issuedLoad)
        val body=read(0,KotlinJournalSetOutcome.COMPLETED,
            actual(null,10,null,null,null),DraftSetLoad.ExplicitBodyweight)
            as KotlinWorkingSetEvidenceReview.Accepted
        assertEquals(SetOutcome.COMPLETED,body.evidence.fact.outcome)
        assertEquals(KotlinSetLoadRelation.COMPLETED_AS_ISSUED,body.evidence.relation)
        assertNull(body.evidence.fact.issuedLoad)
        assertNull(body.evidence.fact.actualLoad)
    }

    @Test fun invalidObservationsNeverCrossTheSharedFutureEvidenceBoundary() {
        val invalid=listOf(
            read(-1,KotlinJournalSetOutcome.COMPLETED),
            read(32,KotlinJournalSetOutcome.COMPLETED),
            read(0,KotlinJournalSetOutcome.COMPLETED,rir=11),
            read(0,KotlinJournalSetOutcome.SKIPPED,actual()),
            read(0,KotlinJournalSetOutcome.FAILED_ATTEMPT,actual("215",4,null)),
            read(0,KotlinJournalSetOutcome.COMPLETED,actual("215",0,null)),
            read(0,KotlinJournalSetOutcome.COMPLETED,actual("215",9,11)),
            read(0,KotlinJournalSetOutcome.COMPLETED,actual("215",9,null,LoadUnit.KG)),
            read(0,KotlinJournalSetOutcome.COMPLETED,actual("215.0001")),
            read(0,KotlinJournalSetOutcome.COMPLETED,actual(null)),
            read(0,KotlinJournalSetOutcome.COMPLETED,
                actual(null,8,null,null,null)),
            read(0,KotlinJournalSetOutcome.COMPLETED,
                actual("215",8,null,machine="wrong machine")),
            read(0,KotlinJournalSetOutcome.COMPLETED,
                actual("215",8,null,machine=""))
        )
        assertTrue(invalid.all{it is KotlinWorkingSetEvidenceReview.Rejected})
        assertEquals(
            KotlinWorkingSetEvidenceReview.Rejected(
                KotlinWorkingSetEvidenceRefusal.INVALID_WORKING_ORDINAL_OR_ISSUED_RIR),
            invalid[0])
        assertEquals(
            KotlinWorkingSetEvidenceReview.Rejected(
                KotlinWorkingSetEvidenceRefusal.INVALID_JOURNAL_NUMBERS),
            invalid[3])
        // Two loads with the same numerical value do NOT imply Machine A
        // and Machine B share performance, progression or inventory.
        val a=read(0,KotlinJournalSetOutcome.COMPLETED,
            actual("215",machine="A")) as KotlinWorkingSetEvidenceReview.Accepted
        val b=read(0,KotlinJournalSetOutcome.COMPLETED,
            actual("215.00",machine="B")) as KotlinWorkingSetEvidenceReview.Accepted
        assertEquals(a.evidence.relation,b.evidence.relation)
        assertNotEquals(a.evidence.machineInstanceId,b.evidence.machineInstanceId)
    }
}
