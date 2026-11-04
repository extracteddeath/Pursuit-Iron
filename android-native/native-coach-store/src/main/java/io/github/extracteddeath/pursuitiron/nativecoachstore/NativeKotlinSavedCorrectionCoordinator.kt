package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*

/**
 * Isolated owner-transaction port for saved-history corrections.
 *
 * Reuses the very same account/program revision fence and authenticated
 * issued-workout authority as Kotlin issuance, journaling and sign-out.
 * It is NOT connected to the production JS-backed Compose workout.
 *
 * IMPORTANT: the app's future global history revision, Last/PR cache and
 * next-session progression snapshots must join a larger atomic transaction
 * before a saved correction may be surfaced as a complete product edit.
 */
class NativeKotlinSavedCorrectionCoordinator(
    private val store: KotlinNativeSavedCorrectionFileStore,
    private val authority: NativeKotlinWorkoutAuthority,
    private val revisionFence: NativeKotlinApprovalRevisionFence
) {
    fun open(ownerKey: String, workoutId: String): NativeSavedCorrectionFileResult =
        runCatching {
            revisionFence.withOwnerTransaction(ownerKey) {
                store.openStored(ownerKey, workoutId) { owner, source ->
                    runCatching {
                        authority.canAccessIssuedWorkout(owner, source)
                    }.getOrDefault(false)
                }
            }
        }.getOrElse {
            NativeSavedCorrectionFileResult.Rejected(
                NativeSavedCorrectionRefusal.STORAGE_UNAVAILABLE)
        }

    fun correct(
        ownerKey: String,
        workoutId: String,
        operation: KotlinSavedSetCorrection
    ): NativeSavedCorrectionFileResult =
        runCatching {
            revisionFence.withOwnerTransaction(ownerKey) {
                store.appendStored(ownerKey, workoutId, operation) { owner, source ->
                    runCatching {
                        authority.canAccessIssuedWorkout(owner, source)
                    }.getOrDefault(false)
                }
            }
        }.getOrElse {
            NativeSavedCorrectionFileResult.Rejected(
                NativeSavedCorrectionRefusal.STORAGE_UNAVAILABLE)
        }
}
