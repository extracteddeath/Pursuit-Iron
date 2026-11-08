package io.github.extracteddeath.pursuitiron

import android.content.SharedPreferences

/**
 * Local, opt-in Progress presentation preference. This is NOT part of
 * training prescriptions, program approvals, authenticated history, PR
 * classification, strength standards data or gym inventory.
 *
 * The verified athlete ID is supplied by the host, never entered here.
 */
internal object NativeStrengthFavorites {
    const val PREFS_NAME="pursuit-strength-favorites-v1"

    val supportedIds=listOf(
        "barbell_bench",
        "back_squat",
        "conventional_deadlift",
        "barbell_ohp"
    )

    private fun key(athleteId:String)="athlete:$athleteId:favorite-lifts"

    fun read(preferences:SharedPreferences,athleteId:String):List<String> =
        preferences.getString(key(athleteId),null)?.split(',')
            ?.filter{it in supportedIds}?.distinct().orEmpty()

    /** A saved favorite is a UI pin, not evidence that a lift was performed. */
    fun write(
        preferences:SharedPreferences,
        athleteId:String,
        exerciseIds:List<String>
    ):Boolean {
        if(exerciseIds.size>supportedIds.size ||
            exerciseIds.size!=exerciseIds.distinct().size ||
            exerciseIds.any{it !in supportedIds})return false
        val editor=preferences.edit()
        if(exerciseIds.isEmpty())editor.remove(key(athleteId))
        else editor.putString(key(athleteId),exerciseIds.joinToString(","))
        return editor.commit()
    }

    fun title(exerciseId:String):String=when(exerciseId) {
        "barbell_bench" -> "Bench press"
        "back_squat" -> "Back squat"
        "conventional_deadlift" -> "Conventional deadlift"
        "barbell_ohp" -> "Standing barbell press"
        else -> "Saved lift"
    }
}
