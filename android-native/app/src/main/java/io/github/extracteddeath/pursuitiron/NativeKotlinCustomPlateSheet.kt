package io.github.extracteddeath.pursuitiron

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.extracteddeath.pursuitiron.nativecoachstore.*
import io.github.extracteddeath.pursuitiron.rebuild.*
import java.math.BigDecimal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Read-only request from an explicit athlete tap; no weight change or log. */
internal data class NativeCustomPlateRequest(
    val exerciseId:String,
    val stamp:CustomWorkoutSourceStamp,
    val enteredLoad:String,
    val unit:LoadUnit,
    val basis:LoadBasis
)

private sealed interface PlateScreenResult {
    data object Checking:PlateScreenResult
    data object Unavailable:PlateScreenResult
    data class Verified(val layout:KotlinVerifiedPlateLayout):PlateScreenResult
}

/**
 * Plate instructions require the SAME owner, gym, and catalog revision
 * that authorized the custom workout, plus this exercise's independently
 * reviewed stock. Do not reuse the legacy web plate calculator's default
 * bar weight, guess a Smith bar, or use a cached unverified plate list.
 */
@Composable
internal fun NativeKotlinCustomPlateSheet(
    host:NativeKotlinCandidateHost,
    request:NativeCustomPlateRequest,
    onDismiss:()->Unit
) {
    val result=remember(host,request){
        mutableStateOf<PlateScreenResult>(PlateScreenResult.Checking)
    }
    LaunchedEffect(host,request) {
        result.value=PlateScreenResult.Checking
        val layout=withContext(Dispatchers.IO) {
            runCatching {
                if(request.basis!=LoadBasis.BARBELL_TOTAL)
                    return@runCatching null
                val entered=request.enteredLoad.toBigDecimalOrNull()
                    ?:return@runCatching null
                val source=(host.readCustomAuthoringSource()
                    as? NativeAuthoringSourceRead.Found)?.source
                    ?:return@runCatching null
                val reviewed=source.setup.reviewed
                    ?:return@runCatching null
                val stamp=request.stamp
                if(source.athleteId!=stamp.athleteId ||
                    source.revision!=stamp.gymCatalogSourceRevision ||
                    reviewed.athleteProfileId!=stamp.athleteId ||
                    reviewed.profileRevision!=stamp.athleteProfileRevision ||
                    reviewed.gym.gymId!=stamp.gymId ||
                    reviewed.gym.unit!=request.unit)
                    return@runCatching null
                val option=CustomExerciseAuthoringDomain.picker(
                    source.setup,source.catalog).firstOrNull {
                    it.exerciseId==request.exerciseId
                } ?:return@runCatching null
                val stock=option.availability.stock
                    as? VerifiedLoadStock.Barbell
                    ?:return@runCatching null
                if(option.loadBasis!=LoadBasis.BARBELL_TOTAL ||
                    option.loadUnit!=request.unit)
                    return@runCatching null
                KotlinVerifiedPlateLayoutDomain.inspect(
                    stock,entered,request.unit)
            }.getOrNull()
        }
        result.value=if(layout==null)PlateScreenResult.Unavailable
            else PlateScreenResult.Verified(layout)
    }

    NativeKotlinPlateResultSheet(request.exerciseId,result.value,onDismiss)
}

internal data class NativeComposerPlateRequest(
    val exerciseId:String,
    val enteredLoad:String,
    val unit:LoadUnit
)

/**
 * The generated-workout reader reopens its exact owner and source-approved
 * journal in the native host. It never receives raw caller-supplied inventory.
 */
@Composable
internal fun NativeKotlinComposerPlateSheet(
    host:NativeKotlinCandidateHost,
    workoutId:String,
    request:NativeComposerPlateRequest,
    onDismiss:()->Unit
) {
    val result=remember(host,workoutId,request) {
        mutableStateOf<PlateScreenResult>(PlateScreenResult.Checking)
    }
    LaunchedEffect(host,workoutId,request) {
        result.value=PlateScreenResult.Checking
        val layout=withContext(Dispatchers.IO) {
            runCatching {
                val entered=request.enteredLoad.toBigDecimalOrNull()
                    ?:return@runCatching null
                host.reviewComposerPlateLayout(
                    workoutId,request.exerciseId,entered,request.unit)
            }.getOrNull()
        }
        result.value=if(layout==null)PlateScreenResult.Unavailable
            else PlateScreenResult.Verified(layout)
    }
    NativeKotlinPlateResultSheet(request.exerciseId,result.value,onDismiss)
}

/** One Material 3 presentation for both verified issued-workout sources. */
@Composable
private fun NativeKotlinPlateResultSheet(
    exerciseId:String,
    current:PlateScreenResult,
    onDismiss:()->Unit
) {
    NativeEditSheet(busy=false,onDismiss=onDismiss) {
        Text("Verified barbell plates",
            style=MaterialTheme.typography.headlineSmall)
        Text(kotlinExerciseLabel(exerciseId),
            style=MaterialTheme.typography.bodyMedium)
        Text("Read-only physical loading guide. Your entered weight, "+
            "recorded sets and approved program remain unchanged.",
            style=MaterialTheme.typography.bodySmall,
            color=MaterialTheme.colorScheme.onSurfaceVariant)
        when(current) {
            PlateScreenResult.Checking ->
                LinearProgressIndicator(Modifier.fillMaxWidth())
            PlateScreenResult.Unavailable -> Text(
                "An exact plate setup could not be verified from the "+
                "current owner-matched gym and issued workout. No plates "+
                "or bar weight are assumed.",
                style=MaterialTheme.typography.bodyMedium,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
            is PlateScreenResult.Verified -> {
                val layout=current.layout
                fun amount(v:BigDecimal)=v.stripTrailingZeros().toPlainString()
                val unit=layout.unit.name.lowercase()
                Text("${amount(layout.total)} $unit total · "+
                    "${amount(layout.barWeight)} $unit confirmed bar",
                    style=MaterialTheme.typography.titleMedium)
                Text("Load the same plates on each side:",
                    style=MaterialTheme.typography.bodySmall)
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement=Arrangement.spacedBy(8.dp),
                    verticalAlignment=Alignment.CenterVertically
                ) {
                    listOf("LEFT","RIGHT").forEach { side ->
                        OutlinedCard(Modifier.weight(1f)) {
                            Column(Modifier.padding(12.dp),
                                verticalArrangement=Arrangement.spacedBy(6.dp)) {
                                Text(side,style=MaterialTheme.typography.labelLarge,
                                    color=MaterialTheme.colorScheme.primary)
                                if(layout.perSide.isEmpty()) Text("Bar only")
                                layout.perSide.forEach {plate->
                                    Text("${amount(plate.weight)} $unit × "+
                                        "${plate.countPerSide}",
                                        style=MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
                Text("Pair counts are per side, not total individual plates. "+
                    "Confirm collars, balance, and safe loading yourself. "+
                    "Smith machines are not treated as barbells.",
                    style=MaterialTheme.typography.bodySmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        TextButton(
            onClick=onDismiss,
            modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)
        ) {Text("Back to workout")}
    }
}
