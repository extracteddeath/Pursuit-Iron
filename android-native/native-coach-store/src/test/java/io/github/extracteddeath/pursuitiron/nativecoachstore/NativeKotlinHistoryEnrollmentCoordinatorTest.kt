package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.math.BigDecimal
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class NativeKotlinHistoryEnrollmentCoordinatorTest {
    @get:Rule val temp = TemporaryFolder()
    private val root get() = temp.root.toPath()
    private val source = KotlinTrainingSourceRevision(
        "athlete-1", 2, "program-1", 3, "gym-1", 3, "catalog-1", "rules-1")
    private var ownerApproved = true
    private var legacyProvenEmpty = true
    private val authority = object : NativeKotlinWorkoutAuthority {
        override fun currentApprovedWorkout(
            ownerKey: String, workoutId: String
        ): NativeKotlinWorkoutApproval? = null
        override fun canAccessIssuedWorkout(
            ownerKey: String, source: KotlinTrainingSourceRevision
        ): Boolean = ownerApproved && ownerKey == "owner-1" &&
            source.athleteId == "athlete-1"
    }
    private fun writer() = KotlinNativeJournalFileStore(root)
    private fun corrections() = KotlinNativeSavedCorrectionFileStore(root, writer())
    private fun manifest() = NativeKotlinHistoryManifestFileStore(root)
    private fun fence() = NativeKotlinApprovalRevisionFence(root)
    private fun migration() = NativeKotlinHistoryEnrollmentCoordinator(
        writer(), corrections(), manifest(), fence(), authority)
    private fun noLegacy(owner: String, athlete: String): Boolean =
        legacyProvenEmpty && owner == "owner-1" && athlete == "athlete-1"
    private fun ownership(owner: String, athlete: String): Boolean =
        ownerApproved && owner == "owner-1" && athlete == "athlete-1"
    private fun enroll(vararg order: String) =
        migration().enrollNativeOnly(
            "owner-1", "athlete-1", order.toList(), ::ownership, ::noLegacy)

    private fun issue(id: Int, finish: Boolean = false): KotlinWorkoutJournalState {
        val workout = "workout-$id"
        val targets = (0..1).map { i -> KotlinWorkoutTarget(
            "bench", ComposerRole.PRIMARY, ComposerSessionPurpose.HYPERTROPHY,
            "set-$id-$i", DraftSetType.WORKING, i,
            DraftSetLoad.Verified(LoadUnit.LB, LoadBasis.SMITH_TOTAL,
                BigDecimal("200")), RepBand(5, 8), 2, 60, 90
        )}
        val snapshot = KotlinStagedWorkout(source, "grant-$id", workout,
            0, 0, ComposerSessionPurpose.HYPERTROPHY, 45, targets)
        val identity = KotlinStoredWorkoutIdentity(
            "storage-$id", workout, source, 1)
        val store = writer()
        assertTrue(store.issue("owner-1", snapshot, identity)
            is NativeJournalFileResult.Durable)
        if (!finish) return (store.openStored(
            "owner-1", workout, ::canRead) as NativeJournalFileResult.Durable).journal
        for (i in 0..1) {
            assertTrue(store.appendStored("owner-1", workout,
                KotlinJournalOperation.RecordSet(
                    "event-$id-$i", i.toLong(), "set-$id-$i",
                    KotlinJournalSetOutcome.COMPLETED,
                    KotlinPerformedSet(BigDecimal("200"), LoadUnit.LB, 8, 2)
                ), ::canRead) is NativeJournalFileResult.Durable)
        }
        return (store.appendStored("owner-1", workout,
            KotlinJournalOperation.Finish("finish-$id", 2), ::canRead)
            as NativeJournalFileResult.Durable).journal
    }

    private fun canRead(owner: String, rev: KotlinTrainingSourceRevision) =
        authority.canAccessIssuedWorkout(owner, rev)

    private fun correct(id: Int) {
        val op = KotlinSavedSetCorrection(
            "edit-$id", 0, "set-$id-1", "event-$id-1",
            KotlinJournalSetOutcome.COMPLETED,
            KotlinPerformedSet(BigDecimal("195.0"), LoadUnit.LB, 4, null)
        )
        assertTrue(corrections().appendStored("owner-1", "workout-$id",
            op, ::canRead) is NativeSavedCorrectionFileResult.Durable)
    }

    private fun refusal(result: NativeHistoryEnrollmentResult) =
        (result as NativeHistoryEnrollmentResult.Rejected).reason

    @Test fun nativeOnlyEmptyEnrollmentRequiresProvenLegacyAbsence() {
        legacyProvenEmpty = false
        assertEquals(NativeHistoryEnrollmentRefusal.LEGACY_HISTORY_UNRESOLVED,
            refusal(enroll()))
        assertEquals(NativeHistoryLedgerResult.Rejected(
            NativeHistoryLedgerRefusal.NOT_ENROLLED),
            manifest().open("owner-1", "athlete-1"))
        legacyProvenEmpty = true
        val accepted = enroll() as NativeHistoryEnrollmentResult.Enrolled
        assertEquals(0, accepted.issuedWorkoutCount)
        assertEquals(0, accepted.recoveredCorrectionCount)
        assertEquals(1L, accepted.manifest.revision)
        assertEquals(emptyList<KotlinHistoryManifestEntry>(),
            accepted.manifest.completions)
        assertTrue((enroll() as NativeHistoryEnrollmentResult.Enrolled).replayed)
    }

    @Test fun allNativeFilesIncludingUnfinishedAndSavedCorrectionsAreVerified() {
        issue(1, finish = true)
        issue(2, finish = false)
        issue(3, finish = true)
        correct(3)
        // The manifest must contain only finished sessions in attested order;
        // the inventory must nevertheless verify workout-2 as well.
        val accepted = enroll("workout-3", "workout-1")
            as NativeHistoryEnrollmentResult.Enrolled
        assertEquals(3, accepted.issuedWorkoutCount)
        assertEquals(1, accepted.recoveredCorrectionCount)
        assertEquals(listOf("workout-3", "workout-1"),
            accepted.manifest.completions.map { it.workoutId })
        assertEquals(listOf(1L, 2L),
            accepted.manifest.completions.map { it.completionOrder })
        assertEquals(listOf(1L, 0L),
            accepted.manifest.completions.map { it.savedCorrectionRevision })
        val read = NativeKotlinGlobalHistoryReadCoordinator(
            writer(), corrections(), authority,
            NativeKotlinHistoryManifestFileAuthority(manifest(), ::ownership),
            fence()).read("owner-1", "athlete-1")
        assertTrue(read.toString(), read is NativeKotlinHistoryReadResult.Review)
        val index = (read as NativeKotlinHistoryReadResult.Review).index
        assertEquals(2, index.completedWorkoutIds.size)
        assertEquals("workout-1", index.lastByExercise.getValue("bench").workoutId)
        // After restart the enrollment is exact idempotent, not reset.
        assertTrue((enroll("workout-3", "workout-1")
            as NativeHistoryEnrollmentResult.Enrolled).replayed)
    }

    @Test fun explicitEmptyInventoryRequirementCannotEnrollExistingComposerWork() {
        val unfinished=issue(1)
        assertEquals(NativeHistoryEnrollmentRefusal.EXISTING_NATIVE_WORKOUT_REQUIRES_MIGRATION,
            refusal(migration().enrollNativeOnly(
                "owner-1","athlete-1",emptyList(),::ownership,::noLegacy,
                requireEmptyNativeInventory=true)))
        assertEquals(NativeHistoryLedgerResult.Rejected(
            NativeHistoryLedgerRefusal.NOT_ENROLLED),
            manifest().open("owner-1","athlete-1"))
        assertEquals(unfinished,(writer().openStored(
            "owner-1","workout-1",::canRead)
            as NativeJournalFileResult.Durable).journal)
        // Normal attested native migration retains its distinct contract:
        // an unfinished journal is inventoried without inventing a Finish.
        val migrated=enroll() as NativeHistoryEnrollmentResult.Enrolled
        assertEquals(1,migrated.issuedWorkoutCount)
        assertTrue(migrated.manifest.completions.isEmpty())
    }

    @Test fun omittedCompletedWorkoutCannotEnrollAsEmptyOrShortenedHistory() {
        issue(1, finish = true)
        issue(2, finish = true)
        assertEquals(NativeHistoryEnrollmentRefusal.INCOMPLETE_OR_DUPLICATE_CHRONOLOGY,
            refusal(enroll()))
        assertEquals(NativeHistoryEnrollmentRefusal.INCOMPLETE_OR_DUPLICATE_CHRONOLOGY,
            refusal(enroll("workout-2")))
        assertEquals(NativeHistoryEnrollmentRefusal.INCOMPLETE_OR_DUPLICATE_CHRONOLOGY,
            refusal(enroll("workout-1", "workout-1")))
        assertEquals(NativeHistoryLedgerResult.Rejected(
            NativeHistoryLedgerRefusal.NOT_ENROLLED),
            manifest().open("owner-1", "athlete-1"))
        assertEquals(2,
            (enroll("workout-1", "workout-2")
                as NativeHistoryEnrollmentResult.Enrolled).manifest.completions.size)
    }

    @Test fun corruptedJournalAndCorrectionCannotDisappearFromInventory() {
        issue(1, finish = true)
        correct(1)
        val editFile = Files.list(root).use { stream ->
            stream.filter { it.fileName.toString().startsWith("correction-") &&
                it.fileName.toString().endsWith(".bin") }.findFirst().orElseThrow()
        }
        val bytes = Files.readAllBytes(editFile)
        bytes[bytes.size - 5] = (bytes[bytes.size - 5].toInt() xor 1).toByte()
        Files.write(editFile, bytes)
        assertEquals(NativeHistoryEnrollmentRefusal.CORRECTION_INVENTORY_INVALID,
            refusal(enroll("workout-1")))
        assertEquals(NativeHistoryLedgerResult.Rejected(
            NativeHistoryLedgerRefusal.NOT_ENROLLED),
            manifest().open("owner-1", "athlete-1"))
    }

    @Test fun orphanedCorrectionFailsClosedInsteadOfGuessingOriginal() {
        issue(1, finish = true)
        correct(1)
        val sourceFile = Files.list(root).use { stream ->
            stream.filter { it.fileName.toString().startsWith("journal-") &&
                it.fileName.toString().endsWith(".bin") }.findFirst().orElseThrow()
        }
        Files.delete(sourceFile)
        assertEquals(NativeHistoryEnrollmentRefusal.ORPHANED_CORRECTION,
            refusal(enroll()))
    }

    @Test fun unapprovedOwnerAndUnmigratedLegacySessionsCannotEnroll() {
        issue(1, finish = true)
        ownerApproved = false
        assertEquals(NativeHistoryEnrollmentRefusal.NOT_AUTHORIZED,
            refusal(enroll("workout-1")))
        ownerApproved = true
        legacyProvenEmpty = false
        assertEquals(NativeHistoryEnrollmentRefusal.LEGACY_HISTORY_UNRESOLVED,
            refusal(enroll("workout-1")))
        assertEquals(NativeHistoryLedgerResult.Rejected(
            NativeHistoryLedgerRefusal.NOT_ENROLLED),
            manifest().open("owner-1", "athlete-1"))
    }

    @Test fun existingEmptyManifestIsNeverReplacedWhenRealHistoryIsFound() {
        // An older isolated test seam could have created an empty manifest
        // before complete file inventory was established.
        val old = manifest().enroll("owner-1", "athlete-1", ::ownership)
        assertTrue(old is NativeHistoryLedgerResult.Durable)
        issue(1, finish = true)
        assertEquals(NativeHistoryEnrollmentRefusal.EXISTING_MANIFEST_CONFLICT,
            refusal(enroll("workout-1")))
        val current = manifest().open("owner-1", "athlete-1")
            as NativeHistoryLedgerResult.Durable
        assertEquals(emptyList<KotlinHistoryManifestEntry>(),
            current.ledger.completions)
    }

    @Test fun unresolvedJournalTemporaryFileBlocksIncompleteEnrollment() {
        issue(1, finish = true)
        Files.write(root.resolve("native-journal-crashed.pending"), byteArrayOf(1))
        assertEquals(NativeHistoryEnrollmentRefusal.JOURNAL_INVENTORY_INVALID,
            refusal(enroll("workout-1")))
    }
}
