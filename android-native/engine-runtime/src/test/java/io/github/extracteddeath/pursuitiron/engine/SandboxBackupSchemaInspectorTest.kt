package io.github.extracteddeath.pursuitiron.engine

import io.github.extracteddeath.pursuitiron.domain.ReadOnlyBackupLimits
import io.github.extracteddeath.pursuitiron.domain.ReadOnlyBackupScreening
import io.github.extracteddeath.pursuitiron.domain.UnverifiedBackupFingerprint
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream

/** Source-only JVM checks; the AndroidX JS execution is tested on emulator. */
class SandboxBackupSchemaInspectorTest {
    private fun proof(source: ByteArray) =
        ReadOnlyBackupScreening.fingerprint(ByteArrayInputStream(source))

    @Test fun transfersOnlyExactSnapshotInReadOnlyRequest() {
        val source = """{"v":13,"saved":[],"history":[]}""".toByteArray()
        val unmodified = source.copyOf()
        val wire = checkedBackupSchemaWire(source,proof(source),"probe-synthetic")
        val envelope = JSONObject(wire)
        assertEquals(1,envelope.getInt("protocolVersion"))
        assertEquals("probe-synthetic",envelope.getString("id"))
        assertEquals("backupSchemaCheck",envelope.getString("method"))
        assertEquals(1,envelope.getJSONObject("params").length())
        assertEquals(String(unmodified,Charsets.UTF_8),
            envelope.getJSONObject("params").getString("rawSnapshot"))
        assertArrayEquals(unmodified,source)
        assertFalse(envelope.has("write"))
        assertFalse(envelope.has("saved"))
    }

    @Test fun rejectsEditedByteCopyBeforeSubmittingToSandbox() {
        val source = """{"v":13,"saved":[]}""".toByteArray()
        val other = """{"v":13,"saved":{}}""".toByteArray()
        assertEquals(source.size,other.size)
        assertTrue(runCatching { checkedBackupSchemaWire(
            other,proof(source),"probe-synthetic"
        ) }.isFailure)
        assertTrue(runCatching { checkedBackupSchemaWire(
            source,UnverifiedBackupFingerprint(source.size.toLong(),"0".repeat(64)),
            "probe-synthetic"
        ) }.isFailure)
        assertTrue(runCatching { checkedBackupSchemaWire(
            source,proof(source),""
        ) }.isFailure)
    }

    @Test fun cannotEncodeOversizedOrMalformedUtf8() {
        val over = ByteArray(ReadOnlyBackupLimits.MAX_SOURCE_BYTES + 1) { 'x'.code.toByte() }
        assertTrue(runCatching { checkedBackupSchemaWire(over,proof(over),"probe") }.isFailure)
        val malformed = byteArrayOf(0x7b,0xff.toByte(),0x7d)
        assertTrue(runCatching { checkedBackupSchemaWire(
            malformed,proof(malformed),"probe"
        ) }.isFailure)
    }
}
