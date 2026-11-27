package io.github.extracteddeath.pursuitiron

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.extracteddeath.pursuitiron.nativecoachstore.*
import io.github.extracteddeath.pursuitiron.rebuild.*
import java.math.BigDecimal
import java.math.RoundingMode

internal data class StrengthTrendSample(
    val completionOrder:Long,
    val estimatedOneRepMax:BigDecimal,
    // The recorded Finish clock, never an inferred calendar day.
    val completedAtEpochMillis:Long?=null
)

/** Corrected, owner-verified working-set evidence. Never uses issued loads
 * or skipped/failed sets as achievements. Custom amplifier follow-ups and
 * warmups are excluded, and units are normalized BEFORE comparing points. */
internal fun verifiedStrengthTrend(
    timeline:NativeKotlinMixedHistoryTimeline,
    exerciseId:String,
    displayUnit:LoadUnit
):List<StrengthTrendSample> = timeline.completions.mapNotNull { entry ->
    val sets: List<Pair<LoadUnit?, KotlinPerformedSet?>> = when(entry) {
        is NativeKotlinMixedHistoryCompletion.Composer ->
            entry.evidence.sets.filter{
                it.exerciseId==exerciseId &&
                    it.type==DraftSetType.WORKING &&
                    it.outcome==KotlinJournalSetOutcome.COMPLETED
            }.mapNotNull{set->
                (set.issuedLoad as? DraftSetLoad.Verified)
                    ?.takeIf{it.basis==LoadBasis.BARBELL_TOTAL}?.let{it.unit to set.actual}
            }
        // No typed measured outcome is a verified rep-based 1RM sample.
        is NativeKotlinMixedHistoryCompletion.Measured -> emptyList()
        is NativeKotlinMixedHistoryCompletion.Freestyle ->
            entry.workout.observations().filter{(exercise,record)->
                exercise.exerciseId==exerciseId && exercise.basis==LoadBasis.BARBELL_TOTAL &&
                    record.type==DraftSetType.WORKING && record.outcome==KotlinJournalSetOutcome.COMPLETED
            }.map{(exercise,record)->exercise.unit to record.actual}
        is NativeKotlinMixedHistoryCompletion.Custom ->
            entry.evidence.visits.filter{
                it.exerciseId==exerciseId &&
                    it.part==CustomWorkoutVisitPart.MAIN &&
                    it.setType==DraftSetType.WORKING &&
                    it.outcome==KotlinJournalSetOutcome.COMPLETED
            }.mapNotNull{set->
                (set.prescribedLoad as? DraftSetLoad.Verified)
                    ?.takeIf{it.basis==LoadBasis.BARBELL_TOTAL}?.let{it.unit to set.actual}
            }
    }
    val best=sets.mapNotNull { (unit,actual) ->
        if(unit==null || actual==null || actual.unit!=unit ||
            KotlinSetNumberAuthorityDomain.machineTagContradictsFreeBarbell(
                LoadBasis.BARBELL_TOTAL,actual.machineInstanceId))return@mapNotNull null
        val actualLoad=actual.load ?:return@mapNotNull null
        val estimate=StrengthStandardsMeasurementDomain.epley(
            actualLoad,actual.reps)?:return@mapNotNull null
        StrengthStandardsMeasurementDomain.convert(
            estimate,unit,displayUnit)
    }.maxOrNull()
    best?.let{StrengthTrendSample(
        entry.manifest.completionOrder,it,entry.manifest.completedAtEpochMillis)}
}.sortedBy {it.completionOrder}

private fun estimatedStrengthLabel(value:BigDecimal,unit:LoadUnit):String =
    "${value.setScale(1,RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()} ${unit.name.lowercase()}"

/** Display-only chart. All points come from corrected native working sets;
 * there are no synthetic milestones, predictions, fake PRs or target weights.
 */
@Composable
internal fun PursuitStrengthTrendChart(
    samples:List<StrengthTrendSample>,
    unit:LoadUnit,
    onOpenHistory:(()->Unit)?=null
) {
    var showValues by rememberSaveable {mutableStateOf(false)}
    PursuitCard {
        PursuitSectionHeading("Estimated strength over time",
            "Your best qualifying working set per finished session; not a tested one-rep maximum.")
        val recent=samples.sortedBy{it.completionOrder}.takeLast(20)
        val axis=NativeKotlinTrendAxisPolicy.project(
            recent.map{it.completedAtEpochMillis})
        val recordedZone=remember { java.time.ZoneId.systemDefault() }
        val recordedLocale=remember { java.util.Locale.getDefault() }
        fun sessionText(sample:StrengthTrendSample)=
            NativeKotlinTrendAxisPolicy.sessionLabel(
                sample.completionOrder,sample.completedAtEpochMillis,
                recordedZone,recordedLocale)
        if(recent.isEmpty()) {
            PursuitHistoryStatus("No qualified sessions yet",
                "Finish a 1–10-rep working set on this exact barbell lift to start a trend.")
        } else {
            val first=recent.first()
            val last=recent.last()
            if(recent.size==1) {
                PursuitHistoryStatus("One qualifying session",
                    sessionText(last) + " · estimated 1RM " +
                    estimatedStrengthLabel(last.estimatedOneRepMax,unit) +
                    ". A second appearance is needed to show change.")
            } else {
                val difference=last.estimatedOneRepMax-first.estimatedOneRepMax
                val signedDifference=(if(difference.signum()>0)"+" else "")+
                    estimatedStrengthLabel(difference,unit)
                PursuitMetricStrip(listOf(
                    "First shown" to estimatedStrengthLabel(first.estimatedOneRepMax,unit),
                    "Latest" to estimatedStrengthLabel(last.estimatedOneRepMax,unit),
                    "Change" to signedDifference
                ))
                val low=recent.minOf{it.estimatedOneRepMax}.toFloat()
                val high=recent.maxOf{it.estimatedOneRepMax}.toFloat()
                val span=(high-low).takeIf{it>0f} ?: 1f
                val lineColor=MaterialTheme.colorScheme.primary
                val guideColor=MaterialTheme.colorScheme.outlineVariant
                val description=recent.joinToString("; ") {
                    sessionText(it) + ", estimated 1RM " +
                        estimatedStrengthLabel(it.estimatedOneRepMax,unit)
                }
                Text("Highest shown: "+
                    estimatedStrengthLabel(recent.maxOf{it.estimatedOneRepMax},unit),
                    style=MaterialTheme.typography.labelMedium,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
                Canvas(Modifier.fillMaxWidth().height(164.dp)
                    .semantics {contentDescription="Estimated 1RM trend. $description"}) {
                    val left=14.dp.toPx()
                    val right=size.width-14.dp.toPx()
                    val top=10.dp.toPx()
                    val bottom=size.height-10.dp.toPx()
                    for(fraction in listOf(0f,0.5f,1f)) {
                        val y=top+(bottom-top)*fraction
                        drawLine(guideColor,Offset(left,y),Offset(right,y),
                            strokeWidth=1.dp.toPx())
                    }
                    fun point(index:Int):Offset {
                        val ratio=axis.fractions[index]
                        val vertical=(recent[index].estimatedOneRepMax.toFloat()-low)/span
                        return Offset(left+(right-left)*ratio,
                            bottom-(bottom-top)*vertical)
                    }
                    for(index in 0 until recent.lastIndex)
                        drawLine(lineColor,point(index),point(index+1),
                            strokeWidth=3.dp.toPx(),cap=StrokeCap.Round)
                    for(index in recent.indices)
                        drawCircle(lineColor,radius=4.dp.toPx(),center=point(index))
                }
                // Separate lines avoid truncating session identifiers at 200% font.
                Text("First shown · " + sessionText(first) + " · " +
                    estimatedStrengthLabel(first.estimatedOneRepMax,unit),
                    style=MaterialTheme.typography.labelSmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Latest · " + sessionText(last) + " · " +
                    estimatedStrengthLabel(last.estimatedOneRepMax,unit),
                    style=MaterialTheme.typography.labelSmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
                Text(if(axis.calendarScaled)
                    "Horizontal spacing follows recorded Finish times."
                    else "Horizontal spacing follows session sequence because dates are missing, equal or out of order.",
                    style=MaterialTheme.typography.bodySmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Difference is between displayed sessions, not a strength prediction or a certified PR.",
                    style=MaterialTheme.typography.bodySmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(if(samples.size>20) "Most recent 20 of ${samples.size} qualifying sessions shown."
                else "${samples.size} qualifying ${if(samples.size==1)"session" else "sessions"} shown.",
                style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick={showValues=!showValues},
                modifier=Modifier.heightIn(min=48.dp)) {
                Text(if(showValues)"Hide chart values" else "Show chart values")
            }
            if(showValues) {
                recent.asReversed().forEach {sample ->
                    Text(sessionText(sample) + " · estimated 1RM " +
                        estimatedStrengthLabel(sample.estimatedOneRepMax,unit),
                        style=MaterialTheme.typography.bodyMedium)
                }
            }
        }
        if(onOpenHistory!=null)OutlinedButton(onClick=onOpenHistory,
            modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {
            Text("Open complete exercise history")
        }
    }
}


/**
 * Shared, read-only line plot for factual exercise measurements. Callers
 * decide whether these are working loads or bodyweight repetitions. The plot
 * is purely visual and never infers improvement, PRs or training decisions.
 */
internal data class PursuitFactualTrendPoint(
    val sessionOrder:Long,
    val value:BigDecimal,
    val completedAtEpochMillis:Long?=null,
    /** Only a specific recorded working set may attach reported effort. */
    val actualRir:Int?=null,
    val showActualEffort:Boolean=false
)

@Composable
internal fun PursuitFactualLinePlot(
    points:List<PursuitFactualTrendPoint>,
    measureLabel:String,
    formatValue:(BigDecimal)->String
) {
    if(points.size<2)return
    val ordered=points.sortedBy {it.sessionOrder}
    val axis=NativeKotlinTrendAxisPolicy.project(
        ordered.map{it.completedAtEpochMillis})
    val recordedZone=remember { java.time.ZoneId.systemDefault() }
    val recordedLocale=remember { java.util.Locale.getDefault() }
    val low=ordered.minOf {it.value}.toFloat()
    val high=ordered.maxOf {it.value}.toFloat()
    val span=(high-low).takeIf {it>0f} ?:1f
    val lineColor=MaterialTheme.colorScheme.primary
    val guideColor=MaterialTheme.colorScheme.outlineVariant
    val spoken=ordered.joinToString("; ") {
        NativeKotlinTrendAxisPolicy.sessionLabel(
            it.sessionOrder,it.completedAtEpochMillis,recordedZone,
            recordedLocale) + ", ${formatValue(it.value)}" +
            (if(it.showActualEffort)
                ", "+(it.actualRir?.let{rir->"$rir actual RIR"}
                    ?: "actual RIR not reported") else "")
    }
    Canvas(Modifier.fillMaxWidth().height(164.dp)
        .semantics {contentDescription="$measureLabel trend. $spoken"}) {
        val left=14.dp.toPx()
        val right=size.width-14.dp.toPx()
        val top=10.dp.toPx()
        val bottom=size.height-10.dp.toPx()
        for(fraction in listOf(0f,0.5f,1f)) {
            val y=top+(bottom-top)*fraction
            drawLine(guideColor,Offset(left,y),Offset(right,y),
                strokeWidth=1.dp.toPx())
        }
        fun position(index:Int):Offset {
            val x=axis.fractions[index]
            val y=(ordered[index].value.toFloat()-low)/span
            return Offset(left+(right-left)*x,
                bottom-(bottom-top)*y)
        }
        for(index in 0 until ordered.lastIndex)
            drawLine(lineColor,position(index),position(index+1),
                strokeWidth=3.dp.toPx(),cap=StrokeCap.Round)
        for(index in ordered.indices)
            drawCircle(lineColor,radius=4.dp.toPx(),center=position(index))
    }
}
