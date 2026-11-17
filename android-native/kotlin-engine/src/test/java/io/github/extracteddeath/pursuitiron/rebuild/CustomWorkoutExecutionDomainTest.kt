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
    private fun visit(key:String,part:CustomWorkoutVisitPart=CustomWorkoutVisitPart.MAIN,
        stage:Int=1)=CustomWorkoutVisitKey(key,part,stage)
    private fun record(j:CustomWorkoutJournalDraft,key:String,
        part:CustomWorkoutVisitPart=CustomWorkoutVisitPart.MAIN,
        outcome:KotlinJournalSetOutcome=KotlinJournalSetOutcome.COMPLETED,
        corrects:String?=null,stage:Int=1,reps:Int=8):CustomWorkoutJournalDraft {
        val set=j.snapshot.slots.flatMap{it.sets}.single{it.setKey==key}
        val load=if(part==CustomWorkoutVisitPart.FOLLOW_UP)
            set.prescription.amplifier?.followUpLoad ?:set.prescription.load
        else set.prescription.load
        val verified=load as? DraftSetLoad.Verified
        val actual=if(outcome==KotlinJournalSetOutcome.SKIPPED)null
            else KotlinPerformedSet(verified?.value,verified?.unit,
                if(outcome==KotlinJournalSetOutcome.FAILED_ATTEMPT)0 else reps,
                if(outcome==KotlinJournalSetOutcome.FAILED_ATTEMPT)null else 2)
        val operation=CustomWorkoutJournalOperation.Record("event-${j.revision}",j.revision,
            visit(key,part,stage),outcome,actual,corrects)
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

    @Test fun remainingCustomExercisePreservesSupersetPartnerAndCompletedMain() {
        for(kind in CustomAmplifierKind.entries) {
            var j=initial(slot("a",amplifier=CustomSetAmplifier(kind,4,15)),slot("b"))
            j=record(j,"a:s1")
            val original=j
            val review=WorkoutRemainingExerciseDomain.custom(j,"a")!!
            assertEquals(listOf(visit("a:s1",CustomWorkoutVisitPart.FOLLOW_UP),visit("a:s2")),review.entries)
            assertEquals(1,review.workingCount)
            assertEquals(1,review.followUpCount)
            assertEquals(1,review.preservedEntryCount)
            for(key in review.entries)j=record(j,key.setKey,key.part,KotlinJournalSetOutcome.SKIPPED)
            assertEquals(original.effectiveVisits[visit("a:s1")],j.effectiveVisits[visit("a:s1")])
            assertEquals(emptyList<CustomWorkoutVisitKey>(),WorkoutRemainingExerciseDomain.custom(j,"a")!!.entries)
            assertFalse(j.effectiveVisits.containsKey(visit("a:s2",CustomWorkoutVisitPart.FOLLOW_UP)))
            assertEquals(visit("b:s1"),next(j))
            assertEquals(original.snapshot,j.snapshot)
            assertNull(j.finishedByEventId)
            assertNull(WorkoutRemainingExerciseDomain.custom(j,"missing"))
        }
    }
    @Test fun remainingCustomExerciseIncludesWarmupsWithoutUnlockingSkippedAmplifiers() {
        val j=initial(slot("a",warmups=2,amplifier=CustomSetAmplifier(CustomAmplifierKind.DROP_SET,4,15)))
        val review=WorkoutRemainingExerciseDomain.custom(j,"a")!!
        assertEquals(2,review.warmupCount)
        assertEquals(2,review.workingCount)
        assertEquals(0,review.followUpCount)
        assertEquals(4,review.entries.size)
        var saved=j
        for(key in review.entries)saved=record(saved,key.setKey,key.part,KotlinJournalSetOutcome.SKIPPED)
        assertTrue(saved.canProposeFinish)
        assertEquals(4,saved.effectiveVisits.size)
        assertNull(WorkoutRemainingExerciseDomain.custom(saved.copy(finishedByEventId="finish"),"a"))
    }

    @Test fun myoMiniSetsAreSavedOneAtATimeAndStopAtThreeReps() {
        val amp=CustomSetAmplifier(CustomAmplifierKind.MYO_REPS,
            5,20,null,100,5)
        var j=initial(slot("myo",1,null,amplifier=amp),
            slot("later",1,null))
        assertEquals(visit("myo:s1"),next(j))
        j=record(j,"myo:s1",reps=14)
        for((index,reps) in listOf(5,5,4,3).withIndex()) {
            val stage=index+1
            assertEquals(visit("myo:s1",CustomWorkoutVisitPart.FOLLOW_UP,stage),next(j))
            assertEquals(20,rest(j).automaticCue?.seconds)
            j=record(j,"myo:s1",CustomWorkoutVisitPart.FOLLOW_UP,
                stage=stage,reps=reps)
        }
        // The fifth mini-set is not manufactured after an early floor.
        assertEquals(visit("later:s1"),next(j))
        assertEquals(90,rest(j).automaticCue?.seconds)
        assertEquals(5,j.effectiveVisits.size)
        assertEquals(4,CustomWorkoutVisitFocusDomain.progress(j).recordedFollowUps)
        assertEquals(4,CustomWorkoutVisitFocusDomain.progress(j).requiredFollowUps)
        assertEquals(4,j.receipts.count {
            (it.operation as? CustomWorkoutJournalOperation.Record)
                ?.visit?.part==CustomWorkoutVisitPart.FOLLOW_UP
        })
        assertEquals(listOf(5,5,4,3),
            j.effectiveVisits.filterKeys{
                it.setKey=="myo:s1" && it.part==CustomWorkoutVisitPart.FOLLOW_UP
            }.values.map {
                ((it.operation as CustomWorkoutJournalOperation.Record).performed!!.reps)
            })
    }

    @Test fun myoResumesAtNextUnrecordedStageAndDoesNotForceAfterShortfall() {
        val amp=CustomSetAmplifier(CustomAmplifierKind.MYO_REPS,4,20,
            followUpRounds=5)
        var j=initial(slot("myo",1,null,amplifier=amp))
        j=record(j,"myo:s1",reps=16)
        j=record(j,"myo:s1",CustomWorkoutVisitPart.FOLLOW_UP,stage=1,reps=4)
        assertEquals(visit("myo:s1",CustomWorkoutVisitPart.FOLLOW_UP,2),next(j))
        // A cold replay reconstructs this same immutable receipt sequence.
        var replay=CustomWorkoutJournalDomain.begin(j.workoutId,j.originalSnapshot,
            j.preparedConsentFingerprint)
        for(receipt in j.receipts) {
            replay=(CustomWorkoutJournalDomain.reduce(replay,receipt.operation)
                as CustomWorkoutJournalDecision.Proposed).draft
        }
        assertEquals(j,replay)
        replay=record(replay,"myo:s1",CustomWorkoutVisitPart.FOLLOW_UP,stage=2,reps=2)
        assertTrue(replay.canProposeFinish)
        assertEquals(2,replay.effectiveVisits.keys.count {
            it.part==CustomWorkoutVisitPart.FOLLOW_UP
        })
        assertNull(next(replay))
    }

    @Test fun futureMyoStageCannotBeRecordedOutOfOrderOrCorrectedAcrossDependents() {
        val amp=CustomSetAmplifier(CustomAmplifierKind.MYO_REPS,4,20,
            followUpRounds=5)
        var j=initial(slot("myo",1,null,amplifier=amp))
        j=record(j,"myo:s1")
        val invalid=CustomWorkoutJournalOperation.Record("future",j.revision,
            visit("myo:s1",CustomWorkoutVisitPart.FOLLOW_UP,3),
            KotlinJournalSetOutcome.COMPLETED,
            KotlinPerformedSet(BigDecimal("20"),LoadUnit.LB,4,2))
        assertEquals(CustomWorkoutJournalRefusal.FOLLOW_UP_NOT_AUTHORED,
            (CustomWorkoutJournalDomain.reduce(j,invalid)
                as CustomWorkoutJournalDecision.Rejected).reason)
        j=record(j,"myo:s1",CustomWorkoutVisitPart.FOLLOW_UP,stage=1,reps=4)
        j=record(j,"myo:s1",CustomWorkoutVisitPart.FOLLOW_UP,stage=2,reps=4)
        val old=j.effectiveVisits.getValue(
            visit("myo:s1",CustomWorkoutVisitPart.FOLLOW_UP,1))
        val revise=CustomWorkoutJournalOperation.Record("rewrite",j.revision,
            visit("myo:s1",CustomWorkoutVisitPart.FOLLOW_UP,1),
            KotlinJournalSetOutcome.COMPLETED,
            KotlinPerformedSet(BigDecimal("20"),LoadUnit.LB,2,2),
            old.operation.eventId)
        assertEquals(CustomWorkoutJournalRefusal.INVALID_CORRECTION,
            (CustomWorkoutJournalDomain.reduce(j,revise)
                as CustomWorkoutJournalDecision.Rejected).reason)
    }

    @Test fun changingMachineOrLoadEndsTheMyoSequenceWithoutErasingFactualStage() {
        val amp=CustomSetAmplifier(CustomAmplifierKind.MYO_REPS,4,20,
            followUpRounds=5)
        var j=initial(slot("myo",1,null,amplifier=amp))
        val set="myo:s1"
        val issued=KotlinPerformedSet(BigDecimal("20"),LoadUnit.LB,16,1,"A")
        val recordMain=CustomWorkoutJournalOperation.Record("main-load",0,
            visit(set),KotlinJournalSetOutcome.COMPLETED,issued)
        j=(CustomWorkoutJournalDomain.reduce(j,recordMain)
            as CustomWorkoutJournalDecision.Proposed).draft
        assertEquals(visit(set,CustomWorkoutVisitPart.FOLLOW_UP,1),next(j))
        val differentMachine=CustomWorkoutJournalOperation.Record("change-machine",1,
            visit(set,CustomWorkoutVisitPart.FOLLOW_UP,1),
            KotlinJournalSetOutcome.COMPLETED,
            KotlinPerformedSet(BigDecimal("20"),LoadUnit.LB,4,2,"B"))
        j=(CustomWorkoutJournalDomain.reduce(j,differentMachine)
            as CustomWorkoutJournalDecision.Proposed).draft
        assertTrue(j.canProposeFinish)
        assertNull(next(j))
        assertEquals("B",((j.effectiveVisits.getValue(
            visit(set,CustomWorkoutVisitPart.FOLLOW_UP,1)).operation
            as CustomWorkoutJournalOperation.Record).performed?.machineInstanceId))
        // The same rule applies to a different logged load on the same machine.
        var k=initial(slot("myo",1,null,amplifier=amp))
        k=(CustomWorkoutJournalDomain.reduce(k,recordMain)
            as CustomWorkoutJournalDecision.Proposed).draft
        val changedWeight=CustomWorkoutJournalOperation.Record("change-load",1,
            visit(set,CustomWorkoutVisitPart.FOLLOW_UP,1),
            KotlinJournalSetOutcome.COMPLETED,
            KotlinPerformedSet(BigDecimal("15"),LoadUnit.LB,4,2,"A"))
        k=(CustomWorkoutJournalDomain.reduce(k,changedWeight)
            as CustomWorkoutJournalDecision.Proposed).draft
        assertTrue(k.canProposeFinish)
        assertNull(next(k))
    }

    @Test fun restPauseEffortsAreRecordedIndividuallyWithShortRestsAndHardStageCap() {
        val amp=CustomSetAmplifier(CustomAmplifierKind.REST_PAUSE,
            3,20,followUpRounds=3)
        var j=initial(slot("pause",1,null,amplifier=amp),slot("later",1,null))
        j=record(j,"pause:s1",reps=12)
        for(stage in 1..3) {
            assertEquals(visit("pause:s1",CustomWorkoutVisitPart.FOLLOW_UP,stage),next(j))
            assertEquals(20,rest(j).automaticCue?.seconds)
            j=record(j,"pause:s1",CustomWorkoutVisitPart.FOLLOW_UP,
                stage=stage,reps=3)
        }
        assertEquals(visit("later:s1"),next(j))
        assertEquals(90,rest(j).automaticCue?.seconds)
        assertEquals(3,CustomWorkoutVisitFocusDomain.progress(j).recordedFollowUps)
        assertEquals(3,CustomWorkoutVisitFocusDomain.progress(j).requiredFollowUps)
        assertEquals(3,j.effectiveVisits.keys.count {
            it.setKey=="pause:s1" && it.part==CustomWorkoutVisitPart.FOLLOW_UP
        })
    }

    @Test fun missedRestPauseRepTargetEndsOptionalWorkAndSurvivesColdReplay() {
        val amp=CustomSetAmplifier(CustomAmplifierKind.REST_PAUSE,
            3,20,followUpRounds=3)
        var j=initial(slot("pause",1,null,amplifier=amp))
        j=record(j,"pause:s1",reps=12)
        j=record(j,"pause:s1",CustomWorkoutVisitPart.FOLLOW_UP,
            stage=1,reps=3)
        assertEquals(visit("pause:s1",CustomWorkoutVisitPart.FOLLOW_UP,2),next(j))
        j=record(j,"pause:s1",CustomWorkoutVisitPart.FOLLOW_UP,
            stage=2,reps=2)
        assertNull(next(j))
        assertTrue(j.canProposeFinish)
        assertEquals(2,CustomWorkoutVisitFocusDomain.progress(j).requiredFollowUps)
        var replay=CustomWorkoutJournalDomain.begin(j.workoutId,
            j.originalSnapshot,j.preparedConsentFingerprint)
        for(receipt in j.receipts) {
            replay=(CustomWorkoutJournalDomain.reduce(replay,receipt.operation)
                as CustomWorkoutJournalDecision.Proposed).draft
        }
        assertEquals(j,replay)
        assertNull(next(replay))
    }

    @Test fun restPauseGuardsOutOfOrderAndMainLoadChangesAfterFollowUps() {
        val amp=CustomSetAmplifier(CustomAmplifierKind.REST_PAUSE,
            3,20,followUpRounds=3)
        var j=initial(slot("pause",1,null,amplifier=amp))
        j=record(j,"pause:s1",reps=12)
        val future=CustomWorkoutJournalOperation.Record("invalid-stage",j.revision,
            visit("pause:s1",CustomWorkoutVisitPart.FOLLOW_UP,3),
            KotlinJournalSetOutcome.COMPLETED,
            KotlinPerformedSet(BigDecimal("20"),LoadUnit.LB,3,2))
        assertEquals(CustomWorkoutJournalRefusal.FOLLOW_UP_NOT_AUTHORED,
            (CustomWorkoutJournalDomain.reduce(j,future)
                as CustomWorkoutJournalDecision.Rejected).reason)
        j=record(j,"pause:s1",CustomWorkoutVisitPart.FOLLOW_UP,stage=1,reps=3)
        val old=j.effectiveVisits.getValue(visit("pause:s1")).operation
            as CustomWorkoutJournalOperation.Record
        val corrected=CustomWorkoutJournalOperation.Record(
            "changed-activation",j.revision,visit("pause:s1"),
            KotlinJournalSetOutcome.COMPLETED,
            KotlinPerformedSet(BigDecimal("25"),LoadUnit.LB,12,2),old.eventId)
        assertEquals(CustomWorkoutJournalRefusal.INVALID_CORRECTION,
            (CustomWorkoutJournalDomain.reduce(j,corrected)
                as CustomWorkoutJournalDecision.Rejected).reason)
        assertEquals(visit("pause:s1",CustomWorkoutVisitPart.FOLLOW_UP,2),next(j))
    }

    @Test fun activationLoadCannotChangeOnceMyoFollowUpsExist() {
        val amp=CustomSetAmplifier(CustomAmplifierKind.MYO_REPS,4,20,
            followUpRounds=5)
        var j=initial(slot("myo",1,null,amplifier=amp))
        j=record(j,"myo:s1",reps=16)
        j=record(j,"myo:s1",CustomWorkoutVisitPart.FOLLOW_UP,stage=1,reps=4)
        val current=j.effectiveVisits.getValue(visit("myo:s1")).operation
            as CustomWorkoutJournalOperation.Record
        val correction=CustomWorkoutJournalOperation.Record(
            "change-old-load",j.revision,visit("myo:s1"),
            KotlinJournalSetOutcome.COMPLETED,
            KotlinPerformedSet(BigDecimal("25"),LoadUnit.LB,16,2),
            current.eventId)
        val rejected=CustomWorkoutJournalDomain.reduce(j,correction)
            as CustomWorkoutJournalDecision.Rejected
        assertEquals(CustomWorkoutJournalRefusal.INVALID_CORRECTION,rejected.reason)
        assertEquals(visit("myo:s1",CustomWorkoutVisitPart.FOLLOW_UP,2),next(j))
    }

}
