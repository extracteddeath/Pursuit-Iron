package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.util.Collections

/**
 * Projection only. Call from the exhaustive, owner-fenced history reader.
 * For the NEXT approved session purpose, use the most recent matching
 * occurrence from the entire authenticated history. Interleaved workouts
 * for other purposes must not erase that history. Never skip an intervening
 * custom workout or changed source to cherry-pick an old success.
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
        val mostRecent=appearances.lastOrNull()
            ?:return reject(NativeCurrentRepPrescriptionRefusal.NO_RECORDED_EXERCISE)
        if(mostRecent is NativeKotlinMixedHistoryCompletion.Custom)
            return reject(NativeCurrentRepPrescriptionRefusal.CUSTOM_WORKOUT_IS_LATEST)
        val staged=KotlinWorkoutSnapshotDomain.stage(
            KotlinWorkoutSnapshotRequest(
                source,source,approval.grant,approval.program,approval.policies))
        val day=(staged as? KotlinWorkoutSnapshotResult.Staged)?.snapshot
            ?:return NativeCurrentRepPrescriptionResult.Reviewed(
                timeline.manifestRevision,mostRecent.manifest.workoutId,
                AdaptiveSetPrescriptionResult.Rejected(
                    AdaptiveSetPrescriptionRefusal.APPROVED_PROGRAM_UNRESOLVED,
                    "The newly approved workout cannot be staged safely."))
        if(day.purpose==ComposerSessionPurpose.UNSPECIFIED)
            return reject(NativeCurrentRepPrescriptionRefusal.SESSION_PURPOSE_CHANGED)
        val last=appearances.filterIsInstance<
            NativeKotlinMixedHistoryCompletion.Composer>().lastOrNull {
                it.purpose==day.purpose
            } ?:return reject(NativeCurrentRepPrescriptionRefusal.SESSION_PURPOSE_CHANGED)
        // Validate the whole suffix after the matching workout, not just
        // the selected event. A custom appearance or changed program/gym
        // revision cannot be bypassed by a purpose-specific review.
        val suffix=appearances.dropWhile {it!==last}
        if(suffix.any {it is NativeKotlinMixedHistoryCompletion.Custom})
            return reject(NativeCurrentRepPrescriptionRefusal.CUSTOM_WORKOUT_IS_LATEST)
        if(suffix.any {
            it is NativeKotlinMixedHistoryCompletion.Composer &&
                it.evidence.source!=source
        })
            return reject(NativeCurrentRepPrescriptionRefusal.EXERCISE_SOURCE_CHANGED)
        if(suffix.any {
            it is NativeKotlinMixedHistoryCompletion.Composer &&
                (it.evidence.workoutId!=it.manifest.workoutId ||
                 it.evidence.finishEventId!=it.manifest.finishEventId ||
                 it.evidence.journalRevision!=it.manifest.originalJournalRevision)
        })
            return reject(NativeCurrentRepPrescriptionRefusal.INVALID_SOURCE_OR_HISTORY)
        val original=last.evidence.exerciseReviews.singleOrNull {
            it.exerciseId==exerciseId
        }
        if(original==null ||
            original.workingSets.any {it.issuedLoad !is DraftSetLoad.Verified})
            return reject(NativeCurrentRepPrescriptionRefusal.INCOMPARABLE_LATEST_WORKOUT)
        val prescription=approval.policies.singleOrNull {
            it.exerciseId==exerciseId && it.purpose==day.purpose
        } ?:return reject(NativeCurrentRepPrescriptionRefusal.INCOMPARABLE_LATEST_WORKOUT)
        if(prescription.scheme!=SetLoadScheme.STRAIGHT ||
            prescription.warmups.isNotEmpty() ||
            last.evidence.sets.any {
                it.exerciseId==exerciseId && it.type==DraftSetType.WARMUP
            })
            return reject(NativeCurrentRepPrescriptionRefusal.INCOMPARABLE_LATEST_WORKOUT)
        val equipment=(approval.program.exercises.singleOrNull {
            it.exerciseId==exerciseId
        }?.equipment as? ComposerEquipment.Verified)
            ?:return reject(NativeCurrentRepPrescriptionRefusal.INCOMPARABLE_LATEST_WORKOUT)
        val inventory=equipment.inventory
            ?:return reject(NativeCurrentRepPrescriptionRefusal.INCOMPARABLE_LATEST_WORKOUT)
        val working=day.targets.filter {
            it.exerciseId==exerciseId && it.type==DraftSetType.WORKING
        }
        // Never substitute the exercise-wide default for a session-specific
        // issued load. All-set straight progression requires an EXACT,
        // uniform, stock-verified working weight for the requested purpose.
        val verified=working.firstOrNull()?.load as? DraftSetLoad.Verified
            ?:return reject(NativeCurrentRepPrescriptionRefusal.INCOMPARABLE_LATEST_WORKOUT)
        if(working.any {
            val actual=it.load as? DraftSetLoad.Verified
            actual==null || actual.unit!=verified.unit ||
                actual.basis!=verified.basis ||
                actual.value.compareTo(verified.value)!=0
        })
            return reject(NativeCurrentRepPrescriptionRefusal.INCOMPARABLE_LATEST_WORKOUT)
        val normalized=KotlinLongitudinalRepEvidenceDomain.review(
            original,last.evidence.workoutId,prescription.workingReps,
            working.size,verified.value,inventory,
            equipment.unit,inventory.basis,prescription.targetRir)
            as? LongitudinalRepEvidenceResult.Comparable
            ?:return reject(NativeCurrentRepPrescriptionRefusal.INCOMPARABLE_LATEST_WORKOUT)
        val actualEvidence=normalized.session

        val proposal=KotlinAdaptiveSetPrescriptionDomain.draft(
            AdaptiveSetPrescriptionRequest(
                KotlinWorkoutSnapshotRequest(
                    source,source,approval.grant,approval.program,approval.policies),
                last.evidence.source,actualEvidence,
                timeline.manifestRevision,timeline.manifestRevision,
                readiness,movementRestricted,
                if(normalized.normalizedPersonalizedTargets)
                    normalized.exactIssuedReps.map { it.minimum }
                else null))
        return NativeCurrentRepPrescriptionResult.Reviewed(
            timeline.manifestRevision,last.manifest.workoutId,proposal)
    }
}
