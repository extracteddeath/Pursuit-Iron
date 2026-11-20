package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import java.util.Collections

/**
 * Source-backed generation bridge for the otherwise separate Strength
 * working-set collapse. Does not manufacture a dose target: it only attempts
 * to cross the exact, previously reviewed half-dose warning using existing
 * STRENGTH prescriptions and their authored maximumSetsPerVisit.
 *
 * This is not an alternate coach, persistence authority, or a conversion of
 * hypertrophy loads to strength loads. Whole-cycle review and the athlete's
 * Adapt Sets permission remain mandatory after this candidate is returned.
 */
data class GeneratedStrengthDoseAddedSet(
    val weekday:Int,
    val slotId:String,
    val exerciseId:String
)

data class GeneratedStrengthDoseCandidate(
    val structure:CustomCycleReview?,
    val added:List<GeneratedStrengthDoseAddedSet>,
    val explanation:String
) {
    val mayIssueWorkout:Boolean get()=false
    val maySaveProgram:Boolean get()=false
}

object GeneratedStrengthDoseRecoveryDomain {
    private val zero=BigDecimal.ZERO
    private const val MAX_EXTRA_SETS=64

    fun preview(
        source:CustomCycleReview,
        targetBlockId:String,
        offered:List<CatalogExerciseChoice>,
        catalog:List<CatalogMovement>,
        selectedBounds:List<DeclaredMuscleDoseBounds>,
        available:List<CustomExerciseAvailability>,
        nonSetSecondsByWeekday:Map<Int,Int>,
        maximumSessionMinutes:Int?,
        excludedSlotIds:Set<String> = emptySet()
    ):GeneratedStrengthDoseCandidate {
        fun refuse(reason:String)=GeneratedStrengthDoseCandidate(
            null,emptyList(),reason)
        val original=source.verifiedDraft
        if(source.status!=CustomCycleReviewStatus.READY_FOR_COACH_REVIEW ||
            original==null || offered.map{it.exerciseId}.distinct().size!=offered.size ||
            catalog.map{it.exerciseId}.distinct().size!=catalog.size ||
            !TrainingMuscleCoverage.isComplete(selectedBounds.map{it.muscle}))
            return refuse("Exact generated cycle, source choices or phase dose bounds are not verified.")
        val boundary=StrengthDoseBoundaryGuardDomain.review(source,targetBlockId)
        if(boundary.status!=
            StrengthDoseBoundaryStatus.HALVED_WORKING_DOSE_REQUIRES_REVIEW)
            return refuse("This generated Strength week does not have a measured half-dose boundary to repair.")
        val block=original.blocks.singleOrNull{it.blockId==targetBlockId}
            ?:return refuse("Target block is missing.")
        if(block.phase!=TrainingPhase.STRENGTH ||
            block.purpose!=CustomBlockPurpose.TRAINING ||
            original.weekOverrides[targetBlockId].orEmpty().isNotEmpty())
            return refuse("Only untouched, uniform Strength templates without week overrides may be considered.")
        val choiceById=offered.associateBy{it.exerciseId}
        val catalogById=catalog.associateBy{it.exerciseId}
        val bounds=selectedBounds.associateBy{it.muscle}
        if(block.days.map{it.weekday}.toSet()!=nonSetSecondsByWeekday.keys ||
            nonSetSecondsByWeekday.values.any{it<0} ||
            maximumSessionMinutes!=null && maximumSessionMinutes !in 1..360)
            return refuse("The reviewed weekday overhead or session limit is unavailable.")

        val dayCount=block.days.associate{day ->
            day.weekday to day.slots.sumOf{slot ->
                slot.sets.count{it.type==DraftSetType.WORKING}
            }
        }.toMutableMap()
        val daySeconds=block.days.associate{day ->
            day.weekday to (
                nonSetSecondsByWeekday.getValue(day.weekday).toLong()+
                    day.slots.sumOf{slot->slot.sets.sumOf{set ->
                        set.activeSeconds.toLong()+set.restSeconds.toLong()
                    }}
                )
        }.toMutableMap()
        val credits=mutableMapOf<TrainingMuscle,BigDecimal>()
        for(day in block.days)for(slot in day.slots) {
            val movement=catalogById[slot.exerciseId]
                ?:return refuse("The Strength exercise has no reviewed catalog entry.")
            val work=slot.sets.count{it.type==DraftSetType.WORKING}
            for(credit in movement.credits) {
                credits[credit.muscle]=(credits[credit.muscle] ?:zero)
                    .add(credit.fractionalSet.multiply(BigDecimal(work)))
            }
        }
        val dayFloors=boundary.affectedDays.associate{loss ->
            loss.weekday to (loss.beforeWorkingSets/2+1)
        }
        val weeklyBefore=boundary.hypertrophyWorkingSets ?:return refuse(
            "A measured hypertrophy working-set baseline is missing.")
        val weeklyAfter=boundary.strengthWorkingSets ?:return refuse(
            "A measured strength working-set baseline is missing.")
        val weeklyFloor=if(weeklyBefore>=8 &&
            weeklyAfter.toLong()*2<=weeklyBefore)
            weeklyBefore/2+1 else 0
        val currentTotal={dayCount.values.sum()}
        val missing={currentTotal()<weeklyFloor ||
            dayFloors.any{(day,floor)->(dayCount[day] ?:0)<floor}}
        var candidate:CustomTrainingCycle=checkNotNull(original)
        val added=mutableListOf<GeneratedStrengthDoseAddedSet>()
        while(missing() && added.size<MAX_EXTRA_SETS) {
            val now=checkNotNull(candidate.blocks.singleOrNull{
                it.blockId==targetBlockId
            })
            val neededDays=dayFloors.filter{(day,floor)->
                (dayCount[day] ?:0)<floor
            }.keys
            data class Eligible(
                val weekday:Int,
                val slot:CustomExerciseSlot,
                val priority:Int,
                val existingSets:Int
            )
            val eligible=now.days.flatMap{day ->
                day.slots.mapNotNull{slot ->
                    val choice=choiceById[slot.exerciseId]
                    val movement=catalogById[slot.exerciseId]
                    val work=slot.sets.filter{it.type==DraftSetType.WORKING}
                    val one=work.firstOrNull()
                    if(neededDays.isNotEmpty() && day.weekday !in neededDays ||
                        slot.slotId in excludedSlotIds ||
                        slot.pinned || slot.coachNote.isNotBlank() ||
                        slot.supersetGroup!=null || slot.attachmentId!=null ||
                        slot.machineInstanceId!=null ||
                        choice==null || movement==null ||
                        movement.creditStatus!=
                            CatalogCreditStatus.AUTHOR_REVIEWED_PROVISIONAL ||
                        movement.creditReviewReference.isNullOrBlank() ||
                        work.isEmpty() || work.size!=slot.sets.size ||
                        work.size>=choice.maximumSetsPerVisit ||
                        slot.sets.size>=20 || one?.targetRir==null ||
                        work.any{it!=one || it.amplifier!=null})
                        return@mapNotNull null
                    val cost=one.activeSeconds.toLong()+
                        one.restSeconds.toLong()
                    val maxSeconds=maximumSessionMinutes?.times(60L)
                    if(maxSeconds!=null &&
                        (daySeconds[day.weekday] ?:Long.MAX_VALUE)+cost>
                            maxSeconds)return@mapNotNull null
                    if(movement.credits.any{credit ->
                        val ceiling=bounds[credit.muscle]?.mrv
                        ceiling==null || (credits[credit.muscle] ?:zero)
                            .add(credit.fractionalSet)>ceiling
                    })return@mapNotNull null
                    Eligible(day.weekday,slot,choice.priority,work.size)
                }
            }.sortedWith(compareBy<Eligible> {it.existingSets}
                .thenByDescending{it.priority}
                .thenBy{it.weekday}
                .thenBy{it.slot.slotId})
            val next=eligible.firstOrNull() ?:break
            val set=next.slot.sets.first()
            val movement=catalogById.getValue(next.slot.exerciseId)
            candidate=candidate.copy(blocks=candidate.blocks.map{old ->
                if(old.blockId!=targetBlockId)old
                else old.copy(days=old.days.map{day ->
                    if(day.weekday!=next.weekday)day
                    else day.copy(slots=day.slots.map{slot ->
                        if(slot.slotId!=next.slot.slotId)slot
                        else slot.copy(sets=slot.sets+set)
                    })
                })
            })
            dayCount[next.weekday]=checkNotNull(dayCount[next.weekday])+1
            daySeconds[next.weekday]=checkNotNull(daySeconds[next.weekday])+
                set.activeSeconds.toLong()+set.restSeconds.toLong()
            for(credit in movement.credits)
                credits[credit.muscle]=(credits[credit.muscle] ?:zero)
                    .add(credit.fractionalSet)
            added+=GeneratedStrengthDoseAddedSet(next.weekday,
                next.slot.slotId,next.slot.exerciseId)
        }
        if(missing() || added.isEmpty())
            return refuse("No complete dose recovery fits the authored Strength set ceilings, muscle MRV and session duration.")
        val inspected=CustomTrainingCycleDomain.review(candidate,available)
        val final=StrengthDoseBoundaryGuardDomain.review(
            inspected,targetBlockId)
        if(inspected.verifiedDraft==null ||
            final.status!=StrengthDoseBoundaryStatus.CONTINUOUS)
            return refuse("The exact source inventory or working-set continuity still fails review.")
        return GeneratedStrengthDoseCandidate(inspected,
            Collections.unmodifiableList(added.toList()),
            "Candidate adds ${added.size} existing strength-prescribed working sets to clear the actual source-backed half-dose boundary. Full-cycle coaching and athlete consent remain required.")
    }
}
