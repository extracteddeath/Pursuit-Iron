package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*

enum class NativeSavedNamedReviewRefusal {
    NOT_AUTHORIZED, NO_CONFIRMED_DRAFT, STALE_PRIOR_CONSENT,
    SOURCE_CHANGED, HISTORY_CHANGED, PROTECTED_WEEK, STOCK_UNVERIFIED,
    FULL_CYCLE_AUDIT_FAILED, STORAGE_UNAVAILABLE
}

/** One proposed next session on the same authenticated tier thread.
 * No caller-supplied reference, increment, slot, history or stock. */
data class NativeTieredNextSelection(val nextWorkoutId:String,val absoluteWeek:Int,
    val recovery:Boolean=false)

/** One day, one workout ID; recovery is explicitly selected per slot. */
data class NativeMultiTierNextSelection(val nextWorkoutId:String,val absoluteWeek:Int,
    val weekday:Int,val recoverySlotIds:Set<String> = emptySet())

data class NativeMultiTierNextSlot(val evidence:CustomTieredTransitionWeekEvidence,
    val result:TieredLinearResult.Candidate) {
    val requiresExplicitResetReview:Boolean get()=needsNewTierResetConsent(evidence.request,result)
}

data class NativeMultiTierNextCandidate(
    val selection:NativeMultiTierNextSelection,
    val slots:List<NativeMultiTierNextSlot>,
    val draft:CustomTrainingCycle,
    val snapshot:CustomWorkoutDaySnapshot,
    val historyRevision:Long,
    val audit:CustomCoachAudit
) {
    val mayIssueWorkout:Boolean get()=false
    val maySaveProgram:Boolean get()=false
}

/** The engine reports every historical reset in the replay. Consent is
 * needed only for a newly generated reset from this latest completed
 * exposure; an earlier reset is authenticated by its own frozen receipt.
 * A recovery exposure contributes no reset decision of its own.
 */
internal fun needsNewTierResetConsent(request:TieredLinearRequest,result:TieredLinearResult.Candidate):Boolean =
    request.completed.lastOrNull()?.workoutId?.let{latest->
        result.transitions.any{it.workoutId==latest && it.decision==TieredLinearDecision.RESET_REVIEW}
    } ?:false

data class NativeTieredNextCandidate(
    val selection:NativeTieredNextSelection,val request:TieredLinearRequest,
    val result:TieredLinearResult.Candidate,val draft:CustomPercentageDraftResult.Proposed,
    val historyRevision:Long,
    val transitionAudit:CustomCoachAudit,
    val priorTierTransitions:List<CustomTieredTransitionWeekEvidence> = emptyList()
) {
    val mayIssueWorkout:Boolean get()=false
    val maySaveProgram:Boolean get()=false
    val requiresExplicitResetReview:Boolean get()=needsNewTierResetConsent(request,result)
}

sealed interface NativeSavedNamedReview {
    data class Ready(
        val draft:CustomTrainingCycle,
        val stamp:CustomWorkoutSourceStamp,
        val receipts:List<NativeNamedPercentageDraftReceipt>,
        val priorConsent:NativeKotlinCustomCycleSignoff,
        val evidence:List<CustomNamedPercentageWeekEvidence>,
        val inputs:CustomCoachAuditInputs,
        val audit:CustomCoachAudit,
        val initialTierReceipt:NativeTieredInitialDraftReceipt?=null,
        val initialTierEvidence:CustomTieredInitialWeekEvidence?=null,
        val initialTierReceipts:List<NativeTieredInitialDraftReceipt> = listOfNotNull(initialTierReceipt),
        val initialTierEvidences:List<CustomTieredInitialWeekEvidence> = listOfNotNull(initialTierEvidence)
    ):NativeSavedNamedReview {
        val maySignoff:Boolean get()=false
        val mayIssueWorkout:Boolean get()=false
        val mayModifyHistory:Boolean get()=false
    }
    /** A verified current named signoff, NOT a workout grant. */
    data class Signed(
        val draft:CustomTrainingCycle,
        val stamp:CustomWorkoutSourceStamp,
        val receipts:List<NativeNamedPercentageDraftReceipt>,
        val consent:NativeKotlinCustomCycleSignoff,
        val evidence:List<CustomNamedPercentageWeekEvidence>,
        val inputs:CustomCoachAuditInputs,
        val audit:CustomCoachAudit,
        val historyRevision:Long,
        val initialTierReceipt:NativeTieredInitialDraftReceipt?=null,
        val initialTierEvidence:CustomTieredInitialWeekEvidence?=null,
        val nextTierCandidate:NativeTieredNextCandidate?=null,
        val initialTierReceipts:List<NativeTieredInitialDraftReceipt> = listOfNotNull(initialTierReceipt),
        val initialTierEvidences:List<CustomTieredInitialWeekEvidence> = listOfNotNull(initialTierEvidence),
        val nextMultiTierCandidate:NativeMultiTierNextCandidate?=null
    ):NativeSavedNamedReview {
        val mayIssueWorkout:Boolean get()=false
        val mayModifyHistory:Boolean get()=false
    }
    data class Rejected(val reason:NativeSavedNamedReviewRefusal,
        val audit:CustomCoachAudit?=null):NativeSavedNamedReview
}

/**
 * Cold-review bridge for an explicitly confirmed, UNSIGNED specialist draft.
 * Reopens all authorities under the same owner fence; never accepts stock,
 * history revisions or saved receipts from the UI. Reuses the signed base
 * fingerprint, canonical source snapshot, physical-stock selector and
 * full-cycle coach audit. No signoff record or workout is written here.
 */
class NativeKotlinSavedNamedPercentageReview(
    private val fence:NativeKotlinApprovalRevisionFence,
    private val identity:NativeKotlinCurrentIdentity,
    private val drafts:NativeCustomCycleDraftFileStore,
    private val sources:NativeKotlinCustomAuthoringSourceFileStore,
    private val signoffs:NativeKotlinCustomCycleSignoffFileStore,
    private val history:NativeKotlinMixedHistoryReadCoordinator,
    private val outstanding:NativeKotlinCustomOutstandingWorkoutInventory,
    private val machineRosters:NativeKotlinMachineRosterFileStore?=null
) {
    private val id=Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,79}$")

    fun review(ownerKey:String,cycleId:String,inputs:CustomCoachAuditInputs):NativeSavedNamedReview =
        evaluate(ownerKey,cycleId,inputs,signed=false)

    /** Reopen current signed fixed prescriptions after cold restart.
     * History may legitimately advance after approval. The eventual session
     * issuer must separately check that its selected session is not protected. */
    fun reviewSigned(ownerKey:String,cycleId:String,inputs:CustomCoachAuditInputs):NativeSavedNamedReview =
        evaluate(ownerKey,cycleId,inputs,signed=true)

    /** Dedicated initial-tier review. Fixed-only callers never inherit it. */
    fun reviewTieredInitial(ownerKey:String,cycleId:String,inputs:CustomCoachAuditInputs):NativeSavedNamedReview =
        evaluate(ownerKey,cycleId,inputs,signed=false,tiered=true)

    fun reviewSignedTieredInitial(ownerKey:String,cycleId:String,inputs:CustomCoachAuditInputs):NativeSavedNamedReview =
        evaluate(ownerKey,cycleId,inputs,signed=true,tiered=true)

    /** Read the signed authored supporting-day targets after the initial
     * exposure is complete. This does not compute a new tier transition.
     * It authenticates the same initial origin as transition review. */
    internal fun reviewSignedTieredSupport(ownerKey:String,cycleId:String,inputs:CustomCoachAuditInputs):NativeSavedNamedReview =
        evaluate(ownerKey,cycleId,inputs,signed=true,tiered=true,supportingDay=true)

    /** First post-initial transition is a read-only proposal. The original
     * signed initial recipe stays immutable. The new cycle also must pass
     * full specialist/coach review; durable consent remains separate. */
    fun previewTieredNext(ownerKey:String,cycleId:String,inputs:CustomCoachAuditInputs,
        selection:NativeTieredNextSelection):NativeSavedNamedReview =
        evaluate(ownerKey,cycleId,inputs,signed=true,tiered=true,next=selection)

    /** First multi-root transition preview. All due slots are selected from
     * signed roots, never from a caller-provided slot/stock/reference list.
     * Later transitions require frozen prior day receipts before review. */
    fun previewMultiTieredNext(ownerKey:String,cycleId:String,inputs:CustomCoachAuditInputs,
        selection:NativeMultiTierNextSelection):NativeSavedNamedReview =
        evaluate(ownerKey,cycleId,inputs,signed=true,tiered=true,
            multiNext=selection.copy(recoverySlotIds=selection.recoverySlotIds.toSet()))

    /** Only an exact persisted day receipt may re-review its own unissued
     * preparation. All other outstanding sessions remain protected. */
    internal fun previewMultiTieredNextForPreparation(ownerKey:String,cycleId:String,inputs:CustomCoachAuditInputs,
        selection:NativeMultiTierNextSelection,receiptFingerprint:String?,
        priorDays:List<NativeFrozenTierDay> = emptyList()):NativeSavedNamedReview =
        evaluate(ownerKey,cycleId,inputs,signed=true,tiered=true,multiNext=selection,
            preparedReceiptFingerprint=receiptFingerprint,priorMultiDays=priorDays)

    /** Only receipt-backed recovery may re-review an already prepared
     * transition. A plain preview must still refuse every outstanding day.
     */
    internal fun previewTieredNextForPreparation(ownerKey:String,cycleId:String,
        inputs:CustomCoachAuditInputs,selection:NativeTieredNextSelection,
        receiptFingerprint:String?,priorTransitions:List<NativeFrozenTierTransition> = emptyList(),
        staleReceipt:NativeFrozenTierTransition?=null):
        NativeSavedNamedReview =
        evaluate(ownerKey,cycleId,inputs,signed=true,tiered=true,next=selection,
            preparedReceiptFingerprint=receiptFingerprint,priorTransitions=priorTransitions,
            staleReceipt=staleReceipt)

    private fun evaluate(ownerKey:String,cycleId:String,inputs:CustomCoachAuditInputs,
        signed:Boolean,tiered:Boolean=false,next:NativeTieredNextSelection?=null,
        preparedReceiptFingerprint:String?=null,
        priorTransitions:List<NativeFrozenTierTransition> = emptyList(),
        supportingDay:Boolean=false,staleReceipt:NativeFrozenTierTransition?=null,
        multiNext:NativeMultiTierNextSelection?=null,
        priorMultiDays:List<NativeFrozenTierDay> = emptyList()):NativeSavedNamedReview {
        fun deny(reason:NativeSavedNamedReviewRefusal,audit:CustomCoachAudit?=null)=
            NativeSavedNamedReview.Rejected(reason,audit)
        if(!id.matches(ownerKey)||!id.matches(cycleId))
            return deny(NativeSavedNamedReviewRefusal.NOT_AUTHORIZED)
        return runCatching {fence.withOwnerTransaction(ownerKey) {
            val owner=identity.signedInIdentity()?.takeIf{it.ownerKey==ownerKey}
                ?:return@withOwnerTransaction deny(NativeSavedNamedReviewRefusal.NOT_AUTHORIZED)
            val stored=drafts.read(ownerKey,cycleId) as? NativeCustomDraftReadResult.Found
                ?:return@withOwnerTransaction deny(NativeSavedNamedReviewRefusal.NO_CONFIRMED_DRAFT)
            val candidate=stored.draft
            val receipts=stored.namedReceipts
            val roots=stored.tieredInitialReceipts
            val initial=roots.singleOrNull()
            if(tiered) {
                if(receipts.isNotEmpty() || !NativeTieredInitialDraftReceiptCodec.validBatch(roots,ownerKey,candidate) ||
                    ((next!=null || supportingDay) && initial==null))
                    return@withOwnerTransaction deny(NativeSavedNamedReviewRefusal.NO_CONFIRMED_DRAFT)
            } else if(roots.isNotEmpty() || !NativeNamedPercentageDraftReceiptCodec.validBatch(receipts,ownerKey,candidate))
                return@withOwnerTransaction deny(NativeSavedNamedReviewRefusal.NO_CONFIRMED_DRAFT)
            val firstRoot=roots.firstOrNull()
            val oldStamp=firstRoot?.stamp ?:receipts.first().stamp
            val consentRevision=firstRoot?.consentRevision ?:receipts.first().consentRevision
            val historyRevision=firstRoot?.historyRevision ?:receipts.first().historyRevision
            val rosterRevision=firstRoot?.machineRosterRevision ?:receipts.first().machineRosterRevision
            val stamp=oldStamp.copy(cycleRevision=candidate.revision)
            val prior=(signoffs.read(ownerKey,cycleId) as? NativeCustomSignoffRead.Found)?.receipt
                ?:return@withOwnerTransaction deny(NativeSavedNamedReviewRefusal.STALE_PRIOR_CONSENT)
            if(prior.ownerKey!=ownerKey || prior.athleteId!=owner.athleteId ||
                prior.cycleId!=cycleId ||
                prior.cycleRevision!=(if(signed)candidate.revision else oldStamp.cycleRevision) ||
                prior.recordRevision!=(if(signed)consentRevision+1 else consentRevision) ||
                prior.sourceRevision!=oldStamp.gymCatalogSourceRevision)
                return@withOwnerTransaction deny(NativeSavedNamedReviewRefusal.STALE_PRIOR_CONSENT)
            val source=(sources.read(ownerKey) as? NativeAuthoringSourceRead.Found)?.source
                ?:return@withOwnerTransaction deny(NativeSavedNamedReviewRefusal.SOURCE_CHANGED)
            val profile=source.setup.reviewed
                ?:return@withOwnerTransaction deny(NativeSavedNamedReviewRefusal.SOURCE_CHANGED)
            if(source.ownerKey!=ownerKey || source.athleteId!=owner.athleteId ||
                candidate.athleteId!=owner.athleteId || source.revision!=stamp.gymCatalogSourceRevision ||
                profile.profileRevision!=stamp.athleteProfileRevision || profile.gym.gymId!=stamp.gymId)
                return@withOwnerTransaction deny(NativeSavedNamedReviewRefusal.SOURCE_CHANGED)

            // Recover the signed base by removing ONLY the confirmed named
            // amendments. An unchanged prior fingerprint is required, rather
            // than trusting any nonnamed content carried by the new draft.
            val affected=(receipts.map{it.blockId}+roots.map{it.blockId}).toSet()
            val stripped=candidate.weekOverrides.mapValues{(_,edits)->
                edits.filterNot{edit->edit.sets.any{it.percentageIntent!=null}}}
            val clean=stripped.filterNot{(blockId,edits)->blockId in affected && edits.isEmpty()}
            val baseOptions=listOf(clean,stripped).distinct().map{
                candidate.copy(revision=oldStamp.cycleRevision,weekOverrides=it)
            }
            val base=if(signed) {
                val hash=if(roots.isNotEmpty())NativeCustomCycleSignoffFingerprint.calculateTieredInitialBatch(
                    candidate,source,inputs,roots)
                else NativeCustomCycleSignoffFingerprint.calculateNamed(candidate,source,inputs,receipts)
                if(hash!=prior.fingerprintSha256)
                    return@withOwnerTransaction deny(NativeSavedNamedReviewRefusal.STALE_PRIOR_CONSENT)
                // Signed fingerprint already binds the complete candidate and
                // receipts. Empty map entries have no effect on base targets.
                baseOptions.first()
            } else baseOptions.singleOrNull{
                NativeCustomCycleSignoffFingerprint.calculate(it,source,inputs)==prior.fingerprintSha256
            } ?:return@withOwnerTransaction deny(NativeSavedNamedReviewRefusal.STALE_PRIOR_CONSENT)

            val roster=if(machineRosters==null)null else
                (machineRosters.read(ownerKey) as? NativeMachineRosterResult.Saved)?.snapshot
                    ?:return@withOwnerTransaction deny(NativeSavedNamedReviewRefusal.STOCK_UNVERIFIED)
            if((roster?.revision?:0L)!=rosterRevision ||
                (roster!=null && roster.ownerId!=ownerKey))
                return@withOwnerTransaction deny(NativeSavedNamedReviewRefusal.SOURCE_CHANGED)
            val timeline=(history.read(ownerKey,owner.athleteId)
                as? NativeKotlinMixedHistoryReadResult.Review)?.timeline
                ?:return@withOwnerTransaction deny(NativeSavedNamedReviewRefusal.HISTORY_CHANGED)
            if(if(signed && (roots.size!=1 || next!=null || supportingDay))timeline.manifestRevision<historyRevision
                else timeline.manifestRevision!=historyRevision)
                return@withOwnerTransaction deny(NativeSavedNamedReviewRefusal.HISTORY_CHANGED)
            val completed=timeline.completions.filterIsInstance<NativeKotlinMixedHistoryCompletion.Custom>()
                .map{it.manifest.workoutId}.toSet()
            val census=(outstanding.inspect(ownerKey,owner.athleteId,completed)
                as? NativeCustomInventoryReview.Reviewed)
                ?:return@withOwnerTransaction deny(NativeSavedNamedReviewRefusal.HISTORY_CHANGED)
            val evidence=ArrayList<CustomNamedPercentageWeekEvidence>()
            for(receipt in receipts) {
                val selection=receipt.selection
                if(!signed && (NativeFutureWeekProposalInventoryPolicy.blocks(census.sessions,cycleId,selection.absoluteWeek) ||
                    census.sessions.any{it.cycleId==cycleId && it.absoluteWeek>=selection.absoluteWeek}))
                    return@withOwnerTransaction deny(NativeSavedNamedReviewRefusal.PROTECTED_WEEK)
                val original=(CustomWorkoutSnapshotDomain.reviewForConsent(base,source.setup,
                    source.catalog,source.revision,inputs,selection.absoluteWeek,selection.weekday)
                    as? CustomWorkoutSnapshotResult.Preview)?.snapshot
                    ?:return@withOwnerTransaction deny(NativeSavedNamedReviewRefusal.STALE_PRIOR_CONSENT)
                if(original.stamp!=oldStamp || original.blockId!=receipt.blockId ||
                    original.weekInBlock!=receipt.weekInBlock ||
                    NativeKotlinCustomMachineBindingPolicy.review(ownerKey,original,source.catalog,roster)
                        !is NativeCustomMachineBindingReview.Verified)
                    return@withOwnerTransaction deny(NativeSavedNamedReviewRefusal.STOCK_UNVERIFIED)
                val selected=original.slots.singleOrNull{it.slotId==selection.slotId}
                    ?:return@withOwnerTransaction deny(NativeSavedNamedReviewRefusal.SOURCE_CHANGED)
                val physical=NativeKotlinCustomSelectedStockDomain.resolve(source,selected,roster)
                    ?:return@withOwnerTransaction deny(NativeSavedNamedReviewRefusal.STOCK_UNVERIFIED)
                val stock=physical.stock
                    ?:return@withOwnerTransaction deny(NativeSavedNamedReviewRefusal.STOCK_UNVERIFIED)
                if(selected.exerciseId!=receipt.exerciseId || selected.machineInstanceId!=receipt.machineInstanceId ||
                    physical.machineInstanceId!=receipt.machineInstanceId ||
                    stock.unit!=receipt.unit || stock.basis!=receipt.basis)
                    return@withOwnerTransaction deny(NativeSavedNamedReviewRefusal.STOCK_UNVERIFIED)
                val block=base.blocks.single{it.blockId==receipt.blockId}
                val scope=BasicProgressionScope(owner.athleteId,cycleId,stamp.gymId,
                    block.blockId,block.blockId+":d"+selection.weekday,selected.exerciseId,
                    selected.machineInstanceId,stock.unit,stock.basis)
                val request=NamedPercentageRequest(scope,selection.scheme,selection.tier,selection.dayRole,
                    selection.referenceKind,selection.reference,true,receipt.weekInBlock,block.weeks,
                    selected.sets.count{it.prescription.type==DraftSetType.WORKING},stock)
                evidence+=CustomNamedPercentageWeekEvidence(CustomNamedPercentageWeekKey(
                    block.blockId,receipt.weekInBlock,selection.weekday,selection.slotId),request)
            }
            val tierEvidences=roots.map{initial->
                val selection=initial.selection
                if((!signed && (NativeFutureWeekProposalInventoryPolicy.blocks(census.sessions,cycleId,selection.absoluteWeek) ||
                    census.sessions.any{it.cycleId==cycleId && it.absoluteWeek>=selection.absoluteWeek})) ||
                    (roots.size==1 && next==null && !supportingDay && timeline.completions.any{it.manifest.workoutId==selection.nextWorkoutId}))
                    return@withOwnerTransaction deny(NativeSavedNamedReviewRefusal.PROTECTED_WEEK)
                val original=(CustomWorkoutSnapshotDomain.reviewForConsent(base,source.setup,
                    source.catalog,source.revision,inputs,selection.absoluteWeek,selection.weekday)
                    as? CustomWorkoutSnapshotResult.Preview)?.snapshot
                    ?:return@withOwnerTransaction deny(NativeSavedNamedReviewRefusal.STALE_PRIOR_CONSENT)
                if(original.stamp!=oldStamp || original.blockId!=initial.blockId ||
                    original.weekInBlock!=initial.weekInBlock ||
                    NativeKotlinCustomMachineBindingPolicy.review(ownerKey,original,source.catalog,roster)
                        !is NativeCustomMachineBindingReview.Verified)
                    return@withOwnerTransaction deny(NativeSavedNamedReviewRefusal.STOCK_UNVERIFIED)
                val selected=original.slots.singleOrNull{it.slotId==selection.slotId}
                    ?:return@withOwnerTransaction deny(NativeSavedNamedReviewRefusal.SOURCE_CHANGED)
                val physical=NativeKotlinCustomSelectedStockDomain.resolve(source,selected,roster)
                    ?:return@withOwnerTransaction deny(NativeSavedNamedReviewRefusal.STOCK_UNVERIFIED)
                val stock=physical.stock
                    ?:return@withOwnerTransaction deny(NativeSavedNamedReviewRefusal.STOCK_UNVERIFIED)
                if(selected.exerciseId!=initial.exerciseId ||
                    selected.machineInstanceId!=initial.machineInstanceId ||
                    physical.machineInstanceId!=initial.machineInstanceId ||
                    stock.unit!=initial.unit || stock.basis!=initial.basis)
                    return@withOwnerTransaction deny(NativeSavedNamedReviewRefusal.STOCK_UNVERIFIED)
                val scope=BasicProgressionScope(owner.athleteId,cycleId,stamp.gymId,
                    initial.blockId,initial.blockId+":d"+selection.weekday,selected.exerciseId,
                    selected.machineInstanceId,stock.unit,stock.basis)
                // An initial review requires an empty actual ledger. A
                // transition instead proves the immutable initial journal
                // below, then replays the full corrected ledger for the
                // next week. Never rederive the old issued recipe using
                // newer actuals.
                val rootCompleted=signed && roots.size>1 && selection.nextWorkoutId in completed
                if(next==null && !supportingDay && !rootCompleted) {
                    val ledger=history.readTieredLedger(ownerKey,owner.athleteId,scope,
                        selection.slotId,selection.tier,selection.absoluteWeek)
                        as? NativeOwnerTieredLedgerResult.Review
                        ?:return@withOwnerTransaction deny(NativeSavedNamedReviewRefusal.HISTORY_CHANGED)
                    if(ledger.manifestRevision!=timeline.manifestRevision || ledger.exposures.isNotEmpty())
                        return@withOwnerTransaction deny(NativeSavedNamedReviewRefusal.HISTORY_CHANGED)
                }
                val request=TieredLinearRequest(selection.nextWorkoutId,scope,selection.tier,
                    TieredLinearState(0,selection.initialLoad),true,emptyList(),stock,
                    selection.maximumAuthorizedIncrease,
                    selected.sets.count{it.prescription.type==DraftSetType.WORKING},sourceSlotId=selection.slotId)
                CustomTieredInitialWeekEvidence(CustomNamedPercentageWeekKey(initial.blockId,
                    initial.weekInBlock,selection.weekday,selection.slotId),request)
            }
            val tierEvidence=tierEvidences.singleOrNull()
            val audit=CustomCycleCoachAuditDomain.reviewSpecialistForReview(candidate,source.setup,
                source.catalog,inputs,stamp,evidence,tierEvidences)
            if(audit.status!=CustomCoachAuditStatus.READY_FOR_ATHLETE_REVIEW ||
                audit.issues.isNotEmpty() || audit.auditedCycleRevision!=candidate.revision ||
                audit.structuralReview?.verifiedDraft!=candidate)
                return@withOwnerTransaction deny(NativeSavedNamedReviewRefusal.FULL_CYCLE_AUDIT_FAILED,audit)
            // Each completed multi-root day must retain the exact signed
            // original snapshot. Other roots can still be issued independently.
            if(signed && roots.size>1) {
                for(root in roots.distinctBy{it.selection.nextWorkoutId}.filter{it.selection.nextWorkoutId in completed}) {
                    val s=root.selection
                    val fact=census.sessions.singleOrNull{it.workoutId==s.nextWorkoutId &&
                        it.cycleId==cycleId && it.absoluteWeek==s.absoluteWeek &&
                        it.weekday==s.weekday && it.issued && it.completed}
                        ?:return@withOwnerTransaction deny(NativeSavedNamedReviewRefusal.HISTORY_CHANGED)
                    val origin=outstanding.preparedOrigin(ownerKey,fact.workoutId)
                        ?:return@withOwnerTransaction deny(NativeSavedNamedReviewRefusal.HISTORY_CHANGED)
                    val exact=(CustomWorkoutSnapshotDomain.reviewSpecialistForConsent(candidate,
                        source.setup,source.catalog,source.revision,inputs,evidence,tierEvidences,
                        s.absoluteWeek,s.weekday) as? CustomWorkoutSnapshotResult.Preview)?.snapshot
                        ?:return@withOwnerTransaction deny(NativeSavedNamedReviewRefusal.FULL_CYCLE_AUDIT_FAILED)
                    if(origin.authority!=NativeCustomPreparationAuthority.TIERED_INITIAL ||
                        origin.consentRevision!=prior.recordRevision ||
                        origin.consentFingerprint!=prior.fingerprintSha256 || origin.snapshot!=exact)
                        return@withOwnerTransaction deny(NativeSavedNamedReviewRefusal.STALE_PRIOR_CONSENT)
                }
            }
            if(next!=null || supportingDay) {
                val selection=initial?.selection
                    ?:return@withOwnerTransaction deny(NativeSavedNamedReviewRefusal.NO_CONFIRMED_DRAFT)
                val initialEvidence=tierEvidence
                    ?:return@withOwnerTransaction deny(NativeSavedNamedReviewRefusal.NO_CONFIRMED_DRAFT)
                if(census.sessions.singleOrNull{it.workoutId==selection.nextWorkoutId &&
                    it.cycleId==cycleId && it.absoluteWeek==selection.absoluteWeek &&
                    it.weekday==selection.weekday && it.issued && it.completed}==null)
                    return@withOwnerTransaction deny(NativeSavedNamedReviewRefusal.HISTORY_CHANGED)
                val origin=outstanding.preparedOrigin(ownerKey,selection.nextWorkoutId)
                    ?:return@withOwnerTransaction deny(NativeSavedNamedReviewRefusal.HISTORY_CHANGED)
                val exact=(CustomWorkoutSnapshotDomain.reviewSpecialistForConsent(candidate,
                    source.setup,source.catalog,source.revision,inputs,evidence,listOf(initialEvidence),
                    selection.absoluteWeek,selection.weekday)
                    as? CustomWorkoutSnapshotResult.Preview)?.snapshot
                    ?:return@withOwnerTransaction deny(NativeSavedNamedReviewRefusal.FULL_CYCLE_AUDIT_FAILED)
                if(origin.authority!=NativeCustomPreparationAuthority.TIERED_INITIAL ||
                    origin.consentRevision!=prior.recordRevision ||
                    origin.consentFingerprint!=prior.fingerprintSha256 || origin.snapshot!=exact)
                    return@withOwnerTransaction deny(NativeSavedNamedReviewRefusal.STALE_PRIOR_CONSENT)
            }
            val nextMultiCandidate=if(multiNext!=null) {
                val ownPreparation=census.sessions.singleOrNull{it.workoutId==multiNext.nextWorkoutId}
                    ?.takeIf{!it.issued && !it.completed && it.cycleId==cycleId &&
                        it.absoluteWeek==multiNext.absoluteWeek && it.weekday==multiNext.weekday}
                    ?.let{outstanding.preparedOrigin(ownerKey,it.workoutId)}
                    ?.takeIf{preparedReceiptFingerprint!=null &&
                        it.authority==NativeCustomPreparationAuthority.TIERED_TRANSITION &&
                        it.consentFingerprint==preparedReceiptFingerprint &&
                        it.consentRevision==prior.recordRevision && it.athleteId==owner.athleteId}
                if(!signed || roots.size<2 || !id.matches(multiNext.nextWorkoutId) ||
                    multiNext.absoluteWeek !in 1..104 || multiNext.weekday !in 0..6 ||
                    roots.any{it.selection.nextWorkoutId==multiNext.nextWorkoutId} ||
                    timeline.completions.any{it.manifest.workoutId==multiNext.nextWorkoutId} ||
                    census.sessions.any{it.workoutId!=ownPreparation?.workoutId &&
                        (!it.completed || it.workoutId==multiNext.nextWorkoutId)})
                    return@withOwnerTransaction deny(NativeSavedNamedReviewRefusal.PROTECTED_WEEK)
                // Historical and live days share one projection. A historical
                // receipt supplies its frozen observations, not a new load.
                // The live ledger must still authenticate every issued prefix.
                val historicalEvidence=ArrayList<CustomTieredTransitionWeekEvidence>()
                var historicalCycle=candidate
                fun projectDay(selection:NativeMultiTierNextSelection,
                    frozen:NativeFrozenTierDay?):NativeMultiTierNextCandidate? {
                    var preceding=0
                    val block=historicalCycle.blocks.firstOrNull{b->
                        val match=selection.absoluteWeek in (preceding+1)..(preceding+b.weeks)
                        if(!match)preceding+=b.weeks
                        match
                    }?:return null
                    val localWeek=selection.absoluteWeek-preceding
                    val due=roots.filter{it.blockId==block.blockId &&
                        it.selection.weekday==selection.weekday &&
                        it.selection.absoluteWeek<=selection.absoluteWeek}
                    val dueIds=due.map{it.selection.slotId}.toSet()
                    if(due.isEmpty() || !dueIds.containsAll(selection.recoverySlotIds) ||
                        (frozen!=null && frozen.slots.keys!=dueIds))return null
                    val slots=ArrayList<NativeMultiTierNextSlot>()
                    val proposals=ArrayList<CustomPercentageDraftResult.Proposed>()
                    val dayStamp=stamp.copy(cycleRevision=historicalCycle.revision)
                    for(root in due) {
                        val s=root.selection
                        val initialRequest=tierEvidences.singleOrNull{it.key==
                            CustomNamedPercentageWeekKey(root.blockId,root.weekInBlock,s.weekday,s.slotId)}
                            ?.request ?:return null
                        val previous=historicalEvidence.filter{it.key.blockId==root.blockId &&
                            it.key.weekday==s.weekday && it.key.slotId==s.slotId}
                        if(selection.absoluteWeek!=s.absoluteWeek+previous.size+1)return null
                        val ids=listOf(s.nextWorkoutId)+previous.map{it.request.nextWorkoutId}
                        val live=(if(frozen==null)history.readTieredLedger(ownerKey,owner.athleteId,
                            initialRequest.scope,s.slotId,s.tier,selection.absoluteWeek)
                            else history.readTieredHistoryPrefixForRecovery(ownerKey,owner.athleteId,
                                initialRequest.scope,s.slotId,s.tier,selection.absoluteWeek))
                            as? NativeOwnerTieredLedgerResult.Review ?:return null
                        if(live.manifestRevision!=timeline.manifestRevision ||
                            live.exposures.map{it.workoutId}!=ids)return null
                        val exposures=if(frozen==null)live.exposures else {
                            val bytes=frozen.slots[s.slotId] ?:return null
                            val saved=runCatching{NativeTieredHistoryPrefixCodec.decode(bytes,
                                initialRequest.scope,s.tier,s.slotId)}.getOrNull() ?:return null
                            if(saved.size!=live.exposures.size || saved.zip(live.exposures).any{(a,b)->
                                a.copy(observations=b.observations)!=b})return null
                            saved
                        }
                        val request=initialRequest.copy(nextWorkoutId=selection.nextWorkoutId,
                            completed=exposures,recovery=s.slotId in selection.recoverySlotIds)
                        val key=CustomNamedPercentageWeekKey(block.blockId,localWeek,s.weekday,s.slotId)
                        val derived=CustomPercentageDraftDomain.reviewTiered(
                            CustomPercentageDraftSource(historicalCycle,dayStamp,key.blockId,key.weekInBlock,
                                key.weekday,key.slotId),request) as? CustomPercentageDraftResult.Proposed ?:return null
                        val numeric=derived.tieredResult ?:return null
                        proposals+=derived
                        slots+=NativeMultiTierNextSlot(CustomTieredTransitionWeekEvidence(key,request),numeric)
                    }
                    val combined=CustomPercentageDraftDomain.combineReviewedWeekEdits(historicalCycle,proposals)
                        ?:return null
                    val proof=historicalEvidence+slots.map{it.evidence}
                    val transitionAudit=CustomCycleCoachAuditDomain.reviewSpecialistForReview(
                        combined,source.setup,source.catalog,inputs,stamp.copy(cycleRevision=combined.revision),
                        evidence,tierEvidences,proof)
                    if(transitionAudit.status!=CustomCoachAuditStatus.READY_FOR_ATHLETE_REVIEW ||
                        transitionAudit.issues.isNotEmpty() || transitionAudit.auditedCycleRevision!=combined.revision ||
                        transitionAudit.structuralReview?.verifiedDraft!=combined ||
                        transitionAudit.coachingPolicyRevisions!=prior.policyReferences)return null
                    val snapshot=(CustomWorkoutSnapshotDomain.reviewSpecialistForConsent(combined,
                        source.setup,source.catalog,source.revision,inputs,evidence,tierEvidences,
                        selection.absoluteWeek,selection.weekday,proof) as? CustomWorkoutSnapshotResult.Preview)
                        ?.snapshot ?:return null
                    if(snapshot.stamp!=stamp.copy(cycleRevision=combined.revision) ||
                        snapshot.blockId!=block.blockId || snapshot.weekInBlock!=localWeek)return null
                    return NativeMultiTierNextCandidate(selection,slots.toList(),combined,snapshot,
                        frozen?.historyRevision ?:timeline.manifestRevision,transitionAudit)
                }
                val historicalFacts=linkedMapOf<String,NativeCustomCycleSessionFact>()
                var lastPosition=0
                var lastRevision=historyRevision
                val currentPosition=multiNext.absoluteWeek*7+multiNext.weekday
                for(old in priorMultiDays) {
                    val s=old.selection
                    val position=s.absoluteWeek*7+s.weekday
                    if(position<=lastPosition || position>=currentPosition || old.owner!=ownerKey ||
                        old.athlete!=owner.athleteId || old.cycle!=cycleId || old.inputs!=inputs ||
                        old.consentRevision!=prior.recordRevision || old.cycleRevision!=candidate.revision ||
                        old.sourceRevision!=prior.sourceRevision || old.consentFingerprint!=prior.fingerprintSha256 ||
                        old.historyRevision<=lastRevision || old.historyRevision>=timeline.manifestRevision ||
                        s.nextWorkoutId==multiNext.nextWorkoutId || s.nextWorkoutId in historicalFacts)
                        return@withOwnerTransaction deny(NativeSavedNamedReviewRefusal.HISTORY_CHANGED)
                    val fact=census.sessions.singleOrNull{it.workoutId==s.nextWorkoutId && it.cycleId==cycleId &&
                        it.absoluteWeek==s.absoluteWeek && it.weekday==s.weekday && it.issued && it.completed}
                        ?:return@withOwnerTransaction deny(NativeSavedNamedReviewRefusal.HISTORY_CHANGED)
                    val origin=outstanding.preparedOrigin(ownerKey,s.nextWorkoutId)
                        ?:return@withOwnerTransaction deny(NativeSavedNamedReviewRefusal.HISTORY_CHANGED)
                    val replay=projectDay(s,old)
                        ?:return@withOwnerTransaction deny(NativeSavedNamedReviewRefusal.HISTORY_CHANGED)
                    if(origin.authority!=NativeCustomPreparationAuthority.TIERED_TRANSITION ||
                        origin.consentRevision!=prior.recordRevision || origin.consentFingerprint!=old.fingerprint ||
                        origin.snapshot!=replay.snapshot ||
                        !NativeCustomCycleDraftCodec.encode(replay.draft).contentEquals(old.proposed) ||
                        old.resets!=replay.slots.filter{it.requiresExplicitResetReview}.map{it.evidence.key.slotId}.toSet())
                        return@withOwnerTransaction deny(NativeSavedNamedReviewRefusal.STALE_PRIOR_CONSENT)
                    historicalFacts[s.nextWorkoutId]=NativeCustomCycleSessionFact(
                        fact.workoutId,fact.absoluteWeek,fact.weekday,candidate.revision,true,true)
                    historicalCycle=replay.draft
                    historicalEvidence+=replay.slots.map{it.evidence}
                    lastPosition=position
                    lastRevision=old.historyRevision
                }
                val facts=census.sessions.filter{it.cycleId==cycleId}.map{item->
                    if(item.workoutId==ownPreparation?.workoutId)
                        NativeCustomCycleSessionFact(item.workoutId,item.absoluteWeek,item.weekday,
                            candidate.revision,item.issued,item.completed)
                    else historicalFacts[item.workoutId] ?:run {
                        val origin=outstanding.preparedOrigin(ownerKey,item.workoutId)
                            ?:return@withOwnerTransaction deny(NativeSavedNamedReviewRefusal.HISTORY_CHANGED)
                        NativeCustomNextCycleSlotDomain.multiInitialFact(candidate,roots,item,origin,prior)
                            ?:return@withOwnerTransaction deny(NativeSavedNamedReviewRefusal.HISTORY_CHANGED)
                    }
                }
                val allowed=when(val scheduled=NativeCustomNextCycleSlotDomain.select(candidate,facts)) {
                    is NativeCustomNextCycleSlot.Next -> scheduled.absoluteWeek==multiNext.absoluteWeek &&
                        scheduled.weekday==multiNext.weekday && ownPreparation==null
                    is NativeCustomNextCycleSlot.Prepared -> scheduled.absoluteWeek==multiNext.absoluteWeek &&
                        scheduled.weekday==multiNext.weekday && scheduled.workoutId==ownPreparation?.workoutId
                    else -> false
                }
                if(!allowed)return@withOwnerTransaction deny(NativeSavedNamedReviewRefusal.PROTECTED_WEEK)
                val projected=projectDay(multiNext,null)
                    ?:return@withOwnerTransaction deny(NativeSavedNamedReviewRefusal.HISTORY_CHANGED)
                val snapshot=projected.snapshot
                if(ownPreparation!=null && ownPreparation.snapshot!=snapshot)
                    return@withOwnerTransaction deny(NativeSavedNamedReviewRefusal.STALE_PRIOR_CONSENT)
                projected
            } else null
            val nextCandidate=if(next!=null) {
                val receipt=initial
                    ?:return@withOwnerTransaction deny(NativeSavedNamedReviewRefusal.NO_CONFIRMED_DRAFT)
                val initialRequest=tierEvidence?.request
                    ?:return@withOwnerTransaction deny(NativeSavedNamedReviewRefusal.NO_CONFIRMED_DRAFT)
                val selection=receipt.selection
                val block=candidate.blocks.single{it.blockId==receipt.blockId}
                val ownPreparation=census.sessions.singleOrNull{it.workoutId==next.nextWorkoutId}
                    ?.takeIf{!it.issued && !it.completed && it.cycleId==cycleId &&
                        it.absoluteWeek==next.absoluteWeek && it.weekday==selection.weekday}
                    ?.let{outstanding.preparedOrigin(ownerKey,it.workoutId)}
                    ?.takeIf{preparedReceiptFingerprint!=null &&
                        it.authority==NativeCustomPreparationAuthority.TIERED_TRANSITION &&
                        it.consentFingerprint==preparedReceiptFingerprint &&
                        it.consentRevision==prior.recordRevision &&
                        it.athleteId==owner.athleteId}
                if(!id.matches(next.nextWorkoutId) ||
                    next.absoluteWeek!=selection.absoluteWeek+priorTransitions.size+1 ||
                    receipt.weekInBlock+priorTransitions.size>=block.weeks ||
                    next.nextWorkoutId==selection.nextWorkoutId ||
                    timeline.completions.any{it.manifest.workoutId==next.nextWorkoutId} ||
                    census.sessions.any{session->
                        if(ownPreparation!=null && session.workoutId==ownPreparation.workoutId)false
                        else session.workoutId==next.nextWorkoutId || !session.completed ||
                            (session.cycleId==cycleId && session.absoluteWeek>=next.absoluteWeek)})
                    return@withOwnerTransaction deny(NativeSavedNamedReviewRefusal.PROTECTED_WEEK)
                val ledger=history.readTieredLedger(ownerKey,owner.athleteId,initialRequest.scope,
                    selection.slotId,selection.tier,next.absoluteWeek)
                    as? NativeOwnerTieredLedgerResult.Review
                    ?:return@withOwnerTransaction deny(NativeSavedNamedReviewRefusal.HISTORY_CHANGED)
                if(ledger.manifestRevision!=timeline.manifestRevision ||
                    ledger.exposures.map{it.workoutId}!=
                        listOf(selection.nextWorkoutId)+priorTransitions.map{it.selection.nextWorkoutId})
                    return@withOwnerTransaction deny(NativeSavedNamedReviewRefusal.HISTORY_CHANGED)

                // Rebuild each previously issued week using the evidence
                // frozen at its consent. Today's corrected actuals cannot
                // change an already-issued target or its original proof.
                // The live ledger must still prove those original targets,
                // stable physical scope and completion order.
                var historicalCycle=candidate
                var previousRevision=historyRevision
                val historicalEvidence=ArrayList<CustomTieredTransitionWeekEvidence>()
                for((index,old) in priorTransitions.withIndex()) {
                    val offset=index+1
                    val week=selection.absoluteWeek+offset
                    val key=CustomNamedPercentageWeekKey(receipt.blockId,
                        receipt.weekInBlock+offset,selection.weekday,selection.slotId)
                    if(old.ownerKey!=ownerKey || old.athleteId!=owner.athleteId ||
                        old.cycleId!=cycleId || old.consentRevision!=prior.recordRevision ||
                        old.cycleRevision!=candidate.revision || old.sourceRevision!=prior.sourceRevision ||
                        old.consentFingerprint!=prior.fingerprintSha256 ||
                        old.historyRevision<=previousRevision || old.historyRevision>=ledger.manifestRevision ||
                        old.selection.absoluteWeek!=week || old.inputs!=inputs ||
                        old.initialState!=initialRequest.initialState ||
                        old.maximumIncrease!=initialRequest.maximumAuthorizedIncrease ||
                        old.setBudget!=initialRequest.approvedSetBudget)
                        return@withOwnerTransaction deny(NativeSavedNamedReviewRefusal.STALE_PRIOR_CONSENT)
                    val frozen=runCatching{NativeTieredHistoryPrefixCodec.decode(old.prefix,
                        initialRequest.scope,selection.tier,selection.slotId)}.getOrNull()
                        ?:return@withOwnerTransaction deny(NativeSavedNamedReviewRefusal.HISTORY_CHANGED)
                    val corrected=ledger.exposures.take(offset)
                    if(frozen.size!=offset || frozen.zip(corrected).any{(saved,live)->
                        saved.copy(observations=live.observations)!=live})
                        return@withOwnerTransaction deny(NativeSavedNamedReviewRefusal.HISTORY_CHANGED)
                    val oldRequest=initialRequest.copy(nextWorkoutId=old.selection.nextWorkoutId,
                        completed=frozen,recovery=old.selection.recovery)
                    val oldNumeric=TieredLinearProgressionDomain.review(oldRequest)
                        as? TieredLinearResult.Candidate
                        ?:return@withOwnerTransaction deny(NativeSavedNamedReviewRefusal.HISTORY_CHANGED)
                    if(needsNewTierResetConsent(oldRequest,oldNumeric)!=old.resetRequired)
                        return@withOwnerTransaction deny(NativeSavedNamedReviewRefusal.STALE_PRIOR_CONSENT)
                    val oldDraft=CustomPercentageDraftDomain.reviewTiered(
                        CustomPercentageDraftSource(historicalCycle,stamp.copy(cycleRevision=historicalCycle.revision),
                            key.blockId,key.weekInBlock,key.weekday,key.slotId),oldRequest)
                        as? CustomPercentageDraftResult.Proposed
                        ?:return@withOwnerTransaction deny(NativeSavedNamedReviewRefusal.FULL_CYCLE_AUDIT_FAILED)
                    historicalCycle=oldDraft.proposal.draft
                    if(!NativeCustomCycleDraftCodec.encode(historicalCycle).contentEquals(old.proposedCycle))
                        return@withOwnerTransaction deny(NativeSavedNamedReviewRefusal.STALE_PRIOR_CONSENT)
                    historicalEvidence+=CustomTieredTransitionWeekEvidence(key,oldRequest)
                    val oldSnapshot=(CustomWorkoutSnapshotDomain.reviewSpecialistForConsent(
                        historicalCycle,source.setup,source.catalog,source.revision,inputs,evidence,
                        listOf(checkNotNull(tierEvidence)),week,selection.weekday,historicalEvidence)
                        as? CustomWorkoutSnapshotResult.Preview)?.snapshot
                        ?:return@withOwnerTransaction deny(NativeSavedNamedReviewRefusal.FULL_CYCLE_AUDIT_FAILED)
                    if(census.sessions.singleOrNull{it.workoutId==old.selection.nextWorkoutId &&
                        it.cycleId==cycleId && it.absoluteWeek==week && it.weekday==selection.weekday &&
                        it.issued && it.completed}==null)
                        return@withOwnerTransaction deny(NativeSavedNamedReviewRefusal.HISTORY_CHANGED)
                    val oldOrigin=outstanding.preparedOrigin(ownerKey,old.selection.nextWorkoutId)
                        ?:return@withOwnerTransaction deny(NativeSavedNamedReviewRefusal.HISTORY_CHANGED)
                    if(oldOrigin.authority!=NativeCustomPreparationAuthority.TIERED_TRANSITION ||
                        oldOrigin.consentRevision!=prior.recordRevision ||
                        oldOrigin.consentFingerprint!=old.fingerprint || oldOrigin.snapshot!=oldSnapshot)
                        return@withOwnerTransaction deny(NativeSavedNamedReviewRefusal.STALE_PRIOR_CONSENT)
                    previousRevision=old.historyRevision
                }

                // New numbers come exclusively from current corrected
                // evidence. If corrections invalidate a previously issued
                // transition, the canonical replay refuses; never silently
                // rewrite or bypass that historical inconsistency.
                val request=initialRequest.copy(nextWorkoutId=next.nextWorkoutId,
                    completed=ledger.exposures,recovery=next.recovery)
                val projected=CustomPercentageDraftDomain.reviewTiered(
                    CustomPercentageDraftSource(historicalCycle,stamp.copy(cycleRevision=historicalCycle.revision),
                        receipt.blockId,receipt.weekInBlock+priorTransitions.size+1,
                        selection.weekday,selection.slotId),request)
                val proposed=projected as? CustomPercentageDraftResult.Proposed
                    ?:return@withOwnerTransaction deny(
                        if((projected as? CustomPercentageDraftResult.Refused)?.reason==CustomPercentageDraftRefusal.PROTOCOL_REFUSED)
                            NativeSavedNamedReviewRefusal.HISTORY_CHANGED else NativeSavedNamedReviewRefusal.FULL_CYCLE_AUDIT_FAILED)
                val numeric=checkNotNull(proposed.tieredResult)
                // Audit the newly proposed full cycle, not only the signed
                // initial recipe. Bind the authenticated next-week proof and
                // the existing initial evidence to the proposed revision.
                val proposedCycle=proposed.proposal.draft
                val nextEvidence=CustomTieredTransitionWeekEvidence(
                    CustomNamedPercentageWeekKey(receipt.blockId,
                        receipt.weekInBlock+priorTransitions.size+1,selection.weekday,selection.slotId),request)
                val transitionAudit=CustomCycleCoachAuditDomain.reviewSpecialistForReview(
                    proposedCycle,source.setup,source.catalog,inputs,
                    stamp.copy(cycleRevision=proposedCycle.revision),evidence,
                    listOf(checkNotNull(tierEvidence)),historicalEvidence+nextEvidence)
                if(transitionAudit.status!=CustomCoachAuditStatus.READY_FOR_ATHLETE_REVIEW ||
                    transitionAudit.issues.isNotEmpty() ||
                    transitionAudit.auditedCycleRevision!=proposedCycle.revision ||
                    transitionAudit.structuralReview?.verifiedDraft!=proposedCycle ||
                    transitionAudit.coachingPolicyRevisions!=prior.policyReferences)
                    return@withOwnerTransaction deny(
                        NativeSavedNamedReviewRefusal.FULL_CYCLE_AUDIT_FAILED,transitionAudit)
                // Cancellation only: authenticate the OLD frozen target against
                // its original observations, not the new corrected target.
                // This cannot authorize a new preparation or first issuance.
                if(staleReceipt!=null) {
                    val old=staleReceipt
                    if(old.ownerKey!=ownerKey || old.athleteId!=owner.athleteId ||
                        old.cycleId!=cycleId || old.selection!=next ||
                        old.consentRevision!=prior.recordRevision ||
                        old.consentFingerprint!=prior.fingerprintSha256 ||
                        old.cycleRevision!=candidate.revision || old.sourceRevision!=prior.sourceRevision ||
                        old.historyRevision<=previousRevision || old.historyRevision>=ledger.manifestRevision ||
                        old.inputs!=inputs || old.initialState!=initialRequest.initialState ||
                        old.maximumIncrease!=initialRequest.maximumAuthorizedIncrease ||
                        old.setBudget!=initialRequest.approvedSetBudget ||
                        (ownPreparation!=null && ownPreparation.consentFingerprint!=old.fingerprint))
                        return@withOwnerTransaction deny(NativeSavedNamedReviewRefusal.STALE_PRIOR_CONSENT)
                    val frozen=runCatching{NativeTieredHistoryPrefixCodec.decode(old.prefix,
                        initialRequest.scope,selection.tier,selection.slotId)}.getOrNull()
                        ?:return@withOwnerTransaction deny(NativeSavedNamedReviewRefusal.HISTORY_CHANGED)
                    if(frozen.size!=ledger.exposures.size || frozen.zip(ledger.exposures).any{(saved,live)->
                        saved.copy(observations=live.observations)!=live})
                        return@withOwnerTransaction deny(NativeSavedNamedReviewRefusal.HISTORY_CHANGED)
                    val oldRequest=request.copy(completed=frozen)
                    val oldNumeric=TieredLinearProgressionDomain.review(oldRequest) as? TieredLinearResult.Candidate
                        ?:return@withOwnerTransaction deny(NativeSavedNamedReviewRefusal.HISTORY_CHANGED)
                    if(needsNewTierResetConsent(oldRequest,oldNumeric)!=old.resetRequired)
                        return@withOwnerTransaction deny(NativeSavedNamedReviewRefusal.STALE_PRIOR_CONSENT)
                    val key=nextEvidence.key
                    val oldDraft=CustomPercentageDraftDomain.reviewTiered(
                        CustomPercentageDraftSource(historicalCycle,stamp.copy(cycleRevision=historicalCycle.revision),
                            key.blockId,key.weekInBlock,key.weekday,key.slotId),oldRequest)
                        as? CustomPercentageDraftResult.Proposed
                        ?:return@withOwnerTransaction deny(NativeSavedNamedReviewRefusal.FULL_CYCLE_AUDIT_FAILED)
                    val oldCycle=oldDraft.proposal.draft
                    if(!NativeCustomCycleDraftCodec.encode(oldCycle).contentEquals(old.proposedCycle))
                        return@withOwnerTransaction deny(NativeSavedNamedReviewRefusal.STALE_PRIOR_CONSENT)
                    val oldSnapshot=(CustomWorkoutSnapshotDomain.reviewSpecialistForConsent(
                        oldCycle,source.setup,source.catalog,source.revision,inputs,evidence,
                        listOf(checkNotNull(tierEvidence)),next.absoluteWeek,selection.weekday,
                        historicalEvidence+CustomTieredTransitionWeekEvidence(key,oldRequest))
                        as? CustomWorkoutSnapshotResult.Preview)?.snapshot
                    if(oldSnapshot==null || (ownPreparation!=null && ownPreparation.snapshot!=oldSnapshot))
                        return@withOwnerTransaction deny(NativeSavedNamedReviewRefusal.STALE_PRIOR_CONSENT)
                } else if(ownPreparation!=null) {
                    val exactNext=(CustomWorkoutSnapshotDomain.reviewSpecialistForConsent(
                        proposedCycle,source.setup,source.catalog,source.revision,inputs,evidence,
                        listOf(checkNotNull(tierEvidence)),next.absoluteWeek,selection.weekday,
                        historicalEvidence+nextEvidence) as? CustomWorkoutSnapshotResult.Preview)?.snapshot
                    if(exactNext==null || ownPreparation.snapshot!=exactNext)
                        return@withOwnerTransaction deny(NativeSavedNamedReviewRefusal.STALE_PRIOR_CONSENT)
                }
                NativeTieredNextCandidate(next,request,numeric,proposed,ledger.manifestRevision,
                    transitionAudit,historicalEvidence.toList())
            } else null
            if(identity.signedInIdentity()!=owner)
                return@withOwnerTransaction deny(NativeSavedNamedReviewRefusal.NOT_AUTHORIZED)
            if(signed) {
                if(audit.coachingPolicyRevisions!=prior.policyReferences)
                    return@withOwnerTransaction deny(NativeSavedNamedReviewRefusal.STALE_PRIOR_CONSENT)
                NativeSavedNamedReview.Signed(candidate,stamp,receipts.toList(),prior,
                    evidence.toList(),inputs,audit,timeline.manifestRevision,initial,tierEvidence,nextCandidate,roots,tierEvidences,nextMultiCandidate)
            } else NativeSavedNamedReview.Ready(candidate,stamp,receipts.toList(),prior,evidence.toList(),inputs,audit,initial,tierEvidence,roots,tierEvidences)
        }}.getOrElse{deny(NativeSavedNamedReviewRefusal.STORAGE_UNAVAILABLE)}
    }
}
