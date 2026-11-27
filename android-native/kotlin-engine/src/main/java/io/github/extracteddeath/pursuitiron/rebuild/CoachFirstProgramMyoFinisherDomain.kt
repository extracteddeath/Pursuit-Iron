package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal

/**
 * OPTIONAL recommendation for the generated-first-program -> reviewed native
 * cycle handoff. Default generated programs remain ordinary straight sets.
 *
 * No physiological stimulus or fatigue is inferred from a catalog label:
 * only an explicitly reviewed, stocked, stable isolation accessory with an
 * actual 12–20-rep activation prescription can be offered. This is a
 * read-only proposal, never an approval or saved-workout authority.
 */
data class FirstProgramMyoFinisherChoice(
    val dayIndex:Int,
    val exerciseIndex:Int,
    val exerciseId:String,
    val setIndex:Int,
    val setKey:String
)

/** One optional generated finisher, with exact target identity and recipe. */
data class FirstProgramFinisherProposal(val choice:FirstProgramMyoFinisherChoice,
    val technique:CustomSetAmplifier)

object CoachFirstProgramMyoFinisherDomain {
    private fun stableStockedIsolation(exercise:DraftPrescribedExercise,
        catalog:CatalogMovement?):Boolean {
        val movement=catalog ?:return false
        if(exercise.role!=ComposerRole.ACCESSORY ||
            movement.role!=ComposerRole.ACCESSORY ||
            movement.pattern!=ComposerPattern.ISOLATION ||
            movement.creditStatus!=CatalogCreditStatus.AUTHOR_REVIEWED_PROVISIONAL ||
            movement.exerciseId!=exercise.exerciseId ||
            movement.expectedLoadBasis==null)return false
        val implement=movement.equipmentId ?:return false
        // A dumbbell, band, free bar or generic unknown station is not
        // silently promoted into a stable cable/machine myo-rep protocol.
        return implement.contains("cable") || implement.contains("machine")
    }

    fun recommend(
        sessions:List<DraftPrescribedSession>,
        catalog:List<CatalogMovement>,
        phase:TrainingPhase,
        experience:AthleteExperience
    ):FirstProgramMyoFinisherChoice? = select(sessions,catalog,phase,experience) { _,set ->
        if(set.targetReps.minimum>=12 && set.targetReps.maximum<=20) prescribedTechnique() else null
    }?.choice

    /** New explicit opt-ins must use the exact independently reviewed gym
     * stock. No effect on the legacy myo-only seed or its persisted bytes. */
    fun recommendTechnique(sessions:List<DraftPrescribedSession>,catalog:List<CatalogMovement>,
        phase:TrainingPhase,experience:AthleteExperience,
        confirmedStock:Map<String,VerifiedLoadStock>,kind:CustomAmplifierKind):FirstProgramFinisherProposal? {
        if(kind !in setOf(CustomAmplifierKind.MYO_REPS,CustomAmplifierKind.DROP_SET,
            CustomAmplifierKind.LENGTHENED_PARTIALS) ||
            catalog.map{it.exerciseId}.distinct().size!=catalog.size)return null
        return select(sessions,catalog,phase,experience){movement,set->
            val load=set.load as? DraftSetLoad.Verified ?:return@select null
            val stock=confirmedStock[movement.equipmentId] ?:return@select null
            val inspected=EquipmentLoadDomain.inspect(stock,load.value,load.unit) as? LoadInspection.Confirmed
                ?:return@select null
            if(!inspected.exact || inspected.basis!=load.basis)return@select null
            val range=AdvancedAmplifierProtocolDomain.template(kind).activationReps
            if(set.targetReps.minimum !in range || set.targetReps.maximum !in range)return@select null
            when(kind) {
                CustomAmplifierKind.MYO_REPS -> prescribedTechnique()
                CustomAmplifierKind.DROP_SET -> {
                    if(!load.basis.higherSettingIncreasesDifficulty)return@select null
                    // A single roughly 15–25% reduction, using only exact
                    // verified stock. Refuse a large/zero/unavailable step.
                    val ceiling=load.value.multiply(BigDecimal("0.85"))
                    val lower=(EquipmentLoadDomain.inspect(stock,ceiling,load.unit)
                        as? LoadInspection.Confirmed)?.atOrBelow ?:return@select null
                    if(lower<load.value.multiply(BigDecimal("0.75")) || lower.signum()<=0)return@select null
                    CustomSetAmplifier(kind,8,10,followUpLoad=load.copy(value=lower),
                        followUpActiveSeconds=40).takeIf{AdvancedAmplifierProtocolDomain.validDropSequence(load,it)}
                }
                CustomAmplifierKind.LENGTHENED_PARTIALS -> {
                    // Start with narrowly reviewed stable variants rather
                    // than assuming every cable or machine loads the stretch.
                    if(experience!=AthleteExperience.ADVANCED ||
                        movement.exerciseId !in setOf("seated_leg_curl","seated-calf"))return@select null
                    CustomSetAmplifier(kind,3,0,followUpActiveSeconds=15)
                }
                else -> null
            }?.takeIf{AdvancedAmplifierProtocolDomain.validStagedFollowUps(it)}
        }
    }

    private fun select(sessions:List<DraftPrescribedSession>,catalog:List<CatalogMovement>,
        phase:TrainingPhase,experience:AthleteExperience,
        recipe:(CatalogMovement,DraftPrescribedSet)->CustomSetAmplifier?):FirstProgramFinisherProposal? {
        if(phase!=TrainingPhase.HYPERTROPHY ||
            experience==AthleteExperience.BEGINNER)return null
        val byId=catalog.associateBy{it.exerciseId}
        for(session in sessions.sortedBy{it.dayIndex}) {
            if(session.purpose!=ComposerSessionPurpose.HYPERTROPHY)continue
            // A finisher follows the other work rather than interrupting
            // the week's primary or secondary strength practice.
            for(index in session.exercises.indices.reversed()) {
                val exercise=session.exercises[index]
                if(!stableStockedIsolation(exercise,byId[exercise.exerciseId]))
                    continue
                val working=exercise.sets.withIndex()
                    .filter{it.value.type==DraftSetType.WORKING}
                if(working.size<2)continue
                val last=working.last()
                val set=last.value
                val load=set.load as? DraftSetLoad.Verified ?:continue
                // Only a physically valid issued load on the reviewed station
                // can become a technique candidate. Never offer a cable or
                // machine finisher on a mismatched basis or malformed weight.
                if(!KotlinSetNumberAuthorityDomain.validIssuedLoad(load) ||
                    load.basis!=byId[exercise.exerciseId]?.expectedLoadBasis)continue
                if(load.value.signum()<=0 ||
                    (set.targetRir==null || set.targetRir !in 1..2))continue
                // If a special back-off or different physical weight is
                // already programmed, do not attach a same-load finisher.
                if(working.any{
                        !KotlinSetNumberAuthorityDomain.sameIssuedTarget(
                            it.value.load,set.load)
                    })continue
                val technique=recipe(byId.getValue(exercise.exerciseId),set) ?:continue
                return FirstProgramFinisherProposal(FirstProgramMyoFinisherChoice(
                    session.dayIndex,index,exercise.exerciseId,
                    last.index,set.stableSetKey),technique)
            }
        }
        return null
    }

    /** Versioned, bounded technique; actual mini-set reps remain independent
     * journal observations. A rep drop stops the optional sequence early. */
    fun prescribedTechnique():CustomSetAmplifier=CustomSetAmplifier(
        kind=CustomAmplifierKind.MYO_REPS,
        followUpReps=4,followUpRestSeconds=20,
        followUpLoad=null,followUpActiveSeconds=60,
        followUpRounds=4)
}
