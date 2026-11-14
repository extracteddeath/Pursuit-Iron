package io.github.extracteddeath.pursuitiron

import io.github.extracteddeath.pursuitiron.nativecoachstore.*
import io.github.extracteddeath.pursuitiron.rebuild.*

/** Read-only entry projection. No approval, workout issuance or save grant. */
internal sealed interface NativeKotlinPlanEntry {
    data class Selected(
        val cycleId:String,
        val read:NativeCustomDraftReadResult,
        val source:NativeCustomAuthoringSnapshot?,
        val retainedUndo:CustomCycleDraftReceipt?,
        val undoUnavailable:Boolean=false
    ):NativeKotlinPlanEntry
    data class Programs(val read:NativeCustomDraftListResult):NativeKotlinPlanEntry
}

/**
 * Re-enter the athlete's selected program before considering the census.
 * The host runs all callbacks under its native owner revision fence. Prior
 * UI state identifies a task; only fresh owner-checked records supply data.
 * Undo survives only an exact draft AND full source match, and the canonical
 * builder must still accept that receipt. Pending reviews are never retained.
 */
internal object NativeKotlinPlanEntryReview {
    fun review(
        identity:NativeKotlinCurrentIdentity,
        visibleDraft:CustomTrainingCycle?,
        visibleSource:NativeCustomAuthoringSnapshot?,
        undo:CustomCycleDraftReceipt?,
        readSource:()->NativeAuthoringSourceRead,
        readDraft:(String)->NativeCustomDraftReadResult,
        listDrafts:()->NativeCustomDraftListResult
    ):NativeKotlinPlanEntry {
        fun unauthorized()=NativeKotlinPlanEntry.Programs(
            NativeCustomDraftListResult.Rejected(NativeCustomDraftRefusal.NOT_AUTHORIZED))
        val owner=runCatching{identity.signedInIdentity()}.getOrNull() ?:return unauthorized()
        val reviewed=forOwner(owner,visibleDraft,visibleSource,undo,readSource,readDraft,listDrafts)
        return if(runCatching{identity.signedInIdentity()}.getOrNull()==owner)reviewed
            else unauthorized()
    }

    private fun forOwner(
        owner:NativeKotlinOwnerIdentity,
        visibleDraft:CustomTrainingCycle?,
        visibleSource:NativeCustomAuthoringSnapshot?,
        undo:CustomCycleDraftReceipt?,
        readSource:()->NativeAuthoringSourceRead,
        readDraft:(String)->NativeCustomDraftReadResult,
        listDrafts:()->NativeCustomDraftListResult
    ):NativeKotlinPlanEntry {
        val sourceRead=readSource()
        if(sourceRead is NativeAuthoringSourceRead.Rejected &&
            sourceRead.reason==NativeAuthoringSourceRefusal.NOT_AUTHORIZED)
            return NativeKotlinPlanEntry.Programs(NativeCustomDraftListResult.Rejected(
                NativeCustomDraftRefusal.NOT_AUTHORIZED))
        val source=(sourceRead as? NativeAuthoringSourceRead.Found)?.source?.takeIf {
            it.ownerKey==owner.ownerKey && it.athleteId==owner.athleteId &&
                it.setup.reviewed?.athleteProfileId==owner.athleteId && it.revision>0
        }
        val selected=visibleDraft?.takeIf {
            it.athleteId==owner.athleteId && visibleSource?.ownerKey==owner.ownerKey &&
                visibleSource.athleteId==owner.athleteId
        }?.cycleId
        val cycleId=selected ?: when(val census=listDrafts()) {
            is NativeCustomDraftListResult.Ready -> census.drafts.singleOrNull()?.cycleId
                ?:return NativeKotlinPlanEntry.Programs(census)
            is NativeCustomDraftListResult.Rejected ->return NativeKotlinPlanEntry.Programs(census)
        }
        val read=readDraft(cycleId)
        val found=(read as? NativeCustomDraftReadResult.Found)?.draft
        if(found!=null && (found.cycleId!=cycleId || found.athleteId!=owner.athleteId))
            return NativeKotlinPlanEntry.Selected(cycleId,
                NativeCustomDraftReadResult.Rejected(NativeCustomDraftRefusal.NOT_AUTHORIZED),null,null)
        val retained=undo?.takeIf {
            found!=null && visibleDraft==found && source!=null && source==visibleSource &&
                (runCatching {CustomCycleBuilderDomain.undo(found,it)}.getOrNull()
                    is CustomCycleBuilderResult.Proposed)
        }
        return NativeKotlinPlanEntry.Selected(cycleId,read,source,retained,
            undoUnavailable=undo!=null && retained==null)
    }
}
