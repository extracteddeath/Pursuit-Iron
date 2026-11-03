package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.math.BigDecimal
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class KotlinIssuedSnapshotStoreTest {
    @get:Rule val temporary=TemporaryFolder()
    private val source=KotlinTrainingSourceRevision(
        "athlete-1",4,"program-1",8,"gym-1",3,"catalog-5","coach-rules-2")
    private val original=KotlinStagedWorkout(
        source,"grant-1","workout-1",2,1,ComposerSessionPurpose.HYPERTROPHY,43,
        listOf(
            KotlinWorkoutTarget("barbell-bench",ComposerRole.PRIMARY,
                ComposerSessionPurpose.HYPERTROPHY,"bench:w0",
                DraftSetType.WARMUP,0,
                DraftSetLoad.Verified(LoadUnit.LB,LoadBasis.BARBELL_TOTAL,
                    BigDecimal("65.0")),RepBand(5,8),null,45,90),
            KotlinWorkoutTarget("barbell-bench",ComposerRole.PRIMARY,
                ComposerSessionPurpose.HYPERTROPHY,"bench:s1",
                DraftSetType.WORKING,1,
                DraftSetLoad.Verified(LoadUnit.LB,LoadBasis.BARBELL_TOTAL,
                    BigDecimal("105.25")),RepBand(5,8),2,60,120),
            KotlinWorkoutTarget("chin-up",ComposerRole.ACCESSORY,
                ComposerSessionPurpose.HYPERTROPHY,"chin:s1",
                DraftSetType.WORKING,0,
                DraftSetLoad.ExplicitBodyweight,RepBand(6,10),3,50,90)
        ))
    private val identity=KotlinStoredWorkoutIdentity(
        "commit-1",original.workoutId,source,1)
    private fun store()=KotlinNativeJournalFileStore(temporary.root.toPath())
    private val authorize:(String,KotlinTrainingSourceRevision)->Boolean =
        { owner, saved ->owner=="owner-1" && saved==source }
    private fun accepted(result:NativeJournalFileResult):NativeJournalFileResult.Durable {
        assertTrue("Unexpected: $result",result is NativeJournalFileResult.Durable)
        return result as NativeJournalFileResult.Durable
    }
    private fun set(id:String,rev:Long,key:String,load:BigDecimal?,reps:Int,rir:Int?)=
        KotlinJournalOperation.RecordSet(id,rev,key,KotlinJournalSetOutcome.COMPLETED,
            KotlinPerformedSet(load,if(load==null)null else LoadUnit.LB,reps,rir))

    @Test fun strictCodecPreservesEveryIssuedSetFieldAndRefusesMalformedInput() {
        val bytes=KotlinIssuedSnapshotCodec.encode(original)
        val decoded=KotlinIssuedSnapshotCodec.decode(bytes)
        assertEquals(original,decoded)
        assertEquals(BigDecimal("65.0"),
            (decoded.targets[0].load as DraftSetLoad.Verified).value)
        assertEquals(BigDecimal("105.25"),
            (decoded.targets[1].load as DraftSetLoad.Verified).value)
        assertNull(decoded.targets[0].targetRir)
        assertEquals(DraftSetLoad.ExplicitBodyweight,decoded.targets[2].load)
        assertThrows(java.io.IOException::class.java) {
            KotlinIssuedSnapshotCodec.decode(bytes+byteArrayOf(0))
        }
        val invalid=original.copy(targets=original.targets+
            original.targets.first().copy(ordinal=100))
        assertThrows(IllegalArgumentException::class.java) {
            KotlinIssuedSnapshotCodec.encode(invalid)
        }
        assertThrows(IllegalArgumentException::class.java) {
            KotlinIssuedSnapshotCodec.encode(original.copy(
                source=source.copy(programRevision=0)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            KotlinIssuedSnapshotCodec.encode(original.copy(
                targets=original.targets.mapIndexed { index,t ->
                    if(index==1)t.copy(load=DraftSetLoad.Verified(
                        LoadUnit.LB,LoadBasis.BARBELL_TOTAL,BigDecimal("159.5001")))
                    else t
                }))
        }
    }

    @Test fun coldRestorationNeedsOnlyPrivateStorageAndCurrentOwnerApproval() {
        val first=store()
        accepted(first.issueAuthorized("owner-1",original,identity,authorize))
        val before=accepted(store().openStored("owner-1","workout-1",authorize))
        assertEquals(original,before.journal.snapshot)
        assertEquals(identity,before.journal.storedIdentity)
        assertTrue(before.replayed)
        val w=accepted(store().appendStored("owner-1","workout-1",
            set("warmup",0,"bench:w0",BigDecimal("65"),8,null),authorize))
        val s=accepted(store().appendStored("owner-1","workout-1",
            set("bench1",1,"bench:s1",BigDecimal("105.25"),7,2),authorize))
        val b=accepted(store().appendStored("owner-1","workout-1",
            set("chin1",2,"chin:s1",null,8,3),authorize))
        assertEquals(3,b.journal.revision)
        val finish=KotlinJournalOperation.Finish("done",3)
        val finished=accepted(store().appendStored(
            "owner-1","workout-1",finish,authorize))
        assertEquals("done",finished.journal.finishedByEventId)
        val cold=accepted(store().openStored("owner-1","workout-1",authorize))
        assertEquals(finished.journal,cold.journal)
        assertEquals(original,cold.journal.snapshot)
        assertEquals(4,cold.journal.receipts.size)
        val replay=accepted(store().appendStored(
            "owner-1","workout-1",finish,authorize))
        assertTrue(replay.replayed)
        assertEquals(4,replay.journal.revision)
    }

    @Test fun unauthorizedIssueMustNotCreateAnyPersistentWorkout() {
        val refused=store().issueAuthorized("owner-1",original,identity){
            _,_ -> false
        }
        assertEquals(NativeJournalFileResult.Rejected(
            NativeJournalFileRefusal.NOT_AUTHORIZED),refused)
        assertEquals(NativeJournalFileResult.Rejected(
            NativeJournalFileRefusal.NOT_ISSUED),
            store().openStored("owner-1","workout-1",authorize))
        val issued=accepted(store().issueAuthorized(
            "owner-1",original,identity,authorize))
        assertFalse(issued.replayed)
        val duplicate=accepted(store().issueAuthorized(
            "owner-1",original,identity,authorize))
        assertTrue(duplicate.replayed)
        assertEquals(0,duplicate.journal.revision)
        assertEquals(NativeJournalFileResult.Rejected(
            NativeJournalFileRefusal.NOT_AUTHORIZED),
            store().issueAuthorized("owner-1",original,identity){_,_ ->
                throw SecurityException("auth expired")
            })
    }

    @Test fun wrongAthleteOrStaleCurrentSourceCannotReadOrAppend() {
        accepted(store().issueAuthorized("owner-1",original,identity,authorize))
        assertEquals(NativeJournalFileResult.Rejected(NativeJournalFileRefusal.NOT_ISSUED),
            store().openStored("owner-2","workout-1",authorize))
        assertEquals(NativeJournalFileResult.Rejected(NativeJournalFileRefusal.NOT_AUTHORIZED),
            store().openStored("owner-1","workout-1"){ _,_ -> false })
        assertEquals(NativeJournalFileResult.Rejected(NativeJournalFileRefusal.NOT_AUTHORIZED),
            store().appendStored("owner-1","workout-1",
                set("e1",0,"bench:w0",BigDecimal("65"),5,null)){ _,rev ->
                rev==source.copy(programRevision=9)
            })
        assertEquals(NativeJournalFileResult.Rejected(NativeJournalFileRefusal.NOT_AUTHORIZED),
            store().openStored("owner-1","workout-1"){ _,_ ->
                throw SecurityException("owner signed out")
            })
        assertEquals(NativeJournalFileResult.Rejected(NativeJournalFileRefusal.INVALID_ARGUMENT),
            store().openStored("../foreign","workout-1",authorize))
        assertEquals(NativeJournalFileResult.Rejected(NativeJournalFileRefusal.INVALID_ARGUMENT),
            store().openStored("owner-1","../foreign",authorize))
        assertEquals(0,accepted(store().openStored("owner-1","workout-1",authorize))
            .journal.revision)
    }

    @Test fun snapshotTamperOrCorruptReceiptNeverSilentlyResetsWorkout() {
        accepted(store().issueAuthorized("owner-1",original,identity,authorize))
        val bad=original.copy(targets=original.targets.mapIndexed { i,t ->
            if(i==1)t.copy(targetRir=0) else t
        })
        assertEquals(NativeJournalFileResult.Rejected(
            NativeJournalFileRefusal.SOURCE_CONFLICT),
            store().open("owner-1",bad,identity))
        val files=Files.list(temporary.root.toPath()).use { stream ->
            stream.filter {it.fileName.toString().endsWith(".bin")}
                .toArray().map {it as java.nio.file.Path}
        }
        assertEquals(1,files.size)
        val bytes=Files.readAllBytes(files[0])
        bytes[bytes.size/3]=(bytes[bytes.size/3].toInt() xor 0x04).toByte()
        Files.write(files[0],bytes)
        assertEquals(NativeJournalFileResult.Rejected(
            NativeJournalFileRefusal.CORRUPT_RECEIPTS),
            store().openStored("owner-1","workout-1",authorize))
        assertEquals(NativeJournalFileResult.Rejected(
            NativeJournalFileRefusal.CORRUPT_RECEIPTS),
            store().appendStored("owner-1","workout-1",
                set("bad",0,"bench:w0",BigDecimal("65"),8,null),authorize))
    }
}
