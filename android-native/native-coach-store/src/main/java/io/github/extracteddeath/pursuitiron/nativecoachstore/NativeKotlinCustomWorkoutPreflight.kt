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

/** A recovered transition is not the original cycle's ordinary signoff.
 * Keep its preflight type separate so existing preparation/issue entry
 * points cannot accept it as a standard signed workout.
 */
internal sealed interface NativeFirstTierTransitionPreflight {
    data class Reviewed(val review:NativeSavedNamedReview.Signed,
        val session:CustomWorkoutDaySnapshot,val receiptFingerprint:String):NativeFirstTierTransitionPreflight {
        val mayIssueWorkout:Boolean get()=false
    }
    data class Rejected(val reason:NativeCustomPreflightRefusal):NativeFirstTierTransitionPreflight
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
    fun inspectNamed(
        ownerKey:String,workoutId:String,cycleId:String,absoluteWeek:Int,weekday:Int,
        inputs:CustomCoachAuditInputs,history:NativeKotlinMixedHistoryReadCoordinator,
        outstanding:NativeKotlinCustomOutstandingWorkoutInventory
    ):NativeCustomPreflightResult =
        inspectSpecialist(ownerKey,workoutId,cycleId,absoluteWeek,weekday,inputs,history,outstanding,false)

    fun inspectTieredInitial(
        ownerKey:String,workoutId:String,cycleId:String,absoluteWeek:Int,weekday:Int,
        inputs:CustomCoachAuditInputs,history:NativeKotlinMixedHistoryReadCoordinator,
        outstanding:NativeKotlinCustomOutstandingWorkoutInventory
    ):NativeCustomPreflightResult =
        inspectSpecialist(ownerKey,workoutId,cycleId,absoluteWeek,weekday,inputs,history,outstanding,true)

    /** Read-only authored supporting day. Recover the original signed rules;
     * callers cannot supply a replacement load, policy or progression state.
     * This is not preparation or issuance authority. */
    fun inspectTieredSupport(ownerKey:String,workoutId:String,cycleId:String,
        absoluteWeek:Int,weekday:Int,history:NativeKotlinMixedHistoryReadCoordinator,
        outstanding:NativeKotlinCustomOutstandingWorkoutInventory):NativeCustomPreflightResult {
        val inputs=consents.signedTieredInputs(ownerKey,cycleId)
            ?:return NativeCustomPreflightResult.Rejected(NativeCustomPreflightRefusal.CONSENT_NOT_CURRENT)
        return inspectSpecialist(ownerKey,workoutId,cycleId,absoluteWeek,weekday,
            inputs,history,outstanding,true,supportingDay=true)
    }

    /** Exact next-session preview from persisted first-transition consent.
     * Requires the original owner, workout ID, week and day, and audits all
     * slots against current physical stock. No preparation or journal write.
     */
    internal fun inspectFirstTierTransition(
        ownerKey:String,workoutId:String,cycleId:String,absoluteWeek:Int,weekday:Int,
        transitionConsent:NativeKotlinFirstTierTransitionConsentStore
    ):NativeFirstTierTransitionPreflight {
        fun deny(reason:NativeCustomPreflightRefusal)=NativeFirstTierTransitionPreflight.Rejected(reason)
        if(!id.matches(ownerKey)||!id.matches(workoutId)||!id.matches(cycleId)||
            absoluteWeek !in 1..104 || weekday !in 0..6)
            return deny(NativeCustomPreflightRefusal.INVALID_SELECTION)
        return runCatching{fence.withOwnerTransaction(ownerKey){
            val owner=identity.signedInIdentity()?.takeIf{it.ownerKey==ownerKey}
                ?:return@withOwnerTransaction deny(NativeCustomPreflightRefusal.NOT_AUTHORIZED)
            val recovered=transitionConsent.recover(ownerKey,cycleId,absoluteWeek)
                as? NativeTieredTransitionRecovery.Ready
                ?:return@withOwnerTransaction deny(NativeCustomPreflightRefusal.CONSENT_NOT_CURRENT)
            val signed=recovered.review
            val next=signed.nextTierCandidate
                ?:return@withOwnerTransaction deny(NativeCustomPreflightRefusal.CONSENT_NOT_CURRENT)
            val initial=signed.initialTierEvidence
                ?:return@withOwnerTransaction deny(NativeCustomPreflightRefusal.CONSENT_NOT_CURRENT)
            val key=initial.key.copy(weekInBlock=initial.key.weekInBlock+next.priorTierTransitions.size+1)
            if(next.selection.nextWorkoutId!=workoutId || next.selection.absoluteWeek!=absoluteWeek ||
                key.weekday!=weekday || signed.consent.cycleId!=cycleId ||
                signed.consent.ownerKey!=ownerKey || signed.consent.athleteId!=owner.athleteId)
                return@withOwnerTransaction deny(NativeCustomPreflightRefusal.INVALID_SELECTION)
            val source=(sources.read(ownerKey) as? NativeAuthoringSourceRead.Found)?.source
                ?:return@withOwnerTransaction deny(NativeCustomPreflightRefusal.NO_REVIEWED_GYM)
            if(source.ownerKey!=ownerKey || source.athleteId!=owner.athleteId ||
                source.revision!=signed.stamp.gymCatalogSourceRevision)
                return@withOwnerTransaction deny(NativeCustomPreflightRefusal.CONSENT_NOT_CURRENT)
            val cycle=next.draft.proposal.draft
            val preview=CustomWorkoutSnapshotDomain.reviewSpecialistForConsent(cycle,
                source.setup,source.catalog,source.revision,signed.inputs,signed.evidence,
                listOf(initial),absoluteWeek,weekday,
                next.priorTierTransitions+CustomTieredTransitionWeekEvidence(key,next.request))
                as? CustomWorkoutSnapshotResult.Preview
                ?:return@withOwnerTransaction deny(NativeCustomPreflightRefusal.COACH_AUDIT_FAILED)
            val snapshot=preview.snapshot
            if(snapshot.stamp!=signed.stamp.copy(cycleRevision=cycle.revision) ||
                snapshot.blockId!=key.blockId || snapshot.weekInBlock!=key.weekInBlock ||
                snapshot.slots.none{it.slotId==key.slotId})
                return@withOwnerTransaction deny(NativeCustomPreflightRefusal.CONSENT_NOT_CURRENT)
            val roster=if(machineRosters==null)null else
                (machineRosters.read(ownerKey) as? NativeMachineRosterResult.Saved)?.snapshot
                    ?:return@withOwnerTransaction deny(NativeCustomPreflightRefusal.MACHINE_BINDING_NOT_VERIFIED)
            if(!verifiedSpecialistStock(ownerKey,snapshot,source,roster))
                return@withOwnerTransaction deny(NativeCustomPreflightRefusal.MACHINE_BINDING_NOT_VERIFIED)
            if(identity.signedInIdentity()!=owner)
                return@withOwnerTransaction deny(NativeCustomPreflightRefusal.NOT_AUTHORIZED)
            NativeFirstTierTransitionPreflight.Reviewed(signed,snapshot,recovered.receiptFingerprint)
        }}.getOrElse{deny(NativeCustomPreflightRefusal.STORAGE_UNAVAILABLE)}
    }

    private fun inspectSpecialist(
        ownerKey:String,workoutId:String,cycleId:String,absoluteWeek:Int,weekday:Int,
        inputs:CustomCoachAuditInputs,history:NativeKotlinMixedHistoryReadCoordinator,
        outstanding:NativeKotlinCustomOutstandingWorkoutInventory,tiered:Boolean,
        supportingDay:Boolean=false
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
            val signed=(if(supportingDay)reviewer.reviewSignedTieredSupport(ownerKey,cycleId,inputs)
                else if(tiered)reviewer.reviewSignedTieredInitial(ownerKey,cycleId,inputs)
                else reviewer.reviewSigned(ownerKey,cycleId,inputs)) as? NativeSavedNamedReview.Signed
                ?:return@withOwnerTransaction deny(NativeCustomPreflightRefusal.CONSENT_NOT_CURRENT)
            if(tiered) {
                val selection=signed.initialTierReceipt?.selection
                    ?:return@withOwnerTransaction deny(NativeCustomPreflightRefusal.CONSENT_NOT_CURRENT)
                val receipt=checkNotNull(signed.initialTierReceipt)
                val valid=if(supportingDay)
                    workoutId!=selection.nextWorkoutId &&
                        NativeCustomNextCycleSlotDomain.isTieredSupportDay(signed.draft,receipt,absoluteWeek,weekday)
                    else selection.nextWorkoutId==workoutId && selection.absoluteWeek==absoluteWeek &&
                        selection.weekday==weekday
                if(!valid || selection.cycleId!=cycleId || signed.initialTierEvidence==null)
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
            if(supportingDay) {
                val initial=checkNotNull(signed.initialTierReceipt)
                val facts=census.sessions.filter{it.cycleId==cycleId}.map{item->
                    val origin=outstanding.preparedOrigin(ownerKey,item.workoutId)
                        ?:return@withOwnerTransaction deny(NativeCustomPreflightRefusal.SESSION_UNAVAILABLE)
                    if(origin.authority==NativeCustomPreparationAuthority.TIERED_INITIAL ||
                        origin.authority==NativeCustomPreparationAuthority.TIERED_SUPPORT) {
                        if(origin.consentRevision!=signed.consent.recordRevision ||
                            origin.consentFingerprint!=signed.consent.fingerprintSha256)
                            return@withOwnerTransaction deny(NativeCustomPreflightRefusal.CONSENT_NOT_CURRENT)
                    }
                    NativeCustomNextCycleSlotDomain.tieredFact(signed.draft,initial,item,origin,true)
                        ?:return@withOwnerTransaction deny(NativeCustomPreflightRefusal.SESSION_UNAVAILABLE)
                }
                val next=NativeCustomNextCycleSlotDomain.select(signed.draft,facts)
                val allowed=when(next) {
                    is NativeCustomNextCycleSlot.Next -> next.absoluteWeek==absoluteWeek && next.weekday==weekday
                    is NativeCustomNextCycleSlot.Prepared -> next.workoutId==workoutId &&
                        next.absoluteWeek==absoluteWeek && next.weekday==weekday
                    else -> false
                }
                if(!allowed)return@withOwnerTransaction deny(NativeCustomPreflightRefusal.SESSION_UNAVAILABLE)
            }
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
            if(preview.snapshot.stamp!=signed.stamp ||
                (supportingDay && preview.snapshot.slots.any{slot->slot.sets.any{it.prescription.percentageIntent!=null}}))
                return@withOwnerTransaction deny(NativeCustomPreflightRefusal.CONSENT_NOT_CURRENT)
            val roster=if(machineRosters==null)null else
                (machineRosters.read(ownerKey) as? NativeMachineRosterResult.Saved)?.snapshot
                    ?:return@withOwnerTransaction deny(NativeCustomPreflightRefusal.MACHINE_BINDING_NOT_VERIFIED)
            if(!verifiedSpecialistStock(ownerKey,preview.snapshot,source,roster))
                return@withOwnerTransaction deny(NativeCustomPreflightRefusal.MACHINE_BINDING_NOT_VERIFIED)
            if(identity.signedInIdentity()!=owner)
                return@withOwnerTransaction deny(NativeCustomPreflightRefusal.NOT_AUTHORIZED)
            NativeCustomPreflightResult.ReviewedPreview(signed.consent,preview.snapshot)
        }}.getOrElse{deny(NativeCustomPreflightRefusal.STORAGE_UNAVAILABLE)}
    }

    private fun verifiedSpecialistStock(ownerKey:String,snapshot:CustomWorkoutDaySnapshot,
        source:NativeCustomAuthoringSnapshot,roster:NativeMachineRosterSnapshot?):Boolean {
        if(NativeKotlinCustomMachineBindingPolicy.review(ownerKey,snapshot,source.catalog,roster)
            !is NativeCustomMachineBindingReview.Verified)return false
        for(slot in snapshot.slots) {
            val physical=NativeKotlinCustomSelectedStockDomain.resolve(source,slot,roster)
                ?:return false
            if(physical.machineInstanceId!=slot.machineInstanceId)return false
            val loads=slot.sets.flatMap{set->listOfNotNull(
                set.prescription.load as? DraftSetLoad.Verified,
                set.prescription.amplifier?.followUpLoad as? DraftSetLoad.Verified,
                set.prescription.amplifier?.secondDropLoad as? DraftSetLoad.Verified)}
            if(loads.any{load->
                physical.stock?.let{stock->
                    (EquipmentLoadDomain.inspect(stock,load.value,load.unit) as? LoadInspection.Confirmed)
                        ?.let{it.exact && it.basis==load.basis}
                }!=true
            })return false
        }
        return true
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
