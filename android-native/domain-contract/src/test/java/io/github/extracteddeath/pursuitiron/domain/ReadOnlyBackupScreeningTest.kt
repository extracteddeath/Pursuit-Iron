package io.github.extracteddeath.pursuitiron.domain

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream

class ReadOnlyBackupScreeningTest {
    private class ObservedStream(delegate: InputStream) : FilterInputStream(delegate) {
        var closed = false
        override fun close() { closed = true; super.close() }
    }

    @Test fun syntheticBytesAreHashedWithoutKeepingOrParsingTheirContents() {
        val contents = "synthetic-not-a-real-backup".toByteArray()
        val stream = ObservedStream(ByteArrayInputStream(contents))
        val result = ReadOnlyBackupScreening.fingerprint(stream)
        assertTrue(stream.closed)
        assertEquals(contents.size.toLong(), result.byteCount)
        assertTrue(result.sha256Hex.matches(Regex("^[0-9a-f]{64}$")))
        assertEquals(result, ReadOnlyBackupScreening.fingerprint(ByteArrayInputStream(contents)))
        assertEquals(listOf("byteCount", "sha256Hex"),
            UnverifiedBackupFingerprint::class.java.declaredFields
                .filter { !it.isSynthetic && !it.name.startsWith("$") }
                .map { it.name })
    }

    @Test fun rejectsOversizeEmptyAndStalledStreamsAndClosesEachOne() {
        val oversize = ObservedStream(ByteArrayInputStream(ByteArray(14)))
        assertTrue(runCatching { ReadOnlyBackupScreening.fingerprint(oversize, 13) }.isFailure)
        assertTrue(oversize.closed)
        val empty = ObservedStream(ByteArrayInputStream(ByteArray(0)))
        assertTrue(runCatching { ReadOnlyBackupScreening.fingerprint(empty) }.isFailure)
        assertTrue(empty.closed)
        val stalled = ObservedStream(object : InputStream() {
            override fun read(): Int = 0
            override fun read(b: ByteArray, off: Int, len: Int): Int = 0
        })
        assertTrue(runCatching { ReadOnlyBackupScreening.fingerprint(stalled) }.isFailure)
        assertTrue(stalled.closed)
        assertTrue(runCatching {
            ReadOnlyBackupScreening.fingerprint(ByteArrayInputStream(byteArrayOf(1)), 0)
        }.isFailure)
    }

    @Test fun anIOFailureClosesTheSource() {
        val broken = ObservedStream(object : InputStream() {
            override fun read(): Int = throw IOException("stream failure")
            override fun read(b: ByteArray, off: Int, len: Int): Int =
                throw IOException("stream failure")
        })
        assertTrue(runCatching { ReadOnlyBackupScreening.fingerprint(broken) }.isFailure)
        assertTrue(broken.closed)
    }
    @Test fun cancellationStopsFingerprintingAndClosesProviderStream() {
        val source = ObservedStream(ByteArrayInputStream(ByteArray(96 * 1024)))
        val cancelled = IllegalStateException("synthetic cancellation")
        var checkpoints = 0
        val failure = runCatching {
            ReadOnlyBackupScreening.fingerprint(source, checkActive = {
                if (++checkpoints == 4) throw cancelled
            })
        }.exceptionOrNull()
        assertSame(cancelled, failure)
        assertTrue("Provider stream must close on cancellation", source.closed)
        assertEquals(4, checkpoints)
    }

}
