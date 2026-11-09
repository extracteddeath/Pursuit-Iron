package io.github.extracteddeath.pursuitiron

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.extracteddeath.pursuitiron.rebuild.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The current gym's CONFIRMED barbell stock only. Never guesses bar weight,
 * Smith mechanics, plate availability or an edited training target. */
@Composable
internal fun NativeVerifiedPlateLoadingControl(
    host:NativeKotlinCandidateHost,
    workoutId:String,
    exerciseId:String,
    custom:Boolean,
    load:DraftSetLoad,
    enabled:Boolean
) {
    val target=load as? DraftSetLoad.Verified ?:return
    if(target.basis!=LoadBasis.BARBELL_TOTAL)return
    var open by rememberSaveable(workoutId,exerciseId,
        target.value.toPlainString()){mutableStateOf(false)}
    var reviewed by remember(host,workoutId,exerciseId,custom,target) {
        mutableStateOf<VerifiedPlateLoadReview?>(null)
    }
    LaunchedEffect(host,workoutId,exerciseId,custom,target,open) {
        if(open) {
            reviewed=null
            reviewed=withContext(Dispatchers.IO) {
                runCatching {
                    host.reviewVerifiedBarbellPlates(
                        workoutId,exerciseId,custom,target)
                }.getOrElse {
                    VerifiedPlateLoadReview.Unavailable(
                        PlateLoadUnavailable.NO_BAR_STOCK)
                }
            }
        }
    }
    OutlinedButton(
        onClick={open=true},
        enabled=enabled,
        modifier=Modifier.heightIn(min=48.dp)
    ) {
        Text("Plates · ${target.value.stripTrailingZeros().toPlainString()} ${target.unit.name.lowercase()}")
    }
    if(open)AlertDialog(
        onDismissRequest={open=false},
        title={Text("Barbell plate loading")},
        text={
            Column(verticalArrangement=Arrangement.spacedBy(PursuitSpace.xs)) {
                when(val result=reviewed) {
                    null -> {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text("Checking verified equipment and plate stock…")
                    }
                    is VerifiedPlateLoadReview.Ready -> {
                        val p=result.breakdown
                        val unit=p.unit.name.lowercase()
                        Text("Confirmed empty bar: ${p.barWeight} $unit")
                        if(p.perSide.isEmpty())Text("No plates needed on either side.")
                        else {
                            Text("Put these plates on EACH side:",
                                style=MaterialTheme.typography.titleSmall)
                            p.perSide.forEach {pair->
                                Text("${pair.weightPerPlate} $unit × ${pair.pairs} per side")
                            }
                        }
                        Text("Assembled total: ${p.achievableTotal} $unit",
                            style=MaterialTheme.typography.titleMedium)
                        if(!p.exact) {
                            Text("Your target is ${p.targetTotal} $unit. This is the closest confirmed load at or below target—not an exact match.",
                                color=MaterialTheme.colorScheme.error,
                                style=MaterialTheme.typography.bodyMedium)
                        } else Text("Exact match to the issued target.",
                            color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    is VerifiedPlateLoadReview.Unavailable -> {
                        val reason=when(result.reason) {
                            PlateLoadUnavailable.NO_BAR_STOCK ->
                                "No currently verified gym bar weight and plate-pair inventory for this issued exercise."
                            PlateLoadUnavailable.INCOMPATIBLE_BASIS ->
                                "This set is not a verified plated barbell total. Smith bars and machine stacks need their own confirmed setup."
                            PlateLoadUnavailable.BELOW_EMPTY_BAR ->
                                "The target is below the confirmed empty bar weight."
                            PlateLoadUnavailable.INVALID_INVENTORY ->
                                "The confirmed inventory is inconsistent or too large to calculate safely."
                            PlateLoadUnavailable.UNIT_MISMATCH ->
                                "Target units do not match the currently confirmed bar inventory."
                            PlateLoadUnavailable.INVALID_TARGET ->
                                "The set target is not a valid load."
                            PlateLoadUnavailable.NOT_REPRESENTABLE ->
                                "No verified plate combination could be reconstructed."
                        }
                        Text(reason,style=MaterialTheme.typography.bodyMedium)
                    }
                }
                Text("Read-only preview. Does not change the target, actual load, recorded set or coaching.",
                    style=MaterialTheme.typography.bodySmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton={
            TextButton(onClick={open=false}) {Text("Close")}
        }
    )
}
