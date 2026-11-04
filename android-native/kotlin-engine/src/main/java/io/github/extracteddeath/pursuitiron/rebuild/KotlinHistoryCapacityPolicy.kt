package io.github.extracteddeath.pursuitiron.rebuild

/**
 * Shared bounds for the current full-replay, single-file native history.
 * This is intentionally not unlimited lifetime storage; a future segmented
 * index must replace the linear whole-manifest file safely.
 */
object KotlinHistoryCapacityPolicy {
    const val LEGACY_FORMAT_MAX_COMPLETIONS = 128
    const val MAX_MANIFEST_COMPLETIONS = 4096
    const val MAX_LEDGER_BYTES = 2_097_152
}
