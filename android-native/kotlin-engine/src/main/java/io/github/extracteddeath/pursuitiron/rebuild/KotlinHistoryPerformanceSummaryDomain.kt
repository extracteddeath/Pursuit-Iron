package io.github.extracteddeath.pursuitiron.rebuild

/**
 * Read-only, factual per-workout summary for the native History surface.
 *
 * Only completed WORKING main sets are compared against their own issued rep
 * bands. Warmups and custom amplifier follow-ups remain separately visible,
 * not inflated into working-set completion or fabricated PRs. This consumes
 * effective corrected facts from the owner-verified native history reader;
 * it never edits them or grants coaching/progression authority.
 */
data class KotlinHistoryPerformanceSummary(
    val workingSets: Int,
    val completedWorkingSets: Int,
    val skippedWorkingSets: Int,
    val failedWorkingSets: Int,
    val repsWithinRange: Int,
    val repsBelowMinimum: Int,
    val repsAboveMaximum: Int,
    val actualRirReportedWorkingSets: Int,
    val followUpVisits: Int,
    val completedFollowUpVisits: Int
) {
    init {
        require(workingSets == completedWorkingSets + skippedWorkingSets + failedWorkingSets)
        require(repsWithinRange + repsBelowMinimum + repsAboveMaximum <= completedWorkingSets)
        require(actualRirReportedWorkingSets in 0..completedWorkingSets)
        require(completedFollowUpVisits in 0..followUpVisits)
    }
}

object KotlinHistoryPerformanceSummaryDomain {
    private data class Row(
        val outcome: KotlinJournalSetOutcome,
        val actual: KotlinPerformedSet?,
        val target: RepBand
    )

    private fun count(
        working: List<Row>,
        followUpVisits: Int = 0,
        completedFollowUpVisits: Int = 0
    ): KotlinHistoryPerformanceSummary {
        val completed = working.filter { it.outcome == KotlinJournalSetOutcome.COMPLETED }
        return KotlinHistoryPerformanceSummary(
            workingSets = working.size,
            completedWorkingSets = completed.size,
            skippedWorkingSets = working.count { it.outcome == KotlinJournalSetOutcome.SKIPPED },
            failedWorkingSets = working.count {
                it.outcome == KotlinJournalSetOutcome.FAILED_ATTEMPT
            },
            repsWithinRange = completed.count {
                it.actual?.reps?.let { reps ->
                    reps in it.target.minimum..it.target.maximum
                } == true
            },
            repsBelowMinimum = completed.count {
                it.actual?.reps?.let { reps -> reps < it.target.minimum } == true
            },
            repsAboveMaximum = completed.count {
                it.actual?.reps?.let { reps -> reps > it.target.maximum } == true
            },
            actualRirReportedWorkingSets = completed.count { it.actual?.actualRir != null },
            followUpVisits = followUpVisits,
            completedFollowUpVisits = completedFollowUpVisits
        )
    }

    fun fromComposer(sets: List<KotlinHistoricalSetFact>): KotlinHistoryPerformanceSummary =
        count(sets.filter { it.type == DraftSetType.WORKING }.map {
            Row(it.outcome, it.actual, it.issuedReps)
        })

    fun fromCustom(visits: List<CustomHistoricalVisitFact>): KotlinHistoryPerformanceSummary {
        val main = visits.filter {
            it.part == CustomWorkoutVisitPart.MAIN && it.setType == DraftSetType.WORKING
        }
        val followUps = visits.filter { it.part == CustomWorkoutVisitPart.FOLLOW_UP }
        return count(
            main.map { Row(it.outcome, it.actual, it.prescribedReps) },
            followUps.size,
            followUps.count { it.outcome == KotlinJournalSetOutcome.COMPLETED }
        )
    }
}
