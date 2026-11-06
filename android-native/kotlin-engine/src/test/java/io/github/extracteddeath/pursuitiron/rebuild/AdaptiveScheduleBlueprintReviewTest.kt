package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class AdaptiveScheduleBlueprintReviewTest {
    private val f=GeneratedAdaptiveBlockFixture
    private fun n(x:String)=BigDecimal(x)
    private fun current()=f.cycle().let {original ->
        original.copy(blocks=listOf(original.blocks.single(),
            original.blocks.single().copy(blockId="block-2",
                label="Future block",weeks=2)))
    }
    private fun continuity()=CustomCoachAuditInputs(
        policiesByPhase=mapOf(TrainingPhase.HYPERTROPHY to
            checkNotNull(f.generation().policy)),
        nonSetSecondsByWeekday=mapOf(0 to 2100,2 to 2100,4 to 2100),
        continuityPolicy=CustomCoachVolumeContinuityPolicy(
            n("0.5"),n("0.5"),n("0.5"),n("0.5")))
    private fun permission()=CustomCycleCoachPermissions(
        adaptSplit=true,adaptDayCount=true,adaptSchedule=true,
        adaptExercises=true,adaptSetCount=true,adaptLoads=true,
        adaptReps=true,adaptRir=true,adaptRest=true)
    private fun base()=AdaptiveBlockGenerationRequest(
        current(),"block-2",f.athlete(),f.movements,continuity(),
        permission(),emptyList())
    private fun setup(split:ComposerSplit,days:Set<Int>):AthleteSetupReview {
        val current=checkNotNull(f.athlete().reviewed)
        return CoachAthleteSetupDomain.review(AthleteSetupDraft(
            profileRevision=current.profileRevision,
            athleteProfileId=current.athleteProfileId,
            goal=current.goal,experience=current.experience,
            split=split,trainingWeekdays=days,
            timezoneId=current.timezoneId,
            sessionTimeBucket=current.sessionTimeBucket,
            hardSessionLimit=current.hardSessionLimit,
            gym=GymProfileDraft(current.gym.gymId,current.gym.unit,
                current.gym.confirmedStock,current.gym.availableFacilities,
                current.gym.availableAttachments),
            allowBodyweight=current.allowBodyweight,
            allowSupersets=current.allowSupersets,
            allowTricepsOnLower=current.allowTricepsOnLower,
            maxBarbellExercisesPerDay=current.maxBarbellExercisesPerDay,
            focusedMuscles=current.focusedMuscles,
            reducedMuscles=current.reducedMuscles,
            excludedExerciseIds=current.excludedExerciseIds,
            lockedTrainingDays=current.lockedTrainingDays,
            hardTrainingDays=current.hardTrainingDays,
            longTrainingDays=current.longTrainingDays,
            declaredRestDays=(0..6).toSet()-days))
    }
    private fun future(split:ComposerSplit)=run {
        val newDays=setOf(0,1,2,4)
        val old=checkNotNull(f.generation().policy)
        val doses=old.selectedBounds.map {bound ->
            bound.copy(minimumDirectDays=bound.minimumDirectDays.coerceAtMost(2))
        }
        ProspectiveBlockCoachingContext(
            setup(split,newDays),
            old.copy(revision="future-r2",split=split,
                trainingDays=4,baselineBounds=doses,selectedBounds=doses,
                minimumPurposeDays=mapOf(
                    ComposerSessionPurpose.HYPERTROPHY to 4)),
            newDays.associateWith {2100})
    }
    private fun blueprint(id:String,ctx:ProspectiveBlockCoachingContext?=null)=
        AdaptiveScheduleBlueprint(id,f.generation().choices,f.policies(),ctx)
    private fun request(vararg blueprints:AdaptiveScheduleBlueprint)=
        AdaptiveScheduleBlueprintRequest(base(),f.generation(),
            blueprints.toList())

    @Test fun blueprintsProduceSameFullyAuditedCurrentOptionsAsManualRequests() {
        val input=request(blueprint("normal"),blueprint("second"))
        val result=AdaptiveBlockOptionGenerationDomain.reviewBlueprints(input)
        val expected=AdaptiveBlockOptionGenerationDomain.review(
            base().copy(alternatives=listOf(
                AdaptiveBlockGenerationAlternative("normal",
                    f.generation(),f.policies()),
                AdaptiveBlockGenerationAlternative("second",
                    f.generation(),f.policies()))))
        assertEquals(expected,result)
        assertEquals(AdaptiveBlockGenerationStatus.OPTIONS_EVALUATED,
            result.status)
        assertEquals(3,result.comparison.candidatesChecked)
        assertTrue(result.outcomes.all {it.eligibleForCycleAudit})
        assertFalse(result.mayApplyAutomatically)
        assertFalse(result.mayIssueWorkout)
    }

    @Test fun reviewedUpperLowerFourDayOptionRemainsProspective() {
        val source=current()
        val ctx=future(ComposerSplit.UPPER_LOWER)
        assertTrue(ctx.setup.issues.toString(),
            ctx.setup.mayReviewProgramCandidate)
        val review=AdaptiveBlockOptionGenerationDomain.reviewBlueprints(
            request(blueprint("future_ul4",ctx)))
        assertNull(review.outcomes.single().refusal)
        assertEquals(GeneratedAdaptiveBlockStatus.READY_FOR_CYCLE_REVIEW,
            review.outcomes.single().generated?.status)
        val four=review.comparison.evaluated.single {
            it.optionId=="future_ul4"
        }
        assertEquals(four.explanation,
            AdaptiveCycleOptionStatus.PROSPECTIVE_REVIEW_REQUIRED,
            four.status)
        assertNotNull(four.qualityScore)
        assertEquals("future_ul4",
            review.comparison.recommendedProspectiveOptionId)
        assertEquals("keep_current",review.recommendedOptionId)
        assertEquals(setOf(0,2,4),
            source.blocks.first().days.map {it.weekday}.toSet())
        assertEquals(4,checkNotNull(review.outcomes.single().generated)
            .proposedOption!!.proposedCycle.blocks.last().days.size)
        assertFalse(review.maySaveProgram)
    }

    @Test fun boundedBlueprintsCannotImpersonateOtherAthleteOrTemplate() {
        val altered=f.generation().copy(
            maximumExercisesPerDay=5)
        assertThrows(IllegalArgumentException::class.java) {
            AdaptiveBlockOptionGenerationDomain.reviewBlueprints(
                request(blueprint("bad")).copy(generatorTemplate=
                    altered.copy(setup=setup(ComposerSplit.UPPER_LOWER,
                        setOf(0,1,2,4)))))
        }
        assertThrows(IllegalArgumentException::class.java) {
            AdaptiveBlockOptionGenerationDomain.reviewBlueprints(
                request(*List(8) {i->blueprint("o"+i)}.toTypedArray()))
        }
        assertThrows(IllegalArgumentException::class.java) {
            AdaptiveBlockOptionGenerationDomain.reviewBlueprints(
                request(blueprint("duplicate"),blueprint("duplicate")))
        }
    }
}
