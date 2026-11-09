package io.github.extracteddeath.pursuitiron.rebuild

import java.util.Collections

/** A conservative, read-only projection of the exact CUSTOM journal. */
data class CustomHistoricalVisitFact(
    val slotId: String,
    val exerciseId: String,
    val setKey: String,
    val part: CustomWorkoutVisitPart,
    val setType: DraftSetType,
    val prescribedLoad: DraftSetLoad,
    val prescribedReps: RepBand,
    val prescribedRir: Int?,
    val amplifierKind: CustomAmplifierKind?,
    val outcome: KotlinJournalSetOutcome,
    val actual: KotlinPerformedSet?,
    val effectiveEventId: String
)
data class CustomWorkoutHistoricalEvidence(
    val athleteId: String,
    val workoutId: String,
    val cycleId: String,
    val cycleRevision: Long,
    val finishedEventId: String,
    val journalRevision: Long,
    val visits: List<CustomHistoricalVisitFact>
) {
    /** No derived PRs or progression until these facts join the global index. */
    val canPublishGlobalHistory: Boolean get()=false
    val canApplyProgression: Boolean get()=false
}
enum class CustomHistoryEvidenceRefusal {
    UNFINISHED, UNVERIFIED_JOURNAL, INCOMPLETE_FACTS
}
sealed interface CustomHistoryEvidenceResult {
    data class Candidate(val evidence:CustomWorkoutHistoricalEvidence):
        CustomHistoryEvidenceResult
    data class Rejected(val reason:CustomHistoryEvidenceRefusal):
        CustomHistoryEvidenceResult
}
object CustomWorkoutHistoryEvidenceDomain {
    /**
     * The journal must have been independently cold-opened by native owner
     * storage. Even then we replay every receipt to reject forged effective
     * maps, skipped followups recorded as done, and invented finish status.
     */
    fun project(journal:CustomWorkoutJournalDraft):CustomHistoryEvidenceResult {
        if(journal.finishedByEventId==null)
            return CustomHistoryEvidenceResult.Rejected(
                CustomHistoryEvidenceRefusal.UNFINISHED)
        var rebuilt=runCatching {
            CustomWorkoutJournalDomain.begin(
                journal.workoutId,journal.originalSnapshot,
                journal.preparedConsentFingerprint)
        }.getOrNull() ?:return CustomHistoryEvidenceResult.Rejected(
            CustomHistoryEvidenceRefusal.UNVERIFIED_JOURNAL)
        for((index,receipt) in journal.receipts.withIndex()) {
            if(receipt.appliedRevision!=(index+1).toLong())
                return CustomHistoryEvidenceResult.Rejected(
                    CustomHistoryEvidenceRefusal.UNVERIFIED_JOURNAL)
            val decided=CustomWorkoutJournalDomain.reduce(
                rebuilt,receipt.operation)
            if(decided !is CustomWorkoutJournalDecision.Proposed ||
                decided.replayed || decided.draft.revision!=receipt.appliedRevision)
                return CustomHistoryEvidenceResult.Rejected(
                    CustomHistoryEvidenceRefusal.UNVERIFIED_JOURNAL)
            rebuilt=decided.draft
        }
        if(rebuilt!=journal || rebuilt.unresolvedVisitCount!=0)
            return CustomHistoryEvidenceResult.Rejected(
                CustomHistoryEvidenceRefusal.UNVERIFIED_JOURNAL)
        val facts=mutableListOf<CustomHistoricalVisitFact>()
        for(slot in journal.snapshot.slots)for(set in slot.sets) {
            val key=CustomWorkoutVisitKey(set.setKey,CustomWorkoutVisitPart.MAIN)
            val main=rebuilt.effectiveVisits[key]?.operation
                as? CustomWorkoutJournalOperation.Record
                ?:return CustomHistoryEvidenceResult.Rejected(
                    CustomHistoryEvidenceRefusal.INCOMPLETE_FACTS)
            facts+=CustomHistoricalVisitFact(
                slot.slotId,slot.exerciseId,set.setKey,
                CustomWorkoutVisitPart.MAIN,set.prescription.type,
                set.prescription.load,set.prescription.reps,
                set.prescription.targetRir,set.prescription.amplifier?.kind,
                main.outcome,main.performed,main.eventId)
            if(main.outcome==KotlinJournalSetOutcome.COMPLETED) {
                val amp=set.prescription.amplifier ?:continue
                val follow=rebuilt.effectiveVisits[CustomWorkoutVisitKey(
                    set.setKey,CustomWorkoutVisitPart.FOLLOW_UP)]?.operation
                    as? CustomWorkoutJournalOperation.Record
                    ?:return CustomHistoryEvidenceResult.Rejected(
                        CustomHistoryEvidenceRefusal.INCOMPLETE_FACTS)
                facts+=CustomHistoricalVisitFact(
                    slot.slotId,slot.exerciseId,set.setKey,
                    CustomWorkoutVisitPart.FOLLOW_UP,set.prescription.type,
                    amp.followUpLoad ?:set.prescription.load,
                    RepBand(amp.followUpReps,amp.followUpReps),null,
                    amp.kind,follow.outcome,follow.performed,follow.eventId)
            }
        }
        return CustomHistoryEvidenceResult.Candidate(
            CustomWorkoutHistoricalEvidence(
                journal.snapshot.stamp.athleteId,journal.workoutId,
                journal.snapshot.stamp.cycleId,
                journal.snapshot.stamp.cycleRevision,
                checkNotNull(journal.finishedByEventId),
                journal.revision,Collections.unmodifiableList(facts)))
    }
}
