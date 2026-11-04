package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class NativeKotlinGlobalHistoryReadCoordinatorTest {
    @get:Rule val temporary = TemporaryFolder()
    private val source = KotlinTrainingSourceRevision(
        "athlete-1", 2, "program-1", 3, "gym-1", 2, "catalog-1", "rules-1")
    private fun n(i: Int) = BigDecimal(i)
    private val root get() = temporary.root.toPath()
    private fun journal() = KotlinNativeJournalFileStore(root)
    private fun corrections() = KotlinNativeSavedCorrectionFileStore(root, journal())
    private fun fence() = NativeKotlinApprovalRevisionFence(root)

    private fun completed(id: Int, load: Int = 200): KotlinWorkoutJournalState {
        val workout = "workout-$id"
        val targets = (0..1).map { ordinal ->
            KotlinWorkoutTarget("bench", ComposerRole.PRIMARY,
                ComposerSessionPurpose.HYPERTROPHY, "set-$id-$ordinal",
                DraftSetType.WORKING, ordinal,
                DraftSetLoad.Verified(LoadUnit.LB, LoadBasis.SMITH_TOTAL, n(200)),
                RepBand(5, 8), 2, 60, 90)
        }
        val snapshot = KotlinStagedWorkout(source, "grant-$id", workout,
            0, 0, ComposerSessionPurpose.HYPERTROPHY, 40, targets)
        val identity = KotlinStoredWorkoutIdentity("stored-$id", workout, source, 1)
        val writer = journal()
        assertTrue(writer.issue("owner-1", snapshot, identity)
            is NativeJournalFileResult.Durable)
        var revision = 0L
        for (target in targets) {
            val recorded = writer.appendStored("owner-1", workout,
                KotlinJournalOperation.RecordSet(
                    "receipt-$id-$revision", revision, target.setKey,
                    KotlinJournalSetOutcome.COMPLETED,
                    KotlinPerformedSet(n(load), LoadUnit.LB, 8, 2)
                ), { _, _ -> true })
            assertTrue(recorded.toString(), recorded is NativeJournalFileResult.Durable)
            revision += 1
        }
        return (writer.appendStored("owner-1", workout,
            KotlinJournalOperation.Finish("finish-$id", revision),
            { _, _ -> true }) as NativeJournalFileResult.Durable).journal
    }

    private class FakeManifest(
        var snapshot: NativeKotlinHistoryManifest?
    ) : NativeKotlinHistoryManifestAuthority {
        var mutateOnFirstRead: (() -> Unit)? = null
        private var reads = 0
        override fun currentHistoryManifest(
            ownerKey: String, athleteId: String
        ): NativeKotlinHistoryManifest? {
            reads++
            val result = snapshot
            if (reads == 1) mutateOnFirstRead?.invoke()
            return result
        }
    }

    private class FakeAccess(var permitted: Boolean = true) :
        NativeKotlinWorkoutAuthority {
        override fun currentApprovedWorkout(
            ownerKey: String, workoutId: String
        ): NativeKotlinWorkoutApproval? = null

        override fun canAccessIssuedWorkout(
            ownerKey: String, source: KotlinTrainingSourceRevision
        ): Boolean = permitted && ownerKey == "owner-1" &&
            source.athleteId == "athlete-1"
    }

    private fun manifest(vararg journals: KotlinWorkoutJournalState,
        revision: Long = 2): NativeKotlinHistoryManifest =
        NativeKotlinHistoryManifest("owner-1", "athlete-1", revision,
            journals.mapIndexed { idx, j ->
                KotlinHistoryManifestEntry(
                    idx.toLong() + 1, j.snapshot.workoutId,
                    j.storedIdentity.storageReceiptId,
                    checkNotNull(j.finishedByEventId), j.revision,
                    if (idx == journals.lastIndex) 1 else 0
                )
            })

    private fun service(
        manifest: FakeManifest, access: FakeAccess = FakeAccess()
    ) = NativeKotlinGlobalHistoryReadCoordinator(
        journal(), corrections(), access, manifest, fence()
    )

    @Test fun reconstructsCorrectedRecordsFromPrivateFilesAfterFreshStoreInstances() {
        val first = completed(1, 200)
        val last = completed(2, 205)
        val edit = KotlinSavedSetCorrection(
            "edit-2", 0, "set-2-1", "receipt-2-1",
            KotlinJournalSetOutcome.COMPLETED,
            KotlinPerformedSet(n(195), LoadUnit.LB, 3, null))
        assertTrue(corrections().appendStored("owner-1", "workout-2",
            edit, { _, _ -> true }) is NativeSavedCorrectionFileResult.Durable)
        val authority = FakeManifest(manifest(first, last))
        val output = service(authority).read("owner-1", "athlete-1")
        assertTrue(output.toString(), output is NativeKotlinHistoryReadResult.Review)
        val view = (output as NativeKotlinHistoryReadResult.Review).index
        assertEquals(listOf("workout-1", "workout-2"),
            view.completedWorkoutIds)
        assertEquals(3, view.lastByExercise.getValue("bench")
            .workingSets.last().actual?.reps)
        assertEquals("edit-2", view.lastByExercise.getValue("bench")
            .workingSets.last().effectiveEventId)
        assertEquals(n(205), view.highestCompletedLoad.values.single().actualLoad)
        assertFalse(view.canPublishPr)
        assertFalse(view.canIssueProgression)
    }

    @Test fun missingCorrectionFileCannotBeMistakenForNoHistoricalEdits() {
        val old = completed(1)
        val latest = completed(2)
        // Manifest proves a correction receipt should exist, but the owner
        // cannot locate the file. Fail closed rather than silently losing it.
        val result = service(FakeManifest(manifest(old, latest)))
            .read("owner-1", "athlete-1")
        assertEquals(NativeKotlinHistoryReadResult.Rejected(
            NativeKotlinHistoryReadRefusal.GLOBAL_HISTORY_REJECTED,
            KotlinGlobalHistoryRefusal.MANIFEST_RECEIPT_MISMATCH), result)
    }

    @Test fun unissuedWorkoutCannotBeFilledWithFabricatedSetHistory() {
        val existing = completed(1)
        val missing = KotlinHistoryManifestEntry(
            2, "missing-2", "stored-missing", "finish-missing", 3, 0)
        val incomplete = manifest(existing).copy(completions =
            listOf(manifest(existing).completions.first(), missing))
        assertEquals(NativeKotlinHistoryReadResult.Rejected(
            NativeKotlinHistoryReadRefusal.MISSING_OR_INVALID_WORKOUT),
            service(FakeManifest(incomplete)).read("owner-1", "athlete-1"))
    }

    @Test fun revokedAthleteAccessAndUnownedManifestCannotOpenHistory() {
        val original = completed(1)
        val valid = manifest(original).copy(completions =
            manifest(original).completions.map { it.copy(savedCorrectionRevision = 0) })
        val access = FakeAccess(permitted = false)
        assertEquals(NativeKotlinHistoryReadResult.Rejected(
            NativeKotlinHistoryReadRefusal.MISSING_OR_INVALID_WORKOUT),
            service(FakeManifest(valid), access).read("owner-1", "athlete-1"))
        assertEquals(NativeKotlinHistoryReadResult.Rejected(
            NativeKotlinHistoryReadRefusal.NOT_AUTHORIZED),
            service(FakeManifest(valid.copy(ownerKey = "owner-2")))
                .read("owner-1", "athlete-1"))
    }

    @Test fun mutatedManifestOnSecondReadFailsEvenInsideFence() {
        val original = completed(1)
        val valid = manifest(original).copy(completions =
            manifest(original).completions.map { it.copy(savedCorrectionRevision = 0) })
        val authority = FakeManifest(valid)
        authority.mutateOnFirstRead = {
            authority.snapshot = valid.copy(revision = valid.revision + 1)
        }
        assertEquals(NativeKotlinHistoryReadResult.Rejected(
            NativeKotlinHistoryReadRefusal.MANIFEST_CHANGED),
            service(authority).read("owner-1", "athlete-1"))
    }

    @Test fun invalidOwnerScopeRejectedBeforeFileOrAuthorityAccess() {
        val authority = FakeManifest(null)
        assertEquals(NativeKotlinHistoryReadResult.Rejected(
            NativeKotlinHistoryReadRefusal.INVALID_REQUEST),
            service(authority).read("../owner", "athlete-1"))
    }
}
