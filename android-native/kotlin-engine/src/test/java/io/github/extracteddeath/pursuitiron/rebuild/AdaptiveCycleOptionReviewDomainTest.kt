package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class AdaptiveCycleOptionReviewDomainTest {
    private fun n(s:String)=BigDecimal(s)
    private fun setup():AthleteSetupReview =
        CoachAthleteSetupDomain.review(AthleteSetupDraft(
            profileRevision="rev-1",athleteProfileId="athlete-1",
            goal=AthleteTrainingGoal.HYPERTROPHY,
            experience=AthleteExperience.ADVANCED,
            split=ComposerSplit.FULL_BODY,
            trainingWeekdays=setOf(0,2,4),
            timezoneId="America/New_York",
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
    private fun working()=CustomAuthoredSet(
        DraftSetType.WORKING,DraftSetLoad.ExplicitBodyweight,
        RepBand(10,15),2,55,75)
    private fun day(i:Int,sets:Int=1)=CustomTrainingDay(
        i,"Core day "+i,ComposerSessionPurpose.HYPERTROPHY,
        listOf(CustomExerciseSlot("slot-"+i,"bodyweight_crunch",
            ComposerRole.ACCESSORY,List(sets){working()},pinned=false)))
    private fun source()=CustomTrainingCycle(
        "cycle-1",4,"athlete-1",AthleteTrainingGoal.HYPERTROPHY,
        "Coach reviewed",listOf(CustomTrainingBlock(
            "block-1","Foundation",TrainingPhase.HYPERTROPHY,
            CustomBlockPurpose.TRAINING,3,
            CustomSplitIdentity.Freeform("Custom core"),
            listOf(day(0),day(2),day(4)))))
    private fun policy():AuthoredGoalProgramPolicy {
        val doses=TrainingMuscle.entries.map {muscle ->
            DeclaredMuscleDoseBounds(muscle,
                n(if(muscle==TrainingMuscle.ABS)"3" else "0"),
                n(if(muscle==TrainingMuscle.ABS)"4" else "0"),
                n(if(muscle==TrainingMuscle.ABS)"5" else "0"),
                if(muscle==TrainingMuscle.ABS)3 else 0)
        }
        return AuthoredGoalProgramPolicy(
            "coach-1","policy-1","Reviewed core volume",
            "athlete-1","rev-1",AthleteTrainingGoal.HYPERTROPHY,
            AthleteExperience.ADVANCED,ComposerSplit.FULL_BODY,
            TrainingPhase.HYPERTROPHY,3,doses,doses,
            mapOf(ComposerSessionPurpose.HYPERTROPHY to 3))
    }
    private fun inputs()=CustomCoachAuditInputs(
        policiesByPhase=mapOf(TrainingPhase.HYPERTROPHY to policy()),
        nonSetSecondsByWeekday=mapOf(0 to 200,2 to 200,4 to 200))
    private fun changed(cycle:CustomTrainingCycle,first:Int,second:Int=1)=
        cycle.copy(revision=5,blocks=listOf(
            cycle.blocks.single().copy(days=listOf(
                day(0,first),day(2,second),day(4)))))
    private fun request(
        alternatives:List<AdaptiveCycleOption>,
        permissions:CustomCycleCoachPermissions =
            CustomCycleCoachPermissions(adaptSetCount=true)
    )=AdaptiveCycleOptionRequest(
        source(),"block-1",setup(),StarterExerciseTranche.movements(),
        inputs(),permissions,alternatives)

    @Test fun recommendsAuditedFourSetsOverThreeOrFiveWithoutIssuing() {
        val base=source()
        val options=listOf(
            AdaptiveCycleOption("five",changed(base,2,2)),
            AdaptiveCycleOption("keep",base.copy(revision=5)),
            AdaptiveCycleOption("four",changed(base,2)))
        val candidate=request(options)
        val review=AdaptiveCycleOptionReviewDomain.review(candidate)
        assertEquals(AdaptiveCycleComparisonStatus.OPTIONS_COMPARED,
            review.status)
        assertEquals("four",review.recommendedOptionId)
        assertEquals(3,review.candidatesChecked)
        assertEquals(listOf(5,3,4),review.evaluated.map {
            it.coachingAudit!!.weeks.first().workingSets
        })
        assertEquals(listOf(
            AdaptiveCycleOptionStatus.READY_FOR_REVIEW,
            AdaptiveCycleOptionStatus.UNCHANGED,
            AdaptiveCycleOptionStatus.READY_FOR_REVIEW),
            review.evaluated.map {it.status})
        assertTrue(review.evaluated.all {it.qualityScore!=null})
        assertFalse(review.mayApplyAutomatically)
        assertFalse(review.mayIssueWorkout)
        assertEquals(review,AdaptiveCycleOptionReviewDomain.review(candidate))
        assertEquals(1,base.blocks.single().days.first()
            .slots.single().sets.size)
    }

    @Test fun unpermittedSetsAndUnavailableDaysNeverWin() {
        val base=source()
        val increase=AdaptiveCycleOption("extra-volume",changed(base,2))
        val newDay=day(1)
        val impossible=AdaptiveCycleOption("four-days",
            base.copy(revision=5,blocks=listOf(
                base.blocks.single().copy(
                    days=base.blocks.single().days+newDay))))
        val review=AdaptiveCycleOptionReviewDomain.review(request(
            listOf(increase,impossible),
            CustomCycleCoachPermissions(adaptSchedule=true,
                adaptDayCount=true,adaptExercises=true)))
        assertEquals(AdaptiveCycleComparisonStatus.NO_FEASIBLE_ALTERNATIVE,
            review.status)
        assertNull(review.recommendedOptionId)
        assertEquals(AdaptiveCycleOptionStatus.PERMISSION_REQUIRED,
            review.evaluated[0].status)
        assertEquals(AdaptiveCycleOptionStatus.COACH_AUDIT_FAILED,
            review.evaluated[1].status)
        assertTrue(review.evaluated[1].coachingAudit!!.issues.any {
            it.kind==CustomCoachIssueKind.ATHLETE_SCHEDULE_CONFLICT
        })
        assertTrue(review.evaluated.all {it.qualityScore==null})
    }

    @Test fun fullCycleContinuityMissingCannotBeReplacedByNiceScore() {
        val base=source()
        val existing=base.blocks.single()
        val heavy=existing.copy(weeks=1,days=listOf(
            day(0,2),day(2,2),day(4)))
        val next=existing.copy(blockId="block-2",weeks=1,
            label="Next training block")
        val proposed=base.copy(revision=5,blocks=listOf(heavy,next))
        val review=AdaptiveCycleOptionReviewDomain.review(
            request(listOf(AdaptiveCycleOption("collapsed",proposed)),
                CustomCycleCoachPermissions(adaptBlocks=true,
                    adaptSetCount=true)))
        assertEquals(AdaptiveCycleComparisonStatus.NO_FEASIBLE_ALTERNATIVE,
            review.status)
        assertEquals(AdaptiveCycleOptionStatus.COACH_POLICY_REQUIRED,
            review.evaluated.single().status)
        assertTrue(review.evaluated.single().coachingAudit!!.issues.any {
            it.kind==CustomCoachIssueKind.CONTINUITY_POLICY_MISSING
        })
        assertNull(review.recommendedOptionId)
    }

    @Test fun unresolvedOriginalAndInvalidOptionIdsFailClosed() {
        val current=request(emptyList())
        assertEquals(AdaptiveCycleComparisonStatus.SOURCE_UNRESOLVED,
            AdaptiveCycleOptionReviewDomain.review(current.copy(
                approvedCycle=source().copy(goal=AthleteTrainingGoal.STRENGTH)
            )).status)
        assertThrows(IllegalArgumentException::class.java) {
            AdaptiveCycleOptionReviewDomain.review(request(
                List(9){i->AdaptiveCycleOption("option-"+i,
                    source().copy(revision=5))}))
        }
        assertThrows(IllegalArgumentException::class.java) {
            AdaptiveCycleOptionReviewDomain.review(request(
                listOf(AdaptiveCycleOption("same",source().copy(revision=5)),
                    AdaptiveCycleOption("same",source().copy(revision=5)))))
        }
    }
}
