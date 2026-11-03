package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class CoachAthleteSetupDomainTest {
    private fun n(value:String)=BigDecimal(value)
    private val dumbbells=VerifiedLoadStock.Totals(LoadUnit.LB,
        LoadBasis.DUMBBELL_PER_HAND,listOf(n("25"),n("30"),n("35")))
    private fun gym()=GymProfileDraft("gym-main",LoadUnit.LB,
        mapOf("dumbbell-rack" to dumbbells),emptySet(),emptySet())
    private fun complete()=AthleteSetupDraft(
        profileRevision="revision-1",athleteProfileId="athlete-test",
        goal=AthleteTrainingGoal.POWERBUILDING,
        experience=AthleteExperience.ADVANCED,
        split=ComposerSplit.UPPER_LOWER,
        trainingWeekdays=setOf(0,1,3,5),timezoneId="America/New_York",
        sessionTimeBucket=SessionTimeBucket.SIXTY_TO_NINETY,
        hardSessionLimit=HardSessionLimit.NO_HARD_LIMIT,
        gym=gym(),allowBodyweight=true,allowSupersets=false,
        allowTricepsOnLower=true,maxBarbellExercisesPerDay=1,
        focusedMuscles=setOf(TrainingMuscle.CHEST),
        reducedMuscles=setOf(TrainingMuscle.CALVES),
        excludedExerciseIds=emptySet(),
        lockedTrainingDays=setOf(0),hardTrainingDays=setOf(3),
        longTrainingDays=setOf(5),declaredRestDays=setOf(2,4,6))

    @Test fun noAthleteInputsAreInventedAndMissingFieldsStayDistinct() {
        val result=CoachAthleteSetupDomain.review(AthleteSetupDraft())
        assertEquals(AthleteSetupStatus.NEEDS_INPUT,result.status)
        assertNull(result.reviewed)
        assertFalse(result.mayReviewProgramCandidate)
        assertFalse(result.maySaveProfile)
        assertFalse(result.mayIssueWorkout)
        for(field in listOf("goal","experience","split","trainingWeekdays",
            "timezoneId","hardSessionLimit","allowBodyweight",
            "maxBarbellExercisesPerDay","gym","declaredRestDays")) {
            assertTrue(field,result.issues.any {
                it.field==field && it.kind==AthleteSetupIssueKind.MISSING
            })
        }
    }

    @Test fun completeVersionedSetupCanRequestReviewButNeverIssueOrSave() {
        val result=CoachAthleteSetupDomain.review(complete())
        assertEquals(AthleteSetupStatus.READY_FOR_PROGRAM_REVIEW,result.status)
        assertTrue(result.issues.isEmpty())
        assertTrue(result.mayReviewProgramCandidate)
        assertFalse(result.mayIssueWorkout)
        assertFalse(result.maySaveProfile)
        val setup=checkNotNull(result.reviewed)
        assertEquals(AthleteTrainingGoal.POWERBUILDING,setup.goal)
        assertEquals(AthleteExperience.ADVANCED,setup.experience)
        assertEquals(listOf(0,1,3,5),setup.trainingWeekdays.sorted())
        assertEquals(60,setup.preferredMinimumMinutes)
        assertEquals(90,setup.preferredMaximumMinutes)
        assertNull(setup.explicitHardMaximumMinutes)
        assertFalse(setup.allowSupersets)
        assertTrue(setup.allowTricepsOnLower)
        assertEquals(1,setup.maxBarbellExercisesPerDay)
        assertEquals(LoadUnit.LB,setup.gym.unit)
    }

    @Test fun sessionBucketIsPreferenceNotAnImplicitHardCap() {
        val twoHours=CoachAthleteSetupDomain.review(complete().copy(
            sessionTimeBucket=SessionTimeBucket.TWO_HOURS_PLUS))
        assertTrue(twoHours.mayReviewProgramCandidate)
        assertEquals(120,twoHours.reviewed?.preferredMinimumMinutes)
        assertNull(twoHours.reviewed?.preferredMaximumMinutes)
        assertNull(twoHours.reviewed?.explicitHardMaximumMinutes)
        val capped=CoachAthleteSetupDomain.review(complete().copy(
            hardSessionLimit=HardSessionLimit.Minutes(110)))
        assertTrue(capped.mayReviewProgramCandidate)
        assertEquals(110,capped.reviewed?.explicitHardMaximumMinutes)
        val contradictory=CoachAthleteSetupDomain.review(complete().copy(
            hardSessionLimit=HardSessionLimit.Minutes(45)))
        assertEquals(AthleteSetupStatus.CONSTRAINT_CONFLICT,contradictory.status)
        assertTrue(contradictory.issues.any {
            it.field=="hardSessionLimit" &&
                it.kind==AthleteSetupIssueKind.CONFLICT
        })
    }

    @Test fun splitScheduleAndWeekdayConflictsAreExplicit() {
        val invalid=listOf(
            complete().copy(split=ComposerSplit.ULPPL),
            complete().copy(trainingWeekdays=setOf(0,1)),
            complete().copy(trainingWeekdays=setOf(0,1,3,7)),
            complete().copy(declaredRestDays=setOf(0,2)),
            complete().copy(lockedTrainingDays=setOf(2)),
            complete().copy(hardTrainingDays=setOf(3),
                longTrainingDays=setOf(3)),
            complete().copy(timezoneId="Atlantis/Unknown"))
        invalid.forEach {draft->
            val result=CoachAthleteSetupDomain.review(draft)
            assertEquals(AthleteSetupStatus.CONSTRAINT_CONFLICT,result.status)
            assertNull(result.reviewed)
        }
        val ulppl=CoachAthleteSetupDomain.review(complete().copy(
            split=ComposerSplit.ULPPL,trainingWeekdays=setOf(0,1,2,3,4),
            declaredRestDays=setOf(5,6),lockedTrainingDays=setOf(0),
            hardTrainingDays=setOf(3),longTrainingDays=setOf(4)))
        assertTrue(ulppl.mayReviewProgramCandidate)
    }

    @Test fun focusReducedAndEquipmentPermissionsDoNotHideConflicts() {
        val overlapping=CoachAthleteSetupDomain.review(complete().copy(
            reducedMuscles=setOf(TrainingMuscle.CHEST)))
        assertTrue(overlapping.issues.any {it.field=="focusedMuscles"})
        val noGear=CoachAthleteSetupDomain.review(complete().copy(
            allowBodyweight=false,gym=gym().copy(confirmedStock=emptyMap())))
        assertTrue(noGear.issues.any {it.field=="gym.confirmedStock"})
        val explicitBodyweight=CoachAthleteSetupDomain.review(complete().copy(
            allowBodyweight=true,gym=gym().copy(confirmedStock=emptyMap())))
        assertTrue(explicitBodyweight.mayReviewProgramCandidate)
        val malformed=CoachAthleteSetupDomain.review(complete().copy(
            excludedExerciseIds=setOf("not a valid id")))
        assertTrue(malformed.issues.any {it.field=="excludedExerciseIds"})
    }

    @Test fun exactGymUnitsAndLoadBasisArePreservedAndChecked() {
        val mixed=CoachAthleteSetupDomain.review(complete().copy(
            gym=gym().copy(confirmedStock=mapOf("dumbbell-rack" to
                VerifiedLoadStock.Totals(LoadUnit.KG,
                    LoadBasis.DUMBBELL_PER_HAND,listOf(n("10")))))))
        assertTrue(mixed.issues.any {
            it.field=="gym.confirmedStock.dumbbell-rack" &&
                it.kind==AthleteSetupIssueKind.CONFLICT
        })
        val invalidInventory=CoachAthleteSetupDomain.review(complete().copy(
            gym=gym().copy(confirmedStock=mapOf("dumbbell-rack" to
                VerifiedLoadStock.Totals(LoadUnit.LB,
                    LoadBasis.DUMBBELL_PER_HAND,listOf(n("30"),n("30.0")))))))
        assertTrue(invalidInventory.issues.any {
            it.kind==AthleteSetupIssueKind.INVENTORY_INVALID
        })
        val valid=checkNotNull(CoachAthleteSetupDomain.review(complete()).reviewed)
        assertEquals(LoadBasis.DUMBBELL_PER_HAND,
            valid.gym.confirmedStock.getValue("dumbbell-rack").basis)
    }

    @Test fun gymInventoryIsFrozenAgainstLaterUserDraftMutation() {
        val loads=mutableListOf(n("25"),n("30"))
        val units=mutableMapOf<String,VerifiedLoadStock>(
            "dumbbell-rack" to VerifiedLoadStock.Totals(LoadUnit.LB,
                LoadBasis.DUMBBELL_PER_HAND,loads))
        val facilities=mutableSetOf("flat-bench")
        val draft=complete().copy(gym=gym().copy(
            confirmedStock=units,availableFacilities=facilities))
        val r=CoachAthleteSetupDomain.review(draft)
        assertTrue(r.mayReviewProgramCandidate)
        val before=checkNotNull(r.reviewed)
        loads.add(n("35"))
        units.clear()
        facilities.clear()
        assertEquals(1,before.gym.confirmedStock.size)
        assertEquals(setOf("flat-bench"),before.gym.availableFacilities)
        val retained=before.gym.confirmedStock.getValue("dumbbell-rack")
            as VerifiedLoadStock.Totals
        assertEquals(listOf(n("25"),n("30")),retained.loads)
        assertThrows(UnsupportedOperationException::class.java) {
            @Suppress("UNCHECKED_CAST")
            val editable=before.gym.confirmedStock as MutableMap<String,VerifiedLoadStock>
            editable.clear()
        }
        assertThrows(UnsupportedOperationException::class.java) {
            @Suppress("UNCHECKED_CAST")
            val editable=retained.loads as MutableList<BigDecimal>
            editable.clear()
        }
        assertThrows(UnsupportedOperationException::class.java) {
            @Suppress("UNCHECKED_CAST")
            val editable=before.trainingWeekdays as MutableSet<Int>
            editable.clear()
        }
    }

    @Test fun reviewIsDeterministicAndNeverModifiesTheInput() {
        val draft=complete()
        val a=CoachAthleteSetupDomain.review(draft)
        val b=CoachAthleteSetupDomain.review(draft)
        assertEquals(a,b)
        assertEquals("revision-1",draft.profileRevision)
        assertEquals(setOf(0,1,3,5),draft.trainingWeekdays)
        assertEquals(4,draft.trainingWeekdays?.size)
    }
}
