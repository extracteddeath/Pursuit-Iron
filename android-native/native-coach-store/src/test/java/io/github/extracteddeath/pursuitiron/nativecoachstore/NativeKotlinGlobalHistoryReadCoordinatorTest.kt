package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId
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
        issuedSource:KotlinTrainingSourceRevision=source,
        weekday:Int=0,dayIndex:Int=0
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
            weekday, dayIndex, ComposerSessionPurpose.HYPERTROPHY,
            40, targets)
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

    @Test fun savedNearFailureCorrectionRevokesPreviouslyFavorableProgramEvidence() {
        val sessions=listOf(
            completed(1,200,machine="press-A"),
            completed(2,200,machine="press-A"),
            completed(3,200,machine="press-A"))
        val correctedManifest=manifest(*sessions.toTypedArray())
        val pristineManifest=correctedManifest.copy(
            completions=correctedManifest.completions.map{
                it.copy(savedCorrectionRevision=0)
            })
        val before=(service(FakeManifest(pristineManifest))
            .readProgramHistory("owner-1","athlete-1",source,setOf(0))
            as NativeKotlinProgramHistoryReadResult.Review).evidence
        assertEquals(KotlinProgramPerformanceGate.REP_CEILING_FOR_REVIEW_ONLY,
            before.performance.threads.single().recentGate)
        assertEquals(0L,before.ledger.workouts.last().correctionRevision)

        // The old favorable read is a *snapshot*, not a future coaching
        // entitlement. An append-only saved actual must revoke that signal
        // when the SAME private history is reopened under the owner fence.
        val edit=KotlinSavedSetCorrection(
            "saved-near-failure",0,"set-3-1","receipt-3-1",
            KotlinJournalSetOutcome.COMPLETED,
            KotlinPerformedSet(n(200),LoadUnit.LB,6,0,"press-A"))
        val committed=corrections().appendStored(
            "owner-1","workout-3",edit,{_,_->true})
        assertTrue(committed.toString(),
            committed is NativeSavedCorrectionFileResult.Durable)
        val after=(service(FakeManifest(correctedManifest))
            .readProgramHistory("owner-1","athlete-1",source,setOf(0))
            as NativeKotlinProgramHistoryReadResult.Review).evidence
        assertEquals(1L,after.ledger.workouts.last().correctionRevision)
        assertNotEquals(before.ownerHistoryFingerprint,
            after.ownerHistoryFingerprint)
        assertNotEquals(before.ledger.exactReplayDigest,
            after.ledger.exactReplayDigest)
        val changed=after.ledger.workingSets.single{
            it.workoutId=="workout-3" && it.setKey=="set-3-1"
        }
        assertEquals("saved-near-failure",changed.effectiveEventId)
        assertEquals("receipt-3-1",changed.correctsEventId)
        assertEquals(6,changed.performedReps)
        assertEquals(0,changed.reportedRir)
        assertEquals(2,changed.targetRir)
        assertEquals(8,changed.issuedReps.maximum)
        assertEquals("press-A",changed.physicalMachineId)
        assertNotEquals(KotlinProgramPerformanceGate.REP_CEILING_FOR_REVIEW_ONLY,
            after.performance.threads.single().recentGate)
        assertEquals(6,after.performance.threads.single()
            .visits.last().performedReps.last())
        assertEquals(0,after.performance.threads.single()
            .visits.last().reportedRir.last())
        assertFalse(after.performance.canApplyToProgram)
        assertFalse(after.canApplyProgramAdaptation)
    }

    @Test fun savedMachineSwitchCannotReuseAnotherStationsCompletedCapacity() {
        val sessions=listOf(
            completed(1,200,machine="press-A"),
            completed(2,200,machine="press-A"),
            completed(3,200,machine="press-A"))
        val correctedManifest=manifest(*sessions.toTypedArray())
        val initial=correctedManifest.copy(
            completions=correctedManifest.completions.map{
                it.copy(savedCorrectionRevision=0)
            })
        val old=(service(FakeManifest(initial))
            .readProgramHistory("owner-1","athlete-1",source,setOf(0))
            as NativeKotlinProgramHistoryReadResult.Review).evidence
        assertEquals(KotlinProgramPerformanceGate.REP_CEILING_FOR_REVIEW_ONLY,
            old.performance.threads.single().recentGate)

        // Second completed working set was actually on another Smith
        // station. Machine-tagged correction receipts are durable in V2.
        val edit=KotlinSavedSetCorrection(
            "saved-switch",0,"set-3-1","receipt-3-1",
            KotlinJournalSetOutcome.COMPLETED,
            KotlinPerformedSet(n(200),LoadUnit.LB,8,2,"press-B"))
        assertTrue(corrections().appendStored("owner-1","workout-3",
            edit,{_,_->true}) is NativeSavedCorrectionFileResult.Durable)
        val after=(service(FakeManifest(correctedManifest))
            .readProgramHistory("owner-1","athlete-1",source,setOf(0))
            as NativeKotlinProgramHistoryReadResult.Review).evidence
        val switched=after.ledger.workingSets.single{
            it.workoutId=="workout-3" && it.setKey=="set-3-1"
        }
        assertEquals("press-B",switched.physicalMachineId)
        assertEquals("saved-switch",switched.effectiveEventId)
        assertEquals(KotlinHistoryComparisonGuard.MIXED_PHYSICAL_MACHINES,
            switched.comparisonGuard)
        assertTrue(after.performance.threads.none{
            it.recentGate==KotlinProgramPerformanceGate.REP_CEILING_FOR_REVIEW_ONLY
        })
        assertEquals(KotlinProgramPerformanceGate.NEEDS_THREE_MATCHED_VISITS,
            after.performance.threads.single{
                it.key.physicalMachineId=="press-A"
            }.recentGate)
        assertFalse(after.canChangeIssuedTargets)
        assertFalse(after.performance.canIssueWorkout)
    }

    @Test fun correctedUnknownActualRirIsNeverFilledFromTargetRir() {
        val sessions=listOf(
            completed(1,200,machine="press-A"),
            completed(2,200,machine="press-A"),
            completed(3,200,machine="press-A"))
        val current=manifest(*sessions.toTypedArray())
        assertTrue(corrections().appendStored("owner-1","workout-3",
            KotlinSavedSetCorrection(
                "saved-unknown-rir",0,"set-3-1","receipt-3-1",
                KotlinJournalSetOutcome.COMPLETED,
                KotlinPerformedSet(n(200),LoadUnit.LB,8,null,"press-A")),
            {_,_->true}) is NativeSavedCorrectionFileResult.Durable)
        val reviewed=(service(FakeManifest(current))
            .readProgramHistory("owner-1","athlete-1",source,setOf(0))
            as NativeKotlinProgramHistoryReadResult.Review).evidence
        val corrected=reviewed.ledger.workingSets.single{
            it.workoutId=="workout-3" && it.setKey=="set-3-1"
        }
        assertNull(corrected.reportedRir)
        assertEquals(2,corrected.targetRir)
        assertEquals(KotlinProgramPerformanceGate.ACTUAL_EFFORT_UNKNOWN,
            reviewed.performance.threads.single().recentGate)
        assertFalse(reviewed.performance.canApplyToProgram)
    }
    private fun nextFutureRequest():GeneratedAdaptiveBlockRequest {
        fun n(s:String)=BigDecimal(s)
        val gym=GymProfileDraft("gym-1",LoadUnit.LB,
            mapOf("dumbbells" to VerifiedLoadStock.Totals(
                LoadUnit.LB,LoadBasis.DUMBBELL_PER_HAND,
                listOf(n("20"),n("25"),n("30")))),
            emptySet(),emptySet())
        val setup=CoachAthleteSetupDomain.review(AthleteSetupDraft(
            profileRevision="rev-1",athleteProfileId="athlete-1",
            goal=AthleteTrainingGoal.POWERBUILDING,
            experience=AthleteExperience.ADVANCED,
            split=ComposerSplit.FULL_BODY,trainingWeekdays=setOf(0,2,4),
            timezoneId="America/New_York",
            sessionTimeBucket=SessionTimeBucket.FORTY_TO_SIXTY,
            hardSessionLimit=HardSessionLimit.NO_HARD_LIMIT,gym=gym,
            allowBodyweight=false,allowSupersets=false,
            allowTricepsOnLower=false,maxBarbellExercisesPerDay=0,
            focusedMuscles=emptySet(),reducedMuscles=emptySet(),
            excludedExerciseIds=emptySet(),lockedTrainingDays=emptySet(),
            hardTrainingDays=emptySet(),longTrainingDays=emptySet(),
            declaredRestDays=setOf(1,3,5,6)))
        fun move(id:String,muscle:TrainingMuscle,
            pattern:ComposerPattern,role:ComposerRole)=
            CatalogMovement(id,id,"dumbbell",null,pattern,role,
                ComposerFocus.entries.toSet(),"dumbbells",
                LoadBasis.DUMBBELL_PER_HAND,
                listOf(DeclaredMuscleCredit(muscle,MuscleCreditRole.DIRECT,n("1"))),
                CatalogCreditStatus.AUTHOR_REVIEWED_PROVISIONAL,"credit-v1")
        val catalog=listOf(
            move("bench",TrainingMuscle.CHEST,
                ComposerPattern.HORIZONTAL_PRESS,ComposerRole.PRIMARY),
            move("row",TrainingMuscle.LATS,
                ComposerPattern.HORIZONTAL_PULL,ComposerRole.SECONDARY),
            move("squat",TrainingMuscle.QUADS,
                ComposerPattern.SQUAT,ComposerRole.SECONDARY))
        val bounds=TrainingMuscle.entries.map {muscle->
            val active=muscle in setOf(TrainingMuscle.CHEST,
                TrainingMuscle.LATS,TrainingMuscle.QUADS)
            DeclaredMuscleDoseBounds(muscle,
                n(if(active)"2" else "0"),
                n(if(active)"5" else "0"),
                n(if(active)"10" else "0"),
                if(active)1 else 0)
        }
        val policy=AuthoredGoalProgramPolicy(
            "policy-1","rev-1","Reviewed authored phase dose",
            "athlete-1","rev-1",AthleteTrainingGoal.POWERBUILDING,
            AthleteExperience.ADVANCED,ComposerSplit.FULL_BODY,
            TrainingPhase.HYPERTROPHY,3,bounds,bounds,
            mapOf(ComposerSessionPurpose.HYPERTROPHY to 3))
        val program=AthleteProgramGenerationRequest(
            setup,TrainingPhase.HYPERTROPHY,policy,
            catalog,catalog.map {
                CatalogExerciseChoice(it.exerciseId,n("25"),90,120,
                    2,4,3,1,1,5)
            },mapOf(0 to 2100,2 to 2100,4 to 2100),3,1)
        val authored=CustomAuthoredSet(
            DraftSetType.WORKING,
            DraftSetLoad.Verified(LoadUnit.LB,
                LoadBasis.DUMBBELL_PER_HAND,n("25")),
            RepBand(8,12),2,90,120)
        val cycle=CustomTrainingCycle(
            "program-1",3,"athlete-1",AthleteTrainingGoal.POWERBUILDING,
            "Future training cycle",listOf(CustomTrainingBlock(
                "block-1","Next block",TrainingPhase.HYPERTROPHY,
                CustomBlockPurpose.TRAINING,3,
                CustomSplitIdentity.Named(ComposerSplit.FULL_BODY),
                listOf(0,2,4).map {weekday->
                    CustomTrainingDay(weekday,"Day "+weekday,
                        ComposerSessionPurpose.HYPERTROPHY,
                        catalog.map {movement->
                            CustomExerciseSlot("old_"+movement.exerciseId,
                                movement.exerciseId,movement.role,
                                listOf(authored),pinned=false)
                        })
                })))
        val prescriptions=catalog.map {
            ExercisePrescriptionPolicy(it.exerciseId,
                ComposerSessionPurpose.HYPERTROPHY,RepBand(8,12),2,
                90,120,SetLoadScheme.STRAIGHT)
        }
        return GeneratedAdaptiveBlockRequest(
            cycle,"block-1","future-1",program,prescriptions)
    }

    @Test fun ownerFencedReadLinksOnlyExactFutureCellsWithoutIssuingNumbers() {
        val sessions=listOf(
            completed(1,200,machine="press-A"),
            completed(2,200,machine="press-A"),
            completed(3,200,machine="press-A"))
        val full=manifest(*sessions.toTypedArray())
        val accurate=full.copy(completions=full.completions.map {
            it.copy(savedCorrectionRevision=0)
        })
        val reader=service(FakeManifest(accurate))
        val request=nextFutureRequest()
        val output=reader.readGeneratedFutureHistory("owner-1",
            "athlete-1",source,setOf(0),request)
        assertTrue(output.toString(),
            output is NativeKotlinGeneratedFutureHistoryResult.Review)
        val review=(output as NativeKotlinGeneratedFutureHistoryResult.Review)
            .evidence
        assertEquals(GeneratedAdaptiveBlockStatus.READY_FOR_CYCLE_REVIEW,
            review.generated.status)
        assertEquals(2,checkNotNull(review.generated.upstreamNumberTrace)
            .schemaVersion)
        assertEquals(accurate.revision,review.ownerHistory.ownerManifestRevision)
        assertEquals(review.ownerHistory.ledger.exactReplayDigest,
            review.linked.ownerSourceReplayDigest)
        assertTrue(review.linked.cells.isNotEmpty())
        assertTrue("Old Smith/Machine A results cannot justify a future DB plan",
            review.linked.cells.none{
                it.status==KotlinFuturePerformanceCellStatus.REP_CEILING_REVIEW_ONLY
            })
        assertFalse(review.canChangeIssuedWorkout)
        assertFalse(review.canApplyAutomatically)
        assertFalse(review.linked.canIssueWorkout)
        assertEquals(listOf("workout-1","workout-2","workout-3"),
            review.observedWeeks.undatedWorkoutIds)
        assertTrue(review.observedWeeks.weeks.isEmpty())
        assertFalse(review.observedWeeks.canApproveVolumeAdaptation)
        assertEquals(NativeKotlinGeneratedFutureHistoryResult.Rejected(
            NativeKotlinGeneratedFutureHistoryRefusal.INVALID_REQUEST),
            reader.readGeneratedFutureHistory("owner-1","athlete-1",
                source.copy(programRevision=2),setOf(0),request))
    }



    @Test fun ownerDatedFullScheduledWeekIsVisibleButNeverAuthorizesAdaptation() {
        val dated=listOf(
            completed(1,200,machine="press-A",weekday=0,dayIndex=0),
            completed(2,200,machine="press-A",weekday=2,dayIndex=1),
            completed(3,200,machine="press-A",weekday=4,dayIndex=2))
        val base=manifest(*dated.toTypedArray())
        val zone=ZoneId.of("America/New_York")
        val days=listOf("2026-11-16","2026-11-18","2026-11-20")
        val datedManifest=base.copy(completions=base.completions.mapIndexed{
            index,entry->entry.copy(savedCorrectionRevision=0,
                completedAtEpochMillis=LocalDate.parse(days[index])
                    .atTime(12,0).atZone(zone).toInstant().toEpochMilli())
        })
        val review=service(FakeManifest(datedManifest))
            .readGeneratedFutureHistory("owner-1","athlete-1",source,
                setOf(0,1,2),nextFutureRequest())
        assertTrue(review.toString(),
            review is NativeKotlinGeneratedFutureHistoryResult.Review)
        val output=(review as NativeKotlinGeneratedFutureHistoryResult.Review)
            .evidence
        assertEquals(3,output.ownerHistory.completionAtMillisByWorkout.size)
        assertTrue(output.observedWeeks.undatedWorkoutIds.isEmpty())
        assertEquals(KotlinObservedWeekCoverage
            .FULL_SCHEDULE_OBSERVED_FOR_REVIEW_ONLY,
            output.observedWeeks.weeks.single().coverage)
        assertEquals(setOf(0,2,4),
            output.observedWeeks.weeks.single().fullyRecordedWeekdays)
        assertEquals(LocalDate.of(2026,11,16),
            output.observedWeeks.weeks.single().monday)
        assertFalse(output.observedWeeks.weeks.single().canProveProgramAdherence)
        assertFalse(output.observedWeeks.canApproveVolumeAdaptation)
        assertFalse(output.canApplyAutomatically)

        // A changed manifest is a new immutable owner snapshot; a previous
        // observed full week cannot legitimize undated or later-corrected data.
        val undated=datedManifest.copy(
            revision=datedManifest.revision+1,
            completions=datedManifest.completions.mapIndexed{i,entry->
                if(i==1)entry.copy(completedAtEpochMillis=null) else entry
            })
        val downgraded=service(FakeManifest(undated))
            .readGeneratedFutureHistory("owner-1","athlete-1",source,
                setOf(0,1,2),nextFutureRequest())
            as NativeKotlinGeneratedFutureHistoryResult.Review
        assertEquals(KotlinObservedWeekCoverage.CHRONOLOGY_UNVERIFIED,
            downgraded.evidence.observedWeeks.weeks.single().coverage)
        assertEquals(listOf("workout-2"),
            downgraded.evidence.observedWeeks.undatedWorkoutIds)
    }

}
