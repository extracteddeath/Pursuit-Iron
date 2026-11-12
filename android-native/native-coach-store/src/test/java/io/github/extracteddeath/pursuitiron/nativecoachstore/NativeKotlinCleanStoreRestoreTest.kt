package io.github.extracteddeath.pursuitiron.nativecoachstore

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Clean-install transaction tests; the real corrected-journal decoder path
 * is exercised in NativeKotlinTrackedWorkoutGatewayTest.
 */
class NativeKotlinCleanStoreRestoreTest {
    @get:Rule val temp=TemporaryFolder()
    private val owner=NativeKotlinOwnerIdentity(
        "local-11111111-1111-4111-8111-111111111111","athlete-1")
    private val password="restore-strong-private-password-123".toCharArray()
    private fun source()=temp.newFolder("source").toPath()
    private fun target()=temp.newFolder("new-device").toPath()
        .resolve("kotlin-coach-v1")
    private fun makeArchive(source:Path):ByteArray {
        val body=java.io.ByteArrayOutputStream().also {stream->
            java.io.DataOutputStream(stream).use {out->
                out.writeInt(0x50494F31)
                out.writeInt(1)
                out.writeUTF(owner.ownerKey)
                out.writeUTF(owner.athleteId)
            }
        }.toByteArray()
        Files.write(source.resolve("local-athlete.bin"),body+
            MessageDigest.getInstance("SHA-256").digest(body))
        val journal=source.resolve("history/finished.bin")
        Files.createDirectories(journal.parent)
        Files.write(journal,"finished-and-corrected-set".toByteArray())
        val snapshot=NativeKotlinEncryptedTrainingSnapshot.capture(
            source,NativeKotlinApprovalRevisionFence(source),
            owner,{owner},{true},password)
        assertTrue(snapshot.toString(),snapshot is NativeKotlinTrainingSnapshotResult.Ready)
        return (snapshot as NativeKotlinTrainingSnapshotResult.Ready).encrypted
    }
    private fun rejected(reason:NativeKotlinCleanRestoreRefusal) =
        NativeKotlinCleanRestoreResult.Rejected(reason)

    @Test fun anExistingAthleteIsNeverReplacedEvenIfTheFolderIsEmpty() {
        val archive=makeArchive(source())
        val target=target()
        Files.createDirectories(target)
        var verified=0
        assertEquals(rejected(NativeKotlinCleanRestoreRefusal.EXISTING_TRAINING_DATA),
            NativeKotlinCleanStoreRestore.restoreToAbsentStore(
                target,archive,password,owner,{_,_->verified++;true}))
        assertEquals(0,verified)
        assertTrue(Files.isDirectory(target))
        val existing=target.resolve("workout-real.bin")
        Files.write(existing,"existing 315lb squat".toByteArray())
        assertEquals(rejected(NativeKotlinCleanRestoreRefusal.EXISTING_TRAINING_DATA),
            NativeKotlinCleanStoreRestore.restoreToAbsentStore(
                target,archive,password,owner,{_,_->verified++;true}))
        assertEquals(0,verified)
        assertEquals("existing 315lb squat",Files.readString(existing))
    }

    @Test fun passwordTamperingAndOwnerMismatchCreateNoStoreOrStaging() {
        val archive=makeArchive(source())
        val target=target()
        val badArchive=archive.copyOf().also {
            it[it.lastIndex-3]=(it[it.lastIndex-3].toInt() xor 1).toByte()
        }
        val archiveAttempts=listOf(
            archive to "wrong-long-password-987".toCharArray(),
            badArchive to password
        )
        for((contents,pass) in archiveAttempts) {
            assertEquals(rejected(NativeKotlinCleanRestoreRefusal.BAD_ARCHIVE),
                NativeKotlinCleanStoreRestore.restoreToAbsentStore(
                    target,contents,pass,owner,{_,_->true}))
            assertFalse(Files.exists(target))
            assertFalse(Files.exists(
                target.parent.resolve(".kotlin-coach-v1.restore-staging")))
        }
        assertEquals(rejected(NativeKotlinCleanRestoreRefusal.BAD_ARCHIVE),
            NativeKotlinCleanStoreRestore.restoreToAbsentStore(
                target,archive,password,
                NativeKotlinOwnerIdentity(owner.ownerKey,"foreign"),
                {_,_->true}))
        assertFalse(Files.exists(target))
    }

    @Test fun stagedVerificationFailureCannotPublishEvenOneWorkoutFile() {
        val archive=makeArchive(source())
        val target=target()
        var checked=0
        assertEquals(rejected(NativeKotlinCleanRestoreRefusal.INVALID_RESTORED_STORE),
            NativeKotlinCleanStoreRestore.restoreToAbsentStore(
                target,archive,password,owner,{stage,from->
                    checked++
                    assertEquals(owner,from)
                    assertTrue(Files.isRegularFile(
                        stage.resolve("history/finished.bin")))
                    false
                }))
        assertEquals(1,checked)
        assertFalse(Files.exists(target))
        assertFalse(Files.exists(
            target.parent.resolve(".kotlin-coach-v1.restore-staging")))
    }

    @Test fun verifierMustNotChangeDecryptedFilesOrAddPermanentFiles() {
        val archive=makeArchive(source())
        val target=target()
        val changed=NativeKotlinCleanStoreRestore.restoreToAbsentStore(
            target,archive,password,owner,{stage,_->
                Files.write(stage.resolve("history/finished.bin"),
                    "silent mutation".toByteArray())
                true
            })
        assertEquals(rejected(NativeKotlinCleanRestoreRefusal.INVALID_RESTORED_STORE),
            changed)
        assertFalse(Files.exists(target))
        val unexpected=NativeKotlinCleanStoreRestore.restoreToAbsentStore(
            target,archive,password,owner,{stage,_->
                Files.write(stage.resolve("unexpected-exercise.bin"),
                    "unverified plan".toByteArray())
                true
            })
        assertEquals(rejected(NativeKotlinCleanRestoreRefusal.INVALID_RESTORED_STORE),
            unexpected)
        assertFalse(Files.exists(target))
    }

    @Test fun aNewlyCreatedLiveStoreBlocksTheCommitAndRemainsUntouched() {
        val archive=makeArchive(source())
        val target=target()
        var verified=0
        assertEquals(rejected(NativeKotlinCleanRestoreRefusal.EXISTING_TRAINING_DATA),
            NativeKotlinCleanStoreRestore.restoreToAbsentStore(
                target,archive,password,owner,{_,_->
                    verified++
                    Files.createDirectories(target)
                    Files.write(target.resolve("other-lifter.bin"),
                        "220x5".toByteArray())
                    true
                }))
        assertEquals(1,verified)
        assertEquals("220x5",Files.readString(
            target.resolve("other-lifter.bin")))
        assertFalse(Files.exists(
            target.parent.resolve(".kotlin-coach-v1.restore-staging")))
    }

    @Test fun anInterruptedPrivateStageIsSafelyCleanedBeforeFreshRestore() {
        val archive=makeArchive(source())
        val target=target()
        val stage=target.parent.resolve(".kotlin-coach-v1.restore-staging")
        Files.createDirectory(stage)
        Files.write(stage.resolve(".restore-in-progress"),
            "PursuitIronKotlinStaging-v1".toByteArray())
        Files.write(stage.resolve("incomplete-journal.bin"),
            "partial data from crashed restore".toByteArray())
        val result=NativeKotlinCleanStoreRestore.restoreToAbsentStore(
            target,archive,password,owner,{stageRoot,from->
                from==owner && Files.exists(
                    stageRoot.resolve("history/finished.bin"))
            })
        assertTrue(result.toString(),result is NativeKotlinCleanRestoreResult.Committed)
        assertTrue(Files.isRegularFile(target.resolve("history/finished.bin")))
        assertFalse(Files.exists(target.resolve("incomplete-journal.bin")))
        assertFalse(Files.exists(target.resolve(".restore-in-progress")))
        assertFalse(Files.exists(stage))
    }

    @Test fun foreignOrUnmarkedStageIsNotDeletedAsAConvenience() {
        val archive=makeArchive(source())
        val target=target()
        val stage=target.parent.resolve(".kotlin-coach-v1.restore-staging")
        Files.createDirectory(stage)
        Files.write(stage.resolve("other-workouts.bin"),
            "do not delete this".toByteArray())
        assertEquals(rejected(NativeKotlinCleanRestoreRefusal.UNTRUSTED_STAGING),
            NativeKotlinCleanStoreRestore.restoreToAbsentStore(
                target,archive,password,owner,{_,_->true}))
        assertEquals("do not delete this",
            Files.readString(stage.resolve("other-workouts.bin")))
        assertFalse(Files.exists(target))
    }

    @Test fun symlinkedLockOrWrongRootCannotReceivePrivateAthleteData() {
        val archive=makeArchive(source())
        val target=target()
        val incorrect=target.parent.resolve("unrelated-data")
        assertEquals(rejected(NativeKotlinCleanRestoreRefusal.INVALID_TARGET),
            NativeKotlinCleanStoreRestore.restoreToAbsentStore(
                incorrect,archive,password,owner,{_,_->true}))
        val elsewhere=temp.newFile("not-an-app-lock").toPath()
        val lock=target.parent.resolve(".kotlin-coach-v1.restore-gate.lock")
        Files.createSymbolicLink(lock,elsewhere)
        assertEquals(rejected(NativeKotlinCleanRestoreRefusal.UNTRUSTED_STAGING),
            NativeKotlinCleanStoreRestore.restoreToAbsentStore(
                target,archive,password,owner,{_,_->true}))
        assertFalse(Files.exists(target))
    }
}
