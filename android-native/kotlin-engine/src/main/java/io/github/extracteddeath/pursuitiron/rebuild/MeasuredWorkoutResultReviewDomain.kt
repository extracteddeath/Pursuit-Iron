package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal

/**
 * Read-only, correction-aware view of actual measured set observations.
 * A target is NEVER presented as a performed result. A resolved skipped or
 * failed attempt is not labeled as a completed repetition/hold/carry.
 *
 * This projection has no write, Finish, progression, PR or history authority.
 * The caller must first obtain an owner-verified parent journal.
 */
enum class MeasuredSetReviewStatus {
    PENDING, COMPLETED, SKIPPED, FAILED_ATTEMPT, UNVERIFIED
}

data class MeasuredSetResultReview(
    val status:MeasuredSetReviewStatus,
    val actualSummary:String?=null
) {
    val resolved:Boolean get()=status==MeasuredSetReviewStatus.COMPLETED ||
        status==MeasuredSetReviewStatus.SKIPPED ||
        status==MeasuredSetReviewStatus.FAILED_ATTEMPT

    val statusLabel:String get()=when(status) {
        MeasuredSetReviewStatus.PENDING -> "Not recorded"
        MeasuredSetReviewStatus.COMPLETED -> "Completed"
        MeasuredSetReviewStatus.SKIPPED -> "Skipped"
        MeasuredSetReviewStatus.FAILED_ATTEMPT -> "Failed attempt"
        MeasuredSetReviewStatus.UNVERIFIED -> "Result unavailable"
    }
}

object MeasuredWorkoutResultReviewDomain {
    fun review(
        plan:MeasuredExerciseSetPlan,
        journal:MeasuredExerciseJournal?
    ):MeasuredSetResultReview {
        if(journal==null || journal.immutablePlan!=plan ||
            !MeasuredExerciseJournalDomain.verified(journal))
            return MeasuredSetResultReview(MeasuredSetReviewStatus.UNVERIFIED)
        val receipt=journal.effectiveRecord
            ?:return MeasuredSetResultReview(MeasuredSetReviewStatus.PENDING)
        val record=receipt.operation as? MeasuredJournalOperation.Record
            ?:return MeasuredSetResultReview(MeasuredSetReviewStatus.UNVERIFIED)
        // Even a detached or incorrectly attached snapshot must not be
        // mistaken for a durable actual observation.
        if(receipt !in journal.receipts ||
            !MeasuredExerciseDomain.validActual(plan,record.outcome,record.actual))
            return MeasuredSetResultReview(MeasuredSetReviewStatus.UNVERIFIED)
        when(record.outcome) {
            MeasuredSetOutcome.SKIPPED ->
                return MeasuredSetResultReview(MeasuredSetReviewStatus.SKIPPED)
            MeasuredSetOutcome.FAILED_ATTEMPT ->
                return MeasuredSetResultReview(MeasuredSetReviewStatus.FAILED_ATTEMPT,
                    actualSummary(plan,checkNotNull(record.actual)))
            MeasuredSetOutcome.COMPLETED ->
                return MeasuredSetResultReview(MeasuredSetReviewStatus.COMPLETED,
                    actualSummary(plan,checkNotNull(record.actual)))
        }
    }

    private fun actualSummary(
        plan:MeasuredExerciseSetPlan,actual:MeasuredExerciseActual
    ):String {
        val definition=checkNotNull(MeasuredExerciseCatalogDomain.find(plan.exerciseId))
        val observations=actual.sides.sortedBy{it.side.ordinal}.map { result ->
            val side=when(result.side) {
                MeasuredSide.BOTH -> ""
                MeasuredSide.LEFT -> "Left "
                MeasuredSide.RIGHT -> "Right "
            }
            val value=when(definition.kind) {
                MeasuredExerciseKind.HOLD_SECONDS -> result.amount.toString()+" s"
                MeasuredExerciseKind.CARRY_CENTIMETERS ->
                    BigDecimal(result.amount).movePointLeft(2)
                        .stripTrailingZeros().toPlainString()+" m"
                MeasuredExerciseKind.BANDED_REPETITIONS ->
                    result.amount.toString()+" reps"
            }
            side+value
        }.toMutableList()
        actual.actualExternalLoad?.let { weight ->
            val unit=when(actual.unit) {
                LoadUnit.LB -> "lb"
                LoadUnit.KG -> "kg"
                null -> return@let
            }
            val basis=when(definition.loadKind) {
                MeasuredLoadKind.VERIFIED_PER_HAND -> " per hand"
                MeasuredLoadKind.VERIFIED_ADDED_LOAD -> " added"
                else -> ""
            }
            observations.add("External "+weight.stripTrailingZeros()
                .toPlainString()+" "+unit+basis)
        }
        actual.bandId?.let { observations.add("Band "+it) }
        actual.elapsedSeconds?.let { observations.add("Elapsed "+it+" s") }
        return observations.joinToString(" · ")
    }
}
