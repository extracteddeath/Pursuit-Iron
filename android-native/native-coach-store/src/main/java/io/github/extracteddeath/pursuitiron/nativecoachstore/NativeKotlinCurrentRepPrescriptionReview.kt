package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.util.Collections

/**
 * Projection only. Call from the exhaustive, owner-fenced history reader.
 * For the NEXT approved training day and session purpose, use the most
 * recent matching occurrence from the entire authenticated history.
 * Interleaved workouts on other days cannot advance this day's reps.
 * Never skip an intervening custom workout or changed source to
 * cherry-pick an old success.
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
        movementRestricted:Boolean,
        /** Old accepted receipts replay their original policy, never re-score history. */
        policyVersion:AdaptiveRepPolicyVersion=AdaptiveRepPolicyVersion.TARGET_RIR_V2,
        /** New, opt-in recovery review; false preserves legacy and manual policy. */
        enableRepeatedFatigueReview:Boolean=false
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
        // A workout's immutable approved day identity determines which
        // evidence may advance its reps. Authored day-specific load policy
        // is optional and never grants permission to borrow another day.
        val last=appearances.filterIsInstance<
            NativeKotlinMixedHistoryCompletion.Composer>().lastOrNull {
                it.purpose==day.purpose && it.dayIndex==day.dayIndex
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
        val prescription=CoachSessionPrescriptionDomain.selectedPolicy(
            approval.policies,exerciseId,day.purpose,day.dayIndex)
            ?:return reject(NativeCurrentRepPrescriptionRefusal.INCOMPARABLE_LATEST_WORKOUT)
        val reviewedBackoff=prescription.scheme==SetLoadScheme.LAST_SET_BACKOFF &&
            prescription.backoffReps==prescription.workingReps &&
            prescription.backoffRir==prescription.targetRir
        if(prescription.scheme!=SetLoadScheme.STRAIGHT && !reviewedBackoff)
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
        // Only the issued working sets inform rep progression. Warmups
        // still require exact authenticated source/target matching.
        if(!NativeKotlinWarmupEvidenceGate.matchesIssued(
                day,last.evidence,exerciseId))
            return reject(NativeCurrentRepPrescriptionRefusal.INCOMPARABLE_LATEST_WORKOUT)
        // No transfer from other days or legacy exercise-wide defaults.
        // The FULL currently approved next day supplies the original stable
        // working-set load of EVERY ordinal. Both the historical issuance
        // and exact equipment stock must independently match it.
        val verified=working.firstOrNull()?.load as? DraftSetLoad.Verified
            ?:return reject(NativeCurrentRepPrescriptionRefusal.INCOMPARABLE_LATEST_WORKOUT)
        val perSet=working.map {it.load as? DraftSetLoad.Verified
            ?:return reject(NativeCurrentRepPrescriptionRefusal.INCOMPARABLE_LATEST_WORKOUT)}
        if(perSet.any{
                it.unit!=verified.unit || it.basis!=verified.basis
            } || (!reviewedBackoff && perSet.any{
                it.value.compareTo(verified.value)!=0
            }) || (reviewedBackoff && (
                perSet.size<2 ||
                    perSet.dropLast(1).any{
                        it.value.compareTo(verified.value)!=0
                    } || perSet.last().value>=verified.value)))
            return reject(NativeCurrentRepPrescriptionRefusal.INCOMPARABLE_LATEST_WORKOUT)
        val normalized=KotlinLongitudinalRepEvidenceDomain.review(
            original,last.evidence.workoutId,prescription.workingReps,
            working.size,verified.value,inventory,
            equipment.unit,inventory.basis,prescription.targetRir,
            if(reviewedBackoff)perSet.map{it.value} else null)
            as? LongitudinalRepEvidenceResult.Comparable
            ?:return reject(NativeCurrentRepPrescriptionRefusal.INCOMPARABLE_LATEST_WORKOUT)
        val actualEvidence=normalized.session

        // The exercise coach already recognizes repeated intra-session
        // deterioration. Apply the SAME verified observation to REAL next
        // rep prescriptions, but only while fatigue advice is enabled.
        // Never skip a same-day occurrence just to find three good workouts,
        // and never borrow capacity from another weekday or phase.
        // This is a review/hold, not a diagnosed recovery problem or deload.
        if(enableRepeatedFatigueReview &&
            policyVersion==AdaptiveRepPolicyVersion.TARGET_RIR_V2) {
            val sameDay=appearances.filterIsInstance<
                NativeKotlinMixedHistoryCompletion.Composer>().filter {
                it.dayIndex==day.dayIndex && it.purpose==day.purpose
            }.takeLast(3)
            if(sameDay.size==3 && sameDay.all{
                    it.evidence.source==source &&
                        NativeKotlinWarmupEvidenceGate.matchesIssued(
                            day,it.evidence,exerciseId)
                }) {
                val observations=sameDay.map{completed->
                    completed.evidence.exerciseReviews.singleOrNull{
                        it.exerciseId==exerciseId
                    }?.progressionWorkout
                }
                if(observations.all{it!=null}) {
                    val trend=SessionFatigueEvidenceDomain.inspectRecent(
                        observations.filterNotNull(),inventory,
                        verifiedPerSetLoading=reviewedBackoff)
                    val signal=trend?.signal
                    if(signal==RepeatedSessionFatigueSignal.BROAD_DECLINE ||
                        signal==RepeatedSessionFatigueSignal.LATER_SET_DECLINE) {
                        val detail=if(signal==RepeatedSessionFatigueSignal.BROAD_DECLINE)
                            "Three comparable same-day workouts show declining effort-aligned reps across most sets including the first. Review recovery, training demands and loads before approving higher rep targets; this is not a fatigue diagnosis."
                        else
                            "Three comparable same-day workouts show stable first-set performance but repeated later-set decline. Review actual rest, exercise order and within-workout fatigue before increasing later-set targets."
                        return NativeCurrentRepPrescriptionResult.Reviewed(
                            timeline.manifestRevision,last.manifest.workoutId,
                            AdaptiveSetPrescriptionResult.Rejected(
                                AdaptiveSetPrescriptionRefusal.INSUFFICIENT_REP_PROGRESS_EVIDENCE,
                                detail))
                    }
                }
            }
        }

        val proposal=KotlinAdaptiveSetPrescriptionDomain.draft(
            AdaptiveSetPrescriptionRequest(
                KotlinWorkoutSnapshotRequest(
                    source,source,approval.grant,approval.program,approval.policies),
                last.evidence.source,actualEvidence,
                timeline.manifestRevision,timeline.manifestRevision,
                readiness,movementRestricted,
                if(normalized.normalizedPersonalizedTargets)
                    normalized.exactIssuedReps.map { it.minimum }
                else null,policyVersion))
        return NativeCurrentRepPrescriptionResult.Reviewed(
            timeline.manifestRevision,last.manifest.workoutId,proposal)
    }
}
