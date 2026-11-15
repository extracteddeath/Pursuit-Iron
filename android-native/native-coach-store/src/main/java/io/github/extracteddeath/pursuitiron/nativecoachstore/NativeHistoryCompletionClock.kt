package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.KotlinHistoryCompletionTime

/** The native transaction observes time once, before its durable Finish intent.
 * Recovery and duplicate acknowledgments use the saved observation instead.
 * An unavailable clock does not block saving a workout or invent a date. */
fun interface NativeHistoryCompletionClock {
    fun epochMillis():Long?

    companion object {
        val Undated=NativeHistoryCompletionClock{null}
        val System=NativeHistoryCompletionClock{java.lang.System.currentTimeMillis()}
    }
}

internal fun NativeHistoryCompletionClock.observe():Long?=
    runCatching{epochMillis()}.getOrNull()?.takeIf{KotlinHistoryCompletionTime.valid(it)}

internal val NativeHistoryWriteIntent.recordedCompletionTime:Long? get()=when(this) {
    is NativeHistoryWriteIntent.Finish -> completedAtEpochMillis
    is NativeHistoryWriteIntent.CustomFinish -> completedAtEpochMillis
    else -> null
}
