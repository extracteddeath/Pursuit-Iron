package io.github.extracteddeath.pursuitiron

import io.github.extracteddeath.pursuitiron.nativecoachstore.NativeKotlinMixedHistoryReadResult
import io.github.extracteddeath.pursuitiron.nativecoachstore.NativeKotlinMixedHistoryRefusal
import io.github.extracteddeath.pursuitiron.nativecoachstore.NativeKotlinOwnerIdentity

/**
 * The opt-in separate-History dialog is a consent to enroll ONE exact
 * independently verified local athlete, not whoever happens to be active at
 * the later confirmation tap.
 *
 * Capturing a current owner requires a fresh NOT_ENROLLED result bracketed by
 * the same signed identity. The host rechecks this exact identity again
 * inside the enrollment coordinator's already-fenced transaction.
 *
 * Pure validation only: no history, login or enrollment authority.
 */
internal object NativeKotlinHistoryEnrollmentConsentPolicy {
    fun capture(
        ownerBefore:NativeKotlinOwnerIdentity?,
        read:NativeKotlinMixedHistoryReadResult?,
        ownerAfter:NativeKotlinOwnerIdentity?
    ):NativeKotlinOwnerIdentity? =
        ownerBefore?.takeIf { expected ->
            expected==ownerAfter &&
                (read as? NativeKotlinMixedHistoryReadResult.Rejected)
                    ?.reason==NativeKotlinMixedHistoryRefusal.NOT_ENROLLED
        }

    fun matches(
        expectedOwner:NativeKotlinOwnerIdentity?,
        currentOwner:NativeKotlinOwnerIdentity?
    ):Boolean = expectedOwner!=null && expectedOwner==currentOwner
}
