package io.github.extracteddeath.pursuitiron

/**
 * Pure eligibility for the two source-verified Exercise Info actions.
 * Selecting another physical Machine A/B retains the SAME exercise but
 * switches station-scoped loads, Last, and PRs. Replacing the movement
 * creates an independently reviewed workout-only prescription.
 * Neither action can rewrite a previously recorded set.
 *
 * Focus and All Sets use the same Info sheet: never duplicate a permanent
 * full-width equipment banner above each working set.
 */
internal data class NativeKotlinEquipmentRecoveryAvailability(
    val canChangeStation:Boolean,
    val canReplaceExercise:Boolean,
    val reason:String?
)

internal object NativeKotlinEquipmentRecoveryPolicy {
    fun review(
        activeWorkout:Boolean,
        hasPhysicalMachineConfig:Boolean,
        hasRecordedExerciseWork:Boolean,
        linkedSuperset:Boolean=false
    ):NativeKotlinEquipmentRecoveryAvailability {
        val canStation=activeWorkout && hasPhysicalMachineConfig
        val canReplace=activeWorkout && !hasRecordedExerciseWork && !linkedSuperset
        val reason=when {
            !activeWorkout -> "Finish or recovery review is read-only."
            hasRecordedExerciseWork ->
                "This exercise already has recorded work. Its saved sets and history are protected."
            linkedSuperset ->
                "This exercise belongs to a linked superset. Change the program or review the whole group instead."
            else -> null
        }
        return NativeKotlinEquipmentRecoveryAvailability(canStation,canReplace,reason)
    }
}
