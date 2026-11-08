package io.github.extracteddeath.pursuitiron

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import io.github.extracteddeath.pursuitiron.domain.NativeRestCue
import io.github.extracteddeath.pursuitiron.rebuild.KotlinWorkoutRestPresentation

/** Shares the native elapsed-time countdown; the Kotlin projection supplies
 * only exact issued durations. Never a new workout or history authority. */
@Composable
internal fun NativeKotlinWorkoutRestTimer(
    workoutId:String,
    rest:KotlinWorkoutRestPresentation,
    enabled:Boolean
) {
    // Completing the last required visit removes the countdown immediately;
    // there is no artificial post-workout rest or timer on a finished workout.
    if(!rest.hasRemainingWork)return
    if(rest.manualIsSuperset && rest.manualRestSeconds>0)Text(
        "Superset · start rest when your round is complete",
        style=MaterialTheme.typography.labelSmall,
        color=MaterialTheme.colorScheme.onSurfaceVariant)
    NativeRestTimer(workoutId,rest.manualRestSeconds,enabled,
        latestReceiptOperationId=rest.latestReceiptEventId,
        autoCue=rest.automaticCue?.let {
            NativeRestCue(it.eventId,it.seconds,it.exerciseIndex)
        })
}
