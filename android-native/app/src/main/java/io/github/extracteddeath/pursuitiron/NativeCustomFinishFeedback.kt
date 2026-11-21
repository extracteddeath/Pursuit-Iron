package io.github.extracteddeath.pursuitiron

import io.github.extracteddeath.pursuitiron.nativecoachstore.NativeCustomFinishRefusal
import io.github.extracteddeath.pursuitiron.nativecoachstore.NativeCustomFinishResult

/**
 * Presentation-only description of a native custom Finish attempt.
 *
 * A lost acknowledgement is not proof that a disk transaction failed:
 * Finish may have committed before the result was lost. The actual owner,
 * journal replay and verified History remain the only completion authorities.
 * No message can retry, publish a PR or advance the training plan.
 */
internal object NativeCustomFinishFeedback {
    fun message(result:NativeCustomFinishResult?,
        historyVerified:Boolean):String? = when(result) {
        is NativeCustomFinishResult.Committed ->
            if(historyVerified)null else
                "Finish was saved, but History could not yet be verified. Reopen this workout and recover History before advancing."
        null ->
            "Finish status is unknown. Your workout may already be saved. Reopen it and verify History before trying Finish again."
        is NativeCustomFinishResult.Rejected -> when(result.reason) {
            NativeCustomFinishRefusal.NOT_AUTHORIZED,
            NativeCustomFinishRefusal.INVALID_SCOPE ->
                "The current account or workout could not be verified. Reopen the correct account and workout before finishing."
            NativeCustomFinishRefusal.NOT_ENROLLED ->
                "This account's History recording is not ready. Review the History setup before finishing."
            NativeCustomFinishRefusal.SESSION_NOT_READY,
            NativeCustomFinishRefusal.FINISH_NOT_READY ->
                "This workout was not ready to finish. Check the latest recorded sets and recover any pending entries."
            NativeCustomFinishRefusal.FOREIGN_PENDING_INTENT,
            NativeCustomFinishRefusal.FINISH_EVENT_MISMATCH,
            NativeCustomFinishRefusal.HISTORY_NOT_READY,
            NativeCustomFinishRefusal.JOURNAL_UNAVAILABLE,
            NativeCustomFinishRefusal.MANIFEST_REJECTED,
            NativeCustomFinishRefusal.STORAGE_UNAVAILABLE ->
                "Finish could not be verified. A save may have completed. Reopen the workout and recover History before retrying."
        }
    }
}
