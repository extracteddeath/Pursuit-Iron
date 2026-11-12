package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import java.util.Collections

/**
 * Review-only, explicitly sourced exercise picker for FULLY custom cycles.
 * A text search, a displayed suggestion or a saved draft must never invent
 * gym stock, machine weight increments, facilities or available attachments.
 *
 * The athlete chooses movements and every set. This layer validates their
 * choices against a CoachAthleteSetupDomain-reviewed athlete + gym snapshot
 * and catalog entries with provisional human-reviewed muscle credits.
 * Production still needs a durable, authenticated gym/catalog revision owner
 * before this candidate editor may authorize training prescriptions.
 */
data class CustomExercisePickerOption(
    val exerciseId: String,
    val familyId: String,
    val variantId: String,
    val attachmentId: String?,
    val loadUnit: LoadUnit?,
    val loadBasis: LoadBasis?,
    val bodyweight: Boolean,
    val availability: CustomExerciseAvailability,
    /** Provisional author-reviewed catalog facts for transparent sorting. */
    val pattern: ComposerPattern? = null,
    val role: ComposerRole? = null,
    val muscleCredits: List<DeclaredMuscleCredit> = emptyList()
)

enum class CustomAuthoringRefusal {
    SETUP_UNRESOLVED, CATALOG_UNRESOLVED, EXERCISE_UNAVAILABLE,
    ATTACHMENT_UNAVAILABLE, EQUIPMENT_UNVERIFIED, INATTAINABLE_LOAD,
    INVALID_SETS, AUTHORING_CONFLICT
}
sealed interface CustomAuthoringResult {
    data class Accepted(val proposal: CustomCycleBuilderResult.Proposed):
        CustomAuthoringResult
    data class Rejected(val reason: CustomAuthoringRefusal):
        CustomAuthoringResult
}

object CustomExerciseAuthoringDomain {
    private val id = Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,79}$")
    private val max = BigDecimal("100000")
    private val zero = BigDecimal.ZERO

    /**
     * Return only movements actually eligible under the reviewed athlete's
     * current gym. A missing, duplicate or unreviewed catalog FAILS CLOSED.
     * This list is not a prescription, a training approval, or a source of
     * unverified increments.
     */
    fun picker(
        setup: AthleteSetupReview,
        catalog: List<CatalogMovement>
    ): List<CustomExercisePickerOption> {
        val athlete = setup.reviewed?.takeIf {
            setup.status == AthleteSetupStatus.READY_FOR_PROGRAM_REVIEW
        } ?: return emptyList()
        if (catalog.isEmpty() || catalog.size > CoachExerciseCatalogDomain.MAX_CATALOG_ENTRIES ||
            catalog.map { it.exerciseId }.distinct().size != catalog.size ||
            catalog.map { it.familyId to it.variantId }.distinct().size !=
                catalog.size) return emptyList()

        val gym = athlete.gym
        val options = catalog.mapNotNull { move ->
            if (!id.matches(move.exerciseId) ||
                !id.matches(move.familyId) || !id.matches(move.variantId) ||
                move.exerciseId in athlete.excludedExerciseIds ||
                move.creditStatus != CatalogCreditStatus.AUTHOR_REVIEWED_PROVISIONAL ||
                move.creditReviewReference.isNullOrBlank() ||
                move.credits.isEmpty() ||
                move.credits.map { it.muscle }.distinct().size !=
                    move.credits.size ||
                move.credits.any { it.fractionalSet <= zero ||
                    it.fractionalSet > BigDecimal.ONE } ||
                !gym.availableFacilities.containsAll(move.requiredFacilityIds) ||
                move.minimumRecoveryGroupDayGap !in 1..6 ||
                (move.recoveryGroupId == null &&
                    move.minimumRecoveryGroupDayGap != 1))
                return@mapNotNull null

            if (move.attachmentId != null &&
                (move.attachmentId !in gym.availableAttachments ||
                    !id.matches(move.attachmentId)))
                return@mapNotNull null

            if (move.equipmentId == null) {
                if (!athlete.allowBodyweight || move.expectedLoadBasis != null)
                    return@mapNotNull null
                val availability = CustomExerciseAvailability(
                    move.exerciseId, null, bodyweightApproved = true,
                    attachmentIds = move.attachmentId?.let { setOf(it) } ?: emptySet())
                CustomExercisePickerOption(move.exerciseId, move.familyId,
                    move.variantId, move.attachmentId, null, null, true,
                    availability,move.pattern,move.role,move.credits.toList())
            } else {
                if (!id.matches(move.equipmentId)) return@mapNotNull null
                val stock = gym.confirmedStock[move.equipmentId]
                    ?: return@mapNotNull null
                if (stock.unit != gym.unit ||
                    stock.basis != move.expectedLoadBasis ||
                    (stock.basis == LoadBasis.BARBELL_TOTAL &&
                        athlete.maxBarbellExercisesPerDay == 0))
                    return@mapNotNull null
                // Reject malformed stock; never turn an inventory exception
                // into a load recommendation.
                val initial = when(stock) {
                    is VerifiedLoadStock.Totals -> stock.loads.firstOrNull()
                    is VerifiedLoadStock.Barbell -> stock.barWeight
                    is VerifiedLoadStock.Stack -> stock.minimum
                } ?: return@mapNotNull null
                val inspection = EquipmentLoadDomain.inspect(
                    stock, initial, gym.unit)
                if (inspection !is LoadInspection.Confirmed ||
                    !inspection.exact || inspection.basis != stock.basis)
                    return@mapNotNull null
                val availability = CustomExerciseAvailability(
                    move.exerciseId, stock, bodyweightApproved = false,
                    attachmentIds = move.attachmentId?.let { setOf(it) } ?: emptySet())
                CustomExercisePickerOption(move.exerciseId, move.familyId,
                    move.variantId, move.attachmentId, gym.unit,
                    stock.basis, false, availability,
                    move.pattern,move.role,move.credits.toList())
            }
        }
        return Collections.unmodifiableList(options)
    }

    private fun validSet(
        set: CustomAuthoredSet,
        selection: CustomExercisePickerOption
    ): CustomAuthoringRefusal? {
        if (set.reps.minimum !in 1..100 ||
            set.reps.maximum !in set.reps.minimum..100 ||
            (set.targetRir != null && set.targetRir !in 0..10) ||
            set.activeSeconds !in 1..3600 ||
            set.restSeconds !in 0..3600 ||
            (set.type == DraftSetType.WARMUP && set.amplifier != null))
            return CustomAuthoringRefusal.INVALID_SETS

        fun validLoad(load: DraftSetLoad): Boolean = when (load) {
            DraftSetLoad.ExplicitBodyweight -> selection.bodyweight
            is DraftSetLoad.Verified -> {
                val stock = selection.availability.stock
                if (stock == null || load.value <= zero || load.value > max ||
                    load.value.stripTrailingZeros().scale() > 3 ||
                    load.unit != stock.unit || load.basis != stock.basis) false
                else {
                    val checked = EquipmentLoadDomain.inspect(
                        stock, load.value, load.unit)
                    checked is LoadInspection.Confirmed && checked.exact &&
                        checked.basis == load.basis
                }
            }
        }
        if (!validLoad(set.load))
            return CustomAuthoringRefusal.INATTAINABLE_LOAD

        val amp = set.amplifier
        if (amp != null) {
            if (amp.followUpReps !in 1..100 ||
                amp.followUpRestSeconds !in 0..3600)
                return CustomAuthoringRefusal.INVALID_SETS
            if (amp.kind == CustomAmplifierKind.DROP_SET) {
                val base = set.load as? DraftSetLoad.Verified
                val reduced = amp.followUpLoad as? DraftSetLoad.Verified
                if (base == null || reduced == null ||
                    base.unit != reduced.unit || base.basis != reduced.basis ||
                    reduced.value >= base.value)
                    return CustomAuthoringRefusal.INVALID_SETS
            }
            if (amp.followUpLoad != null && !validLoad(amp.followUpLoad))
                return CustomAuthoringRefusal.INATTAINABLE_LOAD
        }
        return null
    }

    /**
     * Read-only validation shared by program authoring and a proposed
     * in-session swap. This checks CURRENT reviewed gym stock, variants,
     * exact physical load, rep range and all advanced-set follow-ups.
     * A successful review is NOT authority to edit an issued workout.
     */
    fun reviewFreshSets(
        setup:AthleteSetupReview,
        catalog:List<CatalogMovement>,
        exerciseId:String,
        sets:List<CustomAuthoredSet>
    ):CustomAuthoringRefusal? {
        if(setup.status!=AthleteSetupStatus.READY_FOR_PROGRAM_REVIEW ||
            setup.reviewed==null)
            return CustomAuthoringRefusal.SETUP_UNRESOLVED
        val option=picker(setup,catalog).firstOrNull{
            it.exerciseId==exerciseId
        } ?:return CustomAuthoringRefusal.EXERCISE_UNAVAILABLE
        if(sets.isEmpty() || sets.size>20)
            return CustomAuthoringRefusal.INVALID_SETS
        return sets.firstNotNullOfOrNull{validSet(it,option)}
    }

    /**
     * Re-evaluates the CATALOG + GYM every time. Caller-provided option data
     * can never authorize an unavailable variant or stock. No generated sets:
     * all reps, load, RIR, rest and amplifiers come from the athlete.
     */
    fun addSelected(
        current: CustomTrainingCycle,
        expectedRevision: Long,
        blockId: String,
        weekday: Int,
        slot: CustomExerciseSlot,
        setup: AthleteSetupReview,
        catalog: List<CatalogMovement>
    ): CustomAuthoringResult {
        val athlete = setup.reviewed?.takeIf {
            setup.status == AthleteSetupStatus.READY_FOR_PROGRAM_REVIEW &&
                it.athleteProfileId == current.athleteId
        } ?: return CustomAuthoringResult.Rejected(
            CustomAuthoringRefusal.SETUP_UNRESOLVED)
        val option = picker(setup, catalog).firstOrNull {
            it.exerciseId == slot.exerciseId
        } ?: return CustomAuthoringResult.Rejected(
            CustomAuthoringRefusal.EXERCISE_UNAVAILABLE)
        if (slot.attachmentId != option.attachmentId)
            return CustomAuthoringResult.Rejected(
                CustomAuthoringRefusal.ATTACHMENT_UNAVAILABLE)
        if (!athlete.allowSupersets && slot.supersetGroup != null)
            return CustomAuthoringResult.Rejected(
                CustomAuthoringRefusal.AUTHORING_CONFLICT)
        if (slot.sets.isEmpty() || slot.sets.size > 20)
            return CustomAuthoringResult.Rejected(
                CustomAuthoringRefusal.INVALID_SETS)
        val error = slot.sets.firstNotNullOfOrNull { validSet(it, option) }
        if (error != null) return CustomAuthoringResult.Rejected(error)
        return when (val changed = CustomCycleBuilderDomain.addSlot(
            current, expectedRevision, blockId, weekday, slot)) {
            is CustomCycleBuilderResult.Proposed ->
                CustomAuthoringResult.Accepted(changed)
            is CustomCycleBuilderResult.Rejected ->
                CustomAuthoringResult.Rejected(
                    CustomAuthoringRefusal.AUTHORING_CONFLICT)
        }
    }

    /**
     * Athlete-confirmed, program-draft-only swap. The replacement carries
     * freshly authored sets and a new slot ID; never migrate numeric loads,
     * historical identity, advanced techniques or week overrides. A pinned
     * source is replaceable ONLY through the explicit confirmation UI.
     *
     * Source setup, physical stock, attachments and every new set are checked
     * again immediately before the owner/revision-fenced program write.
     */
    fun replaceSelectedExercise(
        current:CustomTrainingCycle,
        expectedRevision:Long,
        blockId:String,
        weekday:Int,
        previousSlotId:String,
        replacement:CustomExerciseSlot,
        setup:AthleteSetupReview,
        catalog:List<CatalogMovement>
    ):CustomAuthoringResult {
        val athlete=setup.reviewed?.takeIf {
            setup.status==AthleteSetupStatus.READY_FOR_PROGRAM_REVIEW &&
                it.athleteProfileId==current.athleteId
        } ?:return CustomAuthoringResult.Rejected(
            CustomAuthoringRefusal.SETUP_UNRESOLVED)
        val previous=current.blocks.firstOrNull {it.blockId==blockId}
            ?.days?.firstOrNull {it.weekday==weekday}
            ?.slots?.firstOrNull {it.slotId==previousSlotId}
            ?:return CustomAuthoringResult.Rejected(
                CustomAuthoringRefusal.AUTHORING_CONFLICT)
        if(previous.exerciseId==replacement.exerciseId ||
            previous.slotId==replacement.slotId ||
            previous.supersetGroup!=null || replacement.supersetGroup!=null ||
            current.weekOverrides[blockId].orEmpty().any {
                it.weekday==weekday && it.slotId==previousSlotId
            } || replacement.sets.isEmpty() ||
            replacement.sets.size>20 || !replacement.pinned ||
            replacement.coachNote.length>1000)
            return CustomAuthoringResult.Rejected(
                CustomAuthoringRefusal.AUTHORING_CONFLICT)
        val option=picker(setup,catalog).firstOrNull {
            it.exerciseId==replacement.exerciseId
        } ?:return CustomAuthoringResult.Rejected(
            CustomAuthoringRefusal.EXERCISE_UNAVAILABLE)
        val catalogRole=catalog.firstOrNull {
            it.exerciseId==replacement.exerciseId
        }?.role
        if(replacement.role!=catalogRole)
            return CustomAuthoringResult.Rejected(
                CustomAuthoringRefusal.AUTHORING_CONFLICT)
        if(replacement.attachmentId!=option.attachmentId)
            return CustomAuthoringResult.Rejected(
                CustomAuthoringRefusal.ATTACHMENT_UNAVAILABLE)
        if(!athlete.allowSupersets && replacement.supersetGroup!=null)
            return CustomAuthoringResult.Rejected(
                CustomAuthoringRefusal.AUTHORING_CONFLICT)
        val invalid=replacement.sets.firstNotNullOfOrNull {
            validSet(it,option)
        }
        if(invalid!=null)return CustomAuthoringResult.Rejected(invalid)
        return when(val proposed=CustomCycleBuilderDomain.replaceExerciseSlot(
            current,expectedRevision,blockId,weekday,previousSlotId,
            replacement)) {
            is CustomCycleBuilderResult.Proposed ->
                CustomAuthoringResult.Accepted(proposed)
            is CustomCycleBuilderResult.Rejected ->
                CustomAuthoringResult.Rejected(
                    CustomAuthoringRefusal.AUTHORING_CONFLICT)
        }
    }

    /**
     * Edit only the chosen slot. Never replace the same exercise on another
     * day or reset an explicit week override. The gym is checked AGAIN when
     * changing individual loads or advanced-set techniques. A supplied
     * week selects only that override; the block's base sets stay intact.
     */
    fun replaceSelectedSets(
        current: CustomTrainingCycle,
        expectedRevision: Long,
        blockId: String,
        weekday: Int,
        slotId: String,
        newSets: List<CustomAuthoredSet>,
        setup: AthleteSetupReview,
        catalog: List<CatalogMovement>,
        weekInBlock: Int? = null
    ): CustomAuthoringResult {
        val athlete = setup.reviewed?.takeIf {
            setup.status == AthleteSetupStatus.READY_FOR_PROGRAM_REVIEW &&
                it.athleteProfileId == current.athleteId
        } ?: return CustomAuthoringResult.Rejected(
            CustomAuthoringRefusal.SETUP_UNRESOLVED)
        val block = current.blocks.firstOrNull { it.blockId == blockId }
        val slot = block?.days?.firstOrNull { it.weekday == weekday }
            ?.slots?.firstOrNull { it.slotId == slotId }
            ?: return CustomAuthoringResult.Rejected(
                CustomAuthoringRefusal.AUTHORING_CONFLICT)
        val option = picker(setup, catalog).firstOrNull {
            it.exerciseId == slot.exerciseId
        } ?: return CustomAuthoringResult.Rejected(
            CustomAuthoringRefusal.EXERCISE_UNAVAILABLE)
        if (slot.attachmentId != option.attachmentId)
            return CustomAuthoringResult.Rejected(
                CustomAuthoringRefusal.ATTACHMENT_UNAVAILABLE)
        if (!athlete.allowSupersets && slot.supersetGroup != null)
            return CustomAuthoringResult.Rejected(
                CustomAuthoringRefusal.AUTHORING_CONFLICT)
        if (newSets.isEmpty() || newSets.size > 20)
            return CustomAuthoringResult.Rejected(
                CustomAuthoringRefusal.INVALID_SETS)
        val invalid = newSets.firstNotNullOfOrNull { validSet(it, option) }
        if (invalid != null) return CustomAuthoringResult.Rejected(invalid)
        val proposal = if (weekInBlock == null)
            CustomCycleBuilderDomain.replaceSlotSets(
                current, expectedRevision, blockId, weekday, slotId, newSets)
        else CustomCycleBuilderDomain.editWeek(
            current, expectedRevision, blockId, weekInBlock, weekday, slotId, newSets)
        return when (val changed = proposal) {
            is CustomCycleBuilderResult.Proposed ->
                CustomAuthoringResult.Accepted(changed)
            is CustomCycleBuilderResult.Rejected ->
                CustomAuthoringResult.Rejected(
                    CustomAuthoringRefusal.AUTHORING_CONFLICT)
        }
    }
}
