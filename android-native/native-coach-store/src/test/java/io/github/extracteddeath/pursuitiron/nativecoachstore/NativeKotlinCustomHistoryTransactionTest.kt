package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class NativeKotlinCustomHistoryTransactionTest {
    @get:Rule val temp=TemporaryFolder()
    private var current:NativeKotlinOwnerIdentity?=
        NativeKotlinOwnerIdentity("owner-1","athlete-1")
    private val identity=NativeKotlinCurrentIdentity {current}
    private val fence by lazy {
        NativeKotlinApprovalRevisionFence(temp.root.toPath())
    }
    private val drafts by lazy {
        NativeCustomCycleDraftFileStore(temp.root.toPath(),fence,identity)
    }
    private val sources by lazy {
        NativeKotlinCustomAuthoringSourceFileStore(
            temp.root.toPath(),fence,identity)
    }
    private val consent by lazy {
        NativeKotlinCustomCycleSignoffFileStore(
            temp.root.toPath(),fence,identity,drafts,sources)
    }
    private val preflight by lazy{
        NativeKotlinCustomWorkoutPreflight(
            fence,identity,drafts,sources,consent)
    }
    private val prepared by lazy {
        NativeKotlinCustomWorkoutPreparedStore(
            temp.root.toPath(),fence,identity,preflight)
    }
    private val history by lazy {
        NativeKotlinHistoryManifestFileStore(temp.root.toPath())
    }
    private val composerJournal by lazy {
        KotlinNativeJournalFileStore(temp.root.toPath())
    }
    private val issued by lazy {
        NativeKotlinCustomWorkoutJournalFileStore(
            temp.root.toPath(),fence,identity,prepared,preflight,
            history,composerJournal)
    }
    private fun enroll() {
        val ready=history.enroll("owner-1","athlete-1") { o,a ->
            o=="owner-1" && a=="athlete-1" && current==
                NativeKotlinOwnerIdentity(o,a)
        }
        assertTrue(ready.toString(),ready is NativeHistoryLedgerResult.Durable)
    }
    private fun prepareWeek() {
        assertTrue(prepared.prepare("owner-1","workout-2",
            "cycle-1",2,0,input()) is NativeCustomPrepareResult.Prepared)
    }
    private fun issueWeek():NativeCustomJournalResult.Durable {
        val result=issued.issue("owner-1","workout-2",
            "cycle-1",2,0,input())
        assertTrue(result.toString(),result is NativeCustomJournalResult.Durable)
        return result as NativeCustomJournalResult.Durable
    }
    private fun recordSet(event:String,revision:Long,reps:Int=16)=
        CustomWorkoutJournalOperation.Record(
            event,revision,
            CustomWorkoutVisitKey("cycle-1:w2:d0:slot-0:s1",
                CustomWorkoutVisitPart.MAIN),
            KotlinJournalSetOutcome.COMPLETED,
            KotlinPerformedSet(null,null,reps,1)
        )
    private fun n(value:String)=BigDecimal(value)
    private fun setup()=CoachAthleteSetupDomain.review(AthleteSetupDraft(
        profileRevision="rev-1",athleteProfileId="athlete-1",
        goal=AthleteTrainingGoal.HYPERTROPHY,
        experience=AthleteExperience.ADVANCED,
        split=ComposerSplit.FULL_BODY,
        trainingWeekdays=setOf(0,2,4),timezoneId="America/New_York",
        sessionTimeBucket=SessionTimeBucket.FORTY_TO_SIXTY,
        hardSessionLimit=HardSessionLimit.Minutes(60),
        gym=GymProfileDraft("gym-1",LoadUnit.LB,emptyMap(),
            emptySet(),emptySet()),
        allowBodyweight=true,allowSupersets=false,
        allowTricepsOnLower=false,maxBarbellExercisesPerDay=0,
        focusedMuscles=emptySet(),reducedMuscles=emptySet(),
        excludedExerciseIds=emptySet(),lockedTrainingDays=emptySet(),
        hardTrainingDays=emptySet(),longTrainingDays=emptySet(),
        declaredRestDays=setOf(1,3,5,6)
    ))
    private fun source(rev:Long=1)=NativeCustomAuthoringSnapshot(
        "owner-1","athlete-1",rev,setup(),StarterExerciseTranche.movements())
    private fun set(reps:Int)=CustomAuthoredSet(
        DraftSetType.WORKING,DraftSetLoad.ExplicitBodyweight,
        RepBand(reps,reps+2),2,55,75)
    private fun cycle(rev:Long=1)=CustomTrainingCycle(
        "cycle-1",rev,"athlete-1",AthleteTrainingGoal.HYPERTROPHY,
        "Custom cycle",listOf(CustomTrainingBlock(
            "base","Hypertrophy",TrainingPhase.HYPERTROPHY,
            CustomBlockPurpose.TRAINING,3,
            CustomSplitIdentity.Freeform("My split"),
            listOf(0,2,4).map {day ->
                CustomTrainingDay(day,"Core $day",
                    ComposerSessionPurpose.HYPERTROPHY,
                    listOf(CustomExerciseSlot("slot-$day",
                        "bodyweight_crunch",ComposerRole.ACCESSORY,
                        listOf(set(10)),pinned=true,
                        coachNote="My exact prescription")))
            }
        )),mapOf("base" to listOf(
            CustomWeekSlotOverride(2,0,"slot-0",listOf(set(16))))))
    private fun bounds()=TrainingMuscle.entries.map { muscle ->
        DeclaredMuscleDoseBounds(muscle,
            if(muscle==TrainingMuscle.ABS)n("3") else BigDecimal.ZERO,
            if(muscle==TrainingMuscle.ABS)n("4") else BigDecimal.ZERO,
            if(muscle==TrainingMuscle.ABS)n("5") else BigDecimal.ZERO,
            if(muscle==TrainingMuscle.ABS)3 else 0)
    }
    private fun input()=CustomCoachAuditInputs(
        policiesByPhase=mapOf(TrainingPhase.HYPERTROPHY to
            AuthoredGoalProgramPolicy(
                policyId="coach-1",revision="policy-1",
                rationale="Reviewed training volume policy",
                athleteId="athlete-1",athleteRevision="rev-1",
                goal=AthleteTrainingGoal.HYPERTROPHY,
                experience=AthleteExperience.ADVANCED,
                split=ComposerSplit.FULL_BODY,phase=TrainingPhase.HYPERTROPHY,
                trainingDays=3,baselineBounds=bounds(),
                selectedBounds=bounds(),
                minimumPurposeDays=mapOf(ComposerSessionPurpose.HYPERTROPHY to 3)
            )),
        nonSetSecondsByWeekday=mapOf(0 to 200,2 to 200,4 to 200)
    )
    private fun initialize() {
        assertEquals(NativeAuthoringSourceWrite.Committed(1),
            sources.publish("owner-1",0,source()))
        assertEquals(NativeCustomDraftWriteResult.Committed(1),
            drafts.save("owner-1","cycle-1",0,cycle()))
        assertEquals(CustomCoachAuditStatus.READY_FOR_ATHLETE_REVIEW,
            CustomCycleCoachAuditDomain.review(
                cycle(),setup(),source().catalog,input()).status)
    }
    private fun approve() {
        assertTrue(consent.confirm(
            "owner-1","cycle-1",1,1,0,input(),true
        ) is NativeCustomSignoffWrite.Committed)
    }



    private val transactions by lazy {
        NativeKotlinCustomHistoryTransaction(history,issued,fence,identity)
    }
    private fun ready() {
        initialize();approve();prepareWeek();enroll();issueWeek()
    }
    private fun logged() {
        ready()
        val result=issued.record("owner-1","workout-2",
            recordSet("set-1",0))
        assertTrue(result.toString(),result is NativeCustomJournalResult.Durable)
    }
    private fun finish()=CustomWorkoutJournalOperation.Finish(
        "finish-1",1)

    @Test fun oneExactCustomFinishCommitsSharedHistoryOrderAndFactualEvidence() {
        logged()
        val result=transactions.complete("owner-1","athlete-1",
            "workout-2",finish())
        assertTrue(result.toString(),result is NativeCustomFinishResult.Committed)
        val durable=result as NativeCustomFinishResult.Committed
        assertFalse(durable.replayed)
        assertEquals(1L,durable.completion.completionOrder)
        assertEquals("finish-1",durable.completion.finishEventId)
        assertEquals(2L,durable.completion.originalJournalRevision)
        assertTrue(durable.completion.storedReceiptId.startsWith("custom-"))
        assertEquals(1,durable.evidence.visits.size)
        assertEquals(16,durable.evidence.visits.single().actual?.reps)
        assertEquals(1,durable.evidence.visits.single().actual?.actualRir)
        assertFalse(durable.evidence.canPublishGlobalHistory)
        assertFalse(durable.evidence.canApplyProgression)
        assertEquals(null,(history.open("owner-1","athlete-1")
            as NativeHistoryLedgerResult.Durable).ledger.pending)
        val repeated=transactions.complete("owner-1","athlete-1",
            "workout-2",finish())
        assertEquals(durable.copy(replayed=true),repeated)
        assertEquals(1,(history.open("owner-1","athlete-1")
            as NativeHistoryLedgerResult.Durable).ledger.completions.size)
        val denied=transactions.complete("owner-1","athlete-1",
            "workout-2",finish().copy(eventId="different"))
        assertEquals(NativeCustomFinishResult.Rejected(
            NativeCustomFinishRefusal.FINISH_EVENT_MISMATCH),denied)
    }

    @Test fun cannotFinishBeforeEverySetWasExplicitlyLogged() {
        ready()
        assertEquals(NativeCustomFinishResult.Rejected(
            NativeCustomFinishRefusal.FINISH_NOT_READY),
            transactions.complete("owner-1","athlete-1",
                "workout-2",CustomWorkoutJournalOperation.Finish("early",0)))
        val manifest=(history.open("owner-1","athlete-1")
            as NativeHistoryLedgerResult.Durable).ledger
        assertNull(manifest.pending)
        assertTrue(manifest.completions.isEmpty())
    }

    @Test fun redoRecoversAfterIntentPersistedBeforeJournalFinish() {
        logged()
        val base=(history.open("owner-1","athlete-1")
            as NativeHistoryLedgerResult.Durable).ledger
        val intent=NativeHistoryWriteIntent.CustomFinish("workout-2",finish())
        assertTrue(history.prepare("owner-1","athlete-1",base.revision,intent)
            is NativeHistoryLedgerResult.Durable)
        val pending=(history.open("owner-1","athlete-1")
            as NativeHistoryLedgerResult.Durable).ledger.pending
        assertEquals(intent,pending)
        assertEquals(NativeCustomJournalResult.Rejected(
            NativeCustomJournalRefusal.HISTORY_NOT_READY),
            issued.record("owner-1","workout-2",recordSet("stale",1)))
        val reopened=NativeKotlinCustomHistoryTransaction(
            NativeKotlinHistoryManifestFileStore(temp.root.toPath()),
            issued,NativeKotlinApprovalRevisionFence(temp.root.toPath()),identity)
        val recovered=reopened.recover("owner-1","athlete-1")
        assertTrue(recovered.toString(),recovered is NativeCustomFinishResult.Committed)
        assertEquals("finish-1",(issued.open("owner-1","workout-2")
            as NativeCustomJournalResult.Durable).issued.journal.finishedByEventId)
        assertEquals(null,(history.open("owner-1","athlete-1")
            as NativeHistoryLedgerResult.Durable).ledger.pending)
    }

    @Test fun redoRecoversAfterJournalFinishButBeforeManifestFinalization() {
        logged()
        val ledger=(history.open("owner-1","athlete-1")
            as NativeHistoryLedgerResult.Durable).ledger
        val intent=NativeHistoryWriteIntent.CustomFinish("workout-2",finish())
        assertTrue(history.prepare("owner-1","athlete-1",ledger.revision,intent)
            is NativeHistoryLedgerResult.Durable)
        assertTrue(issued.applyPendingFinish("owner-1","workout-2",finish())
            is NativeCustomJournalResult.Durable)
        assertEquals(intent,(history.open("owner-1","athlete-1")
            as NativeHistoryLedgerResult.Durable).ledger.pending)
        val recovered=transactions.recover("owner-1","athlete-1")
        assertTrue(recovered.toString(),recovered is NativeCustomFinishResult.Committed)
        assertTrue(transactions.complete("owner-1","athlete-1",
            "workout-2",finish()) is NativeCustomFinishResult.Committed)
        assertEquals(1,(history.open("owner-1","athlete-1")
            as NativeHistoryLedgerResult.Durable).ledger.completions.size)
    }

    @Test fun oldComposerHistoryReaderCannotSilentlyPublishCustomFacts() {
        logged()
        assertTrue(transactions.complete("owner-1","athlete-1",
            "workout-2",finish()) is NativeCustomFinishResult.Committed)
        val legacy=NativeKotlinHistoryManifestFileAuthority(
            history,{o,a->o=="owner-1" && a=="athlete-1"})
        assertNull(legacy.currentHistoryManifest("owner-1","athlete-1"))
    }

    @Test fun accountChangeAndForeignPendingIntentCannotBeReplayed() {
        logged()
        current=NativeKotlinOwnerIdentity("other-owner","athlete-1")
        assertEquals(NativeCustomFinishResult.Rejected(
            NativeCustomFinishRefusal.NOT_AUTHORIZED),
            transactions.complete("owner-1","athlete-1",
                "workout-2",finish()))
        current=NativeKotlinOwnerIdentity("owner-1","athlete-1")
        assertEquals(NativeCustomFinishResult.Rejected(
            NativeCustomFinishRefusal.FINISH_NOT_READY),
            transactions.recover("owner-1","athlete-1"))
        val manifest=(history.open("owner-1","athlete-1")
            as NativeHistoryLedgerResult.Durable).ledger
        assertTrue(history.prepare("owner-1","athlete-1",manifest.revision,
            NativeHistoryWriteIntent.Finish("composer-other",
                KotlinJournalOperation.Finish("composer-event",0)))
            is NativeHistoryLedgerResult.Durable)
        assertEquals(NativeCustomFinishResult.Rejected(
            NativeCustomFinishRefusal.FOREIGN_PENDING_INTENT),
            transactions.recover("owner-1","athlete-1"))
    }

    @Test fun tamperedFinishedJournalCannotBeMistakenForIndexedCompletion() {
        logged()
        assertTrue(transactions.complete("owner-1","athlete-1",
            "workout-2",finish()) is NativeCustomFinishResult.Committed)
        val file=java.nio.file.Files.list(temp.root.toPath()).use{paths->
            paths.filter{it.fileName.toString().startsWith("issued-custom-") &&
                it.fileName.toString().endsWith(".bin")}.findFirst().orElseThrow()
        }
        val bytes=java.nio.file.Files.readAllBytes(file)
        bytes[bytes.lastIndex]=(bytes.last().toInt() xor 1).toByte()
        java.nio.file.Files.write(file,bytes)
        assertEquals(NativeCustomFinishResult.Rejected(
            NativeCustomFinishRefusal.JOURNAL_UNAVAILABLE),
            transactions.complete("owner-1","athlete-1",
                "workout-2",finish()))
    }

    private val generatedAuthority=object:NativeKotlinWorkoutAuthority {
        override fun currentApprovedWorkout(
            ownerKey:String,workoutId:String
        ):NativeKotlinWorkoutApproval?=null
        override fun canAccessIssuedWorkout(
            ownerKey:String,source:KotlinTrainingSourceRevision
        ):Boolean=ownerKey=="owner-1" && source.athleteId=="athlete-1" &&
            current==NativeKotlinOwnerIdentity("owner-1","athlete-1")
    }

    private fun mixedReader()=NativeKotlinMixedHistoryReadCoordinator(
        history,composerJournal,
        KotlinNativeSavedCorrectionFileStore(temp.root.toPath(),composerJournal),
        issued,generatedAuthority,identity,fence)

    private fun finishGenerated() {
        val src=KotlinTrainingSourceRevision(
            "athlete-1",1,"generated-1",1,"gym-1",1,"catalog-1","policy-1")
        val target=KotlinWorkoutTarget(
            "bench",ComposerRole.PRIMARY,ComposerSessionPurpose.HYPERTROPHY,
            "generated-set",DraftSetType.WORKING,0,
            DraftSetLoad.Verified(LoadUnit.LB,LoadBasis.SMITH_TOTAL,n("200")),
            RepBand(5,8),2,60,90)
        val snapshot=KotlinStagedWorkout(
            src,"grant-generated","generated-1",0,0,
            ComposerSessionPurpose.HYPERTROPHY,40,listOf(target))
        val identity=KotlinStoredWorkoutIdentity(
            "stored-generated","generated-1",src,1)
        assertTrue(composerJournal.issue("owner-1",snapshot,identity)
            is NativeJournalFileResult.Durable)
        assertTrue(composerJournal.appendStored("owner-1","generated-1",
            KotlinJournalOperation.RecordSet(
                "generated-set-event",0,"generated-set",
                KotlinJournalSetOutcome.COMPLETED,
                KotlinPerformedSet(n("200"),LoadUnit.LB,8,2)
            ),{owner,revision->
                generatedAuthority.canAccessIssuedWorkout(owner,revision)
            }) is NativeJournalFileResult.Durable)
        val result=NativeKotlinHistoryTransactionCoordinator(
            history,composerJournal,
            KotlinNativeSavedCorrectionFileStore(temp.root.toPath(),composerJournal),
            generatedAuthority,fence
        ).complete("owner-1","athlete-1","generated-1",
            KotlinJournalOperation.Finish("finish-generated",1))
        assertTrue(result.toString(),result is NativeHistoryTransactionResult.Durable)
    }

    @Test fun mixedTimelineIncludesCustomAndGeneratedInExactFinishOrder() {
        logged()
        assertTrue(transactions.complete(
            "owner-1","athlete-1","workout-2",finish())
            is NativeCustomFinishResult.Committed)
        finishGenerated()
        val result=mixedReader().read("owner-1","athlete-1")
        assertTrue(result.toString(),result is NativeKotlinMixedHistoryReadResult.Review)
        val view=(result as NativeKotlinMixedHistoryReadResult.Review).timeline
        assertEquals(listOf("workout-2","generated-1"),
            view.completions.map{it.manifest.workoutId})
        val custom=view.completions[0] as NativeKotlinMixedHistoryCompletion.Custom
        assertEquals(16,custom.evidence.visits.single().actual?.reps)
        assertEquals(1,custom.evidence.visits.single().actual?.actualRir)
        val generated=view.completions[1] as NativeKotlinMixedHistoryCompletion.Composer
        assertEquals(8,generated.evidence.sets.single().actual?.reps)
        assertEquals("generated-set-event",
            generated.evidence.sets.single().effectiveEventId)
        assertFalse(view.canPublishPr)
        assertFalse(view.canApplyProgression)
        // The old Composer-only Last/PR path stays blocked, not half-correct.
        assertNull(NativeKotlinHistoryManifestFileAuthority(
            history,{_,_->true}
        ).currentHistoryManifest("owner-1","athlete-1"))
    }

    @Test fun mixedHistoryRecomputesGeneratedSavedCorrectionsWithoutInventingCustomPr() {
        logged()
        finishGenerated()
        val corrections=KotlinNativeSavedCorrectionFileStore(
            temp.root.toPath(),composerJournal)
        val result=NativeKotlinHistoryTransactionCoordinator(
            history,composerJournal,corrections,generatedAuthority,fence
        ).correct("owner-1","athlete-1","generated-1",
            KotlinSavedSetCorrection(
                "generated-correction",0,"generated-set",
                "generated-set-event",KotlinJournalSetOutcome.COMPLETED,
                KotlinPerformedSet(n("195"),LoadUnit.LB,4,null)))
        assertTrue(result.toString(),result is NativeHistoryTransactionResult.Durable)
        assertTrue(transactions.complete(
            "owner-1","athlete-1","workout-2",finish())
            is NativeCustomFinishResult.Committed)
        val timeline=(mixedReader().read("owner-1","athlete-1")
            as NativeKotlinMixedHistoryReadResult.Review).timeline
        assertEquals(listOf("generated-1","workout-2"),
            timeline.completions.map{it.manifest.workoutId})
        val generated=timeline.completions.first()
            as NativeKotlinMixedHistoryCompletion.Composer
        assertEquals(4,generated.evidence.sets.single().actual?.reps)
        assertNull(generated.evidence.sets.single().actual?.actualRir)
        assertEquals("generated-correction",
            generated.evidence.sets.single().effectiveEventId)
    }

    @Test fun mixedReaderFailsClosedForPendingRedoRevocationAndCorruption() {
        logged()
        assertTrue(transactions.complete(
            "owner-1","athlete-1","workout-2",finish())
            is NativeCustomFinishResult.Committed)
        assertTrue(mixedReader().read("owner-1","athlete-1")
            is NativeKotlinMixedHistoryReadResult.Review)
        current=NativeKotlinOwnerIdentity("other","athlete-1")
        assertEquals(NativeKotlinMixedHistoryReadResult.Rejected(
            NativeKotlinMixedHistoryRefusal.NOT_AUTHORIZED),
            mixedReader().read("owner-1","athlete-1"))
        current=NativeKotlinOwnerIdentity("owner-1","athlete-1")
        val ledger=(history.open("owner-1","athlete-1")
            as NativeHistoryLedgerResult.Durable).ledger
        val pending=NativeHistoryWriteIntent.Finish(
            "missing",KotlinJournalOperation.Finish("missing-finish",0))
        assertTrue(history.prepare("owner-1","athlete-1",ledger.revision,pending)
            is NativeHistoryLedgerResult.Durable)
        assertEquals(NativeKotlinMixedHistoryReadResult.Rejected(
            NativeKotlinMixedHistoryRefusal.PENDING_TRANSACTION),
            mixedReader().read("owner-1","athlete-1"))
        // A forged/copied or deleted custom file must never yield partial history.
    }

    @Test fun mixedReaderRejectsTamperedCustomJournalInsteadOfDroppingWorkout() {
        logged()
        assertTrue(transactions.complete(
            "owner-1","athlete-1","workout-2",finish())
            is NativeCustomFinishResult.Committed)
        finishGenerated()
        val file=java.nio.file.Files.list(temp.root.toPath()).use{paths->
            paths.filter{it.fileName.toString().startsWith("issued-custom-") &&
                it.fileName.toString().endsWith(".bin")}.findFirst().orElseThrow()
        }
        val bytes=java.nio.file.Files.readAllBytes(file)
        bytes[bytes.lastIndex]=(bytes.last().toInt() xor 1).toByte()
        java.nio.file.Files.write(file,bytes)
        assertEquals(NativeKotlinMixedHistoryReadResult.Rejected(
            NativeKotlinMixedHistoryRefusal.UNVERIFIED_CUSTOM),
            mixedReader().read("owner-1","athlete-1"))
    }

    @Test fun mixedReaderDistinguishesMissingEnrollmentFromCorruptLedger() {
        assertEquals(NativeKotlinMixedHistoryReadResult.Rejected(
            NativeKotlinMixedHistoryRefusal.NOT_ENROLLED),
            mixedReader().read("owner-1","athlete-1"))
        enroll()
        val ledgerFile=java.nio.file.Files.list(temp.root.toPath()).use{paths->
            paths.filter{it.fileName.toString().startsWith("history-") &&
                it.fileName.toString().endsWith(".bin")}.findFirst().orElseThrow()
        }
        val bytes=java.nio.file.Files.readAllBytes(ledgerFile)
        bytes[bytes.lastIndex]=(bytes.last().toInt() xor 1).toByte()
        java.nio.file.Files.write(ledgerFile,bytes)
        assertEquals(NativeKotlinMixedHistoryReadResult.Rejected(
            NativeKotlinMixedHistoryRefusal.INVALID_MANIFEST),
            mixedReader().read("owner-1","athlete-1"))
    }

    @Test fun customManifestReceiptChangesWhenActualPerformanceChanges() {
        ready()
        val issued=issueWeek().issued.journal
        val base=CustomWorkoutJournalDomain.begin(
            issued.workoutId,issued.snapshot,issued.preparedConsentFingerprint)
        fun finished(reps:Int):CustomWorkoutJournalDraft {
            val recorded=CustomWorkoutJournalDomain.reduce(
                base,recordSet("identical-event",0,reps))
                as CustomWorkoutJournalDecision.Proposed
            return (CustomWorkoutJournalDomain.reduce(
                recorded.draft,
                CustomWorkoutJournalOperation.Finish("identical-finish",1))
                as CustomWorkoutJournalDecision.Proposed).draft
        }
        val one=finished(16)
        val two=finished(17)
        assertEquals(one.snapshot,two.snapshot)
        assertEquals(one.finishedByEventId,two.finishedByEventId)
        assertEquals(one.revision,two.revision)
        assertNotEquals(NativeCustomFinishReceipt.compute(
            "owner-1","athlete-1",one),
            NativeCustomFinishReceipt.compute("owner-1","athlete-1",two))
    }
}
