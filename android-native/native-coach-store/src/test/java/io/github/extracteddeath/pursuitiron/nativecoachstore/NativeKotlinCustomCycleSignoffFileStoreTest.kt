package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.math.BigDecimal
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class NativeKotlinCustomCycleSignoffFileStoreTest {
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
    private fun futureStore()=NativeKotlinFutureBlockContextFileStore(
        temp.root.toPath(),fence,identity,drafts,sources)
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
    private fun set()=CustomAuthoredSet(DraftSetType.WORKING,
        DraftSetLoad.ExplicitBodyweight,RepBand(10,15),2,55,75)
    private fun cycle(rev:Long=1)=CustomTrainingCycle(
        "cycle-1",rev,"athlete-1",AthleteTrainingGoal.HYPERTROPHY,
        "My custom cycle",listOf(CustomTrainingBlock(
            "base","Foundation",TrainingPhase.HYPERTROPHY,
            CustomBlockPurpose.TRAINING,3,
            CustomSplitIdentity.Freeform("Custom core"),listOf(0,2,4).map {
                day->CustomTrainingDay(day,"Day $day",
                    ComposerSessionPurpose.HYPERTROPHY,
                    listOf(CustomExerciseSlot("slot-$day","bodyweight_crunch",
                        ComposerRole.ACCESSORY,listOf(set()))))
            }
        )))
    private fun bounds()=TrainingMuscle.entries.map{muscle ->
        DeclaredMuscleDoseBounds(muscle,
            if(muscle==TrainingMuscle.ABS)n("3") else BigDecimal.ZERO,
            if(muscle==TrainingMuscle.ABS)n("4") else BigDecimal.ZERO,
            if(muscle==TrainingMuscle.ABS)n("5") else BigDecimal.ZERO,
            if(muscle==TrainingMuscle.ABS)3 else 0)
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

    @Test fun exactReviewedProgramConsentSurvivesColdRestorationNotWorkoutIssuance() {
        initialize()
        assertEquals(NativeCustomSignoffRead.Missing,
            store().read("owner-1","cycle-1"))
        val receipt=approved()
        assertEquals(1L,receipt.recordRevision)
        assertTrue(receipt.fingerprintSha256.matches(Regex("[a-f0-9]{64}")))
        assertEquals(NativeCustomSignoffRead.Found(receipt,false),
            store().read("owner-1","cycle-1"))
        assertEquals(NativeCustomSignoffRead.Found(receipt,true),
            store().read("owner-1","cycle-1",inputs()))
        assertFalse(receipt.mayIssueWorkout)
        assertFalse(receipt.mayAlterHistory)
    }

    @Test fun unreviewedPolicyAndMissingConsentNeverIssueOrWriteRecord() {
        initialize()
        assertEquals(NativeCustomSignoffWrite.Rejected(
            NativeCustomSignoffRefusal.CONFIRMATION_REQUIRED),
            store().confirm("owner-1","cycle-1",1,1,0,inputs(),false))
        assertEquals(NativeCustomSignoffWrite.Rejected(
            NativeCustomSignoffRefusal.COACH_REVIEW_UNRESOLVED),
            store().confirm("owner-1","cycle-1",1,1,0,
                CustomCoachAuditInputs(),true))
        assertEquals(NativeCustomSignoffRead.Missing,
            store().read("owner-1","cycle-1"))
    }

    @Test fun programEditsAndNewGymRevisionsInvalidateConsentWithoutDeletingHistory() {
        initialize()
        val receipt=approved()
        assertEquals(NativeCustomDraftWriteResult.Committed(2),
            drafts.save("owner-1","cycle-1",1,cycle(2).copy(title="Edited")))
        assertEquals(NativeCustomSignoffRead.Found(receipt,false),
            store().read("owner-1","cycle-1",inputs()))
        assertEquals(NativeCustomSignoffWrite.Rejected(
            NativeCustomSignoffRefusal.STALE_PROGRAM),
            store().confirm("owner-1","cycle-1",1,1,1,inputs(),true))
        assertEquals(NativeAuthoringSourceWrite.Committed(2),
            sources.publish("owner-1",1,source(2)))
        assertEquals(NativeCustomSignoffWrite.Rejected(
            NativeCustomSignoffRefusal.STALE_SOURCE),
            store().confirm("owner-1","cycle-1",2,1,1,inputs(),true))
        assertEquals(NativeCustomDraftReadResult.Found(
            cycle(2).copy(title="Edited")),drafts.read("owner-1","cycle-1"))
    }

    @Test fun actualPolicyContentsAndNonSetTimeAreInFingerprintNotJustVersionName() {
        initialize()
        val receipt=approved()
        val changed=inputs(policy().copy(
            rationale="Changed review but intentionally reused policy version"))
        assertEquals(NativeCustomSignoffRead.Found(receipt,false),
            store().read("owner-1","cycle-1",changed))
        val differentTime=inputs().copy(nonSetSecondsByWeekday=
            mapOf(0 to 201,2 to 200,4 to 200))
        assertEquals(NativeCustomSignoffRead.Found(receipt,false),
            store().read("owner-1","cycle-1",differentTime))
        assertEquals(NativeCustomSignoffRead.Found(receipt,true),
            store().read("owner-1","cycle-1",inputs()))
    }

    @Test fun identitySwitchAndRevocationFailClosed() {
        initialize()
        val receipt=approved()
        active=NativeKotlinOwnerIdentity("other-owner","athlete-1")
        assertEquals(NativeCustomSignoffRead.Rejected(
            NativeCustomSignoffRefusal.NOT_AUTHORIZED),
            store().read("owner-1","cycle-1",inputs()))
        active=NativeKotlinOwnerIdentity("owner-1","athlete-1")
        val revoked=store().revoke("owner-1","cycle-1",1)
        assertTrue(revoked is NativeCustomSignoffWrite.Committed)
        assertEquals(NativeCustomSignoffRead.Revoked(2),
            store().read("owner-1","cycle-1",inputs()))
        assertEquals(NativeCustomSignoffWrite.Rejected(
            NativeCustomSignoffRefusal.STALE_CONSENT),
            store().confirm("owner-1","cycle-1",1,1,0,inputs(),true))
        assertEquals(NativeCustomSignoffWrite.Rejected(
            NativeCustomSignoffRefusal.STALE_CONSENT),
            store().confirm("owner-1","cycle-1",1,1,1,inputs(),true))
        assertEquals(NativeCustomSignoffWrite.Committed(
            receipt.copy(recordRevision=3)),
            store().confirm("owner-1","cycle-1",1,1,2,inputs(),true))
    }

    @Test fun checksumFailureIsNotTreatedAsNewOrApprovedConsent() {
        initialize()
        approved()
        val path=Files.list(temp.root.toPath()).use{stream->
            stream.filter{
                it.fileName.toString().startsWith("custom-consent-")
            }.findFirst().orElseThrow()
        }
        val bytes=Files.readAllBytes(path)
        bytes[bytes.lastIndex]=(bytes.last().toInt() xor 1).toByte()
        Files.write(path,bytes)
        assertEquals(NativeCustomSignoffRead.Rejected(
            NativeCustomSignoffRefusal.CORRUPT_RECORD),
            store().read("owner-1","cycle-1",inputs()))
        assertEquals(NativeCustomSignoffWrite.Rejected(
            NativeCustomSignoffRefusal.CORRUPT_RECORD),
            store().confirm("owner-1","cycle-1",1,1,1,inputs(),true))
    }

    private fun adaptive()=NativeKotlinAdaptiveCycleDraftAcceptance(
        fence,identity,drafts,sources,permissionStore,store(),futureStore())
    private fun adjusted():CustomTrainingCycle {
        val original=cycle(2)
        return original.copy(blocks=original.blocks.map{block->
            block.copy(label="Reviewed foundation",days=block.days.map{day->
                if(day.weekday!=0)day else day.copy(
                    slots=day.slots.map{slot->
                        slot.copy(sets=slot.sets.map{set->
                            set.copy(reps=RepBand(11,15))
                        })
                    })
            })
        })
    }
    private fun option(
        proposed:CustomTrainingCycle=adjusted(),
        permissions:CustomCycleCoachPermissions=
            CustomCycleCoachPermissions(adaptCues=true,adaptReps=true)
    )=NativeAdaptiveCycleDraftCandidate(
        "owner-1","cycle-1","base","coach_option_1",
        1,1,proposed,inputs(),permissions,1)

    @Test fun athleteReviewedAdaptiveOptionPersistsOnlyDraftAndStalesOldConsent() {
        initialize()
        val signed=approved()
        val proposal=option()
        val shown=adaptive().preview(proposal)
        assertTrue(shown.toString(),shown is NativeAdaptiveCycleDraftPreview.Ready)
        val review=shown as NativeAdaptiveCycleDraftPreview.Ready
        assertEquals(setOf(CustomCycleChangeKind.CUES,CustomCycleChangeKind.REPS),
            review.changes.map{it.kind}.toSet())
        assertFalse(review.mayIssueWorkout)
        assertFalse(review.mayApplyAutomatically)
        val saved=adaptive().accept(proposal,review.reviewedFingerprint,true)
        assertEquals(NativeAdaptiveCycleDraftDecision.Saved(2,"coach_option_1"),saved)
        assertFalse((saved as NativeAdaptiveCycleDraftDecision.Saved).mayIssueWorkout)
        assertFalse(saved.isSignedOff)
        assertEquals(NativeCustomDraftReadResult.Found(adjusted()),
            drafts.read("owner-1","cycle-1"))
        assertEquals(NativeCustomSignoffRead.Found(signed,false),
            store().read("owner-1","cycle-1",inputs()))
        assertEquals(NativeAdaptiveCycleDraftDecision.Rejected(
            NativeAdaptiveCycleDraftRefusal.DRAFT_CHANGED),
            adaptive().accept(proposal,review.reviewedFingerprint,true))
        // New cycle revision needs its OWN explicit signoff; a draft is
        // never treated as an approved program or workout grant.
        val renewed=store().confirm("owner-1","cycle-1",2,1,1,inputs(),true)
        assertTrue(renewed.toString(),renewed is NativeCustomSignoffWrite.Committed)
        assertEquals(2L,(renewed as NativeCustomSignoffWrite.Committed)
            .receipt.cycleRevision)
    }

    @Test fun adaptiveOptionNeverBypassesPermissionsOrDisplayedFingerprint() {
        initialize()
        approved()
        val blocked=adaptive().preview(option(
            permissions=CustomCycleCoachPermissions()))
        assertEquals(NativeAdaptiveCycleDraftPreview.Rejected(
            NativeAdaptiveCycleDraftRefusal.PERMISSION_REQUIRED),blocked)
        val proposal=option()
        val review=adaptive().preview(proposal)
            as NativeAdaptiveCycleDraftPreview.Ready
        assertEquals(NativeAdaptiveCycleDraftDecision.Rejected(
            NativeAdaptiveCycleDraftRefusal.CONFIRMATION_REQUIRED),
            adaptive().accept(proposal,review.reviewedFingerprint,false))
        assertEquals(NativeAdaptiveCycleDraftDecision.Rejected(
            NativeAdaptiveCycleDraftRefusal.REVIEW_CHANGED),
            adaptive().accept(proposal,"0".repeat(64),true))
        assertEquals(NativeCustomDraftReadResult.Found(cycle()),
            drafts.read("owner-1","cycle-1"))
        val alternate=option(adjusted().copy(blocks=adjusted().blocks.map {
            it.copy(label="Different but still permitted")
        }))
        assertEquals(NativeAdaptiveCycleDraftDecision.Rejected(
            NativeAdaptiveCycleDraftRefusal.REVIEW_CHANGED),
            adaptive().accept(alternate,review.reviewedFingerprint,true))
        assertEquals(NativeCustomDraftReadResult.Found(cycle()),
            drafts.read("owner-1","cycle-1"))
    }

    @Test fun adaptiveOptionFailsClosedOnSourceEditsForeignAccountAndFutureSplit() {
        initialize()
        approved()
        val proposal=option()
        val ready=adaptive().preview(proposal)
            as NativeAdaptiveCycleDraftPreview.Ready
        val prospective=option(adjusted().copy(blocks=adjusted().blocks.map{
            it.copy(split=CustomSplitIdentity.Named(ComposerSplit.UPPER_LOWER))
        }))
        assertEquals(NativeAdaptiveCycleDraftPreview.Rejected(
            NativeAdaptiveCycleDraftRefusal.TARGET_NOT_FUTURE_BLOCK),
            adaptive().preview(prospective))
        val unrelated=option(adjusted().copy(title="Unreviewed other change"))
        assertEquals(NativeAdaptiveCycleDraftPreview.Rejected(
            NativeAdaptiveCycleDraftRefusal.UNRELATED_BLOCK_CHANGED),
            adaptive().preview(unrelated))
        active=NativeKotlinOwnerIdentity("wrong-owner","athlete-1")
        assertEquals(NativeAdaptiveCycleDraftDecision.Rejected(
            NativeAdaptiveCycleDraftRefusal.NOT_AUTHORIZED),
            adaptive().accept(proposal,ready.reviewedFingerprint,true))
        active=NativeKotlinOwnerIdentity("owner-1","athlete-1")
        assertEquals(NativeAuthoringSourceWrite.Committed(2),
            sources.publish("owner-1",1,source(2)))
        assertEquals(NativeAdaptiveCycleDraftDecision.Rejected(
            NativeAdaptiveCycleDraftRefusal.SOURCE_CHANGED),
            adaptive().accept(proposal,ready.reviewedFingerprint,true))
        assertEquals(NativeCustomDraftReadResult.Found(cycle()),
            drafts.read("owner-1","cycle-1"))
    }


    @Test fun continuityPolicyChangeInvalidatesSignedCycleEvenIfReviewStillReady() {
        initialize()
        val prior=inputs().copy(continuityPolicy=
            CustomCoachVolumeContinuityPolicy(n("0.20"),n("0.25"),
                n("0.15"),n("0.10")))
        val signed=store().confirm("owner-1","cycle-1",1,1,0,prior,true)
        assertTrue(signed.toString(),signed is NativeCustomSignoffWrite.Committed)
        val receipt=(signed as NativeCustomSignoffWrite.Committed).receipt
        assertEquals(NativeCustomSignoffRead.Found(receipt,true),
            store().read("owner-1","cycle-1",prior))
        val cases=listOf(
            prior.copy(continuityPolicy=null),
            prior.copy(continuityPolicy=prior.continuityPolicy!!.copy(
                maximumTotalWorkingSetDropFraction=n("0.19"))),
            prior.copy(continuityPolicy=prior.continuityPolicy!!.copy(
                maximumDirectMuscleDropFraction=n("0.24"))),
            prior.copy(continuityPolicy=prior.continuityPolicy!!.copy(
                maximumTotalWorkingSetRiseFraction=n("0.14"))),
            prior.copy(continuityPolicy=prior.continuityPolicy!!.copy(
                maximumDirectMuscleRiseFraction=n("0.09")))
        )
        cases.forEach {changed ->
            assertEquals(NativeCustomSignoffRead.Found(receipt,false),
                store().read("owner-1","cycle-1",changed))
            assertNotEquals(
                NativeCustomCycleSignoffFingerprint.calculate(
                    cycle(),source(),prior),
                NativeCustomCycleSignoffFingerprint.calculate(
                    cycle(),source(),changed))
        }
        // Equivalent decimal representations must never demand new consent.
        val equivalent=prior.copy(continuityPolicy=
            prior.continuityPolicy!!.copy(
                maximumTotalWorkingSetDropFraction=n("0.200"),
                maximumDirectMuscleRiseFraction=n("0.100")))
        assertEquals(NativeCustomSignoffRead.Found(receipt,true),
            store().read("owner-1","cycle-1",equivalent))
    }


    @Test fun permissionConsentCannotBeForgedByCallerAfterGrantChanges() {
        initialize()
        approved()
        val candidate=option()
        val shown=adaptive().preview(candidate)
            as NativeAdaptiveCycleDraftPreview.Ready
        assertEquals(NativeAdaptivePermissionWrite.Rejected(
            NativeAdaptivePermissionRefusal.NOT_CONFIRMED),
            permissionStore.confirm("owner-1","cycle-1",1,
                CustomCycleChangeKind.entries.toSet(),false))
        assertEquals(NativeAdaptivePermissionWrite.Committed(2),
            permissionStore.confirm("owner-1","cycle-1",1,
                setOf(CustomCycleChangeKind.CUES),true))
        assertEquals(NativeAdaptiveCycleDraftPreview.Rejected(
            NativeAdaptiveCycleDraftRefusal.PERMISSIONS_CHANGED),
            adaptive().preview(candidate))
        val noRep=option(permissions=CustomCycleCoachPermissions(
            adaptCues=true)).copy(expectedPermissionRevision=2)
        assertEquals(NativeAdaptiveCycleDraftPreview.Rejected(
            NativeAdaptiveCycleDraftRefusal.PERMISSION_REQUIRED),
            adaptive().preview(noRep))
        assertEquals(NativeAdaptiveCycleDraftDecision.Rejected(
            NativeAdaptiveCycleDraftRefusal.PERMISSIONS_CHANGED),
            adaptive().accept(candidate,shown.reviewedFingerprint,true))
        assertEquals(NativeAdaptivePermissionWrite.Committed(3),
            permissionStore.revoke("owner-1","cycle-1",2))
        assertEquals(NativeAdaptivePermissionRead.Revoked(3),
            permissionStore.read("owner-1","cycle-1"))
        assertEquals(NativeAdaptiveCycleDraftPreview.Rejected(
            NativeAdaptiveCycleDraftRefusal.PERMISSIONS_NOT_APPROVED),
            adaptive().preview(candidate.copy(expectedPermissionRevision=3)))
        assertEquals(NativeCustomDraftReadResult.Found(cycle()),
            drafts.read("owner-1","cycle-1"))
    }

    @Test fun permissionFileCorruptionAndForeignOwnerFailClosed() {
        initialize()
        val records=Files.list(temp.root.toPath()).use{it.filter {path ->
            path.fileName.toString().startsWith("adapt-permission-")
        }.toList()}
        assertEquals(1,records.size)
        val target=records.single()
        val original=Files.readAllBytes(target)
        val mangled=original.clone()
        mangled[mangled.lastIndex]=(mangled.last().toInt() xor 1).toByte()
        Files.write(target,mangled)
        assertEquals(NativeAdaptivePermissionRead.Rejected(
            NativeAdaptivePermissionRefusal.CORRUPT_RECORD),
            permissionStore.read("owner-1","cycle-1"))
        assertEquals(NativeAdaptiveCycleDraftPreview.Rejected(
            NativeAdaptiveCycleDraftRefusal.PERMISSIONS_NOT_APPROVED),
            adaptive().preview(option()))
        Files.write(target,original)
        active=NativeKotlinOwnerIdentity("foreign-owner","athlete-1")
        assertEquals(NativeAdaptivePermissionRead.Rejected(
            NativeAdaptivePermissionRefusal.NOT_AUTHORIZED),
            permissionStore.read("owner-1","cycle-1"))
        assertEquals(NativeAdaptiveCycleDraftDecision.Rejected(
            NativeAdaptiveCycleDraftRefusal.NOT_AUTHORIZED),
            adaptive().accept(option(),"0".repeat(64),true))
    }


    @Test fun adaptiveOptionRequiresSameSignedBaselineAndCompletePolicyInputs() {
        initialize()
        val proposal=option()
        assertEquals(NativeAdaptiveCycleDraftPreview.Rejected(
            NativeAdaptiveCycleDraftRefusal.CURRENT_CYCLE_NOT_SIGNED_OFF),
            adaptive().preview(proposal))
        val signed=approved()
        val shown=adaptive().preview(proposal)
        assertTrue(shown.toString(),shown is NativeAdaptiveCycleDraftPreview.Ready)
        val prior=shown as NativeAdaptiveCycleDraftPreview.Ready
        val changed=proposal.copy(coaching=inputs().copy(continuityPolicy=
            CustomCoachVolumeContinuityPolicy(n("0.2"),n("0.3"),
                n("0.15"),n("0.1"))))
        assertEquals(NativeAdaptiveCycleDraftPreview.Rejected(
            NativeAdaptiveCycleDraftRefusal.CURRENT_CYCLE_NOT_SIGNED_OFF),
            adaptive().preview(changed))
        assertEquals(NativeAdaptiveCycleDraftDecision.Rejected(
            NativeAdaptiveCycleDraftRefusal.CURRENT_CYCLE_NOT_SIGNED_OFF),
            adaptive().accept(changed,prior.reviewedFingerprint,true))
        assertEquals(NativeCustomSignoffWrite.Committed(
            signed.copy(recordRevision=2)),
            store().revoke("owner-1","cycle-1",1))
        assertEquals(NativeAdaptiveCycleDraftPreview.Rejected(
            NativeAdaptiveCycleDraftRefusal.CURRENT_CYCLE_NOT_SIGNED_OFF),
            adaptive().preview(proposal))
        assertEquals(NativeCustomDraftReadResult.Found(cycle()),
            drafts.read("owner-1","cycle-1"))
    }


    private fun futureSetup():AthleteSetupReview {
        val prior=checkNotNull(setup().reviewed)
        return CoachAthleteSetupDomain.review(AthleteSetupDraft(
            profileRevision=prior.profileRevision,
            athleteProfileId=prior.athleteProfileId,
            goal=prior.goal,experience=prior.experience,
            split=ComposerSplit.UPPER_LOWER,
            trainingWeekdays=setOf(0,1,2,4),
            timezoneId=prior.timezoneId,
            sessionTimeBucket=prior.sessionTimeBucket,
            hardSessionLimit=prior.hardSessionLimit,
            gym=GymProfileDraft(
                prior.gym.gymId,prior.gym.unit,
                prior.gym.confirmedStock,
                prior.gym.availableFacilities,
                prior.gym.availableAttachments),
            allowBodyweight=prior.allowBodyweight,
            allowSupersets=prior.allowSupersets,
            allowTricepsOnLower=prior.allowTricepsOnLower,
            maxBarbellExercisesPerDay=prior.maxBarbellExercisesPerDay,
            focusedMuscles=prior.focusedMuscles,
            reducedMuscles=prior.reducedMuscles,
            excludedExerciseIds=prior.excludedExerciseIds,
            lockedTrainingDays=prior.lockedTrainingDays,
            hardTrainingDays=prior.hardTrainingDays,
            longTrainingDays=prior.longTrainingDays,
            declaredRestDays=setOf(3,5,6)))
    }
    private fun futureContext()=ProspectiveBlockCoachingContext(
        futureSetup(),policy().copy(
            revision="future-r2",
            split=ComposerSplit.UPPER_LOWER,trainingDays=4,
            minimumPurposeDays=mapOf(ComposerSessionPurpose.HYPERTROPHY to 4)),
        mapOf(0 to 200,1 to 200,2 to 200,4 to 200))
    private fun futureInputs()=inputs().copy(continuityPolicy=
        CustomCoachVolumeContinuityPolicy(
            n("0.50"),n("0.50"),n("0.50"),n("0.50")))
    private fun futureBaselineCycle():CustomTrainingCycle {
        val authored=cycle(2)
        val original=authored.blocks.single()
        return authored.copy(blocks=listOf(original,
            original.copy(blockId="build",label="Build")))
    }
    private fun initializeFutureBaseline() {
        initialize()
        assertEquals(NativeCustomDraftWriteResult.Committed(2),
            drafts.save("owner-1","cycle-1",1,futureBaselineCycle()))
        val review=CustomCycleCoachAuditDomain.review(
            futureBaselineCycle(),source().setup,source().catalog,futureInputs())
        assertEquals(review.issues.toString(),
            CustomCoachAuditStatus.READY_FOR_ATHLETE_REVIEW,review.status)
    }
    private fun approveFutureBaseline():NativeKotlinCustomCycleSignoff {
        val result=store().confirm("owner-1","cycle-1",2,1,0,futureInputs(),true)
        assertTrue(result.toString(),result is NativeCustomSignoffWrite.Committed)
        return (result as NativeCustomSignoffWrite.Committed).receipt
    }
    private fun futureCycle():CustomTrainingCycle {
        val baseline=futureBaselineCycle()
        return baseline.copy(revision=3,
            blocks=baseline.blocks.map {block ->
                if(block.blockId!="build")block else
                    block.copy(split=CustomSplitIdentity.Named(
                        ComposerSplit.UPPER_LOWER),
                        days=(block.days+CustomTrainingDay(1,"Day 1",
                            ComposerSessionPurpose.HYPERTROPHY,
                            listOf(CustomExerciseSlot("slot-1",
                                "bodyweight_crunch",ComposerRole.ACCESSORY,
                                listOf(set())))))
                            .sortedBy {it.weekday})
            })
    }
    private fun futurePermissions()=CustomCycleCoachPermissions(
        adaptSplit=true,adaptSchedule=true,adaptDayCount=true,
        adaptExercises=true)
    private fun futureCandidate(
        context:ProspectiveBlockCoachingContext=futureContext()
    )=option(proposed=futureCycle(),permissions=futurePermissions())
        .copy(targetBlockId="build",expectedDraftRevision=2,
            expectedPermissionRevision=2,coaching=futureInputs(),
            prospective=context)


    @Test fun firstBlockCannotBeSavedAsFutureScheduleEvenWithFutureConsent() {
        initialize()
        approved()
        assertEquals(NativeAdaptivePermissionWrite.Committed(2),
            permissionStore.confirm("owner-1","cycle-1",1,
                setOf(CustomCycleChangeKind.SPLIT,
                    CustomCycleChangeKind.SCHEDULE,
                    CustomCycleChangeKind.DAY_COUNT,
                    CustomCycleChangeKind.EXERCISES),true))
        // Real first block has no predecessor. Future policy and a full
        // calendar cannot make it a prospective-only change.
        val proposed=cycle(2).copy(blocks=cycle(2).blocks.map{block->
            block.copy(split=CustomSplitIdentity.Named(ComposerSplit.UPPER_LOWER),
                days=(block.days+CustomTrainingDay(1,"Day 1",
                    ComposerSessionPurpose.HYPERTROPHY,
                    listOf(CustomExerciseSlot("slot-1",
                        "bodyweight_crunch",ComposerRole.ACCESSORY,
                        listOf(set()))))).sortedBy{it.weekday})
        })
        val c=option(proposed=proposed,permissions=futurePermissions())
            .copy(expectedPermissionRevision=2,prospective=futureContext())
        assertEquals(NativeAdaptiveCycleDraftPreview.Rejected(
            NativeAdaptiveCycleDraftRefusal.TARGET_NOT_FUTURE_BLOCK),
            adaptive().preview(c))
        assertEquals(NativeAdaptiveCycleDraftDecision.Rejected(
            NativeAdaptiveCycleDraftRefusal.TARGET_NOT_FUTURE_BLOCK),
            adaptive().accept(c,"0".repeat(64),true))
        assertEquals(NativeCustomDraftReadResult.Found(cycle()),
            drafts.read("owner-1","cycle-1"))
        assertEquals(NativeFutureBlockContextRead.Missing,
            futureStore().read("owner-1","cycle-1","base"))
    }

    @Test fun futureScheduleRequiresFullProspectiveReviewAndExplicitDraftAcceptance() {
        initializeFutureBaseline()
        val signed=approveFutureBaseline()
        assertEquals(NativeAdaptivePermissionWrite.Committed(2),
            permissionStore.confirm("owner-1","cycle-1",1,
                setOf(CustomCycleChangeKind.SPLIT,
                    CustomCycleChangeKind.SCHEDULE,
                    CustomCycleChangeKind.DAY_COUNT,
                    CustomCycleChangeKind.EXERCISES),true))
        val prospective=futureCandidate()
        assertEquals(NativeAdaptiveCycleDraftPreview.Rejected(
            NativeAdaptiveCycleDraftRefusal.FUTURE_PROFILE_REQUIRES_APPROVAL),
            adaptive().preview(prospective.copy(prospective=null)))
        val first=adaptive().preview(prospective)
        assertTrue(first.toString(),first is NativeAdaptiveCycleDraftPreview.Ready)
        val reviewed=first as NativeAdaptiveCycleDraftPreview.Ready
        assertEquals(setOf(CustomCycleChangeKind.SPLIT,
            CustomCycleChangeKind.SCHEDULE,
            CustomCycleChangeKind.DAY_COUNT,
            CustomCycleChangeKind.EXERCISES),
            reviewed.changes.map{it.kind}.toSet())
        val modified=futureCandidate(futureContext().copy(
            policy=futureContext().policy.copy(
                rationale="Changed future coaching evidence with same version")))
        assertEquals(NativeAdaptiveCycleDraftDecision.Rejected(
            NativeAdaptiveCycleDraftRefusal.REVIEW_CHANGED),
            adaptive().accept(modified,reviewed.reviewedFingerprint,true))
        assertEquals(NativeAdaptiveCycleDraftDecision.Rejected(
            NativeAdaptiveCycleDraftRefusal.CONFIRMATION_REQUIRED),
            adaptive().accept(prospective,reviewed.reviewedFingerprint,false))
        assertEquals(NativeAdaptiveCycleDraftDecision.Saved(3,"coach_option_1"),
            adaptive().accept(prospective,reviewed.reviewedFingerprint,true))
        assertEquals(NativeCustomDraftReadResult.Found(futureCycle()),
            drafts.read("owner-1","cycle-1"))
        val restored=futureStore().read("owner-1","cycle-1","build")
        assertTrue(restored.toString(),
            restored is NativeFutureBlockContextRead.Found)
        val receipt=(restored as NativeFutureBlockContextRead.Found).receipt
        assertEquals(futureContext(),receipt.future)
        assertEquals(reviewed.reviewedFingerprint,receipt.reviewedFingerprint)
        assertEquals(3L,receipt.draftRevision)
        assertEquals(1L,receipt.sourceRevision)
        assertFalse(receipt.mayIssueWorkout)
        assertFalse(receipt.mayActivateProfile)
        assertEquals(NativeCustomSignoffRead.Found(signed,false),
            store().read("owner-1","cycle-1",futureInputs()))
        // This is a DRAFT: the actual reviewed gym/profile remains the
        // original three-day split and issuance is still unauthorized.
        assertEquals(ComposerSplit.FULL_BODY,
            (sources.read("owner-1") as NativeAuthoringSourceRead.Found)
                .source.setup.reviewed!!.split)
        assertEquals(NativeCustomSignoffWrite.Rejected(
            NativeCustomSignoffRefusal.COACH_REVIEW_UNRESOLVED),
            store().confirm("owner-1","cycle-1",3,1,1,futureInputs(),true))
    }

    @Test fun futureScheduleCannotLaunderNewGymOrUnapprovedFrequency() {
        initializeFutureBaseline()
        approveFutureBaseline()
        assertEquals(NativeAdaptivePermissionWrite.Committed(2),
            permissionStore.confirm("owner-1","cycle-1",1,
                setOf(CustomCycleChangeKind.SPLIT,
                    CustomCycleChangeKind.SCHEDULE,
                    CustomCycleChangeKind.DAY_COUNT,
                    CustomCycleChangeKind.EXERCISES),true))
        val normal=futureCandidate()
        assertTrue(adaptive().preview(normal).toString(),
            adaptive().preview(normal) is NativeAdaptiveCycleDraftPreview.Ready)
        val other=checkNotNull(futureContext().setup.reviewed)
        val invalid=futureCandidate(futureContext().copy(
            setup=futureContext().setup.copy(reviewed=other.copy(
                experience=AthleteExperience.BEGINNER))))
        assertEquals(NativeAdaptiveCycleDraftPreview.Rejected(
            NativeAdaptiveCycleDraftRefusal.COACH_REVIEW_UNRESOLVED),
            adaptive().preview(invalid))
        assertEquals(NativeCustomDraftReadResult.Found(futureBaselineCycle()),
            drafts.read("owner-1","cycle-1"))
    }


    @Test fun interruptedProspectiveStageIsNotAValidAcceptedFutureContext() {
        initializeFutureBaseline()
        approveFutureBaseline()
        assertTrue(futureStore().stage("owner-1","cycle-1","build",
            futureCycle(),source(),"a".repeat(64),futureContext()))
        assertEquals(NativeFutureBlockContextRead.Rejected(
            NativeFutureBlockContextRefusal.STALE_DRAFT_OR_SOURCE),
            futureStore().read("owner-1","cycle-1","build"))
        assertEquals(NativeCustomDraftReadResult.Found(futureBaselineCycle()),
            drafts.read("owner-1","cycle-1"))
    }

    @Test fun futureContextCorruptionAndChangingGymOrDraftFailsClosed() {
        initializeFutureBaseline()
        approveFutureBaseline()
        assertEquals(NativeAdaptivePermissionWrite.Committed(2),
            permissionStore.confirm("owner-1","cycle-1",1,
                setOf(CustomCycleChangeKind.SPLIT,
                    CustomCycleChangeKind.SCHEDULE,
                    CustomCycleChangeKind.DAY_COUNT,
                    CustomCycleChangeKind.EXERCISES),true))
        val offer=futureCandidate()
        val ready=adaptive().preview(offer)
            as NativeAdaptiveCycleDraftPreview.Ready
        assertEquals(NativeAdaptiveCycleDraftDecision.Saved(3,"coach_option_1"),
            adaptive().accept(offer,ready.reviewedFingerprint,true))
        assertTrue(futureStore().read("owner-1","cycle-1","build") is
            NativeFutureBlockContextRead.Found)
        val record=Files.list(temp.root.toPath()).use {files->
            files.filter{it.fileName.toString().startsWith("future-block-")}
                .findFirst().orElseThrow()
        }
        val original=Files.readAllBytes(record)
        val corrupt=original.clone()
        corrupt[corrupt.lastIndex]=(corrupt.last().toInt() xor 1).toByte()
        Files.write(record,corrupt)
        assertEquals(NativeFutureBlockContextRead.Rejected(
            NativeFutureBlockContextRefusal.CORRUPT_RECORD),
            futureStore().read("owner-1","cycle-1","build"))
        Files.write(record,original)
        assertEquals(NativeAuthoringSourceWrite.Committed(2),
            sources.publish("owner-1",1,source(2)))
        assertEquals(NativeFutureBlockContextRead.Rejected(
            NativeFutureBlockContextRefusal.STALE_DRAFT_OR_SOURCE),
            futureStore().read("owner-1","cycle-1","build"))
    }


    /*
     * Checkpoint-store tests supply a deterministic boundary read ONLY to
     * test the persistence/CAS contract; the production host always injects
     * the real fully verified custom + Composer + history preflight.
     */
    private fun readyFutureForSecondConsent() {
        initializeFutureBaseline();approveFutureBaseline()
        assertEquals(NativeAdaptivePermissionWrite.Committed(2),
            permissionStore.confirm("owner-1","cycle-1",1,
                setOf(CustomCycleChangeKind.SPLIT,
                    CustomCycleChangeKind.SCHEDULE,
                    CustomCycleChangeKind.DAY_COUNT,
                    CustomCycleChangeKind.EXERCISES),true))
        val candidate=futureCandidate()
        val shown=adaptive().preview(candidate)
            as NativeAdaptiveCycleDraftPreview.Ready
        assertEquals(NativeAdaptiveCycleDraftDecision.Saved(3,"coach_option_1"),
            adaptive().accept(candidate,shown.reviewedFingerprint,true))
    }
    private fun approvalCheckpoint(
        boundary:()->NativeFutureActivationReview =
            {NativeFutureActivationReview.BoundaryReviewed(
                "cycle-1","build",4,3,9,7)}
    )=NativeKotlinFutureActivationApprovalFileStore(
        temp.root.toPath(),fence,identity,
        {_,_,_->boundary()},futureStore(),drafts,sources,permissionStore)

    @Test fun futureActivationRejectsCrossCycleOrContradictoryBoundaryEvidence() {
        readyFutureForSecondConsent()
        val valid=NativeFutureActivationReview.BoundaryReviewed(
            "cycle-1","build",4,3,9,7)
        val corrupt=listOf(
            valid.copy(cycleId="another-cycle"),
            valid.copy(blockId="wrong-block"),
            valid.copy(firstWeek=5),
            valid.copy(precedingWeeks=2),
            valid.copy(verifiedPriorSessions=2),
            valid.copy(historyManifestRevision=0),
            valid.copy(firstWeek=1,precedingWeeks=0)
        )
        for((index,boundary) in corrupt.withIndex()){
            val store=approvalCheckpoint{boundary}
            assertEquals("Corrupt evidence "+index,
                NativeFutureApprovalPreview.Rejected(
                    NativeFutureApprovalRefusal.NOT_READY),
                store.preview("owner-1","cycle-1","build"))
            assertEquals("Corrupt evidence cannot be accepted "+index,
                NativeFutureApprovalWrite.Rejected(
                    NativeFutureApprovalRefusal.NOT_READY),
                store.confirm("owner-1","cycle-1","build",0,
                    "a".repeat(64),true))
            assertEquals(NativeFutureApprovalRead.Missing,
                store.read("owner-1","cycle-1","build"))
        }
        assertTrue(approvalCheckpoint().preview(
            "owner-1","cycle-1","build") is NativeFutureApprovalPreview.Ready)
    }

    @Test fun secondConsentIsDurableExactAndNeverChangesCurrentGymOrProgram() {
        readyFutureForSecondConsent()
        val store=approvalCheckpoint()
        assertEquals(NativeFutureApprovalRead.Missing,
            store.read("owner-1","cycle-1","build"))
        val offer=store.preview("owner-1","cycle-1","build")
        assertTrue(offer.toString(),offer is NativeFutureApprovalPreview.Ready)
        val ready=offer as NativeFutureApprovalPreview.Ready
        assertEquals(0L,ready.expectedRecordRevision)
        // This exact next source is inspectable before the athlete signs,
        // and must NOT replace the still-active, three-day gym profile.
        val proposed=ready.sourcePlan
        assertEquals(2L,proposed.proposedSource.revision)
        assertEquals(setOf(0,1,2,4),
            proposed.proposedSource.setup.reviewed?.trainingWeekdays)
        assertEquals(ComposerSplit.UPPER_LOWER,
            proposed.proposedSource.setup.reviewed?.split)
        assertEquals("build",proposed.targetBlockId)
        assertEquals(4,proposed.firstWeek)
        assertEquals(7L,proposed.historyManifestRevision)
        assertEquals(futureContext().policy,proposed.targetPolicy)
        assertEquals(futureContext().nonSetSecondsByWeekday,
            proposed.nonSetSecondsByWeekday)
        assertFalse(proposed.mayActivate)
        assertFalse(proposed.mayIssueWorkout)
        assertEquals(setOf(0,2,4),
            (sources.read("owner-1") as NativeAuthoringSourceRead.Found)
                .source.setup.reviewed!!.trainingWeekdays)
        assertEquals(NativeFutureApprovalWrite.Rejected(
            NativeFutureApprovalRefusal.CONFIRMATION_REQUIRED),
            store.confirm("owner-1","cycle-1","build",0,ready.fingerprint,false))
        assertEquals(NativeFutureApprovalWrite.Rejected(
            NativeFutureApprovalRefusal.REVIEW_CHANGED),
            store.confirm("owner-1","cycle-1","build",0,"a".repeat(64),true))
        val result=store.confirm("owner-1","cycle-1","build",0,
            ready.fingerprint,true)
        assertTrue(result.toString(),result is NativeFutureApprovalWrite.Committed)
        val receipt=(result as NativeFutureApprovalWrite.Committed).approval
        assertEquals(1L,receipt.revision)
        assertFalse(receipt.mayActivateProfile)
        assertFalse(receipt.mayIssueWorkout)
        assertFalse(receipt.mayChangeHistory)
        assertEquals(NativeFutureApprovalRead.Found(receipt,true),
            approvalCheckpoint().read("owner-1","cycle-1","build"))
        assertEquals(NativeFutureApprovalWrite.Rejected(
            NativeFutureApprovalRefusal.REVISION_CONFLICT),
            approvalCheckpoint().confirm("owner-1","cycle-1","build",0,
                ready.fingerprint,true))
        assertEquals(NativeAuthoringSourceRead.Found(source()),
            sources.read("owner-1"))
        assertEquals(NativeCustomDraftReadResult.Found(futureCycle()),
            drafts.read("owner-1","cycle-1"))
    }

    @Test fun secondConsentStalesAfterHistoryOrPermissionsAndRequiresFreshReview() {
        readyFutureForSecondConsent()
        var historyRevision=7L
        val store=approvalCheckpoint{
            NativeFutureActivationReview.BoundaryReviewed(
                "cycle-1","build",4,3,9,historyRevision)
        }
        val ready=store.preview("owner-1","cycle-1","build")
            as NativeFutureApprovalPreview.Ready
        val approved=store.confirm("owner-1","cycle-1","build",0,
            ready.fingerprint,true) as NativeFutureApprovalWrite.Committed
        historyRevision=8L
        assertEquals(NativeFutureApprovalRead.Found(approved.approval,false),
            store.read("owner-1","cycle-1","build"))
        assertEquals(NativeFutureApprovalWrite.Rejected(
            NativeFutureApprovalRefusal.REVIEW_CHANGED),
            store.confirm("owner-1","cycle-1","build",1,
                ready.fingerprint,true))
        val changed=store.preview("owner-1","cycle-1","build")
            as NativeFutureApprovalPreview.Ready
        assertTrue(changed.fingerprint!=ready.fingerprint)
        assertTrue(store.confirm("owner-1","cycle-1","build",1,
            changed.fingerprint,true) is NativeFutureApprovalWrite.Committed)
        assertEquals(NativeAdaptivePermissionWrite.Committed(3),
            permissionStore.confirm("owner-1","cycle-1",2,
                setOf(CustomCycleChangeKind.CUES),true))
        assertTrue(store.read("owner-1","cycle-1","build") is
            NativeFutureApprovalRead.Found)
        assertFalse((store.read("owner-1","cycle-1","build")
            as NativeFutureApprovalRead.Found).isCurrent)
        assertEquals(NativeFutureApprovalPreview.Rejected(
            NativeFutureApprovalRefusal.NOT_READY),
            store.preview("owner-1","cycle-1","build"))
    }

    @Test fun futureApprovalFingerprintCoversCoachingContentBeyondRevisionLabels() {
        val future=futureContext()
        val baseline=NativeFutureBlockContextCodec.coachingContentFingerprint(future)
        assertEquals(baseline,
            NativeFutureBlockContextCodec.coachingContentFingerprint(future))
        val sameLabelDifferentDose=future.copy(policy=future.policy.copy(
            selectedBounds=future.policy.selectedBounds.map {
                if(it.muscle==TrainingMuscle.ABS)it.copy(
                    mav=it.mav.add(n("0.5")))else it
            }))
        assertNotEquals(baseline,
            NativeFutureBlockContextCodec.coachingContentFingerprint(
                sameLabelDifferentDose))
        val sameLabelDifferentRationale=future.copy(
            policy=future.policy.copy(rationale="New coach review"))
        assertNotEquals(baseline,
            NativeFutureBlockContextCodec.coachingContentFingerprint(
                sameLabelDifferentRationale))
        val sameLabelDifferentTime=future.copy(
            nonSetSecondsByWeekday=future.nonSetSecondsByWeekday+
                (1 to 250))
        assertNotEquals(baseline,
            NativeFutureBlockContextCodec.coachingContentFingerprint(
                sameLabelDifferentTime))
        assertEquals(future.policy.revision,sameLabelDifferentDose.policy.revision)
    }

    @Test fun sourceActivationPlanCannotChangeGymGoalOrPhasePolicyWithoutReview() {
        readyFutureForSecondConsent()
        val saved=(futureStore().read("owner-1","cycle-1","build")
            as NativeFutureBlockContextRead.Found).receipt
        val baseline=NativeFutureActivationReview.BoundaryReviewed(
            "cycle-1","build",4,3,9,7)
        val original=source()
        val prepared=NativeFutureActivationSourcePlanDomain.prepare(
            original,saved,baseline,futureCycle())
        assertNotNull(prepared)
        assertEquals(original.catalog,prepared!!.proposedSource.catalog)
        assertEquals(original.athleteId,prepared.proposedSource.athleteId)
        assertEquals(original.revision+1,prepared.proposedSource.revision)
        assertEquals(1L,original.revision)

        val other=checkNotNull(saved.future.setup.reviewed)
        val invalidGym=saved.copy(future=saved.future.copy(
            setup=saved.future.setup.copy(reviewed=other.copy(
                maxBarbellExercisesPerDay=1))))
        assertNull(NativeFutureActivationSourcePlanDomain.prepare(
            original,invalidGym,baseline,futureCycle()))
        val invalidPolicy=saved.copy(future=saved.future.copy(
            policy=saved.future.policy.copy(trainingDays=3)))
        assertNull(NativeFutureActivationSourcePlanDomain.prepare(
            original,invalidPolicy,baseline,futureCycle()))
        val invalidTimes=saved.copy(future=saved.future.copy(
            nonSetSecondsByWeekday=mapOf(0 to 200,1 to 200,2 to 200)))
        assertNull(NativeFutureActivationSourcePlanDomain.prepare(
            original,invalidTimes,baseline,futureCycle()))
        assertNull(NativeFutureActivationSourcePlanDomain.prepare(
            original.copy(revision=2),saved,baseline,futureCycle()))
        assertNull(NativeFutureActivationSourcePlanDomain.prepare(
            original,saved,baseline.copy(blockId="base"),futureCycle()))
    }

    @Test fun revisionedRevocationAndAccountSwitchCannotResurrectSecondConsent() {
        readyFutureForSecondConsent()
        val store=approvalCheckpoint()
        val shown=store.preview("owner-1","cycle-1","build")
            as NativeFutureApprovalPreview.Ready
        val first=(store.confirm("owner-1","cycle-1","build",0,
            shown.fingerprint,true) as NativeFutureApprovalWrite.Committed).approval
        active=NativeKotlinOwnerIdentity("other-owner","athlete-1")
        assertEquals(NativeFutureApprovalRead.Rejected(
            NativeFutureApprovalRefusal.NOT_AUTHORIZED),
            store.read("owner-1","cycle-1","build"))
        active=NativeKotlinOwnerIdentity("owner-1","athlete-1")
        assertEquals(NativeFutureApprovalWrite.Revoked(2),
            store.revoke("owner-1","cycle-1","build",1))
        assertEquals(NativeFutureApprovalRead.Revoked(2),
            store.read("owner-1","cycle-1","build"))
        assertEquals(NativeFutureApprovalWrite.Rejected(
            NativeFutureApprovalRefusal.REVISION_CONFLICT),
            store.confirm("owner-1","cycle-1","build",0,
                first.reviewedFingerprint,true))
        val updated=store.preview("owner-1","cycle-1","build")
            as NativeFutureApprovalPreview.Ready
        assertEquals(2L,updated.expectedRecordRevision)
        assertEquals(NativeFutureApprovalWrite.Committed(
            first.copy(revision=3)),
            store.confirm("owner-1","cycle-1","build",2,
                updated.fingerprint,true))
    }

    @Test fun actualProductionBoundaryIsNotBypassedBySecondConsent() {
        readyFutureForSecondConsent()
        val history=NativeKotlinHistoryManifestFileStore(temp.root.toPath())
        val composer=KotlinNativeJournalFileStore(temp.root.toPath())
        val consent=store()
        val preflight=NativeKotlinCustomWorkoutPreflight(
            fence,identity,drafts,sources,consent)
        val prepared=NativeKotlinCustomWorkoutPreparedStore(
            temp.root.toPath(),fence,identity,preflight)
        val issued=NativeKotlinCustomWorkoutJournalFileStore(
            temp.root.toPath(),fence,identity,prepared,preflight,
            history,composer)
        val corrections=KotlinNativeSavedCorrectionFileStore(
            temp.root.toPath(),composer)
        val approval=NativeKotlinApprovalFileAuthority(
            temp.root.toPath(),fence,identity)
        val reader=NativeKotlinMixedHistoryReadCoordinator(
            history,composer,corrections,issued,approval,identity,fence)
        val inventory=NativeKotlinCustomOutstandingWorkoutInventory(
            prepared,issued)
        val composerInventory=NativeKotlinComposerActivationInventory(
            composer,approval)
        val live=NativeKotlinFutureBlockActivationPreflight(
            fence,identity,drafts,futureStore(),reader,issued,
            inventory,composerInventory)
        val actual=NativeKotlinFutureActivationApprovalFileStore(
            temp.root.toPath(),fence,identity,live,
            futureStore(),drafts,sources,permissionStore)
        assertEquals(NativeFutureApprovalPreview.Rejected(
            NativeFutureApprovalRefusal.NOT_READY),
            actual.preview("owner-1","cycle-1","build"))
        assertEquals(NativeFutureApprovalRead.Missing,
            actual.read("owner-1","cycle-1","build"))
    }

}
