package io.github.extracteddeath.pursuitiron.rebuild

/**
 * Describes how real completed reps vary within one workout at a stable
 * external load. This is observed history, not a prediction of next set
 * capacity, a new training load, or evidence of a medical diagnosis.
 */
enum class FatiguePattern {
    INCOMPLETE_OR_UNCOMPARABLE,
    MIXED_LOADS,
    REPS_STABLE,
    REPS_DECLINING,
    REPS_INCREASING,
    NON_MONOTONIC_REPS
}

data class SessionFatigueEvidence(
    val pattern: FatiguePattern,
    val repChangesBetweenSets: List<Int>,
    val firstSetReps: Int?,
    val lastSetReps: Int?,
    val totalRepChange: Int?,
    val targetFloorMisses: Set<Int>,
    val reportedRirChange: Int?,
    val actualRirReportedSets: Set<Int>
) {
    init {
        if(pattern==FatiguePattern.INCOMPLETE_OR_UNCOMPARABLE ||
            pattern==FatiguePattern.MIXED_LOADS) {
            require(repChangesBetweenSets.isEmpty() && totalRepChange==null &&
                reportedRirChange==null)
        }
    }
}

object SessionFatigueEvidenceDomain {
    fun inspect(
        repBand: RepBand,
        expectedWorkingSets: Int,
        observations: List<WorkingSetFact>
    ): SessionFatigueEvidence {
        val facts=DoubleProgressionEvidenceDomain.assess(repBand,expectedWorkingSets,observations)
        val ordered=observations.sortedBy {it.workingOrdinal}
        val comparable=facts.completedWorkingSets==expectedWorkingSets &&
            expectedWorkingSets>=2

        val first=ordered.firstOrNull {it.outcome==SetOutcome.COMPLETED}
        val last=ordered.lastOrNull {it.outcome==SetOutcome.COMPLETED}
        val pattern=when {
            !comparable -> FatiguePattern.INCOMPLETE_OR_UNCOMPARABLE
            facts.mixedActualLoads -> FatiguePattern.MIXED_LOADS
            else -> {
                val changes=ordered.zipWithNext {a,b->b.actualReps!!-a.actualReps!!}
                when {
                    changes.all {it==0} -> FatiguePattern.REPS_STABLE
                    changes.all {it<=0} -> FatiguePattern.REPS_DECLINING
                    changes.all {it>=0} -> FatiguePattern.REPS_INCREASING
                    else -> FatiguePattern.NON_MONOTONIC_REPS
                }
            }
        }
        val details=comparable && !facts.mixedActualLoads
        val rirChange=if(details && first?.actualRir!=null && last?.actualRir!=null)
            last.actualRir-first.actualRir else null
        return SessionFatigueEvidence(
            pattern=pattern,
            repChangesBetweenSets=if(details)
                ordered.zipWithNext {a,b->b.actualReps!!-a.actualReps!!} else emptyList(),
            firstSetReps=first?.actualReps,
            lastSetReps=last?.actualReps,
            totalRepChange=if(details)last!!.actualReps!!-first!!.actualReps!! else null,
            targetFloorMisses=facts.belowFloorOrdinals,
            reportedRirChange=rirChange,
            actualRirReportedSets=facts.actualRirReportedOrdinals
        )
    }
}
