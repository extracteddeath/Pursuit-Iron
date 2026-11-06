package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.util.Collections

/**
 * The exact SOURCE payload a future activation would need to persist.
 *
 * This is deliberately NOT an activation transaction. It creates no file,
 * cannot change a user's approved source, and never issues a workout.
 * A later owner-fenced transaction must re-read and consume current consent,
 * verified history, both outstanding inventories, the cycle and policy,
 * then atomically commit/recover its source + policy transition.
 */
data class NativeFutureActivationSourcePlan(
    val proposedSource:NativeCustomAuthoringSnapshot,
    val targetBlockId:String,
    val firstWeek:Int,
    val historyManifestRevision:Long,
    val targetPolicy:AuthoredGoalProgramPolicy,
    val nonSetSecondsByWeekday:Map<Int,Int>
) {
    val mayActivate:Boolean get()=false
    val mayIssueWorkout:Boolean get()=false
}

internal object NativeFutureActivationSourcePlanDomain {
    private const val MAX_REVISION=9_007_199_254_740_991L

    fun prepare(
        source:NativeCustomAuthoringSnapshot,
        accepted:NativeFutureBlockContextReceipt,
        boundary:NativeFutureActivationReview.BoundaryReviewed
    ):NativeFutureActivationSourcePlan? {
        val current=source.setup.reviewed ?:return null
        val future=accepted.future.setup.reviewed ?:return null
        if(source.setup.status!=AthleteSetupStatus.READY_FOR_PROGRAM_REVIEW ||
            accepted.future.setup.status!=AthleteSetupStatus.READY_FOR_PROGRAM_REVIEW ||
            source.ownerKey!=accepted.ownerKey ||
            source.athleteId!=accepted.athleteId ||
            current.athleteProfileId!=source.athleteId ||
            future.athleteProfileId!=source.athleteId ||
            source.revision!=accepted.sourceRevision ||
            source.revision !in 1 until MAX_REVISION ||
            accepted.draftRevision !in 1..MAX_REVISION ||
            accepted.blockId!=boundary.blockId ||
            accepted.cycleId!=boundary.cycleId ||
            boundary.firstWeek !in 2..104 ||
            boundary.precedingWeeks!=boundary.firstWeek-1 ||
            boundary.historyManifestRevision<1 ||
            boundary.verifiedPriorSessions<boundary.precedingWeeks)
            return null

        // Only the future split/calendar may differ. Any gym, load,
        // experience, goal, limits, muscles, equipment or injury/exclusion
        // changes require their own complete source and policy review.
        if(future.copy(
                split=current.split,
                trainingWeekdays=current.trainingWeekdays,
                lockedTrainingDays=current.lockedTrainingDays,
                hardTrainingDays=current.hardTrainingDays,
                longTrainingDays=current.longTrainingDays,
                declaredRestDays=current.declaredRestDays)!=current)
            return null

        val policy=accepted.future.policy
        val overhead=accepted.future.nonSetSecondsByWeekday
        if(policy.athleteId!=future.athleteProfileId ||
            policy.athleteRevision!=future.profileRevision ||
            policy.goal!=future.goal || policy.experience!=future.experience ||
            policy.split!=future.split ||
            policy.trainingDays!=future.trainingWeekdays.size ||
            overhead.keys!=future.trainingWeekdays ||
            overhead.values.any{it !in 0..21600})
            return null

        val proposed=source.copy(revision=source.revision+1,
            setup=accepted.future.setup)
        // Exercise a REAL durable source codec before showing the candidate.
        // No made-up gym stock or unpersistable athlete profile may pass.
        if(!runCatching {
            NativeKotlinCustomAuthoringSourceCodec.decode(
                NativeKotlinCustomAuthoringSourceCodec.encode(proposed))==proposed
        }.getOrDefault(false))return null
        return NativeFutureActivationSourcePlan(
            proposed,accepted.blockId,boundary.firstWeek,
            boundary.historyManifestRevision,policy,
            Collections.unmodifiableMap(LinkedHashMap(overhead)))
    }
}
