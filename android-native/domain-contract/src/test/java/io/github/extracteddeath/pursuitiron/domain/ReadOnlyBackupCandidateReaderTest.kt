package io.github.extracteddeath.pursuitiron.domain

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream

class ReadOnlyBackupCandidateReaderTest {
    private class Tracked(input: InputStream) : FilterInputStream(input) {
        var closed = false
        override fun close() { closed = true; super.close() }
    }

    @Test fun smallCopyMatchesTheExactWholeSourceFingerprintAndCanBeCleared() {
        val bytes = """{"v":13,"history":[],"saved":[]}""".toByteArray()
        val stream = Tracked(ByteArrayInputStream(bytes))
        val small = ReadOnlyBackupCandidateReader.screen(stream)
        assertTrue(stream.closed)
        assertTrue(small is ReadOnlyDocumentScreening.SmallCandidate)
        small as ReadOnlyDocumentScreening.SmallCandidate
        assertArrayEquals(bytes,small.bytes)
        val expected = ReadOnlyBackupScreening.fingerprint(ByteArrayInputStream(bytes))
        assertEquals(expected,small.fingerprint)
        assertEquals(String(bytes),ReadOnlyBackupSourceGate.exactText(small.bytes,small.fingerprint))
        small.bytes.fill(0)
        assertArrayEquals(ByteArray(bytes.size),small.bytes)
    }

    @Test fun thresholdAndLargerFilesNeverReturnTruncatedCandidate() {
        val limit = ReadOnlyBackupCandidateReader.MAX_CANDIDATE_BYTES
        val exact=ByteArray(limit) { 'A'.code.toByte() }
        val atLimit=ReadOnlyBackupCandidateReader.screen(ByteArrayInputStream(exact))
        assertTrue(atLimit is ReadOnlyDocumentScreening.SmallCandidate)
        (atLimit as ReadOnlyDocumentScreening.SmallCandidate).bytes.fill(0)

        val over=ByteArray(limit+1) { 'B'.code.toByte() }
        val stream=Tracked(ByteArrayInputStream(over))
        val large=ReadOnlyBackupCandidateReader.screen(stream)
        assertTrue(stream.closed)
        assertTrue(large is ReadOnlyDocumentScreening.FingerprintOnly)
        assertEquals(over.size.toLong(),large.fingerprint.byteCount)
        assertEquals(
            ReadOnlyBackupScreening.fingerprint(ByteArrayInputStream(over)),
            large.fingerprint
        )
    }

    @Test fun errorsCloseTheStreamWithoutProducingARequest() {
        val empty=Tracked(ByteArrayInputStream(byteArrayOf()))
        assertTrue(runCatching { ReadOnlyBackupCandidateReader.screen(empty) }.isFailure)
        assertTrue(empty.closed)
        val exceeded=Tracked(ByteArrayInputStream(ByteArray(12)))
        assertTrue(runCatching { ReadOnlyBackupCandidateReader.screen(exceeded,11) }.isFailure)
        assertTrue(exceeded.closed)
        val stalled=Tracked(object : InputStream() {
            override fun read(): Int = 0
            override fun read(b: ByteArray, off: Int, len: Int): Int = 0
        })
        assertTrue(runCatching { ReadOnlyBackupCandidateReader.screen(stalled) }.isFailure)
        assertTrue(stalled.closed)
        assertTrue(runCatching {
            ReadOnlyBackupCandidateReader.screen(ByteArrayInputStream(byteArrayOf(1)),0)
        }.isFailure)
        val broken=Tracked(object : InputStream() {
            override fun read(): Int = throw IOException("synthetic")
            override fun read(b: ByteArray, off: Int, len: Int): Int =
                throw IOException("synthetic")
        })
        assertTrue(runCatching { ReadOnlyBackupCandidateReader.screen(broken) }.isFailure)
        assertTrue(broken.closed)
    }

    @Test fun aCallerByteArrayCannotMutateTheCapturedCandidate() {
        val bytes=byteArrayOf(1,2,3,4)
        val screened=ReadOnlyBackupCandidateReader.screen(ByteArrayInputStream(bytes))
            as ReadOnlyDocumentScreening.SmallCandidate
        bytes[0]=7
        assertEquals(1,screened.bytes[0].toInt())
        screened.bytes.fill(0)
    }
    @Test fun cancellationStopsAReadInProgressClosesTheSourceAndReturnsNoCandidate() {
        val source = Tracked(ByteArrayInputStream(ByteArray(96 * 1024)))
        val cancelled = IllegalStateException("synthetic cancellation")
        var checkpoints = 0
        val failure = runCatching {
            ReadOnlyBackupCandidateReader.screen(source, checkActive = {
                if (++checkpoints == 4) throw cancelled
            })
        }.exceptionOrNull()
        assertSame(cancelled, failure)
        assertTrue("Provider stream must close on cancellation", source.closed)
        assertEquals("Cancellation must interrupt before whole copy is read", 4, checkpoints)
    }

}
