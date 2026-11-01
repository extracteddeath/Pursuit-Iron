package io.github.extracteddeath.pursuitiron.engine

import io.github.extracteddeath.pursuitiron.domain.BackupSavedProgramInventory
import io.github.extracteddeath.pursuitiron.domain.BackupSavedProgramInventoryCodec
import io.github.extracteddeath.pursuitiron.domain.EngineResponse
import io.github.extracteddeath.pursuitiron.domain.ReadOnlyBackupSchemaCodec
import io.github.extracteddeath.pursuitiron.domain.ReadOnlyBackupSchemaResult
import io.github.extracteddeath.pursuitiron.domain.ReadOnlyBackupSourceGate
import io.github.extracteddeath.pursuitiron.domain.UnverifiedBackupFingerprint
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Isolated canonical read-only schema transport, not a restore importer.
 * Intentionally unconnected to Compose, the document picker, program views,
 * preferences, or workout history. Only sanitized counts escape this adapter.
 */
class SandboxBackupSchemaInspector(private val worker: CanonicalJsReadOnlyService) {
    private val sequence = AtomicLong(0L)

    suspend fun inspectPrograms(
        sourceBytes: ByteArray,
        previouslyScreened: UnverifiedBackupFingerprint
    ): Result<BackupSavedProgramInventory> = suspendCoroutine { continuation ->
        val requestId = "android-backup-programs-${sequence.incrementAndGet()}"
        val request = try {
            val text = ReadOnlyBackupSourceGate.exactText(sourceBytes, previouslyScreened)
            BackupSavedProgramInventoryCodec.encode(requestId, text)
        } catch (failure: Throwable) {
            continuation.resume(Result.failure(failure))
            return@suspendCoroutine
        }
        try {
            worker.backupProgramInventoryAsync(request).whenComplete { raw, failure ->
                continuation.resume(runCatching {
                    if (failure != null) throw (failure.cause ?: failure)
                    when (val decoded = BackupSavedProgramInventoryCodec.decode(raw, requestId)) {
                        is EngineResponse.Success -> decoded.result
                        is EngineResponse.Failure -> throw IllegalStateException(
                            "Canonical backup inventory: ${decoded.error.code}"
                        )
                    }
                })
            }
        } catch (failure: Throwable) {
            continuation.resume(Result.failure(failure))
        }
    }

    suspend fun inspect(
        sourceBytes: ByteArray,
        previouslyScreened: UnverifiedBackupFingerprint
    ): Result<ReadOnlyBackupSchemaResult> = suspendCoroutine { continuation ->
        val requestId = "android-backup-check-${sequence.incrementAndGet()}"
        // Critical: prepare and verify the exact source synchronously. The
        // immutable request String cannot observe later caller byte mutations.
        val request = try {
            checkedBackupSchemaWire(sourceBytes, previouslyScreened, requestId)
        } catch (failure: Throwable) {
            continuation.resume(Result.failure(failure))
            return@suspendCoroutine
        }
        try {
            worker.checkBackupSchemaAsync(request).whenComplete { raw, error ->
                continuation.resume(runCatching {
                    if (error != null) throw (error.cause ?: error)
                    when (val result = ReadOnlyBackupSchemaCodec.decode(raw, requestId)) {
                        is EngineResponse.Success -> result.result
                        is EngineResponse.Failure ->
                            throw IllegalStateException(
                                "Canonical read-only backup schema: ${result.error.code}"
                            )
                    }
                })
            }
        } catch (failure: Throwable) {
            continuation.resume(Result.failure(failure))
        }
    }
}

/**
 * Pure fail-closed request preparation; does not open Android documents.
 * Matching bytes prove equality to a previous copy, not its authenticity.
 */
internal fun checkedBackupSchemaWire(
    sourceBytes: ByteArray,
    previouslyScreened: UnverifiedBackupFingerprint,
    requestId: String
): String {
    val text = ReadOnlyBackupSourceGate.exactText(sourceBytes, previouslyScreened)
    return ReadOnlyBackupSchemaCodec.encode(requestId, text)
}
