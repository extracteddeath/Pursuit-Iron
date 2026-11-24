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

    private fun evaluate(s:NativeNamedPercentageSelection):NativeNamedPercentageReview {
        fun deny(reason:NativeNamedPercentageRefusal)=NativeNamedPercentageReview.Rejected(reason)
        if(!id.matches(s.ownerKey) || !id.matches(s.cycleId) || !id.matches(s.slotId) ||
            s.absoluteWeek !in 1..104 || s.weekday !in 0..6 ||
            s.scheme==NamedPercentageScheme.GZCLP || s.reference<=BigDecimal.ZERO ||
            !KotlinSetNumberAuthorityDomain.validPhysicalLoadValue(s.reference))
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
        // The athlete's supplied number is the proposed review reference,
        // not a durable confirmed maximum or approval to issue anything.
        val request=NamedPercentageRequest(scope,s.scheme,s.tier,s.dayRole,s.referenceKind,
            s.reference,true,next.weekInBlock,block.weeks,
            selected.sets.count{it.prescription.type==DraftSetType.WORKING},stock)
        val proposed=CustomPercentageDraftDomain.review(source,request)
            as? CustomPercentageDraftResult.Proposed
            ?:return deny(NativeNamedPercentageRefusal.RECIPE_REFUSED)
        if(identity.signedInIdentity()!=owner)
            return deny(NativeNamedPercentageRefusal.NOT_AUTHORIZED)
        return NativeNamedPercentageReview.Ready(s,source,request,proposed,
            approved.receipt.recordRevision,timeline.manifestRevision,roster?.revision?:0)
    }

    fun preview(selection:NativeNamedPercentageSelection):NativeNamedPercentageReview =
        runCatching{fence.withOwnerTransaction(selection.ownerKey){evaluate(selection)}}
            .getOrElse{NativeNamedPercentageReview.Rejected(NativeNamedPercentageRefusal.STORAGE_UNAVAILABLE)}

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
                val scope=fresh.request.scope
                val receipt=NativeNamedPercentageDraftReceipt(fresh.selection,fresh.source.stamp,
                    fresh.source.blockId,fresh.source.weekInBlock,scope.exerciseId,
                    scope.machineInstanceId,scope.unit,scope.basis,fresh.consentRevision,
                    fresh.historyRevision,fresh.machineRosterRevision)
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
