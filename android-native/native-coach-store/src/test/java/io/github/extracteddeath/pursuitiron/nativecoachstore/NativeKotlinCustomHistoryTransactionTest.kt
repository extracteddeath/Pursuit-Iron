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
}
