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
    val stage: Int = 1,
    /** Exact issued protocol identity, never reconstructed from actual reps. */
    val percentageIntent: CustomPercentageSetIntent? = null
) {
    internal fun matchesIssuedMain(slot: CustomWorkoutSlotSnapshot, set: CustomWorkoutSetSnapshot): Boolean {
        val target = set.prescription
        return part == CustomWorkoutVisitPart.MAIN && stage == 1 && effectiveEventId.isNotBlank() &&
            set.slotId == slot.slotId && slotId == slot.slotId && exerciseId == slot.exerciseId &&
            setKey == set.setKey && setType == target.type && prescribedReps == target.reps &&
            prescribedRir == target.targetRir && amplifierKind == target.amplifier?.kind &&
            percentageIntent == target.percentageIntent &&
            KotlinSetNumberAuthorityDomain.sameIssuedTarget(prescribedLoad, target.load)
    }
}
data class CustomWorkoutHistoricalEvidence(
    val athleteId: String,
    val workoutId: String,
    val cycleId: String,
    val cycleRevision: Long,
    val finishedEventId: String,
    val journalRevision: Long,
    val visits: List<CustomHistoricalVisitFact>,
    val shortSessionLimitMinutes:Int?=null,
    val shortSessionOmittedSetKeys:List<String> =emptyList(),
    val sessionAdjusted:Boolean=false,
    /** A warm-up-only edit can preserve every original working prescription.
     * Unclassified older callers retain the conservative adjustment gate. */
    val workingPrescriptionAdjusted:Boolean=sessionAdjusted
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
            !prescriptionEditedForTime && !workingPrescriptionAdjusted && mains.isNotEmpty() &&
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
                main.outcome,main.performed,main.eventId,
                percentageIntent=set.prescription.percentageIntent)
            if(main.outcome==KotlinJournalSetOutcome.COMPLETED) {
                val amp=set.prescription.amplifier ?:continue
                val stageKeys=CustomWorkoutExecutionDomain.followUpVisits(
                    rebuilt,set)
                if(stageKeys.isEmpty() && !AdvancedAmplifierProtocolDomain.firstDropSuppressed(
                        amp,set.prescription.load,main.performed))return CustomHistoryEvidenceResult.Rejected(
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
        val adjustments=journal.receipts.mapNotNull{
            it.operation as? CustomWorkoutJournalOperation.AdjustRemaining
        }
        // Warm-up insertion/removal shifts ordinals but must not change a
        // single original working identity, prescription or slot metadata.
        // An actual working edit remains conservative even after Undo.
        fun workingProjection(snapshot:CustomWorkoutDaySnapshot)=snapshot.slots.map{slot->
            slot.copy(sets=slot.sets.filter{it.prescription.type==DraftSetType.WORKING}
                .mapIndexed{index,set->set.copy(ordinalInSlot=index+1)})
        }
        val warmupOnly=adjustments.isNotEmpty() &&
            adjustments.all{it.request.warmupChange!=null} &&
            workingProjection(journal.originalSnapshot)==workingProjection(journal.snapshot)
        return CustomHistoryEvidenceResult.Candidate(
            CustomWorkoutHistoricalEvidence(
                journal.snapshot.stamp.athleteId,journal.workoutId,
                journal.snapshot.stamp.cycleId,
                journal.snapshot.stamp.cycleRevision,
                checkNotNull(journal.finishedByEventId),
                journal.revision,Collections.unmodifiableList(facts),
                journal.originalSnapshot.shortSessionLimitMinutes,
                Collections.unmodifiableList(
                    journal.originalSnapshot.shortSessionOmittedSetKeys.toList()),
                sessionAdjusted=adjustments.isNotEmpty(),
                workingPrescriptionAdjusted=adjustments.isNotEmpty() && !warmupOnly))
    }
}
