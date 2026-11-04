package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Version 1 history MUST survive a transactional upgrade to version 2. */
class NativeKotlinHistoryLedgerV2Test {
    @get:Rule val temp = TemporaryFolder()
    private val root: Path get() = temp.root.toPath()
    private val owner = "owner-1"
    private val athlete = "athlete-1"
    private fun store() = NativeKotlinHistoryManifestFileStore(root)
    private fun entry(i: Int) = KotlinHistoryManifestEntry(
        i.toLong(), "workout-$i", "stored-$i", "finish-$i", 3, 0)
    private fun records(count: Int) = (1..count).map(::entry)
    private fun permit(o: String, a: String) = o == owner && a == athlete
    private fun opened() = (store().open(owner, athlete)
        as NativeHistoryLedgerResult.Durable).ledger

    private fun ledgerFile(): Path {
        val digest = MessageDigest.getInstance("SHA-256").digest(
            (owner + "\u0000" + athlete).toByteArray(Charsets.UTF_8))
        val hex = digest.joinToString("") { "%02x".format(it.toInt() and 255) }
        return root.resolve("history-$hex.bin")
    }

    /** Exact original v1 on-disk framing and entries, not the new v2 writer. */
    private fun installV1(count: Int) {
        val payload = ByteArrayOutputStream()
        DataOutputStream(payload).use { out ->
            out.writeInt(0x50494831)
            out.writeInt(1)
            out.writeUTF(owner)
            out.writeUTF(athlete)
            out.writeLong(1)
            out.writeInt(count)
            for (item in records(count)) {
                out.writeLong(item.completionOrder)
                out.writeUTF(item.workoutId)
                out.writeUTF(item.storedReceiptId)
                out.writeUTF(item.finishEventId)
                out.writeLong(item.originalJournalRevision)
                out.writeLong(item.savedCorrectionRevision)
            }
            out.writeByte(0)
        }
        val data = payload.toByteArray()
        val raw = ByteArrayOutputStream()
        DataOutputStream(raw).use { out ->
            out.writeInt(data.size)
            out.write(data)
            out.write(MessageDigest.getInstance("SHA-256").digest(data))
        }
        Files.write(ledgerFile(), raw.toByteArray())
    }

    @Test fun legacy128ReopensThenPromotesTo129WithoutReorderingOrLoss() {
        installV1(128)
        assertEquals(1, ByteBuffer.wrap(Files.readAllBytes(ledgerFile())).getInt(8))
        assertEquals(records(128), opened().completions)
        val intent = NativeHistoryWriteIntent.Finish(
            "workout-129", KotlinJournalOperation.Finish("finish-129", 0))
        assertTrue(store().prepare(owner, athlete, 1, intent)
            is NativeHistoryLedgerResult.Durable)
        // Crash after prepare: the intent and all 128 original facts remain.
        val pending = opened()
        assertEquals(intent, pending.pending)
        assertEquals(records(128), pending.completions)
        assertEquals(2, ByteBuffer.wrap(Files.readAllBytes(ledgerFile())).getInt(8))
        val resolved = entry(129).copy(originalJournalRevision = 1)
        assertTrue(store().finalize(owner, athlete, intent, resolved)
            is NativeHistoryLedgerResult.Durable)
        val recovered = opened()
        assertEquals(2L, recovered.revision)
        assertNull(recovered.pending)
        assertEquals(records(128), recovered.completions.take(128))
        assertEquals(resolved, recovered.completions.last())
        assertEquals(129, recovered.completions.size)
    }

    @Test fun extendedManifestSurvivesColdOpenAndOldCorrectionManifestRevision() {
        val original = records(512)
        val enrolled = store().enrollVerifiedSnapshot(owner, athlete,
            original, ::permit)
        assertTrue(enrolled.toString(), enrolled is NativeHistoryLedgerResult.Durable)
        assertEquals(original, opened().completions)
        val correction = KotlinSavedSetCorrection("edit-256", 0, "set-256",
            "original-256", KotlinJournalSetOutcome.COMPLETED,
            KotlinPerformedSet(java.math.BigDecimal("200"), LoadUnit.LB, 8, 2))
        val intent = NativeHistoryWriteIntent.Correct("workout-256", correction)
        assertTrue(store().prepare(owner, athlete, 1, intent)
            is NativeHistoryLedgerResult.Durable)
        val withRevision = entry(256).copy(savedCorrectionRevision = 1)
        assertTrue(store().finalize(owner, athlete, intent, withRevision)
            is NativeHistoryLedgerResult.Durable)
        val reopened = opened()
        assertEquals(512, reopened.completions.size)
        assertEquals(2L, reopened.revision)
        assertEquals(withRevision, reopened.completions[255])
        assertEquals(entry(512), reopened.completions.last())
    }

    @Test fun forgedV1CannotSmuggle129EntriesUnderCorrectChecksum() {
        installV1(129)
        assertEquals(NativeHistoryLedgerResult.Rejected(
            NativeHistoryLedgerRefusal.CORRUPT_HISTORY),
            store().open(owner, athlete))
    }

    @Test fun boundRejectsOverflowAndKeepsExistingLedgerUnmodified() {
        val all = records(KotlinHistoryCapacityPolicy.MAX_MANIFEST_COMPLETIONS)
        assertTrue(store().enrollVerifiedSnapshot(owner, athlete, all, ::permit)
            is NativeHistoryLedgerResult.Durable)
        val byteBefore = Files.readAllBytes(ledgerFile())
        assertEquals(NativeHistoryLedgerResult.Rejected(
            NativeHistoryLedgerRefusal.INVALID_MANIFEST_TRANSITION),
            store().enrollVerifiedSnapshot(owner, athlete,
                records(KotlinHistoryCapacityPolicy.MAX_MANIFEST_COMPLETIONS + 1),
                ::permit))
        assertArrayEquals(byteBefore, Files.readAllBytes(ledgerFile()))
        assertEquals(all, opened().completions)
    }

    @Test fun tamperedV2ChecksumFailsClosedWithoutSilentHistoryReset() {
        assertTrue(store().enrollVerifiedSnapshot(owner, athlete,
            records(150), ::permit) is NativeHistoryLedgerResult.Durable)
        val bytes = Files.readAllBytes(ledgerFile())
        bytes[bytes.size - 1] = (bytes.last().toInt() xor 1).toByte()
        Files.write(ledgerFile(), bytes)
        assertEquals(NativeHistoryLedgerResult.Rejected(
            NativeHistoryLedgerRefusal.CORRUPT_HISTORY),
            store().open(owner, athlete))
        assertFalse(store().enrollVerifiedSnapshot(owner, athlete,
            emptyList(), ::permit) is NativeHistoryLedgerResult.Durable)
    }
}
