package io.github.extracteddeath.pursuitiron.rebuild

import java.util.Collections

/**
 * Read-only 45/30/20-minute preview for an independently staged Kotlin workout.
 *
 * This NEVER issues, shortens or persists an active workout. Before a native
 * start can use these omissions, its owner must re-stage the exact approved
 * program, authenticate the current athlete/program/grant/gym revisions, and
 * commit the reduced immutable prescription with its own versioned journal
 * receipt. A preview result is NOT that approval or receipt.
 */
enum class KotlinShortSessionStatus {
    INVALID_LIMIT, SOURCE_OR_PROGRAM_UNRESOLVED, ALREADY_FITS,
    READY_FOR_ATHLETE_REVIEW, PROTECTED_WORK_EXCEEDS_LIMIT
}

data class KotlinShortSessionReview(
    val status: KotlinShortSessionStatus,
    val sourceWorkoutId: String?,
    val requestedMinutes: Int,
    val estimatedSeconds: Long?,
    val originalEstimatedSeconds: Long?,
    val keptSetKeys: List<String>,
    val omittedSetKeys: List<String>,
    val omittedExerciseIds: List<String>,
    val omittedWorkingSets: Int,
    val explanation: String
) {
    val changesPrescription: Boolean get() = omittedSetKeys.isNotEmpty()
    val canStartWorkout: Boolean get() = false
    val canModifyJournal: Boolean get() = false
    val canSaveProgram: Boolean get() = false
}

object KotlinShorterSessionReviewDomain {
    private val allowed = setOf(20, 30, 45)

    /**
     * Authoritative *read-only* entry point. Never accept an arbitrary
     * externally assembled snapshot as proof of an approved workout.
     */
    fun review(
        request: KotlinWorkoutSnapshotRequest,
        requestedMinutes: Int
    ): KotlinShortSessionReview {
        fun unavailable(status: KotlinShortSessionStatus, why: String) =
            KotlinShortSessionReview(status, null, requestedMinutes, null, null,
                emptyList(), emptyList(), emptyList(), 0, why)
        if(requestedMinutes !in allowed)
            return unavailable(KotlinShortSessionStatus.INVALID_LIMIT,
                "Choose Full, 45, 30 or 20 minutes; Full uses the unchanged original workout.")
        val staged = runCatching { KotlinWorkoutSnapshotDomain.stage(request) }.getOrNull()
            as? KotlinWorkoutSnapshotResult.Staged
            ?: return unavailable(KotlinShortSessionStatus.SOURCE_OR_PROGRAM_UNRESOLVED,
                "The full current workout must pass its program and source review first.")
        return inspectStaged(staged.snapshot, request.approvedProgram, requestedMinutes)
    }

    /**
     * Pure preview computation, separately exercised by bounded synthetic
     * tests. Not a public issuance path; production calls review() above.
     */
    internal fun inspectStaged(
        staged: KotlinStagedWorkout,
        program: ComposerRequest,
        requestedMinutes: Int
    ): KotlinShortSessionReview {
        require(requestedMinutes in allowed)
        require(staged.dayIndex in program.days.indices &&
            staged.calendarDay == program.days[staged.dayIndex].calendarDay)
        val rows = staged.targets
        require(rows.isNotEmpty() && rows.size <= 256 &&
            rows.map { it.setKey }.distinct().size == rows.size)
        val order = rows.map { it.exerciseId }.distinct()
        val byId = rows.groupBy { it.exerciseId }
        val options = program.exercises.associateBy { it.exerciseId }
        require(order.all { options.containsKey(it) })
        val intent = program.sessionIntents.singleOrNull {
            it.dayIndex == staged.dayIndex
        }
        val keyLift = intent?.requiredPrimaryExerciseId
            ?.takeIf { byId.containsKey(it) }
            ?: order.firstOrNull { options.getValue(it).role == ComposerRole.PRIMARY }
            ?: order.first()
        val companion = order.firstOrNull { id ->
            id != keyLift &&
                options.getValue(id).role != ComposerRole.ACCESSORY &&
                options.getValue(id).pattern !in setOf(
                    ComposerPattern.ISOLATION, ComposerPattern.CORE)
        }
        val strengthPurpose = staged.purpose == ComposerSessionPurpose.STRENGTH_SKILL ||
            program.phase in setOf(TrainingPhase.STRENGTH, TrainingPhase.PEAK)

        val workById = byId.mapValues { (_,targets) ->
            targets.filter { it.type == DraftSetType.WORKING }
        }
        require(workById.values.all { it.isNotEmpty() })
        // A different final-set load/reps/RIR may be a purpose-specific
        // backoff or another nonuniform protocol. Do not make it disappear
        // by trimming its last row while pretending the scheme is unchanged.
        val structured = workById.mapValues { (_,targets) ->
            targets.map { Triple(it.load,it.reps,it.targetRir) }.distinct().size > 1
        }
        val protectedAll = order.filter { id ->
            options.getValue(id).requiredVisits > 0 ||
                (strengthPurpose &&
                    options.getValue(id).role != ComposerRole.ACCESSORY) ||
                structured.getValue(id)
        }.toSet()
        // Only known unpinned optional exercises can disappear entirely.
        val protectedExercises = setOfNotNull(keyLift,companion) +
            order.filter { options.getValue(it).requiredVisits > 0 }

        val totalSetSeconds = rows.sumOf {
            it.activeSeconds.toLong() + it.restSeconds
        }
        // Keep at least five minutes of preparation. The source's existing
        // rounded duration may encode additional setup not carried by sets.
        // Take the larger figure, never charge a 5-minute allowance twice.
        val overhead = maxOf(
            300L,
            program.days[staged.dayIndex].window.declaredNonSetSeconds.toLong(),
            (staged.estimatedMinutes * 60L - totalSetSeconds).coerceAtLeast(0L)
        )
        val kept = rows.map { it.setKey }.toMutableSet()
        fun visible() = rows.filter { it.setKey in kept }
        fun duration(): Long {
            val active = visible()
            // No rest is performed AFTER a workout's final retained set.
            return overhead + active.sumOf {
                it.activeSeconds.toLong() + it.restSeconds
            } - (active.lastOrNull()?.restSeconds ?: 0)
        }
        val original = duration()
        val limitSeconds = requestedMinutes.toLong() * 60L
        // Preserve user-selected exercise sequence. For otherwise equal
        // removal priorities, cut later optional work first.
        fun priority(id: String): Int = when {
            options.getValue(id).role == ComposerRole.ACCESSORY -> 0
            id != companion && id != keyLift &&
                options.getValue(id).role == ComposerRole.SECONDARY -> 1
            id != keyLift && id != companion -> 2
            id == companion -> 3
            else -> 4
        }
        val cutOrder = order.withIndex().sortedWith(
            compareBy<IndexedValue<String>> { priority(it.value) }
                .thenByDescending { it.index }
        ).map { it.value }
        if(original > limitSeconds) {
            // First drop optional *extra* working sets above two, without
            // reindexing any surviving set. A one-set authored visit remains
            // one set; no synthetic prescription is invented.
            for(id in cutOrder) {
                if(id in protectedAll) continue
                val exercise = workById.getValue(id)
                for(target in exercise.asReversed()) {
                    if(duration() <= limitSeconds) break
                    if(exercise.count { it.setKey in kept } <= 2) break
                    kept.remove(target.setKey)
                }
            }
            // Next omit whole optional exercises, including ALL of their
            // warmups. Never leave a warmup without its working exercise.
            for(id in cutOrder) {
                if(duration() <= limitSeconds) break
                if(id in protectedExercises) continue
                byId.getValue(id).forEach { kept.remove(it.setKey) }
            }
        }

        val surviving = visible()
        val omitted = rows.filterNot { it.setKey in kept }
        val changed = omitted.isNotEmpty()
        val after = duration()
        val status = when {
            !changed && original <= limitSeconds -> KotlinShortSessionStatus.ALREADY_FITS
            after <= limitSeconds -> KotlinShortSessionStatus.READY_FOR_ATHLETE_REVIEW
            else -> KotlinShortSessionStatus.PROTECTED_WORK_EXCEEDS_LIMIT
        }
        val explanation = when(status) {
            KotlinShortSessionStatus.ALREADY_FITS ->
                "The full approved session already fits this estimate; no sets change."
            KotlinShortSessionStatus.READY_FOR_ATHLETE_REVIEW ->
                "Optional work can be omitted without changing remaining weights, reps, RIR, warmups, or rest between sets. Training volume is reduced; explicitly review before starting."
            KotlinShortSessionStatus.PROTECTED_WORK_EXCEEDS_LIMIT ->
                "The protected lifts and their prescribed work exceed this time limit. Choose Full or a longer limit; shortening rest or silently changing strength sets is not allowed."
            else -> error("A staged preview cannot have an invalid source or limit.")
        }
        return KotlinShortSessionReview(
            status,staged.workoutId,requestedMinutes,after,original,
            Collections.unmodifiableList(surviving.map { it.setKey }),
            Collections.unmodifiableList(omitted.map { it.setKey }),
            Collections.unmodifiableList(order.filter {
                id -> byId.getValue(id).all { it.setKey !in kept }
            }),
            omitted.count { it.type == DraftSetType.WORKING },
            explanation
        )
    }
}
