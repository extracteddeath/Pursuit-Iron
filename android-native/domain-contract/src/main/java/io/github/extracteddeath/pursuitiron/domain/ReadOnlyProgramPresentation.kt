package io.github.extracteddeath.pursuitiron.domain

import java.util.Collections

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
    // Not a data class: generated public copy()/component methods would
    // allow other modules to manufacture an approved-looking presentation.
    class Structure internal constructor(
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
        // Detach from potentially mutable source collections BEFORE validation.
        // The original program may be edited or reused while Compose displays.
        // Java unmodifiable wrappers also reject runtime MutableList casts.
        val safe = try {
            val days = summary.days.map { day ->
                day.copy(exerciseIds = Collections.unmodifiableList(ArrayList(day.exerciseIds)))
            }
            summary.copy(days = Collections.unmodifiableList(ArrayList(days)))
        } catch (_: RuntimeException) {
            return ReadOnlyProgramPresentation.Unavailable
        }
        val deletedAt = when (val deletion = evidence.deletion) {
            CurrentProgramDeletionEvidence.CheckedAbsent -> null
            is CurrentProgramDeletionEvidence.CheckedAt -> deletion.deletedAt
        }
        if (!ReadOnlyProgramReferenceGate.sameActiveRevision(
                safe.reference, evidence.reference, deletedAt
            ) ||
            safe.id != safe.reference.programId ||
            !validText(safe.name, 120) || !validText(safe.goal, 40) ||
            safe.weeks !in 1..104 || safe.days.size !in 1..7) {
            return ReadOnlyProgramPresentation.Unavailable
        }
        val dayIds = mutableSetOf<String>()
        for (day in safe.days) {
            if (day.id.length > 128 || !id.matches(day.id) ||
                !dayIds.add(day.id) || !validText(day.label, 120) ||
                day.exerciseIds.size > 30 || day.exerciseIds.any {
                    it.length > 80 || !id.matches(it)
                }) return ReadOnlyProgramPresentation.Unavailable
        }
        return ReadOnlyProgramPresentation.Structure(safe)
    }
}
