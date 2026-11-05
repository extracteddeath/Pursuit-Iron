package io.github.extracteddeath.pursuitiron.nativecoachstore

/**
 * INACTIVE post-v2-commit reconciliation for a candidate mirror.
 *
 * The segmented root must ALREADY match independent authoritative v2,
 * generated/custom journal, saved-correction and shadow replay. This
 * coordinator cannot append to the real history, publish a segmented root,
 * clear redo, grant new workout identity, or replace native write authority.
 *
 * Under one cross-process owner fence, reconcile the independently verified
 * paged index (including a stale prior revision) and atomically acknowledge
 * a same-revision root/index checkpoint. A crash leaves either no checkpoint,
 * a stale old checkpoint, or an exact already-committed checkpoint. Each
 * state can be safely retried, but a corrupted old index/committed checkpoint
 * or an absent index referenced by a committed checkpoint is NEVER repaired
 * from missing-file inference. Fully authenticated source replay remains
 * mandatory on every attempt.
 *
 * This is NOT a production cross-file atomic WAL. The v2 writer remains
 * authoritative, and actual device power-loss/directory fsync testing
 * and an exclusive native source-owner cutover remain blockers.
 */
internal enum class NativeCandidateMirrorRecoveryRefusal {
    INVALID_SCOPE, NOT_AUTHORIZED, UNVERIFIED_ROOT,
    LOST_COMMITTED_INDEX, CORRUPT_INDEX, CORRUPT_CHECKPOINT,
    UNVERIFIED_INDEX, CONFLICT, STORAGE_UNAVAILABLE
}
internal sealed interface NativeCandidateMirrorRecoveryResult {
    data class Consistent(
        val checkpoint: NativeCandidatePairedHistoryCheckpoint,
        val replayed: Boolean
    ) : NativeCandidateMirrorRecoveryResult {
        val canAuthorizeWrites: Boolean get() = false
        val canIssueProgression: Boolean get() = false
        val canTransferAuthority: Boolean get() = false
    }
    data class Rejected(val reason: NativeCandidateMirrorRecoveryRefusal) :
        NativeCandidateMirrorRecoveryResult
}
internal class NativeKotlinCandidateHistoryMirrorRecoveryCoordinator(
    private val fence: NativeKotlinApprovalRevisionFence,
    private val identity: NativeKotlinCurrentIdentity,
    private val root: NativeKotlinCandidateSegmentedRootFileStore,
    private val index: NativeKotlinCandidatePagedIdentityFileStore,
    private val checkpoint: NativeKotlinCandidatePairedHistoryCheckpointFileStore,
    private val afterIndexForTest: (() -> Unit)? = null,
    private val afterCheckpointForTest: (() -> Unit)? = null
) {
    private val id = Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,79}$")
    private fun deny(reason: NativeCandidateMirrorRecoveryRefusal) =
        NativeCandidateMirrorRecoveryResult.Rejected(reason)

    fun reconcile(owner: String, athlete: String):
        NativeCandidateMirrorRecoveryResult {
        if (!id.matches(owner) || !id.matches(athlete))
            return deny(NativeCandidateMirrorRecoveryRefusal.INVALID_SCOPE)
        return runCatching {
            fence.withOwnerTransaction(owner) {
                if (identity.signedInIdentity() !=
                    NativeKotlinOwnerIdentity(owner, athlete))
                    return@withOwnerTransaction deny(
                        NativeCandidateMirrorRecoveryRefusal.NOT_AUTHORIZED)
                val current = (root.reviewAuthenticatedV2Parity(owner, athlete)
                    as? NativeCandidateHistoryRootResult.Reviewed)?.view
                    ?: return@withOwnerTransaction deny(
                        NativeCandidateMirrorRecoveryRefusal.UNVERIFIED_ROOT)
                val presence = checkpoint.presence(owner, athlete)
                if (presence == NativeCandidateCheckpointPresence.INVALID)
                    return@withOwnerTransaction deny(
                        NativeCandidateMirrorRecoveryRefusal.CORRUPT_CHECKPOINT)

                val indexed = index.audit(owner, athlete)
                when (indexed) {
                    is NativeCandidatePagedIdentityResult.Indexed -> {
                        if (indexed.view.rootDigest != current.head.rootDigest ||
                            indexed.view.sourceRevision != current.head.revision ||
                            indexed.view.completionCount != current.head.completionCount)
                            return@withOwnerTransaction deny(
                                NativeCandidateMirrorRecoveryRefusal.CONFLICT)
                    }
                    is NativeCandidatePagedIdentityResult.Rejected -> {
                        if (indexed.reason ==
                            NativeCandidatePagedIdentityRefusal.CORRUPT_INDEX)
                            return@withOwnerTransaction deny(
                                NativeCandidateMirrorRecoveryRefusal.CORRUPT_INDEX)
                        if (indexed.reason ==
                            NativeCandidatePagedIdentityRefusal.NOT_INDEXED &&
                            presence == NativeCandidateCheckpointPresence.PRESENT)
                            return@withOwnerTransaction deny(
                                NativeCandidateMirrorRecoveryRefusal.LOST_COMMITTED_INDEX)
                        if (indexed.reason !=
                            NativeCandidatePagedIdentityRefusal.NOT_INDEXED &&
                            indexed.reason !=
                            NativeCandidatePagedIdentityRefusal.STALE_INDEX)
                            return@withOwnerTransaction deny(
                                NativeCandidateMirrorRecoveryRefusal.UNVERIFIED_INDEX)
                        when (val built = index.build(owner, athlete)) {
                            is NativeCandidatePagedIdentityResult.Indexed -> {
                                if (built.view.rootDigest != current.head.rootDigest ||
                                    built.view.sourceRevision != current.head.revision ||
                                    built.view.completionCount != current.head.completionCount)
                                    return@withOwnerTransaction deny(
                                        NativeCandidateMirrorRecoveryRefusal.CONFLICT)
                            }
                            is NativeCandidatePagedIdentityResult.Rejected -> {
                                return@withOwnerTransaction deny(
                                    if (built.reason ==
                                        NativeCandidatePagedIdentityRefusal.CORRUPT_INDEX)
                                        NativeCandidateMirrorRecoveryRefusal.CORRUPT_INDEX
                                    else NativeCandidateMirrorRecoveryRefusal.UNVERIFIED_INDEX)
                            }
                            else -> return@withOwnerTransaction deny(
                                NativeCandidateMirrorRecoveryRefusal.UNVERIFIED_INDEX)
                        }
                    }
                    else -> return@withOwnerTransaction deny(
                        NativeCandidateMirrorRecoveryRefusal.UNVERIFIED_INDEX)
                }
                // Intentional crash seam: a valid index can be ahead of an
                // absent/old checkpoint, but is not an acknowledged pair.
                afterIndexForTest?.invoke()
                val committed = when (
                    val saved = checkpoint.checkpoint(owner, athlete)
                ) {
                    is NativeCandidatePairedHistoryResult.Verified -> saved
                    is NativeCandidatePairedHistoryResult.Rejected ->
                        return@withOwnerTransaction deny(
                            when (saved.reason) {
                                NativeCandidatePairedHistoryRefusal.CORRUPT_CHECKPOINT ->
                                    NativeCandidateMirrorRecoveryRefusal.CORRUPT_CHECKPOINT
                                NativeCandidatePairedHistoryRefusal.STALE_CHECKPOINT ->
                                    NativeCandidateMirrorRecoveryRefusal.CONFLICT
                                else -> NativeCandidateMirrorRecoveryRefusal.UNVERIFIED_INDEX
                            })
                }
                afterCheckpointForTest?.invoke()
                val reviewed = checkpoint.review(owner, athlete)
                    as? NativeCandidatePairedHistoryResult.Verified
                    ?: return@withOwnerTransaction deny(
                        NativeCandidateMirrorRecoveryRefusal.UNVERIFIED_INDEX)
                if (reviewed.checkpoint != committed.checkpoint ||
                    reviewed.checkpoint.rootDigest != current.head.rootDigest ||
                    reviewed.checkpoint.sourceRevision != current.head.revision ||
                    reviewed.checkpoint.completionCount != current.head.completionCount)
                    return@withOwnerTransaction deny(
                        NativeCandidateMirrorRecoveryRefusal.CONFLICT)
                NativeCandidateMirrorRecoveryResult.Consistent(
                    reviewed.checkpoint, committed.replayed)
            }
        }.getOrElse { deny(NativeCandidateMirrorRecoveryRefusal.STORAGE_UNAVAILABLE) }
    }
}
