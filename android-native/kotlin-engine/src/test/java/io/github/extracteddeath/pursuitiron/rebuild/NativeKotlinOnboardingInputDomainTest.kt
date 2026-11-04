package io.github.extracteddeath.pursuitiron.rebuild

import org.junit.Assert.*
import org.junit.Test

class NativeKotlinOnboardingInputDomainTest {
    private fun blank()=NativeKotlinOnboardingInput(
        profileRevision=null,athleteId=null,goal=null,experience=null,
        split=null,trainingWeekdays=null,timezoneId=null,
        sessionTimeBucket=null,hardSessionLimit=null,gymId=null,
        unit=null,confirmedStock=null,confirmedFacilities=null,
        confirmedAttachments=null,allowBodyweight=null,
        allowSupersets=null,allowTricepsOnLower=null,
        maxBarbellExercisesPerDay=null,focusedMuscles=null,
        reducedMuscles=null,excludedExercises=null,lockedDays=null,
        hardDays=null,longDays=null,declaredRestDays=null)
    private fun bodyweightReady()=blank().copy(
        profileRevision="profile-1",athleteId="athlete-1",
        goal=AthleteTrainingGoal.HYPERTROPHY,
        experience=AthleteExperience.ADVANCED,
        split=ComposerSplit.FULL_BODY,
        trainingWeekdays=setOf(0,2,4),
        timezoneId="America/New_York",
        sessionTimeBucket=SessionTimeBucket.SIXTY_TO_NINETY,
        hardSessionLimit=HardSessionLimit.NO_HARD_LIMIT,
        gymId="gym-1",unit=LoadUnit.LB,
        confirmedStock=emptyMap(),confirmedFacilities=emptySet(),
        confirmedAttachments=emptySet(),allowBodyweight=true,
        allowSupersets=false,allowTricepsOnLower=false,
        maxBarbellExercisesPerDay=0,
        focusedMuscles=emptySet(),reducedMuscles=emptySet(),
        excludedExercises=emptySet(),lockedDays=emptySet(),
        hardDays=emptySet(),longDays=emptySet(),
        declaredRestDays=setOf(1,3,5,6))

    @Test fun blankOnboardingNeverCreatesPreferencesOrAthleteIdentity() {
        val input=blank()
        val review=NativeKotlinOnboardingInputDomain.review(input)
        assertEquals(AthleteSetupStatus.NEEDS_INPUT,review.status)
        assertNull(review.reviewed)
        assertFalse(review.mayIssueWorkout)
        assertFalse(review.maySaveProfile)
        assertNull(input.toDraft().gym?.confirmedStock)
        assertNull(NativeKotlinOnboardingInputDomain.declareRestDays(null,true))
        assertNull(NativeKotlinOnboardingInputDomain.declareRestDays(
            setOf(0,2,4),false))
    }

    @Test fun explicitlyConfirmedEmptyStockIsValidOnlyWithBodyweight() {
        val ready=NativeKotlinOnboardingInputDomain.review(bodyweightReady())
        assertEquals(AthleteSetupStatus.READY_FOR_PROGRAM_REVIEW,ready.status)
        assertEquals(emptyMap<String,VerifiedLoadStock>(),
            ready.reviewed!!.gym.confirmedStock)
        assertFalse(ready.mayIssueWorkout)
        val disallowed=NativeKotlinOnboardingInputDomain.review(
            bodyweightReady().copy(allowBodyweight=false))
        assertEquals(AthleteSetupStatus.CONSTRAINT_CONFLICT,disallowed.status)
        assertTrue(disallowed.issues.any {
            it.field=="gym.confirmedStock"
        })
    }

    @Test fun trainingCalendarMustBeAnExplicitAthleteDeclaration() {
        val selected=setOf(0,2,4)
        val acknowledged=NativeKotlinOnboardingInputDomain.declareRestDays(
            selected,true)
        assertEquals(setOf(1,3,5,6),acknowledged)
        assertEquals(AthleteSetupStatus.NEEDS_INPUT,
            NativeKotlinOnboardingInputDomain.review(
                bodyweightReady().copy(declaredRestDays=null)).status)
        assertEquals(AthleteSetupStatus.CONSTRAINT_CONFLICT,
            NativeKotlinOnboardingInputDomain.review(
                bodyweightReady().copy(
                    declaredRestDays=setOf(0,1,3,5,6))).status)
        assertNull(NativeKotlinOnboardingInputDomain.declareRestDays(
            setOf(0,7),true))
    }

    @Test fun sessionAndRoleChoicesArePreservedAndNeverDefaulted() {
        val source=bodyweightReady().copy(
            focusedMuscles=setOf(TrainingMuscle.CHEST),
            reducedMuscles=setOf(TrainingMuscle.CALVES),
            allowSupersets=true,allowTricepsOnLower=true,
            maxBarbellExercisesPerDay=2,
            lockedDays=setOf(0),hardDays=setOf(2),longDays=setOf(4))
        val reviewed=NativeKotlinOnboardingInputDomain.review(source)
        assertEquals(AthleteSetupStatus.READY_FOR_PROGRAM_REVIEW,reviewed.status)
        val athlete=reviewed.reviewed!!
        assertEquals(setOf(TrainingMuscle.CHEST),athlete.focusedMuscles)
        assertEquals(setOf(TrainingMuscle.CALVES),athlete.reducedMuscles)
        assertEquals(setOf(0),athlete.lockedTrainingDays)
        assertEquals(setOf(2),athlete.hardTrainingDays)
        assertEquals(setOf(4),athlete.longTrainingDays)
        assertTrue(athlete.allowSupersets)
        assertEquals(2,athlete.maxBarbellExercisesPerDay)
        assertEquals(AthleteSetupStatus.NEEDS_INPUT,
            NativeKotlinOnboardingInputDomain.review(source.copy(
                hardSessionLimit=null)).status)
    }
}
