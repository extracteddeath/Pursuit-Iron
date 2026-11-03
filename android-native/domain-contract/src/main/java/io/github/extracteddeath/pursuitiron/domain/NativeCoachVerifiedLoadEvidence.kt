package io.github.extracteddeath.pursuitiron.domain

import org.json.JSONObject
import java.math.BigDecimal
import java.util.Collections

/**
 * Read-only evidence boundary for the independent Kotlin coaching rewrite.
 *
 * The canonical saved program is STILL the authority for training and issued
 * workouts. These facts are not an instruction to prescribe, snap, infer or
 * change a load. The caller must bind the result to the authenticated saved
 * program revision; an untrusted inventory string cannot authorize anything.
 *
 * Only exercise IDs issued by the current canonical program are eligible.
 * A generic machine range, barbell plate catalog or other shared equipment
 * is deliberately NOT converted to an individual exercise's resistance.
 */
sealed interface NativeCoachVerifiedLoadFact {
    val exerciseId: String
    val name: String
    val unit: String

    /** Actual selectable values as logged, not reconstructed mechanical force. */
    data class Exact(
        override val exerciseId: String,
        override val name: String,
        override val unit: String,
        val loads: List<BigDecimal>
    ): NativeCoachVerifiedLoadFact

    /** Confirmed cable / standard machine stack only; never specialty gear. */
    data class Range(
        override val exerciseId: String,
        override val name: String,
        override val unit: String,
        val minimum: BigDecimal,
        val increment: BigDecimal,
        val maximum: BigDecimal
    ): NativeCoachVerifiedLoadFact
}

data class NativeCoachVerifiedLoadReview(
    val candidateCount: Int,
    val facts: List<NativeCoachVerifiedLoadFact>
) {
    /** No verified fact is not a reason to infer an increment. */
    val remainingManual: Int get()=candidateCount-facts.size
}

object NativeCoachVerifiedLoadEvidence {
    private fun number(value: Any): BigDecimal {
        require(value is Number)
        return BigDecimal(BigDecimal(value.toString()).stripTrailingZeros().toPlainString())
    }

    /**
     * Fail closed on unknown, duplicate or ambiguous candidate identities, an
     * invalid inventory, a mismatched unit or an unapproved exercise override.
     * This is NOT a serialization format nor the new engine's input owner.
     */
    fun review(
        raw: String?,
        unit: String,
        candidates: List<NativeStackCandidate>
    ): NativeCoachVerifiedLoadReview {
        require(unit=="lb" || unit=="kg")
        require(candidates.size<=48)
        require(candidates.map { it.id }.distinct().size==candidates.size)
        require(candidates.all { it.id.matches(Regex("^[A-Za-z0-9][A-Za-z0-9_-]{0,79}$")) &&
            it.name.isNotBlank() && it.name.length<=120 &&
            it.mode in setOf("external_load","cable_stack","machine_stack") })
        if(raw==null)return NativeCoachVerifiedLoadReview(candidates.size,emptyList())
        val allowed=candidates.map { it.id }.toSet()
        val exactOnly=candidates.filter { it.mode=="external_load" }.map { it.id }.toSet()
        val validated=NativeGymInventoryCodec.validate(raw,unit,allowed,exactOnly)
        val overrides=JSONObject(validated).optJSONObject("exerciseOverrides")
        val facts=candidates.mapNotNull { candidate ->
            if(overrides==null || !overrides.has(candidate.id))return@mapNotNull null
            val value=overrides.getJSONObject(candidate.id)
            if(value.has("availableLoads")) {
                val loads=value.getJSONArray("availableLoads")
                NativeCoachVerifiedLoadFact.Exact(candidate.id,candidate.name,unit,
                    Collections.unmodifiableList(
                        (0 until loads.length()).map { index->number(loads.get(index)) }))
            } else {
                require(candidate.mode!="external_load")
                NativeCoachVerifiedLoadFact.Range(candidate.id,candidate.name,unit,
                    number(value.get("minimum")),
                    number(value.get("increment")),
                    number(value.get("maximum")))
            }
        }
        return NativeCoachVerifiedLoadReview(candidates.size,
            Collections.unmodifiableList(facts))
    }
}
