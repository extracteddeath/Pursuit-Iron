package io.github.extracteddeath.pursuitiron.rebuild

import java.security.MessageDigest
import java.util.Collections

/**
 * Read-only provenance for the REAL generated future-block path. This is
 * checked after full source/inventory review, before an option is offered.
 * It does not re-prescribe, apply a program or interpret a plan as completed
 * athletic performance.
 */
enum class GeneratedSetNumberOrigin {
    EXERCISE_PHASE_POLICY,
    PROTECTED_ATHLETE_PRESCRIPTION,
    VERIFIED_SOURCE_MENU_RESTORATION,
    OPTED_IN_STRENGTH_DOSE_REPAIR
}

enum class GeneratedHistoryEvidenceState {
    /** The current generated composer has no authenticated workout receipts.
     * Its prior cycle is PRESCRIBED WORK, not completed training history. */
    NOT_CONSUMED,
    /** Replay and corrections verified, but native storage-owner authentication
     * is still required. No historical facts have authorized future numbers. */
    REPLAY_VERIFIED_NOT_STORE_ATTESTED
}

data class GeneratedSetNumberLineage(
    val weekday:Int,
    val slotId:String,
    val exerciseId:String,
    val role:ComposerRole,
    val purpose:ComposerSessionPurpose,
    val ordinal:Int,
    val type:DraftSetType,
    val load:DraftSetLoad,
    val reps:RepBand,
    val targetRir:Int?,
    val activeSeconds:Int,
    val restSeconds:Int,
    val origin:GeneratedSetNumberOrigin,
    val predecessorSlotId:String?,
    /** The exact author-selected purpose/day prescription, if applicable. */
    val policyDayIndex:Int?
)

data class GeneratedPhaseNumberBoundary(
    val sourcePhase:TrainingPhase,
    val destinationPhase:TrainingPhase,
    val sourceExitWeek:Int,
    val destinationEntryWeek:Int,
    val sourceWorkingSets:Int,
    val destinationWorkingSets:Int,
    val sourceExerciseVisits:Int,
    val destinationExerciseVisits:Int
)

data class GeneratedUpstreamNumberTrace(
    val schemaVersion:Int,
    val athleteId:String,
    val profileRevision:String,
    val sourceCycleId:String,
    val sourceCycleRevision:Long,
    val candidateCycleRevision:Long,
    val targetBlockId:String,
    val phase:TrainingPhase,
    val goal:AthleteTrainingGoal,
    /** SHA-256 over the exact reviewed source request, prescriptions, candidate
     * and numbered facts. Requires the SAME canonical source snapshot on replay. */
    val exactRequestDigest:String,
    val sets:List<GeneratedSetNumberLineage>,
    val phaseBoundary:GeneratedPhaseNumberBoundary?,
    /** Re-audited against the exact FINAL proposed cycle, including
     * source-restored exercises and authorized extra Strength sets. Planned
     * muscle stimulus is not a recorded athlete performance observation. */
    val plannedWeekDose:WeeklyDoseFeasibilityReport,
    val historyEvidence:GeneratedHistoryEvidenceState,
    val replayedHistory:KotlinProgramHistoryReadout?=null
) {
    val mayIssueWorkout:Boolean get()=false
    val maySaveProgram:Boolean get()=false
}

/**
 * Checks one-to-one numerical correspondence for every generated set.
 * Prescribed work must match its exercise/purpose/day policy output;
 * only protected source work, exact source-menu restoration and explicitly
 * reviewed Strength dose repairs may differ. Incomparable/partial output
 * fails closed instead of inventing a reason for a rep, set or load.
 */
object GeneratedUpstreamNumberTraceDomain {
    private fun digest(value:String):String=MessageDigest
        .getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        .joinToString(""){(it.toInt() and 255).toString(16).padStart(2,'0')}

    private fun original(set:DraftPrescribedSet)=CustomAuthoredSet(
        set.type,set.load,set.targetReps,set.targetRir,
        set.activeSeconds,set.restSeconds)

    fun review(
        request:GeneratedAdaptiveBlockRequest,
        reviewedCycle:CustomCycleReview,
        generation:AthleteProgramGenerationReview,
        prescribed:SessionPrescriptionDraft,
        continuity:List<GeneratedAdaptiveSlotContinuity>,
        restored:List<StrengthMenuRestoredExercise>,
        doseAdded:List<GeneratedStrengthDoseAddedSet>
    ):GeneratedUpstreamNumberTrace? {
        val athlete=request.program.setup.reviewed ?:return null
        val candidate=reviewedCycle.verifiedDraft ?:return null
        val composed=generation.candidate?.catalogReview?.composedRequest
            ?:return null
        val source=request.currentCycle
        if(reviewedCycle.status!=CustomCycleReviewStatus.READY_FOR_COACH_REVIEW ||
            prescribed.status!=PrescriptionDraftStatus.COMPLETE_FOR_REVIEW ||
            candidate.cycleId!=source.cycleId ||
            candidate.athleteId!=athlete.athleteProfileId ||
            source.athleteId!=athlete.athleteProfileId ||
            candidate.goal!=athlete.goal ||
            candidate.revision!=source.revision+1 ||
            candidate.revision<=0L ||
            source.goal!=athlete.goal)return null
        val block=candidate.blocks.singleOrNull{
            it.blockId==request.targetBlockId
        } ?:return null
        val before=source.blocks.singleOrNull{
            it.blockId==request.targetBlockId
        } ?:return null
        if(block.phase!=request.program.phase ||
            before.phase!=block.phase ||
            block.purpose!=CustomBlockPurpose.TRAINING)return null
        val sessions=mutableMapOf<Int,DraftPrescribedSession>()
        for(session in prescribed.sessions) {
            val day=composed.days.getOrNull(session.dayIndex) ?:return null
            if(sessions.put(day.calendarDay,session)!=null)return null
        }
        // Everything outside the target block remains the original,
        // source-approved cycle, not an arbitrary rewritten "previous"
        // program used to justify a restoration or a phase comparison.
        if(candidate.blocks.size!=source.blocks.size ||
            candidate.blocks.zip(source.blocks).any{(next,prior)->
                next.blockId!=prior.blockId ||
                    (next.blockId!=request.targetBlockId && next!=prior)
            } ||
            candidate.weekOverrides!=source.weekOverrides ||
            candidate.authorNote!=source.authorNote)return null
        val sourceByDay=before.days.associateBy{it.weekday}
        // Source-restored Strength exercises come from the LAST resolved
        // real Hypertrophy TRAINING week, not the unsigned target template
        // or the adjacent deload/recovery week.
        val exitDays=if(restored.isNotEmpty()) {
            if(!request.allowSourceMenuRecovery ||
                block.phase!=TrainingPhase.STRENGTH ||
                restored.map{it.weekday to it.sourceSlotId}.distinct().size
                    !=restored.size)return null
            val boundary=StrengthRosterBoundaryGuardDomain.review(
                reviewedCycle,request.targetBlockId)
            val exitWeek=boundary.sourceExitWeek ?:return null
            val exit=CustomTrainingCycleDomain.week(reviewedCycle,exitWeek)
                ?:return null
            if(exit.phase!=TrainingPhase.HYPERTROPHY ||
                exit.purpose!=CustomBlockPurpose.TRAINING)return null
            exit.days.associateBy{it.weekday}
        } else emptyMap()
        val continuityBySlot=continuity.groupBy{it.weekday to it.proposedSlotId}
        val restoredBySlot=restored.groupBy{it.weekday to it.proposedSlotId}
        val addedBySlot=doseAdded.groupBy{it.weekday to it.slotId}
        val proposalSlots=block.days.flatMap{day->
            day.slots.map{day.weekday to it.slotId}
        }
        if(proposalSlots.size!=proposalSlots.toSet().size ||
            continuityBySlot.keys!=proposalSlots.toSet() ||
            restoredBySlot.keys.any{it !in proposalSlots} ||
            addedBySlot.keys.any{it !in proposalSlots} ||
            restoredBySlot.values.any{it.size!=1})return null

        val numbered=mutableListOf<GeneratedSetNumberLineage>()
        for(day in block.days.sortedBy{it.weekday}) {
            val prescribedDay=sessions[day.weekday] ?:return null
            if(day.purpose!=prescribedDay.purpose)return null
            val sourceDay=sourceByDay[day.weekday]
            for(slot in day.slots) {
                val key=day.weekday to slot.slotId
                val lineage=continuityBySlot[key]?.singleOrNull()
                    ?:return null
                if(lineage.exerciseId!=slot.exerciseId)return null
                val old=sourceDay?.slots?.singleOrNull{
                    it.slotId==slot.slotId
                }
                val protected=old!=null && (
                    old.pinned || old.attachmentId!=null ||
                    old.supersetGroup!=null || old.coachNote.isNotBlank() ||
                    old.sets.any{it.amplifier!=null})
                val restore=restoredBySlot[key]?.singleOrNull()
                val newSets=slot.sets
                val prescribedExercise=prescribedDay.exercises
                    .singleOrNull{it.exerciseId==slot.exerciseId}
                val policy=if(protected || restore!=null)null else {
                    val matches=request.prescriptions.filter{
                        it.exerciseId==slot.exerciseId &&
                            it.purpose==day.purpose &&
                            (it.dayIndex==null ||
                                it.dayIndex==prescribedDay.dayIndex)
                    }
                    matches.singleOrNull{
                        it.dayIndex==prescribedDay.dayIndex
                    } ?:matches.singleOrNull{it.dayIndex==null}
                }
                val regular=prescribedExercise?.sets?.map(::original)
                val extras=addedBySlot[key].orEmpty()
                if(newSets.isEmpty())return null
                when {
                    protected -> {
                        if(restore!=null || extras.isNotEmpty() ||
                            lineage.priorSlotId!=slot.slotId ||
                            old!=slot)return null
                    }
                    restore!=null -> {
                        val historical=exitDays[day.weekday]?.slots
                            ?.singleOrNull{it.slotId==restore.sourceSlotId}
                            ?:return null
                        // Slot ID continuity alone proves nothing about
                        // the numerical prescription. This is an exact
                        // set-for-set, machine/attachment/role comparison
                        // to the authentic resolved source exposure.
                        if(extras.isNotEmpty() ||
                            prescribedExercise!=null ||
                            restore.exerciseId!=slot.exerciseId ||
                            restore.sourceSlotId==slot.slotId ||
                            restore.proposedSlotId!=slot.slotId ||
                            lineage.priorSlotId!=restore.sourceSlotId ||
                            lineage.relationship!=
                                GeneratedAdaptiveSlotRelationship.NEW_DAY_OR_EXERCISE ||
                            historical.role !in setOf(
                                ComposerRole.SECONDARY,ComposerRole.ACCESSORY) ||
                            historical.pinned ||
                            historical.coachNote.isNotBlank() ||
                            historical.attachmentId!=null ||
                            historical.supersetGroup!=null ||
                            historical.machineInstanceId!=null ||
                            historical.sets.any{
                                it.type!=DraftSetType.WORKING ||
                                    it.amplifier!=null
                            } ||
                            slot!=historical.copy(
                                slotId=slot.slotId,pinned=false))
                            return null
                    }
                    else -> {
                        if(policy==null || regular==null ||
                            regular.isEmpty() || newSets.size!=
                                regular.size+extras.size ||
                            newSets.take(regular.size)!=regular ||
                            extras.any{
                                it.exerciseId!=slot.exerciseId ||
                                it.slotId!=slot.slotId
                            })return null
                        // The only currently authorized added numbers are
                        // exact copies of the first straight Strength set.
                        if(extras.isNotEmpty() &&
                            (block.phase!=TrainingPhase.STRENGTH ||
                                !request.allowSourceDoseRecovery ||
                                regular.any{it!=regular.first()} ||
                                newSets.drop(regular.size).any{
                                    it!=regular.first()
                                }))return null
                    }
                }
                for((index,set) in newSets.withIndex()) {
                    val origin=when {
                        protected ->
                            GeneratedSetNumberOrigin.PROTECTED_ATHLETE_PRESCRIPTION
                        restore!=null ->
                            GeneratedSetNumberOrigin.VERIFIED_SOURCE_MENU_RESTORATION
                        index>=checkNotNull(regular).size ->
                            GeneratedSetNumberOrigin.OPTED_IN_STRENGTH_DOSE_REPAIR
                        else ->GeneratedSetNumberOrigin.EXERCISE_PHASE_POLICY
                    }
                    numbered+=GeneratedSetNumberLineage(
                        day.weekday,slot.slotId,slot.exerciseId,slot.role,
                        day.purpose,index+1,set.type,set.load,set.reps,
                        set.targetRir,set.activeSeconds,set.restSeconds,
                        origin,lineage.priorSlotId,
                        if(policy!=null)policy.dayIndex else null)
                }
            }
        }
        if(numbered.size!=block.days.sumOf{day->
            day.slots.sumOf{it.sets.size}
        })return null

        // The previous prescription is read from the *resolved* last
        // training week; a deload/test bridge is not performance evidence.
        val index=candidate.blocks.indexOfFirst{
            it.blockId==request.targetBlockId
        }
        val previous=(index-1 downTo 0).firstOrNull{
            candidate.blocks[it].purpose==CustomBlockPurpose.TRAINING
        }
        val transition=previous?.let{previousIndex->
            val exit=candidate.blocks.take(previousIndex+1).sumOf{it.weeks}
            val entry=candidate.blocks.take(index).sumOf{it.weeks}+1
            val resolved=CustomTrainingCycleDomain.week(reviewedCycle,exit)
                ?:return null
            val previousBlock=candidate.blocks[previousIndex]
            if(resolved.blockId!=previousBlock.blockId)return null
            GeneratedPhaseNumberBoundary(
                previousBlock.phase,block.phase,exit,entry,
                resolved.days.sumOf{day->day.slots.sumOf{slot->
                    slot.sets.count{it.type==DraftSetType.WORKING}
                }},
                block.days.sumOf{day->day.slots.sumOf{slot->
                    slot.sets.count{it.type==DraftSetType.WORKING}
                }},
                resolved.days.sumOf{it.slots.size},
                block.days.sumOf{it.slots.size}
            )
        }
        // Reconstruct the actual final proposed sets rather than borrowing
        // the generator's earlier dose/time audit, which preceded optional
        // source restorations and Strength set-count repair. The same existing
        // WeeklyDoseFeasibilityDomain remains the only muscle-credit owner.
        val catalog=request.program.catalog.associateBy{it.exerciseId}
        val composerDays=composed.days
        if(catalog.size!=request.program.catalog.size ||
            composerDays.size!=block.days.size ||
            composerDays.map{it.calendarDay}.toSet()!=
                block.days.map{it.weekday}.toSet() ||
            prescribed.composer.week.phase!=block.phase ||
            prescribed.composer.week.bounds!=request.program.policy?.selectedBounds)
            return null
        val exposures=mutableListOf<PlannedWorkingExposure>()
        for((dayIndex,composerDay) in composerDays.withIndex()) {
            val targetDay=block.days.singleOrNull{
                it.weekday==composerDay.calendarDay
            } ?:return null
            val lastSlot=targetDay.slots.lastOrNull() ?:return null
            for(slot in targetDay.slots) {
                val exercise=catalog[slot.exerciseId] ?:return null
                if(exercise.creditStatus!=
                    CatalogCreditStatus.AUTHOR_REVIEWED_PROVISIONAL ||
                    exercise.creditReviewReference.isNullOrBlank() ||
                    exercise.credits.isEmpty())return null
                for((ordinal,set) in slot.sets.withIndex()) {
                    val lastInSession=slot.slotId==lastSlot.slotId &&
                        ordinal==slot.sets.lastIndex
                    exposures+=PlannedWorkingExposure(
                        "d${dayIndex}:${slot.slotId}:"+
                            "${set.type.name.lowercase()}:${ordinal}",
                        dayIndex,
                        if(set.type==DraftSetType.WARMUP)
                            ExposureSetKind.WARMUP
                        else ExposureSetKind.WORKING,
                        if(set.type==DraftSetType.WARMUP)emptyList()
                        else exercise.credits,
                        set.activeSeconds,
                        if(lastInSession)0 else set.restSeconds)
                }
            }
        }
        val plannedDose=runCatching{
            WeeklyDoseFeasibilityDomain.audit(prescribed.composer.week.copy(
                exposures=Collections.unmodifiableList(exposures.toList())))
        }.getOrNull() ?:return null
        // A simple unchanged week must match the original audited
        // prescription; restoration or protected edits legitimately change
        // the final delivered weekly plan and are traced above separately.
        // Replayed actuals never become an automatically authorized
        // programming decision. Exact native storage attestation is still
        // missing; this is a factual source-bound review ledger only.
        val history=request.historyReadoutRequest?.let { historical ->
            if(historical.athleteId!=athlete.athleteProfileId ||
                historical.cycleId!=source.cycleId ||
                historical.expectedSource.gymId!=athlete.gym.gymId ||
                historical.expectedSource.programRevision<=0L ||
                historical.expectedSource.programRevision>source.revision)
                return null
            (KotlinProgramHistoryReadoutDomain.review(historical)
                as? KotlinProgramHistoryReadoutResult.Candidate)?.ledger
                ?:return null
        }
        val facts=Collections.unmodifiableList(numbered.toList())
        val signature=digest(listOf(
            "GENERATED_UPSTREAM_V1",source.toString(),
            request.program.toString(),request.prescriptions.toString(),
            candidate.toString(),facts.toString(),transition.toString(),
            plannedDose.toString(),
            history?.exactReplayDigest ?: "NO_REPLAYED_HISTORY"
        ).joinToString("\n"))
        return GeneratedUpstreamNumberTrace(
            1,athlete.athleteProfileId,athlete.profileRevision,
            source.cycleId,source.revision,candidate.revision,
            request.targetBlockId,block.phase,athlete.goal,
            signature,facts,transition,plannedDose,
            if(history==null)GeneratedHistoryEvidenceState.NOT_CONSUMED
            else GeneratedHistoryEvidenceState.REPLAY_VERIFIED_NOT_STORE_ATTESTED,
            history)
    }
}
