package io.github.extracteddeath.pursuitiron.domain

import java.util.Collections

/**
 * PURE reconciliation of durable session receipts. No write handle, PR
 * calculation, load rounding, prescription or workout-authorization logic.
 * Caller must obtain the ordered slots from a certified canonical workout,
 * and receipts from a transactional, authoritative native store.
 */
data class JournalSessionKey(
    val programRevision: ReadOnlyProgramReference,
    val workoutId: String,
    val dayId: String,
    val weekIndex: Int,
    val unit: String,
    val startedAt: Long
)

data class JournalSetSlot(val exerciseIndex: Int, val setIndex: Int, val exerciseId: String)
data class JournalSetValues(val load: String?, val unit: String?, val reps: Int, val rir: Int?)
/** Session-only user selected adjustment, not an injury diagnosis. */
data class JournalSetAdjustment(val action: String,val part: String)
enum class JournalSetDisposition { COMPLETED, SKIPPED }

data class JournalSetIntent(
    val operationId: String,
    val slot: JournalSetSlot,
    val disposition: JournalSetDisposition,
    val values: JournalSetValues?,
    val replacesOperationId: String? = null,
    val adjustment: JournalSetAdjustment? = null
)

data class JournalSetReceipt(
    val session: JournalSessionKey,
    val operationId: String,
    val slot: JournalSetSlot,
    val disposition: JournalSetDisposition,
    val storedValues: JournalSetValues?,
    val fromRevision: Long,
    val toRevision: Long,
    val replacesOperationId: String? = null,
    val adjustment: JournalSetAdjustment? = null
)

data class JournalSetCommit(val intent: JournalSetIntent, val receipt: JournalSetReceipt)
data class JournalFinishIntent(
    val operationId: String,
    val completedAt: Long,
    val durationMs: Long
)
data class JournalFinishReceipt(
    val session: JournalSessionKey,
    val operationId: String,
    val fromRevision: Long,
    val toRevision: Long,
    val completedAt: Long,
    val durationMs: Long
)
data class JournalFinishCommit(val intent: JournalFinishIntent, val receipt: JournalFinishReceipt)

/** Only confirmed store facts belong here; UI drafts must remain separate. */
class WorkoutJournalState internal constructor(
    val session: JournalSessionKey,
    val slots: List<JournalSetSlot>,
    val revision: Long,
    val completed: List<JournalSetCommit>,
    val finish: JournalFinishCommit?,
    val events: List<JournalSetCommit> = completed
) {
    val isFinished get() = finish != null
    val unresolvedCount get() = slots.size - completed.size
}

object WorkoutJournalPolicy {
    const val MAX_EVENTS = 1024
    private const val MAX_REV = 9_007_199_254_740_991L
    private val id = Regex("^[A-Za-z0-9][A-Za-z0-9_-]{0,127}$")
    private val exerciseId = Regex("^[A-Za-z0-9][A-Za-z0-9_-]{0,79}$")
    private val load = Regex("^(0|[1-9][0-9]{0,4})(\\.[0-9]{1,3})?$")
    private val muscle = Regex("^[a-z][a-z_]{0,39}$")

    private fun validObservation(value: JournalSetValues?): Boolean =
        value != null && value.reps in 0..100 &&
            (value.rir == null || value.rir in 0..10) &&
            ((value.load == null && value.unit == null) ||
             (value.load != null && value.unit in setOf("lb", "kg") &&
                 load.matches(value.load)))

    private fun validIntent(intent: JournalSetIntent): Boolean =
        id.matches(intent.operationId) &&
            (intent.replacesOperationId == null || id.matches(intent.replacesOperationId)) &&
            when (intent.disposition) {
                JournalSetDisposition.SKIPPED -> intent.values == null &&
                    (intent.adjustment==null ||
                        intent.adjustment.action in setOf("skip","reduce") &&
                        muscle.matches(intent.adjustment.part))
                JournalSetDisposition.COMPLETED -> intent.adjustment==null &&
                    validObservation(intent.values)
            }

    private fun increment(from: Long, to: Long): Boolean =
        from in 0 until MAX_REV && to == from + 1

    /** Opening a reducer does NOT create a training/persistence capability. */
    fun open(
        session: JournalSessionKey, slots: List<JournalSetSlot>, revision: Long
    ): WorkoutJournalState? {
        if (!id.matches(session.workoutId) || !id.matches(session.dayId) ||
            session.weekIndex !in 1..104 || session.unit !in setOf("lb", "kg") ||
            session.startedAt !in 1..8_640_000_000_000_000L ||
            revision !in 0..MAX_REV ||
            !ReadOnlyProgramReferenceGate.sameActiveRevision(
                session.programRevision, session.programRevision, null
            ) || slots.size !in 1..480) return null
        val detached = ArrayList(slots)
        if (detached.any {
            it.exerciseIndex !in 0..47 || it.setIndex !in 0..19 ||
                !exerciseId.matches(it.exerciseId)
        } || detached.map { it.exerciseIndex to it.setIndex }.toSet().size != detached.size)
            return null
        return WorkoutJournalState(
            session, Collections.unmodifiableList(detached), revision, emptyList(), null
        )
    }

    /** A pending action is NEVER a completed set; only an exact ack can advance. */
    fun afterPersistedSet(
        state: WorkoutJournalState, intent: JournalSetIntent, ack: JournalSetReceipt
    ): WorkoutJournalState? {
        if (!validIntent(intent) || ack.session != state.session ||
            (intent.values?.unit != null && intent.values.unit != state.session.unit) ||
            ack.operationId != intent.operationId || ack.slot != intent.slot ||
            ack.disposition != intent.disposition ||
            ack.storedValues != intent.values || ack.replacesOperationId != intent.replacesOperationId ||
            ack.adjustment != intent.adjustment) return null
        val replay = state.events.find { it.intent.operationId == intent.operationId }
        if (replay != null)
            return if (replay.intent == intent && replay.receipt == ack) state else null
        val prior = state.completed.find { it.intent.slot == intent.slot }
        if (state.isFinished || intent.slot !in state.slots || state.events.size >= MAX_EVENTS ||
            (prior == null && intent.replacesOperationId != null) ||
            (prior != null && intent.replacesOperationId != prior.intent.operationId) ||
            // Reserve one original receipt for every unresolved slot.
            (prior != null && state.events.size + state.unresolvedCount >= MAX_EVENTS) ||
            ack.fromRevision != state.revision ||
            !increment(ack.fromRevision, ack.toRevision)) return null
        val next = ArrayList(state.completed)
        val committed=JournalSetCommit(intent, ack)
        if(prior==null)next += committed else next[next.indexOf(prior)] = committed
        val events=ArrayList(state.events).apply { add(committed) }
        return WorkoutJournalState(
            state.session, state.slots, ack.toRevision,
            Collections.unmodifiableList(next), null, Collections.unmodifiableList(events)
        )
    }

    /** Finish requires committed resolution (completed OR skipped) of each slot. */
    fun afterPersistedFinish(
        state: WorkoutJournalState, intent: JournalFinishIntent,
        ack: JournalFinishReceipt
    ): WorkoutJournalState? {
        if (!id.matches(intent.operationId) || ack.session != state.session ||
            ack.operationId != intent.operationId ||
            intent.completedAt !in state.session.startedAt..8_640_000_000_000_000L ||
            intent.durationMs !in 0..(intent.completedAt - state.session.startedAt) ||
            ack.completedAt != intent.completedAt || ack.durationMs != intent.durationMs ||
            state.events.any { it.intent.operationId == intent.operationId }) return null
        val replay = state.finish
        if (replay != null)
            return if (replay.intent == intent && replay.receipt == ack) state else null
        if (state.unresolvedCount != 0 || ack.fromRevision != state.revision ||
            !increment(ack.fromRevision, ack.toRevision)) return null
        return WorkoutJournalState(
            state.session, state.slots, ack.toRevision, state.completed,
            JournalFinishCommit(intent, ack), state.events
        )
    }
}
