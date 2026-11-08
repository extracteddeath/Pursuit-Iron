package io.github.extracteddeath.pursuitiron.rebuild

import org.junit.Assert.*
import org.junit.Test

class CustomRepForecastPolicyDomainTest {
    @Test fun manualModeHasNoNewCoachingProposals() {
        assertNull(CustomRepForecastPolicyDomain.newProposal(
            CoachAssistancePolicy.MANUAL))
        assertFalse(CustomRepForecastPolicyDomain.permits(
            CoachAssistancePolicy.MANUAL,
            CustomRepForecastPolicy.TARGET_RIR_V2))
        assertFalse(CustomRepForecastPolicyDomain.permits(
            CoachAssistancePolicy.MANUAL,
            CustomRepForecastPolicy.TARGET_RIR_TREND_V3))
    }

    @Test fun progressionOnlyRetainsAllEarnedRepsButNeverInvokesFatigueV3() {
        val settings=CoachAssistancePolicy.PROGRESSION_ONLY
        assertEquals(CustomRepForecastPolicy.TARGET_RIR_V2,
            CustomRepForecastPolicyDomain.newProposal(settings))
        assertTrue(CustomRepForecastPolicyDomain.permits(
            settings,CustomRepForecastPolicy.TARGET_RIR_V2))
        assertTrue(CustomRepForecastPolicyDomain.permits(
            settings,CustomRepForecastPolicy.LEGACY_STEP_V1))
        assertFalse(CustomRepForecastPolicyDomain.permits(
            settings,CustomRepForecastPolicy.TARGET_RIR_TREND_V3))
        assertEquals(CoachReadiness.NORMAL,
            settings.coachingReadiness(CoachReadiness.LOW))
    }

    @Test fun standardOptInAllowsV3AndTurningFatigueOffRevokesV3() {
        val standard=CoachAssistancePolicy.STANDARD
        assertEquals(CustomRepForecastPolicy.TARGET_RIR_TREND_V3,
            CustomRepForecastPolicyDomain.newProposal(standard))
        assertTrue(CustomRepForecastPolicyDomain.permits(
            standard,CustomRepForecastPolicy.TARGET_RIR_TREND_V3))
        assertEquals(CoachReadiness.LOW,
            standard.coachingReadiness(CoachReadiness.LOW))
        val off=standard.copy(fatigueAdaptationEnabled=false)
        assertEquals(CustomRepForecastPolicy.TARGET_RIR_V2,
            CustomRepForecastPolicyDomain.newProposal(off))
        assertFalse(CustomRepForecastPolicyDomain.permits(
            off,CustomRepForecastPolicy.TARGET_RIR_TREND_V3))
    }
}
