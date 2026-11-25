package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import java.util.Collections

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
        val proposal:CustomCycleBuilderResult.Proposed,
        val tierTransitions:List<TieredLinearTransition> = emptyList()
    ):CustomPercentageDraftResult {
        val requiresReviewedReset:Boolean get()=tierTransitions.any{it.decision==TieredLinearDecision.RESET_REVIEW}
        val mayIssueWorkout:Boolean get()=false
        val mayModifyHistory:Boolean get()=false
        val maySaveProgram:Boolean get()=false
    }
}

enum class CustomPercentageRecheckRefusal {
    NOT_EXPLICITLY_ACCEPTED, REFERENCE_OR_STOCK_CHANGED, HISTORY_CHANGED,
    SOURCE_OR_RECIPE_CHANGED, PROPOSAL_CHANGED
}

sealed interface CustomPercentageRecheckResult {
    data class Refused(val reason:CustomPercentageRecheckRefusal):CustomPercentageRecheckResult
    data class Reconfirmed(val proposal:CustomPercentageDraftResult.Proposed,
        val historyRevision:Long):CustomPercentageRecheckResult {
        val maySaveProgram:Boolean get()=false
        val mayIssueWorkout:Boolean get()=false
    }
}

/**
 * Non-writing comparison used at the approval boundary. The native caller
 * must obtain the current source, stock, reviewed reference and history
 * revision from authenticated storage in a single owner transaction.
 * No caller-provided receipt, flag or equality result grants consent.
 */
object CustomPercentageDraftRecheckDomain {
    fun recheck(
        displayed:CustomPercentageDraftResult.Proposed,
        reviewedRequest:NamedPercentageRequest,
        currentSource:CustomPercentageDraftSource,
        currentRequest:NamedPercentageRequest,
        reviewedHistoryRevision:Long,
        currentHistoryRevision:Long,
        explicitlyAccepted:Boolean
    ):CustomPercentageRecheckResult {
        fun no(reason:CustomPercentageRecheckRefusal)=CustomPercentageRecheckResult.Refused(reason)
        if(!explicitlyAccepted)return no(CustomPercentageRecheckRefusal.NOT_EXPLICITLY_ACCEPTED)
        if(reviewedHistoryRevision !in 1..9_007_199_254_740_991L ||
            currentHistoryRevision!=reviewedHistoryRevision)
            return no(CustomPercentageRecheckRefusal.HISTORY_CHANGED)
        if(!currentRequest.referenceConfirmed || !reviewedRequest.referenceConfirmed ||
            currentRequest.referenceKind!=reviewedRequest.referenceKind ||
            currentRequest.confirmedReference!=reviewedRequest.confirmedReference ||
            currentRequest.verifiedStock==null ||
            currentRequest.verifiedStock!=reviewedRequest.verifiedStock)
            return no(CustomPercentageRecheckRefusal.REFERENCE_OR_STOCK_CHANGED)
        if(currentRequest!=reviewedRequest || currentSource.stamp!=displayed.source ||
            currentSource.cycle!=displayed.proposal.receipt.original ||
            currentSource.weekInBlock!=displayed.weekInBlock ||
            ProgramProgressionSlotKey(currentSource.blockId,currentSource.weekday,
                currentSource.slotId)!=displayed.key)
            return no(CustomPercentageRecheckRefusal.SOURCE_OR_RECIPE_CHANGED)
        val fresh=CustomPercentageDraftDomain.review(currentSource,currentRequest)
            as? CustomPercentageDraftResult.Proposed
            ?:return no(CustomPercentageRecheckRefusal.PROPOSAL_CHANGED)
        if(fresh!=displayed)return no(CustomPercentageRecheckRefusal.PROPOSAL_CHANGED)
        return CustomPercentageRecheckResult.Reconfirmed(fresh,currentHistoryRevision)
    }
}

object CustomPercentageDraftDomain {
    private data class Projected(val load:BigDecimal,val reps:Int,val intent:CustomPercentageSetIntent)
    private data class Projection(val sets:List<Projected>,
        val transitions:List<TieredLinearTransition> = emptyList())

    fun review(source:CustomPercentageDraftSource,request:NamedPercentageRequest):CustomPercentageDraftResult =
        reviewSource(source,request.scope,request.tier,request.approvedSetBudget,
            request.weekInBlock,request.blockWeeks) { block ->
            val numeric=NamedPercentageProtocolDomain.review(request)
                as? NamedPercentageResult.Candidate ?:return@reviewSource null
            val reference=request.confirmedReference ?:return@reviewSource null
            Projection(numeric.sets.map{set->
                Projected(set.verifiedLoad,set.minimumReps,CustomPercentageSetIntent(
                    request.scheme,request.tier,request.dayRole,source.weekInBlock,
                    block.weeks,set.sourceOrdinal,numeric.sourceSetCount,reference,
                    set.sourcePercent,set.effectivePercent,null,set.repOut,numeric.recovery))
            })
        }

    /** Replay a complete owner-supplied tier ledger before drafting. Stable slot
     * identity is mandatory here: identical lifts on the same day can coexist. */
    fun reviewTiered(source:CustomPercentageDraftSource,request:TieredLinearRequest):CustomPercentageDraftResult {
        if(request.sourceSlotId!=source.slotId)
            return CustomPercentageDraftResult.Refused(CustomPercentageDraftRefusal.SCOPE_MISMATCH)
        return reviewSource(source,request.scope,request.tier,request.approvedSetBudget,null,null) { block ->
            val numeric=TieredLinearProgressionDomain.review(request)
                as? TieredLinearResult.Candidate ?:return@reviewSource null
            val count=numeric.targets.size
            val size=numeric.sourceSetCount
            val indexes=if(count>size) List(count-size){0}+(0 until size).toList()
                else NamedPercentageProtocolDomain.trimmedSourceIndexes(size,count,size-1,
                    if(numeric.recovery)emptyList() else listOf(size-1))
            val hundred=BigDecimal("100")
            val effective=if(numeric.recovery) BigDecimal("60") else hundred
            Projection(numeric.targets.mapIndexed{index,set->
                Projected(set.load,set.minimumReps,CustomPercentageSetIntent(
                    NamedPercentageScheme.GZCLP,request.tier,PercentageDayRole.STANDARD,
                    source.weekInBlock,block.weeks,indexes[index]+1,size,numeric.state.load,
                    hundred,effective,numeric.state.stage,set.repOut,numeric.recovery))
            },numeric.transitions)
        }
    }

    /** Combine independently rederived slot edits into ONE revision of
     * their common base. Used for fixed prescriptions, tier roots and later
     * same-session tier transitions. Numerical authority stays in review
     * and reviewTiered; this structural projection never grants consent.
     * A conflicting, stale or unrelated edit refuses the whole batch. */
    fun combineReviewedWeekEdits(base:CustomTrainingCycle,
        proposals:List<CustomPercentageDraftResult.Proposed>):CustomTrainingCycle? {
        if(proposals.size !in 1..3000 || base.revision !in 1..9_007_199_254_740_990L)return null
        val stamp=proposals.first().source
        if(stamp.cycleRevision!=base.revision || stamp.cycleId!=base.cycleId ||
            stamp.athleteId!=base.athleteId)return null
        val keys=HashSet<CustomNamedPercentageWeekKey>()
        val overrides=base.weekOverrides.toMutableMap()
        for(p in proposals) {
            if(p.source!=stamp || p.proposal.receipt.original!=base)return null
            val key=CustomNamedPercentageWeekKey(p.key.blockId,p.weekInBlock,p.key.weekday,p.key.slotId)
            if(!keys.add(key))return null
            val original=base.weekOverrides[key.blockId].orEmpty()
            if(original.any{it.weekInBlock==key.weekInBlock && it.weekday==key.weekday && it.slotId==key.slotId})
                return null
            val edit=p.proposal.draft.weekOverrides[key.blockId].orEmpty().singleOrNull{
                it.weekInBlock==key.weekInBlock && it.weekday==key.weekday && it.slotId==key.slotId
            }?:return null
            // Each independent projection must change only its selected
            // week-slot. Do not propagate other fields from a proposal.
            val exact=base.copy(revision=base.revision+1,
                weekOverrides=base.weekOverrides+(key.blockId to (original+edit)))
            if(p.proposal.draft!=exact)return null
            overrides[key.blockId]=overrides[key.blockId].orEmpty()+edit
        }
        return base.copy(revision=base.revision+1,weekOverrides=overrides.toMap())
    }

    private fun reviewSource(
        source:CustomPercentageDraftSource,scope:BasicProgressionScope,tier:PercentageTier,
        budget:Int,requestedWeek:Int?,requestedBlockWeeks:Int?,
        compute:(CustomTrainingBlock)->Projection?
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
            (requestedWeek!=null && requestedWeek!=source.weekInBlock) ||
            (requestedBlockWeeks!=null && requestedBlockWeeks!=block.weeks) ||
            block.purpose!=CustomBlockPurpose.TRAINING)
            return no(CustomPercentageDraftRefusal.STALE_OR_INVALID_SOURCE)
        if(cycle.weekOverrides[block.blockId].orEmpty().any{
                it.weekInBlock==source.weekInBlock && it.weekday==source.weekday &&
                    it.slotId==source.slotId})
            return no(CustomPercentageDraftRefusal.PROTECTED_WEEK)

        if(!scope.valid() || scope.athleteId!=stamp.athleteId ||
            scope.programId!=stamp.cycleId || scope.gymId!=stamp.gymId ||
            scope.blockId!=block.blockId || scope.trainingDayId!=block.blockId+":d"+day.weekday ||
            scope.exerciseId!=slot.exerciseId || scope.machineInstanceId!=slot.machineInstanceId)
            return no(CustomPercentageDraftRefusal.SCOPE_MISMATCH)
        if(slot.role!=when(tier) {
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
        if(working.size!=budget || working.isEmpty())
            return no(CustomPercentageDraftRefusal.BUDGET_MISMATCH)
        val numeric=compute(block) ?:return no(CustomPercentageDraftRefusal.PROTOCOL_REFUSED)
        if(numeric.sets.size!=working.size)return no(CustomPercentageDraftRefusal.BUDGET_MISMATCH)
        var workingIndex=0
        val replacement=slot.sets.map{original->
            if(original.type!=DraftSetType.WORKING) original
            else {
                val set=numeric.sets[workingIndex++]
                val projected=original.copy(
                    load=DraftSetLoad.Verified(scope.unit,scope.basis,set.load),
                    reps=RepBand(set.reps,set.reps),exactRepTarget=set.reps,
                    percentageIntent=set.intent)
                if(!set.intent.validForSet(projected))
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
            edited,Collections.unmodifiableList(numeric.transitions.toList()))
    }
}
