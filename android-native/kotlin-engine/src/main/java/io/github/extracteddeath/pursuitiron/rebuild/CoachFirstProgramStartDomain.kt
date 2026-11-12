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
    val nonSetSecondsPerDay:Int = 900,
    /** Saved calibrations replay their original policy; new plans use v7. */
    val policyVersion:String = CoachFirstProgramStartDomain.POLICY_VERSION
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
    val prescription:SessionPrescriptionDraft?,
    /** Bounded named-main-lift/day-role alternatives; not athlete outcomes. */
    val phasePoliciesChecked:Int=0,
    val phaseSearchBudgetReached:Boolean=false
) {
    /** Deliberately NOT an approval or a usable athlete-specific weight model. */
    val maySaveProgram:Boolean get()=false
    val mayIssueWorkout:Boolean get()=false
}

object CoachFirstProgramStartDomain {
    const val LEGACY_POLICY_VERSION="first-program-reference-v1"
    const val PREVIOUS_POLICY_VERSION="first-program-reference-v2"
    const val TIMED_POLICY_VERSION="first-program-reference-v3"
    const val ALLOCATION_POLICY_VERSION="first-program-reference-v4"
    const val PREVIOUS_PHASE_POLICY_VERSION="first-program-reference-v5"
    const val V6_POLICY_VERSION="first-program-reference-v6"
    const val POLICY_VERSION="first-program-reference-v7"
    fun supportsPolicy(version:String)=version in setOf(
        LEGACY_POLICY_VERSION,PREVIOUS_POLICY_VERSION,TIMED_POLICY_VERSION,
        ALLOCATION_POLICY_VERSION,PREVIOUS_PHASE_POLICY_VERSION,
        V6_POLICY_VERSION,POLICY_VERSION)
    private const val MAX_PHASE_REVIEWS=32
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
        selected:List<CatalogMovement>,
        policyVersion:String
    ):Pair<List<DeclaredMuscleDoseBounds>,List<DeclaredMuscleDoseBounds>> {
        val directlySupported=selected.flatMap {movement ->
            movement.credits.filter {it.role==MuscleCreditRole.DIRECT}
                .map {it.muscle}
        }.toSet()
        val exposed=selected.flatMap {movement->
            movement.credits.filter{it.fractionalSet>zero}.map{it.muscle}
        }.toSet()
        val initial=when(athlete.experience) {
            AthleteExperience.BEGINNER->2
            AthleteExperience.INTERMEDIATE->3
            AthleteExperience.ADVANCED->4
        }
        val baseline=TrainingMuscle.entries.map {muscle ->
            val amount=if(muscle in directlySupported)initial else 0
            // Missing direct work remains an explicit coverage gap. It must
            // not forbid a compound's declared indirect exposure. A zero
            // MRV previously excluded bench, rows and squats altogether.
            val ceiling=if(policyVersion!=LEGACY_POLICY_VERSION && muscle in exposed)
                initial*3 else amount*3
            val target=if(policyVersion!=LEGACY_POLICY_VERSION && muscle in exposed)
                initial*2 else amount*2
            DeclaredMuscleDoseBounds(muscle,num(amount),num(target),
                num(ceiling),if(amount>0)1 else 0)
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

    /**
     * New strength/peak starters compare named-lift/day-role policies before
     * asking the SAME composer for sets. Prefer more eligible practice days;
     * supporting days do not need a fictional main lift. At most 32 phase
     * policies are tried, each with the existing bounded allocation search.
     * A checked candidate is not a global optimum or a calibrated peak/taper.
     */
    fun review(input:FirstProgramStartRequest):FirstProgramStartReview {
        if(input.policyVersion !in setOf(
                PREVIOUS_PHASE_POLICY_VERSION,V6_POLICY_VERSION,POLICY_VERSION) ||
            input.phase==TrainingPhase.HYPERTROPHY)
            return reviewOnce(input)
        if(!input.setup.mayReviewProgramCandidate || input.catalog.size !in 1..CoachExerciseCatalogDomain.MAX_CATALOG_ENTRIES ||
            input.startingLoads.size>32 || input.selectedBodyweightExerciseIds.size>32)
            return reviewOnce(input)
        val primaries=input.catalog.filter {
            it.exerciseId in input.startingLoads && it.role==ComposerRole.PRIMARY &&
                it.equipmentId!=null
        }.distinctBy{it.exerciseId}.sortedBy{it.exerciseId}
        val days=input.setup.reviewed?.trainingWeekdays?.size
        if(primaries.isEmpty() || days==null || days !in 3..6)
            return reviewOnce(input)
        // Five-day ULPPL has only TWO lower-body day focuses. Its legitimate
        // two-squat + one-deadlift exposure target therefore needs a second
        // named strength movement ON an existing lower skill day, not a
        // fabricated sixth workout or a disappearing deadlift. The canonical
        // composer already supports multiple distinct exercises on one day,
        // with stock, set, recovery and full-session audits. Pin the hinge
        // with the existing required-visit contract; keep the four skill-day
        // intents explicitly attached to bench and squat for 2x/2x.
        //
        // Only explicitly calibrated, reviewed PRIMARY variants qualify.
        // A Romanian hinge is NOT silently substituted for a conventional
        // deadlift skill; this is a provisional 2/2/1 review offer, not
        // mandatory dogma for every athlete, phase or custom cycle.
        val fiveDaySkill=if(input.policyVersion==POLICY_VERSION &&
            input.setup.reviewed?.split==ComposerSplit.ULPPL && days==5) {
            fun main(family:String,variant:String?=null)=primaries.firstOrNull {
                it.familyId==family &&
                    (variant==null || it.variantId==variant)
            }
            val bench=main("bench_press")
            val squat=main("squat","back-barbell")
            val deadlift=main("hip_hinge","conventional-barbell")
            if(bench!=null && squat!=null && deadlift!=null)
                Triple(bench.exerciseId,squat.exerciseId,deadlift.exerciseId)
            else null
        } else null
        if(fiveDaySkill!=null) {
            val (bench,squat,deadlift)=fiveDaySkill
            val candidate=reviewOnce(input,listOf(bench,squat),
                skillDays=4,requiredPrimaryDays=2,
                companionPrimaryIds=setOf(deadlift))
            return candidate.copy(
                phasePoliciesChecked=1,
                issues=Collections.unmodifiableList(candidate.issues+
                    if(candidate.status==
                        FirstProgramStartStatus.PRESCRIBED_DRAFT_FOR_REVIEW)
                        "Five-day strength skill review: two distinct $bench days, two distinct $squat days and one $deadlift visit on a verified lower-body skill day. Every working load and full session remains subject to approval."
                    else
                        "The calibrated five-day bench 2 / squat 2 / deadlift 1 goal could not be safely composed under the declared gym, recovery, muscle-dose and time constraints. No lower-frequency substitution was silently accepted; adjust the schedule or explicitly author a different target.")
            )
        }
        val groups=linkedSetOf<List<String>>()
        if(primaries.size<=days)groups+=primaries.map{it.exerciseId}
        // Keep a bounded spread of alternatives for overlapping main-lift
        // variants. Calibration offers are not user-pinned weekly frequencies;
        // every chosen exercise remains available to the complete composer.
        val pairs=primaries.flatMapIndexed {i,left->
            primaries.drop(i+1).map{right->left to right}
        }.sortedBy{(left,right)->if(left.familyId!=right.familyId)0 else 1}
        pairs.take(8).forEach{(left,right)->groups+=listOf(left.exerciseId,right.exerciseId)}
        primaries.take(8).forEach{groups+=listOf(it.exerciseId)}
        // A strength/peak starter should require real repeated skill work
        // when the chosen split has enough eligible days, not accidentally
        // get it only because a permissive 1-day policy happened to place
        // the same lift twice. Try up to four high-value repeated-practice
        // offers first; fall back to the original 1-day assumptions when
        // actual gym/focus/recovery/session audits cannot support them.
        //
        // Full Body with two named lifts over three days cannot make FOUR
        // distinct primary slots, and no policy may silently double-book
        // a day to manufacture frequency.
        data class PhaseOffer(val lifts:List<String>,val requiredDays:Int)
        val repeatOffers=if(input.policyVersion !in setOf(
                V6_POLICY_VERSION,POLICY_VERSION) ||
            input.setup.reviewed?.split==ComposerSplit.FULL_BODY)
            emptyList() else groups.filter{2*it.size<=days}
                .take(4).map{PhaseOffer(it,2)}
        val phaseOffers=repeatOffers+groups.map{PhaseOffer(it,1)}
        var attempts=0
        var best:FirstProgramStartReview?=null
        var bestRank:List<Int>?=null
        fun rank(result:FirstProgramStartReview):List<Int> {
            val week=result.generation?.candidate?.catalogReview?.draft
            return listOf(if(week==null)1 else 0,
                week?.audit?.muscles?.values?.count {
                    it.gapToMev.signum()>0 || it.excessAboveMrv.signum()>0 ||
                        it.missingDirectDays>0
                } ?:100,
                week?.daysWithUnmetPrimary?.size ?:100,
                week?.audit?.daysOutsidePreferredWindow?.size ?:100,
                result.issues.size)
        }
        fun better(a:List<Int>,b:List<Int>)=a.indices.firstOrNull{a[it]!=b[it]}
            ?.let{a[it]<b[it]} ?:false
        search@ for(offer in phaseOffers)
            for(skillDays in days downTo offer.lifts.size*offer.requiredDays) {
            if(attempts>=MAX_PHASE_REVIEWS)break@search
            attempts++
            val result=reviewOnce(input,offer.lifts,skillDays,
                offer.requiredDays)
            if(result.status in setOf(FirstProgramStartStatus.SETUP_REQUIRED,
                FirstProgramStartStatus.CALIBRATION_REQUIRED,
                FirstProgramStartStatus.INVALID_CALIBRATION))return result
            if(result.status==FirstProgramStartStatus.PRESCRIBED_DRAFT_FOR_REVIEW)
                return result.copy(issues=Collections.unmodifiableList(result.issues+
                    if(input.policyVersion==PREVIOUS_PHASE_POLICY_VERSION)
                        "Main-lift practice and supporting days match your chosen split and verified movements."
                    else
                        "Main-lift practice (${offer.requiredDays} distinct day(s) per named lift) and supporting days match your chosen split and verified movements."),
                    phasePoliciesChecked=attempts)
            val score=rank(result)
            if(bestRank==null || better(score,checkNotNull(bestRank))) {
                best=result;bestRank=score
            }
        }
        val unresolved=best ?:return reviewOnce(input)
        return unresolved.copy(issues=Collections.unmodifiableList(unresolved.issues+
            "The bounded main-lift/day-role search found no complete starter. Review the chosen main lifts, available days and session time; other combinations may exist."),
            phasePoliciesChecked=attempts,
            phaseSearchBudgetReached=attempts<phaseOffers.sumOf{
                days-it.lifts.size*it.requiredDays+1
            } || primaries.size>8 || pairs.size>8)
    }

    private fun reviewOnce(input:FirstProgramStartRequest,
        namedPrimaries:List<String>?=null,skillDays:Int?=null,
        requiredPrimaryDays:Int=1,
        companionPrimaryIds:Set<String> =emptySet()):FirstProgramStartReview {
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
        if(!supportsPolicy(input.policyVersion) ||
            input.nonSetSecondsPerDay !in 0..21600 ||
            input.startingLoads.size>32 ||
            input.selectedBodyweightExerciseIds.size>32 ||
            input.startingLoads.keys.intersect(
                input.selectedBodyweightExerciseIds).isNotEmpty() ||
            input.catalog.size !in 1..CoachExerciseCatalogDomain.MAX_CATALOG_ENTRIES ||
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
        // The v6 starter must not silently discard a specifically calibrated
        // secondary lift from the SAME family as a chosen primary, e.g. an
        // incline DB press when bench pressing is the named strength skill.
        // Preserve at most one related secondary per family to keep the
        // bounded starter feasible; prioritize an explicitly cataloged
        // incline variant when several were selected. V1-v5 replay unchanged.
        // This is a *required exposure*, not an automatic load transfer or
        // authority to replace the athlete's chosen main bench lift.
        if(input.policyVersion in setOf(V6_POLICY_VERSION,POLICY_VERSION) &&
            input.phase!=TrainingPhase.HYPERTROPHY) {
            val calibratedPrimaryFamilies=selected.filter {
                it.role==ComposerRole.PRIMARY &&
                    it.exerciseId in input.startingLoads
            }.map{it.familyId}.toSet()
            val retainedRelatedVariants=selected.filter {
                it.role==ComposerRole.SECONDARY &&
                    it.familyId in calibratedPrimaryFamilies &&
                    it.exerciseId in input.startingLoads
            }.groupBy{it.familyId}.values.mapNotNull{variants->
                variants.sortedWith(compareByDescending<CatalogMovement>{
                    it.variantId.contains("incline",ignoreCase=true)
                }.thenBy{it.exerciseId}).firstOrNull()?.exerciseId
            }.toSet()
            for(index in choices.indices)if(
                choices[index].exerciseId in retainedRelatedVariants)
                choices[index]=choices[index].copy(requiredVisits=1)
        }
        // V6/V7 phase-specific dose uses the selected MAIN lift policy.
        // A merely calibrated alternative primary (e.g. OHP alongside a
        // bench-focused Upper/Push block) remains an available exercise but
        // cannot introduce compulsory direct-muscle volume that hijacks
        // the declared bench priority. Unnamed secondary/accessory work is
        // still eligible and contributes its honest direct/indirect credits.
        // Archived v1-v5 dose assumptions are replayed unchanged.
        // A v7 companion primary contributes REAL direct muscle work, even
        // when it shares a day with another named skill. Omitting its dose
        // here would falsely set hamstrings/glutes to zero maximum and make
        // the fully checked deadlift visit impossible.
        val phaseDoseSources=if(input.policyVersion in setOf(
                V6_POLICY_VERSION,POLICY_VERSION) &&
            input.phase!=TrainingPhase.HYPERTROPHY &&
            namedPrimaries!=null)selected.filter {
                it.role!=ComposerRole.PRIMARY ||
                    it.exerciseId in namedPrimaries ||
                    it.exerciseId in companionPrimaryIds
            } else selected
        for(index in choices.indices)
            if(choices[index].exerciseId in companionPrimaryIds)
                // This is ONE conventional deadlift skill appearance, not a
                // suggestion to add more weekly heavy hinges. The normal
                // composer still chooses its actual day, sets and load.
                choices[index]=choices[index].copy(
                    requiredVisits=1,maximumVisits=1)
        val supported=phaseDoseSources.flatMap {it.credits.filter {c->
            c.role==MuscleCreditRole.DIRECT
        }.map{c->c.muscle}}.toSet()
        val gaps=TrainingMuscle.entries.filter {it !in supported}.toSet()
        if(gaps.isNotEmpty())issues+=
            "The starter selection lacks direct exercise coverage for ${gaps.size} tracked muscles. These are not secretly counted as fully trained."

        val (baseline,bounds)=startingBounds(athlete,phaseDoseSources,
            input.policyVersion)
        // The day roles are transparent defaults to be revised after actual
        // training. Strength skill days require a real verified primary lift.
        val purposes=when(input.phase) {
            TrainingPhase.HYPERTROPHY ->
                mapOf(ComposerSessionPurpose.HYPERTROPHY to athlete.trainingWeekdays.size)
            TrainingPhase.STRENGTH,TrainingPhase.PEAK ->
                if(input.policyVersion in setOf(
                    PREVIOUS_PHASE_POLICY_VERSION,
                    V6_POLICY_VERSION,POLICY_VERSION) &&
                    skillDays!=null)
                    linkedMapOf(ComposerSessionPurpose.STRENGTH_SKILL to skillDays,
                        ComposerSessionPurpose.GENERAL_PREPARATION to
                            athlete.trainingWeekdays.size-skillDays).filterValues{it>0}
                else mapOf(ComposerSessionPurpose.STRENGTH_SKILL to athlete.trainingWeekdays.size)
        }
        val primary=selected.filter {
            it.role==ComposerRole.PRIMARY && it.equipmentId!=null
        }.sortedBy{it.exerciseId}.firstOrNull()
        if(input.phase!=TrainingPhase.HYPERTROPHY && primary==null)
            issues+="Strength/peak needs a calibrated, eligible primary movement; none was chosen."
        val policy=AuthoredGoalProgramPolicy(
            policyId=input.policyVersion,
            revision=when(input.policyVersion) {
                LEGACY_POLICY_VERSION->"v1"
                PREVIOUS_POLICY_VERSION->"v2"
                TIMED_POLICY_VERSION->"v3"
                ALLOCATION_POLICY_VERSION->"v4"
                PREVIOUS_PHASE_POLICY_VERSION->"v5"
                V6_POLICY_VERSION->"v6"
                else->"v7"
            },
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
                if(input.policyVersion in setOf(
                    PREVIOUS_PHASE_POLICY_VERSION,
                    V6_POLICY_VERSION,POLICY_VERSION) &&
                    namedPrimaries!=null)
                    namedPrimaries.associateWith{requiredPrimaryDays}
                else mapOf(primary.exerciseId to 1) else emptyMap())
        if(input.phase!=TrainingPhase.HYPERTROPHY && primary==null)
            return output(FirstProgramStartStatus.DRAFT_NEEDS_REVIEW,policy,gaps=gaps)

        val byId=selected.associateBy{it.exerciseId}
        fun prescription(
            exerciseId:String,working:BigDecimal?,workSeconds:Int,restSeconds:Int,
            purpose:ComposerSessionPurpose,dayIndex:Int?
        ):ExercisePrescriptionPolicy? {
            val source=byId[exerciseId] ?:return null
            val desired=when {
                input.phase!=TrainingPhase.HYPERTROPHY &&
                    source.role==ComposerRole.PRIMARY -> RepBand(3,6)
                source.role==ComposerRole.ACCESSORY -> RepBand(10,15)
                else ->RepBand(6,12)
            }
            val practical=source.sourcePracticalReps ?:desired
            val lower=maxOf(desired.minimum,practical.minimum)
            val upper=minOf(desired.maximum,practical.maximum)
            if(lower>upper)return null
            // Preserve the v2 low-volume rehearsal exactly. V3 and later
            // use their reviewed durations while choosing working sets.
            val warmup=if(input.policyVersion!=LEGACY_POLICY_VERSION &&
                source.role!=ComposerRole.ACCESSORY && working!=null) {
                val stock=athlete.gym.confirmedStock[source.equipmentId]
                val lighter=(EquipmentLoadDomain.inspect(stock,
                    working*BigDecimal("0.8"),athlete.gym.unit)
                    as? LoadInspection.Confirmed)?.atOrBelow
                if(lighter!=null && lighter>zero && lighter<working)
                    listOf(DeclaredWarmupStep(lighter,
                        if(input.phase==TrainingPhase.HYPERTROPHY)5 else 3,30,60))
                else emptyList()
            } else emptyList()
            return ExercisePrescriptionPolicy(
                exerciseId,purpose,RepBand(lower,upper),
                targetRir=if(athlete.experience==AthleteExperience.BEGINNER)3 else 2,
                activeSecondsPerSet=workSeconds,restSecondsPerSet=restSeconds,
                scheme=SetLoadScheme.STRAIGHT,warmups=warmup,dayIndex=dayIndex)
        }
        val timed=input.policyVersion in setOf(TIMED_POLICY_VERSION,
            ALLOCATION_POLICY_VERSION,PREVIOUS_PHASE_POLICY_VERSION,
            V6_POLICY_VERSION,POLICY_VERSION)
        val sharedPolicies=if(timed)purposes.keys.flatMap {purpose->choices.mapNotNull {choice->
            prescription(choice.exerciseId,choice.selectedLoad,
                choice.activeSecondsPerSet,choice.restSecondsPerSet,
                purpose,null)
        }} else emptyList()
        fun generate(offered:List<CatalogExerciseChoice>)=
            CoachAthleteProgramGenerator.generate(
                AthleteProgramGenerationRequest(input.setup,input.phase,policy,
                    input.catalog,offered,
                    athlete.trainingWeekdays.associateWith {
                        input.nonSetSecondsPerDay
                    },
                    maximumExercisesPerDay=when(athlete.sessionTimeBucket) {
                        SessionTimeBucket.FORTY_TO_SIXTY->5
                        SessionTimeBucket.SIXTY_TO_NINETY->7
                        SessionTimeBucket.TWO_HOURS_PLUS->9
                    },
                    preferredPatternsPerDay=2,
                    extraFocuses=input.extraFocuses,
                    visitTimings=CoachSessionPrescriptionDomain.timingsForPolicies(sharedPolicies)))
        var generated=runCatching {
            generate(choices)
        }.getOrElse {
            issues+="The independent weekly composer rejected these inputs."
            return output(FirstProgramStartStatus.DRAFT_NEEDS_REVIEW,policy,gaps=gaps)
        }
        val initialAudit=generated.candidate?.catalogReview?.draft?.audit
        if(input.policyVersion in setOf(ALLOCATION_POLICY_VERSION,
            PREVIOUS_PHASE_POLICY_VERSION,V6_POLICY_VERSION,
            POLICY_VERSION) &&
            generated.status!=AthleteGenerationStatus.GENERATED_FOR_REVIEW &&
            initialAudit?.satisfiesDeclaredHardConstraints==true &&
            initialAudit.sessions.any {it.finding==TimeWindowFinding.ABOVE_PREFERENCE}) {
            // A single greedy allocation is not proof that the athlete's
            // week cannot fit. Compare at most four smaller secondary/
            // accessory visit envelopes through the SAME complete composer.
            // Keep primary options, minimum dose/frequency, stock, warmups,
            // effort, recovery and both time preferences/hard caps unchanged.
            // V4 and later use this search; saved v1-v3 remain exact.
            for((secondary,accessory) in listOf(3 to 3,2 to 3,3 to 2,2 to 2)) {
                val offered=choices.map {choice->
                    val cap=when(byId.getValue(choice.exerciseId).role) {
                        ComposerRole.PRIMARY->choice.maximumSetsPerVisit
                        ComposerRole.SECONDARY->secondary
                        ComposerRole.ACCESSORY->accessory
                    }
                    choice.copy(maximumSetsPerVisit=minOf(choice.maximumSetsPerVisit,cap))
                }
                val alternative=runCatching{generate(offered)}.getOrNull() ?:continue
                if(alternative.status==AthleteGenerationStatus.GENERATED_FOR_REVIEW) {
                    generated=alternative
                    issues+="A smaller secondary/accessory set allocation fits the full week while preserving the reviewed muscle-dose, frequency and recovery constraints."
                    break
                }
            }
        }
        if(generated.status!=AthleteGenerationStatus.GENERATED_FOR_REVIEW) {
            issues+=generated.issues.map{it.explanation}
            return output(FirstProgramStartStatus.DRAFT_NEEDS_REVIEW,policy,
                generated,gaps=gaps)
        }
        // The single canonical composer must prove that every extra skill
        // primary actually landed on an AUTHORED strength-skill day; a pinned
        // generic visit on a supporting day is not a 2/2/1 strength block.
        // Never publish an apparently complete week with this hidden gap.
        if(companionPrimaryIds.isNotEmpty()) {
            val verifiedWeek=generated.candidate?.catalogReview?.draft
            val skillDaysInDraft=verifiedWeek?.sessions?.filter {
                it.purpose==ComposerSessionPurpose.STRENGTH_SKILL
            }?.map{it.dayIndex}?.toSet().orEmpty()
            // In the five-day 2 bench / 2 squat / 1 deadlift offer,
            // merely finding the deadlift on ANY skill day is insufficient.
            // It must share a real, audited squat-skill lower session.
            // Check actual visits, not labels or the authored intent alone.
            val requiredCompanionDays=if(input.policyVersion==POLICY_VERSION &&
                athlete.split==ComposerSplit.ULPPL && namedPrimaries!=null) {
                val namedSquats=selected.filter {
                    it.exerciseId in namedPrimaries &&
                        it.familyId=="squat" && it.variantId=="back-barbell"
                }.map{it.exerciseId}.toSet()
                verifiedWeek?.visits.orEmpty().filter {
                    it.exerciseId in namedSquats && it.dayIndex in skillDaysInDraft
                }.map{it.dayIndex}.toSet()
            } else skillDaysInDraft
            if(verifiedWeek==null || companionPrimaryIds.any{ id ->
                verifiedWeek.visits.none{
                    it.exerciseId==id && it.dayIndex in requiredCompanionDays
                }
            }) {
                issues+="A required companion strength lift was not verified alongside its named lower-body skill lift; the program may not omit or relabel it."
                return output(FirstProgramStartStatus.DRAFT_NEEDS_REVIEW,
                    policy,generated,gaps=gaps)
            }
        }
        val composed=generated.candidate?.catalogReview?.composedRequest
        if(composed==null) {
            issues+="The candidate is missing its auditable weekly prescription."
            return output(FirstProgramStartStatus.DRAFT_NEEDS_REVIEW,policy,
                generated,gaps=gaps)
        }
        val prescriptions=if(timed)sharedPolicies else composed.sessionIntents.flatMap {intent ->
            composed.exercises.mapNotNull {option->
                prescription(option.exerciseId,
                    (option.equipment as? ComposerEquipment.Verified)?.plannedLoad,
                    option.workSeconds,option.restSeconds,intent.purpose,intent.dayIndex)
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
