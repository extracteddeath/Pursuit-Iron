package io.github.extracteddeath.pursuitiron.rebuild

import java.util.Collections

/**
 * Read-only shortlist for an athlete-initiated movement replacement.
 *
 * INPUT MUST be CustomExerciseAuthoringDomain.picker() from the currently
 * reviewed athlete/gym/catalog snapshot. This sorter NEVER authorizes an
 * exercise, claims numeric load equivalence or transfers prescriptions.
 * An unknown source is reported as unranked rather than guessed from a name.
 */
enum class CustomSwapFit {
    SAME_PATTERN_AND_FAMILY,
    SAME_PATTERN,
    SAME_FAMILY,
    SHARED_REVIEWED_MUSCLES,
    OTHER_VERIFIED_OPTION,
    SOURCE_UNRESOLVED
}
data class CustomSwapRankedOption(
    val choice: CustomExercisePickerOption,
    val fit: CustomSwapFit,
    val sharedMuscles: Set<TrainingMuscle>,
    val sameRole: Boolean,
    val explanation: String
) {
    val mayTransferWeight:Boolean get()=false
    val mayApplyWithoutReview:Boolean get()=false
}
object CustomExerciseSwapRankingDomain {
    private fun credits(option:CustomExercisePickerOption):Set<TrainingMuscle> =
        option.muscleCredits.filter {
            it.fractionalSet.signum()>0
        }.map{it.muscle}.toSet()

    fun rank(
        available:List<CustomExercisePickerOption>,
        sourceExerciseId:String?,
        search:String="",
        samePatternOnly:Boolean=false,
        sharedMusclesOnly:Boolean=false,
        sameRoleOnly:Boolean=false
    ):List<CustomSwapRankedOption> {
        // Fail closed on duplicate catalog identities. Do not build a
        // shortlist from invented or unresolved source metadata.
        if(available.map{it.exerciseId}.distinct().size!=available.size)
            return emptyList()
        val source=available.firstOrNull {it.exerciseId==sourceExerciseId}
        val sourceMuscles=source?.let(::credits).orEmpty()
        val needle=search.trim().take(80)
        val sorted=available.asSequence()
            .filter{it.exerciseId!=sourceExerciseId}
            .filter {
                needle.isBlank() ||
                    it.exerciseId.contains(needle,ignoreCase=true) ||
                    it.familyId.contains(needle,ignoreCase=true) ||
                    it.variantId.contains(needle,ignoreCase=true) ||
                    it.attachmentId?.contains(needle,ignoreCase=true)==true ||
                    it.pattern?.name?.contains(needle,ignoreCase=true)==true ||
                    credits(it).any{muscle->
                        muscle.name.contains(needle,ignoreCase=true)
                    }
            }
            .map { candidate ->
                val samePattern=source?.pattern!=null &&
                    candidate.pattern==source.pattern
                val sameFamily=source!=null &&
                    source.familyId==candidate.familyId
                val sameRole=source?.role!=null &&
                    candidate.role==source.role
                val shared=if(source!=null)
                    credits(candidate).intersect(sourceMuscles)
                    else emptySet()
                val fit=when {
                    source==null -> CustomSwapFit.SOURCE_UNRESOLVED
                    samePattern && sameFamily ->
                        CustomSwapFit.SAME_PATTERN_AND_FAMILY
                    samePattern -> CustomSwapFit.SAME_PATTERN
                    sameFamily -> CustomSwapFit.SAME_FAMILY
                    shared.isNotEmpty() -> CustomSwapFit.SHARED_REVIEWED_MUSCLES
                    else -> CustomSwapFit.OTHER_VERIFIED_OPTION
                }
                val reasons=mutableListOf<String>()
                if(samePattern)reasons+="Same reviewed movement pattern"
                if(sameFamily)reasons+="Same exercise family"
                if(sameRole)reasons+="Same reviewed training role"
                if(shared.isNotEmpty())reasons+=
                    "Shared credited muscles: "+
                        shared.sortedBy {it.name}.joinToString(", "){
                            it.name.lowercase().replace('_',' ')
                        }
                if(reasons.isEmpty())reasons+=if(source==null)
                    "Source pattern unavailable; alphabetical verified options"
                else "Different pattern; review muscle coverage and the session"
                CustomSwapRankedOption(candidate,fit,shared,sameRole,
                    reasons.joinToString(" · "))
            }
            .filter {option->
                (!samePatternOnly || option.fit in setOf(
                    CustomSwapFit.SAME_PATTERN,
                    CustomSwapFit.SAME_PATTERN_AND_FAMILY)) &&
                (!sharedMusclesOnly || option.sharedMuscles.isNotEmpty()) &&
                (!sameRoleOnly || option.sameRole)
            }
            .sortedWith(compareBy<CustomSwapRankedOption>{
                it.fit.ordinal
            }.thenByDescending {it.sameRole}
                .thenByDescending {it.sharedMuscles.size}
                .thenBy{it.choice.exerciseId})
            .toList()
        return Collections.unmodifiableList(sorted)
    }
}
