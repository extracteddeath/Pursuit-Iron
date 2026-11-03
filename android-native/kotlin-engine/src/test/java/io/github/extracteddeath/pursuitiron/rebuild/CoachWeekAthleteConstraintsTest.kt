package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class CoachWeekAthleteConstraintsTest {
    private fun n(s:String)=BigDecimal(s)
    private val bar=VerifiedLoadStock.Barbell(LoadUnit.LB,n("45"),
        mapOf(n("10") to 2))
    private val dumbbells=VerifiedLoadStock.Totals(LoadUnit.LB,
        LoadBasis.DUMBBELL_PER_HAND,listOf(n("20"),n("25"),n("30")))
    private fun option(
        id:String,muscle:TrainingMuscle,
        pattern:ComposerPattern,
        allowed:Set<ComposerFocus> = setOf(ComposerFocus.FULL_BODY),
        equipment:ComposerEquipment = ComposerEquipment.Verified(
            LoadUnit.LB,n("65"),bar),
        pinned:Int=1,
        maxVisits:Int=3
    )=ComposerExerciseOption(id,pattern,allowed,
        equipment,listOf(DeclaredMuscleCredit(muscle,MuscleCreditRole.DIRECT,n("1"))),
        90,120,2,3,maxVisits,pinned,1,5)
    private val bench=option("bar-bench",TrainingMuscle.CHEST,
        ComposerPattern.HORIZONTAL_PRESS)
    private val row=option("bar-row",TrainingMuscle.LATS,
        ComposerPattern.HORIZONTAL_PULL)
    private val squat=option("db-squat",TrainingMuscle.QUADS,ComposerPattern.SQUAT,
        equipment=ComposerEquipment.Verified(LoadUnit.LB,n("25"),dumbbells))
    private fun bounds()=TrainingMuscle.entries.map {
        DeclaredMuscleDoseBounds(it,n("0"),n("6"),n("10"),0)
    }
    private fun full(
        options:List<ComposerExerciseOption> =listOf(bench,row,squat),
        cap:Int?=1
    )=ComposerRequest(ComposerSplit.FULL_BODY,TrainingPhase.HYPERTROPHY,
        listOf(0,2,4).map {day ->
            ComposerDay(day,ComposerFocus.FULL_BODY,
                DeclaredSessionWindow(1,100,null,0))
        },bounds(),options,3,1,maximumBarbellExercisesPerDay=cap)

    @Test fun twoPinnedBarbellLiftsAreScheduledOnDifferentDays() {
        val plan=CoachWeekComposer.compose(full())
        assertTrue(plan.missingRequiredVisits.isEmpty())
        // A pinned visit is a minimum, NOT a maximum. The coach may
        // repeat a lift on another day while respecting the daily cap.
        assertTrue(plan.visits.count {it.exerciseId=="bar-bench"}>=1)
        assertTrue(plan.visits.count {it.exerciseId=="bar-row"}>=1)
        val barIds=setOf("bar-bench","bar-row")
        assertTrue(plan.visits.filter {it.exerciseId in barIds}
            .groupBy {it.dayIndex}.values.all {it.size<=1})
        assertEquals(plan.visits,CoachWeekComposer.compose(full()).visits)
        assertFalse(plan.canIssueTargets)
    }

    @Test fun explicitZeroBarbellCapKeepsUnmetPinnedExercisesVisible() {
        val original=full(cap=0)
        val result=CoachWeekComposer.compose(original)
        assertTrue(result.visits.none {
            it.exerciseId in setOf("bar-bench","bar-row")
        })
        assertEquals(1,result.missingRequiredVisits["bar-bench"])
        assertEquals(1,result.missingRequiredVisits["bar-row"])
        assertEquals(ComposerStatus.CONSTRAINTS_UNRESOLVED,result.status)
        assertTrue(result.explanations.any {it.contains("barbell-day limit")})
        assertTrue(result.visits.any {it.exerciseId=="db-squat"})
    }

    @Test fun upperLowerCanPlaceTricepsOnlyOnUpperDaysWhenDisabledOnLower() {
        val triceps=option("triceps",TrainingMuscle.TRICEPS,
            ComposerPattern.ISOLATION,
            allowed=setOf(ComposerFocus.UPPER,ComposerFocus.LOWER),
            equipment=ComposerEquipment.Verified(
                LoadUnit.LB,n("25"),dumbbells))
        val upper=option("upper-press",TrainingMuscle.CHEST,
            ComposerPattern.HORIZONTAL_PRESS,
            allowed=setOf(ComposerFocus.UPPER),
            equipment=ComposerEquipment.Verified(
                LoadUnit.LB,n("25"),dumbbells))
        val lower=option("lower-squat",TrainingMuscle.QUADS,
            ComposerPattern.SQUAT,allowed=setOf(ComposerFocus.LOWER),
            equipment=ComposerEquipment.Verified(
                LoadUnit.LB,n("25"),dumbbells))
        val days=listOf(ComposerFocus.UPPER,ComposerFocus.LOWER,
            ComposerFocus.UPPER,ComposerFocus.LOWER).mapIndexed {i,focus ->
            ComposerDay(i*2%7,focus,DeclaredSessionWindow(1,100,null,0))
        }.sortedBy {it.calendarDay}
        val request=ComposerRequest(ComposerSplit.UPPER_LOWER,
            TrainingPhase.HYPERTROPHY,days,bounds(),
            listOf(triceps,upper,lower),2,1,
            allowDirectTricepsOnLower=false)
        val result=CoachWeekComposer.compose(request)
        assertTrue(result.visits.any {it.exerciseId=="triceps"})
        assertTrue(result.visits.filter {it.exerciseId=="triceps"}.all {
            days[it.dayIndex].focus==ComposerFocus.UPPER
        })
        val allowed=CoachWeekComposer.compose(
            request.copy(allowDirectTricepsOnLower=true))
        assertFalse(allowed.canIssueTargets)
    }

    @Test fun lowerOnlyTricepsIsUnresolvedRatherThanForcedIntoLowerDay() {
        val forbidden=option("lower-triceps",TrainingMuscle.TRICEPS,
            ComposerPattern.ISOLATION,
            allowed=setOf(ComposerFocus.LOWER),
            equipment=ComposerEquipment.Verified(LoadUnit.LB,n("25"),dumbbells))
        val upper=option("upper-press",TrainingMuscle.CHEST,
            ComposerPattern.HORIZONTAL_PRESS,allowed=setOf(ComposerFocus.UPPER),
            equipment=ComposerEquipment.Verified(LoadUnit.LB,n("25"),dumbbells))
        val lower=option("lower-squat",TrainingMuscle.QUADS,
            ComposerPattern.SQUAT,allowed=setOf(ComposerFocus.LOWER),
            equipment=ComposerEquipment.Verified(LoadUnit.LB,n("25"),dumbbells))
        val dayFocus=listOf(ComposerFocus.UPPER,ComposerFocus.LOWER,
            ComposerFocus.UPPER,ComposerFocus.LOWER)
        val request=ComposerRequest(ComposerSplit.UPPER_LOWER,
            TrainingPhase.HYPERTROPHY,dayFocus.mapIndexed {day,focus ->
                ComposerDay(day,focus,DeclaredSessionWindow(1,100,null,0))
            },bounds(),listOf(forbidden,upper,lower),2,1,
            allowDirectTricepsOnLower=false)
        val result=CoachWeekComposer.compose(request)
        assertEquals(1,result.missingRequiredVisits["lower-triceps"])
        assertTrue(result.visits.none {it.exerciseId=="lower-triceps"})
        assertEquals(ComposerStatus.CONSTRAINTS_UNRESOLVED,result.status)
    }

    @Test fun localRepairCannotSneakSecondBarbellOntoFullDay() {
        val source=full(cap=1)
        val authored=listOf(
            ComposerVisit(0,"bar-bench",ComposerPattern.HORIZONTAL_PRESS,2),
            ComposerVisit(1,"bar-row",ComposerPattern.HORIZONTAL_PULL,2),
            ComposerVisit(2,"db-squat",ComposerPattern.SQUAT,2))
        val repaired=CoachWeekComposer.rebalanceForReview(source,authored)
        assertTrue(repaired.filter {
            it.exerciseId in setOf("bar-bench","bar-row")
        }.groupBy {it.dayIndex}.values.all {it.size<=1})
        assertThrows(IllegalArgumentException::class.java) {
            CoachWeekComposer.rebalanceForReview(source,authored+
                ComposerVisit(0,"bar-row",ComposerPattern.HORIZONTAL_PULL,2))
        }
    }

    @Test fun capDoesNotConfuseSmithAndDumbbellsWithBarbell() {
        val smith=option("smith-press",TrainingMuscle.CHEST,
            ComposerPattern.HORIZONTAL_PRESS,
            equipment=ComposerEquipment.Verified(LoadUnit.LB,n("65"),
                VerifiedLoadStock.Totals(LoadUnit.LB,LoadBasis.SMITH_TOTAL,
                    listOf(n("65")))))
        val noBar=full(options=listOf(smith,squat),cap=0)
        val plan=CoachWeekComposer.compose(noBar)
        assertTrue(plan.visits.any {it.exerciseId=="smith-press"})
        assertFalse(plan.visits.any {it.exerciseId=="bar-bench"})
    }

    @Test fun pplLegsDayAlsoRespectsDisabledDirectTriceps() {
        val push=option("ppl-push",TrainingMuscle.CHEST,
            ComposerPattern.HORIZONTAL_PRESS,
            allowed=setOf(ComposerFocus.PUSH),
            equipment=ComposerEquipment.Verified(LoadUnit.LB,n("25"),dumbbells))
        val pull=option("ppl-pull",TrainingMuscle.LATS,
            ComposerPattern.HORIZONTAL_PULL,
            allowed=setOf(ComposerFocus.PULL),
            equipment=ComposerEquipment.Verified(LoadUnit.LB,n("25"),dumbbells))
        val legs=option("ppl-squat",TrainingMuscle.QUADS,
            ComposerPattern.SQUAT,
            allowed=setOf(ComposerFocus.LEGS),
            equipment=ComposerEquipment.Verified(LoadUnit.LB,n("25"),dumbbells))
        val triceps=option("ppl-triceps",TrainingMuscle.TRICEPS,
            ComposerPattern.ISOLATION,
            allowed=setOf(ComposerFocus.LEGS),
            equipment=ComposerEquipment.Verified(LoadUnit.LB,n("25"),dumbbells))
        val focuses=listOf(ComposerFocus.PUSH,ComposerFocus.PULL,
            ComposerFocus.LEGS)
        val request=ComposerRequest(ComposerSplit.PUSH_PULL_LEGS,
            TrainingPhase.HYPERTROPHY,
            focuses.mapIndexed {day,focus ->
                ComposerDay(day*2,focus,DeclaredSessionWindow(1,100,null,0))
            },bounds(),listOf(push,pull,legs,triceps),2,1,
            allowDirectTricepsOnLower=false)
        val forbidden=CoachWeekComposer.compose(request)
        assertEquals(1,forbidden.missingRequiredVisits["ppl-triceps"])
        assertTrue(forbidden.visits.none {it.exerciseId=="ppl-triceps"})
        assertEquals(ComposerStatus.CONSTRAINTS_UNRESOLVED,forbidden.status)
        val allowed=CoachWeekComposer.compose(
            request.copy(allowDirectTricepsOnLower=true))
        assertTrue(allowed.visits.any {it.exerciseId=="ppl-triceps"})
        assertTrue(allowed.missingRequiredVisits["ppl-triceps"]==null)
        assertFalse(allowed.canIssueTargets)
    }

    @Test fun invalidPerDayCapsFailFastInsteadOfSilentlyClamping() {
        assertThrows(IllegalArgumentException::class.java) {
            CoachWeekComposer.compose(full(cap=-1))
        }
        assertThrows(IllegalArgumentException::class.java) {
            CoachWeekComposer.compose(full(cap=13))
        }
    }
}
