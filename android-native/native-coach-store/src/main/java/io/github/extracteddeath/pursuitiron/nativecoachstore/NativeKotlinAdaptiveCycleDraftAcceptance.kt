package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.security.MessageDigest
import java.util.Collections

/**
 * Athlete-reviewed candidate acceptance for an EXISTING custom-cycle draft.
 *
 * The current gym/profile is the only source of authority in this slice.
 * A proposed future schedule requiring different athlete setup or a future
 * phase policy remains preview-only until those changes have their own
 * coordinated, versioned native approval. This writer never updates workout
 * grants, signoffs, journals, historical observations or active sessions.
 *
 * Preview and acceptance both read and audit the exact full cycle under the
 * same owner fence as draft/source/signoff stores. Caller-provided source,
 * cached badges, or an "approved" boolean cannot replace these reads.
 */
data class NativeAdaptiveCycleDraftCandidate(
    val ownerKey:String,
    val cycleId:String,
    val targetBlockId:String,
    val optionId:String,
    val expectedDraftRevision:Long,
    val expectedSourceRevision:Long,
    val proposedCycle:CustomTrainingCycle,
    val coaching:CustomCoachAuditInputs,
    val coachPermissions:CustomCycleCoachPermissions,
    val expectedPermissionRevision:Long
)

enum class NativeAdaptiveCycleDraftRefusal {
    INVALID_REQUEST, NOT_AUTHORIZED, DRAFT_MISSING, SOURCE_MISSING,
    DRAFT_CHANGED, SOURCE_CHANGED, UNRELATED_BLOCK_CHANGED,
    FUTURE_PROFILE_REQUIRES_APPROVAL, COACH_REVIEW_UNRESOLVED,
    PERMISSION_REQUIRED, PERMISSIONS_NOT_APPROVED, PERMISSIONS_CHANGED,
    CONFIRMATION_REQUIRED, REVIEW_CHANGED,
    DRAFT_STORAGE_REJECTED, STORAGE_UNAVAILABLE
}
sealed interface NativeAdaptiveCycleDraftPreview {
    data class Ready(
        val optionId:String,
        val reviewedFingerprint:String,
        val changes:List<CustomCycleChange>,
        val currentDraftRevision:Long,
        val currentSourceRevision:Long
    ):NativeAdaptiveCycleDraftPreview {
        val mayIssueWorkout:Boolean get()=false
        val mayApplyAutomatically:Boolean get()=false
    }
    data class Rejected(val reason:NativeAdaptiveCycleDraftRefusal):
        NativeAdaptiveCycleDraftPreview
}
sealed interface NativeAdaptiveCycleDraftDecision {
    data class Saved(val newDraftRevision:Long,val optionId:String):
        NativeAdaptiveCycleDraftDecision {
        /** Saving a draft is NOT program signoff or session issuance. */
        val mayIssueWorkout:Boolean get()=false
        val isSignedOff:Boolean get()=false
    }
    data class Rejected(val reason:NativeAdaptiveCycleDraftRefusal):
        NativeAdaptiveCycleDraftDecision
}

class NativeKotlinAdaptiveCycleDraftAcceptance(
    private val fence:NativeKotlinApprovalRevisionFence,
    private val identity:NativeKotlinCurrentIdentity,
    private val drafts:NativeCustomCycleDraftFileStore,
    private val sources:NativeKotlinCustomAuthoringSourceFileStore,
    private val permissionStore:NativeKotlinAdaptiveCyclePermissionFileStore
) {
    private val id=Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,79}$")
    private val digest=Regex("^[a-f0-9]{64}$")
    private val maxRevision=9_007_199_254_740_990L

    private fun owner(key:String):NativeKotlinOwnerIdentity? {
        if(!id.matches(key))return null
        return runCatching{identity.signedInIdentity()}.getOrNull()
            ?.takeIf{it.ownerKey==key && id.matches(it.athleteId)}
    }
    private fun valid(c:NativeAdaptiveCycleDraftCandidate):Boolean =
        listOf(c.ownerKey,c.cycleId,c.targetBlockId,c.optionId).all(id::matches) &&
            c.optionId!="keep_current" &&
            c.expectedDraftRevision in 1..maxRevision &&
            c.expectedSourceRevision in 1..maxRevision &&
            c.expectedPermissionRevision in 1..maxRevision &&
            c.proposedCycle.cycleId==c.cycleId &&
            c.proposedCycle.revision==c.expectedDraftRevision+1 &&
            c.proposedCycle.blocks.count{it.blockId==c.targetBlockId}==1

    private fun evaluate(
        c:NativeAdaptiveCycleDraftCandidate
    ):NativeAdaptiveCycleDraftPreview {
        if(!valid(c))return NativeAdaptiveCycleDraftPreview.Rejected(
            NativeAdaptiveCycleDraftRefusal.INVALID_REQUEST)
        val signed=owner(c.ownerKey)
            ?:return NativeAdaptiveCycleDraftPreview.Rejected(
                NativeAdaptiveCycleDraftRefusal.NOT_AUTHORIZED)
        // Caller-provided toggles alone are not authorization. Reopen the
        // current versioned, athlete-approved rights under the owner fence.
        val rights=when(val read=permissionStore.read(c.ownerKey,c.cycleId)) {
            is NativeAdaptivePermissionRead.Found->read.receipt
            else->return NativeAdaptiveCycleDraftPreview.Rejected(
                NativeAdaptiveCycleDraftRefusal.PERMISSIONS_NOT_APPROVED)
        }
        if(rights.athleteId!=signed.athleteId ||
            rights.revision!=c.expectedPermissionRevision)
            return NativeAdaptiveCycleDraftPreview.Rejected(
                NativeAdaptiveCycleDraftRefusal.PERMISSIONS_CHANGED)
        val requested=CustomCycleChangeKind.entries.filter {
            c.coachPermissions.permits(it)
        }.toSet()
        if(requested!=rights.allowedKinds)
            return NativeAdaptiveCycleDraftPreview.Rejected(
                NativeAdaptiveCycleDraftRefusal.PERMISSION_REQUIRED)
        val draft=when(val found=drafts.read(c.ownerKey,c.cycleId)) {
            is NativeCustomDraftReadResult.Found -> found.draft
            is NativeCustomDraftReadResult.Missing,
            is NativeCustomDraftReadResult.Deleted ->
                return NativeAdaptiveCycleDraftPreview.Rejected(
                    NativeAdaptiveCycleDraftRefusal.DRAFT_MISSING)
            else ->return NativeAdaptiveCycleDraftPreview.Rejected(
                NativeAdaptiveCycleDraftRefusal.STORAGE_UNAVAILABLE)
        }
        if(draft.revision!=c.expectedDraftRevision ||
            draft.athleteId!=signed.athleteId ||
            c.proposedCycle.athleteId!=signed.athleteId)
            return NativeAdaptiveCycleDraftPreview.Rejected(
                NativeAdaptiveCycleDraftRefusal.DRAFT_CHANGED)
        val authoring=when(val read=sources.read(c.ownerKey)) {
            is NativeAuthoringSourceRead.Found ->read.source
            else ->return NativeAdaptiveCycleDraftPreview.Rejected(
                NativeAdaptiveCycleDraftRefusal.SOURCE_MISSING)
        }
        if(authoring.revision!=c.expectedSourceRevision ||
            authoring.athleteId!=signed.athleteId)
            return NativeAdaptiveCycleDraftPreview.Rejected(
                NativeAdaptiveCycleDraftRefusal.SOURCE_CHANGED)

        val original=draft.blocks.singleOrNull{it.blockId==c.targetBlockId}
            ?:return NativeAdaptiveCycleDraftPreview.Rejected(
                NativeAdaptiveCycleDraftRefusal.INVALID_REQUEST)
        val updated=c.proposedCycle.blocks.single{it.blockId==c.targetBlockId}
        // A one-block choice can never clandestinely rewrite another block,
        // the athlete's cycle title, or other blocks' weekly overrides.
        if(draft.blocks.map{it.blockId}!=c.proposedCycle.blocks.map{it.blockId} ||
            draft.blocks.filter{it.blockId!=c.targetBlockId}!=
                c.proposedCycle.blocks.filter{it.blockId!=c.targetBlockId} ||
            draft.weekOverrides.filterKeys{it!=c.targetBlockId}!=
                c.proposedCycle.weekOverrides.filterKeys{it!=c.targetBlockId} ||
            draft.title!=c.proposedCycle.title ||
            draft.authorNote!=c.proposedCycle.authorNote ||
            draft.goal!=c.proposedCycle.goal ||
            draft.athleteId!=c.proposedCycle.athleteId)
            return NativeAdaptiveCycleDraftPreview.Rejected(
                NativeAdaptiveCycleDraftRefusal.UNRELATED_BLOCK_CHANGED)

        // A future changed split / weekday map needs a separately reviewed
        // source/policy update. Never persist it as if today's source were
        // sufficient to authorize the new calendar.
        if(original.split!=updated.split ||
            original.days.map{it.weekday}!=updated.days.map{it.weekday})
            return NativeAdaptiveCycleDraftPreview.Rejected(
                NativeAdaptiveCycleDraftRefusal.FUTURE_PROFILE_REQUIRES_APPROVAL)
        val comparison=runCatching{
            AdaptiveCycleOptionReviewDomain.review(
                AdaptiveCycleOptionRequest(draft,c.targetBlockId,
                    authoring.setup,authoring.catalog,c.coaching,
                    c.coachPermissions,listOf(AdaptiveCycleOption(
                        c.optionId,c.proposedCycle))))
        }.getOrNull() ?:return NativeAdaptiveCycleDraftPreview.Rejected(
            NativeAdaptiveCycleDraftRefusal.COACH_REVIEW_UNRESOLVED)
        val review=comparison.evaluated.singleOrNull()
            ?:return NativeAdaptiveCycleDraftPreview.Rejected(
                NativeAdaptiveCycleDraftRefusal.COACH_REVIEW_UNRESOLVED)
        if(review.status==AdaptiveCycleOptionStatus.PERMISSION_REQUIRED)
            return NativeAdaptiveCycleDraftPreview.Rejected(
                NativeAdaptiveCycleDraftRefusal.PERMISSION_REQUIRED)
        if(comparison.status!=AdaptiveCycleComparisonStatus.OPTIONS_COMPARED ||
            review.status!=AdaptiveCycleOptionStatus.READY_FOR_REVIEW ||
            review.transition?.status!=
                CustomCycleTransitionStatus.READY_FOR_ATHLETE_REVIEW ||
            review.coachingAudit?.status!=
                CustomCoachAuditStatus.READY_FOR_ATHLETE_REVIEW ||
            review.coachingAudit?.issues?.isNotEmpty()!=false)
            return NativeAdaptiveCycleDraftPreview.Rejected(
                NativeAdaptiveCycleDraftRefusal.COACH_REVIEW_UNRESOLVED)

        val prior=NativeCustomCycleSignoffFingerprint.calculate(
            draft,authoring,c.coaching)
        val next=NativeCustomCycleSignoffFingerprint.calculate(
            c.proposedCycle,authoring,c.coaching)
        val scope=listOf(c.ownerKey,c.cycleId,c.targetBlockId,c.optionId,
            prior,next,c.expectedPermissionRevision.toString(),
            rights.allowedKinds.sortedBy{it.name}.joinToString(",")).joinToString("\u0000")
        val sha=MessageDigest.getInstance("SHA-256")
            .digest(scope.toByteArray(Charsets.UTF_8))
            .joinToString(""){"%02x".format(it.toInt() and 255)}
        return NativeAdaptiveCycleDraftPreview.Ready(c.optionId,sha,
            Collections.unmodifiableList(checkNotNull(review.transition).differences),
            draft.revision,authoring.revision)
    }

    fun preview(c:NativeAdaptiveCycleDraftCandidate):NativeAdaptiveCycleDraftPreview {
        if(!valid(c))return NativeAdaptiveCycleDraftPreview.Rejected(
            NativeAdaptiveCycleDraftRefusal.INVALID_REQUEST)
        return runCatching{fence.withOwnerTransaction(c.ownerKey){evaluate(c)}}
            .getOrElse{NativeAdaptiveCycleDraftPreview.Rejected(
                NativeAdaptiveCycleDraftRefusal.STORAGE_UNAVAILABLE)}
    }

    /**
     * A fresh, exact on-screen review fingerprint and user confirmation are
     * required. Re-read/re-audit inside owner lock; save ONE revision-CAS
     * draft record only. Its previous signoff is now stale by revision.
     */
    fun accept(
        c:NativeAdaptiveCycleDraftCandidate,
        expectedReviewedFingerprint:String,
        athleteConfirmed:Boolean
    ):NativeAdaptiveCycleDraftDecision {
        if(!athleteConfirmed)return NativeAdaptiveCycleDraftDecision.Rejected(
            NativeAdaptiveCycleDraftRefusal.CONFIRMATION_REQUIRED)
        if(!digest.matches(expectedReviewedFingerprint))
            return NativeAdaptiveCycleDraftDecision.Rejected(
                NativeAdaptiveCycleDraftRefusal.REVIEW_CHANGED)
        return runCatching {
            fence.withOwnerTransaction(c.ownerKey) {
                val reviewed=evaluate(c)
                if(reviewed is NativeAdaptiveCycleDraftPreview.Rejected)
                    return@withOwnerTransaction NativeAdaptiveCycleDraftDecision.Rejected(
                        reviewed.reason)
                val ready=reviewed as NativeAdaptiveCycleDraftPreview.Ready
                if(ready.reviewedFingerprint!=expectedReviewedFingerprint)
                    return@withOwnerTransaction NativeAdaptiveCycleDraftDecision.Rejected(
                        NativeAdaptiveCycleDraftRefusal.REVIEW_CHANGED)
                // No historical workout, source record or consent is written.
                when(drafts.save(c.ownerKey,c.cycleId,c.expectedDraftRevision,
                    c.proposedCycle)) {
                    is NativeCustomDraftWriteResult.Committed ->
                        NativeAdaptiveCycleDraftDecision.Saved(
                            c.proposedCycle.revision,c.optionId)
                    else ->NativeAdaptiveCycleDraftDecision.Rejected(
                        NativeAdaptiveCycleDraftRefusal.DRAFT_STORAGE_REJECTED)
                }
            }
        }.getOrElse { NativeAdaptiveCycleDraftDecision.Rejected(
            NativeAdaptiveCycleDraftRefusal.STORAGE_UNAVAILABLE) }
    }
}
