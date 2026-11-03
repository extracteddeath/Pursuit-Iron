package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class CoachGoalProgramPolicyDomainTest {
    private fun n(s:String)=BigDecimal(s)
    private fun athlete(
        goal:AthleteTrainingGoal=AthleteTrainingGoal.HYPERTROPHY,
        experience:AthleteExperience=AthleteExperience.ADVANCED,
        focused:Set<TrainingMuscle> =emptySet(),
        reduced:Set<TrainingMuscle> =emptySet()
    ):ReviewedAthleteSetup {
        val stock=VerifiedLoadStock.Totals(LoadUnit.LB,
            LoadBasis.DUMBBELL_PER_HAND,listOf(n("20"),n("25")))
        val setup=AthleteSetupDraft(
            profileRevision="athlete-rev-2",athleteProfileId="athlete-7",
            goal=goal,experience=experience,
            split=ComposerSplit.FULL_BODY,
            trainingWeekdays=setOf(0,2,4),timezoneId="America/New_York",
            sessionTimeBucket=SessionTimeBucket.SIXTY_TO_NINETY,
            hardSessionLimit=HardSessionLimit.NO_HARD_LIMIT,
            gym=GymProfileDraft("gym-1",LoadUnit.LB,
                mapOf("db" to stock),emptySet(),emptySet()),
            allowBodyweight=true,allowSupersets=false,
            allowTricepsOnLower=false,maxBarbellExercisesPerDay=2,
            focusedMuscles=focused,reducedMuscles=reduced,
            excludedExerciseIds=emptySet(),lockedTrainingDays=emptySet(),
            hardTrainingDays=emptySet(),longTrainingDays=emptySet(),
            declaredRestDays=setOf(1,3,5,6))
        return checkNotNull(CoachAthleteSetupDomain.review(setup).reviewed)
    }
    private fun bounds()=TrainingMuscle.entries.map {muscle ->
        val active=muscle in setOf(TrainingMuscle.CHEST,
            TrainingMuscle.BICEPS,TrainingMuscle.QUADS)
        DeclaredMuscleDoseBounds(muscle,
            n(if(active)"2"else"0"),n(if(active)"4"else"0"),
            n(if(active)"8"else"0"),if(active)1 else 0)
    }
    private fun intents(
        purpose:ComposerSessionPurpose=ComposerSessionPurpose.HYPERTROPHY,
        main:String?=null
    )=listOf(0,2,4).map { AthleteCalendarIntent(it,purpose,main) }

    private fun policy(
        athlete:ReviewedAthleteSetup,
        phase:TrainingPhase=TrainingPhase.HYPERTROPHY,
        before:List<DeclaredMuscleDoseBounds> =bounds(),
        after:List<DeclaredMuscleDoseBounds> =before,
        purpose:ComposerSessionPurpose=ComposerSessionPurpose.HYPERTROPHY
    )=AuthoredGoalProgramPolicy(
        "coach-authored","revision-3",
        "Exercises and weekly dose are explicitly reviewed for this athlete.",
        athlete.athleteProfileId,athlete.profileRevision,
        athlete.goal,athlete.experience,athlete.split,phase,
        athlete.trainingWeekdays.size,before,after,mapOf(purpose to 1))

    @Test fun noPolicyCannotBeMisrepresentedAsGoalOrExperienceAware() {
        val a=athlete()
        val result=CoachGoalProgramPolicyDomain.review(
            a,TrainingPhase.HYPERTROPHY,bounds(),intents(),null)
        assertEquals(GoalPolicyStatus.UNRESOLVED,result.status)
        assertTrue(result.issues.any {it.kind==GoalPolicyIssueKind.POLICY_MISSING})
        assertFalse(result.mayIssueWorkout)
        assertFalse(result.maySaveProgram)
    }

    @Test fun fullyMatchedHypertrophyPolicyIsReviewableButCannotIssue() {
        val a=athlete()
        val draft=policy(a)
        val result=CoachGoalProgramPolicyDomain.review(
            a,TrainingPhase.HYPERTROPHY,bounds(),intents(),draft)
        assertEquals(GoalPolicyStatus.MATCHED_FOR_REVIEW,result.status)
        assertTrue(result.issues.isEmpty())
        assertFalse(result.mayIssueWorkout)
        // Numeric equality: no false mismatch merely due to decimal scale.
        val scaled=bounds().map {
            it.copy(mev=n(it.mev.toPlainString()+".0"))
        }
        assertEquals(GoalPolicyStatus.MATCHED_FOR_REVIEW,
            CoachGoalProgramPolicyDomain.review(
                a,TrainingPhase.HYPERTROPHY,scaled,intents(),draft).status)
    }

    @Test fun wrongAthleteRevisionGoalExperienceSplitAndPhaseAreRejected() {
        val a=athlete()
        val good=policy(a)
        val changed=listOf(
            good.copy(athleteId="other-athlete"),
            good.copy(athleteRevision="old-revision"),
            good.copy(goal=AthleteTrainingGoal.STRENGTH),
            good.copy(experience=AthleteExperience.BEGINNER),
            good.copy(split=ComposerSplit.UPPER_LOWER),
            good.copy(trainingDays=6),
            good.copy(phase=TrainingPhase.PEAK)
        )
        for(item in changed) {
            val review=CoachGoalProgramPolicyDomain.review(
                a,TrainingPhase.HYPERTROPHY,bounds(),intents(),item)
            assertEquals(GoalPolicyStatus.UNRESOLVED,review.status)
            assertTrue(review.issues.any {
                it.kind==GoalPolicyIssueKind.CONTEXT_MISMATCH
            })
        }
        val blank=CoachGoalProgramPolicyDomain.review(
            a,TrainingPhase.HYPERTROPHY,bounds(),intents(),
            good.copy(rationale=" ",policyId="id has spaces"))
        assertTrue(blank.issues.any {
            it.kind==GoalPolicyIssueKind.INVALID_PROVENANCE
        })
    }

    @Test fun differentDoseOrMissingMuscleCannotBePassedAsAuthoredTarget() {
        val a=athlete()
        val p=policy(a)
        val changed=bounds().map {
            if(it.muscle==TrainingMuscle.CHEST)
                it.copy(mav=n("5")) else it
        }
        val wrong=CoachGoalProgramPolicyDomain.review(
            a,TrainingPhase.HYPERTROPHY,changed,intents(),p)
        assertEquals(GoalPolicyStatus.UNRESOLVED,wrong.status)
        assertTrue(wrong.issues.any {it.kind==GoalPolicyIssueKind.INVALID_DOSE})
        val missing=CoachGoalProgramPolicyDomain.review(
            a,TrainingPhase.HYPERTROPHY,bounds().drop(1),intents(),p)
        assertTrue(missing.issues.any {it.kind==GoalPolicyIssueKind.INVALID_DOSE})
        val broken=p.copy(selectedBounds=bounds().map {
            if(it.muscle==TrainingMuscle.CHEST)
                it.copy(mev=n("9"),mav=n("4")) else it
        })
        assertTrue(CoachGoalProgramPolicyDomain.review(a,
            TrainingPhase.HYPERTROPHY,bounds(),intents(),broken).issues.any {
                it.kind==GoalPolicyIssueKind.INVALID_DOSE
            })
    }

    @Test fun focusedAndReducedMusclesMustActuallyChangeDoseInIntendedDirection() {
        val a=athlete(focused=setOf(TrainingMuscle.CHEST),
            reduced=setOf(TrainingMuscle.BICEPS))
        val before=bounds()
        val fake=CoachGoalProgramPolicyDomain.review(
            a,TrainingPhase.HYPERTROPHY,before,intents(),policy(a,before=before))
        assertEquals(2,fake.issues.count {
            it.kind==GoalPolicyIssueKind.PRIORITY_NOT_APPLIED
        })
        val selected=before.map {
            when(it.muscle) {
                TrainingMuscle.CHEST -> it.copy(mav=n("6"),minimumDirectDays=2)
                TrainingMuscle.BICEPS -> it.copy(mav=n("3"),mev=n("1"))
                else -> it
            }
        }
        val real=CoachGoalProgramPolicyDomain.review(
            a,TrainingPhase.HYPERTROPHY,selected,intents(),
            policy(a,before=before,after=selected))
        assertEquals(GoalPolicyStatus.MATCHED_FOR_REVIEW,real.status)
        val inverted=selected.map {
            when(it.muscle) {
                TrainingMuscle.CHEST -> it.copy(mav=n("3"))
                TrainingMuscle.BICEPS -> it.copy(mav=n("6"))
                else -> it
            }
        }
        val invalid=CoachGoalProgramPolicyDomain.review(
            a,TrainingPhase.HYPERTROPHY,inverted,intents(),
            policy(a,before=before,after=inverted))
        assertTrue(invalid.issues.count {
            it.kind==GoalPolicyIssueKind.PRIORITY_NOT_APPLIED
        }>=2)
    }

    @Test fun phaseSpecificSkillDaysNeedAnAuthoredNamedMainLift() {
        val a=athlete(goal=AthleteTrainingGoal.POWERBUILDING)
        val strength=policy(a,phase=TrainingPhase.STRENGTH,
            purpose=ComposerSessionPurpose.STRENGTH_SKILL)
        val noMain=CoachGoalProgramPolicyDomain.review(
            a,TrainingPhase.STRENGTH,bounds(),
            intents(ComposerSessionPurpose.STRENGTH_SKILL),strength)
        assertTrue(noMain.issues.any {
            it.kind==GoalPolicyIssueKind.PRIMARY_SKILL_MISSING
        })
        val valid=CoachGoalProgramPolicyDomain.review(
            a,TrainingPhase.STRENGTH,bounds(),
            intents(ComposerSessionPurpose.STRENGTH_SKILL,"bench-press"),strength)
        assertEquals(GoalPolicyStatus.MATCHED_FOR_REVIEW,valid.status)
        val wrongPurpose=CoachGoalProgramPolicyDomain.review(
            a,TrainingPhase.STRENGTH,bounds(),intents(),
            strength)
        assertTrue(wrongPurpose.issues.any {
            it.kind==GoalPolicyIssueKind.DAY_PURPOSE_MISSING
        })
        val peak=CoachGoalProgramPolicyDomain.review(
            a,TrainingPhase.PEAK,bounds(),
            intents(ComposerSessionPurpose.STRENGTH_SKILL,"bench-press"),
            strength.copy(phase=TrainingPhase.PEAK))
        assertEquals(GoalPolicyStatus.MATCHED_FOR_REVIEW,peak.status)
    }

    @Test fun authoredMinimumNumberOfPurposeDaysMustBePresent() {
        val a=athlete()
        val p=policy(a).copy(
            minimumPurposeDays=mapOf(ComposerSessionPurpose.HYPERTROPHY to 3))
        val mixed=listOf(
            AthleteCalendarIntent(0,ComposerSessionPurpose.HYPERTROPHY),
            AthleteCalendarIntent(2,ComposerSessionPurpose.GENERAL_PREPARATION),
            AthleteCalendarIntent(4,ComposerSessionPurpose.HYPERTROPHY))
        val result=CoachGoalProgramPolicyDomain.review(
            a,TrainingPhase.HYPERTROPHY,bounds(),mixed,p)
        assertTrue(result.issues.any {
            it.kind==GoalPolicyIssueKind.DAY_PURPOSE_MISSING
        })
    }

    @Test fun impossibleOrAmbiguousPrimaryFrequencyIsNotSilentlyClamped() {
        val a=athlete()
        val p=policy(a)
        for(map in listOf(
            mapOf("bench" to 0),
            mapOf("bench" to 4),
            mapOf("bad id" to 1))) {
            val review=CoachGoalProgramPolicyDomain.review(
                a,TrainingPhase.HYPERTROPHY,bounds(),intents(),
                p.copy(primaryLiftMinimumDays=map))
            assertTrue(review.issues.any {
                it.kind==GoalPolicyIssueKind.INVALID_LIFT_FREQUENCY
            })
            assertEquals(GoalPolicyStatus.UNRESOLVED,review.status)
        }
    }

    @Test fun returnedFindingsCannotBeMutatedAndAreRepeatable() {
        val a=athlete()
        val a1=CoachGoalProgramPolicyDomain.review(
            a,TrainingPhase.HYPERTROPHY,bounds(),intents(),null)
        val a2=CoachGoalProgramPolicyDomain.review(
            a,TrainingPhase.HYPERTROPHY,bounds(),intents(),null)
        assertEquals(a1,a2)
        assertThrows(UnsupportedOperationException::class.java) {
            @Suppress("UNCHECKED_CAST")
            val list=a1.issues as MutableList<GoalPolicyIssue>
            list.clear()
        }
    }
}
