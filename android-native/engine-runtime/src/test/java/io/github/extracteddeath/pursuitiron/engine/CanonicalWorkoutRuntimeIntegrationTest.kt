package io.github.extracteddeath.pursuitiron.engine

import io.github.extracteddeath.pursuitiron.domain.*
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.YearMonth
import java.time.ZoneOffset

/** Actual packaged canonical JS -> Kotlin transport -> atomic store/journal. No APK. */
class CanonicalWorkoutRuntimeIntegrationTest {
    private fun sha(raw: String)=java.security.MessageDigest.getInstance("SHA-256")
        .digest(raw.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 255) }
    private class Drive : CanonicalWorkoutStoreBytes, WorkoutJournalBytes {
        var raw: ByteArray?=null
        var writes=0
        var failAfterWrite=false
        var failBeforeWrite=false
        var failReadAfterWrite=false
        private var failNextRead=false
        override fun read(): ByteArray? {
            if(failNextRead) {failNextRead=false;throw java.io.IOException("Synthetic post-commit read failure")}
            return raw?.copyOf()
        }
        override fun commitAtomically(bytes: ByteArray) {
            if (failBeforeWrite) throw java.io.IOException("Synthetic disk-full before commit")
            writes++;raw=bytes.copyOf()
            if(failReadAfterWrite)failNextRead=true
            if (failAfterWrite) throw java.io.IOException("Synthetic post-commit crash")
        }
        override fun clearAtomically() { raw=null }
    }
    private class BackupDocument: NativeBackupExportDocument {
        val output=ByteArrayOutputStream();var discarded=false
        override fun openOutput()=output
        override fun openInput()=ByteArrayInputStream(output.toByteArray())
        override fun discard() { discarded=true;output.reset() }
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

    @Test(timeout=60000) fun acknowledgedCanonicalSetsTriggerOnlySafeRestCues() {
        NodeTransport().use { node -> runBlocking {
            val engine=SandboxCanonicalWorkoutStoreEngine(node)
            val setup=NativeProgramSetupCodec.setupJson(
                NativeProgramProfile(name="Rest confirmation",allowSupersets=false),
                "rest-create","rest-program",1000,223)
            val program=checkNotNull(engine.prepareProgram(setup))
            val storeDrive=Drive();val owner=CanonicalWorkoutStorePersistence(storeDrive,engine)
            assertTrue(owner.initialize(program.snapshot))
            val plan=checkNotNull(engine.prepareWorkout(program.snapshot,
                NativeWorkoutCodec.startJson("rest-program","rest-workout",2000)))
            val journalDrive=Drive();val journal=WorkoutJournalPersistence(journalDrive)
            var progress=checkNotNull(owner.beginIssuedSession(plan,journal))
            assertNull(NativeRestCuePolicy.latest(progress,0))
            val eligible=plan.exercises.firstOrNull {
                !it.superset && it.restSeconds in 1..3600 && it.sets.size>=2
            } ?: error("Generated nonpaired workout must have multiple issued sets")
            val first=eligible.sets.first().slot
            val observation=JournalSetValues("0",plan.session.unit,8,2)
            progress=NativeWorkoutProgress(plan,checkNotNull(journal.recordSet(plan.session,
                progress.journal.revision,JournalSetIntent("rest-logged",first,
                    JournalSetDisposition.COMPLETED,observation))))
            val expected=eligible.sets.drop(1).firstOrNull()
                ?.extensionRestSeconds ?: eligible.restSeconds
            assertEquals(NativeRestCue("rest-logged",expected,eligible.index),
                NativeRestCuePolicy.latest(progress,eligible.index))
            assertNull(NativeRestCuePolicy.latest(progress,eligible.index+100))
            val writes=journalDrive.writes
            val storeWrites=storeDrive.writes
            // The cue is pure. Recovering durable journal bytes must not
            // append a set or synthesize another training receipt.
            val recovered=checkNotNull(WorkoutJournalPersistence(journalDrive).recover())
            assertEquals(progress.journal.revision,recovered.revision)
            assertEquals(NativeRestCue("rest-logged",expected,eligible.index),
                NativeRestCuePolicy.latest(NativeWorkoutProgress(plan,recovered),eligible.index))
            assertEquals(writes,journalDrive.writes)
            assertEquals(storeWrites,storeDrive.writes)
            val second=eligible.sets[1].slot
            progress=NativeWorkoutProgress(plan,checkNotNull(journal.recordSet(plan.session,
                progress.journal.revision,JournalSetIntent("rest-skipped",second,
                    JournalSetDisposition.SKIPPED,null))))
            assertNull(NativeRestCuePolicy.latest(progress,eligible.index))
            progress=NativeWorkoutProgress(plan,checkNotNull(journal.recordSet(plan.session,
                progress.journal.revision,JournalSetIntent("rest-correction",first,
                    JournalSetDisposition.COMPLETED,observation.copy(reps=9),
                    replacesOperationId="rest-logged"))))
            assertNull("Correcting the same set cannot restart a rest timer",
                NativeRestCuePolicy.latest(progress,eligible.index))
            assertEquals(storeWrites,storeDrive.writes)
        } }
    }

    @Test(timeout=60000) fun packagedEnginePreviewsBackupConflictRiskWithoutWritingPersonalStore() {
        NodeTransport().use { node -> runBlocking {
            val engine=SandboxCanonicalWorkoutStoreEngine(node)
            val drive=Drive()
            val store=CanonicalWorkoutStorePersistence(drive,engine)
            assertTrue(store.initialize(source))
            val sourceBytes=checkNotNull(store.readOwnedCurrentStoreBytes())
            val current=String(sourceBytes,Charsets.UTF_8)
            sourceBytes.fill(0)
            val selected=JSONObject(current).put("savedAt",JSONObject(current).getLong("savedAt")+1).toString()
            val priorWrites=drive.writes
            val preview=checkNotNull(engine.previewBackupMerge(current,selected))
            assertEquals(sha(current),preview.currentSha256)
            assertEquals(sha(selected),preview.incomingSha256)
            assertEquals(64,preview.hypotheticalMergedSha256.length)
            assertTrue(preview.hypotheticalMergedBytes>0)
            assertEquals(JSONObject(current).getJSONArray("history").length(),preview.current.history)
            assertEquals(JSONObject(selected).getJSONArray("history").length(),preview.incoming.history)
            assertTrue(preview.hypothetical.history>=preview.current.history)
            val maybePrepared=engine.prepareBackupMerge(
                current,selected,preview.hypotheticalMergedSha256)
            if (preview.flags.isEmpty()) {
                val prepared=checkNotNull(maybePrepared)
                assertEquals(preview.hypotheticalMergedSha256,prepared.candidateSha256)
                assertEquals(preview.hypotheticalMergedBytes,prepared.candidateBytes)
                assertTrue(engine.validateInitialSnapshot(prepared.candidateSnapshot))
            } else {
                assertNull("Existing device data must not be changed without consent",maybePrepared)
            }
            assertNull(engine.prepareBackupMerge(current,selected,"f".repeat(64)))
            val cleanCurrent="""{"v":13,"savedAt":1,"saved":[],"history":[],"cycles":[],"custom":[],"gyms":[],"perf":{},"tombs":{}}"""
            val cleanIncoming="""{"v":13,"savedAt":2,"saved":[],"history":[{"id":"new-synthetic","date":2,"updatedAt":2}],"cycles":[],"custom":[],"gyms":[],"perf":{},"tombs":{}}"""
            val cleanPreview=checkNotNull(engine.previewBackupMerge(cleanCurrent,cleanIncoming))
            assertTrue("Additive source must have no silent change risk",cleanPreview.flags.isEmpty())
            val additive=checkNotNull(engine.prepareBackupMerge(
                cleanCurrent,cleanIncoming,cleanPreview.hypotheticalMergedSha256))
            assertEquals(cleanPreview.hypotheticalMergedSha256,additive.candidateSha256)
            assertEquals(1,JSONObject(additive.candidateSnapshot).getJSONArray("history").length())
            assertTrue(engine.validateInitialSnapshot(additive.candidateSnapshot))
            assertEquals(priorWrites,drive.writes)
            assertArrayEquals(current.toByteArray(Charsets.UTF_8),
                checkNotNull(store.readOwnedCurrentStoreBytes()))
            assertNull(engine.previewBackupMerge(current,"invalid-json"))
            assertEquals(priorWrites,drive.writes)
        } }
    }

    @Test(timeout=60000) fun savedProgramOrganizationSurvivesRestartWorkoutCompletionAndLostSaveAcknowledgement() {
        NodeTransport().use { node -> runBlocking {
            val engine=SandboxCanonicalWorkoutStoreEngine(node)
            val proposal=checkNotNull(engine.prepareProgram(NativeProgramSetupCodec.setupJson(
                NativeProgramProfile(name="Editable plan",unit="kg"),"create-edit","program-edit",1000,223)))
            val drive=Drive();var store=CanonicalWorkoutStorePersistence(drive,engine)
            assertTrue(store.initialize(proposal.snapshot))
            fun verified(summary: ReadOnlyProgramSummary)=ReadOnlyProgramPresentationPolicy.inspect(summary,
                CurrentProgramEvidence(summary.reference,CurrentProgramDeletionEvidence.CheckedAbsent)) as ReadOnlyProgramPresentation.Structure
            val original=NativeProgramEditDraft.from(verified(proposal.program))
            val draft=checkNotNull(checkNotNull(checkNotNull(original.renameProgram("Organized plan"))
                .renameDay(original.days[0].id,"Reordered training"))
                .moveExercise(original.days[0].id,0,1))
            val request=NativeProgramEditCodec.editJson(draft,"edit-once",2000)
            val initialWrites=drive.writes
            var notifications=0
            store.subscribeCommittedChanges { notifications++ }
            val saved=checkNotNull(store.saveProgramEdits(request))
            assertEquals("Organized plan",saved.name)
            assertEquals(draft.days,saved.days)
            assertEquals(initialWrites+1,drive.writes);assertEquals(1,notifications)
            assertNotNull(store.saveProgramEdits(request));assertEquals(initialWrites+1,drive.writes)
            store=CanonicalWorkoutStorePersistence(drive,engine)
            var snapshot=String(checkNotNull(store.readOwnedCurrentStoreBytes()),Charsets.UTF_8)
            val reopened=checkNotNull(engine.inspectProgram(snapshot))
            assertEquals(saved.reference,reopened.program.reference)
            assertEquals("Editable plan",reopened.profile.name) // creation provenance stays factual
            val journal=WorkoutJournalPersistence(Drive())
            val plan=checkNotNull(engine.prepareWorkout(snapshot,
                NativeWorkoutCodec.startJson("program-edit","after-edit-workout",3000)))
            assertEquals(draft.days[0].exerciseIds.first(),plan.exercises.first().id)
            assertEquals("Reordered training",plan.dayLabel)
            var progress=checkNotNull(store.beginIssuedSession(plan,journal))
            val during=NativeProgramEditCodec.editJson(checkNotNull(
                NativeProgramEditDraft.from(verified(reopened.program)).renameProgram("Blocked edit")),"edit-during",4000)
            assertNull(store.saveProgramEdits(during))
            for((i,slot) in plan.slots.withIndex())progress=NativeWorkoutProgress(plan,checkNotNull(journal.recordSet(
                plan.session,progress.journal.revision,JournalSetIntent("edit-set-$i",slot,
                    if(i==0)JournalSetDisposition.COMPLETED else JournalSetDisposition.SKIPPED,
                    if(i==0)JournalSetValues("40","kg",6,2) else null))))
            val finished=checkNotNull(journal.finish(plan.session,progress.journal.revision,
                JournalFinishIntent("edit-finish",63000,60000)))
            assertTrue(checkNotNull(store.complete(plan.session,finished.revision,journal)).recoveryCleared)
            snapshot=String(checkNotNull(store.readOwnedCurrentStoreBytes()),Charsets.UTF_8)
            val historical=JSONObject(snapshot).getJSONArray("history").toString()
            val current=checkNotNull(engine.inspectProgram(snapshot))
            val next=checkNotNull(NativeProgramEditDraft.from(verified(current.program)).renameProgram("After completion"))
            val nextRequest=NativeProgramEditCodec.editJson(next,"edit-after-workout",64000)
            val beforeWrite=drive.writes
            drive.failBeforeWrite=true
            assertTrue(runCatching { store.saveProgramEdits(nextRequest) }.isFailure)
            assertEquals(beforeWrite,drive.writes)
            assertEquals(snapshot,String(checkNotNull(store.readOwnedCurrentStoreBytes()),Charsets.UTF_8))
            drive.failBeforeWrite=false;drive.failAfterWrite=true
            assertNotNull(store.saveProgramEdits(nextRequest)) // exact committed bytes reconcile the lost ack
            drive.failAfterWrite=false
            assertEquals(beforeWrite+1,drive.writes)
            store=CanonicalWorkoutStorePersistence(drive,engine)
            assertNotNull(store.saveProgramEdits(nextRequest));assertEquals(beforeWrite+1,drive.writes)
            snapshot=String(checkNotNull(store.readOwnedCurrentStoreBytes()),Charsets.UTF_8)
            assertEquals(historical,JSONObject(snapshot).getJSONArray("history").toString())
            assertEquals(sha(snapshot),checkNotNull(store.lastCompletionReceipt()).savedSnapshotSha256)
            assertEquals("After completion",checkNotNull(engine.inspectProgram(snapshot)).program.name)
            val readbackDraft=checkNotNull(NativeProgramEditDraft.from(verified(checkNotNull(engine.inspectProgram(snapshot)).program))
                .renameProgram("Recovered readback"))
            val readbackRequest=NativeProgramEditCodec.editJson(readbackDraft,"readback-edit",65000)
            var readbackNotifications=0
            store.subscribeCommittedChanges {readbackNotifications++}
            val beforeReadback=drive.writes
            drive.failReadAfterWrite=true
            assertTrue(runCatching { store.saveProgramEdits(readbackRequest) }.isFailure)
            assertEquals(beforeReadback+1,drive.writes)
            assertEquals(1,readbackNotifications)
            drive.failReadAfterWrite=false
            assertNotNull(store.saveProgramEdits(readbackRequest))
            assertEquals(beforeReadback+1,drive.writes)

            assertNull(store.saveProgramEdits(request)) // an older accepted edit cannot replay over a newer one
        } }
    }

    @Test(timeout=60000) fun removedProgramMovementSurvivesAtomicSaveRestartAndWorkoutIssuance() {
        NodeTransport().use { node -> runBlocking {
            val engine=SandboxCanonicalWorkoutStoreEngine(node)
            val proposal=checkNotNull(engine.prepareProgram(NativeProgramSetupCodec.setupJson(
                NativeProgramProfile(name="Editable removal",allowSupersets=false),
                "create-removal","program-removal",1000,223)))
            val drive=Drive();var owner=CanonicalWorkoutStorePersistence(drive,engine)
            assertTrue(owner.initialize(proposal.snapshot))
            val selected=proposal.program.days.first()
            val initial=JSONObject(proposal.snapshot).getJSONArray("saved").getJSONObject(0)
            val firstDay=initial.getJSONArray("days").getJSONObject(0)
            val primary=firstDay.optInt("primaryIndex",-1)
            val pairMap=initial.optJSONObject("ss")
            val removable=selected.exerciseIds.indices.lastOrNull { index ->
                index!=primary && pairMap?.has("${selected.id}:${index}")!=true &&
                    (index==0 || pairMap?.has("${selected.id}:${index-1}")!=true)
            }
            assertNotNull("An ordinary generated day needs a removable accessory",removable)
            val verified=ReadOnlyProgramPresentationPolicy.inspect(proposal.program,
                CurrentProgramEvidence(proposal.program.reference,CurrentProgramDeletionEvidence.CheckedAbsent))
                as ReadOnlyProgramPresentation.Structure
            val draft=checkNotNull(NativeProgramEditDraft.from(verified).removeExercise(
                selected.id,checkNotNull(removable)))
            val request=NativeProgramEditCodec.editJson(draft,"remove-once",2000)
            assertEquals(2,JSONObject(request).getInt("format"))
            val originalHistory=JSONObject(proposal.snapshot).getJSONArray("history").toString()
            val writes=drive.writes
            val saved=checkNotNull(owner.saveProgramEdits(request))
            assertEquals(selected.exerciseIds.size-1,saved.days.first().exerciseIds.size)
            assertEquals(draft.days,saved.days)
            assertEquals(writes+1,drive.writes)
            var stored=String(checkNotNull(owner.readOwnedCurrentStoreBytes()),Charsets.UTF_8)
            assertTrue(engine.validateInitialSnapshot(stored))
            assertEquals(JSONObject(proposal.snapshot).getJSONArray("history").toString(),
                JSONObject(stored).getJSONArray("history").toString())
            assertEquals(1,JSONObject(stored).getJSONObject("nativeProgramEdit").getInt("format"))
            owner=CanonicalWorkoutStorePersistence(drive,engine)
            assertNotNull(owner.saveProgramEdits(request))
            assertEquals("Replaying the accepted operation must not commit again",writes+1,drive.writes)
            stored=String(checkNotNull(owner.readOwnedCurrentStoreBytes()),Charsets.UTF_8)
            val refreshed=checkNotNull(engine.inspectProgram(stored))
            assertEquals(draft.days,refreshed.program.days)
            val plan=checkNotNull(engine.prepareWorkout(stored,
                NativeWorkoutCodec.startJson("program-removal","post-removal-workout",3000)))
            val journal=WorkoutJournalPersistence(Drive())
            assertNotNull(owner.beginIssuedSession(plan,journal))
            // Beginning a real session may commit its own reservation; the blocked
            // edit must not add another store write.
            val writesAtSessionStart=drive.writes
            val competing=checkNotNull(NativeProgramEditDraft.from(
                ReadOnlyProgramPresentationPolicy.inspect(refreshed.program,
                    CurrentProgramEvidence(refreshed.program.reference,
                        CurrentProgramDeletionEvidence.CheckedAbsent))
                    as ReadOnlyProgramPresentation.Structure).renameProgram("Blocked while active"))
            assertNull(owner.saveProgramEdits(NativeProgramEditCodec.editJson(
                competing,"edit-while-active",4000)))
            assertEquals(writesAtSessionStart,drive.writes)
            assertEquals(originalHistory,JSONObject(String(
                checkNotNull(owner.readOwnedCurrentStoreBytes()),Charsets.UTF_8))
                .getJSONArray("history").toString())
        } }
    }

    @Test(timeout=60000) fun delayedProgramSaveCannotOverwriteANewerProgramRevision() {
        NodeTransport().use { node -> runBlocking {
            val started=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>()
            val delayed=CanonicalWorkoutStoreTransport { wire ->
                val result=node.request(wire)
                val request=JSONObject(wire)
                if(request.getString("method")=="prepareNativeProgramEdit" &&
                    JSONObject(request.getJSONObject("params").getString("editJson")).getString("operationId")=="delayed-edit") {
                    started.complete(Unit);release.await()
                }
                result
            }
            val engine=SandboxCanonicalWorkoutStoreEngine(delayed)
            val proposal=checkNotNull(engine.prepareProgram(NativeProgramSetupCodec.setupJson(
                NativeProgramProfile(),"race-create","race-program",1000,223)))
            val drive=Drive();val store=CanonicalWorkoutStorePersistence(drive,engine)
            assertTrue(store.initialize(proposal.snapshot))
            val verified=ReadOnlyProgramPresentationPolicy.inspect(proposal.program,
                CurrentProgramEvidence(proposal.program.reference,CurrentProgramDeletionEvidence.CheckedAbsent)) as ReadOnlyProgramPresentation.Structure
            val original=NativeProgramEditDraft.from(verified)
            val first=NativeProgramEditCodec.editJson(checkNotNull(original.renameProgram("Late result")),"delayed-edit",2000)
            val second=NativeProgramEditCodec.editJson(checkNotNull(original.renameProgram("Newer save")),"newer-edit",3000)
            val pending=async { store.saveProgramEdits(first) }
            started.await()
            assertEquals("Newer save",checkNotNull(store.saveProgramEdits(second)).name)
            val writes=drive.writes
            release.complete(Unit)
            assertNull(pending.await());assertEquals(writes,drive.writes)
            val snapshot=String(checkNotNull(store.readOwnedCurrentStoreBytes()),Charsets.UTF_8)
            assertEquals("Newer save",checkNotNull(engine.inspectProgram(snapshot)).program.name)
        } }
    }

    @Test(timeout=60000) fun actualSessionNoteSurvivesRestartCorrectionsCompletionAndExportWithoutAffectingSetRevisions() {
        NodeTransport().use { node -> runBlocking {
            val engine=SandboxCanonicalWorkoutStoreEngine(node)
            val proposal=checkNotNull(engine.prepareProgram(NativeProgramSetupCodec.setupJson(
                NativeProgramProfile(name="Durable session notes",unit="kg"),"notes-create","notes-program",1000,223)))
            val drive=Drive();val recovery=Drive()
            var owner=CanonicalWorkoutStorePersistence(drive,engine)
            var journal=WorkoutJournalPersistence(recovery)
            assertTrue(owner.initialize(proposal.snapshot))
            val plan=checkNotNull(engine.prepareWorkout(proposal.snapshot,
                NativeWorkoutCodec.startJson("notes-program","notes-workout",2000)))
            var progress=checkNotNull(owner.beginIssuedSession(plan,journal))
            val note=JournalSessionNote("note-initial","Seat 4\nNo strap today.",2500)
            recovery.failAfterWrite=true
            assertTrue(runCatching {journal.recordNote(plan.session,0,note)}.isFailure)
            recovery.failAfterWrite=false
            owner=CanonicalWorkoutStorePersistence(drive,engine);journal=WorkoutJournalPersistence(recovery)
            progress=checkNotNull(owner.recoverIssuedSession(journal))
            assertEquals(note,progress.journal.note);assertEquals(0L,progress.journal.revision)
            val writes=recovery.writes
            assertNotNull(journal.recordNote(plan.session,0,note));assertEquals(writes,recovery.writes)
            val review=checkNotNull(engine.inspectWorkoutMuscles(proposal.snapshot,plan.issuedJson))
            for((i,slot) in plan.slots.withIndex()) {
                val part=review.rows.single {it.exerciseIndex==slot.exerciseIndex}.primaryPart
                val intent=JournalSetIntent("note-working-$i",slot,
                    if(i==0)JournalSetDisposition.COMPLETED else JournalSetDisposition.SKIPPED,
                    if(i==0)JournalSetValues("40","kg",6,2) else null,
                    adjustment=if(i==1)JournalSetAdjustment("skip",part) else null)
                progress=NativeWorkoutProgress(plan,checkNotNull(journal.recordSet(plan.session,progress.journal.revision,intent)))
                assertEquals(note,progress.journal.note)
            }
            val first=progress.journal.completed.first().intent
            val corrected=JournalSetIntent("note-active-correction",first.slot,JournalSetDisposition.COMPLETED,
                JournalSetValues("45","kg",6,2),first.operationId)
            progress=NativeWorkoutProgress(plan,checkNotNull(journal.recordSet(plan.session,progress.journal.revision,corrected)))
            val finalNote=JournalSessionNote("note-final","Final note: seat 4\nFelt strong. 🏋️",3000,note.operationId)
            progress=NativeWorkoutProgress(plan,checkNotNull(journal.recordNote(plan.session,progress.journal.revision,finalNote)))
            assertEquals(plan.slots.size+1L,progress.journal.revision)
            val finished=checkNotNull(journal.finish(plan.session,progress.journal.revision,
                JournalFinishIntent("notes-finish",62000,60000)))
            assertEquals(finalNote,finished.note)
            assertTrue(checkNotNull(owner.complete(plan.session,finished.revision,journal)).recoveryCleared)
            assertNull(journal.recover())
            val snapshot=String(checkNotNull(owner.readOwnedCurrentStoreBytes()),Charsets.UTF_8)
            val history=checkNotNull(engine.inspectHistory(snapshot,plan.session.workoutId))
            assertEquals(finalNote.text,history.sessionNote)
            assertEquals(plan.slots.size,history.totalSets);assertEquals(1,history.setsDone)
            assertEquals(270L,history.volume)
            val firstObservation=checkNotNull(history.editRows).first {it.retained}
            val correction=NativeHistoryObservationCodec.correctionJson(history,"note-history-correct",63000,
                listOf(NativeHistoryObservationChange(firstObservation.slot,JournalSetDisposition.COMPLETED,
                    JournalSetValues("50","kg",6,2))))
            assertEquals(finalNote.text,checkNotNull(owner.correctHistory(correction)).sessionNote)
            val exported=BackupDocument()
            assertNotNull(NativeBackupExporter(owner,engine).export(exported))
            val savedNote=JSONObject(String(exported.output.toByteArray(),Charsets.UTF_8)).getJSONArray("history")
                .getJSONObject(0).getJSONObject("nativeSessionNote")
            assertEquals(finalNote.text,savedNote.getString("text"))
            val nextSnapshot=String(checkNotNull(owner.readOwnedCurrentStoreBytes()),Charsets.UTF_8)
            val next=checkNotNull(engine.prepareWorkout(nextSnapshot,
                NativeWorkoutCodec.startJson("notes-program","notes-next",64000)))
            assertNull(checkNotNull(owner.beginIssuedSession(next,journal)).journal.note)
        } }
    }

    @Test(timeout=120000) fun pinnedExerciseFactsRecoverPublishOnceAndCarryThroughHistoryBackupAndNextWorkout() {
        NodeTransport().use { node -> runBlocking {
            val engine=SandboxCanonicalWorkoutStoreEngine(node)
            val proposal=checkNotNull(engine.prepareProgram(NativeProgramSetupCodec.setupJson(
                NativeProgramProfile(name="Pinned exercise facts",unit="kg"),"pinned-create","pinned-program",1000,223)))
            val source=JSONObject(proposal.snapshot);val program=source.getJSONArray("saved").getJSONObject(0)
            val days=program.getJSONArray("days");val exerciseId=days.getJSONObject(0).getJSONArray("exercises").getString(0)
            for(i in 0 until days.length()) {
                days.getJSONObject(i).getJSONArray("exercises").put(0,exerciseId).put(1,exerciseId)
            }
            source.put("exNotes",JSONObject().put(exerciseId,"Old cue").put("unrelated","Keep this note"))
                .put("exSetup",JSONObject().put(exerciseId,JSONObject().put("oldMachine","7"))
                    .put("unrelated",JSONObject().put("seat","9")))
            val origin=source.toString();val drive=Drive();val recovery=Drive()
            var owner=CanonicalWorkoutStorePersistence(drive,engine);var journal=WorkoutJournalPersistence(recovery)
            assertTrue(owner.initialize(origin))
            val plan=checkNotNull(engine.prepareWorkout(origin,NativeWorkoutCodec.startJson("pinned-program","pinned-workout",2000)))
            assertTrue(plan.exercises.count {it.id==exerciseId}>=2)
            val row=plan.exercises.first();assertEquals("Old cue",row.pinnedNote?.text)
            assertTrue(checkNotNull(row.pinnedNote).fields.any {it.key=="oldMachine" && it.orphan})
            var progress=checkNotNull(owner.beginIssuedSession(plan,journal))
            val first=JournalExerciseNote("pinned-first",exerciseId,"Pause on chest 🏋️",mapOf("oldMachine" to "8"),2500)
            recovery.failAfterWrite=true
            assertTrue(runCatching {journal.recordExerciseNote(plan,0,0,first)}.isFailure)
            recovery.failAfterWrite=false;owner=CanonicalWorkoutStorePersistence(drive,engine);journal=WorkoutJournalPersistence(recovery)
            progress=checkNotNull(owner.recoverIssuedSession(journal));assertEquals(listOf(first),progress.journal.exerciseNotes)
            assertEquals(plan.issuedJson,progress.plan.issuedJson);assertEquals(0L,progress.journal.revision)
            val recoveryWrites=recovery.writes
            assertNotNull(journal.recordExerciseNote(plan,0,0,first));assertEquals(recoveryWrites,recovery.writes)
            progress=NativeWorkoutProgress(plan,checkNotNull(journal.recordNote(plan.session,0,
                JournalSessionNote("pinned-session-note","Only this session",3000))))
            progress=NativeWorkoutProgress(plan,checkNotNull(journal.recordWarmup(plan,0,0,
                JournalWarmupIntent("pinned-warmup",JournalWarmupSlot(row.index,row.warmupTargets.size,row.id),
                    JournalSetDisposition.COMPLETED,JournalSetValues("20","kg",5,null),3100))))
            for((i,slot) in plan.slots.withIndex())progress=NativeWorkoutProgress(plan,checkNotNull(journal.recordSet(
                plan.session,progress.journal.revision,JournalSetIntent("pinned-working-$i",slot,
                    if(i==0)JournalSetDisposition.COMPLETED else JournalSetDisposition.SKIPPED,
                    if(i==0)JournalSetValues("40","kg",6,2) else null))))
            val final=JournalExerciseNote("pinned-final",exerciseId,"Final cue\nWrists stacked.",emptyMap(),4000,first.operationId)
            progress=NativeWorkoutProgress(plan,checkNotNull(journal.recordExerciseNote(plan,progress.journal.revision,1,final)))
            assertEquals(plan.slots.size.toLong(),progress.journal.revision)
            assertEquals(final.text,NativeExerciseNotePolicy.resolved(plan,progress.journal.exerciseNotes,exerciseId)?.text)
            // The canonical snapshot remains pinned until history and the exercise edits publish together.
            assertEquals(origin,String(checkNotNull(owner.readOwnedCurrentStoreBytes()),Charsets.UTF_8))
            val finished=checkNotNull(journal.finish(plan.session,progress.journal.revision,JournalFinishIntent("pinned-finish",62000,60000)))
            drive.failAfterWrite=true
            assertTrue(runCatching {owner.complete(plan.session,finished.revision,journal)}.isFailure)
            drive.failAfterWrite=false;owner=CanonicalWorkoutStorePersistence(drive,engine);journal=WorkoutJournalPersistence(recovery)
            assertNotNull(owner.recoverIssuedSession(journal))
            assertTrue(checkNotNull(owner.complete(plan.session,finished.revision,journal)).recoveryCleared)
            assertNull(journal.recover())
            val saved=String(checkNotNull(owner.readOwnedCurrentStoreBytes()),Charsets.UTF_8);val savedStore=JSONObject(saved)
            assertEquals(final.text,savedStore.getJSONObject("exNotes").getString(exerciseId))
            assertEquals("Keep this note",savedStore.getJSONObject("exNotes").getString("unrelated"))
            assertEquals(0,savedStore.getJSONObject("exSetup").getJSONObject(exerciseId).length())
            assertEquals("9",savedStore.getJSONObject("exSetup").getJSONObject("unrelated").getString("seat"))
            assertEquals(1,savedStore.getJSONArray("history").length())
            assertEquals(2,savedStore.getJSONArray("history").getJSONObject(0).getJSONObject("nativeExerciseNotes").getJSONArray("edits").length())
            val history=checkNotNull(engine.inspectHistory(saved,plan.session.workoutId))
            assertEquals(1,history.setsDone);assertEquals(240L,history.volume);assertEquals("Only this session",history.sessionNote)
            val working=checkNotNull(history.editRows).first {it.retained}
            assertNotNull(owner.correctHistory(NativeHistoryObservationCodec.correctionJson(history,"pinned-history-correction",63000,
                listOf(NativeHistoryObservationChange(working.slot,JournalSetDisposition.COMPLETED,JournalSetValues("45","kg",6,2))))))
            val document=BackupDocument();assertNotNull(NativeBackupExporter(owner,engine).export(document))
            val exported=JSONObject(String(document.output.toByteArray(),Charsets.UTF_8))
            assertEquals(final.text,exported.getJSONObject("exNotes").getString(exerciseId))
            assertEquals(2,exported.getJSONArray("history").getJSONObject(0).getJSONObject("nativeExerciseNotes").getJSONArray("edits").length())
            val nextSnapshot=String(checkNotNull(owner.readOwnedCurrentStoreBytes()),Charsets.UTF_8)
            val next=checkNotNull(engine.prepareWorkout(nextSnapshot,NativeWorkoutCodec.startJson("pinned-program","pinned-next",64000)))
            val nextRow=next.exercises.first {it.id==exerciseId}
            assertEquals(final.text,nextRow.pinnedNote?.text);assertTrue(checkNotNull(nextRow.pinnedNote).setup.isEmpty())
            assertFalse(checkNotNull(nextRow.pinnedNote).fields.any {it.key=="oldMachine"})
            assertTrue(checkNotNull(owner.beginIssuedSession(next,journal)).journal.exerciseNotes.isEmpty())
        } }
    }

    @Test(timeout=120000) fun unauthorizedPinnedMapChangesOrMissingAuditNeverRetireRecovery() {
        NodeTransport().use { node -> runBlocking {
            val real=SandboxCanonicalWorkoutStoreEngine(node)
            for(mode in listOf("unrelated-note","unrelated-setup","changed-value","missing-audit")) {
                val forged=object: PinnedCanonicalWorkoutStoreEngine by real,NativeWorkoutEngine by real {
                    override suspend fun prepareIssuedCompletion(origin: String,current: String,journal: String,issued: String): String? {
                        val response=JSONObject(checkNotNull(real.prepareIssuedCompletion(origin,current,journal,issued)))
                        val entry=response.getJSONObject("historyEntry");val candidate=JSONObject(response.getString("candidateSnapshot"))
                        when(mode) {
                            "unrelated-note"->candidate.getJSONObject("exNotes").put("unrelated","Forged")
                            "unrelated-setup"->candidate.getJSONObject("exSetup").put("unrelated",JSONObject().put("seat","99"))
                            "changed-value"->candidate.getJSONObject("exNotes").put(entry.getJSONObject("nativeExerciseNotes")
                                .getJSONArray("edits").getJSONObject(0).getString("exerciseId"),"Forged")
                            else->entry.remove("nativeExerciseNotes")
                        }
                        candidate.getJSONArray("history").put(0,entry)
                        val text=candidate.toString();response.put("candidateSnapshot",text).put("candidateSnapshotSha256",sha(text))
                        return response.toString()
                    }
                }
                val proposal=checkNotNull(real.prepareProgram(NativeProgramSetupCodec.setupJson(
                    NativeProgramProfile(),"bad-pinned-create","bad-pinned-program",1000,223)))
                val drive=Drive();val recovery=Drive();var owner=CanonicalWorkoutStorePersistence(drive,forged)
                val journal=WorkoutJournalPersistence(recovery);assertTrue(owner.initialize(proposal.snapshot))
                val plan=checkNotNull(real.prepareWorkout(proposal.snapshot,NativeWorkoutCodec.startJson("bad-pinned-program","bad-pinned-workout",2000)))
                owner.beginIssuedSession(plan,journal)
                var state=checkNotNull(journal.recordExerciseNote(plan,0,0,
                    JournalExerciseNote("bad-pinned-edit",plan.exercises.first().id,"Only this exercise",emptyMap(),2500)))
                for((i,slot) in plan.slots.withIndex())state=checkNotNull(journal.recordSet(plan.session,state.revision,
                    JournalSetIntent("bad-pinned-work-$i",slot,JournalSetDisposition.SKIPPED,null)))
                state=checkNotNull(journal.finish(plan.session,state.revision,JournalFinishIntent("bad-pinned-finish",62000,60000)))
                val writes=drive.writes
                assertTrue(mode,runCatching {owner.complete(plan.session,state.revision,journal)}.isFailure)
                assertEquals(writes,drive.writes);assertTrue(checkNotNull(journal.recover()).isFinished)
                owner=CanonicalWorkoutStorePersistence(drive,real)
                assertTrue(checkNotNull(owner.complete(plan.session,state.revision,journal)).recoveryCleared)
            }
        } }
    }

    @Test(timeout=120000) fun actualWarmupsKeepTargetsAndFactsThroughRecoveryHistoryCorrectionAndExportWithoutWorkingCredit() {
        NodeTransport().use { node -> runBlocking {
            val engine=SandboxCanonicalWorkoutStoreEngine(node)
            val proposal=checkNotNull(engine.prepareProgram(NativeProgramSetupCodec.setupJson(
                NativeProgramProfile(name="Integrated warm-ups",unit="kg"),"warm-create","warm-program",1000,223)))
            val drive=Drive();val recovery=Drive()
            var owner=CanonicalWorkoutStorePersistence(drive,engine);var journal=WorkoutJournalPersistence(recovery)
            assertTrue(owner.initialize(proposal.snapshot))
            // Build actual canonical lifting history rather than inventing a warm-up prescription.
            for(rotation in 0..2) {
                val source=String(checkNotNull(owner.readOwnedCurrentStoreBytes()),Charsets.UTF_8)
                val plan=checkNotNull(engine.prepareWorkout(source,NativeWorkoutCodec.startJson("warm-program","warm-seed-$rotation",2000+rotation*70000L)))
                var progress=checkNotNull(owner.beginIssuedSession(plan,journal))
                assertTrue(progress.journal.tracksWarmups)
                for((i,slot) in plan.slots.withIndex())progress=NativeWorkoutProgress(plan,checkNotNull(journal.recordSet(plan.session,
                    progress.journal.revision,JournalSetIntent("seed-work-$i",slot,JournalSetDisposition.COMPLETED,JournalSetValues("100","kg",8,2)))))
                val finish=checkNotNull(journal.finish(plan.session,progress.journal.revision,
                    JournalFinishIntent("seed-finish",plan.session.startedAt+60000,60000)))
                assertTrue(checkNotNull(owner.complete(plan.session,finish.revision,journal)).recoveryCleared)
            }
            val source=String(checkNotNull(owner.readOwnedCurrentStoreBytes()),Charsets.UTF_8)
            val plan=checkNotNull(engine.prepareWorkout(source,NativeWorkoutCodec.startJson("warm-program","warm-workout",300000)))
            val row=plan.exercises.first();assertTrue(row.warmupTargets.size>=2)
            var progress=checkNotNull(owner.beginIssuedSession(plan,journal))
            val first=JournalWarmupIntent("warm-first",row.warmupTargets[0].slot,JournalSetDisposition.COMPLETED,JournalSetValues("40","kg",8,null),301000)
            recovery.failAfterWrite=true
            assertTrue(runCatching {journal.recordWarmup(plan,0,0,first)}.isFailure)
            recovery.failAfterWrite=false
            owner=CanonicalWorkoutStorePersistence(drive,engine);journal=WorkoutJournalPersistence(recovery)
            progress=checkNotNull(owner.recoverIssuedSession(journal))
            assertEquals(plan.issuedJson,progress.plan.issuedJson);assertEquals(listOf(first),progress.journal.warmupEvents)
            val writes=recovery.writes
            assertNotNull(journal.recordWarmup(plan,0,0,first));assertEquals(writes,recovery.writes)
            var state=checkNotNull(journal.recordWarmup(plan,0,1,JournalWarmupIntent("warm-skip",row.warmupTargets[1].slot,
                JournalSetDisposition.SKIPPED,null,302000)))
            val added=JournalWarmupSlot(row.index,row.warmupTargets.size,row.id)
            state=checkNotNull(journal.recordWarmup(plan,0,state.warmupEvents.size,JournalWarmupIntent("warm-added",added,
                JournalSetDisposition.COMPLETED,JournalSetValues("0","kg",10,null),303000)))
            state=checkNotNull(journal.recordWarmup(plan,0,state.warmupEvents.size,JournalWarmupIntent("warm-attempt",first.slot,
                JournalSetDisposition.COMPLETED,JournalSetValues("80","kg",0,null),304000,first.operationId)))
            state=checkNotNull(journal.recordNote(plan.session,0,JournalSessionNote("warm-note","Keep this session note",305000)))
            assertEquals(0L,state.revision);assertEquals(plan.slots.size,state.unresolvedCount)
            for((i,slot) in plan.slots.withIndex())state=checkNotNull(journal.recordSet(plan.session,state.revision,
                JournalSetIntent("warm-working-$i",slot,if(i==1)JournalSetDisposition.SKIPPED else JournalSetDisposition.COMPLETED,
                    if(i==1)null else JournalSetValues("100","kg",8,2))))
            val finish=checkNotNull(journal.finish(plan.session,state.revision,JournalFinishIntent("warm-finish",360000,60000)))
            assertTrue(checkNotNull(owner.complete(plan.session,finish.revision,journal)).recoveryCleared)
            val completed=String(checkNotNull(owner.readOwnedCurrentStoreBytes()),Charsets.UTF_8)
            var history=checkNotNull(engine.inspectHistory(completed,plan.session.workoutId))
            assertEquals("Keep this session note",history.sessionNote)
            assertEquals(plan.slots.size,history.totalSets);assertEquals(plan.slots.size-1,history.setsDone)
            assertEquals((plan.slots.size-1)*800L,history.volume)
            assertEquals(0,history.warmups.single {it.slot==first.slot}.values?.reps)
            assertEquals("0",history.warmups.single {it.slot==added}.values?.load)
            assertEquals(JournalSetDisposition.SKIPPED,history.warmups.single {it.slot==row.warmupTargets[1].slot}.disposition)
            assertTrue(history.warmups.any {it.disposition==null})
            val recordedFacts=JSONObject(completed).getJSONArray("history").getJSONObject(0).getJSONObject("nativeWarmups")
            val rawIssued=JSONObject(plan.issuedJson).getJSONArray("rows").getJSONObject(0).getJSONArray("sets")
            val originals=org.json.JSONArray().apply {for(i in 0 until rawIssued.length())if(rawIssued.getJSONObject(i).getBoolean("warm"))put(rawIssued.get(i))}
            assertEquals(originals.toString(),recordedFacts.getJSONArray("exercises").getJSONObject(0).getJSONArray("targets").toString())
            val request=NativeWarmupHistoryCodec.correctionJson(history,"warm-history-edit",370000,
                listOf(NativeWarmupChange(first.slot,JournalSetDisposition.COMPLETED,JournalSetValues("500","kg",12,1))))
            val beforeHistoryWrites=drive.writes
            drive.failAfterWrite=true
            assertTrue(runCatching {owner.correctHistory(request)}.isFailure)
            drive.failAfterWrite=false;owner=CanonicalWorkoutStorePersistence(drive,engine)
            history=checkNotNull(owner.correctHistory(request))
            assertEquals(beforeHistoryWrites+1,drive.writes)
            assertEquals("500",history.warmups.single {it.slot==first.slot}.values?.load)
            assertEquals("Keep this session note",history.sessionNote)
            val corrected=String(checkNotNull(owner.readOwnedCurrentStoreBytes()),Charsets.UTF_8)
            assertEquals(JSONObject(completed).getJSONObject("perf").toString(),JSONObject(corrected).getJSONObject("perf").toString())
            assertEquals(sha(corrected),checkNotNull(owner.lastCompletionReceipt()).savedSnapshotSha256)
            val nextStart=NativeWorkoutCodec.startJson("warm-program","warm-next",390000)
            assertEquals(JSONObject(checkNotNull(engine.prepareWorkout(completed,nextStart)).issuedJson).getJSONArray("rows").toString(),
                JSONObject(checkNotNull(engine.prepareWorkout(corrected,nextStart)).issuedJson).getJSONArray("rows").toString())
            val observation=checkNotNull(history.editRows).first {it.retained}
            val workEdit=NativeHistoryObservationCodec.correctionJson(history,"work-after-warmup",380000,
                listOf(NativeHistoryObservationChange(observation.slot,JournalSetDisposition.COMPLETED,JournalSetValues("90","kg",8,2))))
            history=checkNotNull(owner.correctHistory(workEdit))
            assertEquals("500",history.warmups.single {it.slot==first.slot}.values?.load)
            val exported=BackupDocument();assertNotNull(NativeBackupExporter(owner,engine).export(exported))
            val exportedHistory=JSONObject(String(exported.output.toByteArray(),Charsets.UTF_8)).getJSONArray("history").getJSONObject(0)
            assertEquals(recordedFacts.getJSONArray("exercises").toString(),exportedHistory.getJSONObject("nativeWarmups").getJSONArray("exercises").toString())
            val current=String(checkNotNull(owner.readOwnedCurrentStoreBytes()),Charsets.UTF_8)
            val next=checkNotNull(engine.prepareWorkout(current,nextStart))
            val opened=checkNotNull(owner.beginIssuedSession(next,journal))
            assertTrue(opened.journal.warmupEvents.isEmpty());assertNull(opened.journal.note)
        } }
    }

    @Test(timeout=60000) fun aWarmupTargetChangedByTheBackendCannotRetireRecoveryOrInventAWorkingSet() {
        NodeTransport().use { node -> runBlocking {
            val real=SandboxCanonicalWorkoutStoreEngine(node)
            val forged=object: PinnedCanonicalWorkoutStoreEngine by real,NativeWorkoutEngine by real {
                override suspend fun prepareIssuedCompletion(origin: String,current: String,journal: String,issued: String): String? {
                    val raw=JSONObject(checkNotNull(real.prepareIssuedCompletion(origin,current,journal,issued)))
                    val entry=raw.getJSONObject("historyEntry")
                    entry.getJSONObject("nativeWarmups").getJSONArray("exercises").getJSONObject(0).getJSONArray("targets")
                        .put(JSONObject().put("warm",true).put("weight","999").put("reps","99"))
                    val candidate=JSONObject(raw.getString("candidateSnapshot"));candidate.getJSONArray("history").put(0,entry)
                    val text=candidate.toString();raw.put("candidateSnapshot",text).put("candidateSnapshotSha256",sha(text))
                    return raw.toString()
                }
            }
            val proposal=checkNotNull(real.prepareProgram(NativeProgramSetupCodec.setupJson(
                NativeProgramProfile(),"bad-warm-create","bad-warm-program",1000,223)))
            val drive=Drive();val recovery=Drive();var owner=CanonicalWorkoutStorePersistence(drive,forged)
            val journal=WorkoutJournalPersistence(recovery)
            assertTrue(owner.initialize(proposal.snapshot))
            val plan=checkNotNull(real.prepareWorkout(proposal.snapshot,NativeWorkoutCodec.startJson("bad-warm-program","bad-warm-workout",2000)))
            var progress=checkNotNull(owner.beginIssuedSession(plan,journal))
            val ex=plan.exercises.first()
            var state=checkNotNull(journal.recordWarmup(plan,0,0,JournalWarmupIntent("only-warmup",
                JournalWarmupSlot(ex.index,ex.warmupTargets.size,ex.id),JournalSetDisposition.COMPLETED,JournalSetValues("20",plan.session.unit,8,null),2100)))
            for((i,slot) in plan.slots.withIndex())state=checkNotNull(journal.recordSet(plan.session,state.revision,
                JournalSetIntent("skipped-work-$i",slot,JournalSetDisposition.SKIPPED,null)))
            val finish=checkNotNull(journal.finish(plan.session,state.revision,JournalFinishIntent("bad-warm-finish",62000,60000)))
            val writes=drive.writes
            assertTrue(runCatching {owner.complete(plan.session,finish.revision,journal)}.isFailure)
            assertEquals(writes,drive.writes);assertTrue(checkNotNull(journal.recover()).isFinished)
            owner=CanonicalWorkoutStorePersistence(drive,real)
            assertTrue(checkNotNull(owner.complete(plan.session,finish.revision,journal)).recoveryCleared)
            val snapshot=String(checkNotNull(owner.readOwnedCurrentStoreBytes()),Charsets.UTF_8)
            val history=checkNotNull(real.inspectHistory(snapshot,plan.session.workoutId))
            assertEquals(0,history.setsDone);assertEquals(0L,history.volume);assertTrue(history.exercises.isEmpty())
            assertEquals("20",history.warmups.single {it.disposition==JournalSetDisposition.COMPLETED}.values?.load)
        } }
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
                JournalSetIntent("reduce-first",initial.first(),JournalSetDisposition.SKIPPED,null,
                    adjustment=JournalSetAdjustment("reduce",part))) }.isFailure)
            recovery.failAfterWrite=false
            journal=WorkoutJournalPersistence(recovery)
            owner=CanonicalWorkoutStorePersistence(drive,engine)
            progress=checkNotNull(owner.recoverIssuedSession(journal))
            assertEquals(plan.issuedJson,progress.plan.issuedJson)
            assertEquals(initial.drop(1),NativeSessionSkipPolicy.reduceAffected(progress.journal,review,part))
            for((i,slot) in NativeSessionSkipPolicy.reduceAffected(progress.journal,review,part).withIndex()) {
                progress=NativeWorkoutProgress(plan,checkNotNull(journal.recordSet(plan.session,progress.journal.revision,
                    JournalSetIntent("reduce-other-$i",slot,JournalSetDisposition.SKIPPED,null,
                        adjustment=JournalSetAdjustment("reduce",part)))))
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
            val adjusted=history.getJSONObject("nativeSetAdjustments")
            assertEquals(1,adjusted.getInt("format"))
            val annotated=adjusted.getJSONArray("events")
            assertEquals(initial.size,annotated.length())
            for(index in 0 until annotated.length()) {
                val entry=annotated.getJSONObject(index)
                assertEquals("reduce",entry.getString("action"))
                assertEquals(part,entry.getString("part"))
            }
            val audit=history.getJSONObject("nativeSetRevisions")
            assertEquals(1,audit.getInt("format"))
            assertEquals(plan.slots.size,audit.getJSONArray("events").length())
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

    @Test(timeout=60000) fun historyBrowserPagesAndCalendarsOlderWorkoutsWithoutMixingCompletionOrCancellation() {
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
            val monthQuery=NativeHistoryCalendarQuery(YearMonth.of(1970,1),ZoneOffset.UTC)
            val month=checkNotNull(reader.calendar(monthQuery))
            assertEquals(29,month.entries.size);assertEquals(1,month.activeDays)
            assertEquals(month.entries,month.workoutsOn(monthQuery.firstUtcDay))
            assertTrue(month.entries.any { !it.detailsAvailable });assertEquals(writes,drive.writes)
            val emptyMonth=checkNotNull(reader.calendar(monthQuery.copy(month=YearMonth.of(1970,2))))
            assertTrue(emptyMonth.entries.isEmpty());assertEquals(29,emptyMonth.unfilteredTotal)
            val filter=NativeHistoryFilter(legacy.getString("programName"),100000,900000)
            val filteredFirst=checkNotNull(reader.page(filter=filter))
            assertEquals(25,filteredFirst.total);assertEquals(29,filteredFirst.unfilteredTotal);assertEquals(filter,filteredFirst.filter)
            val filteredSecond=checkNotNull(reader.page(checkNotNull(filteredFirst.nextOffset),filteredFirst.snapshotSha256,filter))
            val filteredLast=checkNotNull(reader.page(checkNotNull(filteredSecond.nextOffset),filteredFirst.snapshotSha256,filter))
            val filteredRows=filteredFirst.entries+filteredSecond.entries+filteredLast.entries
            assertEquals(25,filteredRows.size);assertEquals(25,filteredRows.map { it.historyId }.toSet().size)
            assertTrue(filteredRows.all { it.historyId.startsWith("legacy-") });assertEquals(1,filteredLast.entries.size)
            val namedMonth=checkNotNull(reader.calendar(monthQuery.copy(search=filter.search)))
            assertEquals(filteredRows,namedMonth.entries)
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
            assertNull(reader.calendar(monthQuery,month.snapshotSha256))
            val correctedMonth=checkNotNull(reader.calendar(monthQuery))
            assertEquals(month.entries.map { it.historyId to it.date },correctedMonth.entries.map { it.historyId to it.date })
            assertEquals(month.activeDays,correctedMonth.activeDays)
            assertTrue(correctedMonth.entries.single { it.historyId==older.historyId }.volume<
                month.entries.single { it.historyId==older.historyId }.volume)
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
            val stale=async { reader.calendar(monthQuery) };reached.await()
            for((i,slot) in next.slots.withIndex())progress=NativeWorkoutProgress(next,checkNotNull(journal.recordSet(
                next.session,progress.journal.revision,JournalSetIntent("skip-next-$i",slot,JournalSetDisposition.SKIPPED,null))))
            val finished=checkNotNull(journal.finish(next.session,progress.journal.revision,JournalFinishIntent("finish-next",6060000,60000)))
            assertNotNull(owner.complete(next.session,finished.revision,journal))
            release.complete(Unit);assertNull(stale.await())
            block=false;assertEquals(30,checkNotNull(reader.page()).total)
            reached=CompletableDeferred();release=CompletableDeferred();block=true
            val beforeCancel=drive.writes
            val cancelled=async { reader.calendar(monthQuery) };reached.await();cancelled.cancelAndJoin();release.complete(Unit)
            assertEquals(beforeCancel,drive.writes)
            assertEquals("browser-next",checkNotNull(owner.lastCompletionReceipt()).session.workoutId)
            block=false
            val reopened=NativeHistoryReadPort(CanonicalWorkoutStorePersistence(drive,engine),engine)
            assertEquals(30,checkNotNull(reopened.page()).total)
            val restoredFilter=checkNotNull(reopened.page(filter=filter))
            assertEquals(25,restoredFilter.total);assertEquals(30,restoredFilter.unfilteredTotal)
            assertEquals(filteredFirst.entries,restoredFilter.entries)
            val restoredMonth=checkNotNull(reopened.calendar(monthQuery))
            assertEquals(30,restoredMonth.entries.size);assertEquals("browser-next",restoredMonth.entries.first().historyId)
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
                assertEquals(6,handoff.getInt("format"))
                assertEquals(0,handoff.getJSONArray("warmups").length())
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

    @Test(timeout=60000) fun repeatedLiftLocationsAreVerifiedBeforePublicationAndSurviveCorrectionRecovery() {
        NodeTransport().use { node -> runBlocking {
            val actual=SandboxCanonicalWorkoutStoreEngine(node)
            val proposal=checkNotNull(actual.prepareProgram(NativeProgramSetupCodec.setupJson(
                NativeProgramProfile(name="Repeated lift",unit="kg"),"create-repeat","program-repeat",1000,223)))
            val source=JSONObject(proposal.snapshot)
            val program=source.getJSONArray("saved").getJSONObject(0)
            val day=program.getJSONArray("days").getJSONObject(0)
            val id=day.getJSONArray("exercises").getString(0)
            day.getJSONArray("exercises").put(1,id)
            val cells=program.getJSONObject("nextWeekPrescriptions")
            cells.put(day.getString("id")+":1",JSONObject(cells.getJSONObject(day.getString("id")+":0").toString()))
            for(slot in 0..1)cells.getJSONObject(day.getString("id")+":$slot").getJSONObject("1")
                .put("sets",12).put("tech","drop set")
            var forgeLocations=true
            val transport=CanonicalWorkoutStoreTransport { wire ->
                val raw=node.request(wire)
                if(forgeLocations && JSONObject(wire).getString("method")=="prepareIssuedWorkoutHistory") {
                    val response=JSONObject(raw);val result=response.getJSONObject("result")
                    val entry=result.getJSONObject("historyEntry")
                    entry.getJSONObject("nativeSetLocations").getJSONArray("sets").getJSONObject(0)
                        .getJSONObject("slot").put("exerciseIndex",47)
                    val candidate=JSONObject(result.getString("candidateSnapshot"))
                    val history=candidate.getJSONArray("history")
                    for(i in 0 until history.length())if(history.getJSONObject(i).getString("id")==entry.getString("id"))
                        history.put(i,entry)
                    result.put("candidateSnapshot",candidate.toString())
                        .put("candidateSnapshotSha256",sha(candidate.toString()))
                    response.toString()
                } else raw
            }
            val engine=SandboxCanonicalWorkoutStoreEngine(transport)
            val drive=Drive();val recovery=Drive()
            var store=CanonicalWorkoutStorePersistence(drive,engine)
            var journal=WorkoutJournalPersistence(recovery)
            val origin=source.toString();assertTrue(store.initialize(origin))
            val plan=checkNotNull(engine.prepareWorkout(origin,NativeWorkoutCodec.startJson("program-repeat","workout-repeat",2000)))
            assertEquals(plan.exercises[0].id,plan.exercises[1].id)
            assertTrue(plan.exercises.take(2).all { it.sets.size==14 })
            var progress=checkNotNull(store.beginIssuedSession(plan,journal))
            for((index,slot) in plan.slots.reversed().withIndex()) {
                val skip=slot.exerciseIndex==0 && slot.setIndex==1
                val zero=slot.exerciseIndex==1 && slot.setIndex==0
                progress=NativeWorkoutProgress(plan,checkNotNull(journal.recordSet(plan.session,progress.journal.revision,
                    JournalSetIntent("repeat-$index",slot,if(skip)JournalSetDisposition.SKIPPED else JournalSetDisposition.COMPLETED,
                        if(skip)null else JournalSetValues(if(zero)"0" else "75","kg",8,null)))))
            }
            val finish=checkNotNull(journal.finish(plan.session,progress.journal.revision,
                JournalFinishIntent("finish-repeat",62000,60000)))
            val writes=drive.writes
            assertTrue(runCatching { store.complete(plan.session,finish.revision,journal) }.isFailure)
            assertEquals(writes,drive.writes);assertNotNull(journal.recover())
            forgeLocations=false
            assertNotNull(store.complete(plan.session,finish.revision,journal))
            val before=String(checkNotNull(store.readOwnedCurrentStoreBytes()),Charsets.UTF_8)
            val view=checkNotNull(engine.inspectHistory(before,"workout-repeat"))
            val lift=view.exercises.single { it.id==id }
            assertEquals(listOf(0,1),lift.sets.map { it.exerciseIndex }.distinct())
            val target=lift.sets.last { it.exerciseIndex==1 && it.kind=="drop" }
            assertTrue(target.setIndex>19)
            val command=NativeHistoryCodec.correctionJson(view,"correct-repeat",70000,
                listOf(NativeHistoryChange(id,target.setIndex,"35",9,null)))
            drive.failAfterWrite=true
            assertTrue(runCatching { store.correctHistory(command) }.isFailure)
            drive.failAfterWrite=false
            store=CanonicalWorkoutStorePersistence(drive,engine);journal=WorkoutJournalPersistence(recovery)
            val afterCrashWrites=drive.writes
            val corrected=checkNotNull(store.correctHistory(command))
            assertEquals(afterCrashWrites,drive.writes)
            val after=String(checkNotNull(store.readOwnedCurrentStoreBytes()),Charsets.UTF_8)
            val oldEntry=JSONObject(before).getJSONArray("history").getJSONObject(0)
            val entry=JSONObject(after).getJSONArray("history").getJSONObject(0)
            assertEquals(oldEntry.getJSONObject("nativeSetLocations").toString(),entry.getJSONObject("nativeSetLocations").toString())
            assertEquals(oldEntry.getInt("volume")-75*8+35*9,entry.getInt("volume"))
            assertEquals(oldEntry.getInt("setsDone"),entry.getInt("setsDone"))
            val updated=corrected.exercises.single { it.id==id }
            assertEquals("35",updated.sets.single { it.setIndex==target.setIndex }.load)
            assertTrue(updated.sets.filter { it.exerciseIndex==0 }.all { it.load=="75" })
            assertEquals(75.0,checkNotNull(updated.record).load,0.0)
            assertNull(journal.recover())
            var returned=false
            for(step in 0..2) {
                val snapshot=String(checkNotNull(store.readOwnedCurrentStoreBytes()),Charsets.UTF_8)
                val next=checkNotNull(engine.prepareWorkout(snapshot,NativeWorkoutCodec.startJson(
                    "program-repeat","repeat-next-$step",80000+step*70000L)))
                if(next.session.dayId==plan.session.dayId) {
                    val first=next.exercises[0].sets;val second=next.exercises[1].sets
                    assertEquals("75 kg × 8",first[0].last);assertNull(first[1].last)
                    assertNull(second[0].last);assertEquals("75 kg × 8",second[1].last)
                    assertTrue(next.exercises.flatMap { it.sets }.filter { it.isExtension }.all { it.last==null })
                    returned=true;break
                }
                var pending=checkNotNull(store.beginIssuedSession(next,journal))
                for((i,slot) in next.slots.withIndex())pending=NativeWorkoutProgress(next,
                    checkNotNull(journal.recordSet(next.session,pending.journal.revision,JournalSetIntent("repeat-next-$step-$i",slot,
                        JournalSetDisposition.COMPLETED,JournalSetValues("40","kg",8,null)))))
                val ended=checkNotNull(journal.finish(next.session,pending.journal.revision,
                    JournalFinishIntent("repeat-next-finish-$step",next.session.startedAt+60000,60000)))
                assertNotNull(store.complete(next.session,ended.revision,journal))
            }
            assertTrue(returned)
        } }
    }

    @Test(timeout=60000) fun savedObservationRestoresVerifyTargetsAndRecoverDiskFullLostAcknowledgementAndRestart() {
        NodeTransport().use { node -> runBlocking {
            val actual=SandboxCanonicalWorkoutStoreEngine(node)
            val proposal=checkNotNull(actual.prepareProgram(NativeProgramSetupCodec.setupJson(
                NativeProgramProfile(name="Saved observation journey",unit="kg"),"create-observations","program-observations",1000,223)))
            val source=JSONObject(proposal.snapshot);val program=source.getJSONArray("saved").getJSONObject(0)
            val day=program.getJSONArray("days").getJSONObject(0)
            program.getJSONObject("nextWeekPrescriptions").getJSONObject(day.getString("id")+":0")
                .getJSONObject("1").put("tech","drop set")
            var forge: String?=null
            val transport=CanonicalWorkoutStoreTransport { wire ->
                val raw=node.request(wire)
                if(forge!=null && JSONObject(wire).getString("method")=="prepareNativeHistoryCorrection") {
                    val response=JSONObject(raw);val result=response.getJSONObject("result")
                    val candidate=JSONObject(result.getString("candidateSnapshot"))
                    val entry=candidate.getJSONArray("history").getJSONObject(0)
                    when(forge) {
                        "targets" -> entry.getJSONObject("nativeHistoryBasis").getJSONArray("rows").getJSONObject(0)
                            .getJSONArray("omitted").getJSONObject(0).getJSONObject("template").getJSONObject("decisionTrace")
                            .getJSONObject("target").put("load",9999)
                        "observation" -> entry.getJSONObject("nativeSetObservations").getJSONArray("events").getJSONObject(2)
                            .getJSONObject("values").put("load","41")
                        "audit" -> entry.getJSONObject("nativeHistoryRevisions").getJSONArray("events").getJSONObject(0)
                            .getJSONObject("from").put("disposition","COMPLETED")
                        "active-audit" -> entry.getJSONObject("nativeSetRevisions").getJSONArray("events").getJSONObject(0)
                            .getJSONObject("values").put("load","39")
                        "program" -> candidate.getJSONArray("saved").getJSONObject(0).put("name","Unrequested program change")
                    }
                    result.put("candidateSnapshot",candidate.toString()).put("candidateSnapshotSha256",sha(candidate.toString()))
                    result.getJSONObject("history").put("snapshotSha256",sha(candidate.toString()))
                    response.toString()
                } else raw
            }
            val engine=SandboxCanonicalWorkoutStoreEngine(transport)
            val drive=Drive();val recovery=Drive()
            var store=CanonicalWorkoutStorePersistence(drive,engine);var journal=WorkoutJournalPersistence(recovery)
            val origin=source.toString();assertTrue(store.initialize(origin))
            val plan=checkNotNull(engine.prepareWorkout(origin,NativeWorkoutCodec.startJson("program-observations","workout-observations",2000)))
            val first=plan.exercises[0]
            val muscles=checkNotNull(engine.inspectWorkoutMuscles(origin,plan.issuedJson))
            val part=muscles.rows.first { it.exerciseIndex==first.index }.primaryPart
            var progress=checkNotNull(store.beginIssuedSession(plan,journal))
            for((i,slot) in plan.slots.withIndex()) {
                val skip=slot.exerciseIndex==first.index && slot.setIndex==1
                val zero=slot.exerciseIndex==first.index && slot.setIndex==0
                progress=NativeWorkoutProgress(plan,checkNotNull(journal.recordSet(plan.session,progress.journal.revision,
                    JournalSetIntent("observe-$i",slot,if(skip)JournalSetDisposition.SKIPPED else JournalSetDisposition.COMPLETED,
                        if(skip)null else JournalSetValues(if(zero)"0" else "50","kg",8,null),
                        adjustment=if(skip)JournalSetAdjustment("reduce",part) else null))))
            }
            val finish=checkNotNull(journal.finish(plan.session,progress.journal.revision,
                JournalFinishIntent("finish-observations",62000,60000)))
            assertNotNull(store.complete(plan.session,finish.revision,journal))
            val before=String(checkNotNull(store.readOwnedCurrentStoreBytes()),Charsets.UTF_8)
            val original=JSONObject(before).getJSONArray("history").getJSONObject(0)
            val view=checkNotNull(engine.inspectHistory(before,"workout-observations"))
            val rows=checkNotNull(view.editRows)
            val skipped=rows.single { it.disposition==JournalSetDisposition.SKIPPED }
            val zero=rows.single { it.slot.exerciseIndex==first.index && it.slot.setIndex==0 }
            assertNull(skipped.values);assertEquals("0",checkNotNull(zero.values).load);assertFalse(zero.retained)
            val request=NativeHistoryObservationCodec.correctionJson(view,"restore-owned",70000,
                listOf(NativeHistoryObservationChange(skipped.slot,JournalSetDisposition.COMPLETED,JournalSetValues("45","kg",9,3))))
            val writes=drive.writes
            for(mode in listOf("targets","observation","audit","active-audit","program")) {
                forge=mode
                assertTrue("Forged $mode must fail before publication",runCatching { store.correctHistory(request) }.isFailure)
                assertEquals(writes,drive.writes)
                assertEquals(before,String(checkNotNull(store.readOwnedCurrentStoreBytes()),Charsets.UTF_8))
            }
            forge=null
            var notifications=0;store.subscribeCommittedChanges { notifications++ }
            drive.failBeforeWrite=true
            assertTrue(runCatching { store.correctHistory(request) }.isFailure)
            assertEquals(before,String(checkNotNull(store.readOwnedCurrentStoreBytes()),Charsets.UTF_8));assertEquals(0,notifications)
            drive.failBeforeWrite=false;drive.failAfterWrite=true
            assertTrue(runCatching { store.correctHistory(request) }.isFailure)
            drive.failAfterWrite=false;assertEquals(1,notifications)
            store=CanonicalWorkoutStorePersistence(drive,engine);journal=WorkoutJournalPersistence(recovery)
            val committedWrites=drive.writes
            var corrected=checkNotNull(store.correctHistory(request));assertEquals(committedWrites,drive.writes)
            var raw=String(checkNotNull(store.readOwnedCurrentStoreBytes()),Charsets.UTF_8)
            var entry=JSONObject(raw).getJSONArray("history").getJSONObject(0)
            assertEquals(original.getInt("setsDone")+1,entry.getInt("setsDone"))
            assertEquals(original.getInt("volume")+45*9,entry.getInt("volume"))
            assertEquals(0,entry.getJSONObject("nativeSetAdjustments").getJSONArray("events").length())
            assertEquals(original.getJSONObject("nativeSetRevisions").toString(),entry.getJSONObject("nativeSetRevisions").toString())
            assertEquals(original.getJSONObject("nativeCompletion").toString(),entry.getJSONObject("nativeCompletion").toString())
            corrected=checkNotNull(store.correctHistory(NativeHistoryObservationCodec.correctionJson(corrected,"correct-zero",70010,
                listOf(NativeHistoryObservationChange(zero.slot,JournalSetDisposition.COMPLETED,JournalSetValues("40","kg",8,null))))))
            assertTrue(checkNotNull(corrected.editRows).single { it.slot==zero.slot }.retained)
            val extension=checkNotNull(corrected.editRows).first { it.slot.exerciseIndex==first.index && it.kind=="drop" }
            corrected=checkNotNull(store.correctHistory(NativeHistoryObservationCodec.correctionJson(corrected,"skip-extension",70020,
                listOf(NativeHistoryObservationChange(extension.slot,JournalSetDisposition.SKIPPED,null)))))
            corrected=checkNotNull(store.correctHistory(NativeHistoryObservationCodec.correctionJson(corrected,"restore-extension",70030,
                listOf(NativeHistoryObservationChange(extension.slot,JournalSetDisposition.COMPLETED,JournalSetValues("999","kg",2,null))))))
            assertEquals(50.0,checkNotNull(corrected.exercises.single { it.id==first.id }.record).load,0.0)
            val all=checkNotNull(corrected.editRows).filter { it.slot.exerciseId==first.id }
                .map { NativeHistoryObservationChange(it.slot,JournalSetDisposition.SKIPPED,null) }
            corrected=checkNotNull(store.correctHistory(NativeHistoryObservationCodec.correctionJson(corrected,"skip-entire-lift",70040,all)))
            assertTrue(corrected.exercises.none { it.id==first.id })
            val third=checkNotNull(corrected.editRows).single { it.slot.exerciseIndex==first.index && it.slot.setIndex==2 }
            corrected=checkNotNull(store.correctHistory(NativeHistoryObservationCodec.correctionJson(corrected,"restore-third",70050,
                listOf(NativeHistoryObservationChange(third.slot,JournalSetDisposition.COMPLETED,JournalSetValues("40","kg",8,null))))))
            assertEquals(3,corrected.exercises.single { it.id==first.id }.sets.single().ordinal)
            assertNull(journal.recover())
            raw=String(checkNotNull(store.readOwnedCurrentStoreBytes()),Charsets.UTF_8)
            entry=JSONObject(raw).getJSONArray("history").getJSONObject(0)
            assertEquals(62000,entry.getInt("date"));assertEquals(1,JSONObject(raw).getJSONArray("history").length())
            assertEquals(original.getJSONObject("nativeSetRevisions").toString(),entry.getJSONObject("nativeSetRevisions").toString())
            val next=checkNotNull(engine.prepareWorkout(raw,NativeWorkoutCodec.startJson("program-observations","next-observation-workout",80000)))
            assertNotEquals(plan.session.dayId,next.session.dayId);assertNotNull(store.beginIssuedSession(next,journal))
            val pendingWrites=drive.writes
            assertNull(store.correctHistory(NativeHistoryObservationCodec.correctionJson(corrected,"blocked-observation-edit",80001,
                listOf(NativeHistoryObservationChange(third.slot,JournalSetDisposition.SKIPPED,null)))))
            assertEquals(pendingWrites,drive.writes);assertEquals(next.session,checkNotNull(journal.recover()).session)
        } }
    }

    @Test(timeout=60000) fun whollySkippedCompletionVerifiesEveryMissingTemplateAgainstPinnedIssuedTargets() {
        NodeTransport().use { node -> runBlocking {
            val actual=SandboxCanonicalWorkoutStoreEngine(node)
            val proposal=checkNotNull(actual.prepareProgram(NativeProgramSetupCodec.setupJson(
                NativeProgramProfile(name="Skipped target proof",unit="kg"),"create-skipped-basis","program-skipped-basis",1000,223)))
            var forge=true
            val transport=CanonicalWorkoutStoreTransport { wire ->
                val raw=node.request(wire)
                if(forge && JSONObject(wire).getString("method")=="prepareIssuedWorkoutHistory") {
                    val response=JSONObject(raw);val result=response.getJSONObject("result");val entry=result.getJSONObject("historyEntry")
                    entry.getJSONObject("nativeHistoryBasis").getJSONArray("rows").getJSONObject(0).getJSONArray("omitted")
                        .getJSONObject(0).getJSONObject("template").getJSONObject("decisionTrace").getJSONObject("target").put("load",9999)
                    val candidate=JSONObject(result.getString("candidateSnapshot"));candidate.getJSONArray("history").put(0,entry)
                    result.put("candidateSnapshot",candidate.toString()).put("candidateSnapshotSha256",sha(candidate.toString()))
                    response.toString()
                } else raw
            }
            val engine=SandboxCanonicalWorkoutStoreEngine(transport)
            val drive=Drive();val recovery=Drive();val store=CanonicalWorkoutStorePersistence(drive,engine)
            val journal=WorkoutJournalPersistence(recovery);assertTrue(store.initialize(proposal.snapshot))
            val plan=checkNotNull(engine.prepareWorkout(proposal.snapshot,
                NativeWorkoutCodec.startJson("program-skipped-basis","workout-skipped-basis",2000)))
            var progress=checkNotNull(store.beginIssuedSession(plan,journal))
            for((i,slot) in plan.slots.withIndex())progress=NativeWorkoutProgress(plan,
                checkNotNull(journal.recordSet(plan.session,progress.journal.revision,
                    JournalSetIntent("skip-basis-$i",slot,JournalSetDisposition.SKIPPED,null))))
            val finish=checkNotNull(journal.finish(plan.session,progress.journal.revision,JournalFinishIntent("finish-skipped-basis",62000,60000)))
            val writes=drive.writes
            assertTrue(runCatching { store.complete(plan.session,finish.revision,journal) }.isFailure)
            assertEquals(writes,drive.writes);assertNotNull(journal.recover())
            forge=false;assertNotNull(store.complete(plan.session,finish.revision,journal))
            val raw=String(checkNotNull(store.readOwnedCurrentStoreBytes()),Charsets.UTF_8)
            val view=checkNotNull(engine.inspectHistory(raw,"workout-skipped-basis"))
            assertTrue(view.exercises.isEmpty());assertEquals(0,view.setsDone)
            assertEquals(plan.slots.size,checkNotNull(view.editRows).size)
            val row=checkNotNull(view.editRows).first();assertNull(row.values)
            val restored=checkNotNull(store.correctHistory(NativeHistoryObservationCodec.correctionJson(view,"restore-skipped-basis",70000,
                listOf(NativeHistoryObservationChange(row.slot,JournalSetDisposition.COMPLETED,JournalSetValues("40","kg",8,null))))))
            assertEquals(1,restored.setsDone);assertEquals(320L,restored.volume)
        } }
    }

    @Test(timeout=60000) fun failedAttemptsSurviveActiveRecoveryCompletionHistoryEditsAndNextSessionReplay() {
        NodeTransport().use { node -> runBlocking {
            val engine=SandboxCanonicalWorkoutStoreEngine(node)
            val proposal=checkNotNull(engine.prepareProgram(NativeProgramSetupCodec.setupJson(
                NativeProgramProfile(name="Failed-attempt integration",goal="strength",experience="beginner",unit="kg"),
                "create-failed","program-failed",1000,223)))
            val source=JSONObject(proposal.snapshot);val program=source.getJSONArray("saved").getJSONObject(0)
            val day=program.getJSONArray("days").getJSONObject(0);val id=day.getJSONArray("exercises").getString(0)
            day.put("primaryIndex",0);program.getJSONObject("config").put("percentScheme","gzclp")
            program.put("trainingMax",JSONObject().put(id,80)).put("trainingMaxUnit","kg")
            program.getJSONObject("nextWeekPrescriptions").getJSONObject(day.getString("id")+":0").getJSONObject("1")
                .put("tech","drop set")
            val origin=source.toString();val plan=checkNotNull(engine.prepareWorkout(origin,
                NativeWorkoutCodec.startJson("program-failed","workout-failed",2000)))
            val first=plan.exercises[0];assertTrue(first.sets.filter { !it.isExtension }.all { it.allowFailedAttempt })
            assertTrue(first.sets.filter { it.isExtension }.all { !it.allowFailedAttempt })
            val drive=Drive();val recovery=Drive()
            var store=CanonicalWorkoutStorePersistence(drive,engine);var journal=WorkoutJournalPersistence(recovery)
            assertTrue(store.initialize(origin));var progress=checkNotNull(store.beginIssuedSession(plan,journal))
            for((i,slot) in plan.slots.withIndex()) {
                val target=plan.exercises.flatMap { it.sets }.single { it.slot==slot }
                val failed=slot.exerciseIndex==first.index && !target.isExtension
                val values=JournalSetValues(if(failed)"80" else "40","kg",if(failed)0 else 6,null)
                assertTrue(NativeWorkoutCodec.acceptsValues(plan,slot,values))
                progress=NativeWorkoutProgress(plan,checkNotNull(journal.recordSet(plan.session,progress.journal.revision,
                    JournalSetIntent("failed-$i",slot,JournalSetDisposition.COMPLETED,values))))
                if(i==0) {
                    store=CanonicalWorkoutStorePersistence(drive,engine);journal=WorkoutJournalPersistence(recovery)
                    progress=checkNotNull(store.recoverIssuedSession(journal))
                    assertEquals(plan.issuedJson,progress.plan.issuedJson)
                    assertTrue(progress.plan.exercises[0].sets[0].allowFailedAttempt)
                    val old=progress.journal.completed.single()
                    progress=NativeWorkoutProgress(plan,checkNotNull(journal.recordSet(plan.session,progress.journal.revision,
                        JournalSetIntent("active-failed-correction",slot,JournalSetDisposition.COMPLETED,
                            JournalSetValues("85","kg",0,0),old.intent.operationId))))
                }
            }
            val ended=checkNotNull(journal.finish(plan.session,progress.journal.revision,
                JournalFinishIntent("finish-failed",62000,60000)))
            drive.failAfterWrite=true
            assertTrue(runCatching { store.complete(plan.session,ended.revision,journal) }.isFailure)
            drive.failAfterWrite=false
            store=CanonicalWorkoutStorePersistence(drive,engine);journal=WorkoutJournalPersistence(recovery)
            assertNotNull(store.complete(plan.session,ended.revision,journal));assertNull(journal.recover())
            val initial=String(checkNotNull(store.readOwnedCurrentStoreBytes()),Charsets.UTF_8)
            val original=JSONObject(initial).getJSONArray("history").getJSONObject(0)
            var view=checkNotNull(engine.inspectHistory(initial,"workout-failed"))
            assertTrue(view.exercises.single { it.id==id }.sets.filter { it.kind=="working" }.all { it.failedAttempt && it.reps==0 })
            assertNull(view.exercises.single { it.id==id }.record)
            val row=checkNotNull(view.editRows).first { it.slot.exerciseIndex==first.index && it.kind=="working" }
            assertTrue(row.allowFailedAttempt);assertEquals(0,checkNotNull(row.values).reps)
            val request=NativeHistoryObservationCodec.correctionJson(view,"correct-failed-zero",70000,
                listOf(NativeHistoryObservationChange(row.slot,JournalSetDisposition.COMPLETED,JournalSetValues("90","kg",0,null))))
            drive.failBeforeWrite=true
            assertTrue(runCatching { store.correctHistory(request) }.isFailure)
            assertEquals(initial,String(checkNotNull(store.readOwnedCurrentStoreBytes()),Charsets.UTF_8))
            drive.failBeforeWrite=false;drive.failAfterWrite=true
            assertTrue(runCatching { store.correctHistory(request) }.isFailure)
            drive.failAfterWrite=false;store=CanonicalWorkoutStorePersistence(drive,engine)
            val writes=drive.writes;view=checkNotNull(store.correctHistory(request));assertEquals(writes,drive.writes)
            assertEquals(original.getLong("volume"),view.volume);assertNull(view.exercises.single { it.id==id }.record)
            var raw=String(checkNotNull(store.readOwnedCurrentStoreBytes()),Charsets.UTF_8)
            var entry=JSONObject(raw).getJSONArray("history").getJSONObject(0)
            assertEquals(0,entry.getJSONObject("perf").getJSONObject(id).getInt("reps"))
            assertEquals(original.getJSONObject("nativeSetRevisions").toString(),entry.getJSONObject("nativeSetRevisions").toString())
            view=checkNotNull(store.correctHistory(NativeHistoryObservationCodec.correctionJson(view,"failed-to-positive",70010,
                listOf(NativeHistoryObservationChange(row.slot,JournalSetDisposition.COMPLETED,JournalSetValues("50","kg",2,null))))))
            assertEquals(original.getLong("volume")+100,view.volume)
            assertEquals(50.0,checkNotNull(view.exercises.single { it.id==id }.record).load,0.0)
            view=checkNotNull(store.correctHistory(NativeHistoryObservationCodec.correctionJson(view,"positive-to-failed",70020,
                listOf(NativeHistoryObservationChange(row.slot,JournalSetDisposition.COMPLETED,JournalSetValues("95","kg",0,null))))))
            assertEquals(original.getLong("volume"),view.volume);assertNull(view.exercises.single { it.id==id }.record)
            view=checkNotNull(store.correctHistory(NativeHistoryObservationCodec.correctionJson(view,"failed-to-skip",70030,
                listOf(NativeHistoryObservationChange(row.slot,JournalSetDisposition.SKIPPED,null)))))
            val skippedCount=view.setsDone
            view=checkNotNull(store.correctHistory(NativeHistoryObservationCodec.correctionJson(view,"skip-to-failed",70040,
                listOf(NativeHistoryObservationChange(row.slot,JournalSetDisposition.COMPLETED,JournalSetValues("80","kg",0,null))))))
            assertEquals(skippedCount+1,view.setsDone)
            raw=String(checkNotNull(store.readOwnedCurrentStoreBytes()),Charsets.UTF_8)
            entry=JSONObject(raw).getJSONArray("history").getJSONObject(0)
            assertEquals(original.getJSONObject("nativeCompletion").toString(),entry.getJSONObject("nativeCompletion").toString())
            assertEquals(1,JSONObject(raw).getJSONArray("history").length())
            val backup=BackupDocument();val writesBeforeExport=drive.writes
            val export=NativeBackupExporter(store,engine).export(backup)
            val exported=backup.output.toByteArray()
            assertArrayEquals(raw.toByteArray(Charsets.UTF_8),exported)
            assertEquals(sha(raw),export.snapshotSha256);assertEquals(exported.size.toLong(),export.byteCount)
            assertFalse(backup.discarded);assertEquals(writesBeforeExport,drive.writes)
            val backupText=String(exported,Charsets.UTF_8)
            assertTrue(engine.validateInitialSnapshot(backupText))
            val backupHistory=checkNotNull(engine.inspectHistory(backupText,"workout-failed"))
            assertEquals(view.entrySha256,backupHistory.entrySha256)
            assertEquals(view.editRows,backupHistory.editRows);assertNull(backupHistory.exercises.single { it.id==id }.record)
            exported.fill(0)
            var returned=false
            for(step in 0..2) {
                val snapshot=String(checkNotNull(store.readOwnedCurrentStoreBytes()),Charsets.UTF_8)
                val next=checkNotNull(engine.prepareWorkout(snapshot,NativeWorkoutCodec.startJson(
                    "program-failed","failed-next-$step",80000+step*70000L)))
                if(next.session.dayId==plan.session.dayId) {
                    val work=next.exercises[0].sets.filter { !it.isExtension }
                    assertTrue(work.first().last?.contains("× 0 · failed attempt")==true)
                    assertTrue(work.all { it.load.toBigDecimal()<=80.toBigDecimal() })
                    returned=true;break
                }
                var pending=checkNotNull(store.beginIssuedSession(next,journal))
                for((i,slot) in next.slots.withIndex())pending=NativeWorkoutProgress(next,
                    checkNotNull(journal.recordSet(next.session,pending.journal.revision,JournalSetIntent("failed-next-$step-$i",slot,
                        JournalSetDisposition.COMPLETED,JournalSetValues("40","kg",6,null)))))
                val finish=checkNotNull(journal.finish(next.session,pending.journal.revision,
                    JournalFinishIntent("failed-next-finish-$step",next.session.startedAt+60000,60000)))
                assertNotNull(store.complete(next.session,finish.revision,journal))
            }
            assertTrue(returned)
        } }
    }

    @Test(timeout=60000) fun ordinaryProgramsCannotAcquireFailedAttemptPermissionFromForgedTransport() {
        NodeTransport().use { node -> runBlocking {
            val actual=SandboxCanonicalWorkoutStoreEngine(node)
            val proposal=checkNotNull(actual.prepareProgram(NativeProgramSetupCodec.setupJson(
                NativeProgramProfile(name="Attempt permission boundary",unit="kg"),"create-no-failure","program-no-failure",1000,223)))
            var forgeIssued=true;var forgeBasis=false
            val transport=CanonicalWorkoutStoreTransport { wire ->
                val raw=node.request(wire);val method=JSONObject(wire).getString("method")
                if(forgeIssued && method=="prepareNativeWorkout")JSONObject(raw).apply {
                    getJSONObject("result").put("allowFailedAttempts",true)
                }.toString() else if(forgeBasis && method=="prepareIssuedWorkoutHistory") {
                    val response=JSONObject(raw);val result=response.getJSONObject("result");val entry=result.getJSONObject("historyEntry")
                    entry.getJSONObject("nativeHistoryBasis").put("allowFailedAttempts",true)
                    val candidate=JSONObject(result.getString("candidateSnapshot"));candidate.getJSONArray("history").put(0,entry)
                    result.put("candidateSnapshot",candidate.toString()).put("candidateSnapshotSha256",sha(candidate.toString()))
                    response.toString()
                } else raw
            }
            val engine=SandboxCanonicalWorkoutStoreEngine(transport)
            val start=NativeWorkoutCodec.startJson("program-no-failure","workout-no-failure",2000)
            assertNull(runCatching { engine.prepareWorkout(proposal.snapshot,start) }.getOrNull())
            forgeIssued=false
            val plan=checkNotNull(engine.prepareWorkout(proposal.snapshot,start))
            assertTrue(plan.exercises.flatMap { it.sets }.none { it.allowFailedAttempt })
            assertFalse(NativeWorkoutCodec.acceptsValues(plan,plan.slots[0],JournalSetValues("80","kg",0,null)))
            val drive=Drive();val recovery=Drive();val store=CanonicalWorkoutStorePersistence(drive,engine)
            val journal=WorkoutJournalPersistence(recovery);assertTrue(store.initialize(proposal.snapshot))
            var progress=checkNotNull(store.beginIssuedSession(plan,journal))
            for((i,slot) in plan.slots.withIndex())progress=NativeWorkoutProgress(plan,
                checkNotNull(journal.recordSet(plan.session,progress.journal.revision,JournalSetIntent("ordinary-$i",slot,
                    JournalSetDisposition.COMPLETED,JournalSetValues("40","kg",6,null)))))
            val finish=checkNotNull(journal.finish(plan.session,progress.journal.revision,JournalFinishIntent("finish-ordinary",62000,60000)))
            forgeBasis=true;val writes=drive.writes
            assertTrue(runCatching { store.complete(plan.session,finish.revision,journal) }.isFailure)
            assertEquals(writes,drive.writes);assertNotNull(journal.recover())
            forgeBasis=false;assertNotNull(store.complete(plan.session,finish.revision,journal))
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
