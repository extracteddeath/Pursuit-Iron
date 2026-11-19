package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

/**
 * Generated plans must remain tied to the reviewed athlete's volume policy,
 * training restrictions and physical gym, not merely a mathematically
 * feasible week supplied by a stale caller.
 */
class GoalRealizationSourceScopeTest {
    private val fixture=GeneratedAdaptiveBlockFixture

    @Test fun realKotlinGeneratedWeekKeepsItsExactPolicyAndGymBinding() {
        val input=fixture.generation()
        val generated=CoachAthleteProgramGenerator.generate(input)
        assertEquals(AthleteGenerationStatus.GENERATED_FOR_REVIEW,generated.status)
        val catalog=checkNotNull(generated.candidate?.catalogReview)
        val profile=checkNotNull(input.setup.reviewed)
        val policy=checkNotNull(input.policy)
        val result=CoachGoalProgramRealizationDomain.review(
            profile,input.phase,generated.intents,policy,catalog)
        assertEquals(result.issues.toString(),
            GoalRealizationStatus.DELIVERED_FOR_REVIEW,result.status)
        assertFalse(result.maySaveProgram)
        assertFalse(result.mayIssueWorkout)
    }

    @Test fun goalSourceCannotSubstituteAnotherDosePolicyOrAthleteConstraints() {
        val input=fixture.generation()
        val generated=CoachAthleteProgramGenerator.generate(input)
        val proposed=checkNotNull(generated.candidate?.catalogReview)
        val template=checkNotNull(proposed.composedRequest)
        val original=checkNotNull(proposed.draft)
        val athlete=checkNotNull(input.setup.reviewed)
        val policy=checkNotNull(input.policy)
        fun review(
            candidate:CatalogWeekReview=proposed,
            profile:ReviewedAthleteSetup=athlete,
            goalPolicy:AuthoredGoalProgramPolicy=policy
        )=CoachGoalProgramRealizationDomain.review(
            profile,input.phase,generated.intents,goalPolicy,candidate)

        val relaxed=template.bounds.map {bound ->
            if(bound.muscle==TrainingMuscle.CHEST)
                bound.copy(mev=BigDecimal.ZERO) else bound
        }
        val forged=listOf(
            review(goalPolicy=policy.copy(goal=AthleteTrainingGoal.STRENGTH)),
            review(goalPolicy=policy.copy(athleteRevision="older")),
            review(proposed.copy(composedRequest=template.copy(bounds=relaxed))),
            review(proposed.copy(draft=original.copy(week=
                original.week.copy(bounds=relaxed)))),
            review(proposed.copy(composedRequest=template.copy(
                maximumBarbellExercisesPerDay=null))),
            review(proposed.copy(composedRequest=template.copy(
                allowDirectTricepsOnLower=true))),
            review(proposed.copy(composedRequest=template.copy(
                sessionIntents=emptyList()))),
            review(proposed.copy(composedRequest=template.copy(
                days=template.days.map {day->day.copy(
                    window=day.window.copy(preferredMinimumMinutes=42))}))),
            review(profile=athlete.copy(split=ComposerSplit.UPPER_LOWER))
        )
        forged.forEachIndexed {index,result ->
            assertEquals("Invalid source "+index+": "+result.issues,
                GoalRealizationStatus.UNRESOLVED,result.status)
            assertTrue("No policy-source refusal "+index+": "+result.issues,
                result.issues.any {
                    it.kind==GoalRealizationIssueKind.UNVERIFIED_CANDIDATE &&
                        it.field=="week.policy"
                })
            assertFalse(result.maySaveProgram)
            assertFalse(result.mayIssueWorkout)
        }
    }

    @Test fun exerciseCannotBorrowAnotherGymOrDisabledBodyweight() {
        val input=fixture.generation()
        val generated=CoachAthleteProgramGenerator.generate(input)
        val candidate=checkNotNull(generated.candidate?.catalogReview)
        val template=checkNotNull(candidate.composedRequest)
        val athlete=checkNotNull(input.setup.reviewed)
        val policy=checkNotNull(input.policy)
        fun inspect(
            candidate:CatalogWeekReview,
            profile:ReviewedAthleteSetup=athlete
        )=CoachGoalProgramRealizationDomain.review(
            profile,input.phase,generated.intents,policy,candidate)
        val real=checkNotNull(candidate.draft)
        val first=real.visits.first().exerciseId
        val excluded=inspect(candidate,athlete.copy(
            excludedExerciseIds=setOf(first)))
        val wrongGym=inspect(candidate,athlete.copy(gym=
            athlete.gym.copy(confirmedStock=emptyMap())))
        val bodyweight=inspect(candidate.copy(composedRequest=template.copy(
            exercises=template.exercises.map {exercise ->
                if(exercise.exerciseId==first)exercise.copy(
                    equipment=ComposerEquipment.Bodyweight)
                else exercise
            })))
        val impossibleLoad=inspect(candidate.copy(composedRequest=template.copy(
            exercises=template.exercises.map {exercise ->
                if(exercise.exerciseId!=first)exercise
                else {
                    val verified=exercise.equipment as ComposerEquipment.Verified
                    exercise.copy(equipment=verified.copy(
                        plannedLoad=BigDecimal("27")))
                }
            })))
        listOf(excluded,wrongGym,bodyweight,impossibleLoad)
            .forEachIndexed {index,result ->
                assertEquals("Unverified gym movement "+index+": "+result.issues,
                    GoalRealizationStatus.UNRESOLVED,result.status)
                assertTrue(result.issues.any {
                    it.kind==GoalRealizationIssueKind.UNVERIFIED_CANDIDATE &&
                        it.field=="week.gym"
                })
                assertFalse(result.mayIssueWorkout)
            }
    }
}
