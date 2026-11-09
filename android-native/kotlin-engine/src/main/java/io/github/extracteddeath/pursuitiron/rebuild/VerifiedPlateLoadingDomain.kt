package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import java.util.Collections

/** EXACT currently confirmed physical stock, never default 45lb bars,
 * guessed plate pairs, Smith offsets or automatic logged weight updates. */
data class VerifiedPlatePair(val weightPerPlate:BigDecimal,val pairs:Int)
data class VerifiedPlateBreakdown(
    val unit:LoadUnit,
    val targetTotal:BigDecimal,
    val achievableTotal:BigDecimal,
    val barWeight:BigDecimal,
    val perSide:List<VerifiedPlatePair>,
    val exact:Boolean
)
enum class PlateLoadUnavailable {
    NO_BAR_STOCK, INCOMPATIBLE_BASIS, BELOW_EMPTY_BAR, INVALID_INVENTORY,
    UNIT_MISMATCH, INVALID_TARGET, NOT_REPRESENTABLE
}
sealed interface VerifiedPlateLoadReview {
    data class Ready(val breakdown:VerifiedPlateBreakdown):VerifiedPlateLoadReview
    data class Unavailable(val reason:PlateLoadUnavailable):VerifiedPlateLoadReview
}

/**
 * Bounded, deterministic plate-pair reconstruction. We use the same
 * verified attainability gate as the Kotlin equipment engine. Never round
 * a target to an unverified load. If exact stock does not exist, display the
 * largest safe load AT OR BELOW target as an explicitly labeled alternative.
 *
 * Each per-side count consumes one confirmed PAIR, not one plate. Minimum
 * number of pairs breaks ties; larger plates are preferred on equal count.
 */
object VerifiedPlateLoadingDomain {
    private const val SCALE=3
    private const val MAX_STATES=100_000
    private fun milli(value:BigDecimal):Long =
        value.movePointRight(SCALE).longValueExact()
    private fun normalized(value:BigDecimal):BigDecimal =
        BigDecimal(value.stripTrailingZeros().toPlainString())

    fun review(stock:VerifiedLoadStock?,target:BigDecimal,unit:LoadUnit):
        VerifiedPlateLoadReview {
        if(stock==null)
            return VerifiedPlateLoadReview.Unavailable(
                PlateLoadUnavailable.NO_BAR_STOCK)
        if(stock.unit!=unit)
            return VerifiedPlateLoadReview.Unavailable(
                PlateLoadUnavailable.UNIT_MISMATCH)
        if(stock !is VerifiedLoadStock.Barbell)
            return VerifiedPlateLoadReview.Unavailable(
                PlateLoadUnavailable.INCOMPATIBLE_BASIS)
        val inspection=EquipmentLoadDomain.inspect(stock,target,unit)
        if(inspection is LoadInspection.Unavailable) {
            val reason=when(inspection.reason) {
                LoadUnavailableReason.INVALID_INPUT -> PlateLoadUnavailable.INVALID_TARGET
                LoadUnavailableReason.UNIT_MISMATCH -> PlateLoadUnavailable.UNIT_MISMATCH
                else -> PlateLoadUnavailable.INVALID_INVENTORY
            }
            return VerifiedPlateLoadReview.Unavailable(reason)
        }
        val allowed=inspection as LoadInspection.Confirmed
        val best=allowed.atOrBelow ?:return VerifiedPlateLoadReview.Unavailable(
            PlateLoadUnavailable.BELOW_EMPTY_BAR)
        if(best<stock.barWeight)
            return VerifiedPlateLoadReview.Unavailable(
                PlateLoadUnavailable.INVALID_INVENTORY)
        return runCatching {
            val amount=milli(best.subtract(stock.barWeight)
                .divide(BigDecimal("2")))
            val inventory=stock.pairedPlates.entries
                .sortedByDescending{it.key}
            val dp=HashMap<Long,IntArray>()
            dp[0L]=IntArray(inventory.size)
            for((index,entry) in inventory.withIndex()) {
                val perPlate=milli(entry.key)
                val prior=dp.entries.toList()
                for((side,combo) in prior) {
                    for(pairs in 1..entry.value) {
                        val nextWeight=side+perPlate*pairs
                        if(nextWeight>amount)break
                        val attempt=combo.copyOf()
                        attempt[index]+=pairs
                        val original=dp[nextWeight]
                        if(original==null ||
                            attempt.sum()<original.sum() ||
                            attempt.sum()==original.sum() &&
                            // Larger plate quantities win exact ties.
                            attempt.toList().zip(original.toList())
                                .firstOrNull{(a,b)->a!=b}
                                ?.let{(a,b)->a>b}==true)
                            dp[nextWeight]=attempt
                        if(dp.size>MAX_STATES)
                            throw IllegalArgumentException("plate inventory too broad")
                    }
                }
            }
            val chosen=dp[amount]
                ?:return@runCatching VerifiedPlateLoadReview.Unavailable(
                    PlateLoadUnavailable.NOT_REPRESENTABLE)
            val onSide=inventory.indices.mapNotNull {index ->
                chosen[index].takeIf{it>0}?.let{pairs->
                    VerifiedPlatePair(normalized(inventory[index].key),pairs)
                }
            }
            VerifiedPlateLoadReview.Ready(VerifiedPlateBreakdown(
                unit=unit,targetTotal=normalized(target),
                achievableTotal=normalized(best),
                barWeight=normalized(stock.barWeight),
                perSide=Collections.unmodifiableList(onSide),
                exact=allowed.exact
            ))
        }.getOrElse{
            VerifiedPlateLoadReview.Unavailable(
                PlateLoadUnavailable.INVALID_INVENTORY)
        }
    }
}
