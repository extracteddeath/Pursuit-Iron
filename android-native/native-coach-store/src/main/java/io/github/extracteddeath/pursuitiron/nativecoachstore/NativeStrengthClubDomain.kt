package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.LoadBasis
import io.github.extracteddeath.pursuitiron.rebuild.LoadUnit
import io.github.extracteddeath.pursuitiron.rebuild.StrengthStandardsMeasurementDomain
import java.math.BigDecimal
import java.util.Collections

/**
 * Factual, read-only progress toward a 1,000 lb bench/squat/deadlift total.
 *
 * Caller MUST pass an owner-verified, correction-aware mixed-history facts
 * projection. No training target, machine, dumbbell, Smith, warm-up, skipped
 * set, failed attempt or follow-up amplifier can qualify. This module never
 * writes achievements, program changes or progression.
 *
 * An estimated 1RM is useful for progress display but NOT an actual logged
 * single and must NEVER grant a club milestone.
 */
data class NativeStrengthClubEvidence(
    val projectedByLiftLb:Map<String,BigDecimal>,
    val loggedSinglesByLiftLb:Map<String,BigDecimal>,
    val projectedTotalLb:BigDecimal?,
    val loggedSinglesTotalLb:BigDecimal?
) {
    val hasLoggedThousandPoundTotal:Boolean
        get()=loggedSinglesTotalLb?.let { it>=BigDecimal("1000") }==true
}

object NativeStrengthClubDomain {
    val requiredLiftIds=listOf(
        "barbell_bench", "back_squat", "conventional_deadlift"
    )
    fun review(facts:NativeKotlinMixedHistoryFacts):NativeStrengthClubEvidence {
        val projected=linkedMapOf<String,BigDecimal>()
        val singles=linkedMapOf<String,BigDecimal>()
        for(fact in facts.mostRepsAtLoad) {
            if(fact.key.exerciseId !in requiredLiftIds ||
                fact.key.basis!=LoadBasis.BARBELL_TOTAL)continue
            val estimate=StrengthStandardsMeasurementDomain.epley(
                fact.actualLoad,fact.reps)?:continue
            val estimateLb=StrengthStandardsMeasurementDomain.convert(
                estimate,fact.key.unit,LoadUnit.LB)
            val id=fact.key.exerciseId
            if(estimateLb>(projected[id]?:BigDecimal.ZERO))
                projected[id]=estimateLb
        }
        // Singles are a SEPARATE immutable fact projection. Choosing the
        // highest rep-count at a load must never suppress a logged 1RM.
        for(fact in facts.bestLoggedSingleByLoad.values) {
            if(fact.key.exerciseId !in requiredLiftIds ||
                fact.key.basis!=LoadBasis.BARBELL_TOTAL ||
                fact.reps!=1)continue
            val lb=StrengthStandardsMeasurementDomain.convert(
                fact.actualLoad,fact.key.unit,LoadUnit.LB)
            val id=fact.key.exerciseId
            if(lb>(singles[id]?:BigDecimal.ZERO))singles[id]=lb
        }
        fun completedTotal(rows:Map<String,BigDecimal>):BigDecimal? =
            if(requiredLiftIds.all {it in rows})
                requiredLiftIds.fold(BigDecimal.ZERO) {sum,id->
                    sum+checkNotNull(rows[id])
                }
            else null
        return NativeStrengthClubEvidence(
            Collections.unmodifiableMap(projected.toMap()),
            Collections.unmodifiableMap(singles.toMap()),
            completedTotal(projected),completedTotal(singles)
        )
    }
}
