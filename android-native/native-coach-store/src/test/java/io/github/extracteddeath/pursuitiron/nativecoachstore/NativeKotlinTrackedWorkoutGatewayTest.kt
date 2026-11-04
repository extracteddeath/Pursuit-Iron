package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class NativeKotlinTrackedWorkoutGatewayTest {
    @get:Rule val temporary = TemporaryFolder()
    private val root get() = temporary.root.toPath()
    private val source = KotlinTrainingSourceRevision(
        "athlete-1", 2, "program-1", 4, "gym-1", 3, "catalog-1", "rules-1")
    private var active = true
    private val authority = object : NativeKotlinWorkoutAuthority {
        override fun currentApprovedWorkout(
            ownerKey: String, workoutId: String
        ): NativeKotlinWorkoutApproval? = null

        override fun canAccessIssuedWorkout(
            ownerKey: String, source: KotlinTrainingSourceRevision
        ): Boolean = active && ownerKey == "owner-1" &&
            source.athleteId == "athlete-1"
    }
    private fun journal() = KotlinNativeJournalFileStore(root)
    private fun correction() = KotlinNativeSavedCorrectionFileStore(root, journal())
    private fun ledger() = NativeKotlinHistoryManifestFileStore(root)
    private fun fence() = NativeKotlinApprovalRevisionFence(root)
    private fun history() = NativeKotlinHistoryTransactionCoordinator(
        ledger(), journal(), correction(), authority, fence())
    private fun gateway() = NativeKotlinTrackedWorkoutGateway(
        NativeKotlinWorkoutCoordinator(journal(), authority, fence()),
        history(), ledger(), correction(), authority, fence())
    private fun allow(owner: String, from: KotlinTrainingSourceRevision) =
        authority.canAccessIssuedWorkout(owner, from)

    private fun issue(): KotlinWorkoutJournalState {
        val targets = (0..1).map { index ->
            KotlinWorkoutTarget(
                "bench", ComposerRole.PRIMARY,
                ComposerSessionPurpose.HYPERTROPHY, "set-$index",
                DraftSetType.WORKING, index,
                DraftSetLoad.Verified(
                    LoadUnit.LB, LoadBasis.SMITH_TOTAL, BigDecimal("200")),
                RepBand(5, 8), 2, 60, 90)
        }
        val snapshot = KotlinStagedWorkout(
            source, "grant-1", "workout-1", 0, 0,
            ComposerSessionPurpose.HYPERTROPHY, 45, targets)
        val identity = KotlinStoredWorkoutIdentity(
            "storage-1", "workout-1", source, 1)
        return (journal().issue("owner-1", snapshot, identity)
            as NativeJournalFileResult.Durable).journal
    }

    private fun enroll() {
        val result = history().enroll("owner-1", "athlete-1") { o, a ->
            active && o == "owner-1" && a == "athlete-1"
        }
        assertTrue(result.toString(), result is NativeHistoryTransactionResult.Durable)
    }

    private fun set(index: Int, revision: Long) =
        KotlinJournalOperation.RecordSet(
            "event-$index", revision, "set-$index",
            KotlinJournalSetOutcome.COMPLETED,
            KotlinPerformedSet(BigDecimal("200"), LoadUnit.LB, 8, 2))
    private fun finish() = KotlinJournalOperation.Finish("finish-1", 2L)
    private fun edit(event: String = "correction-1", reps: Int = 3) =
        KotlinSavedSetCorrection(
            event, 0, "set-1", "event-1",
            KotlinJournalSetOutcome.COMPLETED,
            KotlinPerformedSet(BigDecimal("200"), LoadUnit.LB, reps, null))

    private fun original(): KotlinWorkoutJournalState =
        (journal().openStored("owner-1", "workout-1", ::allow)
            as NativeJournalFileResult.Durable).journal

    private fun recorded(): KotlinWorkoutJournalState {
        var current = original()
        for (index in 0..1) {
            val op = set(index, current.revision)
            val result = gateway().append(
                "owner-1", "athlete-1", "workout-1", op)
            assertTrue(result.toString(), result is NativeJournalFileResult.Durable)
            current = (result as NativeJournalFileResult.Durable).journal
        }
        return current
    }

    @Test fun withoutEnrolledHistoryAllCandidateEntryPointsFailClosed() {
        issue()
        val facade = gateway()
        assertEquals(NativeKotlinIssueResult.Rejected(
            NativeKotlinIssueRefusal.HISTORY_NOT_READY),
            facade.issue("owner-1", "athlete-1", "workout-1"))
        val blocked = NativeJournalFileResult.Rejected(
            NativeJournalFileRefusal.HISTORY_NOT_READY)
        assertEquals(blocked, facade.open("owner-1", "athlete-1", "workout-1"))
        assertEquals(blocked, facade.append(
            "owner-1", "athlete-1", "workout-1", set(0, 0L)))
        assertEquals(blocked, facade.append(
            "owner-1", "athlete-1", "workout-1", finish()))
        assertEquals(NativeSavedCorrectionFileResult.Rejected(
            NativeSavedCorrectionRefusal.HISTORY_NOT_READY),
            facade.correct("owner-1", "athlete-1", "workout-1", edit()))
        assertEquals(0L, original().revision)
        assertEquals(NativeHistoryLedgerResult.Rejected(
            NativeHistoryLedgerRefusal.NOT_ENROLLED),
            ledger().open("owner-1", "athlete-1"))
    }

    @Test fun allGatewayWritesUseHistoryManifestAndRecomputeCorrectedLast() {
        issue()
        enroll()
        recorded()
        val finishOutcome = gateway().append(
            "owner-1", "athlete-1", "workout-1", finish())
        assertTrue(finishOutcome.toString(),
            finishOutcome is NativeJournalFileResult.Durable)
        assertEquals("finish-1",
            (finishOutcome as NativeJournalFileResult.Durable).journal.finishedByEventId)
        assertFalse(finishOutcome.replayed)
        assertEquals(2L, (ledger().open("owner-1", "athlete-1")
            as NativeHistoryLedgerResult.Durable).ledger.revision)
        val retry = gateway().append(
            "owner-1", "athlete-1", "workout-1", finish())
            as NativeJournalFileResult.Durable
        assertTrue(retry.replayed)
        val saved = gateway().correct(
            "owner-1", "athlete-1", "workout-1", edit())
        assertTrue(saved.toString(), saved is NativeSavedCorrectionFileResult.Durable)
        assertFalse((saved as NativeSavedCorrectionFileResult.Durable).replayed)
        assertEquals(3L, (ledger().open("owner-1", "athlete-1")
            as NativeHistoryLedgerResult.Durable).ledger.revision)
        val second = gateway().correct(
            "owner-1", "athlete-1", "workout-1", edit())
            as NativeSavedCorrectionFileResult.Durable
        assertTrue(second.replayed)
        val manifest = NativeKotlinHistoryManifestFileAuthority(
            ledger(), { _, _ -> active })
        val historyResult = NativeKotlinGlobalHistoryReadCoordinator(
            journal(), correction(), authority, manifest, fence()
        ).read("owner-1", "athlete-1")
        assertTrue(historyResult.toString(),
            historyResult is NativeKotlinHistoryReadResult.Review)
        val index = (historyResult as NativeKotlinHistoryReadResult.Review).index
        assertEquals(3L, index.manifestRevision)
        assertEquals(3, index.lastByExercise.getValue("bench")
            .workingSets.last().actual?.reps)
        assertEquals("correction-1", index.lastByExercise.getValue("bench")
            .workingSets.last().effectiveEventId)
        assertEquals(3L, original().revision)
        assertFalse(index.canIssueProgression)
    }

    @Test fun pendingFinishIsRecoveredBeforeOpeningAndIsNeverDuplicated() {
        issue()
        enroll()
        recorded()
        val pending = NativeHistoryWriteIntent.Finish("workout-1", finish())
        assertTrue(ledger().prepare("owner-1", "athlete-1", 1, pending)
            is NativeHistoryLedgerResult.Durable)
        val opened = gateway().open("owner-1", "athlete-1", "workout-1")
            as NativeJournalFileResult.Durable
        assertEquals("finish-1", opened.journal.finishedByEventId)
        assertEquals(3L, opened.journal.revision)
        assertEquals(2L, (ledger().open("owner-1", "athlete-1")
            as NativeHistoryLedgerResult.Durable).ledger.revision)
        assertEquals(1, opened.journal.receipts.count {
            it.operation is KotlinJournalOperation.Finish
        })
    }

    @Test fun revocationAndOutOfBandFinishNeverBecomeUntrackedSuccess() {
        issue()
        enroll()
        recorded()
        active = false
        assertEquals(NativeJournalFileResult.Rejected(
            NativeJournalFileRefusal.NOT_AUTHORIZED),
            gateway().open("owner-1", "athlete-1", "workout-1"))
        assertEquals(2L, originalUnderTestRevision())
        active = true
        assertTrue(journal().appendStored(
            "owner-1", "workout-1", finish(), ::allow)
            is NativeJournalFileResult.Durable)
        assertEquals(NativeJournalFileResult.Rejected(
            NativeJournalFileRefusal.HISTORY_NOT_READY),
            gateway().append("owner-1", "athlete-1", "workout-1", finish()))
        assertTrue((ledger().open("owner-1", "athlete-1")
            as NativeHistoryLedgerResult.Durable).ledger.completions.isEmpty())
    }

    private fun originalUnderTestRevision(): Long {
        val durable = journal().openStored("owner-1", "workout-1") {
                _, source -> source.athleteId == "athlete-1"
        } as NativeJournalFileResult.Durable
        return durable.journal.revision
    }

    @Test fun foreignAthleteApprovalIsRejectedBeforeAnyJournalIsIssued() {
        enroll()
        val foreign = source.copy(athleteId = "another-athlete")
        val fake = object : NativeKotlinWorkoutAuthority {
            override fun currentApprovedWorkout(
                ownerKey: String, workoutId: String
            ): NativeKotlinWorkoutApproval? =
                NativeKotlinWorkoutApproval(
                    "owner-1", "another-athlete", foreign, foreign,
                    KotlinWorkoutSelectionGrant(
                        "foreign-grant", "foreign-workout", foreign, 0),
                    ComposerRequest(
                        ComposerSplit.FULL_BODY, TrainingPhase.HYPERTROPHY,
                        emptyList(), emptyList(), emptyList(), 1),
                    emptyList(), "foreign-storage", 1L
                )

            override fun canAccessIssuedWorkout(
                ownerKey: String, source: KotlinTrainingSourceRevision
            ) = ownerKey == "owner-1"
        }
        val facade = NativeKotlinTrackedWorkoutGateway(
            NativeKotlinWorkoutCoordinator(journal(), fake, fence()),
            NativeKotlinHistoryTransactionCoordinator(
                ledger(), journal(), correction(), fake, fence()),
            ledger(), correction(), fake, fence()
        )
        assertEquals(NativeKotlinIssueResult.Rejected(
            NativeKotlinIssueRefusal.NOT_AUTHORIZED),
            facade.issue("owner-1", "athlete-1", "foreign-workout"))
        assertEquals(NativeJournalFileResult.Rejected(
            NativeJournalFileRefusal.NOT_ISSUED),
            journal().openStored("owner-1", "foreign-workout") {
                    _, _ -> true
            })
        assertTrue((ledger().open("owner-1", "athlete-1")
            as NativeHistoryLedgerResult.Durable).ledger.completions.isEmpty())
    }

}
