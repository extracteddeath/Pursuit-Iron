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
    val effectiveEventId: String,
    /** Distinct saved mini-set, never a new ordinary working set. */
    val stage: Int = 1
)
data class CustomWorkoutHistoricalEvidence(
    val athleteId: String,
    val workoutId: String,
    val cycleId: String,
    val cycleRevision: Long,
    val finishedEventId: String,
    val journalRevision: Long,
    val visits: List<CustomHistoricalVisitFact>,
    val shortSessionLimitMinutes:Int?=null,
    val shortSessionOmittedSetKeys:List<String> =emptyList()
) {
    val prescriptionEditedForTime:Boolean
        get()=shortSessionOmittedSetKeys.isNotEmpty()
    /**
     * A finished journal may still contain SKIPPED/FAILED working sets.
     * Native future-block activation must count only actual completed
     * original MAIN working sets from owner-replayed History, never a
     * Finish event, amplifier mini-set, or reduced-time substitute alone.
     * This is a historical observation, not athlete adherence or consent.
     */
    val allIssuedMainWorkingSetsCompleted:Boolean get() {
        val mains=visits.filter{
            it.part==CustomWorkoutVisitPart.MAIN &&
                it.setType==DraftSetType.WORKING
        }
        return shortSessionLimitMinutes==null &&
            !prescriptionEditedForTime && mains.isNotEmpty() &&
            mains.all{
                it.outcome==KotlinJournalSetOutcome.COMPLETED &&
                    it.actual!=null
            }
    }
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
                val stageKeys=CustomWorkoutExecutionDomain.followUpVisits(
                    rebuilt,set)
                if(stageKeys.isEmpty())return CustomHistoryEvidenceResult.Rejected(
                    CustomHistoryEvidenceRefusal.INCOMPLETE_FACTS)
                for(followKey in stageKeys) {
                    val follow=rebuilt.effectiveVisits[followKey]?.operation
                        as? CustomWorkoutJournalOperation.Record
                        ?:return CustomHistoryEvidenceResult.Rejected(
                            CustomHistoryEvidenceRefusal.INCOMPLETE_FACTS)
                    facts+=CustomHistoricalVisitFact(
                        slot.slotId,slot.exerciseId,set.setKey,
                        CustomWorkoutVisitPart.FOLLOW_UP,set.prescription.type,
                        AdvancedAmplifierProtocolDomain.followUpLoadForStage(
                            amp,set.prescription.load,followKey.stage),
                        RepBand(amp.followUpReps,amp.followUpReps),null,
                        amp.kind,follow.outcome,follow.performed,follow.eventId,
                        followKey.stage)
                }
            }
        }
        return CustomHistoryEvidenceResult.Candidate(
            CustomWorkoutHistoricalEvidence(
                journal.snapshot.stamp.athleteId,journal.workoutId,
                journal.snapshot.stamp.cycleId,
                journal.snapshot.stamp.cycleRevision,
                checkNotNull(journal.finishedByEventId),
                journal.revision,Collections.unmodifiableList(facts),
                journal.originalSnapshot.shortSessionLimitMinutes,
                Collections.unmodifiableList(
                    journal.originalSnapshot.shortSessionOmittedSetKeys.toList())))
    }
}
