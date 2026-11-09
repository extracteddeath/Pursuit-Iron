package io.github.extracteddeath.pursuitiron

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.extracteddeath.pursuitiron.nativecoachstore.NativeStrengthClubEvidence
import io.github.extracteddeath.pursuitiron.nativecoachstore.NativeStrengthClubDomain
import java.math.BigDecimal
import java.math.RoundingMode

private fun clubLb(value:BigDecimal):String =
    value.setScale(1,RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()

/**
 * Display-only evidence. A projected Epley total never becomes a tested,
 * officially certified or logged-single club achievement.
 */
@Composable
internal fun NativeKotlinStrengthClubPanel(
    evidence:NativeStrengthClubEvidence?,
    onOpenStandards:(()->Unit)?=null
) {
    PursuitCard {
        PursuitSectionHeading("1,000 lb club",
            "Bench press + back squat + conventional deadlift · barbell working sets")
        if(evidence==null) {
            Text("Verified completed workout history is unavailable. No strength total can be shown.",
                style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            val singles=evidence.loggedSinglesTotalLb
            val projected=evidence.projectedTotalLb
            Text(if(singles==null)
                "Logged 1-rep total · ${evidence.loggedSinglesByLiftLb.size} of 3 lifts"
                else "Logged 1-rep total · ${clubLb(singles)} / 1,000 lb",
                style=MaterialTheme.typography.titleMedium)
            if(singles!=null) {
                LinearProgressIndicator(progress={(singles.divide(
                    BigDecimal("1000"),4,RoundingMode.HALF_UP
                ).toFloat()).coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth())
            }
            if(evidence.hasLoggedThousandPoundTotal)
                Text("1,000 lb logged-single threshold met · self-reported workout records, not competition-certified.",
                    style=MaterialTheme.typography.bodyMedium,
                    color=MaterialTheme.colorScheme.primary)
            Text(if(projected==null)
                "Estimated 1RM total · ${evidence.projectedByLiftLb.size} of 3 lifts have qualifying 1–10-rep sets"
                else "Estimated 1RM total · ${clubLb(projected)} lb (Epley projection)",
                style=MaterialTheme.typography.bodyMedium)
            Text("Only corrected, completed barbell working sets count. Estimated 1RMs do not earn a logged-single milestone. Smith, machine and dumbbell loads are not interchangeable.",
                style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if(onOpenStandards!=null)
            OutlinedButton(onClick=onOpenStandards,
                modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {
                Text("View strength standards and lift history")
            }
    }
}
