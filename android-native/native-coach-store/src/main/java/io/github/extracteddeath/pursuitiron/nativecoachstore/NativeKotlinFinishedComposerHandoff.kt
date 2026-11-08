package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.KotlinWorkoutJournalState

/**
 * Read-only post-Finish navigation gate for a generated workout.
 *
 * Its inputs MUST come from the authenticated native workout reader and the
 * independently owner-fenced, complete mixed-history reader. This compares
 * their exact saved source and finish receipts; a UI-local "finished" flag,
 * a pending transaction, or a history row for another day never suffices.
 * It does NOT issue or authorize a future workout or coaching adjustment.
 */
enum class NativeComposerFinishHandoffRefusal {
    NOT_FINISHED,
    VERIFIED_HISTORY_UNAVAILABLE,
    COMPLETION_NOT_IN_HISTORY,
    FINISH_RECEIPTS_MISMATCH
}

sealed interface NativeComposerFinishHandoffResult {
    data class Verified(
        val workoutId:String,
        val historyRevision:Long,
        val completionOrder:Long
    ): NativeComposerFinishHandoffResult {
        val canIssueNextWorkout:Boolean get()=false
    }
    data class Rejected(
        val reason:NativeComposerFinishHandoffRefusal
    ): NativeComposerFinishHandoffResult
}

object NativeKotlinFinishedComposerHandoff {
    fun inspect(
        journal:KotlinWorkoutJournalState?,
        history:NativeKotlinMixedHistoryReadResult
    ):NativeComposerFinishHandoffResult {
        fun reject(reason:NativeComposerFinishHandoffRefusal)=
            NativeComposerFinishHandoffResult.Rejected(reason)
        val finished=journal?.finishedByEventId
            ?:return reject(NativeComposerFinishHandoffRefusal.NOT_FINISHED)
        val timeline=(history as? NativeKotlinMixedHistoryReadResult.Review)
            ?.timeline ?:return reject(
                NativeComposerFinishHandoffRefusal.VERIFIED_HISTORY_UNAVAILABLE)
        val snapshot=journal.snapshot
        if(timeline.athleteId!=snapshot.source.athleteId)
            return reject(NativeComposerFinishHandoffRefusal.FINISH_RECEIPTS_MISMATCH)
        val matches=timeline.completions.filter{
            it.manifest.workoutId==snapshot.workoutId
        }
        if(matches.isEmpty())
            return reject(NativeComposerFinishHandoffRefusal.COMPLETION_NOT_IN_HISTORY)
        val completion=matches.singleOrNull()
            as? NativeKotlinMixedHistoryCompletion.Composer
            ?:return reject(NativeComposerFinishHandoffRefusal.FINISH_RECEIPTS_MISMATCH)
        val manifest=completion.manifest
        val evidence=completion.evidence
        if(manifest.finishEventId!=finished ||
            manifest.originalJournalRevision!=journal.revision ||
            manifest.storedReceiptId!=journal.storedIdentity.storageReceiptId ||
            evidence.workoutId!=snapshot.workoutId ||
            evidence.finishEventId!=finished ||
            evidence.journalRevision!=journal.revision ||
            evidence.source!=snapshot.source ||
            completion.dayIndex!=snapshot.dayIndex ||
            completion.purpose!=snapshot.purpose)
            return reject(NativeComposerFinishHandoffRefusal.FINISH_RECEIPTS_MISMATCH)
        return NativeComposerFinishHandoffResult.Verified(
            snapshot.workoutId,timeline.manifestRevision,
            manifest.completionOrder)
    }
}
