package io.github.extracteddeath.pursuitiron.rebuild

/**
 * One understandable source of truth for OPTIONAL coaching behavior.
 *
 * These switches never disable equipment validation, the athlete's authored
 * program, exercise restrictions, journal safety, or explicit Finish/history.
 * All recommendations require separate, revocable athlete acceptance.
 */
data class CoachAssistancePolicy(
    val coachingSuggestionsEnabled:Boolean=true,
    val fatigueAdaptationEnabled:Boolean=true,
    val progressionSuggestionsEnabled:Boolean=true
) {
    val manualOnly:Boolean get()=!coachingSuggestionsEnabled &&
        !fatigueAdaptationEnabled && !progressionSuggestionsEnabled
    val offersProgression:Boolean get()=
        coachingSuggestionsEnabled && progressionSuggestionsEnabled
    val offersFatigueAdvice:Boolean get()=
        coachingSuggestionsEnabled && fatigueAdaptationEnabled

    /** When fatigue coaching is off, a bad readiness day does not rewrite
     * the target. A reported injury/movement restriction always remains a
     * safety boundary regardless of preference. */
    fun coachingReadiness(reported:CoachReadiness):CoachReadiness =
        if(offersFatigueAdvice)reported else CoachReadiness.NORMAL

    companion object {
        val MANUAL=CoachAssistancePolicy(false,false,false)
        val STANDARD=CoachAssistancePolicy()
        /** Natural double progression without fatigue-driven recommendations. */
        val PROGRESSION_ONLY=CoachAssistancePolicy(
            coachingSuggestionsEnabled=true,
            fatigueAdaptationEnabled=false,
            progressionSuggestionsEnabled=true)
    }
}
