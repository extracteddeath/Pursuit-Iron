package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*

enum class NativeCustomPreflightRefusal {
    NOT_AUTHORIZED, INVALID_SELECTION, NO_DURABLE_CYCLE,
    NO_REVIEWED_GYM, CONSENT_NOT_CURRENT, COACH_AUDIT_FAILED,
    SESSION_UNAVAILABLE, MACHINE_BINDING_NOT_VERIFIED, STORAGE_UNAVAILABLE
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
 * Authenticated consent review of the shorter-session calculation.
 * This is a read-only proposal, NOT a changed prepared custom workout or
 * signed shorter-session grant. No journal or future program can be mutated.
 */
sealed interface NativeCustomShortSessionPreflight {
    data class Reviewed(
        val receipt:NativeKotlinCustomCycleSignoff,
        val absoluteWeek:Int,
        val weekday:Int,
        val review:CustomShortSessionReview
    ):NativeCustomShortSessionPreflight {
        val mayIssue:Boolean get()=false
        val maySave:Boolean get()=false
    }
    data class Rejected(val reason:NativeCustomPreflightRefusal):
        NativeCustomShortSessionPreflight
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
    private val consents:NativeKotlinCustomCycleSignoffFileStore,
    private val machineRosters:NativeKotlinMachineRosterFileStore?=null
) {
    private val id=Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,79}$")
    /**
     * The SAME owner-fenced, source-revalidated athlete signoff must be
     * reviewed before calculating optional cuts. Never trust a UI-supplied
     * CustomWorkoutDaySnapshot or a previous preview as current approval.
     */
    fun reviewShorter(
        ownerKey:String,cycleId:String,
        absoluteWeek:Int,weekday:Int,
        coachingInputs:CustomCoachAuditInputs,
        minutes:Int
    ):NativeCustomShortSessionPreflight =
        when(val approved=inspect(ownerKey,cycleId,
            absoluteWeek,weekday,coachingInputs)) {
            is NativeCustomPreflightResult.Rejected ->
                NativeCustomShortSessionPreflight.Rejected(approved.reason)
            is NativeCustomPreflightResult.ReviewedPreview ->
                NativeCustomShortSessionPreflight.Reviewed(
                    approved.receipt,approved.session.absoluteWeek,
                    approved.session.weekday,
                    CustomShorterSessionReviewDomain.inspect(
                        approved.session,minutes)
                )
        }

    /** Named consent has its own authenticated projection, never a bypass
     * of ordinary preflight. The same exact selection is checked again at
     * first issuance; a completed day cannot be prepared under a new ID. */
    internal fun inspectNamed(
        ownerKey:String,workoutId:String,cycleId:String,absoluteWeek:Int,weekday:Int,
        inputs:CustomCoachAuditInputs,history:NativeKotlinMixedHistoryReadCoordinator,
        outstanding:NativeKotlinCustomOutstandingWorkoutInventory
    ):NativeCustomPreflightResult =
        inspectSpecialist(ownerKey,workoutId,cycleId,absoluteWeek,weekday,inputs,history,outstanding,false)

    internal fun inspectTieredInitial(
        ownerKey:String,workoutId:String,cycleId:String,absoluteWeek:Int,weekday:Int,
        inputs:CustomCoachAuditInputs,history:NativeKotlinMixedHistoryReadCoordinator,
        outstanding:NativeKotlinCustomOutstandingWorkoutInventory
    ):NativeCustomPreflightResult =
        inspectSpecialist(ownerKey,workoutId,cycleId,absoluteWeek,weekday,inputs,history,outstanding,true)

    private fun inspectSpecialist(
        ownerKey:String,workoutId:String,cycleId:String,absoluteWeek:Int,weekday:Int,
        inputs:CustomCoachAuditInputs,history:NativeKotlinMixedHistoryReadCoordinator,
        outstanding:NativeKotlinCustomOutstandingWorkoutInventory,tiered:Boolean
    ):NativeCustomPreflightResult {
        fun deny(reason:NativeCustomPreflightRefusal)=NativeCustomPreflightResult.Rejected(reason)
        if(!id.matches(ownerKey)||!id.matches(workoutId)||!id.matches(cycleId)||
            absoluteWeek !in 1..104 || weekday !in 0..6)
            return deny(NativeCustomPreflightRefusal.INVALID_SELECTION)
        return runCatching{fence.withOwnerTransaction(ownerKey){
            val owner=identity.signedInIdentity()?.takeIf{it.ownerKey==ownerKey}
                ?:return@withOwnerTransaction deny(NativeCustomPreflightRefusal.NOT_AUTHORIZED)
            val reviewer=NativeKotlinSavedNamedPercentageReview(fence,identity,drafts,sources,
                consents,history,outstanding,machineRosters)
            val signed=(if(tiered)reviewer.reviewSignedTieredInitial(ownerKey,cycleId,inputs)
                else reviewer.reviewSigned(ownerKey,cycleId,inputs)) as? NativeSavedNamedReview.Signed
                ?:return@withOwnerTransaction deny(NativeCustomPreflightRefusal.CONSENT_NOT_CURRENT)
            if(tiered) {
                val selection=signed.initialTierReceipt?.selection
                    ?:return@withOwnerTransaction deny(NativeCustomPreflightRefusal.CONSENT_NOT_CURRENT)
                if(selection.nextWorkoutId!=workoutId || selection.absoluteWeek!=absoluteWeek ||
                    selection.weekday!=weekday || selection.cycleId!=cycleId ||
                    signed.initialTierEvidence==null)
                    return@withOwnerTransaction deny(NativeCustomPreflightRefusal.INVALID_SELECTION)
            }
            val timeline=(history.read(ownerKey,owner.athleteId)
                as? NativeKotlinMixedHistoryReadResult.Review)?.timeline
                ?:return@withOwnerTransaction deny(NativeCustomPreflightRefusal.CONSENT_NOT_CURRENT)
            if(timeline.manifestRevision!=signed.historyRevision)
                return@withOwnerTransaction deny(NativeCustomPreflightRefusal.CONSENT_NOT_CURRENT)
            val completed=timeline.completions.filterIsInstance<NativeKotlinMixedHistoryCompletion.Custom>()
                .map{it.manifest.workoutId}.toSet()
            val census=(outstanding.inspect(ownerKey,owner.athleteId,completed)
                as? NativeCustomInventoryReview.Reviewed)
                ?:return@withOwnerTransaction deny(NativeCustomPreflightRefusal.SESSION_UNAVAILABLE)
            if(census.sessions.any{session->
                if(session.workoutId==workoutId)
                    session.issued || session.completed || session.cycleId!=cycleId ||
                        session.absoluteWeek!=absoluteWeek || session.weekday!=weekday
                else !session.completed || (session.cycleId==cycleId &&
                    session.absoluteWeek==absoluteWeek && session.weekday==weekday)
            })return@withOwnerTransaction deny(NativeCustomPreflightRefusal.SESSION_UNAVAILABLE)
            val source=(sources.read(ownerKey) as? NativeAuthoringSourceRead.Found)?.source
                ?:return@withOwnerTransaction deny(NativeCustomPreflightRefusal.NO_REVIEWED_GYM)
            if(source.ownerKey!=ownerKey || source.athleteId!=owner.athleteId ||
                source.revision!=signed.stamp.gymCatalogSourceRevision)
                return@withOwnerTransaction deny(NativeCustomPreflightRefusal.CONSENT_NOT_CURRENT)
            val preview=CustomWorkoutSnapshotDomain.reviewSpecialistForConsent(signed.draft,
                source.setup,source.catalog,source.revision,inputs,signed.evidence,
                listOfNotNull(signed.initialTierEvidence),absoluteWeek,weekday)
                as? CustomWorkoutSnapshotResult.Preview
                ?:return@withOwnerTransaction deny(NativeCustomPreflightRefusal.COACH_AUDIT_FAILED)
            if(preview.snapshot.stamp!=signed.stamp)
                return@withOwnerTransaction deny(NativeCustomPreflightRefusal.CONSENT_NOT_CURRENT)
            val roster=if(machineRosters==null)null else
                (machineRosters.read(ownerKey) as? NativeMachineRosterResult.Saved)?.snapshot
                    ?:return@withOwnerTransaction deny(NativeCustomPreflightRefusal.MACHINE_BINDING_NOT_VERIFIED)
            if(NativeKotlinCustomMachineBindingPolicy.review(ownerKey,preview.snapshot,source.catalog,roster)
                !is NativeCustomMachineBindingReview.Verified)
                return@withOwnerTransaction deny(NativeCustomPreflightRefusal.MACHINE_BINDING_NOT_VERIFIED)
            // All selected slots, even nonnamed ones, must use exact physical
            // stock. Never borrow a roster default without explicit binding.
            for(slot in preview.snapshot.slots) {
                val physical=NativeKotlinCustomSelectedStockDomain.resolve(source,slot,roster)
                    ?:return@withOwnerTransaction deny(NativeCustomPreflightRefusal.MACHINE_BINDING_NOT_VERIFIED)
                if(physical.machineInstanceId!=slot.machineInstanceId)
                    return@withOwnerTransaction deny(NativeCustomPreflightRefusal.MACHINE_BINDING_NOT_VERIFIED)
                val loads=slot.sets.flatMap{set->listOfNotNull(
                    set.prescription.load as? DraftSetLoad.Verified,
                    set.prescription.amplifier?.followUpLoad as? DraftSetLoad.Verified,
                    set.prescription.amplifier?.secondDropLoad as? DraftSetLoad.Verified)}
                if(loads.any{load->
                    physical.stock?.let{stock->
                        (EquipmentLoadDomain.inspect(stock,load.value,load.unit) as? LoadInspection.Confirmed)
                            ?.let{it.exact && it.basis==load.basis}
                    }!=true
                })return@withOwnerTransaction deny(NativeCustomPreflightRefusal.MACHINE_BINDING_NOT_VERIFIED)
            }
            if(identity.signedInIdentity()!=owner)
                return@withOwnerTransaction deny(NativeCustomPreflightRefusal.NOT_AUTHORIZED)
            NativeCustomPreflightResult.ReviewedPreview(signed.consent,preview.snapshot)
        }}.getOrElse{deny(NativeCustomPreflightRefusal.STORAGE_UNAVAILABLE)}
    }

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
                    is CustomWorkoutSnapshotResult.Preview -> {
                        val roster=if(result.snapshot.slots.any {
                            it.machineInstanceId!=null
                        }) (machineRosters?.read(ownerKey)
                            as? NativeMachineRosterResult.Saved)?.snapshot
                        else null
                        when(NativeKotlinCustomMachineBindingPolicy.review(
                            ownerKey,result.snapshot,source.catalog,roster)) {
                            is NativeCustomMachineBindingReview.Rejected ->
                                NativeCustomPreflightResult.Rejected(
                                    NativeCustomPreflightRefusal.MACHINE_BINDING_NOT_VERIFIED)
                            is NativeCustomMachineBindingReview.Verified ->
                                NativeCustomPreflightResult.ReviewedPreview(
                                    current,result.snapshot)
                        }
                    }
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
