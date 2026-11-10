package io.github.extracteddeath.pursuitiron.rebuild

/**
 * Deliberate, read-only copy of an actually completed LAST set to a fresh
 * editable journal draft. The reference domain is responsible for verified
 * athlete, same-program-day, gym, exercise/slot and load-basis matching.
 *
 * Do not convert this to coaching advice or infer an actual RIR from targets.
 * The athlete must still tap Complete to write a set to the native journal.
 */
object WorkoutLastDraftDomain {
    fun reviewedActual(
        history:WorkoutLastReferenceResult?,
        key:WorkoutReferenceKey,
        currentLoad:DraftSetLoad
    ):KotlinPerformedSet? {
        val record=(history as? WorkoutLastReferenceResult.Ready)
            ?.sets?.get(key) ?:return null
        if(record.outcome!=KotlinJournalSetOutcome.COMPLETED)return null
        val actual=record.actual ?:return null
        // Check the reference's provenance basis as well as the reported
        // actual. A barbell/smith/machine or per-hand load is never exchanged.
        val sameBasis=when(currentLoad) {
            DraftSetLoad.ExplicitBodyweight -> record.loadBasis==null
            is DraftSetLoad.Verified -> record.loadBasis==currentLoad.basis
        }
        if(!sameBasis ||
            !CustomWorkoutJournalDomain.actualValid(
                currentLoad,KotlinJournalSetOutcome.COMPLETED,actual))
            return null
        return actual
    }
}
