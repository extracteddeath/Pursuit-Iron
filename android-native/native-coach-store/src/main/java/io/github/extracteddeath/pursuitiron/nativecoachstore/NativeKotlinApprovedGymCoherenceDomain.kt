package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.math.BigDecimal

/**
 * Strict READ-ONLY comparison of an accepted generated Kotlin program with
 * the current, separately verified native athlete + gym + catalog source.
 *
 * Both inputs are public data classes. This is a pure feasibility rule,
 * NOT authentication; the owner coordinator must read BOTH from private
 * disk under the SAME NativeKotlinApprovalRevisionFence before any mutation.
 *
 * A program cannot authorize new loads if its physical stock, exercise
 * family/variant/attachment, muscle credits or athlete schedule disagree
 * with the present native source. Missing context is a refusal, not a guess.
 */
enum class NativeApprovedGymCoherenceRefusal {
    UNREVIEWED_SOURCE, WRONG_ATHLETE_OR_GYM, INVALID_SCHEDULE,
    EXCLUDED_EXERCISE, UNKNOWN_EXERCISE_VARIANT,
    CATALOG_INTENT_CHANGED, ATTACHMENT_OR_FACILITY_UNAVAILABLE,
    PHYSICAL_EQUIPMENT_CHANGED
}

sealed interface NativeApprovedGymCoherenceResult {
    data object Matched : NativeApprovedGymCoherenceResult
    data class Rejected(
        val reason: NativeApprovedGymCoherenceRefusal,
        val exerciseId: String? = null
    ) : NativeApprovedGymCoherenceResult
}

object NativeKotlinApprovedGymCoherenceDomain {
    private fun norm(v: BigDecimal) =
        BigDecimal(v.stripTrailingZeros().toPlainString())

    private fun equivalent(a: VerifiedLoadStock, b: VerifiedLoadStock):Boolean =
        when {
            a is VerifiedLoadStock.Totals && b is VerifiedLoadStock.Totals ->
                a.unit==b.unit && a.basis==b.basis &&
                    a.loads.map(::norm).toSet()==b.loads.map(::norm).toSet() &&
                    a.loads.size==b.loads.size
            a is VerifiedLoadStock.Stack && b is VerifiedLoadStock.Stack ->
                a.unit==b.unit && a.basis==b.basis &&
                    norm(a.minimum)==norm(b.minimum) &&
                    norm(a.maximum)==norm(b.maximum) &&
                    norm(a.increment)==norm(b.increment)
            a is VerifiedLoadStock.Barbell && b is VerifiedLoadStock.Barbell ->
                a.unit==b.unit && norm(a.barWeight)==norm(b.barWeight) &&
                    a.pairedPlates.mapKeys {norm(it.key)}
                        .mapValues {it.value} ==
                    b.pairedPlates.mapKeys {norm(it.key)}
                        .mapValues {it.value}
            else -> false
        }

    fun inspect(
        approval: NativeKotlinWorkoutApproval,
        authoring: NativeCustomAuthoringSnapshot
    ): NativeApprovedGymCoherenceResult {
        fun refused(reason: NativeApprovedGymCoherenceRefusal,id: String?=null) =
            NativeApprovedGymCoherenceResult.Rejected(reason,id)
        val athlete=authoring.setup.reviewed
        if(authoring.setup.status!=AthleteSetupStatus.READY_FOR_PROGRAM_REVIEW ||
            athlete==null || authoring.revision<1)
            return refused(NativeApprovedGymCoherenceRefusal.UNREVIEWED_SOURCE)
        if(authoring.ownerKey!=approval.ownerKey ||
            authoring.athleteId!=approval.athleteId ||
            athlete.athleteProfileId!=approval.athleteId ||
            athlete.gym.gymId!=approval.currentSource.gymId)
            return refused(NativeApprovedGymCoherenceRefusal.WRONG_ATHLETE_OR_GYM)
        val plan=approval.program
        val weekdays=plan.days.map {it.calendarDay}
        if(plan.split!=athlete.split ||
            weekdays.toSet()!=athlete.trainingWeekdays ||
            weekdays.distinct().size!=weekdays.size ||
            weekdays.any {it in athlete.declaredRestDays} ||
            plan.maximumBarbellExercisesPerDay != athlete.maxBarbellExercisesPerDay ||
            plan.allowDirectTricepsOnLower != athlete.allowTricepsOnLower)
            return refused(NativeApprovedGymCoherenceRefusal.INVALID_SCHEDULE)
        val catalog=authoring.catalog.associateBy {it.exerciseId}
        if(catalog.size!=authoring.catalog.size)
            return refused(NativeApprovedGymCoherenceRefusal.UNKNOWN_EXERCISE_VARIANT)
        for(option in plan.exercises) {
            val id=option.exerciseId
            if(id in athlete.excludedExerciseIds)
                return refused(NativeApprovedGymCoherenceRefusal.EXCLUDED_EXERCISE,id)
            val move=catalog[id] ?:return refused(
                NativeApprovedGymCoherenceRefusal.UNKNOWN_EXERCISE_VARIANT,id)
            if(move.creditStatus!=CatalogCreditStatus.AUTHOR_REVIEWED_PROVISIONAL ||
                move.creditReviewReference.isNullOrBlank() ||
                move.pattern!=option.pattern ||
                move.role!=option.role ||
                !move.permittedFocus.containsAll(option.allowedFocus) ||
                move.credits!=option.credits ||
                move.sourcePracticalReps!=option.sourcePracticalReps ||
                move.recoveryGroupId!=option.recoveryGroup ||
                move.minimumRecoveryGroupDayGap!=option.minimumGroupCalendarDayGap)
                return refused(NativeApprovedGymCoherenceRefusal.CATALOG_INTENT_CHANGED,id)
            if(!athlete.gym.availableFacilities.containsAll(move.requiredFacilityIds) ||
                (move.attachmentId!=null &&
                    move.attachmentId !in athlete.gym.availableAttachments))
                return refused(NativeApprovedGymCoherenceRefusal.ATTACHMENT_OR_FACILITY_UNAVAILABLE,id)
            when(val equipment=option.equipment) {
                ComposerEquipment.Bodyweight -> {
                    if(!athlete.allowBodyweight || move.equipmentId!=null ||
                        move.expectedLoadBasis!=null)
                        return refused(
                            NativeApprovedGymCoherenceRefusal.PHYSICAL_EQUIPMENT_CHANGED,id)
                }
                is ComposerEquipment.Verified -> {
                    val actualStock=move.equipmentId?.let {
                        athlete.gym.confirmedStock[it]
                    }
                    val optionStock=equipment.inventory
                    val attainable=EquipmentLoadDomain.inspect(
                        actualStock,equipment.plannedLoad,equipment.unit)
                    if(actualStock==null || optionStock==null ||
                        actualStock.unit!=equipment.unit ||
                        actualStock.basis!=move.expectedLoadBasis ||
                        !equivalent(actualStock,optionStock) ||
                        attainable !is LoadInspection.Confirmed ||
                        !attainable.exact ||
                        attainable.basis!=move.expectedLoadBasis)
                        return refused(
                            NativeApprovedGymCoherenceRefusal.PHYSICAL_EQUIPMENT_CHANGED,id)
                }
            }
        }
        return NativeApprovedGymCoherenceResult.Matched
    }
}
