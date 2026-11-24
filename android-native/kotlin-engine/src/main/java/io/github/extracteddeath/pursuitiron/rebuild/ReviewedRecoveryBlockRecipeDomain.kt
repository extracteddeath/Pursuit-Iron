package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal

/**
 * Review-only recovery recipe for an existing future recovery block or a
 * new trailing recovery week. The owner establishes issuance eligibility.
 * The caller chooses the set cap, reserve floor and optional physical step
 * budget; none is a universal deload recommendation. No estimated load or
 * calendar change is made.
 */
data class ReviewedRecoveryBlockRecipeRequest(
    val source:AdaptiveCycleOptionRequest,
    val maximumWorkingSetsPerSlot:Int,
    val minimumTargetRir:Int,
    /** Null preserves all weights. Otherwise require exactly one easier
     * attainable setting per retained weighted working set, within this budget. */
    val maximumEasierStep:BigDecimal?=null,
    val verifiedMachineConfigurations:List<KotlinGymMachineAlternatives>?=null,
    /** Explicit opt-in to append after the final TRAINING block. The source
     * targetBlockId identifies that final block, not the new recovery ID. */
    val appendRecoveryBlockId:String?=null
)

enum class ReviewedRecoveryBlockRecipeStatus {
    INVALID_REQUEST, SOURCE_UNRESOLVED, PERMISSION_REQUIRED,
    NO_CHANGE, AUDIT_REFUSED, READY_FOR_REVIEW, LOAD_STEP_UNAVAILABLE
}

data class ReviewedRecoveryBlockRecipeResult(
    val status:ReviewedRecoveryBlockRecipeStatus,
    val candidate:AdaptiveCycleOption?,
    val comparison:AdaptiveCycleComparison?,
    val explanation:String
) {
    val mayIssueWorkout:Boolean get()=false
    val mayApplyAutomatically:Boolean get()=false
    val mayModifyHistory:Boolean get()=false
}

object ReviewedRecoveryBlockRecipeDomain {
    fun propose(request:ReviewedRecoveryBlockRecipeRequest):ReviewedRecoveryBlockRecipeResult {
        fun result(status:ReviewedRecoveryBlockRecipeStatus,why:String,
            option:AdaptiveCycleOption?=null,comparison:AdaptiveCycleComparison?=null)=
            ReviewedRecoveryBlockRecipeResult(status,option,comparison,why)
        val input=request.source
        val cycle=input.approvedCycle
        val budget=request.maximumEasierStep
        val appendId=request.appendRecoveryBlockId
        val anchor=cycle.blocks.singleOrNull{it.blockId==input.targetBlockId}
        val appending=appendId!=null
        if(anchor==null ||
            (!appending && anchor.purpose!=CustomBlockPurpose.RECOVERY) ||
            (appending && (anchor!=cycle.blocks.lastOrNull() ||
                anchor.purpose!=CustomBlockPurpose.TRAINING ||
                !Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,79}$").matches(appendId!!) ||
                cycle.blocks.any{it.blockId==appendId} ||
                cycle.blocks.size>=26 || cycle.blocks.sumOf{it.weeks.toLong()}>=104L)) ||
            request.maximumWorkingSetsPerSlot !in 1..32 ||
            request.minimumTargetRir !in 2..10 ||
            (budget!=null && (budget.signum()<=0 ||
                !KotlinSetNumberAuthorityDomain.validPhysicalLoadValue(budget))) ||
            cycle.revision !in 1 until 9_007_199_254_740_991L || input.alternatives.isNotEmpty() ||
            input.prospectiveByOptionId.isNotEmpty() ||
            input.sourceProspectiveByBlockId.isNotEmpty() ||
            (!appending && cycle.weekOverrides[anchor.blockId].orEmpty().isNotEmpty()))
            return result(ReviewedRecoveryBlockRecipeStatus.INVALID_REQUEST,
                "Choose an existing recovery template without overrides or append after the final training block, with explicit recipe settings.")
        val sourceReview=AdaptiveCycleOptionReviewDomain.review(input)
        if(sourceReview.sourceAudit.status!=CustomCoachAuditStatus.READY_FOR_ATHLETE_REVIEW)
            return result(ReviewedRecoveryBlockRecipeStatus.SOURCE_UNRESOLVED,
                "The complete source cycle must pass review before proposing a recovery recipe.",
                comparison=sourceReview)
        if(appending && !input.permissions.adaptBlocks)
            return result(ReviewedRecoveryBlockRecipeStatus.PERMISSION_REQUIRED,
                "Adding a recovery week requires block adaptation permission.")
        // Clone the last RESOLVED training week, not the block template.
        // Existing calendar and week overrides remain exactly unchanged.
        val block=if(appending) {
            val structural=sourceReview.sourceAudit.structuralReview
            val last=structural?.let{CustomTrainingCycleDomain.week(it,it.totalWeeks)}
                ?:return result(ReviewedRecoveryBlockRecipeStatus.SOURCE_UNRESOLVED,
                    "The final training week could not be resolved.",comparison=sourceReview)
            anchor.copy(blockId=checkNotNull(appendId),label="Recovery",
                purpose=CustomBlockPurpose.RECOVERY,weeks=1,days=last.days)
        } else anchor
        // Explicit permission checks cover removed working sets too; a
        // removed amplifier cannot disappear behind the set-count permission.
        val working=block.days.flatMap{it.slots}.flatMap{it.sets}
            .filter{it.type==DraftSetType.WORKING}
        val trim=block.days.flatMap{it.slots}.any{slot->
            slot.sets.count{it.type==DraftSetType.WORKING}>request.maximumWorkingSetsPerSlot
        }
        val raiseReserve=working.any{it.targetRir==null ||
            it.targetRir<request.minimumTargetRir}
        val removeAmplifiers=working.any{it.amplifier!=null}
        val removeWeightedAmplifiers=working.any{
            it.amplifier?.followUpLoad!=null || it.amplifier?.secondDropLoad!=null
        }
        if((trim && !input.permissions.adaptSetCount) ||
            (raiseReserve && !input.permissions.adaptRir) ||
            (removeAmplifiers && !input.permissions.adaptAmplifiers) ||
            ((removeWeightedAmplifiers || budget!=null) && !input.permissions.adaptLoads))
            return result(ReviewedRecoveryBlockRecipeStatus.PERMISSION_REQUIRED,
                "Set-count, reserve and amplifier changes need their separate permissions.")
        val gym=input.athleteSetup.reviewed?.gym
        val inventory=CustomExerciseAuthoringDomain.picker(input.athleteSetup,input.catalog)
            .associateBy{it.exerciseId}
        val changed=block.copy(days=block.days.map{day->day.copy(
            slots=day.slots.map{slot->
                var count=0
                val retained=slot.sets.mapNotNull{set->
                    if(set.type!=DraftSetType.WORKING)set
                    else if(++count>request.maximumWorkingSetsPerSlot)null
                    else {
                        val old=set.load as? DraftSetLoad.Verified
                        val load=if(budget==null || old==null)set.load else {
                            val movement=input.catalog.singleOrNull{it.exerciseId==slot.exerciseId}
                            val physical=ReviewedFutureWeekProgressionDomain
                                .requiresPhysicalMachineIdentity(old.basis)
                            val stock=if(physical && movement!=null && gym!=null)
                                ReviewedFutureWeekProgressionDomain.confirmedBoundMachineStock(
                                    gym.gymId,movement,slot.machineInstanceId,
                                    request.verifiedMachineConfigurations)
                            else if(!physical && slot.machineInstanceId==null)
                                inventory[slot.exerciseId]?.availability?.stock
                            else null
                            val inspected=EquipmentLoadDomain.inspect(stock,old.value,old.unit)
                                as? LoadInspection.Confirmed
                            val easier=inspected?.nextEasier
                            if(inspected==null || !inspected.exact ||
                                inspected.basis!=old.basis || easier==null ||
                                !KotlinSetNumberAuthorityDomain.validPrescribedLoad(easier,old.basis) ||
                                old.basis.compareDifficulty(easier,old.value)>=0 ||
                                easier.subtract(old.value).abs()>budget)
                                return result(ReviewedRecoveryBlockRecipeStatus.LOAD_STEP_UNAVAILABLE,
                                    "No approved easier setting within the chosen limit for ${slot.exerciseId}. No partial recipe was proposed.")
                            old.copy(value=easier)
                        }
                        set.copy(load=load,targetRir=maxOf(
                            set.targetRir ?: request.minimumTargetRir,request.minimumTargetRir),
                            amplifier=null)
                    }
                }
                // Never silently make a retained warmup harder than any
                // adjusted working set. This slice does not rewrite warmups.
                if(budget!=null && retained.filter{it.type==DraftSetType.WARMUP}.any{warm->
                    val warmLoad=warm.load as? DraftSetLoad.Verified
                    retained.filter{it.type==DraftSetType.WORKING}.any{work->
                        val workingLoad=work.load as? DraftSetLoad.Verified
                        warmLoad==null || workingLoad==null ||
                            warmLoad.basis!=workingLoad.basis || warmLoad.unit!=workingLoad.unit ||
                            workingLoad.basis.compareDifficulty(warmLoad.value,workingLoad.value)>0
                    }
                })return result(ReviewedRecoveryBlockRecipeStatus.LOAD_STEP_UNAVAILABLE,
                    "Review the retained warmup before reducing working-set difficulty.")
                slot.copy(sets=retained)
            })})
        if(changed==block)return result(ReviewedRecoveryBlockRecipeStatus.NO_CHANGE,
            "The authored recovery recipe already meets these settings.")
        val proposed=cycle.copy(revision=cycle.revision+1,
            blocks=if(appending)cycle.blocks+changed else
                cycle.blocks.map{if(it.blockId==block.blockId)changed else it})
        val option=AdaptiveCycleOption("reviewed_recovery_recipe",proposed)
        // The existing option review targets a block that exists in the
        // source. Keep the anchor as its target; its full-cycle audit and
        // transition permission checks include the appended block.
        val review=AdaptiveCycleOptionReviewDomain.review(input.copy(alternatives=listOf(option)))
        if(review.evaluated.singleOrNull()?.status!=AdaptiveCycleOptionStatus.READY_FOR_REVIEW)
            return result(ReviewedRecoveryBlockRecipeStatus.AUDIT_REFUSED,
                "The proposed recovery recipe did not pass the existing permission and complete-cycle audits.",
                comparison=review)
        return result(ReviewedRecoveryBlockRecipeStatus.READY_FOR_REVIEW,
            "Review fewer sets, more prescribed reserve and no amplifiers. "+
                (if(budget==null)"Retained loads are unchanged. " else
                    "Weighted working sets use one verified easier setting within the approved limit. ")+
                (if(appending)"One recovery week is appended; existing weeks and calendars are unchanged." else
                    "Rep targets, equipment and calendar remain unchanged."),
            option,review)
    }
}
