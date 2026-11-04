package io.github.extracteddeath.pursuitiron.rebuild

import java.util.Collections

/**
 * Append-only correction of observations belonging to a FINISHED workout.
 * The original workout journal, finish receipt and issued targets NEVER change.
 * Native storage must authenticate the finished journal, compare-and-swap the
 * correction revision, and durably commit each operation before acknowledging it.
 */
data class KotlinSavedSetCorrection(
    val eventId: String,
    val expectedCorrectionRevision: Long,
    val setKey: String,
    /** Current effective event ID, whether original or a later correction. */
    val correctsEventId: String,
    val outcome: KotlinJournalSetOutcome,
    val actual: KotlinPerformedSet?
)

data class KotlinSavedSetCorrectionReceipt(
    val appliedCorrectionRevision: Long,
    val correction: KotlinSavedSetCorrection
)

data class KotlinSavedHistoryCorrectionState(
    val originalFinishedJournal: KotlinWorkoutJournalState,
    val correctionRevision: Long,
    val receipts: List<KotlinSavedSetCorrectionReceipt>,
    val effectiveCorrections: Map<String, KotlinSavedSetCorrectionReceipt>
) {
    val canCommitHistory: Boolean get() = false
    val canChangeIssuedTargets: Boolean get() = false
}

enum class KotlinSavedCorrectionRefusal {
    INVALID_ORIGINAL_JOURNAL,
    INVALID_STORED_CORRECTIONS,
    REVISION_CONFLICT,
    EVENT_ID_COLLISION,
    UNKNOWN_SET,
    WRONG_PREDECESSOR,
    INVALID_VALUES,
    TOO_MANY_CORRECTIONS
}

sealed interface KotlinSavedCorrectionDecision {
    data class Proposed(
        val state: KotlinSavedHistoryCorrectionState,
        /** True only for the exact previously applied receipt. */
        val replayed: Boolean
    ) : KotlinSavedCorrectionDecision

    data class Rejected(val reason: KotlinSavedCorrectionRefusal) :
        KotlinSavedCorrectionDecision
}

object KotlinSavedHistoryCorrectionDomain {
    private val id = Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,79}$")
    private const val MAX_CORRECTIONS = 512

    /** Authentication of the journal's disk bytes is the STORE's obligation. */
    fun begin(finished: KotlinWorkoutJournalState): KotlinSavedHistoryCorrectionState {
        require(KotlinWorkoutHistoryEvidenceDomain.project(finished)
            is KotlinHistoryProjectionResult.Candidate)
        return KotlinSavedHistoryCorrectionState(
            finished, 0, emptyList(), emptyMap()
        )
    }

    private fun apply(
        state: KotlinSavedHistoryCorrectionState,
        operation: KotlinSavedSetCorrection
    ): KotlinSavedCorrectionDecision {
        if (!id.matches(operation.eventId) ||
            !id.matches(operation.correctsEventId) ||
            operation.setKey.isEmpty() || operation.setKey.length > 100)
            return KotlinSavedCorrectionDecision.Rejected(
                KotlinSavedCorrectionRefusal.INVALID_VALUES)

        val duplicate = state.receipts.firstOrNull {
            it.correction.eventId == operation.eventId
        }
        if (duplicate != null)
            return if (duplicate.correction == operation)
                KotlinSavedCorrectionDecision.Proposed(state, true)
            else KotlinSavedCorrectionDecision.Rejected(
                KotlinSavedCorrectionRefusal.EVENT_ID_COLLISION)

        if (state.originalFinishedJournal.receipts.any {
                it.operation.eventId == operation.eventId
            })
            return KotlinSavedCorrectionDecision.Rejected(
                KotlinSavedCorrectionRefusal.EVENT_ID_COLLISION)

        if (operation.expectedCorrectionRevision != state.correctionRevision)
            return KotlinSavedCorrectionDecision.Rejected(
                KotlinSavedCorrectionRefusal.REVISION_CONFLICT)
        if (state.receipts.size >= MAX_CORRECTIONS)
            return KotlinSavedCorrectionDecision.Rejected(
                KotlinSavedCorrectionRefusal.TOO_MANY_CORRECTIONS)

        val originalTarget = state.originalFinishedJournal.snapshot.targets
            .firstOrNull { it.setKey == operation.setKey }
            ?: return KotlinSavedCorrectionDecision.Rejected(
                KotlinSavedCorrectionRefusal.UNKNOWN_SET)

        val currentlyEffective = state.effectiveCorrections[operation.setKey]
            ?.correction?.eventId
            ?: state.originalFinishedJournal.effectiveSets[operation.setKey]
                ?.operation?.eventId
        if (currentlyEffective != operation.correctsEventId)
            return KotlinSavedCorrectionDecision.Rejected(
                KotlinSavedCorrectionRefusal.WRONG_PREDECESSOR)

        if (!KotlinWorkoutJournalDomain.validPerformed(
                originalTarget, operation.outcome, operation.actual))
            return KotlinSavedCorrectionDecision.Rejected(
                KotlinSavedCorrectionRefusal.INVALID_VALUES)

        val receipt = KotlinSavedSetCorrectionReceipt(
            state.correctionRevision + 1, operation)
        val effective = LinkedHashMap(state.effectiveCorrections)
        effective[operation.setKey] = receipt
        return KotlinSavedCorrectionDecision.Proposed(
            KotlinSavedHistoryCorrectionState(
                state.originalFinishedJournal, receipt.appliedCorrectionRevision,
                Collections.unmodifiableList(state.receipts + receipt),
                Collections.unmodifiableMap(effective)
            ), false)
    }

    /**
     * Replay ONLY committed ordered receipts, never a saved effective map.
     * The returned object is a pure candidate, not proof of persistence.
     */
    fun replay(
        original: KotlinWorkoutJournalState,
        receipts: List<KotlinSavedSetCorrectionReceipt>,
        expectedRevision: Long
    ): KotlinSavedCorrectionDecision {
        val initial = runCatching { begin(original) }.getOrNull()
            ?: return KotlinSavedCorrectionDecision.Rejected(
                KotlinSavedCorrectionRefusal.INVALID_ORIGINAL_JOURNAL)
        if (receipts.size > MAX_CORRECTIONS)
            return KotlinSavedCorrectionDecision.Rejected(
                KotlinSavedCorrectionRefusal.TOO_MANY_CORRECTIONS)
        if (expectedRevision < 0 || expectedRevision != receipts.size.toLong())
            return KotlinSavedCorrectionDecision.Rejected(
                KotlinSavedCorrectionRefusal.INVALID_STORED_CORRECTIONS)

        var current = initial
        for ((index, receipt) in receipts.withIndex()) {
            if (receipt.appliedCorrectionRevision != index.toLong() + 1 ||
                receipt.correction.expectedCorrectionRevision != index.toLong())
                return KotlinSavedCorrectionDecision.Rejected(
                    KotlinSavedCorrectionRefusal.INVALID_STORED_CORRECTIONS)
            val step = apply(current, receipt.correction)
            if (step !is KotlinSavedCorrectionDecision.Proposed ||
                step.replayed ||
                step.state.correctionRevision != receipt.appliedCorrectionRevision)
                return KotlinSavedCorrectionDecision.Rejected(
                    KotlinSavedCorrectionRefusal.INVALID_STORED_CORRECTIONS)
            current = step.state
        }
        return KotlinSavedCorrectionDecision.Proposed(current, false)
    }

    fun verifyStoredState(
        state: KotlinSavedHistoryCorrectionState
    ): KotlinSavedCorrectionDecision {
        val result = replay(
            state.originalFinishedJournal, state.receipts, state.correctionRevision)
        if (result !is KotlinSavedCorrectionDecision.Proposed ||
            result.state != state)
            return KotlinSavedCorrectionDecision.Rejected(
                KotlinSavedCorrectionRefusal.INVALID_STORED_CORRECTIONS)
        return result
    }

    /** A valid proposal still requires native-owner atomic commit/CAS. */
    fun reduce(
        state: KotlinSavedHistoryCorrectionState,
        operation: KotlinSavedSetCorrection
    ): KotlinSavedCorrectionDecision {
        if (verifyStoredState(state) !is KotlinSavedCorrectionDecision.Proposed)
            return KotlinSavedCorrectionDecision.Rejected(
                KotlinSavedCorrectionRefusal.INVALID_STORED_CORRECTIONS)
        return apply(state, operation)
    }
}
