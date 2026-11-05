package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.math.BigDecimal
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class NativeKotlinIndependentHistoryReceiptVerifierTest {
    @get:Rule val temp = TemporaryFolder()
    private val root get() = temp.root.toPath()
    private val owner = "owner-1"
    private val athlete = "athlete-1"
    private var signedIn: NativeKotlinOwnerIdentity? =
        NativeKotlinOwnerIdentity(owner, athlete)
    private val identity = NativeKotlinCurrentIdentity { signedIn }
    private val fence by lazy { NativeKotlinApprovalRevisionFence(root) }
    private val v2 by lazy { NativeKotlinHistoryManifestFileStore(root) }
    private val composer by lazy { KotlinNativeJournalFileStore(root) }
    private val edits by lazy { KotlinNativeSavedCorrectionFileStore(root, composer) }
    private val drafts by lazy { NativeCustomCycleDraftFileStore(root, fence, identity) }
    private val sourceStore by lazy {
        NativeKotlinCustomAuthoringSourceFileStore(root, fence, identity)
    }
    private val signoffs by lazy {
        NativeKotlinCustomCycleSignoffFileStore(
            root, fence, identity, drafts, sourceStore)
    }
    private val preflight by lazy {
        NativeKotlinCustomWorkoutPreflight(
            fence, identity, drafts, sourceStore, signoffs)
    }
    private val prepared by lazy {
        NativeKotlinCustomWorkoutPreparedStore(
            root, fence, identity, preflight)
    }
    private val custom by lazy {
        NativeKotlinCustomWorkoutJournalFileStore(
            root, fence, identity, prepared, preflight, v2, composer)
    }
    private val customEdits by lazy {
        NativeKotlinCustomSavedCorrectionFileStore(
            root, fence, identity, custom, v2)
    }
    private val authority = object: NativeKotlinWorkoutAuthority {
        override fun currentApprovedWorkout(
            ownerKey: String, workoutId: String
        ): NativeKotlinWorkoutApproval? = null
        override fun canAccessIssuedWorkout(
            ownerKey: String, source: KotlinTrainingSourceRevision
        ): Boolean = ownerKey == owner && source.athleteId == athlete
    }
    private fun verifier() = NativeKotlinIndependentHistoryReceiptVerifier(
        composer, edits, custom, customEdits, authority, fence, identity)
    private fun access(o: String, source: KotlinTrainingSourceRevision) =
        authority.canAccessIssuedWorkout(o, source)

    private fun finishedComposer(): KotlinHistoryManifestEntry {
        val source = KotlinTrainingSourceRevision(
            athlete, 2, "program-1", 3, "gym-1", 3, "catalog-1", "rules-1")
        val target = KotlinWorkoutTarget(
            "bench", ComposerRole.PRIMARY,
            ComposerSessionPurpose.HYPERTROPHY, "set-1",
            DraftSetType.WORKING, 0,
            DraftSetLoad.Verified(
                LoadUnit.LB, LoadBasis.SMITH_TOTAL, BigDecimal("200")),
            RepBand(5, 8), 2, 60, 90)
        val snapshot = KotlinStagedWorkout(
            source, "grant-1", "workout-1", 0, 0,
            ComposerSessionPurpose.HYPERTROPHY, 45, listOf(target))
        assertTrue(composer.issue(owner, snapshot, KotlinStoredWorkoutIdentity(
            "stored-1", "workout-1", source, 1))
            is NativeJournalFileResult.Durable)
        val recorded = composer.appendStored(
            owner, "workout-1", KotlinJournalOperation.RecordSet(
                "set-event-1", 0, "set-1",
                KotlinJournalSetOutcome.COMPLETED,
                KotlinPerformedSet(BigDecimal("200"), LoadUnit.LB, 8, 2)
            ), ::access)
        assertTrue(recorded.toString(), recorded is NativeJournalFileResult.Durable)
        val finished = composer.appendStored(
            owner, "workout-1", KotlinJournalOperation.Finish("finish-1", 1),
            ::access)
        assertTrue(finished.toString(), finished is NativeJournalFileResult.Durable)
        return KotlinHistoryManifestEntry(
            1, "workout-1", "stored-1", "finish-1", 2, 0)
    }
    private fun intent() = NativeHistoryWriteIntent.Finish(
        "workout-1", KotlinJournalOperation.Finish("finish-1", 1))
    private fun corrected() = KotlinSavedSetCorrection(
        "edit-1", 0, "set-1", "set-event-1",
        KotlinJournalSetOutcome.COMPLETED,
        KotlinPerformedSet(BigDecimal("195"), LoadUnit.LB, 6, 1))
    private fun rejected(reason: NativeIndependentReceiptRefusal) =
        NativeIndependentReceiptResult.Rejected(reason)

    @Test fun verifiedComposerFinishNeedsNoV2LedgerAndIssuesNoCoaching() {
        val completion = finishedComposer()
        assertEquals(NativeHistoryLedgerResult.Rejected(
            NativeHistoryLedgerRefusal.NOT_ENROLLED), v2.open(owner, athlete))
        val first = verifier().verify(owner, athlete, intent(), completion)
        assertTrue(first.toString(), first is NativeIndependentReceiptResult.Verified)
        val evidence = (first as NativeIndependentReceiptResult.Verified).evidence
        assertEquals(completion, evidence.entry)
        assertEquals("finish-1", evidence.intentEventId)
        assertFalse(evidence.isCustom)
        assertFalse(evidence.canPublishHistory)
        assertFalse(evidence.canIssueProgression)
        assertFalse(evidence.provesCompleteHistory)
        assertEquals(first, verifier().verify(owner, athlete, intent(), completion))
    }

    @Test fun forgedFinishIdentityRevisionAndWrongOwnerFailClosed() {
        val entry = finishedComposer()
        assertEquals(rejected(NativeIndependentReceiptRefusal.FINISH_MISMATCH),
            verifier().verify(owner, athlete, intent(),
                entry.copy(storedReceiptId = "forged")))
        assertEquals(rejected(NativeIndependentReceiptRefusal.FINISH_MISMATCH),
            verifier().verify(owner, athlete,
                intent().copy(operation = KotlinJournalOperation.Finish(
                    "another-finish", 1)), entry))
        assertEquals(rejected(NativeIndependentReceiptRefusal.FINISH_MISMATCH),
            verifier().verify(owner, athlete, intent(),
                entry.copy(originalJournalRevision = 3)))
        signedIn = null
        assertEquals(rejected(NativeIndependentReceiptRefusal.NOT_AUTHORIZED),
            verifier().verify(owner, athlete, intent(), entry))
    }

    @Test fun savedEditMustMatchLastActualDurableReceipt() {
        val original = finishedComposer()
        val correction = corrected()
        val editIntent = NativeHistoryWriteIntent.Correct("workout-1", correction)
        val current = original.copy(savedCorrectionRevision = 1)
        assertEquals(rejected(NativeIndependentReceiptRefusal.CORRECTION_MISMATCH),
            verifier().verify(owner, athlete, editIntent, current))
        assertTrue(edits.appendStored(owner, "workout-1", correction, ::access)
            is NativeSavedCorrectionFileResult.Durable)
        val verified = verifier().verify(owner, athlete, editIntent, current)
        assertTrue(verified.toString(), verified is NativeIndependentReceiptResult.Verified)
        assertEquals("edit-1",
            (verified as NativeIndependentReceiptResult.Verified)
                .evidence.intentEventId)
        assertEquals(rejected(NativeIndependentReceiptRefusal.CORRECTION_MISMATCH),
            verifier().verify(owner, athlete, editIntent,
                current.copy(savedCorrectionRevision = 2)))
        assertEquals(rejected(NativeIndependentReceiptRefusal.CORRECTION_MISMATCH),
            verifier().verify(owner, athlete,
                NativeHistoryWriteIntent.Correct("workout-1",
                    correction.copy(eventId = "fake-edit")), current))
        assertEquals(rejected(NativeIndependentReceiptRefusal.CORRECTION_MISMATCH),
            verifier().verify(owner, athlete, intent(), original))
    }

    @Test fun corruptSavedCorrectionFileCannotBeBypassedByOriginalFinish() {
        val original = finishedComposer()
        assertTrue(edits.appendStored(owner, "workout-1", corrected(), ::access)
            is NativeSavedCorrectionFileResult.Durable)
        val path = Files.list(root).use { paths ->
            paths.filter { it.fileName.toString().startsWith("corrected-") &&
                it.fileName.toString().endsWith(".bin") }.findFirst().orElse(null)
        }
        // If the store names the file differently, still require the
        // corrected source to be rejected instead of silently disregarded.
        assertNotNull("saved correction file missing", path)
        val bytes = Files.readAllBytes(path)
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
        Files.write(path, bytes)
        assertEquals(rejected(NativeIndependentReceiptRefusal.JOURNAL_NOT_VERIFIED),
            verifier().verify(owner, athlete,
                NativeHistoryWriteIntent.Correct("workout-1", corrected()),
                original.copy(savedCorrectionRevision = 1)))
    }
}
