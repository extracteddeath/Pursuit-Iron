package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.io.IOException
import org.junit.Assert.*
import org.junit.Test

class NativeCustomWorkoutJournalCodecTest {
    private val key="cycle-1:w1:d0:raise-1:s1"
    private fun snapshot():CustomWorkoutDaySnapshot {
        val authored=CustomAuthoredSet(
            DraftSetType.WORKING,DraftSetLoad.ExplicitBodyweight,
            RepBand(8,12),2,45,90,
            CustomSetAmplifier(CustomAmplifierKind.MYO_REPS,4,20))
        val slot=CustomWorkoutSlotSnapshot(
            "raise-1","bodyweight_crunch","trunk_flexion",
            "bodyweight",ComposerRole.ACCESSORY,null,null,
            true,"Controlled movement",
            listOf(CustomWorkoutSetSnapshot(key,"raise-1",1,authored)))
        return CustomWorkoutDaySnapshot(
            CustomWorkoutSourceStamp("athlete-1","profile-1",
                "cycle-1",4,"gym-1",3),
            1,1,"block-1",TrainingPhase.HYPERTROPHY,
            CustomBlockPurpose.TRAINING,
            CustomSplitIdentity.Freeform("Custom bodyweight"),
            0,"Full A",ComposerSessionPurpose.HYPERTROPHY,
            listOf(slot),155,300)
    }
    private fun initial()=CustomWorkoutJournalDomain.begin(
        "workout-1",snapshot(),"a".repeat(64))
    private fun actual(reps:Int,rir:Int?=null)=
        KotlinPerformedSet(null,null,reps,rir)
    private fun event(event:String,rev:Long,part:CustomWorkoutVisitPart,
        reps:Int,actualRir:Int?=null)=CustomWorkoutJournalOperation.Record(
        event,rev,CustomWorkoutVisitKey(key,part),
        KotlinJournalSetOutcome.COMPLETED,actual(reps,actualRir))
    private fun accept(
        state:CustomWorkoutJournalDraft,
        op:CustomWorkoutJournalOperation
    )=(CustomWorkoutJournalDomain.reduce(state,op)
        as CustomWorkoutJournalDecision.Proposed).draft
    private fun record(state:CustomWorkoutJournalDraft)=
        NativeCustomWorkoutJournalRecord("owner-1",state)

    @Test fun coldReopenReplaysIndependentActualRirAndFollowupData() {
        val main=accept(initial(),event("event-main",0,
            CustomWorkoutVisitPart.MAIN,10,null))
        val amp=accept(main,event("event-myo",1,
            CustomWorkoutVisitPart.FOLLOW_UP,4,0))
        val finished=accept(amp,
            CustomWorkoutJournalOperation.Finish("event-finish",2))
        val bytes=NativeCustomWorkoutJournalCodec.encode(record(finished))
        val recovered=NativeCustomWorkoutJournalCodec.decode(bytes)
        assertEquals(record(finished),recovered)
        assertEquals(bytes.toList(),
            NativeCustomWorkoutJournalCodec.encode(recovered).toList())
        val mainReceipt=recovered.journal.effectiveVisits.getValue(
            CustomWorkoutVisitKey(key,CustomWorkoutVisitPart.MAIN))
        assertNull((mainReceipt.operation as
            CustomWorkoutJournalOperation.Record).performed?.actualRir)
        assertEquals(2,recovered.journal.snapshot.slots.single()
            .sets.single().prescription.targetRir)
        val ampReceipt=recovered.journal.effectiveVisits.getValue(
            CustomWorkoutVisitKey(key,CustomWorkoutVisitPart.FOLLOW_UP))
        assertEquals(4,(ampReceipt.operation as
            CustomWorkoutJournalOperation.Record).performed?.reps)
        assertEquals(0,(ampReceipt.operation as
            CustomWorkoutJournalOperation.Record).performed?.actualRir)
        assertFalse(recovered.journal.persisted)
        assertFalse(recovered.journal.mayWriteHistory)
        assertEquals("event-finish",recovered.journal.finishedByEventId)
    }

    @Test fun pendingAmplifierRemainsPendingAcrossColdRoundtrip() {
        val afterMain=accept(initial(),event("main",0,
            CustomWorkoutVisitPart.MAIN,10,2))
        val loaded=NativeCustomWorkoutJournalCodec.decode(
            NativeCustomWorkoutJournalCodec.encode(record(afterMain)))
        assertEquals(1,loaded.journal.unresolvedVisitCount)
        assertFalse(loaded.journal.canProposeFinish)
        val next=CustomWorkoutJournalDomain.reduce(loaded.journal,
            CustomWorkoutJournalOperation.Finish("premature-finish",1))
        assertEquals(CustomWorkoutJournalDecision.Rejected(
            CustomWorkoutJournalRefusal.INCOMPLETE_WORKOUT),next)
    }

    @Test fun alteredReceiptOrderAndCachedStateAreNeverSaved() {
        val main=accept(initial(),event("main",0,
            CustomWorkoutVisitPart.MAIN,10))
        val amp=accept(main,event("amp",1,
            CustomWorkoutVisitPart.FOLLOW_UP,4))
        assertThrows(IOException::class.java) {
            NativeCustomWorkoutJournalCodec.encode(record(
                amp.copy(receipts=amp.receipts.reversed())))
        }
        assertThrows(IllegalArgumentException::class.java) {
            NativeCustomWorkoutJournalCodec.encode(record(
                amp.copy(effectiveVisits=emptyMap())))
        }
        assertThrows(IOException::class.java) {
            NativeCustomWorkoutJournalCodec.encode(record(
                amp.copy(finishedByEventId="invented-finish")))
        }
        assertThrows(IOException::class.java) {
            NativeCustomWorkoutJournalCodec.encode(record(
                amp.copy(revision=78)))
        }
    }

    @Test fun truncatedUnknownVersionAndTrailingBytesAreRejected() {
        val bytes=NativeCustomWorkoutJournalCodec.encode(record(initial()))
        assertThrows(IOException::class.java) {
            NativeCustomWorkoutJournalCodec.decode(bytes.copyOf(bytes.size-1))
        }
        assertThrows(IOException::class.java) {
            NativeCustomWorkoutJournalCodec.decode(bytes+byteArrayOf(0))
        }
        val invalid=bytes.clone()
        invalid[7]=99
        assertThrows(IOException::class.java) {
            NativeCustomWorkoutJournalCodec.decode(invalid)
        }
    }
}
