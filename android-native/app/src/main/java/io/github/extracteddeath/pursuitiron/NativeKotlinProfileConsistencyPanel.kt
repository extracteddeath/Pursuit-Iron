package io.github.extracteddeath.pursuitiron

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** A compact, non-competitive view of recorded training attendance. Missing
 * Finish dates are disclosed, not converted into a fake streak. */
@Composable
internal fun NativeKotlinProfileConsistencyPanel(
    report:NativeProfileConsistencyReport?
) {
    PursuitCard {
        PursuitSectionHeading(
            "Training consistency",
            "Last 8 calendar weeks · recorded workout Finish dates"
        )
        if(report==null) {
            Text("Consistency is unavailable until native History is verified.",
                style=MaterialTheme.typography.bodyMedium,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
            return@PursuitCard
        }
        PursuitMetricStrip(listOf(
            "Sessions" to report.sessionsInWindow.toString(),
            "Active days" to report.activeDaysInWindow.toString(),
            "Active weeks" to "${report.activeWeeks} / ${report.weeks.size}"
        ))
        if(report.sessionsInWindow==0) {
            Text("No workouts with recorded Finish dates in this period.",
                style=MaterialTheme.typography.bodyMedium,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            val highest=report.weeks.maxOf{it.sessions}.coerceAtLeast(1)
            val shortDate=DateTimeFormatter.ofPattern("M/d",Locale.getDefault())
            val longDate=DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                .withLocale(Locale.getDefault())
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                if(maxWidth<370.dp || LocalDensity.current.fontScale>=1.3f) {
                    Column(verticalArrangement=Arrangement.spacedBy(PursuitSpace.xs)) {
                        report.weeks.asReversed().forEach { week ->
                            Row(Modifier.fillMaxWidth().semantics(mergeDescendants=true) {
                                contentDescription="Week of ${week.weekStartingMonday.format(longDate)}: " +
                                    "${week.sessions} recorded workouts over ${week.activeDays} training days"
                            },verticalAlignment=Alignment.CenterVertically,
                                horizontalArrangement=Arrangement.spacedBy(PursuitSpace.xs)) {
                                Text(week.weekStartingMonday.format(shortDate),
                                    modifier=Modifier.width(42.dp),
                                    style=MaterialTheme.typography.labelMedium)
                                LinearProgressIndicator(
                                    progress={week.sessions.toFloat()/highest.toFloat()},
                                    modifier=Modifier.weight(1f)
                                )
                                Text(week.sessions.toString(),
                                    style=MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                } else {
                    Row(Modifier.fillMaxWidth(),
                        horizontalArrangement=Arrangement.spacedBy(PursuitSpace.xxs),
                        verticalAlignment=Alignment.Bottom) {
                        report.weeks.forEach {week ->
                            Column(Modifier.weight(1f).semantics(mergeDescendants=true) {
                                contentDescription="Week of ${week.weekStartingMonday.format(longDate)}: " +
                                    "${week.sessions} recorded workouts over ${week.activeDays} training days"
                            },horizontalAlignment=Alignment.CenterHorizontally,
                                verticalArrangement=Arrangement.spacedBy(PursuitSpace.xxs)) {
                                Text(week.sessions.toString(),
                                    style=MaterialTheme.typography.labelSmall)
                                Box(Modifier.fillMaxWidth().height(64.dp),
                                    contentAlignment=Alignment.BottomCenter) {
                                    if(week.sessions>0) Box(
                                        Modifier.fillMaxWidth(0.72f)
                                            .fillMaxHeight(week.sessions.toFloat()/highest.toFloat())
                                            .background(MaterialTheme.colorScheme.primary,
                                                RoundedCornerShape(topStart=6.dp,topEnd=6.dp))
                                    ) else Box(
                                        Modifier.fillMaxWidth(0.72f).height(3.dp)
                                            .background(MaterialTheme.colorScheme.outlineVariant,
                                                RoundedCornerShape(2.dp))
                                    )
                                }
                                Text(week.weekStartingMonday.format(shortDate),
                                    style=MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
        if(report.undatedOrInvalidSessions>0)
            Text("${report.undatedOrInvalidSessions} finished ${if(report.undatedOrInvalidSessions==1)"workout has" else "workouts have"} no usable Finish date and cannot be placed in a week.",
                style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(report.futureDatedSessions>0)
            Text("${report.futureDatedSessions} future-dated ${if(report.futureDatedSessions==1)"workout is" else "workouts are"} excluded from this past-activity chart. Review the device clock.",
                style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.error)
        Text("Weeks start Monday, using your device’s time zone. Older workouts remain in History. Activity here never changes your training, levels or coaching.",
            style=MaterialTheme.typography.bodySmall,
            color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
