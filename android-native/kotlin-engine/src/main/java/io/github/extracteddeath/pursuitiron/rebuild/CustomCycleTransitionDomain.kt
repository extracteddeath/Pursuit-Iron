package io.github.extracteddeath.pursuitiron.rebuild

import java.util.Collections

/** Custom-program edits always produce a review, never a silent replacement. */
enum class CustomCycleEditOrigin { ATHLETE_EDIT, COACH_SUGGESTION }
enum class CustomCycleChangeKind {
    GOAL, BLOCKS, PHASE, SPLIT, SCHEDULE, EXERCISES, REMOVE_PINNED,
    ATTACHMENTS, SUPERSETS, SET_COUNT, LOADS, REPS, RIR, REST,
    AMPLIFIERS, CUES, WEEK_OVERRIDES, DAY_COUNT
}
data class CustomCycleChange(
    val kind: CustomCycleChangeKind,
    val location: String,
    val explanation: String
)
data class CustomCycleCoachPermissions(
    val adaptGoal: Boolean = false,
    val adaptBlocks: Boolean = false,
    val adaptPhase: Boolean = false,
    val adaptSplit: Boolean = false,
    val adaptSchedule: Boolean = false,
    val adaptExercises: Boolean = false,
    val removePinned: Boolean = false,
    val adaptAttachments: Boolean = false,
    val adaptSupersets: Boolean = false,
    val adaptSetCount: Boolean = false,
    val adaptLoads: Boolean = false,
    val adaptReps: Boolean = false,
    val adaptRir: Boolean = false,
    val adaptRest: Boolean = false,
    val adaptAmplifiers: Boolean = false,
    val adaptCues: Boolean = false,
    val adaptWeekOverrides: Boolean = false,
    /** Different from changing a split or moving existing training days. */
    val adaptDayCount: Boolean = false
) {
    fun permits(kind: CustomCycleChangeKind): Boolean = when(kind) {
        CustomCycleChangeKind.GOAL -> adaptGoal
        CustomCycleChangeKind.BLOCKS -> adaptBlocks
        CustomCycleChangeKind.PHASE -> adaptPhase
        CustomCycleChangeKind.SPLIT -> adaptSplit
        CustomCycleChangeKind.SCHEDULE -> adaptSchedule
        CustomCycleChangeKind.DAY_COUNT -> adaptDayCount
        CustomCycleChangeKind.EXERCISES -> adaptExercises
        CustomCycleChangeKind.REMOVE_PINNED -> removePinned
        CustomCycleChangeKind.ATTACHMENTS -> adaptAttachments
        CustomCycleChangeKind.SUPERSETS -> adaptSupersets
        CustomCycleChangeKind.SET_COUNT -> adaptSetCount
        CustomCycleChangeKind.LOADS -> adaptLoads
        CustomCycleChangeKind.REPS -> adaptReps
        CustomCycleChangeKind.RIR -> adaptRir
        CustomCycleChangeKind.REST -> adaptRest
        CustomCycleChangeKind.AMPLIFIERS -> adaptAmplifiers
        CustomCycleChangeKind.CUES -> adaptCues
        CustomCycleChangeKind.WEEK_OVERRIDES -> adaptWeekOverrides
    }
}
enum class CustomCycleTransitionStatus {
    INVALID_SOURCE_OR_CANDIDATE, STALE_REVISION, PERMISSION_REQUIRED,
    NO_CHANGE, READY_FOR_ATHLETE_REVIEW
}
data class CustomCycleTransitionReview(
    val status: CustomCycleTransitionStatus,
    val differences: List<CustomCycleChange>,
    val blockedKinds: Set<CustomCycleChangeKind>,
    val originalReview: CustomCycleReview,
    val proposedReview: CustomCycleReview
) {
    val mayApplyAutomatically: Boolean get() = false
    val mayModifyHistory: Boolean get() = false
    val mayIssueWorkout: Boolean get() = false
}

/**
 * Compares the whole authored cycle, not just a general composer exercise
 * catalog. A change of sets, RIR, rest, amplifier or week override is not
 * masked by a shared exercise ID or the same phase label.
 */
object CustomCycleTransitionDomain {
    private fun compareSets(
        old: List<CustomAuthoredSet>, next: List<CustomAuthoredSet>,
        at: String, emit: (CustomCycleChangeKind, String, String) -> Unit
    ) {
        if (old.size != next.size || old.map { it.type } != next.map { it.type })
            emit(CustomCycleChangeKind.SET_COUNT, at,
                "Number or type of authored sets changed.")
        for (i in 0 until minOf(old.size, next.size)) {
            val a = old[i]
            val b = next[i]
            val path = at + ".set." + i
            if (a.load != b.load) emit(CustomCycleChangeKind.LOADS, path,
                "Working load, unit or physical basis changed.")
            if (a.reps != b.reps) emit(CustomCycleChangeKind.REPS, path,
                "Authored repetition target changed.")
            if (a.targetRir != b.targetRir) emit(CustomCycleChangeKind.RIR, path,
                "Authored effort target changed.")
            if (a.restSeconds != b.restSeconds ||
                a.activeSeconds != b.activeSeconds)
                emit(CustomCycleChangeKind.REST, path,
                    "Rest or working time changed.")
            if (a.amplifier != b.amplifier) {
                emit(CustomCycleChangeKind.AMPLIFIERS, path,
                    "An authored amplifier or follow-up protocol changed.")
                if (a.amplifier?.followUpLoad != b.amplifier?.followUpLoad)
                    emit(CustomCycleChangeKind.LOADS, path + ".amplifier",
                        "Amplifier follow-up load changed.")
            }
        }
    }

    private fun differences(
        old: CustomTrainingCycle, next: CustomTrainingCycle
    ): List<CustomCycleChange> {
        val found = linkedMapOf<Pair<CustomCycleChangeKind,String>, CustomCycleChange>()
        fun emit(kind: CustomCycleChangeKind, path: String, reason: String) {
            found[kind to path] = CustomCycleChange(kind, path, reason)
        }
        if (old.goal != next.goal) emit(CustomCycleChangeKind.GOAL,
            "goal", "Goal changed.")
        if (old.title != next.title || old.authorNote != next.authorNote)
            emit(CustomCycleChangeKind.CUES, "cycle", "Cycle description changed.")
        if (old.blocks.map { it.blockId } != next.blocks.map { it.blockId })
            emit(CustomCycleChangeKind.BLOCKS, "blocks",
                "Block order or stable IDs changed.")
        val nextBlocks = next.blocks.associateBy { it.blockId }
        for (block in old.blocks) {
            val proposed = nextBlocks[block.blockId]
            val path = "block." + block.blockId
            if (proposed == null) {
                if (block.days.any { day -> day.slots.any { it.pinned } })
                    emit(CustomCycleChangeKind.REMOVE_PINNED, path,
                        "Removing a block removes athlete-pinned exercises.")
                continue
            }
            if (block.weeks != proposed.weeks || block.purpose != proposed.purpose)
                emit(CustomCycleChangeKind.BLOCKS, path,
                    "Block duration or purpose changed.")
            if (block.phase != proposed.phase)
                emit(CustomCycleChangeKind.PHASE, path, "Training phase changed.")
            if (block.split != proposed.split)
                emit(CustomCycleChangeKind.SPLIT, path, "Training split changed.")
            if (block.label != proposed.label)
                emit(CustomCycleChangeKind.CUES, path, "Block label changed.")
            if (block.days.size != proposed.days.size)
                emit(CustomCycleChangeKind.DAY_COUNT, path,
                    "Weekly training-day count changed in this block.")
            if (block.days.map { it.weekday } != proposed.days.map { it.weekday })
                emit(CustomCycleChangeKind.SCHEDULE, path,
                    "Training days or order changed.")
            // A newly added session also adds exercise prescriptions.
            // adaptSchedule alone cannot authorize unseen exercises.
            if (proposed.days.any { newDay ->
                    block.days.none { it.weekday==newDay.weekday } &&
                        newDay.slots.isNotEmpty()
                })
                emit(CustomCycleChangeKind.EXERCISES, path,
                    "A newly scheduled day introduces exercise slots.")
            val nextDays = proposed.days.associateBy { it.weekday }
            for (day in block.days) {
                val newerDay = nextDays[day.weekday]
                val location = path + ".day." + day.weekday
                if (newerDay == null) {
                    if(day.slots.isNotEmpty())
                        emit(CustomCycleChangeKind.EXERCISES, location,
                            "A removed day also removes its exercise slots.")
                    if (day.slots.any { it.pinned })
                        emit(CustomCycleChangeKind.REMOVE_PINNED, location,
                            "Removed day contains athlete-pinned exercises.")
                    continue
                }
                if (day.purpose != newerDay.purpose || day.label != newerDay.label)
                    emit(CustomCycleChangeKind.SCHEDULE, location,
                        "Day name or training purpose changed.")
                if (day.slots.map { it.slotId } != newerDay.slots.map { it.slotId })
                    emit(CustomCycleChangeKind.EXERCISES, location,
                        "Exercise order or slots changed.")
                val slots = newerDay.slots.associateBy { it.slotId }
                for (slot in day.slots) {
                    val proposedSlot = slots[slot.slotId]
                    val slotPath = location + ".slot." + slot.slotId
                    if (proposedSlot == null) {
                        if (slot.pinned) emit(CustomCycleChangeKind.REMOVE_PINNED,
                            slotPath, "Athlete-pinned exercise removed.")
                        continue
                    }
                    if (slot.exerciseId != proposedSlot.exerciseId ||
                        slot.role != proposedSlot.role || slot.pinned != proposedSlot.pinned)
                        emit(CustomCycleChangeKind.EXERCISES, slotPath,
                            "Exercise, role or pin changed.")
                    if (slot.pinned && slot.exerciseId != proposedSlot.exerciseId)
                        emit(CustomCycleChangeKind.REMOVE_PINNED, slotPath,
                            "Athlete-pinned exercise was replaced.")
                    if (slot.attachmentId != proposedSlot.attachmentId)
                        emit(CustomCycleChangeKind.ATTACHMENTS, slotPath,
                            "Equipment attachment changed.")
                    if (slot.supersetGroup != proposedSlot.supersetGroup)
                        emit(CustomCycleChangeKind.SUPERSETS, slotPath,
                            "Superset pairing changed.")
                    if (slot.coachNote != proposedSlot.coachNote)
                        emit(CustomCycleChangeKind.CUES, slotPath,
                            "Athlete note changed.")
                    compareSets(slot.sets, proposedSlot.sets, slotPath, ::emit)
                }
            }
        }
        if (old.weekOverrides.keys != next.weekOverrides.keys)
            emit(CustomCycleChangeKind.WEEK_OVERRIDES, "weekOverrides",
                "Week override blocks changed.")
        for (blockId in old.weekOverrides.keys.union(next.weekOverrides.keys)) {
            val original = old.weekOverrides[blockId].orEmpty().associateBy {
                Triple(it.weekInBlock, it.weekday, it.slotId)
            }
            val proposed = next.weekOverrides[blockId].orEmpty().associateBy {
                Triple(it.weekInBlock, it.weekday, it.slotId)
            }
            if (original.keys != proposed.keys)
                emit(CustomCycleChangeKind.WEEK_OVERRIDES,
                    "weekOverrides." + blockId,
                    "Athlete-authored weekly override added or removed.")
            for (key in original.keys.intersect(proposed.keys)) {
                val a = checkNotNull(original[key])
                val b = checkNotNull(proposed[key])
                compareSets(a.sets, b.sets,
                    "weekOverrides." + blockId + "." + a.weekInBlock +
                        "." + a.weekday + "." + a.slotId, ::emit)
            }
        }
        return found.values.toList()
    }

    fun review(
        original: CustomTrainingCycle,
        proposed: CustomTrainingCycle,
        availableExercises: List<CustomExerciseAvailability>,
        origin: CustomCycleEditOrigin,
        permissions: CustomCycleCoachPermissions = CustomCycleCoachPermissions()
    ): CustomCycleTransitionReview {
        val a = CustomTrainingCycleDomain.review(original, availableExercises)
        val b = CustomTrainingCycleDomain.review(proposed, availableExercises)
        val delta = Collections.unmodifiableList(differences(original, proposed))
        val blocked = if (origin == CustomCycleEditOrigin.COACH_SUGGESTION)
            delta.map { it.kind }.filterNot(permissions::permits).toSet()
        else emptySet()
        val status = when {
            original.cycleId != proposed.cycleId ||
                original.athleteId != proposed.athleteId ||
                a.verifiedDraft == null || b.verifiedDraft == null ->
                    CustomCycleTransitionStatus.INVALID_SOURCE_OR_CANDIDATE
            proposed.revision != original.revision + 1 ->
                CustomCycleTransitionStatus.STALE_REVISION
            delta.isEmpty() -> CustomCycleTransitionStatus.NO_CHANGE
            blocked.isNotEmpty() -> CustomCycleTransitionStatus.PERMISSION_REQUIRED
            else -> CustomCycleTransitionStatus.READY_FOR_ATHLETE_REVIEW
        }
        return CustomCycleTransitionReview(
            status, delta, Collections.unmodifiableSet(blocked), a, b)
    }
}
