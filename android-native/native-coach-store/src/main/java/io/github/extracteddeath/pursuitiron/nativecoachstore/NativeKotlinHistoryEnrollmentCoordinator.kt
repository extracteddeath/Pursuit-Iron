package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*

/**
 * Safe native-only enrollment. Migration from JS backups is NOT inferred:
 * the app's real account/data-migration owner must independently certify
 * there is NO unimported legacy history for this athlete.
 *
 * All existing Kotlin journal files (including unfinished) and all saved
 * correction files are enumerated from private disk, not caller-selected.
 * The caller supplies an authoritative chronological ordering because old
 * journal envelopes do not contain durable completion ordinals.
 */
enum class NativeHistoryEnrollmentRefusal {
    INVALID_SCOPE, NOT_AUTHORIZED, LEGACY_HISTORY_UNRESOLVED,
    JOURNAL_INVENTORY_INVALID, CORRECTION_INVENTORY_INVALID,
    INCOMPLETE_OR_DUPLICATE_CHRONOLOGY, ORPHANED_CORRECTION,
    INVALID_WORKOUT_OR_CORRECTION, INDEX_REJECTED,
    EXISTING_MANIFEST_CONFLICT, STORAGE_UNAVAILABLE,
    SEPARATE_HISTORY_NOT_CONFIRMED, SEPARATE_NATIVE_OWNER_UNVERIFIED,
    EXISTING_NATIVE_WORKOUT_REQUIRES_MIGRATION, CUSTOM_INVENTORY_UNVERIFIED
}

sealed interface NativeHistoryEnrollmentResult {
    data class Enrolled(
        val manifest: NativeKotlinHistoryManifest,
        val issuedWorkoutCount: Int,
        val recoveredCorrectionCount: Int,
        val replayed: Boolean
    ) : NativeHistoryEnrollmentResult

    data class Rejected(val reason: NativeHistoryEnrollmentRefusal) :
        NativeHistoryEnrollmentResult
}

/**
 * The only production-shaped enrollment boundary. It cannot manufacture a
 * "new athlete with no workouts" merely because the UI listed zero sessions.
 *
 * All owner mutations must honor the injected revision fence. Low-level
 * internal empty-enroll methods exist only as test seams for the earlier
 * transaction protocol; the candidate host must not call them.
 */
class NativeKotlinHistoryEnrollmentCoordinator(
    private val journalStore: KotlinNativeJournalFileStore,
    private val correctionStore: KotlinNativeSavedCorrectionFileStore,
    private val historyStore: NativeKotlinHistoryManifestFileStore,
    private val ownerFence: NativeKotlinApprovalRevisionFence,
    private val workoutAuthority: NativeKotlinWorkoutAuthority
) {
    private val id = Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,79}$")

    /**
     * @param chronologicalFinishedWorkoutIds authenticated completion order
     *     from a separate owner-controlled history migration source, NEVER
     *     only the current UI workout list or unordered filenames.
     * @param certifyNoUnmigratedLegacyHistory independent native migration
     *     owner evidence. False/null/throw must refuse, not enroll empty.
     */
    fun enrollNativeOnly(
        owner: String,
        athlete: String,
        chronologicalFinishedWorkoutIds: List<String>,
        authenticateOwner: (String, String) -> Boolean,
        certifyNoUnmigratedLegacyHistory: (String, String) -> Boolean,
        requireEmptyNativeInventory:Boolean=false
    ): NativeHistoryEnrollmentResult {
        if (!id.matches(owner) || !id.matches(athlete))
            return NativeHistoryEnrollmentResult.Rejected(
                NativeHistoryEnrollmentRefusal.INVALID_SCOPE)
        return runCatching {
            ownerFence.withOwnerTransaction(owner) {
                enrollFenced(owner, athlete, chronologicalFinishedWorkoutIds,
                    authenticateOwner, certifyNoUnmigratedLegacyHistory,
                    requireEmptyNativeInventory)
            }
        }.getOrElse {
            NativeHistoryEnrollmentResult.Rejected(
                NativeHistoryEnrollmentRefusal.STORAGE_UNAVAILABLE)
        }
    }


    /**
     * First-class, **independent** local training history, NOT a legacy
     * migration or a declaration that this person never trained before.
     *
     * Only an explicitly opted-in, freshly minted device-local Kotlin owner
     * may use it. The host separately validates the immutable local-owner
     * identity and reviewed starter source; this method checks the COMPLETE
     * native custom census and all composer/correction inventory while the
     * SAME owner transaction is held. Any existing native workout makes the
     * independent empty-history path unavailable. Other web/app records are
     * never read, modified or claimed to be imported.
     */
    fun enrollSeparateLocalHistory(
        owner:String,
        athlete:String,
        explicitSeparateHistoryConsent:Boolean,
        authenticateOwner:(String,String)->Boolean,
        verifyIndependentLocalOwner:(String,String)->Boolean,
        customInventory:NativeKotlinCustomOutstandingWorkoutInventory
    ):NativeHistoryEnrollmentResult {
        fun no(reason:NativeHistoryEnrollmentRefusal)=
            NativeHistoryEnrollmentResult.Rejected(reason)
        if(!id.matches(owner) || !id.matches(athlete))
            return no(NativeHistoryEnrollmentRefusal.INVALID_SCOPE)
        if(!explicitSeparateHistoryConsent)
            return no(NativeHistoryEnrollmentRefusal.SEPARATE_HISTORY_NOT_CONFIRMED)
        return runCatching {
            ownerFence.withOwnerTransaction(owner) {
                if(!runCatching{authenticateOwner(owner,athlete)}.getOrDefault(false))
                    return@withOwnerTransaction no(
                        NativeHistoryEnrollmentRefusal.NOT_AUTHORIZED)
                if(!runCatching{
                        verifyIndependentLocalOwner(owner,athlete)
                    }.getOrDefault(false))
                    return@withOwnerTransaction no(
                        NativeHistoryEnrollmentRefusal.SEPARATE_NATIVE_OWNER_UNVERIFIED)
                // The inventory enumerates both prepared and issued custom
                // sessions and reconciles them. An "empty UI" is no evidence.
                val custom=customInventory.inspect(owner,athlete,emptySet())
                if(custom !is NativeCustomInventoryReview.Reviewed)
                    return@withOwnerTransaction no(
                        NativeHistoryEnrollmentRefusal.CUSTOM_INVENTORY_UNVERIFIED)
                if(custom.sessions.isNotEmpty())
                    return@withOwnerTransaction no(
                        NativeHistoryEnrollmentRefusal.EXISTING_NATIVE_WORKOUT_REQUIRES_MIGRATION)
                val previous=historyStore.open(owner,athlete)
                if(previous is NativeHistoryLedgerResult.Durable &&
                    (!previous.ledger.readyForHistoryReads ||
                        previous.ledger.completions.isNotEmpty()))
                    return@withOwnerTransaction no(
                        NativeHistoryEnrollmentRefusal.EXISTING_MANIFEST_CONFLICT)
                if(previous is NativeHistoryLedgerResult.Rejected &&
                    previous.reason!=NativeHistoryLedgerRefusal.NOT_ENROLLED)
                    return@withOwnerTransaction no(
                        NativeHistoryEnrollmentRefusal.EXISTING_MANIFEST_CONFLICT)
                // Legacy data is NOT certified absent for the HUMAN.
                // This is a new, separate native history for this local owner
                // and requires zero native journal or correction records.
                enrollFenced(owner,athlete,emptyList(),
                    authenticateOwner, {o,a->
                        verifyIndependentLocalOwner(o,a)
                    },requireEmptyNativeInventory=true)
            }
        }.getOrElse {
            no(NativeHistoryEnrollmentRefusal.STORAGE_UNAVAILABLE)
        }
    }

    private fun enrollFenced(
        owner: String,
        athlete: String,
        chronology: List<String>,
        authenticateOwner: (String, String) -> Boolean,
        certifyNoUnmigratedLegacyHistory: (String, String) -> Boolean,
        requireEmptyNativeInventory:Boolean=false
    ): NativeHistoryEnrollmentResult {
        fun denied(reason: NativeHistoryEnrollmentRefusal) =
            NativeHistoryEnrollmentResult.Rejected(reason)

        val authenticated = runCatching {
            authenticateOwner(owner, athlete)
        }.getOrDefault(false)
        if (!authenticated)
            return denied(NativeHistoryEnrollmentRefusal.NOT_AUTHORIZED)
        if (!runCatching {
                certifyNoUnmigratedLegacyHistory(owner, athlete)
            }.getOrDefault(false))
            return denied(NativeHistoryEnrollmentRefusal.LEGACY_HISTORY_UNRESOLVED)

        // An explicitly ordered empty list is valid ONLY when the physical
        // inventory confirms that no completed Kotlin journal exists.
        if (chronology.size > KotlinHistoryCapacityPolicy.MAX_MANIFEST_COMPLETIONS ||
            chronology.any { !id.matches(it) } ||
            chronology.distinct().size != chronology.size)
            return denied(NativeHistoryEnrollmentRefusal.INCOMPLETE_OR_DUPLICATE_CHRONOLOGY)

        val scanned = journalStore.inventoryOwner(owner, athlete) { o, source ->
            source.athleteId == athlete && runCatching {
                workoutAuthority.canAccessIssuedWorkout(o, source)
            }.getOrDefault(false)
        }
        if (scanned !is NativeKotlinJournalInventoryResult.Verified)
            return denied(NativeHistoryEnrollmentRefusal.JOURNAL_INVENTORY_INVALID)
        val journals = scanned.journals
        if(requireEmptyNativeInventory && journals.isNotEmpty())
            return denied(
                NativeHistoryEnrollmentRefusal.EXISTING_NATIVE_WORKOUT_REQUIRES_MIGRATION)
        val finished = journals.filter { it.finishedByEventId != null }
        val savedScan = correctionStore.inventoryOwner(owner)
        if (savedScan !is NativeSavedCorrectionInventoryResult.Verified)
            return denied(NativeHistoryEnrollmentRefusal.CORRECTION_INVENTORY_INVALID)
        val correctionIds = savedScan.workoutIds
        if(requireEmptyNativeInventory && correctionIds.isNotEmpty())
            return denied(
                NativeHistoryEnrollmentRefusal.EXISTING_NATIVE_WORKOUT_REQUIRES_MIGRATION)
        if (correctionIds.any { corrected ->
                finished.none { it.snapshot.workoutId == corrected }
            })
            return denied(NativeHistoryEnrollmentRefusal.ORPHANED_CORRECTION)
        if (finished.size != chronology.size ||
            finished.map { it.snapshot.workoutId }.toSet() != chronology.toSet() ||
            journals.map { it.storedIdentity.storageReceiptId }.distinct().size !=
                journals.size)
            return denied(NativeHistoryEnrollmentRefusal.INCOMPLETE_OR_DUPLICATE_CHRONOLOGY)

        val byId = finished.associateBy { it.snapshot.workoutId }
        val completed = ArrayList<KotlinChronologicalWorkout>(chronology.size)
        val manifestEntries = ArrayList<KotlinHistoryManifestEntry>(chronology.size)
        var seenCorrections = 0
        for ((index, workoutId) in chronology.withIndex()) {
            val journal = byId[workoutId]
                ?: return denied(NativeHistoryEnrollmentRefusal.INCOMPLETE_OR_DUPLICATE_CHRONOLOGY)
            val edits = correctionStore.openStored(owner, workoutId) { o, source ->
                source.athleteId == athlete && runCatching {
                    workoutAuthority.canAccessIssuedWorkout(o, source)
                }.getOrDefault(false)
            }
            if (edits !is NativeSavedCorrectionFileResult.Opened ||
                edits.state.originalFinishedJournal != journal ||
                (workoutId in correctionIds) != edits.hasCorrectionFile)
                return denied(NativeHistoryEnrollmentRefusal.INVALID_WORKOUT_OR_CORRECTION)
            val revision = if (edits.hasCorrectionFile)
                edits.state.correctionRevision else 0L
            if (edits.hasCorrectionFile) {
                if (revision <= 0)
                    return denied(NativeHistoryEnrollmentRefusal.INVALID_WORKOUT_OR_CORRECTION)
                seenCorrections += edits.state.receipts.size
            }
            val ordinal = index.toLong() + 1
            completed.add(KotlinChronologicalWorkout(
                ordinal, journal,
                if (edits.hasCorrectionFile) edits.state else null))
            manifestEntries.add(KotlinHistoryManifestEntry(
                ordinal, workoutId, journal.storedIdentity.storageReceiptId,
                checkNotNull(journal.finishedByEventId), journal.revision, revision))
        }
        // This read-projector validates every immutable receipt + effective
        // correction fact, not merely the claimed completion ordinals.
        val checked = KotlinGlobalHistoryIndexDomain.project(
            KotlinGlobalHistoryRead(athlete, 1, 1, manifestEntries, completed))
        if (checked !is KotlinGlobalHistoryResult.Candidate)
            return denied(NativeHistoryEnrollmentRefusal.INDEX_REJECTED)

        val stored = historyStore.enrollVerifiedSnapshot(
            owner, athlete, manifestEntries, authenticateOwner)
        if (stored !is NativeHistoryLedgerResult.Durable)
            return denied(if (stored is NativeHistoryLedgerResult.Rejected &&
                stored.reason == NativeHistoryLedgerRefusal.INVALID_MANIFEST_TRANSITION)
                NativeHistoryEnrollmentRefusal.EXISTING_MANIFEST_CONFLICT
            else NativeHistoryEnrollmentRefusal.STORAGE_UNAVAILABLE)
        if (!stored.ledger.readyForHistoryReads ||
            stored.ledger.completions != manifestEntries)
            return denied(NativeHistoryEnrollmentRefusal.EXISTING_MANIFEST_CONFLICT)
        return NativeHistoryEnrollmentResult.Enrolled(
            NativeKotlinHistoryManifest(owner, athlete, stored.ledger.revision,
                stored.ledger.completions.toList()),
            journals.size, seenCorrections, stored.replayed)
    }
}
