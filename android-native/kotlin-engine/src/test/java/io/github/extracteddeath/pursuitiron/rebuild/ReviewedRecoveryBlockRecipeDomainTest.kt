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
