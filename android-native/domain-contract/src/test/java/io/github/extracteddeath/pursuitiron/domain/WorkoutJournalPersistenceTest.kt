package io.github.extracteddeath.pursuitiron.domain

import org.junit.Assert.*
import org.junit.Test

/** Synthetic store fault injection: no actual athlete history is written. */
class WorkoutJournalPersistenceTest {
    private class MemoryBytes : WorkoutJournalBytes {
        var committed: ByteArray? = null
        var attempts = 0
        var failBefore = false
        var failAfter = false
        override fun read(): ByteArray? = committed?.copyOf()
        override fun commitAtomically(bytes: ByteArray) {
            attempts++
            if (failBefore) throw IllegalStateException("Disk full")
            committed = bytes.copyOf()
            if (failAfter) throw IllegalStateException("Power lost after commit")
        }
    }

    private val session=JournalSessionKey(
        ReadOnlyProgramReference("synthetic-program",42,100,13,"a".repeat(64)),
        "workout-1"
    )
    private val a=JournalSetSlot(0,0,"bb-bench")
    private val b=JournalSetSlot(0,1,"bb-bench")
    private val c=JournalSetSlot(1,0,"cable-row")
    private val slots=listOf(a,b,c)
    private val observation=JournalSetValues("185.5","lb",8,2)
    private fun done(op:String,slot:JournalSetSlot)=
        JournalSetIntent(op,slot,JournalSetDisposition.COMPLETED,observation)
    private fun skip(op:String,slot:JournalSetSlot)=
        JournalSetIntent(op,slot,JournalSetDisposition.SKIPPED,null)

    @Test fun restartRecoversDurableUserValuesAndRefusesCompetingSession() {
        val drive=MemoryBytes()
        val journal=WorkoutJournalPersistence(drive)
        assertEquals(7,checkNotNull(journal.begin(session,slots,7)).revision)
        assertEquals(8,checkNotNull(journal.recordSet(session,7,done("op-1",a))).revision)
        val restarted=WorkoutJournalPersistence(drive)
        val restored=checkNotNull(restarted.recover())
        assertEquals(2,restored.unresolvedCount)
        assertEquals(observation,restored.completed.single().receipt.storedValues)
        assertEquals(slots,restored.slots)
        assertNotNull(restarted.begin(session,slots,7))
        assertNull(restarted.begin(session.copy(workoutId="other"),slots,7))
        assertNull(restarted.begin(session,slots,8))
    }

    @Test fun staleWritesDuplicateSlotsAndConflictingOpIdsDoNotPersist() {
        val drive=MemoryBytes()
        val journal=WorkoutJournalPersistence(drive)
        assertNotNull(journal.begin(session,slots,3))
        assertNotNull(journal.recordSet(session,3,done("first",a)))
        val before=drive.attempts
        assertNull(journal.recordSet(session,3,done("other",b)))
        assertNull(journal.recordSet(session,4,done("another",a)))
        assertNull(journal.recordSet(session.copy(workoutId="other"),4,done("other",b)))
        assertNull(journal.recordSet(session,4,done("first",b)))
        assertNull(journal.recordSet(session,4,done("first",a).copy(
            values=observation.copy(reps=9))))
        assertEquals(before,drive.attempts)
        assertEquals(4,checkNotNull(journal.recover()).revision)
    }

    @Test fun failedAtomicWriteDoesNotClaimSetOrWorkoutCompletion() {
        val drive=MemoryBytes()
        val journal=WorkoutJournalPersistence(drive)
        assertNotNull(journal.begin(session,listOf(a),2))
        drive.failBefore=true
        assertTrue(runCatching { journal.recordSet(session,2,done("set-1",a)) }.isFailure)
        assertEquals(2,checkNotNull(journal.recover()).revision)
        drive.failBefore=false
        assertNotNull(journal.recordSet(session,2,done("set-1",a)))
        drive.failBefore=true
        assertTrue(runCatching {
            journal.finish(session,3,JournalFinishIntent("finish-1"))
        }.isFailure)
        assertFalse(checkNotNull(journal.recover()).isFinished)
        drive.failBefore=false
        assertTrue(checkNotNull(journal.finish(session,3,JournalFinishIntent("finish-1"))).isFinished)
    }

    @Test fun crashAfterWriteReplaysWithoutDuplicateTransaction() {
        val drive=MemoryBytes()
        val journal=WorkoutJournalPersistence(drive)
        assertNotNull(journal.begin(session,listOf(a),10))
        drive.failAfter=true
        assertTrue(runCatching { journal.recordSet(session,10,done("repeat",a)) }.isFailure)
        drive.failAfter=false
        val restarted=WorkoutJournalPersistence(drive)
        val before=drive.attempts
        assertEquals(11,checkNotNull(restarted.recordSet(session,10,done("repeat",a))).revision)
        assertEquals(before,drive.attempts)
        assertNull(restarted.recordSet(session,10,done("repeat",a).copy(
            values=observation.copy(load="190"))))
        drive.failAfter=true
        assertTrue(runCatching {
            restarted.finish(session,11,JournalFinishIntent("finish"))
        }.isFailure)
        drive.failAfter=false
        val after=WorkoutJournalPersistence(drive)
        assertTrue(checkNotNull(after.recover()).isFinished)
        val attempts=drive.attempts
        assertEquals(12,checkNotNull(after.finish(session,11,JournalFinishIntent("finish"))).revision)
        assertEquals(attempts,drive.attempts)
    }

    @Test fun finishingRequiresEverySetDurablyCompletedOrSkipped() {
        val drive=MemoryBytes()
        val journal=WorkoutJournalPersistence(drive)
        assertNotNull(journal.begin(session,slots,10))
        assertNull(journal.finish(session,10,JournalFinishIntent("finish")))
        assertNotNull(journal.recordSet(session,10,done("s1",a)))
        assertNotNull(journal.recordSet(session,11,done("s2",b)))
        assertNull(journal.finish(session,12,JournalFinishIntent("finish")))
        assertNotNull(journal.recordSet(session,12,skip("s3",c)))
        val finished=checkNotNull(journal.finish(session,13,JournalFinishIntent("finish")))
        assertEquals(14,finished.revision)
        assertEquals(0,finished.unresolvedCount)
        assertTrue(checkNotNull(WorkoutJournalPersistence(drive).recover()).isFinished)
        assertNull(journal.recordSet(session,14,done("late",c)))
        assertNull(journal.finish(session,14,JournalFinishIntent("again")))
        assertNotNull(journal.begin(session,slots,10))
    }

    @Test fun damagedJournalFailsClosedAndNeverOverwritesLastKnownBytes() {
        val drive=MemoryBytes()
        val journal=WorkoutJournalPersistence(drive)
        val editable=slots.toMutableList()
        assertNotNull(journal.begin(session,editable,5))
        editable.clear()
        assertEquals(3,checkNotNull(journal.recover()).slots.size)
        val raw=checkNotNull(drive.committed)
        drive.committed=raw.copyOf().also { it[it.size/2]=(it[it.size/2].toInt() xor 1).toByte() }
        assertTrue(runCatching { journal.recover() }.isFailure)
        assertTrue(runCatching { journal.begin(session,slots,5) }.isFailure)
        drive.committed=ByteArray(WorkoutJournalPersistence.MAX_JOURNAL_BYTES+1)
        assertTrue(runCatching { journal.recover() }.isFailure)
        assertTrue(runCatching { journal.begin(session,slots,5) }.isFailure)
    }
}
