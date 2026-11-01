package io.github.extracteddeath.pursuitiron.engine

import io.github.extracteddeath.pursuitiron.domain.*
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Actual packaged canonical JS -> Kotlin transport -> atomic store/journal. No APK. */
class CanonicalWorkoutRuntimeIntegrationTest {
    private class Drive : CanonicalWorkoutStoreBytes, WorkoutJournalBytes {
        var raw: ByteArray?=null
        var writes=0
        var failAfterWrite=false
        override fun read()=raw?.copyOf()
        override fun commitAtomically(bytes: ByteArray) {
            writes++;raw=bytes.copyOf()
            if (failAfterWrite) throw java.io.IOException("Synthetic post-commit crash")
        }
        override fun clearAtomically() { raw=null }
    }
    private class NodeTransport : CanonicalWorkoutStoreTransport, AutoCloseable {
        private val root=checkNotNull(System.getenv("PURSUIT_CANONICAL_REPO")) {
            "Run build-runtime-proof.mjs and set PURSUIT_CANONICAL_REPO before runtime integration tests"
        }
        private val bundle=checkNotNull(System.getenv("PURSUIT_CANONICAL_BUNDLE"))
        private val process=ProcessBuilder("node","--no-warnings",
            File(root,"android-native/engine-bridge/fixture-runtime-server.mjs").path,bundle)
            .redirectError(ProcessBuilder.Redirect.INHERIT).start()
        private val input=process.inputStream.bufferedReader(Charsets.UTF_8)
        private val output=process.outputStream.bufferedWriter(Charsets.UTF_8)
        private val guard=Any()
        override suspend fun request(wire: String): String=withContext(Dispatchers.IO) {
            synchronized(guard) {
                output.write(wire);output.newLine();output.flush()
                checkNotNull(input.readLine()) { "Canonical integration runtime stopped" }
            }
        }
        override fun close() {
            runCatching { output.close() };runCatching { input.close() };process.destroyForcibly()
        }
    }
    private val fixture: JSONObject by lazy {
        JSONObject(checkNotNull(javaClass.classLoader?.getResourceAsStream("finished-journal-golden.json"))
            .bufferedReader().use { it.readText() })
    }
    private val source get()=fixture.getString("rawSnapshot")
    private val handoff get()=fixture.getJSONObject("handoff")
    private val session: JournalSessionKey get() {
        val s=handoff.getJSONObject("session");val r=s.getJSONObject("reference")
        return JournalSessionKey(ReadOnlyProgramReference(r.getString("programId"),
            r.getLong("programUpdatedAt"),r.getLong("storeSavedAt"),r.getInt("storeVersion"),
            r.getString("snapshotSha256")),s.getString("workoutId"),s.getString("dayId"),
            s.getInt("weekIndex"),s.getString("unit"),s.getLong("startedAt"))
    }
    private fun slot(v: JSONObject)=JournalSetSlot(v.getInt("exerciseIndex"),v.getInt("setIndex"),v.getString("exerciseId"))
    private fun finish(store: CanonicalWorkoutStorePersistence,journal: WorkoutJournalPersistence) {
        val a=handoff.getJSONArray("slots")
        val slots=(0 until a.length()).map { slot(a.getJSONObject(it)) }
        var state=checkNotNull(store.beginSession(session,slots,handoff.getLong("startRevision"),journal))
        val events=handoff.getJSONArray("events")
        for (i in 0 until events.length()) {
            val e=events.getJSONObject(i);val v=if(e.isNull("values")) null else e.getJSONObject("values")
            state=checkNotNull(journal.recordSet(session,state.revision,JournalSetIntent(
                e.getString("operationId"),slot(e.getJSONObject("slot")),
                JournalSetDisposition.valueOf(e.getString("disposition")),v?.let {
                    JournalSetValues(it.getString("load"),it.getString("unit"),it.getInt("reps"),
                        if(it.isNull("rir")) null else it.getInt("rir")) })))
        }
        val f=handoff.getJSONObject("finish")
        assertNotNull(journal.finish(session,state.revision,JournalFinishIntent(f.getString("operationId"),
            f.getLong("completedAt"),f.getLong("durationMs"))))
    }

    @Test(timeout=60000) fun realOnboardingCreatesSavesAndRestoresWithoutReplacingAnotherProgram() {
        NodeTransport().use { node -> runBlocking {
            val engine=SandboxCanonicalWorkoutStoreEngine(node)
            val options=checkNotNull(engine.programOptions())
            assertEquals(listOf(4,6),options.splits.single { it.id=="upper_lower" }.days)
            val profile=NativeProgramProfile(name="Native first program",unit="kg",equipment=options.equipment)
            val setup=NativeProgramSetupCodec.setupJson(profile,"create-new","program-new",1000,223)
            val proposal=checkNotNull(engine.prepareProgram(setup))
            assertEquals("Native first program",proposal.program.name)
            assertEquals(3,proposal.program.days.size)
            assertEquals("kg",proposal.profile.unit)
            val drive=Drive();var store=CanonicalWorkoutStorePersistence(drive,engine)
            assertNull(store.readOwnedCurrentStoreBytes()) // review has not written anything
            assertTrue(store.initialize(proposal.snapshot))
            val writes=drive.writes
            assertTrue(store.initialize(proposal.snapshot));assertEquals(writes,drive.writes)
            store=CanonicalWorkoutStorePersistence(drive,engine)
            val bytes=checkNotNull(store.readOwnedCurrentStoreBytes())
            val restored=checkNotNull(engine.inspectProgram(String(bytes,Charsets.UTF_8)));bytes.fill(0)
            assertEquals("program-new",restored.program.id)
            assertEquals("create-new",restored.creationId)
            assertEquals("kg",restored.profile.unit)
            val other=checkNotNull(engine.prepareProgram(NativeProgramSetupCodec.setupJson(profile,
                "create-other","program-other",2000,223)))
            assertFalse(store.initialize(other.snapshot))
            assertEquals(writes,drive.writes)
        } }
    }

    @Test(timeout=60000) fun actualPackagedEnginePublishesHistoryAndRecoversLostCommitAcknowledgement() {
        NodeTransport().use { node -> runBlocking {
            val engine=SandboxCanonicalWorkoutStoreEngine(node)
            val drive=Drive();val recovery=Drive()
            var store=CanonicalWorkoutStorePersistence(drive,engine)
            var journal=WorkoutJournalPersistence(recovery)
            assertFalse(engine.validateInitialSnapshot("{\"v\":99,\"private\":\"not-logged\"}"))
            assertTrue(store.initialize(source))
            finish(store,journal)
            drive.failAfterWrite=true
            assertTrue(runCatching { store.complete(session,12,journal) }.isFailure)
            assertNotNull(journal.recover())
            drive.failAfterWrite=false
            store=CanonicalWorkoutStorePersistence(drive,engine)
            journal=WorkoutJournalPersistence(recovery)
            assertTrue(checkNotNull(store.complete(session,12,journal)).recoveryCleared)
            val snapshot=String(checkNotNull(store.readOwnedCurrentStoreBytes()),Charsets.UTF_8)
            assertEquals(fixture.getJSONObject("preparation").getString("candidateSnapshot"),snapshot)
            val history=JSONObject(snapshot).getJSONArray("history")
            assertEquals(1,history.length())
            val sets=history.getJSONObject(0).getJSONObject("perf").getJSONObject("bb-bench").getJSONArray("sets")
            assertEquals(0,sets.getJSONObject(0).getInt("workingSetIndex"))
            assertEquals(2,sets.getJSONObject(1).getInt("workingSetIndex"))
            assertFalse(sets.getJSONObject(1).getBoolean("rirReported"))
            assertNull(journal.recover())
            val writes=drive.writes
            assertNotNull(store.complete(session,12,journal))
            assertEquals(writes,drive.writes)
        } }
    }

    @Test(timeout=60000) fun cancelledRuntimeResponseCannotPublishHistoryAndExactRetryStillWorks() {
        NodeTransport().use { node -> runBlocking {
            val delayed=CompletableDeferred<String>()
            var blockCompletion=true
            val transport=CanonicalWorkoutStoreTransport { wire ->
                if (JSONObject(wire).getString("method")=="prepareWorkoutHistory" && blockCompletion)
                    delayed.await() else node.request(wire)
            }
            val drive=Drive();val recovery=Drive();val engine=SandboxCanonicalWorkoutStoreEngine(transport)
            val store=CanonicalWorkoutStorePersistence(drive,engine)
            val journal=WorkoutJournalPersistence(recovery)
            assertTrue(store.initialize(source));finish(store,journal)
            val job=launch(start=CoroutineStart.UNDISPATCHED) { store.complete(session,12,journal) }
            job.cancelAndJoin();delayed.complete("late response must not reach decoder or storage")
            assertTrue(checkNotNull(journal.recover()).isFinished)
            assertEquals(source,String(checkNotNull(store.readOwnedCurrentStoreBytes()),Charsets.UTF_8))
            blockCompletion=false
            assertTrue(checkNotNull(store.complete(session,12,journal)).recoveryCleared)
        } }
    }
}
