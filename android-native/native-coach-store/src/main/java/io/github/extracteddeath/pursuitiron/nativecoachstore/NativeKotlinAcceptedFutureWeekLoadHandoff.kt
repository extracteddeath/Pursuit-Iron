package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.math.BigDecimal

/**
 * The only native bridge from an accepted, audited future-week option to a
 * changed session preview. This is NOT preparation, workout issuance, or a
 * cycle edit. An issuer may use it only inside the same owner transaction
 * and MUST separately authenticate the prepared snapshot and provenance.
 *
 * Do not expose VerifiedLoadStock as an input from UI code: the current
 * native reviewer recomputes it from the athlete's approved gym and actual
 * corrected workout receipts under the owner lock.
 */
sealed interface NativeAcceptedFutureWeekLoadHandoff {
    data class Ready(
        val original:CustomWorkoutDaySnapshot,
        val projected:CustomWorkoutDaySnapshot,
        val slotId:String,
        val fingerprint:String,
        val recordRevision:Long
    ):NativeAcceptedFutureWeekLoadHandoff {
        val mayIssueWorkout:Boolean get()=false
        val mayModifyTrainingCycle:Boolean get()=false
        val mayModifyHistory:Boolean get()=false
    }
    data class Rejected(val reason:NativeAcceptedFutureWeekLoadRefusal):
        NativeAcceptedFutureWeekLoadHandoff
}
enum class NativeAcceptedFutureWeekLoadRefusal {
    INVALID_SELECTION, CONSENT_MISSING_OR_REVOKED,
    STALE_CONSENT_OR_REVIEW, SOURCE_CHANGED, EQUIPMENT_UNVERIFIED,
    AUDITED_OPTION_MISMATCH, STORAGE_UNAVAILABLE
}

class NativeKotlinAcceptedFutureWeekLoadHandoff(
    private val fence:NativeKotlinApprovalRevisionFence,
    private val consents:NativeKotlinFutureWeekLoadConsentFileStore,
    private val future:NativeKotlinCustomFutureWeekLoadOptionReview,
    private val nextReviewer:NativeKotlinCustomNextWorkoutReview,
    private val activation:NativeKotlinFutureActivationCommitTransaction
) {
    private val id=Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,79}$")
    private fun same(a:BigDecimal,b:BigDecimal)=a.compareTo(b)==0

    fun preview(
        ownerKey:String,cycleId:String,blockId:String,
        coaching:CustomCoachAuditInputs,
        absoluteWeek:Int,weekday:Int,slotId:String
    ):NativeAcceptedFutureWeekLoadHandoff {
        fun no(r:NativeAcceptedFutureWeekLoadRefusal)=
            NativeAcceptedFutureWeekLoadHandoff.Rejected(r)
        if(listOf(ownerKey,cycleId,blockId,slotId).any{!id.matches(it)}||
            absoluteWeek !in 1..104||weekday !in 0..6)
            return no(NativeAcceptedFutureWeekLoadRefusal.INVALID_SELECTION)
        return runCatching{fence.withOwnerTransaction(ownerKey){
            val accepted=(consents.readCurrent(ownerKey,cycleId,blockId,
                coaching,absoluteWeek,weekday,slotId)
                as? NativeFutureWeekConsentRead.Current)?.receipt
                ?:return@withOwnerTransaction no(
                    NativeAcceptedFutureWeekLoadRefusal.CONSENT_MISSING_OR_REVOKED)
            val earned=(future.review(ownerKey,coaching,absoluteWeek,weekday,
                slotId,accepted.readiness,accepted.movementRestricted,
                accepted.maximumAuthorizedIncrease)
                as? NativeFutureWeekLoadOptionResult.Ready)
                ?:return@withOwnerTransaction no(
                    NativeAcceptedFutureWeekLoadRefusal.STALE_CONSENT_OR_REVIEW)
            if(earned.reviewFingerprint!=accepted.reviewFingerprint ||
                earned.historyRevision!=accepted.historyRevision ||
                earned.draftRevision!=accepted.draftRevision ||
                earned.sourceRevision!=accepted.sourceRevision ||
                earned.settingsRevision!=accepted.settingsRevision ||
                earned.permissionRevision!=accepted.permissionRevision ||
                earned.affectedAbsoluteWeek!=absoluteWeek ||
                earned.affectedWeekday!=weekday ||
                earned.affectedSlotId!=slotId ||
                !same(earned.previousLoad,accepted.previousLoad) ||
                !same(earned.candidateLoad,accepted.proposedLoad) ||
                earned.option.proposedCycle.cycleId!=cycleId)
                return@withOwnerTransaction no(
                    NativeAcceptedFutureWeekLoadRefusal.STALE_CONSENT_OR_REVIEW)
            val rechecked=(nextReviewer.review(ownerKey,coaching,absoluteWeek,
                weekday,slotId,accepted.readiness,
                accepted.movementRestricted,accepted.maximumAuthorizedIncrease)
                as? NativeCustomNextCoachResult.Reviewed)
                ?:return@withOwnerTransaction no(
                    NativeAcceptedFutureWeekLoadRefusal.EQUIPMENT_UNVERIFIED)
            if(rechecked.historyRevision!=accepted.historyRevision ||
                rechecked.verifiedStock==null ||
                !same(rechecked.review.loadReviewCandidate
                    ?:BigDecimal.ZERO,accepted.proposedLoad))
                return@withOwnerTransaction no(
                    NativeAcceptedFutureWeekLoadRefusal.EQUIPMENT_UNVERIFIED)
            val source=(activation.previewActivatedWorkout(ownerKey,coaching,
                absoluteWeek,weekday) as? CustomWorkoutSnapshotResult.Preview)
                ?.snapshot ?:return@withOwnerTransaction no(
                    NativeAcceptedFutureWeekLoadRefusal.SOURCE_CHANGED)
            val projected=NativeFutureWeekLoadSessionProjection.preview(
                source,NativeFutureWeekConsentRead.Current(accepted),
                rechecked.verifiedStock)
                as? NativeFutureWeekLoadProjection.Ready
                ?:return@withOwnerTransaction no(
                    NativeAcceptedFutureWeekLoadRefusal.SOURCE_CHANGED)

            // Prove the displayed sets are EXACTLY the single slot override
            // that passed the full native, mixed-context cycle audit. This
            // prevents a projection implementation drift from laundering a
            // new unreviewed prescription into workout preparation.
            val proposed=earned.option.proposedCycle
            val block=proposed.blocks.singleOrNull{it.blockId==blockId}
                ?:return@withOwnerTransaction no(
                    NativeAcceptedFutureWeekLoadRefusal.AUDITED_OPTION_MISMATCH)
            val expected=proposed.weekOverrides[blockId].orEmpty().singleOrNull{
                it.weekInBlock==source.weekInBlock &&
                    it.weekday==weekday && it.slotId==slotId
            } ?:return@withOwnerTransaction no(
                NativeAcceptedFutureWeekLoadRefusal.AUDITED_OPTION_MISMATCH)
            val changed=projected.preview.slots.singleOrNull{
                it.slotId==slotId
            } ?:return@withOwnerTransaction no(
                NativeAcceptedFutureWeekLoadRefusal.AUDITED_OPTION_MISMATCH)
            if(source.blockId!=block.blockId ||
                expected.sets!=changed.sets.map{it.prescription} ||
                proposed.weekOverrides[blockId].orEmpty().size!=1 ||
                projected.original!=source ||
                projected.preview.slots.size!=source.slots.size)
                return@withOwnerTransaction no(
                    NativeAcceptedFutureWeekLoadRefusal.AUDITED_OPTION_MISMATCH)
            NativeAcceptedFutureWeekLoadHandoff.Ready(
                source,projected.preview,slotId,earned.reviewFingerprint,
                accepted.recordRevision)
        }}.getOrElse{
            no(NativeAcceptedFutureWeekLoadRefusal.STORAGE_UNAVAILABLE)
        }
    }
}
