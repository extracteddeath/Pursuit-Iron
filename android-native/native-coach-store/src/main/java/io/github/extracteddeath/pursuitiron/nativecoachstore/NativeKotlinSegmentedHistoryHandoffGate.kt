package io.github.extracteddeath.pursuitiron.nativecoachstore

/**
 * A strict pre-cutover REVIEW; not authority transfer.
 *
 * A checksummed v2 manifest and content-addressed segmented shadow can agree
 * while both still contain fabricated or missing workout receipts. This gate
 * ALSO replays every Composer and custom workout, including saved edits, under
 * the same owner transaction fence and compares the entire chronological
 * manifest. No accepted result enables PR publishing, progression, or a
 * second history writer.
 */
enum class NativeSegmentedHistoryHandoffRefusal {
    INVALID_SCOPE, NOT_AUTHORIZED, SHADOW_NOT_VERIFIED,
    WORKOUT_REPLAY_REJECTED, SOURCE_CHANGED, STORAGE_UNAVAILABLE
}

data class NativeSegmentedHistoryHandoffReview(
    val manifestRevision: Long,
    val completionCount: Int,
    val sourceFingerprint: String,
    val verifiedSegmentCount: Int
) {
    val canTransferWriteAuthority: Boolean get() = false
    val canIssueProgression: Boolean get() = false
    val canPublishPr: Boolean get() = false
}

sealed interface NativeSegmentedHistoryHandoffResult {
    data class Review(val review: NativeSegmentedHistoryHandoffReview) :
        NativeSegmentedHistoryHandoffResult
    data class Rejected(
        val reason: NativeSegmentedHistoryHandoffRefusal,
        val shadowReason: NativeSegmentedHistoryRefusal? = null,
        val replayReason: NativeKotlinMixedHistoryRefusal? = null
    ) : NativeSegmentedHistoryHandoffResult
}

class NativeKotlinSegmentedHistoryHandoffGate(
    private val ledgerStore: NativeKotlinHistoryManifestFileStore,
    private val shadowStore: NativeKotlinSegmentedHistoryShadowStore,
    private val replayReader: NativeKotlinMixedHistoryReadCoordinator,
    private val fence: NativeKotlinApprovalRevisionFence,
    private val identity: NativeKotlinCurrentIdentity
) {
    private val id = Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,79}$")

    fun review(
        owner: String, athlete: String
    ): NativeSegmentedHistoryHandoffResult {
        fun deny(reason: NativeSegmentedHistoryHandoffRefusal) =
            NativeSegmentedHistoryHandoffResult.Rejected(reason)
        if (!id.matches(owner) || !id.matches(athlete))
            return deny(NativeSegmentedHistoryHandoffRefusal.INVALID_SCOPE)
        return runCatching {
            fence.withOwnerTransaction(owner) {
                if (identity.signedInIdentity() !=
                    NativeKotlinOwnerIdentity(owner, athlete))
                    return@withOwnerTransaction deny(
                        NativeSegmentedHistoryHandoffRefusal.NOT_AUTHORIZED)

                // Same fence guards the authoritative ledger, all journals,
                // the immutable segmented index, and this final comparison.
                val parity = when (val result =
                    shadowStore.auditParity(owner, athlete)) {
                    is NativeSegmentedHistoryResult.Parity -> result.audit
                    is NativeSegmentedHistoryResult.Rejected ->
                        return@withOwnerTransaction NativeSegmentedHistoryHandoffResult.Rejected(
                            NativeSegmentedHistoryHandoffRefusal.SHADOW_NOT_VERIFIED,
                            shadowReason = result.reason)
                    else -> return@withOwnerTransaction deny(
                        NativeSegmentedHistoryHandoffRefusal.SHADOW_NOT_VERIFIED)
                }
                val replay = when (val read = replayReader.read(owner, athlete)) {
                    is NativeKotlinMixedHistoryReadResult.Review -> read.timeline
                    is NativeKotlinMixedHistoryReadResult.Rejected ->
                        return@withOwnerTransaction NativeSegmentedHistoryHandoffResult.Rejected(
                            NativeSegmentedHistoryHandoffRefusal.WORKOUT_REPLAY_REJECTED,
                            replayReason = read.reason)
                }

                val current = (ledgerStore.open(owner, athlete)
                    as? NativeHistoryLedgerResult.Durable)?.ledger
                    ?: return@withOwnerTransaction deny(
                        NativeSegmentedHistoryHandoffRefusal.SOURCE_CHANGED)
                if (!current.readyForHistoryReads ||
                    current.ownerKey != owner || current.athleteId != athlete ||
                    replay.athleteId != athlete ||
                    current.revision != parity.sourceRevision ||
                    replay.manifestRevision != parity.sourceRevision ||
                    current.completions.size != parity.verifiedCompletionCount ||
                    replay.completions.map { it.manifest } != current.completions ||
                    identity.signedInIdentity() !=
                        NativeKotlinOwnerIdentity(owner, athlete))
                    return@withOwnerTransaction deny(
                        NativeSegmentedHistoryHandoffRefusal.SOURCE_CHANGED)

                NativeSegmentedHistoryHandoffResult.Review(
                    NativeSegmentedHistoryHandoffReview(
                        parity.sourceRevision, parity.verifiedCompletionCount,
                        parity.sourceFingerprint, parity.verifiedSegmentCount))
            }
        }.getOrElse {
            deny(NativeSegmentedHistoryHandoffRefusal.STORAGE_UNAVAILABLE)
        }
    }
}
