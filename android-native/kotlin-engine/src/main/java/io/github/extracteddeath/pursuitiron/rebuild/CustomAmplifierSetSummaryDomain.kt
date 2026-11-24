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
    val progress: String,
    /** Only actually completed follow-ups; NEVER the planned targets. */
    val actuals: String? = null
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
            CustomAmplifierKind.MYO_REPS ->
                "${amp.followUpRounds} mini-sets × ${amp.followUpReps} reps · " +
                    "same activation load · ${amp.followUpRestSeconds}s between"
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
        // In a corrupted or stale view, a forged mini-set can have the right
        // rep count while its physical load differs. Neither the completion
        // label nor the actuals must declare that sequence valid.
        val sameLoadMismatch=AdvancedAmplifierProtocolDomain
            .requiresActivationLoad(amp.kind) &&
            (main.performed==null || observed.any { receipt ->
                receipt.outcome==KotlinJournalSetOutcome.COMPLETED &&
                    (receipt.performed==null ||
                        !CustomWorkoutExecutionDomain.sameActualLoadAndStation(
                            main.performed,receipt.performed))
            })
        val permitted=CustomWorkoutExecutionDomain.followUpVisits(journal,set)
        val next=permitted.firstOrNull{it !in journal.effectiveVisits}
        val progress=when {
            sameLoadMismatch ->
                "Recorded follow-up load/station mismatch · verify journal"
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
        val completedObservations=observed.mapNotNull { receipt ->
            receipt.performed?.takeIf {
                receipt.outcome==KotlinJournalSetOutcome.COMPLETED
            }
        }
        val actuals=if(completedObservations.isEmpty())null
            else when(amp.kind) {
                CustomAmplifierKind.DROP_SET ->
                    "Performed drops: "+completedObservations.joinToString(" → ") {
                        "${it.load?.toPlainString() ?: "?"} "+
                            "${it.unit?.name?.lowercase() ?: "?"} × ${it.reps}"
                    }
                CustomAmplifierKind.LENGTHENED_PARTIALS ->
                    "Logged ${completedObservations.sumOf{it.reps}} partial reps"
                else -> {
                    // Show the physical activation load that was actually
                    // logged, NOT the issued prescription or an earlier set.
                    // An invalid/stale draft cannot silently display changed
                    // mini weights as a normal same-load technique.
                    val activation=main.performed
                    val sameStationAndLoad=activation!=null &&
                        completedObservations.all {
                            CustomWorkoutExecutionDomain.sameActualLoadAndStation(
                                activation,it)
                        }
                    if(!sameStationAndLoad)
                        "Follow-up load/station mismatch · review recorded technique"
                    else {
                        val loadLabel=when {
                            activation.load==null && activation.unit==null ->
                                "bodyweight"
                            activation.load!=null && activation.unit!=null ->
                                "${activation.load.stripTrailingZeros().toPlainString()} " +
                                    activation.unit.name.lowercase()
                            else -> "unverified load"
                        }
                        "Performed ${AdvancedAmplifierProtocolDomain.stageNoun(amp.kind,true)} at " +
                            loadLabel+": "+
                            completedObservations.joinToString(" + "){it.reps.toString()}+
                            " reps"
                    }
                }
            }
        return CustomAmplifierSetSummary(title,planned,progress,actuals)
    }
}
