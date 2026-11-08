package io.github.extracteddeath.pursuitiron

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
    val estimatedOneRepMax:BigDecimal
)

/** Corrected, owner-verified working-set evidence. Never uses issued loads
 * or skipped/failed sets as achievements. Custom amplifier follow-ups and
 * warmups are excluded, and units are normalized BEFORE comparing points. */
internal fun verifiedStrengthTrend(
    timeline:NativeKotlinMixedHistoryTimeline,
    exerciseId:String,
    displayUnit:LoadUnit
):List<StrengthTrendSample> = timeline.completions.mapNotNull { entry ->
    val sets:List<Pair<DraftSetLoad,KotlinPerformedSet?>>=when(entry) {
        is NativeKotlinMixedHistoryCompletion.Composer ->
            entry.evidence.sets.filter{
                it.exerciseId==exerciseId &&
                    it.type==DraftSetType.WORKING &&
                    it.outcome==KotlinJournalSetOutcome.COMPLETED
            }.map{it.issuedLoad to it.actual}
        is NativeKotlinMixedHistoryCompletion.Custom ->
            entry.evidence.visits.filter{
                it.exerciseId==exerciseId &&
                    it.part==CustomWorkoutVisitPart.MAIN &&
                    it.setType==DraftSetType.WORKING &&
                    it.outcome==KotlinJournalSetOutcome.COMPLETED
            }.map{it.prescribedLoad to it.actual}
    }
    val best=sets.mapNotNull { (issued,actual) ->
        val loaded=issued as? DraftSetLoad.Verified
        if(loaded?.basis!=LoadBasis.BARBELL_TOTAL ||
            actual?.unit!=loaded.unit)return@mapNotNull null
        val actualLoad=actual.load ?:return@mapNotNull null
        val estimate=StrengthStandardsMeasurementDomain.epley(
            actualLoad,actual.reps)?:return@mapNotNull null
        StrengthStandardsMeasurementDomain.convert(
            estimate,loaded.unit,displayUnit)
    }.maxOrNull()
    best?.let{StrengthTrendSample(entry.manifest.completionOrder,it)}
}

@Composable
internal fun PursuitStrengthTrendChart(
    samples:List<StrengthTrendSample>,
    unit:LoadUnit
) {
    PursuitCard {
        PursuitSectionHeading("Estimated strength over time",
            "Best qualifying working set in each finished session, not a tested 1RM.")
        if(samples.size<2) {
            Text("Two completed appearances are needed to show a trend.",
                style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            val recent=samples.takeLast(20)
            val low=recent.minOf{it.estimatedOneRepMax}.toFloat()
            val high=recent.maxOf{it.estimatedOneRepMax}.toFloat()
            val span=(high-low).takeIf{it>0f} ?: 1f
            val lineColor=MaterialTheme.colorScheme.primary
            val description=recent.joinToString("; "){
                "Session ${it.completionOrder}: ${it.estimatedOneRepMax.setScale(1,RoundingMode.HALF_UP)} ${unit.name.lowercase()}"
            }
            Canvas(Modifier.fillMaxWidth().height(120.dp)
                .semantics {contentDescription=
                    "Estimated 1RM trend. $description"}) {
                val left=12.dp.toPx()
                val right=size.width-12.dp.toPx()
                val top=12.dp.toPx()
                val bottom=size.height-12.dp.toPx()
                fun point(index:Int):Offset {
                    val ratio=index.toFloat()/(recent.size-1).toFloat()
                    val vertical=(recent[index].estimatedOneRepMax.toFloat()-low)/span
                    return Offset(left+(right-left)*ratio,
                        bottom-(bottom-top)*vertical)
                }
                for(index in 0 until recent.lastIndex) {
                    drawLine(lineColor,start=point(index),end=point(index+1),
                        strokeWidth=3.dp.toPx(),cap=StrokeCap.Round)
                }
                for(index in recent.indices)drawCircle(lineColor,
                    radius=4.dp.toPx(),center=point(index))
            }
            Row(Modifier.fillMaxWidth(),
                horizontalArrangement=Arrangement.SpaceBetween) {
                Text("Session ${recent.first().completionOrder}: "+
                    "${recent.first().estimatedOneRepMax.setScale(1,RoundingMode.HALF_UP)} ${unit.name.lowercase()}",
                    style=MaterialTheme.typography.labelSmall)
                Text("Session ${recent.last().completionOrder}: "+
                    "${recent.last().estimatedOneRepMax.setScale(1,RoundingMode.HALF_UP)} ${unit.name.lowercase()}",
                    style=MaterialTheme.typography.labelSmall)
            }
            Text(if(samples.size>20)"Most recent 20 qualifying sessions shown."
                else "${samples.size} qualifying sessions shown.",
                style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
