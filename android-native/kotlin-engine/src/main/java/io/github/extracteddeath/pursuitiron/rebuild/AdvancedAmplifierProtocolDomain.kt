package io.github.extracteddeath.pursuitiron.rebuild

/**
 * Evidence-informed technique guidance, deliberately separate from authored
 * prescriptions and factual journal receipts.
 *
 * Legacy custom journals retain their one-follow-up behavior. Only explicitly
 * authored staged myo-reps, rest-pause and clusters use separate visit IDs.
 * No unperformed stage becomes a receipt, a working set, or progression evidence.
 * Drop sets still require a separately verified reduction, not an invented load.
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
    val roundLabel: String,
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
        "${followUpRounds.first}–${followUpRounds.last} ${roundLabel} · " +
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
            roundLabel = "mini-sets",
            activationReps = 12..20,
            followUpReps = 3..5,
            followUpRounds = 3..5,
            intraSetRestSeconds = 10..30,
            loadRule = AmplifierLoadRule.SAME_LOAD,
            techniqueCue = "One hard activation set (about 1–2 RIR), then short " +
                "same-weight mini-sets. Stop when reps reach your planned floor, fall below the safe minimum, or form deteriorates.",
            selectionCue = "Best for experienced lifters on stable machine/cable " +
                "or isolation work in a hypertrophy session; avoid heavy technical lifts."
        )
        CustomAmplifierKind.DROP_SET -> AdvancedAmplifierTemplate(
            title = "Drop set",
            roundLabel = "weight drops",
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
            roundLabel = "rest-pause efforts",
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
            roundLabel = "clusters",
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

    /** One vocabulary for authoring, Preview, Focus and saved stage review.
     * These labels never imply that unperformed optional stages are records.
     * The legacy single-follow-up view stays identifiable as a follow-up. */
    fun stageNoun(kind:CustomAmplifierKind,plural:Boolean=false):String = when(kind) {
        CustomAmplifierKind.MYO_REPS -> if(plural)"mini-sets" else "mini-set"
        CustomAmplifierKind.REST_PAUSE -> if(plural)"efforts" else "effort"
        CustomAmplifierKind.DROP_SET -> if(plural)"weight drops" else "weight drop"
        CustomAmplifierKind.CLUSTER -> if(plural)"clusters" else "cluster"
    }

    fun stageLabel(amp:CustomSetAmplifier,ordinal:Int):String {
        val title=template(amp.kind).title
        if(amp.followUpRounds<=1)return "$title · follow-up"
        return "$title · ${stageNoun(amp.kind)} $ordinal of ${amp.followUpRounds}"
    }

    fun stageReviewCue(amp:CustomSetAmplifier):String =
        if(amp.followUpRounds<=1)
            "One separately logged follow-up; unperformed work is not recorded."
        else when(amp.kind) {
            CustomAmplifierKind.MYO_REPS ->
                "Each mini-set saves separately; stop at the rep floor or if form deteriorates."
            CustomAmplifierKind.REST_PAUSE ->
                "Each rest-pause effort saves separately; stop after a rep shortfall or if form deteriorates."
            CustomAmplifierKind.DROP_SET ->
                "Each weight drop must use its separately verified load and actual reps."
            CustomAmplifierKind.CLUSTER ->
                "Each planned cluster is a separately saved quality-rep observation; stop on missed or unplanned reps, or when technique declines."
        }

    fun stageEarlyStopTitle(kind:CustomAmplifierKind):String =
        "End ${template(kind).title.lowercase()} early?"

    fun stageEarlyStopDescription(kind:CustomAmplifierKind):String =
        when(kind) {
            CustomAmplifierKind.MYO_REPS ->
                "End this sequence if form or recovery is declining. This stage is saved as skipped; no later mini-sets are created."
            CustomAmplifierKind.REST_PAUSE ->
                "End this sequence when reps or technique decline. This effort is saved as skipped; no later rest-pause efforts are created."
            CustomAmplifierKind.CLUSTER ->
                "End the planned clusters if quality or technique declines. This cluster is saved as skipped; no later clusters are created."
            else ->
                "This stage is saved as skipped; unperformed stages are never recorded."
        }

    /** Both authoring entry points use one policy for OPTIONAL staged work.
     * Legacy single-follow-up recipes keep their exact old semantics. */
    fun validStagedFollowUps(amp:CustomSetAmplifier):Boolean =
        amp.followUpRounds==1 || when(amp.kind) {
            CustomAmplifierKind.MYO_REPS ->
                amp.followUpRounds in 3..5 && amp.followUpReps in 3..5 &&
                    amp.followUpRestSeconds in 10..30 && amp.followUpLoad==null
            CustomAmplifierKind.REST_PAUSE ->
                amp.followUpRounds in 2..3 && amp.followUpReps in 2..5 &&
                    amp.followUpRestSeconds in 15..30 && amp.followUpLoad==null
            CustomAmplifierKind.CLUSTER ->
                amp.followUpRounds in 2..5 && amp.followUpReps in 1..4 &&
                    amp.followUpRestSeconds in 15..45 &&
                    // This first staged-cluster contract is fixed to the
                    // athlete-authored activation load; arbitrary alternative
                    // stage weights would need an explicit stage-by-stage plan.
                    amp.followUpLoad==null
            else -> false
        }

    /** Short-rest efforts keep the exact activation load. A missed authored
     * rep target ends optional extra work, even before the planned maximum.
     * This is read-only; no recorded reps are changed or fabricated. */
    fun continueRestPause(targetReps:Int,observed:List<Int>,
        plannedLimit:Int):Boolean =
        targetReps in 2..5 && plannedLimit in 2..3 &&
            observed.isNotEmpty() && observed.size<plannedLimit &&
            observed.all{it in targetReps..100}

    /**
     * Planned clusters are quality practice, NOT a failure sequence.
     * Following the first different observed repetition count, require an
     * explicit athlete review instead of creating more optional clusters.
     * A completed observation stays factual even if it ended the protocol.
     */
    fun continueCluster(targetReps:Int,observed:List<Int>,
        plannedLimit:Int):Boolean =
        targetReps in 1..4 && plannedLimit in 2..5 &&
            observed.isNotEmpty() && observed.size<plannedLimit &&
            observed.all { it==targetReps }

    /**
     * Evaluates observed mini-set reps without inventing observations.
     * Minimum planned rounds are NOT a requirement after fatigue or a miss.
     * Continue while reps remain above the authored floor (usually 3),
     * optionally stopping on the first drop below the preferred 4–5 target.
     * A final mini-set reaching the floor ends the sequence.
     */
    fun nextMyoRepStep(
        targetReps: Int,
        observedMiniSets: List<Int>,
        plannedLimit: Int = 5,
        techniqueCompromised: Boolean = false,
        minimumAcceptableReps: Int = 3,
        stopOnTargetDrop: Boolean = false
    ): MyoRepNextStep {
        if (targetReps !in 3..5 || minimumAcceptableReps !in 3..targetReps ||
            plannedLimit !in 3..5 ||
            observedMiniSets.size > plannedLimit ||
            observedMiniSets.any { it !in 0..100 })
            return MyoRepNextStep.INVALID_SEQUENCE
        if (techniqueCompromised) return MyoRepNextStep.STOP_TECHNIQUE
        if (observedMiniSets.any { it < minimumAcceptableReps } ||
            stopOnTargetDrop && observedMiniSets.any { it < targetReps } ||
            targetReps > minimumAcceptableReps &&
            observedMiniSets.lastOrNull() == minimumAcceptableReps)
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
