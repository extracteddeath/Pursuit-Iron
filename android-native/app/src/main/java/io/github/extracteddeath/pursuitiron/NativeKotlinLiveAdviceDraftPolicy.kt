package io.github.extracteddeath.pursuitiron

import io.github.extracteddeath.pursuitiron.rebuild.DraftSetLoad
import io.github.extracteddeath.pursuitiron.rebuild.RepBand

/**
 * A live coach reviews the durable, issued workout, never an unfinished
 * editor input. Once an athlete edits this set's load OR rep entry, presenting
 * a fresh-looking suggestion computed from the original issued values would
 * be misleading. Hide that advisory UI until the set is recorded and the
 * owner can re-review actual evidence. Do NOT alter or clear the typed draft.
 *
 * The selected physical machine is part of the draft's caller-owned key.
 * This is only a display gate, not an alternate coaching or number policy.
 */
internal object NativeKotlinLiveAdviceDraftPolicy {
    fun hasUnsubmittedChange(
        issuedLoad:DraftSetLoad,
        issuedReps:RepBand,
        selectedMachineId:String?,
        draft:CurrentWorkoutEditorValue?
    ):Boolean {
        if(draft==null)return false
        val original=initialMachineScopedLoad(issuedLoad,null,selectedMachineId)
        return draft.weight!=original ||
            draft.reps!=issuedReps.minimum.toString()
    }
}
