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

    private fun accept(id:Int,day:Int):KotlinWorkoutJournalState {
        val grant=KotlinWorkoutSelectionGrant(
            "grant-$id","workout-$id",source,day)
        val approval=NativeKotlinWorkoutApproval(
            "owner-1","athlete-1",source,source,grant,program,policies,
            "storage-$id",id.toLong())
        assertEquals(NativeApprovalWriteResult.Committed(id.toLong()),
            approvals.publish("owner-1",(id-1).toLong(),approval))
        val issued=NativeKotlinWorkoutCoordinator(journals,approvals,fence)
            .issue("owner-1","workout-$id")
        assertTrue(issued.toString(),issued is NativeKotlinIssueResult.Committed)
        return (issued as NativeKotlinIssueResult.Committed).journal
    }
    private fun complete(id:Int,day:Int):Pair<KotlinWorkoutJournalState,String> {
        var journal=accept(id,day)
        var pressKey=""
        for ((index,target) in journal.snapshot.targets.withIndex()) {
            val load=(target.load as DraftSetLoad.Verified)
            if(target.exerciseId=="db-press" &&
                target.type==DraftSetType.WORKING && pressKey.isEmpty())
                pressKey=target.setKey
            val event=KotlinJournalOperation.RecordSet(
                "set-$id-$index",journal.revision,target.setKey,
                KotlinJournalSetOutcome.COMPLETED,
                KotlinPerformedSet(load.value,load.unit,12,2))
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
        assertEquals("workout-4",following.workout.workoutId)
        assertEquals(listOf(RepBand(12,12),RepBand(12,12)),
            following.workout.targets.filter {
                it.exerciseId=="db-press" && it.type==DraftSetType.WORKING
            }.map {it.reps})
        assertFalse(following.mayIssueWorkout)
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
        complete(2,2)
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
        complete(2,2)
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
        val old=complete(2,2)
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
        assertEquals(n("35"),
            ((next.program.exercises.first().equipment as ComposerEquipment.Verified)
                .plannedLoad))
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

    @Test fun correctionBeforeExplicitAcceptanceInvalidatesPreviouslyShownReview() {
        enrolled()
        complete(1,1)
        val last=complete(2,2)
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
        val last=complete(2,2)
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
}
