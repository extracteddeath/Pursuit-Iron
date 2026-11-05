package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.util.Collections

/**
 * Projection only. Call from the exhaustive, owner-fenced history reader.
 * An athlete's MOST RECENT appearance is authoritative: never skip a custom
 * visit or an unsuccessful attempt to cherry-pick an earlier good session.
 */
enum class NativeCurrentRepPrescriptionRefusal {
    INVALID_SOURCE_OR_HISTORY,
    NO_RECORDED_EXERCISE,
    CUSTOM_WORKOUT_IS_LATEST,
    INCOMPARABLE_LATEST_WORKOUT,
    EXERCISE_SOURCE_CHANGED,
    SESSION_PURPOSE_CHANGED
}

sealed interface NativeCurrentRepPrescriptionResult {
    data class Rejected(
        val reason: NativeCurrentRepPrescriptionRefusal
    ) : NativeCurrentRepPrescriptionResult
    data class Reviewed(
        val historyRevision: Long,
        val priorWorkoutId: String,
        val proposal: AdaptiveSetPrescriptionResult
    ) : NativeCurrentRepPrescriptionResult {
        val canIssueWorkout:Boolean get()=false
        val canModifyHistory:Boolean get()=false
    }
}

object NativeKotlinCurrentRepPrescriptionDomain {
    fun inspect(
        approval:NativeKotlinWorkoutApproval,
        timeline:NativeKotlinMixedHistoryTimeline,
        exerciseId:String,
        readiness:CoachReadiness,
        movementRestricted:Boolean
    ):NativeCurrentRepPrescriptionResult {
        fun reject(reason:NativeCurrentRepPrescriptionRefusal)=
            NativeCurrentRepPrescriptionResult.Rejected(reason)
        val source=approval.approvedSource
        if(approval.currentSource!=source ||
            approval.grant.approvedSource!=source ||
            approval.athleteId!=source.athleteId ||
            timeline.athleteId!=source.athleteId ||
            timeline.manifestRevision<1 ||
            timeline.completions.size>KotlinHistoryCapacityPolicy.MAX_MANIFEST_COMPLETIONS ||
            timeline.completions.map {it.manifest.completionOrder} !=
                (1..timeline.completions.size).map {it.toLong()} ||
            timeline.completions.map {it.manifest.workoutId}.distinct().size !=
                timeline.completions.size)
            return reject(NativeCurrentRepPrescriptionRefusal.INVALID_SOURCE_OR_HISTORY)
        val appearances=timeline.completions.filter { completion ->
            when(completion) {
                is NativeKotlinMixedHistoryCompletion.Composer ->
                    completion.evidence.sets.any {it.exerciseId==exerciseId}
                is NativeKotlinMixedHistoryCompletion.Custom ->
                    completion.evidence.visits.any {it.exerciseId==exerciseId}
            }
        }
        val last=appearances.lastOrNull()
            ?:return reject(NativeCurrentRepPrescriptionRefusal.NO_RECORDED_EXERCISE)
        if(last !is NativeKotlinMixedHistoryCompletion.Composer)
            return reject(NativeCurrentRepPrescriptionRefusal.CUSTOM_WORKOUT_IS_LATEST)
        if(last.evidence.source!=source)
            return reject(NativeCurrentRepPrescriptionRefusal.EXERCISE_SOURCE_CHANGED)
        if(last.evidence.workoutId!=last.manifest.workoutId ||
            last.evidence.finishEventId!=last.manifest.finishEventId ||
            last.evidence.journalRevision!=last.manifest.originalJournalRevision)
            return reject(NativeCurrentRepPrescriptionRefusal.INVALID_SOURCE_OR_HISTORY)
        val original=last.evidence.exerciseReviews.singleOrNull {
            it.exerciseId==exerciseId
        }
        if(original==null ||
            original.progressionGate!=KotlinHistoryProgressionGate.COMPARABLE_FOR_REVIEW ||
            original.progressionWorkout==null ||
            original.workingSets.any {it.issuedLoad !is DraftSetLoad.Verified})
            return reject(NativeCurrentRepPrescriptionRefusal.INCOMPARABLE_LATEST_WORKOUT)
        val staged=KotlinWorkoutSnapshotDomain.stage(
            KotlinWorkoutSnapshotRequest(
                source,source,approval.grant,approval.program,approval.policies))
        val day=(staged as? KotlinWorkoutSnapshotResult.Staged)?.snapshot
            ?:return NativeCurrentRepPrescriptionResult.Reviewed(
                timeline.manifestRevision,last.manifest.workoutId,
                AdaptiveSetPrescriptionResult.Rejected(
                    AdaptiveSetPrescriptionRefusal.APPROVED_PROGRAM_UNRESOLVED,
                    "The newly approved workout cannot be staged safely."))
        if(last.purpose!=day.purpose ||
            last.purpose==ComposerSessionPurpose.UNSPECIFIED)
            return reject(NativeCurrentRepPrescriptionRefusal.SESSION_PURPOSE_CHANGED)

        val proposal=KotlinAdaptiveSetPrescriptionDomain.draft(
            AdaptiveSetPrescriptionRequest(
                KotlinWorkoutSnapshotRequest(
                    source,source,approval.grant,approval.program,approval.policies),
                last.evidence.source,original.progressionWorkout,
                timeline.manifestRevision,timeline.manifestRevision,
                readiness,movementRestricted))
        return NativeCurrentRepPrescriptionResult.Reviewed(
            timeline.manifestRevision,last.manifest.workoutId,proposal)
    }
}
