package io.github.extracteddeath.pursuitiron.domain

/**
 * Display-only rest suggestion from an ACKNOWLEDGED working-set event.
 *
 * The durations are the issued engine's own values, never native training
 * calculations. This policy has no mutable timer, training history, clock or
 * persistence access. A Compose timer may ignore the cue after restoring UI.
 */
data class NativeRestCue(val operationId: String, val seconds: Int, val exerciseIndex: Int)

object NativeRestCuePolicy {
    fun latest(progress: NativeWorkoutProgress, selectedExerciseIndex: Int): NativeRestCue? {
        val plan=progress.plan
        val journal=progress.journal
        if(plan.session!=journal.session || journal.isFinished || journal.unresolvedCount==0)
            return null
        val last=journal.events.lastOrNull() ?: return null
        val action=last.intent
        if(action.disposition!=JournalSetDisposition.COMPLETED ||
            action.replacesOperationId!=null || last.receipt.operationId!=action.operationId ||
            last.receipt.session!=plan.session ||
            journal.completed.none { it.intent.operationId==action.operationId })
            return null
        val exercise=plan.exercises.firstOrNull { it.index==selectedExerciseIndex &&
            it.index==action.slot.exerciseIndex } ?: return null
        // A superset's rest belongs to the PAIR, not a random individual set;
        // no automated cue is issued until a real paired-session rest protocol.
        if(exercise.superset) return null
        val set=exercise.sets.firstOrNull { it.slot==action.slot } ?: return null
        if(set.isExtension && set.extensionRestSeconds==null) return null
        val resolved=journal.completed.map { it.intent.slot }.toSet()
        val next=exercise.sets.firstOrNull { it.slot !in resolved }
        val seconds=next?.extensionRestSeconds ?: exercise.restSeconds
        if(seconds !in 1..3600) return null
        return NativeRestCue(action.operationId,seconds,exercise.index)
    }
}
