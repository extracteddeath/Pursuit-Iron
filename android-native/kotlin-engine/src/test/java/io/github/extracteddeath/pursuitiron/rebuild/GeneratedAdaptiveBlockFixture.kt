package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal

/** Reviewed, explicit athlete/program fixture; no inferred muscle thresholds. */
internal object GeneratedAdaptiveBlockFixture {
    fun n(s:String)=BigDecimal(s)
    private val gym=GymProfileDraft("gym-1",LoadUnit.LB,
        mapOf("dumbbells" to VerifiedLoadStock.Totals(
            LoadUnit.LB,LoadBasis.DUMBBELL_PER_HAND,
            listOf(n("20"),n("25"),n("30")))),
        emptySet(),emptySet())
    fun athlete()=CoachAthleteSetupDomain.review(AthleteSetupDraft(
        profileRevision="rev-1",athleteProfileId="athlete-1",
        goal=AthleteTrainingGoal.POWERBUILDING,
        experience=AthleteExperience.ADVANCED,
        split=ComposerSplit.FULL_BODY,trainingWeekdays=setOf(0,2,4),
        timezoneId="America/New_York",
        sessionTimeBucket=SessionTimeBucket.FORTY_TO_SIXTY,
        hardSessionLimit=HardSessionLimit.NO_HARD_LIMIT,gym=gym,
        allowBodyweight=false,allowSupersets=false,
        allowTricepsOnLower=false,maxBarbellExercisesPerDay=0,
        focusedMuscles=emptySet(),reducedMuscles=emptySet(),
        excludedExerciseIds=emptySet(),lockedTrainingDays=emptySet(),
        hardTrainingDays=emptySet(),longTrainingDays=emptySet(),
        declaredRestDays=setOf(1,3,5,6)))
    private fun move(id:String,muscle:TrainingMuscle,
        pattern:ComposerPattern,role:ComposerRole=ComposerRole.SECONDARY)=
        CatalogMovement(id,id,"dumbbell",null,pattern,role,
            ComposerFocus.entries.toSet(),"dumbbells",
            LoadBasis.DUMBBELL_PER_HAND,
            listOf(DeclaredMuscleCredit(muscle,MuscleCreditRole.DIRECT,n("1"))),
            CatalogCreditStatus.AUTHOR_REVIEWED_PROVISIONAL,"credit-v1")
    val movements=listOf(
        move("bench",TrainingMuscle.CHEST,ComposerPattern.HORIZONTAL_PRESS,
            ComposerRole.PRIMARY),
        move("row",TrainingMuscle.LATS,ComposerPattern.HORIZONTAL_PULL),
        move("squat",TrainingMuscle.QUADS,ComposerPattern.SQUAT))
    fun generation():AthleteProgramGenerationRequest {
        val bounds=TrainingMuscle.entries.map {muscle->
            val active=muscle in setOf(TrainingMuscle.CHEST,
                TrainingMuscle.LATS,TrainingMuscle.QUADS)
            DeclaredMuscleDoseBounds(muscle,
                n(if(active)"2" else "0"),
                n(if(active)"5" else "0"),
                n(if(active)"10" else "0"),
                if(active)1 else 0)
        }
        val policy=AuthoredGoalProgramPolicy(
            "policy-1","rev-1","Reviewed authored phase dose",
            "athlete-1","rev-1",AthleteTrainingGoal.POWERBUILDING,
            AthleteExperience.ADVANCED,ComposerSplit.FULL_BODY,
            TrainingPhase.HYPERTROPHY,3,bounds,bounds,
            mapOf(ComposerSessionPurpose.HYPERTROPHY to 3))
        return AthleteProgramGenerationRequest(
            athlete(),TrainingPhase.HYPERTROPHY,policy,
            movements,movements.map {
                CatalogExerciseChoice(it.exerciseId,n("25"),90,120,
                    2,4,3,1,1,5)
            },mapOf(0 to 1200,2 to 1200,4 to 1200),3,1)
    }
    fun policies()=movements.map {
        ExercisePrescriptionPolicy(it.exerciseId,
            ComposerSessionPurpose.HYPERTROPHY,RepBand(8,12),2,
            90,120,SetLoadScheme.STRAIGHT)
    }
    private fun oldSet()=CustomAuthoredSet(
        DraftSetType.WORKING,
        DraftSetLoad.Verified(LoadUnit.LB,LoadBasis.DUMBBELL_PER_HAND,n("25")),
        RepBand(8,12),2,90,120)
    fun cycle()=CustomTrainingCycle(
        "cycle-1",4,"athlete-1",AthleteTrainingGoal.POWERBUILDING,
        "Future training cycle",listOf(CustomTrainingBlock(
            "block-1","Next block",TrainingPhase.HYPERTROPHY,
            CustomBlockPurpose.TRAINING,3,
            CustomSplitIdentity.Named(ComposerSplit.FULL_BODY),
            listOf(0,2,4).map {weekday->
                CustomTrainingDay(weekday,"Day "+weekday,
                    ComposerSessionPurpose.HYPERTROPHY,
                    movements.map {movement->
                        CustomExerciseSlot("old_"+movement.exerciseId,
                            movement.exerciseId,movement.role,
                            listOf(oldSet()),pinned=false)
                    })
            })))
    fun request(
        original:CustomTrainingCycle=cycle(),
        prescriptions:List<ExercisePrescriptionPolicy> =policies()
    )=GeneratedAdaptiveBlockRequest(original,"block-1","option1",
        generation(),prescriptions)
}
