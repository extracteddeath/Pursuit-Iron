package io.github.extracteddeath.pursuitiron

import io.github.extracteddeath.pursuitiron.nativecoachstore.*
import io.github.extracteddeath.pursuitiron.rebuild.*

/** A completed native workout's exact journal outcomes, not estimates or PRs.
 * The UI may build these only from an owner-fenced, verified History result. */
internal data class NativeKotlinRecapVisit(
    val exerciseId:String,
    val type:DraftSetType,
    val followUp:Boolean,
    val outcome:KotlinJournalSetOutcome
)

internal data class NativeKotlinRecapExercise(
    val exerciseId:String,
    val completedMain:Int,
    val skippedMain:Int,
    val failedMain:Int
)

internal data class NativeKotlinWorkoutRecap(
    val completedMain:Int,
    val skippedMain:Int,
    val failedMain:Int,
    val completedWarmups:Int,
    val completedAmplifiers:Int,
    val skippedOther:Int,
    val failedOther:Int,
    val exercises:List<NativeKotlinRecapExercise>
) {
    val totalRecordedVisits:Int get()=
        completedMain+skippedMain+failedMain+
        completedWarmups+completedAmplifiers+skippedOther+failedOther
}

/** No training policy, edited result, predicted strength or approximate volume.
 * Distinguishes MAIN working sets from warmups and amplifier follow-ups.
 * All skipped and failed entries are still represented (including non-main).
 */
internal object NativeKotlinWorkoutRecapDomain {
    private val validExercise=Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,79}$")
    fun fromVerifiedCompletion(
        completion:NativeKotlinMixedHistoryCompletion
    ):NativeKotlinWorkoutRecap? {
        val rows=when(completion) {
            is NativeKotlinMixedHistoryCompletion.Composer ->
                completion.evidence.sets.map {
                    NativeKotlinRecapVisit(
                        it.exerciseId,it.type,false,it.outcome)
                }
            is NativeKotlinMixedHistoryCompletion.Custom ->
                completion.evidence.visits.map {
                    NativeKotlinRecapVisit(
                        it.exerciseId,it.setType,
                        it.part==CustomWorkoutVisitPart.FOLLOW_UP,it.outcome)
                }
        }
        return summarize(rows)
    }

    fun summarize(rows:List<NativeKotlinRecapVisit>):NativeKotlinWorkoutRecap? {
        if(rows.isEmpty() || rows.size>4096 ||
            rows.any{!validExercise.matches(it.exerciseId)})return null
        val groups=rows.groupBy{it.exerciseId}
        if(groups.size>128)return null
        fun main(row:NativeKotlinRecapVisit)=
            !row.followUp && row.type==DraftSetType.WORKING
        fun count(predicate:(NativeKotlinRecapVisit)->Boolean)=
            rows.count(predicate)
        val done=KotlinJournalSetOutcome.COMPLETED
        val skip=KotlinJournalSetOutcome.SKIPPED
        val failed=KotlinJournalSetOutcome.FAILED_ATTEMPT
        val exercises=groups.map{(id,items)->
            NativeKotlinRecapExercise(
                id,items.count{main(it) && it.outcome==done},
                items.count{main(it) && it.outcome==skip},
                items.count{main(it) && it.outcome==failed})
        }
        return NativeKotlinWorkoutRecap(
            completedMain=count{main(it)&&it.outcome==done},
            skippedMain=count{main(it)&&it.outcome==skip},
            failedMain=count{main(it)&&it.outcome==failed},
            completedWarmups=count{
                !it.followUp && it.type==DraftSetType.WARMUP && it.outcome==done},
            completedAmplifiers=count{it.followUp&&it.outcome==done},
            skippedOther=count{!main(it)&&it.outcome==skip},
            failedOther=count{!main(it)&&it.outcome==failed},
            exercises=exercises
        )
    }
}
