package io.github.extracteddeath.pursuitiron.rebuild

/**
 * Read-only custom workout focus order. Distinct from the durable next-visit
 * policy: a lifter can REVIEW a logged entry, another main set or a valid
 * amplifier follow-up without changing progression, prescriptions or journal
 * receipts. A skipped/failed main set never unlocks its amplifier.
 */
data class CustomWorkoutVisitProgress(
    val recordedVisits:Int,
    val requiredVisits:Int,
    val recordedMainSets:Int,
    val totalMainSets:Int,
    val recordedFollowUps:Int,
    val requiredFollowUps:Int
) {
    val fraction:Float get()=if(requiredVisits<=0)0f
        else (recordedVisits.toFloat()/requiredVisits).coerceIn(0f,1f)
}

object CustomWorkoutVisitFocusDomain {
    /**
     * Main set and triggered amplifier follow-up receipts are separately
     * required. A completed main with an unresolved follow-up is NOT
     * 100% progress; skipped/failed mains don't create fake follow-ups.
     * This is a source-projected display count, not a progression signal.
     */
    fun progress(journal:CustomWorkoutJournalDraft):CustomWorkoutVisitProgress {
        val order=visibleOrder(journal)
        val main=order.filter{it.part==CustomWorkoutVisitPart.MAIN}
        val follow=order.filter{it.part==CustomWorkoutVisitPart.FOLLOW_UP}
        val logged=journal.effectiveVisits
        return CustomWorkoutVisitProgress(
            recordedVisits=order.count{it in logged},
            requiredVisits=order.size,
            recordedMainSets=main.count{it in logged},
            totalMainSets=main.size,
            recordedFollowUps=follow.count{it in logged},
            requiredFollowUps=follow.size)
    }

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
