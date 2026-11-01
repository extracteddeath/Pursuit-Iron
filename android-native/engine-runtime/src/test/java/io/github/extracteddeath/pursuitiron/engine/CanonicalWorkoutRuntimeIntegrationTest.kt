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
        var failBeforeWrite=false
        override fun read()=raw?.copyOf()
        override fun commitAtomically(bytes: ByteArray) {
            if (failBeforeWrite) throw java.io.IOException("Synthetic disk-full before commit")
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

    @Test(timeout=60000) fun realNativeJourneyPinsTargetsResumesLoggedSetsAndCompletesExactlyOnce() {
        NodeTransport().use { node -> runBlocking {
            val engine=SandboxCanonicalWorkoutStoreEngine(node)
            val proposal=checkNotNull(engine.prepareProgram(NativeProgramSetupCodec.setupJson(
                NativeProgramProfile(name="End to end native",unit="kg"),"create-journey","program-journey",1000,223)))
            val drive=Drive();val recovery=Drive()
            var store=CanonicalWorkoutStorePersistence(drive,engine)
            var journal=WorkoutJournalPersistence(recovery)
            assertTrue(store.initialize(proposal.snapshot))
            val plan=checkNotNull(engine.prepareWorkout(proposal.snapshot,
                NativeWorkoutCodec.startJson("program-journey","workout-journey",2000)))
            assertEquals("kg",plan.session.unit);assertEquals(1,plan.session.weekIndex)
            // Crash between pinning the issued targets and creating the journal.
            drive.failAfterWrite=true
            assertTrue(runCatching { store.beginIssuedSession(plan,journal) }.isFailure)
            assertNull(journal.recover());drive.failAfterWrite=false
            store=CanonicalWorkoutStorePersistence(drive,engine);journal=WorkoutJournalPersistence(recovery)
            var progress=checkNotNull(store.recoverIssuedSession(journal))
            assertEquals(plan.issuedJson,progress.plan.issuedJson)
            assertEquals(plan.slots,progress.journal.slots)
            val competing=checkNotNull(engine.prepareWorkout(proposal.snapshot,
                NativeWorkoutCodec.startJson("program-journey","workout-competitor",3000)))
            assertNull(store.beginIssuedSession(competing,journal))
            for((index,slot) in plan.slots.withIndex()) {
                val skipped=index==1
                val values=if(skipped)null else JournalSetValues(if(index==0)"0" else "40",plan.session.unit,6,if(index%2==0)2 else null)
                val intent=JournalSetIntent("journey-set-$index",slot,
                    if(skipped)JournalSetDisposition.SKIPPED else JournalSetDisposition.COMPLETED,values)
                // Lost acknowledgement must recover one saved observation, not
                // invent a second set or change an engine-issued target.
                if(index==0) {
                    recovery.failAfterWrite=true
                    assertTrue(runCatching { journal.recordSet(plan.session,progress.journal.revision,intent) }.isFailure)
                    recovery.failAfterWrite=false
                    store=CanonicalWorkoutStorePersistence(drive,engine);journal=WorkoutJournalPersistence(recovery)
                    progress=checkNotNull(store.recoverIssuedSession(journal))
                } else progress=NativeWorkoutProgress(progress.plan,checkNotNull(journal.recordSet(
                    plan.session,progress.journal.revision,intent)))
                assertEquals(index+1,progress.journal.completed.size)
                assertEquals(plan.issuedJson,progress.plan.issuedJson)
            }
            assertEquals(0,progress.journal.unresolvedCount)
            assertEquals(proposal.snapshot,String(checkNotNull(store.readOwnedCurrentStoreBytes()),Charsets.UTF_8))
            val finished=checkNotNull(journal.finish(plan.session,progress.journal.revision,
                JournalFinishIntent("journey-finish",62000,60000)))
            // Canonical history lands before journal removal, even if its ack is lost.
            drive.failAfterWrite=true
            assertTrue(runCatching { store.complete(plan.session,finished.revision,journal) }.isFailure)
            assertTrue(checkNotNull(journal.recover()).isFinished);drive.failAfterWrite=false
            store=CanonicalWorkoutStorePersistence(drive,engine);journal=WorkoutJournalPersistence(recovery)
            assertTrue(checkNotNull(store.recoverIssuedSession(journal)).journal.isFinished)
            assertTrue(checkNotNull(store.complete(plan.session,finished.revision,journal)).recoveryCleared)
            assertNull(store.recoverIssuedSession(journal));assertNull(journal.recover())
            val snapshot=String(checkNotNull(store.readOwnedCurrentStoreBytes()),Charsets.UTF_8)
            val history=JSONObject(snapshot).getJSONArray("history");assertEquals(1,history.length())
            val recorded=history.getJSONObject(0).getJSONObject("perf").getJSONObject(plan.exercises.first().id).getJSONArray("sets")
            assertEquals(2,recorded.getJSONObject(0).getInt("workingSetIndex"))
            val nativeEvents=history.getJSONObject(0).getJSONObject("nativeSetObservations").getJSONArray("events")
            assertEquals(plan.slots.size,nativeEvents.length())
            assertEquals("0",nativeEvents.getJSONObject(0).getJSONObject("values").getString("load"))
            assertEquals("SKIPPED",nativeEvents.getJSONObject(1).getString("disposition"))
            val trace=recorded.getJSONObject(0).getJSONObject("decisionTrace")
            assertEquals("user",trace.getJSONObject("applied").getString("owner"))
            assertEquals(40,trace.getJSONObject("applied").getInt("load"))
            val issued=JSONObject(plan.issuedJson).getJSONArray("rows").getJSONObject(0).getJSONArray("sets")
            val original=(0 until issued.length()).map { issued.getJSONObject(it) }.filter { !it.getBoolean("warm") }[2]
                .getJSONObject("target").getJSONObject("decisionTrace")
            assertEquals(original.getJSONObject("evaluation").toString(),trace.getJSONObject("evaluation").toString())
            assertEquals(original.getJSONObject("target").toString(),trace.getJSONObject("target").toString())
            val writes=drive.writes
            assertNotNull(store.complete(plan.session,finished.revision,journal));assertEquals(writes,drive.writes)
            val next=checkNotNull(engine.prepareWorkout(snapshot,
                NativeWorkoutCodec.startJson("program-journey","workout-next",63000)))
            assertNotEquals(plan.session.dayId,next.session.dayId)
            assertEquals(1,next.session.weekIndex)
            assertNotEquals(plan.session.programRevision.snapshotSha256,next.session.programRevision.snapshotSha256)
            assertNotNull(store.beginIssuedSession(next,journal))
            assertEquals("workout-next",checkNotNull(store.recoverIssuedSession(journal)).plan.session.workoutId)
            assertEquals("program-journey",checkNotNull(engine.inspectProgram(snapshot)).program.id)
        } }
    }

    @Test(timeout=120000) fun canonicalMyoAndDropExtensionsSurviveNativeJournalAndHistory() {
        NodeTransport().use { node -> runBlocking {
            val engine=SandboxCanonicalWorkoutStoreEngine(node)
            val proposal=checkNotNull(engine.prepareProgram(NativeProgramSetupCodec.setupJson(
                NativeProgramProfile(name="Advanced extensions",unit="kg"),
                "create-advanced","program-advanced",1000,223)))
            for ((method,kind,count) in listOf(
                Triple("myo-reps","myo",3),Triple("drop set","drop",2)
            )) {
                // Author the test cue in an actual saved canonical weekly cell.
                // The JS engine still generates all loads and extension rows.
                val source=JSONObject(proposal.snapshot)
                val program=source.getJSONArray("saved").getJSONObject(0)
                val firstDay=program.getJSONArray("days").getJSONObject(0)
                val prescription=program.getJSONObject("nextWeekPrescriptions")
                    .getJSONObject("${firstDay.getString("id")}:0").getJSONObject("1")
                prescription.put("tech",method)
                val original=source.toString()
                val drive=Drive();val recovery=Drive()
                val store=CanonicalWorkoutStorePersistence(drive,engine)
                val journal=WorkoutJournalPersistence(recovery)
                assertTrue(store.initialize(original))
                val plan=checkNotNull(engine.prepareWorkout(original,
                    NativeWorkoutCodec.startJson("program-advanced","workout-${kind}",2000)))
                val extensions=plan.exercises.first().sets.filter { it.isExtension }
                assertEquals(count,extensions.size)
                assertTrue(extensions.all { it.kind==kind && it.extensionRestSeconds==15 })
                var progress=checkNotNull(store.beginIssuedSession(plan,journal))
                for((index,slot) in plan.slots.withIndex()) {
                    progress=NativeWorkoutProgress(plan,checkNotNull(journal.recordSet(plan.session,
                        progress.journal.revision,JournalSetIntent("extension-set-$index",slot,
                            JournalSetDisposition.COMPLETED,JournalSetValues("40","kg",6,null)))))
                }
                assertEquals(0,progress.journal.unresolvedCount)
                val finished=checkNotNull(journal.finish(plan.session,progress.journal.revision,
                    JournalFinishIntent("extension-finish",62000,60000)))
                assertTrue(checkNotNull(store.complete(plan.session,finished.revision,journal)).recoveryCleared)
                val stored=JSONObject(String(checkNotNull(store.readOwnedCurrentStoreBytes()),Charsets.UTF_8))
                val history=stored.getJSONArray("history").getJSONObject(0)
                val observations=history.getJSONObject("nativeSetObservations").getJSONArray("events")
                assertEquals(plan.slots.size,observations.length())
                val logged=history.getJSONObject("perf").getJSONObject(plan.exercises.first().id)
                    .getJSONArray("sets")
                val loggedKinds=(0 until logged.length()).map { logged.getJSONObject(it) }
                    .filter { it.optBoolean("sub",false) }.map { it.getString("kind") }
                assertEquals(List(count){kind},loggedKinds)
                assertNull(journal.recover())
            }
        } }
    }

    @Test(timeout=60000) fun deloadConsumptionCommitsWithHistoryAndSurvivesBothCommitFailureWindows() {
        NodeTransport().use { node -> runBlocking {
            val engine=SandboxCanonicalWorkoutStoreEngine(node)
            val proposal=checkNotNull(engine.prepareProgram(NativeProgramSetupCodec.setupJson(
                NativeProgramProfile(name="One-shot deload",unit="kg"),
                "create-deload","program-deload",1000,223)))
            val start=NativeWorkoutCodec.startJson("program-deload","workout-deload",2000)
            val seeded=JSONObject(proposal.snapshot)
            val days=seeded.getJSONArray("saved").getJSONObject(0).getJSONArray("days")
            val prior=org.json.JSONArray()
            for(index in 0 until days.length()) {
                val day=days.getJSONObject(index);val ids=day.getJSONArray("exercises");val perf=JSONObject()
                for(slot in 0 until ids.length()) perf.put(ids.getString(slot),JSONObject()
                    .put("weight",100).put("reps",6).put("sets",org.json.JSONArray().put(JSONObject()
                        .put("w",100).put("r",6).put("rir",2).put("rirReported",true).put("workingSetIndex",0))))
                prior.put(JSONObject().put("id","prior-$index").put("programId","program-deload")
                    .put("dayId",day.getString("id")).put("dayLabel",day.getString("label"))
                    .put("date",1100+index).put("updatedAt",1100+index).put("weekIndex",1)
                    .put("unit","kg").put("perf",perf))
            }
            seeded.put("history",prior)
            val baseline=checkNotNull(engine.prepareWorkout(seeded.toString(),start))
            val trained=baseline.exercises[0];val skipped=baseline.exercises[1]
            assertTrue(trained.sets[0].load.toDouble()>0)
            val original=seeded
            original.getJSONArray("saved").getJSONObject(0)
                .put("deloadNext",JSONObject().put(trained.id,0.9).put(skipped.id,0.9))
                .put("updatedAt",62000)
            val origin=original.toString()
            val plan=checkNotNull(engine.prepareWorkout(origin,start))
            val firstWork=plan.exercises[0].sets[0]
            val trace=JSONObject(plan.issuedJson).getJSONArray("rows").getJSONObject(0).getJSONArray("sets")
            val traced=(0 until trace.length()).map { trace.getJSONObject(it) }.first { !it.getBoolean("warm") }
            assertEquals("user_one_shot_deload",traced.getJSONObject("target")
                .getJSONObject("decisionTrace").getJSONObject("evaluation").getString("reasonCode"))
            assertEquals(firstWork.load,traced.getString("weight"))
            assertTrue(firstWork.load.toDouble()<trained.sets[0].load.toDouble())
            val drive=Drive();val recovery=Drive()
            var store=CanonicalWorkoutStorePersistence(drive,engine)
            var journal=WorkoutJournalPersistence(recovery)
            assertTrue(store.initialize(origin))
            var progress=checkNotNull(store.beginIssuedSession(plan,journal))
            for((index,slot) in plan.slots.withIndex()) {
                val isSkipped=slot.exerciseId==skipped.id
                progress=NativeWorkoutProgress(plan,checkNotNull(journal.recordSet(plan.session,
                    progress.journal.revision,JournalSetIntent("deload-set-$index",slot,
                        if(isSkipped)JournalSetDisposition.SKIPPED else JournalSetDisposition.COMPLETED,
                        if(isSkipped)null else JournalSetValues("40","kg",6,null)))))
                if(index==0) {
                    store=CanonicalWorkoutStorePersistence(drive,engine);journal=WorkoutJournalPersistence(recovery)
                    progress=checkNotNull(store.recoverIssuedSession(journal))
                    assertEquals(plan.issuedJson,progress.plan.issuedJson)
                }
            }
            val finished=checkNotNull(journal.finish(plan.session,progress.journal.revision,
                JournalFinishIntent("deload-finish",62000,60000)))
            drive.failBeforeWrite=true
            assertTrue(runCatching { store.complete(plan.session,finished.revision,journal) }.isFailure)
            assertEquals(origin,String(checkNotNull(store.readOwnedCurrentStoreBytes()),Charsets.UTF_8))
            assertTrue(checkNotNull(journal.recover()).isFinished)
            drive.failBeforeWrite=false;drive.failAfterWrite=true
            assertTrue(runCatching { store.complete(plan.session,finished.revision,journal) }.isFailure)
            drive.failAfterWrite=false
            val committed=String(checkNotNull(store.readOwnedCurrentStoreBytes()),Charsets.UTF_8)
            val stored=JSONObject(committed);val program=stored.getJSONArray("saved").getJSONObject(0)
            assertFalse(program.getJSONObject("deloadNext").has(trained.id))
            assertEquals(0.9,program.getJSONObject("deloadNext").getDouble(skipped.id),0.00001)
            assertEquals(62001,program.getInt("updatedAt"))
            assertEquals(prior.length()+1,stored.getJSONArray("history").length())
            assertTrue(checkNotNull(journal.recover()).isFinished)
            store=CanonicalWorkoutStorePersistence(drive,engine);journal=WorkoutJournalPersistence(recovery)
            assertEquals(plan.issuedJson,checkNotNull(store.recoverIssuedSession(journal)).plan.issuedJson)
            assertTrue(checkNotNull(store.complete(plan.session,finished.revision,journal)).recoveryCleared)
            assertEquals(committed,String(checkNotNull(store.readOwnedCurrentStoreBytes()),Charsets.UTF_8))
            assertNull(journal.recover())
            val writes=drive.writes
            assertNotNull(store.complete(plan.session,finished.revision,journal));assertEquals(writes,drive.writes)
            val next=checkNotNull(engine.prepareWorkout(committed,
                NativeWorkoutCodec.startJson("program-deload","workout-after-deload",63000)))
            assertNotEquals(plan.session.dayId,next.session.dayId)
            assertNotNull(store.beginIssuedSession(next,journal))
        } }
    }

    @Test(timeout=60000) fun canonicalHistoryCorrectionSurvivesDiskFullLostAcknowledgementRestartAndExactRetry() {
        NodeTransport().use { node -> runBlocking {
            val engine=SandboxCanonicalWorkoutStoreEngine(node)
            val proposal=checkNotNull(engine.prepareProgram(NativeProgramSetupCodec.setupJson(
                NativeProgramProfile(name="Correction journey",unit="kg"),"create-correction","program-correction",1000,223)))
            val source=JSONObject(proposal.snapshot)
            val firstDay=source.getJSONArray("saved").getJSONObject(0).getJSONArray("days").getJSONObject(0)
            source.getJSONArray("saved").getJSONObject(0).getJSONObject("nextWeekPrescriptions")
                .getJSONObject(firstDay.getString("id")+":0").getJSONObject("1").put("tech","drop set")
            val plan=checkNotNull(engine.prepareWorkout(source.toString(),NativeWorkoutCodec.startJson(
                "program-correction","workout-correction",2000)))
            assertTrue(plan.exercises[0].sets.any { it.isExtension })
            val drive=Drive();val recovery=Drive()
            var store=CanonicalWorkoutStorePersistence(drive,engine)
            var journal=WorkoutJournalPersistence(recovery)
            assertTrue(store.initialize(source.toString()))
            var progress=checkNotNull(store.beginIssuedSession(plan,journal))
            for((index,slot) in plan.slots.withIndex()) {
                progress=NativeWorkoutProgress(plan,checkNotNull(journal.recordSet(plan.session,progress.journal.revision,
                    JournalSetIntent("correct-set-$index",slot,
                        if(index==1)JournalSetDisposition.SKIPPED else JournalSetDisposition.COMPLETED,
                        if(index==1)null else JournalSetValues("200","kg",6,null)))))
            }
            val finish=checkNotNull(journal.finish(plan.session,progress.journal.revision,
                JournalFinishIntent("finish-correction",62000,60000)))
            assertTrue(checkNotNull(store.complete(plan.session,finish.revision,journal)).recoveryCleared)
            val before=String(checkNotNull(store.readOwnedCurrentStoreBytes()),Charsets.UTF_8)
            val view=checkNotNull(engine.inspectHistory(before,"workout-correction"))
            val lift=view.exercises[0]
            assertTrue(lift.sets.any { it.kind=="drop" })
            val changes=lift.sets.filter { it.kind=="working" }.map {
                NativeHistoryChange(lift.id,it.setIndex,"50",8,4)
            }
            val command=NativeHistoryCodec.correctionJson(view,"correct-once",70000,changes)
            var notifications=0
            store.subscribeCommittedChanges { notifications++ }
            drive.failBeforeWrite=true
            assertTrue(runCatching { store.correctHistory(command) }.isFailure)
            assertEquals(before,String(checkNotNull(store.readOwnedCurrentStoreBytes()),Charsets.UTF_8))
            assertEquals(0,notifications)
            drive.failBeforeWrite=false;drive.failAfterWrite=true
            assertTrue(runCatching { store.correctHistory(command) }.isFailure)
            drive.failAfterWrite=false
            assertEquals(1,notifications)
            val committed=String(checkNotNull(store.readOwnedCurrentStoreBytes()),Charsets.UTF_8)
            assertNotEquals(before,committed)
            val oldEntry=JSONObject(before).getJSONArray("history").getJSONObject(0)
            val entry=JSONObject(committed).getJSONArray("history").getJSONObject(0)
            assertEquals(oldEntry.getInt("volume")-changes.size*200*6+changes.size*50*8,entry.getInt("volume"))
            assertEquals(oldEntry.getInt("setsDone"),entry.getInt("setsDone"))
            assertEquals(oldEntry.getJSONObject("nativeCompletion").toString(),entry.getJSONObject("nativeCompletion").toString())
            store=CanonicalWorkoutStorePersistence(drive,engine);journal=WorkoutJournalPersistence(recovery)
            val writes=drive.writes
            val corrected=checkNotNull(store.correctHistory(command))
            assertEquals(writes,drive.writes)
            assertEquals(50.0,checkNotNull(corrected.exercises[0].record).load,0.0)
            assertTrue(corrected.exercises[0].sets.filter { it.kind=="working" }.all { it.rir==4 && it.reps==8 })
            assertTrue(corrected.exercises[0].sets.filter { it.kind=="drop" }.all { it.load=="200" && it.rir==null })
            assertEquals(committed,String(checkNotNull(store.readOwnedCurrentStoreBytes()),Charsets.UTF_8))
            assertNotNull(store.complete(plan.session,finish.revision,journal))
            assertEquals(writes,drive.writes)
            assertNull(journal.recover())
            val stale=NativeHistoryCodec.correctionJson(view,"different-stale-operation",70001,changes)
            assertNull(store.correctHistory(stale))
            val next=checkNotNull(engine.prepareWorkout(committed,NativeWorkoutCodec.startJson(
                "program-correction","workout-after-correction",71000)))
            assertNotEquals(plan.session.dayId,next.session.dayId)
            assertNotNull(store.beginIssuedSession(next,journal))
            val pendingWrites=drive.writes
            assertNull(store.correctHistory(NativeHistoryCodec.correctionJson(corrected,"during-workout",72000,
                listOf(changes[0].copy(load="55")))))
            assertEquals(pendingWrites,drive.writes)
            assertNotNull(store.complete(plan.session,finish.revision,journal))
            assertEquals(next.session,checkNotNull(journal.recover()).session)
            assertEquals(next.issuedJson,checkNotNull(store.recoverIssuedSession(journal)).plan.issuedJson)
        } }
    }

    @Test(timeout=60000) fun historyCorrectionRejectsChangedResponseAndConcurrentWorkoutWithoutPublishing() {
        NodeTransport().use { node -> runBlocking {
            val actual=SandboxCanonicalWorkoutStoreEngine(node)
            val proposal=checkNotNull(actual.prepareProgram(NativeProgramSetupCodec.setupJson(
                NativeProgramProfile(unit="kg"),"create-conflict","program-conflict",1000,223)))
            val drive=Drive();val recovery=Drive()
            val reached=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>()
            var delayCorrection=false;var corrupt=false
            val transport=CanonicalWorkoutStoreTransport { wire ->
                val response=node.request(wire)
                if(JSONObject(wire).getString("method")=="prepareNativeHistoryCorrection") {
                    if(delayCorrection) { reached.complete(Unit);release.await() }
                    if(corrupt)JSONObject(response).apply {
                        getJSONObject("result").put("inputCorrectionSha256","f".repeat(64))
                    }.toString() else response
                } else response
            }
            val engine=SandboxCanonicalWorkoutStoreEngine(transport)
            val store=CanonicalWorkoutStorePersistence(drive,engine);val journal=WorkoutJournalPersistence(recovery)
            assertTrue(store.initialize(proposal.snapshot))
            val plan=checkNotNull(engine.prepareWorkout(proposal.snapshot,
                NativeWorkoutCodec.startJson("program-conflict","workout-conflict",2000)))
            var progress=checkNotNull(store.beginIssuedSession(plan,journal))
            for((index,slot) in plan.slots.withIndex())progress=NativeWorkoutProgress(plan,
                checkNotNull(journal.recordSet(plan.session,progress.journal.revision,JournalSetIntent("conflict-$index",slot,
                    JournalSetDisposition.COMPLETED,JournalSetValues("40","kg",6,null)))))
            val finish=checkNotNull(journal.finish(plan.session,progress.journal.revision,JournalFinishIntent("conflict-finish",62000,60000)))
            assertNotNull(store.complete(plan.session,finish.revision,journal))
            val snapshot=String(checkNotNull(store.readOwnedCurrentStoreBytes()),Charsets.UTF_8)
            val view=checkNotNull(engine.inspectHistory(snapshot,"workout-conflict"))
            val ex=view.exercises[0];val set=ex.sets[0]
            val command=NativeHistoryCodec.correctionJson(view,"correct-conflict",70000,
                listOf(NativeHistoryChange(ex.id,set.setIndex,"45",7,null)))
            val writes=drive.writes;corrupt=true
            assertTrue(runCatching { store.correctHistory(command) }.isFailure)
            assertEquals(writes,drive.writes)
            corrupt=false;delayCorrection=true
            val next=checkNotNull(engine.prepareWorkout(snapshot,
                NativeWorkoutCodec.startJson("program-conflict","workout-concurrent",71000)))
            val correction=async { store.correctHistory(command) }
            reached.await()
            assertNotNull(store.beginIssuedSession(next,journal))
            val pendingWrites=drive.writes
            release.complete(Unit)
            assertNull(correction.await())
            assertEquals(pendingWrites,drive.writes)
            assertEquals(snapshot,String(checkNotNull(store.readOwnedCurrentStoreBytes()),Charsets.UTF_8))
            assertEquals(next.session,checkNotNull(journal.recover()).session)
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
