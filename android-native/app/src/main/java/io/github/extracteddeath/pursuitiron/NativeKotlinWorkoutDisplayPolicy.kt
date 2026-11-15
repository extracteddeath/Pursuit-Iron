package io.github.extracteddeath.pursuitiron

internal enum class WorkoutReferenceMode { TARGET,LAST }
internal enum class WorkoutStartLayout(val label:String) {
    AUTOMATIC("Screen size"),FOCUS("Focus"),ALL_SETS("All sets");
    /** Initial presentation only; a resumed workout retains its own view. */
    fun startsInFocus(widthDp:Int):Boolean=when(this) {
        AUTOMATIC -> widthDp<600
        FOCUS -> true
        ALL_SETS -> false
    }
}

/** Device display choices are independent of athlete training authority. */
internal data class NativeKotlinWorkoutDisplaySettings(
    val referenceMode:WorkoutReferenceMode=WorkoutReferenceMode.TARGET,
    val startLayout:WorkoutStartLayout=WorkoutStartLayout.AUTOMATIC,
    val keepScreenAwake:Boolean=true
) {
    companion object {
        const val FILE="native-workout-display"
        const val REFERENCE="reference-mode"
        const val LAYOUT="start-layout"
        const val AWAKE="keep-screen-awake"
        fun decode(values:Map<String,*>):NativeKotlinWorkoutDisplaySettings {
            // Preserve the existing reference key and exact enum names.
            // Unknown/wrong-type fields fall back independently; reading
            // does not rewrite or erase another version's preferences.
            return NativeKotlinWorkoutDisplaySettings(
                WorkoutReferenceMode.entries.firstOrNull{it.name==values[REFERENCE]}
                    ?:WorkoutReferenceMode.TARGET,
                WorkoutStartLayout.entries.firstOrNull{it.name==values[LAYOUT]}
                    ?:WorkoutStartLayout.AUTOMATIC,
                values[AWAKE] as? Boolean ?:true)
        }
    }
}
