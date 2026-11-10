package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import java.util.Collections

/** Fresh, athlete-reviewed replacement prescription. No prior lift loads,
 * original exercise actuals, or target RIR are copied into this input. */
data class KotlinComposerSwapSetIntent(
    val type:DraftSetType,
    val ordinal:Int,
    val load:DraftSetLoad,
    val reps:RepBand,
    val targetRir:Int?
)
data class KotlinComposerSwapChoice(
    val option:ComposerExerciseOption,
    val samePattern:Boolean,
    val sharedReviewedMuscleCount:Int,
    val explanation:String
)

/** Candidate discovery only: never issues or changes a session. */
object KotlinComposerSwapRankingDomain {
    /**
     * Search the exact already-reviewed candidate catalog. A muscle search is
     * discovery, NOT evidence of equal stimulus or a prescription transfer.
     * Multiple words may match across the name, pattern and credited muscles.
     */
    private fun matchesSearch(option:ComposerExerciseOption,search:String):Boolean {
        val needle=search.trim().take(80).lowercase()
            .replace('_',' ').replace('-',' ')
        if(needle.isBlank()) return true
        fun readable(value:String)=value.lowercase().replace('_',' ').replace('-',' ')
        val catalogText=(listOf(option.exerciseId,option.pattern.name)+
            option.credits.filter{it.fractionalSet.signum()>0}
                .map{it.muscle.name}).joinToString(" ",transform=::readable)
        return needle.split(Regex("\\s+")).all{it in catalogText}
    }

    /** Keep the owner's ranking intact; do not rank filtered search results
     * against an incomplete source catalog. */
    fun filterRanked(
        choices:List<KotlinComposerSwapChoice>,
        search:String="",
        samePatternOnly:Boolean=false,
        sharedMusclesOnly:Boolean=false
    ):List<KotlinComposerSwapChoice> =
        Collections.unmodifiableList(choices.filter{
            (!samePatternOnly || it.samePattern) &&
                (!sharedMusclesOnly || it.sharedReviewedMuscleCount>0) &&
                matchesSearch(it.option,search)
        })

    fun rank(
        source:ComposerExerciseOption,
        alternatives:List<ComposerExerciseOption>,
        currentExerciseIds:Set<String>,
        dayFocus:ComposerFocus,
        search:String=""
    ):List<KotlinComposerSwapChoice> {
        // Only unique, positive, DIRECT reviewed credits establish actual
        // overlap. Secondary/zero-credit mentions are useful for discovery,
        // but must never inflate a replacement's shared-muscle ranking.
        fun reviewedDirectMuscles(option:ComposerExerciseOption)=
            option.credits.asSequence().filter{
                it.role==MuscleCreditRole.DIRECT &&
                    it.fractionalSet.signum()>0
            }.map{it.muscle}.toSet()
        val baseline=reviewedDirectMuscles(source)
        return alternatives.asSequence().filter{it.exerciseId!=source.exerciseId &&
            it.exerciseId !in currentExerciseIds &&
            dayFocus in it.allowedFocus &&
            it.role==source.role &&
            matchesSearch(it,search)
        }.map{candidate->
            val shared=reviewedDirectMuscles(candidate).intersect(baseline).size
            val same=candidate.pattern==source.pattern
            KotlinComposerSwapChoice(candidate,same,shared,
                if(same)"Same training pattern and role"
                else if(shared>0)"Shares reviewed direct muscle targets"
                else "Different pattern; review your intended stimulus")
        }.sortedWith(compareByDescending<KotlinComposerSwapChoice>{it.samePattern}
            .thenByDescending{it.sharedReviewedMuscleCount}
            .thenBy{it.option.exerciseId}).toList()
    }
}

sealed interface KotlinComposerSwapReview {
    data class Ready(val operation:KotlinJournalOperation.SwapExercise):
        KotlinComposerSwapReview
    data class Rejected(val reason:String):KotlinComposerSwapReview
}

/**
 * Workout-only authoring gate. Called by the native owner with CURRENT
 * approved Composer program/gym option, inside the owner revision fence.
 * This cannot authorize itself or select a different athlete, gym or cycle.
 */
object KotlinComposerSwapReviewDomain {
    private val limit=BigDecimal("100000")
    fun review(
        state:KotlinWorkoutJournalState,
        eventId:String,expectedRevision:Long,
        previousExerciseId:String,
        selected:ComposerExerciseOption,
        dayFocus:ComposerFocus,
        intents:List<KotlinComposerSwapSetIntent>
    ):KotlinComposerSwapReview {
        fun reject(message:String)=KotlinComposerSwapReview.Rejected(message)
        if(state.finishedByEventId!=null || expectedRevision!=state.revision)
            return reject("Workout revision changed or this workout finished.")
        val original=state.snapshot.targets.filter{
            it.exerciseId==previousExerciseId
        }
        if(original.isEmpty() || original.size>32 ||
            original.any{it.role!=selected.role ||
                it.purpose!=original.first().purpose})
            return reject("Original exercise or reviewed role is unavailable.")
        if(previousExerciseId==selected.exerciseId ||
            state.snapshot.targets.any{it.exerciseId==selected.exerciseId} ||
            dayFocus !in selected.allowedFocus)
            return reject("Replacement is unavailable in this training day.")
        if(intents.size!=original.size ||
            intents.map{it.type to it.ordinal}.toSet()!=
                original.map{it.type to it.ordinal}.toSet() ||
            intents.map{it.type to it.ordinal}.distinct().size!=intents.size)
            return reject("Every warm-up and working set needs a fresh target.")
        if(state.receipts.any{r->
            (r.operation as? KotlinJournalOperation.RecordSet)
                ?.setKey in original.map{it.setKey}.toSet()
        })return reject("Recorded sets cannot be replaced.")
        if(eventId.length !in 1..60 ||
            !eventId.matches(Regex("^[A-Za-z0-9][A-Za-z0-9_:-]*$")))
            return reject("Invalid workout operation.")
        val result=intents.map{target->
            if(target.reps.minimum !in 1..100 ||
                target.reps.maximum !in target.reps.minimum..100 ||
                target.targetRir?.let{it !in 0..10}==true ||
                (target.type==DraftSetType.WORKING &&
                    selected.sourcePracticalReps?.let{
                        target.reps.minimum<it.minimum ||
                            target.reps.maximum>it.maximum
                    }==true))return reject("Replacement reps or effort are unverified.")
            val allowed=when(val gear=selected.equipment) {
                ComposerEquipment.Bodyweight ->
                    target.load==DraftSetLoad.ExplicitBodyweight
                is ComposerEquipment.Verified -> {
                    val weighted=target.load as? DraftSetLoad.Verified
                    weighted!=null && weighted.unit==gear.unit &&
                        weighted.basis==gear.inventory?.basis &&
                        weighted.value>=BigDecimal.ZERO &&
                        weighted.value<=limit &&
                        weighted.value.stripTrailingZeros().scale()<=3 &&
                        EquipmentLoadDomain.inspect(
                            gear.inventory,weighted.value,gear.unit
                        ).let{it is LoadInspection.Confirmed && it.exact}
                }
            }
            if(!allowed)return reject(
                "The selected weight and gym equipment do not match.")
            KotlinWorkoutTarget(
                selected.exerciseId,selected.role,original.first().purpose,
                "swap:${eventId}:${target.type.name.lowercase()}:${target.ordinal}",
                target.type,target.ordinal,target.load,target.reps,
                target.targetRir,selected.workSeconds,selected.restSeconds)
        }
        val event=KotlinJournalOperation.SwapExercise(
            eventId,expectedRevision,previousExerciseId,
            Collections.unmodifiableList(result))
        val reduced=KotlinWorkoutJournalDomain.reduce(state,event)
        if(reduced !is KotlinJournalDecision.Proposed)
            return reject("The replacement would conflict with saved workout history.")
        return KotlinComposerSwapReview.Ready(event)
    }
}
