package io.github.extracteddeath.pursuitiron.rebuild

import java.util.Collections

/**
 * Saved corrections for an ALREADY FINISHED, athlete-authored custom workout.
 *
 * The issued snapshot, set identities, original factual receipts and Finish
 * event remain immutable. These are independent proposed correction receipts;
 * a native owner must durably CAS a correction file and redo the shared history
 * manifest transaction before showing corrected Last, PR or progression.
 *
 * A custom follow-up is its own visit, never an implied completed exposure.
 */
data class CustomSavedVisitCorrection(
    val eventId: String,
    val expectedCorrectionRevision: Long,
    val visit: CustomWorkoutVisitKey,
    val correctsEventId: String,
    val outcome: KotlinJournalSetOutcome,
    val performed: KotlinPerformedSet?
)

data class CustomSavedVisitCorrectionReceipt(
    val appliedCorrectionRevision: Long,
    val correction: CustomSavedVisitCorrection
)

data class CustomSavedHistoryCorrectionState(
    val originalFinishedJournal: CustomWorkoutJournalDraft,
    val correctionRevision: Long,
    val receipts: List<CustomSavedVisitCorrectionReceipt>,
    val effectiveCorrections: Map<CustomWorkoutVisitKey, CustomSavedVisitCorrectionReceipt>
) {
    val mayChangeOriginalJournal: Boolean get() = false
    val mayPublishHistory: Boolean get() = false
}

enum class CustomSavedCorrectionRefusal {
    INVALID_ORIGINAL_JOURNAL, INVALID_STORED_CORRECTIONS,
    REVISION_CONFLICT, EVENT_ID_COLLISION, UNKNOWN_VISIT,
    WRONG_PREDECESSOR, INVALID_VALUES, INCONSISTENT_AMPLIFIER,
    TOO_MANY_CORRECTIONS
}

sealed interface CustomSavedCorrectionDecision {
    data class Proposed(
        val state: CustomSavedHistoryCorrectionState,
        val replayed: Boolean
    ) : CustomSavedCorrectionDecision
    data class Rejected(val reason: CustomSavedCorrectionRefusal) :
        CustomSavedCorrectionDecision
}

object CustomSavedHistoryCorrectionDomain {
    private val id = Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,79}$")
    private const val MAX_CORRECTIONS = 512

    /** The store still has to authenticate the owner and persisted bytes. */
    fun begin(original: CustomWorkoutJournalDraft): CustomSavedHistoryCorrectionState {
        require(CustomWorkoutHistoryEvidenceDomain.project(original)
            is CustomHistoryEvidenceResult.Candidate)
        return CustomSavedHistoryCorrectionState(
            original, 0, emptyList(), emptyMap())
    }

    private fun current(
        state: CustomSavedHistoryCorrectionState,
        visit: CustomWorkoutVisitKey
    ): CustomWorkoutJournalOperation.Record? {
        val saved = state.effectiveCorrections[visit]?.correction
        if (saved != null)
            return CustomWorkoutJournalOperation.Record(
                saved.eventId, saved.expectedCorrectionRevision,
                saved.visit, saved.outcome, saved.performed,
                saved.correctsEventId)
        return state.originalFinishedJournal.effectiveVisits[visit]?.operation
            as? CustomWorkoutJournalOperation.Record
    }

    private fun apply(
        state: CustomSavedHistoryCorrectionState,
        operation: CustomSavedVisitCorrection
    ): CustomSavedCorrectionDecision {
        fun reject(reason: CustomSavedCorrectionRefusal) =
            CustomSavedCorrectionDecision.Rejected(reason)
        if (!id.matches(operation.eventId) ||
            !id.matches(operation.correctsEventId) ||
            operation.expectedCorrectionRevision < 0)
            return reject(CustomSavedCorrectionRefusal.INVALID_VALUES)

        val duplicate = state.receipts.firstOrNull {
            it.correction.eventId == operation.eventId
        }
        if (duplicate != null)
            return if (duplicate.correction == operation)
                CustomSavedCorrectionDecision.Proposed(state, true)
            else reject(CustomSavedCorrectionRefusal.EVENT_ID_COLLISION)
        if (state.originalFinishedJournal.receipts.any {
                it.operation.eventId == operation.eventId
            })
            return reject(CustomSavedCorrectionRefusal.EVENT_ID_COLLISION)
        if (operation.expectedCorrectionRevision != state.correctionRevision)
            return reject(CustomSavedCorrectionRefusal.REVISION_CONFLICT)
        if (state.receipts.size >= MAX_CORRECTIONS)
            return reject(CustomSavedCorrectionRefusal.TOO_MANY_CORRECTIONS)

        val target = state.originalFinishedJournal.snapshot.slots.asSequence()
            .flatMap { it.sets.asSequence() }
            .firstOrNull { it.setKey == operation.visit.setKey }
            ?: return reject(CustomSavedCorrectionRefusal.UNKNOWN_VISIT)
        val original = state.originalFinishedJournal.effectiveVisits[operation.visit]
            ?.operation as? CustomWorkoutJournalOperation.Record
            ?: return reject(CustomSavedCorrectionRefusal.UNKNOWN_VISIT)
        val effective = current(state, operation.visit)
            ?: return reject(CustomSavedCorrectionRefusal.UNKNOWN_VISIT)
        if (effective.eventId != operation.correctsEventId)
            return reject(CustomSavedCorrectionRefusal.WRONG_PREDECESSOR)

        val mainKey = CustomWorkoutVisitKey(
            operation.visit.setKey, CustomWorkoutVisitPart.MAIN)
        val followKey = CustomWorkoutVisitKey(
            operation.visit.setKey, CustomWorkoutVisitPart.FOLLOW_UP)
        val main = current(state, mainKey)
            ?: return reject(CustomSavedCorrectionRefusal.UNKNOWN_VISIT)
        val amplifier = target.prescription.amplifier
        val followed = current(state, followKey)
        val load = when (operation.visit.part) {
            CustomWorkoutVisitPart.MAIN -> {
                if (operation.outcome == KotlinJournalSetOutcome.COMPLETED &&
                    amplifier != null && followed == null)
                    return reject(CustomSavedCorrectionRefusal.INCONSISTENT_AMPLIFIER)
                // To turn an originally completed main set into a skip/failure,
                // the independent follow-up must FIRST be marked skipped.
                if (operation.outcome != KotlinJournalSetOutcome.COMPLETED &&
                    followed != null &&
                    followed.outcome != KotlinJournalSetOutcome.SKIPPED)
                    return reject(CustomSavedCorrectionRefusal.INCONSISTENT_AMPLIFIER)
                target.prescription.load
            }
            CustomWorkoutVisitPart.FOLLOW_UP -> {
                if (amplifier == null ||
                    main.outcome != KotlinJournalSetOutcome.COMPLETED ||
                    original.visit.part != CustomWorkoutVisitPart.FOLLOW_UP)
                    return reject(CustomSavedCorrectionRefusal.INCONSISTENT_AMPLIFIER)
                amplifier.followUpLoad ?: target.prescription.load
            }
        }
        if (!CustomWorkoutJournalDomain.actualValid(
                load, operation.outcome, operation.performed))
            return reject(CustomSavedCorrectionRefusal.INVALID_VALUES)

        val receipt = CustomSavedVisitCorrectionReceipt(
            state.correctionRevision + 1, operation)
        val next = LinkedHashMap(state.effectiveCorrections)
        next[operation.visit] = receipt
        return CustomSavedCorrectionDecision.Proposed(
            CustomSavedHistoryCorrectionState(
                state.originalFinishedJournal, receipt.appliedCorrectionRevision,
                Collections.unmodifiableList(state.receipts + receipt),
                Collections.unmodifiableMap(next)), false)
    }

    /** An exact retry is idempotent; a changed same-ID request is not. */
    fun reduce(
        state: CustomSavedHistoryCorrectionState,
        operation: CustomSavedVisitCorrection
    ): CustomSavedCorrectionDecision {
        if (verifyStoredState(state) !is CustomSavedCorrectionDecision.Proposed)
            return CustomSavedCorrectionDecision.Rejected(
                CustomSavedCorrectionRefusal.INVALID_STORED_CORRECTIONS)
        return apply(state, operation)
    }

    /** Reconstruct from the original complete journal + ordered receipts. */
    fun replay(
        original: CustomWorkoutJournalDraft,
        receipts: List<CustomSavedVisitCorrectionReceipt>,
        expectedRevision: Long
    ): CustomSavedCorrectionDecision {
        val first = runCatching { begin(original) }.getOrNull()
            ?: return CustomSavedCorrectionDecision.Rejected(
                CustomSavedCorrectionRefusal.INVALID_ORIGINAL_JOURNAL)
        if (receipts.size > MAX_CORRECTIONS)
            return CustomSavedCorrectionDecision.Rejected(
                CustomSavedCorrectionRefusal.TOO_MANY_CORRECTIONS)
        if (expectedRevision < 0 || expectedRevision != receipts.size.toLong())
            return CustomSavedCorrectionDecision.Rejected(
                CustomSavedCorrectionRefusal.INVALID_STORED_CORRECTIONS)
        var current = first
        for ((index, receipt) in receipts.withIndex()) {
            if (receipt.appliedCorrectionRevision != index.toLong() + 1 ||
                receipt.correction.expectedCorrectionRevision != index.toLong())
                return CustomSavedCorrectionDecision.Rejected(
                    CustomSavedCorrectionRefusal.INVALID_STORED_CORRECTIONS)
            val next = apply(current, receipt.correction)
            if (next !is CustomSavedCorrectionDecision.Proposed ||
                next.replayed ||
                next.state.correctionRevision != receipt.appliedCorrectionRevision)
                return CustomSavedCorrectionDecision.Rejected(
                    CustomSavedCorrectionRefusal.INVALID_STORED_CORRECTIONS)
            current = next.state
        }
        return CustomSavedCorrectionDecision.Proposed(current, false)
    }

    fun verifyStoredState(
        state: CustomSavedHistoryCorrectionState
    ): CustomSavedCorrectionDecision {
        val result = replay(
            state.originalFinishedJournal, state.receipts,
            state.correctionRevision)
        return if (result is CustomSavedCorrectionDecision.Proposed &&
            result.state == state) result
        else CustomSavedCorrectionDecision.Rejected(
            CustomSavedCorrectionRefusal.INVALID_STORED_CORRECTIONS)
    }
}

/**
 * Present corrected factual evidence without editing the immutable finished
 * journal. Original discarded follow-up work is retained in audit receipts
 * but does not become a performed set when its main is now skipped/failed.
 */
data class CustomSavedCorrectedHistory(
    val evidence: CustomWorkoutHistoricalEvidence,
    val correctionRevision: Long
) {
    val mayPublishPr: Boolean get() = false
    val mayChangeProgression: Boolean get() = false
}

sealed interface CustomSavedHistoryProjectionResult {
    data class Candidate(val corrected: CustomSavedCorrectedHistory) :
        CustomSavedHistoryProjectionResult
    data object Rejected : CustomSavedHistoryProjectionResult
}

object CustomSavedHistoryEvidenceDomain {
    fun projectSaved(
        state: CustomSavedHistoryCorrectionState
    ): CustomSavedHistoryProjectionResult {
        if (CustomSavedHistoryCorrectionDomain.verifyStoredState(state)
            !is CustomSavedCorrectionDecision.Proposed)
            return CustomSavedHistoryProjectionResult.Rejected
        val original = CustomWorkoutHistoryEvidenceDomain.project(
            state.originalFinishedJournal)
        val facts = (original as? CustomHistoryEvidenceResult.Candidate)
            ?.evidence ?: return CustomSavedHistoryProjectionResult.Rejected
        val mainByKey = facts.visits.filter {
            it.part == CustomWorkoutVisitPart.MAIN
        }.associate { it.setKey to
            (state.effectiveCorrections[CustomWorkoutVisitKey(
                it.setKey, CustomWorkoutVisitPart.MAIN)]?.correction?.outcome
                ?: it.outcome) }
        val corrected = facts.visits.mapNotNull { fact ->
            if (fact.part == CustomWorkoutVisitPart.FOLLOW_UP &&
                mainByKey[fact.setKey] != KotlinJournalSetOutcome.COMPLETED)
                return@mapNotNull null
            val op = state.effectiveCorrections[CustomWorkoutVisitKey(
                fact.setKey, fact.part)]?.correction
            if (op == null) fact
            else fact.copy(
                outcome = op.outcome,
                actual = op.performed,
                effectiveEventId = op.eventId)
        }
        return CustomSavedHistoryProjectionResult.Candidate(
            CustomSavedCorrectedHistory(
                facts.copy(visits = Collections.unmodifiableList(corrected)),
                state.correctionRevision))
    }
}
