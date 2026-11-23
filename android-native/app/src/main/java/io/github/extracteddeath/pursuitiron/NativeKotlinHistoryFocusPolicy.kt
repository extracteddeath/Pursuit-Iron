package io.github.extracteddeath.pursuitiron

/** Navigation-only preference, never a source of history or athlete authority.
 * An expired, missing or duplicated workout ID must fail closed, leaving
 * the complete verified History available for normal browsing. */
internal object NativeKotlinHistoryFocusPolicy {
    private val workoutId=Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,79}$")
    fun admit(requested:String?,verifiedWorkoutIds:List<String>):String? =
        requested?.takeIf { workoutId.matches(it) &&
            verifiedWorkoutIds.count { verified->verified==it }==1
        }
    /**
     * Exercise detail is a persisted *navigation hint*, never a grant to
     * reopen another athlete's records. It must belong to the athlete who
     * selected it AND exist in that athlete's freshly verified repetition
     * ledger. Timed measured-only movements have a separate history view.
     */
    fun admitExercise(requested:String?,selectedAthleteId:String?,
        currentAthleteId:String?,verifiedRepExerciseIds:Collection<String>):String? =
        requested?.takeIf { exerciseId ->
            !selectedAthleteId.isNullOrBlank() &&
                selectedAthleteId==currentAthleteId &&
                exerciseId.isNotBlank() && exerciseId in verifiedRepExerciseIds
        }
}
