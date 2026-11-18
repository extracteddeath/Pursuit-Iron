package io.github.extracteddeath.pursuitiron.rebuild

import java.util.Collections

/**
 * Provenance for an ACTUAL custom-program -> future Strength boundary.
 * This is deliberately not a generic "strength needs more curls" quota:
 * prior completed hypertrophy work, a source-revision-matched cycle audit,
 * and individually reviewed catalog credits must establish the exercise
 * identity first. No history, load, workout or program is mutated here.
 */
enum class StrengthSourceArmsStatus {
    SOURCE_UNVERIFIED, NO_REVIEWABLE_ARM_SOURCE, REVIEW_READY
}
data class StrengthSourceArmCandidate(
    val exerciseId:String,
    val directMuscle:TrainingMuscle,
    val priorWorkingSets:Int
)
data class StrengthSourceArmReview(
    val status:StrengthSourceArmsStatus,
    val cycleId:String?,
    val athleteId:String?,
    val sourceCycleRevision:Long?,
    val sourceBlockId:String?,
    val candidates:List<StrengthSourceArmCandidate>,
    val explanation:String
) {
    /** Only the generator's complete equipment/clock/dose composer can
     * decide whether a visit is actually feasible. This is not an approval. */
    val mayIssueWorkout:Boolean get()=false
    val mayEditSavedCycle:Boolean get()=false
    val preferredExerciseIds:Set<String> get()=
        candidates.map {it.exerciseId}.toSet()
}

object StrengthSourceArmContinuityDomain {
    private val armMuscles=listOf(TrainingMuscle.BICEPS,
        TrainingMuscle.BRACHIALIS,TrainingMuscle.TRICEPS)

    fun review(
        cycle:CustomTrainingCycle?,
        coachAudit:CustomCoachAudit?,
        priorBlockId:String,
        catalog:List<CatalogMovement>,
        expectedAthleteId:String
    ):StrengthSourceArmReview {
        fun reject(reason:String)=StrengthSourceArmReview(
            StrengthSourceArmsStatus.SOURCE_UNVERIFIED,
            cycle?.cycleId,cycle?.athleteId,cycle?.revision,priorBlockId,emptyList(),reason)
        val structural=coachAudit?.structuralReview
        if(cycle==null || cycle.athleteId!=expectedAthleteId ||
            cycle.revision<1 || coachAudit==null ||
            coachAudit.auditedCycleRevision!=cycle.revision ||
            structural==null || structural.verifiedDraft!=cycle ||
            structural.status!=CustomCycleReviewStatus.READY_FOR_COACH_REVIEW)
            return reject("The athlete's exact saved custom cycle and its current source audit are required.")
        if(catalog.map{it.exerciseId}.distinct().size!=catalog.size)
            return reject("The exercise inventory contains ambiguous stable identities.")
        val index=cycle.blocks.indexOfFirst{it.blockId==priorBlockId}
        if(index<0 || index+1>=cycle.blocks.size ||
            cycle.blocks[index].phase!=TrainingPhase.HYPERTROPHY ||
            cycle.blocks[index].purpose!=CustomBlockPurpose.TRAINING ||
            cycle.blocks[index+1].phase!=TrainingPhase.STRENGTH ||
            cycle.blocks[index+1].purpose!=CustomBlockPurpose.TRAINING)
            return reject("Only the last hypertrophy training week before a real future Strength block can support this continuity review.")
        val exitWeek=cycle.blocks.take(index+1).sumOf{it.weeks}
        val prior=CustomTrainingCycleDomain.week(
            structural,exitWeek)
        if(prior==null || prior.blockId!=priorBlockId ||
            prior.phase!=TrainingPhase.HYPERTROPHY)
            return reject("The exact final hypertrophy week could not be resolved, including overrides.")
        val catalogById=catalog.associateBy{it.exerciseId}
        val setsByExercise=prior.days.flatMap{it.slots}
            .groupBy{it.exerciseId}
            .mapValues{(_,slots)->slots.sumOf{slot->
                slot.sets.count{set->set.type==DraftSetType.WORKING}
            }}
        val candidates=armMuscles.mapNotNull{muscle->
            setsByExercise.entries.mapNotNull{(id,sets)->
                val spec=catalogById[id]
                if(sets<=0 || spec==null ||
                    spec.role!=ComposerRole.ACCESSORY ||
                    spec.pattern!=ComposerPattern.ISOLATION ||
                    spec.creditStatus!=
                        CatalogCreditStatus.AUTHOR_REVIEWED_PROVISIONAL ||
                    spec.creditReviewReference.isNullOrBlank() ||
                    spec.credits.none{credit->
                        credit.muscle==muscle &&
                            credit.role==MuscleCreditRole.DIRECT
                    })null
                else StrengthSourceArmCandidate(id,muscle,sets)
            }.sortedWith(compareByDescending<StrengthSourceArmCandidate>{
                it.priorWorkingSets
            }.thenBy{it.exerciseId}).firstOrNull()
        }.distinctBy{it.exerciseId}
        return StrengthSourceArmReview(
            if(candidates.isEmpty())StrengthSourceArmsStatus.NO_REVIEWABLE_ARM_SOURCE
            else StrengthSourceArmsStatus.REVIEW_READY,
            cycle.cycleId,cycle.athleteId,cycle.revision,priorBlockId,
            Collections.unmodifiableList(candidates),
            if(candidates.isEmpty())
                "The resolved source does not contain directly credited, catalog-reviewed biceps, brachialis or triceps accessories."
            else "The source has individually verified direct arm exercises. The next Strength week must still satisfy its full fatigue, load, equipment, time and dose checks before preserving them."
        )
    }
}
