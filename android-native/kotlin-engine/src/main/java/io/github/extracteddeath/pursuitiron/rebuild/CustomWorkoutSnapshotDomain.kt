package io.github.extracteddeath.pursuitiron.rebuild

import java.util.Collections

/**
 * A lossless native Kotlin session snapshot for athlete-authored training.
 *
 * Unlike ComposerRequest-based KotlinWorkoutTarget, this retains exact
 * per-slot exercise choices, stable slot IDs, all individual warmup/working
 * sets, attachments, supersets, pinned status, notes, selected block/week,
 * and every amplifier including explicit follow-up load.
 *
 * The preview NEVER issues a workout or modifies the training journal.
 * The owner-scoped custom issuer must verify an exact current athlete
 * consent, durable source, history migration and one-time workout ID before
 * accepting any session. This data is not an authorization token.
 */
data class CustomWorkoutSetSnapshot(
    val setKey: String,
    val slotId: String,
    val ordinalInSlot: Int,
    val prescription: CustomAuthoredSet
)
data class CustomWorkoutSlotSnapshot(
    val slotId: String,
    val exerciseId: String,
    val familyId: String,
    val variantId: String,
    val role: ComposerRole,
    val attachmentId: String?,
    val supersetGroup: String?,
    val pinned: Boolean,
    val coachNote: String,
    val sets: List<CustomWorkoutSetSnapshot>,
    /** Bound physical machine from the signed custom cycle, not a live
     * gym default. Performed sets still record the actual A/B machine. */
    val machineInstanceId: String? = null
)
data class CustomWorkoutSourceStamp(
    val athleteId: String,
    val athleteProfileRevision: String,
    val cycleId: String,
    val cycleRevision: Long,
    val gymId: String,
    val gymCatalogSourceRevision: Long
)
data class CustomWorkoutDaySnapshot(
    val stamp: CustomWorkoutSourceStamp,
    val absoluteWeek: Int,
    val weekInBlock: Int,
    val blockId: String,
    val phase: TrainingPhase,
    val blockPurpose: CustomBlockPurpose,
    val split: CustomSplitIdentity,
    val weekday: Int,
    val dayLabel: String,
    val sessionPurpose: ComposerSessionPurpose,
    val slots: List<CustomWorkoutSlotSnapshot>,
    /** Full set+rest duration only if EVERY amplifier includes separately
     * declared active follow-up seconds; otherwise a known partial time. */
    val knownSetAndRestSeconds: Long,
    val declaredNonSetSeconds: Int?,
    /** Selected on an explicitly authorized single-workout time review.
     * Null means original authored Full; never changes the custom cycle. */
    val shortSessionLimitMinutes:Int?=null,
    /** Stable set keys omitted before issuance, not skipped/failed sets. */
    val shortSessionOmittedSetKeys:List<String> =emptyList()
) {
    val prescriptionEditedForTime:Boolean
        get()=shortSessionOmittedSetKeys.isNotEmpty()
    val isIssued: Boolean get() = false
    val mayRecordSets: Boolean get() = false
    val mayFinishWorkout: Boolean get() = false
}
enum class CustomWorkoutSnapshotRefusal {
    UNREVIEWED_ATHLETE, WRONG_ATHLETE, STALE_DRAFT,
    INVALID_WEEK, INVALID_DAY, UNVERIFIED_CATALOG,
    INVALID_CYCLE, UNAVAILABLE_EXERCISE, INCOMPATIBLE_VARIANT,
    MISSING_SLOT_OR_SET, DUPLICATE_SET_IDENTITY, EXCESSIVE_SESSION,
    COACHING_REVIEW_REQUIRED
}
sealed interface CustomWorkoutSnapshotResult {
    data class Preview(val snapshot: CustomWorkoutDaySnapshot):
        CustomWorkoutSnapshotResult
    data class Rejected(val reason: CustomWorkoutSnapshotRefusal):
        CustomWorkoutSnapshotResult
}

object CustomWorkoutSnapshotDomain {
    private const val MAX_TARGETS = 256

    /**
     * Validate the WHOLE authored cycle before previewing even one day.
     * Do not let a locally valid day bypass a bad override elsewhere.
     * Read-only projection may include amplifiers that still lack the extra
     * active-work duration necessary for signed coaching approval.
     */
    fun preview(
        cycle: CustomTrainingCycle,
        setup: AthleteSetupReview,
        catalog: List<CatalogMovement>,
        sourceRevision: Long,
        absoluteWeek: Int,
        weekday: Int
    ): CustomWorkoutSnapshotResult {
        val athlete=setup.reviewed?.takeIf {
            setup.status==AthleteSetupStatus.READY_FOR_PROGRAM_REVIEW
        } ?:return CustomWorkoutSnapshotResult.Rejected(
            CustomWorkoutSnapshotRefusal.UNREVIEWED_ATHLETE)
        if(cycle.athleteId!=athlete.athleteProfileId ||
            cycle.goal!=athlete.goal)
            return CustomWorkoutSnapshotResult.Rejected(
                CustomWorkoutSnapshotRefusal.WRONG_ATHLETE)
        if(cycle.revision !in 1..9_007_199_254_740_991L ||
            sourceRevision !in 1..9_007_199_254_740_991L)
            return CustomWorkoutSnapshotResult.Rejected(
                CustomWorkoutSnapshotRefusal.STALE_DRAFT)
        if(catalog.isEmpty() || catalog.size>CoachExerciseCatalogDomain.MAX_CATALOG_ENTRIES ||
            catalog.map {it.exerciseId}.distinct().size!=catalog.size ||
            catalog.map{it.familyId to it.variantId}.distinct().size!=catalog.size ||
            catalog.any { m ->
                m.creditStatus!=CatalogCreditStatus.AUTHOR_REVIEWED_PROVISIONAL ||
                m.creditReviewReference.isNullOrBlank() ||
                m.credits.isEmpty()
            })
            return CustomWorkoutSnapshotResult.Rejected(
                CustomWorkoutSnapshotRefusal.UNVERIFIED_CATALOG)
        val options=CustomExerciseAuthoringDomain.picker(setup,catalog)
        val review=CustomTrainingCycleDomain.review(
            cycle,options.map{it.availability})
        if(review.verifiedDraft==null ||
            review.status!=CustomCycleReviewStatus.READY_FOR_COACH_REVIEW)
            return CustomWorkoutSnapshotResult.Rejected(
                CustomWorkoutSnapshotRefusal.INVALID_CYCLE)
        val resolved=CustomTrainingCycleDomain.week(review,absoluteWeek)
            ?:return CustomWorkoutSnapshotResult.Rejected(
                CustomWorkoutSnapshotRefusal.INVALID_WEEK)
        val day=resolved.days.singleOrNull { it.weekday==weekday }
            ?:return CustomWorkoutSnapshotResult.Rejected(
                CustomWorkoutSnapshotRefusal.INVALID_DAY)
        if(weekday !in athlete.trainingWeekdays ||
            day.slots.isEmpty() || day.slots.size>32)
            return CustomWorkoutSnapshotResult.Rejected(
                CustomWorkoutSnapshotRefusal.INVALID_DAY)
        if(!athlete.allowSupersets && day.slots.any{it.supersetGroup!=null})
            return CustomWorkoutSnapshotResult.Rejected(
                CustomWorkoutSnapshotRefusal.INVALID_DAY)
        val movementById=catalog.associateBy{it.exerciseId}
        val availableById=options.associateBy{it.exerciseId}
        val slots=mutableListOf<CustomWorkoutSlotSnapshot>()
        val seenKeys=mutableSetOf<String>()
        var knownSeconds=0L
        for(slot in day.slots) {
            val m=movementById[slot.exerciseId]
                ?:return CustomWorkoutSnapshotResult.Rejected(
                    CustomWorkoutSnapshotRefusal.UNAVAILABLE_EXERCISE)
            val option=availableById[slot.exerciseId]
                ?:return CustomWorkoutSnapshotResult.Rejected(
                    CustomWorkoutSnapshotRefusal.UNAVAILABLE_EXERCISE)
            if(slot.role!=m.role ||
                slot.exerciseId in athlete.excludedExerciseIds ||
                slot.attachmentId!=m.attachmentId ||
                option.attachmentId!=m.attachmentId ||
                (m.equipmentId==null && !athlete.allowBodyweight))
                return CustomWorkoutSnapshotResult.Rejected(
                    CustomWorkoutSnapshotRefusal.INCOMPATIBLE_VARIANT)
            val sets=slot.sets.mapIndexed{index,set->
                // Stable in this immutable prescription. Duplicate movement
                // IDs are fine: slot identity, not exercise ID, defines a set.
                val key="${cycle.cycleId}:w${absoluteWeek}:d${weekday}:${slot.slotId}:s${index+1}"
                if(!seenKeys.add(key))
                    return CustomWorkoutSnapshotResult.Rejected(
                        CustomWorkoutSnapshotRefusal.DUPLICATE_SET_IDENTITY)
                knownSeconds+=set.activeSeconds.toLong()+set.restSeconds+
                    (set.amplifier?.followUpRestSeconds ?:0)+
                    (set.amplifier?.followUpActiveSeconds ?:0)
                CustomWorkoutSetSnapshot(key,slot.slotId,index+1,
                    set.copy(amplifier=set.amplifier?.copy()))
            }
            if(sets.isEmpty())
                return CustomWorkoutSnapshotResult.Rejected(
                    CustomWorkoutSnapshotRefusal.MISSING_SLOT_OR_SET)
            slots+=CustomWorkoutSlotSnapshot(
                slot.slotId,slot.exerciseId,m.familyId,m.variantId,
                slot.role,slot.attachmentId,slot.supersetGroup,slot.pinned,
                slot.coachNote,Collections.unmodifiableList(sets),
                slot.machineInstanceId)
        }
        if(slots.sumOf{it.sets.size}>MAX_TARGETS)
            return CustomWorkoutSnapshotResult.Rejected(
                CustomWorkoutSnapshotRefusal.EXCESSIVE_SESSION)
        return CustomWorkoutSnapshotResult.Preview(CustomWorkoutDaySnapshot(
            CustomWorkoutSourceStamp(
                athlete.athleteProfileId,athlete.profileRevision,
                cycle.cycleId,cycle.revision,athlete.gym.gymId,
                sourceRevision
            ),
            resolved.absoluteWeek,resolved.weekInBlock,resolved.blockId,
            resolved.phase,resolved.purpose,resolved.split,weekday,
            day.label,day.purpose,Collections.unmodifiableList(slots),
            knownSeconds,null
        ))
    }

    /**
     * Preview the FIRST valid workout after a prospective block has become
     * the active athlete schedule. Previously completed blocks retain the
     * original reviewed setup in the full-cycle dose/continuity audit.
     *
     * This deliberately has NO issuance authority. A separate durable,
     * revision-bound activated-cycle consent must still precede preparation
     * or issuance, and may only authorize weeks in the activated block.
     */
    fun previewActivatedBlock(
        cycle:CustomTrainingCycle,
        originalSetup:AthleteSetupReview,
        activeSetup:AthleteSetupReview,
        catalog:List<CatalogMovement>,
        sourceRevision:Long,
        inputs:CustomCoachAuditInputs,
        activatedContext:ProspectiveBlockCoachingContext,
        targetBlockId:String,
        firstWeek:Int,
        absoluteWeek:Int,
        weekday:Int
    ):CustomWorkoutSnapshotResult {
        if(activeSetup!=activatedContext.setup ||
            firstWeek !in 2..104 || absoluteWeek !in firstWeek..104)
            return CustomWorkoutSnapshotResult.Rejected(
                CustomWorkoutSnapshotRefusal.COACHING_REVIEW_REQUIRED)
        val index=cycle.blocks.indexOfFirst {it.blockId==targetBlockId}
        if(index<=0 || cycle.blocks.take(index).sumOf {it.weeks}+1!=firstWeek)
            return CustomWorkoutSnapshotResult.Rejected(
                CustomWorkoutSnapshotRefusal.COACHING_REVIEW_REQUIRED)
        val target=cycle.blocks[index]
        val last=firstWeek+target.weeks-1
        if(absoluteWeek>last || target.purpose!=CustomBlockPurpose.TRAINING)
            return CustomWorkoutSnapshotResult.Rejected(
                CustomWorkoutSnapshotRefusal.INVALID_WEEK)
        val audit=CustomCycleCoachAuditDomain.review(
            cycle,originalSetup,catalog,inputs,
            mapOf(targetBlockId to activatedContext))
        if(audit.status!=CustomCoachAuditStatus.READY_FOR_ATHLETE_REVIEW ||
            audit.issues.isNotEmpty())
            return CustomWorkoutSnapshotResult.Rejected(
                CustomWorkoutSnapshotRefusal.COACHING_REVIEW_REQUIRED)
        val day=preview(cycle,activeSetup,catalog,sourceRevision,
            absoluteWeek,weekday)
        if(day !is CustomWorkoutSnapshotResult.Preview)return day
        if(day.snapshot.blockId!=targetBlockId)
            return CustomWorkoutSnapshotResult.Rejected(
                CustomWorkoutSnapshotRefusal.INVALID_WEEK)
        val overhead=activatedContext.nonSetSecondsByWeekday[weekday]
            ?:return CustomWorkoutSnapshotResult.Rejected(
                CustomWorkoutSnapshotRefusal.COACHING_REVIEW_REQUIRED)
        return day.copy(snapshot=day.snapshot.copy(
            declaredNonSetSeconds=overhead))
    }

    /**
     * Additional full-cycle coaching gate for a signoff-bound snapshot.
     * Native owner transaction still must verify the actual consent bytes
     * and all current source revisions; this alone cannot grant issuance.
     */
    fun reviewForConsent(
        cycle: CustomTrainingCycle,
        setup: AthleteSetupReview,
        catalog: List<CatalogMovement>,
        sourceRevision: Long,
        inputs: CustomCoachAuditInputs,
        absoluteWeek: Int,
        weekday: Int
    ): CustomWorkoutSnapshotResult {
        val audit=CustomCycleCoachAuditDomain.review(cycle,setup,catalog,inputs)
        if(audit.status!=CustomCoachAuditStatus.READY_FOR_ATHLETE_REVIEW ||
            audit.issues.isNotEmpty())
            return CustomWorkoutSnapshotResult.Rejected(
                CustomWorkoutSnapshotRefusal.COACHING_REVIEW_REQUIRED)
        val result=preview(cycle,setup,catalog,sourceRevision,
            absoluteWeek,weekday)
        if(result !is CustomWorkoutSnapshotResult.Preview)return result
        val overhead=inputs.nonSetSecondsByWeekday[weekday]
            ?:return CustomWorkoutSnapshotResult.Rejected(
                CustomWorkoutSnapshotRefusal.COACHING_REVIEW_REQUIRED)
        return result.copy(snapshot=result.snapshot.copy(
            declaredNonSetSeconds=overhead))
    }
}
