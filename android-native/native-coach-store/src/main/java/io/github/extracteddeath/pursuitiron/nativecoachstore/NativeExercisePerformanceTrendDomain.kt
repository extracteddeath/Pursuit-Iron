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
    val reps:NativeExerciseRepWindow,
    /** Null for portable free weights and unassisted bodyweight history. */
    val gymId:String?=null,
    val machineInstanceId:String?=null
) {
    val bodyweightOnly:Boolean get()=basis==null
    /** The factual value is the counterweight that helps the athlete.
     * Lower is harder; this is NOT lifted mass or a strength-standard 1RM. */
    val assistedCounterweight:Boolean get()=basis==LoadBasis.ASSISTANCE_COUNTERWEIGHT
}
data class NativeExerciseTrendPoint(
    val completionOrder:Long,
    val actualLoad:BigDecimal?,
    val performedReps:Int,
    /** RIR actually reported for this same selected completed working set.
     * Null means unknown, never target or assumed effort. */
    val actualRir:Int?=null
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
        val unit:LoadUnit?,
        val basis:LoadBasis?,
        val actual:KotlinPerformedSet?,
        val gymId:String
    ) {
        constructor(issued:DraftSetLoad,actual:KotlinPerformedSet?,gymId:String):
            this((issued as? DraftSetLoad.Verified)?.unit,
                (issued as? DraftSetLoad.Verified)?.basis,actual,gymId)
    }
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
                    }.map { Visit(it.issuedLoad,it.actual,
                        entry.evidence.source.gymId) }
                // Typed holds, distance, carry time and band outcomes are
                // never repetition observations or candidates for a load PR.
                is NativeKotlinMixedHistoryCompletion.Measured -> emptyList()
                is NativeKotlinMixedHistoryCompletion.Freestyle ->
                    entry.workout.observations().filter{(exercise,record)->
                        exercise.exerciseId==exerciseId && record.type==DraftSetType.WORKING &&
                            record.outcome==KotlinJournalSetOutcome.COMPLETED
                    }.map{(exercise,record)->
                        Visit(exercise.unit,exercise.basis,record.actual,entry.workout.source.gymId)
                    }
                is NativeKotlinMixedHistoryCompletion.Custom ->
                    entry.evidence.visits.filter {
                        it.exerciseId==exerciseId &&
                            it.part==CustomWorkoutVisitPart.MAIN &&
                            it.setType==DraftSetType.WORKING &&
                            it.outcome==KotlinJournalSetOutcome.COMPLETED
                    }.map { Visit(it.prescribedLoad,it.actual,entry.gymId) }
            }
            for(visit in sets) {
                val actual=visit.actual ?:continue
                val window=NativeExerciseRepWindow.forReps(actual.reps) ?:continue
                val key:NativeExerciseTrendKey
                val weight:BigDecimal?
                when(val basis=visit.basis) {
                    null -> {
                        if(actual.load!=null || actual.unit!=null)continue
                        key=NativeExerciseTrendKey(null,window)
                        weight=null
                    }
                    else -> {
                        val unit=visit.unit ?:continue
                        val performed=actual.load ?:continue
                        val assistance=basis==
                            LoadBasis.ASSISTANCE_COUNTERWEIGHT
                        if(actual.unit!=unit ||
                            (if(assistance)performed<zero else performed<=zero) ||
                            performed>max ||
                            performed.stripTrailingZeros().scale()>3)continue
                        val stationScoped=actual.machineInstanceId!=null ||
                            basis in setOf(LoadBasis.MACHINE_STACK,
                                LoadBasis.CABLE_STACK,LoadBasis.SMITH_TOTAL,
                                LoadBasis.EXERCISE_OVERRIDE,
                                LoadBasis.ASSISTANCE_COUNTERWEIGHT)
                        key=NativeExerciseTrendKey(basis,window,
                            if(stationScoped)visit.gymId else null,
                            actual.machineInstanceId)
                        weight=StrengthStandardsMeasurementDomain.convert(
                            performed,unit,displayUnit)
                    }
                }
                val sample=NativeExerciseTrendPoint(order,weight,actual.reps,actual.actualRir)
                val bySession=candidates.getOrPut(key){linkedMapOf()}
                val previous=bySession[order]
                // A lower counterweight is a harder assisted set. Unlike
                // external loads, ranking by a higher number would label
                // increased assistance as greater strength. Keep exact
                // source, station, rep window and actual-RIR provenance.
                // These are descriptive selections, never strength PRs.
                val improves=when {
                    key.bodyweightOnly ->
                        previous==null || sample.performedReps>previous.performedReps
                    key.assistedCounterweight ->
                        previous==null ||
                            checkNotNull(weight)<checkNotNull(previous.actualLoad) ||
                            (checkNotNull(weight).compareTo(checkNotNull(previous.actualLoad))==0 &&
                                sample.performedReps>previous.performedReps)
                    else ->previous==null ||
                        checkNotNull(weight)>checkNotNull(previous.actualLoad) ||
                        (checkNotNull(weight).compareTo(checkNotNull(previous.actualLoad))==0 &&
                            sample.performedReps>previous.performedReps)
                }
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
        }.thenBy {it.key.reps.lower}.thenBy {
            it.key.gymId.orEmpty()
        }.thenBy {it.key.machineInstanceId.orEmpty()})
        return NativeExercisePerformanceTrend(
            timeline.athleteId,timeline.manifestRevision,exerciseId,displayUnit,
            Collections.unmodifiableList(series))
    }
}
