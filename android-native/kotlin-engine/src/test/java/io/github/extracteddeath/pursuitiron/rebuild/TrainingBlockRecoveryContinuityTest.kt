package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

/**
 * A recovery week or performance-test bridge must not reset the regular
 * training dose baseline. The 30 -> 11 scenario is an engineering regression
 * for the athlete-reported strength-block collapse, not a clinical volume
 * recommendation or an approval to change a real training cycle.
 */
class TrainingBlockRecoveryContinuityTest {
    private val fixture=GeneratedAdaptiveBlockFixture
    private fun n(value:String)=BigDecimal(value)
    private fun original()=fixture.cycle()
    private fun template()=original().blocks.single()
    private fun modifiedDay(
        day:CustomTrainingDay,
        counts:Map<String,Int>,
        purpose:ComposerSessionPurpose=ComposerSessionPurpose.HYPERTROPHY,
        pinBench:Boolean=false,
        strength:Boolean=false
    )=day.copy(
        purpose=purpose,
        slots=day.slots.map {slot ->
            val old=slot.sets.single()
            val prescribed=if(strength)old.copy(
                reps=RepBand(3,6),restSeconds=150) else old
            slot.copy(
                sets=List(checkNotNull(counts[slot.exerciseId])){prescribed},
                pinned=pinBench && slot.exerciseId=="bench")
        })

    private fun hypertrophy():CustomTrainingBlock {
        val setsByDay=listOf(
            mapOf("bench" to 4,"row" to 3,"squat" to 3),
            mapOf("bench" to 3,"row" to 4,"squat" to 3),
            mapOf("bench" to 3,"row" to 3,"squat" to 4))
        return template().copy(
            weeks=1,label="Reviewed hypertrophy baseline",
            days=template().days.mapIndexed {index,day ->
                modifiedDay(day,setsByDay[index])
            })
    }

    private fun interruption(purpose:CustomBlockPurpose)=template().copy(
        blockId=if(purpose==CustomBlockPurpose.RECOVERY)"recovery"
            else "performance",
        label="Temporary lower-load interruption",
        purpose=purpose,weeks=1)

    private fun strength():CustomTrainingBlock {
        val setsByDay=listOf(
            mapOf("bench" to 2,"row" to 1,"squat" to 1),
            mapOf("bench" to 2,"row" to 1,"squat" to 1),
            mapOf("bench" to 1,"row" to 1,"squat" to 1))
        return template().copy(
            blockId="strength",label="Strength training",
            phase=TrainingPhase.STRENGTH,weeks=1,
            days=template().days.mapIndexed {index,day ->
                modifiedDay(day,setsByDay[index],
                    purpose=if(index<2)
                        ComposerSessionPurpose.STRENGTH_SKILL
                    else ComposerSessionPurpose.GENERAL_PREPARATION,
                    pinBench=index<2,strength=true)
            })
    }

    private fun cycle(interruptedBy:CustomBlockPurpose=
        CustomBlockPurpose.RECOVERY)=
        original().copy(blocks=listOf(
            hypertrophy(),interruption(interruptedBy),strength()))

    private fun coaching(
        limit:CustomCoachVolumeContinuityPolicy?=null
    ):CustomCoachAuditInputs {
        val hypertrophy=checkNotNull(fixture.generation().policy)
        val strength=hypertrophy.copy(
            policyId="strength-policy",revision="strength-v1",
            phase=TrainingPhase.STRENGTH,
            minimumPurposeDays=mapOf(
                ComposerSessionPurpose.STRENGTH_SKILL to 2,
                ComposerSessionPurpose.GENERAL_PREPARATION to 1),
            primaryLiftMinimumDays=mapOf("bench" to 2))
        return CustomCoachAuditInputs(
            policiesByPhase=mapOf(
                TrainingPhase.HYPERTROPHY to hypertrophy,
                TrainingPhase.STRENGTH to strength),
            nonSetSecondsByWeekday=mapOf(
                0 to 1200,2 to 1200,4 to 1200),
            continuityPolicy=limit)
    }

    private fun review(
        interruptedBy:CustomBlockPurpose=CustomBlockPurpose.RECOVERY,
        limit:CustomCoachVolumeContinuityPolicy?=null
    )=CustomCycleCoachAuditDomain.review(
        cycle(interruptedBy),fixture.athlete(),
        fixture.movements,coaching(limit))

    @Test fun recoveryCannotMask30To11StrengthWorkingSetAndDirectMuscleDrop() {
        val audited=review(limit=CustomCoachVolumeContinuityPolicy(
            n("0.25"),n("0.25")))
        assertEquals(listOf(30,9,11),
            audited.weeks.map {it.workingSets})
        assertEquals(listOf(
            CustomBlockPurpose.TRAINING,CustomBlockPurpose.RECOVERY,
            CustomBlockPurpose.TRAINING),
            audited.weeks.map {it.blockPurpose})
        assertEquals(CustomCoachAuditStatus.UNRESOLVED,audited.status)
        assertTrue(audited.issues.toString(),audited.issues.any {
            it.kind==CustomCoachIssueKind.UNJUSTIFIED_BLOCK_VOLUME_DROP &&
                it.location=="week.3.block.strength" &&
                it.explanation.contains("30 to 11")})
        assertTrue(audited.issues.any {
            it.kind==CustomCoachIssueKind.DIRECT_MUSCLE_DROP &&
                it.location=="week.3.LATS"})
        // All three weeks retain their exact authored sets. Neither normal
        // training week breaks its own explicit phase-specific MEV/MRV.
        assertEquals(n("10"),audited.weeks[0]
            .directSets.getValue(TrainingMuscle.CHEST))
        assertEquals(n("3"),audited.weeks[2]
            .directSets.getValue(TrainingMuscle.LATS))
        assertFalse(audited.issues.toString(),audited.issues.any {
            it.kind in setOf(
                CustomCoachIssueKind.BELOW_WEEKLY_MEV,
                CustomCoachIssueKind.ABOVE_WEEKLY_MRV,
                CustomCoachIssueKind.GOAL_POLICY_UNRESOLVED)})
        assertFalse(audited.mayIssueWorkout)
    }

    @Test fun missingContinuityPolicyStillBlocksReturnAfterRecovery() {
        val audited=review()
        assertEquals(CustomCoachAuditStatus.COACHING_POLICY_REQUIRED,
            audited.status)
        assertTrue(audited.issues.any {
            it.kind==CustomCoachIssueKind.CONTINUITY_POLICY_MISSING})
    }

    @Test fun testWeekAlsoCannotEraseNormalTrainingBaseline() {
        val audited=review(
            interruptedBy=CustomBlockPurpose.PERFORMANCE_TEST,
            limit=CustomCoachVolumeContinuityPolicy(
                n("0.25"),n("0.25")))
        assertEquals(listOf(30,9,11),
            audited.weeks.map {it.workingSets})
        assertTrue(audited.issues.any {
            it.kind==CustomCoachIssueKind.UNJUSTIFIED_BLOCK_VOLUME_DROP &&
                it.location=="week.3.block.strength"})
    }


    private fun fixedStrength(source:CustomTrainingCycle):CustomTrainingCycle =
        source.copy(revision=source.revision+1,
            blocks=source.blocks.map {block->
                if(block.blockId!="strength")block else block.copy(
                    days=block.days.map {day->day.copy(
                        slots=day.slots.map {slot->
                            val original=slot.sets.first()
                            slot.copy(sets=List(3){original})
                        })
                    })
            })

    private fun repairRequest(
        source:CustomTrainingCycle,
        alternatives:List<AdaptiveCycleOption>,
        permissions:CustomCycleCoachPermissions=
            CustomCycleCoachPermissions(adaptSetCount=true)
    )=AdaptiveCycleOptionRequest(
        source,"strength",fixture.athlete(),fixture.movements,
        coaching(CustomCoachVolumeContinuityPolicy(n("0.25"),n("0.25"))),
        permissions,alternatives)

    @Test fun badStrengthBlockCanBeRepairedWithoutErasingHypertrophyOrRecovery() {
        val source=cycle()
        val fixed=fixedStrength(source)
        val reviewed=AdaptiveCycleOptionReviewDomain.review(repairRequest(
            source,listOf(
                AdaptiveCycleOption("keep_current",
                    source.copy(revision=source.revision+1)),
                AdaptiveCycleOption("repair_strength",fixed))))
        assertTrue(reviewed.sourceRepairRequired)
        assertEquals(CustomCoachAuditStatus.UNRESOLVED,
            reviewed.sourceAudit.status)
        assertEquals(AdaptiveCycleComparisonStatus.OPTIONS_COMPARED,
            reviewed.status)
        assertEquals("repair_strength",reviewed.recommendedOptionId)
        assertEquals(AdaptiveCycleOptionStatus.COACH_AUDIT_FAILED,
            reviewed.evaluated[0].status)
        assertEquals(AdaptiveCycleOptionStatus.READY_FOR_REVIEW,
            reviewed.evaluated[1].status)
        assertEquals(listOf(30,9,27),
            reviewed.evaluated[1].coachingAudit!!.weeks.map {it.workingSets})
        assertEquals(source.blocks.take(2),fixed.blocks.take(2))
        assertEquals(source.blocks.last().days.map {day->
            day.slots.filter {it.pinned}.map {it.slotId}
        },fixed.blocks.last().days.map {day->
            day.slots.filter {it.pinned}.map {it.slotId}
        })
        assertTrue(fixed.blocks.last().days.flatMap {it.slots}
            .flatMap {it.sets}.all {it.reps==RepBand(3,6) &&
                it.targetRir==2 && it.restSeconds==150})
        assertFalse(reviewed.mayIssueWorkout)
        assertFalse(reviewed.mayApplyAutomatically)
        assertEquals(source,cycle())
    }

    @Test fun repairStillRequiresExplicitAdaptSetCountPermission() {
        val source=cycle()
        val reviewed=AdaptiveCycleOptionReviewDomain.review(repairRequest(
            source,listOf(AdaptiveCycleOption("repair",
                fixedStrength(source))),
            CustomCycleCoachPermissions()))
        assertTrue(reviewed.sourceRepairRequired)
        assertEquals(AdaptiveCycleComparisonStatus.NO_FEASIBLE_ALTERNATIVE,
            reviewed.status)
        assertEquals(AdaptiveCycleOptionStatus.PERMISSION_REQUIRED,
            reviewed.evaluated.single().status)
        assertNull(reviewed.recommendedOptionId)
    }

    @Test fun repairCannotChangeEarlierBlocksEvenWithBroadEditPermission() {
        val source=cycle()
        val repaired=fixedStrength(source)
        val changed=repaired.copy(blocks=repaired.blocks.map {block->
            if(block.blockId=="recovery")block.copy(
                label="Changed recovery") else block})
        val reviewed=AdaptiveCycleOptionReviewDomain.review(repairRequest(
            source,listOf(AdaptiveCycleOption("smuggled",changed)),
            CustomCycleCoachPermissions(adaptSetCount=true,adaptCues=true)))
        assertTrue(reviewed.sourceRepairRequired)
        assertEquals(AdaptiveCycleOptionStatus.INVALID_CYCLE,
            reviewed.evaluated.single().status)
        assertNull(reviewed.recommendedOptionId)
    }

    @Test fun unrelatedSourceCoachDefectCannotBeLaunderedThroughRepair() {
        val source=cycle()
        val deficient=source.copy(blocks=source.blocks.map {block->
            if(block.blockId!="block-1")block else block.copy(
                days=block.days.map {day->day.copy(
                    slots=day.slots.filter {slot->
                        day.weekday==0 || slot.exerciseId!="row"
                    }.map {slot->
                        if(slot.exerciseId=="row")
                            slot.copy(sets=slot.sets.take(1)) else slot
                    })
                })
        })
        val reviewed=AdaptiveCycleOptionReviewDomain.review(repairRequest(
            deficient,listOf(AdaptiveCycleOption("repaired",
                fixedStrength(deficient)))))
        assertEquals(AdaptiveCycleComparisonStatus.SOURCE_UNRESOLVED,
            reviewed.status)
        assertFalse(reviewed.sourceRepairRequired)
        assertEquals(0,reviewed.candidatesChecked)
        assertTrue(reviewed.sourceAudit.issues.any {
            it.kind==CustomCoachIssueKind.BELOW_WEEKLY_MEV &&
                it.location.startsWith("week.1.")})
    }

    @Test fun missingReviewedContinuityPolicyCannotUseRepairException() {
        val source=cycle()
        val request=repairRequest(source,listOf(
            AdaptiveCycleOption("repair",fixedStrength(source))))
            .copy(coaching=coaching())
        val reviewed=AdaptiveCycleOptionReviewDomain.review(request)
        assertEquals(AdaptiveCycleComparisonStatus.SOURCE_UNRESOLVED,
            reviewed.status)
        assertFalse(reviewed.sourceRepairRequired)
        assertTrue(reviewed.sourceAudit.issues.any {
            it.kind==CustomCoachIssueKind.CONTINUITY_POLICY_MISSING})
    }

    private fun autoRepair(
        source:CustomTrainingCycle=cycle(),
        cap:Int=3,
        permissions:CustomCycleCoachPermissions=
            CustomCycleCoachPermissions(adaptSetCount=true),
        reviewInput:CustomCoachAuditInputs=coaching(
            CustomCoachVolumeContinuityPolicy(n("0.25"),n("0.25")))
    )=AdaptiveStrengthVolumeRepairDomain.propose(
        AdaptiveStrengthVolumeRepairRequest(
            AdaptiveCycleOptionRequest(
                source,"strength",fixture.athlete(),fixture.movements,
                reviewInput,permissions,emptyList()),
            maximumWorkingSetsPerSlot=cap))

    @Test fun generatedStrengthRepairActuallyProducesAudited24SetWeek() {
        val source=cycle()
        val repaired=autoRepair(source)
        assertEquals(repaired.explanation,
            AdaptiveStrengthVolumeRepairStatus.CANDIDATE_READY_FOR_REVIEW,
            repaired.status)
        assertEquals(13,repaired.addedWorkingSets)
        assertFalse(repaired.searchTruncated)
        assertTrue(repaired.comparison.sourceRepairRequired)
        assertEquals("auto_strength_dose_repair",
            repaired.comparison.recommendedOptionId)
        assertEquals(AdaptiveCycleOptionStatus.READY_FOR_REVIEW,
            repaired.comparison.evaluated.single().status)
        val changed=checkNotNull(repaired.proposedOption).proposedCycle
        assertEquals(source.revision+1,changed.revision)
        assertEquals(source.blocks.take(2),changed.blocks.take(2))
        assertEquals(source.weekOverrides,changed.weekOverrides)
        assertEquals(listOf(30,9,24),
            repaired.comparison.evaluated.single()
                .coachingAudit!!.weeks.map {it.workingSets})
        val measured=repaired.comparison.evaluated.single()
            .coachingAudit!!.weeks.last()
        for(muscle in listOf(
            TrainingMuscle.CHEST,TrainingMuscle.LATS,TrainingMuscle.QUADS)) {
            assertEquals(n("8"),measured.directSets.getValue(muscle))
            assertTrue(measured.totalSetCredits.getValue(muscle)<=n("10"))
        }
        assertTrue(measured.estimatedSecondsByDay.values.all {
            it!=null && it in 2400L..3600L
        })
        val before=source.blocks.last().days.associateBy {it.weekday}
        for(day in changed.blocks.last().days) {
            val earlier=checkNotNull(before[day.weekday])
                .slots.associateBy {it.slotId}
            for(slot in day.slots) {
                val old=checkNotNull(earlier[slot.slotId])
                assertEquals(old.exerciseId,slot.exerciseId)
                assertEquals(old.pinned,slot.pinned)
                assertEquals(old.attachmentId,slot.attachmentId)
                assertEquals(old.sets.first(),slot.sets.first())
                assertTrue(slot.sets.size<=3)
                assertTrue(slot.sets.all {it==old.sets.first()})
            }
        }
        assertFalse(repaired.maySaveProgram)
        assertFalse(repaired.mayApplyAutomatically)
        assertFalse(repaired.mayIssueWorkout)
        assertFalse(repaired.mayAlterHistory)
        assertEquals(source,cycle())
        assertEquals(repaired,autoRepair(source))
    }

    private fun repeatedStrength(weeks:Int)=cycle().let {source->
        source.copy(blocks=source.blocks.map {block->
            if(block.blockId=="strength")block.copy(weeks=weeks)
            else block
        })
    }

    @Test fun generatedRepairCoversEveryWeekOfAnUnchangedStrengthTemplate() {
        val source=repeatedStrength(3)
        val proposed=autoRepair(source)
        assertEquals(proposed.explanation,
            AdaptiveStrengthVolumeRepairStatus.CANDIDATE_READY_FOR_REVIEW,
            proposed.status)
        assertEquals(3,proposed.repairedWeekCount)
        assertEquals(13,proposed.addedWorkingSets)
        val repaired=checkNotNull(proposed.proposedOption).proposedCycle
        assertEquals(3,repaired.blocks.last().weeks)
        assertEquals(source.blocks.take(2),repaired.blocks.take(2))
        assertEquals(listOf(30,9,24,24,24),
            proposed.comparison.evaluated.single()
                .coachingAudit!!.weeks.map {it.workingSets})
        val after=proposed.comparison.evaluated.single()
            .coachingAudit!!.weeks.drop(2)
        assertEquals(3,after.size)
        assertTrue(after.all {week->
            listOf(TrainingMuscle.CHEST,TrainingMuscle.LATS,
                TrainingMuscle.QUADS).all {muscle->
                week.directSets.getValue(muscle)==n("8") &&
                    week.totalSetCredits.getValue(muscle)<=n("10")
            }
        })
        assertEquals(proposed,autoRepair(source))
        assertEquals(source,repeatedStrength(3))
        assertFalse(proposed.mayIssueWorkout)
        assertFalse(proposed.mayApplyAutomatically)
    }

    @Test fun longStrengthBlockRefusesUnboundedTemplateExpansion() {
        val denied=autoRepair(source=repeatedStrength(9))
        assertEquals(AdaptiveStrengthVolumeRepairStatus.PRESCRIPTION_UNSUPPORTED,
            denied.status)
        assertNull(denied.proposedOption)
        assertEquals(0,denied.repairedWeekCount)
    }

    @Test fun changingOneStrengthWeekDoesNotSilentlyOverwriteItsOverrides() {
        val source=repeatedStrength(3)
        val day=source.blocks.last().days.first()
        val slot=day.slots.first()
        val varied=source.copy(weekOverrides=mapOf(
            "strength" to listOf(CustomWeekSlotOverride(
                2,day.weekday,slot.slotId,slot.sets+slot.sets))))
        val result=autoRepair(source=varied)
        assertNotEquals(AdaptiveStrengthVolumeRepairStatus.CANDIDATE_READY_FOR_REVIEW,
            result.status)
        assertNull(result.proposedOption)
        assertEquals(0,result.repairedWeekCount)
        assertEquals(varied.weekOverrides,source.copy(
            weekOverrides=varied.weekOverrides).weekOverrides)
    }

    @Test fun generatedStrengthRepairNeedsIndependentSetCountPermission() {
        val denied=autoRepair(permissions=CustomCycleCoachPermissions())
        assertEquals(AdaptiveStrengthVolumeRepairStatus.PERMISSION_REQUIRED,
            denied.status)
        assertNull(denied.proposedOption)
        assertEquals(0,denied.addedWorkingSets)
        assertFalse(denied.mayIssueWorkout)
    }

    @Test fun repairedStrengthCannotInventMoreSetsThanExplicitSlotCap() {
        val rejected=autoRepair(cap=2)
        assertEquals(AdaptiveStrengthVolumeRepairStatus.NO_FEASIBLE_REPAIR,
            rejected.status)
        assertNull(rejected.proposedOption)
        assertTrue(rejected.addedWorkingSets>0)
        assertNull(rejected.comparison.recommendedOptionId)
    }

    @Test fun generatorRefusesMissingContinuityPolicyOrUnrelatedDoseDefects() {
        val missing=autoRepair(reviewInput=coaching())
        assertEquals(AdaptiveStrengthVolumeRepairStatus.SOURCE_UNRESOLVED,
            missing.status)
        val source=cycle()
        val deficient=source.copy(blocks=source.blocks.map {block->
            if(block.blockId!="block-1")block else block.copy(
                days=block.days.map {day->day.copy(
                    slots=day.slots.filter {
                        day.weekday==0 || it.exerciseId!="row"
                    }.map {slot ->
                        if(slot.exerciseId=="row")
                            slot.copy(sets=slot.sets.take(1)) else slot
                    })
                })
        })
        val refused=autoRepair(source=deficient)
        assertEquals(AdaptiveStrengthVolumeRepairStatus.SOURCE_UNRESOLVED,
            refused.status)
        assertNull(refused.proposedOption)
    }

    @Test fun repairDoesNotRelaxAlreadyAuthoredSetCeilings() {
        val refused=autoRepair(cap=1)
        assertEquals(AdaptiveStrengthVolumeRepairStatus.PRESCRIPTION_UNSUPPORTED,
            refused.status)
        assertNull(refused.proposedOption)
        assertEquals(0,refused.addedWorkingSets)
    }

    @Test fun generatorPreservesWeeklyOverridesAndUnknownEffort() {
        val source=cycle()
        val week=source.blocks.last().days.first()
        val slot=week.slots.first()
        val withOverride=source.copy(weekOverrides=mapOf(
            "strength" to listOf(CustomWeekSlotOverride(
                1,week.weekday,slot.slotId,slot.sets))))
        val blocked=autoRepair(source=withOverride)
        assertEquals(AdaptiveStrengthVolumeRepairStatus.PRESCRIPTION_UNSUPPORTED,
            blocked.status)
        val unknownEffort=source.copy(blocks=source.blocks.map {block->
            if(block.blockId!="strength")block else block.copy(
                days=block.days.map {day->day.copy(
                    slots=day.slots.map {item->
                        if(item.exerciseId!="row")item else item.copy(
                            sets=item.sets.map {it.copy(targetRir=null)})
                    })
                })
        })
        val refused=autoRepair(source=unknownEffort)
        assertEquals(AdaptiveStrengthVolumeRepairStatus.PRESCRIPTION_UNSUPPORTED,
            refused.status)
        assertNull(refused.proposedOption)
    }

    @Test fun explicitlyReviewedLargerDropCanPassWithoutRewritingDeload() {
        val source=cycle()
        val audited=review(limit=CustomCoachVolumeContinuityPolicy(
            n("0.75"),n("0.75")))
        assertEquals(audited.issues.toString(),
            CustomCoachAuditStatus.READY_FOR_ATHLETE_REVIEW,audited.status)
        assertEquals(listOf(30,9,11),
            audited.weeks.map {it.workingSets})
        assertTrue(audited.issues.none {
            it.kind in setOf(
                CustomCoachIssueKind.UNJUSTIFIED_BLOCK_VOLUME_DROP,
                CustomCoachIssueKind.DIRECT_MUSCLE_DROP)})
        assertEquals(source,cycle())
        assertFalse(audited.mayPublishApprovedProgram)
        assertFalse(audited.mayIssueWorkout)
    }
}
