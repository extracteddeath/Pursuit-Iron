package io.github.extracteddeath.pursuitiron.domain

/**
 * Conservative v13 read-only snapshot window. All JSON is still verified
 * by the canonical JavaScript parser; these bounds are transport limits,
 * not approval to import or run a program.
 */
object ReadOnlyBackupLimits {
    const val MAX_SOURCE_BYTES = 1024 * 1024
    const val MAX_SOURCE_CHARACTERS = 1024 * 1024
    const val MAX_WIRE_BYTES = 4 * 1024 * 1024
}
