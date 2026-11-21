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

    @Test fun generatedAndCustomColdHistoryContainsCorrectionsNotPhantomNumbers() {
        val source=KotlinTrainingSourceRevision(
            "athlete",1,"program",1,"gym",1,"catalog","coach")
        val targets=(0..1).map{ordinal->
            KotlinWorkoutTarget("press",ComposerRole.ACCESSORY,
                ComposerSessionPurpose.HYPERTROPHY,"set-$ordinal",
                DraftSetType.WORKING,ordinal,load(),
                RepBand(8,12),2,45,90)
        }
        val issued=KotlinStagedWorkout(source,"grant","generated-day",
            0,0,ComposerSessionPurpose.HYPERTROPHY,30,targets)
        var generated=KotlinWorkoutJournalDomain.begin(issued,
            KotlinStoredWorkoutIdentity("receipt","generated-day",source,1))
        fun recordGenerated(
            key:String,weight:String,event:String,corrects:String?=null
        ) {
            generated=(KotlinWorkoutJournalDomain.reduce(generated,
                KotlinJournalOperation.RecordSet(event,generated.revision,key,
                    KotlinJournalSetOutcome.COMPLETED,
                    actual(weight,8,null),corrects)) as KotlinJournalDecision.Proposed).state
        }
        recordGenerated("set-0","215","gen-0")
        recordGenerated("set-1","205","gen-1")
        recordGenerated("set-1","210","gen-1-correct","gen-1")
        assertEquals(List(2){n("215")},generated.snapshot.targets.map {
            (it.load as DraftSetLoad.Verified).value
        })
        generated=(KotlinWorkoutJournalDomain.reduce(generated,
            KotlinJournalOperation.Finish("gen-finish",generated.revision))
            as KotlinJournalDecision.Proposed).state
        val generatedHistory=KotlinWorkoutHistoryEvidenceDomain.project(generated)
            as KotlinHistoryProjectionResult.Candidate
        val generatedFacts=generatedHistory.evidence.sets
        assertEquals(2,generatedFacts.size)
        assertEquals(listOf(n("215"),n("215")),generatedFacts.map {
            (it.issuedLoad as DraftSetLoad.Verified).value
        })
        assertEquals(listOf(n("215"),n("210")),generatedFacts.map{it.actual?.load})
        assertEquals("gen-1-correct",generatedFacts.last().effectiveEventId)
        assertNull(generatedFacts.last().actual?.actualRir)
        assertEquals(KotlinSetLoadRelation.COMPLETED_DIFFERENT_ACTUAL,
            (KotlinSetNumberAuthorityDomain.review(
                generatedFacts.last().issuedLoad,generatedFacts.last().outcome,
                generatedFacts.last().actual) as KotlinSetNumberReview.Accepted)
                .fact.relation)

        val customSets=(1..2).map{ordinal->
            CustomWorkoutSetSnapshot("custom-$ordinal","slot",ordinal,
                CustomAuthoredSet(DraftSetType.WORKING,load(),
                    RepBand(8,12),2,45,90))
        }
        val customDay=CustomWorkoutDaySnapshot(
            CustomWorkoutSourceStamp("athlete","profile","cycle",1,"gym",1),
            1,1,"block",TrainingPhase.HYPERTROPHY,
            CustomBlockPurpose.TRAINING,CustomSplitIdentity.Freeform("Custom"),
            0,"Upper",ComposerSessionPurpose.HYPERTROPHY,
            listOf(CustomWorkoutSlotSnapshot(
                "slot","press","family","variant",ComposerRole.ACCESSORY,
                null,null,false,"",customSets)),1000,null)
        var custom=CustomWorkoutJournalDomain.begin(
            "custom-day",customDay,"a".repeat(64))
        fun recordCustom(
            key:String,weight:String,event:String,corrects:String?=null
        ) {
            custom=(CustomWorkoutJournalDomain.reduce(custom,
                CustomWorkoutJournalOperation.Record(event,custom.revision,
                    CustomWorkoutVisitKey(key,CustomWorkoutVisitPart.MAIN),
                    KotlinJournalSetOutcome.COMPLETED,
                    actual(weight,8,null),corrects))
                as CustomWorkoutJournalDecision.Proposed).draft
        }
        recordCustom("custom-1","215","custom-1")
        recordCustom("custom-2","205","custom-2")
        recordCustom("custom-2","210","custom-2-correct","custom-2")
        assertEquals(List(2){n("215")},custom.snapshot.slots.single().sets.map {
            (it.prescription.load as DraftSetLoad.Verified).value
        })
        custom=(CustomWorkoutJournalDomain.reduce(custom,
            CustomWorkoutJournalOperation.Finish("custom-finish",custom.revision))
            as CustomWorkoutJournalDecision.Proposed).draft
        val customHistory=CustomWorkoutHistoryEvidenceDomain.project(custom)
            as CustomHistoryEvidenceResult.Candidate
        val customFacts=customHistory.evidence.visits
        assertEquals(2,customFacts.size)
        assertEquals(listOf(n("215"),n("215")),customFacts.map {
            (it.prescribedLoad as DraftSetLoad.Verified).value
        })
        assertEquals(listOf(n("215"),n("210")),customFacts.map{it.actual?.load})
        assertEquals("custom-2-correct",customFacts.last().effectiveEventId)
        assertNull(customFacts.last().actual?.actualRir)
        // No future planner is authorized by either historical read.
        assertFalse(generatedHistory.evidence.canApplyProgression)
        assertFalse(customHistory.evidence.canApplyProgression)
    }

    @Test fun futureProposalsAndPerformedSetsShareExactlyOnePhysicalWeightEnvelope() {
        // Numeric format (not history, consent, increment stock or effort)
        // must be identical across the present and future number policies.
        val permitted=listOf("0","0.0000","0.001","12.1000",
            "100.0010","99999.999","100000.0000","1E+3")
        val refused=listOf("-0.001","0.0001","12.1001",
            "99999.9991","100000.001","100001","1E-4")
        for(text in permitted) {
            val value=n(text)
            assertTrue("Valid physical weight: $text",
                KotlinSetNumberAuthorityDomain.validPhysicalLoadValue(value))
            assertTrue("Valid completed physical observation: $text",
                KotlinSetNumberAuthorityDomain.valid(load("215"),
                    KotlinJournalSetOutcome.COMPLETED,
                    actual(text)))
        }
        for(text in refused) {
            val value=n(text)
            assertFalse("Invalid physical weight: $text",
                KotlinSetNumberAuthorityDomain.validPhysicalLoadValue(value))
            assertFalse("Invalid completed physical observation: $text",
                KotlinSetNumberAuthorityDomain.valid(load("215"),
                    KotlinJournalSetOutcome.COMPLETED,actual(text)))
        }
        // This envelope is shared, but an accepted performed set never
        // creates automatic permission to issue any new weight.
        val recorded=KotlinSetNumberAuthorityDomain.review(load(),
            KotlinJournalSetOutcome.COMPLETED,actual("100000.0000"))
            as KotlinSetNumberReview.Accepted
        assertFalse(recorded.fact.mayModifyIssuedTarget)
        assertFalse(recorded.fact.mayChangeFutureProgram)
    }
}
