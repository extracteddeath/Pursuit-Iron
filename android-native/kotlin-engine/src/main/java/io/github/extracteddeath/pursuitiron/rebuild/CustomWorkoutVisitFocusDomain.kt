package io.github.extracteddeath.pursuitiron.rebuild

/**
 * Read-only custom workout focus order. Distinct from the durable next-visit
 * policy: a lifter can REVIEW a logged entry, another main set or a valid
 * amplifier follow-up without changing progression, prescriptions or journal
 * receipts. A skipped/failed main set never unlocks its amplifier.
 */
object CustomWorkoutVisitFocusDomain {
    fun visibleOrder(journal:CustomWorkoutJournalDraft):List<CustomWorkoutVisitKey> =
        buildList {
            for(slot in journal.snapshot.slots) for(set in slot.sets) {
                val main=CustomWorkoutVisitKey(
                    set.setKey,CustomWorkoutVisitPart.MAIN)
                add(main)
                val saved=(journal.effectiveVisits[main]?.operation
                    as? CustomWorkoutJournalOperation.Record)
                if(set.prescription.amplifier!=null &&
                    saved?.outcome==KotlinJournalSetOutcome.COMPLETED) {
                    add(CustomWorkoutVisitKey(
                        set.setKey,CustomWorkoutVisitPart.FOLLOW_UP))
                }
            }
        }
}
