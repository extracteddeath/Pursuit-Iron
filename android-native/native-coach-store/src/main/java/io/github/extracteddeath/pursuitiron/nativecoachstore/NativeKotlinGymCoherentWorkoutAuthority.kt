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
    private val authoring: NativeKotlinCustomAuthoringSourceFileStore
) : NativeKotlinWorkoutAuthority {
    override fun currentApprovedWorkout(
        ownerKey: String, workoutId: String
    ): NativeKotlinWorkoutApproval? {
        val current=approvals.currentApprovedWorkout(ownerKey,workoutId)
            ?:return null
        val nativeSource=(authoring.read(ownerKey)
            as? NativeAuthoringSourceRead.Found)?.source
            ?:return null
        if(NativeKotlinApprovedGymCoherenceDomain.inspect(current,nativeSource)
            !=NativeApprovedGymCoherenceResult.Matched)
            return null
        return current
    }

    override fun canAccessIssuedWorkout(
        ownerKey: String, source: KotlinTrainingSourceRevision
    ): Boolean=approvals.canAccessIssuedWorkout(ownerKey,source)
}
