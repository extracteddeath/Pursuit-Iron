package io.github.extracteddeath.pursuitiron.nativecoachstore

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class NativeKotlinPinnedExerciseNoteFileStoreTest {
    @get:Rule val temp=TemporaryFolder()
    private var active:NativeKotlinOwnerIdentity?=
        NativeKotlinOwnerIdentity("owner-a","athlete-a")
    private val identity=NativeKotlinCurrentIdentity {active}
    private fun store()=NativeKotlinPinnedExerciseNoteFileStore(
        temp.root.toPath(),
        NativeKotlinApprovalRevisionFence(temp.root.toPath()),identity)
    private fun read(id:String="barbell_bench")=
        store().read("owner-a",id)
    private fun save(
        id:String="barbell_bench",revision:Long=0,event:String="pin-1",
        text:String="Pause on chest",
        setup:Map<String,String> =mapOf("grip" to "Pinky on ring")
    )=store().save("owner-a",id,revision,event,text,setup)

    @Test fun noteSurvivesColdReopenAndCarriesAcrossWorkoutsByExactExercise() {
        val empty=read() as NativePinnedNoteRead.Current
        assertEquals(0L,empty.note.revision)
        assertFalse(empty.note.hasContent)
        val first=save()
        assertTrue(first.toString(),first is NativePinnedNoteWrite.Saved)
        val saved=(first as NativePinnedNoteWrite.Saved).note
        assertEquals(1L,saved.revision)
        assertEquals("Pause on chest",saved.text)
        assertEquals("Pinky on ring",saved.setup["grip"])
        val reopened=read() as NativePinnedNoteRead.Current
        assertEquals(saved,reopened.note)
        assertEquals(NativePinnedNoteWrite.Saved(saved,true),save())
        assertEquals(1L,(read() as NativePinnedNoteRead.Current).note.revision)
        assertEquals(0L,(read("paused_bench") as NativePinnedNoteRead.Current)
            .note.revision) // Nearby pattern is NOT the same exercise ID.
        val second=save("paused_bench",event="pin-2",text="One count",setup=emptyMap())
        assertTrue(second is NativePinnedNoteWrite.Saved)
        assertEquals(saved,(read() as NativePinnedNoteRead.Current).note)
    }

    @Test fun clearsAndIndependentRevisionsDoNotConsumeWorkoutSets() {
        assertTrue(save() is NativePinnedNoteWrite.Saved)
        val clear=save(revision=1,event="pin-clear",text="",setup=emptyMap())
        val cleared=(clear as NativePinnedNoteWrite.Saved).note
        assertEquals(2L,cleared.revision)
        assertFalse(cleared.hasContent)
        assertEquals("",cleared.text)
        assertEquals(emptyMap<String,String>(),cleared.setup)
        assertEquals(cleared,(read() as NativePinnedNoteRead.Current).note)
        assertEquals(NativePinnedNoteWrite.Rejected(
            NativePinnedNoteRefusal.REVISION_CONFLICT),
            save(revision=0,event="pin-stale"))
        assertEquals(NativePinnedNoteWrite.Rejected(
            NativePinnedNoteRefusal.EVENT_ID_COLLISION),
            save(revision=1,event="pin-clear",text="Different"))
        assertEquals(2L,(read() as NativePinnedNoteRead.Current).note.revision)
    }

    @Test fun malformedInputAndInventedSetupKeysNeverWrite() {
        val invalid=listOf(
            save(event="bad id"),
            save(text="x".repeat(2001)),
            save(text="bad\u0000note"),
            save(text="\uD800"),
            save(setup=mapOf("invented-machine-metadata" to "3")),
            save(setup=mapOf("attachment" to "x".repeat(161))),
            save(setup=mapOf("grip" to "  ")),
            save(id="../other"),
            save(revision=-1)
        )
        assertTrue(invalid.all{it is NativePinnedNoteWrite.Rejected})
        assertFalse(Files.list(temp.root.toPath()).use {stream->
            stream.anyMatch{it.fileName.toString().startsWith("pinned-exercise-")}
        })
    }

    @Test fun wrongOwnerAndCorruptOrPendingBytesFailClosed() {
        save()
        active=NativeKotlinOwnerIdentity("owner-a","other-athlete")
        assertEquals(NativePinnedNoteRead.Rejected(
            NativePinnedNoteRefusal.CORRUPT_OR_PENDING),read())
        active=null
        assertEquals(NativePinnedNoteRead.Rejected(
            NativePinnedNoteRefusal.NOT_AUTHORIZED),read())
        active=NativeKotlinOwnerIdentity("owner-a","athlete-a")
        val path=Files.list(temp.root.toPath()).use {stream->
            stream.filter{
                it.fileName.toString().startsWith("pinned-exercise-") &&
                    it.fileName.toString().endsWith(".bin")
            }.findFirst().orElseThrow()
        }
        val bytes=Files.readAllBytes(path)
        bytes[bytes.lastIndex]=(bytes.last().toInt() xor 1).toByte()
        Files.write(path,bytes)
        assertEquals(NativePinnedNoteRead.Rejected(
            NativePinnedNoteRefusal.CORRUPT_OR_PENDING),read())
        assertEquals(NativePinnedNoteWrite.Rejected(
            NativePinnedNoteRefusal.CORRUPT_OR_PENDING),
            save(revision=1,event="pin-again"))
        // Recreate the genuine file before simulating an unresolved rename.
        Files.delete(path)
        save()
        val pending=path.resolveSibling(
            path.fileName.toString().removeSuffix(".bin")+".pending")
        Files.write(pending,byteArrayOf(1))
        assertEquals(NativePinnedNoteRead.Rejected(
            NativePinnedNoteRefusal.CORRUPT_OR_PENDING),read())
    }

    @Test fun ownerFenceAndPerExerciseCasPermitOnlyOneConcurrentEdit() {
        val executor=Executors.newFixedThreadPool(2)
        val gate=CountDownLatch(1)
        try {
            val submissions=listOf("note-one","note-two").map { id ->
                executor.submit<NativePinnedNoteWrite> {
                    gate.await()
                    save(event=id,text=id)
                }
            }
            gate.countDown()
            val results=submissions.map{it.get(15,TimeUnit.SECONDS)}
            assertEquals(1,results.count{it is NativePinnedNoteWrite.Saved})
            assertEquals(1,results.count{
                it==NativePinnedNoteWrite.Rejected(
                    NativePinnedNoteRefusal.REVISION_CONFLICT)
            })
            val note=(read() as NativePinnedNoteRead.Current).note
            assertEquals(1L,note.revision)
            assertEquals(1,results.count{
                it is NativePinnedNoteWrite.Saved && it.note==note
            })
        }finally{executor.shutdownNow()}
    }
}
