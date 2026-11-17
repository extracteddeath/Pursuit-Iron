package io.github.extracteddeath.pursuitiron

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import io.github.extracteddeath.pursuitiron.rebuild.KotlinHistoryPerformanceSummary

/**
 * Reusable, observational feedback for both History and verified post-Finish.
 * The canonical Kotlin History projection owns the counts. This component
 * makes no progression claim, PR classification, RIR estimate or load change.
 */
@Composable
internal fun NativeKotlinPerformanceEvidenceLines(
    evidence:KotlinHistoryPerformanceSummary
) {
    if(evidence.completedWorkingSets==0)return
    Text(
        "Reps: ${evidence.repsWithinRange} within target · "+
            "${evidence.repsBelowMinimum} below · ${evidence.repsAboveMaximum} above",
        style=MaterialTheme.typography.bodySmall)
    Text(
        "Actual RIR recorded: ${evidence.actualRirReportedWorkingSets} of "+
            "${evidence.completedWorkingSets} completed working sets",
        style=MaterialTheme.typography.bodySmall,
        color=MaterialTheme.colorScheme.onSurfaceVariant)
}
