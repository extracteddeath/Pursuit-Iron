package io.github.extracteddeath.pursuitiron

import io.github.extracteddeath.pursuitiron.nativecoachstore.NativeMachineRosterSnapshot
import io.github.extracteddeath.pursuitiron.rebuild.*

/**
 * Workout-facing station name. Existing physical machine IDs remain the only
 * persistence/history keys; raw private IDs never become visible UI labels.
 * Unknown old station names get a stable ordinal, not another station's name.
 */
internal fun workoutPhysicalStationName(
    exerciseId:String,gymId:String?,machineId:String?,
    roster:NativeMachineRosterSnapshot?
):String? {
    if(machineId==null)return null
    val known=roster?.configurations.orEmpty().filter{
        it.gymId==gymId && it.exerciseId==exerciseId
    }.flatMap{configuration->
        configuration.machines.map {configuration.gymId to it.id}
    } + (gymId to machineId)
    return NativeKotlinMachineStationLabels.label(
        exerciseId,gymId,machineId,known,roster).substringAfter(" · ")
}

/** Exact prior physical-station observations, never suggestions for new loads. */
internal fun workoutStationLastCaption(
    history:WorkoutLastReferenceResult?,
    workingKeys:List<WorkoutReferenceKey>,
    stationId:String
):String {
    if(history==null)return "Checking same-day history…"
    return when(val last=WorkoutMachineLastPreviewDomain.review(
        history,workingKeys,stationId)) {
        WorkoutMachineLastPreview.Unavailable ->
            "Previous sets unavailable · enter your own load"
        WorkoutMachineLastPreview.NoMatchingSet ->
            "No comparable previous set on this machine"
        is WorkoutMachineLastPreview.NotCompleted ->
            if(last.outcome==KotlinJournalSetOutcome.FAILED_ATTEMPT)
                "Last visit: failed attempt · no load copied"
            else "Last visit: skipped · no load copied"
        is WorkoutMachineLastPreview.Completed -> {
            val amount=last.load?.stripTrailingZeros()?.toPlainString()
            val unit=last.unit?.name?.lowercase()
            val loadText=if(amount!=null && unit!=null)"$amount $unit"
                else if(amount==null)"bodyweight" else "$amount (unit unreported)"
            "Last on this machine: $loadText × ${last.reps}" +
                (last.actualRir?.let{" · $it RIR"} ?: " · RIR unreported")
        }
    }
}

/**
 * One saved-set presentation for generated and custom journals. These are
 * factual receipts, never current target weights or next-set recommendations.
 * Physical station names are looked up by the SAVED actual machine identity.
 */
internal fun nativeSavedSetCaption(
    prefix:String,
    outcome:KotlinJournalSetOutcome?,
    actual:KotlinPerformedSet?,
    issuedLoad:DraftSetLoad,
    exerciseId:String,
    issuedGymId:String?,
    roster:NativeMachineRosterSnapshot?
):String {
    val parts=mutableListOf(
        outcome?.name?.replace('_',' ')?.lowercase() ?: "outcome unavailable")
    if(actual!=null) {
        parts+="${actual.reps} reps"
        // One factual load-label authority across the in-workout receipt,
        // Target/Last, set correction and final History. A missing observed
        // weight is NOT evidence of bodyweight. Per-hand, stack and assisted
        // counterweight conventions come only from a matching issued unit,
        // never from an inferred machine or gym.
        val observed=kotlinActualLoadLabel(actual,issuedLoad)
        parts+=when(observed) {
            "Bodyweight" -> "bodyweight"
            "Load not reported" -> "load not reported"
            else -> "at $observed"
        }
        parts+=(actual.actualRir?.let{"$it actual RIR"} ?: "actual RIR unreported")
        if(actual.machineInstanceId!=null)
            workoutPhysicalStationName(
                exerciseId,issuedGymId,actual.machineInstanceId,roster)
                ?.let{parts+=it}
    }
    return "$prefix: "+parts.joinToString(" · ")
}
