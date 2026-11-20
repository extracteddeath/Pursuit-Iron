package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

/**
 * Cross-route numerical contract: raw editor -> both journal reducers ->
 * corrected factual receipt -> issued/actual distinction. Nothing here may
 * infer a future load, use actual RIR from a target, or normalize Machine B
 * into a physically distinct Machine A.
 */
class KotlinSetNumberAuthorityDomainTest {
    private fun n(v:String)=BigDecimal(v)
    private fun load(v:String="215")=DraftSetLoad.Verified(
        LoadUnit.LB,LoadBasis.MACHINE_STACK,n(v))
    private fun actual(
        weight:String?="215",reps:Int=8,rir:Int?=2,
        unit:LoadUnit?=LoadUnit.LB,machine:String?="machine-A"
    )=KotlinPerformedSet(weight?.let(::n),unit,reps,rir,machine)

    @Test fun allOutcomesAndNumericalDeviationsHaveOneExplicitMeaning() {
        val issued=load()
        val pending=KotlinSetNumberAuthorityDomain.review(issued,null,null)
            as KotlinSetNumberReview.Accepted
        assertEquals(KotlinSetLoadRelation.UNRECORDED,pending.fact.relation)
        assertNull(pending.fact.actualRir)
        assertNull(pending.fact.performed)
        assertFalse(pending.fact.mayModifyIssuedTarget)
        assertFalse(pending.fact.mayChangeFutureProgram)

        val matched=KotlinSetNumberAuthorityDomain.review(issued,
            KotlinJournalSetOutcome.COMPLETED,actual("215.000"))
            as KotlinSetNumberReview.Accepted
        assertEquals(KotlinSetLoadRelation.COMPLETED_AS_ISSUED,matched.fact.relation)
        assertEquals(issued,matched.fact.issuedLoad)
        assertEquals(n("215.000"),matched.fact.performed?.load)

        val different=KotlinSetNumberAuthorityDomain.review(issued,
            KotlinJournalSetOutcome.COMPLETED,actual("205",rir=null))
            as KotlinSetNumberReview.Accepted
        assertEquals(KotlinSetLoadRelation.COMPLETED_DIFFERENT_ACTUAL,
            different.fact.relation)
        assertNull(different.fact.actualRir)
        assertEquals(n("215"),(different.fact.issuedLoad as DraftSetLoad.Verified).value)
        assertEquals(n("205"),different.fact.performed?.load)
        assertFalse(different.fact.mayModifyIssuedTarget)

        val failed=KotlinSetNumberAuthorityDomain.review(issued,
            KotlinJournalSetOutcome.FAILED_ATTEMPT,actual("215",0,null))
            as KotlinSetNumberReview.Accepted
        assertEquals(KotlinSetLoadRelation.FAILED_ATTEMPT,failed.fact.relation)
        assertEquals(n("215"),failed.fact.performed?.load)
        val skipped=KotlinSetNumberAuthorityDomain.review(issued,
            KotlinJournalSetOutcome.SKIPPED,null)
            as KotlinSetNumberReview.Accepted
        assertEquals(KotlinSetLoadRelation.SKIPPED,skipped.fact.relation)
        assertNull(skipped.fact.performed)

        val bodyweight=KotlinSetNumberAuthorityDomain.review(
            DraftSetLoad.ExplicitBodyweight,KotlinJournalSetOutcome.COMPLETED,
            actual(null,12,null,null,null)) as KotlinSetNumberReview.Accepted
        assertEquals(KotlinSetLoadRelation.COMPLETED_AS_ISSUED,bodyweight.fact.relation)
        assertNull(bodyweight.fact.performed?.load)
    }

    @Test fun generatedCustomAndParserAcceptExactlyTheSameActualNumberMatrix() {
        val issued=load()
        val target=KotlinWorkoutTarget("press",ComposerRole.ACCESSORY,
            ComposerSessionPurpose.HYPERTROPHY,"press:s0",DraftSetType.WORKING,
            0,issued,RepBand(8,12),2,45,90)
        val states=listOf(
            KotlinJournalSetOutcome.COMPLETED to actual("215.000",8,null),
            KotlinJournalSetOutcome.COMPLETED to actual("185",12,0,"LB".let{LoadUnit.LB},"machine-B"),
            KotlinJournalSetOutcome.COMPLETED to actual("0",1,10),
            KotlinJournalSetOutcome.FAILED_ATTEMPT to actual("215",0,null),
            KotlinJournalSetOutcome.SKIPPED to null,
            KotlinJournalSetOutcome.COMPLETED to actual("215",0),
            KotlinJournalSetOutcome.COMPLETED to actual("215",101),
            KotlinJournalSetOutcome.FAILED_ATTEMPT to actual("215",8,null),
            KotlinJournalSetOutcome.COMPLETED to actual("215",8,11),
            KotlinJournalSetOutcome.COMPLETED to actual("215",8,2,LoadUnit.KG),
            KotlinJournalSetOutcome.COMPLETED to actual(null),
            KotlinJournalSetOutcome.COMPLETED to actual("-1"),
            KotlinJournalSetOutcome.COMPLETED to actual("100000.001"),
            KotlinJournalSetOutcome.COMPLETED to actual("12.0001"),
            KotlinJournalSetOutcome.COMPLETED to actual("215",8,2,machine=""),
            KotlinJournalSetOutcome.COMPLETED to actual("215",8,2,machine="x".repeat(100))
        )
        val expected=listOf(true,true,true,true,true,
            false,false,false,false,false,false,false,false,false,false,false)
        for((i,input) in states.withIndex()) {
            val (outcome,performed)=input
            val policy=KotlinSetNumberAuthorityDomain.valid(issued,outcome,performed)
            assertEquals("Case $i",expected[i],policy)
            assertEquals("Generated case $i",policy,
                KotlinWorkoutJournalDomain.validPerformed(target,outcome,performed))
            assertEquals("Custom case $i",policy,
                CustomWorkoutJournalDomain.actualValid(issued,outcome,performed))
        }
        assertTrue(KotlinSetNumberAuthorityDomain.valid(
            DraftSetLoad.ExplicitBodyweight,KotlinJournalSetOutcome.COMPLETED,
            actual(null,8,null,null,null)))
        assertFalse(KotlinSetNumberAuthorityDomain.valid(
            DraftSetLoad.ExplicitBodyweight,KotlinJournalSetOutcome.COMPLETED,actual()))
        val accepted=KotlinWorkoutEntryDomain.parse(target,"205","9","")
            as KotlinSetEntryResult.Accepted
        assertTrue(KotlinSetNumberAuthorityDomain.valid(issued,
            KotlinJournalSetOutcome.COMPLETED,accepted.performed))
        assertEquals(n("205"),accepted.performed.load)
        assertNull(accepted.performed.actualRir)
        assertEquals(KotlinSetEntryRefusal.INVALID_WEIGHT,
            (KotlinWorkoutEntryDomain.parse(target,"205.0001","9","")
                as KotlinSetEntryResult.Rejected).reason)
    }

    @Test fun sameActualLoadCannotBeConfusedWithDifferentPhysicalMachine() {
        val issued=load("200")
        val fromA=actual("200",machine="machine-A")
        val fromB=actual("200.00",machine="machine-B")
        val a=KotlinSetNumberAuthorityDomain.review(issued,
            KotlinJournalSetOutcome.COMPLETED,fromA)
            as KotlinSetNumberReview.Accepted
        val b=KotlinSetNumberAuthorityDomain.review(issued,
            KotlinJournalSetOutcome.COMPLETED,fromB)
            as KotlinSetNumberReview.Accepted
        // Both match a numeric issued target. They do not provide the
        // physical equivalence needed for progression across machines.
        assertEquals(KotlinSetLoadRelation.COMPLETED_AS_ISSUED,a.fact.relation)
        assertEquals(KotlinSetLoadRelation.COMPLETED_AS_ISSUED,b.fact.relation)
        assertNotEquals(a.fact.performed?.machineInstanceId,
            b.fact.performed?.machineInstanceId)
        assertEquals(fromB.machineInstanceId,b.fact.performed?.machineInstanceId)
    }
}
