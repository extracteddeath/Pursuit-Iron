package io.github.extracteddeath.pursuitiron.domain

import org.junit.Assert.*
import org.junit.Test

class WorkoutJournalNoteTest {
    private class Drive: WorkoutJournalBytes {
        var bytes: ByteArray?=null;var writes=0;var before=false;var after=false
        override fun read()=bytes?.copyOf()
        override fun commitAtomically(bytes: ByteArray) {
            if(before)throw java.io.IOException("disk full")
            this.bytes=bytes.copyOf();writes++
            if(after)throw java.io.IOException("lost acknowledgement")
        }
    }
    private val session=JournalSessionKey(ReadOnlyProgramReference("program",100,100,13,"a".repeat(64)),
        "workout","day",1,"lb",1000)
    private val slot=JournalSetSlot(0,0,"bench")

    @Test fun notesRetainTheirOwnIdentityAcrossSetsReplacementClearAndFinish() {
        val drive=Drive();var journal=WorkoutJournalPersistence(drive)
        assertNotNull(journal.begin(session,listOf(slot),0))
        val first=JournalSessionNote("note-one","Felt good\nUse the lower seat next time.",1100)
        var state=checkNotNull(journal.recordNote(session,0,first))
        assertEquals(0L,state.revision);assertEquals(1,state.unresolvedCount)
        state=checkNotNull(journal.recordSet(session,0,JournalSetIntent("set-one",slot,
            JournalSetDisposition.COMPLETED,JournalSetValues("100","lb",6,2))))
        assertEquals(first,state.note);assertEquals(1L,state.revision)
        val writes=drive.writes
        assertNotNull(journal.recordNote(session,0,first));assertEquals(writes,drive.writes)
        val second=JournalSessionNote("note-two","Changed session note",1200,first.operationId)
        assertNull(journal.recordNote(session,0,second))
        assertNotNull(journal.recordNote(session,1,second))
        journal=WorkoutJournalPersistence(drive)
        assertEquals(second,checkNotNull(journal.recover()).note)
        assertNull(journal.recordNote(session,1,first))
        val clear=JournalSessionNote("note-clear","",1300,second.operationId)
        assertNotNull(journal.recordNote(session,1,clear))
        assertNull(journal.finish(session,1,JournalFinishIntent("too-early",1250,250)))
        state=checkNotNull(journal.finish(session,1,JournalFinishIntent("finish",1500,500)))
        assertEquals(clear,state.note);assertEquals(2L,state.revision)
        val handoff=org.json.JSONObject(checkNotNull(journal.finishedHandoff(session,2)))
        assertEquals(5,handoff.getInt("format"));assertEquals("",handoff.getJSONObject("note").getString("text"))
        assertEquals(1,handoff.getJSONArray("events").length())
        assertNull(journal.recordNote(session,2,JournalSessionNote("after-finish","Not allowed",1600,clear.operationId)))
    }

    @Test fun noteWritesRefuseDiskFullAndRecoverAnExactLostAcknowledgement() {
        val drive=Drive();var journal=WorkoutJournalPersistence(drive)
        assertNotNull(journal.begin(session,listOf(slot),0))
        val note=JournalSessionNote("note-durable","Private session note 🏋️",1100)
        drive.before=true
        assertTrue(runCatching {journal.recordNote(session,0,note)}.isFailure)
        assertNull(checkNotNull(journal.recover()).note)
        drive.before=false;drive.after=true
        assertTrue(runCatching {journal.recordNote(session,0,note)}.isFailure)
        drive.after=false;journal=WorkoutJournalPersistence(drive)
        val writes=drive.writes
        assertEquals(note,checkNotNull(journal.recover()).note)
        assertNotNull(journal.recordNote(session,0,note));assertEquals(writes,drive.writes)
        assertNull(WorkoutJournalPersistence(Drive()).begin(session.copy(workoutId="another-workout"),listOf(slot),0)?.note)
    }

    @Test fun notesRejectMalformedTextStaleClocksForeignSessionsAndSetOperationCollisions() {
        val drive=Drive();val journal=WorkoutJournalPersistence(drive)
        assertNotNull(journal.begin(session,listOf(slot),0))
        for(text in listOf("x".repeat(2001),"bad\u0000text","bad\rtext","\uD800"))
            assertNull(journal.recordNote(session,0,JournalSessionNote("note-invalid",text,1100)))
        assertTrue(JournalSessionNoteCodec.validText("line one\nline two\t🙂"))
        assertNull(journal.recordNote(session.copy(workoutId="foreign"),0,JournalSessionNote("note","valid",1100)))
        assertNull(journal.recordNote(session,0,JournalSessionNote("note","before workout",999)))
        assertNotNull(journal.recordNote(session,0,JournalSessionNote("note","first",1100)))
        assertNull(journal.recordNote(session,0,JournalSessionNote("note-next","stale clock",1100,"note")))
        assertNull(journal.recordSet(session,0,JournalSetIntent("note",slot,JournalSetDisposition.SKIPPED,null)))
        assertNotNull(journal.recordSet(session,0,JournalSetIntent("set-one",slot,JournalSetDisposition.SKIPPED,null)))
        assertNull(journal.recordNote(session,1,JournalSessionNote("set-one","collision",1200,"note")))
        assertEquals("first",checkNotNull(journal.recover()).note?.text)
    }
}
