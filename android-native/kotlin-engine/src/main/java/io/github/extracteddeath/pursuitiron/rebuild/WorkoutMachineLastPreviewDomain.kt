package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal

/**
 * One read-only, same-program-day preview for each physical station.
 *
 * Callers supply owner-verified [WorkoutLastReferenceResult] for the exact
 * issued workout and only its current exercise's stable working-set keys.
 * Never consult generic .sets: the latest visit on another station is not
 * evidence of a decline, a suggested conversion, or permission to progress.
 */
sealed interface WorkoutMachineLastPreview {
    data object Unavailable:WorkoutMachineLastPreview
    data object NoMatchingSet:WorkoutMachineLastPreview
    data class Completed(
        val load:BigDecimal?,
        val unit:LoadUnit?,
        val reps:Int,
        val actualRir:Int?,
        val completionOrder:Long
    ):WorkoutMachineLastPreview
    data class NotCompleted(
        val outcome:KotlinJournalSetOutcome,
        val completionOrder:Long
    ):WorkoutMachineLastPreview
}

object WorkoutMachineLastPreviewDomain {
    fun review(
        history:WorkoutLastReferenceResult,
        workingSetKeys:List<WorkoutReferenceKey>,
        machineId:String
    ):WorkoutMachineLastPreview {
        if(history !is WorkoutLastReferenceResult.Ready)
            return WorkoutMachineLastPreview.Unavailable
        if(!KotlinMachineAlternativesDomain.validInstanceId(machineId))
            return WorkoutMachineLastPreview.NoMatchingSet
        // The caller must enumerate exact current-workout exercise keys.
        // Never use an unrelated exercise or an arbitrary 'best' machine PR.
        val matches=workingSetKeys.distinct().mapNotNull {key->
            history.machineSets[WorkoutMachineReferenceKey(key,machineId)]
        }
        if(matches.isEmpty())return WorkoutMachineLastPreview.NoMatchingSet
        val newestOrder=matches.maxOf{it.completionOrder}
        val latest=matches.first{it.completionOrder==newestOrder}
        if(latest.completionOrder<=0L)
            return WorkoutMachineLastPreview.Unavailable
        val actual=latest.actual
        if(actual!=null &&
            actual.machineInstanceId!=machineId)
            return WorkoutMachineLastPreview.Unavailable
        return when(latest.outcome) {
            KotlinJournalSetOutcome.COMPLETED ->
                if(actual==null || actual.reps<1)
                    WorkoutMachineLastPreview.Unavailable
                else WorkoutMachineLastPreview.Completed(
                    actual.load,actual.unit,actual.reps,
                    actual.actualRir,latest.completionOrder)
            KotlinJournalSetOutcome.SKIPPED,
            KotlinJournalSetOutcome.FAILED_ATTEMPT ->
                WorkoutMachineLastPreview.NotCompleted(
                    latest.outcome,latest.completionOrder)
        }
    }
}
