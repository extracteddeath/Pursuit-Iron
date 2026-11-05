package io.github.extracteddeath.pursuitiron.nativecoachstore

/**
 * Recovery rehearsal for the candidate root -> shadow -> paired index/seal
 * publication chain AFTER the real v2 writer has durably committed a Finish
 * or correction. No new workout, journal, or correction is written here.
 *
 * The entire chain runs behind one cross-process owner fence. Each stage
 * independently reopens and authenticates its on-disk inputs; a crash at
 * any boundary can be retried without deriving a completion from a UI,
 * a staged patch, or a missing index. The staged redo is deliberately kept
 * for investigation. This is NOT the production single-writer transaction:
 * v2 remains the ONLY history authority, including for progression.
 */
internal enum class NativeCandidateStagedRecoveryRefusal {
    INVALID_SCOPE, NOT_AUTHORIZED, NO_VERIFIED_STAGED_REDO,
    ROOT_NOT_VERIFIED, SHADOW_NOT_VERIFIED, PAIR_NOT_VERIFIED,
    DURABLE_INTENT_NOT_VERIFIED, INTENT_NOT_ACKNOWLEDGED,
    CONFLICT, STORAGE_UNAVAILABLE
}

internal sealed interface NativeCandidateStagedRecoveryResult {
    data class Consistent(
        val checkpoint: NativeCandidatePairedHistoryCheckpoint,
        val rootReplayed: Boolean,
        val shadowReplayed: Boolean,
        val checkpointReplayed: Boolean,
        val intentAcknowledgmentReplayed: Boolean
    ) : NativeCandidateStagedRecoveryResult {
        val canAuthorizeHistoryReads: Boolean get() = false
        val canAuthorizeWrites: Boolean get() = false
        val canIssueProgression: Boolean get() = false
        val canTransferAuthority: Boolean get() = false
    }

    data class Rejected(val reason: NativeCandidateStagedRecoveryRefusal) :
        NativeCandidateStagedRecoveryResult
}

internal class NativeKotlinCandidateStagedReplayRecoveryCoordinator(
    private val fence: NativeKotlinApprovalRevisionFence,
    private val identity: NativeKotlinCurrentIdentity,
    private val redo: NativeKotlinSegmentedRedoFileStore,
    private val durableIntent: NativeKotlinCandidateHistoryTransactionIntentFileStore,
    private val root: NativeKotlinCandidateSegmentedRootFileStore,
    private val replayReader: NativeKotlinMixedHistoryReadCoordinator,
    private val receiptVerifier: NativeKotlinIndependentHistoryReceiptVerifier,
    private val shadow: NativeKotlinSegmentedHistoryShadowStore,
    private val mirrorRecovery: NativeKotlinCandidateHistoryMirrorRecoveryCoordinator,
    private val afterRootForTest: (() -> Unit)? = null,
    private val afterShadowForTest: (() -> Unit)? = null
) {
    private val id = Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,79}$")
    private fun deny(reason: NativeCandidateStagedRecoveryRefusal) =
        NativeCandidateStagedRecoveryResult.Rejected(reason)

    fun resume(owner: String, athlete: String):
        NativeCandidateStagedRecoveryResult {
        if (!id.matches(owner) || !id.matches(athlete))
            return deny(NativeCandidateStagedRecoveryRefusal.INVALID_SCOPE)
        return runCatching {
            fence.withOwnerTransaction(owner) {
                if (identity.signedInIdentity() !=
                    NativeKotlinOwnerIdentity(owner, athlete))
                    return@withOwnerTransaction deny(
                        NativeCandidateStagedRecoveryRefusal.NOT_AUTHORIZED)

                // The redo must be an actual persisted, fully staged intent.
                // A missing or corrupt WAL is never inferred from v2 history.
                val staged = (redo.read(owner, athlete)
                    as? NativeSegmentedRedoResult.Durable)?.redo
                    ?: return@withOwnerTransaction deny(
                        NativeCandidateStagedRecoveryRefusal.NO_VERIFIED_STAGED_REDO)
                val patch = staged.stagedPatch
                    ?: return@withOwnerTransaction deny(
                        NativeCandidateStagedRecoveryRefusal.NO_VERIFIED_STAGED_REDO)
                if (patch.consumedIntent != staged.intent ||
                    patch.priorRootDigest != staged.base.rootDigest)
                    return@withOwnerTransaction deny(
                        NativeCandidateStagedRecoveryRefusal.CONFLICT)

                // Freeze the exact staged operation BEFORE publishing root or
                // touching a paged index. A previously persisted ticket must
                // equal this redo, even after an interrupted root swap.
                val prepared = durableIntent.prepare(owner, athlete)
                    as? NativeCandidateTransactionIntentResult.Durable
                    ?: return@withOwnerTransaction deny(
                        NativeCandidateStagedRecoveryRefusal.DURABLE_INTENT_NOT_VERIFIED)
                if (prepared.intent.nextRevision != patch.nextHeadRevision ||
                    prepared.intent.nextCompletionCount != patch.nextCompletionCount ||
                    prepared.intent.baseRootDigest != patch.priorRootDigest)
                    return@withOwnerTransaction deny(
                        NativeCandidateStagedRecoveryRefusal.CONFLICT)

                // Authenticates journal/receipt and full replay against the
                // already committed v2 manifest before it publishes a root.
                val published = root.publishReplayVerifiedV2Mirror(
                    owner, athlete, replayReader, receiptVerifier)
                    as? NativeCandidateHistoryRootResult.Reviewed
                    ?: return@withOwnerTransaction deny(
                        NativeCandidateStagedRecoveryRefusal.ROOT_NOT_VERIFIED)
                if (published.view.head.rootDigest.isBlank() ||
                    published.view.head.revision != patch.nextHeadRevision ||
                    published.view.head.completionCount != patch.nextCompletionCount)
                    return@withOwnerTransaction deny(
                        NativeCandidateStagedRecoveryRefusal.CONFLICT)
                afterRootForTest?.invoke()

                val synced = shadow.sync(owner, athlete)
                    as? NativeSegmentedHistoryResult.Indexed
                    ?: return@withOwnerTransaction deny(
                        NativeCandidateStagedRecoveryRefusal.SHADOW_NOT_VERIFIED)
                if (synced.revision != patch.nextHeadRevision ||
                    synced.count.toLong() != patch.nextCompletionCount)
                    return@withOwnerTransaction deny(
                        NativeCandidateStagedRecoveryRefusal.CONFLICT)
                afterShadowForTest?.invoke()

                val pair = mirrorRecovery.reconcile(owner, athlete)
                    as? NativeCandidateMirrorRecoveryResult.Consistent
                    ?: return@withOwnerTransaction deny(
                        NativeCandidateStagedRecoveryRefusal.PAIR_NOT_VERIFIED)
                if (pair.checkpoint.ownerKey != owner ||
                    pair.checkpoint.athleteId != athlete ||
                    pair.checkpoint.sourceRevision != patch.nextHeadRevision ||
                    pair.checkpoint.completionCount != patch.nextCompletionCount ||
                    pair.checkpoint.rootDigest != published.view.head.rootDigest)
                    return@withOwnerTransaction deny(
                        NativeCandidateStagedRecoveryRefusal.CONFLICT)
                // This final intent acknowledgment is a second durable
                // candidate marker, NOT the new application's commit point.
                val acknowledged = durableIntent.acknowledge(owner, athlete)
                    as? NativeCandidateTransactionIntentResult.Durable
                    ?: return@withOwnerTransaction deny(
                        NativeCandidateStagedRecoveryRefusal.INTENT_NOT_ACKNOWLEDGED)
                if (!acknowledged.intent.acknowledged ||
                    acknowledged.intent.acknowledgedRootDigest !=
                        pair.checkpoint.rootDigest ||
                    acknowledged.intent.acknowledgedDirectoryDigest !=
                        pair.checkpoint.pagedDirectoryFileDigest)
                    return@withOwnerTransaction deny(
                        NativeCandidateStagedRecoveryRefusal.CONFLICT)
                NativeCandidateStagedRecoveryResult.Consistent(
                    pair.checkpoint, published.replayed, synced.replayed,
                    pair.replayed, acknowledged.replayed)
            }
        }.getOrElse {
            deny(NativeCandidateStagedRecoveryRefusal.STORAGE_UNAVAILABLE)
        }
    }
}
