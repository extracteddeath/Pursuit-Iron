package io.github.extracteddeath.pursuitiron.domain

import org.junit.Assert.*
import org.junit.Test

/** Real atomic journal writes, with process-reopen and lost-ack simulation. */
class NativeSessionSkipPolicyTest {
    private class MemoryBytes : WorkoutJournalBytes {
        var current: ByteArray?=null
        var failAfterCommit=false
        override fun read()=current?.copyOf()
        override fun commitAtomically(bytes: ByteArray) {
            current=bytes.copyOf()
            if(failAfterCommit)throw IllegalStateException("ack lost after atomic write")
        }
    }
    private val session=JournalSessionKey(ReadOnlyProgramReference(
        "program-1",42,100,13,"a".repeat(64)),"workout-1","day-1",1,"lb",1000)
    private val a=JournalSetSlot(0,0,"bb-bench")
    private val b=JournalSetSlot(0,1,"bb-bench")
    private val extension=JournalSetSlot(0,2,"bb-bench")
    private val other=JournalSetSlot(1,0,"cable-row")
    private val slots=listOf(a,b,extension,other)
    private val value=JournalSetValues("185","lb",8,2)
    private fun intent(slot: JournalSetSlot,op: String)=JournalSetIntent(
        op,slot,JournalSetDisposition.SKIPPED,null)

    @Test fun onlyUnresolvedSlotsAreSelectedAndCompletedObservationsSurvive() {
        val drive=MemoryBytes()
        val owner=WorkoutJournalPersistence(drive)
        assertNotNull(owner.begin(session,slots,0))
        val completed=checkNotNull(owner.recordSet(session,0,
            JournalSetIntent("logged",a,JournalSetDisposition.COMPLETED,value)))
        assertEquals(listOf(b,extension),NativeSessionSkipPolicy.remaining(completed,0))
        assertEquals(listOf(other),NativeSessionSkipPolicy.remaining(completed,1))
        val first=checkNotNull(owner.recordSet(session,completed.revision,intent(b,"skip-b")))
        assertEquals(listOf(extension),NativeSessionSkipPolicy.remaining(first,0))
        val recovered=checkNotNull(WorkoutJournalPersistence(drive).recover())
        assertEquals(listOf(extension),NativeSessionSkipPolicy.remaining(recovered,0))
        val ended=checkNotNull(WorkoutJournalPersistence(drive).recordSet(
            session,recovered.revision,intent(extension,"skip-extension")))
        assertTrue(NativeSessionSkipPolicy.remaining(ended,0).isEmpty())
        assertEquals(1,ended.unresolvedCount)
        assertEquals(JournalSetDisposition.COMPLETED,ended.completed.single { it.intent.slot==a }.intent.disposition)
        assertEquals(value,ended.completed.single { it.intent.slot==a }.receipt.storedValues)
        assertEquals(2,ended.completed.count { it.intent.disposition==JournalSetDisposition.SKIPPED })
        assertEquals(listOf(other),NativeSessionSkipPolicy.remaining(ended,1))
    }

    @Test fun lostAcknowledgementCanResumeWithoutDoubleSkippingAnExtension() {
        val drive=MemoryBytes()
        val owner=WorkoutJournalPersistence(drive)
        assertNotNull(owner.begin(session,slots,0))
        drive.failAfterCommit=true
        assertThrows(IllegalStateException::class.java) {
            owner.recordSet(session,0,intent(a,"skip-a"))
        }
        drive.failAfterCommit=false
        val restart=WorkoutJournalPersistence(drive)
        val restored=checkNotNull(restart.recover())
        assertEquals(1,restored.revision)
        assertEquals(listOf(b,extension),NativeSessionSkipPolicy.remaining(restored,0))
        val second=checkNotNull(restart.recordSet(session,restored.revision,intent(b,"skip-b")))
        val third=checkNotNull(restart.recordSet(session,second.revision,intent(extension,"skip-extension")))
        assertEquals(3,third.revision)
        assertEquals(3,third.completed.map { it.intent.slot }.distinct().size)
        assertEquals(1,third.unresolvedCount)
        assertTrue(NativeSessionSkipPolicy.remaining(third,0).isEmpty())
    }

    @Test fun invalidAndFinishedExerciseRequestsFailClosed() {
        val open=checkNotNull(WorkoutJournalPolicy.open(session,slots,0))
        for(index in listOf(-1,2,48))assertThrows(IllegalArgumentException::class.java) {
            NativeSessionSkipPolicy.remaining(open,index)
        }
        val ended=open
        var current=ended
        for((n,slot) in slots.withIndex()) {
            val value=intent(slot,"skip-$n")
            current=checkNotNull(WorkoutJournalPolicy.afterPersistedSet(current,value,
                JournalSetReceipt(session,value.operationId,slot,JournalSetDisposition.SKIPPED,null,n.toLong(),n.toLong()+1)))
        }
        val fin=JournalFinishIntent("finish",61000,60000)
        val finished=checkNotNull(WorkoutJournalPolicy.afterPersistedFinish(current,fin,
            JournalFinishReceipt(session,"finish",4,5,61000,60000)))
        assertThrows(IllegalArgumentException::class.java) {
            NativeSessionSkipPolicy.remaining(finished,0)
        }
    }
}
