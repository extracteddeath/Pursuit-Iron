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

    @Test fun adjustedCustomJournalReplaysAddedRowsButIssuedCodecStillRejectsThem() {
        val base=snapshot()
        val slot=base.slots.single()
        val first=slot.sets.single().copy(prescription=slot.sets.single().prescription.copy(amplifier=null))
        val second=first.copy(setKey="cycle-1:w1:d0:raise-1:s2",ordinalInSlot=2)
        val source=base.copy(slots=listOf(slot.copy(pinned=false,sets=listOf(first,second))),knownSetAndRestSeconds=270)
        var state=CustomWorkoutJournalDomain.begin("workout-1",source,"a".repeat(64))
        state=accept(state,event("main",0,CustomWorkoutVisitPart.MAIN,10))
        val before=state
        val edit=CustomWorkoutJournalOperation.AdjustRemaining("adjust",1,
            MidSessionWorkingEdit("workout-1",1,"raise-1",3,RepBand(10,15),150))
        val adjusted=accept(state,edit)
        assertEquals(source,adjusted.originalSnapshot)
        assertEquals(before.effectiveVisits,adjusted.effectiveVisits)
        assertEquals(first,adjusted.snapshot.slots.single().sets.first())
        assertEquals("mid-2-1",adjusted.snapshot.slots.single().sets.last().setKey)
        assertEquals(525L,adjusted.snapshot.knownSetAndRestSeconds)
        assertTrue(runCatching{NativeKotlinCustomWorkoutSnapshotCodec.encode(adjusted.snapshot)}.isFailure)
        val bytes=NativeCustomWorkoutJournalCodec.encode(record(adjusted))
        assertEquals(7,java.nio.ByteBuffer.wrap(bytes,4,4).int)
        val reopened=NativeCustomWorkoutJournalCodec.decode(bytes).journal
        assertEquals(adjusted,reopened)
        assertEquals(CustomWorkoutJournalDecision.Proposed(reopened,true),CustomWorkoutJournalDomain.reduce(reopened,edit))
        val restored=accept(reopened,CustomWorkoutJournalOperation.UndoAdjustment("undo",2,"adjust"))
        assertEquals(source,restored.snapshot)
        assertEquals(before.effectiveVisits,restored.effectiveVisits)
        assertEquals(restored,NativeCustomWorkoutJournalCodec.decode(NativeCustomWorkoutJournalCodec.encode(record(restored))).journal)
        val again=edit.copy(eventId="again",expectedRevision=3,request=edit.request.copy(expectedRevision=3))
        state=accept(restored,again)
        for(set in state.snapshot.slots.single().sets.drop(1)) {
            state=accept(state,CustomWorkoutJournalOperation.Record("record-${set.ordinalInSlot}",state.revision,
                CustomWorkoutVisitKey(set.setKey,CustomWorkoutVisitPart.MAIN),KotlinJournalSetOutcome.COMPLETED,actual(12)))
        }
        assertEquals(CustomWorkoutJournalDecision.Rejected(CustomWorkoutJournalRefusal.INVALID_ADJUSTMENT),
            CustomWorkoutJournalDomain.reduce(state,CustomWorkoutJournalOperation.UndoAdjustment("late",state.revision,"again")))
        state=accept(state,CustomWorkoutJournalOperation.Finish("finish",state.revision))
        val facts=(CustomWorkoutHistoryEvidenceDomain.project(state) as CustomHistoryEvidenceResult.Candidate).evidence
        assertTrue(facts.sessionAdjusted)
        assertTrue(facts.workingPrescriptionAdjusted)
        assertFalse(facts.allIssuedMainWorkingSetsCompleted)
        assertEquals(listOf(10,12,12),facts.visits.map{it.actual?.reps})
        assertEquals(state,NativeCustomWorkoutJournalCodec.decode(NativeCustomWorkoutJournalCodec.encode(record(state))).journal)
    }

    @Test fun firstWeightedWarmupReceiptKeepsExactMassAndShiftedWorkingIdentity() {
        val base=snapshot()
        val slot=base.slots.single()
        val load=DraftSetLoad.Verified(LoadUnit.LB,LoadBasis.MACHINE_STACK,java.math.BigDecimal("30.0"))
        val working=slot.sets.single().copy(prescription=slot.sets.single().prescription.copy(
            load=load,amplifier=null))
        val source=base.copy(slots=listOf(slot.copy(sets=listOf(working))),knownSetAndRestSeconds=135)
        val start=CustomWorkoutJournalDomain.begin("workout-1",source,"a".repeat(64))
        val warm=load.copy(value=java.math.BigDecimal("22.50"))
        val request=MidSessionWorkingEdit("workout-1",0,"raise-1",1,
            warmupChange=SessionWarmupChange.ADD_FIRST_VERIFIED,firstWarmupLoad=warm)
        val op=CustomWorkoutJournalOperation.AdjustRemaining("first-warm",0,request)
        val added=accept(start,op)
        val cold=NativeCustomWorkoutJournalCodec.decode(NativeCustomWorkoutJournalCodec.encode(record(added))).journal
        assertEquals(added,cold)
        assertEquals(request,(cold.receipts.single().operation as CustomWorkoutJournalOperation.AdjustRemaining).request)
        val rows=cold.snapshot.slots.single().sets
        assertEquals(warm,rows.first().prescription.load)
        assertEquals(working.setKey,rows.last().setKey)
        assertEquals(working.prescription,rows.last().prescription)
        assertEquals(source,cold.originalSnapshot)
        val wrong=request.copy(firstWarmupLoad=load)
        assertTrue(MidSessionWorkingEditDomain.custom(start,wrong) is MidSessionWorkingEditReview.Rejected)
        val removed=accept(cold,CustomWorkoutJournalOperation.AdjustRemaining("remove-first",1,
            MidSessionWorkingEdit("workout-1",1,"raise-1",1,warmupChange=SessionWarmupChange.REMOVE_PENDING)))
        assertEquals(source,removed.snapshot)
        assertEquals(removed,NativeCustomWorkoutJournalCodec.decode(NativeCustomWorkoutJournalCodec.encode(record(removed))).journal)
    }

    @Test fun warmupEditsReplayStableOriginalWorkingKeysAndExcludeRemovedWork() {
        val base=snapshot()
        val slot=base.slots.single()
        val warm=slot.sets.single().copy(prescription=CustomAuthoredSet(
            DraftSetType.WARMUP,DraftSetLoad.ExplicitBodyweight,RepBand(5,5),null,30,60))
        val working=slot.sets.single().copy(setKey="cycle-1:w1:d0:raise-1:s2",ordinalInSlot=2,
            prescription=slot.sets.single().prescription.copy(amplifier=null))
        val source=base.copy(slots=listOf(slot.copy(sets=listOf(warm,working))),
            knownSetAndRestSeconds=225)
        val start=CustomWorkoutJournalDomain.begin("workout-1",source,"a".repeat(64))
        fun request(state:CustomWorkoutJournalDraft,change:SessionWarmupChange)=
            MidSessionWorkingEdit("workout-1",state.revision,"raise-1",1,warmupChange=change)
        fun cold(state:CustomWorkoutJournalDraft)=NativeCustomWorkoutJournalCodec.decode(
            NativeCustomWorkoutJournalCodec.encode(record(state))).journal
        val add=CustomWorkoutJournalOperation.AdjustRemaining("repeat",0,
            request(start,SessionWarmupChange.REPEAT_APPROVED))
        val added=cold(accept(start,add))
        val rows=added.snapshot.slots.single().sets
        assertEquals(listOf(key,"mid-1-1",working.setKey),rows.map{it.setKey})
        assertEquals(warm.prescription,rows[1].prescription)
        assertEquals(working.prescription,rows[2].prescription)
        assertEquals(source,added.originalSnapshot)
        assertEquals(315L,added.snapshot.knownSetAndRestSeconds)
        assertTrue(runCatching{NativeKotlinCustomWorkoutSnapshotCodec.encode(added.snapshot)}.isFailure)
        assertEquals(9,java.nio.ByteBuffer.wrap(NativeCustomWorkoutJournalCodec.encode(record(added)),4,4).int)
        assertEquals(added,cold(added))
        assertEquals(CustomWorkoutJournalDecision.Proposed(added,true),CustomWorkoutJournalDomain.reduce(added,add))
        val removed=cold(accept(added,CustomWorkoutJournalOperation.AdjustRemaining("remove",1,
            request(added,SessionWarmupChange.REMOVE_PENDING))))
        assertEquals(source,removed.snapshot)
        assertEquals(removed,cold(removed))
        val without=cold(accept(removed,CustomWorkoutJournalOperation.AdjustRemaining("remove-original",2,
            request(removed,SessionWarmupChange.REMOVE_PENDING))))
        assertEquals(working.setKey,without.snapshot.slots.single().sets.single().setKey)
        assertEquals(135L,without.snapshot.knownSetAndRestSeconds)
        assertEquals(source,without.originalSnapshot)
        assertTrue(runCatching{NativeKotlinCustomWorkoutSnapshotCodec.encode(without.snapshot)}.isFailure)
        val restored=cold(accept(without,CustomWorkoutJournalOperation.UndoAdjustment("undo",3,"remove-original")))
        assertEquals(source,restored.snapshot)
        val main=cold(accept(restored,CustomWorkoutJournalOperation.Record("working",4,
            CustomWorkoutVisitKey(working.setKey,CustomWorkoutVisitPart.MAIN),
            KotlinJournalSetOutcome.COMPLETED,actual(10,1))))
        assertEquals(MidSessionWorkingEditReview.Rejected(MidSessionWorkingEditRefusal.RESOLVED_WORK),
            MidSessionWorkingEditDomain.custom(main,request(main,SessionWarmupChange.REPEAT_APPROVED)))
        assertEquals(main,cold(main))
        val warmLogged=cold(accept(main,CustomWorkoutJournalOperation.Record("warmup",5,
            CustomWorkoutVisitKey(key,CustomWorkoutVisitPart.MAIN),
            KotlinJournalSetOutcome.COMPLETED,actual(5))))
        val finished=cold(accept(warmLogged,CustomWorkoutJournalOperation.Finish("finish",6)))
        val evidence=(CustomWorkoutHistoryEvidenceDomain.project(finished) as CustomHistoryEvidenceResult.Candidate).evidence
        assertEquals(source,finished.originalSnapshot)
        assertEquals(listOf(DraftSetType.WARMUP,DraftSetType.WORKING),evidence.visits.map{it.setType})
        assertEquals(listOf(5,10),evidence.visits.map{it.actual?.reps})
        assertTrue(evidence.sessionAdjusted)
        assertFalse(evidence.workingPrescriptionAdjusted)
        assertTrue(evidence.allIssuedMainWorkingSetsCompleted)
    }

    @Test fun fullLegacyJournalCanResolveAnAmplifierAndColdReopenFinished() {
        var draft=initial()
        repeat(2048) { index ->
            draft=accept(draft,CustomWorkoutJournalOperation.SessionNote("note-$index",
                draft.revision,"Training"))
        }
        draft=NativeCustomWorkoutJournalCodec.decode(
            NativeCustomWorkoutJournalCodec.encode(record(draft))).journal
        assertEquals(CustomWorkoutJournalDecision.Rejected(CustomWorkoutJournalRefusal.TOO_MANY_EVENTS),
            CustomWorkoutJournalDomain.reduce(draft,
                CustomWorkoutJournalOperation.SessionNote("extra",draft.revision,"")))
        draft=accept(draft,event("main",draft.revision,CustomWorkoutVisitPart.MAIN,10))
        assertFalse(draft.canProposeFinish)
        draft=accept(draft,event("myo",draft.revision,CustomWorkoutVisitPart.FOLLOW_UP,4))
        val finish=CustomWorkoutJournalOperation.Finish("finish",draft.revision)
        draft=accept(draft,finish)
        val reopened=NativeCustomWorkoutJournalCodec.decode(
            NativeCustomWorkoutJournalCodec.encode(record(draft))).journal
        assertEquals(draft,reopened)
        assertEquals(CustomWorkoutJournalDecision.Proposed(reopened,true),
            CustomWorkoutJournalDomain.reduce(reopened,finish))
    }

    @Test fun exponentActivationAndMyoActualsKeepExactIdentityAcrossColdReplay() {
        val base=snapshot()
        val slot=base.slots.single()
        val set=slot.sets.single()
        val weighted=base.copy(slots=listOf(slot.copy(sets=listOf(set.copy(
            prescription=set.prescription.copy(load=DraftSetLoad.Verified(
                LoadUnit.LB,LoadBasis.MACHINE_STACK,java.math.BigDecimal("30"))))))))
        val start=CustomWorkoutJournalDomain.begin("workout-1",weighted,"a".repeat(64))
        val main=event("decimal-main",0,CustomWorkoutVisitPart.MAIN,10).copy(
            performed=KotlinPerformedSet(java.math.BigDecimal("3E+1"),LoadUnit.LB,10,2,"A"))
        val mini=event("decimal-mini",1,CustomWorkoutVisitPart.FOLLOW_UP,4).copy(
            performed=KotlinPerformedSet(java.math.BigDecimal("30.00"),LoadUnit.LB,4,1,"A"))
        val afterMain=accept(start,main)
        val cold=NativeCustomWorkoutJournalCodec.decode(
            NativeCustomWorkoutJournalCodec.encode(record(afterMain))).journal
        assertEquals(afterMain,cold)
        assertEquals(CustomWorkoutJournalDecision.Proposed(cold,true),
            CustomWorkoutJournalDomain.reduce(cold,main))
        val done=accept(accept(cold,mini),CustomWorkoutJournalOperation.Finish("finish",2))
        val bytes=NativeCustomWorkoutJournalCodec.encode(record(done))
        assertEquals(6,java.nio.ByteBuffer.wrap(bytes,4,4).int)
        val reopened=NativeCustomWorkoutJournalCodec.decode(bytes)
        assertEquals(record(done),reopened)
        assertArrayEquals(bytes,NativeCustomWorkoutJournalCodec.encode(reopened))
        val facts=(CustomWorkoutHistoryEvidenceDomain.project(reopened.journal)
            as CustomHistoryEvidenceResult.Candidate).evidence.visits
        assertEquals(listOf(main.performed,mini.performed),facts.map{it.actual})
        assertEquals(CustomWorkoutJournalDecision.Proposed(reopened.journal,true),
            CustomWorkoutJournalDomain.reduce(reopened.journal,mini))
    }

    @Test fun largeNotesLeaveRoomForExplicitAmplifierSkipAndFinish() {
        var state=accept(initial(),event("budget-main",0,CustomWorkoutVisitPart.MAIN,10))
        val note="漢".repeat(CustomWorkoutJournalDomain.MAX_SESSION_NOTE_CHARS)
        var refused=false
        for(index in 0..349) {
            val proposed=accept(state,CustomWorkoutJournalOperation.SessionNote(
                "budget-note-$index",state.revision,note))
            if(index>=320 && runCatching {
                    NativeCustomWorkoutJournalCodec.encode(record(proposed))
                }.isFailure) {
                refused=true
                break
            }
            state=proposed
        }
        assertTrue("The fixture must reach the encoded-byte limit",refused)
        val saved=NativeCustomWorkoutJournalCodec.encode(record(state))
        assertEquals(record(state),NativeCustomWorkoutJournalCodec.decode(saved))
        val next=CustomWorkoutJournalDomain.nextUnresolvedVisit(state)
        assertEquals(CustomWorkoutVisitKey(key,CustomWorkoutVisitPart.FOLLOW_UP),next)
        state=accept(state,CustomWorkoutJournalOperation.Record(
            "s".repeat(80),state.revision,checkNotNull(next),
            KotlinJournalSetOutcome.SKIPPED,null))
        assertTrue(state.canProposeFinish)
        NativeCustomWorkoutJournalCodec.encode(record(state))
        val finished=accept(state,CustomWorkoutJournalOperation.Finish(
            "f".repeat(80),state.revision))
        val reopened=NativeCustomWorkoutJournalCodec.decode(
            NativeCustomWorkoutJournalCodec.encode(record(finished)))
        assertEquals(record(finished),reopened)
        assertEquals(0,reopened.journal.unresolvedVisitCount)
    }

    @Test fun machineVariantsPersistIndependentlyInMainAndAmplifierV4Receipts() {
        val base=initial()
        val main=accept(base,CustomWorkoutJournalOperation.Record(
            "machine-main",0,CustomWorkoutVisitKey(key,CustomWorkoutVisitPart.MAIN),
            KotlinJournalSetOutcome.COMPLETED,
            KotlinPerformedSet(null,null,12,2,"machine_A")))
        val follower=accept(main,CustomWorkoutJournalOperation.Record(
            "machine-amp",1,CustomWorkoutVisitKey(key,CustomWorkoutVisitPart.FOLLOW_UP),
            KotlinJournalSetOutcome.COMPLETED,
            KotlinPerformedSet(null,null,4,2,"machine_A")))
        val encoded=NativeCustomWorkoutJournalCodec.encode(record(follower))
        assertEquals(4,java.nio.ByteBuffer.wrap(encoded,4,4).int)
        val cold=NativeCustomWorkoutJournalCodec.decode(encoded)
        assertEquals(record(follower),cold)
        for(part in CustomWorkoutVisitPart.entries) {
            val stored=cold.journal.effectiveVisits.getValue(
                CustomWorkoutVisitKey(key,part)).operation
                    as CustomWorkoutJournalOperation.Record
            assertEquals("machine_A",stored.performed?.machineInstanceId)
        }
        val changed=accept(cold.journal,CustomWorkoutJournalOperation.Finish(
            "finish-machine-A",2))
        val closed=NativeCustomWorkoutJournalCodec.decode(
            NativeCustomWorkoutJournalCodec.encode(record(changed)))
        assertEquals(record(changed),closed)
        assertEquals(1,java.nio.ByteBuffer.wrap(
            NativeCustomWorkoutJournalCodec.encode(record(base)),4,4).int)
    }

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
    @Test fun myoStagesRoundTripAsDistinctV5ReceiptsWithoutRewritingV1History() {
        val legacy=initial()
        val legacyBytes=NativeCustomWorkoutJournalCodec.encode(record(legacy))
        assertEquals(1,java.nio.ByteBuffer.wrap(legacyBytes,4,4).int)
        val source=snapshot()
        val amended=source.copy(
            slots=source.slots.map { slot -> slot.copy(
                sets=slot.sets.map { set -> set.copy(
                    prescription=set.prescription.copy(
                        amplifier=set.prescription.amplifier!!.copy(followUpRounds=5))
                ) }
            ) },
            knownSetAndRestSeconds=source.knownSetAndRestSeconds+80L
        )
        var journal=CustomWorkoutJournalDomain.begin(
            "workout-myo",amended,"a".repeat(64))
        journal=accept(journal,CustomWorkoutJournalOperation.Record(
            "main",0,CustomWorkoutVisitKey(key,CustomWorkoutVisitPart.MAIN),
            KotlinJournalSetOutcome.COMPLETED,actual(16,1)))
        for((index,reps) in listOf(5,4,3).withIndex()) {
            val visit=CustomWorkoutVisitKey(key,CustomWorkoutVisitPart.FOLLOW_UP,index+1)
            assertEquals(visit,CustomWorkoutJournalDomain.nextUnresolvedVisit(journal))
            journal=accept(journal,CustomWorkoutJournalOperation.Record(
                "stage-${index+1}",journal.revision,visit,
                KotlinJournalSetOutcome.COMPLETED,actual(reps,2)))
            val cold=NativeCustomWorkoutJournalCodec.encode(record(journal))
            assertEquals(5,java.nio.ByteBuffer.wrap(cold,4,4).int)
            assertEquals(journal,NativeCustomWorkoutJournalCodec.decode(cold).journal)
            assertArrayEquals(cold,NativeCustomWorkoutJournalCodec.encode(
                NativeCustomWorkoutJournalCodec.decode(cold)))
        }
        assertEquals(null,CustomWorkoutJournalDomain.nextUnresolvedVisit(journal))
        assertTrue(journal.canProposeFinish)
        assertEquals(listOf(5,4,3),journal.effectiveVisits
            .filterKeys{it.part==CustomWorkoutVisitPart.FOLLOW_UP}
            .values.map{
                (it.operation as CustomWorkoutJournalOperation.Record).performed!!.reps
            })
        assertArrayEquals(legacyBytes,NativeCustomWorkoutJournalCodec.encode(
            NativeCustomWorkoutJournalCodec.decode(legacyBytes)))
    }

    @Test fun ownerFencedStagedRestPauseAndClustersSurviveRealJournalDiskEncoding() {
        val baseline=snapshot()
        val baselineBytes=NativeCustomWorkoutJournalCodec.encode(
            NativeCustomWorkoutJournalRecord("owner-1",
                CustomWorkoutJournalDomain.begin("baseline",baseline,"a".repeat(64))))
        for(amp in listOf(
            CustomSetAmplifier(CustomAmplifierKind.REST_PAUSE,3,20,followUpRounds=3),
            CustomSetAmplifier(CustomAmplifierKind.CLUSTER,2,30,followUpRounds=4))) {
            val old=baseline.slots.single().sets.single().prescription.amplifier!!
            val staged=baseline.copy(
                slots=baseline.slots.map{slot->slot.copy(sets=slot.sets.map{set->
                    set.copy(prescription=set.prescription.copy(amplifier=amp))
                })},
                knownSetAndRestSeconds=baseline.knownSetAndRestSeconds-
                    old.followUpRestSeconds*old.followUpRounds+
                    amp.followUpRestSeconds*amp.followUpRounds)
            var j=CustomWorkoutJournalDomain.begin("workout-"+amp.kind.name,
                staged,"a".repeat(64))
            j=accept(j,CustomWorkoutJournalOperation.Record(
                "main-"+amp.kind.name,j.revision,
                CustomWorkoutVisitKey(key,CustomWorkoutVisitPart.MAIN),
                KotlinJournalSetOutcome.COMPLETED,actual(10,2)))
            for(stage in 1..amp.followUpRounds) {
                assertEquals(CustomWorkoutVisitKey(
                    key,CustomWorkoutVisitPart.FOLLOW_UP,stage),
                    CustomWorkoutJournalDomain.nextUnresolvedVisit(j))
                val bytes=NativeCustomWorkoutJournalCodec.encode(record(j))
                assertEquals(5,java.nio.ByteBuffer.wrap(bytes,4,4).int)
                val reopened=NativeCustomWorkoutJournalCodec.decode(bytes)
                assertEquals(record(j),reopened)
                assertArrayEquals(bytes,NativeCustomWorkoutJournalCodec.encode(reopened))
                j=accept(reopened.journal,CustomWorkoutJournalOperation.Record(
                    "stage-"+amp.kind.name+"-"+stage,reopened.journal.revision,
                    CustomWorkoutVisitKey(key,CustomWorkoutVisitPart.FOLLOW_UP,stage),
                    KotlinJournalSetOutcome.COMPLETED,
                    actual(amp.followUpReps,2)))
            }
            assertNull(CustomWorkoutJournalDomain.nextUnresolvedVisit(j))
            assertTrue(j.canProposeFinish)
            val completed=accept(j,CustomWorkoutJournalOperation.Finish(
                "finish-"+amp.kind.name,j.revision))
            val bytes=NativeCustomWorkoutJournalCodec.encode(record(completed))
            val cold=NativeCustomWorkoutJournalCodec.decode(bytes)
            assertEquals(record(completed),cold)
            assertEquals(amp.followUpRounds,cold.journal.effectiveVisits.keys.count{
                it.part==CustomWorkoutVisitPart.FOLLOW_UP
            })
            assertEquals("finish-"+amp.kind.name,cold.journal.finishedByEventId)
            assertNull(CustomWorkoutJournalDomain.nextUnresolvedVisit(cold.journal))
            assertArrayEquals(bytes,NativeCustomWorkoutJournalCodec.encode(cold))
        }
        assertArrayEquals(baselineBytes,NativeCustomWorkoutJournalCodec.encode(
            NativeCustomWorkoutJournalCodec.decode(baselineBytes)))
    }

    @Test fun skippedStagedClusterAndRestPauseNeverCreatePhantomLaterReceiptsOnColdResume() {
        val base=snapshot()
        for(amp in listOf(
            CustomSetAmplifier(CustomAmplifierKind.REST_PAUSE,3,20,followUpRounds=3),
            CustomSetAmplifier(CustomAmplifierKind.CLUSTER,2,30,followUpRounds=5))) {
            val staged=base.copy(
                slots=base.slots.map{slot->slot.copy(sets=slot.sets.map{set->
                    set.copy(prescription=set.prescription.copy(amplifier=amp))
                })},
                knownSetAndRestSeconds=base.knownSetAndRestSeconds-20+
                    amp.followUpRestSeconds*amp.followUpRounds)
            var j=CustomWorkoutJournalDomain.begin("stop-"+amp.kind.name,
                staged,"a".repeat(64))
            j=accept(j,CustomWorkoutJournalOperation.Record(
                "main-stop-"+amp.kind.name,0,
                CustomWorkoutVisitKey(key,CustomWorkoutVisitPart.MAIN),
                KotlinJournalSetOutcome.COMPLETED,actual(10,2)))
            j=accept(j,CustomWorkoutJournalOperation.Record(
                "stage1-"+amp.kind.name,1,
                CustomWorkoutVisitKey(key,CustomWorkoutVisitPart.FOLLOW_UP,1),
                KotlinJournalSetOutcome.COMPLETED,actual(amp.followUpReps,2)))
            val cold=NativeCustomWorkoutJournalCodec.decode(
                NativeCustomWorkoutJournalCodec.encode(record(j)))
            assertEquals(CustomWorkoutVisitKey(key,
                CustomWorkoutVisitPart.FOLLOW_UP,2),
                CustomWorkoutJournalDomain.nextUnresolvedVisit(cold.journal))
            j=accept(cold.journal,CustomWorkoutJournalOperation.Record(
                "skip-stage2-"+amp.kind.name,cold.journal.revision,
                CustomWorkoutVisitKey(key,CustomWorkoutVisitPart.FOLLOW_UP,2),
                KotlinJournalSetOutcome.SKIPPED,null))
            assertNull(CustomWorkoutJournalDomain.nextUnresolvedVisit(j))
            assertEquals(2,j.effectiveVisits.keys.count{
                it.part==CustomWorkoutVisitPart.FOLLOW_UP
            })
            assertTrue(j.canProposeFinish)
            val replay=NativeCustomWorkoutJournalCodec.decode(
                NativeCustomWorkoutJournalCodec.encode(record(j)))
            assertEquals(record(j),replay)
            assertNull(CustomWorkoutJournalDomain.nextUnresolvedVisit(replay.journal))
        }
    }

    @Test fun finalSetPartialRepsColdReplaySeparatelyWithoutInventingFullRangeSets() {
        val baseline=snapshot()
        val old=baseline.slots.single().sets.single().prescription.amplifier!!
        val partial=CustomSetAmplifier(CustomAmplifierKind.LENGTHENED_PARTIALS,
            3,0,followUpActiveSeconds=15)
        val issued=baseline.copy(slots=baseline.slots.map{slot->
            slot.copy(sets=slot.sets.map{set->
                set.copy(prescription=set.prescription.copy(amplifier=partial))
            })
        },knownSetAndRestSeconds=baseline.knownSetAndRestSeconds-
            old.followUpRestSeconds*old.followUpRounds+15)
        var j=CustomWorkoutJournalDomain.begin("partial-session",issued,
            "a".repeat(64))
        j=accept(j,CustomWorkoutJournalOperation.Record("full-rom",0,
            CustomWorkoutVisitKey(key,CustomWorkoutVisitPart.MAIN),
            KotlinJournalSetOutcome.COMPLETED,actual(10,2)))
        val before=NativeCustomWorkoutJournalCodec.encode(record(j))
        val cold=NativeCustomWorkoutJournalCodec.decode(before)
        assertEquals(j,cold.journal)
        assertEquals(CustomWorkoutVisitKey(key,CustomWorkoutVisitPart.FOLLOW_UP),
            CustomWorkoutJournalDomain.nextUnresolvedVisit(cold.journal))
        assertFalse(cold.journal.canProposeFinish)
        j=accept(cold.journal,CustomWorkoutJournalOperation.Record(
            "partial-repetitions",j.revision,
            CustomWorkoutVisitKey(key,CustomWorkoutVisitPart.FOLLOW_UP),
            KotlinJournalSetOutcome.COMPLETED,actual(3,null)))
        assertNull(CustomWorkoutJournalDomain.nextUnresolvedVisit(j))
        j=accept(j,CustomWorkoutJournalOperation.Finish(
            "finished-with-partials",j.revision))
        val saved=NativeCustomWorkoutJournalCodec.encode(record(j))
        val replay=NativeCustomWorkoutJournalCodec.decode(saved)
        assertEquals(record(j),replay)
        assertArrayEquals(saved,NativeCustomWorkoutJournalCodec.encode(replay))
        val evidence=CustomWorkoutHistoryEvidenceDomain.project(replay.journal)
            as CustomHistoryEvidenceResult.Candidate
        assertEquals(1,evidence.evidence.visits.count{
            it.part==CustomWorkoutVisitPart.MAIN
        })
        val extension=evidence.evidence.visits.single{
            it.part==CustomWorkoutVisitPart.FOLLOW_UP
        }
        assertEquals(CustomAmplifierKind.LENGTHENED_PARTIALS,extension.amplifierKind)
        assertEquals(3,extension.actual?.reps)
        assertFalse(evidence.evidence.canApplyProgression)
    }

    @Test fun twoDropV6SnapshotsJournalV5ColdReplayRetainsBothDistinctActualsAndHistory() {
        val baseline=snapshot()
        val main=DraftSetLoad.Verified(LoadUnit.LB,
            LoadBasis.SMITH_TOTAL,java.math.BigDecimal("185"))
        val amp=CustomSetAmplifier(CustomAmplifierKind.DROP_SET,8,10,
            followUpLoad=main.copy(value=java.math.BigDecimal("155")),
            followUpRounds=2,
            secondDropLoad=main.copy(value=java.math.BigDecimal("135")))
        val originalAmp=baseline.slots.single().sets.single().prescription.amplifier!!
        val issued=baseline.copy(slots=baseline.slots.map{slot->
            slot.copy(sets=slot.sets.map{set->
                set.copy(prescription=set.prescription.copy(
                    load=main,amplifier=amp))
            })
        },knownSetAndRestSeconds=baseline.knownSetAndRestSeconds-
            originalAmp.followUpRestSeconds*originalAmp.followUpRounds+
            amp.followUpRestSeconds*amp.followUpRounds)
        fun observed(weight:String,reps:Int)=KotlinPerformedSet(
            java.math.BigDecimal(weight),LoadUnit.LB,reps,2,"machine_A")
        var j=CustomWorkoutJournalDomain.begin("two-drop-owned",issued,
            "a".repeat(64))
        j=accept(j,CustomWorkoutJournalOperation.Record("main-two",j.revision,
            CustomWorkoutVisitKey(key,CustomWorkoutVisitPart.MAIN),
            KotlinJournalSetOutcome.COMPLETED,observed("185",10)))
        for((stage,weight) in listOf("155","135").withIndex()) {
            val stageIndex=stage+1
            assertEquals(CustomWorkoutVisitKey(key,CustomWorkoutVisitPart.FOLLOW_UP,
                stageIndex),CustomWorkoutJournalDomain.nextUnresolvedVisit(j))
            val wire=NativeCustomWorkoutJournalCodec.encode(record(j))
            assertEquals(5,java.nio.ByteBuffer.wrap(wire,4,4).int)
            val recovered=NativeCustomWorkoutJournalCodec.decode(wire)
            assertEquals(record(j),recovered)
            val set=recovered.journal.snapshot.slots.single().sets.single()
            assertEquals(amp,set.prescription.amplifier)
            j=accept(recovered.journal,CustomWorkoutJournalOperation.Record(
                "drop-"+stageIndex,j.revision,
                CustomWorkoutVisitKey(key,CustomWorkoutVisitPart.FOLLOW_UP,
                    stageIndex),KotlinJournalSetOutcome.COMPLETED,
                observed(weight,8)))
        }
        assertNull(CustomWorkoutJournalDomain.nextUnresolvedVisit(j))
        assertTrue(j.canProposeFinish)
        j=accept(j,CustomWorkoutJournalOperation.Finish(
            "finish-two-drop",j.revision))
        val saved=NativeCustomWorkoutJournalCodec.encode(record(j))
        val cold=NativeCustomWorkoutJournalCodec.decode(saved)
        assertEquals(record(j),cold)
        assertArrayEquals(saved,NativeCustomWorkoutJournalCodec.encode(cold))
        val facts=(CustomWorkoutHistoryEvidenceDomain.project(cold.journal)
            as CustomHistoryEvidenceResult.Candidate).evidence.visits
        assertEquals(listOf(java.math.BigDecimal("155"),
            java.math.BigDecimal("135")),facts.filter{
                it.part==CustomWorkoutVisitPart.FOLLOW_UP
            }.map{(it.prescribedLoad as DraftSetLoad.Verified).value})
        assertEquals(listOf(java.math.BigDecimal("155"),
            java.math.BigDecimal("135")),facts.filter{
                it.part==CustomWorkoutVisitPart.FOLLOW_UP
            }.map{it.actual?.load})
    }

}
