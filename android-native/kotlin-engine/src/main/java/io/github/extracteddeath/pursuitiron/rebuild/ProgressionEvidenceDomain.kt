package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal

/**
 * Factual double-progression evidence, deliberately NOT a weight prescriber.
 * Neither target RIR nor an exercise's heaviest logged set may masquerade
 * as observed effort or as a load every set could complete.
 */
data class RepBand(val minimum: Int, val maximum: Int) {
    init { require(minimum in 1..100 && maximum in minimum..100) }
}

enum class SetOutcome { COMPLETED, SKIPPED, FAILED_ATTEMPT }

/** All indices are stable original working-set ordinals, not list positions. */
data class WorkingSetFact(
    val workingOrdinal: Int,
    val outcome: SetOutcome,
    val actualLoad: BigDecimal?,
    val actualReps: Int?,
    val actualRir: Int?,
    val issuedLoad: BigDecimal? = null,
    val issuedRir: Int? = null
)

enum class ProgressionEvidenceSignal {
    INCOMPLETE_OR_SKIPPED,
    BELOW_REP_FLOOR,
    BUILDING_REPS,
    MIXED_LOAD_CEILING,
    UNIFORM_LOAD_CEILING_FOR_REVIEW,
    /** Rep ceiling in a signed unweighted bodyweight prescription: no load claim. */
    UNLOADED_REP_CEILING_FOR_REVIEW
}

data class ProgressionEvidence(
    val signal: ProgressionEvidenceSignal,
    val expectedWorkingSets: Int,
    val completedWorkingSets: Int,
    val missingOrdinals: Set<Int>,
    val skippedOrdinals: Set<Int>,
    val failedAttemptOrdinals: Set<Int>,
    val belowFloorOrdinals: Set<Int>,
    val ceilingOrdinals: Set<Int>,
    val nearFailureOrdinals: Set<Int>,
    val actualRirReportedOrdinals: Set<Int>,
    val mixedActualLoads: Boolean
)

/**
 * The result cannot prescribe next load by construction. An eventual
 * separate, certified policy must incorporate exercise-specific loading,
 * fatigue, rep targets, the whole exercise history and explicit user choice.
 */
object DoubleProgressionEvidenceDomain {
    private val zero=BigDecimal.ZERO
    private val maximumLoad=BigDecimal("100000")

    private fun validLoad(value: BigDecimal?): Boolean =
        value != null && value >= zero && value <= maximumLoad &&
            value.stripTrailingZeros().scale() <= 3

    fun assess(
        targets: RepBand,
        expectedWorkingSets: Int,
        observations: List<WorkingSetFact>,
        /** Only the authenticated unweighted source may enable this mode.
         * Never turn missing machine/dumbbell loads into bodyweight work. */
        explicitlyUnloaded: Boolean = false
    ): ProgressionEvidence {
        require(expectedWorkingSets in 1..32)
        require(observations.size <= expectedWorkingSets)
        require(observations.map { it.workingOrdinal }.distinct().size == observations.size)
        for (observation in observations) {
            require(observation.workingOrdinal in 0 until expectedWorkingSets)
            require(if(explicitlyUnloaded)observation.issuedLoad==null
                else observation.issuedLoad==null || validLoad(observation.issuedLoad))
            require(observation.issuedRir == null || observation.issuedRir in 0..10)
            when(observation.outcome) {
                SetOutcome.COMPLETED -> require(
                    (if(explicitlyUnloaded)observation.actualLoad==null
                        else validLoad(observation.actualLoad)) &&
                        observation.actualReps != null && observation.actualReps in 1..100 &&
                        (observation.actualRir == null || observation.actualRir in 0..10))
                SetOutcome.FAILED_ATTEMPT -> require(
                    (if(explicitlyUnloaded)observation.actualLoad==null
                        else validLoad(observation.actualLoad)) &&
                        observation.actualReps == 0 && observation.actualRir == null)
                SetOutcome.SKIPPED -> require(
                    observation.actualLoad == null &&
                        observation.actualReps == null && observation.actualRir == null)
            }
        }
        // Exactly preserve actual reported effort. Missing RIR stays missing,
        // even if an issued target RIR exists for this ordinal.
        val ordered=observations.sortedBy { it.workingOrdinal }
        val completed=ordered.filter { it.outcome == SetOutcome.COMPLETED }
        val skipped=ordered.filter { it.outcome == SetOutcome.SKIPPED }
        val failed=ordered.filter { it.outcome == SetOutcome.FAILED_ATTEMPT }
        val missing=(0 until expectedWorkingSets).filter { i ->
            ordered.none { it.workingOrdinal == i }
        }.toSet()
        val below=completed.filter { it.actualReps!! < targets.minimum }
            .map { it.workingOrdinal }.toSet()
        val atCeiling=completed.filter { it.actualReps!! >= targets.maximum }
            .map { it.workingOrdinal }.toSet()
        val nearFailure=completed.filter { it.actualRir != null && it.actualRir <= 1 }
            .map { it.workingOrdinal }.toSet()
        val effortKnown=completed.filter { it.actualRir != null }
            .map { it.workingOrdinal }.toSet()
        val distinctLoads=if(explicitlyUnloaded)emptyList() else
            completed.map { checkNotNull(it.actualLoad).stripTrailingZeros() }.distinct()
        val mixed=distinctLoads.size>1

        val signal=when {
            completed.size!=expectedWorkingSets -> ProgressionEvidenceSignal.INCOMPLETE_OR_SKIPPED
            below.isNotEmpty() -> ProgressionEvidenceSignal.BELOW_REP_FLOOR
            atCeiling.size!=expectedWorkingSets -> ProgressionEvidenceSignal.BUILDING_REPS
            mixed -> ProgressionEvidenceSignal.MIXED_LOAD_CEILING
            explicitlyUnloaded ->ProgressionEvidenceSignal.UNLOADED_REP_CEILING_FOR_REVIEW
            else -> ProgressionEvidenceSignal.UNIFORM_LOAD_CEILING_FOR_REVIEW
        }
        return ProgressionEvidence(
            signal=signal,
            expectedWorkingSets=expectedWorkingSets,
            completedWorkingSets=completed.size,
            missingOrdinals=missing,
            skippedOrdinals=skipped.map {it.workingOrdinal}.toSet(),
            failedAttemptOrdinals=failed.map {it.workingOrdinal}.toSet(),
            belowFloorOrdinals=below,ceilingOrdinals=atCeiling,
            nearFailureOrdinals=nearFailure,actualRirReportedOrdinals=effortKnown,
            mixedActualLoads=mixed
        )
    }
}
