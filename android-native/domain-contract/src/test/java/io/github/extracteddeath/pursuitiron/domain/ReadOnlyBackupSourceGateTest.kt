package io.github.extracteddeath.pursuitiron.domain

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream

class ReadOnlyBackupSourceGateTest {
    private fun fingerprint(raw: ByteArray) =
        ReadOnlyBackupScreening.fingerprint(ByteArrayInputStream(raw))

    @Test fun exactPreviouslyScreenedUtf8CopyIsPassedUnmodified() {
        val raw = """{"v":13,"saved":[],"label":"synthetic"}"""
        val bytes=raw.toByteArray(Charsets.UTF_8)
        val screened=fingerprint(bytes)
        assertEquals(raw,ReadOnlyBackupSourceGate.exactText(bytes,screened))
        assertEquals(raw,ReadOnlyBackupSourceGate.exactText(bytes,screened))
    }

    @Test fun exactLargerThanLegacy128KisStillBoundedAndVerifiable() {
        val raw = ByteArray(256 * 1024) { 'x'.code.toByte() }
        val proof = fingerprint(raw)
        val text = ReadOnlyBackupSourceGate.exactText(raw, proof)
        assertEquals(raw.size, text.length)
        assertTrue(runCatching { ReadOnlyBackupSourceGate.exactText(
            raw, proof.copy(sha256Hex = "0".repeat(64))
        ) }.isFailure)
    }

    @Test fun rejectsStaleEqualLengthCopyAndForgedOrMalformedIdentity() {
        val first="""{"v":13,"label":"synthetic-A"}""".toByteArray()
        val second="""{"v":13,"label":"synthetic-B"}""".toByteArray()
        val proof=fingerprint(first)
        assertEquals(first.size,second.size)
        assertTrue(runCatching { ReadOnlyBackupSourceGate.exactText(second,proof) }.isFailure)
        assertTrue(runCatching { ReadOnlyBackupSourceGate.exactText(
            first,proof.copy(byteCount=proof.byteCount+1)) }.isFailure)
        assertTrue(runCatching { ReadOnlyBackupSourceGate.exactText(
            first,proof.copy(sha256Hex=proof.sha256Hex.uppercase())) }.isFailure)
        assertTrue(runCatching { ReadOnlyBackupSourceGate.exactText(
            first,proof.copy(sha256Hex="f".repeat(64))) }.isFailure)
    }

    @Test fun rejectsMalformedUtf8AndOversizedSchemaSourceWithoutRepair() {
        val invalid = byteArrayOf(0x7b, 0x22, 0x78, 0x22, 0x3a, 0xc3.toByte(), 0x7d)
        assertTrue(runCatching {
            ReadOnlyBackupSourceGate.exactText(invalid,fingerprint(invalid))
        }.isFailure)
        val bom = byteArrayOf(0xef.toByte(),0xbb.toByte(),0xbf.toByte()) +
            """{"v":13}""".toByteArray()
        assertTrue(runCatching { ReadOnlyBackupSourceGate.exactText(bom,fingerprint(bom)) }.isFailure)
        val chars = ByteArray(ReadOnlyBackupLimits.MAX_SOURCE_CHARACTERS+1) { 'x'.code.toByte() }
        assertTrue(runCatching {
            ReadOnlyBackupSourceGate.exactText(chars,fingerprint(chars))
        }.isFailure)
        assertTrue(runCatching {
            ReadOnlyBackupSourceGate.exactText(byteArrayOf(),UnverifiedBackupFingerprint(
                0,"0".repeat(64)))
        }.isFailure)
        val large = ByteArray(ReadOnlyBackupLimits.MAX_SOURCE_BYTES+1) { 0x31 }
        assertTrue(runCatching {
            ReadOnlyBackupSourceGate.exactText(large,UnverifiedBackupFingerprint(
                large.size.toLong(),"0".repeat(64)))
        }.isFailure)
    }
}
