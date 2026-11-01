package io.github.extracteddeath.pursuitiron.domain

/**
 * A UI confirmation may request skipping the rest of ONE issued exercise.
 * Derive its pending slots from durable journal receipts, not user drafts.
 * This is a selection guard only: it neither authorizes a write nor
 * reorders or recalculates the engine-issued workout.
 */
object NativeSessionSkipPolicy {
    fun remaining(journal: WorkoutJournalState,exerciseIndex: Int): List<JournalSetSlot> {
        require(!journal.isFinished && exerciseIndex in 0..47)
        val slots=journal.slots.filter { it.exerciseIndex==exerciseIndex }
        require(slots.isNotEmpty() && slots.map { it.exerciseId }.toSet().size==1)
        val resolved=journal.completed.map { it.intent.slot }.toSet()
        return slots.filterNot { it in resolved }
    }
}
