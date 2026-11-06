package io.github.extracteddeath.pursuitiron.nativecoachstore

/**
 * An expired COACH suggestion is not a reason to block an athlete's normal
 * workout. Its associated amended targets MUST be discarded and the exact,
 * currently signed-off AUTHORED session used instead.
 *
 * Only stale-history/consent decisions receive this fallback. A corrupt
 * receipt, storage failure, identity mismatch, or unverified active cycle
 * still fails closed. An already-prepared AMENDED workout remains frozen,
 * and issuance refuses it if its proof is no longer current.
 *
 * This must be shared by both preparation and issuance; otherwise a stale
 * approval might prepare a clean baseline but block its journal at Start.
 */
internal object NativeKotlinStaleWorkoutAdvicePolicy {
    fun rep(read:NativeCustomNextRepRead):NativeCustomNextRepRead =
        if(read==NativeCustomNextRepRead.Rejected(
                NativeCustomNextRepRefusal.STALE_CONSENT_OR_HISTORY))
            NativeCustomNextRepRead.Missing else read

    fun load(read:NativeCustomLoadRead):NativeCustomLoadRead =
        if(read==NativeCustomLoadRead.Rejected(
                NativeCustomLoadRefusal.STALE_HISTORY_OR_CONSENT))
            NativeCustomLoadRead.Missing else read
}
