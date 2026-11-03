package io.github.extracteddeath.pursuitiron

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import io.github.extracteddeath.pursuitiron.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Read-only Material 3 physical plate viewer. The caller has confirmed
 * canonical barbell_plates loading for THIS issued session, set and load.
 * Does not change actual set entries, issued targets or future progression.
 */
/** Matching plates on each side; never a mechanical resistance estimate. */
@Composable
private fun RowScope.VerifiedPlateSide(label: String,layout: NativeBarbellPlateBreakdown) {
    OutlinedCard(modifier=Modifier.weight(1f)) {
        Column(Modifier.padding(8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Text(label,style=MaterialTheme.typography.labelLarge,
                color=MaterialTheme.colorScheme.primary)
            if(layout.perSide.isEmpty())Text("Empty",style=MaterialTheme.typography.bodySmall)
            layout.perSide.forEach { pair ->
                Text("${pair.weight} ${layout.unit} × ${pair.pairs}",
                    style=MaterialTheme.typography.labelMedium)
            }
        }
    }
}

/** A result belongs to the exact saved inventory and issued load lookup. */
private sealed interface PlateLookup {
    data object Loading : PlateLookup
    data object Unavailable : PlateLookup
    data class Verified(val layout: NativeBarbellPlateBreakdown) : PlateLookup
}

@Composable
internal fun NativeVerifiedPlateBreakdown(
    state: NativeRuntimeState,session: JournalSessionKey,
    selectedLoad: String,exact: Boolean
) {
    val saved=state.saved
    val inventory=saved?.loadingInventory
    val allowed=saved?.loadingCandidates?.map { it.id }?.toSet().orEmpty()
    val exactOnly=saved?.loadingCandidates?.filter { it.mode=="external_load" }
        ?.map { it.id }?.toSet().orEmpty()
    // Even a bounded physical-stock search can be expensive for an unusual
    // inventory. Keep it off Compose's main thread; key both the job and its
    // result to the exact persisted stock, session, unit and entered load.
    // A new key gets Loading rather than briefly showing an old plate setup.
    val lookup=remember(inventory,session.workoutId,session.unit,selectedLoad,allowed,exactOnly,exact) {
        mutableStateOf<PlateLookup>(PlateLookup.Loading)
    }
    LaunchedEffect(lookup) {
        val verified=if(!exact)null else withContext(Dispatchers.Default) {
            NativeBarbellPlateBreakdownPolicy.inspect(
                inventory,session.unit,selectedLoad,allowed,exactOnly)
        }
        lookup.value=when {
            !exact || verified==null -> PlateLookup.Unavailable
            else -> PlateLookup.Verified(verified)
        }
    }
    val breakdown=(lookup.value as? PlateLookup.Verified)?.layout
    var show by remember(session.workoutId,selectedLoad,inventory,allowed,exactOnly,exact) {
        mutableStateOf(false)
    }
    if(!exact) {
        Text("That load is not an exact selectable barbell total. No plate layout is assumed.",
            style=MaterialTheme.typography.bodySmall,
            color=MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    if(breakdown==null) {
        Text(if(lookup.value==PlateLookup.Loading) "Checking saved physical plates…"
            else "An exact plate layout could not be verified for this load.",
            style=MaterialTheme.typography.bodySmall,
            color=MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    OutlinedButton(onClick={show=true},modifier=Modifier.fillMaxWidth()) {
        Text("Show plates for ${breakdown.totalLoad} ${breakdown.unit}")
    }
    if(show) AlertDialog(
        onDismissRequest={show=false},
        title={Text("Verified plate setup")},
        text={
            Column(Modifier.heightIn(max=360.dp).verticalScroll(rememberScrollState()),
                verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("Total ${breakdown.totalLoad} ${breakdown.unit} · Bar ${breakdown.barWeight} ${breakdown.unit}")
                Text("Load the SAME plates on each side:",style=MaterialTheme.typography.titleSmall)
                Row(Modifier.fillMaxWidth(),
                    horizontalArrangement=Arrangement.spacedBy(6.dp),
                    verticalAlignment=Alignment.CenterVertically) {
                    VerifiedPlateSide("LEFT",breakdown)
                    Text("BAR",style=MaterialTheme.typography.labelSmall,
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                    VerifiedPlateSide("RIGHT",breakdown)
                }
                Text("Uses only saved physical plate pairs. Confirm collars, balance and safe loading yourself. This does not log or prescribe a weight.",
                    style=MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton={TextButton(onClick={show=false}) { Text("Done") }}
    )
}
