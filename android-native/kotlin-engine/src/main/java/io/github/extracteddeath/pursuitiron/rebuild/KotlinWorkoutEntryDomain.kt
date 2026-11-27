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
    /**
     * The parser determines field-specific errors; acceptance of a parsed
     * performed set is ALWAYS checked by the same engine-owned policy as
     * generated/custom journals and cold replay.
     */
    private fun accepted(
        expectedUnit:LoadUnit?,
        performed:KotlinPerformedSet,
        failed:Boolean
    ):KotlinSetEntryResult =
        if(KotlinSetNumberAuthorityDomain.actualRefusal(expectedUnit,
                if(failed)KotlinJournalSetOutcome.FAILED_ATTEMPT
                else KotlinJournalSetOutcome.COMPLETED,performed)==null)
            KotlinSetEntryResult.Accepted(performed)
        else KotlinSetEntryResult.Rejected(KotlinSetEntryRefusal.INVALID_WEIGHT)

    private val weightFormat=Regex("^(?:0|[1-9][0-9]{0,5})(?:\\.[0-9]{1,3})?$")
    private val numeric=Regex("^[0-9]{1,3}$")

    fun parse(
        target:KotlinWorkoutTarget,
        weightText:String,
        repsText:String,
        actualRirText:String,
        failed:Boolean=false
    ):KotlinSetEntryResult = parse(target.load,weightText,repsText,actualRirText,failed)

    /** Custom main/follow-up visits use the same bounded actual-entry policy. */
    fun parse(
        load:DraftSetLoad,
        weightText:String,
        repsText:String,
        actualRirText:String,
        failed:Boolean=false
    ):KotlinSetEntryResult {
        // Keep malformed issued-load refusal distinct from actual-only entry.
        // Existing field validation order is retained by checking after parse.
        val result=parseUnplanned((load as? DraftSetLoad.Verified)?.unit,
            weightText,repsText,actualRirText,failed)
        if(result is KotlinSetEntryResult.Accepted &&
            !KotlinSetNumberAuthorityDomain.validIssuedLoad(load))
            return KotlinSetEntryResult.Rejected(KotlinSetEntryRefusal.INVALID_WEIGHT)
        return result
    }

    /** Shared actual-only field parser. A verified bodyweight movement uses
     * null unit; an unknown/unreviewed physical context must not call this. */
    fun parseUnplanned(
        expectedUnit:LoadUnit?,
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
            if(!KotlinSetNumberAuthorityDomain.validCompletedReps(result))
                return KotlinSetEntryResult.Rejected(KotlinSetEntryRefusal.INVALID_REPS)
            result
        }
        val rir=if(failed || actualRirText.isBlank())null else {
            val raw=actualRirText.trim()
            val value=raw.toIntOrNull()
            if(!numeric.matches(raw) || value==null || !KotlinSetNumberAuthorityDomain.validRir(value))
                return KotlinSetEntryResult.Rejected(KotlinSetEntryRefusal.INVALID_RIR)
            value
        }
        return when(expectedUnit) {
            null ->
                accepted(null,KotlinPerformedSet(null,null,reps,rir),failed)
            else -> {
                val text=weightText.trim()
                if(text.isEmpty())
                    return KotlinSetEntryResult.Rejected(KotlinSetEntryRefusal.MISSING_WEIGHT)
                if(!weightFormat.matches(text))
                    return KotlinSetEntryResult.Rejected(KotlinSetEntryRefusal.INVALID_WEIGHT)
                val amount=runCatching{BigDecimal(text)}.getOrNull()
                    ?:return KotlinSetEntryResult.Rejected(
                        KotlinSetEntryRefusal.INVALID_WEIGHT)
                // The parser owns text syntax only. The shared receipt
                // authority below owns the physical numeric envelope.
                accepted(expectedUnit,KotlinPerformedSet(amount,expectedUnit,reps,rir),failed)
            }
        }
    }
}
