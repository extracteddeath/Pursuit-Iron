package io.github.extracteddeath.pursuitiron.domain

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.util.concurrent.CancellationException

/**
 * FUTURE, CURRENT canonical store owner. Must be the app's authoritative
 * storage owner, never document picker, imported backup, shared prefs clone or
 * a cached transport DTO. Ownership of the returned ByteArray transfers to
 * the caller, which MUST wipe it. No implementation is shipped in the app.
 */
interface CanonicalCurrentSnapshotOwner {
    suspend fun readOwnedCurrentStoreBytes(): ByteArray?
    fun subscribeCommittedChanges(onChanged: () -> Unit): AutoCloseable
}

/**
 * Future pinned canonical JS projection service. Must parse exact v13 bytes
 * via the web schema owner and produce only the existing read-only transport.
 * Neither this interface nor a fake Kotlin parser certifies a JS sandbox.
 */
interface PinnedCanonicalPlanProjector {
    suspend fun projectCurrentSavedProgram(
        exactUtf8Snapshot: String, programId: String
    ): CanonicalCurrentPlanFrame?
}

/**
 * Authenticated-current-source PORT foundation: byte bounds, strict UTF-8,
 * SHA-256 equality to the canonical reference, and temporary byte clearing.
 * The current-store owner and JS projector remain unconnected. This class
 * never creates authority from the user's selected offline backup.
 */
class ExactCurrentSnapshotProjectionPort(
    private val owner: CanonicalCurrentSnapshotOwner,
    private val projector: PinnedCanonicalPlanProjector
) : CanonicalCurrentPlanReadPort {
    private val identifier = Regex("^[A-Za-z0-9][A-Za-z0-9_-]{0,127}$")
    private val hexDigits = "0123456789abcdef"

    override suspend fun readCurrentProjection(programId: String): CanonicalCurrentPlanFrame? {
        if (!identifier.matches(programId)) return null
        val source = owner.readOwnedCurrentStoreBytes() ?: return null
        var candidate: ByteArray? = null
        return try {
            if (source.isEmpty() || source.size > ReadOnlyBackupLimits.MAX_SOURCE_BYTES)
                return null
            // Read and hash one private, complete byte snapshot. Do not retain
            // source aliases that could mutate while the projector is running.
            candidate = source.copyOf()
            val exact = candidate
            if (exact.size >= 3 && exact[0] == 0xef.toByte() &&
                exact[1] == 0xbb.toByte() && exact[2] == 0xbf.toByte())
                return null
            val decoder = Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            val snapshotText = decoder.decode(ByteBuffer.wrap(exact)).toString()
            if (snapshotText.isEmpty() ||
                snapshotText.length > ReadOnlyBackupLimits.MAX_SOURCE_CHARACTERS)
                return null
            val digest = MessageDigest.getInstance("SHA-256").digest(exact)
            val sha = buildString(digest.size * 2) {
                digest.forEach { byte ->
                    val value = byte.toInt() and 0xff
                    append(hexDigits[value ushr 4])
                    append(hexDigits[value and 0x0f])
                }
            }
            val projected = projector.projectCurrentSavedProgram(snapshotText, programId)
                ?: return null
            if (projected.reference.programId != programId ||
                projected.reference.snapshotSha256 != sha)
                return null
            projected
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        } finally {
            candidate?.fill(0)
            source.fill(0)
        }
    }

    override fun subscribeCommittedChanges(onChanged: () -> Unit): AutoCloseable =
        owner.subscribeCommittedChanges(onChanged)
}
