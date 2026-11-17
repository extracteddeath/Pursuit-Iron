package io.github.extracteddeath.pursuitiron.rebuild

/**
 * Read-only, set-attached technique summary for the active Train workspace.
 *
 * Prescriptions are never confused with completed mini-sets, drops or partial
 * reps. The owner-fenced journal remains the sole source of actuals; this
 * projection cannot authorize an additional visit or mutate a target.
 */
data class CustomAmplifierSetSummary(
    val title: String,
    val planned: String,
    val progress: String
)

object CustomAmplifierSetSummaryDomain {
    fun review(
        journal: CustomWorkoutJournalDraft,
        set: CustomWorkoutSetSnapshot
    ): CustomAmplifierSetSummary? {
        if(set.prescription.type!=DraftSetType.WORKING)return null
        val amp=set.prescription.amplifier ?:return null
        val title=AdvancedAmplifierProtocolDomain.template(amp.kind).title
        val planned=when(amp.kind) {
            CustomAmplifierKind.LENGTHENED_PARTIALS ->
                "Optional ${amp.followUpReps} partial reps · same load · no rest"
            CustomAmplifierKind.DROP_SET -> {
                val loads=(1..amp.followUpRounds).map { stage ->
                    (AdvancedAmplifierProtocolDomain.dropStageLoad(amp,stage)
                        as? DraftSetLoad.Verified)?.let {
                        "${it.value.toPlainString()} ${it.unit.name.lowercase()}"
                    } ?: "unverified"
                }
                "${amp.followUpRounds} weight ${if(amp.followUpRounds==1)"drop" else "drops"} · "+
                    loads.joinToString(" → ")+" · ${amp.followUpReps} reps per drop"
            }
            else ->
                "${amp.followUpRounds} ${AdvancedAmplifierProtocolDomain.stageNoun(amp.kind,true)}"+
                    " × ${amp.followUpReps} reps · ${amp.followUpRestSeconds}s between"
        }
        val main=journal.effectiveVisits[CustomWorkoutVisitKey(
            set.setKey,CustomWorkoutVisitPart.MAIN)]?.operation
            as? CustomWorkoutJournalOperation.Record
        if(main==null)
            return CustomAmplifierSetSummary(title,planned,"Planned after this working set")
        if(main.outcome!=KotlinJournalSetOutcome.COMPLETED)
            return CustomAmplifierSetSummary(title,planned,
                "Not performed · main working set was not completed")

        val observed=(1..amp.followUpRounds).mapNotNull { stage ->
            val key=CustomWorkoutVisitKey(set.setKey,
                CustomWorkoutVisitPart.FOLLOW_UP,stage)
            journal.effectiveVisits[key]?.operation
                as? CustomWorkoutJournalOperation.Record
        }
        val completed=observed.count{it.outcome==KotlinJournalSetOutcome.COMPLETED}
        val permitted=CustomWorkoutExecutionDomain.followUpVisits(journal,set)
        val next=permitted.firstOrNull{it !in journal.effectiveVisits}
        val progress=when {
            next!=null ->
                "$completed of ${amp.followUpRounds} logged · Next: "+
                    AdvancedAmplifierProtocolDomain.stageLabel(amp,next.stage)
            observed.isEmpty() ->
                "No follow-up eligible for the logged activation load or station"
            completed==amp.followUpRounds ->
                "Complete · $completed of ${amp.followUpRounds} logged"
            else ->
                "Sequence ended · $completed of ${amp.followUpRounds} logged"
        }
        return CustomAmplifierSetSummary(title,planned,progress)
    }
}
