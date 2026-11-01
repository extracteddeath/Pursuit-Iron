package io.github.extracteddeath.pursuitiron.domain

import java.util.concurrent.CancellationException

/**
 * Future authoritative CURRENT canonical-store adapter, not a selected backup.
 * No implementation with personal data exists in the native preview.
 */
interface CurrentReadOnlyPlanSource {
    suspend fun currentEvidence(programId: String): CurrentProgramEvidence?
    suspend fun projectStructure(programId: String): ReadOnlyProgramSummary?

    /**
     * Register for EVERY committed canonical-store revision, deletion,
     * history merge, logout or source replacement. Call only AFTER the store
     * commits a change. The returned handle MUST unsubscribe on close.
     *
     * This is required, not optional: an unobserved current store may not
     * feed a personal Plan presentation. The caller handles any thread.
     */
    fun subscribeCanonicalChanges(onChanged: () -> Unit): AutoCloseable
}

/** Fail closed until Android owns and verifies the canonical current store. */
object UnconnectedCurrentReadOnlyPlanSource : CurrentReadOnlyPlanSource {
    override suspend fun currentEvidence(programId: String): CurrentProgramEvidence? = null
    override suspend fun projectStructure(programId: String): ReadOnlyProgramSummary? = null
    override fun subscribeCanonicalChanges(onChanged: () -> Unit): AutoCloseable =
        AutoCloseable {}
}

/**
 * Read current evidence before and after projection to detect concurrent edits,
 * changed tombstones and stale revisions. Callers must refresh on app resume and
 * canonical store updates; this does not authorize workouts or backup restore.
 */
class CurrentReadOnlyPlanGateway(private val source: CurrentReadOnlyPlanSource) {
    private val id = Regex("^[A-Za-z0-9][A-Za-z0-9_-]{0,127}$")

    suspend fun inspect(programId: String): ReadOnlyProgramPresentation {
        if (!id.matches(programId)) return ReadOnlyProgramPresentation.Unavailable
        return try {
            val before = source.currentEvidence(programId)
                ?: return ReadOnlyProgramPresentation.Unavailable
            val structure = source.projectStructure(programId)
                ?: return ReadOnlyProgramPresentation.Unavailable
            val after = source.currentEvidence(programId)
                ?: return ReadOnlyProgramPresentation.Unavailable
            if (before != after || structure.id != programId)
                ReadOnlyProgramPresentation.Unavailable
            else ReadOnlyProgramPresentationPolicy.inspect(structure, after)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            ReadOnlyProgramPresentation.Unavailable
        }
    }
}
