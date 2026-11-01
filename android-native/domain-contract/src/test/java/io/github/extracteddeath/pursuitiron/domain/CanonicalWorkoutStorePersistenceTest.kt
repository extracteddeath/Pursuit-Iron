package io.github.extracteddeath.pursuitiron.domain

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.CancellationException
import kotlin.coroutines.*

/** Invented inputs; shared output comes from the actual canonical JS engine. */
class CanonicalWorkoutStorePersistenceTest {
    private class Drive : CanonicalWorkoutStoreBytes, WorkoutJournalBytes {
        var raw: ByteArray?=null
        var writes=0
        var clears=0
        var failBeforeWrite: Int?=null
        var failAfterWrite: Int?=null
        var failBeforeClear=false
        var failAfterClear=false
        var onClear: (() -> Unit)?=null
        val issued=mutableListOf<ByteArray>()
        override fun read()=raw?.copyOf()?.also { issued.add(it) }
        override fun commitAtomically(bytes: ByteArray) {
            writes++
            if (writes==failBeforeWrite) throw IOException("Synthetic full disk")
            raw=bytes.copyOf()
            if (writes==failAfterWrite) throw IOException("Synthetic crash after commit")
        }
        override fun clearAtomically() {
            clears++; onClear?.invoke()
            if (failBeforeClear) throw IOException("Synthetic cleanup failure")
            raw=null
            if (failAfterClear) throw IOException("Synthetic crash after cleanup")
        }
    }
    private val fixture: JSONObject by lazy {
        JSONObject(checkNotNull(javaClass.classLoader?.getResourceAsStream("finished-journal-golden.json"))
            .bufferedReader().use { it.readText() })
    }
    private val source get()=fixture.getString("rawSnapshot")
    private val wire get()=fixture.getJSONObject("handoff")
    private val canonical get()=fixture.getJSONObject("preparation")
    private val session: JournalSessionKey get() {
        val s=wire.getJSONObject("session"); val r=s.getJSONObject("reference")
        return JournalSessionKey(ReadOnlyProgramReference(r.getString("programId"),
            r.getLong("programUpdatedAt"),r.getLong("storeSavedAt"),r.getInt("storeVersion"),
            r.getString("snapshotSha256")),s.getString("workoutId"),s.getString("dayId"),
            s.getInt("weekIndex"),s.getString("unit"),s.getLong("startedAt"))
    }
    private fun sha(raw: String)=MessageDigest.getInstance("SHA-256")
        .digest(raw.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 255) }
    private fun slot(v: JSONObject)=JournalSetSlot(v.getInt("exerciseIndex"),
        v.getInt("setIndex"),v.getString("exerciseId"))
    private val slots get()=wire.getJSONArray("slots").let { a ->
        (0 until a.length()).map { slot(a.getJSONObject(it)) } }
    private class Call<T>(block: suspend () -> T) {
        var result: Result<T>?=null
        init { block.startCoroutine(object: Continuation<T> {
            override val context=EmptyCoroutineContext
            override fun resumeWith(result: Result<T>) { this@Call.result=result }
        }) }
        fun value(): T=checkNotNull(result) { "Synthetic coroutine is still suspended" }.getOrThrow()
    }
    private fun <T> now(block: suspend () -> T): T=Call(block).value()
    private inner class Engine : PinnedCanonicalWorkoutStoreEngine {
        var approved=true
        var prepareCalls=0
        var validationCalls=0
        var fault: Exception?=null
        var pause: (suspend () -> Unit)?=null
        var mutate: ((JSONObject) -> Unit)?=null
        override suspend fun validateInitialSnapshot(exactSnapshot: String): Boolean {
            validationCalls++; return approved && exactSnapshot==source
        }
        override suspend fun prepareCompletion(origin: String,current: String,finishedJournal: String): String {
            prepareCalls++
            assertEquals(source,origin)
            assertTrue(current==source || current==canonical.getString("candidateSnapshot"))
            fault?.let { throw it }
            pause?.invoke()
            // Records/prescriptions are copied verbatim from the canonical JS
            // golden output. Only request byte hashes/identity vary on a retry.
            val p=JSONObject(canonical.toString()).put("inputHandoffSha256",sha(finishedJournal))
                .put("expectedSnapshotSha256",sha(current))
            if (current!=source) p.put("alreadyPresent",true).put("candidateSnapshot",current)
                .put("candidateSnapshotSha256",sha(current))
            mutate?.invoke(p)
            return p.toString()
        }
    }
    private inner class Setup {
        val storeDrive=Drive(); val journalDrive=Drive(); val engine=Engine()
        var store=CanonicalWorkoutStorePersistence(storeDrive,engine)
        var journal=WorkoutJournalPersistence(journalDrive)
        fun bootstrap() { assertTrue(now { store.initialize(source) }) }
        fun begin(): WorkoutJournalState=checkNotNull(store.beginSession(session,slots,
            wire.getLong("startRevision"),journal))
        fun finish(): WorkoutJournalState {
            var state=begin()
            val events=wire.getJSONArray("events")
            for (i in 0 until events.length()) {
                val e=events.getJSONObject(i)
                val v=if (e.isNull("values")) null else e.getJSONObject("values")
                state=checkNotNull(journal.recordSet(session,state.revision,JournalSetIntent(
                    e.getString("operationId"),slot(e.getJSONObject("slot")),
                    JournalSetDisposition.valueOf(e.getString("disposition")),v?.let {
                        JournalSetValues(it.getString("load"),it.getString("unit"),it.getInt("reps"),
                            if (it.isNull("rir")) null else it.getInt("rir")) })))
            }
            val f=wire.getJSONObject("finish")
            return checkNotNull(journal.finish(session,state.revision,JournalFinishIntent(
                f.getString("operationId"),f.getLong("completedAt"),f.getLong("durationMs"))))
        }
        fun snapshot(): String=String(checkNotNull(now { store.readOwnedCurrentStoreBytes() }),Charsets.UTF_8)
        fun restart() {
            store=CanonicalWorkoutStorePersistence(storeDrive,engine)
            journal=WorkoutJournalPersistence(journalDrive)
        }
        fun complete()=now { store.complete(session,wire.getJSONObject("finish").getLong("toRevision"),journal) }
    }

    @Test fun canonicalHistoryIsDurableBeforeRecoveryRemovalAndNextSessionCanBegin() {
        val s=Setup(); var changes=0
        val subscription=s.store.subscribeCommittedChanges { changes++ }
        s.bootstrap(); s.finish()
        assertEquals(1,changes) // pinning/recovery metadata is not a new Plan revision
        s.journalDrive.onClear={
            val current=JSONObject(s.snapshot()).getJSONArray("history")
            assertEquals(1,current.length())
            assertEquals(session.workoutId,current.getJSONObject(0).getString("id"))
        }
        val result=checkNotNull(s.complete())
        assertTrue(result.recoveryCleared)
        assertEquals(12,result.receipt.journalRevision)
        assertEquals(canonical.getString("candidateSnapshot"),s.snapshot())
        assertNull(s.journal.recover())
        assertEquals(2,changes)
        assertTrue(s.storeDrive.issued.all { bytes -> bytes.all { it==0.toByte() } })
        subscription.close()
        s.restart()
        val before=s.storeDrive.writes
        assertTrue(checkNotNull(s.complete()).recoveryCleared)
        assertEquals(before,s.storeDrive.writes)
        val nextRef=session.programRevision.copy(snapshotSha256=sha(s.snapshot()),storeSavedAt=61000)
        val next=session.copy(programRevision=nextRef,workoutId="next-workout",startedAt=62000)
        assertNotNull(s.store.beginSession(next,slots,0,s.journal))
        assertNull(s.store.beginSession(session,slots,7,s.journal))
    }

    @Test fun unfinishedForeignOrStaleSessionsNeverReachCompletionEngine() {
        val s=Setup(); s.bootstrap(); val opened=s.begin()
        assertNull(now { s.store.complete(session,opened.revision,s.journal) })
        assertNull(s.store.beginSession(session.copy(workoutId="other"),slots,7,s.journal))
        assertNull(s.store.beginSession(session,slots,8,s.journal))
        assertNull(s.store.beginSession(session,slots.reversed(),7,s.journal))
        assertEquals(0,s.engine.prepareCalls)
        s.finish()
        assertNull(now { s.store.complete(session.copy(weekIndex=2),12,s.journal) })
        assertNull(now { s.store.complete(session,11,s.journal) })
        assertEquals(0,s.engine.prepareCalls)
        assertEquals(source,s.snapshot())
    }

    @Test fun failedHistoryWriteRetainsExactOriginAndEverySetForRetry() {
        val s=Setup(); s.bootstrap(); s.finish()
        s.storeDrive.failBeforeWrite=s.storeDrive.writes+1
        assertTrue(runCatching { s.complete() }.isFailure)
        assertEquals(source,s.snapshot())
        assertTrue(checkNotNull(s.journal.recover()).isFinished)
        assertEquals(0,s.journalDrive.clears)
        s.storeDrive.failBeforeWrite=null; s.restart()
        assertTrue(checkNotNull(s.complete()).recoveryCleared)
        assertEquals(1,JSONObject(s.snapshot()).getJSONArray("history").length())
    }

    @Test fun crashAfterHistoryCommitReplaysWithoutAnotherHistoryEntry() {
        val s=Setup(); var changes=0
        s.store.subscribeCommittedChanges { changes++ }
        s.bootstrap(); s.finish()
        s.storeDrive.failAfterWrite=s.storeDrive.writes+1
        assertTrue(runCatching { s.complete() }.isFailure)
        assertEquals(1,JSONObject(s.snapshot()).getJSONArray("history").length())
        assertNotNull(s.journal.recover())
        assertEquals(2,changes) // observers see the committed snapshot despite lost ack
        s.storeDrive.failAfterWrite=null; s.restart()
        val before=s.storeDrive.writes
        assertTrue(checkNotNull(s.complete()).recoveryCleared)
        assertEquals(before+1,s.storeDrive.writes) // metadata retirement only
        assertEquals(1,JSONObject(s.snapshot()).getJSONArray("history").length())
    }

    @Test fun crashesDuringRecoveryCleanupCannotEraseHistoryOrStrandNextWorkout() {
        for (point in 0..3) {
            val s=Setup(); s.bootstrap(); s.finish()
            when (point) {
                0 -> s.journalDrive.failBeforeClear=true
                1 -> s.journalDrive.failAfterClear=true
                2 -> s.storeDrive.failBeforeWrite=s.storeDrive.writes+2
                3 -> s.storeDrive.failAfterWrite=s.storeDrive.writes+2
            }
            assertTrue(runCatching { s.complete() }.isFailure)
            assertEquals(1,JSONObject(s.snapshot()).getJSONArray("history").length())
            s.journalDrive.failBeforeClear=false; s.journalDrive.failAfterClear=false
            s.storeDrive.failBeforeWrite=null; s.storeDrive.failAfterWrite=null
            s.restart()
            assertTrue(checkNotNull(s.complete()).recoveryCleared)
            assertNull(s.journal.recover())
            assertEquals(1,JSONObject(s.snapshot()).getJSONArray("history").length())
        }
    }

    @Test fun initializationAndOriginPinFailuresDoNotReplaceOwnedData() {
        val s=Setup(); s.engine.approved=false
        assertFalse(now { s.store.initialize(source) })
        assertEquals(0,s.storeDrive.writes)
        s.engine.approved=true; s.bootstrap()
        assertFalse(now { s.store.initialize(source+" ") })
        val writes=s.storeDrive.writes
        s.storeDrive.failAfterWrite=writes+1
        assertTrue(runCatching { s.begin() }.isFailure)
        assertNull(s.journal.recover())
        s.storeDrive.failAfterWrite=null; s.restart()
        assertNotNull(s.begin())
        assertEquals(source,s.snapshot())
    }

    @Test fun staleCorruptOrForgedPreparationNeverWritesHistoryOrClearsRecovery() {
        val mutations=listOf<(JSONObject)->Unit>(
            { it.put("persisted",true) },{ it.put("approvedForWorkoutExecution",true) },
            { it.put("source","untrusted") },{ it.put("inputHandoffSha256","f".repeat(64)) },
            { it.put("sourceSnapshotSha256","f".repeat(64)) },
            { it.put("expectedSnapshotSha256","f".repeat(64)) },
            { it.put("candidateSnapshotSha256","f".repeat(64)) },
            { it.put("alreadyPresent",true) },{ it.put("extra",true) },
            { it.getJSONObject("historyEntry").put("dayId","another-day") },
            { it.getJSONObject("historyEntry").put("date",62000) },
            { it.getJSONObject("historyEntry").getJSONObject("nativeCompletion").put("journalRevision",13) },
            { it.getJSONObject("historyEntry").getJSONObject("perf").getJSONObject("bb-bench")
                .getJSONArray("sets").getJSONObject(0).put("r",9) }
        )
        for (mutate in mutations) {
            val s=Setup(); s.bootstrap(); s.finish(); val writes=s.storeDrive.writes
            s.engine.mutate=mutate
            assertTrue(runCatching { s.complete() }.isFailure)
            assertEquals(writes,s.storeDrive.writes)
            assertEquals(0,s.journalDrive.clears)
            assertTrue(checkNotNull(s.journal.recover()).isFinished)
        }
    }

    @Test fun cancellationAndOverlappingFinishRequestsNeverLoseOrDuplicateWork() {
        val s=Setup(); s.bootstrap(); s.finish()
        s.engine.fault=CancellationException("Synthetic cancelled engine call")
        assertTrue(runCatching { s.complete() }.exceptionOrNull() is CancellationException)
        assertNotNull(s.journal.recover()); assertEquals(source,s.snapshot())
        s.engine.fault=null
        val paused=mutableListOf<Continuation<Unit>>()
        s.engine.pause={ suspendCoroutine { paused.add(it) } }
        val one=Call { s.store.complete(session,12,s.journal) }
        val two=Call { s.store.complete(session,12,s.journal) }
        assertNull(one.result); assertNull(two.result)
        assertEquals(source,s.snapshot()) // no store lock held while engine awaits
        paused[0].resume(Unit); paused[1].resume(Unit)
        assertTrue(checkNotNull(one.value()).recoveryCleared)
        assertTrue(checkNotNull(two.value()).recoveryCleared)
        assertEquals(1,JSONObject(s.snapshot()).getJSONArray("history").length())
        assertEquals(1,s.journalDrive.clears)
    }

    @Test fun corruptOversizedAndInvalidUtf8StoresArePreservedWithoutBootstrap() {
        for (bad in listOf(byteArrayOf(0xc3.toByte(),0x28),"garbage".toByteArray(),
            ByteArray(CanonicalWorkoutStorePersistence.MAX_STORE_BYTES+1))) {
            val s=Setup(); s.storeDrive.raw=bad.copyOf()
            assertTrue(runCatching { now { s.store.initialize(source) } }.isFailure)
            assertEquals(0,s.engine.validationCalls)
            assertEquals(0,s.storeDrive.writes)
            assertArrayEquals(bad,s.storeDrive.raw)
            assertTrue(s.storeDrive.issued.all { bytes -> bytes.all { it==0.toByte() } })
        }
        val s=Setup()
        for (bad in listOf("\uFEFF"+source,"\uD800"," ".repeat(ReadOnlyBackupLimits.MAX_SOURCE_BYTES+1)))
            assertTrue(runCatching { now { s.store.initialize(bad) } }.isFailure)
        assertEquals(0,s.engine.validationCalls)
    }
}
