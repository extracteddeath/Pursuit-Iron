package io.github.extracteddeath.pursuitiron.domain

/**
 * Selection is only a transient navigation intent, NEVER a workout session,
 * token granting logging authority, or cached personal program content.
 */
data class ReadOnlyTrainingDaySelection(
    val reference: ReadOnlyProgramReference,
    val dayId: String
)

/** Only independently policy-approved, current program structure can be shown. */
sealed interface ReadOnlyTrainingDayView {
    data object Unavailable : ReadOnlyTrainingDayView
    class Structure internal constructor(
        val programName: String,
        val dayLabel: String,
        val dayIndex: Int,
        val exerciseIds: List<String>
    ) : ReadOnlyTrainingDayView
}

/**
 * The Train tab must re-resolve an exact selected revision against the *current*
 * approved Plan presentation, not copy arbitrary old program data across tabs.
 * A changed snapshot, program revision or removed day invalidates selection.
 */
object ReadOnlyTrainingDayJourney {
    fun select(
        current: ReadOnlyProgramPresentation,
        dayId: String
    ): ReadOnlyTrainingDaySelection? {
        if (current !is ReadOnlyProgramPresentation.Structure) return null
        if (current.program.days.none { it.id == dayId }) return null
        return ReadOnlyTrainingDaySelection(current.program.reference, dayId)
    }

    fun inspect(
        current: ReadOnlyProgramPresentation,
        selection: ReadOnlyTrainingDaySelection?
    ): ReadOnlyTrainingDayView {
        if (current !is ReadOnlyProgramPresentation.Structure || selection == null)
            return ReadOnlyTrainingDayView.Unavailable
        val program = current.program
        if (selection.reference != program.reference ||
            selection.reference.programId != program.id)
            return ReadOnlyTrainingDayView.Unavailable
        val index = program.days.indexOfFirst { it.id == selection.dayId }
        if (index < 0) return ReadOnlyTrainingDayView.Unavailable
        val day = program.days[index]
        return ReadOnlyTrainingDayView.Structure(
            program.name, day.label, index, day.exerciseIds
        )
    }
}
