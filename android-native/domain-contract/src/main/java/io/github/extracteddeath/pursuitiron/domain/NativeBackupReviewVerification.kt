package io.github.extracteddeath.pursuitiron.domain

/**
 * In-process, read-only re-selection certificate.
 *
 * A preview by itself cannot bind a later provider selection to the exact
 * bytes originally reviewed. Only fingerprints and redacted canonical counts
 * are retained. This cannot create NativeBackupRestoreApproval, grant an
 * import, or call a store writer.
 */
class NativeBackupReviewTicket private constructor(
    val preview: NativeBackupMergePreview,
    val selectedBytes: Int
) {
    companion object {
        private const val MAX_BYTES=ReadOnlyBackupLimits.MAX_SOURCE_BYTES
        private val digestPattern=Regex("^[0-9a-f]{64}$")

        fun open(preview: NativeBackupMergePreview, selected: UnverifiedBackupFingerprint):
            NativeBackupReviewTicket {
            require(selected.byteCount in 1..MAX_BYTES.toLong())
            require(digestPattern.matches(selected.sha256Hex) &&
                digestPattern.matches(preview.incomingSha256) &&
                digestPattern.matches(preview.currentSha256) &&
                digestPattern.matches(preview.hypotheticalMergedSha256))
            require(preview.incomingSha256==selected.sha256Hex)
            require(preview.hypotheticalMergedBytes>0)
            return NativeBackupReviewTicket(preview,selected.byteCount.toInt())
        }
    }

    /**
     * Requires a new, complete independent provider read, a new owner-side
     * canonical preview, and byte-for-byte-equivalent redacted risk data.
     * A changed saved store, altered backup or different conflict prediction
     * can never masquerade as verification.
     */
    fun matchesReselection(
        freshSelection: UnverifiedBackupFingerprint,
        freshPreview: NativeBackupMergePreview
    ): Boolean =
        freshSelection.byteCount==selectedBytes.toLong() &&
        freshSelection.sha256Hex==preview.incomingSha256 &&
        freshPreview==preview

    val additiveCandidateOnly: Boolean get() =
        preview.flags.isEmpty() && preview.conflicts==0 &&
        preview.historyDropped==0 && preview.ownedRecordsRemoved==0 &&
        preview.ownedRecordsReplaced==0 && preview.settingsChanged==0

    // Even an additive candidate is informational. This type deliberately
    // offers neither approval evidence nor an install/undo entry point.
}
