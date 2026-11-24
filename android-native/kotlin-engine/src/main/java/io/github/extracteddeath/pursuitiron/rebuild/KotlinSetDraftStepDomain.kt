package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal

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
    /**
     * Explicit +/- gesture on a weight draft only. Stock must be confirmed
     * for the exact issued basis and unit by the caller. Typed off-grid
     * values remain untouched until the athlete chooses a direction.
     * Never invent a fixed increment or silently round an actual.
     */
    fun stepLoad(text:String,direction:Int,count:Int,
        target:DraftSetLoad.Verified,stock:VerifiedLoadStock?):String? {
        if(direction !in listOf(-1,1) || count !in 1..3 ||
            stock==null || stock.unit!=target.unit || stock.basis!=target.basis ||
            !text.matches(Regex("^[0-9]+(?:[.][0-9]+)?$")))return null
        val start=text.toBigDecimalOrNull() ?:return null
        if(!KotlinSetNumberAuthorityDomain.validPhysicalLoadValue(start))return null
        var current=start
        repeat(count) {
            val inspection=EquipmentLoadDomain.inspect(stock,current,target.unit)
                as? LoadInspection.Confirmed ?:return null
            val next=if(direction==1)inspection.nextAbove else inspection.nextBelow
            if(next==null || !KotlinSetNumberAuthorityDomain.validPrescribedLoad(
                    next,target.basis))
                return current.takeIf{it.compareTo(start)!=0}
                    ?.stripTrailingZeros()?.toPlainString()
            current=next
        }
        return current.takeIf{it.compareTo(start)!=0}
            ?.stripTrailingZeros()?.toPlainString()
    }

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
