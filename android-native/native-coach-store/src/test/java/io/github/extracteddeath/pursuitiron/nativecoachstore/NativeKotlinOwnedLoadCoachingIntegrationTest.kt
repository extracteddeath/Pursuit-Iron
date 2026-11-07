package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * REAL app-private file stores and finish transactions, not synthetic
 * evidence: read both saved workouts through the exhaustive native ledger.
 */
class NativeKotlinOwnedLoadCoachingIntegrationTest {
    @get:Rule val temp=TemporaryFolder()
    private val root get()=temp.root.toPath()
    private fun n(value:String)=BigDecimal(value)
    private val source=KotlinTrainingSourceRevision(
        "athlete-1",4,"program-1",8,"gym-1",3,"catalog-5","coach-rules-2")
    private val identity=NativeKotlinCurrentIdentity {
        NativeKotlinOwnerIdentity("owner-1","athlete-1")
    }
    private val fence by lazy {NativeKotlinApprovalRevisionFence(root)}
    private val approvals by lazy {
        NativeKotlinApprovalFileAuthority(root,fence,identity)
    }
    private val journals by lazy {KotlinNativeJournalFileStore(root)}
    private val corrections by lazy {
        KotlinNativeSavedCorrectionFileStore(root,journals)
    }
    private val history by lazy {NativeKotlinHistoryManifestFileStore(root)}
    private val transactions by lazy {
        NativeKotlinHistoryTransactionCoordinator(
            history,journals,corrections,approvals,fence)
    }
    private val drafts by lazy {
        NativeCustomCycleDraftFileStore(root,fence,identity)
    }
    private val authoring by lazy {
        NativeKotlinCustomAuthoringSourceFileStore(root,fence,identity)
    }
    private val signoffs by lazy {
        NativeKotlinCustomCycleSignoffFileStore(
            root,fence,identity,drafts,authoring)
    }
    private val preflight by lazy {
        NativeKotlinCustomWorkoutPreflight(
            fence,identity,drafts,authoring,signoffs)
    }
    private val prepared by lazy {
        NativeKotlinCustomWorkoutPreparedStore(
            root,fence,identity,preflight)
    }
    private val custom by lazy {
        NativeKotlinCustomWorkoutJournalFileStore(
            root,fence,identity,prepared,preflight,history,journals)
    }
    private val reader by lazy {
        NativeKotlinMixedHistoryReadCoordinator(
            history,journals,corrections,custom,approvals,identity,fence)
    }

    private val stock=VerifiedLoadStock.Totals(
        LoadUnit.LB,LoadBasis.DUMBBELL_PER_HAND,
        listOf(n("20"),n("25"),n("30"),n("35")))
    private fun option(id:String,muscle:TrainingMuscle,role:ComposerRole)=
        ComposerExerciseOption(
            id,if(muscle==TrainingMuscle.CHEST)
                ComposerPattern.HORIZONTAL_PRESS else ComposerPattern.HORIZONTAL_PULL,
            setOf(ComposerFocus.FULL_BODY),
            ComposerEquipment.Verified(LoadUnit.LB,n("30"),stock),
            listOf(DeclaredMuscleCredit(muscle,MuscleCreditRole.DIRECT,n("1"))),
            90,120,2,2,3,3,1,2,role=role)
    private val program by lazy {
        val window=DeclaredSessionWindow(1,120,null,60)
        val bounds=TrainingMuscle.entries.map { muscle ->
            if(muscle in setOf(TrainingMuscle.CHEST,TrainingMuscle.LATS))
                DeclaredMuscleDoseBounds(muscle,n("2"),n("8"),n("10"),1)
            else DeclaredMuscleDoseBounds(muscle,n("0"),n("0"),n("0"),0)
        }
        ComposerRequest(ComposerSplit.FULL_BODY,TrainingPhase.STRENGTH,
            listOf(ComposerDay(0,ComposerFocus.FULL_BODY,window),
                ComposerDay(2,ComposerFocus.FULL_BODY,window),
                ComposerDay(4,ComposerFocus.FULL_BODY,window)),
            bounds,listOf(
                option("db-press",TrainingMuscle.CHEST,ComposerRole.PRIMARY),
                option("db-row",TrainingMuscle.LATS,ComposerRole.SECONDARY)),
            3,1,listOf(
                ComposerSessionIntent(0,ComposerSessionPurpose.STRENGTH_SKILL,"db-press"),
                ComposerSessionIntent(1,ComposerSessionPurpose.HYPERTROPHY),
                ComposerSessionIntent(2,ComposerSessionPurpose.HYPERTROPHY)),
            maximumBarbellExercisesPerDay=0,
            allowDirectTricepsOnLower=false)
    }
    private fun policy(id:String,purpose:ComposerSessionPurpose)=
        ExercisePrescriptionPolicy(id,purpose,RepBand(8,12),2,
            90,120,SetLoadScheme.STRAIGHT)
    private val policies=listOf(
        policy("db-press",ComposerSessionPurpose.STRENGTH_SKILL),
        policy("db-press",ComposerSessionPurpose.HYPERTROPHY),
        policy("db-row",ComposerSessionPurpose.STRENGTH_SKILL),
        policy("db-row",ComposerSessionPurpose.HYPERTROPHY))

    private fun accept(id:Int,day:Int,
        sessionPolicies:List<ExercisePrescriptionPolicy> = policies
    ):KotlinWorkoutJournalState {
        val grant=KotlinWorkoutSelectionGrant(
            "grant-$id","workout-$id",source,day)
        val approval=NativeKotlinWorkoutApproval(
            "owner-1","athlete-1",source,source,grant,program,sessionPolicies,
            "storage-$id",id.toLong())
        assertEquals(NativeApprovalWriteResult.Committed(id.toLong()),
            approvals.publish("owner-1",(id-1).toLong(),approval))
        val issued=NativeKotlinWorkoutCoordinator(journals,approvals,fence)
            .issue("owner-1","workout-$id")
        assertTrue(issued.toString(),issued is NativeKotlinIssueResult.Committed)
        return (issued as NativeKotlinIssueResult.Committed).journal
    }
    private fun complete(id:Int,day:Int,
        sessionPolicies:List<ExercisePrescriptionPolicy> = policies,
        actualRir:Int?=2
    ):Pair<KotlinWorkoutJournalState,String> {
        var journal=accept(id,day,sessionPolicies)
        var pressKey=""
        for ((index,target) in journal.snapshot.targets.withIndex()) {
            val load=(target.load as DraftSetLoad.Verified)
            if(target.exerciseId=="db-press" &&
                target.type==DraftSetType.WORKING && pressKey.isEmpty())
                pressKey=target.setKey
            val event=KotlinJournalOperation.RecordSet(
                "set-$id-$index",journal.revision,target.setKey,
                KotlinJournalSetOutcome.COMPLETED,
                KotlinPerformedSet(load.value,load.unit,12,actualRir))
            val written=journals.appendStored(
                "owner-1",journal.snapshot.workoutId,event,
                approvals::canAccessIssuedWorkout)
            assertTrue(written.toString(),written is NativeJournalFileResult.Durable)
            journal=(written as NativeJournalFileResult.Durable).journal
        }
        assertTrue(pressKey.isNotBlank())
        val end=transactions.complete("owner-1","athlete-1",
            "workout-$id",KotlinJournalOperation.Finish(
                "finish-$id",journal.revision))
        assertTrue(end.toString(),end is NativeHistoryTransactionResult.Durable)
        return journal to pressKey
    }
    private fun review():NativeOwnerLoadReviewResult =
        reader.reviewCurrentApprovedLoad(
            "owner-1","athlete-1",approvals,"db-press",
            n("5"),CoachReadiness.NORMAL,false)

    private fun enrolled() {
        val opened=transactions.enroll("owner-1","athlete-1") { owner,athlete ->
            owner=="owner-1" && athlete=="athlete-1"
        }
        assertTrue(opened.toString(),opened is NativeHistoryTransactionResult.Durable)
    }
    private fun intent(
        proposed:NativeCurrentLoadReviewResult.Reviewed,
        confirmation:Boolean=true,
        workoutId:String="workout-3",
        candidate:BigDecimal=n("35"),
        expectedHistory:Long=proposed.historyRevision
    )=NativeKotlinCoachLoadAcceptanceIntent(
        "db-press",2,expectedHistory,proposed.comparedWorkoutIds,
        CoachNextLoadChangeKind.EARNED_INCREMENT,n("30"),candidate,
        proposed.coaching.affectedDayIndices,workoutId,"grant-next",
        "storage-next",1,confirmation)
    private fun accept(
        data:NativeKotlinCoachLoadAcceptanceIntent
    )=reader.acceptCurrentApprovedLoad(
        "owner-1","athlete-1",approvals,data,n("5"),
        CoachReadiness.NORMAL,false)

    private fun sourceRecord():NativeCustomAuthoringSnapshot {
        val reviewed=CoachAthleteSetupDomain.review(AthleteSetupDraft(
            profileRevision="profile-1",athleteProfileId="athlete-1",
            goal=AthleteTrainingGoal.STRENGTH,
            experience=AthleteExperience.ADVANCED,
            split=ComposerSplit.FULL_BODY,
            trainingWeekdays=setOf(0,2,4),
            timezoneId="America/New_York",
            sessionTimeBucket=SessionTimeBucket.SIXTY_TO_NINETY,
            hardSessionLimit=HardSessionLimit.NO_HARD_LIMIT,
            gym=GymProfileDraft("gym-1",LoadUnit.LB,
                mapOf("db-press-stock" to stock,"db-row-stock" to stock),
                emptySet(),emptySet()),
            allowBodyweight=true,allowSupersets=false,
            allowTricepsOnLower=false,maxBarbellExercisesPerDay=0,
            focusedMuscles=emptySet(),reducedMuscles=emptySet(),
            excludedExerciseIds=emptySet(),lockedTrainingDays=emptySet(),
            hardTrainingDays=emptySet(),longTrainingDays=emptySet(),
            declaredRestDays=setOf(1,3,5,6)))
        assertEquals(AthleteSetupStatus.READY_FOR_PROGRAM_REVIEW,reviewed.status)
        val catalog=program.exercises.map {ex->
            CatalogMovement(ex.exerciseId,"family-"+ex.exerciseId,"default",
                null,ex.pattern,ex.role,ex.allowedFocus,
                ex.exerciseId+"-stock",LoadBasis.DUMBBELL_PER_HAND,
                ex.credits,CatalogCreditStatus.AUTHOR_REVIEWED_PROVISIONAL,
                "review-1",emptySet(),ex.sourcePracticalReps,ex.recoveryGroup,
                ex.minimumGroupCalendarDayGap)
        }
        return NativeCustomAuthoringSnapshot(
            "owner-1","athlete-1",1,reviewed,catalog)
    }

    @Test fun latestCorrectedWorkoutShapesNextPerSetRepsWithoutChangingIssuedTargets() {
        enrolled()
        val completed=complete(1,1)
        val oldJournal=completed.first
        val editedKey=completed.second
        val parent=oldJournal.effectiveSets.getValue(editedKey)
            .operation.eventId
        val saved=transactions.correct("owner-1","athlete-1","workout-1",
            KotlinSavedSetCorrection(
                "rep-correction-1",0,editedKey,parent,
                KotlinJournalSetOutcome.COMPLETED,
                KotlinPerformedSet(n("30"),LoadUnit.LB,11,3)))
        assertTrue(saved.toString(),saved is NativeHistoryTransactionResult.Durable)
        val next=NativeKotlinWorkoutApproval(
            "owner-1","athlete-1",source,source,
            KotlinWorkoutSelectionGrant("grant-2","workout-2",source,1),
            program,policies,"storage-2",2)
        assertEquals(NativeApprovalWriteResult.Committed(2),
            approvals.publish("owner-1",1,next))
        val gym=sourceRecord()
        assertEquals(NativeAuthoringSourceWrite.Committed(1),
            authoring.publish("owner-1",0,gym))
        val result=reader.reviewCurrentNextSetTargets(
            "owner-1","athlete-1",approvals,"db-press",
            CoachReadiness.NORMAL,false,authoring)
        assertTrue(result.toString(),result is NativeOwnerNextSetResult.Review)
        val verified=result as NativeOwnerNextSetResult.Review
        assertEquals(2L,verified.approvalRevision)
        val historyReview=verified.candidate
            as NativeCurrentRepPrescriptionResult.Reviewed
        assertEquals("workout-1",historyReview.priorWorkoutId)
        val plan=historyReview.proposal
            as AdaptiveSetPrescriptionResult.Candidate
        assertEquals("workout-2",plan.workout.workoutId)
        assertEquals(listOf(11,12),plan.observedReps)
        assertEquals(listOf(12,12),plan.newRepTargets)
        assertEquals("db-press:d1:s0",plan.editedSetKey)
        assertEquals(listOf(RepBand(12,12),RepBand(12,12)),
            plan.workout.targets.filter { it.exerciseId=="db-press" }
                .map {it.reps})
        assertTrue(plan.workout.targets.filter {
            it.exerciseId=="db-press"
        }.all {
            (it.load as DraftSetLoad.Verified).value.compareTo(n("30"))==0 &&
                it.targetRir==2
        })
        assertTrue(plan.workout.targets.filter {
            it.exerciseId=="db-row"
        }.all {it.reps==RepBand(8,12)})
        assertFalse(historyReview.canIssueWorkout)
        assertFalse(plan.mayIssueWorkout)
        assertEquals(8L,oldJournal.snapshot.source.programRevision)
        assertTrue(journals.openStored("owner-1","workout-1",
            approvals::canAccessIssuedWorkout) is NativeJournalFileResult.Durable)
        assertEquals(NativeJournalFileResult.Rejected(
            NativeJournalFileRefusal.NOT_ISSUED),
            journals.openStored("owner-1","workout-2",
                approvals::canAccessIssuedWorkout))
        val issuer=NativeKotlinWorkoutCoordinator(journals,approvals,fence)
        assertTrue(issuer.issue("owner-1","workout-2")
            is NativeKotlinIssueResult.Committed)
        assertEquals(NativeOwnerNextSetResult.Rejected(
            NativeOwnerNextSetRefusal.ALREADY_ISSUED_OR_CONFLICT),
            reader.reviewCurrentNextSetTargets(
                "owner-1","athlete-1",approvals,"db-press",
                CoachReadiness.NORMAL,false,authoring))
        // The approved generic workout is not silently edited by this
        // read-only candidate. A separate acceptance protocol is required.
        val issued=issuer.open("owner-1","workout-2")
            as NativeJournalFileResult.Durable
        assertTrue(issued.journal.snapshot.targets.filter {
            it.exerciseId=="db-press"
        }.all {it.reps==RepBand(8,12)})
    }

    private fun repReadyHistory():NativeKotlinAcceptedRepIntent {
        enrolled()
        val previous=complete(1,1)
        val first=previous.second
        val original=previous.first.effectiveSets.getValue(first).operation.eventId
        assertTrue(transactions.correct(
            "owner-1","athlete-1","workout-1",
            KotlinSavedSetCorrection(
                "rep-accept-correction",0,first,original,
                KotlinJournalSetOutcome.COMPLETED,
                KotlinPerformedSet(n("30"),LoadUnit.LB,11,3)))
            is NativeHistoryTransactionResult.Durable)
        val issued=NativeKotlinWorkoutApproval(
            "owner-1","athlete-1",source,source,
            KotlinWorkoutSelectionGrant("grant-2","workout-2",source,1),
            program,policies,"storage-2",2)
        assertEquals(NativeApprovalWriteResult.Committed(2),
            approvals.publish("owner-1",1,issued))
        assertEquals(NativeAuthoringSourceWrite.Committed(1),
            authoring.publish("owner-1",0,sourceRecord()))
        val current=reader.reviewCurrentNextSetTargets(
            "owner-1","athlete-1",approvals,"db-press",
            CoachReadiness.NORMAL,false,authoring)
        assertTrue(current.toString(),current is NativeOwnerNextSetResult.Review)
        val displayed=(current as NativeOwnerNextSetResult.Review).candidate
            as NativeCurrentRepPrescriptionResult.Reviewed
        val proposal=displayed.proposal as AdaptiveSetPrescriptionResult.Candidate
        return NativeKotlinAcceptedRepIntent(
            "db-press",current.approvalRevision,displayed.historyRevision,
            "workout-2",proposal.historicalWorkoutId,proposal.editedSetKey,
            proposal.observedReps,proposal.newRepTargets,
            "workout-3","grant-3","storage-3",true)
    }

    private fun acceptRep(intent:NativeKotlinAcceptedRepIntent)=
        reader.acceptReviewedNextSetTargets(
            "owner-1","athlete-1",approvals,intent,
            CoachReadiness.NORMAL,false,authoring)

    private fun repIssuer():NativeKotlinWorkoutCoordinator {
        val authority=NativeKotlinGymCoherentWorkoutAuthority(
            approvals,authoring,fence)
        return NativeKotlinWorkoutCoordinator(journals,authority,fence,
            NativeKotlinAcceptedRepSnapshotVerifier {owner,approved ->
                reader.rederiveAcceptedRepSnapshot(
                    owner,"athlete-1",approvals,approved,authoring)
            })
    }

    @Test fun explicitlyAcceptedRepTargetsSurviveColdOpenAndIssueExactlyOnce() {
        val evidence=repReadyHistory()
        assertEquals(NativeKotlinRepAcceptanceResult.Rejected(
            NativeKotlinRepAcceptanceRefusal.NOT_ACCEPTED),
            acceptRep(evidence.copy(explicitlyAccepted=false)))
        assertEquals(NativeKotlinRepAcceptanceResult.Rejected(
            NativeKotlinRepAcceptanceRefusal.PROPOSAL_CHANGED),
            acceptRep(evidence.copy(expectedNewRepTargets=listOf(15,15))))
        assertEquals(NativeKotlinRepAcceptanceResult.Rejected(
            NativeKotlinRepAcceptanceRefusal.IDENTIFIER_ALREADY_USED),
            acceptRep(evidence.copy(nextWorkoutId="workout-1")))
        val committed=acceptRep(evidence)
        assertTrue(committed.toString(),
            committed is NativeKotlinRepAcceptanceResult.Committed)
        assertEquals(3L,(committed as NativeKotlinRepAcceptanceResult.Committed)
            .approvalRevision)
        assertFalse(committed.workoutIssued)
        val proof=checkNotNull(approvals.currentOwnedApproval("owner-1"))
            .acceptedRepTargets
        assertNotNull(proof)
        assertEquals(listOf(11,12),proof?.observedReps)
        assertEquals(listOf(12,12),proof?.newRepTargets)
        assertEquals("workout-1",proof?.historicalWorkoutId)
        assertEquals(NativeKotlinIssueResult.Rejected(
            NativeKotlinIssueRefusal.WORKOUT_UNRESOLVED,
            KotlinSnapshotRejection.UNVERIFIED_TARGET),
            NativeKotlinWorkoutCoordinator(journals,approvals,fence)
                .issue("owner-1","workout-3"))
        val issued=repIssuer().issue("owner-1","workout-3")
        assertTrue(issued.toString(),issued is NativeKotlinIssueResult.Committed)
        val journal=(issued as NativeKotlinIssueResult.Committed).journal
        assertFalse(issued.replayed)
        val press=journal.snapshot.targets.filter {
            it.exerciseId=="db-press" && it.type==DraftSetType.WORKING
        }
        assertEquals(listOf(RepBand(12,12),RepBand(12,12)),
            press.map {it.reps})
        assertTrue(press.all {
            (it.load as DraftSetLoad.Verified).value.compareTo(n("30"))==0 &&
                it.targetRir==2 && it.restSeconds==120
        })
        assertTrue(journal.snapshot.targets.filter {it.exerciseId=="db-row"}
            .all {it.reps==RepBand(8,12)})
        assertTrue(repIssuer().open("owner-1","workout-3")
            is NativeJournalFileResult.Durable)
        assertEquals(NativeKotlinRepAcceptanceResult.Rejected(
            NativeKotlinRepAcceptanceRefusal.APPROVAL_CHANGED),acceptRep(evidence))
        val older=repIssuer().open("owner-1","workout-1")
            as NativeJournalFileResult.Durable
        assertTrue(older.journal.snapshot.targets.filter {
            it.exerciseId=="db-press"
        }.all {it.reps==RepBand(8,12)})
    }

    @Test fun completedPersonalizedWorkoutCanEarnNextRepTargetWithoutReset() {
        val first=repReadyHistory()
        assertTrue(acceptRep(first) is NativeKotlinRepAcceptanceResult.Committed)
        val native=repIssuer()
        val granted=native.issue("owner-1","workout-3")
            as NativeKotlinIssueResult.Committed
        var completed=granted.journal
        assertEquals(listOf(RepBand(12,12),RepBand(12,12)),
            completed.snapshot.targets.filter {
                it.exerciseId=="db-press" && it.type==DraftSetType.WORKING
            }.map {it.reps})
        for ((index,target) in completed.snapshot.targets.withIndex()) {
            val physical=target.load as DraftSetLoad.Verified
            val actual=if(target.exerciseId=="db-press") {
                if(target.ordinal==0)12 else 11
            } else 12
            val outcome=journals.appendStored("owner-1","workout-3",
                KotlinJournalOperation.RecordSet(
                    "personalized-set-$index",completed.revision,
                    target.setKey,KotlinJournalSetOutcome.COMPLETED,
                    KotlinPerformedSet(physical.value,physical.unit,actual,3)),
                approvals::canAccessIssuedWorkout)
            assertTrue(outcome.toString(),outcome is NativeJournalFileResult.Durable)
            completed=(outcome as NativeJournalFileResult.Durable).journal
        }
        val finish=transactions.complete("owner-1","athlete-1",
            "workout-3",KotlinJournalOperation.Finish(
                "personalized-finish",completed.revision))
        assertTrue(finish.toString(),
            finish is NativeHistoryTransactionResult.Durable)
        val historical=reader.read("owner-1","athlete-1")
            as NativeKotlinMixedHistoryReadResult.Review
        assertEquals(2,historical.timeline.completions.size)
        val last=historical.timeline.completions.last()
            as NativeKotlinMixedHistoryCompletion.Composer
        assertEquals("workout-3",last.evidence.workoutId)
        assertEquals(listOf(RepBand(12,12),RepBand(12,12)),
            last.evidence.exerciseReviews.single {it.exerciseId=="db-press"}
                .workingSets.map {it.issuedReps})
        val current=checkNotNull(approvals.currentOwnedApproval("owner-1"))
        assertEquals(NativeApprovalWriteResult.Committed(4),
            approvals.publish("owner-1",3,current.copy(
                grant=current.grant.copy(
                    workoutId="workout-4",grantId="grant-4"),
                storageReceiptId="storage-4",storageRevision=4,
                acceptedRepTargets=null)))
        val next=reader.reviewCurrentNextSetTargets(
            "owner-1","athlete-1",approvals,"db-press",
            CoachReadiness.NORMAL,false,authoring)
        assertTrue(next.toString(),next is NativeOwnerNextSetResult.Review)
        val reviewed=(next as NativeOwnerNextSetResult.Review).candidate
            as NativeCurrentRepPrescriptionResult.Reviewed
        val following=reviewed.proposal as AdaptiveSetPrescriptionResult.Candidate
        assertEquals("workout-3",following.historicalWorkoutId)
        assertEquals(listOf(12,11),following.observedReps)
        assertEquals(listOf(12,12),following.newRepTargets)
        assertEquals(AdaptiveRepDecisionKind.REPEAT_PREVIOUSLY_ISSUED_TARGETS,
            following.decisionKind)
        assertEquals("workout-4",following.workout.workoutId)
        assertEquals(listOf(RepBand(12,12),RepBand(12,12)),
            following.workout.targets.filter {
                it.exerciseId=="db-press" && it.type==DraftSetType.WORKING
            }.map {it.reps})
        assertFalse(following.mayIssueWorkout)
    }

    @Test fun reportedGoalEffortKeepsExactRepTargetsThroughAnotherDayAndColdIssuance() {
        assertRepeatedRepIssuance(2)
    }

    @Test fun unreportedGoalEffortKeepsExactRepTargetsThroughAnotherDayAndColdIssuance() {
        assertRepeatedRepIssuance(null)
    }

    private fun assertRepeatedRepIssuance(actualRir:Int?) {
        assertTrue(acceptRep(repReadyHistory()) is NativeKotlinRepAcceptanceResult.Committed)
        var journal=(repIssuer().issue("owner-1","workout-3")
            as NativeKotlinIssueResult.Committed).journal
        for((index,target) in journal.snapshot.targets.withIndex()) {
            val load=target.load as DraftSetLoad.Verified
            val reps=if(target.exerciseId=="db-press" && target.ordinal==0)11 else 12
            val saved=journals.appendStored("owner-1","workout-3",
                KotlinJournalOperation.RecordSet("repeat-set-$index",journal.revision,
                    target.setKey,KotlinJournalSetOutcome.COMPLETED,
                    KotlinPerformedSet(load.value,load.unit,reps,actualRir)),
                approvals::canAccessIssuedWorkout)
            assertTrue(saved.toString(),saved is NativeJournalFileResult.Durable)
            journal=(saved as NativeJournalFileResult.Durable).journal
        }
        assertTrue(transactions.complete("owner-1","athlete-1","workout-3",
            KotlinJournalOperation.Finish("repeat-finish",journal.revision))
            is NativeHistoryTransactionResult.Durable)
        // Same exercise AND purpose on a different day must not replace
        // the individualized day-1 result with day 2's full ceiling work.
        complete(4,2)
        val current=checkNotNull(approvals.currentOwnedApproval("owner-1"))
        assertEquals(NativeApprovalWriteResult.Committed(5),
            approvals.publish("owner-1",4,current.copy(
                grant=current.grant.copy(workoutId="workout-5",grantId="grant-5",dayIndex=1),
                storageReceiptId="storage-5",storageRevision=5,acceptedRepTargets=null)))
        val displayed=reader.reviewCurrentNextSetTargets(
            "owner-1","athlete-1",approvals,"db-press",
            CoachReadiness.NORMAL,false,authoring) as NativeOwnerNextSetResult.Review
        val review=displayed.candidate as NativeCurrentRepPrescriptionResult.Reviewed
        val candidate=review.proposal as AdaptiveSetPrescriptionResult.Candidate
        assertEquals("workout-3",candidate.historicalWorkoutId)
        assertEquals(listOf(11,12),candidate.observedReps)
        assertEquals(listOf(12,12),candidate.newRepTargets)
        assertEquals(AdaptiveRepDecisionKind.REPEAT_PREVIOUSLY_ISSUED_TARGETS,
            candidate.decisionKind)
        assertEquals(if(actualRir==null)2 else 0,candidate.assumedGoalRirSetCount)
        val intent=NativeKotlinAcceptedRepIntent(
            "db-press",displayed.approvalRevision,review.historyRevision,
            "workout-5",candidate.historicalWorkoutId,candidate.editedSetKey,
            candidate.observedReps,candidate.newRepTargets,
            "workout-6","grant-6","storage-6",true,candidate.decisionKind)
        assertEquals(NativeKotlinRepAcceptanceResult.Rejected(
            NativeKotlinRepAcceptanceRefusal.PROPOSAL_CHANGED),
            acceptRep(intent.copy(expectedDecisionKind=
                AdaptiveRepDecisionKind.EARNED_ONE_MORE_REP)))
        assertTrue(acceptRep(intent) is NativeKotlinRepAcceptanceResult.Committed)
        assertEquals(AdaptiveRepDecisionKind.REPEAT_PREVIOUSLY_ISSUED_TARGETS,
            checkNotNull(approvals.currentOwnedApproval("owner-1"))
                .acceptedRepTargets?.decisionKind)

        val coldJournals=KotlinNativeJournalFileStore(root)
        val coldApprovals=NativeKotlinApprovalFileAuthority(root,fence,identity)
        val coldReader=NativeKotlinMixedHistoryReadCoordinator(
            NativeKotlinHistoryManifestFileStore(root),coldJournals,
            KotlinNativeSavedCorrectionFileStore(root,coldJournals),custom,
            coldApprovals,identity,fence)
        val coldIssuer=NativeKotlinWorkoutCoordinator(coldJournals,
            NativeKotlinGymCoherentWorkoutAuthority(coldApprovals,authoring,fence),fence,
            NativeKotlinAcceptedRepSnapshotVerifier {owner,approved->
                coldReader.rederiveAcceptedRepSnapshot(
                    owner,"athlete-1",coldApprovals,approved,authoring)
            })
        val issued=coldIssuer.issue("owner-1","workout-6")
        assertTrue(issued.toString(),issued is NativeKotlinIssueResult.Committed)
        val next=(issued as NativeKotlinIssueResult.Committed).journal
        assertTrue(next.effectiveSets.isEmpty())
        assertEquals(listOf(RepBand(12,12),RepBand(12,12)),
            next.snapshot.targets.filter{it.exerciseId=="db-press"}.map{it.reps})
        assertTrue(next.snapshot.targets.filter{it.exerciseId=="db-press"}.all {
            (it.load as DraftSetLoad.Verified).value.compareTo(n("30"))==0 && it.targetRir==2
        })
        assertTrue(next.snapshot.targets.filter{it.exerciseId=="db-row"}.all {
            it.reps==RepBand(8,12)
        })
        val historical=coldIssuer.open("owner-1","workout-3") as NativeJournalFileResult.Durable
        val keys=historical.journal.snapshot.targets.filter{it.exerciseId=="db-press"}.map{it.setKey}
        assertEquals(listOf(11,12),keys.map {
            (historical.journal.effectiveSets.getValue(it).operation as KotlinJournalOperation.RecordSet)
                .performed!!.reps
        })
        assertTrue(keys.all {
            (historical.journal.effectiveSets.getValue(it).operation as KotlinJournalOperation.RecordSet)
                .performed!!.actualRir==actualRir
        })
        assertTrue((coldIssuer.issue("owner-1","workout-6")
            as NativeKotlinIssueResult.Committed).replayed)
    }

    @Test fun diskBackedConservativeWeightReviewUsesPersonalizedThenOrdinaryCeilingSessions() {
        val intent=repReadyHistory()
        assertTrue(acceptRep(intent) is NativeKotlinRepAcceptanceResult.Committed)
        val issuer=repIssuer()
        var issued=(issuer.issue("owner-1","workout-3")
            as NativeKotlinIssueResult.Committed).journal
        val exact=issued.snapshot.targets.filter {
            it.exerciseId=="db-press" && it.type==DraftSetType.WORKING
        }.map {it.reps}
        assertEquals(listOf(RepBand(12,12),RepBand(12,12)),exact)
        for((index,target) in issued.snapshot.targets.withIndex()) {
            val physical=target.load as DraftSetLoad.Verified
            val applied=journals.appendStored(
                "owner-1","workout-3",
                KotlinJournalOperation.RecordSet(
                    "weight-3-$index",issued.revision,target.setKey,
                    KotlinJournalSetOutcome.COMPLETED,
                    KotlinPerformedSet(physical.value,physical.unit,12,2)),
                approvals::canAccessIssuedWorkout)
            assertTrue(applied.toString(),applied is NativeJournalFileResult.Durable)
            issued=(applied as NativeJournalFileResult.Durable).journal
        }
        assertTrue(transactions.complete(
            "owner-1","athlete-1","workout-3",
            KotlinJournalOperation.Finish("weight-3-finish",issued.revision))
            is NativeHistoryTransactionResult.Durable)
        val before=reader.read("owner-1","athlete-1")
            as NativeKotlinMixedHistoryReadResult.Review
        val completed=before.timeline.completions.last()
            as NativeKotlinMixedHistoryCompletion.Composer
        assertEquals(KotlinHistoryProgressionGate.COMPARABLE_FOR_REVIEW,
            completed.evidence.exerciseReviews.single {
                it.exerciseId=="db-press"
            }.progressionGate)
        // All equal narrow target bands are syntactically comparable by
        // the old projector, but they are NOT the original 8–12 policy.
        assertEquals(RepBand(12,12),
            completed.evidence.exerciseReviews.single {
                it.exerciseId=="db-press"
            }.progressionWorkout?.targetReps)
        complete(4,1)
        val proposed=review()
        assertTrue(proposed.toString(),proposed is NativeOwnerLoadReviewResult.Review)
        val ownerReview=proposed as NativeOwnerLoadReviewResult.Review
        assertTrue(ownerReview.toString(),
            ownerReview.coaching is NativeCurrentLoadReviewResult.Reviewed)
        val verified=ownerReview.coaching as NativeCurrentLoadReviewResult.Reviewed
        assertEquals(listOf("workout-3","workout-4"),verified.comparedWorkoutIds)
        assertEquals(CoachNextLoadReviewStatus.READY_FOR_ATHLETE_REVIEW,
            verified.coaching.status)
        assertEquals(CoachNextLoadChangeKind.EARNED_INCREMENT,
            verified.coaching.kind)
        assertEquals(n("35"),verified.coaching.candidateLoad)
        assertFalse(verified.coaching.maySaveProgram)
        val reread=reader.read("owner-1","athlete-1")
            as NativeKotlinMixedHistoryReadResult.Review
        assertEquals(RepBand(12,12),
            (reread.timeline.completions[1]
                as NativeKotlinMixedHistoryCompletion.Composer)
                .evidence.exerciseReviews.single {
                    it.exerciseId=="db-press"
                }.workingSets.first().issuedReps)
        assertTrue(issuer.open("owner-1","workout-3")
            is NativeJournalFileResult.Durable)
    }

    @Test fun correctionAfterRepAcceptanceRevokesUnissuedTargetWithoutHistoryLoss() {
        val intent=repReadyHistory()
        assertTrue(acceptRep(intent) is NativeKotlinRepAcceptanceResult.Committed)
        val latest=reader.read("owner-1","athlete-1")
            as NativeKotlinMixedHistoryReadResult.Review
        val observed=latest.timeline.completions.single().manifest.workoutId
        assertEquals("workout-1",observed)
        val old=repIssuer().open("owner-1","workout-1")
            as NativeJournalFileResult.Durable
        val key=old.journal.snapshot.targets.first {
            it.exerciseId=="db-press" && it.type==DraftSetType.WORKING
        }.setKey
        val persisted=old.journal.effectiveSets.getValue(key).operation.eventId
        assertTrue(transactions.correct("owner-1","athlete-1","workout-1",
            KotlinSavedSetCorrection("rep-changed-after-accept",1,key,"rep-accept-correction",
                KotlinJournalSetOutcome.COMPLETED,
                KotlinPerformedSet(n("30"),LoadUnit.LB,9,1)))
            is NativeHistoryTransactionResult.Durable)
        val declined=repIssuer().issue("owner-1","workout-3")
        assertEquals(NativeKotlinIssueResult.Rejected(
            NativeKotlinIssueRefusal.WORKOUT_UNRESOLVED,
            KotlinSnapshotRejection.UNVERIFIED_TARGET),declined)
        assertEquals(NativeJournalFileResult.Rejected(
            NativeJournalFileRefusal.NOT_ISSUED),
            journals.openStored("owner-1","workout-3",
                approvals::canAccessIssuedWorkout))
        assertEquals("workout-3",
            approvals.currentOwnedApproval("owner-1")?.grant?.workoutId)
    }

    @Test fun currentGymEditBlocksNewWorkoutIssueWithoutInvalidatingHistoricalAccess() {
        enrolled()
        val gym=sourceRecord()
        assertEquals(NativeAuthoringSourceWrite.Committed(1),
            authoring.publish("owner-1",0,gym))
        val newGrant=KotlinWorkoutSelectionGrant(
            "first-native-grant","future-native-workout",source,1)
        val original=NativeKotlinWorkoutApproval(
            "owner-1","athlete-1",source,source,newGrant,program,
            policies,"first-native-receipt",1)
        assertEquals(NativeApprovalWriteResult.Committed(1),
            approvals.publish("owner-1",0,original))
        val coherent=NativeKotlinGymCoherentWorkoutAuthority(
            approvals,authoring,fence)
        assertEquals(original,coherent.currentApprovedWorkout(
            "owner-1","future-native-workout"))
        val proposed=NativeKotlinGymCatalogEditingDomain.removeStock(
            gym,1,"db-press-stock") as NativeGymCatalogEdit.Proposed
        assertEquals(NativeAuthoringSourceWrite.Committed(2),
            authoring.publish("owner-1",1,proposed.source))
        assertNull(coherent.currentApprovedWorkout(
            "owner-1","future-native-workout"))
        val issuer=NativeKotlinWorkoutCoordinator(journals,coherent,fence)
        val tracked=NativeKotlinTrackedWorkoutGateway(
            issuer,transactions,history,corrections,coherent,fence)
        assertEquals(NativeKotlinIssueResult.Rejected(
            NativeKotlinIssueRefusal.NOT_AUTHORIZED),
            tracked.issue("owner-1","athlete-1","future-native-workout"))
        assertEquals(NativeJournalFileResult.Rejected(
            NativeJournalFileRefusal.NOT_ISSUED),
            journals.openStored("owner-1","future-native-workout",
                coherent::canAccessIssuedWorkout))
        assertTrue(coherent.canAccessIssuedWorkout("owner-1",source))
    }

    @Test fun currentGymMustBePersistedAndAgreeBeforeCoachingApproval() {
        enrolled()
        complete(1,1)
        complete(2,1)
        val absent=reader.reviewCurrentApprovedLoad(
            "owner-1","athlete-1",approvals,"db-press",n("5"),
            CoachReadiness.NORMAL,false,authoring)
        assertEquals(NativeOwnerLoadReviewResult.Rejected(
            NativeOwnerLoadReviewRefusal.GYM_SOURCE_UNAVAILABLE),absent)
        val original=sourceRecord()
        assertEquals(NativeAuthoringSourceWrite.Committed(1),
            authoring.publish("owner-1",0,original))
        val valid=reader.reviewCurrentApprovedLoad(
            "owner-1","athlete-1",approvals,"db-press",n("5"),
            CoachReadiness.NORMAL,false,authoring)
        assertTrue(valid.toString(),valid is NativeOwnerLoadReviewResult.Review)
        val verified=(valid as NativeOwnerLoadReviewResult.Review)
            .coaching as NativeCurrentLoadReviewResult.Reviewed
        assertEquals(CoachNextLoadReviewStatus.READY_FOR_ATHLETE_REVIEW,
            verified.coaching.status)
        val strict=reader.acceptCurrentApprovedLoad(
            "owner-1","athlete-1",approvals,intent(verified),n("5"),
            CoachReadiness.NORMAL,false,authoring)
        assertTrue(strict.toString(),strict is NativeKotlinCoachAcceptanceResult.Committed)
        val current=checkNotNull(approvals.currentOwnedApproval("owner-1"))
        assertEquals(9L,current.currentSource.programRevision)
        assertEquals(NativeApprovedGymCoherenceResult.Matched,
            NativeKotlinApprovedGymCoherenceDomain.inspect(current,original))
    }

    @Test fun removingConfirmedStockAfterReviewRevokesPendingNativeProgression() {
        enrolled()
        complete(1,1)
        complete(2,1)
        val original=sourceRecord()
        assertEquals(NativeAuthoringSourceWrite.Committed(1),
            authoring.publish("owner-1",0,original))
        val shown=(reader.reviewCurrentApprovedLoad(
            "owner-1","athlete-1",approvals,"db-press",n("5"),
            CoachReadiness.NORMAL,false,authoring)
            as NativeOwnerLoadReviewResult.Review).coaching
            as NativeCurrentLoadReviewResult.Reviewed
        val edited=NativeKotlinGymCatalogEditingDomain.removeStock(
            original,1,"db-press-stock")
        assertTrue(edited.toString(),edited is NativeGymCatalogEdit.Proposed)
        val changed=(edited as NativeGymCatalogEdit.Proposed).source
        assertEquals(NativeAuthoringSourceWrite.Committed(2),
            authoring.publish("owner-1",1,changed))
        val rejected=reader.acceptCurrentApprovedLoad(
            "owner-1","athlete-1",approvals,intent(shown),n("5"),
            CoachReadiness.NORMAL,false,authoring)
        assertEquals(NativeKotlinCoachAcceptanceResult.Rejected(
            NativeKotlinCoachAcceptanceRefusal.GYM_SOURCE_CHANGED),rejected)
        assertEquals(2L,approvals.currentOwnedApproval("owner-1")?.storageRevision)
        assertEquals(NativeOwnerLoadReviewResult.Rejected(
            NativeOwnerLoadReviewRefusal.GYM_SOURCE_CHANGED),
            reader.reviewCurrentApprovedLoad(
                "owner-1","athlete-1",approvals,"db-press",n("5"),
                CoachReadiness.NORMAL,false,authoring))
        // The original issued workout is still accessible for honest
        // history corrections despite a change to today's gym inventory.
        assertTrue(NativeKotlinWorkoutCoordinator(journals,approvals,fence)
            .open("owner-1","workout-2") is NativeJournalFileResult.Durable)
    }

    @Test fun athleteAcceptsExactRecheckedReviewIntoNewRevisionWithoutMutatingIssuedWorkout() {
        enrolled()
        complete(1,1)
        val old=complete(2,1)
        val initial=(review() as NativeOwnerLoadReviewResult.Review)
            .coaching as NativeCurrentLoadReviewResult.Reviewed
        val expected=intent(initial)
        assertEquals(NativeKotlinCoachAcceptanceResult.Rejected(
            NativeKotlinCoachAcceptanceRefusal.NOT_EXPLICITLY_ACCEPTED),
            accept(expected.copy(explicitlyAccepted=false)))
        assertEquals(NativeKotlinCoachAcceptanceResult.Rejected(
            NativeKotlinCoachAcceptanceRefusal.PROPOSAL_CHANGED),
            accept(expected.copy(expectedCandidateLoad=n("40"))))
        assertEquals(NativeKotlinCoachAcceptanceResult.Rejected(
            NativeKotlinCoachAcceptanceRefusal.IDENTIFIER_ALREADY_USED),
            accept(expected.copy(newWorkoutId="workout-1")))
        assertEquals(NativeKotlinCoachAcceptanceResult.Rejected(
            NativeKotlinCoachAcceptanceRefusal.IDENTIFIER_ALREADY_USED),
            accept(expected.copy(newStorageReceiptId="storage-2")))
        assertEquals(2L,approvals.currentOwnedApproval("owner-1")?.storageRevision)
        val saved=accept(expected)
        assertTrue(saved.toString(),saved is NativeKotlinCoachAcceptanceResult.Committed)
        val committed=saved as NativeKotlinCoachAcceptanceResult.Committed
        assertEquals(3L,committed.storageRevision)
        assertEquals(9L,committed.approvedSource.programRevision)
        assertFalse(committed.workoutIssued)
        assertFalse(committed.productionCutover)
        val next=checkNotNull(approvals.currentOwnedApproval("owner-1"))
        assertEquals("workout-3",next.grant.workoutId)
        // A day-local increment must NOT rewrite the default load used
        // by any other hypertrophy training day.
        assertEquals(n("30"),
            ((next.program.exercises.first().equipment as ComposerEquipment.Verified)
                .plannedLoad))
        assertEquals(n("35"),next.policies.single {
            it.exerciseId=="db-press" && it.dayIndex==1
        }.purposeWorkingLoad)
        assertEquals(8L,old.first.snapshot.source.programRevision)
        assertEquals(n("30"),
            ((program.exercises.first().equipment as ComposerEquipment.Verified)
                .plannedLoad))
        val reopened=NativeKotlinWorkoutCoordinator(journals,approvals,fence)
            .open("owner-1","workout-2") as NativeJournalFileResult.Durable
        assertEquals(3L,reopened.journal.snapshot.source.gymRevision)
        assertEquals(8L,reopened.journal.snapshot.source.programRevision)
        assertEquals(NativeKotlinCoachAcceptanceResult.Rejected(
            NativeKotlinCoachAcceptanceRefusal.APPROVAL_CHANGED),accept(expected))
        val issued=NativeKotlinWorkoutCoordinator(journals,approvals,fence)
            .issue("owner-1","workout-3")
        assertTrue(issued.toString(),issued is NativeKotlinIssueResult.Committed)
        val targets=(issued as NativeKotlinIssueResult.Committed).journal.snapshot.targets
        assertTrue(targets.filter { it.exerciseId=="db-press" }.all {
            (it.load as DraftSetLoad.Verified).value.compareTo(n("35"))==0
        })
    }

    @Test fun assumedGoalProgressionSurvivesRealHistoryAcceptanceAndColdOpenWithActualRirNull() {
        enrolled()
        complete(1,1,actualRir=null)
        complete(2,1,actualRir=null)
        val shown=(review() as NativeOwnerLoadReviewResult.Review)
            .coaching as NativeCurrentLoadReviewResult.Reviewed
        assertEquals(CoachNextLoadReviewStatus.READY_FOR_ATHLETE_REVIEW,shown.coaching.status)
        assertEquals(n("35"),shown.coaching.candidateLoad)
        assertTrue(shown.coaching.explanation.contains("assumes their original goal RIR"))
        assertTrue(accept(intent(shown)) is NativeKotlinCoachAcceptanceResult.Committed)
        val cold=KotlinNativeJournalFileStore(root)
        for(id in listOf("workout-1","workout-2")) {
            val restored=cold.openStored("owner-1",id,approvals::canAccessIssuedWorkout)
                as NativeJournalFileResult.Durable
            assertTrue(restored.journal.snapshot.targets.all {it.targetRir==2})
            assertTrue(restored.journal.effectiveSets.values.all {
                (it.operation as KotlinJournalOperation.RecordSet).performed?.actualRir==null
            })
        }
        val issued=NativeKotlinWorkoutCoordinator(journals,approvals,fence)
            .issue("owner-1","workout-3") as NativeKotlinIssueResult.Committed
        assertTrue(issued.journal.effectiveSets.isEmpty())
        assertTrue(issued.journal.snapshot.targets.filter {it.exerciseId=="db-press"}.all {
            (it.load as DraftSetLoad.Verified).value.compareTo(n("35"))==0 && it.targetRir==2
        })
    }

    @Test fun correctionBeforeExplicitAcceptanceInvalidatesPreviouslyShownReview() {
        enrolled()
        complete(1,1)
        val last=complete(2,1)
        val shown=(review() as NativeOwnerLoadReviewResult.Review)
            .coaching as NativeCurrentLoadReviewResult.Reviewed
        val original=last.first.effectiveSets.getValue(last.second)
            .operation.eventId
        val changed=transactions.correct("owner-1","athlete-1","workout-2",
            KotlinSavedSetCorrection("edited-before-accept",0,last.second,original,
                KotlinJournalSetOutcome.COMPLETED,
                KotlinPerformedSet(n("30"),LoadUnit.LB,8,2)))
        assertTrue(changed.toString(),changed is NativeHistoryTransactionResult.Durable)
        assertEquals(NativeKotlinCoachAcceptanceResult.Rejected(
            NativeKotlinCoachAcceptanceRefusal.HISTORY_CHANGED),
            accept(intent(shown)))
        assertEquals(2L,approvals.currentOwnedApproval("owner-1")?.storageRevision)
        val refreshed=intent(shown,expectedHistory=4)
        assertEquals(NativeKotlinCoachAcceptanceResult.Rejected(
            NativeKotlinCoachAcceptanceRefusal.PROPOSAL_CHANGED),
            accept(refreshed))
    }

    @Test fun actualSavedWorkoutCorrectionRevokesEarnedProgressionWithoutBacktracking() {
        val enrollment=transactions.enroll("owner-1","athlete-1") {
            owner,athlete ->
            owner=="owner-1" && athlete=="athlete-1"
        }
        assertTrue(enrollment.toString(),
            enrollment is NativeHistoryTransactionResult.Durable)
        val first=complete(1,1)
        val last=complete(2,1)
        val raw=review()
        assertTrue(raw.toString(),raw is NativeOwnerLoadReviewResult.Review)
        val coaching=(raw as NativeOwnerLoadReviewResult.Review)
            .coaching as NativeCurrentLoadReviewResult.Reviewed
        assertEquals(listOf("workout-1","workout-2"),
            coaching.comparedWorkoutIds)
        assertEquals(CoachNextLoadReviewStatus.READY_FOR_ATHLETE_REVIEW,
            coaching.coaching.status)
        assertEquals(n("35"),coaching.coaching.candidateLoad)

        val originalEvent=last.first.effectiveSets.getValue(last.second)
            .operation.eventId
        val edit=KotlinSavedSetCorrection(
            "edit-latest",0,last.second,originalEvent,
            KotlinJournalSetOutcome.COMPLETED,
            KotlinPerformedSet(n("30"),LoadUnit.LB,8,2))
        val corrected=transactions.correct(
            "owner-1","athlete-1","workout-2",edit)
        assertTrue(corrected.toString(),
            corrected is NativeHistoryTransactionResult.Durable)
        val after=review() as NativeOwnerLoadReviewResult.Review
        val newest=after.coaching as NativeCurrentLoadReviewResult.Reviewed
        assertEquals(listOf("workout-1","workout-2"),
            newest.comparedWorkoutIds)
        assertNotEquals(CoachNextLoadReviewStatus.READY_FOR_ATHLETE_REVIEW,
            newest.coaching.status)
        assertNull(newest.coaching.candidateLoad)
        assertEquals(n("30"),
            ((program.exercises.first().equipment as ComposerEquipment.Verified)
                .plannedLoad))
        assertFalse(newest.coaching.maySaveProgram)
        assertTrue(first.first.snapshot.targets.isNotEmpty())
    }

    @Test fun ownerDiskHistoryInterleavedPurposeRepReviewToIssuedSession() {
        enrolled()
        val mixed=policies.map {p ->
            if(p.exerciseId=="db-press" &&
                p.purpose==ComposerSessionPurpose.STRENGTH_SKILL)
                p.copy(purposeWorkingLoad=n("25")) else p
        }
        val strength=complete(1,0,mixed)
        val volume=complete(2,1,mixed)
        assertTrue(strength.first.snapshot.targets.filter {
            it.exerciseId=="db-press" && it.type==DraftSetType.WORKING
        }.all { (it.load as DraftSetLoad.Verified).value==n("25") })
        assertTrue(volume.first.snapshot.targets.filter {
            it.exerciseId=="db-press" && it.type==DraftSetType.WORKING
        }.all { (it.load as DraftSetLoad.Verified).value==n("30") })
        val key=strength.second
        val originalEvent=strength.first.effectiveSets.getValue(key).operation.eventId
        assertTrue(transactions.correct(
            "owner-1","athlete-1","workout-1",
            KotlinSavedSetCorrection("strength-correction",0,key,
                originalEvent,KotlinJournalSetOutcome.COMPLETED,
                KotlinPerformedSet(n("25"),LoadUnit.LB,11,3)))
            is NativeHistoryTransactionResult.Durable)

        val approval=NativeKotlinWorkoutApproval(
            "owner-1","athlete-1",source,source,
            KotlinWorkoutSelectionGrant("grant-3","workout-3",source,0),
            program,mixed,"storage-3",3)
        assertEquals(NativeApprovalWriteResult.Committed(3),
            approvals.publish("owner-1",2,approval))
        assertEquals(NativeAuthoringSourceWrite.Committed(1),
            authoring.publish("owner-1",0,sourceRecord()))
        val current=reader.reviewCurrentNextSetTargets(
            "owner-1","athlete-1",approvals,"db-press",
            CoachReadiness.NORMAL,false,authoring)
        assertTrue(current.toString(),current is NativeOwnerNextSetResult.Review)
        val scoped=current as NativeOwnerNextSetResult.Review
        val reviewed=scoped.candidate as NativeCurrentRepPrescriptionResult.Reviewed
        assertEquals("workout-1",reviewed.priorWorkoutId)
        val candidate=reviewed.proposal as AdaptiveSetPrescriptionResult.Candidate
        assertEquals(listOf(11,12),candidate.observedReps)
        assertEquals(listOf(12,12),candidate.newRepTargets)
        assertTrue(candidate.workout.targets.filter {
            it.exerciseId=="db-press" && it.type==DraftSetType.WORKING
        }.all {(it.load as DraftSetLoad.Verified).value==n("25")})

        val intent=NativeKotlinAcceptedRepIntent(
            "db-press",scoped.approvalRevision,reviewed.historyRevision,
            "workout-3",candidate.historicalWorkoutId,
            candidate.editedSetKey,candidate.observedReps,candidate.newRepTargets,
            "workout-4","grant-4","storage-4",true)
        assertTrue(acceptRep(intent) is NativeKotlinRepAcceptanceResult.Committed)
        val issued=repIssuer().issue("owner-1","workout-4")
        assertTrue(issued.toString(),issued is NativeKotlinIssueResult.Committed)
        val saved=(issued as NativeKotlinIssueResult.Committed).journal
        assertEquals(listOf(RepBand(12,12),RepBand(12,12)),
            saved.snapshot.targets.filter {
                it.exerciseId=="db-press" && it.type==DraftSetType.WORKING
            }.map {it.reps})
        assertTrue(saved.snapshot.targets.filter {
            it.exerciseId=="db-press" && it.type==DraftSetType.WORKING
        }.all {(it.load as DraftSetLoad.Verified).value==n("25")})
        assertTrue(repIssuer().open("owner-1","workout-1")
            is NativeJournalFileResult.Durable)
        assertTrue(repIssuer().open("owner-1","workout-2")
            is NativeJournalFileResult.Durable)
        assertEquals(NativeOwnerNextSetResult.Rejected(
            NativeOwnerNextSetRefusal.ALREADY_ISSUED_OR_CONFLICT),
            reader.reviewCurrentNextSetTargets(
                "owner-1","athlete-1",approvals,"db-press",
                CoachReadiness.NORMAL,false,authoring))
    }


    @Test fun diskBackedPurposeSpecificLoadProgressionIssuesOnlyStrengthChange() {
        enrolled()
        val mixed=policies.map {p ->
            if(p.exerciseId=="db-press" &&
                p.purpose==ComposerSessionPurpose.STRENGTH_SKILL)
                p.copy(purposeWorkingLoad=n("25")) else p
        }
        val first=complete(1,0,mixed)
        val intervening=complete(2,1,mixed)
        val third=complete(3,0,mixed)
        val next=NativeKotlinWorkoutApproval(
            "owner-1","athlete-1",source,source,
            KotlinWorkoutSelectionGrant("grant-4","workout-4",source,0),
            program,mixed,"storage-4",4)
        assertEquals(NativeApprovalWriteResult.Committed(4),
            approvals.publish("owner-1",3,next))
        val result=reader.reviewCurrentApprovedLoad(
            "owner-1","athlete-1",approvals,"db-press",n("5"),
            CoachReadiness.NORMAL,false)
        assertTrue(result.toString(),result is NativeOwnerLoadReviewResult.Review)
        val presented=(result as NativeOwnerLoadReviewResult.Review)
            .coaching as NativeCurrentLoadReviewResult.Reviewed
        assertEquals(listOf("workout-1","workout-3"),
            presented.comparedWorkoutIds)
        assertEquals(CoachNextLoadReviewStatus.READY_FOR_ATHLETE_REVIEW,
            presented.coaching.status)
        assertEquals(n("25"),presented.coaching.originalLoad)
        assertEquals(n("30"),presented.coaching.candidateLoad)
        assertEquals(listOf(0),presented.coaching.affectedDayIndices)
        val expected=NativeKotlinCoachLoadAcceptanceIntent(
            "db-press",4,presented.historyRevision,
            presented.comparedWorkoutIds,CoachNextLoadChangeKind.EARNED_INCREMENT,
            n("25"),n("30"),listOf(0),
            "workout-5","grant-5","storage-5",0,true)
        assertEquals(NativeKotlinCoachAcceptanceResult.Rejected(
            NativeKotlinCoachAcceptanceRefusal.NOT_EXPLICITLY_ACCEPTED),
            accept(expected.copy(explicitlyAccepted=false)))
        assertTrue(accept(expected) is NativeKotlinCoachAcceptanceResult.Committed)
        val restored=checkNotNull(approvals.currentOwnedApproval("owner-1"))
        assertEquals(n("30"),restored.policies.single {
            it.exerciseId=="db-press" &&
                it.purpose==ComposerSessionPurpose.STRENGTH_SKILL &&
                it.dayIndex==0
        }.purposeWorkingLoad)
        assertNull(restored.policies.single {
            it.exerciseId=="db-press" &&
                it.purpose==ComposerSessionPurpose.HYPERTROPHY
        }.purposeWorkingLoad)
        assertEquals(n("30"),(restored.program.exercises.first()
            .equipment as ComposerEquipment.Verified).plannedLoad)
        val issued=NativeKotlinWorkoutCoordinator(journals,approvals,fence)
            .issue("owner-1","workout-5")
        assertTrue(issued.toString(),issued is NativeKotlinIssueResult.Committed)
        assertTrue((issued as NativeKotlinIssueResult.Committed).journal
            .snapshot.targets.filter {
                it.exerciseId=="db-press" && it.type==DraftSetType.WORKING
            }.all { (it.load as DraftSetLoad.Verified).value==n("30") })
        assertTrue(first.first.snapshot.targets.filter {
            it.exerciseId=="db-press" && it.type==DraftSetType.WORKING
        }.all { (it.load as DraftSetLoad.Verified).value==n("25") })
        assertTrue(intervening.first.snapshot.targets.filter {
            it.exerciseId=="db-press" && it.type==DraftSetType.WORKING
        }.all { (it.load as DraftSetLoad.Verified).value==n("30") })
        assertTrue(third.first.snapshot.targets.filter {
            it.exerciseId=="db-press" && it.type==DraftSetType.WORKING
        }.all { (it.load as DraftSetLoad.Verified).value==n("25") })
    }


    @Test fun diskHistoryCanProgressDefaultHypertrophyWithoutChangingStrengthWeight() {
        enrolled()
        val mixed=policies.map {p ->
            if(p.exerciseId=="db-press" &&
                p.purpose==ComposerSessionPurpose.STRENGTH_SKILL)
                p.copy(purposeWorkingLoad=n("25")) else p
        }
        val first=complete(1,1,mixed)
        val strength=complete(2,0,mixed)
        val latest=complete(3,1,mixed)
        val next=NativeKotlinWorkoutApproval(
            "owner-1","athlete-1",source,source,
            KotlinWorkoutSelectionGrant("grant-4","workout-4",source,1),
            program,mixed,"storage-4",4)
        assertEquals(NativeApprovalWriteResult.Committed(4),
            approvals.publish("owner-1",3,next))
        val current=reader.reviewCurrentApprovedLoad(
            "owner-1","athlete-1",approvals,"db-press",n("5"),
            CoachReadiness.NORMAL,false)
        assertTrue(current.toString(),current is NativeOwnerLoadReviewResult.Review)
        val coaching=(current as NativeOwnerLoadReviewResult.Review)
            .coaching as NativeCurrentLoadReviewResult.Reviewed
        assertEquals(listOf("workout-1","workout-3"),coaching.comparedWorkoutIds)
        assertEquals(CoachNextLoadReviewStatus.READY_FOR_ATHLETE_REVIEW,
            coaching.coaching.status)
        val intent=NativeKotlinCoachLoadAcceptanceIntent(
            "db-press",4,coaching.historyRevision,coaching.comparedWorkoutIds,
            CoachNextLoadChangeKind.EARNED_INCREMENT,n("30"),n("35"),
            listOf(1),"workout-5","grant-5","storage-5",1,true)
        assertTrue(accept(intent) is NativeKotlinCoachAcceptanceResult.Committed)
        val stored=checkNotNull(approvals.currentOwnedApproval("owner-1"))
        assertEquals(n("25"),stored.policies.single {p ->
            p.exerciseId=="db-press" &&
                p.purpose==ComposerSessionPurpose.STRENGTH_SKILL
        }.purposeWorkingLoad)
        assertEquals(n("35"),stored.policies.single {p ->
            p.exerciseId=="db-press" &&
                p.purpose==ComposerSessionPurpose.HYPERTROPHY &&
                p.dayIndex==1
        }.purposeWorkingLoad)
        assertNull(stored.policies.single {p ->
            p.exerciseId=="db-press" &&
                p.purpose==ComposerSessionPurpose.HYPERTROPHY &&
                p.dayIndex==null
        }.purposeWorkingLoad)
        assertEquals(n("30"),(stored.program.exercises.first()
            .equipment as ComposerEquipment.Verified).plannedLoad)
        val issued=NativeKotlinWorkoutCoordinator(journals,approvals,fence)
            .issue("owner-1","workout-5")
        assertTrue(issued.toString(),issued is NativeKotlinIssueResult.Committed)
        assertTrue((issued as NativeKotlinIssueResult.Committed).journal
            .snapshot.targets.filter {
                it.exerciseId=="db-press" && it.type==DraftSetType.WORKING
            }.all {(it.load as DraftSetLoad.Verified).value==n("35")})
        assertTrue(strength.first.snapshot.targets.filter {
            it.exerciseId=="db-press" && it.type==DraftSetType.WORKING
        }.all {(it.load as DraftSetLoad.Verified).value==n("25")})
        assertTrue(listOf(first,latest).all {saved ->
            saved.first.snapshot.targets.filter {
                it.exerciseId=="db-press" && it.type==DraftSetType.WORKING
            }.all {(it.load as DraftSetLoad.Verified).value==n("30")}
        })
    }


    @Test fun diskVerifiedUpperAndPushSamePurposeProgressIndependently() {
        enrolled()
        val policiesByDay=policies+policies.single {p ->
            p.exerciseId=="db-press" &&
                p.purpose==ComposerSessionPurpose.HYPERTROPHY
        }.copy(dayIndex=2,purposeWorkingLoad=n("25"))
        val upperFirst=complete(1,1,policiesByDay)
        val push=complete(2,2,policiesByDay)
        val upperLast=complete(3,1,policiesByDay)
        assertTrue(push.first.snapshot.targets.filter {
            it.exerciseId=="db-press" && it.type==DraftSetType.WORKING
        }.all {(it.load as DraftSetLoad.Verified).value==n("25")})
        val next=NativeKotlinWorkoutApproval(
            "owner-1","athlete-1",source,source,
            KotlinWorkoutSelectionGrant("grant-4","workout-4",source,1),
            program,policiesByDay,"storage-4",4)
        assertEquals(NativeApprovalWriteResult.Committed(4),
            approvals.publish("owner-1",3,next))
        val shown=reader.reviewCurrentApprovedLoad(
            "owner-1","athlete-1",approvals,"db-press",n("5"),
            CoachReadiness.NORMAL,false)
        assertTrue(shown.toString(),shown is NativeOwnerLoadReviewResult.Review)
        val reviewed=(shown as NativeOwnerLoadReviewResult.Review)
            .coaching as NativeCurrentLoadReviewResult.Reviewed
        assertEquals(listOf("workout-1","workout-3"),reviewed.comparedWorkoutIds)
        assertEquals(CoachNextLoadReviewStatus.READY_FOR_ATHLETE_REVIEW,
            reviewed.coaching.status)
        assertEquals(listOf(1),reviewed.coaching.affectedDayIndices)
        val accepted=NativeKotlinCoachLoadAcceptanceIntent(
            "db-press",4,reviewed.historyRevision,reviewed.comparedWorkoutIds,
            CoachNextLoadChangeKind.EARNED_INCREMENT,n("30"),n("35"),
            listOf(1),"workout-5","grant-5","storage-5",1,true)
        assertTrue(accept(accepted) is NativeKotlinCoachAcceptanceResult.Committed)
        val stored=checkNotNull(approvals.currentOwnedApproval("owner-1"))
        assertEquals(n("35"),stored.policies.single {
            it.exerciseId=="db-press" && it.dayIndex==1
        }.purposeWorkingLoad)
        assertEquals(n("25"),stored.policies.single {
            it.exerciseId=="db-press" && it.dayIndex==2
        }.purposeWorkingLoad)
        val issued=NativeKotlinWorkoutCoordinator(journals,approvals,fence)
            .issue("owner-1","workout-5")
        assertTrue(issued.toString(),issued is NativeKotlinIssueResult.Committed)
        assertTrue((issued as NativeKotlinIssueResult.Committed).journal
            .snapshot.targets.filter {
                it.exerciseId=="db-press" && it.type==DraftSetType.WORKING
            }.all {(it.load as DraftSetLoad.Verified).value==n("35")})
        assertTrue(upperFirst.first.snapshot.targets.filter {
            it.exerciseId=="db-press" && it.type==DraftSetType.WORKING
        }.all {(it.load as DraftSetLoad.Verified).value==n("30")})
        assertTrue(upperLast.first.snapshot.targets.filter {
            it.exerciseId=="db-press" && it.type==DraftSetType.WORKING
        }.all {(it.load as DraftSetLoad.Verified).value==n("30")})
        assertTrue(push.first.snapshot.targets.filter {
            it.exerciseId=="db-press" && it.type==DraftSetType.WORKING
        }.all {(it.load as DraftSetLoad.Verified).value==n("25")})
    }


    @Test fun diskVerifiedDefaultLoadingUsesIndependentDayEvidenceAndApproval() {
        enrolled()
        val upperFirst=complete(1,1)
        val push=complete(2,2)
        // Two successes on different scheduled days do NOT authorize the
        // upcoming Push-day increment, even though their exercise, reps,
        // purpose and default exercise-wide load policy are identical.
        val unavailable=reader.reviewCurrentApprovedLoad(
            "owner-1","athlete-1",approvals,"db-press",n("5"),
            CoachReadiness.NORMAL,false)
        assertTrue(unavailable.toString(),
            unavailable is NativeOwnerLoadReviewResult.Review)
        assertEquals(NativeCurrentLoadReviewResult.Rejected(
            NativeCurrentLoadReviewRefusal.NOT_ENOUGH_EXERCISE_HISTORY),
            (unavailable as NativeOwnerLoadReviewResult.Review).coaching)

        val upperLast=complete(3,1)
        val shown=reader.reviewCurrentApprovedLoad(
            "owner-1","athlete-1",approvals,"db-press",n("5"),
            CoachReadiness.NORMAL,false)
        val proposed=(shown as NativeOwnerLoadReviewResult.Review)
            .coaching as NativeCurrentLoadReviewResult.Reviewed
        assertEquals(listOf("workout-1","workout-3"),
            proposed.comparedWorkoutIds)
        assertEquals(CoachNextLoadReviewStatus.READY_FOR_ATHLETE_REVIEW,
            proposed.coaching.status)
        assertEquals(listOf(1),proposed.coaching.affectedDayIndices)
        val confirm=NativeKotlinCoachLoadAcceptanceIntent(
            "db-press",3,proposed.historyRevision,proposed.comparedWorkoutIds,
            CoachNextLoadChangeKind.EARNED_INCREMENT,n("30"),n("35"),
            listOf(1),"workout-4","grant-4","storage-4",2,true)
        assertTrue(accept(confirm) is NativeKotlinCoachAcceptanceResult.Committed)
        val stored=checkNotNull(approvals.currentOwnedApproval("owner-1"))
        assertEquals(n("30"),(stored.program.exercises.first().equipment
            as ComposerEquipment.Verified).plannedLoad)
        assertEquals(n("35"),stored.policies.single {
            it.exerciseId=="db-press" && it.dayIndex==1
        }.purposeWorkingLoad)
        assertNull(stored.policies.single {
            it.exerciseId=="db-press" &&
                it.purpose==ComposerSessionPurpose.HYPERTROPHY &&
                it.dayIndex==null
        }.purposeWorkingLoad)
        val pushIssued=NativeKotlinWorkoutCoordinator(journals,approvals,fence)
            .issue("owner-1","workout-4")
        assertTrue(pushIssued.toString(),
            pushIssued is NativeKotlinIssueResult.Committed)
        assertTrue((pushIssued as NativeKotlinIssueResult.Committed)
            .journal.snapshot.targets.filter {
                it.exerciseId=="db-press" && it.type==DraftSetType.WORKING
            }.all {(it.load as DraftSetLoad.Verified).value==n("30")})
        assertTrue(listOf(upperFirst,push,upperLast).all { recorded ->
            recorded.first.snapshot.targets.filter {
                it.exerciseId=="db-press" && it.type==DraftSetType.WORKING
            }.all {(it.load as DraftSetLoad.Verified).value==n("30")}
        })
    }


    private fun verifiedBackoffPolicies()=policies.map {p->
        if(p.exerciseId=="db-press" &&
            p.purpose==ComposerSessionPurpose.STRENGTH_SKILL)
            p.copy(scheme=SetLoadScheme.LAST_SET_BACKOFF,
                maximumAuthorizedBackoff=n("5"),
                backoffReps=p.workingReps,backoffRir=p.targetRir)
        else p
    }

    private fun backoffReadyHistory():NativeKotlinAcceptedRepIntent {
        enrolled()
        val samePolicy=verifiedBackoffPolicies()
        val (finished,_) = complete(1,0,samePolicy)
        val press=finished.snapshot.targets.filter {
            it.exerciseId=="db-press" && it.type==DraftSetType.WORKING
        }.sortedBy {it.ordinal}
        assertEquals(2,press.size)
        assertEquals(listOf(n("30"),n("25")),
            press.map{(it.load as DraftSetLoad.Verified).value})
        val backoff=press.last()
        val sourceEvent=finished.effectiveSets.getValue(backoff.setKey)
            .operation.eventId
        assertTrue(transactions.correct(
            "owner-1","athlete-1","workout-1",
            KotlinSavedSetCorrection(
                "verified-backoff-correction",0,backoff.setKey,sourceEvent,
                KotlinJournalSetOutcome.COMPLETED,
                KotlinPerformedSet(n("25"),LoadUnit.LB,11,4)))
            is NativeHistoryTransactionResult.Durable)
        val subsequent=NativeKotlinWorkoutApproval(
            "owner-1","athlete-1",source,source,
            KotlinWorkoutSelectionGrant("grant-2","workout-2",source,0),
            program,samePolicy,"storage-2",2)
        assertEquals(NativeApprovalWriteResult.Committed(2),
            approvals.publish("owner-1",1,subsequent))
        assertEquals(NativeAuthoringSourceWrite.Committed(1),
            authoring.publish("owner-1",0,sourceRecord()))
        val display=reader.reviewCurrentNextSetTargets(
            "owner-1","athlete-1",approvals,"db-press",
            CoachReadiness.NORMAL,false,authoring)
        assertTrue(display.toString(),display is NativeOwnerNextSetResult.Review)
        val reviewed=display as NativeOwnerNextSetResult.Review
        val context=reviewed.candidate
            as NativeCurrentRepPrescriptionResult.Reviewed
        val proposal=context.proposal as AdaptiveSetPrescriptionResult.Candidate
        assertEquals("workout-1",proposal.historicalWorkoutId)
        assertEquals(listOf(12,11),proposal.observedReps)
        assertEquals(listOf(12,12),proposal.newRepTargets)
        assertEquals(backoff.setKey,proposal.editedSetKey)
        assertEquals(listOf(n("30"),n("25")),
            proposal.workout.targets.filter{
                it.exerciseId=="db-press" && it.type==DraftSetType.WORKING
            }.sortedBy {it.ordinal}
                .map{(it.load as DraftSetLoad.Verified).value})
        return NativeKotlinAcceptedRepIntent(
            "db-press",reviewed.approvalRevision,context.historyRevision,
            "workout-2",proposal.historicalWorkoutId,proposal.editedSetKey,
            proposal.observedReps,proposal.newRepTargets,
            "workout-3","grant-3","storage-3",true)
    }

    @Test fun realCorrectedBackoffJournalToConsentToNativeIssuedNextWorkout() {
        val intent=backoffReadyHistory()
        assertEquals(NativeKotlinRepAcceptanceResult.Rejected(
            NativeKotlinRepAcceptanceRefusal.NOT_ACCEPTED),
            acceptRep(intent.copy(explicitlyAccepted=false)))
        assertEquals(NativeKotlinRepAcceptanceResult.Rejected(
            NativeKotlinRepAcceptanceRefusal.PROPOSAL_CHANGED),
            acceptRep(intent.copy(expectedNewRepTargets=listOf(12,11))))
        val recorded=acceptRep(intent)
        assertTrue(recorded.toString(),
            recorded is NativeKotlinRepAcceptanceResult.Committed)
        assertEquals(3L,(recorded as NativeKotlinRepAcceptanceResult.Committed)
            .approvalRevision)
        assertEquals(NativeKotlinIssueResult.Rejected(
            NativeKotlinIssueRefusal.WORKOUT_UNRESOLVED,
            KotlinSnapshotRejection.UNVERIFIED_TARGET),
            NativeKotlinWorkoutCoordinator(journals,approvals,fence)
                .issue("owner-1","workout-3"))
        val issuer=repIssuer()
        val result=issuer.issue("owner-1","workout-3")
        assertTrue(result.toString(),result is NativeKotlinIssueResult.Committed)
        val journal=(result as NativeKotlinIssueResult.Committed).journal
        val press=journal.snapshot.targets.filter{
            it.exerciseId=="db-press" && it.type==DraftSetType.WORKING
        }.sortedBy{it.ordinal}
        assertEquals(listOf(n("30"),n("25")),
            press.map{(it.load as DraftSetLoad.Verified).value})
        assertEquals(listOf(RepBand(12,12),RepBand(12,12)),
            press.map{it.reps})
        assertEquals(listOf(2,2),press.map{it.targetRir})
        assertTrue(journal.snapshot.targets.filter{
            it.exerciseId=="db-row" && it.type==DraftSetType.WORKING
        }.all{it.reps==RepBand(8,12)})
        assertEquals(journal,
            (repIssuer().open("owner-1","workout-3")
                as NativeJournalFileResult.Durable).journal)
        val former=repIssuer().open("owner-1","workout-1")
            as NativeJournalFileResult.Durable
        assertEquals(RepBand(8,12),
            former.journal.snapshot.targets.first{
                it.exerciseId=="db-press" &&
                    it.type==DraftSetType.WORKING
            }.reps)
        assertEquals(listOf(n("30"),n("25")),
            former.journal.snapshot.targets.filter{
                it.exerciseId=="db-press" && it.type==DraftSetType.WORKING
            }.sortedBy{it.ordinal}.map{
                (it.load as DraftSetLoad.Verified).value
            })
        assertEquals(NativeOwnerNextSetResult.Rejected(
            NativeOwnerNextSetRefusal.ALREADY_ISSUED_OR_CONFLICT),
            reader.reviewCurrentNextSetTargets(
                "owner-1","athlete-1",approvals,"db-press",
                CoachReadiness.NORMAL,false,authoring))
    }

    @Test fun correctedBackoffAfterConsentInvalidatesUnissuedNextWorkout() {
        val intent=backoffReadyHistory()
        assertTrue(acceptRep(intent) is NativeKotlinRepAcceptanceResult.Committed)
        val past=repIssuer().open("owner-1","workout-1")
            as NativeJournalFileResult.Durable
        val last=past.journal.snapshot.targets.last{
            it.exerciseId=="db-press" && it.type==DraftSetType.WORKING
        }
        val changed=transactions.correct(
            "owner-1","athlete-1","workout-1",
            KotlinSavedSetCorrection(
                "backoff-revoked-after-consent",1,last.setKey,
                "verified-backoff-correction",
                KotlinJournalSetOutcome.COMPLETED,
                KotlinPerformedSet(n("25"),LoadUnit.LB,9,1)))
        assertTrue(changed.toString(),changed is NativeHistoryTransactionResult.Durable)
        assertEquals(NativeKotlinIssueResult.Rejected(
            NativeKotlinIssueRefusal.WORKOUT_UNRESOLVED,
            KotlinSnapshotRejection.UNVERIFIED_TARGET),
            repIssuer().issue("owner-1","workout-3"))
        assertEquals(NativeJournalFileResult.Rejected(
            NativeJournalFileRefusal.NOT_ISSUED),
            journals.openStored("owner-1","workout-3",
                approvals::canAccessIssuedWorkout))
        assertEquals(listOf("workout-1"),
            (reader.read("owner-1","athlete-1")
                as NativeKotlinMixedHistoryReadResult.Review)
                .timeline.completions.map{it.manifest.workoutId})
    }


    @Test fun completedPersonalizedBackoffRetainsExactLoadsAndRepeatsMissedRepTarget() {
        val intent=backoffReadyHistory()
        assertTrue(acceptRep(intent) is NativeKotlinRepAcceptanceResult.Committed)
        var inProgress=(repIssuer().issue("owner-1","workout-3")
            as NativeKotlinIssueResult.Committed).journal
        val issuedBackoff=inProgress.snapshot.targets.filter {
            it.exerciseId=="db-press" && it.type==DraftSetType.WORKING
        }.sortedBy{it.ordinal}
        assertEquals(listOf(n("30"),n("25")),
            issuedBackoff.map{(it.load as DraftSetLoad.Verified).value})
        assertEquals(listOf(12,12),issuedBackoff.map{it.reps.minimum})
        for((i,set) in inProgress.snapshot.targets.withIndex()) {
            val stock=(set.load as DraftSetLoad.Verified)
            val reps=if(set.setKey==issuedBackoff.last().setKey)11
                else set.reps.maximum
            val written=journals.appendStored(
                "owner-1","workout-3",
                KotlinJournalOperation.RecordSet(
                    "backoff-next-log-$i",inProgress.revision,set.setKey,
                    KotlinJournalSetOutcome.COMPLETED,
                    KotlinPerformedSet(stock.value,stock.unit,reps,3)),
                approvals::canAccessIssuedWorkout)
            assertTrue(written.toString(),written is NativeJournalFileResult.Durable)
            inProgress=(written as NativeJournalFileResult.Durable).journal
        }
        assertTrue(transactions.complete(
            "owner-1","athlete-1","workout-3",
            KotlinJournalOperation.Finish(
                "backoff-repeat-finish",inProgress.revision))
            is NativeHistoryTransactionResult.Durable)
        val evidence=(reader.read("owner-1","athlete-1")
            as NativeKotlinMixedHistoryReadResult.Review)
            .timeline.completions.last()
            as NativeKotlinMixedHistoryCompletion.Composer
        assertEquals(listOf(n("30"),n("25")),
            evidence.evidence.exerciseReviews.single{
                it.exerciseId=="db-press"
            }.workingSets.map{(it.issuedLoad as DraftSetLoad.Verified).value})
        assertEquals(listOf(RepBand(12,12),RepBand(12,12)),
            evidence.evidence.exerciseReviews.single{
                it.exerciseId=="db-press"
            }.workingSets.map{it.issuedReps})
        val current=checkNotNull(approvals.currentOwnedApproval("owner-1"))
        assertEquals(NativeApprovalWriteResult.Committed(4),
            approvals.publish("owner-1",3,current.copy(
                grant=current.grant.copy(
                    workoutId="workout-4",grantId="grant-4"),
                storageReceiptId="storage-4",storageRevision=4,
                acceptedRepTargets=null)))
        val review=reader.reviewCurrentNextSetTargets(
            "owner-1","athlete-1",approvals,"db-press",
            CoachReadiness.NORMAL,false,authoring)
        assertTrue(review.toString(),review is NativeOwnerNextSetResult.Review)
        val verified=(review as NativeOwnerNextSetResult.Review)
            .candidate as NativeCurrentRepPrescriptionResult.Reviewed
        val proposal=verified.proposal as AdaptiveSetPrescriptionResult.Candidate
        assertEquals("workout-3",proposal.historicalWorkoutId)
        assertEquals(AdaptiveRepDecisionKind.REPEAT_PREVIOUSLY_ISSUED_TARGETS,
            proposal.decisionKind)
        assertEquals(listOf(12,11),proposal.observedReps)
        assertEquals(listOf(12,12),proposal.newRepTargets)
        assertEquals(listOf(n("30"),n("25")),
            proposal.workout.targets.filter{
                it.exerciseId=="db-press" && it.type==DraftSetType.WORKING
            }.sortedBy{it.ordinal}.map{
                (it.load as DraftSetLoad.Verified).value
            })
        assertEquals(NativeJournalFileResult.Rejected(
            NativeJournalFileRefusal.NOT_ISSUED),
            journals.openStored("owner-1","workout-4",
                approvals::canAccessIssuedWorkout))
    }

}
