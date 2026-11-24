package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import java.util.Collections

/**
 * Athlete-intent-only builder for mixed typed measured sessions.
 *
 * One workout can contain multiple real measurement types, physical stations,
 * band identities and loads. Nothing in this model issues a workout, approves
 * a program, equates measured seconds with reps, or transfers working weight.
 * The owner-fenced native consent source MUST independently revalidate every
 * set against the current gym before it can issue or enter History.
 */
data class MeasuredSessionSelection(
    val exerciseId:String,
    val targetAmount:Int,
    val sets:Int,
    val singleSide:MeasuredSide?=null,
    val targetExternalLoad:BigDecimal?=null,
    val bandId:String?=null,
    val matchedPairConfirmed:Boolean=false
)

object MeasuredSessionAuthoringDomain {
    val setsPerExercise = 1..8
    private val id=Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,79}$")
    private fun verifiedBands(profile:ReviewedAthleteSetup):Set<String> =
        profile.gym.availableAttachments.filterTo(mutableSetOf()) {
            it.startsWith("band-") && it.length>5 && id.matches(it)
        }

    private fun fixtures(profile:ReviewedAthleteSetup):Set<String> {
        val facts=profile.gym.availableFacilities+
            profile.gym.availableAttachments
        return facts +
            (if("pullup-bar" in facts)setOf("pull-up-bar") else emptySet())+
            (if("wall-support" in facts)setOf("stable-wall") else emptySet())
    }

    /** Gym options are hints, NOT proof of a verified exact load or issuance. */
    fun available(profile:ReviewedAthleteSetup):List<MeasuredExerciseDefinition> {
        val witnessed=fixtures(profile)
        val bands=verifiedBands(profile)
        return Collections.unmodifiableList(
            MeasuredExerciseCatalogDomain.all.filter{definition->
                if(definition.exerciseId in profile.excludedExerciseIds ||
                    !witnessed.containsAll(definition.requiredFixtures))
                    false
                else when(definition.loadKind) {
                    MeasuredLoadKind.UNWEIGHTED ->profile.allowBodyweight
                    MeasuredLoadKind.BANDED_BODYWEIGHT ->
                        profile.allowBodyweight &&
                            "bands" in profile.gym.availableFacilities &&
                            bands.isNotEmpty()
                    MeasuredLoadKind.VERIFIED_PER_HAND,
                    MeasuredLoadKind.VERIFIED_ADDED_LOAD -> {
                        val stock=MeasuredPhysicalStockSource
                            .equipmentIdFor(definition.exerciseId)
                            ?.let {profile.gym.confirmedStock[it]}
                        stock!=null &&
                            stock.basis==definition.expectedBasis &&
                            stock.unit==profile.gym.unit &&
                            (definition.kind!=MeasuredExerciseKind.CARRY_CENTIMETERS ||
                                "dumbbells" in profile.gym.availableFacilities) &&
                            (definition.exerciseId!="weighted-plank" ||
                                profile.allowBodyweight)
                    }
                }
            })
    }

    private fun physicalStation(
        profile:ReviewedAthleteSetup,definition:MeasuredExerciseDefinition
    ):String {
        if(definition.kind==MeasuredExerciseKind.CARRY_CENTIMETERS)
            return checkNotNull(MeasuredPhysicalStockSource.equipmentIdFor(
                definition.exerciseId))
        val facts=profile.gym.availableFacilities+
            profile.gym.availableAttachments
        return definition.requiredFixtures.firstOrNull()?.let {required->
            when {
                required=="pull-up-bar" && "pullup-bar" in facts->"pullup-bar"
                required=="stable-wall" && "wall-support" in facts->"wall-support"
                else->required
            }
        } ?: "floor"
    }

    /** Exact saved-state representation of unapproved intent only.
     * Only the caller's owner/gym/revision fence allows its reuse. */
    fun saveDraft(selection:MeasuredSessionSelection):String =
        listOf(selection.exerciseId,selection.targetAmount.toString(),
            selection.sets.toString(),selection.singleSide?.name.orEmpty(),
            selection.targetExternalLoad?.toPlainString().orEmpty(),
            selection.bandId.orEmpty(),
            selection.matchedPairConfirmed.toString()).joinToString("|")

    fun restoreDraft(raw:String):MeasuredSessionSelection? {
        if(raw.length>400)return null
        val fields=raw.split('|')
        if(fields.size!=7 || !id.matches(fields[0]))return null
        val amount=fields[1].toIntOrNull()?:return null
        val sets=fields[2].toIntOrNull()?:return null
        val side=fields[3].takeIf{it.isNotEmpty()}?.let{name->
            MeasuredSide.entries.singleOrNull{it.name==name}?:return null
        }
        val load=fields[4].takeIf{it.isNotEmpty()}?.let{
            runCatching{BigDecimal(it)}.getOrNull()?:return null
        }
        val band=fields[5].takeIf{it.isNotEmpty()}
        if(band!=null && !id.matches(band))return null
        val pair=when(fields[6]) {
            "true"->true
            "false"->false
            else->return null
        }
        return MeasuredSessionSelection(fields[0],amount,sets,side,load,band,pair)
    }

    /** Preview-only: a fully typed 1..64-set workout, never a signoff. */
    fun compose(
        workoutId:String,sourceRevision:Long,profile:ReviewedAthleteSetup,
        selections:List<MeasuredSessionSelection>
    ):MeasuredWorkoutPlan? {
        if(!id.matches(workoutId) || sourceRevision<1 ||
            sourceRevision>9_007_199_254_740_991L ||
            selections.size !in 1..32 ||
            selections.any{it.sets !in setsPerExercise} ||
            selections.sumOf{it.sets} !in 1..64)return null
        val choices=available(profile).associateBy{it.exerciseId}
        val sets=ArrayList<MeasuredExerciseSetPlan>()
        val verifiedBandIds=verifiedBands(profile)
        for(choice in selections) {
            val definition=choices[choice.exerciseId]?:return null
            val sides=when(definition.sideRule) {
                MeasuredSideRule.BOTH -> {
                    if(choice.singleSide!=null)return null
                    setOf(MeasuredSide.BOTH)
                }
                MeasuredSideRule.LEFT_AND_RIGHT -> {
                    if(choice.singleSide!=null)return null
                    setOf(MeasuredSide.LEFT,MeasuredSide.RIGHT)
                }
                MeasuredSideRule.EXPLICIT_SINGLE_SIDE -> {
                    val chosen=choice.singleSide
                    if(chosen!=MeasuredSide.LEFT && chosen!=MeasuredSide.RIGHT)
                        return null
                    setOf(chosen)
                }
            }
            val loaded=definition.loadKind in setOf(
                MeasuredLoadKind.VERIFIED_ADDED_LOAD,
                MeasuredLoadKind.VERIFIED_PER_HAND)
            val stock=if(loaded)
                MeasuredPhysicalStockSource.equipmentIdFor(definition.exerciseId)
                    ?.let{profile.gym.confirmedStock[it]}?:return null
                else null
            val external=if(loaded)choice.targetExternalLoad?:return null
                else {
                    if(choice.targetExternalLoad!=null)return null
                    null
                }
            if(loaded) {
                // Physical stock may expose a zero selector, but that alone
                // never authorizes a zero added/hand-held prescription.
                // Reuse the SAME issued-load authority as regular workouts.
                if(external==null ||
                    !KotlinSetNumberAuthorityDomain.validPrescribedLoad(
                        external,definition.expectedBasis ?:return null))
                    return null
                val proof=EquipmentLoadDomain.inspect(
                    stock,external,profile.gym.unit)
                if(proof !is LoadInspection.Confirmed || !proof.exact ||
                    proof.basis!=definition.expectedBasis)return null
            }
            if(choice.matchedPairConfirmed!=(definition.exerciseId=="farmers") ||
                (definition.exerciseId=="farmers" && !loaded))
                return null
            val band=if(definition.loadKind==MeasuredLoadKind.BANDED_BODYWEIGHT) {
                choice.bandId?.takeIf{it in verifiedBandIds}?:return null
            } else {
                if(choice.bandId!=null)return null
                null
            }
            val station=physicalStation(profile,definition)
            repeat(choice.sets) {
                val index=sets.size+1
                sets+=MeasuredExerciseSetPlan(workoutId,"set-$index",
                    definition.exerciseId,profile.gym.gymId,sourceRevision,
                    station,choice.targetAmount,sides,
                    definition.requiredFixtures,
                    physicalStock=stock,intendedExternalLoad=external,
                    unit=if(loaded)profile.gym.unit else null,
                    matchedPairConfirmed=choice.matchedPairConfirmed,
                    bandId=band,
                    gymConfirmedBandIds=if(band!=null)setOf(band) else emptySet())
            }
        }
        val plan=MeasuredWorkoutPlan(workoutId,profile.gym.gymId,
            sourceRevision,Collections.unmodifiableList(sets))
        return plan.takeIf{MeasuredWorkoutJournalDomain.begin(it)!=null}
    }
}
