package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.math.BigDecimal
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class KotlinNativeSavedCorrectionFileStoreTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun n(value: String) = BigDecimal(value)
    private val source = KotlinTrainingSourceRevision(
        "athlete-1", 2, "program-1", 4, "gym-1", 3, "catalog-2", "rules-3")
    private val targets = (0..1).map { ordinal ->
        KotlinWorkoutTarget(
            "bench", ComposerRole.PRIMARY,
            ComposerSessionPurpose.HYPERTROPHY, "set-$ordinal",
            DraftSetType.WORKING, ordinal,
            DraftSetLoad.Verified(LoadUnit.LB, LoadBasis.SMITH_TOTAL, n("200")),
            RepBand(5, 8), 2, 60, 120)
    }
    private val snapshot = KotlinStagedWorkout(
        source, "grant-1", "workout-1", 0, 0,
        ComposerSessionPurpose.HYPERTROPHY, 35, targets)
    private val identity = KotlinStoredWorkoutIdentity(
        "storage-1", "workout-1", source, 1)
    private val allow: (String, KotlinTrainingSourceRevision) -> Boolean =
        { owner, revision -> owner == "owner-1" && revision == source }

    private fun filesDir(): Path = temporary.root.toPath()
    private fun journal() = KotlinNativeJournalFileStore(filesDir())
    private fun corrections() = KotlinNativeSavedCorrectionFileStore(filesDir(), journal())

    private fun durableJournal(result: NativeJournalFileResult) =
        (result as NativeJournalFileResult.Durable).journal
    private fun stored(result: NativeSavedCorrectionFileResult) =
        result as NativeSavedCorrectionFileResult.Durable
    private fun opened(result: NativeSavedCorrectionFileResult) =
        result as NativeSavedCorrectionFileResult.Opened

    private fun issueAndFinish(): KotlinWorkoutJournalState {
        val writer = journal()
        durableJournal(writer.issue("owner-1", snapshot, identity))
        durableJournal(writer.appendStored("owner-1", "workout-1",
            KotlinJournalOperation.RecordSet(
                "event-0", 0, "set-0", KotlinJournalSetOutcome.COMPLETED,
                KotlinPerformedSet(n("200"), LoadUnit.LB, 8, 3)), allow))
        durableJournal(writer.appendStored("owner-1", "workout-1",
            KotlinJournalOperation.RecordSet(
                "event-1", 1, "set-1", KotlinJournalSetOutcome.COMPLETED,
                KotlinPerformedSet(n("200"), LoadUnit.LB, 8, 3)), allow))
        return durableJournal(writer.appendStored("owner-1", "workout-1",
            KotlinJournalOperation.Finish("finish-1", 2), allow))
    }

    private fun correction(
        event: String = "edit-1", expectedRevision: Long = 0,
        previous: String = "event-1", reps: Int = 4
    ) = KotlinSavedSetCorrection(
        event, expectedRevision, "set-1", previous,
        KotlinJournalSetOutcome.COMPLETED,
        KotlinPerformedSet(n("200"), LoadUnit.LB, reps, null))

    private fun correctionFile(): Path = Files.list(filesDir()).use { stream ->
        val list = stream.filter {
            it.fileName.toString().startsWith("correction-") &&
                it.fileName.toString().endsWith(".bin")
        }.toList()
        assertEquals(1, list.size)
        list.single()
    }

    @Test fun noFileReadCreatesNoEditAndRejectsUnfinishedWorkout() {
        val storage = journal()
        durableJournal(storage.issue("owner-1", snapshot, identity))
        assertEquals(NativeSavedCorrectionFileResult.Rejected(
            NativeSavedCorrectionRefusal.NOT_ISSUED_OR_FINISHED),
            corrections().openStored("owner-1", "workout-1", allow))
        durableJournal(storage.appendStored("owner-1", "workout-1",
            KotlinJournalOperation.RecordSet(
                "event-0", 0, "set-0", KotlinJournalSetOutcome.COMPLETED,
                KotlinPerformedSet(n("200"), LoadUnit.LB, 8, 3)), allow))
        durableJournal(storage.appendStored("owner-1", "workout-1",
            KotlinJournalOperation.RecordSet(
                "event-1", 1, "set-1", KotlinJournalSetOutcome.COMPLETED,
                KotlinPerformedSet(n("200"), LoadUnit.LB, 8, 3)), allow))
        durableJournal(storage.appendStored("owner-1", "workout-1",
            KotlinJournalOperation.Finish("finish-1", 2), allow))
        val result = opened(corrections().openStored("owner-1", "workout-1", allow))
        assertFalse(result.hasCorrectionFile)
        assertEquals(0L, result.state.correctionRevision)
        assertFalse(Files.list(filesDir()).use { stream ->
            stream.anyMatch { it.fileName.toString().endsWith(".bin") &&
                it.fileName.toString().startsWith("correction-") }
        })
    }

    @Test fun crashRestartRetainsCorrectionsWithoutChangingOriginalOrTargetRir() {
        val original = issueAndFinish()
        val first = stored(corrections().appendStored(
            "owner-1", "workout-1", correction(), allow))
        assertFalse(first.replayed)
        assertEquals(1L, first.state.correctionRevision)
        val cold = opened(corrections().openStored("owner-1", "workout-1", allow))
        assertTrue(cold.hasCorrectionFile)
        assertEquals(first.state, cold.state)
        val retry = stored(corrections().appendStored(
            "owner-1", "workout-1", correction(), allow))
        assertTrue(retry.replayed)
        assertEquals(1, retry.state.receipts.size)
        val history = (KotlinWorkoutHistoryEvidenceDomain.projectSaved(
            cold.state) as KotlinHistoryProjectionResult.Candidate).evidence
        assertEquals(4, history.sets.last().actual?.reps)
        assertNull(history.sets.last().actual?.actualRir)
        assertEquals(2, history.sets.last().issuedRir)
        assertEquals("event-1", history.sets.last().correctsEventId)
        assertEquals("finish-1", history.finishEventId)
        assertEquals(setOf(1), history.exerciseReviews.single()
            .progressionReview?.belowFloorOrdinals)
        assertEquals(original, durableJournal(
            journal().openStored("owner-1", "workout-1", allow)))
        assertEquals(8, (original.effectiveSets.getValue("set-1").operation
            as KotlinJournalOperation.RecordSet).performed?.reps)
    }

    @Test fun concurrentCorrectionAttemptsCommitExactlyOneRevision() {
        issueAndFinish()
        val pool = Executors.newFixedThreadPool(2)
        val gate = CountDownLatch(1)
        try {
            val jobs = listOf("edit-a", "edit-b").map { id ->
                pool.submit<NativeSavedCorrectionFileResult> {
                    gate.await()
                    corrections().appendStored("owner-1", "workout-1",
                        correction(event = id, reps = if (id == "edit-a") 5 else 6), allow)
                }
            }
            gate.countDown()
            val results = jobs.map { it.get(15, TimeUnit.SECONDS) }
            assertEquals(1, results.count { it is NativeSavedCorrectionFileResult.Durable })
            assertEquals(1, results.count {
                it is NativeSavedCorrectionFileResult.Rejected &&
                    it.reason == NativeSavedCorrectionRefusal.CORRECTION_REJECTED &&
                    it.correctionReason == KotlinSavedCorrectionRefusal.REVISION_CONFLICT
            })
            val reopened = opened(corrections().openStored(
                "owner-1", "workout-1", allow))
            assertEquals(1L, reopened.state.correctionRevision)
            assertEquals(1, reopened.state.receipts.size)
        } finally {
            pool.shutdownNow()
        }
    }

    @Test fun corruptCorrectionFileFailsClosedAndIsNeverOverwritten() {
        issueAndFinish()
        stored(corrections().appendStored("owner-1", "workout-1",
            correction(), allow))
        val file = correctionFile()
        val bytes = Files.readAllBytes(file)
        bytes[bytes.size / 2] = (bytes[bytes.size / 2].toInt() xor 1).toByte()
        Files.write(file, bytes)
        val before = Files.readAllBytes(file)
        assertEquals(NativeSavedCorrectionFileResult.Rejected(
            NativeSavedCorrectionRefusal.CORRUPT_OR_CONFLICTING_FILE),
            corrections().openStored("owner-1", "workout-1", allow))
        assertEquals(NativeSavedCorrectionFileResult.Rejected(
            NativeSavedCorrectionRefusal.CORRUPT_OR_CONFLICTING_FILE),
            corrections().appendStored("owner-1", "workout-1",
                correction(event = "try-replace"), allow))
        assertArrayEquals(before, Files.readAllBytes(file))
    }

    @Test fun ownerAndPredecessorMismatchCannotSilentlyEditHistory() {
        issueAndFinish()
        assertEquals(NativeSavedCorrectionFileResult.Rejected(
            NativeSavedCorrectionRefusal.NOT_AUTHORIZED),
            corrections().appendStored("owner-1", "workout-1", correction(),
                { _, _ -> false }))
        assertEquals(NativeSavedCorrectionFileResult.Rejected(
            NativeSavedCorrectionRefusal.NOT_ISSUED_OR_FINISHED),
            corrections().openStored("owner-2", "workout-1", allow))
        assertEquals(NativeSavedCorrectionRefusal.CORRECTION_REJECTED,
            (corrections().appendStored("owner-1", "workout-1",
                correction(previous = "wrong"), allow)
                as NativeSavedCorrectionFileResult.Rejected).reason)
        assertEquals(NativeSavedCorrectionFileResult.Rejected(
            NativeSavedCorrectionRefusal.INVALID_ARGUMENT),
            corrections().appendStored("../bad", "workout-1", correction(), allow))
        val empty = opened(corrections().openStored("owner-1", "workout-1", allow))
        assertEquals(0L, empty.state.correctionRevision)
        assertFalse(empty.hasCorrectionFile)
    }

    @Test fun chainedSavedEditsRecoverInOrderAndRejectStaleEdit() {
        issueAndFinish()
        stored(corrections().appendStored("owner-1", "workout-1",
            correction(), allow))
        val second = stored(corrections().appendStored("owner-1", "workout-1",
            correction(event = "edit-2", expectedRevision = 1,
                previous = "edit-1", reps = 7), allow))
        val recovered = opened(corrections().openStored("owner-1", "workout-1", allow))
        assertEquals(second.state, recovered.state)
        assertEquals(2L, recovered.state.correctionRevision)
        assertEquals(listOf("edit-1", "edit-2"),
            recovered.state.receipts.map { it.correction.eventId })
        assertEquals(KotlinSavedCorrectionRefusal.REVISION_CONFLICT,
            (corrections().appendStored("owner-1", "workout-1",
                correction(event = "stale", expectedRevision = 0), allow)
                as NativeSavedCorrectionFileResult.Rejected).correctionReason)
    }
}
