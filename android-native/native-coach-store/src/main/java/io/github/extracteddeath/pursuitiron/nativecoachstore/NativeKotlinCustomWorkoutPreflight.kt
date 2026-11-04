package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*

enum class NativeCustomPreflightRefusal {
    NOT_AUTHORIZED, INVALID_SELECTION, NO_DURABLE_CYCLE,
    NO_REVIEWED_GYM, CONSENT_NOT_CURRENT, COACH_AUDIT_FAILED,
    SESSION_UNAVAILABLE, STORAGE_UNAVAILABLE
}
sealed interface NativeCustomPreflightResult {
    /**
     * Owner-verified, fully reviewed session projection. Not issued: no
     * workout grant, no journal event, no history mutation. The future
     * custom-workout issuer must recheck and atomically persist its own
     * session using this same OS fence and a verified history migration.
     */
    data class ReviewedPreview(
        val receipt: NativeKotlinCustomCycleSignoff,
        val session: CustomWorkoutDaySnapshot
    ): NativeCustomPreflightResult {
        val isIssued:Boolean get() = false
        val mayLog:Boolean get() = false
    }
    data class Rejected(val reason:NativeCustomPreflightRefusal):
        NativeCustomPreflightResult
}

/**
 * Real native, owner-fenced preflight for custom week/day selection. It does
 * not run through the lossy ComposerRequest snapshot. A caller's displayed
 * 'approved' flag is ignored: full policy+cycle+gym fingerprint is recomputed
 * on every preview from app-private persisted sources and current identity.
 */
class NativeKotlinCustomWorkoutPreflight(
    private val fence:NativeKotlinApprovalRevisionFence,
    private val identity:NativeKotlinCurrentIdentity,
    private val drafts:NativeCustomCycleDraftFileStore,
    private val sources:NativeKotlinCustomAuthoringSourceFileStore,
    private val consents:NativeKotlinCustomCycleSignoffFileStore
) {
    private val id=Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,79}$")
    fun inspect(
        ownerKey:String,
        cycleId:String,
        absoluteWeek:Int,
        weekday:Int,
        coachingInputs:CustomCoachAuditInputs
    ):NativeCustomPreflightResult {
        if(!id.matches(ownerKey) || !id.matches(cycleId) ||
            absoluteWeek !in 1..104 || weekday !in 0..6)
            return NativeCustomPreflightResult.Rejected(
                NativeCustomPreflightRefusal.INVALID_SELECTION)
        return runCatching {
            fence.withOwnerTransaction(ownerKey) {
                val owner=identity.signedInIdentity()?.takeIf {
                    it.ownerKey==ownerKey
                } ?:return@withOwnerTransaction NativeCustomPreflightResult.Rejected(
                    NativeCustomPreflightRefusal.NOT_AUTHORIZED)
                val consent=consents.read(ownerKey,cycleId,coachingInputs)
                val current=(consent as? NativeCustomSignoffRead.Found)
                    ?.takeIf{it.isCurrent}?.receipt
                    ?:return@withOwnerTransaction NativeCustomPreflightResult.Rejected(
                        NativeCustomPreflightRefusal.CONSENT_NOT_CURRENT)
                val draft=(drafts.read(ownerKey,cycleId)
                    as? NativeCustomDraftReadResult.Found)?.draft
                    ?:return@withOwnerTransaction NativeCustomPreflightResult.Rejected(
                        NativeCustomPreflightRefusal.NO_DURABLE_CYCLE)
                val source=(sources.read(ownerKey)
                    as? NativeAuthoringSourceRead.Found)?.source
                    ?:return@withOwnerTransaction NativeCustomPreflightResult.Rejected(
                        NativeCustomPreflightRefusal.NO_REVIEWED_GYM)
                if(draft.athleteId!=owner.athleteId ||
                    source.athleteId!=owner.athleteId ||
                    current.athleteId!=owner.athleteId ||
                    current.cycleRevision!=draft.revision ||
                    current.sourceRevision!=source.revision)
                    return@withOwnerTransaction NativeCustomPreflightResult.Rejected(
                        NativeCustomPreflightRefusal.CONSENT_NOT_CURRENT)
                val result=CustomWorkoutSnapshotDomain.reviewForConsent(
                    draft,source.setup,source.catalog,source.revision,
                    coachingInputs,absoluteWeek,weekday)
                if(identity.signedInIdentity()!=owner)
                    return@withOwnerTransaction NativeCustomPreflightResult.Rejected(
                        NativeCustomPreflightRefusal.NOT_AUTHORIZED)
                when(result) {
                    is CustomWorkoutSnapshotResult.Preview ->
                        NativeCustomPreflightResult.ReviewedPreview(
                            current,result.snapshot)
                    is CustomWorkoutSnapshotResult.Rejected ->
                        NativeCustomPreflightResult.Rejected(
                            if(result.reason==CustomWorkoutSnapshotRefusal.COACHING_REVIEW_REQUIRED)
                                NativeCustomPreflightRefusal.COACH_AUDIT_FAILED
                            else NativeCustomPreflightRefusal.SESSION_UNAVAILABLE)
                }
            }
        }.getOrElse {
            NativeCustomPreflightResult.Rejected(
                NativeCustomPreflightRefusal.STORAGE_UNAVAILABLE)
        }
    }
}
