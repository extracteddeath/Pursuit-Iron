package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.math.BigDecimal
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Proves that manifest-only parity cannot accidentally authorize a missing
 * workout. Genuine completed Composer evidence passes; the live application
 * still keeps the v2 writer and full replay authority.
 */
class NativeKotlinSegmentedHistoryHandoffGateTest {
    @get:Rule val temp = TemporaryFolder()
    private val root get() = temp.root.toPath()
    private val owner = "owner-1"
    private val athlete = "athlete-1"
    private var signedIn: NativeKotlinOwnerIdentity? =
        NativeKotlinOwnerIdentity(owner, athlete)
    private val identity = NativeKotlinCurrentIdentity { signedIn }
    private val fence by lazy { NativeKotlinApprovalRevisionFence(root) }
    private val ledger by lazy { NativeKotlinHistoryManifestFileStore(root) }
    private val composer by lazy { KotlinNativeJournalFileStore(root) }
    private val corrections by lazy {
        KotlinNativeSavedCorrectionFileStore(root, composer)
    }
    private val drafts by lazy {
        NativeCustomCycleDraftFileStore(root, fence, identity)
    }
    private val authoring by lazy {
        NativeKotlinCustomAuthoringSourceFileStore(root, fence, identity)
    }
    private val signoffs by lazy {
        NativeKotlinCustomCycleSignoffFileStore(
            root, fence, identity, drafts, authoring)
    }
    private val preflight by lazy {
        NativeKotlinCustomWorkoutPreflight(
            fence, identity, drafts, authoring, signoffs)
    }
    private val prepared by lazy {
        NativeKotlinCustomWorkoutPreparedStore(
            root, fence, identity, preflight)
    }
    private val custom by lazy {
        NativeKotlinCustomWorkoutJournalFileStore(
            root, fence, identity, prepared, preflight, ledger, composer)
    }
    private val authority = object : NativeKotlinWorkoutAuthority {
        override fun currentApprovedWorkout(
            ownerKey: String, workoutId: String
        ): NativeKotlinWorkoutApproval? = null
        override fun canAccessIssuedWorkout(
            ownerKey: String, source: KotlinTrainingSourceRevision
        ): Boolean = ownerKey == owner && source.athleteId == athlete
    }
    private val shadow by lazy {
        NativeKotlinSegmentedHistoryShadowStore(root, ledger, fence, identity)
    }
    private val replay by lazy {
        NativeKotlinMixedHistoryReadCoordinator(
            ledger, composer, corrections, custom, authority, identity, fence)
    }
    private val gate by lazy {
        NativeKotlinSegmentedHistoryHandoffGate(
            ledger, shadow, replay, fence, identity)
    }
    private fun permit(o: String, a: String) =
        signedIn == NativeKotlinOwnerIdentity(o, a)
    private fun source() = KotlinTrainingSourceRevision(
        athlete, 2, "program-1", 3, "gym-1", 3, "catalog-1", "rules-1")
    private fun grant() = KotlinStoredWorkoutIdentity(
        "stored-1", "workout-1", source(), 1)

    private fun recordRealWorkout(): KotlinHistoryManifestEntry {
        val target = KotlinWorkoutTarget(
            "bench", ComposerRole.PRIMARY,
            ComposerSessionPurpose.HYPERTROPHY, "set-1",
            DraftSetType.WORKING, 0,
            DraftSetLoad.Verified(
                LoadUnit.LB, LoadBasis.SMITH_TOTAL, BigDecimal("200")),
            RepBand(5, 8), 2, 60, 90)
        val snapshot = KotlinStagedWorkout(
            source(), "grant-1", "workout-1", 0, 0,
            ComposerSessionPurpose.HYPERTROPHY, 45, listOf(target))
        assertTrue(composer.issue(owner, snapshot, grant())
            is NativeJournalFileResult.Durable)
        val recorded = composer.appendStored(
            owner, "workout-1", KotlinJournalOperation.RecordSet(
                "set-event-1", 0, "set-1", KotlinJournalSetOutcome.COMPLETED,
                KotlinPerformedSet(BigDecimal("200"), LoadUnit.LB, 8, 2)
            ), ::canRead)
        assertTrue(recorded.toString(), recorded is NativeJournalFileResult.Durable)
        val finished = composer.appendStored(
            owner, "workout-1", KotlinJournalOperation.Finish(
                "finish-1", (recorded as NativeJournalFileResult.Durable).journal.revision
            ), ::canRead)
        assertTrue(finished.toString(), finished is NativeJournalFileResult.Durable)
        val journal = (finished as NativeJournalFileResult.Durable).journal
        return KotlinHistoryManifestEntry(
            1, "workout-1", journal.storedIdentity.storageReceiptId,
            checkNotNull(journal.finishedByEventId), journal.revision, 0)
    }
    private fun canRead(o: String, revision: KotlinTrainingSourceRevision) =
        authority.canAccessIssuedWorkout(o, revision)

    @Test fun exactSegmentParityWithoutWorkoutFilesIsNotHandoffEvidence() {
        val fake = KotlinHistoryManifestEntry(
            1, "workout-1", "stored-1", "finish-1", 2, 0)
        assertTrue(ledger.enrollVerifiedSnapshot(
            owner, athlete, listOf(fake), ::permit)
            is NativeHistoryLedgerResult.Durable)
        assertEquals(NativeSegmentedHistoryResult.Indexed(1, 1, false),
            shadow.sync(owner, athlete))
        assertTrue(shadow.auditParity(owner, athlete)
            is NativeSegmentedHistoryResult.Parity)
        assertEquals(NativeSegmentedHistoryHandoffResult.Rejected(
            NativeSegmentedHistoryHandoffRefusal.WORKOUT_REPLAY_REJECTED,
            replayReason = NativeKotlinMixedHistoryRefusal.UNVERIFIED_COMPOSER),
            gate.review(owner, athlete))
    }

    @Test fun completedComposerJournalPassesFullReviewButCannotCutover() {
        val real = recordRealWorkout()
        assertTrue(ledger.enrollVerifiedSnapshot(
            owner, athlete, listOf(real), ::permit)
            is NativeHistoryLedgerResult.Durable)
        assertTrue(shadow.sync(owner, athlete)
            is NativeSegmentedHistoryResult.Indexed)
        val result = gate.review(owner, athlete)
        assertTrue(result.toString(), result is NativeSegmentedHistoryHandoffResult.Review)
        val review = (result as NativeSegmentedHistoryHandoffResult.Review).review
        assertEquals(1, review.completionCount)
        assertEquals(1, review.verifiedSegmentCount)
        assertEquals(1L, review.manifestRevision)
        assertFalse(review.canTransferWriteAuthority)
        assertFalse(review.canPublishPr)
        assertFalse(review.canIssueProgression)

        val intent = NativeHistoryWriteIntent.Finish(
            "workout-2", KotlinJournalOperation.Finish("finish-2", 0))
        assertTrue(ledger.prepare(owner, athlete, 1, intent)
            is NativeHistoryLedgerResult.Durable)
        assertEquals(NativeSegmentedHistoryHandoffResult.Rejected(
            NativeSegmentedHistoryHandoffRefusal.SHADOW_NOT_VERIFIED,
            shadowReason = NativeSegmentedHistoryRefusal.SOURCE_NOT_READY),
            gate.review(owner, athlete))
    }

    @Test fun corruptedOldShardAndRevokedOwnerBothFailClosed() {
        val real = recordRealWorkout()
        assertTrue(ledger.enrollVerifiedSnapshot(
            owner, athlete, listOf(real), ::permit)
            is NativeHistoryLedgerResult.Durable)
        assertTrue(shadow.sync(owner, athlete)
            is NativeSegmentedHistoryResult.Indexed)
        val shard = Files.list(root).use { stream ->
            stream.filter { it.fileName.toString().endsWith(".seg") }
                .findFirst().orElseThrow()
        }
        val bytes = Files.readAllBytes(shard)
        bytes[bytes.size - 2] = (bytes[bytes.size - 2].toInt() xor 1).toByte()
        Files.write(shard, bytes)
        assertEquals(NativeSegmentedHistoryHandoffResult.Rejected(
            NativeSegmentedHistoryHandoffRefusal.SHADOW_NOT_VERIFIED,
            shadowReason = NativeSegmentedHistoryRefusal.CORRUPT_INDEX),
            gate.review(owner, athlete))
        signedIn = null
        assertEquals(NativeSegmentedHistoryHandoffResult.Rejected(
            NativeSegmentedHistoryHandoffRefusal.NOT_AUTHORIZED),
            gate.review(owner, athlete))
    }
    @Test fun postFinishEditRequiresPendingRecoveryAndNewSegmentIndex() {
        val recorded = recordRealWorkout()
        assertTrue(ledger.enrollVerifiedSnapshot(
            owner, athlete, listOf(recorded), ::permit)
            is NativeHistoryLedgerResult.Durable)
        assertEquals(NativeSegmentedHistoryResult.Indexed(1, 1, false),
            shadow.sync(owner, athlete))
        assertTrue(gate.review(owner, athlete)
            is NativeSegmentedHistoryHandoffResult.Review)

        val edit = KotlinSavedSetCorrection(
            "saved-edit-1", 0, "set-1", "set-event-1",
            KotlinJournalSetOutcome.COMPLETED,
            KotlinPerformedSet(BigDecimal("195"), LoadUnit.LB, 6, 1))
        val pending = NativeHistoryWriteIntent.Correct("workout-1", edit)
        assertTrue(ledger.prepare(owner, athlete, 1, pending)
            is NativeHistoryLedgerResult.Durable)
        val notFinalized = NativeSegmentedHistoryHandoffResult.Rejected(
            NativeSegmentedHistoryHandoffRefusal.SHADOW_NOT_VERIFIED,
            shadowReason = NativeSegmentedHistoryRefusal.SOURCE_NOT_READY)
        assertEquals(notFinalized, gate.review(owner, athlete))

        val edited = corrections.appendStored(
            owner, "workout-1", edit, ::canRead)
        assertTrue(edited.toString(), edited is NativeSavedCorrectionFileResult.Durable)
        // Even if the saved correction is durable, the manifest is still
        // pending. A transaction may be interrupted at exactly this point.
        assertEquals(notFinalized, gate.review(owner, athlete))
        assertTrue(ledger.finalize(
            owner, athlete, pending,
            recorded.copy(savedCorrectionRevision = 1)
        ) is NativeHistoryLedgerResult.Durable)
        assertEquals(NativeSegmentedHistoryHandoffResult.Rejected(
            NativeSegmentedHistoryHandoffRefusal.SHADOW_NOT_VERIFIED,
            shadowReason = NativeSegmentedHistoryRefusal.STALE_SOURCE),
            gate.review(owner, athlete))

        assertEquals(NativeSegmentedHistoryResult.Indexed(2, 1, false),
            shadow.sync(owner, athlete))
        val recovered = gate.review(owner, athlete)
        assertTrue(recovered.toString(),
            recovered is NativeSegmentedHistoryHandoffResult.Review)
        val review = (recovered as NativeSegmentedHistoryHandoffResult.Review).review
        assertEquals(2L, review.manifestRevision)
        assertEquals(1, review.completionCount)
        assertFalse(review.canTransferWriteAuthority)
    }

}
