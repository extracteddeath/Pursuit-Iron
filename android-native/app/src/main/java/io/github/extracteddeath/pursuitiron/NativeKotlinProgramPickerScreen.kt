package io.github.extracteddeath.pursuitiron

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import io.github.extracteddeath.pursuitiron.nativecoachstore.*

/** The same search, ordering and row semantics on cold entry and in the chooser. */
@Composable
internal fun NativeKotlinSavedProgramList(
    programs:List<NativeCustomDraftSummary>,owner:NativeKotlinOwnerIdentity?,
    currentCycleId:String?,busy:Boolean,loaded:Boolean,
    onOpen:(NativeKotlinProgramSelection)->Unit
) {
    var query by rememberSaveable(owner?.ownerKey,owner?.athleteId){mutableStateOf("")}
    var visibleCount by rememberSaveable(owner?.ownerKey,owner?.athleteId,query){mutableStateOf(20)}
    val matches=remember(programs,query,currentCycleId){matchingPrograms(programs,query,currentCycleId)}
    if(programs.isNotEmpty() || query.isNotEmpty()) {
        OutlinedTextField(value=query,onValueChange={query=it},singleLine=true,
            label={Text("Search saved programs")},enabled=!busy,modifier=Modifier.fillMaxWidth())
    }
    when {
        !loaded && busy -> Text("Loading saved programs…",style=MaterialTheme.typography.bodySmall)
        !loaded -> Text("Refresh saved programs to try again.",style=MaterialTheme.typography.bodySmall)
        programs.isEmpty() -> Text("No saved programs for this athlete yet.",
            style=MaterialTheme.typography.bodyMedium)
        matches.isEmpty() -> {
            Text("No programs match your search.",style=MaterialTheme.typography.bodyMedium)
            TextButton(onClick={query=""},enabled=!busy){Text("Clear search")}
        }
        else -> {
            Text("${minOf(visibleCount,matches.size)} of ${matches.size} ${if(matches.size==1)"program" else "programs"}",
                style=MaterialTheme.typography.labelMedium,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
            matches.take(visibleCount).forEach {saved->
                val selected=saved.cycleId==currentCycleId
                PursuitDestinationRow(title=saved.title,
                    description=(if(selected)"Current program · " else "")+saved.programDescription(),
                    selected=selected,enabled=!busy && owner!=null,
                    onClick={owner?.let{onOpen(NativeKotlinProgramSelection(it,saved.cycleId,saved.revision))}},
                    icon={Icon(if(selected)Icons.Filled.CheckCircle else Icons.Filled.FitnessCenter,
                        contentDescription=null)})
            }
            if(visibleCount<matches.size)OutlinedButton(onClick={visibleCount+=20},
                enabled=!busy,modifier=Modifier.fillMaxWidth()){Text("Show more programs")}
        }
    }
}

@Composable
internal fun NativeKotlinProgramPickerSheet(
    state:NativeKotlinProgramPickerState,currentCycleId:String?,
    onDismiss:()->Unit,onRefresh:()->Unit,onOpen:(NativeKotlinProgramSelection)->Unit
) {
    // These are cancellable reads. Dismissal invalidates their token, even while loading.
    NativeEditSheet(busy=false,onDismiss=onDismiss) {
        PursuitSectionHeading("Open saved program",
            "Review a saved program and its workouts.")
        PursuitResponsiveActionPair(
            primary={modifier->OutlinedButton(onClick=onDismiss,modifier=modifier){Text("Cancel")}},
            secondary={modifier->TextButton(onClick=onRefresh,enabled=!state.busy,
                modifier=modifier){Text("Refresh programs")}})
        if(state.busy) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(if(state.opening==null)"Reading your saved programs…" else "Opening the latest saved program…",
                style=MaterialTheme.typography.bodySmall)
        }
        state.failure?.let{Text(it.programPickerMessage(),style=MaterialTheme.typography.bodyMedium,
            color=MaterialTheme.colorScheme.error)}
        NativeKotlinSavedProgramList(state.programs,state.owner,currentCycleId,state.busy,
            loaded=state.owner!=null,onOpen=onOpen)
    }
}
