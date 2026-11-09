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
    /** Sole reviewed direct-muscle coverage of the removed exercise on this day. */
    val uniqueDayCoverage: Set<TrainingMuscle>,
    /** Potential overlap from candidate credits, NOT assured without authored working sets. */
    val preservedDayCoverage: Set<TrainingMuscle>,
    val explanation: String
) {
    val mayTransferWeight:Boolean get()=false
    val mayApplyWithoutReview:Boolean get()=false
}
object CustomExerciseSwapRankingDomain {
    private fun matchesName(value:String?,query:String):Boolean =
        value?.let {name->
            name.contains(query,ignoreCase=true) ||
            name.replace('_',' ').replace('-',' ')
                .contains(query,ignoreCase=true)
        } ?: false

    private fun credits(option:CustomExercisePickerOption):Set<TrainingMuscle> =
        option.muscleCredits.filter {
            it.fractionalSet.signum()>0
        }.map{it.muscle}.toSet()

    private fun directCredits(option:CustomExercisePickerOption):Set<TrainingMuscle> =
        option.muscleCredits.filter {
            it.role==MuscleCreditRole.DIRECT &&
                it.fractionalSet.signum()>0
        }.map{it.muscle}.toSet()

    fun rank(
        available:List<CustomExercisePickerOption>,
        sourceExerciseId:String?,
        search:String="",
        samePatternOnly:Boolean=false,
        sharedMusclesOnly:Boolean=false,
        sameRoleOnly:Boolean=false,
        sameFamilyOnly:Boolean=false,
        /** Other slots with WORKING sets in this program day or workout.
         * Null = unavailable context; never invent day-wide muscle coverage. */
        otherDayExerciseIds:List<String>?=null,
        /** Explicit working-set evidence for the original slot is required. */
        sourceHasWorkingSets:Boolean=false
    ):List<CustomSwapRankedOption> {
        // Fail closed on duplicate catalog identities. Do not build a
        // shortlist from invented or unresolved source metadata.
        if(available.map{it.exerciseId}.distinct().size!=available.size)
            return emptyList()
        val byId=available.associateBy{it.exerciseId}
        val source=byId[sourceExerciseId]
        val sourceMuscles=source?.let(::credits).orEmpty()
        // A day with an unavailable catalog entry cannot support an exact
        // coverage claim. Fall back to pattern/family/role ranking instead.
        val dayCoverageKnown=sourceHasWorkingSets && source!=null &&
            otherDayExerciseIds!=null &&
            otherDayExerciseIds.all{it in byId}
        val uniqueDirect=if(dayCoverageKnown) {
            val otherDirect=otherDayExerciseIds.orEmpty().flatMap{
                directCredits(checkNotNull(byId[it]))
            }.toSet()
            directCredits(checkNotNull(source))-otherDirect
        } else emptySet()
        val needle=search.trim().take(80)
        val sorted=available.asSequence()
            .filter{it.exerciseId!=sourceExerciseId}
            .filter {
                needle.isBlank() ||
                    matchesName(it.exerciseId,needle) ||
                    matchesName(it.familyId,needle) ||
                    matchesName(it.variantId,needle) ||
                    matchesName(it.attachmentId,needle) ||
                    matchesName(it.pattern?.name,needle) ||
                    credits(it).any{muscle->
                        matchesName(muscle.name,needle)
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
                val retained=uniqueDirect.intersect(directCredits(candidate))
                val reasons=mutableListOf<String>()
                if(samePattern)reasons+="Same reviewed movement pattern"
                if(sameFamily)reasons+="Same exercise family"
                if(sameRole)reasons+="Same reviewed training role"
                if(shared.isNotEmpty())reasons+=
                    "Shared credited muscles: "+
                        shared.sortedBy {it.name}.joinToString(", "){
                            it.name.lowercase().replace('_',' ')
                        }
                if(uniqueDirect.isNotEmpty()) {
                    fun names(muscles:Set<TrainingMuscle>)=muscles
                        .sortedBy{it.name}.joinToString(", "){
                            it.name.lowercase().replace('_',' ')
                        }
                    if(retained.isNotEmpty())reasons+=
                        "Could cover sole direct-day muscles with working sets: "+names(retained)
                    val lost=uniqueDirect-retained
                    if(lost.isNotEmpty())reasons+=
                        "Not directly covered by this candidate: "+names(lost)
                }
                if(reasons.isEmpty())reasons+=if(source==null)
                    "Source pattern unavailable; alphabetical verified options"
                else "Different pattern; review muscle coverage and the session"
                CustomSwapRankedOption(candidate,fit,shared,sameRole,
                    uniqueDirect,retained,reasons.joinToString(" · "))
            }
            .filter {option->
                (!samePatternOnly || option.fit in setOf(
                    CustomSwapFit.SAME_PATTERN,
                    CustomSwapFit.SAME_PATTERN_AND_FAMILY)) &&
                (!sharedMusclesOnly || option.sharedMuscles.isNotEmpty()) &&
                (!sameRoleOnly || option.sameRole) &&
                (!sameFamilyOnly || option.fit in setOf(
                    CustomSwapFit.SAME_PATTERN_AND_FAMILY,
                    CustomSwapFit.SAME_FAMILY))
            }
            .sortedWith(compareBy<CustomSwapRankedOption>{
                it.fit.ordinal
            }.thenByDescending {it.preservedDayCoverage.size}
                .thenByDescending {it.sameRole}
                .thenByDescending {it.sharedMuscles.size}
                .thenBy{it.choice.exerciseId})
            .toList()
        return Collections.unmodifiableList(sorted)
    }
}
