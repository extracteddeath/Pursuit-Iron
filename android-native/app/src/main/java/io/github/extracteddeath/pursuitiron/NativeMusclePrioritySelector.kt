package io.github.extracteddeath.pursuitiron

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.extracteddeath.pursuitiron.domain.NativeProgramMuscle

/** Shared native control, never computes training dosage or muscle credits. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NativeMusclePrioritySelector(
    muscles: List<NativeProgramMuscle>,
    focus: Map<String,Int>,
    reduced: List<String>,
    enabled: Boolean,
    onChange: (String,Int)->Unit
) {
    if(muscles.isEmpty()) {
        Text("Muscle priorities are unavailable until the training engine is ready.",
            color=MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    var selected by rememberSaveable { mutableStateOf(muscles.first().id) }
    val active=muscles.firstOrNull { it.id==selected } ?: muscles.first()
    val selectedMode=if(active.id in reduced) -1 else focus[active.id] ?: 0
    Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text("Choose a muscle, then set its emphasis. Other muscles remain balanced.",
            style=MaterialTheme.typography.bodyMedium)
        FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp),
            verticalArrangement=Arrangement.spacedBy(4.dp)) {
            muscles.forEach { option ->
                val mode=if(option.id in reduced) -1 else focus[option.id] ?: 0
                val marker=when(mode) {
                    -1 -> " · Less"
                    1 -> " · Focus"
                    2 -> " · Specialize"
                    else -> ""
                }
                PreviewChoice(option.label+marker,active.id==option.id,enabled) {
                    selected=option.id
                }
            }
        }
        Text("${active.label} emphasis",style=MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),
            verticalArrangement=Arrangement.spacedBy(6.dp)) {
            listOf(0 to "Balanced",1 to "Prioritize",2 to "Specialize",-1 to "Reduce").forEach { (mode,label) ->
                val withinLimit=when(mode) {
                    -1 -> active.id in reduced || reduced.size<3
                    1,2 -> active.id in focus || focus.size<3
                    else -> true
                }
                PreviewChoice(label,selectedMode==mode,enabled && withinLimit) {
                    onChange(active.id,mode)
                }
            }
        }
        Text("Priorities: ${focus.size}/3 · Reduced muscles: ${reduced.size}/3. " +
            "Reduced muscles can still receive compound work. The generated plan is reviewed before saving.",
            style=MaterialTheme.typography.bodySmall,
            color=MaterialTheme.colorScheme.onSurfaceVariant,
            modifier=Modifier.fillMaxWidth())
    }
}
