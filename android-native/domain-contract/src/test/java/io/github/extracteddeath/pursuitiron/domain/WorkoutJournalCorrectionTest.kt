package io.github.extracteddeath.pursuitiron.domain

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.security.MessageDigest

class WorkoutJournalCorrectionTest {
    private class Drive : WorkoutJournalBytes {
        var raw: ByteArray?=null;var writes=0;var failBefore=false;var failAfter=false
        override fun read()=raw?.copyOf()
        override fun commitAtomically(bytes: ByteArray) {
            if(failBefore)throw java.io.IOException("Synthetic disk full")
            raw=bytes.copyOf();writes++
            if(failAfter)throw java.io.IOException("Synthetic lost acknowledgement")
        }
    }
    private val session=JournalSessionKey(ReadOnlyProgramReference("program",1,1,13,"a".repeat(64)),
        "workout","day",1,"kg",1000)
    private val slot=JournalSetSlot(0,0,"bb-bench")
    private val value=JournalSetValues("100","kg",6,null)
    private fun done(op: String,s: JournalSetSlot=slot)=JournalSetIntent(op,s,JournalSetDisposition.COMPLETED,value)

    @Test fun correctionRetainsOriginalReceiptAndExactOldRetriesCannotUndoNewerValues() {
        val drive=Drive();var journal=WorkoutJournalPersistence(drive)
        assertNotNull(journal.begin(session,listOf(slot),0))
        val original=done("first")
        var state=checkNotNull(journal.recordSet(session,0,original))
        assertEquals(2,JSONObject(String(checkNotNull(drive.raw),Charsets.UTF_8)).getInt("format"))
        val edit=original.copy(operationId="edit",values=value.copy(load="90",reps=8,rir=2),replacesOperationId="first")
        drive.failBefore=true
        assertTrue(runCatching { journal.recordSet(session,state.revision,edit) }.isFailure)
        assertEquals(value,checkNotNull(journal.recover()).completed.single().intent.values)
        drive.failBefore=false;drive.failAfter=true
        assertTrue(runCatching { journal.recordSet(session,1,edit) }.isFailure)
        drive.failAfter=false;journal=WorkoutJournalPersistence(drive)
        val writes=drive.writes
        state=checkNotNull(journal.recordSet(session,1,edit))
        assertEquals(writes,drive.writes);assertEquals(2,state.events.size);assertEquals(1,state.completed.size)
        assertEquals(0,state.unresolvedCount);assertEquals(edit,state.completed.single().intent)
        assertEquals(original,state.events[0].intent)
        assertEquals(edit,checkNotNull(journal.recordSet(session,0,original)).completed.single().intent)
        assertNull(journal.recordSet(session,2,edit.copy(operationId="stale",replacesOperationId="first")))
        assertNull(journal.recordSet(session,1,edit.copy(values=value)))
        assertEquals(writes,drive.writes)
        val skipped=JournalSetIntent("skip",slot,JournalSetDisposition.SKIPPED,null,"edit")
        state=checkNotNull(journal.recordSet(session,2,skipped))
        assertEquals(JournalSetDisposition.SKIPPED,state.completed.single().intent.disposition)
        val restored=done("restore").copy(replacesOperationId="skip",values=value.copy(load="0",rir=0))
        state=checkNotNull(journal.recordSet(session,3,restored))
        assertEquals(4,state.events.size);assertEquals(1,state.completed.size)
        assertNull(journal.finish(session,4,JournalFinishIntent("first",62000,61000)))
        state=checkNotNull(journal.finish(session,4,JournalFinishIntent("finish",62000,61000)))
        val wire=JSONObject(checkNotNull(journal.finishedHandoff(session,state.revision)))
        assertEquals(3,wire.getInt("format"));assertEquals(4,wire.getJSONArray("events").length())
        assertTrue(wire.getJSONArray("events").getJSONObject(0).isNull("replacesOperationId"))
        assertEquals("skip",wire.getJSONArray("events").getJSONObject(3).getString("replacesOperationId"))
        assertNull(journal.recordSet(session,5,done("late").copy(replacesOperationId="restore")))
        assertEquals(5,checkNotNull(WorkoutJournalPersistence(drive).recover()).revision)
    }

    @Test fun forgedCorrectionChainCannotRecoverEvenWhenEnvelopeChecksumWasReplaced() {
        val drive=Drive();val journal=WorkoutJournalPersistence(drive)
        journal.begin(session,listOf(slot),0);journal.recordSet(session,0,done("first"))
        assertNotNull(journal.recordSet(session,1,done("edit").copy(replacesOperationId="first")))
        val valid=checkNotNull(drive.raw).copyOf()
        for(duplicate in listOf(false,true)) {
            val envelope=JSONObject(String(valid,Charsets.UTF_8))
            val payload=JSONObject(envelope.getString("payload"));val events=payload.getJSONArray("events")
            if(duplicate)events.put(JSONObject(events.getJSONObject(0).toString()))
            else events.getJSONObject(1).put("replacesOperationId","unrelated-operation")
            val text=payload.toString()
            val hash=MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it.toInt() and 255) }
            drive.raw=envelope.put("payload",text).put("sha256",hash).toString().toByteArray()
            val preserved=drive.raw!!.copyOf()
            assertTrue(runCatching { journal.recover() }.isFailure)
            assertTrue(runCatching { journal.begin(session,listOf(slot),0) }.isFailure)
            assertArrayEquals(preserved,drive.raw)
        }
    }

    @Test fun acknowledgedCorrectionRequiresTheExactPriorReceipt() {
        val initial=checkNotNull(WorkoutJournalPolicy.open(session,listOf(slot),0))
        val intent=done("first")
        val state=checkNotNull(WorkoutJournalPolicy.afterPersistedSet(initial,intent,
            JournalSetReceipt(session,"first",slot,intent.disposition,value,0,1)))
        val edit=done("edit").copy(replacesOperationId="first")
        val ack=JournalSetReceipt(session,"edit",slot,edit.disposition,value,1,2,"first")
        assertNull(WorkoutJournalPolicy.afterPersistedSet(state,edit,ack.copy(replacesOperationId="other")))
        assertNull(WorkoutJournalPolicy.afterPersistedSet(state,edit.copy(replacesOperationId=null),ack))
        val corrected=checkNotNull(WorkoutJournalPolicy.afterPersistedSet(state,edit,ack))
        assertEquals(1,corrected.completed.size);assertEquals(2,corrected.events.size)
    }

    @Test(timeout=60000) fun editCapacityReservesEnoughBytesToResolveEveryRemainingSlotAndFinish() {
        val drive=Drive();val journal=WorkoutJournalPersistence(drive)
        val slots=(0 until 20).map { JournalSetSlot(0,it,"bb-bench") }
        var state=checkNotNull(journal.begin(session,slots,0))
        state=checkNotNull(journal.recordSet(session,0,done("first")))
        var refused=false
        for(i in 0 until WorkoutJournalPolicy.MAX_EVENTS) {
            val edit=done("edit-$i-"+"x".repeat(110)).copy(
                replacesOperationId=state.completed[0].intent.operationId,values=value.copy(load="99999.999",reps=100,rir=10))
            val next=journal.recordSet(session,state.revision,edit)
            if(next==null) { refused=true;break }
            state=next
        }
        assertTrue(refused)
        for(s in slots.drop(1))state=checkNotNull(journal.recordSet(session,state.revision,done("finish-set-${s.setIndex}",s)))
        assertEquals(0,state.unresolvedCount)
        assertNotNull(journal.finish(session,state.revision,JournalFinishIntent("finish",62000,61000)))
    }
}
