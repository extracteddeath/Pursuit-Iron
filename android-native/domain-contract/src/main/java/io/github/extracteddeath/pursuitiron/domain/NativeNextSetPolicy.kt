package io.github.extracteddeath.pursuitiron.domain

/**
 * Display-only next-set projection from a verified, issued session plus its
 * durably acknowledged journal. Never calculates reps/loads or edits history.
 * Exercise position and original slot identity are deliberately separate.
 */
data class NativePendingSet(
    val exercisePosition: Int,
    val exercise: NativeWorkoutExercise,
    val set: NativeWorkoutSet
)
data class NativeNextSetPreview(
    val current: NativePendingSet?,
    val upNext: NativePendingSet?,
    val remainingTotal: Int
)

object NativeNextSetPolicy {
    fun inspect(progress: NativeWorkoutProgress, selectedPosition: Int): NativeNextSetPreview? {
        val plan=progress.plan
        val journal=progress.journal
        if(plan.session!=journal.session || journal.isFinished ||
            plan.exercises.isEmpty() || selectedPosition !in plan.exercises.indices ||
            plan.slots!=journal.slots ||
            journal.completed.size>journal.slots.size) return null

        val committed=journal.completed.map { it.intent.slot }.toSet()
        if(committed.size!=journal.completed.size || !journal.slots.containsAll(committed))
            return null
        // Ordered strictly as emitted by the canonical engine. No sorting by
        // exercise IDs: duplicate movements have distinct indexed slots.
        val issued=plan.exercises.flatMapIndexed { position,exercise ->
            exercise.sets.map { set -> NativePendingSet(position,exercise,set) }
        }
        val pending=issued.filter { it.set.slot !in committed }
        if(pending.size!=journal.unresolvedCount)return null
        if(pending.isEmpty())return NativeNextSetPreview(null,null,0)

        val current=pending.firstOrNull { it.exercisePosition==selectedPosition }
        // If the chosen exercise is fully resolved, resume the first pending
        // exercise after it, wrapping only if required.
        val cursor=if(current!=null)issued.indexOfFirst { it.set.slot==current.set.slot }
            else issued.indexOfLast { it.exercisePosition==selectedPosition }
        val after=issued.drop(cursor+1).firstOrNull { it.set.slot !in committed }
        val before=issued.take((cursor+1).coerceIn(0,issued.size))
            .firstOrNull { it.set.slot !in committed }
        val upcoming=if(current!=null && pending.size==1)null
            else after ?: before
        return NativeNextSetPreview(current,upcoming,pending.size)
    }
}
