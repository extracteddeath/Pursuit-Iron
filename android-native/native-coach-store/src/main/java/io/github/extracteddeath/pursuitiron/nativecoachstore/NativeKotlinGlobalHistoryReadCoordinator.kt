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
