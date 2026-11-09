package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.io.IOException
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
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

    @Test fun versionThreeSessionNotesSurviveColdReplayAndLegacyFormatIsUnchanged() {
        val original=initial()
        val legacy=NativeCustomWorkoutJournalCodec.encode(record(original))
        assertEquals(1,java.nio.ByteBuffer.wrap(legacy,4,4).int)
        val note=CustomWorkoutJournalOperation.SessionNote(
            "note-1",0,"Shoulder felt fine today")
        val withNote=accept(original,note)
        val bytes=NativeCustomWorkoutJournalCodec.encode(record(withNote))
        assertEquals(3,java.nio.ByteBuffer.wrap(bytes,4,4).int)
        val cold=NativeCustomWorkoutJournalCodec.decode(bytes)
        assertEquals("Shoulder felt fine today",cold.journal.sessionNote)
        assertEquals(withNote,cold.journal)
        assertEquals(bytes.toList(),
            NativeCustomWorkoutJournalCodec.encode(cold).toList())
        val cleared=accept(cold.journal,
            CustomWorkoutJournalOperation.SessionNote("clear-note",1,""))
        assertEquals("",NativeCustomWorkoutJournalCodec.decode(
            NativeCustomWorkoutJournalCodec.encode(record(cleared)))
            .journal.sessionNote)
        // This is a factual journal revision, not a change to authored work.
        assertEquals(original.snapshot,cleared.snapshot)
        assertEquals(original.unresolvedVisitCount,cleared.unresolvedVisitCount)
        assertEquals(legacy.toList(),
            NativeCustomWorkoutJournalCodec.encode(record(original)).toList())
        val truncated=bytes.copyOf(bytes.size-3)
        assertThrows(IOException::class.java) {
            NativeCustomWorkoutJournalCodec.decode(truncated)
        }
    }

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

    @Test fun swapReceiptRoundTripPreservesOriginalApprovalAndOtherActuals() {
        val origin=snapshot()
        val other=CustomWorkoutSlotSnapshot(
            "row-2","bodyweight_row","row","bodyweight",
            ComposerRole.ACCESSORY,null,null,true,"Other exercise",
            listOf(CustomWorkoutSetSnapshot(
                "cycle-1:w1:d0:row-2:s1","row-2",1,
                origin.slots.single().sets.single().prescription.copy(
                    amplifier=null)))
        )
        val initial=CustomWorkoutJournalDomain.begin("swap-workout",
            origin.copy(slots=origin.slots+other,
                knownSetAndRestSeconds=290L),"b".repeat(64))
        val prior=accept(initial,CustomWorkoutJournalOperation.Record(
            "other-record",0L,
            CustomWorkoutVisitKey("cycle-1:w1:d0:row-2:s1",
                CustomWorkoutVisitPart.MAIN),
            KotlinJournalSetOutcome.COMPLETED,actual(11,1)))
        val next=CustomWorkoutSlotSnapshot(
            "new-slot","bodyweight_pull","pull","bodyweight",
            ComposerRole.ACCESSORY,null,null,true,"New explicit targets",
            listOf(CustomWorkoutSetSnapshot(
                "cycle-1:w1:d0:new-slot:s1","new-slot",1,
                origin.slots.single().sets.single().prescription.copy(
                    amplifier=null)))
        )
        val swapped=accept(prior,CustomWorkoutJournalOperation.Swap(
            "event-swap",1L,"raise-1",next))
        val bytes=NativeCustomWorkoutJournalCodec.encode(record(swapped))
        assertEquals(2,java.nio.ByteBuffer.wrap(bytes,4,4).int)
        val recovered=NativeCustomWorkoutJournalCodec.decode(bytes)
        assertEquals(record(swapped),recovered)
        assertEquals(origin.slots.single().slotId,
            recovered.journal.originalSnapshot.slots.first().slotId)
        assertEquals("new-slot",recovered.journal.snapshot.slots.first().slotId)
        assertEquals(prior.effectiveVisits,recovered.journal.effectiveVisits)
        assertEquals(bytes.toList(),
            NativeCustomWorkoutJournalCodec.encode(recovered).toList())
        assertThrows(IllegalArgumentException::class.java) {
            NativeCustomWorkoutJournalCodec.encode(
                record(swapped.copy(snapshot=origin)))
        }
    }


    @Test fun legacyV1UnswappedJournalStillOpensWithoutMigratingFacts() {
        val initial=initial()
        val snapshot=NativeKotlinCustomWorkoutSnapshotCodec.encode(
            initial.snapshot)
        val out=ByteArrayOutputStream()
        DataOutputStream(out).use{data->
            data.writeInt(0x5049434A)
            data.writeInt(1)
            data.writeUTF("owner-1")
            data.writeUTF("workout-1")
            data.writeUTF(initial.preparedConsentFingerprint)
            data.writeInt(snapshot.size)
            data.write(snapshot)
            data.writeLong(0L)
            data.writeBoolean(false)
            data.writeInt(0)
        }
        val decoded=NativeCustomWorkoutJournalCodec.decode(out.toByteArray())
        assertEquals(record(initial),decoded)
        assertEquals(initial.snapshot,decoded.journal.originalSnapshot)
        val preserved=NativeCustomWorkoutJournalCodec.encode(decoded)
        assertArrayEquals(out.toByteArray(),preserved)
        assertEquals(1,java.nio.ByteBuffer.wrap(preserved,4,4).int)
        val upgraded=NativeCustomWorkoutJournalCodec.decode(preserved)
        assertEquals(decoded,upgraded)
    }


    @Test fun durableSwapUndoReplaysOriginalTargetsWithoutDowngradingJournalV1() {
        val approved=initial()
        val next=CustomWorkoutSlotSnapshot(
            "new-slot","bodyweight_pull","pull","bodyweight",
            ComposerRole.ACCESSORY,null,null,true,"Replacement",
            listOf(CustomWorkoutSetSnapshot(
                "cycle-1:w1:d0:new-slot:s1","new-slot",1,
                approved.snapshot.slots.single().sets.single().prescription
                    .copy(amplifier=null)))
        )
        val swapped=accept(approved,CustomWorkoutJournalOperation.Swap(
            "swap-once",0L,"raise-1",next))
        val undone=accept(swapped,CustomWorkoutJournalOperation.UndoSwap(
            "undo-once",1L,"swap-once"))
        assertEquals(approved.snapshot,undone.snapshot)
        assertEquals(approved.originalSnapshot,undone.originalSnapshot)
        assertEquals(2L,undone.revision)
        val bytes=NativeCustomWorkoutJournalCodec.encode(record(undone))
        // A journal with swap history must remain v2 even when its current
        // visible roster matches v1. Dropping receipt history would be unsafe.
        assertEquals(2,java.nio.ByteBuffer.wrap(bytes,4,4).int)
        val reopened=NativeCustomWorkoutJournalCodec.decode(bytes)
        assertEquals(record(undone),reopened)
        assertEquals(bytes.toList(),
            NativeCustomWorkoutJournalCodec.encode(reopened).toList())
        val originalBytes=NativeCustomWorkoutJournalCodec.encode(record(approved))
        assertEquals(1,java.nio.ByteBuffer.wrap(originalBytes,4,4).int)
        assertEquals(record(approved),
            NativeCustomWorkoutJournalCodec.decode(originalBytes))
    }

    @Test fun fabricatedSwapUndoCannotBeEncodedAsACompletedJournal() {
        val invalid=initial().copy(
            receipts=listOf(CustomWorkoutJournalReceipt(
                1,CustomWorkoutJournalOperation.UndoSwap(
                    "invented",0L,"never-swapped"))),
            revision=1L)
        assertThrows(IOException::class.java) {
            // encode's replay rejects invalid causal history before publishing.
            NativeCustomWorkoutJournalCodec.encode(record(invalid))
        }
    }
}
