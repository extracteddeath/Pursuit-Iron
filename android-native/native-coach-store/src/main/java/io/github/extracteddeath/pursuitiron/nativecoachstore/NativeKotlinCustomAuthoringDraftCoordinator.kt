package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.CustomTrainingCycle

/**
 * Owner-locked cross-store CAS: a reviewed custom exercise edit is only
 * durable while the exact native gym/catalog revision used to validate
 * physical loads and attachments is still authoritative.
 *
 * This is an authoring safeguard, not a program approval. Regular edits to
 * blank titles/blocks may use the independent draft store.
 */
class NativeKotlinCustomAuthoringDraftCoordinator(
    private val source: NativeKotlinCustomAuthoringSourceFileStore,
    private val drafts: NativeCustomCycleDraftFileStore,
    private val fence: NativeKotlinApprovalRevisionFence,
    private val identity: NativeKotlinCurrentIdentity
) {
    fun saveVerifiedExerciseEdit(
        ownerKey: String,
        draft: CustomTrainingCycle,
        expectedDraftRevision: Long,
        expectedSourceRevision: Long
    ): NativeCustomDraftWriteResult {
        if(expectedSourceRevision !in 1..9_007_199_254_740_991L)
            return NativeCustomDraftWriteResult.Rejected(
                NativeCustomDraftRefusal.STALE_REVISION)
        return runCatching {
            fence.withOwnerTransaction(ownerKey) {
                val owner=identity.signedInIdentity()
                if(owner==null || owner.ownerKey!=ownerKey ||
                    owner.athleteId!=draft.athleteId)
                    return@withOwnerTransaction NativeCustomDraftWriteResult.Rejected(
                        NativeCustomDraftRefusal.NOT_AUTHORIZED)
                val snapshot=source.read(ownerKey)
                if(snapshot !is NativeAuthoringSourceRead.Found ||
                    snapshot.source.revision!=expectedSourceRevision ||
                    snapshot.source.athleteId!=owner.athleteId)
                    return@withOwnerTransaction NativeCustomDraftWriteResult.Rejected(
                        if(snapshot is NativeAuthoringSourceRead.Rejected &&
                            snapshot.reason==NativeAuthoringSourceRefusal.CORRUPT_RECORD)
                            NativeCustomDraftRefusal.CORRUPT_RECORD
                        else NativeCustomDraftRefusal.STALE_REVISION)
                // The source cannot change after this check while this owner
                // file lock remains held, including between JVM instances.
                if(identity.signedInIdentity()!=owner)
                    return@withOwnerTransaction NativeCustomDraftWriteResult.Rejected(
                        NativeCustomDraftRefusal.NOT_AUTHORIZED)
                drafts.save(ownerKey,draft.cycleId,expectedDraftRevision,draft)
            }
        }.getOrElse {
            NativeCustomDraftWriteResult.Rejected(
                NativeCustomDraftRefusal.STORAGE_UNAVAILABLE)
        }
    }
}
