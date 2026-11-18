package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class StrengthSourceArmContinuityDomainTest {
    private val load=BigDecimal("25")
    private val work=CustomAuthoredSet(DraftSetType.WORKING,
        DraftSetLoad.ExplicitBodyweight,RepBand(8,12),2,45,60)
    private fun slot(id:String,exercise:String,sets:Int)=CustomExerciseSlot(
        id,exercise,ComposerRole.ACCESSORY,List(sets){work})
    private fun catalog(id:String,muscle:TrainingMuscle,
        credit:MuscleCreditRole=MuscleCreditRole.DIRECT,
        status:CatalogCreditStatus=CatalogCreditStatus.AUTHOR_REVIEWED_PROVISIONAL)=
        CatalogMovement(id,"family-$id","variant",null,
            ComposerPattern.ISOLATION,ComposerRole.ACCESSORY,
            setOf(ComposerFocus.UPPER,ComposerFocus.PULL,ComposerFocus.PUSH,
                ComposerFocus.FULL_BODY),
            "dumbbells",LoadBasis.DUMBBELL_PER_HAND,
            listOf(DeclaredMuscleCredit(muscle,credit,BigDecimal.ONE)),
            status,"reviewed-$id")
    private val catalog=listOf(
        catalog("curl",TrainingMuscle.BICEPS),
        catalog("hammer",TrainingMuscle.BRACHIALIS),
        catalog("extension",TrainingMuscle.TRICEPS),
        catalog("row",TrainingMuscle.BICEPS,MuscleCreditRole.INDIRECT))
    private fun cycle():CustomTrainingCycle {
        val hyper=CustomTrainingBlock("hyper","Hypertrophy",
            TrainingPhase.HYPERTROPHY,CustomBlockPurpose.TRAINING,2,
            CustomSplitIdentity.Freeform("ULPPL"),listOf(
                CustomTrainingDay(0,"Upper",ComposerSessionPurpose.HYPERTROPHY,
                    listOf(slot("c","curl",2),slot("h","hammer",3),
                        slot("e","extension",2),slot("r","row",4)))
            ))
        val strength=CustomTrainingBlock("strength","Strength",
            TrainingPhase.STRENGTH,CustomBlockPurpose.TRAINING,2,
            hyper.split,listOf(CustomTrainingDay(0,"Upper",
                ComposerSessionPurpose.STRENGTH_SKILL,
                listOf(slot("a","row",3)))))
        return CustomTrainingCycle("source-cycle",7,"owner-a",
            AthleteTrainingGoal.POWERBUILDING,
            "Verified custom-to-strength source",listOf(hyper,strength))
    }
    private fun audit(cycle:CustomTrainingCycle)=CustomCoachAudit(
        CustomCoachAuditStatus.UNRESOLVED,emptyList(),
        CustomCycleReview(CustomCycleReviewStatus.READY_FOR_COACH_REVIEW,
            emptyList(),4,cycle),
        emptyList(),"athlete-revision",cycle.revision,emptyMap())

    @Test fun readsActualExitWeekAndPreservesIndependentArmFunctions() {
        val c=cycle()
        val original=c.toString()
        val result=StrengthSourceArmContinuityDomain.review(
            c,audit(c),"hyper",catalog,"owner-a")
        assertEquals(StrengthSourceArmsStatus.REVIEW_READY,result.status)
        assertEquals(listOf("curl","hammer","extension"),
            result.candidates.map{it.exerciseId})
        assertEquals(listOf(TrainingMuscle.BICEPS,TrainingMuscle.BRACHIALIS,
            TrainingMuscle.TRICEPS),result.candidates.map{it.directMuscle})
        assertEquals(listOf(2,3,2),
            result.candidates.map{it.priorWorkingSets})
        assertFalse(result.mayIssueWorkout)
        assertFalse(result.mayEditSavedCycle)
        assertEquals(original,c.toString())
    }

    @Test fun staleAuditWrongAthleteAndPeakCannotProduceContinuation() {
        val c=cycle()
        assertEquals(StrengthSourceArmsStatus.SOURCE_UNVERIFIED,
            StrengthSourceArmContinuityDomain.review(
                c,audit(c.copy(revision=8)),"hyper",catalog,"owner-a").status)
        assertEquals(StrengthSourceArmsStatus.SOURCE_UNVERIFIED,
            StrengthSourceArmContinuityDomain.review(
                c,audit(c),"hyper",catalog,"owner-b").status)
        assertEquals(StrengthSourceArmsStatus.SOURCE_UNVERIFIED,
            StrengthSourceArmContinuityDomain.review(
                c.copy(blocks=listOf(c.blocks.first(),c.blocks[1].copy(
                    phase=TrainingPhase.PEAK))),
                audit(c),"hyper",catalog,"owner-a").status)
        assertEquals(StrengthSourceArmsStatus.SOURCE_UNVERIFIED,
            StrengthSourceArmContinuityDomain.review(
                c,audit(c),"strength",catalog,"owner-a").status)
    }

    @Test fun neverPromotesIndirectRowsOrUnreviewedVariantsToDirectArmWork() {
        val c=cycle()
        val catalogWithoutDirect=catalog.map{spec->
            if(spec.exerciseId=="curl" || spec.exerciseId=="hammer" ||
                spec.exerciseId=="extension")
                spec.copy(credits=spec.credits.map{
                    it.copy(role=MuscleCreditRole.INDIRECT)})
            else spec
        }
        val review=StrengthSourceArmContinuityDomain.review(
            c,audit(c),"hyper",catalogWithoutDirect,"owner-a")
        assertEquals(StrengthSourceArmsStatus.NO_REVIEWABLE_ARM_SOURCE,
            review.status)
        val unreviewed=catalog.map {
            if(it.exerciseId=="hammer")
                it.copy(creditStatus=CatalogCreditStatus.UNREVIEWED)
            else it
        }
        val verified=StrengthSourceArmContinuityDomain.review(
            c,audit(c),"hyper",unreviewed,"owner-a")
        assertFalse("hammer" in verified.preferredExerciseIds)
        assertEquals(setOf("curl","extension"),verified.preferredExerciseIds)
    }

    @Test fun finalWeekOverrideTakesPrecedenceOverFirstWeekCustomTemplate() {
        val c=cycle()
        val updated=c.copy(weekOverrides=mapOf("hyper" to listOf(
            CustomWeekSlotOverride(2,0,"h",emptyList()))))
        val reviewed=StrengthSourceArmContinuityDomain.review(
            updated,audit(updated),"hyper",catalog,"owner-a")
        assertEquals(setOf("curl","extension"),reviewed.preferredExerciseIds)
    }
}
