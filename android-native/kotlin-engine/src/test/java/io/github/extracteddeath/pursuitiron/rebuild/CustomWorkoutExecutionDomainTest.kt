package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class CustomWorkoutExecutionDomainTest {
    private fun slot(id:String,count:Int=2,group:String?="pair",warmups:Int=0,
        amplifier:CustomSetAmplifier?=null,rest:Int=90)=CustomWorkoutSlotSnapshot(
        id,"exercise-$id","family","variant",ComposerRole.ACCESSORY,null,group,true,"",
        (1..warmups).map {number->CustomWorkoutSetSnapshot("$id:w$number",id,number,
            CustomAuthoredSet(DraftSetType.WARMUP,DraftSetLoad.ExplicitBodyweight,
                RepBand(5,5),null,20,45))}+
        (1..count).map {number->CustomWorkoutSetSnapshot("$id:s$number",id,warmups+number,
            CustomAuthoredSet(DraftSetType.WORKING,
                DraftSetLoad.Verified(LoadUnit.LB,LoadBasis.DUMBBELL_PER_HAND,BigDecimal("20")),
                RepBand(8,12),2,40,rest,amplifier))})
    private fun initial(vararg slots:CustomWorkoutSlotSnapshot)=CustomWorkoutJournalDomain.begin(
        "workout-1",CustomWorkoutDaySnapshot(
            CustomWorkoutSourceStamp("athlete","profile","cycle",1,"gym",1),
            1,1,"base",TrainingPhase.HYPERTROPHY,CustomBlockPurpose.TRAINING,
            CustomSplitIdentity.Freeform("Custom"),0,"Upper",
            ComposerSessionPurpose.HYPERTROPHY,slots.toList(),1000,null),"a".repeat(64))
    private fun visit(key:String,part:CustomWorkoutVisitPart=CustomWorkoutVisitPart.MAIN)=CustomWorkoutVisitKey(key,part)
    private fun record(j:CustomWorkoutJournalDraft,key:String,
        part:CustomWorkoutVisitPart=CustomWorkoutVisitPart.MAIN,
        outcome:KotlinJournalSetOutcome=KotlinJournalSetOutcome.COMPLETED,
        corrects:String?=null):CustomWorkoutJournalDraft {
        val set=j.snapshot.slots.flatMap{it.sets}.single{it.setKey==key}
        val load=if(part==CustomWorkoutVisitPart.FOLLOW_UP)
            set.prescription.amplifier?.followUpLoad ?:set.prescription.load
        else set.prescription.load
        val verified=load as? DraftSetLoad.Verified
        val actual=if(outcome==KotlinJournalSetOutcome.SKIPPED)null
            else KotlinPerformedSet(verified?.value,verified?.unit,
                if(outcome==KotlinJournalSetOutcome.FAILED_ATTEMPT)0 else 8,
                if(outcome==KotlinJournalSetOutcome.FAILED_ATTEMPT)null else 2)
        val operation=CustomWorkoutJournalOperation.Record("event-${j.revision}",j.revision,
            visit(key,part),outcome,actual,corrects)
        return (CustomWorkoutJournalDomain.reduce(j,operation) as CustomWorkoutJournalDecision.Proposed).draft
    }
    private fun next(j:CustomWorkoutJournalDraft)=CustomWorkoutJournalDomain.nextUnresolvedVisit(j)
    private fun rest(j:CustomWorkoutJournalDraft)=KotlinWorkoutRestDomain.inspect(j,next(j))
    private fun order(j:CustomWorkoutJournalDraft)=CustomWorkoutExecutionDomain.orderedSets(j.snapshot).map{it.set.setKey}

    @Test fun pairAlternatesEveryWorkingRoundAndAllThreeProjectionsShareTheOrder() {
        var j=initial(slot("a"),slot("b"),slot("solo",1,null))
        val expected=listOf("a:s1","b:s1","a:s2","b:s2","solo:s1")
        assertEquals(expected,order(j))
        assertEquals(expected.map{visit(it)},CustomWorkoutVisitFocusDomain.visibleOrder(j))
        val snapshot=j.snapshot
        for((index,key) in expected.withIndex()) {
            assertEquals(visit(key),next(j))
            j=record(j,key)
            if(index<expected.lastIndex)assertEquals(if(index%2==0)0 else 90,rest(j).automaticCue?.seconds)
        }
        assertNull(next(j));assertNull(rest(j).automaticCue)
        assertEquals(snapshot,j.snapshot)
        assertEquals(snapshot,j.originalSnapshot)
        assertEquals(expected,j.receipts.map{(it.operation as CustomWorkoutJournalOperation.Record).visit.setKey})
        assertTrue(j.effectiveVisits.values.all {
            ((it.operation as CustomWorkoutJournalOperation.Record).performed?.load)==BigDecimal("20")
        })
    }
    @Test fun tripletAndUnequalCountsNeverDuplicateOrDropASet() {
        val j=initial(slot("a",3),slot("b",1),slot("c",2))
        assertEquals(listOf("a:s1","b:s1","c:s1","a:s2","c:s2","a:s3"),order(j))
        val steps=CustomWorkoutExecutionDomain.orderedSets(j.snapshot)
        assertEquals(listOf(1,1,1,2,2,3),steps.map{it.round?.number})
        assertTrue(steps.all{it.round?.total==3})
        assertEquals(6,steps.map{it.set.setKey}.distinct().size)
    }
    @Test fun warmupsStayTogetherPerMovementAndUseTheirOwnRestBeforeAnyWorkingRound() {
        var j=initial(slot("a",warmups=2),slot("b",warmups=1))
        assertEquals(listOf("a:w1","a:w2","b:w1","a:s1","b:s1","a:s2","b:s2"),order(j))
        for(key in listOf("a:w1","a:w2","b:w1")) {
            assertFalse(rest(j).manualIsSuperset)
            assertEquals(visit(key),next(j))
            j=record(j,key)
            assertEquals(45,rest(j).automaticCue?.seconds)
        }
        assertTrue(rest(j).manualIsSuperset)
        assertEquals(visit("a:s1"),next(j))
    }
    @Test fun discontiguousMembersExecuteAtTheFirstMemberWithoutChangingAuthoredSnapshotOrder() {
        val j=initial(slot("a"),slot("solo",1,null),slot("b"),slot("other",1,"second"),slot("last",1,"second"))
        assertEquals(listOf("a:s1","b:s1","a:s2","b:s2","solo:s1","other:s1","last:s1"),order(j))
        assertEquals(listOf("a","solo","b","other","last"),j.snapshot.slots.map{it.slotId})
    }
    @Test fun unlinkedAndMalformedGroupsPreserveTheirOrdinaryOrder() {
        val unlinked=initial(slot("a",group=null),slot("b",group=null))
        assertEquals(listOf("a:s1","a:s2","b:s1","b:s2"),order(unlinked))
        for(members in listOf(1,4)) {
            val j=initial(*(1..members).map{slot("a$it")}.toTypedArray())
            assertEquals(j.snapshot.slots.flatMap{it.sets}.map{it.setKey},order(j))
            assertTrue(CustomWorkoutExecutionDomain.orderedSets(j.snapshot).all{it.round==null})
            assertNull(rest(record(j,"a1:s1")).automaticCue)
        }
    }
    @Test fun everyAmplifierKeepsItsOwnPauseAndMustResolveBeforeThePartner() {
        for(kind in CustomAmplifierKind.entries) {
            var j=initial(slot("a",amplifier=CustomSetAmplifier(kind,4,15)),slot("b"))
            j=record(j,"a:s1")
            assertEquals(visit("a:s1",CustomWorkoutVisitPart.FOLLOW_UP),next(j))
            assertEquals(15,rest(j).automaticCue?.seconds)
            j=record(j,"a:s1",CustomWorkoutVisitPart.FOLLOW_UP)
            assertEquals(visit("b:s1"),next(j))
            assertEquals(0,rest(j).automaticCue?.seconds)
        }
    }
    @Test fun partnersAmplifierClosesTheRoundAndUsesItsAuthoredRest() {
        var j=initial(slot("a",rest=150),slot("b",amplifier=CustomSetAmplifier(CustomAmplifierKind.MYO_REPS,4,20),rest=75))
        j=record(j,"a:s1");j=record(j,"b:s1")
        assertEquals(20,rest(j).automaticCue?.seconds)
        j=record(j,"b:s1",CustomWorkoutVisitPart.FOLLOW_UP)
        assertEquals(75,rest(j).automaticCue?.seconds)
        assertEquals(visit("a:s2"),next(j))
        assertEquals(150,rest(j).manualRestSeconds)
    }
    @Test fun outOfOrderCompletionCannotClaimAnUnfinishedRoundOrSkipATriggeredAmplifier() {
        var j=initial(slot("a"),slot("b",amplifier=CustomSetAmplifier(CustomAmplifierKind.DROP_SET,4,0)))
        j=record(j,"b:s1")
        assertEquals(visit("b:s1",CustomWorkoutVisitPart.FOLLOW_UP),next(j))
        assertEquals(0,rest(j).automaticCue?.seconds)
        j=record(j,"b:s1",CustomWorkoutVisitPart.FOLLOW_UP)
        assertEquals(visit("a:s1"),next(j))
        assertEquals(0,rest(j).automaticCue?.seconds)
        j=record(j,"a:s1")
        assertEquals(90,rest(j).automaticCue?.seconds)
    }
    @Test fun skipOrFailureIsNeverACompletedSetOrAnAutomaticRest() {
        for(outcome in listOf(KotlinJournalSetOutcome.SKIPPED,KotlinJournalSetOutcome.FAILED_ATTEMPT)) {
            var j=initial(slot("a",amplifier=CustomSetAmplifier(CustomAmplifierKind.MYO_REPS,4,20)),slot("b"))
            j=record(j,"a:s1",outcome=outcome)
            assertEquals(visit("b:s1"),next(j));assertNull(rest(j).automaticCue)
            assertFalse(CustomWorkoutVisitFocusDomain.visibleOrder(j).contains(visit("a:s1",CustomWorkoutVisitPart.FOLLOW_UP)))
            j=record(j,"b:s1")
            assertEquals(90,rest(j).automaticCue?.seconds)
            assertEquals(visit("a:s2"),next(j))
        }
    }
    @Test fun noteEditsAndCorrectionsNeverRestartRoundRestOrLoseAuthoredTargets() {
        var j=initial(slot("a"),slot("b"))
        val snapshot=j.snapshot
        j=record(j,"a:s1");j=record(j,"b:s1")
        val before=rest(j)
        j=(CustomWorkoutJournalDomain.reduce(j,CustomWorkoutJournalOperation.SessionNote("note",j.revision,"Fine"))
            as CustomWorkoutJournalDecision.Proposed).draft
        assertEquals(before,rest(j))
        j=record(j,"a:s1",corrects="event-0")
        assertNull(rest(j).automaticCue)
        assertEquals(snapshot,j.snapshot)
        assertEquals(visit("a:s2"),next(j))
    }
    @Test fun remainingSoloRoundsRestNormallyAndTheFinalVisitHasNoPostWorkoutTimer() {
        var j=initial(slot("a",3),slot("b",1,rest=120))
        j=record(j,"a:s1");assertEquals(0,rest(j).automaticCue?.seconds)
        j=record(j,"b:s1");assertEquals(120,rest(j).automaticCue?.seconds)
        j=record(j,"a:s2");assertEquals(90,rest(j).automaticCue?.seconds)
        j=record(j,"a:s3");assertNull(rest(j).automaticCue)
        assertFalse(rest(j).hasRemainingWork)
        assertTrue(j.canProposeFinish)
    }
    @Test fun exactRetriesAndFinishKeepTheSameReceiptsAndNoPhantomNextVisit() {
        var j=initial(slot("a",1),slot("b",1))
        j=record(j,"a:s1")
        val retry=CustomWorkoutJournalDomain.reduce(j,j.receipts.single().operation) as CustomWorkoutJournalDecision.Proposed
        assertTrue(retry.replayed);assertEquals(j,retry.draft)
        j=record(j,"b:s1")
        val done=(CustomWorkoutJournalDomain.reduce(j,CustomWorkoutJournalOperation.Finish("finish",j.revision))
            as CustomWorkoutJournalDecision.Proposed).draft
        assertNull(next(done));assertNull(rest(done).automaticCue)
        assertEquals(3,done.receipts.size)
    }
    @Test fun aTripletRestsOnlyAfterTheThirdCompletedMovement() {
        var j=initial(slot("a"),slot("b",rest=120),slot("c",rest=150))
        j=record(j,"a:s1");assertEquals(0,rest(j).automaticCue?.seconds)
        j=record(j,"b:s1");assertEquals(0,rest(j).automaticCue?.seconds)
        j=record(j,"c:s1");assertEquals(150,rest(j).automaticCue?.seconds)
        assertEquals(visit("a:s2"),next(j))
    }
    @Test fun multipleOutOfOrderAmplifiersResolveLatestFirstWithoutChangingFocusReviewOrder() {
        val amp=CustomSetAmplifier(CustomAmplifierKind.REST_PAUSE,4,15)
        var j=initial(slot("a",amplifier=amp),slot("b",amplifier=amp))
        j=record(j,"a:s1");j=record(j,"b:s1")
        assertEquals(visit("b:s1",CustomWorkoutVisitPart.FOLLOW_UP),next(j))
        val review=CustomWorkoutVisitFocusDomain.visibleOrder(j)
        j=record(j,"b:s1",CustomWorkoutVisitPart.FOLLOW_UP)
        assertEquals(visit("a:s1",CustomWorkoutVisitPart.FOLLOW_UP),next(j))
        assertEquals(0,rest(j).automaticCue?.seconds)
        j=record(j,"a:s1",CustomWorkoutVisitPart.FOLLOW_UP)
        assertEquals(90,rest(j).automaticCue?.seconds)
        assertEquals(visit("a:s2"),next(j))
        assertEquals(review,CustomWorkoutVisitFocusDomain.visibleOrder(j))
    }
}
