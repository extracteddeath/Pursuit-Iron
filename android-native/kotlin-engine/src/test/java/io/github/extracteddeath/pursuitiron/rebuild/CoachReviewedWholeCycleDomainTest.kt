package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class CoachReviewedWholeCycleDomainTest {
    // Shared immutable fixture across JUnit test class instances: the
    // bounded 32-candidate composer runs only once per phase and profile.
    private companion object {
    val powerSeed by lazy {StarterProgramReleaseFixture.request(
        ComposerSplit.ULPPL,TrainingPhase.STRENGTH)}
    private val hyperSeed by lazy {StarterProgramReleaseFixture.request(
        ComposerSplit.ULPPL,TrainingPhase.HYPERTROPHY)}
    private val hyperStarters by lazy {
        TrainingPhase.entries.associateWith {phase->
            CoachFirstProgramStartDomain.review(
                hyperSeed.copy(phase=phase))
        }
    }
    private val powerStarters by lazy {
        TrainingPhase.entries.associateWith {phase->
            CoachFirstProgramStartDomain.review(
                (if(phase==TrainingPhase.HYPERTROPHY)hyperSeed
                    else powerSeed).copy(setup=powerSeed.setup,phase=phase))
        }
    }
    }
    private fun request(
        goal:AthleteTrainingGoal=AthleteTrainingGoal.HYPERTROPHY,
        totalWeeks:Int=6,
        event:Boolean=false,
        withHypertrophy:Boolean=true,
        withStrength:Boolean=true,
        withPeak:Boolean=true,
        continuity:CustomCoachVolumeContinuityPolicy?=null,
        targets:Set<String> = if(event)setOf("barbell_bench") else emptySet()
    ):ReviewedWholeCycleRequest {
        // All phases use the SAME reviewed athlete and gym. The existing
        // release fixture supplies a POWERBUILDING setup for strength.
        val setup=if(goal==AthleteTrainingGoal.HYPERTROPHY)
            hyperSeed.setup else powerSeed.setup
        val verified=if(goal==AthleteTrainingGoal.HYPERTROPHY)
            hyperStarters else powerStarters
        val starters=linkedMapOf<TrainingPhase,FirstProgramStartReview>()
        if(withHypertrophy)starters[TrainingPhase.HYPERTROPHY]=
            checkNotNull(verified[TrainingPhase.HYPERTROPHY])
        if(withStrength)starters[TrainingPhase.STRENGTH]=
            checkNotNull(verified[TrainingPhase.STRENGTH])
        if(withPeak)starters[TrainingPhase.PEAK]=
            checkNotNull(verified[TrainingPhase.PEAK])
        return ReviewedWholeCycleRequest(
            "cycle-verified","Verified cycle",setup,
            StarterExerciseTranche.movements(),starters,totalWeeks,event,
            continuity,targets)
    }

    @Test fun hypertrophyWithoutTestKeepsOneVerifiedPhaseAndAuditsEveryWeek() {
        val inputs=request(totalWeeks=6)
        val result=CoachReviewedWholeCycleDomain.propose(inputs)
        assertEquals(result.explanation,
            ReviewedWholeCycleStatus.OPTIONS_AUDITED,result.status)
        assertEquals(1,result.candidates.size)
        val candidate=result.candidates.single()
        assertEquals(ReviewedWholeCycleCandidateStatus.READY_FOR_ATHLETE_REVIEW,
            candidate.status)
        assertEquals(listOf(TrainingPhase.HYPERTROPHY to 6),
            candidate.allocations)
        assertEquals(6,candidate.audit?.weeks?.size)
        assertTrue(candidate.audit!!.issues.isEmpty())
        assertEquals(6,candidate.proposedCycle!!.blocks.single().weeks)
        assertFalse(result.mayIssueWorkout)
        assertFalse(result.maySaveProgram)
        assertFalse(candidate.mayApplyAutomatically)
        assertEquals(inputs.phaseStarts[TrainingPhase.HYPERTROPHY]!!.policy?.phase,
            candidate.proposedCycle.blocks.single().phase)
    }

    @Test fun powerbuildingAuditsCompetingFullBlockAllocationsWithNoAutomaticPeak() {
        val inputs=request(goal=AthleteTrainingGoal.POWERBUILDING,
            totalWeeks=9,continuity=CustomCoachVolumeContinuityPolicy(
                BigDecimal("1"),BigDecimal("1"),
                BigDecimal("1"),BigDecimal("1")))
        val result=CoachReviewedWholeCycleDomain.propose(inputs)
        assertTrue(result.candidates.size in 2..3)
        assertTrue(result.candidates.all {it.allocations.sumOf {p->p.second}==9})
        assertTrue(result.candidates.all {it.allocations.map {p->p.first}==
            listOf(TrainingPhase.HYPERTROPHY,TrainingPhase.STRENGTH)})
        assertTrue(result.candidates.all {it.proposedCycle==null ||
            it.audit?.weeks?.size==9})
        assertTrue(result.candidates.all {it.status==
            ReviewedWholeCycleCandidateStatus.READY_FOR_ATHLETE_REVIEW ||
            it.audit!=null})
        assertTrue(result.candidates.all {it.proposedCycle==null ||
            it.proposedCycle.blocks.none {b->b.phase==TrainingPhase.PEAK}})
        assertFalse(result.mayApplyAutomatically)
    }

    @Test fun missingPeakAndMissingTransitionLimitsNeverBecomeImplicitApproval() {
        val withoutPeak=CoachReviewedWholeCycleDomain.propose(
            request(goal=AthleteTrainingGoal.POWERBUILDING,totalWeeks=9,
                event=true,withPeak=false))
        assertEquals(ReviewedWholeCycleStatus.NO_REVIEWABLE_CYCLE,
            withoutPeak.status)
        assertTrue(withoutPeak.candidates.all {
            it.status==ReviewedWholeCycleCandidateStatus.MISSING_VERIFIED_PHASE})
        assertNull(withoutPeak.initialReviewOptionId)
        val noContinuity=CoachReviewedWholeCycleDomain.propose(
            request(goal=AthleteTrainingGoal.POWERBUILDING,totalWeeks=8))
        assertEquals(ReviewedWholeCycleStatus.NO_REVIEWABLE_CYCLE,
            noContinuity.status)
        assertTrue(noContinuity.candidates.any {it.status==
            ReviewedWholeCycleCandidateStatus.FULL_CYCLE_POLICY_REQUIRED})
        assertTrue(noContinuity.candidates.all {
            it.status!=ReviewedWholeCycleCandidateStatus.READY_FOR_ATHLETE_REVIEW})
    }

    @Test fun testPreparationRequiresNamedPrimaryLiftAndDistinctPeakReview() {
        val bounds=CustomCoachVolumeContinuityPolicy(
            BigDecimal("1"),BigDecimal("1"),
            BigDecimal("1"),BigDecimal("1"))
        val source=request(goal=AthleteTrainingGoal.POWERBUILDING,
            totalWeeks=9,event=true,continuity=bounds)
        val missing=CoachReviewedWholeCycleDomain.propose(
            source.copy(performanceTargetExerciseIds=emptySet()))
        assertEquals(ReviewedWholeCycleStatus.INVALID_INPUT,missing.status)
        assertTrue(missing.candidates.isEmpty())
        val unrelated=CoachReviewedWholeCycleDomain.propose(
            source.copy(performanceTargetExerciseIds=setOf("barbell_ohp")))
        assertEquals(ReviewedWholeCycleStatus.NO_REVIEWABLE_CYCLE,
            unrelated.status)
        assertTrue(unrelated.candidates.all {candidate ->
            candidate.status==
                ReviewedWholeCycleCandidateStatus.PERFORMANCE_TARGET_UNRESOLVED &&
                candidate.explanation.contains("barbell_ohp")})
        val matched=CoachReviewedWholeCycleDomain.propose(source)
        assertEquals(ReviewedWholeCycleStatus.NO_REVIEWABLE_CYCLE,matched.status)
        assertTrue(matched.candidates.all {it.proposedCycle!=null &&
            it.audit?.weeks?.size==9})
        assertTrue(matched.candidates.none {
            it.status==ReviewedWholeCycleCandidateStatus.PERFORMANCE_TARGET_UNRESOLVED ||
                it.status==ReviewedWholeCycleCandidateStatus.READY_FOR_ATHLETE_REVIEW})
        assertTrue(matched.candidates.any {it.status==
            ReviewedWholeCycleCandidateStatus.PEAK_PREPARATION_REVIEW_REQUIRED})
        assertNull(matched.initialReviewOptionId)
        assertFalse(matched.mayIssueWorkout)
    }

    /**
     * The whole-cycle owner must accept the same bounded catalog the
     * first-program and catalog composer accept. Extra source identities
     * are deliberately UNAVAILABLE: this is scale capacity, not a claim
     * that 376 movements are reviewed or should enter a user's plan.
     */
    @Test fun completeCycleAcceptsWebSizedCatalogWithoutSilentMovementSubstitution() {
        val source=request(totalWeeks=6)
        val baseline=CoachReviewedWholeCycleDomain.propose(source)
        assertEquals(ReviewedWholeCycleStatus.OPTIONS_AUDITED,baseline.status)
        val prototype=source.catalog.first()
        fun extended(total:Int)=source.catalog+(
            1..(total-source.catalog.size)).map{index->
                prototype.copy(
                    exerciseId="unavailable-catalog-$index",
                    familyId="unavailable-family-$index",
                    variantId="provisional",
                    equipmentId="unavailable-physical-station",
                    expectedLoadBasis=LoadBasis.EXERCISE_OVERRIDE,
                    attachmentId=null,
                    requiredFacilityIds=emptySet(),
                    recoveryGroupId=null,
                    minimumRecoveryGroupDayGap=1)
            }
        val webSize=extended(376)
        val withWebInventory=CoachReviewedWholeCycleDomain.propose(
            source.copy(catalog=webSize))
        assertEquals("A valid 376-ID inventory must not be discarded.",
            ReviewedWholeCycleStatus.OPTIONS_AUDITED,
            withWebInventory.status)
        assertEquals(baseline.initialReviewOptionId,
            withWebInventory.initialReviewOptionId)
        assertEquals(baseline.candidates.map{it.allocations},
            withWebInventory.candidates.map{it.allocations})
        assertEquals(baseline.candidates.map{it.status},
            withWebInventory.candidates.map{it.status})
        assertTrue(withWebInventory.candidates.all {candidate->
            candidate.audit?.status==CustomCoachAuditStatus.READY_FOR_ATHLETE_REVIEW &&
                candidate.audit.weeks.size==6
        })
        val unavailable=webSize.drop(source.catalog.size)
            .map{it.exerciseId}.toSet()
        assertTrue(withWebInventory.candidates.flatMap{candidate->
            candidate.proposedCycle?.blocks.orEmpty()
        }.flatMap{block->block.days}.flatMap{day->day.slots}
            .none{it.exerciseId in unavailable})
        assertFalse(withWebInventory.mayIssueWorkout)
        assertFalse(withWebInventory.mayApplyAutomatically)
        assertFalse(withWebInventory.maySaveProgram)

        // Verify full MULTI-BLOCK phase transitions, not only a one-phase
        // hypertrophy template. The original two-block baseline already
        // passes the independent all-week continuity audit.
        val multiSource=request(goal=AthleteTrainingGoal.POWERBUILDING,
            totalWeeks=9,continuity=CustomCoachVolumeContinuityPolicy(
                BigDecimal("1"),BigDecimal("1"),
                BigDecimal("1"),BigDecimal("1")))
        val multiBaseline=CoachReviewedWholeCycleDomain.propose(multiSource)
        val multiWide=CoachReviewedWholeCycleDomain.propose(
            multiSource.copy(catalog=webSize))
        assertEquals(multiBaseline.status,multiWide.status)
        assertEquals(multiBaseline.initialReviewOptionId,
            multiWide.initialReviewOptionId)
        assertEquals(multiBaseline.candidates.map{it.status},
            multiWide.candidates.map{it.status})
        assertEquals(multiBaseline.candidates.map{it.allocations},
            multiWide.candidates.map{it.allocations})
        assertTrue(multiWide.candidates.any{
            it.audit?.weeks?.size==9 && it.proposedCycle?.blocks?.size==2
        })
        assertTrue(multiWide.candidates.flatMap{candidate->
            candidate.proposedCycle?.blocks.orEmpty()
        }.flatMap{block->block.days}.flatMap{day->day.slots}
            .none{it.exerciseId in unavailable})
        assertFalse(multiWide.mayIssueWorkout)
        assertFalse(multiWide.maySaveProgram)
        assertFalse(multiWide.mayApplyAutomatically)

        // Same exact 1,024-entry shared capacity boundary, no bypass from
        // the whole-cycle constructor or a silent truncation to 256.
        assertEquals(ReviewedWholeCycleStatus.INVALID_INPUT,
            CoachReviewedWholeCycleDomain.propose(source.copy(
                catalog=extended(
                    CoachExerciseCatalogDomain.MAX_CATALOG_ENTRIES+1))).status)
        val collision=webSize.toMutableList()
        collision[collision.lastIndex]=collision[collision.lastIndex].copy(
            familyId=collision[collision.lastIndex-1].familyId)
        assertEquals(ReviewedWholeCycleStatus.INVALID_INPUT,
            CoachReviewedWholeCycleDomain.propose(source.copy(
                catalog=collision)).status)
    }

    @Test fun eventAndFeasibilityAreExplicitNotInvented() {
        val unsupported=CoachReviewedWholeCycleDomain.propose(
            request(goal=AthleteTrainingGoal.HYPERTROPHY,
                totalWeeks=8,event=true))
        assertEquals(ReviewedWholeCycleStatus.NO_REVIEWABLE_CYCLE,
            unsupported.status)
        assertTrue(unsupported.candidates.isEmpty())
        val invalid=CoachReviewedWholeCycleDomain.propose(
            request(totalWeeks=27))
        assertEquals(ReviewedWholeCycleStatus.INVALID_INPUT,invalid.status)
        val incomplete=CoachReviewedWholeCycleDomain.propose(
            request(goal=AthleteTrainingGoal.POWERBUILDING,
                totalWeeks=8,withStrength=false))
        assertEquals(ReviewedWholeCycleStatus.NO_REVIEWABLE_CYCLE,
            incomplete.status)
        assertTrue(incomplete.candidates.all {
            it.status==ReviewedWholeCycleCandidateStatus.MISSING_VERIFIED_PHASE})
    }
}
