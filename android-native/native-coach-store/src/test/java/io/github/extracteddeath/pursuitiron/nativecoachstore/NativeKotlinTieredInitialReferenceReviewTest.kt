package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class NativeKotlinTieredInitialReferenceReviewTest {
    @get:Rule val temp=TemporaryFolder()
    private var active:NativeKotlinOwnerIdentity?=
        NativeKotlinOwnerIdentity("owner-1","athlete-1")
    private val identity=NativeKotlinCurrentIdentity{active}
    private val fence by lazy{NativeKotlinApprovalRevisionFence(temp.root.toPath())}
    private val drafts by lazy{NativeCustomCycleDraftFileStore(
        temp.root.toPath(),fence,identity)}
    private val sources by lazy{NativeKotlinCustomAuthoringSourceFileStore(
        temp.root.toPath(),fence,identity)}
    private fun store()=NativeKotlinCustomCycleSignoffFileStore(
        temp.root.toPath(),fence,identity,drafts,sources)
    private val permissionStore by lazy{NativeKotlinAdaptiveCyclePermissionFileStore(
        temp.root.toPath(),fence,identity)}
    private fun n(value:String)=BigDecimal(value)
    private fun setup()=CoachAthleteSetupDomain.review(AthleteSetupDraft(
        profileRevision="rev-1",athleteProfileId="athlete-1",
        goal=AthleteTrainingGoal.HYPERTROPHY,
        experience=AthleteExperience.ADVANCED,
        split=ComposerSplit.FULL_BODY,
        trainingWeekdays=setOf(0,2,4),timezoneId="America/New_York",
        sessionTimeBucket=SessionTimeBucket.FORTY_TO_SIXTY,
        hardSessionLimit=HardSessionLimit.Minutes(60),
        gym=GymProfileDraft("gym-1",LoadUnit.LB,
            mapOf("straight-bar" to VerifiedLoadStock.Barbell(LoadUnit.LB,n("10"),
                mapOf(n("5") to 2,n("2.5") to 2))),
            setOf("flat-bench","rack"),emptySet()),
        allowBodyweight=true,allowSupersets=false,
        allowTricepsOnLower=false,maxBarbellExercisesPerDay=1,
        focusedMuscles=emptySet(),reducedMuscles=emptySet(),
        excludedExerciseIds=emptySet(),lockedTrainingDays=emptySet(),
        hardTrainingDays=emptySet(),longTrainingDays=emptySet(),
        declaredRestDays=setOf(1,3,5,6)
    ))
    private fun source(rev:Long=1)=NativeCustomAuthoringSnapshot(
        "owner-1","athlete-1",rev,setup(),StarterExerciseTranche.movements())
    private fun set()=CustomAuthoredSet(DraftSetType.WORKING,
        DraftSetLoad.Verified(LoadUnit.LB,LoadBasis.BARBELL_TOTAL,n("20")),
        RepBand(10,15),2,55,75)
    private fun cycle()=CustomTrainingCycle(
        "cycle-1",1,"athlete-1",AthleteTrainingGoal.HYPERTROPHY,
        "My custom cycle",listOf(CustomTrainingBlock(
            "base","Foundation",TrainingPhase.HYPERTROPHY,
            CustomBlockPurpose.TRAINING,3,
            CustomSplitIdentity.Freeform("Custom core"),listOf(0,2,4).map { day->
                CustomTrainingDay(day,"Day $day",ComposerSessionPurpose.HYPERTROPHY,
                    listOf(CustomExerciseSlot("slot-$day","barbell_bench",
                        ComposerRole.PRIMARY,listOf(set()))))
            }
        )))
    private fun bounds()=TrainingMuscle.entries.map{muscle->
        val main=muscle==TrainingMuscle.CHEST
        DeclaredMuscleDoseBounds(muscle,n(if(main)"3" else "0"),
            n(if(main)"4" else "0"),n("5"),if(main)3 else 0)
    }
    private fun policy()=AuthoredGoalProgramPolicy(
        policyId="coach-1",revision="policy-1",
        rationale="Authored training and recovery review",
        athleteId="athlete-1",athleteRevision="rev-1",
        goal=AthleteTrainingGoal.HYPERTROPHY,
        experience=AthleteExperience.ADVANCED,
        split=ComposerSplit.FULL_BODY,phase=TrainingPhase.HYPERTROPHY,
        trainingDays=3,baselineBounds=bounds(),
        selectedBounds=bounds(),
        minimumPurposeDays=mapOf(ComposerSessionPurpose.HYPERTROPHY to 3)
    )
    private fun inputs(policy:AuthoredGoalProgramPolicy=policy())=
        CustomCoachAuditInputs(
            policiesByPhase=mapOf(TrainingPhase.HYPERTROPHY to policy),
            nonSetSecondsByWeekday=mapOf(0 to 200,2 to 200,4 to 200)
        )
    private fun initialize() {
        assertEquals(AthleteSetupStatus.READY_FOR_PROGRAM_REVIEW,setup().status)
        assertEquals(NativeAuthoringSourceWrite.Committed(1),
            sources.publish("owner-1",0,source()))
        assertEquals(NativeCustomDraftWriteResult.Committed(1),
            drafts.save("owner-1","cycle-1",0,cycle()))
        assertEquals(NativeAdaptivePermissionWrite.Committed(1),
            permissionStore.confirm("owner-1","cycle-1",0,
                setOf(CustomCycleChangeKind.CUES,CustomCycleChangeKind.REPS),
                true))
        assertEquals(CustomCoachAuditStatus.READY_FOR_ATHLETE_REVIEW,
            CustomCycleCoachAuditDomain.review(
                cycle(),source().setup,source().catalog,inputs()).status)
    }
    private fun approved():NativeKotlinCustomCycleSignoff {
        val result=store().confirm("owner-1","cycle-1",1,1,0,inputs(),true)
        assertTrue(result.toString(),result is NativeCustomSignoffWrite.Committed)
        return (result as NativeCustomSignoffWrite.Committed).receipt
    }

    private val root get()=temp.root.toPath()
    private val history by lazy{NativeKotlinHistoryManifestFileStore(root)}
    private val preflight by lazy{NativeKotlinCustomWorkoutPreflight(fence,identity,drafts,sources,store())}
    private val prepared by lazy{NativeKotlinCustomWorkoutPreparedStore(root,fence,identity,preflight)}
    private val composer by lazy{KotlinNativeJournalFileStore(root)}
    private val issuer by lazy{NativeKotlinCustomWorkoutJournalFileStore(root,fence,identity,
        prepared,preflight,history,composer)}
    private val reader by lazy{NativeKotlinMixedHistoryReadCoordinator(history,composer,
        KotlinNativeSavedCorrectionFileStore(root,composer),issuer,
        object:NativeKotlinWorkoutAuthority {
            override fun currentApprovedWorkout(ownerKey:String,workoutId:String):
                NativeKotlinWorkoutApproval?=null
            override fun canAccessIssuedWorkout(ownerKey:String,source:KotlinTrainingSourceRevision)=
                ownerKey=="owner-1" && source.athleteId=="athlete-1"
        },identity,fence,NativeKotlinCustomSavedCorrectionFileStore(root,fence,identity,issuer,history))}
    private fun review()=NativeKotlinNamedPercentageDraftReview(fence,identity,drafts,sources,
        store(),preflight,reader,NativeKotlinCustomOutstandingWorkoutInventory(prepared,issuer))
    private fun enroll() {
        assertTrue(history.enroll("owner-1","athlete-1"){owner,athlete->
            active==NativeKotlinOwnerIdentity(owner,athlete)
        } is NativeHistoryLedgerResult.Durable)
    }
    private fun selection()=NativeTieredInitialSelection("owner-1","cycle-1",1,0,
        "slot-0","tier-first",PercentageTier.PRIMARY,n("25"),n("5"))

    @Test fun initialTierRequiresRealOwnerConsentLedgerAndExactPhysicalReference() {
        initialize()
        val s=selection()
        assertEquals(NativeTieredInitialReview.Rejected(NativeNamedPercentageRefusal.CURRENT_CONSENT_REQUIRED),
            review().previewTieredInitial(s))
        val approval=approved()
        assertEquals(NativeTieredInitialReview.Rejected(NativeNamedPercentageRefusal.HISTORY_UNVERIFIED),
            review().previewTieredInitial(s))
        enroll()
        val result=review().previewTieredInitial(s)
        assertTrue(result.toString(),result is NativeTieredInitialReview.Ready)
        val ready=result as NativeTieredInitialReview.Ready
        assertEquals(approval.recordRevision,ready.consentRevision)
        assertEquals(TieredLinearState(0,n("25")),ready.request.initialState)
        assertEquals("tier-first",ready.request.nextWorkoutId)
        assertEquals("slot-0",ready.request.sourceSlotId)
        assertTrue(ready.request.completed.isEmpty())
        assertEquals(LoadBasis.BARBELL_TOTAL,ready.request.scope.basis)
        val authored=ready.proposal.proposal.draft.weekOverrides.getValue("base").single().sets.single()
        val intent=checkNotNull(authored.percentageIntent)
        assertEquals(NamedPercentageScheme.GZCLP,intent.scheme)
        assertEquals(0,intent.tierStage)
        assertEquals(3,authored.exactRepTarget)
        assertEquals(n("25"),(authored.load as DraftSetLoad.Verified).value)
        assertTrue(intent.repOut)
        assertFalse(ready.maySaveProgram)
        assertFalse(ready.mayIssueWorkout)
        assertFalse(ready.mayModifyHistory)
        assertEquals(ready,review().reconfirmTieredInitial(s,ready,true))
        assertEquals(NativeTieredInitialReview.Rejected(NativeNamedPercentageRefusal.CONFIRMATION_REQUIRED),
            review().reconfirmTieredInitial(s,ready,false))
        assertEquals(NativeTieredInitialReview.Rejected(NativeNamedPercentageRefusal.REVIEW_CHANGED),
            review().reconfirmTieredInitial(s.copy(initialLoad=n("30")),ready,true))
        assertEquals(NativeTieredInitialReview.Rejected(NativeNamedPercentageRefusal.RECIPE_REFUSED),
            review().previewTieredInitial(s.copy(initialLoad=n("26"))))
        assertEquals(NativeTieredInitialReview.Rejected(NativeNamedPercentageRefusal.INVALID_SELECTION),
            review().previewTieredInitial(s.copy(maximumAuthorizedIncrease=BigDecimal.ZERO)))
        assertEquals(1L,(drafts.read("owner-1","cycle-1") as NativeCustomDraftReadResult.Found).draft.revision)
        assertEquals(NativeCustomPreparedRead.Missing,prepared.open("owner-1","tier-first"))
        assertTrue(prepared.prepare("owner-1","other-prepared","cycle-1",1,0,inputs())
            is NativeCustomPrepareResult.Prepared)
        assertEquals(NativeTieredInitialReview.Rejected(NativeNamedPercentageRefusal.PROTECTED_WEEK),
            review().reconfirmTieredInitial(s,ready,true))
        active=NativeKotlinOwnerIdentity("other-owner","other-athlete")
        assertEquals(NativeTieredInitialReview.Rejected(NativeNamedPercentageRefusal.NOT_AUTHORIZED),
            review().previewTieredInitial(s))
    }

    @Test fun initialTierCannotBorrowOlderOrdinarySameDayReceiptsOrRestartAUsedSlot() {
        initialize()
        approved()
        enroll()
        val old="ordinary-week-one"
        assertTrue(prepared.prepare("owner-1",old,"cycle-1",1,0,inputs())
            is NativeCustomPrepareResult.Prepared)
        assertTrue(issuer.issue("owner-1",old,"cycle-1",1,0,inputs())
            is NativeCustomJournalResult.Durable)
        val record=issuer.record("owner-1",old,CustomWorkoutJournalOperation.Record(
            "ordinary-actual",0,CustomWorkoutVisitKey("cycle-1:w1:d0:slot-0:s1",
                CustomWorkoutVisitPart.MAIN),KotlinJournalSetOutcome.COMPLETED,
            KotlinPerformedSet(n("20"),LoadUnit.LB,12,2)))
        assertTrue(record.toString(),record is NativeCustomJournalResult.Durable)
        assertTrue(NativeKotlinCustomHistoryTransaction(history,issuer,fence,identity)
            .complete("owner-1","athlete-1",old,CustomWorkoutJournalOperation.Finish("ordinary-finish",1))
            is NativeCustomFinishResult.Committed)
        assertEquals(NativeTieredInitialReview.Rejected(NativeNamedPercentageRefusal.HISTORY_UNVERIFIED),
            review().previewTieredInitial(selection().copy(absoluteWeek=2)))
        assertEquals(NativeTieredInitialReview.Rejected(NativeNamedPercentageRefusal.PROTECTED_WEEK),
            review().previewTieredInitial(selection().copy(absoluteWeek=2,weekday=2,slotId="slot-2",
                nextWorkoutId=old)))
        assertEquals(1L,(drafts.read("owner-1","cycle-1") as NativeCustomDraftReadResult.Found).draft.revision)
    }
}
