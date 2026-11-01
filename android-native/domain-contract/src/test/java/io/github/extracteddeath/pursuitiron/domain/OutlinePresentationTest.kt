package io.github.extracteddeath.pursuitiron.domain

import org.junit.Assert.*
import org.junit.Test

class OutlinePresentationTest {
    private val exercise = WorkoutOutlineExercise(
        exerciseId = "bb-bench", name = "Barbell Bench Press",
        role = "primary_strength", sets = 3, repMin = 5, repMax = 8,
        rirMin = 1, rirMax = 3, restSeconds = 180.0, progressionStyle = "double"
    )
    private val synthetic = WorkoutOutline(
        source = WorkoutOutlineCodec.SOURCE, readOnly = true, syntheticOnly = true,
        split = "full_body", dayIndex = 0, sessionId = "synthetic-session",
        day = "monday", estimatedMinutes = 48.0, maxMinutes = 60.0,
        exercises = listOf(exercise)
    )

    @Test fun onlyExplicitReadOnlySyntheticOutlineCanBeShown() {
        assertEquals(OutlinePresentation.Unavailable,OutlinePresentationPolicy.inspect(null))
        assertEquals(OutlinePresentation.SyntheticOnly(synthetic),
            OutlinePresentationPolicy.inspect(synthetic))
        assertEquals(OutlinePresentation.Unavailable,OutlinePresentationPolicy.inspect(
            synthetic.copy(syntheticOnly = false)))
        assertEquals(OutlinePresentation.Unavailable,OutlinePresentationPolicy.inspect(
            synthetic.copy(readOnly = false)))
        assertEquals(OutlinePresentation.Unavailable,OutlinePresentationPolicy.inspect(
            synthetic.copy(source = "inferred")))
        assertEquals(OutlinePresentation.Unavailable,OutlinePresentationPolicy.inspect(
            synthetic.copy(dayIndex = 3)))
    }

    @Test fun presentationNeverCorrectsOrInventsEngineTargets() {
        for (invalid in listOf(
            exercise.copy(sets = 0),
            exercise.copy(repMin = 12, repMax = 10),
            exercise.copy(rirMax = 12),
            exercise.copy(restSeconds = Double.NaN),
            exercise.copy(restSeconds = Double.POSITIVE_INFINITY),
            exercise.copy(exerciseId = "invalid id"),
            exercise.copy(progressionStyle = "")
        )) {
            assertEquals(OutlinePresentation.Unavailable,
                OutlinePresentationPolicy.inspect(synthetic.copy(exercises=listOf(invalid))))
        }
        assertEquals(OutlinePresentation.Unavailable,
            OutlinePresentationPolicy.inspect(synthetic.copy(exercises=emptyList())))
    }
}
