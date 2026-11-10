package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class NativeKotlinCustomWorkoutPreflightTest {
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
    private fun initialize() {
        assertEquals(NativeAuthoringSourceWrite.Committed(1),
            sources.publish("owner-1",0,source()))
        assertEquals(NativeCustomDraftWriteResult.Committed(1),
            drafts.save("owner-1","cycle-1",0,cycle()))
        assertEquals(CustomCoachAuditStatus.READY_FOR_ATHLETE_REVIEW,
            CustomCycleCoachAuditDomain.review(
                cycle(),setup(),source().catalog,input()).status)
    }
    private fun approve() {
        assertTrue(consent.confirm(
            "owner-1","cycle-1",1,1,0,input(),true
        ) is NativeCustomSignoffWrite.Committed)
    }

    @Test fun exactWeekOverrideBecomesNonIssuingOwnerReviewedSession() {
        initialize()
        approve()
        val result=preflight.inspect("owner-1","cycle-1",2,0,input())
        assertTrue(result.toString(),
            result is NativeCustomPreflightResult.ReviewedPreview)
        val reviewed=result as NativeCustomPreflightResult.ReviewedPreview
        val snap=reviewed.session
        assertEquals("cycle-1:w2:d0:slot-0:s1",
            snap.slots.single().sets.single().setKey)
        assertEquals(16,snap.slots.single().sets.single()
            .prescription.reps.minimum)
        assertEquals("My exact prescription",snap.slots.single().coachNote)
        assertEquals(1L,reviewed.receipt.recordRevision)
        assertEquals(1L,snap.stamp.gymCatalogSourceRevision)
        assertEquals(200,snap.declaredNonSetSeconds)
        assertFalse(reviewed.isIssued)
        assertFalse(reviewed.mayLog)
        assertFalse(snap.isIssued)
        assertFalse(snap.mayRecordSets)
        assertEquals(10,(
            preflight.inspect("owner-1","cycle-1",1,0,input())
                as NativeCustomPreflightResult.ReviewedPreview
            ).session.slots.single().sets.single().prescription.reps.minimum)
    }

    @Test fun shorterCustomPreviewNeedsCurrentSignedCycleAndNeverIssuesOrEdits() {
        initialize();approve()
        val checked=preflight.reviewShorter(
            "owner-1","cycle-1",2,0,input(),20)
        assertTrue(checked.toString(),
            checked is NativeCustomShortSessionPreflight.Reviewed)
        val proposal=(checked as NativeCustomShortSessionPreflight.Reviewed)
        assertEquals(1L,proposal.receipt.recordRevision)
        assertEquals("athlete-1",proposal.review.source?.athleteId)
        assertEquals(1L,proposal.review.source?.gymCatalogSourceRevision)
        assertEquals(CustomShortSessionStatus.ALREADY_FITS,
            proposal.review.status)
        assertEquals(330L,proposal.review.reviewedEstimatedSeconds)
        assertEquals(listOf("cycle-1:w2:d0:slot-0:s1"),
            proposal.review.retainedSetKeys)
        assertTrue(proposal.review.omittedSetKeys.isEmpty())
        assertFalse(proposal.mayIssue)
        assertFalse(proposal.maySave)
        assertFalse(proposal.review.canIssueWorkout)
        assertFalse(proposal.review.canSaveCustomCycle)
        assertEquals(NativeCustomShortSessionPreflight.Rejected(
            NativeCustomPreflightRefusal.CONSENT_NOT_CURRENT),
            preflight.reviewShorter("owner-1","cycle-1",2,0,
                input().copy(nonSetSecondsByWeekday=mapOf(0 to 210,2 to 200,4 to 200)),
                20))
        current=NativeKotlinOwnerIdentity("other-owner","athlete-1")
        assertEquals(NativeCustomShortSessionPreflight.Rejected(
            NativeCustomPreflightRefusal.NOT_AUTHORIZED),
            preflight.reviewShorter("owner-1","cycle-1",2,0,input(),20))
        current=NativeKotlinOwnerIdentity("owner-1","athlete-1")
        assertEquals(NativeCustomShortSessionPreflight.Rejected(
            NativeCustomPreflightRefusal.INVALID_SELECTION),
            preflight.reviewShorter("owner-1","cycle-1",105,0,input(),20))
        assertTrue(prepared.open("owner-1","unused-time-review")
            is NativeCustomPreparedRead.Missing)
    }

    @Test fun neverPreparesWithoutAthleteConsentOrWithChangedPolicy() {
        initialize()
        assertEquals(NativeCustomPreflightResult.Rejected(
            NativeCustomPreflightRefusal.CONSENT_NOT_CURRENT),
            preflight.inspect("owner-1","cycle-1",1,0,input()))
        approve()
        assertEquals(NativeCustomPreflightResult.Rejected(
            NativeCustomPreflightRefusal.CONSENT_NOT_CURRENT),
            preflight.inspect("owner-1","cycle-1",1,0,input().copy(
                nonSetSecondsByWeekday=mapOf(0 to 201,2 to 200,4 to 200))))
    }

    @Test fun gymOrCycleRevisionEditInvalidatesPreviewAndCannotReissueOldConsent() {
        initialize();approve()
        assertEquals(NativeAuthoringSourceWrite.Committed(2),
            sources.publish("owner-1",1,source(2)))
        assertEquals(NativeCustomPreflightResult.Rejected(
            NativeCustomPreflightRefusal.CONSENT_NOT_CURRENT),
            preflight.inspect("owner-1","cycle-1",1,0,input()))
        assertEquals(NativeCustomDraftWriteResult.Committed(2),
            drafts.save("owner-1","cycle-1",1,cycle(2)))
        assertEquals(NativeCustomPreflightResult.Rejected(
            NativeCustomPreflightRefusal.CONSENT_NOT_CURRENT),
            preflight.inspect("owner-1","cycle-1",1,0,input()))
    }

    @Test fun changingIdentityOrInvalidSelectionDoesNotExposePrivateSets() {
        initialize();approve()
        current=NativeKotlinOwnerIdentity("other-owner","athlete-1")
        assertEquals(NativeCustomPreflightResult.Rejected(
            NativeCustomPreflightRefusal.NOT_AUTHORIZED),
            preflight.inspect("owner-1","cycle-1",1,0,input()))
        current=NativeKotlinOwnerIdentity("owner-1","athlete-1")
        assertEquals(NativeCustomPreflightResult.Rejected(
            NativeCustomPreflightRefusal.INVALID_SELECTION),
            preflight.inspect("owner-1","cycle-1",105,0,input()))
        assertEquals(NativeCustomPreflightResult.Rejected(
            NativeCustomPreflightRefusal.SESSION_UNAVAILABLE),
            preflight.inspect("owner-1","cycle-1",2,1,input()))
    }
    @Test fun preparedExactCustomWeekIsColdRecoverableButNeverIssued() {
        initialize();approve()
        val first=prepared.prepare("owner-1","workout-2",
            "cycle-1",2,0,input())
        assertTrue(first.toString(),first is NativeCustomPrepareResult.Prepared)
        val saved=(first as NativeCustomPrepareResult.Prepared).workout
        assertFalse(first.replayed)
        assertEquals("cycle-1:w2:d0:slot-0:s1",
            saved.snapshot.slots.single().sets.single().setKey)
        assertEquals(16,saved.snapshot.slots.single().sets.single()
            .prescription.reps.minimum)
        assertFalse(saved.isIssued)
        assertFalse(saved.mayLog)
        assertFalse(saved.mayFinish)
        val restored=NativeKotlinCustomWorkoutPreparedStore(
            temp.root.toPath(),
            NativeKotlinApprovalRevisionFence(temp.root.toPath()),
            identity,preflight)
        assertEquals(NativeCustomPreparedRead.Found(saved),
            restored.open("owner-1","workout-2"))
        assertEquals(NativeCustomPrepareResult.Prepared(saved,true),
            restored.prepare("owner-1","workout-2",
                "cycle-1",2,0,input()))
    }

    @Test fun reusedIdCannotReplaceDifferentWeekOrChangedPrescriptions() {
        initialize();approve()
        assertTrue(prepared.prepare("owner-1","workout-1","cycle-1",
            1,0,input()) is NativeCustomPrepareResult.Prepared)
        assertEquals(NativeCustomPrepareResult.Rejected(
            NativeCustomPrepareRefusal.DIFFERENT_WORKOUT_ALREADY_PREPARED),
            prepared.prepare("owner-1","workout-1","cycle-1",
                2,0,input()))
        val old=(prepared.open("owner-1","workout-1")
            as NativeCustomPreparedRead.Found).workout
        assertEquals(10,old.snapshot.slots.single().sets.single()
            .prescription.reps.minimum)
    }

    @Test fun revokedConsentAndChangedGymPreventNewPreparationWithoutDestroyingOldFile() {
        initialize();approve()
        val previous=prepared.prepare("owner-1","workout-old",
            "cycle-1",1,0,input())
        assertTrue(previous is NativeCustomPrepareResult.Prepared)
        assertTrue(consent.revoke("owner-1","cycle-1",1)
            is NativeCustomSignoffWrite.Committed)
        assertEquals(NativeCustomPrepareResult.Rejected(
            NativeCustomPrepareRefusal.REVIEW_UNAVAILABLE),
            prepared.prepare("owner-1","workout-new",
                "cycle-1",2,0,input()))
        assertTrue(prepared.open("owner-1","workout-old")
            is NativeCustomPreparedRead.Found)
        assertEquals(NativeAuthoringSourceWrite.Committed(2),
            sources.publish("owner-1",1,source(2)))
        assertEquals(NativeCustomPrepareResult.Rejected(
            NativeCustomPrepareRefusal.REVIEW_UNAVAILABLE),
            prepared.prepare("owner-1","workout-third",
                "cycle-1",1,0,input()))
    }

    @Test fun identitySwitchAndChecksumCorruptionNeverExposePreparedContents() {
        initialize();approve()
        assertTrue(prepared.prepare("owner-1","workout-1",
            "cycle-1",1,0,input()) is NativeCustomPrepareResult.Prepared)
        current=NativeKotlinOwnerIdentity("other-owner","athlete-1")
        assertEquals(NativeCustomPreparedRead.Rejected(
            NativeCustomPrepareRefusal.NOT_AUTHORIZED),
            prepared.open("owner-1","workout-1"))
        current=NativeKotlinOwnerIdentity("owner-1","athlete-1")
        val file=java.nio.file.Files.list(temp.root.toPath()).use { files ->
            files.filter { it.fileName.toString()
                .startsWith("prepared-custom-") }.findFirst().orElseThrow()
        }
        val bytes=java.nio.file.Files.readAllBytes(file)
        bytes[bytes.lastIndex]=(bytes.last().toInt() xor 1).toByte()
        java.nio.file.Files.write(file,bytes)
        assertEquals(NativeCustomPreparedRead.Rejected(
            NativeCustomPrepareRefusal.CORRUPT_RECORD),
            prepared.open("owner-1","workout-1"))
        assertEquals(NativeCustomPrepareResult.Rejected(
            NativeCustomPrepareRefusal.CORRUPT_RECORD),
            prepared.prepare("owner-1","workout-1",
                "cycle-1",1,0,input()))
    }

    @Test fun futureWeekApprovalHasIndependentColdRecoverableSchema() {
        initialize();approve()
        val session=(preflight.inspect("owner-1","cycle-1",2,0,input())
            as NativeCustomPreflightResult.ReviewedPreview).session
        val value=NativeCustomPreparedWorkout(
            "owner-1","athlete-1","future-workout-2",
            1,"a".repeat(64),session,
            NativeCustomPreparationAuthority.ACTIVATED_BLOCK,
            futureWeekApprovalFingerprint="b".repeat(64),
            futureWeekSlotId="slot-0")
        // Exercise the real checksum + versioned disk codec, including
        // cold decode. A legacy per-workout load fingerprint is NULL.
        val writer=NativeKotlinCustomWorkoutPreparedStore::class.java
            .getDeclaredMethod("encode",NativeCustomPreparedWorkout::class.java)
        writer.isAccessible=true
        val bytes=writer.invoke(prepared,value) as ByteArray
        assertEquals(6,java.nio.ByteBuffer.wrap(bytes).getInt(8))
        val file=temp.root.toPath().resolve("future-version-6.bin")
        java.nio.file.Files.write(file,bytes)
        val reader=NativeKotlinCustomWorkoutPreparedStore::class.java
            .getDeclaredMethod("decode",java.nio.file.Path::class.java)
        reader.isAccessible=true
        val restored=reader.invoke(prepared,file) as NativeCustomPreparedWorkout
        assertEquals(value,restored)
        assertNull(restored.repApprovalFingerprint)
        assertNull(restored.loadApprovalFingerprint)
        assertEquals("slot-0",restored.futureWeekSlotId)
        assertEquals("b".repeat(64),restored.futureWeekApprovalFingerprint)

        // Both old and new approval cannot share one prepared snapshot.
        val wrong=value.copy(loadApprovalFingerprint="c".repeat(64))
        val failure=runCatching{writer.invoke(prepared,wrong)}.exceptionOrNull()
        assertTrue(failure is java.lang.reflect.InvocationTargetException)
        assertTrue(failure?.cause is IllegalArgumentException)
        val noSlot=value.copy(futureWeekSlotId=null)
        val missing=runCatching{writer.invoke(prepared,noSlot)}.exceptionOrNull()
        assertTrue(missing?.cause is IllegalArgumentException)
    }

}
