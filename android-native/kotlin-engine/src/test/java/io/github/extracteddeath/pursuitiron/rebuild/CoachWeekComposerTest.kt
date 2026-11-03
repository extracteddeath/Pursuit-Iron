package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class CoachWeekComposerTest {
    private fun n(v:String)=BigDecimal(v)
    private fun credit(m:TrainingMuscle)=DeclaredMuscleCredit(
        m,MuscleCreditRole.DIRECT,BigDecimal.ONE)
    private fun bounds(vararg specific:DeclaredMuscleDoseBounds)=
        TrainingMuscle.entries.map {muscle->
            specific.firstOrNull {it.muscle==muscle} ?:dose(muscle,"0","0","0")
        }
    private fun dose(m:TrainingMuscle,min:String,max:String,cap:String,
        directDays:Int=0)=DeclaredMuscleDoseBounds(m,n(min),n(max),n(cap),directDays)
    private val available=VerifiedLoadStock.Totals(LoadUnit.LB,
        LoadBasis.DUMBBELL_PER_HAND,listOf(n("25"),n("30"),n("35"),n("40")))
    private fun option(id:String,m:TrainingMuscle,pattern:ComposerPattern,
        focus: Set<ComposerFocus> = setOf(ComposerFocus.FULL_BODY),
        equipment:ComposerEquipment=ComposerEquipment.Verified(
            LoadUnit.LB,n("30"),available),
        required:Int=0,visits:Int=3,gap:Int=2,priority:Int=1)=
        ComposerExerciseOption(id,pattern,focus,equipment,listOf(credit(m)),
            workSeconds=90,restSeconds=180,
            minimumSetsPerVisit=2,maximumSetsPerVisit=4,
            maximumVisits=visits,requiredVisits=required,
            minimumCalendarDayGap=gap,priority=priority)
    private val chest=TrainingMuscle.CHEST
    private val lats=TrainingMuscle.LATS
    private val quads=TrainingMuscle.QUADS
    private val chestOption=option("db-press",chest,ComposerPattern.HORIZONTAL_PRESS)
    private val backOption=option("db-row",lats,ComposerPattern.HORIZONTAL_PULL)
    private val legOption=option("goblet-squat",quads,ComposerPattern.SQUAT)
    // A fourth compatible pattern makes 2-pattern days possible across
    // Monday/Sunday without violating repeated-exercise recovery gaps.
    private val chestFly=option("chest-fly",chest,ComposerPattern.ISOLATION,
        gap=1)
    private fun days(calendar:List<Int> = listOf(0,2,4,6),
        focuses:List<ComposerFocus> = List(calendar.size){ComposerFocus.FULL_BODY},
        min:Int=18,max:Int?=50,hard:Int?=null,overhead:Int=600)=
        calendar.indices.map {i->
            ComposerDay(calendar[i],focuses[i],
                DeclaredSessionWindow(min,max,hard,overhead))
        }
    private fun base(
        exerciseOptions:List<ComposerExerciseOption> =
            listOf(chestOption,backOption,legOption,chestFly),
        bands:List<DeclaredMuscleDoseBounds> =
            bounds(dose(chest,"4","8","9",2),dose(lats,"4","8","9",2),
                dose(quads,"4","8","9",2)),
        schedule:List<ComposerDay> = days(),
        split:ComposerSplit=ComposerSplit.FULL_BODY,
        patterns:Int=2,
        maxPerDay:Int=4
    )=ComposerRequest(split,TrainingPhase.HYPERTROPHY,
        schedule,bands,exerciseOptions,maxPerDay,patterns)

    @Test fun fourDayWeekBuildsRealDaysAndFulfillsDeclaredMuscleTargets() {
        val request=base()
        val output=CoachWeekComposer.compose(request)
        assertEquals(ComposerStatus.FEASIBLE_DRAFT_FOR_REVIEW,output.status)
        assertFalse(output.canIssueTargets)
        assertTrue(output.audit.satisfiesDeclaredHardConstraints)
        assertTrue(output.audit.daysWithoutWorkingSets.isEmpty())
        assertTrue(output.audit.daysOutsidePreferredWindow.isEmpty())
        assertTrue(output.daysBelowPatternPreference.isEmpty())
        assertTrue(output.missingRequiredVisits.isEmpty())
        assertTrue(output.visits.groupBy {it.dayIndex}.values.all {
            it.map {v->v.pattern}.distinct().size>=2
        })
        for(m in listOf(chest,lats,quads)) {
            val actual=checkNotNull(output.audit.muscles[m])
            assertTrue(actual.effectiveWorkingSets>=n("4"))
            assertTrue(actual.effectiveWorkingSets<=n("9"))
            assertTrue(actual.directTrainingDays>=2)
        }
        assertEquals(output.visits.sumOf {it.sets},output.week.exposures.size)
        assertTrue(output.week.exposures.all {it.kind==ExposureSetKind.WORKING})
    }

    @Test fun plannedDurationDoesNotInventARecoveryPeriodAfterWorkoutEnds() {
        val request=base()
        val result=CoachWeekComposer.compose(request)
        val byDay=result.week.exposures.groupBy {it.dayIndex}
        for(session in result.sessions) {
            val finalVisit=checkNotNull(session.orderedVisits.lastOrNull())
            val finalKey=finalVisit.exerciseId+":d"+session.dayIndex+
                ":s"+(finalVisit.sets-1)
            val exposures=checkNotNull(byDay[session.dayIndex])
            assertEquals(0,exposures.single {it.setKey==finalKey}.restSeconds)
            assertTrue(exposures.filter {it.setKey!=finalKey}.all {
                it.restSeconds==180
            })
            val expected=exposures.sumOf {
                it.activeSeconds.toLong()+it.restSeconds
            }+request.days[session.dayIndex].window.declaredNonSetSeconds
            assertEquals(expected,result.audit.sessions[session.dayIndex].declaredSeconds)
        }
        assertFalse(result.canIssueTargets)
    }

    @Test fun identicalInputsYieldIdenticalWeekWithoutWritingWorkoutTargets() {
        val request=base()
        val first=CoachWeekComposer.compose(request)
        val second=CoachWeekComposer.compose(request)
        assertEquals(first.visits,second.visits)
        assertEquals(first.week,second.week)
        assertEquals(first.audit,second.audit)
        assertFalse(first.canIssueTargets)
        assertEquals(4,request.exercises.size)
    }

    @Test fun requiredAuthoredExercisesAreNeverSilentlyDropped() {
        val must=chestOption.copy(requiredVisits=2,priority=10)
        val output=CoachWeekComposer.compose(base(exerciseOptions=listOf(
            must,backOption,legOption)))
        assertTrue(output.visits.count {it.exerciseId==must.exerciseId}>=2)
        assertFalse(must.exerciseId in output.missingRequiredVisits)
        assertEquals(2,must.requiredVisits)
    }

    @Test fun unavailableEquipmentIsRejectedEvenWhenExerciseIsPinned() {
        val invalid=chestOption.copy(requiredVisits=2,
            equipment=ComposerEquipment.Verified(LoadUnit.LB,n("32.5"),available))
        val output=CoachWeekComposer.compose(base(exerciseOptions=listOf(
            invalid,backOption,legOption)))
        assertTrue(invalid.exerciseId in output.excludedUnverifiedExerciseIds)
        assertEquals(2,output.missingRequiredVisits[invalid.exerciseId])
        assertTrue(output.visits.none {it.exerciseId==invalid.exerciseId})
        assertEquals(ComposerStatus.CONSTRAINTS_UNRESOLVED,output.status)
    }

    @Test fun unverifiedInventoryDoesNotBecomeAHomeGymWeight() {
        val invalid=chestOption.copy(equipment=ComposerEquipment.Verified(
            LoadUnit.KG,n("30"),available))
        val output=CoachWeekComposer.compose(base(exerciseOptions=listOf(
            invalid,backOption,legOption)))
        assertTrue(invalid.exerciseId in output.excludedUnverifiedExerciseIds)
        assertFalse(output.audit.satisfiesDeclaredHardConstraints)
    }

    @Test fun verifiedBodyweightIsExplicitAndDoesNotRequirePhantomPlates() {
        val body=chestOption.copy(equipment=ComposerEquipment.Bodyweight)
        val report=CoachWeekComposer.compose(base(exerciseOptions=listOf(
            body,backOption,legOption)))
        assertFalse(body.exerciseId in report.excludedUnverifiedExerciseIds)
        assertTrue(report.visits.any {it.exerciseId==body.exerciseId})
    }

    @Test fun cyclicalRecoveryGapBlocksSundayMondayRepeatAsRealCalendarDays() {
        val chestOnly=chestOption.copy(requiredVisits=2,maximumVisits=2,
            minimumCalendarDayGap=2,
            allowedFocus=setOf(ComposerFocus.FULL_BODY))
        val schedule=days(listOf(0,3,6))
        val other=option("rows",lats,ComposerPattern.HORIZONTAL_PULL,
            gap=1,visits=3)
        val leg=option("legs",quads,ComposerPattern.SQUAT,gap=1,visits=3)
        val result=CoachWeekComposer.compose(base(
            exerciseOptions=listOf(chestOnly,other,leg),
            schedule=schedule,
            bands=bounds(dose(chest,"2","4","5",1),dose(lats,"2","6","8",1),
                dose(quads,"2","6","8",1))))
        val chestDays=result.visits.filter {it.exerciseId=="db-press"}
            .map {schedule[it.dayIndex].calendarDay}
        assertTrue(chestDays.size>=2)
        assertFalse(0 in chestDays && 6 in chestDays)
    }

    @Test fun impossibleMuscleDoseIsReportedWithoutFabricatedExercises() {
        val one=chestOption.copy(maximumVisits=1,maximumSetsPerVisit=2,
            minimumSetsPerVisit=2)
        val result=CoachWeekComposer.compose(base(
            exerciseOptions=listOf(one,backOption,legOption),
            bands=bounds(dose(chest,"8","10","12",2),
                dose(lats,"4","6","8",1),dose(quads,"4","6","8",1))))
        assertEquals(ComposerStatus.CONSTRAINTS_UNRESOLVED,result.status)
        assertEquals(DoseFinding.BELOW_MEV,result.audit.muscles[chest]?.finding)
        assertFalse(result.canIssueTargets)
        assertTrue(result.explanations.isNotEmpty())
    }

    @Test fun noJunkSetsAreAddedPastMavToFakeLongSession() {
        val schedule=days(min=75,max=90,overhead=300)
        val request=base(schedule=schedule,
            bands=bounds(dose(chest,"2","2","2",1),
                dose(lats,"2","2","2",1),dose(quads,"2","2","2",1)))
        val result=CoachWeekComposer.compose(request)
        assertTrue(result.audit.daysOutsidePreferredWindow.isNotEmpty())
        assertEquals(ComposerStatus.CONSTRAINTS_UNRESOLVED,result.status)
        assertTrue(result.audit.muscles.values.all {
            it.finding!=DoseFinding.ABOVE_MRV
        })
        assertTrue(result.explanations.any {it.contains("time range")})
    }

    @Test fun upperLowerFocusRejectsLegOptionsOnUpperAndViceVersa() {
        val schedule=days(focuses=listOf(ComposerFocus.UPPER,ComposerFocus.LOWER,
            ComposerFocus.UPPER,ComposerFocus.LOWER))
        val push=chestOption.copy(allowedFocus=setOf(ComposerFocus.UPPER))
        val pull=backOption.copy(allowedFocus=setOf(ComposerFocus.UPPER))
        val legs=legOption.copy(allowedFocus=setOf(ComposerFocus.LOWER))
        val result=CoachWeekComposer.compose(base(exerciseOptions=listOf(
            push,pull,legs),schedule=schedule,split=ComposerSplit.UPPER_LOWER,
            bands=bounds(dose(chest,"2","6","8",1),
                dose(lats,"2","6","8",1),dose(quads,"2","6","8",1)),
            patterns=1))
        assertTrue(result.visits.all {visit->
            val f=schedule[visit.dayIndex].focus
            when(visit.exerciseId) {
                "goblet-squat" -> f==ComposerFocus.LOWER
                else -> f==ComposerFocus.UPPER
            }
        })
    }

    @Test fun pplAndUlpplSchedulesAreSupportedWithExplicitFocus() {
        val full=listOf(ComposerFocus.UPPER,ComposerFocus.LOWER,
            ComposerFocus.PUSH,ComposerFocus.PULL,ComposerFocus.LEGS)
        val schedule=days(listOf(0,1,2,4,5),full)
        val choices=listOf(
            chestOption.copy(allowedFocus=setOf(ComposerFocus.UPPER,ComposerFocus.PUSH)),
            backOption.copy(allowedFocus=setOf(ComposerFocus.UPPER,ComposerFocus.PULL)),
            legOption.copy(allowedFocus=setOf(ComposerFocus.LOWER,ComposerFocus.LEGS)))
        val result=CoachWeekComposer.compose(base(exerciseOptions=choices,
            schedule=schedule,split=ComposerSplit.ULPPL,patterns=1,
            bands=bounds(dose(chest,"2","6","8",1),dose(lats,"2","6","8",1),
                dose(quads,"2","6","8",1))))
        assertTrue(result.visits.isNotEmpty())
        assertEquals(5,result.audit.sessions.size)
        val ppl=days(listOf(0,2,4),listOf(
            ComposerFocus.PUSH,ComposerFocus.PULL,ComposerFocus.LEGS))
        val pplResult=CoachWeekComposer.compose(base(
            exerciseOptions=choices.map {opt->
                opt.copy(allowedFocus=when(opt.exerciseId) {
                    "db-press" -> setOf(ComposerFocus.PUSH)
                    "db-row" -> setOf(ComposerFocus.PULL)
                    else -> setOf(ComposerFocus.LEGS)
                })
            },
            schedule=ppl,split=ComposerSplit.PUSH_PULL_LEGS,patterns=1))
        assertEquals(3,pplResult.audit.sessions.size)
        assertTrue(pplResult.visits.isNotEmpty())
    }

    @Test fun illFormedSchedulesAndInventedCreditsAreRejected() {
        val original=base()
        val invalid=listOf(
            original.copy(days=days(listOf(0,0,2,4))),
            original.copy(days=days(listOf(0,4,2,6))),
            original.copy(days=days(listOf(0,2))),
            original.copy(split=ComposerSplit.UPPER_LOWER),
            original.copy(exercises=listOf(chestOption,chestOption)),
            original.copy(exercises=listOf(chestOption.copy(
                credits=listOf(credit(chest),credit(chest))))),
            original.copy(exercises=listOf(chestOption.copy(workSeconds=0))),
            original.copy(exercises=listOf(chestOption.copy(requiredVisits=4))),
            original.copy(exercises=listOf(chestOption.copy(
                minimumSetsPerVisit=5,maximumSetsPerVisit=4)))
        )
        invalid.forEach {request ->
            assertThrows(IllegalArgumentException::class.java) {
                CoachWeekComposer.compose(request)
            }
        }
    }

    @Test fun relatedHeavyMovementsRespectSharedRecoveryAcrossExerciseIds() {
        val first=chestOption.copy(exerciseId="bench-a",
            requiredVisits=1,recoveryGroup="heavy-press",
            minimumGroupCalendarDayGap=2)
        val second=chestOption.copy(exerciseId="bench-b",
            requiredVisits=1,recoveryGroup="heavy-press",
            minimumGroupCalendarDayGap=2)
        val schedule=days(calendar=listOf(0,1,4))
        val report=CoachWeekComposer.compose(base(
            exerciseOptions=listOf(first,second,backOption,legOption),
            schedule=schedule,patterns=1,
            bands=bounds(dose(chest,"4","8","10",1),
                dose(lats,"2","6","8",1),dose(quads,"2","6","8",1))))
        assertTrue(report.visits.any {it.exerciseId=="bench-a"})
        assertTrue(report.visits.any {it.exerciseId=="bench-b"})
        assertTrue(report.missingRequiredVisits.isEmpty())
        val heavy=report.visits.filter {
            it.exerciseId=="bench-a" || it.exerciseId=="bench-b"
        }
        for(a in heavy)for(b in heavy) {
            if(a.dayIndex==b.dayIndex)continue
            val gap=kotlin.math.abs(schedule[a.dayIndex].calendarDay-
                schedule[b.dayIndex].calendarDay)
            assertTrue(minOf(gap,7-gap)>=2)
        }
    }

    @Test fun malformedRecoveryGroupsCannotBypassTheScheduleAudit() {
        for(bad in listOf(
            chestOption.copy(recoveryGroup="Invalid Group!"),
            chestOption.copy(recoveryGroup="heavy-press",minimumGroupCalendarDayGap=0),
            chestOption.copy(recoveryGroup="heavy-press",minimumGroupCalendarDayGap=7)
        )) {
            assertThrows(IllegalArgumentException::class.java) {
                CoachWeekComposer.compose(base(exerciseOptions=listOf(bad,backOption,legOption)))
            }
        }
    }


    @Test fun authoredMainLiftStartsBeforeHigherPriorityOtherPrimary() {
        val required=chestOption.copy(exerciseId="main-bench",
            role=ComposerRole.PRIMARY,priority=1,
            requiredVisits=3,maximumVisits=3,minimumCalendarDayGap=1)
        val other=chestOption.copy(exerciseId="other-bench",
            role=ComposerRole.PRIMARY,priority=10,
            requiredVisits=3,maximumVisits=3,minimumCalendarDayGap=1)
        val schedule=days(calendar=listOf(0,2,4),min=1,max=90)
        val request=base(
            exerciseOptions=listOf(required,other,backOption,legOption),
            schedule=schedule,patterns=1,maxPerDay=4,
            bands=bounds(dose(chest,"12","20","24",1),
                dose(lats,"2","8","12",1),dose(quads,"2","8","12",1)))
            .copy(sessionIntents=listOf(
                ComposerSessionIntent(0,ComposerSessionPurpose.STRENGTH_SKILL,
                    "main-bench")))
        val result=CoachWeekComposer.compose(request)
        assertTrue(result.missingRequiredVisits.isEmpty())
        val first=result.sessions.first()
        assertTrue(first.requiredPrimaryScheduled)
        assertEquals("main-bench",first.orderedVisits.first().exerciseId)
        assertTrue(first.orderedVisits.any {it.exerciseId=="other-bench"})
        assertFalse(result.canIssueTargets)
    }

    @Test fun strengthDayPreservesExplicitMainLiftAndPlacesItBeforeAccessories() {
        val heavy=chestOption.copy(exerciseId="bench-primary",
            role=ComposerRole.PRIMARY,priority=8,
            requiredVisits=0,minimumCalendarDayGap=1,
            maximumVisits=1)
        val accessory=chestFly.copy(role=ComposerRole.ACCESSORY,priority=10)
        val request=base(exerciseOptions=listOf(heavy,accessory,backOption,legOption),
            patterns=1).copy(sessionIntents=listOf(
                ComposerSessionIntent(0,ComposerSessionPurpose.STRENGTH_SKILL,
                    "bench-primary")))
        val result=CoachWeekComposer.compose(request)
        assertEquals(emptySet<Int>(),result.daysWithUnmetPrimary)
        val monday=result.sessions.first()
        assertEquals(ComposerSessionPurpose.STRENGTH_SKILL,monday.purpose)
        assertEquals("bench-primary",monday.primaryExerciseId)
        assertTrue(monday.requiredPrimaryScheduled)
        assertEquals("bench-primary",monday.orderedVisits.first().exerciseId)
        assertFalse(result.canIssueTargets)
    }

    @Test fun unavailableMainLiftCannotBeSilentlyReplacedByAnotherPress() {
        val heavy=chestOption.copy(exerciseId="bench-primary",
            role=ComposerRole.PRIMARY,
            equipment=ComposerEquipment.Verified(LoadUnit.LB,n("32.5"),available))
        val substitute=chestFly.copy(role=ComposerRole.ACCESSORY)
        val report=CoachWeekComposer.compose(base(
            exerciseOptions=listOf(heavy,substitute,backOption,legOption),
            patterns=1).copy(sessionIntents=listOf(
                ComposerSessionIntent(0,ComposerSessionPurpose.STRENGTH_SKILL,
                    "bench-primary"))))
        assertEquals(setOf(0),report.daysWithUnmetPrimary)
        assertEquals(ComposerStatus.CONSTRAINTS_UNRESOLVED,report.status)
        assertFalse(report.sessions.first().requiredPrimaryScheduled)
        assertTrue("bench-primary" in report.excludedUnverifiedExerciseIds)
        assertTrue(report.explanations.any {it.contains("main lift")})
    }

    @Test fun dayIntentMustReferToRealPrimaryWithCompatibleDayFocus() {
        val valid=base(exerciseOptions=listOf(
            chestOption.copy(role=ComposerRole.PRIMARY),backOption,legOption))
        val bad=listOf(
            valid.copy(sessionIntents=listOf(
                ComposerSessionIntent(0,ComposerSessionPurpose.STRENGTH_SKILL,"db-row"))),
            valid.copy(sessionIntents=listOf(
                ComposerSessionIntent(0,ComposerSessionPurpose.HYPERTROPHY,"not-a-lift"))),
            valid.copy(sessionIntents=listOf(
                ComposerSessionIntent(4,ComposerSessionPurpose.STRENGTH_SKILL,"db-press"))),
            valid.copy(sessionIntents=listOf(
                ComposerSessionIntent(0,ComposerSessionPurpose.HYPERTROPHY),
                ComposerSessionIntent(0,ComposerSessionPurpose.STRENGTH_SKILL)))
        )
        bad.forEach {request ->
            assertThrows(IllegalArgumentException::class.java) {
                CoachWeekComposer.compose(request)
            }
        }
    }

    @Test fun ordinaryDaysKeepPurposeUnknownWithoutFakingStrengthIntent() {
        val result=CoachWeekComposer.compose(base())
        assertEquals(4,result.sessions.size)
        assertTrue(result.sessions.all {
            it.purpose==ComposerSessionPurpose.UNSPECIFIED &&
                it.primaryExerciseId==null && it.requiredPrimaryScheduled
        })
    }

}
