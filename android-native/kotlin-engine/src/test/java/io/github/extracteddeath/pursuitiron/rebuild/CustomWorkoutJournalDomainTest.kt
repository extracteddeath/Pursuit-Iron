package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class CustomWorkoutJournalDomainTest {
    private val hash="a".repeat(64)
    private val first="cycle-1:w1:d0:slot-a:s1"
    private fun makeSet(
        load:DraftSetLoad=DraftSetLoad.ExplicitBodyweight,
        amplifier:CustomSetAmplifier?=CustomSetAmplifier(
            CustomAmplifierKind.MYO_REPS,4,20)
    )=CustomAuthoredSet(DraftSetType.WORKING,load,
        RepBand(8,12),2,45,90,amplifier)
    private fun snapshot(set:CustomAuthoredSet=makeSet())=
        CustomWorkoutDaySnapshot(
            stamp=CustomWorkoutSourceStamp(
                "athlete-1","profile-1","cycle-1",3,"gym-1",8),
            absoluteWeek=1,weekInBlock=1,blockId="base",
            phase=TrainingPhase.HYPERTROPHY,
            blockPurpose=CustomBlockPurpose.TRAINING,
            split=CustomSplitIdentity.Freeform("My split"),
            weekday=0,dayLabel="Upper A",
            sessionPurpose=ComposerSessionPurpose.HYPERTROPHY,
            slots=listOf(CustomWorkoutSlotSnapshot(
                "slot-a","bodyweight_crunch","trunk_flexion",
                "bodyweight",ComposerRole.ACCESSORY,null,null,
                pinned=true,coachNote="My exact set",
                sets=listOf(CustomWorkoutSetSnapshot(first,"slot-a",1,set)))),
            knownSetAndRestSeconds=155L,declaredNonSetSeconds=100
        )
    private fun initial(set:CustomAuthoredSet=makeSet())=
        CustomWorkoutJournalDomain.begin("workout-1",snapshot(set),hash)
    private fun key(part:CustomWorkoutVisitPart)=
        CustomWorkoutVisitKey(first,part)
    private fun actual(reps:Int,rir:Int?=null)=
        KotlinPerformedSet(null,null,reps,rir)
    private fun record(
        event:String,revision:Long,
        part:CustomWorkoutVisitPart=CustomWorkoutVisitPart.MAIN,
        outcome:KotlinJournalSetOutcome=KotlinJournalSetOutcome.COMPLETED,
        performed:KotlinPerformedSet?=actual(10,1),
        correction:String?=null
    )=CustomWorkoutJournalOperation.Record(
        event,revision,key(part),outcome,performed,correction)
    private fun accept(
        prior:CustomWorkoutJournalDraft,
        operation:CustomWorkoutJournalOperation
    ):CustomWorkoutJournalDraft {
        val result=CustomWorkoutJournalDomain.reduce(prior,operation)
        assertTrue(result.toString(),
            result is CustomWorkoutJournalDecision.Proposed)
        return (result as CustomWorkoutJournalDecision.Proposed).draft
    }

    @Test fun boundedSessionNoteIsRevisionedClearableAndNeverCompletesSets() {
        val start=initial(makeSet(amplifier=null))
        val add=CustomWorkoutJournalOperation.SessionNote("note-1",0,"Breathing felt good")
        val saved=accept(start,add)
        assertEquals("Breathing felt good",saved.sessionNote)
        assertEquals(1L,saved.revision)
        assertEquals(1,saved.unresolvedVisitCount)
        assertEquals(start.snapshot,saved.snapshot)
        assertTrue(saved.effectiveVisits.isEmpty())
        val retry=CustomWorkoutJournalDomain.reduce(saved,add)
            as CustomWorkoutJournalDecision.Proposed
        assertTrue(retry.replayed)
        assertEquals(saved,retry.draft)
        assertEquals(CustomWorkoutJournalDecision.Rejected(
            CustomWorkoutJournalRefusal.STALE_REVISION),
            CustomWorkoutJournalDomain.reduce(saved,
                CustomWorkoutJournalOperation.SessionNote("stale-note",0,"stale")))
        assertEquals(CustomWorkoutJournalDecision.Rejected(
            CustomWorkoutJournalRefusal.EVENT_ID_COLLISION),
            CustomWorkoutJournalDomain.reduce(saved,
                add.copy(text="replaced without permission")))
        assertEquals(CustomWorkoutJournalDecision.Rejected(
            CustomWorkoutJournalRefusal.INVALID_SESSION_NOTE),
            CustomWorkoutJournalDomain.reduce(saved,
                CustomWorkoutJournalOperation.SessionNote("bad-note",1,"x".repeat(2001))))
        assertEquals(CustomWorkoutJournalDecision.Rejected(
            CustomWorkoutJournalRefusal.INVALID_SESSION_NOTE),
            CustomWorkoutJournalDomain.reduce(saved,
                CustomWorkoutJournalOperation.SessionNote("control-note",1,"bad\u0000control")))
        val cleared=accept(saved,
            CustomWorkoutJournalOperation.SessionNote("note-clear",1,""))
        assertEquals("",cleared.sessionNote)
        assertEquals(2,cleared.receipts.size)
        val main=accept(cleared,record("main-after-note",2))
        val finished=accept(main,CustomWorkoutJournalOperation.Finish("note-finish",3))
        assertEquals("",finished.sessionNote)
        assertEquals(CustomWorkoutJournalDecision.Rejected(
            CustomWorkoutJournalRefusal.FINISHED),
            CustomWorkoutJournalDomain.reduce(finished,
                CustomWorkoutJournalOperation.SessionNote("after-finish",4,"no")))
    }

    @Test fun mainAndFollowupHaveSeparateFactsAndFinishNeverAssumesAmplifier() {
        val initial=initial()
        assertEquals(1,initial.unresolvedVisitCount)
        assertFalse(initial.persisted)
        assertFalse(initial.mayWriteHistory)
        val main=record("event-main",0)
        val afterMain=accept(initial,main)
        assertEquals(1,afterMain.unresolvedVisitCount)
        assertFalse(afterMain.canProposeFinish)
        assertEquals(CustomWorkoutJournalDecision.Rejected(
            CustomWorkoutJournalRefusal.INCOMPLETE_WORKOUT),
            CustomWorkoutJournalDomain.reduce(afterMain,
                CustomWorkoutJournalOperation.Finish("early-finish",1)))
        val follow=record("event-myo",1,
            CustomWorkoutVisitPart.FOLLOW_UP,
            performed=actual(4,0))
        val afterFollow=accept(afterMain,follow)
        assertEquals(0,afterFollow.unresolvedVisitCount)
        assertEquals(2,afterFollow.effectiveVisits.size)
        assertEquals(10,(afterFollow.effectiveVisits[key(CustomWorkoutVisitPart.MAIN)]
            ?.operation as CustomWorkoutJournalOperation.Record)
            .performed!!.reps)
        assertEquals(4,(afterFollow.effectiveVisits[key(CustomWorkoutVisitPart.FOLLOW_UP)]
            ?.operation as CustomWorkoutJournalOperation.Record)
            .performed!!.reps)
        assertTrue(afterFollow.canProposeFinish)
        val finish=CustomWorkoutJournalOperation.Finish("event-finish",2)
        val complete=accept(afterFollow,finish)
        assertEquals("event-finish",complete.finishedByEventId)
        assertFalse(complete.persisted)
        assertEquals(CustomWorkoutJournalDecision.Rejected(
            CustomWorkoutJournalRefusal.FINISHED),
            CustomWorkoutJournalDomain.reduce(complete,
                record("new-event",3)))
        val replay=CustomWorkoutJournalDomain.reduce(complete,main)
        assertTrue(replay is CustomWorkoutJournalDecision.Proposed)
        assertTrue((replay as CustomWorkoutJournalDecision.Proposed).replayed)
        assertEquals(complete,replay.draft)
    }

    @Test fun skippedMainRequiresNoFollowupAndCannotInventExtraPerformance() {
        val skipped=record("skip-main",0,
            outcome=KotlinJournalSetOutcome.SKIPPED,performed=null)
        val state=accept(initial(),skipped)
        assertEquals(0,state.unresolvedVisitCount)
        assertEquals(CustomWorkoutJournalDecision.Rejected(
            CustomWorkoutJournalRefusal.MAIN_NOT_COMPLETED),
            CustomWorkoutJournalDomain.reduce(state,record(
                "fake-myo",1,CustomWorkoutVisitPart.FOLLOW_UP,
                performed=actual(4))))
        val complete=accept(state,
            CustomWorkoutJournalOperation.Finish("done",1))
        assertEquals(1,complete.effectiveVisits.size)
        assertEquals(0,complete.receipts.filter{
            it.operation is CustomWorkoutJournalOperation.Record &&
                it.operation.visit.part==CustomWorkoutVisitPart.FOLLOW_UP
        }.size)
        assertEquals("done",complete.finishedByEventId)
    }

    @Test fun correctionMaintainsCausalRelationshipAndNoOrphanedFollowup() {
        val main=accept(initial(),record("initial-main",0))
        val done=accept(main,record("initial-extra",1,
            CustomWorkoutVisitPart.FOLLOW_UP,
            performed=actual(4)))
        assertEquals(CustomWorkoutJournalDecision.Rejected(
            CustomWorkoutJournalRefusal.INVALID_CORRECTION),
            CustomWorkoutJournalDomain.reduce(done,
                record("fix-main",2,
                    outcome=KotlinJournalSetOutcome.SKIPPED,
                    performed=null,correction="initial-main")))
        val skippedExtra=accept(done,record("fix-extra",2,
            CustomWorkoutVisitPart.FOLLOW_UP,
            KotlinJournalSetOutcome.SKIPPED,
            null,correction="initial-extra"))
        val skippedMain=accept(skippedExtra,record("fix-main",3,
            outcome=KotlinJournalSetOutcome.SKIPPED,
            performed=null,correction="initial-main"))
        assertEquals(0,skippedMain.unresolvedVisitCount)
        assertFalse(skippedMain.persisted)
        assertEquals(CustomWorkoutJournalDecision.Rejected(
            CustomWorkoutJournalRefusal.INVALID_CORRECTION),
            CustomWorkoutJournalDomain.reduce(skippedMain,
                record("wrong-correction",4,
                    outcome=KotlinJournalSetOutcome.COMPLETED,
                    performed=actual(9),correction="initial-extra")))
        val complete=accept(skippedMain,
            CustomWorkoutJournalOperation.Finish("finish",4))
        assertEquals(5,complete.revision)
    }

    @Test fun followupLoadFactsAreSeparateFromTargetAndReportedRirNotPresumed() {
        val initial=initial(makeSet(
            load=DraftSetLoad.Verified(
                LoadUnit.LB,LoadBasis.DUMBBELL_PER_HAND,BigDecimal("20")),
            amplifier=CustomSetAmplifier(CustomAmplifierKind.DROP_SET,
                8,25,DraftSetLoad.Verified(
                    LoadUnit.LB,LoadBasis.DUMBBELL_PER_HAND,
                    BigDecimal("15")))))
        assertEquals(CustomWorkoutJournalDecision.Rejected(
            CustomWorkoutJournalRefusal.INVALID_ACTUAL),
            CustomWorkoutJournalDomain.reduce(initial,record(
                "wrong-unit",0,performed=KotlinPerformedSet(
                    BigDecimal("20"),LoadUnit.KG,8,null))))
        val main=accept(initial,record("main",0,
            performed=KotlinPerformedSet(BigDecimal("20"),LoadUnit.LB,10,null)))
        assertNull((main.receipts[0].operation as
            CustomWorkoutJournalOperation.Record).performed?.actualRir)
        assertEquals(2,main.snapshot.slots.single().sets.single()
            .prescription.targetRir)
        assertEquals(CustomWorkoutJournalDecision.Rejected(
            CustomWorkoutJournalRefusal.INVALID_ACTUAL),
            CustomWorkoutJournalDomain.reduce(main,record("too-heavy",1,
                CustomWorkoutVisitPart.FOLLOW_UP,
                performed=KotlinPerformedSet(BigDecimal("100001"),
                    LoadUnit.LB,8,null))))
        val follow=accept(main,record("drop",1,
            CustomWorkoutVisitPart.FOLLOW_UP,
            performed=KotlinPerformedSet(BigDecimal("15"),
                LoadUnit.LB,7,0)))
        assertEquals(0,follow.unresolvedVisitCount)
        assertEquals(BigDecimal("15"),(
            follow.effectiveVisits[key(CustomWorkoutVisitPart.FOLLOW_UP)]
                ?.operation as CustomWorkoutJournalOperation.Record
            ).performed!!.load)
    }

    @Test fun noAmplifierMeansNoFollowupAndStaleReceiptsNeverMutate() {
        val without=initial(makeSet(amplifier=null))
        val denied=CustomWorkoutJournalDomain.reduce(without,
            record("extra",0,CustomWorkoutVisitPart.FOLLOW_UP))
        assertEquals(CustomWorkoutJournalDecision.Rejected(
            CustomWorkoutJournalRefusal.FOLLOW_UP_NOT_AUTHORED),denied)
        val after=accept(without,record("main",0))
        assertEquals(0,after.unresolvedVisitCount)
        assertEquals(CustomWorkoutJournalDecision.Rejected(
            CustomWorkoutJournalRefusal.STALE_REVISION),
            CustomWorkoutJournalDomain.reduce(after,record("stale",0)))
        assertEquals(CustomWorkoutJournalDecision.Rejected(
            CustomWorkoutJournalRefusal.EVENT_ID_COLLISION),
            CustomWorkoutJournalDomain.reduce(after,record(
                "main",0,performed=actual(6))))
        assertEquals(1,after.receipts.size)
    }

    private fun twoSlotInitial():CustomWorkoutJournalDraft {
        val old=snapshot(makeSet(amplifier=null))
        val second=CustomWorkoutSlotSnapshot(
            "slot-b","bodyweight_row","row","bodyweight",
            ComposerRole.ACCESSORY,null,null,true,"Keep this work",
            listOf(CustomWorkoutSetSnapshot(
                "cycle-1:w1:d0:slot-b:s1","slot-b",1,
                makeSet(amplifier=null)))
        )
        return CustomWorkoutJournalDomain.begin("workout-2",
            old.copy(slots=old.slots+second,
                knownSetAndRestSeconds=270L),hash)
    }
    private fun substitute(event:String="swap-1",revision:Long=1L)=
        CustomWorkoutJournalOperation.Swap(
            event,revision,"slot-a",
            CustomWorkoutSlotSnapshot(
                "slot-new","replacement_curl","curl","bodyweight",
                ComposerRole.ACCESSORY,null,null,true,"Fresh note",
                listOf(CustomWorkoutSetSnapshot(
                    "cycle-1:w1:d0:slot-new:s1","slot-new",1,
                    makeSet(amplifier=null)))))

    @Test fun swapPreservesOtherLoggedSetAndOriginalApprovedSnapshot() {
        val initial=twoSlotInitial()
        val row="cycle-1:w1:d0:slot-b:s1"
        val record=CustomWorkoutJournalOperation.Record(
            "logged-b",0L,CustomWorkoutVisitKey(row,
                CustomWorkoutVisitPart.MAIN),
            KotlinJournalSetOutcome.COMPLETED,actual(9,2))
        val before=accept(initial,record)
        val changed=accept(before,substitute())
        assertEquals(before.originalSnapshot,changed.originalSnapshot)
        assertEquals(2,changed.snapshot.slots.size)
        assertEquals(listOf("slot-new","slot-b"),
            changed.snapshot.slots.map{it.slotId})
        assertEquals(before.effectiveVisits,changed.effectiveVisits)
        assertEquals(1,changed.unresolvedVisitCount)
        assertEquals("slot-new",changed.snapshot.slots.first().slotId)
        assertEquals(2,changed.receipts.size)
        assertEquals(CustomWorkoutVisitKey(
            "cycle-1:w1:d0:slot-new:s1",CustomWorkoutVisitPart.MAIN),
            CustomWorkoutJournalDomain.nextUnresolvedVisit(changed))
        val repeat=CustomWorkoutJournalDomain.reduce(changed,substitute())
            as CustomWorkoutJournalDecision.Proposed
        assertTrue(repeat.replayed)
        assertEquals(changed,repeat.draft)
        val complete=accept(changed,CustomWorkoutJournalOperation.Record(
            "new-performed",2L,CustomWorkoutVisitKey(
                "cycle-1:w1:d0:slot-new:s1",CustomWorkoutVisitPart.MAIN),
            KotlinJournalSetOutcome.COMPLETED,actual(12,1)))
        assertTrue(complete.canProposeFinish)
        assertEquals(2,complete.effectiveVisits.size)
        assertEquals(3,complete.revision)
    }

    @Test fun swapRefusesTouchedOrProtectedSlotAndStaleIdentity() {
        val first=twoSlotInitial()
        val touched=accept(first,CustomWorkoutJournalOperation.Record(
            "main-a",0L,CustomWorkoutVisitKey(
                "cycle-1:w1:d0:slot-a:s1",CustomWorkoutVisitPart.MAIN),
            KotlinJournalSetOutcome.SKIPPED,null))
        assertEquals(CustomWorkoutJournalDecision.Rejected(
            CustomWorkoutJournalRefusal.SWAP_ALREADY_STARTED),
            CustomWorkoutJournalDomain.reduce(touched,substitute()))
        val linked=first.copy(snapshot=first.snapshot.copy(
            slots=first.snapshot.slots.mapIndexed{i,slot ->
                slot.copy(supersetGroup=if(i==0)"pair" else "pair")
            }))
        assertEquals(CustomWorkoutJournalDecision.Rejected(
            CustomWorkoutJournalRefusal.SWAP_PROTECTED),
            CustomWorkoutJournalDomain.reduce(linked,substitute(revision=0L)))
        assertEquals(CustomWorkoutJournalDecision.Rejected(
            CustomWorkoutJournalRefusal.SWAP_IDENTITY_CONFLICT),
            CustomWorkoutJournalDomain.reduce(first,
                substitute(revision=0L).copy(replacement=
                    substitute().replacement.copy(slotId="slot-b"))))
        assertEquals(CustomWorkoutJournalDecision.Rejected(
            CustomWorkoutJournalRefusal.STALE_REVISION),
            CustomWorkoutJournalDomain.reduce(first,substitute()))
    }

    @Test fun multipleSwapsCannotReuseAnyPriorTargetIdentity() {
        val before=twoSlotInitial()
        val after=accept(before,substitute(revision=0L))
        val again=CustomWorkoutJournalOperation.Swap(
            "swap-b",1L,"slot-b",
            substitute().replacement.copy(
                exerciseId="different_curl"))
        assertEquals(CustomWorkoutJournalDecision.Rejected(
            CustomWorkoutJournalRefusal.SWAP_IDENTITY_CONFLICT),
            CustomWorkoutJournalDomain.reduce(after,again))
        assertEquals(before.originalSnapshot,after.originalSnapshot)
    }


    @Test fun undoSwapRestoresExactApprovedSlotAndKeepsUnrelatedActuals() {
        val original=twoSlotInitial()
        val untouched=CustomWorkoutJournalOperation.Record(
            "logged-other",0L,
            CustomWorkoutVisitKey("cycle-1:w1:d0:slot-b:s1",
                CustomWorkoutVisitPart.MAIN),
            KotlinJournalSetOutcome.COMPLETED,actual(9,2))
        val before=accept(original,untouched)
        val swapped=accept(before,substitute())
        val undo=CustomWorkoutJournalOperation.UndoSwap(
            "undo-1",2L,"swap-1")
        val recovered=accept(swapped,undo)
        assertEquals(original.snapshot,recovered.snapshot)
        assertEquals(original.originalSnapshot,recovered.originalSnapshot)
        assertEquals(before.effectiveVisits,recovered.effectiveVisits)
        assertEquals(3L,recovered.revision)
        assertEquals(3,recovered.receipts.size)
        assertEquals(CustomWorkoutVisitKey(first,CustomWorkoutVisitPart.MAIN),
            CustomWorkoutJournalDomain.nextUnresolvedVisit(recovered))
        val exactRetry=CustomWorkoutJournalDomain.reduce(recovered,undo)
            as CustomWorkoutJournalDecision.Proposed
        assertTrue(exactRetry.replayed)
        assertEquals(recovered,exactRetry.draft)
        assertEquals(CustomWorkoutJournalDecision.Rejected(
            CustomWorkoutJournalRefusal.SWAP_UNDO_UNAVAILABLE),
            CustomWorkoutJournalDomain.reduce(recovered,
                CustomWorkoutJournalOperation.UndoSwap(
                    "undo-twice",3L,"swap-1")))
    }

    @Test fun undoRefusesRecordedReplacementAndUnrelatedOrStaleSwapEvents() {
        val original=twoSlotInitial()
        val swapped=accept(original,substitute(revision=0L))
        val target="cycle-1:w1:d0:slot-new:s1"
        val active=accept(swapped,CustomWorkoutJournalOperation.Record(
            "performed-new",1L,
            CustomWorkoutVisitKey(target,CustomWorkoutVisitPart.MAIN),
            KotlinJournalSetOutcome.COMPLETED,actual(10,2)))
        assertEquals(CustomWorkoutJournalDecision.Rejected(
            CustomWorkoutJournalRefusal.SWAP_ALREADY_STARTED),
            CustomWorkoutJournalDomain.reduce(active,
                CustomWorkoutJournalOperation.UndoSwap(
                    "unsafe-undo",2L,"swap-1")))
        assertEquals(CustomWorkoutJournalDecision.Rejected(
            CustomWorkoutJournalRefusal.SWAP_UNDO_UNAVAILABLE),
            CustomWorkoutJournalDomain.reduce(swapped,
                CustomWorkoutJournalOperation.UndoSwap(
                    "unknown-undo",1L,"missing")))
        assertEquals(CustomWorkoutJournalDecision.Rejected(
            CustomWorkoutJournalRefusal.STALE_REVISION),
            CustomWorkoutJournalDomain.reduce(swapped,
                CustomWorkoutJournalOperation.UndoSwap(
                    "stale-undo",0L,"swap-1")))
        assertEquals(CustomWorkoutJournalDecision.Rejected(
            CustomWorkoutJournalRefusal.EVENT_ID_COLLISION),
            CustomWorkoutJournalDomain.reduce(swapped,
                CustomWorkoutJournalOperation.UndoSwap(
                    "swap-1",1L,"swap-1")))
    }

    @Test fun undoMayReverseLatestSlotInSwapChainButCannotReuseDiscardedSetIds() {
        val original=twoSlotInitial()
        val firstSwap=accept(original,substitute(revision=0L))
        val secondReplacement=substitute().replacement.copy(
            slotId="slot-next",exerciseId="second_curl",
            sets=substitute().replacement.sets.map {
                it.copy(slotId="slot-next",
                    setKey="cycle-1:w1:d0:slot-next:s1")
            })
        val second=accept(firstSwap,CustomWorkoutJournalOperation.Swap(
            "swap-2",1L,"slot-new",secondReplacement))
        assertEquals(CustomWorkoutJournalDecision.Rejected(
            CustomWorkoutJournalRefusal.SWAP_UNDO_UNAVAILABLE),
            CustomWorkoutJournalDomain.reduce(second,
                CustomWorkoutJournalOperation.UndoSwap(
                    "wrong-order",2L,"swap-1")))
        val restoreMid=accept(second,CustomWorkoutJournalOperation.UndoSwap(
            "undo-2",2L,"swap-2"))
        assertEquals("slot-new",restoreMid.snapshot.slots.first().slotId)
        val restoreOriginal=accept(restoreMid,
            CustomWorkoutJournalOperation.UndoSwap("undo-1",3L,"swap-1"))
        assertEquals(original.snapshot,restoreOriginal.snapshot)
        val reused=substitute("swap-reuse",4L)
        assertEquals(CustomWorkoutJournalDecision.Rejected(
            CustomWorkoutJournalRefusal.SWAP_IDENTITY_CONFLICT),
            CustomWorkoutJournalDomain.reduce(restoreOriginal,reused))
    }

    @Test fun malformedMachineIdsCannotBecomeCustomJournalReceiptsOrCorrections() {
        val target=DraftSetLoad.Verified(
            LoadUnit.LB,LoadBasis.DUMBBELL_PER_HAND,BigDecimal("20"))
        val source=initial(makeSet(load=target,amplifier=null))
        val invalid=listOf("","A B","bad/slash","..","!".repeat(4),
            "x".repeat(81),"machine-\u0000one")
        for((index,station) in invalid.withIndex()) {
            val decision=CustomWorkoutJournalDomain.reduce(source,
                record("invalid-machine-$index",0,
                    performed=KotlinPerformedSet(
                        BigDecimal("20"),LoadUnit.LB,9,2,station)))
            assertEquals("Unexpectedly accepted machine ID: $station",
                CustomWorkoutJournalDecision.Rejected(
                    CustomWorkoutJournalRefusal.INVALID_ACTUAL),decision)
        }
        // The same rule must fence post-finish-correctable receipts; it
        // cannot be bypassed by changing the machine in a correction.
        val saved=accept(source,record("valid-machine-1",0,
            performed=KotlinPerformedSet(BigDecimal("20"),LoadUnit.LB,
                9,2,"gym-1:press-A")))
        assertEquals(CustomWorkoutJournalDecision.Rejected(
            CustomWorkoutJournalRefusal.INVALID_ACTUAL),
            CustomWorkoutJournalDomain.reduce(saved,record(
                "invalid-machine-fix",1,
                performed=KotlinPerformedSet(BigDecimal("20"),LoadUnit.LB,
                    10,2,"invalid machine"),
                correction="valid-machine-1")))
        val corrected=accept(saved,record("valid-machine-fix",1,
            performed=KotlinPerformedSet(BigDecimal("20"),LoadUnit.LB,
                10,2,"gym-1:press-B"),correction="valid-machine-1"))
        assertEquals(target,corrected.snapshot.slots.single().sets.single()
            .prescription.load)
        assertEquals("gym-1:press-B",
            (corrected.effectiveVisits.getValue(key(CustomWorkoutVisitPart.MAIN))
                .operation as CustomWorkoutJournalOperation.Record)
                .performed?.machineInstanceId)
        val legacy=accept(source,record("legacy-machine",0,
            performed=KotlinPerformedSet(BigDecimal("20"),
                LoadUnit.LB,9,2,null)))
        assertNull((legacy.effectiveVisits.getValue(
            key(CustomWorkoutVisitPart.MAIN)).operation
            as CustomWorkoutJournalOperation.Record)
            .performed?.machineInstanceId)
    }

    @Test fun machineSpecificManualLoadCorrectionNeverChangesCustomIssuedTarget() {
        val planned=DraftSetLoad.Verified(
            LoadUnit.LB,LoadBasis.DUMBBELL_PER_HAND,BigDecimal("20"))
        val issued=initial(makeSet(load=planned,amplifier=null))
        val saved=accept(issued,record("machine-a-first",0,
            performed=KotlinPerformedSet(
                BigDecimal("15"),LoadUnit.LB,9,1,"A")))
        val corrected=accept(saved,record("machine-a-correction",1,
            performed=KotlinPerformedSet(
                BigDecimal("17.5"),LoadUnit.LB,10,2,"A"),
            correction="machine-a-first"))
        assertEquals(issued.snapshot,corrected.snapshot)
        assertEquals(issued.originalSnapshot,corrected.originalSnapshot)
        assertEquals(planned,corrected.snapshot.slots.single()
            .sets.single().prescription.load)
        assertEquals(BigDecimal("17.5"),
            (corrected.effectiveVisits.getValue(key(CustomWorkoutVisitPart.MAIN))
                .operation as CustomWorkoutJournalOperation.Record).performed?.load)
        assertEquals("A",
            (corrected.effectiveVisits.getValue(key(CustomWorkoutVisitPart.MAIN))
                .operation as CustomWorkoutJournalOperation.Record)
                .performed?.machineInstanceId)
        assertEquals(2,corrected.receipts.size)
        assertEquals("machine-a-first",
            (corrected.receipts.first().operation as CustomWorkoutJournalOperation.Record).eventId)
    }

    @Test fun focusOrderNeverInventsAmplifiersOrChangesUnresolvedOwnership() {
        val start=initial()
        val mainKey=key(CustomWorkoutVisitPart.MAIN)
        val followKey=key(CustomWorkoutVisitPart.FOLLOW_UP)
        assertEquals(listOf(mainKey),
            CustomWorkoutVisitFocusDomain.visibleOrder(start))
        assertEquals(mainKey,CustomWorkoutJournalDomain.nextUnresolvedVisit(start))

        val savedMain=accept(start,record("focus-main",0))
        assertEquals(listOf(mainKey,followKey),
            CustomWorkoutVisitFocusDomain.visibleOrder(savedMain))
        assertEquals(followKey,
            CustomWorkoutJournalDomain.nextUnresolvedVisit(savedMain))
        val savedFollow=accept(savedMain,record(
            "focus-follow",1,CustomWorkoutVisitPart.FOLLOW_UP,
            performed=actual(4)))
        assertEquals(listOf(mainKey,followKey),
            CustomWorkoutVisitFocusDomain.visibleOrder(savedFollow))
        assertNull(CustomWorkoutJournalDomain.nextUnresolvedVisit(savedFollow))

        val skipped=accept(initial(),record("focus-skip",0,
            outcome=KotlinJournalSetOutcome.SKIPPED,performed=null))
        assertEquals(listOf(mainKey),
            CustomWorkoutVisitFocusDomain.visibleOrder(skipped))
        assertNull(CustomWorkoutJournalDomain.nextUnresolvedVisit(skipped))
        assertEquals(start.snapshot,skipped.snapshot)
    }

    @Test fun focusCanNavigateAllAuthoredMainSetsButOnlyRealFollowups() {
        val single=snapshot()
        val primary=single.slots.single().sets.single()
        val second=primary.copy(
            setKey="cycle-1:w1:d0:slot-a:s2",ordinalInSlot=2,
            prescription=makeSet(amplifier=null))
        val two=single.copy(slots=listOf(
            single.slots.single().copy(sets=listOf(primary,second))))
        val start=CustomWorkoutJournalDomain.begin("workout-1",two,hash)
        val mainA=CustomWorkoutVisitKey(first,CustomWorkoutVisitPart.MAIN)
        val mainB=CustomWorkoutVisitKey(second.setKey,CustomWorkoutVisitPart.MAIN)
        val followA=CustomWorkoutVisitKey(first,CustomWorkoutVisitPart.FOLLOW_UP)
        assertEquals(listOf(mainA,mainB),
            CustomWorkoutVisitFocusDomain.visibleOrder(start))

        // The owner permits out-of-order set logging; view order must stay
        // authored rather than reordering a late-resolved first exercise.
        val savedB=accept(start,CustomWorkoutJournalOperation.Record(
            "focus-b",0,mainB,KotlinJournalSetOutcome.COMPLETED,actual(10)))
        assertEquals(listOf(mainA,mainB),
            CustomWorkoutVisitFocusDomain.visibleOrder(savedB))
        assertEquals(mainA,CustomWorkoutJournalDomain.nextUnresolvedVisit(savedB))
        val savedA=accept(savedB,CustomWorkoutJournalOperation.Record(
            "focus-a",1,mainA,KotlinJournalSetOutcome.COMPLETED,actual(11)))
        assertEquals(listOf(mainA,followA,mainB),
            CustomWorkoutVisitFocusDomain.visibleOrder(savedA))
        assertEquals(followA,CustomWorkoutJournalDomain.nextUnresolvedVisit(savedA))
        assertFalse(CustomWorkoutVisitFocusDomain.visibleOrder(savedA)
            .contains(CustomWorkoutVisitKey(second.setKey,
                CustomWorkoutVisitPart.FOLLOW_UP)))
        assertEquals(two,savedA.snapshot)
    }

    @Test fun focusProgressCountsRequiredAmplifierAfterCompletedMainOnly() {
        val started=initial()
        val before=CustomWorkoutVisitFocusDomain.progress(started)
        assertEquals(0,before.recordedVisits)
        assertEquals(1,before.requiredVisits)
        assertEquals(0,before.requiredFollowUps)
        assertEquals(0f,before.fraction,0.0001f)

        val savedMain=accept(started,record("progress-main",0))
        val pending=CustomWorkoutVisitFocusDomain.progress(savedMain)
        assertEquals(1,pending.recordedMainSets)
        assertEquals(1,pending.totalMainSets)
        assertEquals(1,pending.requiredFollowUps)
        assertEquals(0,pending.recordedFollowUps)
        assertEquals(1,pending.recordedVisits)
        assertEquals(2,pending.requiredVisits)
        assertEquals(0.5f,pending.fraction,0.0001f)
        assertEquals(1,savedMain.unresolvedVisitCount)

        val savedFollow=accept(savedMain,record(
            "progress-follow",1,CustomWorkoutVisitPart.FOLLOW_UP,
            performed=actual(4)))
        val done=CustomWorkoutVisitFocusDomain.progress(savedFollow)
        assertEquals(2,done.recordedVisits)
        assertEquals(2,done.requiredVisits)
        assertEquals(1f,done.fraction,0.0001f)
        assertEquals(0,savedFollow.unresolvedVisitCount)

        val skipped=accept(initial(),record("progress-skipped",0,
            outcome=KotlinJournalSetOutcome.SKIPPED,performed=null))
        val skippedProgress=CustomWorkoutVisitFocusDomain.progress(skipped)
        assertEquals(1,skippedProgress.recordedVisits)
        assertEquals(1,skippedProgress.requiredVisits)
        assertEquals(0,skippedProgress.requiredFollowUps)
        assertEquals(1f,skippedProgress.fraction,0.0001f)
        assertEquals(0,skipped.unresolvedVisitCount)
    }

}
