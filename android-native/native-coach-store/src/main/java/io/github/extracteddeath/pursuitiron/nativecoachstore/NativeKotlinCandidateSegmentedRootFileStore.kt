package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
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
 * ISOLATED candidate copy-on-write root. It never enrolls the actual athlete
 * in the native application, issues coaching, or replaces the v2 history
 * ledger. Candidate publication is deliberately INTERNAL until a production
 * single-writer / journal authority can replay every receipt at the commit
 * boundary.
 *
 * The only allowed bootstrap is from an authenticated FULL replay-reviewed v2
 * ledger. The candidate has an independent file namespace; crash between
 * writing an immutable shard and replacing the root leaves an orphan, not
 * fabricated history. WAL remains intact after publication for investigation.
 * There is NO automatic reset, WAL deletion, or source authority transfer.
 *
 * Limitation: this initial candidate scans all descriptors for duplicate
 * workout IDs and root integrity. Production needs a durable unique-ID index,
 * directory durability, power-loss tests and an explicit migration owner.
 */
data class NativeCandidateHistoryRootView(
    val head: NativeSegmentedWriteHead,
    val verifiedSegmentCount: Int,
    val lastParentDigest: String?
) {
    val canPublishHistory: Boolean get() = false
    val canIssueProgression: Boolean get() = false
    val canTransferAuthority: Boolean get() = false
}

enum class NativeCandidateHistoryRootRefusal {
    INVALID_SCOPE, NOT_AUTHORIZED, NOT_BOOTSTRAPPED, BOOTSTRAP_DENIED,
    PENDING_REDO_REQUIRED, BASE_REVISION_CONFLICT, INVALID_PATCH,
    UNVERIFIED_SOURCE, CORRUPT_ROOT, CORRUPT_SEGMENT, STORAGE_UNAVAILABLE
}

sealed interface NativeCandidateHistoryRootResult {
    data class Reviewed(val view: NativeCandidateHistoryRootView, val replayed: Boolean) :
        NativeCandidateHistoryRootResult
    data class Rejected(val reason: NativeCandidateHistoryRootRefusal) :
        NativeCandidateHistoryRootResult
}

internal class NativeKotlinCandidateSegmentedRootFileStore(
    private val directory: Path,
    private val fence: NativeKotlinApprovalRevisionFence,
    private val identity: NativeKotlinCurrentIdentity,
    private val ledger: NativeKotlinHistoryManifestFileStore,
    private val handoff: NativeKotlinSegmentedHistoryHandoffGate,
    private val redo: NativeKotlinSegmentedRedoFileStore,
    /** Internal failure-injection seam; never configured by the app. */
    private val beforeRootPublicationForTest: (() -> Unit)? = null,
    private val afterRootPublicationForTest: (() -> Unit)? = null
) {
    private data class Ref(val first: Long, val size: Int, val sha: String)
    private data class Root(
        val owner: String, val athlete: String, val revision: Long,
        val count: Long, val segments: List<Ref>,
        val parent: String?, val lastPatchHash: String?
    )
    private data class Opened(val root: Root, val digest: String)
    private val id = Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,79}$")
    private val sha = Regex("^[a-f0-9]{64}$")

    companion object {
        private const val ROOT_MAGIC = 0x50494352 // PICR
        private const val SHARD_MAGIC = 0x50494353 // PICS
        private const val VERSION = 1
        private const val LIMIT = 9_007_199_254_740_991L
        private const val PER_SEGMENT = NativeSegmentedHistoryWritePlanDomain.SEGMENT_SIZE
        // Bounded on-disk root format: ~800K descriptors, far past v2 capacity.
        // The owner will need an indexed directory for truly unlimited history.
        private const val MAX_ROOT_BYTES = 16_777_216
        private const val MAX_SHARD_BYTES = 65_536
    }

    private fun hash(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 255) }
    private fun identityHash(owner: String, athlete: String) =
        hash((owner + "\u0000" + athlete).toByteArray(Charsets.UTF_8))
    private fun rootFile(owner: String, athlete: String) =
        directory.resolve("candidate-history-" + identityHash(owner, athlete) + ".root")
    private fun shardFile(owner: String, athlete: String, digest: String) =
        directory.resolve("candidate-history-" + identityHash(owner, athlete) +
            "-" + digest + ".shard")
    private fun deny(reason: NativeCandidateHistoryRootRefusal) =
        NativeCandidateHistoryRootResult.Rejected(reason)
    private fun authorized(owner: String, athlete: String) =
        runCatching {
            identity.signedInIdentity() == NativeKotlinOwnerIdentity(owner, athlete)
        }.getOrDefault(false)
    private fun guarded(owner: String, athlete: String,
        action: () -> NativeCandidateHistoryRootResult
    ): NativeCandidateHistoryRootResult {
        if (!id.matches(owner) || !id.matches(athlete))
            return deny(NativeCandidateHistoryRootRefusal.INVALID_SCOPE)
        return runCatching {
            fence.withOwnerTransaction(owner) {
                if (!authorized(owner, athlete))
                    deny(NativeCandidateHistoryRootRefusal.NOT_AUTHORIZED)
                else {
                    Files.createDirectories(directory)
                    if (Files.isSymbolicLink(directory) ||
                        !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS))
                        throw IOException("unsafe directory")
                    action()
                }
            }
        }.getOrElse { deny(NativeCandidateHistoryRootRefusal.STORAGE_UNAVAILABLE) }
    }

    private fun DataOutputStream.entry(value: KotlinHistoryManifestEntry) {
        writeLong(value.completionOrder)
        writeUTF(value.workoutId); writeUTF(value.storedReceiptId)
        writeUTF(value.finishEventId); writeLong(value.originalJournalRevision)
        writeLong(value.savedCorrectionRevision)
    }
    private fun DataInputStream.entry() = KotlinHistoryManifestEntry(
        readLong(), readUTF(), readUTF(), readUTF(), readLong(), readLong())

    private fun wrap(body: ByteArray, max: Int): ByteArray {
        if (body.size > max - 36) throw IOException("oversized object")
        val out = ByteArrayOutputStream()
        DataOutputStream(out).use { it.writeInt(body.size); it.write(body)
            it.write(MessageDigest.getInstance("SHA-256").digest(body)) }
        return out.toByteArray()
    }
    private fun unwrap(bytes: ByteArray, max: Int): DataInputStream {
        if (bytes.size !in 40..max) throw IOException("invalid envelope")
        val d = DataInputStream(ByteArrayInputStream(bytes))
        val count = d.readInt()
        if (count < 4 || count + 36 != bytes.size)
            throw IOException("invalid envelope length")
        val body = ByteArray(count)
        d.readFully(body)
        val check = ByteArray(32)
        d.readFully(check)
        if (!MessageDigest.isEqual(
                MessageDigest.getInstance("SHA-256").digest(body), check))
            throw IOException("checksum mismatch")
        return DataInputStream(ByteArrayInputStream(body))
    }
    private fun readFile(path: Path, max: Int): ByteArray {
        if (Files.isSymbolicLink(path) ||
            !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) ||
            Files.size(path) > max.toLong())
            throw IOException("unsafe candidate file")
        return Files.readAllBytes(path).also {
            if (it.size > max) throw IOException("oversize candidate file")
        }
    }
    private fun validEntry(e: KotlinHistoryManifestEntry) =
        e.completionOrder in 1..LIMIT &&
            e.originalJournalRevision in 1..LIMIT &&
            e.savedCorrectionRevision in 0..LIMIT &&
            id.matches(e.workoutId) && id.matches(e.storedReceiptId) &&
            id.matches(e.finishEventId)

    private fun encodeShard(owner: String, athlete: String, first: Long,
        entries: List<KotlinHistoryManifestEntry>
    ): ByteArray {
        if (entries.size !in 1..PER_SEGMENT ||
            entries.withIndex().any { (i, e) ->
                !validEntry(e) || e.completionOrder != first + i
            }) throw IOException("invalid shard")
        val out = ByteArrayOutputStream()
        DataOutputStream(out).use { d ->
            d.writeInt(SHARD_MAGIC); d.writeInt(VERSION)
            d.writeUTF(owner); d.writeUTF(athlete)
            d.writeLong(first); d.writeInt(entries.size)
            entries.forEach { d.entry(it) }
        }
        return wrap(out.toByteArray(), MAX_SHARD_BYTES)
    }
    private fun decodeShard(owner: String, athlete: String, ref: Ref):
        List<KotlinHistoryManifestEntry> {
        val bytes = readFile(shardFile(owner, athlete, ref.sha), MAX_SHARD_BYTES)
        if (hash(bytes) != ref.sha) throw IOException("shard identity")
        val d = unwrap(bytes, MAX_SHARD_BYTES)
        if (d.readInt() != SHARD_MAGIC || d.readInt() != VERSION ||
            d.readUTF() != owner || d.readUTF() != athlete ||
            d.readLong() != ref.first || d.readInt() != ref.size)
            throw IOException("wrong shard")
        val entries = (0 until ref.size).map { d.entry() }
        if (d.available() != 0 || entries.withIndex().any { (i,e) ->
                !validEntry(e) || e.completionOrder != ref.first + i
            }) throw IOException("invalid shard chronology")
        return entries
    }

    private fun encodeRoot(root: Root): ByteArray {
        val out = ByteArrayOutputStream()
        DataOutputStream(out).use { d ->
            d.writeInt(ROOT_MAGIC); d.writeInt(VERSION)
            d.writeUTF(root.owner); d.writeUTF(root.athlete)
            d.writeLong(root.revision); d.writeLong(root.count)
            d.writeBoolean(root.parent != null)
            if (root.parent != null) d.writeUTF(root.parent)
            d.writeBoolean(root.lastPatchHash != null)
            if (root.lastPatchHash != null) d.writeUTF(root.lastPatchHash)
            d.writeInt(root.segments.size)
            root.segments.forEach {
                d.writeLong(it.first); d.writeInt(it.size); d.writeUTF(it.sha)
            }
        }
        return wrap(out.toByteArray(), MAX_ROOT_BYTES)
    }
    private fun readRoot(owner: String, athlete: String): Opened? {
        val path = rootFile(owner, athlete)
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return null
        val bytes = readFile(path, MAX_ROOT_BYTES)
        val d = unwrap(bytes, MAX_ROOT_BYTES)
        if (d.readInt() != ROOT_MAGIC || d.readInt() != VERSION)
            throw IOException("unknown root version")
        val recordedOwner = d.readUTF(); val recordedAthlete = d.readUTF()
        val revision = d.readLong(); val count = d.readLong()
        val parent = if (d.readBoolean()) d.readUTF() else null
        val patch = if (d.readBoolean()) d.readUTF() else null
        val number = d.readInt()
        if (recordedOwner != owner || recordedAthlete != athlete ||
            revision !in 1..LIMIT || count !in 0 until LIMIT ||
            (parent != null && !sha.matches(parent)) ||
            (patch != null && !sha.matches(patch)) ||
            (parent == null) != (patch == null) ||
            number < 0 || number > MAX_ROOT_BYTES / 70 ||
            number.toLong() != (count + PER_SEGMENT - 1) / PER_SEGMENT)
            throw IOException("invalid root metadata")
        val refs = (0 until number).map { i ->
            Ref(d.readLong(), d.readInt(), d.readUTF()).also {
                val expected = minOf(PER_SEGMENT.toLong(),
                    count - i.toLong() * PER_SEGMENT).toInt()
                if (it.first != i.toLong() * PER_SEGMENT + 1 ||
                    it.size != expected || !sha.matches(it.sha))
                    throw IOException("invalid root reference")
            }
        }
        if (d.available() != 0) throw IOException("root trailing bytes")
        return Opened(Root(owner, athlete, revision, count, refs, parent, patch),
            hash(bytes))
    }

    private fun atomicWrite(path: Path, bytes: ByteArray) {
        val temp = Files.createTempFile(directory, "candidate-history-", ".pending")
        try {
            FileChannel.open(temp, StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING).use { channel ->
                val buffer = ByteBuffer.wrap(bytes)
                while (buffer.hasRemaining()) channel.write(buffer)
                channel.force(true)
            }
            Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING)
        } finally { Files.deleteIfExists(temp) }
    }
    private fun publishShard(owner: String, athlete: String, first: Long,
        entries: List<KotlinHistoryManifestEntry>
    ): Ref {
        val encoded = encodeShard(owner, athlete, first, entries)
        val digest = hash(encoded)
        val file = shardFile(owner, athlete, digest)
        if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
            if (!readFile(file, MAX_SHARD_BYTES).contentEquals(encoded))
                throw IOException("immutable shard collision")
        } else atomicWrite(file, encoded)
        return Ref(first, entries.size, digest)
    }
    private fun view(opened: Opened) = NativeCandidateHistoryRootView(
        NativeSegmentedWriteHead(opened.root.owner, opened.root.athlete,
            opened.root.revision, opened.root.count, opened.digest),
        opened.root.segments.size, opened.root.parent)

    /** Reads and verifies every referenced shard: a complete candidate audit. */
    fun audit(owner: String, athlete: String): NativeCandidateHistoryRootResult =
        guarded(owner, athlete) {
            val opened = try { readRoot(owner, athlete) } catch (_: Exception) {
                return@guarded deny(NativeCandidateHistoryRootRefusal.CORRUPT_ROOT)
            } ?: return@guarded deny(NativeCandidateHistoryRootRefusal.NOT_BOOTSTRAPPED)
            try {
                val ids = HashSet<String>()
                val receipts = HashSet<String>()
                for (ref in opened.root.segments)
                    for (entry in decodeShard(owner, athlete, ref))
                        if (!ids.add(entry.workoutId) ||
                            !receipts.add(entry.storedReceiptId))
                            throw IOException("duplicated completion")
            } catch (_: Exception) {
                return@guarded deny(NativeCandidateHistoryRootRefusal.CORRUPT_SEGMENT)
            }
            NativeCandidateHistoryRootResult.Reviewed(view(opened), true)
        }

    /**
     * Read-only, fail-closed migration parity preflight. A valid candidate
     * checksum by itself cannot prove that every workout is enrolled or that
     * the still-authoritative v2 history has not moved. Replay the complete
     * mixed workout history and its shadow under the owner fence, then compare
     * EVERY candidate shard row and the head revision to that source.
     *
     * A staged redo is acceptable only when the exact patch was already
     * committed to the candidate root. Pending/incomplete redo blocks review.
     * A positive result is evidence for a future cutover decision, NOT a
     * write-authority switch, progression permission, or UI history source.
     */
    fun reviewAuthenticatedV2Parity(
        owner: String, athlete: String
    ): NativeCandidateHistoryRootResult = guarded(owner, athlete) {
        val sourceReview = (handoff.review(owner, athlete)
            as? NativeSegmentedHistoryHandoffResult.Review)?.review
            ?: return@guarded deny(NativeCandidateHistoryRootRefusal.UNVERIFIED_SOURCE)
        val source = (ledger.open(owner, athlete)
            as? NativeHistoryLedgerResult.Durable)?.ledger
            ?: return@guarded deny(NativeCandidateHistoryRootRefusal.UNVERIFIED_SOURCE)
        if (!source.readyForHistoryReads || source.pending != null ||
            source.ownerKey != owner || source.athleteId != athlete ||
            source.revision != sourceReview.manifestRevision ||
            source.completions.size != sourceReview.completionCount)
            return@guarded deny(NativeCandidateHistoryRootRefusal.UNVERIFIED_SOURCE)

        val opened = try { readRoot(owner, athlete) } catch (_: Exception) {
            return@guarded deny(NativeCandidateHistoryRootRefusal.CORRUPT_ROOT)
        } ?: return@guarded deny(NativeCandidateHistoryRootRefusal.NOT_BOOTSTRAPPED)
        if (opened.root.revision != source.revision ||
            opened.root.count != source.completions.size.toLong())
            return@guarded deny(NativeCandidateHistoryRootRefusal.UNVERIFIED_SOURCE)

        // WAL is intentionally preserved after publication. Distinguish an
        // acknowledged, exactly matching root from an uncommitted patch.
        when (val pending = redo.read(owner, athlete)) {
            is NativeSegmentedRedoResult.Rejected ->
                if (pending.reason != NativeSegmentedRedoRefusal.NOT_PREPARED)
                    return@guarded deny(
                        NativeCandidateHistoryRootRefusal.PENDING_REDO_REQUIRED)
            is NativeSegmentedRedoResult.Durable -> {
                val patch = pending.redo.stagedPatch
                    ?: return@guarded deny(
                        NativeCandidateHistoryRootRefusal.PENDING_REDO_REQUIRED)
                if (opened.root.parent != pending.redo.base.rootDigest ||
                    opened.root.lastPatchHash != patchDigest(patch) ||
                    opened.root.revision != patch.nextHeadRevision ||
                    opened.root.count != patch.nextCompletionCount)
                    return@guarded deny(
                        NativeCandidateHistoryRootRefusal.PENDING_REDO_REQUIRED)
            }
        }

        val entries = try {
            val ids = HashSet<String>()
            val receipts = HashSet<String>()
            opened.root.segments.flatMap { ref ->
                decodeShard(owner, athlete, ref).also { rows ->
                    for (entry in rows)
                        if (!ids.add(entry.workoutId) ||
                            !receipts.add(entry.storedReceiptId))
                            throw IOException("duplicated history")
                }
            }
        } catch (_: Exception) {
            return@guarded deny(NativeCandidateHistoryRootRefusal.CORRUPT_SEGMENT)
        }
        if (entries != source.completions)
            return@guarded deny(NativeCandidateHistoryRootRefusal.UNVERIFIED_SOURCE)
        NativeCandidateHistoryRootResult.Reviewed(view(opened), true)
    }

    /**
     * One-time candidate bootstrap, only from COMPLETE authenticated v2
     * journal replay + shadow parity. Never creates a new empty athlete by
     * looking at a UI list or by absence of a candidate root.
     */
    fun bootstrapFromVerifiedV2(owner: String, athlete: String):
        NativeCandidateHistoryRootResult = guarded(owner, athlete) {
        val review = handoff.review(owner, athlete)
        if (review !is NativeSegmentedHistoryHandoffResult.Review)
            return@guarded deny(NativeCandidateHistoryRootRefusal.BOOTSTRAP_DENIED)
        val verified = (ledger.open(owner, athlete)
            as? NativeHistoryLedgerResult.Durable)?.ledger
            ?: return@guarded deny(NativeCandidateHistoryRootRefusal.BOOTSTRAP_DENIED)
        if (!verified.readyForHistoryReads ||
            verified.revision != review.review.manifestRevision ||
            verified.completions.size != review.review.completionCount)
            return@guarded deny(NativeCandidateHistoryRootRefusal.BOOTSTRAP_DENIED)
        val existing = try { readRoot(owner, athlete) } catch (_: Exception) {
            return@guarded deny(NativeCandidateHistoryRootRefusal.CORRUPT_ROOT)
        }
        if (existing != null)
            return@guarded deny(NativeCandidateHistoryRootRefusal.BASE_REVISION_CONFLICT)
        // An abandoned candidate WAL requires explicit investigation, not
        // a silent new root that could forget its pending transaction.
        if (redo.read(owner, athlete) !is NativeSegmentedRedoResult.Rejected ||
            (redo.read(owner, athlete) as NativeSegmentedRedoResult.Rejected).reason !=
                NativeSegmentedRedoRefusal.NOT_PREPARED)
            return@guarded deny(NativeCandidateHistoryRootRefusal.PENDING_REDO_REQUIRED)

        val refs = verified.completions.chunked(PER_SEGMENT).mapIndexed { i, items ->
            publishShard(owner, athlete, i.toLong() * PER_SEGMENT + 1, items)
        }
        val root = Root(owner, athlete, verified.revision,
            verified.completions.size.toLong(), refs, null, null)
        atomicWrite(rootFile(owner, athlete), encodeRoot(root))
        val opened = readRoot(owner, athlete)
            ?: throw IOException("new root missing")
        NativeCandidateHistoryRootResult.Reviewed(view(opened), false)
    }

    /**
     * Candidate root CAS/acknowledgment identity MUST bind the exact redo
     * operation, not merely its event ID. Otherwise a different correction
     * with the SAME event ID, root/segment and manifest correction revision
     * could be misreported as an already committed action after a crash.
     *
     * This is a SHA-256 equality digest, NOT an owner signature or durable
     * history authority. The v2 replay + independent receipt verifier remain
     * mandatory. Legacy candidate roots using the shorter digest fail closed.
     */
    internal fun patchDigest(patch: NativeSegmentedWritePatch): String {
        val out = ByteArrayOutputStream()
        DataOutputStream(out).use { d ->
            d.writeInt(2) // versioned fingerprint schema
            d.writeUTF(patch.priorRootDigest)
            d.writeLong(patch.expectedHeadRevision)
            d.writeLong(patch.nextHeadRevision)
            d.writeLong(patch.nextCompletionCount)
            d.writeLong(patch.replaceSegmentFirstOrdinal)
            d.writeInt(patch.completions.size)
            patch.completions.forEach { d.entry(it) }
            val intent = patch.consumedIntent
            d.writeUTF(intent.workoutId)
            when (intent) {
                is NativeHistoryWriteIntent.Finish -> {
                    d.writeByte(1)
                    d.writeUTF(intent.operation.eventId)
                    d.writeLong(intent.operation.expectedJournalRevision)
                }
                is NativeHistoryWriteIntent.CustomFinish -> {
                    d.writeByte(2)
                    d.writeUTF(intent.operation.eventId)
                    d.writeLong(intent.operation.expectedRevision)
                }
                is NativeHistoryWriteIntent.Correct -> {
                    d.writeByte(3)
                    val correction = intent.operation
                    d.writeUTF(correction.eventId)
                    d.writeLong(correction.expectedCorrectionRevision)
                    d.writeUTF(correction.setKey)
                    d.writeUTF(correction.correctsEventId)
                    d.writeUTF(correction.outcome.name)
                    d.patchPerformed(correction.actual)
                }
                is NativeHistoryWriteIntent.CustomCorrect -> {
                    d.writeByte(4)
                    val correction = intent.operation
                    d.writeUTF(correction.eventId)
                    d.writeLong(correction.expectedCorrectionRevision)
                    d.writeUTF(correction.visit.setKey)
                    d.writeUTF(correction.visit.part.name)
                    d.writeUTF(correction.correctsEventId)
                    d.writeUTF(correction.outcome.name)
                    d.patchPerformed(correction.performed)
                }
            }
        }
        return hash(out.toByteArray())
    }

    private fun DataOutputStream.patchPerformed(performed: KotlinPerformedSet?) {
        writeBoolean(performed != null)
        if (performed == null) return
        val reportedLoad = performed.load
        writeBoolean(reportedLoad != null)
        if (reportedLoad != null) writeUTF(reportedLoad.toPlainString())
        val reportedUnit = performed.unit
        writeBoolean(reportedUnit != null)
        if (reportedUnit != null) writeUTF(reportedUnit.name)
        writeInt(performed.reps)
        val reportedRir = performed.actualRir
        writeBoolean(reportedRir != null)
        if (reportedRir != null) writeInt(reportedRir)
    }

    /**
     * STRONGER read-only v2 mirror path: the currently authoritative ledger
     * must already have finalized the exact Finish/correction, and the full
     * saved Composer/custom journal history must independently replay. The
     * *entire* projected candidate root must equal that source, not merely
     * one changed row. Everything stays under the same owner fence.
     *
     * This cannot be used after cutover, because v2 is still the source of
     * truth here. Does not authenticate a future single-writer journal,
     * transfer ownership, or make candidate history visible to Compose.
     */
    internal fun publishReplayVerifiedV2Mirror(
        owner: String, athlete: String,
        replayReader: NativeKotlinMixedHistoryReadCoordinator,
        receiptVerifier: NativeKotlinIndependentHistoryReceiptVerifier
    ): NativeCandidateHistoryRootResult = guarded(owner, athlete) {
        val staged = (redo.read(owner, athlete)
            as? NativeSegmentedRedoResult.Durable)?.redo
            ?: return@guarded deny(NativeCandidateHistoryRootRefusal.PENDING_REDO_REQUIRED)
        val patch = staged.stagedPatch
            ?: return@guarded deny(NativeCandidateHistoryRootRefusal.PENDING_REDO_REQUIRED)
        val source = (ledger.open(owner, athlete)
            as? NativeHistoryLedgerResult.Durable)?.ledger
            ?: return@guarded deny(NativeCandidateHistoryRootRefusal.UNVERIFIED_SOURCE)
        if (!source.readyForHistoryReads || source.pending != null ||
            source.ownerKey != owner || source.athleteId != athlete ||
            source.revision != patch.nextHeadRevision ||
            source.completions.size.toLong() != patch.nextCompletionCount)
            return@guarded deny(NativeCandidateHistoryRootRefusal.UNVERIFIED_SOURCE)

        val replay = (replayReader.read(owner, athlete)
            as? NativeKotlinMixedHistoryReadResult.Review)?.timeline
            ?: return@guarded deny(NativeCandidateHistoryRootRefusal.UNVERIFIED_SOURCE)
        if (replay.athleteId != athlete ||
            replay.manifestRevision != source.revision ||
            replay.completions.map { it.manifest } != source.completions)
            return@guarded deny(NativeCandidateHistoryRootRefusal.UNVERIFIED_SOURCE)

        val current = try { readRoot(owner, athlete) } catch (_: Exception) {
            return@guarded deny(NativeCandidateHistoryRootRefusal.CORRUPT_ROOT)
        } ?: return@guarded deny(NativeCandidateHistoryRootRefusal.NOT_BOOTSTRAPPED)
        val sameCommittedPatch = current.root.revision == patch.nextHeadRevision &&
            current.root.parent == staged.base.rootDigest &&
            current.root.lastPatchHash == patchDigest(patch) &&
            current.root.count == patch.nextCompletionCount
        if (!sameCommittedPatch &&
            (current.digest != staged.base.rootDigest ||
             current.root.revision != staged.base.revision ||
             current.root.count != staged.base.completionCount))
            return@guarded deny(NativeCandidateHistoryRootRefusal.BASE_REVISION_CONFLICT)

        // Verify existing immutable material, including unchanged old shards.
        // The source replay already proved all receipts, and the projected
        // manifest must match all of those exact entries before any root swap.
        val entries = try {
            current.root.segments.flatMap { ref ->
                decodeShard(owner, athlete, ref)
            }
        } catch (_: Exception) {
            return@guarded deny(NativeCandidateHistoryRootRefusal.CORRUPT_SEGMENT)
        }
        val projected = if (sameCommittedPatch) entries else {
            val firstIndex = patch.replaceSegmentFirstOrdinal - 1
            if (firstIndex < 0 || firstIndex > entries.size.toLong())
                return@guarded deny(NativeCandidateHistoryRootRefusal.INVALID_PATCH)
            val first = firstIndex.toInt()
            val oldSize = if (first == entries.size) 0 else
                minOf(PER_SEGMENT, entries.size - first)
            if (first != entries.size && first % PER_SEGMENT != 0)
                return@guarded deny(NativeCandidateHistoryRootRefusal.INVALID_PATCH)
            entries.take(first) + patch.completions + entries.drop(first + oldSize)
        }
        if (projected != source.completions)
            return@guarded deny(NativeCandidateHistoryRootRefusal.UNVERIFIED_SOURCE)
        val receipt = source.completions.singleOrNull {
            it.workoutId == staged.intent.workoutId
        } ?: return@guarded deny(NativeCandidateHistoryRootRefusal.UNVERIFIED_SOURCE)
        if (receiptVerifier.verify(owner, athlete, staged.intent, receipt)
            !is NativeIndependentReceiptResult.Verified)
            return@guarded deny(NativeCandidateHistoryRootRefusal.UNVERIFIED_SOURCE)
        // The same lock also protects the second, disk-backed root/redo CAS.
        // A lost publication ack is recognized by its exact parent/patch hash.
        publishStagedForCandidateReview(owner, athlete)
    }

    /**
     * Candidate-only ROOT publication of an already persisted WAL. This is
     * not original receipt verification. A production caller cannot use it;
     * no app-facing host references this internal class.
     */
    internal fun publishStagedForCandidateReview(owner: String, athlete: String):
        NativeCandidateHistoryRootResult = guarded(owner, athlete) {
        val record = (redo.read(owner, athlete)
            as? NativeSegmentedRedoResult.Durable)?.redo
            ?: return@guarded deny(NativeCandidateHistoryRootRefusal.PENDING_REDO_REQUIRED)
        val patch = record.stagedPatch
            ?: return@guarded deny(NativeCandidateHistoryRootRefusal.PENDING_REDO_REQUIRED)
        val opened = try { readRoot(owner, athlete) } catch (_: Exception) {
            return@guarded deny(NativeCandidateHistoryRootRefusal.CORRUPT_ROOT)
        } ?: return@guarded deny(NativeCandidateHistoryRootRefusal.NOT_BOOTSTRAPPED)
        val root = opened.root
        val patchHash = patchDigest(patch)
        // Re-running after root publication is idempotent; never overwrite
        // a newer unrelated revision, even if the WAL is still present.
        if (root.revision == patch.nextHeadRevision &&
            root.parent == record.base.rootDigest &&
            root.lastPatchHash == patchHash &&
            root.count == patch.nextCompletionCount)
            return@guarded audit(owner, athlete)
        if (record.base.ownerKey != owner || record.base.athleteId != athlete ||
            opened.digest != record.base.rootDigest ||
            root.revision != record.base.revision ||
            root.count != record.base.completionCount ||
            patch.priorRootDigest != opened.digest)
            return@guarded deny(NativeCandidateHistoryRootRefusal.BASE_REVISION_CONFLICT)
        val index = ((patch.replaceSegmentFirstOrdinal - 1) / PER_SEGMENT).toInt()
        if (index < 0 || index > root.segments.size)
            return@guarded deny(NativeCandidateHistoryRootRefusal.INVALID_PATCH)
        val sourceReceipts = HashSet<String>()
        val original = try {
            // Recheck ALL existing shards; do not assume caller-provided
            // null proves a new workout ID or a unique correction target.
            val workouts = HashSet<String>()
            val receipts = sourceReceipts
            val shards = root.segments.map { ref ->
                decodeShard(owner, athlete, ref).also { entries ->
                    for (e in entries)
                        if (!workouts.add(e.workoutId) ||
                            !receipts.add(e.storedReceiptId))
                            throw IOException("duplicate original receipt")
                }
            }
            val target = record.target
            val sameId = shards.flatten().filter {
                it.workoutId == record.intent.workoutId
            }
            if (target == null && sameId.isNotEmpty() ||
                target != null && (sameId.size != 1 || sameId.single() != target))
                throw IOException("nonunique original target")
            if (index == root.segments.size) emptyList()
            else shards[index]
        } catch (_: Exception) {
            return@guarded deny(NativeCandidateHistoryRootRefusal.CORRUPT_SEGMENT)
        }
        // Re-run the pure reducer over the ACTUAL disk shard. This binds
        // every proposed row to the canonical root and target. Journal proof
        // still belongs to the future single writer.
        val resolved = if (record.target == null) patch.completions.lastOrNull()
        else patch.completions.firstOrNull {
            it.workoutId == record.target.workoutId
        } ?: return@guarded deny(NativeCandidateHistoryRootRefusal.INVALID_PATCH)
        if (resolved == null) return@guarded deny(
            NativeCandidateHistoryRootRefusal.INVALID_PATCH)
        if (record.target == null &&
            resolved.storedReceiptId in sourceReceipts)
            return@guarded deny(NativeCandidateHistoryRootRefusal.INVALID_PATCH)
        val plan = NativeSegmentedHistoryWritePlanDomain.finalize(
            record.base.copy(pending = record.intent), record.intent, record.target,
            NativeSegmentedWriteSegment(opened.digest,
                patch.replaceSegmentFirstOrdinal, original), resolved)
        if ((plan as? NativeSegmentedWriteDecision.ProposedPatch)?.patch != patch)
            return@guarded deny(NativeCandidateHistoryRootRefusal.INVALID_PATCH)

        return@guarded try {
            val replacement = publishShard(owner, athlete,
                patch.replaceSegmentFirstOrdinal, patch.completions)
            val nextRefs = root.segments.toMutableList()
            if (index == nextRefs.size) nextRefs.add(replacement)
            else nextRefs[index] = replacement
            val updated = root.copy(revision = patch.nextHeadRevision,
                count = patch.nextCompletionCount, segments = nextRefs,
                parent = opened.digest, lastPatchHash = patchHash)
            // The root is the only commit point; orphaned pre-commit shard
            // is harmless and remains outside current history.
            beforeRootPublicationForTest?.invoke()
            atomicWrite(rootFile(owner, athlete), encodeRoot(updated))
            afterRootPublicationForTest?.invoke()
            // audit() reports a verified read (replayed=true). This path
            // actually performed a NEW root publication; never mislabel
            // that first commit as an acknowledged retry.
            when (val published = audit(owner, athlete)) {
                is NativeCandidateHistoryRootResult.Reviewed ->
                    published.copy(replayed = false)
                else -> published
            }
        } catch (_: Exception) {
            deny(NativeCandidateHistoryRootRefusal.STORAGE_UNAVAILABLE)
        }
    }
}
