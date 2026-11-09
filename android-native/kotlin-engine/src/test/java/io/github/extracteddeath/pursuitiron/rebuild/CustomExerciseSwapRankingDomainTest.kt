package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class CustomExerciseSwapRankingDomainTest {
    private fun option(
        id:String,family:String,pattern:ComposerPattern,
        muscles:List<TrainingMuscle>,
        role:ComposerRole=ComposerRole.ACCESSORY,
        attachment:String?=null
    )=CustomExercisePickerOption(
        id,family,id,attachment,LoadUnit.LB,LoadBasis.CABLE_STACK,
        false,CustomExerciseAvailability(id,null,false,
            attachmentIds=attachment?.let{setOf(it)} ?: emptySet()),
        pattern,role,muscles.map {
            DeclaredMuscleCredit(it,MuscleCreditRole.DIRECT,BigDecimal.ONE)
        })
    private val chest=TrainingMuscle.CHEST
    private val lats=TrainingMuscle.LATS
    private val rows=listOf(
        option("press-original","press",ComposerPattern.HORIZONTAL_PRESS,
            listOf(chest)),
        option("press-variant","press",ComposerPattern.HORIZONTAL_PRESS,
            listOf(chest)),
        option("other-press","other",ComposerPattern.HORIZONTAL_PRESS,
            listOf(chest)),
        option("chest-fly","fly",ComposerPattern.ISOLATION,listOf(chest)),
        option("lat-pulldown","raise",ComposerPattern.ISOLATION,listOf(lats))
    )

    @Test fun rankedByReviewedPatternFamilyThenMuscleAndNotByNameGuess() {
        val r=CustomExerciseSwapRankingDomain.rank(rows,"press-original")
        assertEquals(listOf("press-variant","other-press","chest-fly",
            "lat-pulldown"),r.map {it.choice.exerciseId})
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
            "not-in-current-gym","pulldown")
        assertEquals(listOf("lat-pulldown"),unknown.map{it.choice.exerciseId})
        assertEquals(CustomSwapFit.SOURCE_UNRESOLVED,unknown.single().fit)
        assertEquals(emptyList<CustomSwapRankedOption>(),
            CustomExerciseSwapRankingDomain.rank(rows,"press-original",
                search="nonexistent"))
    }
    @Test fun searchCanHideSourceRowWithoutLosingItsRankingContext() {
        val filtered=CustomExerciseSwapRankingDomain.rank(
            rows,"press-original",search="chest-fly")
        assertEquals(listOf("chest-fly"),
            filtered.map {it.choice.exerciseId})
        assertEquals(CustomSwapFit.SHARED_REVIEWED_MUSCLES,
            filtered.single().fit)
        assertTrue(filtered.single().explanation.contains("Shared credited muscles"))
    }

    @Test fun duplicateUnverifiedInputFailsClosedAndNoSourceIsNotRecommended() {
        assertTrue(CustomExerciseSwapRankingDomain.rank(
            rows+rows.first(),"press-original").isEmpty())
        assertTrue(CustomExerciseSwapRankingDomain.rank(
            rows,null).all {it.fit==CustomSwapFit.SOURCE_UNRESOLVED})
    }

    @Test fun preservesReviewedTrainingRoleWithinSameFitTier() {
        val alternatives=listOf(
            option("original","main",ComposerPattern.HORIZONTAL_PRESS,
                listOf(chest),ComposerRole.PRIMARY),
            option("alpha-accessory","alternative",ComposerPattern.HORIZONTAL_PRESS,
                listOf(chest),ComposerRole.ACCESSORY),
            option("zulu-primary","alternative",ComposerPattern.HORIZONTAL_PRESS,
                listOf(chest),ComposerRole.PRIMARY),
            option("unrelated-primary","other",ComposerPattern.ISOLATION,
                listOf(lats),ComposerRole.PRIMARY)
        )
        val ranked=CustomExerciseSwapRankingDomain.rank(alternatives,"original")
        assertEquals(listOf("zulu-primary","alpha-accessory","unrelated-primary"),
            ranked.map{it.choice.exerciseId})
        assertTrue(ranked[0].sameRole)
        assertFalse(ranked[1].sameRole)
        assertTrue(ranked[0].explanation.contains("Same reviewed training role"))
        val roleFilter=CustomExerciseSwapRankingDomain.rank(alternatives,
            "original",sameRoleOnly=true)
        assertEquals(listOf("zulu-primary","unrelated-primary"),
            roleFilter.map{it.choice.exerciseId})
        assertTrue(CustomExerciseSwapRankingDomain.rank(alternatives,
            "unknown",sameRoleOnly=true).isEmpty())
    }

    @Test fun searchesVerifiedAttachmentEvenWhenExerciseIdDoesNotContainName() {
        val variants=listOf(
            option("pushdown-a","pushdown",ComposerPattern.ISOLATION,
                listOf(chest),attachment="straight_bar"),
            option("pushdown-b","pushdown",ComposerPattern.ISOLATION,
                listOf(chest),attachment="dual_rope"),
            option("pushdown-c","pushdown",ComposerPattern.ISOLATION,
                listOf(chest),attachment="v-bar")
        )
        assertEquals(listOf("pushdown-b"),CustomExerciseSwapRankingDomain.rank(
            variants,"pushdown-a",search="DUAL_ROPE").map{it.choice.exerciseId})
        assertEquals(listOf("pushdown-b"),CustomExerciseSwapRankingDomain.rank(
            variants,"pushdown-a",search="dual rope").map{it.choice.exerciseId})
        assertEquals(listOf("pushdown-c"),CustomExerciseSwapRankingDomain.rank(
            variants,"pushdown-a",search="V-BAR").map{it.choice.exerciseId})
        assertEquals(listOf("pushdown-c"),CustomExerciseSwapRankingDomain.rank(
            variants,"pushdown-a",search="v bar").map{it.choice.exerciseId})
        assertTrue(CustomExerciseSwapRankingDomain.rank(variants,"pushdown-a",
            search="single_rope").isEmpty())
    }

    @Test fun humanReadablePatternSearchKeepsReviewedSourceAndRank() {
        assertEquals(listOf("press-variant","other-press"),
            CustomExerciseSwapRankingDomain.rank(rows,"press-original",
                search="horizontal press").map{it.choice.exerciseId})
    }

    @Test fun sameFamilyOnlyUsesReviewedFamilyNotNameOrPatternGuess() {
        val candidates=rows+option("press-isolation","press",
            ComposerPattern.ISOLATION,listOf(chest))
        assertEquals(listOf("press-variant","press-isolation"),
            CustomExerciseSwapRankingDomain.rank(candidates,"press-original",
                sameFamilyOnly=true).map{it.choice.exerciseId})
        assertTrue(CustomExerciseSwapRankingDomain.rank(candidates,
            "missing-source",sameFamilyOnly=true).isEmpty())
    }

    @Test fun keepsSoleReviewedDirectMuscleCoverageWithinOtherwiseEqualSwapFit() {
        val muscles=listOf(chest,TrainingMuscle.TRICEPS)
        val day=listOf(
            option("source-press","press",ComposerPattern.HORIZONTAL_PRESS,muscles),
            option("alpha-chest","press",ComposerPattern.HORIZONTAL_PRESS,listOf(chest)),
            option("zulu-triceps","press",ComposerPattern.HORIZONTAL_PRESS,
                listOf(TrainingMuscle.TRICEPS)),
            option("other-chest","fly",ComposerPattern.ISOLATION,listOf(chest))
        )
        val noContext=CustomExerciseSwapRankingDomain.rank(day,"source-press")
        assertEquals("alpha-chest",noContext.first().choice.exerciseId)
        assertTrue(noContext.all{it.uniqueDayCoverage.isEmpty()})
        val contextual=CustomExerciseSwapRankingDomain.rank(day,"source-press",
            otherDayExerciseIds=listOf("other-chest"),
            sourceHasWorkingSets=true)
        assertEquals("zulu-triceps",contextual.first().choice.exerciseId)
        assertEquals(setOf(TrainingMuscle.TRICEPS),contextual.first().uniqueDayCoverage)
        assertEquals(setOf(TrainingMuscle.TRICEPS),contextual.first().preservedDayCoverage)
        assertTrue(contextual.first().explanation.contains("Retains sole reviewed direct-day coverage"))
        val losing=contextual.first{it.choice.exerciseId=="alpha-chest"}
        assertTrue(losing.preservedDayCoverage.isEmpty())
        assertTrue(losing.explanation.contains("Review lost direct-day coverage"))
    }

    @Test fun unavailableOtherDayMovementNeverInventsACoverageScore() {
        val day=listOf(
            option("source-press","press",ComposerPattern.HORIZONTAL_PRESS,
                listOf(chest,TrainingMuscle.TRICEPS)),
            option("alpha-chest","press",ComposerPattern.HORIZONTAL_PRESS,listOf(chest)),
            option("zulu-triceps","press",ComposerPattern.HORIZONTAL_PRESS,
                listOf(TrainingMuscle.TRICEPS))
        )
        val unknown=CustomExerciseSwapRankingDomain.rank(day,"source-press",
            otherDayExerciseIds=listOf("not-in-reviewed-catalog"),
            sourceHasWorkingSets=true)
        assertEquals("alpha-chest",unknown.first().choice.exerciseId)
        assertTrue(unknown.all {
            it.uniqueDayCoverage.isEmpty() && it.preservedDayCoverage.isEmpty() &&
                !it.explanation.contains("direct-day coverage")
        })
        val warmupOnly=CustomExerciseSwapRankingDomain.rank(day,"source-press",
            otherDayExerciseIds=emptyList(),sourceHasWorkingSets=false)
        assertTrue(warmupOnly.all {it.uniqueDayCoverage.isEmpty()})
        val missingWorkingProof=CustomExerciseSwapRankingDomain.rank(day,"source-press",
            otherDayExerciseIds=emptyList())
        assertTrue(missingWorkingProof.all{it.preservedDayCoverage.isEmpty()})
        val singleMovement=CustomExerciseSwapRankingDomain.rank(day,"source-press",
            otherDayExerciseIds=emptyList(),sourceHasWorkingSets=true)
        assertEquals(setOf(chest,TrainingMuscle.TRICEPS),
            singleMovement.first().uniqueDayCoverage)
    }
}
