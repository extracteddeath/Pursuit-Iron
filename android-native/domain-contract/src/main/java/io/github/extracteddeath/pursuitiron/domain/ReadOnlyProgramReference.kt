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
    private val digestPattern = Regex("^[0-9a-f]{64}$")

    private fun wellFormed(ref: ReadOnlyProgramReference): Boolean =
        ref.programId.isNotBlank() && ref.programId.length <= 128 &&
            ref.programId.none { it.isISOControl() } &&
            ref.programUpdatedAt >= 0 && ref.storeSavedAt >= 0 &&
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
            (currentTombstoneAt < 0 ||
                currentTombstoneAt >= current.programUpdatedAt)) return false
        return requested == current
    }
}
