package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.math.BigDecimal

/** Explicit reference entered by the athlete for review, in the current
 * verified gym unit. No inferred max and no caller-supplied stock/history. */
data class NativeNamedPercentageSelection(
    val ownerKey:String,
    val cycleId:String,
    val absoluteWeek:Int,
    val weekday:Int,
    val slotId:String,
    val scheme:NamedPercentageScheme,
    val tier:PercentageTier,
    val dayRole:PercentageDayRole,
    val referenceKind:PercentageReferenceKind,
    val reference:BigDecimal
)

/** Explicit first GZCLP entry at stage zero. No hidden restart or inferred
 * training max; the next ID and maximum earned increment are reviewed. */
data class NativeTieredInitialSelection(
    val ownerKey:String,val cycleId:String,val absoluteWeek:Int,val weekday:Int,
    val slotId:String,val nextWorkoutId:String,val tier:PercentageTier,
    val initialLoad:BigDecimal,val maximumAuthorizedIncrease:BigDecimal
)

sealed interface NativeTieredInitialReview {
    data class Ready(val selection:NativeTieredInitialSelection,
        val source:CustomPercentageDraftSource,val request:TieredLinearRequest,
        val proposal:CustomPercentageDraftResult.Proposed,val consentRevision:Long,
        val historyRevision:Long,val machineRosterRevision:Long):NativeTieredInitialReview {
        val maySaveProgram:Boolean get()=false
        val mayIssueWorkout:Boolean get()=false
        val mayModifyHistory:Boolean get()=false
    }
    data class Rejected(val reason:NativeNamedPercentageRefusal):NativeTieredInitialReview
}

enum class NativeNamedPercentageRefusal {
    INVALID_SELECTION, NOT_AUTHORIZED, CURRENT_CONSENT_REQUIRED,
    SOURCE_OR_STOCK_UNAVAILABLE, HISTORY_UNVERIFIED, PROTECTED_WEEK,
    RECIPE_REFUSED, CONFIRMATION_REQUIRED, REVIEW_CHANGED, DRAFT_SAVE_REJECTED, STORAGE_UNAVAILABLE
}

sealed interface NativeNamedPercentageReview {
    data class Rejected(val reason:NativeNamedPercentageRefusal):NativeNamedPercentageReview
    data class Ready(
        val selection:NativeNamedPercentageSelection,
        val source:CustomPercentageDraftSource,
        val request:NamedPercentageRequest,
        val proposal:CustomPercentageDraftResult.Proposed,
        val consentRevision:Long,
        val historyRevision:Long,
        val machineRosterRevision:Long
    ):NativeNamedPercentageReview {
        val maySaveProgram:Boolean get()=false
        val mayIssueWorkout:Boolean get()=false
    }
}

sealed interface NativeNamedDraftAcceptance {
    data class Saved(val revision:Long,val receipt:NativeNamedPercentageDraftReceipt):
        NativeNamedDraftAcceptance {
        val isSignedOff:Boolean get()=false
        val mayIssueWorkout:Boolean get()=false
    }
    data class Rejected(val reason:NativeNamedPercentageRefusal):NativeNamedDraftAcceptance
}

sealed interface NativeNamedBatchReview {
    data class Ready(val selections:List<NativeNamedPercentageSelection>,
        val individual:List<NativeNamedPercentageReview.Ready>,
        val draft:CustomTrainingCycle,
        val receipts:List<NativeNamedPercentageDraftReceipt>):NativeNamedBatchReview {
        val mayIssueWorkout:Boolean get()=false
        val maySaveProgram:Boolean get()=false
    }
    data class Rejected(val reason:NativeNamedPercentageRefusal):NativeNamedBatchReview
}
sealed interface NativeNamedBatchAcceptance {
    data class Saved(val revision:Long,val receipts:List<NativeNamedPercentageDraftReceipt>):
        NativeNamedBatchAcceptance {
        val mayIssueWorkout:Boolean get()=false
        val isSignedOff:Boolean get()=false
    }
    data class Rejected(val reason:NativeNamedPercentageRefusal):NativeNamedBatchAcceptance
}

/**
 * Fixed named percentage authoring review using the existing owner-fenced
 * draft, signoff, physical-stock selector, mixed history and outstanding
 * workout inventory. No new numerical or historical authority.
 *
 * This is an explicit athlete edit, not adaptive coaching. It works without
 * enabling coaching, but cannot change a prepared or already observed week.
 * Reconfirmation is read only until the dedicated named full-cycle audit,
 * durable approval and execution UI are integrated.
 */
class NativeKotlinNamedPercentageDraftReview(
    private val fence:NativeKotlinApprovalRevisionFence,
    private val identity:NativeKotlinCurrentIdentity,
    private val drafts:NativeCustomCycleDraftFileStore,
    private val sources:NativeKotlinCustomAuthoringSourceFileStore,
    private val signoffs:NativeKotlinCustomCycleSignoffFileStore,
    private val preflight:NativeKotlinCustomWorkoutPreflight,
    private val history:NativeKotlinMixedHistoryReadCoordinator,
    private val outstanding:NativeKotlinCustomOutstandingWorkoutInventory,
    private val machineRosters:NativeKotlinMachineRosterFileStore?=null
) {
    private val id=Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,79}$")

    private data class SourceSelection(val ownerKey:String,val cycleId:String,
        val absoluteWeek:Int,val weekday:Int,val slotId:String)
    private sealed interface SourceReview {
        data class Rejected(val reason:NativeNamedPercentageRefusal):SourceReview
        data class Ready(
            val source:CustomPercentageDraftSource,val scope:BasicProgressionScope,
            val stock:VerifiedLoadStock,val budget:Int,val blockWeeks:Int,
            val consentRevision:Long,val historyRevision:Long,val rosterRevision:Long,
            val knownWorkoutIds:Set<String>
        ):SourceReview
    }

    /** Shared authority for fixed and tiered specialist reference previews.
     * The caller supplies identities, never a stock or history receipt. */
    private fun reviewSource(s:SourceSelection):SourceReview {
        fun deny(reason:NativeNamedPercentageRefusal)=SourceReview.Rejected(reason)
        if(!id.matches(s.ownerKey)||!id.matches(s.cycleId)||!id.matches(s.slotId)||
            s.absoluteWeek !in 1..104 || s.weekday !in 0..6)
            return deny(NativeNamedPercentageRefusal.INVALID_SELECTION)
        val owner=identity.signedInIdentity()?.takeIf{it.ownerKey==s.ownerKey}
            ?:return deny(NativeNamedPercentageRefusal.NOT_AUTHORIZED)
        val accepted=(signoffs.recoverReviewedInputs(s.ownerKey,s.cycleId)
            as? NativeCustomSavedCoachReview.Ready)
            ?:return deny(NativeNamedPercentageRefusal.CURRENT_CONSENT_REQUIRED)
        val approved=(preflight.inspect(s.ownerKey,s.cycleId,s.absoluteWeek,s.weekday,
            accepted.inputs) as? NativeCustomPreflightResult.ReviewedPreview)
            ?:return deny(NativeNamedPercentageRefusal.CURRENT_CONSENT_REQUIRED)
        val cycle=(drafts.read(s.ownerKey,s.cycleId)
            as? NativeCustomDraftReadResult.Found)?.draft
            ?:return deny(NativeNamedPercentageRefusal.CURRENT_CONSENT_REQUIRED)
        val gymSource=(sources.read(s.ownerKey)
            as? NativeAuthoringSourceRead.Found)?.source
            ?:return deny(NativeNamedPercentageRefusal.SOURCE_OR_STOCK_UNAVAILABLE)
        val next=approved.session
        val gym=gymSource.setup.reviewed?.gym
            ?:return deny(NativeNamedPercentageRefusal.SOURCE_OR_STOCK_UNAVAILABLE)
        if(accepted.receipt!=approved.receipt ||
            owner.athleteId!=cycle.athleteId || owner.athleteId!=gymSource.athleteId ||
            owner.athleteId!=next.stamp.athleteId ||
            next.stamp.cycleId!=cycle.cycleId || next.stamp.cycleRevision!=cycle.revision ||
            next.stamp.gymCatalogSourceRevision!=gymSource.revision ||
            next.stamp.gymId!=gym.gymId || next.absoluteWeek!=s.absoluteWeek ||
            next.weekday!=s.weekday)
            return deny(NativeNamedPercentageRefusal.CURRENT_CONSENT_REQUIRED)
        val block=cycle.blocks.singleOrNull{it.blockId==next.blockId}
            ?:return deny(NativeNamedPercentageRefusal.CURRENT_CONSENT_REQUIRED)
        val selected=next.slots.singleOrNull{it.slotId==s.slotId}
            ?:return deny(NativeNamedPercentageRefusal.INVALID_SELECTION)
        val roster=if(machineRosters==null)null else
            (machineRosters.read(s.ownerKey) as? NativeMachineRosterResult.Saved)?.snapshot
                ?:return deny(NativeNamedPercentageRefusal.SOURCE_OR_STOCK_UNAVAILABLE)
        if(roster!=null && roster.ownerId!=s.ownerKey)
            return deny(NativeNamedPercentageRefusal.SOURCE_OR_STOCK_UNAVAILABLE)
        val physical=NativeKotlinCustomSelectedStockDomain.resolve(gymSource,selected,roster)
            ?:return deny(NativeNamedPercentageRefusal.SOURCE_OR_STOCK_UNAVAILABLE)
        val stock=physical.stock
            ?:return deny(NativeNamedPercentageRefusal.SOURCE_OR_STOCK_UNAVAILABLE)
        // An unbound slot must be explicitly bound before using a roster
        // default as a signed percentage progression identity.
        if(physical.machineInstanceId!=selected.machineInstanceId)
            return deny(NativeNamedPercentageRefusal.SOURCE_OR_STOCK_UNAVAILABLE)
        val timeline=(history.read(s.ownerKey,owner.athleteId)
            as? NativeKotlinMixedHistoryReadResult.Review)?.timeline
            ?:return deny(NativeNamedPercentageRefusal.HISTORY_UNVERIFIED)
        val completed=timeline.completions
            .filterIsInstance<NativeKotlinMixedHistoryCompletion.Custom>()
            .map{it.manifest.workoutId}.toSet()
        val census=(outstanding.inspect(s.ownerKey,owner.athleteId,completed)
            as? NativeCustomInventoryReview.Reviewed)
            ?:return deny(NativeNamedPercentageRefusal.HISTORY_UNVERIFIED)
        if(NativeFutureWeekProposalInventoryPolicy.blocks(census.sessions,s.cycleId,s.absoluteWeek) ||
            census.sessions.any{it.cycleId==s.cycleId && it.absoluteWeek>=s.absoluteWeek})
            return deny(NativeNamedPercentageRefusal.PROTECTED_WEEK)
        val scope=BasicProgressionScope(owner.athleteId,cycle.cycleId,gym.gymId,
            block.blockId,block.blockId+":d"+s.weekday,selected.exerciseId,
            selected.machineInstanceId,stock.unit,stock.basis)
        val source=CustomPercentageDraftSource(cycle,next.stamp,block.blockId,
            next.weekInBlock,s.weekday,s.slotId)
        if(identity.signedInIdentity()!=owner)
            return deny(NativeNamedPercentageRefusal.NOT_AUTHORIZED)
        return SourceReview.Ready(source,scope,stock,
            selected.sets.count{it.prescription.type==DraftSetType.WORKING},block.weeks,
            approved.receipt.recordRevision,timeline.manifestRevision,roster?.revision?:0L,
            (timeline.completions.map{it.manifest.workoutId}+census.sessions.map{it.workoutId}).toSet())
    }

    private fun evaluate(s:NativeNamedPercentageSelection):NativeNamedPercentageReview {
        fun deny(reason:NativeNamedPercentageRefusal)=NativeNamedPercentageReview.Rejected(reason)
        if(s.scheme==NamedPercentageScheme.GZCLP || s.reference<=BigDecimal.ZERO ||
            !KotlinSetNumberAuthorityDomain.validPhysicalLoadValue(s.reference))
            return deny(NativeNamedPercentageRefusal.INVALID_SELECTION)
        val context=when(val checked=reviewSource(SourceSelection(s.ownerKey,s.cycleId,
            s.absoluteWeek,s.weekday,s.slotId))) {
            is SourceReview.Rejected -> return deny(checked.reason)
            is SourceReview.Ready -> checked
        }
        val request=NamedPercentageRequest(context.scope,s.scheme,s.tier,s.dayRole,s.referenceKind,
            s.reference,true,context.source.weekInBlock,context.blockWeeks,context.budget,context.stock)
        val proposed=CustomPercentageDraftDomain.review(context.source,request)
            as? CustomPercentageDraftResult.Proposed
            ?:return deny(NativeNamedPercentageRefusal.RECIPE_REFUSED)
        if(identity.signedInIdentity()!=NativeKotlinOwnerIdentity(s.ownerKey,context.scope.athleteId))
            return deny(NativeNamedPercentageRefusal.NOT_AUTHORIZED)
        return NativeNamedPercentageReview.Ready(s,context.source,request,proposed,
            context.consentRevision,context.historyRevision,context.rosterRevision)
    }

    /** First tier entry only. A reference may not replace an existing
     * history-dependent tier ledger. Later tier decisions require a durable
     * initial receipt and explicit transition approval, not this method. */
    private fun evaluateTieredInitial(s:NativeTieredInitialSelection):NativeTieredInitialReview {
        fun deny(reason:NativeNamedPercentageRefusal)=NativeTieredInitialReview.Rejected(reason)
        if(!id.matches(s.nextWorkoutId)||s.initialLoad<=BigDecimal.ZERO ||
            s.maximumAuthorizedIncrease<=BigDecimal.ZERO ||
            !KotlinSetNumberAuthorityDomain.validPhysicalLoadValue(s.initialLoad) ||
            !KotlinSetNumberAuthorityDomain.validPhysicalLoadValue(s.maximumAuthorizedIncrease))
            return deny(NativeNamedPercentageRefusal.INVALID_SELECTION)
        val context=when(val checked=reviewSource(SourceSelection(s.ownerKey,s.cycleId,
            s.absoluteWeek,s.weekday,s.slotId))) {
            is SourceReview.Rejected -> return deny(checked.reason)
            is SourceReview.Ready -> checked
        }
        if(s.nextWorkoutId in context.knownWorkoutIds)
            return deny(NativeNamedPercentageRefusal.PROTECTED_WEEK)
        val ledger=history.readTieredLedger(s.ownerKey,context.scope.athleteId,context.scope,
            s.slotId,s.tier,s.absoluteWeek) as? NativeOwnerTieredLedgerResult.Review
            ?:return deny(NativeNamedPercentageRefusal.HISTORY_UNVERIFIED)
        if(ledger.manifestRevision!=context.historyRevision)
            return deny(NativeNamedPercentageRefusal.HISTORY_UNVERIFIED)
        if(ledger.exposures.isNotEmpty())
            return deny(NativeNamedPercentageRefusal.PROTECTED_WEEK)
        val request=TieredLinearRequest(s.nextWorkoutId,context.scope,s.tier,
            TieredLinearState(0,s.initialLoad),true,ledger.exposures,context.stock,
            s.maximumAuthorizedIncrease,context.budget,sourceSlotId=s.slotId)
        val proposed=CustomPercentageDraftDomain.reviewTiered(context.source,request)
            as? CustomPercentageDraftResult.Proposed
            ?:return deny(NativeNamedPercentageRefusal.RECIPE_REFUSED)
        if(identity.signedInIdentity()!=NativeKotlinOwnerIdentity(s.ownerKey,context.scope.athleteId))
            return deny(NativeNamedPercentageRefusal.NOT_AUTHORIZED)
        return NativeTieredInitialReview.Ready(s,context.source,request,proposed,
            context.consentRevision,context.historyRevision,context.rosterRevision)
    }

    fun previewTieredInitial(selection:NativeTieredInitialSelection):NativeTieredInitialReview =
        runCatching{fence.withOwnerTransaction(selection.ownerKey){evaluateTieredInitial(selection)}}
            .getOrElse{NativeTieredInitialReview.Rejected(NativeNamedPercentageRefusal.STORAGE_UNAVAILABLE)}

    fun reconfirmTieredInitial(selection:NativeTieredInitialSelection,
        displayed:NativeTieredInitialReview.Ready,athleteConfirmed:Boolean):NativeTieredInitialReview {
        if(!athleteConfirmed)return NativeTieredInitialReview.Rejected(
            NativeNamedPercentageRefusal.CONFIRMATION_REQUIRED)
        return runCatching{fence.withOwnerTransaction(selection.ownerKey){
            when(val fresh=evaluateTieredInitial(selection)) {
                is NativeTieredInitialReview.Rejected -> fresh
                is NativeTieredInitialReview.Ready -> if(fresh==displayed)fresh
                    else NativeTieredInitialReview.Rejected(NativeNamedPercentageRefusal.REVIEW_CHANGED)
            }
        }}.getOrElse{NativeTieredInitialReview.Rejected(NativeNamedPercentageRefusal.STORAGE_UNAVAILABLE)}
    }

    fun preview(selection:NativeNamedPercentageSelection):NativeNamedPercentageReview =
        runCatching{fence.withOwnerTransaction(selection.ownerKey){evaluate(selection)}}
            .getOrElse{NativeNamedPercentageReview.Rejected(NativeNamedPercentageRefusal.STORAGE_UNAVAILABLE)}

    private fun receipt(fresh:NativeNamedPercentageReview.Ready):NativeNamedPercentageDraftReceipt {
        val scope=fresh.request.scope
        return NativeNamedPercentageDraftReceipt(fresh.selection,fresh.source.stamp,
            fresh.source.blockId,fresh.source.weekInBlock,scope.exerciseId,
            scope.machineInstanceId,scope.unit,scope.basis,fresh.consentRevision,
            fresh.historyRevision,fresh.machineRosterRevision)
    }

    /** Evaluate all amendments against one unchanged approved source, then
     * combine only their independently derived week overrides. Never save
     * a partial batch or require signing off each intermediate draft. */
    private fun evaluateBatch(selections:List<NativeNamedPercentageSelection>):NativeNamedBatchReview {
        fun deny(reason:NativeNamedPercentageRefusal)=NativeNamedBatchReview.Rejected(reason)
        if(selections.size !in 1..NativeNamedPercentageDraftReceiptCodec.MAX_RECEIPTS)
            return deny(NativeNamedPercentageRefusal.INVALID_SELECTION)
        val first=selections.first()
        if(selections.any{it.ownerKey!=first.ownerKey || it.cycleId!=first.cycleId} ||
            selections.map{Triple(it.absoluteWeek,it.weekday,it.slotId)}.distinct().size!=selections.size)
            return deny(NativeNamedPercentageRefusal.INVALID_SELECTION)
        val reviews=ArrayList<NativeNamedPercentageReview.Ready>()
        for(selection in selections) {
            val reviewed=evaluate(selection)
            if(reviewed is NativeNamedPercentageReview.Rejected)return deny(reviewed.reason)
            reviews+=reviewed as NativeNamedPercentageReview.Ready
        }
        val initial=reviews.first()
        val base=initial.source.cycle
        if(reviews.any{it.source.cycle!=base || it.source.stamp!=initial.source.stamp ||
            it.consentRevision!=initial.consentRevision || it.historyRevision!=initial.historyRevision ||
            it.machineRosterRevision!=initial.machineRosterRevision})
            return deny(NativeNamedPercentageRefusal.REVIEW_CHANGED)
        val overrides=base.weekOverrides.toMutableMap()
        for(review in reviews) {
            val src=review.source
            val edit=review.proposal.proposal.draft.weekOverrides[src.blockId].orEmpty()
                .singleOrNull{it.weekInBlock==src.weekInBlock && it.weekday==src.weekday &&
                    it.slotId==src.slotId}
                ?:return deny(NativeNamedPercentageRefusal.RECIPE_REFUSED)
            overrides[src.blockId]=overrides[src.blockId].orEmpty()+edit
        }
        val candidate=base.copy(revision=base.revision+1,weekOverrides=overrides.toMap())
        val receipts=reviews.map(::receipt)
        val evidence=reviews.map{review->
            val src=review.source
            CustomNamedPercentageWeekEvidence(CustomNamedPercentageWeekKey(src.blockId,
                src.weekInBlock,src.weekday,src.slotId),review.request)
        }
        if(!NativeNamedPercentageDraftReceiptCodec.validBatch(receipts,first.ownerKey,candidate) ||
            CustomNamedPercentageCycleAuditDomain.review(candidate,
                initial.source.stamp.copy(cycleRevision=candidate.revision),evidence)
                    !is CustomNamedPercentageCycleAudit.Verified)
            return deny(NativeNamedPercentageRefusal.RECIPE_REFUSED)
        return NativeNamedBatchReview.Ready(selections.toList(),reviews.toList(),candidate,receipts)
    }

    fun previewBatch(selections:List<NativeNamedPercentageSelection>):NativeNamedBatchReview {
        val input=selections.toList()
        val first=input.firstOrNull()?:return NativeNamedBatchReview.Rejected(
            NativeNamedPercentageRefusal.INVALID_SELECTION)
        return runCatching{fence.withOwnerTransaction(first.ownerKey){evaluateBatch(input)}}
            .getOrElse{NativeNamedBatchReview.Rejected(NativeNamedPercentageRefusal.STORAGE_UNAVAILABLE)}
    }

    fun acceptBatch(selections:List<NativeNamedPercentageSelection>,displayed:NativeNamedBatchReview.Ready,
        athleteConfirmed:Boolean):NativeNamedBatchAcceptance {
        fun deny(reason:NativeNamedPercentageRefusal)=NativeNamedBatchAcceptance.Rejected(reason)
        if(!athleteConfirmed)return deny(NativeNamedPercentageRefusal.CONFIRMATION_REQUIRED)
        val input=selections.toList()
        val first=input.firstOrNull()?:return deny(NativeNamedPercentageRefusal.INVALID_SELECTION)
        return runCatching{fence.withOwnerTransaction(first.ownerKey){
            val fresh=evaluateBatch(input)
            if(fresh is NativeNamedBatchReview.Rejected)return@withOwnerTransaction deny(fresh.reason)
            fresh as NativeNamedBatchReview.Ready
            if(fresh!=displayed)return@withOwnerTransaction deny(NativeNamedPercentageRefusal.REVIEW_CHANGED)
            val prior=fresh.individual.first().source.cycle.revision
            when(drafts.saveReviewedNamedBatch(first.ownerKey,first.cycleId,prior,fresh.draft,fresh.receipts)) {
                is NativeCustomDraftWriteResult.Committed ->
                    NativeNamedBatchAcceptance.Saved(fresh.draft.revision,fresh.receipts)
                else -> deny(NativeNamedPercentageRefusal.DRAFT_SAVE_REJECTED)
            }
        }}.getOrElse{deny(NativeNamedPercentageRefusal.STORAGE_UNAVAILABLE)}
    }

    /** Reopen all authoritative inputs, compare the complete displayed
     * proposal, then atomically save only the next UNSIGNED draft revision.
     * The reference receipt survives a cold read; a generic edit clears it.
     * The previous cycle signoff is necessarily stale after this write. */
    fun acceptDraft(selection:NativeNamedPercentageSelection,
        displayed:NativeNamedPercentageReview.Ready,
        athleteConfirmed:Boolean):NativeNamedDraftAcceptance {
        if(!athleteConfirmed)return NativeNamedDraftAcceptance.Rejected(
            NativeNamedPercentageRefusal.CONFIRMATION_REQUIRED)
        return runCatching {
            fence.withOwnerTransaction(selection.ownerKey) {
                val current=reconfirm(selection,displayed,true)
                if(current is NativeNamedPercentageReview.Rejected)
                    return@withOwnerTransaction NativeNamedDraftAcceptance.Rejected(current.reason)
                val fresh=current as NativeNamedPercentageReview.Ready
                val receipt=receipt(fresh)
                val candidate=fresh.proposal.proposal.draft
                when(drafts.saveReviewedNamed(selection.ownerKey,selection.cycleId,
                    fresh.source.cycle.revision,candidate,receipt)) {
                    is NativeCustomDraftWriteResult.Committed ->
                        NativeNamedDraftAcceptance.Saved(candidate.revision,receipt)
                    else -> NativeNamedDraftAcceptance.Rejected(
                        NativeNamedPercentageRefusal.DRAFT_SAVE_REJECTED)
                }
            }
        }.getOrElse{NativeNamedDraftAcceptance.Rejected(NativeNamedPercentageRefusal.STORAGE_UNAVAILABLE)}
    }

    fun reconfirm(selection:NativeNamedPercentageSelection,displayed:NativeNamedPercentageReview.Ready,
        athleteConfirmed:Boolean):NativeNamedPercentageReview {
        if(!athleteConfirmed)
            return NativeNamedPercentageReview.Rejected(NativeNamedPercentageRefusal.CONFIRMATION_REQUIRED)
        return runCatching {
            fence.withOwnerTransaction(selection.ownerKey) {
                val fresh=evaluate(selection)
                if(fresh !is NativeNamedPercentageReview.Ready)return@withOwnerTransaction fresh
                if(fresh!=displayed ||
                    CustomPercentageDraftRecheckDomain.recheck(displayed.proposal,displayed.request,
                        fresh.source,fresh.request,displayed.historyRevision,fresh.historyRevision,true)
                        !is CustomPercentageRecheckResult.Reconfirmed)
                    NativeNamedPercentageReview.Rejected(NativeNamedPercentageRefusal.REVIEW_CHANGED)
                else fresh
            }
        }.getOrElse{NativeNamedPercentageReview.Rejected(NativeNamedPercentageRefusal.STORAGE_UNAVAILABLE)}
    }
}
