package io.github.extracteddeath.pursuitiron.rebuild

import org.junit.Assert.*
import org.junit.Test

class CoachExerciseChoiceSearchDomainTest {
    private val approved=listOf(
        "barbell_bench_press", "seated_cable_row", "incline_dumbbell_press",
        "barbell_back_squat", "seated_cable_row"
    )
    private val labels=mapOf(
        "barbell_bench_press" to "Bench Press",
        "seated_cable_row" to "Seated Cable Row",
        "incline_dumbbell_press" to "Incline Dumbbell Press",
        "barbell_back_squat" to "Back Squat"
    )
    private fun search(query:String)=CoachExerciseChoiceSearchDomain.filter(
        approved,query){labels[it] ?: it}

    @Test fun noSearchKeepsOnlyApprovedChoicesInOriginalOrder() {
        assertEquals(approved.distinct(),search(""))
        assertEquals(approved.distinct(),search("   "))
    }
    @Test fun orderIndependentWordsMatchAcrossVisibleNameAndId() {
        assertEquals(listOf("incline_dumbbell_press"),search("press incline"))
        assertEquals(listOf("incline_dumbbell_press"),search("DUMBBELL press"))
        assertEquals(listOf("seated_cable_row"),search("row cable"))
        assertEquals(listOf("barbell_back_squat"),search("back-squat"))
    }
    @Test fun cannotInventUnapprovedMovementWhenSearchMatchesText() {
        assertTrue(search("machine chest press").isEmpty())
        assertTrue(search("unapproved exercise").isEmpty())
        assertEquals(listOf("barbell_bench_press"),
            search("barbell_bench"))
    }
    @Test fun filteringNeverMutatesSourceOrReordersAuthoredChoices() {
        val copy=approved.toList()
        assertEquals(listOf("barbell_bench_press","incline_dumbbell_press"),
            search("press"))
        assertEquals(copy,approved)
        assertEquals(approved.distinct(),search(""))
    }
}
