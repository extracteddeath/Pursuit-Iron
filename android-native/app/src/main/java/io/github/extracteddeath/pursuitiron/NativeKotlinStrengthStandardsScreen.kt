package io.github.extracteddeath.pursuitiron

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.extracteddeath.pursuitiron.nativecoachstore.*
import io.github.extracteddeath.pursuitiron.rebuild.*
import java.math.BigDecimal
import java.math.RoundingMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Informational, source-attributed broad BODYWEIGHT-RATIO reference bands.
 * Strength Level 2026 entire-community 1RM reference tables. These are not
 * individual age/bodyweight-adjusted percentiles, performance predictions or
 * approved training targets. Do not infer sex or bodyweight from training data.
 *
 * https://strengthlevel.com/strength-standards/bench-press
 * https://strengthlevel.com/strength-standards/squat
 * https://strengthlevel.com/strength-standards/deadlift
 * https://strengthlevel.com/strength-standards/shoulder-press
 */
private data class PublishedStrengthReference(
    val exerciseId:String,
    val title:String,
    val url:String,
    val male:List<Double>,
    val female:List<Double>
)
private val publishedReferences=listOf(
    PublishedStrengthReference("barbell_bench","Bench press",
        "https://strengthlevel.com/strength-standards/bench-press",
        listOf(0.50,1.00,1.25,1.50,2.00),
        listOf(0.30,0.50,0.75,1.10,1.45)),
    PublishedStrengthReference("back_squat","Back squat",
        "https://strengthlevel.com/strength-standards/squat",
        listOf(0.75,1.25,1.75,2.25,2.75),
        listOf(0.50,0.75,1.25,1.75,2.25)),
    PublishedStrengthReference("conventional_deadlift","Conventional deadlift",
        "https://strengthlevel.com/strength-standards/deadlift",
        listOf(1.00,1.50,2.00,2.50,3.25),
        listOf(0.75,1.00,1.50,2.00,2.50)),
    PublishedStrengthReference("barbell_ohp","Standing barbell press",
        "https://strengthlevel.com/strength-standards/shoulder-press",
        listOf(0.35,0.55,0.80,1.05,1.35),
        listOf(0.20,0.35,0.50,0.70,0.95))
)
private val publishedTierNames=listOf(
    "Beginner","Novice","Intermediate","Advanced","Elite"
)

private fun epleyEstimate(load:BigDecimal,reps:Int):BigDecimal? =
    StrengthStandardsMeasurementDomain.epley(load,reps)
private fun scalarLabel(value:BigDecimal):String=
    value.stripTrailingZeros().toPlainString()
private fun displayedTier(ratio:Double,thresholds:List<Double>):String {
    val level=thresholds.indexOfLast{ratio>=it}
    return if(level<0)"Below beginner reference" else publishedTierNames[level]
}

/**
 * Dedicated Progress / Strength Standards screen. Actual performance comes
 * from the FULL owner-verified native mixed-history facts, never from entered
 * targets, unverified backups, manual PR badges or older substituted sessions.
 * Bodyweight and comparison group are display-only ephemeral choices.
 */
@Composable
internal fun NativeKotlinStrengthStandardsScreen(
    host:NativeKotlinCandidateHost
) {
    var result by remember(host) {
        mutableStateOf<NativeKotlinMixedFactsReadResult?>(null)
    }
    var revision by remember{mutableIntStateOf(0)}
    var bodyweightText by rememberSaveable{mutableStateOf("")}
    var bodyweightUnit by rememberSaveable{mutableStateOf(LoadUnit.LB.name)}
    var comparisonGroup by rememberSaveable{mutableStateOf<String?>(null)}
    var historyExerciseId by rememberSaveable{mutableStateOf<String?>(null)}
    val uri=LocalUriHandler.current
    LaunchedEffect(host,revision) {
        result=null
        result=withContext(Dispatchers.IO) {
            runCatching{host.reviewVerifiedKotlinWorkingSetFacts()}.getOrElse{
                NativeKotlinMixedFactsReadResult.HistoryRejected(
                    NativeKotlinMixedHistoryRefusal.STORAGE_UNAVAILABLE)
            }
        }
    }
    val facts=(result as? NativeKotlinMixedFactsReadResult.Review)?.facts
    val completed=facts?.mostRepsAtLoad.orEmpty()
        .filter{it.key.basis==LoadBasis.BARBELL_TOTAL}
    val displayUnit=LoadUnit.valueOf(bodyweightUnit)
    val bodyweight=runCatching{BigDecimal(bodyweightText.trim())}.getOrNull()
        ?.takeIf{it>BigDecimal.ZERO && it<=BigDecimal("1000")}
    PursuitScreenContent {
        PageHeading("PROGRESS","Strength standards",
            "Compare your actual logged barbell performance with published community reference bands.")
        PursuitCard(highlighted=true) {
            PursuitSectionHeading("Your comparison",
                "Optional; these settings never change your workouts or training recommendations.")
            Text("Community comparison group",style=MaterialTheme.typography.labelLarge)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement=Arrangement.spacedBy(PursuitSpace.xs)) {
                FilterChip(selected=comparisonGroup=="male",
                    onClick={comparisonGroup=if(comparisonGroup=="male")null else "male"},
                    label={Text("Male reference")})
                FilterChip(selected=comparisonGroup=="female",
                    onClick={comparisonGroup=if(comparisonGroup=="female")null else "female"},
                    label={Text("Female reference")})
            }
            Text("Bodyweight units",style=MaterialTheme.typography.labelLarge)
            SingleChoiceSegmentedButtonRow {
                LoadUnit.entries.forEachIndexed { index, unit ->
                    SegmentedButton(
                        selected=displayUnit==unit,
                        shape=SegmentedButtonDefaults.itemShape(
                            index=index,count=LoadUnit.entries.size),
                        onClick={
                            if(displayUnit!=unit) {
                                val entered=runCatching {
                                    BigDecimal(bodyweightText.trim())
                                }.getOrNull()
                                if(entered!=null && entered>BigDecimal.ZERO) {
                                    bodyweightText=StrengthStandardsMeasurementDomain
                                        .convert(entered,displayUnit,unit)
                                        .setScale(2,RoundingMode.HALF_UP)
                                        .stripTrailingZeros().toPlainString()
                                }
                                bodyweightUnit=unit.name
                            }
                        }
                    ) { Text(unit.name) }
                }
            }
            OutlinedTextField(value=bodyweightText,
                onValueChange={input->
                    if(input.length<=9 && input.all{it.isDigit()||it=='.'})
                        bodyweightText=input
                },
                singleLine=true,
                label={Text("Bodyweight (${displayUnit.name.lowercase()})")},
                isError=bodyweightText.isNotEmpty()&&bodyweight==null,
                keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),
                modifier=Modifier.fillMaxWidth())
            Text("Changing units converts your entered bodyweight. Saved sets retain their original weight units.",
                style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Broad entire-community reference only, not an age- or bodyweight-specific ranking. The estimated 1RM is not a tested maximum.",
                style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        when(result) {
            null -> LinearProgressIndicator(Modifier.fillMaxWidth())
            is NativeKotlinMixedFactsReadResult.HistoryRejected,
            is NativeKotlinMixedFactsReadResult.FactsRejected ->
                PursuitEmptyState("Verified history is unavailable",
                    "Strength scores are withheld when complete corrected training history cannot be verified.")
            is NativeKotlinMixedFactsReadResult.Review -> {
                if(completed.isEmpty())
                    PursuitEmptyState("Start building your strength record",
                        "Complete and save a native barbell workout to see its verified performance here.")
                publishedReferences.forEach { standard ->
                    val best=completed.filter{it.key.exerciseId==standard.exerciseId}
                        .mapNotNull {lift->
                            epleyEstimate(lift.actualLoad,lift.reps)?.let{estimate->
                                lift to StrengthStandardsMeasurementDomain.convert(
                                    estimate,lift.key.unit,displayUnit)
                            }
                        }.maxByOrNull{it.second}
                    PursuitCard {
                        PursuitExerciseHeading(standard.exerciseId,
                            onHistory={historyExerciseId=standard.exerciseId},
                            subtitle="Source-matched barbell movement")
                        if(best==null)PursuitHistoryStatus("No qualifying working set",
                            "Log a completed set of 1–10 reps to calculate an estimated maximum.")
                        else {
                            val (lift,estimate)=best
                            PursuitMetricStrip(listOf(
                                "Estimated 1RM" to "${scalarLabel(estimate.setScale(1,RoundingMode.HALF_UP))} ${displayUnit.name.lowercase()}",
                                "From logged" to "${scalarLabel(lift.actualLoad)} ${lift.key.unit.name.lowercase()} × ${lift.reps}",
                                "Session" to lift.completionOrder.toString()
                            ))
                            val eligible=bodyweight!=null && comparisonGroup!=null
                            if(eligible) {
                                val ratio=checkNotNull(
                                    StrengthStandardsMeasurementDomain.relativeStrength(
                                        estimate,checkNotNull(bodyweight))
                                ).toDouble()
                                val bands=if(comparisonGroup=="male")standard.male
                                    else standard.female
                                val tier=displayedTier(ratio,bands)
                                Text("$tier reference · ${"%.2f".format(java.util.Locale.US,ratio)}× bodyweight",
                                    style=MaterialTheme.typography.titleMedium)
                                LinearProgressIndicator(
                                    progress={(ratio / bands.last()).coerceIn(0.0,1.0).toFloat()},
                                    modifier=Modifier.fillMaxWidth())
                                Text("Reference thresholds: ${bands.map{"%.2f×".format(java.util.Locale.US,it)}.joinToString(" · ")}",
                                    style=MaterialTheme.typography.bodySmall,
                                    color=MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                Text("Choose a reference group and enter bodyweight to view the broad, unit-corrected comparison.",
                                    style=MaterialTheme.typography.bodySmall,
                                    color=MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        TextButton(onClick={uri.openUri(standard.url)}) {
                            Text("Reference methodology and full age/weight tables")
                        }
                    }
                }
            }
        }
        Text("Strength Level community bodyweight-ratio reference tables, accessed November 2026. Ratios are approximate broad bands and are not a training dose prescription, personal-record certification, or exact percentile. Other exercises and equipment variants remain in your full History.",
            style=MaterialTheme.typography.bodySmall,
            color=MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick={revision++},modifier=Modifier.fillMaxWidth()) {
            Text("Refresh verified strength history")
        }
    }
    historyExerciseId?.let { exerciseId ->
        NativeKotlinExerciseHistorySheet(host,exerciseId,
            onDismiss={historyExerciseId=null})
    }
}
