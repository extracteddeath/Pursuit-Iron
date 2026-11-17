package io.github.extracteddeath.pursuitiron.rebuild

/**
 * Evidence-informed technique guidance, deliberately separate from authored
 * prescriptions and factual journal receipts.
 *
 * IMPORTANT: the historical custom journal stores one FOLLOW_UP visit for an
 * amplifier. A multi-mini-set template must NEVER be passed off as multiple
 * logged observations, counted toward working-set progression, or used to
 * silently rewrite a saved workout. Sequence issuance requires a versioned
 * per-stage journal/schema migration and its own acceptance tests.
 *
 * References and product decisions: android-native/docs/AMPLIFIER_PROTOCOL.md
 */
enum class AmplifierLoadRule {
    SAME_LOAD,
    VERIFIED_REDUCED_LOAD,
    AUTHORED_LOAD
}

data class AdvancedAmplifierTemplate(
    val title: String,
    val activationReps: IntRange,
    val followUpReps: IntRange,
    val followUpRounds: IntRange,
    val intraSetRestSeconds: IntRange,
    val loadRule: AmplifierLoadRule,
    val techniqueCue: String,
    val selectionCue: String
) {
    // A protocol description is NOT an issued workout or a logged sequence.
    val oneLine: String get() =
        "${followUpRounds.first}–${followUpRounds.last} mini-sets · " +
            "${followUpReps.first}–${followUpReps.last} reps · " +
            "${intraSetRestSeconds.first}–${intraSetRestSeconds.last}s between"
}

enum class MyoRepNextStep {
    KEEP_GOING,
    PLANNED_LIMIT_REACHED,
    STOP_REP_DROP,
    STOP_TECHNIQUE,
    INVALID_SEQUENCE
}

object AdvancedAmplifierProtocolDomain {
    fun template(kind: CustomAmplifierKind): AdvancedAmplifierTemplate = when (kind) {
        CustomAmplifierKind.MYO_REPS -> AdvancedAmplifierTemplate(
            title = "Myo-reps",
            activationReps = 12..20,
            followUpReps = 3..5,
            followUpRounds = 3..5,
            intraSetRestSeconds = 10..30,
            loadRule = AmplifierLoadRule.SAME_LOAD,
            techniqueCue = "One hard activation set (about 1–2 RIR), then short " +
                "same-weight mini-sets. Stop on a missed target or deteriorating form.",
            selectionCue = "Best for experienced lifters on stable machine/cable " +
                "or isolation work in a hypertrophy session; avoid heavy technical lifts."
        )
        CustomAmplifierKind.DROP_SET -> AdvancedAmplifierTemplate(
            title = "Drop set",
            activationReps = 8..20,
            followUpReps = 6..15,
            followUpRounds = 1..2,
            intraSetRestSeconds = 0..15,
            loadRule = AmplifierLoadRule.VERIFIED_REDUCED_LOAD,
            techniqueCue = "After a hard set, reduce the load by roughly 15–25% " +
                "per drop, with minimal rest. Each reduction needs its own actual load and reps.",
            selectionCue = "Optional final-set time-saver for stable hypertrophy accessories; " +
                "not a replacement for quality heavy strength work."
        )
        CustomAmplifierKind.REST_PAUSE -> AdvancedAmplifierTemplate(
            title = "Rest-pause",
            activationReps = 8..20,
            followUpReps = 2..5,
            followUpRounds = 1..3,
            intraSetRestSeconds = 15..30,
            loadRule = AmplifierLoadRule.SAME_LOAD,
            techniqueCue = "Use the same load for brief additional efforts after short " +
                "rests. Stop when the prescribed reps or safe technique cannot be maintained.",
            selectionCue = "Use sparingly for stable accessories, not automatically on every lift."
        )
        CustomAmplifierKind.CLUSTER -> AdvancedAmplifierTemplate(
            title = "Cluster sets",
            activationReps = 2..6,
            followUpReps = 1..4,
            followUpRounds = 2..5,
            intraSetRestSeconds = 15..45,
            loadRule = AmplifierLoadRule.AUTHORED_LOAD,
            techniqueCue = "Planned small groups of quality reps separated by short rests; " +
                "unlike myo-reps, do not turn the clusters into repeated failure attempts.",
            selectionCue = "A separately planned quality or strength method, not a " +
                "default hypertrophy finisher."
        )
    }

    /**
     * Evaluates observed mini-set reps without inventing observations.
     * Minimum planned rounds are NOT a requirement after fatigue or a miss.
     * The targeted 3–5 reps must be maintained to continue.
     */
    fun nextMyoRepStep(
        targetReps: Int,
        observedMiniSets: List<Int>,
        plannedLimit: Int = 5,
        techniqueCompromised: Boolean = false
    ): MyoRepNextStep {
        if (targetReps !in 3..5 || plannedLimit !in 3..5 ||
            observedMiniSets.size > plannedLimit ||
            observedMiniSets.any { it !in 0..100 })
            return MyoRepNextStep.INVALID_SEQUENCE
        if (techniqueCompromised) return MyoRepNextStep.STOP_TECHNIQUE
        if (observedMiniSets.any { it < targetReps })
            return MyoRepNextStep.STOP_REP_DROP
        if (observedMiniSets.size == plannedLimit)
            return MyoRepNextStep.PLANNED_LIMIT_REACHED
        return MyoRepNextStep.KEEP_GOING
    }

    /**
     * Conservative automatic-placement gate. Athlete-authored techniques
     * remain valid independent choices; this gate never edits their work.
     */
    fun recommendedAsHypertrophyFinisher(
        kind: CustomAmplifierKind,
        phase: TrainingPhase,
        experienced: Boolean,
        stableAccessory: Boolean,
        highFatigue: Boolean,
        athleteEnabled: Boolean,
        alreadyUsedThisSession: Boolean
    ): Boolean = athleteEnabled && experienced && stableAccessory &&
        !highFatigue && !alreadyUsedThisSession &&
        phase == TrainingPhase.HYPERTROPHY &&
        kind in setOf(CustomAmplifierKind.MYO_REPS,
            CustomAmplifierKind.DROP_SET, CustomAmplifierKind.REST_PAUSE)
}
