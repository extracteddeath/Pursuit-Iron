package io.github.extracteddeath.pursuitiron.domain

/**
 * Presentation authorization remains separate from JSON decoding. A caller
 * must independently verify current canonical store bytes and tombstones;
 * matching synthetic or cached DTOs alone never proves a live user program.
 * The shipping preview supplies null and cannot activate this gate.
 */
sealed interface ReadOnlyProgramPresentation {
    data object Unavailable : ReadOnlyProgramPresentation
    data class Structure(val program: ReadOnlyProgramSummary) : ReadOnlyProgramPresentation
}

object ReadOnlyProgramPresentationPolicy {
    private val id = Regex("^[A-Za-z0-9][A-Za-z0-9_-]*$")
    private fun validText(value: String, limit: Int) =
        value.isNotBlank() && value.length <= limit && value.none { it.isISOControl() }

    fun inspect(
        summary: ReadOnlyProgramSummary?,
        currentReference: ReadOnlyProgramReference?,
        currentTombstoneAt: Long?
    ): ReadOnlyProgramPresentation {
        if (summary == null || currentReference == null ||
            !ReadOnlyProgramReferenceGate.sameActiveRevision(
                summary.reference, currentReference, currentTombstoneAt
            ) ||
            summary.id != summary.reference.programId ||
            !validText(summary.name, 120) || !validText(summary.goal, 40) ||
            summary.weeks !in 1..104 || summary.days.size !in 1..7) {
            return ReadOnlyProgramPresentation.Unavailable
        }
        val dayIds = mutableSetOf<String>()
        for (day in summary.days) {
            if (day.id.length > 128 || !id.matches(day.id) ||
                !dayIds.add(day.id) || !validText(day.label, 120) ||
                day.exerciseIds.size > 30 || day.exerciseIds.any {
                    it.length > 80 || !id.matches(it)
                }) return ReadOnlyProgramPresentation.Unavailable
        }
        return ReadOnlyProgramPresentation.Structure(summary)
    }
}
