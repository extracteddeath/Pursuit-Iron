package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class CoachReviewedWholeCycleDomainTest {
    private fun request(
        goal:AthleteTrainingGoal=AthleteTrainingGoal.HYPERTROPHY,
        totalWeeks:Int=6,
        event:Boolean=false,
        withHypertrophy:Boolean=true,
        withStrength:Boolean=true,
        withPeak:Boolean=true,
        continuity:CustomCoachVolumeContinuityPolicy?=null
    ):ReviewedWholeCycleRequest {
        // The test uses the real curated exercise catalog, real confirmed
        // gym stock and the complete first-program composer. All phase
        // policies must match the exact same athlete/goal/gym.
        val source=StarterProgramReleaseFixture.request(
            ComposerSplit.ULPPL,TrainingPhase.STRENGTH)
        val hyperSetup=StarterProgramReleaseFixture.request(
            ComposerSplit.ULPPL,TrainingPhase.HYPERTROPHY)
        val setup=if(goal==AthleteTrainingGoal.HYPERTROPHY)
            hyperSetup.setup else {
            // The existing release fixture is POWERBUILDING for strength.
            // Strength-only is tested by the separately validated goal gate.
            source.setup
        }
        val starters=linkedMapOf<TrainingPhase,FirstProgramStartReview>()
        if(withHypertrophy)starters[TrainingPhase.HYPERTROPHY]=
            CoachFirstProgramStartDomain.review(hyperSetup.copy(setup=setup))
        if(withStrength)starters[TrainingPhase.STRENGTH]=
            CoachFirstProgramStartDomain.review(source.copy(setup=setup))
        if(withPeak)starters[TrainingPhase.PEAK]=
            CoachFirstProgramStartDomain.review(source.copy(
                setup=setup,phase=TrainingPhase.PEAK))
        return ReviewedWholeCycleRequest(
            "cycle-verified","Verified cycle",setup,
            StarterExerciseTranche.movements(),starters,totalWeeks,event,
            continuity)
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
