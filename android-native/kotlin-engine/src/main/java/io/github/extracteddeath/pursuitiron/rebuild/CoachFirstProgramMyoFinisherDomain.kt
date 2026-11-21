package io.github.extracteddeath.pursuitiron.rebuild

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
    ):FirstProgramMyoFinisherChoice? {
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
                if(load.value.signum()<=0 || set.targetReps.minimum<12 ||
                    set.targetReps.maximum>20 ||
                    (set.targetRir==null || set.targetRir !in 1..2))continue
                // If a special back-off or different physical weight is
                // already programmed, do not attach a same-load finisher.
                if(working.any{
                        !KotlinSetNumberAuthorityDomain.sameIssuedTarget(
                            it.value.load,set.load)
                    })continue
                return FirstProgramMyoFinisherChoice(
                    session.dayIndex,index,exercise.exerciseId,
                    last.index,set.stableSetKey)
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
