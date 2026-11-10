package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.math.BigDecimal
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class KotlinNativeJournalFileStoreTest {
    @get:Rule val temporary=TemporaryFolder()
    private val source=KotlinTrainingSourceRevision(
        "athlete-1",2,"program-1",3,"gym-1",4,"catalog-1","coach-1")
    private val targets=listOf(
        KotlinWorkoutTarget("db-press",ComposerRole.PRIMARY,
            ComposerSessionPurpose.HYPERTROPHY,"set-1",DraftSetType.WORKING,0,
            DraftSetLoad.Verified(LoadUnit.LB,LoadBasis.DUMBBELL_PER_HAND,
                BigDecimal("30")),RepBand(8,12),2,60,120),
        KotlinWorkoutTarget("db-press",ComposerRole.PRIMARY,
            ComposerSessionPurpose.HYPERTROPHY,"set-2",DraftSetType.WORKING,1,
            DraftSetLoad.Verified(LoadUnit.LB,LoadBasis.DUMBBELL_PER_HAND,
                BigDecimal("30")),RepBand(8,12),2,60,120)
    )
    private val snapshot=KotlinStagedWorkout(
        source,"grant-1","workout-1",0,0,ComposerSessionPurpose.HYPERTROPHY,35,
        targets)
    private val identity=KotlinStoredWorkoutIdentity(
        "issued-1","workout-1",source,1)
    private fun store()=KotlinNativeJournalFileStore(temporary.root.toPath())
    private fun completed(id:String,revision:Long,set:String,reps:Int,corrects:String?=null)=
        KotlinJournalOperation.RecordSet(id,revision,set,KotlinJournalSetOutcome.COMPLETED,
            KotlinPerformedSet(BigDecimal("30"),LoadUnit.LB,reps,2),corrects)
    private fun ok(result:NativeJournalFileResult):NativeJournalFileResult.Durable {
        assertTrue(result.toString(),result is NativeJournalFileResult.Durable)
        return result as NativeJournalFileResult.Durable
    }

    @Test fun generatedSessionNoteIsOwnerScopedV4DurableClearableAndReplayed() {
        val s=store()
        ok(s.issue("owner-1",snapshot,identity))
        val op=KotlinJournalOperation.SessionNote("note-1",0,"Good pace")
        val saved=ok(s.append("owner-1",snapshot,identity,op)).journal
        assertEquals("Good pace",saved.sessionNote)
        assertEquals(1L,saved.revision)
        assertEquals(2,saved.unresolvedSetCount)
        val reopened=ok(store().open("owner-1",snapshot,identity)).journal
        assertEquals("Good pace",reopened.sessionNote)
        assertEquals(saved,reopened)
        val bytes=Files.list(temporary.root.toPath()).use {stream->
            val path=stream.filter{it.fileName.toString().endsWith(".bin")}
                .findFirst().orElseThrow()
            Files.readAllBytes(path)
        }
        assertEquals(4,java.nio.ByteBuffer.wrap(bytes,8,4).int)
        assertTrue(ok(store().append("owner-1",snapshot,identity,op)).replayed)
        assertEquals(NativeJournalFileResult.Rejected(
            NativeJournalFileRefusal.JOURNAL_REJECTED,KotlinJournalRefusal.STALE_REVISION),
            s.append("owner-1",snapshot,identity,
                KotlinJournalOperation.SessionNote("old-note",0,"not saved")))
        assertEquals(NativeJournalFileResult.Rejected(
            NativeJournalFileRefusal.NOT_ISSUED),
            store().open("other-owner",snapshot,identity))
        val cleared=ok(s.append("owner-1",snapshot,identity,
            KotlinJournalOperation.SessionNote("clear-note",1,""))).journal
        assertEquals("",cleared.sessionNote)
        assertEquals(2,cleared.receipts.size)
        assertEquals(0,cleared.resolvedSetCount)
        val set=ok(s.append("owner-1",snapshot,identity,
            completed("set-after-note",2,"set-1",10))).journal
        assertEquals(1,set.resolvedSetCount)
        assertEquals("",set.sessionNote)
        assertEquals(snapshot,set.snapshot)
    }

    @Test fun coldRestartRestoresReceiptsAndOnlyAckAfterFileCommit() {
        val storage=store()
        assertEquals(NativeJournalFileResult.Rejected(
            NativeJournalFileRefusal.NOT_ISSUED),
            storage.open("owner-1",snapshot,identity))
        val issued=ok(storage.issue("owner-1",snapshot,identity))
        assertFalse(issued.replayed)
        assertEquals(0,issued.journal.revision)
        assertEquals(2,issued.journal.unresolvedSetCount)
        val first=ok(storage.append("owner-1",snapshot,identity,
            completed("event-1",0,"set-1",10)))
        assertFalse(first.replayed)
        assertEquals(1,first.journal.revision)
        assertEquals(1,first.journal.unresolvedSetCount)
        val afterRestart=store()
        val recovered=ok(afterRestart.open("owner-1",snapshot,identity))
        assertEquals(first.journal,recovered.journal)
        assertEquals(KotlinWorkoutRestCue("event-1",120,0),
            KotlinWorkoutRestDomain.inspect(recovered.journal,"set-2").automaticCue)
        val second=ok(afterRestart.append("owner-1",snapshot,identity,
            completed("event-2",1,"set-2",9)))
        assertEquals(0,second.journal.unresolvedSetCount)
        assertFalse(KotlinWorkoutRestDomain.inspect(second.journal,"set-2").hasRemainingWork)
        assertNull(KotlinWorkoutRestDomain.inspect(second.journal,"set-2").automaticCue)
        val completed=ok(storage.append("owner-1",snapshot,identity,
            KotlinJournalOperation.Finish("finish-1",2)))
        assertEquals("finish-1",completed.journal.finishedByEventId)
        val third=store()
        assertEquals(completed.journal,
            ok(third.open("owner-1",snapshot,identity)).journal)
        assertEquals(snapshot.targets,completed.journal.snapshot.targets)
        assertFalse(completed.journal.persisted)
    }

    @Test fun repeatedEventsAreExactlyOnceAndWrongRevisionsCannotOverwrite() {
        val storage=store()
        ok(storage.issue("owner-1",snapshot,identity))
        val event=completed("event-1",0,"set-1",10)
        val first=ok(storage.append("owner-1",snapshot,identity,event))
        val retry=ok(store().append("owner-1",snapshot,identity,event))
        assertTrue(retry.replayed)
        assertEquals(first.journal,retry.journal)
        assertEquals(1,retry.journal.receipts.size)
        assertEquals(NativeJournalFileResult.Rejected(
            NativeJournalFileRefusal.JOURNAL_REJECTED,
            KotlinJournalRefusal.STALE_REVISION),
            storage.append("owner-1",snapshot,identity,
                completed("event-2",0,"set-2",10)))
        assertEquals(NativeJournalFileResult.Rejected(
            NativeJournalFileRefusal.JOURNAL_REJECTED,
            KotlinJournalRefusal.EVENT_ID_COLLISION),
            storage.append("owner-1",snapshot,identity,
                completed("event-1",0,"set-1",11)))
        assertEquals(first.journal,ok(storage.open("owner-1",snapshot,identity)).journal)
    }

    @Test fun concurrentStoreInstancesCannotBothCommitTheSameJournalRevision() {
        val first=store()
        ok(first.issue("owner-1",snapshot,identity))
        val executor=java.util.concurrent.Executors.newFixedThreadPool(2)
        val gate=java.util.concurrent.CountDownLatch(1)
        try {
            val jobs=listOf("event-a","event-b").map { eventId ->
                executor.submit<NativeJournalFileResult> {
                    gate.await()
                    store().append("owner-1",snapshot,identity,
                        completed(eventId,0,"set-1",10))
                }
            }
            gate.countDown()
            val results=jobs.map {it.get(10,java.util.concurrent.TimeUnit.SECONDS)}
            assertEquals(1,results.count {it is NativeJournalFileResult.Durable})
            assertEquals(1,results.count {
                it==NativeJournalFileResult.Rejected(
                    NativeJournalFileRefusal.JOURNAL_REJECTED,
                    KotlinJournalRefusal.STALE_REVISION)
            })
            val reopened=ok(store().open("owner-1",snapshot,identity)).journal
            assertEquals(1,reopened.revision)
            assertEquals(1,reopened.receipts.size)
            assertEquals(1,reopened.effectiveSets.size)
        } finally {
            executor.shutdownNow()
        }
    }

    @Test fun correctionRetainsOriginalReceiptAcrossColdRecovery() {
        val storage=store()
        ok(storage.issue("owner-1",snapshot,identity))
        ok(storage.append("owner-1",snapshot,identity,
            completed("event-1",0,"set-1",10)))
        val revised=ok(storage.append("owner-1",snapshot,identity,
            completed("edit-1",1,"set-1",8,"event-1")))
        assertEquals(2,revised.journal.revision)
        assertEquals(2,revised.journal.receipts.size)
        val reopened=ok(store().open("owner-1",snapshot,identity)).journal
        assertEquals(revised.journal,reopened)
        val observed=(reopened.effectiveSets.getValue("set-1").operation
            as KotlinJournalOperation.RecordSet).performed
        assertEquals(8,observed?.reps)
        assertEquals("event-1",reopened.receipts.first().operation.eventId)
        assertEquals("edit-1",reopened.receipts.last().operation.eventId)
        assertEquals("edit-1",KotlinWorkoutRestDomain.inspect(reopened,"set-2")
            .latestReceiptEventId)
        assertNull(KotlinWorkoutRestDomain.inspect(reopened,"set-2").automaticCue)
    }

    @Test fun immutableIssuedProgramAndAuthenticatedOwnerScopeNeverDrift() {
        val storage=store()
        ok(storage.issue("owner-1",snapshot,identity))
        assertEquals(NativeJournalFileResult.Rejected(NativeJournalFileRefusal.NOT_ISSUED),
            storage.open("owner-2",snapshot,identity))
        assertEquals(NativeJournalFileResult.Rejected(NativeJournalFileRefusal.SOURCE_CONFLICT),
            storage.open("owner-1",snapshot.copy(
                targets=targets.mapIndexed {i,t->
                    if(i==0)t.copy(targetRir=1) else t
                }),identity))
        assertEquals(NativeJournalFileResult.Rejected(NativeJournalFileRefusal.SOURCE_CONFLICT),
            storage.open("owner-1",snapshot,
                identity.copy(storedRevision=2)))
        assertEquals(NativeJournalFileResult.Rejected(NativeJournalFileRefusal.ISSUE_CONFLICT),
            storage.issue("owner-1",snapshot.copy(
                estimatedMinutes=36),identity))
        assertEquals(0,ok(storage.open("owner-1",snapshot,identity)).journal.revision)
    }

    @Test fun corruptedDataIsRejectedNotResetOrConvertedToNewWorkout() {
        val storage=store()
        ok(storage.issue("owner-1",snapshot,identity))
        ok(storage.append("owner-1",snapshot,identity,
            completed("event-1",0,"set-1",11)))
        val files=Files.list(temporary.root.toPath()).use { stream ->
            stream.filter {it.fileName.toString().endsWith(".bin")}
                .toArray().map {it as java.nio.file.Path}
        }
        assertEquals(1,files.size)
        val bytes=Files.readAllBytes(files.first())
        bytes[bytes.size/2]=(bytes[bytes.size/2].toInt() xor 0x01).toByte()
        Files.write(files.first(),bytes)
        assertEquals(NativeJournalFileResult.Rejected(
            NativeJournalFileRefusal.CORRUPT_RECEIPTS),
            store().open("owner-1",snapshot,identity))
        assertEquals(NativeJournalFileResult.Rejected(
            NativeJournalFileRefusal.ISSUE_CONFLICT),
            store().issue("owner-1",snapshot,identity))
        assertEquals(NativeJournalFileResult.Rejected(
            NativeJournalFileRefusal.CORRUPT_RECEIPTS),
            store().append("owner-1",snapshot,identity,
                completed("event-2",1,"set-2",9)))
    }

    @Test fun composerSwapUndoColdReopenAndFinishKeepAllIssuedReceipts() {
        val s=store()
        ok(s.issue("owner-1",snapshot,identity))
        val swappedTargets=targets.map{it.copy(
            exerciseId="chest-machine",
            setKey="swap:sw-1:working:${it.ordinal}",
            load=DraftSetLoad.Verified(LoadUnit.LB,
                LoadBasis.DUMBBELL_PER_HAND,BigDecimal("25"))
        )}
        val change=KotlinJournalOperation.SwapExercise(
            "sw-1",0L,"db-press",swappedTargets)
        val swapped=ok(s.append("owner-1",snapshot,identity,change)).journal
        assertEquals(snapshot,swapped.originalSnapshot)
        assertEquals("chest-machine",
            swapped.snapshot.targets.first().exerciseId)
        assertEquals(swapped,ok(store().open("owner-1",snapshot,identity)).journal)
        val newKeys=swapped.snapshot.targets.map{it.setKey}
        assertEquals("swap:sw-1:working:0",newKeys[0])
        assertEquals("swap:sw-1:working:1",newKeys[1])
        val undo=KotlinJournalOperation.UndoSwap("undo-1",1L,"sw-1")
        val restored=ok(s.append("owner-1",snapshot,identity,undo)).journal
        assertEquals(snapshot,restored.snapshot)
        assertEquals(snapshot,restored.originalSnapshot)
        assertEquals(2L,restored.revision)
        assertTrue(ok(store().append("owner-1",snapshot,identity,undo))
            .replayed)
        assertEquals(restored,
            ok(store().open("owner-1",snapshot,identity)).journal)
        ok(s.append("owner-1",snapshot,identity,
            completed("original-record-0",2L,"set-1",10)))
        ok(s.append("owner-1",snapshot,identity,
            completed("original-record-1",3L,"set-2",9)))
        val finished=ok(s.append("owner-1",snapshot,identity,
            KotlinJournalOperation.Finish("finish-restored",4L))).journal
        assertEquals("finish-restored",finished.finishedByEventId)
        assertEquals(5,finished.receipts.size)
        assertEquals(snapshot,finished.snapshot)
        assertTrue(KotlinWorkoutHistoryEvidenceDomain.project(finished)
            is KotlinHistoryProjectionResult.Candidate)
        assertEquals(finished,
            ok(store().open("owner-1",snapshot,identity)).journal)
    }

    @Test fun startedComposerSwapCannotUndoAndTamperedBytesRefuseColdOpen() {
        val s=store()
        ok(s.issue("owner-1",snapshot,identity))
        val replacement=targets.map{it.copy(
            exerciseId="chest-machine",
            setKey="swap:swap-touched:working:${it.ordinal}")
        }
        val before=ok(s.append("owner-1",snapshot,identity,
            KotlinJournalOperation.SwapExercise(
                "swap-touched",0L,"db-press",replacement))).journal
        val performed=KotlinJournalOperation.RecordSet(
            "new-actual",1L,replacement.first().setKey,
            KotlinJournalSetOutcome.COMPLETED,
            KotlinPerformedSet(BigDecimal("30"),LoadUnit.LB,10,2))
        ok(s.append("owner-1",snapshot,identity,performed))
        assertEquals(NativeJournalFileResult.Rejected(
            NativeJournalFileRefusal.JOURNAL_REJECTED,
            KotlinJournalRefusal.SWAP_ALREADY_STARTED),
            s.append("owner-1",snapshot,identity,
                KotlinJournalOperation.UndoSwap(
                    "unsafe-undo",2L,"swap-touched")))
        assertEquals(2L,
            ok(store().open("owner-1",snapshot,identity)).journal.revision)
        val files=Files.list(temporary.root.toPath()).use{
            stream->stream.filter {
                it.fileName.toString().startsWith("journal-") &&
                    it.fileName.toString().endsWith(".bin")
            }.toList()
        }
        assertEquals(1,files.size)
        val bytes=Files.readAllBytes(files.single())
        bytes[bytes.size-1]=(bytes.last().toInt() xor 0x7f).toByte()
        Files.write(files.single(),bytes)
        assertEquals(NativeJournalFileResult.Rejected(
            NativeJournalFileRefusal.CORRUPT_RECEIPTS),
            store().open("owner-1",snapshot,identity))
    }
}
