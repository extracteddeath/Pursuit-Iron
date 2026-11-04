package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class KotlinSavedHistoryCorrectionDomainTest {
    private fun n(v: Int) = BigDecimal(v)
    private val source = KotlinTrainingSourceRevision(
        "athlete-1", 2, "program-1", 4, "gym-1", 1, "catalog-2", "rules-3")
    private val physical = DraftSetLoad.Verified(
        LoadUnit.LB, LoadBasis.SMITH_TOTAL, n(200))
    private val stock = VerifiedLoadStock.Totals(
        LoadUnit.LB, LoadBasis.SMITH_TOTAL, listOf(n(195), n(200), n(205)))

    private fun workout(id: Int): KotlinWorkoutJournalState {
        val workoutId = "workout-$id"
        val targets = (0 until 2).map { ordinal ->
            KotlinWorkoutTarget("bench", ComposerRole.PRIMARY,
                ComposerSessionPurpose.HYPERTROPHY, "slot-$id-$ordinal",
                DraftSetType.WORKING, ordinal, physical, RepBand(5, 8),
                2, 60, 90)
        }
        val snapshot = KotlinStagedWorkout(
            source, "grant-$id", workoutId, 0, 0,
            ComposerSessionPurpose.HYPERTROPHY, 45, targets)
        var journal = KotlinWorkoutJournalDomain.begin(
            snapshot, KotlinStoredWorkoutIdentity(
                "storage-$id", workoutId, source, 1))
        for (ordinal in targets.indices) {
            val receipt = KotlinJournalOperation.RecordSet(
                "original-$id-$ordinal", journal.revision,
                targets[ordinal].setKey, KotlinJournalSetOutcome.COMPLETED,
                KotlinPerformedSet(n(200), LoadUnit.LB, 8, 3))
            journal = (KotlinWorkoutJournalDomain.reduce(journal, receipt)
                as KotlinJournalDecision.Proposed).state
        }
        return (KotlinWorkoutJournalDomain.reduce(journal,
            KotlinJournalOperation.Finish("finish-$id", journal.revision))
            as KotlinJournalDecision.Proposed).state
    }

    private fun change(
        state: KotlinSavedHistoryCorrectionState,
        eventId: String,
        reps: Int,
        previous: String,
        ordinal: Int = 1
    ) = KotlinSavedSetCorrection(
        eventId, state.correctionRevision, "slot-2-$ordinal",
        previous, KotlinJournalSetOutcome.COMPLETED,
        KotlinPerformedSet(n(200), LoadUnit.LB, reps, null))

    private fun apply(
        state: KotlinSavedHistoryCorrectionState,
        operation: KotlinSavedSetCorrection
    ) = (KotlinSavedHistoryCorrectionDomain.reduce(state, operation)
        as KotlinSavedCorrectionDecision.Proposed).state

    private fun projected(state: KotlinSavedHistoryCorrectionState) =
        (KotlinWorkoutHistoryEvidenceDomain.projectSaved(state)
            as KotlinHistoryProjectionResult.Candidate).evidence

    @Test fun savedCorrectionChangesOnlyEffectiveHistoryNotOriginalWorkout() {
        val original = workout(2)
        val before = KotlinSavedHistoryCorrectionDomain.begin(original)
        val correction = change(before, "edit-2-a", 3, "original-2-1")
        val after = apply(before, correction)
        assertEquals(1L, after.correctionRevision)
        assertEquals(1, after.receipts.size)
        assertFalse(after.canCommitHistory)
        assertFalse(after.canChangeIssuedTargets)
        assertEquals(KotlinSavedCorrectionDecision.Proposed(after, false),
            KotlinSavedHistoryCorrectionDomain.verifyStoredState(after))
        val effective = projected(after)
        assertEquals("finish-2", effective.finishEventId)
        assertEquals(3L, effective.journalRevision)
        assertEquals("edit-2-a", effective.sets.last().effectiveEventId)
        assertEquals("original-2-1", effective.sets.last().correctsEventId)
        assertEquals(3, effective.sets.last().actual?.reps)
        assertNull(effective.sets.last().actual?.actualRir)
        assertEquals(2, effective.sets.last().issuedRir)
        assertEquals(n(200),
            (effective.sets.last().issuedLoad as DraftSetLoad.Verified).value)
        val recheckedOriginal = KotlinWorkoutHistoryEvidenceDomain.project(original)
            as KotlinHistoryProjectionResult.Candidate
        assertEquals(8, recheckedOriginal.evidence.sets.last().actual?.reps)
        assertEquals("original-2-1",
            recheckedOriginal.evidence.sets.last().effectiveEventId)
        assertEquals(setOf(1), effective.exerciseReviews.single()
            .progressionReview?.belowFloorOrdinals)
    }

    @Test fun revisionCasAndCorrectionChainRetainEveryPriorReceipt() {
        val original = workout(2)
        val initial = KotlinSavedHistoryCorrectionDomain.begin(original)
        val firstOp = change(initial, "edit-2-a", 7, "original-2-1")
        val first = apply(initial, firstOp)
        val secondOp = change(first, "edit-2-b", 8, "edit-2-a")
        val second = apply(first, secondOp)
        assertEquals(2L, second.correctionRevision)
        assertEquals(listOf("edit-2-a", "edit-2-b"),
            second.receipts.map { it.correction.eventId })
        assertEquals("edit-2-b", projected(second).sets.last().effectiveEventId)
        assertEquals("edit-2-a", projected(second).sets.last().correctsEventId)
        assertEquals(8, projected(second).sets.last().actual?.reps)
        assertEquals(KotlinSavedCorrectionDecision.Proposed(second, true),
            KotlinSavedHistoryCorrectionDomain.reduce(second, firstOp))
        assertEquals(KotlinSavedCorrectionRefusal.REVISION_CONFLICT,
            (KotlinSavedHistoryCorrectionDomain.reduce(second,
                change(initial, "edit-stale", 3, "edit-2-b"))
                as KotlinSavedCorrectionDecision.Rejected).reason)
        assertEquals(KotlinSavedCorrectionRefusal.WRONG_PREDECESSOR,
            (KotlinSavedHistoryCorrectionDomain.reduce(second,
                change(second, "edit-wrong", 3, "original-2-1"))
                as KotlinSavedCorrectionDecision.Rejected).reason)
        assertEquals(KotlinSavedCorrectionRefusal.EVENT_ID_COLLISION,
            (KotlinSavedHistoryCorrectionDomain.reduce(second,
                change(second, "edit-2-b", 2, "edit-2-b"))
                as KotlinSavedCorrectionDecision.Rejected).reason)
        assertEquals(KotlinSavedCorrectionRefusal.EVENT_ID_COLLISION,
            (KotlinSavedHistoryCorrectionDomain.reduce(second,
                change(second, "original-2-0", 2, "edit-2-b"))
                as KotlinSavedCorrectionDecision.Rejected).reason)
    }

    @Test fun forgedOrMissingSavedReceiptsCannotChangeHistory() {
        val original = workout(2)
        val initial = KotlinSavedHistoryCorrectionDomain.begin(original)
        val corrected = apply(initial, change(initial,
            "edit-2-a", 5, "original-2-1"))
        assertEquals(KotlinSavedCorrectionRefusal.INVALID_STORED_CORRECTIONS,
            (KotlinSavedHistoryCorrectionDomain.verifyStoredState(
                corrected.copy(effectiveCorrections = emptyMap()))
                as KotlinSavedCorrectionDecision.Rejected).reason)
        assertEquals(
            KotlinHistoryProjectionResult.Rejected(
                KotlinHistoryProjectionRefusal.INVALID_SAVED_CORRECTIONS),
            KotlinWorkoutHistoryEvidenceDomain.projectSaved(
                corrected.copy(effectiveCorrections = emptyMap())))
        assertEquals(KotlinSavedCorrectionRefusal.INVALID_STORED_CORRECTIONS,
            (KotlinSavedHistoryCorrectionDomain.replay(
                original, corrected.receipts, 0)
                as KotlinSavedCorrectionDecision.Rejected).reason)
        assertEquals(KotlinSavedCorrectionRefusal.INVALID_STORED_CORRECTIONS,
            (KotlinSavedHistoryCorrectionDomain.replay(
                original, corrected.receipts + corrected.receipts.single(), 2)
                as KotlinSavedCorrectionDecision.Rejected).reason)
        assertEquals(KotlinSavedCorrectionRefusal.INVALID_ORIGINAL_JOURNAL,
            (KotlinSavedHistoryCorrectionDomain.replay(
                original.copy(finishedByEventId = null), emptyList(), 0)
                as KotlinSavedCorrectionDecision.Rejected).reason)
    }

    @Test fun unsupportedValuesUnknownSlotsAndSilentOutcomeChangesAreRejected() {
        val initial = KotlinSavedHistoryCorrectionDomain.begin(workout(2))
        val correction = change(initial, "edit-valid", 4, "original-2-1")
        val invalid = listOf(
            correction.copy(setKey = "different"),
            correction.copy(actual = KotlinPerformedSet(null, null, 4, null)),
            correction.copy(actual = KotlinPerformedSet(n(200), LoadUnit.KG, 4, null)),
            correction.copy(actual = KotlinPerformedSet(n(200), LoadUnit.LB, 4, 11)),
            correction.copy(outcome = KotlinJournalSetOutcome.SKIPPED),
            correction.copy(eventId = "")
        )
        val reasons = invalid.map {
            (KotlinSavedHistoryCorrectionDomain.reduce(initial, it)
                as KotlinSavedCorrectionDecision.Rejected).reason
        }
        assertEquals(KotlinSavedCorrectionRefusal.UNKNOWN_SET, reasons[0])
        assertTrue(reasons.drop(1).all {
            it == KotlinSavedCorrectionRefusal.INVALID_VALUES
        })
    }

    @Test fun correctedOldSavedWorkoutRecomputesTwoSessionCoachDecision() {
        val old = workout(1)
        val newer = workout(2)
        val stockHistory = listOf(
            KotlinChronologicalWorkout(1, old),
            KotlinChronologicalWorkout(2, newer))
        fun inspect(workouts: List<KotlinChronologicalWorkout>, revision: Long) =
            KotlinProgressionTimelineDomain.review(KotlinProgressionTimelineRequest(
                "athlete-1", "bench", revision, revision, workouts, stock, n(5)))

        assertEquals(IncreaseReviewReason.QUALIFIED_FOR_REVIEW,
            inspect(stockHistory, 7).progression?.reason)
        val saved = KotlinSavedHistoryCorrectionDomain.begin(old)
        val edit = KotlinSavedSetCorrection(
            "old-false-high-edit", 0, "slot-1-1", "original-1-1",
            KotlinJournalSetOutcome.COMPLETED,
            KotlinPerformedSet(n(200), LoadUnit.LB, 3, 1))
        val corrected = apply(saved, edit)
        val revised = stockHistory.mapIndexed { idx, item ->
            if (idx == 0) item.copy(savedCorrections = corrected) else item
        }
        val review = inspect(revised, 8)
        assertEquals(KotlinTimelineReviewStatus.VERIFIED_REVIEW, review.status)
        assertEquals(IncreaseReviewReason.NEEDS_SECOND_COMPARABLE_SESSION,
            review.progression?.reason)
        assertNull(review.progression?.reviewCandidate)
        val stale = KotlinProgressionTimelineDomain.review(
            KotlinProgressionTimelineRequest(
                "athlete-1", "bench", 7, 8, stockHistory, stock, n(5)))
        assertEquals(KotlinTimelineReviewStatus.STALE_HISTORY, stale.status)
        assertEquals(KotlinTimelineReviewStatus.UNVERIFIED_JOURNAL,
            inspect(revised.mapIndexed { idx, item ->
                if (idx == 0) item.copy(journal = newer) else item
            }, 8).status)
    }
}
