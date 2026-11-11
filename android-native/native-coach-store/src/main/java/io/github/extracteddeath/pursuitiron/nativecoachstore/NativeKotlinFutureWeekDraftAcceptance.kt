package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.math.BigDecimal
import java.security.MessageDigest

/**
 * An explicit human-confirmed change to ONE next unissued custom-cycle week.
 * This saves a new unsigned draft revision, NEVER an approved workout,
 * issued session, workout history entry or automatically accepted coaching
 * adjustment. The athlete must re-review/sign the revised full cycle before
 * using its newly authored prescriptions.
 */
data class NativeFutureWeekDraftSelection(
    val ownerKey:String,
    val cycleId:String,
    val absoluteWeek:Int,
    val weekday:Int,
    val slotId:String,
    val readiness:CoachReadiness,
    val movementRestricted:Boolean,
    val maximumAuthorizedIncrease:BigDecimal,
    val forecast:CustomRepForecastPolicy=CustomRepForecastPolicy.TARGET_RIR_V2
)

enum class NativeFutureWeekDraftRefusal {
    CONFIRMATION_REQUIRED, INVALID_FINGERPRINT, NO_ELIGIBLE_CHANGE,
    SOURCE_OR_HISTORY_CHANGED, PREPARED_OR_ISSUED_WORKOUT,
    PROGRAM_SAVE_REJECTED, STORAGE_UNAVAILABLE
}

sealed interface NativeFutureWeekDraftPreview {
    data class Ready(
        val fingerprint:String,
        val proposal:ReviewedFutureWeekProgression,
        val currentCycleRevision:Long
    ):NativeFutureWeekDraftPreview {
        val maySaveAutomatically:Boolean get()=false
        val mayIssueWorkout:Boolean get()=false
    }
    data class Rejected(val reason:NativeFutureWeekDraftRefusal):
        NativeFutureWeekDraftPreview
}

sealed interface NativeFutureWeekDraftDecision {
    data class Saved(val revision:Long,val changedAbsoluteWeek:Int):
        NativeFutureWeekDraftDecision {
        val isSignedOff:Boolean get()=false
        val mayIssueWorkout:Boolean get()=false
        val mayChangeHistory:Boolean get()=false
    }
    data class Rejected(val reason:NativeFutureWeekDraftRefusal):
        NativeFutureWeekDraftDecision
}

/**
 * A mutation never trusts the earlier screen preview. Both preview and
 * acceptance rederive a candidate through NativeKotlinReviewedFutureWeekProposal,
 * including signed original cycle, full journal + correction history, live
 * gym/catalog, adaptation rights, settings and authoritative inventory.
 *
 * Acceptance holds the owner OS lock from fresh rederivation through the
 * one-record draft revision-CAS write. A new draft invalidates its old
 * content-bound signoff by definition. There is no partial multi-file write.
 */
class NativeKotlinFutureWeekDraftAcceptance(
    private val fence:NativeKotlinApprovalRevisionFence,
    private val proposals:NativeKotlinReviewedFutureWeekProposal,
    private val drafts:NativeCustomCycleDraftFileStore
) {
    private val digestFormat=Regex("^[a-f0-9]{64}$")

    companion object {
        internal fun eligible(result:NativeFutureWeekProposalResult.Reviewed):Boolean {
            val p=result.proposal
            // Cross-module public properties are not smart-castable.
            val candidate=p.candidate ?:return false
            return p.status==
                    ReviewedFutureWeekProgressionStatus.CANDIDATE_FOR_ATHLETE_REVIEW &&
                p.changedAbsoluteWeek!=null &&
                p.changedSlotId!=null &&
                candidate.revision==result.cycleRevision+1L
        }

        /** Complete content + all six mutable authority revisions. */
        internal fun fingerprint(
            selection:NativeFutureWeekDraftSelection,
            verified:NativeFutureWeekProposalResult.Reviewed
        ):String {
            require(eligible(verified))
            val candidate=checkNotNull(verified.proposal.candidate)
            val content=NativeCustomCycleDraftCodec.encode(candidate)
            val envelope=ByteArrayOutputStream()
            DataOutputStream(envelope).use{out->
                out.writeUTF("pursuit-native-future-week-draft-cas-v1")
                out.writeUTF(selection.ownerKey)
                out.writeUTF(selection.cycleId)
                out.writeInt(selection.absoluteWeek)
                out.writeInt(selection.weekday)
                out.writeUTF(selection.slotId)
                out.writeUTF(selection.readiness.name)
                out.writeBoolean(selection.movementRestricted)
                out.writeUTF(selection.maximumAuthorizedIncrease
                    .stripTrailingZeros().toPlainString())
                out.writeUTF(selection.forecast.name)
                out.writeLong(verified.cycleRevision)
                out.writeLong(verified.gymSourceRevision)
                out.writeLong(verified.historyManifestRevision)
                out.writeLong(verified.permissionRevision)
                out.writeLong(verified.consentRevision)
                out.writeLong(verified.assistanceRevision)
                out.writeInt(checkNotNull(verified.proposal.changedAbsoluteWeek))
                out.writeUTF(checkNotNull(verified.proposal.changedSlotId))
                out.writeInt(content.size)
                out.write(content)
            }
            return MessageDigest.getInstance("SHA-256")
                .digest(envelope.toByteArray())
                .joinToString(""){"%02x".format(it.toInt() and 255)}
        }
    }

    private fun evaluate(s:NativeFutureWeekDraftSelection):
        NativeFutureWeekDraftPreview {
        val r=proposals.review(
            s.ownerKey,s.cycleId,s.absoluteWeek,s.weekday,s.slotId,
            s.readiness,s.movementRestricted,s.maximumAuthorizedIncrease,s.forecast)
        val verified=r as? NativeFutureWeekProposalResult.Reviewed
            ?:return NativeFutureWeekDraftPreview.Rejected(
                if((r as? NativeFutureWeekProposalResult.Rejected)?.reason==
                    NativeFutureWeekProposalRefusal.FUTURE_WORKOUT_ALREADY_PREPARED)
                    NativeFutureWeekDraftRefusal.PREPARED_OR_ISSUED_WORKOUT
                else NativeFutureWeekDraftRefusal.SOURCE_OR_HISTORY_CHANGED)
        if(!eligible(verified))
            return NativeFutureWeekDraftPreview.Rejected(
                NativeFutureWeekDraftRefusal.NO_ELIGIBLE_CHANGE)
        return NativeFutureWeekDraftPreview.Ready(
            fingerprint(s,verified),verified.proposal,verified.cycleRevision)
    }

    fun preview(s:NativeFutureWeekDraftSelection):NativeFutureWeekDraftPreview =
        runCatching {
            fence.withOwnerTransaction(s.ownerKey){evaluate(s)}
        }.getOrElse{NativeFutureWeekDraftPreview.Rejected(
            NativeFutureWeekDraftRefusal.STORAGE_UNAVAILABLE)}

    /**
     * Called only after a separate confirmation click. The opaque review
     * fingerprint is compared to a fresh rederivation under the owner lock;
     * a changed source, correction, permission, current workout or program
     * revision cannot silently pass. A changed program is UN-SIGNED.
     */
    fun accept(
        s:NativeFutureWeekDraftSelection,
        shownFingerprint:String,
        athleteConfirmed:Boolean
    ):NativeFutureWeekDraftDecision {
        if(!athleteConfirmed)return NativeFutureWeekDraftDecision.Rejected(
            NativeFutureWeekDraftRefusal.CONFIRMATION_REQUIRED)
        if(!digestFormat.matches(shownFingerprint))
            return NativeFutureWeekDraftDecision.Rejected(
                NativeFutureWeekDraftRefusal.INVALID_FINGERPRINT)
        return runCatching {
            fence.withOwnerTransaction(s.ownerKey) {
                val now=evaluate(s)
                val ready=now as? NativeFutureWeekDraftPreview.Ready
                    ?:return@withOwnerTransaction NativeFutureWeekDraftDecision.Rejected(
                        (now as NativeFutureWeekDraftPreview.Rejected).reason)
                if(ready.fingerprint!=shownFingerprint)
                    return@withOwnerTransaction NativeFutureWeekDraftDecision.Rejected(
                        NativeFutureWeekDraftRefusal.SOURCE_OR_HISTORY_CHANGED)
                val revised=checkNotNull(ready.proposal.candidate)
                when(drafts.save(s.ownerKey,s.cycleId,ready.currentCycleRevision,
                    revised)) {
                    is NativeCustomDraftWriteResult.Committed ->
                        NativeFutureWeekDraftDecision.Saved(
                            revised.revision,
                            checkNotNull(ready.proposal.changedAbsoluteWeek))
                    else ->NativeFutureWeekDraftDecision.Rejected(
                        NativeFutureWeekDraftRefusal.PROGRAM_SAVE_REJECTED)
                }
            }
        }.getOrElse{NativeFutureWeekDraftDecision.Rejected(
            NativeFutureWeekDraftRefusal.STORAGE_UNAVAILABLE)}
    }
}
