package io.github.extracteddeath.pursuitiron.domain

import org.junit.Assert.*
import org.junit.Test

class WorkoutJournalWarmupTest {
    private class Drive: WorkoutJournalBytes {
        var raw: ByteArray?=null;var writes=0;var before=false;var after=false
        override fun read()=raw?.copyOf()
        override fun commitAtomically(bytes: ByteArray) {
            if(before)throw java.io.IOException("disk full")
            raw=bytes.copyOf();writes++
            if(after)throw java.io.IOException("lost acknowledgement")
        }
    }
    private val session=JournalSessionKey(ReadOnlyProgramReference("program",100,100,13,"a".repeat(64)),"workout","day",1,"lb",1000)
    private fun slot(ex: Int,index: Int)=JournalWarmupSlot(ex,index,"bench")
    private fun value(load: String="25",reps: Int=8,unit: String="lb")=JournalSetValues(load,unit,reps,null)
    private val plan=NativeWorkoutPlan("synthetic-issued",session,"Program","Upper",listOf(
        NativeWorkoutExercise(0,"bench","Bench press",120,false,listOf("20 lb × 8","30 lb × 5"),
            listOf(NativeWorkoutSet(JournalSetSlot(0,0,"bench"),"50","6","5-8","2",null)),
            listOf(NativeWarmupTarget(slot(0,0),"20","8"),NativeWarmupTarget(slot(0,1),"30","5"))),
        NativeWorkoutExercise(1,"bench","Bench press",120,false,listOf("20 lb × 8"),
            listOf(NativeWorkoutSet(JournalSetSlot(1,0,"bench"),"50","6","5-8","2",null)),
            listOf(NativeWarmupTarget(slot(1,0),"20","8")))
    ))

    @Test fun warmupsKeepIndependentPositionsAndAuditAcrossWorkingSetsNotesAndFinish() {
        val drive=Drive();var journal=WorkoutJournalPersistence(drive)
        assertNotNull(journal.begin(session,plan.slots,0))
        val first=JournalWarmupIntent("warm-first",slot(0,0),JournalSetDisposition.COMPLETED,value(),1100)
        var state=checkNotNull(journal.recordWarmup(plan,0,0,first))
        assertEquals(0L,state.revision);assertEquals(2,state.unresolvedCount)
        val repeated=JournalWarmupIntent("warm-repeated",slot(1,0),JournalSetDisposition.COMPLETED,value("35"),1200)
        state=checkNotNull(journal.recordWarmup(plan,0,1,repeated))
        state=checkNotNull(journal.recordWarmup(plan,0,2,JournalWarmupIntent("warm-skip",slot(0,1),JournalSetDisposition.SKIPPED,null,1300)))
        state=checkNotNull(journal.recordWarmup(plan,0,3,JournalWarmupIntent("warm-added",slot(0,2),JournalSetDisposition.COMPLETED,value("0",10),1400)))
        val corrected=JournalWarmupIntent("warm-attempt",slot(0,0),JournalSetDisposition.COMPLETED,value("80",0),1500,first.operationId)
        state=checkNotNull(journal.recordWarmup(plan,0,4,corrected))
        assertEquals(4,state.warmups.size);assertEquals(5,state.warmupEvents.size)
        assertEquals("35",state.warmups.single {it.slot==slot(1,0)}.values?.load)
        assertEquals(0,state.warmups.single {it.slot==slot(0,0)}.values?.reps)
        assertNull(journal.recordWarmup(plan,0,5,first.copy(operationId="stale-warm")))
        state=checkNotNull(journal.recordNote(session,0,JournalSessionNote("session-note","Keep this note",1600)))
        state=checkNotNull(journal.recordSet(session,0,JournalSetIntent("working-one",plan.slots[0],JournalSetDisposition.COMPLETED,value("50",6))))
        assertEquals(5,state.warmupEvents.size);assertEquals(1L,state.revision)
        journal=WorkoutJournalPersistence(drive)
        assertEquals(state.warmupEvents,checkNotNull(journal.recover()).warmupEvents)
        assertNotNull(journal.recordSet(session,1,JournalSetIntent("working-two",plan.slots[1],JournalSetDisposition.SKIPPED,null)))
        assertNull(journal.finish(session,2,JournalFinishIntent("too-early",1450,450)))
        state=checkNotNull(journal.finish(session,2,JournalFinishIntent("finish",2000,1000)))
        assertEquals(3L,state.revision);assertEquals(2,state.completed.size);assertEquals(5,state.warmupEvents.size)
        val handoff=org.json.JSONObject(checkNotNull(journal.finishedHandoff(session,3)))
        assertEquals(6,handoff.getInt("format"));assertEquals(5,handoff.getJSONArray("warmups").length())
        assertEquals(2,handoff.getJSONArray("events").length());assertEquals("Keep this note",handoff.getJSONObject("note").getString("text"))
        assertNull(journal.recordWarmup(plan,3,5,corrected.copy(operationId="after-finish",recordedAt=2100)))
    }

    @Test fun trackingRetainsOriginalTargetCapabilityEvenWhenNoWarmupIsRecorded() {
        val drive=Drive();var journal=WorkoutJournalPersistence(drive)
        var state=checkNotNull(journal.begin(session,plan.slots,0,tracksWarmups=true))
        assertTrue(state.tracksWarmups);assertTrue(state.warmupEvents.isEmpty())
        for((i,slot) in plan.slots.withIndex())state=checkNotNull(journal.recordSet(session,state.revision,
            JournalSetIntent("unlogged-work-$i",slot,JournalSetDisposition.SKIPPED,null)))
        assertTrue(state.tracksWarmups)
        journal=WorkoutJournalPersistence(drive);state=checkNotNull(journal.recover())
        assertTrue(state.tracksWarmups)
        state=checkNotNull(journal.finish(session,state.revision,JournalFinishIntent("unlogged-finish",2000,1000)))
        val handoff=org.json.JSONObject(checkNotNull(journal.finishedHandoff(session,state.revision)))
        assertEquals(6,handoff.getInt("format"));assertEquals(0,handoff.getJSONArray("warmups").length())
    }

    @Test fun diskFailureLeavesOldFactsAndAnExactLostAcknowledgementRetriesWithoutAnotherWrite() {
        val drive=Drive();var journal=WorkoutJournalPersistence(drive)
        assertNotNull(journal.begin(session,plan.slots,0))
        val event=JournalWarmupIntent("warm-once",slot(0,0),JournalSetDisposition.COMPLETED,value(),1100)
        drive.before=true;assertTrue(runCatching {journal.recordWarmup(plan,0,0,event)}.isFailure)
        assertTrue(checkNotNull(journal.recover()).warmupEvents.isEmpty())
        drive.before=false;drive.after=true
        assertTrue(runCatching {journal.recordWarmup(plan,0,0,event)}.isFailure)
        drive.after=false;journal=WorkoutJournalPersistence(drive)
        val writes=drive.writes
        assertEquals(listOf(event),checkNotNull(journal.recordWarmup(plan,0,0,event)).warmupEvents)
        assertEquals(writes,drive.writes)
        assertNull(journal.recordWarmup(plan,0,1,event.copy(values=value("26"))))
    }

    @Test fun missingForeignStaleAndMalformedWarmupFactsCannotEnterTheJournal() {
        val drive=Drive();val journal=WorkoutJournalPersistence(drive)
        assertNotNull(journal.begin(session,plan.slots,0))
        val event=JournalWarmupIntent("valid-warm",slot(0,0),JournalSetDisposition.COMPLETED,value(),1100)
        for(invalid in listOf(event.copy(slot=slot(0,19)),event.copy(slot=slot(47,0)),
            event.copy(slot=slot(0,0).copy(exerciseId="foreign")),event.copy(slot=slot(0,-1)),
            event.copy(values=value("0",0)),event.copy(values=value("25",-1)),event.copy(values=value("25",5,"kg")),
            event.copy(recordedAt=999),event.copy(values=null),event.copy(replacesOperationId="missing")))
            assertNull(journal.recordWarmup(plan,0,0,invalid))
        assertNull(journal.recordWarmup(plan,1,0,event))
        assertNull(journal.recordWarmup(plan,0,1,event))
        assertNotNull(journal.recordWarmup(plan,0,0,event))
        assertNull(journal.recordSet(session,0,JournalSetIntent(event.operationId,plan.slots[0],JournalSetDisposition.SKIPPED,null)))
        assertNull(journal.recordNote(session,0,JournalSessionNote(event.operationId,"collision",1200)))
        assertTrue(runCatching {
            (checkNotNull(journal.recover()).warmupEvents as MutableList<JournalWarmupIntent>).clear()
        }.isFailure)
    }

    @Test(timeout=60000) fun repeatedWarmupEditsReserveSpaceForEveryWorkingSetAndFinish() {
        val drive=Drive();val journal=WorkoutJournalPersistence(drive)
        var state=checkNotNull(journal.begin(session,plan.slots,0));var refused=false
        for(i in 0..1024) {
            val prior=state.warmups.firstOrNull()
            val event=JournalWarmupIntent("warm-$i-"+"x".repeat(110),slot(0,0),JournalSetDisposition.COMPLETED,value("99999.999",100),
                1100+i.toLong(),prior?.operationId)
            val next=journal.recordWarmup(plan,0,state.warmupEvents.size,event)
            if(next==null) {refused=true;break}
            state=next
        }
        assertTrue(refused);assertTrue(state.warmupEvents.size>100)
        for((i,slot) in plan.slots.withIndex())state=checkNotNull(journal.recordSet(session,state.revision,
            JournalSetIntent("work-$i",slot,JournalSetDisposition.SKIPPED,null,
                adjustment=JournalSetAdjustment("reduce","x".repeat(40)))))
        assertNotNull(journal.finish(session,state.revision,JournalFinishIntent("finish",10000,9000)))
    }
}
