package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class KotlinGlobalHistoryIndexDomainTest {
    private fun n(v: Int) = BigDecimal(v)
    private val source = KotlinTrainingSourceRevision(
        "athlete-1", 2, "program-1", 3, "gym-1", 2, "catalog-1", "rules-1")

    private fun completed(
        index: Int,
        actualLoads: List<Int> = listOf(200, 200),
        actualReps: List<Int> = listOf(8, 8),
        outcomes: List<KotlinJournalSetOutcome> =
            listOf(KotlinJournalSetOutcome.COMPLETED, KotlinJournalSetOutcome.COMPLETED),
        exercise: String = "bench",
        basis: LoadBasis = LoadBasis.SMITH_TOTAL,
        sourceRevision: KotlinTrainingSourceRevision = source,
        warmup: Boolean = false
    ): KotlinWorkoutJournalState {
        val id = "workout-$index"
        val targets = buildList {
            if (warmup) add(KotlinWorkoutTarget(
                exercise, ComposerRole.PRIMARY, ComposerSessionPurpose.HYPERTROPHY,
                "warmup-$index", DraftSetType.WARMUP, 0,
                DraftSetLoad.Verified(LoadUnit.LB, basis, n(20)),
                RepBand(5, 5), null, 60, 60
            ))
            for (ordinal in actualReps.indices) add(KotlinWorkoutTarget(
                exercise, ComposerRole.PRIMARY, ComposerSessionPurpose.HYPERTROPHY,
                "set-$index-$ordinal", DraftSetType.WORKING, ordinal,
                DraftSetLoad.Verified(LoadUnit.LB, basis, n(200)),
                RepBand(5, 8), 2, 60, 90
            ))
        }
        val snapshot = KotlinStagedWorkout(
            sourceRevision, "grant-$index", id, 0, 0,
            ComposerSessionPurpose.HYPERTROPHY, 40, targets)
        var j = KotlinWorkoutJournalDomain.begin(snapshot,
            KotlinStoredWorkoutIdentity("stored-$index", id, sourceRevision, 1))
        if (warmup) {
            val warm = KotlinJournalOperation.RecordSet(
                "warmup-event-$index", j.revision, targets.first().setKey,
                KotlinJournalSetOutcome.COMPLETED,
                KotlinPerformedSet(n(500), LoadUnit.LB, 5, null))
            j = (KotlinWorkoutJournalDomain.reduce(j, warm)
                as KotlinJournalDecision.Proposed).state
        }
        for (i in actualReps.indices) {
            val outcome = outcomes[i]
            val record = KotlinJournalOperation.RecordSet(
                "event-$index-$i", j.revision, "set-$index-$i", outcome,
                when (outcome) {
                    KotlinJournalSetOutcome.SKIPPED -> null
                    KotlinJournalSetOutcome.FAILED_ATTEMPT ->
                        KotlinPerformedSet(n(actualLoads[i]), LoadUnit.LB, 0, null)
                    KotlinJournalSetOutcome.COMPLETED ->
                        KotlinPerformedSet(n(actualLoads[i]), LoadUnit.LB, actualReps[i], 2)
                })
            j = (KotlinWorkoutJournalDomain.reduce(j, record)
                as KotlinJournalDecision.Proposed).state
        }
        return (KotlinWorkoutJournalDomain.reduce(j,
            KotlinJournalOperation.Finish("finish-$index", j.revision))
            as KotlinJournalDecision.Proposed).state
    }

    private fun correction(
        original: KotlinWorkoutJournalState,
        reps: Int = 3,
        load: Int = 195
    ): KotlinSavedHistoryCorrectionState {
        val initial = KotlinSavedHistoryCorrectionDomain.begin(original)
        return (KotlinSavedHistoryCorrectionDomain.reduce(initial,
            KotlinSavedSetCorrection("saved-edit", 0,
                original.snapshot.targets.last().setKey,
                "event-2-1", KotlinJournalSetOutcome.COMPLETED,
                KotlinPerformedSet(n(load), LoadUnit.LB, reps, 1)))
            as KotlinSavedCorrectionDecision.Proposed).state
    }

    private fun snapshot(
        vararg entries: KotlinChronologicalWorkout,
        revision: Long = 6
    ): KotlinGlobalHistoryRead = KotlinGlobalHistoryRead(
        "athlete-1", revision, revision,
        entries.map { session ->
            val j = session.journal
            KotlinHistoryManifestEntry(
                session.completionOrder, j.snapshot.workoutId,
                j.storedIdentity.storageReceiptId,
                checkNotNull(j.finishedByEventId), j.revision,
                session.savedCorrections?.correctionRevision ?: 0)
        }, entries.toList()
    )

    private fun item(j: KotlinWorkoutJournalState, order: Long) =
        KotlinChronologicalWorkout(order, j)

    private fun index(read: KotlinGlobalHistoryRead) =
        (KotlinGlobalHistoryIndexDomain.project(read)
            as KotlinGlobalHistoryResult.Candidate).index

    @Test fun lastShowsActualLatestWorkoutEvenWhenEverySetSkippedOrFailed() {
        val first = completed(1)
        val last = completed(2, outcomes = listOf(
            KotlinJournalSetOutcome.SKIPPED,
            KotlinJournalSetOutcome.FAILED_ATTEMPT))
        val result = index(snapshot(item(first, 1), item(last, 2)))
        val lastExercise = result.lastByExercise.getValue("bench")
        assertEquals("workout-2", lastExercise.workoutId)
        assertEquals(listOf(KotlinJournalSetOutcome.SKIPPED,
            KotlinJournalSetOutcome.FAILED_ATTEMPT),
            lastExercise.workingSets.map { it.outcome })
        assertEquals(n(200), result.highestCompletedLoad.values.single().actualLoad)
        assertEquals("workout-1", result.highestCompletedLoad.values.single().workoutId)
        assertEquals(2, result.completedWorkoutIds.size)
        assertFalse(result.canPublishPr)
        assertFalse(result.canIssueProgression)
    }

    @Test fun savedCorrectionInvalidatesOldHighWeightRecordAndChangesFingerprint() {
        val first = completed(1, actualLoads = listOf(200, 200))
        val second = completed(2, actualLoads = listOf(205, 205))
        val before = index(snapshot(item(first, 1), item(second, 2)))
        assertEquals(n(205), before.highestCompletedLoad.values.single().actualLoad)
        val edited = correction(second, reps = 3, load = 195)
        val after = index(snapshot(item(first, 1),
            KotlinChronologicalWorkout(2, second, edited), revision = 7))
        assertEquals(n(205), after.highestCompletedLoad.values.single().actualLoad)
        // The first uncorrected set still really did complete at 205.
        assertEquals(1, after.mostRepsAtLoad.count { it.actualLoad == n(195) })
        assertEquals(3, after.mostRepsAtLoad.single {
            it.actualLoad == n(195) }.actualReps)
        assertNotEquals(before.contentFingerprint, after.contentFingerprint)
        assertEquals("saved-edit",
            after.lastByExercise.getValue("bench").workingSets.last().effectiveEventId)

        val correctedFirst = (KotlinSavedHistoryCorrectionDomain.reduce(
            edited, KotlinSavedSetCorrection("saved-edit-2", 1, "set-2-0",
                "event-2-0", KotlinJournalSetOutcome.COMPLETED,
                KotlinPerformedSet(n(195), LoadUnit.LB, 3, 1)))
            as KotlinSavedCorrectionDecision.Proposed).state
        val fullyCorrected = index(snapshot(item(first, 1),
            KotlinChronologicalWorkout(2, second, correctedFirst), revision = 8))
        assertEquals(n(200), fullyCorrected.highestCompletedLoad.values.single().actualLoad)
        assertEquals("workout-1",
            fullyCorrected.highestCompletedLoad.values.single().workoutId)
        assertFalse(fullyCorrected.mostRepsAtLoad.any { it.actualLoad == n(205) })
        assertEquals(3, fullyCorrected.mostRepsAtLoad.single {
            it.actualLoad == n(195) }.actualReps)
    }

    @Test fun warmupsNeverBecomeRecordsAndPhysicalBasesAreNotMixed() {
        val smith = completed(1, actualLoads = listOf(200, 205), warmup = true)
        val hand = completed(2, actualLoads = listOf(30, 30),
            basis = LoadBasis.DUMBBELL_PER_HAND)
        val result = index(snapshot(item(smith, 1), item(hand, 2)))
        assertEquals(2, result.highestCompletedLoad.size)
        val smithRecord = result.highestCompletedLoad.getValue(
            KotlinHistoryLoadKey("bench", LoadUnit.LB, LoadBasis.SMITH_TOTAL))
        assertEquals(n(205), smithRecord.actualLoad)
        val handRecord = result.highestCompletedLoad.getValue(
            KotlinHistoryLoadKey("bench", LoadUnit.LB, LoadBasis.DUMBBELL_PER_HAND))
        assertEquals(n(30), handRecord.actualLoad)
        assertFalse(result.mostRepsAtLoad.any { it.actualLoad == n(500) })
    }

    @Test fun manifestDetectsMissingEditedFileOrStaleRevision() {
        val first = completed(1)
        val second = completed(2)
        val correction = correction(second)
        val read = snapshot(item(first, 1),
            KotlinChronologicalWorkout(2, second, correction))
        val deletedFileView = read.copy(
            completedWorkouts = listOf(item(first, 1), item(second, 2)))
        assertEquals(KotlinGlobalHistoryResult.Rejected(
            KotlinGlobalHistoryRefusal.MANIFEST_RECEIPT_MISMATCH),
            KotlinGlobalHistoryIndexDomain.project(deletedFileView))
        val stale = read.copy(currentManifestRevision = 7)
        assertEquals(KotlinGlobalHistoryResult.Rejected(
            KotlinGlobalHistoryRefusal.STALE_MANIFEST),
            KotlinGlobalHistoryIndexDomain.project(stale))
        val forged = read.copy(manifest = read.manifest.mapIndexed { i, entry ->
            if (i == 1) entry.copy(savedCorrectionRevision = 0) else entry
        })
        assertEquals(KotlinGlobalHistoryResult.Rejected(
            KotlinGlobalHistoryRefusal.MANIFEST_RECEIPT_MISMATCH),
            KotlinGlobalHistoryIndexDomain.project(forged))
    }

    @Test fun duplicateOrReorderedManifestCannotBeIndexed() {
        val first = completed(1)
        val second = completed(2)
        val initial = snapshot(item(first, 1), item(second, 2))
        assertEquals(KotlinGlobalHistoryResult.Rejected(
            KotlinGlobalHistoryRefusal.MANIFEST_ORDER_MISMATCH),
            KotlinGlobalHistoryIndexDomain.project(initial.copy(
                manifest = initial.manifest.reversed())))
        assertEquals(KotlinGlobalHistoryResult.Rejected(
            KotlinGlobalHistoryRefusal.DUPLICATE_WORKOUT),
            KotlinGlobalHistoryIndexDomain.project(snapshot(
                item(first, 1), item(first, 2))))
        assertEquals(KotlinGlobalHistoryResult.Rejected(
            KotlinGlobalHistoryRefusal.MANIFEST_RECEIPT_MISMATCH),
            KotlinGlobalHistoryIndexDomain.project(initial.copy(
                manifest = initial.manifest.mapIndexed { i, entry ->
                    if (i == 1) entry.copy(finishEventId = "wrong-finish") else entry
                })))
    }

    @Test fun incorrectAthleteTamperedJournalAndMismatchedCorrectionsAreRejected() {
        val first = completed(1)
        val second = completed(2)
        val read = snapshot(item(first, 1), item(second, 2))
        assertEquals(KotlinGlobalHistoryResult.Rejected(
            KotlinGlobalHistoryRefusal.CROSS_ATHLETE),
            KotlinGlobalHistoryIndexDomain.project(read.copy(athleteId = "stranger")))
        assertEquals(KotlinGlobalHistoryResult.Rejected(
            KotlinGlobalHistoryRefusal.UNVERIFIED_WORKOUT_OR_CORRECTIONS),
            KotlinGlobalHistoryIndexDomain.project(snapshot(
                item(first, 1), item(second.copy(effectiveSets = emptyMap()), 2))))
        val wrongCorrection = KotlinSavedHistoryCorrectionDomain.begin(second)
        val incorrect = KotlinChronologicalWorkout(2, first, wrongCorrection)
        val swapped = snapshot(item(second, 1), incorrect)
        assertEquals(KotlinGlobalHistoryResult.Rejected(
            KotlinGlobalHistoryRefusal.UNVERIFIED_WORKOUT_OR_CORRECTIONS),
            KotlinGlobalHistoryIndexDomain.project(swapped))
    }

    @Test fun identicalReadMakesIdenticalRevisionBoundFingerprint() {
        val first = completed(1)
        val second = completed(2)
        val read = snapshot(item(first, 1), item(second, 2))
        val firstProjection = index(read)
        val secondProjection = index(read)
        assertEquals(firstProjection, secondProjection)
        assertEquals(64, firstProjection.contentFingerprint.length)
        assertEquals(6L, firstProjection.manifestRevision)
        assertTrue(index(snapshot()).completedWorkoutIds.isEmpty())
    }
}
