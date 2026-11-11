package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class ReviewedFutureWeekProgressionDomainTest {
    private fun set()=CustomAuthoredSet(
        DraftSetType.WORKING,DraftSetLoad.ExplicitBodyweight,
        RepBand(10,15),2,55,75)
    private fun day(weekday:Int)=CustomTrainingDay(
        weekday,"Core $weekday",ComposerSessionPurpose.HYPERTROPHY,
        listOf(CustomExerciseSlot("slot-$weekday","bodyweight_crunch",
            ComposerRole.ACCESSORY,listOf(set(),set()),pinned=false)))
    private fun cycle()=CustomTrainingCycle(
        "cycle-1",1,"athlete-1",AthleteTrainingGoal.HYPERTROPHY,
        "Evidence cycle",listOf(CustomTrainingBlock(
            "base","Foundation",TrainingPhase.HYPERTROPHY,
            CustomBlockPurpose.TRAINING,4,
            CustomSplitIdentity.Freeform("Custom core"),
            listOf(day(0),day(2),day(4)))))
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
        declaredRestDays=setOf(1,3,5,6)))
    private fun policy()=TrainingMuscle.entries.map {muscle->
        val isAbs=muscle==TrainingMuscle.ABS
        DeclaredMuscleDoseBounds(muscle,
            BigDecimal(if(isAbs)"3" else "0"),
            BigDecimal(if(isAbs)"6" else "0"),
            BigDecimal(if(isAbs)"12" else "0"),
            if(isAbs)3 else 0)
    }.let {bounds->
        AuthoredGoalProgramPolicy("source-1","v1",
            "Explicitly reviewed core dose",
            "athlete-1","rev-1",AthleteTrainingGoal.HYPERTROPHY,
            AthleteExperience.ADVANCED,ComposerSplit.FULL_BODY,
            TrainingPhase.HYPERTROPHY,3,bounds,bounds,
            mapOf(ComposerSessionPurpose.HYPERTROPHY to 3))
    }
    private fun inputs()=CustomCoachAuditInputs(
        policiesByPhase=mapOf(TrainingPhase.HYPERTROPHY to policy()),
        nonSetSecondsByWeekday=mapOf(0 to 200,2 to 200,4 to 200))
    private fun preview(source:CustomTrainingCycle,week:Int,
        weekday:Int=0):CustomWorkoutDaySnapshot =
        (CustomWorkoutSnapshotDomain.preview(source,setup(),
            StarterExerciseTranche.movements(),1,week,weekday)
            as CustomWorkoutSnapshotResult.Preview).snapshot
    private fun completed(source:CustomTrainingCycle,week:Int,
        order:Long,actualRir:Int=3,reps:Int=10):CustomCoachingHistoricalSession {
        val old=preview(source,week)
        val slot=old.slots.single()
        val facts=slot.sets.mapIndexed {index,prescribed->
            CustomHistoricalVisitFact(
                slot.slotId,slot.exerciseId,prescribed.setKey,
                CustomWorkoutVisitPart.MAIN,DraftSetType.WORKING,
                prescribed.prescription.load,prescribed.prescription.reps,
                prescribed.prescription.targetRir,null,
                KotlinJournalSetOutcome.COMPLETED,
                KotlinPerformedSet(null,null,reps,actualRir),
                "record-$week-$index")
        }
        return CustomCoachingHistoricalSession(order,old,
            CustomWorkoutHistoricalEvidence("athlete-1","w-$week",
                source.cycleId,source.revision,"finish-$week",2,facts))
    }
    private fun request(
        source:CustomTrainingCycle=cycle(),
        permissions:CustomCycleCoachPermissions=CustomCycleCoachPermissions(
            adaptWeekOverrides=true,adaptReps=true),
        readiness:CoachReadiness=CoachReadiness.NORMAL,
        lastCompleted:Int=2,
        nextWeek:Int=3,
        history:List<CustomCoachingHistoricalSession> =
            listOf(completed(source,1,1),completed(source,2,2))
    ):ReviewedFutureWeekProgressionRequest =
        ReviewedFutureWeekProgressionRequest(source,setup(),
            StarterExerciseTranche.movements(),inputs(),1,lastCompleted,
            CustomNextWorkoutCoachingRequest(
                preview(source,nextWeek),"slot-0",history,null,
                BigDecimal.ONE,readiness,false,
                CustomRepForecastPolicy.TARGET_RIR_V2),
            permissions)

    @Test fun machineABAndCableOrSmithCannotBorrowACycleLevelLoad() {
        val machineBases=setOf(LoadBasis.MACHINE_STACK,LoadBasis.CABLE_STACK,
            LoadBasis.SMITH_TOTAL,LoadBasis.EXERCISE_OVERRIDE)
        assertTrue(machineBases.all {
            ReviewedFutureWeekProgressionDomain.requiresPhysicalMachineIdentity(it)
        })
        assertFalse(ReviewedFutureWeekProgressionDomain.requiresPhysicalMachineIdentity(
            LoadBasis.BARBELL_TOTAL))
        assertFalse(ReviewedFutureWeekProgressionDomain.requiresPhysicalMachineIdentity(
            LoadBasis.DUMBBELL_PER_HAND))
        assertFalse(ReviewedFutureWeekProgressionDomain.requiresPhysicalMachineIdentity(null))
    }

    @Test fun genuineCompletedRepsChangeOnlyOneUpcomingSlotAndWeek() {
        val source=cycle()
        val result=ReviewedFutureWeekProgressionDomain.propose(
            request(source=source))
        assertEquals(result.explanation,
            ReviewedFutureWeekProgressionStatus.CANDIDATE_FOR_ATHLETE_REVIEW,
            result.status)
        assertEquals(CustomNextWorkoutCoachingStatus.BUILD_REPS,
            result.coaching?.status)
        val candidate=checkNotNull(result.candidate)
        assertEquals(2L,candidate.revision)
        assertEquals(3,result.changedAbsoluteWeek)
        assertEquals("slot-0",result.changedSlotId)
        assertEquals(CustomCoachAuditStatus.READY_FOR_ATHLETE_REVIEW,
            result.candidateAudit?.status)
        assertEquals(listOf(11,11),preview(candidate,3).slots.single()
            .sets.map{it.prescription.reps.minimum})
        assertEquals(listOf(10,10),preview(candidate,2).slots.single()
            .sets.map{it.prescription.reps.minimum})
        assertEquals(listOf(10,10),preview(candidate,4).slots.single()
            .sets.map{it.prescription.reps.minimum})
        assertEquals(listOf(10,10),preview(candidate,3,2).slots.single()
            .sets.map{it.prescription.reps.minimum})
        assertEquals(source.blocks,candidate.blocks)
        assertTrue(source.weekOverrides.isEmpty())
        assertEquals(1,candidate.weekOverrides["base"]?.size)
        assertFalse(result.mayIssueWorkout)
        assertFalse(result.maySaveProgram)
        assertFalse(result.mayChangeWorkoutHistory)
    }

    @Test fun missingPermissionAndLowReadinessDoNotChangeTargets() {
        val unpermitted=ReviewedFutureWeekProgressionDomain.propose(
            request(permissions=CustomCycleCoachPermissions(
                adaptReps=true)))
        assertEquals(ReviewedFutureWeekProgressionStatus.PERMISSION_REQUIRED,
            unpermitted.status)
        assertNull(unpermitted.candidate)
        val low=ReviewedFutureWeekProgressionDomain.propose(
            request(readiness=CoachReadiness.LOW))
        assertEquals(ReviewedFutureWeekProgressionStatus.HOLD_WITHOUT_AMENDMENT,
            low.status)
        assertEquals(
            CustomNextWorkoutCoachingStatus.RESTRICTED_OR_LOW_READINESS,
            low.coaching?.status)
        assertNull(low.candidate)
    }

    @Test fun repeatedReportedCapacityDeclineHoldsInsteadOfAssumingChronicFatigue() {
        val source=cycle()
        val evidence=listOf(
            completed(source,1,1,actualRir=3,reps=14),
            completed(source,2,2,actualRir=2,reps=10))
        val input=request(source=source,history=evidence).let {
            it.copy(coachingRequest=it.coachingRequest.copy(
                repForecastPolicy=CustomRepForecastPolicy.TARGET_RIR_TREND_V3))
        }
        val review=ReviewedFutureWeekProgressionDomain.propose(input)
        assertEquals(ReviewedFutureWeekProgressionStatus.HOLD_WITHOUT_AMENDMENT,
            review.status)
        assertEquals(
            CustomNextWorkoutCoachingStatus.HOLD_MARKED_SAME_DAY_CAPACITY_DROP,
            review.coaching?.status)
        assertNull(review.candidate)
        assertFalse(review.mayIssueWorkout)
    }

    @Test fun cannotRewritePastFutureOrAthleteCustomizedWeek() {
        val back=ReviewedFutureWeekProgressionDomain.propose(
            request(nextWeek=2))
        assertEquals(ReviewedFutureWeekProgressionStatus.NOT_NEXT_UNISSUED_WEEK,
            back.status)
        val far=ReviewedFutureWeekProgressionDomain.propose(
            request(nextWeek=4))
        assertEquals(ReviewedFutureWeekProgressionStatus.NOT_NEXT_UNISSUED_WEEK,
            far.status)
        val current=cycle().copy(weekOverrides=mapOf("base" to listOf(
            CustomWeekSlotOverride(3,0,"slot-0",listOf(set(),set())))))
        val protected=ReviewedFutureWeekProgressionDomain.propose(
            request(source=current,history=listOf(
                completed(current,1,1),completed(current,2,2))))
        assertEquals(ReviewedFutureWeekProgressionStatus.ALREADY_CUSTOMIZED,
            protected.status)
        assertNull(protected.candidate)
    }

    @Test fun changedSourceAndMissingCoachingPolicyCannotBeLaundered() {
        val request=request()
        val forged=request.copy(coachingRequest=request.coachingRequest.copy(
            next=request.coachingRequest.next.copy(
                stamp=request.coachingRequest.next.stamp.copy(
                    cycleRevision=999))))
        assertEquals(ReviewedFutureWeekProgressionStatus.UNVERIFIED_SOURCE,
            ReviewedFutureWeekProgressionDomain.propose(forged).status)
        val unreviewed=request.copy(coachPolicy=CustomCoachAuditInputs())
        assertEquals(ReviewedFutureWeekProgressionStatus.UNVERIFIED_SOURCE,
            ReviewedFutureWeekProgressionDomain.propose(unreviewed).status)
        assertNull(ReviewedFutureWeekProgressionDomain.propose(unreviewed)
            .candidate)
    }
}
