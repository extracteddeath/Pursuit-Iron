package io.github.extracteddeath.pursuitiron.domain

import org.junit.Assert.*
import org.junit.Test

/** Synthetic workout and persisted receipts; no timer or training writes. */
class NativeRestCuePolicyTest {
    private val reference=ReadOnlyProgramReference("plan-rest",100,100,13,"e".repeat(64))
    private val session=JournalSessionKey(reference,"workout-rest","day-rest",1,"lb",1000)
    private val first=JournalSetSlot(0,0,"bb-bench")
    private val second=JournalSetSlot(0,1,"bb-bench")
    private val extension=JournalSetSlot(0,2,"bb-bench")
    private val nextExercise=JournalSetSlot(1,0,"db-row")
    private val observed=JournalSetValues("135","lb",8,2)

    private fun plan(superset: Boolean=false): NativeWorkoutPlan {
        val sets=listOf(
            NativeWorkoutSet(first,"135","8","6-10","2",null),
            NativeWorkoutSet(second,"135","8","6-10","2",null),
            NativeWorkoutSet(extension,"100","12","12-15","2",null,
                isExtension=true,kind="drop",extensionRestSeconds=25))
        return NativeWorkoutPlan("issued",session,"Program","Day",
            listOf(NativeWorkoutExercise(0,"bb-bench","Bench",90,superset,emptyList(),sets),
                NativeWorkoutExercise(1,"db-row","Row",120,false,emptyList(),
                    listOf(NativeWorkoutSet(nextExercise,"45","10","8-12","2",null)))))
    }
    private fun fresh()=checkNotNull(WorkoutJournalPolicy.open(
        session,listOf(first,second,extension,nextExercise),0))

    private fun add(state: WorkoutJournalState,slot: JournalSetSlot,operationId: String,
        disposition: JournalSetDisposition=JournalSetDisposition.COMPLETED,
        correctionOf: String?=null
    ): WorkoutJournalState {
        val values=if(disposition==JournalSetDisposition.COMPLETED)observed else null
        val intent=JournalSetIntent(operationId,slot,disposition,values,correctionOf)
        val receipt=JournalSetReceipt(session,operationId,slot,disposition,values,
            state.revision,state.revision+1,correctionOf)
        return checkNotNull(WorkoutJournalPolicy.afterPersistedSet(state,intent,receipt))
    }

    @Test fun onlyFreshConfirmedWorkingSetProducesCue() {
        val initial=fresh()
        assertNull(NativeRestCuePolicy.latest(NativeWorkoutProgress(plan(),initial),0))
        val logged=add(initial,first,"op-1")
        assertEquals(NativeRestCue("op-1",90,0),
            NativeRestCuePolicy.latest(NativeWorkoutProgress(plan(),logged),0))
        assertNull(NativeRestCuePolicy.latest(NativeWorkoutProgress(plan(),logged),1))
        assertNull(NativeRestCuePolicy.latest(NativeWorkoutProgress(plan(superset=true),logged),0))
        assertNull(NativeRestCuePolicy.latest(NativeWorkoutProgress(plan().let { p->
            NativeWorkoutPlan(p.issuedJson,session.copy(workoutId="wrong"),
                p.programName,p.dayLabel,p.exercises)
        },logged),0))
    }

    @Test fun extensionUsesItsIssuedRestAndCorrectionsNeverStartNewRest() {
        var state=add(fresh(),first,"first")
        state=add(state,second,"second")
        assertEquals(NativeRestCue("second",25,0),
            NativeRestCuePolicy.latest(NativeWorkoutProgress(plan(),state),0))
        state=add(state,second,"correct-second",correctionOf="second")
        assertNull(NativeRestCuePolicy.latest(NativeWorkoutProgress(plan(),state),0))
        state=add(state,extension,"extension")
        assertEquals(NativeRestCue("extension",90,0),
            NativeRestCuePolicy.latest(NativeWorkoutProgress(plan(),state),0))
        state=add(state,nextExercise,"row")
        assertNull(NativeRestCuePolicy.latest(NativeWorkoutProgress(plan(),state),1))
    }

    @Test fun skipsDoNotStartRestAndNoCueAfterAllSetsResolved() {
        var state=add(fresh(),first,"skip",JournalSetDisposition.SKIPPED)
        assertNull(NativeRestCuePolicy.latest(NativeWorkoutProgress(plan(),state),0))
        state=add(state,second,"set")
        state=add(state,extension,"extension")
        state=add(state,nextExercise,"last")
        assertEquals(0,state.unresolvedCount)
        assertNull(NativeRestCuePolicy.latest(NativeWorkoutProgress(plan(),state),1))
    }
}
