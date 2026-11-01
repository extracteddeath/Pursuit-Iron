package io.github.extracteddeath.pursuitiron.domain

import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/**
 * Exact source-copy consistency check between the prior user-initiated
 * fingerprint screening and any FUTURE read-only canonical parser call.
 *
 * No file/URI access, persistence, JSON parsing, history mutation or Compose
 * integration. The native preview still only screens a source and closes it.
 */
object ReadOnlyBackupSourceGate {
    private const val MAX_PROBE_BYTES = 128 * 1024
    private const val MAX_PROBE_CHARACTERS = 65536
    private val shaPattern = Regex("^[0-9a-f]{64}$")

    /**
     * Refuse source switching, silent lossy UTF-8 replacements and unbounded
     * content. A checksum is a consistency proof, NOT source authenticity,
     * a validated backup, user consent for import or a write permit.
     */
    fun exactText(
        bytes: ByteArray,
        previouslyScreened: UnverifiedBackupFingerprint
    ): String {
        require(bytes.isNotEmpty() && bytes.size <= MAX_PROBE_BYTES) {
            "Schema probe source exceeds the bounded compatibility window"
        }
        require(previouslyScreened.byteCount == bytes.size.toLong() &&
            shaPattern.matches(previouslyScreened.sha256Hex)) {
            "Source does not match the previously screened byte count"
        }
        val current = ReadOnlyBackupScreening.fingerprint(ByteArrayInputStream(bytes))
        require(current == previouslyScreened) {
            "Source bytes changed since the fingerprint was screened"
        }
        val decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        val text = decoder.decode(ByteBuffer.wrap(bytes)).toString()
        require(text.isNotEmpty() && text.length <= MAX_PROBE_CHARACTERS &&
            !text.startsWith("\uFEFF")) {
            "Source is not a bounded canonical UTF-8 JSON copy"
        }
        // The only schema owner remains canonical parseStoredData in the JS
        // sandbox; callers MUST still pass the result through that parser.
        return text
    }
}
