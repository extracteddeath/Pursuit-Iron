package io.github.extracteddeath.pursuitiron.domain

import org.junit.Assert.*
import org.junit.Test

/** Synthetic persistence receipts. No actual workout history is written. */
class WorkoutJournalPolicyTest {
    private val reference = ReadOnlyProgramReference(
        "synthetic-program", 42, 100, 13, "a".repeat(64))
    private val session = JournalSessionKey(reference,"synthetic-workout","synthetic-day",1,"lb",1000)
    private val s1=JournalSetSlot(0,0,"bb-bench")
    private val s2=JournalSetSlot(0,1,"bb-bench")
    private val s3=JournalSetSlot(1,0,"cable-row")
    private val values=JournalSetValues("185.5","lb",8,2)
    private fun initial()=checkNotNull(WorkoutJournalPolicy.open(session,listOf(s1,s2,s3),20))
    private fun ack(state: WorkoutJournalState, slot: JournalSetSlot, op: String,
                    skipped: Boolean = false): WorkoutJournalState? {
        val outcome=if (skipped) JournalSetDisposition.SKIPPED else JournalSetDisposition.COMPLETED
        val v=if (skipped) null else values
        return WorkoutJournalPolicy.afterPersistedSet(state,
            JournalSetIntent(op,slot,outcome,v),
            JournalSetReceipt(session,op,slot,outcome,v,state.revision,state.revision+1))
    }

    @Test fun onlyMatchingDurableReceiptsAdvanceVisibleWorkout() {
        val pending=initial()
        val intent=JournalSetIntent("op-a",s1,JournalSetDisposition.COMPLETED,values)
        val written=JournalSetReceipt(session,"op-a",s1,JournalSetDisposition.COMPLETED,values,20,21)
        assertNull(WorkoutJournalPolicy.afterPersistedSet(
            pending,intent,written.copy(storedValues=values.copy(load="190"))))
        assertEquals(3,pending.unresolvedCount)
        val saved=checkNotNull(WorkoutJournalPolicy.afterPersistedSet(pending,intent,written))
        assertEquals(2,saved.unresolvedCount)
        assertEquals(21L,saved.revision)
        assertEquals(values,saved.completed.single().receipt.storedValues)
        assertSame(saved,WorkoutJournalPolicy.afterPersistedSet(saved,intent,written))
        val another=checkNotNull(ack(saved,s2,"op-b"))
        assertSame(another,WorkoutJournalPolicy.afterPersistedSet(another,intent,written))
        assertNull(ack(saved,s1,"op-duplicate-slot"))
    }

    @Test fun rejectStaleOutOfOrderOrForgedAcknowledgements() {
        val old=initial()
        val intent=JournalSetIntent("op-a",s1,JournalSetDisposition.COMPLETED,values)
        val written=JournalSetReceipt(session,"op-a",s1,JournalSetDisposition.COMPLETED,values,20,21)
        assertNull(WorkoutJournalPolicy.afterPersistedSet(old,intent,
            written.copy(fromRevision=19)))
        assertNull(WorkoutJournalPolicy.afterPersistedSet(old,intent,
            written.copy(toRevision=23)))
        assertNull(WorkoutJournalPolicy.afterPersistedSet(old,intent,
            written.copy(session=session.copy(workoutId="other"))))
        assertNull(WorkoutJournalPolicy.afterPersistedSet(old,intent,
            written.copy(session=session.copy(
                programRevision=reference.copy(programUpdatedAt=43)))))
        assertNull(WorkoutJournalPolicy.afterPersistedSet(old,intent,
            written.copy(slot=s2)))
        assertNull(WorkoutJournalPolicy.afterPersistedSet(old,
            intent.copy(values=values.copy(reps=9)),written))
        assertNull(ack(old,JournalSetSlot(2,0,"curl"),"not-a-slot"))
    }

    @Test fun finishRequiresCommittedOrSkippedSetsAndCannotRepeat() {
        var state=initial()
        val finish=JournalFinishIntent("finish-op",61000,60000)
        assertNull(WorkoutJournalPolicy.afterPersistedFinish(
            state,finish,JournalFinishReceipt(session,"finish-op",20,21,61000,60000)))
        state=checkNotNull(ack(state,s1,"a"))
        state=checkNotNull(ack(state,s2,"b"))
        assertNull(WorkoutJournalPolicy.afterPersistedFinish(
            state,finish,JournalFinishReceipt(session,"finish-op",22,23,61000,60000)))
        state=checkNotNull(ack(state,s3,"c",skipped=true))
        assertEquals(0,state.unresolvedCount)
        val saved=JournalFinishReceipt(session,"finish-op",23,24,61000,60000)
        assertNull(WorkoutJournalPolicy.afterPersistedFinish(state,finish,
            saved.copy(completedAt=62000)))
        assertNull(WorkoutJournalPolicy.afterPersistedFinish(state,finish,
            saved.copy(durationMs=59000)))
        val completed=checkNotNull(WorkoutJournalPolicy.afterPersistedFinish(state,finish,saved))
        assertTrue(completed.isFinished)
        assertEquals(24L,completed.revision)
        assertSame(completed,WorkoutJournalPolicy.afterPersistedFinish(completed,finish,saved))
        assertNull(WorkoutJournalPolicy.afterPersistedFinish(completed,
            JournalFinishIntent("second-finish",61000,60000),saved.copy(operationId="second-finish")))
        assertNull(ack(completed,s1,"after-finish"))
    }

    @Test fun rejectInvalidSetObservationsAndMutationOfPlannedSlots() {
        val state=initial()
        for (bad in listOf(
            values.copy(load="-5"),
            values.copy(load="185.5.5"),
            values.copy(load="185.0",unit=null),
            values.copy(unit="stones"),
            values.copy(reps=101),
            values.copy(rir=11)
        )) {
            val intent=JournalSetIntent("bad",s1,JournalSetDisposition.COMPLETED,bad)
            assertNull(WorkoutJournalPolicy.afterPersistedSet(state,intent,
                JournalSetReceipt(session,"bad",s1,JournalSetDisposition.COMPLETED,bad,20,21)))
        }
        assertNull(WorkoutJournalPolicy.open(session, listOf(s1,s1),20))
        assertNull(WorkoutJournalPolicy.open(session, emptyList(),20))
        assertNull(WorkoutJournalPolicy.open(
            session.copy(programRevision=reference.copy(snapshotSha256="bad")),listOf(s1),20))
        val mutable=mutableListOf(s1,s2)
        val opened=checkNotNull(WorkoutJournalPolicy.open(session,mutable,20))
        mutable.clear()
        assertEquals(2,opened.slots.size)
        assertTrue(runCatching {
            @Suppress("UNCHECKED_CAST")
            (opened.slots as MutableList<JournalSetSlot>).clear()
        }.isFailure)
    }
}
