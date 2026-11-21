package io.github.extracteddeath.pursuitiron.rebuild

import java.util.Collections

/**
 * One typed, multi-set measured workout. Unlike a repetitions-based journal,
 * every visit retains the exact unit, sides, external load basis, band and
 * physical station from its original reviewed plan.
 *
 * This pure reducer has NO ownership, disk-write, history or issuance rights.
 * Its parent-level Finish is a proposed state only. A separate owner-fenced
 * native transaction must durably commit Finish to the canonical History
 * manifest before either History or progression can consume the workout.
 */
data class MeasuredWorkoutPlan(
    val workoutId:String,
    val gymId:String,
    val gymRevision:Long,
    val sets:List<MeasuredExerciseSetPlan>
) {
    val mayIssueWorkout:Boolean get()=false
    val mayPublishHistory:Boolean get()=false
}

sealed interface MeasuredWorkoutOperation {
    val eventId:String
    val expectedRevision:Long

    data class Record(
        override val eventId:String,
        override val expectedRevision:Long,
        val setKey:String,
        val outcome:MeasuredSetOutcome,
        val actual:MeasuredExerciseActual?,
        val correctsEventId:String?=null
    ):MeasuredWorkoutOperation

    data class Finish(
        override val eventId:String,
        override val expectedRevision:Long
    ):MeasuredWorkoutOperation
}

data class MeasuredWorkoutReceipt(
    val revision:Long,
    val operation:MeasuredWorkoutOperation
)

data class MeasuredWorkoutJournal(
    val approvedPlan:MeasuredWorkoutPlan,
    val revision:Long,
    val receipts:List<MeasuredWorkoutReceipt>,
    val bySetKey:Map<String,MeasuredExerciseJournal>,
    val finishedByEventId:String?
) {
    val canProposeFinish:Boolean get()=
        finishedByEventId==null && bySetKey.isNotEmpty() &&
        bySetKey.values.all{it.effectiveRecord!=null}
    /** A proposed Finish is NOT a committed canonical History entry. */
    val mayPublishHistory:Boolean get()=false
    val mayChangeTrainingProgram:Boolean get()=false
}

enum class MeasuredWorkoutRefusal {
    INVALID_SOURCE, INVALID_EVENT, UNKNOWN_SET, STALE_REVISION,
    EVENT_COLLISION, SET_REJECTED, INCOMPLETE, FINISHED,
    TOO_MANY_RECEIPTS
}
sealed interface MeasuredWorkoutDecision {
    data class Proposed(
        val journal:MeasuredWorkoutJournal,
        val replayed:Boolean=false
    ):MeasuredWorkoutDecision
    data class Rejected(
        val reason:MeasuredWorkoutRefusal,
        val setReason:MeasurementRefusal?=null
    ):MeasuredWorkoutDecision
}

object MeasuredWorkoutJournalDomain {
    private const val MAX_SETS=64
    private const val MAX_RECEIPTS=1024
    private val id=Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,79}$")
    private const val MAX_REVISION=9_007_199_254_740_991L

    /**
     * All sets must come from the ONE exact approved parent workout and gym.
     * A screen cannot combine individually valid sets from unrelated workouts.
     */
    fun begin(input:MeasuredWorkoutPlan):MeasuredWorkoutJournal? {
        val frozen=input.copy(sets=Collections.unmodifiableList(
            input.sets.map(MeasuredWorkoutSnapshotDomain::plan)))
        if(!id.matches(frozen.workoutId) || !id.matches(frozen.gymId) ||
            frozen.gymRevision !in 1..MAX_REVISION ||
            frozen.sets.size !in 1..MAX_SETS ||
            frozen.sets.map{it.setKey}.toSet().size!=frozen.sets.size ||
            frozen.sets.any{set->
                set.workoutId!=frozen.workoutId ||
                set.gymId!=frozen.gymId ||
                set.gymRevision!=frozen.gymRevision ||
                MeasuredExerciseDomain.inspect(set) !is MeasurementPlanResult.Candidate
            })return null
        val initial=frozen.sets.associate{plan->
            plan.setKey to (MeasuredExerciseJournalDomain.begin(plan)
                ?:return null)
        }
        return MeasuredWorkoutJournal(frozen,0L,emptyList(),
            Collections.unmodifiableMap(initial),null)
    }

    fun reduce(
        original:MeasuredWorkoutJournal,
        input:MeasuredWorkoutOperation
    ):MeasuredWorkoutDecision {
        val operation=MeasuredWorkoutSnapshotDomain.operation(input)
        fun reject(reason:MeasuredWorkoutRefusal,
            setReason:MeasurementRefusal?=null)=
            MeasuredWorkoutDecision.Rejected(reason,setReason)
        if(!id.matches(operation.eventId))
            return reject(MeasuredWorkoutRefusal.INVALID_EVENT)
        // A retry is idempotent ONLY if the entire operation matches. The
        // event namespace is workout-global, not per exercise or per side.
        val old=original.receipts.firstOrNull{
            it.operation.eventId==operation.eventId
        }
        if(old!=null)return if(old.operation==operation)
            MeasuredWorkoutDecision.Proposed(original,true)
        else reject(MeasuredWorkoutRefusal.EVENT_COLLISION)
        if(original.revision!=operation.expectedRevision)
            return reject(MeasuredWorkoutRefusal.STALE_REVISION)
        if(original.finishedByEventId!=null)
            return reject(MeasuredWorkoutRefusal.FINISHED)
        if(original.receipts.size>=MAX_RECEIPTS ||
            original.revision>=MAX_REVISION)
            return reject(MeasuredWorkoutRefusal.TOO_MANY_RECEIPTS)

        val nextSetStates=when(operation) {
            is MeasuredWorkoutOperation.Record -> {
                val earlier=original.bySetKey[operation.setKey]
                    ?:return reject(MeasuredWorkoutRefusal.UNKNOWN_SET)
                // The single-set reducer remains the ONLY authority for
                // actual quantities, correction ancestry and equipment.
                val reduced=MeasuredExerciseJournalDomain.reduce(earlier,
                    MeasuredJournalOperation.Record(operation.eventId,
                        earlier.revision,operation.outcome,operation.actual,
                        operation.correctsEventId))
                val updated=(reduced as? MeasuredJournalDecision.Proposed)
                    ?.takeIf{!it.replayed}?.journal
                    ?:return reject(MeasuredWorkoutRefusal.SET_REJECTED,
                        (reduced as? MeasuredJournalDecision.Rejected)?.reason)
                Collections.unmodifiableMap(original.bySetKey+
                    (operation.setKey to updated))
            }
            is MeasuredWorkoutOperation.Finish -> {
                if(!original.canProposeFinish)
                    return reject(MeasuredWorkoutRefusal.INCOMPLETE)
                original.bySetKey
            }
        }
        val finished=when(operation) {
            is MeasuredWorkoutOperation.Finish ->operation.eventId
            else ->null
        }
        val receipt=MeasuredWorkoutReceipt(original.revision+1,operation)
        return MeasuredWorkoutDecision.Proposed(original.copy(
            revision=original.revision+1,
            receipts=Collections.unmodifiableList(original.receipts+receipt),
            bySetKey=nextSetStates,
            finishedByEventId=finished))
    }

    /** Cold replay validates the entire cross-set event stream and Finish. */
    fun replay(
        plan:MeasuredWorkoutPlan,
        receipts:List<MeasuredWorkoutReceipt>
    ):MeasuredWorkoutJournal? {
        if(receipts.size>MAX_RECEIPTS)return null
        var state=begin(plan) ?:return null
        for(receipt in receipts) {
            val proposed=reduce(state,receipt.operation)
                as? MeasuredWorkoutDecision.Proposed ?:return null
            if(proposed.replayed ||
                proposed.journal.revision!=receipt.revision)return null
            state=proposed.journal
        }
        return state
    }

    /** Owner verification remains the native caller's responsibility. */
    fun verified(journal:MeasuredWorkoutJournal):Boolean =
        replay(journal.approvedPlan,journal.receipts)==journal
}
