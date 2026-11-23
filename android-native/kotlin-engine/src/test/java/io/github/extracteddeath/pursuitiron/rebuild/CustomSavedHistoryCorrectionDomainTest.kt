package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class CustomSavedHistoryCorrectionDomainTest {
    private val setKey = "cycle-1:w1:d0:slot-1:s1"
    private val main = CustomWorkoutVisitKey(setKey, CustomWorkoutVisitPart.MAIN)
    private val follow = CustomWorkoutVisitKey(setKey, CustomWorkoutVisitPart.FOLLOW_UP)

    private fun finished(
        skippedMain: Boolean = false,
        skippedFollow: Boolean = false,
        activationLoad: BigDecimal = BigDecimal("20"),
        amplifier: CustomSetAmplifier? = null
    ): CustomWorkoutJournalDraft {
        val authored = CustomAuthoredSet(
            DraftSetType.WORKING,
            DraftSetLoad.Verified(LoadUnit.LB, LoadBasis.DUMBBELL_PER_HAND,
                BigDecimal("20")),
            RepBand(10, 15), 2, 60, 90,
            amplifier ?: CustomSetAmplifier(CustomAmplifierKind.DROP_SET, 6, 15,
                DraftSetLoad.Verified(LoadUnit.LB,
                    LoadBasis.DUMBBELL_PER_HAND, BigDecimal("15"))))
        val snapshot = CustomWorkoutDaySnapshot(
            CustomWorkoutSourceStamp(
                "athlete-1", "revision-1", "cycle-1", 2, "gym-1", 5),
            1, 1, "block-1", TrainingPhase.HYPERTROPHY,
            CustomBlockPurpose.TRAINING,
            CustomSplitIdentity.Freeform("My split"),
            0, "Upper", ComposerSessionPurpose.HYPERTROPHY,
            listOf(CustomWorkoutSlotSnapshot(
                "slot-1", "db_lateral_raise", "lateral_raise", "dumbbell",
                ComposerRole.ACCESSORY, null, null, true, "Strict",
                listOf(CustomWorkoutSetSnapshot(
                    setKey, "slot-1", 1, authored)))),
            165, 180)
        var journal = CustomWorkoutJournalDomain.begin(
            "workout-1", snapshot, "a".repeat(64))
        fun accept(op: CustomWorkoutJournalOperation) {
            val step = CustomWorkoutJournalDomain.reduce(journal, op)
            assertTrue(step.toString(), step is CustomWorkoutJournalDecision.Proposed)
            journal = (step as CustomWorkoutJournalDecision.Proposed).draft
        }
        accept(CustomWorkoutJournalOperation.Record(
            "main-original", 0, main,
            if (skippedMain) KotlinJournalSetOutcome.SKIPPED
            else KotlinJournalSetOutcome.COMPLETED,
            if (skippedMain) null
            else KotlinPerformedSet(activationLoad, LoadUnit.LB, 13, null)))
        if (!skippedMain && activationLoad > BigDecimal("15")) accept(CustomWorkoutJournalOperation.Record(
            "follow-original", 1, follow,
            if (skippedFollow) KotlinJournalSetOutcome.SKIPPED
            else KotlinJournalSetOutcome.COMPLETED,
            if (skippedFollow) null
            else KotlinPerformedSet(
                if(amplifier?.kind==CustomAmplifierKind.MYO_REPS)
                    activationLoad else BigDecimal("15"),
                LoadUnit.LB, if(amplifier?.kind==CustomAmplifierKind.MYO_REPS)4 else 6,1)))
        accept(CustomWorkoutJournalOperation.Finish(
            "finish-original", journal.revision))
        return journal
    }

    private fun correction(
        event: String, revision: Long, visit: CustomWorkoutVisitKey,
        predecessor: String, outcome: KotlinJournalSetOutcome,
        performed: KotlinPerformedSet?
    ) = CustomSavedVisitCorrection(
        event, revision, visit, predecessor, outcome, performed)

    private fun accept(
        state: CustomSavedHistoryCorrectionState,
        op: CustomSavedVisitCorrection
    ): CustomSavedHistoryCorrectionState {
        val r = CustomSavedHistoryCorrectionDomain.reduce(state, op)
        assertTrue(r.toString(), r is CustomSavedCorrectionDecision.Proposed)
        assertFalse((r as CustomSavedCorrectionDecision.Proposed).replayed)
        return r.state
    }

    @Test fun savedMyoCorrectionsCannotTurnAnActivationIntoDropLoadedMinis() {
        val original=finished(
            activationLoad=BigDecimal("47.5"),
            amplifier=CustomSetAmplifier(CustomAmplifierKind.MYO_REPS,
                4,20,followUpRounds=1))
        val state=CustomSavedHistoryCorrectionDomain.begin(original)
        val changed=correction("wrong-myo-load",0,follow,
            "follow-original",KotlinJournalSetOutcome.COMPLETED,
            KotlinPerformedSet(BigDecimal("30"),LoadUnit.LB,5,null))
        assertEquals(CustomSavedCorrectionDecision.Rejected(
            CustomSavedCorrectionRefusal.INCONSISTENT_AMPLIFIER),
            CustomSavedHistoryCorrectionDomain.reduce(state,changed))
        val failedAtDifferentWeight=changed.copy(
            eventId="failed-25",
            outcome=KotlinJournalSetOutcome.FAILED_ATTEMPT,
            performed=KotlinPerformedSet(BigDecimal("25"),LoadUnit.LB,0,null))
        assertEquals(CustomSavedCorrectionDecision.Rejected(
            CustomSavedCorrectionRefusal.INCONSISTENT_AMPLIFIER),
            CustomSavedHistoryCorrectionDomain.reduce(state,failedAtDifferentWeight))
        val accepted=accept(state,changed.copy(
            eventId="same-myo-load",
            performed=KotlinPerformedSet(BigDecimal("47.50"),LoadUnit.LB,5,null)))
        assertEquals(original,accepted.originalFinishedJournal)
        assertEquals(0, (CustomSavedHistoryEvidenceDomain.projectSaved(accepted)
            as CustomSavedHistoryProjectionResult.Candidate).corrected
                .evidence.visits.last().actual!!.load!!
                .compareTo(BigDecimal("47.5")))
        // A correction to the activation cannot orphan that saved mini.
        assertEquals(CustomSavedCorrectionDecision.Rejected(
            CustomSavedCorrectionRefusal.INCONSISTENT_AMPLIFIER),
            CustomSavedHistoryCorrectionDomain.reduce(accepted,
                correction("activation-changed",1,main,"main-original",
                    KotlinJournalSetOutcome.COMPLETED,
                    KotlinPerformedSet(BigDecimal("45"),LoadUnit.LB,13,null))))
    }

    @Test fun correctingCompletedMainCannotKeepCompletedDropSet() {
        val original = finished()
        val first = CustomSavedHistoryCorrectionDomain.begin(original)
        val invalid = correction("skip-main", 0, main, "main-original",
            KotlinJournalSetOutcome.SKIPPED, null)
        assertEquals(CustomSavedCorrectionDecision.Rejected(
            CustomSavedCorrectionRefusal.INCONSISTENT_AMPLIFIER),
            CustomSavedHistoryCorrectionDomain.reduce(first, invalid))
        val skipFollow = correction("skip-follow", 0, follow, "follow-original",
            KotlinJournalSetOutcome.SKIPPED, null)
        val second = accept(first, skipFollow)
        val third = accept(second, correction(
            "skip-main", 1, main, "main-original",
            KotlinJournalSetOutcome.SKIPPED, null))
        assertEquals("finish-original", third.originalFinishedJournal.finishedByEventId)
        assertEquals(original, third.originalFinishedJournal)
        val projected = CustomSavedHistoryEvidenceDomain.projectSaved(third)
        assertTrue(projected is CustomSavedHistoryProjectionResult.Candidate)
        val result = (projected as CustomSavedHistoryProjectionResult.Candidate).corrected
        assertEquals(2L, result.correctionRevision)
        assertEquals(1, result.evidence.visits.size)
        assertEquals(main.setKey, result.evidence.visits.single().setKey)
        assertEquals(KotlinJournalSetOutcome.SKIPPED,
            result.evidence.visits.single().outcome)
        assertNull(result.evidence.visits.single().actual)
        assertFalse(result.mayPublishPr)
        assertFalse(result.mayChangeProgression)
    }

    @Test fun actualRirStaysOptionalAndVerifiedLoadBasisRemainsPhysical() {
        val first = CustomSavedHistoryCorrectionDomain.begin(finished())
        val noRir = correction("main-adjust", 0, main, "main-original",
            KotlinJournalSetOutcome.COMPLETED,
            KotlinPerformedSet(BigDecimal("20"), LoadUnit.LB, 14, null))
        val saved = accept(first, noRir)
        val actual = (CustomSavedHistoryEvidenceDomain.projectSaved(saved)
            as CustomSavedHistoryProjectionResult.Candidate).corrected
            .evidence.visits.first()
        assertEquals(14, actual.actual?.reps)
        assertNull(actual.actual?.actualRir)
        assertEquals(2, actual.prescribedRir)
        assertEquals("main-adjust", actual.effectiveEventId)

        assertEquals(CustomSavedCorrectionDecision.Rejected(
            CustomSavedCorrectionRefusal.INVALID_VALUES),
            CustomSavedHistoryCorrectionDomain.reduce(first,
                correction("wrong-unit", 0, main, "main-original",
                    KotlinJournalSetOutcome.COMPLETED,
                    KotlinPerformedSet(BigDecimal("20"), LoadUnit.KG, 14, 1))))
        assertEquals(CustomSavedCorrectionDecision.Rejected(
            CustomSavedCorrectionRefusal.INVALID_VALUES),
            CustomSavedHistoryCorrectionDomain.reduce(first,
                correction("invalid-rir", 0, main, "main-original",
                    KotlinJournalSetOutcome.COMPLETED,
                    KotlinPerformedSet(BigDecimal("20"), LoadUnit.LB, 14, 11))))
    }

    @Test fun correctingFinishedActivationKeepsItsSuppressedDropAbsent() {
        val state = CustomSavedHistoryCorrectionDomain.begin(finished(activationLoad=BigDecimal("15")))
        val update = correction("correct-reps", 0, main, "main-original",
            KotlinJournalSetOutcome.COMPLETED, KotlinPerformedSet(BigDecimal("15"), LoadUnit.LB, 12, 1))
        val saved = accept(state, update)
        val projection = CustomSavedHistoryEvidenceDomain.projectSaved(saved)
            as CustomSavedHistoryProjectionResult.Candidate
        val only = projection.corrected.evidence.visits.single()
        assertEquals(CustomWorkoutVisitPart.MAIN, only.part)
        assertEquals(update.performed, only.actual)
        assertEquals(CustomSavedCorrectionDecision.Rejected(CustomSavedCorrectionRefusal.INCONSISTENT_AMPLIFIER),
            CustomSavedHistoryCorrectionDomain.reduce(state, update.copy(
                performed=KotlinPerformedSet(BigDecimal("20"), LoadUnit.LB, 12, 1))))
    }

    @Test fun cannotPromoteOriginallySkippedMainWithUnrecordedFollowup() {
        val state = CustomSavedHistoryCorrectionDomain.begin(finished(skippedMain = true))
        assertEquals(CustomSavedCorrectionDecision.Rejected(
            CustomSavedCorrectionRefusal.INCONSISTENT_AMPLIFIER),
            CustomSavedHistoryCorrectionDomain.reduce(state,
                correction("invent-main", 0, main, "main-original",
                    KotlinJournalSetOutcome.COMPLETED,
                    KotlinPerformedSet(BigDecimal("20"), LoadUnit.LB, 11, 1))))
        assertEquals(CustomSavedCorrectionDecision.Rejected(
            CustomSavedCorrectionRefusal.UNKNOWN_VISIT),
            CustomSavedHistoryCorrectionDomain.reduce(state,
                correction("invent-drop", 0, follow, "main-original",
                    KotlinJournalSetOutcome.COMPLETED,
                    KotlinPerformedSet(BigDecimal("15"), LoadUnit.LB, 6, 1))))
    }

    @Test fun wrongPredecessorStaleRevisionAndEventIdCollisionsFailClosed() {
        val first = CustomSavedHistoryCorrectionDomain.begin(finished())
        val op = correction("main-edit", 0, main, "main-original",
            KotlinJournalSetOutcome.COMPLETED,
            KotlinPerformedSet(BigDecimal("20"), LoadUnit.LB, 14, 1))
        val accepted = accept(first, op)
        val repeated = CustomSavedHistoryCorrectionDomain.reduce(accepted, op)
        assertTrue((repeated as CustomSavedCorrectionDecision.Proposed).replayed)
        assertEquals(accepted, repeated.state)

        assertEquals(CustomSavedCorrectionDecision.Rejected(
            CustomSavedCorrectionRefusal.EVENT_ID_COLLISION),
            CustomSavedHistoryCorrectionDomain.reduce(accepted,
                op.copy(performed=KotlinPerformedSet(
                    BigDecimal("20"), LoadUnit.LB, 12, 1))))
        assertEquals(CustomSavedCorrectionDecision.Rejected(
            CustomSavedCorrectionRefusal.EVENT_ID_COLLISION),
            CustomSavedHistoryCorrectionDomain.reduce(accepted,
                op.copy(eventId="main-original", expectedCorrectionRevision=1,
                    correctsEventId="main-edit")))
        assertEquals(CustomSavedCorrectionDecision.Rejected(
            CustomSavedCorrectionRefusal.REVISION_CONFLICT),
            CustomSavedHistoryCorrectionDomain.reduce(accepted,
                op.copy(eventId="new-edit", correctsEventId="main-edit")))
        assertEquals(CustomSavedCorrectionDecision.Rejected(
            CustomSavedCorrectionRefusal.WRONG_PREDECESSOR),
            CustomSavedHistoryCorrectionDomain.reduce(accepted,
                op.copy(eventId="new-edit", expectedCorrectionRevision=1)))
    }

    @Test fun coldReplayRejectsTamperingAndForgedEffectiveCache() {
        val original = finished(skippedFollow = true)
        val state = CustomSavedHistoryCorrectionDomain.begin(original)
        val accepted = accept(state, correction(
            "edit-main", 0, main, "main-original",
            KotlinJournalSetOutcome.COMPLETED,
            KotlinPerformedSet(BigDecimal("20"), LoadUnit.LB, 12, 0)))
        val replay = CustomSavedHistoryCorrectionDomain.replay(
            original, accepted.receipts, accepted.correctionRevision)
        assertEquals(accepted,
            (replay as CustomSavedCorrectionDecision.Proposed).state)
        assertEquals(CustomSavedCorrectionDecision.Rejected(
            CustomSavedCorrectionRefusal.INVALID_STORED_CORRECTIONS),
            CustomSavedHistoryCorrectionDomain.verifyStoredState(
                accepted.copy(effectiveCorrections=emptyMap())))
        assertEquals(CustomSavedCorrectionDecision.Rejected(
            CustomSavedCorrectionRefusal.INVALID_STORED_CORRECTIONS),
            CustomSavedHistoryCorrectionDomain.replay(
                original, accepted.receipts, 2))
        assertEquals(CustomSavedCorrectionDecision.Rejected(
            CustomSavedCorrectionRefusal.INVALID_STORED_CORRECTIONS),
            CustomSavedHistoryCorrectionDomain.replay(original,
                accepted.receipts.map {
                    it.copy(appliedCorrectionRevision=7)
                }, accepted.correctionRevision))
    }

    @Test fun changingFollowupToSkippedPreservesOriginalAuditableReceipt() {
        val original = finished()
        val edited = accept(CustomSavedHistoryCorrectionDomain.begin(original),
            correction("drop-skip", 0, follow, "follow-original",
                KotlinJournalSetOutcome.SKIPPED, null))
        val facts = (CustomSavedHistoryEvidenceDomain.projectSaved(edited)
            as CustomSavedHistoryProjectionResult.Candidate).corrected.evidence.visits
        assertEquals(2, facts.size)
        assertEquals(KotlinJournalSetOutcome.SKIPPED, facts[1].outcome)
        assertNull(facts[1].actual)
        assertEquals("drop-skip", facts[1].effectiveEventId)
        assertEquals("follow-original",
            (original.effectiveVisits[follow]?.operation
                as CustomWorkoutJournalOperation.Record).eventId)
    }

    @Test fun duplicateExerciseSlotsRemainIndependentWhenCorrectingSavedHistory() {
        val template = finished().snapshot
        val originalSlot = template.slots.single()
        val otherKey = "cycle-1:w1:d0:slot-2:s1"
        val otherSlot = originalSlot.copy(
            slotId = "slot-2",
            sets = listOf(originalSlot.sets.single().copy(
                setKey = otherKey, slotId = "slot-2")))
        var journal = CustomWorkoutJournalDomain.begin(
            "workout-duplicate", template.copy(
                slots = listOf(originalSlot, otherSlot)), "a".repeat(64))
        fun record(
            event: String, visit: CustomWorkoutVisitKey,
            outcome: KotlinJournalSetOutcome,
            performed: KotlinPerformedSet?
        ) {
            val result = CustomWorkoutJournalDomain.reduce(journal,
                CustomWorkoutJournalOperation.Record(event, journal.revision,
                    visit, outcome, performed))
            assertTrue(result is CustomWorkoutJournalDecision.Proposed)
            journal = (result as CustomWorkoutJournalDecision.Proposed).draft
        }
        record("first-main", main, KotlinJournalSetOutcome.COMPLETED,
            KotlinPerformedSet(BigDecimal("20"), LoadUnit.LB, 12, 1))
        record("first-follow", follow, KotlinJournalSetOutcome.SKIPPED, null)
        record("second-main", CustomWorkoutVisitKey(
            otherKey, CustomWorkoutVisitPart.MAIN),
            KotlinJournalSetOutcome.COMPLETED,
            KotlinPerformedSet(BigDecimal("20"), LoadUnit.LB, 10, 2))
        record("second-follow", CustomWorkoutVisitKey(
            otherKey, CustomWorkoutVisitPart.FOLLOW_UP),
            KotlinJournalSetOutcome.COMPLETED,
            KotlinPerformedSet(BigDecimal("15"), LoadUnit.LB, 6, 0))
        val finish = CustomWorkoutJournalDomain.reduce(journal,
            CustomWorkoutJournalOperation.Finish("finish-duplicate",
                journal.revision))
        assertTrue(finish is CustomWorkoutJournalDecision.Proposed)
        val original = (finish as CustomWorkoutJournalDecision.Proposed).draft
        val saved = accept(CustomSavedHistoryCorrectionDomain.begin(original),
            correction("first-only", 0, main, "first-main",
                KotlinJournalSetOutcome.COMPLETED,
                KotlinPerformedSet(BigDecimal("20"), LoadUnit.LB, 14, 1)))
        val facts = (CustomSavedHistoryEvidenceDomain.projectSaved(saved)
            as CustomSavedHistoryProjectionResult.Candidate).corrected.evidence.visits
        assertEquals(4, facts.size)
        assertEquals(14, facts.single {
            it.setKey == setKey && it.part == CustomWorkoutVisitPart.MAIN
        }.actual?.reps)
        assertEquals(10, facts.single {
            it.setKey == otherKey && it.part == CustomWorkoutVisitPart.MAIN
        }.actual?.reps)
        assertEquals("second-main", facts.single {
            it.setKey == otherKey && it.part == CustomWorkoutVisitPart.MAIN
        }.effectiveEventId)
    }
    @Test fun savedMyoStageCorrectionCannotAliasFirstMiniSetOrRewriteEarlierDependents() {
        val work=CustomAuthoredSet(DraftSetType.WORKING,
            DraftSetLoad.Verified(LoadUnit.LB,LoadBasis.DUMBBELL_PER_HAND,
                BigDecimal("20")),RepBand(12,20),2,60,90,
            CustomSetAmplifier(CustomAmplifierKind.MYO_REPS,5,20,
                followUpActiveSeconds=80,followUpRounds=5))
        val session=CustomWorkoutDaySnapshot(
            CustomWorkoutSourceStamp("athlete-1","revision-1","cycle-1",
                2,"gym-1",5),1,1,"block-1",TrainingPhase.HYPERTROPHY,
            CustomBlockPurpose.TRAINING,CustomSplitIdentity.Freeform("My split"),
            0,"Upper",ComposerSessionPurpose.HYPERTROPHY,
            listOf(CustomWorkoutSlotSnapshot("slot-1","db_lateral_raise",
                "lateral_raise","dumbbell",ComposerRole.ACCESSORY,
                null,null,true,"Strict",listOf(CustomWorkoutSetSnapshot(
                    setKey,"slot-1",1,work)))),330,180)
        var j=CustomWorkoutJournalDomain.begin("myo-finish",session,"a".repeat(64))
        fun save(visit:CustomWorkoutVisitKey,event:String,reps:Int) {
            val next=CustomWorkoutJournalDomain.reduce(j,
                CustomWorkoutJournalOperation.Record(event,j.revision,visit,
                    KotlinJournalSetOutcome.COMPLETED,
                    KotlinPerformedSet(BigDecimal("20"),LoadUnit.LB,reps,2)))
            assertTrue(next.toString(),next is CustomWorkoutJournalDecision.Proposed)
            j=(next as CustomWorkoutJournalDecision.Proposed).draft
        }
        save(main,"myo-main",15)
        val stage1=CustomWorkoutVisitKey(setKey,CustomWorkoutVisitPart.FOLLOW_UP,1)
        val stage2=CustomWorkoutVisitKey(setKey,CustomWorkoutVisitPart.FOLLOW_UP,2)
        val stage3=CustomWorkoutVisitKey(setKey,CustomWorkoutVisitPart.FOLLOW_UP,3)
        save(stage1,"myo-1",5)
        save(stage2,"myo-2",4)
        save(stage3,"myo-3",3)
        j=(CustomWorkoutJournalDomain.reduce(j,
            CustomWorkoutJournalOperation.Finish("finish-myo",j.revision))
            as CustomWorkoutJournalDecision.Proposed).draft
        val originals=CustomSavedHistoryCorrectionDomain.begin(j)
        val finalCorrection=CustomSavedVisitCorrection("fix-myo-3",0,stage3,
            "myo-3",KotlinJournalSetOutcome.COMPLETED,
            KotlinPerformedSet(BigDecimal("20"),LoadUnit.LB,2,2))
        // Four reps would require a fourth mini-set, which was never recorded.
        val reopensWorkout = finalCorrection.copy(
            performed = KotlinPerformedSet(BigDecimal("20"), LoadUnit.LB, 4, 2))
        assertEquals(CustomSavedCorrectionDecision.Rejected(
            CustomSavedCorrectionRefusal.INCONSISTENT_AMPLIFIER),
            CustomSavedHistoryCorrectionDomain.reduce(originals, reopensWorkout))
        val corrected=accept(originals,finalCorrection)
        val projected=CustomSavedHistoryEvidenceDomain.projectSaved(corrected)
            as CustomSavedHistoryProjectionResult.Candidate
        val minis=projected.corrected.evidence.visits.filter {
            it.part==CustomWorkoutVisitPart.FOLLOW_UP
        }
        assertEquals(listOf(1,2,3),minis.map{it.stage})
        assertEquals(listOf(5,4,2),minis.map{it.actual?.reps})
        assertEquals(listOf("myo-1","myo-2","fix-myo-3"),
            minis.map{it.effectiveEventId})
        val firstEdit = CustomSavedVisitCorrection("edit-first", 1, stage1, "myo-1",
            KotlinJournalSetOutcome.COMPLETED, KotlinPerformedSet(BigDecimal("20"), LoadUnit.LB, 4, 1))
        val earlier = accept(corrected, firstEdit)
        val earlierFacts = (CustomSavedHistoryEvidenceDomain.projectSaved(earlier)
            as CustomSavedHistoryProjectionResult.Candidate).corrected.evidence.visits
        assertEquals(listOf(4, 4, 2), earlierFacts.filter {
            it.part == CustomWorkoutVisitPart.FOLLOW_UP
        }.map { it.actual?.reps })
        assertEquals(CustomSavedCorrectionDecision.Rejected(CustomSavedCorrectionRefusal.INCONSISTENT_AMPLIFIER),
            CustomSavedHistoryCorrectionDomain.reduce(corrected, firstEdit.copy(
                performed = KotlinPerformedSet(BigDecimal("20"), LoadUnit.LB, 2, 1))))
        val impossible=CustomSavedVisitCorrection("fix-myo-1",1,stage1,
            "myo-1",KotlinJournalSetOutcome.SKIPPED,null)
        assertEquals(CustomSavedCorrectionDecision.Rejected(
            CustomSavedCorrectionRefusal.INCONSISTENT_AMPLIFIER),
            CustomSavedHistoryCorrectionDomain.reduce(corrected,impossible))
        assertEquals(j,corrected.originalFinishedJournal)
    }

    /** All receipts are real journal reductions; only the saved-correction
     * logic is under test, never a synthetic forged effective map. */
    private fun finishedStaged(kind:CustomAmplifierKind):CustomWorkoutJournalDraft {
        val template=finished().snapshot
        val originalSlot=template.slots.single()
        val authored=originalSlot.sets.single()
        val amp=when(kind) {
            CustomAmplifierKind.DROP_SET -> CustomSetAmplifier(
                kind,6,15,DraftSetLoad.Verified(LoadUnit.LB,
                    LoadBasis.DUMBBELL_PER_HAND,BigDecimal("15")),
                followUpActiveSeconds=30,followUpRounds=2,
                secondDropLoad=DraftSetLoad.Verified(LoadUnit.LB,
                    LoadBasis.DUMBBELL_PER_HAND,BigDecimal("10")))
            CustomAmplifierKind.MYO_REPS ->
                CustomSetAmplifier(kind,5,20,followUpActiveSeconds=60,
                    followUpRounds=3)
            CustomAmplifierKind.REST_PAUSE ->
                CustomSetAmplifier(kind,3,20,followUpActiveSeconds=40,
                    followUpRounds=2)
            CustomAmplifierKind.LENGTHENED_PARTIALS ->
                CustomSetAmplifier(kind,4,0,followUpActiveSeconds=20)
            CustomAmplifierKind.CLUSTER ->
                CustomSetAmplifier(kind,2,25,followUpActiveSeconds=50,
                    followUpRounds=2)
        }
        val updatedSet=authored.copy(prescription=authored.prescription.copy(
            amplifier=amp))
        val updatedSnapshot=template.copy(slots=listOf(originalSlot.copy(
            sets=listOf(updatedSet))))
        var journal=CustomWorkoutJournalDomain.begin(
            "corrected-${kind.name.lowercase()}",updatedSnapshot,"a".repeat(64))
        fun record(event:String,visit:CustomWorkoutVisitKey,
                   load:String,reps:Int) {
            val result=CustomWorkoutJournalDomain.reduce(journal,
                CustomWorkoutJournalOperation.Record(
                    event,journal.revision,visit,KotlinJournalSetOutcome.COMPLETED,
                    KotlinPerformedSet(BigDecimal(load),LoadUnit.LB,reps,2)))
            assertTrue(result.toString(),result is CustomWorkoutJournalDecision.Proposed)
            journal=(result as CustomWorkoutJournalDecision.Proposed).draft
        }
        record("main-stage",main,"20",13)
        val stageLoads=if(kind==CustomAmplifierKind.DROP_SET)
            listOf("15" to 6,"10" to 6)
        else if(kind==CustomAmplifierKind.MYO_REPS)
            listOf("20" to 5,"20" to 4,"20" to 3)
        else if(kind==CustomAmplifierKind.REST_PAUSE)
            listOf("20" to 3,"20" to 3)
        else if(kind==CustomAmplifierKind.CLUSTER)
            listOf("20" to 2,"20" to 2)
        else listOf("20" to 4)
        stageLoads.forEachIndexed{index,(weight,reps)->
            record("stage-${index+1}",CustomWorkoutVisitKey(
                setKey,CustomWorkoutVisitPart.FOLLOW_UP,index+1),weight,reps)
        }
        val final=CustomWorkoutJournalDomain.reduce(journal,
            CustomWorkoutJournalOperation.Finish(
                "finish-staged",journal.revision))
        assertTrue(final.toString(),final is CustomWorkoutJournalDecision.Proposed)
        return (final as CustomWorkoutJournalDecision.Proposed).draft
    }

    @Test fun secondDropSavedCorrectionCannotReplaceReductionWithAnIncrease() {
        val original=finishedStaged(CustomAmplifierKind.DROP_SET)
        val initial=CustomSavedHistoryCorrectionDomain.begin(original)
        val second=CustomWorkoutVisitKey(
            setKey,CustomWorkoutVisitPart.FOLLOW_UP,2)
        val decrease=correction("correct-drop2",0,second,"stage-2",
            KotlinJournalSetOutcome.COMPLETED,
            KotlinPerformedSet(BigDecimal("12"),LoadUnit.LB,7,2))
        val edited=accept(initial,decrease)
        assertEquals(CustomSavedCorrectionDecision.Proposed(edited,false),
            CustomSavedHistoryCorrectionDomain.verifyStoredState(edited))
        val facts=(CustomSavedHistoryEvidenceDomain.projectSaved(edited)
            as CustomSavedHistoryProjectionResult.Candidate).corrected.evidence
        assertEquals(BigDecimal("12"),facts.visits.last().actual?.load)
        assertEquals(BigDecimal("15"),facts.visits[
            facts.visits.size-2].actual?.load)
        val increase=decrease.copy(eventId="wrong-drop",
            performed=KotlinPerformedSet(BigDecimal("16"),LoadUnit.LB,7,2))
        assertEquals(CustomSavedCorrectionDecision.Rejected(
            CustomSavedCorrectionRefusal.INCONSISTENT_AMPLIFIER),
            CustomSavedHistoryCorrectionDomain.reduce(initial,increase))
        assertEquals(original,edited.originalFinishedJournal)
    }

    @Test fun finishedSameLoadTechniquesCannotBeRestoredOnAnotherWeight() {
        for(kind in listOf(CustomAmplifierKind.MYO_REPS,
            CustomAmplifierKind.REST_PAUSE,CustomAmplifierKind.CLUSTER,
            CustomAmplifierKind.LENGTHENED_PARTIALS)) {
            val original=finishedStaged(kind)
            val base=CustomSavedHistoryCorrectionDomain.begin(original)
            val finalStage=(original.effectiveVisits.keys.filter {
                it.part==CustomWorkoutVisitPart.FOLLOW_UP
            }.maxBy{it.stage})
            val prior=(original.effectiveVisits.getValue(finalStage).operation
                as CustomWorkoutJournalOperation.Record)
            val edited=correction("invalid-follow-load",0,finalStage,
                prior.eventId,KotlinJournalSetOutcome.COMPLETED,
                KotlinPerformedSet(BigDecimal("19"),LoadUnit.LB,3,2))
            assertEquals(kind.name,CustomSavedCorrectionDecision.Rejected(
                CustomSavedCorrectionRefusal.INCONSISTENT_AMPLIFIER),
                CustomSavedHistoryCorrectionDomain.reduce(base,edited))
        }
    }

    @Test fun savedActivationCorrectionsCompareNumericLoadAndExactStationIdentity() {
        val original=finishedStaged(CustomAmplifierKind.MYO_REPS)
        val base=CustomSavedHistoryCorrectionDomain.begin(original)
        val same=correction("same-numeric-load",0,main,"main-stage",
            KotlinJournalSetOutcome.COMPLETED,
            KotlinPerformedSet(BigDecimal("20.000"),LoadUnit.LB,14,1))
        val accepted=accept(base,same)
        assertEquals(accepted,(CustomSavedHistoryCorrectionDomain.replay(
            original,accepted.receipts,accepted.correctionRevision)
                as CustomSavedCorrectionDecision.Proposed).state)
        val changed=same.copy(eventId="different-load",
            performed=KotlinPerformedSet(BigDecimal("21"),LoadUnit.LB,14,1))
        assertEquals(CustomSavedCorrectionDecision.Rejected(
            CustomSavedCorrectionRefusal.INCONSISTENT_AMPLIFIER),
            CustomSavedHistoryCorrectionDomain.reduce(base,changed))
        val station=same.copy(eventId="different-station",
            performed=KotlinPerformedSet(BigDecimal("20"),LoadUnit.LB,
                14,1,machineInstanceId="machine-b"))
        assertEquals(CustomSavedCorrectionDecision.Rejected(
            CustomSavedCorrectionRefusal.INCONSISTENT_AMPLIFIER),
            CustomSavedHistoryCorrectionDomain.reduce(base,station))
    }

}
