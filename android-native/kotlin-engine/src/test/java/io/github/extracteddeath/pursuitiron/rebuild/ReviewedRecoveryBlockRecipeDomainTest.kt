package io.github.extracteddeath.pursuitiron.rebuild

import org.junit.Assert.*
import org.junit.Test

class ReviewedRecoveryBlockRecipeDomainTest {
    private val fixture=GeneratedAdaptiveBlockFixture
    private fun cycle():CustomTrainingCycle {
        val original=fixture.cycle()
        val training=original.blocks.single().let{block->
            block.copy(weeks=1,days=block.days.map{day->day.copy(
                slots=day.slots.map{slot->slot.copy(
                    sets=List(3){slot.sets.single()})})})
        }
        val recovery=training.copy(blockId="recovery",label="Recovery",
            purpose=CustomBlockPurpose.RECOVERY,days=training.days.map{day->
                day.copy(slots=day.slots.map{slot->slot.copy(
                    sets=slot.sets.take(2))})})
        return original.copy(blocks=listOf(training,recovery))
    }
    private fun request(source:CustomTrainingCycle=cycle(),
        permissions:CustomCycleCoachPermissions=CustomCycleCoachPermissions(
            adaptSetCount=true,adaptRir=true,adaptAmplifiers=true),
        cap:Int=1,rir:Int=4,target:String="recovery")=
        ReviewedRecoveryBlockRecipeRequest(
            AdaptiveCycleOptionRequest(source,target,fixture.athlete(),
                fixture.movements,CustomCoachAuditInputs(
                    mapOf(TrainingPhase.HYPERTROPHY to
                        checkNotNull(fixture.generation().policy)),
                    mapOf(0 to 1200,2 to 1200,4 to 1200)),
                permissions,emptyList()),cap,rir)

    @Test fun explicitRecoveryRecipeIsAuditedWithoutMovingCalendarOrChangingLoad() {
        val source=cycle()
        val result=ReviewedRecoveryBlockRecipeDomain.propose(request(source))
        assertEquals(result.explanation,ReviewedRecoveryBlockRecipeStatus.READY_FOR_REVIEW,
            result.status)
        val option=checkNotNull(result.candidate)
        val proposed=option.proposedCycle
        assertEquals(source.revision+1,proposed.revision)
        assertEquals(source.blocks.first(),proposed.blocks.first())
        assertEquals(source.weekOverrides,proposed.weekOverrides)
        val before=source.blocks.last()
        val after=proposed.blocks.last()
        assertEquals(before.copy(days=after.days),after)
        for((oldDay,newDay) in before.days.zip(after.days)) {
            assertEquals(oldDay.copy(slots=newDay.slots),newDay)
            for((old,new) in oldDay.slots.zip(newDay.slots)) {
                assertEquals(old.copy(sets=new.sets),new)
                assertEquals(listOf(old.sets.first().copy(targetRir=4)),new.sets)
            }
        }
        assertEquals(listOf(27,9),result.comparison!!.evaluated.single()
            .coachingAudit!!.weeks.map{it.workingSets})
        assertFalse(result.mayIssueWorkout)
        assertFalse(result.mayApplyAutomatically)
        assertFalse(result.mayModifyHistory)
        assertEquals(source,cycle())
        assertEquals(result,ReviewedRecoveryBlockRecipeDomain.propose(request(source)))
    }

    @Test fun optionalLoadReductionRequiresPermissionAndAnExactEasierStepWithinBudget() {
        val source=cycle()
        val base=request(source)
        val step=fixture.n("5")
        val denied=ReviewedRecoveryBlockRecipeDomain.propose(base.copy(maximumEasierStep=step))
        assertEquals(ReviewedRecoveryBlockRecipeStatus.PERMISSION_REQUIRED,denied.status)
        val allowed=base.copy(source=base.source.copy(permissions=
            base.source.permissions.copy(adaptLoads=true)),maximumEasierStep=step)
        val candidate=ReviewedRecoveryBlockRecipeDomain.propose(allowed)
        assertEquals(candidate.explanation,ReviewedRecoveryBlockRecipeStatus.READY_FOR_REVIEW,
            candidate.status)
        val proposed=candidate.candidate!!.proposedCycle
        assertEquals(source.blocks.first(),proposed.blocks.first())
        for(set in proposed.blocks.last().days.flatMap{it.slots}.flatMap{it.sets}) {
            assertEquals(fixture.n("20"),(set.load as DraftSetLoad.Verified).value)
            assertEquals(4,set.targetRir)
        }
        val tooSmall=ReviewedRecoveryBlockRecipeDomain.propose(
            allowed.copy(maximumEasierStep=fixture.n("4")))
        assertEquals(ReviewedRecoveryBlockRecipeStatus.LOAD_STEP_UNAVAILABLE,tooSmall.status)
        assertNull(tooSmall.candidate)
        val atFloor=source.copy(blocks=source.blocks.map{block->
            block.copy(days=block.days.map{day->day.copy(slots=day.slots.map{slot->
                slot.copy(sets=slot.sets.map{set->set.copy(load=
                    (set.load as DraftSetLoad.Verified).copy(value=fixture.n("20")))})
            })})
        })
        val floor=ReviewedRecoveryBlockRecipeDomain.propose(
            allowed.copy(source=allowed.source.copy(approvedCycle=atFloor)))
        assertEquals(ReviewedRecoveryBlockRecipeStatus.LOAD_STEP_UNAVAILABLE,floor.status)
        assertNull(floor.candidate)
        assertFalse(candidate.mayIssueWorkout)
    }

    @Test fun appendRecoveryUsesFinalResolvedWeekAndNeverShiftsExistingWeeks() {
        val base=cycle().let{it.copy(blocks=it.blocks.take(1))}
        val slot=base.blocks.last().days.first().slots.first()
        val changed=slot.sets.map{it.copy(targetRir=3)}
        val source=base.copy(weekOverrides=mapOf("block-1" to listOf(
            CustomWeekSlotOverride(1,0,slot.slotId,changed))))
        val request=request(source,target="block-1").copy(
            appendRecoveryBlockId="recovery-next",minimumTargetRir=2)
        val denied=ReviewedRecoveryBlockRecipeDomain.propose(request)
        assertEquals(ReviewedRecoveryBlockRecipeStatus.PERMISSION_REQUIRED,denied.status)
        val allowed=request.copy(source=request.source.copy(
            permissions=request.source.permissions.copy(adaptBlocks=true)))
        val result=ReviewedRecoveryBlockRecipeDomain.propose(allowed)
        assertEquals(result.explanation,ReviewedRecoveryBlockRecipeStatus.READY_FOR_REVIEW,
            result.status)
        val proposed=result.candidate!!.proposedCycle
        assertEquals(source.blocks,proposed.blocks.dropLast(1))
        assertEquals(source.weekOverrides,proposed.weekOverrides)
        assertEquals(source.revision+1,proposed.revision)
        val recovery=proposed.blocks.last()
        assertEquals("recovery-next",recovery.blockId)
        assertEquals(CustomBlockPurpose.RECOVERY,recovery.purpose)
        assertEquals(1,recovery.weeks)
        assertEquals(source.blocks.last().days.map{it.weekday},recovery.days.map{it.weekday})
        assertEquals(changed.first(),recovery.days.first().slots.first().sets.single())
        assertEquals(listOf(27,9),result.comparison!!.evaluated.single()
            .coachingAudit!!.weeks.map{it.workingSets})
        assertFalse(result.mayIssueWorkout)
        assertFalse(result.mayApplyAutomatically)
        val duplicate=ReviewedRecoveryBlockRecipeDomain.propose(
            allowed.copy(appendRecoveryBlockId="block-1"))
        assertEquals(ReviewedRecoveryBlockRecipeStatus.INVALID_REQUEST,duplicate.status)
    }

    @Test fun insertionPreservesEarlierWeeksAndAllLaterBlockRelativeOverrides() {
        val first=cycle().blocks.first().copy(weeks=2)
        val later=first.copy(blockId="later-training",label="Later training",weeks=1)
        val slot=first.days.first().slots.first()
        val anchorSets=slot.sets.map{it.copy(targetRir=3)}
        val laterSets=slot.sets.map{it.copy(targetRir=5)}
        val source=cycle().copy(blocks=listOf(first,later),weekOverrides=mapOf(
            first.blockId to listOf(CustomWeekSlotOverride(2,0,slot.slotId,anchorSets)),
            later.blockId to listOf(CustomWeekSlotOverride(1,0,slot.slotId,laterSets))))
        val initial=request(source,target=first.blockId,rir=3)
        val allowed=initial.copy(
            source=initial.source.copy(
                permissions=initial.source.permissions.copy(adaptBlocks=true),
                coaching=initial.source.coaching.copy(continuityPolicy=
                    CustomCoachVolumeContinuityPolicy(fixture.n("0.25"),fixture.n("0.25")))),
            appendRecoveryBlockId="recovery-between",insertAfterAnchor=true)
        val withoutOptIn=ReviewedRecoveryBlockRecipeDomain.propose(
            allowed.copy(insertAfterAnchor=false))
        assertEquals(ReviewedRecoveryBlockRecipeStatus.INVALID_REQUEST,withoutOptIn.status)
        val result=ReviewedRecoveryBlockRecipeDomain.propose(allowed)
        assertEquals(result.explanation,ReviewedRecoveryBlockRecipeStatus.READY_FOR_REVIEW,
            result.status)
        val proposed=result.candidate!!.proposedCycle
        assertEquals(listOf(first.blockId,"recovery-between",later.blockId),
            proposed.blocks.map{it.blockId})
        assertEquals(source.blocks,proposed.blocks.filter{it.blockId!="recovery-between"})
        assertEquals(source.weekOverrides,proposed.weekOverrides)
        val sourceReview=result.comparison!!.sourceAudit.structuralReview!!
        val changedReview=result.comparison!!.evaluated.single().coachingAudit!!.structuralReview!!
        for(w in 1..2) {
            val before=CustomTrainingCycleDomain.week(sourceReview,w)!!
            val after=CustomTrainingCycleDomain.week(changedReview,w)!!
            assertEquals(before.days,after.days)
            assertEquals(before.blockId,after.blockId)
            assertEquals(before.weekInBlock,after.weekInBlock)
        }
        val recovery=CustomTrainingCycleDomain.week(changedReview,3)!!
        assertEquals(CustomBlockPurpose.RECOVERY,recovery.purpose)
        assertEquals(anchorSets.take(1),recovery.days.first().slots.first().sets)
        val beforeLater=CustomTrainingCycleDomain.week(sourceReview,3)!!
        val afterLater=CustomTrainingCycleDomain.week(changedReview,4)!!
        assertEquals(beforeLater.days,afterLater.days)
        assertEquals(beforeLater.blockId,afterLater.blockId)
        assertEquals(beforeLater.weekInBlock,afterLater.weekInBlock)
        assertEquals(listOf(27,27,9,27),result.comparison!!.evaluated.single()
            .coachingAudit!!.weeks.map{it.workingSets})
        assertFalse(result.mayIssueWorkout)
        assertFalse(result.mayModifyHistory)
    }

    @Test fun eachChangedDimensionNeedsPermissionAndOnlyRecoveryMayBeTargeted() {
        for(permissions in listOf(
            CustomCycleCoachPermissions(adaptRir=true),
            CustomCycleCoachPermissions(adaptSetCount=true))) {
            val result=ReviewedRecoveryBlockRecipeDomain.propose(request(permissions=permissions))
            assertEquals(ReviewedRecoveryBlockRecipeStatus.PERMISSION_REQUIRED,result.status)
            assertNull(result.candidate)
        }
        assertEquals(ReviewedRecoveryBlockRecipeStatus.INVALID_REQUEST,
            ReviewedRecoveryBlockRecipeDomain.propose(request(target="block-1")).status)
        assertEquals(ReviewedRecoveryBlockRecipeStatus.NO_CHANGE,
            ReviewedRecoveryBlockRecipeDomain.propose(request(cap=2,rir=2)).status)
        val source=cycle()
        val slot=source.blocks.last().days.first().slots.first()
        val overridden=source.copy(weekOverrides=mapOf("recovery" to listOf(
            CustomWeekSlotOverride(1,0,slot.slotId,slot.sets))))
        assertEquals(ReviewedRecoveryBlockRecipeStatus.INVALID_REQUEST,
            ReviewedRecoveryBlockRecipeDomain.propose(request(overridden)).status)
    }

    @Test fun removingAnAmplifiedTailSetCannotBypassAmplifierPermission() {
        val source=cycle().let{cycle->cycle.copy(blocks=cycle.blocks.map{block->
            block.copy(days=block.days.map{day->day.copy(slots=day.slots.map{slot->
                slot.copy(sets=slot.sets.mapIndexed{i,set->
                    if(i==1)set.copy(amplifier=CustomSetAmplifier(
                        CustomAmplifierKind.MYO_REPS,4,20,followUpActiveSeconds=60,
                        followUpRounds=3)) else set
                })
            })})
        })}
        val denied=ReviewedRecoveryBlockRecipeDomain.propose(request(source,
            CustomCycleCoachPermissions(adaptSetCount=true,adaptRir=true)))
        assertEquals(ReviewedRecoveryBlockRecipeStatus.PERMISSION_REQUIRED,denied.status)
        assertNull(denied.candidate)
        val permitted=ReviewedRecoveryBlockRecipeDomain.propose(request(source))
        assertEquals(permitted.explanation,ReviewedRecoveryBlockRecipeStatus.READY_FOR_REVIEW,
            permitted.status)
        assertTrue(permitted.candidate!!.proposedCycle.blocks.last().days
            .flatMap{it.slots}.flatMap{it.sets}.all{it.amplifier==null})
        assertTrue(source.blocks.first().days.flatMap{it.slots}
            .flatMap{it.sets}.any{it.amplifier!=null})
    }
}
