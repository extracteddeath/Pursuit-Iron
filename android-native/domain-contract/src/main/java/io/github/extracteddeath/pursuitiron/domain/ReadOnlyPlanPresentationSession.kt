package io.github.extracteddeath.pursuitiron.domain

/**
 * Transient, display-only lifecycle gate for a future authoritative CURRENT
 * canonical store. Never persist this state or treat backups as current plans.
 * The current Android preview has no source with personal data connected.
 */
class ReadOnlyPlanPresentationSession(
    private val gateway: CurrentReadOnlyPlanGateway
) {
    private val lock = Any()
    private var generation = 0L
    private var foregroundProgramId: String? = null
    private var visible: ReadOnlyProgramPresentation = ReadOnlyProgramPresentation.Unavailable

    /** Entering the Plan screen always clears stale personal structure. */
    fun onForeground(programId: String?) = synchronized(lock) {
        generation++
        foregroundProgramId = programId
        visible = ReadOnlyProgramPresentation.Unavailable
    }

    /** Release personal structure on backgrounding or navigating away. */
    fun onBackground() = synchronized(lock) {
        generation++
        foregroundProgramId = null
        visible = ReadOnlyProgramPresentation.Unavailable
    }

    /** The future authoritative source owner must deliver every store change. */
    fun onCanonicalStoreChanged() = synchronized(lock) {
        generation++
        visible = ReadOnlyProgramPresentation.Unavailable
    }

    fun current(): ReadOnlyProgramPresentation = synchronized(lock) { visible }

    /**
     * Any new refresh hides old structure. Results from an older read cannot
     * repopulate after navigation, backgrounding, writes or a newer refresh.
     */
    suspend fun refresh(): ReadOnlyProgramPresentation {
        val attempt = synchronized(lock) {
            val selected = foregroundProgramId
                ?: return ReadOnlyProgramPresentation.Unavailable
            generation++
            visible = ReadOnlyProgramPresentation.Unavailable
            generation to selected
        }
        val proposed = gateway.inspect(attempt.second)
        return synchronized(lock) {
            if (generation != attempt.first || foregroundProgramId != attempt.second) {
                ReadOnlyProgramPresentation.Unavailable
            } else {
                visible = proposed
                proposed
            }
        }
    }
}
