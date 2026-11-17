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
}
