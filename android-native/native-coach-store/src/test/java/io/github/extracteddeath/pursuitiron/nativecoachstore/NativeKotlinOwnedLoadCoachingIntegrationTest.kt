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
            maximumBarbellExercisesPerDay=0)
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
            approvals,authoring)
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
