package io.github.extracteddeath.pursuitiron.rebuild

import java.util.Collections

/**
 * Read-only projection of correction-aware CUSTOM History, for the actual
 * working-set row. The journal/History verifier remains the authority: this
 * component never generates a set, changes an observed repetition, or grants
 * correction/issuance permission.
 *
 * Return null for structural conflicts, so the UI can show the original flat
 * facts rather than silently hiding any saved observation.
 */
data class CustomHistoricalSetGroup(
    val main: CustomHistoricalVisitFact,
    val followUps: List<CustomHistoricalVisitFact>
) {
    val techniqueCaption: String?
        get() {
            val kind=main.amplifierKind ?:return null
            val name=AdvancedAmplifierProtocolDomain.template(kind).title
            if(main.outcome!=KotlinJournalSetOutcome.COMPLETED)
                return "$name · not performed (main set ${main.outcome.name.lowercase().replace('_',' ')})"
            if(followUps.isEmpty())return "$name · no follow-up performed"

            val actuals=followUps.joinToString(" + ") { fact ->
                when(fact.outcome) {
                    KotlinJournalSetOutcome.SKIPPED -> "skipped"
                    KotlinJournalSetOutcome.FAILED_ATTEMPT -> "failed"
                    KotlinJournalSetOutcome.COMPLETED -> when(kind) {
                        CustomAmplifierKind.DROP_SET -> {
                            val performed=fact.actual
                            val load=performed?.load?.stripTrailingZeros()?.toPlainString() ?: "?"
                            val unit=performed?.unit?.name?.lowercase() ?: "?"
                            "$load $unit × ${performed?.reps ?: "?"}"
                        }
                        else -> fact.actual?.reps?.toString() ?: "?"
                    }
                }
            }
            return when(kind) {
                CustomAmplifierKind.LENGTHENED_PARTIALS ->
                    "$name · $actuals partial reps (not full ROM)"
                CustomAmplifierKind.DROP_SET ->
                    "$name · ${followUps.size} ${if(followUps.size==1)"drop" else "drops"} · $actuals"
                CustomAmplifierKind.MYO_REPS,CustomAmplifierKind.REST_PAUSE,
                CustomAmplifierKind.CLUSTER ->
                    "$name · ${followUps.size} ${AdvancedAmplifierProtocolDomain.stageNoun(kind,plural=followUps.size!=1)} · $actuals reps"
            }
        }
}

object CustomHistoricalTechniqueGroupingDomain {
    /** The evidence already comes from current owner-verified History. This
     * second structural check protects presentation against detached or
     * duplicated amplifier rows and preserves source order of regular sets. */
    fun group(visits:List<CustomHistoricalVisitFact>):List<CustomHistoricalSetGroup>? {
        val groups=linkedMapOf<String,MutableList<CustomHistoricalVisitFact>>()
        val mains=linkedMapOf<String,CustomHistoricalVisitFact>()
        val seenStages=mutableSetOf<Pair<String,Int>>()
        for(fact in visits) {
            if(fact.part==CustomWorkoutVisitPart.MAIN) {
                if(fact.stage!=1 || mains.containsKey(fact.setKey))return null
                mains[fact.setKey]=fact
                groups[fact.setKey]=mutableListOf()
            } else {
                val main=mains[fact.setKey] ?:return null
                val recorded=groups[fact.setKey] ?:return null
                if(main.outcome!=KotlinJournalSetOutcome.COMPLETED ||
                    main.setType!=DraftSetType.WORKING ||
                    main.amplifierKind==null ||
                    fact.amplifierKind!=main.amplifierKind ||
                    fact.slotId!=main.slotId ||
                    fact.exerciseId!=main.exerciseId ||
                    fact.setType!=main.setType ||
                    fact.stage !in 1..5 ||
                    !seenStages.add(fact.setKey to fact.stage) ||
                    fact.stage!=recorded.size+1)return null
                recorded+=fact
            }
        }
        return Collections.unmodifiableList(mains.map{(key,main)->
            CustomHistoricalSetGroup(main,
                Collections.unmodifiableList(groups.getValue(key).toList()))
        })
    }
}
