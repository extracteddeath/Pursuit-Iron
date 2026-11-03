package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal

/**
 * Shared native set-entry validation. The UI shows a target as a suggestion,
 * not proof that the athlete lifted it; the athlete explicitly submits actual
 * values. Blank actual RIR remains UNKNOWN rather than becoming target RIR.
 *
 * For a FAILED_ATTEMPT, reps=0 and RIR=null are explicit; the actual
 * attempted weight still comes from the athlete's input, never the target.
 */
enum class KotlinSetEntryRefusal {
    MISSING_REPS, INVALID_REPS, INVALID_RIR,
    MISSING_WEIGHT, INVALID_WEIGHT
}
sealed interface KotlinSetEntryResult {
    data class Accepted(val performed:KotlinPerformedSet):KotlinSetEntryResult
    data class Rejected(val reason:KotlinSetEntryRefusal):KotlinSetEntryResult
}
object KotlinWorkoutEntryDomain {
    private val weightFormat=Regex("^(?:0|[1-9][0-9]{0,5})(?:\\.[0-9]{1,3})?$")
    private val maxWeight=BigDecimal("100000")
    private val numeric=Regex("^[0-9]{1,3}$")

    fun parse(
        target:KotlinWorkoutTarget,
        weightText:String,
        repsText:String,
        actualRirText:String,
        failed:Boolean=false
    ):KotlinSetEntryResult {
        val reps=if(failed)0 else {
            val raw=repsText.trim()
            if(raw.isEmpty())return KotlinSetEntryResult.Rejected(
                KotlinSetEntryRefusal.MISSING_REPS)
            if(!numeric.matches(raw))
                return KotlinSetEntryResult.Rejected(KotlinSetEntryRefusal.INVALID_REPS)
            val result=raw.toIntOrNull()
                ?:return KotlinSetEntryResult.Rejected(KotlinSetEntryRefusal.INVALID_REPS)
            if(result !in 1..100)
                return KotlinSetEntryResult.Rejected(KotlinSetEntryRefusal.INVALID_REPS)
            result
        }
        val rir=if(failed || actualRirText.isBlank())null else {
            val raw=actualRirText.trim()
            val value=raw.toIntOrNull()
            if(!numeric.matches(raw) || value==null || value !in 0..10)
                return KotlinSetEntryResult.Rejected(KotlinSetEntryRefusal.INVALID_RIR)
            value
        }
        return when(val planned=target.load) {
            DraftSetLoad.ExplicitBodyweight ->
                KotlinSetEntryResult.Accepted(KotlinPerformedSet(
                    null,null,reps,rir))
            is DraftSetLoad.Verified -> {
                val text=weightText.trim()
                if(text.isEmpty())
                    return KotlinSetEntryResult.Rejected(KotlinSetEntryRefusal.MISSING_WEIGHT)
                if(!weightFormat.matches(text))
                    return KotlinSetEntryResult.Rejected(KotlinSetEntryRefusal.INVALID_WEIGHT)
                val amount=runCatching{BigDecimal(text)}.getOrNull()
                    ?:return KotlinSetEntryResult.Rejected(
                        KotlinSetEntryRefusal.INVALID_WEIGHT)
                if(amount<BigDecimal.ZERO || amount>maxWeight)
                    return KotlinSetEntryResult.Rejected(KotlinSetEntryRefusal.INVALID_WEIGHT)
                KotlinSetEntryResult.Accepted(KotlinPerformedSet(
                    amount,planned.unit,reps,rir))
            }
        }
    }
}
