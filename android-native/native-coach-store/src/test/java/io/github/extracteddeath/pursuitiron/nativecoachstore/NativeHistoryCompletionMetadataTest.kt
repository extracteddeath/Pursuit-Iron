package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.nio.ByteBuffer
import java.nio.file.Files
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class NativeHistoryCompletionMetadataTest {
    @get:Rule val temp=TemporaryFolder()
    private val owner="owner-1"
    private val athlete="athlete-1"
    private fun store()=NativeKotlinHistoryManifestFileStore(temp.root.toPath())
    private fun row(time:Long?)=KotlinHistoryManifestEntry(1,"workout-1","stored-1","finish-1",2,0,time)
    private fun permitted(o:String,a:String)=o==owner && a==athlete

    @Test fun datedAndUndatedEntriesRoundTripWithoutFillingAnyMissingDate() {
        val expected=listOf(row(1794690000000L),row(null).copy(completionOrder=2,
            workoutId="workout-2",storedReceiptId="stored-2",finishEventId="finish-2"))
        assertTrue(store().enrollVerifiedSnapshot(owner,athlete,expected,::permitted) is NativeHistoryLedgerResult.Durable)
        val actual=(store().open(owner,athlete) as NativeHistoryLedgerResult.Durable).ledger
        assertEquals(expected,actual.completions)
        assertNull(actual.completions.last().completedAtEpochMillis)
    }

    @Test fun finalizationCannotReplaceTheTimestampAlreadyBoundToThePendingIntent() {
        assertTrue(store().enroll(owner,athlete,::permitted) is NativeHistoryLedgerResult.Durable)
        val intent=NativeHistoryWriteIntent.Finish("workout-1",KotlinJournalOperation.Finish("finish-1",1),1000L)
        assertTrue(store().prepare(owner,athlete,1,intent) is NativeHistoryLedgerResult.Durable)
        assertEquals(NativeHistoryLedgerResult.Rejected(NativeHistoryLedgerRefusal.INVALID_MANIFEST_TRANSITION),
            store().finalize(owner,athlete,intent,row(1001L)))
        val pending=(store().open(owner,athlete) as NativeHistoryLedgerResult.Durable).ledger
        assertEquals(intent,pending.pending)
        assertTrue(pending.completions.isEmpty())
        assertTrue(store().finalize(owner,athlete,intent,row(1000L)) is NativeHistoryLedgerResult.Durable)
    }

    @Test fun aChecksumValidButOutOfRangeDateIsRejectedAsCorruptHistory() {
        val recorded=1794690000000L
        assertTrue(store().enrollVerifiedSnapshot(owner,athlete,listOf(row(recorded)),::permitted)
            is NativeHistoryLedgerResult.Durable)
        val file=Files.list(temp.root.toPath()).use{it.filter{path->Files.isRegularFile(path)}.findFirst().orElseThrow()}
        val bytes=Files.readAllBytes(file)
        val payloadSize=ByteBuffer.wrap(bytes).int
        val payload=bytes.copyOfRange(4,4+payloadSize)
        val marker=ByteBuffer.allocate(8).putLong(recorded).array()
        val locations=(0..payload.size-8).filter{index->payload.copyOfRange(index,index+8).contentEquals(marker)}
        assertEquals(1,locations.size)
        ByteBuffer.wrap(payload).putLong(locations.single(),Long.MAX_VALUE)
        val forged=ByteBuffer.allocate(4+payload.size+32).putInt(payload.size).put(payload)
            .put(MessageDigest.getInstance("SHA-256").digest(payload)).array()
        Files.write(file,forged)
        assertEquals(NativeHistoryLedgerResult.Rejected(NativeHistoryLedgerRefusal.CORRUPT_HISTORY),store().open(owner,athlete))
    }
}
