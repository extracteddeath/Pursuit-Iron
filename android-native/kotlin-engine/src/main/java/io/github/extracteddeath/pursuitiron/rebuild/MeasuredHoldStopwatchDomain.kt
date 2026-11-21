package io.github.extracteddeath.pursuitiron.rebuild

/**
 * A manually controlled stopwatch for SECOND-measured holds.
 * It supplies an optional draft input, NEVER a performed set or
 * authenticated measurement. The athlete must tap "Use measured seconds"
 * then explicitly save the resulting actual using the native journal.
 *
 * A monotonic uptime clock measures the effort. An independently saved
 * boot-epoch anchor makes a process-death/reboot or large wall-clock
 * adjustment fail closed rather than manufacturing a many-hour hold.
 */
data class MeasuredHoldStopwatchState(
    val startedMonotonicMs:Long?=null,
    val approximateBootEpochMs:Long?=null,
    val stoppedElapsedMs:Long?=null
) {
    val running:Boolean get()=startedMonotonicMs!=null
}

object MeasuredHoldStopwatchDomain {
    const val MAX_SECONDS=3600
    private const val MAX_ELAPSED_MS=3_600_999L
    private const val MAX_BOOT_DRIFT_MS=60_000L

    fun start(nowMonotonicMs:Long,nowEpochMs:Long):
        MeasuredHoldStopwatchState? {
        if(nowMonotonicMs<0 || nowEpochMs<0 ||
            nowEpochMs<nowMonotonicMs)return null
        return MeasuredHoldStopwatchState(
            startedMonotonicMs=nowMonotonicMs,
            approximateBootEpochMs=nowEpochMs-nowMonotonicMs)
    }

    fun elapsedMs(state:MeasuredHoldStopwatchState,
        nowMonotonicMs:Long,nowEpochMs:Long):Long? {
        val started=state.startedMonotonicMs
            ?:return state.stoppedElapsedMs?.takeIf{
                it in 0..MAX_ELAPSED_MS
            }
        val anchor=state.approximateBootEpochMs ?:return null
        if(state.stoppedElapsedMs!=null ||
            started<0 || anchor<0 ||
            nowMonotonicMs<started || nowEpochMs<nowMonotonicMs)
            return null
        val boot=nowEpochMs-nowMonotonicMs
        // Avoid signed-Long overflow on corrupted restored UI state.
        if(boot<0 || (boot>anchor &&
                boot-anchor>MAX_BOOT_DRIFT_MS) ||
            (boot<anchor && anchor-boot>MAX_BOOT_DRIFT_MS))
            return null
        return (nowMonotonicMs-started).takeIf{it in 0..MAX_ELAPSED_MS}
    }

    fun stop(state:MeasuredHoldStopwatchState,
        nowMonotonicMs:Long,nowEpochMs:Long):
        MeasuredHoldStopwatchState? {
        if(!state.running)return null
        val measured=elapsedMs(state,nowMonotonicMs,nowEpochMs)
            ?:return null
        return MeasuredHoldStopwatchState(stoppedElapsedMs=measured)
    }

    /** Truncate fractional seconds; NEVER overstate a measured hold. */
    fun usableSeconds(state:MeasuredHoldStopwatchState):Int? {
        if(state.running)return null
        // Validate the original Long before narrowing. Corrupt restored
        // milliseconds can otherwise wrap to a plausible small Int, while
        // the display correctly refuses the very same elapsed value.
        val elapsed=state.stoppedElapsedMs?.takeIf{
            it in 0..MAX_ELAPSED_MS
        } ?:return null
        return (elapsed/1000L).toInt().takeIf{it in 1..MAX_SECONDS}
    }

    fun display(state:MeasuredHoldStopwatchState,
        nowMonotonicMs:Long,nowEpochMs:Long):String? {
        val millis=elapsedMs(state,nowMonotonicMs,nowEpochMs) ?:return null
        return (millis/60_000).toString().padStart(2,'0')+":"+
            ((millis/1_000)%60).toString().padStart(2,'0')+"."+
            ((millis%1_000)/100).toString()
    }
}
