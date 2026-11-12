package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.math.BigDecimal

/**
 * Portable, factual history only. The caller MUST first read an owner-verified
 * NativeKotlinMixedHistoryReadResult.Review; this formatter has no file,
 * network, workout, coaching, approval or backup-restore permissions.
 *
 * Export is deliberately NOT a restorable application backup. It contains
 * effective corrected outcomes and original targets, not predicted PRs,
 * estimated historical dates, unsubmitted edits or private session notes.
 */
sealed interface NativeKotlinHistoryCsvResult {
    data class Ready(val csv:String,val sessionCount:Int,val rowCount:Int):
        NativeKotlinHistoryCsvResult
    data object InvalidHistory:NativeKotlinHistoryCsvResult
    data object TooLarge:NativeKotlinHistoryCsvResult
}

object NativeKotlinVerifiedHistoryCsvExport {
    private const val VERSION="pursuit-iron-history-v1"
    private const val MAX_ROWS=100_000
    private const val MAX_CHARS=16*1024*1024
    private val columns=listOf(
        "format","session_order","workout_kind","workout_id","gym_id",
        "exercise_id","slot_id","set_key","set_part","set_type","outcome",
        "target_load","target_unit","target_basis","target_reps_min",
        "target_reps_max","target_rir","actual_load","actual_unit",
        "actual_reps","actual_rir","machine_id","amplifier"
    )

    // Quoting alone does NOT stop spreadsheet formula evaluation. A leading
    // apostrophe protects user-sourced identifiers when opened in a sheet.
    private fun cell(text:String):String {
        val dangerous=text.firstOrNull()?.let {
            it=='=' || it=='+' || it=='-' || it=='@' ||
                it=='\t' || it=='\r' || it=='\n'
        }==true
        return "\"" + (if(dangerous)"'$text" else text)
            .replace("\"","\"\"") + "\""
    }
    private fun number(value:BigDecimal?):String =
        value?.stripTrailingZeros()?.toPlainString().orEmpty()

    private fun load(load:DraftSetLoad):Triple<String,String,String> = when(load) {
        is DraftSetLoad.Verified ->Triple(
            number(load.value),load.unit.name,load.basis.name)
        DraftSetLoad.ExplicitBodyweight ->Triple("","","BODYWEIGHT")
    }

    fun prepare(history:NativeKotlinMixedHistoryTimeline):
        NativeKotlinHistoryCsvResult {
        if(history.athleteId.isBlank() || history.manifestRevision<1 ||
            history.completions.size>MAX_ROWS)return NativeKotlinHistoryCsvResult.InvalidHistory
        val ids=mutableSetOf<String>()
        var expectedOrder=1L
        var rows=0
        val csv=StringBuilder(columns.joinToString(","){cell(it)}).append('\n')

        fun add(
            order:Long,kind:String,workoutId:String,gymId:String,
            exerciseId:String,slotId:String,setKey:String,part:String,type:DraftSetType,
            outcome:KotlinJournalSetOutcome,prescription:DraftSetLoad,
            reps:RepBand,rir:Int?,actual:KotlinPerformedSet?,
            amplifier:String=""
        ):Boolean {
            if(rows>=MAX_ROWS)return false
            val (targetLoad,targetUnit,targetBasis)=load(prescription)
            val fields=listOf(
                VERSION,order.toString(),kind,workoutId,gymId,
                exerciseId,slotId,
                setKey,part,type.name,outcome.name,
                targetLoad,targetUnit,targetBasis,reps.minimum.toString(),
                reps.maximum.toString(),rir?.toString().orEmpty(),
                number(actual?.load),actual?.unit?.name.orEmpty(),
                actual?.reps?.toString().orEmpty(),
                actual?.actualRir?.toString().orEmpty(),
                actual?.machineInstanceId.orEmpty(),amplifier
            )
            csv.append(fields.joinToString(","){cell(it)}).append('\n')
            rows++
            return csv.length<=MAX_CHARS
        }

        for(completion in history.completions) {
            val manifest=completion.manifest
            if(manifest.completionOrder!=expectedOrder++ ||
                !ids.add(manifest.workoutId))
                return NativeKotlinHistoryCsvResult.InvalidHistory
            when(completion) {
                is NativeKotlinMixedHistoryCompletion.Composer -> {
                    val fact=completion.evidence
                    if(fact.workoutId!=manifest.workoutId ||
                        fact.finishEventId!=manifest.finishEventId)
                        return NativeKotlinHistoryCsvResult.InvalidHistory
                    if(fact.sets.any {
                        it.outcome==KotlinJournalSetOutcome.SKIPPED && it.actual!=null
                    })return NativeKotlinHistoryCsvResult.InvalidHistory
                    for(set in fact.sets)if(!add(
                        manifest.completionOrder,"GENERATED",manifest.workoutId,
                        fact.source.gymId,set.exerciseId,"",set.setKey,"MAIN",set.type,set.outcome,
                        set.issuedLoad,set.issuedReps,set.issuedRir,set.actual))
                        return NativeKotlinHistoryCsvResult.TooLarge
                }
                is NativeKotlinMixedHistoryCompletion.Custom -> {
                    val fact=completion.evidence
                    if(fact.athleteId!=history.athleteId ||
                        fact.workoutId!=manifest.workoutId ||
                        fact.finishedEventId!=manifest.finishEventId)
                        return NativeKotlinHistoryCsvResult.InvalidHistory
                    if(fact.visits.any {
                        it.outcome==KotlinJournalSetOutcome.SKIPPED && it.actual!=null
                    })return NativeKotlinHistoryCsvResult.InvalidHistory
                    for(visit in fact.visits)if(!add(
                        manifest.completionOrder,"CUSTOM",manifest.workoutId,
                        completion.gymId,visit.exerciseId,visit.slotId,visit.setKey,
                        visit.part.name,visit.setType,visit.outcome,
                        visit.prescribedLoad,visit.prescribedReps,
                        visit.prescribedRir,visit.actual,
                        visit.amplifierKind?.name.orEmpty()))
                        return NativeKotlinHistoryCsvResult.TooLarge
                }
            }
        }
        return NativeKotlinHistoryCsvResult.Ready(
            csv.toString(),history.completions.size,rows)
    }
}
