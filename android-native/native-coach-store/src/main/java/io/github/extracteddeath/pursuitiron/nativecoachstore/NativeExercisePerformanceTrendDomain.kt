package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.math.BigDecimal
import java.util.Collections

/**
 * Read-only display projection. Call ONLY with the complete, owner-verified,
 * correction-aware mixed timeline from NativeKotlinCandidateHost.
 *
 * A cable stack, a dumbbell per-hand load and a barbell total are NEVER one
 * comparable series. Rep windows are factual filters rather than 1RM
 * estimates or a claim that sets in the same window have equal difficulty.
 * No weights, set targets, training decisions, PRs, or history are written.
 */
enum class NativeExerciseRepWindow(val lower:Int,val upper:Int,val label:String) {
    ONE_TO_FIVE(1,5,"1–5 reps"),
    SIX_TO_TEN(6,10,"6–10 reps"),
    ELEVEN_TO_FIFTEEN(11,15,"11–15 reps"),
    SIXTEEN_TO_THIRTY(16,30,"16–30 reps"),
    THIRTY_ONE_PLUS(31,100,"31–100 reps");
    companion object {
        fun forReps(reps:Int):NativeExerciseRepWindow? =
            entries.firstOrNull { reps in it.lower..it.upper }
    }
}
data class NativeExerciseTrendKey(
    val basis:LoadBasis?,
    val reps:NativeExerciseRepWindow
) {
    val bodyweightOnly:Boolean get()=basis==null
}
data class NativeExerciseTrendPoint(
    val completionOrder:Long,
    val actualLoad:BigDecimal?,
    val performedReps:Int
)
data class NativeExerciseTrendSeries(
    val key:NativeExerciseTrendKey,
    val points:List<NativeExerciseTrendPoint>
)
data class NativeExercisePerformanceTrend(
    val athleteId:String,
    val manifestRevision:Long,
    val exerciseId:String,
    val displayUnit:LoadUnit,
    val series:List<NativeExerciseTrendSeries>
) {
    val canPublishPr:Boolean get()=false
    val canPrescribeWorkout:Boolean get()=false
    val canEditHistory:Boolean get()=false
}

object NativeExercisePerformanceTrendDomain {
    private val id=Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,79}$")
    private val zero=BigDecimal.ZERO
    private val max=BigDecimal("100000")

    private data class Visit(
        val issued:DraftSetLoad,
        val actual:KotlinPerformedSet?
    )
    /**
     * Only an actual COMPLETED, non-amplifier, WORKING visit may enter the
     * projection. Warmups, failed attempts, skipped sets and planned-only
     * prescriptions cannot make progress appear artificially strong.
     */
    fun project(
        timeline:NativeKotlinMixedHistoryTimeline,
        exerciseId:String,
        displayUnit:LoadUnit
    ):NativeExercisePerformanceTrend {
        require(id.matches(timeline.athleteId) && id.matches(exerciseId))
        val candidates=linkedMapOf<NativeExerciseTrendKey,
            MutableMap<Long,NativeExerciseTrendPoint>>()
        for(entry in timeline.completions) {
            val order=entry.manifest.completionOrder
            val sets: List<Visit> = when(entry) {
                is NativeKotlinMixedHistoryCompletion.Composer ->
                    entry.evidence.sets.filter {
                        it.exerciseId==exerciseId &&
                            it.type==DraftSetType.WORKING &&
                            it.outcome==KotlinJournalSetOutcome.COMPLETED
                    }.map { Visit(it.issuedLoad,it.actual) }
                is NativeKotlinMixedHistoryCompletion.Custom ->
                    entry.evidence.visits.filter {
                        it.exerciseId==exerciseId &&
                            it.part==CustomWorkoutVisitPart.MAIN &&
                            it.setType==DraftSetType.WORKING &&
                            it.outcome==KotlinJournalSetOutcome.COMPLETED
                    }.map { Visit(it.prescribedLoad,it.actual) }
            }
            for(visit in sets) {
                val actual=visit.actual ?:continue
                val window=NativeExerciseRepWindow.forReps(actual.reps) ?:continue
                val key:NativeExerciseTrendKey
                val weight:BigDecimal?
                when(val issued=visit.issued) {
                    is DraftSetLoad.Verified -> {
                        val performed=actual.load ?:continue
                        if(actual.unit!=issued.unit || performed<=zero ||
                            performed>max ||
                            performed.stripTrailingZeros().scale()>3)continue
                        key=NativeExerciseTrendKey(issued.basis,window)
                        weight=StrengthStandardsMeasurementDomain.convert(
                            performed,issued.unit,displayUnit)
                    }
                    DraftSetLoad.ExplicitBodyweight -> {
                        // A completed unweighted rep set is comparable only
                        // with other unweighted sets of this exact movement.
                        // Weighted/assisted bodyweight is not quietly treated
                        // as an external positive gym load.
                        if(actual.load!=null || actual.unit!=null)continue
                        key=NativeExerciseTrendKey(null,window)
                        weight=null
                    }
                }
                val sample=NativeExerciseTrendPoint(order,weight,actual.reps)
                val bySession=candidates.getOrPut(key){linkedMapOf()}
                val previous=bySession[order]
                val improves=if(key.bodyweightOnly)
                    previous==null || sample.performedReps>previous.performedReps
                else previous==null ||
                    checkNotNull(weight)>checkNotNull(previous.actualLoad) ||
                    (checkNotNull(weight).compareTo(checkNotNull(previous.actualLoad))==0 &&
                        sample.performedReps>previous.performedReps)
                if(improves)bySession[order]=sample
            }
        }
        val series=candidates.map { (key,values) ->
            NativeExerciseTrendSeries(key,Collections.unmodifiableList(
                values.values.sortedBy {it.completionOrder}))
        }.sortedWith(compareByDescending<NativeExerciseTrendSeries>{
            it.points.size
        }.thenBy {it.key.bodyweightOnly}.thenBy {
            it.key.basis?.name.orEmpty()
        }.thenBy {it.key.reps.lower})
        return NativeExercisePerformanceTrend(
            timeline.athleteId,timeline.manifestRevision,exerciseId,displayUnit,
            Collections.unmodifiableList(series))
    }
}
