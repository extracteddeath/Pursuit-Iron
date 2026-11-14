package io.github.extracteddeath.pursuitiron

import io.github.extracteddeath.pursuitiron.nativecoachstore.*
import io.github.extracteddeath.pursuitiron.rebuild.*

/** A read locator bound to the athlete who saw the list, never a save grant. */
internal data class NativeKotlinProgramSelection(
    val owner:NativeKotlinOwnerIdentity,
    val cycleId:String,
    val listedRevision:Long
)

/** Read-only entry projection. No approval, workout issuance or save grant. */
internal sealed interface NativeKotlinPlanEntry {
    data class Selected(
        val cycleId:String,
        val read:NativeCustomDraftReadResult,
        val source:NativeCustomAuthoringSnapshot?,
        val retainedUndo:CustomCycleDraftReceipt?,
        val undoUnavailable:Boolean=false,
        val selectionUpdated:Boolean=false
    ):NativeKotlinPlanEntry
    data class Programs(val read:NativeCustomDraftListResult,
        val owner:NativeKotlinOwnerIdentity?=null):NativeKotlinPlanEntry
}

/**
 * Re-enter the athlete's selected program before considering the census.
 * The host runs all callbacks under its native owner revision fence. Prior
 * UI state identifies a task; only fresh owner-checked records supply data.
 * Undo survives only an exact draft AND full source match, and the canonical
 * builder must still accept that receipt. Pending reviews are never retained.
 */
internal object NativeKotlinPlanEntryReview {
    private fun unauthorized()=NativeKotlinPlanEntry.Programs(
        NativeCustomDraftListResult.Rejected(NativeCustomDraftRefusal.NOT_AUTHORIZED))

    /** Browse without replacing the open task or reading/mutating its policies. */
    fun programs(identity:NativeKotlinCurrentIdentity,
        listDrafts:()->NativeCustomDraftListResult):NativeKotlinPlanEntry.Programs {
        val owner=runCatching{identity.signedInIdentity()}.getOrNull() ?:return unauthorized()
        val listed=listDrafts()
        return if(runCatching{identity.signedInIdentity()}.getOrNull()==owner)
            NativeKotlinPlanEntry.Programs(listed,
                owner.takeIf{listed is NativeCustomDraftListResult.Ready})
        else unauthorized()
    }

    fun review(
        identity:NativeKotlinCurrentIdentity,
        visibleDraft:CustomTrainingCycle?,
        visibleSource:NativeCustomAuthoringSnapshot?,
        undo:CustomCycleDraftReceipt?,
        readSource:()->NativeAuthoringSourceRead,
        readDraft:(String)->NativeCustomDraftReadResult,
        listDrafts:()->NativeCustomDraftListResult,
        selection:NativeKotlinProgramSelection?=null
    ):NativeKotlinPlanEntry {
        val owner=runCatching{identity.signedInIdentity()}.getOrNull() ?:return unauthorized()
        if(selection!=null && selection.owner!=owner)return unauthorized()
        val reviewed=forOwner(owner,visibleDraft,visibleSource,undo,readSource,readDraft,listDrafts,selection)
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
        listDrafts:()->NativeCustomDraftListResult,
        selection:NativeKotlinProgramSelection?
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
        val selected=selection?.cycleId ?: visibleDraft?.takeIf {
            it.athleteId==owner.athleteId && visibleSource?.ownerKey==owner.ownerKey &&
                visibleSource.athleteId==owner.athleteId
        }?.cycleId
        val cycleId=selected ?: when(val census=listDrafts()) {
            is NativeCustomDraftListResult.Ready -> census.drafts.singleOrNull()?.cycleId
                ?:return NativeKotlinPlanEntry.Programs(census,owner)
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
            undoUnavailable=undo!=null && retained==null &&
                (selection==null || visibleDraft?.cycleId==cycleId),
            selectionUpdated=selection!=null && found!=null &&
                found.revision!=selection.listedRevision)
    }
}
