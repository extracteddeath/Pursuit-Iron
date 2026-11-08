package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class CoachFirstProgramStartDomainTest {
    private fun n(value:String)=BigDecimal(value)
    private val source=GeneratedAdaptiveBlockFixture.athlete()
    private val movements=GeneratedAdaptiveBlockFixture.movements
    private fun request(
        loads:Map<String,BigDecimal> = emptyMap(),
        phase:TrainingPhase=TrainingPhase.HYPERTROPHY
    )=FirstProgramStartRequest(
        source,movements,loads,emptySet(),phase,
        nonSetSecondsPerDay=1200)

    @Test fun newAthleteSeesActualMovementsButNoInventedTrainingWeights() {
        val reviewed=CoachFirstProgramStartDomain.review(request())
        assertEquals(FirstProgramStartStatus.CALIBRATION_REQUIRED,reviewed.status)
        assertEquals(listOf("bench","row","squat"),
            reviewed.eligibleMovements.map {it.exerciseId})
        assertTrue(reviewed.eligibleMovements.all {it.needsWeightCalibration})
        assertTrue(reviewed.chosenExerciseIds.isEmpty())
        assertNull(reviewed.policy)
        assertNull(reviewed.generation)
        assertNull(reviewed.prescription)
        assertFalse(reviewed.mayIssueWorkout)
        assertFalse(reviewed.maySaveProgram)
    }

    @Test fun calibrationMustBeExactAndEquipmentSpecific() {
        val invalid=CoachFirstProgramStartDomain.review(request(
            mapOf("bench" to n("27.5"))))
        assertEquals(FirstProgramStartStatus.INVALID_CALIBRATION,invalid.status)
        assertNull(invalid.generation)
        assertTrue(invalid.issues.any {it.contains("rounded")})
        val unknown=CoachFirstProgramStartDomain.review(request(
            mapOf("unknown-exercise" to n("25"))))
        assertEquals(FirstProgramStartStatus.INVALID_CALIBRATION,unknown.status)
        assertNull(unknown.policy)
    }

    @Test fun calibratedFirstProgramIncludesAllSixteenMusclesAndVersionedPolicy() {
        val starts=mapOf("bench" to n("25"),"row" to n("25"),
            "squat" to n("25"))
        val result=CoachFirstProgramStartDomain.review(request(starts))
        val policy=checkNotNull(result.policy)
        assertEquals(CoachFirstProgramStartDomain.POLICY_VERSION,policy.policyId)
        assertEquals("athlete-1",policy.athleteId)
        assertEquals(3,policy.trainingDays)
        assertEquals(TrainingPhase.HYPERTROPHY,policy.phase)
        assertEquals(TrainingMuscle.entries.toSet(),
            policy.selectedBounds.map {it.muscle}.toSet())
        assertEquals(n("4"),policy.selectedBounds.single{
            it.muscle==TrainingMuscle.CHEST}.mev)
        assertEquals(setOf("bench","row","squat"),result.chosenExerciseIds.toSet())
        assertTrue(result.unsupportedMuscles.isNotEmpty())
        assertFalse(result.maySaveProgram)
        assertFalse(result.mayIssueWorkout)
        assertEquals(result,CoachFirstProgramStartDomain.review(request(starts)))
    }

    @Test fun strengthDoesNotInferPrimaryFromAnAccessoryOrUncalibratedLift() {
        val onlyAccessory=CoachFirstProgramStartDomain.review(request(
            mapOf("row" to n("25")),TrainingPhase.STRENGTH))
        assertEquals(FirstProgramStartStatus.DRAFT_NEEDS_REVIEW,onlyAccessory.status)
        assertTrue(onlyAccessory.issues.any {it.contains("primary")})
        assertNull(onlyAccessory.generation)
        val calibrated=CoachFirstProgramStartDomain.review(request(
            mapOf("bench" to n("25"),"row" to n("25"),"squat" to n("25")),
            TrainingPhase.STRENGTH))
        assertEquals(mapOf("bench" to 1),
            calibrated.policy?.primaryLiftMinimumDays)
        assertFalse(calibrated.mayIssueWorkout)
    }

    @Test fun userPreferencesAreNotMutatedOrReplacedByCandidateGeneration() {
        val starts=linkedMapOf("bench" to n("25"),"row" to n("30"))
        val original=starts.toMap()
        val result=CoachFirstProgramStartDomain.review(request(starts))
        assertEquals(original,starts)
        assertTrue(result.chosenExerciseIds.containsAll(starts.keys))
        assertFalse(result.maySaveProgram)
    }
    @Test fun ordinaryCalibratedFirstProgramCanReachReviewableCompleteWeek() {
        val loads=mapOf("bench" to n("25"),"row" to n("25"),
            "squat" to n("25"))
        val result=CoachFirstProgramStartDomain.review(request(loads))
        assertEquals(result.issues.joinToString(" | "),
            FirstProgramStartStatus.PRESCRIBED_DRAFT_FOR_REVIEW,result.status)
        assertEquals(AthleteGenerationStatus.GENERATED_FOR_REVIEW,
            result.generation?.status)
        assertEquals(PrescriptionDraftStatus.COMPLETE_FOR_REVIEW,
            result.prescription?.status)
        assertEquals(3,result.prescription?.sessions?.size)
        assertTrue(result.prescription?.sessions?.all{it.exercises.isNotEmpty()}==true)
        val source=GeneratedAdaptiveBlockFixture.generation()
        val converted=CoachFirstProgramDraftConversionDomain.propose(
            result,source.setup,source.catalog,
            "calibrated-first-program","My first program",4)
        assertEquals(converted.reason,
            FirstProgramDraftStatus.READY_FOR_SAVING_DRAFT,converted.status)
        val policy=CoachFirstProgramDraftConversionDomain
            .policyForInitialFullCycleReview(result,source.setup)
        assertNotNull(policy)
        val audit=CustomCycleCoachAuditDomain.review(
            checkNotNull(converted.cycle),source.setup,
            source.catalog,checkNotNull(policy))
        assertEquals(audit.issues.toString(),
            CustomCoachAuditStatus.READY_FOR_ATHLETE_REVIEW,audit.status)
    }

    @Test fun indirectExposureDoesNotForbidACompoundOrInventDirectCoverage() {
        val compound=movements.map {movement->
            if(movement.exerciseId=="bench")movement.copy(credits=movement.credits+
                DeclaredMuscleCredit(TrainingMuscle.TRICEPS,
                    MuscleCreditRole.INDIRECT,n("0.5"))) else movement
        }
        val result=CoachFirstProgramStartDomain.review(request(mapOf(
            "bench" to n("25"),"row" to n("25"),"squat" to n("25")))
            .copy(catalog=compound))
        assertEquals(result.issues.toString(),
            FirstProgramStartStatus.PRESCRIBED_DRAFT_FOR_REVIEW,result.status)
        val triceps=checkNotNull(result.policy).selectedBounds.single {
            it.muscle==TrainingMuscle.TRICEPS
        }
        assertEquals(n("0"),triceps.mev)
        assertEquals(n("8"),triceps.mav)
        assertEquals(0,triceps.minimumDirectDays)
        assertEquals(n("12"),triceps.mrv)
        assertTrue(TrainingMuscle.TRICEPS in result.unsupportedMuscles)
        val dose=checkNotNull(result.prescription?.finalAudit)
            .muscles.getValue(TrainingMuscle.TRICEPS)
        assertEquals(n("0"),dose.directWorkingSets)
        assertTrue(dose.indirectWorkingSets>BigDecimal.ZERO)
        // An unrelated unexposed muscle gains neither minimum nor ceiling.
        assertEquals(n("0"),result.policy?.selectedBounds?.single {
            it.muscle==TrainingMuscle.WRIST_EXTENSORS
        }?.mrv)
    }

    @Test fun lighterWarmupsAreExactSeparateSetsAndNeverWorkingDose() {
        val result=CoachFirstProgramStartDomain.review(request(mapOf(
            "bench" to n("25"),"row" to n("25"),"squat" to n("25"))))
        val prescribed=checkNotNull(result.prescription)
        assertEquals(PrescriptionDraftStatus.COMPLETE_FOR_REVIEW,prescribed.status)
        val sets=prescribed.sessions.flatMap{it.exercises}.flatMap{it.sets}
        val warmups=sets.filter{it.type==DraftSetType.WARMUP}
        assertTrue(warmups.isNotEmpty())
        assertTrue(warmups.all {
            it.load==DraftSetLoad.Verified(LoadUnit.LB,
                LoadBasis.DUMBBELL_PER_HAND,n("20")) &&
                it.targetReps==RepBand(5,5) && it.targetRir==null
        })
        assertEquals(prescribed.composer.audit.muscles,
            prescribed.finalAudit?.muscles)
        val noLighter=CoachFirstProgramStartDomain.review(request(mapOf(
            "bench" to n("20"),"row" to n("20"),"squat" to n("20"))))
        assertTrue(checkNotNull(noLighter.prescription).sessions
            .flatMap{it.exercises}.flatMap{it.sets}
            .none{it.type==DraftSetType.WARMUP})
    }

    @Test fun savedV1PolicyReplaysWithoutNewWarmupsOrChangedAttribution() {
        val legacy=CoachFirstProgramStartDomain.review(request(mapOf(
            "bench" to n("25"),"row" to n("25"),"squat" to n("25")))
            .copy(policyVersion=CoachFirstProgramStartDomain.LEGACY_POLICY_VERSION,
                nonSetSecondsPerDay=2100))
        assertEquals(FirstProgramStartStatus.PRESCRIBED_DRAFT_FOR_REVIEW,legacy.status)
        assertEquals("first-program-reference-v1",legacy.policy?.policyId)
        assertEquals("v1",legacy.policy?.revision)
        assertTrue(checkNotNull(legacy.prescription).sessions
            .flatMap{it.exercises}.flatMap{it.sets}
            .none{it.type==DraftSetType.WARMUP})
        val saved=CoachFirstProgramDraftConversionDomain.propose(
            legacy,source,movements,"old-first","My first program",4)
        assertTrue(checkNotNull(saved.cycle).authorNote.orEmpty()
            .startsWith("Provisional first-program-reference-v1 plan."))
        val unknown=CoachFirstProgramStartDomain.review(request()
            .copy(policyVersion="future-unknown"))
        assertEquals(FirstProgramStartStatus.INVALID_CALIBRATION,unknown.status)
        assertNull(unknown.policy)
    }

    @Test fun separatelyTimedWarmupsCannotBypassTheFullSessionReview() {
        val result=CoachFirstProgramStartDomain.review(request(mapOf(
            "bench" to n("25"),"row" to n("25"),"squat" to n("25")))
            .copy(nonSetSecondsPerDay=1800))
        assertEquals(FirstProgramStartStatus.DRAFT_NEEDS_REVIEW,result.status)
        val draft=checkNotNull(result.prescription)
        assertEquals(PrescriptionDraftStatus.UNRESOLVED,draft.status)
        assertTrue(checkNotNull(draft.finalAudit).daysOutsidePreferredWindow.isNotEmpty())
        assertFalse(result.maySaveProgram)
        assertFalse(result.mayIssueWorkout)
    }

}
