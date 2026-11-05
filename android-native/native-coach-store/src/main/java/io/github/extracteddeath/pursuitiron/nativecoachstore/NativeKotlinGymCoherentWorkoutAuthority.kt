package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.KotlinTrainingSourceRevision

/**
 * Strict source-aware issuance authority for the ISOLATED Compose candidate.
 *
 * A prior already-issued workout can still be resumed/finished after the gym
 * is edited, preserving its historical targets. NEW issuance instead requires
 * the CURRENT native authoring source to agree with the full approved plan.
 *
 * Both persistent reads run within the issuer's owner revision fence. Without
 * the same fence for gym edits this would be a time-of-check/time-of-use hole.
 */
class NativeKotlinGymCoherentWorkoutAuthority(
    private val approvals: NativeKotlinApprovalFileAuthority,
    private val authoring: NativeKotlinCustomAuthoringSourceFileStore,
    private val fence: NativeKotlinApprovalRevisionFence
) : NativeKotlinWorkoutAuthority {
    override fun currentApprovedWorkout(
        ownerKey: String, workoutId: String
    ): NativeKotlinWorkoutApproval? = runCatching {
        fence.withOwnerTransaction(ownerKey) {
            val current=approvals.currentApprovedWorkout(ownerKey,workoutId)
                ?:return@withOwnerTransaction null
            val nativeSource=(authoring.read(ownerKey)
                as? NativeAuthoringSourceRead.Found)?.source
                ?:return@withOwnerTransaction null
            if(NativeKotlinApprovedGymCoherenceDomain.inspect(current,nativeSource)
                !=NativeApprovedGymCoherenceResult.Matched)
                return@withOwnerTransaction null
            current
        }
    }.getOrNull()

    override fun canAccessIssuedWorkout(
        ownerKey: String, source: KotlinTrainingSourceRevision
    ): Boolean=approvals.canAccessIssuedWorkout(ownerKey,source)
}
