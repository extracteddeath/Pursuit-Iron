package io.github.extracteddeath.pursuitiron.rebuild

/**
 * Presentation-only numeric draft stepping for the Kotlin workout editors.
 * This does not validate a performed journal receipt, set a workout load,
 * infer effort, or modify a prescription. The canonical number authority
 * still validates every submitted actual independently.
 *
 * Replaces use of the historical JavaScript-era domain-contract steppers in
 * the first official native beta. A long press performs one bounded edit.
 */
object KotlinSetDraftStepDomain {
    fun stepReps(text:String,direction:Int,allowFailedAttempt:Boolean):String? {
        if(direction != 1 && direction != -1)return null
        val minimum=if(allowFailedAttempt)0 else 1
        val previous=text.toIntOrNull()
            ?:if(text.isEmpty() && direction==1)minimum-1 else return null
        val next=previous+direction
        return if(next in minimum..100)next.toString() else null
    }

    fun stepRepsBy(text:String,direction:Int,count:Int,
        allowFailedAttempt:Boolean):String? {
        if(count !in 1..3)return null
        var current=text
        repeat(count) {
            current=stepReps(current,direction,allowFailedAttempt)
                ?:return current.takeIf{it!=text}
        }
        return current.takeIf{it!=text}
    }

    /** Unreported RIR remains blank; + selects 0 and − from 0 clears it. */
    fun stepRir(text:String,direction:Int):String? {
        if(direction != 1 && direction != -1)return null
        if(text.isEmpty())return if(direction==1)"0" else null
        val current=text.toIntOrNull() ?:return null
        if(current !in 0..10)return null
        if(direction==-1 && current==0)return ""
        val next=current+direction
        return if(next in 0..10)next.toString() else null
    }

    fun stepRirBy(text:String,direction:Int,count:Int):String? {
        if(count !in 1..2)return null
        var current=text
        repeat(count) {
            current=stepRir(current,direction)
                ?:return current.takeIf{it!=text}
        }
        return current.takeIf{it!=text}
    }
}
