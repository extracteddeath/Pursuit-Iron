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
    /** Isolated weighted variant for the real progression integration test. */
    private var weightedFixture=false
    private var phaseTransitionFixture=false
    private var machineBindingFixture=false
    private var phaseAuthoredWeight=25
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
        gym=GymProfileDraft("gym-1",LoadUnit.LB,
            if(machineBindingFixture)mapOf(
                "leg-extension-machine" to VerifiedLoadStock.Stack(
                    LoadUnit.LB,LoadBasis.EXERCISE_OVERRIDE,
                    n("20"),n("5"),n("160")))
            else if(phaseTransitionFixture)mapOf("straight-bar" to VerifiedLoadStock.Barbell(
                LoadUnit.LB,n("10"),mapOf(n("5") to (if(phaseAuthoredWeight>=30)4 else 2),n("2.5") to 2)))
            else if(weightedFixture)mapOf("dumbbell-rack" to VerifiedLoadStock.Totals(
                LoadUnit.LB,LoadBasis.DUMBBELL_PER_HAND,
                listOf(n("20"),n("25"),n("30")))) else emptyMap(),
            if(phaseTransitionFixture)setOf("flat-bench","rack") else emptySet(),emptySet()),
        allowBodyweight=true,allowSupersets=false,
        allowTricepsOnLower=false,maxBarbellExercisesPerDay=if(phaseTransitionFixture)1 else 0,
        focusedMuscles=emptySet(),reducedMuscles=emptySet(),
        excludedExerciseIds=emptySet(),lockedTrainingDays=emptySet(),
        hardTrainingDays=emptySet(),longTrainingDays=emptySet(),
        declaredRestDays=setOf(1,3,5,6)
    ))
    private fun source(rev:Long=1)=NativeCustomAuthoringSnapshot(
        "owner-1","athlete-1",rev,setup(),StarterExerciseTranche.movements())
    private fun set()=CustomAuthoredSet(DraftSetType.WORKING,
        if(machineBindingFixture)DraftSetLoad.Verified(
            LoadUnit.LB,LoadBasis.EXERCISE_OVERRIDE,n("55"))
        else if(weightedFixture)DraftSetLoad.Verified(LoadUnit.LB,
            if(phaseTransitionFixture)LoadBasis.BARBELL_TOTAL else LoadBasis.DUMBBELL_PER_HAND,n("20"))
        else DraftSetLoad.ExplicitBodyweight,RepBand(10,15),2,55,75)
    private fun cycle(rev:Long=1)=CustomTrainingCycle(
        "cycle-1",rev,"athlete-1",AthleteTrainingGoal.HYPERTROPHY,
        "My custom cycle",listOf(CustomTrainingBlock(
            "base","Foundation",TrainingPhase.HYPERTROPHY,
            CustomBlockPurpose.TRAINING,3,
            CustomSplitIdentity.Freeform("Custom core"),listOf(0,2,4).map {
                day->CustomTrainingDay(day,"Day $day",
                    ComposerSessionPurpose.HYPERTROPHY,
                    listOf(CustomExerciseSlot("slot-$day",
                        if(machineBindingFixture)"leg_extension" else
                            if(phaseTransitionFixture)"barbell_bench" else
                                if(weightedFixture)"db_curl" else "bodyweight_crunch",
                        if(phaseTransitionFixture)ComposerRole.PRIMARY else
                            ComposerRole.ACCESSORY,listOf(set()),
                        machineInstanceId=if(machineBindingFixture)"A" else null)))
            }
        )))
    private fun bounds()=TrainingMuscle.entries.map{muscle ->
        val focus=if(machineBindingFixture)TrainingMuscle.QUADS else
            if(phaseTransitionFixture)TrainingMuscle.CHEST else
            if(weightedFixture)TrainingMuscle.BICEPS else TrainingMuscle.ABS
        DeclaredMuscleDoseBounds(muscle,
            if(muscle==focus)n("3") else BigDecimal.ZERO,
            if(muscle==focus)n("4") else BigDecimal.ZERO,
            if(muscle==focus || phaseTransitionFixture)n("5") else BigDecimal.ZERO,
            if(muscle==focus)3 else 0)
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

    @Test fun signedMachineASlotPreparesAndColdOpensDespiteGymBDefault() {
        machineBindingFixture=true
        initialize()
        approved()
        val root=temp.root.toPath()
        val roster=NativeKotlinMachineRosterFileStore(root)
        val a=KotlinPhysicalMachine("A","Leg extension A",
            unit=LoadUnit.LB,increment=n("5"),
            minimum=n("20"),maximum=n("160"))
        val b=KotlinPhysicalMachine("B","Leg extension B",
            unit=LoadUnit.LB,increment=n("10"),
            minimum=n("20"),maximum=n("180"))
        val config=KotlinGymMachineAlternatives(
            "gym-1","leg_extension","B",listOf(a,b))
        assertTrue(roster.save("owner-1",0,listOf(config))
            is NativeMachineRosterResult.Saved)
        val preflight=NativeKotlinCustomWorkoutPreflight(
            fence,identity,drafts,sources,store(),roster)
        val reviewed=preflight.inspect("owner-1","cycle-1",1,0,inputs())
        assertTrue(reviewed.toString(),
            reviewed is NativeCustomPreflightResult.ReviewedPreview)
        val session=(reviewed as NativeCustomPreflightResult.ReviewedPreview).session
        assertEquals("A",session.slots.single().machineInstanceId)
        assertEquals(n("55"),
            (session.slots.single().sets.single().prescription.load
                as DraftSetLoad.Verified).value)
        val ledger=NativeKotlinHistoryManifestFileStore(root)
        assertTrue(ledger.enroll("owner-1","athlete-1"){owner,athlete->
            active==NativeKotlinOwnerIdentity(owner,athlete)
        } is NativeHistoryLedgerResult.Durable)
        val prepared=NativeKotlinCustomWorkoutPreparedStore(
            root,fence,identity,preflight)
        val stored=prepared.prepare("owner-1","machine-a-visit",
            "cycle-1",1,0,inputs())
        assertTrue(stored.toString(),stored is NativeCustomPrepareResult.Prepared)
        val issued=NativeKotlinCustomWorkoutJournalFileStore(
            root,fence,identity,prepared,preflight,ledger,
            KotlinNativeJournalFileStore(root))
        val first=issued.issue("owner-1","machine-a-visit",
            "cycle-1",1,0,inputs())
        assertTrue(first.toString(),first is NativeCustomJournalResult.Durable)
        val coldRoster=NativeKotlinMachineRosterFileStore(root)
        val coldPreflight=NativeKotlinCustomWorkoutPreflight(
            fence,identity,drafts,sources,store(),coldRoster)
        val coldPrepared=NativeKotlinCustomWorkoutPreparedStore(
            root,fence,identity,coldPreflight)
        val coldIssuer=NativeKotlinCustomWorkoutJournalFileStore(
            root,fence,identity,coldPrepared,coldPreflight,ledger,
            KotlinNativeJournalFileStore(root))
        val reopened=coldIssuer.open("owner-1","machine-a-visit")
        assertTrue(reopened.toString(),reopened is NativeCustomJournalResult.Durable)
        assertEquals("A",(reopened as NativeCustomJournalResult.Durable)
            .issued.journal.originalSnapshot.slots.single().machineInstanceId)
        // A subsequently deactivated physical station cannot be silently
        // replaced by the gym's B default in the original issued session.
        assertTrue(roster.save("owner-1",1,listOf(config.copy(
            machines=listOf(a.copy(active=false),b))))
            is NativeMachineRosterResult.Saved)
        assertEquals(NativeCustomPreflightResult.Rejected(
            NativeCustomPreflightRefusal.MACHINE_BINDING_NOT_VERIFIED),
            coldPreflight.inspect("owner-1","cycle-1",2,0,inputs()))
        assertEquals(reopened,coldIssuer.open("owner-1","machine-a-visit"))
        assertEquals(2L,(drafts.read("owner-1","cycle-1")
            as NativeCustomDraftReadResult.Found).draft.revision)
    }

    @Test fun exactReviewedProgramConsentSurvivesColdRestorationNotWorkoutIssuance() {
        initialize()
        assertEquals(NativeCustomSignoffRead.Missing,
            store().read("owner-1","cycle-1"))
        assertEquals(NativeCustomSavedCoachReview.Missing,
            store().recoverReviewedInputs("owner-1","cycle-1"))
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

    @Test fun savedCustomRulesColdRecoverInManualModeAndCannotBeMutated() {
        initialize()
        val supplied=inputs()
        val overhead=supplied.nonSetSecondsByWeekday.toMutableMap()
        val phases=supplied.policiesByPhase.toMutableMap()
        val mutable=supplied.copy(policiesByPhase=phases,nonSetSecondsByWeekday=overhead)
        val approved=store().confirm("owner-1","cycle-1",1,1,0,mutable,true)
            as NativeCustomSignoffWrite.Committed
        phases.clear();overhead[0]=21600
        assertTrue(coachSettings.save("owner-1",0,CoachAssistancePolicy.MANUAL)
            is NativeCoachSettingsWrite.Saved)
        val recovered=store().recoverReviewedInputs("owner-1","cycle-1")
            as NativeCustomSavedCoachReview.Ready
        assertEquals(supplied,recovered.inputs)
        assertEquals(approved.receipt,recovered.receipt)
        assertEquals(NativeCustomSignoffRead.Found(approved.receipt,true),
            store().read("owner-1","cycle-1",recovered.inputs))
        assertThrows(UnsupportedOperationException::class.java) {
            (recovered.inputs.nonSetSecondsByWeekday as MutableMap)[0]=900
        }
        assertThrows(UnsupportedOperationException::class.java) {
            (recovered.inputs.policiesByPhase.values.first().selectedBounds
                as MutableList).clear()
        }
        assertFalse(recovered.receipt.mayIssueWorkout)
        assertEquals(cycle(),(drafts.read("owner-1","cycle-1")
            as NativeCustomDraftReadResult.Found).draft)
    }

    /** Independently reproduce the published V1 envelope. V2's optional
     * input bytes are supplied only to construct a checksum-valid tamper. */
    private fun writeConsentFixture(receipt:NativeKotlinCustomCycleSignoff,
        version:Int,reviewed:CustomCoachAuditInputs?=null) {
        val bytes=java.io.ByteArrayOutputStream()
        java.io.DataOutputStream(bytes).use {out->
            out.writeInt(0x50494341);out.writeInt(version)
            out.writeUTF(receipt.ownerKey);out.writeUTF(receipt.athleteId)
            out.writeUTF(receipt.cycleId);out.writeLong(receipt.recordRevision)
            out.writeBoolean(true)
            out.writeLong(receipt.cycleRevision);out.writeLong(receipt.sourceRevision)
            out.writeUTF(receipt.fingerprintSha256)
            out.writeInt(receipt.policyReferences.size)
            receipt.policyReferences.forEach {(phase,revision)->
                out.writeUTF(phase.name);out.writeUTF(revision)
            }
            if(version==2) {
                out.writeBoolean(reviewed!=null)
                reviewed?.let {
                    val inputBytes=NativeCustomCoachAuditInputsCodec.encode(it)
                    out.writeInt(inputBytes.size);out.write(inputBytes)
                }
            }
        }
        val body=bytes.toByteArray()
        val envelope=java.io.ByteArrayOutputStream()
        java.io.DataOutputStream(envelope).use {out->
            out.writeInt(body.size);out.write(body)
            out.write(java.security.MessageDigest.getInstance("SHA-256").digest(body))
        }
        val file=Files.list(temp.root.toPath()).use {files->
            files.filter {it.fileName.toString().startsWith("custom-consent-") &&
                it.fileName.toString().endsWith(".bin")}.findFirst().orElseThrow()
        }
        Files.write(file,envelope.toByteArray())
    }

    @Test fun v1ConsentReplaysUnchangedAndOneFreshReviewSavesItsMissingRules() {
        initialize()
        val receipt=approved()
        writeConsentFixture(receipt,1)
        assertEquals(NativeCustomSignoffRead.Found(receipt,true),
            store().read("owner-1","cycle-1",inputs()))
        assertEquals(NativeCustomSavedCoachReview.LegacyWithoutInputs,
            store().recoverReviewedInputs("owner-1","cycle-1"))
        val renewed=store().confirm("owner-1","cycle-1",1,1,1,inputs(),true)
            as NativeCustomSignoffWrite.Committed
        val recovered=store().recoverReviewedInputs("owner-1","cycle-1")
            as NativeCustomSavedCoachReview.Ready
        assertEquals(receipt.copy(recordRevision=2),renewed.receipt)
        assertEquals(inputs(),recovered.inputs)
        assertEquals(renewed.receipt,recovered.receipt)
    }

    @Test fun recomputedChecksumDoesNotApproveAlteredSavedPolicyOrOverhead() {
        initialize()
        val receipt=approved()
        for(changed in listOf(inputs(policy().copy(rationale="Different rules")),
            inputs().copy(nonSetSecondsByWeekday=mapOf(0 to 201,2 to 200,4 to 200)))) {
            writeConsentFixture(receipt,2,changed)
            assertEquals(NativeCustomSavedCoachReview.Rejected(
                NativeCustomSignoffRefusal.STALE_CONSENT),
                store().recoverReviewedInputs("owner-1","cycle-1"))
        }
        assertEquals(cycle(),(drafts.read("owner-1","cycle-1")
            as NativeCustomDraftReadResult.Found).draft)
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
        assertEquals(NativeCustomSavedCoachReview.Rejected(
            NativeCustomSignoffRefusal.STALE_PROGRAM),
            store().recoverReviewedInputs("owner-1","cycle-1"))
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

    @Test fun newGymSourceRequiresFreshReviewInsteadOfRestoringOldRules() {
        initialize()
        val receipt=approved()
        assertEquals(NativeAuthoringSourceWrite.Committed(2),
            sources.publish("owner-1",1,source(2)))
        assertEquals(NativeCustomSavedCoachReview.Rejected(
            NativeCustomSignoffRefusal.STALE_SOURCE),
            store().recoverReviewedInputs("owner-1","cycle-1"))
        assertEquals(NativeCustomSignoffRead.Found(receipt,false),
            store().read("owner-1","cycle-1",inputs()))
    }

    @Test fun identitySwitchAndRevocationFailClosed() {
        initialize()
        val receipt=approved()
        active=NativeKotlinOwnerIdentity("other-owner","athlete-1")
        assertEquals(NativeCustomSignoffRead.Rejected(
            NativeCustomSignoffRefusal.NOT_AUTHORIZED),
            store().read("owner-1","cycle-1",inputs()))
        assertEquals(NativeCustomSavedCoachReview.Rejected(
            NativeCustomSignoffRefusal.NOT_AUTHORIZED),
            store().recoverReviewedInputs("owner-1","cycle-1"))
        active=NativeKotlinOwnerIdentity("owner-1","athlete-1")
        val revoked=store().revoke("owner-1","cycle-1",1)
        assertTrue(revoked is NativeCustomSignoffWrite.Committed)
        assertEquals(NativeCustomSignoffRead.Revoked(2),
            store().read("owner-1","cycle-1",inputs()))
        assertEquals(NativeCustomSavedCoachReview.Rejected(
            NativeCustomSignoffRefusal.STALE_CONSENT),
            store().recoverReviewedInputs("owner-1","cycle-1"))
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
        assertEquals(NativeCustomSavedCoachReview.Rejected(
            NativeCustomSignoffRefusal.CORRUPT_RECORD),
            store().recoverReviewedInputs("owner-1","cycle-1"))
        assertEquals(NativeCustomSignoffWrite.Rejected(
            NativeCustomSignoffRefusal.CORRUPT_RECORD),
            store().confirm("owner-1","cycle-1",1,1,1,inputs(),true))
    }

    private val coachSettings by lazy {NativeKotlinCoachAssistanceSettingsStore(
        temp.root.toPath(),fence,identity)}
    private fun adaptive()=NativeKotlinAdaptiveCycleDraftAcceptance(
        fence,identity,drafts,sources,permissionStore,store(),futureStore(),
        coachSettings)
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

    @Test fun manualModeRevokesAlreadyDisplayedAdaptiveCycleProposal() {
        initialize()
        approved()
        val candidate=option()
        val displayed=adaptive().preview(candidate)
            as NativeAdaptiveCycleDraftPreview.Ready
        assertEquals(NativeCoachSettingsWrite.Saved(
            NativeCoachAssistanceSettings("owner-1","athlete-1",1,
                CoachAssistancePolicy.MANUAL)),
            coachSettings.save("owner-1",0,CoachAssistancePolicy.MANUAL))
        assertEquals(NativeAdaptiveCycleDraftPreview.Rejected(
            NativeAdaptiveCycleDraftRefusal.ASSISTANCE_DISABLED),
            adaptive().preview(candidate))
        assertEquals(NativeAdaptiveCycleDraftDecision.Rejected(
            NativeAdaptiveCycleDraftRefusal.ASSISTANCE_DISABLED),
            adaptive().accept(candidate,displayed.reviewedFingerprint,true))
        assertEquals(NativeCustomDraftReadResult.Found(cycle()),
            drafts.read("owner-1","cycle-1"))
        assertEquals(NativeCustomSignoffRead.Found(
            (store().read("owner-1","cycle-1",inputs())
                as NativeCustomSignoffRead.Found).receipt,true),
            store().read("owner-1","cycle-1",inputs()))
    }

    @Test fun progressionOptOutDoesNotDisableOtherStructuralCoaching() {
        initialize()
        approved()
        assertTrue(coachSettings.save("owner-1",0,
            CoachAssistancePolicy.STANDARD.copy(
                progressionSuggestionsEnabled=false))
            is NativeCoachSettingsWrite.Saved)
        assertEquals(NativeAdaptiveCycleDraftPreview.Rejected(
            NativeAdaptiveCycleDraftRefusal.ASSISTANCE_DISABLED),
            adaptive().preview(option()))
        val cueOnly=option(
            proposed=cycle(2).copy(blocks=cycle(2).blocks.map {
                it.copy(label="Different reviewed block label")
            }))
        val structural=adaptive().preview(cueOnly)
        assertTrue(structural.toString(),
            structural is NativeAdaptiveCycleDraftPreview.Ready)
        val displayed=structural as NativeAdaptiveCycleDraftPreview.Ready
        assertEquals(setOf(CustomCycleChangeKind.CUES),
            displayed.changes.map {it.kind}.toSet())
        assertEquals(NativeAdaptiveCycleDraftDecision.Saved(
            2,"coach_option_1"),
            adaptive().accept(cueOnly,displayed.reviewedFingerprint,true))
    }

    @Test fun assistanceRevisionChangesInvalidateDisplayedApprovalEvenWhenStillEnabled() {
        initialize()
        approved()
        val candidate=option()
        val displayed=adaptive().preview(candidate)
            as NativeAdaptiveCycleDraftPreview.Ready
        assertTrue(coachSettings.save("owner-1",0,
            CoachAssistancePolicy.PROGRESSION_ONLY)
            is NativeCoachSettingsWrite.Saved)
        val updated=adaptive().preview(candidate)
            as NativeAdaptiveCycleDraftPreview.Ready
        assertNotEquals(displayed.reviewedFingerprint,
            updated.reviewedFingerprint)
        assertEquals(NativeAdaptiveCycleDraftDecision.Rejected(
            NativeAdaptiveCycleDraftRefusal.REVIEW_CHANGED),
            adaptive().accept(candidate,displayed.reviewedFingerprint,true))
        assertEquals(NativeCustomDraftReadResult.Found(cycle()),
            drafts.read("owner-1","cycle-1"))
        // Unchanged full review is still permitted under the athlete's
        // chosen no-fatigue progression mode and a NEW on-screen approval.
        assertEquals(NativeAdaptiveCycleDraftDecision.Saved(
            2,"coach_option_1"),
            adaptive().accept(candidate,updated.reviewedFingerprint,true))
    }

    @Test fun corruptCoachSettingsRefuseNativeAdaptiveCycleWithoutRestoringDefault() {
        initialize()
        approved()
        assertTrue(coachSettings.save("owner-1",0,CoachAssistancePolicy.MANUAL)
            is NativeCoachSettingsWrite.Saved)
        val settingsFile=Files.list(temp.root.toPath()).use {files->
            files.filter {it.fileName.toString().startsWith(
                "coach-assistance-") && it.fileName.toString().endsWith(".bin")
            }.findFirst().orElseThrow()
        }
        val bytes=Files.readAllBytes(settingsFile)
        bytes[bytes.lastIndex]=(bytes.last().toInt() xor 1).toByte()
        Files.write(settingsFile,bytes)
        assertEquals(NativeAdaptiveCycleDraftPreview.Rejected(
            NativeAdaptiveCycleDraftRefusal.ASSISTANCE_UNAVAILABLE),
            adaptive().preview(option()))
        assertEquals(NativeCustomDraftReadResult.Found(cycle()),
            drafts.read("owner-1","cycle-1"))
    }

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
    private fun futureInputs()=inputs().copy(
        policiesByPhase=if(!phaseTransitionFixture)inputs().policiesByPhase else
            inputs().policiesByPhase+(TrainingPhase.STRENGTH to policy().copy(
                revision="strength-policy-1",phase=TrainingPhase.STRENGTH,
                minimumPurposeDays=mapOf(ComposerSessionPurpose.STRENGTH_SKILL to 3),
                primaryLiftMinimumDays=mapOf("barbell_bench" to 3))),
        continuityPolicy=
        CustomCoachVolumeContinuityPolicy(
            n("0.50"),n("0.50"),n("0.50"),n("0.50")))
    private fun futureBaselineCycle():CustomTrainingCycle {
        val authored=cycle(2)
        val original=authored.blocks.single()
        val next=original.copy(blockId="build",label="Build")
        return authored.copy(blocks=listOf(original,if(!phaseTransitionFixture)next
            else next.copy(phase=TrainingPhase.STRENGTH,days=next.days.map{day->
                day.copy(purpose=ComposerSessionPurpose.STRENGTH_SKILL,
                    slots=day.slots.map{slot->slot.copy(sets=slot.sets.map{set->
                        set.copy(load=DraftSetLoad.Verified(LoadUnit.LB,
                            LoadBasis.BARBELL_TOTAL,n(phaseAuthoredWeight.toString())),
                            reps=RepBand(5,8),targetRir=3)
                    })})
            })))
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
                                if(weightedFixture)"db_curl" else "bodyweight_crunch",
                                ComposerRole.ACCESSORY,
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
    /** Full, disk-verified historical boundary: nine real custom Finish commits. */
    private fun persistNineHistoricalBaseWorkouts(actualRir:Int?=2) {
        val root=temp.root.toPath()
        val ledger=NativeKotlinHistoryManifestFileStore(root)
        assertTrue(ledger.enroll("owner-1","athlete-1"){o,a->
            active==NativeKotlinOwnerIdentity(o,a)
        } is NativeHistoryLedgerResult.Durable)
        val preflight=NativeKotlinCustomWorkoutPreflight(
            fence,identity,drafts,sources,store())
        val prepared=NativeKotlinCustomWorkoutPreparedStore(
            root,fence,identity,preflight)
        val issued=NativeKotlinCustomWorkoutJournalFileStore(
            root,fence,identity,prepared,preflight,ledger,
            KotlinNativeJournalFileStore(root))
        val transaction=NativeKotlinCustomHistoryTransaction(
            ledger,issued,fence,identity)
        for(week in 1..3)for(day in listOf(0,2,4)) {
            val workoutId="real-base-w$week-d$day"
            assertTrue(prepared.prepare("owner-1",workoutId,"cycle-1",
                week,day,futureInputs()) is NativeCustomPrepareResult.Prepared)
            assertTrue(issued.issue("owner-1",workoutId,"cycle-1",
                week,day,futureInputs()) is NativeCustomJournalResult.Durable)
            val set="cycle-1:w$week:d$day:slot-$day:s1"
            val recorded=issued.record("owner-1",workoutId,
                CustomWorkoutJournalOperation.Record(
                    "observed-w$week-d$day",0,
                    CustomWorkoutVisitKey(set,CustomWorkoutVisitPart.MAIN),
                    KotlinJournalSetOutcome.COMPLETED,
                    KotlinPerformedSet(
                        if(weightedFixture)n("20") else null,
                        if(weightedFixture)LoadUnit.LB else null,12,actualRir)))
            assertTrue(recorded.toString(),
                recorded is NativeCustomJournalResult.Durable)
            val finish=transaction.complete("owner-1","athlete-1",
                workoutId,CustomWorkoutJournalOperation.Finish(
                    "finish-w$week-d$day",1))
            assertTrue(finish.toString(),
                finish is NativeCustomFinishResult.Committed)
        }
        val entries=(ledger.open("owner-1","athlete-1")
            as NativeHistoryLedgerResult.Durable).ledger.completions
        assertEquals(9,entries.size)
        assertEquals((1L..9L).toList(),entries.map{it.completionOrder})
    }

    private fun readyFutureForSecondConsent(realPriorHistory:Boolean=false) {
        initializeFutureBaseline();approveFutureBaseline()
        if(realPriorHistory)persistNineHistoricalBaseWorkouts()
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

    @Test fun activationPlanMustMatchExactPersistedBlockPhaseAndWeekBoundary() {
        readyFutureForSecondConsent()
        val receipt=(futureStore().read("owner-1","cycle-1","build")
            as NativeFutureBlockContextRead.Found).receipt
        val source=source()
        val actual=futureCycle()
        val boundary=NativeFutureActivationReview.BoundaryReviewed(
            "cycle-1","build",4,3,9,7)
        assertNotNull(NativeFutureActivationSourcePlanDomain.prepare(
            source,receipt,boundary,actual))
        assertNull(NativeFutureActivationSourcePlanDomain.prepare(
            source,receipt,boundary,
            actual.copy(revision=actual.revision+1)))
        assertNull(NativeFutureActivationSourcePlanDomain.prepare(
            source,receipt,boundary,
            actual.copy(cycleId="wrong-cycle")))
        assertNull(NativeFutureActivationSourcePlanDomain.prepare(
            source,receipt,boundary,
            actual.copy(blocks=actual.blocks.map {block->
                if(block.blockId=="base")block.copy(weeks=4)else block
            })))
        assertNull(NativeFutureActivationSourcePlanDomain.prepare(
            source,receipt,boundary,
            actual.copy(blocks=actual.blocks.map {block->
                if(block.blockId=="build")
                    block.copy(phase=TrainingPhase.STRENGTH)else block
            })))
        assertNull(NativeFutureActivationSourcePlanDomain.prepare(
            source,receipt.copy(future=receipt.future.copy(
                policy=receipt.future.policy.copy(phase=TrainingPhase.STRENGTH))),
            boundary,actual))
        assertNull(NativeFutureActivationSourcePlanDomain.prepare(
            source,receipt,boundary,
            actual.copy(blocks=actual.blocks.map {block->
                if(block.blockId!="build")block else block.copy(
                    days=block.days.filter {it.weekday!=1})
            })))
        assertNull(NativeFutureActivationSourcePlanDomain.prepare(
            source,receipt.copy(future=receipt.future.copy(
                policy=receipt.future.policy.copy(
                    selectedBounds=receipt.future.policy.selectedBounds.dropLast(1)))),
            boundary,actual))
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

    private fun newHandoff()=NativeKotlinFutureActivationHandoffFileStore(
        temp.root.toPath(),fence,identity,sources)

    @Test fun approvedFutureHandoffIsCrashDurableAndBlocksNewWorkoutPreparation() {
        readyFutureForSecondConsent()
        val approvals=approvalCheckpoint()
        val preview=approvals.preview("owner-1","cycle-1","build")
            as NativeFutureApprovalPreview.Ready
        val accepted=approvals.confirm("owner-1","cycle-1","build",0,
            preview.fingerprint,true) as NativeFutureApprovalWrite.Committed
        val handoff=newHandoff()
        assertFalse(handoff.blocksNewIssuance("owner-1"))
        assertEquals(NativeFutureHandoffReview.Rejected(
            NativeFutureHandoffRefusal.CONFIRMATION_REQUIRED),
            handoff.stage("owner-1","cycle-1","build",
                accepted.approval.revision,preview.fingerprint,approvals,false))
        val staged=handoff.stage("owner-1","cycle-1","build",
            accepted.approval.revision,preview.fingerprint,approvals,true)
        assertTrue(staged.toString(),staged is NativeFutureHandoffReview.Staged)
        val receipt=(staged as NativeFutureHandoffReview.Staged).receipt
        assertFalse(staged.replayed)
        assertEquals(1L,receipt.sourceRevision)
        assertEquals(4,receipt.firstWeek)
        assertEquals(preview.fingerprint,receipt.approvalFingerprint)
        assertTrue(handoff.blocksNewIssuance("owner-1"))
        assertEquals(NativeFutureHandoffReview.Staged(receipt,false),
            newHandoff().read("owner-1"))
        assertEquals(NativeFutureHandoffReview.Staged(receipt,true),
            newHandoff().stage("owner-1","cycle-1","build",
                accepted.approval.revision,preview.fingerprint,approvals,true))
        val preflight=NativeKotlinCustomWorkoutPreflight(
            fence,identity,drafts,sources,store())
        val prepared=NativeKotlinCustomWorkoutPreparedStore(
            temp.root.toPath(),fence,identity,preflight,newHandoff())
        assertEquals(NativeCustomPrepareResult.Rejected(
            NativeCustomPrepareRefusal.REVIEW_UNAVAILABLE),
            prepared.prepare("owner-1","new_workout","cycle-1",1,0,inputs()))
        assertEquals(NativeAuthoringSourceRead.Found(source()),
            sources.read("owner-1"))
        assertEquals(NativeFutureHandoffReview.Cancelled,
            newHandoff().cancel("owner-1",receipt.approvalRevision,
                receipt.approvalFingerprint,true))
        assertFalse(handoff.blocksNewIssuance("owner-1"))
        assertEquals(NativeFutureHandoffReview.Missing,handoff.read("owner-1"))
    }

    @Test fun handoffRejectsStaleApprovalAndNeverClearsAfterSourceChanges() {
        readyFutureForSecondConsent()
        val approvals=approvalCheckpoint()
        val preview=approvals.preview("owner-1","cycle-1","build")
            as NativeFutureApprovalPreview.Ready
        val committed=approvals.confirm("owner-1","cycle-1","build",0,
            preview.fingerprint,true) as NativeFutureApprovalWrite.Committed
        val handoff=newHandoff()
        assertEquals(NativeFutureHandoffReview.Rejected(
            NativeFutureHandoffRefusal.REVIEW_CHANGED),
            handoff.stage("owner-1","cycle-1","build",committed.approval.revision,
                "a".repeat(64),approvals,true))
        assertEquals(NativeFutureHandoffReview.Missing,handoff.read("owner-1"))
        val staged=handoff.stage("owner-1","cycle-1","build",
            committed.approval.revision,preview.fingerprint,approvals,true)
                as NativeFutureHandoffReview.Staged
        assertEquals(NativeAuthoringSourceWrite.Committed(2),
            sources.publish("owner-1",1,source(2)))
        assertEquals(NativeFutureHandoffReview.Rejected(
            NativeFutureHandoffRefusal.SOURCE_CHANGED),
            handoff.cancel("owner-1",staged.receipt.approvalRevision,
                staged.receipt.approvalFingerprint,true))
        assertTrue(handoff.blocksNewIssuance("owner-1"))
        assertEquals(NativeFutureHandoffReview.Staged(staged.receipt,false),
            newHandoff().read("owner-1"))
        active=NativeKotlinOwnerIdentity("someone_else","athlete-1")
        assertTrue(handoff.blocksNewIssuance("owner-1"))
        assertEquals(NativeFutureHandoffReview.Rejected(
            NativeFutureHandoffRefusal.NOT_AUTHORIZED),
            handoff.read("owner-1"))
    }

    @Test fun corruptedOrInterruptedHandoffCannotBeMistakenForNoBarrier() {
        readyFutureForSecondConsent()
        val approvals=approvalCheckpoint()
        val ready=approvals.preview("owner-1","cycle-1","build")
            as NativeFutureApprovalPreview.Ready
        val approved=approvals.confirm("owner-1","cycle-1","build",0,
            ready.fingerprint,true) as NativeFutureApprovalWrite.Committed
        val handoff=newHandoff()
        assertTrue(handoff.stage("owner-1","cycle-1","build",
            approved.approval.revision,ready.fingerprint,approvals,true)
            is NativeFutureHandoffReview.Staged)
        val file=Files.list(temp.root.toPath()).use{stream->
            stream.filter {it.fileName.toString().startsWith("future-handoff-") &&
                it.fileName.toString().endsWith(".bin")}
                .findFirst().orElseThrow()
        }
        val bytes=Files.readAllBytes(file)
        val damaged=bytes.clone()
        damaged[damaged.lastIndex]=(damaged.last().toInt() xor 1).toByte()
        Files.write(file,damaged)
        assertEquals(NativeFutureHandoffReview.Rejected(
            NativeFutureHandoffRefusal.RECORD_CORRUPT),
            newHandoff().read("owner-1"))
        assertTrue(newHandoff().blocksNewIssuance("owner-1"))
        Files.write(file,bytes)
        val prefix=file.fileName.toString().removeSuffix(".bin")
        val unfinished=temp.root.toPath().resolve(prefix+"-orphan.pending")
        Files.write(unfinished,byteArrayOf(1))
        assertEquals(NativeFutureHandoffReview.Rejected(
            NativeFutureHandoffRefusal.RECORD_CORRUPT),
            newHandoff().read("owner-1"))
        assertTrue(newHandoff().blocksNewIssuance("owner-1"))
    }

    private data class ActivationFixture(
        val approvals:NativeKotlinFutureActivationApprovalFileStore,
        val handoff:NativeKotlinFutureActivationHandoffFileStore,
        val receipt:NativeFutureActivationHandoff
    )
    private fun activationFixture(
        historyRevision:Long=7,
        realPriorHistory:Boolean=false
    ):ActivationFixture {
        readyFutureForSecondConsent(realPriorHistory)
        val approvals=approvalCheckpoint {
            NativeFutureActivationReview.BoundaryReviewed(
                "cycle-1","build",4,3,9,historyRevision)
        }
        val preview=approvals.preview("owner-1","cycle-1","build")
            as NativeFutureApprovalPreview.Ready
        val accepted=approvals.confirm("owner-1","cycle-1","build",0,
            preview.fingerprint,true) as NativeFutureApprovalWrite.Committed
        val handoff=newHandoff()
        val staged=handoff.stage("owner-1","cycle-1","build",
            accepted.approval.revision,preview.fingerprint,approvals,true)
            as NativeFutureHandoffReview.Staged
        return ActivationFixture(approvals,handoff,staged.receipt)
    }
    private fun activationTransaction(
        fixture:ActivationFixture,
        onIntent:()->Unit={},
        onSource:()->Unit={}
    )=NativeKotlinFutureActivationCommitTransaction(
        temp.root.toPath(),fence,identity,sources,drafts,futureStore(),
        fixture.approvals,fixture.handoff,onIntent,onSource)

    @Test fun activatedCycleAuditsOriginalCompletedScheduleAndNewFuturePolicy() {
        val fixture=activationFixture()
        val transaction=activationTransaction(fixture)
        assertTrue(transaction.commit("owner-1") is NativeFutureCommitReview.Applied)
        val reviewed=activationTransaction(fixture).reviewActivatedCycle(
            "owner-1",futureInputs())
        assertTrue(reviewed.toString(),
            reviewed is NativeFutureActivatedCycleReview.Ready)
        val accepted=reviewed as NativeFutureActivatedCycleReview.Ready
        assertEquals("build",accepted.blockId)
        assertEquals(4,accepted.firstWeek)
        assertEquals(2L,accepted.sourceRevision)
        assertEquals(6,accepted.audit.weeks.size)
        assertEquals(3,accepted.audit.weeks.first().knownSecondsByDay.size)
        assertEquals(4,accepted.audit.weeks.last().knownSecondsByDay.size)
        assertEquals(CustomCoachAuditStatus.READY_FOR_ATHLETE_REVIEW,
            accepted.audit.status)
        assertFalse(accepted.mayIssueWorkout)
        assertTrue(fixture.handoff.blocksNewIssuance("owner-1"))

        // The activated source cannot rewrite the completed base phase.
        assertEquals(ComposerSplit.UPPER_LOWER,
            (sources.read("owner-1") as NativeAuthoringSourceRead.Found)
                .source.setup.reviewed?.split)
        assertEquals(listOf(0,2,4),futureBaselineCycle().blocks.first()
            .days.map {it.weekday})
    }

    @Test fun activatedFutureWorkoutPreviewPreservesExactCustomSetsButCannotIssue() {
        val fixture=activationFixture()
        val tx=activationTransaction(fixture)
        assertTrue(tx.commit("owner-1") is NativeFutureCommitReview.Applied)
        val preview=tx.previewActivatedWorkout("owner-1",futureInputs(),4,1)
        assertTrue(preview.toString(),preview is CustomWorkoutSnapshotResult.Preview)
        val snap=(preview as CustomWorkoutSnapshotResult.Preview).snapshot
        assertEquals("build",snap.blockId)
        assertEquals(4,snap.absoluteWeek)
        assertEquals(1,snap.weekday)
        assertEquals(2L,snap.stamp.gymCatalogSourceRevision)
        assertEquals(200,snap.declaredNonSetSeconds)
        assertEquals(listOf("slot-1"),snap.slots.map{it.slotId})
        assertEquals("bodyweight_crunch",snap.slots.single().exerciseId)
        assertEquals(RepBand(10,15),
            snap.slots.single().sets.single().prescription.reps)
        assertFalse(snap.isIssued)
        assertFalse(snap.mayRecordSets)
        assertFalse(snap.mayFinishWorkout)
        assertEquals(CustomWorkoutSnapshotResult.Rejected(
            CustomWorkoutSnapshotRefusal.COACHING_REVIEW_REQUIRED),
            tx.previewActivatedWorkout("owner-1",futureInputs(),3,0))
        assertEquals(CustomWorkoutSnapshotResult.Rejected(
            CustomWorkoutSnapshotRefusal.INVALID_WEEK),
            tx.previewActivatedWorkout("owner-1",futureInputs(),7,0))
        assertEquals(CustomWorkoutSnapshotResult.Rejected(
            CustomWorkoutSnapshotRefusal.COACHING_REVIEW_REQUIRED),
            tx.previewActivatedWorkout("owner-1",CustomCoachAuditInputs(),4,1))
        assertTrue(fixture.handoff.blocksNewIssuance("owner-1"))
    }

    @Test fun activatedCycleRejectsUnreviewedBasePolicyOrChangedDraft() {
        val fixture=activationFixture()
        val tx=activationTransaction(fixture)
        assertTrue(tx.commit("owner-1") is NativeFutureCommitReview.Applied)
        val missing=tx.reviewActivatedCycle("owner-1",
            CustomCoachAuditInputs(continuityPolicy=
                futureInputs().continuityPolicy))
        assertTrue(missing is NativeFutureActivatedCycleReview.Unresolved)
        val invalid=tx.reviewActivatedCycle("owner-1",
            futureInputs().copy(continuityPolicy=null))
        assertTrue(invalid is NativeFutureActivatedCycleReview.Unresolved)
        assertEquals(NativeCustomDraftWriteResult.Committed(4),
            drafts.save("owner-1","cycle-1",3,
                futureCycle().copy(revision=4,title="Edited after activation")))
        assertEquals(NativeFutureActivatedCycleReview.Rejected(
            NativeFutureCommitRefusal.APPROVAL_STALE),
            tx.reviewActivatedCycle("owner-1",futureInputs()))
        assertTrue(fixture.handoff.blocksNewIssuance("owner-1"))
    }

    @Test fun futureActivationSourceAndCoachingSurviveColdRestart() {
        val fixture=activationFixture()
        val tx=activationTransaction(fixture)
        assertEquals(NativeFutureCommitReview.Missing,tx.read("owner-1"))
        val applied=tx.commit("owner-1")
        assertTrue(applied.toString(),applied is NativeFutureCommitReview.Applied)
        val context=(applied as NativeFutureCommitReview.Applied).context
        assertEquals(2L,context.sourceRevision)
        assertEquals("build",context.blockId)
        assertEquals(4,context.firstWeek)
        assertEquals(futureContext().policy,context.policy)
        assertEquals(futureContext().nonSetSecondsByWeekday,
            context.nonSetSecondsByWeekday)
        assertEquals(ComposerSplit.UPPER_LOWER,
            (sources.read("owner-1") as NativeAuthoringSourceRead.Found)
                .source.setup.reviewed?.split)
        assertEquals(2L,
            (sources.read("owner-1") as NativeAuthoringSourceRead.Found)
                .source.revision)
        assertTrue(fixture.handoff.blocksNewIssuance("owner-1"))
        assertTrue(activationTransaction(fixture).read("owner-1") is
            NativeFutureCommitReview.Applied)
        assertEquals(NativeFutureCommitReview.Applied(context,true),
            activationTransaction(fixture).commit("owner-1"))
        assertEquals(NativeFutureHandoffReview.Rejected(
            NativeFutureHandoffRefusal.SOURCE_CHANGED),
            fixture.handoff.cancel("owner-1",fixture.receipt.approvalRevision,
                fixture.receipt.approvalFingerprint,true))
    }

    @Test fun interruptedActivationBeforeSourceWriteCanReplayOrExplicitlyAbort() {
        val fixture=activationFixture()
        val interrupted=activationTransaction(fixture,
            onIntent={throw IllegalStateException("simulate process death")})
        assertEquals(NativeFutureCommitReview.Rejected(
            NativeFutureCommitRefusal.STORAGE_UNAVAILABLE),
            interrupted.commit("owner-1"))
        assertEquals(NativeAuthoringSourceRead.Found(source()),
            sources.read("owner-1"))
        assertEquals(NativeFutureCommitReview.Prepared("cycle-1","build"),
            activationTransaction(fixture).read("owner-1"))
        assertTrue(fixture.handoff.blocksNewIssuance("owner-1"))
        assertEquals(NativeFutureHandoffReview.Rejected(
            NativeFutureHandoffRefusal.ALREADY_STAGED),
            fixture.handoff.cancel("owner-1",fixture.receipt.approvalRevision,
                fixture.receipt.approvalFingerprint,true))
        assertEquals(NativeFutureCommitReview.Cancelled,
            activationTransaction(fixture).abortBeforeSourceApplied(
                "owner-1",fixture.receipt.approvalRevision,
                fixture.receipt.approvalFingerprint,true))
        assertEquals(NativeFutureHandoffReview.Missing,
            fixture.handoff.read("owner-1"))
        assertEquals(NativeAuthoringSourceRead.Found(source()),
            sources.read("owner-1"))
    }

    @Test fun interruptedActivationAfterSourceWriteReplaysWithoutDoubleIncrement() {
        val fixture=activationFixture()
        val interrupted=activationTransaction(fixture,
            onSource={throw IllegalStateException("simulate process death")})
        assertEquals(NativeFutureCommitReview.Rejected(
            NativeFutureCommitRefusal.STORAGE_UNAVAILABLE),
            interrupted.commit("owner-1"))
        assertEquals(2L,
            (sources.read("owner-1") as NativeAuthoringSourceRead.Found)
                .source.revision)
        val recovered=activationTransaction(fixture)
        assertTrue(recovered.read("owner-1") is NativeFutureCommitReview.Applied)
        assertTrue(recovered.commit("owner-1") is NativeFutureCommitReview.Applied)
        assertEquals(2L,
            (sources.read("owner-1") as NativeAuthoringSourceRead.Found)
                .source.revision)
        assertEquals(NativeFutureCommitReview.Rejected(
            NativeFutureCommitRefusal.SOURCE_CONFLICT),
            recovered.abortBeforeSourceApplied("owner-1",
                fixture.receipt.approvalRevision,
                fixture.receipt.approvalFingerprint,true))
        assertTrue(fixture.handoff.blocksNewIssuance("owner-1"))
    }

    @Test fun activationIntentRejectsStaleApprovalAndSourceConflictWithoutWriting() {
        val fixture=activationFixture()
        assertEquals(NativeFutureApprovalWrite.Revoked(2),
            fixture.approvals.revoke("owner-1","cycle-1","build",1))
        assertEquals(NativeFutureCommitReview.Rejected(
            NativeFutureCommitRefusal.APPROVAL_STALE),
            activationTransaction(fixture).commit("owner-1"))
        assertEquals(NativeAuthoringSourceRead.Found(source()),
            sources.read("owner-1"))
        assertEquals(NativeFutureCommitReview.Missing,
            activationTransaction(fixture).read("owner-1"))
        assertTrue(fixture.handoff.blocksNewIssuance("owner-1"))
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

    private fun activatedSignoff(fixture:ActivationFixture)=
        NativeKotlinActivatedCycleSignoffFileStore(
            temp.root.toPath(),fence,identity,activationTransaction(fixture))

    @Test fun activatedSignoffRequiresFreshAuditAndIndependentAthleteConfirmation() {
        val fixture=activationFixture()
        val signoff=activatedSignoff(fixture)
        val tx=activationTransaction(fixture)
        val before="0".repeat(64)
        assertEquals(NativeActivatedSignoffWrite.Rejected(
            NativeActivatedSignoffRefusal.REVIEW_NOT_READY),
            signoff.confirm("owner-1",0,before,futureInputs(),true))
        assertTrue(tx.commit("owner-1") is NativeFutureCommitReview.Applied)
        val ready=tx.reviewActivatedCycle("owner-1",futureInputs())
            as NativeFutureActivatedCycleReview.Ready
        assertTrue(ready.reviewFingerprint.matches(Regex("[a-f0-9]{64}")))
        assertEquals(NativeActivatedSignoffWrite.Rejected(
            NativeActivatedSignoffRefusal.CONFIRMATION_REQUIRED),
            signoff.confirm("owner-1",0,ready.reviewFingerprint,futureInputs(),false))
        assertEquals(NativeActivatedSignoffRead.Missing,
            signoff.read("owner-1",futureInputs()))
        assertEquals(NativeActivatedSignoffWrite.Rejected(
            NativeActivatedSignoffRefusal.FINGERPRINT_CHANGED),
            signoff.confirm("owner-1",0,before,futureInputs(),true))
        assertEquals(NativeActivatedSignoffWrite.Rejected(
            NativeActivatedSignoffRefusal.REVIEW_NOT_READY),
            signoff.confirm("owner-1",0,ready.reviewFingerprint,
                CustomCoachAuditInputs(),true))
        val committed=signoff.confirm("owner-1",0,ready.reviewFingerprint,
            futureInputs(),true) as NativeActivatedSignoffWrite.Committed
        assertEquals(1L,committed.receipt.recordRevision)
        assertFalse(committed.receipt.mayIssueWorkout)
        assertFalse(committed.receipt.mayChangeHistory)
        assertEquals(NativeActivatedSignoffRead.Found(committed.receipt,true),
            activatedSignoff(fixture).read("owner-1",futureInputs()))
        assertEquals(NativeActivatedSignoffRead.Found(committed.receipt,false),
            activatedSignoff(fixture).read("owner-1"))
        assertEquals(NativeActivatedSignoffWrite.Rejected(
            NativeActivatedSignoffRefusal.REVISION_CONFLICT),
            activatedSignoff(fixture).confirm("owner-1",0,
                ready.reviewFingerprint,futureInputs(),true))
        assertTrue(fixture.handoff.blocksNewIssuance("owner-1"))
    }

    @Test fun activatedConsentTracksChangedCoachingAndStaleDraftWithoutReissuing() {
        val fixture=activationFixture()
        val tx=activationTransaction(fixture)
        assertTrue(tx.commit("owner-1") is NativeFutureCommitReview.Applied)
        val inputs=futureInputs()
        val fingerprint=(tx.reviewActivatedCycle("owner-1",inputs)
            as NativeFutureActivatedCycleReview.Ready).reviewFingerprint
        val signoff=activatedSignoff(fixture)
        val receipt=(signoff.confirm("owner-1",0,fingerprint,inputs,true)
            as NativeActivatedSignoffWrite.Committed).receipt
        val previous=signoff.read("owner-1",inputs)
        assertEquals(NativeActivatedSignoffRead.Found(receipt,true),previous)
        val original=inputs.policiesByPhase.getValue(TrainingPhase.HYPERTROPHY)
        val amended=inputs.copy(policiesByPhase=
            inputs.policiesByPhase+(TrainingPhase.HYPERTROPHY to
                original.copy(rationale="Changed coaching rationale")))
        val revised=tx.reviewActivatedCycle("owner-1",amended)
        assertTrue(revised.toString(),revised is NativeFutureActivatedCycleReview.Ready)
        assertNotEquals(fingerprint,
            (revised as NativeFutureActivatedCycleReview.Ready).reviewFingerprint)
        assertEquals(NativeActivatedSignoffRead.Found(receipt,false),
            signoff.read("owner-1",amended))
        assertEquals(NativeActivatedSignoffWrite.Rejected(
            NativeActivatedSignoffRefusal.FINGERPRINT_CHANGED),
            signoff.confirm("owner-1",1,fingerprint,amended,true))
        assertEquals(NativeCustomDraftWriteResult.Committed(4),
            drafts.save("owner-1","cycle-1",3,
                futureCycle().copy(revision=4,title="Changed after approval")))
        assertEquals(NativeActivatedSignoffRead.Found(receipt,false),
            activatedSignoff(fixture).read("owner-1",inputs))
        assertEquals(NativeActivatedSignoffWrite.Rejected(
            NativeActivatedSignoffRefusal.REVIEW_NOT_READY),
            activatedSignoff(fixture).confirm("owner-1",1,
                fingerprint,inputs,true))
        assertTrue(fixture.handoff.blocksNewIssuance("owner-1"))
    }

    @Test fun activatedConsentRevocationAndCorruptionNeverBecomeApproval() {
        val fixture=activationFixture()
        val tx=activationTransaction(fixture)
        assertTrue(tx.commit("owner-1") is NativeFutureCommitReview.Applied)
        val inputs=futureInputs()
        val fingerprint=(tx.reviewActivatedCycle("owner-1",inputs)
            as NativeFutureActivatedCycleReview.Ready).reviewFingerprint
        val signoff=activatedSignoff(fixture)
        val receipt=(signoff.confirm("owner-1",0,fingerprint,inputs,true)
            as NativeActivatedSignoffWrite.Committed).receipt
        assertEquals(NativeActivatedSignoffWrite.Revoked(2),
            activatedSignoff(fixture).revoke("owner-1",1))
        assertEquals(NativeActivatedSignoffRead.Revoked(2),
            signoff.read("owner-1",inputs))
        assertEquals(NativeActivatedSignoffWrite.Rejected(
            NativeActivatedSignoffRefusal.REVISION_CONFLICT),
            signoff.confirm("owner-1",1,fingerprint,inputs,true))
        assertEquals(NativeActivatedSignoffRead.Rejected(
            NativeActivatedSignoffRefusal.NOT_AUTHORIZED),
            signoff.read("other-owner",inputs))
        val file=Files.list(temp.root.toPath()).use{stream->
            stream.filter{it.fileName.toString().startsWith("activated-cycle-signoff-") &&
                it.fileName.toString().endsWith(".bin")}.findFirst().orElseThrow()
        }
        val original=Files.readAllBytes(file)
        val corrupt=original.clone()
        corrupt[corrupt.lastIndex]=(corrupt.last().toInt() xor 1).toByte()
        Files.write(file,corrupt)
        assertEquals(NativeActivatedSignoffRead.Rejected(
            NativeActivatedSignoffRefusal.RECORD_CORRUPT),
            activatedSignoff(fixture).read("owner-1",inputs))
        Files.write(file,original)
        val pending=temp.root.toPath().resolve(
            file.fileName.toString().removeSuffix(".bin")+"-abandoned.pending")
        Files.write(pending,byteArrayOf(1))
        assertEquals(NativeActivatedSignoffRead.Rejected(
            NativeActivatedSignoffRefusal.RECORD_CORRUPT),
            signoff.read("owner-1",inputs))
        assertFalse(receipt.mayIssueWorkout)
        assertTrue(fixture.handoff.blocksNewIssuance("owner-1"))
    }

    private fun activatedPrepared(
        fixture:ActivationFixture
    ):NativeKotlinCustomWorkoutPreparedStore {
        val preflight=NativeKotlinCustomWorkoutPreflight(
            fence,identity,drafts,sources,store())
        return NativeKotlinCustomWorkoutPreparedStore(
            temp.root.toPath(),fence,identity,preflight,fixture.handoff)
    }

    @Test fun activatedWorkoutIsPreparedLosslesslyAfterConsentAndColdRecovers() {
        val fixture=activationFixture()
        val tx=activationTransaction(fixture)
        val signoff=activatedSignoff(fixture)
        val prepared=activatedPrepared(fixture)
        val inputs=futureInputs()
        val name="activated-build-week4-day1"
        assertEquals(NativeCustomPrepareResult.Rejected(
            NativeCustomPrepareRefusal.REVIEW_UNAVAILABLE),
            prepared.prepareActivated("owner-1",name,"cycle-1",4,1,
                inputs,signoff,tx))
        assertTrue(tx.commit("owner-1") is NativeFutureCommitReview.Applied)
        assertEquals(NativeCustomPrepareResult.Rejected(
            NativeCustomPrepareRefusal.REVIEW_UNAVAILABLE),
            prepared.prepareActivated("owner-1",name,"cycle-1",4,1,
                inputs,signoff,tx))
        val review=tx.reviewActivatedCycle("owner-1",inputs)
            as NativeFutureActivatedCycleReview.Ready
        val confirmed=signoff.confirm("owner-1",0,
            review.reviewFingerprint,inputs,true)
        assertTrue(confirmed is NativeActivatedSignoffWrite.Committed)
        val first=prepared.prepareActivated("owner-1",name,"cycle-1",4,1,
            inputs,signoff,tx)
        assertTrue(first.toString(),first is NativeCustomPrepareResult.Prepared)
        val workout=(first as NativeCustomPrepareResult.Prepared).workout
        assertFalse(first.replayed)
        assertEquals(NativeCustomPreparationAuthority.ACTIVATED_BLOCK,
            workout.authority)
        assertEquals("build",workout.snapshot.blockId)
        assertEquals(2L,workout.snapshot.stamp.gymCatalogSourceRevision)
        assertEquals("slot-1",workout.snapshot.slots.single().slotId)
        assertEquals(RepBand(10,15),
            workout.snapshot.slots.single().sets.single().prescription.reps)
        assertEquals(200,workout.snapshot.declaredNonSetSeconds)
        assertFalse(workout.isIssued)
        assertFalse(workout.mayLog)
        assertFalse(workout.mayFinish)
        assertEquals(NativeCustomPreparedRead.Found(workout),
            activatedPrepared(fixture).open("owner-1",name))
        assertEquals(NativeCustomPrepareResult.Prepared(workout,true),
            activatedPrepared(fixture).prepareActivated("owner-1",name,
                "cycle-1",4,1,inputs,activatedSignoff(fixture),
                activationTransaction(fixture)))
        assertEquals(NativeCustomPrepareResult.Rejected(
            NativeCustomPrepareRefusal.DIFFERENT_WORKOUT_ALREADY_PREPARED),
            prepared.prepareActivated("owner-1",name,"cycle-1",4,2,
                inputs,signoff,tx))
        assertEquals(NativeCustomPrepareResult.Rejected(
            NativeCustomPrepareRefusal.DIFFERENT_WORKOUT_ALREADY_PREPARED),
            activatedPrepared(fixture).prepareActivated("owner-1",
                "second-id-same-day","cycle-1",4,1,
                inputs,activatedSignoff(fixture),activationTransaction(fixture)))
        assertEquals(NativeCustomPrepareResult.Rejected(
            NativeCustomPrepareRefusal.REVIEW_UNAVAILABLE),
            prepared.prepare("owner-1","unapproved-new","cycle-1",4,1,inputs))
        assertTrue(fixture.handoff.blocksNewIssuance("owner-1"))
    }

    @Test fun activatedPreparedWorkoutCannotEnterLegacyCustomIssuer() {
        val fixture=activationFixture()
        val tx=activationTransaction(fixture)
        assertTrue(tx.commit("owner-1") is NativeFutureCommitReview.Applied)
        val inputs=futureInputs()
        val signoff=activatedSignoff(fixture)
        val fingerprint=(tx.reviewActivatedCycle("owner-1",inputs)
            as NativeFutureActivatedCycleReview.Ready).reviewFingerprint
        assertTrue(signoff.confirm("owner-1",0,fingerprint,inputs,true)
            is NativeActivatedSignoffWrite.Committed)
        val prepared=activatedPrepared(fixture)
        val name="activated-only-not-issued"
        assertTrue(prepared.prepareActivated("owner-1",name,"cycle-1",4,1,
            inputs,signoff,tx) is NativeCustomPrepareResult.Prepared)
        val preflight=NativeKotlinCustomWorkoutPreflight(
            fence,identity,drafts,sources,store())
        // Test seam deliberately omits the handoff barrier; authority type
        // itself must still prohibit laundering a new approval into v1 issue.
        val oldIssuer=NativeKotlinCustomWorkoutJournalFileStore(
            temp.root.toPath(),fence,identity,prepared,preflight,
            NativeKotlinHistoryManifestFileStore(temp.root.toPath()),
            KotlinNativeJournalFileStore(temp.root.toPath()))
        assertEquals(NativeCustomJournalResult.Rejected(
            NativeCustomJournalRefusal.CONSENT_CHANGED),
            oldIssuer.issue("owner-1",name,"cycle-1",4,1,inputs))
        assertEquals(NativeCustomJournalResult.Rejected(
            NativeCustomJournalRefusal.NOT_ISSUED),
            oldIssuer.open("owner-1",name))
    }

    @Test fun activatedPreparationRejectsRevokedAndStaleSignoffButPreservesSnapshot() {
        val fixture=activationFixture()
        val tx=activationTransaction(fixture)
        assertTrue(tx.commit("owner-1") is NativeFutureCommitReview.Applied)
        val inputs=futureInputs()
        val signoff=activatedSignoff(fixture)
        val ready=tx.reviewActivatedCycle("owner-1",inputs)
            as NativeFutureActivatedCycleReview.Ready
        val accepted=signoff.confirm("owner-1",0,
            ready.reviewFingerprint,inputs,true)
            as NativeActivatedSignoffWrite.Committed
        val prepared=activatedPrepared(fixture)
        val first=prepared.prepareActivated("owner-1","original-activated",
            "cycle-1",4,1,inputs,signoff,tx)
            as NativeCustomPrepareResult.Prepared
        assertEquals(NativeCustomPrepareResult.Rejected(
            NativeCustomPrepareRefusal.REVIEW_UNAVAILABLE),
            prepared.prepareActivated("owner-1","wrong-cycle",
                "different-cycle",4,1,inputs,signoff,tx))
        assertEquals(NativeCustomPrepareResult.Rejected(
            NativeCustomPrepareRefusal.REVIEW_UNAVAILABLE),
            prepared.prepareActivated("owner-1","previous-week",
                "cycle-1",3,0,inputs,signoff,tx))
        assertEquals(NativeActivatedSignoffWrite.Revoked(2),
            signoff.revoke("owner-1",accepted.receipt.recordRevision))
        assertEquals(NativeCustomPrepareResult.Rejected(
            NativeCustomPrepareRefusal.REVIEW_UNAVAILABLE),
            prepared.prepareActivated("owner-1","after-revocation",
                "cycle-1",4,1,inputs,signoff,tx))
        assertEquals(NativeCustomPreparedRead.Found(first.workout),
            activatedPrepared(fixture).open("owner-1","original-activated"))
        assertTrue(fixture.handoff.blocksNewIssuance("owner-1"))
    }

    private fun enrollActivatedHistory(
        ledger:NativeKotlinHistoryManifestFileStore
    ) {
        // Synthetic manifest fixtures are confined to the native issuer test:
        // production stage preflight independently verifies actual journals.
        val entries=(1..9).map{i->KotlinHistoryManifestEntry(
            i.toLong(),"historical-$i","stored-$i","completed-$i",3,0)}
        val result=ledger.enrollVerifiedSnapshot("owner-1","athlete-1",
            entries){o,a->o=="owner-1" && a=="athlete-1" &&
                active==NativeKotlinOwnerIdentity(o,a)}
        assertTrue(result.toString(),result is NativeHistoryLedgerResult.Durable)
    }

    private fun activatedIssuer(
        fixture:ActivationFixture,
        ledger:NativeKotlinHistoryManifestFileStore,
        handoff:NativeKotlinFutureActivationHandoffFileStore?=fixture.handoff
    ):NativeKotlinCustomWorkoutJournalFileStore {
        val preflight=NativeKotlinCustomWorkoutPreflight(
            fence,identity,drafts,sources,store())
        return NativeKotlinCustomWorkoutJournalFileStore(
            temp.root.toPath(),fence,identity,activatedPrepared(fixture),
            preflight,ledger,KotlinNativeJournalFileStore(temp.root.toPath()),
            handoff)
    }

    @Test fun signedActivatedWorkoutIssuesLogsAndReplaysWithDurableHistory() {
        val fixture=activationFixture(historyRevision=1)
        val tx=activationTransaction(fixture)
        assertTrue(tx.commit("owner-1") is NativeFutureCommitReview.Applied)
        val inputs=futureInputs()
        val signoff=activatedSignoff(fixture)
        val fingerprint=(tx.reviewActivatedCycle("owner-1",inputs)
            as NativeFutureActivatedCycleReview.Ready).reviewFingerprint
        assertTrue(signoff.confirm("owner-1",0,fingerprint,inputs,true)
            is NativeActivatedSignoffWrite.Committed)
        val workoutId="future-issued-w4-d1"
        assertTrue(activatedPrepared(fixture).prepareActivated("owner-1",
            workoutId,"cycle-1",4,1,inputs,signoff,tx)
            is NativeCustomPrepareResult.Prepared)
        val ledger=NativeKotlinHistoryManifestFileStore(temp.root.toPath())
        val issuer=activatedIssuer(fixture,ledger)
        assertEquals(NativeCustomJournalResult.Rejected(
            NativeCustomJournalRefusal.HISTORY_NOT_READY),
            issuer.issueActivated("owner-1",workoutId,"cycle-1",
                4,1,inputs,signoff,tx))
        enrollActivatedHistory(ledger)
        val first=issuer.issueActivated("owner-1",workoutId,"cycle-1",
            4,1,inputs,signoff,tx)
        assertTrue(first.toString(),first is NativeCustomJournalResult.Durable)
        assertFalse((first as NativeCustomJournalResult.Durable).replayed)
        assertEquals("build",first.issued.journal.snapshot.blockId)
        assertEquals(first.issued.journal.snapshot,
            (activatedPrepared(fixture).open("owner-1",workoutId)
                as NativeCustomPreparedRead.Found).workout.snapshot)
        assertEquals(NativeCustomJournalResult.Durable(first.issued,true),
            activatedIssuer(fixture,ledger).issueActivated(
                "owner-1",workoutId,"cycle-1",4,1,
                inputs,activatedSignoff(fixture),activationTransaction(fixture)))
        val recorded=issuer.record("owner-1",workoutId,
            CustomWorkoutJournalOperation.Record(
                "future-actual-set",0,
                CustomWorkoutVisitKey("cycle-1:w4:d1:slot-1:s1",
                    CustomWorkoutVisitPart.MAIN),
                KotlinJournalSetOutcome.COMPLETED,
                KotlinPerformedSet(null,null,12,1)))
        assertTrue(recorded.toString(),recorded is NativeCustomJournalResult.Durable)
        assertEquals(recorded.let{(it as NativeCustomJournalResult.Durable).issued},
            (activatedIssuer(fixture,ledger).open("owner-1",workoutId)
                as NativeCustomJournalResult.Durable).issued)
        // Same existing redo-backed Finish transaction owns completion; no
        // special future-block history writer or unindexed Finish shortcut.
        val completions=NativeKotlinCustomHistoryTransaction(
            ledger,issuer,fence,identity)
        val finish=CustomWorkoutJournalOperation.Finish("future-finish",1)
        val completed=completions.complete("owner-1","athlete-1",
            workoutId,finish)
        assertTrue(completed.toString(),
            completed is NativeCustomFinishResult.Committed)
        val receipt=completed as NativeCustomFinishResult.Committed
        assertEquals(12,receipt.evidence.visits.single().actual?.reps)
        assertEquals(10,(ledger.open("owner-1","athlete-1")
            as NativeHistoryLedgerResult.Durable).ledger.completions.size)
        assertEquals(receipt.copy(replayed=true),
            NativeKotlinCustomHistoryTransaction(
                ledger,activatedIssuer(fixture,ledger),fence,identity)
                .complete("owner-1","athlete-1",workoutId,finish))
        assertEquals(NativeCustomJournalResult.Rejected(
            NativeCustomJournalRefusal.WORKOUT_ID_CONFLICT),
            issuer.issue("owner-1",workoutId,"cycle-1",4,1,inputs))
        assertTrue(fixture.handoff.blocksNewIssuance("owner-1"))
    }

    @Test fun activatedWorkoutCannotIssueWithRevokedConsentOrOldPreparedGrant() {
        val fixture=activationFixture(historyRevision=1)
        val tx=activationTransaction(fixture)
        assertTrue(tx.commit("owner-1") is NativeFutureCommitReview.Applied)
        val inputs=futureInputs()
        val signoff=activatedSignoff(fixture)
        val fingerprint=(tx.reviewActivatedCycle("owner-1",inputs)
            as NativeFutureActivatedCycleReview.Ready).reviewFingerprint
        val approved=signoff.confirm("owner-1",0,fingerprint,inputs,true)
            as NativeActivatedSignoffWrite.Committed
        val workoutId="pending-activated-issue"
        assertTrue(activatedPrepared(fixture).prepareActivated("owner-1",
            workoutId,"cycle-1",4,1,inputs,signoff,tx)
            is NativeCustomPrepareResult.Prepared)
        val ledger=NativeKotlinHistoryManifestFileStore(temp.root.toPath())
        enrollActivatedHistory(ledger)
        assertEquals(NativeCustomJournalResult.Rejected(
            NativeCustomJournalRefusal.WORKOUT_ID_CONFLICT),
            activatedIssuer(fixture,ledger).issueActivated(
                "owner-1",workoutId,"cycle-1",4,2,inputs,signoff,tx))
        assertEquals(NativeActivatedSignoffWrite.Revoked(2),
            signoff.revoke("owner-1",approved.receipt.recordRevision))
        assertEquals(NativeCustomJournalResult.Rejected(
            NativeCustomJournalRefusal.CONSENT_CHANGED),
            activatedIssuer(fixture,ledger).issueActivated(
                "owner-1",workoutId,"cycle-1",4,1,inputs,signoff,tx))
        assertEquals(NativeCustomJournalResult.Rejected(
            NativeCustomJournalRefusal.NOT_ISSUED),
            activatedIssuer(fixture,ledger).open("owner-1",workoutId))
    }


    private data class LiveFutureCoachFixture(
        val activation:NativeKotlinFutureActivationCommitTransaction,
        val signoff:NativeKotlinActivatedCycleSignoffFileStore,
        val prepared:NativeKotlinCustomWorkoutPreparedStore,
        val issuer:NativeKotlinCustomWorkoutJournalFileStore,
        val history:NativeKotlinHistoryManifestFileStore,
        val reader:NativeKotlinMixedHistoryReadCoordinator,
        val coach:NativeKotlinCustomNextWorkoutReview,
        val repApprovals:NativeKotlinCustomNextRepApprovalFileStore,
        val assistance:NativeKotlinCoachAssistanceSettingsStore
    )

    private fun liveFutureCoachFixture():LiveFutureCoachFixture {
        // Unlike a fabricated nine-item manifest, each preceding workout is
        // a real issued, logged, redo-Finished, native persisted journal.
        val fixture=activationFixture(historyRevision=1,realPriorHistory=true)
        val tx=activationTransaction(fixture)
        assertTrue(tx.commit("owner-1") is NativeFutureCommitReview.Applied)
        val inputs=futureInputs()
        val signoff=activatedSignoff(fixture)
        val fingerprint=(tx.reviewActivatedCycle("owner-1",inputs)
            as NativeFutureActivatedCycleReview.Ready).reviewFingerprint
        assertTrue(signoff.confirm("owner-1",0,fingerprint,inputs,true)
            is NativeActivatedSignoffWrite.Committed)
        val history=NativeKotlinHistoryManifestFileStore(temp.root.toPath())
        val prepared=activatedPrepared(fixture)
        val issuer=activatedIssuer(fixture,history)
        val composer=KotlinNativeJournalFileStore(temp.root.toPath())
        val authority=object:NativeKotlinWorkoutAuthority {
            override fun currentApprovedWorkout(
                ownerKey:String,workoutId:String
            ):NativeKotlinWorkoutApproval?=null
            override fun canAccessIssuedWorkout(
                ownerKey:String,source:KotlinTrainingSourceRevision
            )=ownerKey=="owner-1" && source.athleteId=="athlete-1"
        }
        val reader=NativeKotlinMixedHistoryReadCoordinator(history,
            composer,KotlinNativeSavedCorrectionFileStore(
                temp.root.toPath(),composer),issuer,authority,identity,fence)
        val verified=reader.read("owner-1","athlete-1")
        assertTrue("Real nine-session history: $verified",
            verified is NativeKotlinMixedHistoryReadResult.Review)
        assertEquals(9,(verified as NativeKotlinMixedHistoryReadResult.Review)
            .timeline.completions.size)
        val assistance=NativeKotlinCoachAssistanceSettingsStore(
            temp.root.toPath(),fence,identity)
        val coach=NativeKotlinCustomNextWorkoutReview(
            fence,identity,tx,signoff,sources,reader,issuer,assistance)
        val reps=NativeKotlinCustomNextRepApprovalFileStore(
            temp.root.toPath(),fence,identity,tx,coach,prepared,issuer)
        return LiveFutureCoachFixture(tx,signoff,prepared,issuer,history,
            reader,coach,reps,assistance)
    }

    private fun finishRealActivatedSession(
        f:LiveFutureCoachFixture,week:Int,reps:Int,
        repApprovals:NativeKotlinCustomNextRepApprovalFileStore?=null
    ):String {
        val workoutId="real-activated-week$week"
        val inputs=futureInputs()
        val prepared=f.prepared.prepareActivated("owner-1",workoutId,
            "cycle-1",week,1,inputs,f.signoff,f.activation,repApprovals)
        assertTrue(prepared.toString(),prepared is NativeCustomPrepareResult.Prepared)
        val issued=f.issuer.issueActivated("owner-1",workoutId,
            "cycle-1",week,1,inputs,f.signoff,f.activation,repApprovals)
        assertTrue(issued.toString(),issued is NativeCustomJournalResult.Durable)
        val recorded=f.issuer.record("owner-1",workoutId,
            CustomWorkoutJournalOperation.Record(
                "real-actual-w$week",0,
                CustomWorkoutVisitKey("cycle-1:w$week:d1:slot-1:s1",
                    CustomWorkoutVisitPart.MAIN),
                KotlinJournalSetOutcome.COMPLETED,
                KotlinPerformedSet(
                    if(weightedFixture)n("20") else null,
                    if(weightedFixture)LoadUnit.LB else null,reps,2)))
        assertTrue(recorded.toString(),recorded is NativeCustomJournalResult.Durable)
        val finish=NativeKotlinCustomHistoryTransaction(
            f.history,f.issuer,fence,identity)
            .complete("owner-1","athlete-1",workoutId,
                CustomWorkoutJournalOperation.Finish(
                    "real-finish-w$week",1))
        assertTrue(finish.toString(),finish is NativeCustomFinishResult.Committed)
        return workoutId
    }

    @Test fun blockEntryCoachingReopensRealPriorHistoryWithoutInventingAStartingLoad() {
        weightedFixture=true
        val f=liveFutureCoachFixture()
        val before=f.reader.read("owner-1","athlete-1")
            as NativeKotlinMixedHistoryReadResult.Review
        val result=f.coach.review("owner-1",futureInputs(),4,0,
            "slot-0",CoachReadiness.NORMAL,false,n("5"))
        assertTrue(result.toString(),result is NativeCustomNextCoachResult.Reviewed)
        val reviewed=result as NativeCustomNextCoachResult.Reviewed
        assertEquals(before.timeline.manifestRevision,reviewed.historyRevision)
        assertEquals(CustomNextWorkoutCoachingStatus.NO_MATCHING_SESSION,
            reviewed.review.status)
        // Activation changes the reviewed athlete/gym source and split.
        // Same exercise + same physical weight alone cannot prove continuity.
        assertEquals(CustomBlockBaselineStatus.ATHLETE_OR_GYM_SOURCE_CHANGED,
            reviewed.priorBlockBaseline?.status)
        assertEquals(CustomBlockStartingLoadStatus.BASELINE_UNAVAILABLE,
            reviewed.blockStartingLoads?.status)
        assertTrue(checkNotNull(reviewed.blockStartingLoads).startingSets.isEmpty())
        assertEquals(reviewed.priorBlockBaseline,reviewed.blockStartingLoads?.baseline)
        assertFalse(reviewed.mayIssueWorkout)
        assertFalse(checkNotNull(reviewed.blockStartingLoads).mayChangePrescription)
        val after=f.reader.read("owner-1","athlete-1")
            as NativeKotlinMixedHistoryReadResult.Review
        assertEquals(before,after)
        assertTrue(f.issuer.open("owner-1","not-issued")
            is NativeCustomJournalResult.Rejected)
        // A fresh reader uses persisted facts, not the previous UI result.
        val reopened=NativeKotlinCustomNextWorkoutReview(
            NativeKotlinApprovalRevisionFence(temp.root.toPath()),identity,
            f.activation,f.signoff,sources,f.reader,f.issuer,f.assistance)
        assertEquals(reviewed,reopened.review("owner-1",futureInputs(),4,0,
            "slot-0",CoachReadiness.NORMAL,false,n("5")))
    }

    private data class AuthoredCoachFixture(
        val coach:NativeKotlinCustomNextWorkoutReview,
        val reader:NativeKotlinMixedHistoryReadCoordinator,
        val issuer:NativeKotlinCustomWorkoutJournalFileStore,
        val history:NativeKotlinHistoryManifestFileStore,
        val corrections:NativeKotlinCustomSavedCorrectionFileStore,
        val prepared:NativeKotlinCustomWorkoutPreparedStore
    )
    private fun authoredCoachFixture(actualRir:Int?=2,
        initializeHistory:Boolean=true,phaseTransition:Boolean=false):AuthoredCoachFixture {
        weightedFixture=true
        phaseTransitionFixture=phaseTransition
        if(initializeHistory) {
            initializeFutureBaseline();approveFutureBaseline()
            persistNineHistoricalBaseWorkouts(actualRir)
        }
        val root=temp.root.toPath()
        val history=NativeKotlinHistoryManifestFileStore(root)
        val preflight=NativeKotlinCustomWorkoutPreflight(
            fence,identity,drafts,sources,store())
        val prepared=NativeKotlinCustomWorkoutPreparedStore(root,fence,identity,preflight)
        val issuer=NativeKotlinCustomWorkoutJournalFileStore(root,fence,identity,
            prepared,
            preflight,history,KotlinNativeJournalFileStore(root))
        val activation=NativeKotlinFutureActivationCommitTransaction(
            root,fence,identity,sources,drafts,futureStore(),
            approvalCheckpoint(),newHandoff())
        val composer=KotlinNativeJournalFileStore(root)
        val authority=object:NativeKotlinWorkoutAuthority {
            override fun currentApprovedWorkout(ownerKey:String,workoutId:String):
                NativeKotlinWorkoutApproval?=null
            override fun canAccessIssuedWorkout(ownerKey:String,source:KotlinTrainingSourceRevision)=
                ownerKey=="owner-1" && source.athleteId=="athlete-1"
        }
        val corrections=NativeKotlinCustomSavedCorrectionFileStore(
            root,fence,identity,issuer,history)
        val reader=NativeKotlinMixedHistoryReadCoordinator(history,composer,
            KotlinNativeSavedCorrectionFileStore(root,composer),issuer,
            authority,identity,fence,corrections)
        return AuthoredCoachFixture(NativeKotlinCustomNextWorkoutReview(
            fence,identity,activation,
            NativeKotlinActivatedCycleSignoffFileStore(root,fence,identity,activation),
            sources,reader,issuer,
            NativeKotlinCoachAssistanceSettingsStore(root,fence,identity),preflight),
            reader,issuer,history,corrections,prepared)
    }

    private fun calibrationStore(f:AuthoredCoachFixture)=NativeKotlinBlockCalibrationConsentStore(
        temp.root.toPath(),fence,identity,f.coach,drafts,sources,permissionStore,f.reader,
        NativeKotlinCustomOutstandingWorkoutInventory(f.prepared,f.issuer),
        KotlinNativeJournalFileStore(temp.root.toPath()),f.prepared)
    private fun calibrationPreview(f:AuthoredCoachFixture,w:String="calibrated-week4")=
        calibrationStore(f).preview("owner-1",w,"cycle-1",futureInputs(),4,0,
            "slot-0",CoachReadiness.NORMAL,false,n("5"))
    private fun calibrationRights() {
        assertTrue(permissionStore.confirm("owner-1","cycle-1",1,
            setOf(CustomCycleChangeKind.LOADS,CustomCycleChangeKind.WEEK_OVERRIDES),true)
            is NativeAdaptivePermissionWrite.Committed)
    }

    @Test fun phaseCalibrationAcceptancePreparationAndIssuanceKeepExactPerSetTargets() {
        val f=authoredCoachFixture(actualRir=null,phaseTransition=true)
        assertEquals(NativeBlockCalibrationPreview.Rejected(NativeCalibrationRefusal.PERMISSION_REQUIRED),
            calibrationPreview(f))
        calibrationRights()
        val offer=calibrationPreview(f) as NativeBlockCalibrationPreview.Ready
        assertEquals(6,offer.auditedWeeks)
        assertEquals(listOf(n("25")),offer.receipt.authoredLoads)
        assertEquals(listOf(n("20")),offer.receipt.proposedLoads)
        val writer=calibrationStore(f)
        assertEquals(NativeBlockCalibrationWrite.Rejected(NativeCalibrationRefusal.CONFIRMATION_REQUIRED),
            writer.accept(offer.receipt,futureInputs(),false))
        assertEquals(NativeBlockCalibrationWrite.Accepted(offer.receipt,false),
            writer.accept(offer.receipt,futureInputs(),true))
        assertEquals(NativeBlockCalibrationWrite.Accepted(offer.receipt,true),
            writer.accept(offer.receipt,futureInputs(),true))
        val cold=authoredCoachFixture(initializeHistory=false,phaseTransition=true)
        val approval=calibrationStore(cold)
        assertEquals(NativeBlockCalibrationRead.Current(offer),approval.readCurrent(
            "owner-1","calibrated-week4","cycle-1",4,0,futureInputs()))
        assertEquals(listOf(offer.receipt),
            (approval.pendingPreparations("owner-1","cycle-1") as NativePendingCalibrationRead.Ready).acceptances)
        // The generic path must not silently substitute the authored 25.
        assertEquals(NativeCustomPrepareResult.Rejected(NativeCustomPrepareRefusal.REVIEW_UNAVAILABLE),
            cold.prepared.prepare("owner-1","calibrated-week4","cycle-1",4,0,futureInputs()))
        val prepared=cold.prepared.prepare("owner-1","calibrated-week4","cycle-1",4,0,futureInputs(),approval)
            as NativeCustomPrepareResult.Prepared
        assertEquals(offer.projected,prepared.workout.snapshot)
        assertEquals(offer.receipt.fingerprint,prepared.workout.calibrationApprovalFingerprint)
        assertEquals(prepared.workout,(cold.prepared.open("owner-1","calibrated-week4")
            as NativeCustomPreparedRead.Found).workout)
        assertEquals(listOf(prepared.workout),
            (approval.pendingPreparations("owner-1","cycle-1") as NativePendingCalibrationRead.Ready).workouts)
        assertEquals(NativeCustomJournalResult.Rejected(NativeCustomJournalRefusal.CONSENT_CHANGED),
            cold.issuer.issue("owner-1","calibrated-week4","cycle-1",4,0,futureInputs()))
        val issued=cold.issuer.issue("owner-1","calibrated-week4","cycle-1",4,0,futureInputs(),approval)
            as NativeCustomJournalResult.Durable
        assertEquals(offer.projected,issued.issued.journal.snapshot)
        assertEquals(NativePreparedManualResetPreview.Rejected(NativePreparedManualResetRefusal.ALREADY_ISSUED),
            cold.prepared.previewManualReset("owner-1","calibrated-week4","cycle-1",4,0,futureInputs(),
                null,null,cold.issuer,NativeKotlinCoachAssistanceSettingsStore(temp.root.toPath(),fence,identity)))
        assertTrue((approval.pendingPreparations("owner-1","cycle-1")
            as NativePendingCalibrationRead.Ready).workouts.isEmpty())
        assertEquals(issued.issued,cold.issuer.issue("owner-1","calibrated-week4","cycle-1",4,0,
            futureInputs(),approval).let{(it as NativeCustomJournalResult.Durable).issued})
        assertEquals(n("25"),((drafts.read("owner-1","cycle-1") as NativeCustomDraftReadResult.Found)
            .draft.blocks.last().days.first().slots.single().sets.single().load as DraftSetLoad.Verified).value)
        val recorded=cold.issuer.record("owner-1","calibrated-week4",CustomWorkoutJournalOperation.Record(
            "calibrated-observation",0,CustomWorkoutVisitKey("cycle-1:w4:d0:slot-0:s1",
                CustomWorkoutVisitPart.MAIN),KotlinJournalSetOutcome.COMPLETED,
            KotlinPerformedSet(n("20"),LoadUnit.LB,5,null)))
        assertTrue(recorded.toString(),recorded is NativeCustomJournalResult.Durable)
        val finish=NativeKotlinCustomHistoryTransaction(cold.history,cold.issuer,fence,identity)
            .complete("owner-1","athlete-1","calibrated-week4",
                CustomWorkoutJournalOperation.Finish("calibrated-finish",1))
        assertTrue(finish.toString(),finish is NativeCustomFinishResult.Committed)
        val history=cold.reader.read("owner-1","athlete-1") as NativeKotlinMixedHistoryReadResult.Review
        val fact=(history.timeline.completions.last() as NativeKotlinMixedHistoryCompletion.Custom).evidence.visits.single()
        assertEquals(n("20"),(fact.prescribedLoad as DraftSetLoad.Verified).value)
        assertNull(fact.actual!!.actualRir)
    }

    @Test fun acceptedLowerBlockEntryWeightsRepeatThroughLaterWeeksOnlyWithFreshApproval() {
        val initial=authoredCoachFixture(actualRir=null,phaseTransition=true)
        calibrationRights()
        val first=calibrationPreview(initial,"carry-week4")
            as NativeBlockCalibrationPreview.Ready
        val firstConsent=calibrationStore(initial)
        assertEquals(listOf(n("20")),first.receipt.proposedLoads)
        assertTrue(firstConsent.accept(first.receipt,futureInputs(),true)
            is NativeBlockCalibrationWrite.Accepted)
        assertTrue(initial.prepared.prepare("owner-1","carry-week4","cycle-1",4,0,
            futureInputs(),firstConsent) is NativeCustomPrepareResult.Prepared)
        assertTrue(initial.issuer.issue("owner-1","carry-week4","cycle-1",4,0,
            futureInputs(),firstConsent) is NativeCustomJournalResult.Durable)
        assertTrue(initial.issuer.record("owner-1","carry-week4",
            CustomWorkoutJournalOperation.Record("carry-set-w4",0,
                CustomWorkoutVisitKey("cycle-1:w4:d0:slot-0:s1",CustomWorkoutVisitPart.MAIN),
                KotlinJournalSetOutcome.COMPLETED,
                KotlinPerformedSet(n("20"),LoadUnit.LB,5,null)))
            is NativeCustomJournalResult.Durable)
        assertTrue(NativeKotlinCustomHistoryTransaction(initial.history,initial.issuer,
            fence,identity).complete("owner-1","athlete-1","carry-week4",
                CustomWorkoutJournalOperation.Finish("carry-finish-w4",1))
            is NativeCustomFinishResult.Committed)

        val cold=authoredCoachFixture(initializeHistory=false,phaseTransition=true)
        val coached=cold.coach.reviewAuthored("owner-1","cycle-1",futureInputs(),
            5,0,"slot-0",CoachReadiness.NORMAL,false,n("5"))
            as NativeCustomNextCoachResult.Reviewed
        val suggested=checkNotNull(coached.sameBlockLoadContinuity)
        assertEquals(CustomSameBlockLoadStatus.REVIEW_LOWER_REPEAT,suggested.status)
        assertEquals("carry-week4",suggested.previousWorkoutId)
        assertEquals(listOf(n("25")),suggested.sets.map{it.authoredLoad})
        assertEquals(listOf(n("20")),suggested.sets.map{it.proposedLoad})
        assertEquals(1,suggested.assumedGoalRirSetCount)
        assertNull(suggested.sets.single().previousActualRir)
        assertFalse(suggested.mayIssueWorkout)
        assertEquals(CustomAuthoredLoadReversionStatus.CONFIRM_HIGHER_AUTHORED_LOAD,
            coached.authoredLoadReversion?.status)
        assertEquals(NativeCustomPrepareResult.Rejected(
            NativeCustomPrepareRefusal.AUTHORED_LOAD_REVERSION_REVIEW_REQUIRED),
            cold.prepared.prepare("owner-1","unaccepted-higher-week5","cycle-1",5,0,
                futureInputs(),calibrationStore(cold)))
        assertEquals(NativeCustomPreparedRead.Missing,
            cold.prepared.open("owner-1","unaccepted-higher-week5"))

        val secondStore=calibrationStore(cold)
        val second=secondStore.preview("owner-1","carry-week5","cycle-1",
            futureInputs(),5,0,"slot-0",CoachReadiness.NORMAL,false,n("5"))
            as NativeBlockCalibrationPreview.Ready
        assertEquals(6,second.auditedWeeks)
        assertEquals(listOf(n("20")),second.receipt.proposedLoads)
        assertEquals(listOf(n("25")),second.receipt.authoredLoads)
        assertEquals(NativeBlockCalibrationWrite.Rejected(
            NativeCalibrationRefusal.CONFIRMATION_REQUIRED),
            secondStore.accept(second.receipt,futureInputs(),false))
        assertTrue(secondStore.accept(second.receipt,futureInputs(),true)
            is NativeBlockCalibrationWrite.Accepted)
        val restarted=authoredCoachFixture(initializeHistory=false,phaseTransition=true)
        val again=calibrationStore(restarted)
        assertEquals(NativeBlockCalibrationRead.Current(second),
            again.readCurrent("owner-1","carry-week5","cycle-1",5,0,futureInputs()))
        assertEquals(NativeCustomPrepareResult.Rejected(
            NativeCustomPrepareRefusal.REVIEW_UNAVAILABLE),
            restarted.prepared.prepare("owner-1","carry-week5","cycle-1",5,0,
                futureInputs()))
        val prepared=restarted.prepared.prepare("owner-1","carry-week5","cycle-1",
            5,0,futureInputs(),again) as NativeCustomPrepareResult.Prepared
        assertEquals(second.projected,prepared.workout.snapshot)
        val issued=restarted.issuer.issue("owner-1","carry-week5","cycle-1",5,0,
            futureInputs(),again) as NativeCustomJournalResult.Durable
        assertEquals(second.projected,issued.issued.journal.snapshot)
        assertTrue(restarted.issuer.record("owner-1","carry-week5",
            CustomWorkoutJournalOperation.Record("carry-set-w5",0,
                CustomWorkoutVisitKey("cycle-1:w5:d0:slot-0:s1",CustomWorkoutVisitPart.MAIN),
                KotlinJournalSetOutcome.COMPLETED,
                KotlinPerformedSet(n("20"),LoadUnit.LB,5,null)))
            is NativeCustomJournalResult.Durable)
        assertTrue(NativeKotlinCustomHistoryTransaction(restarted.history,restarted.issuer,
            fence,identity).complete("owner-1","athlete-1","carry-week5",
                CustomWorkoutJournalOperation.Finish("carry-finish-w5",1))
            is NativeCustomFinishResult.Committed)
        val third=authoredCoachFixture(initializeHistory=false,phaseTransition=true)
            .coach.reviewAuthored("owner-1","cycle-1",futureInputs(),6,0,
                "slot-0",CoachReadiness.NORMAL,false,n("5"))
            as NativeCustomNextCoachResult.Reviewed
        assertEquals(CustomSameBlockLoadStatus.REVIEW_LOWER_REPEAT,
            third.sameBlockLoadContinuity?.status)
        assertEquals("carry-week5",third.sameBlockLoadContinuity?.previousWorkoutId)
        assertEquals(listOf(n("20")),third.sameBlockLoadContinuity?.sets?.map{it.proposedLoad})
        assertEquals(n("25"),((drafts.read("owner-1","cycle-1")
            as NativeCustomDraftReadResult.Found).draft.blocks.last()
            .days.first().slots.single().sets.single().load as DraftSetLoad.Verified).value)

        // Accept and prepare the sixth-week repeat, then correct the *latest*
        // completed fifth-week record. The stale receipt must not issue or
        // silently fall back to the higher authored target.
        val lastStore=calibrationStore(restarted)
        val lastOffer=lastStore.preview("owner-1","carry-week6","cycle-1",
            futureInputs(),6,0,"slot-0",CoachReadiness.NORMAL,false,n("5"))
            as NativeBlockCalibrationPreview.Ready
        assertEquals(listOf(n("20")),lastOffer.receipt.proposedLoads)
        assertTrue(lastStore.accept(lastOffer.receipt,futureInputs(),true)
            is NativeBlockCalibrationWrite.Accepted)
        val staged=restarted.prepared.prepare("owner-1","carry-week6","cycle-1",
            6,0,futureInputs(),lastStore) as NativeCustomPrepareResult.Prepared
        assertEquals(lastOffer.projected,staged.workout.snapshot)

        val correction=NativeKotlinCustomHistoryCorrectionTransaction(
            restarted.history,restarted.issuer,restarted.corrections,fence,identity)
            .correct("owner-1","athlete-1","carry-week5",
                CustomSavedVisitCorrection("carry-correct-near-failure",0,
                    CustomWorkoutVisitKey("cycle-1:w5:d0:slot-0:s1",
                        CustomWorkoutVisitPart.MAIN),"carry-set-w5",
                    KotlinJournalSetOutcome.COMPLETED,
                    KotlinPerformedSet(n("20"),LoadUnit.LB,5,0)))
        assertTrue(correction.toString(),
            correction is NativeCustomCorrectionTransactionResult.Committed)
        val after=authoredCoachFixture(initializeHistory=false,phaseTransition=true)
        val current=after.coach.reviewAuthored("owner-1","cycle-1",futureInputs(),
            6,0,"slot-0",CoachReadiness.NORMAL,false,n("5"))
            as NativeCustomNextCoachResult.Reviewed
        assertEquals(CustomSameBlockLoadStatus.NEAR_FAILURE,
            current.sameBlockLoadContinuity?.status)
        assertNull(current.sameBlockLoadContinuity?.proposedWorkout)
        assertEquals(CustomAuthoredLoadReversionStatus.CONFIRM_HIGHER_AUTHORED_LOAD,
            current.authoredLoadReversion?.status)
        assertEquals(NativeCustomPrepareResult.Rejected(
            NativeCustomPrepareRefusal.AUTHORED_LOAD_REVERSION_REVIEW_REQUIRED),
            after.prepared.prepare("owner-1","unaccepted-higher-after-correction",
                "cycle-1",6,0,futureInputs(),calibrationStore(after)))
        val coldConsent=calibrationStore(after)
        assertTrue(coldConsent.readCurrent("owner-1","carry-week6","cycle-1",
            6,0,futureInputs()) is NativeBlockCalibrationRead.Rejected)
        assertEquals(NativeCustomJournalResult.Rejected(
            NativeCustomJournalRefusal.CONSENT_CHANGED),
            after.issuer.issue("owner-1","carry-week6","cycle-1",6,0,
                futureInputs(),coldConsent))
        assertEquals(NativeCustomPreparedRead.Found(staged.workout),
            after.prepared.open("owner-1","carry-week6"))
        assertEquals(NativeCustomJournalResult.Rejected(
            NativeCustomJournalRefusal.NOT_ISSUED),
            after.issuer.open("owner-1","carry-week6"))

        // Simulate an old caller that prepared the original heavier weight
        // without injecting the guard. Even this cold-recoverable authored
        // snapshot cannot become ISSUED through the guarded native path.
        val oldPrepared=after.prepared.prepare("owner-1","old-unguarded-week6",
            "cycle-1",6,0,futureInputs()) as NativeCustomPrepareResult.Prepared
        assertEquals(n("25"),((oldPrepared.workout.snapshot.slots.single()
            .sets.single().prescription.load) as DraftSetLoad.Verified).value)
        assertEquals(NativeCustomJournalResult.Rejected(
            NativeCustomJournalRefusal.CONSENT_CHANGED),
            after.issuer.issue("owner-1","old-unguarded-week6","cycle-1",
                6,0,futureInputs(),coldConsent))
        assertEquals(NativeCustomJournalResult.Rejected(
            NativeCustomJournalRefusal.NOT_ISSUED),
            after.issuer.open("owner-1","old-unguarded-week6"))
    }

    private fun finishedLowerWeekFour(nearFailure:Boolean=false,reps:Int=5,
        authoredWeight:Int=25):AuthoredCoachFixture {
        phaseAuthoredWeight=authoredWeight
        val f=authoredCoachFixture(actualRir=null,phaseTransition=true)
        calibrationRights()
        val approval=calibrationStore(f)
        val offer=calibrationPreview(f,"lower-week4") as NativeBlockCalibrationPreview.Ready
        assertTrue(approval.accept(offer.receipt,futureInputs(),true) is NativeBlockCalibrationWrite.Accepted)
        assertTrue(f.prepared.prepare("owner-1","lower-week4","cycle-1",4,0,futureInputs(),approval)
            is NativeCustomPrepareResult.Prepared)
        assertTrue(f.issuer.issue("owner-1","lower-week4","cycle-1",4,0,futureInputs(),approval)
            is NativeCustomJournalResult.Durable)
        assertTrue(f.issuer.record("owner-1","lower-week4",CustomWorkoutJournalOperation.Record(
            "lower-set",0,CustomWorkoutVisitKey("cycle-1:w4:d0:slot-0:s1",CustomWorkoutVisitPart.MAIN),
            KotlinJournalSetOutcome.COMPLETED,KotlinPerformedSet(n("20"),LoadUnit.LB,reps,
                if(nearFailure)0 else null))) is NativeCustomJournalResult.Durable)
        assertTrue(NativeKotlinCustomHistoryTransaction(f.history,f.issuer,fence,identity)
            .complete("owner-1","athlete-1","lower-week4",CustomWorkoutJournalOperation.Finish(
                "lower-finish",1)) is NativeCustomFinishResult.Committed)
        return f
    }
    private fun authoredReturn(f:AuthoredCoachFixture,w:String="return-week5")=
        calibrationStore(f).previewAuthoredReturn("owner-1",w,"cycle-1",futureInputs(),5,0)

    private fun finishedTwoReducedWorkouts(authoredWeight:Int=25):AuthoredCoachFixture {
        val f=finishedLowerWeekFour(reps=8,authoredWeight=authoredWeight)
        val approval=calibrationStore(f)
        val repeat=approval.preview("owner-1","lower-week5","cycle-1",futureInputs(),5,0,
            "slot-0",CoachReadiness.NORMAL,false,n("5")) as NativeBlockCalibrationPreview.Ready
        assertEquals(NativeBlockLoadConsentKind.CONSERVATIVE_REDUCTION,repeat.receipt.kind)
        assertTrue(approval.accept(repeat.receipt,futureInputs(),true) is NativeBlockCalibrationWrite.Accepted)
        assertTrue(f.prepared.prepare("owner-1","lower-week5","cycle-1",5,0,futureInputs(),approval)
            is NativeCustomPrepareResult.Prepared)
        assertTrue(f.issuer.issue("owner-1","lower-week5","cycle-1",5,0,futureInputs(),approval)
            is NativeCustomJournalResult.Durable)
        assertTrue(f.issuer.record("owner-1","lower-week5",CustomWorkoutJournalOperation.Record(
            "second-lower-set",0,CustomWorkoutVisitKey("cycle-1:w5:d0:slot-0:s1",CustomWorkoutVisitPart.MAIN),
            KotlinJournalSetOutcome.COMPLETED,KotlinPerformedSet(n("20"),LoadUnit.LB,8,null)))
            is NativeCustomJournalResult.Durable)
        assertTrue(NativeKotlinCustomHistoryTransaction(f.history,f.issuer,fence,identity)
            .complete("owner-1","athlete-1","lower-week5",CustomWorkoutJournalOperation.Finish(
                "second-lower-finish",1)) is NativeCustomFinishResult.Committed)
        return f
    }
    private fun earnedBaselinePreview(f:AuthoredCoachFixture,w:String="earned-week6",
        readiness:CoachReadiness=CoachReadiness.NORMAL,restricted:Boolean=false,limit:BigDecimal=n("5"))=
        calibrationStore(f).preview("owner-1",w,"cycle-1",futureInputs(),6,0,"slot-0",
            readiness,restricted,limit,NativeBlockLoadConsentKind.EARNED_BASELINE_INCREASE)
    private fun correctSecondReducedWorkout(f:AuthoredCoachFixture) {
        val result=NativeKotlinCustomHistoryCorrectionTransaction(f.history,f.issuer,f.corrections,fence,identity)
            .correct("owner-1","athlete-1","lower-week5",CustomSavedVisitCorrection(
                "invalidate-earned",0,CustomWorkoutVisitKey("cycle-1:w5:d0:slot-0:s1",CustomWorkoutVisitPart.MAIN),
                "second-lower-set",KotlinJournalSetOutcome.COMPLETED,KotlinPerformedSet(n("20"),LoadUnit.LB,8,0)))
        assertTrue(result.toString(),result is NativeCustomCorrectionTransactionResult.Committed)
    }

    @Test fun twoReducedCeilingWorkoutsEarnASeparatelyAcceptedStepToTheAuthoredLoad() {
        val f=finishedTwoReducedWorkouts()
        val coached=f.coach.reviewAuthored("owner-1","cycle-1",futureInputs(),6,0,"slot-0",
            CoachReadiness.NORMAL,false,n("5")) as NativeCustomNextCoachResult.Reviewed
        assertEquals(CustomNextWorkoutCoachingStatus.CHANGED_PRESCRIPTION,coached.review.status)
        assertEquals(CustomReducedBaselineProgressionStatus.READY_FOR_REVIEW,
            coached.reducedBaselineProgression?.status)
        val approval=calibrationStore(f)
        val earned=earnedBaselinePreview(f) as NativeBlockCalibrationPreview.Ready
        assertEquals(NativeBlockLoadConsentKind.EARNED_BASELINE_INCREASE,earned.receipt.kind)
        assertEquals(listOf("lower-week4","lower-week5"),earned.receipt.comparedWorkoutIds)
        assertEquals(listOf(n("20")),earned.receipt.baselineLoads)
        assertEquals(listOf(n("25")),earned.receipt.proposedLoads)
        assertEquals(2,earned.receipt.assumedGoalRirSetCount)
        assertEquals(earned.original,earned.projected)
        assertEquals(NativeBlockCalibrationWrite.Rejected(NativeCalibrationRefusal.CONFIRMATION_REQUIRED),
            approval.accept(earned.receipt,futureInputs(),false))
        assertEquals(NativeBlockCalibrationWrite.Rejected(NativeCalibrationRefusal.REVIEW_CHANGED),
            approval.accept(earned.receipt.copy(comparedWorkoutIds=listOf("fake-one","fake-two")),futureInputs(),true))
        assertTrue(approval.accept(earned.receipt,futureInputs(),true) is NativeBlockCalibrationWrite.Accepted)
        val cold=authoredCoachFixture(initializeHistory=false,phaseTransition=true)
        val reopened=calibrationStore(cold)
        assertEquals(NativeBlockCalibrationRead.Current(earned),
            reopened.readCurrent("owner-1","earned-week6","cycle-1",6,0,futureInputs()))
        assertEquals(listOf(earned.receipt),(reopened.pendingPreparations("owner-1","cycle-1")
            as NativePendingCalibrationRead.Ready).acceptances)
        assertEquals(NativeCustomPrepareResult.Rejected(NativeCustomPrepareRefusal.REVIEW_UNAVAILABLE),
            cold.prepared.prepare("owner-1","earned-week6","cycle-1",6,0,futureInputs()))
        val prepared=cold.prepared.prepare("owner-1","earned-week6","cycle-1",6,0,
            futureInputs(),reopened,earned.receipt.fingerprint) as NativeCustomPrepareResult.Prepared
        assertEquals(earned.receipt.fingerprint,prepared.workout.calibrationApprovalFingerprint)
        assertNull(prepared.workout.authoredReturnFingerprint);assertFalse(prepared.workout.resetToAuthored)
        assertEquals(NativeCustomJournalResult.Rejected(NativeCustomJournalRefusal.CONSENT_CHANGED),
            cold.issuer.issue("owner-1","earned-week6","cycle-1",6,0,futureInputs()))
        val issued=cold.issuer.issue("owner-1","earned-week6","cycle-1",6,0,futureInputs(),reopened)
            as NativeCustomJournalResult.Durable
        assertEquals(earned.projected,issued.issued.journal.snapshot)
        assertTrue(cold.issuer.record("owner-1","earned-week6",CustomWorkoutJournalOperation.Record(
            "earned-set",0,CustomWorkoutVisitKey("cycle-1:w6:d0:slot-0:s1",CustomWorkoutVisitPart.MAIN),
            KotlinJournalSetOutcome.COMPLETED,KotlinPerformedSet(n("25"),LoadUnit.LB,5,null)))
            is NativeCustomJournalResult.Durable)
        assertTrue(NativeKotlinCustomHistoryTransaction(cold.history,cold.issuer,fence,identity)
            .complete("owner-1","athlete-1","earned-week6",CustomWorkoutJournalOperation.Finish(
                "earned-finish",1)) is NativeCustomFinishResult.Committed)
        val facts=(cold.reader.read("owner-1","athlete-1") as NativeKotlinMixedHistoryReadResult.Review)
            .timeline.completions.filterIsInstance<NativeKotlinMixedHistoryCompletion.Custom>()
        assertEquals(listOf(n("20"),n("20"),n("25")),facts.takeLast(3).map{
            (it.evidence.visits.single().prescribedLoad as DraftSetLoad.Verified).value})
        assertTrue(facts.takeLast(3).all{it.evidence.visits.single().actual!!.actualRir==null})
        correctSecondReducedWorkout(cold)
        assertTrue(cold.issuer.issue("owner-1","earned-week6","cycle-1",6,0,futureInputs(),reopened)
            is NativeCustomJournalResult.Durable)
        assertEquals(2L,(drafts.read("owner-1","cycle-1") as NativeCustomDraftReadResult.Found).draft.revision)
    }

    @Test fun earnedReturnCanStayBelowAuthoredLoadAndKeepsTheConservativeRepeatSeparate() {
        val f=finishedTwoReducedWorkouts(authoredWeight=30)
        val earned=earnedBaselinePreview(f) as NativeBlockCalibrationPreview.Ready
        assertEquals(listOf(n("30")),earned.receipt.authoredLoads)
        assertEquals(listOf(n("20")),earned.receipt.baselineLoads)
        assertEquals(listOf(n("25")),earned.receipt.proposedLoads)
        val approval=calibrationStore(f)
        val repeat=approval.preview("owner-1","repeat-instead","cycle-1",futureInputs(),6,0,"slot-0",
            CoachReadiness.NORMAL,false,n("5")) as NativeBlockCalibrationPreview.Ready
        assertEquals(NativeBlockLoadConsentKind.CONSERVATIVE_REDUCTION,repeat.receipt.kind)
        assertEquals(listOf(n("20")),repeat.receipt.proposedLoads)
        assertTrue(repeat.receipt.baselineLoads.isEmpty())
        assertNotEquals(repeat.receipt.fingerprint,earned.receipt.fingerprint)
        assertEquals(NativeBlockCalibrationWrite.Rejected(NativeCalibrationRefusal.REVIEW_CHANGED),
            approval.accept(repeat.receipt.copy(kind=NativeBlockLoadConsentKind.EARNED_BASELINE_INCREASE),futureInputs(),true))
        assertTrue(approval.accept(earned.receipt,futureInputs(),true) is NativeBlockCalibrationWrite.Accepted)
        assertTrue(f.prepared.prepare("owner-1","earned-week6","cycle-1",6,0,futureInputs(),approval)
            is NativeCustomPrepareResult.Prepared)
        val issued=f.issuer.issue("owner-1","earned-week6","cycle-1",6,0,futureInputs(),approval)
            as NativeCustomJournalResult.Durable
        assertEquals(n("25"),(issued.issued.journal.snapshot.slots.single().sets.single()
            .prescription.load as DraftSetLoad.Verified).value)
        assertEquals(n("30"),((drafts.read("owner-1","cycle-1") as NativeCustomDraftReadResult.Found)
            .draft.blocks.last().days.first().slots.single().sets.single().load as DraftSetLoad.Verified).value)
    }

    @Test fun lateCorrectionRevokesEarnedConsentBeforePreparationAndBeforeFirstIssuance() {
        val f=finishedTwoReducedWorkouts()
        val shown=earnedBaselinePreview(f) as NativeBlockCalibrationPreview.Ready
        val approval=calibrationStore(f)
        assertTrue(approval.accept(shown.receipt,futureInputs(),true) is NativeBlockCalibrationWrite.Accepted)
        val saved=f.prepared.prepare("owner-1","earned-week6","cycle-1",6,0,futureInputs(),approval)
            as NativeCustomPrepareResult.Prepared
        correctSecondReducedWorkout(f)
        val cold=authoredCoachFixture(initializeHistory=false,phaseTransition=true)
        val reopened=calibrationStore(cold)
        assertTrue(reopened.readCurrent("owner-1","earned-week6","cycle-1",6,0,futureInputs())
            is NativeBlockCalibrationRead.Rejected)
        assertEquals(NativeBlockCalibrationWrite.Accepted(shown.receipt,true),
            reopened.accept(shown.receipt,futureInputs(),true))
        assertEquals(NativeCustomPrepareResult.Rejected(NativeCustomPrepareRefusal.REVIEW_UNAVAILABLE),
            cold.prepared.prepare("owner-1","earned-week6","cycle-1",6,0,futureInputs(),reopened))
        assertEquals(NativeCustomJournalResult.Rejected(NativeCustomJournalRefusal.CONSENT_CHANGED),
            cold.issuer.issue("owner-1","earned-week6","cycle-1",6,0,futureInputs(),reopened))
        assertEquals(NativeCustomPreparedRead.Found(saved.workout),cold.prepared.open("owner-1","earned-week6"))
        assertEquals(NativeCustomJournalResult.Rejected(NativeCustomJournalRefusal.NOT_ISSUED),
            cold.issuer.open("owner-1","earned-week6"))
        assertEquals(NativeBlockCalibrationWrite.Rejected(NativeCalibrationRefusal.REVIEW_NOT_READY),
            reopened.accept(shown.receipt.copy(workoutId="new-after-correction"),futureInputs(),true))
    }

    @Test fun earnedReturnRespectsPermissionStepReadinessAndWrongOwnerRefusals() {
        val f=finishedTwoReducedWorkouts()
        assertTrue(earnedBaselinePreview(f,readiness=CoachReadiness.LOW) is NativeBlockCalibrationPreview.Rejected)
        assertTrue(earnedBaselinePreview(f,restricted=true) is NativeBlockCalibrationPreview.Rejected)
        assertTrue(earnedBaselinePreview(f,limit=n("4")) is NativeBlockCalibrationPreview.Rejected)
        val shown=earnedBaselinePreview(f) as NativeBlockCalibrationPreview.Ready
        val approval=calibrationStore(f)
        assertTrue(approval.accept(shown.receipt,futureInputs(),true) is NativeBlockCalibrationWrite.Accepted)
        assertTrue(f.prepared.prepare("owner-1","earned-week6","cycle-1",6,0,futureInputs(),approval)
            is NativeCustomPrepareResult.Prepared)
        assertTrue(permissionStore.confirm("owner-1","cycle-1",2,emptySet(),true)
            is NativeAdaptivePermissionWrite.Committed)
        assertEquals(NativeCustomJournalResult.Rejected(NativeCustomJournalRefusal.CONSENT_CHANGED),
            f.issuer.issue("owner-1","earned-week6","cycle-1",6,0,futureInputs(),approval))
        active=NativeKotlinOwnerIdentity("other-owner","other-athlete")
        assertEquals(NativeBlockCalibrationWrite.Rejected(NativeCalibrationRefusal.NOT_AUTHORIZED),
            approval.accept(shown.receipt,futureInputs(),true))
    }

    @Test fun earnedReceiptRejectsAnUnknownKindEvenWithARecomputedChecksum() {
        val f=finishedTwoReducedWorkouts()
        val shown=earnedBaselinePreview(f) as NativeBlockCalibrationPreview.Ready
        val approval=calibrationStore(f)
        approval.accept(shown.receipt,futureInputs(),true)
        f.prepared.prepare("owner-1","earned-week6","cycle-1",6,0,futureInputs(),approval)
        val file=Files.list(temp.root.toPath()).use{stream->stream.filter{
            it.fileName.toString().startsWith("block-calibration-")&&it.fileName.toString().endsWith(".bin")&&
                String(Files.readAllBytes(it),Charsets.ISO_8859_1).contains("earned-week6")
        }.findFirst().get()}
        val raw=Files.readAllBytes(file)
        val body=raw.copyOfRange(4,raw.size-32)
        assertEquals(2,java.nio.ByteBuffer.wrap(body).getInt(4))
        val marker=String(body,Charsets.ISO_8859_1).indexOf(NativeBlockLoadConsentKind.EARNED_BASELINE_INCREASE.name)
        assertTrue(marker>0);body[marker]='X'.code.toByte()
        val tampered=java.nio.ByteBuffer.allocate(raw.size).putInt(body.size).put(body)
            .put(java.security.MessageDigest.getInstance("SHA-256").digest(body)).array()
        Files.write(file,tampered)
        val cold=authoredCoachFixture(initializeHistory=false,phaseTransition=true)
        val reopened=calibrationStore(cold)
        assertEquals(NativeBlockCalibrationRead.Rejected(NativeCalibrationRefusal.CORRUPT_RECORD),
            reopened.readCurrent("owner-1","earned-week6","cycle-1",6,0,futureInputs()))
        assertEquals(NativeBlockCalibrationWrite.Rejected(NativeCalibrationRefusal.CORRUPT_RECORD),
            reopened.accept(shown.receipt,futureInputs(),true))
        assertEquals(NativeCustomJournalResult.Rejected(NativeCustomJournalRefusal.CONSENT_CHANGED),
            cold.issuer.issue("owner-1","earned-week6","cycle-1",6,0,futureInputs(),reopened))
        assertArrayEquals(tampered,Files.readAllBytes(file))
    }

    @Test fun coachingOffCanExplicitlyResetAnEarnedStepThatExactlyMatchesAuthoredTargets() {
        val f=finishedTwoReducedWorkouts()
        val shown=earnedBaselinePreview(f) as NativeBlockCalibrationPreview.Ready
        val approval=calibrationStore(f)
        approval.accept(shown.receipt,futureInputs(),true)
        val saved=f.prepared.prepare("owner-1","earned-week6","cycle-1",6,0,futureInputs(),approval)
            as NativeCustomPrepareResult.Prepared
        val settings=NativeKotlinCoachAssistanceSettingsStore(temp.root.toPath(),fence,identity)
        assertTrue(settings.save("owner-1",0,CoachAssistancePolicy.MANUAL) is NativeCoachSettingsWrite.Saved)
        assertEquals(NativeCustomJournalResult.Rejected(NativeCustomJournalRefusal.CONSENT_CHANGED),
            f.issuer.issue("owner-1","earned-week6","cycle-1",6,0,futureInputs(),approval))
        val reset=f.prepared.previewManualReset("owner-1","earned-week6","cycle-1",6,0,futureInputs(),
            null,null,f.issuer,settings) as NativePreparedManualResetPreview.Ready
        assertEquals(saved.workout.snapshot,reset.authored)
        val result=f.prepared.acceptManualReset("owner-1","earned-week6","cycle-1",6,0,futureInputs(),
            null,null,f.issuer,settings,reset.preparedFingerprint,reset.settingsRevision,true)
            as NativePreparedManualResetWrite.Reset
        assertTrue(result.prepared.resetToAuthored);assertNull(result.prepared.calibrationApprovalFingerprint)
        assertTrue(settings.save("owner-1",1,CoachAssistancePolicy.STANDARD) is NativeCoachSettingsWrite.Saved)
        assertEquals(NativeCustomPrepareResult.Prepared(result.prepared,true),f.prepared.prepare(
            "owner-1","earned-week6","cycle-1",6,0,futureInputs(),approval))
        assertTrue(f.issuer.issue("owner-1","earned-week6","cycle-1",6,0,futureInputs(),approval)
            is NativeCustomJournalResult.Durable)
    }
    private fun correctLowerWeekFour(f:AuthoredCoachFixture,event:String="lower-edit",revision:Long=0,
        prior:String="lower-set") {
        val corrected=NativeKotlinCustomHistoryCorrectionTransaction(f.history,f.issuer,f.corrections,fence,identity)
            .correct("owner-1","athlete-1","lower-week4",CustomSavedVisitCorrection(
                event,revision,CustomWorkoutVisitKey("cycle-1:w4:d0:slot-0:s1",CustomWorkoutVisitPart.MAIN),
                prior,KotlinJournalSetOutcome.COMPLETED,KotlinPerformedSet(n("20"),LoadUnit.LB,5,0)))
        assertTrue(corrected.toString(),corrected is NativeCustomCorrectionTransactionResult.Committed)
    }

    @Test fun heavierAuthoredReturnRequiresExactConfirmationAndColdRevalidationBeforeIssue() {
        val f=finishedLowerWeekFour()
        val historyBefore=f.reader.read("owner-1","athlete-1")
        val shown=authoredReturn(f) as NativeAuthoredLoadReturnPreview.Ready
        assertEquals(6,shown.auditedWeeks)
        assertEquals("lower-week4",shown.higherSlots.single().previousWorkoutId)
        assertEquals(n("20"),shown.higherSlots.single().higherSets.single().lastIssuedLoad)
        assertEquals(n("25"),shown.higherSlots.single().higherSets.single().authoredLoad)
        assertFalse(shown.isEarnedProgression);assertFalse(shown.mayIssueWorkout)
        assertEquals(historyBefore,f.reader.read("owner-1","athlete-1"))
        assertEquals(NativeCustomPreparedRead.Missing,f.prepared.open("owner-1","return-week5"))
        assertEquals(NativeCustomPrepareResult.Rejected(NativeCustomPrepareRefusal.CONFIRMATION_REQUIRED),
            f.prepared.prepareAuthoredReturn(shown,futureInputs(),false,calibrationStore(f)))
        assertEquals(NativeCustomPrepareResult.Rejected(NativeCustomPrepareRefusal.REVIEW_CHANGED),
            f.prepared.prepareAuthoredReturn(shown.copy(higherSlots=emptyList()),futureInputs(),true,calibrationStore(f)))
        val accepted=f.prepared.prepareAuthoredReturn(shown,futureInputs(),true,calibrationStore(f))
            as NativeCustomPrepareResult.Prepared
        assertEquals(shown.authored,accepted.workout.snapshot)
        assertEquals(shown.fingerprint,accepted.workout.authoredReturnFingerprint)
        assertFalse(accepted.workout.resetToAuthored);assertNull(accepted.workout.calibrationApprovalFingerprint)
        val cold=authoredCoachFixture(initializeHistory=false,phaseTransition=true)
        assertEquals(NativeCustomPreparedRead.Found(accepted.workout),cold.prepared.open("owner-1","return-week5"))
        assertEquals(listOf(accepted.workout),(calibrationStore(cold).pendingPreparations("owner-1","cycle-1")
            as NativePendingCalibrationRead.Ready).workouts)
        assertEquals(NativeCustomPrepareResult.Prepared(accepted.workout,true),
            cold.prepared.prepare("owner-1","return-week5","cycle-1",5,0,futureInputs(),calibrationStore(cold)))
        assertEquals(NativeCustomJournalResult.Rejected(NativeCustomJournalRefusal.CONSENT_CHANGED),
            cold.issuer.issue("owner-1","return-week5","cycle-1",5,0,futureInputs()))
        val issued=cold.issuer.issue("owner-1","return-week5","cycle-1",5,0,futureInputs(),calibrationStore(cold))
            as NativeCustomJournalResult.Durable
        assertEquals(shown.authored,issued.issued.journal.snapshot)
        assertEquals(NativePreparedManualResetPreview.Rejected(NativePreparedManualResetRefusal.ALREADY_ISSUED),
            cold.prepared.previewManualReset("owner-1","return-week5","cycle-1",5,0,futureInputs(),
                null,null,cold.issuer,NativeKotlinCoachAssistanceSettingsStore(temp.root.toPath(),fence,identity)))
        assertTrue(cold.issuer.record("owner-1","return-week5",CustomWorkoutJournalOperation.Record(
            "return-set",0,CustomWorkoutVisitKey("cycle-1:w5:d0:slot-0:s1",CustomWorkoutVisitPart.MAIN),
            KotlinJournalSetOutcome.COMPLETED,KotlinPerformedSet(n("25"),LoadUnit.LB,5,null)))
            is NativeCustomJournalResult.Durable)
        assertTrue(NativeKotlinCustomHistoryTransaction(cold.history,cold.issuer,fence,identity)
            .complete("owner-1","athlete-1","return-week5",CustomWorkoutJournalOperation.Finish(
                "return-finish",1)) is NativeCustomFinishResult.Committed)
        assertTrue(NativeKotlinCoachAssistanceSettingsStore(temp.root.toPath(),fence,identity)
            .save("owner-1",0,CoachAssistancePolicy.MANUAL) is NativeCoachSettingsWrite.Saved)
        assertTrue(cold.issuer.issue("owner-1","return-week5","cycle-1",5,0,futureInputs())
            is NativeCustomJournalResult.Durable)
        val facts=(cold.reader.read("owner-1","athlete-1") as NativeKotlinMixedHistoryReadResult.Review)
            .timeline.completions.filterIsInstance<NativeKotlinMixedHistoryCompletion.Custom>()
        assertEquals(n("20"),(facts[facts.lastIndex-1].evidence.visits.single().prescribedLoad as DraftSetLoad.Verified).value)
        assertEquals(n("25"),(facts.last().evidence.visits.single().prescribedLoad as DraftSetLoad.Verified).value)
        assertEquals(2L,(drafts.read("owner-1","cycle-1") as NativeCustomDraftReadResult.Found).draft.revision)
    }

    @Test fun correctedHistoryRequiresFreshAuthoredReturnConsentAtBothWriteBoundaries() {
        val f=finishedLowerWeekFour()
        val old=authoredReturn(f) as NativeAuthoredLoadReturnPreview.Ready
        correctLowerWeekFour(f)
        assertEquals(NativeCustomPrepareResult.Rejected(NativeCustomPrepareRefusal.REVIEW_CHANGED),
            f.prepared.prepareAuthoredReturn(old,futureInputs(),true,calibrationStore(f)))
        assertEquals(NativeCustomPreparedRead.Missing,f.prepared.open("owner-1","return-week5"))
        val fresh=authoredReturn(f) as NativeAuthoredLoadReturnPreview.Ready
        assertNotEquals(old.fingerprint,fresh.fingerprint)
        val saved=f.prepared.prepareAuthoredReturn(fresh,futureInputs(),true,calibrationStore(f))
            as NativeCustomPrepareResult.Prepared
        correctLowerWeekFour(f,"lower-edit-again",1,"lower-edit")
        val cold=authoredCoachFixture(initializeHistory=false,phaseTransition=true)
        assertEquals(NativeCustomJournalResult.Rejected(NativeCustomJournalRefusal.CONSENT_CHANGED),
            cold.issuer.issue("owner-1","return-week5","cycle-1",5,0,futureInputs(),calibrationStore(cold)))
        assertEquals(NativeCustomPrepareResult.Rejected(NativeCustomPrepareRefusal.REVIEW_CHANGED),
            cold.prepared.prepare("owner-1","return-week5","cycle-1",5,0,futureInputs(),calibrationStore(cold)))
        assertEquals(NativeCustomPreparedRead.Found(saved.workout),cold.prepared.open("owner-1","return-week5"))
        val rechecked=authoredReturn(cold) as NativeAuthoredLoadReturnPreview.Ready
        assertNotEquals(fresh.fingerprint,rechecked.fingerprint)
        val renewed=cold.prepared.prepareAuthoredReturn(rechecked,futureInputs(),true,calibrationStore(cold))
            as NativeCustomPrepareResult.Prepared
        assertEquals(saved.workout.snapshot,renewed.workout.snapshot)
        assertTrue(cold.issuer.issue("owner-1","return-week5","cycle-1",5,0,futureInputs(),calibrationStore(cold))
            is NativeCustomJournalResult.Durable)
    }

    @Test fun nearFailureReturnIsAnExplicitAuthoredChoiceAndCanAddConsentToOldUntouchedPreparation() {
        val f=finishedLowerWeekFour(nearFailure=true)
        val old=f.prepared.prepare("owner-1","return-week5","cycle-1",5,0,futureInputs())
            as NativeCustomPrepareResult.Prepared
        assertNull(old.workout.authoredReturnFingerprint)
        assertEquals(NativeCustomJournalResult.Rejected(NativeCustomJournalRefusal.CONSENT_CHANGED),
            f.issuer.issue("owner-1","return-week5","cycle-1",5,0,futureInputs(),calibrationStore(f)))
        val shown=authoredReturn(f) as NativeAuthoredLoadReturnPreview.Ready
        val accepted=f.prepared.prepareAuthoredReturn(shown,futureInputs(),true,calibrationStore(f))
            as NativeCustomPrepareResult.Prepared
        assertEquals(old.workout,accepted.workout.copy(authoredReturnFingerprint=null))
        assertFalse(shown.isEarnedProgression)
        assertEquals(NativeCustomPrepareResult.Prepared(accepted.workout,true),
            f.prepared.prepareAuthoredReturn(shown,futureInputs(),true,calibrationStore(f)))
    }

    @Test fun authoredReturnSettingsAndSourceChangesNeverDiscardStoredConsentProof() {
        val f=finishedLowerWeekFour()
        val shown=authoredReturn(f) as NativeAuthoredLoadReturnPreview.Ready
        val saved=f.prepared.prepareAuthoredReturn(shown,futureInputs(),true,calibrationStore(f))
            as NativeCustomPrepareResult.Prepared
        val settings=NativeKotlinCoachAssistanceSettingsStore(temp.root.toPath(),fence,identity)
        assertTrue(settings.save("owner-1",0,CoachAssistancePolicy.MANUAL) is NativeCoachSettingsWrite.Saved)
        assertEquals(NativeCustomJournalResult.Rejected(NativeCustomJournalRefusal.CONSENT_CHANGED),
            f.issuer.issue("owner-1","return-week5","cycle-1",5,0,futureInputs(),calibrationStore(f)))
        assertEquals(NativeCustomPrepareResult.Rejected(NativeCustomPrepareRefusal.REVIEW_CHANGED),
            f.prepared.prepare("owner-1","return-week5","cycle-1",5,0,futureInputs(),calibrationStore(f)))
        assertEquals(NativeCustomPreparedRead.Found(saved.workout),f.prepared.open("owner-1","return-week5"))
        assertTrue(settings.save("owner-1",1,CoachAssistancePolicy.STANDARD) is NativeCoachSettingsWrite.Saved)
        assertEquals(NativeCustomJournalResult.Rejected(NativeCustomJournalRefusal.CONSENT_CHANGED),
            f.issuer.issue("owner-1","return-week5","cycle-1",5,0,futureInputs(),calibrationStore(f)))
        assertTrue(sources.publish("owner-1",1,source(2)) is NativeAuthoringSourceWrite.Committed)
        assertEquals(NativeCustomJournalResult.Rejected(NativeCustomJournalRefusal.CONSENT_CHANGED),
            f.issuer.issue("owner-1","return-week5","cycle-1",5,0,futureInputs(),calibrationStore(f)))
        assertEquals(NativeCustomPreparedRead.Found(saved.workout),f.prepared.open("owner-1","return-week5"))
    }

    @Test fun coachingOffCanExplicitlyRestoreAuthoredReturnAndNeverReviveTheClearedProof() {
        val f=finishedLowerWeekFour()
        val shown=authoredReturn(f) as NativeAuthoredLoadReturnPreview.Ready
        val saved=f.prepared.prepareAuthoredReturn(shown,futureInputs(),true,calibrationStore(f))
            as NativeCustomPrepareResult.Prepared
        val settings=NativeKotlinCoachAssistanceSettingsStore(temp.root.toPath(),fence,identity)
        assertTrue(settings.save("owner-1",0,CoachAssistancePolicy.MANUAL) is NativeCoachSettingsWrite.Saved)
        val reset=f.prepared.previewManualReset("owner-1","return-week5","cycle-1",5,0,
            futureInputs(),null,null,f.issuer,settings) as NativePreparedManualResetPreview.Ready
        assertEquals(saved.workout.snapshot,reset.authored)
        assertEquals(NativePreparedManualResetWrite.Rejected(NativePreparedManualResetRefusal.CONFIRMATION_REQUIRED),
            f.prepared.acceptManualReset("owner-1","return-week5","cycle-1",5,0,futureInputs(),null,null,
                f.issuer,settings,reset.preparedFingerprint,reset.settingsRevision,false))
        val restored=f.prepared.acceptManualReset("owner-1","return-week5","cycle-1",5,0,
            futureInputs(),null,null,f.issuer,settings,reset.preparedFingerprint,reset.settingsRevision,true)
            as NativePreparedManualResetWrite.Reset
        assertEquals(saved.workout.snapshot,restored.prepared.snapshot)
        assertTrue(restored.prepared.resetToAuthored);assertNull(restored.prepared.authoredReturnFingerprint)
        assertTrue(settings.save("owner-1",1,CoachAssistancePolicy.STANDARD) is NativeCoachSettingsWrite.Saved)
        assertEquals(NativeCustomPrepareResult.Prepared(restored.prepared,true),
            f.prepared.prepare("owner-1","return-week5","cycle-1",5,0,futureInputs(),calibrationStore(f)))
        assertTrue(f.prepared.prepareAuthoredReturn(shown,futureInputs(),true,calibrationStore(f))
            is NativeCustomPrepareResult.Rejected)
        assertTrue(f.issuer.issue("owner-1","return-week5","cycle-1",5,0,futureInputs(),calibrationStore(f))
            is NativeCustomJournalResult.Durable)
    }

    @Test fun corruptAuthoredReturnPreparationRefusesIssuanceWithoutCreatingAReplacement() {
        val f=finishedLowerWeekFour()
        val shown=authoredReturn(f) as NativeAuthoredLoadReturnPreview.Ready
        assertTrue(f.prepared.prepareAuthoredReturn(shown,futureInputs(),true,calibrationStore(f))
            is NativeCustomPrepareResult.Prepared)
        val file=Files.list(temp.root.toPath()).use{stream->stream.filter{
            if(!it.fileName.toString().startsWith("prepared-custom-")||!it.fileName.toString().endsWith(".bin"))false
            else String(Files.readAllBytes(it),Charsets.ISO_8859_1).contains("return-week5")
        }.findFirst().get()}
        val bytes=Files.readAllBytes(file);bytes[bytes.lastIndex]=(bytes.last().toInt() xor 255).toByte()
        Files.write(file,bytes)
        val cold=authoredCoachFixture(initializeHistory=false,phaseTransition=true)
        assertEquals(NativeCustomPreparedRead.Rejected(NativeCustomPrepareRefusal.CORRUPT_RECORD),
            cold.prepared.open("owner-1","return-week5"))
        assertEquals(NativeCustomJournalResult.Rejected(NativeCustomJournalRefusal.NOT_PREPARED),
            cold.issuer.issue("owner-1","return-week5","cycle-1",5,0,futureInputs(),calibrationStore(cold)))
        assertTrue(cold.prepared.prepareAuthoredReturn(shown,futureInputs(),true,calibrationStore(cold))
            is NativeCustomPrepareResult.Rejected)
        assertArrayEquals(bytes,Files.readAllBytes(file))
        assertEquals(NativeCustomJournalResult.Rejected(NativeCustomJournalRefusal.NOT_ISSUED),
            cold.issuer.open("owner-1","return-week5"))
    }

    @Test fun authoredReturnRejectsOtherDayClaimsAndWrongOwners() {
        val f=finishedLowerWeekFour()
        val shown=authoredReturn(f) as NativeAuthoredLoadReturnPreview.Ready
        assertTrue(f.prepared.prepare("owner-1","other-week5","cycle-1",5,0,futureInputs())
            is NativeCustomPrepareResult.Prepared)
        assertEquals(NativeAuthoredLoadReturnPreview.Rejected(NativeCalibrationRefusal.ALREADY_PREPARED_OR_ISSUED),
            authoredReturn(f))
        assertEquals(NativeCustomPrepareResult.Rejected(NativeCustomPrepareRefusal.REVIEW_CHANGED),
            f.prepared.prepareAuthoredReturn(shown,futureInputs(),true,calibrationStore(f)))
        active=NativeKotlinOwnerIdentity("other-owner","other-athlete")
        assertEquals(NativeCustomPrepareResult.Rejected(NativeCustomPrepareRefusal.NOT_AUTHORIZED),
            f.prepared.prepareAuthoredReturn(shown,futureInputs(),true,calibrationStore(f)))
        assertEquals(NativeAuthoredLoadReturnPreview.Rejected(NativeCalibrationRefusal.NOT_AUTHORIZED),
            authoredReturn(f))
    }

    @Test fun changedHistoryBeforeIssueRefusesCalibrationWithoutChangingPreparedTargets() {
        val f=authoredCoachFixture(phaseTransition=true);calibrationRights()
        val offer=calibrationPreview(f) as NativeBlockCalibrationPreview.Ready
        val approval=calibrationStore(f)
        assertTrue(approval.accept(offer.receipt,futureInputs(),true) is NativeBlockCalibrationWrite.Accepted)
        val prepared=f.prepared.prepare("owner-1","calibrated-week4","cycle-1",4,0,futureInputs(),approval)
            as NativeCustomPrepareResult.Prepared
        val corrected=NativeKotlinCustomHistoryCorrectionTransaction(f.history,f.issuer,f.corrections,fence,identity)
            .correct("owner-1","athlete-1","real-base-w3-d0",CustomSavedVisitCorrection(
                "calibration-stale",0,CustomWorkoutVisitKey("cycle-1:w3:d0:slot-0:s1",CustomWorkoutVisitPart.MAIN),
                "observed-w3-d0",KotlinJournalSetOutcome.COMPLETED,KotlinPerformedSet(n("20"),LoadUnit.LB,12,1)))
        assertTrue(corrected is NativeCustomCorrectionTransactionResult.Committed)
        assertTrue(approval.readCurrent("owner-1","calibrated-week4","cycle-1",4,0,futureInputs())
            is NativeBlockCalibrationRead.Rejected)
        assertEquals(NativeBlockCalibrationWrite.Accepted(offer.receipt,true),
            approval.accept(offer.receipt,futureInputs(),true))
        assertEquals(NativeCustomJournalResult.Rejected(NativeCustomJournalRefusal.CONSENT_CHANGED),
            f.issuer.issue("owner-1","calibrated-week4","cycle-1",4,0,futureInputs(),approval))
        assertEquals(NativeCustomPreparedRead.Found(prepared.workout),f.prepared.open("owner-1","calibrated-week4"))
    }

    @Test fun coachingOffRequiresConfirmedAuthoredResetAndNeverRewritesAnIssuedWorkout() {
        val f=authoredCoachFixture(phaseTransition=true);calibrationRights()
        val approval=calibrationStore(f)
        val offer=calibrationPreview(f) as NativeBlockCalibrationPreview.Ready
        approval.accept(offer.receipt,futureInputs(),true)
        f.prepared.prepare("owner-1","calibrated-week4","cycle-1",4,0,futureInputs(),approval)
        val settings=NativeKotlinCoachAssistanceSettingsStore(temp.root.toPath(),fence,identity)
        assertTrue(settings.save("owner-1",0,CoachAssistancePolicy.MANUAL) is NativeCoachSettingsWrite.Saved)
        assertEquals(NativeCustomJournalResult.Rejected(NativeCustomJournalRefusal.CONSENT_CHANGED),
            f.issuer.issue("owner-1","calibrated-week4","cycle-1",4,0,futureInputs(),approval))
        val reset=f.prepared.previewManualReset("owner-1","calibrated-week4","cycle-1",4,0,
            futureInputs(),null,null,f.issuer,settings) as NativePreparedManualResetPreview.Ready
        assertEquals(n("25"),(reset.authored.slots.single().sets.single().prescription.load as DraftSetLoad.Verified).value)
        assertEquals(NativePreparedManualResetWrite.Rejected(NativePreparedManualResetRefusal.CONFIRMATION_REQUIRED),
            f.prepared.acceptManualReset("owner-1","calibrated-week4","cycle-1",4,0,futureInputs(),null,null,
                f.issuer,settings,reset.preparedFingerprint,reset.settingsRevision,false))
        val saved=f.prepared.acceptManualReset("owner-1","calibrated-week4","cycle-1",4,0,futureInputs(),null,null,
            f.issuer,settings,reset.preparedFingerprint,reset.settingsRevision,true) as NativePreparedManualResetWrite.Reset
        assertTrue(saved.prepared.resetToAuthored)
        assertNull(saved.prepared.calibrationApprovalFingerprint)
        assertEquals(saved.prepared,(f.prepared.open("owner-1","calibrated-week4") as NativeCustomPreparedRead.Found).workout)
        assertTrue(settings.save("owner-1",1,CoachAssistancePolicy.STANDARD) is NativeCoachSettingsWrite.Saved)
        val issued=f.issuer.issue("owner-1","calibrated-week4","cycle-1",4,0,futureInputs())
            as NativeCustomJournalResult.Durable
        assertEquals(reset.authored,issued.issued.journal.snapshot)
        assertEquals(NativePreparedManualResetPreview.Rejected(NativePreparedManualResetRefusal.NO_AMENDED_PREPARATION),
            f.prepared.previewManualReset("owner-1","calibrated-week4","cycle-1",4,0,
                futureInputs(),null,null,f.issuer,settings))
    }

    @Test fun calibrationRejectsForgedDisplayedWeightsAndAlreadyPreparedDays() {
        val f=authoredCoachFixture(phaseTransition=true);calibrationRights()
        val approval=calibrationStore(f)
        val offer=calibrationPreview(f) as NativeBlockCalibrationPreview.Ready
        assertEquals(NativeBlockCalibrationWrite.Rejected(NativeCalibrationRefusal.REVIEW_CHANGED),
            approval.accept(offer.receipt.copy(proposedLoads=listOf(n("25"))),futureInputs(),true))
        assertEquals(NativeBlockCalibrationWrite.Rejected(NativeCalibrationRefusal.REVIEW_CHANGED),
            approval.accept(offer.receipt.copy(workoutId="forged-identity"),futureInputs(),true))
        assertTrue(f.prepared.prepare("owner-1","authored-already-owned","cycle-1",4,0,futureInputs())
            is NativeCustomPrepareResult.Prepared)
        assertEquals(NativeBlockCalibrationPreview.Rejected(NativeCalibrationRefusal.ALREADY_PREPARED_OR_ISSUED),
            calibrationPreview(f))
    }

    @Test fun changedPermissionsBeforePreparationCannotFallBackToAuthoredTargets() {
        val f=authoredCoachFixture(phaseTransition=true);calibrationRights()
        val offer=calibrationPreview(f) as NativeBlockCalibrationPreview.Ready
        val approval=calibrationStore(f)
        approval.accept(offer.receipt,futureInputs(),true)
        assertTrue(permissionStore.confirm("owner-1","cycle-1",2,setOf(CustomCycleChangeKind.CUES),true)
            is NativeAdaptivePermissionWrite.Committed)
        assertTrue(approval.readCurrent("owner-1","calibrated-week4","cycle-1",4,0,futureInputs())
            is NativeBlockCalibrationRead.Rejected)
        assertEquals(NativeCustomPrepareResult.Rejected(NativeCustomPrepareRefusal.REVIEW_UNAVAILABLE),
            f.prepared.prepare("owner-1","calibrated-week4","cycle-1",4,0,futureInputs(),approval))
        assertEquals(NativeCustomPreparedRead.Missing,f.prepared.open("owner-1","calibrated-week4"))
    }

    @Test fun corruptCalibrationConsentRefusesReadsAndPreparation() {
        val f=authoredCoachFixture(phaseTransition=true);calibrationRights()
        val approval=calibrationStore(f)
        val offer=calibrationPreview(f) as NativeBlockCalibrationPreview.Ready
        approval.accept(offer.receipt,futureInputs(),true)
        val file=Files.list(temp.root.toPath()).use{stream->stream.filter{
            it.fileName.toString().startsWith("block-calibration-")&&it.fileName.toString().endsWith(".bin")
        }.findFirst().get()}
        val bytes=Files.readAllBytes(file);bytes[bytes.lastIndex]=(bytes.last().toInt() xor 255).toByte()
        Files.write(file,bytes)
        assertEquals(NativeBlockCalibrationRead.Rejected(NativeCalibrationRefusal.CORRUPT_RECORD),
            approval.readCurrent("owner-1","calibrated-week4","cycle-1",4,0,futureInputs()))
        assertEquals(NativeBlockCalibrationPreview.Rejected(NativeCalibrationRefusal.CORRUPT_RECORD),calibrationPreview(f))
        assertEquals(NativeCustomPrepareResult.Rejected(NativeCustomPrepareRefusal.REVIEW_UNAVAILABLE),
            f.prepared.prepare("owner-1","calibrated-week4","cycle-1",4,0,futureInputs(),approval))
    }

    @Test fun missingAcceptedCalibrationCannotProduceAnAuthoredFallback() {
        val f=authoredCoachFixture(phaseTransition=true);calibrationRights()
        val approval=calibrationStore(f)
        val offer=calibrationPreview(f) as NativeBlockCalibrationPreview.Ready
        approval.accept(offer.receipt,futureInputs(),true)
        val file=Files.list(temp.root.toPath()).use{stream->stream.filter{
            it.fileName.toString().startsWith("block-calibration-")&&it.fileName.toString().endsWith(".bin")
        }.findFirst().get()}
        Files.delete(file)
        assertEquals(NativeCustomPrepareResult.Rejected(NativeCustomPrepareRefusal.REVIEW_UNAVAILABLE),
            f.prepared.prepare("owner-1","calibrated-week4","cycle-1",4,0,futureInputs(),approval,
                offer.receipt.fingerprint))
        assertEquals(NativeCustomPreparedRead.Missing,f.prepared.open("owner-1","calibrated-week4"))
    }

    @Test fun calibrationNeverReplacesAnAuthoredWeekException() {
        val f=authoredCoachFixture(phaseTransition=true);calibrationRights()
        val original=(drafts.read("owner-1","cycle-1") as NativeCustomDraftReadResult.Found).draft
        val next=original.copy(revision=3,weekOverrides=mapOf("build" to listOf(
            CustomWeekSlotOverride(1,0,"slot-0",original.blocks.last().days.first().slots.single().sets))))
        assertTrue(drafts.save("owner-1","cycle-1",2,next) is NativeCustomDraftWriteResult.Committed)
        assertTrue(store().confirm("owner-1","cycle-1",3,1,1,futureInputs(),true)
            is NativeCustomSignoffWrite.Committed)
        assertEquals(NativeBlockCalibrationPreview.Rejected(NativeCalibrationRefusal.PROTECTED_WEEK),calibrationPreview(f))
    }

    /**
     * The new confirmed future-week draft flow must be safe against real
     * durable journals, not only hand-built in-memory inventory facts.
     */
    private fun futureWeekProposalReader(f:AuthoredCoachFixture):
        NativeKotlinReviewedFutureWeekProposal {
        val preflight=NativeKotlinCustomWorkoutPreflight(
            fence,identity,drafts,sources,store())
        val proposal=NativeKotlinReviewedFutureWeekProposal(
            fence,identity,drafts,sources,store(),permissionStore,
            NativeKotlinCoachAssistanceSettingsStore(temp.root.toPath(),
                fence,identity),
            preflight,f.reader,f.issuer,f.coach,
            NativeKotlinCustomOutstandingWorkoutInventory(f.prepared,f.issuer))
        return proposal
    }
    private fun futureWeekDraftWriter(f:AuthoredCoachFixture)=
        NativeKotlinFutureWeekDraftAcceptance(
            fence,futureWeekProposalReader(f),drafts)

    private fun enableFutureWeekDraftCoaching() {
        val rights=(permissionStore.read("owner-1","cycle-1")
            as NativeAdaptivePermissionRead.Found).receipt
        assertEquals(NativeAdaptivePermissionWrite.Committed(
            rights.revision+1),permissionStore.confirm(
                "owner-1","cycle-1",rights.revision,
                rights.allowedKinds+setOf(CustomCycleChangeKind.WEEK_OVERRIDES,CustomCycleChangeKind.LOADS),true))
        assertTrue(coachSettings.save("owner-1",0,CoachAssistancePolicy.STANDARD)
            is NativeCoachSettingsWrite.Saved)
    }

    private fun futureWeekDraftSelection()=NativeFutureWeekDraftSelection(
        "owner-1","cycle-1",4,0,"slot-0",
        CoachReadiness.NORMAL,false,n("5"))

    /** Real signed cycle, real weekly issued visits, real completion ledger. */
    private fun completeEarnedWeek(f:AuthoredCoachFixture,week:Int,
        reps:Int=10,rir:Int=3) {
        val id="earned-build-week$week"
        assertTrue(f.prepared.prepare("owner-1",id,"cycle-1",week,0,
            futureInputs()) is NativeCustomPrepareResult.Prepared)
        assertTrue(f.issuer.issue("owner-1",id,"cycle-1",week,0,
            futureInputs()) is NativeCustomJournalResult.Durable)
        val result=f.issuer.record("owner-1",id,
            CustomWorkoutJournalOperation.Record("earned-record-$week",0,
                CustomWorkoutVisitKey("cycle-1:w$week:d0:slot-0:s1",
                    CustomWorkoutVisitPart.MAIN),
                KotlinJournalSetOutcome.COMPLETED,
                KotlinPerformedSet(if(weightedFixture)n("20") else null,
                    if(weightedFixture)LoadUnit.LB else null,reps,rir)))
        assertTrue(result.toString(),result is NativeCustomJournalResult.Durable)
        assertTrue(NativeKotlinCustomHistoryTransaction(
            f.history,f.issuer,fence,identity).complete("owner-1","athlete-1",
                id,CustomWorkoutJournalOperation.Finish("earned-finish-$week",1))
            is NativeCustomFinishResult.Committed)
    }

    @Test fun realFinishedWeekCanSaveExactlyOneDraftThenRequireFreshCycleSignoff() {
        val f=authoredCoachFixture()
        completeEarnedWeek(f,4)
        enableFutureWeekDraftCoaching()
        val selected=futureWeekDraftSelection().copy(absoluteWeek=5)
        val initialCycle=(drafts.read("owner-1","cycle-1")
            as NativeCustomDraftReadResult.Found).draft
        val originalConsent=(store().read("owner-1","cycle-1",futureInputs())
            as NativeCustomSignoffRead.Found)
        assertTrue(originalConsent.isCurrent)
        val originalHistory=f.reader.read("owner-1","athlete-1")
        val underlying=futureWeekProposalReader(f).review(
            selected.ownerKey,selected.cycleId,selected.absoluteWeek,
            selected.weekday,selected.slotId,selected.readiness,
            selected.movementRestricted,selected.maximumAuthorizedIncrease,
            selected.forecast)
        val offer=futureWeekDraftWriter(f).preview(selected)
        assertTrue("Owner proposal=$underlying; draft preview=$offer",
            offer is NativeFutureWeekDraftPreview.Ready)
        val shown=offer as NativeFutureWeekDraftPreview.Ready
        assertEquals(NativeFutureWeekDraftDecision.Rejected(
            NativeFutureWeekDraftRefusal.CONFIRMATION_REQUIRED),
            futureWeekDraftWriter(f).accept(selected,shown.fingerprint,false))
        assertEquals(initialCycle,(drafts.read("owner-1","cycle-1")
            as NativeCustomDraftReadResult.Found).draft)
        // Restart from the same durable directory, with no in-memory
        // candidate or cached history injected into the writer.
        val cold=authoredCoachFixture(initializeHistory=false)
        assertEquals(offer,futureWeekDraftWriter(cold).preview(selected))
        assertEquals(NativeFutureWeekDraftDecision.Saved(3,5),
            futureWeekDraftWriter(cold).accept(selected,shown.fingerprint,true))
        val saved=(drafts.read("owner-1","cycle-1")
            as NativeCustomDraftReadResult.Found).draft
        assertEquals(3L,saved.revision)
        assertEquals(initialCycle.blocks,saved.blocks)
        val week=saved.weekOverrides["build"].orEmpty()
        assertEquals(1,week.size)
        assertEquals(2,week.single().weekInBlock)
        assertEquals(0,week.single().weekday)
        assertEquals("slot-0",week.single().slotId)
        assertEquals(11,week.single().sets.single().reps.minimum)
        val originalLoad=initialCycle.blocks.last().days.single {
            it.weekday==0
        }.slots.single().sets.single().load
        assertEquals(originalLoad,week.single().sets.single().load)
        assertEquals(originalHistory,cold.reader.read("owner-1","athlete-1"))
        assertEquals(NativeCustomSavedCoachReview.Rejected(
            NativeCustomSignoffRefusal.STALE_PROGRAM),
            store().recoverReviewedInputs("owner-1","cycle-1"))
        assertEquals(NativeCustomPrepareResult.Rejected(
            NativeCustomPrepareRefusal.REVIEW_UNAVAILABLE),
            cold.prepared.prepare("owner-1","revised-week5","cycle-1",5,0,
                futureInputs()))
        assertEquals(NativeFutureWeekDraftPreview.Rejected(
            NativeFutureWeekDraftRefusal.SOURCE_OR_HISTORY_CHANGED),
            futureWeekDraftWriter(cold).preview(selected))
        assertEquals(NativeFutureWeekDraftDecision.Rejected(
            NativeFutureWeekDraftRefusal.SOURCE_OR_HISTORY_CHANGED),
            futureWeekDraftWriter(cold).accept(selected,shown.fingerprint,true))
        // The old consent never approves the changed cycle. Explicitly
        // re-review, then create a separate prepared (not issued) session.
        val renewed=store().confirm("owner-1","cycle-1",3,1,
            originalConsent.receipt.recordRevision,futureInputs(),true)
        assertTrue(renewed.toString(),renewed is NativeCustomSignoffWrite.Committed)
        val prepared=cold.prepared.prepare("owner-1","revised-week5",
            "cycle-1",5,0,futureInputs())
        assertTrue(prepared.toString(),prepared is NativeCustomPrepareResult.Prepared)
        val workout=(prepared as NativeCustomPrepareResult.Prepared).workout
        assertEquals(11,
            workout.snapshot.slots.single().sets.single().prescription.reps.minimum)
        assertEquals(originalHistory,cold.reader.read("owner-1","athlete-1"))
    }

    @Test fun futureWeekDraftRefusesPreparedWorkoutAcrossColdRestart() {
        val f=authoredCoachFixture()
        enableFutureWeekDraftCoaching()
        val before=(drafts.read("owner-1","cycle-1")
            as NativeCustomDraftReadResult.Found).draft
        assertTrue(f.prepared.prepare("owner-1","future-draft-pending",
            "cycle-1",4,0,futureInputs()) is NativeCustomPrepareResult.Prepared)
        val first=futureWeekDraftWriter(f).preview(futureWeekDraftSelection())
        assertEquals(NativeFutureWeekDraftPreview.Rejected(
            NativeFutureWeekDraftRefusal.PREPARED_OR_ISSUED_WORKOUT),first)
        val cold=authoredCoachFixture(initializeHistory=false)
        val after=futureWeekDraftWriter(cold)
        assertEquals(first,after.preview(futureWeekDraftSelection()))
        assertEquals(NativeFutureWeekDraftDecision.Rejected(
            NativeFutureWeekDraftRefusal.CONFIRMATION_REQUIRED),
            after.accept(futureWeekDraftSelection(),"0".repeat(64),false))
        assertEquals(NativeFutureWeekDraftDecision.Rejected(
            NativeFutureWeekDraftRefusal.PREPARED_OR_ISSUED_WORKOUT),
            after.accept(futureWeekDraftSelection(),"0".repeat(64),true))
        assertEquals(before,(drafts.read("owner-1","cycle-1")
            as NativeCustomDraftReadResult.Found).draft)
        assertTrue(cold.prepared.open("owner-1","future-draft-pending")
            is NativeCustomPreparedRead.Found)
    }

    @Test fun futureWeekDraftCannotCrossOwnerOrBypassSignedBaseline() {
        val f=authoredCoachFixture()
        enableFutureWeekDraftCoaching()
        val writer=futureWeekDraftWriter(f)
        val before=(drafts.read("owner-1","cycle-1")
            as NativeCustomDraftReadResult.Found).draft
        active=NativeKotlinOwnerIdentity("another-owner","another-athlete")
        assertEquals(NativeFutureWeekDraftPreview.Rejected(
            NativeFutureWeekDraftRefusal.SOURCE_OR_HISTORY_CHANGED),
            writer.preview(futureWeekDraftSelection()))
        assertEquals(NativeFutureWeekDraftDecision.Rejected(
            NativeFutureWeekDraftRefusal.SOURCE_OR_HISTORY_CHANGED),
            writer.accept(futureWeekDraftSelection(),"0".repeat(64),true))
        active=NativeKotlinOwnerIdentity("owner-1","athlete-1")
        assertEquals(before,(drafts.read("owner-1","cycle-1")
            as NativeCustomDraftReadResult.Found).draft)
        // A signed cycle is mandatory: a new unsigned revision cannot
        // acquire a coaching write grant from the previous consent.
        val newer=before.copy(revision=before.revision+1,
            authorNote="Unsigned owner edit")
        assertTrue(drafts.save("owner-1","cycle-1",before.revision,newer)
            is NativeCustomDraftWriteResult.Committed)
        assertEquals(NativeFutureWeekDraftPreview.Rejected(
            NativeFutureWeekDraftRefusal.SOURCE_OR_HISTORY_CHANGED),
            writer.preview(futureWeekDraftSelection()))
        assertEquals(newer,(drafts.read("owner-1","cycle-1")
            as NativeCustomDraftReadResult.Found).draft)
    }

    private fun authoredReview(f:AuthoredCoachFixture,
        readiness:CoachReadiness=CoachReadiness.NORMAL,restricted:Boolean=false)=
        f.coach.reviewAuthored("owner-1","cycle-1",futureInputs(),
            4,0,"slot-0",readiness,restricted,n("5"))

    @Test fun ordinarySignedCustomBlocksUseRealPriorWorkWithoutScheduleActivation() {
        val f=authoredCoachFixture(actualRir=null)
        val before=f.reader.read("owner-1","athlete-1")
            as NativeKotlinMixedHistoryReadResult.Review
        val result=authoredReview(f)
        assertTrue(result.toString(),result is NativeCustomNextCoachResult.Reviewed)
        val ready=result as NativeCustomNextCoachResult.Reviewed
        assertEquals(CustomBlockBaselineStatus.VERIFIED_CONTEXT_ONLY,
            ready.priorBlockBaseline?.status)
        val start=checkNotNull(ready.blockStartingLoads)
        assertEquals(CustomBlockStartingLoadStatus.REPEAT_AUTHORED_LOADS,start.status)
        assertEquals("real-base-w3-d0",start.baseline.previousWorkoutId)
        assertEquals(n("20"),start.startingSets.single().load)
        assertEquals(LoadBasis.DUMBBELL_PER_HAND,start.startingSets.single().basis)
        assertEquals(1,start.assumedGoalRirSetCount)
        assertNull(start.startingSets.single().actualRir)
        assertNull(start.baseline.priorWorkingSets.single().actualRir)
        assertEquals("build",ready.nextWorkout?.blockId)
        assertEquals("slot-0",ready.selectedSlotId)
        assertEquals(CustomNextWorkoutCoachingStatus.NO_MATCHING_SESSION,ready.review.status)
        assertNull(ready.review.loadReviewCandidate)
        assertEquals(before,f.reader.read("owner-1","athlete-1"))
        assertEquals(ready,authoredReview(authoredCoachFixture(initializeHistory=false)))
        assertEquals(CustomBlockStartingLoadStatus.RESTRICTED_OR_LOW_READINESS,
            (authoredReview(f,CoachReadiness.LOW) as NativeCustomNextCoachResult.Reviewed)
                .blockStartingLoads?.status)
        assertEquals(CustomBlockStartingLoadStatus.RESTRICTED_OR_LOW_READINESS,
            (authoredReview(f,restricted=true) as NativeCustomNextCoachResult.Reviewed)
                .blockStartingLoads?.status)
    }

    @Test fun changedPhaseReviewsRealPriorLoadWithoutOverwritingAuthoredPrescription() {
        val f=authoredCoachFixture(actualRir=null,phaseTransition=true)
        val before=f.reader.read("owner-1","athlete-1")
        val result=authoredReview(f)
        assertTrue(result.toString(),result is NativeCustomNextCoachResult.Reviewed)
        val ready=result as NativeCustomNextCoachResult.Reviewed
        assertEquals(CustomBlockBaselineStatus.PRESCRIPTION_CHANGED,
            ready.priorBlockBaseline?.status)
        val transition=checkNotNull(ready.transitionStartingLoads)
        assertEquals(CustomBlockTransitionLoadStatus.READY_FOR_REVIEW,transition.status)
        val set=transition.sets.single()
        assertEquals(n("25"),set.authoredLoad)
        assertEquals(n("20"),set.proposedLoad)
        assertEquals(5,set.startingReps)
        assertEquals(3,set.targetRir)
        assertEquals(2,set.previousGoalRir)
        assertEquals(ProgressionEffortSource.ASSUMED_GOAL,set.effortSource)
        assertNull(set.previousActualRir)
        assertEquals(1,transition.assumedGoalRirSetCount)
        assertTrue(transition.changesAuthoredLoads)
        assertFalse(transition.mayIssueWorkout)
        assertEquals(n("25"),
            (ready.nextWorkout!!.slots.single().sets.single().prescription.load
                as DraftSetLoad.Verified).value)
        assertEquals(n("20"),
            (transition.proposedWorkout!!.slots.single().sets.single().prescription.load
                as DraftSetLoad.Verified).value)
        assertEquals(before,f.reader.read("owner-1","athlete-1"))
        assertEquals(ready,authoredReview(authoredCoachFixture(initializeHistory=false,
            phaseTransition=true)))
        assertEquals(CustomBlockTransitionLoadStatus.RECOVERY_OR_RESTRICTION,
            (authoredReview(f,CoachReadiness.LOW) as NativeCustomNextCoachResult.Reviewed)
                .transitionStartingLoads?.status)
    }

    @Test fun savedNearFailureCorrectionWithdrawsChangedPhaseStartingLoad() {
        val f=authoredCoachFixture(phaseTransition=true)
        assertEquals(CustomBlockTransitionLoadStatus.READY_FOR_REVIEW,
            (authoredReview(f) as NativeCustomNextCoachResult.Reviewed)
                .transitionStartingLoads?.status)
        val correction=NativeKotlinCustomHistoryCorrectionTransaction(
            f.history,f.issuer,f.corrections,fence,identity).correct(
                "owner-1","athlete-1","real-base-w3-d0",
                CustomSavedVisitCorrection("correct-phase-baseline",0,
                    CustomWorkoutVisitKey("cycle-1:w3:d0:slot-0:s1",
                        CustomWorkoutVisitPart.MAIN),"observed-w3-d0",
                    KotlinJournalSetOutcome.COMPLETED,
                    KotlinPerformedSet(n("20"),LoadUnit.LB,12,1)))
        assertTrue(correction.toString(),correction is NativeCustomCorrectionTransactionResult.Committed)
        val after=authoredReview(f) as NativeCustomNextCoachResult.Reviewed
        assertEquals(CustomBlockTransitionLoadStatus.NEAR_FAILURE,
            after.transitionStartingLoads?.status)
        assertNull(after.transitionStartingLoads?.proposedWorkout)
    }

    @Test fun latestSavedCustomCorrectionInvalidatesBlockStartingAdvice() {
        val f=authoredCoachFixture()
        val first=authoredReview(f) as NativeCustomNextCoachResult.Reviewed
        assertEquals(CustomBlockStartingLoadStatus.REPEAT_AUTHORED_LOADS,
            first.blockStartingLoads?.status)
        val corrected=NativeKotlinCustomHistoryCorrectionTransaction(
            f.history,f.issuer,f.corrections,fence,identity).correct(
                "owner-1","athlete-1","real-base-w3-d0",
                CustomSavedVisitCorrection(
                    "correct-last-baseline",0,
                    CustomWorkoutVisitKey("cycle-1:w3:d0:slot-0:s1",
                        CustomWorkoutVisitPart.MAIN),
                    "observed-w3-d0",KotlinJournalSetOutcome.FAILED_ATTEMPT,
                    KotlinPerformedSet(n("20"),LoadUnit.LB,0,null)))
        assertTrue(corrected.toString(),corrected is NativeCustomCorrectionTransactionResult.Committed)
        val after=authoredReview(f) as NativeCustomNextCoachResult.Reviewed
        assertTrue(after.historyRevision>first.historyRevision)
        assertEquals(CustomBlockBaselineStatus.PREVIOUS_WORKOUT_UNVERIFIED_OR_INCOMPLETE,
            after.priorBlockBaseline?.status)
        assertEquals(CustomBlockStartingLoadStatus.BASELINE_UNAVAILABLE,
            after.blockStartingLoads?.status)
        assertTrue(checkNotNull(after.blockStartingLoads).startingSets.isEmpty())
    }

    @Test fun ordinaryCustomCoachingRefusesRevokedConsentOrWrongCycle() {
        val f=authoredCoachFixture()
        assertEquals(NativeCustomNextCoachResult.Rejected(
            NativeCustomNextCoachRefusal.AUTHORED_CONSENT_UNAVAILABLE),
            f.coach.reviewAuthored("owner-1","missing-cycle",futureInputs(),
                4,0,"slot-0",CoachReadiness.NORMAL,false,n("5")))
        assertTrue(store().revoke("owner-1","cycle-1",1)
            is NativeCustomSignoffWrite.Committed)
        assertEquals(NativeCustomNextCoachResult.Rejected(
            NativeCustomNextCoachRefusal.AUTHORED_CONSENT_UNAVAILABLE),authoredReview(f))
    }

    @Test fun blockEntryBaselineRespectsCoachingOffAndDoesNotAlterAuthoredWorkout() {
        weightedFixture=true
        val f=liveFutureCoachFixture()
        val original=f.activation.previewActivatedWorkout(
            "owner-1",futureInputs(),4,0)
        assertTrue(original is CustomWorkoutSnapshotResult.Preview)
        val current=f.assistance.read("owner-1") as NativeCoachSettingsRead.Current
        assertTrue(f.assistance.save("owner-1",current.settings.revision,
            CoachAssistancePolicy.MANUAL) is NativeCoachSettingsWrite.Saved)
        assertEquals(NativeCustomNextCoachResult.Rejected(
            NativeCustomNextCoachRefusal.ASSISTANCE_DISABLED),
            f.coach.review("owner-1",futureInputs(),4,0,"slot-0",
                CoachReadiness.NORMAL,false,n("5")))
        assertEquals(original,f.activation.previewActivatedWorkout(
            "owner-1",futureInputs(),4,0))
    }

    @Test fun realCustomFinishToApprovedNextRepToIssuedWorkoutSurvivesRestart() {
        val f=liveFutureCoachFixture()
        finishRealActivatedSession(f,4,12)
        finishRealActivatedSession(f,5,13)
        val coaching=f.coach.review("owner-1",futureInputs(),6,1,
            "slot-1",CoachReadiness.NORMAL,false,n("5"))
        assertTrue(coaching.toString(),coaching is NativeCustomNextCoachResult.Reviewed)
        val suggestion=(coaching as NativeCustomNextCoachResult.Reviewed).review
        assertEquals(CustomNextWorkoutCoachingStatus.BUILD_REPS,
            suggestion.status)
        assertEquals(14,suggestion.nextSetSuggestions.single().proposedNextReps)
        assertNull(suggestion.loadReviewCandidate)
        val approvalPreview=f.repApprovals.preview("owner-1","real-activated-week6",
            futureInputs(),6,1,"slot-1",CoachReadiness.NORMAL,false,n("5"))
        assertTrue(approvalPreview.toString(),
            approvalPreview is NativeCustomNextRepPreview.Ready)
        val offered=(approvalPreview as NativeCustomNextRepPreview.Ready)
        assertEquals(listOf(14),offered.receipt.nextMinimumReps)
        assertFalse(offered.amendment.mayIssueWorkout)
        assertEquals(NativeCustomNextRepWrite.Rejected(
            NativeCustomNextRepRefusal.CONFIRMATION_REQUIRED),
            f.repApprovals.accept("owner-1","real-activated-week6",
                futureInputs(),6,1,"slot-1",CoachReadiness.NORMAL,false,
                n("5"),offered.receipt.fingerprint,
                offered.receipt.historyRevision,offered.receipt.consentRevision,
                offered.receipt.setKeys,offered.receipt.nextMinimumReps,false))
        assertEquals(NativeCustomNextRepRead.Missing,
            f.repApprovals.readCurrent("owner-1","real-activated-week6",
                futureInputs(),6,1))
        val accepted=f.repApprovals.accept("owner-1","real-activated-week6",
            futureInputs(),6,1,"slot-1",CoachReadiness.NORMAL,false,n("5"),
            offered.receipt.fingerprint,offered.receipt.historyRevision,
            offered.receipt.consentRevision,offered.receipt.setKeys,
            offered.receipt.nextMinimumReps,true)
        assertEquals(NativeCustomNextRepWrite.Accepted(offered.receipt,false),
            accepted)
        assertEquals(NativeCustomNextRepRead.Current(
            offered.receipt,offered.amendment),
            NativeKotlinCustomNextRepApprovalFileStore(
                temp.root.toPath(),fence,identity,f.activation,f.coach,
                f.prepared,
                f.issuer).readCurrent("owner-1","real-activated-week6",
                    futureInputs(),6,1))
        // No preparation until the athlete explicitly accepted exact targets.
        val start=f.prepared.prepareActivated("owner-1",
            "real-activated-week6","cycle-1",6,1,
            futureInputs(),f.signoff,f.activation,f.repApprovals)
        assertTrue(start.toString(),start is NativeCustomPrepareResult.Prepared)
        val snap=(start as NativeCustomPrepareResult.Prepared).workout
        assertEquals(RepBand(14,15),
            snap.snapshot.slots.single().sets.single().prescription.reps)
        assertEquals(offered.receipt.fingerprint,snap.repApprovalFingerprint)
        val issued=f.issuer.issueActivated("owner-1",
            "real-activated-week6","cycle-1",6,1,
            futureInputs(),f.signoff,f.activation,f.repApprovals)
        assertTrue(issued.toString(),issued is NativeCustomJournalResult.Durable)
        assertEquals(snap.snapshot,
            (issued as NativeCustomJournalResult.Durable).issued.journal.snapshot)
        val record=f.issuer.record("owner-1","real-activated-week6",
            CustomWorkoutJournalOperation.Record(
                "real-actual-w6",0,
                CustomWorkoutVisitKey("cycle-1:w6:d1:slot-1:s1",
                    CustomWorkoutVisitPart.MAIN),
                KotlinJournalSetOutcome.COMPLETED,
                KotlinPerformedSet(null,null,14,2)))
        assertTrue(record.toString(),record is NativeCustomJournalResult.Durable)
        val finish=NativeKotlinCustomHistoryTransaction(
            f.history,f.issuer,fence,identity)
            .complete("owner-1","athlete-1","real-activated-week6",
                CustomWorkoutJournalOperation.Finish("real-finish-w6",1))
        assertTrue(finish.toString(),finish is NativeCustomFinishResult.Committed)
        val all=f.reader.read("owner-1","athlete-1")
        assertTrue(all.toString(),all is NativeKotlinMixedHistoryReadResult.Review)
        assertEquals(12,(all as NativeKotlinMixedHistoryReadResult.Review)
            .timeline.completions.size)
        assertEquals(14,
            (all.timeline.completions.last() as NativeKotlinMixedHistoryCompletion.Custom)
                .evidence.visits.single().actual?.reps)
    }



    @Test fun acceptedRepAmendmentCannotSurviveRevokedOrReconfirmedCycleConsent() {
        val f=liveFutureCoachFixture()
        finishRealActivatedSession(f,4,12)
        finishRealActivatedSession(f,5,13)
        val nextId="future-rep-revocation-test"
        val inputs=futureInputs()
        val candidate=f.repApprovals.preview("owner-1",nextId,inputs,
            6,1,"slot-1",CoachReadiness.NORMAL,false,n("5"))
            as NativeCustomNextRepPreview.Ready
        assertTrue(f.repApprovals.accept("owner-1",nextId,inputs,
            6,1,"slot-1",CoachReadiness.NORMAL,false,n("5"),
            candidate.receipt.fingerprint,
            candidate.receipt.historyRevision,
            candidate.receipt.consentRevision,
            candidate.receipt.setKeys,candidate.receipt.nextMinimumReps,true)
            is NativeCustomNextRepWrite.Accepted)
        val original=f.repApprovals.readCurrent("owner-1",nextId,
            inputs,6,1)
        assertTrue(original.toString(),original is NativeCustomNextRepRead.Current)
        val signed=f.signoff.read("owner-1",inputs)
            as NativeActivatedSignoffRead.Found
        assertEquals(NativeActivatedSignoffWrite.Revoked(
            signed.receipt.recordRevision+1),
            f.signoff.revoke("owner-1",signed.receipt.recordRevision))
        assertEquals(NativeCustomNextRepRead.Rejected(
            NativeCustomNextRepRefusal.STALE_CONSENT_OR_HISTORY),
            f.repApprovals.readCurrent("owner-1",nextId,inputs,6,1))
        assertEquals(NativeCustomPrepareResult.Rejected(
            NativeCustomPrepareRefusal.REVIEW_UNAVAILABLE),
            f.prepared.prepareActivated("owner-1",nextId,"cycle-1",
                6,1,inputs,f.signoff,f.activation,f.repApprovals))
        val fingerprint=(f.activation.reviewActivatedCycle("owner-1",inputs)
            as NativeFutureActivatedCycleReview.Ready).reviewFingerprint
        assertTrue(f.signoff.confirm("owner-1",
            signed.receipt.recordRevision+1,fingerprint,inputs,true)
            is NativeActivatedSignoffWrite.Committed)
        // Fresh signoff does NOT resurrect a previous rep-acceptance receipt.
        assertEquals(NativeCustomNextRepRead.Rejected(
            NativeCustomNextRepRefusal.STALE_CONSENT_OR_HISTORY),
            f.repApprovals.readCurrent("owner-1",nextId,inputs,6,1))
        // With a NEW current base signoff, stale advice is discarded.
        // The normal authored workout must remain trainable.
        val baseline=f.prepared.prepareActivated("owner-1",nextId,
            "cycle-1",6,1,inputs,f.signoff,f.activation,f.repApprovals)
        assertTrue(baseline.toString(),baseline is NativeCustomPrepareResult.Prepared)
        val selected=(baseline as NativeCustomPrepareResult.Prepared).workout
        assertNull(selected.repApprovalFingerprint)
        assertEquals(RepBand(10,15),
            selected.snapshot.slots.single().sets.single().prescription.reps)
        val issued=f.issuer.issueActivated("owner-1",nextId,"cycle-1",
            6,1,inputs,f.signoff,f.activation,f.repApprovals)
        assertTrue(issued.toString(),issued is NativeCustomJournalResult.Durable)
        assertEquals(selected.snapshot,
            (issued as NativeCustomJournalResult.Durable).issued.journal.snapshot)
    }



    /**
     * Unlike a caller-authored AdaptiveCycleOption, this candidate comes
     * from actual native issued/completed/replayed workout history.
     */
    private fun futureWeekLoadOption(f:LiveFutureCoachFixture)=
        NativeKotlinCustomFutureWeekLoadOptionReview(
            fence,identity,f.coach,f.activation,drafts,sources,
            permissionStore,f.assistance,f.reader,
            NativeKotlinCustomOutstandingWorkoutInventory(
                f.prepared,f.issuer))

    private fun permitNextLoadAndWeekOverrides() {
        val current=(permissionStore.read("owner-1","cycle-1")
            as NativeAdaptivePermissionRead.Found).receipt
        val saved=permissionStore.confirm("owner-1","cycle-1",
            current.revision,current.allowedKinds+setOf(
                CustomCycleChangeKind.WEEK_OVERRIDES,
                CustomCycleChangeKind.LOADS),true)
        assertEquals(NativeAdaptivePermissionWrite.Committed(
            current.revision+1),saved)
    }

    @Test fun realCustomCompletionEarnsReviewedSingleFutureWeekOptionOnly() {
        weightedFixture=true
        val f=liveFutureCoachFixture()
        finishRealActivatedSession(f,4,15)
        finishRealActivatedSession(f,5,15)
        val before=(drafts.read("owner-1","cycle-1")
            as NativeCustomDraftReadResult.Found).draft
        val missing=futureWeekLoadOption(f).review("owner-1",futureInputs(),
            6,1,"slot-1",CoachReadiness.NORMAL,false,n("5"))
        assertEquals(NativeFutureWeekLoadOptionResult.Rejected(
            NativeFutureWeekLoadRefusal.PERMISSION_REQUIRED),missing)
        permitNextLoadAndWeekOverrides()
        val reviewed=futureWeekLoadOption(f).review("owner-1",futureInputs(),
            6,1,"slot-1",CoachReadiness.NORMAL,false,n("5"))
        assertTrue(reviewed.toString(),reviewed is NativeFutureWeekLoadOptionResult.Ready)
        val ready=reviewed as NativeFutureWeekLoadOptionResult.Ready
        assertEquals(n("20"),ready.previousLoad)
        assertEquals(n("25"),ready.candidateLoad)
        assertEquals(listOf("real-activated-week4","real-activated-week5"),
            ready.comparedWorkoutIds)
        assertEquals(6,ready.affectedAbsoluteWeek)
        assertEquals(1,ready.affectedWeekday)
        assertEquals("slot-1",ready.affectedSlotId)
        assertEquals(3L,ready.draftRevision)
        val proposed=ready.option.proposedCycle
        assertEquals(4L,proposed.revision)
        assertEquals(before.blocks,proposed.blocks)
        assertEquals(1,proposed.weekOverrides["build"]!!.size)
        val override=proposed.weekOverrides.getValue("build").single()
        assertEquals(3,override.weekInBlock)
        assertEquals(1,override.weekday)
        assertEquals("slot-1",override.slotId)
        assertEquals(n("25"),
            (override.sets.single().load as DraftSetLoad.Verified).value)
        assertEquals(before, (drafts.read("owner-1","cycle-1")
            as NativeCustomDraftReadResult.Found).draft)
        assertEquals(reviewed,futureWeekLoadOption(f).review(
            "owner-1",futureInputs(),6,1,"slot-1",
            CoachReadiness.NORMAL,false,n("5")))
        assertFalse(ready.mayApplyAutomatically)
        assertFalse(ready.mayIssueWorkout)
        assertFalse(ready.mayEditHistory)
    }

    @Test fun nativeWeekLoadReviewHonorsManualAndNoFatigueCoaching() {
        weightedFixture=true
        val f=liveFutureCoachFixture()
        finishRealActivatedSession(f,4,15)
        finishRealActivatedSession(f,5,15)
        permitNextLoadAndWeekOverrides()
        val opts=futureWeekLoadOption(f)
        assertTrue(f.assistance.save("owner-1",0,
            CoachAssistancePolicy.MANUAL) is NativeCoachSettingsWrite.Saved)
        assertEquals(NativeFutureWeekLoadOptionResult.Rejected(
            NativeFutureWeekLoadRefusal.ASSISTANCE_DISABLED),
            opts.review("owner-1",futureInputs(),6,1,"slot-1",
                CoachReadiness.NORMAL,false,n("5")))
        assertTrue(f.assistance.save("owner-1",1,
            CoachAssistancePolicy.PROGRESSION_ONLY)
            is NativeCoachSettingsWrite.Saved)
        val straightforward=opts.review("owner-1",futureInputs(),
            6,1,"slot-1",CoachReadiness.LOW,false,n("5"))
        assertTrue(straightforward.toString(),
            straightforward is NativeFutureWeekLoadOptionResult.Ready)
        assertEquals(NativeFutureWeekLoadOptionResult.Rejected(
            NativeFutureWeekLoadRefusal.NOT_EARNED),
            opts.review("owner-1",futureInputs(),6,1,"slot-1",
                CoachReadiness.LOW,true,n("5")))
        active=NativeKotlinOwnerIdentity("other-owner","athlete-1")
        assertEquals(NativeFutureWeekLoadOptionResult.Rejected(
            NativeFutureWeekLoadRefusal.NOT_AUTHORIZED),
            opts.review("owner-1",futureInputs(),6,1,"slot-1",
                CoachReadiness.NORMAL,false,n("5")))
    }

    @Test fun nativeFutureOptionCannotChangePreparedOrCompletedWeek() {
        weightedFixture=true
        val f=liveFutureCoachFixture()
        finishRealActivatedSession(f,4,15)
        finishRealActivatedSession(f,5,15)
        permitNextLoadAndWeekOverrides()
        val prepared=f.prepared.prepareActivated("owner-1","already-prepared",
            "cycle-1",6,1,futureInputs(),f.signoff,f.activation)
        assertTrue(prepared.toString(),prepared is NativeCustomPrepareResult.Prepared)
        assertEquals(NativeFutureWeekLoadOptionResult.Rejected(
            NativeFutureWeekLoadRefusal.WORKOUT_ALREADY_PREPARED_OR_ISSUED),
            futureWeekLoadOption(f).review("owner-1",futureInputs(),
                6,1,"slot-1",CoachReadiness.NORMAL,false,n("5")))
        assertEquals(NativeFutureWeekLoadOptionResult.Rejected(
            NativeFutureWeekLoadRefusal.NOT_EARNED),
            futureWeekLoadOption(f).review("owner-1",futureInputs(),
                6,1,"slot-1",CoachReadiness.NORMAL,true,n("5")))
    }

    @Test fun verifiedDumbbellProgressionNeedsFullHistoryAndExplicitLoadApproval() {
        weightedFixture=true
        val f=liveFutureCoachFixture()
        finishRealActivatedSession(f,4,15)
        finishRealActivatedSession(f,5,15)
        val owner="owner-1"
        val workoutId="future-load-week6"
        val inputs=futureInputs()
        val maxIncrease=n("5")
        val observed=f.coach.review(owner,inputs,6,1,"slot-1",
            CoachReadiness.NORMAL,false,maxIncrease)
        assertTrue(observed.toString(),observed is NativeCustomNextCoachResult.Reviewed)
        val ready=(observed as NativeCustomNextCoachResult.Reviewed)
        assertEquals(CustomNextWorkoutCoachingStatus.READY_FOR_LOAD_REVIEW,
            ready.review.status)
        assertEquals(n("25"),ready.review.loadReviewCandidate)
        assertEquals(listOf("real-activated-week4","real-activated-week5"),
            ready.review.comparedWorkoutIds)
        assertEquals(LoadBasis.DUMBBELL_PER_HAND,ready.verifiedStock?.basis)
        val approvals=NativeKotlinCustomNextLoadApprovalFileStore(
            temp.root.toPath(),fence,identity,f.activation,f.coach,
            f.prepared,f.issuer)
        val preview=approvals.preview(owner,workoutId,inputs,6,1,"slot-1",
            CoachReadiness.NORMAL,false,maxIncrease)
        assertTrue(preview.toString(),preview is NativeCustomLoadPreview.Ready)
        val shown=(preview as NativeCustomLoadPreview.Ready).receipt
        assertEquals(n("20"),shown.originalLoad)
        assertEquals(n("25"),shown.proposedLoad)
        assertEquals(LoadUnit.LB,shown.unit)
        assertEquals(LoadBasis.DUMBBELL_PER_HAND,shown.basis)
        assertEquals(NativeCustomLoadWrite.Rejected(
            NativeCustomLoadRefusal.CONFIRMATION_REQUIRED),
            approvals.accept(owner,workoutId,inputs,6,1,"slot-1",
                CoachReadiness.NORMAL,false,maxIncrease,shown.fingerprint,
                shown.historyRevision,shown.consentRevision,
                shown.setKeys,shown.originalLoad,shown.proposedLoad,false))
        assertEquals(NativeCustomLoadRead.Missing,
            approvals.readCurrent(owner,workoutId,inputs,6,1))
        assertEquals(NativeCustomLoadWrite.Rejected(
            NativeCustomLoadRefusal.FINGERPRINT_CHANGED),
            approvals.accept(owner,workoutId,inputs,6,1,"slot-1",
                CoachReadiness.NORMAL,false,maxIncrease,shown.fingerprint,
                shown.historyRevision,shown.consentRevision,
                shown.setKeys,shown.originalLoad,n("30"),true))
        val accepted=approvals.accept(owner,workoutId,inputs,6,1,"slot-1",
            CoachReadiness.NORMAL,false,maxIncrease,shown.fingerprint,
            shown.historyRevision,shown.consentRevision,
            shown.setKeys,shown.originalLoad,shown.proposedLoad,true)
        assertEquals(NativeCustomLoadWrite.Accepted(shown,false),accepted)
        val restarted=NativeKotlinCustomNextLoadApprovalFileStore(
            temp.root.toPath(),fence,identity,f.activation,f.coach,
            f.prepared,f.issuer)
        assertTrue(restarted.readCurrent(owner,workoutId,inputs,6,1)
            is NativeCustomLoadRead.Current)
        val prepared=f.prepared.prepareActivated(owner,workoutId,"cycle-1",
            6,1,inputs,f.signoff,f.activation,f.repApprovals,restarted)
        assertTrue(prepared.toString(),prepared is NativeCustomPrepareResult.Prepared)
        val snapshot=(prepared as NativeCustomPrepareResult.Prepared).workout
        assertEquals(shown.fingerprint,snapshot.loadApprovalFingerprint)
        assertNull(snapshot.repApprovalFingerprint)
        val load=snapshot.snapshot.slots.single().sets.single().prescription.load
            as DraftSetLoad.Verified
        assertEquals(n("25"),load.value)
        assertEquals(RepBand(10,15),
            snapshot.snapshot.slots.single().sets.single().prescription.reps)
        assertEquals(NativeCustomPreparedRead.Found(snapshot),
            f.prepared.open(owner,workoutId))
        val issued=f.issuer.issueActivated(owner,workoutId,"cycle-1",6,1,
            inputs,f.signoff,f.activation,f.repApprovals,restarted)
        assertTrue(issued.toString(),issued is NativeCustomJournalResult.Durable)
        assertEquals(snapshot.snapshot,
            (issued as NativeCustomJournalResult.Durable).issued.journal.snapshot)
        assertEquals(NativeCustomLoadPreview.Rejected(
            NativeCustomLoadRefusal.ALREADY_PREPARED_OR_ISSUED),
            restarted.preview(owner,workoutId,inputs,6,1,"slot-1",
                CoachReadiness.NORMAL,false,maxIncrease))
        val record=f.issuer.record(owner,workoutId,
            CustomWorkoutJournalOperation.Record("load-actual-week6",0,
                CustomWorkoutVisitKey("cycle-1:w6:d1:slot-1:s1",
                    CustomWorkoutVisitPart.MAIN),
                KotlinJournalSetOutcome.COMPLETED,
                KotlinPerformedSet(n("25"),LoadUnit.LB,10,2)))
        assertTrue(record.toString(),record is NativeCustomJournalResult.Durable)
        assertTrue(NativeKotlinCustomHistoryTransaction(
            f.history,f.issuer,fence,identity).complete(owner,"athlete-1",
                workoutId,CustomWorkoutJournalOperation.Finish(
                    "load-finish-week6",1)) is NativeCustomFinishResult.Committed)
        val recovered=f.reader.read(owner,"athlete-1")
        assertTrue(recovered.toString(),
            recovered is NativeKotlinMixedHistoryReadResult.Review)
        val last=(recovered as NativeKotlinMixedHistoryReadResult.Review)
            .timeline.completions.last() as NativeKotlinMixedHistoryCompletion.Custom
        assertEquals(n("25"),last.evidence.visits.single().actual?.load)
        assertEquals(10,last.evidence.visits.single().actual?.reps)
    }



    @Test fun turningCoachOffBlocksReviewButDoesNotBlockNormalTrainingOrFinish() {
        val f=liveFutureCoachFixture()
        finishRealActivatedSession(f,4,12)
        finishRealActivatedSession(f,5,13)
        val before=f.coach.review("owner-1",futureInputs(),6,1,"slot-1",
            CoachReadiness.NORMAL,false,n("5"))
        assertTrue(before.toString(),before is NativeCustomNextCoachResult.Reviewed)
        assertEquals(NativeCoachSettingsWrite.Saved(NativeCoachAssistanceSettings(
            "owner-1","athlete-1",1,CoachAssistancePolicy.MANUAL)),
            f.assistance.save("owner-1",0,CoachAssistancePolicy.MANUAL))
        assertEquals(NativeCustomNextCoachResult.Rejected(
            NativeCustomNextCoachRefusal.ASSISTANCE_DISABLED),
            f.coach.review("owner-1",futureInputs(),6,1,"slot-1",
                CoachReadiness.NORMAL,false,n("5")))
        assertEquals(NativeCustomNextRepPreview.Rejected(
            NativeCustomNextRepRefusal.REVIEW_NOT_READY),
            f.repApprovals.preview("owner-1","manual-training-week6",
                futureInputs(),6,1,"slot-1",CoachReadiness.NORMAL,
                false,n("5")))
        // Native workout execution, journal recording and Finish still work
        // identically. A manual athlete can log the exact authored targets.
        val prepared=f.prepared.prepareActivated("owner-1",
            "manual-training-week6","cycle-1",6,1,futureInputs(),f.signoff,
            f.activation,f.repApprovals)
        assertTrue(prepared.toString(),prepared is NativeCustomPrepareResult.Prepared)
        val session=(prepared as NativeCustomPrepareResult.Prepared).workout
        assertNull(session.repApprovalFingerprint)
        assertEquals(RepBand(10,15),
            session.snapshot.slots.single().sets.single().prescription.reps)
        val issued=f.issuer.issueActivated("owner-1","manual-training-week6",
            "cycle-1",6,1,futureInputs(),f.signoff,f.activation,f.repApprovals)
        assertTrue(issued.toString(),issued is NativeCustomJournalResult.Durable)
        assertTrue(f.issuer.record("owner-1","manual-training-week6",
            CustomWorkoutJournalOperation.Record("manual-observed",0,
                CustomWorkoutVisitKey("cycle-1:w6:d1:slot-1:s1",
                    CustomWorkoutVisitPart.MAIN),
                KotlinJournalSetOutcome.COMPLETED,
                KotlinPerformedSet(null,null,14,null)))
            is NativeCustomJournalResult.Durable)
        assertTrue(NativeKotlinCustomHistoryTransaction(
            f.history,f.issuer,fence,identity).complete(
                "owner-1","athlete-1","manual-training-week6",
                CustomWorkoutJournalOperation.Finish("manual-finish",1))
            is NativeCustomFinishResult.Committed)
    }

    @Test fun fatigueOffKeepsEarnedProgressionWithoutReadinessSuggestions() {
        val f=liveFutureCoachFixture()
        finishRealActivatedSession(f,4,12)
        finishRealActivatedSession(f,5,13)
        assertEquals(NativeCustomNextCoachResult.Reviewed::class,
            f.coach.review("owner-1",futureInputs(),6,1,"slot-1",
                CoachReadiness.NORMAL,false,n("5"))::class)
        val low=f.coach.review("owner-1",futureInputs(),6,1,"slot-1",
            CoachReadiness.LOW,false,n("5"))
            as NativeCustomNextCoachResult.Reviewed
        assertEquals(CustomNextWorkoutCoachingStatus.RESTRICTED_OR_LOW_READINESS,
            low.review.status)
        assertTrue(f.assistance.save("owner-1",0,
            CoachAssistancePolicy.PROGRESSION_ONLY)
            is NativeCoachSettingsWrite.Saved)
        val normal=f.coach.review("owner-1",futureInputs(),6,1,"slot-1",
            CoachReadiness.LOW,false,n("5"))
            as NativeCustomNextCoachResult.Reviewed
        assertEquals(CustomNextWorkoutCoachingStatus.BUILD_REPS,
            normal.review.status)
        assertEquals(14,normal.review.nextSetSuggestions.single().proposedNextReps)
        // Disabling fatigue is NOT permission to ignore an injured movement.
        val restricted=f.coach.review("owner-1",futureInputs(),6,1,"slot-1",
            CoachReadiness.LOW,true,n("5"))
            as NativeCustomNextCoachResult.Reviewed
        assertEquals(CustomNextWorkoutCoachingStatus.RESTRICTED_OR_LOW_READINESS,
            restricted.review.status)
    }


    @Test fun savedRepAdviceNeverResurrectsAfterCoachWasTurnedOffThenOn() {
        val f=liveFutureCoachFixture()
        finishRealActivatedSession(f,4,12)
        finishRealActivatedSession(f,5,13)
        val key="owner-1"
        val workoutId="toggle-coach-week6"
        val input=futureInputs()
        val shown=f.repApprovals.preview(key,workoutId,input,6,1,"slot-1",
            CoachReadiness.NORMAL,false,n("5"))
            as NativeCustomNextRepPreview.Ready
        assertTrue(f.repApprovals.accept(key,workoutId,input,6,1,"slot-1",
            CoachReadiness.NORMAL,false,n("5"),
            shown.receipt.fingerprint,shown.receipt.historyRevision,
            shown.receipt.consentRevision,shown.receipt.setKeys,
            shown.receipt.nextMinimumReps,true)
            is NativeCustomNextRepWrite.Accepted)
        assertTrue(f.repApprovals.readCurrent(key,workoutId,input,6,1)
            is NativeCustomNextRepRead.Current)
        assertTrue(f.assistance.save(key,0,CoachAssistancePolicy.MANUAL)
            is NativeCoachSettingsWrite.Saved)
        assertEquals(NativeCustomNextRepRead.Rejected(
            NativeCustomNextRepRefusal.STALE_CONSENT_OR_HISTORY),
            f.repApprovals.readCurrent(key,workoutId,input,6,1))
        val manual=f.prepared.prepareActivated(key,workoutId,
            "cycle-1",6,1,input,f.signoff,f.activation,f.repApprovals)
        assertTrue(manual.toString(),manual is NativeCustomPrepareResult.Prepared)
        val baseline=(manual as NativeCustomPrepareResult.Prepared).workout
        assertNull(baseline.repApprovalFingerprint)
        assertEquals(RepBand(10,15),
            baseline.snapshot.slots.single().sets.single().prescription.reps)
        assertTrue(f.assistance.save(key,1,CoachAssistancePolicy.STANDARD)
            is NativeCoachSettingsWrite.Saved)
        // No old "one tap" accepted progression survives a settings toggle.
        assertEquals(NativeCustomNextRepRead.Rejected(
            NativeCustomNextRepRefusal.STALE_CONSENT_OR_HISTORY),
            f.repApprovals.readCurrent(key,workoutId,input,6,1))
        val replay=f.prepared.prepareActivated(key,workoutId,
            "cycle-1",6,1,input,f.signoff,f.activation,f.repApprovals)
        assertEquals(NativeCustomPrepareResult.Prepared(baseline,true),replay)
        val issued=f.issuer.issueActivated(key,workoutId,"cycle-1",
            6,1,input,f.signoff,f.activation,f.repApprovals)
        assertTrue(issued.toString(),issued is NativeCustomJournalResult.Durable)
        assertEquals(baseline.snapshot,
            (issued as NativeCustomJournalResult.Durable).issued.journal.snapshot)
    }



    @Test fun oldApprovedWeightDoesNotBlockAuthoredTrainingAfterCoachOff() {
        weightedFixture=true
        val f=liveFutureCoachFixture()
        finishRealActivatedSession(f,4,15)
        finishRealActivatedSession(f,5,15)
        val id="manual-after-load-approval"
        val inputs=futureInputs()
        val approvals=NativeKotlinCustomNextLoadApprovalFileStore(
            temp.root.toPath(),fence,identity,f.activation,f.coach,
            f.prepared,f.issuer)
        val ready=approvals.preview("owner-1",id,inputs,6,1,"slot-1",
            CoachReadiness.NORMAL,false,n("5"))
            as NativeCustomLoadPreview.Ready
        val receipt=ready.receipt
        assertEquals(NativeCustomLoadWrite.Accepted(receipt,false),
            approvals.accept("owner-1",id,inputs,6,1,"slot-1",
                CoachReadiness.NORMAL,false,n("5"),receipt.fingerprint,
                receipt.historyRevision,receipt.consentRevision,
                receipt.setKeys,receipt.originalLoad,receipt.proposedLoad,true))
        assertTrue(f.assistance.save("owner-1",0,CoachAssistancePolicy.MANUAL)
            is NativeCoachSettingsWrite.Saved)
        assertEquals(NativeCustomLoadRead.Rejected(
            NativeCustomLoadRefusal.STALE_HISTORY_OR_CONSENT),
            approvals.readCurrent("owner-1",id,inputs,6,1))
        val prepared=f.prepared.prepareActivated("owner-1",id,"cycle-1",
            6,1,inputs,f.signoff,f.activation,f.repApprovals,approvals)
        assertTrue(prepared.toString(),prepared is NativeCustomPrepareResult.Prepared)
        val original=(prepared as NativeCustomPrepareResult.Prepared).workout
        assertNull(original.loadApprovalFingerprint)
        assertNull(original.repApprovalFingerprint)
        val load=original.snapshot.slots.single().sets.single()
            .prescription.load as DraftSetLoad.Verified
        assertEquals(n("20"),load.value)
        assertEquals(RepBand(10,15),original.snapshot.slots.single().sets.single()
            .prescription.reps)
        val issued=f.issuer.issueActivated("owner-1",id,"cycle-1",
            6,1,inputs,f.signoff,f.activation,f.repApprovals,approvals)
        assertTrue(issued.toString(),issued is NativeCustomJournalResult.Durable)
        assertEquals(original.snapshot,
            (issued as NativeCustomJournalResult.Durable).issued.journal.snapshot)
        assertTrue(f.assistance.save("owner-1",1,
            CoachAssistancePolicy.STANDARD) is NativeCoachSettingsWrite.Saved)
        assertEquals(NativeCustomLoadRead.Rejected(
            NativeCustomLoadRefusal.STALE_HISTORY_OR_CONSENT),
            approvals.readCurrent("owner-1",id,inputs,6,1))
        assertEquals(n("20"),(f.issuer.open("owner-1",id)
            as NativeCustomJournalResult.Durable).issued.journal.snapshot
            .slots.single().sets.single().prescription.load
            .let{(it as DraftSetLoad.Verified).value})
    }

    @Test fun corruptedAdviceStillFailsClosedInsteadOfSilentlyFallingBack() {
        val f=liveFutureCoachFixture()
        finishRealActivatedSession(f,4,12)
        finishRealActivatedSession(f,5,13)
        val id="corrupted-rep-advice"
        val inputs=futureInputs()
        val proposal=f.repApprovals.preview("owner-1",id,inputs,
            6,1,"slot-1",CoachReadiness.NORMAL,false,n("5"))
            as NativeCustomNextRepPreview.Ready
        val r=proposal.receipt
        assertTrue(f.repApprovals.accept("owner-1",id,inputs,6,1,"slot-1",
            CoachReadiness.NORMAL,false,n("5"),r.fingerprint,
            r.historyRevision,r.consentRevision,r.setKeys,r.nextMinimumReps,
            true) is NativeCustomNextRepWrite.Accepted)
        val file=Files.list(temp.root.toPath()).use{stream->
            stream.filter{
                it.fileName.toString().startsWith("future-rep-") &&
                    it.fileName.toString().endsWith(".bin")
            }.findFirst().orElseThrow()}
        val damaged=Files.readAllBytes(file)
        damaged[damaged.lastIndex]=(damaged.last().toInt() xor 1).toByte()
        Files.write(file,damaged)
        assertEquals(NativeCustomNextRepRead.Rejected(
            NativeCustomNextRepRefusal.CORRUPT_RECORD),
            f.repApprovals.readCurrent("owner-1",id,inputs,6,1))
        assertTrue(f.assistance.save("owner-1",0,
            CoachAssistancePolicy.MANUAL) is NativeCoachSettingsWrite.Saved)
        // A corrupt receipt is not interchangeable with an expired one.
        assertEquals(NativeCustomPrepareResult.Rejected(
            NativeCustomPrepareRefusal.REVIEW_UNAVAILABLE),
            f.prepared.prepareActivated("owner-1",id,"cycle-1",
                6,1,inputs,f.signoff,f.activation,f.repApprovals))
    }


    @Test fun explicitRepResetRestoresAuthoredWorkoutAndKeepsJournalImmutable() {
        val f=liveFutureCoachFixture()
        finishRealActivatedSession(f,4,12)
        finishRealActivatedSession(f,5,13)
        val key="owner-1"
        val workout="reset-rep-before-start"
        val inputs=futureInputs()
        val advice=f.repApprovals.preview(key,workout,inputs,6,1,"slot-1",
            CoachReadiness.NORMAL,false,n("5"))
            as NativeCustomNextRepPreview.Ready
        val proposed=advice.receipt
        assertTrue(f.repApprovals.accept(key,workout,inputs,6,1,
            "slot-1",CoachReadiness.NORMAL,false,n("5"),
            proposed.fingerprint,proposed.historyRevision,
            proposed.consentRevision,proposed.setKeys,
            proposed.nextMinimumReps,true)
            is NativeCustomNextRepWrite.Accepted)
        val prepared=f.prepared.prepareActivated(key,workout,"cycle-1",6,1,
            inputs,f.signoff,f.activation,f.repApprovals)
        assertTrue(prepared.toString(),prepared is NativeCustomPrepareResult.Prepared)
        val adjusted=(prepared as NativeCustomPrepareResult.Prepared).workout
        assertEquals(RepBand(14,15),adjusted.snapshot.slots.single()
            .sets.single().prescription.reps)
        val preview=f.prepared.previewManualReset(key,workout,"cycle-1",6,1,
            inputs,f.signoff,f.activation,f.issuer,f.assistance)
        assertTrue(preview.toString(),
            preview is NativePreparedManualResetPreview.Ready)
        val offered=preview as NativePreparedManualResetPreview.Ready
        assertEquals(adjusted,offered.original)
        assertEquals(RepBand(10,15),
            offered.authored.slots.single().sets.single().prescription.reps)
        assertFalse(offered.mayChangeIssuedWorkout)
        assertFalse(offered.mayChangeHistory)
        assertEquals(NativePreparedManualResetWrite.Rejected(
            NativePreparedManualResetRefusal.CONFIRMATION_REQUIRED),
            f.prepared.acceptManualReset(key,workout,"cycle-1",6,1,
                inputs,f.signoff,f.activation,f.issuer,f.assistance,
                offered.preparedFingerprint,offered.settingsRevision,false))
        assertEquals(NativePreparedManualResetWrite.Rejected(
            NativePreparedManualResetRefusal.STALE_PREVIEW),
            f.prepared.acceptManualReset(key,workout,"cycle-1",6,1,
                inputs,f.signoff,f.activation,f.issuer,f.assistance,
                "a".repeat(64),offered.settingsRevision,true))
        val committed=f.prepared.acceptManualReset(key,workout,"cycle-1",6,1,
            inputs,f.signoff,f.activation,f.issuer,f.assistance,
            offered.preparedFingerprint,offered.settingsRevision,true)
        assertTrue(committed.toString(),committed is NativePreparedManualResetWrite.Reset)
        val reset=(committed as NativePreparedManualResetWrite.Reset).prepared
        assertTrue(reset.resetToAuthored)
        assertNull(reset.repApprovalFingerprint)
        assertNull(reset.loadApprovalFingerprint)
        assertEquals(offered.authored,reset.snapshot)
        // Cold process recovery of v5: create an independent store instance
        // with no in-memory reference to the prepared object.
        val recoveredPreparation=NativeKotlinCustomWorkoutPreparedStore(
            temp.root.toPath(),fence,identity,
            NativeKotlinCustomWorkoutPreflight(
                fence,identity,drafts,sources,store()))
        assertEquals(NativeCustomPreparedRead.Found(reset),
            recoveredPreparation.open(key,workout))
        // The old rep receipt is still mathematically earned and valid,
        // but cannot reappear after explicit reset of the prepared snapshot.
        assertTrue(f.repApprovals.readCurrent(key,workout,inputs,6,1)
            is NativeCustomNextRepRead.Current)
        assertEquals(NativeCustomPrepareResult.Prepared(reset,true),
            f.prepared.prepareActivated(key,workout,"cycle-1",6,1,
                inputs,f.signoff,f.activation,f.repApprovals))
        val issued=f.issuer.issueActivated(key,workout,"cycle-1",6,1,
            inputs,f.signoff,f.activation,f.repApprovals)
        assertTrue(issued.toString(),issued is NativeCustomJournalResult.Durable)
        assertEquals(offered.authored,
            (issued as NativeCustomJournalResult.Durable).issued.journal.snapshot)
        assertEquals(NativePreparedManualResetPreview.Rejected(
            NativePreparedManualResetRefusal.NO_AMENDED_PREPARATION),
            f.prepared.previewManualReset(key,workout,"cycle-1",6,1,inputs,
                f.signoff,f.activation,f.issuer,f.assistance))
        assertTrue(f.issuer.record(key,workout,
            CustomWorkoutJournalOperation.Record("reset-rep-performed",0,
                CustomWorkoutVisitKey("cycle-1:w6:d1:slot-1:s1",
                    CustomWorkoutVisitPart.MAIN),
                KotlinJournalSetOutcome.COMPLETED,
                KotlinPerformedSet(null,null,13,2)))
            is NativeCustomJournalResult.Durable)
        assertTrue(NativeKotlinCustomHistoryTransaction(
            f.history,f.issuer,fence,identity).complete(key,"athlete-1",
                workout,CustomWorkoutJournalOperation.Finish(
                    "reset-rep-finish",1)) is NativeCustomFinishResult.Committed)
        val recovered=f.reader.read(key,"athlete-1")
        assertTrue(recovered.toString(),
            recovered is NativeKotlinMixedHistoryReadResult.Review)
        val last=(recovered as NativeKotlinMixedHistoryReadResult.Review)
            .timeline.completions.last() as NativeKotlinMixedHistoryCompletion.Custom
        assertEquals(offered.authored, f.issuer.open(key,workout).let{
            (it as NativeCustomJournalResult.Durable).issued.journal.snapshot
        })
        assertEquals(13,last.evidence.visits.single().actual?.reps)
    }

    @Test fun explicitLoadResetWorksButCannotRewriteAlreadyIssuedWorkout() {
        weightedFixture=true
        val f=liveFutureCoachFixture()
        finishRealActivatedSession(f,4,15)
        finishRealActivatedSession(f,5,15)
        val key="owner-1"
        val workout="reset-dumbbell-before-start"
        val inputs=futureInputs()
        val approvals=NativeKotlinCustomNextLoadApprovalFileStore(
            temp.root.toPath(),fence,identity,f.activation,f.coach,
            f.prepared,f.issuer)
        val shown=approvals.preview(key,workout,inputs,6,1,"slot-1",
            CoachReadiness.NORMAL,false,n("5")) as NativeCustomLoadPreview.Ready
        val offer=shown.receipt
        assertTrue(approvals.accept(key,workout,inputs,6,1,"slot-1",
            CoachReadiness.NORMAL,false,n("5"),offer.fingerprint,
            offer.historyRevision,offer.consentRevision,offer.setKeys,
            offer.originalLoad,offer.proposedLoad,true)
            is NativeCustomLoadWrite.Accepted)
        val prepared=f.prepared.prepareActivated(key,workout,"cycle-1",6,1,
            inputs,f.signoff,f.activation,f.repApprovals,approvals)
        assertTrue(prepared.toString(),prepared is NativeCustomPrepareResult.Prepared)
        val suggested=(prepared as NativeCustomPrepareResult.Prepared).workout
        assertEquals(n("25"),(suggested.snapshot.slots.single().sets.single()
            .prescription.load as DraftSetLoad.Verified).value)
        val shownReset=f.prepared.previewManualReset(key,workout,"cycle-1",
            6,1,inputs,f.signoff,f.activation,f.issuer,f.assistance)
            as NativePreparedManualResetPreview.Ready
        val result=f.prepared.acceptManualReset(key,workout,"cycle-1",6,1,
            inputs,f.signoff,f.activation,f.issuer,f.assistance,
            shownReset.preparedFingerprint,shownReset.settingsRevision,true)
        assertTrue(result.toString(),result is NativePreparedManualResetWrite.Reset)
        val original=(result as NativePreparedManualResetWrite.Reset).prepared
        assertEquals(n("20"),(original.snapshot.slots.single().sets.single()
            .prescription.load as DraftSetLoad.Verified).value)
        assertTrue(original.resetToAuthored)
        // A load receipt can still be current. It must not override a reset.
        assertTrue(approvals.readCurrent(key,workout,inputs,6,1)
            is NativeCustomLoadRead.Current)
        assertEquals(NativeCustomPrepareResult.Prepared(original,true),
            f.prepared.prepareActivated(key,workout,"cycle-1",6,1,
                inputs,f.signoff,f.activation,f.repApprovals,approvals))
        assertTrue(f.issuer.issueActivated(key,workout,"cycle-1",6,1,
            inputs,f.signoff,f.activation,f.repApprovals,approvals)
            is NativeCustomJournalResult.Durable)
        assertEquals(n("20"),((f.issuer.open(key,workout)
            as NativeCustomJournalResult.Durable).issued.journal.snapshot
            .slots.single().sets.single().prescription.load
            as DraftSetLoad.Verified).value)
        assertEquals(NativePreparedManualResetWrite.Rejected(
            NativePreparedManualResetRefusal.NO_AMENDED_PREPARATION),
            f.prepared.acceptManualReset(key,workout,"cycle-1",6,1,
                inputs,f.signoff,f.activation,f.issuer,f.assistance,
                shownReset.preparedFingerprint,shownReset.settingsRevision,true))
        // Nothing can replace a logged journal after it is already issued.
        assertTrue(f.issuer.record(key,workout,
            CustomWorkoutJournalOperation.Record("issued-load-target",0,
                CustomWorkoutVisitKey("cycle-1:w6:d1:slot-1:s1",
                    CustomWorkoutVisitPart.MAIN),
                KotlinJournalSetOutcome.COMPLETED,
                KotlinPerformedSet(n("20"),LoadUnit.LB,10,2)))
            is NativeCustomJournalResult.Durable)
        assertEquals(n("20"),((f.issuer.open(key,workout)
            as NativeCustomJournalResult.Durable).issued.journal.snapshot
            .slots.single().sets.single().prescription.load
            as DraftSetLoad.Verified).value)
    }



    @Test fun cannotResetAnAlreadyIssuedAdjustedWorkoutOrRetargetItsHistory() {
        val f=liveFutureCoachFixture()
        finishRealActivatedSession(f,4,12)
        finishRealActivatedSession(f,5,13)
        val key="owner-1"
        val id="issued-adjusted-cannot-reset"
        val inputs=futureInputs()
        val proposal=f.repApprovals.preview(key,id,inputs,6,1,"slot-1",
            CoachReadiness.NORMAL,false,n("5")) as NativeCustomNextRepPreview.Ready
        val receipt=proposal.receipt
        assertTrue(f.repApprovals.accept(key,id,inputs,6,1,"slot-1",
            CoachReadiness.NORMAL,false,n("5"),receipt.fingerprint,
            receipt.historyRevision,receipt.consentRevision,
            receipt.setKeys,receipt.nextMinimumReps,true)
            is NativeCustomNextRepWrite.Accepted)
        assertTrue(f.prepared.prepareActivated(key,id,"cycle-1",6,1,inputs,
            f.signoff,f.activation,f.repApprovals)
            is NativeCustomPrepareResult.Prepared)
        val old=f.prepared.open(key,id) as NativeCustomPreparedRead.Found
        val shown=f.prepared.previewManualReset(key,id,"cycle-1",6,1,
            inputs,f.signoff,f.activation,f.issuer,f.assistance)
        assertTrue(shown.toString(),shown is NativePreparedManualResetPreview.Ready)
        assertTrue(f.issuer.issueActivated(key,id,"cycle-1",6,1,
            inputs,f.signoff,f.activation,f.repApprovals)
            is NativeCustomJournalResult.Durable)
        val ready=shown as NativePreparedManualResetPreview.Ready
        assertEquals(NativePreparedManualResetWrite.Rejected(
            NativePreparedManualResetRefusal.ALREADY_ISSUED),
            f.prepared.acceptManualReset(key,id,"cycle-1",6,1,inputs,
                f.signoff,f.activation,f.issuer,f.assistance,
                ready.preparedFingerprint,ready.settingsRevision,true))
        assertEquals(NativeCustomPreparedRead.Found(old.workout),
            f.prepared.open(key,id))
        assertEquals(RepBand(14,15),(f.issuer.open(key,id)
            as NativeCustomJournalResult.Durable).issued.journal.snapshot
            .slots.single().sets.single().prescription.reps)
    }

    @Test fun resetPreviewExpiresWhenCoachPreferenceRevisionChanges() {
        val f=liveFutureCoachFixture()
        finishRealActivatedSession(f,4,12)
        finishRealActivatedSession(f,5,13)
        val key="owner-1"
        val id="prepared-reset-preference-cas"
        val inputs=futureInputs()
        val shown=f.repApprovals.preview(key,id,inputs,6,1,"slot-1",
            CoachReadiness.NORMAL,false,n("5")) as NativeCustomNextRepPreview.Ready
        val receipt=shown.receipt
        assertTrue(f.repApprovals.accept(key,id,inputs,6,1,"slot-1",
            CoachReadiness.NORMAL,false,n("5"),receipt.fingerprint,
            receipt.historyRevision,receipt.consentRevision,
            receipt.setKeys,receipt.nextMinimumReps,true)
            is NativeCustomNextRepWrite.Accepted)
        val adjusted=f.prepared.prepareActivated(key,id,"cycle-1",6,1,
            inputs,f.signoff,f.activation,f.repApprovals)
        assertTrue(adjusted.toString(),adjusted is NativeCustomPrepareResult.Prepared)
        val preview=f.prepared.previewManualReset(key,id,"cycle-1",6,1,
            inputs,f.signoff,f.activation,f.issuer,f.assistance)
            as NativePreparedManualResetPreview.Ready
        assertTrue(f.assistance.save(key,0,CoachAssistancePolicy.MANUAL)
            is NativeCoachSettingsWrite.Saved)
        assertEquals(NativePreparedManualResetWrite.Rejected(
            NativePreparedManualResetRefusal.STALE_PREVIEW),
            f.prepared.acceptManualReset(key,id,"cycle-1",6,1,inputs,
                f.signoff,f.activation,f.issuer,f.assistance,
                preview.preparedFingerprint,preview.settingsRevision,true))
        val fresh=f.prepared.previewManualReset(key,id,"cycle-1",6,1,
            inputs,f.signoff,f.activation,f.issuer,f.assistance)
            as NativePreparedManualResetPreview.Ready
        assertEquals(1L,fresh.settingsRevision)
        assertTrue(f.prepared.acceptManualReset(key,id,"cycle-1",6,1,inputs,
            f.signoff,f.activation,f.issuer,f.assistance,
            fresh.preparedFingerprint,fresh.settingsRevision,true)
            is NativePreparedManualResetWrite.Reset)
        assertEquals(RepBand(10,15),(f.prepared.open(key,id)
            as NativeCustomPreparedRead.Found).workout.snapshot.slots.single()
            .sets.single().prescription.reps)
        assertTrue(f.issuer.issueActivated(key,id,"cycle-1",6,1,
            inputs,f.signoff,f.activation,f.repApprovals)
            is NativeCustomJournalResult.Durable)
    }


}
