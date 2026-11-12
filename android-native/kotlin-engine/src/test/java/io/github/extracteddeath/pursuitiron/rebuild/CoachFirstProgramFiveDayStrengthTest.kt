package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

/**
 * The real five-day ULPPL source path, not a mocked set of desired labels.
 * A 2 bench / 2 squat / 1 conventional deadlift strength week has five
 * physical exposures on FOUR strength-skill days: a verified lower day
 * contains both squat and deadlift. No sixth day or cross-lift load transfer.
 */
class CoachFirstProgramFiveDayStrengthTest {
    private fun n(v:String)=BigDecimal(v)
    private val bar=VerifiedLoadStock.Barbell(LoadUnit.LB,n("45"),
        mapOf(n("45") to 2,n("25") to 2,n("10") to 2,n("5") to 2))
    private val dumbbells=VerifiedLoadStock.Totals(LoadUnit.LB,
        LoadBasis.DUMBBELL_PER_HAND,listOf(n("15"),n("20"),n("25"),n("30"),
            n("35"),n("40"),n("45"),n("50")))
    private fun setup(gym:GymProfileDraft=GymProfileDraft(
        "five-day-gym",LoadUnit.LB,
        mapOf("straight-bar" to bar,"dumbbell-rack" to dumbbells),
        setOf("flat-bench","incline-bench","rack"),emptySet()
    ))=CoachAthleteSetupDomain.review(AthleteSetupDraft(
        profileRevision="revision-5day",athleteProfileId="athlete-5day",
        goal=AthleteTrainingGoal.POWERBUILDING,
        experience=AthleteExperience.ADVANCED,
        split=ComposerSplit.ULPPL,
        trainingWeekdays=setOf(0,1,3,4,5),
        timezoneId="America/New_York",
        sessionTimeBucket=SessionTimeBucket.FORTY_TO_SIXTY,
        hardSessionLimit=HardSessionLimit.NO_HARD_LIMIT,
        gym=gym,allowBodyweight=false,allowSupersets=false,
        allowTricepsOnLower=false,maxBarbellExercisesPerDay=2,
        focusedMuscles=emptySet(),reducedMuscles=emptySet(),
        excludedExerciseIds=emptySet(),lockedTrainingDays=emptySet(),
        hardTrainingDays=emptySet(),longTrainingDays=emptySet(),
        declaredRestDays=setOf(2,6)
    ))
    private fun calibrations()=linkedMapOf(
        "barbell_bench" to n("135"),"back_squat" to n("155"),
        "conventional_deadlift" to n("185"),
        "incline_db_press" to n("30"),
        "one_arm_db_row" to n("30"),
        "db_lateral_raise" to n("20"),
        "db_curl" to n("20"),
        "db_overhead_triceps" to n("20"),
        "db_calf_raise" to n("30"),
        // Independently calibrated hinge support, not a second heavy
        // conventional deadlift or an inferred cross-exercise load.
        "db_rdl" to n("30"),
        "db_split_squat" to n("30"),
        "db_rear_delt_raise" to n("20")
    )
    private fun input(
        reviewed:AthleteSetupReview=setup(),
        starts:Map<String,BigDecimal> =calibrations(),
        phase:TrainingPhase=TrainingPhase.STRENGTH,
        // Match the production first-program default: 15 minutes of declared
        // non-set/setup time, instead of forcing a 35-minute overhead into
        // a 40–60-minute bucket before the actual lifts are counted.
        overhead:Int=900,
        version:String=CoachFirstProgramStartDomain.POLICY_VERSION
    )=FirstProgramStartRequest(reviewed,
        StarterExerciseTranche.movements(),starts,emptySet(),phase,
        nonSetSecondsPerDay=overhead,policyVersion=version)

    @Test fun calibratedFiveDayULPPLCanReviewTwoBenchTwoSquatOneDeadliftWithNoLoadTransfer() {
        val result=CoachFirstProgramStartDomain.review(input())
        assertEquals(
            result.issues.joinToString(" | ")+
                " | candidate="+result.generation?.candidate?.issues+
                " | week="+result.generation?.candidate?.catalogReview?.draft?.let{
                    "primaryMissing="+it.daysWithUnmetPrimary+
                    "; requiredMissing="+it.missingRequiredVisits+
                    "; dose="+it.audit.muscles.values.filter {m->
                        m.gapToMev.signum()>0 || m.missingDirectDays>0 ||
                            m.excessAboveMrv.signum()>0
                    }.map{m->m.muscle to m.gapToMev}+
                    "; time="+it.audit.daysOutsidePreferredWindow+
                    "; sessions="+it.audit.sessions.map{day->
                        "${day.dayIndex}:${day.roundedMinutes}m:${day.finding}"
                    }
                },
            FirstProgramStartStatus.PRESCRIBED_DRAFT_FOR_REVIEW,
            result.status)
        assertEquals(1,result.phasePoliciesChecked)
        assertEquals(mapOf("barbell_bench" to 2,"back_squat" to 2),
            result.policy?.primaryLiftMinimumDays)
        val generation=checkNotNull(result.generation)
        assertEquals(AthleteGenerationStatus.GENERATED_FOR_REVIEW,
            generation.status)
        val week=checkNotNull(generation.candidate?.catalogReview?.draft)
        val weekdays=checkNotNull(generation.candidate?.catalogReview
            ?.composedRequest).days.map{it.calendarDay}
        assertEquals(5,week.sessions.size)
        assertEquals(4,week.sessions.count{
            it.purpose==ComposerSessionPurpose.STRENGTH_SKILL
        })
        val visits=week.visits
        fun days(id:String)=visits.filter{it.exerciseId==id}
            .map{weekdays[it.dayIndex]}.toSet()
        assertEquals(2,days("barbell_bench").size)
        assertEquals(2,days("back_squat").size)
        assertEquals(1,days("conventional_deadlift").size)
        val deadliftIndex=visits.single{
            it.exerciseId=="conventional_deadlift"
        }.dayIndex
        assertEquals(ComposerSessionPurpose.STRENGTH_SKILL,
            week.sessions[deadliftIndex].purpose)
        assertTrue(week.sessions[deadliftIndex].orderedVisits.any{
            it.exerciseId=="back_squat"
        })
        // Three independently calibrated barbell skills must keep three
        // distinct working loads in the actual prescription. Comparing the
        // fixture to itself would not catch a cross-exercise weight transfer.
        val expectedLoads=mapOf("barbell_bench" to n("135"),
            "back_squat" to n("155"),"conventional_deadlift" to n("185"))
        val prescribed=checkNotNull(result.prescription)
        for((exerciseId,calibrated) in expectedLoads) {
            val actual=prescribed.sessions.flatMap {it.exercises}
                .filter {it.exerciseId==exerciseId}
            assertTrue("$exerciseId was never prescribed",actual.isNotEmpty())
            val working=actual.flatMap {it.sets }
                .filter {it.type==DraftSetType.WORKING}
            assertTrue("$exerciseId has no working sets",working.isNotEmpty())
            assertTrue("$exerciseId silently borrowed another load: $working",
                working.all {
                    (it.load as? DraftSetLoad.Verified)?.value==calibrated
                })
        }
        assertTrue(week.audit.satisfiesDeclaredHardConstraints)
        assertTrue(checkNotNull(result.prescription).finalAudit
            ?.daysOutsidePreferredWindow?.isEmpty()==true)
        assertFalse(result.mayIssueWorkout)
        assertFalse(result.maySaveProgram)
        // Do not stop at a correct preview: the actual four-week authored
        // cycle must preserve the companion deadlift as a protected exercise
        // and pass the same native all-week coaching audit.
        val source=input()
        val converted=CoachFirstProgramDraftConversionDomain.propose(
            result,source.setup,source.catalog,
            "five-day-first-block","ULPPL strength",4)
        assertEquals(converted.reason,
            FirstProgramDraftStatus.READY_FOR_SAVING_DRAFT,
            converted.status)
        val block=checkNotNull(converted.cycle).blocks.single()
        assertEquals(4,block.weeks)
        val visitsByDay=block.days.flatMap{day->
            day.slots.map{day.weekday to it}
        }
        val companion=visitsByDay.single{
            it.second.exerciseId=="conventional_deadlift"
        }
        assertTrue(companion.second.pinned)
        assertEquals(2,visitsByDay.count{
            it.second.exerciseId=="barbell_bench" && it.second.pinned
        })
        assertEquals(2,visitsByDay.count{
            it.second.exerciseId=="back_squat" && it.second.pinned
        })
        val auditInputs=checkNotNull(CoachFirstProgramDraftConversionDomain
            .policyForInitialFullCycleReview(result,source.setup))
        val audit=CustomCycleCoachAuditDomain.review(
            checkNotNull(converted.cycle),source.setup,source.catalog,auditInputs)
        assertEquals(audit.issues.toString(),
            CustomCoachAuditStatus.READY_FOR_ATHLETE_REVIEW,audit.status)
        assertEquals(result,CoachFirstProgramStartDomain.review(input()))
    }

    @Test fun strengthAndPeakKeepTheFiveDayTargetButV6SavedPolicyReplays() {
        val peak=CoachFirstProgramStartDomain.review(
            input(phase=TrainingPhase.PEAK))
        assertEquals(mapOf("barbell_bench" to 2,"back_squat" to 2),
            peak.policy?.primaryLiftMinimumDays)
        assertEquals(1,peak.phasePoliciesChecked)
        // A v7 "peak" label or minimum-days policy is not proof the real
        // composed peak week contains the requested exercise exposures.
        assertEquals("Peak week is not fully reviewable: ${peak.issues}",
            FirstProgramStartStatus.PRESCRIBED_DRAFT_FOR_REVIEW,peak.status)
        val peakWeek=checkNotNull(peak.generation?.candidate?.catalogReview?.draft)
        val peakVisits=peakWeek.visits
        val benchDays=peakVisits.filter{it.exerciseId=="barbell_bench"}
            .map{it.dayIndex}.toSet()
        val squatDays=peakVisits.filter{it.exerciseId=="back_squat"}
            .map{it.dayIndex}.toSet()
        val deadliftDays=peakVisits.filter{it.exerciseId=="conventional_deadlift"}
            .map{it.dayIndex}.toSet()
        assertEquals(2,benchDays.size)
        assertEquals(2,squatDays.size)
        assertEquals(1,deadliftDays.size)
        assertTrue(deadliftDays.single() in squatDays)
        assertTrue(benchDays.intersect(squatDays).isEmpty())
        assertTrue(peakWeek.audit.satisfiesDeclaredHardConstraints)
        assertTrue(checkNotNull(peak.prescription?.finalAudit)
            .daysOutsidePreferredWindow.isEmpty())
        assertFalse(peak.mayIssueWorkout)
        // This is not proof of an individualized taper or meet-day peak.
        val previous=CoachFirstProgramStartDomain.review(input(
            version=CoachFirstProgramStartDomain.V6_POLICY_VERSION))
        assertEquals("first-program-reference-v6",previous.policy?.policyId)
        assertEquals("v6",previous.policy?.revision)
        assertTrue(CoachFirstProgramStartDomain.supportsPolicy(
            CoachFirstProgramStartDomain.V6_POLICY_VERSION))
        assertEquals("v8",CoachFirstProgramStartDomain.review(input())
            .policy?.revision)
        val savedV7=CoachFirstProgramStartDomain.review(input(
            version=CoachFirstProgramStartDomain.V7_POLICY_VERSION))
        assertEquals("v7",savedV7.policy?.revision)
        assertEquals(mapOf("barbell_bench" to 2,"back_squat" to 2),
            savedV7.policy?.primaryLiftMinimumDays)
        assertEquals(FirstProgramStartStatus.PRESCRIBED_DRAFT_FOR_REVIEW,
            savedV7.status)
    }

    @Test fun impossibleGymAndTimeNeverQuietlyReduceTheNamedSkillFrequency() {
        val absentBar=setup(GymProfileDraft("no-barbell",LoadUnit.LB,
            mapOf("dumbbell-rack" to dumbbells),
            setOf("flat-bench","incline-bench","rack"),emptySet()))
        val absent=CoachFirstProgramStartDomain.review(
            input(reviewed=absentBar))
        assertEquals(FirstProgramStartStatus.INVALID_CALIBRATION,absent.status)
        assertFalse(absent.mayIssueWorkout)
        // The failed v7 rehearsal with 35 minutes of non-set overhead
        // had correct named lifts but sessions lasting 60–80 minutes.
        // The product must refuse that 40–60-minute preview rather than
        // silently hide the violation or reduce the required 2/2/1 visits.
        val cramped=CoachFirstProgramStartDomain.review(input(overhead=2100))
        assertEquals(FirstProgramStartStatus.DRAFT_NEEDS_REVIEW,cramped.status)
        assertEquals(mapOf("barbell_bench" to 2,"back_squat" to 2),
            cramped.policy?.primaryLiftMinimumDays)
        assertFalse(cramped.mayIssueWorkout)
                val tooLong=CoachFirstProgramStartDomain.review(input(overhead=3600))
        assertEquals(FirstProgramStartStatus.DRAFT_NEEDS_REVIEW,tooLong.status)
        assertEquals(mapOf("barbell_bench" to 2,"back_squat" to 2),
            tooLong.policy?.primaryLiftMinimumDays)
        assertTrue(tooLong.issues.any{
            it.contains("No lower-frequency substitution was silently accepted")
        })
        assertFalse(tooLong.mayIssueWorkout)
        assertEquals(1,tooLong.phasePoliciesChecked)
    }

    @Test fun noUncalibratedDeadliftOrRomanianHingeCanPretendToBeConventionalSkill() {
        val without=calibrations()-"conventional_deadlift"
        val alternative=CoachFirstProgramStartDomain.review(input(
            starts=without+("db_rdl" to n("30"))))
        assertFalse(alternative.issues.any{it.contains(
            "bench 2 / squat 2 / deadlift 1 goal")})
        assertFalse(alternative.chosenExerciseIds.contains("conventional_deadlift"))
        assertFalse(alternative.mayIssueWorkout)
    }
}
