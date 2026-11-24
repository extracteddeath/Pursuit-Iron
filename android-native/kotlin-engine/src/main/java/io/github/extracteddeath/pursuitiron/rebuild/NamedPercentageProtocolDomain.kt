package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import java.util.Collections

/** Named protocol intent, pinned to the reviewed web percentage-protocols.js
 * at 29faa204212a4d54088b4870341350e605939185. No JavaScript at runtime.
 * Numeric realization delegates to the same authority as authored waves.
 * A candidate is not an issued workout or consent to change a training max. */
enum class NamedPercentageScheme {
    FIVE_THREE_ONE, FIVE_THREE_ONE_BEGINNER, MADCOW, NSUNS, TEXAS, GZCLP, GVT, RIPPLER, JT
}
enum class PercentageReferenceKind { TRAINING_MAX, TOP_FIVE_REP_LOAD, ONE_REP_MAX }
enum class PercentageTier { PRIMARY, SECONDARY }
enum class PercentageDayRole { STANDARD, TEXAS_VOLUME, TEXAS_RECOVERY, TEXAS_INTENSITY }

data class NamedPercentageRequest(
    val scope: BasicProgressionScope,
    val scheme: NamedPercentageScheme,
    val tier: PercentageTier,
    val dayRole: PercentageDayRole,
    val referenceKind: PercentageReferenceKind,
    val confirmedReference: BigDecimal?,
    val referenceConfirmed: Boolean,
    val weekInBlock: Int,
    val blockWeeks: Int,
    val approvedSetBudget: Int,
    val verifiedStock: VerifiedLoadStock?
)
enum class NamedPercentageRefusal {
    INVALID_SCOPE_OR_REFERENCE, INVALID_WEEK_OR_BUDGET, WRONG_REFERENCE_KIND,
    INCOMPATIBLE_DAY_ROLE, TIERED_HISTORY_REQUIRED, PHYSICAL_LOAD_REFUSED
}
data class NamedPercentageSet(
    val sourceOrdinal: Int,
    val sourcePercent: BigDecimal,
    val effectivePercent: BigDecimal,
    val rawCalculatedLoad: BigDecimal,
    val verifiedLoad: BigDecimal,
    val minimumReps: Int,
    val repOut: Boolean
)
sealed interface NamedPercentageResult {
    data class Refused(
        val reason: NamedPercentageRefusal,
        val physicalReason: BasicProgrammedLoadRefusal? = null
    ): NamedPercentageResult
    data class Candidate(
        val scheme: NamedPercentageScheme,
        val tier: PercentageTier,
        val weekInBlock: Int,
        val sourceSetCount: Int,
        val adapted: Boolean,
        val recovery: Boolean,
        val sets: List<NamedPercentageSet>
    ): NamedPercentageResult {
        val mayIssueWorkout: Boolean get() = false
        val mayModifyHistory: Boolean get() = false
        val maySaveProgram: Boolean get() = false
        val mayChangeReference: Boolean get() = false
    }
}

object NamedPercentageProtocolDomain {
    private data class Intent(val percent: BigDecimal, val reps: Int, val repOut: Boolean)
    private fun n(value: String) = BigDecimal(value)
    private fun row(vararg entries: String): List<Intent> = entries.map {
        val (pct, target) = it.split(":")
        Intent(n(pct), target.removeSuffix("+").toInt(), target.endsWith("+"))
    }
    private val fiveThreeOne = listOf(
        row("65:5","75:5","85:5+"),
        row("70:3","80:3","90:3+"),
        row("75:5","85:3","95:1+"),
        row("40:5","50:5","60:5")
    )
    private val beginner = fiveThreeOne.map { week ->
        week.map { it.copy(reps=5,repOut=false) }
    }
    private val rippler = listOf(
        row("70:5","75:5","80:5","75:5","70:5+"),
        row("72.5:4","80:3","85:2","80:3","75:4+"),
        row("75:3","82.5:2","90:1","82.5:2","77.5:3+")
    )
    private val hundred = n("100")
    private val zero = BigDecimal.ZERO

    fun referenceKind(scheme: NamedPercentageScheme): PercentageReferenceKind = when(scheme) {
        NamedPercentageScheme.MADCOW, NamedPercentageScheme.TEXAS ->
            PercentageReferenceKind.TOP_FIVE_REP_LOAD
        NamedPercentageScheme.GVT -> PercentageReferenceKind.ONE_REP_MAX
        else -> PercentageReferenceKind.TRAINING_MAX
    }

    fun review(request: NamedPercentageRequest): NamedPercentageResult {
        fun no(reason: NamedPercentageRefusal, physical: BasicProgrammedLoadRefusal? = null) =
            NamedPercentageResult.Refused(reason,physical)
        val reference = request.confirmedReference
        if (!request.scope.valid() || !request.referenceConfirmed || reference==null ||
            reference<=zero || !KotlinSetNumberAuthorityDomain.validPhysicalLoadValue(reference))
            return no(NamedPercentageRefusal.INVALID_SCOPE_OR_REFERENCE)
        if (request.blockWeeks !in 1..52 || request.weekInBlock<1 ||
            request.approvedSetBudget !in 1..20)
            return no(NamedPercentageRefusal.INVALID_WEEK_OR_BUDGET)
        // GZCLP is not a fixed percentage table: its actual production route
        // replays per-tier issued targets and factual completed history.
        if (request.scheme==NamedPercentageScheme.GZCLP)
            return no(NamedPercentageRefusal.TIERED_HISTORY_REQUIRED)
        if (request.referenceKind!=referenceKind(request.scheme))
            return no(NamedPercentageRefusal.WRONG_REFERENCE_KIND)
        if ((request.scheme==NamedPercentageScheme.TEXAS) !=
            (request.dayRole!=PercentageDayRole.STANDARD))
            return no(NamedPercentageRefusal.INCOMPATIBLE_DAY_ROLE)
        if (request.scope.basis==LoadBasis.ASSISTANCE_COUNTERWEIGHT ||
            KotlinSetNumberAuthorityDomain.machineTagContradictsFreeBarbell(
                request.scope.basis,request.scope.machineInstanceId))
            return no(NamedPercentageRefusal.PHYSICAL_LOAD_REFUSED,
                BasicProgrammedLoadRefusal.INCOMPATIBLE_ASSISTANCE_OR_LOAD_BASIS)
        val stock = request.verifiedStock
            ?:return no(NamedPercentageRefusal.PHYSICAL_LOAD_REFUSED,
                BasicProgrammedLoadRefusal.UNVERIFIED_GYM_STOCK)
        val beyond = request.weekInBlock>request.blockWeeks
        val selfDeload = request.scheme in setOf(NamedPercentageScheme.FIVE_THREE_ONE,
            NamedPercentageScheme.FIVE_THREE_ONE_BEGINNER)
        val cycleIndex = if(beyond) 3 else (request.weekInBlock-1)%4
        val secondary = request.tier==PercentageTier.SECONDARY
        val source = when(request.scheme) {
            NamedPercentageScheme.FIVE_THREE_ONE -> fiveThreeOne[cycleIndex]
            NamedPercentageScheme.FIVE_THREE_ONE_BEGINNER -> {
                val week=beginner[cycleIndex]
                if(secondary && !beyond) List(5){week.first().copy(reps=5,repOut=false)}
                else week
            }
            NamedPercentageScheme.MADCOW -> row("50:5","62.5:5","75:5","87.5:5","100:5")
            NamedPercentageScheme.NSUNS ->
                row("75:5","85:3","95:1+","90:3","85:3","80:5","75:5","70:5","65:5+")
            NamedPercentageScheme.TEXAS -> when(request.dayRole) {
                PercentageDayRole.TEXAS_VOLUME -> List(5){Intent(n("90"),5,false)}
                PercentageDayRole.TEXAS_RECOVERY -> List(2){Intent(n("80"),5,false)}
                PercentageDayRole.TEXAS_INTENSITY -> row("100:5+")
                PercentageDayRole.STANDARD -> return no(NamedPercentageRefusal.INCOMPATIBLE_DAY_ROLE)
            }
            NamedPercentageScheme.GVT -> List(10){Intent(n("60"),10,false)}
            NamedPercentageScheme.RIPPLER -> if(secondary) row("65:8","70:6","65:8+")
                else rippler[(request.weekInBlock-1)%3]
            NamedPercentageScheme.JT -> if(secondary) row("65:10","70:8","70:6+")
                else row("70:5","80:3","87.5:2","92.5:1+","82.5:3","75:5+")
            NamedPercentageScheme.GZCLP -> return no(NamedPercentageRefusal.TIERED_HISTORY_REQUIRED)
        }
        val perWeek=when(request.scheme) {
            NamedPercentageScheme.MADCOW -> n("0.025")
            NamedPercentageScheme.TEXAS, NamedPercentageScheme.JT -> n("0.015")
            NamedPercentageScheme.GVT -> n("0.02")
            NamedPercentageScheme.RIPPLER -> n("0.005")
            else -> zero
        }
        val genericDeload=beyond&&!selfDeload
        val multiplier=if(genericDeload)n("0.6")
            else if(beyond)BigDecimal.ONE
            else BigDecimal.ONE.add(perWeek.multiply(BigDecimal.valueOf(
                (request.weekInBlock-1).toLong())))
        val effectiveSource=if(genericDeload)
            source.take(maxOf(2,(source.size+1)/2)).map{it.copy(repOut=false)}
            else source
        val realized=ArrayList<NamedPercentageSet>()
        for((ordinal,intent) in effectiveSource.withIndex()) {
            val effective=intent.percent.multiply(multiplier)
            val raw=reference.multiply(effective).divide(hundred)
            when(val result=BasicProgrammedPercentageLoadDomain.verifiedPhysicalLoad(
                request.scope,stock,raw)) {
                is ProgrammedPhysicalLoadReview.Refused ->
                    return no(NamedPercentageRefusal.PHYSICAL_LOAD_REFUSED,result.reason)
                is ProgrammedPhysicalLoadReview.Confirmed -> realized+=NamedPercentageSet(
                    ordinal+1,intent.percent,effective,raw,result.value,intent.reps,intent.repOut)
            }
        }
        val adapted=adapt(realized,request.approvedSetBudget)
        return NamedPercentageResult.Candidate(
            request.scheme,request.tier,request.weekInBlock,realized.size,
            adapted.size!=realized.size,beyond || (selfDeload && cycleIndex==3),
            Collections.unmodifiableList(adapted))
    }

    /** Same precedence as the pinned web set-budget contract: last tied
     * heaviest set, final set, first set, rep-outs, evenly spaced then rest.
     * Add only copies of the first set, with rep-out disabled. */
    private fun adapt(source: List<NamedPercentageSet>, count: Int): List<NamedPercentageSet> {
        if(count==source.size)return source.toList()
        if(count>source.size)return List(count-source.size){source.first().copy(repOut=false)}+source
        val peak=source.indices.reduce{best,i ->
            if(source[i].verifiedLoad>=source[best].verifiedLoad)i else best
        }
        val spread=(0 until count).map { i ->
            // Integer half-up rounding for nonnegative indices.
            val denominator=maxOf(1,count-1)
            (2*i*(source.size-1)+denominator)/(2*denominator)
        }
        val indexes=(listOf(peak,source.lastIndex,0)+
            source.indices.filter{source[it].repOut}+spread+source.indices.toList())
            .distinct().take(count).sorted()
        return indexes.map{source[it]}
    }
}
