package io.github.extracteddeath.pursuitiron.domain

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.security.MessageDigest

class NativeExerciseNotesTest {
    private class Bytes: WorkoutJournalBytes {
        var raw: ByteArray?=null;var writes=0;var failBefore=false;var failAfter=false
        override fun read()=raw?.copyOf()
        override fun commitAtomically(bytes: ByteArray) {
            if(failBefore)error("Synthetic full disk")
            writes++;raw=bytes.copyOf()
            if(failAfter)error("Synthetic lost acknowledgement")
        }
    }
    private val session=JournalSessionKey(ReadOnlyProgramReference("program-pinned",42,100,13,"a".repeat(64)),
        "workout-pinned","day-pinned",1,"lb",1000)
    private val context=NativePinnedNote("Old cue",mapOf("legacy" to "7"),
        listOf(NativeSetupField("hooks","J-hooks",false),NativeSetupField("legacy","legacy",true)))
    private val plan=NativeWorkoutPlan("{}",session,"Pinned test","Day 1",listOf(
        NativeWorkoutExercise(0,"bb-bench","Bench",120,false,emptyList(),
            listOf(NativeWorkoutSet(JournalSetSlot(0,0,"bb-bench"),"40","6","6",null,null)),pinnedNote=context),
        NativeWorkoutExercise(1,"bb-bench","Bench again",120,false,emptyList(),
            listOf(NativeWorkoutSet(JournalSetSlot(1,0,"bb-bench"),"40","6","6",null,null)),pinnedNote=context)
    ),tracksWarmups=true)
    private fun edit(op: String="pin-one",text: String="Pause on chest",at: Long=2000,replaces: String?=null)=
        JournalExerciseNote(op,"bb-bench",text,mapOf("legacy" to "8"),at,replaces)

    @Test fun lostAckRecoversDetachedValuesAndExactRetriesWithoutAdvancingSetProgress() {
        val bytes=Bytes();var journal=WorkoutJournalPersistence(bytes)
        journal.begin(session,plan.slots,0,true)
        val mutable=linkedMapOf("legacy" to "8");val command=edit().copy(setup=mutable)
        bytes.failAfter=true
        assertTrue(runCatching {journal.recordExerciseNote(plan,0,0,command)}.isFailure)
        bytes.failAfter=false;mutable["legacy"]="changed after save"
        journal=WorkoutJournalPersistence(bytes)
        val restored=checkNotNull(journal.recover())
        assertEquals(listOf(edit()),restored.exerciseNotes);assertEquals(0L,restored.revision)
        assertEquals(plan.slots.size,restored.unresolvedCount)
        assertEquals("8",NativeExerciseNotePolicy.resolved(plan,restored.exerciseNotes,"bb-bench")?.setup?.get("legacy"))
        val writes=bytes.writes
        assertNotNull(journal.recordExerciseNote(plan,0,0,edit()));assertEquals(writes,bytes.writes)
        assertNull(journal.recordExerciseNote(plan,0,0,edit(text="different retry")))
        assertTrue(runCatching {(restored.exerciseNotes[0].setup as MutableMap)["legacy"]="bad"}.isFailure)
        val envelope=JSONObject(String(checkNotNull(bytes.raw),Charsets.UTF_8))
        assertEquals(7,envelope.getInt("format"))
    }

    @Test fun staleEditorsWrongCapabilitiesClocksAndUnknownFieldsNeverWrite() {
        val bytes=Bytes();val journal=WorkoutJournalPersistence(bytes);journal.begin(session,plan.slots,0,true)
        assertNotNull(journal.recordExerciseNote(plan,0,0,edit()))
        val writes=bytes.writes
        val cases=listOf(edit("two",replaces=null),edit("two",at=2000,replaces="pin-one"),
            edit("two",replaces="pin-one").copy(exerciseId="not-issued"),
            edit("two",replaces="pin-one").copy(setup=mapOf("invented" to "3")),
            edit("two",replaces="pin-one").copy(text="x".repeat(2001)),
            edit("two",replaces="pin-one").copy(setup=mapOf("legacy" to "x".repeat(201))),
            edit("two",at=999,replaces="pin-one"),edit("two",text="\uD800",replaces="pin-one"))
        for(command in cases)assertNull(journal.recordExerciseNote(plan,0,1,command))
        assertNull(journal.recordExerciseNote(plan,1,1,edit("two",at=3000,replaces="pin-one")))
        assertNull(journal.recordExerciseNote(plan,0,0,edit("two",at=3000,replaces="pin-one")))
        val legacy=NativeWorkoutPlan("{}",session,"Old","Day",plan.exercises)
        assertNull(journal.recordExerciseNote(legacy,0,1,edit("two",at=3000,replaces="pin-one")))
        assertEquals(writes,bytes.writes)
    }

    @Test fun editsSurviveOtherJournalOperationsAndCannotShareTheirIdentitiesOrPredateFinish() {
        val bytes=Bytes();val journal=WorkoutJournalPersistence(bytes);journal.begin(session,plan.slots,0,true)
        var state=checkNotNull(journal.recordExerciseNote(plan,0,0,edit()))
        assertNull(journal.recordNote(session,0,JournalSessionNote("pin-one","Collision",3000)))
        state=checkNotNull(journal.recordNote(session,0,JournalSessionNote("session-note","Session only",3000)))
        assertEquals(1,state.exerciseNotes.size)
        val warm=JournalWarmupIntent("warm",JournalWarmupSlot(0,0,"bb-bench"),JournalSetDisposition.COMPLETED,
            JournalSetValues("20","lb",6,null),3000)
        assertNull(journal.recordWarmup(plan,0,0,warm.copy(operationId="pin-one")))
        state=checkNotNull(journal.recordWarmup(plan,0,0,warm))
        assertEquals(1,state.exerciseNotes.size)
        assertNull(journal.recordExerciseNote(plan,0,1,edit("warm",at=4000,replaces="pin-one")))
        for((i,slot) in plan.slots.withIndex()) {
            assertNull(journal.recordSet(session,state.revision,JournalSetIntent("pin-one",slot,JournalSetDisposition.SKIPPED,null)))
            state=checkNotNull(journal.recordSet(session,state.revision,JournalSetIntent("set-$i",slot,JournalSetDisposition.SKIPPED,null)))
            assertEquals(1,state.exerciseNotes.size)
        }
        val cleared=edit("pin-clear","",5000,"pin-one").copy(setup=emptyMap())
        state=checkNotNull(journal.recordExerciseNote(plan,state.revision,1,cleared))
        assertEquals(2L,state.revision);assertTrue(state.completed.all {it.intent.disposition==JournalSetDisposition.SKIPPED})
        assertNull(journal.finish(session,state.revision,JournalFinishIntent("finish",4000,3000)))
        assertNull(journal.finish(session,state.revision,JournalFinishIntent("pin-clear",6000,5000)))
        state=checkNotNull(journal.finish(session,state.revision,JournalFinishIntent("finish",6000,5000)))
        assertEquals(cleared,state.exerciseNotes.last());assertEquals("",NativeExerciseNotePolicy.resolved(plan,state.exerciseNotes,"bb-bench")?.text)
        assertNull(journal.recordExerciseNote(plan,state.revision,2,edit("too-late",at=7000,replaces="pin-clear")))
        val handoff=JSONObject(checkNotNull(journal.finishedHandoff(session,state.revision)))
        assertEquals(7,handoff.getInt("format"));assertEquals(2,handoff.getJSONArray("exerciseNotes").length())
        assertEquals(state.exerciseNotes,checkNotNull(WorkoutJournalPersistence(bytes).recover()).exerciseNotes)
    }

    @Test fun editingCannotConsumeRemainingSetAndFinishSpace() {
        val bytes=Bytes();val journal=WorkoutJournalPersistence(bytes);var state=checkNotNull(journal.begin(session,plan.slots,0,true))
        var accepted=0
        for(i in 0..128) {
            val command=edit("bounded-$i","🏋️".repeat(650),2000L+i,state.exerciseNotes.lastOrNull()?.operationId)
            val next=journal.recordExerciseNote(plan,0,state.exerciseNotes.size,command) ?: break
            state=next;accepted++
        }
        assertTrue(accepted in 1..128)
        val writes=bytes.writes
        bytes.failBefore=true
        assertTrue(runCatching {journal.recordSet(session,0,JournalSetIntent("before-full",plan.slots[0],JournalSetDisposition.SKIPPED,null))}.isFailure)
        bytes.failBefore=false;assertEquals(writes,bytes.writes)
        for((i,slot) in plan.slots.withIndex())state=checkNotNull(journal.recordSet(session,state.revision,
            JournalSetIntent("remaining-$i",slot,JournalSetDisposition.COMPLETED,JournalSetValues("99999.999","lb",100,10))))
        state=checkNotNull(journal.finish(session,state.revision,JournalFinishIntent("finish-full",6000,5000)))
        assertTrue(state.isFinished);assertEquals(accepted,checkNotNull(journal.recover()).exerciseNotes.size)
    }

    @Test fun malformedResignedEditAuditCannotRecoverAndOldFormatsRemainReadable() {
        val bytes=Bytes();val journal=WorkoutJournalPersistence(bytes);journal.begin(session,plan.slots,0,true)
        assertEquals(6,JSONObject(String(checkNotNull(bytes.raw),Charsets.UTF_8)).getInt("format"))
        journal.recordExerciseNote(plan,0,0,edit())
        val original=checkNotNull(bytes.raw).copyOf()
        for(mutate in listOf<(JSONObject)->Unit>(
            {it.put("updatedAt",999)}, {it.put("extra",true)}, {it.put("text","bad\u0000text")},
            {it.put("replacesOperationId","pin-one")}, {it.put("operationId","bad op")})) {
            val envelope=JSONObject(String(original,Charsets.UTF_8));val payload=JSONObject(envelope.getString("payload"))
            mutate(payload.getJSONArray("exerciseNotes").getJSONObject(0))
            val raw=payload.toString();val hash=MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
                .joinToString("") {"%02x".format(it.toInt() and 255)}
            bytes.raw=envelope.put("payload",raw).put("sha256",hash).toString().toByteArray(Charsets.UTF_8)
            assertTrue(runCatching {WorkoutJournalPersistence(bytes).recover()}.isFailure)
        }
        val old=Bytes();val legacy=WorkoutJournalPersistence(old);legacy.begin(session,plan.slots,0)
        assertEquals(2,JSONObject(String(checkNotNull(old.raw),Charsets.UTF_8)).getInt("format"))
        assertTrue(checkNotNull(WorkoutJournalPersistence(old).recover()).exerciseNotes.isEmpty())
    }
}
