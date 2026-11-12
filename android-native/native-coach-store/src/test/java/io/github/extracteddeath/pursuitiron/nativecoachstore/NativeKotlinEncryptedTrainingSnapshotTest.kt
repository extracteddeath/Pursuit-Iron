package io.github.extracteddeath.pursuitiron.nativecoachstore

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class NativeKotlinEncryptedTrainingSnapshotTest {
    @get:Rule val temp=TemporaryFolder()
    private val root:Path get()=temp.root.toPath()
    private val owner=NativeKotlinOwnerIdentity(
        "local-11111111-1111-4111-8111-111111111111","athlete-1")
    private val password="long-private-passphrase-123".toCharArray()
    private fun fence()=NativeKotlinApprovalRevisionFence(root)

    private fun write(relative:String,text:String) {
        val path=root.resolve(relative)
        Files.createDirectories(path.parent)
        Files.write(path,text.toByteArray(Charsets.UTF_8))
    }

    private fun localIdentity() {
        // Actual app-private PIO1 v1 owner/athlete binary, including SHA256.
        val payload=java.io.ByteArrayOutputStream().also {bytes->
            java.io.DataOutputStream(bytes).use {output->
                output.writeInt(0x50494F31)
                output.writeInt(1)
                output.writeUTF(owner.ownerKey)
                output.writeUTF(owner.athleteId)
            }
        }.toByteArray()
        val checksum=MessageDigest.getInstance("SHA-256").digest(payload)
        Files.write(root.resolve("local-athlete.bin"),payload+checksum)
    }

    private fun capture(
        currentOwner:()->NativeKotlinOwnerIdentity?={owner},
        history:()->Boolean={true},
        key:CharArray=password
    )=NativeKotlinEncryptedTrainingSnapshot.capture(
        root,fence(),owner,currentOwner,history,key)

    @Test fun roundTripPreservesEntireNativeDirectoryWithoutWritingOrInventingData() {
        localIdentity()
        write("history/athlete-1.v2","completed corrected 215 actual 210")
        write("generated/journal-1.bin","generated workout with 2 difficult sets")
        write("custom/journal-2.bin","custom workout, amplifier and RIR unknown")
        write("programs/consent.bin","signed program and next-day constraints")
        write("machine/alternatives.bin","gym1-A gym1-B")
        write("some-incomplete-write.bin.bak","recoverable atomic backup")
        write("coach-approval-example.lock","temporary-process-lock")
        val filesBefore=listOf(
            "history/athlete-1.v2","generated/journal-1.bin",
            "custom/journal-2.bin","programs/consent.bin",
            "machine/alternatives.bin","some-incomplete-write.bin.bak",
            "local-athlete.bin")
        val originals=filesBefore.associateWith {Files.readAllBytes(root.resolve(it))}
        val result=capture()
        assertTrue(result.toString(),result is NativeKotlinTrainingSnapshotResult.Ready)
        val ready=result as NativeKotlinTrainingSnapshotResult.Ready
        assertEquals(7,ready.fileCount)
        assertTrue(ready.unencryptedBytes>0)
        assertFalse(String(ready.encrypted,Charsets.ISO_8859_1)
            .contains("completed corrected 215 actual 210"))
        val opened=NativeKotlinEncryptedTrainingSnapshot.inspect(
            ready.encrypted,password,owner)
        assertTrue(opened.toString(),opened is NativeKotlinTrainingSnapshotRead.Verified)
        val decoded=opened as NativeKotlinTrainingSnapshotRead.Verified
        assertEquals(owner,decoded.owner)
        assertEquals(filesBefore.sorted(),decoded.files.map{it.relativePath})
        decoded.files.forEach {
            assertArrayEquals(originals.getValue(it.relativePath),it.contents)
        }
        // Never exports the fence's ephemeral lock or modifies any workout.
        assertFalse(decoded.files.any{it.relativePath.endsWith(".lock")})
        filesBefore.forEach {
            assertArrayEquals(originals.getValue(it),Files.readAllBytes(root.resolve(it)))
        }
        // Fresh nonce/salt: identical source is not a deterministic ciphertext.
        val again=capture() as NativeKotlinTrainingSnapshotResult.Ready
        assertFalse(ready.encrypted.contentEquals(again.encrypted))
    }

    @Test fun wrongPassphraseTamperingAndTruncationAreAlwaysRefused() {
        localIdentity()
        write("journal/test.bin","my logged workouts")
        val encrypted=(capture() as NativeKotlinTrainingSnapshotResult.Ready).encrypted
        assertEquals(NativeKotlinTrainingSnapshotRead.Rejected(
            NativeKotlinTrainingSnapshotRefusal.INVALID_ARCHIVE_OR_PASSWORD),
            NativeKotlinEncryptedTrainingSnapshot.inspect(
                encrypted,"very-wrong-password-99".toCharArray()))
        val tampered=encrypted.copyOf()
        tampered[tampered.lastIndex-17]=(tampered[tampered.lastIndex-17].toInt() xor 7).toByte()
        assertEquals(NativeKotlinTrainingSnapshotRead.Rejected(
            NativeKotlinTrainingSnapshotRefusal.INVALID_ARCHIVE_OR_PASSWORD),
            NativeKotlinEncryptedTrainingSnapshot.inspect(tampered,password))
        assertEquals(NativeKotlinTrainingSnapshotRead.Rejected(
            NativeKotlinTrainingSnapshotRefusal.INVALID_ARCHIVE_OR_PASSWORD),
            NativeKotlinEncryptedTrainingSnapshot.inspect(
                encrypted.copyOf(encrypted.size-1),password))
        assertEquals(NativeKotlinTrainingSnapshotRead.Rejected(
            NativeKotlinTrainingSnapshotRefusal.OWNER_MISMATCH),
            NativeKotlinEncryptedTrainingSnapshot.inspect(
                encrypted,password,NativeKotlinOwnerIdentity(owner.ownerKey,"other")))
    }

    @Test fun currentOwnerAndVerifiedHistoryAreMandatory() {
        localIdentity()
        write("workouts/set.bin","an existing saved workout")
        assertEquals(NativeKotlinTrainingSnapshotResult.Rejected(
            NativeKotlinTrainingSnapshotRefusal.OWNER_MISMATCH),
            capture(currentOwner={NativeKotlinOwnerIdentity(owner.ownerKey,"other")}))
        assertEquals(NativeKotlinTrainingSnapshotResult.Rejected(
            NativeKotlinTrainingSnapshotRefusal.UNVERIFIED_HISTORY),
            capture(history={false}))
        assertEquals(NativeKotlinTrainingSnapshotResult.Rejected(
            NativeKotlinTrainingSnapshotRefusal.WEAK_PASSWORD),
            capture(key="short".toCharArray()))
        var checked=0
        assertEquals(NativeKotlinTrainingSnapshotResult.Rejected(
            NativeKotlinTrainingSnapshotRefusal.CHANGED_DURING_READ),
            capture(currentOwner={
                checked++
                if(checked>1)null else owner
            }))
    }

    @Test fun noOwnerIdentityAndLinksNeverBecomeBackupSuccess() {
        write("journal/issued.bin","set receipt without local identity")
        assertEquals(NativeKotlinTrainingSnapshotResult.Rejected(
            NativeKotlinTrainingSnapshotRefusal.INVALID_STORAGE),
            capture())
        localIdentity()
        val external=temp.newFile("outside.txt").toPath()
        val link=root.resolve("journal/linked.bin")
        Files.createSymbolicLink(link,external)
        assertEquals(NativeKotlinTrainingSnapshotResult.Rejected(
            NativeKotlinTrainingSnapshotRefusal.INVALID_STORAGE),
            capture())
        assertTrue(Files.exists(external))
    }

    @Test fun oversizeStoreIsRejectedRatherThanTruncatedIntoApparentlyValidBackup() {
        localIdentity()
        val big=ByteArray(16*1024*1024+1){2}
        Files.write(root.resolve("too-large.bin"),big)
        assertEquals(NativeKotlinTrainingSnapshotResult.Rejected(
            NativeKotlinTrainingSnapshotRefusal.TOO_LARGE),
            capture())
    }

    @Test fun historyCheckRunsInsideTheReentrantOwnerTransactionFence() {
        localIdentity()
        write("journal/test.bin","working set")
        val result=capture(history={
            // Nested use must not try to lock the same JVM file again.
            fence().withOwnerTransaction(owner.ownerKey){true}
        })
        assertTrue(result.toString(),result is NativeKotlinTrainingSnapshotResult.Ready)
    }
}
