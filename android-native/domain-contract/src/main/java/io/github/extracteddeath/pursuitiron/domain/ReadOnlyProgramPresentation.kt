package io.github.extracteddeath.pursuitiron.domain

/** Distinguish an explicitly checked empty tombstone from missing evidence. */
sealed interface CurrentProgramDeletionEvidence {
    data object CheckedAbsent : CurrentProgramDeletionEvidence
    data class CheckedAt(val deletedAt: Long) : CurrentProgramDeletionEvidence
}

/** Independent current canonical-store facts; never infer from an offline backup. */
data class CurrentProgramEvidence(
    val reference: ReadOnlyProgramReference,
    val deletion: CurrentProgramDeletionEvidence
)

/** Approved structure only. No prescriptions, logging or writes. */
sealed interface ReadOnlyProgramPresentation {
    data object Unavailable : ReadOnlyProgramPresentation
    data class Structure internal constructor(
        val program: ReadOnlyProgramSummary
    ) : ReadOnlyProgramPresentation
}

object ReadOnlyProgramPresentationPolicy {
    private val id = Regex("^[A-Za-z0-9][A-Za-z0-9_-]*$")
    private fun validText(value: String, limit: Int) =
        value.isNotBlank() && value.length <= limit && value.none { it.isISOControl() }

    fun inspect(
        summary: ReadOnlyProgramSummary?,
        evidence: CurrentProgramEvidence?
    ): ReadOnlyProgramPresentation {
        if (summary == null || evidence == null) return ReadOnlyProgramPresentation.Unavailable
        val deletedAt = when (val deletion = evidence.deletion) {
            CurrentProgramDeletionEvidence.CheckedAbsent -> null
            is CurrentProgramDeletionEvidence.CheckedAt -> deletion.deletedAt
        }
        if (!ReadOnlyProgramReferenceGate.sameActiveRevision(
                summary.reference, evidence.reference, deletedAt
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
