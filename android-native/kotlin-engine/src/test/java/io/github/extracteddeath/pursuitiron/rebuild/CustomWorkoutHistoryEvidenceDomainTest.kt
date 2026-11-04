package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class CustomWorkoutHistoryEvidenceDomainTest {
    private val mainKey="cycle-1:w1:d0:slot-1:s1"
    private val main=CustomWorkoutVisitKey(mainKey,CustomWorkoutVisitPart.MAIN)
    private val follow=CustomWorkoutVisitKey(mainKey,CustomWorkoutVisitPart.FOLLOW_UP)
    private fun draft():CustomWorkoutJournalDraft {
        val work=CustomAuthoredSet(DraftSetType.WORKING,
            DraftSetLoad.Verified(LoadUnit.LB,LoadBasis.DUMBBELL_PER_HAND,
                BigDecimal("20")),RepBand(10,15),2,60,90,
            CustomSetAmplifier(CustomAmplifierKind.DROP_SET,6,15,
                DraftSetLoad.Verified(LoadUnit.LB,
                    LoadBasis.DUMBBELL_PER_HAND,BigDecimal("15"))))
        val snapshot=CustomWorkoutDaySnapshot(
            CustomWorkoutSourceStamp("athlete-1","revision-1",
                "cycle-1",2,"gym-1",5),
            1,1,"block-1",TrainingPhase.HYPERTROPHY,
            CustomBlockPurpose.TRAINING,
            CustomSplitIdentity.Freeform("My custom"),
            0,"Upper",ComposerSessionPurpose.HYPERTROPHY,
            listOf(CustomWorkoutSlotSnapshot(
                "slot-1","db_lateral_raise","lateral_raise",
                "dumbbell",ComposerRole.ACCESSORY,
                null,null,true,"Strict",
                listOf(CustomWorkoutSetSnapshot(
                    mainKey,"slot-1",1,work)))),
            225,180)
        return CustomWorkoutJournalDomain.begin(
            "workout-1",snapshot,"a".repeat(64))
    }
    private fun accept(
        state:CustomWorkoutJournalDraft,
        op:CustomWorkoutJournalOperation
    )=(CustomWorkoutJournalDomain.reduce(state,op)
        as CustomWorkoutJournalDecision.Proposed).draft
    private fun record(
        id:String,rev:Long,visit:CustomWorkoutVisitKey,
        outcome:KotlinJournalSetOutcome,
        actual:KotlinPerformedSet?
    )=CustomWorkoutJournalOperation.Record(
        id,rev,visit,outcome,actual)

    @Test fun exactMainAndDropFactsRetainSeparateLoadsAndActualRir() {
        val first=accept(draft(),record("main-1",0,main,
            KotlinJournalSetOutcome.COMPLETED,
            KotlinPerformedSet(BigDecimal("20"),LoadUnit.LB,13,null)))
        val second=accept(first,record("drop-1",1,follow,
            KotlinJournalSetOutcome.COMPLETED,
            KotlinPerformedSet(BigDecimal("15"),LoadUnit.LB,6,0)))
        val finished=accept(second,CustomWorkoutJournalOperation.Finish(
            "finish-1",2))
        val result=CustomWorkoutHistoryEvidenceDomain.project(finished)
        assertTrue(result.toString(),result is CustomHistoryEvidenceResult.Candidate)
        val facts=(result as CustomHistoryEvidenceResult.Candidate).evidence
        assertEquals(2,facts.visits.size)
        assertEquals(listOf(CustomWorkoutVisitPart.MAIN,
            CustomWorkoutVisitPart.FOLLOW_UP),facts.visits.map{it.part})
        assertEquals(BigDecimal("20"),
            (facts.visits[0].prescribedLoad as DraftSetLoad.Verified).value)
        assertEquals(BigDecimal("15"),
            (facts.visits[1].prescribedLoad as DraftSetLoad.Verified).value)
        assertEquals(2,facts.visits[0].prescribedRir)
        assertNull(facts.visits[0].actual?.actualRir)
        assertEquals(0,facts.visits[1].actual?.actualRir)
        assertEquals(6,facts.visits[1].actual?.reps)
        assertEquals(CustomAmplifierKind.DROP_SET,facts.visits[1].amplifierKind)
        assertFalse(facts.canPublishGlobalHistory)
        assertFalse(facts.canApplyProgression)
    }

    @Test fun failedMainDoesNotInventFollowUpCompletedWork() {
        val failed=accept(draft(),record("failed",0,main,
            KotlinJournalSetOutcome.FAILED_ATTEMPT,
            KotlinPerformedSet(BigDecimal("20"),LoadUnit.LB,0,null)))
        val finished=accept(failed,
            CustomWorkoutJournalOperation.Finish("finish",1))
        val result=CustomWorkoutHistoryEvidenceDomain.project(finished)
        assertTrue(result is CustomHistoryEvidenceResult.Candidate)
        val facts=(result as CustomHistoryEvidenceResult.Candidate).evidence.visits
        assertEquals(1,facts.size)
        assertEquals(KotlinJournalSetOutcome.FAILED_ATTEMPT,
            facts.single().outcome)
        assertEquals(CustomWorkoutVisitPart.MAIN,facts.single().part)
    }

    @Test fun uncommittedOrMutatedReceiptsCannotBecomeHistoricalEvidence() {
        val initial=draft()
        assertEquals(CustomHistoryEvidenceResult.Rejected(
            CustomHistoryEvidenceRefusal.UNFINISHED),
            CustomWorkoutHistoryEvidenceDomain.project(initial))
        val main=accept(initial,record("main-1",0,this.main,
            KotlinJournalSetOutcome.COMPLETED,
            KotlinPerformedSet(BigDecimal("20"),LoadUnit.LB,12,1)))
        val after=accept(main,record("follow-1",1,follow,
            KotlinJournalSetOutcome.SKIPPED,null))
        val complete=accept(after,
            CustomWorkoutJournalOperation.Finish("finish",2))
        assertEquals(CustomHistoryEvidenceResult.Rejected(
            CustomHistoryEvidenceRefusal.UNVERIFIED_JOURNAL),
            CustomWorkoutHistoryEvidenceDomain.project(
                complete.copy(effectiveVisits=emptyMap())))
    }
}
