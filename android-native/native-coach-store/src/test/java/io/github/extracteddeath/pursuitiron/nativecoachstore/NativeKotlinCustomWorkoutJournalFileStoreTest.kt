package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class NativeKotlinCustomWorkoutJournalFileStoreTest {
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


    @Test fun preparationAndConsentAloneNeverIssueUntilHistoryIsEnrolled() {
        initialize();approve();prepareWeek()
        assertEquals(NativeCustomJournalResult.Rejected(
            NativeCustomJournalRefusal.HISTORY_NOT_READY),
            issued.issue("owner-1","workout-2","cycle-1",2,0,input()))
        assertEquals(NativeCustomJournalResult.Rejected(
            NativeCustomJournalRefusal.NOT_ISSUED),
            issued.open("owner-1","workout-2"))
        enroll()
        val result=issueWeek()
        assertFalse(result.replayed)
        assertTrue(result.issued.isIssued)
        assertFalse(result.issued.mayFinishWithoutHistoryTransaction)
        assertEquals(16,result.issued.journal.snapshot.slots.single()
            .sets.single().prescription.reps.minimum)
        assertEquals(0L,result.issued.journal.revision)
        assertEquals(NativeCustomJournalResult.Durable(result.issued,true),
            issued.open("owner-1","workout-2"))
        assertEquals(NativeCustomJournalResult.Durable(result.issued,true),
            issued.issue("owner-1","workout-2","cycle-1",2,0,input()))
    }

    @Test fun actualSetWriteIsDurableAndColdRecoveryDoesNotInventRir() {
        initialize();approve();prepareWeek();enroll();issueWeek()
        val operation=recordSet("event-1",0)
        val saved=issued.record("owner-1","workout-2",operation)
        assertTrue(saved.toString(),saved is NativeCustomJournalResult.Durable)
        val durable=(saved as NativeCustomJournalResult.Durable)
        assertFalse(durable.replayed)
        assertEquals(1L,durable.issued.journal.revision)
        val fresh=NativeKotlinCustomWorkoutJournalFileStore(
            temp.root.toPath(),NativeKotlinApprovalRevisionFence(
                temp.root.toPath()),identity,prepared,preflight,
            history,composerJournal)
        assertEquals(NativeCustomJournalResult.Durable(durable.issued,true),
            fresh.open("owner-1","workout-2"))
        assertEquals(NativeCustomJournalResult.Durable(durable.issued,true),
            fresh.record("owner-1","workout-2",operation))
        assertEquals(1,durable.issued.journal.effectiveVisits.size)
        assertEquals(16,(durable.issued.journal.receipts.single().operation
            as CustomWorkoutJournalOperation.Record).performed?.reps)
        assertEquals(1,(durable.issued.journal.receipts.single().operation
            as CustomWorkoutJournalOperation.Record).performed?.actualRir)
    }

    @Test fun oldJournalRevisionAndReusedWorkoutIdCannotOverwriteTargets() {
        initialize();approve();prepareWeek();enroll();issueWeek()
        assertTrue(issued.record("owner-1","workout-2",
            recordSet("first",0)) is NativeCustomJournalResult.Durable)
        assertEquals(NativeCustomJournalResult.Rejected(
            NativeCustomJournalRefusal.STALE_OR_INVALID_SET,
            CustomWorkoutJournalRefusal.STALE_REVISION),
            issued.record("owner-1","workout-2",recordSet("stale",0)))
        assertEquals(NativeCustomJournalResult.Rejected(
            NativeCustomJournalRefusal.WORKOUT_ID_CONFLICT),
            issued.issue("owner-1","workout-2","cycle-1",1,0,input()))
        assertEquals(1L,(issued.open("owner-1","workout-2")
            as NativeCustomJournalResult.Durable).issued.journal.revision)
    }

    @Test fun revocationAfterIssuancePreservesFrozenWorkoutButBlocksFreshIssue() {
        initialize();approve()
        prepareWeek()
        assertTrue(prepared.prepare("owner-1","workout-1",
            "cycle-1",1,0,input()) is NativeCustomPrepareResult.Prepared)
        enroll();issueWeek()
        assertTrue(consent.revoke("owner-1","cycle-1",1)
            is NativeCustomSignoffWrite.Committed)
        assertEquals(NativeCustomJournalResult.Rejected(
            NativeCustomJournalRefusal.CONSENT_CHANGED),
            issued.issue("owner-1","workout-1","cycle-1",1,0,input()))
        assertTrue(issued.record("owner-1","workout-2",
            recordSet("set-after-change",0)) is NativeCustomJournalResult.Durable)
        assertEquals(16,(issued.open("owner-1","workout-2")
            as NativeCustomJournalResult.Durable).issued.journal
            .snapshot.slots.single().sets.single().prescription.reps.minimum)
    }

    @Test fun identitySwitchAndCorruptJournalNeverExposeOrOverwriteTraining() {
        initialize();approve();prepareWeek();enroll();issueWeek()
        current=NativeKotlinOwnerIdentity("other-owner","athlete-1")
        assertEquals(NativeCustomJournalResult.Rejected(
            NativeCustomJournalRefusal.NOT_AUTHORIZED),
            issued.open("owner-1","workout-2"))
        current=NativeKotlinOwnerIdentity("owner-1","athlete-1")
        val file=java.nio.file.Files.list(temp.root.toPath()).use{files->
            files.filter{it.fileName.toString().startsWith("issued-custom-") &&
                it.fileName.toString().endsWith(".bin")}.findFirst().orElseThrow()
        }
        val bytes=java.nio.file.Files.readAllBytes(file)
        bytes[bytes.lastIndex]=(bytes.last().toInt() xor 1).toByte()
        java.nio.file.Files.write(file,bytes)
        assertEquals(NativeCustomJournalResult.Rejected(
            NativeCustomJournalRefusal.CORRUPT_JOURNAL),
            issued.open("owner-1","workout-2"))
        assertEquals(NativeCustomJournalResult.Rejected(
            NativeCustomJournalRefusal.CORRUPT_JOURNAL),
            issued.record("owner-1","workout-2",
                recordSet("cannot-write",0)))
    }

    private fun inventory()=NativeKotlinCustomOutstandingWorkoutInventory(
        prepared,issued)

    @Test fun censusDistinguishesPreparedIssuedAndManifestCompletedWorkouts() {
        initialize();approve();prepareWeek()
        val planned=inventory().inspect("owner-1","athlete-1",emptySet())
        assertTrue(planned.toString(),
            planned is NativeCustomInventoryReview.Reviewed)
        val before=(planned as NativeCustomInventoryReview.Reviewed)
            .sessions.single()
        assertEquals("workout-2",before.workoutId)
        assertEquals("cycle-1",before.cycleId)
        assertEquals(2,before.absoluteWeek)
        assertFalse(before.issued)
        assertFalse(before.completed)
        enroll();issueWeek()
        val issuedReview=inventory().inspect("owner-1","athlete-1",emptySet())
        assertTrue(issuedReview.toString(),
            issuedReview is NativeCustomInventoryReview.Reviewed)
        val entry=(issuedReview as NativeCustomInventoryReview.Reviewed)
            .outstanding.single()
        assertTrue(entry.issued)
        assertFalse(entry.completed)
        assertEquals(NativeCustomInventoryReview.Rejected(
            NativeCustomInventoryRefusal.UNVERIFIED_COMPLETION),
            inventory().inspect("owner-1","athlete-1",setOf("workout-2")))
        // Reopen both stores cold without relying on a caller's memory.
        val reopened=NativeKotlinCustomOutstandingWorkoutInventory(
            NativeKotlinCustomWorkoutPreparedStore(
                temp.root.toPath(),fence,identity,preflight),
            NativeKotlinCustomWorkoutJournalFileStore(
                temp.root.toPath(),fence,identity,prepared,preflight,
                history,composerJournal))
        assertEquals(issuedReview,
            reopened.inspect("owner-1","athlete-1",emptySet()))
    }

    @Test fun censusCannotIgnoreStrayRenamedOrTamperedPreparedRecords() {
        initialize();approve();prepareWeek()
        val original=java.nio.file.Files.list(temp.root.toPath()).use{files->
            files.filter{it.fileName.toString().startsWith("prepared-custom-")}
                .findFirst().orElseThrow()
        }
        val fake=original.resolveSibling("prepared-custom-"+
            "a".repeat(64)+".bin")
        java.nio.file.Files.copy(original,fake)
        assertEquals(NativeCustomInventoryReview.Rejected(
            NativeCustomInventoryRefusal.CORRUPT_OR_UNRECOGNIZED_FILE),
            inventory().inspect("owner-1","athlete-1",emptySet()))
        java.nio.file.Files.delete(fake)
        val bytes=java.nio.file.Files.readAllBytes(original)
        val corrupted=bytes.clone()
        corrupted[corrupted.lastIndex]=
            (corrupted.last().toInt() xor 1).toByte()
        java.nio.file.Files.write(original,corrupted)
        assertEquals(NativeCustomInventoryReview.Rejected(
            NativeCustomInventoryRefusal.CORRUPT_OR_UNRECOGNIZED_FILE),
            inventory().inspect("owner-1","athlete-1",emptySet()))
    }

    @Test fun censusCannotIgnoreCorruptedIssuedJournal() {
        initialize();approve();prepareWeek();enroll();issueWeek()
        val file=java.nio.file.Files.list(temp.root.toPath()).use{files->
            files.filter{it.fileName.toString().startsWith("issued-custom-")}
                .findFirst().orElseThrow()
        }
        val bytes=java.nio.file.Files.readAllBytes(file)
        bytes[bytes.lastIndex]=(bytes.last().toInt() xor 1).toByte()
        java.nio.file.Files.write(file,bytes)
        assertEquals(NativeCustomInventoryReview.Rejected(
            NativeCustomInventoryRefusal.CORRUPT_OR_UNRECOGNIZED_FILE),
            inventory().inspect("owner-1","athlete-1",emptySet()))
    }

}
