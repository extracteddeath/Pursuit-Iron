package io.github.extracteddeath.pursuitiron

/**
 * Owner-verifying Resume has its OWN read path. Waiting for an optional
 * custom/measured inventory or a time-budget preview cannot veto reopening
 * an already issued generated workout: those reads are not prerequisites or
 * authorities for the signed generated journal.
 *
 * We still serialize duplicate Resume taps and competing manual navigation.
 * The handler independently checks the current approval and unfinished
 * workout on disk before entering Train; this is presentation gating ONLY.
 */
internal object NativeKotlinTrainResumeGate {
    fun mayVerifyGenerated(
        resumeChecking:Boolean,
        manualLookupBusy:Boolean,
        anotherWorkoutOpen:Boolean,
        timeAcceptanceBusy:Boolean=false
    ):Boolean = !resumeChecking && !manualLookupBusy && !anotherWorkoutOpen &&
        !timeAcceptanceBusy
}
