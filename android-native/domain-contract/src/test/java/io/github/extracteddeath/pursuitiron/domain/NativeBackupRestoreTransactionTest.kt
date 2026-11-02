package io.github.extracteddeath.pursuitiron.domain

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

/** Entirely synthetic: no user backup, external document, Compose or APK. */
class NativeBackupRestoreTransactionTest {
    private val before="""{"v":13,"savedAt":10,"saved":[],"history":[],"cycles":[],"custom":[],"gyms":[],"perf":{},"tombs":{}}"""
    private val incoming="""{"v":13,"savedAt":11,"saved":[],"history":[{"id":"added","date":20,"updatedAt":20}],"cycles":[],"custom":[],"gyms":[],"perf":{},"tombs":{}}"""
    private val candidate="""{"v":13,"savedAt":11,"saved":[],"history":[{"id":"added","date":20,"updatedAt":20}],"cycles":[],"custom":[],"gyms":[],"perf":{},"tombs":{}}"""
    private fun sha(v: String)=WorkoutStoreRuntimeCodec.sha(v)
    private class Drive : CanonicalWorkoutStoreBytes,WorkoutJournalBytes {
        var raw: ByteArray?=null
        var writes=0
        var failBefore=false
        var failAfter=false
        override fun read()=raw?.copyOf()
        override fun commitAtomically(bytes: ByteArray) {
            if(failBefore)throw IOException("before atomic write")
            raw=bytes.copyOf();writes++
            if(failAfter)throw IOException("post-commit acknowledgement lost")
        }
        override fun clearAtomically() { raw=null }
    }
    private fun <T> now(block: suspend () -> T): T {
        var value: Result<T>?=null
        block.startCoroutine(object: Continuation<T> {
            override val context=EmptyCoroutineContext
            override fun resumeWith(result: Result<T>) { value=result }
        })
        return checkNotNull(value).getOrThrow()
    }
    private inner class Engine: PinnedCanonicalWorkoutStoreEngine,PinnedCanonicalBackupMergeEngine {
        var onPreview: (() -> Unit)?=null
        override suspend fun validateInitialSnapshot(exactSnapshot: String)=
            exactSnapshot==before || exactSnapshot==candidate
        override suspend fun prepareCompletion(origin: String,current: String,finishedJournal: String): String?=null
        override suspend fun previewBackupMerge(currentSnapshot: String,incomingSnapshot: String): NativeBackupMergePreview? {
            onPreview?.invoke()
            if(currentSnapshot!=before || incomingSnapshot!=incoming)return null
            return NativeBackupMergePreview(sha(before),sha(incoming),sha(candidate),
                candidate.toByteArray(Charsets.UTF_8).size,
                NativeBackupMergeCounts(0,0,0,0,0),NativeBackupMergeCounts(0,1,0,0,0),
                NativeBackupMergeCounts(0,1,0,0,0),0,0,0,0,0,emptyList())
        }
        override suspend fun prepareBackupMerge(currentSnapshot: String,incomingSnapshot: String,
            expectedCandidateSha256: String): NativeBackupMergePreparation? {
            if(currentSnapshot!=before || incomingSnapshot!=incoming ||
                expectedCandidateSha256!=sha(candidate))return null
            return NativeBackupMergePreparation(candidate,sha(before),sha(incoming),
                sha(candidate),candidate.toByteArray(Charsets.UTF_8).size)
        }
    }
    private inner class Setup {
        val drive=Drive()
        val engine=Engine()
        var store=CanonicalWorkoutStorePersistence(drive,engine)
        val approval=NativeBackupRestoreApproval("restore-1",sha(before),sha(incoming),sha(candidate))
        fun bootstrap() { assertTrue(now { store.initialize(before) }) }
        fun current(): String {
            val bytes=checkNotNull(now { store.readOwnedCurrentStoreBytes() })
            return try { String(bytes,Charsets.UTF_8) } finally { bytes.fill(0) }
        }
        fun install()=now { store.installApprovedBackupMerge(incoming,approval) }
        fun reboot() { store=CanonicalWorkoutStorePersistence(drive,engine) }
    }
    @Test fun approvedAdditiveMergeAndExactUndoAreAtomicAndIdempotent() {
        val s=Setup();s.bootstrap()
        assertEquals(before,s.current())
        val originalWrites=s.drive.writes
        val undo=checkNotNull(s.install())
        assertEquals(sha(candidate),undo.installedSha256)
        assertEquals(originalWrites+1,s.drive.writes)
        assertEquals(candidate,s.current())
        assertEquals(2,JSONObject(String(checkNotNull(s.drive.raw),Charsets.UTF_8)).getInt("format"))
        s.reboot()
        assertEquals(sha(candidate),checkNotNull(s.install()).installedSha256)
        assertEquals(originalWrites+1,s.drive.writes)
        assertTrue(s.store.undoApprovedBackupMerge("restore-1"))
        assertEquals(before,s.current())
        assertEquals(originalWrites+2,s.drive.writes)
        s.reboot()
        assertTrue(s.store.undoApprovedBackupMerge("restore-1"))
        assertEquals(originalWrites+2,s.drive.writes)
        assertFalse(s.store.undoApprovedBackupMerge("different-operation"))
    }
    @Test fun lostAcknowledgementsRecoverExactCommittedMergeAndUndo() {
        val s=Setup();s.bootstrap()
        s.drive.failAfter=true
        assertTrue(runCatching { s.install() }.isFailure)
        s.drive.failAfter=false
        assertEquals(candidate,s.current())
        val writes=s.drive.writes
        s.reboot();assertNotNull(s.install())
        assertEquals(writes,s.drive.writes)
        s.drive.failAfter=true
        assertTrue(runCatching { s.store.undoApprovedBackupMerge("restore-1") }.isFailure)
        s.drive.failAfter=false
        assertEquals(before,s.current())
        val afterUndo=s.drive.writes
        s.reboot();assertTrue(s.store.undoApprovedBackupMerge("restore-1"))
        assertEquals(afterUndo,s.drive.writes)
    }
    @Test fun staleAuthFailuresAndInFlightStoreMutationsNeverPublishCandidate() {
        val s=Setup();s.bootstrap()
        val starting=s.drive.writes
        assertNull(now { s.store.installApprovedBackupMerge(incoming,
            NativeBackupRestoreApproval("wrong",sha(before),sha(incoming),"f".repeat(64))) })
        assertNull(now { s.store.installApprovedBackupMerge(incoming+" ",
            s.approval) })
        assertEquals(starting,s.drive.writes)
        // A store change after engine computation must invalidate the
        // captured expected state, even if the candidate itself was valid.
        val session=JournalSessionKey(ReadOnlyProgramReference("demo",1,10,13,sha(before)),
            "workout","day",1,"lb",100)
        val journal=WorkoutJournalPersistence(Drive())
        s.engine.onPreview={
            assertNotNull(s.store.beginSession(session,listOf(JournalSetSlot(0,0,"bb-bench")),0,journal))
            s.engine.onPreview=null
        }
        assertNull(s.install())
        assertEquals(before,s.current())
        assertFalse(s.store.undoApprovedBackupMerge("restore-1"))
    }
    @Test fun startingNewTrainingInvalidatesUncommittedUndoRights() {
        val s=Setup();s.bootstrap();assertNotNull(s.install())
        val revision=ReadOnlyProgramReference("demo",1,11,13,sha(candidate))
        val session=JournalSessionKey(revision,"workout-2","day",1,"lb",100)
        val journal=WorkoutJournalPersistence(Drive())
        assertNotNull(s.store.beginSession(session,listOf(JournalSetSlot(0,0,"bb-bench")),0,journal))
        assertFalse(s.store.undoApprovedBackupMerge("restore-1"))
        assertEquals(candidate,s.current())
    }
    @Test fun failedWritesAndInvalidFormatPreserveExistingBytes() {
        val s=Setup();s.bootstrap()
        val original=checkNotNull(s.drive.raw).copyOf()
        s.drive.failBefore=true
        assertTrue(runCatching { s.install() }.isFailure)
        assertArrayEquals(original,s.drive.raw)
        s.drive.failBefore=false
        assertNotNull(s.install())
        val stored=checkNotNull(s.drive.raw).copyOf()
        s.drive.raw=stored.copyOf().apply { this[lastIndex]=(this[lastIndex].toInt() xor 1).toByte() }
        assertTrue(runCatching { s.current() }.isFailure)
        assertFalse(stored.contentEquals(s.drive.raw))
        // Corrupt bytes remain untouched instead of silently becoming empty.
        assertEquals(stored.size,s.drive.raw?.size)
    }
}
