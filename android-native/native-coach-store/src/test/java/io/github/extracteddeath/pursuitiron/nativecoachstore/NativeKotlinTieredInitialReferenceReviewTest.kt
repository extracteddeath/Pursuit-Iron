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

    @Test fun initialTierAcceptanceIsAtomicColdReadableAndNotWorkoutConsent() {
        initialize()
        val prior=approved()
        enroll()
        val s=selection()
        val displayed=review().previewTieredInitial(s) as NativeTieredInitialReview.Ready
        assertEquals(NativeTieredInitialAcceptance.Rejected(NativeNamedPercentageRefusal.CONFIRMATION_REQUIRED),
            review().acceptTieredInitial(s,displayed,false))
        assertEquals(NativeTieredInitialAcceptance.Rejected(NativeNamedPercentageRefusal.REVIEW_CHANGED),
            review().acceptTieredInitial(s.copy(maximumAuthorizedIncrease=n("10")),displayed,true))
        val forged=displayed.copy(historyRevision=displayed.historyRevision+1)
        assertEquals(NativeTieredInitialAcceptance.Rejected(NativeNamedPercentageRefusal.REVIEW_CHANGED),
            review().acceptTieredInitial(s,forged,true))
        assertEquals(NativeCustomDraftReadResult.Found(cycle()),drafts.read("owner-1","cycle-1"))
        val result=review().acceptTieredInitial(s,displayed,true)
        assertTrue(result.toString(),result is NativeTieredInitialAcceptance.Saved)
        val saved=result as NativeTieredInitialAcceptance.Saved
        assertEquals(2L,saved.revision)
        assertEquals(s,saved.receipt.selection)
        assertEquals(prior.recordRevision,saved.receipt.consentRevision)
        assertFalse(saved.receipt.mayIssueWorkout)
        assertFalse(saved.receipt.maySignoff)
        assertFalse(saved.isSignedOff)
        val cold=NativeCustomCycleDraftFileStore(root,NativeKotlinApprovalRevisionFence(root),identity)
        val exact=displayed.proposal.proposal.draft
        assertEquals(NativeCustomDraftReadResult.Found(exact,tieredInitialReceipt=saved.receipt),
            cold.read("owner-1","cycle-1"))
        assertFalse((store().read("owner-1","cycle-1",inputs()) as NativeCustomSignoffRead.Found).isCurrent)
        assertTrue(prepared.prepare("owner-1","tier-first","cycle-1",1,0,inputs())
            is NativeCustomPrepareResult.Rejected)
        assertTrue(review().acceptTieredInitial(s,displayed,true) is NativeTieredInitialAcceptance.Rejected)
        active=NativeKotlinOwnerIdentity("other-owner","other-athlete")
        assertEquals(NativeCustomDraftReadResult.Rejected(NativeCustomDraftRefusal.NOT_AUTHORIZED),
            cold.read("owner-1","cycle-1"))
        active=NativeKotlinOwnerIdentity("owner-1","athlete-1")
        assertEquals(NativeCustomDraftWriteResult.Committed(3),
            cold.save("owner-1","cycle-1",2,exact.copy(revision=3,title="Edited")))
        val changed=cold.read("owner-1","cycle-1") as NativeCustomDraftReadResult.Found
        assertNull(changed.tieredInitialReceipt)
        assertTrue(changed.namedReceipts.isEmpty())
        assertTrue(review().acceptTieredInitial(s,displayed,true) is NativeTieredInitialAcceptance.Rejected)
    }

    @Test fun tieredInitialFinalConsentColdRevalidatesAndNeverGrantsOrdinaryOrFixedIssuance() {
        initialize()
        val prior=approved()
        enroll()
        val s=selection()
        val proposal=review().previewTieredInitial(s) as NativeTieredInitialReview.Ready
        val saved=review().acceptTieredInitial(s,proposal,true) as NativeTieredInitialAcceptance.Saved
        val inventory=NativeKotlinCustomOutstandingWorkoutInventory(prepared,issuer)
        fun reviewer()=NativeKotlinSavedNamedPercentageReview(fence,identity,drafts,sources,
            store(),reader,inventory)
        val pending=reviewer().reviewTieredInitial("owner-1","cycle-1",inputs())
        assertTrue(pending.toString(),pending is NativeSavedNamedReview.Ready)
        val displayed=pending as NativeSavedNamedReview.Ready
        assertEquals(saved.receipt,displayed.initialTierReceipt)
        assertEquals(proposal.request,displayed.initialTierEvidence?.request)
        assertEquals(prior,displayed.priorConsent)
        assertTrue(displayed.receipts.isEmpty())
        assertTrue(displayed.evidence.isEmpty())
        assertFalse(displayed.mayIssueWorkout)
        assertTrue(reviewer().review("owner-1","cycle-1",inputs()) is NativeSavedNamedReview.Rejected)
        assertTrue(reviewer().reviewSignedTieredInitial("owner-1","cycle-1",inputs()) is NativeSavedNamedReview.Rejected)
        assertEquals(NativeCustomSignoffWrite.Rejected(NativeCustomSignoffRefusal.CONFIRMATION_REQUIRED),
            store().confirmTieredInitial("owner-1","cycle-1",displayed,false,reader,inventory))
        assertTrue(store().confirmNamed("owner-1","cycle-1",displayed,true,reader,inventory)
            is NativeCustomSignoffWrite.Rejected)
        val altered=displayed.copy(initialTierReceipt=saved.receipt.copy(
            selection=s.copy(maximumAuthorizedIncrease=n("10"))))
        assertEquals(NativeCustomSignoffWrite.Rejected(NativeCustomSignoffRefusal.STALE_CONSENT),
            store().confirmTieredInitial("owner-1","cycle-1",altered,true,reader,inventory))
        assertEquals(prior,(store().read("owner-1","cycle-1") as NativeCustomSignoffRead.Found).receipt)
        val result=store().confirmTieredInitial("owner-1","cycle-1",displayed,true,reader,inventory)
        assertTrue(result.toString(),result is NativeCustomSignoffWrite.Committed)
        val receipt=(result as NativeCustomSignoffWrite.Committed).receipt
        assertEquals(prior.recordRevision+1,receipt.recordRevision)
        assertEquals(2L,receipt.cycleRevision)
        assertNotEquals(NativeCustomCycleSignoffFingerprint.calculate(displayed.draft,source(),inputs()),
            receipt.fingerprintSha256)
        val coldFence=NativeKotlinApprovalRevisionFence(root)
        val coldDrafts=NativeCustomCycleDraftFileStore(root,coldFence,identity)
        val coldSignoffs=NativeKotlinCustomCycleSignoffFileStore(root,coldFence,identity,coldDrafts,sources)
        val cold=NativeKotlinSavedNamedPercentageReview(coldFence,identity,coldDrafts,sources,
            coldSignoffs,reader,inventory)
        val verified=cold.reviewSignedTieredInitial("owner-1","cycle-1",inputs())
        assertTrue(verified.toString(),verified is NativeSavedNamedReview.Signed)
        val signed=verified as NativeSavedNamedReview.Signed
        assertEquals(receipt,signed.consent)
        assertEquals(saved.receipt,signed.initialTierReceipt)
        assertEquals(displayed.initialTierEvidence,signed.initialTierEvidence)
        assertEquals(displayed.draft,signed.draft)
        assertFalse(signed.mayIssueWorkout)
        assertTrue(cold.reviewSigned("owner-1","cycle-1",inputs()) is NativeSavedNamedReview.Rejected)
        assertTrue(cold.reviewTieredInitial("owner-1","cycle-1",inputs()) is NativeSavedNamedReview.Rejected)
        assertFalse((coldSignoffs.read("owner-1","cycle-1",inputs()) as NativeCustomSignoffRead.Found).isCurrent)
        assertTrue(prepared.prepare("owner-1",s.nextWorkoutId,"cycle-1",1,0,inputs())
            is NativeCustomPrepareResult.Rejected)
        assertTrue(prepared.prepareNamed("owner-1",s.nextWorkoutId,"cycle-1",1,0,inputs(),reader,inventory)
            is NativeCustomPrepareResult.Rejected)
        val changed=inputs().copy(nonSetSecondsByWeekday=mapOf(0 to 300,2 to 200,4 to 200))
        assertTrue(cold.reviewSignedTieredInitial("owner-1","cycle-1",changed) is NativeSavedNamedReview.Rejected)
        active=NativeKotlinOwnerIdentity("other-owner","other-athlete")
        assertTrue(cold.reviewSignedTieredInitial("owner-1","cycle-1",inputs()) is NativeSavedNamedReview.Rejected)
        active=NativeKotlinOwnerIdentity("owner-1","athlete-1")
        assertTrue(coldSignoffs.revoke("owner-1","cycle-1",receipt.recordRevision)
            is NativeCustomSignoffWrite.Committed)
        assertTrue(cold.reviewSignedTieredInitial("owner-1","cycle-1",inputs()) is NativeSavedNamedReview.Rejected)
    }

    @Test fun initialTierIssuesOnlyExactApprovedIdAndBuildsNextCandidateFromDurableLedger() {
        initialize()
        approved()
        enroll()
        val s=selection()
        val proposal=review().previewTieredInitial(s) as NativeTieredInitialReview.Ready
        assertTrue(review().acceptTieredInitial(s,proposal,true) is NativeTieredInitialAcceptance.Saved)
        val inventory=NativeKotlinCustomOutstandingWorkoutInventory(prepared,issuer)
        val reviewer=NativeKotlinSavedNamedPercentageReview(fence,identity,drafts,sources,
            store(),reader,inventory)
        val pending=reviewer.reviewTieredInitial("owner-1","cycle-1",inputs()) as NativeSavedNamedReview.Ready
        fun prep(w:String=s.nextWorkoutId,week:Int=1,day:Int=0)=
            prepared.prepareTieredInitial("owner-1",w,"cycle-1",week,day,inputs(),reader,inventory)
        fun coldIssuer()=NativeKotlinCustomWorkoutJournalFileStore(root,fence,identity,
            NativeKotlinCustomWorkoutPreparedStore(root,fence,identity,preflight),
            preflight,history,composer)
        fun issue(w:String=s.nextWorkoutId,week:Int=1,day:Int=0)=
            coldIssuer().issueTieredInitial("owner-1",w,"cycle-1",week,day,inputs(),reader)
        assertTrue(prep() is NativeCustomPrepareResult.Rejected)
        val consent=store().confirmTieredInitial("owner-1","cycle-1",pending,true,reader,inventory)
            as NativeCustomSignoffWrite.Committed
        assertTrue(prep("other-id") is NativeCustomPrepareResult.Rejected)
        assertTrue(prep(week=2) is NativeCustomPrepareResult.Rejected)
        assertTrue(prep(day=2) is NativeCustomPrepareResult.Rejected)
        assertEquals(NativeCustomJournalResult.Rejected(NativeCustomJournalRefusal.NOT_PREPARED),issue())
        val result=prep()
        assertTrue(result.toString(),result is NativeCustomPrepareResult.Prepared)
        val saved=(result as NativeCustomPrepareResult.Prepared).workout
        assertEquals(NativeCustomPreparationAuthority.TIERED_INITIAL,saved.authority)
        assertEquals(consent.receipt.fingerprintSha256,saved.consentFingerprint)
        assertEquals(NativeCustomPrepareResult.Prepared(saved,true),prep())
        assertEquals(NativeCustomPreparedRead.Found(saved),
            NativeKotlinCustomWorkoutPreparedStore(root,NativeKotlinApprovalRevisionFence(root),
                identity,preflight).open("owner-1",s.nextWorkoutId))
        assertTrue(issuer.issueNamed("owner-1",s.nextWorkoutId,"cycle-1",1,0,inputs(),reader)
            is NativeCustomJournalResult.Rejected)
        assertTrue(issuer.issue("owner-1",s.nextWorkoutId,"cycle-1",1,0,inputs())
            is NativeCustomJournalResult.Rejected)
        val first=issue()
        assertTrue(first.toString(),first is NativeCustomJournalResult.Durable)
        val issued=(first as NativeCustomJournalResult.Durable).issued
        assertEquals(saved.snapshot,issued.journal.originalSnapshot)
        assertEquals(NativeCustomJournalResult.Durable(issued,true),issue())
        val target=saved.snapshot.slots.single().sets.single()
        assertEquals(3,target.prescription.exactRepTarget)
        val intent=checkNotNull(target.prescription.percentageIntent)
        assertEquals(0,intent.tierStage)
        assertTrue(intent.repOut)
        val record=coldIssuer().record("owner-1",s.nextWorkoutId,
            CustomWorkoutJournalOperation.Record("tier-actual",0,
                CustomWorkoutVisitKey(target.setKey,CustomWorkoutVisitPart.MAIN),
                KotlinJournalSetOutcome.COMPLETED,KotlinPerformedSet(n("25"),LoadUnit.LB,5,0)))
        assertTrue(record.toString(),record is NativeCustomJournalResult.Durable)
        val finish=CustomWorkoutJournalOperation.Finish("tier-finish",1)
        val completed=NativeKotlinCustomHistoryTransaction(history,coldIssuer(),fence,identity)
            .complete("owner-1","athlete-1",s.nextWorkoutId,finish)
        assertTrue(completed.toString(),completed is NativeCustomFinishResult.Committed)
        val fact=(completed as NativeCustomFinishResult.Committed).evidence.visits.single()
        assertEquals(intent,fact.percentageIntent)
        assertEquals(5,fact.actual?.reps)
        assertEquals(target.prescription.load,fact.prescribedLoad)
        assertEquals(completed.copy(replayed=true),
            NativeKotlinCustomHistoryTransaction(history,coldIssuer(),fence,identity)
                .complete("owner-1","athlete-1",s.nextWorkoutId,finish))
        val ledger=reader.readTieredLedger("owner-1","athlete-1",proposal.request.scope,
            s.slotId,s.tier,2)
        assertTrue(ledger.toString(),ledger is NativeOwnerTieredLedgerResult.Review)
        val verified=ledger as NativeOwnerTieredLedgerResult.Review
        assertEquals(1,verified.exposures.size)
        val next=TieredLinearProgressionDomain.review(proposal.request.copy(
            nextWorkoutId="tier-next",completed=verified.exposures))
        assertTrue(next.toString(),next is TieredLinearResult.Candidate)
        val candidate=next as TieredLinearResult.Candidate
        assertEquals(TieredLinearState(0,n("30")),candidate.state)
        assertEquals(TieredLinearDecision.EARNED_INCREMENT,candidate.transitions.single().decision)
        assertFalse(candidate.mayIssueWorkout)
        assertTrue(prep("tier-next",2,0) is NativeCustomPrepareResult.Rejected)
        assertTrue(reviewer.reviewSignedTieredInitial("owner-1","cycle-1",inputs())
            is NativeSavedNamedReview.Rejected)
        assertTrue(store().revoke("owner-1","cycle-1",consent.receipt.recordRevision)
            is NativeCustomSignoffWrite.Committed)
        assertTrue(issue() is NativeCustomJournalResult.Durable)
        assertTrue(issuer.issueNamed("owner-1",s.nextWorkoutId,"cycle-1",1,0,inputs(),reader)
            is NativeCustomJournalResult.Rejected)
        active=NativeKotlinOwnerIdentity("other-owner","other-athlete")
        assertEquals(NativeCustomJournalResult.Rejected(NativeCustomJournalRefusal.NOT_AUTHORIZED),issue())
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
