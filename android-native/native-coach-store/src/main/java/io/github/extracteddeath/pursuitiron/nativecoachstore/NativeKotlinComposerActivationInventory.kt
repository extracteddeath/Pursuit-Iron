package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.KotlinTrainingSourceRevision
import io.github.extracteddeath.pursuitiron.rebuild.KotlinWorkoutJournalState

/**
 * Composer sessions remain independently owned by their native journal
 * store. This read-only gate protects a custom future-block source change
 * from racing with a live Composer workout, even for another program.
 *
 * Completion is authoritative ONLY when a replay-verified issued journal
 * agrees with the complete, authenticated mixed-history manifest. A journal
 * reporting Finish before the manifest is committed is NOT completed.
 */
internal data class NativeComposerInventoryFact(
    val workoutId:String,
    val finishEventId:String?
)

internal enum class NativeComposerActivationInventoryRefusal {
    INVENTORY_NOT_VERIFIED, UNRESOLVED_COMPOSER_WORKOUT,
    JOURNAL_MANIFEST_CONFLICT
}
internal sealed interface NativeComposerActivationInventoryResult {
    data class Clear(val issuedCount:Int,val completedCount:Int):
        NativeComposerActivationInventoryResult {
        val mayActivate:Boolean get()=false
    }
    data class Rejected(val reason:NativeComposerActivationInventoryRefusal):
        NativeComposerActivationInventoryResult
}

/** No caller's 'finished' flag can substitute for a manifest entry. */
internal object NativeComposerActivationInventoryDomain {
    fun review(
        issued:List<NativeComposerInventoryFact>,
        verifiedCompleted:Map<String,String>
    ):NativeComposerActivationInventoryResult {
        fun reject(reason:NativeComposerActivationInventoryRefusal)=
            NativeComposerActivationInventoryResult.Rejected(reason)
        if(issued.map{it.workoutId}.distinct().size!=issued.size)
            return reject(NativeComposerActivationInventoryRefusal.JOURNAL_MANIFEST_CONFLICT)
        val journals=issued.associateBy{it.workoutId}
        if(verifiedCompleted.keys.any{it !in journals})
            return reject(NativeComposerActivationInventoryRefusal.JOURNAL_MANIFEST_CONFLICT)
        for(record in issued) {
            val manifestFinish=verifiedCompleted[record.workoutId]
            if(record.finishEventId!=null && manifestFinish!=record.finishEventId)
                return reject(NativeComposerActivationInventoryRefusal.JOURNAL_MANIFEST_CONFLICT)
            if(manifestFinish!=null && record.finishEventId!=manifestFinish)
                return reject(NativeComposerActivationInventoryRefusal.JOURNAL_MANIFEST_CONFLICT)
        }
        if(issued.any{it.finishEventId==null})
            return reject(NativeComposerActivationInventoryRefusal.UNRESOLVED_COMPOSER_WORKOUT)
        return NativeComposerActivationInventoryResult.Clear(
            issued.size,verifiedCompleted.size)
    }
}

/**
 * Must be called with the shared owner revision fence already held.
 * The existing journal inventory itself uses verified issued snapshots and
 * replay. Authorization is delegated to the native workout authority, not
 * a boolean constructed by an Android screen.
 */
internal class NativeKotlinComposerActivationInventory(
    private val journals:KotlinNativeJournalFileStore,
    private val authority:NativeKotlinWorkoutAuthority
) {
    fun inspect(
        ownerKey:String,athleteId:String,
        completed:List<NativeKotlinMixedHistoryCompletion.Composer>
    ):NativeComposerActivationInventoryResult {
        val verified=linkedMapOf<String,String>()
        for(entry in completed) {
            val id=entry.manifest.workoutId
            if(verified.put(id,entry.manifest.finishEventId)!=null)
                return NativeComposerActivationInventoryResult.Rejected(
                    NativeComposerActivationInventoryRefusal.JOURNAL_MANIFEST_CONFLICT)
        }
        val scan=journals.inventoryOwner(ownerKey,athleteId) {owner,source->
            owner==ownerKey && source.athleteId==athleteId &&
                authority.canAccessIssuedWorkout(owner,source)
        }
        if(scan !is NativeKotlinJournalInventoryResult.Verified)
            return NativeComposerActivationInventoryResult.Rejected(
                NativeComposerActivationInventoryRefusal.INVENTORY_NOT_VERIFIED)
        return NativeComposerActivationInventoryDomain.review(
            scan.journals.map {
                NativeComposerInventoryFact(it.snapshot.workoutId,it.finishedByEventId)
            },verified)
    }
}
