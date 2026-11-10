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
            history,composerJournal,null,sources)
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
    private fun setup(withPullup:Boolean=false)=CoachAthleteSetupDomain.review(AthleteSetupDraft(
        profileRevision="rev-1",athleteProfileId="athlete-1",
        goal=AthleteTrainingGoal.HYPERTROPHY,
        experience=AthleteExperience.ADVANCED,
        split=ComposerSplit.FULL_BODY,
        trainingWeekdays=setOf(0,2,4),timezoneId="America/New_York",
        sessionTimeBucket=SessionTimeBucket.FORTY_TO_SIXTY,
        hardSessionLimit=HardSessionLimit.Minutes(60),
        gym=GymProfileDraft("gym-1",LoadUnit.LB,emptyMap(),
            if(withPullup)setOf("pullup-bar") else emptySet(),emptySet()),
        allowBodyweight=true,allowSupersets=false,
        allowTricepsOnLower=false,maxBarbellExercisesPerDay=0,
        focusedMuscles=emptySet(),reducedMuscles=emptySet(),
        excludedExerciseIds=emptySet(),lockedTrainingDays=emptySet(),
        hardTrainingDays=emptySet(),longTrainingDays=emptySet(),
        declaredRestDays=setOf(1,3,5,6)
    ))
    private fun source(
        rev:Long=1,withPullup:Boolean=false
    )=NativeCustomAuthoringSnapshot(
        "owner-1","athlete-1",rev,setup(withPullup),
        StarterExerciseTranche.movements())
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
    private fun initialize(withPullup:Boolean=false) {
        assertEquals(NativeAuthoringSourceWrite.Committed(1),
            sources.publish("owner-1",0,source(withPullup=withPullup)))
        assertEquals(NativeCustomDraftWriteResult.Committed(1),
            drafts.save("owner-1","cycle-1",0,cycle()))
        assertEquals(CustomCoachAuditStatus.READY_FOR_ATHLETE_REVIEW,
            CustomCycleCoachAuditDomain.review(
                cycle(),setup(withPullup),source(withPullup=withPullup).catalog,input()).status)
    }
    private fun approve() {
        assertTrue(consent.confirm(
            "owner-1","cycle-1",1,1,0,input(),true
        ) is NativeCustomSignoffWrite.Committed)
    }

    /** Fresh native readers, as used after returning from Finish or restarting.
     * No in-memory completion list is passed to the next-session chooser. */
    private fun coldNextSession(inputs:CustomCoachAuditInputs=input()):NativeCustomNextCycleSlot {
        val root=temp.root.toPath()
        val lock=NativeKotlinApprovalRevisionFence(root)
        val savedDrafts=NativeCustomCycleDraftFileStore(root,lock,identity)
        val savedSource=NativeKotlinCustomAuthoringSourceFileStore(root,lock,identity)
        val savedConsent=NativeKotlinCustomCycleSignoffFileStore(
            root,lock,identity,savedDrafts,savedSource)
        val check=NativeKotlinCustomWorkoutPreflight(
            lock,identity,savedDrafts,savedSource,savedConsent)
        val plans=NativeKotlinCustomWorkoutPreparedStore(root,lock,identity,check)
        val ledger=NativeKotlinHistoryManifestFileStore(root)
        val composer=KotlinNativeJournalFileStore(root)
        val journals=NativeKotlinCustomWorkoutJournalFileStore(
            root,lock,identity,plans,check,ledger,composer)
        val authority=object:NativeKotlinWorkoutAuthority {
            override fun currentApprovedWorkout(ownerKey:String,workoutId:String)
                :NativeKotlinWorkoutApproval?=null
            override fun canAccessIssuedWorkout(ownerKey:String,
                source:KotlinTrainingSourceRevision):Boolean=false
        }
        val savedEdits=NativeKotlinCustomSavedCorrectionFileStore(
            root,lock,identity,journals,ledger)
        val timeline=NativeKotlinMixedHistoryReadCoordinator(
            ledger,composer,KotlinNativeSavedCorrectionFileStore(root,composer),
            journals,authority,identity,lock,savedEdits)
        return NativeKotlinCustomNextCycleSessionReview(
            lock,identity,savedDrafts,savedSource,savedConsent,timeline,
            NativeKotlinCustomOutstandingWorkoutInventory(plans,journals),plans
        ).review("owner-1","cycle-1",inputs)
    }

    /** Exactly one extra optional accessory visit in one training day,
     * physically long enough to be omitted for a true 20-minute review,
     * without adding fictitious volume to other authored weeks. */
    private fun shortenedCycle():CustomTrainingCycle {
        val base=cycle()
        return base.copy(blocks=base.blocks.map {block->
            block.copy(days=block.days.map {day->
                if(day.weekday!=0)day
                else day.copy(slots=day.slots+CustomExerciseSlot(
                    "optional-0","bodyweight_crunch",ComposerRole.ACCESSORY,
                    listOf(set(12).copy(restSeconds=1800)),
                    pinned=false,coachNote="Optional on short days"))
            })
        })
    }
    private fun initializeShortenedCycle() {
        val authored=shortenedCycle()
        assertEquals(NativeAuthoringSourceWrite.Committed(1),
            sources.publish("owner-1",0,source()))
        assertEquals(NativeCustomDraftWriteResult.Committed(1),
            drafts.save("owner-1","cycle-1",0,authored))
        val audit=CustomCycleCoachAuditDomain.review(
            authored,setup(),source().catalog,input())
        assertEquals(audit.issues.toString(),
            CustomCoachAuditStatus.READY_FOR_ATHLETE_REVIEW,audit.status)
        approve()
    }

    @Test fun trueShortCustomWorkoutIssuesAndColdReopensWithoutPhantomSets() {
        initializeShortenedCycle();enroll()
        val approved=preflight.inspect("owner-1","cycle-1",2,0,input())
            as NativeCustomPreflightResult.ReviewedPreview
        assertEquals(2,approved.session.slots.size)
        val preview=CustomShorterSessionReviewDomain.inspect(
            approved.session,20)
        assertEquals(CustomShortSessionStatus.READY_FOR_ATHLETE_REVIEW,
            preview.status)
        assertEquals(listOf("cycle-1:w2:d0:optional-0:s1"),
            preview.omittedSetKeys)
        val plan=prepared.prepareShorter(
            "owner-1","short-session-1","cycle-1",2,0,input(),
            20,athleteConfirmed=true)
        assertTrue(plan.toString(),plan is NativeCustomPrepareResult.Prepared)
        val saved=(plan as NativeCustomPrepareResult.Prepared).workout
        assertEquals(20,saved.snapshot.shortSessionLimitMinutes)
        assertEquals(preview.omittedSetKeys,
            saved.snapshot.shortSessionOmittedSetKeys)
        assertEquals(listOf("slot-0"),saved.snapshot.slots.map{it.slotId})
        assertEquals(listOf("cycle-1:w2:d0:slot-0:s1"),
            saved.snapshot.slots.flatMap{it.sets}.map{it.setKey})
        assertEquals(NativeCustomPrepareResult.Prepared(saved,true),
            prepared.prepareShorter("owner-1","short-session-1",
                "cycle-1",2,0,input(),20,true))
        assertEquals(NativeCustomPrepareResult.Rejected(
            NativeCustomPrepareRefusal.DIFFERENT_WORKOUT_ALREADY_PREPARED),
            prepared.prepare("owner-1","short-session-1",
                "cycle-1",2,0,input()))
        val first=issued.issue("owner-1","short-session-1",
            "cycle-1",2,0,input())
        assertTrue(first.toString(),first is NativeCustomJournalResult.Durable)
        val issuedJournal=(first as NativeCustomJournalResult.Durable).issued.journal
        assertEquals(saved.snapshot,issuedJournal.snapshot)
        assertEquals(saved.snapshot,issuedJournal.originalSnapshot)
        assertEquals(1,issuedJournal.unresolvedVisitCount)
        assertEquals(listOf("cycle-1:w2:d0:slot-0:s1"),
            issuedJournal.snapshot.slots.flatMap{it.sets}.map{it.setKey})
        val performed=issued.record("owner-1","short-session-1",
            recordSet("set-event-a",0))
        assertTrue(performed.toString(),performed is NativeCustomJournalResult.Durable)
        val finished=(performed as NativeCustomJournalResult.Durable).issued.journal
        assertEquals(0,finished.unresolvedVisitCount)
        assertTrue(finished.canProposeFinish)
        assertEquals(1,finished.receipts.size)
        assertEquals(0,finished.snapshot.slots.flatMap{it.sets}.count {
            it.setKey in preview.omittedSetKeys
        })
        val cold=NativeKotlinCustomWorkoutJournalFileStore(
            temp.root.toPath(),NativeKotlinApprovalRevisionFence(temp.root.toPath()),
            identity,prepared,preflight,history,composerJournal)
        val restored=cold.open("owner-1","short-session-1")
        assertTrue(restored.toString(),restored is NativeCustomJournalResult.Durable)
        assertEquals(finished,(restored as NativeCustomJournalResult.Durable).issued.journal)
        val replay=issued.issue("owner-1","short-session-1",
            "cycle-1",2,0,input())
        assertTrue(replay.toString(),replay is NativeCustomJournalResult.Durable)
        assertTrue((replay as NativeCustomJournalResult.Durable).replayed)
        assertEquals(finished,replay.issued.journal)
        assertEquals(2,approved.session.slots.size)
    }

    @Test fun shortCustomPreparationRefusesNoConsentUnconfirmedAndStaleRevision() {
        initializeShortenedCycle()
        assertEquals(NativeCustomPrepareResult.Rejected(
            NativeCustomPrepareRefusal.CONFIRMATION_REQUIRED),
            prepared.prepareShorter("owner-1","short-session-2","cycle-1",
                2,0,input(),20,false))
        assertEquals(NativeCustomPrepareResult.Rejected(
            NativeCustomPrepareRefusal.INVALID_SELECTION),
            prepared.prepareShorter("owner-1","short-session-2","cycle-1",
                2,0,input(),32,true))
        val saved=prepared.prepareShorter(
            "owner-1","short-session-2","cycle-1",2,0,input(),20,true)
        assertTrue(saved.toString(),saved is NativeCustomPrepareResult.Prepared)
        assertEquals(NativeAuthoringSourceWrite.Committed(2),
            sources.publish("owner-1",1,source(2)))
        enroll()
        assertEquals(NativeCustomJournalResult.Rejected(
            NativeCustomJournalRefusal.CONSENT_CHANGED),
            issued.issue("owner-1","short-session-2","cycle-1",2,0,input()))
        val after=prepared.open("owner-1","short-session-2")
        assertEquals((saved as NativeCustomPrepareResult.Prepared).workout,
            (after as NativeCustomPreparedRead.Found).workout)
        assertEquals(NativeCustomJournalResult.Rejected(
            NativeCustomJournalRefusal.NOT_ISSUED),
            issued.open("owner-1","short-session-2"))
        current=NativeKotlinOwnerIdentity("another-owner","athlete-1")
        assertEquals(NativeCustomPrepareResult.Rejected(
            NativeCustomPrepareRefusal.NOT_AUTHORIZED),
            prepared.prepareShorter("owner-1","other-1","cycle-1",2,0,
                input(),20,true))
    }

    @Test fun coldReturnToTrainingTraversesWholeCycleWithoutRepeatingFinishedWork() {
        initialize();approve()
        assertTrue(coldNextSession() is NativeCustomNextCycleSlot.Rejected)
        assertTrue(independentEnrollment() is NativeHistoryEnrollmentResult.Enrolled)
        val completedIds=mutableListOf<String>()
        for(week in 1..3)for(day in listOf(0,2,4)) {
            val id="training-w$week-d$day"
            // A fresh approval reader supplies ALL rules after each restart;
            // no remembered UI policy or generated-program seed is present.
            val inputs=(NativeKotlinCustomCycleSignoffFileStore(
                temp.root.toPath(),NativeKotlinApprovalRevisionFence(temp.root.toPath()),
                identity,drafts,sources).recoverReviewedInputs("owner-1","cycle-1")
                as NativeCustomSavedCoachReview.Ready).inputs
            assertEquals(NativeCustomNextCycleSlot.Next(week,day,"Core $day"),
                coldNextSession(inputs))
            assertTrue(prepared.prepare("owner-1",id,"cycle-1",week,day,inputs)
                is NativeCustomPrepareResult.Prepared)
            assertEquals(NativeCustomNextCycleSlot.Prepared(id,week,day,"Core $day"),
                coldNextSession(inputs))
            val started=issued.issue("owner-1",id,"cycle-1",week,day,inputs)
                as NativeCustomJournalResult.Durable
            val original=started.issued.journal.snapshot
            assertEquals(NativeCustomNextCycleSlot.ResumeIssued(
                id,week,day,"Core $day"),coldNextSession(inputs))
            // Week 2's authored exception is an actual issued target; all
            // other days retain the original prescription independently.
            val reps=if(week==2 && day==0)16 else 10
            val set=original.slots.single().sets.single()
            assertEquals(reps,set.prescription.reps.minimum)
            val recorded=issued.record("owner-1",id,
                CustomWorkoutJournalOperation.Record("set-w$week-d$day",0,
                    CustomWorkoutVisitKey(set.setKey,CustomWorkoutVisitPart.MAIN),
                    KotlinJournalSetOutcome.COMPLETED,
                    KotlinPerformedSet(null,null,reps,null)))
                as NativeCustomJournalResult.Durable
            // Even with every set logged, only manifest-backed Finish moves
            // the program forward. A read/restart cannot invent completion.
            assertTrue(recorded.issued.journal.canProposeFinish)
            assertEquals(NativeCustomNextCycleSlot.ResumeIssued(
                id,week,day,"Core $day"),coldNextSession(inputs))
            val finish=CustomWorkoutJournalOperation.Finish(
                "finish-w$week-d$day",recorded.issued.journal.revision)
            val transaction=NativeKotlinCustomHistoryTransaction(
                history,issued,fence,identity)
            assertTrue(transaction.complete("owner-1","athlete-1",id,finish)
                is NativeCustomFinishResult.Committed)
            // Retrying a lost acknowledgment cannot count this workout twice.
            assertTrue(transaction.complete("owner-1","athlete-1",id,finish)
                is NativeCustomFinishResult.Committed)
            completedIds+=id
            val ledger=(history.open("owner-1","athlete-1")
                as NativeHistoryLedgerResult.Durable).ledger
            assertEquals(completedIds,ledger.completions.map{it.workoutId})
            val restored=(issued.open("owner-1",id)
                as NativeCustomJournalResult.Durable).issued.journal
            assertEquals(original,restored.snapshot)
            val evidence=CustomWorkoutHistoryEvidenceDomain.project(restored)
                as CustomHistoryEvidenceResult.Candidate
            assertEquals(reps,evidence.evidence.visits.single().actual?.reps)
            assertNull(evidence.evidence.visits.single().actual?.actualRir)
        }
        assertEquals(NativeCustomNextCycleSlot.CycleComplete,coldNextSession())
        assertEquals(cycle(),(drafts.read("owner-1","cycle-1")
            as NativeCustomDraftReadResult.Found).draft)
    }

    @Test fun returnToTrainingDoesNotBypassConsentOrChangedAthlete() {
        initialize();approve()
        assertTrue(independentEnrollment() is NativeHistoryEnrollmentResult.Enrolled)
        assertEquals(NativeCustomNextCycleSlot.Next(1,0,"Core 0"),coldNextSession())
        current=null
        assertTrue(coldNextSession() is NativeCustomNextCycleSlot.Rejected)
        current=NativeKotlinOwnerIdentity("owner-1","athlete-1")
        assertTrue(consent.revoke("owner-1","cycle-1",1)
            is NativeCustomSignoffWrite.Committed)
        assertTrue(coldNextSession() is NativeCustomNextCycleSlot.Rejected)
        assertTrue((history.open("owner-1","athlete-1")
            as NativeHistoryLedgerResult.Durable).ledger.completions.isEmpty())
    }


    private fun independentEnrollment(
        confirmed:Boolean=true, separate:Boolean=true
    ):NativeHistoryEnrollmentResult {
        val authority=object:NativeKotlinWorkoutAuthority {
            override fun currentApprovedWorkout(
                ownerKey:String,workoutId:String
            ):NativeKotlinWorkoutApproval?=null
            override fun canAccessIssuedWorkout(
                ownerKey:String,source:KotlinTrainingSourceRevision
            ):Boolean=false
        }
        return NativeKotlinHistoryEnrollmentCoordinator(
            composerJournal,KotlinNativeSavedCorrectionFileStore(
                temp.root.toPath(),composerJournal),
            history,fence,authority
        ).enrollSeparateLocalHistory(
            "owner-1","athlete-1",confirmed,
            authenticateOwner={o,a->
                current==NativeKotlinOwnerIdentity(o,a)
            },
            verifyIndependentLocalOwner={o,a->
                separate && current==NativeKotlinOwnerIdentity(o,a)
            },
            customInventory=NativeKotlinCustomOutstandingWorkoutInventory(
                prepared,issued))
    }

    @Test fun freshSeparateNativeProfileRequiresExplicitConsentAndOwnerProof() {
        val denied=independentEnrollment(confirmed=false)
        assertEquals(NativeHistoryEnrollmentResult.Rejected(
            NativeHistoryEnrollmentRefusal.SEPARATE_HISTORY_NOT_CONFIRMED),
            denied)
        assertEquals(NativeHistoryLedgerResult.Rejected(
            NativeHistoryLedgerRefusal.NOT_ENROLLED),
            history.open("owner-1","athlete-1"))
        assertEquals(NativeHistoryEnrollmentResult.Rejected(
            NativeHistoryEnrollmentRefusal.SEPARATE_NATIVE_OWNER_UNVERIFIED),
            independentEnrollment(separate=false))
        val accepted=independentEnrollment() as NativeHistoryEnrollmentResult.Enrolled
        assertEquals(0,accepted.issuedWorkoutCount)
        assertEquals(0,accepted.manifest.completions.size)
        assertEquals(1L,accepted.manifest.revision)
        assertTrue((independentEnrollment()
            as NativeHistoryEnrollmentResult.Enrolled).replayed)
    }

    @Test fun unissuedPreparedCustomWorkoutBlocksNewEmptyIndependentHistory() {
        initialize();approve();prepareWeek()
        assertEquals(NativeHistoryEnrollmentResult.Rejected(
            NativeHistoryEnrollmentRefusal.EXISTING_NATIVE_WORKOUT_REQUIRES_MIGRATION),
            independentEnrollment())
        assertEquals(NativeHistoryLedgerResult.Rejected(
            NativeHistoryLedgerRefusal.NOT_ENROLLED),
            history.open("owner-1","athlete-1"))
    }

    @Test fun separateHistoryUsesExistingWorkoutIssueAndFinishAuthorities() {
        initialize();approve();prepareWeek()
        assertEquals(NativeHistoryEnrollmentResult.Rejected(
            NativeHistoryEnrollmentRefusal.EXISTING_NATIVE_WORKOUT_REQUIRES_MIGRATION),
            independentEnrollment())
        // The real user must enroll BEFORE preparing a workout. Attempting to
        // reset from a partial program cannot force an empty history manifest.
    }

    @Test fun independentNewNativeHistoryUnlocksActualFirstIssueAndFinish() {
        initialize();approve()
        val enrolled=independentEnrollment()
        assertTrue(enrolled.toString(),
            enrolled is NativeHistoryEnrollmentResult.Enrolled)
        // Preparing an actual workout remains a separate consent-bound act.
        prepareWeek()
        assertTrue(issueWeek().issued.journal.canProposeFinish.not())
        val saved=issued.record("owner-1","workout-2",
            recordSet("first-workout-set",0))
        assertTrue(saved.toString(),saved is NativeCustomJournalResult.Durable)
        val recorded=(saved as NativeCustomJournalResult.Durable).issued.journal
        assertTrue(recorded.canProposeFinish)
        val finished=NativeKotlinCustomHistoryTransaction(
            history,issued,fence,identity).complete(
                "owner-1","athlete-1","workout-2",
                CustomWorkoutJournalOperation.Finish(
                    "first-workout-finish",recorded.revision))
        assertTrue(finished.toString(),finished is NativeCustomFinishResult.Committed)
        val completed=(history.open("owner-1","athlete-1")
            as NativeHistoryLedgerResult.Durable).ledger.completions
        assertEquals(listOf("workout-2"),completed.map{it.workoutId})
        assertEquals("first-workout-finish",completed.single().finishEventId)
        val reopened=issued.open("owner-1","workout-2")
            as NativeCustomJournalResult.Durable
        assertEquals("first-workout-finish",
            reopened.issued.journal.finishedByEventId)
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

    @Test fun customSessionNoteIsOwnerFencedAtomicAndReplayable() {
        initialize();approve();prepareWeek();enroll();issueWeek()
        val note=CustomWorkoutJournalOperation.SessionNote(
            "note-1",0,"Strong grip today")
        val durable=issued.saveSessionNote("owner-1","workout-2",note)
        assertTrue(durable.toString(),durable is NativeCustomJournalResult.Durable)
        val saved=(durable as NativeCustomJournalResult.Durable).issued.journal
        assertEquals("Strong grip today",saved.sessionNote)
        assertEquals(1L,saved.revision)
        assertEquals(1,saved.unresolvedVisitCount)
        assertTrue(saved.effectiveVisits.isEmpty())
        val fresh=NativeKotlinCustomWorkoutJournalFileStore(
            temp.root.toPath(),NativeKotlinApprovalRevisionFence(
                temp.root.toPath()),identity,prepared,preflight,
            history,composerJournal)
        assertEquals("Strong grip today",
            (fresh.open("owner-1","workout-2")
                as NativeCustomJournalResult.Durable).issued.journal.sessionNote)
        assertTrue((fresh.saveSessionNote("owner-1","workout-2",note)
            as NativeCustomJournalResult.Durable).replayed)
        val stale=fresh.saveSessionNote("owner-1","workout-2",
            CustomWorkoutJournalOperation.SessionNote("stale",0,"Not saved"))
        assertEquals(CustomWorkoutJournalRefusal.STALE_REVISION,
            (stale as NativeCustomJournalResult.Rejected).journalReason)
        assertEquals("Strong grip today",
            (issued.open("owner-1","workout-2")
                as NativeCustomJournalResult.Durable).issued.journal.sessionNote)
        current=NativeKotlinOwnerIdentity("owner-2","athlete-2")
        val denied=fresh.saveSessionNote("owner-1","workout-2",
            CustomWorkoutJournalOperation.SessionNote("wrong-owner",1,"leak"))
        assertEquals(NativeCustomJournalRefusal.NOT_AUTHORIZED,
            (denied as NativeCustomJournalResult.Rejected).reason)
        current=NativeKotlinOwnerIdentity("owner-1","athlete-1")
        val cleared=fresh.saveSessionNote("owner-1","workout-2",
            CustomWorkoutJournalOperation.SessionNote("clear",1,""))
        assertEquals("",(cleared as NativeCustomJournalResult.Durable)
            .issued.journal.sessionNote)
        val recorded=issued.record("owner-1","workout-2",
            recordSet("set-after-note",2))
        assertTrue(recorded is NativeCustomJournalResult.Durable)
        assertEquals(1,(recorded as NativeCustomJournalResult.Durable)
            .issued.journal.effectiveVisits.size)
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

    private fun resumeReview()=NativeKotlinCustomResumeReview(
        fence,identity,NativeKotlinMixedHistoryReadCoordinator(
            history,composerJournal,
            KotlinNativeSavedCorrectionFileStore(temp.root.toPath(),composerJournal),
            issued,NativeKotlinApprovalFileAuthority(
                temp.root.toPath(),fence,identity),
            identity,fence),inventory(),issued)

    @Test fun resumeDoesNotTreatPreparedOnlyWorkoutAsIssued() {
        initialize();approve();prepareWeek();enroll()
        val review=resumeReview().review("owner-1")
        assertTrue(review.toString(),review is NativeCustomResumeReview.Ready)
        assertTrue((review as NativeCustomResumeReview.Ready).workouts.isEmpty())
        assertFalse(review.mayIssueWorkout)
        assertFalse(review.mayAlterHistory)
    }

    @Test fun issuedCustomWorkoutReopensFromVerifiedOwnerInventoryWithoutIdEntry() {
        initialize();approve();prepareWeek();enroll();issueWeek()
        val before=resumeReview().review("owner-1")
        assertTrue(before.toString(),before is NativeCustomResumeReview.Ready)
        val item=(before as NativeCustomResumeReview.Ready).workouts.single()
        assertEquals("workout-2",item.workoutId)
        assertEquals("cycle-1",item.cycleId)
        assertEquals(2,item.absoluteWeek)
        assertEquals(0,item.weekday)
        assertEquals("Core 0",item.dayLabel)
        assertEquals(0L,item.journalRevision)
        assertEquals(1,item.unresolvedVisitCount)
        val write=issued.record("owner-1","workout-2",recordSet("observed",0))
        assertTrue(write.toString(),write is NativeCustomJournalResult.Durable)
        val cold=resumeReview().review("owner-1")
        assertTrue(cold.toString(),cold is NativeCustomResumeReview.Ready)
        assertEquals(1L,(cold as NativeCustomResumeReview.Ready)
            .workouts.single().journalRevision)
        assertEquals(0,cold.workouts.single().unresolvedVisitCount)
    }

    @Test fun committedCustomFinishDisappearsFromResumableList() {
        initialize();approve();prepareWeek();enroll();issueWeek()
        assertTrue(issued.record("owner-1","workout-2",
            recordSet("observed",0)) is NativeCustomJournalResult.Durable)
        val transaction=NativeKotlinCustomHistoryTransaction(
            history,issued,fence,identity)
        val finish=transaction.complete("owner-1","athlete-1",
            "workout-2",CustomWorkoutJournalOperation.Finish(
                "completed-1",1L))
        assertTrue(finish.toString(),finish is NativeCustomFinishResult.Committed)
        val review=resumeReview().review("owner-1")
        assertTrue(review.toString(),review is NativeCustomResumeReview.Ready)
        assertTrue((review as NativeCustomResumeReview.Ready).workouts.isEmpty())
    }

    @Test fun resumeFailsClosedOnOwnerSwitchAndCorruptedIssuedFile() {
        initialize();approve();prepareWeek();enroll();issueWeek()
        current=NativeKotlinOwnerIdentity("other-owner","athlete-1")
        assertEquals(NativeCustomResumeReview.Rejected(
            NativeCustomResumeRefusal.NOT_AUTHORIZED),
            resumeReview().review("owner-1"))
        current=NativeKotlinOwnerIdentity("owner-1","athlete-1")
        val file=java.nio.file.Files.list(temp.root.toPath()).use{files->
            files.filter{it.fileName.toString().startsWith("issued-custom-")}
                .findFirst().orElseThrow()
        }
        val bytes=java.nio.file.Files.readAllBytes(file)
        bytes[bytes.lastIndex]=(bytes.last().toInt() xor 1).toByte()
        java.nio.file.Files.write(file,bytes)
        assertEquals(NativeCustomResumeReview.Rejected(
            NativeCustomResumeRefusal.WORKOUT_INVENTORY_UNVERIFIED),
            resumeReview().review("owner-1"))
    }


    @Test fun durableLiveSwapSurvivesColdReopenAndVerifiedFinish() {
        initialize(withPullup=true);approve();prepareWeek();enroll()
        val initial=issueWeek().issued.journal
        assertEquals("bodyweight_crunch",
            initial.originalSnapshot.slots.single().exerciseId)
        val chosen=listOf(set(8))
        val swapped=issued.swap("owner-1","workout-2",
            "swap-live-1",0L,"slot-0","pullup",chosen,"Fresh pullup setup")
        assertTrue(swapped.toString(),swapped is NativeCustomJournalResult.Durable)
        val live=(swapped as NativeCustomJournalResult.Durable).issued.journal
        assertEquals("bodyweight_crunch",
            live.originalSnapshot.slots.single().exerciseId)
        assertEquals("pullup",live.snapshot.slots.single().exerciseId)
        assertEquals(1L,live.revision)
        val fresh=NativeKotlinCustomWorkoutJournalFileStore(
            temp.root.toPath(),NativeKotlinApprovalRevisionFence(
                temp.root.toPath()),identity,prepared,preflight,
            history,composerJournal,null,sources)
        val opened=fresh.open("owner-1","workout-2")
            as NativeCustomJournalResult.Durable
        assertEquals(live,opened.issued.journal)
        assertTrue((fresh.swap("owner-1","workout-2",
            "swap-live-1",0L,"slot-0","pullup",chosen,
            "Fresh pullup setup") as NativeCustomJournalResult.Durable).replayed)
        val key=live.snapshot.slots.single().sets.single().setKey
        val updated=fresh.record("owner-1","workout-2",
            CustomWorkoutJournalOperation.Record(
                "record-new",1L,CustomWorkoutVisitKey(
                    key,CustomWorkoutVisitPart.MAIN),
                KotlinJournalSetOutcome.COMPLETED,
                KotlinPerformedSet(null,null,9,2)))
        assertTrue(updated.toString(),updated is NativeCustomJournalResult.Durable)
        val journal=(updated as NativeCustomJournalResult.Durable).issued.journal
        assertEquals(1,journal.effectiveVisits.size)
        assertTrue(journal.canProposeFinish)
        val finished=NativeKotlinCustomHistoryTransaction(
            history,fresh,fence,identity).complete(
                "owner-1","athlete-1","workout-2",
                CustomWorkoutJournalOperation.Finish(
                    "finish-after-swap",journal.revision))
        assertTrue(finished.toString(),finished is NativeCustomFinishResult.Committed)
        val restored=fresh.open("owner-1","workout-2")
            as NativeCustomJournalResult.Durable
        assertEquals("finish-after-swap",
            restored.issued.journal.finishedByEventId)
        assertEquals(1,restored.issued.journal.effectiveVisits.size)
        assertEquals("pullup",restored.issued.journal.snapshot.slots.single().exerciseId)
        assertEquals("bodyweight_crunch",
            restored.issued.journal.originalSnapshot.slots.single().exerciseId)
    }

    @Test fun undoLiveSwapPersistsOriginalSessionAndAllowsOriginalFinish() {
        initialize(withPullup=true);approve();prepareWeek();enroll()
        val approved=issueWeek().issued.journal
        val original=approved.snapshot
        val swapped=issued.swap("owner-1","workout-2",
            "swap-reversible",0L,"slot-0","pullup",
            listOf(set(8)),"New pullup")
        assertTrue(swapped is NativeCustomJournalResult.Durable)
        val restored=issued.undoSwap("owner-1","workout-2",
            "undo-reversible",1L,"swap-reversible")
        assertTrue(restored.toString(),
            restored is NativeCustomJournalResult.Durable)
        val journal=(restored as NativeCustomJournalResult.Durable)
            .issued.journal
        assertEquals(original,journal.snapshot)
        assertEquals(original,journal.originalSnapshot)
        assertEquals(2L,journal.revision)
        assertEquals(2,journal.receipts.size)
        assertEquals(1,journal.unresolvedVisitCount)

        val fresh=NativeKotlinCustomWorkoutJournalFileStore(
            temp.root.toPath(),NativeKotlinApprovalRevisionFence(
                temp.root.toPath()),identity,prepared,preflight,
            history,composerJournal,null,sources)
        assertEquals(journal,(fresh.open("owner-1","workout-2")
            as NativeCustomJournalResult.Durable).issued.journal)
        val retry=fresh.undoSwap("owner-1","workout-2",
            "undo-reversible",1L,"swap-reversible")
        assertTrue((retry as NativeCustomJournalResult.Durable).replayed)
        val duplicate=fresh.undoSwap("owner-1","workout-2",
            "undo-again",2L,"swap-reversible")
        assertEquals(CustomWorkoutJournalRefusal.SWAP_UNDO_UNAVAILABLE,
            (duplicate as NativeCustomJournalResult.Rejected).journalReason)
        val logged=fresh.record("owner-1","workout-2",
            recordSet("original-after-undo",2))
        assertTrue(logged is NativeCustomJournalResult.Durable)
        val finished=NativeKotlinCustomHistoryTransaction(
            history,fresh,fence,identity).complete(
                "owner-1","athlete-1","workout-2",
                CustomWorkoutJournalOperation.Finish("finish-undo",3L))
        assertTrue(finished.toString(),
            finished is NativeCustomFinishResult.Committed)
        val completed=(fresh.open("owner-1","workout-2")
            as NativeCustomJournalResult.Durable).issued.journal
        assertEquals("finish-undo",completed.finishedByEventId)
        assertEquals(original,completed.snapshot)
        assertEquals(4,completed.receipts.size)
    }

    @Test fun undoLiveSwapRefusesTouchedReplacementAndForeignOwner() {
        initialize(withPullup=true);approve();prepareWeek();enroll();issueWeek()
        val new=(issued.swap("owner-1","workout-2",
            "swap-started",0L,"slot-0","pullup",
            listOf(set(8)),"") as NativeCustomJournalResult.Durable)
            .issued.journal
        val setKey=new.snapshot.slots.single().sets.single().setKey
        assertTrue(issued.record("owner-1","workout-2",
            CustomWorkoutJournalOperation.Record(
                "performed-replacement",1L,CustomWorkoutVisitKey(
                    setKey,CustomWorkoutVisitPart.MAIN),
                KotlinJournalSetOutcome.COMPLETED,
                KotlinPerformedSet(null,null,9,2)))
            is NativeCustomJournalResult.Durable)
        val denied=issued.undoSwap("owner-1","workout-2",
            "undo-touched",2L,"swap-started")
        assertEquals(CustomWorkoutJournalRefusal.SWAP_ALREADY_STARTED,
            (denied as NativeCustomJournalResult.Rejected).journalReason)
        current=NativeKotlinOwnerIdentity("other-owner","athlete-1")
        assertEquals(NativeCustomJournalRefusal.NOT_AUTHORIZED,
            (issued.undoSwap("owner-1","workout-2",
                "foreign-undo",2L,"swap-started")
                as NativeCustomJournalResult.Rejected).reason)
        current=NativeKotlinOwnerIdentity("owner-1","athlete-1")
        assertEquals("pullup",(issued.open("owner-1","workout-2")
            as NativeCustomJournalResult.Durable).issued.journal.snapshot
            .slots.single().exerciseId)
    }

    @Test fun liveSwapRejectsStartedOriginalAndUnverifiedEquipment() {
        initialize();approve();prepareWeek();enroll();issueWeek()
        assertEquals(NativeCustomJournalRefusal.SWAP_TARGET_UNAVAILABLE,
            (issued.swap("owner-1","workout-2","try-pullup",0L,
                "slot-0","pullup",listOf(set(8)),"")
                as NativeCustomJournalResult.Rejected).reason)
        val logged=issued.record("owner-1","workout-2",
            recordSet("saved-before-swap",0))
        assertTrue(logged is NativeCustomJournalResult.Durable)
        val refused=issued.swap("owner-1","workout-2",
            "try-after-log",1L,"slot-0","bodyweight_crunch",
            listOf(set(8)),"")
        assertTrue(refused is NativeCustomJournalResult.Rejected)
        assertEquals(1L,(issued.open("owner-1","workout-2")
            as NativeCustomJournalResult.Durable).issued.journal.revision)
    }

}
