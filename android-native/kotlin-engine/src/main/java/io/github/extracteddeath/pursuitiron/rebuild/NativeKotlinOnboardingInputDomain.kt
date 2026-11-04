package io.github.extracteddeath.pursuitiron.rebuild

/**
 * An explicit, complete athlete-provided collection of onboarding facts.
 * Optional facts stay unknown until the athlete confirms them. In particular
 * no workout weekday, session duration, hard ceiling, supplement of gym stock,
 * or bodyweight/superset preference is inferred by the native app.
 */
data class NativeKotlinOnboardingInput(
    val profileRevision: String?,
    val athleteId: String?,
    val goal: AthleteTrainingGoal?,
    val experience: AthleteExperience?,
    val split: ComposerSplit?,
    val trainingWeekdays: Set<Int>?,
    val timezoneId: String?,
    val sessionTimeBucket: SessionTimeBucket?,
    val hardSessionLimit: HardSessionLimit?,
    val gymId: String?,
    val unit: LoadUnit?,
    val confirmedStock: Map<String, VerifiedLoadStock>?,
    val confirmedFacilities: Set<String>?,
    val confirmedAttachments: Set<String>?,
    val allowBodyweight: Boolean?,
    val allowSupersets: Boolean?,
    val allowTricepsOnLower: Boolean?,
    val maxBarbellExercisesPerDay: Int?,
    val focusedMuscles: Set<TrainingMuscle>?,
    val reducedMuscles: Set<TrainingMuscle>?,
    val excludedExercises: Set<String>?,
    val lockedDays: Set<Int>?,
    val hardDays: Set<Int>?,
    val longDays: Set<Int>?,
    val declaredRestDays: Set<Int>?
) {
    fun toDraft(): AthleteSetupDraft = AthleteSetupDraft(
        profileRevision=profileRevision,athleteProfileId=athleteId,
        goal=goal,experience=experience,split=split,
        trainingWeekdays=trainingWeekdays,timezoneId=timezoneId,
        sessionTimeBucket=sessionTimeBucket,hardSessionLimit=hardSessionLimit,
        gym=GymProfileDraft(gymId,unit,confirmedStock,
            confirmedFacilities,confirmedAttachments),
        allowBodyweight=allowBodyweight,allowSupersets=allowSupersets,
        allowTricepsOnLower=allowTricepsOnLower,
        maxBarbellExercisesPerDay=maxBarbellExercisesPerDay,
        focusedMuscles=focusedMuscles,reducedMuscles=reducedMuscles,
        excludedExerciseIds=excludedExercises,
        lockedTrainingDays=lockedDays,
        hardTrainingDays=hardDays,longTrainingDays=longDays,
        declaredRestDays=declaredRestDays
    )
}

object NativeKotlinOnboardingInputDomain {
    /** Delegates all validity and cross-constraint decisions to Kotlin. */
    fun review(input: NativeKotlinOnboardingInput): AthleteSetupReview =
        CoachAthleteSetupDomain.review(input.toDraft())

    /** Explicit opt-in: choosing all unselected weekdays as rest is never
     * an invisible default or a permanently hardcoded week structure. */
    fun declareRestDays(
        selectedTrainingDays: Set<Int>?,
        athleteConfirmedComplement: Boolean
    ): Set<Int>? =
        if (athleteConfirmedComplement && selectedTrainingDays!=null &&
            selectedTrainingDays.all { it in 0..6 })
            (0..6).filterNot { it in selectedTrainingDays }.toSet()
        else null
}
