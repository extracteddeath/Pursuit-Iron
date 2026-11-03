package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import java.util.Collections

/**
 * Native-engine replacement milestone: produce a self-contained immutable
 * target snapshot for ONE approved day, before an atomic store transaction.
 *
 * The old JS engine is not imported, called, or used as fallback. The owner
 * must authenticate the current athlete/program/gym/catalog/rules revisions,
 * the user's accepted program and the one-time workout ID independently.
 * This pure rule domain CANNOT issue a workout or persist a journal by itself.
 */
data class KotlinTrainingSourceRevision(
    val athleteId: String,
    val athleteRevision: Long,
    val programId: String,
    val programRevision: Long,
    val gymId: String,
    val gymRevision: Long,
    val catalogRevision: String,
    val coachingRulesRevision: String
)

/** Native storage must authenticate this grant, including its program revision. */
data class KotlinWorkoutSelectionGrant(
    val grantId: String,
    val workoutId: String,
    val approvedSource: KotlinTrainingSourceRevision,
    /** Index of day in the independently approved ComposerRequest. */
    val dayIndex: Int
)

data class KotlinWorkoutSnapshotRequest(
    val source: KotlinTrainingSourceRevision,
    val currentSource: KotlinTrainingSourceRevision,
    val grant: KotlinWorkoutSelectionGrant,
    val approvedProgram: ComposerRequest,
    val policies: List<ExercisePrescriptionPolicy>
)

data class KotlinWorkoutTarget(
    val exerciseId: String,
    val role: ComposerRole,
    val purpose: ComposerSessionPurpose,
    val setKey: String,
    val type: DraftSetType,
    val ordinal: Int,
    val load: DraftSetLoad,
    val reps: RepBand,
    val targetRir: Int?,
    val activeSeconds: Int,
    val restSeconds: Int
)

data class KotlinStagedWorkout(
    val source: KotlinTrainingSourceRevision,
    val grantId: String,
    val workoutId: String,
    val calendarDay: Int,
    val dayIndex: Int,
    val purpose: ComposerSessionPurpose,
    val estimatedMinutes: Long,
    val targets: List<KotlinWorkoutTarget>
) {
    /** Only native owned, atomic storage may issue or acknowledge this. */
    val persisted: Boolean get() = false
    val canModifyJournal: Boolean get() = false
}

enum class KotlinSnapshotRejection {
    INVALID_SOURCE,
    SOURCE_CHANGED,
    INVALID_GRANT,
    INVALID_PROGRAM,
    PROGRAM_UNRESOLVED,
    DAY_UNAVAILABLE,
    UNVERIFIED_TARGET
}

sealed interface KotlinWorkoutSnapshotResult {
    data class Rejected(val reason: KotlinSnapshotRejection) : KotlinWorkoutSnapshotResult
    data class Staged(val snapshot: KotlinStagedWorkout) : KotlinWorkoutSnapshotResult
}

object KotlinWorkoutSnapshotDomain {
    private val id = Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,63}$")
    private const val MAX_REV = 9_007_199_254_740_991L
    private val zero = BigDecimal.ZERO
    private val maxLoad = BigDecimal("100000")

    private fun sourceValid(source: KotlinTrainingSourceRevision): Boolean =
        listOf(source.athleteId, source.programId, source.gymId,
            source.catalogRevision, source.coachingRulesRevision).all(id::matches) &&
            listOf(source.athleteRevision, source.programRevision, source.gymRevision)
                .all { it in 1..MAX_REV }

    private fun verifiedTarget(
        option: ComposerExerciseOption, target: DraftPrescribedSet
    ): Boolean = when(val equipment=option.equipment) {
        ComposerEquipment.Bodyweight ->
            target.load == DraftSetLoad.ExplicitBodyweight
        is ComposerEquipment.Verified -> {
            val weight = target.load as? DraftSetLoad.Verified ?: return false
            if(weight.unit != equipment.unit || weight.basis != equipment.inventory?.basis ||
                weight.value < zero || weight.value > maxLoad)
                return false
            val inspection=EquipmentLoadDomain.inspect(
                equipment.inventory,weight.value,equipment.unit)
            inspection is LoadInspection.Confirmed && inspection.exact &&
                inspection.basis == weight.basis
        }
    }

    /**
     * Recompute the whole week's set prescription from source inputs: a
     * caller-provided 'ready' draft can never authorize an issued session.
     * No partially reviewed or even just one valid day can slip through
     * if the rest of the approved program violates declared constraints.
     */
    fun stage(request: KotlinWorkoutSnapshotRequest): KotlinWorkoutSnapshotResult {
        if(!sourceValid(request.source) || !sourceValid(request.currentSource))
            return KotlinWorkoutSnapshotResult.Rejected(KotlinSnapshotRejection.INVALID_SOURCE)
        if(request.source != request.currentSource)
            return KotlinWorkoutSnapshotResult.Rejected(KotlinSnapshotRejection.SOURCE_CHANGED)
        if(!id.matches(request.grant.grantId) || !id.matches(request.grant.workoutId) ||
            request.grant.approvedSource != request.source ||
            request.grant.dayIndex !in request.approvedProgram.days.indices)
            return KotlinWorkoutSnapshotResult.Rejected(KotlinSnapshotRejection.INVALID_GRANT)

        val draft=runCatching {
            CoachSessionPrescriptionDomain.draft(request.approvedProgram,request.policies)
        }.getOrElse {
            return KotlinWorkoutSnapshotResult.Rejected(KotlinSnapshotRejection.INVALID_PROGRAM)
        }
        if(draft.status != PrescriptionDraftStatus.COMPLETE_FOR_REVIEW ||
            draft.finalAudit?.satisfiesDeclaredHardConstraints != true)
            return KotlinWorkoutSnapshotResult.Rejected(KotlinSnapshotRejection.PROGRAM_UNRESOLVED)

        val selected=draft.sessions.singleOrNull {
            it.dayIndex==request.grant.dayIndex
        } ?: return KotlinWorkoutSnapshotResult.Rejected(KotlinSnapshotRejection.DAY_UNAVAILABLE)
        val calendar=request.approvedProgram.days[request.grant.dayIndex].calendarDay
        val minutes=selected.estimatedMinutes
        if(selected.exercises.isEmpty() || minutes==null || minutes<=0)
            return KotlinWorkoutSnapshotResult.Rejected(KotlinSnapshotRejection.DAY_UNAVAILABLE)
        val options=request.approvedProgram.exercises.associateBy {it.exerciseId}
        val targets=selected.exercises.flatMap { exercise->
            exercise.sets.map { set->
                KotlinWorkoutTarget(exercise.exerciseId,exercise.role,exercise.purpose,
                    set.stableSetKey,set.type,set.ordinal,set.load,
                    set.targetReps,set.targetRir,set.activeSeconds,set.restSeconds)
            }
        }
        if(targets.isEmpty() || targets.size>256 ||
            targets.map { it.setKey }.distinct().size!=targets.size ||
            targets.any { target->
                val exercise=options[target.exerciseId] ?:return@any true
                val set=selected.exercises.firstOrNull { it.exerciseId==target.exerciseId }
                    ?.sets?.firstOrNull { it.stableSetKey==target.setKey } ?:return@any true
                !verifiedTarget(exercise,set) ||
                    target.activeSeconds !in 1..3600 ||
                    target.restSeconds !in 0..3600 ||
                    (target.targetRir!=null && target.targetRir !in 0..10)
            })
            return KotlinWorkoutSnapshotResult.Rejected(KotlinSnapshotRejection.UNVERIFIED_TARGET)

        return KotlinWorkoutSnapshotResult.Staged(KotlinStagedWorkout(
            request.source,request.grant.grantId,request.grant.workoutId,
            calendar,request.grant.dayIndex,selected.purpose,minutes,
            Collections.unmodifiableList(targets)
        ))
    }
}
