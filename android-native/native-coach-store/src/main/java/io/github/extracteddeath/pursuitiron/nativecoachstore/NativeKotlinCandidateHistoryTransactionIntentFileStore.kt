package io.github.extracteddeath.pursuitiron.nativecoachstore

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest

/**
 * INACTIVE transaction-intent marker tying ONE exact durable segmented redo
 * to a subsequent replay-verified root, BOTH identity trees and checkpoint.
 *
 * This is a candidate rehearsal, NOT the source history WAL or a new writer.
 * Prepared state NEVER authorizes history reads or any training decision.
 * Acknowledgment requires a finalized, replay-authenticated v2 history and
 * exactly matching candidate root/index/checkpoint + original redo patch.
 * There is deliberately no delete, force-reset, or authority-transfer API.
 * The original v2 source remains the only history writer.
 */
internal data class NativeCandidateHistoryTransactionIntent(
    val ownerKey: String,
    val athleteId: String,
    val baseRootDigest: String,
    val baseRevision: Long,
    val nextRevision: Long,
    val nextCompletionCount: Long,
    val operationPatchDigest: String,
    val acknowledgedRootDigest: String?,
    val acknowledgedDirectoryDigest: String?
) {
    val acknowledged: Boolean
        get() = acknowledgedRootDigest != null &&
            acknowledgedDirectoryDigest != null
    val canAuthorizeHistoryReads: Boolean get() = false
    val canAuthorizeWrites: Boolean get() = false
    val canIssueProgression: Boolean get() = false
    val canTransferAuthority: Boolean get() = false
}

internal enum class NativeCandidateTransactionIntentRefusal {
    INVALID_SCOPE, NOT_AUTHORIZED, NOT_STAGED, NOT_PREPARED,
    PENDING_TRANSACTION, CONFLICTING_REDO, CORRUPT_INTENT,
    UNVERIFIED_COMMIT, STORAGE_UNAVAILABLE
}
internal sealed interface NativeCandidateTransactionIntentResult {
    data class Durable(
        val intent: NativeCandidateHistoryTransactionIntent,
        val replayed: Boolean
    ) : NativeCandidateTransactionIntentResult
    data class Rejected(val reason: NativeCandidateTransactionIntentRefusal) :
        NativeCandidateTransactionIntentResult
}

internal class NativeKotlinCandidateHistoryTransactionIntentFileStore(
    private val directory: Path,
    private val fence: NativeKotlinApprovalRevisionFence,
    private val identity: NativeKotlinCurrentIdentity,
    private val redo: NativeKotlinSegmentedRedoFileStore,
    private val root: NativeKotlinCandidateSegmentedRootFileStore,
    private val paged: NativeKotlinCandidatePagedIdentityFileStore,
    private val checkpoint: NativeKotlinCandidatePairedHistoryCheckpointFileStore,
    private val beforeAcknowledgmentForTest: (() -> Unit)? = null,
    private val afterAcknowledgmentForTest: (() -> Unit)? = null
) {
    private val id = Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,79}$")
    private val digest = Regex("^[a-f0-9]{64}$")
    private companion object {
        const val MAGIC = 0x50495449 // PITI (candidate only)
        const val VERSION = 1
        const val MAX_BYTES = 2048
        const val MAX_REVISION = 9_007_199_254_740_991L
    }
    private fun refuse(r: NativeCandidateTransactionIntentRefusal) =
        NativeCandidateTransactionIntentResult.Rejected(r)
    private fun hash(bytes: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(bytes)
    private fun hex(bytes: ByteArray) = bytes.joinToString("") {
        "%02x".format(it.toInt() and 255)
    }
    private fun intentFile(owner: String, athlete: String) =
        directory.resolve("candidate-history-txn-" +
            hex(hash((owner + "\u0000" + athlete).toByteArray(Charsets.UTF_8))) +
            ".txintent")

    private fun guarded(
        owner: String, athlete: String,
        work: () -> NativeCandidateTransactionIntentResult
    ): NativeCandidateTransactionIntentResult {
        if (!id.matches(owner) || !id.matches(athlete))
            return refuse(NativeCandidateTransactionIntentRefusal.INVALID_SCOPE)
        return runCatching {
            fence.withOwnerTransaction(owner) {
                if (identity.signedInIdentity() !=
                    NativeKotlinOwnerIdentity(owner, athlete))
                    refuse(NativeCandidateTransactionIntentRefusal.NOT_AUTHORIZED)
                else {
                    Files.createDirectories(directory)
                    if (Files.isSymbolicLink(directory) ||
                        !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS))
                        throw IOException("unsafe transaction intent directory")
                    work()
                }
            }
        }.getOrElse {
            refuse(NativeCandidateTransactionIntentRefusal.STORAGE_UNAVAILABLE)
        }
    }

    private fun validate(v: NativeCandidateHistoryTransactionIntent) {
        if (!id.matches(v.ownerKey) || !id.matches(v.athleteId) ||
            !digest.matches(v.baseRootDigest) ||
            !digest.matches(v.operationPatchDigest) ||
            v.baseRevision !in 1 until MAX_REVISION ||
            v.nextRevision != v.baseRevision + 1 ||
            v.nextCompletionCount !in 0 until MAX_REVISION ||
            (v.acknowledgedRootDigest == null) !=
                (v.acknowledgedDirectoryDigest == null) ||
            v.acknowledgedRootDigest?.let { !digest.matches(it) } == true ||
            v.acknowledgedDirectoryDigest?.let { !digest.matches(it) } == true)
            throw IOException("invalid candidate transaction intent")
    }

    private fun encode(v: NativeCandidateHistoryTransactionIntent): ByteArray {
        validate(v)
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { d ->
            d.writeInt(MAGIC); d.writeInt(VERSION)
            d.writeUTF(v.ownerKey); d.writeUTF(v.athleteId)
            d.writeUTF(v.baseRootDigest)
            d.writeLong(v.baseRevision); d.writeLong(v.nextRevision)
            d.writeLong(v.nextCompletionCount); d.writeUTF(v.operationPatchDigest)
            d.writeBoolean(v.acknowledged)
            if (v.acknowledged) {
                d.writeUTF(checkNotNull(v.acknowledgedRootDigest))
                d.writeUTF(checkNotNull(v.acknowledgedDirectoryDigest))
            }
        }
        val bytes = output.toByteArray()
        if (bytes.size > MAX_BYTES - 36)
            throw IOException("transaction intent too large")
        val wrapped = ByteArrayOutputStream()
        DataOutputStream(wrapped).use {
            it.writeInt(bytes.size); it.write(bytes); it.write(hash(bytes))
        }
        return wrapped.toByteArray()
    }

    private fun decode(
        bytes: ByteArray, owner: String, athlete: String
    ): NativeCandidateHistoryTransactionIntent {
        if (bytes.size !in 40..MAX_BYTES)
            throw IOException("invalid intent envelope")
        val frame = DataInputStream(ByteArrayInputStream(bytes))
        val size = frame.readInt()
        if (size < 8 || size + 36 != bytes.size)
            throw IOException("invalid intent framing")
        val body = ByteArray(size)
        frame.readFully(body)
        val checksum = ByteArray(32)
        frame.readFully(checksum)
        if (!MessageDigest.isEqual(hash(body), checksum))
            throw IOException("intent integrity mismatch")
        val d = DataInputStream(ByteArrayInputStream(body))
        if (d.readInt() != MAGIC || d.readInt() != VERSION)
            throw IOException("unknown intent schema")
        val savedOwner = d.readUTF()
        val savedAthlete = d.readUTF()
        val baseRoot = d.readUTF()
        val baseRevision = d.readLong()
        val nextRevision = d.readLong()
        val count = d.readLong()
        val patchDigest = d.readUTF()
        val acknowledged = d.readBoolean()
        val saved = NativeCandidateHistoryTransactionIntent(
            savedOwner, savedAthlete, baseRoot, baseRevision, nextRevision,
            count, patchDigest,
            if (acknowledged) d.readUTF() else null,
            if (acknowledged) d.readUTF() else null
        )
        validate(saved)
        if (d.available() != 0 ||
            saved.ownerKey != owner || saved.athleteId != athlete)
            throw IOException("transaction scope or trailing bytes")
        return saved
    }

    private fun read(
        owner: String, athlete: String
    ): NativeCandidateHistoryTransactionIntent? {
        val file = intentFile(owner, athlete)
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) return null
        if (Files.isSymbolicLink(file) ||
            !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) ||
            Files.size(file) !in 40L..MAX_BYTES.toLong())
            throw IOException("unsafe transaction intent")
        return decode(Files.readAllBytes(file), owner, athlete)
    }

    private fun atomicWrite(
        owner: String, athlete: String,
        saved: NativeCandidateHistoryTransactionIntent
    ) {
        val tmp = Files.createTempFile(directory, "candidate-history-txn-", ".pending")
        try {
            FileChannel.open(tmp, StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING).use { ch ->
                val buffer = ByteBuffer.wrap(encode(saved))
                while (buffer.hasRemaining()) ch.write(buffer)
                ch.force(true)
            }
            Files.move(tmp, intentFile(owner, athlete),
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally { Files.deleteIfExists(tmp) }
    }

    private fun staged(owner: String, athlete: String):
        NativeCandidateHistoryTransactionIntent? {
        val record = (redo.read(owner, athlete)
            as? NativeSegmentedRedoResult.Durable)?.redo ?: return null
        val patch = record.stagedPatch ?: return null
        if (record.base.ownerKey != owner || record.base.athleteId != athlete ||
            record.base.pending != null ||
            patch.priorRootDigest != record.base.rootDigest ||
            patch.expectedHeadRevision != record.base.revision ||
            patch.nextHeadRevision != record.base.revision + 1 ||
            patch.consumedIntent != record.intent ||
            patch.nextCompletionCount < record.base.completionCount ||
            patch.nextCompletionCount > record.base.completionCount + 1)
            return null
        return NativeCandidateHistoryTransactionIntent(
            owner, athlete, record.base.rootDigest, record.base.revision,
            patch.nextHeadRevision, patch.nextCompletionCount,
            root.patchDigest(patch), null, null
        )
    }

    /** Prepare only against the exact, already staged durable WAL. */
    fun prepare(owner: String, athlete: String):
        NativeCandidateTransactionIntentResult = guarded(owner, athlete) {
        val proposed = staged(owner, athlete)
            ?: return@guarded refuse(NativeCandidateTransactionIntentRefusal.NOT_STAGED)
        val existing = try { read(owner, athlete) } catch (_: Exception) {
            return@guarded refuse(NativeCandidateTransactionIntentRefusal.CORRUPT_INTENT)
        }
        if (existing != null)
            return@guarded if (existing.copy(
                    acknowledgedRootDigest = null,
                    acknowledgedDirectoryDigest = null) == proposed)
                NativeCandidateTransactionIntentResult.Durable(existing, true)
            else refuse(NativeCandidateTransactionIntentRefusal.PENDING_TRANSACTION)
        // Initial source root is not yet committed; verify the stated parent
        // physically exists as a valid candidate root without trusting WAL.
        val opened = (root.audit(owner, athlete)
            as? NativeCandidateHistoryRootResult.Reviewed)?.view
            ?: return@guarded refuse(NativeCandidateTransactionIntentRefusal.CONFLICTING_REDO)
        if (opened.head.rootDigest != proposed.baseRootDigest ||
            opened.head.revision != proposed.baseRevision)
            return@guarded refuse(NativeCandidateTransactionIntentRefusal.CONFLICTING_REDO)
        return@guarded try {
            atomicWrite(owner, athlete, proposed)
            NativeCandidateTransactionIntentResult.Durable(proposed, false)
        } catch (_: Exception) {
            refuse(NativeCandidateTransactionIntentRefusal.STORAGE_UNAVAILABLE)
        }
    }

    /**
     * Do NOT commit from prepared state. Only ack an already published exact
     * WAL patch after verified v2 source, root, identity directory and paired
     * checkpoint all attest to the SAME revision and historical identities.
     */
    fun acknowledge(owner: String, athlete: String):
        NativeCandidateTransactionIntentResult = guarded(owner, athlete) {
        val saved = try { read(owner, athlete) } catch (_: Exception) {
            return@guarded refuse(NativeCandidateTransactionIntentRefusal.CORRUPT_INTENT)
        } ?: return@guarded refuse(NativeCandidateTransactionIntentRefusal.NOT_PREPARED)
        val proposed = staged(owner, athlete)
            ?: return@guarded refuse(NativeCandidateTransactionIntentRefusal.CONFLICTING_REDO)
        if (saved.copy(acknowledgedRootDigest = null,
                acknowledgedDirectoryDigest = null) != proposed)
            return@guarded refuse(NativeCandidateTransactionIntentRefusal.CONFLICTING_REDO)
        val completed = (root.reviewAuthenticatedV2Parity(owner, athlete)
            as? NativeCandidateHistoryRootResult.Reviewed)?.view
            ?: return@guarded refuse(NativeCandidateTransactionIntentRefusal.UNVERIFIED_COMMIT)
        val indexed = (paged.audit(owner, athlete)
            as? NativeCandidatePagedIdentityResult.Indexed)?.view
            ?: return@guarded refuse(NativeCandidateTransactionIntentRefusal.UNVERIFIED_COMMIT)
        val pair = (checkpoint.review(owner, athlete)
            as? NativeCandidatePairedHistoryResult.Verified)?.checkpoint
            ?: return@guarded refuse(NativeCandidateTransactionIntentRefusal.UNVERIFIED_COMMIT)
        if (completed.lastParentDigest != saved.baseRootDigest ||
            completed.head.revision != saved.nextRevision ||
            completed.head.completionCount != saved.nextCompletionCount ||
            indexed.rootDigest != completed.head.rootDigest ||
            indexed.sourceRevision != completed.head.revision ||
            indexed.completionCount != completed.head.completionCount ||
            pair.rootDigest != completed.head.rootDigest ||
            pair.sourceRevision != completed.head.revision ||
            pair.completionCount != completed.head.completionCount)
            return@guarded refuse(NativeCandidateTransactionIntentRefusal.UNVERIFIED_COMMIT)
        val confirmed = saved.copy(
            acknowledgedRootDigest = completed.head.rootDigest,
            acknowledgedDirectoryDigest = pair.pagedDirectoryFileDigest)
        if (saved.acknowledged)
            return@guarded if (saved == confirmed)
                NativeCandidateTransactionIntentResult.Durable(saved, true)
            else refuse(NativeCandidateTransactionIntentRefusal.CONFLICTING_REDO)
        return@guarded try {
            beforeAcknowledgmentForTest?.invoke()
            atomicWrite(owner, athlete, confirmed)
            afterAcknowledgmentForTest?.invoke()
            NativeCandidateTransactionIntentResult.Durable(confirmed, false)
        } catch (_: Exception) {
            refuse(NativeCandidateTransactionIntentRefusal.STORAGE_UNAVAILABLE)
        }
    }

    /** Read stored bytes ONLY; this is not authentication or release approval. */
    fun inspect(owner: String, athlete: String):
        NativeCandidateTransactionIntentResult = guarded(owner, athlete) {
        try {
            read(owner, athlete)?.let {
                NativeCandidateTransactionIntentResult.Durable(it, true)
            } ?: refuse(NativeCandidateTransactionIntentRefusal.NOT_PREPARED)
        } catch (_: Exception) {
            refuse(NativeCandidateTransactionIntentRefusal.CORRUPT_INTENT)
        }
    }
}
