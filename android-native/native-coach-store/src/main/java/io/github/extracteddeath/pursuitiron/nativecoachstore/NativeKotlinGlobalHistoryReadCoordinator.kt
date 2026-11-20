package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.util.Collections

/**
 * This interface must read a COMPLETE, authenticated owner-controlled
 * history manifest from durable native account storage. Never implement
 * it with a Compose screen's list of workout IDs or an exported JS cache.
 */
interface NativeKotlinHistoryManifestAuthority {
    fun currentHistoryManifest(
        ownerKey: String, athleteId: String
    ): NativeKotlinHistoryManifest?
}

data class NativeKotlinHistoryManifest(
    val ownerKey: String,
    val athleteId: String,
    val revision: Long,
    /** Every retained completion, ordered by its durable completion ordinal. */
    val completions: List<KotlinHistoryManifestEntry>
)

enum class NativeKotlinHistoryReadRefusal {
    INVALID_REQUEST,
    NOT_AUTHORIZED,
    MANIFEST_CHANGED,
    MISSING_OR_INVALID_WORKOUT,
    MISSING_OR_INVALID_CORRECTIONS,
    GLOBAL_HISTORY_REJECTED,
    STORAGE_UNAVAILABLE
}

sealed interface NativeKotlinHistoryReadResult {
    /** Read-only projection; Last/PR UI publication needs separate approval. */
    data class Review(
        val index: KotlinGlobalHistoryIndex
    ) : NativeKotlinHistoryReadResult
    data class Rejected(
        val reason: NativeKotlinHistoryReadRefusal,
        val projectionReason: KotlinGlobalHistoryRefusal? = null
    ) : NativeKotlinHistoryReadResult
}

/**
 * Native owner-fenced, complete-manifest read for a SINGLE immutable
 * issued-program source epoch. Records are only accepted from the same
 * private journal/correction files used by History; the caller cannot pass
 * replacement workout objects, event summaries, or a partial UI list.
 *
 * This is evidence for a FUTURE planning review, not an automatic consent
 * grant or permission to modify accepted programs or issued workouts.
 */
data class NativeKotlinProgramHistoryReview(
    val ledger:KotlinProgramHistoryReadout,
    val ownerManifestRevision:Long,
    val ownerHistoryFingerprint:String,
    /** Complete-manifest ordinal, not lexical workout ID/date guessing. */
    val completionOrderByWorkout:Map<String,Long>,
    /** Reconciled, conservative per-day/per-purpose/per-machine actual
     * performance: never a generated prescription or change entitlement. */
    val performance:KotlinProgramPerformanceReport
) {
    val canApplyProgramAdaptation:Boolean get()=false
    val canChangeIssuedTargets:Boolean get()=false
}

enum class NativeKotlinProgramHistoryRefusal {
    INVALID_REQUEST,
    OWNER_HISTORY_UNAVAILABLE,
    INVALID_PROGRAM_EVIDENCE,
    INVALID_PERFORMANCE_EVIDENCE,
    STORAGE_UNAVAILABLE
}

sealed interface NativeKotlinProgramHistoryReadResult {
    data class Review(val evidence:NativeKotlinProgramHistoryReview):
        NativeKotlinProgramHistoryReadResult
    data class Rejected(
        val reason:NativeKotlinProgramHistoryRefusal,
        val ownerHistoryReason:NativeKotlinHistoryReadRefusal?=null,
        val programEvidenceReason:KotlinProgramHistoryReadoutRefusal?=null,
        val performanceReason:KotlinProgramPerformanceRefusal?=null
    ):NativeKotlinProgramHistoryReadResult
}

/**
 * Read-only bridge to the actual generated future block. The complete native
 * manifest was reopened under the owner transaction FIRST, then the regular
 * generator and existing historical performance authority were used. This
 * is a snapshot for a later independent athlete-coaching review: no approval,
 * workout issuance or source-revision lock persists beyond this call.
 */
data class NativeKotlinGeneratedFutureHistoryReview(
    val generated:GeneratedAdaptiveBlockResult,
    val ownerHistory:NativeKotlinProgramHistoryReview,
    val linked:KotlinFuturePerformanceLink
) {
    val canApplyAutomatically:Boolean get()=false
    val canChangeIssuedWorkout:Boolean get()=false
    val canPromotePhase:Boolean get()=false
}

enum class NativeKotlinGeneratedFutureHistoryRefusal {
    INVALID_REQUEST, OWNER_HISTORY_UNAVAILABLE, GENERATION_UNRESOLVED,
    SOURCE_OR_CELL_MISMATCH, STORAGE_UNAVAILABLE
}
sealed interface NativeKotlinGeneratedFutureHistoryResult {
    data class Review(val evidence:NativeKotlinGeneratedFutureHistoryReview):
        NativeKotlinGeneratedFutureHistoryResult
    data class Rejected(
        val reason:NativeKotlinGeneratedFutureHistoryRefusal,
        val historyReason:NativeKotlinProgramHistoryRefusal?=null,
        val linkReason:KotlinFuturePerformanceLinkRefusal?=null
    ):NativeKotlinGeneratedFutureHistoryResult
}

/**
 * Owner-scoped snapshot *read*, built from real private journal/correction
 * files rather than UI-provided completed sets. Calls BOTH stores under
 * the same account revision fence as workout issuance / source mutations.
 *
 * This is not a transactional manifest writer. Without an independently
 * durable complete manifest, and without every Finish/edit using the same
 * lock/manifest revision, the production cutover gate is still blocked.
 */
class NativeKotlinGlobalHistoryReadCoordinator(
    private val journalStore: KotlinNativeJournalFileStore,
    private val correctionStore: KotlinNativeSavedCorrectionFileStore,
    private val workoutAuthority: NativeKotlinWorkoutAuthority,
    private val manifestAuthority: NativeKotlinHistoryManifestAuthority,
    private val ownerFence: NativeKotlinApprovalRevisionFence
) {
    private val id = Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,79}$")

    fun read(ownerKey: String, athleteId: String): NativeKotlinHistoryReadResult {
        if (!id.matches(ownerKey) || !id.matches(athleteId))
            return NativeKotlinHistoryReadResult.Rejected(
                NativeKotlinHistoryReadRefusal.INVALID_REQUEST)
        return runCatching {
            ownerFence.withOwnerTransaction(ownerKey) {
                readFenced(ownerKey, athleteId)
            }
        }.getOrElse {
            NativeKotlinHistoryReadResult.Rejected(
                NativeKotlinHistoryReadRefusal.STORAGE_UNAVAILABLE)
        }
    }

    private sealed interface VerifiedFencedHistory {
        data class Ready(
            val index:KotlinGlobalHistoryIndex,
            val sourceRead:KotlinGlobalHistoryRead
        ):VerifiedFencedHistory
        data class Rejected(val reason:NativeKotlinHistoryReadResult.Rejected):
            VerifiedFencedHistory
    }

    /**
     * Both the existing global history UI and future-program evidence must
     * consume this ONE coherent owner-locked read. Never rely on an already
     * assembled Last/PR index as the only source of set observations.
     */
    private fun readFenced(
        ownerKey:String,athleteId:String
    ):NativeKotlinHistoryReadResult = when(
        val verified=readVerifiedFenced(ownerKey,athleteId)
    ) {
        is VerifiedFencedHistory.Ready ->
            NativeKotlinHistoryReadResult.Review(verified.index)
        is VerifiedFencedHistory.Rejected -> verified.reason
    }

    /**
     * Source epoch is selected only AFTER validating the COMPLETE native
     * manifest against every durable workout and correction. Mixed program
     * revisions, gyms and catalog/rules versions are never pooled.
     */
    fun readProgramHistory(
        ownerKey:String,
        athleteId:String,
        expectedSource:KotlinTrainingSourceRevision,
        allowedDayIndices:Set<Int>
    ):NativeKotlinProgramHistoryReadResult {
        if(!id.matches(ownerKey) || !id.matches(athleteId) ||
            expectedSource.athleteId!=athleteId ||
            !id.matches(expectedSource.programId) ||
            allowedDayIndices.isEmpty() ||
            allowedDayIndices.any{it !in 0..6})
            return NativeKotlinProgramHistoryReadResult.Rejected(
                NativeKotlinProgramHistoryRefusal.INVALID_REQUEST)
        return runCatching {
            ownerFence.withOwnerTransaction(ownerKey) {
                when(val verified=readVerifiedFenced(ownerKey,athleteId)) {
                    is VerifiedFencedHistory.Rejected ->
                        NativeKotlinProgramHistoryReadResult.Rejected(
                            NativeKotlinProgramHistoryRefusal.OWNER_HISTORY_UNAVAILABLE,
                            ownerHistoryReason=verified.reason.reason)
                    is VerifiedFencedHistory.Ready -> {
                        val selected=verified.sourceRead.completedWorkouts.filter{
                            it.journal.snapshot.source==expectedSource
                        }
                        val projected=KotlinProgramHistoryReadoutDomain.review(
                            KotlinProgramHistoryReadoutRequest(
                                athleteId,expectedSource.programId,expectedSource,
                                allowedDayIndices,
                                selected.map{it.journal},
                                selected.mapNotNull{it.savedCorrections}
                            )
                        )
                        when(projected) {
                            is KotlinProgramHistoryReadoutResult.Rejected ->
                                NativeKotlinProgramHistoryReadResult.Rejected(
                                    NativeKotlinProgramHistoryRefusal.INVALID_PROGRAM_EVIDENCE,
                                    programEvidenceReason=projected.reason)
                            is KotlinProgramHistoryReadoutResult.Candidate -> {
                                val ordinals=linkedMapOf<String,Long>()
                                for(workout in selected)
                                    ordinals[workout.journal.snapshot.workoutId]=
                                        workout.completionOrder
                                val ordered=Collections.unmodifiableMap(
                                    ordinals)
                                val trends=KotlinProgramPerformanceEvidenceDomain
                                    .review(projected.ledger,ordered)
                                when(trends) {
                                    is KotlinProgramPerformanceResult.Rejected ->
                                        NativeKotlinProgramHistoryReadResult.Rejected(
                                            NativeKotlinProgramHistoryRefusal
                                                .INVALID_PERFORMANCE_EVIDENCE,
                                            performanceReason=trends.reason)
                                    is KotlinProgramPerformanceResult.Reviewed ->
                                        NativeKotlinProgramHistoryReadResult.Review(
                                            NativeKotlinProgramHistoryReview(
                                                projected.ledger,
                                                verified.index.manifestRevision,
                                                verified.index.contentFingerprint,
                                                ordered,trends.report
                                            )
                                        )
                                }
                            }
                        }
                    }
                }
            }
        }.getOrElse{
            NativeKotlinProgramHistoryReadResult.Rejected(
                NativeKotlinProgramHistoryRefusal.STORAGE_UNAVAILABLE)
        }
    }

    /**
     * Future-only association of one generated candidate's exact numbered
     * cells with owner-attested history. Do not feed a caller-replayed
     * journal into the generator; the owner-coherent History remains sole
     * source of athlete observations. History is a read-only snapshot and
     * must be revalidated at eventual program acceptance.
     */
    fun readGeneratedFutureHistory(
        ownerKey:String,athleteId:String,
        expectedSource:KotlinTrainingSourceRevision,
        allowedDayIndices:Set<Int>,
        input:GeneratedAdaptiveBlockRequest
    ):NativeKotlinGeneratedFutureHistoryResult {
        val athlete=input.program.setup.reviewed
        if(!id.matches(ownerKey) || !id.matches(athleteId) ||
            athlete==null || athlete.athleteProfileId!=athleteId ||
            input.currentCycle.athleteId!=athleteId ||
            input.currentCycle.cycleId!=expectedSource.programId ||
            input.currentCycle.revision!=expectedSource.programRevision ||
            expectedSource.athleteId!=athleteId ||
            expectedSource.gymId!=athlete.gym.gymId)
            return NativeKotlinGeneratedFutureHistoryResult.Rejected(
                NativeKotlinGeneratedFutureHistoryRefusal.INVALID_REQUEST)
        val owner=readProgramHistory(ownerKey,athleteId,expectedSource,
            allowedDayIndices)
        if(owner !is NativeKotlinProgramHistoryReadResult.Review)
            return NativeKotlinGeneratedFutureHistoryResult.Rejected(
                NativeKotlinGeneratedFutureHistoryRefusal.OWNER_HISTORY_UNAVAILABLE,
                historyReason=(owner as NativeKotlinProgramHistoryReadResult.Rejected).reason)
        // This exact user-facing source snapshot has no untrusted replay
        // attached. Any later accepted change needs a fresh owner read.
        val trustedInput=input.copy(historyReadoutRequest=null)
        val generated=runCatching {
            GeneratedAdaptiveBlockDomain.propose(trustedInput)
        }.getOrNull()
        if(generated==null ||
            generated.status!=GeneratedAdaptiveBlockStatus.READY_FOR_CYCLE_REVIEW)
            return NativeKotlinGeneratedFutureHistoryResult.Rejected(
                NativeKotlinGeneratedFutureHistoryRefusal.GENERATION_UNRESOLVED)
        return when(val link=KotlinFutureProgramPerformanceLinkDomain.review(
            trustedInput,generated,owner.evidence.ledger,
            owner.evidence.performance)) {
            is KotlinFuturePerformanceLinkResult.Rejected ->
                NativeKotlinGeneratedFutureHistoryResult.Rejected(
                    NativeKotlinGeneratedFutureHistoryRefusal.SOURCE_OR_CELL_MISMATCH,
                    linkReason=link.reason)
            is KotlinFuturePerformanceLinkResult.Reviewed ->
                NativeKotlinGeneratedFutureHistoryResult.Review(
                    NativeKotlinGeneratedFutureHistoryReview(
                        generated,owner.evidence,link.link))
        }
    }

    private fun readVerifiedFenced(
        ownerKey: String, athleteId: String
    ): VerifiedFencedHistory {
        fun denied(reason:NativeKotlinHistoryReadRefusal)=
            VerifiedFencedHistory.Rejected(
                NativeKotlinHistoryReadResult.Rejected(reason))

        val manifest = runCatching {
            manifestAuthority.currentHistoryManifest(ownerKey, athleteId)
        }.getOrNull() ?: return denied(NativeKotlinHistoryReadRefusal.NOT_AUTHORIZED)
        if (manifest.ownerKey != ownerKey || manifest.athleteId != athleteId ||
            manifest.revision < 1 ||
            manifest.completions.size > KotlinHistoryCapacityPolicy.MAX_MANIFEST_COMPLETIONS)
            return denied(NativeKotlinHistoryReadRefusal.NOT_AUTHORIZED)
        val frozen = manifest.copy(completions = manifest.completions.toList())
        val recovered = ArrayList<KotlinChronologicalWorkout>(
            frozen.completions.size)
        for (entry in frozen.completions) {
            val authenticate: (String, KotlinTrainingSourceRevision) -> Boolean =
                { owner, source ->
                    source.athleteId == athleteId && runCatching {
                        workoutAuthority.canAccessIssuedWorkout(owner, source)
                    }.getOrDefault(false)
                }
            val original = journalStore.openStored(
                ownerKey, entry.workoutId, authenticate)
            if (original !is NativeJournalFileResult.Durable)
                return denied(NativeKotlinHistoryReadRefusal.MISSING_OR_INVALID_WORKOUT)
            val edits = correctionStore.openStored(
                ownerKey, entry.workoutId, authenticate)
            if (edits !is NativeSavedCorrectionFileResult.Opened)
                return denied(NativeKotlinHistoryReadRefusal.MISSING_OR_INVALID_CORRECTIONS)
            if (edits.state.originalFinishedJournal != original.journal)
                return denied(NativeKotlinHistoryReadRefusal.MISSING_OR_INVALID_CORRECTIONS)
            recovered.add(KotlinChronologicalWorkout(
                entry.completionOrder, original.journal,
                if (edits.hasCorrectionFile) edits.state else null
            ))
        }

        // The complete manifest owner MUST participate in this fence.
        val refreshed = runCatching {
            manifestAuthority.currentHistoryManifest(ownerKey, athleteId)
        }.getOrNull()
        if (refreshed != frozen)
            return denied(NativeKotlinHistoryReadRefusal.MANIFEST_CHANGED)
        val complete=KotlinGlobalHistoryRead(
            athleteId, frozen.revision, frozen.revision,
            frozen.completions, recovered
        )
        val result = KotlinGlobalHistoryIndexDomain.project(complete)
        if (result !is KotlinGlobalHistoryResult.Candidate) {
            val reason=(result as KotlinGlobalHistoryResult.Rejected).reason
            return VerifiedFencedHistory.Rejected(
                NativeKotlinHistoryReadResult.Rejected(
                    NativeKotlinHistoryReadRefusal.GLOBAL_HISTORY_REJECTED,
                    reason))
        }
        return VerifiedFencedHistory.Ready(result.index,complete)
    }
}
