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
        skippedFollow: Boolean = false
    ): CustomWorkoutJournalDraft {
        val authored = CustomAuthoredSet(
            DraftSetType.WORKING,
            DraftSetLoad.Verified(LoadUnit.LB, LoadBasis.DUMBBELL_PER_HAND,
                BigDecimal("20")),
            RepBand(10, 15), 2, 60, 90,
            CustomSetAmplifier(CustomAmplifierKind.DROP_SET, 6, 15,
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
            else KotlinPerformedSet(BigDecimal("20"), LoadUnit.LB, 13, null)))
        if (!skippedMain) accept(CustomWorkoutJournalOperation.Record(
            "follow-original", 1, follow,
            if (skippedFollow) KotlinJournalSetOutcome.SKIPPED
            else KotlinJournalSetOutcome.COMPLETED,
            if (skippedFollow) null
            else KotlinPerformedSet(BigDecimal("15"), LoadUnit.LB, 6, 1)))
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
}
