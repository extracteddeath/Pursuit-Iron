package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class KotlinWorkoutJournalReplayDomainTest {
    private val source=KotlinTrainingSourceRevision(
        "athlete-1",4,"program-1",8,"gym-1",3,"catalog-5","coach-rules-2")
    private val snapshot=KotlinStagedWorkout(
        source,"grant-1","session-1",0,0,ComposerSessionPurpose.HYPERTROPHY,30,
        listOf(
            KotlinWorkoutTarget("db-press",ComposerRole.PRIMARY,
                ComposerSessionPurpose.HYPERTROPHY,"set-1",DraftSetType.WORKING,0,
                DraftSetLoad.Verified(LoadUnit.LB,LoadBasis.DUMBBELL_PER_HAND,
                    BigDecimal("30")),RepBand(8,12),2,60,120),
            KotlinWorkoutTarget("db-press",ComposerRole.PRIMARY,
                ComposerSessionPurpose.HYPERTROPHY,"set-2",DraftSetType.WORKING,1,
                DraftSetLoad.Verified(LoadUnit.LB,LoadBasis.DUMBBELL_PER_HAND,
                    BigDecimal("30")),RepBand(8,12),2,60,120)
        ))
    private val identity=KotlinStoredWorkoutIdentity(
        "storage-commit-1","session-1",source,1)
    private fun record(id:String,rev:Long,key:String,reps:Int,corrects:String?=null)=
        KotlinJournalOperation.RecordSet(id,rev,key,KotlinJournalSetOutcome.COMPLETED,
            KotlinPerformedSet(BigDecimal("30"),LoadUnit.LB,reps,2),corrects)
    private fun applied(j:KotlinWorkoutJournalState,op:KotlinJournalOperation)=
        (KotlinWorkoutJournalDomain.reduce(j,op) as KotlinJournalDecision.Proposed).state
    private fun initial()=KotlinWorkoutJournalDomain.begin(snapshot,identity)
    private fun complete():KotlinWorkoutJournalState {
        val one=applied(initial(),record("e1",0,"set-1",10))
        val two=applied(one,record("e2",1,"set-2",9))
        return applied(two,KotlinJournalOperation.Finish("finish",2))
    }

    @Test fun replaysRealCommittedReceiptsAndPreservesOriginalTargets() {
        val original=complete()
        val recovered=KotlinWorkoutJournalReplayDomain.replay(
            snapshot,identity,original.receipts,3,"finish")
        assertEquals(KotlinJournalReplayResult.Recovered(original),recovered)
        assertEquals(KotlinJournalReplayResult.Recovered(original),
            KotlinWorkoutJournalReplayDomain.verifyStoredState(original))
        assertEquals(BigDecimal("30"),
            (snapshot.targets.first().load as DraftSetLoad.Verified).value)
        assertEquals("finish",original.finishedByEventId)
        assertEquals(2,original.resolvedSetCount)
    }

    @Test fun rejectsMissingDuplicatedOutOfOrderOrRewrittenReceipts() {
        val j=complete()
        val one=j.receipts[0]
        val two=j.receipts[1]
        val finish=j.receipts[2]
        fun rejected(receipts:List<KotlinJournalReceipt>,rev:Long,
            expected:String?="finish")=
            KotlinWorkoutJournalReplayDomain.replay(snapshot,identity,receipts,rev,expected)
                as KotlinJournalReplayResult.Rejected
        assertEquals(KotlinJournalReplayRefusal.NONCONTIGUOUS_REVISION,
            rejected(listOf(one,finish),3).reason)
        assertEquals(KotlinJournalReplayRefusal.NONCONTIGUOUS_REVISION,
            rejected(listOf(one,two.copy(appliedRevision=3),finish),3).reason)
        assertEquals(KotlinJournalReplayRefusal.NONCONTIGUOUS_REVISION,
            rejected(listOf(two,one,finish),3).reason)
        assertEquals(KotlinJournalReplayRefusal.INVALID_OPERATION,
            rejected(listOf(one,one.copy(appliedRevision=2),finish),3).reason)
        val collision=two.copy(operation=record("e1",1,"set-2",9))
        assertEquals(KotlinJournalReplayRefusal.INVALID_OPERATION,
            rejected(listOf(one,collision,finish),3).reason)
        val edited=two.copy(operation=record("e2",1,"set-1",9))
        assertEquals(KotlinJournalReplayRefusal.INVALID_OPERATION,
            rejected(listOf(one,edited,finish),3).reason)
        assertEquals(KotlinJournalReplayRefusal.STORED_STATE_MISMATCH,
            rejected(j.receipts,3,null).reason)
        assertEquals(KotlinJournalReplayRefusal.INVALID_ISSUED_IDENTITY,
            KotlinWorkoutJournalReplayDomain.replay(snapshot,
                identity.copy(workoutId="wrong"),j.receipts,3,"finish")
                .let { (it as KotlinJournalReplayResult.Rejected).reason })
    }

    @Test fun correctionsReplayWithoutDeletingOldObservations() {
        val one=applied(initial(),record("e1",0,"set-1",10))
        val corrected=applied(one,record("fix-1",1,"set-1",8,"e1"))
        assertEquals(KotlinJournalReplayResult.Recovered(corrected),
            KotlinWorkoutJournalReplayDomain.replay(
                snapshot,identity,corrected.receipts,2))
        assertEquals(2,corrected.receipts.size)
        assertEquals("fix-1",corrected.effectiveSets.getValue("set-1").operation.eventId)
        assertEquals("e1",corrected.receipts.first().operation.eventId)
    }

    @Test fun denormalizedEffectiveSetMapCannotOverrideJournalReceipts() {
        val j=complete()
        val forged=j.copy(effectiveSets=emptyMap())
        assertEquals(KotlinJournalReplayResult.Rejected(
            KotlinJournalReplayRefusal.STORED_STATE_MISMATCH),
            KotlinWorkoutJournalReplayDomain.verifyStoredState(forged))
        val forgedRevision=j.copy(revision=2)
        assertEquals(KotlinJournalReplayRefusal.NONCONTIGUOUS_REVISION,
            (KotlinWorkoutJournalReplayDomain.verifyStoredState(forgedRevision)
                as KotlinJournalReplayResult.Rejected).reason)
        val forgedFinish=j.copy(finishedByEventId=null)
        assertEquals(KotlinJournalReplayRefusal.STORED_STATE_MISMATCH,
            (KotlinWorkoutJournalReplayDomain.verifyStoredState(forgedFinish)
                as KotlinJournalReplayResult.Rejected).reason)
        assertEquals(0,initial().revision)
        assertFalse(initial().persisted)
    }

    @Test fun invalidReceiptOperationsCannotBecomeStoredHistory() {
        val valid=applied(initial(),record("e1",0,"set-1",10))
        val impossible=KotlinJournalReceipt(2,
            KotlinJournalOperation.Finish("early",1))
        assertEquals(KotlinJournalReplayRefusal.INVALID_OPERATION,
            (KotlinWorkoutJournalReplayDomain.replay(snapshot,identity,
                valid.receipts+impossible,2) as KotlinJournalReplayResult.Rejected).reason)
        val fake=KotlinJournalReceipt(1,record("e1",0,"unknown",10))
        assertEquals(KotlinJournalReplayRefusal.INVALID_OPERATION,
            (KotlinWorkoutJournalReplayDomain.replay(snapshot,identity,
                listOf(fake),1) as KotlinJournalReplayResult.Rejected).reason)
    }
}
