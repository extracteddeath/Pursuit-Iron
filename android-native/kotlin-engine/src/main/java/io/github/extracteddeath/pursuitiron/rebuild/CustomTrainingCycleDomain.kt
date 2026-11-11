package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import java.util.Collections

/**
 * Authoritative *draft structure* for a truly athlete-authored training cycle.
 * Unlike the general week composer, this never searches for different
 * exercises, redistributes sets, rotates movements or changes RIR.
 *
 * The full cycle is authored in explicit blocks and optional per-week edits:
 * no hidden hypertrophy -> strength reduction, no dropped custom amplifiers,
 * no automatic next-phase rewrite. Issuance and persistence require separate
 * identity, catalog, volume, equipment and account approvals.
 */
sealed interface CustomSplitIdentity {
    data class Named(val split: ComposerSplit) : CustomSplitIdentity
    /** e.g. Upper A / Chest + Arms / Powerbuilding; no preset restrictions. */
    data class Freeform(val label: String) : CustomSplitIdentity
}

enum class CustomBlockPurpose { TRAINING, RECOVERY, PERFORMANCE_TEST }
enum class CustomAmplifierKind { DROP_SET, MYO_REPS, REST_PAUSE, CLUSTER }

data class CustomSetAmplifier(
    val kind: CustomAmplifierKind,
    /** Follow-up reps are prescribed, NEVER observed as completed reps. */
    val followUpReps: Int,
    val followUpRestSeconds: Int,
    /** An extra physical weight must be independently attainable. */
    val followUpLoad: DraftSetLoad? = null,
    /** Separately athlete-authored ACTIVE seconds for the complete
     * follow-up protocol, not its rest. Null is unknown, not zero. */
    val followUpActiveSeconds: Int? = null
)

data class CustomAuthoredSet(
    val type: DraftSetType,
    val load: DraftSetLoad,
    val reps: RepBand,
    val targetRir: Int?,
    val activeSeconds: Int,
    val restSeconds: Int,
    val amplifier: CustomSetAmplifier? = null
)

data class CustomExerciseSlot(
    /** Stable athlete-authored identity; not an array index. */
    val slotId: String,
    val exerciseId: String,
    val role: ComposerRole,
    val sets: List<CustomAuthoredSet>,
    val attachmentId: String? = null,
    val supersetGroup: String? = null,
    /** May be pinned independently of its exercise type. */
    val pinned: Boolean = true,
    val coachNote: String = "",
    /** Optional physical station bound to THIS authored slot (Machine A/B).
     * Null preserves legacy per-gym workout defaults. This is not a generic
     * exercise weight conversion or an already-issued workout override. */
    val machineInstanceId: String? = null
)

data class CustomTrainingDay(
    val weekday: Int,
    val label: String,
    val purpose: ComposerSessionPurpose,
    /** All visits are in athlete-selected order, not composer order. */
    val slots: List<CustomExerciseSlot>
)

data class CustomTrainingBlock(
    val blockId: String,
    val label: String,
    val phase: TrainingPhase,
    val purpose: CustomBlockPurpose,
    val weeks: Int,
    val split: CustomSplitIdentity,
    val days: List<CustomTrainingDay>
)

/** A week-level replacement of ONE slot, preserving other week prescriptions. */
data class CustomWeekSlotOverride(
    /** 1-based week offset within a block. */
    val weekInBlock: Int,
    val weekday: Int,
    val slotId: String,
    val sets: List<CustomAuthoredSet>
)

data class CustomTrainingCycle(
    val cycleId: String,
    val revision: Long,
    val athleteId: String,
    val goal: AthleteTrainingGoal,
    val title: String,
    val blocks: List<CustomTrainingBlock>,
    val weekOverrides: Map<String, List<CustomWeekSlotOverride>> = emptyMap(),
    /** Named author intent persists through block changes and custom edits. */
    val authorNote: String = ""
)

data class CustomExerciseAvailability(
    val exerciseId: String,
    val stock: VerifiedLoadStock?,
    val bodyweightApproved: Boolean,
    val attachmentIds: Set<String> = emptySet()
)

enum class CustomCycleIssueKind {
    INVALID_ID, INVALID_REVISION, INVALID_TITLE, INVALID_CYCLE_LENGTH,
    INVALID_DAY, DUPLICATE_DAY, EMPTY_SESSION, DUPLICATE_SLOT,
    INVALID_SLOT, INVALID_SET, UNAVAILABLE_EXERCISE, UNAVAILABLE_ATTACHMENT,
    NO_VERIFIED_STOCK, INATTAINABLE_LOAD, INVALID_SUPERSET,
    INVALID_WEEK_OVERRIDE, EXCESSIVE_WORK
}

data class CustomCycleIssue(
    val kind: CustomCycleIssueKind,
    val location: String,
    val explanation: String
)

enum class CustomCycleReviewStatus { UNRESOLVED, READY_FOR_COACH_REVIEW }

data class CustomCycleReview(
    val status: CustomCycleReviewStatus,
    val issues: List<CustomCycleIssue>,
    val totalWeeks: Int,
    /** A complete, immutable copy is only returned for a valid blueprint. */
    val verifiedDraft: CustomTrainingCycle?
) {
    val canIssueWorkout: Boolean get() = false
    val canPublishProgram: Boolean get() = false
}

data class CustomResolvedWeek(
    val cycleId: String,
    val cycleRevision: Long,
    val absoluteWeek: Int,
    val blockId: String,
    val weekInBlock: Int,
    val phase: TrainingPhase,
    val purpose: CustomBlockPurpose,
    val split: CustomSplitIdentity,
    val days: List<CustomTrainingDay>
) {
    val canIssueWorkout: Boolean get() = false
}

/**
 * A deliberately conservative snapshot. Review cannot trust a caller
 * saying "already approved" or "equipment available"; exact verified stock
 * is rechecked for EVERY set, amplifier and weekly override.
 */
object CustomTrainingCycleDomain {
    private val id = Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,79}$")
    private val zero = BigDecimal.ZERO
    private val max = BigDecimal("100000")
    private const val MAX_WEEKS = 104
    private const val MAX_BLOCKS = 26
    private const val MAX_SETS_PER_SLOT = 20
    private const val MAX_SLOTS_PER_DAY = 32
    private const val MAX_OVERRIDE_COUNT = 3000

    private fun loadIsAttainable(
        load: DraftSetLoad,
        available: CustomExerciseAvailability
    ): CustomCycleIssueKind? = when (load) {
        DraftSetLoad.ExplicitBodyweight ->
            if (available.bodyweightApproved) null
            else CustomCycleIssueKind.UNAVAILABLE_EXERCISE
        is DraftSetLoad.Verified -> {
            if (load.value <= zero || load.value > max ||
                load.value.stripTrailingZeros().scale() > 3)
                CustomCycleIssueKind.INVALID_SET
            else if (available.stock == null)
                CustomCycleIssueKind.NO_VERIFIED_STOCK
            else {
                val reviewed = EquipmentLoadDomain.inspect(
                    available.stock, load.value, load.unit)
                if (reviewed !is LoadInspection.Confirmed ||
                    reviewed.basis != load.basis || !reviewed.exact)
                    CustomCycleIssueKind.INATTAINABLE_LOAD
                else null
            }
        }
    }

    private fun frozen(set: CustomAuthoredSet): CustomAuthoredSet = set.copy(
        amplifier = set.amplifier?.copy()
    )

    private fun frozenSlot(slot: CustomExerciseSlot) = slot.copy(
        sets = Collections.unmodifiableList(slot.sets.map(::frozen))
    )

    private fun frozenDay(day: CustomTrainingDay) = day.copy(
        slots = Collections.unmodifiableList(day.slots.map(::frozenSlot))
    )

    private fun frozenBlock(block: CustomTrainingBlock) = block.copy(
        days = Collections.unmodifiableList(block.days.map(::frozenDay))
    )

    private fun freeze(input: CustomTrainingCycle): CustomTrainingCycle =
        input.copy(
            blocks = Collections.unmodifiableList(input.blocks.map(::frozenBlock)),
            weekOverrides = Collections.unmodifiableMap(
                input.weekOverrides.mapValues { (_, edits) ->
                    Collections.unmodifiableList(edits.map { entry ->
                        entry.copy(sets =
                            Collections.unmodifiableList(entry.sets.map(::frozen)))
                    })
                }),
        )

    fun review(
        input: CustomTrainingCycle,
        availableExercises: List<CustomExerciseAvailability>
    ): CustomCycleReview {
        val issues = mutableListOf<CustomCycleIssue>()
        fun issue(kind: CustomCycleIssueKind, at: String, why: String) {
            if (issues.size < 256) issues += CustomCycleIssue(kind, at, why)
        }
        if (!id.matches(input.cycleId) || !id.matches(input.athleteId) ||
            input.blocks.any { !id.matches(it.blockId) })
            issue(CustomCycleIssueKind.INVALID_ID, "cycle",
                "The cycle, athlete and block IDs must have stable valid values.")
        if (input.revision !in 1..9_007_199_254_740_991L)
            issue(CustomCycleIssueKind.INVALID_REVISION, "revision",
                "The author revision must be a positive finite integer.")
        if (input.title.isBlank() || input.title.length > 120 ||
            input.authorNote.length > 2000 ||
            input.blocks.any { it.label.isBlank() || it.label.length > 120 ||
                (it.split is CustomSplitIdentity.Freeform &&
                    (it.split.label.isBlank() || it.split.label.length > 80)) })
            issue(CustomCycleIssueKind.INVALID_TITLE, "cycle.title",
                "Cycle, block and custom split labels must be concise.")
        val total = input.blocks.sumOf { it.weeks }
        if (input.blocks.isEmpty() || input.blocks.size > MAX_BLOCKS ||
            total !in 1..MAX_WEEKS || input.blocks.any { it.weeks !in 1..52 } ||
            input.blocks.map { it.blockId }.distinct().size != input.blocks.size)
            issue(CustomCycleIssueKind.INVALID_CYCLE_LENGTH, "blocks",
                "A cycle needs 1–26 uniquely identified blocks and at most 104 scheduled weeks.")
        if (availableExercises.size > 2048 ||
            availableExercises.map { it.exerciseId }.distinct().size !=
                availableExercises.size)
            issue(CustomCycleIssueKind.UNAVAILABLE_EXERCISE, "gym",
                "The available equipment lookup must use unique exercise IDs.")
        val inventory = availableExercises.associateBy { it.exerciseId }
        var totalOverrides = 0
        fun verifySets(
            at: String,
            slot: CustomExerciseSlot,
            sets: List<CustomAuthoredSet>
        ) {
            if (sets.isEmpty() || sets.size > MAX_SETS_PER_SLOT) {
                issue(CustomCycleIssueKind.INVALID_SET, at,
                    "Every exercise needs 1–20 explicitly authored sets.")
                return
            }
            val availability = inventory[slot.exerciseId]
            if (availability == null)
                issue(CustomCycleIssueKind.UNAVAILABLE_EXERCISE, at,
                    "The selected exercise is absent from the confirmed gym.")
            for ((setIndex, set) in sets.withIndex()) {
                val here = "$at.set.$setIndex"
                if (set.activeSeconds !in 1..3600 ||
                    set.restSeconds !in 0..3600 ||
                    (set.targetRir != null && set.targetRir !in 0..10) ||
                    set.reps.minimum !in 1..100 ||
                    set.reps.maximum !in set.reps.minimum..100 ||
                    (set.type == DraftSetType.WARMUP && set.amplifier != null))
                    issue(CustomCycleIssueKind.INVALID_SET, here,
                        "Invalid reps, effort, timing or warmup amplifier.")
                val amplifier = set.amplifier
                if (amplifier != null) {
                    if (amplifier.followUpReps !in 1..100 ||
                        amplifier.followUpRestSeconds !in 0..3600 ||
                        amplifier.followUpActiveSeconds?.let{
                            it !in 1..3600
                        }==true)
                        issue(CustomCycleIssueKind.INVALID_SET, here,
                            "Amplifier reps and intra-set rest require explicit valid values.")
                    // DROP_SET requires a separately authored lower follow-up
                    // load, not a pretend completion at the original weight.
                    if (amplifier.kind == CustomAmplifierKind.DROP_SET) {
                        val before = set.load as? DraftSetLoad.Verified
                        val after = amplifier.followUpLoad as? DraftSetLoad.Verified
                        if (before == null || after == null ||
                            before.unit != after.unit || before.basis != after.basis ||
                            after.value >= before.value)
                            issue(CustomCycleIssueKind.INVALID_SET, here,
                                "Drop sets require a lower load with matching unit and basis.")
                    }
                }
                if (availability != null) {
                    val error = loadIsAttainable(set.load, availability)
                    if (error != null)
                        issue(error, here, "The authored set load is not confirmed attainable.")
                    val extraLoad = amplifier?.followUpLoad
                    if (extraLoad != null) {
                        val extraError = loadIsAttainable(extraLoad, availability)
                        if (extraError != null)
                            issue(extraError, "$here.amplifier",
                                "The amplifier follow-up load is not verified.")
                    }
                }
            }
        }
        for ((blockIndex, block) in input.blocks.withIndex()) {
            val prefix = "block.$blockIndex"
            if (block.days.isEmpty() || block.days.size > 7)
                issue(CustomCycleIssueKind.INVALID_DAY, prefix,
                    "A custom block needs 1–7 authored training days.")
            if (block.days.map { it.weekday }.distinct().size != block.days.size)
                issue(CustomCycleIssueKind.DUPLICATE_DAY, prefix,
                    "A weekday cannot occur twice in one block.")
            for ((dayIndex, day) in block.days.withIndex()) {
                val at = "$prefix.day.$dayIndex"
                if (day.weekday !in 0..6 || day.label.isBlank() ||
                    day.label.length > 80 ||
                    day.purpose == ComposerSessionPurpose.UNSPECIFIED)
                    issue(CustomCycleIssueKind.INVALID_DAY, at,
                        "Choose a real weekday, label and session purpose.")
                if (day.slots.isEmpty() || day.slots.size > MAX_SLOTS_PER_DAY)
                    issue(CustomCycleIssueKind.EMPTY_SESSION, at,
                        "Custom sessions need 1–32 explicit exercises.")
                if (day.slots.map { it.slotId }.distinct().size != day.slots.size)
                    issue(CustomCycleIssueKind.DUPLICATE_SLOT, at,
                        "Each exercise slot on this day needs a unique stable ID.")
                for ((slotIndex, slot) in day.slots.withIndex()) {
                    val location = "$at.slot.$slotIndex"
                    if (!id.matches(slot.slotId) ||
                        !id.matches(slot.exerciseId) ||
                        slot.coachNote.length > 1000 ||
                        (slot.attachmentId != null && !id.matches(slot.attachmentId)) ||
                        (slot.supersetGroup != null && !id.matches(slot.supersetGroup)) ||
                        (slot.machineInstanceId != null &&
                            !KotlinMachineAlternativesDomain.validInstanceId(
                                slot.machineInstanceId)))
                        issue(CustomCycleIssueKind.INVALID_SLOT, location,
                            "Slots need valid IDs and bounded authored notes.")
                    if (slot.machineInstanceId != null &&
                        slot.sets.none { set ->
                            val basis=(set.load as? DraftSetLoad.Verified)?.basis
                            ReviewedFutureWeekProgressionDomain
                                .requiresPhysicalMachineIdentity(basis)
                        })
                        issue(CustomCycleIssueKind.INVALID_SLOT, location,
                            "A physical Machine A/B slot needs a machine/cable/Smith load basis.")
                    if (slot.attachmentId != null &&
                        slot.attachmentId !in
                            (inventory[slot.exerciseId]?.attachmentIds ?: emptySet()))
                        issue(CustomCycleIssueKind.UNAVAILABLE_ATTACHMENT, location,
                            "This attachment is not verified for the chosen exercise.")
                    verifySets(location, slot, slot.sets)
                }
                for ((group, members) in day.slots.filter {
                    it.supersetGroup != null
                }.groupBy { it.supersetGroup }) {
                    if (members.size !in 2..3)
                        issue(CustomCycleIssueKind.INVALID_SUPERSET, at,
                            "Superset group $group must contain 2–3 exercise slots.")
                }
            }
            val overrides = input.weekOverrides[block.blockId].orEmpty()
            totalOverrides += overrides.size
            val touched = mutableSetOf<Triple<Int, Int, String>>()
            for ((i, override) in overrides.withIndex()) {
                val at = "$prefix.override.$i"
                val slot = block.days.firstOrNull {
                    it.weekday == override.weekday
                }?.slots?.firstOrNull { it.slotId == override.slotId }
                if (override.weekInBlock !in 1..block.weeks || slot == null ||
                    !touched.add(Triple(override.weekInBlock,
                        override.weekday, override.slotId))) {
                    issue(CustomCycleIssueKind.INVALID_WEEK_OVERRIDE, at,
                        "Override must name one existing slot, once per block week.")
                    continue
                }
                verifySets(at, slot, override.sets)
            }
        }
        if (input.weekOverrides.keys.any { key ->
                input.blocks.none { it.blockId == key }
            } || totalOverrides > MAX_OVERRIDE_COUNT)
            issue(CustomCycleIssueKind.INVALID_WEEK_OVERRIDE, "weekOverrides",
                "Overrides must name existing blocks and remain within review bounds.")
        if (input.blocks.sumOf { block ->
                block.days.sumOf { day -> day.slots.sumOf { it.sets.size } }
            } > 1500)
            issue(CustomCycleIssueKind.EXCESSIVE_WORK, "blocks",
                "Too many authored sets for one bounded planning review.")
        val accepted = issues.isEmpty()
        return CustomCycleReview(
            if (accepted) CustomCycleReviewStatus.READY_FOR_COACH_REVIEW
            else CustomCycleReviewStatus.UNRESOLVED,
            Collections.unmodifiableList(issues.toList()),
            total, if (accepted) freeze(input) else null)
    }

    /**
     * Exact week projection. A custom weekly override changes ONLY its own
     * stable slot; original block templates and other weeks remain untouched.
     * The caller must first pass the FULL blueprint inventory review.
     */
    fun week(review: CustomCycleReview, absoluteWeek: Int): CustomResolvedWeek? {
        val cycle = review.verifiedDraft ?: return null
        if (review.status != CustomCycleReviewStatus.READY_FOR_COACH_REVIEW ||
            absoluteWeek !in 1..review.totalWeeks) return null
        var offset = absoluteWeek
        val block = cycle.blocks.firstOrNull {
            if (offset > it.weeks) { offset -= it.weeks; false } else true
        } ?: return null
        val selected = cycle.weekOverrides[block.blockId].orEmpty()
            .filter { it.weekInBlock == offset }
            .associateBy { it.weekday to it.slotId }
        val days = block.days.map { day ->
            day.copy(slots = Collections.unmodifiableList(day.slots.map { slot ->
                val sets = selected[day.weekday to slot.slotId]?.sets
                if (sets != null) slot.copy(sets = sets) else slot
            }))
        }
        return CustomResolvedWeek(
            cycle.cycleId, cycle.revision, absoluteWeek, block.blockId,
            offset, block.phase, block.purpose, block.split,
            Collections.unmodifiableList(days))
    }
}
