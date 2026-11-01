package io.github.extracteddeath.pursuitiron.domain

/**
 * A UI confirmation may request skipping the rest of ONE issued exercise.
 * Derive its pending slots from durable journal receipts, not user drafts.
 * This is a selection guard only: it neither authorizes a write nor
 * reorders or recalculates the engine-issued workout.
 */
object NativeSessionSkipPolicy {
    /**
     * Selects unresolved journal slots across every canonical engine-reviewed
     * exercise involving a muscle. Reviews are UI guides, not prescriptions:
     * only the pinned journal's exact slot roster may ever be committed.
     */
    fun remainingAffected(journal: WorkoutJournalState,review: NativeWorkoutMuscleReview,
        part: String): List<JournalSetSlot> {
        require(!journal.isFinished && review.workoutId==journal.session.workoutId)
        require(part in review.parts && review.rows.size in 1..48 &&
            review.parts==review.parts.distinct().sorted())
        val issued=journal.slots.groupBy { it.exerciseIndex }
        require(issued.size==review.rows.size &&
            review.rows.map { it.exerciseIndex }.toSet()==issued.keys)
        for(row in review.rows) {
            val slots=checkNotNull(issued[row.exerciseIndex])
            require(row.exerciseIndex in 0..47 && slots.isNotEmpty() &&
                slots.all { it.exerciseId==row.exerciseId } &&
                row.primaryPart in row.involvedParts &&
                row.involvedParts.isNotEmpty() &&
                row.involvedParts.all { it in review.parts })
        }
        val affected=review.rows.filter { part in it.involvedParts }.map { it.exerciseIndex }.toSet()
        require(affected.isNotEmpty())
        val resolved=journal.completed.map { it.intent.slot }.toSet()
        return journal.slots.filter { it.exerciseIndex in affected && it !in resolved }
    }

    /**
     * Stable half-dose cap for explicitly reviewed muscle involvement.
     * Keep the first ceil(original issued slots / 2) slots PER exercise.
     * Never erase an already completed or skipped observation.
     *
     * The fixed issue-order boundary is intentional: recomputing "half the
     * currently pending sets" after a partial write would skip more and more
     * on every crash retry. An already completed late set stays completed.
     * This is a user-confirmed selection, never a new engine prescription.
     */
    fun reduceAffected(journal: WorkoutJournalState,review: NativeWorkoutMuscleReview,
        part: String): List<JournalSetSlot> {
        val pending=remainingAffected(journal,review,part).toSet()
        val affected=review.rows.filter { part in it.involvedParts }.map { it.exerciseIndex }.toSet()
        val candidate=journal.slots.filter { it.exerciseIndex in affected }
            .groupBy { it.exerciseIndex }.values.flatMap { sets ->
                val ordered=sets.sortedBy { it.setIndex }
                ordered.drop((ordered.size+1)/2)
            }.toSet()
        return journal.slots.filter { it in candidate && it in pending }
    }

    fun remaining(journal: WorkoutJournalState,exerciseIndex: Int): List<JournalSetSlot> {
        require(!journal.isFinished && exerciseIndex in 0..47)
        val slots=journal.slots.filter { it.exerciseIndex==exerciseIndex }
        require(slots.isNotEmpty() && slots.map { it.exerciseId }.toSet().size==1)
        val resolved=journal.completed.map { it.intent.slot }.toSet()
        return slots.filterNot { it in resolved }
    }
}
