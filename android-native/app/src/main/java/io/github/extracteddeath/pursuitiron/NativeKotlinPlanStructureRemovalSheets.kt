package io.github.extracteddeath.pursuitiron

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Both confirmations are scrollable and keep the destructive action in the
 * anchored footer for keyboard use and large font sizes. No confirmation
 * token persists across app/process recreation or athlete switching. */
@Composable
internal fun NativeKotlinPlanDayRemovalSheet(
    review:NativePlanDayRemovalReview,
    busy:Boolean,
    stillCurrent:Boolean,
    onConfirm:()->Unit,
    onDismiss:()->Unit
) {
    NativeEditSheet(
        busy=busy,
        onDismiss=onDismiss,
        footer={
            Button(onClick=onConfirm,enabled=!busy && stillCurrent,
                modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {
                Text("Remove training day")
            }
            TextButton(onClick=onDismiss,enabled=!busy,
                modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {
                Text("Keep training day")
            }
        }
    ) {
        Text("Remove training day?",
            style=MaterialTheme.typography.titleLarge)
        Text("${review.dayLabel} · ${review.blockLabel}",
            style=MaterialTheme.typography.titleMedium)
        Text(
            "The day is removed from every one of this block's "+
                "${review.blockWeeks} weeks, including ${review.exerciseCount} exercise "+
                "visits, ${review.workingSets} base working sets and "+
                "${review.warmupSets} warm-ups per week.",
            style=MaterialTheme.typography.bodyMedium)
        if(review.pinnedExercises>0)Text(
            "This day includes ${review.pinnedExercises} pinned exercise(s).",
            style=MaterialTheme.typography.bodyMedium)
        if(review.overrideCount>0)Text(
            "Deletes ${review.overrideCount} week-specific prescription(s) in "+
                "week(s) ${review.overrideWeeks.joinToString(", ")} "+
                "with ${review.overrideWorkingSets} override working sets. "+
                "These custom values are NOT transferred to another day.",
            style=MaterialTheme.typography.bodyMedium,
            color=MaterialTheme.colorScheme.error)
        if(review.remainingDays==0)Text(
            "This will leave the block without a training day. "+
                "It cannot issue new workouts until the program passes review.",
            style=MaterialTheme.typography.bodyMedium,
            color=MaterialTheme.colorScheme.error)
        Text(
            "Other training days, prior issued workouts, corrected History and "+
                "actual logged sets remain unchanged. A new full-cycle review "+
                "and approval are required. Undo is available immediately "+
                "after a successful save.",
            style=MaterialTheme.typography.bodySmall,
            color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(!stillCurrent)Text(
            "The saved plan changed. Close and reopen removal to review the "+
                "current day before making changes.",
            style=MaterialTheme.typography.bodyMedium,
            color=MaterialTheme.colorScheme.error)
    }
}

@Composable
internal fun NativeKotlinPlanBlockRemovalSheet(
    review:NativePlanBlockRemovalReview,
    busy:Boolean,
    stillCurrent:Boolean,
    onConfirm:()->Unit,
    onDismiss:()->Unit
) {
    NativeEditSheet(
        busy=busy,
        onDismiss=onDismiss,
        footer={
            Button(onClick=onConfirm,enabled=!busy && stillCurrent,
                modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {
                Text("Remove entire training block")
            }
            TextButton(onClick=onDismiss,enabled=!busy,
                modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {
                Text("Keep training block")
            }
        }
    ) {
        Text("Remove entire training block?",
            style=MaterialTheme.typography.titleLarge)
        Text(review.blockLabel,style=MaterialTheme.typography.titleMedium)
        Text(
            "Deletes this ${review.blockWeeks}-week authored phase "+
                "(${review.trainingDays} training day(s), "+
                "${review.exerciseVisits} exercise visits, "+
                "${review.workingSets} base working sets and "+
                "${review.warmupSets} warm-ups per week).",
            style=MaterialTheme.typography.bodyMedium)
        if(review.pinnedExercises>0)Text(
            "The phase contains ${review.pinnedExercises} pinned exercise visits.",
            style=MaterialTheme.typography.bodyMedium)
        if(review.overrideCount>0)Text(
            "All ${review.overrideCount} authored week-specific prescription(s) "+
                "in this phase are deleted, including "+
                "${review.overrideWorkingSets} exception working sets.",
            style=MaterialTheme.typography.bodyMedium,
            color=MaterialTheme.colorScheme.error)
        if(review.shiftsLaterBlockWeeks)Text(
            "Later blocks move earlier in the FUTURE cycle calendar by "+
                "${review.blockWeeks} weeks. Already issued and completed "+
                "workouts retain their original saved identities and records.",
            style=MaterialTheme.typography.bodyMedium,
            color=MaterialTheme.colorScheme.error)
        Text(
            "${review.remainingBlocks} block(s) remain unchanged in content. "+
                "This is a draft-only change; it requires a fresh full-cycle "+
                "review and approval. Undo is available immediately after saving.",
            style=MaterialTheme.typography.bodySmall,
            color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(!stillCurrent)Text(
            "The saved plan changed. Close and reopen removal before making "+
                "changes to its training blocks.",
            style=MaterialTheme.typography.bodyMedium,
            color=MaterialTheme.colorScheme.error)
    }
}
