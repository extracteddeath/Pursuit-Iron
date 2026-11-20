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

    private fun completed(
        id:Int,load:Int=200,machine:String?=null,
        issuedSource:KotlinTrainingSourceRevision=source
    ):KotlinWorkoutJournalState {
        val workout = "workout-$id"
        val targets = (0..1).map { ordinal ->
            KotlinWorkoutTarget("bench", ComposerRole.PRIMARY,
                ComposerSessionPurpose.HYPERTROPHY, "set-$id-$ordinal",
                DraftSetType.WORKING, ordinal,
                DraftSetLoad.Verified(LoadUnit.LB, LoadBasis.SMITH_TOTAL, n(200)),
                RepBand(5, 8), 2, 60, 90)
        }
        val snapshot = KotlinStagedWorkout(issuedSource, "grant-$id", workout,
            0, 0, ComposerSessionPurpose.HYPERTROPHY, 40, targets)
        val identity = KotlinStoredWorkoutIdentity(
            "stored-$id", workout, issuedSource, 1)
        val writer = journal()
        assertTrue(writer.issue("owner-1", snapshot, identity)
            is NativeJournalFileResult.Durable)
        var revision = 0L
        for (target in targets) {
            val recorded = writer.appendStored("owner-1", workout,
                KotlinJournalOperation.RecordSet(
                    "receipt-$id-$revision", revision, target.setKey,
                    KotlinJournalSetOutcome.COMPLETED,
                    KotlinPerformedSet(n(load),LoadUnit.LB,8,2,machine)
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

    @Test fun nativeProgramReadVerifiesEntireOwnerManifestBeforeEpochSelection() {
        val old=completed(1,200,machine="press-A")
        val newer=source.copy(programRevision=4)
        val newerWorkout=completed(2,205,machine="press-B",
            issuedSource=newer)
        val existing=manifest(old,newerWorkout).copy(
            completions=manifest(old,newerWorkout).completions.map{
                it.copy(savedCorrectionRevision=0)
            })
        val reader=service(FakeManifest(existing))
        val first=reader.readProgramHistory(
            "owner-1","athlete-1",source,setOf(0))
            as NativeKotlinProgramHistoryReadResult.Review
        val evidence=first.evidence
        assertEquals(listOf("workout-1"),
            evidence.ledger.workouts.map{it.workoutId})
        assertEquals(2,evidence.ledger.workingSets.size)
        assertEquals(setOf("press-A"),
            evidence.ledger.workingSets.map{it.physicalMachineId}.toSet())
        assertEquals(setOf(KotlinHistoryComparisonGuard.COMPARABLE_REVIEW_ONLY),
            evidence.ledger.workingSets.map{it.comparisonGuard}.toSet())
        assertEquals(mapOf("workout-1" to 1L),
            evidence.completionOrderByWorkout)
        assertEquals(2L,evidence.ownerManifestRevision)
        assertEquals(64,evidence.ownerHistoryFingerprint.length)
        assertFalse(evidence.canApplyProgramAdaptation)
        assertFalse(evidence.canChangeIssuedTargets)
        assertEquals(evidence.ledger.exactReplayDigest,
            evidence.performance.sourceReplayDigest)
        assertEquals(KotlinProgramPerformanceGate.NEEDS_THREE_MATCHED_VISITS,
            evidence.performance.threads.single().recentGate)
        assertFalse(evidence.performance.canApplyToProgram)
        assertFalse(evidence.ledger.canAuthorizeProgression)
        assertEquals(evidence, (reader.readProgramHistory(
            "owner-1","athlete-1",source,setOf(0))
            as NativeKotlinProgramHistoryReadResult.Review).evidence)
        val second=(reader.readProgramHistory(
            "owner-1","athlete-1",newer,setOf(0))
            as NativeKotlinProgramHistoryReadResult.Review).evidence
        assertEquals(listOf("workout-2"),
            second.ledger.workouts.map{it.workoutId})
        assertEquals(setOf("press-B"),
            second.ledger.workingSets.map{it.physicalMachineId}.toSet())
        assertEquals(mapOf("workout-2" to 2L),
            second.completionOrderByWorkout)
        assertEquals(evidence.ownerHistoryFingerprint,
            second.ownerHistoryFingerprint)

        // Selecting just the older program epoch must NOT allow an
        // invalid unselected newer completion to disappear silently.
        val corrupt=existing.copy(completions=existing.completions.mapIndexed{
            i,entry->if(i==0)entry else entry.copy(
                storedReceiptId="different-stored-id")
        })
        val rejected=service(FakeManifest(corrupt)).readProgramHistory(
            "owner-1","athlete-1",source,setOf(0))
        assertEquals(NativeKotlinProgramHistoryReadResult.Rejected(
            NativeKotlinProgramHistoryRefusal.OWNER_HISTORY_UNAVAILABLE,
            ownerHistoryReason=NativeKotlinHistoryReadRefusal.GLOBAL_HISTORY_REJECTED),
            rejected)
    }

    @Test fun nativeProgramReadConsumesDurableSavedCorrectionsAndOriginalIssue() {
        val old=completed(1,200,machine="press-A")
        val correction=KotlinSavedSetCorrection(
            "saved-fix",0,"set-1-1","receipt-1-1",
            KotlinJournalSetOutcome.COMPLETED,
            KotlinPerformedSet(n(195),LoadUnit.LB,6,null,"press-A"))
        val written=corrections().appendStored("owner-1","workout-1",
            correction,{_,_->true})
        assertTrue(written.toString(),
            written is NativeSavedCorrectionFileResult.Durable)
        val view=service(FakeManifest(manifest(old))).readProgramHistory(
            "owner-1","athlete-1",source,setOf(0))
            as NativeKotlinProgramHistoryReadResult.Review
        val after=view.evidence.ledger.workingSets.last()
        assertEquals("saved-fix",after.effectiveEventId)
        assertEquals("receipt-1-1",after.correctsEventId)
        assertEquals(n(195),after.performedLoad)
        assertEquals(6,after.performedReps)
        assertNull(after.reportedRir)
        assertEquals(2,after.targetRir)
        assertEquals(n(200),
            (after.issuedLoad as DraftSetLoad.Verified).value)
        assertEquals(KotlinHistoryComparisonGuard.COMPARABLE_REVIEW_ONLY,
            after.comparisonGuard)
        assertEquals(1L,view.evidence.ledger.workouts.single().correctionRevision)
        assertFalse(view.evidence.ledger.canAuthorizeProgression)
    }

    @Test fun nativeProgramReadRefusesUnownedStaleOrOutOfScopeEvidence() {
        val old=completed(1)
        val valid=manifest(old).copy(completions=manifest(old).completions.map{
            it.copy(savedCorrectionRevision=0)
        })
        val reader=service(FakeManifest(valid))
        assertEquals(NativeKotlinProgramHistoryReadResult.Rejected(
            NativeKotlinProgramHistoryRefusal.INVALID_REQUEST),
            reader.readProgramHistory("owner-1","athlete-1",
                source.copy(athleteId="different"),setOf(0)))
        assertEquals(NativeKotlinProgramHistoryReadResult.Rejected(
            NativeKotlinProgramHistoryRefusal.INVALID_PROGRAM_EVIDENCE,
            programEvidenceReason=
                KotlinProgramHistoryReadoutRefusal.INVALID_DAY_OR_SET),
            reader.readProgramHistory("owner-1","athlete-1",source,setOf(2)))
        val revoked=service(FakeManifest(valid),FakeAccess(false))
        assertEquals(NativeKotlinProgramHistoryReadResult.Rejected(
            NativeKotlinProgramHistoryRefusal.OWNER_HISTORY_UNAVAILABLE,
            ownerHistoryReason=NativeKotlinHistoryReadRefusal.MISSING_OR_INVALID_WORKOUT),
            revoked.readProgramHistory("owner-1","athlete-1",source,setOf(0)))
        val changed=FakeManifest(valid)
        changed.mutateOnFirstRead={
            changed.snapshot=valid.copy(revision=valid.revision+1)
        }
        assertEquals(NativeKotlinProgramHistoryReadResult.Rejected(
            NativeKotlinProgramHistoryRefusal.OWNER_HISTORY_UNAVAILABLE,
            ownerHistoryReason=NativeKotlinHistoryReadRefusal.MANIFEST_CHANGED),
            service(changed).readProgramHistory(
                "owner-1","athlete-1",source,setOf(0)))
    }

    @Test fun threeOwnerStoredSameMachineSessionsOnlyOfferRepRangeReview() {
        val sessions=listOf(
            completed(1,200,machine="press-A"),
            completed(2,200,machine="press-A"),
            completed(3,200,machine="press-A"))
        val manifest=manifest(*sessions.toTypedArray()).copy(
            completions=manifest(*sessions.toTypedArray()).completions.map{
                it.copy(savedCorrectionRevision=0)
            })
        val view=service(FakeManifest(manifest)).readProgramHistory(
            "owner-1","athlete-1",source,setOf(0))
            as NativeKotlinProgramHistoryReadResult.Review
        val report=view.evidence.performance
        assertEquals(KotlinProgramPerformanceGate.REP_CEILING_FOR_REVIEW_ONLY,
            report.threads.single().recentGate)
        assertEquals(2,report.schemaVersion)
        assertEquals(0,report.threads.single().key.calendarDay)
        assertEquals(ComposerRole.PRIMARY,report.threads.single().key.role)
        assertEquals(listOf("workout-1","workout-2","workout-3"),
            report.threads.single().evaluatedWorkoutIds)
        assertEquals(2,report.threads.single().visits.last().workingSets)
        assertEquals(2,report.threads.single().visits.last().targetRir.first())
        assertEquals(2,report.threads.single().visits.last().reportedRir.first())
        assertFalse(report.canApplyToProgram)
        assertFalse(view.evidence.canApplyProgramAdaptation)
    }

    @Test fun ownerStoredAmbiguousMachineVisitInvalidatesOlderATrend() {
        val sessions=listOf(
            completed(1,200,machine="press-A"),
            completed(2,200,machine="press-A"),
            completed(3,200,machine="press-A"),
            completed(4,200,machine=null))
        val original=manifest(*sessions.toTypedArray())
        val accurate=original.copy(completions=original.completions.map{
            it.copy(savedCorrectionRevision=0)
        })
        val view=service(FakeManifest(accurate)).readProgramHistory(
            "owner-1","athlete-1",source,setOf(0))
            as NativeKotlinProgramHistoryReadResult.Review
        val a=view.evidence.performance.threads.single{
            it.key.physicalMachineId=="press-A"
        }
        assertEquals(KotlinProgramPerformanceGate.AMBIGUOUS_INTERVENING_MACHINE,
            a.recentGate)
        assertEquals(listOf("workout-1","workout-2","workout-3"),
            a.evaluatedWorkoutIds)
        assertEquals(KotlinProgramPerformanceGate.NEEDS_THREE_MATCHED_VISITS,
            view.evidence.performance.threads.single{
                it.key.physicalMachineId==null
            }.recentGate)
    }
}
