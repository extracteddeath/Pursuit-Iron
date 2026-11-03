package io.github.extracteddeath.pursuitiron.domain

/**
 * Presentation-only contract for exercise-specific verified machine loading.
 * Candidate IDs and modes are supplied by the authenticated, saved program.
 * Filtering never creates an exercise, a weight or a selectable increment.
 */
object NativeExerciseWeightEditorPolicy {
    fun eligible(
        candidates: List<NativeStackCandidate>,
        query: String,
        configuredOnly: Boolean,
        draft: Map<String, String>,
        focusedId: String? = null
    ): List<NativeStackCandidate> {
        val search = query.trim()
        return candidates.distinctBy { it.id }.filter { candidate ->
            val configured = !draft["override:${candidate.id}"].isNullOrBlank()
            (candidate.id == focusedId ||
                (!configuredOnly || configured) &&
                    (search.isEmpty() ||
                        candidate.name.contains(search, ignoreCase = true) ||
                        candidate.id.contains(search, ignoreCase = true)))
        }
    }

    fun configuredCount(
        candidates: List<NativeStackCandidate>,
        draft: Map<String, String>
    ): Int = candidates.map { it.id }.distinct().count {
        !draft["override:$it"].isNullOrBlank()
    }

    /** All ambiguous specialty machines have exact-only physical loads. */
    fun exactOnly(candidate: NativeStackCandidate): Boolean =
        candidate.mode == "external_load"

    fun preferredKind(candidate: NativeStackCandidate, existing: String?): String =
        if (exactOnly(candidate) || !existing.orEmpty().trimStart()
                .startsWith("range:", ignoreCase = true)) "exact" else "range"

    /** The field edits numbers only. Type selection never converts a saved range. */
    fun formatEntry(kind: String, values: String, exactOnly: Boolean): String {
        require(kind in setOf("exact", "range") &&
            !(exactOnly && kind == "range"))
        val body = values.trim()
        return if (body.isEmpty()) "" else "$kind: $body"
    }

    fun entryValues(raw: String): String = raw.substringAfter(':', raw).trim()
}
