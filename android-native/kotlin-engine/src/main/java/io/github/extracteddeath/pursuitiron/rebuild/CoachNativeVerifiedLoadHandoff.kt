package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import java.util.Collections

/**
 * Standalone Kotlin-side counterpart to Compose's NativeCoachVerifiedLoadEvidence.
 *
 * A future native owner must first obtain this evidence from a CURRENT,
 * authenticated saved program and re-check its revision before handing it off.
 * This domain does not parse JS snapshots, issue training, persist results,
 * authenticate the owner, or promote old coach reviews into prescriptions.
 */
data class CoachCanonicalLoadRevision(
    val programId: String,
    val programUpdatedAt: Long,
    val storeSavedAt: Long,
    val storeVersion: Int,
    val snapshotSha256: String
)

data class CoachEligibleLoadCandidate(
    val id: String,
    val name: String,
    val mode: String
)

/** DTOs match the native read-only projection; not an independent inventory. */
sealed interface CoachNativeExerciseLoadFact {
    val exerciseId: String
    val name: String
    val unit: String

    data class Exact(
        override val exerciseId: String,
        override val name: String,
        override val unit: String,
        val loads: List<BigDecimal>
    ) : CoachNativeExerciseLoadFact

    data class Range(
        override val exerciseId: String,
        override val name: String,
        override val unit: String,
        val minimum: BigDecimal,
        val increment: BigDecimal,
        val maximum: BigDecimal
    ) : CoachNativeExerciseLoadFact
}

data class CoachNativeLoadEvidence(
    val reference: CoachCanonicalLoadRevision,
    val candidateCount: Int,
    val facts: List<CoachNativeExerciseLoadFact>
)

enum class CoachLoadHandoffRejection {
    INVALID_REVISION, STALE_REVISION, INVALID_CANDIDATES,
    INVALID_EVIDENCE, INVALID_PHYSICAL_STOCK
}

sealed interface CoachLoadHandoffReview {
    data class Rejected(val reason: CoachLoadHandoffRejection) : CoachLoadHandoffReview
    data class Accepted(
        val reference: CoachCanonicalLoadRevision,
        val byExerciseId: Map<String, VerifiedLoadStock>,
        val manualExerciseIds: Set<String>
    ) : CoachLoadHandoffReview {
        val canIssueWorkout: Boolean get() = false
        val canApplyProgression: Boolean get() = false
    }
}

object CoachNativeVerifiedLoadHandoff {
    private val id = Regex("^[A-Za-z0-9][A-Za-z0-9_-]{0,127}$")
    private val candidateId = Regex("^[A-Za-z0-9][A-Za-z0-9_-]{0,79}$")
    private val digest = Regex("^[0-9a-f]{64}$")
    private val maxClock = 9_007_199_254_740_991L
    private val maxLoad = BigDecimal("99999.999")
    private val zero = BigDecimal.ZERO

    private fun validRevision(ref: CoachCanonicalLoadRevision): Boolean =
        id.matches(ref.programId) && ref.programUpdatedAt in 0..maxClock &&
            ref.storeSavedAt in 0..maxClock && ref.storeVersion == 13 &&
            digest.matches(ref.snapshotSha256)

    private fun validNumber(value: BigDecimal): Boolean =
        value >= zero && value <= maxLoad &&
            value.stripTrailingZeros().scale() <= 3

    private fun normalized(value: BigDecimal): BigDecimal =
        BigDecimal(value.stripTrailingZeros().toPlainString())

    /**
     * Fail closed on any malformed input: even one bad override invalidates the
     * entire handoff, never just the offending exercise. A validated revision
     * must STILL match the current authenticated store before each use.
     */
    fun review(
        evidence: CoachNativeLoadEvidence,
        current: CoachCanonicalLoadRevision,
        candidates: List<CoachEligibleLoadCandidate>,
        expectedUnit: LoadUnit
    ): CoachLoadHandoffReview {
        if (!validRevision(current) || !validRevision(evidence.reference))
            return CoachLoadHandoffReview.Rejected(CoachLoadHandoffRejection.INVALID_REVISION)
        if (current != evidence.reference)
            return CoachLoadHandoffReview.Rejected(CoachLoadHandoffRejection.STALE_REVISION)
        if (candidates.size > 48 || candidates.distinctBy { it.id }.size != candidates.size ||
            candidates.any {
                !candidateId.matches(it.id) || it.name.isBlank() || it.name.length > 120 ||
                    it.mode !in setOf("external_load", "cable_stack", "machine_stack")
            })
            return CoachLoadHandoffReview.Rejected(CoachLoadHandoffRejection.INVALID_CANDIDATES)
        if (evidence.candidateCount != candidates.size ||
            evidence.facts.size > candidates.size ||
            evidence.facts.map { it.exerciseId }.distinct().size != evidence.facts.size)
            return CoachLoadHandoffReview.Rejected(CoachLoadHandoffRejection.INVALID_EVIDENCE)

        val known = candidates.associateBy { it.id }
        val stock = linkedMapOf<String, VerifiedLoadStock>()
        for (fact in evidence.facts) {
            val candidate = known[fact.exerciseId]
                ?: return CoachLoadHandoffReview.Rejected(CoachLoadHandoffRejection.INVALID_EVIDENCE)
            if (candidate.name != fact.name ||
                fact.unit != (if (expectedUnit == LoadUnit.LB) "lb" else "kg"))
                return CoachLoadHandoffReview.Rejected(CoachLoadHandoffRejection.INVALID_EVIDENCE)
            val unit = expectedUnit
            val physical = when (fact) {
                is CoachNativeExerciseLoadFact.Exact -> {
                    if (fact.loads.size !in 1..250 || fact.loads.any { !validNumber(it) } ||
                        fact.loads.map(::normalized).distinct().size != fact.loads.size)
                        return CoachLoadHandoffReview.Rejected(CoachLoadHandoffRejection.INVALID_PHYSICAL_STOCK)
                    VerifiedLoadStock.Totals(
                        unit, LoadBasis.EXERCISE_OVERRIDE,
                        Collections.unmodifiableList(fact.loads.map(::normalized))
                    )
                }
                is CoachNativeExerciseLoadFact.Range -> {
                    if (candidate.mode == "external_load" ||
                        !validNumber(fact.minimum) || !validNumber(fact.maximum) ||
                        !validNumber(fact.increment) || fact.increment <= zero ||
                        fact.minimum > fact.maximum)
                        return CoachLoadHandoffReview.Rejected(CoachLoadHandoffRejection.INVALID_PHYSICAL_STOCK)
                    VerifiedLoadStock.Stack(
                        unit, LoadBasis.EXERCISE_OVERRIDE,
                        normalized(fact.minimum), normalized(fact.increment),
                        normalized(fact.maximum)
                    )
                }
            }
            // The physical solver independently rechecks range bounds,
            // duplicates, precision, and attainable-list explosion.
            if (EquipmentLoadDomain.inspect(physical, zero, unit)
                is LoadInspection.Unavailable)
                return CoachLoadHandoffReview.Rejected(CoachLoadHandoffRejection.INVALID_PHYSICAL_STOCK)
            stock[fact.exerciseId] = physical
        }
        val manual = candidates.map { it.id }.filterNot { it in stock.keys }.toSet()
        return CoachLoadHandoffReview.Accepted(
            current, Collections.unmodifiableMap(stock),
            Collections.unmodifiableSet(manual)
        )
    }

    /**
     * A stale review or unverified exercise never gains selectable loads.
     * Only a CURRENT reference checked by the native owner can use this
     * non-prescribing inspection.
     */
    fun inspect(
        review: CoachLoadHandoffReview,
        current: CoachCanonicalLoadRevision,
        exerciseId: String,
        entered: BigDecimal,
        unit: LoadUnit
    ): LoadInspection {
        val trusted = review as? CoachLoadHandoffReview.Accepted
        if (trusted == null || !validRevision(current) || trusted.reference != current ||
            !candidateId.matches(exerciseId))
            return LoadInspection.Unavailable(LoadUnavailableReason.NO_CONFIRMED_STOCK)
        return EquipmentLoadDomain.inspect(trusted.byExerciseId[exerciseId], entered, unit)
    }

    /** A progression review is only a candidate. Never author or apply a load. */
    fun progressionReview(
        review: CoachLoadHandoffReview,
        current: CoachCanonicalLoadRevision,
        previous: ProgressionWorkoutEvidence,
        latest: ProgressionWorkoutEvidence,
        maximumAllowedStep: BigDecimal
    ): IncreaseReview? {
        val trusted = review as? CoachLoadHandoffReview.Accepted ?: return null
        if (!validRevision(current) || trusted.reference != current ||
            !candidateId.matches(latest.exerciseId) || latest.exerciseId != previous.exerciseId)
            return null
        return ConservativeProgressionReview.assess(
            previous, latest, trusted.byExerciseId[latest.exerciseId], maximumAllowedStep
        )
    }
}
