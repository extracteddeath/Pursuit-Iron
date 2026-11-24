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
        if(roundLabel=="lengthened partials")
            "Optional final-set extension · ${followUpReps.first}–${followUpReps.last} "+
                "partial reps · same load · no rest"
        else "${followUpRounds.first}–${followUpRounds.last} ${roundLabel} · " +
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

/**
 * A single engine-owned source for the initial *editable* follow-up load.
 * The original prescribed target and the athlete's performed set are distinct.
 * No editor, generated cycle or future progression may treat an older first
 * working set or a session-history button as the activation weight.
 */
sealed interface AmplifierFollowUpEntryLoad {
    data class RecordedActivation(val performed:KotlinPerformedSet):
        AmplifierFollowUpEntryLoad
    data class AuthoredStage(val target:DraftSetLoad):
        AmplifierFollowUpEntryLoad
    /** No guessed load, no opposite-machine fallback. */
    data object ActivationUnavailable:AmplifierFollowUpEntryLoad
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
        CustomAmplifierKind.LENGTHENED_PARTIALS -> AdvancedAmplifierTemplate(
            title = "Lengthened partials",
            roundLabel = "lengthened partials",
            activationReps = 6..20,
            followUpReps = 1..8,
            followUpRounds = 1..1,
            intraSetRestSeconds = 0..0,
            loadRule = AmplifierLoadRule.SAME_LOAD,
            techniqueCue = "After completing the final full-range working set, " +
                "optionally log 1–8 controlled partial repetitions in the " +
                "lengthened portion of the range using the SAME load and " +
                "physical station. Stop when position or technique degrades; " +
                "partials are not full-range repetitions.",
            selectionCue = "Opt-in for experienced lifters on stable, " +
                "appropriate accessories; not automatic, and not required " +
                "on heavy technical lifts or when fatigue/injury argues against it."
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
        CustomAmplifierKind.LENGTHENED_PARTIALS ->
            if(plural)"partial reps" else "partial rep"
    }

    fun stageLabel(amp:CustomSetAmplifier,ordinal:Int):String {
        val title=template(amp.kind).title
        if(amp.kind==CustomAmplifierKind.LENGTHENED_PARTIALS)
            return "Lengthened partials · final-set extension"
        if(amp.followUpRounds<=1)return "$title · follow-up"
        return "$title · ${stageNoun(amp.kind)} $ordinal of ${amp.followUpRounds}"
    }

    fun stageReviewCue(amp:CustomSetAmplifier):String =
        if(amp.kind==CustomAmplifierKind.LENGTHENED_PARTIALS)
            "Final working set only · partial reps logged separately at the activation load; never counted as full-range reps or a new regular set."
        else if(amp.followUpRounds<=1)
            "One separately logged follow-up; unperformed work is not recorded."
        else when(amp.kind) {
            CustomAmplifierKind.MYO_REPS ->
                "Each mini-set saves separately; stop at the rep floor or if form deteriorates."
            CustomAmplifierKind.REST_PAUSE ->
                "Each rest-pause effort saves separately; stop after a rep shortfall or if form deteriorates."
            CustomAmplifierKind.DROP_SET ->
                "Each weight drop has its own independently verified lower load, physical station and separately logged actual reps."
            CustomAmplifierKind.CLUSTER ->
                "Each planned cluster is a separately saved quality-rep observation; stop on missed or unplanned reps, or when technique declines."
            CustomAmplifierKind.LENGTHENED_PARTIALS ->
                "Log partial repetitions separately on the final working set; never turn them into full-range reps."
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
            CustomAmplifierKind.LENGTHENED_PARTIALS ->
                "Skip the optional final-set partial extension if technique or joint comfort is poor. No partial reps are invented."
            else ->
                "This stage is saved as skipped; unperformed stages are never recorded."
        }

    /** The durable protocol specifies one independently reviewed physical
     * load per drop. Never clone first-drop weight into a second drop. */
    fun dropStageLoad(amp:CustomSetAmplifier,stage:Int):DraftSetLoad? =
        if(amp.kind!=CustomAmplifierKind.DROP_SET)return null
        else when(stage) {
            1 -> amp.followUpLoad
            2 -> if(amp.followUpRounds==2)amp.secondDropLoad else null
            else -> null
        }

    fun validDropSequence(main:DraftSetLoad,amp:CustomSetAmplifier):Boolean {
        if(amp.kind!=CustomAmplifierKind.DROP_SET ||
            amp.followUpRounds !in 1..2)return false
        val before=main as? DraftSetLoad.Verified ?:return false
        val first=amp.followUpLoad as? DraftSetLoad.Verified ?:return false
        // This protocol owns descending external-resistance settings.
        // Lowering assistance makes the exercise harder, not easier.
        // Assistance needs a separately reviewed reverse-direction protocol.
        if(!before.basis.higherSettingIncreasesDifficulty)return false
        if(before.unit!=first.unit || before.basis!=first.basis ||
            !KotlinSetNumberAuthorityDomain.validPrescribedLoad(
                before.value,before.basis) ||
            !KotlinSetNumberAuthorityDomain.validPrescribedLoad(
                first.value,first.basis) ||
            first.value.signum()<=0 || first.value>=before.value)return false
        if(amp.followUpRounds==1)return amp.secondDropLoad==null
        val second=amp.secondDropLoad as? DraftSetLoad.Verified ?:return false
        return first.unit==second.unit && first.basis==second.basis &&
            KotlinSetNumberAuthorityDomain.validPrescribedLoad(
                second.value,second.basis) &&
            second.value.signum()>0 && second.value<first.value
    }

    /** A valid authored drop can end at activation when the actual load is too low. */
    internal fun firstDropSuppressed(
        amp:CustomSetAmplifier, issued:DraftSetLoad, activation:KotlinPerformedSet?
    ):Boolean = validDropSequence(issued,amp) && activation!=null &&
        KotlinSetNumberAuthorityDomain.valid(issued,KotlinJournalSetOutcome.COMPLETED,activation) &&
        eligiblePerformedDropTarget(amp,issued,activation,null,activation.machineInstanceId,1)==null

    /**
     * The one source of truth for whether a physical drop stage may be
     * offered. An issued reduction is not evidence of an actual one:
     * always compare against the LAST physically performed stage, and
     * require the original activation's station throughout.
     *
     * Stage two is not inferred from the activation: it is only available
     * after its first drop was completed at the authored stage-one load.
     */
    private fun eligiblePerformedDropTarget(
        amp:CustomSetAmplifier,
        issuedActivation:DraftSetLoad,
        performedActivation:KotlinPerformedSet?,
        precedingDropPerformed:KotlinPerformedSet?,
        selectedMachineId:String?,
        stage:Int
    ):DraftSetLoad.Verified? {
        if(!validDropSequence(issuedActivation,amp) ||
            stage !in 1..amp.followUpRounds)return null
        val target=dropStageLoad(amp,stage)
            as? DraftSetLoad.Verified ?:return null
        val activation=performedActivation ?:return null
        val previous=if(stage==1)activation else precedingDropPerformed
            ?:return null
        val firstTarget=dropStageLoad(amp,1)
            as? DraftSetLoad.Verified ?:return null
        if(!KotlinSetNumberAuthorityDomain.valid(
                issuedActivation,KotlinJournalSetOutcome.COMPLETED,activation) ||
            (stage==2 && !KotlinSetNumberAuthorityDomain.valid(
                firstTarget,KotlinJournalSetOutcome.COMPLETED,previous)) ||
            activation.unit!=target.unit || activation.load==null ||
            activation.load.compareTo(firstTarget.value)<=0 ||
            activation.machineInstanceId!=selectedMachineId ||
            previous.reps<=0 || previous.unit!=target.unit ||
            previous.machineInstanceId!=activation.machineInstanceId ||
            previous.load==null ||
            previous.load.compareTo(target.value)<=0)
            return null
        if(stage==2 && previous.load.compareTo(firstTarget.value)!=0)
            return null
        return target
    }

    /**
     * One physical-drop observation policy for both successful reductions
     * and FAILED attempts. A zero-rep attempt is still a real attempted load
     * and must be lower than the immediately preceding completed stage.
     * A different unit/station or a stale first-drop anchor is not a drop.
     * A SKIP has no attempted physical weight and is handled separately.
     *
     * This is shared by active workout, Focus validation and saved History:
     * no caller may treat an issued lower target as evidence that an athlete
     * actually attempted a reduction.
     */
    fun validDropStageObservation(
        amp:CustomSetAmplifier,
        issuedActivation:DraftSetLoad,
        performedActivation:KotlinPerformedSet?,
        precedingDropPerformed:KotlinPerformedSet?,
        observedStage:KotlinPerformedSet?,
        stage:Int,
        outcome:KotlinJournalSetOutcome
    ):Boolean {
        if(outcome==KotlinJournalSetOutcome.SKIPPED)return false
        val target=eligiblePerformedDropTarget(
            amp,issuedActivation,performedActivation,
            precedingDropPerformed,performedActivation?.machineInstanceId,
            stage) ?:return false
        val previous=if(stage==1)performedActivation else precedingDropPerformed
        val precedingLoad=previous?.load ?:return false
        val actual=observedStage ?:return false
        val attemptedLoad=actual.load ?:return false
        return KotlinSetNumberAuthorityDomain.valid(target,outcome,actual) &&
            actual.unit==target.unit &&
            actual.machineInstanceId==performedActivation?.machineInstanceId &&
            attemptedLoad.signum()>0 &&
            attemptedLoad.compareTo(precedingLoad)<0
    }

    /** Compatibility entry for callers checking a COMPLETED drop only. */
    fun validPerformedDropStage(
        amp:CustomSetAmplifier,
        issuedActivation:DraftSetLoad,
        performedActivation:KotlinPerformedSet?,
        precedingDropPerformed:KotlinPerformedSet?,
        completed:KotlinPerformedSet?,
        stage:Int
    ):Boolean = validDropStageObservation(
        amp,issuedActivation,performedActivation,precedingDropPerformed,
        completed,stage,KotlinJournalSetOutcome.COMPLETED)

    /**
     * Only an explicit drop set owns an independently authored follow-up
     * load. A same-load technique has ONE issued target and ONE performed
     * activation anchor; it must never acquire a separate stage weight.
     */
    fun followUpLoadForStage(amp:CustomSetAmplifier,
        main:DraftSetLoad,stage:Int):DraftSetLoad =
        if(amp.kind==CustomAmplifierKind.DROP_SET)
            dropStageLoad(amp,stage) ?:main
        else main

    /** Applies equally to single-stage and staged versions of a technique. */
    fun requiresActivationLoad(kind:CustomAmplifierKind):Boolean =
        kind in setOf(CustomAmplifierKind.MYO_REPS,
            CustomAmplifierKind.REST_PAUSE,CustomAmplifierKind.CLUSTER,
            CustomAmplifierKind.LENGTHENED_PARTIALS)

    /**
     * Validation for every durable same-load follow-up and history correction.
     * Comparing physical BigDecimal values rather than display strings avoids
     * false mismatches (47.50 == 47.5). Station identity and unit are exact.
     * A manual load change is a different actual technique, not a myo-rep.
     * Do not silently recategorize it or rewrite the recorded weight.
     */
    fun validSameLoadFollowUp(
        amp:CustomSetAmplifier,
        issuedActivation:DraftSetLoad,
        performedActivation:KotlinPerformedSet?,
        followUp:KotlinPerformedSet?,
        outcome:KotlinJournalSetOutcome
    ):Boolean {
        if(!requiresActivationLoad(amp.kind) ||
            !validStagedFollowUps(amp) ||
            outcome==KotlinJournalSetOutcome.SKIPPED)return false
        val activation=performedActivation ?:return false
        val observed=followUp ?:return false
        return KotlinSetNumberAuthorityDomain.valid(
                issuedActivation,KotlinJournalSetOutcome.COMPLETED,activation) &&
            KotlinSetNumberAuthorityDomain.valid(
                issuedActivation,outcome,observed) &&
            CustomWorkoutExecutionDomain.sameActualLoadAndStation(
                activation,observed)
    }

    /**
     * The entry field for every new same-load myo / rest-pause / cluster /
     * partial extension belongs to the COMPLETED activation set to which
     * that amplifier is attached: not the first working set, issued load,
     * other exercise, another physical machine or previous session.
     *
     * Same-load protocols cannot carry independent authored follow-up
     * weights, even in their single-stage form. Only drop sets have distinct
     * authored loads, which must be verified against completed actuals.
     * This is presentation input only, never a change to issued targets,
     * performed receipts or the next session's suggestion.
     */
    fun resolveFollowUpEntryLoad(
        amp:CustomSetAmplifier,
        issuedActivation:DraftSetLoad,
        performedActivation:KotlinPerformedSet?,
        selectedMachineId:String?,
        stage:Int,
        /** For the second drop this is the FIRST PERFORMED drop, never
         * another copy of the activation or a guessed authored weight. */
        precedingDropPerformed:KotlinPerformedSet?=null
    ):AmplifierFollowUpEntryLoad {
        // An authored target is not evidence that it is a real *drop*
        // from the completed activation. This applies to legacy single-drop
        // and newer two-drop recipes identically, including Focus prefill.
        if(stage !in 1..amp.followUpRounds || !validStagedFollowUps(amp))
            return AmplifierFollowUpEntryLoad.ActivationUnavailable
        if(amp.kind==CustomAmplifierKind.DROP_SET) {
            val target=eligiblePerformedDropTarget(
                amp,issuedActivation,performedActivation,
                precedingDropPerformed,selectedMachineId,stage)
                ?:return AmplifierFollowUpEntryLoad.ActivationUnavailable
            return AmplifierFollowUpEntryLoad.AuthoredStage(target)
        }
        if(!requiresActivationLoad(amp.kind))
            return AmplifierFollowUpEntryLoad.ActivationUnavailable
        val actual=performedActivation
            ?:return AmplifierFollowUpEntryLoad.ActivationUnavailable
        if(actual.machineInstanceId!=selectedMachineId ||
            !KotlinSetNumberAuthorityDomain.valid(
                issuedActivation,KotlinJournalSetOutcome.COMPLETED,actual))
            return AmplifierFollowUpEntryLoad.ActivationUnavailable
        return AmplifierFollowUpEntryLoad.RecordedActivation(actual)
    }

    /** Both authoring entry points use one policy for OPTIONAL staged work.
     * Legacy single-follow-up recipes keep their exact old semantics. */
    fun validStagedFollowUps(amp:CustomSetAmplifier):Boolean =
        if(amp.kind==CustomAmplifierKind.DROP_SET)
            amp.followUpRounds in 1..2 &&
                (amp.followUpRounds==1 && amp.secondDropLoad==null ||
                 amp.followUpRounds==2 && amp.followUpLoad!=null &&
                    amp.secondDropLoad!=null &&
                    amp.followUpReps in 6..15 &&
                    amp.followUpRestSeconds in 0..15)
        else if(amp.followUpLoad!=null || amp.secondDropLoad!=null)false
        else if(amp.kind==CustomAmplifierKind.LENGTHENED_PARTIALS)
            amp.followUpRounds==1 && amp.followUpReps in 1..8 &&
                amp.followUpRestSeconds==0 && amp.followUpLoad==null
        else amp.followUpRounds==1 || when(amp.kind) {
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
