package io.github.extracteddeath.pursuitiron.rebuild

import java.util.Collections

/** A read-only selection of unresolved entries in ONE current exercise/slot.
 * The exact journal revision must be checked again by its durable writer.
 * Skipping these entries never changes a prescription or a future program. */
data class RemainingExerciseReview<K>(
    val exerciseId:String,
    val journalRevision:Long,
    val entries:List<K>,
    val warmupCount:Int,
    val workingCount:Int,
    val followUpCount:Int,
    val preservedEntryCount:Int
)

enum class WorkoutConcernAction { REDUCE, SKIP }

/** Exact preview of the affected issued entries. No diagnosis, prescription
 * mutation or workout authorization. The owner rederives it at commit. */
data class WorkoutConcernGroup<K>(
    val exerciseId:String,val entries:List<K>,val recordedCount:Int
)
data class WorkoutConcernReview<K>(
    val workoutId:String,val journalRevision:Long,val muscle:TrainingMuscle,
    val action:WorkoutConcernAction,val groups:List<WorkoutConcernGroup<K>>
) {
    val entries:List<K> get()=groups.flatMap{it.entries}
}

object WorkoutRemainingExerciseDomain {
    private data class ConcernEntry<K>(val key:K,val type:DraftSetType,
        val followUp:Boolean=false,val recorded:Boolean=false)

    /** Keep the first ceil(original working sets / 2) in each issued
     * occurrence, not half of what is left on every retry. Never touch
     * an observed entry. Skip unlocked technique extensions and do not
     * fabricate stages for a skipped activation. Unaffected work stays. */
    private fun <K> selectConcern(
        entries:List<ConcernEntry<K>>,action:WorkoutConcernAction
    ):List<K> {
        if(action==WorkoutConcernAction.SKIP)
            return entries.filterNot{it.recorded}.map{it.key}
        val working=entries.filter{!it.followUp && it.type==DraftSetType.WORKING}
        val kept=working.take((working.size+1)/2)
        val tail=working.drop(kept.size).map{it.key}.toSet()
        val retainsPendingWorking=kept.any{!it.recorded}
        return entries.filter{entry->
            !entry.recorded && (entry.followUp || entry.key in tail ||
                (entry.type==DraftSetType.WARMUP && !retainsPendingWorking))
        }.map{it.key}
    }

    private fun involvement(
        exerciseIds:Set<String>,credits:Map<String,List<DeclaredMuscleCredit>>
    ):Map<String,Set<TrainingMuscle>>? {
        if(exerciseIds.any{credits[it].isNullOrEmpty()})return null
        return exerciseIds.associateWith{id->
            credits.getValue(id).filter{it.fractionalSet.signum()>0}.map{it.muscle}.toSet()
        }.takeIf{map->map.values.none{it.isEmpty()}}
    }

    private fun <K> concernReviews(
        workoutId:String,revision:Long,groups:List<Pair<String,List<ConcernEntry<K>>>>,
        credits:Map<String,List<DeclaredMuscleCredit>>
    ):List<WorkoutConcernReview<K>> {
        val parts=involvement(groups.map{it.first}.toSet(),credits)?:return emptyList()
        return TrainingMuscle.entries.flatMap{muscle->
            WorkoutConcernAction.entries.mapNotNull{action->
                val affected=groups.filter{muscle in parts.getValue(it.first)}.mapNotNull{(id,entries)->
                    val selected=selectConcern(entries,action)
                    selected.takeIf{it.isNotEmpty()}?.let{
                        WorkoutConcernGroup(id,it,entries.count{entry->entry.recorded})
                    }
                }
                affected.takeIf{it.isNotEmpty()}?.let{
                    WorkoutConcernReview(workoutId,revision,muscle,action,it)
                }
            }
        }
    }

    fun generatedConcerns(journal:KotlinWorkoutJournalState,
        credits:Map<String,List<DeclaredMuscleCredit>>):List<WorkoutConcernReview<String>> {
        if(journal.finishedByEventId!=null)return emptyList()
        // Preserve A/B/A occurrences independently, even with equal IDs.
        val groups=KotlinWorkoutPresentationDomain.inspect(journal).exercises.map{exercise->
            exercise.exerciseId to exercise.sets.map{set->
                ConcernEntry(set.target.setKey,set.target.type,recorded=set.isResolved)
            }
        }
        return concernReviews(journal.snapshot.workoutId,journal.revision,groups,credits)
    }

    fun customConcerns(journal:CustomWorkoutJournalDraft,
        credits:Map<String,List<DeclaredMuscleCredit>>):List<WorkoutConcernReview<CustomWorkoutVisitKey>> {
        if(journal.finishedByEventId!=null)return emptyList()
        val order=CustomWorkoutVisitFocusDomain.visibleOrder(journal)
        val groups=journal.snapshot.slots.map{slot->
            val sets=slot.sets.associateBy{it.setKey}
            slot.exerciseId to order.filter{it.setKey in sets}.map{visit->
                ConcernEntry(visit,sets.getValue(visit.setKey).prescription.type,
                    visit.part==CustomWorkoutVisitPart.FOLLOW_UP,visit in journal.effectiveVisits)
            }
        }
        return concernReviews(journal.workoutId,journal.revision,groups,credits)
    }

    fun generated(
        journal:KotlinWorkoutJournalState,exerciseId:String
    ):RemainingExerciseReview<String>? {
        if(journal.finishedByEventId!=null ||
            !KotlinWorkoutPresentationDomain.hasSingleOccurrence(
                journal.snapshot.targets,exerciseId))return null
        val targets=journal.snapshot.targets.filter{it.exerciseId==exerciseId}
        if(targets.isEmpty())return null
        val remaining=targets.filter{it.setKey !in journal.effectiveSets}
        return RemainingExerciseReview(exerciseId,journal.revision,
            Collections.unmodifiableList(remaining.map{it.setKey}),
            remaining.count{it.type==DraftSetType.WARMUP},
            remaining.count{it.type==DraftSetType.WORKING},0,
            targets.size-remaining.size)
    }

    /** Scope by authored slot, since a custom day may repeat a movement.
     * Only COMPLETED mains unlock follow-ups. Skipping an unlogged main
     * does not fabricate a follow-up observation. Existing follow-ups and
     * main results, including failures and corrections, remain untouched. */
    fun custom(
        journal:CustomWorkoutJournalDraft,slotId:String
    ):RemainingExerciseReview<CustomWorkoutVisitKey>? {
        if(journal.finishedByEventId!=null)return null
        val slot=journal.snapshot.slots.singleOrNull{it.slotId==slotId}
            ?:return null
        val targets=slot.sets.associateBy{it.setKey}
        val visits=CustomWorkoutVisitFocusDomain.visibleOrder(journal)
            .filter{it.setKey in targets}
        val remaining=visits.filter{it !in journal.effectiveVisits}
        return RemainingExerciseReview(slot.exerciseId,journal.revision,
            Collections.unmodifiableList(remaining),
            remaining.count{it.part==CustomWorkoutVisitPart.MAIN &&
                targets.getValue(it.setKey).prescription.type==DraftSetType.WARMUP},
            remaining.count{it.part==CustomWorkoutVisitPart.MAIN &&
                targets.getValue(it.setKey).prescription.type==DraftSetType.WORKING},
            remaining.count{it.part==CustomWorkoutVisitPart.FOLLOW_UP},
            visits.size-remaining.size)
    }
}
