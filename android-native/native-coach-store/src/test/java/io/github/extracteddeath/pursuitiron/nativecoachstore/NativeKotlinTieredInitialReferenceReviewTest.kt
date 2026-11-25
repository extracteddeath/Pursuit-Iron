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
    private fun cycle(weeks:Int=3)=CustomTrainingCycle(
        "cycle-1",1,"athlete-1",AthleteTrainingGoal.HYPERTROPHY,
        "My custom cycle",listOf(CustomTrainingBlock(
            "base","Foundation",TrainingPhase.HYPERTROPHY,
            CustomBlockPurpose.TRAINING,weeks,
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
    private fun initialize(weeks:Int=3) {
        assertEquals(AthleteSetupStatus.READY_FOR_PROGRAM_REVIEW,setup().status)
        assertEquals(NativeAuthoringSourceWrite.Committed(1),
            sources.publish("owner-1",0,source()))
        assertEquals(NativeCustomDraftWriteResult.Committed(1),
            drafts.save("owner-1","cycle-1",0,cycle(weeks)))
        assertEquals(NativeAdaptivePermissionWrite.Committed(1),
            permissionStore.confirm("owner-1","cycle-1",0,
                setOf(CustomCycleChangeKind.CUES,CustomCycleChangeKind.REPS),
                true))
        assertEquals(CustomCoachAuditStatus.READY_FOR_ATHLETE_REVIEW,
            CustomCycleCoachAuditDomain.review(
                cycle(weeks),source().setup,source().catalog,inputs()).status)
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

    @Test fun multipleTierReferencesShareOneReviewedRevisionAndRemainIndependentByDay() {
        initialize()
        val first=selection()
        val second=first.copy(weekday=2,slotId="slot-2",nextWorkoutId="tier-second",initialLoad=n("30"))
        val third=first.copy(weekday=4,slotId="slot-4",nextWorkoutId="tier-third",initialLoad=n("35"))
        val selections=listOf(first,second,third)
        fun preview(s:List<NativeTieredInitialSelection> = selections)=review().previewTieredInitialBatch(s)
        assertEquals(NativeTieredInitialBatchReview.Rejected(NativeNamedPercentageRefusal.CURRENT_CONSENT_REQUIRED),preview())
        approved()
        enroll()
        val result=preview()
        assertTrue(result.toString(),result is NativeTieredInitialBatchReview.Ready)
        val ready=result as NativeTieredInitialBatchReview.Ready
        assertEquals(2L,ready.draft.revision)
        assertEquals(3,ready.receipts.size)
        assertEquals(listOf(n("25"),n("30"),n("35")),ready.individual.map{it.request.initialState.load})
        assertEquals(3,ready.individual.map{it.request.scope.trainingDayId}.distinct().size)
        assertEquals(3,ready.individual.map{it.request.sourceSlotId}.distinct().size)
        assertTrue(ready.individual.all{it.request.completed.isEmpty()})
        val edits=ready.draft.weekOverrides.getValue("base")
        assertEquals(listOf(0,2,4),edits.map{it.weekday})
        assertEquals(listOf(n("25"),n("30"),n("35")),edits.map{(it.sets.single().load as DraftSetLoad.Verified).value})
        assertTrue(edits.all{it.sets.single().percentageIntent?.tierStage==0})
        assertTrue(NativeTieredInitialDraftReceiptCodec.validBatch(ready.receipts,"owner-1",ready.draft))
        assertFalse(NativeTieredInitialDraftReceiptCodec.valid(ready.receipts.first(),"owner-1",ready.draft))
        assertFalse(NativeTieredInitialDraftReceiptCodec.validBatch(ready.receipts.dropLast(1),"owner-1",ready.draft))
        assertFalse(NativeTieredInitialDraftReceiptCodec.validBatch(ready.receipts+ready.receipts.first(),"owner-1",ready.draft))
        assertFalse(NativeTieredInitialDraftReceiptCodec.validBatch(ready.receipts,"other-owner",ready.draft))
        assertFalse(NativeTieredInitialDraftReceiptCodec.validBatch(
            ready.receipts.mapIndexed{i,r->if(i==1)r.copy(selection=r.selection.copy(nextWorkoutId=first.nextWorkoutId)) else r},
            "owner-1",ready.draft))
        assertEquals(ready,review().reconfirmTieredInitialBatch(selections,ready,true))
        assertEquals(NativeTieredInitialBatchReview.Rejected(NativeNamedPercentageRefusal.CONFIRMATION_REQUIRED),
            review().reconfirmTieredInitialBatch(selections,ready,false))
        assertEquals(NativeTieredInitialBatchReview.Rejected(NativeNamedPercentageRefusal.REVIEW_CHANGED),
            review().reconfirmTieredInitialBatch(listOf(first,second.copy(initialLoad=n("35")),third),ready,true))
        assertEquals(NativeTieredInitialBatchReview.Rejected(NativeNamedPercentageRefusal.INVALID_SELECTION),
            preview(listOf(first,second.copy(nextWorkoutId=first.nextWorkoutId))))
        assertEquals(NativeTieredInitialBatchReview.Rejected(NativeNamedPercentageRefusal.INVALID_SELECTION),
            preview(listOf(first,first.copy(absoluteWeek=2,nextWorkoutId="another-week"))))
        assertTrue(preview(listOf(first,second.copy(initialLoad=n("31")))) is NativeTieredInitialBatchReview.Rejected)
        assertFalse(ready.maySaveProgram)
        assertFalse(ready.mayIssueWorkout)
        assertEquals(1L,(drafts.read("owner-1","cycle-1") as NativeCustomDraftReadResult.Found).draft.revision)
        assertEquals(NativeCustomPreparedRead.Missing,prepared.open("owner-1",first.nextWorkoutId))
        assertTrue(prepared.prepare("owner-1","other-prepared","cycle-1",1,0,inputs())
            is NativeCustomPrepareResult.Prepared)
        assertEquals(NativeTieredInitialBatchReview.Rejected(NativeNamedPercentageRefusal.PROTECTED_WEEK),
            review().reconfirmTieredInitialBatch(selections,ready,true))
        active=NativeKotlinOwnerIdentity("other-owner","other-athlete")
        assertEquals(NativeTieredInitialBatchReview.Rejected(NativeNamedPercentageRefusal.NOT_AUTHORIZED),preview())
    }

    @Test fun batchReferencesAreStoredSignedAndIssuedInOrderWithoutOrdinaryFallback() {
        initialize()
        val original=approved()
        enroll()
        val first=selection()
        val second=first.copy(weekday=2,slotId="slot-2",nextWorkoutId="tier-second",initialLoad=n("30"))
        val third=first.copy(weekday=4,slotId="slot-4",nextWorkoutId="tier-third",initialLoad=n("35"))
        val selections=listOf(first,second,third)
        val shown=review().previewTieredInitialBatch(selections) as NativeTieredInitialBatchReview.Ready
        assertEquals(NativeTieredInitialBatchAcceptance.Rejected(NativeNamedPercentageRefusal.CONFIRMATION_REQUIRED),
            review().acceptTieredInitialBatch(selections,shown,false))
        assertEquals(NativeTieredInitialBatchAcceptance.Rejected(NativeNamedPercentageRefusal.REVIEW_CHANGED),
            review().acceptTieredInitialBatch(listOf(first,second.copy(initialLoad=n("35")),third),shown,true))
        assertEquals(NativeTieredInitialBatchAcceptance.Saved(2,shown.receipts),
            review().acceptTieredInitialBatch(selections,shown,true))
        val coldFence=NativeKotlinApprovalRevisionFence(root)
        val coldDrafts=NativeCustomCycleDraftFileStore(root,coldFence,identity)
        val coldSignoffs=NativeKotlinCustomCycleSignoffFileStore(root,coldFence,identity,coldDrafts,sources)
        val saved=coldDrafts.read("owner-1","cycle-1") as NativeCustomDraftReadResult.Found
        assertEquals(shown.draft,saved.draft)
        assertEquals(shown.receipts,saved.tieredInitialReceipts)
        assertNull(saved.tieredInitialReceipt)
        assertTrue(saved.namedReceipts.isEmpty())
        assertEquals(original,(coldSignoffs.read("owner-1","cycle-1") as NativeCustomSignoffRead.Found).receipt)
        val inventory=NativeKotlinCustomOutstandingWorkoutInventory(prepared,issuer)
        val restored=coldSignoffs.recoverTieredInitialReview("owner-1","cycle-1",reader,inventory)
        assertTrue(restored.toString(),restored is NativeSavedNamedReview.Ready)
        val ready=restored as NativeSavedNamedReview.Ready
        assertEquals(shown.receipts,ready.initialTierReceipts)
        assertEquals(shown.individual.map{it.request},ready.initialTierEvidences.map{it.request})
        assertEquals(CustomCoachAuditStatus.READY_FOR_ATHLETE_REVIEW,ready.audit.status)
        assertTrue(coldSignoffs.confirmTieredInitial("owner-1","cycle-1",ready,false,reader,inventory)
            is NativeCustomSignoffWrite.Rejected)
        assertTrue(coldSignoffs.confirmNamed("owner-1","cycle-1",ready,true,reader,inventory)
            is NativeCustomSignoffWrite.Rejected)
        assertTrue(coldSignoffs.confirmTieredInitial("owner-1","cycle-1",
            ready.copy(initialTierReceipts=ready.initialTierReceipts.dropLast(1)),true,reader,inventory)
            is NativeCustomSignoffWrite.Rejected)
        val approval=coldSignoffs.confirmTieredInitial("owner-1","cycle-1",ready,true,reader,inventory)
        assertTrue(approval.toString(),approval is NativeCustomSignoffWrite.Committed)
        val receipt=(approval as NativeCustomSignoffWrite.Committed).receipt
        assertEquals(2L,receipt.cycleRevision)
        assertEquals(original.recordRevision+1,receipt.recordRevision)
        assertEquals(NativeCustomCycleSignoffFingerprint.calculateTieredInitialBatch(
            saved.draft,source(),inputs(),shown.receipts),receipt.fingerprintSha256)
        assertEquals(receipt.fingerprintSha256,NativeCustomCycleSignoffFingerprint.calculateTieredInitialBatch(
            saved.draft,source(),inputs(),shown.receipts.reversed()))
        val signed=coldSignoffs.recoverTieredInitialReview("owner-1","cycle-1",reader,inventory)
            as NativeSavedNamedReview.Signed
        assertEquals(shown.receipts,signed.initialTierReceipts)
        assertEquals(receipt,signed.consent)
        assertFalse(signed.mayIssueWorkout)
        assertNull(coldSignoffs.signedTieredInputs("owner-1","cycle-1"))
        assertTrue(preflight.inspect("owner-1","cycle-1",1,0,inputs()) is NativeCustomPreflightResult.Rejected)
        fun coldPrepared()=NativeKotlinCustomWorkoutPreparedStore(root,coldFence,identity,preflight)
        fun coldJournal()=NativeKotlinCustomWorkoutJournalFileStore(root,coldFence,identity,
            coldPrepared(),preflight,history,composer)
        fun calendar()=NativeKotlinCustomNextCycleSessionReview(coldFence,identity,coldDrafts,sources,
            coldSignoffs,reader,inventory,coldPrepared())
        fun next()=calendar().reviewMultiTieredInitial("owner-1","cycle-1")
        fun prep(s:NativeTieredInitialSelection)=coldPrepared().prepareTieredInitial("owner-1",s.nextWorkoutId,
            "cycle-1",s.absoluteWeek,s.weekday,inputs(),reader,inventory)
        fun issue(s:NativeTieredInitialSelection)=coldJournal().issueTieredInitial("owner-1",s.nextWorkoutId,
            "cycle-1",s.absoluteWeek,s.weekday,inputs(),reader)
        assertTrue(calendar().review("owner-1","cycle-1",inputs()) is NativeCustomNextCycleSlot.Rejected)
        assertTrue(calendar().reviewTieredCalendar("owner-1","cycle-1") is NativeCustomNextCycleSlot.Rejected)
        assertEquals(NativeCustomNextCycleSlot.Next(1,0,"Day 0"),next())
        assertTrue(prep(second) is NativeCustomPrepareResult.Rejected)
        assertTrue(prep(first.copy(nextWorkoutId="wrong-id")) is NativeCustomPrepareResult.Rejected)
        for(s in selections) {
            assertEquals(NativeCustomNextCycleSlot.Next(1,s.weekday,"Day "+s.weekday),next())
            val p=prep(s) as NativeCustomPrepareResult.Prepared
            assertEquals(NativeCustomPreparationAuthority.TIERED_INITIAL,p.workout.authority)
            assertEquals(receipt.fingerprintSha256,p.workout.consentFingerprint)
            val target=p.workout.snapshot.slots.single().sets.single()
            assertEquals(s.initialLoad,(target.prescription.load as DraftSetLoad.Verified).value)
            assertEquals(NativeCustomNextCycleSlot.Prepared(s.nextWorkoutId,1,s.weekday,"Day "+s.weekday),next())
            assertEquals(NativeCustomPrepareResult.Prepared(p.workout,true),prep(s))
            val issued=issue(s)
            assertTrue(issued.toString(),issued is NativeCustomJournalResult.Durable)
            assertEquals(p.workout.snapshot,(issued as NativeCustomJournalResult.Durable).issued.journal.originalSnapshot)
            assertEquals(NativeCustomNextCycleSlot.ResumeIssued(s.nextWorkoutId,1,s.weekday,"Day "+s.weekday),next())
            assertTrue(coldJournal().record("owner-1",s.nextWorkoutId,
                CustomWorkoutJournalOperation.Record("${s.nextWorkoutId}-actual",0,
                    CustomWorkoutVisitKey(target.setKey,CustomWorkoutVisitPart.MAIN),
                    KotlinJournalSetOutcome.COMPLETED,KotlinPerformedSet(s.initialLoad,LoadUnit.LB,
                        if(s.weekday==2)1 else 5,0))) is NativeCustomJournalResult.Durable)
            val finished=NativeKotlinCustomHistoryTransaction(history,coldJournal(),coldFence,identity)
                .complete("owner-1","athlete-1",s.nextWorkoutId,
                    CustomWorkoutJournalOperation.Finish("${s.nextWorkoutId}-finish",1))
            assertTrue(finished.toString(),finished is NativeCustomFinishResult.Committed)
            assertTrue(prep(s.copy(nextWorkoutId="duplicate-"+s.weekday)) is NativeCustomPrepareResult.Rejected)
        }
        assertEquals(NativeCustomNextCycleSlot.Next(2,0,"Day 0"),next())
        assertTrue(prep(first.copy(absoluteWeek=2,nextWorkoutId="unapproved-transition")) is NativeCustomPrepareResult.Rejected)
        for((index,s) in selections.withIndex()) {
            val request=shown.individual[index].request
            val ledger=reader.readTieredLedger("owner-1","athlete-1",request.scope,s.slotId,s.tier,2)
                as NativeOwnerTieredLedgerResult.Review
            assertEquals(listOf(s.nextWorkoutId),ledger.exposures.map{it.workoutId})
            val result=TieredLinearProgressionDomain.review(request.copy(nextWorkoutId="next-"+s.weekday,
                completed=ledger.exposures)) as TieredLinearResult.Candidate
            assertEquals(if(s.weekday==2)TieredLinearState(1,s.initialLoad)
                else TieredLinearState(0,s.initialLoad+n("5")),result.state)
        }
        val transitionReviewer=NativeKotlinSavedNamedPercentageReview(coldFence,identity,coldDrafts,sources,
            coldSignoffs,reader,inventory)
        fun preview(s:NativeMultiTierNextSelection)=transitionReviewer.previewMultiTieredNext("owner-1","cycle-1",inputs(),s)
        val nextSelection=NativeMultiTierNextSelection("week-two-day-zero",2,0)
        assertTrue(preview(nextSelection.copy(weekday=2)) is NativeSavedNamedReview.Rejected)
        assertTrue(preview(nextSelection.copy(absoluteWeek=3)) is NativeSavedNamedReview.Rejected)
        assertTrue(preview(nextSelection.copy(nextWorkoutId=first.nextWorkoutId)) is NativeSavedNamedReview.Rejected)
        assertTrue(preview(nextSelection.copy(recoverySlotIds=setOf("slot-2"))) is NativeSavedNamedReview.Rejected)
        val proposed=preview(nextSelection) as NativeSavedNamedReview.Signed
        val transition=checkNotNull(proposed.nextMultiTierCandidate)
        assertEquals(nextSelection,transition.selection)
        assertEquals(saved.draft.revision+1,transition.draft.revision)
        assertEquals(TieredLinearState(0,n("30")),transition.slots.single().result.state)
        assertEquals("slot-0",transition.slots.single().evidence.key.slotId)
        assertEquals(n("30"),(transition.snapshot.slots.single().sets.single().prescription.load as DraftSetLoad.Verified).value)
        assertFalse(transition.mayIssueWorkout)
        assertFalse(transition.maySaveProgram)
        assertEquals(transition, (preview(nextSelection) as NativeSavedNamedReview.Signed).nextMultiTierCandidate)
        fun transitionStore()=NativeKotlinFirstTierTransitionConsentStore(root,
            NativeKotlinApprovalRevisionFence(root),identity,coldDrafts,sources,coldSignoffs,reader,inventory)
        fun recoverDay(week:Int=2,day:Int=0)=transitionStore().recoverMulti("owner-1","cycle-1",week,day)
        fun confirmDay(review:NativeSavedNamedReview.Signed=proposed,confirmed:Boolean=true,
            resets:Set<String> = emptySet(),recoveries:Set<String> = emptySet())=
            transitionStore().confirmMulti("owner-1","cycle-1",review,confirmed,resets,recoveries)
        assertEquals(NativeTieredTransitionRecovery.Refused(NativeTieredTransitionConsentResult.MISSING),recoverDay())
        assertEquals(NativeTieredTransitionConsentResult.CONFIRMATION_REQUIRED,confirmDay(confirmed=false))
        assertEquals(NativeTieredTransitionConsentResult.CONFIRMATION_REQUIRED,confirmDay(resets=setOf("slot-0")))
        val recoveryReview=preview(nextSelection.copy(recoverySlotIds=setOf("slot-0"))) as NativeSavedNamedReview.Signed
        assertEquals(NativeTieredTransitionConsentResult.CONFIRMATION_REQUIRED,confirmDay(recoveryReview))
        assertEquals(NativeTieredTransitionConsentResult.STALE_OR_UNVERIFIED,
            confirmDay(proposed.copy(nextMultiTierCandidate=transition.copy(historyRevision=transition.historyRevision+1))))
        assertEquals(NativeTieredTransitionConsentResult.RECORDED,confirmDay())
        val restoredDay=recoverDay() as NativeTieredTransitionRecovery.Ready
        assertEquals(proposed,restoredDay.review)
        assertFalse(restoredDay.mayIssueWorkout)
        assertEquals(restoredDay,recoverDay())
        assertEquals(NativeTieredTransitionConsentResult.REPLAYED,confirmDay())
        assertEquals(NativeTieredTransitionConsentResult.CONFLICT_OR_CORRUPT,
            confirmDay(recoveryReview,recoveries=setOf("slot-0")))
        assertEquals(NativeTieredTransitionRecovery.Refused(NativeTieredTransitionConsentResult.MISSING),recoverDay(day=2))
        assertEquals(NativeTieredTransitionRecovery.Refused(NativeTieredTransitionConsentResult.MISSING),recoverDay(week=3))
        assertEquals(restoredDay,recoverDay())
        val file=java.nio.file.Files.list(root).use{files->
            files.filter{it.fileName.toString().startsWith("tier-day-transition-")}.toList().single()
        }
        val frozen=java.nio.file.Files.readAllBytes(file)
        java.nio.file.Files.write(file,frozen.copyOf().also{it[10]=(it[10].toInt() xor 1).toByte()})
        assertEquals(NativeTieredTransitionRecovery.Refused(NativeTieredTransitionConsentResult.CONFLICT_OR_CORRUPT),recoverDay())
        assertEquals(NativeTieredTransitionConsentResult.CONFLICT_OR_CORRUPT,confirmDay())
        java.nio.file.Files.write(file,frozen)
        assertEquals(restoredDay,recoverDay())
        val original=(coldPrepared().open("owner-1",first.nextWorkoutId) as NativeCustomPreparedRead.Found).workout
        val correction=NativeKotlinCustomHistoryCorrectionTransaction(history,coldJournal(),
            NativeKotlinCustomSavedCorrectionFileStore(root,fence,identity,coldJournal(),history),fence,identity)
            .correct("owner-1","athlete-1",first.nextWorkoutId,
                CustomSavedVisitCorrection("multi-tier-correction",0,
                    CustomWorkoutVisitKey(original.snapshot.slots.single().sets.single().setKey,
                        CustomWorkoutVisitPart.MAIN),"${first.nextWorkoutId}-actual",
                    KotlinJournalSetOutcome.COMPLETED,KotlinPerformedSet(n("25"),LoadUnit.LB,1,0)))
        assertTrue(correction.toString(),correction is NativeCustomCorrectionTransactionResult.Committed)
        assertEquals(NativeTieredTransitionRecovery.Refused(NativeTieredTransitionConsentResult.STALE_OR_UNVERIFIED),recoverDay())
        val corrected=preview(nextSelection) as NativeSavedNamedReview.Signed
        assertEquals(TieredLinearState(1,n("25")),corrected.nextMultiTierCandidate?.slots?.single()?.result?.state)
        assertEquals(NativeTieredTransitionConsentResult.CONFLICT_OR_CORRUPT,confirmDay(corrected))
        assertArrayEquals(frozen,java.nio.file.Files.readAllBytes(file))
        assertEquals(saved,coldDrafts.read("owner-1","cycle-1"))
        assertEquals(NativeCustomPreparedRead.Missing,coldPrepared().open("owner-1",nextSelection.nextWorkoutId))
        active=NativeKotlinOwnerIdentity("other-owner","other-athlete")
        assertTrue(preview(nextSelection) is NativeSavedNamedReview.Rejected)
        assertTrue(coldSignoffs.recoverTieredInitialReview("owner-1","cycle-1",reader,inventory)
            is NativeSavedNamedReview.Rejected)
    }

    @Test fun multiRootSupportingDayRetainsAuthoredTargetsBetweenIndependentInitialWorkouts() {
        initialize()
        approved()
        enroll()
        val first=selection()
        val last=first.copy(weekday=4,slotId="slot-4",nextWorkoutId="tier-last",initialLoad=n("35"))
        val roots=listOf(first,last)
        val shown=review().previewTieredInitialBatch(roots) as NativeTieredInitialBatchReview.Ready
        assertTrue(review().acceptTieredInitialBatch(roots,shown,true) is NativeTieredInitialBatchAcceptance.Saved)
        val inventory=NativeKotlinCustomOutstandingWorkoutInventory(prepared,issuer)
        val ready=store().recoverTieredInitialReview("owner-1","cycle-1",reader,inventory) as NativeSavedNamedReview.Ready
        val receipt=(store().confirmTieredInitial("owner-1","cycle-1",ready,true,reader,inventory)
            as NativeCustomSignoffWrite.Committed).receipt
        fun coldPrepared()=NativeKotlinCustomWorkoutPreparedStore(root,NativeKotlinApprovalRevisionFence(root),identity,preflight)
        fun coldJournal()=NativeKotlinCustomWorkoutJournalFileStore(root,fence,identity,coldPrepared(),preflight,history,composer)
        fun calendar()=NativeKotlinCustomNextCycleSessionReview(fence,identity,drafts,sources,store(),reader,inventory,coldPrepared())
        fun next()=calendar().reviewMultiTieredInitial("owner-1","cycle-1")
        fun prep(s:NativeTieredInitialSelection)=coldPrepared().prepareTieredInitial("owner-1",s.nextWorkoutId,
            "cycle-1",s.absoluteWeek,s.weekday,inputs(),reader,inventory)
        fun issue(s:NativeTieredInitialSelection)=coldJournal().issueTieredInitial("owner-1",s.nextWorkoutId,
            "cycle-1",s.absoluteWeek,s.weekday,inputs(),reader)
        fun prepSupport(id:String="support",week:Int=1,day:Int=2)=coldPrepared().prepareTieredSupport(
            "owner-1",id,"cycle-1",week,day,reader,inventory)
        fun finish(p:NativeCustomPreparedWorkout,load:BigDecimal,reps:Int) {
            val id=p.workoutId
            val target=p.snapshot.slots.single().sets.single()
            assertTrue(coldJournal().record("owner-1",id,
                CustomWorkoutJournalOperation.Record("$id-actual",0,
                    CustomWorkoutVisitKey(target.setKey,CustomWorkoutVisitPart.MAIN),
                    KotlinJournalSetOutcome.COMPLETED,KotlinPerformedSet(load,LoadUnit.LB,reps,0)))
                is NativeCustomJournalResult.Durable)
            val finished=NativeKotlinCustomHistoryTransaction(history,coldJournal(),fence,identity)
                .complete("owner-1","athlete-1",id,CustomWorkoutJournalOperation.Finish("$id-finish",1))
            assertTrue(finished.toString(),finished is NativeCustomFinishResult.Committed)
        }
        assertTrue(calendar().isMultiTieredSupportDay("owner-1","cycle-1",1,2))
        assertTrue(calendar().isMultiTieredSupportDay("owner-1","cycle-1",2,2))
        assertFalse(calendar().isMultiTieredSupportDay("owner-1","cycle-1",2,0))
        assertFalse(calendar().isMultiTieredSupportDay("owner-1","cycle-1",2,4))
        assertTrue(prepSupport() is NativeCustomPrepareResult.Rejected)
        val p1=(prep(first) as NativeCustomPrepareResult.Prepared).workout
        assertTrue(issue(first) is NativeCustomJournalResult.Durable)
        finish(p1,n("25"),5)
        assertEquals(NativeCustomNextCycleSlot.Next(1,2,"Day 2"),next())
        assertTrue(prep(last) is NativeCustomPrepareResult.Rejected)
        assertTrue(prepSupport(id=last.nextWorkoutId) is NativeCustomPrepareResult.Rejected)
        assertTrue(prepSupport(day=4) is NativeCustomPrepareResult.Rejected)
        val support=(prepSupport() as NativeCustomPrepareResult.Prepared).workout
        assertEquals(NativeCustomPreparationAuthority.TIERED_SUPPORT,support.authority)
        assertEquals(receipt.fingerprintSha256,support.consentFingerprint)
        assertEquals(set(),support.snapshot.slots.single().sets.single().prescription)
        assertEquals(NativeCustomNextCycleSlot.Prepared("support",1,2,"Day 2"),next())
        assertEquals(NativeCustomPrepareResult.Prepared(support,true),prepSupport())
        assertTrue(coldJournal().issueTieredSupport("owner-1","support","cycle-1",1,2,reader)
            is NativeCustomJournalResult.Durable)
        assertEquals(NativeCustomNextCycleSlot.ResumeIssued("support",1,2,"Day 2"),next())
        finish(support,n("20"),12)
        assertEquals(NativeCustomNextCycleSlot.Next(1,4,"Day 4"),next())
        assertTrue(prepSupport(id="duplicate") is NativeCustomPrepareResult.Rejected)
        val p2=(prep(last) as NativeCustomPrepareResult.Prepared).workout
        assertTrue(issue(last) is NativeCustomJournalResult.Durable)
        finish(p2,n("35"),1)
        assertEquals(NativeCustomNextCycleSlot.Next(2,0,"Day 0"),next())
        assertTrue(prepSupport(week=2,day=0) is NativeCustomPrepareResult.Rejected)
        assertTrue(prepSupport(week=2) is NativeCustomPrepareResult.Rejected)
        for(r in shown.individual) {
            val request=r.request
            val ledger=reader.readTieredLedger("owner-1","athlete-1",request.scope,
                r.selection.slotId,r.selection.tier,2) as NativeOwnerTieredLedgerResult.Review
            assertEquals(listOf(r.selection.nextWorkoutId),ledger.exposures.map{it.workoutId})
            val result=TieredLinearProgressionDomain.review(request.copy(nextWorkoutId="next-"+r.selection.weekday,
                completed=ledger.exposures)) as TieredLinearResult.Candidate
            assertEquals(if(r.selection.weekday==0)TieredLinearState(0,n("30"))
                else TieredLinearState(1,n("35")),result.state)
        }
    }

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
        assertEquals(NativeCustomDraftReadResult.Found(exact,tieredInitialReceipts=listOf(saved.receipt)),
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
        assertEquals(displayed,store().recoverTieredInitialReview("owner-1","cycle-1",reader,inventory))
        assertTrue(store().recoverFixedNamedReview("owner-1","cycle-1",reader,inventory)
            is NativeSavedNamedReview.Rejected)
        assertNull(store().signedTieredInputs("owner-1","cycle-1"))
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
        assertEquals(signed,coldSignoffs.recoverTieredInitialReview("owner-1","cycle-1",reader,inventory))
        assertEquals(inputs(),coldSignoffs.signedTieredInputs("owner-1","cycle-1"))
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
        assertTrue(coldSignoffs.recoverTieredInitialReview("owner-1","cycle-1",reader,inventory)
            is NativeSavedNamedReview.Rejected)
        assertNull(coldSignoffs.signedTieredInputs("owner-1","cycle-1"))
        active=NativeKotlinOwnerIdentity("owner-1","athlete-1")
        assertTrue(coldSignoffs.revoke("owner-1","cycle-1",receipt.recordRevision)
            is NativeCustomSignoffWrite.Committed)
        assertTrue(cold.reviewSignedTieredInitial("owner-1","cycle-1",inputs()) is NativeSavedNamedReview.Rejected)
        assertTrue(coldSignoffs.recoverTieredInitialReview("owner-1","cycle-1",reader,inventory)
            is NativeSavedNamedReview.Rejected)
        assertNull(coldSignoffs.signedTieredInputs("owner-1","cycle-1"))
    }

    @Test fun authoredSupportingDayUsesExistingDurableJournalAndLeavesTierHistoryIndependent() {
        initialize()
        approved()
        enroll()
        val initial=selection()
        val proposal=review().previewTieredInitial(initial) as NativeTieredInitialReview.Ready
        assertTrue(review().acceptTieredInitial(initial,proposal,true) is NativeTieredInitialAcceptance.Saved)
        val inventory=NativeKotlinCustomOutstandingWorkoutInventory(prepared,issuer)
        val reviewer=NativeKotlinSavedNamedPercentageReview(fence,identity,drafts,sources,
            store(),reader,inventory)
        val pending=reviewer.reviewTieredInitial("owner-1","cycle-1",inputs()) as NativeSavedNamedReview.Ready
        val consent=store().confirmTieredInitial("owner-1","cycle-1",pending,true,reader,inventory)
            as NativeCustomSignoffWrite.Committed
        fun coldPrepared()=NativeKotlinCustomWorkoutPreparedStore(root,
            NativeKotlinApprovalRevisionFence(root),identity,preflight)
        fun coldJournal()=NativeKotlinCustomWorkoutJournalFileStore(root,fence,identity,
            coldPrepared(),preflight,history,composer)
        fun prepareSupport(w:String="support-day",day:Int=2,week:Int=1)=
            coldPrepared().prepareTieredSupport("owner-1",w,"cycle-1",week,day,reader,inventory)
        fun issueSupport(w:String="support-day",day:Int=2,week:Int=1)=
            coldJournal().issueTieredSupport("owner-1",w,"cycle-1",week,day,reader)
        fun calendar()=NativeKotlinCustomNextCycleSessionReview(
            NativeKotlinApprovalRevisionFence(root),identity,drafts,sources,store(),reader,inventory,coldPrepared())
        fun transitions()=NativeKotlinFirstTierTransitionConsentStore(root,
            NativeKotlinApprovalRevisionFence(root),identity,drafts,sources,store(),reader,inventory)
        fun previewTransition(w:String="tier-next",week:Int=2,day:Int=0,recovery:Boolean=false)=
            transitions().previewScheduled("owner-1","cycle-1",day,
                NativeTieredNextSelection(w,week,recovery),calendar())
        assertTrue(prepareSupport() is NativeCustomPrepareResult.Rejected)
        val first=prepared.prepareTieredInitial("owner-1",initial.nextWorkoutId,"cycle-1",1,0,
            inputs(),reader,inventory) as NativeCustomPrepareResult.Prepared
        assertTrue(coldJournal().issueTieredInitial("owner-1",initial.nextWorkoutId,"cycle-1",1,0,
            inputs(),reader) is NativeCustomJournalResult.Durable)
        assertTrue(prepareSupport() is NativeCustomPrepareResult.Rejected)
        fun finish(workoutId:String,snapshot:CustomWorkoutDaySnapshot,load:BigDecimal,reps:Int) {
            val target=snapshot.slots.single().sets.single()
            assertTrue(coldJournal().record("owner-1",workoutId,
                CustomWorkoutJournalOperation.Record("${workoutId}-actual",0,
                    CustomWorkoutVisitKey(target.setKey,CustomWorkoutVisitPart.MAIN),
                    KotlinJournalSetOutcome.COMPLETED,KotlinPerformedSet(load,LoadUnit.LB,reps,0)))
                is NativeCustomJournalResult.Durable)
            val result=NativeKotlinCustomHistoryTransaction(history,coldJournal(),fence,identity)
                .complete("owner-1","athlete-1",workoutId,
                    CustomWorkoutJournalOperation.Finish("${workoutId}-finish",1))
            assertTrue(result.toString(),result is NativeCustomFinishResult.Committed)
        }
        finish(initial.nextWorkoutId,first.workout.snapshot,n("25"),5)
        assertTrue(previewTransition() is NativeSavedNamedReview.Rejected)
        assertEquals(NativeCustomNextCycleSlot.Next(1,2,"Day 2"),
            calendar().reviewTieredCalendar("owner-1","cycle-1"))
        assertTrue(prepareSupport("out-of-order",4) is NativeCustomPrepareResult.Rejected)
        assertEquals(NativeCustomJournalResult.Rejected(NativeCustomJournalRefusal.NOT_PREPARED),issueSupport())
        assertTrue(prepareSupport(day=0) is NativeCustomPrepareResult.Rejected)
        val preparation=prepareSupport()
        assertTrue(preparation.toString(),preparation is NativeCustomPrepareResult.Prepared)
        val support=(preparation as NativeCustomPrepareResult.Prepared).workout
        assertEquals(NativeCustomPreparationAuthority.TIERED_SUPPORT,support.authority)
        assertEquals(consent.receipt.fingerprintSha256,support.consentFingerprint)
        assertEquals(set(),support.snapshot.slots.single().sets.single().prescription)
        assertEquals(NativeCustomPreparedRead.Found(support),coldPrepared().open("owner-1","support-day"))
        assertEquals(NativeCustomNextCycleSlot.Prepared("support-day",1,2,"Day 2"),
            calendar().reviewTieredCalendar("owner-1","cycle-1"))
        assertEquals(NativeCustomPrepareResult.Prepared(support,true),prepareSupport())
        assertTrue(prepareSupport("another-support") is NativeCustomPrepareResult.Rejected)
        assertTrue(coldJournal().issueNamed("owner-1","support-day","cycle-1",1,2,inputs(),reader)
            is NativeCustomJournalResult.Rejected)
        assertTrue(coldJournal().issue("owner-1","support-day","cycle-1",1,2,inputs())
            is NativeCustomJournalResult.Rejected)
        val issued=issueSupport()
        assertTrue(issued.toString(),issued is NativeCustomJournalResult.Durable)
        assertEquals(support.snapshot,(issued as NativeCustomJournalResult.Durable).issued.journal.originalSnapshot)
        assertEquals(NativeCustomNextCycleSlot.ResumeIssued("support-day",1,2,"Day 2"),
            calendar().reviewTieredCalendar("owner-1","cycle-1"))
        finish("support-day",support.snapshot,n("20"),12)
        assertTrue(prepareSupport("another-support") is NativeCustomPrepareResult.Rejected)
        assertEquals(NativeCustomNextCycleSlot.Next(1,4,"Day 4"),
            calendar().reviewTieredCalendar("owner-1","cycle-1"))
        val lastSupport=prepareSupport("support-last",4) as NativeCustomPrepareResult.Prepared
        assertTrue(issueSupport("support-last",4) is NativeCustomJournalResult.Durable)
        finish("support-last",lastSupport.workout.snapshot,n("20"),12)
        assertEquals(NativeCustomNextCycleSlot.Next(2,0,"Day 0"),
            calendar().reviewTieredCalendar("owner-1","cycle-1"))
        val ledger=reader.readTieredLedger("owner-1","athlete-1",proposal.request.scope,
            initial.slotId,initial.tier,2) as NativeOwnerTieredLedgerResult.Review
        assertEquals(listOf(initial.nextWorkoutId),ledger.exposures.map{it.workoutId})
        assertTrue(previewTransition(day=2) is NativeSavedNamedReview.Rejected)
        assertTrue(previewTransition(week=3) is NativeSavedNamedReview.Rejected)
        assertEquals(NativeTieredTransitionRecovery.Refused(NativeTieredTransitionConsentResult.MISSING),
            transitions().recoverScheduled("owner-1","cycle-1",2,0,calendar()))
        val next=previewTransition()
        assertTrue(next.toString(),next is NativeSavedNamedReview.Signed)
        val reviewed=next as NativeSavedNamedReview.Signed
        assertEquals(TieredLinearState(0,n("30")),reviewed.nextTierCandidate?.result?.state)
        assertEquals(NativeTieredTransitionConsentResult.CONFIRMATION_REQUIRED,
            transitions().confirmScheduled("owner-1","cycle-1",reviewed,calendar(),false,false,false))
        assertEquals(NativeTieredTransitionConsentResult.RECORDED,
            transitions().confirmScheduled("owner-1","cycle-1",reviewed,calendar(),true,false,false))
        val recovered=transitions().recoverScheduled("owner-1","cycle-1",2,0,calendar())
            as NativeTieredTransitionRecovery.Ready
        assertEquals(reviewed,recovered.review)
        assertTrue(previewTransition("new-id") is NativeSavedNamedReview.Rejected)
        fun inspectTransition()=transitions().inspectScheduled("owner-1","cycle-1","tier-next",2,0,calendar(),preflight)
        val pre=inspectTransition() as NativeFirstTierTransitionPreflight.Reviewed
        assertEquals(recovered.receiptFingerprint,pre.receiptFingerprint)
        val oldStaged=transitions().prepareScheduled("owner-1","cycle-1",pre,calendar(),preflight,coldPrepared())
            as NativeCustomPrepareResult.Prepared
        assertEquals(NativeCustomNextCycleSlot.Prepared("tier-next",2,0,"Day 0"),
            calendar().reviewTieredCalendar("owner-1","cycle-1"))
        assertEquals(pre,inspectTransition())
        assertNull(transitions().reviewStaleScheduled("owner-1","cycle-1",2,0,calendar()))
        // The corrected initial actual now misses its minimum reps. The
        // original 30 lb stage-1 target must not be issued or silently edited.
        val correction=NativeKotlinCustomHistoryCorrectionTransaction(history,coldJournal(),
            NativeKotlinCustomSavedCorrectionFileStore(root,fence,identity,coldJournal(),history),fence,identity)
            .correct("owner-1","athlete-1",initial.nextWorkoutId,
                CustomSavedVisitCorrection("scheduled-tier-correction",0,
                    CustomWorkoutVisitKey(first.workout.snapshot.slots.single().sets.single().setKey,
                        CustomWorkoutVisitPart.MAIN),"${initial.nextWorkoutId}-actual",
                    KotlinJournalSetOutcome.COMPLETED,KotlinPerformedSet(n("25"),LoadUnit.LB,1,0)))
        assertTrue(correction.toString(),correction is NativeCustomCorrectionTransactionResult.Committed)
        assertTrue(transitions().issueScheduled("owner-1","cycle-1",oldStaged.workout,
            calendar(),preflight,coldPrepared(),coldJournal()) is NativeCustomJournalResult.Rejected)
        fun staleReview()=transitions().reviewStaleScheduled("owner-1","cycle-1",2,0,calendar())
        val stale=checkNotNull(staleReview())
        assertEquals(oldStaged.workout,stale.preparation)
        assertEquals(recovered.receiptFingerprint,stale.previousReceiptFingerprint)
        assertEquals(n("30"),(stale.previousTargets.single().load as DraftSetLoad.Verified).value)
        assertEquals(TieredLinearState(1,n("25")),stale.review.nextTierCandidate?.result?.state)
        assertFalse(transitions().cancelStaleScheduled("owner-1","cycle-1",stale,calendar(),coldPrepared(),false))
        assertFalse(transitions().cancelStaleScheduled("owner-1","cycle-1",stale.copy(previousReceiptFingerprint="0".repeat(64)),
            calendar(),coldPrepared(),true))
        assertEquals(NativeTieredTransitionConsentResult.CONFLICT_OR_CORRUPT,
            transitions().replaceStaleScheduled("owner-1","cycle-1",stale,calendar(),true,false,false))
        assertTrue(transitions().cancelStaleScheduled("owner-1","cycle-1",stale,calendar(),coldPrepared(),true))
        assertEquals(NativeCustomPreparedRead.Missing,coldPrepared().open("owner-1","tier-next"))
        val replacement=checkNotNull(staleReview())
        assertNull(replacement.preparation)
        assertEquals(stale.review,replacement.review)
        assertEquals(NativeTieredTransitionConsentResult.CONFIRMATION_REQUIRED,
            transitions().replaceStaleScheduled("owner-1","cycle-1",replacement,calendar(),false,false,false))
        assertEquals(NativeTieredTransitionConsentResult.REPLACED,
            transitions().replaceStaleScheduled("owner-1","cycle-1",replacement,calendar(),true,false,false))
        assertNull(staleReview())
        val corrected=inspectTransition() as NativeFirstTierTransitionPreflight.Reviewed
        assertNotEquals(pre.receiptFingerprint,corrected.receiptFingerprint)
        val staged=transitions().prepareScheduled("owner-1","cycle-1",corrected,calendar(),preflight,coldPrepared())
            as NativeCustomPrepareResult.Prepared
        val second=transitions().issueScheduled("owner-1","cycle-1",staged.workout,calendar(),preflight,coldPrepared(),coldJournal())
        assertTrue(second.toString(),second is NativeCustomJournalResult.Durable)
        assertEquals(NativeCustomNextCycleSlot.ResumeIssued("tier-next",2,0,"Day 0"),
            calendar().reviewTieredCalendar("owner-1","cycle-1"))
        finish("tier-next",staged.workout.snapshot,n("25"),5)
        assertEquals(NativeCustomNextCycleSlot.Next(2,2,"Day 2"),
            calendar().reviewTieredCalendar("owner-1","cycle-1"))
        assertTrue(previewTransition("tier-third",3) is NativeSavedNamedReview.Rejected)
        for(day in listOf(2,4)) {
            val id="support-w2-d"+day
            val p=prepareSupport(id,day,2) as NativeCustomPrepareResult.Prepared
            assertEquals(set(),p.workout.snapshot.slots.single().sets.single().prescription)
            assertTrue(issueSupport(id,day,2) is NativeCustomJournalResult.Durable)
            finish(id,p.workout.snapshot,n("20"),12)
        }
        assertEquals(NativeCustomNextCycleSlot.Next(3,0,"Day 0"),
            calendar().reviewTieredCalendar("owner-1","cycle-1"))
        val third=previewTransition("tier-third",3) as NativeSavedNamedReview.Signed
        assertEquals(TieredLinearState(1,n("30")),third.nextTierCandidate?.result?.state)
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
        fun calendar()=NativeKotlinCustomNextCycleSessionReview(
            NativeKotlinApprovalRevisionFence(root),identity,drafts,sources,store(),reader,inventory,prepared)
        assertTrue(calendar().reviewTieredInitial("owner-1","cycle-1",inputs()) is NativeCustomNextCycleSlot.Rejected)
        assertTrue(calendar().reviewTieredCalendar("owner-1","cycle-1") is NativeCustomNextCycleSlot.Rejected)
        val nextSelection=NativeTieredNextSelection("tier-next",2)
        assertTrue(reviewer.previewTieredNext("owner-1","cycle-1",inputs(),nextSelection)
            is NativeSavedNamedReview.Rejected)
        val consent=store().confirmTieredInitial("owner-1","cycle-1",pending,true,reader,inventory)
            as NativeCustomSignoffWrite.Committed
        assertEquals(NativeCustomNextCycleSlot.Next(1,0,"Day 0"),
            calendar().reviewTieredInitial("owner-1","cycle-1",inputs()))
        assertTrue(calendar().review("owner-1","cycle-1",inputs()) is NativeCustomNextCycleSlot.Rejected)
        assertTrue(calendar().reviewTieredInitial("foreign-owner","cycle-1",inputs()) is NativeCustomNextCycleSlot.Rejected)
        assertTrue(calendar().reviewTieredCalendar("foreign-owner","cycle-1") is NativeCustomNextCycleSlot.Rejected)
        assertEquals(NativeCustomNextCycleSlot.Next(1,0,"Day 0"),
            calendar().reviewTieredCalendar("owner-1","cycle-1"))
        assertTrue(prep("other-id") is NativeCustomPrepareResult.Rejected)
        assertTrue(prep(week=2) is NativeCustomPrepareResult.Rejected)
        assertTrue(prep(day=2) is NativeCustomPrepareResult.Rejected)
        assertEquals(NativeCustomJournalResult.Rejected(NativeCustomJournalRefusal.NOT_PREPARED),issue())
        val result=prep()
        assertTrue(result.toString(),result is NativeCustomPrepareResult.Prepared)
        val saved=(result as NativeCustomPrepareResult.Prepared).workout
        assertEquals(NativeCustomNextCycleSlot.Prepared(s.nextWorkoutId,1,0,"Day 0"),
            calendar().reviewTieredInitial("owner-1","cycle-1",inputs()))
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
        assertEquals(NativeCustomNextCycleSlot.ResumeIssued(s.nextWorkoutId,1,0,"Day 0"),
            calendar().reviewTieredInitial("owner-1","cycle-1",inputs()))
        assertEquals(NativeCustomJournalResult.Durable(issued,true),issue())
        assertTrue(reviewer.previewTieredNext("owner-1","cycle-1",inputs(),nextSelection)
            is NativeSavedNamedReview.Rejected)
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
        assertTrue(calendar().reviewTieredInitial("owner-1","cycle-1",inputs()) is NativeCustomNextCycleSlot.Rejected)
        assertEquals(NativeCustomNextCycleSlot.Next(1,2,"Day 2"),
            calendar().reviewTieredCalendar("owner-1","cycle-1"))
        assertTrue(calendar().review("owner-1","cycle-1",inputs()) is NativeCustomNextCycleSlot.Rejected)
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
        fun support(w:String="support-1",week:Int=1,day:Int=2,owner:String="owner-1")=
            NativeKotlinCustomWorkoutPreflight(fence,identity,drafts,sources,store())
                .inspectTieredSupport(owner,w,"cycle-1",week,day,reader,inventory)
        val supportPreview=support()
        assertTrue(supportPreview.toString(),supportPreview is NativeCustomPreflightResult.ReviewedPreview)
        val supporting=(supportPreview as NativeCustomPreflightResult.ReviewedPreview)
        assertEquals(consent.receipt,supporting.receipt)
        assertEquals(1,supporting.session.absoluteWeek)
        assertEquals(2,supporting.session.weekday)
        assertEquals(set(),supporting.session.slots.single().sets.single().prescription)
        assertFalse(supporting.isIssued)
        assertFalse(supporting.mayLog)
        assertEquals(supporting,support())
        assertTrue(support(day=0) is NativeCustomPreflightResult.Rejected)
        assertTrue(support(day=1) is NativeCustomPreflightResult.Rejected)
        assertTrue(support(week=4) is NativeCustomPreflightResult.Rejected)
        assertTrue(support(w=s.nextWorkoutId) is NativeCustomPreflightResult.Rejected)
        assertTrue(support(owner="foreign-owner") is NativeCustomPreflightResult.Rejected)
        assertTrue(prepared.prepare("owner-1","support-1","cycle-1",1,2,inputs()) is NativeCustomPrepareResult.Rejected)
        assertTrue(prepared.prepareNamed("owner-1","support-1","cycle-1",1,2,inputs(),reader,inventory) is NativeCustomPrepareResult.Rejected)
        assertTrue(prep("support-1",1,2) is NativeCustomPrepareResult.Rejected)
        assertEquals(verified,reader.readTieredLedger("owner-1","athlete-1",proposal.request.scope,s.slotId,s.tier,2))
        val next=TieredLinearProgressionDomain.review(proposal.request.copy(
            nextWorkoutId="tier-next",completed=verified.exposures))
        assertTrue(next.toString(),next is TieredLinearResult.Candidate)
        val candidate=next as TieredLinearResult.Candidate
        assertEquals(TieredLinearState(0,n("30")),candidate.state)
        assertEquals(TieredLinearDecision.EARNED_INCREMENT,candidate.transitions.single().decision)
        assertFalse(candidate.mayIssueWorkout)
        val reviewed=reviewer.previewTieredNext("owner-1","cycle-1",inputs(),nextSelection)
        assertTrue(reviewed.toString(),reviewed is NativeSavedNamedReview.Signed)
        val transition=checkNotNull((reviewed as NativeSavedNamedReview.Signed).nextTierCandidate)
        assertEquals(candidate,transition.result)
        assertEquals(proposal.request.initialState,transition.request.initialState)
        assertEquals(proposal.request.maximumAuthorizedIncrease,transition.request.maximumAuthorizedIncrease)
        assertEquals(verified.manifestRevision,transition.historyRevision)
        // The exact verified ledger behind a real native transition must
        // survive the frozen receipt representation without changing any target.
        val frozen=NativeTieredHistoryPrefixCodec.encode(transition.request.scope,
            s.tier,s.slotId,transition.request.completed)
        val restored=NativeTieredHistoryPrefixCodec.decode(frozen,transition.request.scope,
            s.tier,s.slotId)
        assertEquals(verified.exposures,restored)
        assertEquals(transition.result,TieredLinearProgressionDomain.review(
            transition.request.copy(completed=restored)))
        assertArrayEquals(frozen,NativeTieredHistoryPrefixCodec.encode(
            transition.request.scope,s.tier,s.slotId,restored))
        val changedPrefix=restored.map{exposure->exposure.copy(
            observations=exposure.observations.map{it.copy(actualReps=1)})}
        assertFalse(frozen.contentEquals(NativeTieredHistoryPrefixCodec.encode(
            transition.request.scope,s.tier,s.slotId,changedPrefix)))
        assertNotEquals(transition.result,TieredLinearProgressionDomain.review(
            transition.request.copy(completed=changedPrefix)))
        assertEquals(saved.snapshot.slots.single().sets.single().prescription,
            transition.draft.proposal.draft.weekOverrides.getValue("base").single{it.weekInBlock==1}.sets.single())
        assertEquals(n("30"),(transition.draft.proposal.draft.weekOverrides.getValue("base")
            .single{it.weekInBlock==2}.sets.single().load as DraftSetLoad.Verified).value)
        assertFalse(transition.mayIssueWorkout)
        assertFalse(transition.maySaveProgram)
        assertFalse(transition.requiresExplicitResetReview)
        assertEquals(CustomCoachAuditStatus.READY_FOR_ATHLETE_REVIEW,
            transition.transitionAudit.status)
        assertTrue(transition.transitionAudit.issues.isEmpty())
        assertEquals(transition.draft.proposal.draft,
            transition.transitionAudit.structuralReview?.verifiedDraft)
        assertEquals(transition.draft.proposal.draft.revision,
            transition.transitionAudit.auditedCycleRevision)
        assertEquals(consent.receipt.policyReferences,
            transition.transitionAudit.coachingPolicyRevisions)
        val recovery=reviewer.previewTieredNext("owner-1","cycle-1",inputs(),nextSelection.copy(recovery=true))
        assertTrue(recovery.toString(),recovery is NativeSavedNamedReview.Signed)
        val light=checkNotNull((recovery as NativeSavedNamedReview.Signed).nextTierCandidate)
        assertEquals(candidate.state,light.result.state)
        assertTrue(light.result.recovery)
        assertEquals(CustomCoachAuditStatus.READY_FOR_ATHLETE_REVIEW,
            light.transitionAudit.status)
        assertEquals(light.draft.proposal.draft,
            light.transitionAudit.structuralReview?.verifiedDraft)
        assertNotEquals(transition.transitionAudit.structuralReview?.verifiedDraft,
            light.transitionAudit.structuralReview?.verifiedDraft)
        assertEquals(n("15"),light.result.targets.single().load)
        assertFalse(light.result.targets.single().repOut)
        // Identical history is necessary but insufficient for consent:
        // normal and recovery proposals must remain distinct prescriptions.
        assertArrayEquals(frozen,NativeTieredHistoryPrefixCodec.encode(
            light.request.scope,s.tier,s.slotId,light.request.completed))
        assertNotEquals(transition.result.targets,light.result.targets)
        // Persist consent under the same owner fence only after a fresh
        // full-cycle review, with separate acceptance for recovery.
        fun transitionStore()=NativeKotlinFirstTierTransitionConsentStore(root,
            NativeKotlinApprovalRevisionFence(root),identity,drafts,sources,store(),reader,inventory)
        assertEquals(NativeTieredTransitionConsentResult.MISSING,
            transitionStore().verify("owner-1","cycle-1",reviewed))
        assertEquals(NativeTieredTransitionRecovery.Refused(NativeTieredTransitionConsentResult.MISSING),
            transitionStore().recover("owner-1","cycle-1"))
        assertEquals(NativeTieredTransitionConsentResult.CONFIRMATION_REQUIRED,
            transitionStore().confirm("owner-1","cycle-1",reviewed,false))
        assertEquals(NativeTieredTransitionConsentResult.CONFIRMATION_REQUIRED,
            transitionStore().confirm("owner-1","cycle-1",recovery,true))
        fun nextPreflight(w:String="tier-next",week:Int=2,day:Int=0)=
            preflight.inspectFirstTierTransition("owner-1",w,"cycle-1",week,day,transitionStore())
        assertTrue(nextPreflight() is NativeFirstTierTransitionPreflight.Rejected)
        assertEquals(NativeTieredTransitionConsentResult.RECORDED,
            transitionStore().confirm("owner-1","cycle-1",reviewed,true))
        val projected=nextPreflight()
        assertTrue(projected.toString(),projected is NativeFirstTierTransitionPreflight.Reviewed)
        val snapshot=(projected as NativeFirstTierTransitionPreflight.Reviewed).session
        assertEquals(2,snapshot.absoluteWeek)
        assertEquals(transition.draft.proposal.draft.revision,snapshot.stamp.cycleRevision)
        assertEquals(transition.draft.proposal.draft.weekOverrides.getValue("base")
            .single{it.weekInBlock==2}.sets.single(),snapshot.slots.single().sets.single().prescription)
        assertFalse(projected.mayIssueWorkout)
        assertTrue(nextPreflight(w="another-id") is NativeFirstTierTransitionPreflight.Rejected)
        assertTrue(nextPreflight(week=3) is NativeFirstTierTransitionPreflight.Rejected)
        assertTrue(nextPreflight(day=2) is NativeFirstTierTransitionPreflight.Rejected)
        assertEquals(NativeTieredTransitionConsentResult.VERIFIED,
            transitionStore().verify("owner-1","cycle-1",reviewed))
        val cold=transitionStore().recover("owner-1","cycle-1")
        assertTrue(cold.toString(),cold is NativeTieredTransitionRecovery.Ready)
        val recovered=cold as NativeTieredTransitionRecovery.Ready
        assertEquals(reviewed,recovered.review)
        assertEquals(projected.receiptFingerprint,recovered.receiptFingerprint)
        assertFalse(recovered.mayIssueWorkout)
        val receiptFile=java.nio.file.Files.list(root).use{files->
            files.filter{it.fileName.toString().startsWith("tier-first-transition-") &&
                it.fileName.toString().endsWith(".bin")}.findFirst().get()
        }
        val originalBytes=java.nio.file.Files.readAllBytes(receiptFile)
        java.nio.file.Files.write(receiptFile,originalBytes.copyOf(originalBytes.size-1))
        assertEquals(NativeTieredTransitionRecovery.Refused(NativeTieredTransitionConsentResult.CONFLICT_OR_CORRUPT),
            transitionStore().recover("owner-1","cycle-1"))
        java.nio.file.Files.write(receiptFile,originalBytes)
        assertEquals(cold,transitionStore().recover("owner-1","cycle-1"))
        assertEquals(NativeTieredTransitionConsentResult.REPLAYED,
            transitionStore().confirm("owner-1","cycle-1",reviewed,true))
        assertEquals(NativeTieredTransitionConsentResult.CONFLICT_OR_CORRUPT,
            transitionStore().confirm("owner-1","cycle-1",recovery,true,recoveryConfirmed=true))
        active=NativeKotlinOwnerIdentity("other-owner","other-athlete")
        assertEquals(NativeTieredTransitionConsentResult.NOT_AUTHORIZED,
            transitionStore().verify("owner-1","cycle-1",reviewed))
        active=NativeKotlinOwnerIdentity("owner-1","athlete-1")
        assertTrue(reviewer.previewTieredNext("owner-1","cycle-1",inputs(),nextSelection.copy(absoluteWeek=3))
            is NativeSavedNamedReview.Rejected)
        assertTrue(reviewer.previewTieredNext("owner-1","cycle-1",inputs(),nextSelection.copy(nextWorkoutId=s.nextWorkoutId))
            is NativeSavedNamedReview.Rejected)
        assertEquals(2L,(drafts.read("owner-1","cycle-1") as NativeCustomDraftReadResult.Found).draft.revision)
        assertTrue(prep("tier-next",2,0) is NativeCustomPrepareResult.Rejected)
        fun prepareNext()=prepared.prepareFirstTierTransition("owner-1","tier-next",
            "cycle-1",2,0,transitionStore())
        fun issueNext()=coldIssuer().issueFirstTierTransition("owner-1","tier-next",
            "cycle-1",2,0,transitionStore())
        assertEquals(NativeCustomJournalResult.Rejected(NativeCustomJournalRefusal.NOT_PREPARED),issueNext())
        val preparation=prepareNext()
        assertTrue(preparation.toString(),preparation is NativeCustomPrepareResult.Prepared)
        val nextPrepared=(preparation as NativeCustomPrepareResult.Prepared).workout
        assertEquals(NativeCustomPreparationAuthority.TIERED_TRANSITION,nextPrepared.authority)
        assertEquals(NativeCustomNextCycleSlot.Prepared("tier-next",2,0,"Day 0"),
            calendar().reviewTieredCalendar("owner-1","cycle-1"))
        assertEquals(projected.receiptFingerprint,nextPrepared.consentFingerprint)
        assertNotEquals(consent.receipt.fingerprintSha256,nextPrepared.consentFingerprint)
        assertEquals(snapshot,nextPrepared.snapshot)
        assertEquals(NativeCustomPrepareResult.Prepared(nextPrepared,true),prepareNext())
        assertEquals(cold,transitionStore().recover("owner-1","cycle-1"))
        assertEquals(NativeTieredTransitionConsentResult.REPLAYED,
            transitionStore().confirm("owner-1","cycle-1",reviewed,true))
        assertTrue(reviewer.previewTieredNext("owner-1","cycle-1",inputs(),nextSelection)
            is NativeSavedNamedReview.Rejected)
        assertTrue(issue("tier-next",2,0) is NativeCustomJournalResult.Rejected)
        assertTrue(issuer.issueNamed("owner-1","tier-next","cycle-1",2,0,inputs(),reader)
            is NativeCustomJournalResult.Rejected)
        assertTrue(issuer.issue("owner-1","tier-next","cycle-1",2,0,inputs())
            is NativeCustomJournalResult.Rejected)
        // Corruption after preparation must fail at first issuance, not
        // silently authorize the stored target. Restoring exact bytes allows
        // the still-current approval, without changing the preparation.
        java.nio.file.Files.write(receiptFile,originalBytes.copyOf(originalBytes.size-1))
        assertEquals(NativeCustomJournalResult.Rejected(NativeCustomJournalRefusal.CONSENT_CHANGED),issueNext())
        java.nio.file.Files.write(receiptFile,originalBytes)
        val nextIssued=issueNext()
        assertTrue(nextIssued.toString(),nextIssued is NativeCustomJournalResult.Durable)
        val second=(nextIssued as NativeCustomJournalResult.Durable).issued
        assertEquals(snapshot,second.journal.originalSnapshot)
        assertEquals(NativeCustomNextCycleSlot.ResumeIssued("tier-next",2,0,"Day 0"),
            calendar().reviewTieredCalendar("owner-1","cycle-1"))
        assertEquals(NativeCustomJournalResult.Durable(second,true),issueNext())
        val secondTarget=snapshot.slots.single().sets.single()
        val nextRecord=coldIssuer().record("owner-1","tier-next",CustomWorkoutJournalOperation.Record(
            "tier-next-actual",0,CustomWorkoutVisitKey(secondTarget.setKey,CustomWorkoutVisitPart.MAIN),
            KotlinJournalSetOutcome.COMPLETED,KotlinPerformedSet(n("30"),LoadUnit.LB,5,0)))
        assertTrue(nextRecord.toString(),nextRecord is NativeCustomJournalResult.Durable)
        val secondFinish=NativeKotlinCustomHistoryTransaction(history,coldIssuer(),fence,identity)
            .complete("owner-1","athlete-1","tier-next",CustomWorkoutJournalOperation.Finish("tier-next-finish",1))
        assertTrue(secondFinish.toString(),secondFinish is NativeCustomFinishResult.Committed)
        assertEquals(NativeCustomNextCycleSlot.Next(1,2,"Day 2"),
            calendar().reviewTieredCalendar("owner-1","cycle-1"))
        val twoWeeks=reader.readTieredLedger("owner-1","athlete-1",proposal.request.scope,s.slotId,s.tier,3)
        assertTrue(twoWeeks.toString(),twoWeeks is NativeOwnerTieredLedgerResult.Review)
        assertEquals(listOf(s.nextWorkoutId,"tier-next"),
            (twoWeeks as NativeOwnerTieredLedgerResult.Review).exposures.map{it.workoutId})

        // Week three is a genuinely new consent, not a mutation of week
        // two. A correction may change future evidence without changing
        // the earlier immutable issued snapshot or consent bytes.
        val thirdSelection=NativeTieredNextSelection("tier-third",3)
        fun thirdPreview()=transitionStore().preview("owner-1","cycle-1",inputs(),thirdSelection)
        assertTrue(thirdPreview() is NativeSavedNamedReview.Signed)
        val correctionStore=NativeKotlinCustomSavedCorrectionFileStore(root,fence,identity,
            coldIssuer(),history)
        val corrector=NativeKotlinCustomHistoryCorrectionTransaction(history,coldIssuer(),
            correctionStore,fence,identity)
        fun correctFirst(event:String,revision:Long,predecessor:String,reps:Int)=
            corrector.correct("owner-1","athlete-1",s.nextWorkoutId,
                CustomSavedVisitCorrection(event,revision,
                    CustomWorkoutVisitKey(target.setKey,CustomWorkoutVisitPart.MAIN),predecessor,
                    KotlinJournalSetOutcome.COMPLETED,KotlinPerformedSet(n("25"),LoadUnit.LB,reps,0)))
        val incompatible=correctFirst("tier-correct-fail",0,"tier-actual",1)
        assertTrue(incompatible.toString(),incompatible is NativeCustomCorrectionTransactionResult.Committed)
        assertTrue(thirdPreview() is NativeSavedNamedReview.Rejected)
        assertArrayEquals(originalBytes,java.nio.file.Files.readAllBytes(receiptFile))
        val compatible=correctFirst("tier-correct-good",1,"tier-correct-fail",6)
        assertTrue(compatible.toString(),compatible is NativeCustomCorrectionTransactionResult.Committed)
        val thirdReview=thirdPreview()
        assertTrue(thirdReview.toString(),thirdReview is NativeSavedNamedReview.Signed)
        val thirdSigned=thirdReview as NativeSavedNamedReview.Signed
        val third=checkNotNull(thirdSigned.nextTierCandidate)
        assertEquals(1,third.priorTierTransitions.size)
        assertEquals(5,third.priorTierTransitions.single().request.completed.single()
            .observations.single().actualReps)
        assertEquals(6,third.request.completed.first().observations.single().actualReps)
        assertEquals(transition.draft.proposal.draft.weekOverrides.getValue("base"),
            third.draft.proposal.draft.weekOverrides.getValue("base").filter{it.weekInBlock<3})
        assertEquals(4L,third.draft.proposal.draft.revision)
        assertEquals(TieredLinearProgressionDomain.review(third.request),third.result)
        assertEquals(NativeTieredTransitionConsentResult.RECORDED,
            transitionStore().confirm("owner-1","cycle-1",thirdSigned,true))
        val thirdRecovered=transitionStore().recover("owner-1","cycle-1",3)
        assertTrue(thirdRecovered.toString(),thirdRecovered is NativeTieredTransitionRecovery.Ready)
        assertEquals(thirdSigned,(thirdRecovered as NativeTieredTransitionRecovery.Ready).review)
        fun prepareThird()=prepared.prepareFirstTierTransition("owner-1","tier-third","cycle-1",3,0,
            transitionStore())
        fun issueThird()=coldIssuer().issueFirstTierTransition("owner-1","tier-third","cycle-1",3,0,
            transitionStore())
        val thirdPrepared=prepareThird()
        assertTrue(thirdPrepared.toString(),thirdPrepared is NativeCustomPrepareResult.Prepared)
        val thirdWorkout=(thirdPrepared as NativeCustomPrepareResult.Prepared).workout
        assertEquals(NativeCustomPrepareResult.Prepared(thirdWorkout,true),prepareThird())
        assertNotEquals(nextPrepared.consentFingerprint,thirdWorkout.consentFingerprint)
        // Earlier receipt corruption also prevents the later issue.
        java.nio.file.Files.write(receiptFile,originalBytes.copyOf(originalBytes.size-1))
        assertEquals(NativeCustomJournalResult.Rejected(NativeCustomJournalRefusal.CONSENT_CHANGED),issueThird())
        java.nio.file.Files.write(receiptFile,originalBytes)
        val thirdIssued=issueThird()
        assertTrue(thirdIssued.toString(),thirdIssued is NativeCustomJournalResult.Durable)
        val thirdTarget=thirdWorkout.snapshot.slots.single().sets.single()
        val thirdLoad=(thirdTarget.prescription.load as DraftSetLoad.Verified).value
        assertEquals(third.result.targets.single().load,thirdLoad)
        val thirdRecord=coldIssuer().record("owner-1","tier-third",CustomWorkoutJournalOperation.Record(
            "tier-third-actual",0,CustomWorkoutVisitKey(thirdTarget.setKey,CustomWorkoutVisitPart.MAIN),
            KotlinJournalSetOutcome.COMPLETED,KotlinPerformedSet(thirdLoad,LoadUnit.LB,5,0)))
        assertTrue(thirdRecord.toString(),thirdRecord is NativeCustomJournalResult.Durable)
        val thirdFinish=NativeKotlinCustomHistoryTransaction(history,coldIssuer(),fence,identity)
            .complete("owner-1","athlete-1","tier-third",CustomWorkoutJournalOperation.Finish("tier-third-finish",1))
        assertTrue(thirdFinish.toString(),thirdFinish is NativeCustomFinishResult.Committed)
        val threeWeeks=reader.readTieredLedger("owner-1","athlete-1",proposal.request.scope,s.slotId,s.tier,4)
        assertTrue(threeWeeks.toString(),threeWeeks is NativeOwnerTieredLedgerResult.Review)
        assertEquals(listOf(s.nextWorkoutId,"tier-next","tier-third"),
            (threeWeeks as NativeOwnerTieredLedgerResult.Review).exposures.map{it.workoutId})
        assertEquals(6,threeWeeks.exposures.first().observations.single().actualReps)
        assertArrayEquals(originalBytes,java.nio.file.Files.readAllBytes(receiptFile))
        assertTrue(transitionStore().preview("owner-1","cycle-1",inputs(),
            NativeTieredNextSelection("tier-fourth",4)) is NativeSavedNamedReview.Rejected)
        assertEquals(2L,(drafts.read("owner-1","cycle-1") as NativeCustomDraftReadResult.Found).draft.revision)
        assertTrue(reviewer.reviewSignedTieredInitial("owner-1","cycle-1",inputs())
            is NativeSavedNamedReview.Rejected)
        assertTrue(store().revoke("owner-1","cycle-1",consent.receipt.recordRevision)
            is NativeCustomSignoffWrite.Committed)
        assertEquals(NativeTieredTransitionConsentResult.STALE_OR_UNVERIFIED,
            transitionStore().verify("owner-1","cycle-1",reviewed))
        assertEquals(NativeTieredTransitionRecovery.Refused(NativeTieredTransitionConsentResult.STALE_OR_UNVERIFIED),
            transitionStore().recover("owner-1","cycle-1"))
        assertTrue(nextPreflight() is NativeFirstTierTransitionPreflight.Rejected)
        assertTrue(reviewer.previewTieredNext("owner-1","cycle-1",inputs(),nextSelection)
            is NativeSavedNamedReview.Rejected)
        assertTrue(issueThird() is NativeCustomJournalResult.Durable)
        assertTrue(issueNext() is NativeCustomJournalResult.Durable)
        assertTrue(issue() is NativeCustomJournalResult.Durable)
        assertTrue(issuer.issueNamed("owner-1",s.nextWorkoutId,"cycle-1",1,0,inputs(),reader)
            is NativeCustomJournalResult.Rejected)
        active=NativeKotlinOwnerIdentity("other-owner","other-athlete")
        assertEquals(NativeCustomJournalResult.Rejected(NativeCustomJournalRefusal.NOT_AUTHORIZED),issue())
    }

    @Test fun recoveryAndResetNeedSeparateConsentThroughActualIssuedWeeks() {
        initialize(6)
        approved()
        enroll()
        val initial=selection()
        val initialProposal=review().previewTieredInitial(initial) as NativeTieredInitialReview.Ready
        assertTrue(review().acceptTieredInitial(initial,initialProposal,true) is NativeTieredInitialAcceptance.Saved)
        val inventory=NativeKotlinCustomOutstandingWorkoutInventory(prepared,issuer)
        val reviewer=NativeKotlinSavedNamedPercentageReview(fence,identity,drafts,sources,
            store(),reader,inventory)
        val pending=reviewer.reviewTieredInitial("owner-1","cycle-1",inputs()) as NativeSavedNamedReview.Ready
        assertTrue(store().confirmTieredInitial("owner-1","cycle-1",pending,true,reader,inventory)
            is NativeCustomSignoffWrite.Committed)
        fun coldStore()=NativeKotlinFirstTierTransitionConsentStore(root,
            NativeKotlinApprovalRevisionFence(root),identity,drafts,sources,store(),reader,inventory)
        fun coldJournal()=NativeKotlinCustomWorkoutJournalFileStore(root,fence,identity,
            NativeKotlinCustomWorkoutPreparedStore(root,fence,identity,preflight),preflight,history,composer)
        fun finish(workoutId:String,snapshot:CustomWorkoutDaySnapshot,reps:Int) {
            val target=snapshot.slots.single().sets.single()
            val load=(target.prescription.load as DraftSetLoad.Verified).value
            val actual=KotlinPerformedSet(load,LoadUnit.LB,reps,if(reps==0)null else 0)
            val outcome=if(reps==0)KotlinJournalSetOutcome.FAILED_ATTEMPT else KotlinJournalSetOutcome.COMPLETED
            val recorded=coldJournal().record("owner-1",workoutId,CustomWorkoutJournalOperation.Record(
                "$workoutId-actual",0,CustomWorkoutVisitKey(target.setKey,CustomWorkoutVisitPart.MAIN),
                outcome,actual))
            assertTrue(recorded.toString(),recorded is NativeCustomJournalResult.Durable)
            val finished=NativeKotlinCustomHistoryTransaction(history,coldJournal(),fence,identity)
                .complete("owner-1","athlete-1",workoutId,CustomWorkoutJournalOperation.Finish("$workoutId-finish",1))
            assertTrue(finished.toString(),finished is NativeCustomFinishResult.Committed)
        }
        val first=prepared.prepareTieredInitial("owner-1",initial.nextWorkoutId,"cycle-1",1,0,
            inputs(),reader,inventory)
        assertTrue(first.toString(),first is NativeCustomPrepareResult.Prepared)
        assertTrue(coldJournal().issueTieredInitial("owner-1",initial.nextWorkoutId,"cycle-1",1,0,inputs(),reader)
            is NativeCustomJournalResult.Durable)
        val firstSnapshot=(first as NativeCustomPrepareResult.Prepared).workout.snapshot
        finish(initial.nextWorkoutId,firstSnapshot,1)
        // Cold initial review must not reopen a completed initial session.
        assertTrue(store().recoverTieredInitialReview("owner-1","cycle-1",reader,inventory)
            is NativeSavedNamedReview.Rejected)

        fun transition(week:Int,workout:String,recovery:Boolean,stage:Int,
            expectedReference:String,expectedLoad:String,reset:Boolean,actualReps:Int) {
            val selection=NativeTieredNextSelection(workout,week,recovery)
            val preview=coldStore().preview("owner-1","cycle-1",inputs(),selection)
            assertTrue(preview.toString(),preview is NativeSavedNamedReview.Signed)
            assertEquals(preview,coldStore().previewUsingSavedRules("owner-1","cycle-1",selection))
            assertTrue(coldStore().previewUsingSavedRules("foreign-owner","cycle-1",selection)
                is NativeSavedNamedReview.Rejected)
            var displayed=preview as NativeSavedNamedReview.Signed
            val candidate=checkNotNull(displayed.nextTierCandidate)
            assertEquals(stage,candidate.result.state.stage)
            assertEquals(0,n(expectedReference).compareTo(candidate.result.state.load))
            assertEquals(0,n(expectedLoad).compareTo(candidate.result.targets.single().load))
            assertEquals(recovery,candidate.result.recovery)
            assertEquals(reset,candidate.requiresExplicitResetReview)
            assertEquals(!recovery,candidate.result.targets.single().repOut)
            fun prepare()=prepared.prepareFirstTierTransition("owner-1",workout,"cycle-1",week,0,coldStore())
            fun issue()=coldJournal().issueFirstTierTransition("owner-1",workout,"cycle-1",week,0,coldStore())
            assertEquals(NativeTieredTransitionConsentResult.CONFIRMATION_REQUIRED,
                coldStore().confirm("owner-1","cycle-1",displayed,false,
                    resetConfirmed=true,recoveryConfirmed=true))
            if(reset||recovery) {
                assertEquals(NativeTieredTransitionConsentResult.CONFIRMATION_REQUIRED,
                    coldStore().confirm("owner-1","cycle-1",displayed,true))
                assertTrue(prepare() is NativeCustomPrepareResult.Rejected)
                assertEquals(NativeCustomJournalResult.Rejected(NativeCustomJournalRefusal.NOT_PREPARED),issue())
            }
            assertEquals(NativeTieredTransitionConsentResult.RECORDED,
                coldStore().confirm("owner-1","cycle-1",displayed,true,
                    resetConfirmed=reset,recoveryConfirmed=recovery))
            assertEquals(week-1,coldStore().inventoryForRecovery("owner-1"))
            if(week==2) {
                // Before preparation there is no linked journal fingerprint.
                // A forged reset flag with a recomputed checksum must still
                // fail the canonical numerical recovery replay.
                val receipt=java.nio.file.Files.list(root).use{files->
                    files.filter{it.fileName.toString().startsWith("tier-first-transition-")}.findFirst().get()
                }
                val original=java.nio.file.Files.readAllBytes(receipt)
                val payload=original.copyOf(original.size-32)
                val input=java.io.DataInputStream(java.io.ByteArrayInputStream(payload))
                input.readInt();input.readInt()
                repeat(3){input.readUTF()}
                repeat(3){input.readLong()}
                input.readUTF();input.readLong();input.readUTF();input.readInt();input.readBoolean()
                val resetOffset=payload.size-input.available()
                payload[resetOffset]=if(reset)0 else 1
                val forged=payload+java.security.MessageDigest.getInstance("SHA-256").digest(payload)
                java.nio.file.Files.write(receipt,forged)
                assertNull(coldStore().inventoryForRecovery("owner-1"))
                java.nio.file.Files.write(receipt,original)
                assertEquals(1,coldStore().inventoryForRecovery("owner-1"))
                val old=coldStore().recover("owner-1","cycle-1",week)
                    as NativeTieredTransitionRecovery.Ready
                val stalePreparation=prepare() as NativeCustomPrepareResult.Prepared
                assertFalse(prepared.cancelStaleTierPreparation(stalePreparation.workout,coldStore(),true))
                val correction=NativeKotlinCustomHistoryCorrectionTransaction(history,coldJournal(),
                    NativeKotlinCustomSavedCorrectionFileStore(root,fence,identity,coldJournal(),history),
                    fence,identity).correct("owner-1","athlete-1",initial.nextWorkoutId,
                    CustomSavedVisitCorrection("tier-preparation-correction",0,
                        CustomWorkoutVisitKey(firstSnapshot.slots.single().sets.single().setKey,
                            CustomWorkoutVisitPart.MAIN),"${initial.nextWorkoutId}-actual",
                        KotlinJournalSetOutcome.COMPLETED,KotlinPerformedSet(n("25"),LoadUnit.LB,2,0)))
                assertTrue(correction.toString(),correction is NativeCustomCorrectionTransactionResult.Committed)
                assertEquals(NativeTieredTransitionRecovery.Refused(NativeTieredTransitionConsentResult.STALE_OR_UNVERIFIED),
                    coldStore().recover("owner-1","cycle-1",week))
                assertTrue(issue() is NativeCustomJournalResult.Rejected)
                assertFalse(prepared.cancelStaleTierPreparation(stalePreparation.workout,coldStore(),false))
                assertFalse(prepared.cancelStaleTierPreparation(
                    stalePreparation.workout.copy(consentFingerprint="0".repeat(64)),coldStore(),true))
                assertEquals(NativeCustomPreparedRead.Found(stalePreparation.workout),
                    prepared.open("owner-1",workout))
                val coldPrepared=NativeKotlinCustomWorkoutPreparedStore(root,
                    NativeKotlinApprovalRevisionFence(root),identity,preflight)
                assertTrue(coldPrepared.cancelStaleTierPreparation(stalePreparation.workout,coldStore(),true))
                assertEquals(NativeCustomPreparedRead.Missing,prepared.open("owner-1",workout))
                assertFalse(coldPrepared.cancelStaleTierPreparation(stalePreparation.workout,coldStore(),true))
                assertEquals(NativeTieredTransitionRecovery.Refused(NativeTieredTransitionConsentResult.STALE_OR_UNVERIFIED),
                    coldStore().recover("owner-1","cycle-1",week))
                assertTrue(prepare() is NativeCustomPrepareResult.Rejected)
                assertEquals(NativeCustomJournalResult.Rejected(NativeCustomJournalRefusal.NOT_PREPARED),issue())
                val fresh=coldStore().previewUsingSavedRules("owner-1","cycle-1",selection)
                assertTrue(fresh.toString(),fresh is NativeSavedNamedReview.Signed)
                assertEquals(coldStore().preview("owner-1","cycle-1",inputs(),selection),fresh)
                displayed=fresh as NativeSavedNamedReview.Signed
                assertEquals(NativeTieredTransitionConsentResult.CONFLICT_OR_CORRUPT,
                    coldStore().confirm("owner-1","cycle-1",displayed,true,recoveryConfirmed=true))
                assertEquals(NativeTieredTransitionConsentResult.CONFIRMATION_REQUIRED,
                    coldStore().replaceStaleUnprepared("owner-1","cycle-1",displayed,
                        old.receiptFingerprint,true))
                assertEquals(NativeTieredTransitionConsentResult.CONFLICT_OR_CORRUPT,
                    coldStore().replaceStaleUnprepared("owner-1","cycle-1",displayed,
                        "0".repeat(64),true,recoveryConfirmed=true))
                assertEquals(NativeTieredTransitionConsentResult.REPLACED,
                    coldStore().replaceStaleUnprepared("owner-1","cycle-1",displayed,
                        old.receiptFingerprint,true,recoveryConfirmed=true))
                assertEquals(NativeTieredTransitionConsentResult.CONFLICT_OR_CORRUPT,
                    coldStore().replaceStaleUnprepared("owner-1","cycle-1",displayed,
                        old.receiptFingerprint,true,recoveryConfirmed=true))
            }
            val recovered=coldStore().recover("owner-1","cycle-1",week)
            assertTrue(recovered.toString(),recovered is NativeTieredTransitionRecovery.Ready)
            assertEquals(displayed,(recovered as NativeTieredTransitionRecovery.Ready).review)
            val plan=prepare()
            assertTrue(plan.toString(),plan is NativeCustomPrepareResult.Prepared)
            val workoutPlan=(plan as NativeCustomPrepareResult.Prepared).workout
            assertEquals(recovered.receiptFingerprint,workoutPlan.consentFingerprint)
            val target=workoutPlan.snapshot.slots.single().sets.single().prescription
            val intent=checkNotNull(target.percentageIntent)
            assertEquals(stage,intent.tierStage)
            assertEquals(recovery,intent.recovery)
            assertEquals(!recovery,intent.repOut)
            assertEquals(0,n(expectedReference).compareTo(intent.referenceLoad))
            assertEquals(0,n(expectedLoad).compareTo((target.load as DraftSetLoad.Verified).value))
            assertEquals(NativeCustomPrepareResult.Prepared(workoutPlan,true),prepare())
            assertEquals(NativeTieredTransitionConsentResult.STALE_OR_UNVERIFIED,
                coldStore().replaceStaleUnprepared("owner-1","cycle-1",displayed,
                    recovered.receiptFingerprint,true,resetConfirmed=reset,recoveryConfirmed=recovery))
            val issued=issue()
            assertTrue(issued.toString(),issued is NativeCustomJournalResult.Durable)
            assertEquals(workoutPlan.snapshot,(issued as NativeCustomJournalResult.Durable).issued.journal.originalSnapshot)
            finish(workout,workoutPlan.snapshot,actualReps)
        }

        // The 60% recovery is explicitly accepted, never an ordinary weight
        // reduction, and even a high-rep recovery cannot advance the tier.
        transition(2,"tier-recovery",true,1,"25","15",false,12)
        transition(3,"tier-stage-one",false,1,"25","25",false,1)
        transition(4,"tier-stage-two",false,2,"25","25",false,0)
        // A failed stage-two attempt requires an explicitly reviewed reset.
        // Use the canonical physical-load rounding, not a duplicated formula.
        val ledger=reader.readTieredLedger("owner-1","athlete-1",initialProposal.request.scope,
            initial.slotId,initial.tier,5) as NativeOwnerTieredLedgerResult.Review
        val reset=TieredLinearProgressionDomain.review(initialProposal.request.copy(
            nextWorkoutId="tier-reset",completed=ledger.exposures)) as TieredLinearResult.Candidate
        assertTrue(reset.requiresReviewedReset)
        assertTrue(reset.state.load<n("25"))
        transition(5,"tier-reset",false,0,reset.state.load.toString(),reset.state.load.toString(),true,3)
        assertEquals(4,coldStore().inventoryForRecovery("owner-1"))
        val afterResetLedger=reader.readTieredLedger("owner-1","athlete-1",initialProposal.request.scope,
            initial.slotId,initial.tier,6) as NativeOwnerTieredLedgerResult.Review
        val afterReset=TieredLinearProgressionDomain.review(initialProposal.request.copy(
            nextWorkoutId="tier-after-reset",completed=afterResetLedger.exposures)) as TieredLinearResult.Candidate
        assertTrue(afterReset.requiresReviewedReset) // historic reset remains in replay
        // But no second reset approval is needed just because a prior one
        // appears in the complete ledger. Its frozen receipt proves consent.
        transition(6,"tier-after-reset",false,0,afterReset.state.load.toString(),
            afterReset.state.load.toString(),false,3)
        assertEquals(5,coldStore().inventoryForRecovery("owner-1"))
        val receipt=java.nio.file.Files.list(root).use{files->
            files.filter{it.fileName.toString().startsWith("tier-first-transition-") &&
                it.fileName.toString().endsWith("-w6.bin")}.findFirst().get()
        }
        val original=java.nio.file.Files.readAllBytes(receipt)
        val expected=checkNotNull(active)
        val identityBytes=java.io.ByteArrayOutputStream().also{stream->
            java.io.DataOutputStream(stream).use{out->
                out.writeInt(0x50494F31);out.writeInt(1)
                out.writeUTF(expected.ownerKey);out.writeUTF(expected.athleteId)
            }
        }.toByteArray()
        java.nio.file.Files.write(root.resolve("local-athlete.bin"),
            identityBytes+java.security.MessageDigest.getInstance("SHA-256").digest(identityBytes))
        val before=NativeKotlinStagedCoreStoreAudit.inspect(root,expected)
        assertTrue(before.toString(),before is NativeKotlinStagedCoreAuditResult.Verified)
        val verified=before as NativeKotlinStagedCoreAuditResult.Verified
        assertEquals(5,verified.tierTransitionReceipts)
        assertEquals(6,verified.finishedWorkouts)
        val password="tier-specialist-recovery-password".toCharArray()
        fun capture():ByteArray {
            val archive=NativeKotlinEncryptedTrainingSnapshot.capture(root,fence,expected,
                {active},{reader.read(expected.ownerKey,expected.athleteId) is
                    NativeKotlinMixedHistoryReadResult.Review},password)
            assertTrue(archive.toString(),archive is NativeKotlinTrainingSnapshotResult.Ready)
            return (archive as NativeKotlinTrainingSnapshotResult.Ready).encrypted
        }
        val validArchive=capture()
        java.nio.file.Files.delete(receipt)
        assertNull(coldStore().inventoryForRecovery("owner-1"))
        assertEquals(NativeKotlinStagedCoreAuditResult.Rejected(
            NativeKotlinStagedCoreRefusal.TIER_TRANSITION_INVENTORY_UNVERIFIED),
            NativeKotlinStagedCoreStoreAudit.inspect(root,expected))
        // Encryption authenticates the captured bytes; it cannot replace
        // the independent numerical and linked receipt audit on restore.
        val incompleteArchive=capture()
        java.nio.file.Files.write(receipt,original.copyOf(original.size-1))
        assertNull(coldStore().inventoryForRecovery("owner-1"))
        java.nio.file.Files.write(receipt,original)
        val renamed=receipt.resolveSibling(receipt.fileName.toString()+".renamed")
        java.nio.file.Files.move(receipt,renamed)
        assertNull(coldStore().inventoryForRecovery("owner-1"))
        java.nio.file.Files.move(renamed,receipt)
        assertEquals(5,coldStore().inventoryForRecovery("owner-1"))
        val all=reader.readTieredLedger("owner-1","athlete-1",initialProposal.request.scope,
            initial.slotId,initial.tier,7)
        assertTrue(all.toString(),all is NativeOwnerTieredLedgerResult.Review)
        val exposures=(all as NativeOwnerTieredLedgerResult.Review).exposures
        assertEquals(listOf(initial.nextWorkoutId,"tier-recovery","tier-stage-one","tier-stage-two",
            "tier-reset","tier-after-reset"),exposures.map{it.workoutId})
        assertEquals(n("25"),exposures[1].recoveryReference)
        assertFalse(exposures[1].comparableTraining)
        assertEquals(SetOutcome.FAILED_ATTEMPT,exposures[3].observations.single().outcome)
        assertEquals(0,exposures[3].observations.single().actualReps)
        assertEquals(2L,(drafts.read("owner-1","cycle-1") as NativeCustomDraftReadResult.Found).draft.revision)
        val destination=temp.newFolder("tier-clean-install").toPath().resolve("kotlin-coach-v1")
        fun restore(archive:ByteArray)=NativeKotlinCleanStoreRestore.restoreToAbsentStore(
            destination,archive,password,expected,{stage,who->
                NativeKotlinStagedCoreStoreAudit.inspect(stage,who) is NativeKotlinStagedCoreAuditResult.Verified
            })
        assertEquals(NativeKotlinCleanRestoreResult.Rejected(
            NativeKotlinCleanRestoreRefusal.INVALID_RESTORED_STORE),restore(incompleteArchive))
        assertFalse(java.nio.file.Files.exists(destination))
        val restored=restore(validArchive)
        assertTrue(restored.toString(),restored is NativeKotlinCleanRestoreResult.Committed)
        assertEquals(before,NativeKotlinStagedCoreStoreAudit.inspect(destination,expected))
        // Exact receipt bytes survive encryption, staging, and atomic
        // publication. The cold audit independently replays all six weeks.
        assertArrayEquals(original,java.nio.file.Files.readAllBytes(destination.resolve(receipt.fileName)))
        assertEquals(NativeKotlinCleanRestoreResult.Rejected(
            NativeKotlinCleanRestoreRefusal.EXISTING_TRAINING_DATA),restore(validArchive))
        assertEquals(before,NativeKotlinStagedCoreStoreAudit.inspect(destination,expected))
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
