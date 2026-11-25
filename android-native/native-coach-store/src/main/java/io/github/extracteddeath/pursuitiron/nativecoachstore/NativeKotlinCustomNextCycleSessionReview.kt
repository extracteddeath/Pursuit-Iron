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
    /** Supporting workouts retain the authored targets, only within the
     * approved tier block and after the selected initial exposure. */
    fun isTieredSupportDay(cycle:CustomTrainingCycle,initial:NativeTieredInitialDraftReceipt,
        week:Int,weekday:Int):Boolean {
        val selection=initial.selection
        val block=cycle.blocks.singleOrNull{it.blockId==initial.blockId} ?:return false
        return weekday!=selection.weekday &&
            week in selection.absoluteWeek..(selection.absoluteWeek+block.weeks-initial.weekInBlock) &&
            (week!=selection.absoluteWeek || weekday>selection.weekday) &&
            block.days.any{it.weekday==weekday}
    }

    /** One factual mapping for both calendar discovery and supporting-day
     * preflight. The transition's derived revision is normalized only here,
     * never in the persisted preparation or its issuance proof. */
    fun tieredFact(cycle:CustomTrainingCycle,initial:NativeTieredInitialDraftReceipt,
        item:NativeCustomInventoryFact,original:NativeCustomPreparedWorkout,
        includeLater:Boolean):NativeCustomCycleSessionFact? {
        val selection=initial.selection
        val revision=original.snapshot.stamp.cycleRevision
        val offset=item.absoluteWeek-selection.absoluteWeek
        val block=cycle.blocks.singleOrNull{it.blockId==initial.blockId} ?:return null
        val transition=includeLater && original.authority==NativeCustomPreparationAuthority.TIERED_TRANSITION &&
            item.weekday==selection.weekday && offset in 1..(block.weeks-initial.weekInBlock) &&
            revision==cycle.revision+offset.toLong()
        val permitted=transition ||
            (original.authority==NativeCustomPreparationAuthority.TIERED_INITIAL &&
                item.workoutId==selection.nextWorkoutId && item.absoluteWeek==selection.absoluteWeek &&
                item.weekday==selection.weekday && revision==cycle.revision) ||
            (includeLater && original.authority==NativeCustomPreparationAuthority.TIERED_SUPPORT &&
                revision==cycle.revision && isTieredSupportDay(cycle,initial,item.absoluteWeek,item.weekday)) ||
            (original.authority==NativeCustomPreparationAuthority.CUSTOM_CYCLE &&
                item.completed && revision<cycle.revision)
        if(!permitted)return null
        return NativeCustomCycleSessionFact(item.workoutId,item.absoluteWeek,item.weekday,
            if(transition)cycle.revision else revision,item.issued,item.completed)
    }

    /** Authored support is permitted only on a day with no active tier
     * thread, inside a block containing reviewed roots. Never mistake a
     * later exposure on a tier weekday for an ordinary supporting day. */
    fun isMultiTieredSupportDay(cycle:CustomTrainingCycle,roots:List<NativeTieredInitialDraftReceipt>,
        week:Int,weekday:Int):Boolean {
        var offset=0
        for(block in cycle.blocks) {
            val end=offset+block.weeks
            if(week in (offset+1)..end) {
                val local=roots.filter{it.blockId==block.blockId}
                return local.isNotEmpty() && week>=local.minOf{it.selection.absoluteWeek} &&
                    block.days.any{it.weekday==weekday} &&
                    local.none{it.selection.weekday==weekday && it.selection.absoluteWeek<=week}
            }
            offset=end
        }
        return false
    }

    /** Map initial and authored support days from a multi-root signed
     * draft. Multiple slots share one journal; later transitions are
     * deliberately not inferred. */
    fun multiInitialFact(cycle:CustomTrainingCycle,roots:List<NativeTieredInitialDraftReceipt>,
        item:NativeCustomInventoryFact,origin:NativeCustomPreparedWorkout,
        consent:NativeKotlinCustomCycleSignoff):NativeCustomCycleSessionFact? {
        val revision=origin.snapshot.stamp.cycleRevision
        val matches=roots.any{r->r.selection.nextWorkoutId==item.workoutId &&
            r.selection.absoluteWeek==item.absoluteWeek && r.selection.weekday==item.weekday}
        val current=revision==cycle.revision && origin.consentRevision==consent.recordRevision &&
            origin.consentFingerprint==consent.fingerprintSha256
        val valid=when(origin.authority) {
            NativeCustomPreparationAuthority.TIERED_INITIAL -> matches && current
            NativeCustomPreparationAuthority.TIERED_SUPPORT -> current &&
                roots.none{it.selection.nextWorkoutId==item.workoutId} &&
                isMultiTieredSupportDay(cycle,roots,item.absoluteWeek,item.weekday) &&
                origin.snapshot.slots.none{slot->slot.sets.any{it.prescription.percentageIntent!=null}}
            NativeCustomPreparationAuthority.CUSTOM_CYCLE -> item.completed && revision<cycle.revision
            else -> false
        }
        if(!valid)return null
        return NativeCustomCycleSessionFact(item.workoutId,item.absoluteWeek,item.weekday,
            revision,item.issued,item.completed)
    }

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
    private val prepared:NativeKotlinCustomWorkoutPreparedStore,
    private val machineRosters:NativeKotlinMachineRosterFileStore?=null
) {
    fun review(ownerKey:String,cycleId:String,
        inputs:CustomCoachAuditInputs):NativeCustomNextCycleSlot =
        reviewInternal(ownerKey,cycleId,inputs,false)

    /** Initial tier only. No transition or supporting-day authority. */
    fun reviewTieredInitial(ownerKey:String,cycleId:String,
        inputs:CustomCoachAuditInputs):NativeCustomNextCycleSlot =
        reviewInternal(ownerKey,cycleId,inputs,true)

    /** Classify a specific multi-root calendar position using the same
     * policy as preparation and issuance. This is display-only. */
    fun isMultiTieredSupportDay(ownerKey:String,cycleId:String,week:Int,weekday:Int):Boolean =
        runCatching{fence.withOwnerTransaction(ownerKey){
            val stored=drafts.read(ownerKey,cycleId) as? NativeCustomDraftReadResult.Found
                ?:return@withOwnerTransaction false
            stored.tieredInitialReceipts.size>1 &&
                NativeCustomNextCycleSlotDomain.isMultiTieredSupportDay(stored.draft,stored.tieredInitialReceipts,week,weekday)
        }}.getOrDefault(false)

    /** Multi-root schedule uses the authenticated initial review and full
     * census. Transition facts require their exact durable day receipts;
     * calendar discovery never grants preparation or issuance. */
    fun reviewMultiTieredInitial(ownerKey:String,cycleId:String,
        transitionConsent:NativeKotlinFirstTierTransitionConsentStore?=null):NativeCustomNextCycleSlot {
        fun no()=NativeCustomNextCycleSlot.Rejected("Current multi-thread approval or calendar is unavailable.")
        return runCatching{fence.withOwnerTransaction(ownerKey){
            val owner=identity.signedInIdentity()?.takeIf{it.ownerKey==ownerKey}
                ?:return@withOwnerTransaction no()
            val signed=signoffs.recoverTieredInitialReview(ownerKey,cycleId,history,inventory,machineRosters)
                as? NativeSavedNamedReview.Signed ?:return@withOwnerTransaction no()
            if(signed.initialTierReceipts.size<2)return@withOwnerTransaction no()
            val timeline=(history.read(ownerKey,owner.athleteId)
                as? NativeKotlinMixedHistoryReadResult.Review)?.timeline ?:return@withOwnerTransaction no()
            if(timeline.manifestRevision!=signed.historyRevision)return@withOwnerTransaction no()
            val completed=timeline.completions.filterIsInstance<NativeKotlinMixedHistoryCompletion.Custom>()
                .map{it.manifest.workoutId}.toSet()
            val census=(inventory.inspect(ownerKey,owner.athleteId,completed) as? NativeCustomInventoryReview.Reviewed)
                ?:return@withOwnerTransaction no()
            val facts=census.sessions.filter{it.cycleId==cycleId}.map{item->
                val origin=inventory.preparedOrigin(ownerKey,item.workoutId)?:return@withOwnerTransaction no()
                val fact=if(origin.authority==NativeCustomPreparationAuthority.TIERED_TRANSITION)
                    transitionConsent?.multiTransitionCalendarFact(signed,item,origin)
                else NativeCustomNextCycleSlotDomain.multiInitialFact(
                    signed.draft,signed.initialTierReceipts,item,origin,signed.consent)
                fact ?:return@withOwnerTransaction no()
            }
            if(identity.signedInIdentity()!=owner)return@withOwnerTransaction no()
            NativeCustomNextCycleSlotDomain.select(signed.draft,facts)
        }}.getOrElse{no()}
    }

    /** Read-only schedule discovery after initial approval, including after
     * Finish. This is not a specialist preview, preparation or issue grant.
     * The actual transition reviewer still authenticates every receipt. */
    fun reviewTieredCalendar(ownerKey:String,cycleId:String):NativeCustomNextCycleSlot {
        val inputs=signoffs.signedTieredInputs(ownerKey,cycleId)
            ?:return NativeCustomNextCycleSlot.Rejected("Current tiered approval is unavailable.")
        return reviewInternal(ownerKey,cycleId,inputs,true,calendarOnly=true)
    }

    private fun reviewInternal(ownerKey:String,cycleId:String,
        inputs:CustomCoachAuditInputs,tiered:Boolean,calendarOnly:Boolean=false):NativeCustomNextCycleSlot {
        fun no(message:String)=NativeCustomNextCycleSlot.Rejected(message)
        return runCatching {
            fence.withOwnerTransaction(ownerKey) {
                val owner=identity.signedInIdentity()?.takeIf {
                    it.ownerKey==ownerKey
                }?:return@withOwnerTransaction no(
                    "No verified active athlete.")
                val stored=(drafts.read(ownerKey,cycleId)
                    as? NativeCustomDraftReadResult.Found)
                    ?:return@withOwnerTransaction no(
                        "The saved cycle could not be verified.")
                val cycle=stored.draft
                val gym=(source.read(ownerKey)
                    as? NativeAuthoringSourceRead.Found)?.source
                    ?:return@withOwnerTransaction no(
                        "Saved athlete and equipment setup is unavailable.")
                if(cycle.athleteId!=owner.athleteId ||
                    gym.athleteId!=owner.athleteId)
                    return@withOwnerTransaction no(
                        "Saved program belongs to a different athlete.")
                // A fixed named cycle has a different signed fingerprint.
                // Never pretend its typed receipts are ordinary consent or
                // let a stale named record fall back to ordinary review.
                val fixedNamed=stored.namedReceipts.isNotEmpty()
                val initial=stored.tieredInitialReceipt
                if((stored.tieredInitialReceipts.isNotEmpty())!=tiered ||
                    (tiered && (fixedNamed || initial==null)))
                    return@withOwnerTransaction no(
                        "Tiered progression requires its dedicated reference or transition review.")
                if(calendarOnly) {
                    if(signoffs.signedTieredInputs(ownerKey,cycleId)!=inputs)
                        return@withOwnerTransaction no("Current tiered approval is unavailable.")
                } else if(tiered) {
                    val reviewed=signoffs.recoverTieredInitialReview(ownerKey,cycleId,
                        history,inventory,machineRosters) as? NativeSavedNamedReview.Signed
                        ?:return@withOwnerTransaction no("Current initial tier approval is unavailable or completed.")
                    if(reviewed.draft!=cycle || reviewed.inputs!=inputs ||
                        reviewed.initialTierReceipt!=initial ||
                        reviewed.stamp.gymCatalogSourceRevision!=gym.revision)
                        return@withOwnerTransaction no("Initial tier source changed.")
                } else if(fixedNamed) {
                    val reviewed=NativeKotlinSavedNamedPercentageReview(
                        fence,identity,drafts,source,signoffs,history,inventory,machineRosters)
                        .reviewSigned(ownerKey,cycleId,inputs) as? NativeSavedNamedReview.Signed
                        ?:return@withOwnerTransaction no(
                            "Review and sign this exact named cycle first.")
                    if(reviewed.draft!=cycle ||
                        reviewed.stamp.gymCatalogSourceRevision!=gym.revision)
                        return@withOwnerTransaction no("Named cycle source changed.")
                } else {
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
                }
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
                    if(tiered) {
                        val receipt=initial ?:return@withOwnerTransaction no("Missing initial tier reference.")
                        NativeCustomNextCycleSlotDomain.tieredFact(cycle,receipt,item,original,calendarOnly)
                            ?:return@withOwnerTransaction no("A workout uses a separate or earlier training approval.")
                    } else {
                        val permitted=if(fixedNamed)
                            original.authority==NativeCustomPreparationAuthority.NAMED_CYCLE ||
                            (original.authority==NativeCustomPreparationAuthority.CUSTOM_CYCLE &&
                                item.completed && original.snapshot.stamp.cycleRevision<cycle.revision)
                        else original.authority==NativeCustomPreparationAuthority.CUSTOM_CYCLE
                        if(!permitted)return@withOwnerTransaction no("A workout uses a separate or earlier training approval.")
                        NativeCustomCycleSessionFact(item.workoutId,item.absoluteWeek,item.weekday,
                            original.snapshot.stamp.cycleRevision,item.issued,item.completed)
                    }
                }
                if(identity.signedInIdentity()!=owner)
                    return@withOwnerTransaction no("Active athlete changed.")
                val next=NativeCustomNextCycleSlotDomain.select(cycle,facts)
                if(!tiered || calendarOnly)return@withOwnerTransaction next
                val exact=checkNotNull(initial).selection
                val matches=when(next) {
                    is NativeCustomNextCycleSlot.Next ->
                        next.absoluteWeek==exact.absoluteWeek && next.weekday==exact.weekday
                    is NativeCustomNextCycleSlot.Prepared ->
                        next.workoutId==exact.nextWorkoutId && next.absoluteWeek==exact.absoluteWeek &&
                            next.weekday==exact.weekday
                    is NativeCustomNextCycleSlot.ResumeIssued ->
                        next.workoutId==exact.nextWorkoutId && next.absoluteWeek==exact.absoluteWeek &&
                            next.weekday==exact.weekday
                    else -> false
                }
                if(matches)next else no("The next scheduled day is not the approved initial tier session. Supporting-day or transition review is required.")
            }
        }.getOrElse {
            no("Native next-workout history review was unavailable.")
        }
    }
}
