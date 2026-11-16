package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

/** The observed frequency of DIRECT muscle exposure is a read-only swap
 * quality tie-breaker, not automatic replacement or a volume prescription. */
class CustomExerciseSwapWeeklyCoverageTest {
    private fun choice(id:String,family:String,
        muscles:List<TrainingMuscle>
    )=CustomExercisePickerOption(
        id,family,id,null,LoadUnit.LB,LoadBasis.CABLE_STACK,false,
        CustomExerciseAvailability(id,null,false),
        ComposerPattern.HORIZONTAL_PRESS,ComposerRole.ACCESSORY,
        muscles.map{DeclaredMuscleCredit(it,MuscleCreditRole.DIRECT,BigDecimal.ONE)}
    )

    private val chest=TrainingMuscle.CHEST
    private val triceps=TrainingMuscle.TRICEPS

    private val choices=listOf(
        choice("source","press",listOf(chest,triceps)),
        choice("alpha-triceps","press",listOf(triceps)),
        choice("zulu-chest","press",listOf(chest)),
        choice("other-triceps","different",listOf(triceps))
    )

    private fun rank(
        otherToday:List<String>?=emptyList(),
        otherWeek:List<List<String>>?=listOf(listOf("other-triceps")),
        hasWorking:Boolean=true
    )=CustomExerciseSwapRankingDomain.rank(
        choices,"source",
        otherDayExerciseIds=otherToday,
        sourceHasWorkingSets=hasWorking,
        otherWeekdayExerciseIds=otherWeek
    )

    @Test fun equallyMatchedCandidatesPreserveScarcerObservedWeekday() {
        val original=rank(otherWeek=null)
        assertEquals("alpha-triceps",original.first().choice.exerciseId)
        val reviewed=rank()
        assertEquals("zulu-chest",reviewed.first().choice.exerciseId)
        val chestChoice=reviewed.first()
        assertEquals(mapOf(chest to 0,triceps to 1),
            chestChoice.otherDirectWeekdaysByMuscle)
        assertEquals(setOf(chest),chestChoice.preservedWeekMuscles)
        assertEquals(3,chestChoice.weeklyRetentionPriority)
        assertTrue(chestChoice.explanation.contains(
            "this week's only observed direct training day"))
        val tricepsChoice=reviewed.single{it.choice.exerciseId=="alpha-triceps"}
        assertEquals(2,tricepsChoice.weeklyRetentionPriority)
        assertFalse(chestChoice.mayTransferWeight)
        assertFalse(chestChoice.mayApplyWithoutReview)
    }

    @Test fun countsOtherWeekdaysNotExerciseSlotsOrSets() {
        val reviewed=rank(otherWeek=listOf(
            listOf("other-triceps","other-triceps"),
            listOf("other-triceps"),
            emptyList()
        ))
        val preserved=reviewed.single{it.choice.exerciseId=="alpha-triceps"}
        assertEquals(2,preserved.otherDirectWeekdaysByMuscle[triceps])
        assertEquals(1,preserved.weeklyRetentionPriority)
    }

    @Test fun missingPreviousGymItemOrUnknownWeekNeverCreatesDeficitSignal() {
        val invalid=listOf<List<List<String>>?>(
            null,
            listOf(listOf("unreviewed-retired-exercise")),
            List(7){emptyList()}
        )
        invalid.forEach{week->
            val result=rank(otherWeek=week)
            assertEquals("alpha-triceps",result.first().choice.exerciseId)
            assertTrue(result.all{
                it.otherDirectWeekdaysByMuscle.isEmpty() &&
                    it.preservedWeekMuscles.isEmpty() &&
                    it.weeklyRetentionPriority==0 &&
                    !it.explanation.contains("this week's only")
            })
        }
        assertTrue(rank(hasWorking=false).all{
            it.otherDirectWeekdaysByMuscle.isEmpty()
        })
        assertTrue(CustomExerciseSwapRankingDomain.rank(
            choices,"retired-source",
            otherDayExerciseIds=emptyList(),sourceHasWorkingSets=true,
            otherWeekdayExerciseIds=emptyList()).all{
                it.weeklyRetentionPriority==0
            })
    }

    @Test fun sameDayDirectCoverageIsNotFalselyCountedAsOnlyWeeklyExposure() {
        val reviewed=rank(otherToday=listOf("other-triceps"),
            otherWeek=listOf(emptyList()))
        assertTrue(reviewed.all{
            triceps !in it.otherDirectWeekdaysByMuscle &&
                triceps !in it.preservedWeekMuscles
        })
        val sameDayCoversBoth=CustomExerciseSwapRankingDomain.rank(
            choices,"source",
            otherDayExerciseIds=listOf("source"),
            sourceHasWorkingSets=true,
            otherWeekdayExerciseIds=listOf(emptyList()))
        assertTrue(sameDayCoversBoth.all{
            it.uniqueDayCoverage.isEmpty() &&
                it.weeklyRetentionPriority==0
        })
    }

    @Test fun repeatedRankingIsPureAndSearchDoesNotInventCoverage() {
        val full=rank()
        assertEquals(full,rank())
        val searched=CustomExerciseSwapRankingDomain.rank(choices,"source",
            search="zulu",
            otherDayExerciseIds=emptyList(),sourceHasWorkingSets=true,
            otherWeekdayExerciseIds=listOf(listOf("other-triceps")))
        assertEquals(listOf("zulu-chest"),searched.map{it.choice.exerciseId})
        assertEquals(3,searched.single().weeklyRetentionPriority)
        assertEquals(choices,choices.toList())
    }
}
