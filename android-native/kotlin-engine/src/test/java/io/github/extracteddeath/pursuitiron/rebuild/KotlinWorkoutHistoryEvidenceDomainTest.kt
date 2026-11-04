package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class KotlinWorkoutHistoryEvidenceDomainTest {
    private val source = KotlinTrainingSourceRevision(
        "athlete-1", 4, "program-1", 8, "gym-1", 3, "catalog-5", "coach-rules-2")
    private val physical = DraftSetLoad.Verified(
        LoadUnit.LB, LoadBasis.DUMBBELL_PER_HAND, BigDecimal("30"))
    private val body = DraftSetLoad.ExplicitBodyweight

    private fun target(
        key: String,
        ordinal: Int,
        type: DraftSetType = DraftSetType.WORKING,
        load: DraftSetLoad = physical,
        reps: RepBand = RepBand(8, 12),
        exercise: String = "press"
    ) = KotlinWorkoutTarget(
        exercise, ComposerRole.PRIMARY, ComposerSessionPurpose.HYPERTROPHY,
        key, type, ordinal, load, reps, 2, 60, 120)

    private fun start(vararg targets: KotlinWorkoutTarget): KotlinWorkoutJournalState {
        val snapshot = KotlinStagedWorkout(
            source, "grant-1", "workout-1", 0, 0,
            ComposerSessionPurpose.HYPERTROPHY, 45, targets.toList())
        return KotlinWorkoutJournalDomain.begin(snapshot, KotlinStoredWorkoutIdentity(
            "stored-1", snapshot.workoutId, snapshot.source, 1))
    }

    private fun apply(
        journal: KotlinWorkoutJournalState, op: KotlinJournalOperation
    ): KotlinWorkoutJournalState =
        (KotlinWorkoutJournalDomain.reduce(journal, op) as
            KotlinJournalDecision.Proposed).state

    private fun record(
        journal: KotlinWorkoutJournalState,
        id: String,
        key: String,
        outcome: KotlinJournalSetOutcome,
        actual: KotlinPerformedSet? = null,
        corrects: String? = null
    ) = apply(journal, KotlinJournalOperation.RecordSet(
        id, journal.revision, key, outcome, actual, corrects))

    private fun finish(journal: KotlinWorkoutJournalState) =
        apply(journal, KotlinJournalOperation.Finish(
            "finish-1", journal.revision))

    private fun actual(reps: Int, rir: Int? = null) =
        KotlinPerformedSet(BigDecimal("30"), LoadUnit.LB, reps, rir)

    private fun evidence(journal: KotlinWorkoutJournalState) =
        (KotlinWorkoutHistoryEvidenceDomain.project(journal)
            as KotlinHistoryProjectionResult.Candidate).evidence

    @Test fun correctedFactsPreserveTargetProvenanceWithoutInventingRir() {
        var j = start(
            target("warmup", 0, DraftSetType.WARMUP, reps = RepBand(5, 5)),
            target("first", 0), target("second", 1))
        j = record(j, "event-w", "warmup", KotlinJournalSetOutcome.SKIPPED)
        j = record(j, "event-a", "first", KotlinJournalSetOutcome.COMPLETED, actual(12))
        j = record(j, "event-c", "first", KotlinJournalSetOutcome.COMPLETED,
            actual(8, 1), corrects = "event-a")
        j = record(j, "event-b", "second", KotlinJournalSetOutcome.SKIPPED)
        val history = evidence(finish(j))
        assertEquals("finish-1", history.finishEventId)
        assertEquals(3, history.sets.size)
        assertEquals("warmup", history.sets.first().setKey)
        assertEquals("event-c", history.sets[1].effectiveEventId)
        assertEquals("event-a", history.sets[1].correctsEventId)
        assertEquals(8, history.sets[1].actual?.reps)
        assertEquals(2, history.sets[1].issuedRir)
        assertEquals(KotlinJournalSetOutcome.SKIPPED, history.sets[2].outcome)
        assertNull(history.sets[2].actual)
        assertEquals(1, history.exerciseReviews.size)
        val review = history.exerciseReviews.single()
        assertEquals(listOf("first", "second"), review.workingSets.map { it.setKey })
        assertEquals(KotlinHistoryProgressionGate.COMPARABLE_FOR_REVIEW,
            review.progressionGate)
        val progression = checkNotNull(review.progressionReview)
        val chronologicalInput = checkNotNull(review.progressionWorkout)
        assertEquals("workout-1", chronologicalInput.workoutId)
        assertEquals("press", chronologicalInput.exerciseId)
        assertEquals(listOf(0, 1), chronologicalInput.observations.map { it.workingOrdinal })
        assertEquals(listOf(8, null), chronologicalInput.observations.map { it.actualReps })
        assertEquals(listOf(1, null), chronologicalInput.observations.map { it.actualRir })
        assertEquals(1, progression.completedWorkingSets)
        assertEquals(setOf(1), progression.skippedOrdinals)
        assertEquals(setOf(0), progression.nearFailureOrdinals)
        assertEquals(setOf(0), progression.actualRirReportedOrdinals)
        assertEquals(ProgressionEvidenceSignal.INCOMPLETE_OR_SKIPPED,
            progression.signal)
        assertFalse(history.canPublishHistory)
        assertFalse(history.canApplyProgression)
        assertEquals(5L, history.journalRevision)
        assertEquals(4, j.receipts.size) // uncommitted Finish is not in this journal
    }

    @Test fun missingActualRirRemainsMissingWhenPrescribedRirExists() {
        var j = start(target("first", 0), target("second", 1))
        j = record(j, "event-a", "first", KotlinJournalSetOutcome.COMPLETED,
            actual(12))
        j = record(j, "event-b", "second", KotlinJournalSetOutcome.COMPLETED,
            actual(12))
        val history = evidence(finish(j))
        val review = checkNotNull(history.exerciseReviews.single().progressionReview)
        assertEquals(ProgressionEvidenceSignal.UNIFORM_LOAD_CEILING_FOR_REVIEW,
            review.signal)
        assertTrue(review.actualRirReportedOrdinals.isEmpty())
        assertTrue(history.sets.all { it.actual?.actualRir == null })
        assertTrue(history.sets.all { it.issuedRir == 2 })
    }

    @Test fun nonComparableBackoffAndBodyweightAreNotFalseDpSignals() {
        var backoff = start(target("top", 0), target("backoff", 1,
            reps = RepBand(6, 8)))
        backoff = record(backoff, "event-a", "top", KotlinJournalSetOutcome.COMPLETED,
            actual(12))
        backoff = record(backoff, "event-b", "backoff",
            KotlinJournalSetOutcome.COMPLETED, actual(8))
        val mixed = evidence(finish(backoff)).exerciseReviews.single()
        assertEquals(KotlinHistoryProgressionGate.MIXED_REP_BANDS,
            mixed.progressionGate)
        assertNull(mixed.progressionReview)
        assertNull(mixed.progressionWorkout)
        assertEquals(2, mixed.workingSets.size)

        var unweighted = start(target("chin-a", 0, load = body, exercise = "chinup"))
        unweighted = record(unweighted, "event-c", "chin-a",
            KotlinJournalSetOutcome.COMPLETED, KotlinPerformedSet(null, null, 10, null))
        val reviewed = evidence(finish(unweighted)).exerciseReviews.single()
        assertEquals(KotlinHistoryProgressionGate.UNWEIGHTED_BODYWEIGHT,
            reviewed.progressionGate)
        assertNull(reviewed.progressionReview)
        assertNull(reviewed.progressionWorkout)
        assertNull(reviewed.workingSets.single().actual?.load)
    }

    @Test fun failedAttemptIsNotReportedAsMissingOrSuccessful() {
        var j = start(target("first", 0), target("second", 1))
        j = record(j, "event-a", "first", KotlinJournalSetOutcome.COMPLETED, actual(9))
        j = record(j, "event-b", "second", KotlinJournalSetOutcome.FAILED_ATTEMPT,
            actual(0))
        val history = evidence(finish(j))
        val p = checkNotNull(history.exerciseReviews.single().progressionReview)
        assertEquals(setOf(1), p.failedAttemptOrdinals)
        assertTrue(p.missingOrdinals.isEmpty())
        assertEquals(1, p.completedWorkingSets)
        assertEquals(ProgressionEvidenceSignal.INCOMPLETE_OR_SKIPPED, p.signal)
    }

    @Test fun unfinishedOrForgedDenormalizedStateCannotBecomeHistory() {
        val initial = start(target("first", 0))
        assertEquals(
            KotlinHistoryProjectionResult.Rejected(KotlinHistoryProjectionRefusal.NOT_FINISHED),
            KotlinWorkoutHistoryEvidenceDomain.project(initial))
        val resolved = record(initial, "event-a", "first",
            KotlinJournalSetOutcome.COMPLETED, actual(10))
        val completed = finish(resolved)
        val forged = completed.copy(effectiveSets = emptyMap())
        assertEquals(
            KotlinHistoryProjectionResult.Rejected(KotlinHistoryProjectionRefusal.INVALID_JOURNAL),
            KotlinWorkoutHistoryEvidenceDomain.project(forged))
        val modifiedFinish = completed.copy(finishedByEventId = "other-finish")
        assertEquals(
            KotlinHistoryProjectionResult.Rejected(KotlinHistoryProjectionRefusal.INVALID_JOURNAL),
            KotlinWorkoutHistoryEvidenceDomain.project(modifiedFinish))
        assertEquals(1, evidence(completed).sets.size)
    }

    @Test fun mixedActualLoadsNeverAuthorizeAllSetPromotion() {
        var j = start(target("first", 0), target("second", 1))
        j = record(j, "event-a", "first", KotlinJournalSetOutcome.COMPLETED,
            KotlinPerformedSet(BigDecimal("200"), LoadUnit.LB, 12, 3))
        j = record(j, "event-b", "second", KotlinJournalSetOutcome.COMPLETED,
            KotlinPerformedSet(BigDecimal("215"), LoadUnit.LB, 12, 0))
        val history = evidence(finish(j))
        val review = checkNotNull(history.exerciseReviews.single().progressionReview)
        assertEquals(ProgressionEvidenceSignal.MIXED_LOAD_CEILING, review.signal)
        assertTrue(review.mixedActualLoads)
        assertEquals(setOf(1), review.nearFailureOrdinals)
        assertFalse(history.canApplyProgression)
        assertEquals(listOf(BigDecimal("200"), BigDecimal("215")),
            history.exerciseReviews.single().progressionWorkout?.observations?.map { it.actualLoad })
        assertEquals(listOf(BigDecimal("200"), BigDecimal("215")),
            history.exerciseReviews.single().workingSets.map { it.actual?.load })
        assertTrue(history.sets.all {
            (it.issuedLoad as DraftSetLoad.Verified).value == BigDecimal("30")
        })
    }

    @Test fun duplicatedWorkingOrdinalsRetainFactsButBlockProgression() {
        var j = start(target("first", 0), target("second", 0))
        j = record(j, "event-a", "first", KotlinJournalSetOutcome.COMPLETED, actual(9))
        j = record(j, "event-b", "second", KotlinJournalSetOutcome.COMPLETED, actual(9))
        val review = evidence(finish(j)).exerciseReviews.single()
        assertEquals(KotlinHistoryProgressionGate.DUPLICATE_OR_GAPPED_ORDINALS,
            review.progressionGate)
        assertEquals(2, review.workingSets.size)
        assertNull(review.progressionReview)
    }

}
