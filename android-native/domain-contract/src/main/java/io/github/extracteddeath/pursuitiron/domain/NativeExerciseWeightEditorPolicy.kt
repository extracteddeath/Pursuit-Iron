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

    /**
     * Raw list remains unchanged when the editor switches mode. No conversion
     * or implicit default; only a user-supplied list can become a saved fact.
     */
    fun withKind(kind: String, raw: String): String {
        require(kind == "exact" || kind == "range")
        val input = raw.trim()
        val body = input.substringAfter(':', input).trim()
        return if (body.isEmpty()) "" else "$kind: $body"
    }

    fun entryValues(raw: String): String = raw.substringAfter(':', raw).trim()
}
