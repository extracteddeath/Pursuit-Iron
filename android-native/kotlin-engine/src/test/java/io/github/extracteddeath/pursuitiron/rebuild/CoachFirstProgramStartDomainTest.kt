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
        nonSetSecondsPerDay=2100)

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
}
