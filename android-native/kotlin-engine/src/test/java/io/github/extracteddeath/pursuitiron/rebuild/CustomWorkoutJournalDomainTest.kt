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

}
