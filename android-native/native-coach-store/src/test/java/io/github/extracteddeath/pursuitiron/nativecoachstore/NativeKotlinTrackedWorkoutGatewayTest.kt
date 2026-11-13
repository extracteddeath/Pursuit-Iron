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

    @Test fun coldResumedFourSetWorkoutNeverReissuesWeightAfterFatigueOrManualCorrection() {
        // A real disk-backed workout/history journey, not only a pure
        // policy assertion. Three hard sets must not make the next 215 lb
        // issued set become 210 lb after a cold gateway open. A deliberate
        // correction to the ACTUAL is retained separately in history.
        val prescribed = BigDecimal("215")
        val targets = (0..3).map {ordinal->
            KotlinWorkoutTarget(
                "bench",ComposerRole.PRIMARY,
                ComposerSessionPurpose.STRENGTH_SKILL,"hard-set-$ordinal",
                DraftSetType.WORKING,ordinal,
                DraftSetLoad.Verified(
                    LoadUnit.LB,LoadBasis.SMITH_TOTAL,prescribed),
                RepBand(5,8),3,60,120)
        }
        val snapshot=KotlinStagedWorkout(
            source,"grant-four","four-set-safety",0,0,
            ComposerSessionPurpose.STRENGTH_SKILL,55,targets)
        val identity=KotlinStoredWorkoutIdentity(
            "storage-four","four-set-safety",source,1)
        val issued=journal().issue("owner-1",snapshot,identity)
        assertTrue(issued.toString(),issued is NativeJournalFileResult.Durable)
        enroll()

        // New gateway/coordinator/journal reader per call, as after the
        // UI or process recreates state. No Compose draft carries targets.
        fun cold():KotlinWorkoutJournalState {
            val opened=gateway().open("owner-1","athlete-1","four-set-safety")
            assertTrue(opened.toString(),
                opened is NativeJournalFileResult.Durable)
            return (opened as NativeJournalFileResult.Durable).journal
        }
        fun unchanged(j:KotlinWorkoutJournalState) {
            assertEquals(snapshot,j.originalSnapshot)
            assertEquals(snapshot,j.snapshot)
            assertEquals(List(4){prescribed},j.snapshot.targets.map {
                (it.load as DraftSetLoad.Verified).value
            })
        }
        unchanged(cold())
        var saved=cold()
        for (ordinal in 0..3) {
            val logged=gateway().append("owner-1","athlete-1",
                "four-set-safety",KotlinJournalOperation.RecordSet(
                    "hard-event-$ordinal",saved.revision,
                    "hard-set-$ordinal",KotlinJournalSetOutcome.COMPLETED,
                    KotlinPerformedSet(prescribed,LoadUnit.LB,2,0,"A")))
            assertTrue(logged.toString(),
                logged is NativeJournalFileResult.Durable)
            saved=cold()
            unchanged(saved)
            assertEquals(ordinal+1,saved.resolvedSetCount)
            if(ordinal==0) {
                val reviewed=KotlinJournalLiveSetCoachDomain.review(
                    saved,"hard-set-1",
                    VerifiedLoadStock.Totals(LoadUnit.LB,LoadBasis.SMITH_TOTAL,
                        listOf(BigDecimal("205"),BigDecimal("210"),prescribed)),
                    CoachReadiness.NORMAL,false,BigDecimal("5"))
                    as LiveSetCoachResult.Reviewed
                assertEquals(prescribed,reviewed.coaching.issuedTargetUnchanged)
                assertFalse(reviewed.canModifyWorkout)
                assertFalse(reviewed.canIssueWorkout)
                assertFalse(reviewed.coaching.canApplyAutomatically)
            }
            if(ordinal==1) {
                // Correct only the completed first set's actual load.
                // An athlete-entered lower result must not propagate to
                // any pending set or rewrite the signed prescription.
                val corrected=gateway().append("owner-1","athlete-1",
                    "four-set-safety",KotlinJournalOperation.RecordSet(
                        "hard-correction",saved.revision,
                        "hard-set-0",KotlinJournalSetOutcome.COMPLETED,
                        KotlinPerformedSet(BigDecimal("210"),LoadUnit.LB,2,0,"A"),
                        correctsEventId="hard-event-0"))
                assertTrue(corrected.toString(),
                    corrected is NativeJournalFileResult.Durable)
                saved=cold()
                unchanged(saved)
                assertEquals(BigDecimal("210"),
                    (saved.effectiveSets.getValue("hard-set-0").operation
                        as KotlinJournalOperation.RecordSet).performed?.load)
                assertEquals(2,saved.resolvedSetCount)
            }
        }
        assertTrue(saved.canProposeFinish)
        val completed=gateway().append("owner-1","athlete-1",
            "four-set-safety",KotlinJournalOperation.Finish(
                "hard-finish",saved.revision))
        assertTrue(completed.toString(),
            completed is NativeJournalFileResult.Durable)
        val finished=cold()
        unchanged(finished)
        assertEquals("hard-finish",finished.finishedByEventId)
        assertEquals(5,finished.receipts.count {
            it.operation is KotlinJournalOperation.RecordSet
        })
        assertTrue(KotlinWorkoutJournalReplayDomain.verifyStoredState(finished)
            is KotlinJournalReplayResult.Recovered)

        // Cold history must distinguish four issued 215-lb targets from
        // the one explicitly corrected actual of 210 lb.
        val manifest=NativeKotlinHistoryManifestFileAuthority(
            ledger(),{_,_->active})
        val outcome=NativeKotlinGlobalHistoryReadCoordinator(
            journal(),correction(),authority,manifest,fence()
        ).read("owner-1","athlete-1")
        assertTrue(outcome.toString(),
            outcome is NativeKotlinHistoryReadResult.Review)
        val facts=(outcome as NativeKotlinHistoryReadResult.Review)
            .index.lastByExercise.getValue("bench").workingSets
        assertEquals(List(4){prescribed},facts.map{
            (it.issuedLoad as DraftSetLoad.Verified).value
        })
        assertEquals(listOf(BigDecimal("210"),prescribed,prescribed,prescribed),
            facts.map{it.actual?.load})
        assertEquals(setOf("A"),facts.map{it.actual?.machineInstanceId}.toSet())
    }

    @Test fun levelingDraftIsNotAReceiptAndRealLoggedSetsRemainIndependent() {
        // The prior session was an accidental rising pyramid. Leveling is an
        // optional starting point for today's unsaved editors, not a policy
        // that subsequently averages, overwrites or signs journal entries.
        val stock=VerifiedLoadStock.Totals(
            LoadUnit.LB,LoadBasis.SMITH_TOTAL,
            listOf("100","105","110","115","120","125","130").map(::BigDecimal))
        val review=DoubleProgressionLevelingDomain.review(
            listOf("100","120","125","130").map {
                LevelingSetFact(BigDecimal(it),5,3)
            },RepBand(5,8),2,stock
        ) as LevelingReviewResult.Candidate
        assertEquals(BigDecimal("120"),review.proposedStraightSetLoad)
        assertEquals(5,review.repsPerSet)
        assertFalse(review.mayModifyIssuedWorkout)

        val target=BigDecimal("120")
        val targets=(0..3).map {index->
            KotlinWorkoutTarget(
                "bench",ComposerRole.PRIMARY,
                ComposerSessionPurpose.HYPERTROPHY,"level-set-$index",
                DraftSetType.WORKING,index,
                DraftSetLoad.Verified(LoadUnit.LB,LoadBasis.SMITH_TOTAL,target),
                RepBand(5,8),2,60,120)
        }
        val snapshot=KotlinStagedWorkout(source,"grant-level",
            "leveling-session",0,0,ComposerSessionPurpose.HYPERTROPHY,
            50,targets)
        val identity=KotlinStoredWorkoutIdentity(
            "storage-level","leveling-session",source,1)
        val first=journal().issue("owner-1",snapshot,identity)
        assertTrue(first.toString(),first is NativeJournalFileResult.Durable)
        enroll()
        fun cold():KotlinWorkoutJournalState {
            val opened=gateway().open("owner-1","athlete-1","leveling-session")
            assertTrue(opened.toString(),opened is NativeJournalFileResult.Durable)
            return (opened as NativeJournalFileResult.Durable).journal
        }
        var session=cold()
        assertEquals(snapshot,session.originalSnapshot)
        assertTrue(session.effectiveSets.isEmpty()) // Review itself saved nothing.

        // The athlete changes some suggested loads/reps/RIR after tapping
        // Leveling. Those individual actuals are normal verified receipts,
        // NOT forced to the optional 120 x 5 prefill or its assumed goal RIR.
        val observedLoads=listOf("120","125","120","115").map(::BigDecimal)
        val observedReps=listOf(5,5,6,5)
        val observedRir=listOf<Int?>(2,1,3,null)
        for(index in targets.indices) {
            val saved=gateway().append(
                "owner-1","athlete-1","leveling-session",
                KotlinJournalOperation.RecordSet(
                    "level-actual-$index",session.revision,"level-set-$index",
                    KotlinJournalSetOutcome.COMPLETED,
                    KotlinPerformedSet(
                        observedLoads[index],LoadUnit.LB,
                        observedReps[index],observedRir[index])))
            assertTrue(saved.toString(),saved is NativeJournalFileResult.Durable)
            session=cold() // Simulates process restart between real sets.
            assertEquals(snapshot,session.snapshot)
            assertEquals(List(4){target},session.snapshot.targets.map {
                (it.load as DraftSetLoad.Verified).value
            })
            assertEquals(index+1,session.resolvedSetCount)
            for(done in 0..index) {
                val actual=(session.effectiveSets.getValue("level-set-$done")
                    .operation as KotlinJournalOperation.RecordSet).performed
                assertEquals(observedLoads[done],actual?.load)
                assertEquals(observedReps[done],actual?.reps)
                assertEquals(observedRir[done],actual?.actualRir)
            }
        }
        assertTrue(session.canProposeFinish)
        val finish=gateway().append(
            "owner-1","athlete-1","leveling-session",
            KotlinJournalOperation.Finish("finish-leveling",session.revision))
        assertTrue(finish.toString(),finish is NativeJournalFileResult.Durable)
        val finished=cold()
        assertEquals("finish-leveling",finished.finishedByEventId)
        assertEquals(snapshot,finished.originalSnapshot)
        val history=NativeKotlinGlobalHistoryReadCoordinator(
            journal(),correction(),authority,
            NativeKotlinHistoryManifestFileAuthority(ledger(),{_,_->active}),
            fence()
        ).read("owner-1","athlete-1")
        assertTrue(history.toString(),history is NativeKotlinHistoryReadResult.Review)
        val facts=(history as NativeKotlinHistoryReadResult.Review)
            .index.lastByExercise.getValue("bench").workingSets
        assertEquals(observedLoads,facts.map{it.actual?.load})
        assertEquals(observedReps,facts.map{it.actual?.reps})
        assertEquals(observedRir,facts.map{it.actual?.actualRir})
        assertEquals(List(4){target},facts.map{
            (it.issuedLoad as DraftSetLoad.Verified).value
        })
    }

    @Test fun encryptedArchiveStagesAndAtomicallyRestoresARealCorrectedWorkout() {
        issue()
        enroll()
        recorded()
        assertTrue(gateway().append(
            "owner-1","athlete-1","workout-1",finish())
            is NativeJournalFileResult.Durable)
        assertTrue(gateway().correct(
            "owner-1","athlete-1","workout-1",edit())
            is NativeSavedCorrectionFileResult.Durable)
        val bytes=java.io.ByteArrayOutputStream().also {buffer->
            java.io.DataOutputStream(buffer).use {out->
                out.writeInt(0x50494F31)
                out.writeInt(1)
                out.writeUTF("owner-1")
                out.writeUTF("athlete-1")
            }
        }.toByteArray()
        java.nio.file.Files.write(root.resolve("local-athlete.bin"),
            bytes+java.security.MessageDigest.getInstance("SHA-256")
                .digest(bytes))
        val me=NativeKotlinOwnerIdentity("owner-1","athlete-1")
        val manifest=NativeKotlinHistoryManifestFileAuthority(
            ledger(),{_,_->active})
        fun sourceValid()=NativeKotlinGlobalHistoryReadCoordinator(
            journal(),correction(),authority,manifest,fence()
        ).read("owner-1","athlete-1") is NativeKotlinHistoryReadResult.Review
        val key="restore-real-corrected-workout-passphrase".toCharArray()
        val archive=(NativeKotlinEncryptedTrainingSnapshot.capture(
            root,fence(),me,{me},::sourceValid,key)
            as NativeKotlinTrainingSnapshotResult.Ready).encrypted
        val device=temporary.newFolder("new-install").toPath()
        val destination=device.resolve("kotlin-coach-v1")
        var inspected=0
        fun inspectRealStagedFiles(stage:java.nio.file.Path,owner:NativeKotlinOwnerIdentity):Boolean {
            inspected++
            if(owner!=me)return false
            val journal=KotlinNativeJournalFileStore(stage)
            val opened=journal.openStored(
                "owner-1","workout-1"){id,source->
                    id=="owner-1" && source.athleteId=="athlete-1"
                } as? NativeJournalFileResult.Durable ?:return false
            if(opened.journal.finishedByEventId!="finish-1" ||
                opened.journal.revision!=3L)return false
            val history=NativeKotlinHistoryManifestFileStore(stage)
            val stored=history.open("owner-1","athlete-1")
                as? NativeHistoryLedgerResult.Durable ?:return false
            if(stored.ledger.revision!=3L ||
                stored.ledger.pending!=null)return false
            val facts=NativeKotlinGlobalHistoryReadCoordinator(
                journal,KotlinNativeSavedCorrectionFileStore(stage,journal),
                authority,NativeKotlinHistoryManifestFileAuthority(
                    history,{_,_->active}),
                NativeKotlinApprovalRevisionFence(stage)
            ).read("owner-1","athlete-1") as? NativeKotlinHistoryReadResult.Review
                ?:return false
            return facts.index.lastByExercise.getValue("bench")
                .workingSets.last().actual?.reps==3
        }
        val result=NativeKotlinCleanStoreRestore.restoreToAbsentStore(
            destination,archive,key,me,::inspectRealStagedFiles)
        assertTrue(result.toString(),result is NativeKotlinCleanRestoreResult.Committed)
        assertEquals(1,inspected)
        assertTrue(java.nio.file.Files.isDirectory(destination))
        assertFalse(java.nio.file.Files.exists(
            device.resolve(".kotlin-coach-v1.restore-staging")))
        assertFalse(java.nio.file.Files.exists(
            destination.resolve(".restore-in-progress")))
        assertTrue(inspectRealStagedFiles(destination,me))
        assertTrue(sourceValid()) // Original app data remains unchanged.
        val snapshotRevision=ledger().open("owner-1","athlete-1")
            as NativeHistoryLedgerResult.Durable
        assertEquals(3L,snapshotRevision.ledger.revision)
        assertEquals(NativeKotlinCleanRestoreResult.Rejected(
            NativeKotlinCleanRestoreRefusal.EXISTING_TRAINING_DATA),
            NativeKotlinCleanStoreRestore.restoreToAbsentStore(
                destination,archive,key,me,::inspectRealStagedFiles))
        assertEquals(2,inspected)
    }

    @Test fun encryptedSnapshotContainsRealFinishedCorrectedJournalAndLedgerUnchanged() {
        issue()
        enroll()
        recorded()
        assertTrue(gateway().append(
            "owner-1","athlete-1","workout-1",finish())
            is NativeJournalFileResult.Durable)
        assertTrue(gateway().correct(
            "owner-1","athlete-1","workout-1",edit())
            is NativeSavedCorrectionFileResult.Durable)
        // The actual PIO1 binary identity is an essential part of the app's
        // private data; restore cannot just regenerate a random local owner.
        val identity=java.io.ByteArrayOutputStream().also {buffer->
            java.io.DataOutputStream(buffer).use {out->
                out.writeInt(0x50494F31)
                out.writeInt(1)
                out.writeUTF("owner-1")
                out.writeUTF("athlete-1")
            }
        }.toByteArray()
        java.nio.file.Files.write(root.resolve("local-athlete.bin"),
            identity+java.security.MessageDigest.getInstance("SHA-256")
                .digest(identity))
        val expected=NativeKotlinOwnerIdentity("owner-1","athlete-1")
        val manifest=NativeKotlinHistoryManifestFileAuthority(
            ledger(),{_,_->active})
        fun validHistory()=NativeKotlinGlobalHistoryReadCoordinator(
            journal(),correction(),authority,manifest,fence()
        ).read("owner-1","athlete-1") is NativeKotlinHistoryReadResult.Review
        assertTrue(validHistory())
        val secret="several-month-beta-recovery-passphrase".toCharArray()
        val prepared=NativeKotlinEncryptedTrainingSnapshot.capture(
            root,fence(),expected,{if(active)expected else null},
            ::validHistory,secret)
        assertTrue(prepared.toString(),
            prepared is NativeKotlinTrainingSnapshotResult.Ready)
        val archive=(prepared as NativeKotlinTrainingSnapshotResult.Ready)
        assertTrue(archive.fileCount>=3)
        val restored=NativeKotlinEncryptedTrainingSnapshot.inspect(
            archive.encrypted,secret,expected)
        assertTrue(restored.toString(),
            restored is NativeKotlinTrainingSnapshotRead.Verified)
        val copies=(restored as NativeKotlinTrainingSnapshotRead.Verified)
            .files.associateBy{it.relativePath}
        assertArrayEquals(java.nio.file.Files.readAllBytes(
            root.resolve("local-athlete.bin")),
            copies.getValue("local-athlete.bin").contents)
        assertTrue(copies.keys.any{it!="local-athlete.bin"})
        for((relative,entry) in copies) {
            // Compare the exact real binary journal, correction and ledger
            // records, not merely decoded or synthetic set-level DTOs.
            assertArrayEquals(java.nio.file.Files.readAllBytes(
                root.resolve(relative)),entry.contents)
        }
        // Exercise the eventual restore format against REAL readers in an
        // isolated second directory. This is cold replay, not a production
        // overwrite/restore API: the active owner's files are untouched.
        val staging=root.resolveSibling(root.fileName.toString()+"-restore-proof")
        java.nio.file.Files.createDirectory(staging)
        copies.values.forEach {file->
            val target=staging.resolve(file.relativePath)
            java.nio.file.Files.createDirectories(target.parent)
            java.nio.file.Files.write(target,file.contents)
        }
        val coldJournal=KotlinNativeJournalFileStore(staging)
        val replayed=coldJournal.openStored(
            "owner-1","workout-1"){o,from->
                o=="owner-1" && from.athleteId=="athlete-1"
            }
        assertTrue(replayed.toString(),replayed is NativeJournalFileResult.Durable)
        val replay=(replayed as NativeJournalFileResult.Durable).journal
        assertEquals("finish-1",replay.finishedByEventId)
        assertEquals(3L,replay.revision)
        val coldLedger=NativeKotlinHistoryManifestFileStore(staging)
        assertEquals(3L,(coldLedger.open("owner-1","athlete-1")
            as NativeHistoryLedgerResult.Durable).ledger.revision)
        val replayHistory=NativeKotlinGlobalHistoryReadCoordinator(
            coldJournal,
            KotlinNativeSavedCorrectionFileStore(staging,coldJournal),
            authority,
            NativeKotlinHistoryManifestFileAuthority(
                coldLedger,{_,_->active}),
            NativeKotlinApprovalRevisionFence(staging)
        ).read("owner-1","athlete-1")
        assertTrue(replayHistory.toString(),
            replayHistory is NativeKotlinHistoryReadResult.Review)
        assertEquals(3,(replayHistory as NativeKotlinHistoryReadResult.Review)
            .index.lastByExercise.getValue("bench")
            .workingSets.last().actual?.reps)

        // Journal and correction history are still independently valid
        // after backup; no Finish, new session or revision was issued.
        val latest=original()
        assertEquals("finish-1",latest.finishedByEventId)
        assertEquals(3L,latest.revision)
        assertTrue(validHistory())
        assertEquals(3L,(ledger().open("owner-1","athlete-1")
            as NativeHistoryLedgerResult.Durable).ledger.revision)
        active=false
        assertEquals(NativeKotlinTrainingSnapshotResult.Rejected(
            NativeKotlinTrainingSnapshotRefusal.OWNER_MISMATCH),
            NativeKotlinEncryptedTrainingSnapshot.capture(root,fence(),
                expected,{null},::validHistory,secret))
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
