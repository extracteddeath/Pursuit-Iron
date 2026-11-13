package io.github.extracteddeath.pursuitiron.rebuild

import org.junit.Assert.*
import org.junit.Test

/**
 * Regression contract for the existing Kotlin coaching policy.
 * Disabled optional advice must not be resurrected by another enabled flag.
 * Does not bypass mandatory injury/equipment/journal safety.
 */
class CoachAssistancePolicyMatrixTest {
    @Test fun everyCombinationHasIndependentAndDeterministicOffSwitches() {
        for (coaching in listOf(false, true)) {
            for (fatigue in listOf(false, true)) {
                for (progression in listOf(false, true)) {
                    val policy = CoachAssistancePolicy(
                        coachingSuggestionsEnabled = coaching,
                        fatigueAdaptationEnabled = fatigue,
                        progressionSuggestionsEnabled = progression
                    )
                    assertEquals(coaching && fatigue, policy.offersFatigueAdvice)
                    assertEquals(coaching && progression, policy.offersProgression)
                    assertEquals(!coaching && !fatigue && !progression, policy.manualOnly)
                    assertEquals(CoachReadiness.NORMAL,
                        policy.coachingReadiness(CoachReadiness.NORMAL))
                    assertEquals(policy, policy.copy())
                }
            }
        }
    }

    @Test fun globalCoachingOffDominatesIndividualEnabledFlags() {
        val policy = CoachAssistancePolicy(
            coachingSuggestionsEnabled = false,
            fatigueAdaptationEnabled = true,
            progressionSuggestionsEnabled = true
        )
        assertFalse(policy.offersFatigueAdvice)
        assertFalse(policy.offersProgression)
        assertFalse(policy.manualOnly) // The underlying preferences remain intact.
    }

    @Test fun progressionOnlyKeepsFatigueInterventionOff() {
        val policy = CoachAssistancePolicy.PROGRESSION_ONLY
        assertTrue(policy.offersProgression)
        assertFalse(policy.offersFatigueAdvice)
        assertFalse(policy.manualOnly)
    }

    @Test fun manualModeCannotOfferEitherOptionalAdviceType() {
        val policy = CoachAssistancePolicy.MANUAL
        assertTrue(policy.manualOnly)
        assertFalse(policy.offersProgression)
        assertFalse(policy.offersFatigueAdvice)
    }
}
