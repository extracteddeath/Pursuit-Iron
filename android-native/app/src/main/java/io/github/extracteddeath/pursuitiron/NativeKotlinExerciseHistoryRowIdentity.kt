package io.github.extracteddeath.pursuitiron

import io.github.extracteddeath.pursuitiron.rebuild.CustomWorkoutVisitPart

/**
 * Stable, collision-free presentation identity for a single factual History
 * visit. A multi-stage myo/rest-pause/cluster/drop sequence has a distinct
 * row per stage. This is not a journal ID or correction authorization.
 */
internal fun nativeExerciseHistoryRowKey(
    workoutId:String,
    setKey:String,
    part:CustomWorkoutVisitPart?,
    stage:Int=1
):String {
    require(workoutId.isNotEmpty() && setKey.isNotEmpty() && stage in 1..5)
    return "${workoutId.length}:$workoutId:${setKey.length}:$setKey:"+
        (part?.name ?: "COMPOSER")+":$stage"
}
