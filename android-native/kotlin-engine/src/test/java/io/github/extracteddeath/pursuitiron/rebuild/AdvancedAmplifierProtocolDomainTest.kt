package io.github.extracteddeath.pursuitiron.rebuild

import org.junit.Assert.*
import org.junit.Test

class AdvancedAmplifierProtocolDomainTest {
    @Test fun myoTemplateRequiresMultipleShortSameLoadMiniSets() {
        val plan = AdvancedAmplifierProtocolDomain.template(CustomAmplifierKind.MYO_REPS)
        assertEquals(12..20, plan.activationReps)
        assertEquals(3..5, plan.followUpReps)
        assertEquals(3..5, plan.followUpRounds)
        assertEquals(10..30, plan.intraSetRestSeconds)
        assertEquals(AmplifierLoadRule.SAME_LOAD, plan.loadRule)
        assertTrue(plan.oneLine.contains("mini-sets"))
        assertTrue(plan.techniqueCue.contains("Stop"))
    }

    @Test fun underTargetEndsMyoSequenceInsteadOfForcingRemainingRounds() {
        fun step(target: Int, reps: List<Int>, cap: Int = 5, compromised: Boolean = false) =
            AdvancedAmplifierProtocolDomain.nextMyoRepStep(
                target, reps, cap, techniqueCompromised = compromised)
        assertEquals(MyoRepNextStep.KEEP_GOING, step(4, listOf(4, 4), 5))
        assertEquals(MyoRepNextStep.STOP_REP_DROP, step(4, listOf(4, 4, 3), 5))
        assertEquals(MyoRepNextStep.KEEP_GOING, step(5, listOf(5, 4), 3))
        assertEquals(MyoRepNextStep.STOP_REP_DROP, step(5, listOf(5, 4, 3), 5))
        assertEquals(MyoRepNextStep.STOP_TECHNIQUE,
            step(4, listOf(4), 5, compromised = true))
    }

    @Test fun rangeBoundaryAndCapAreExplicit() {
        fun step(target: Int, reps: List<Int>, cap: Int = 5, compromised: Boolean = false) =
            AdvancedAmplifierProtocolDomain.nextMyoRepStep(
                target, reps, cap, techniqueCompromised = compromised)
        assertEquals(MyoRepNextStep.PLANNED_LIMIT_REACHED, step(3, listOf(3, 3, 3), 3))
        assertEquals(MyoRepNextStep.PLANNED_LIMIT_REACHED,
            step(5, listOf(5, 5, 5, 5, 5), 5))
        assertEquals(MyoRepNextStep.INVALID_SEQUENCE, step(2, listOf(2), 5))
        assertEquals(MyoRepNextStep.INVALID_SEQUENCE,
            step(4, listOf(4, 4, 4, 4, 4, 4), 5))
        assertEquals(MyoRepNextStep.INVALID_SEQUENCE, step(4, listOf(-1), 5))
        assertEquals(MyoRepNextStep.KEEP_GOING, step(3, listOf(3, 3), 5))
        assertEquals(MyoRepNextStep.INVALID_SEQUENCE, step(4, listOf(4), 6))
    }

    @Test fun restPauseMayBeStagedButNeverSilentlyChangesWeightOrForcesExtraEfforts() {
        val policy=AdvancedAmplifierProtocolDomain
        val staged=CustomSetAmplifier(CustomAmplifierKind.REST_PAUSE,
            3,20,followUpRounds=3)
        assertTrue(policy.validStagedFollowUps(staged))
        assertTrue(policy.validStagedFollowUps(staged.copy(followUpRounds=2)))
        // Older single-visit prescriptions still decode and execute identically.
        assertTrue(policy.validStagedFollowUps(staged.copy(followUpRounds=1)))
        assertFalse(policy.validStagedFollowUps(staged.copy(followUpRounds=4)))
        assertFalse(policy.validStagedFollowUps(staged.copy(followUpReps=6)))
        assertFalse(policy.validStagedFollowUps(staged.copy(followUpRestSeconds=10)))
        assertFalse(policy.validStagedFollowUps(staged.copy(
            followUpLoad=DraftSetLoad.ExplicitBodyweight)))
        assertFalse(policy.validStagedFollowUps(
            CustomSetAmplifier(CustomAmplifierKind.DROP_SET,3,20,
                followUpRounds=2)))
        assertFalse(policy.validStagedFollowUps(
            CustomSetAmplifier(CustomAmplifierKind.CLUSTER,3,20,
                followUpRounds=2)))
        assertTrue(policy.continueRestPause(3,listOf(3),3))
        assertTrue(policy.continueRestPause(3,listOf(3,4),3))
        assertFalse(policy.continueRestPause(3,listOf(3,2),3))
        assertFalse(policy.continueRestPause(3,listOf(3,3,3),3))
        assertFalse(policy.continueRestPause(6,listOf(6),3))
    }

    @Test fun dropsRequireLoadReductionsAndClustersAreNotMyoReps() {
        val drop = AdvancedAmplifierProtocolDomain.template(CustomAmplifierKind.DROP_SET)
        val cluster = AdvancedAmplifierProtocolDomain.template(CustomAmplifierKind.CLUSTER)
        assertEquals(AmplifierLoadRule.VERIFIED_REDUCED_LOAD, drop.loadRule)
        assertEquals(1..2, drop.followUpRounds)
        assertTrue(drop.oneLine.contains("weight drops"))
        assertFalse(drop.oneLine.contains("mini-sets"))
        assertTrue(cluster.oneLine.contains("clusters"))
        assertEquals(0..15, drop.intraSetRestSeconds)
        assertEquals(AmplifierLoadRule.AUTHORED_LOAD, cluster.loadRule)
        assertTrue(cluster.techniqueCue.contains("unlike myo-reps"))
    }

    @Test fun oneTechniqueVocabularyAcrossPlanPreviewAndStagedWorkout() {
        val policy=AdvancedAmplifierProtocolDomain
        val myo=CustomSetAmplifier(CustomAmplifierKind.MYO_REPS,4,20,followUpRounds=5)
        val pause=CustomSetAmplifier(CustomAmplifierKind.REST_PAUSE,3,20,followUpRounds=3)
        assertEquals("Myo-reps · mini-set 2 of 5",policy.stageLabel(myo,2))
        assertEquals("Rest-pause · effort 2 of 3",policy.stageLabel(pause,2))
        assertEquals("Rest-pause · follow-up",policy.stageLabel(pause.copy(followUpRounds=1),1))
        assertEquals("efforts",policy.stageNoun(CustomAmplifierKind.REST_PAUSE,true))
        assertEquals("weight drops",policy.stageNoun(CustomAmplifierKind.DROP_SET,true))
        assertEquals("clusters",policy.stageNoun(CustomAmplifierKind.CLUSTER,true))
        assertTrue(policy.stageReviewCue(myo).contains("rep floor"))
        assertTrue(policy.stageReviewCue(pause).contains("rep shortfall"))
        assertFalse(policy.stageReviewCue(pause).contains("myo"))
        assertEquals("End rest-pause early?",policy.stageEarlyStopTitle(pause.kind))
        assertTrue(policy.stageEarlyStopDescription(pause.kind).contains("no later rest-pause efforts"))
    }

    @Test fun advancedTechniquesAreNotMandatoryAndNotPlacedOnEveryStrengthSet() {
        fun eligible(
            kind: CustomAmplifierKind, phase: TrainingPhase,
            experienced: Boolean = true, stable: Boolean = true,
            fatigued: Boolean = false, enabled: Boolean = true,
            used: Boolean = false
        ) = AdvancedAmplifierProtocolDomain.recommendedAsHypertrophyFinisher(
            kind, phase, experienced, stable, fatigued, enabled, used)
        assertTrue(eligible(CustomAmplifierKind.MYO_REPS, TrainingPhase.HYPERTROPHY))
        assertFalse(eligible(CustomAmplifierKind.MYO_REPS,
            TrainingPhase.HYPERTROPHY, experienced = false))
        assertFalse(eligible(CustomAmplifierKind.MYO_REPS,
            TrainingPhase.HYPERTROPHY, stable = false))
        assertFalse(eligible(CustomAmplifierKind.DROP_SET,
            TrainingPhase.HYPERTROPHY, fatigued = true))
        assertFalse(eligible(CustomAmplifierKind.MYO_REPS,
            TrainingPhase.HYPERTROPHY, enabled = false))
        assertFalse(eligible(CustomAmplifierKind.REST_PAUSE,
            TrainingPhase.HYPERTROPHY, used = true))
        assertFalse(eligible(CustomAmplifierKind.DROP_SET, TrainingPhase.STRENGTH))
        assertFalse(eligible(CustomAmplifierKind.CLUSTER, TrainingPhase.HYPERTROPHY))
    }
}
