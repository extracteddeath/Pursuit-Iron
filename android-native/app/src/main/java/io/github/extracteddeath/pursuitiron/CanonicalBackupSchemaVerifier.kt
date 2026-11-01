package io.github.extracteddeath.pursuitiron

import io.github.extracteddeath.pursuitiron.domain.BackupSavedProgramInventory
import io.github.extracteddeath.pursuitiron.domain.ReadOnlyBackupSchemaResult
import io.github.extracteddeath.pursuitiron.domain.UnverifiedBackupFingerprint

/**
 * Optional metadata-only read gateway. Shipping Compose preview supplies null.
 * Implementations must pass the exact bytes through the independently pinned
 * canonical JS sandbox and strict Kotlin codec; no other schema owner is valid.
 * A successful result never authorizes restore, saves or workout execution.
 */
internal interface CanonicalBackupSchemaVerifier {
    suspend fun inspect(
        exactSmallCopy: ByteArray,
        fingerprint: UnverifiedBackupFingerprint
    ): Result<ReadOnlyBackupSchemaResult>

    /** User-selected backup copy only; never the current active program. */
    suspend fun inspectPrograms(
        exactSmallCopy: ByteArray,
        fingerprint: UnverifiedBackupFingerprint
    ): Result<BackupSavedProgramInventory>
}
