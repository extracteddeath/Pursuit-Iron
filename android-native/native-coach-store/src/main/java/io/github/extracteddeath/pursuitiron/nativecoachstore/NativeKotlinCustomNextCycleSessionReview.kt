package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.util.Collections

/**
 * The "next" action means next in the reviewed SAVED cycle sequence, not
 * "today": onboarding does not establish an actual cycle start date.
 * Selection never issues, logs, marks finished, or skips a completed workout
 * based on an unverified UI flag or calendar assumption.
 */
data class NativeCustomCycleSessionFact(
    val workoutId:String,
    val absoluteWeek:Int,
    val weekday:Int,
    val cycleRevision:Long,
    val issued:Boolean,
    val completed:Boolean
)
sealed interface NativeCustomNextCycleSlot {
    data class Next(
        val absoluteWeek:Int,val weekday:Int,val dayLabel:String
    ):NativeCustomNextCycleSlot
    data class Prepared(
        val workoutId:String,val absoluteWeek:Int,val weekday:Int,
        val dayLabel:String
    ):NativeCustomNextCycleSlot
    data class ResumeIssued(
        val workoutId:String,val absoluteWeek:Int,val weekday:Int,
        val dayLabel:String
    ):NativeCustomNextCycleSlot
    data object CycleComplete:NativeCustomNextCycleSlot
    data class Rejected(val reason:String):NativeCustomNextCycleSlot
}

internal object NativeCustomNextCycleSlotDomain {
    /** Called only with already owner-verified full manifest/census evidence. */
    fun select(cycle:CustomTrainingCycle,
        facts:List<NativeCustomCycleSessionFact>):NativeCustomNextCycleSlot {
        fun refuse(message:String)=NativeCustomNextCycleSlot.Rejected(message)
        if(cycle.blocks.isEmpty() ||
            cycle.blocks.sumOf{it.weeks} !in 1..104 ||
            cycle.blocks.any {it.weeks !in 1..52 ||
                it.days.isEmpty() ||
                it.days.map{d->d.weekday}.distinct().size!=it.days.size ||
                it.days.any{d->d.weekday !in 0..6}})
            return refuse("The saved training-cycle calendar is incomplete.")
        // A FINISHED old-revision workout is still a real occurrence of
        // its scheduled day after the athlete edits and re-signs future work.
        // Otherwise an approved Week 2 coaching edit would make the app
        // forget Week 1 and block every remaining training session.
        // Never grant coaching comparability from that old revision: this
        // selector only marks completed calendar positions.
        if(facts.any{
                it.cycleRevision !in 1..cycle.revision ||
                it.absoluteWeek !in 1..cycle.blocks.sumOf{b->b.weeks} ||
                it.weekday !in 0..6 || it.completed&&!it.issued
            } ||
            facts.map{it.absoluteWeek to it.weekday}.distinct().size!=facts.size)
            return refuse("Training history contains a future or invalid revision, or an ambiguous repeated session. Choose a day manually.")
        val schedule=linkedMapOf<Pair<Int,Int>,String>()
        var week=0
        for(block in cycle.blocks)repeat(block.weeks) {
            week++
            for(day in block.days.sortedBy{it.weekday})
                schedule[week to day.weekday]=day.label
        }
        val byDay=facts.associateBy{it.absoluteWeek to it.weekday}
        if(byDay.keys.any{it !in schedule})
            return refuse("A previously prepared workout is no longer in this saved training calendar. Review the changed program.")
        // A not-yet-finished old-revision workout must be resolved using
        // its original immutable issued snapshot, never silently reissued
        // with the new cycle's targets. Train can still locate it from the
        // complete native pending-workout inventory.
        if(facts.any{!it.completed && it.cycleRevision!=cycle.revision})
            return refuse("An unfinished workout belongs to an earlier program revision. Resume or resolve that workout before continuing this revised cycle.")
        // Existing issued work outranks a new session even when someone
        // trained out of order. Prepared-only sessions come next; never
        // create a duplicate while a cold-recoverable preparation exists.
        val outstanding=facts.filter{!it.completed}.sortedWith(
            compareByDescending<NativeCustomCycleSessionFact>{it.issued}
                .thenBy{it.absoluteWeek}.thenBy{it.weekday})
            .firstOrNull()
        if(outstanding!=null) {
            val label=checkNotNull(schedule[
                outstanding.absoluteWeek to outstanding.weekday])
            return if(outstanding.issued)
                NativeCustomNextCycleSlot.ResumeIssued(
                    outstanding.workoutId,outstanding.absoluteWeek,
                    outstanding.weekday,label)
            else NativeCustomNextCycleSlot.Prepared(
                outstanding.workoutId,outstanding.absoluteWeek,
                outstanding.weekday,label)
        }
        for((slot,label) in schedule)if(slot !in byDay)
            return NativeCustomNextCycleSlot.Next(slot.first,slot.second,label)
        return NativeCustomNextCycleSlot.CycleComplete
    }
}

/**
 * Owner-fenced read of source, exact cycle, complete signed coaching review,
 * committed mixed history AND the full prepared/issued census. No guessed
 * dates or automated workouts. A stale custom draft cannot reuse consent.
 */
class NativeKotlinCustomNextCycleSessionReview(
    private val fence:NativeKotlinApprovalRevisionFence,
    private val identity:NativeKotlinCurrentIdentity,
    private val drafts:NativeCustomCycleDraftFileStore,
    private val source:NativeKotlinCustomAuthoringSourceFileStore,
    private val signoffs:NativeKotlinCustomCycleSignoffFileStore,
    private val history:NativeKotlinMixedHistoryReadCoordinator,
    private val inventory:NativeKotlinCustomOutstandingWorkoutInventory,
    private val prepared:NativeKotlinCustomWorkoutPreparedStore
) {
    fun review(ownerKey:String,cycleId:String,
        inputs:CustomCoachAuditInputs):NativeCustomNextCycleSlot {
        fun no(message:String)=NativeCustomNextCycleSlot.Rejected(message)
        return runCatching {
            fence.withOwnerTransaction(ownerKey) {
                val owner=identity.signedInIdentity()?.takeIf {
                    it.ownerKey==ownerKey
                }?:return@withOwnerTransaction no(
                    "No verified active athlete.")
                val cycle=(drafts.read(ownerKey,cycleId)
                    as? NativeCustomDraftReadResult.Found)?.draft
                    ?:return@withOwnerTransaction no(
                        "The saved cycle could not be verified.")
                val gym=(source.read(ownerKey)
                    as? NativeAuthoringSourceRead.Found)?.source
                    ?:return@withOwnerTransaction no(
                        "Saved athlete and equipment setup is unavailable.")
                if(cycle.athleteId!=owner.athleteId ||
                    gym.athleteId!=owner.athleteId)
                    return@withOwnerTransaction no(
                        "Saved program belongs to a different athlete.")
                val audit=CustomCycleCoachAuditDomain.review(
                    cycle,gym.setup,gym.catalog,inputs)
                if(audit.status!=CustomCoachAuditStatus.READY_FOR_ATHLETE_REVIEW ||
                    audit.issues.isNotEmpty())
                    return@withOwnerTransaction no(
                        "The full-cycle coaching policy needs a current review.")
                val consent=(signoffs.read(ownerKey,cycleId,inputs)
                    as? NativeCustomSignoffRead.Found)
                if(consent?.isCurrent!=true)
                    return@withOwnerTransaction no(
                        "Review and sign this exact cycle first.")
                val timeline=(history.read(ownerKey,owner.athleteId)
                    as? NativeKotlinMixedHistoryReadResult.Review)?.timeline
                    ?:return@withOwnerTransaction no(
                        "Native workout history is not enrolled or verified.")
                val completed=timeline.completions.filterIsInstance<
                    NativeKotlinMixedHistoryCompletion.Custom>()
                    .map{it.manifest.workoutId}.toSet()
                val census=(inventory.inspect(ownerKey,owner.athleteId,completed)
                    as? NativeCustomInventoryReview.Reviewed)
                    ?:return@withOwnerTransaction no(
                        "Prepared and issued workout history cannot be reconciled.")
                val records=(prepared.inventory(ownerKey)
                    as? NativePreparedInventoryResult.Found)?.workouts
                    ?:return@withOwnerTransaction no(
                        "Saved workout preparations could not be verified.")
                val byId=records.associateBy{it.workoutId}
                if(byId.size!=records.size ||
                    census.sessions.map{it.workoutId}.toSet()!=byId.keys)
                    return@withOwnerTransaction no(
                        "Workout preparation and history inventory disagree.")
                val facts=census.sessions.filter{it.cycleId==cycleId}.map{item->
                    val original=byId[item.workoutId]
                        ?:return@withOwnerTransaction no(
                            "Original issued targets are unavailable.")
                    if(original.authority!=NativeCustomPreparationAuthority.CUSTOM_CYCLE)
                        return@withOwnerTransaction no(
                            "An activated cycle uses a separate training approval.")
                    NativeCustomCycleSessionFact(
                        item.workoutId,item.absoluteWeek,item.weekday,
                        original.snapshot.stamp.cycleRevision,
                        item.issued,item.completed)
                }
                if(identity.signedInIdentity()!=owner)
                    return@withOwnerTransaction no("Active athlete changed.")
                NativeCustomNextCycleSlotDomain.select(cycle,facts)
            }
        }.getOrElse {
            no("Native next-workout history review was unavailable.")
        }
    }
}
