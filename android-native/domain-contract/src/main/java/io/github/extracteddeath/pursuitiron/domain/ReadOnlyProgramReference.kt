package io.github.extracteddeath.pursuitiron.domain

/**
 * Inactive, data-only program reference. The Node-only canonical v13
 * inspector derives matching fields from exact source bytes, but the native
 * app has no verified importer or current-store owner yet.
 * It is NOT a schema parser, renderer, write permit or program snapshot.
 *
 * Canonical records migrate per-program updatedAt, use savedAt for legacy
 * tie-breaking, and honor tombstones at/after a record's update time.
 */
data class ReadOnlyProgramReference(
    val programId: String,
    val programUpdatedAt: Long,
    val storeSavedAt: Long,
    val storeVersion: Int,
    val snapshotSha256: String
)

/** Refuse rather than silently resolve a different revision or deleted program. */
object ReadOnlyProgramReferenceGate {
    const val SUPPORTED_STORE_VERSION = 13
    private const val MAX_EXACT_JS_INTEGER = 9_007_199_254_740_991L
    private val programIdPattern = Regex("^[A-Za-z0-9][A-Za-z0-9_-]{0,127}$")
    private val digestPattern = Regex("^[0-9a-f]{64}$")
    private fun validClock(value: Long): Boolean = value in 0..MAX_EXACT_JS_INTEGER

    private fun wellFormed(ref: ReadOnlyProgramReference): Boolean =
        programIdPattern.matches(ref.programId) &&
            validClock(ref.programUpdatedAt) && validClock(ref.storeSavedAt) &&
            ref.storeVersion == SUPPORTED_STORE_VERSION &&
            digestPattern.matches(ref.snapshotSha256)

    /**
     * The caller must supply independently parsed CURRENT canonical store
     * facts; it must never infer missing clocks, tombstones or digests.
     */
    fun sameActiveRevision(
        requested: ReadOnlyProgramReference,
        current: ReadOnlyProgramReference,
        currentTombstoneAt: Long?
    ): Boolean {
        if (!wellFormed(requested) || !wellFormed(current)) return false
        if (currentTombstoneAt != null &&
            (!validClock(currentTombstoneAt) ||
                currentTombstoneAt >= current.programUpdatedAt)) return false
        return requested == current
    }
}
