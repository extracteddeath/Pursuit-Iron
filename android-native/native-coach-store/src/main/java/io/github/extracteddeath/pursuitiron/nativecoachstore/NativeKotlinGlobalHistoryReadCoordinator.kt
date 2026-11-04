package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*

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

    private fun readFenced(
        ownerKey: String, athleteId: String
    ): NativeKotlinHistoryReadResult {
        fun denied(reason: NativeKotlinHistoryReadRefusal) =
            NativeKotlinHistoryReadResult.Rejected(reason)

        val manifest = runCatching {
            manifestAuthority.currentHistoryManifest(ownerKey, athleteId)
        }.getOrNull() ?: return denied(NativeKotlinHistoryReadRefusal.NOT_AUTHORIZED)
        if (manifest.ownerKey != ownerKey || manifest.athleteId != athleteId ||
            manifest.revision < 1 || manifest.completions.size > 128)
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

        // The manifest owner must participate in this fence for the
        // re-read to guarantee a coherent snapshot.
        val refreshed = runCatching {
            manifestAuthority.currentHistoryManifest(ownerKey, athleteId)
        }.getOrNull()
        if (refreshed != frozen)
            return denied(NativeKotlinHistoryReadRefusal.MANIFEST_CHANGED)
        val result = KotlinGlobalHistoryIndexDomain.project(
            KotlinGlobalHistoryRead(
                athleteId, frozen.revision, frozen.revision,
                frozen.completions, recovered
            )
        )
        if (result !is KotlinGlobalHistoryResult.Candidate) {
            val reason = (result as KotlinGlobalHistoryResult.Rejected).reason
            return NativeKotlinHistoryReadResult.Rejected(
                NativeKotlinHistoryReadRefusal.GLOBAL_HISTORY_REJECTED, reason)
        }
        return NativeKotlinHistoryReadResult.Review(result.index)
    }
}
