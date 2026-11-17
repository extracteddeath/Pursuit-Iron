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
        assertTrue(plan.techniqueCue.contains("Stop"))
    }

    @Test fun underTargetEndsMyoSequenceInsteadOfForcingRemainingRounds() {
        val step = AdvancedAmplifierProtocolDomain::nextMyoRepStep
        assertEquals(MyoRepNextStep.KEEP_GOING, step(4, listOf(4, 4), 5))
        assertEquals(MyoRepNextStep.STOP_REP_DROP, step(4, listOf(4, 4, 3), 5))
        assertEquals(MyoRepNextStep.STOP_REP_DROP, step(5, listOf(5, 4), 3))
        assertEquals(MyoRepNextStep.STOP_TECHNIQUE,
            step(4, listOf(4), 5, techniqueCompromised = true))
    }

    @Test fun rangeBoundaryAndCapAreExplicit() {
        val step = AdvancedAmplifierProtocolDomain::nextMyoRepStep
        assertEquals(MyoRepNextStep.PLANNED_LIMIT_REACHED, step(3, listOf(3, 3, 3), 3))
        assertEquals(MyoRepNextStep.PLANNED_LIMIT_REACHED,
            step(5, listOf(5, 5, 5, 5, 5), 5))
        assertEquals(MyoRepNextStep.INVALID_SEQUENCE, step(2, listOf(2), 5))
        assertEquals(MyoRepNextStep.INVALID_SEQUENCE,
            step(4, listOf(4, 4, 4, 4, 4, 4), 5))
        assertEquals(MyoRepNextStep.INVALID_SEQUENCE, step(4, listOf(-1), 5))
        assertEquals(MyoRepNextStep.INVALID_SEQUENCE, step(4, listOf(4), 6))
    }

    @Test fun dropsRequireLoadReductionsAndClustersAreNotMyoReps() {
        val drop = AdvancedAmplifierProtocolDomain.template(CustomAmplifierKind.DROP_SET)
        val cluster = AdvancedAmplifierProtocolDomain.template(CustomAmplifierKind.CLUSTER)
        assertEquals(AmplifierLoadRule.VERIFIED_REDUCED_LOAD, drop.loadRule)
        assertEquals(1..2, drop.followUpRounds)
        assertEquals(0..15, drop.intraSetRestSeconds)
        assertEquals(AmplifierLoadRule.AUTHORED_LOAD, cluster.loadRule)
        assertTrue(cluster.techniqueCue.contains("unlike myo-reps"))
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
