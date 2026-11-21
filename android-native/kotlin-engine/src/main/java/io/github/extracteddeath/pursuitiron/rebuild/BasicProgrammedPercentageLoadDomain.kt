package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Collections

/**
 * Deterministic PROGRAMMED loading, separate from earned overload.
 *
 * Strength percentages, week waves, tapers and DUP day roles do not depend
 * on the last workout's reps. They require an explicitly confirmed training
 * max, a complete immutable week schedule and exact gym stock. This is only
 * a proposed future set load and never a new logged actual or working grant.
 */
data class BasicProgrammedWeek(
    val weekInBlock:Int,
    /** A percent as written: 82.5 means 82.5%, not a decimal fraction. */
    val percentOfTrainingMax:BigDecimal,
    val workingSets:Int,
    val reps:RepBand
)
data class BasicProgrammedLoadRequest(
    val scope:BasicProgressionScope,
    val trainingMax:BigDecimal?,
    /** Confirmation is explicit; an estimated 1RM may never be inferred here. */
    val trainingMaxConfirmed:Boolean,
    val blockWeeks:Int,
    val authoredWeeks:List<BasicProgrammedWeek>,
    val verifiedStock:VerifiedLoadStock?
)
enum class BasicProgrammedLoadRefusal {
    INVALID_SCOPE_OR_MAX, INCOMPLETE_OR_INVALID_WEEK_SCHEDULE,
    INCOMPATIBLE_ASSISTANCE_OR_LOAD_BASIS, UNVERIFIED_GYM_STOCK,
    NO_SAFE_AT_OR_BELOW_LOAD
}
data class BasicProgrammedWeekLoad(
    val weekInBlock:Int,
    val requestedPercent:BigDecimal,
    val rawCalculatedLoad:BigDecimal,
    val verifiedLoad:BigDecimal,
    val workingSets:Int,
    val reps:RepBand
)
sealed interface BasicProgrammedLoadResult {
    data class Refused(val reason:BasicProgrammedLoadRefusal):BasicProgrammedLoadResult
    data class Candidate(val weeks:List<BasicProgrammedWeekLoad>):BasicProgrammedLoadResult {
        val mayModifyIssuedWorkout:Boolean get()=false
        val mayModifyHistory:Boolean get()=false
        val maySaveProgram:Boolean get()=false
    }
}

object BasicProgrammedPercentageLoadDomain {
    private val zero=BigDecimal.ZERO
    private val hundred=BigDecimal("100")

    fun review(request:BasicProgrammedLoadRequest):BasicProgrammedLoadResult {
        fun no(reason:BasicProgrammedLoadRefusal)=BasicProgrammedLoadResult.Refused(reason)
        val max=request.trainingMax
        if(!request.scope.valid() || !request.trainingMaxConfirmed ||
            max==null || max<=zero ||
            !KotlinSetNumberAuthorityDomain.validPhysicalLoadValue(max))
            return no(BasicProgrammedLoadRefusal.INVALID_SCOPE_OR_MAX)
        if(request.blockWeeks !in 1..52 ||
            request.authoredWeeks.size!=request.blockWeeks ||
            request.authoredWeeks.map{it.weekInBlock}!=
                (1..request.blockWeeks).toList())
            return no(BasicProgrammedLoadRefusal.INCOMPLETE_OR_INVALID_WEEK_SCHEDULE)
        if(request.authoredWeeks.any {step->
            step.workingSets !in 1..32 ||
                step.percentOfTrainingMax<=zero ||
                step.percentOfTrainingMax>hundred ||
                step.percentOfTrainingMax.stripTrailingZeros().scale()>3
        })return no(BasicProgrammedLoadRefusal.INCOMPLETE_OR_INVALID_WEEK_SCHEDULE)
        val basis=request.scope.basis
        if(basis==LoadBasis.ASSISTANCE_COUNTERWEIGHT)
            return no(BasicProgrammedLoadRefusal.INCOMPATIBLE_ASSISTANCE_OR_LOAD_BASIS)
        val stock=request.verifiedStock
        if(stock==null || stock.unit!=request.scope.unit || stock.basis!=basis)
            return no(BasicProgrammedLoadRefusal.UNVERIFIED_GYM_STOCK)
        val proposed=ArrayList<BasicProgrammedWeekLoad>()
        for(step in request.authoredWeeks) {
            val raw=max.multiply(step.percentOfTrainingMax).divide(hundred)
            // EquipmentLoadDomain can inspect three decimal places only. This
            // is a DOWN-only numeric envelope; the load actually proposed is
            // selected solely from verified inventory at or below the raw
            // percentage. No rounding UP or invented physical increment.
            val search=raw.setScale(3,RoundingMode.DOWN)
            val inspected=EquipmentLoadDomain.inspect(stock,search,request.scope.unit)
                as? LoadInspection.Confirmed
                ?:return no(BasicProgrammedLoadRefusal.UNVERIFIED_GYM_STOCK)
            val attainable=inspected.atOrBelow
                ?:return no(BasicProgrammedLoadRefusal.NO_SAFE_AT_OR_BELOW_LOAD)
            if(attainable>raw || attainable<zero ||
                (attainable.signum()==0 && !basis.permitsZeroAddedLoad))
                return no(BasicProgrammedLoadRefusal.NO_SAFE_AT_OR_BELOW_LOAD)
            proposed+=BasicProgrammedWeekLoad(step.weekInBlock,
                step.percentOfTrainingMax,raw,
                BigDecimal(attainable.stripTrailingZeros().toPlainString()),
                step.workingSets,step.reps)
        }
        return BasicProgrammedLoadResult.Candidate(
            Collections.unmodifiableList(proposed))
    }
}
