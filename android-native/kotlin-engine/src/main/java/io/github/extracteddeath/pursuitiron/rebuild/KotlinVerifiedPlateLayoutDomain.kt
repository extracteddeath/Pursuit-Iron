package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal

/** Exact number of physical plates of one size ON EACH side of a bar. */
data class KotlinPlateSide(
    val weight:BigDecimal,
    val countPerSide:Int
)

/** Read-only plate arithmetic, not a proposed weight or a logged lift. */
data class KotlinVerifiedPlateLayout(
    val barWeight:BigDecimal,
    val total:BigDecimal,
    val unit:LoadUnit,
    val perSide:List<KotlinPlateSide>
)

/**
 * Only confirmed, exact BARBELL_TOTAL stock may yield a diagram.
 * Smith machines and other stock types are never treated as a barbell.
 * Keep the independent gym loadability validator authoritative, and
 * enforce a bounded pair-count search to avoid unbounded UI work.
 */
object KotlinVerifiedPlateLayoutDomain {
    private const val MAX_STATES=100_000
    private data class Plate(val weight:BigDecimal,val milli:Long,val pairs:Int)
    private data class Solution(val totalPairs:Int,val counts:List<Int>)

    fun inspect(
        stock:VerifiedLoadStock?,
        entered:BigDecimal,
        unit:LoadUnit
    ):KotlinVerifiedPlateLayout? {
        val bar=stock as? VerifiedLoadStock.Barbell ?:return null
        val checked=EquipmentLoadDomain.inspect(bar,entered,unit)
            as? LoadInspection.Confirmed ?:return null
        if(!checked.exact || checked.basis!=LoadBasis.BARBELL_TOTAL)return null
        return runCatching {
            // Inventory supports at most 3 decimals. A desired half-load
            // that cannot be expressed at that precision is not attainable.
            fun milli(value:BigDecimal)=
                value.movePointRight(3).longValueExact()
            val needed=milli(entered.subtract(bar.barWeight)
                .divide(BigDecimal("2")))
            require(needed>=0)
            val plates=bar.pairedPlates.entries.map{
                Plate(it.key,milli(it.key),it.value)
            }.sortedByDescending{it.milli}
            require(plates.isNotEmpty() && plates.all{
                it.milli>0 && it.pairs in 1..24
            })
            var examined=0
            val memo=HashMap<Pair<Int,Long>,Solution?>()
            fun solve(index:Int,remaining:Long):Solution? {
                if(remaining==0L)
                    return Solution(0,List(plates.size-index){0})
                if(index==plates.size)return null
                val key=index to remaining
                if(memo.containsKey(key))return memo[key]
                require(++examined<=MAX_STATES)
                val plate=plates[index]
                val count=minOf(plate.pairs,(remaining/plate.milli).toInt())
                var best:Solution?=null
                // Descending order preserves heavier plates on equal
                // total-pair-count solutions (no invented preference scores).
                for(n in count downTo 0) {
                    val tail=solve(index+1,remaining-plate.milli*n) ?:continue
                    val candidate=Solution(n+tail.totalPairs,
                        listOf(n)+tail.counts)
                    if(best==null || candidate.totalPairs<best.totalPairs)
                        best=candidate
                }
                memo[key]=best
                return best
            }
            val solution=solve(0,needed) ?:return null
            val perSide=plates.mapIndexedNotNull{index,plate->
                solution.counts[index].takeIf{it>0}?.let{count->
                    KotlinPlateSide(plate.weight,count)
                }
            }
            val reconstructed=bar.barWeight.add(
                perSide.fold(BigDecimal.ZERO){acc,pair->
                    acc.add(pair.weight.multiply(
                        BigDecimal(pair.countPerSide)))
                }.multiply(BigDecimal("2")))
            require(reconstructed.compareTo(entered)==0)
            KotlinVerifiedPlateLayout(
                bar.barWeight,entered,unit,perSide)
        }.getOrNull()
    }
}
