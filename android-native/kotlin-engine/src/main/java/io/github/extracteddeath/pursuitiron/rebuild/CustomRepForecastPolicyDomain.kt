package io.github.extracteddeath.pursuitiron.rebuild

/**
 * One policy selector for next-workout target-RIR coaching.
 *
 * PROGRESSION_ONLY must remain genuine: normal earned rep suggestions
 * continue, but the optional longitudinal performance-regression/fatigue
 * warning does not run. MANUAL receives no new coaching proposals.
 * Historical receipts replay their stored policy, not a new default.
 */
object CustomRepForecastPolicyDomain {
    fun newProposal(policy:CoachAssistancePolicy):CustomRepForecastPolicy? =
        when {
            !policy.offersProgression -> null
            policy.offersFatigueAdvice ->
                CustomRepForecastPolicy.TARGET_RIR_TREND_V3
            else ->CustomRepForecastPolicy.TARGET_RIR_V2
        }

    /** The owner independently verifies the current preference before
     * calculating or accepting a stored policy-specific future receipt. */
    fun permits(
        policy:CoachAssistancePolicy,
        requested:CustomRepForecastPolicy
    ):Boolean =policy.offersProgression &&
        (requested!=CustomRepForecastPolicy.TARGET_RIR_TREND_V3 ||
            policy.offersFatigueAdvice)
}
