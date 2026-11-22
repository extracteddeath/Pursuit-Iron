package io.github.extracteddeath.pursuitiron.rebuild

/**
 * One strict historical machine-evidence reader, shared by the basic
 * progression and opt-in coaching consumers. A B visit can leave older
 * Machine A history independent ONLY when every relevant issued working
 * set has a distinct, matching, completed and physically identified receipt.
 *
 * No station is guessed from the authored machine label or exercise name;
 * unknown, skipped and partially recorded work cannot certify separation.
 * This projection cannot issue, revise or save any training targets.
 */
internal object CustomVerifiedSwappedStationEvidenceDomain {
    private fun completeMachines(
        session: CustomCoachingHistoricalSession,
        slots: List<CustomWorkoutSlotSnapshot>
    ): Set<String>? {
        val snapshot = session.snapshot
        val evidence = session.evidence
        if (slots.isEmpty() || snapshot.stamp.athleteId != evidence.athleteId ||
            snapshot.stamp.cycleId != evidence.cycleId ||
            snapshot.stamp.cycleRevision != evidence.cycleRevision ||
            slots.map { it.slotId }.distinct().size != slots.size)
            return null

        val slotIds = slots.map { it.slotId }.toSet()
        val prescribed = slots.flatMap { slot ->
            slot.sets.filter { it.prescription.type == DraftSetType.WORKING }
        }
        val visits = evidence.visits.filter {
            it.part == CustomWorkoutVisitPart.MAIN &&
                it.setType == DraftSetType.WORKING && it.slotId in slotIds
        }
        // A missing, duplicated or foreign set can hide an attempt at A.
        if (prescribed.isEmpty() || prescribed.size != visits.size ||
            prescribed.map { it.setKey }.toSet().size != prescribed.size ||
            visits.map { it.setKey }.toSet().size != visits.size)
            return null
        val byKey = visits.associateBy { it.setKey }
        val stations = mutableSetOf<String>()
        for (slot in slots) for (set in slot.sets) {
            if (set.prescription.type != DraftSetType.WORKING) continue
            val fact = byKey[set.setKey] ?: return null
            val target = set.prescription
            val issued = target.load as? DraftSetLoad.Verified ?: return null
            val actual = fact.actual ?: return null
            val machine = actual.machineInstanceId ?: return null
            if (!fact.matchesIssuedMain(slot, set) ||
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

    /** A real native Swap removes the original slot and creates new keys.
     * Every working set on that day needs verified station attribution. */
    fun machinesWhenOriginalSlotAbsent(
        session: CustomCoachingHistoricalSession,
        originalSlotId: String
    ): Set<String>? {
        if (originalSlotId.isBlank() ||
            session.snapshot.slots.any { it.slotId == originalSlotId })
            return null
        return completeMachines(session,session.snapshot.slots).takeIf {
            // No extra unassigned main working receipts may be discarded.
            it != null && session.evidence.visits.count { fact ->
                fact.part == CustomWorkoutVisitPart.MAIN &&
                    fact.setType == DraftSetType.WORKING
            } == session.snapshot.slots.sumOf { slot ->
                slot.sets.count { set -> set.prescription.type == DraftSetType.WORKING }
            }
        }
    }

    /** When a known slot still exists, verify only that actual exercise's
     * full original working-set ledger. Other unrelated lifts need not be
     * weighted-machine sets for this station-specific history decision. */
    fun machinesForVerifiedSlot(
        session: CustomCoachingHistoricalSession,
        slotId: String
    ): Set<String>? {
        val slot = session.snapshot.slots.singleOrNull { it.slotId == slotId }
            ?:return null
        return completeMachines(session,listOf(slot))
    }
}
