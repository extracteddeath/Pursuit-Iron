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
    val maximumAuthorizedIncrement: BigDecimal,
    val reviewedCatalog: Map<String, CatalogMovement> = emptyMap(),
    val experience: AthleteExperience? = null
)

enum class CustomBasicHistoryRefusal {
    INVALID_NEXT_SOURCE,
    NO_SAVED_BASIC_METHOD,
    AUTHORED_OR_SPECIALIST_OWNS_LOAD,
    EXACT_REP_TARGETS_UNAVAILABLE,
    TARGET_ALREADY_TRAINED,
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
    /** Manual bodyweight sets use their signed prescription without a numerical proposal. */
    data class ManualBodyweight(val slotId: String): CustomBasicHistoryResult
    data class UnloadedReviewed(
        val historyWorkoutId:String,
        val completionOrder:Long,
        val candidate:BasicUnloadedProgressionResult.Candidate,
        val blockId:String,
        val weekday:Int,
        val slotId:String
    ):CustomBasicHistoryResult {
        val mayIssueWorkout:Boolean get()=false
        val maySaveProgram:Boolean get()=false
        val mayModifyHistory:Boolean get()=false
    }
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

        if(saved.progressionMethod in setOf(SavedProgressionMethod.REP_TARGETS,
                SavedProgressionMethod.LADDER,SavedProgressionMethod.BODYWEIGHT_REPS) &&
            selected.sets.any{set->set.prescription.type==DraftSetType.WORKING &&
                set.prescription.exactRepTarget?.let{
                    it in set.prescription.reps.minimum..set.prescription.reps.maximum
                }!=true})
            return no(CustomBasicHistoryRefusal.EXACT_REP_TARGETS_UNAVAILABLE)
        val plan=BasicProgramProgressionPlanDomain.review(cycle,
            reviewedCatalog = request.reviewedCatalog, experience = request.experience,
            previewWeek = ProgramProgressionWeek(block.blockId, next.weekInBlock))
            as? ProgramProgressionPlan.Ready
            ?:return no(CustomBasicHistoryRefusal.INVALID_NEXT_SOURCE)
        val assignment=plan.assignments.singleOrNull { it.key==
            ProgramProgressionSlotKey(block.blockId,next.weekday,request.slotId) }
            ?:return no(CustomBasicHistoryRefusal.INVALID_NEXT_SOURCE)
        val unweighted=assignment.route==ProgramProgressionRoute.BASIC_BODYWEIGHT_REPS
        val e1rm=assignment.route==ProgramProgressionRoute.E1RM_COACH_REVIEW &&
            saved.progressionMethod==SavedProgressionMethod.E1RM
        val strategy=when(assignment.route) {
            ProgramProgressionRoute.MANUAL -> BasicProgressionStrategy.MANUAL_LOGGING
            ProgramProgressionRoute.BASIC_WEIGHTED -> assignment.basicStrategy
            ProgramProgressionRoute.E1RM_COACH_REVIEW ->
                BasicProgressionStrategy.STRICT_DOUBLE_PROGRESSION.takeIf{e1rm}
            else -> null
        }
        if(!unweighted && strategy==null)
            return no(CustomBasicHistoryRefusal.AUTHORED_OR_SPECIALIST_OWNS_LOAD)
        if(unweighted && (request.selectedMachineInstanceId!=null ||
            selected.machineInstanceId!=null))
            return no(CustomBasicHistoryRefusal.INVALID_NEXT_SOURCE)

        val working=selected.sets.filter {it.prescription.type==DraftSetType.WORKING}
        if (working.size !in 1..32)
            return no(CustomBasicHistoryRefusal.INVALID_NEXT_SOURCE)
        val manual = strategy == BasicProgressionStrategy.MANUAL_LOGGING
        if (manual && working.all { it.prescription.load == DraftSetLoad.ExplicitBodyweight }) {
            if (request.selectedMachineInstanceId != null || next.stamp.gymId.isBlank() ||
                block.blockId.isBlank() || selected.exerciseId.isBlank())
                return no(CustomBasicHistoryRefusal.INVALID_NEXT_SOURCE)
            return CustomBasicHistoryResult.ManualBodyweight(selected.slotId)
        }
        if(working.any {it.prescription.percentageIntent!=null})
            return no(CustomBasicHistoryRefusal.AUTHORED_OR_SPECIALIST_OWNS_LOAD)
        if(!manual && (working.any {it.prescription.amplifier!=null} ||
            working.map {it.prescription.reps}.distinct().size!=1))
            return no(CustomBasicHistoryRefusal.INVALID_NEXT_SOURCE)
        val targetReps=working.first().prescription.reps
        if(unweighted && working.any{
            it.prescription.load!=DraftSetLoad.ExplicitBodyweight
        })return no(CustomBasicHistoryRefusal.INVALID_NEXT_SOURCE)
        val loads=if(unweighted)emptyList() else working.map {
            it.prescription.load as? DraftSetLoad.Verified
                ?:return no(CustomBasicHistoryRefusal.INVALID_NEXT_SOURCE)
        }
        if(!unweighted && loads.map {it.unit to it.basis}.distinct().size!=1)
            return no(CustomBasicHistoryRefusal.INVALID_NEXT_SOURCE)
        val unit=loads.firstOrNull()?.unit
        val basis=loads.firstOrNull()?.basis
        // A bound physical Machine A/B is not a free barbell, regardless of
        // an old BARBELL_TOTAL label. Manual logging can retain its exact
        // source targets; coached progression cannot earn an increase from
        // a mislabeled physical station or convert it into barbell strength.
        if(!manual && basis!=null &&
            KotlinSetNumberAuthorityDomain.machineTagContradictsFreeBarbell(
                basis, request.selectedMachineInstanceId ?: selected.machineInstanceId))
            return no(CustomBasicHistoryRefusal.INVALID_NEXT_SOURCE)
        val exactReps=if(unweighted || strategy in setOf(
                BasicProgressionStrategy.REP_TARGET_PROGRESSION,
                BasicProgressionStrategy.REP_LADDER_PROGRESSION)) {
            working.map {it.prescription.exactRepTarget
                ?:return no(CustomBasicHistoryRefusal.EXACT_REP_TARGETS_UNAVAILABLE)}
        } else null
        val normalizedMachine=KotlinMachineAlternativesDomain.normalizeMachineId(
            request.selectedMachineInstanceId)
        val scope=if(unweighted)null else BasicProgressionScope(
            next.stamp.athleteId,cycle.cycleId,next.stamp.gymId,
            next.blockId,next.blockId+":d"+next.weekday,selected.exerciseId,
            request.selectedMachineInstanceId,checkNotNull(unit),checkNotNull(basis))
        if(scope!=null && !scope.valid())
            return no(CustomBasicHistoryRefusal.INVALID_NEXT_SOURCE)

        if(manual) {
            val repeat=BasicProgressionPolicyDomain.review(BasicProgressionRequest(
                BasicProgressionStrategy.MANUAL_LOGGING,request.nextWorkoutId,checkNotNull(scope),null,null,targetReps,
                loads.map{it.value},null,request.maximumAuthorizedIncrement))
            return when(repeat) {
                is BasicProgressionResult.Candidate -> CustomBasicHistoryResult.Reviewed(
                    null,null,repeat,next.blockId,next.weekday,request.slotId,
                    request.selectedMachineInstanceId)
                is BasicProgressionResult.Refused ->
                    no(CustomBasicHistoryRefusal.INVALID_NEXT_SOURCE,repeat.reason)
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
        var latestStationIncompatible=false
        var latestSourceChanged=false
        for(session in request.completed) {
            val snap=session.snapshot
            val evidence=session.evidence
            if(snap.stamp.athleteId!=cycle.athleteId ||
                snap.stamp.athleteId!=evidence.athleteId ||
                snap.stamp.cycleId!=evidence.cycleId ||
                snap.stamp.cycleRevision!=evidence.cycleRevision ||
                snap.shortSessionOmittedSetKeys!=evidence.shortSessionOmittedSetKeys)
                return no(CustomBasicHistoryRefusal.INVALID_HISTORY)
            if(snap.stamp.cycleId!=cycle.cycleId ||
                snap.blockId!=next.blockId || snap.weekday!=next.weekday)
                continue
            // Calendar ownership is independent of which gym or station
            // supplied the performance. Only an upcoming day can advance.
            if(snap.absoluteWeek>=next.absoluteWeek)
                return no(CustomBasicHistoryRefusal.TARGET_ALREADY_TRAINED)
            if(snap.stamp.gymId!=next.stamp.gymId)continue
            // A verified B visit is independent of A's physical load, but
            // not independent of the approved coaching source. An
            // intervening changed gym inventory/profile/phase cannot be
            // silently skipped to resurrect an older A increase.
            val sourceMatches=snap.stamp==next.stamp &&
                snap.phase==next.phase &&
                snap.blockPurpose==next.blockPurpose &&
                snap.split==next.split &&
                snap.sessionPurpose==next.sessionPurpose
            if(!sourceMatches) {
                latestStationIncompatible=true
                latestSourceChanged=true
                latest=session
                continue
            }
            val historicalSlot=snap.slots.singleOrNull {it.slotId==request.slotId}
            if(historicalSlot==null) {
                // Native Swap issues a NEW slot ID and new set keys. A later
                // same-day replacement must not vanish from the progression
                // ledger and promote the older original slot's success.
                // Only independently verified work performed EXCLUSIVELY on
                // other physical machines can leave this station's history
                // unchanged. Unknown/skipped stations fail closed.
                val independentlyVerifiedStation=if(unweighted)null else
                    CustomVerifiedSwappedStationEvidenceDomain
                        .machinesWhenOriginalSlotAbsent(session,request.slotId)
                        ?.singleOrNull()
                val definitelyOtherMachine=independentlyVerifiedStation!=null &&
                    independentlyVerifiedStation!=normalizedMachine
                if(definitelyOtherMachine)continue
                latestStationIncompatible=true
                latestSourceChanged=false
                latest=session
                continue
            }
            // Do not discard a more recent substitution BEFORE inspecting
            // its actual machine. A same-station swapped lift must block an
            // older successful set from earning a weight increase. Only a
            // fully attributed DIFFERENT physical station is independent.
            val visits=evidence.visits.filter {
                it.slotId==request.slotId &&
                it.part==CustomWorkoutVisitPart.MAIN &&
                it.setType==DraftSetType.WORKING
            }
            if(visits.isEmpty())
                return no(CustomBasicHistoryRefusal.INVALID_HISTORY)
            // Machine comparison must preserve raw absent actual IDs.
            // normalizeMachineId(null) creates a compatibility sentinel,
            // which is NOT evidence that a physical station was used.
            val actualMachineIds=visits.mapNotNull{it.actual}.map{
                it.machineInstanceId
            }
            if(actualMachineIds.any {it!=null &&
                !KotlinMachineAlternativesDomain.validInstanceId(it)})
                return no(CustomBasicHistoryRefusal.INVALID_HISTORY)
            val observedMachines=actualMachineIds.filterNotNull().toSet()
            // Skip only visits fully attributed to a different physical station.
            // Ambiguous visits stay candidates until a newer visit supersedes them.
            // A single recorded B set is not proof that a second
            // unrecorded/failed working set was also performed on B. Use
            // the SAME complete-receipt authority as native Swap evidence.
            val independentStation=if(unweighted)null else
                CustomVerifiedSwappedStationEvidenceDomain
                    .machinesForVerifiedSlot(session,request.slotId)
                    ?.singleOrNull()
            if(independentStation!=null && independentStation!=normalizedMachine)
                continue
            latestSourceChanged=false
            latestStationIncompatible =
                (unweighted && (historicalSlot.machineInstanceId != null || observedMachines.isNotEmpty())) ||
                (historicalSlot.machineInstanceId != null && observedMachines.isEmpty()) ||
                (observedMachines.isNotEmpty() && (observedMachines.size != 1 ||
                    normalizedMachine !in observedMachines || actualMachineIds.any { it == null }))
            latest=session
        }
        val prior=latest ?:return no(CustomBasicHistoryRefusal.NO_COMPARABLE_HISTORY)
        if(latestSourceChanged)
            return no(CustomBasicHistoryRefusal.HISTORY_SOURCE_CHANGED)
        if(latestStationIncompatible)
            return no(CustomBasicHistoryRefusal.LAST_VISIT_INCOMPATIBLE)
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
        // The same named slot is not proof of the same authored movement.
        // Machine, incline and attachment changes can have distinct loads.
        if(oldSlot.exerciseId!=selected.exerciseId ||
            oldSlot.familyId!=selected.familyId ||
            oldSlot.variantId!=selected.variantId ||
            oldSlot.attachmentId!=selected.attachmentId)
            return no(CustomBasicHistoryRefusal.LAST_VISIT_INCOMPATIBLE)
        val expected=oldSlot.sets.filter {it.prescription.type==DraftSetType.WORKING}
        val observations=prior.evidence.visits.filter {
            it.slotId==request.slotId &&
            it.part==CustomWorkoutVisitPart.MAIN &&
            it.setType==DraftSetType.WORKING
        }
        if(expected.size!=working.size || observations.size!=expected.size ||
            expected.any{it.prescription.amplifier!=null || it.prescription.percentageIntent!=null} ||
            expected.map{it.setKey}.distinct().size!=expected.size ||
            observations.map{it.setKey}.toSet()!=expected.map{it.setKey}.toSet() ||
            observations.map{it.setKey}.distinct().size!=observations.size)
            return no(CustomBasicHistoryRefusal.LAST_VISIT_INCOMPATIBLE)
        val byKey=observations.associateBy {it.setKey}
        val facts=expected.mapIndexed {ordinal,item ->
            val observed=byKey.getValue(item.setKey)
            if(!observed.matchesIssuedMain(oldSlot, item) ||
                item.prescription.reps!=targetReps ||
                (e1rm && (item.prescription.targetRir==null ||
                    item.prescription.targetRir!=working[ordinal].prescription.targetRir)) ||
                observed.amplifierKind!=null)
                return no(CustomBasicHistoryRefusal.LAST_VISIT_INCOMPATIBLE)
            val issued=if(unweighted) {
                if(item.prescription.load!=DraftSetLoad.ExplicitBodyweight ||
                    observed.actual?.let {it.load!=null || it.unit!=null ||
                        it.machineInstanceId!=null}==true)
                    return no(CustomBasicHistoryRefusal.LAST_VISIT_INCOMPATIBLE)
                null
            } else {
                val prescribed=observed.prescribedLoad as? DraftSetLoad.Verified
                    ?:return no(CustomBasicHistoryRefusal.LAST_VISIT_INCOMPATIBLE)
                if(prescribed.unit!=unit || prescribed.basis!=basis ||
                    observed.actual!=null &&
                        (observed.actual.unit!=unit ||
                            KotlinMachineAlternativesDomain.normalizeMachineId(
                                observed.actual.machineInstanceId)!=normalizedMachine))
                    return no(CustomBasicHistoryRefusal.LAST_VISIT_INCOMPATIBLE)
                prescribed.value
            }
            WorkingSetFact(
                ordinal,
                when(observed.outcome) {
                    KotlinJournalSetOutcome.COMPLETED -> SetOutcome.COMPLETED
                    KotlinJournalSetOutcome.SKIPPED -> SetOutcome.SKIPPED
                    KotlinJournalSetOutcome.FAILED_ATTEMPT -> SetOutcome.FAILED_ATTEMPT
                },
                observed.actual?.load,observed.actual?.reps,
                observed.actual?.actualRir,issued,item.prescription.targetRir)
        }
        if(unweighted) {
            val unloadedScope=BasicUnloadedProgressionScope(
                next.stamp.athleteId,next.stamp.gymId,cycle.cycleId,
                next.blockId,next.blockId+":d"+next.weekday,selected.exerciseId)
            val observed=BasicUnloadedWorkoutEvidence(
                prior.evidence.workoutId,selected.exerciseId,targetReps,
                expected.size,facts,unloadedScope.trainingDayId)
            return when(val evaluated=BasicBodyweightRepProgressionDomain.review(
                BasicUnloadedProgressionRequest(request.nextWorkoutId,
                    unloadedScope,unloadedScope,observed,
                    working.map {it.prescription.load},checkNotNull(exactReps),
                    targetReps))) {
                is BasicUnloadedProgressionResult.Candidate ->
                    CustomBasicHistoryResult.UnloadedReviewed(
                        prior.evidence.workoutId,prior.completionOrder,evaluated,
                        next.blockId,next.weekday,request.slotId)
                is BasicUnloadedProgressionResult.Rejected ->
                    no(CustomBasicHistoryRefusal.LAST_VISIT_INCOMPATIBLE)
            }
        }
        val validatedScope=checkNotNull(scope)
        val priorEvidence=ProgressionWorkoutEvidence(
            prior.evidence.workoutId,selected.exerciseId,checkNotNull(unit),
            targetReps,expected.size,facts,validatedScope.trainingDayId)
        val calculated=BasicProgressionPolicyDomain.review(BasicProgressionRequest(
            checkNotNull(strategy),request.nextWorkoutId,
            validatedScope,validatedScope,priorEvidence,targetReps,
            loads.map{it.value},request.verifiedStock,
            request.maximumAuthorizedIncrement,exactReps,effortAdjustedE1rm=e1rm))
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
