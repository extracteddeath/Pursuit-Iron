package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import java.util.Collections

/**
 * Review-only connection between completely replayed Kotlin history and one
 * not-yet-issued strength-block slot. Never revises a finished workout or
 * applies the suggestion to the approved athlete cycle.
 *
 * Native ownership must authenticate full history window and first unissued
 * week against current persisted state before using this read-only review.
 */
data class EvidenceBackedStrengthWeekLoadRequest(
    val cycleReview:AdaptiveCycleOptionRequest,
    val history:KotlinProgressionTimelineRequest,
    val targetWeekInBlock:Int,
    val firstUnissuedWeekInBlock:Int,
    val weekday:Int,
    val slotId:String,
    val readiness:CoachReadiness,
    val movementRestricted:Boolean,
    val optionId:String="earned_strength_week_load",
    /**
     * Caller must supply the CURRENT owner-read assistance policy. This
     * DTO is not evidence of ownership or persisted preference revision.
     */
    val assistance:CoachAssistancePolicy=CoachAssistancePolicy.STANDARD,
    /**
     * Explicit optional choice: preserve the earned load in every remaining
     * unissued week of this block rather than silently reverting to the
     * original template in the next week. An authored weekly exception is
     * always protected and causes the entire proposal to be refused.
     */
    val carryForwardThroughBlock:Boolean=false
)

enum class EvidenceBackedStrengthWeekLoadStatus {
    INVALID_SCOPE, STALE_SOURCE_OR_GYM, PROTECTED_WEEK,
    HISTORY_UNVERIFIED, EVIDENCE_NOT_EARNED,
    DIFFERENT_PRESCRIPTION, RECOVERY_PRIORITY, ASSISTANCE_DISABLED,
    PERMISSION_REQUIRED, COACH_AUDIT_FAILED, READY_FOR_ATHLETE_REVIEW
}

data class EvidenceBackedStrengthWeekLoadResult(
    val status:EvidenceBackedStrengthWeekLoadStatus,
    val proposed:AdaptiveCycleOption?,
    val cycleComparison:AdaptiveCycleComparison?,
    val comparedWorkoutIds:List<String>,
    val oldLoad:BigDecimal?,
    val nextLoad:BigDecimal?,
    val explanation:String,
    /** A proposed weekly-template change is never retroactive. */
    val affectedWeeks:List<Int> = emptyList()
) {
    val mayApplyAutomatically:Boolean get()=false
    val mayIssueWorkout:Boolean get()=false
    val mayAlterHistory:Boolean get()=false
}

object EvidenceBackedStrengthWeekLoadDomain {
    private val id=Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,79}$")
    private fun same(a:BigDecimal,b:BigDecimal)=a.compareTo(b)==0
    private fun norm(v:BigDecimal)=BigDecimal(v.stripTrailingZeros().toPlainString())

    fun propose(input:EvidenceBackedStrengthWeekLoadRequest):
        EvidenceBackedStrengthWeekLoadResult {
        fun no(status:EvidenceBackedStrengthWeekLoadStatus,why:String,
            comparison:AdaptiveCycleComparison?=null,
            ids:List<String> =emptyList())=
            EvidenceBackedStrengthWeekLoadResult(
                status,null,comparison,ids,null,null,why)
        val review=input.cycleReview
        require(review.alternatives.isEmpty() &&
            review.prospectiveByOptionId.isEmpty()) {
            "An evidence bridge must review its own single complete candidate."
        }
        val cycle=review.approvedCycle
        val block=cycle.blocks.singleOrNull {it.blockId==review.targetBlockId}
        val day=block?.days?.singleOrNull {it.weekday==input.weekday}
        val slot=day?.slots?.singleOrNull {it.slotId==input.slotId}
        val history=input.history
        if(!id.matches(input.optionId) || !id.matches(input.slotId) ||
            cycle.revision<1 || cycle.revision==Long.MAX_VALUE ||
            block==null || block.phase!=TrainingPhase.STRENGTH ||
            block.purpose!=CustomBlockPurpose.TRAINING ||
            block.weeks !in 1..8 || day==null || slot==null ||
            input.firstUnissuedWeekInBlock !in 1..block.weeks ||
            input.targetWeekInBlock !in input.firstUnissuedWeekInBlock..block.weeks ||
            history.requestedDayIndex!=day.weekday ||
            history.requestedPurpose!=day.purpose ||
            history.exerciseId!=slot.exerciseId)
            return no(EvidenceBackedStrengthWeekLoadStatus.INVALID_SCOPE,
                "A current, exact future strength week, purpose, day and exercise are required.")
        if(!input.assistance.offersProgression)
            return no(EvidenceBackedStrengthWeekLoadStatus.ASSISTANCE_DISABLED,
                "Coaching or progression suggestions are off. The athlete's authored program remains unchanged.")
        val weeks=if(input.carryForwardThroughBlock)
            (input.targetWeekInBlock..block.weeks).toList()
        else listOf(input.targetWeekInBlock)
        if(cycle.weekOverrides[block.blockId].orEmpty().any {override->
            override.weekInBlock in weeks &&
                override.weekday==input.weekday &&
                override.slotId==slot.slotId
        })return no(EvidenceBackedStrengthWeekLoadStatus.PROTECTED_WEEK,
            "One affected future week already contains an athlete-authored prescription.")
        if(input.movementRestricted ||
            (input.assistance.offersFatigueAdvice &&
                input.readiness==CoachReadiness.LOW))
            return no(EvidenceBackedStrengthWeekLoadStatus.RECOVERY_PRIORITY,
                "Movement restrictions always block progression; low readiness takes priority when fatigue coaching is enabled.")

        val athlete=review.athleteSetup.reviewed
        val movement=review.catalog.singleOrNull {it.exerciseId==slot.exerciseId}
        val stock=movement?.equipmentId?.let {
            athlete?.gym?.confirmedStock?.get(it)
        }
        if(athlete==null || athlete.athleteProfileId!=cycle.athleteId ||
            history.athleteId!=cycle.athleteId ||
            history.completedWorkouts.isEmpty() ||
            history.completedWorkouts.any {
                val source=it.journal.snapshot.source
                source.athleteId!=cycle.athleteId ||
                    source.programId!=cycle.cycleId ||
                    source.programRevision!=cycle.revision ||
                    source.gymId!=athlete.gym.gymId
            } || history.currentVerifiedStock==null ||
            stock==null || stock!=history.currentVerifiedStock ||
            movement?.expectedLoadBasis!=stock.basis)
            return no(EvidenceBackedStrengthWeekLoadStatus.STALE_SOURCE_OR_GYM,
                "Exercise, athlete, exact gym inventory and historical program revision must agree.")

        val sets=slot.sets
        val loads=sets.mapNotNull {it.load as? DraftSetLoad.Verified}
        if(sets.isEmpty() || sets.size>20 ||
            sets.any {it.type!=DraftSetType.WORKING ||
                it.targetRir==null || it.amplifier!=null ||
                it.reps!=sets.first().reps ||
                it.targetRir!=sets.first().targetRir} ||
            loads.size!=sets.size || loads.any {
                it.basis!=stock.basis || it.unit!=stock.unit ||
                    !same(it.value,loads.first().value)
            })
            return no(EvidenceBackedStrengthWeekLoadStatus.DIFFERENT_PRESCRIPTION,
                "Only verified, uniform straight-set strength prescriptions can earn this change.")
        val current=loads.first().value
        val timeline=KotlinProgressionTimelineDomain.review(history)
        if(timeline.status!=KotlinTimelineReviewStatus.VERIFIED_REVIEW)
            return no(EvidenceBackedStrengthWeekLoadStatus.HISTORY_UNVERIFIED,
                "The latest two exercise appearances are not fully comparable.",
                ids=timeline.comparedWorkoutIds)
        val increase=timeline.progression
        if(increase?.canOfferIncrease!=true ||
            increase.currentObservedLoad==null ||
            increase.reviewCandidate==null)
            return no(EvidenceBackedStrengthWeekLoadStatus.EVIDENCE_NOT_EARNED,
                "Two complete, comparable sessions have not earned an increase.",
                ids=timeline.comparedWorkoutIds)

        // Reproject the already replayed latest completion to verify exact
        // issued prescriptions; a matching exercise ID alone is not enough.
        val latestId=timeline.comparedWorkoutIds.last()
        val logged=history.completedWorkouts.singleOrNull {
            it.journal.snapshot.workoutId==latestId
        } ?:return no(EvidenceBackedStrengthWeekLoadStatus.HISTORY_UNVERIFIED,
            "The compared completion was not retained.")
        val projection=logged.savedCorrections?.let {
            KotlinWorkoutHistoryEvidenceDomain.projectSaved(it)
        } ?:KotlinWorkoutHistoryEvidenceDomain.project(logged.journal)
        val latest=(projection as? KotlinHistoryProjectionResult.Candidate)
            ?.evidence?.exerciseReviews?.singleOrNull {
                it.exerciseId==slot.exerciseId
            }?.progressionWorkout
        if(latest==null || latest.expectedWorkingSets!=sets.size ||
            latest.targetReps!=sets.first().reps ||
            latest.observations.size!=sets.size ||
            latest.observations.any {
                it.issuedLoad==null ||
                    !same(checkNotNull(it.issuedLoad),current)
            } || !same(increase.currentObservedLoad,current))
            return no(EvidenceBackedStrengthWeekLoadStatus.DIFFERENT_PRESCRIPTION,
                "The latest issued reps, set counts and weight must match this exact future slot.",
                ids=timeline.comparedWorkoutIds)
        val candidate=increase.reviewCandidate
        val at=EquipmentLoadDomain.inspect(stock,current,stock.unit)
        val to=EquipmentLoadDomain.inspect(stock,candidate,stock.unit)
        if(at !is LoadInspection.Confirmed || !at.exact ||
            at.nextAbove==null || !same(at.nextAbove,candidate) ||
            to !is LoadInspection.Confirmed || !to.exact ||
            candidate<=current ||
            candidate.subtract(current)>history.maximumAuthorizedIncrease)
            return no(EvidenceBackedStrengthWeekLoadStatus.STALE_SOURCE_OR_GYM,
                "The earned load is not the first exact physical increment in the confirmed gym.")
        val newSets=Collections.unmodifiableList(sets.map {set->
            val load=set.load as DraftSetLoad.Verified
            set.copy(load=load.copy(value=norm(candidate)))
        })
        val overrides=weeks.map{week->
            CustomWeekSlotOverride(week,input.weekday,input.slotId,newSets)
        }
        val option=AdaptiveCycleOption(input.optionId,cycle.copy(
            revision=cycle.revision+1,
            weekOverrides=Collections.unmodifiableMap(
                cycle.weekOverrides+(block.blockId to
                    Collections.unmodifiableList(
                        cycle.weekOverrides[block.blockId].orEmpty()+overrides)))))
        val compared=AdaptiveCycleOptionReviewDomain.review(
            review.copy(alternatives=listOf(option)))
        val assessment=compared.evaluated.singleOrNull()
        if(assessment?.status==AdaptiveCycleOptionStatus.PERMISSION_REQUIRED)
            return no(EvidenceBackedStrengthWeekLoadStatus.PERMISSION_REQUIRED,
                "The athlete must separately permit weekly overrides and load increases.",
                compared,timeline.comparedWorkoutIds)
        if(assessment?.status!=AdaptiveCycleOptionStatus.READY_FOR_REVIEW ||
            compared.recommendedOptionId!=input.optionId)
            return no(EvidenceBackedStrengthWeekLoadStatus.COACH_AUDIT_FAILED,
                "The full cycle, session, physical dose or continuity audit rejected the option.",
                compared,timeline.comparedWorkoutIds)
        return EvidenceBackedStrengthWeekLoadResult(
            EvidenceBackedStrengthWeekLoadStatus.READY_FOR_ATHLETE_REVIEW,
            option,compared,Collections.unmodifiableList(timeline.comparedWorkoutIds),
            norm(current),norm(candidate),
            "The two latest comparable performances earned one exact load step across ${weeks.size} explicitly selected future strength week(s) on this day. Athlete approval is still required.",
            Collections.unmodifiableList(weeks))
    }
}
