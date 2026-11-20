package io.github.extracteddeath.pursuitiron

/**
 * Read-only presentation eligibility for the SAME owner-verified issued
 * workout. Not a journal authorization or a substitute for the revision,
 * equipment, superset and recorded-visit checks in the native host.
 *
 * Equipment/swap selection is independently reviewed by
 * NativeKotlinEquipmentRecoveryPolicy; these actions remain in Exercise
 * Info instead of a duplicate set-card control group.
 */
internal data class NativeWorkoutInfoActionAvailability(
    val mayRequestSkipRemaining:Boolean,
    val mayRequestUndoSwap:Boolean
)

internal object NativeWorkoutInfoActionPolicy {
    fun review(
        active:Boolean,
        hasRemainingSets:Boolean,
        hasActiveSwap:Boolean,
        hasRecordedWork:Boolean,
        linkedSuperset:Boolean=false
    ):NativeWorkoutInfoActionAvailability =
        NativeWorkoutInfoActionAvailability(
            // Skips are explicit and must still pass the native journal's
            // current revision, scope and confirmation dialog.
            mayRequestSkipRemaining=active && hasRemainingSets,
            // An undo must never erase an actual, failed attempt, skipped
            // receipt or any linked superset's durable replacement slot.
            mayRequestUndoSwap=active && hasActiveSwap &&
                !hasRecordedWork && !linkedSuperset
        )
}
