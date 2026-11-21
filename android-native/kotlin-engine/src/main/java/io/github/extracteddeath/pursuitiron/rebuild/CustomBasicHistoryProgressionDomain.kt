package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal

/**
 * Read-only adaptation of one completed, corrected CUSTOM session into the
 * existing basic progression calculator. This is NOT an authentication or
 * approval authority: only a native owner-fenced caller may supply the
 * complete verified history, original issued snapshots, source cycle and
 * current verified physical stock. No result changes a draft or issued set.
 *
 * A physical machine, program block, weekday and stable slot are separate
 * progression contexts. Follow-up mini-sets, warmups and shortened sessions
 * cannot earn a straight working-set increase.
 */
data class CustomBasicHistoryRequest(
    val cycle: CustomTrainingCycle,
    val next: CustomWorkoutDaySnapshot,
    val nextWorkoutId: String,
    val slotId: String,
    val completed: List<CustomCoachingHistoricalSession>,
    val selectedMachineInstanceId: String?,
    val verifiedStock: VerifiedLoadStock?,
    val maximumAuthorizedIncrement: BigDecimal
)

enum class CustomBasicHistoryRefusal {
    INVALID_NEXT_SOURCE,
    NO_SAVED_BASIC_METHOD,
    AUTHORED_OR_SPECIALIST_OWNS_LOAD,
    EXACT_REP_TARGETS_UNAVAILABLE,
    NO_COMPARABLE_HISTORY,
    LAST_VISIT_INCOMPATIBLE,
    HISTORY_SOURCE_CHANGED,
    INVALID_HISTORY
}

sealed interface CustomBasicHistoryResult {
    data class Rejected(
        val reason: CustomBasicHistoryRefusal,
        val evaluatorReason: BasicProgressionRefusal? = null
    ): CustomBasicHistoryResult
    data class Reviewed(
        val historyWorkoutId: String?,
        val completionOrder: Long?,
        val candidate: BasicProgressionResult.Candidate,
        val blockId: String,
        val weekday: Int,
        val slotId: String,
        val machineInstanceId: String?
    ): CustomBasicHistoryResult {
        val mayIssueWorkout:Boolean get()=false
        val maySaveProgram:Boolean get()=false
        val mayModifyHistory:Boolean get()=false
    }
}

object CustomBasicHistoryProgressionDomain {
    fun review(request: CustomBasicHistoryRequest): CustomBasicHistoryResult {
        fun no(reason: CustomBasicHistoryRefusal, evaluator: BasicProgressionRefusal?=null) =
            CustomBasicHistoryResult.Rejected(reason,evaluator)
        val cycle=request.cycle
        val next=request.next
        if(request.nextWorkoutId.isBlank() || request.slotId.isBlank() ||
            cycle.cycleId!=next.stamp.cycleId ||
            cycle.athleteId!=next.stamp.athleteId ||
            cycle.revision!=next.stamp.cycleRevision ||
            next.shortSessionLimitMinutes!=null || next.prescriptionEditedForTime ||
            next.blockPurpose!=CustomBlockPurpose.TRAINING)
            return no(CustomBasicHistoryRefusal.INVALID_NEXT_SOURCE)

        var precedingWeeks=0
        val block=cycle.blocks.firstOrNull { b ->
            if(b.blockId==next.blockId)true
            else { precedingWeeks+=b.weeks;false }
        } ?:return no(CustomBasicHistoryRefusal.INVALID_NEXT_SOURCE)
        if(next.weekInBlock !in 1..block.weeks ||
            next.absoluteWeek!=precedingWeeks+next.weekInBlock ||
            block.phase!=next.phase || block.purpose!=next.blockPurpose)
            return no(CustomBasicHistoryRefusal.INVALID_NEXT_SOURCE)
        val day=block.days.singleOrNull { it.weekday==next.weekday }
            ?:return no(CustomBasicHistoryRefusal.INVALID_NEXT_SOURCE)
        val saved=day.slots.singleOrNull { it.slotId==request.slotId }
            ?:return no(CustomBasicHistoryRefusal.INVALID_NEXT_SOURCE)
        val selected=next.slots.singleOrNull { it.slotId==request.slotId }
            ?:return no(CustomBasicHistoryRefusal.INVALID_NEXT_SOURCE)
        val exact=cycle.weekOverrides[block.blockId].orEmpty().singleOrNull {
            it.weekInBlock==next.weekInBlock && it.weekday==next.weekday &&
                it.slotId==request.slotId
        }?.sets ?: saved.sets
        if(saved.exerciseId!=selected.exerciseId ||
            saved.progressionMethod==null ||
            exact!=selected.sets.map {it.prescription} ||
            selected.sets.map {it.ordinalInSlot}!=
                (1..selected.sets.size).toList() ||
            (selected.machineInstanceId!=null &&
                selected.machineInstanceId!=request.selectedMachineInstanceId))
            return if(saved.progressionMethod==null)
                no(CustomBasicHistoryRefusal.NO_SAVED_BASIC_METHOD)
            else no(CustomBasicHistoryRefusal.INVALID_NEXT_SOURCE)

        val plan=BasicProgramProgressionPlanDomain.review(cycle)
            as? ProgramProgressionPlan.Ready
            ?:return no(CustomBasicHistoryRefusal.INVALID_NEXT_SOURCE)
        val assignment=plan.assignments.singleOrNull { it.key==
            ProgramProgressionSlotKey(block.blockId,next.weekday,request.slotId) }
            ?:return no(CustomBasicHistoryRefusal.INVALID_NEXT_SOURCE)
        val strategy=when(assignment.route) {
            ProgramProgressionRoute.MANUAL -> BasicProgressionStrategy.MANUAL_LOGGING
            ProgramProgressionRoute.BASIC_WEIGHTED -> assignment.basicStrategy
            else -> null
        } ?:return no(CustomBasicHistoryRefusal.AUTHORED_OR_SPECIALIST_OWNS_LOAD)

        val working=selected.sets.filter {it.prescription.type==DraftSetType.WORKING}
        if(working.size !in 1..32 ||
            working.any {it.prescription.amplifier!=null} ||
            working.map {it.prescription.reps}.distinct().size!=1)
            return no(CustomBasicHistoryRefusal.INVALID_NEXT_SOURCE)
        val targetReps=working.first().prescription.reps
        val loads=working.map {
            it.prescription.load as? DraftSetLoad.Verified
                ?:return no(CustomBasicHistoryRefusal.INVALID_NEXT_SOURCE)
        }
        if(loads.map {it.unit to it.basis}.distinct().size!=1)
            return no(CustomBasicHistoryRefusal.INVALID_NEXT_SOURCE)
        val unit=loads.first().unit
        val basis=loads.first().basis
        val exactReps=if(strategy in setOf(
                BasicProgressionStrategy.REP_TARGET_PROGRESSION,
                BasicProgressionStrategy.REP_LADDER_PROGRESSION)) {
            if(working.any {it.prescription.reps.minimum!=it.prescription.reps.maximum})
                return no(CustomBasicHistoryRefusal.EXACT_REP_TARGETS_UNAVAILABLE)
            working.map {it.prescription.reps.minimum}
        } else null
        val normalizedMachine=KotlinMachineAlternativesDomain.normalizeMachineId(
            request.selectedMachineInstanceId)
        val scope=BasicProgressionScope(
            next.stamp.athleteId,cycle.cycleId,next.stamp.gymId,
            next.blockId,next.blockId+":d"+next.weekday,selected.exerciseId,
            request.selectedMachineInstanceId,unit,basis)
        if(!scope.valid())
            return no(CustomBasicHistoryRefusal.INVALID_NEXT_SOURCE)

        if(strategy==BasicProgressionStrategy.MANUAL_LOGGING) {
            val manual=BasicProgressionPolicyDomain.review(BasicProgressionRequest(
                strategy,request.nextWorkoutId,scope,null,null,targetReps,
                loads.map{it.value},null,request.maximumAuthorizedIncrement))
            return when(manual) {
                is BasicProgressionResult.Candidate -> CustomBasicHistoryResult.Reviewed(
                    null,null,manual,next.blockId,next.weekday,request.slotId,
                    request.selectedMachineInstanceId)
                is BasicProgressionResult.Refused ->
                    no(CustomBasicHistoryRefusal.INVALID_NEXT_SOURCE,manual.reason)
            }
        }

        // Source-bound complete ordered ledger, not a UI-picked successful
        // workout. Other gyms, training days and machines are independent,
        // while a newer incomplete visit at THIS station blocks fallback.
        if(request.completed.map {it.completionOrder}!=
            request.completed.map {it.completionOrder}.distinct().sorted() ||
            request.completed.any {it.completionOrder<=0} ||
            request.completed.map {it.evidence.workoutId}.distinct().size!=
                request.completed.size)
            return no(CustomBasicHistoryRefusal.INVALID_HISTORY)
        var latest:CustomCoachingHistoricalSession?=null
        for(session in request.completed) {
            val snap=session.snapshot
            val evidence=session.evidence
            if(snap.stamp.athleteId!=evidence.athleteId ||
                snap.stamp.cycleId!=evidence.cycleId ||
                snap.stamp.cycleRevision!=evidence.cycleRevision ||
                snap.shortSessionOmittedSetKeys!=evidence.shortSessionOmittedSetKeys)
                return no(CustomBasicHistoryRefusal.INVALID_HISTORY)
            if(snap.stamp.cycleId!=cycle.cycleId ||
                snap.stamp.gymId!=next.stamp.gymId ||
                snap.blockId!=next.blockId || snap.weekday!=next.weekday)
                continue
            val historicalSlot=snap.slots.singleOrNull {it.slotId==request.slotId}
                ?:continue
            if(historicalSlot.exerciseId!=selected.exerciseId)continue
            val visits=evidence.visits.filter {
                it.slotId==request.slotId &&
                it.part==CustomWorkoutVisitPart.MAIN &&
                it.setType==DraftSetType.WORKING
            }
            if(visits.isEmpty())
                return no(CustomBasicHistoryRefusal.INVALID_HISTORY)
            val observedMachines=visits.mapNotNull{it.actual}.map {
                KotlinMachineAlternativesDomain.normalizeMachineId(
                    it.machineInstanceId)
            }.toSet()
            // A wholly different, explicitly observed machine is its OWN
            // history. An unknown/partial station is never quietly bypassed.
            if(observedMachines.size==1 &&
                normalizedMachine !in observedMachines &&
                historicalSlot.machineInstanceId!=request.selectedMachineInstanceId)
                continue
            if(observedMachines.size>1 ||
                (observedMachines.isNotEmpty() &&
                    normalizedMachine !in observedMachines))
                return no(CustomBasicHistoryRefusal.LAST_VISIT_INCOMPATIBLE)
            latest=session
        }
        val prior=latest ?:return no(CustomBasicHistoryRefusal.NO_COMPARABLE_HISTORY)
        val original=prior.snapshot
        if(original.stamp.cycleRevision!=next.stamp.cycleRevision ||
            original.stamp.gymCatalogSourceRevision!=
                next.stamp.gymCatalogSourceRevision ||
            original.stamp.athleteProfileRevision!=
                next.stamp.athleteProfileRevision)
            return no(CustomBasicHistoryRefusal.HISTORY_SOURCE_CHANGED)
        if(prior.evidence.prescriptionEditedForTime ||
            original.prescriptionEditedForTime ||
            original.shortSessionLimitMinutes!=null ||
            original.slots.count {it.slotId==request.slotId}!=1)
            return no(CustomBasicHistoryRefusal.LAST_VISIT_INCOMPATIBLE)
        val oldSlot=original.slots.single {it.slotId==request.slotId}
        val expected=oldSlot.sets.filter {it.prescription.type==DraftSetType.WORKING}
        val observations=prior.evidence.visits.filter {
            it.slotId==request.slotId &&
            it.part==CustomWorkoutVisitPart.MAIN &&
            it.setType==DraftSetType.WORKING
        }
        if(expected.size!=working.size || observations.size!=expected.size ||
            expected.any{it.prescription.amplifier!=null} ||
            expected.map{it.setKey}.distinct().size!=expected.size ||
            observations.map{it.setKey}.toSet()!=expected.map{it.setKey}.toSet() ||
            observations.map{it.setKey}.distinct().size!=observations.size)
            return no(CustomBasicHistoryRefusal.LAST_VISIT_INCOMPATIBLE)
        val byKey=observations.associateBy {it.setKey}
        val facts=expected.mapIndexed {ordinal,item ->
            val observed=byKey.getValue(item.setKey)
            val prescribed=observed.prescribedLoad as? DraftSetLoad.Verified
                ?:return no(CustomBasicHistoryRefusal.LAST_VISIT_INCOMPATIBLE)
            if(item.prescription.load!=prescribed ||
                item.prescription.reps!=observed.prescribedReps ||
                item.prescription.reps!=targetReps ||
                prescribed.unit!=unit || prescribed.basis!=basis ||
                observed.amplifierKind!=null ||
                (observed.actual!=null &&
                    (observed.actual.unit!=unit ||
                        KotlinMachineAlternativesDomain.normalizeMachineId(
                            observed.actual.machineInstanceId)!=normalizedMachine)))
                return no(CustomBasicHistoryRefusal.LAST_VISIT_INCOMPATIBLE)
            WorkingSetFact(
                ordinal,
                when(observed.outcome) {
                    KotlinJournalSetOutcome.COMPLETED -> SetOutcome.COMPLETED
                    KotlinJournalSetOutcome.SKIPPED -> SetOutcome.SKIPPED
                    KotlinJournalSetOutcome.FAILED_ATTEMPT -> SetOutcome.FAILED_ATTEMPT
                },
                observed.actual?.load,observed.actual?.reps,
                observed.actual?.actualRir,prescribed.value,item.prescription.targetRir)
        }
        val priorEvidence=ProgressionWorkoutEvidence(
            prior.evidence.workoutId,selected.exerciseId,unit,targetReps,
            expected.size,facts,scope.trainingDayId)
        val calculated=BasicProgressionPolicyDomain.review(BasicProgressionRequest(
            strategy,request.nextWorkoutId,scope,scope,priorEvidence,targetReps,
            loads.map{it.value},request.verifiedStock,
            request.maximumAuthorizedIncrement,exactReps))
        return when(calculated) {
            is BasicProgressionResult.Candidate -> CustomBasicHistoryResult.Reviewed(
                prior.evidence.workoutId,prior.completionOrder,calculated,
                next.blockId,next.weekday,request.slotId,
                request.selectedMachineInstanceId)
            is BasicProgressionResult.Refused ->
                no(CustomBasicHistoryRefusal.LAST_VISIT_INCOMPATIBLE,calculated.reason)
        }
    }
}
