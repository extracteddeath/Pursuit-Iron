package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import java.util.Collections

/**
 * First-program orchestration, not a second composer or workout issuer.
 *
 * A new athlete can supply ordinary setup and one-time starting loads instead
 * of authoring MEV/MAV/MRV tables, day purposes and exercise visit policies.
 * All rules below are VERSIONED, deliberately provisional starting assumptions
 * to be adapted from real performance. The app must show the assumptions.
 *
 * In particular: a verified rack of dumbbells proves 30 lb EXISTS, not that a
 * specific lifter can complete 30 lb for 12 reps. No load is selected from
 * stock unless the athlete explicitly calibrated that exact movement.
 */
data class FirstProgramStartRequest(
    val setup:AthleteSetupReview,
    val catalog:List<CatalogMovement>,
    /** Only exercises the athlete chose and calibrated are selected. */
    val startingLoads:Map<String,BigDecimal>,
    /** Bodyweight availability is not proof the athlete can perform the lift. */
    val selectedBodyweightExerciseIds:Set<String>,
    val phase:TrainingPhase,
    /** Explicit extra split day, e.g. a 5-day Upper/Lower schedule. */
    val extraFocuses:List<ComposerFocus> = emptyList(),
    /** Planning assumption. Editable, not inferred from the time bucket. */
    val nonSetSecondsPerDay:Int = 900
)
enum class FirstProgramStartStatus {
    SETUP_REQUIRED, CALIBRATION_REQUIRED, INVALID_CALIBRATION,
    DRAFT_NEEDS_REVIEW, PRESCRIBED_DRAFT_FOR_REVIEW
}
data class FirstProgramMovementOption(
    val exerciseId:String,
    val needsWeightCalibration:Boolean,
    val unit:LoadUnit,
    val basis:LoadBasis?
)
data class FirstProgramStartReview(
    val status:FirstProgramStartStatus,
    val eligibleMovements:List<FirstProgramMovementOption>,
    val chosenExerciseIds:List<String>,
    val unsupportedMuscles:Set<TrainingMuscle>,
    val issues:List<String>,
    val policy:AuthoredGoalProgramPolicy?,
    val generation:AthleteProgramGenerationReview?,
    val prescription:SessionPrescriptionDraft?
) {
    /** Deliberately NOT an approval or a usable athlete-specific weight model. */
    val maySaveProgram:Boolean get()=false
    val mayIssueWorkout:Boolean get()=false
}

object CoachFirstProgramStartDomain {
    const val POLICY_VERSION="first-program-reference-v1"
    private val zero=BigDecimal.ZERO
    private fun num(v:Int)=BigDecimal(v)
    private fun freeze(items:List<String>)=Collections.unmodifiableList(items)
    private fun stable(v:BigDecimal)=BigDecimal(v.stripTrailingZeros().toPlainString())

    /**
     * Reference planning bands are NOT empirically established personal
     * minimum effective/adaptive/maximum recoverable volume. The downstream
     * weekly dose audit treats them as declared provisional planning bounds,
     * and longitudinal reviews must replace them as evidence accumulates.
     */
    private fun startingBounds(
        athlete:ReviewedAthleteSetup,
        selected:List<CatalogMovement>
    ):Pair<List<DeclaredMuscleDoseBounds>,List<DeclaredMuscleDoseBounds>> {
        val directlySupported=selected.flatMap {movement ->
            movement.credits.filter {it.role==MuscleCreditRole.DIRECT}
                .map {it.muscle}
        }.toSet()
        val initial=when(athlete.experience) {
            AthleteExperience.BEGINNER->2
            AthleteExperience.INTERMEDIATE->3
            AthleteExperience.ADVANCED->4
        }
        val baseline=TrainingMuscle.entries.map {muscle ->
            val amount=if(muscle in directlySupported)initial else 0
            DeclaredMuscleDoseBounds(muscle,num(amount),num(amount*2),
                num(amount*3),if(amount>0)1 else 0)
        }
        val selectedBounds=baseline.map {b ->
            when(b.muscle) {
                in athlete.focusedMuscles ->b.copy(
                    mev=b.mev+BigDecimal.ONE,
                    mav=b.mav+BigDecimal.ONE,
                    mrv=b.mrv+BigDecimal.ONE,
                    minimumDirectDays=if(b.minimumDirectDays>0 &&
                        athlete.trainingWeekdays.size>=4)2 else b.minimumDirectDays)
                in athlete.reducedMuscles ->b.copy(
                    mev=(b.mev-BigDecimal.ONE).max(zero),
                    mav=(b.mav-BigDecimal.ONE).max(zero),
                    mrv=(b.mrv-BigDecimal.ONE).max(zero),
                    minimumDirectDays=if(b.minimumDirectDays>0)1 else 0)
                else ->b
            }
        }
        return baseline to selectedBounds
    }

    fun review(input:FirstProgramStartRequest):FirstProgramStartReview {
        val issues=ArrayList<String>()
        val eligible=ArrayList<FirstProgramMovementOption>()
        val selected=ArrayList<CatalogMovement>()
        val choices=ArrayList<CatalogExerciseChoice>()
        fun output(
            status:FirstProgramStartStatus,
            policy:AuthoredGoalProgramPolicy?=null,
            generated:AthleteProgramGenerationReview?=null,
            prescription:SessionPrescriptionDraft?=null,
            gaps:Set<TrainingMuscle> =emptySet()
        )=FirstProgramStartReview(status,
            Collections.unmodifiableList(eligible.toList()),
            Collections.unmodifiableList(selected.map{it.exerciseId}),
            Collections.unmodifiableSet(gaps.toSet()),
            Collections.unmodifiableList(issues.toList()),
            policy,generated,prescription)
        val athlete=input.setup.reviewed
        if(!input.setup.mayReviewProgramCandidate || athlete==null) {
            issues+="Confirm your profile, schedule and actual gym first."
            return output(FirstProgramStartStatus.SETUP_REQUIRED)
        }
        if(input.nonSetSecondsPerDay !in 0..21600 ||
            input.startingLoads.size>32 ||
            input.selectedBodyweightExerciseIds.size>32 ||
            input.startingLoads.keys.intersect(
                input.selectedBodyweightExerciseIds).isNotEmpty() ||
            input.catalog.size !in 1..256 ||
            input.catalog.map{it.exerciseId}.distinct().size!=input.catalog.size) {
            issues+="Starting time, exercise choices or calibration inputs are invalid."
            return output(FirstProgramStartStatus.INVALID_CALIBRATION)
        }

        // Filter by recorded physical facilities, implement identity, unit,
        // bodyweight consent and explicit exercise exclusions. Do not choose
        // an invented stock item or infer ability from the lightest plate.
        val available=LinkedHashMap<String,CatalogMovement>()
        for(spec in input.catalog) {
            if(spec.exerciseId in athlete.excludedExerciseIds ||
                spec.creditStatus!=CatalogCreditStatus.AUTHOR_REVIEWED_PROVISIONAL ||
                spec.creditReviewReference.isNullOrBlank() ||
                !athlete.gym.availableFacilities.containsAll(spec.requiredFacilityIds) ||
                (spec.attachmentId!=null &&
                    spec.attachmentId !in athlete.gym.availableAttachments))
                continue
            val usable=if(spec.equipmentId==null)
                spec.expectedLoadBasis==null && athlete.allowBodyweight
            else {
                val stock=athlete.gym.confirmedStock[spec.equipmentId]
                stock!=null && stock.unit==athlete.gym.unit &&
                    stock.basis==spec.expectedLoadBasis &&
                    !(stock.basis==LoadBasis.BARBELL_TOTAL &&
                        athlete.maxBarbellExercisesPerDay==0)
            }
            if(!usable)continue
            available[spec.exerciseId]=spec
            eligible+=FirstProgramMovementOption(spec.exerciseId,
                spec.equipmentId!=null,athlete.gym.unit,spec.expectedLoadBasis)
        }
        val requested=(input.startingLoads.keys+
            input.selectedBodyweightExerciseIds).toSet()
        if(requested.any {it !in available}) {
            issues+="A chosen movement is excluded, unreviewed or not available in your confirmed gym."
            return output(FirstProgramStartStatus.INVALID_CALIBRATION)
        }
        if(requested.isEmpty()) {
            issues+="Choose the movements you intend to train and confirm their first working loads. A gym inventory alone cannot establish personal strength."
            return output(FirstProgramStartStatus.CALIBRATION_REQUIRED)
        }
        for(id in requested.sorted()) {
            val movement=checkNotNull(available[id])
            val chosenLoad=input.startingLoads[id]
            if(movement.equipmentId==null) {
                if(chosenLoad!=null) {
                    issues+="A bodyweight movement cannot receive an invented implement load."
                    return output(FirstProgramStartStatus.INVALID_CALIBRATION)
                }
            } else {
                val stock=athlete.gym.confirmedStock[movement.equipmentId]
                val checked=chosenLoad?.let {
                    EquipmentLoadDomain.inspect(stock,it,athlete.gym.unit)
                }
                if(chosenLoad==null || chosenLoad<=zero ||
                    checked !is LoadInspection.Confirmed || !checked.exact) {
                    issues+="The selected starting load for ${id} is not an exact, confirmed physical weight. Review the actual equipment; nothing was rounded."
                    return output(FirstProgramStartStatus.INVALID_CALIBRATION)
                }
            }
            selected+=movement
            val primary=movement.role==ComposerRole.PRIMARY
            val rest=when(movement.role) {
                ComposerRole.PRIMARY->150
                ComposerRole.SECONDARY->120
                ComposerRole.ACCESSORY->75
            }
            choices+=CatalogExerciseChoice(
                exerciseId=id,selectedLoad=chosenLoad?.let(::stable),
                activeSecondsPerSet=55,restSecondsPerSet=rest,
                minimumSetsPerVisit=2,maximumSetsPerVisit=4,
                maximumVisits=if(athlete.split==ComposerSplit.FULL_BODY)
                    athlete.trainingWeekdays.size else 2,
                requiredVisits=0,minimumCalendarDayGap=1,
                priority=if(primary)8 else if(movement.role==ComposerRole.SECONDARY)6 else 4,
                recoveryGroup=movement.recoveryGroupId,
                minimumGroupCalendarDayGap=movement.minimumRecoveryGroupDayGap)
        }
        val supported=selected.flatMap {it.credits.filter {c->
            c.role==MuscleCreditRole.DIRECT
        }.map{c->c.muscle}}.toSet()
        val gaps=TrainingMuscle.entries.filter {it !in supported}.toSet()
        if(gaps.isNotEmpty())issues+=
            "The starter selection lacks direct exercise coverage for ${gaps.size} tracked muscles. These are not secretly counted as fully trained."

        val (baseline,bounds)=startingBounds(athlete,selected)
        // The day roles are transparent defaults to be revised after actual
        // training. Strength skill days require a real verified primary lift.
        val purposes=when(input.phase) {
            TrainingPhase.HYPERTROPHY ->
                mapOf(ComposerSessionPurpose.HYPERTROPHY to athlete.trainingWeekdays.size)
            TrainingPhase.STRENGTH,TrainingPhase.PEAK ->
                mapOf(ComposerSessionPurpose.STRENGTH_SKILL to athlete.trainingWeekdays.size)
        }
        val primary=selected.filter {
            it.role==ComposerRole.PRIMARY && it.equipmentId!=null
        }.sortedBy{it.exerciseId}.firstOrNull()
        if(input.phase!=TrainingPhase.HYPERTROPHY && primary==null)
            issues+="Strength/peak needs a calibrated, eligible primary movement; none was chosen."
        val policy=AuthoredGoalProgramPolicy(
            policyId=POLICY_VERSION,revision="v1",
            rationale="Versioned provisional volume and effort starting plan; personal responses, fatigue and recoverable dose require calibration.",
            athleteId=athlete.athleteProfileId,
            athleteRevision=athlete.profileRevision,
            goal=athlete.goal,experience=athlete.experience,
            split=athlete.split,phase=input.phase,
            trainingDays=athlete.trainingWeekdays.size,
            baselineBounds=baseline,selectedBounds=bounds,
            minimumPurposeDays=purposes,
            primaryLiftMinimumDays=if(primary!=null &&
                input.phase!=TrainingPhase.HYPERTROPHY)
                mapOf(primary.exerciseId to 1) else emptyMap())
        if(input.phase!=TrainingPhase.HYPERTROPHY && primary==null)
            return output(FirstProgramStartStatus.DRAFT_NEEDS_REVIEW,policy,gaps=gaps)
        val generated=runCatching {
            CoachAthleteProgramGenerator.generate(
                AthleteProgramGenerationRequest(input.setup,input.phase,policy,
                    input.catalog,choices,
                    athlete.trainingWeekdays.associateWith {
                        input.nonSetSecondsPerDay
                    },
                    maximumExercisesPerDay=when(athlete.sessionTimeBucket) {
                        SessionTimeBucket.FORTY_TO_SIXTY->5
                        SessionTimeBucket.SIXTY_TO_NINETY->7
                        SessionTimeBucket.TWO_HOURS_PLUS->9
                    },
                    preferredPatternsPerDay=2,
                    extraFocuses=input.extraFocuses))
        }.getOrElse {
            issues+="The independent weekly composer rejected these inputs."
            return output(FirstProgramStartStatus.DRAFT_NEEDS_REVIEW,policy,gaps=gaps)
        }
        if(generated.status!=AthleteGenerationStatus.GENERATED_FOR_REVIEW) {
            issues+=generated.issues.map{it.explanation}
            return output(FirstProgramStartStatus.DRAFT_NEEDS_REVIEW,policy,
                generated,gaps=gaps)
        }
        val composed=generated.candidate?.catalogReview?.composedRequest
        if(composed==null) {
            issues+="The candidate is missing its auditable weekly prescription."
            return output(FirstProgramStartStatus.DRAFT_NEEDS_REVIEW,policy,
                generated,gaps=gaps)
        }
        val byId=selected.associateBy{it.exerciseId}
        val prescriptions=composed.sessionIntents.flatMap {intent ->
            composed.exercises.mapNotNull {option->
                val source=byId[option.exerciseId] ?:return@mapNotNull null
                val desired=when {
                    input.phase!=TrainingPhase.HYPERTROPHY &&
                        source.role==ComposerRole.PRIMARY -> RepBand(3,6)
                    source.role==ComposerRole.ACCESSORY -> RepBand(10,15)
                    else ->RepBand(6,12)
                }
                val practical=source.sourcePracticalReps ?: desired
                val lower=maxOf(desired.minimum,practical.minimum)
                val upper=minOf(desired.maximum,practical.maximum)
                // Explicitly refuse a mismatched rep scheme instead of
                // silently issuing reps outside the reviewed movement range.
                if(lower>upper)return@mapNotNull null
                ExercisePrescriptionPolicy(
                    option.exerciseId,intent.purpose,RepBand(lower,upper),
                    targetRir=if(athlete.experience==AthleteExperience.BEGINNER)3 else 2,
                    activeSecondsPerSet=option.workSeconds,
                    restSecondsPerSet=option.restSeconds,
                    scheme=SetLoadScheme.STRAIGHT,
                    dayIndex=intent.dayIndex)
            }
        }
        val prescribed=runCatching {
            CoachSessionPrescriptionDomain.draft(composed,prescriptions)
        }.getOrElse {
            issues+="The set prescription could not be audited safely."
            return output(FirstProgramStartStatus.DRAFT_NEEDS_REVIEW,policy,
                generated,gaps=gaps)
        }
        if(prescribed.status!=PrescriptionDraftStatus.COMPLETE_FOR_REVIEW)
            issues+=prescribed.reviewNotes
        return output(
            if(prescribed.status==PrescriptionDraftStatus.COMPLETE_FOR_REVIEW)
                FirstProgramStartStatus.PRESCRIBED_DRAFT_FOR_REVIEW
            else FirstProgramStartStatus.DRAFT_NEEDS_REVIEW,
            policy,generated,prescribed,gaps)
    }
}
