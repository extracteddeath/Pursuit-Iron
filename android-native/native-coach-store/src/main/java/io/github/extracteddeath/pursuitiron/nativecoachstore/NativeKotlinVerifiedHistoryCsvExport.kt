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
        val dated=history.completions.any{it.manifest.completedAtEpochMillis!=null}
        val measured=history.completions.any{
            it is NativeKotlinMixedHistoryCompletion.Measured}
        val measuredColumns=listOf("measurement_kind","measurement_unit",
            "measured_sides","physical_setup_id","band_id",
            "target_measurement","actual_carry_seconds")
        val freestyle=history.completions.any{it is NativeKotlinMixedHistoryCompletion.Freestyle}
        val staged=history.completions.filterIsInstance<
            NativeKotlinMixedHistoryCompletion.Custom>().any {completion ->
            completion.evidence.visits.any{it.stage>1}
        }
        val header=columns+(if(staged)listOf("amplifier_stage") else emptyList())+
            (if(dated)listOf("completed_at_utc") else emptyList())+
            (if(measured)measuredColumns else emptyList())+
            (if(freestyle)listOf("actual_load_basis") else emptyList())
        val format=if(freestyle)"pursuit-iron-history-v5"
            else if(measured)"pursuit-iron-history-v4"
            else if(staged)"pursuit-iron-history-v3"
            else if(dated)"pursuit-iron-history-v2" else VERSION
        val csv=StringBuilder(header.joinToString(","){cell(it)}).append('\n')

        fun add(
            order:Long,kind:String,workoutId:String,gymId:String,
            exerciseId:String,slotId:String,setKey:String,part:String,type:DraftSetType,
            outcome:KotlinJournalSetOutcome,prescription:DraftSetLoad?,
            reps:RepBand?,rir:Int?,actual:KotlinPerformedSet?,
            amplifier:String="",amplifierStage:Int=1,
            completedAtEpochMillis:Long?=null,
            physicalBasis:LoadBasis?=(prescription as? DraftSetLoad.Verified)?.basis
        ):Boolean {
            if(rows>=MAX_ROWS)return false
            val (targetLoad,targetUnit,targetBasis)=prescription?.let(::load)?:Triple("","","")
            val fields=listOf(
                format,order.toString(),kind,workoutId,gymId,
                exerciseId,slotId,
                setKey,part,type.name,outcome.name,
                targetLoad,targetUnit,targetBasis,reps?.minimum?.toString().orEmpty(),
                reps?.maximum?.toString().orEmpty(),rir?.toString().orEmpty(),
                number(actual?.load),actual?.unit?.name.orEmpty(),
                actual?.reps?.toString().orEmpty(),
                actual?.actualRir?.toString().orEmpty(),
                actual?.machineInstanceId.orEmpty(),amplifier
            )
            val values=fields+
                (if(staged)listOf(if(part=="FOLLOW_UP")amplifierStage.toString()
                    else "") else emptyList())+
                (if(dated)listOf(completedAtEpochMillis?.let{
                    java.time.Instant.ofEpochMilli(it).toString()}.orEmpty())
                else emptyList())+
                (if(measured)List(measuredColumns.size){""} else emptyList())+
                (if(freestyle)listOf(if(actual==null)"" else physicalBasis?.name?:"BODYWEIGHT") else emptyList())
            csv.append(values.joinToString(","){cell(it)}).append('\n')
            rows++
            return csv.length<=MAX_CHARS
        }

        fun addMeasured(
            completion:NativeKotlinMixedHistoryCompletion.Measured,
            plan:MeasuredExerciseSetPlan,
            actual:MeasuredJournalOperation.Record,
            definition:MeasuredExerciseDefinition
        ):Boolean {
            if(rows>=MAX_ROWS)return false
            val outcome=actual.outcome
            val observed=actual.actual
            val unit=when(definition.kind) {
                MeasuredExerciseKind.HOLD_SECONDS -> "SECONDS"
                MeasuredExerciseKind.CARRY_CENTIMETERS -> "CENTIMETERS"
                MeasuredExerciseKind.BANDED_REPETITIONS -> "REPETITIONS"
            }
            val fields=listOf(
                format,completion.manifest.completionOrder.toString(),
                "MEASURED",completion.manifest.workoutId,
                completion.stored.journal.approvedPlan.gymId,
                plan.exerciseId,"",plan.setKey,"MEASUREMENT","WORKING",
                outcome.name,number(plan.intendedExternalLoad),
                plan.unit?.name.orEmpty(),
                definition.expectedBasis?.name.orEmpty(),"","","",
                number(observed?.actualExternalLoad),observed?.unit?.name.orEmpty(),
                "","","",""
            )
            val details=listOf(definition.kind.name,unit,
                observed?.sides?.sortedBy{it.side.ordinal}
                    ?.joinToString("|"){"${it.side.name}:${it.amount}"}.orEmpty(),
                plan.physicalSetupId,plan.bandId.orEmpty(),
                plan.targetAmount.toString(),
                observed?.elapsedSeconds?.toString().orEmpty())
            val full=fields+
                (if(staged)listOf("") else emptyList())+
                (if(dated)listOf(completion.manifest.completedAtEpochMillis?.let{
                    java.time.Instant.ofEpochMilli(it).toString()}.orEmpty())
                    else emptyList())+
                details+(if(freestyle)listOf("") else emptyList())
            if(full.size!=header.size)return false
            csv.append(full.joinToString(","){cell(it)}).append('\n')
            rows++
            return csv.length<=MAX_CHARS
        }

        for(completion in history.completions) {
            val manifest=completion.manifest
            if(manifest.completionOrder!=expectedOrder++ ||
                !ids.add(manifest.workoutId))
                return NativeKotlinHistoryCsvResult.InvalidHistory
            when(completion) {
                is NativeKotlinMixedHistoryCompletion.Freestyle -> {
                    val workout=completion.workout
                    if(workout.source.athleteId!=history.athleteId ||
                        workout.workoutId!=manifest.workoutId ||
                        workout.finishedByEventId!=manifest.finishEventId ||
                        workout.revision!=manifest.originalJournalRevision ||
                        manifest.savedCorrectionRevision!=0L ||
                        KotlinFreestyleWorkoutDomain.replay(workout.workoutId,workout.source,
                            workout.receipts)!=workout)
                        return NativeKotlinHistoryCsvResult.InvalidHistory
                    for((exercise,record) in workout.observations())if(!add(
                        manifest.completionOrder,"FREESTYLE",manifest.workoutId,
                        workout.source.gymId,exercise.exerciseId,exercise.slotId,record.setKey,
                        "MAIN",record.type,record.outcome,null,null,null,record.actual,
                        completedAtEpochMillis=manifest.completedAtEpochMillis,
                        physicalBasis=exercise.basis))
                        return NativeKotlinHistoryCsvResult.TooLarge
                }
                is NativeKotlinMixedHistoryCompletion.Measured -> {
                    val stored=completion.stored
                    if(stored.athleteId!=history.athleteId ||
                        stored.journal.approvedPlan.workoutId!=manifest.workoutId ||
                        stored.journal.finishedByEventId!=manifest.finishEventId ||
                        stored.journal.revision!=manifest.originalJournalRevision)
                        return NativeKotlinHistoryCsvResult.InvalidHistory
                    for(plan in stored.journal.approvedPlan.sets) {
                        val definition=MeasuredExerciseCatalogDomain.find(
                            plan.exerciseId)
                            ?:return NativeKotlinHistoryCsvResult.InvalidHistory
                        val effective=stored.journal.bySetKey[plan.setKey]
                            ?.effectiveRecord?.operation
                            as? MeasuredJournalOperation.Record
                            ?:return NativeKotlinHistoryCsvResult.InvalidHistory
                        if(!addMeasured(completion,plan,effective,definition))
                            return NativeKotlinHistoryCsvResult.TooLarge
                    }
                }
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
                        set.issuedLoad,set.issuedReps,set.issuedRir,set.actual,
                        completedAtEpochMillis=manifest.completedAtEpochMillis))
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
                        visit.amplifierKind?.name.orEmpty(),
                        amplifierStage=visit.stage,
                        completedAtEpochMillis=manifest.completedAtEpochMillis))
                        return NativeKotlinHistoryCsvResult.TooLarge
                }
            }
        }
        return NativeKotlinHistoryCsvResult.Ready(
            csv.toString(),history.completions.size,rows)
    }
}
