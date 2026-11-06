package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.math.BigDecimal

/**
 * Explicit native athlete action after displaying the complete reviewed
 * program and its affected days. These are EXPECTATIONS, not authority.
 * A caller-supplied accepted flag alone never proves an authorized plan.
 *
 * The owner-fenced coordinator must re-read real approval and EVERY saved
 * completion/correction, independently recompute the proposal, check each
 * expectation and publish only a fresh, revisioned Kotlin-owned grant.
 */
data class NativeKotlinCoachLoadAcceptanceIntent(
    val exerciseId: String,
    val expectedApprovalRevision: Long,
    val expectedHistoryRevision: Long,
    val expectedComparedWorkoutIds: List<String>,
    val expectedKind: CoachNextLoadChangeKind,
    val expectedOriginalLoad: BigDecimal,
    val expectedCandidateLoad: BigDecimal,
    val expectedAffectedDayIndices: List<Int>,
    val newWorkoutId: String,
    val newGrantId: String,
    val newStorageReceiptId: String,
    val newDayIndex: Int,
    /** The UI must set only on a user-triggered explicit acceptance. */
    val explicitlyAccepted: Boolean
)

enum class NativeKotlinCoachAcceptanceRefusal {
    INVALID_SCOPE, NOT_AUTHORIZED, ASSISTANCE_DISABLED, NOT_EXPLICITLY_ACCEPTED,
    NO_CURRENT_APPROVAL, APPROVAL_CHANGED, HISTORY_UNAVAILABLE,
    HISTORY_CHANGED, NO_VERIFIED_COACHING_CHANGE, PROPOSAL_CHANGED,
    IDENTIFIER_ALREADY_USED, NEW_WORKOUT_UNRESOLVED,
    GYM_SOURCE_UNAVAILABLE, GYM_SOURCE_CHANGED,
    APPROVAL_STORE_REJECTED, STORAGE_UNAVAILABLE
}

sealed interface NativeKotlinCoachAcceptanceResult {
    data class Committed(
        val approvedSource: KotlinTrainingSourceRevision,
        val approvedWorkoutId: String,
        val storageRevision: Long,
        val historyRevisionReviewed: Long
    ) : NativeKotlinCoachAcceptanceResult {
        /** Approval is not issuance or workout completion. */
        val workoutIssued: Boolean get() = false
        val productionCutover: Boolean get() = false
    }
    data class Rejected(val reason: NativeKotlinCoachAcceptanceRefusal) :
        NativeKotlinCoachAcceptanceResult
}
