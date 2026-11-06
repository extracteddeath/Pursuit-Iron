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
        fence,identity,drafts,sources,permissionStore,store())
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
            NativeAdaptiveCycleDraftRefusal.FUTURE_PROFILE_REQUIRES_APPROVAL),
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
    private fun futureCycle():CustomTrainingCycle {
        val base=cycle(2)
        return base.copy(blocks=base.blocks.map {block ->
            block.copy(split=CustomSplitIdentity.Named(
                ComposerSplit.UPPER_LOWER),
                days=(block.days+CustomTrainingDay(1,"Day 1",
                    ComposerSessionPurpose.HYPERTROPHY,
                    listOf(CustomExerciseSlot("slot-1","bodyweight_crunch",
                        ComposerRole.ACCESSORY,listOf(set())))))
                    .sortedBy {it.weekday})
        })
    }
    private fun futurePermissions()=CustomCycleCoachPermissions(
        adaptSplit=true,adaptSchedule=true,adaptDayCount=true,
        adaptExercises=true)
    private fun futureCandidate(
        context:ProspectiveBlockCoachingContext=futureContext()
    )=option(proposed=futureCycle(),permissions=futurePermissions())
        .copy(expectedPermissionRevision=2,prospective=context)

    @Test fun futureScheduleRequiresFullProspectiveReviewAndExplicitDraftAcceptance() {
        initialize()
        val signed=approved()
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
        assertEquals(NativeAdaptiveCycleDraftDecision.Saved(2,"coach_option_1"),
            adaptive().accept(prospective,reviewed.reviewedFingerprint,true))
        assertEquals(NativeCustomDraftReadResult.Found(futureCycle()),
            drafts.read("owner-1","cycle-1"))
        assertEquals(NativeCustomSignoffRead.Found(signed,false),
            store().read("owner-1","cycle-1",inputs()))
        // This is a DRAFT: the actual reviewed gym/profile remains the
        // original three-day split and issuance is still unauthorized.
        assertEquals(ComposerSplit.FULL_BODY,
            (sources.read("owner-1") as NativeAuthoringSourceRead.Found)
                .source.setup.reviewed!!.split)
        assertEquals(NativeCustomSignoffWrite.Rejected(
            NativeCustomSignoffRefusal.COACH_REVIEW_UNRESOLVED),
            store().confirm("owner-1","cycle-1",2,1,1,inputs(),true))
    }

    @Test fun futureScheduleCannotLaunderNewGymOrUnapprovedFrequency() {
        initialize()
        approved()
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
        assertEquals(NativeCustomDraftReadResult.Found(cycle()),
            drafts.read("owner-1","cycle-1"))
    }

}
