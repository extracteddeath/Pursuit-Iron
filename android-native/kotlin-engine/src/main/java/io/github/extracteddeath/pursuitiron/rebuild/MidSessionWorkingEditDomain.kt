package io.github.extracteddeath.pursuitiron.rebuild

import java.util.Collections

/** Explicit single-session edit of ordinary working rows. This is a review,
 * not an issuance, a journal write, a future-program approval or a new
 * progression calculator. Both native workout types use the same policy. */
data class MidSessionWorkingEdit(
    val workoutId:String,
    val expectedRevision:Long,
    /** Generated exercise ID or exact custom slot ID (never a display ordinal). */
    val occurrenceId:String,
    /** Total working rows, including already resolved working rows. */
    val workingSetCount:Int,
    val reps:RepBand?=null,
    val restSeconds:Int?=null
)
enum class MidSessionWorkingEditRefusal {
    STALE_WORKOUT, FINISHED, UNKNOWN_OCCURRENCE, INVALID_REQUEST,
    PROTECTED_PROTOCOL, RESOLVED_WORK, NO_PENDING_WORK, SESSION_LIMIT, NO_CHANGE
}
sealed interface MidSessionWorkingEditReview<out T> {
    data class Rejected(val reason:MidSessionWorkingEditRefusal):
        MidSessionWorkingEditReview<Nothing>
    data class Reviewed<T>(
        val request:MidSessionWorkingEdit,
        val before:List<T>,
        val after:List<T>,
        val addedSetKeys:List<String>,
        val omittedSetKeys:List<String>,
        /** Existing factual work is untouched; added rows have no actuals. */
        val resolvedSetKeys:List<String>
    ):MidSessionWorkingEditReview<T> {
        val mayAppendJournal:Boolean get()=false
        val maySaveProgram:Boolean get()=false
    }
}
/** Derived from the immediately preceding adjustment receipt, not
 * serialized as a separate authority. Any other operation closes Undo. */
data class MidSessionUndo<T>(val eventId:String,val before:T)

object MidSessionWorkingEditDomain {
    /** Revision-scoped identity accepted only in a replay-verified effective snapshot. */
    fun isAddedSetKey(key:String):Boolean =
        Regex("^mid-[1-9][0-9]{0,15}-[1-9][0-9]?$").matches(key)
    private fun refused(reason:MidSessionWorkingEditRefusal)=MidSessionWorkingEditReview.Rejected(reason)
    private data class Row<T>(
        val value:T,val key:String,val type:DraftSetType,val ordinal:Int,
        val load:DraftSetLoad,val reps:RepBand,val rir:Int?,val active:Int,val rest:Int
    )
    private fun <T> review(
        request:MidSessionWorkingEdit,
        rows:List<Row<T>>,allKeys:Set<String>,resolved:Set<String>,
        protected:Boolean,allowReduction:Boolean,maxKeyLength:Int,
        currentSetCount:Int,previouslyAdjusted:Boolean,maxOrdinal:Int,
        copy:(T,String,Int,RepBand,Int)->T
    ):MidSessionWorkingEditReview<T> {
        if(rows.isEmpty())return refused(MidSessionWorkingEditRefusal.UNKNOWN_OCCURRENCE)
        if(protected)return refused(MidSessionWorkingEditRefusal.PROTECTED_PROTOCOL)
        val band=request.reps
        if(request.workingSetCount !in 1..20 ||
            band?.let{!KotlinSetNumberAuthorityDomain.validCompletedReps(it.minimum) ||
                !KotlinSetNumberAuthorityDomain.validCompletedReps(it.maximum) ||
                it.minimum>it.maximum}==true ||
            request.restSeconds?.let{it !in 0..3600}==true)
            return refused(MidSessionWorkingEditRefusal.INVALID_REQUEST)
        val working=rows.filter{it.type==DraftSetType.WORKING}
        if(working.isEmpty() || rows.any{!KotlinSetNumberAuthorityDomain.validIssuedLoad(it.load)})
            return refused(MidSessionWorkingEditRefusal.PROTECTED_PROTOCOL)
        // Only ordinary uniform working prescriptions can be edited by this
        // path. Do not flatten a backoff, pyramid, or specialist protocol.
        val pending=working.filter{it.key !in resolved}
        if(pending.isEmpty())return refused(MidSessionWorkingEditRefusal.NO_PENDING_WORK)
        val template=pending.last()
        if(working.any{!KotlinSetNumberAuthorityDomain.sameIssuedTarget(it.load,template.load) ||
                it.rir!=template.rir || it.active!=template.active} ||
            (if(previouslyAdjusted)pending else working).any{
                it.reps!=template.reps || it.rest!=template.rest
            } || rows.dropWhile{it.type==DraftSetType.WARMUP}.any{it.type!=DraftSetType.WORKING})
            return refused(MidSessionWorkingEditRefusal.PROTECTED_PROTOCOL)
        val difference=request.workingSetCount-working.size
        if(difference<0 && !allowReduction)return refused(MidSessionWorkingEditRefusal.PROTECTED_PROTOCOL)
        if(difference<0 && -difference>pending.size)
            return refused(MidSessionWorkingEditRefusal.RESOLVED_WORK)
        if(currentSetCount+difference>256 || rows.size+difference>20)
            return refused(MidSessionWorkingEditRefusal.SESSION_LIMIT)
        // Remove only a suffix of working rows. A later resolved row (even
        // a skip or failed attempt) prevents removing a row ahead of it.
        val omitted=if(difference<0)working.takeLast(-difference) else emptyList()
        if(omitted.any{it.key in resolved})return refused(MidSessionWorkingEditRefusal.RESOLVED_WORK)
        val omittedKeys=omitted.map{it.key}
        val retained=rows.filter{it.key !in omittedKeys}
        val next=retained.map{row->
            if(row.type!=DraftSetType.WORKING || row.key in resolved)row.value
            else copy(row.value,row.key,row.ordinal,band?:row.reps,request.restSeconds?:row.rest)
        }.toMutableList()
        val added=mutableListOf<String>()
        if(difference>0) {
            // Revision-scoped keys never replace an existing actual or
            // original ordinal. They are not set facts until actually logged.
            val lastOrdinal=rows.maxOf{it.ordinal}
            if(lastOrdinal>maxOrdinal-difference)return refused(MidSessionWorkingEditRefusal.SESSION_LIMIT)
            repeat(difference){i->
                val key="mid-${request.expectedRevision+1}-${i+1}"
                if(key.length>maxKeyLength || key in allKeys)
                    return refused(MidSessionWorkingEditRefusal.INVALID_REQUEST)
                added.add(key)
                next.add(copy(template.value,key,lastOrdinal+i+1,band?:template.reps,
                    request.restSeconds?:template.rest))
            }
        }
        if(next==rows.map{it.value})return refused(MidSessionWorkingEditRefusal.NO_CHANGE)
        return MidSessionWorkingEditReview.Reviewed(request,
            Collections.unmodifiableList(rows.map{it.value}),Collections.unmodifiableList(next),
            Collections.unmodifiableList(added),Collections.unmodifiableList(omittedKeys),
            Collections.unmodifiableList(rows.filter{it.key in resolved}.map{it.key}))
    }

    fun generated(state:KotlinWorkoutJournalState,request:MidSessionWorkingEdit):
        MidSessionWorkingEditReview<KotlinWorkoutTarget> {
        if(state.snapshot.workoutId!=request.workoutId || state.revision!=request.expectedRevision ||
            request.expectedRevision !in 0 until 9_007_199_254_740_991L)
            return refused(MidSessionWorkingEditRefusal.STALE_WORKOUT)
        if(state.finishedByEventId!=null)return refused(MidSessionWorkingEditRefusal.FINISHED)
        val rows=state.snapshot.targets.filter{it.exerciseId==request.occurrenceId}
        return review(request,rows.map{Row(it,it.setKey,it.type,it.ordinal,it.load,
            it.reps,it.targetRir,it.activeSeconds,it.restSeconds)},
            (state.originalSnapshot.targets+state.snapshot.targets).map{it.setKey}.toSet(),
            state.effectiveSets.keys,
            protected=state.snapshot.purpose!=ComposerSessionPurpose.HYPERTROPHY ||
                state.snapshot.shortSessionLimitMinutes!=null,
            allowReduction=rows.all{it.role==ComposerRole.ACCESSORY},maxKeyLength=100,
            currentSetCount=state.snapshot.targets.size,
            previouslyAdjusted=state.receipts.any{
                (it.operation as? KotlinJournalOperation.AdjustRemaining)?.request?.occurrenceId==request.occurrenceId
            },maxOrdinal=255
        ){row,key,ordinal,reps,rest->row.copy(setKey=key,ordinal=ordinal,reps=reps,restSeconds=rest)}
    }

    /** Recompute effective session timing from the original approved
     * estimate and exact set-time delta, avoiding accumulated rounding. */
    /** Independently review the complete approved week with this ONE
     * effective day substituted. Only the existing muscle-dose/time auditor
     * computes capacity. Never treat a UI-supplied estimate as approval.
     * No mixed-swap or specialist-day adjustments in this initial lane. */
    fun approvedGenerated(
        state:KotlinWorkoutJournalState,request:MidSessionWorkingEdit,
        program:ComposerRequest,policies:List<ExercisePrescriptionPolicy>,
        sameSourcePeers:List<KotlinWorkoutJournalState>
    ):Boolean = runCatching {
        if(state.receipts.any{it.operation is KotlinJournalOperation.SwapExercise ||
                it.operation is KotlinJournalOperation.UndoSwap} ||
            !KotlinWorkoutPresentationDomain.hasSingleOccurrence(state.snapshot.targets,request.occurrenceId))
            return@runCatching false
        val next=generatedSnapshot(state,request)?:return@runCatching false
        val approved=CoachSessionPrescriptionDomain.draft(program,policies)
        if(approved.status!=PrescriptionDraftStatus.COMPLETE_FOR_REVIEW ||
            approved.finalAudit?.satisfiesDeclaredHardConstraints!=true)return@runCatching false
        val day=approved.sessions.singleOrNull{it.dayIndex==next.dayIndex}
            ?:return@runCatching false
        if(day.purpose!=next.purpose || program.days.getOrNull(next.dayIndex)?.calendarDay!=next.calendarDay)
            return@runCatching false
        val options=program.exercises.associateBy{it.exerciseId}
        if(options.size!=program.exercises.size)return@runCatching false
        val groups=next.targets.groupBy{it.exerciseId}
        if(groups.keys.toList()!=day.exercises.map{it.exerciseId})return@runCatching false
        val revised=day.exercises.map{original->
            val option=options[original.exerciseId]?:return@runCatching false
            val rows=groups.getValue(original.exerciseId)
            val working=rows.filter{it.type==DraftSetType.WORKING}
            val policy=CoachSessionPrescriptionDomain.selectedPolicy(policies,original.exerciseId,
                day.purpose,day.dayIndex)?:return@runCatching false
            if(working.size !in option.minimumSetsPerVisit..option.maximumSetsPerVisit ||
                rows.any{it.role!=option.role || it.purpose!=day.purpose})return@runCatching false
            if(original.exerciseId==request.occurrenceId) {
                if(policy.scheme!=SetLoadScheme.STRAIGHT)return@runCatching false
                val band=option.sourcePracticalReps?:policy.workingReps
                // Reps may change within reviewed practical range, but an
                // increased upper bound needs a new active-duration review.
                if(working.any{it.reps.minimum<band.minimum || it.reps.maximum>band.maximum ||
                        it.reps.maximum>policy.workingReps.maximum ||
                        it.restSeconds<policy.restSecondsPerSet})return@runCatching false
            }
            for(row in rows) {
                val matches=when(val equipment=option.equipment) {
                    ComposerEquipment.Bodyweight -> row.load==DraftSetLoad.ExplicitBodyweight
                    is ComposerEquipment.Verified -> {
                        val load=row.load as? DraftSetLoad.Verified?:return@runCatching false
                        val inspected=EquipmentLoadDomain.inspect(equipment.inventory,load.value,load.unit)
                        load.unit==equipment.unit && load.basis==equipment.inventory?.basis &&
                            inspected is LoadInspection.Confirmed && inspected.exact && inspected.basis==load.basis
                    }
                }
                if(!matches)return@runCatching false
            }
            original.copy(sets=rows.map{row->DraftPrescribedSet(row.setKey,row.type,row.ordinal,row.load,
                row.reps,row.targetRir,row.activeSeconds,row.restSeconds)})
        }
        // Generated grants identify a reviewed week by program/source
        // revision, rather than the custom-cycle absolute-week field.
        // Never infer a second occurrence of the same day to be another week.
        val peers=sameSourcePeers.filter{it.snapshot.workoutId!=state.snapshot.workoutId}
        if(peers.any{it.originalSnapshot.source!=state.originalSnapshot.source ||
                it.snapshot.source!=state.originalSnapshot.source ||
                it.snapshot.dayIndex==day.dayIndex ||
                it.receipts.any{receipt->receipt.operation is KotlinJournalOperation.SwapExercise ||
                    receipt.operation is KotlinJournalOperation.UndoSwap}} ||
            peers.map{it.snapshot.dayIndex}.distinct().size!=peers.size)
            return@runCatching false
        val peerDays=peers.associateBy{it.snapshot.dayIndex}
        if(peerDays.keys.any{key->approved.sessions.none{it.dayIndex==key}})
            return@runCatching false
        val sessions=approved.sessions.map{planned->
            if(planned.dayIndex==day.dayIndex)day.copy(exercises=revised)
            else {
                val peer=peerDays[planned.dayIndex]
                if(peer==null)planned
                else {
                    val prior=peer.snapshot
                    if(prior.purpose!=planned.purpose ||
                        program.days.getOrNull(prior.dayIndex)?.calendarDay!=prior.calendarDay)
                        return@runCatching false
                    // A short day cannot supply extra capacity; use the
                    // full planned dose. A changed shortened snapshot is
                    // not supported by this ordinary-workout audit.
                    if(prior.shortSessionLimitMinutes!=null) {
                        if(prior!=peer.originalSnapshot)return@runCatching false
                        planned
                    } else {
                        val priorGroups=prior.targets.groupBy{it.exerciseId}
                        if(priorGroups.keys.toList()!=planned.exercises.map{it.exerciseId})
                            return@runCatching false
                        planned.copy(exercises=planned.exercises.map{original->
                            val rows=priorGroups.getValue(original.exerciseId)
                            val option=options[original.exerciseId]?:return@runCatching false
                            val working=rows.count{it.type==DraftSetType.WORKING}
                            if(rows.any{it.role!=option.role || it.purpose!=planned.purpose} ||
                                working>option.maximumSetsPerVisit)return@runCatching false
                            // Do not spend prior reductions or skipped work
                            // as newly available weekly training capacity.
                            if(working<original.sets.count{it.type==DraftSetType.WORKING})original
                            else original.copy(sets=rows.map{row->DraftPrescribedSet(
                                row.setKey,row.type,row.ordinal,row.load,row.reps,
                                row.targetRir,row.activeSeconds,row.restSeconds)})
                        })
                    }
                }
            }
        }
        CoachSessionPrescriptionDomain.auditPrescription(approved.composer,options,sessions)
            .satisfiesDeclaredHardConstraints
    }.getOrDefault(false)

    internal fun generatedSnapshot(state:KotlinWorkoutJournalState,request:MidSessionWorkingEdit):KotlinStagedWorkout? {
        val review=generated(state,request) as? MidSessionWorkingEditReview.Reviewed ?:return null
        val old=state.snapshot
        val start=old.targets.indexOfFirst{it.exerciseId==request.occurrenceId}
        if(start<0)return null
        // The existing generated projection is grouped by exercise.
        if(old.targets.drop(start).take(review.before.size)!=review.before)return null
        val rows=old.targets.toMutableList().apply{
            repeat(review.before.size){removeAt(start)}
            addAll(start,review.after)
        }
        fun seconds(targets:List<KotlinWorkoutTarget>)=
            targets.sumOf{it.activeSeconds.toLong()+it.restSeconds}-(targets.lastOrNull()?.restSeconds?:0)
        val baseline=state.originalSnapshot
        val estimate=baseline.estimatedMinutes*60L+seconds(rows)-seconds(baseline.targets)
        if(estimate !in 1..86_400L)return null
        return old.copy(targets=Collections.unmodifiableList(rows),estimatedMinutes=(estimate+59)/60)
    }

    internal fun customSnapshot(state:CustomWorkoutJournalDraft,request:MidSessionWorkingEdit):CustomWorkoutDaySnapshot? {
        val review=custom(state,request) as? MidSessionWorkingEditReview.Reviewed ?:return null
        val slots=state.snapshot.slots.map{slot->
            if(slot.slotId==request.occurrenceId)slot.copy(sets=review.after) else slot
        }
        val seconds=CustomWorkoutTimingDomain.totalSeconds(slots.flatMap{it.sets.map{set->set.prescription}})
            ?:return null
        return state.snapshot.copy(slots=Collections.unmodifiableList(slots),knownSetAndRestSeconds=seconds)
    }

    /** Audit a transient override of exactly the already issued day.
     * No draft revision, cycle consent or persistent program is changed. */
    fun approvedCustom(
        state:CustomWorkoutJournalDraft,request:MidSessionWorkingEdit,
        cycle:CustomTrainingCycle,setup:AthleteSetupReview,
        catalog:List<CatalogMovement>,inputs:CustomCoachAuditInputs,
        sameWeekPeers:List<CustomWorkoutJournalDraft>
    ):Boolean = runCatching {
        val stamp=state.originalSnapshot.stamp
        if(cycle.cycleId!=stamp.cycleId || cycle.revision!=stamp.cycleRevision ||
            cycle.athleteId!=stamp.athleteId ||
            state.receipts.any{it.operation is CustomWorkoutJournalOperation.Swap ||
                it.operation is CustomWorkoutJournalOperation.UndoSwap})
            return@runCatching false
        val profile=setup.reviewed?:return@runCatching false
        if(profile.athleteProfileId!=stamp.athleteId ||
            profile.profileRevision!=stamp.athleteProfileRevision || profile.gym.gymId!=stamp.gymId)
            return@runCatching false
        val next=customSnapshot(state,request)?:return@runCatching false
        val baseline=CustomCycleCoachAuditDomain.review(cycle,setup,catalog,inputs)
        if(baseline.status!=CustomCoachAuditStatus.READY_FOR_ATHLETE_REVIEW)
            return@runCatching false
        val week=CustomTrainingCycleDomain.week(
            baseline.structuralReview?:return@runCatching false,next.absoluteWeek)
            ?:return@runCatching false
        if(week.blockId!=next.blockId || week.weekInBlock!=next.weekInBlock ||
            week.phase!=next.phase || week.purpose!=next.blockPurpose)
            return@runCatching false
        val day=week.days.singleOrNull{it.weekday==next.weekday}?:return@runCatching false
        if(day.purpose!=next.sessionPurpose || day.slots.map{it.slotId}!=next.slots.map{it.slotId})
            return@runCatching false
        for((original,slot) in day.slots.zip(next.slots)) {
            if(original.exerciseId!=slot.exerciseId || original.attachmentId!=slot.attachmentId ||
                original.role!=slot.role || original.supersetGroup!=slot.supersetGroup ||
                original.pinned!=slot.pinned || original.machineInstanceId!=slot.machineInstanceId)
                return@runCatching false
            if(slot.slotId==request.occurrenceId) {
                val issued=state.originalSnapshot.slots.singleOrNull{it.slotId==slot.slotId}
                    ?:return@runCatching false
                val originalWorking=issued.sets.filter{it.prescription.type==DraftSetType.WORKING}
                if(originalWorking.isEmpty())return@runCatching false
                val maxReps=originalWorking.maxOf{it.prescription.reps.maximum}
                val restFloor=originalWorking.minOf{it.prescription.restSeconds}
                if(slot.sets.any{it.prescription.type==DraftSetType.WORKING &&
                        (it.prescription.reps.maximum>maxReps || it.prescription.restSeconds<restFloor)})
                    return@runCatching false
            }
        }
        // Reuse the reviewed weekly dose and timing audit for all issued
        // days, not only this day's new edit. Keep the planned full dose for
        // shortened peers; never use a skipped or omitted set as a credit to
        // authorize extra workload elsewhere. Unissued days stay in the plan.
        val peers=sameWeekPeers.filter{it.workoutId!=state.workoutId &&
            it.originalSnapshot.stamp.cycleId==stamp.cycleId &&
            it.originalSnapshot.absoluteWeek==next.absoluteWeek}
        if(peers.map{it.originalSnapshot.weekday}.distinct().size!=peers.size ||
            peers.any{it.originalSnapshot.weekday==next.weekday})
            return@runCatching false
        val peerOverrides=mutableListOf<CustomWeekSlotOverride>()
        val replacedDays=mutableSetOf(day.weekday)
        for(peer in peers) {
            val prior=peer.snapshot
            if(peer.originalSnapshot.stamp!=stamp || prior.stamp!=stamp ||
                prior.absoluteWeek!=next.absoluteWeek || prior.blockId!=week.blockId ||
                prior.weekInBlock!=week.weekInBlock || prior.phase!=week.phase ||
                prior.blockPurpose!=week.purpose)
                return@runCatching false
            // Existing shortened sessions are counted at their full signed
            // dose. Swapped/otherwise incompatible rosters need reconciliation,
            // not an assumed baseline or an invented muscle credit.
            if(prior.shortSessionLimitMinutes!=null) {
                if(prior.slots!=peer.originalSnapshot.slots)return@runCatching false
                continue
            }
            val planned=week.days.singleOrNull{it.weekday==prior.weekday}
                ?:return@runCatching false
            if(planned.purpose!=prior.sessionPurpose ||
                planned.slots.map{it.slotId}!=prior.slots.map{it.slotId})
                return@runCatching false
            for((original,slot) in planned.slots.zip(prior.slots)) {
                if(original.exerciseId!=slot.exerciseId || original.attachmentId!=slot.attachmentId ||
                    original.role!=slot.role || original.supersetGroup!=slot.supersetGroup ||
                    original.pinned!=slot.pinned || original.machineInstanceId!=slot.machineInstanceId)
                    return@runCatching false
                // Retain the original signed dose when a peer cut sets. This
                // initial audit never spends earlier cuts as extra capacity.
                val effective=slot.sets.map{it.prescription}
                if(effective.count{it.type==DraftSetType.WORKING}<
                    original.sets.count{it.type==DraftSetType.WORKING})
                    peerOverrides.add(CustomWeekSlotOverride(week.weekInBlock,prior.weekday,
                        slot.slotId,original.sets))
                else peerOverrides.add(CustomWeekSlotOverride(week.weekInBlock,prior.weekday,
                    slot.slotId,effective))
            }
            replacedDays.add(prior.weekday)
        }
        val overrides=cycle.weekOverrides[week.blockId].orEmpty().filterNot{
            it.weekInBlock==week.weekInBlock && it.weekday in replacedDays
        }+peerOverrides+next.slots.map{slot->CustomWeekSlotOverride(
            week.weekInBlock,day.weekday,slot.slotId,slot.sets.map{it.prescription})}
        val transient=cycle.copy(weekOverrides=cycle.weekOverrides+(week.blockId to overrides))
        CustomCycleCoachAuditDomain.review(transient,setup,catalog,inputs).status==
            CustomCoachAuditStatus.READY_FOR_ATHLETE_REVIEW
    }.getOrDefault(false)

    /** Read-only choices for one ordinary, still-editable occurrence of the
     * exact issued authored cycle. Reuse the saved-method policy, not a
     * second progression table. This is not a journal operation or consent.
     * The owner must independently load the signed cycle and validate stock
     * before applying a future session-only choice. */
    fun availableCustomProgressionMethods(
        state:CustomWorkoutJournalDraft,cycle:CustomTrainingCycle,
        workoutId:String,expectedRevision:Long,slotId:String
    ):Set<SavedProgressionMethod> {
        val original=state.originalSnapshot
        val stamp=original.stamp
        if(cycle.cycleId!=stamp.cycleId || cycle.athleteId!=stamp.athleteId ||
            cycle.revision!=stamp.cycleRevision ||
            state.receipts.any{it.operation is CustomWorkoutJournalOperation.Swap ||
                it.operation is CustomWorkoutJournalOperation.UndoSwap})
            return emptySet()
        val block=cycle.blocks.singleOrNull{it.blockId==original.blockId}
            ?:return emptySet()
        if(block.phase!=original.phase || block.purpose!=original.blockPurpose)
            return emptySet()
        val day=block.days.singleOrNull{it.weekday==original.weekday}
            ?:return emptySet()
        val authored=day.slots.singleOrNull{it.slotId==slotId}?:return emptySet()
        val issued=original.slots.singleOrNull{it.slotId==slotId}?:return emptySet()
        val active=state.snapshot.slots.singleOrNull{it.slotId==slotId}?:return emptySet()
        if(authored.exerciseId!=issued.exerciseId || authored.attachmentId!=issued.attachmentId ||
            authored.machineInstanceId!=issued.machineInstanceId || authored.role!=issued.role ||
            authored.supersetGroup!=issued.supersetGroup || authored.pinned!=issued.pinned ||
            active.exerciseId!=issued.exerciseId || active.attachmentId!=issued.attachmentId ||
            active.machineInstanceId!=issued.machineInstanceId || active.role!=issued.role ||
            active.supersetGroup!=issued.supersetGroup || active.pinned!=issued.pinned)
            return emptySet()
        // An unchanged prescription is only NO_CHANGE after the ordinary
        // working-row, pending-work and exact-revision checks succeed.
        val guard=custom(state,MidSessionWorkingEdit(workoutId,expectedRevision,slotId,
            active.sets.count{it.prescription.type==DraftSetType.WORKING}))
        if(guard!=MidSessionWorkingEditReview.Rejected(MidSessionWorkingEditRefusal.NO_CHANGE))
            return emptySet()
        return Collections.unmodifiableSet(CustomCycleBuilderDomain.availableProgressionMethods(
            cycle,ProgramProgressionSlotKey(block.blockId,day.weekday,slotId)))
    }

    fun custom(state:CustomWorkoutJournalDraft,request:MidSessionWorkingEdit):
        MidSessionWorkingEditReview<CustomWorkoutSetSnapshot> {
        if(state.workoutId!=request.workoutId || state.revision!=request.expectedRevision ||
            request.expectedRevision !in 0 until 9_007_199_254_740_991L)
            return refused(MidSessionWorkingEditRefusal.STALE_WORKOUT)
        if(state.finishedByEventId!=null)return refused(MidSessionWorkingEditRefusal.FINISHED)
        val slot=state.snapshot.slots.singleOrNull{it.slotId==request.occurrenceId}
            ?:return refused(MidSessionWorkingEditRefusal.UNKNOWN_OCCURRENCE)
        val rows=slot.sets.map{set->with(set.prescription){
            Row(set,set.setKey,type,set.ordinalInSlot,load,reps,targetRir,activeSeconds,restSeconds)
        }}
        return review(request,rows,
            (state.originalSnapshot.slots+state.snapshot.slots).flatMap{it.sets}.map{it.setKey}.toSet(),
            state.effectiveVisits.keys.map{it.setKey}.toSet(),
            protected=state.snapshot.blockPurpose!=CustomBlockPurpose.TRAINING ||
                state.snapshot.sessionPurpose!=ComposerSessionPurpose.HYPERTROPHY ||
                state.snapshot.shortSessionLimitMinutes!=null || slot.supersetGroup!=null || slot.sets.any{
                    it.prescription.amplifier!=null || it.prescription.percentageIntent!=null ||
                        it.prescription.exactRepTarget!=null
                },
            allowReduction=!slot.pinned && slot.role==ComposerRole.ACCESSORY,maxKeyLength=160,
            currentSetCount=state.snapshot.slots.sumOf{it.sets.size},
            previouslyAdjusted=state.receipts.any{
                (it.operation as? CustomWorkoutJournalOperation.AdjustRemaining)?.request?.occurrenceId==request.occurrenceId
            },maxOrdinal=20
        ){row,key,ordinal,reps,rest->row.copy(setKey=key,ordinalInSlot=ordinal,
            prescription=row.prescription.copy(reps=reps,restSeconds=rest))}
    }
}
