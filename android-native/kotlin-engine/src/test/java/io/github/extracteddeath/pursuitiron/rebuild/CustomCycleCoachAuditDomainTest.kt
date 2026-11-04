package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class CustomCycleCoachAuditDomainTest {
    private fun n(s:String)=BigDecimal(s)
    private fun setup(
        selected:Set<Int> = setOf(0,2,4),
        excluded:Set<String> = emptySet()
    ):AthleteSetupReview = CoachAthleteSetupDomain.review(AthleteSetupDraft(
        profileRevision="rev-1",athleteProfileId="athlete-1",
        goal=AthleteTrainingGoal.HYPERTROPHY,
        experience=AthleteExperience.ADVANCED,
        split=ComposerSplit.FULL_BODY,
        trainingWeekdays=selected,timezoneId="America/New_York",
        sessionTimeBucket=SessionTimeBucket.FORTY_TO_SIXTY,
        hardSessionLimit=HardSessionLimit.Minutes(60),
        gym=GymProfileDraft("gym-1",LoadUnit.LB,emptyMap(),
            emptySet(),emptySet()),
        allowBodyweight=true,allowSupersets=false,
        allowTricepsOnLower=false,maxBarbellExercisesPerDay=0,
        focusedMuscles=emptySet(),reducedMuscles=emptySet(),
        excludedExerciseIds=excluded,lockedTrainingDays=emptySet(),
        hardTrainingDays=emptySet(),longTrainingDays=emptySet(),
        declaredRestDays=(0..6).filterNot{it in selected}.toSet()
    ))
    private fun authoredSet()=CustomAuthoredSet(
        DraftSetType.WORKING,DraftSetLoad.ExplicitBodyweight,
        RepBand(10,15),2,55,75)
    private fun authoredDay(day:Int)=CustomTrainingDay(
        day,"Core $day",ComposerSessionPurpose.HYPERTROPHY,
        listOf(CustomExerciseSlot("slot-$day","bodyweight_crunch",
            ComposerRole.ACCESSORY,listOf(authoredSet()))))
    private fun cycle(
        weeks:Int=3,
        days:List<CustomTrainingDay>=listOf(0,2,4).map(::authoredDay),
        overrides:Map<String,List<CustomWeekSlotOverride>>=emptyMap()
    )=CustomTrainingCycle("cycle-1",4,"athlete-1",
        AthleteTrainingGoal.HYPERTROPHY,"My fully custom plan",
        listOf(CustomTrainingBlock("block-1","Foundation",
            TrainingPhase.HYPERTROPHY,CustomBlockPurpose.TRAINING,
            weeks,CustomSplitIdentity.Freeform("Custom core"),days)),
        overrides)
    private fun bounds(absMev:String="3",absMrv:String="5")=
        TrainingMuscle.entries.map {m ->
            DeclaredMuscleDoseBounds(
                muscle=m,
                mev=if(m==TrainingMuscle.ABS)n(absMev) else n("0"),
                mav=if(m==TrainingMuscle.ABS)n("4") else n("0"),
                mrv=if(m==TrainingMuscle.ABS)n(absMrv) else n("0"),
                minimumDirectDays=if(m==TrainingMuscle.ABS)3 else 0)
        }
    private fun policy(
        bounds:List<DeclaredMuscleDoseBounds> = bounds()
    )=AuthoredGoalProgramPolicy(
        policyId="coach-1",revision="policy-1",rationale="Reviewed core dosage",
        athleteId="athlete-1",athleteRevision="rev-1",
        goal=AthleteTrainingGoal.HYPERTROPHY,
        experience=AthleteExperience.ADVANCED,
        split=ComposerSplit.FULL_BODY,phase=TrainingPhase.HYPERTROPHY,
        trainingDays=3,baselineBounds=bounds,
        selectedBounds=bounds,
        minimumPurposeDays=mapOf(ComposerSessionPurpose.HYPERTROPHY to 3)
    )
    private fun inputs(
        policy:AuthoredGoalProgramPolicy?=policy(),
        nonSet:Map<Int,Int> = mapOf(0 to 200,2 to 200,4 to 200)
    )=CustomCoachAuditInputs(
        policiesByPhase=if(policy==null)emptyMap()
            else mapOf(TrainingPhase.HYPERTROPHY to policy),
        nonSetSecondsByWeekday=nonSet)

    @Test fun exactUserAuthoredCycleCanReachReviewNotIssuance() {
        val source=cycle()
        val result=CustomCycleCoachAuditDomain.review(
            source,setup(),StarterExerciseTranche.movements(),inputs())
        assertEquals(result.issues.toString(),
            CustomCoachAuditStatus.READY_FOR_ATHLETE_REVIEW,result.status)
        assertEquals(3,result.weeks.size)
        assertEquals(listOf(0,2,4),result.weeks.first()
            .estimatedSecondsByDay.keys.toList())
        assertEquals(3,result.weeks.first().workingSets)
        assertEquals(n("3"),result.weeks.first()
            .totalSetCredits.getValue(TrainingMuscle.ABS))
        assertEquals(3,result.weeks.first()
            .directDays.getValue(TrainingMuscle.ABS))
        assertEquals(330L,result.weeks.first()
            .estimatedSecondsByDay.getValue(0))
        assertEquals(source,result.structuralReview!!.verifiedDraft)
        assertFalse(result.mayPublishApprovedProgram)
        assertFalse(result.mayIssueWorkout)
        assertFalse(result.mayAlterWorkoutHistory)
    }

    @Test fun noAuthoredPolicyOrTimeEvidenceMeansNeverApproved() {
        val result=CustomCycleCoachAuditDomain.review(
            cycle(),setup(),StarterExerciseTranche.movements())
        assertEquals(CustomCoachAuditStatus.COACHING_POLICY_REQUIRED,result.status)
        assertTrue(result.issues.any {
            it.kind==CustomCoachIssueKind.REVIEWED_POLICY_MISSING
        })
        assertTrue(result.issues.any {
            it.kind==CustomCoachIssueKind.NON_SET_TIME_UNDECLARED
        })
        assertTrue(result.weeks.all{it.estimatedSecondsByDay.values.all {v->v==null}})
        assertFalse(result.mayIssueWorkout)
    }

    @Test fun everyOverrideWeekIsMeasuredWithoutRewritingOtherWeeks() {
        val extra=List(6){authoredSet()}
        val overrides=mapOf("block-1" to listOf(
            CustomWeekSlotOverride(2,0,"slot-0",extra)))
        val result=CustomCycleCoachAuditDomain.review(
            cycle(overrides=overrides),setup(),
            StarterExerciseTranche.movements(),inputs())
        assertEquals(CustomCoachAuditStatus.UNRESOLVED,result.status)
        assertEquals(3,result.weeks[0].workingSets)
        assertEquals(8,result.weeks[1].workingSets)
        assertEquals(3,result.weeks[2].workingSets)
        assertTrue(result.issues.any {
            it.kind==CustomCoachIssueKind.ABOVE_WEEKLY_MRV &&
            it.location.startsWith("week.2.")
        })
        assertFalse(result.issues.any {
            it.kind==CustomCoachIssueKind.ABOVE_WEEKLY_MRV &&
            it.location.startsWith("week.1.")
        })
        assertEquals(1,cycle(overrides=overrides).blocks.single()
            .days.first().slots.single().sets.size)
    }

    @Test fun disabledMovementOrDifferentCalendarBlocksCoachReview() {
        val result=CustomCycleCoachAuditDomain.review(
            cycle(),setup(excluded=setOf("bodyweight_crunch")),
            StarterExerciseTranche.movements(),inputs())
        assertEquals(CustomCoachAuditStatus.INVALID_SOURCE,result.status)
        assertTrue(result.issues.any{
            it.kind==CustomCoachIssueKind.EXCLUDED_EXERCISE
        })
        assertTrue(result.issues.any{
            it.kind==CustomCoachIssueKind.INVALID_CUSTOM_CYCLE
        })
        val mismatch=CustomCycleCoachAuditDomain.review(
            cycle(days=listOf(authoredDay(0),authoredDay(2))),
            setup(),StarterExerciseTranche.movements(),inputs())
        assertTrue(mismatch.issues.any{
            it.kind==CustomCoachIssueKind.ATHLETE_SCHEDULE_CONFLICT
        })
        assertNotEquals(CustomCoachAuditStatus.READY_FOR_ATHLETE_REVIEW,
            mismatch.status)
    }

    @Test fun malformedPoliciesAndImpossibleTimeCapsAreActionable() {
        val bad=policy().copy(revision="bad revision")
        val denied=CustomCycleCoachAuditDomain.review(
            cycle(),setup(),StarterExerciseTranche.movements(),
            inputs(policy=bad))
        assertTrue(denied.issues.any{
            it.kind==CustomCoachIssueKind.GOAL_POLICY_UNRESOLVED
        })
        val over=CustomCycleCoachAuditDomain.review(
            cycle(),setup(),StarterExerciseTranche.movements(),
            inputs(nonSet=mapOf(0 to 3600,2 to 200,4 to 200)))
        assertTrue(over.issues.any{
            it.kind==CustomCoachIssueKind.SESSION_HARD_LIMIT
        })
    }
}
