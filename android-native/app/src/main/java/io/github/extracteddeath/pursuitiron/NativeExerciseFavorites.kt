package io.github.extracteddeath.pursuitiron

import android.content.SharedPreferences
import io.github.extracteddeath.pursuitiron.nativecoachstore.*

/**
 * Athlete-scoped presentation pins for ANY exercise found in the complete,
 * owner-verified mixed workout history. This is intentionally separate from
 * the four published, source-matched barbell Strength Standards references.
 * Pins are not PR evidence, coaching inputs, or program approvals.
 */
internal object NativeExerciseFavorites {
    const val PREFS_NAME="pursuit-exercise-favorites-v1"
    const val MAX_FAVORITES=12
    private val validId=Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,79}$")

    private fun key(athleteId:String)="athlete:$athleteId:history-favorites"

    /** A verified timeline, never a suggested workout, defines eligibility. */
    fun loggedIds(history:NativeKotlinMixedHistoryTimeline):List<String> =
        history.completions.flatMap {entry ->
            when(entry) {
                is NativeKotlinMixedHistoryCompletion.Composer ->
                    entry.evidence.sets.map {it.exerciseId}
                is NativeKotlinMixedHistoryCompletion.Custom ->
                    entry.evidence.visits.map {it.exerciseId}
            }
        }.filter(validId::matches).distinct().sortedBy(::kotlinExerciseLabel)

    fun read(
        prefs:SharedPreferences,athleteId:String,loggedIds:Set<String>
    ):List<String> {
        if(!validId.matches(athleteId))return emptyList()
        return prefs.getString(key(athleteId),null)?.split(',')
            ?.filter {validId.matches(it) && it in loggedIds}
            ?.distinct()?.take(MAX_FAVORITES).orEmpty()
    }

    /** Reorder, pin and unpin only confirmed entries in the current history. */
    fun write(
        prefs:SharedPreferences,athleteId:String,
        chosen:List<String>,loggedIds:Set<String>
    ):Boolean {
        if(!validId.matches(athleteId) ||
            chosen.size>MAX_FAVORITES ||
            chosen.distinct().size!=chosen.size ||
            chosen.any{!validId.matches(it) || it !in loggedIds})return false
        val editor=prefs.edit()
        if(chosen.isEmpty())editor.remove(key(athleteId))
        else editor.putString(key(athleteId),chosen.joinToString(","))
        return editor.commit()
    }
}
