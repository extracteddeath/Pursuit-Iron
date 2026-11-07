package io.github.extracteddeath.pursuitiron.rebuild

/** Actual effort stays nullable in history. Only progression may use a goal assumption. */
enum class ProgressionEffortSource { REPORTED, ASSUMED_GOAL, UNAVAILABLE }

data class ProgressionEffort(val rir: Int?, val source: ProgressionEffortSource) {
    val assumed: Boolean get() = source == ProgressionEffortSource.ASSUMED_GOAL
}

object ProgressionEffortDomain {
    fun forCompletedSet(actualRir: Int?, issuedGoalRir: Int?): ProgressionEffort {
        require(actualRir == null || actualRir in 0..10)
        require(issuedGoalRir == null || issuedGoalRir in 0..10)
        return when {
            actualRir != null -> ProgressionEffort(actualRir, ProgressionEffortSource.REPORTED)
            issuedGoalRir != null -> ProgressionEffort(issuedGoalRir, ProgressionEffortSource.ASSUMED_GOAL)
            else -> ProgressionEffort(null, ProgressionEffortSource.UNAVAILABLE)
        }
    }

    fun resolve(set: WorkingSetFact): ProgressionEffort =
        if (set.outcome == SetOutcome.COMPLETED) forCompletedSet(set.actualRir, set.issuedRir)
        else ProgressionEffort(null, ProgressionEffortSource.UNAVAILABLE)

    fun assumptionNote(count: Int): String = if (count == 0) "" else
        " RIR was not entered for $count sets, so this review assumes their original goal RIR. Logged actual RIR remains unreported."
}
