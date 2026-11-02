package io.github.extracteddeath.pursuitiron.domain

import org.junit.Assert.*
import org.junit.Test

/** Synthetic immutable issued plan and real reducer receipts. No engine math. */
class NativeNextSetPolicyTest {
    private val reference=ReadOnlyProgramReference("next-program",12,15,13,"f".repeat(64))
    private val session=JournalSessionKey(reference,"next-workout","next-day",1,"lb",1000)
    private val a=JournalSetSlot(0,0,"bench")
    private val b=JournalSetSlot(0,1,"bench")
    private val c=JournalSetSlot(1,0,"bench")
    private val d=JournalSetSlot(1,1,"bench")
    private val e=JournalSetSlot(2,0,"row")
    private val values=JournalSetValues("90","lb",6,2)
    private val slots=listOf(a,b,c,d,e)
    private val exercises=listOf(
        NativeWorkoutExercise(0,"bench","Bench",90,false,emptyList(),listOf(
            NativeWorkoutSet(a,"90","6","5-8","2","80 lb × 6"),
            NativeWorkoutSet(b,"90","6","5-8","2","80 lb × 6"))),
        NativeWorkoutExercise(1,"bench","Bench again",90,false,emptyList(),listOf(
            NativeWorkoutSet(c,"90","6","5-8","2",null),
            NativeWorkoutSet(d,"90","6","5-8","2",null))),
        NativeWorkoutExercise(2,"row","Row",75,false,emptyList(),listOf(
            NativeWorkoutSet(e,"45","10","8-12","2",null)))
    )
    private val plan=NativeWorkoutPlan("issued",session,"Training","Upper",exercises)
    private fun empty()=checkNotNull(WorkoutJournalPolicy.open(session,slots,0))
    private fun log(state: WorkoutJournalState,slot: JournalSetSlot,id: String,
        disposition: JournalSetDisposition=JournalSetDisposition.COMPLETED,
        replace: String?=null
    ): WorkoutJournalState {
        val observed=if(disposition==JournalSetDisposition.COMPLETED)values else null
        val intent=JournalSetIntent(id,slot,disposition,observed,replace)
        val receipt=JournalSetReceipt(session,id,slot,disposition,observed,
            state.revision,state.revision+1,replace)
        return checkNotNull(WorkoutJournalPolicy.afterPersistedSet(state,intent,receipt))
    }

    @Test fun currentAndUpNextFollowIssuedSlotsIncludingRepeatedMovements() {
        val initial=NativeNextSetPolicy.inspect(NativeWorkoutProgress(plan,empty()),0)
        assertEquals(a,initial?.current?.set?.slot)
        assertEquals(b,initial?.upNext?.set?.slot)
        assertEquals(5,initial?.remainingTotal)
        assertEquals(c,NativeNextSetPolicy.inspect(NativeWorkoutProgress(plan,empty()),1)?.current?.set?.slot)
        var state=log(empty(),a,"logged-a")
        assertEquals(b,NativeNextSetPolicy.inspect(NativeWorkoutProgress(plan,state),0)?.current?.set?.slot)
        assertEquals(c,NativeNextSetPolicy.inspect(NativeWorkoutProgress(plan,state),0)?.upNext?.set?.slot)
        state=log(state,b,"skipped-b",JournalSetDisposition.SKIPPED)
        val after=NativeNextSetPolicy.inspect(NativeWorkoutProgress(plan,state),0)
        assertNull(after?.current)
        assertEquals(c,after?.upNext?.set?.slot)
        assertEquals(1,after?.upNext?.exercisePosition)
        assertEquals("Bench again",after?.upNext?.exercise?.name)
        assertEquals("5-8",after?.upNext?.set?.targetReps)
        assertEquals("90",after?.upNext?.set?.load)
    }

    @Test fun wrapsWithinIssuedOrderAndKeepsCorrectionSlotResolved() {
        var state=log(empty(),a,"a-logged")
        state=log(state,b,"b-logged")
        state=log(state,c,"c-logged")
        val atTail=NativeNextSetPolicy.inspect(NativeWorkoutProgress(plan,state),2)
        assertEquals(e,atTail?.current?.set?.slot)
        assertEquals(d,atTail?.upNext?.set?.slot)
        state=log(state,c,"c-correction",replace="c-logged")
        assertEquals(d,NativeNextSetPolicy.inspect(NativeWorkoutProgress(plan,state),1)?.current?.set?.slot)
        assertEquals(e,NativeNextSetPolicy.inspect(NativeWorkoutProgress(plan,state),1)?.upNext?.set?.slot)
        state=log(state,d,"d-logged")
        assertNull(NativeNextSetPolicy.inspect(NativeWorkoutProgress(plan,state),2)?.upNext)
        assertEquals(e,NativeNextSetPolicy.inspect(NativeWorkoutProgress(plan,state),2)?.current?.set?.slot)
        state=log(state,e,"e-logged")
        val complete=NativeNextSetPolicy.inspect(NativeWorkoutProgress(plan,state),2)
        assertEquals(0,complete?.remainingTotal)
        assertNull(complete?.current)
        assertNull(complete?.upNext)
    }

    @Test fun focusCrossesExercisesOnlyAfterNewAcknowledgedFinalSet() {
        var state=empty()
        assertNull(NativeNextSetPolicy.advanceAfterAcknowledged(
            NativeWorkoutProgress(plan,state),0,null))
        state=log(state,a,"a-logged")
        assertNull(NativeNextSetPolicy.advanceAfterAcknowledged(
            NativeWorkoutProgress(plan,state),0,null))
        state=log(state,b,"b-skipped",JournalSetDisposition.SKIPPED)
        val resolved=NativeWorkoutProgress(plan,state)
        assertEquals(1,NativeNextSetPolicy.advanceAfterAcknowledged(resolved,0,"a-logged"))
        assertNull("Receipt replay cannot jump",NativeNextSetPolicy.advanceAfterAcknowledged(resolved,0,"b-skipped"))
        assertNull("Do not move manually selected other tabs",
            NativeNextSetPolicy.advanceAfterAcknowledged(resolved,1,"a-logged"))
        assertNull("Do not move during unrelated history changes",
            NativeNextSetPolicy.advanceAfterAcknowledged(resolved,2,"a-logged"))
        state=log(state,b,"b-corrected",JournalSetDisposition.COMPLETED,replace="b-skipped")
        assertNull("Correction cannot auto-navigate",
            NativeNextSetPolicy.advanceAfterAcknowledged(NativeWorkoutProgress(plan,state),0,"b-skipped"))

        state=log(state,e,"row-logged")
        assertNull("Different exercise receipt cannot move selected bench",
            NativeNextSetPolicy.advanceAfterAcknowledged(NativeWorkoutProgress(plan,state),1,"b-corrected"))
        state=log(state,c,"second-bench-1")
        assertNull(NativeNextSetPolicy.advanceAfterAcknowledged(
            NativeWorkoutProgress(plan,state),1,"row-logged"))
        state=log(state,d,"second-bench-2")
        assertNull("No remaining exercise after final saved set",
            NativeNextSetPolicy.advanceAfterAcknowledged(
                NativeWorkoutProgress(plan,state),1,"second-bench-1"))
    }

    @Test fun staleSessionOrUnmatchedIssuedSlotsFailClosed() {
        val state=empty()
        assertNull(NativeNextSetPolicy.inspect(NativeWorkoutProgress(plan,state),-1))
        assertNull(NativeNextSetPolicy.inspect(NativeWorkoutProgress(plan,state),3))
        assertNull(NativeNextSetPolicy.inspect(NativeWorkoutProgress(
            NativeWorkoutPlan("issued",session.copy(workoutId="other"),
                "Training","Upper",exercises),state),0))
        val wrong=NativeWorkoutPlan("issued",session,"Training","Upper",listOf(
            NativeWorkoutExercise(0,"bench","Bench",90,false,emptyList(),listOf(
                NativeWorkoutSet(b,"90","6","5-8","2",null),
                NativeWorkoutSet(a,"90","6","5-8","2",null))),
            exercises[1],exercises[2]))
        assertNull(NativeNextSetPolicy.inspect(NativeWorkoutProgress(wrong,state),0))
    }
}
