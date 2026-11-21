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
        corrects:String?=null,stage:Int=1,reps:Int=8,
        actualWeight:String?=null,station:String?=null):CustomWorkoutJournalDraft {
        val set=j.snapshot.slots.flatMap{it.sets}.single{it.setKey==key}
        val load=if(part==CustomWorkoutVisitPart.FOLLOW_UP)
            set.prescription.amplifier?.let{
                AdvancedAmplifierProtocolDomain.followUpLoadForStage(
                    it,set.prescription.load,stage)
            } ?:set.prescription.load
        else set.prescription.load
        val verified=load as? DraftSetLoad.Verified
        val actual=if(outcome==KotlinJournalSetOutcome.SKIPPED)null
            else KotlinPerformedSet(actualWeight?.let(::BigDecimal) ?: verified?.value,
                verified?.unit,
                if(outcome==KotlinJournalSetOutcome.FAILED_ATTEMPT)0 else reps,
                if(outcome==KotlinJournalSetOutcome.FAILED_ATTEMPT)null else 2,
                station)
        val operation=CustomWorkoutJournalOperation.Record("event-${j.revision}",j.revision,
            visit(key,part,stage),outcome,actual,corrects)
        return (CustomWorkoutJournalDomain.reduce(j,operation) as CustomWorkoutJournalDecision.Proposed).draft
    }
    private fun next(j:CustomWorkoutJournalDraft)=CustomWorkoutJournalDomain.nextUnresolvedVisit(j)
    private fun rest(j:CustomWorkoutJournalDraft)=KotlinWorkoutRestDomain.inspect(j,next(j))
    private fun order(j:CustomWorkoutJournalDraft)=CustomWorkoutExecutionDomain.orderedSets(j.snapshot).map{it.set.setKey}


    @Test fun numericScaleOfPhysicalWeightNeverEndsAValidStagedAmplifier() {
        val protocols=listOf(
            CustomSetAmplifier(CustomAmplifierKind.MYO_REPS,4,20,
                followUpRounds=3) to 4,
            CustomSetAmplifier(CustomAmplifierKind.REST_PAUSE,3,20,
                followUpRounds=3) to 3,
            CustomSetAmplifier(CustomAmplifierKind.CLUSTER,2,30,
                followUpRounds=3) to 2
        )
        for((amp,miniReps) in protocols) {
            var j=initial(slot("stage",1,null,amplifier=amp))
            val original=j.snapshot
            // Same real 20 lb setting, entered with different decimal scales.
            j=record(j,"stage:s1",reps=16,actualWeight="20.000",
                station="machine_A")
            assertEquals(visit("stage:s1",CustomWorkoutVisitPart.FOLLOW_UP,1),next(j))
            j=record(j,"stage:s1",CustomWorkoutVisitPart.FOLLOW_UP,
                stage=1,reps=miniReps,actualWeight="20.0",station="machine_A")
            assertEquals(visit("stage:s1",CustomWorkoutVisitPart.FOLLOW_UP,2),next(j))
            assertFalse(j.canProposeFinish)

            // Recovery from actual journal receipts must make the same choice,
            // without normalizing or rewriting any saved athlete observations.
            var replay=CustomWorkoutJournalDomain.begin(j.workoutId,
                j.originalSnapshot,j.preparedConsentFingerprint)
            for(receipt in j.receipts) {
                replay=(CustomWorkoutJournalDomain.reduce(
                    replay,receipt.operation) as CustomWorkoutJournalDecision.Proposed).draft
            }
            assertEquals(j,replay)
            for(stage in 2..3) {
                assertEquals(visit("stage:s1",CustomWorkoutVisitPart.FOLLOW_UP,
                    stage),next(replay))
                replay=record(replay,"stage:s1",CustomWorkoutVisitPart.FOLLOW_UP,
                    stage=stage,reps=miniReps,
                    actualWeight=if(stage==2)"20.00" else "20",
                    station="machine_A")
            }
            assertNull(next(replay))
            assertTrue(replay.canProposeFinish)
            assertEquals(original,replay.snapshot)
            assertEquals(listOf(BigDecimal("20.000"),BigDecimal("20.0"),
                BigDecimal("20.00"),BigDecimal("20")),replay.receipts.map {
                (it.operation as CustomWorkoutJournalOperation.Record).performed!!.load
            })
            val finished=(CustomWorkoutJournalDomain.reduce(replay,
                CustomWorkoutJournalOperation.Finish("finish-stage",replay.revision))
                as CustomWorkoutJournalDecision.Proposed).draft
            assertNotNull(finished.finishedByEventId)
            assertEquals(4,finished.effectiveVisits.size)
        }
    }

    @Test fun alteredPhysicalLoadOrMachineStillEndsOptionalStagedWork() {
        val amp=CustomSetAmplifier(CustomAmplifierKind.MYO_REPS,4,20,
            followUpRounds=5)
        for((load,machine) in listOf("19.5" to "machine_A",
            "20.00" to "machine_B")) {
            var j=initial(slot("myo",1,null,amplifier=amp))
            j=record(j,"myo:s1",reps=15,actualWeight="20",
                station="machine_A")
            j=record(j,"myo:s1",CustomWorkoutVisitPart.FOLLOW_UP,
                stage=1,reps=4,actualWeight=load,station=machine)
            // The different observation remains factual, but no later
            // same-load mini-set can be manufactured from changed stock.
            assertEquals(2,j.effectiveVisits.size)
            assertNull(next(j))
            assertTrue(j.canProposeFinish)
            assertEquals(BigDecimal(load),(j.receipts.last().operation
                as CustomWorkoutJournalOperation.Record).performed?.load)
        }
    }

    @Test fun numericScalePreservesCorrectedActivationAndFinalSetPartials() {
        val amp=CustomSetAmplifier(CustomAmplifierKind.LENGTHENED_PARTIALS,3,0)
        var j=initial(slot("partials",1,null,amplifier=amp))
        j=record(j,"partials:s1",reps=10,actualWeight="20.00",
            station="machine_A")
        j=record(j,"partials:s1",CustomWorkoutVisitPart.FOLLOW_UP,
            reps=3,actualWeight="20",station="machine_A")
        assertTrue(j.canProposeFinish)
        // Changing ONLY scale or other non-load information remains a
        // permissible correction after dependent factual partial work.
        val mainKey=visit("partials:s1")
        val main=j.effectiveVisits.getValue(mainKey)
            .operation as CustomWorkoutJournalOperation.Record
        val corrected=CustomWorkoutJournalOperation.Record(
            "scale-correction",j.revision,mainKey,
            KotlinJournalSetOutcome.COMPLETED,
            KotlinPerformedSet(BigDecimal("20.000"),LoadUnit.LB,11,2,"machine_A"),
            main.eventId)
        j=(CustomWorkoutJournalDomain.reduce(j,corrected)
            as CustomWorkoutJournalDecision.Proposed).draft
        assertEquals(11,(j.effectiveVisits.getValue(mainKey).operation
            as CustomWorkoutJournalOperation.Record).performed?.reps)
        assertTrue(j.canProposeFinish)
        assertEquals(BigDecimal("20"),(j.snapshot.slots.single().sets.single()
            .prescription.load as DraftSetLoad.Verified).value)

        val changed=corrected.copy(eventId="changed-mass",
            expectedRevision=j.revision,
            performed=corrected.performed?.copy(load=BigDecimal("21")),
            correctsEventId="scale-correction")
        assertEquals(CustomWorkoutJournalRefusal.INVALID_CORRECTION,
            (CustomWorkoutJournalDomain.reduce(j,changed)
                as CustomWorkoutJournalDecision.Rejected).reason)
        val switched=changed.copy(eventId="changed-station",
            performed=corrected.performed?.copy(machineInstanceId="machine_B"))
        assertEquals(CustomWorkoutJournalRefusal.INVALID_CORRECTION,
            (CustomWorkoutJournalDomain.reduce(j,switched)
                as CustomWorkoutJournalDecision.Rejected).reason)
        val original=j
        var replay=CustomWorkoutJournalDomain.begin(j.workoutId,
            j.originalSnapshot,j.preparedConsentFingerprint)
        for(receipt in j.receipts)
            replay=(CustomWorkoutJournalDomain.reduce(replay,receipt.operation)
                as CustomWorkoutJournalDecision.Proposed).draft
        assertEquals(original,replay)
        assertEquals(1,replay.effectiveVisits.keys.count{
            it.part==CustomWorkoutVisitPart.FOLLOW_UP
        })
    }

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

    @Test fun changedPhysicalStationOrLoadEndsRestPauseButNeverDeletesReceipts() {
        val amp=CustomSetAmplifier(CustomAmplifierKind.REST_PAUSE,
            3,20,followUpRounds=3)
        var station=initial(slot("pause",1,null,amplifier=amp))
        val main=CustomWorkoutJournalOperation.Record("main-A",0,
            visit("pause:s1"),KotlinJournalSetOutcome.COMPLETED,
            KotlinPerformedSet(BigDecimal("20"),LoadUnit.LB,12,2,"A"))
        station=(CustomWorkoutJournalDomain.reduce(station,main)
            as CustomWorkoutJournalDecision.Proposed).draft
        val changeStation=CustomWorkoutJournalOperation.Record("stage-B",station.revision,
            visit("pause:s1",CustomWorkoutVisitPart.FOLLOW_UP,1),
            KotlinJournalSetOutcome.COMPLETED,
            KotlinPerformedSet(BigDecimal("20"),LoadUnit.LB,3,2,"B"))
        station=(CustomWorkoutJournalDomain.reduce(station,changeStation)
            as CustomWorkoutJournalDecision.Proposed).draft
        assertNull(next(station))
        assertEquals(2,station.effectiveVisits.size)
        assertEquals("B",((station.effectiveVisits.getValue(
            visit("pause:s1",CustomWorkoutVisitPart.FOLLOW_UP,1)).operation
            as CustomWorkoutJournalOperation.Record).performed?.machineInstanceId))

        var load=initial(slot("pause",1,null,amplifier=amp))
        load=(CustomWorkoutJournalDomain.reduce(load,main)
            as CustomWorkoutJournalDecision.Proposed).draft
        val changeWeight=CustomWorkoutJournalOperation.Record("stage-lower",load.revision,
            visit("pause:s1",CustomWorkoutVisitPart.FOLLOW_UP,1),
            KotlinJournalSetOutcome.COMPLETED,
            KotlinPerformedSet(BigDecimal("15"),LoadUnit.LB,3,2,"A"))
        load=(CustomWorkoutJournalDomain.reduce(load,changeWeight)
            as CustomWorkoutJournalDecision.Proposed).draft
        assertNull(next(load))
        assertEquals(2,load.effectiveVisits.size)
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

    @Test fun plannedClustersStageActualQualityRepsAndHoldSupersetPartnerUntilResolved() {
        val amp=CustomSetAmplifier(CustomAmplifierKind.CLUSTER,
            2,30,followUpRounds=4)
        var j=initial(slot("cluster",1,"group",amplifier=amp),
            slot("partner",1,"group"))
        j=record(j,"cluster:s1",reps=5)
        for(stage in 1..4) {
            assertEquals(visit("cluster:s1",CustomWorkoutVisitPart.FOLLOW_UP,stage),
                next(j))
            assertEquals(30,rest(j).automaticCue?.seconds)
            j=record(j,"cluster:s1",CustomWorkoutVisitPart.FOLLOW_UP,
                stage=stage,reps=2)
        }
        assertEquals(visit("partner:s1"),next(j))
        assertEquals(0,rest(j).automaticCue?.seconds)
        assertEquals(4,CustomWorkoutVisitFocusDomain.progress(j).recordedFollowUps)
        assertEquals(4,CustomWorkoutVisitFocusDomain.progress(j).requiredFollowUps)
        assertEquals(4,j.effectiveVisits.keys.count {
            it.setKey=="cluster:s1" &&
                it.part==CustomWorkoutVisitPart.FOLLOW_UP
        })
        assertTrue(j.effectiveVisits.values.all {
            ((it.operation as CustomWorkoutJournalOperation.Record)
                .performed?.load)==BigDecimal("20")
        })
        j=record(j,"partner:s1")
        assertNull(next(j))
        assertTrue(j.canProposeFinish)
    }

    @Test fun clusterShortfallAndEarlyEndLeaveDurableTruthWithoutInventingExtraStages() {
        val amp=CustomSetAmplifier(CustomAmplifierKind.CLUSTER,
            3,30,followUpRounds=5)
        var j=initial(slot("cluster",1,null,amplifier=amp))
        j=record(j,"cluster:s1",reps=5)
        j=record(j,"cluster:s1",CustomWorkoutVisitPart.FOLLOW_UP,
            stage=1,reps=3)
        assertEquals(visit("cluster:s1",CustomWorkoutVisitPart.FOLLOW_UP,2),
            next(j))
        j=record(j,"cluster:s1",CustomWorkoutVisitPart.FOLLOW_UP,
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

        var skipped=initial(slot("stop",1,null,amplifier=amp))
        skipped=record(skipped,"stop:s1",reps=5)
        skipped=record(skipped,"stop:s1",CustomWorkoutVisitPart.FOLLOW_UP,
            stage=1,reps=3)
        skipped=record(skipped,"stop:s1",CustomWorkoutVisitPart.FOLLOW_UP,
            outcome=KotlinJournalSetOutcome.SKIPPED,stage=2)
        assertNull(next(skipped))
        assertTrue(skipped.canProposeFinish)
        assertEquals(2,skipped.effectiveVisits.keys.count{
            it.part==CustomWorkoutVisitPart.FOLLOW_UP
        })
        assertEquals(KotlinJournalSetOutcome.SKIPPED,
            (skipped.effectiveVisits.getValue(visit(
                "stop:s1",CustomWorkoutVisitPart.FOLLOW_UP,2)).operation
                as CustomWorkoutJournalOperation.Record).outcome)
    }

    @Test fun clustersRejectOutOfOrderStageAndRetrospectiveStationLoadChanges() {
        val amp=CustomSetAmplifier(CustomAmplifierKind.CLUSTER,
            2,30,followUpRounds=3)
        var j=initial(slot("cluster",1,null,amplifier=amp))
        j=record(j,"cluster:s1",reps=5)
        val future=CustomWorkoutJournalOperation.Record("future-cluster",
            j.revision,visit("cluster:s1",CustomWorkoutVisitPart.FOLLOW_UP,3),
            KotlinJournalSetOutcome.COMPLETED,
            KotlinPerformedSet(BigDecimal("20"),LoadUnit.LB,2,2))
        assertEquals(CustomWorkoutJournalRefusal.FOLLOW_UP_NOT_AUTHORED,
            (CustomWorkoutJournalDomain.reduce(j,future)
                as CustomWorkoutJournalDecision.Rejected).reason)
        j=record(j,"cluster:s1",CustomWorkoutVisitPart.FOLLOW_UP,stage=1,reps=2)
        val old=j.effectiveVisits.getValue(visit("cluster:s1")).operation
            as CustomWorkoutJournalOperation.Record
        val altered=CustomWorkoutJournalOperation.Record(
            "alter-activation",j.revision,visit("cluster:s1"),
            KotlinJournalSetOutcome.COMPLETED,
            KotlinPerformedSet(BigDecimal("25"),LoadUnit.LB,5,2),old.eventId)
        assertEquals(CustomWorkoutJournalRefusal.INVALID_CORRECTION,
            (CustomWorkoutJournalDomain.reduce(j,altered)
                as CustomWorkoutJournalDecision.Rejected).reason)
        val wrongStation=CustomWorkoutJournalOperation.Record(
            "other-station",j.revision,
            visit("cluster:s1",CustomWorkoutVisitPart.FOLLOW_UP,2),
            KotlinJournalSetOutcome.COMPLETED,
            KotlinPerformedSet(BigDecimal("20"),LoadUnit.LB,2,2,"B"))
        val changed=(CustomWorkoutJournalDomain.reduce(j,wrongStation)
            as CustomWorkoutJournalDecision.Proposed).draft
        assertNull(next(changed))
        assertEquals(3,changed.effectiveVisits.size)
        assertEquals("B",((changed.effectiveVisits.getValue(
            visit("cluster:s1",CustomWorkoutVisitPart.FOLLOW_UP,2)).operation
            as CustomWorkoutJournalOperation.Record).performed?.machineInstanceId))
    }

    @Test fun finalSetLengthenedPartialsLogSeparateActualsWithoutNewRegularWork() {
        val partial=CustomSetAmplifier(CustomAmplifierKind.LENGTHENED_PARTIALS,3,0,
            followUpActiveSeconds=15)
        val original=slot("partials",2,null,amplifier=partial)
        val configured=original.copy(sets=original.sets.mapIndexed{index,set->
            if(index==0)set.copy(prescription=set.prescription.copy(amplifier=null))
            else set
        })
        var j=initial(configured)
        assertEquals(visit("partials:s1"),next(j))
        j=record(j,"partials:s1")
        assertEquals(visit("partials:s2"),next(j))
        j=record(j,"partials:s2",reps=10)
        assertEquals(visit("partials:s2",CustomWorkoutVisitPart.FOLLOW_UP),next(j))
        assertFalse(j.canProposeFinish)
        j=record(j,"partials:s2",CustomWorkoutVisitPart.FOLLOW_UP,reps=3)
        assertNull(next(j))
        assertTrue(j.canProposeFinish)
        assertEquals(3,j.effectiveVisits.size)
        assertEquals(1,CustomWorkoutVisitFocusDomain.progress(j).recordedFollowUps)
        val savedPartial=j.effectiveVisits.getValue(visit(
            "partials:s2",CustomWorkoutVisitPart.FOLLOW_UP)).operation
            as CustomWorkoutJournalOperation.Record
        assertEquals(3,savedPartial.performed?.reps)
        assertEquals(BigDecimal("20"),savedPartial.performed?.load)
        assertEquals(CustomAmplifierKind.LENGTHENED_PARTIALS,
            configured.sets.last().prescription.amplifier?.kind)
        val closed=(CustomWorkoutJournalDomain.reduce(j,
            CustomWorkoutJournalOperation.Finish("finish-partials",j.revision))
            as CustomWorkoutJournalDecision.Proposed).draft
        val evidence=CustomWorkoutHistoryEvidenceDomain.project(closed)
            as CustomHistoryEvidenceResult.Candidate
        assertEquals(3,evidence.evidence.visits.size)
        assertEquals(2,evidence.evidence.visits.count{
            it.part==CustomWorkoutVisitPart.MAIN
        })
        val partialHistory=evidence.evidence.visits.single{
            it.part==CustomWorkoutVisitPart.FOLLOW_UP
        }
        assertEquals(CustomAmplifierKind.LENGTHENED_PARTIALS,
            partialHistory.amplifierKind)
        assertEquals(3,partialHistory.actual?.reps)
        assertFalse(evidence.evidence.canApplyProgression)
    }

    @Test fun partialExtensionCannotQuietlyChangeActivationLoadOrPhysicalMachine() {
        val amp=CustomSetAmplifier(CustomAmplifierKind.LENGTHENED_PARTIALS,3,0)
        var journal=initial(slot("partial",1,null,amplifier=amp))
        journal=record(journal,"partial:s1",reps=10)
        val follow=visit("partial:s1",CustomWorkoutVisitPart.FOLLOW_UP)
        val changedLoad=CustomWorkoutJournalOperation.Record(
            "wrong-partial-load",journal.revision,follow,
            KotlinJournalSetOutcome.COMPLETED,
            KotlinPerformedSet(BigDecimal("15"),LoadUnit.LB,3,2))
        assertEquals(CustomWorkoutJournalRefusal.INVALID_ACTUAL,
            (CustomWorkoutJournalDomain.reduce(journal,changedLoad)
                as CustomWorkoutJournalDecision.Rejected).reason)
        val wrongMachine=changedLoad.copy(eventId="wrong-partial-machine",
            performed=KotlinPerformedSet(BigDecimal("20"),LoadUnit.LB,3,2,"machine_B"))
        assertEquals(CustomWorkoutJournalRefusal.INVALID_ACTUAL,
            (CustomWorkoutJournalDomain.reduce(journal,wrongMachine)
                as CustomWorkoutJournalDecision.Rejected).reason)
        journal=record(journal,"partial:s1",CustomWorkoutVisitPart.FOLLOW_UP,
            reps=3)
        val originalMain=journal.effectiveVisits.getValue(visit("partial:s1"))
            .operation as CustomWorkoutJournalOperation.Record
        val changeMain=CustomWorkoutJournalOperation.Record(
            "main-after-partial",journal.revision,visit("partial:s1"),
            KotlinJournalSetOutcome.COMPLETED,
            KotlinPerformedSet(BigDecimal("25"),LoadUnit.LB,10,2),
            originalMain.eventId)
        assertEquals(CustomWorkoutJournalRefusal.INVALID_CORRECTION,
            (CustomWorkoutJournalDomain.reduce(journal,changeMain)
                as CustomWorkoutJournalDecision.Rejected).reason)
        assertNull(next(journal))
        assertTrue(journal.canProposeFinish)
    }

    @Test fun singleDropMustBeBelowRealActivationOrItCannotBeIssued() {
        fun verified(n:String)=DraftSetLoad.Verified(LoadUnit.LB,
            LoadBasis.DUMBBELL_PER_HAND,BigDecimal(n))
        val amp=CustomSetAmplifier(CustomAmplifierKind.DROP_SET,8,10,
            followUpLoad=verified("15"))
        for(actualWeight in listOf("12","15","15.00")) {
            var journal=initial(slot("single",1,null,amplifier=amp))
            journal=record(journal,"single:s1",actualWeight=actualWeight,
                station="machine-A")
            val set=journal.snapshot.slots.single().sets.single()
            assertTrue("An increase or unchanged load cannot be called a drop",
                CustomWorkoutExecutionDomain.followUpVisits(journal,set).isEmpty())
            assertNull(next(journal))
            assertTrue(journal.canProposeFinish)
            val invented=CustomWorkoutJournalOperation.Record(
                "wrong-single-drop-${actualWeight.replace('.', '_')}",journal.revision,
                visit("single:s1",CustomWorkoutVisitPart.FOLLOW_UP),
                KotlinJournalSetOutcome.COMPLETED,
                KotlinPerformedSet(BigDecimal("15"),LoadUnit.LB,8,2,
                    "machine-A"))
            assertEquals(CustomWorkoutJournalRefusal.FOLLOW_UP_NOT_AUTHORED,
                (CustomWorkoutJournalDomain.reduce(journal,invented)
                    as CustomWorkoutJournalDecision.Rejected).reason)
        }
        for(actualWeight in listOf("20","17.5")) {
            var journal=initial(slot("single",1,null,amplifier=amp))
            journal=record(journal,"single:s1",actualWeight=actualWeight,
                station="machine-A")
            assertEquals(visit("single:s1",CustomWorkoutVisitPart.FOLLOW_UP),
                next(journal))
            journal=record(journal,"single:s1",CustomWorkoutVisitPart.FOLLOW_UP,
                actualWeight="15",station="machine-A")
            assertNull(next(journal))
            assertTrue(journal.canProposeFinish)
            assertEquals(BigDecimal(actualWeight), (journal.receipts.first()
                .operation as CustomWorkoutJournalOperation.Record).performed!!.load)
            assertEquals(BigDecimal("15"),(journal.receipts.last()
                .operation as CustomWorkoutJournalOperation.Record).performed!!.load)
            var replay=CustomWorkoutJournalDomain.begin(journal.workoutId,
                journal.originalSnapshot,journal.preparedConsentFingerprint)
            journal.receipts.forEach{receipt->
                replay=(CustomWorkoutJournalDomain.reduce(replay,receipt.operation)
                    as CustomWorkoutJournalDecision.Proposed).draft
            }
            assertEquals(journal,replay)
            assertNull(next(replay))
        }
    }

    @Test fun malformedLegacySingleDropCannotGetAnUnreviewedWeightFromJournal() {
        fun verified(n:String)=DraftSetLoad.Verified(LoadUnit.LB,
            LoadBasis.DUMBBELL_PER_HAND,BigDecimal(n))
        val invalid=listOf(
            CustomSetAmplifier(CustomAmplifierKind.DROP_SET,8,10,
                followUpLoad=verified("20")),
            CustomSetAmplifier(CustomAmplifierKind.DROP_SET,8,10,
                followUpLoad=verified("25")),
            CustomSetAmplifier(CustomAmplifierKind.DROP_SET,8,10,
                followUpLoad=null),
            CustomSetAmplifier(CustomAmplifierKind.DROP_SET,8,10,
                followUpLoad=DraftSetLoad.Verified(LoadUnit.KG,
                    LoadBasis.DUMBBELL_PER_HAND,BigDecimal("7")))
        )
        invalid.forEach { amp->
            var journal=initial(slot("bad",1,null,amplifier=amp))
            journal=record(journal,"bad:s1",actualWeight="20")
            val set=journal.snapshot.slots.single().sets.single()
            assertTrue(amp.toString(),
                CustomWorkoutExecutionDomain.followUpVisits(journal,set).isEmpty())
            assertNull(next(journal))
            assertTrue(journal.canProposeFinish)
        }
    }

    @Test fun twoPhysicalDropStagesRecordTheirOwnActualsBeforeNextSupersetMovement() {
        fun verified(n:String)=DraftSetLoad.Verified(LoadUnit.LB,
            LoadBasis.DUMBBELL_PER_HAND,BigDecimal(n))
        val amp=CustomSetAmplifier(CustomAmplifierKind.DROP_SET,8,10,
            followUpLoad=verified("15"),followUpRounds=2,
            secondDropLoad=verified("10"))
        var j=initial(slot("drop",1,"pair",amplifier=amp),
            slot("partner",1,"pair"))
        assertEquals(visit("drop:s1"),next(j))
        j=record(j,"drop:s1",reps=10)
        assertEquals(visit("drop:s1",CustomWorkoutVisitPart.FOLLOW_UP,1),next(j))
        assertEquals(10,rest(j).automaticCue?.seconds)
        j=record(j,"drop:s1",CustomWorkoutVisitPart.FOLLOW_UP,
            stage=1,reps=8)
        assertEquals(visit("drop:s1",CustomWorkoutVisitPart.FOLLOW_UP,2),next(j))
        j=record(j,"drop:s1",CustomWorkoutVisitPart.FOLLOW_UP,
            stage=2,reps=7)
        assertEquals(visit("partner:s1"),next(j))
        assertEquals(0,rest(j).automaticCue?.seconds)
        val stages=j.effectiveVisits.entries.filter{it.key.setKey=="drop:s1" &&
            it.key.part==CustomWorkoutVisitPart.FOLLOW_UP}.sortedBy{it.key.stage}
        assertEquals(listOf(BigDecimal("15"),BigDecimal("10")),stages.map{
            ((it.value.operation as CustomWorkoutJournalOperation.Record)
                .performed?.load)
        })
        assertEquals(2,CustomWorkoutVisitFocusDomain.progress(j).recordedFollowUps)
        j=record(j,"partner:s1")
        assertNull(next(j))
        assertTrue(j.canProposeFinish)
        val closed=(CustomWorkoutJournalDomain.reduce(j,
            CustomWorkoutJournalOperation.Finish("finish-two-drops",j.revision))
            as CustomWorkoutJournalDecision.Proposed).draft
        val history=CustomWorkoutHistoryEvidenceDomain.project(closed)
            as CustomHistoryEvidenceResult.Candidate
        assertEquals(listOf(BigDecimal("15"),BigDecimal("10")),
            history.evidence.visits.filter{it.part==CustomWorkoutVisitPart.FOLLOW_UP}
                .map{(it.prescribedLoad as DraftSetLoad.Verified).value})
        assertEquals(2,history.evidence.visits.count{
            it.part==CustomWorkoutVisitPart.MAIN
        })
        assertFalse(history.evidence.canApplyProgression)
    }

    @Test fun changedFirstDropNeverAutoIssuesSecondAndActivationCorrectionIsProtected() {
        fun verified(n:String)=DraftSetLoad.Verified(LoadUnit.LB,
            LoadBasis.DUMBBELL_PER_HAND,BigDecimal(n))
        val amp=CustomSetAmplifier(CustomAmplifierKind.DROP_SET,8,10,
            followUpLoad=verified("15"),followUpRounds=2,
            secondDropLoad=verified("10"))
        var j=initial(slot("drop",1,null,amplifier=amp))
        j=record(j,"drop:s1")
        val wrongLoad=CustomWorkoutJournalOperation.Record(
            "actual-different",j.revision,
            visit("drop:s1",CustomWorkoutVisitPart.FOLLOW_UP,1),
            KotlinJournalSetOutcome.COMPLETED,
            KotlinPerformedSet(BigDecimal("12"),LoadUnit.LB,8,2))
        j=(CustomWorkoutJournalDomain.reduce(j,wrongLoad)
            as CustomWorkoutJournalDecision.Proposed).draft
        assertNull(next(j))
        assertEquals(1,CustomWorkoutVisitFocusDomain.progress(j).recordedFollowUps)
        assertTrue(j.canProposeFinish)
        val outOfOrder=CustomWorkoutJournalOperation.Record(
            "unissued-second",j.revision,
            visit("drop:s1",CustomWorkoutVisitPart.FOLLOW_UP,2),
            KotlinJournalSetOutcome.COMPLETED,
            KotlinPerformedSet(BigDecimal("10"),LoadUnit.LB,8,2))
        assertEquals(CustomWorkoutJournalRefusal.FOLLOW_UP_NOT_AUTHORED,
            (CustomWorkoutJournalDomain.reduce(j,outOfOrder)
                as CustomWorkoutJournalDecision.Rejected).reason)

        var exact=initial(slot("drop",1,null,amplifier=amp))
        exact=record(exact,"drop:s1")
        exact=record(exact,"drop:s1",CustomWorkoutVisitPart.FOLLOW_UP,stage=1)
        val priorMain=exact.effectiveVisits.getValue(visit("drop:s1"))
            .operation as CustomWorkoutJournalOperation.Record
        val attempt=CustomWorkoutJournalOperation.Record(
            "attempt-rebase",exact.revision,visit("drop:s1"),
            KotlinJournalSetOutcome.COMPLETED,
            KotlinPerformedSet(BigDecimal("25"),LoadUnit.LB,8,2),
            priorMain.eventId)
        assertEquals(CustomWorkoutJournalRefusal.INVALID_CORRECTION,
            (CustomWorkoutJournalDomain.reduce(exact,attempt)
                as CustomWorkoutJournalDecision.Rejected).reason)
        exact=record(exact,"drop:s1",CustomWorkoutVisitPart.FOLLOW_UP,
            outcome=KotlinJournalSetOutcome.SKIPPED,stage=2)
        assertNull(next(exact))
        assertTrue(exact.canProposeFinish)
        assertEquals(2,exact.effectiveVisits.keys.count{
            it.part==CustomWorkoutVisitPart.FOLLOW_UP
        })
    }

    @Test fun noDropStageIsIssuedIfActualActivationIsBelowFirstReduction() {
        fun verified(n:String)=DraftSetLoad.Verified(LoadUnit.LB,
            LoadBasis.DUMBBELL_PER_HAND,BigDecimal(n))
        val amp=CustomSetAmplifier(CustomAmplifierKind.DROP_SET,8,10,
            followUpLoad=verified("15"),followUpRounds=2,
            secondDropLoad=verified("10"))
        var j=initial(slot("safe",1,null,amplifier=amp))
        // Even if the issued activation was 20 lb, the athlete really
        // lifted 12 lb. Calling 15 lb a drop would be a dangerous lie.
        val actual=CustomWorkoutJournalOperation.Record(
            "activation-lighter-than-drop",j.revision,visit("safe:s1"),
            KotlinJournalSetOutcome.COMPLETED,
            KotlinPerformedSet(BigDecimal("12"),LoadUnit.LB,8,2))
        j=(CustomWorkoutJournalDomain.reduce(j,actual)
            as CustomWorkoutJournalDecision.Proposed).draft
        assertTrue(CustomWorkoutExecutionDomain.followUpVisits(
            j,j.snapshot.slots.single().sets.single()).isEmpty())
        assertNull(next(j))
        assertTrue(j.canProposeFinish)
        val attempt=CustomWorkoutJournalOperation.Record(
            "unapproved-higher-weight",j.revision,
            visit("safe:s1",CustomWorkoutVisitPart.FOLLOW_UP,1),
            KotlinJournalSetOutcome.COMPLETED,
            KotlinPerformedSet(BigDecimal("15"),LoadUnit.LB,8,2))
        assertEquals(CustomWorkoutJournalRefusal.FOLLOW_UP_NOT_AUTHORED,
            (CustomWorkoutJournalDomain.reduce(j,attempt)
                as CustomWorkoutJournalDecision.Rejected).reason)
    }


    @Test fun mainSetTechniqueIsVisibleBeforeAndAfterMyoStagesAndNeverInventsReps() {
        val amp=CustomSetAmplifier(CustomAmplifierKind.MYO_REPS,5,20,
            followUpRounds=5)
        var j=initial(slot("a",1,null,amplifier=amp))
        val set=j.snapshot.slots.single().sets.single()
        val planned=CustomAmplifierSetSummaryDomain.review(j,set)!!
        assertEquals("Myo-reps",planned.title)
        assertTrue(planned.planned.contains("5 mini-sets"))
        assertEquals("Planned after this working set",planned.progress)

        j=record(j,"a:s1",reps=16)
        assertTrue(CustomAmplifierSetSummaryDomain.review(j,set)!!.progress
            .contains("Next: Myo-reps · mini-set 1 of 5"))
        j=record(j,"a:s1",CustomWorkoutVisitPart.FOLLOW_UP,stage=1,reps=5)
        assertTrue(CustomAmplifierSetSummaryDomain.review(j,set)!!.progress
            .contains("1 of 5 logged · Next:"))
        j=record(j,"a:s1",CustomWorkoutVisitPart.FOLLOW_UP,stage=2,reps=4)
        j=record(j,"a:s1",CustomWorkoutVisitPart.FOLLOW_UP,stage=3,reps=3)
        assertEquals("Sequence ended · 3 of 5 logged",
            CustomAmplifierSetSummaryDomain.review(j,set)!!.progress)
        assertEquals("Performed mini-sets: 5 + 4 + 3 reps",
            CustomAmplifierSetSummaryDomain.review(j,set)!!.actuals)
        assertEquals(3,j.effectiveVisits.keys.count{
            it.part==CustomWorkoutVisitPart.FOLLOW_UP
        })
        val skipped=record(initial(slot("a",1,null,amplifier=amp)),
            "a:s1",outcome=KotlinJournalSetOutcome.SKIPPED)
        assertTrue(CustomAmplifierSetSummaryDomain.review(skipped,set)!!.progress
            .startsWith("Not performed"))
    }

    @Test fun mainSetShowsBothRealDropWeightsAndOnlyLoggedStages() {
        val amp=CustomSetAmplifier(CustomAmplifierKind.DROP_SET,8,0,
            DraftSetLoad.Verified(LoadUnit.LB,LoadBasis.DUMBBELL_PER_HAND,
                BigDecimal("15")),40,2,
            DraftSetLoad.Verified(LoadUnit.LB,LoadBasis.DUMBBELL_PER_HAND,
                BigDecimal("10")))
        var j=initial(slot("drop",1,null,amplifier=amp))
        val set=j.snapshot.slots.single().sets.single()
        val planned=CustomAmplifierSetSummaryDomain.review(j,set)!!
        assertTrue(planned.planned.contains("15 lb → 10 lb"))
        assertTrue(planned.progress.startsWith("Planned after"))
        j=record(j,"drop:s1",reps=12)
        assertTrue(CustomAmplifierSetSummaryDomain.review(j,set)!!.progress
            .contains("Next: Drop set · weight drop 1 of 2"))
        j=record(j,"drop:s1",CustomWorkoutVisitPart.FOLLOW_UP,stage=1,reps=8)
        assertTrue(CustomAmplifierSetSummaryDomain.review(j,set)!!.progress
            .contains("1 of 2 logged · Next: Drop set · weight drop 2 of 2"))
        j=record(j,"drop:s1",CustomWorkoutVisitPart.FOLLOW_UP,stage=2,reps=8)
        assertEquals("Complete · 2 of 2 logged",
            CustomAmplifierSetSummaryDomain.review(j,set)!!.progress)
        assertEquals("Performed drops: 15 lb × 8 → 10 lb × 8",
            CustomAmplifierSetSummaryDomain.review(j,set)!!.actuals)
        assertEquals(3,j.effectiveVisits.size)
    }

}
