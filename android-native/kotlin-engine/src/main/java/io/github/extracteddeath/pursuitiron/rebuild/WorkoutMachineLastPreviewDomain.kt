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
        val completionOrder:Long,
        /** Exact reviewed issued physical convention, if it was recorded. */
        val loadBasis:LoadBasis?=null
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
        val matches=workingSetKeys.distinct().mapIndexedNotNull {index,key->
            history.machineSets[WorkoutMachineReferenceKey(key,machineId)]
                ?.let{index to it}
        }
        if(matches.isEmpty())return WorkoutMachineLastPreview.NoMatchingSet
        // All sets in one workout have the SAME completion order. Prefer
        // its later working-set ordinal so a later failed attempt cannot be
        // hidden behind an earlier successful set from that same session.
        val latest=matches.maxWith(
            compareBy<Pair<Int,WorkoutLastSetReference>>(
                {it.second.completionOrder},{it.first})).second
        if(latest.completionOrder<=0L)
            return WorkoutMachineLastPreview.Unavailable
        val actual=latest.actual
        if(actual!=null &&
            actual.machineInstanceId!=machineId)
            return WorkoutMachineLastPreview.Unavailable
        return when(latest.outcome) {
            KotlinJournalSetOutcome.COMPLETED ->
                if(actual==null || actual.reps<1 || actual.load==null ||
                    actual.unit==null)
                    // A physical stack has a real load and unit. Never
                    // display an unverified 'bodyweight' station baseline.
                    WorkoutMachineLastPreview.Unavailable
                else WorkoutMachineLastPreview.Completed(
                    actual.load,actual.unit,actual.reps,
                    actual.actualRir,latest.completionOrder,latest.loadBasis)
            KotlinJournalSetOutcome.SKIPPED,
            KotlinJournalSetOutcome.FAILED_ATTEMPT ->
                WorkoutMachineLastPreview.NotCompleted(
                    latest.outcome,latest.completionOrder)
        }
    }
}
