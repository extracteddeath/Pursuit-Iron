package io.github.extracteddeath.pursuitiron.domain

import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest

/**
 * Single-pass source intake for explicitly user-selected documents.
 *
 * Always hashes the complete source up to 50 MiB. A copy of at most 1 MiB
 * is held transiently for the small-source canonical parser only. Larger
 * sources are FINGERPRINT ONLY: never silently parse partial data.
 *
 * No filename, URI, permissions, file writes or persisted bytes.
 */
sealed interface ReadOnlyDocumentScreening {
    val fingerprint: UnverifiedBackupFingerprint

    data class FingerprintOnly(
        override val fingerprint: UnverifiedBackupFingerprint
    ) : ReadOnlyDocumentScreening

    /** Caller must clear bytes in finally; never place this in Compose state. */
    class SmallCandidate(
        override val fingerprint: UnverifiedBackupFingerprint,
        val bytes: ByteArray
    ) : ReadOnlyDocumentScreening
}

object ReadOnlyBackupCandidateReader {
    const val MAX_CANDIDATE_BYTES = ReadOnlyBackupLimits.MAX_SOURCE_BYTES
    private val hexDigits = "0123456789abcdef".toCharArray()

    fun screen(
        source: InputStream,
        maxBytes: Long = ReadOnlyBackupScreening.MAX_COPY_BYTES,
        checkActive: () -> Unit = {}
    ): ReadOnlyDocumentScreening {
        require(maxBytes in 1..ReadOnlyBackupScreening.MAX_COPY_BYTES)
        val candidate = ByteArray(MAX_CANDIDATE_BYTES)
        val chunk = ByteArray(32 * 1024)
        return try {
            source.use { stream ->
                val digest = MessageDigest.getInstance("SHA-256")
                var count = 0L
                var capturable = true
                while (true) {
                    // Cancellation is checked on either side of provider I/O.
                    // This closes the stream and wipes captured bytes promptly
                    // after the next completed read if the UI is disposed.
                    checkActive()
                    val read = stream.read(chunk)
                    checkActive()
                    if (read == -1) break
                    if (read == 0) throw IOException("Document stream made no progress")
                    if (count > maxBytes - read) throw IOException("Selected document exceeds safety limit")
                    digest.update(chunk, 0, read)
                    if (capturable) {
                        if (count + read <= MAX_CANDIDATE_BYTES) {
                            chunk.copyInto(candidate, count.toInt(), 0, read)
                        } else {
                            // No partial, silently truncated JSON ever crosses the gate.
                            candidate.fill(0)
                            capturable = false
                        }
                    }
                    count += read
                }
                if (count == 0L) throw IOException("Empty selected document")
                val digestBytes = digest.digest()
                val hex = CharArray(digestBytes.size * 2)
                digestBytes.forEachIndexed { index, value ->
                    val b = value.toInt() and 0xff
                    hex[2 * index] = hexDigits[b ushr 4]
                    hex[2 * index + 1] = hexDigits[b and 15]
                }
                val fingerprint = UnverifiedBackupFingerprint(count, String(hex))
                if (capturable) {
                    ReadOnlyDocumentScreening.SmallCandidate(
                        fingerprint, candidate.copyOf(count.toInt())
                    )
                } else ReadOnlyDocumentScreening.FingerprintOnly(fingerprint)
            }
        } finally {
            candidate.fill(0)
            chunk.fill(0)
        }
    }
}
