package io.github.extracteddeath.pursuitiron.rebuild

import org.junit.Assert.*
import org.junit.Test

/**
 * Actual bounded generation across four split/phase combinations.
 * Check the output working-set dose, direct-day frequency, verified stock,
 * required primary lift exposures and achievable session length.
 */
class GeneratedProgramQualityMatrixTest {
    private val fixture=GeneratedAdaptiveBlockFixture
    private data class Scenario(
        val name:String,val split:ComposerSplit,val days:Set<Int>,
        val phase:TrainingPhase,val extras:List<ComposerFocus> =emptyList()
    )
    private val scenarios=listOf(
        Scenario("3-day full-body hypertrophy",ComposerSplit.FULL_BODY,
            setOf(0,2,4),TrainingPhase.HYPERTROPHY),
        Scenario("4-day upper/lower hypertrophy",ComposerSplit.UPPER_LOWER,
            setOf(0,1,3,4),TrainingPhase.HYPERTROPHY),
        Scenario("5-day ULPPL strength",ComposerSplit.ULPPL,
            setOf(0,1,2,4,5),TrainingPhase.STRENGTH),
        Scenario("6-day PPL peak",ComposerSplit.PUSH_PULL_LEGS,
            setOf(0,1,2,3,4,5),TrainingPhase.PEAK)
    )
    private fun allowed(id:String)=when(id) {
        "bench" ->setOf(ComposerFocus.FULL_BODY,ComposerFocus.UPPER,
            ComposerFocus.PUSH)
        "row" ->setOf(ComposerFocus.FULL_BODY,ComposerFocus.UPPER,
            ComposerFocus.PULL)
        "squat" ->setOf(ComposerFocus.FULL_BODY,ComposerFocus.LOWER,
            ComposerFocus.LEGS)
        else ->error("Unexpected catalog exercise")
    }
    private fun request(scenario:Scenario):AthleteProgramGenerationRequest {
        val base=fixture.generation()
        val original=checkNotNull(base.setup.reviewed)
        val reviewed=original.copy(
            split=scenario.split,
            trainingWeekdays=scenario.days,
            declaredRestDays=(0..6).toSet()-scenario.days)
        val setup=base.setup.copy(reviewed=reviewed)
        val skill=scenario.phase!=TrainingPhase.HYPERTROPHY
        val policy=checkNotNull(base.policy).copy(
            split=scenario.split,trainingDays=scenario.days.size,
            phase=scenario.phase,
            minimumPurposeDays=if(skill)mapOf(
                ComposerSessionPurpose.STRENGTH_SKILL to 2,
                ComposerSessionPurpose.GENERAL_PREPARATION to scenario.days.size-2)
            else mapOf(ComposerSessionPurpose.HYPERTROPHY to scenario.days.size),
            primaryLiftMinimumDays=if(skill)mapOf("bench" to 2) else emptyMap())
        return base.copy(setup=setup,phase=scenario.phase,policy=policy,
            catalog=base.catalog.map {
                it.copy(permittedFocus=allowed(it.exerciseId))
            },nonSetSecondsByWeekday=scenario.days.associateWith {2100},
            extraFocuses=scenario.extras,maximumExercisesPerDay=3,
            preferredPatternsPerDay=1)
    }

    @Test fun realisticGenerationMatrixPreservesDoseFrequencyStockAndTime() {
        for(s in scenarios) {
            val plan=request(s)
            val review=CoachAthleteProgramGenerator.generate(plan)
            assertEquals("${s.name}: ${review.issues} / ${review.search}",
                AthleteGenerationStatus.GENERATED_FOR_REVIEW,review.status)
            val candidate=checkNotNull(review.candidate)
            val composed=checkNotNull(candidate.catalogReview?.composedRequest)
            val week=checkNotNull(candidate.catalogReview?.draft)
            val realization=checkNotNull(candidate.realization)
            assertEquals(s.name,ComposerStatus.FEASIBLE_DRAFT_FOR_REVIEW,
                week.status)
            assertEquals(s.name,GoalRealizationStatus.DELIVERED_FOR_REVIEW,
                realization.status)
            assertTrue(s.name,realization.issues.isEmpty())
            assertEquals(s.name,s.days,review.focusByWeekday.keys)
            assertEquals(s.name,s.days.size,composed.days.size)
            assertEquals(s.name,s.days.size,week.sessions.size)
            assertEquals(s.name,0,week.audit.daysWithoutWorkingSets.size)
            assertTrue(s.name,week.audit.satisfiesDeclaredHardConstraints)
            assertTrue(s.name,week.audit.daysOutsidePreferredWindow.isEmpty())
            assertTrue(s.name,week.missingRequiredVisits.isEmpty())
            assertTrue(s.name,week.daysWithUnmetPrimary.isEmpty())
            assertTrue(s.name,week.visits.all {visit ->
                val focus=composed.days[visit.dayIndex].focus
                focus in allowed(visit.exerciseId)
            })
            assertTrue(s.name,week.audit.muscles.values.all {
                it.gapToMev.signum()==0 &&
                    it.excessAboveMrv.signum()==0 &&
                    it.missingDirectDays==0
            })
            assertTrue(s.name,week.visits.all { visit ->
                val option=composed.exercises.single {
                    it.exerciseId==visit.exerciseId
                }
                val movement=plan.catalog.single {
                    it.exerciseId==visit.exerciseId
                }
                val verified=option.equipment as? ComposerEquipment.Verified
                    ?:return@all false
                val stock=checkNotNull(
                    plan.setup.reviewed?.gym?.confirmedStock?.get(
                        movement.equipmentId))
                val inspected=EquipmentLoadDomain.inspect(stock,
                    verified.plannedLoad,verified.unit)
                inspected is LoadInspection.Confirmed && inspected.exact
            })
            if(s.phase in setOf(TrainingPhase.STRENGTH,TrainingPhase.PEAK)) {
                assertEquals(s.name,2,
                    realization.primaryFrequencies.single().deliveredDays)
                assertEquals(s.name,2,review.intents.count {
                    it.purpose==ComposerSessionPurpose.STRENGTH_SKILL &&
                        it.requiredPrimaryExerciseId=="bench"
                })
            }
            assertEquals(s.name,review,
                CoachAthleteProgramGenerator.generate(plan))
        }
    }
}
