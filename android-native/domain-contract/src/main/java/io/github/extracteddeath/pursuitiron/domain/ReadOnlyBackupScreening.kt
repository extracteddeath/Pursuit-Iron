package io.github.extracteddeath.pursuitiron.domain

import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest

/** Byte-level fingerprint only; never means that a training backup was validated. */
data class UnverifiedBackupFingerprint(val byteCount: Long, val sha256Hex: String)

/** Stream and close a user-selected copy. Never caches its data or parses programs. */
object ReadOnlyBackupScreening {
    const val MAX_COPY_BYTES = 50L * 1024 * 1024
    private val hexDigits = "0123456789abcdef".toCharArray()

    fun fingerprint(source: InputStream, maxBytes: Long = MAX_COPY_BYTES): UnverifiedBackupFingerprint {
        require(maxBytes in 1..MAX_COPY_BYTES) { "Invalid screening limit" }
        return source.use { input ->
            val sha = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(32 * 1024)
            var count = 0L
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                if (read == 0) throw IOException("Input made no progress")
                if (count > maxBytes - read) throw IOException("File exceeds screening limit")
                sha.update(buffer, 0, read)
                count += read
            }
            if (count == 0L) throw IOException("An empty file cannot be screened")
            val bytes = sha.digest()
            val characters = CharArray(bytes.size * 2)
            bytes.forEachIndexed { index, byte ->
                val unsigned = byte.toInt() and 0xff
                characters[index * 2] = hexDigits[unsigned ushr 4]
                characters[index * 2 + 1] = hexDigits[unsigned and 0x0f]
            }
            UnverifiedBackupFingerprint(count, String(characters))
        }
    }
}
