package io.github.extracteddeath.pursuitiron.rebuild

/** Exactly one arithmetic policy for authored custom working, rest and
 * optional amplifier time, shared by the reviewed short-session planner and
 * the live Swap / UndoSwap journal. This counts KNOWN seconds only; missing
 * follow-up active time remains unknown and must be separately declared
 * before a short-session fit can be claimed.
 *
 * This is not a license to change an issued set or approve equipment.
 */
object CustomWorkoutTimingDomain {
    fun setSeconds(set:CustomAuthoredSet):Long? {
        if(set.activeSeconds !in 1..3600 || set.restSeconds !in 0..3600)
            return null
        val amplifier=set.amplifier
        if(amplifier!=null && (
                amplifier.followUpRestSeconds !in 0..3600 ||
                !AdvancedAmplifierProtocolDomain.validStagedFollowUps(amplifier) ||
                amplifier.followUpActiveSeconds?.let{it !in 1..3600}==true))
            return null
        return set.activeSeconds.toLong()+set.restSeconds.toLong()+
            (amplifier?.followUpRestSeconds?.toLong()
                ?.times(amplifier.followUpRounds) ?:0L)+
            (amplifier?.followUpActiveSeconds?.toLong() ?:0L)
    }

    fun totalSeconds(sets:Iterable<CustomAuthoredSet>,
        maximumSeconds:Long=10_000_000L):Long? {
        if(maximumSeconds<1L)return null
        var total=0L
        var count=0
        for(set in sets) {
            if(++count>256)return null
            val seconds=setSeconds(set) ?:return null
            // Never use an overflowing sum to declare a tiny session.
            if(seconds>maximumSeconds-total)return null
            total+=seconds
        }
        return total.takeIf{count>0 && it in 1L..maximumSeconds}
    }
}
