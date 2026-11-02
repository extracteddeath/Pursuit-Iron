package io.github.extracteddeath.pursuitiron

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.extracteddeath.pursuitiron.domain.*

/**
 * Shared live-set equipment controls. This component NEVER chooses a load,
 * assumes an increment or changes a prescription. Every offered adjacent
 * load comes from the canonical engine's source-bound, verified inventory.
 * The caller still owns the unsaved actual-value draft.
 */
@Composable
internal fun NativeVerifiedLoadOptions(
    session: JournalSessionKey,
    set: NativeWorkoutSet,
    load: String,
    state: NativeRuntimeState,
    enabled: Boolean,
    onLoad: (String)->Unit,
    onInspect: (JournalSetSlot,String)->Unit
) {
    // Never borrow a result from a different workout, set, unit or typed load.
    val review=state.equipmentLoad?.takeIf {
        it.workoutId==session.workoutId && it.slot==set.slot &&
            it.unit==session.unit && it.inputLoad==load
    }
    val validInput=load.matches(Regex("^(0|[1-9][0-9]{0,4})(\\\\.[0-9]{1,3})?$"))
    var showExplanation by rememberSaveable(session.workoutId,set.slot.exerciseIndex,set.slot.setIndex) {
        mutableStateOf(false)
    }
    if(validInput) {
        TextButton(onClick={onInspect(set.slot,load)},
            enabled=enabled && !state.equipmentLoadBusy,
            modifier=Modifier.fillMaxWidth()) {
            Text(when {
                state.equipmentLoadBusy -> "Checking available loads…"
                review?.confirmed==true -> "Refresh equipment load options"
                else -> "Check equipment load options"
            })
        }
    }
    if(review?.confirmed==true) {
        val sourceLabel=when(review.family) {
            "dumbbells_per_hand" -> "Verified dumbbells · per hand"
            "barbell_plates" -> "Verified plates · total load"
            "exercise_override" -> "Verified exercise-specific loading"
            "smith_confirmed_totals" -> "Verified Smith total load"
            "machine_stack" -> "Verified machine stack"
            "cable_stack" -> "Verified cable stack"
            else -> "Verified equipment loads"
        }
        Text(sourceLabel,style=MaterialTheme.typography.labelMedium,
            color=MaterialTheme.colorScheme.primary)
        if(review.previous!=null || review.next!=null) {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                if(review.previous!=null)
                    OutlinedButton(onClick={onLoad(review.previous)},
                        enabled=enabled,modifier=Modifier.weight(1f)) {
                        Text("− ${review.previous} ${session.unit}")
                    }
                if(review.next!=null)
                    OutlinedButton(onClick={onLoad(review.next)},
                        enabled=enabled,modifier=Modifier.weight(1f)) {
                        Text("+ ${review.next} ${session.unit}")
                    }
            }
        } else Text("No adjacent verified load options for this set.",
            style=MaterialTheme.typography.bodySmall,
            color=MaterialTheme.colorScheme.onSurfaceVariant)

        if(!review.exact)
            Text("Entered load is not a selectable inventory value. Manual entry stays available.",
                style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick={showExplanation=!showExplanation}) {
            Text(if(showExplanation)"Hide equipment details" else "About these loads")
        }
        if(showExplanation) {
            Text(if(review.family=="exercise_override" && review.mode=="external_load")
                "Verified machine values reflect your saved logging scale, not effective mechanical resistance. Keep the same basis for every set."
                else "Adjacent loads were verified against this exercise's saved equipment. Only your actual logged value changes when selected.",
                style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else if(review!=null) {
        Text("Equipment not confirmed for this exercise. No assumed load increments are offered.",
            style=MaterialTheme.typography.bodySmall,
            color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if(review==null)state.equipmentLoadError?.let {
        Text(it,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error)
    }
}
