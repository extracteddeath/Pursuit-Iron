package io.github.extracteddeath.pursuitiron.domain

/**
 * Navigation over already approved synthetic canonical workout structure.
 * This is a review cursor, NOT a completed set or an athlete's workout.
 * It neither predicts per-set performance nor changes engine prescriptions.
 */
data class SyntheticSetCursor(val exerciseIndex: Int, val setIndex: Int)

data class SyntheticSetPosition(
    val exercise: WorkoutOutlineExercise,
    val exerciseNumber: Int,
    val setNumber: Int,
    val exerciseCount: Int,
    val totalExerciseSets: Int,
    val overallSetNumber: Int,
    val totalWorkoutSets: Int
)

object SyntheticSetReviewNavigation {
    fun inspect(
        approved: OutlinePresentation.SyntheticOnly,
        cursor: SyntheticSetCursor
    ): SyntheticSetPosition? {
        val exercises = approved.outline.exercises
        if (cursor.exerciseIndex !in exercises.indices) return null
        val current = exercises[cursor.exerciseIndex]
        if (cursor.setIndex !in 0 until current.sets) return null
        val priorSets = exercises.take(cursor.exerciseIndex).sumOf { it.sets }
        return SyntheticSetPosition(
            exercise = current,
            exerciseNumber = cursor.exerciseIndex + 1,
            setNumber = cursor.setIndex + 1,
            exerciseCount = exercises.size,
            totalExerciseSets = current.sets,
            overallSetNumber = priorSets + cursor.setIndex + 1,
            totalWorkoutSets = exercises.sumOf { it.sets }
        )
    }

    fun next(
        approved: OutlinePresentation.SyntheticOnly,
        cursor: SyntheticSetCursor
    ): SyntheticSetCursor? {
        val current = inspect(approved, cursor) ?: return null
        return when {
            cursor.setIndex + 1 < current.totalExerciseSets ->
                cursor.copy(setIndex = cursor.setIndex + 1)
            cursor.exerciseIndex + 1 < current.exerciseCount ->
                SyntheticSetCursor(cursor.exerciseIndex + 1, 0)
            else -> null
        }
    }

    fun previous(
        approved: OutlinePresentation.SyntheticOnly,
        cursor: SyntheticSetCursor
    ): SyntheticSetCursor? {
        if (inspect(approved, cursor) == null) return null
        return when {
            cursor.setIndex > 0 -> cursor.copy(setIndex = cursor.setIndex - 1)
            cursor.exerciseIndex > 0 -> {
                val before = cursor.exerciseIndex - 1
                SyntheticSetCursor(before, approved.outline.exercises[before].sets - 1)
            }
            else -> null
        }
    }

    fun jumpToExercise(
        approved: OutlinePresentation.SyntheticOnly,
        exerciseIndex: Int
    ): SyntheticSetCursor? =
        SyntheticSetCursor(exerciseIndex, 0).takeIf { inspect(approved, it) != null }
}
