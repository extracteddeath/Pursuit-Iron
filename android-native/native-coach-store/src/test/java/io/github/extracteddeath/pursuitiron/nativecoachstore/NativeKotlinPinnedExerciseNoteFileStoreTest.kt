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
            save(setup=mapOf("__proto__" to "3")),
            save(setup=mapOf("attachment" to "x".repeat(201))),
            save(setup=mapOf("grip" to "  ")),
            save(id="../other"),
            save(revision=-1)
        )
        assertTrue(invalid.all{it is NativePinnedNoteWrite.Rejected})
        assertFalse(Files.list(temp.root.toPath()).use {stream->
            stream.anyMatch{it.fileName.toString().startsWith("pinned-exercise-")}
        })
    }

    @Test fun v2CatalogAndSavedOrphanFieldsSurviveLegacyColdUpgradeWithoutLoss() {
        fun persistedVersion():Int {
            val path=Files.list(temp.root.toPath()).use { stream->
                stream.filter{
                    it.fileName.toString().startsWith("pinned-exercise-") &&
                        it.fileName.toString().endsWith(".bin")
                }.findFirst().orElseThrow()
            }
            val raw=Files.readAllBytes(path)
            return java.nio.ByteBuffer.wrap(raw,8,4).int
        }
        assertTrue(save(setup=mapOf("grip" to "Pinky on ring"))
            is NativePinnedNoteWrite.Saved)
        assertEquals(1,persistedVersion())
        val first=(read() as NativePinnedNoteRead.Current).note
        val amended=save(revision=first.revision,event="pin-v2",
            text="Retain previous observations",
            setup=mapOf("grip" to "Pinky on ring",
                "rack_height" to "6", "obsoleteField" to "Keep this"))
        assertTrue(amended is NativePinnedNoteWrite.Saved)
        assertEquals(2,persistedVersion())
        val cold=(read() as NativePinnedNoteRead.Current).note
        assertEquals("6",cold.setup["rack_height"])
        assertEquals("Keep this",cold.setup["obsoleteField"])
        assertEquals("Pinky on ring",cold.setup["grip"])
        val withoutOrphan=save(revision=cold.revision,
            event="pin-clear-orphan",text=cold.text,
            setup=cold.setup-"obsoleteField")
        assertTrue(withoutOrphan is NativePinnedNoteWrite.Saved)
        assertFalse((read() as NativePinnedNoteRead.Current)
            .note.setup.containsKey("obsoleteField"))
        assertEquals(2,persistedVersion())
        assertEquals(NativePinnedNoteWrite.Rejected(
            NativePinnedNoteRefusal.REVISION_CONFLICT),
            save(revision=cold.revision,event="pin-stale-orphan",
                setup=cold.setup))
    }

    @Test fun boundedLegacyOrphanKeysPreserveOwnerSecurityAndRejectInvalidObjects() {
        assertTrue(NativePinnedExerciseNotePolicy.validContent(
            "saved",mapOf("oldPadSetting" to "3","back rest" to "5")))
        assertFalse(NativePinnedExerciseNotePolicy.validContent(
            "x",mapOf("constructor" to "bad")))
        assertFalse(NativePinnedExerciseNotePolicy.validContent(
            "x",mapOf("old\nkey" to "bad")))
        assertFalse(NativePinnedExerciseNotePolicy.validContent(
            "x",(1..33).associate{"field_"+it to "1"}))
        assertTrue(save(setup=mapOf("oldPadSetting" to "3"))
            is NativePinnedNoteWrite.Saved)
        assertEquals("3",(read() as NativePinnedNoteRead.Current)
            .note.setup["oldPadSetting"])
        active=NativeKotlinOwnerIdentity("owner-a","other-athlete")
        assertEquals(NativePinnedNoteRead.Rejected(
            NativePinnedNoteRefusal.CORRUPT_OR_PENDING),read())
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

    @Test fun exhaustiveRecoveryInventoryPreservesLegacyAndModernNoteSchemas() {
        val modern=save(event="note-modern",text="Bench pins at six",
            setup=mapOf("rack_height" to "6","oldPadSetting" to "3"))
        assertTrue(modern is NativePinnedNoteWrite.Saved)
        val legacy=save("paused_bench",event="note-v1",text="One count",
            setup=mapOf("grip" to "Pinky on ring"))
        assertTrue(legacy is NativePinnedNoteWrite.Saved)
        val scan=store().inventoryOwner("owner-a")
        assertTrue(scan.toString(),scan is NativePinnedNoteInventory.Verified)
        val notes=(scan as NativePinnedNoteInventory.Verified).notes
        assertEquals(2,notes.size)
        assertEquals((read() as NativePinnedNoteRead.Current).note,
            notes.single{it.exerciseId=="barbell_bench"})
        assertEquals((read("paused_bench") as NativePinnedNoteRead.Current).note,
            notes.single{it.exerciseId=="paused_bench"})
        // An exercise can disappear from a newer catalog without losing
        // personal setup notes. No workout load is inferred from the note.
        assertEquals("3",notes.single {it.exerciseId=="barbell_bench"}
            .setup["oldPadSetting"])
        active=null
        assertEquals(NativePinnedNoteInventory.InvalidOrUnrecognized,
            store().inventoryOwner("owner-a"))
    }

    @Test fun exhaustiveRecoveryInventoryRefusesCorruptedForeignOrRenamedNotes() {
        assertTrue(save() is NativePinnedNoteWrite.Saved)
        val saved=Files.list(temp.root.toPath()).use {stream->
            stream.filter{
                it.fileName.toString().startsWith("pinned-exercise-") &&
                    it.fileName.toString().endsWith(".bin")
            }.findFirst().orElseThrow()
        }
        val raw=Files.readAllBytes(saved)
        fun scan()=store().inventoryOwner("owner-a")
        assertTrue(scan() is NativePinnedNoteInventory.Verified)
        val corrupted=raw.copyOf()
        corrupted[corrupted.lastIndex]=(corrupted.last().toInt() xor 1).toByte()
        Files.write(saved,corrupted)
        assertEquals(NativePinnedNoteInventory.InvalidOrUnrecognized,scan())
        assertArrayEquals(corrupted,Files.readAllBytes(saved))
        Files.write(saved,raw)
        val renamed=saved.resolveSibling(
            "pinned-exercise-"+"b".repeat(64)+".bin")
        Files.move(saved,renamed)
        assertEquals(NativePinnedNoteInventory.InvalidOrUnrecognized,scan())
        Files.move(renamed,saved)
        active=NativeKotlinOwnerIdentity("owner-a","other-athlete")
        assertEquals(NativePinnedNoteInventory.InvalidOrUnrecognized,scan())
        active=NativeKotlinOwnerIdentity("owner-a","athlete-a")
        assertTrue(scan() is NativePinnedNoteInventory.Verified)
        // A genuine file belonging to a different owner is not ignored.
        active=NativeKotlinOwnerIdentity("owner-b","athlete-b")
        val foreignStore=NativeKotlinPinnedExerciseNoteFileStore(
            temp.root.toPath(),
            NativeKotlinApprovalRevisionFence(temp.root.toPath()),identity)
        assertTrue(foreignStore.save("owner-b","press",0,"other-owner-1",
            "Other athlete note",emptyMap()) is NativePinnedNoteWrite.Saved)
        active=NativeKotlinOwnerIdentity("owner-a","athlete-a")
        assertEquals(NativePinnedNoteInventory.InvalidOrUnrecognized,scan())
    }

    @Test fun exhaustiveRecoveryInventoryRefusesPendingUnrecognizedAndLinkedFiles() {
        assertTrue(save() is NativePinnedNoteWrite.Saved)
        val record=Files.list(temp.root.toPath()).use {stream->
            stream.filter{it.fileName.toString().startsWith("pinned-exercise-")}
                .findFirst().orElseThrow()
        }
        val leftovers=record.resolveSibling(
            record.fileName.toString().removeSuffix(".bin")+".pending")
        Files.write(leftovers,byteArrayOf(1,2,3))
        assertEquals(NativePinnedNoteInventory.InvalidOrUnrecognized,
            store().inventoryOwner("owner-a"))
        Files.delete(leftovers)
        val unexpected=temp.root.toPath().resolve(
            "pinned-exercise-"+"c".repeat(64)+".unknown")
        Files.write(unexpected,byteArrayOf(2))
        assertEquals(NativePinnedNoteInventory.InvalidOrUnrecognized,
            store().inventoryOwner("owner-a"))
        Files.delete(unexpected)
        val linked=temp.root.toPath().resolve(
            "pinned-exercise-"+"d".repeat(64)+".bin")
        Files.createSymbolicLink(linked,record)
        assertEquals(NativePinnedNoteInventory.InvalidOrUnrecognized,
            store().inventoryOwner("owner-a"))
        Files.delete(linked)
        assertTrue(store().inventoryOwner("owner-a")
            is NativePinnedNoteInventory.Verified)
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
