package io.github.extracteddeath.pursuitiron

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
}
