package io.github.extracteddeath.pursuitiron

import io.github.extracteddeath.pursuitiron.nativecoachstore.NativeCustomFinishRefusal
import io.github.extracteddeath.pursuitiron.nativecoachstore.NativeCustomFinishResult

/**
 * Finish confirmation is consent to one exact, durably observed journal
 * revision. A late correction, skip, machine change, or recovery refresh
 * must require the athlete to review the completed workout again.
 *
 * This is presentation authorization only. The journal owner still performs
 * the authoritative revision-checked Finish transaction and History handoff.
 */
internal data class NativeFinishReviewToken(
    val workoutId:String,
    val revision:Long
)

internal object NativeFinishReviewPolicy {
    fun canCommit(
        reviewed:NativeFinishReviewToken?,
        workoutId:String,
        currentRevision:Long?,
        eligible:Boolean
    ):Boolean =
        reviewed!=null && reviewed.workoutId==workoutId &&
            currentRevision!=null && reviewed.revision==currentRevision &&
            eligible

    /** One Finish review/presentation authority, never a History writer.
     * Missing acknowledgement does NOT mean the durable Finish rolled back.
     * The owner-verified journal and History remain authoritative. */
    fun customFinishMessage(result:NativeCustomFinishResult?,
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

