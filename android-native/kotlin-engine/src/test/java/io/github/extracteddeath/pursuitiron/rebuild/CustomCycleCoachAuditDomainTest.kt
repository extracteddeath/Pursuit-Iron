package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class CustomCycleCoachAuditDomainTest {
    private fun n(s:String)=BigDecimal(s)
    private fun setup(
        selected:Set<Int> = setOf(0,2,4),
        excluded:Set<String> = emptySet()
    ):AthleteSetupReview = CoachAthleteSetupDomain.review(AthleteSetupDraft(
        profileRevision="rev-1",athleteProfileId="athlete-1",
        goal=AthleteTrainingGoal.HYPERTROPHY,
        experience=AthleteExperience.ADVANCED,
        split=ComposerSplit.FULL_BODY,
        trainingWeekdays=selected,timezoneId="America/New_York",
        sessionTimeBucket=SessionTimeBucket.FORTY_TO_SIXTY,
        hardSessionLimit=HardSessionLimit.Minutes(60),
        gym=GymProfileDraft("gym-1",LoadUnit.LB,emptyMap(),
            emptySet(),emptySet()),
        allowBodyweight=true,allowSupersets=false,
        allowTricepsOnLower=false,maxBarbellExercisesPerDay=0,
        focusedMuscles=emptySet(),reducedMuscles=emptySet(),
        excludedExerciseIds=excluded,lockedTrainingDays=emptySet(),
        hardTrainingDays=emptySet(),longTrainingDays=emptySet(),
        declaredRestDays=(0..6).filterNot{it in selected}.toSet()
    ))
    private fun authoredSet()=CustomAuthoredSet(
        DraftSetType.WORKING,DraftSetLoad.ExplicitBodyweight,
        RepBand(10,15),2,55,75)
    private fun authoredDay(day:Int)=CustomTrainingDay(
        day,"Core $day",ComposerSessionPurpose.HYPERTROPHY,
        listOf(CustomExerciseSlot("slot-$day","bodyweight_crunch",
            ComposerRole.ACCESSORY,listOf(authoredSet()))))
    private fun cycle(
        weeks:Int=3,
        days:List<CustomTrainingDay> = listOf(0,2,4).map(::authoredDay),
        overrides:Map<String,List<CustomWeekSlotOverride>> = emptyMap()
    )=CustomTrainingCycle("cycle-1",4,"athlete-1",
        AthleteTrainingGoal.HYPERTROPHY,"My fully custom plan",
        listOf(CustomTrainingBlock("block-1","Foundation",
            TrainingPhase.HYPERTROPHY,CustomBlockPurpose.TRAINING,
            weeks,CustomSplitIdentity.Freeform("Custom core"),days)),
        overrides)
    private fun bounds(absMev:String="3",absMrv:String="5")=
        TrainingMuscle.entries.map {m ->
            DeclaredMuscleDoseBounds(
                muscle=m,
                mev=if(m==TrainingMuscle.ABS)n(absMev) else n("0"),
                mav=if(m==TrainingMuscle.ABS)n("4") else n("0"),
                mrv=if(m==TrainingMuscle.ABS)n(absMrv) else n("0"),
                minimumDirectDays=if(m==TrainingMuscle.ABS)3 else 0)
        }
    private fun policy(
        bounds:List<DeclaredMuscleDoseBounds> = bounds()
    )=AuthoredGoalProgramPolicy(
        policyId="coach-1",revision="policy-1",rationale="Reviewed core dosage",
        athleteId="athlete-1",athleteRevision="rev-1",
        goal=AthleteTrainingGoal.HYPERTROPHY,
        experience=AthleteExperience.ADVANCED,
        split=ComposerSplit.FULL_BODY,phase=TrainingPhase.HYPERTROPHY,
        trainingDays=3,baselineBounds=bounds,
        selectedBounds=bounds,
        minimumPurposeDays=mapOf(ComposerSessionPurpose.HYPERTROPHY to 3)
    )
    private fun inputs(
        policy:AuthoredGoalProgramPolicy?=policy(),
        nonSet:Map<Int,Int> = mapOf(0 to 200,2 to 200,4 to 200)
    )=CustomCoachAuditInputs(
        policiesByPhase=if(policy==null)emptyMap()
            else mapOf(TrainingPhase.HYPERTROPHY to policy),
        nonSetSecondsByWeekday=nonSet)

    @Test fun exactUserAuthoredCycleCanReachReviewNotIssuance() {
        val source=cycle()
        val result=CustomCycleCoachAuditDomain.review(
            source,setup(),StarterExerciseTranche.movements(),inputs())
        assertEquals(result.issues.toString(),
            CustomCoachAuditStatus.READY_FOR_ATHLETE_REVIEW,result.status)
        assertEquals(3,result.weeks.size)
        assertEquals(listOf(0,2,4),result.weeks.first()
            .estimatedSecondsByDay.keys.toList())
        assertEquals(3,result.weeks.first().workingSets)
        assertEquals(n("3"),result.weeks.first()
            .totalSetCredits.getValue(TrainingMuscle.ABS))
        assertEquals(3,result.weeks.first()
            .directDays.getValue(TrainingMuscle.ABS))
        assertEquals(330L,result.weeks.first()
            .estimatedSecondsByDay.getValue(0))
        assertEquals(source,result.structuralReview!!.verifiedDraft)
        assertFalse(result.mayPublishApprovedProgram)
        assertFalse(result.mayIssueWorkout)
        assertFalse(result.mayAlterWorkoutHistory)
    }

    @Test fun noAuthoredPolicyOrTimeEvidenceMeansNeverApproved() {
        val result=CustomCycleCoachAuditDomain.review(
            cycle(),setup(),StarterExerciseTranche.movements())
        assertEquals(CustomCoachAuditStatus.COACHING_POLICY_REQUIRED,result.status)
        assertTrue(result.issues.any {
            it.kind==CustomCoachIssueKind.REVIEWED_POLICY_MISSING
        })
        assertTrue(result.issues.any {
            it.kind==CustomCoachIssueKind.NON_SET_TIME_UNDECLARED
        })
        assertTrue(result.weeks.all{it.estimatedSecondsByDay.values.all {v->v==null}})
        assertFalse(result.mayIssueWorkout)
    }

    @Test fun everyOverrideWeekIsMeasuredWithoutRewritingOtherWeeks() {
        val extra=List(6){authoredSet()}
        val overrides=mapOf("block-1" to listOf(
            CustomWeekSlotOverride(2,0,"slot-0",extra)))
        val result=CustomCycleCoachAuditDomain.review(
            cycle(overrides=overrides),setup(),
            StarterExerciseTranche.movements(),inputs())
        assertEquals(CustomCoachAuditStatus.COACHING_POLICY_REQUIRED,result.status)
        assertEquals(3,result.weeks[0].workingSets)
        assertEquals(8,result.weeks[1].workingSets)
        assertEquals(3,result.weeks[2].workingSets)
        assertTrue(result.issues.any {
            it.kind==CustomCoachIssueKind.CONTINUITY_POLICY_MISSING
        })
        assertTrue(result.issues.any {
            it.kind==CustomCoachIssueKind.ABOVE_WEEKLY_MRV &&
            it.location.startsWith("week.2.")
        })
        assertFalse(result.issues.any {
            it.kind==CustomCoachIssueKind.ABOVE_WEEKLY_MRV &&
            it.location.startsWith("week.1.")
        })
        assertEquals(1,cycle(overrides=overrides).blocks.single()
            .days.first().slots.single().sets.size)
    }

    @Test fun disabledMovementOrDifferentCalendarBlocksCoachReview() {
        val result=CustomCycleCoachAuditDomain.review(
            cycle(),setup(excluded=setOf("bodyweight_crunch")),
            StarterExerciseTranche.movements(),inputs())
        assertEquals(CustomCoachAuditStatus.INVALID_SOURCE,result.status)
        assertTrue(result.issues.any{
            it.kind==CustomCoachIssueKind.EXCLUDED_EXERCISE
        })
        assertTrue(result.issues.any{
            it.kind==CustomCoachIssueKind.INVALID_CUSTOM_CYCLE
        })
        val mismatch=CustomCycleCoachAuditDomain.review(
            cycle(days=listOf(authoredDay(0),authoredDay(2))),
            setup(),StarterExerciseTranche.movements(),inputs())
        assertTrue(mismatch.issues.any{
            it.kind==CustomCoachIssueKind.ATHLETE_SCHEDULE_CONFLICT
        })
        assertNotEquals(CustomCoachAuditStatus.READY_FOR_ATHLETE_REVIEW,
            mismatch.status)
    }

    @Test fun malformedPoliciesAndImpossibleTimeCapsAreActionable() {
        val bad=policy().copy(revision="bad revision")
        val denied=CustomCycleCoachAuditDomain.review(
            cycle(),setup(),StarterExerciseTranche.movements(),
            inputs(policy=bad))
        assertTrue(denied.issues.any{
            it.kind==CustomCoachIssueKind.GOAL_POLICY_UNRESOLVED
        })
        val over=CustomCycleCoachAuditDomain.review(
            cycle(),setup(),StarterExerciseTranche.movements(),
            inputs(nonSet=mapOf(0 to 3600,2 to 200,4 to 200)))
        assertTrue(over.issues.any{
            it.kind==CustomCoachIssueKind.SESSION_HARD_LIMIT
        })
    }
    private fun twoBlockCycle(recoverySecond: Boolean = false): CustomTrainingCycle {
        val source=cycle(weeks=1)
        val original=source.blocks.single()
        val first=original.copy(days=original.days.map { day ->
            if(day.weekday==4) day else day.copy(slots=day.slots.map {
                it.copy(sets=listOf(authoredSet(),authoredSet()))
            })
        })
        val second=original.copy(
            blockId="block-2",
            label="Next training block",
            purpose=if(recoverySecond) CustomBlockPurpose.RECOVERY
                else CustomBlockPurpose.TRAINING,
            days=original.days.map { day -> day.copy(slots=day.slots.map {
                it.copy(sets=listOf(authoredSet()))
            }) }
        )
        return source.copy(blocks=listOf(first,second))
    }

    private fun intraBlockFiveToThreeWeekCycle(): CustomTrainingCycle {
        val heavierSets=listOf(authoredSet(),authoredSet())
        val overrides=mapOf("block-1" to listOf(
            CustomWeekSlotOverride(1,0,"slot-0",heavierSets),
            CustomWeekSlotOverride(1,2,"slot-2",heavierSets)))
        return cycle(weeks=3,overrides=overrides)
    }

    @Test fun withinBlockDropNeedsExplicitContinuityEvenWhenBothWeeksMeetMev() {
        val original=intraBlockFiveToThreeWeekCycle()
        val audit=CustomCycleCoachAuditDomain.review(original,setup(),
            StarterExerciseTranche.movements(),inputs())
        assertEquals(listOf(5,3,3),audit.weeks.map {it.workingSets})
        assertEquals(CustomCoachAuditStatus.COACHING_POLICY_REQUIRED,audit.status)
        assertTrue(audit.issues.any {
            it.kind==CustomCoachIssueKind.CONTINUITY_POLICY_MISSING
        })
        assertTrue(audit.issues.none {
            it.kind in setOf(CustomCoachIssueKind.BELOW_WEEKLY_MEV,
                CustomCoachIssueKind.ABOVE_WEEKLY_MRV)
        })
        assertFalse(audit.mayIssueWorkout)
        assertEquals(1,original.blocks.single().days.first()
            .slots.single().sets.size)
    }

    @Test fun smallerWithinBlockDropLimitRefusesOtherwiseIndividuallyValidWeeks() {
        val audit=CustomCycleCoachAuditDomain.review(
            intraBlockFiveToThreeWeekCycle(),setup(),
            StarterExerciseTranche.movements(),
            inputs().copy(continuityPolicy=CustomCoachVolumeContinuityPolicy(
                n("0.25"),n("0.25"))))
        assertEquals(CustomCoachAuditStatus.UNRESOLVED,audit.status)
        assertTrue(audit.issues.any {
            it.kind==CustomCoachIssueKind.UNJUSTIFIED_BLOCK_VOLUME_DROP &&
                it.location.contains("week.2.block.block-1")
        })
        assertTrue(audit.issues.any {
            it.kind==CustomCoachIssueKind.DIRECT_MUSCLE_DROP &&
                it.location.contains("week.2.ABS")
        })
    }

    @Test fun explicitlyApprovedWithinBlockDoseTransitionCanPassFullAudit() {
        val original=intraBlockFiveToThreeWeekCycle()
        val audit=CustomCycleCoachAuditDomain.review(
            original,setup(),StarterExerciseTranche.movements(),
            inputs().copy(continuityPolicy=CustomCoachVolumeContinuityPolicy(
                n("0.5"),n("0.5"))))
        assertEquals(audit.issues.toString(),
            CustomCoachAuditStatus.READY_FOR_ATHLETE_REVIEW,audit.status)
        assertTrue(audit.issues.none {
            it.kind in setOf(CustomCoachIssueKind.CONTINUITY_POLICY_MISSING,
                CustomCoachIssueKind.UNJUSTIFIED_BLOCK_VOLUME_DROP,
                CustomCoachIssueKind.DIRECT_MUSCLE_DROP)
        })
        assertEquals(listOf(5,3,3),audit.weeks.map {it.workingSets})
        assertFalse(audit.mayIssueWorkout)
    }

    private fun intraBlockThreeToFiveWeekCycle(): CustomTrainingCycle {
        val ramp=listOf(authoredSet(),authoredSet())
        val overrides=mapOf("block-1" to listOf(
            CustomWeekSlotOverride(2,0,"slot-0",ramp),
            CustomWeekSlotOverride(2,2,"slot-2",ramp),
            CustomWeekSlotOverride(3,0,"slot-0",ramp),
            CustomWeekSlotOverride(3,2,"slot-2",ramp)))
        return cycle(weeks=3,overrides=overrides)
    }

    @Test fun weeklyVolumeRampNeedsReviewedIncreaseCeilingEvenInsideMevMrv() {
        val source=intraBlockThreeToFiveWeekCycle()
        val missing=CustomCycleCoachAuditDomain.review(
            source,setup(),StarterExerciseTranche.movements(),inputs())
        assertEquals(listOf(3,5,5),missing.weeks.map {it.workingSets})
        assertEquals(CustomCoachAuditStatus.COACHING_POLICY_REQUIRED,
            missing.status)
        assertTrue(missing.issues.any {
            it.kind==CustomCoachIssueKind.CONTINUITY_POLICY_MISSING
        })
        val dropOnly=CustomCycleCoachAuditDomain.review(
            source,setup(),StarterExerciseTranche.movements(),
            inputs().copy(continuityPolicy=CustomCoachVolumeContinuityPolicy(
                n("0.5"),n("0.5"))))
        assertEquals(CustomCoachAuditStatus.COACHING_POLICY_REQUIRED,
            dropOnly.status)
        assertTrue(dropOnly.issues.any {
            it.kind==CustomCoachIssueKind.CONTINUITY_POLICY_MISSING
        })
    }

    @Test fun cappedWeeklyRampRejectsOversizedSetsAndMuscleSpecificIncrease() {
        val result=CustomCycleCoachAuditDomain.review(
            intraBlockThreeToFiveWeekCycle(),setup(),
            StarterExerciseTranche.movements(),
            inputs().copy(continuityPolicy=CustomCoachVolumeContinuityPolicy(
                n("0.5"),n("0.5"),n("0.5"),n("0.5"))))
        assertEquals(CustomCoachAuditStatus.UNRESOLVED,result.status)
        assertTrue(result.issues.any {
            it.kind==CustomCoachIssueKind.UNJUSTIFIED_TRAINING_VOLUME_SPIKE &&
                it.location.contains("week.2.block.block-1")
        })
        assertTrue(result.issues.any {
            it.kind==CustomCoachIssueKind.DIRECT_MUSCLE_SPIKE &&
                it.location.contains("week.2.ABS")
        })
        assertFalse(result.mayIssueWorkout)
    }

    @Test fun explicitlyAuthorizedWeeklyRampCanPassWithoutInventingAnySets() {
        val source=intraBlockThreeToFiveWeekCycle()
        val review=CustomCycleCoachAuditDomain.review(
            source,setup(),StarterExerciseTranche.movements(),
            inputs().copy(continuityPolicy=CustomCoachVolumeContinuityPolicy(
                n("0.5"),n("0.5"),n("0.75"),n("0.75"))))
        assertEquals(review.issues.toString(),
            CustomCoachAuditStatus.READY_FOR_ATHLETE_REVIEW,review.status)
        assertTrue(review.issues.none {
            it.kind in setOf(CustomCoachIssueKind.CONTINUITY_POLICY_MISSING,
                CustomCoachIssueKind.UNJUSTIFIED_TRAINING_VOLUME_SPIKE,
                CustomCoachIssueKind.DIRECT_MUSCLE_SPIKE)
        })
        assertEquals(listOf(3,5,5),review.weeks.map {it.workingSets})
        assertEquals(1,source.blocks.single().days.first()
            .slots.single().sets.size)
        assertFalse(review.mayIssueWorkout)
    }

    @Test fun unexplainedBlockVolumeCollapseIsNotApproved() {
        val cycle=twoBlockCycle()
        val audit=CustomCycleCoachAuditDomain.review(
            cycle,setup(),StarterExerciseTranche.movements(),
            inputs().copy(continuityPolicy=CustomCoachVolumeContinuityPolicy(
                n("0.25"),n("0.25"))))
        assertEquals(listOf(5,3),audit.weeks.map {it.workingSets})
        assertEquals(CustomCoachAuditStatus.UNRESOLVED,audit.status)
        assertTrue(audit.issues.any {
            it.kind==CustomCoachIssueKind.UNJUSTIFIED_BLOCK_VOLUME_DROP &&
                it.location.contains("block-2")
        })
        assertTrue(audit.issues.any {
            it.kind==CustomCoachIssueKind.DIRECT_MUSCLE_DROP &&
                it.location.contains("ABS")
        })
        assertFalse(audit.mayIssueWorkout)
        assertEquals(5,cycle.blocks.first().days.flatMap { it.slots }
            .flatMap { it.sets }.count {it.type==DraftSetType.WORKING})
    }

    @Test fun expresslyReviewedTransitionLimitCanAcceptThreeOfFiveSets() {
        val result=CustomCycleCoachAuditDomain.review(
            twoBlockCycle(),setup(),StarterExerciseTranche.movements(),
            inputs().copy(continuityPolicy=CustomCoachVolumeContinuityPolicy(
                n("0.5"),n("0.5"))))
        assertEquals(result.issues.toString(),
            CustomCoachAuditStatus.READY_FOR_ATHLETE_REVIEW,result.status)
        assertTrue(result.issues.none {
            it.kind in setOf(CustomCoachIssueKind.UNJUSTIFIED_BLOCK_VOLUME_DROP,
                CustomCoachIssueKind.DIRECT_MUSCLE_DROP)
        })
    }

    @Test fun declaredRecoveryBlockIsNotMisreportedAsNormalTrainingCollapse() {
        val result=CustomCycleCoachAuditDomain.review(
            twoBlockCycle(recoverySecond=true),setup(),
            StarterExerciseTranche.movements(),
            inputs().copy(continuityPolicy=CustomCoachVolumeContinuityPolicy(
                n("0.1"),n("0.1"))))
        assertTrue(result.issues.none {
            it.kind in setOf(CustomCoachIssueKind.UNJUSTIFIED_BLOCK_VOLUME_DROP,
                CustomCoachIssueKind.DIRECT_MUSCLE_DROP)
        })
    }

    @Test fun missingContinuityPolicyBlocksTwoTrainingBlocksEvenIfBothMeetMev() {
        val audit=CustomCycleCoachAuditDomain.review(
            twoBlockCycle(),setup(),StarterExerciseTranche.movements(),
            inputs())
        assertEquals(listOf(5,3),audit.weeks.map { it.workingSets })
        assertEquals(CustomCoachAuditStatus.COACHING_POLICY_REQUIRED,audit.status)
        assertTrue(audit.issues.any {
            it.kind==CustomCoachIssueKind.CONTINUITY_POLICY_MISSING
        })
        assertFalse(audit.mayIssueWorkout)
    }

    @Test fun invalidContinuityPolicyNeverPassesSilently() {
        val result=CustomCycleCoachAuditDomain.review(
            twoBlockCycle(),setup(),StarterExerciseTranche.movements(),
            inputs().copy(continuityPolicy=CustomCoachVolumeContinuityPolicy(
                n("1.5"),n("-0.1"))))
        assertEquals(CustomCoachAuditStatus.UNRESOLVED,result.status)
        assertTrue(result.issues.any {
            it.kind==CustomCoachIssueKind.CONTINUITY_POLICY_INVALID
        })
    }

    private fun completeUserPolicyRequest():CustomCoachPolicyAuthoringRequest {
        val complete=TrainingMuscle.entries.associateWith{m->
            if(m==TrainingMuscle.ABS)
                CustomMuscleDoseEntry("3","4","5","3")
            else CustomMuscleDoseEntry("0","0","0","0")
        }
        return CustomCoachPolicyAuthoringRequest(
            phases=listOf(CustomPhasePolicyEntry(
                phase=TrainingPhase.HYPERTROPHY,
                policyId="athlete-coach-1",revision="policy-v1",
                rationale="Explicitly reviewed athlete core-dose prescription",
                baseline=complete,selected=complete,
                minimumPurposeDays=mapOf(
                    ComposerSessionPurpose.HYPERTROPHY to "3"))),
            nonSetSecondsByWeekday=mapOf(0 to "200",2 to "200",4 to "200")
        )
    }

    @Test fun explicitCompleteSixteenMusclePolicyCanReachExactCoachAudit() {
        val result=CustomCoachPolicyAuthoringDomain.inspect(
            cycle(),setup(),completeUserPolicyRequest())
        assertTrue(result.toString(),
            result is CustomCoachPolicyAuthoringResult.Ready)
        val inputs=(result as CustomCoachPolicyAuthoringResult.Ready).inputs
        assertEquals(16,inputs.policiesByPhase.getValue(
            TrainingPhase.HYPERTROPHY).selectedBounds.size)
        assertEquals(n("3"),inputs.policiesByPhase.getValue(
            TrainingPhase.HYPERTROPHY).selectedBounds.first{
                it.muscle==TrainingMuscle.ABS}.mev)
        assertEquals(200,inputs.nonSetSecondsByWeekday[0])
        val audit=CustomCycleCoachAuditDomain.review(cycle(),setup(),
            StarterExerciseTranche.movements(),inputs)
        assertEquals(audit.issues.toString(),
            CustomCoachAuditStatus.READY_FOR_ATHLETE_REVIEW,audit.status)
        assertFalse(audit.mayIssueWorkout)
    }

    @Test fun blankPolicyCannotBeInferredAsZeroOrApproved() {
        val missing=completeUserPolicyRequest().copy(phases=emptyList())
        val result=CustomCoachPolicyAuthoringDomain.inspect(cycle(),setup(),
            missing)
        assertTrue(result.toString(),
            result is CustomCoachPolicyAuthoringResult.Blocked)
        val base=completeUserPolicyRequest()
        val absentZero=base.copy(phases=base.phases.map{phase->
            phase.copy(selected=phase.selected-
                TrainingMuscle.WRIST_EXTENSORS)
        })
        val rejected=CustomCoachPolicyAuthoringDomain.inspect(cycle(),setup(),
            absentZero)
        assertTrue(rejected.toString(),
            rejected is CustomCoachPolicyAuthoringResult.Blocked)
        assertTrue((rejected as CustomCoachPolicyAuthoringResult.Blocked)
            .missingOrInvalid.any{it.contains("all 16")})
    }

    @Test fun invalidMuscleDoseAndUnreviewedOverheadNeverBecomePolicy() {
        val base=completeUserPolicyRequest()
        val bad=base.copy(phases=base.phases.map{phase->
            phase.copy(selected=phase.selected+(
                TrainingMuscle.ABS to CustomMuscleDoseEntry(
                    "8","4","5","4")))
        },nonSetSecondsByWeekday=mapOf(0 to "200",2 to "",4 to "200"))
        val denied=CustomCoachPolicyAuthoringDomain.inspect(cycle(),setup(),bad)
        assertTrue(denied.toString(),
            denied is CustomCoachPolicyAuthoringResult.Blocked)
        val issues=(denied as CustomCoachPolicyAuthoringResult.Blocked)
            .missingOrInvalid
        assertTrue(issues.any{it.contains("ABS")})
        assertTrue(issues.any{it.contains("Day 3")})
    }

    @Test fun continuityCeilingsAreOnlyKeptWhenExplicitAndValid() {
        val base=completeUserPolicyRequest()
        val complete=base.copy(
            totalDropFraction="0.20",directDropFraction="0.25",
            totalRiseFraction="0.10",directRiseFraction="0.15")
        val ready=CustomCoachPolicyAuthoringDomain.inspect(cycle(),setup(),
            complete) as CustomCoachPolicyAuthoringResult.Ready
        val retained=checkNotNull(ready.inputs.continuityPolicy)
        assertEquals(n("0.20"),retained.maximumTotalWorkingSetDropFraction)
        assertEquals(n("0.15"),retained.maximumDirectMuscleRiseFraction)
        assertNull((CustomCoachPolicyAuthoringDomain.inspect(
            cycle(),setup(),base) as CustomCoachPolicyAuthoringResult.Ready)
            .inputs.continuityPolicy)
        assertTrue(CustomCoachPolicyAuthoringDomain.inspect(cycle(),setup(),
            base.copy(totalDropFraction="0.2",directDropFraction="",
                totalRiseFraction="0.1")) is
                    CustomCoachPolicyAuthoringResult.Blocked)
        assertTrue(CustomCoachPolicyAuthoringDomain.inspect(cycle(),setup(),
            base.copy(totalDropFraction="2",directDropFraction="0.4")) is
                    CustomCoachPolicyAuthoringResult.Blocked)
    }

    @Test fun missingCoachTargetsStillPermitFactualAuthoredEvidenceWithoutApprovingDose() {
        val audited=CustomCycleCoachAuditDomain.review(
            cycle(),setup(),StarterExerciseTranche.movements())
        assertEquals(CustomCoachAuditStatus.COACHING_POLICY_REQUIRED,audited.status)
        val result=CustomCoachPolicyEvidenceDomain.review(audited)
        assertTrue(result.toString(),
            result is CustomCoachPolicyEvidenceReview.Reviewed)
        val evidence=(result as CustomCoachPolicyEvidenceReview.Reviewed).evidence
        val phase=evidence.phaseWorkloads.getValue(TrainingPhase.HYPERTROPHY)
        assertEquals(3,phase.trainingWeekCount)
        assertEquals(3,phase.minimumWorkingSets)
        assertEquals(3,phase.maximumWorkingSets)
        assertEquals(n("3"),phase.muscleRanges.getValue(
            TrainingMuscle.ABS).minimumWeeklyCredit)
        assertEquals(n("0"),phase.muscleRanges.getValue(
            TrainingMuscle.WRIST_EXTENSORS).maximumWeeklyCredit)
        assertEquals(3,phase.muscleRanges.getValue(
            TrainingMuscle.ABS).minimumDirectWeekdays)
        assertEquals(130L,phase.minimumKnownSecondsByDay[0])
        assertEquals(2,evidence.transitions.size)
        assertEquals(n("0.000"),
            evidence.transitions.first().workingSetChangeFraction)
        assertFalse(evidence.canSupplyDoseTargets)
        assertFalse(evidence.canSupplyContinuityCeilings)
        assertFalse(evidence.canIssueWorkout)
    }

    @Test fun evidenceTracksActualTrainingDropsAcrossBlockAndRecoveryBoundaries() {
        val authored=twoBlockCycle(recoverySecond=false)
        val inspected=CustomCycleCoachAuditDomain.review(
            authored,setup(),StarterExerciseTranche.movements())
        val result=CustomCoachPolicyEvidenceDomain.review(inspected)
        assertTrue(result.toString(),
            result is CustomCoachPolicyEvidenceReview.Reviewed)
        val evidence=(result as CustomCoachPolicyEvidenceReview.Reviewed).evidence
        assertEquals(2,evidence.phaseWorkloads.getValue(
            TrainingPhase.HYPERTROPHY).trainingWeekCount)
        assertEquals(3,evidence.phaseWorkloads.getValue(
            TrainingPhase.HYPERTROPHY).minimumWorkingSets)
        assertEquals(5,evidence.phaseWorkloads.getValue(
            TrainingPhase.HYPERTROPHY).maximumWorkingSets)
        assertEquals(n("-0.400"),
            evidence.transitions.single().workingSetChangeFraction)
        val withRecovery=CustomCycleCoachAuditDomain.review(
            twoBlockCycle(recoverySecond=true),setup(),
            StarterExerciseTranche.movements())
        val recoveryEvidence=CustomCoachPolicyEvidenceDomain.review(withRecovery)
        assertTrue(recoveryEvidence.toString(),
            recoveryEvidence is CustomCoachPolicyEvidenceReview.Reviewed)
        assertTrue((recoveryEvidence as CustomCoachPolicyEvidenceReview.Reviewed)
            .evidence.transitions.isEmpty())
    }

    @Test fun unverifiedCatalogStructureAndSourceCannotGenerateFakeVolumeEvidence() {
        val missingCatalog=CustomCycleCoachAuditDomain.review(
            cycle(),setup(),emptyList())
        assertEquals(CustomCoachPolicyEvidenceReview.Rejected(
            CustomCoachEvidenceRefusal.UNVERIFIED_CATALOG),
            CustomCoachPolicyEvidenceDomain.review(missingCatalog))
        val mismatch=CustomCycleCoachAuditDomain.review(
            cycle(),setup(excluded=setOf("bodyweight_crunch")),
            StarterExerciseTranche.movements())
        assertTrue(CustomCoachPolicyEvidenceDomain.review(mismatch) is
            CustomCoachPolicyEvidenceReview.Rejected)
        val invalid=CustomCycleCoachAuditDomain.review(
            cycle(days=emptyList()),setup(),StarterExerciseTranche.movements())
        assertTrue(CustomCoachPolicyEvidenceDomain.review(invalid) is
            CustomCoachPolicyEvidenceReview.Rejected)
    }

    @Test fun actionableCoachReviewIsReadOnlyForFullyCompliantCustomCycle() {
        val authored=cycle()
        val audit=CustomCycleCoachAuditDomain.review(
            authored,setup(),StarterExerciseTranche.movements(),inputs())
        val result=CustomCoachActionReviewDomain.review(audit)
        assertTrue(result.toString(),
            result is CustomCoachActionReviewResult.Reviewed)
        val review=(result as CustomCoachActionReviewResult.Reviewed).review
        assertTrue(review.findings.toString(),review.findings.isEmpty())
        assertEquals(authored.revision,review.cycleRevision)
        assertFalse(review.mayApplyChange)
        assertFalse(review.mayApprovePolicy)
        assertFalse(review.mayIssueWorkout)
        assertEquals(authored,audit.structuralReview?.verifiedDraft)
    }

    @Test fun unreviewedVolumeDropIsDescribedNotCalledUnsafeOrAutoCorrected() {
        val authored=intraBlockFiveToThreeWeekCycle()
        val audit=CustomCycleCoachAuditDomain.review(
            authored,setup(),StarterExerciseTranche.movements())
        val result=CustomCoachActionReviewDomain.review(audit)
        assertTrue(result.toString(),result is CustomCoachActionReviewResult.Reviewed)
        val findings=(result as CustomCoachActionReviewResult.Reviewed)
            .review.findings
        assertTrue(findings.any{
            it.status==CustomCoachFindingStatus.COACH_POLICY_BLOCKER &&
                it.location=="continuity"
        })
        val observed=findings.single{
            it.status==CustomCoachFindingStatus.OBSERVED_CHANGE
        }
        assertEquals(2,observed.absoluteWeek)
        assertEquals("block-1",observed.blockId)
        assertTrue(observed.explanation.contains("5 working sets"))
        assertTrue(observed.explanation.contains("3 in training week 2"))
        assertTrue(observed.options.any{
            it.kind==CustomCoachActionKind.EDIT_AUTHORED_WEEK
        })
        assertFalse(findings.any{
            it.status==CustomCoachFindingStatus.CONFIRMED_POLICY_VIOLATION
        })
        assertEquals(authored,audit.structuralReview?.verifiedDraft)
    }

    @Test fun reviewedContinuityViolationUsesAuditedWeekAndDoesNotDoubleCountChange() {
        val audit=CustomCycleCoachAuditDomain.review(
            intraBlockFiveToThreeWeekCycle(),setup(),
            StarterExerciseTranche.movements(),
            inputs().copy(continuityPolicy=CustomCoachVolumeContinuityPolicy(
                n("0.25"),n("0.25"))))
        val result=CustomCoachActionReviewDomain.review(audit)
        assertTrue(result.toString(),result is CustomCoachActionReviewResult.Reviewed)
        val findings=(result as CustomCoachActionReviewResult.Reviewed)
            .review.findings
        val violation=findings.single{
            it.status==CustomCoachFindingStatus.CONFIRMED_POLICY_VIOLATION &&
                it.title=="Decline exceeds the reviewed continuity policy" &&
                it.location.startsWith("week.2.block.")
        }
        assertEquals(2,violation.absoluteWeek)
        assertEquals("block-1",violation.blockId)
        assertTrue(violation.options.any{
            it.kind==CustomCoachActionKind.EDIT_AUTHORED_WEEK
        })
        assertFalse(findings.any{
            it.status==CustomCoachFindingStatus.OBSERVED_CHANGE &&
                it.absoluteWeek==2
        })
    }

    @Test fun coachedVolumeViolationOffersInspectionNotAnUnvalidatedNewNumber() {
        val audit=CustomCycleCoachAuditDomain.review(
            cycle(),setup(),StarterExerciseTranche.movements(),
            inputs(policy=policy(bounds=bounds(absMev="4",absMrv="5"))))
        val result=CustomCoachActionReviewDomain.review(audit)
        assertTrue(result.toString(),result is CustomCoachActionReviewResult.Reviewed)
        val findings=(result as CustomCoachActionReviewResult.Reviewed)
            .review.findings
        assertTrue(findings.any{
            it.status==CustomCoachFindingStatus.CONFIRMED_POLICY_VIOLATION &&
                it.title=="Below the reviewed weekly volume minimum" &&
                it.absoluteWeek==1 &&
                it.explanation.contains("MEV")
        })
        assertFalse(findings.any{it.options.any{option->
            option.explanation.contains("raise it to ") ||
                option.explanation.contains("lower it to ")
        }})
    }

    @Test fun unverifiedCatalogOrAuthoredStructureCannotYieldCoachOptions() {
        val noCatalog=CustomCycleCoachAuditDomain.review(
            cycle(),setup(),emptyList())
        assertTrue(CustomCoachActionReviewDomain.review(noCatalog) is
            CustomCoachActionReviewResult.Rejected)
        val malformed=CustomCycleCoachAuditDomain.review(
            cycle(days=emptyList()),setup(),StarterExerciseTranche.movements())
        assertTrue(CustomCoachActionReviewDomain.review(malformed) is
            CustomCoachActionReviewResult.Rejected)
    }

}
