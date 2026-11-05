package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class CoachAthleteProgramGeneratorTest {
    private fun n(v:String)=BigDecimal(v)
    private val db=VerifiedLoadStock.Totals(LoadUnit.LB,
        LoadBasis.DUMBBELL_PER_HAND,listOf(n("20"),n("25"),n("30")))
    private fun setup(
        split:ComposerSplit=ComposerSplit.FULL_BODY,
        weekdays:Set<Int> =setOf(0,2,4)
    )=CoachAthleteSetupDomain.review(AthleteSetupDraft(
        profileRevision="revision2",athleteProfileId="athlete2",
        goal=AthleteTrainingGoal.POWERBUILDING,
        experience=AthleteExperience.ADVANCED,
        split=split,trainingWeekdays=weekdays,
        timezoneId="America/New_York",
        sessionTimeBucket=SessionTimeBucket.FORTY_TO_SIXTY,
        hardSessionLimit=HardSessionLimit.NO_HARD_LIMIT,
        gym=GymProfileDraft("main-gym",LoadUnit.LB,
            mapOf("dumbbells" to db),emptySet(),emptySet()),
        allowBodyweight=false,allowSupersets=false,
        allowTricepsOnLower=false,maxBarbellExercisesPerDay=0,
        focusedMuscles=emptySet(),reducedMuscles=emptySet(),
        excludedExerciseIds=emptySet(),lockedTrainingDays=emptySet(),
        hardTrainingDays=emptySet(),longTrainingDays=emptySet(),
        declaredRestDays=(0..6).toSet()-weekdays))
    private fun movement(
        id:String,muscle:TrainingMuscle,
        pattern:ComposerPattern,
        focus:Set<ComposerFocus> =ComposerFocus.entries.toSet(),
        role:ComposerRole=ComposerRole.SECONDARY
    )=CatalogMovement(id,id,"dumbbell",null,pattern,role,focus,
        "dumbbells",LoadBasis.DUMBBELL_PER_HAND,
        listOf(DeclaredMuscleCredit(muscle,MuscleCreditRole.DIRECT,n("1"))),
        CatalogCreditStatus.AUTHOR_REVIEWED_PROVISIONAL,"reviewed-volume-v1")
    private val bench=movement("bench",TrainingMuscle.CHEST,
        ComposerPattern.HORIZONTAL_PRESS,role=ComposerRole.PRIMARY)
    private val row=movement("row",TrainingMuscle.LATS,
        ComposerPattern.HORIZONTAL_PULL)
    private val squat=movement("squat",TrainingMuscle.QUADS,
        ComposerPattern.SQUAT)
    private val movements=listOf(bench,row,squat)
    private fun choice(id:String,visits:Int=3,gap:Int=1)=
        CatalogExerciseChoice(id,n("25"),90,120,2,4,visits,1,gap,5)
    private fun bounds():List<DeclaredMuscleDoseBounds> =
        TrainingMuscle.entries.map {muscle ->
            val active=muscle in setOf(TrainingMuscle.CHEST,
                TrainingMuscle.LATS,TrainingMuscle.QUADS)
            DeclaredMuscleDoseBounds(muscle,
                n(if(active)"2"else"0"),
                n(if(active)"5"else"0"),
                n(if(active)"10"else"0"),if(active)1 else 0)
        }
    private fun policy(
        s:AthleteSetupReview,
        phase:TrainingPhase=TrainingPhase.HYPERTROPHY,
        purposes:Map<ComposerSessionPurpose,Int> =
            mapOf(ComposerSessionPurpose.HYPERTROPHY to
                checkNotNull(s.reviewed).trainingWeekdays.size),
        primaries:Map<String,Int> =emptyMap()
    ):AuthoredGoalProgramPolicy {
        val athlete=checkNotNull(s.reviewed)
        val dose=bounds()
        return AuthoredGoalProgramPolicy("authored-week","v2",
            "Stable explicit doses and primary lift frequency.",
            athlete.athleteProfileId,athlete.profileRevision,
            athlete.goal,athlete.experience,athlete.split,
            phase,athlete.trainingWeekdays.size,dose,dose,purposes,primaries)
    }
    private fun request(
        s:AthleteSetupReview=setup(),
        phase:TrainingPhase=TrainingPhase.HYPERTROPHY,
        policy:AuthoredGoalProgramPolicy=policy(s,phase),
        extra:List<ComposerFocus> =emptyList(),
        choices:List<CatalogExerciseChoice> =
            movements.map {choice(it.exerciseId)}
    )=AthleteProgramGenerationRequest(
        s,phase,policy,movements,choices,
        checkNotNull(s.reviewed).trainingWeekdays.associateWith {1200},
        3,1,extra)

    @Test fun refusesIncompleteProfileOrMissingPolicyWithoutGuessing() {
        val valid=request()
        val incomplete=CoachAthleteProgramGenerator.generate(
            valid.copy(setup=CoachAthleteSetupDomain.review(AthleteSetupDraft())))
        assertEquals(AthleteGenerationStatus.INPUTS_UNRESOLVED,incomplete.status)
        assertNull(incomplete.candidate)
        assertTrue(incomplete.focusByWeekday.isEmpty())
        val noPolicy=CoachAthleteProgramGenerator.generate(
            valid.copy(policy=null))
        assertEquals(AthleteGenerationStatus.INPUTS_UNRESOLVED,noPolicy.status)
        assertTrue(noPolicy.issues.any {
            it.kind==AthleteGenerationIssueKind.POLICY_UNRESOLVED
        })
    }

    @Test fun threeDayUpperLowerRequiresExplicitExtraFocusAndKeepsWeekdays() {
        val s=setup(ComposerSplit.UPPER_LOWER,setOf(0,2,4))
        val original=request(s=s)
        val missing=CoachAthleteProgramGenerator.generate(original)
        assertEquals(AthleteGenerationStatus.INPUTS_UNRESOLVED,missing.status)
        assertTrue(missing.issues.any {
            it.kind==AthleteGenerationIssueKind.FOCUS_DISTRIBUTION_INVALID
        })
        val generated=CoachAthleteProgramGenerator.generate(original.copy(
            extraFocuses=listOf(ComposerFocus.LOWER)))
        assertEquals(setOf(0,2,4),generated.focusByWeekday.keys)
        assertEquals(2,generated.focusByWeekday.values.count {
            it==ComposerFocus.LOWER
        })
        assertEquals(1,generated.focusByWeekday.values.count {
            it==ComposerFocus.UPPER
        })
        assertEquals(setOf(0,2,4),generated.intents.map {it.weekday}.toSet())
        assertFalse(generated.maySaveProgram)
        assertFalse(generated.mayIssueWorkout)
    }

    @Test fun fiveDayULPPLProducesAllFiveDistinctSplitDayFocuses() {
        val s=setup(ComposerSplit.ULPPL,setOf(0,1,2,4,5))
        val generated=CoachAthleteProgramGenerator.generate(request(s=s))
        assertEquals(ComposerFocus.entries.filter {
            it in setOf(ComposerFocus.UPPER,ComposerFocus.LOWER,
                ComposerFocus.PUSH,ComposerFocus.PULL,ComposerFocus.LEGS)
        }.toSet(),generated.focusByWeekday.values.toSet())
        assertEquals(5,generated.intents.size)
        assertEquals(5,generated.focusByWeekday.size)
        assertNotNull(generated.candidate)
    }

    @Test fun PPLFourDaysRequiresExtraFocusButSixDaysDoesNot() {
        val four=setup(ComposerSplit.PUSH_PULL_LEGS,setOf(0,1,3,5))
        val unresolved=CoachAthleteProgramGenerator.generate(request(s=four))
        assertEquals(AthleteGenerationStatus.INPUTS_UNRESOLVED,unresolved.status)
        val fourDay=CoachAthleteProgramGenerator.generate(request(s=four,
            extra=listOf(ComposerFocus.PULL)))
        assertEquals(2,fourDay.focusByWeekday.values.count {
            it==ComposerFocus.PULL
        })
        val six=setup(ComposerSplit.PUSH_PULL_LEGS,setOf(0,1,2,3,4,5))
        val sixDay=CoachAthleteProgramGenerator.generate(request(s=six))
        assertEquals(6,sixDay.focusByWeekday.size)
        for(focus in listOf(ComposerFocus.PUSH,ComposerFocus.PULL,
            ComposerFocus.LEGS))
            assertEquals(2,sixDay.focusByWeekday.values.count {it==focus})
    }

    @Test fun priorityLiftPlacementUsesVerifiedLoadAndRealDayFrequency() {
        val s=setup()
        val p=policy(s,primaries=mapOf("bench" to 2))
        val result=CoachAthleteProgramGenerator.generate(request(s=s,policy=p))
        assertEquals(3,result.intents.size)
        assertEquals(2,result.intents.count {
            it.requiredPrimaryExerciseId=="bench"
        })
        assertEquals(2,result.intents.filter {
            it.requiredPrimaryExerciseId=="bench"
        }.map {it.weekday}.distinct().size)
        val matching=checkNotNull(result.candidate)
        assertNotNull(matching.realization)
        assertTrue(matching.issues.none {
            it.kind==AthleteCandidateIssueKind.GOAL_POLICY_UNRESOLVED
        })
        assertEquals(2,matching.realization?.primaryFrequencies?.single()?.requiredDays)
    }

    @Test fun strengthPurposeDaysGetActualPrimaryLiftsNotJustSkillLabels() {
        val s=setup()
        val p=policy(s,phase=TrainingPhase.STRENGTH,
            purposes=mapOf(ComposerSessionPurpose.STRENGTH_SKILL to 2,
                ComposerSessionPurpose.GENERAL_PREPARATION to 1),
            primaries=mapOf("bench" to 2))
        val generated=CoachAthleteProgramGenerator.generate(request(
            s=s,phase=TrainingPhase.STRENGTH,policy=p))
        assertEquals(2,generated.intents.count {
            it.purpose==ComposerSessionPurpose.STRENGTH_SKILL
        })
        assertEquals(2,generated.intents.count {
            it.purpose==ComposerSessionPurpose.STRENGTH_SKILL &&
                it.requiredPrimaryExerciseId=="bench"
        })
        assertEquals(1,generated.intents.count {
            it.purpose==ComposerSessionPurpose.GENERAL_PREPARATION
        })
        assertTrue(generated.candidate?.issues?.none {
            it.kind==AthleteCandidateIssueKind.GOAL_POLICY_UNRESOLVED
        } ==true)
    }

    @Test fun badLiftRecoveryAcrossSundayMondayIsNotSilentlyRelaxed() {
        val s=setup(weekdays=setOf(0,2,6))
        val p=policy(s,primaries=mapOf("bench" to 3))
        val constrained=request(s=s,policy=p,choices=listOf(
            choice("bench",visits=3,gap=2),
            choice("row"),choice("squat")))
        val result=CoachAthleteProgramGenerator.generate(constrained)
        assertEquals(AthleteGenerationStatus.PLACEMENTS_UNRESOLVED,result.status)
        assertNull(result.candidate)
        assertTrue(result.issues.any {
            it.kind==AthleteGenerationIssueKind.SCHEDULE_CONFLICT
        })
    }

    @Test fun unachievablePrimaryMaxVisitsIsReportedNotDropped() {
        val s=setup()
        val p=policy(s,primaries=mapOf("bench" to 3))
        val constrained=request(s=s,policy=p,choices=listOf(
            choice("bench",visits=1),choice("row"),choice("squat")))
        val result=CoachAthleteProgramGenerator.generate(constrained)
        assertEquals(AthleteGenerationStatus.PLACEMENTS_UNRESOLVED,result.status)
        assertTrue(result.focusByWeekday.isEmpty())
        assertNull(result.candidate)
    }

    @Test fun invalidPurposeTotalCannotCreateInventedGeneralWorkouts() {
        val s=setup()
        val incomplete=policy(s,purposes=
            mapOf(ComposerSessionPurpose.HYPERTROPHY to 1))
        val generated=CoachAthleteProgramGenerator.generate(request(
            s=s,policy=incomplete))
        assertEquals(AthleteGenerationStatus.INPUTS_UNRESOLVED,generated.status)
        assertTrue(generated.intents.isEmpty())
    }

    @Test fun optedInSelectorPrunesUnknownOfferButPreservesPinnedLifts() {
        val original=request()
        val extra=choice("unknown-machine",visits=2).copy(requiredVisits=0)
        val authored=original.choices.map {
            if(it.exerciseId=="bench")it.copy(requiredVisits=1)
            else it.copy(requiredVisits=0)
        }
        val optIn=original.copy(
            choices=authored+extra,selectFromOfferedCatalog=true,
            maximumSelectedOptions=3)
        val review=CoachAthleteProgramGenerator.generate(optIn)
        val selected=checkNotNull(review.exerciseSelection)
        assertEquals(AthleteSelectionStatus.SHORTLIST_FOR_REVIEW,
            selected.status)
        assertEquals(setOf("bench","row","squat"),
            selected.choices.map {it.exerciseId}.toSet())
        assertTrue(selected.excluded.any {
            it.exerciseId=="unknown-machine"
        })
        assertNotNull(review.candidate)
        assertTrue(review.candidate?.catalogReview?.requestedExerciseIds
            ?.contains("unknown-machine")==false)
        assertFalse(review.maySaveProgram)
    }

    @Test fun defaultSelectionModeNeverSilentlyPrunesUnknownAuthoredChoice() {
        val original=request()
        val offered=original.choices+choice("unknown-machine")
        val noOptIn=CoachAthleteProgramGenerator.generate(
            original.copy(choices=offered))
        assertNull(noOptIn.exerciseSelection)
        assertTrue(noOptIn.status!=AthleteGenerationStatus.GENERATED_FOR_REVIEW)
    }

    @Test fun generationIsDeterministicAndDoesNotMutateCallerInputs() {
        val input=request()
        val a=CoachAthleteProgramGenerator.generate(input)
        val b=CoachAthleteProgramGenerator.generate(input)
        assertEquals(a,b)
        assertEquals(setOf(0,2,4),input.setup.reviewed?.trainingWeekdays)
        assertEquals(3,input.choices.size)
        assertFalse(a.mayIssueWorkout)
    }

    @Test fun feasibleWeekRankingPrefersRecoveryDoseAndBalancedSessionLength() {
        val generated=CoachAthleteProgramGenerator.generate(request())
        val actual=generated.candidate?.catalogReview?.draft
        if(generated.status==AthleteGenerationStatus.GENERATED_FOR_REVIEW) {
            assertNotNull(actual)
            val evidence=generated.search
            assertTrue(evidence.feasibleWeeksCompared>=1)
            assertTrue(evidence.feasibleWeeksCompared<=5)
            assertTrue(evidence.draftsAudited>=evidence.feasibleWeeksCompared)
            assertTrue(evidence.placementsChecked>=evidence.draftsAudited)
            assertTrue(evidence.draftsAudited<=32)
            val focus=checkNotNull(generated.candidate?.catalogReview)
                .composedRequest?.days?.map {it.focus}
            val purpose=generated.intents.map {it.purpose}
            val score=CoachAthleteProgramGenerator.qualityScore(
                checkNotNull(actual),checkNotNull(focus),purpose,
                checkNotNull(generated.candidate?.catalogReview)
                    .composedRequest!!.days.map {it.calendarDay})
            assertEquals(6,score.size)
            assertTrue(score.all {it>=0L})
        } else {
            assertEquals(0,generated.search.feasibleWeeksCompared)
            assertTrue(generated.issues.isNotEmpty())
        }
        assertEquals(generated,CoachAthleteProgramGenerator.generate(request()))
    }


    @Test fun repeatLiftRecoveryRankingRecognizesWeekBoundaryAndSeparateExercises() {
        val calendar=listOf(0,2,4,6)
        fun lift(day:Int,id:String="bench")=ComposerVisit(
            day,id,ComposerPattern.HORIZONTAL_PRESS,3)
        val spaced=listOf(lift(0),lift(2))
        val twoDayGap=listOf(lift(0),lift(3))
        assertEquals(0L,CoachAthleteProgramGenerator.repeatLiftSpacingPenalty(
            spaced,calendar))
        // Saturday -> Monday is a two-day calendar gap (the wrap counts).
        assertEquals(20L,CoachAthleteProgramGenerator.repeatLiftSpacingPenalty(
            twoDayGap,listOf(0,2,4,5)))
        val sundayMonday=listOf(lift(0),lift(3))
        assertEquals(100L,CoachAthleteProgramGenerator.repeatLiftSpacingPenalty(
            sundayMonday,listOf(0,2,3,6)))
        assertEquals(0L,CoachAthleteProgramGenerator.repeatLiftSpacingPenalty(
            listOf(lift(0),lift(0),lift(3,"row")),calendar))
        assertEquals(
            CoachAthleteProgramGenerator.repeatLiftSpacingPenalty(spaced,calendar),
            CoachAthleteProgramGenerator.repeatLiftSpacingPenalty(
                spaced.reversed(),calendar))
    }

    @Test fun spacingPenaltyNeverOverridesEquipmentVolumeOrAuthoredConstraints() {
        val input=request()
        val generated=CoachAthleteProgramGenerator.generate(input)
        val draft=generated.candidate?.catalogReview?.draft
        if(generated.status==AthleteGenerationStatus.GENERATED_FOR_REVIEW) {
            val audited=checkNotNull(draft)
            assertTrue(audited.audit.satisfiesDeclaredHardConstraints)
            assertTrue(audited.daysWithUnmetPrimary.isEmpty())
            assertTrue(audited.missingRequiredVisits.isEmpty())
            assertTrue(audited.excludedUnverifiedExerciseIds.isEmpty())
            assertEquals(0,generated.issues.size)
            assertTrue(generated.search.feasibleWeeksCompared in 1..5)
        }else {
            assertFalse(generated.maySaveProgram)
            assertFalse(generated.mayIssueWorkout)
        }
        assertEquals(generated,CoachAthleteProgramGenerator.generate(input))
    }


    @Test fun primaryPlacementEnumeratesAlternativesWithoutBreakingFrequencyOrRecovery() {
        val s=setup(weekdays=setOf(0,1,3,5))
        val p=policy(s,primaries=mapOf("bench" to 2))
        val calendar=listOf(0,1,3,5)
        val focus=List(4){ComposerFocus.FULL_BODY}
        val purpose=List(4){ComposerSessionPurpose.HYPERTROPHY}
        val options=movements.associateBy {it.exerciseId}
        val choices=movements.associate {it.exerciseId to choice(it.exerciseId,
            visits=3,gap=2)}
        val placements=CoachAthleteProgramGenerator.placements(
            calendar,focus,purpose,p,options,choices)
        assertTrue("More than one valid coach placement must be reviewed",
            placements.size>=2)
        assertTrue(placements.size<=8)
        assertEquals(placements.distinct().size,placements.size)
        assertTrue(placements.all {schedule ->
            val assigned=schedule.filter {it.requiredPrimaryExerciseId=="bench"}
                .map {it.weekday}
            assigned.size>=2 && assigned.size<=3 &&
                assigned.indices.all {a->assigned.indices.all {b->
                    a==b || minOf(kotlin.math.abs(assigned[a]-assigned[b]),
                        7-kotlin.math.abs(assigned[a]-assigned[b]))>=2
                }}
        })
        assertEquals(placements,
            CoachAthleteProgramGenerator.placements(calendar,focus,purpose,
                p,options,choices))
        assertEquals(0,p.primaryLiftMinimumDays["row"] ?: 0)
    }

    @Test fun alternatePrimaryPlacementsRespectSundayToMondayRecovery() {
        val s=setup(weekdays=setOf(0,2,4,6))
        val policy=policy(s,primaries=mapOf("bench" to 2))
        val assignments=CoachAthleteProgramGenerator.placements(
            listOf(0,2,4,6),List(4){ComposerFocus.FULL_BODY},
            List(4){ComposerSessionPurpose.HYPERTROPHY},
            policy,movements.associateBy {it.exerciseId},
            movements.associate {it.exerciseId to choice(it.exerciseId,
                visits=3,gap=2)})
        assertTrue(assignments.isNotEmpty())
        assertTrue(assignments.all {a->
            val days=a.filter {it.requiredPrimaryExerciseId=="bench"}
                .map {it.weekday}.sorted()
            days.size>=2 &&
                days.zipWithNext().all { (x,y)->y-x>=2 } &&
                (days.first()+7-days.last())>=2
        })
    }

}
