package io.github.extracteddeath.pursuitiron.rebuild

/**
 * The source-bound authoring bridge for named percentage intent. Recompute
 * the numerical candidate from the actual selected slot and supplied reviewed
 * reference/stock, never translate a caller-supplied fabricated candidate.
 * No history mutation, signoff, saved-plan mutation or workout issuance.
 */
data class CustomPercentageDraftSource(
    val cycle: CustomTrainingCycle,
    val stamp: CustomWorkoutSourceStamp,
    val blockId: String,
    val weekInBlock: Int,
    val weekday: Int,
    val slotId: String
)

enum class CustomPercentageDraftRefusal {
    STALE_OR_INVALID_SOURCE, SCOPE_MISMATCH, PROTECTED_WEEK,
    UNSUPPORTED_SLOT, BUDGET_MISMATCH, PROTOCOL_REFUSED,
    INVALID_PROJECTED_SET, BUILDER_REFUSED
}

sealed interface CustomPercentageDraftResult {
    data class Refused(val reason:CustomPercentageDraftRefusal):CustomPercentageDraftResult
    data class Proposed(
        val source:CustomWorkoutSourceStamp,
        val key:ProgramProgressionSlotKey,
        val weekInBlock:Int,
        val originalSets:List<CustomAuthoredSet>,
        val proposal:CustomCycleBuilderResult.Proposed
    ):CustomPercentageDraftResult {
        val mayIssueWorkout:Boolean get()=false
        val mayModifyHistory:Boolean get()=false
        val maySaveProgram:Boolean get()=false
    }
}

object CustomPercentageDraftDomain {
    fun review(
        source:CustomPercentageDraftSource,
        request:NamedPercentageRequest
    ):CustomPercentageDraftResult {
        fun no(reason:CustomPercentageDraftRefusal)=CustomPercentageDraftResult.Refused(reason)
        val cycle=source.cycle
        val stamp=source.stamp
        if(cycle.cycleId!=stamp.cycleId || cycle.athleteId!=stamp.athleteId ||
            cycle.revision!=stamp.cycleRevision ||
            cycle.revision !in 1..9_007_199_254_740_990L ||
            stamp.athleteProfileRevision.isBlank() || stamp.gymId.isBlank() ||
            stamp.gymCatalogSourceRevision !in 1..9_007_199_254_740_991L ||
            source.weekday !in 0..6 || source.slotId.isBlank())
            return no(CustomPercentageDraftRefusal.STALE_OR_INVALID_SOURCE)
        val block=cycle.blocks.singleOrNull{it.blockId==source.blockId}
            ?:return no(CustomPercentageDraftRefusal.STALE_OR_INVALID_SOURCE)
        val day=block.days.singleOrNull{it.weekday==source.weekday}
            ?:return no(CustomPercentageDraftRefusal.STALE_OR_INVALID_SOURCE)
        val slot=day.slots.singleOrNull{it.slotId==source.slotId}
            ?:return no(CustomPercentageDraftRefusal.STALE_OR_INVALID_SOURCE)
        if(source.weekInBlock !in 1..block.weeks ||
            request.weekInBlock!=source.weekInBlock || request.blockWeeks!=block.weeks ||
            block.purpose!=CustomBlockPurpose.TRAINING)
            return no(CustomPercentageDraftRefusal.STALE_OR_INVALID_SOURCE)
        if(cycle.weekOverrides[block.blockId].orEmpty().any{
                it.weekInBlock==source.weekInBlock && it.weekday==source.weekday &&
                    it.slotId==source.slotId})
            return no(CustomPercentageDraftRefusal.PROTECTED_WEEK)

        val scope=request.scope
        if(!scope.valid() || scope.athleteId!=stamp.athleteId ||
            scope.programId!=stamp.cycleId || scope.gymId!=stamp.gymId ||
            scope.blockId!=block.blockId || scope.trainingDayId!=block.blockId+":d"+day.weekday ||
            scope.exerciseId!=slot.exerciseId || scope.machineInstanceId!=slot.machineInstanceId)
            return no(CustomPercentageDraftRefusal.SCOPE_MISMATCH)
        if(slot.role!=when(request.tier) {
                PercentageTier.PRIMARY -> ComposerRole.PRIMARY
                PercentageTier.SECONDARY -> ComposerRole.SECONDARY
            } || slot.sets.size !in 1..20 || slot.supersetGroup!=null ||
            slot.sets.any{set->
                val load=set.load as? DraftSetLoad.Verified
                load==null || load.unit!=scope.unit || load.basis!=scope.basis ||
                    set.amplifier!=null || set.percentageIntent!=null ||
                    !KotlinSetNumberAuthorityDomain.validPrescribedLoad(load.value,load.basis) ||
                    CustomWorkoutTimingDomain.setSeconds(set)==null
            })return no(CustomPercentageDraftRefusal.UNSUPPORTED_SLOT)
        val working=slot.sets.filter{it.type==DraftSetType.WORKING}
        if(working.size!=request.approvedSetBudget || working.isEmpty())
            return no(CustomPercentageDraftRefusal.BUDGET_MISMATCH)
        val numeric=NamedPercentageProtocolDomain.review(request)
            as? NamedPercentageResult.Candidate
            ?:return no(CustomPercentageDraftRefusal.PROTOCOL_REFUSED)
        val reference=request.confirmedReference
            ?:return no(CustomPercentageDraftRefusal.PROTOCOL_REFUSED)
        var workingIndex=0
        val replacement=slot.sets.map{original->
            if(original.type!=DraftSetType.WORKING) original
            else {
                val set=numeric.sets[workingIndex++]
                val intent=CustomPercentageSetIntent(
                    request.scheme,request.tier,request.dayRole,request.weekInBlock,
                    request.blockWeeks,set.sourceOrdinal,numeric.sourceSetCount,
                    reference,set.sourcePercent,set.effectivePercent,null,set.repOut,numeric.recovery)
                val projected=original.copy(
                    load=DraftSetLoad.Verified(scope.unit,scope.basis,set.verifiedLoad),
                    reps=RepBand(set.minimumReps,set.minimumReps),
                    exactRepTarget=set.minimumReps,percentageIntent=intent)
                if(!intent.validForSet(projected))
                    return no(CustomPercentageDraftRefusal.INVALID_PROJECTED_SET)
                projected
            }
        }
        // The ordinary revisioned draft editor preserves every other week,
        // slot and block. This is an undoable proposal, not owner consent.
        val edited=CustomCycleBuilderDomain.editWeek(cycle,stamp.cycleRevision,
            block.blockId,source.weekInBlock,day.weekday,slot.slotId,replacement)
            as? CustomCycleBuilderResult.Proposed
            ?:return no(CustomPercentageDraftRefusal.BUILDER_REFUSED)
        return CustomPercentageDraftResult.Proposed(stamp,
            ProgramProgressionSlotKey(block.blockId,day.weekday,slot.slotId),
            source.weekInBlock,edited.receipt.original.blocks.single{it.blockId==block.blockId}
                .days.single{it.weekday==day.weekday}.slots.single{it.slotId==slot.slotId}.sets,
            edited)
    }
}
