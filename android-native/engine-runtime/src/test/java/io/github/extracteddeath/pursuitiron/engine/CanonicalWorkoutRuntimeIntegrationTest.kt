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
            // Exercise the real pinned JS worker through the Kotlin transport.
            // A preview cannot persist data or become a workout authorization.
            for(minutes in listOf(45,30,20)) {
                val preview=checkNotNull(engine.previewShorterSession(proposal.snapshot,proposal.program.id,minutes))
                assertEquals("program-new",preview.programId)
                assertEquals(minutes,preview.minutes)
                assertTrue(preview.estimatedMinutes<=preview.originalMinutes)
                assertEquals(preview.changed,preview.cuts.isNotEmpty())
            }
            assertNull(engine.previewShorterSession(proposal.snapshot,"another-program",30))
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
            // The packaged canonical source, not Kotlin, owns muscle exposure.
            val muscleReview=checkNotNull(engine.inspectWorkoutMuscles(proposal.snapshot,plan.issuedJson))
            assertEquals(plan.session.workoutId,muscleReview.workoutId)
            assertEquals(plan.exercises.size,muscleReview.rows.size)
            assertTrue(muscleReview.parts.containsAll(muscleReview.rows.flatMap { it.involvedParts }))
            assertTrue(muscleReview.rows.all { it.primaryPart in it.involvedParts })
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

    @Test(timeout=120000) fun canonicalRelatedMuscleSkipsPreserveCompletedSetsAcrossCrashAndFinish() {
        NodeTransport().use { node -> runBlocking {
            val engine=SandboxCanonicalWorkoutStoreEngine(node)
            val proposal=checkNotNull(engine.prepareProgram(NativeProgramSetupCodec.setupJson(
                NativeProgramProfile(name="Affected session journal",unit="kg"),
                "create-related","program-related",1000,223)))
            val drive=Drive();val recovery=Drive()
            var owner=CanonicalWorkoutStorePersistence(drive,engine)
            var journal=WorkoutJournalPersistence(recovery)
            assertTrue(owner.initialize(proposal.snapshot))
            val plan=checkNotNull(engine.prepareWorkout(proposal.snapshot,
                NativeWorkoutCodec.startJson("program-related","workout-related",2000)))
            val review=checkNotNull(engine.inspectWorkoutMuscles(proposal.snapshot,plan.issuedJson))
            val index=checkNotNull(review.rows.firstOrNull { row->
                plan.slots.count { it.exerciseIndex==row.exerciseIndex }>=2
            }).exerciseIndex
            val part=review.rows.single { it.exerciseIndex==index }.primaryPart
            var progress=checkNotNull(owner.beginIssuedSession(plan,journal))
            val related=NativeSessionSkipPolicy.remainingAffected(progress.journal,review,part)
            assertTrue(related.size>=2)
            val preserved=related.first()
            progress=NativeWorkoutProgress(plan,checkNotNull(journal.recordSet(plan.session,
                progress.journal.revision,JournalSetIntent("preserved-related",preserved,
                    JournalSetDisposition.COMPLETED,JournalSetValues("40","kg",6,2)))))
            val pending=NativeSessionSkipPolicy.remainingAffected(progress.journal,review,part)
            assertTrue(preserved !in pending && pending.isNotEmpty())
            // Lost acknowledgement after an atomic skip cannot cause a second
            // write of that slot or discard the original completed observation.
            recovery.failAfterWrite=true
            assertTrue(runCatching { journal.recordSet(plan.session,progress.journal.revision,
                JournalSetIntent("related-lost-ack",pending.first(),JournalSetDisposition.SKIPPED,null)) }.isFailure)
            recovery.failAfterWrite=false
            owner=CanonicalWorkoutStorePersistence(drive,engine)
            journal=WorkoutJournalPersistence(recovery)
            progress=checkNotNull(owner.recoverIssuedSession(journal))
            assertEquals(plan.issuedJson,progress.plan.issuedJson)
            assertTrue(pending.first() !in NativeSessionSkipPolicy.remainingAffected(progress.journal,review,part))
            for((n,slot) in NativeSessionSkipPolicy.remainingAffected(progress.journal,review,part).withIndex()) {
                progress=NativeWorkoutProgress(plan,checkNotNull(journal.recordSet(plan.session,
                    progress.journal.revision,JournalSetIntent("related-rest-$n",slot,
                        JournalSetDisposition.SKIPPED,null))))
            }
            assertEquals(0,NativeSessionSkipPolicy.remainingAffected(progress.journal,review,part).size)
            val resolved=progress.journal.completed.map { it.intent.slot }.toSet()
            for((n,slot) in plan.slots.filterNot { it in resolved }.withIndex()) {
                progress=NativeWorkoutProgress(plan,checkNotNull(journal.recordSet(plan.session,
                    progress.journal.revision,JournalSetIntent("other-complete-$n",slot,
                        JournalSetDisposition.COMPLETED,JournalSetValues("40","kg",6,null)))))
            }
            assertEquals(0,progress.journal.unresolvedCount)
            assertEquals(JournalSetDisposition.COMPLETED,
                progress.journal.completed.single { it.intent.slot==preserved }.intent.disposition)
            val skipCount=progress.journal.completed.count { it.intent.disposition==JournalSetDisposition.SKIPPED }
            assertEquals(pending.size,skipCount)
            val finish=checkNotNull(journal.finish(plan.session,progress.journal.revision,
                JournalFinishIntent("related-finish",62000,60000)))
            assertTrue(checkNotNull(owner.complete(plan.session,finish.revision,journal)).recoveryCleared)
            val published=String(checkNotNull(owner.readOwnedCurrentStoreBytes()),Charsets.UTF_8)
            val entry=JSONObject(published).getJSONArray("history").getJSONObject(0)
            assertEquals(skipCount,plan.slots.size-entry.getInt("setsDone"))
            assertEquals(plan.slots.size,entry.getInt("totalSets"))
            val evidence=entry.getJSONObject("nativeSetObservations").getJSONArray("events")
            assertEquals(plan.slots.size,evidence.length())
            assertEquals(skipCount,(0 until evidence.length()).count {
                evidence.getJSONObject(it).getString("disposition")=="SKIPPED"
            })
            assertEquals("COMPLETED",(0 until evidence.length()).map {
                evidence.getJSONObject(it)
            }.single { it.getJSONObject("slot").getInt("exerciseIndex")==preserved.exerciseIndex &&
                it.getJSONObject("slot").getInt("setIndex")==preserved.setIndex }
                .getString("disposition"))
            assertNull(owner.recoverIssuedSession(journal))
        } }
    }

    @Test(timeout=120000) fun canonicalReducedAffectedWorkStaysStableAfterLostAcknowledgementAndFinish() {
        NodeTransport().use { node -> runBlocking {
            val engine=SandboxCanonicalWorkoutStoreEngine(node)
            val proposal=checkNotNull(engine.prepareProgram(NativeProgramSetupCodec.setupJson(
                NativeProgramProfile(name="Stable reduced work",goal="hypertrophy",unit="kg"),
                "reduce-create","reduce-program",1000,223)))
            val drive=Drive();val recovery=Drive()
            var owner=CanonicalWorkoutStorePersistence(drive,engine)
            var journal=WorkoutJournalPersistence(recovery)
            assertTrue(owner.initialize(proposal.snapshot))
            val plan=checkNotNull(engine.prepareWorkout(proposal.snapshot,
                NativeWorkoutCodec.startJson("reduce-program","reduce-workout",2000)))
            val review=checkNotNull(engine.inspectWorkoutMuscles(proposal.snapshot,plan.issuedJson))
            var progress=checkNotNull(owner.beginIssuedSession(plan,journal))
            val part=checkNotNull(review.parts.firstOrNull {
                muscle->NativeSessionSkipPolicy.reduceAffected(progress.journal,review,muscle).size>=2
            })
            val initial=NativeSessionSkipPolicy.reduceAffected(progress.journal,review,part)
            assertTrue(initial.size>=2)
            val kept=checkNotNull(plan.slots.firstOrNull { it !in initial })
            progress=NativeWorkoutProgress(plan,checkNotNull(journal.recordSet(plan.session,
                progress.journal.revision,JournalSetIntent("reduce-kept",kept,
                    JournalSetDisposition.COMPLETED,JournalSetValues("40","kg",6,2)))))
            // One confirmed skip was committed but its acknowledgement was lost.
            recovery.failAfterWrite=true
            assertTrue(runCatching { journal.recordSet(plan.session,progress.journal.revision,
                JournalSetIntent("reduce-first",initial.first(),JournalSetDisposition.SKIPPED,null)) }.isFailure)
            recovery.failAfterWrite=false
            journal=WorkoutJournalPersistence(recovery)
            owner=CanonicalWorkoutStorePersistence(drive,engine)
            progress=checkNotNull(owner.recoverIssuedSession(journal))
            assertEquals(plan.issuedJson,progress.plan.issuedJson)
            assertEquals(initial.drop(1),NativeSessionSkipPolicy.reduceAffected(progress.journal,review,part))
            for((i,slot) in NativeSessionSkipPolicy.reduceAffected(progress.journal,review,part).withIndex()) {
                progress=NativeWorkoutProgress(plan,checkNotNull(journal.recordSet(plan.session,progress.journal.revision,
                    JournalSetIntent("reduce-other-$i",slot,JournalSetDisposition.SKIPPED,null))))
            }
            assertTrue(NativeSessionSkipPolicy.reduceAffected(progress.journal,review,part).isEmpty())
            val acknowledged=progress.journal.completed.map { it.intent.slot }.toSet()
            for((i,slot) in plan.slots.filterNot { it in acknowledged }.withIndex()) {
                progress=NativeWorkoutProgress(plan,checkNotNull(journal.recordSet(plan.session,
                    progress.journal.revision,JournalSetIntent("reduce-complete-$i",slot,
                        JournalSetDisposition.COMPLETED,JournalSetValues("40","kg",6,null)))))
            }
            assertEquals(0,progress.journal.unresolvedCount)
            assertEquals(JournalSetDisposition.COMPLETED,
                progress.journal.completed.single { it.intent.slot==kept }.intent.disposition)
            assertEquals(initial.size,
                progress.journal.completed.count { it.intent.disposition==JournalSetDisposition.SKIPPED })
            val finished=checkNotNull(journal.finish(plan.session,progress.journal.revision,
                JournalFinishIntent("reduce-finish",62000,60000)))
            assertTrue(checkNotNull(owner.complete(plan.session,finished.revision,journal)).recoveryCleared)
            val snapshot=String(checkNotNull(owner.readOwnedCurrentStoreBytes()),Charsets.UTF_8)
            val history=JSONObject(snapshot).getJSONArray("history").getJSONObject(0)
            assertEquals(plan.slots.size,history.getInt("totalSets"))
            assertEquals(plan.slots.size-initial.size,history.getInt("setsDone"))
            val events=history.getJSONObject("nativeSetObservations").getJSONArray("events")
            assertEquals(initial.size,(0 until events.length()).count {
                events.getJSONObject(it).getString("disposition")=="SKIPPED"
            })
            val saved=drive.writes
            assertNotNull(owner.complete(plan.session,finished.revision,journal))
            assertEquals(saved,drive.writes)
        } }
    }

    @Test(timeout=120000) fun realShortenedWorkoutRetainsPinnedDoseAndHistoryAcrossRecovery() {
        NodeTransport().use { node -> runBlocking {
            val engine=SandboxCanonicalWorkoutStoreEngine(node)
            val profile=NativeProgramProfile(name="Native short session",goal="hypertrophy",
                experience="beginner",unit="lb",session="s60",
                equipment=checkNotNull(engine.programOptions()).equipment)
            var chosen: Pair<NativeProgramProposal,NativeShortSessionPreview>?=null
            for(seed in listOf(21,223,782,947)) {
                val proposal=checkNotNull(engine.prepareProgram(NativeProgramSetupCodec.setupJson(
                    profile,"short-create","short-program",1000,seed)))
                for(minutes in listOf(45,30,20)) {
                    val preview=checkNotNull(engine.previewShorterSession(proposal.snapshot,"short-program",minutes))
                    if(preview.fits && preview.changed) {
                        chosen=proposal to preview
                        break
                    }
                }
                if(chosen!=null)break
            }
            val (proposal,preview)=checkNotNull(chosen)
            val drive=Drive();val recovery=Drive()
            var owner=CanonicalWorkoutStorePersistence(drive,engine)
            var journal=WorkoutJournalPersistence(recovery)
            assertTrue(owner.initialize(proposal.snapshot))
            val plan=checkNotNull(engine.prepareWorkout(proposal.snapshot,
                NativeWorkoutCodec.startJson("short-program","short-workout",2000,preview.minutes)))
            assertEquals(preview.minutes,plan.shortenedMinutes)
            assertEquals(preview.estimatedMinutes,plan.shortenedEstimatedMinutes)
            val muscleReview=checkNotNull(engine.inspectWorkoutMuscles(proposal.snapshot,plan.issuedJson))
            assertEquals(plan.session.workoutId,muscleReview.workoutId)
            assertEquals(plan.exercises.size,muscleReview.rows.size)
            assertTrue(muscleReview.rows.all { it.primaryPart in it.involvedParts })
            assertTrue(plan.slots.isNotEmpty())
            var progress=checkNotNull(owner.beginIssuedSession(plan,journal))
            owner=CanonicalWorkoutStorePersistence(drive,engine)
            journal=WorkoutJournalPersistence(recovery)
            progress=checkNotNull(owner.recoverIssuedSession(journal))
            assertEquals(plan.issuedJson,progress.plan.issuedJson)
            for((index,slot) in plan.slots.withIndex()) {
                val intent=JournalSetIntent("short-set-$index",slot,
                    JournalSetDisposition.COMPLETED,JournalSetValues("40","lb",6,2))
                progress=NativeWorkoutProgress(progress.plan,checkNotNull(journal.recordSet(
                    plan.session,progress.journal.revision,intent)))
            }
            val finished=checkNotNull(journal.finish(plan.session,progress.journal.revision,
                JournalFinishIntent("short-finish",62000,60000)))
            assertTrue(checkNotNull(owner.complete(plan.session,finished.revision,journal)).recoveryCleared)
            val snapshot=String(checkNotNull(owner.readOwnedCurrentStoreBytes()),Charsets.UTF_8)
            val history=JSONObject(snapshot).getJSONArray("history").getJSONObject(0)
            assertEquals("short-workout",history.getString("id"))
            assertEquals(true,history.getBoolean("timeLimited"))
            assertEquals(true,history.getBoolean("prescriptionEdited"))
            assertEquals(preview.minutes,history.getJSONObject("sessionLengthPlan").getInt("minutes"))
            assertEquals(plan.slots.size,history.getInt("totalSets"))
            assertEquals(plan.slots.size,
                history.getJSONObject("nativeSetObservations").getJSONArray("events").length())
            val writes=drive.writes
            assertNotNull(owner.complete(plan.session,finished.revision,journal))
            assertEquals(writes,drive.writes)
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

    @Test(timeout=60000) fun historyBrowserPagesOlderWorkoutsAndRejectsReadsOverlappingCompletionOrCancellation() {
        NodeTransport().use { node -> runBlocking {
            var block=false;var forgeFilter=false
            var reached=CompletableDeferred<Unit>();var release=CompletableDeferred<Unit>()
            val transport=CanonicalWorkoutStoreTransport { wire ->
                var response=node.request(wire)
                if(forgeFilter && JSONObject(wire).getString("method")=="inspectNativeHistoryPage")
                    response=JSONObject(response).apply { getJSONObject("result").getJSONObject("filter")
                        .put("search","unrequested filter") }.toString()
                if(block && JSONObject(wire).getString("method")=="inspectNativeHistoryPage") {
                    reached.complete(Unit);release.await()
                }
                response
            }
            val engine=SandboxCanonicalWorkoutStoreEngine(transport)
            val proposal=checkNotNull(engine.prepareProgram(NativeProgramSetupCodec.setupJson(
                NativeProgramProfile(unit="kg"),"create-browser","program-browser",1000,223)))
            val seed=JSONObject(proposal.snapshot);val history=seed.getJSONArray("history")
            val legacy=fixture.getJSONObject("preparation").getJSONObject("historyEntry")
            for(n in 0 until 25)history.put(JSONObject(legacy.toString()).put("id","legacy-$n")
                .put("date",100000+n*30000).put("updatedAt",100000+n*30000))
            val drive=Drive();val recovery=Drive();val owner=CanonicalWorkoutStorePersistence(drive,engine)
            val journal=WorkoutJournalPersistence(recovery)
            assertTrue(owner.initialize(seed.toString()))
            for(n in 0 until 4) {
                val source=String(checkNotNull(owner.readOwnedCurrentStoreBytes()),Charsets.UTF_8)
                val started=2000L+n*1000000L
                val plan=checkNotNull(engine.prepareWorkout(source,NativeWorkoutCodec.startJson("program-browser","browser-$n",started)))
                var progress=checkNotNull(owner.beginIssuedSession(plan,journal))
                for((i,slot) in plan.slots.withIndex())progress=NativeWorkoutProgress(plan,checkNotNull(journal.recordSet(
                    plan.session,progress.journal.revision,JournalSetIntent("browser-$n-$i",slot,JournalSetDisposition.COMPLETED,
                        JournalSetValues(if(n==0)"200" else "90","kg",6,null)))))
                val finished=checkNotNull(journal.finish(plan.session,progress.journal.revision,
                    JournalFinishIntent("finish-browser-$n",started+60000,60000)))
                assertTrue(checkNotNull(owner.complete(plan.session,finished.revision,journal)).recoveryCleared)
            }
            val reader=NativeHistoryReadPort(owner,engine);val writes=drive.writes
            val first=checkNotNull(reader.page());assertEquals(29,first.total);assertEquals("browser-3",first.entries[0].historyId)
            val filter=NativeHistoryFilter(legacy.getString("programName"),100000,900000)
            val filteredFirst=checkNotNull(reader.page(filter=filter))
            assertEquals(25,filteredFirst.total);assertEquals(29,filteredFirst.unfilteredTotal);assertEquals(filter,filteredFirst.filter)
            val filteredSecond=checkNotNull(reader.page(checkNotNull(filteredFirst.nextOffset),filteredFirst.snapshotSha256,filter))
            val filteredLast=checkNotNull(reader.page(checkNotNull(filteredSecond.nextOffset),filteredFirst.snapshotSha256,filter))
            val filteredRows=filteredFirst.entries+filteredSecond.entries+filteredLast.entries
            assertEquals(25,filteredRows.size);assertEquals(25,filteredRows.map { it.historyId }.toSet().size)
            assertTrue(filteredRows.all { it.historyId.startsWith("legacy-") });assertEquals(1,filteredLast.entries.size)
            val olderFilter=NativeHistoryFilter(seed.getJSONArray("saved").getJSONObject(0).getString("name"),62000,62001)
            val olderPage=checkNotNull(reader.page(filter=olderFilter))
            assertEquals("browser-0",olderPage.entries.single().historyId)
            val empty=checkNotNull(reader.page(filter=NativeHistoryFilter("no matching program")))
            assertEquals(0,empty.total);assertEquals(29,empty.unfilteredTotal);assertNull(empty.nextOffset)
            forgeFilter=true
            assertTrue(runCatching { reader.page(filter=filter) }.isFailure)
            forgeFilter=false
            val second=checkNotNull(reader.page(checkNotNull(first.nextOffset),first.snapshotSha256))
            val last=checkNotNull(reader.page(checkNotNull(second.nextOffset),first.snapshotSha256))
            assertEquals(5,last.entries.size);assertEquals("browser-0",last.entries.last().historyId)
            assertFalse(first.entries.first { it.historyId.startsWith("legacy-") }.detailsAvailable)
            val older=checkNotNull(reader.workout("browser-0",last.snapshotSha256))
            val before=String(checkNotNull(owner.readOwnedCurrentStoreBytes()),Charsets.UTF_8)
            val latest=JSONObject(before).getJSONArray("history").let { rows->
                (0 until rows.length()).map { rows.getJSONObject(it) }.single { it.getString("id")=="browser-3" }
            }
            val lift=older.exercises.first { latest.getJSONObject("perf").has(it.id) }
            assertEquals(writes,drive.writes)
            val corrected=checkNotNull(owner.correctHistory(NativeHistoryCodec.correctionJson(older,"correct-older",5000000,
                lift.sets.map { NativeHistoryChange(lift.id,it.setIndex,"50",it.reps,it.rir) })))
            assertEquals("browser-3",checkNotNull(owner.lastCompletionReceipt()).session.workoutId)
            val after=String(checkNotNull(owner.readOwnedCurrentStoreBytes()),Charsets.UTF_8)
            assertEquals(JSONObject(before).getJSONObject("perf").getJSONObject(lift.id).toString(),
                JSONObject(after).getJSONObject("perf").getJSONObject(lift.id).toString())
            assertEquals(90.0,checkNotNull(corrected.exercises.single { it.id==lift.id }.record).load,0.0)
            assertNull(reader.page(12,first.snapshotSha256))
            assertNull(reader.page(12,filteredFirst.snapshotSha256,filter))
            val correctedFiltered=checkNotNull(reader.page(filter=olderFilter))
            assertEquals(olderFilter,correctedFiltered.filter)
            assertEquals(olderPage.entries.map { it.historyId to it.date },correctedFiltered.entries.map { it.historyId to it.date })
            assertTrue(correctedFiltered.entries.single().volume<olderPage.entries.single().volume)
            val refreshed=checkNotNull(reader.page());assertEquals(first.entries.map { it.historyId },refreshed.entries.map { it.historyId })
            val refreshedLast=checkNotNull(reader.page(24,refreshed.snapshotSha256))
            assertEquals(last.entries.map { it.historyId },refreshedLast.entries.map { it.historyId })
            val request=NativeWorkoutCodec.startJson("program-browser","browser-next",6000000)
            val nextBefore=checkNotNull(engine.prepareWorkout(before,request));val next=checkNotNull(engine.prepareWorkout(after,request))
            assertEquals(nextBefore.session.dayId,next.session.dayId);assertEquals(nextBefore.session.weekIndex,next.session.weekIndex)
            var progress=checkNotNull(owner.beginIssuedSession(next,journal))
            assertNotNull(reader.workout("browser-0",refreshed.snapshotSha256)) // Read-only browsing during an active workout.
            assertNull(owner.correctHistory(NativeHistoryCodec.correctionJson(corrected,"blocked-active-edit",5100000,
                listOf(NativeHistoryChange(lift.id,lift.sets[0].setIndex,"55",6,null)))))
            block=true
            val stale=async { reader.page(filter=filter) };reached.await()
            for((i,slot) in next.slots.withIndex())progress=NativeWorkoutProgress(next,checkNotNull(journal.recordSet(
                next.session,progress.journal.revision,JournalSetIntent("skip-next-$i",slot,JournalSetDisposition.SKIPPED,null))))
            val finished=checkNotNull(journal.finish(next.session,progress.journal.revision,JournalFinishIntent("finish-next",6060000,60000)))
            assertNotNull(owner.complete(next.session,finished.revision,journal))
            release.complete(Unit);assertNull(stale.await())
            block=false;assertEquals(30,checkNotNull(reader.page()).total)
            reached=CompletableDeferred();release=CompletableDeferred();block=true
            val beforeCancel=drive.writes
            val cancelled=async { reader.page(filter=filter) };reached.await();cancelled.cancelAndJoin();release.complete(Unit)
            assertEquals(beforeCancel,drive.writes)
            assertEquals("browser-next",checkNotNull(owner.lastCompletionReceipt()).session.workoutId)
            block=false
            val reopened=NativeHistoryReadPort(CanonicalWorkoutStorePersistence(drive,engine),engine)
            assertEquals(30,checkNotNull(reopened.page()).total)
            val restoredFilter=checkNotNull(reopened.page(filter=filter))
            assertEquals(25,restoredFilter.total);assertEquals(30,restoredFilter.unfilteredTotal)
            assertEquals(filteredFirst.entries,restoredFilter.entries)
            assertEquals("browser-0",checkNotNull(reopened.workout("browser-0")).historyId)
        } }
    }

    @Test(timeout=60000) fun activeEditsSurviveRecoveryAndPublishLatestValuesWithOriginalAudit() {
        NodeTransport().use { node -> runBlocking {
            val engine=SandboxCanonicalWorkoutStoreEngine(node)
            for(tech in listOf("myo-reps","drop set")) {
                val suffix=if(tech=="myo-reps")"myo" else "drop"
                val programId="program-active-$suffix";val workoutId="workout-active-$suffix"
                val proposal=checkNotNull(engine.prepareProgram(NativeProgramSetupCodec.setupJson(
                    NativeProgramProfile(unit="kg"),"create-active-$suffix",programId,1000,223)))
                val seeded=JSONObject(proposal.snapshot);val program=seeded.getJSONArray("saved").getJSONObject(0)
                val day=program.getJSONArray("days").getJSONObject(0)
                program.getJSONObject("nextWeekPrescriptions").getJSONObject(day.getString("id")+":0")
                    .getJSONObject("1").put("tech",tech)
                val origin=seeded.toString()
                val plan=checkNotNull(engine.prepareWorkout(origin,NativeWorkoutCodec.startJson(programId,workoutId,2000)))
                val extension=plan.exercises[0].sets.first { it.isExtension }
                val drive=Drive();val recovery=Drive()
                var owner=CanonicalWorkoutStorePersistence(drive,engine);var journal=WorkoutJournalPersistence(recovery)
                assertTrue(owner.initialize(origin))
                var progress=checkNotNull(owner.beginIssuedSession(plan,journal))
                val first=plan.slots[0]
                val initial=JournalSetIntent("original-first",first,JournalSetDisposition.COMPLETED,JournalSetValues("40","kg",6,null))
                progress=NativeWorkoutProgress(plan,checkNotNull(journal.recordSet(plan.session,0,initial)))
                val correction=initial.copy(operationId="edit-first",values=JournalSetValues("0","kg",8,2),replacesOperationId=initial.operationId)
                recovery.failBeforeWrite=true
                assertTrue(runCatching { journal.recordSet(plan.session,1,correction) }.isFailure)
                assertEquals("40",checkNotNull(journal.recover()).completed.single().intent.values?.load)
                recovery.failBeforeWrite=false;recovery.failAfterWrite=true
                assertTrue(runCatching { journal.recordSet(plan.session,1,correction) }.isFailure)
                recovery.failAfterWrite=false
                owner=CanonicalWorkoutStorePersistence(drive,engine);journal=WorkoutJournalPersistence(recovery)
                progress=checkNotNull(owner.recoverIssuedSession(journal))
                assertEquals(plan.issuedJson,progress.plan.issuedJson)
                assertEquals(2,progress.journal.events.size);assertEquals(1,progress.journal.completed.size)
                assertEquals("0",progress.journal.completed.single().intent.values?.load)
                val writes=recovery.writes
                assertNotNull(journal.recordSet(plan.session,1,correction))
                assertEquals(writes,recovery.writes)
                assertEquals("0",checkNotNull(journal.recordSet(plan.session,0,initial)).completed.single().intent.values?.load)
                assertNull(journal.recordSet(plan.session,progress.journal.revision,correction.copy(operationId="stale-edit")))
                for((index,slot) in plan.slots.withIndex().drop(1)) {
                    val skipped=index==1
                    progress=NativeWorkoutProgress(plan,checkNotNull(journal.recordSet(plan.session,progress.journal.revision,
                        JournalSetIntent("original-$index",slot,if(skipped)JournalSetDisposition.SKIPPED else JournalSetDisposition.COMPLETED,
                            if(skipped)null else JournalSetValues("40","kg",6,null)))))
                }
                for((slot,load,reps) in listOf(Triple(plan.slots[1],"45",8),Triple(extension.slot,"25",5))) {
                    val prior=progress.journal.completed.single { it.intent.slot==slot }
                    progress=NativeWorkoutProgress(plan,checkNotNull(journal.recordSet(plan.session,progress.journal.revision,
                        JournalSetIntent("correct-${slot.setIndex}",slot,JournalSetDisposition.COMPLETED,
                            JournalSetValues(load,"kg",reps,0),prior.intent.operationId))))
                }
                val finished=checkNotNull(journal.finish(plan.session,progress.journal.revision,JournalFinishIntent("finish-active",62000,60000)))
                val handoff=JSONObject(checkNotNull(journal.finishedHandoff(plan.session,finished.revision)))
                assertEquals(3,handoff.getInt("format"))
                assertEquals(plan.slots.size+3,handoff.getJSONArray("events").length())
                drive.failAfterWrite=true
                assertTrue(runCatching { owner.complete(plan.session,finished.revision,journal) }.isFailure)
                drive.failAfterWrite=false;owner=CanonicalWorkoutStorePersistence(drive,engine);journal=WorkoutJournalPersistence(recovery)
                assertTrue(checkNotNull(owner.complete(plan.session,finished.revision,journal)).recoveryCleared)
                assertNull(journal.recover())
                val snapshot=String(checkNotNull(owner.readOwnedCurrentStoreBytes()),Charsets.UTF_8)
                val entry=JSONObject(snapshot).getJSONArray("history").getJSONObject(0)
                assertEquals(plan.slots.size,entry.getInt("setsDone"));assertEquals(plan.slots.size,entry.getInt("totalSets"))
                val audit=entry.getJSONObject("nativeSetRevisions")
                assertEquals(handoff.getJSONArray("events").toString(),audit.getJSONArray("events").toString())
                assertEquals("40",audit.getJSONArray("events").getJSONObject(0).getJSONObject("values").getString("load"))
                assertEquals(plan.slots.size,entry.getJSONObject("nativeSetObservations").getJSONArray("events").length())
                val sets=entry.getJSONObject("perf").getJSONObject(first.exerciseId).getJSONArray("sets")
                val main=(0 until sets.length()).map { sets.getJSONObject(it) }.filter { !it.optBoolean("sub") }
                assertFalse(main.any { it.getInt("workingSetIndex")==0 })
                assertTrue(main.any { it.getInt("workingSetIndex")==1 && it.getInt("w")==45 && it.getInt("r")==8 })
                assertTrue((0 until sets.length()).map { sets.getJSONObject(it) }.any { it.optBoolean("sub") && it.getInt("w")==25 })
                val view=checkNotNull(engine.inspectHistory(snapshot,workoutId));val ex=view.exercises[0];val set=ex.sets[0]
                assertNotNull(owner.correctHistory(NativeHistoryCodec.correctionJson(view,"later-history-edit",70000,
                    listOf(NativeHistoryChange(ex.id,set.setIndex,"50",9,null)))))
                val after=String(checkNotNull(owner.readOwnedCurrentStoreBytes()),Charsets.UTF_8)
                assertEquals(audit.toString(),JSONObject(after).getJSONArray("history").getJSONObject(0).getJSONObject("nativeSetRevisions").toString())
                val next=checkNotNull(engine.prepareWorkout(after,NativeWorkoutCodec.startJson(programId,"next-$suffix",71000)))
                assertNotEquals(plan.session.dayId,next.session.dayId)
                assertNotNull(owner.beginIssuedSession(next,journal))
            }
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
