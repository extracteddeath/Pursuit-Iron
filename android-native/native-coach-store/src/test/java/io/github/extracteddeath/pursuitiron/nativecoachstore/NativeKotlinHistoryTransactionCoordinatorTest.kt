package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.math.BigDecimal
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class NativeKotlinHistoryTransactionCoordinatorTest {
    @get:Rule val temporary = TemporaryFolder()
    private val source = KotlinTrainingSourceRevision(
        "athlete-1", 2, "program-1", 4, "gym-1", 3, "catalog-1", "rules-1")
    private val root get() = temporary.root.toPath()
    private fun journal() = KotlinNativeJournalFileStore(root)
    private fun corrections() = KotlinNativeSavedCorrectionFileStore(root, journal())
    private fun ledger() = NativeKotlinHistoryManifestFileStore(root)
    private fun fence() = NativeKotlinApprovalRevisionFence(root)
    private var allowed = true
    private val auth = object : NativeKotlinWorkoutAuthority {
        override fun currentApprovedWorkout(
            ownerKey: String, workoutId: String
        ): NativeKotlinWorkoutApproval? = null

        override fun canAccessIssuedWorkout(
            ownerKey: String, source: KotlinTrainingSourceRevision
        ): Boolean = allowed && ownerKey == "owner-1" &&
            source.athleteId == "athlete-1"
    }
    private fun coordinator() = NativeKotlinHistoryTransactionCoordinator(
        ledger(), journal(), corrections(), auth, fence())
    private fun accepted(result: NativeHistoryTransactionResult) =
        result as NativeHistoryTransactionResult.Durable
    private fun manifest() = ledger().open("owner-1", "athlete-1")
        as NativeHistoryLedgerResult.Durable

    private fun enroll() {
        assertEquals(1L, accepted(coordinator().enroll(
            "owner-1", "athlete-1", { _, _ -> allowed })).manifest.revision)
    }
    private fun issueResolved(id: Int): KotlinWorkoutJournalState {
        val workout = "workout-$id"
        val targets = (0..1).map { i ->
            KotlinWorkoutTarget("bench", ComposerRole.PRIMARY,
                ComposerSessionPurpose.HYPERTROPHY, "slot-$id-$i",
                DraftSetType.WORKING, i,
                DraftSetLoad.Verified(LoadUnit.LB, LoadBasis.SMITH_TOTAL,
                    BigDecimal("200")),
                RepBand(5, 8), 2, 60, 90)
        }
        val snapshot = KotlinStagedWorkout(
            source, "grant-$id", workout, 0, 0,
            ComposerSessionPurpose.HYPERTROPHY, 40, targets)
        val identity = KotlinStoredWorkoutIdentity(
            "storage-$id", workout, source, 1)
        val writer = journal()
        assertTrue(writer.issue("owner-1", snapshot, identity)
            is NativeJournalFileResult.Durable)
        var result: KotlinWorkoutJournalState? = null
        for (i in 0..1) {
            val accepted = writer.appendStored("owner-1", workout,
                KotlinJournalOperation.RecordSet(
                    "event-$id-$i", i.toLong(), "slot-$id-$i",
                    KotlinJournalSetOutcome.COMPLETED,
                    KotlinPerformedSet(BigDecimal("200"), LoadUnit.LB, 8, 2)
                ), { owner, revision -> auth.canAccessIssuedWorkout(owner, revision) })
            result = (accepted as NativeJournalFileResult.Durable).journal
        }
        return checkNotNull(result)
    }
    private fun finish(id: Int) = KotlinJournalOperation.Finish("finish-$id", 2)
    private fun edit(
        workout: Int, event: String = "edit-$workout",
        revision: Long = 0, predecessor: String = "event-$workout-1",
        reps: Int = 3
    ) = KotlinSavedSetCorrection(
        event, revision, "slot-$workout-1", predecessor,
        KotlinJournalSetOutcome.COMPLETED,
        KotlinPerformedSet(BigDecimal("200"), LoadUnit.LB, reps, null)
    )
    private fun ownedJournal(owner: String, source: KotlinTrainingSourceRevision) =
        auth.canAccessIssuedWorkout(owner, source)
    private fun finished(id: Int) {
        assertEquals(id, accepted(coordinator().complete("owner-1", "athlete-1",
            "workout-$id", finish(id))).manifest.completions.size)
    }

    @Test fun enrollmentMustBeApprovedAndNeverImplicitlyCreatesEmptyHistory() {
        assertEquals(NativeHistoryTransactionRefusal.NOT_ENROLLED_OR_AUTHORIZED,
            (coordinator().recover("owner-1", "athlete-1")
                as NativeHistoryTransactionResult.Rejected).reason)
        allowed = false
        assertEquals(NativeHistoryTransactionRefusal.NOT_ENROLLED_OR_AUTHORIZED,
            (coordinator().enroll("owner-1", "athlete-1",
                { _, _ -> allowed })
                as NativeHistoryTransactionResult.Rejected).reason)
        assertEquals(NativeHistoryLedgerResult.Rejected(
            NativeHistoryLedgerRefusal.NOT_ENROLLED),
            ledger().open("owner-1", "athlete-1"))
        allowed = true
        enroll()
        assertEquals(emptyList<KotlinHistoryManifestEntry>(),
            manifest().ledger.completions)
    }

    @Test fun committedFinishesAreUniqueOrderedAndExactRetriesDoNotAdvanceRevision() {
        enroll()
        issueResolved(1)
        finished(1)
        val first = manifest().ledger
        assertEquals(2L, first.revision)
        assertEquals("workout-1", first.completions.single().workoutId)
        assertEquals(1L, first.completions.single().completionOrder)
        assertTrue(accepted(coordinator().complete(
            "owner-1", "athlete-1", "workout-1", finish(1))).replayed)
        assertEquals(first, manifest().ledger)
        issueResolved(2)
        finished(2)
        val second = manifest().ledger
        assertEquals(3L, second.revision)
        assertEquals(listOf("workout-1", "workout-2"),
            second.completions.map { it.workoutId })
        assertEquals(listOf(1L, 2L),
            second.completions.map { it.completionOrder })
    }

    @Test fun editCommitsCorrectionAndManifestRevisionThenIdempotentlyRetries() {
        enroll()
        issueResolved(1)
        finished(1)
        val first = accepted(coordinator().correct(
            "owner-1", "athlete-1", "workout-1", edit(1)))
        assertFalse(first.replayed)
        assertEquals(3L, first.manifest.revision)
        assertEquals(1L, first.manifest.completions.single().savedCorrectionRevision)
        assertTrue(accepted(coordinator().correct(
            "owner-1", "athlete-1", "workout-1", edit(1))).replayed)
        assertEquals(3L, manifest().ledger.revision)
        val second = accepted(coordinator().correct(
            "owner-1", "athlete-1", "workout-1",
            edit(1, "edit-again", 1, "edit-1", 4)))
        assertEquals(4L, second.manifest.revision)
        assertEquals(2L, second.manifest.completions.single().savedCorrectionRevision)
        val last = corrections().openStored("owner-1", "workout-1",
            ::ownedJournal) as NativeSavedCorrectionFileResult.Opened
        assertEquals(2L, last.state.correctionRevision)
        assertEquals(4, (KotlinWorkoutHistoryEvidenceDomain.projectSaved(
            last.state) as KotlinHistoryProjectionResult.Candidate)
            .evidence.sets.last().actual?.reps)
    }

    @Test fun recoverAfterPrepareBeforeOriginalFinishReplaysExactlyOneEvent() {
        enroll()
        issueResolved(1)
        val intent = NativeHistoryWriteIntent.Finish("workout-1", finish(1))
        val prepared = ledger().prepare("owner-1", "athlete-1", 1, intent)
        assertTrue(prepared is NativeHistoryLedgerResult.Durable)
        assertFalse(manifest().ledger.readyForHistoryReads)
        val read = NativeKotlinHistoryManifestFileAuthority(
            ledger(), { _, _ -> true })
        assertNull(read.currentHistoryManifest("owner-1", "athlete-1"))
        val result = accepted(coordinator().recover("owner-1", "athlete-1"))
        assertEquals(1, result.manifest.completions.size)
        assertEquals(2L, result.manifest.revision)
        assertNull(manifest().ledger.pending)
        assertEquals(3L, (journal().openStored(
            "owner-1", "workout-1", ::ownedJournal)
            as NativeJournalFileResult.Durable).journal.revision)
        assertEquals(1, (journal().openStored("owner-1", "workout-1",
            ::ownedJournal) as NativeJournalFileResult.Durable)
            .journal.receipts.count {
                it.operation is KotlinJournalOperation.Finish
            })
    }

    @Test fun recoverAfterFinishReceiptBeforeManifestFinalization() {
        enroll()
        issueResolved(1)
        val intent = NativeHistoryWriteIntent.Finish("workout-1", finish(1))
        ledger().prepare("owner-1", "athlete-1", 1, intent)
        val direct = journal().appendStored("owner-1", "workout-1",
            finish(1), ::ownedJournal)
        assertTrue(direct is NativeJournalFileResult.Durable)
        val result = accepted(coordinator().recover("owner-1", "athlete-1"))
        assertEquals(2L, result.manifest.revision)
        assertEquals("finish-1", result.manifest.completions.single().finishEventId)
        assertTrue(accepted(coordinator().complete(
            "owner-1", "athlete-1", "workout-1", finish(1))).replayed)
    }

    @Test fun recoverSavedEditWithAndWithoutPreviouslyWrittenCorrectionFile() {
        enroll()
        issueResolved(1)
        finished(1)
        val intent = NativeHistoryWriteIntent.Correct("workout-1", edit(1))
        ledger().prepare("owner-1", "athlete-1", 2, intent)
        assertEquals(3L, accepted(coordinator().recover(
            "owner-1", "athlete-1")).manifest.revision)
        assertTrue(accepted(coordinator().correct("owner-1", "athlete-1",
            "workout-1", edit(1))).replayed)
        val again = NativeHistoryWriteIntent.Correct("workout-1",
            edit(1, "later", 1, "edit-1", 4))
        ledger().prepare("owner-1", "athlete-1", 3, again)
        assertTrue(corrections().appendStored("owner-1", "workout-1",
            again.operation, ::ownedJournal) is NativeSavedCorrectionFileResult.Durable)
        assertEquals(4L, accepted(coordinator().recover(
            "owner-1", "athlete-1")).manifest.revision)
        assertEquals(2L, manifest().ledger.completions.single().savedCorrectionRevision)
        assertEquals(2, (corrections().openStored(
            "owner-1", "workout-1", ::ownedJournal)
            as NativeSavedCorrectionFileResult.Opened).state.receipts.size)
    }

    @Test fun pendingIntentBlocksHistoryAndFailedAuthCannotSilentlyEraseIt() {
        enroll()
        issueResolved(1)
        ledger().prepare("owner-1", "athlete-1", 1,
            NativeHistoryWriteIntent.Finish("workout-1", finish(1)))
        allowed = false
        assertEquals(NativeHistoryTransactionRefusal.HISTORY_NOT_READY,
            (coordinator().recover("owner-1", "athlete-1")
                as NativeHistoryTransactionResult.Rejected).reason)
        assertFalse(manifest().ledger.readyForHistoryReads)
        assertEquals(1L, manifest().ledger.revision)
        allowed = true
        assertEquals(2L, accepted(coordinator().recover(
            "owner-1", "athlete-1")).manifest.revision)
    }

    @Test fun outOfBandFinishedWorkoutIsNotSilentlyImportedOrDuplicated() {
        enroll()
        issueResolved(1)
        assertTrue(journal().appendStored("owner-1", "workout-1",
            finish(1), ::ownedJournal) is NativeJournalFileResult.Durable)
        assertEquals(NativeHistoryTransactionRefusal.WORKOUT_ALREADY_UNTRACKED,
            (coordinator().complete("owner-1", "athlete-1", "workout-1",
                finish(1)) as NativeHistoryTransactionResult.Rejected).reason)
        assertTrue(manifest().ledger.completions.isEmpty())
    }

    @Test fun invalidManifestFileIsRejectedNotResetDuringEnroll() {
        enroll()
        val bins = Files.list(root).use { stream ->
            stream.filter { it.fileName.toString().startsWith("history-") &&
                it.fileName.toString().endsWith(".bin") }.toList()
        }
        assertEquals(1, bins.size)
        val original = Files.readAllBytes(bins.single())
        original[original.size / 2] =
            (original[original.size / 2].toInt() xor 1).toByte()
        Files.write(bins.single(), original)
        assertEquals(NativeHistoryLedgerResult.Rejected(
            NativeHistoryLedgerRefusal.CORRUPT_HISTORY),
            ledger().enroll("owner-1", "athlete-1", { _, _ -> true }))
        assertEquals(NativeHistoryTransactionRefusal.LEDGER_REJECTED,
            (coordinator().recover("owner-1", "athlete-1")
                as NativeHistoryTransactionResult.Rejected).reason)
        assertArrayEquals(original, Files.readAllBytes(bins.single()))
    }

    @Test fun committedManifestFeedsRealPrivateHistoryReaderAndPendingBlocksIt() {
        enroll()
        issueResolved(1)
        finished(1)
        val sourceAuthority = NativeKotlinHistoryManifestFileAuthority(
            ledger(), { owner, athlete ->
                allowed && owner == "owner-1" && athlete == "athlete-1"
            })
        fun read() = NativeKotlinGlobalHistoryReadCoordinator(
            journal(), corrections(), auth, sourceAuthority, fence()
        ).read("owner-1", "athlete-1")

        val initial = read() as NativeKotlinHistoryReadResult.Review
        assertEquals(2L, initial.index.manifestRevision)
        assertEquals(8, initial.index.lastByExercise
            .getValue("bench").workingSets.last().actual?.reps)
        val firstFingerprint = initial.index.contentFingerprint

        val intent = NativeHistoryWriteIntent.Correct("workout-1", edit(1))
        val prepared = ledger().prepare("owner-1", "athlete-1", 2, intent)
        assertTrue(prepared is NativeHistoryLedgerResult.Durable)
        assertTrue(read() is NativeKotlinHistoryReadResult.Rejected)
        assertEquals(3L, accepted(coordinator().recover(
            "owner-1", "athlete-1")).manifest.revision)
        val revised = read() as NativeKotlinHistoryReadResult.Review
        assertEquals(3L, revised.index.manifestRevision)
        assertEquals(3, revised.index.lastByExercise
            .getValue("bench").workingSets.last().actual?.reps)
        assertNotEquals(firstFingerprint, revised.index.contentFingerprint)
        assertFalse(revised.index.canPublishPr)
        assertFalse(revised.index.canIssueProgression)
    }

}
