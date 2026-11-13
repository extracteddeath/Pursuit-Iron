package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import java.util.Collections

/**
 * Evidence-only handoff at the FIRST week of a subsequent custom cycle block.
 *
 * The current native custom coach correctly refuses to award reps or weight
 * from a different block/revision. This domain can still display the LAST
 * verified exposure as a prior baseline when it is truly comparable.
 * Inputs must come from the complete, owner-fenced, corrected native ledger,
 * not a UI-selected "best" workout. Even a positive result NEVER proposes
 * or writes a load, authorizes progression, issues sets or changes history.
 */
data class CustomBlockBaselineRequest(
    val next:CustomWorkoutDaySnapshot,
    val slotId:String,
    val completed:List<CustomCoachingHistoricalSession>,
    val verifiedStock:VerifiedLoadStock?
)

enum class CustomBlockBaselineStatus {
    INVALID_HISTORY,
    NOT_BLOCK_ENTRY,
    TARGET_ALREADY_TRAINED,
    NO_PRIOR_SAME_WEEKDAY,
    GAP_OR_SAME_BLOCK,
    EXERCISE_OR_VARIANT_CHANGED,
    ATHLETE_OR_GYM_SOURCE_CHANGED,
    PHASE_PURPOSE_OR_SPLIT_CHANGED,
    PRESCRIPTION_CHANGED,
    PREVIOUS_WORKOUT_UNVERIFIED_OR_INCOMPLETE,
    EQUIPMENT_UNVERIFIED,
    VERIFIED_CONTEXT_ONLY,
    /** Phase/purpose changed, but exact observed work remains useful as a
     * historic reference; never a carried progression authorization. */
    VERIFIED_PHASE_REFERENCE_ONLY
}

data class CustomBlockBaselineSet(
    val ordinal:Int,
    val priorLoad:BigDecimal,
    val loadUnit:LoadUnit,
    val performedReps:Int,
    /** Unreported actual RIR stays null; NEVER overwrite it with goal RIR. */
    val actualRir:Int?
)

data class CustomBlockBaselineReview(
    val status:CustomBlockBaselineStatus,
    val previousWorkoutId:String?,
    val previousBlockId:String?,
    val previousSlotId:String?,
    val priorWorkingSets:List<CustomBlockBaselineSet>,
    val explanation:String
) {
    val mayApplyProgression:Boolean get()=false
    val mayChangePrescription:Boolean get()=false
    val mayIssueWorkout:Boolean get()=false
    val mayModifyHistory:Boolean get()=false
}

/** Last same-day exposure wins, including a failed or changed exercise. */
internal data class CustomBlockExposureInspection(
    val baseline:CustomBlockBaselineReview,
    val previous:CustomCoachingHistoricalSession?=null,
    val priorSlot:CustomWorkoutSlotSnapshot?=null
)

object CustomBlockBaselineContinuityDomain {
    fun review(request:CustomBlockBaselineRequest):CustomBlockBaselineReview =
        inspect(request,true).baseline

    /** Shared factual verification; relaxed recipe comparison grants no progression. */
    internal fun inspect(request:CustomBlockBaselineRequest,
        exactPrescription:Boolean):CustomBlockExposureInspection {
        fun refuse(status:CustomBlockBaselineStatus,why:String)=
            CustomBlockExposureInspection(
                CustomBlockBaselineReview(status,null,null,null,emptyList(),why))
        val next=request.next
        if(request.completed.map {it.completionOrder}.let {
                it.any {order->order<1L} ||
                    it.zipWithNext().any {pair->pair.first>=pair.second}
            } ||
            request.completed.map {it.evidence.workoutId}.distinct().size!=
                request.completed.size ||
            request.completed.any {historical ->
                historical.evidence.athleteId!=historical.snapshot.stamp.athleteId ||
                    historical.evidence.cycleId!=historical.snapshot.stamp.cycleId ||
                    historical.evidence.cycleRevision!=historical.snapshot.stamp.cycleRevision ||
                    historical.evidence.workoutId.isBlank() ||
                    historical.evidence.journalRevision<1 ||
                    historical.snapshot.absoluteWeek !in 1..104
            })
            return refuse(CustomBlockBaselineStatus.INVALID_HISTORY,
                "Native completion order or archived workout provenance is invalid.")
        val nextSlot=next.slots.singleOrNull {it.slotId==request.slotId}
            ?:return refuse(CustomBlockBaselineStatus.EXERCISE_OR_VARIANT_CHANGED,
                "The new day has no unique selected exercise slot.")
        if(next.blockPurpose!=CustomBlockPurpose.TRAINING ||
            next.absoluteWeek !in 2..104 || next.weekInBlock!=1)
            return refuse(CustomBlockBaselineStatus.NOT_BLOCK_ENTRY,
                "Baseline handoff is only reviewed at the first training week of a new block.")
        val sameDay=request.completed.filter {session ->
            session.snapshot.stamp.athleteId==next.stamp.athleteId &&
                session.snapshot.stamp.cycleId==next.stamp.cycleId &&
                session.snapshot.weekday==next.weekday
        }
        if(sameDay.any {it.snapshot.absoluteWeek>=next.absoluteWeek})
            return refuse(CustomBlockBaselineStatus.TARGET_ALREADY_TRAINED,
                "The requested day is already completed; never reuse older best performances.")
        val previous=sameDay.lastOrNull()
            ?:return refuse(CustomBlockBaselineStatus.NO_PRIOR_SAME_WEEKDAY,
                "No finished exercise day on this exact weekday in the same cycle.")
        if(previous.snapshot.absoluteWeek!=next.absoluteWeek-1 ||
            previous.snapshot.blockId==next.blockId ||
            previous.snapshot.blockPurpose!=CustomBlockPurpose.TRAINING)
            return refuse(CustomBlockBaselineStatus.GAP_OR_SAME_BLOCK,
                "The adjacent preceding training week must belong to a different training block.")
        val old=previous.snapshot
        val matches=old.slots.filter {it.exerciseId==nextSlot.exerciseId}
        val priorSlot=matches.singleOrNull()
            ?:return refuse(CustomBlockBaselineStatus.EXERCISE_OR_VARIANT_CHANGED,
                "The most recent previous day lacks a unique matching exercise.")
        if(next.slots.count {it.exerciseId==nextSlot.exerciseId}!=1 ||
            priorSlot.familyId!=nextSlot.familyId ||
            priorSlot.variantId!=nextSlot.variantId ||
            priorSlot.attachmentId!=nextSlot.attachmentId ||
            priorSlot.machineInstanceId!=nextSlot.machineInstanceId ||
            priorSlot.role!=nextSlot.role)
            return refuse(CustomBlockBaselineStatus.EXERCISE_OR_VARIANT_CHANGED,
                "The physical A/B machine, equipment variant, attachment, exercise role or identity changed; do not transfer this baseline.")
        if(old.stamp.gymId!=next.stamp.gymId ||
            old.stamp.gymCatalogSourceRevision!=next.stamp.gymCatalogSourceRevision ||
            old.stamp.athleteProfileRevision!=next.stamp.athleteProfileRevision)
            return refuse(CustomBlockBaselineStatus.ATHLETE_OR_GYM_SOURCE_CHANGED,
                "Gym inventory or athlete source changed; start a new verified baseline.")
        if(old.split!=next.split)
            return refuse(CustomBlockBaselineStatus.PHASE_PURPOSE_OR_SPLIT_CHANGED,
                "A revised training split changes the weekly fatigue context; establish a fresh baseline.")
        val changedPhaseOrPurpose=old.phase!=next.phase ||
            old.sessionPurpose!=next.sessionPurpose
        val priorSets=priorSlot.sets
        val targetSets=nextSlot.sets
        val oldWorking=priorSets.filter {
            it.prescription.type==DraftSetType.WORKING
        }
        if(oldWorking.isEmpty() || oldWorking.size>32 ||
            priorSets.map{it.setKey}.distinct().size!=priorSets.size ||
            priorSets.map{it.ordinalInSlot}.distinct().size!=priorSets.size ||
            priorSets.any{it.ordinalInSlot<1 || it.setKey.isBlank()} ||
            (exactPrescription &&
                (priorSets.map {it.prescription}!=targetSets.map {it.prescription} ||
                priorSets.map {it.ordinalInSlot}!=targetSets.map {it.ordinalInSlot})) ||
            oldWorking.any {it.prescription.amplifier!=null} ||
            priorSlot.supersetGroup!=nextSlot.supersetGroup)
            return refuse(CustomBlockBaselineStatus.PRESCRIPTION_CHANGED,
                "Set count, order, weight, rep range, RIR, rest or technique changed.")
        val allFacts=previous.evidence.visits.filter {
            it.slotId==priorSlot.slotId
        }
        val workFacts=allFacts.filter {
            it.part==CustomWorkoutVisitPart.MAIN &&
                it.setType==DraftSetType.WORKING
        }
        if(workFacts.size!=oldWorking.size ||
            workFacts.map {it.setKey}.toSet()!=
                oldWorking.map {it.setKey}.toSet() ||
            allFacts.any {it.part==CustomWorkoutVisitPart.FOLLOW_UP})
            return refuse(CustomBlockBaselineStatus.PREVIOUS_WORKOUT_UNVERIFIED_OR_INCOMPLETE,
                "Missing, altered or technique-specific historical working receipts.")
        val stock=request.verifiedStock
        if(stock==null)
            return refuse(CustomBlockBaselineStatus.EQUIPMENT_UNVERIFIED,
                "Current approved physical equipment stock is unavailable.")
        val observed=ArrayList<CustomBlockBaselineSet>()
        for(set in oldWorking) {
            val fact=workFacts.singleOrNull {it.setKey==set.setKey}
                ?:return refuse(
                    CustomBlockBaselineStatus.PREVIOUS_WORKOUT_UNVERIFIED_OR_INCOMPLETE,
                    "Historical working-set receipts are not unique.")
            val prescription=set.prescription
            val issued=prescription.load as? DraftSetLoad.Verified
                ?:return refuse(CustomBlockBaselineStatus.EQUIPMENT_UNVERIFIED,
                    "Bodyweight and assisted loads need a separate baseline policy.")
            val actual=fact.actual
            if(fact.exerciseId!=priorSlot.exerciseId ||
                fact.slotId!=priorSlot.slotId ||
                fact.setType!=prescription.type ||
                fact.part!=CustomWorkoutVisitPart.MAIN ||
                fact.prescribedLoad!=issued ||
                fact.prescribedReps!=prescription.reps ||
                fact.prescribedRir!=prescription.targetRir ||
                fact.amplifierKind!=null || fact.effectiveEventId.isBlank() ||
                fact.outcome!=KotlinJournalSetOutcome.COMPLETED ||
                !CustomWorkoutJournalDomain.actualValid(issued,fact.outcome,actual) ||
                actual==null || actual.reps<prescription.reps.minimum ||
                actual.load?.compareTo(issued.value)!=0 || actual.unit!=issued.unit)
                return refuse(
                    CustomBlockBaselineStatus.PREVIOUS_WORKOUT_UNVERIFIED_OR_INCOMPLETE,
                    "The last workout did not finish all exact prescribed working sets.")
            // A/B performed-set receipts must agree with the approved
            // physical station; a legacy/unassigned receipt never proves A.
            // The same logical exercise and stack do not grant transfer.
            if(KotlinMachineAlternativesDomain.normalizeMachineId(
                    actual.machineInstanceId)!=
                KotlinMachineAlternativesDomain.normalizeMachineId(
                    nextSlot.machineInstanceId))
                return refuse(
                    CustomBlockBaselineStatus.PREVIOUS_WORKOUT_UNVERIFIED_OR_INCOMPLETE,
                    "The last working set was actually performed on a different or unassigned physical machine; establish a separate A/B baseline.")
            val inspected=EquipmentLoadDomain.inspect(stock,issued.value,issued.unit)
            if(stock.basis!=issued.basis || stock.unit!=issued.unit ||
                inspected !is LoadInspection.Confirmed || !inspected.exact)
                return refuse(CustomBlockBaselineStatus.EQUIPMENT_UNVERIFIED,
                    "The previous working weight is not buildable with the current verified gym.")
            observed+=CustomBlockBaselineSet(set.ordinalInSlot,issued.value,
                issued.unit,actual.reps,actual.actualRir)
        }
        return CustomBlockExposureInspection(CustomBlockBaselineReview(
            if(changedPhaseOrPurpose)
                CustomBlockBaselineStatus.VERIFIED_PHASE_REFERENCE_ONLY
            else CustomBlockBaselineStatus.VERIFIED_CONTEXT_ONLY,
            previous.evidence.workoutId,old.blockId,priorSlot.slotId,
            Collections.unmodifiableList(observed.toList()),
            (if(changedPhaseOrPurpose)
                "A new phase or session purpose reinterprets training intensity. "
            else "The adjacent previous block has comparable same-day work. ")+
                "The previous complete sets are factual reference only, not "+
                "a carried-over or automatically earned progression. New-block "+
                "weights, rep targets and reported RIR remain independently authored."),
            previous,priorSlot)
    }
}
