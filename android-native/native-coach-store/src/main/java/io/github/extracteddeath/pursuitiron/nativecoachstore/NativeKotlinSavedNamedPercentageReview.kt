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

data class NativeTieredNextCandidate(
    val selection:NativeTieredNextSelection,val request:TieredLinearRequest,
    val result:TieredLinearResult.Candidate,val draft:CustomPercentageDraftResult.Proposed,
    val historyRevision:Long,
    val transitionAudit:CustomCoachAudit
) {
    val mayIssueWorkout:Boolean get()=false
    val maySaveProgram:Boolean get()=false
    val requiresExplicitResetReview:Boolean get()=result.requiresReviewedReset
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
        val initialTierEvidence:CustomTieredInitialWeekEvidence?=null
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
        val nextTierCandidate:NativeTieredNextCandidate?=null
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

    /** First post-initial transition is a read-only proposal. The original
     * signed initial recipe stays immutable. The new cycle also must pass
     * full specialist/coach review; durable consent remains separate. */
    fun previewTieredNext(ownerKey:String,cycleId:String,inputs:CustomCoachAuditInputs,
        selection:NativeTieredNextSelection):NativeSavedNamedReview =
        evaluate(ownerKey,cycleId,inputs,signed=true,tiered=true,next=selection)

    private fun evaluate(ownerKey:String,cycleId:String,inputs:CustomCoachAuditInputs,
        signed:Boolean,tiered:Boolean=false,next:NativeTieredNextSelection?=null):NativeSavedNamedReview {
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
            val initial=stored.tieredInitialReceipt
            if(tiered) {
                if(initial==null || receipts.isNotEmpty() ||
                    !NativeTieredInitialDraftReceiptCodec.valid(initial,ownerKey,candidate))
                    return@withOwnerTransaction deny(NativeSavedNamedReviewRefusal.NO_CONFIRMED_DRAFT)
            } else if(initial!=null || !NativeNamedPercentageDraftReceiptCodec.validBatch(receipts,ownerKey,candidate))
                return@withOwnerTransaction deny(NativeSavedNamedReviewRefusal.NO_CONFIRMED_DRAFT)
            val oldStamp=initial?.stamp ?:receipts.first().stamp
            val consentRevision=initial?.consentRevision ?:receipts.first().consentRevision
            val historyRevision=initial?.historyRevision ?:receipts.first().historyRevision
            val rosterRevision=initial?.machineRosterRevision ?:receipts.first().machineRosterRevision
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
            val affected=(receipts.map{it.blockId}+listOfNotNull(initial?.blockId)).toSet()
            val stripped=candidate.weekOverrides.mapValues{(_,edits)->
                edits.filterNot{edit->edit.sets.any{it.percentageIntent!=null}}}
            val clean=stripped.filterNot{(blockId,edits)->blockId in affected && edits.isEmpty()}
            val baseOptions=listOf(clean,stripped).distinct().map{
                candidate.copy(revision=oldStamp.cycleRevision,weekOverrides=it)
            }
            val base=if(signed) {
                val hash=if(initial!=null)NativeCustomCycleSignoffFingerprint.calculateTieredInitial(
                    candidate,source,inputs,initial)
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
            if(if(signed && (initial==null || next!=null))timeline.manifestRevision<historyRevision
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
            val tierEvidence=if(initial!=null) {
                val selection=initial.selection
                if((!signed && (NativeFutureWeekProposalInventoryPolicy.blocks(census.sessions,cycleId,selection.absoluteWeek) ||
                    census.sessions.any{it.cycleId==cycleId && it.absoluteWeek>=selection.absoluteWeek})) ||
                    (next==null && timeline.completions.any{it.manifest.workoutId==selection.nextWorkoutId}))
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
                if(next==null) {
                    val ledger=history.readTieredLedger(ownerKey,owner.athleteId,scope,
                        selection.slotId,selection.tier,selection.absoluteWeek)
                        as? NativeOwnerTieredLedgerResult.Review
                        ?:return@withOwnerTransaction deny(NativeSavedNamedReviewRefusal.HISTORY_CHANGED)
                    if(ledger.manifestRevision!=historyRevision || ledger.exposures.isNotEmpty())
                        return@withOwnerTransaction deny(NativeSavedNamedReviewRefusal.HISTORY_CHANGED)
                }
                val request=TieredLinearRequest(selection.nextWorkoutId,scope,selection.tier,
                    TieredLinearState(0,selection.initialLoad),true,emptyList(),stock,
                    selection.maximumAuthorizedIncrease,
                    selected.sets.count{it.prescription.type==DraftSetType.WORKING},sourceSlotId=selection.slotId)
                CustomTieredInitialWeekEvidence(CustomNamedPercentageWeekKey(initial.blockId,
                    initial.weekInBlock,selection.weekday,selection.slotId),request)
            } else null
            val audit=CustomCycleCoachAuditDomain.reviewSpecialistForReview(candidate,source.setup,
                source.catalog,inputs,stamp,evidence,listOfNotNull(tierEvidence))
            if(audit.status!=CustomCoachAuditStatus.READY_FOR_ATHLETE_REVIEW ||
                audit.issues.isNotEmpty() || audit.auditedCycleRevision!=candidate.revision ||
                audit.structuralReview?.verifiedDraft!=candidate)
                return@withOwnerTransaction deny(NativeSavedNamedReviewRefusal.FULL_CYCLE_AUDIT_FAILED,audit)
            val nextCandidate=if(next!=null) {
                val receipt=initial
                    ?:return@withOwnerTransaction deny(NativeSavedNamedReviewRefusal.NO_CONFIRMED_DRAFT)
                val initialRequest=tierEvidence?.request
                    ?:return@withOwnerTransaction deny(NativeSavedNamedReviewRefusal.NO_CONFIRMED_DRAFT)
                val selection=receipt.selection
                val block=candidate.blocks.single{it.blockId==receipt.blockId}
                if(!id.matches(next.nextWorkoutId) ||
                    next.absoluteWeek!=selection.absoluteWeek+1 ||
                    receipt.weekInBlock>=block.weeks ||
                    next.nextWorkoutId==selection.nextWorkoutId ||
                    timeline.completions.any{it.manifest.workoutId==next.nextWorkoutId} ||
                    census.sessions.any{it.workoutId==next.nextWorkoutId || !it.completed ||
                        (it.cycleId==cycleId && it.absoluteWeek>=next.absoluteWeek)})
                    return@withOwnerTransaction deny(NativeSavedNamedReviewRefusal.PROTECTED_WEEK)
                if(census.sessions.singleOrNull{it.workoutId==selection.nextWorkoutId &&
                    it.cycleId==cycleId && it.absoluteWeek==selection.absoluteWeek &&
                    it.weekday==selection.weekday && it.issued && it.completed}==null)
                    return@withOwnerTransaction deny(NativeSavedNamedReviewRefusal.HISTORY_CHANGED)
                val origin=outstanding.preparedOrigin(ownerKey,selection.nextWorkoutId)
                    ?:return@withOwnerTransaction deny(NativeSavedNamedReviewRefusal.HISTORY_CHANGED)
                val exact=(CustomWorkoutSnapshotDomain.reviewSpecialistForConsent(candidate,
                    source.setup,source.catalog,source.revision,inputs,evidence,listOf(tierEvidence),
                    selection.absoluteWeek,selection.weekday)
                    as? CustomWorkoutSnapshotResult.Preview)?.snapshot
                    ?:return@withOwnerTransaction deny(NativeSavedNamedReviewRefusal.FULL_CYCLE_AUDIT_FAILED)
                if(origin.authority!=NativeCustomPreparationAuthority.TIERED_INITIAL ||
                    origin.consentRevision!=prior.recordRevision ||
                    origin.consentFingerprint!=prior.fingerprintSha256 || origin.snapshot!=exact)
                    return@withOwnerTransaction deny(NativeSavedNamedReviewRefusal.STALE_PRIOR_CONSENT)
                val ledger=history.readTieredLedger(ownerKey,owner.athleteId,initialRequest.scope,
                    selection.slotId,selection.tier,next.absoluteWeek)
                    as? NativeOwnerTieredLedgerResult.Review
                    ?:return@withOwnerTransaction deny(NativeSavedNamedReviewRefusal.HISTORY_CHANGED)
                if(ledger.manifestRevision!=timeline.manifestRevision ||
                    ledger.exposures.singleOrNull()?.workoutId!=selection.nextWorkoutId)
                    return@withOwnerTransaction deny(NativeSavedNamedReviewRefusal.HISTORY_CHANGED)
                val request=initialRequest.copy(nextWorkoutId=next.nextWorkoutId,
                    completed=ledger.exposures,recovery=next.recovery)
                val numeric=TieredLinearProgressionDomain.review(request) as? TieredLinearResult.Candidate
                    ?:return@withOwnerTransaction deny(NativeSavedNamedReviewRefusal.HISTORY_CHANGED)
                val proposed=CustomPercentageDraftDomain.reviewTiered(
                    CustomPercentageDraftSource(candidate,stamp,receipt.blockId,
                        receipt.weekInBlock+1,selection.weekday,selection.slotId),request)
                    as? CustomPercentageDraftResult.Proposed
                    ?:return@withOwnerTransaction deny(NativeSavedNamedReviewRefusal.FULL_CYCLE_AUDIT_FAILED)
                // Audit the newly proposed full cycle, not only the signed
                // initial recipe. Bind the authenticated next-week proof and
                // the existing initial evidence to the proposed revision.
                val proposedCycle=proposed.proposal.draft
                val nextEvidence=CustomTieredTransitionWeekEvidence(
                    CustomNamedPercentageWeekKey(receipt.blockId,receipt.weekInBlock+1,
                        selection.weekday,selection.slotId),request)
                val transitionAudit=CustomCycleCoachAuditDomain.reviewSpecialistForReview(
                    proposedCycle,source.setup,source.catalog,inputs,
                    stamp.copy(cycleRevision=proposedCycle.revision),evidence,
                    listOf(checkNotNull(tierEvidence)),listOf(nextEvidence))
                if(transitionAudit.status!=CustomCoachAuditStatus.READY_FOR_ATHLETE_REVIEW ||
                    transitionAudit.issues.isNotEmpty() ||
                    transitionAudit.auditedCycleRevision!=proposedCycle.revision ||
                    transitionAudit.structuralReview?.verifiedDraft!=proposedCycle ||
                    transitionAudit.coachingPolicyRevisions!=prior.policyReferences)
                    return@withOwnerTransaction deny(
                        NativeSavedNamedReviewRefusal.FULL_CYCLE_AUDIT_FAILED,transitionAudit)
                NativeTieredNextCandidate(next,request,numeric,proposed,ledger.manifestRevision,
                    transitionAudit)
            } else null
            if(identity.signedInIdentity()!=owner)
                return@withOwnerTransaction deny(NativeSavedNamedReviewRefusal.NOT_AUTHORIZED)
            if(signed) {
                if(audit.coachingPolicyRevisions!=prior.policyReferences)
                    return@withOwnerTransaction deny(NativeSavedNamedReviewRefusal.STALE_PRIOR_CONSENT)
                NativeSavedNamedReview.Signed(candidate,stamp,receipts.toList(),prior,
                    evidence.toList(),inputs,audit,timeline.manifestRevision,initial,tierEvidence,nextCandidate)
            } else NativeSavedNamedReview.Ready(candidate,stamp,receipts.toList(),prior,evidence.toList(),inputs,audit,initial,tierEvidence)
        }}.getOrElse{deny(NativeSavedNamedReviewRefusal.STORAGE_UNAVAILABLE)}
    }
}
