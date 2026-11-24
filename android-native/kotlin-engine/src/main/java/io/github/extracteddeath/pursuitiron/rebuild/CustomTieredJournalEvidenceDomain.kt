package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import java.util.Collections

enum class CustomTieredJournalRefusal {
    INVALID_SCOPE, UNVERIFIED_JOURNAL, CHANGED_OR_SHORTENED_SESSION,
    INVALID_PROTOCOL, INCOMPATIBLE_OBSERVATION, RECOVERY_REQUIRES_REVIEW
}

sealed interface CustomTieredJournalResult {
    data class Refused(val reason: CustomTieredJournalRefusal): CustomTieredJournalResult
    data class Candidate(val exposure: TieredLinearExposure): CustomTieredJournalResult {
        val mayIssueWorkout: Boolean get() = false
        val maySaveProgram: Boolean get() = false
    }
}

/**
 * Adapts one exact, corrected, finished journal into a GZCLP exposure.
 * The owner must cold-open the journal and prove ledger completeness, source
 * consent, chronology and current stock before replay. A refused exposure
 * must never be silently removed from that ledger.
 */
object CustomTieredJournalEvidenceDomain {
    fun project(
        journal: CustomWorkoutJournalDraft,
        completionOrder: Long,
        scope: BasicProgressionScope,
        slotId: String,
        tier: PercentageTier
    ): CustomTieredJournalResult {
        fun no(reason: CustomTieredJournalRefusal) = CustomTieredJournalResult.Refused(reason)
        val original = journal.originalSnapshot
        if (!scope.valid() || completionOrder <= 0 || slotId.isBlank() ||
            original.stamp.athleteId != scope.athleteId ||
            original.stamp.cycleId != scope.programId ||
            original.stamp.gymId != scope.gymId ||
            original.blockId != scope.blockId ||
            original.blockId + ":d" + original.weekday != scope.trainingDayId)
            return no(CustomTieredJournalRefusal.INVALID_SCOPE)
        val evidence = (CustomWorkoutHistoryEvidenceDomain.project(journal)
            as? CustomHistoryEvidenceResult.Candidate)?.evidence
            ?: return no(CustomTieredJournalRefusal.UNVERIFIED_JOURNAL)
        if (journal.snapshot != original || original.prescriptionEditedForTime ||
            original.shortSessionLimitMinutes != null)
            return no(CustomTieredJournalRefusal.CHANGED_OR_SHORTENED_SESSION)
        val slot = original.slots.singleOrNull { it.slotId == slotId }
            ?: return no(CustomTieredJournalRefusal.INVALID_SCOPE)
        if (slot.exerciseId != scope.exerciseId ||
            slot.machineInstanceId != scope.machineInstanceId ||
            slot.role != (if (tier == PercentageTier.PRIMARY) ComposerRole.PRIMARY
                else ComposerRole.SECONDARY))
            return no(CustomTieredJournalRefusal.INVALID_SCOPE)
        val sets = slot.sets.filter { it.prescription.type == DraftSetType.WORKING }
        if (sets.size !in 1..20 || sets.map { it.setKey }.distinct().size != sets.size)
            return no(CustomTieredJournalRefusal.INVALID_PROTOCOL)
        val first = sets.first().prescription.percentageIntent
            ?: return no(CustomTieredJournalRefusal.INVALID_PROTOCOL)
        val stage = first.tierStage ?: return no(CustomTieredJournalRefusal.INVALID_PROTOCOL)
        if (stage !in 0..2) return no(CustomTieredJournalRefusal.INVALID_PROTOCOL)
        val expectedReps = if (tier == PercentageTier.PRIMARY) listOf(3,2,1)[stage]
            else listOf(10,8,6)[stage]
        val expectedCount = if (tier == PercentageTier.PRIMARY) listOf(5,6,10)[stage] else 3
        val facts = evidence.visits.filter {
            it.slotId == slotId && it.part == CustomWorkoutVisitPart.MAIN &&
                it.setType == DraftSetType.WORKING
        }
        if (facts.size != sets.size || facts.map { it.setKey }.distinct().size != facts.size ||
            facts.map { it.setKey }.toSet() != sets.map { it.setKey }.toSet())
            return no(CustomTieredJournalRefusal.INCOMPATIBLE_OBSERVATION)
        val targets = mutableListOf<TieredLinearSetTarget>()
        val observations = mutableListOf<WorkingSetFact>()
        for ((index, set) in sets.withIndex()) {
            val prescription = set.prescription
            val intent = prescription.percentageIntent
                ?: return no(CustomTieredJournalRefusal.INVALID_PROTOCOL)
            val load = prescription.load as? DraftSetLoad.Verified
                ?: return no(CustomTieredJournalRefusal.INVALID_PROTOCOL)
            if (intent.recovery || original.blockPurpose != CustomBlockPurpose.TRAINING)
                return no(CustomTieredJournalRefusal.RECOVERY_REQUIRES_REVIEW)
            if (!intent.validForSet(prescription) || intent.scheme != NamedPercentageScheme.GZCLP ||
                intent.tier != tier || intent.tierStage != stage ||
                intent.weekInBlock != original.weekInBlock ||
                intent.blockWeeks != first.blockWeeks ||
                intent.sourceSetCount != expectedCount ||
                intent.referenceLoad.compareTo(first.referenceLoad) != 0 ||
                intent.repOut != (index == sets.lastIndex) ||
                prescription.exactRepTarget != expectedReps ||
                load.unit != scope.unit || load.basis != scope.basis ||
                load.value.compareTo(first.referenceLoad) != 0)
                return no(CustomTieredJournalRefusal.INVALID_PROTOCOL)
            val fact = facts.single { it.setKey == set.setKey }
            val actual = fact.actual
            if (!fact.matchesIssuedMain(slot, set) ||
                fact.outcome == KotlinJournalSetOutcome.SKIPPED ||
                actual == null || actual.unit != scope.unit ||
                actual.machineInstanceId != scope.machineInstanceId ||
                actual.load?.compareTo(load.value) != 0)
                return no(CustomTieredJournalRefusal.INCOMPATIBLE_OBSERVATION)
            targets += TieredLinearSetTarget(index, load.value, expectedReps, intent.repOut)
            observations += WorkingSetFact(index,
                if (fact.outcome == KotlinJournalSetOutcome.COMPLETED) SetOutcome.COMPLETED
                else SetOutcome.FAILED_ATTEMPT,
                actual.load, actual.reps, actual.actualRir, load.value, prescription.targetRir)
        }
        return CustomTieredJournalResult.Candidate(TieredLinearExposure(
            journal.workoutId, completionOrder, scope, tier, stage,
            Collections.unmodifiableList(targets), Collections.unmodifiableList(observations),
            true, slotId))
    }
}
