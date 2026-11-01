package io.github.extracteddeath.pursuitiron.domain

/**
 * The training UI may inspect synthetic-only, audited engine output but MUST NOT
 * mistake it for the athlete's program. This module has no loading, logging,
 * persistence, identity or prescription capability.
 */
sealed interface OutlinePresentation {
    data object Unavailable : OutlinePresentation
    data class SyntheticOnly(val outline: WorkoutOutline) : OutlinePresentation
}

/** Explicitly denies absent, live-looking or malformed session metadata. */
object OutlinePresentationPolicy {
    fun inspect(outline: WorkoutOutline?): OutlinePresentation {
        if (outline == null ||
            outline.source != WorkoutOutlineCodec.SOURCE ||
            !outline.readOnly || !outline.syntheticOnly ||
            !WorkoutOutlineCodec.validChoice(outline.split, outline.dayIndex) ||
            outline.sessionId.isBlank() || outline.sessionId.length > 128 ||
            outline.day.isBlank() || outline.day.length > 40 ||
            !outline.estimatedMinutes.isFinite() ||
            !outline.maxMinutes.isFinite() ||
            outline.estimatedMinutes !in 0.0..600.0 ||
            outline.maxMinutes !in 0.0..600.0 ||
            outline.exercises.size !in 1..24
        ) return OutlinePresentation.Unavailable
        val safe = outline.exercises.all { exercise ->
            exercise.exerciseId.matches(Regex("^[a-zA-Z0-9][a-zA-Z0-9_-]{0,79}$")) &&
                exercise.name.isNotBlank() && exercise.name.length <= 120 &&
                exercise.role.isNotBlank() && exercise.role.length <= 60 &&
                exercise.sets in 1..20 &&
                exercise.repMin in 1..100 && exercise.repMax in exercise.repMin..100 &&
                exercise.rirMin in 0..10 && exercise.rirMax in exercise.rirMin..10 &&
                exercise.restSeconds.isFinite() && exercise.restSeconds in 0.0..600.0 &&
                exercise.progressionStyle.isNotBlank() && exercise.progressionStyle.length <= 50
        }
        return if (safe) OutlinePresentation.SyntheticOnly(outline)
            else OutlinePresentation.Unavailable
    }
}
