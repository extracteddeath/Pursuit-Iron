package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class KotlinWorkoutJournalDomainTest {
    private fun n(raw:String)=BigDecimal(raw)
    private val source=KotlinTrainingSourceRevision(
        "athlete-1",4,"program-1",8,"gym-1",3,"catalog-5","coach-rules-2"
    )
    private val load=DraftSetLoad.Verified(
        LoadUnit.LB,LoadBasis.DUMBBELL_PER_HAND,n("30"))
    private fun target(
        key:String,type:DraftSetType=DraftSetType.WORKING,
        prescribed:DraftSetLoad=load,rir:Int?=2
    )=KotlinWorkoutTarget("db-press",ComposerRole.PRIMARY,
        ComposerSessionPurpose.HYPERTROPHY,key,type,0,
        prescribed,RepBand(8,12),rir,60,120)
    private val original=KotlinStagedWorkout(
        source,"grant-1","session-1",0,0,ComposerSessionPurpose.HYPERTROPHY,40,
        listOf(target("db-press:d0:w0",DraftSetType.WARMUP,rir=null),
            target("db-press:d0:s0"),
            target("db-press:d0:s1"),
            target("db-press:d0:s2")))
    private val receipt=KotlinStoredWorkoutIdentity(
        "storage-commit-1","session-1",source,1)
    private fun begin()=KotlinWorkoutJournalDomain.begin(original,receipt)
    private fun actual(reps:Int,rir:Int?=2,weight:String="30")=
        KotlinPerformedSet(n(weight),LoadUnit.LB,reps,rir)
    private fun record(id:String,revision:Long,key:String,kind:KotlinJournalSetOutcome,
        values:KotlinPerformedSet?,corrects:String?=null)=
        KotlinJournalOperation.RecordSet(id,revision,key,kind,values,corrects)
    private fun apply(j:KotlinWorkoutJournalState,op:KotlinJournalOperation):
        KotlinWorkoutJournalState {
        val accepted=KotlinWorkoutJournalDomain.reduce(j,op)
        assertTrue(accepted.toString(),accepted is KotlinJournalDecision.Proposed)
        return (accepted as KotlinJournalDecision.Proposed).state
    }
    @Test fun actualsStaySeparateFromWarmupAndWorkingPrescriptions() {
        val initial=begin()
        assertEquals(4,initial.unresolvedSetCount)
        assertEquals(0,initial.resolvedSetCount)
        assertFalse(initial.canProposeFinish)
        val w=record("event-warmup",0,"db-press:d0:w0",
            KotlinJournalSetOutcome.COMPLETED,actual(8,null,"25"))
        val wDone=apply(initial,w)
        assertEquals(1,wDone.revision)
        assertEquals(3,wDone.unresolvedSetCount)
        assertEquals(0,initial.revision)
        assertTrue(initial.effectiveSets.isEmpty())
        assertFalse(wDone.persisted)
        val typed=wDone.effectiveSets.getValue("db-press:d0:w0")
            .operation as KotlinJournalOperation.RecordSet
        assertEquals(n("25"),typed.performed?.load)
        assertNull(typed.performed?.actualRir)
        val prescribed=original.targets.first()
        assertEquals(n("30"),(prescribed.load as DraftSetLoad.Verified).value)
        assertNull(prescribed.targetRir)
        assertEquals(original,wDone.snapshot)
        assertEquals(KotlinJournalDecision.Rejected(KotlinJournalRefusal.INCOMPLETE_WORKOUT),
            KotlinWorkoutJournalDomain.reduce(wDone,
                KotlinJournalOperation.Finish("finish-1",1)))
    }

    @Test fun retriesAreIdempotentAcrossLostAcknowledgmentsAndRevisionChanges() {
        val original=begin()
        val w=record("same-request",0,"db-press:d0:s0",
            KotlinJournalSetOutcome.COMPLETED,actual(10))
        val next=apply(original,w)
        assertEquals(KotlinJournalDecision.Proposed(next,true),
            KotlinWorkoutJournalDomain.reduce(next,w))
        assertEquals(1,next.receipts.size)
        assertEquals(KotlinJournalDecision.Rejected(KotlinJournalRefusal.EVENT_ID_COLLISION),
            KotlinWorkoutJournalDomain.reduce(next,w.copy(performed=actual(12))))
        assertEquals(KotlinJournalDecision.Rejected(KotlinJournalRefusal.STALE_REVISION),
            KotlinWorkoutJournalDomain.reduce(next,
                record("new-id",0,"db-press:d0:s1",
                    KotlinJournalSetOutcome.COMPLETED,actual(10))))
        assertEquals(KotlinJournalDecision.Rejected(KotlinJournalRefusal.ALREADY_RECORDED),
            KotlinWorkoutJournalDomain.reduce(next,
                record("overwrite",1,"db-press:d0:s0",
                    KotlinJournalSetOutcome.COMPLETED,actual(12))))
        assertEquals(KotlinJournalDecision.Rejected(KotlinJournalRefusal.UNKNOWN_SET),
            KotlinWorkoutJournalDomain.reduce(next,
                record("unknown",1,"not-a-set",
                    KotlinJournalSetOutcome.SKIPPED,null)))
    }

    @Test fun explicitCorrectionSupersedesReceiptWithoutErasingOriginalHistory() {
        val first=apply(begin(),record("r1",0,"db-press:d0:s0",
            KotlinJournalSetOutcome.COMPLETED,actual(9,null,"30")))
        val wrong=record("r2",1,"db-press:d0:s0",
            KotlinJournalSetOutcome.COMPLETED,actual(10,1,"32.5"),"wrong")
        assertEquals(KotlinJournalDecision.Rejected(
            KotlinJournalRefusal.INVALID_CORRECTION),
            KotlinWorkoutJournalDomain.reduce(first,wrong))
        val correction=wrong.copy(correctsEventId="r1")
        val corrected=apply(first,correction)
        assertEquals(2,corrected.revision)
        assertEquals(1,corrected.resolvedSetCount)
        assertEquals(2,corrected.receipts.size)
        assertEquals("r1",corrected.receipts.first().operation.eventId)
        assertEquals("r2",corrected.effectiveSets.getValue("db-press:d0:s0")
            .operation.eventId)
        assertEquals(n("32.5"),(corrected.effectiveSets.getValue("db-press:d0:s0")
            .operation as KotlinJournalOperation.RecordSet).performed?.load)
        assertEquals(n("30"),(original.targets[1].load as DraftSetLoad.Verified).value)
        assertTrue(corrected.receipts.map {it.appliedRevision}==listOf(1L,2L))
        assertEquals(KotlinJournalDecision.Rejected(
            KotlinJournalRefusal.INVALID_CORRECTION),
            KotlinWorkoutJournalDomain.reduce(corrected,
                wrong.copy(eventId="r3",expectedJournalRevision=2,correctsEventId="r1")))
        assertEquals(KotlinJournalDecision.Proposed(corrected,true),
            KotlinWorkoutJournalDomain.reduce(corrected,correction))
    }

    @Test fun skipFailureAndFinishPreserveOutcomeSemantics() {
        var j=begin()
        j=apply(j,record("w",0,"db-press:d0:w0",
            KotlinJournalSetOutcome.SKIPPED,null))
        j=apply(j,record("f",1,"db-press:d0:s0",
            KotlinJournalSetOutcome.FAILED_ATTEMPT,actual(0,null,"30")))
        j=apply(j,record("s1",2,"db-press:d0:s1",
            KotlinJournalSetOutcome.COMPLETED,actual(8,1)))
        j=apply(j,record("s2",3,"db-press:d0:s2",
            KotlinJournalSetOutcome.COMPLETED,actual(10,2)))
        assertTrue(j.canProposeFinish)
        assertEquals(0,j.unresolvedSetCount)
        val finish=KotlinJournalOperation.Finish("finish-1",4)
        val finished=apply(j,finish)
        assertEquals("finish-1",finished.finishedByEventId)
        assertEquals(5,finished.revision)
        assertFalse(finished.canProposeFinish)
        assertEquals(KotlinJournalDecision.Proposed(finished,true),
            KotlinWorkoutJournalDomain.reduce(finished,finish))
        assertEquals(KotlinJournalDecision.Rejected(KotlinJournalRefusal.FINISHED),
            KotlinWorkoutJournalDomain.reduce(finished,
                record("late-edit",5,"db-press:d0:s0",
                    KotlinJournalSetOutcome.COMPLETED,actual(8),"f")))
    }

    @Test fun cannotMisrecordSkipFailedAttemptsOrReportedEffort() {
        val j=begin()
        val invalid=listOf(
            record("bad-1",0,"db-press:d0:s0",KotlinJournalSetOutcome.COMPLETED,
                actual(0)),
            record("bad-2",0,"db-press:d0:s0",KotlinJournalSetOutcome.COMPLETED,
                actual(12,11)),
            record("bad-3",0,"db-press:d0:s0",KotlinJournalSetOutcome.COMPLETED,
                KotlinPerformedSet(n("30"),LoadUnit.KG,10,2)),
            record("bad-4",0,"db-press:d0:s0",KotlinJournalSetOutcome.FAILED_ATTEMPT,
                actual(0,2)),
            record("bad-5",0,"db-press:d0:s0",KotlinJournalSetOutcome.SKIPPED,
                actual(8)),
            record("bad-6",0,"db-press:d0:s0",KotlinJournalSetOutcome.COMPLETED,
                actual(10,2,"159.5001")),
            record("bad-7",0,"db-press:d0:s0",KotlinJournalSetOutcome.COMPLETED,
                actual(8,2,"-5")),
            record("bad-8",0,"db-press:d0:s0",KotlinJournalSetOutcome.COMPLETED,
                null),
            record("bad-9",0,"db-press:d0:s0",KotlinJournalSetOutcome.COMPLETED,
                actual(101)),
            record("bad-10",0,"db-press:d0:s0",KotlinJournalSetOutcome.COMPLETED,
                actual(10,2,"100000.1"))
        )
        invalid.forEach { entry->
            assertEquals(entry.toString(),KotlinJournalDecision.Rejected(
                KotlinJournalRefusal.INVALID_VALUES),
                KotlinWorkoutJournalDomain.reduce(j,entry))
        }
        assertEquals(0,j.revision)
    }

    @Test fun bodyweightIsNeverSilentlyTranslatedIntoZeroPounds() {
        val bw=original.copy(targets=listOf(
            target("body:d0:s0",prescribed=DraftSetLoad.ExplicitBodyweight)))
        val j=KotlinWorkoutJournalDomain.begin(bw,receipt)
        assertEquals(KotlinJournalDecision.Rejected(KotlinJournalRefusal.INVALID_VALUES),
            KotlinWorkoutJournalDomain.reduce(j,
                record("fake-zero",0,"body:d0:s0",KotlinJournalSetOutcome.COMPLETED,
                    actual(10,2,"0"))))
        val performed=KotlinPerformedSet(null,null,12,null)
        val next=apply(j,record("actual-bw",0,"body:d0:s0",
            KotlinJournalSetOutcome.COMPLETED,performed))
        assertEquals(performed,(next.effectiveSets.getValue("body:d0:s0")
            .operation as KotlinJournalOperation.RecordSet).performed)
    }

    @Test fun stateCollectionsCannotBeMutatedAfterCommitProposal() {
        val next=apply(begin(),record("e1",0,"db-press:d0:s0",
            KotlinJournalSetOutcome.COMPLETED,actual(10)))
        assertThrows(UnsupportedOperationException::class.java) {
            @Suppress("UNCHECKED_CAST")
            (next.receipts as MutableList<KotlinJournalReceipt>).clear()
        }
        assertThrows(UnsupportedOperationException::class.java) {
            @Suppress("UNCHECKED_CAST")
            (next.effectiveSets as MutableMap<String,KotlinJournalReceipt>).clear()
        }
        assertEquals(1,next.revision)
        assertEquals(1,next.effectiveSets.size)
        assertThrows(IllegalArgumentException::class.java) {
            KotlinWorkoutJournalDomain.begin(original,
                receipt.copy(workoutId="foreign-workout"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            KotlinWorkoutJournalDomain.begin(original,
                receipt.copy(source=source.copy(programRevision=9)))
        }
    }
}
