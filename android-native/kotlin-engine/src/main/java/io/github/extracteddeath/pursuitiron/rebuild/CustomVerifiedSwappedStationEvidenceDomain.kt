package io.github.extracteddeath.pursuitiron.rebuild

/**
 * ONE strict evidence rule shared by basic progression and optional V2/V3
 * coaching when a native workout Swap replaced an original slot with new
 * slot and set identities. It cannot infer which movement was replaced:
 * only a complete, independently attributed, weighted and performed visit
 * can establish that the day occurred on a different physical station.
 *
 * Null is an ambiguous/unverified visit, not evidence of another machine.
 * The caller separately compares the returned station set with its own
 * requested station; no historical receipt or future prescription is edited.
 */
internal object CustomVerifiedSwappedStationEvidenceDomain {
    fun machinesWhenOriginalSlotAbsent(
        session: CustomCoachingHistoricalSession,
        originalSlotId: String
    ): Set<String>? {
        val snapshot = session.snapshot
        val evidence = session.evidence
        if (originalSlotId.isBlank() || snapshot.slots.any { it.slotId == originalSlotId } ||
            snapshot.stamp.athleteId != evidence.athleteId ||
            snapshot.stamp.cycleId != evidence.cycleId ||
            snapshot.stamp.cycleRevision != evidence.cycleRevision)
            return null

        val prescribed = snapshot.slots.flatMap { slot ->
            slot.sets.filter { it.prescription.type == DraftSetType.WORKING }
        }
        val visits = evidence.visits.filter {
            it.part == CustomWorkoutVisitPart.MAIN &&
                it.setType == DraftSetType.WORKING
        }
        // A partial set history may hide work on the original machine.
        if (prescribed.isEmpty() || prescribed.size != visits.size ||
            prescribed.map { it.setKey }.toSet().size != prescribed.size ||
            visits.map { it.setKey }.toSet().size != visits.size)
            return null
        val byKey = visits.associateBy { it.setKey }
        val stations = mutableSetOf<String>()
        for (slot in snapshot.slots) for (set in slot.sets) {
            if (set.prescription.type != DraftSetType.WORKING) continue
            val fact = byKey[set.setKey] ?: return null
            val target = set.prescription
            val issued = target.load as? DraftSetLoad.Verified ?: return null
            val actual = fact.actual ?: return null
            val machine = actual.machineInstanceId ?: return null
            if (fact.slotId != slot.slotId || fact.exerciseId != slot.exerciseId ||
                fact.part != CustomWorkoutVisitPart.MAIN ||
                fact.setType != DraftSetType.WORKING ||
                fact.prescribedLoad != target.load ||
                fact.prescribedReps != target.reps ||
                fact.prescribedRir != target.targetRir ||
                fact.amplifierKind != target.amplifier?.kind ||
                fact.effectiveEventId.isBlank() ||
                fact.outcome != KotlinJournalSetOutcome.COMPLETED ||
                actual.load == null || actual.unit != issued.unit ||
                !KotlinMachineAlternativesDomain.validInstanceId(machine) ||
                !CustomWorkoutJournalDomain.actualValid(
                    target.load, fact.outcome, actual))
                return null
            stations.add(machine)
        }
        return stations.takeIf { it.isNotEmpty() }
    }
}
