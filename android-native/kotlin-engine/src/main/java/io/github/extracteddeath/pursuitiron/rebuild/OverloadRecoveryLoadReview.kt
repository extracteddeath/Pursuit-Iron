package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal

/**
 * Independent Kotlin coaching candidate, NOT a write to an issued session.
 *
 * A severe overload attempt should not make the heaviest attempted load
 * tomorrow's all-set target. If a comparable *earlier* session demonstrated
 * every working set at a verified common load, adequate reps and actual
 * effort, this review may offer that demonstrated load for coach/athlete
 * approval. It never estimates an unobserved one-rep max, guesses a plate
 * increment or silently changes logged performance.
 *
 * This is not a full progression/deload policy: injuries, pain, altered
 * exercise variants, long gaps, fatigue and persistent regressions still
 * require separate athlete-context gates before prescription.
 */
enum class OverloadRecoveryReason {
    INCOMPARABLE_WORKOUTS,
    PRIOR_CAPACITY_NOT_DEMONSTRATED,
    LATEST_SESSION_INCOMPLETE,
    INSUFFICIENT_LATEST_EVIDENCE,
    NO_HIGHER_LOAD_ATTEMPT,
    NO_REP_FLOOR_FAILURE,
    PREVIOUS_LOAD_UNVERIFIED,
    RETURN_TO_DEMONSTRATED_LOAD_FOR_REVIEW
}

data class OverloadRecoveryLoadReview(
    val reason: OverloadRecoveryReason,
    val demonstratedLoad: BigDecimal?,
    val heaviestAttemptedLoad: BigDecimal?,
    val candidateReturnLoad: BigDecimal?
) {
    val canOfferForReview: Boolean get() =
        reason == OverloadRecoveryReason.RETURN_TO_DEMONSTRATED_LOAD_FOR_REVIEW
            && candidateReturnLoad != null

    init {
        require((reason == OverloadRecoveryReason.RETURN_TO_DEMONSTRATED_LOAD_FOR_REVIEW)
            == (candidateReturnLoad != null))
    }

    /** This never grants permission to mutate a session or issue a target. */
    val canAutoApply: Boolean get() = false
}

object OverloadRecoveryLoadReviewDomain {
    private fun normal(load: BigDecimal) =
        BigDecimal(load.stripTrailingZeros().toPlainString())

    fun assess(
        previous: ProgressionWorkoutEvidence,
        latest: ProgressionWorkoutEvidence,
        verifiedStock: VerifiedLoadStock?,
        expectedLoadBasis: LoadBasis
    ): OverloadRecoveryLoadReview {
        val comparable = previous.workoutId.isNotBlank() &&
            latest.workoutId.isNotBlank() &&
            previous.workoutId != latest.workoutId &&
            previous.exerciseId.isNotBlank() &&
            previous.exerciseId == latest.exerciseId &&
            previous.unit == latest.unit &&
            previous.targetReps == latest.targetReps &&
            previous.expectedWorkingSets == latest.expectedWorkingSets &&
            previous.expectedWorkingSets in 1..32

        fun result(
            reason: OverloadRecoveryReason,
            prior: BigDecimal? = null,
            heavy: BigDecimal? = null,
            candidate: BigDecimal? = null
        ) = OverloadRecoveryLoadReview(reason, prior, heavy, candidate)

        if (!comparable)
            return result(OverloadRecoveryReason.INCOMPARABLE_WORKOUTS)

        // Domain validation is mandatory even for a refusal. Malformed set
        // values should not become a plausible recovery recommendation.
        val priorFacts = DoubleProgressionEvidenceDomain.assess(
            previous.targetReps, previous.expectedWorkingSets, previous.observations)
        val latestFacts = DoubleProgressionEvidenceDomain.assess(
            latest.targetReps, latest.expectedWorkingSets, latest.observations)

        val priorRows = previous.observations.sortedBy { it.workingOrdinal }
        val latestRows = latest.observations.sortedBy { it.workingOrdinal }
        val priorLoads = priorRows.mapNotNull { it.actualLoad?.let(::normal) }.distinct()
        if (priorFacts.completedWorkingSets != previous.expectedWorkingSets ||
            priorFacts.mixedActualLoads || priorLoads.size != 1 ||
            priorLoads.single().signum() <= 0 ||
            priorFacts.belowFloorOrdinals.isNotEmpty() ||
            priorRows.any { it.actualRir == null || it.actualRir < 2 })
            return result(OverloadRecoveryReason.PRIOR_CAPACITY_NOT_DEMONSTRATED)

        val demonstrated = priorLoads.single()
        if (latestFacts.completedWorkingSets != latest.expectedWorkingSets)
            return result(OverloadRecoveryReason.LATEST_SESSION_INCOMPLETE,
                demonstrated)

        val loads = latestRows.mapNotNull { it.actualLoad?.let(::normal) }
        val heavy = loads.maxOrNull()
        if (latestRows.any { it.actualRir == null } ||
            loads.any { it < demonstrated })
            return result(OverloadRecoveryReason.INSUFFICIENT_LATEST_EVIDENCE,
                demonstrated, heavy)

        if (heavy == null || heavy <= demonstrated)
            return result(OverloadRecoveryReason.NO_HIGHER_LOAD_ATTEMPT,
                demonstrated, heavy)

        if (latestFacts.belowFloorOrdinals.isEmpty())
            return result(OverloadRecoveryReason.NO_REP_FLOOR_FAILURE,
                demonstrated, heavy)

        // A historical load is NOT a safe return target when the latest
        // workout also showed a near-limit rep-floor failure at that very
        // load. A short set with >=2 actual reps in reserve is different:
        // it is not proof that the athlete could not do the required reps.
        // Preserve the user's real sets and request a fresh recovery review.
        if (latestRows.any { row ->
                row.actualLoad?.compareTo(demonstrated) == 0 &&
                    checkNotNull(row.actualReps) < latest.targetReps.minimum &&
                    checkNotNull(row.actualRir) <= 1
            })
            return result(OverloadRecoveryReason.INSUFFICIENT_LATEST_EVIDENCE,
                demonstrated, heavy)

        // Never round, extrapolate or use an unconfirmed gym increment.
        if (verifiedStock?.basis != expectedLoadBasis)
            return result(OverloadRecoveryReason.PREVIOUS_LOAD_UNVERIFIED,
                demonstrated, heavy)
        val inspected = EquipmentLoadDomain.inspect(
            verifiedStock, demonstrated, latest.unit)
        if (inspected !is LoadInspection.Confirmed || !inspected.exact)
            return result(OverloadRecoveryReason.PREVIOUS_LOAD_UNVERIFIED,
                demonstrated, heavy)

        return result(OverloadRecoveryReason.RETURN_TO_DEMONSTRATED_LOAD_FOR_REVIEW,
            demonstrated, heavy, demonstrated)
    }
}
