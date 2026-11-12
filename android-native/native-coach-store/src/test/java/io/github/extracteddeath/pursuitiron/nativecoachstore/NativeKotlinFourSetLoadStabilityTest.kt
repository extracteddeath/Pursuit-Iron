package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Regression contract for #518: a repeated poor set may result in optional
 * advice, but no reducer, cold codec or native journal may silently edit the
 * FOUR immutable issued working targets. These are real native receipts, not
 * snapshots mocked after the fact. UI/device acceptance remains separate.
 */
class NativeKotlinFourSetLoadStabilityTest {
    @get:Rule val temp=TemporaryFolder()
    private fun n(raw:String)=BigDecimal(raw)
    private val load=DraftSetLoad.Verified(
        LoadUnit.LB,LoadBasis.SMITH_TOTAL,n("215"))
    private val stock=VerifiedLoadStock.Totals(
        LoadUnit.LB,LoadBasis.SMITH_TOTAL,
        listOf(n("195"),n("200"),n("205"),n("210"),n("215")))
    private val source=KotlinTrainingSourceRevision(
        "athlete-1",2,"program-1",1,"gym-1",1,"catalog-1","coach-1")

    private fun generatedSnapshot(
        loads:List<String> =listOf("215","215","215","215")
    ):KotlinStagedWorkout {
        val targets=loads.mapIndexed{ordinal,value->
            KotlinWorkoutTarget("smith-press",ComposerRole.PRIMARY,
                ComposerSessionPurpose.STRENGTH_SKILL,
                "smith-press:s$ordinal",DraftSetType.WORKING,ordinal,
                DraftSetLoad.Verified(LoadUnit.LB,LoadBasis.SMITH_TOTAL,n(value)),
                RepBand(5,8),2,60,120)
        }
        return KotlinStagedWorkout(source,"grant-stability","workout-stability",
            0,0,ComposerSessionPurpose.STRENGTH_SKILL,50,targets)
    }
    private val issuedIdentity=KotlinStoredWorkoutIdentity(
        "receipt-stability","workout-stability",source,1)
    private fun fileStore()=KotlinNativeJournalFileStore(temp.root.toPath())
    private fun native(result:NativeJournalFileResult):KotlinWorkoutJournalState {
        assertTrue(result.toString(),result is NativeJournalFileResult.Durable)
        return (result as NativeJournalFileResult.Durable).journal
    }
    private fun expectedLoads(journal:KotlinWorkoutJournalState)=
        journal.snapshot.targets.map{
            (it.load as DraftSetLoad.Verified).value
        }

    @Test fun fourBadGeneratedSetsKeepAll215TargetsThroughAdviceColdOpenAndFinish() {
        val snapshot=generatedSnapshot()
        val store=fileStore()
        var committed=native(store.issue("owner-1",snapshot,issuedIdentity))
        assertEquals(List(4){n("215")},expectedLoads(committed))
        for(index in 0..3) {
            val key="smith-press:s$index"
            if(index>0) {
                val advice=KotlinJournalLiveSetCoachDomain.review(
                    committed,key,stock,CoachReadiness.NORMAL,false,n("5"))
                    as LiveSetCoachResult.Reviewed
                assertEquals(InWorkoutDirection.REVIEW_SMALLER_NEXT_LOAD,
                    advice.coaching.direction)
                assertEquals(n("210"),advice.coaching.weightForHumanReview)
                assertEquals(n("215"),advice.coaching.issuedTargetUnchanged)
                assertFalse(advice.coaching.canApplyAutomatically)
                assertFalse(advice.canModifyWorkout)
                assertEquals(LiveSetCoachResult.Unavailable(
                    LiveSetCoachUnavailable.ASSISTANCE_DISABLED),
                    KotlinJournalLiveSetCoachDomain.review(
                        committed,key,stock,CoachReadiness.NORMAL,false,n("5"),
                        assistance=CoachAssistancePolicy.MANUAL))
                assertEquals(List(4){n("215")},expectedLoads(committed))
            }
            val observation=KotlinJournalOperation.RecordSet(
                "under-floor-$index",committed.revision,key,
                KotlinJournalSetOutcome.COMPLETED,
                KotlinPerformedSet(n("215"),LoadUnit.LB,2,0))
            committed=native(store.append("owner-1",snapshot,
                issuedIdentity,observation))
            val fresh=native(fileStore().open("owner-1",snapshot,issuedIdentity))
            assertEquals(committed,fresh)
            assertEquals(snapshot,fresh.snapshot)
            assertEquals(List(4){n("215")},expectedLoads(fresh))
            val seen=KotlinWorkoutPresentationDomain.inspect(fresh)
                .exercises.single().sets
            assertEquals(List(4){n("215")},
                seen.map{(it.target.load as DraftSetLoad.Verified).value})
            assertEquals(index+1,seen.count{it.actual!=null})
            assertTrue(seen.take(index+1).all{
                it.actual?.load==n("215") && it.actual?.reps==2 &&
                    it.actual?.actualRir==0
            })
            committed=fresh
        }
        val finished=native(fileStore().append("owner-1",snapshot,
            issuedIdentity,KotlinJournalOperation.Finish(
                "finish-stability",committed.revision)))
        assertEquals(snapshot,finished.originalSnapshot)
        assertEquals(List(4){n("215")},expectedLoads(finished))
        assertTrue(KotlinWorkoutHistoryEvidenceDomain.project(finished)
            is KotlinHistoryProjectionResult.Candidate)
        assertEquals(finished,native(fileStore().open(
            "owner-1",snapshot,issuedIdentity)))
    }

    @Test fun explicitDifferentActualIsNotAHiddenProgramReduction() {
        val snapshot=generatedSnapshot()
        val store=fileStore()
        var journal=native(store.issue("owner-1",snapshot,issuedIdentity))
        journal=native(store.append("owner-1",snapshot,issuedIdentity,
            KotlinJournalOperation.RecordSet("user-edited-actual",0,
                "smith-press:s0",KotlinJournalSetOutcome.COMPLETED,
                KotlinPerformedSet(n("210"),LoadUnit.LB,5,1))))
        val advice=KotlinJournalLiveSetCoachDomain.review(journal,
            "smith-press:s1",stock,CoachReadiness.NORMAL,false,n("5"))
            as LiveSetCoachResult.Reviewed
        assertEquals(InWorkoutDirection.REVIEW_AUTHORED_LOAD_CHANGE,
            advice.coaching.direction)
        assertNull(advice.coaching.weightForHumanReview)
        assertEquals(n("215"),advice.coaching.issuedTargetUnchanged)
        assertTrue(advice.coaching.message.contains("previous set"))
        assertFalse(advice.canModifyWorkout)
        val restored=native(fileStore().open("owner-1",snapshot,issuedIdentity))
        assertEquals(snapshot,restored.snapshot)
        assertEquals(n("210"),(restored.effectiveSets.getValue("smith-press:s0")
            .operation as KotlinJournalOperation.RecordSet).performed?.load)
        assertEquals(List(4){n("215")},expectedLoads(restored))
    }

    private fun customSnapshot(
        loads:List<String> =listOf("215","215","215","215")
    ):CustomWorkoutDaySnapshot {
        val sets=loads.mapIndexed{ordinal,value->
            CustomWorkoutSetSnapshot("slot-a:s$ordinal","slot-a",
                ordinal+1,CustomAuthoredSet(DraftSetType.WORKING,
                    DraftSetLoad.Verified(LoadUnit.LB,LoadBasis.SMITH_TOTAL,n(value)),
                    RepBand(5,8),2,60,120))
        }
        val slot=CustomWorkoutSlotSnapshot("slot-a","smith-press","press",
            "smith",ComposerRole.PRIMARY,null,null,false,"",sets)
        return CustomWorkoutDaySnapshot(
            CustomWorkoutSourceStamp("athlete-1","profile-1","cycle-1",1,
                "gym-1",1),
            1,1,"base",TrainingPhase.STRENGTH,CustomBlockPurpose.TRAINING,
            CustomSplitIdentity.Freeform("My split"),0,"Upper",
            ComposerSessionPurpose.STRENGTH_SKILL,listOf(slot),1080,900)
    }
    private fun coldCustom(journal:CustomWorkoutJournalDraft):
        CustomWorkoutJournalDraft {
        val bytes=NativeCustomWorkoutJournalCodec.encode(
            NativeCustomWorkoutJournalRecord("owner-1",journal))
        val copy=NativeCustomWorkoutJournalCodec.decode(bytes)
        assertEquals("owner-1",copy.ownerKey)
        assertEquals(journal,copy.journal)
        return copy.journal
    }
    private fun customLoads(journal:CustomWorkoutJournalDraft)=
        journal.snapshot.slots.single().sets.map{
            (it.prescription.load as DraftSetLoad.Verified).value
        }

    @Test fun fourBadCustomSetsKeepAuthoredLoadsThroughReceiptReplayAndMachineScope() {
        val issued=customSnapshot()
        var journal=coldCustom(CustomWorkoutJournalDomain.begin(
            "custom-stability",issued,"a".repeat(64)))
        assertEquals(List(4){n("215")},customLoads(journal))
        for(index in 0..3) {
            val key="slot-a:s$index"
            if(index>0) {
                val advice=KotlinJournalLiveSetCoachDomain.review(journal,key,
                    stock,CoachReadiness.NORMAL,false,n("5"),
                    machineScope=LiveMachineCoachScope.Physical("A"))
                    as LiveSetCoachResult.Reviewed
                assertEquals(InWorkoutDirection.REVIEW_SMALLER_NEXT_LOAD,
                    advice.coaching.direction)
                assertEquals(n("210"),advice.coaching.weightForHumanReview)
                assertEquals(n("215"),advice.coaching.issuedTargetUnchanged)
                assertFalse(advice.canModifyWorkout)
                assertEquals(LiveSetCoachResult.Unavailable(
                    LiveSetCoachUnavailable.PHYSICAL_MACHINE_HISTORY_MISMATCH),
                    KotlinJournalLiveSetCoachDomain.review(journal,key,
                        stock,CoachReadiness.NORMAL,false,n("5"),
                        machineScope=LiveMachineCoachScope.Physical("B")))
                assertEquals(LiveSetCoachResult.Unavailable(
                    LiveSetCoachUnavailable.ASSISTANCE_DISABLED),
                    KotlinJournalLiveSetCoachDomain.review(journal,key,
                        stock,CoachReadiness.NORMAL,false,n("5"),
                        assistance=CoachAssistancePolicy.MANUAL,
                        machineScope=LiveMachineCoachScope.Physical("A")))
            }
            val event=CustomWorkoutJournalOperation.Record(
                "custom-failure-$index",journal.revision,
                CustomWorkoutVisitKey(key,CustomWorkoutVisitPart.MAIN),
                KotlinJournalSetOutcome.COMPLETED,
                KotlinPerformedSet(n("215"),LoadUnit.LB,2,0,"A"))
            val applied=CustomWorkoutJournalDomain.reduce(journal,event)
            assertTrue(applied.toString(),applied is CustomWorkoutJournalDecision.Proposed)
            journal=coldCustom((applied as CustomWorkoutJournalDecision.Proposed).draft)
            assertEquals(issued,journal.originalSnapshot)
            assertEquals(List(4){n("215")},customLoads(journal))
        }
        assertEquals(4,journal.effectiveVisits.size)
        assertTrue(journal.effectiveVisits.values.all{
            (it.operation as CustomWorkoutJournalOperation.Record)
                .performed?.load==n("215")
        })
    }

    @Test fun mixedCustomTargetsCannotBeRelabeledAsAnInferredBackoff() {
        val issued=customSnapshot(listOf("215","215","205","205"))
        var journal=coldCustom(CustomWorkoutJournalDomain.begin(
            "custom-backoff-unlabeled",issued,"b".repeat(64)))
        val original=journal.originalSnapshot
        val event=CustomWorkoutJournalOperation.Record(
            "first-top-set",journal.revision,
            CustomWorkoutVisitKey("slot-a:s0",CustomWorkoutVisitPart.MAIN),
            KotlinJournalSetOutcome.COMPLETED,
            KotlinPerformedSet(n("215"),LoadUnit.LB,2,0))
        journal=coldCustom((CustomWorkoutJournalDomain.reduce(journal,event)
            as CustomWorkoutJournalDecision.Proposed).draft)
        assertEquals(LiveSetCoachResult.Unavailable(
            LiveSetCoachUnavailable.UNVERIFIED_OR_MIXED_SET_SCHEME),
            KotlinJournalLiveSetCoachDomain.review(journal,"slot-a:s1",stock,
                CoachReadiness.NORMAL,false,n("5")))
        assertEquals(original,journal.originalSnapshot)
        assertEquals(listOf(n("215"),n("215"),n("205"),n("205")),
            customLoads(journal))
        assertFalse(journal.finishedByEventId!=null)
    }
}
