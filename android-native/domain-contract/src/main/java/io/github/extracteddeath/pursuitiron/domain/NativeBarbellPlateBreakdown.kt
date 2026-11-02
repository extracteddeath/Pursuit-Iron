package io.github.extracteddeath.pursuitiron.domain

import java.math.BigDecimal
import org.json.JSONObject

/** A physical plate pair: count is PER SIDE, never total individual plates. */
data class NativePlatePairCount(val weight: String,val pairs: Int)
data class NativeBarbellPlateBreakdown(
    val barWeight: String,val totalLoad: String,val unit: String,
    val perSide: List<NativePlatePairCount>
)

/**
 * Read-only plate visualization from a validated, SAVED physical inventory.
 * No inferred bar weight, default plates, progression or journal mutation.
 * A bounded exact solver prefers the fewest pairs, then larger denominations.
 * An unreachable/oversized configuration fails closed instead of rounding.
 */
object NativeBarbellPlateBreakdownPolicy {
    private const val MAX_SEARCH_STATES=100_000
    private data class Stock(val cents: Long,val weight: String,val pairs: Int)
    private data class Solution(val pairCount: Int,val counts: List<Int>)

    fun inspect(
        inventory: String?,unit: String,targetLoad: String,
        allowedExerciseIds: Set<String> = emptySet(),
        exactOnlyIds: Set<String> = emptySet()
    ): NativeBarbellPlateBreakdown? {
        if(inventory==null || unit !in setOf("lb","kg"))return null
        return runCatching {
            val validated=NativeGymInventoryCodec.validate(
                inventory,unit,allowedExerciseIds,exactOnlyIds)
            val barbell=JSONObject(validated).optJSONObject("barbell") ?: return null
            val bar=BigDecimal(barbell.get("barWeight").toString())
            val target=BigDecimal(targetLoad)
            require(target>=bar && target>=BigDecimal.ZERO)
            fun cents(value: BigDecimal)=value.movePointRight(2).longValueExact()
            // Input may have three decimal places; never round it into stock.
            val wanted=cents(target.subtract(bar).divide(BigDecimal("2")))
            val stock=barbell.getJSONArray("platePairs")
            val entries=(0 until stock.length()).map { index->
                val item=stock.getJSONObject(index)
                val weight=BigDecimal(item.get("weight").toString())
                Stock(cents(weight),weight.stripTrailingZeros().toPlainString(),
                    item.getInt("pairs"))
            }.sortedByDescending { it.cents }
            require(entries.all { it.cents>0L && it.pairs in 1..50 })
            var visited=0
            val memo=HashMap<Pair<Int,Long>,Solution?>()
            fun solve(index: Int,remaining: Long): Solution? {
                if(remaining==0L)return Solution(0,List(entries.size-index){0})
                if(index==entries.size)return null
                val key=index to remaining
                if(memo.containsKey(key))return memo[key]
                if(++visited>MAX_SEARCH_STATES)throw IllegalStateException("Plate search exceeded safe limit")
                val part=entries[index]
                val maximum=minOf(part.pairs,(remaining/part.cents).toInt())
                var best:Solution?=null
                for(count in maximum downTo 0) {
                    val tail=solve(index+1,remaining-count*part.cents) ?: continue
                    val candidate=Solution(count+tail.pairCount,listOf(count)+tail.counts)
                    if(best==null || candidate.pairCount<best.pairCount)best=candidate
                }
                memo[key]=best
                return best
            }
            val layout=solve(0,wanted) ?: return null
            val pieces=entries.indices.mapNotNull { i->
                layout.counts[i].takeIf { it>0 }?.let {
                    NativePlatePairCount(entries[i].weight,it)
                }
            }
            NativeBarbellPlateBreakdown(
                bar.stripTrailingZeros().toPlainString(),
                target.stripTrailingZeros().toPlainString(),unit,pieces
            )
        }.getOrNull()
    }
}
