package io.github.extracteddeath.pursuitiron.domain

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** The very same invented finished journal is consumed by canonical JS tests. */
class WorkoutJournalHandoffTest {
    private class Memory : WorkoutJournalBytes {
        var raw: ByteArray? = null
        override fun read() = raw?.copyOf()
        override fun commitAtomically(bytes: ByteArray) { raw=bytes.copyOf() }
    }
    private val fixture: JSONObject by lazy {
        JSONObject(checkNotNull(javaClass.classLoader
            ?.getResourceAsStream("finished-journal-golden.json"))
            .bufferedReader().use { it.readText() })
    }
    private val handoff get() = fixture.getJSONObject("handoff")
    private val session: JournalSessionKey get() {
        val s=handoff.getJSONObject("session")
        val r=s.getJSONObject("reference")
        return JournalSessionKey(ReadOnlyProgramReference(
            r.getString("programId"),r.getLong("programUpdatedAt"),
            r.getLong("storeSavedAt"),r.getInt("storeVersion"),r.getString("snapshotSha256")),
            s.getString("workoutId"),s.getString("dayId"),s.getInt("weekIndex"),
            s.getString("unit"),s.getLong("startedAt"))
    }
    private fun slot(v: JSONObject)=JournalSetSlot(
        v.getInt("exerciseIndex"),v.getInt("setIndex"),v.getString("exerciseId"))

    @Test fun durableCrossLanguageHandoffPreservesContextSkippedPositionsAndFinishTime() {
        val bytes=Memory()
        var owner=WorkoutJournalPersistence(bytes)
        val rawSlots=handoff.getJSONArray("slots")
        val slots=(0 until rawSlots.length()).map { slot(rawSlots.getJSONObject(it)) }
        val s=session
        var state=checkNotNull(owner.begin(s,slots,handoff.getLong("startRevision")))
        assertNull(owner.finishedHandoff(s,state.revision))
        val events=handoff.getJSONArray("events")
        for (i in 0 until events.length()) {
            val e=events.getJSONObject(i)
            val v=if (e.isNull("values")) null else e.getJSONObject("values")
            val values=v?.let { JournalSetValues(
                if (it.isNull("load")) null else it.getString("load"),
                if (it.isNull("unit")) null else it.getString("unit"),
                it.getInt("reps"),if (it.isNull("rir")) null else it.getInt("rir")) }
            state=checkNotNull(owner.recordSet(s,state.revision,JournalSetIntent(
                e.getString("operationId"),slot(e.getJSONObject("slot")),
                JournalSetDisposition.valueOf(e.getString("disposition")),values)))
            owner=WorkoutJournalPersistence(bytes) // process-owner restart after every set
        }
        assertNull(owner.finishedHandoff(s,state.revision))
        val f=handoff.getJSONObject("finish")
        val intent=JournalFinishIntent(f.getString("operationId"),
            f.getLong("completedAt"),f.getLong("durationMs"))
        state=checkNotNull(owner.finish(s,state.revision,intent))
        val restarted=WorkoutJournalPersistence(bytes)
        val exported=checkNotNull(restarted.finishedHandoff(s,state.revision))
        assertTrue(handoff.similar(JSONObject(exported)))
        assertNull(restarted.finishedHandoff(s,state.revision-1))
        assertNull(restarted.finishedHandoff(s.copy(weekIndex=2),state.revision))
        assertNull(restarted.finish(s,f.getLong("fromRevision"),intent.copy(completedAt=62000)))
        assertNull(restarted.finish(s,f.getLong("fromRevision"),intent.copy(durationMs=59000)))
        assertEquals(exported,restarted.finishedHandoff(s,state.revision))
        assertTrue(checkNotNull(restarted.recover()).isFinished)
    }

    @Test fun invalidSessionContextAndFinishClocksCannotBeCommitted() {
        val owner=WorkoutJournalPersistence(Memory())
        val slots=listOf(JournalSetSlot(0,0,"bb-bench"))
        for (bad in listOf(session.copy(dayId=""),session.copy(weekIndex=0),
            session.copy(unit="stones"),session.copy(startedAt=0)))
            assertNull(owner.begin(bad,slots,0))
        val s=session
        assertNotNull(owner.begin(s,slots,0))
        assertNull(owner.recordSet(s,0,JournalSetIntent("mixed-unit",slots[0],
            JournalSetDisposition.COMPLETED,JournalSetValues("100","kg",8,2))))
        assertNotNull(owner.recordSet(s,0,JournalSetIntent("set",slots[0],
            JournalSetDisposition.SKIPPED,null)))
        assertNull(owner.finish(s,1,JournalFinishIntent("finish",s.startedAt-1,0)))
        assertNull(owner.finish(s,1,JournalFinishIntent("finish",s.startedAt+10,11)))
        assertFalse(checkNotNull(owner.recover()).isFinished)
    }
}
