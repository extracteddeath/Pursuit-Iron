package io.github.extracteddeath.pursuitiron.domain

import java.util.concurrent.CancellationException

/**
 * One read-only, already canonically projected CURRENT-store record.
 * Only a future verified canonical store owner may provide frames. The digest
 * must describe its current bytes, not an offline user-selected backup.
 * No raw store, history, working loads, prescription, or write handle here.
 */
data class CanonicalCurrentPlanFrame(
    val reference: ReadOnlyProgramReference,
    val deletion: CurrentProgramDeletionEvidence,
    val transportJson: String
)

/**
 * Future certified current-store port. No production implementation exists.
 * Every read must observe current canonical source bytes; never cached or
 * document-picker bytes. Notify subscribers after every committed change.
 */
interface CanonicalCurrentPlanReadPort {
    suspend fun readCurrentProjection(programId: String): CanonicalCurrentPlanFrame?
    fun subscribeCommittedChanges(onChanged: () -> Unit): AutoCloseable
}

/**
 * Routes current canonical JavaScript projection into the strict Kotlin codec
 * and policy, without implementing Kotlin store parsing, merging or logging.
 * CurrentReadOnlyPlanGateway separately rechecks revision around projection.
 */
class CanonicalCurrentPlanSourceAdapter(
    private val port: CanonicalCurrentPlanReadPort
) : CurrentReadOnlyPlanSource {
    private fun allowed(
        programId: String, frame: CanonicalCurrentPlanFrame
    ): Boolean {
        val deletedAt = when (val deletion = frame.deletion) {
            CurrentProgramDeletionEvidence.CheckedAbsent -> null
            is CurrentProgramDeletionEvidence.CheckedAt -> deletion.deletedAt
        }
        return programId == frame.reference.programId &&
            ReadOnlyProgramReferenceGate.sameActiveRevision(
                frame.reference, frame.reference, deletedAt
            )
    }

    override suspend fun currentEvidence(programId: String): CurrentProgramEvidence? {
        val frame = port.readCurrentProjection(programId) ?: return null
        return if (allowed(programId, frame))
            CurrentProgramEvidence(frame.reference, frame.deletion)
        else null
    }

    override suspend fun projectStructure(programId: String): ReadOnlyProgramSummary? {
        val frame = port.readCurrentProjection(programId) ?: return null
        if (!allowed(programId, frame)) return null
        return try {
            val decoded = ReadOnlyProgramTransportCodec.decode(
                frame.transportJson, frame.reference
            )
            val checked = ReadOnlyProgramPresentationPolicy.inspect(
                decoded, CurrentProgramEvidence(frame.reference, frame.deletion)
            )
            (checked as? ReadOnlyProgramPresentation.Structure)?.program
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }

    override fun subscribeCanonicalChanges(onChanged: () -> Unit): AutoCloseable =
        port.subscribeCommittedChanges(onChanged)
}
