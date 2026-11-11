package io.github.extracteddeath.pursuitiron.domain

/**
 * Shared, pure INPUT policy for native set entry, active corrections, and
 * historical corrections. No prescription, load increment, rounding or
 * history mutation is inferred from a UI gesture.
 */
object NativeSetEntryPolicy {
    private val loadPattern=Regex("^(0|[1-9][0-9]{0,4})(\\.[0-9]{1,3})?$")

    fun accepts(load: String, reps: String, rir: String, allowFailedAttempt: Boolean): Boolean {
        if(!loadPattern.matches(load))return false
        val count=reps.toIntOrNull() ?: return false
        val effort=rir.toIntOrNull()
        if(count !in (if(allowFailedAttempt)0 else 1)..100 ||
            (rir.isNotEmpty() && (effort==null || effort !in 0..10)))return false
        return count>0 || allowFailedAttempt && load.toBigDecimal().signum()>0
    }

    /** One rep at a time; an empty draft receives the smallest legal value. */
    fun stepReps(text: String, direction: Int, allowFailedAttempt: Boolean): String? {
        if(direction!=1 && direction!=-1)return null
        val minimum=if(allowFailedAttempt)0 else 1
        val current=text.toIntOrNull() ?: if(text.isEmpty() && direction==1)minimum-1 else return null
        val next=current+direction
        return if(next in minimum..100)next.toString() else null
    }

    /**
     * A long-press is ONE bounded draft edit, never an auto-repeat or a
     * training prescription. When near a limit, use the last legal step
     * rather than wrap, overwrite or manufacture an out-of-range actual.
     */
    fun stepRepsBy(text:String,direction:Int,count:Int,
        allowFailedAttempt:Boolean):String? {
        if(count !in 1..3)return null
        var updated=text
        for(i in 0 until count) {
            updated=stepReps(updated,direction,allowFailedAttempt)?:break
        }
        return updated.takeIf{it!=text}
    }

    /**
     * RIR is OPTIONAL. Tapping + when unset selects 0, and - on 0 clears
     * the observation instead of inventing effort or forcing zero RIR.
     */
    fun stepRir(text: String, direction: Int): String? {
        if(direction!=1 && direction!=-1)return null
        if(text.isEmpty())return if(direction==1)"0" else null
        val current=text.toIntOrNull() ?: return null
        if(current !in 0..10)return null
        if(direction==-1 && current==0)return ""
        val next=current+direction
        return if(next in 0..10)next.toString() else null
    }
    fun stepRirBy(text:String,direction:Int,count:Int):String? {
        if(count !in 1..2)return null
        var updated=text
        for(i in 0 until count) {
            updated=stepRir(updated,direction)?:break
        }
        return updated.takeIf{it!=text}
    }
}
