package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class CustomExerciseSwapRankingDomainTest {
    private fun option(
        id:String,family:String,pattern:ComposerPattern,
        muscles:List<TrainingMuscle>
    )=CustomExercisePickerOption(
        id,family,id,null,LoadUnit.LB,LoadBasis.CABLE_STACK,
        false,CustomExerciseAvailability(id,null,false),
        pattern,ComposerRole.ACCESSORY,muscles.map {
            DeclaredMuscleCredit(it,MuscleCreditRole.DIRECT,BigDecimal.ONE)
        })
    private val chest=TrainingMuscle.CHEST
    private val delts=TrainingMuscle.SIDE_DELTS
    private val rows=listOf(
        option("press-original","press",ComposerPattern.HORIZONTAL_PRESS,
            listOf(chest)),
        option("press-variant","press",ComposerPattern.HORIZONTAL_PRESS,
            listOf(chest)),
        option("other-press","other",ComposerPattern.HORIZONTAL_PRESS,
            listOf(chest)),
        option("chest-fly","fly",ComposerPattern.ISOLATION,listOf(chest)),
        option("lateral-raise","raise",ComposerPattern.ISOLATION,listOf(delts))
    )

    @Test fun rankedByReviewedPatternFamilyThenMuscleAndNotByNameGuess() {
        val r=CustomExerciseSwapRankingDomain.rank(rows,"press-original")
        assertEquals(listOf("press-variant","other-press","chest-fly",
            "lateral-raise"),r.map {it.choice.exerciseId})
        assertEquals(CustomSwapFit.SAME_PATTERN_AND_FAMILY,r[0].fit)
        assertEquals(CustomSwapFit.SAME_PATTERN,r[1].fit)
        assertEquals(CustomSwapFit.SHARED_REVIEWED_MUSCLES,r[2].fit)
        assertTrue(r[2].explanation.contains("Shared credited muscles"))
        assertTrue(r.all {!it.mayTransferWeight && !it.mayApplyWithoutReview})
    }
    @Test fun filtersAndUnknownSourceAreExplicit() {
        assertEquals(listOf("press-variant","other-press"),
            CustomExerciseSwapRankingDomain.rank(rows,"press-original",
                samePatternOnly=true).map{it.choice.exerciseId})
        assertEquals(listOf("press-variant","other-press","chest-fly"),
            CustomExerciseSwapRankingDomain.rank(rows,"press-original",
                sharedMusclesOnly=true).map{it.choice.exerciseId})
        val unknown=CustomExerciseSwapRankingDomain.rank(rows,
            "not-in-current-gym","lateral")
        assertEquals(listOf("lateral-raise"),unknown.map{it.choice.exerciseId})
        assertEquals(CustomSwapFit.SOURCE_UNRESOLVED,unknown.single().fit)
        assertEquals(emptyList<CustomSwapRankedOption>(),
            CustomExerciseSwapRankingDomain.rank(rows,"press-original",
                search="nonexistent"))
    }
    @Test fun duplicateUnverifiedInputFailsClosedAndNoSourceIsNotRecommended() {
        assertTrue(CustomExerciseSwapRankingDomain.rank(
            rows+rows.first(),"press-original").isEmpty())
        assertTrue(CustomExerciseSwapRankingDomain.rank(
            rows,null).all {it.fit==CustomSwapFit.SOURCE_UNRESOLVED})
    }
}
